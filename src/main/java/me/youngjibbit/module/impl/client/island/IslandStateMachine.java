package me.youngjibbit.module.impl.client.island;

import java.util.Locale;
import java.util.Arrays;

/**
 * Pure, bounded view state. Callers supply already validated, immutable observations.
 * Scaffold, module lifecycle and correction providers are DATA_PROVIDER_PENDING; tests
 * may inject fixtures, but rendering is never a source of gameplay/network events.
 */
public final class IslandStateMachine {
    public enum State {
        IDLE, TARGET, SCAFFOLD, MODULE_TOAST, POSSIBLE_SETBACK;
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

    /** Fixture-only until TECH approves a read-only block/slot source. */
    public static final class Scaffold {
        public final boolean active;
        public final int blockCount;
        public final String approach;
        public Scaffold(boolean active, int blockCount, String approach) {
            this.active = active; this.blockCount = blockCount;
            this.approach = approach == null ? "" : approach;
        }
        public boolean valid() { return active && blockCount >= 0; }
    }

    public static final class Selection {
        public final State state;
        public final Target target;
        public final Scaffold scaffold;
        public final String label;
        private Selection(State state, Target target, Scaffold scaffold, String label) {
            this.state = state; this.target = target; this.scaffold = scaffold; this.label = label;
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
    }
    private long time(long nanos) {
        if (!clockValid || nanos > lastNow) lastNow = nanos;
        clockValid = true;
        return lastNow;
    }

    /**
     * A single visible event, not an unbounded queue. This is intentionally not
     * connected to ModuleManager or packets until a certified owner is available.
     */
    public boolean offer(State state, String eventId, String label, long sourceEpoch, long nowNanos) {
        enterEpoch(sourceEpoch);
        long now = time(nowNanos);
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

    /** Priority: correction > toast > fresh target > explicit scaffold fixture > idle. */
    public Selection resolve(long sourceEpoch, long nowNanos, Target target, Scaffold scaffold,
                             boolean showTarget, boolean showScaffold) {
        enterEpoch(sourceEpoch);
        long now = time(nowNanos);
        if (active != null && now >= active.expires) active = null;
        if (active != null) return new Selection(active.state, null, null, active.label);
        if (showTarget && target != null && target.valid())
            return new Selection(State.TARGET, target, null, target.name);
        if (showScaffold && scaffold != null && scaffold.valid())
            return new Selection(State.SCAFFOLD, null, scaffold, scaffold.approach);
        return new Selection(State.IDLE, null, null, "");
    }

    public int pendingEventCount() { return active == null ? 0 : 1; }
    public int rememberedEventCount() { return recentCount; }

    public void reset() {
        epoch = Long.MIN_VALUE; lastNow = 0; warningCooldownUntil = 0;
        active = null; clearRecent(); clockValid = false; warningSeen = false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
