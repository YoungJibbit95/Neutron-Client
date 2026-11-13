package me.youngjibbit.core.module;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Module;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.function.Consumer;

/**
 * Opt-in interruption guards. The default policy disables its owner; consumers
 * with an abort callback retain their activation and discard only current work.
 */
public final class ModuleActionSafety implements Subscriber {
    private static final Set<ModuleActionSafety> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static long nextOrdinal;
    private final Module owner;
    private final Supplier<Minecraft> client;
    private final Consumer<String> abortOperation;
    private final CheckBoxValue stopOnDamage, exclusive;
    private boolean active;
    private long ordinal;
    private Object world;
    private EntityPlayerSP player;
    private PacketPipeline pipeline;
    private long contextVersion;
    private float health = Float.NaN;
    private boolean interrupting, interrupted, unloading;
    private long tickSequence, interruptedAtTick;

    public ModuleActionSafety(Module owner, Supplier<Minecraft> client) {
        this(owner, client, null);
    }

    public ModuleActionSafety(Module owner, Supplier<Minecraft> client, Consumer<String> abortOperation) {
        this.owner = owner;
        this.client = client;
        this.abortOperation = abortOperation;
        // Keep the historical ID for existing profiles while describing the selected policy.
        stopOnDamage = new CheckBoxValue("Stop on Damage", owner, false) {
            @Override public String getName() {
                return abortOperation == null ? super.getName() : "Abort on Damage";
            }
        };
        exclusive = new CheckBoxValue("Exclusive Timing", owner, false);
        stopOnDamage.setOnChange(value -> primeHealth());
    }

    public void enable() {
        disable();
        active = true;
        synchronized (ACTIVE) { ordinal = ++nextOrdinal; ACTIVE.add(this); }
        primeHealth();
        // Do not disable recursively during onEnable: the owner's subscription
        // is still being established. Every action has its own preflight below.
        Pulsar.BUS.subscribe(this);
    }

    public void disable() {
        active = false;
        Pulsar.BUS.unsubscribe(this);
        synchronized (ACTIVE) { ACTIVE.remove(this); }
        interrupted = unloading = false;
        clearContext();
    }

    private void clearContext() {
        world = null; player = null; pipeline = null; health = Float.NaN; contextVersion = 0;
    }

    public String canonicalName(String name) {
        return "Stop on Damage".equalsIgnoreCase(name) || "Abort on Damage".equalsIgnoreCase(name)
                ? "stop_on_damage" : name;
    }

    public void resetConfigDefaults() { stopOnDamage.reset(); exclusive.reset(); }

    /** Establish the baseline at a live opt-in, before the next health packet can arrive. */
    private void primeHealth() {
        if (!active || !owner.isToggled()) { health = Float.NaN; return; }
        if (unloading || interrupting) return;
        Minecraft current = client.get();
        if (current == null || !current.isCallingFromMinecraftThread()) return;
        player = current.thePlayer; world = current.theWorld; pipeline = pipeline(current);
        contextVersion = pipeline == null ? 0 : pipeline.getContextVersion();
        health = stopOnDamage.getValue() && player != null && world != null && player.worldObj == world
                ? player.getHealth() : Float.NaN;
    }

    /** Also called directly before work, so queued/reentrant events cannot act after a stop. */
    public boolean allows() {
        if (!active || !owner.isToggled() || interrupting || unloading) return false;
        Minecraft current = client.get();
        if (current == null || !current.isCallingFromMinecraftThread()) return false;
        boolean conflict = false;
        synchronized (ACTIVE) {
            for (ModuleActionSafety other : ACTIVE) {
                if (other != this && other.active && other.owner.isToggled() && other.ordinal < ordinal
                        && other.client.get() == current && (exclusive.getValue() || other.exclusive.getValue())) {
                    conflict = true;
                    break;
                }
            }
        }
        if (conflict) return stop("timing_conflict");
        EntityPlayerSP local = current.thePlayer;
        PacketPipeline connection = pipeline(current);
        long version = connection == null ? 0 : connection.getContextVersion();
        if (player != local || world != current.theWorld || pipeline != connection || contextVersion != version) {
            player = local; world = current.theWorld; pipeline = connection; health = Float.NaN;
            contextVersion = version;
        }
        if (!stopOnDamage.getValue()) health = Float.NaN;
        else if (local != null && world != null && local.worldObj == world) {
            float observedHealth = local.getHealth();
            boolean damaged = local.isDead || local.hurtTime > 0 || Float.isFinite(observedHealth)
                    && (observedHealth <= 0 || Float.isFinite(health) && observedHealth < health);
            health = observedHealth;
            if (damaged) return stop("damage");
        }
        // An edge-only S06 decrease may have no hurt animation. Do not reopen work
        // from lower-priority listeners or nested callbacks of that same tick.
        if (interrupted && tickSequence <= interruptedAtTick) return false;
        interrupted = false;
        return true;
    }

    private boolean stop(String reason) {
        if (abortOperation != null) {
            if (interrupted || interrupting) return false;
            interrupted = true;
            interruptedAtTick = tickSequence;
            interrupting = true;
            try { abortOperation.accept(reason); }
            finally {
                // Cleanup can manually disable/re-enable this owner. That is a
                // new activation, but it still cannot reuse the interrupted event.
                if (active && owner.isToggled()) {
                    interrupted = true;
                    interruptedAtTick = tickSequence;
                }
                interrupting = false;
            }
            return false;
        }
        // Normal onDisable releases that owner's existing state. Detach first
        // because cleanup can synchronously publish packet and update events.
        disable();
        if (owner.isToggled()) owner.setToggled(false);
        return false;
    }

    private static PacketPipeline pipeline(Minecraft current) {
        return current.thePlayer == null || current.thePlayer.sendQueue == null ? null
                : current.thePlayer.sendQueue.getNetworkManager().getClientPackets();
    }

    @Subscribe private final Listener<TickEvent> tick = new Listener<>(event -> {
        if (event.isPre() && !interrupting) tickSequence++;
        allows();
    }, EventPriority.HIGHEST);
    @Subscribe private final Listener<EventUpdate> update = new Listener<>(event -> allows(), EventPriority.HIGHEST);
    @Subscribe private final Listener<EventRotation> rotation = new Listener<>(event -> allows(), EventPriority.HIGHEST);
    private void appliedPacket(EventPacket event) {
        Minecraft current = client.get();
        // Only completed application on this exact connection is evidence.
        if (event.isPost() && event.isInbound() && current != null && event.getPipeline() == pipeline(current)) allows();
    }
    @Subscribe private final Listener<EventPacket> packet = new Listener<>(this::appliedPacket, EventPriority.HIGHEST);
    private void sceneChanged(WorldEvent event) {
        if (abortOperation == null || !active) return;
        // The owner receives the same event and clears its operation. Unload runs
        // while Minecraft still exposes the old world, so preflights stay closed.
        clearContext();
        unloading = event instanceof WorldEvent.Unload;
        interrupted = true;
        interruptedAtTick = tickSequence;
    }
    @Subscribe private final Listener<WorldEvent> scene = new Listener<>(this::sceneChanged, EventPriority.HIGHEST);
}
