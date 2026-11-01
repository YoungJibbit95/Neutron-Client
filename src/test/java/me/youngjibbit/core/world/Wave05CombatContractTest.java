package me.youngjibbit.core.world;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

/** Each real consumer case gets a stable client in a fresh, normally compiled JVM. */
@RunWith(Parameterized.class)
public class Wave05CombatContractTest {
    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"targetLossAfterAcceptedSwingCannotStartAutoblock"},
                {"nestedManualAttackAndAuraShareOneControllerBudget"},
                {"packetPreReentryCannotDuplicateManualAttack"},
                {"cancelledAndThrowingCallbacksReleaseManualControllerScope"},
                {"liveTargetStillStartsAutoblockAfterAttack"},
                {"visibleAimAssistLeavesClicksManualAndMovementOnCameraBasis"}
        });
    }

    private final String method;

    public Wave05CombatContractTest(String method) {
        this.method = method;
    }

    @Test public void realConsumerContractInFreshJvm() throws Exception {
        Wave2IsolatedCase.run(Wave05CombatContractChecks.class, method);
    }
}
