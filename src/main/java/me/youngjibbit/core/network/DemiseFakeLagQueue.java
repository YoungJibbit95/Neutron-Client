package me.youngjibbit.core.network;

import me.youngjibbit.event.EventFlow;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Connection-owned BlinkComponent/LagUtils semantics from DemisePrivate e219a97 (AGPL-3.0).
 * Entries retain the pipeline continuation; they never resend through PRE or bypass its guards.
 */
public final class DemiseFakeLagQueue {
    public enum Mode { PULSE, SPOOF }
    public interface ReleaseListener { void released(Ticket ticket, boolean beforeTransport); }
    public static final int MAX_PACKETS = 1024;
    public static final long SPOOF_LEASE_MS = 100, MAX_AGE_MS = 2000;
    private static final Set<Class<?>> SPOOF_OUT = Collections.unmodifiableSet(new java.util.HashSet<Class<?>>(Arrays.asList(
            C00PacketKeepAlive.class, C0FPacketConfirmTransaction.class, C02PacketUseEntity.class,
            C0DPacketCloseWindow.class, C0EPacketClickWindow.class, C0CPacketInput.class,
            C0BPacketEntityAction.class, C08PacketPlayerBlockPlacement.class, C07PacketPlayerDigging.class,
            C09PacketHeldItemChange.class, C13PacketPlayerAbilities.class, C15PacketClientSettings.class,
            C16PacketClientStatus.class, C17PacketCustomPayload.class, C18PacketSpectate.class,
            C19PacketResourcePackStatus.class, C03PacketPlayer.class, C03PacketPlayer.C04PacketPlayerPosition.class,
            C03PacketPlayer.C05PacketPlayerLook.class, C03PacketPlayer.C06PacketPlayerPosLook.class, C0APacketAnimation.class)));
    private final LongSupplier clock;
    private final ArrayDeque<Ticket> waiting = new ArrayDeque<>();
    // A released continuation can still be waiting in the pre-existing bounded PacketDelays.
    private final Set<Ticket> outstanding = Collections.newSetFromMap(new IdentityHashMap<Ticket, Boolean>());
    private Request request, releasing;
    private long generation, lastNow = Long.MIN_VALUE;
    private boolean enabled, draining, closed;

    public DemiseFakeLagQueue(LongSupplier clock) { this.clock = java.util.Objects.requireNonNull(clock); }

    public static boolean matches(Mode mode, Packet<?> packet, EventFlow flow) {
        if (packet == null || flow == null) return false;
        if (mode == Mode.PULSE) return flow == EventFlow.OUTBOUND && !(packet instanceof C00PacketKeepAlive);
        return flow == EventFlow.INBOUND ? packet.getClass() == S1CPacketEntityMetadata.class
                : flow == EventFlow.OUTBOUND && SPOOF_OUT.contains(packet.getClass());
    }

    public void request(Object owner, Mode mode, int delayMs, BooleanSupplier sceneCurrent, ReleaseListener listener) {
        java.util.Objects.requireNonNull(owner); java.util.Objects.requireNonNull(mode);
        java.util.Objects.requireNonNull(sceneCurrent); java.util.Objects.requireNonNull(listener);
        if (closed) return;
        long now = now();
        if (request != null && (request.owner != owner || request.mode != mode)) release(request.owner, true);
        boolean continuing = request != null && request.owner == owner && request.mode == mode;
        request = new Request(owner, mode, Math.max(0, Math.min(1000, delayMs)), now, sceneCurrent, listener);
        // LagUtils.spoof renews its lease; the next source Update enables its packet listener.
        if (mode == Mode.PULSE) enabled = true;
        else if (!continuing) enabled = false;
    }

    /** The global LagUtils Update runs before the module's own Update. */
    public void sourceUpdate(Object owner) {
        pump();
        Request current = request;
        if (current == null || current.owner != owner || current.mode != Mode.SPOOF) return;
        enabled = true;
        drain(false, current.owner, now(), current.delayMs);
    }

    /** Lease/age safety only. Timed Spoof releases retain the source's ordinary Update phase. */
    public void pump() {
        if (closed) return;
        long now = now();
        Request current = request;
        if (current != null && !current.sceneCurrent.getAsBoolean()) { discard(); return; }
        if (current != null && current.mode == Mode.SPOOF && now - current.renewed > SPOOF_LEASE_MS * 1000000L) {
            release(current.owner, true);
        }
        Ticket first = waiting.peekFirst();
        if (first != null && now - first.created > MAX_AGE_MS * 1000000L) {
            Object owner = first.owner;
            release(owner, true);
        }
        // A handler may leave its PacketThreadUtil continuation scheduled. It
        // cannot retain an owned envelope indefinitely even if that task is lost.
        java.util.Iterator<Ticket> pending = outstanding.iterator();
        while (pending.hasNext()) {
            Ticket ticket = pending.next();
            if (ticket.awaitingApplication && now - ticket.created > MAX_AGE_MS * 1000000L) {
                ticket.discarded = true; pending.remove();
            }
        }
    }

    /** Called after final PRE selection, only for non-silent packets on this play connection. */
    public boolean defer(Packet<?> packet, EventFlow flow, BooleanSupplier invocationCurrent, Consumer<Ticket> continuation) {
        pump();
        Request current = enabled ? request : draining ? releasing : null;
        if (closed || current == null || !matches(current.mode, packet, flow)) return false;
        if (!current.sceneCurrent.getAsBoolean() || !invocationCurrent.getAsBoolean()) { discard(); return false; }
        if (waiting.size() >= MAX_PACKETS) {
            drain(true, null, now(), 0);
            // A release observer may renew the same owner or change its mode. Take the
            // factual current request after that callback, before appending this packet.
            current = enabled ? request : draining ? releasing : null;
        }
        if (waiting.size() >= MAX_PACKETS || outstanding.size() >= MAX_PACKETS * 2) {
            discard(); throw new IllegalStateException("FakeLag queue exceeded its reentrant release bound");
        }
        if (closed || current == null || !matches(current.mode, packet, flow)
                || !(enabled && request == current || draining && releasing == current)) return false;
        if (!current.sceneCurrent.getAsBoolean() || !invocationCurrent.getAsBoolean()) { discard(); return false; }
        Ticket ticket = new Ticket(current, packet, flow, now(), generation, invocationCurrent, continuation);
        waiting.addLast(ticket); outstanding.add(ticket);
        return true;
    }

    /** Normal disable/criteria loss publishes only still-current entries; lifecycle loss calls discard. */
    public void release(Object owner, boolean send) {
        if (request != null && request.owner == owner) { request = null; enabled = false; }
        if (send) drain(true, owner, now(), 0);
        else discardPending(owner, null);
    }

    /** Early Spoof release drops the remaining source queue, while its currently releasing packet survives. */
    public void discardPending(Object owner, Ticket current) {
        if (request != null && request.owner == owner) { request = null; enabled = false; }
        for (Ticket ticket : new java.util.ArrayList<>(outstanding)) {
            if (ticket.owner == owner && ticket != current) { ticket.discarded = true; outstanding.remove(ticket); }
        }
        waiting.removeIf(ticket -> ticket.discarded);
    }

    private void drain(boolean force, Object owner, long now, int delayMs) {
        if (draining || closed) return;
        draining = true;
        long entered = generation;
        try {
            for (int i = 0; i < MAX_PACKETS && generation == entered; i++) {
                Ticket ticket = waiting.peekFirst();
                if (ticket == null || owner != null && ticket.owner != owner) return;
                if (!force && now - ticket.created <= delayMs * 1000000L) return;
                waiting.removeFirst();
                if (!ticket.current()) { ticket.finish(); continue; }
                // LagUtils.dispatch publishes without PacketReleaseEvent; only
                // timed Spoof release observes the position before transport.
                ticket.observeRelease = !force || ticket.mode == Mode.PULSE;
                releasing = ticket.source;
                ticket.continuation.accept(ticket);
            }
        } catch (RuntimeException | Error failure) {
            discard(); throw failure;
        } finally { releasing = null; draining = false; }
    }

    private long now() {
        long now = clock.getAsLong();
        if (lastNow != Long.MIN_VALUE && now < lastNow) discard();
        lastNow = now;
        return now;
    }
    public void discard() {
        generation++; request = null; enabled = false;
        for (Ticket ticket : outstanding) ticket.discarded = true;
        waiting.clear(); outstanding.clear();
    }
    public void close() { closed = true; discard(); }
    public int size() { return waiting.size(); }
    public boolean owns(Object owner) { return !closed && request != null && request.owner == owner; }
    public boolean holding(Object owner) {
        for (Ticket ticket : waiting) if (ticket.owner == owner && !ticket.discarded) return true;
        return false;
    }

    public final class Ticket {
        public final Packet<?> packet;
        public final EventFlow flow;
        public final Mode mode;
        public final long created;
        private final Object owner;
        private final Request source;
        private final long capturedGeneration;
        private final BooleanSupplier invocationCurrent, sceneCurrent;
        private final ReleaseListener listener;
        private final Consumer<Ticket> continuation;
        private boolean discarded, beforeCalled, afterCalled, observeRelease, awaitingApplication;
        private Ticket(Request request, Packet<?> packet, EventFlow flow, long created, long generation,
                       BooleanSupplier invocationCurrent, Consumer<Ticket> continuation) {
            source = request; owner = request.owner; mode = request.mode; sceneCurrent = request.sceneCurrent; listener = request.listener;
            this.packet = packet; this.flow = flow; this.created = created; capturedGeneration = generation;
            this.invocationCurrent = invocationCurrent; this.continuation = continuation;
        }
        boolean current() {
            boolean current = !closed && !discarded && generation == capturedGeneration
                    && (!awaitingApplication || clock.getAsLong() - created <= MAX_AGE_MS * 1000000L)
                    && sceneCurrent.getAsBoolean() && invocationCurrent.getAsBoolean()
                    && !discarded && generation == capturedGeneration;
            if (!current) finish();
            return current;
        }
        void awaitApplication() { awaitingApplication = true; }
        boolean beforeApply() {
            if (!current()) return false;
            if (mode == Mode.SPOOF && observeRelease && !beforeCalled) {
                beforeCalled = true; listener.released(this, true);
            }
            return current();
        }
        void accepted() {
            if (mode == Mode.PULSE && !afterCalled && current()) {
                afterCalled = true; listener.released(this, false);
            }
        }
        void finish() { discarded = true; outstanding.remove(this); }
    }
    private static final class Request {
        final Object owner;
        final Mode mode;
        final int delayMs;
        final long renewed;
        final BooleanSupplier sceneCurrent;
        final ReleaseListener listener;
        Request(Object owner, Mode mode, int delayMs, long renewed, BooleanSupplier sceneCurrent, ReleaseListener listener) {
            this.owner = owner; this.mode = mode; this.delayMs = delayMs; this.renewed = renewed;
            this.sceneCurrent = sceneCurrent; this.listener = listener;
        }
    }
}
