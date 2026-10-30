package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

/** Mouse counts are discrete; requested angular targets are continuous. */
public class RotationSensitivityTest {

    @Test public void exactHalfCountsMatchPinnedLegacyRoundToIntRatherThanTiesToEven() {
        float quantum = RotationSensitivity.mouseStep(.5f);
        float[] halfCounts = {-.5f, -1.5f, -2.5f, .5f, 1.5f, 2.5f};
        int[] expectedCounts = {0, -1, -2, 1, 2, 3};
        for (int i = 0; i < halfCounts.length; i++) {
            float wanted = halfCounts[i] * quantum;
            assertEquals("Fixture must be an exact half count after Float division",
                    halfCounts[i], wanted / quantum, 0);
            assertEquals("Legacy roundToInt half count " + halfCounts[i],
                    expectedCounts[i] * quantum, RotationSensitivity.snap(wanted, 0, .5f), 0);
        }
    }

    @Test public void immediatelyEitherSideOfHalfAQuantumUsesTheCorrectIntegerCount() {
        float quantum = RotationSensitivity.mouseStep(.5f);
        float half = quantum / 2;
        assertEquals(0, RotationSensitivity.snap(Math.nextDown(half), 0, .5f), 0);
        assertEquals(quantum, RotationSensitivity.snap(Math.nextUp(half), 0, .5f), 0);
        assertEquals(-quantum, RotationSensitivity.snap(Math.nextDown(-half), 0, .5f), 0);
        assertEquals(0, RotationSensitivity.snap(Math.nextUp(-half), 0, .5f), 0);
    }

    @Test public void quantumMatchesOneCountThroughVanillaEntitySetAngles() throws Exception {
        for (float sensitivity : new float[]{0, .15f, .5f, 1}) {
            RotationBehaviorHookTest.Player player = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
            // EntityRenderer's unsmoothed mouse path passes this factor to setAngles.
            float scaled = sensitivity * .6f + .2f;
            float mouseFactor = scaled * scaled * scaled * 8;
            player.setAngles(mouseFactor, mouseFactor);
            float quantum = RotationSensitivity.mouseStep(sensitivity);
            assertEquals(player.rotationYaw, quantum, Math.ulp(quantum));
            assertEquals(player.rotationPitch, -quantum, Math.ulp(quantum));
        }
        assertEquals(.0096f, RotationSensitivity.mouseStep(0), 1e-8f);
        assertEquals(.15f, RotationSensitivity.mouseStep(.5f), 1e-7f);
        assertEquals(.6144f, RotationSensitivity.mouseStep(1), 1e-7f);
    }

    @Test public void subStepIsNotEmittedAsImpossibleMouseMovement() {
        float quantum = RotationSensitivity.mouseStep(1f);
        assertEquals(.6144f, quantum, 1e-6f);
        // Historical behavior emitted 0.03° at sensitivity 1.0; that is
        // neither zero counts nor an integer number of mouse counts.
        assertEquals(0, RotationSensitivity.snap(.03f, 0, 1f), 0);
        assertEquals(0, RotationSensitivity.snap(-.03f, 0, 1f), 0);
        assertEquals(quantum, RotationSensitivity.snap(.5f, 0, 1f), 1e-7f);
        assertEquals(-quantum, RotationSensitivity.snap(-.5f, 0, 1f), 1e-7f);
    }

    @Test public void quantizationIsRelativeToTheActualPreviousYawNotWorldZero() {
        float previous = 37.123f;
        float quantum = RotationSensitivity.mouseStep(.5f);
        float proposed = previous + 10 * quantum + .03f;
        float snapped = RotationSensitivity.snap(proposed, previous, .5f);
        assertEquals(previous + 10 * quantum, snapped, 1e-5f);
        assertEquals(10, (snapped - previous) / quantum, 1e-4f);
    }

    @Test public void positiveNegativeAndSlowIncrementSeriesStayOnMouseGrid() {
        for(float sensitivity : new float[]{0f,.15f,.5f,1f}) {
            float quantum = RotationSensitivity.mouseStep(sensitivity);
            float previous = -143.219f;
            for(int i=-120;i<=120;i++) {
                float wanted = previous + (i + .37f) * quantum;
                float snapped = RotationSensitivity.snap(wanted, previous, sensitivity);
                double count = (snapped - previous) / (double) quantum;
                // At large absolute yaws, float ulps slightly perturb the ratio.
                assertEquals("sensitivity=" + sensitivity + " count=" + i, i, count, .01);
                assertTrue(Float.isFinite(snapped));
            }
        }
    }

    @Test public void absentOrInvalidSamplesNeverCreateNanOrFabricateAngles() {
        assertEquals(17, RotationSensitivity.snap(17, Float.NaN, .5f), 0);
        assertEquals(17, RotationSensitivity.snap(17, 13, Float.NaN), 0);
        assertTrue(Float.isNaN(RotationSensitivity.snap(Float.NaN, 13, .5f)));
    }

    @Test public void repeatedSmallTargetsBelowHalfStepRemainStable() {
        float previous=13f;
        for(int i=0;i<200;i++) {
            previous=RotationSensitivity.snap(13.08f, previous, 1f);
            assertEquals(13f,previous,0);
        }
    }
}
