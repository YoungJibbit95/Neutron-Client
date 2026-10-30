package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.combat.TargetingService;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.Scaffold;
import me.zero.alpine.event.EventPriority;
import me.zero.alpine.listener.Listener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldSettings;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Real walking/C03 acceptance with synthetic world and embedded transport; no game/server claim. */
public class ScaffoldReleaseWave2Test {
    private static final String RELEASE = "scaffold-release";

    private static void noAutomaticActions(List<Object> packets) {
        for (Object packet : packets) {
            assertFalse("disabled Scaffold emits no C08", packet instanceof C08PacketPlayerBlockPlacement);
            assertFalse("disabled Scaffold emits no C09", packet instanceof C09PacketHeldItemChange);
            assertFalse("release is not a swing right", packet instanceof C0APacketAnimation);
            assertFalse("release is not an attack right", packet instanceof C02PacketUseEntity);
        }
    }

    private static int movements(List<Object> packets) {
        int count = 0;
        for (Object packet : packets) if (packet instanceof C03PacketPlayer) count++;
        return count;
    }

    private static void releaseAccepted(ScaffoldOwnerContinuityTest.Run run) {
        assertEquals("only a newly accepted release frame owns this source", RELEASE, run.rotation.getConfirmedOwner());
        assertTrue(run.rotation.canUseRotation(RELEASE, false));
        assertFalse("disable revokes the actual Scaffold action source", run.rotation.canUseRotation("scaffold", false));
        assertFalse(run.f.scaffold.isToggled());
        assertFalse(run.f.scaffold.correctionEnabled());
    }

    private static float distanceFromCamera(ScaffoldOwnerContinuityTest.Run run) {
        return (float) Math.hypot(MathHelper.wrapAngleTo180_float(run.rotation.getEffectiveYaw() - run.f.player.rotationYaw),
                run.rotation.getEffectivePitch() - run.f.player.rotationPitch);
    }

    private static void disableAccepted(ScaffoldOwnerContinuityTest.Run run) throws Exception {
        run.walk();
        assertEquals("precondition: actual Scaffold C03 accepted", "scaffold", run.rotation.getConfirmedOwner());
        assertTrue("test pose differs from the camera", distanceFromCamera(run) > 1);
        run.f.scaffold.setToggled(false);
        assertNull("arming is not source acceptance", run.rotation.getConfirmedOwner());
        assertFalse(run.rotation.canUseRotation("scaffold", false));
        assertNull(ScaffoldOwnerContinuityTest.field(Scaffold.class, "currentTarget").get(run.f.scaffold));
        assertNull(ScaffoldOwnerContinuityTest.field(Scaffold.class, "frameTarget").get(run.f.scaffold));
        assertEquals(1, run.f.mc.timer.timerSpeed, 0);
        noAutomaticActions(run.drain());
    }

    @Test public void twoRealReleaseFramesThenThirdOuterAdvanceEndsRotationWithoutActionsOrCameraWrites() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            run.f.scaffold.getNumberSetting("Timer").setValue(1.6D);
            disableAccepted(run);
            assertEquals("first accepted outer ID is a valid zero", 0, run.f.runtime.getObservation().getOuterTickId());
            assertEquals("disable occurred between real tick lifetimes", -1, run.f.runtime.getObservation().getActiveOuterTickId());
            float cameraYaw = run.f.player.rotationYaw, cameraPitch = run.f.player.rotationPitch;
            float previousDistance = distanceFromCamera(run);
            for (int i = 0; i < 2; i++) {
                List<Object> packets = run.walk();
                noAutomaticActions(packets);
                assertEquals("one C03 from the existing walking body", 1, movements(packets));
                releaseAccepted(run);
                float distance = distanceFromCamera(run);
                assertTrue("release is a bounded intermediate pose", distance > 0 && distance < previousDistance);
                previousDistance = distance;
                assertEquals(cameraYaw, run.f.player.rotationYaw, 0);
                assertEquals(cameraPitch, run.f.player.rotationPitch, 0);
                assertEquals(1, run.f.mc.timer.timerSpeed, 0);
                assertEquals(64, run.f.player.inventory.mainInventory[3].stackSize);
                assertEquals(0, run.f.player.inventory.currentItem);
            }
            List<Object> packets = run.walk();
            noAutomaticActions(packets);
            assertEquals(1, movements(packets));
            assertNull("third real outer advance is the hard owner deadline", run.rotation.getConfirmedOwner());
            assertEquals(cameraYaw, run.rotation.getEffectiveYaw(), 0);
            assertEquals(cameraPitch, run.rotation.getEffectivePitch(), 0);
        }
    }

    @Test public void monotoneDeadlineRevokesAtTheNextQueryWithoutATickOrPacket() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            long started = run.scene.motionTime.get();
            noAutomaticActions(run.walk());
            releaseAccepted(run);
            long outer = run.f.runtime.getObservation().getOuterTickId();
            float wireYaw = run.rotation.getEffectiveYaw(), wirePitch = run.rotation.getEffectivePitch();
            run.scene.motionTime.set(started + 200_000_000L);
            assertNull("200ms is an inclusive hard deadline", run.rotation.getConfirmedOwner());
            assertFalse("expired release cannot become unowned numerical action authority", run.rotation.canUseRotation(false));
            assertEquals(outer, run.f.runtime.getObservation().getOuterTickId());
            assertEquals(-1, run.f.runtime.getObservation().getActiveOuterTickId());
            assertEquals("expiry retains actual wire history", wireYaw, run.rotation.getEffectiveYaw(), 0);
            assertEquals(wirePitch, run.rotation.getEffectivePitch(), 0);
            assertTrue("query is packet free", run.drain().isEmpty());
        }
    }

    @Test public void ordinaryAndEarnedExtraShareOneReleaseProgressAndDoNotCreateExtraActions() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            RuntimeObservation observation = run.f.runtime.getObservation();
            List<Float> yaws = new ArrayList<>();
            List<Long> ticks = new ArrayList<>();
            run.scene.motionTime.addAndGet(50_000_000L);
            observation.beginOuterTick();
            try {
                observation.observePlayerStep(run.f.player, () -> {
                    run.f.player.onUpdateWalkingPlayer();
                    releaseAccepted(run);
                    ticks.add(observation.getActiveOuterTickId());
                    yaws.add(run.rotation.getEffectiveYaw());
                    return true;
                }, step -> { step.getAsBoolean(); step.getAsBoolean(); });
            } finally { observation.endOuterTick(); }
            List<Object> packets = run.drain();
            noAutomaticActions(packets);
            assertEquals("one normal movement per body, not lease-generated packets", 2, movements(packets));
            assertEquals(2, ticks.size());
            assertEquals(ticks.get(0), ticks.get(1));
            assertEquals("extra body does not advance the release trajectory", yaws.get(0), yaws.get(1), .0001f);
            noAutomaticActions(run.walk());
            releaseAccepted(run);
            noAutomaticActions(run.walk());
            assertNull(run.rotation.getConfirmedOwner());
        }
    }

    @Test public void withheldTicksCountWithoutInventingMotionBodiesOrPackets() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            noAutomaticActions(run.walk());
            releaseAccepted(run);
            RuntimeObservation observation = run.f.runtime.getObservation();
            for (int i = 0; i < 2; i++) {
                observation.beginOuterTick();
                try {
                    observation.observePlayerStep(run.f.player, () -> { fail("withheld never enters a body"); return true; }, step -> { });
                    assertNotNull(observation.getLastWithheld());
                } finally { observation.endOuterTick(); }
                assertTrue(run.drain().isEmpty());
                if (i == 0) releaseAccepted(run);
            }
            assertNull("third real tick expires even with no walking callback", run.rotation.getConfirmedOwner());
            assertFalse(run.rotation.canUseRotation(false));
            assertEquals(64, run.f.player.inventory.mainInventory[3].stackSize);
        }
    }

    @Test public void cancelledDelayedOrRewrittenReleaseC03NeverConfirmsAnOwnerAndLateReplayCannotRenew() throws Exception {
        for (int mode = 0; mode < 3; mode++) try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            final int scenario = mode;
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.packet = event -> {
                if (event.isPre() && event.getPacket() instanceof C03PacketPlayer) {
                    if (scenario == 0) event.cancel();
                    if (scenario == 2) event.setPacket(new C03PacketPlayer.C05PacketPlayerLook(79, 11, true));
                }
            };
            if (mode == 1) run.f.network.getClientPackets().delays().request("wave2-release", 200,
                    (packet, flow) -> packet instanceof C03PacketPlayer);
            Pulsar.BUS.subscribe(hooks);
            try {
                List<Object> packets = run.walk();
                noAutomaticActions(packets);
                assertEquals("rejected release C03 never reaches transport", 0, movements(packets));
                assertNull(run.rotation.getConfirmedOwner());
                assertFalse(run.rotation.canUseRotation("scaffold", false));
            } finally { Pulsar.BUS.unsubscribe(hooks); }
            run.scene.motionTime.addAndGet(200_000_000L);
            if (mode == 1) run.f.network.getClientPackets().delays().release("wave2-release");
            List<Object> replay = run.drain();
            noAutomaticActions(replay);
            assertEquals("held frame's original ticket cannot revive after its lifetime", 0, movements(replay));
            assertNull(run.rotation.getConfirmedOwner());
        }
    }

    @Test public void packetPostEpochInvalidationKeepsTransportFactButDeniesReleaseAuthority() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            int[] callbacks = {0};
            hooks.packet = event -> {
                if (event.isPost() && event.getPacket() instanceof C03PacketPlayer) {
                    callbacks[0]++;
                    run.f.network.getClientPackets().worldChanged();
                }
            };
            Pulsar.BUS.subscribe(hooks);
            List<Object> packets;
            try { packets = run.walk(); } finally { Pulsar.BUS.unsubscribe(hooks); }
            assertEquals(1, callbacks[0]);
            assertEquals("already transmitted movement remains a transport fact", 1, movements(packets));
            noAutomaticActions(packets);
            assertNull(run.rotation.getConfirmedOwner());
            assertFalse(run.rotation.canUseRotation(RELEASE, false));
        }
    }

    @Test public void observedGuiFocusPauseAndSceneBoundariesPermanentlyEndTheLease() throws Exception {
        for (int mode = 0; mode < 7; mode++) try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            noAutomaticActions(run.walk());
            releaseAccepted(run);
            RuntimeObservation observation = run.f.runtime.getObservation();
            if (mode == 0) run.f.mc.currentScreen = new GuiChat();
            if (mode == 1) run.f.mc.inGameHasFocus = false;
            if (mode == 2) ScaffoldOwnerContinuityTest.field(Minecraft.class, "isGamePaused").setBoolean(run.f.mc, true);
            if (mode == 3) run.f.network.getClientPackets().worldChanged();
            if (mode == 4) observation.invalidateScene();
            if (mode == 5) { run.f.mc.theWorld = run.f.world; observation.captureScene(); run.f.mc.theWorld = run.scene; }
            if (mode == 6) run.f.player.isDead = true;
            // This existing observer capture records even suspension->resume between lease queries.
            observation.captureScene();
            run.f.mc.currentScreen = null;
            run.f.mc.inGameHasFocus = true;
            ScaffoldOwnerContinuityTest.field(Minecraft.class, "isGamePaused").setBoolean(run.f.mc, false);
            run.f.player.isDead = false;
            observation.captureScene();
            assertNull("observed boundary cannot be undone by returning to scene A: " + mode, run.rotation.getConfirmedOwner());
            noAutomaticActions(run.walk());
            assertNull("no stale lease revival after boundary " + mode, run.rotation.getConfirmedOwner());
        }
    }

    @Test public void reenableCancelsTheOldReleaseAndRequiresAFreshScaffoldC03() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            noAutomaticActions(run.walk());
            releaseAccepted(run);
            run.f.scaffold.setToggled(true);
            assertNull("new generation inherits neither old lease nor accepted source", run.rotation.getConfirmedOwner());
            assertFalse(run.rotation.canUseRotation("scaffold", false));
            run.walk();
            assertEquals("normal enabled listener reacquires only through fresh C03", "scaffold", run.rotation.getConfirmedOwner());
        }
    }

    @Test public void foreignAuraOrVisibleAimRequestWinsAndOldReleaseDoesNotReturn() throws Exception {
        for (boolean visible : new boolean[]{false, true}) try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            noAutomaticActions(run.walk());
            releaseAccepted(run);
            ScaffoldOwnerContinuityTest.Hooks hooks = new ScaffoldOwnerContinuityTest.Hooks();
            hooks.motion = event -> {
                if (!event.isPre()) return;
                if (visible) run.rotation.requestVisible("aimassist", RotationManager.AIM_ASSIST_PRIORITY, 12, 3,
                        run.f.player.rotationYaw, run.f.player.rotationPitch, () -> true);
                else run.rotation.request("aura", RotationManager.AURA_PRIORITY, 12, 3);
            };
            Pulsar.BUS.subscribe(hooks);
            try { noAutomaticActions(run.walk()); } finally { Pulsar.BUS.unsubscribe(hooks); }
            assertEquals(visible ? "aimassist" : "aura", run.rotation.getConfirmedOwner());
            if (visible) assertEquals("visible assistance keeps its existing camera semantics", 12, run.f.player.rotationYaw, 0);
            else assertEquals("silent Aura does not change the camera", 0, run.f.player.rotationYaw, 0);
            noAutomaticActions(run.walk());
            assertNull("a foreign override permanently ends the old release", run.rotation.getConfirmedOwner());
        }
    }

    @Test public void disableWithoutPriorScaffoldAcceptanceNeverStartsARelease() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            run.noSupport();
            noAutomaticActions(run.walk());
            assertNull(run.rotation.getConfirmedOwner());
            run.f.scaffold.setToggled(false);
            noAutomaticActions(run.walk());
            assertNull(run.rotation.getConfirmedOwner());
            assertEquals(0, run.rotation.getEffectiveYaw(), 0);
            assertEquals(0, run.rotation.getEffectivePitch(), 0);
        }
    }

    private void reservedSource(boolean visible) throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            run.rotation.bindConnection(run.f.network.getClientPackets());
            run.rotation.beginMotion(run.f.player, run.scene, 0, 0);
            if (visible) run.rotation.requestVisible(RELEASE, Integer.MAX_VALUE, 45, 10, 0, 0, () -> true);
            else run.rotation.request(RELEASE, Integer.MAX_VALUE, 45, 10);
            EventMotion motion = new EventMotion(.5, 64, .5, 0, 0, true);
            run.rotation.resolve(motion);
            run.rotation.finishMotion(motion.getYaw(), motion.getPitch(), true);
            assertNull("public requests cannot forge the internal lifecycle source", run.rotation.getConfirmedOwner());
            assertEquals(0, run.f.player.rotationYaw, 0);
            assertEquals(0, run.f.player.rotationPitch, 0);
            assertTrue(run.drain().isEmpty());
        }
    }

    @Test public void ordinaryRequestCannotSpoofTheReservedReleaseSource() throws Exception { reservedSource(false); }
    @Test public void visibleRequestCannotSpoofTheReservedReleaseSource() throws Exception { reservedSource(true); }

    @Test public void auraRotationsOffCannotBorrowAReleaseFrameEvenWhenItsActualWireRayHits() throws Exception {
        try (ScaffoldOwnerContinuityTest.Run run = new ScaffoldOwnerContinuityTest.Run("Post")) {
            disableAccepted(run);
            run.f.mc.gameSettings.keyBindUseItem = new KeyBinding("wave2.use", 0, "synthetic");
            run.f.player.sendQueue.clientWorldController = run.scene;
            ScaffoldOwnerContinuityTest.field(World.class, "loadedEntityList").set(run.scene, new ArrayList<>());
            ScaffoldOwnerContinuityTest.field(PlayerControllerMP.class, "currentGameType").set(run.f.mc.playerController,
                    WorldSettings.GameType.SPECTATOR);
            run.f.aura.getBooleanSetting("Rotations").setValue(false);
            int[] checked = {0};
            Listener<EventMotion> beforeAura = new Listener<>(EventMotion.class, event -> {
                if (!event.isPost()) return;
                releaseAccepted(run);
                float yaw = run.rotation.getEffectiveYaw(), pitch = run.rotation.getEffectivePitch();
                double radians = Math.toRadians(yaw);
                RotationBehaviorHookTest.Player target;
                try {
                    target = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
                    target.worldObj = run.scene;
                    target.setEntityId(62);
                    target.posX = run.f.player.posX - Math.sin(radians) * 2;
                    target.posY = 64;
                    target.posZ = run.f.player.posZ + Math.cos(radians) * 2;
                    target.setEntityBoundingBox(new AxisAlignedBB(target.posX - .3, 64, target.posZ - .3,
                            target.posX + .3, 65.8, target.posZ + .3));
                    ScaffoldOwnerContinuityTest.field(World.class, "loadedEntityList").set(run.scene,
                            new ArrayList<>(Collections.singletonList(target)));
                    ScaffoldOwnerContinuityTest.field(Aura.class, "target").set(run.f.aura, target);
                    ScaffoldOwnerContinuityTest.field(Aura.class, "worldContext").set(run.f.aura, run.scene);
                    ScaffoldOwnerContinuityTest.field(Aura.class, "playerContext").set(run.f.aura, run.f.player);
                } catch (Exception error) { throw new AssertionError(error); }
                assertTrue(run.f.runtime.getTargetingService().isValidTarget(target, new TargetingService.Options()));
                assertTrue("denial is ownership, not a missing target or failed wire ray",
                        run.f.runtime.getRaycastService().canHit(target, yaw, pitch, 3));
                checked[0]++;
            }, EventPriority.HIGHEST);
            Pulsar.BUS.subscribe(beforeAura);
            run.f.aura.onEnable();
            try {
                List<Object> packets = run.walk();
                assertEquals("actual release POST and positive target reached", 1, checked[0]);
                assertFalse(run.f.runtime.getAttackScheduler().hasAttackThisTick());
                noAutomaticActions(packets);
            } finally {
                run.f.aura.onDisable();
                Pulsar.BUS.unsubscribe(beforeAura);
            }
        }
    }
}
