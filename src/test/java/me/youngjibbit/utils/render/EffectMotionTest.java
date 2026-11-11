package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class EffectMotionTest {
    private static EffectMotion moving(int hz, int scale) {
        EffectMotion result = new EffectMotion();
        for (int i = 0; i <= hz; i++) {
            double t = (double) i / hz;
            result.sample(t, (float) ((100 + t * 240) * scale), (float) ((200 - t * 120) * scale),
                    120 * scale, 200 * scale, 1280 * scale, 720 * scale, true);
        }
        return result;
    }

    @Test public void equalMovementHasEqualResponseAtDifferentFrameRates() {
        EffectMotion low = moving(30, 1), medium = moving(60, 1), high = moving(144, 1);
        assertTrue(low.flowX() < -.39f);
        assertTrue(low.flowY() > .19f);
        assertEquals(low.flowX(), medium.flowX(), .0001f);
        assertEquals(low.flowY(), medium.flowY(), .0001f);
        assertEquals(low.flowX(), high.flowX(), .0001f);
        assertEquals(low.flowY(), high.flowY(), .0001f);
        assertEquals(low.energy(), high.energy(), .0001f);
    }

    @Test public void uniformPixelScalingPreservesTheResponse() {
        EffectMotion normal = moving(60, 1), scaled = moving(60, 2);
        assertEquals(normal.flowX(), scaled.flowX(), .00001f);
        assertEquals(normal.flowY(), scaled.flowY(), .00001f);
        assertEquals(normal.energy(), scaled.energy(), .00001f);
    }

    @Test public void stoppingHasAContinuousTailThatSettlesAtAllFrameRates() {
        EffectMotion low = moving(30, 1), high = moving(144, 1);
        low.sample(1 + 1.0 / 30, 340, 80, 120, 200, 1280, 720, true);
        assertTrue(low.energy() > .3f);
        for (int i = 2; i <= 30; i++) low.sample(1 + i / 30.0, 340, 80, 120, 200, 1280, 720, true);
        for (int i = 1; i <= 144; i++) high.sample(1 + i / 144.0, 340, 80, 120, 200, 1280, 720, true);
        assertTrue(low.energy() < .00001f);
        assertEquals(low.flowX(), high.flowX(), .00001f);
        assertEquals(low.flowY(), high.flowY(), .00001f);
    }

    @Test public void repeatedDrawsInOneFrameDoNotIntegrateOrReplaceTheAnchor() {
        EffectMotion single = moving(60, 1), repeated = moving(60, 1);
        float flow = repeated.flowX();
        repeated.sample(1, 900, -900, 40, 40, 1280, 720, true);
        assertEquals(flow, repeated.flowX(), 0);
        single.sample(1.05, 352, 74, 120, 200, 1280, 720, true);
        repeated.sample(1.05, 352, 74, 120, 200, 1280, 720, true);
        assertEquals(single.flowX(), repeated.flowX(), 0);
        assertEquals(single.flowY(), repeated.flowY(), 0);
    }

    @Test public void teleportsLargeResizesAndViewportChangesResetWithoutImpulse() {
        EffectMotion teleport = moving(60, 1);
        teleport.sample(1.016, 5000, 5000, 120, 200, 1280, 720, true);
        assertNeutral(teleport);
        EffectMotion resized = moving(60, 1);
        resized.sample(1.016, 340, 80, 400, 200, 1280, 720, true);
        assertNeutral(resized);
        EffectMotion viewport = moving(60, 1);
        viewport.sample(1.016, 340, 80, 120, 200, 1920, 1080, true);
        assertNeutral(viewport);
    }

    @Test public void disabledMotionDoesNotReplayMovementAfterReenable() {
        EffectMotion motion = moving(60, 1);
        motion.sample(1.1, 350, 80, 120, 200, 1280, 720, false);
        assertNeutral(motion);
        motion.sample(1.2, 600, 80, 120, 200, 1280, 720, true);
        assertNeutral(motion);
        motion.sample(1.3, 600, 80, 120, 200, 1280, 720, true);
        assertNeutral(motion);
    }

    @Test public void clockGapsRewindsAndInvalidGeometryDiscardStaleMotion() {
        EffectMotion gap = moving(60, 1);
        gap.sample(2, 350, 80, 120, 200, 1280, 720, true);
        assertNeutral(gap);
        EffectMotion rewind = moving(60, 1);
        rewind.sample(.5, 200, 80, 120, 200, 1280, 720, true);
        assertNeutral(rewind);
        EffectMotion invalid = moving(60, 1);
        invalid.sample(1.1, Float.NaN, 80, 120, 200, 1280, 720, true);
        assertNeutral(invalid);
        invalid.sample(1.2, 600, 80, 120, 200, 1280, 720, true);
        assertNeutral(invalid);
        invalid.sample(Double.NaN, 600, 80, 120, 200, 1280, 720, true);
        assertNeutral(invalid);
    }

    @Test public void fastDirectionChangesStayFiniteAndBounded() {
        EffectMotion motion = new EffectMotion();
        for (int i = 0; i < 600; i++) {
            motion.sample(i / 120.0, (float) (400 + 20 * Math.sin(i * .2)),
                    (float) (300 + 20 * Math.cos(i * .2)), 40, 40, 1280, 720, true);
            assertTrue(Float.isFinite(motion.flowX()));
            assertTrue(Float.isFinite(motion.flowY()));
            assertTrue(motion.energy() >= 0 && motion.energy() <= 1);
            assertTrue(Math.hypot(motion.flowX(), motion.flowY()) <= 1.00001);
        }
        motion.reset();
        assertNeutral(motion);
    }

    @Test public void numericallyIndistinguishableSamplesDoNotAmplifyIntoInvalidFlow() {
        EffectMotion motion = new EffectMotion();
        motion.sample(0, 100, 100, 40, 40, 1280, 720, true);
        motion.sample(Double.MIN_VALUE, 100.01f, 100, 40, 40, 1280, 720, true);
        assertNeutral(motion);
        motion.sample(.02, 100, 100, 40, 40, 1280, 720, true);
        assertNeutral(motion);
    }

    private static void assertNeutral(EffectMotion motion) {
        assertEquals(0, motion.flowX(), 0);
        assertEquals(0, motion.flowY(), 0);
        assertEquals(0, motion.energy(), 0);
    }
}
