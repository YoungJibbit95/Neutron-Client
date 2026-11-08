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
            scene.motionTime.set(tick * 50_000_000L);
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
}
