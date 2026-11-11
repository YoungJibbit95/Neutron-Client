package me.youngjibbit.core.module;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.update.EventUpdate;
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

/**
 * Restrictive opt-in stops for the three timing modules. No packet scheduling,
 * hit prediction, automatic restart, or changes to another module's work.
 */
public final class ModuleActionSafety implements Subscriber {
    private static final Set<ModuleActionSafety> ACTIVE = Collections.newSetFromMap(new WeakHashMap<>());
    private static long nextOrdinal;
    private final Module owner;
    private final Supplier<Minecraft> client;
    private final CheckBoxValue stopOnDamage, exclusive;
    private boolean active;
    private long ordinal;
    private Object world;
    private EntityPlayerSP player;
    private PacketPipeline pipeline;
    private long contextVersion;
    private float health = Float.NaN;

    public ModuleActionSafety(Module owner, Supplier<Minecraft> client) {
        this.owner = owner;
        this.client = client;
        stopOnDamage = new CheckBoxValue("Stop on Damage", owner, false);
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
        world = null; player = null; pipeline = null; health = Float.NaN; contextVersion = 0;
    }

    public void resetConfigDefaults() { stopOnDamage.reset(); exclusive.reset(); }

    /** Establish the baseline at a live opt-in, before the next health packet can arrive. */
    private void primeHealth() {
        if (!active || !owner.isToggled()) { health = Float.NaN; return; }
        Minecraft current = client.get();
        if (current == null || !current.isCallingFromMinecraftThread()) return;
        player = current.thePlayer; world = current.theWorld; pipeline = pipeline(current);
        contextVersion = pipeline == null ? 0 : pipeline.getContextVersion();
        health = stopOnDamage.getValue() && player != null && world != null && player.worldObj == world
                ? player.getHealth() : Float.NaN;
    }

    /** Also called directly before work, so queued/reentrant events cannot act after a stop. */
    public boolean allows() {
        if (!active || !owner.isToggled()) return false;
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
        if (conflict) return stop();
        EntityPlayerSP local = current.thePlayer;
        PacketPipeline connection = pipeline(current);
        long version = connection == null ? 0 : connection.getContextVersion();
        if (player != local || world != current.theWorld || pipeline != connection || contextVersion != version) {
            player = local; world = current.theWorld; pipeline = connection; health = Float.NaN;
            contextVersion = version;
        }
        if (!stopOnDamage.getValue()) { health = Float.NaN; return true; }
        if (local == null || world == null || local.worldObj != world) return true;
        float observedHealth = local.getHealth();
        boolean damaged = local.isDead || local.hurtTime > 0 || Float.isFinite(observedHealth)
                && (observedHealth <= 0 || Float.isFinite(health) && observedHealth < health);
        health = observedHealth;
        return damaged ? stop() : true;
    }

    private boolean stop() {
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

    @Subscribe private final Listener<TickEvent> tick = new Listener<>(event -> allows(), EventPriority.HIGHEST);
    @Subscribe private final Listener<EventUpdate> update = new Listener<>(event -> allows(), EventPriority.HIGHEST);
    @Subscribe private final Listener<EventRotation> rotation = new Listener<>(event -> allows(), EventPriority.HIGHEST);
    private void appliedPacket(EventPacket event) {
        Minecraft current = client.get();
        // Only completed application on this exact connection is evidence.
        if (event.isPost() && event.isInbound() && current != null && event.getPipeline() == pipeline(current)) allows();
    }
    @Subscribe private final Listener<EventPacket> packet = new Listener<>(this::appliedPacket, EventPriority.HIGHEST);
}
