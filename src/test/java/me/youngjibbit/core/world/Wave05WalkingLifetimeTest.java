package me.youngjibbit.core.world;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class Wave05WalkingLifetimeTest {
    @Parameterized.Parameters(name = "{index}: {0}")
    public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"vanillaPacketPreNestedWalkingKeepsOnlyInnerFrame"},
                {"aimAssistPacketPreNestedWalkingKeepsOnlyInnerFrame"},
                {"vanillaPacketPostNestedWalkingPreservesBothTransportFacts"},
                {"aimAssistPacketPostNestedWalkingPreservesBothTransportFacts"},
                {"cancelledOuterMotionPreCannotRejectAcceptedInnerFrame"},
                {"throwingOuterPacketPreCannotRejectAcceptedInnerFrame"},
                {"vanillaReplacementStillReachesTransport"},
                {"aimAssistReplacementStillReachesTransport"},
                {"vanillaHeldSameFrameReplaysWithoutAuthority"},
                {"vanillaHeldOlderFrameReplaysWithoutAuthority"},
                {"auraHeldSameFrameReplaysWithoutAuthority"},
                {"auraHeldOlderFrameReplaysWithoutAuthority"},
                {"aimAssistHeldSameFrameReplaysWithoutAuthority"},
                {"aimAssistHeldOlderFrameReplaysWithoutAuthority"}
        });
    }
    private final String method;
    public Wave05WalkingLifetimeTest(String method) { this.method = method; }
    @Test public void realWalkingLifetimeInFreshJvm() throws Exception {
        Wave2IsolatedCase.run(Wave05WalkingLifetimeChecks.class, method);
    }
}
