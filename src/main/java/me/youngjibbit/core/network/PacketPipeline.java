package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.CombatDiagnosticTrace.PacketToken;
import me.youngjibbit.core.combat.CombatDiagnosticTrace.Stage;
import me.youngjibbit.module.impl.client.CombatDiagnostics;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.event.EventPhase;
import net.minecraft.network.*;
import net.minecraft.util.IThreadListener;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;
import java.util.ArrayDeque;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** One play connection. Transport hands off here; module callbacks and application run on the client thread. */
public final class PacketPipeline {
    private static final int MAX_HANDOFF = 4096;
    private final IThreadListener thread;
    private final BooleanSupplier connected;
    private final Consumer<Packet<?>> acceptedOutbound;
    private final Consumer<Throwable> failure;
    private final Consumer<Packet<?>> appliedInbound;
    private final ArrayDeque<Handoff> handoff = new ArrayDeque<>();
    private final PacketDelays delays;
    private final ReceivedEntityPositions receivedPositions = new ReceivedEntityPositions();
    private boolean scheduled;
    private boolean draining, worldBoundary, correction;
    private volatile long appliedScene;
    private volatile long contextVersion;
    private long receivedScene;
    private volatile boolean closed;
    private final java.util.concurrent.atomic.AtomicBoolean closing = new java.util.concurrent.atomic.AtomicBoolean();

    public PacketPipeline(IThreadListener thread, BooleanSupplier connected,
                          Consumer<Packet<?>> acceptedOutbound, Consumer<Throwable> failure) {
        this(thread, connected, acceptedOutbound, failure, new PacketDelays());
    }
    public PacketPipeline(IThreadListener thread, BooleanSupplier connected,
                          Consumer<Packet<?>> acceptedOutbound, Consumer<Throwable> failure, PacketDelays delays) {
        this(thread,connected,acceptedOutbound,failure,delays,packet -> { });
    }
    public PacketPipeline(IThreadListener thread, BooleanSupplier connected,
                          Consumer<Packet<?>> acceptedOutbound, Consumer<Throwable> failure, PacketDelays delays,
                          Consumer<Packet<?>> appliedInbound) {
        this.thread = thread; this.connected = connected;
        this.acceptedOutbound = acceptedOutbound; this.failure = failure;
        this.delays = delays;
        this.appliedInbound = appliedInbound;
    }

    public boolean isActive() { return !closed && connected.getAsBoolean(); }

    /** A flag belongs to this invocation, never to a packet object's future uses. */
    public Packet<?> outbound(Packet<?> packet, boolean silent, Consumer<Packet<?>> transport) {
        return outbound(packet, silent, transport, () -> true);
    }
    public Packet<?> outbound(Packet<?> packet, boolean silent, Consumer<Packet<?>> transport, BooleanSupplier validAction) {
        return outboundSelected(packet, silent, transport, selected -> validAction.getAsBoolean());
    }
    public Packet<?> outbound(Packet<?> packet, boolean silent, Consumer<Packet<?>> transport, Predicate<Packet<?>> validAction) {
        return outboundSelected(packet, silent, transport, validAction);
    }
    public Packet<?> outboundSelected(Packet<?> packet, boolean silent, Consumer<Packet<?>> transport, Predicate<Packet<?>> validAction) {
        if (!thread.isCallingFromMinecraftThread()) {
            long scene = appliedScene;
            execute(() -> { if(scene == appliedScene) outboundSelected(packet, silent, transport, validAction); }, true);
            return null; // scheduled is not yet accepted
        }
        long attemptedScene = appliedScene;
        if (!draining) drainOutbound();
        if (!isActive() || packet == null || attemptedScene != appliedScene) return null;
        PacketToken trace = CombatDiagnostics.beginPacket(packet);
        final Packet<?> selected;
        try { selected = select(packet, EventFlow.OUTBOUND, silent); }
        catch (RuntimeException | Error failure) {
            CombatDiagnostics.packetStage(trace,packet,Stage.FAILED);throw failure;
        }
        if (selected == null) { CombatDiagnostics.packetStage(trace,packet,Stage.CANCELLED);return null; }
        CombatDiagnostics.packetStage(trace,selected,Stage.SELECTED);
        if (!isActive() || attemptedScene != appliedScene) { CombatDiagnostics.packetStage(trace,selected,Stage.DENIED);return null; }
        long scene = appliedScene;
        boolean[] accepted = {false};
        Runnable apply = () -> {
            final boolean valid;
            try { valid = isActive() && scene == appliedScene && validAction.test(selected); }
            catch (RuntimeException | Error failure) {
                CombatDiagnostics.packetStage(trace,selected,Stage.FAILED);throw failure;
            }
            if (!valid) {
                CombatDiagnostics.packetStage(trace,selected,Stage.DENIED);return;
            }
            try { transport.accept(selected); }
            catch (RuntimeException | Error failure) {
                CombatDiagnostics.packetStage(trace,selected,Stage.FAILED);throw failure;
            }
            accepted[0] = true;
            CombatDiagnostics.packetStage(trace,selected,Stage.LOCAL_ACCEPTED);
            acceptedOutbound.accept(selected);
            if (!silent) post(selected, EventFlow.OUTBOUND, EventPhase.POST);
        };
        // Actions are dependency barriers: a hit/placement must never overtake withheld movement.
        if (correction || !(selected instanceof C03PacketPlayer)) {
            CombatDiagnostics.packetStage(trace,selected,Stage.BARRIER);delays.barrier(EventFlow.OUTBOUND);
        } else if (!silent && delays.defer(selected, EventFlow.OUTBOUND, apply)) {
            CombatDiagnostics.packetStage(trace,selected,Stage.HELD);return null;
        } else if (silent) {
            CombatDiagnostics.packetStage(trace,selected,Stage.BARRIER);delays.barrier(EventFlow.OUTBOUND);
        }
        apply.run();
        return accepted[0] ? selected : null;
    }

    public void inbound(Packet<?> packet, INetHandler handler, boolean silent) {
        inbound(packet, handler, silent, () -> true);
    }
    public void inbound(Packet<?> packet, INetHandler handler, boolean silent, BooleanSupplier handlerValid) {
        boolean boundary = packet instanceof S01PacketJoinGame || packet instanceof S07PacketRespawn;
        final long scene;
        synchronized (handoff) { scene = boundary ? ++receivedScene : receivedScene; }
        execute(() -> {
            if (!isActive() || !handlerValid.getAsBoolean()) return;
            if (boundary) { appliedScene = scene; discardDelayed(); }
            else if (scene != appliedScene) return;
            boolean previousBoundary = worldBoundary, previousCorrection = correction;
            worldBoundary = boundary;
            try {
                // Corrections/lifecycle must discard obsolete movement before callbacks can release it.
                if (packet instanceof S08PacketPlayerPosLook || packet instanceof S40PacketDisconnect
                        || packet instanceof S06PacketUpdateHealth && ((S06PacketUpdateHealth)packet).getHealth() <= 0) discardDelayed();
                long attemptedScene = appliedScene;
                Packet<?> selected = select(packet, EventFlow.INBOUND, silent);
                if (selected == null || !isActive() || !handlerValid.getAsBoolean() || attemptedScene != appliedScene) return;
                // Listeners may replace a movement packet with a correction/lifecycle packet.
                if (selected instanceof S08PacketPlayerPosLook || selected instanceof S40PacketDisconnect
                        || selected instanceof S01PacketJoinGame || selected instanceof S07PacketRespawn
                        || selected instanceof S06PacketUpdateHealth && ((S06PacketUpdateHealth)selected).getHealth() <= 0) discardDelayed();
                if (!boundary && (selected instanceof S01PacketJoinGame || selected instanceof S07PacketRespawn)) {
                    worldChanged(); // a replacement changed the scene boundary; queued old-scene envelopes are obsolete
                    worldBoundary = true;
                }
                correction = selected instanceof S08PacketPlayerPosLook;
                final long applicationScene = appliedScene;
                BooleanSupplier entityIdentity = handler instanceof net.minecraft.client.network.NetHandlerPlayClient
                        ? ((net.minecraft.client.network.NetHandlerPlayClient) handler).captureEntityIdentity(selected) : () -> true;
                Runnable positionApplied = handler instanceof net.minecraft.client.network.NetHandlerPlayClient
                        ? receivedPositions.receive(((net.minecraft.client.network.NetHandlerPlayClient) handler).clientWorldController, selected)
                        : () -> { };
                if (!silent) Pulsar.BUS.post(new me.youngjibbit.event.packet.PacketReceiveEvent(selected, this));
                if (!isActive() || !handlerValid.getAsBoolean() || applicationScene != appliedScene) return;
                Runnable apply = () -> {
                    if (selected instanceof S08PacketPlayerPosLook || selected instanceof S07PacketRespawn
                            || selected instanceof S06PacketUpdateHealth && ((S06PacketUpdateHealth)selected).getHealth() <= 0) {
                        discardDelayed();
                        me.youngjibbit.core.combat.RotationManager rotation=me.youngjibbit.core.bootstrap.RuntimeServices.rotation();
                        if (rotation.isConnection(this)) rotation.reset();
                    }
                    PacketApplication.process(selected, handler,
                        () -> isActive() && handlerValid.getAsBoolean() && entityIdentity.getAsBoolean()
                                && applicationScene == appliedScene, () -> {
                            positionApplied.run();
                            appliedInbound.accept(selected);
                            if (!silent) post(selected, EventFlow.INBOUND, EventPhase.POST);
                        });
                };
                if (selected instanceof S14PacketEntity && !silent) {
                    if (delays.defer(selected, EventFlow.INBOUND, apply)) return;
                } else if (!(selected instanceof S00PacketKeepAlive)
                        && !(selected instanceof S02PacketChat) && !(selected instanceof S03PacketTimeUpdate)) {
                    delays.barrier(EventFlow.INBOUND);
                }
                apply.run();
            } finally { worldBoundary = previousBoundary; correction = previousCorrection; }
        }, false);
    }

    public PacketDelays delays() { return delays; }
    /** Client-thread receipt snapshot, including movement queued before a consumer selected this exact entity. */
    public net.minecraft.util.Vec3 receivedPosition(net.minecraft.entity.Entity entity) {
        if (!thread.isCallingFromMinecraftThread()) throw new IllegalStateException("Position snapshot requires client thread");
        return isActive() ? receivedPositions.position(entity) : null;
    }
    /** Invalidates action evidence even when silent application intentionally omits packet events. */
    public long getContextVersion() { return contextVersion; }
    private void discardDelayed() { contextVersion++; delays.discard(); receivedPositions.clear(); }
    public void pump() {
        if (!thread.isCallingFromMinecraftThread()) throw new IllegalStateException("Packet pump requires client thread");
        if (!isActive()) { discardDelayed(); return; }
        try { delays.pump(); }
        catch (Throwable error) { close();failure.accept(error); }
    }
    public void worldChanged() {
        if (!thread.isCallingFromMinecraftThread()) throw new IllegalStateException("World change requires client thread");
        discardDelayed();
        if (!worldBoundary) synchronized (handoff) {
            appliedScene = ++receivedScene;
            handoff.clear();
        }
    }

    private Packet<?> select(Packet<?> packet, EventFlow flow, boolean silent) {
        if (packet == null) return null;
        Packet<?> selected = packet;
        if (!silent) {
            EventPacket event = post(packet, flow, EventPhase.PRE);
            selected = event.isCancelled() ? null : event.getPacket();
        }
        if (selected != null && EnumConnectionState.PLAY.getPacketId(flow == EventFlow.INBOUND
                ? EnumPacketDirection.CLIENTBOUND : EnumPacketDirection.SERVERBOUND, (Packet) selected) == null)
            throw new IllegalArgumentException("Packet replacement has the wrong play direction: " + selected.getClass().getName());
        return selected;
    }

    private EventPacket post(Packet<?> packet, EventFlow flow, EventPhase phase) {
        EventPacket event = new EventPacket(packet, this);
        event.setEventFlow(flow); event.setEventPhase(phase);
        Pulsar.BUS.post(event);
        return event;
    }

    private void execute(Runnable operation, boolean outbound) {
        if (thread.isCallingFromMinecraftThread()) {
            if (!draining) drain();
            if (isActive()) operation.run();
            return;
        }
        boolean start = false;
        synchronized (handoff) {
            if (closed) return;
            if (handoff.size() >= MAX_HANDOFF) {
                close();
                failure.accept(new IllegalStateException("Client packet handoff exceeded " + MAX_HANDOFF));
                return;
            }
            handoff.add(new Handoff(operation, delays.now(), outbound));
            if (!scheduled) { scheduled = true; start = true; }
        }
        if (start) thread.addScheduledTask(this::drain);
    }

    /** Preserve outgoing order without applying an incoming world switch in the middle of a player update. */
    private void drainOutbound() {
        draining = true;
        try {
            for (int i=0;i<MAX_HANDOFF;i++) {
                Handoff next = null;
                synchronized(handoff) {
                    java.util.Iterator<Handoff> pending = handoff.iterator();
                    while(pending.hasNext()) {
                        Handoff candidate = pending.next();
                        if(candidate.outbound) {next=candidate;pending.remove();break;}
                    }
                }
                if(next==null)return;
                if(delays.now()-next.created>5000000000L)throw new IllegalStateException("Client packet handoff expired");
                if(isActive())next.operation.run();
            }
        } catch(Throwable error) {
            close();failure.accept(error);
        } finally {draining=false;}
    }

    private void drain() {
        if (draining) return;
        draining = true;
        try {
            for (int i = 0; i < MAX_HANDOFF; i++) {
                Handoff next;
                synchronized (handoff) {
                    next = handoff.poll();
                    if (next == null) { scheduled = false; return; }
                }
                if (delays.now() - next.created > 5000000000L)
                    throw new IllegalStateException("Client packet handoff expired");
                if (isActive()) next.operation.run();
            }
        } catch (Throwable error) {
            close(); failure.accept(error); return;
        } finally {
            draining = false;
        }
        synchronized (handoff) {
            scheduled = false;
            if (!handoff.isEmpty() && !closed) {
                close();
                failure.accept(new IllegalStateException("Client packet handoff exceeded per-frame work bound"));
            }
        }
    }

    public void close() {
        if (!closing.compareAndSet(false,true)) return;
        closed = true;
        Runnable ended = () -> {
            receivedPositions.clear();
            me.youngjibbit.core.bootstrap.RuntimeServices.rotation().invalidateConnection(this);
        };
        if (thread.isCallingFromMinecraftThread()) ended.run(); else thread.addScheduledTask(ended);
        delays.close();
        synchronized (handoff) { handoff.clear(); }
    }
    private static final class Handoff {
        final Runnable operation; final long created; final boolean outbound;
        Handoff(Runnable operation,long created,boolean outbound) {this.operation=operation;this.created=created;this.outbound=outbound;}
    }
}
