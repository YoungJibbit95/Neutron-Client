package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.BodyRotationController;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.NetworkTestSupport;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.EventRotation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.impl.player.BedBreaker;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Native prepared movement, walking transport, corrections and input; synthetic block store only. */
public class BedBreakerSilentRotationTest {
    @Test public void acceptedSilentLookLeavesCameraAndCrosshairIndependent() throws Exception { run("acceptedSilentLookLeavesCameraAndCrosshairIndependent"); }
    @Test public void pendingAndCancelledRotationsCannotAuthorizeMining() throws Exception { run("pendingAndCancelledRotationsCannotAuthorizeMining"); }
    @Test public void firstPreparedStepCorrectsMovementAndAcceptedPoseDrivesModelOnly() throws Exception { run("firstPreparedStepCorrectsMovementAndAcceptedPoseDrivesModelOnly"); }
    @Test public void lateDisableFocusOrUseItemRejectsPreparedC03() throws Exception { run("lateDisableFocusOrUseItemRejectsPreparedC03"); }
    @Test public void changedPoseInPacketPreCannotAcquireOwnership() throws Exception { run("changedPoseInPacketPreCannotAcquireOwnership"); }
    @Test public void rejectedNextC03RevokesPreviouslyAcceptedMiningAuthority() throws Exception { run("rejectedNextC03RevokesPreviouslyAcceptedMiningAuthority"); }
    @Test public void cameraChangesDuringMiningDoNotReplaceTheServerRay() throws Exception { run("cameraChangesDuringMiningDoNotReplaceTheServerRay"); }
    @Test public void physicalMovementBetweenPreparationAndC03KeepsItsRotation() throws Exception { run("physicalMovementBetweenPreparationAndC03KeepsItsRotation"); }
    @Test public void delayedSilentC03CannotReplayAfterItsFrameEnds() throws Exception { run("delayedSilentC03CannotReplayAfterItsFrameEnds"); }

    private static void run(String name) throws Exception { Wave2IsolatedCase.run(Checks.class, name); }
    private static boolean movementPre(EventPacket e) { return e.isOutbound() && e.isPre() && e.getPacket() instanceof C03PacketPlayer; }
    private static List<Object> drain(BedBreakerTestSupport.Fixture f) {
        List<Object> result = new ArrayList<>(); Object packet;
        while ((packet = f.f.channel.readOutbound()) != null) result.add(packet);
        return result;
    }
    private static void setup(BedBreakerTestSupport.Fixture f) throws Exception {
        BedBreakerTestSupport.field(EntityPlayerSP.class, "mc").set(f.f.player, f.f.mc);
        f.f.player.setEntityBoundingBox(new AxisAlignedBB(.2, 64, .2, .8, 65.8, .8));
        f.f.mc.timer = new Timer(20, () -> 0);
        f.f.network.configureClientPackets(new NetworkTestSupport.MainThread(), f.f.player.sendQueue::observeAcceptedOutbound);
        f.f.player.rotationYaw = 120; f.f.player.rotationPitch = -17;
    }
    private static List<Object> walk(BedBreakerTestSupport.Fixture f, Runnable afterPreparation) {
        RuntimeObservation observation = f.f.runtime.getObservation();
        observation.beginOuterTick();
        try {
            observation.observePlayerStep(f.f.player, () -> {
                f.f.runtime.getRotationManager().prepareMovement(f.f.player);
                afterPreparation.run(); f.f.player.onUpdateWalkingPlayer(); return true;
            }, step -> step.getAsBoolean());
        } finally { observation.endOuterTick(); }
        return drain(f);
    }
    private static C03PacketPlayer onlyMovement(List<Object> packets) {
        C03PacketPlayer movement = null;
        for (Object packet : packets) {
            assertFalse("mining belongs to normal input stage", packet instanceof C07PacketPlayerDigging);
            if (packet instanceof C03PacketPlayer) { assertNull(movement); movement = (C03PacketPlayer) packet; }
        }
        assertNotNull("real C03 reached embedded transport", movement); return movement;
    }
    private static void camera(BedBreakerTestSupport.Fixture f, float yaw, float pitch) {
        assertEquals(yaw, f.f.player.rotationYaw, 0); assertEquals(pitch, f.f.player.rotationPitch, 0);
    }
    public static class Checks {
        @Test public void acceptedSilentLookLeavesCameraAndCrosshairIndependent() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); C03PacketPlayer packet = onlyMovement(walk(f, () -> { }));
                RotationManager r = f.f.runtime.getRotationManager();
                assertTrue(packet.getRotating()); assertEquals(BedBreaker.ROTATION_SOURCE, r.getConfirmedOwner());
                assertEquals(r.getEffectiveYaw(), packet.getYaw(), .001f); assertEquals(r.getEffectivePitch(), packet.getPitch(), .001f);
                camera(f, 120, -17); assertTrue("server looks down at bed", packet.getPitch() > 0);
                f.f.mc.objectMouseOver = null; f.input(false); List<C07PacketPlayerDigging> digs = f.digs();
                assertEquals(1, digs.size()); assertEquals(f.pos, digs.get(0).getPosition());
                assertEquals(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, digs.get(0).getStatus());
                assertNull(f.f.mc.objectMouseOver); camera(f, 120, -17);
            }
        }
        @Test public void pendingAndCancelledRotationsCannotAuthorizeMining() throws Exception {
            for (boolean cancelled : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); RotationManager r = f.f.runtime.getRotationManager();
                if (cancelled) {
                    f.observer.action = e -> { if (movementPre(e)) e.cancel(); }; assertTrue(walk(f, () -> { }).isEmpty());
                } else {
                    r.bindConnection(f.f.network.getClientPackets()); r.beginMotion(f.f.player, f.world, 120, -17);
                    Pulsar.BUS.post(new EventRotation(new EventMotion(.5, 64, .5, 120, -17, true)));
                }
                f.input(false); assertTrue(f.digs().isEmpty()); assertEquals(0, f.controller.automaticCalls);
                assertFalse(r.canUseRotation(BedBreaker.ROTATION_SOURCE, false)); camera(f, 120, -17);
            }
        }
        @Test public void firstPreparedStepCorrectsMovementAndAcceptedPoseDrivesModelOnly() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); f.f.player.rotationYaw = 90; f.f.player.rotationPitch = 0;
                onlyMovement(walk(f, () -> {
                    RotationManager r = f.f.runtime.getRotationManager(); RotationManager.MovementRotation p = r.getPreparedMovement();
                    assertNotNull(p); assertEquals(BedBreaker.ROTATION_SOURCE, p.source); assertEquals(MoveFixController.Mode.SIMPLE, p.mode);
                    assertFalse("prediction grants no action ownership", r.canUseRotation(BedBreaker.ROTATION_SOURCE, false));
                    assertNull("model requires accepted pose", RotationCorrections.renderPose(f.f.player, 90));
                    f.f.player.motionX = f.f.player.motionZ = 0; f.f.player.moveFlying(0, 1, .1f);
                    assertEquals("W retains westward camera movement", -.1, f.f.player.motionX, 4e-4); assertEquals(0, f.f.player.motionZ, 4e-4);
                }));
                RotationManager r = f.f.runtime.getRotationManager(); BodyRotationController.Pose pose = RotationCorrections.renderPose(f.f.player, 90);
                assertNotNull(pose); assertEquals(r.getEffectiveYaw(), pose.headYaw, .001f);
                assertEquals(r.getEffectivePitch(), pose.headPitch, .001f); assertEquals(r.getEffectiveYaw(), pose.bodyYaw, .001f);
                camera(f, 90, 0); f.module.setToggled(false);
                assertNull("Disable releases model correction", RotationCorrections.renderPose(f.f.player, 90));
            }
        }
        @Test public void lateDisableFocusOrUseItemRejectsPreparedC03() throws Exception {
            for (int scenario = 0; scenario < 3; scenario++) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); AtomicBoolean seen = new AtomicBoolean(); final int mode = scenario;
                f.observer.action = e -> { if (movementPre(e)) {
                    seen.set(true);
                    if (mode == 0) f.module.setToggled(false);
                    else if (mode == 1) f.f.mc.inGameHasFocus = false;
                    else f.f.player.usingItem = true;
                } };
                assertTrue(walk(f, () -> { }).isEmpty()); assertTrue(seen.get());
                assertNull(f.f.runtime.getRotationManager().getConfirmedOwner()); f.input(false);
                assertTrue(f.digs().isEmpty()); assertEquals(0, f.controller.automaticCalls); camera(f, 120, -17);
            }
        }
        @Test public void changedPoseInPacketPreCannotAcquireOwnership() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); AtomicBoolean seen = new AtomicBoolean();
                f.observer.action = e -> {
                    if (movementPre(e)) {
                        C03PacketPlayer p = (C03PacketPlayer) e.getPacket(); seen.set(true);
                        if (p instanceof C03PacketPlayer.C06PacketPlayerPosLook)
                            e.setPacket(new C03PacketPlayer.C06PacketPlayerPosLook(p.getPositionX(), p.getPositionY(), p.getPositionZ(), p.getYaw() + 3, p.getPitch(), p.isOnGround()));
                        else e.setPacket(new C03PacketPlayer.C05PacketPlayerLook(p.getYaw() + 3, p.getPitch(), p.isOnGround()));
                    }
                };
                assertTrue(walk(f, () -> { }).isEmpty()); assertTrue(seen.get());
                assertNull(f.f.runtime.getRotationManager().getConfirmedOwner()); f.input(false);
                assertTrue(f.digs().isEmpty()); assertEquals(0, f.controller.automaticCalls);
            }
        }
        @Test public void rejectedNextC03RevokesPreviouslyAcceptedMiningAuthority() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); onlyMovement(walk(f, () -> { })); assertEquals(BedBreaker.ROTATION_SOURCE, f.f.runtime.getRotationManager().getConfirmedOwner());
                f.f.player.ticksExisted++; f.observer.action = e -> { if (movementPre(e)) e.cancel(); };
                assertTrue(walk(f, () -> { }).isEmpty()); f.input(false);
                assertTrue(f.digs().isEmpty()); assertEquals(0, f.controller.automaticCalls); assertNull(f.f.runtime.getRotationManager().getConfirmedOwner());
            }
        }
        @Test public void cameraChangesDuringMiningDoNotReplaceTheServerRay() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); onlyMovement(walk(f, () -> { })); f.f.player.rotationYaw = -135; f.f.player.rotationPitch = -35;
                MovingObjectPosition cursor = new MovingObjectPosition(f.f.player, f.f.player.getPositionVector());
                f.f.mc.objectMouseOver = cursor; AtomicBoolean seen = new AtomicBoolean();
                f.observer.action = e -> {
                    if (e.isOutbound() && e.isPre() && e.getPacket() instanceof C07PacketPlayerDigging) {
                        seen.set(true); f.f.player.rotationYaw = 45; f.f.player.rotationPitch = -10;
                    }
                };
                f.input(false); assertTrue(seen.get()); assertEquals(1, f.digs().size());
                assertTrue(f.controller.isMiningBlock(f.pos)); assertSame(cursor, f.f.mc.objectMouseOver); camera(f, 45, -10);
            }
        }
        @Test public void physicalMovementBetweenPreparationAndC03KeepsItsRotation() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); C03PacketPlayer sent = onlyMovement(walk(f, () -> {
                    f.f.player.posX += .05; f.f.player.setEntityBoundingBox(new AxisAlignedBB(.25, 64, .2, .85, 65.8, .8));
                }));
                assertTrue(sent.isMoving()); assertEquals(.55, sent.getPositionX(), 1e-9);
                assertEquals(BedBreaker.ROTATION_SOURCE, f.f.runtime.getRotationManager().getConfirmedOwner()); camera(f, 120, -17);
            }
        }
        @Test public void delayedSilentC03CannotReplayAfterItsFrameEnds() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); f.f.network.getClientPackets().delays().request("bed-test", 200, (packet, flow) -> packet instanceof C03PacketPlayer);
                assertTrue(walk(f, () -> { }).isEmpty()); assertNull(f.f.runtime.getRotationManager().getConfirmedOwner());
                assertEquals(1, f.f.network.getClientPackets().delays().size()); f.f.network.getClientPackets().delays().release("bed-test");
                assertTrue(drain(f).isEmpty()); f.input(false); assertTrue(f.digs().isEmpty()); assertEquals(0, f.controller.automaticCalls);
            }
        }
    }
}
