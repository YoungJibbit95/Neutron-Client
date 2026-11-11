package me.youngjibbit.core.combat;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.packet.EventPacket;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import me.zero.alpine.listener.Subscriber;
import me.youngjibbit.core.world.ScaffoldClientFlowTest;
import me.youngjibbit.module.Module;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.Entity;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Real Aura + patched walking/packet hook plus controlled RotationManager PRE negative fixtures. */
public class AdaptiveAuraIntegrationTest {
    // The fixture replaces static-final Minecraft references with Unsafe. Isolate cases
    // under the ordinary Java 8 JIT so a previous fixture cannot be constant-folded.
    @Test public void actualAuraWalkingHookEmitsOnlyExistingC03AndAcceptedGcdGrid() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "actualAuraWalkingHookEmitsOnlyExistingC03AndAcceptedGcdGrid");
    }
    @Test public void relativeNearestAtLowSpeedReachesARealRayOnEverySensitivityGrid() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "relativeNearestAtLowSpeedReachesARealRayOnEverySensitivityGrid");
    }
    @Test public void subCountAndMixedMotionIntervalsEarnBoundedMouseCountCredit() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "subCountAndMixedMotionIntervalsEarnBoundedMouseCountCredit");
    }
    @Test public void targetSwitchLosesOldAcquisitionAndDoesNotReplayItsTurn() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "targetSwitchLosesOldAcquisitionAndDoesNotReplayItsTurn");
    }
    @Test public void validTargetRayHoldsWithoutNoiseDriftAndMovingTargetResumesTheSameScopedEngine() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "validTargetRayHoldsWithoutNoiseDriftAndMovingTargetResumesTheSameScopedEngine");
    }
    @Test public void cancelledRealC03RevokesOwnershipAndRestartsWithoutCatchup() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "cancelledRealC03RevokesOwnershipAndRestartsWithoutCatchup");
    }
    @Test public void sameSequencePreReentryAndDifferentRenderCadenceDoNotConsumeExtraMotion() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "sameSequencePreReentryAndDifferentRenderCadenceDoNotConsumeExtraMotion");
    }
    @Test public void targetFlappingInsideOneMotionSequenceCannotReacquireRepeatedly() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "targetFlappingInsideOneMotionSequenceCannotReacquireRepeatedly");
    }
    @Test public void sensitivityChangeCannotReuseThePreviousGridOrSpendTheSameMotionTwice() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "sensitivityChangeCannotReuseThePreviousGridOrSpendTheSameMotionTwice");
    }
    @Test public void rejectedC03AndForeignOwnerCannotRetainAdaptiveAuthority() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "rejectedC03AndForeignOwnerCannotRetainAdaptiveAuthority");
    }
    @Test public void adaptivePresetThroughRealWalkingHookKeepsAcceptedC03AndNoC02() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "adaptivePresetThroughRealWalkingHookKeepsAcceptedC03AndNoC02");
    }
    @Test public void dynamicHitpointTargetSwitchAndDisableStartFreshMotion() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "dynamicHitpointTargetSwitchAndDisableStartFreshMotion");
    }
    @Test public void guiFocusDisableAndConnectionBoundaryInvalidateWithoutGhostAcquisition() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "guiFocusDisableAndConnectionBoundaryInvalidateWithoutGhostAcquisition");
    }
    @Test public void independentLegacyHitpointUsesTheAcceptedGridAndGlobalSpeedCap() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "independentLegacyHitpointUsesTheAcceptedGridAndGlobalSpeedCap");
    }
    @Test public void keepValidAimStopsEvenMaximumIndependentLegacyNoise() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "keepValidAimStopsEvenMaximumIndependentLegacyNoise");
    }
    @Test public void independentPointStateCannotSurviveRejectedTransportOrReconfiguration() throws Exception {
        me.youngjibbit.core.world.Wave2IsolatedCase.run(Checks.class, "independentPointStateCannotSurviveRejectedTransportOrReconfiguration");
    }

    public static class Checks {
    private static final class Harness implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f;
        final ScaffoldClientFlowTest.Scene scene;
        final RotationManager rotation;
        final RotationBehaviorHookTest.Player first;
        boolean sawC03;

        Harness() throws Exception {
            f = new RotationBehaviorHookTest.Fixture();
            scene = ScaffoldClientFlowTest.prepare(f, .5, .48);
            rotation = f.runtime.getRotationManager();
            first = target(-1, 2);
            setTarget(first);
            f.player.usingItem = true; // Do not perform a real attack; this test verifies movement/ownership.
            f.aura.getBooleanSetting("Best Aim Vec").setValue(false);
            f.aura.getModeSetting("Rot Mode").setValue("Adaptive");
            f.aura.onEnable();
        }

        RotationBehaviorHookTest.Player target(double x, double z) throws Exception {
            RotationBehaviorHookTest.Player entity =
                    RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
            entity.posX = entity.lastTickPosX = x;
            entity.posY = entity.lastTickPosY = 64;
            entity.posZ = entity.lastTickPosZ = z;
            entity.worldObj = scene;
            entity.setEntityBoundingBox(new AxisAlignedBB(x - .3, 64, z - .3, x + .3, 65.8, z + .3));
            return entity;
        }

        void setTarget(RotationBehaviorHookTest.Player target) throws Exception {
            RotationBehaviorHookTest.field(World.class, "loadedEntityList").set(scene,
                    new ArrayList<Entity>(Collections.<Entity>singletonList(target)));
        }

        void clearTargets() throws Exception {
            RotationBehaviorHookTest.field(World.class, "loadedEntityList").set(scene, new ArrayList<Entity>());
        }

        void drain() {
            Object packet;
            while ((packet = f.channel.readOutbound()) != null) {
                if (packet instanceof C03PacketPlayer) sawC03 = true;
                assertFalse("Adaptive must never directly emit an attack", packet instanceof C02PacketUseEntity);
            }
        }

        void walk(int tick) {
            walkAt(tick * 50_000_000L);
        }

        void walkAt(long nanos) {
            scene.motionTime.set(nanos);
            ScaffoldClientFlowTest.ordinaryWalking(f);
            drain();
        }

        public void close() throws Exception {
            f.aura.onDisable();
            f.close();
        }
    }

    @Test public void actualAuraWalkingHookEmitsOnlyExistingC03AndAcceptedGcdGrid() throws Exception {
        try (Harness h = new Harness()) {
            float sensitivity = h.f.mc.gameSettings.mouseSensitivity;
            float quantum = RotationSensitivity.mouseStep(sensitivity);
            float yaw = 0, pitch = 0;
            boolean changed = false;
            for (int tick = 0; tick < 30; tick++) {
                h.walk(tick);
                assertTrue("real walking frame must be transport-accepted", h.rotation.isMotionAccepted());
                assertEquals("aura", h.rotation.getConfirmedOwner());
                float nextYaw = h.rotation.getEffectiveYaw(), nextPitch = h.rotation.getEffectivePitch();
                assertEquals(Math.rint((nextYaw - yaw) / quantum), (nextYaw - yaw) / quantum, 1e-3);
                assertEquals(Math.rint((nextPitch - pitch) / quantum), (nextPitch - pitch) / quantum, 1e-3);
                assertTrue(nextPitch >= -90 && nextPitch <= 90);
                changed |= Math.abs(nextYaw) > 1;
                yaw = nextYaw;
                pitch = nextPitch;
                assertEquals("silent Aura must not move camera yaw", 0, h.f.player.rotationYaw, 0);
            }
            assertTrue(h.sawC03);
            assertTrue("Adaptive candidate must reach live Aura consumer", changed);
        }
    }

    @Test public void relativeNearestAtLowSpeedReachesARealRayOnEverySensitivityGrid() throws Exception {
        for(float sensitivity:new float[]{0,.5f,1}) {
            try(Harness h=new Harness()) {
                h.f.mc.gameSettings.mouseSensitivity=sensitivity;
                h.f.aura.getModeSetting("Adaptive Aim Point").setValue("Nearest");
                h.f.aura.getModeSetting("Adaptive Curve").setValue("Relative");
                h.f.aura.getModeSetting("Adaptive Noise Profile").setValue("Off");
                h.f.aura.getNumberSetting("adaptive_min_aim_speed").setValue(20D);
                h.f.aura.getNumberSetting("adaptive_max_aim_speed").setValue(20D);
                h.f.aura.getBooleanSetting("Adaptive Keep Valid Aim").setValue(false);
                float yaw=0,pitch=0;boolean hit=false;
                net.minecraft.util.Vec3 feet=new net.minecraft.util.Vec3(h.f.player.posX,h.f.player.posY,h.f.player.posZ);
                for(int tick=0;tick<220;tick++) {
                    h.walk(tick);
                    float nextYaw=h.rotation.getEffectiveYaw(),nextPitch=h.rotation.getEffectivePitch();
                    double dy=net.minecraft.util.MathHelper.wrapAngleTo180_float(nextYaw-yaw),dp=nextPitch-pitch;
                    assertTrue("global cap includes final mouse-count rounding",Math.hypot(dy,dp)<=1.0001);
                    hit|=h.f.runtime.getRaycastService().canHitFrom(feet,h.first,nextYaw,nextPitch,3.2f,false);
                    yaw=nextYaw;pitch=nextPitch;
                }
                assertTrue("low-speed Relative must not stall at a hitbox edge; sensitivity="+sensitivity,hit);
                assertTrue(h.f.runtime.getRaycastService().canHitFrom(feet,h.first,yaw,pitch,3.2f,false));
            }
        }
    }

    @Test public void subCountAndMixedMotionIntervalsEarnBoundedMouseCountCredit() throws Exception {
        for(int[] intervals:new int[][]{{25},{25,50,25,75},{10,25,50}}) {
            try(Harness h=new Harness()) {
                h.f.mc.gameSettings.mouseSensitivity=1;
                h.f.aura.getModeSetting("Adaptive Aim Point").setValue("Nearest");
                h.f.aura.getModeSetting("Adaptive Curve").setValue("Relative");
                h.f.aura.getModeSetting("Adaptive Noise Profile").setValue("Off");
                h.f.aura.getNumberSetting("adaptive_min_aim_speed").setValue(20D);
                h.f.aura.getNumberSetting("adaptive_max_aim_speed").setValue(20D);
                h.f.aura.getBooleanSetting("Adaptive Keep Valid Aim").setValue(false);
                double total=0;long now=0;float yaw=0,pitch=0;
                h.walkAt(0);
                for(int tick=0;tick<240;tick++) {
                    int millis=intervals[tick%intervals.length];now+=millis*1_000_000L;h.walkAt(now);
                    float nextYaw=h.rotation.getEffectiveYaw(),nextPitch=h.rotation.getEffectivePitch();
                    double delta=Math.hypot(net.minecraft.util.MathHelper.wrapAngleTo180_float(nextYaw-yaw),nextPitch-pitch);
                    total+=delta;
                    assertTrue("sub-count credit never borrows future time",total<=now*1e-9*20+.001);
                    if(millis>=50)assertTrue("representable motion retains the strict cap",delta<=millis*.02+.0001);
                    yaw=nextYaw;pitch=nextPitch;
                }
                net.minecraft.util.Vec3 feet=new net.minecraft.util.Vec3(h.f.player.posX,h.f.player.posY,h.f.player.posZ);
                assertTrue("short intervals must not stall",h.f.runtime.getRaycastService().canHitFrom(feet,h.first,yaw,pitch,3.2f,false));
                h.f.aura.getBooleanSetting("Adaptive Keep Valid Aim").setValue(true);
                for(int tick=0;tick<8;tick++) {
                    now+=25_000_000L;h.walkAt(now);
                    assertEquals(yaw,h.rotation.getEffectiveYaw(),0);assertEquals(pitch,h.rotation.getEffectivePitch(),0);
                }
            }
        }
    }

    @Test public void targetSwitchLosesOldAcquisitionAndDoesNotReplayItsTurn() throws Exception {
        try (Harness h = new Harness()) {
            for (int i = 0; i < 18; i++) h.walk(i);
            float last = h.rotation.getEffectiveYaw();
            RotationBehaviorHookTest.Player second = h.target(2.5, 2.5);
            h.setTarget(second);
            h.walk(18);
            assertSame(second, h.f.aura.getActiveTarget());
            assertEquals("new acquisition starts with a zero-motion hold", 0,
                    net.minecraft.util.MathHelper.wrapAngleTo180_float(h.rotation.getEffectiveYaw() - last), .12f);
            boolean moved = false;
            for (int i = 19; i < 55; i++) {
                h.walk(i);
                moved |= Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(
                        h.rotation.getEffectiveYaw() - last)) > 1;
            }
            assertTrue("fresh target must cause a new bounded turn", moved);
            h.clearTargets();
            h.walk(56);
            assertNull(h.f.aura.getActiveTarget());
            // Existing accepted aura-release can legitimately own the return frame.
            assertNotEquals("aura", h.rotation.getConfirmedOwner());
        }
    }

    @Test public void validTargetRayHoldsWithoutNoiseDriftAndMovingTargetResumesTheSameScopedEngine() throws Exception {
        try(Harness h=new Harness()) {
            h.f.aura.getModeSetting("Adaptive Hitpoint").setValue("Dynamic");
            h.f.aura.getNumberSetting("Adaptive Variation Strength").setValue(1D);
            RaycastService rays=h.f.runtime.getRaycastService();
            int tick=0;boolean hit=false;
            net.minecraft.util.Vec3 feet=new net.minecraft.util.Vec3(h.f.player.posX,h.f.player.posY,h.f.player.posZ);
            for(;tick<80;tick++) {
                h.walk(tick);
                if(rays.canHitFrom(feet,h.first,h.rotation.getEffectiveYaw(),h.rotation.getEffectivePitch(),3.2f,false)) {
                    hit=true;tick++;break;
                }
            }
            assertTrue("Adaptive must acquire an actual entity ray",hit);
            float yaw=h.rotation.getEffectiveYaw(),pitch=h.rotation.getEffectivePitch();
            for(int held=0;held<20;held++,tick++) {
                h.walk(tick);
                assertEquals("useful yaw survives strong hitpoint variation",yaw,h.rotation.getEffectiveYaw(),0);
                assertEquals("useful pitch survives strong hitpoint variation",pitch,h.rotation.getEffectivePitch(),0);
                assertEquals("aura",h.rotation.getConfirmedOwner());
            }
            h.first.posX=h.first.lastTickPosX=2.5;
            h.first.posZ=h.first.lastTickPosZ=2;
            h.first.setEntityBoundingBox(new AxisAlignedBB(2.2,64,1.7,2.8,65.8,2.3));
            assertFalse(rays.canHitFrom(feet,h.first,yaw,pitch,3.2f,false));
            boolean corrected=false;
            for(int miss=0;miss<24;miss++,tick++) {
                h.walk(tick);
                corrected|=Math.abs(net.minecraft.util.MathHelper.wrapAngleTo180_float(h.rotation.getEffectiveYaw()-yaw))>1;
            }
            assertTrue("losing the current target ray resumes correction",corrected);
            assertSame(h.first,h.f.aura.getActiveTarget());
        }
    }

    private static final class CancelC03 implements Subscriber {
        @Subscribe final Listener<EventPacket> packets = new Listener<>(event -> {
            if(event.isOutbound() && event.isPre() && event.getPacket() instanceof C03PacketPlayer)
                event.cancel();
        });
    }

    @Test public void cancelledRealC03RevokesOwnershipAndRestartsWithoutCatchup() throws Exception {
        try(Harness h = new Harness()) {
            for(int i = 0; i < 8; i++) h.walk(i);
            assertEquals("aura", h.rotation.getConfirmedOwner());
            float acceptedYaw = h.rotation.getEffectiveYaw();
            CancelC03 cancel = new CancelC03();
            Pulsar.BUS.subscribe(cancel);
            try {
                h.walk(8);
            } finally {
                Pulsar.BUS.unsubscribe(cancel);
            }
            assertFalse("cancelled PRE transport must not be reported accepted", h.rotation.isMotionAccepted());
            assertNull("cancelled movement cannot own an attack", h.rotation.getConfirmedOwner());
            assertEquals("last accepted yaw remains the only rotation basis", acceptedYaw,
                    h.rotation.getEffectiveYaw(), 0);
            h.walk(9);
            assertTrue(h.rotation.isMotionAccepted());
            assertEquals("fresh acquisition's first step holds its accepted basis",
                    acceptedYaw, h.rotation.getEffectiveYaw(), .12f);
        }
    }

    @Test public void sameSequencePreReentryAndDifferentRenderCadenceDoNotConsumeExtraMotion() throws Exception {
        float[] baseline = controlledSchedule(0);
        for (int fps : new int[]{20, 60, 144, 240}) {
            assertArrayEquals("Render fps " + fps, baseline, controlledSchedule(fps), 0f);
        }
    }

    private static float[] controlledSchedule(int fps) throws Exception {
        try (Harness h = new Harness()) {
            List<Float> angles = new ArrayList<>();
            RuntimeObservation observed = h.f.runtime.getObservation();
            for (int motion = 0; motion < 32; motion++) {
                h.scene.motionTime.set(motion * 50_000_000L);
                observed.beginOuterTick();
                try {
                    h.rotation.bindConnection(h.f.network.getClientPackets());
                    h.rotation.beginMotion(h.f.player, h.scene, h.f.player.rotationYaw, h.f.player.rotationPitch);
                    float[] first = h.rotation.calculateRotations("aura", h.f.player.rotationYaw,
                            h.f.player.rotationPitch, h.first, false, profile(), false);
                    assertNotNull(first);
                    for (int render = 0; render < fps / 20; render++) {
                        h.scene.motionTime.set(motion * 50_000_000L + render * 1_000L);
                        float[] again = h.rotation.calculateRotations("aura", h.f.player.rotationYaw,
                                h.f.player.rotationPitch, h.first, false, profile(), false);
                        assertArrayEquals(first, again, 0f);
                    }
                    h.rotation.request("aura", RotationManager.AURA_PRIORITY, first[0], first[1]);
                    h.rotation.finishMotion(first[0], first[1], true);
                    // A POST/HUD read is never another motion step and must not clear history.
                    for (int render = 0; render < fps / 20; render++) {
                        float[] idle = h.rotation.calculateRotations("aura", h.f.player.rotationYaw,
                                h.f.player.rotationPitch, h.first, false, profile(), false);
                        assertArrayEquals(first, idle, 0f);
                    }
                    angles.add(first[0]);
                    angles.add(first[1]);
                } finally {
                    observed.endOuterTick();
                }
            }
            float[] result = new float[angles.size()];
            for (int i = 0; i < result.length; i++) result[i] = angles.get(i);
            return result;
        }
    }

    private static RotationProfile profile() {
        RotationProfile p = new RotationProfile();
        p.adaptive = true;
        p.yawSpeed = 28;
        p.pitchSpeed = 28;
        return p;
    }

    @Test public void targetFlappingInsideOneMotionSequenceCannotReacquireRepeatedly() throws Exception {
        try (Harness h = new Harness()) {
            RuntimeObservation observed = h.f.runtime.getObservation();
            observed.beginOuterTick();
            try {
                h.rotation.bindConnection(h.f.network.getClientPackets());
                h.rotation.beginMotion(h.f.player, h.scene, 0, 0);
                RotationBehaviorHookTest.Player other = h.target(2.5, 2.5);
                float[] first = h.rotation.calculateRotations("aura", 0, 0,
                        h.first, false, profile(), false);
                assertNotNull(first);
                assertArrayEquals(first, h.rotation.calculateRotations("aura", 0, 0,
                        other, false, profile(), false), 0f);
                assertArrayEquals(first, h.rotation.calculateRotations("aura", 0, 0,
                        h.first, false, profile(), false), 0f);
                java.lang.reflect.Field states = RotationManager.class.getDeclaredField("states");
                states.setAccessible(true);
                Object state = ((java.util.Map<?, ?>) states.get(h.rotation)).get("aura");
                java.lang.reflect.Field ordinal = state.getClass().getDeclaredField("adaptiveOrdinal");
                ordinal.setAccessible(true);
                assertEquals("no additional acquisition within the same physical motion", 1L,
                        ordinal.getLong(state));
            } finally {
                observed.endOuterTick();
            }
        }
    }

    @Test public void sensitivityChangeCannotReuseThePreviousGridOrSpendTheSameMotionTwice() throws Exception {
        try(Harness h=new Harness()) {
            RuntimeObservation observed=h.f.runtime.getObservation();observed.beginOuterTick();
            try {
                h.rotation.bindConnection(h.f.network.getClientPackets());
                h.rotation.beginMotion(h.f.player,h.scene,0,0);
                RotationProfile p=profile();p.adaptiveExplicitControls=true;
                float[] first=h.rotation.calculateRotations("aura",0,0,h.first,false,p,false);
                assertNotNull(first);
                h.f.mc.gameSettings.mouseSensitivity=1;
                assertArrayEquals(new float[]{0,0},h.rotation.calculateRotations("aura",0,0,h.first,false,p,false),0);
                h.f.mc.gameSettings.mouseSensitivity=.5f;
                assertArrayEquals(new float[]{0,0},h.rotation.calculateRotations("aura",0,0,h.first,false,p,false),0);
                java.lang.reflect.Field states=RotationManager.class.getDeclaredField("states");states.setAccessible(true);
                Object state=((java.util.Map<?,?>)states.get(h.rotation)).get("aura");
                java.lang.reflect.Field ordinal=state.getClass().getDeclaredField("adaptiveOrdinal");ordinal.setAccessible(true);
                java.lang.reflect.Field credit=state.getClass().getDeclaredField("adaptiveCountCredit");credit.setAccessible(true);
                assertEquals(1,ordinal.getLong(state));assertEquals(0,credit.getDouble(state),0);
            } finally { observed.endOuterTick(); }
        }
    }

    @Test public void rejectedC03AndForeignOwnerCannotRetainAdaptiveAuthority() throws Exception {
        try (Harness h = new Harness()) {
            RuntimeObservation observed = h.f.runtime.getObservation();
            observed.beginOuterTick();
            try {
                h.rotation.bindConnection(h.f.network.getClientPackets());
                h.rotation.beginMotion(h.f.player, h.scene, 0, 0);
                float[] proposed = h.rotation.calculateRotations("aura", 0, 0, h.first, false, profile(), false);
                assertNotNull(proposed);
                assertFalse(h.rotation.isMotionAccepted());
                h.rotation.request("aura", RotationManager.AURA_PRIORITY, proposed[0], proposed[1]);
                h.rotation.finishMotion(proposed[0], proposed[1], false);
                assertFalse("rejected C03 is not an accepted owner", h.rotation.isMotionAccepted());
                assertFalse(h.rotation.canUseRotation("aura", false));
                assertNull(h.rotation.getConfirmedOwner());
                h.rotation.beginMotion(h.f.player, h.scene, 0, 0);
                float[] fresh = h.rotation.calculateRotations("aura", 0, 0, h.first, false, profile(), false);
                assertArrayEquals("rejection must restart with a hold", new float[]{0, 0}, fresh, 0);
            } finally {
                observed.endOuterTick();
            }
            h.rotation.reset("aura");
            h.f.accept("scaffold", 75);
            assertEquals("scaffold", h.rotation.getConfirmedOwner());
            observed.beginOuterTick();
            try {
                h.rotation.beginMotion(h.f.player, h.scene, 0, 0);
                float[] notOwner = h.rotation.calculateRotations("aura", 0, 0, h.first, false, profile(), false);
                assertNotNull(notOwner);
                assertEquals(75, notOwner[0], 1);
            } finally {
                observed.endOuterTick();
            }
        }
    }

    @Test public void adaptivePresetThroughRealWalkingHookKeepsAcceptedC03AndNoC02() throws Exception {
        double[] firstYaw = new double[3];
        int i = 0;
        for (String speed : new String[]{"Calm", "Balanced", "Swift"}) {
            try (Harness h = new Harness()) {
                h.f.aura.getModeSetting("Adaptive Speed").setValue(speed);
                h.f.aura.getModeSetting("Adaptive Hitpoint").setValue("Organic");
                h.f.aura.getNumberSetting("Adaptive Variation Strength").setValue(.8);
                for (int tick = 0; tick < 7; tick++) {
                    h.walk(tick);
                    assertTrue(h.rotation.isMotionAccepted());
                    assertEquals("aura", h.rotation.getConfirmedOwner());
                }
                firstYaw[i++] = Math.abs(h.rotation.getEffectiveYaw());
                assertTrue(h.sawC03);
            }
        }
        // Different harnesses have independent per-acquisition seeds and rotation history.
        // Preset speed caps are ordered in AdaptivePresetMotionTest instead.
        for (double yaw : firstYaw)
            assertTrue("each real consumer must return a finite rotation", Double.isFinite(yaw));
    }

    @Test public void dynamicHitpointTargetSwitchAndDisableStartFreshMotion() throws Exception {
        try(Harness h=new Harness()) {
            h.f.aura.getModeSetting("Adaptive Speed").setValue("Swift");
            h.f.aura.getModeSetting("Adaptive Hitpoint").setValue("Dynamic");
            h.f.aura.getNumberSetting("Adaptive Variation Strength").setValue(1D);
            for(int i=0;i<14;i++) h.walk(i);
            float before=h.rotation.getEffectiveYaw();
            RotationBehaviorHookTest.Player next=h.target(2.5,2.5);
            h.setTarget(next);
            h.walk(14);
            assertSame(next,h.f.aura.getActiveTarget());
            assertEquals(0,net.minecraft.util.MathHelper.wrapAngleTo180_float(
                    h.rotation.getEffectiveYaw()-before),.12f);
            h.f.aura.onDisable();
            assertNull(h.rotation.getConfirmedOwner());
            h.f.aura.onEnable();
            h.walk(15);
            assertTrue(h.rotation.isMotionAccepted());
        }
    }

    @Test public void guiFocusDisableAndConnectionBoundaryInvalidateWithoutGhostAcquisition() throws Exception {
        try (Harness h = new Harness()) {
            for (int i = 0; i < 5; i++) h.walk(i);
            RuntimeObservation observation = h.f.runtime.getObservation();
            observation.beginOuterTick();
            try {
                h.rotation.beginMotion(h.f.player, h.scene, 0, 0);
                h.f.mc.currentScreen = new GuiScreen() { };
                assertNull("GUI must refuse an Adaptive request",
                        h.rotation.calculateRotations("aura", 0, 0, h.first, false, profile(), false));
                h.f.mc.currentScreen = null;
                h.f.mc.inGameHasFocus = false;
                assertNull("lost focus must refuse a request",
                        h.rotation.calculateRotations("aura", 0, 0, h.first, false, profile(), false));
                h.f.mc.inGameHasFocus = true;
                observation.invalidateScene();
                float[] afterEpoch = h.rotation.calculateRotations("aura", 0, 0,
                        h.first, false, profile(), false);
                assertNotNull(afterEpoch);
                assertEquals("scene transition must rearm with hold", 0,
                        net.minecraft.util.MathHelper.wrapAngleTo180_float(afterEpoch[0] - h.rotation.getEffectiveYaw()), .12);
            } finally {
                observation.endOuterTick();
            }
            h.f.aura.onDisable();
            assertNull(h.rotation.getConfirmedOwner());
            h.f.aura.onEnable();
            h.walk(6);
            assertTrue("fresh Aura still uses the manager's transport", h.rotation.isMotionAccepted());
        }
    }

    private static void independentLegacy(Harness h) {
        h.f.aura.getModeSetting("Adaptive Aim Point").setValue("Legacy");
        h.f.aura.getModeSetting("Adaptive Hitpoint").setValue("Stable");
        h.f.aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Custom");
        h.f.aura.getModeSetting("Adaptive Legacy Hitpoint Algorithm").setValue("Perlin");
        h.f.aura.getNumberSetting("adaptive_legacy_hitpoint_radius").setValue(100D);
        h.f.aura.getNumberSetting("adaptive_legacy_hitpoint_travel_speed").setValue(3D);
        h.f.aura.getNumberSetting("adaptive_legacy_hitpoint_travel_noise").setValue(100D);
        h.f.aura.getNumberSetting("adaptive_initial_hitpoint_radius").setValue(100D);
        h.f.aura.getNumberSetting("adaptive_path_amount").setValue(100D);
        h.f.aura.getNumberSetting("adaptive_path_tempo").setValue(3D);
        h.f.aura.getModeSetting("Adaptive Speed Noise Profile").setValue("Organic");
        h.f.aura.getNumberSetting("Adaptive Speed Noise Amount").setValue(1D);
    }

    @Test public void independentLegacyHitpointUsesTheAcceptedGridAndGlobalSpeedCap() throws Exception {
        try(Harness h=new Harness()) {
            independentLegacy(h);
            h.f.aura.getBooleanSetting("Adaptive Keep Valid Aim").setValue(false);
            h.f.aura.getNumberSetting("adaptive_min_aim_speed").setValue(60D);
            h.f.aura.getNumberSetting("adaptive_max_aim_speed").setValue(140D);
            float quantum=RotationSensitivity.mouseStep(h.f.mc.gameSettings.mouseSensitivity),yaw=0,pitch=0;
            boolean moved=false;
            for(int tick=0;tick<100;tick++) {
                h.walk(tick);
                assertTrue(h.rotation.isMotionAccepted());assertEquals("aura",h.rotation.getConfirmedOwner());
                float nextYaw=h.rotation.getEffectiveYaw(),nextPitch=h.rotation.getEffectivePitch();
                double dy=net.minecraft.util.MathHelper.wrapAngleTo180_float(nextYaw-yaw),dp=nextPitch-pitch;
                assertEquals(Math.rint(dy/quantum),dy/quantum,1e-3);
                assertEquals(Math.rint(dp/quantum),dp/quantum,1e-3);
                assertTrue("new point/path controls must retain the global emitted cap",Math.hypot(dy,dp)<=7.0001);
                moved|=Math.hypot(dy,dp)>quantum*.5;yaw=nextYaw;pitch=nextPitch;
            }
            assertTrue(moved);assertSame(h.first,h.f.aura.getActiveTarget());
            assertEquals(0,h.f.player.rotationYaw,0);assertEquals(0,h.f.player.rotationPitch,0);
        }
    }

    @Test public void keepValidAimStopsEvenMaximumIndependentLegacyNoise() throws Exception {
        try(Harness h=new Harness()) {
            independentLegacy(h);
            RaycastService rays=h.f.runtime.getRaycastService();
            net.minecraft.util.Vec3 feet=new net.minecraft.util.Vec3(h.f.player.posX,h.f.player.posY,h.f.player.posZ);
            boolean hit=false;int tick=0;
            for(;tick<120;tick++) {
                h.walk(tick);
                if(rays.canHitFrom(feet,h.first,h.rotation.getEffectiveYaw(),h.rotation.getEffectivePitch(),3.2f,false)) {
                    hit=true;tick++;break;
                }
            }
            assertTrue("real accepted ray required before testing hold",hit);
            float yaw=h.rotation.getEffectiveYaw(),pitch=h.rotation.getEffectivePitch();
            for(int held=0;held<30;held++,tick++) {
                h.walk(tick);
                assertEquals(yaw,h.rotation.getEffectiveYaw(),0);assertEquals(pitch,h.rotation.getEffectivePitch(),0);
                assertEquals("aura",h.rotation.getConfirmedOwner());
            }
        }
    }

    @Test public void independentPointStateCannotSurviveRejectedTransportOrReconfiguration() throws Exception {
        try(Harness h=new Harness()) {
            independentLegacy(h);
            h.f.aura.getBooleanSetting("Adaptive Keep Valid Aim").setValue(false);
            for(int tick=0;tick<8;tick++)h.walk(tick);
            float yaw=h.rotation.getEffectiveYaw(),pitch=h.rotation.getEffectivePitch();
            CancelC03 cancel=new CancelC03();Pulsar.BUS.subscribe(cancel);
            try { h.walk(8); } finally { Pulsar.BUS.unsubscribe(cancel); }
            assertFalse(h.rotation.isMotionAccepted());assertNull(h.rotation.getConfirmedOwner());
            assertEquals(yaw,h.rotation.getEffectiveYaw(),0);assertEquals(pitch,h.rotation.getEffectivePitch(),0);
            h.walk(9);
            assertTrue(h.rotation.isMotionAccepted());
            assertEquals(yaw,h.rotation.getEffectiveYaw(),.12f);assertEquals(pitch,h.rotation.getEffectivePitch(),.12f);
            h.f.aura.getNumberSetting("adaptive_initial_hitpoint_radius").setValue(0D);
            assertNull("changed point tuning must release old rotation ownership",h.rotation.getConfirmedOwner());
            h.walk(10);
            assertTrue(h.rotation.isMotionAccepted());assertEquals("aura",h.rotation.getConfirmedOwner());
            h.f.aura.onDisable();assertNull(h.rotation.getConfirmedOwner());assertNull(h.f.aura.getActiveTarget());
        }
    }

    }
}
