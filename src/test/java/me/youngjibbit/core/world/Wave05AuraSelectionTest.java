package me.youngjibbit.core.world;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class Wave05AuraSelectionTest {
    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"multiCyclesTwoRealTargetsOnlyAfterAcceptedControllerAttacks"},
                {"rejectedMultiAttackRetriesSameTargetBeforeAdvancing"},
                {"singleRetainsTheFirstSortedTargetAcrossAcceptedAttacks"},
                {"invisibleFilterSelectsVisibleTargetUntilExplicitlyAllowed"},
                {"playersFilterPreventsAllPlayerAttacksUntilExplicitlyAllowed"},
                {"teamFilterSelectsEnemyUntilSameTeamIsExplicitlyAllowed"}
        });
    }
    private final String method;
    public Wave05AuraSelectionTest(String method) { this.method = method; }
    @Test public void realAuraSelectionInFreshJvm() throws Exception {
        Wave2IsolatedCase.run(Wave05AuraSelectionChecks.class, method);
    }
}
