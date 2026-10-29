package me.youngjibbit.core.contract;

import org.junit.Test;

import static me.youngjibbit.core.contract.ExecutedStep.Kind.*;
import static org.junit.Assert.*;

public class ExecutedStepTest {
    private final Object connection = new Object(), player = new Object(), world = new Object();
    private final SceneContext scene = new SceneContext(connection, player, world, 0, 0,
            SceneContext.Availability.ACTIVE);

    @Test public void ordinaryDescribesAnExecutedBodyWithStableOuterTickAndIndex() {
        ExecutedStep step = new ExecutedStep(scene, 42, 0, ORDINARY, false);
        assertTrue(step.isExecuted());
        assertSame(scene, step.getScene());
        assertEquals(42, step.getOuterTickId());
        assertEquals(42, step.getOuterTickId());
        assertEquals(0, step.getStepIndex());
        assertEquals(ORDINARY, step.getKind());
        assertFalse(step.isInterrupted());
    }

    @Test public void earnedExtraIsExecutedAndRetainsItsDistinctIndex() {
        ExecutedStep extra = new ExecutedStep(scene, 42, 2, EARNED_EXTRA, false);
        assertTrue(extra.isExecuted());
        assertEquals(EARNED_EXTRA, extra.getKind());
        assertEquals(2, extra.getStepIndex());
        assertEquals(42, extra.getOuterTickId());
    }

    @Test public void withheldIsAnObservationOutsideTheExecutedStepTypeAndKinds() {
        WithheldObservation withheld = new WithheldObservation(scene, 42);
        assertFalse(withheld.isExecuted());
        assertSame(scene, withheld.getScene());
        assertEquals(42, withheld.getOuterTickId());
        assertFalse(ExecutedStep.class.isAssignableFrom(WithheldObservation.class));
        assertArrayEquals(new ExecutedStep.Kind[] {ORDINARY, EARNED_EXTRA}, ExecutedStep.Kind.values());
    }

    @Test public void interruptedBodyIsStillDescribedWithoutPretendingItWasWithheld() {
        ExecutedStep step = new ExecutedStep(scene, 42, 0, ORDINARY, true);
        assertTrue(step.isExecuted());
        assertTrue(step.isInterrupted());
    }

    @Test public void impossibleStepIndicesAndNegativeTickIdsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ExecutedStep(scene, -1, 0, ORDINARY, false));
        assertThrows(IllegalArgumentException.class, () -> new ExecutedStep(scene, 42, -1, ORDINARY, false));
        assertThrows(IllegalArgumentException.class, () -> new ExecutedStep(scene, 42, 1, ORDINARY, false));
        assertThrows(IllegalArgumentException.class, () -> new ExecutedStep(scene, 42, 0, EARNED_EXTRA, false));
        assertThrows(IllegalArgumentException.class, () -> new WithheldObservation(scene, -1));
    }

    @Test public void requiredStepDataCannotBeOmitted() {
        assertThrows(NullPointerException.class, () -> new ExecutedStep(null, 42, 0, ORDINARY, false));
        assertThrows(NullPointerException.class, () -> new ExecutedStep(scene, 42, 0, null, false));
        assertThrows(NullPointerException.class, () -> new WithheldObservation(null, 42));
    }

    @Test public void zeroAndMaximumTickIdsAreNotConfusedWithSentinels() {
        assertEquals(0, new ExecutedStep(scene, 0, 0, ORDINARY, false).getOuterTickId());
        assertEquals(Long.MAX_VALUE, new ExecutedStep(scene, Long.MAX_VALUE, 0, ORDINARY, false).getOuterTickId());
        assertEquals(0, new WithheldObservation(scene, 0).getOuterTickId());
    }
}
