package me.youngjibbit.core.combat;

import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.world.DemiseRotationHooks;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldSettings;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/** Real Aura selection, mouse-frame hooks, ordinary walking and locally accepted transport. */
public class AuraPreAimIntegrationTest {
    @Test public void rangeControlsPreparationAndNeverExpandsTheAttackBoundary() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "rangeControlsPreparationAndNeverExpandsTheAttackBoundary");
    }
    @Test public void neutronPredictionChangesAcceptedPreAimWithoutMutatingTheLiveBox() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "neutronPredictionChangesAcceptedPreAimWithoutMutatingTheLiveBox");
    }
    @Test public void demisePredictionChangesAcceptedPreAimWithoutMutatingTheLiveBox() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "demisePredictionChangesAcceptedPreAimWithoutMutatingTheLiveBox");
    }
    @Test public void inReachAndStationaryTargetsReceiveNoPredictionLead() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "inReachAndStationaryTargetsReceiveNoPredictionLead");
    }
    @Test public void selectableNeutronNoiseStillReceivesTheIndependentPredictionInput() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "selectableNeutronNoiseStillReceivesTheIndependentPredictionInput");
    }
    @Test public void arsenicPreparationOverridesTheOrdinaryHorizonWithAndWithoutMovingHitpoints() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "arsenicPreparationOverridesTheOrdinaryHorizonWithAndWithoutMovingHitpoints");
    }
    @Test public void arsenicInReachIgnoresPreparatoryPredictionAndRetainsTheOrdinaryController() throws Exception {
        Wave2IsolatedCase.run(Checks.class, "arsenicInReachIgnoresPreparatoryPredictionAndRetainsTheOrdinaryController");
    }

    public static class Checks {
        private static final class Harness implements AutoCloseable {
            final RotationBehaviorHookTest.Fixture fixture = new RotationBehaviorHookTest.Fixture();
            ScaffoldClientFlowTest.Scene scene = ScaffoldClientFlowTest.prepare(fixture, .5, .5);
            RotationManager rotation = fixture.runtime.getRotationManager();
            final RotationBehaviorHookTest.Player target = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
            final AtomicLong clickClock = new AtomicLong(1000);
            final AttackScheduler scheduler = new AttackScheduler(clickClock::get, (min, max) -> 10);
            int attacks, movements;

            Harness(String engine) throws Exception {
                prepareInputAndPose();
                RotationBehaviorHookTest.field(ClientRuntime.class, "attackScheduler").set(fixture.runtime, scheduler);
                target.worldObj = scene;
                target.setEntityId(17);
                moveTarget(5.5, .2);
                scene.loadedEntityList.add(target);
                fixture.aura.getModeSetting("Rot Mode").setValue("Adaptive");
                fixture.aura.getModeSetting("Adaptive Rotation Engine").setValue(engine);
                fixture.aura.getModeSetting("Adaptive Aim Point").setValue("Head");
                fixture.aura.getModeSetting("Adaptive Curve").setValue("Linear");
                fixture.aura.getModeSetting("Adaptive Speed Noise Profile").setValue("Off");
                fixture.aura.getModeSetting("Adaptive Hitpoint").setValue("Stable");
                fixture.aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Off");
                fixture.aura.getNumberSetting("adaptive_path_amount").setValue(0D);
                fixture.aura.getNumberSetting("adaptive_initial_hitpoint_radius").setValue(0D);
                fixture.aura.getNumberSetting("adaptive_min_aim_speed").setValue(720D);
                fixture.aura.getNumberSetting("adaptive_max_aim_speed").setValue(720D);
                fixture.aura.getBooleanSetting("Adaptive Keep Valid Aim").setValue(false);
                fixture.aura.getBooleanSetting("Best Aim Vec").setValue(false);
                fixture.aura.getBooleanSetting("Fail Swing").setValue(false);
                fixture.aura.getNumberSetting("Min CPS").setValue(10D);
                fixture.aura.getNumberSetting("Max CPS").setValue(10D);
                fixture.aura.getNumberSetting("Reach").setValue(3D);
                fixture.aura.onEnable();
            }

            private void prepareInputAndPose() throws Exception {
                fixture.mc.gameSettings.keyBindUseItem = new KeyBinding("preaim.use", 0, "synthetic");
                fixture.mc.gameSettings.keyBindAttack = new KeyBinding("preaim.attack", 0, "synthetic");
                fixture.player.lastTickPosX = fixture.player.posX;
                fixture.player.lastTickPosY = fixture.player.posY;
                fixture.player.lastTickPosZ = fixture.player.posZ;
                fixture.player.rotationYaw = fixture.player.rotationPitch = 0;
                fixture.player.prevRotationYaw = fixture.player.prevRotationPitch = 0;
                RotationBehaviorHookTest.field(net.minecraft.client.entity.EntityPlayerSP.class, "lastReportedYaw")
                        .setFloat(fixture.player, 0);
                RotationBehaviorHookTest.field(net.minecraft.client.entity.EntityPlayerSP.class, "lastReportedPitch")
                        .setFloat(fixture.player, 0);
                RotationBehaviorHookTest.field(net.minecraft.client.entity.EntityPlayerSP.class, "positionUpdateTicks")
                        .setInt(fixture.player, 0);
                fixture.player.sendQueue.clientWorldController = scene;
                RotationBehaviorHookTest.field(PlayerControllerMP.class, "currentGameType")
                        .set(fixture.mc.playerController, WorldSettings.GameType.SPECTATOR);
            }

            void freshArsenicScene() throws Exception {
                // Keep one Minecraft identity for the whole isolated case. Module.mc
                // is static final; replacing it through Unsafe after JIT warmup is not
                // a supported client transition. Fresh scene/controller state gives
                // each comparison the same seed without changing that identity.
                fixture.aura.onDisable();
                rotation.reset();
                scene = ScaffoldClientFlowTest.prepare(fixture, .5, .5);
                rotation = fixture.runtime.getRotationManager();
                prepareInputAndPose();
                target.worldObj = scene;
                scene.loadedEntityList.add(target);
                attacks = movements = 0;
            }

            void moveTarget(double z, double horizontalVelocity) {
                target.posX = .5;
                target.posY = 64;
                target.posZ = z;
                target.lastTickPosX = target.posX - horizontalVelocity;
                target.lastTickPosY = target.posY;
                target.lastTickPosZ = target.posZ;
                target.setEntityBoundingBox(new AxisAlignedBB(.2, 64, z - .3, .8, 65.8, z + .3));
            }

            void range(double blocks) { fixture.aura.getNumberSetting("pre_aim_range").setValue(blocks); }
            void prediction(double ticks) { fixture.aura.getNumberSetting("pre_aim_prediction").setValue(ticks); }

            void walk() {
                // Exercise the same mouse callbacks as EntityRenderer between ordinary 20 Hz steps.
                // These callbacks produce candidates only; the next walking C03 grants ownership.
                for (int frame = 0; frame < 3; frame++) {
                    scene.motionTime.addAndGet(16_666_667L);
                    DemiseRotationHooks.mousePre();
                    DemiseRotationHooks.mouseUpdate();
                }
                scheduler.beginTick();
                clickClock.addAndGet(150);
                ScaffoldClientFlowTest.ordinaryWalking(fixture);
                Object packet;
                while ((packet = fixture.channel.readOutbound()) != null) {
                    if (packet instanceof C03PacketPlayer) movements++;
                    if (packet instanceof C02PacketUseEntity) {
                        assertEquals(C02PacketUseEntity.Action.ATTACK, ((C02PacketUseEntity) packet).getAction());
                        assertSame(target, ((C02PacketUseEntity) packet).getEntityFromWorld(scene));
                        attacks++;
                    }
                }
            }

            float settle() throws Exception {
                return settle(fixture.aura.getModeSetting("Rot Mode").getValue());
            }

            float settle(String label) throws Exception {
                for (int tick = 0; tick < 30; tick++) walk();
                assertTrue("the ordinary movement transport must actually be accepted", rotation.isMotionAccepted());
                assertEquals("aura", rotation.getConfirmedOwner());
                net.minecraft.entity.EntityLivingBase selected = fixture.aura.getActiveTarget();
                if (selected != target) {
                    // Unsafe-created players have no GameProfile, so Entity.toString
                    // obscures an assertSame failure. Keep the exact identity check
                    // and report only primitives for the fixture's scene boundaries.
                    Class<?> type = fixture.aura.getClass();
                    java.lang.reflect.Method usable = type.getDeclaredMethod("usable");
                    usable.setAccessible(true);
                    java.lang.reflect.Method options = type.getDeclaredMethod("acquisitionOptions");
                    options.setAccessible(true);
                    fail(label + ": selected identity=" + System.identityHashCode(selected)
                            + ", expected=" + System.identityHashCode(target)
                            + ", field=" + System.identityHashCode(RotationBehaviorHookTest.field(type, "target").get(fixture.aura))
                            + ", usable=" + usable.invoke(fixture.aura)
                            + ", worldContext=" + (RotationBehaviorHookTest.field(type, "worldContext").get(fixture.aura) == scene)
                            + ", playerContext=" + (RotationBehaviorHookTest.field(type, "playerContext").get(fixture.aura) == fixture.player)
                            + ", singleton=" + (net.minecraft.client.Minecraft.getMinecraft() == fixture.mc)
                            + ", moduleClient=" + (RotationBehaviorHookTest.field(me.youngjibbit.module.Module.class, "mc").get(null) == fixture.mc)
                            + ", runtime=" + (me.youngjibbit.core.bootstrap.RuntimeServices.runtime() == fixture.runtime)
                            + ", scene=" + (fixture.mc.theWorld == scene)
                            + ", entityWorld=" + (target.worldObj == scene)
                            + ", entityId=" + (scene.getEntityByID(target.getEntityId()) == target)
                            + ", loaded=" + scene.loadedEntityList.contains(target)
                            + ", valid=" + me.youngjibbit.core.bootstrap.RuntimeServices.targeting().isValidTarget(target,
                            (TargetingService.Options) options.invoke(fixture.aura)));
                }
                assertTrue(Float.isFinite(rotation.getEffectiveYaw()));
                assertTrue(Float.isFinite(rotation.getEffectivePitch()));
                assertEquals("pre-aim is silent; it must not turn the camera", 0, fixture.player.rotationYaw, 0);
                return rotation.getEffectiveYaw();
            }

            void assertLiveGeometry(AxisAlignedBB box, double z, double lastX) {
                assertSame("prediction must not replace or expand the live hitbox", box, target.getEntityBoundingBox());
                assertEquals(.5, target.posX, 0);
                assertEquals(64, target.posY, 0);
                assertEquals(z, target.posZ, 0);
                assertEquals(lastX, target.lastTickPosX, 0);
            }

            @Override public void close() throws Exception {
                try { fixture.aura.onDisable(); }
                finally { fixture.close(); }
            }
        }

        @Test public void rangeControlsPreparationAndNeverExpandsTheAttackBoundary() throws Exception {
            try (Harness h = new Harness("Neutron")) {
                h.walk();
                assertNull("the default range cannot acquire this remote target", h.fixture.aura.getActiveTarget());
                assertNull(h.rotation.getConfirmedOwner());
                h.range(6);
                h.settle();
                assertEquals("a preparation target is not an attack target", 0, h.attacks);
                assertTrue(h.movements > 0);
                assertEquals(3, h.fixture.aura.getNumberSetting("Reach").getValue(), 0);

                h.range(3);
                h.walk();
                assertNull("lowering the slider revokes the old preparation target", h.fixture.aura.getActiveTarget());
                assertNull(h.rotation.getConfirmedOwner());
                h.range(6);
                h.settle();
                h.fixture.aura.getBooleanSetting("Rotations").setValue(false);
                h.walk();
                assertNull("rotations off restricts acquisition to the actual attack reach", h.fixture.aura.getActiveTarget());
                assertNull(h.rotation.getConfirmedOwner());
                assertEquals(0, h.attacks);

                h.fixture.aura.getBooleanSetting("Rotations").setValue(true);
                h.range(1);
                h.moveTarget(2.5, 0);
                h.settle();
                assertTrue("positive control: a smaller preparation range does not shrink valid attack reach", h.attacks > 0);
                assertEquals(3, h.fixture.aura.getNumberSetting("Reach").getValue(), 0);
            }
        }

        private static void checkPrediction(String engine) throws Exception {
            try (Harness h = new Harness(engine)) {
                // Isolate prediction geometry from Demise's reference low-gain integer
                // mouse-count deadband. The separate frame/parity suites exercise .15.
                if ("Demise".equals(engine))
                    h.fixture.aura.getNumberSetting("Demise Extra Smoothing").setValue(1D);
                h.range(6);
                AxisAlignedBB box = h.target.getEntityBoundingBox();
                h.prediction(0);
                float current = h.settle();
                assertEquals("zero prediction tracks the current horizontal position", 0, current, .35);
                h.prediction(1);
                float oneTick = h.settle();
                h.prediction(2);
                float twoTicks = h.settle();
                assertTrue("one tick must lead a target moving right: " + oneTick, oneTick < current - 1);
                assertTrue("the larger slider value must cause a larger lead: " + oneTick + " / " + twoTicks,
                        twoTicks < oneTick - 1);
                h.prediction(0);
                assertEquals("turning prediction back to zero removes the lead", current, h.settle(), .35);
                h.assertLiveGeometry(box, 5.5, .3);
                assertEquals("no prediction value expands attack reach", 0, h.attacks);
            }
        }

        @Test public void neutronPredictionChangesAcceptedPreAimWithoutMutatingTheLiveBox() throws Exception {
            checkPrediction("Neutron");
        }

        @Test public void demisePredictionChangesAcceptedPreAimWithoutMutatingTheLiveBox() throws Exception {
            checkPrediction("Demise");
        }

        @Test public void inReachAndStationaryTargetsReceiveNoPredictionLead() throws Exception {
            for (String engine : new String[]{"Neutron", "Demise"}) {
                try (Harness h = new Harness(engine)) {
                    // The reference Demise default (.15) deliberately has a slow
                    // mouse-count deadband. This case tests reach/prediction, not
                    // that convergence behavior (covered by the frame parity tests).
                    // Use the same deterministic full-gain setting as checkPrediction.
                    if ("Demise".equals(engine))
                        h.fixture.aura.getNumberSetting("Demise Extra Smoothing").setValue(1D);
                    h.range(6);
                    h.prediction(3);
                    h.moveTarget(2.5, .2);
                    assertEquals("a moving target within Reach uses its current point", 0, h.settle(), .35);
                    assertTrue(engine + ": the in-range assertion must exercise a live attack path", h.attacks > 0);
                    int inRangeAttacks = h.attacks;
                    h.moveTarget(5.5, 0);
                    AxisAlignedBB box = h.target.getEntityBoundingBox();
                    assertEquals("a stationary preparation target has zero lead", 0, h.settle(), .35);
                    h.assertLiveGeometry(box, 5.5, .5);
                    assertEquals(inRangeAttacks, h.attacks);
                }
            }
        }

        @Test public void selectableNeutronNoiseStillReceivesTheIndependentPredictionInput() throws Exception {
            try (Harness h = new Harness("Demise")) {
                h.range(6);
                h.fixture.aura.getModeSetting("Adaptive Aim Point").setValue("Neutron Noise");
                h.fixture.aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Custom");
                h.fixture.aura.getModeSetting("Adaptive Legacy Hitpoint Algorithm").setValue("ValueCubic");
                h.fixture.aura.getNumberSetting("adaptive_legacy_hitpoint_radius").setValue(5D);
                h.fixture.aura.getNumberSetting("adaptive_legacy_hitpoint_travel_speed").setValue(1D);
                AxisAlignedBB box = h.target.getEntityBoundingBox();
                float unpredicted = h.settle();
                h.prediction(2);
                float predicted = h.settle();
                assertTrue("prediction remains independent of the selected moving in-box point",
                        predicted < unpredicted - 2);
                assertEquals("Neutron Noise", h.fixture.aura.getModeSetting("Adaptive Aim Point").getValue());
                h.assertLiveGeometry(box, 5.5, .3);
                assertEquals(0, h.attacks);
            }
        }

        private static float[] arsenicTrace(Harness h, String mode, boolean variation, double ordinaryTicks,
                                             double preparationTicks, boolean outsideReach) throws Exception {
            h.freshArsenicScene();
            h.fixture.aura.getModeSetting("Rot Mode").setValue(mode);
            h.fixture.aura.getNumberSetting("Prediction Ticks").setValue(ordinaryTicks);
            h.fixture.aura.getNumberSetting("Miss Chance").setValue(0D);
            h.fixture.aura.getBooleanSetting("Arsenic Soft Motion").setValue(false);
            h.fixture.aura.getBooleanSetting("Hitpoint Variation").setValue(variation);
            h.fixture.aura.getModeSetting("Hitpoint Profile").setValue("Ausgewogen");
            h.fixture.aura.getNumberSetting("Hitpoint Strength").setValue(.5D);
            h.fixture.aura.getNumberSetting("Hitpoint Speed").setValue(1D);
            h.range(6);
            h.prediction(preparationTicks);
            double z = outsideReach ? 5.5 : 2.5;
            h.moveTarget(z, .2);
            AxisAlignedBB box = h.target.getEntityBoundingBox();
            h.fixture.aura.onEnable();
            h.settle(mode + ", variation=" + variation + ", ordinary=" + ordinaryTicks
                    + ", preparation=" + preparationTicks + ", outside=" + outsideReach);
            float[] trace = new float[40];
            for (int sample = 0; sample < trace.length; sample += 2) {
                h.walk();
                assertTrue(h.rotation.isMotionAccepted());
                assertEquals("aura", h.rotation.getConfirmedOwner());
                trace[sample] = h.rotation.getEffectiveYaw();
                trace[sample + 1] = h.rotation.getEffectivePitch();
            }
            h.assertLiveGeometry(box, z, .3);
            assertEquals("the preparatory slider must not overwrite the ordinary setting",
                    ordinaryTicks, h.fixture.aura.getNumberSetting("Prediction Ticks").getValue(), 0);
            assertEquals(3, h.fixture.aura.getNumberSetting("Reach").getValue(), 0);
            if (outsideReach) assertEquals("Arsenic preparation cannot authorize a C02 attack", 0, h.attacks);
            return trace;
        }

        private static double averageYaw(float[] trace) {
            double sum = 0;
            for (int sample = 0; sample < trace.length; sample += 2) sum += trace[sample];
            return sum / (trace.length / 2);
        }

        private static boolean different(float[] first, float[] second) {
            for (int sample = 0; sample < first.length; sample++)
                if (Math.abs(first[sample] - second[sample]) > .01) return true;
            return false;
        }

        @Test public void arsenicPreparationOverridesTheOrdinaryHorizonWithAndWithoutMovingHitpoints() throws Exception {
            try (Harness h = new Harness("Neutron")) {
                for (String mode : new String[]{"Arsenic Lazy", "Arsenic Heuristics"}) {
                    float[] withoutVariation = null;
                    for (boolean variation : new boolean[]{false, true}) {
                        String label = mode + ", moving hitpoint=" + variation;
                        // Each scene uses the same seed and time sequence. Equality checks
                        // isolate the horizon from the controller's intentional wander.
                        float[] noPrediction = arsenicTrace(h, mode, variation, 0, 0, true);
                        float[] ordinaryOnly = arsenicTrace(h, mode, variation, 3, 0, true);
                        assertArrayEquals(label + ": zero preparation must suppress the nonzero ordinary horizon",
                                noPrediction, ordinaryOnly, 0);
                        float[] preparation = arsenicTrace(h, mode, variation, 3, 3, true);
                        double lead = averageYaw(noPrediction) - averageYaw(preparation);
                        // A .2 block/tick target over three ticks gives about 6.8 degrees
                        // at this distance. Half-strength hitpoint blending must not halve
                        // that independent lead; it must also work with variation disabled.
                        assertTrue(label + ": the full independent preparatory lead must reach accepted C03, got " + lead,
                                lead > 4);
                        if (!variation) withoutVariation = noPrediction;
                        else assertTrue(mode + ": the enabled moving point must still affect the accepted path",
                                different(withoutVariation, noPrediction));
                    }
                }
            }
        }

        @Test public void arsenicInReachIgnoresPreparatoryPredictionAndRetainsTheOrdinaryController() throws Exception {
            try (Harness h = new Harness("Neutron")) {
                for (String mode : new String[]{"Arsenic Lazy", "Arsenic Heuristics"}) {
                    for (boolean variation : new boolean[]{false, true}) {
                        String label = mode + ", moving hitpoint=" + variation;
                        float[] ordinary = arsenicTrace(h, mode, variation, 3, 0, false);
                        float[] preparation = arsenicTrace(h, mode, variation, 3, 3, false);
                        assertArrayEquals(label + ": an in-Reach target must retain the complete ordinary path",
                                ordinary, preparation, 0);
                        if ("Arsenic Lazy".equals(mode) && !variation) {
                            float[] noPrediction = arsenicTrace(h, mode, false, 0, 0, false);
                            assertTrue("positive control: the historical Lazy horizon remains active inside Reach",
                                    averageYaw(noPrediction) - averageYaw(ordinary) > 8);
                        }
                    }
                }
            }
        }
    }
}
