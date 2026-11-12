package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.core.combat.RotationCorrections;
import me.youngjibbit.core.combat.RotationManager;
import me.youngjibbit.core.network.NetworkTestSupport;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.module.impl.player.BedBreaker;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Timer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovementInput;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

/** Native prepared movement, walking transport, corrections and input; synthetic block store only. */
public class BedBreakerSilentRotationTest {
    @Test public void acceptedSilentLookAndPickLeavePhysicalCameraUnchanged() throws Exception { run("acceptedSilentLookAndPickLeavePhysicalCameraUnchanged"); }
    @Test public void pendingAndCancelledRotationsCannotAuthorizeMining() throws Exception { run("pendingAndCancelledRotationsCannotAuthorizeMining"); }
    @Test public void firstPreparedStepUsesDemiseMoveFixAndSmoothModelPose() throws Exception { run("firstPreparedStepUsesDemiseMoveFixAndSmoothModelPose"); }
    @Test public void lateActionCancellationLeavesOnlyValidReturnRotation() throws Exception { run("lateActionCancellationLeavesOnlyValidReturnRotation"); }
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
        f.update(); // Even-tick source search, without accepted bed rotation or mining.
        f.frames(1); // Sample the camera, then establish the first Angle target.
        f.next();
    }
    private static List<Object> walk(BedBreakerTestSupport.Fixture f, Runnable afterPreparation) {
        f.frames(5);
        RuntimeObservation observation = f.f.runtime.getObservation();
        observation.beginOuterTick();
        try {
            observation.observePlayerStep(f.f.player, () -> {
                f.f.runtime.getRotationManager().prepareMovement(f.f.player);
                afterPreparation.run(); f.f.player.onUpdateWalkingPlayer(); return true;
            }, step -> step.getAsBoolean());
        } finally { observation.endOuterTick(); }
        f.pick();
        return drain(f);
    }
    private static C03PacketPlayer onlyMovement(List<Object> packets) {
        C03PacketPlayer movement = null;
        for (Object packet : packets) {
            assertFalse("mining belongs to the ordinary Update stage", packet instanceof C07PacketPlayerDigging);
            if (packet instanceof C03PacketPlayer) { assertNull(movement); movement = (C03PacketPlayer) packet; }
        }
        assertNotNull("real C03 reached embedded transport", movement); return movement;
    }
    private static double distance(float yaw, float pitch, float[] target) {
        return Math.hypot(MathHelper.wrapAngleTo180_float(yaw - target[0]), pitch - target[1]);
    }
    private static void align(BedBreakerTestSupport.Fixture f) {
        float[] target = DemiseBedBreakerGeometry.rotations(f.f.player.getPositionEyes(1), f.pos);
        for (int i = 0; i < 24; i++) {
            C03PacketPlayer packet = onlyMovement(walk(f, () -> { })); f.next();
            if (distance(packet.getYaw(), packet.getPitch(), target) < .2) return;
        }
        fail("continuous rotation must converge through real accepted C03s");
    }
    private static void camera(BedBreakerTestSupport.Fixture f, float yaw, float pitch) {
        assertEquals(yaw, f.f.player.rotationYaw, 0); assertEquals(pitch, f.f.player.rotationPitch, 0);
    }
    public static class Checks {
        @Test public void acceptedSilentLookAndPickLeavePhysicalCameraUnchanged() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); C03PacketPlayer packet = onlyMovement(walk(f, () -> { }));
                RotationManager r = f.f.runtime.getRotationManager();
                assertTrue(packet.getRotating()); assertEquals(BedBreaker.ROTATION_SOURCE, r.getConfirmedOwner());
                assertEquals(r.getEffectiveYaw(), packet.getYaw(), .001f); assertEquals(r.getEffectivePitch(), packet.getPitch(), .001f);
                assertTrue("bounded first step", distance(packet.getYaw(), packet.getPitch(), new float[]{120,-17}) <= 45.0001);
                assertTrue("activation cannot snap to the bed", distance(packet.getYaw(), packet.getPitch(),
                        DemiseBedBreakerGeometry.rotations(f.f.player.getPositionEyes(1),f.pos)) > 1);
                camera(f, 120, -17); f.next(); align(f);
                assertNotNull(f.f.mc.objectMouseOver); assertEquals(f.pos, f.f.mc.objectMouseOver.getBlockPos());
                f.input(false); List<C07PacketPlayerDigging> digs = f.digs();
                assertEquals(1, digs.size()); assertEquals(f.pos, digs.get(0).getPosition());
                assertEquals(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, digs.get(0).getStatus());
                camera(f, 120, -17);
            }
        }
        @Test public void pendingAndCancelledRotationsCannotAuthorizeMining() throws Exception {
            for (boolean cancelled : new boolean[]{false, true}) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); RotationManager r = f.f.runtime.getRotationManager();
                if (cancelled) {
                    f.observer.action = e -> { if (movementPre(e)) e.cancel(); }; assertTrue(walk(f, () -> { }).isEmpty());
                } else {
                    f.frames(5); f.observed(() -> r.prepareMovement(f.f.player));
                }
                f.input(false); assertTrue(f.digs().isEmpty()); assertEquals(0, f.controller.automaticCalls);
                assertFalse(r.canUseRotation(BedBreaker.ROTATION_SOURCE, false)); camera(f, 120, -17);
            }
        }
        @Test public void firstPreparedStepUsesDemiseMoveFixAndSmoothModelPose() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); f.f.player.movementInput = new MovementInput();
                onlyMovement(walk(f, () -> {
                    RotationManager r = f.f.runtime.getRotationManager(); RotationManager.MovementRotation p = r.getPreparedMovement();
                    assertNotNull(p); assertEquals(BedBreaker.ROTATION_SOURCE, p.source); assertEquals(MoveFixController.Mode.ROTATION_BOUND, p.mode);
                    assertFalse("prediction grants no mining ownership", r.canUseRotation(BedBreaker.ROTATION_SOURCE, false));
                    MovementInput.moveForward=1; MovementInput.moveStrafe=0;
                    DemiseMoveFix.Input expected=DemiseMoveFix.correct(0,1,p.yaw,120);
                    DemiseRotationHooks.input(f.f.player.movementInput);
                    assertEquals(expected.forward,MovementInput.moveForward,0); assertEquals(expected.strafe,MovementInput.moveStrafe,0);
                    f.f.player.motionX=f.f.player.motionZ=0;
                    f.f.player.moveFlying(MovementInput.moveStrafe,MovementInput.moveForward,.1f);
                    float scale=.1f/Math.max(1,MathHelper.sqrt_float(expected.strafe*expected.strafe+expected.forward*expected.forward));
                    float sine=MathHelper.sin(p.yaw*(float)Math.PI/180),cosine=MathHelper.cos(p.yaw*(float)Math.PI/180);
                    assertEquals(expected.strafe*scale*cosine-expected.forward*scale*sine,f.f.player.motionX,2e-6);
                    assertEquals(expected.forward*scale*cosine+expected.strafe*scale*sine,f.f.player.motionZ,2e-6);
                }));
                assertEquals(f.module.rotationEngine.currentAim().yaw,DemiseRotationHooks.modelYaw(f.f.player,120),0);
                assertNull("Demise supplies its own model hooks",RotationCorrections.renderPose(f.f.player,120));
                camera(f,120,-17); float[] before=f.module.rotationEngine.rotation(); f.module.setToggled(false);
                assertArrayEquals("disable retains the start of the return",before,f.module.rotationEngine.rotation(),0);
                f.frames(1); assertTrue(distance(before[0],before[1],f.module.rotationEngine.rotation())<=12.0001);
            }
        }
        @Test public void lateActionCancellationLeavesOnlyValidReturnRotation() throws Exception {
            for (int scenario = 0; scenario < 3; scenario++) try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); AtomicBoolean seen = new AtomicBoolean(); final int mode = scenario;
                f.observer.action = e -> { if (movementPre(e)) {
                    seen.set(true);
                    if (mode == 0) f.module.setToggled(false);
                    else if (mode == 1) f.f.mc.inGameHasFocus = false;
                    else f.f.player.usingItem = true;
                } };
                List<Object> packets=walk(f, () -> { }); assertTrue(seen.get());
                if(mode==1) { assertTrue(packets.isEmpty()); assertNull(f.f.runtime.getRotationManager().getConfirmedOwner()); }
                else onlyMovement(packets); // Valid rotation-only continuation survives action cancellation.
                f.input(false); assertTrue(f.digs().isEmpty()); assertEquals(0,f.controller.automaticCalls);
                assertTrue(f.module.rotationEngine.returning()); camera(f,120,-17);
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
                setup(f); align(f); assertEquals(BedBreaker.ROTATION_SOURCE, f.f.runtime.getRotationManager().getConfirmedOwner());
                 f.observer.action = e -> { if (movementPre(e)) e.cancel(); };
                assertTrue(walk(f, () -> { }).isEmpty()); f.input(false);
                assertTrue(f.digs().isEmpty()); assertEquals(0, f.controller.automaticCalls); assertNull(f.f.runtime.getRotationManager().getConfirmedOwner());
            }
        }
        @Test public void cameraChangesDuringMiningDoNotReplaceTheServerRay() throws Exception {
            try (BedBreakerTestSupport.Fixture f = new BedBreakerTestSupport.Fixture()) {
                setup(f); align(f); f.f.player.rotationYaw = -135; f.f.player.rotationPitch = -35;
                MovingObjectPosition cursor = f.f.mc.objectMouseOver; assertNotNull(cursor);
                AtomicBoolean seen = new AtomicBoolean();
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
