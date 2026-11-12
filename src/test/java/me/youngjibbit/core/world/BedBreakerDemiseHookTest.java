package me.youngjibbit.core.world;

import org.junit.Test;

/** Fresh JVM per real-client fixture, matching the existing original-Demise hook tests. */
public class BedBreakerDemiseHookTest {
    @Test public void focusReturnKeepsC03AndPhysicsContinuousWithoutActionCallbacks() throws Exception {
        Wave2IsolatedCase.run(BedBreakerDemiseHookChecks.class,"focusReturnKeepsC03AndPhysicsContinuousWithoutActionCallbacks");
    }
    @Test public void guiReturnKeepsC03AndPhysicsContinuousWithoutActionCallbacks() throws Exception {
        Wave2IsolatedCase.run(BedBreakerDemiseHookChecks.class,"guiReturnKeepsC03AndPhysicsContinuousWithoutActionCallbacks");
    }
    @Test public void disabledTailUsesOnePoseForWireModelLookAndManualCamera() throws Exception {
        Wave2IsolatedCase.run(BedBreakerDemiseHookChecks.class,"disabledTailUsesOnePoseForWireModelLookAndManualCamera");
    }
    @Test public void allCorrectionModesUseRawKeysBeforeSneakAndOnePhysicsTransform() throws Exception {
        Wave2IsolatedCase.run(BedBreakerDemiseHookChecks.class,"allCorrectionModesUseRawKeysBeforeSneakAndOnePhysicsTransform");
    }
    @Test public void scaffoldAndForeignOwnersDoNotShareTargetsOrPresentation() throws Exception {
        Wave2IsolatedCase.run(BedBreakerDemiseHookChecks.class,"scaffoldAndForeignOwnersDoNotShareTargetsOrPresentation");
    }
    @Test public void rejectedC03KeepsAnimationContinuousAndRecoveryBounded() throws Exception {
        Wave2IsolatedCase.run(BedBreakerDemiseHookChecks.class,"rejectedC03KeepsAnimationContinuousAndRecoveryBounded");
    }
    @Test public void suspensionKeepsReturnButSceneEpochCannotReviveIt() throws Exception {
        Wave2IsolatedCase.run(BedBreakerDemiseHookChecks.class,"suspensionKeepsReturnButSceneEpochCannotReviveIt");
    }
    @Test public void visibleSettingIsBoundedAndCannotTakeAnotherOwnersCamera() throws Exception {
        Wave2IsolatedCase.run(BedBreakerDemiseHookChecks.class,"visibleSettingIsBoundedAndCannotTakeAnotherOwnersCamera");
    }
}
