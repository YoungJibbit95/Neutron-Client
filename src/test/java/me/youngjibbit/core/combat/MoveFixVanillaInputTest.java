package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

public class MoveFixVanillaInputTest {
    @Test public void everyEnabledModeProducesVanillaKeyboardAxesAtEverySlowdown() {
        for (MoveFixController.Mode mode : MoveFixController.Mode.values())
            for (int yaw = -180; yaw <= 180; yaw += 3)
                for (float strength : new float[]{.98f, .294f, .196f, .0588f})
                    for (int s = -1; s <= 1; s++) for (int f = -1; f <= 1; f++) {
                        MoveFixController.Input result = MoveFixController.correct(s * strength, f * strength, 0, yaw, mode);
                        assertEquals(mode + " strafe yaw=" + yaw, Math.rint(result.strafe / strength), result.strafe / strength, 1e-6);
                        assertEquals(mode + " forward yaw=" + yaw, Math.rint(result.forward / strength), result.forward / strength, 1e-6);
                        assertTrue(Math.abs(result.strafe) <= strength + 1e-6);
                        assertTrue(Math.abs(result.forward) <= strength + 1e-6);
                    }
    }

    @Test public void sprintAdmissionFollowsRemappedForwardForEveryMode() {
        for (MoveFixController.Mode mode : MoveFixController.Mode.values()) {
            RotationManager rotation = new RotationManager();
            rotation.beginMotion(0, 0);
            rotation.request("aura", 20, 90, 0);
            rotation.finishMotion(90, 0, true);
            RotationCorrections.Selection selected = RotationCorrections.select(rotation, "aura", true, mode, BodyRotationController.Mode.VANILLA);
            MoveFixController.Input input = MoveFixController.correct(0, 1, 0, 90, mode);
            assertEquals(mode.name(), input.forward, RotationCorrections.correctedSprintForward(selected, 0, 0, 1), 1e-6);
        }
    }

    @Test public void sprintJumpImpulseUsesWireYawForEveryEnabledMode() {
        for (MoveFixController.Mode mode : MoveFixController.Mode.values()) {
            assertEquals(mode.name(), mode == MoveFixController.Mode.OFF ? 0 : 90,
                    MoveFixController.jumpYaw(0, 90, mode), 0);
        }
    }
}
