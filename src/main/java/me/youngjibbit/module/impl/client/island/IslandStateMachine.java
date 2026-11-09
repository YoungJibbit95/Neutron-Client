package me.youngjibbit.module.impl.client.island;

import java.util.Locale;
import java.util.Arrays;

/**
 * Pure, bounded view state. Callers supply already validated, immutable observations.
 * Activity adapters consume copied producer observations; module lifecycle and correction
 * providers remain pending. Rendering never becomes a source of gameplay/network events.
 */
public final class IslandStateMachine {
    public enum State {
        IDLE, TARGET, SCAFFOLD, MODULE_TOAST, POSSIBLE_SETBACK, CLOCK_REMINDER, INVENTORY_ACTIVITY;
        public static State fromName(String value) {
            if (value != null) try { return valueOf(value.toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignored) { /* unknown saved/fixture state */ }
            return IDLE;
        }
    }

    public static final class Target {
        public final int entityId;
        public final String name;
        public final float health, maxHealth, absorption;

        public Target(int entityId, String name, float health, float maxHealth, float absorption) {
            this.entityId = entityId;
            this.name = name == null ? "" : name.trim();
            this.maxHealth = Float.isFinite(maxHealth) && maxHealth > 0 ? maxHealth : 20f;
            this.health = Float.isFinite(health) ? clamp(health, 0, this.maxHealth) : 0;
            this.absorption = Float.isFinite(absorption) ? clamp(absorption, 0, 2048) : 0;
        }
        public boolean valid() { return entityId >= 0 && !name.isEmpty(); }
        public float fraction() { return clamp(health / maxHealth, 0, 1); }
    }

    /** Copied render data; the legacy constructor remains a headless fixture. */
    public static final class Scaffold {
        public final boolean active,observed;
        public final int blockCount,metadata,itemId;
        public final String approach,profile,status;
        public final long sequence;
        public Scaffold(boolean active,int blockCount,String approach) {
            this(active,blockCount,approach,"","",-1,-1,false,0);
        }
        private Scaffold(boolean active,int blockCount,String approach,String profile,String status,
                         int itemId,int metadata,boolean observed,long sequence) {
            this.active=active;this.blockCount=blockCount;this.approach=approach==null?"":approach;
            this.profile=profile==null?"":profile;this.status=status==null?"":status;
            this.itemId=itemId;this.metadata=metadata;this.observed=observed;this.sequence=sequence;
        }
        public static Scaffold observed(int blockCount,String approach,String profile,String status,
                                        int itemId,int metadata,long sequence) {
            return new Scaffold(true,blockCount,approach,profile,status,itemId,metadata,true,sequence);
        }
        public boolean valid(){return active&&blockCount>=(observed?-1:0);}
    }

    /** Uhr-Erinnerung an lokale 10-Minuten-Marken (:00, :10, ...). Discret, einmal pro Slot. */
    public static final class ClockReminder {
        public final String time;   // "HH:mm"
        public final boolean acknowledged;
        public ClockReminder(String time, boolean acknowledged) {
            this.time = time == null ? "" : time.trim();
            this.acknowledged = acknowledged;
        }
        public boolean valid() { return !acknowledged && time.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]"); }
    }

    /** Immutable presentation data from a validated read-only producer or a legacy test fixture. */
    public static final class InventoryActivity {
        public final String action,producer;
        public final int processedCount,totalCount;
        public final boolean complete,observed;
        public final long sequence;
        public InventoryActivity(String action,int processedCount,int totalCount,boolean complete) {
            this("Inventory",action,processedCount,totalCount,complete,false,0);
        }
        private InventoryActivity(String producer,String action,int processedCount,int totalCount,
                                  boolean complete,boolean observed,long sequence) {
            this.producer=producer==null?"Inventory":producer;
            this.action=action==null?"":action.trim();this.processedCount=processedCount;
            this.totalCount=totalCount;this.complete=complete;this.observed=observed;this.sequence=sequence;
        }
        public static InventoryActivity observed(String producer,String action,int processedCount,int totalCount,
                                                 boolean complete,long sequence) {
            return new InventoryActivity(producer,action,processedCount,totalCount,complete,true,sequence);
        }
        public boolean valid(){return !action.isEmpty()&&processedCount>=(observed?-1:0)&&totalCount>=(observed?-1:0)
                &&(processedCount<0||totalCount<0||processedCount<=totalCount);}
    }

    public static final class Selection {
        public final State state;
        public final Target target;
        public final Scaffold scaffold;
        public final ClockReminder clock;
        public final InventoryActivity inventory;
        public final String label;
        private Selection(State state, Target target, Scaffold scaffold, ClockReminder clock, InventoryActivity inventory, String label) {
            this.state = state; this.target = target; this.scaffold = scaffold;
            this.clock = clock; this.inventory = inventory; this.label = label;
        }
    }

    private static final long TOAST_NANOS = 1_900_000_000L;
    private static final long WARNING_NANOS = 2_400_000_000L;
    private static final long WARNING_COOLDOWN_NANOS = 4_000_000_000L;
    private long epoch = Long.MIN_VALUE, lastNow, warningCooldownUntil;
    private boolean clockValid, warningSeen;
    private Event active;
    // Bounded idempotency window: no duplicate toasts during rapid toggle bursts.
    private static final int RECENT_EVENTS = 32;
    private final String[] recentEventIds = new String[RECENT_EVENTS];
    private int recentCursor, recentCount;

    private void clearRecent() {
        Arrays.fill(recentEventIds, null);
        recentCursor = recentCount = 0;
    }
    private boolean seen(String id) {
        for (int i = 0; i < recentCount; i++)
            if (id.equals(recentEventIds[i])) return true;
        return false;
    }
    private void remember(String id) {
        recentEventIds[recentCursor] = id;
        recentCursor = (recentCursor + 1) % RECENT_EVENTS;
        if (recentCount < RECENT_EVENTS) recentCount++;
    }

    private static final class Event {
        final State state;
        final String label;
        final long expires;
        Event(State state, String label, long expires) {
            this.state = state; this.label = label; this.expires = expires;
        }
    }

    private void enterEpoch(long next) {
        if (epoch == next) return;
        epoch = next; active = null; clearRecent();
        warningCooldownUntil = 0; lastNow = 0; clockValid = false; warningSeen = false;
        // Clock reminders remain deduplicated across scene changes. Inventory is scene-local.
        lastInventoryOffer=Long.MIN_VALUE;lastInventoryAction="";
    }
    private long time(long nanos) {
        if (!clockValid || nanos > lastNow) lastNow = nanos;
        clockValid = true;
        return lastNow;
    }

    private static final long CLOCK_NANOS = 4_500_000_000L;
    private static final long INVENTORY_NANOS = 5_000_000_000L;
    private static final long CLOCK_DEDUPE_NANOS = 10L * 60_000_000_000L; // 10 Minuten
    private long lastClockOffer = Long.MIN_VALUE;
    private long lastInventoryOffer = Long.MIN_VALUE;
    private String lastClockEventId = ""; // für Dedup im offer()
    private String lastClockTime = "";    // für Slot-Vergleich im resolve()
    private String lastInventoryAction = "";

    /**
     * Ein einziges sichtbares Event, kein unbounded Queue. Absichtlich nicht an
     * ModuleManager/Pakete angebunden bis ein zertifizierter Owner existiert.
     * CLOCK_REMINDER dedupliziert pro 10-Minuten-Slot, INVENTORY_ACTIVITY pro Aktion.
     */
    public boolean offer(State state, String eventId, String label, long sourceEpoch, long nowNanos) {
        enterEpoch(sourceEpoch);
        long now = time(nowNanos);
        if(active!=null&&now>=active.expires)active=null;
        if(active!=null&&eventPriority(active.state)>eventPriority(state))return false;
        if (state == State.CLOCK_REMINDER) {
            if(!new ClockReminder(label,false).valid())return false;
            if (eventId == null || eventId.trim().isEmpty()) return false;
            if (lastClockOffer != Long.MIN_VALUE && eventId.equals(lastClockEventId)
                    && now - lastClockOffer < CLOCK_DEDUPE_NANOS) return false;
            long ttl = CLOCK_NANOS;
            active = new Event(state, IslandLayout.limitText(label, 42), now + ttl);
            lastClockEventId = eventId;
            lastClockTime = label;
            lastClockOffer = now;
            return true;
        }
        if (state == State.INVENTORY_ACTIVITY) {
            if(label==null||label.trim().isEmpty())return false;
            if (eventId == null || eventId.trim().isEmpty() || seen(eventId)) return false;
            if (lastInventoryOffer != Long.MIN_VALUE && eventId.equals(lastInventoryAction)
                    && now - lastInventoryOffer < CLOCK_DEDUPE_NANOS) return false;
            long ttl = INVENTORY_NANOS;
            active = new Event(state, IslandLayout.limitText(label, 42), now + ttl);
            lastInventoryAction = eventId;
            lastInventoryOffer = now;
            remember(eventId);
            return true;
        }
        if (state != State.MODULE_TOAST && state != State.POSSIBLE_SETBACK) return false;
        if (eventId == null || eventId.trim().isEmpty() || seen(eventId)) return false;
        if (state == State.POSSIBLE_SETBACK && warningSeen && now < warningCooldownUntil) return false;
        if (active != null && active.expires > now && active.state == State.POSSIBLE_SETBACK
                && state == State.MODULE_TOAST) return false;
        long ttl = state == State.POSSIBLE_SETBACK ? WARNING_NANOS : TOAST_NANOS;
        active = new Event(state, state == State.POSSIBLE_SETBACK ? "Possible setback"
                : IslandLayout.limitText(label, 42), now + ttl);
        remember(eventId);
        if (state == State.POSSIBLE_SETBACK) { warningCooldownUntil = now + WARNING_COOLDOWN_NANOS; warningSeen = true; }
        return true;
    }

    /** Priority: safety correction > active event > fresh target > scaffold > clock > inventory > idle. */
    public Selection resolve(long sourceEpoch, long nowNanos, Target target, Scaffold scaffold,
                             ClockReminder clock, InventoryActivity inventory,
                             boolean showTarget, boolean showScaffold, boolean showClock, boolean showInventory) {
        enterEpoch(sourceEpoch);
        long now = time(nowNanos);
        if (active != null && now >= active.expires) active = null;
        if (active != null && (active.state != State.CLOCK_REMINDER || showClock)
                && (active.state != State.INVENTORY_ACTIVITY || showInventory)) {
            ClockReminder activeClock=active.state==State.CLOCK_REMINDER?new ClockReminder(active.label,false):null;
            InventoryActivity activeInventory=active.state==State.INVENTORY_ACTIVITY&&inventory!=null&&inventory.valid()?inventory:null;
            return new Selection(active.state,null,null,activeClock,activeInventory,active.label);
        }
        if (showTarget && target != null && target.valid())
            return new Selection(State.TARGET, target, null, null, null, target.name);
        if (showScaffold && scaffold != null && scaffold.valid())
            return new Selection(State.SCAFFOLD, null, scaffold, null, null, scaffold.approach);
        if (showClock && clock != null && clock.valid()
                && (lastClockTime.isEmpty() || now - lastClockOffer >= CLOCK_DEDUPE_NANOS || !clock.time.equals(lastClockTime)))
            return new Selection(State.CLOCK_REMINDER, null, null, clock, null, clock.time);
        if (showInventory && inventory != null && inventory.valid()
                && (lastInventoryOffer==Long.MIN_VALUE || (now>=lastInventoryOffer && now-lastInventoryOffer<INVENTORY_NANOS)))
            return new Selection(State.INVENTORY_ACTIVITY, null, null, null, inventory, inventory.action);
        return new Selection(State.IDLE, null, null, null, null, "");
    }

    /**
     * Legacy-Kompatibilität: alte Sechsparameter-Signatur ohne Clock und Inventory.
     * Delegiert an die erweiterte Signatur mit null für Clock/Inventory und false für deren Anzeige-Flags.
     * Bestehende Tests und Aufrufer bleiben unverändert kompilierbar und ausführbar.
     */
    public Selection resolve(long sourceEpoch, long nowNanos, Target target, Scaffold scaffold,
                             boolean showTarget, boolean showScaffold) {
        return resolve(sourceEpoch, nowNanos, target, scaffold, null, null, showTarget, showScaffold, false, false);
    }

    public int pendingEventCount() { return active == null ? 0 : 1; }
    public int rememberedEventCount() { return recentCount; }

    public void reset() {
        epoch = Long.MIN_VALUE; lastNow = 0; warningCooldownUntil = 0;
        active = null; clearRecent(); clockValid = false; warningSeen = false;
        lastClockOffer = Long.MIN_VALUE; lastInventoryOffer = Long.MIN_VALUE;
        lastClockEventId = ""; lastClockTime = ""; lastInventoryAction = "";
    }

    private static int eventPriority(State state) {
        if(state==State.POSSIBLE_SETBACK)return 4;
        if(state==State.MODULE_TOAST)return 3;
        if(state==State.CLOCK_REMINDER)return 2;
        if(state==State.INVENTORY_ACTIVITY)return 1;
        return 0;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
