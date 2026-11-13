package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.*;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class FakeLagLifecycleTest {
    private static int outstanding(PacketPipeline pipe) throws Exception {
        java.lang.reflect.Field field = DemiseFakeLagQueue.class.getDeclaredField("outstanding"); field.setAccessible(true);
        return ((Set<?>) field.get(pipe.fakeLag())).size();
    }
    private static final class ScheduledMetadata extends NetworkTestSupport.Play {
        final net.minecraft.util.IThreadListener thread;
        int applications;
        Runnable body = () -> { };
        ScheduledMetadata(NetworkTestSupport.Fixture f, net.minecraft.util.IThreadListener thread) {
            super(f.client, f.network); this.thread = thread;
        }
        @Override public void handleEntityMetadata(S1CPacketEntityMetadata packet) {
            net.minecraft.network.PacketThreadUtil.checkThreadAndEnqueue(packet, this, thread);
            applications++; body.run();
        }
    }
    private final class ScheduledFixture implements AutoCloseable {
        final NetworkTestSupport.Fixture connection = new NetworkTestSupport.Fixture();
        final AtomicLong now = new AtomicLong();
        final PacketPipeline pipe = pipeline(now, new ArrayList<>());
        final Object owner = new Object();
        final AtomicBoolean ready = new AtomicBoolean(), handlerCurrent = new AtomicBoolean(true);
        final List<Runnable> tasks = new ArrayList<>();
        final NetworkTestSupport.WorldStub world = NetworkTestSupport.bare(NetworkTestSupport.WorldStub.class);
        final ScheduledMetadata play = new ScheduledMetadata(connection, new net.minecraft.util.IThreadListener() {
            public boolean isCallingFromMinecraftThread() { return ready.get(); }
            public com.google.common.util.concurrent.ListenableFuture<Object> addScheduledTask(Runnable task) {
                tasks.add(task); return com.google.common.util.concurrent.Futures.immediateFuture(null);
            }
        });
        final NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer();
        ScheduledFixture() throws Exception {
            play.clientWorldController = world;
            world.target = NetworkTestSupport.bare(net.minecraft.client.entity.EntityOtherPlayerMP.class);
            world.target.setEntityId(7); world.target.worldObj = world;
            Pulsar.BUS.subscribe(observer);
            pipe.fakeLag().request(owner, DemiseFakeLagQueue.Mode.SPOOF, 10, () -> true, (ticket, before) -> { });
            pipe.fakeLag().sourceUpdate(owner);
            S1CPacketEntityMetadata packet = new S1CPacketEntityMetadata();
            java.lang.reflect.Field id = S1CPacketEntityMetadata.class.getDeclaredField("entityId"); id.setAccessible(true); id.setInt(packet, 7);
            pipe.inbound(packet, play, false, handlerCurrent::get);
            now.set(11_000_000L); pipe.fakeLag().sourceUpdate(owner);
            assertEquals(1, tasks.size()); assertEquals(1, outstanding(pipe));
        }
        void resume() { ready.set(true); tasks.get(0).run(); }
        public void close() { Pulsar.BUS.unsubscribe(observer); pipe.close(); connection.close(); }
    }
    private PacketPipeline pipeline(AtomicLong clock, List<Packet<?>> accepted) {
        return new PacketPipeline(new NetworkTestSupport.MainThread(), () -> true, accepted::add,
                error -> { throw new AssertionError(error); }, new PacketDelays(clock::get));
    }
    private void pulse(PacketPipeline pipe, Object owner) {
        pipe.fakeLag().request(owner, DemiseFakeLagQueue.Mode.PULSE, 100, () -> true, (ticket, before) -> { });
    }

    @Test public void joinRespawnCorrectionDisconnectAndDeathDiscardBeforeLifecycleCallbacks() {
        Packet<?>[] boundaries = {new S01PacketJoinGame(), new S07PacketRespawn(), new S08PacketPlayerPosLook(),
                new S40PacketDisconnect(), new S06PacketUpdateHealth(0, 0, 0)};
        for (Packet<?> boundary : boundaries) {
            List<Packet<?>> sent = new ArrayList<>(); PacketPipeline p = pipeline(new AtomicLong(), sent); Object owner = new Object();
            pulse(p, owner); p.outbound(new C03PacketPlayer(true), false, ignored -> { });
            long before = p.getContextVersion(); AtomicInteger applied = new AtomicInteger();
            p.inbound(boundary, NetworkTestSupport.handler(a -> {
                assertEquals(0, p.fakeLag().size()); p.fakeLag().release(owner, true); applied.incrementAndGet();
            }), false);
            assertTrue(p.getContextVersion() > before); assertEquals(1, applied.get()); assertTrue(sent.isEmpty()); p.close();
        }
    }

    @Test public void contextLossAlsoInvalidatesAlreadyReleasedTicketsWaitingInAnotherOwnerQueue() {
        List<Packet<?>> sent = new ArrayList<>(); PacketPipeline p = pipeline(new AtomicLong(), sent); Object owner = new Object(), other = new Object();
        try {
            p.delays().request(other, 200, (packet, direction) -> true); pulse(p, owner);
            p.outbound(new C03PacketPlayer(true), false, ignored -> { }); p.fakeLag().release(owner, true);
            assertEquals(1, p.delays().size()); p.worldChanged(); p.delays().release(other);
            assertTrue(sent.isEmpty()); assertEquals(0, p.delays().size());
        } finally { p.close(); }
    }

    @Test public void closedConnectionCannotReplayIntoAReplacementConnection() {
        List<Packet<?>> oldSent = new ArrayList<>(), newSent = new ArrayList<>(); Object owner = new Object();
        PacketPipeline old = pipeline(new AtomicLong(), oldSent); pulse(old, owner);
        old.outbound(new C03PacketPlayer(true), false, ignored -> { }); old.close();
        PacketPipeline fresh = pipeline(new AtomicLong(), newSent);
        try {
            old.fakeLag().release(owner, true); pulse(old, owner);
            Packet<?> packet = new C03PacketPlayer(true); assertSame(packet, fresh.outbound(packet, false, ignored -> { }));
            assertTrue(oldSent.isEmpty()); assertEquals(Collections.singletonList(packet), newSent);
            assertEquals(0, old.fakeLag().size());
        } finally { fresh.close(); }
    }

    @Test public void spoofEarlyClearKeepsOnlyTheCurrentlyReleasingPacketAndRechecksTheHandler() {
        AtomicLong now = new AtomicLong(); List<Packet<?>> sent = new ArrayList<>(); PacketPipeline p = pipeline(now, sent); Object owner = new Object();
        try {
            p.fakeLag().request(owner, DemiseFakeLagQueue.Mode.SPOOF, 10, () -> true,
                    (ticket, before) -> p.fakeLag().discardPending(owner, ticket));
            p.fakeLag().sourceUpdate(owner);
            Packet<?> first = new C03PacketPlayer(true); p.outbound(first, false, ignored -> { });
            p.outbound(new C03PacketPlayer(true), false, ignored -> { }); p.outbound(new C02PacketUseEntity(), false, ignored -> { });
            now.set(11_000_000L); p.fakeLag().sourceUpdate(owner);
            assertEquals(Collections.singletonList(first), sent); assertEquals(0, p.fakeLag().size());
            AtomicBoolean handler = new AtomicBoolean(true); AtomicInteger applications = new AtomicInteger();
            p.fakeLag().request(owner, DemiseFakeLagQueue.Mode.SPOOF, 0, () -> true, (ticket, before) -> handler.set(false));
            p.fakeLag().sourceUpdate(owner);
            p.inbound(new S1CPacketEntityMetadata(), NetworkTestSupport.handler(a -> applications.incrementAndGet()), false, handler::get);
            now.incrementAndGet(); p.fakeLag().sourceUpdate(owner); assertEquals(0, applications.get());
        } finally { p.close(); }
    }

    @Test public void correctionInsideReleaseVetoesTheCurrentPacketAndAllOlderEnvelopes() {
        AtomicLong now = new AtomicLong(); List<Packet<?>> sent = new ArrayList<>(); PacketPipeline p = pipeline(now, sent); Object owner = new Object();
        try {
            p.fakeLag().request(owner, DemiseFakeLagQueue.Mode.SPOOF, 10, () -> true,
                    (ticket, before) -> p.inbound(new S08PacketPlayerPosLook(), NetworkTestSupport.handler(a -> { }), false));
            p.fakeLag().sourceUpdate(owner);
            p.outbound(new C03PacketPlayer(true), false, ignored -> { }); p.outbound(new C02PacketUseEntity(), false, ignored -> { });
            now.set(11_000_000L); p.fakeLag().sourceUpdate(owner);
            assertTrue(sent.isEmpty()); assertEquals(0, p.fakeLag().size());
        } finally { p.close(); }
    }

    @Test public void expiredLeaseCallbackCannotPublishTheNewPacketAcrossItsChangedEpoch() {
        AtomicLong now = new AtomicLong(); List<Packet<?>> sent = new ArrayList<>(); PacketPipeline p = pipeline(now, sent); Object owner = new Object();
        NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer(); Pulsar.BUS.subscribe(observer);
        try {
            p.fakeLag().request(owner, DemiseFakeLagQueue.Mode.SPOOF, 1000, () -> true,
                    (ticket, before) -> fail("source dispatch has no release-position event"));
            Packet<?> old = new C03PacketPlayer(true);
            observer.action = event -> {
                if (event.isPost() && event.getPacket() == old)
                    p.inbound(new S08PacketPlayerPosLook(), NetworkTestSupport.handler(a -> { }), false);
            };
            p.fakeLag().sourceUpdate(owner); p.outbound(old, false, ignored -> { });
            now.set(101_000_000L);
            assertNull(p.outbound(new C0APacketAnimation(), false, ignored -> fail("old epoch escaped after pump")));
            assertEquals(Collections.singletonList(old), sent); assertEquals(0, p.fakeLag().size());
        } finally { Pulsar.BUS.unsubscribe(observer); p.close(); }
    }

    @Test public void expiredOriginalRotationAndNativeActionGuardsAreNotRevivedByRelease() {
        List<Packet<?>> sent = new ArrayList<>(); PacketPipeline p = pipeline(new AtomicLong(), sent); Object owner = new Object();
        AtomicBoolean originalFrame = new AtomicBoolean(true); AtomicInteger nativeCompletion = new AtomicInteger();
        try {
            pulse(p, owner); Packet<?> ordinary = new C03PacketPlayer(true);
            p.outbound(ordinary, false, ignored -> { });
            p.outbound(new C03PacketPlayer.C05PacketPlayerLook(45, 20, true), false, ignored -> fail("expired module-owned C03"), originalFrame::get);
            Packet<?> accepted = p.outbound(new C02PacketUseEntity(), false, ignored -> fail("expired native click"), originalFrame::get);
            if (accepted != null) nativeCompletion.incrementAndGet();
            originalFrame.set(false); p.fakeLag().release(owner, true);
            assertEquals(Collections.singletonList(ordinary), sent); assertEquals(0, nativeCompletion.get());
        } finally { p.close(); }
    }

    @Test public void releaseExceptionAndOverAgeCleanupCannotLeaveReplayablePackets() {
        AtomicLong now = new AtomicLong(); List<Packet<?>> sent = new ArrayList<>(); PacketPipeline p = pipeline(now, sent); Object owner = new Object();
        try {
            p.fakeLag().request(owner, DemiseFakeLagQueue.Mode.PULSE, 100, () -> true,
                    (ticket, before) -> { throw new IllegalStateException("release callback"); });
            Packet<?> first = new C03PacketPlayer(true); p.outbound(first, false, ignored -> { });
            p.outbound(new C03PacketPlayer(true), false, ignored -> { });
            try { p.fakeLag().release(owner, true); fail(); } catch (IllegalStateException expected) { }
            p.fakeLag().release(owner, true); assertEquals(Collections.singletonList(first), sent); assertEquals(0, p.fakeLag().size());
            pulse(p, owner); Packet<?> next = new C03PacketPlayer(true); p.outbound(next, false, ignored -> { });
            now.set(2_000_000_001L); p.pump(); assertEquals(Arrays.asList(first, next), sent); assertEquals(0, p.fakeLag().size());
        } finally { p.close(); }
    }

    @Test public void invalidSceneDropsInsteadOfFlushingAndPreCancellationDoesNotCreatePost() {
        List<Packet<?>> sent = new ArrayList<>(); PacketPipeline p = pipeline(new AtomicLong(), sent); Object owner = new Object();
        AtomicBoolean current = new AtomicBoolean(true); NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer(); Pulsar.BUS.subscribe(observer);
        try {
            p.fakeLag().request(owner, DemiseFakeLagQueue.Mode.PULSE, 100, current::get, (ticket, before) -> fail("obsolete scene"));
            p.outbound(new C03PacketPlayer(true), false, ignored -> { }); current.set(false); p.pump();
            assertTrue(sent.isEmpty()); assertEquals(Collections.singletonList("outPre"), observer.calls);
        } finally { Pulsar.BUS.unsubscribe(observer); p.close(); }
    }

    @Test public void scheduledMetadataApplicationRetainsItsTicketAndPostsOnlyAfterFactualCompletion() {
        for (int scenario = 0; scenario < 3; scenario++) {
            AtomicLong now = new AtomicLong(); PacketPipeline p = pipeline(now, new ArrayList<>()); Object owner = new Object();
            AtomicBoolean ready = new AtomicBoolean(), handlerCurrent = new AtomicBoolean(true);
            AtomicInteger applications = new AtomicInteger(), releaseHooks = new AtomicInteger();
            List<Runnable> scheduled = new ArrayList<>();
            net.minecraft.util.IThreadListener applicationThread = new net.minecraft.util.IThreadListener() {
                public boolean isCallingFromMinecraftThread() { return ready.get(); }
                public com.google.common.util.concurrent.ListenableFuture<Object> addScheduledTask(Runnable task) {
                    scheduled.add(task); return com.google.common.util.concurrent.Futures.immediateFuture(null);
                }
            };
            final net.minecraft.network.play.INetHandlerPlayClient[] handler = {null};
            handler[0] = NetworkTestSupport.handler(arguments -> {
                net.minecraft.network.PacketThreadUtil.checkThreadAndEnqueue((Packet) arguments[0], handler[0], applicationThread);
                applications.incrementAndGet();
            });
            NetworkTestSupport.Observer observer = new NetworkTestSupport.Observer(); Pulsar.BUS.subscribe(observer);
            try {
                p.fakeLag().request(owner, DemiseFakeLagQueue.Mode.SPOOF, 10, () -> true, (ticket, before) -> releaseHooks.incrementAndGet());
                p.fakeLag().sourceUpdate(owner);
                p.inbound(new S1CPacketEntityMetadata(), handler[0], false, handlerCurrent::get);
                now.set(11_000_000L); p.fakeLag().sourceUpdate(owner);
                assertEquals(1, scheduled.size()); assertEquals(1, releaseHooks.get()); assertEquals(0, applications.get());
                assertEquals(Collections.singletonList("inPre"), observer.calls);
                if (scenario == 1) p.worldChanged(); else if (scenario == 2) handlerCurrent.set(false);
                ready.set(true); scheduled.get(0).run(); scheduled.get(0).run();
                assertEquals(scenario == 0 ? 1 : 0, applications.get());
                assertEquals(scenario == 0 ? Arrays.asList("inPre", "inPost") : Collections.singletonList("inPre"), observer.calls);
                assertEquals(1, releaseHooks.get());
            } finally { Pulsar.BUS.unsubscribe(observer); p.close(); }
        }
    }

    @Test public void removedOrReplacedMetadataEntityCannotUseAQueuedApplicationAndLeavesNoOutstandingTicket() throws Exception {
        for (boolean replace : new boolean[]{false, true}) try (ScheduledFixture f = new ScheduledFixture()) {
            f.world.target = replace ? NetworkTestSupport.bare(net.minecraft.client.entity.EntityOtherPlayerMP.class) : null;
            if (replace) { f.world.target.setEntityId(7); f.world.target.worldObj = f.world; }
            f.resume(); f.resume(); assertEquals(0, f.play.applications); assertEquals(0, outstanding(f.pipe));
            assertFalse(f.observer.calls.contains("inPost"));
        }
    }

    @Test public void scheduledFailureDiscardsFakeLagAndFinalApplicationVetoFinalizesWithoutPost() throws Exception {
        for (boolean failure : new boolean[]{false, true}) try (ScheduledFixture f = new ScheduledFixture()) {
            Object other = new Object(); f.pipe.delays().request(other, 100, (packet, direction) -> true);
            f.pipe.outbound(new C0APacketAnimation(), false, ignored -> { }); assertEquals(1, f.pipe.fakeLag().size());
            IllegalStateException error = new IllegalStateException("late metadata handler");
            f.play.body = () -> { if (failure) throw error; f.handlerCurrent.set(false); };
            if (failure) assertSame(error, assertThrows(IllegalStateException.class, f::resume)); else f.resume();
            f.resume(); assertEquals(1, f.play.applications); assertFalse(f.observer.calls.contains("inPost"));
            assertEquals(failure ? 0 : 1, outstanding(f.pipe));
            assertEquals(failure ? 0 : 1, f.pipe.fakeLag().size());
            assertTrue("the foreign timing owner remains intact", f.pipe.delays().hasOwner(other));
        }
    }

    @Test public void anAbandonedScheduledApplicationExpiresWithoutRetainingAnOwnedEnvelope() throws Exception {
        try (ScheduledFixture f = new ScheduledFixture()) {
            f.now.set(2_000_000_001L); f.pipe.pump(); assertEquals(0, outstanding(f.pipe));
            f.resume(); assertEquals(0, f.play.applications); assertFalse(f.observer.calls.contains("inPost"));
        }
    }
}
