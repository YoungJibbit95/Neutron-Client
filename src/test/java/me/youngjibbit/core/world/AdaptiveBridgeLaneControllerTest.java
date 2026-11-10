package me.youngjibbit.core.world;

import org.junit.Test;
import static org.junit.Assert.*;

public class AdaptiveBridgeLaneControllerTest {
    @Test public void straightMotionAndReadonlyProjectionKeepOneRawIntentLane() {
        AdaptiveBridgeLaneController controller = new AdaptiveBridgeLaneController();
        assertNull(controller.current());
        AdaptiveBridgeLaneController.Input first = controller.update(.5, .5, 0, .2, 0, 1, 0, true);
        AdaptiveBridgeLaneController.Lane lane = first.lane;
        assertNotNull(lane);
        assertEquals(1, first.forward, 0); assertEquals(0, first.strafe, 0);
        assertEquals(.5, lane.projectX(.51, 9), 1e-12);
        assertEquals(9, lane.projectZ(.51, 9), 1e-12);
        assertSame(lane, controller.current());
        // A slowdown changes strength, not the player's route intention.
        assertSame(lane, controller.update(.51, .7, 0, .1, 0, .3f, 0, true).lane);
        assertSame(lane, controller.current());
    }

    @Test public void correctionDampsSidewaysMomentumAndPreservesSlowdownMagnitude() {
        for (float yaw : new float[]{-180, -135, -90, -17, 0, 45, 179, 540})
            for (float factor : new float[]{1, .98f, .3f, .2f, .06f})
                for (float[] keys : new float[][]{{1,0}, {1,1}, {-1,1}, {0,-1}}) {
                    AdaptiveBridgeLaneController controller = new AdaptiveBridgeLaneController();
                    float f = keys[0] * factor, s = keys[1] * factor;
                    AdaptiveBridgeLaneController.Lane lane = controller.update(0, 0, 0, 0, yaw, f, s, true).lane;
                    double nx = -lane.directionZ, nz = lane.directionX;
                    AdaptiveBridgeLaneController.Input result = controller.update(nx * .2, nz * .2,
                            nx * .04, nz * .04, yaw, f, s, true);
                    assertSame(lane, result.lane);
                    assertEquals(Math.hypot(f, s), Math.hypot(result.forward, result.strafe), 2e-7);
                    double radians = Math.toRadians(yaw);
                    double x = result.strafe * Math.cos(radians) - result.forward * Math.sin(radians);
                    double z = result.forward * Math.cos(radians) + result.strafe * Math.sin(radians);
                    assertTrue("correction must point back into the lane", x * nx + z * nz < 0);
                    double steeringAngle = Math.atan2(Math.abs(x * nx + z * nz), x * lane.directionX + z * lane.directionZ);
                    assertTrue("one sample cannot sharply turn the player", steeringAngle <= Math.atan(.18) + 1e-6);
                }
    }

    @Test public void deadbandAvoidsAlternatingCorrectionsFromSmallPositionNoise() {
        AdaptiveBridgeLaneController controller = new AdaptiveBridgeLaneController();
        controller.update(.5, 0, 0, 0, 0, 1, 0, true);
        for (int i = 0; i < 30; i++) {
            AdaptiveBridgeLaneController.Input input = controller.update(.5 + (i % 2 == 0 ? .01 : -.01),
                    i * .1, 0, .1, 0, 1, 0, true);
            assertEquals(0, input.strafe, 0);
            assertEquals(1, input.forward, 0);
        }
        // Damping still acts inside the positional deadband when moving sideways.
        assertTrue(controller.update(.5, 3, .05, .1, 0, 1, 0, true).strafe < 0);
    }

    @Test public void vanillaGroundStepsConvergeAfterLateralDisturbanceWithoutOscillation() {
        AdaptiveBridgeLaneController controller = new AdaptiveBridgeLaneController();
        controller.update(0, 0, 0, .2, 0, 1, 0, true);
        double x = .20, z = 0, vx = .04, vz = .2;
        double maximumError = x;
        for (int tick = 0; tick < 160; tick++) {
            AdaptiveBridgeLaneController.Input input = controller.update(x, z, vx, vz, 0, 1, 0, true);
            double cap = Math.max(1, Math.hypot(input.strafe, input.forward));
            // Ordinary ground acceleration then drag, independent of controller internals.
            vx = (vx + .1 * input.strafe / cap) * .546;
            vz = (vz + .1 * input.forward / cap) * .546;
            x += vx; z += vz;
            maximumError = Math.max(maximumError, Math.abs(x));
            assertTrue("damping must not drive a side-to-side oscillation", x >= -.01);
        }
        assertTrue("initial disturbance stays bounded", maximumError < .24);
        assertTrue("position returns to the narrow route deadband", Math.abs(x) < .035);
        assertTrue("sideways velocity settles", Math.abs(vx) < 1e-4);
    }

    @Test public void stopOffDirectionChangeTeleportAndInvalidSamplesCannotRetainOldRoute() {
        AdaptiveBridgeLaneController controller = new AdaptiveBridgeLaneController();
        AdaptiveBridgeLaneController.Lane first = controller.update(0, 0, 0, 0, 0, 1, 0, true).lane;
        AdaptiveBridgeLaneController.Input turn = controller.update(.1, .1, 0, 0, 90, 1, 0, true);
        assertNotSame(first, turn.lane);
        assertEquals(.1, turn.lane.anchorX, 0);
        assertEquals(.1, turn.lane.anchorZ, 0);
        AdaptiveBridgeLaneController.Input moved = controller.update(9, 9, 0, 0, 90, 1, 0, true);
        assertNotSame(turn.lane, moved.lane); assertEquals(9, moved.lane.anchorX, 0);
        AdaptiveBridgeLaneController.Input off = controller.update(9.2, 9, .3, .2, 90, .3f, .3f, false);
        assertNull(off.lane); assertNull(controller.current());
        assertEquals(.3f, off.forward, 0); assertEquals(.3f, off.strafe, 0);
        controller.update(0, 0, 0, 0, 0, 1, 0, true);
        AdaptiveBridgeLaneController.Input stopped = controller.update(.2, 0, .3, 0, 0, 0, 0, true);
        assertEquals(0, stopped.forward, 0); assertEquals(0, stopped.strafe, 0); assertNull(controller.current());
        controller.update(0, 0, 0, 0, 0, 1, 0, true);
        assertNull(controller.update(Double.NaN, 0, 0, 0, 0, 1, 0, true).lane);
        controller.update(0, 0, 0, 0, 0, 1, 0, true); controller.reset();
        assertNull(controller.current());
    }
}
