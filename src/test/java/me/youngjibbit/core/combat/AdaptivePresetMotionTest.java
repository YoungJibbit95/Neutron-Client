package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

/** Java 8 deterministic preset, moving-box, time and steering regression. */
public class AdaptivePresetMotionTest {
    private static AdaptivePresetMotion.Configuration config(
            AdaptivePresetMotion.Speed speed, AdaptivePresetMotion.Hitpoint point, double strength) {
        return new AdaptivePresetMotion.Configuration(speed, point, strength, 28, 20);
    }

    @Test public void allSpeedPresetsUseOneBoundedControllerAndMonotoneCaps() {
        double lastYaw = 0, lastPitch = 0, lastAcceleration = 0;
        for (AdaptivePresetMotion.Speed speed : AdaptivePresetMotion.Speed.values()) {
            AdaptivePresetMotion.Configuration p = config(speed, AdaptivePresetMotion.Hitpoint.STABLE, .5);
            AdaptiveMotionController.Limits limits = p.limits();
            assertTrue(limits.maxYawSpeed > lastYaw);
            assertTrue(limits.maxPitchSpeed > lastPitch);
            assertTrue(limits.maxAcceleration > lastAcceleration);
            assertTrue(limits.maxYawSpeed <= 720);
            assertTrue(limits.maxPitchSpeed <= 720);
            assertTrue(limits.maxAcceleration <= 4200);
            assertTrue(limits.maxJerk <= 84000);
            assertEquals(.14, limits.variation, 1e-12);
            assertEquals(.12, limits.curvature, 1e-12);
            new AdaptiveMotionController(2, limits);
            lastYaw = limits.maxYawSpeed;
            lastPitch = limits.maxPitchSpeed;
            lastAcceleration = limits.maxAcceleration;
        }
    }

    @Test public void variationIsExplicitlyBoundedAndInvalidSettingsFailClosed() {
        for (double v : new double[]{0, .25, .5, 1}) {
            AdaptiveMotionController.Limits limit =
                    config(AdaptivePresetMotion.Speed.BALANCED, AdaptivePresetMotion.Hitpoint.STABLE, v).limits();
            assertEquals(.28 * v, limit.variation, 1e-12);
            assertEquals(.24 * v, limit.curvature, 1e-12);
            assertTrue(limit.variation <= .3 && limit.curvature <= .25);
        }
        for (double v : new double[]{-1, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            try { config(AdaptivePresetMotion.Speed.BALANCED, AdaptivePresetMotion.Hitpoint.STABLE, v);
                fail("invalid variation " + v);
            } catch (IllegalArgumentException expected) { /* expected */ }
        }
        try { new AdaptivePresetMotion.Configuration(null, AdaptivePresetMotion.Hitpoint.STABLE, .5, 28, 20);
            fail("no speed");
        } catch (IllegalArgumentException expected) { /* expected */ }
        try { new AdaptivePresetMotion.Configuration(AdaptivePresetMotion.Speed.CALM, null, .5, 28, 20);
            fail("no hitpoint");
        } catch (IllegalArgumentException expected) { /* expected */ }
        try { new AdaptivePresetMotion.Configuration(AdaptivePresetMotion.Speed.CALM,
                AdaptivePresetMotion.Hitpoint.STABLE, .5, Double.NaN, 20);
            fail("non-finite yaw");
        } catch (IllegalArgumentException expected) { /* expected */ }
    }

    @Test public void stableUsesExactlyExistingPointAndAllStylesRespectMovingBoxes() {
        for (AdaptivePresetMotion.Hitpoint style : AdaptivePresetMotion.Hitpoint.values()) {
            AdaptivePresetMotion.HitpointTrajectory sampler =
                    new AdaptivePresetMotion.HitpointTrajectory(1357, style);
            double[] initial = sampler.point(.4, .7, .6, 0, 0, 0, 1, 1, 1, 0);
            assertArrayEquals(new double[]{.4, .7, .6}, initial, 1e-12);
            for (int i = 0; i <= 360; i++) {
                double tx = i * .12, t = i * .025;
                double[] p = sampler.point(tx + .4, .7, .6, tx, 0, 0, tx + 1, 1, 1, t);
                assertNotNull(p);
                assertTrue(p[0] >= tx && p[0] <= tx + 1);
                assertTrue(p[1] >= 0 && p[1] <= 1);
                assertTrue(p[2] >= 0 && p[2] <= 1);
                if (style == AdaptivePresetMotion.Hitpoint.STABLE)
                    assertArrayEquals(new double[]{tx + .4, .7, .6}, p, 1e-11);
            }
        }
    }

    @Test public void presetNoiseIsContinuousAndStableAcrossRenderRates() {
        for (AdaptivePresetMotion.Hitpoint style : AdaptivePresetMotion.Hitpoint.values()) {
            AdaptivePresetMotion.HitpointTrajectory sample =
                    new AdaptivePresetMotion.HitpointTrajectory(394, style);
            double[] previous = sample.point(.5, .55, .5, 0, 0, 0, 1, 1, 1, 0);
            boolean changed = false;
            for (int tick = 1; tick <= 240; tick++) {
                double time = tick * .025;
                double[] next = sample.point(.5, .55, .5, 0, 0, 0, 1, 1, 1, time);
                double distance = Math.hypot(next[0] - previous[0], next[2] - previous[2]);
                assertTrue("smooth local change", distance < .045);
                changed |= distance > 1e-7;
                for (int fps : new int[]{20,60,144,240}) {
                    AdaptivePresetMotion.HitpointTrajectory frames =
                            new AdaptivePresetMotion.HitpointTrajectory(394, style);
                    for (int render = 0; render < fps / 20; render++)
                        assertArrayEquals(next, frames.point(.5,.55,.5,0,0,0,1,1,1,time), 0);
                }
                previous = next;
            }
            assertEquals(style != AdaptivePresetMotion.Hitpoint.STABLE, changed);
        }
    }

    @Test public void invalidBoxAndTimeDoNotYieldDisplacedPoints() {
        AdaptivePresetMotion.HitpointTrajectory t =
                new AdaptivePresetMotion.HitpointTrajectory(4, AdaptivePresetMotion.Hitpoint.DYNAMIC);
        assertNull(t.point(0,0,0, 0,0,0, 0,1,1, 1));
        assertNull(t.point(0,0,0, 0,0,0, Double.NaN,1,1, 1));
        assertNull(t.point(0,0,0, 0,0,0, 1,1,1, -1));
        assertNull(t.point(0,0,0, 0,0,0, 1,1,1, Double.NaN));
        assertNull(t.point(Double.POSITIVE_INFINITY,0,0,0,0,0,1,1,1,1));
    }

    @Test public void abruptReversalRespectsAngularSpeedAndJerkForEachPreset() {
        for (AdaptivePresetMotion.Speed speed : AdaptivePresetMotion.Speed.values()) {
            for (long seed = 0; seed < 16; seed++) {
                AdaptiveMotionController.Limits lim = config(speed,
                        AdaptivePresetMotion.Hitpoint.DYNAMIC, .85).limits();
                AdaptiveMotionController motion = new AdaptiveMotionController(seed, lim);
                Object owner = new Object(), world = new Object(), target = new Object();
                AdaptiveMotionController.Acquisition acquisition =
                        new AdaptiveMotionController.Acquisition(owner, world, target, 1, 1);
                double yaw = -80, pitch = -30, before = 0;
                for (int tick = 0; tick < 100; tick++) {
                    double desired = tick < 20 ? 80 : tick < 50 ? -110 : 120;
                    AdaptiveMotionController.Result next = motion.advance(acquisition,
                            tick, tick * 50_000_000L, yaw, pitch, desired, 25, true);
                    assertTrue(Double.isFinite(next.yaw) && Double.isFinite(next.pitch));
                    assertTrue(Math.abs(next.yawVelocity) <= lim.maxYawSpeed + 1e-8);
                    assertTrue(Math.abs(next.pitchVelocity) <= lim.maxPitchSpeed + 1e-8);
                    assertTrue(Math.abs(next.yawAcceleration) <= lim.maxAcceleration * 1.18 + 1e-8);
                    assertTrue(Math.abs(next.pitchAcceleration) <= lim.maxAcceleration * 1.18 + 1e-8);
                    double delta = Math.abs(wrap(next.yaw - yaw));
                    assertTrue(delta <= lim.maxYawSpeed * .05 + 1e-7);
                    if (tick > 1 && tick != 20 && tick != 50
                            && Math.abs(wrap(desired - yaw)) > 15 && Math.abs(next.yawVelocity) > .01)
                        assertTrue(Math.abs(next.yawAcceleration - before) <= lim.maxJerk * .05 + 1e-6);
                    before = next.yawAcceleration;
                    yaw = next.yaw; pitch = next.pitch;
                }
                assertTrue("still tracking after reversals", Math.abs(wrap(120-yaw)) < 35);
            }
        }
    }

    @Test public void acquireFreshTargetGetsIndependentSeedAndHold() {
        AdaptivePresetMotion.Configuration p=config(AdaptivePresetMotion.Speed.BALANCED,
                AdaptivePresetMotion.Hitpoint.DYNAMIC,.5);
        assertTrue(p.same(config(AdaptivePresetMotion.Speed.BALANCED, AdaptivePresetMotion.Hitpoint.DYNAMIC,.5)));
        assertFalse(p.same(config(AdaptivePresetMotion.Speed.SWIFT, AdaptivePresetMotion.Hitpoint.DYNAMIC,.5)));
        assertFalse(p.same(config(AdaptivePresetMotion.Speed.BALANCED, AdaptivePresetMotion.Hitpoint.ORGANIC,.5)));
        assertFalse(p.same(config(AdaptivePresetMotion.Speed.BALANCED, AdaptivePresetMotion.Hitpoint.DYNAMIC,.6)));
        AdaptivePresetMotion.HitpointTrajectory a = new AdaptivePresetMotion.HitpointTrajectory(1, p.hitpoint);
        AdaptivePresetMotion.HitpointTrajectory b = new AdaptivePresetMotion.HitpointTrajectory(2, p.hitpoint);
        assertArrayEquals(a.point(.5,.5,.5,0,0,0,1,1,1,0),
                b.point(.5,.5,.5,0,0,0,1,1,1,0),0);
        double[] moved = a.point(.5,.5,.5,0,0,0,1,1,1,1);
        double[] restarted = b.point(.5,.5,.5,0,0,0,1,1,1,0);
        assertArrayEquals(new double[]{.5,.5,.5}, restarted, 0);
        assertTrue(Math.abs(moved[0]-.5)+Math.abs(moved[1]-.5)+Math.abs(moved[2]-.5)>1e-5);
    }

    private static double wrap(double v) {
        v %= 360; if(v <= -180) v += 360; if(v > 180) v -= 360; return v;
    }
}
