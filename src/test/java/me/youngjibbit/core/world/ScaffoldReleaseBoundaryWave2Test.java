package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.PacketFlowTest;
import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.core.observation.MinecraftSceneSource;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.EventMotion;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.*;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import static org.junit.Assert.*;

/** Fresh normal-JIT cases keep Unsafe client fixture replacement outside warmed shared code. */
@RunWith(Parameterized.class)
public class ScaffoldReleaseBoundaryWave2Test {
    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"oldAcceptedPoseCannotRebindToGenerationInvalidatedBeforeDisable"},
                {"numericFinishWithoutANewResolvedC03CannotBorrowTheOldAcceptanceReceipt"},
                {"reenableInsideReleasePacketPreOrPostRevokesTheOldTicket"},
                {"losingForeignOfferBeforeDisablePreventsReleaseInTheSameFrame"},
                {"realAuraCameraIntentBeforeDisablePreventsReleaseAndCannotAttackAnOffCameraTarget"},
                {"reenableDuringReleaseCaptureCannotBeClearedOrUnsubscribedByTheOldDisable"},
                {"clockReversalRevokesAcceptedReleaseButPendingExpiryPreservesLegacyNumbers"},
                {"shutdownReplacementAndTickWrapPermanentlyEndTheOldLease"},
                {"observedGuiAbaRevokesWithoutSourceSamplingFromRepeatedQueries"},
                {"nestedWalkingInsideResolveCannotSendOrPostTheOlderNeutralFrame"},
                {"nestedWalkingInsideFinishCannotOverwriteReportingOrPostTheOlderFrame"}
        });
    }

    private final String method;
    public ScaffoldReleaseBoundaryWave2Test(String method) { this.method = method; }
    @Test public void assertionCaseInFreshNormalJitJvm() throws Exception { Wave2IsolatedCase.run(Checks.class, method); }

    public static final class Checks {
        private static final String RELEASE = "scaffold-release";

        private static long movements(List<Object> packets) {
            return packets.stream().filter(packet -> packet instanceof C03PacketPlayer).count();
        }

        private static void noActions(List<Object> packets) {
            for (Object packet : packets) {
                assertFalse(packet instanceof C08PacketPlayerBlockPlacement);
                assertFalse(packet instanceof C09PacketHeldItemChange);
                assertFalse(packet instanceof C0APacketAnimation);
                assertFalse(packet instanceof C02PacketUseEntity);
            }
        }

        private static void disableAfterRealAcceptance(ScaffoldOwnerContinuityTest.Run run) {
            run.walk();
            assertEquals("scaffold", run.rotation.getConfirmedOwner());
            run.f.scaffold.setToggled(false);
            assertNull("pending release is not accepted authority", run.rotation.getConfirmedOwner());
        }

        private static void acceptedRelease(ScaffoldOwnerContinuityTest.Run run) {
            disableAfterRealAcceptance(run);
            noActions(run.walk());
            assertEquals(RELEASE, run.rotation.getConfirmedOwner());
        }

        @Test public void oldAcceptedPoseCannotRebindToGenerationInvalidatedBeforeDisable() throws Exception {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                run.walk(); assertEquals("scaffold", run.rotation.getConfirmedOwner());
                run.f.runtime.getObservation().invalidateScene();
                run.f.scaffold.setToggled(false);
                noActions(run.walk());
                assertNull("old accepted scene cannot be resampled as a new lease origin", run.rotation.getConfirmedOwner());
                assertFalse(run.rotation.canUseRotation(RELEASE, false));
            }
        }

        @Test public void numericFinishWithoutANewResolvedC03CannotBorrowTheOldAcceptanceReceipt() throws Exception {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                run.walk(); assertEquals("scaffold", run.rotation.getConfirmedOwner());
                long sequence = run.rotation.getMotionSequence();
                run.f.accept("scaffold", 45);
                assertTrue(run.rotation.getMotionSequence() > sequence);
                assertEquals("the legacy numeric API alone can seed owner numbers", "scaffold", run.rotation.getConfirmedOwner());
                assertTrue(run.drain().isEmpty());
                run.f.scaffold.setToggled(false);
                noActions(run.walk());
                assertNull("new numeric finish had no resolved/transported C03 evidence", run.rotation.getConfirmedOwner());
                assertFalse(run.rotation.canUseRotation(RELEASE, false));
            }
        }

        @Test public void reenableInsideReleasePacketPreOrPostRevokesTheOldTicket() throws Exception {
            for (boolean pre : new boolean[]{true, false}) {
                try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                    disableAfterRealAcceptance(run);
                    AtomicInteger calls = new AtomicInteger();
                    ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
                    hooks.packet = event -> {
                        if (event.isOutbound() && event.isPre() == pre && event.getPacket() instanceof C03PacketPlayer
                                && calls.getAndIncrement() == 0) run.f.scaffold.setToggled(true);
                    };
                    Pulsar.BUS.subscribe(hooks);
                    List<Object> actual;
                    try { actual = run.walk(); } finally { Pulsar.BUS.unsubscribe(hooks); }
                    assertEquals(1, calls.get());
                    assertEquals(pre ? 0 : 1, movements(actual));
                    noActions(actual);
                    assertNull("old release packet cannot bind the new enable generation", run.rotation.getConfirmedOwner());
                    run.walk();
                    assertEquals("a real fresh enabled frame remains usable", "scaffold", run.rotation.getConfirmedOwner());
                }
            }
        }

        @Test public void losingForeignOfferBeforeDisablePreventsReleaseInTheSameFrame() throws Exception {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                run.walk(); assertEquals("scaffold", run.rotation.getConfirmedOwner());
                AtomicInteger reached = new AtomicInteger();
                Listener<EventMotion> afterScaffold = new Listener<>(EventMotion.class, event -> {
                    if (!event.isPre()) return;
                    run.rotation.request("aura", RotationManager.AURA_PRIORITY, 12, 3);
                    run.f.scaffold.setToggled(false); reached.incrementAndGet();
                }, EventPriority.LOWEST);
                Pulsar.BUS.subscribe(afterScaffold);
                try { noActions(run.walk()); } finally { Pulsar.BUS.unsubscribe(afterScaffold); }
                assertEquals(1, reached.get());
                assertFalse(run.rotation.canUseRotation(RELEASE, false));
                noActions(run.walk());
                assertNull("losing foreign intent still permanently prevents a later release", run.rotation.getConfirmedOwner());
            }
        }

        @Test public void realAuraCameraIntentBeforeDisablePreventsReleaseAndCannotAttackAnOffCameraTarget() throws Exception {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                run.walk(); assertEquals("scaffold", run.rotation.getConfirmedOwner());
                double angle = Math.toRadians(run.rotation.getEffectiveYaw());
                RotationBehaviorHookTest.Player target = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
                target.worldObj = run.scene; target.setEntityId(64);
                target.posX = .5 - Math.sin(angle) * 2; target.posY = 64; target.posZ = .5 + Math.cos(angle) * 2;
                target.setEntityBoundingBox(new AxisAlignedBB(target.posX - .3, 64, target.posZ - .3,
                        target.posX + .3, 65.8, target.posZ + .3));
                ScaffoldOwnerContinuityTest.field(World.class, "loadedEntityList").set(run.scene,
                        new ArrayList<>(Collections.singletonList(target)));
                run.f.mc.gameSettings.keyBindUseItem = new KeyBinding("boundary.use", 0, "synthetic");
                run.f.player.sendQueue.clientWorldController = run.scene;
                ScaffoldOwnerContinuityTest.field(PlayerControllerMP.class, "currentGameType").set(run.f.mc.playerController,
                        WorldSettings.GameType.SPECTATOR);
                run.f.aura.getBooleanSetting("Rotations").setValue(false);
                Field auraTarget = ScaffoldOwnerContinuityTest.field(me.youngjibbit.module.impl.combat.Aura.class, "target");
                AtomicInteger reached = new AtomicInteger();
                Listener<EventMotion> disableLast = new Listener<>(EventMotion.class, event -> {
                    if (!event.isPre()) return;
                    try { assertNotNull(auraTarget.get(run.f.aura)); }
                    catch (IllegalAccessException failure) { throw new AssertionError(failure); }
                    assertFalse("camera ray is deliberately different from the old Scaffold ray",
                            run.f.runtime.getRaycastService().canHit(target, 0, 0, 3));
                    run.f.scaffold.setToggled(false); reached.incrementAndGet();
                }, EventPriority.LOWEST);
                run.f.aura.onEnable(); Pulsar.BUS.subscribe(disableLast);
                try { noActions(run.walk()); }
                finally { Pulsar.BUS.unsubscribe(disableLast); run.f.aura.onDisable(); }
                assertEquals(1, reached.get());
                assertFalse(run.rotation.canUseRotation(RELEASE, false));
                noActions(run.walk()); assertNull(run.rotation.getConfirmedOwner());
            }
        }

        @SuppressWarnings("unchecked")
        @Test public void reenableDuringReleaseCaptureCannotBeClearedOrUnsubscribedByTheOldDisable() throws Exception {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                run.walk(); assertEquals("scaffold", run.rotation.getConfirmedOwner());
                RuntimeObservation observation = run.f.runtime.getObservation();
                Field source = ScaffoldOwnerContinuityTest.field(RuntimeObservation.class, "source");
                Supplier<RuntimeObservation.SceneSample> original = (Supplier<RuntimeObservation.SceneSample>) source.get(observation);
                AtomicBoolean once = new AtomicBoolean(true); AtomicInteger reentries = new AtomicInteger();
                source.set(observation, (Supplier<RuntimeObservation.SceneSample>) () -> {
                    if (once.compareAndSet(true, false)) {
                        run.f.scaffold.setToggled(true);
                        run.walk();
                        assertEquals("nested new-generation walking really accepted", "scaffold", run.rotation.getConfirmedOwner());
                        reentries.incrementAndGet();
                    }
                    return original.get();
                });
                try {
                    run.f.scaffold.setToggled(false);
                    assertEquals(1, reentries.get());
                    assertTrue(run.f.scaffold.isToggled());
                    assertEquals("old disable tail cannot clear the accepted new generation", "scaffold", run.rotation.getConfirmedOwner());
                    run.walk();
                    assertEquals("new normal listener remains subscribed", "scaffold", run.rotation.getConfirmedOwner());
                } finally { source.set(observation, original); }
            }
        }

        @Test public void clockReversalRevokesAcceptedReleaseButPendingExpiryPreservesLegacyNumbers() throws Exception {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                acceptedRelease(run);
                long lastObserved = run.scene.motionTime.get();
                run.scene.motionTime.set(lastObserved - 1);
                assertNull("even a small reversal above the start time is a hard end", run.rotation.getConfirmedOwner());
                assertFalse(run.rotation.canUseRotation(false));
                run.scene.motionTime.set(lastObserved);
                assertNull(run.rotation.getConfirmedOwner()); assertTrue(run.drain().isEmpty());
            }
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                disableAfterRealAcceptance(run);
                boolean historicNumeric = run.rotation.canUseRotation(false);
                assertTrue("source reset historically preserves the actual old wire basis", historicNumeric);
                run.scene.motionTime.addAndGet(200_000_000L);
                assertNull(run.rotation.getConfirmedOwner());
                assertFalse(run.rotation.canUseRotation(RELEASE, false));
                assertFalse(run.rotation.canUseRotation("scaffold", false));
                assertEquals("a never-accepted release cannot erase unrelated legacy numeric history",
                        historicNumeric, run.rotation.canUseRotation(false));
                assertTrue(run.drain().isEmpty());
            }
        }

        @Test public void shutdownReplacementAndTickWrapPermanentlyEndTheOldLease() throws Exception {
            for (int boundary = 0; boundary < 5; boundary++) {
                try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                    acceptedRelease(run);
                    RuntimeObservation observation = run.f.runtime.getObservation();
                    if (boundary == 0) observation.reset();
                    if (boundary == 1) {
                        RuntimeObservation replacement = new RuntimeObservation(() -> MinecraftSceneSource.capture(run.f.mc));
                        Field field = ScaffoldOwnerContinuityTest.field(ClientRuntime.class, "observation");
                        field.set(run.f.runtime, replacement); replacement.beginOuterTick();
                        assertNull(run.rotation.getConfirmedOwner());
                        replacement.reset(); field.set(run.f.runtime, observation);
                    }
                    if (boundary == 2) {
                        Field field = ScaffoldOwnerContinuityTest.field(ClientRuntime.class, "rotationManager");
                        field.set(run.f.runtime, new RotationManager(run.scene.motionTime::get, 811));
                        assertNull(run.rotation.getConfirmedOwner());
                        field.set(run.f.runtime, run.rotation);
                    }
                    if (boundary == 3) run.f.network.configureClientPackets(new PacketFlowTest.MainThread());
                    if (boundary == 4) {
                        ScaffoldOwnerContinuityTest.field(RuntimeObservation.class, "outerTickId").setLong(observation, Long.MAX_VALUE);
                        observation.beginOuterTick(); observation.endOuterTick();
                    }
                    assertNull("hard boundary permanently ends old release: " + boundary, run.rotation.getConfirmedOwner());
                    assertFalse(run.rotation.canUseRotation(RELEASE, false));
                    assertFalse(run.rotation.canUseRotation(false));
                    assertTrue("reads do not invent cleanup packets", run.drain().isEmpty());
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Test public void observedGuiAbaRevokesWithoutSourceSamplingFromRepeatedQueries() throws Exception {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                acceptedRelease(run);
                RuntimeObservation observation = run.f.runtime.getObservation();
                Field source = ScaffoldOwnerContinuityTest.field(RuntimeObservation.class, "source");
                Supplier<RuntimeObservation.SceneSample> original = (Supplier<RuntimeObservation.SceneSample>) source.get(observation);
                AtomicInteger samples = new AtomicInteger();
                long generation = observation.getObservedConnectionGeneration(), suspension = observation.getObservedSuspensionRevision();
                source.set(observation, (Supplier<RuntimeObservation.SceneSample>) () -> { samples.incrementAndGet(); return original.get(); });
                try {
                    run.f.mc.currentScreen = new GuiChat(); observation.captureScene();
                    run.f.mc.currentScreen = null; observation.captureScene();
                    assertEquals(generation, observation.getObservedConnectionGeneration());
                    assertEquals(suspension + 1, observation.getObservedSuspensionRevision());
                    for (int query = 0; query < 100; query++) {
                        assertNull(run.rotation.getConfirmedOwner());
                        assertFalse(run.rotation.canUseRotation(RELEASE, false));
                        assertFalse(run.rotation.isMotionAccepted());
                        observation.getObservedSuspensionRevision();
                    }
                    assertEquals("pure revocation reads never resample scene", 2, samples.get());
                    assertTrue(run.drain().isEmpty());
                } finally { source.set(observation, original); }
            }
        }

        private static boolean inside(String method) {
            for (StackTraceElement frame : Thread.currentThread().getStackTrace())
                if (RotationManager.class.getName().equals(frame.getClassName()) && method.equals(frame.getMethodName())) return true;
            return false;
        }

        private static void nestedWalkingBoundary(boolean finish) throws Exception {
            try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
                acceptedRelease(run);
                AtomicBoolean once = new AtomicBoolean(true);
                AtomicInteger reentries = new AtomicInteger(), posts = new AtomicInteger();
                float[] nestedReported = {Float.NaN};
                Runnable nested = () -> {
                    run.f.player.rotationYaw = 45;
                    run.f.player.onUpdateWalkingPlayer();
                    try { nestedReported[0] = ScaffoldOwnerContinuityTest.field(EntityPlayerSP.class, "lastReportedYaw").getFloat(run.f.player); }
                    catch (Exception error) { throw new AssertionError(error); }
                    reentries.incrementAndGet();
                };
                PacketPipeline pipeline = run.f.network.getClientPackets();
                Field callback = ScaffoldOwnerContinuityTest.field(finish ? RotationManager.class : PacketPipeline.class,
                        finish ? "clock" : "connected");
                Object original = callback.get(finish ? run.rotation : pipeline);
                if (finish) {
                    LongSupplier clock = (LongSupplier) original;
                    callback.set(run.rotation, (LongSupplier) () -> {
                        if (inside("finishMotion") && once.compareAndSet(true, false)) nested.run();
                        return clock.getAsLong();
                    });
                } else {
                    BooleanSupplier connected = (BooleanSupplier) original;
                    callback.set(pipeline, (BooleanSupplier) () -> {
                        if (inside("resolve") && once.compareAndSet(true, false)) nested.run();
                        return connected.getAsBoolean();
                    });
                }
                Listener<EventMotion> counter = new Listener<>(EventMotion.class, event -> { if (event.isPost()) posts.incrementAndGet(); });
                Pulsar.BUS.subscribe(counter);
                try {
                    List<Object> actual = run.walk();
                    assertEquals("a real lease callback entered nested walking", 1, reentries.get());
                    assertEquals("only nested POST may finish after the frame replacement", 1, posts.get());
                    assertEquals("resolve reentry precedes the outer send; finish reentry follows it", finish ? 2 : 1, movements(actual));
                    assertEquals("old caller cannot overwrite nested reporting", nestedReported[0],
                            ScaffoldOwnerContinuityTest.field(EntityPlayerSP.class, "lastReportedYaw").getFloat(run.f.player), 0);
                    assertEquals(RELEASE, run.rotation.getConfirmedOwner());
                    noActions(actual);
                } finally {
                    Pulsar.BUS.unsubscribe(counter);
                    callback.set(finish ? run.rotation : pipeline, original);
                }
            }
        }

        @Test public void nestedWalkingInsideResolveCannotSendOrPostTheOlderNeutralFrame() throws Exception {
            nestedWalkingBoundary(false);
        }

        @Test public void nestedWalkingInsideFinishCannotOverwriteReportingOrPostTheOlderFrame() throws Exception {
            nestedWalkingBoundary(true);
        }
    }
}
