package me.youngjibbit.core.combat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.*;

@RunWith(Parameterized.class)
public class RotationBezierConsumerTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases(){return Arrays.asList(new Object[][]{
            {"optionalCurveReachesBothRealConsumersWithBoundedQuantizedSteps"},
            {"duplicateMotionCalculationsConsumeOnlyOneStep"},
            {"rejectedAndHeldC03NeverCommitCandidateAngles"},
            {"rejectedHeldAndForeignOutcomesMatchExplicitRearmControl"},
            {"targetLossAndForeignOwnerCannotLeakVisibleAssistance"},
            {"standaloneAcceptedPoseLossMatchesExplicitRearmOnNextRealAcquisition"},
            {"ordinaryMatchingAcceptedC03KeepsOneEnvelopeAcrossRealMotions"},
            {"laterManualInputSurvivesVisibleBezierResolution"}
    });}
    private final String method;
    public RotationBezierConsumerTest(String method){this.method=method;}
    @Test public void isolatedRealConsumer() throws Exception {RotationBezierIsolatedCase.run(method);}
}
