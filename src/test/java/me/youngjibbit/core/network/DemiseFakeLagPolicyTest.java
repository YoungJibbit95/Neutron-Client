package me.youngjibbit.core.network;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.Vec3;
import org.junit.Test;

import static me.youngjibbit.core.network.DemiseFakeLagPolicy.Action.*;
import static org.junit.Assert.*;

/** Boundary cases taken from the pinned FakeLag/TimerUtils/PlayerUtils source, independent of Minecraft ticks. */
public class DemiseFakeLagPolicyTest {
    private static DemiseFakeLagPolicy policy() { DemiseFakeLagPolicy p = new DemiseFakeLagPolicy((min, max) -> min); p.reset(0); return p; }
    private static DemiseFakeLagPolicy.Input input(double distance) {
        DemiseFakeLagPolicy.Input i = new DemiseFakeLagPolicy.Input();
        i.eligible = i.visible = true; i.boxDistance = distance; i.clientDistance = 4; i.serverDistance = 5; return i;
    }

    @Test public void pulseUsesStrictRecoilAndDelayBoundariesAndSamplesItsStartOnce() {
        DemiseFakeLagPolicy p = policy(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings(); s.smart = false;
        DemiseFakeLagPolicy.Input i = input(5);
        assertEquals(RELEASE, p.update(50, s, i).action);
        DemiseFakeLagPolicy.Result start = p.update(51, s, i);
        assertEquals(PULSE, start.action); assertTrue(start.pickPosition);
        assertFalse(p.update(100, s, i).pickPosition); assertTrue(p.blinking());
        assertEquals(RELEASE, p.update(101, s, i).action); assertFalse(p.blinking());
        assertEquals(RELEASE, p.update(151, s, i).action);
        assertEquals(PULSE, p.update(152, s, i).action);
        assertEquals(RELEASE, p.update(202, s, i).action);
    }

    @Test public void pulseRetainsTheOriginalCriteriaLossFlagUntilItsDelayExpires() {
        DemiseFakeLagPolicy p = policy(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings(); s.smart = false;
        DemiseFakeLagPolicy.Input i = input(5); p.update(51, s, i);
        i.visible = false; assertEquals(RELEASE, p.update(70, s, i).action);
        assertTrue("FakeLag.blinking differs from the underlying disabled BlinkComponent", p.blinking());
        assertFalse(p.picked()); p.update(101, s, i); assertFalse(p.blinking());
        i.eligible = false; assertEquals(NONE, p.update(102, s, i).action);
    }

    @Test public void simpleCriteriaKeepsInclusiveFloatRangeAndVisibilityWithCrossedRangeEndpoints() {
        DemiseFakeLagPolicy p = policy(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings(); s.smart = false;
        DemiseFakeLagPolicy.Input i = input(4 - 1e-8);
        assertTrue("source casts the measured distance to float", p.criteria(0, s, i));
        i.boxDistance = 6; assertTrue(p.criteria(0, s, i));
        i.boxDistance = 6.01; assertFalse(p.criteria(0, s, i));
        s.attackRange = 8; i.boxDistance = 7; assertTrue(p.criteria(0, s, i));
        i.visible = false; assertFalse(p.criteria(0, s, i)); i.visible = true;
        i.selfHurtTime = 1; assertFalse(p.criteria(0, s, i)); s.hurtTimeToStop = 1; assertTrue(p.criteria(0, s, i));
        s.alwaysSpoof = true; i.boxDistance = 1000; assertTrue(p.criteria(0, s, i));
    }

    @Test public void smartCriteriaPreservesHurtRecoveryAttackThresholdAndStrictApproach() {
        DemiseFakeLagPolicy p = policy(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings();
        DemiseFakeLagPolicy.Input i = input(5); i.visible = false;
        assertFalse(p.criteria(100, s, i)); assertTrue(p.criteria(101, s, i));
        i.serverDistance = i.clientDistance; assertFalse(p.criteria(101, s, i)); i.serverDistance = 5;
        i.selfHurtTime = 1; assertFalse(p.criteria(200, s, i)); i.selfHurtTime = 0;
        assertFalse(p.criteria(300, s, i)); assertTrue(p.criteria(301, s, i));
        p.attack(); i.targetHurtTime = 4; assertTrue(p.criteria(301, s, i));
        i.targetHurtTime = 3; assertFalse(p.criteria(301, s, i));
        p.update(302, s, i); assertTrue("normal Update consumes the attack marker", p.criteria(303, s, i));
    }

    @Test public void keepRangeProducesOneInputTapAndTimerTicksAreFiftyMillisecondsWithStrictExpiry() {
        DemiseFakeLagPolicy p = policy(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings();
        DemiseFakeLagPolicy.Input i = input(2.75); s.keepRange = "WTap"; p.attack();
        p.update(200, s, i); assertTrue(p.consumeWTap()); assertFalse(p.consumeWTap());
        s.keepRange = "Timer down"; p.attack(); p.update(201, s, i);
        assertEquals(.75f, p.timer(251, s), 0); assertTrue(Float.isNaN(p.timer(252, s)));
        p.attack(); p.update(300, s, i); s.smart = false;
        assertTrue("a configuration change cannot strand the global timer", Float.isNaN(p.timer(300, s)));
        p.reset(400); assertFalse(p.consumeWTap()); assertTrue(Float.isNaN(p.timer(400, s)));
    }

    @Test public void experimentalCounterRetainsSourceRepeatedEvaluationAndItsExplicitOverride() {
        DemiseFakeLagPolicy p = policy(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings();
        s.dynamicHurtTime = true; s.searchRange = 1;
        DemiseFakeLagPolicy.Input i = input(4); i.selfHurtTime = 9; i.mouseDistance = 4;
        assertFalse(p.criteria(1, s, i)); assertFalse(p.criteria(1, s, i)); assertTrue(p.criteria(1, s, i));
        assertEquals(3, p.targetHits()); i.targetHurtTime = 10; assertFalse(p.criteria(1, s, i)); assertEquals(0, p.targetHits());
        i.targetHurtTime = 0; i.boxDistance = 4.51; p.criteria(1, s, i); assertEquals(0, p.targetHits());
    }

    @Test public void spoofResamplesAndUsesOpponentDelayOnlyForTheSmartAimingSwing() {
        DemiseFakeLagPolicy p = policy(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings(); s.mode = "Spoof";
        DemiseFakeLagPolicy.Input i = input(5); i.targetAimingAtServer = i.targetSwinging = true;
        DemiseFakeLagPolicy.Result result = p.update(101, s, i);
        assertEquals(SPOOF, result.action); assertEquals(200, result.delayMs); assertFalse(result.resetPosition);
        i.targetSwinging = false; assertEquals(100, p.update(102, s, i).delayMs);
        i.clientDistance = i.serverDistance; result = p.update(103, s, i);
        assertEquals(RELEASE, result.action); assertTrue(result.resetPosition); assertFalse(p.blinking());
        assertEquals(NONE, p.update(104, s, i).action);
    }

    @Test public void firstHitResetsOnlyAfterLeavingThreeBlocksAndCrossedDelayBoundsAreSampledSafely() {
        double[] sampled = new double[2]; DemiseFakeLagPolicy p = new DemiseFakeLagPolicy((a, b) -> { sampled[0] = a; sampled[1] = b; return a; });
        p.reset(0); p.consumeFirstHit(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings();
        s.delayMin = 400; s.delayMax = 100; s.smart = false;
        DemiseFakeLagPolicy.Input i = input(3); p.update(1, s, i); assertFalse(p.firstHit());
        assertArrayEquals(new double[]{100, 400}, sampled, 0);
        i.boxDistance = 3.001; p.update(2, s, i); assertTrue(p.firstHit());
        p.reset(10); assertTrue(p.firstHit()); assertFalse(p.blinking()); assertEquals(0, p.delayMs());
    }

    @Test public void historicalBoxDistanceUsesTheCurrentClosestPointAndMouseDistanceUsesTheHitbox() {
        AxisAlignedBB box = new AxisAlignedBB(3, 0, 0, 4, 2, 1);
        Vec3 current = new Vec3(0, 1.62, .5), historical = new Vec3(5, 1.62, .5);
        assertEquals(2, DemiseFakeLagMath.boxDistance(current, historical, box), 0);
        assertEquals(1, DemiseFakeLagMath.boxDistance(historical, historical, box), 0);
        assertEquals(3, DemiseFakeLagMath.mouseDistance(current, new Vec3(1, 0, 0), box, false), 0);
        assertEquals(Double.MAX_VALUE, DemiseFakeLagMath.mouseDistance(current, new Vec3(0, 0, 1), box, false), 0);
        assertEquals(Double.MAX_VALUE, DemiseFakeLagMath.mouseDistance(current, new Vec3(1, 0, 0), box, true), 0);
    }

    @Test public void postDispatchFlagsUseFreshTimeAndTheCallbacksCurrentBlinkingState() {
        DemiseFakeLagPolicy p = policy(); DemiseFakeLagPolicy.Settings s = new DemiseFakeLagPolicy.Settings(); s.smart = false;
        DemiseFakeLagPolicy.Input i = input(5); p.update(51, s, i); i.visible = false;
        assertEquals(RELEASE, p.beginUpdate(101, s, i).action);
        assertTrue("source dispatch still sees its old blinking flag", p.blinking()); assertTrue(p.picked());
        p.earlyRelease(); p.finishUpdate(101); assertFalse(p.blinking()); assertFalse(p.picked());
        i.visible = true; p.update(102, s, i);
        assertEquals("callback prevented the previous timer reset; this reset occurred at 102", RELEASE, p.update(152, s, i).action);
        assertEquals(PULSE, p.update(153, s, i).action);

        p = policy(); p.update(51, s, i); i.visible = false;
        p.beginUpdate(90, s, i); p.finishUpdate(101); assertFalse("elapsed is sampled after dispatch", p.blinking());

        p = policy(); s.mode = "Spoof"; i.visible = true; p.update(51, s, i); i.visible = false;
        DemiseFakeLagPolicy.Result result = p.beginUpdate(52, s, i);
        assertEquals(RELEASE, result.action); assertTrue(result.resetPosition); assertTrue(p.blinking());
        p.finishUpdate(52); assertFalse(p.blinking()); assertEquals(NONE, p.update(53, s, i).action);
    }
}
