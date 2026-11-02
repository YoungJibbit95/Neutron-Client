package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.motion.EventMotion;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import me.youngjibbit.core.observation.RuntimeObservation;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.client.entity.EntityPlayerSP;
import org.junit.Test;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** A packet already sent stays sent; POST callbacks cannot renew its obsolete source rights. */
public class C03PostWaveTest {
    private void invalidatedAfterTransport(Consumer<ScaffoldOwnerContinuityTest.Run> change) throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            run.walk();
            assertEquals("initial accepted scaffold pose", "scaffold", run.rotation.getConfirmedOwner());
            run.noSupport();
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            int[] packetPosts = {0};
            boolean[] staleSourceAtMotionPost = {false};
            hooks.packet = event -> {
                if (event.isOutbound() && event.isPost() && event.getPacket() instanceof C03PacketPlayer) {
                    packetPosts[0]++;
                    change.accept(run);
                }
            };
            Listener<EventMotion> firstMotionConsumer = new Listener<>(EventMotion.class, event -> {
                if (event.isPost()) staleSourceAtMotionPost[0] = run.rotation.canUseRotation("scaffold", false);
            }, EventPriority.HIGHEST);
            // Check the first consumer after finishMotion, before Scaffold's own cleanup can hide it.
            Pulsar.BUS.subscribe(hooks);
            Pulsar.BUS.subscribe(firstMotionConsumer);
            try {
                List<Object> packets = run.walk();
                assertEquals("one actual transport occurred before the invalidating callback", 1,
                        packets.stream().filter(packet -> packet instanceof C03PacketPlayer).count());
                assertEquals(1, packetPosts[0]);
                assertFalse("obsolete acceptance must not authorize the next Motion POST consumer", staleSourceAtMotionPost[0]);
                assertFalse(run.rotation.canUseRotation("scaffold", false));
                assertNull(run.rotation.getConfirmedOwner());
                ScaffoldOwnerContinuityTest.noActions(packets);
            } finally { Pulsar.BUS.unsubscribe(firstMotionConsumer); Pulsar.BUS.unsubscribe(hooks); }
        }
    }

    @Test public void observerGenerationChangedInPacketPostCannotRenewSourceAuthority() throws Exception {
        invalidatedAfterTransport(run -> run.f.runtime.getObservation().invalidateScene());
    }

    @Test public void packetEpochChangedInPacketPostCannotRenewSourceAuthority() throws Exception {
        invalidatedAfterTransport(run -> run.f.network.getClientPackets().worldChanged());
    }

    @Test public void guiSuspensionInPacketPostCannotRenewSourceAuthority() throws Exception {
        invalidatedAfterTransport(run -> run.f.mc.currentScreen = new GuiChat());
    }

    @Test public void postTransportSceneSamplingFailureRevokesOwnFrameAndPropagates() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            run.walk(); run.noSupport();
            RuntimeObservation observation = run.f.runtime.getObservation();
            java.lang.reflect.Field source = ScaffoldOwnerContinuityTest.field(RuntimeObservation.class, "source");
            Object originalSource = source.get(observation);
            IllegalStateException failure = new IllegalStateException("post-transport scene sample");
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (event.isOutbound() && event.isPost() && event.getPacket() instanceof C03PacketPlayer) {
                    try {
                        source.set(observation, (Supplier<RuntimeObservation.SceneSample>) () -> { throw failure; });
                    } catch (IllegalAccessException error) { throw new AssertionError(error); }
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                assertSame(failure, assertThrows(IllegalStateException.class, run::walk));
                assertEquals("transport already happened", 1,
                        run.drain().stream().filter(packet -> packet instanceof C03PacketPlayer).count());
                assertFalse("failed final validation cannot leave accepted action authority", run.rotation.isMotionAccepted());
                assertNull(run.rotation.getConfirmedOwner());
                assertNull(observation.getCurrentStep());
                assertEquals(-1, observation.getActiveOuterTickId());
            } finally {
                Pulsar.BUS.unsubscribe(hooks);
                source.set(observation, originalSource);
            }
        }
    }

    @Test public void connectionRemovedInPacketPostKeepsTransportFactWithoutStaleAuthorityOrCrash() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            run.walk(); run.noSupport();
            java.lang.reflect.Field queue = ScaffoldOwnerContinuityTest.field(EntityPlayerSP.class, "sendQueue");
            Object original = queue.get(run.f.player);
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (event.isOutbound() && event.isPost() && event.getPacket() instanceof C03PacketPlayer) {
                    try { queue.set(run.f.player, null); }
                    catch (IllegalAccessException error) { throw new AssertionError(error); }
                }
            };
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> packets = run.walk();
                assertEquals(1, packets.stream().filter(packet -> packet instanceof C03PacketPlayer).count());
                assertFalse(run.rotation.canUseRotation("scaffold", false));
                assertNull(run.f.runtime.getObservation().getCurrentStep());
            } finally { Pulsar.BUS.unsubscribe(hooks); queue.set(run.f.player, original); }
        }
    }
}
