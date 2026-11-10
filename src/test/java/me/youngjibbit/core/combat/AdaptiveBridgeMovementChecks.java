package me.youngjibbit.core.combat;

import me.youngjibbit.event.player.JumpFixEvent;
import me.youngjibbit.event.player.PlayerMoveUpdateEvent;
import org.junit.Test;
import static org.junit.Assert.*;

/** Actual moveFlying/jump consumers in an isolated, disposable client fixture. */
public class AdaptiveBridgeMovementChecks {
    @Test public void adaptiveMovementKeepsWorldHeadingAndMagnitudeAcrossAimAndSlowdownMatrix() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            f.scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
            for (MoveFixController.Mode mode : MoveFixController.Mode.values()) {
                f.scaffold.getModeSetting("movefix").setValue(mode.label);
                for (float camera : new float[]{-179, 0, 71, 180})
                    for (float aim : new float[]{-179, -90, -22.6f, -22.4f, 0, 22.4f, 22.6f, 90, 179}) {
                        f.player.rotationYaw = camera; f.accept("scaffold", aim);
                        for (float strength : new float[]{.98f, .294f, .196f, .0588f})
                            for (int s = -1; s <= 1; s++) for (int forward = -1; forward <= 1; forward++) {
                                f.player.motionX = f.player.motionZ = 0;
                                f.player.moveFlying(s * strength, forward * strength, .1f);
                                double yaw = Math.toRadians(camera), cap = Math.max(1, Math.hypot(s * strength, forward * strength));
                                assertEquals(mode + " X", .1 * strength * (s * Math.cos(yaw) - forward * Math.sin(yaw)) / cap,
                                        f.player.motionX, 3e-5);
                                assertEquals(mode + " Z", .1 * strength * (forward * Math.cos(yaw) + s * Math.sin(yaw)) / cap,
                                        f.player.motionZ, 3e-5);
                                assertEquals(camera, f.player.rotationYaw, 0);
                                assertEquals(aim, f.runtime.getRotationManager().getEffectiveYaw(), 0);
                                assertEquals("scaffold", f.runtime.getRotationManager().getConfirmedOwner());
                                assertEquals(forward * strength, RotationCorrections.sprintForward(camera, s * strength, forward * strength), 0);
                            }
                    }
            }
            assertNull(f.channel.readOutbound());
        }
    }

    @Test public void adaptiveJumpAndDirectMotionDoNotGainSidewaysImpulseFromPlacementAim() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            f.scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
            f.player.rotationYaw = 37; f.player.sprint = true;
            for (MoveFixController.Mode mode : MoveFixController.Mode.values()) {
                f.scaffold.getModeSetting("movefix").setValue(mode.label);
                for (float aim : new float[]{-179, -90, 0, 90, 179}) {
                    f.accept("scaffold", aim); f.player.motionX = f.player.motionZ = 0;
                    f.player.jump();
                    assertEquals(-.2 * Math.sin(Math.toRadians(37)), f.player.motionX, 3e-5);
                    assertEquals(.2 * Math.cos(Math.toRadians(37)), f.player.motionZ, 3e-5);
                    assertTrue(f.player.motionY > .4);
                    assertEquals(-8, RotationCorrections.directMotionYaw(37, -8, .3f, .3f), 0);
                    assertEquals(37, f.player.rotationYaw, 0);
                }
            }
            assertNull(f.channel.readOutbound());
        }
    }

    @Test public void adaptivePreservationCannotLeakIntoOtherApproachesOwnersOrCancelledEvents() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            f.player.rotationYaw = 0;
            f.scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
            f.scaffold.getModeSetting("movefix").setValue("Rotationsgebunden");
            f.accept("scaffold", 90); f.player.moveFlying(0, 1, .1f);
            assertEquals(0, f.player.motionX, 3e-5); assertEquals(.1, f.player.motionZ, 3e-5);
            f.scaffold.getModeSetting("Scaffold Approach").setValue("Neutron");
            f.accept("scaffold", 90); f.player.motionX = f.player.motionZ = 0;
            f.player.moveFlying(0, 1, .1f);
            assertEquals(-.1, f.player.motionX, 3e-5); assertEquals(0, f.player.motionZ, 3e-5);
            f.scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
            f.accept("aura", -90); f.player.motionX = f.player.motionZ = 0;
            f.player.moveFlying(0, 1, .1f);
            assertEquals(.1, f.player.motionX, 3e-5); assertEquals(0, f.player.motionZ, 3e-5);
        }
        RotationManager manager = new RotationManager();
        manager.beginMotion(17, 7); manager.request("scaffold", 30, 129, 80); manager.finishMotion(129, 80, true);
        RotationCorrections.Selection selected = RotationCorrections.select(manager, "scaffold", true,
                MoveFixController.Mode.PRECISE, BodyRotationController.Mode.VANILLA, true);
        PlayerMoveUpdateEvent cancelled = new PlayerMoveUpdateEvent(.3f, .2f, .01f, 17, 7);
        cancelled.setCancelled(true); RotationCorrections.applyMovement(selected, cancelled);
        assertTrue(cancelled.claimRotationCorrection()); assertEquals(17, cancelled.getYaw(), 0);
        assertEquals(.3f, cancelled.getStrafe(), 0); assertEquals(.2f, cancelled.getForward(), 0);
        PlayerMoveUpdateEvent preserved = new PlayerMoveUpdateEvent(.3f, .2f, .01f, 17, 7);
        RotationCorrections.applyMovement(selected, preserved);
        assertFalse(preserved.claimRotationCorrection());
        assertEquals(17, preserved.getYaw(), 0); assertEquals(.01f, preserved.getFriction(), 0);
        JumpFixEvent jump = new JumpFixEvent(17);
        RotationCorrections.applyJump(selected, jump);
        assertEquals(17, jump.getYaw(), 0); assertFalse(jump.claimRotationCorrection());
        assertEquals(129, manager.getEffectiveYaw(), 0);
    }
}
