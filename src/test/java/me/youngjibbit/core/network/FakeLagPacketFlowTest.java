package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.EventFlow;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** Real PRE -> dedicated hold -> original delay/barrier -> guarded transport -> POST integration. */
public class FakeLagPacketFlowTest {
    private static final class Fixture implements AutoCloseable {
        final AtomicLong now = new AtomicLong();
        final Object owner = new Object();
        final List<Packet<?>> sent = new ArrayList<>(), applied = new ArrayList<>();
        final List<Throwable> failures = new ArrayList<>();
        final NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer();
        final PacketPipeline pipe = new PacketPipeline(new NetworkTestSupport.MainThread(), () -> true,
                sent::add, failures::add, new PacketDelays(now::get));
        Fixture() { Pulsar.BUS.subscribe(observer); }
        void request(DemiseFakeLagQueue.Mode mode, int delay, DemiseFakeLagQueue.ReleaseListener listener) {
            pipe.fakeLag().request(owner, mode, delay, () -> true, listener);
        }
        Packet<?> send(Packet<?> packet) { return pipe.outbound(packet, false, p -> { }); }
        void inbound(Packet<?> packet) { pipe.inbound(packet, NetworkTestSupport.handler(a -> applied.add((Packet<?>) a[0])), false); }
        void at(long millis) { now.set(millis * 1_000_000L); }
        @Override public void close() { Pulsar.BUS.unsubscribe(observer); pipe.close(); }
    }

    @Test public void pulseHoldsActionsChatAndMovementButKeepAliveAndSilentInvocationsRemainIndependent() {
        try (Fixture f = new Fixture()) {
            List<Packet<?>> released = new ArrayList<>();
            f.request(DemiseFakeLagQueue.Mode.PULSE, 100, (ticket, before) -> {
                assertFalse(before); assertTrue(f.sent.contains(ticket.packet)); released.add(ticket.packet);
            });
            List<Packet<?>> held = Arrays.asList(new C03PacketPlayer(true), new C02PacketUseEntity(),
                    new C0APacketAnimation(), new C09PacketHeldItemChange(2), new C01PacketChatMessage("hello"));
            for (Packet<?> packet : held) assertNull(f.send(packet));
            assertEquals(5, f.pipe.fakeLag().size()); assertTrue(f.sent.isEmpty());
            Packet<?> keepAlive = new C00PacketKeepAlive(3), silent = new C03PacketPlayer(true);
            assertSame(keepAlive, f.send(keepAlive));
            assertSame(silent, f.pipe.outbound(silent, true, p -> { }));
            assertEquals(Arrays.asList(keepAlive, silent), f.sent);
            assertEquals(6, Collections.frequency(f.observer.calls, "outPre"));
            f.pipe.fakeLag().release(f.owner, true);
            List<Packet<?>> expected = new ArrayList<>(Arrays.asList(keepAlive, silent)); expected.addAll(held);
            assertEquals(expected, f.sent); assertEquals(held, released);
            assertEquals(6, Collections.frequency(f.observer.calls, "outPre"));
            assertEquals(6, Collections.frequency(f.observer.calls, "outPost"));
        }
    }

    @Test public void spoofHasExactPacketSelectionNextUpdateActivationAndTheFullThousandMillisecondRange() {
        try (Fixture f = new Fixture()) {
            AtomicInteger releaseHooks = new AtomicInteger();
            DemiseFakeLagQueue.ReleaseListener listener = (ticket, before) -> {
                assertTrue(before); assertFalse(f.sent.contains(ticket.packet)); assertFalse(f.applied.contains(ticket.packet));
                releaseHooks.incrementAndGet();
            };
            f.request(DemiseFakeLagQueue.Mode.SPOOF, 1000, listener);
            Packet<?> startup = new C03PacketPlayer(true); assertSame(startup, f.send(startup));
            f.pipe.fakeLag().sourceUpdate(f.owner);
            Packet<?> keepAlive = new C00PacketKeepAlive(4), confirm = new C0FPacketConfirmTransaction();
            Packet<?> metadata = new S1CPacketEntityMetadata(), movement = new C03PacketPlayer(true);
            assertNull(f.send(keepAlive)); assertNull(f.send(confirm)); f.inbound(metadata); assertNull(f.send(movement));
            Packet<?> chat = new C01PacketChatMessage("immediate"); assertSame(chat, f.send(chat));
            assertEquals(4, f.pipe.fakeLag().size()); assertTrue(f.applied.isEmpty());
            for (int ms = 50; ms <= 1000; ms += 50) {
                f.at(ms); f.pipe.fakeLag().sourceUpdate(f.owner);
                f.request(DemiseFakeLagQueue.Mode.SPOOF, 1000, listener);
                assertEquals("strict source deadline at " + ms, 4, f.pipe.fakeLag().size());
            }
            f.at(1001); f.pipe.fakeLag().sourceUpdate(f.owner);
            assertEquals(Arrays.asList(startup, chat, keepAlive, confirm, movement), f.sent);
            assertEquals(Collections.singletonList(metadata), f.applied); assertEquals(4, releaseHooks.get());
            assertEquals(5, Collections.frequency(f.observer.calls, "outPre"));
            assertEquals(5, Collections.frequency(f.observer.calls, "outPost"));
            assertEquals(1, Collections.frequency(f.observer.calls, "inPre"));
            assertEquals(1, Collections.frequency(f.observer.calls, "inPost"));
        }
    }

    @Test public void spoofReleaseUsesCurrentDelayAndExpiredLeasePublishesOnlyOnce() {
        try (Fixture f = new Fixture()) {
            f.request(DemiseFakeLagQueue.Mode.SPOOF, 500, (ticket, before) -> { });
            f.pipe.fakeLag().sourceUpdate(f.owner); Packet<?> packet = new C03PacketPlayer(true); f.send(packet);
            f.at(50); f.request(DemiseFakeLagQueue.Mode.SPOOF, 40, (ticket, before) -> { });
            f.pipe.fakeLag().sourceUpdate(f.owner); assertEquals(Collections.singletonList(packet), f.sent);
            Packet<?> next = new C0APacketAnimation(); f.send(next);
            f.at(150); f.pipe.pump(); assertEquals(1, f.pipe.fakeLag().size());
            f.at(151); f.pipe.pump(); f.pipe.pump();
            assertEquals(Arrays.asList(packet, next), f.sent); assertEquals(0, f.pipe.fakeLag().size());
        }
    }

    @Test public void finalPreSelectionIsHeldOnceAndCancelledPacketsNeverEnterTheQueue() {
        try (Fixture f = new Fixture()) {
            f.request(DemiseFakeLagQueue.Mode.PULSE, 100, (ticket, before) -> { });
            Packet<?> original = new C03PacketPlayer(true), replacement = new C0APacketAnimation();
            f.observer.action = e -> { if (e.isPre()) e.setPacket(replacement); };
            f.send(original); assertEquals(1, f.pipe.fakeLag().size());
            f.observer.action = e -> { if (e.isPre()) e.cancel(); }; f.send(new C03PacketPlayer(true));
            f.pipe.fakeLag().release(f.owner, true);
            assertEquals(Collections.singletonList(replacement), f.sent);
            assertEquals(Arrays.asList("outPre", "outPre", "outPost"), f.observer.calls);
        }
    }

    @Test public void originalActionGuardIsCheckedBeforeAndAfterTheSpoofReleaseHook() {
        for (boolean invalidateInside : new boolean[]{false, true}) try (Fixture f = new Fixture()) {
            AtomicBoolean valid = new AtomicBoolean(true); AtomicInteger hooks = new AtomicInteger(), accepted = new AtomicInteger();
            f.request(DemiseFakeLagQueue.Mode.SPOOF, 10, (ticket, before) -> {
                hooks.incrementAndGet(); if (invalidateInside) valid.set(false);
            });
            f.pipe.fakeLag().sourceUpdate(f.owner);
            assertNull(f.pipe.outboundSelected(new C02PacketUseEntity(), false, p -> fail("stale action"),
                    p -> valid.get(), p -> accepted.incrementAndGet()));
            if (!invalidateInside) valid.set(false);
            f.at(11); f.pipe.fakeLag().sourceUpdate(f.owner);
            assertEquals(invalidateInside ? 1 : 0, hooks.get()); assertEquals(0, accepted.get());
            assertTrue(f.sent.isEmpty()); assertFalse(f.observer.calls.contains("outPost"));
        }
    }

    @Test public void pulseReleaseObserversCannotRunForRejectedOrFailedTransport() {
        for (boolean reject : new boolean[]{false, true}) try (Fixture f = new Fixture()) {
            AtomicInteger hooks = new AtomicInteger();
            f.request(DemiseFakeLagQueue.Mode.PULSE, 100, (ticket, before) -> hooks.incrementAndGet());
            f.pipe.outbound(new C03PacketPlayer(true), false, p -> { throw new IllegalStateException("transport"); }, () -> !reject);
            try { f.pipe.fakeLag().release(f.owner, true); assertTrue(reject); }
            catch (IllegalStateException expected) { assertFalse(reject); }
            assertEquals(0, hooks.get()); assertEquals(0, f.pipe.fakeLag().size());
            assertFalse(f.observer.calls.contains("outPost"));
        }
    }

    @Test public void releaseReentrancyPreservesOlderPacketOrderAndDoesNotRepeatPreOrPost() {
        try (Fixture f = new Fixture()) {
            Packet<?> first = new C0APacketAnimation(), second = new C03PacketPlayer(true), nested = new C09PacketHeldItemChange(1);
            f.request(DemiseFakeLagQueue.Mode.PULSE, 100, (ticket, before) -> { if (ticket.packet == first) f.send(nested); });
            f.send(first); f.send(second); f.pipe.fakeLag().release(f.owner, true);
            assertEquals(Arrays.asList(first, second, nested), f.sent); assertEquals(0, f.pipe.fakeLag().size());
            assertEquals(3, Collections.frequency(f.observer.calls, "outPre"));
            assertEquals(3, Collections.frequency(f.observer.calls, "outPost"));
        }
    }

    @Test public void capacityDrainWithRenewalAndNestedSendsKeepsFifoAndRemainsBounded() {
        try (Fixture f = new Fixture()) {
            List<Packet<?>> older = new ArrayList<>(); Packet<?> nested = new C0APacketAnimation(), overflow = new C0APacketAnimation();
            f.request(DemiseFakeLagQueue.Mode.PULSE, 100, (ticket, before) -> {
                if (ticket.packet == older.get(0)) {
                    f.request(DemiseFakeLagQueue.Mode.PULSE, 100, (t, b) -> { }); f.send(nested);
                }
            });
            for (int i = 0; i < DemiseFakeLagQueue.MAX_PACKETS; i++) { Packet<?> packet = new C0APacketAnimation(); older.add(packet); f.send(packet); }
            f.send(overflow); assertEquals(older, f.sent); assertEquals(2, f.pipe.fakeLag().size());
            f.pipe.fakeLag().release(f.owner, true);
            older.add(nested); older.add(overflow); assertEquals(older, f.sent);
            assertEquals(0, f.pipe.fakeLag().size());
        }
    }

    @Test public void aSecondTimingOwnerIsAppliedOnceAndKeepsItsExistingBarrierContract() {
        try (Fixture f = new Fixture()) {
            AtomicInteger hooks = new AtomicInteger(); Object other = new Object();
            f.pipe.delays().request(other, 200, (p, direction) -> direction == EventFlow.OUTBOUND);
            f.request(DemiseFakeLagQueue.Mode.PULSE, 100, (ticket, before) -> hooks.incrementAndGet());
            Packet<?> movement = new C03PacketPlayer(true); f.send(movement); f.pipe.fakeLag().release(f.owner, true);
            assertEquals(0, hooks.get()); assertEquals(1, f.pipe.delays().size()); assertTrue(f.sent.isEmpty());
            Packet<?> action = new C02PacketUseEntity(); assertSame(action, f.send(action));
            assertEquals(Arrays.asList(movement, action), f.sent); assertEquals(1, hooks.get());
            assertEquals(2, Collections.frequency(f.observer.calls, "outPre"));
            assertEquals(2, Collections.frequency(f.observer.calls, "outPost"));
            assertTrue(f.pipe.delays().hasOwner(other));
        }
    }
}
