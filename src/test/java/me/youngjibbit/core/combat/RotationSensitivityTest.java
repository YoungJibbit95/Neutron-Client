package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

/** Mouse counts are discrete; requested angular targets are continuous. */
public class RotationSensitivityTest {

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
                assertEquals("sensitivity=" + sensitivity + " count=" + i, i, count, .002);
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
