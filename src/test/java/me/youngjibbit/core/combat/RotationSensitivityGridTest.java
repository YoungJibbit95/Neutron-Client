package me.youngjibbit.core.combat;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * The vanilla 1.8.9 mouse sensitivity defines a discrete yaw/pitch increment.
 * Compare to the mathematical grid independently from RotationManager.quantize.
 */
public class RotationSensitivityGridTest {
    private static float mouseStep(float sensitivity) {
        float factor = sensitivity * .6f + .2f;
        return factor * factor * factor * 1.2f;
    }

    @Test public void subStepRequestsCannotInventAnUnattainableMouseMovement() {
        for (float sensitivity : new float[]{0, .5f, 1}) {
            float gcd = mouseStep(sensitivity);
            RotationManager manager = new RotationManager();
            float[] tiny = manager.step("aimassist", 10, -20,
                    10 + .49f * gcd, -20 - .49f * gcd, 180, 180, sensitivity);
            assertEquals(10, tiny[0], 1e-5);
            assertEquals(-20, tiny[1], 1e-5);

            float[] aboveHalf = manager.step("aimassist", 10, -20,
                    10 + .75f * gcd, -20 - .75f * gcd, 180, 180, sensitivity);
            assertEquals(10 + gcd, aboveHalf[0], 2e-5);
            assertEquals(-20 - gcd, aboveHalf[1], 2e-5);
        }
    }

    @Test public void everyStepRemainsOnTheSensitivityGridAcrossAngles() {
        for (float sensitivity : new float[]{0, .5f, 1}) {
            float gcd = mouseStep(sensitivity);
            for (float startYaw : new float[]{-175, -30, 0, 40, 175}) {
                for (float delta : new float[]{-7.31f, -.91f, -.17f, -.025f, 0, .025f, .17f, .91f, 7.31f}) {
                    RotationManager manager = new RotationManager();
                    float[] result = manager.step("aura", startYaw, 5,
                            startYaw + delta, 5 - delta, 180, 180, sensitivity);
                    double yawSteps = (result[0] - startYaw) / (double) gcd;
                    double pitchSteps = (result[1] - 5) / (double) gcd;
                    assertEquals("yaw " + sensitivity + "/" + startYaw + "/" + delta,
                            Math.rint(yawSteps), yawSteps, .002);
                    assertEquals("pitch " + sensitivity + "/" + startYaw + "/" + delta,
                            Math.rint(pitchSteps), pitchSteps, .002);
                    assertEquals("yaw nearest grid", delta, result[0] - startYaw,
                            gcd * .5005f + 1e-5f);
                    assertEquals("pitch nearest grid", -delta, result[1] - 5,
                            gcd * .5005f + 1e-5f);
                }
            }
        }
    }

    @Test public void yawWrapIsContinuousAndQuantizedRelativeToPriorRotation() {
        RotationManager manager = new RotationManager();
        float previousYaw = 179f;
        float targetYaw = -179f;
        float[] result = manager.step("aura", previousYaw, 0, targetYaw, 0,
                10, 10, .5f);
        float gcd = mouseStep(.5f);
        float delta = result[0] - previousYaw;
        assertEquals(2, delta, gcd * .51f);
        assertEquals(Math.rint(delta / gcd), delta / gcd, .002);
        assertTrue("must follow the short turn over 180, not spin 358 degrees", delta > 0 && delta < 3);
    }
}
