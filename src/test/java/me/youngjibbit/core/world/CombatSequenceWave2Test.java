package me.youngjibbit.core.world;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/** One outer Surefire case per original assertion; child results are never added again. */
@RunWith(Parameterized.class)
public class CombatSequenceWave2Test {
    @Parameterized.Parameters(name = "{index}: {1}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {CombatSequenceWave2Checks.class, "ordinaryAuraCorrelatesOpportunityAndDistinctAcceptedSwingAndAttack"},
                {CombatSequenceWave2Checks.class, "targetLossAfterAcceptedSwingRetainsSwingButHasNoAcceptedAttack"},
                {CombatSequenceWave2Checks.class, "ownerLossAfterAcceptedSwingCannotAuthorizeC02FromEqualNumbers"},
                {CombatSequenceWave2Checks.class, "swingBarrierReleasesTwoOwnerMovementHoldAndC02RechecksTheChangedWirePose"},
                {CombatSequenceWave2Checks.class, "earnedExtraAddsNoSwingOrAttackAndHasItsOwnObservedOpportunity"},
                {CombatSequenceWave2Checks.class, "controllerOutsideAuraScopePreservesUnknownOriginAndManualBehavior"},
                {CombatSequenceWave2Checks.class, "diagnosticsOffPreservesRealAuraPacketsWithoutNewHistory"},
                {CombatSequenceBoundaryWave2Checks.class, "oldStepCompletionCannotMigrateToANewGenerationOrEpochRun"},
                {CombatSequenceBoundaryWave2Checks.class, "oldObserverFinallyCannotWriteAStepIntoItsReplacementRun"},
                {CombatSequenceBoundaryWave2Checks.class, "relevantSelectedRewriteGetsExactlyOneReceiptEvenWhenOriginalPacketWasOther"},
                {CombatSequenceBoundaryWave2Checks.class, "packetPreThrowKeepsItsThrowableAndGetsATerminalFailureReceipt"},
                {CombatSequenceBoundaryWave2Checks.class, "finalPredicateThrowRunsOnceKeepsItsThrowableAndGetsATerminalFailureReceipt"},
                {CombatSequenceBoundaryWave2Checks.class, "auraPacketPostThrowKeepsAcceptedSwingAndRecordsInterruptedBody"},
                {CombatSequenceBoundaryWave2Checks.class, "realPacketBurstStaysBoundedAndStoredEvidenceHasNoGameReferences"},
                {CombatSequenceBoundaryWave2Checks.class, "nestedOpportunityRestoresParentAndOldRunTokenCannotCaptureNewPackets"},
                {CombatSequenceBoundaryWave2Checks.class, "diagnosticsOnAndOffHaveEqualSceneSamplingGuardCallsAndActualPackets"},
                {CombatSequenceBoundaryWave2Checks.class, "traceOwnRuntimeFailureCannotChangePacketsOrTheGameplayThrowable"}
        });
    }

    private final Class<?> checks;
    private final String method;

    public CombatSequenceWave2Test(Class<?> checks, String method) {
        this.checks = checks; this.method = method;
    }

    @Test public void unchangedAssertionCaseInFreshNormalJitJvm() throws Exception {
        Wave2IsolatedCase.run(checks, method);
    }
}
