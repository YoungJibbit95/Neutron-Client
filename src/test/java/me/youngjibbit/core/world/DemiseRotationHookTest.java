package me.youngjibbit.core.world;

import org.junit.Test;

/** Each real-client fixture owns one fresh JVM; no Unsafe static-final/JIT cross-fixture contamination. */
public class DemiseRotationHookTest {
    @Test public void rayPlanCanAimEarlyWithoutInventingAcceptedRotationHistory() throws Exception {
        Wave2IsolatedCase.run(DemiseRotationHookChecks.class, "rayPlanCanAimEarlyWithoutInventingAcceptedRotationHistory");
    }
    @Test public void acceptedHistorySurvivesFocusButNeverAnotherEpoch() throws Exception {
        Wave2IsolatedCase.run(DemiseRotationHookChecks.class, "acceptedHistorySurvivesFocusButNeverAnotherEpoch");
    }
    @Test public void originalAngleProducerPreservesManualCamera() throws Exception {
        Wave2IsolatedCase.run(DemiseRotationHookChecks.class, "originalAngleProducerPreservesManualCamera");
    }
    @Test public void rawMoveFixPrecedesSneakAndPhysicsConsumesItOnce() throws Exception {
        Wave2IsolatedCase.run(DemiseRotationHookChecks.class, "rawMoveFixPrecedesSneakAndPhysicsConsumesItOnce");
    }
    @Test public void acceptedWireAndModelPoseSurviveFreeCameraAndDisableTail() throws Exception {
        Wave2IsolatedCase.run(DemiseRotationHookChecks.class, "acceptedWireAndModelPoseSurviveFreeCameraAndDisableTail");
    }
    @Test public void anotherOwnerAndInvalidScenesDoNotInheritDemisePose() throws Exception {
        Wave2IsolatedCase.run(DemiseRotationHookChecks.class, "anotherOwnerAndInvalidScenesDoNotInheritDemisePose");
    }
    @Test public void consecutiveGroundCountersRunBeforeEntityUpdate() throws Exception {
        Wave2IsolatedCase.run(DemiseRotationHookChecks.class, "consecutiveGroundCountersRunBeforeEntityUpdate");
    }
    @Test public void noneCorrectionStopsOppositeSprintOnlyDuringDisableTail() throws Exception {
        Wave2IsolatedCase.run(DemiseRotationHookChecks.class, "noneCorrectionStopsOppositeSprintOnlyDuringDisableTail");
    }
}
