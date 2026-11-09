package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

/** Deterministic, Minecraft-free JUnit 4 fixtures for the optional Adaptive math path. */
public class AdaptiveMotionControllerTest {
    private static final AdaptiveMotionController.Limits LIMITS = AdaptiveMotionController.Limits.balanced();

    private static AdaptiveMotionController.Acquisition token(long epoch, long ordinal) {
        return new AdaptiveMotionController.Acquisition(new Object(), new Object(), new Object(), epoch, ordinal);
    }

    private static AdaptiveMotionController.Result step(AdaptiveMotionController c,
            AdaptiveMotionController.Acquisition a, long sequence, long nanos,
            double currentYaw, double currentPitch, double targetYaw, double targetPitch) {
        return c.advance(a, sequence, nanos, currentYaw, currentPitch, targetYaw, targetPitch, true);
    }

    private static double wrap(double angle) {
        double v = angle % 360;
        if (v <= -180) v += 360;
        if (v > 180) v -= 360;
        return v;
    }

    @Test public void zeroErrorRemainsQuietAndOldModesAreNotReferenced() {
        AdaptiveMotionController c = new AdaptiveMotionController(7, LIMITS);
        AdaptiveMotionController.Acquisition a = token(0, 0);
        for (int i = 0; i < 40; i++) {
            AdaptiveMotionController.Result r = step(c, a, i, i * 50_000_000L, 33, -4, 33, -4);
            assertFalse(r.candidate);
            assertEquals(33, r.yaw, 0);
            assertEquals(-4, r.pitch, 0);
            assertEquals(0, r.yawVelocity, 0);
            assertEquals(0, r.pitchVelocity, 0);
        }
    }

    @Test public void wrapAndPitchEdgeAreBoundedWithoutOvershoot() {
        for (int sign : new int[]{-1, 1}) {
            AdaptiveMotionController c = new AdaptiveMotionController(38, LIMITS);
            AdaptiveMotionController.Acquisition a = token(1, sign + 2);
            double yaw = sign > 0 ? 179 : -179;
            double target = -yaw;
            double pitch = sign > 0 ? 89 : -89;
            double desiredPitch = sign > 0 ? 200 : -200;
            step(c, a, 0, 0, yaw, pitch, target, desiredPitch);
            for (int i = 1; i <= 80; i++) {
                double before = Math.abs(wrap(target - yaw));
                AdaptiveMotionController.Result r = step(c, a, i, i * 50_000_000L,
                        yaw, pitch, target, desiredPitch);
                assertTrue(Double.isFinite(r.yaw));
                assertTrue(Double.isFinite(r.pitch));
                assertTrue(r.pitch >= -90 && r.pitch <= 90);
                assertTrue(Math.abs(wrap(r.yaw - yaw)) <= before + 1e-9);
                assertTrue(Math.abs(wrap(target - r.yaw)) <= before + 1e-9);
                assertTrue(Math.abs(wrap(r.yaw - yaw)) <= LIMITS.maxYawSpeed * .05 + 1e-8);
                yaw = r.yaw;
                pitch = r.pitch;
            }
            assertEquals(0, wrap(target - yaw), 1e-7);
            assertEquals(sign > 0 ? 90 : -90, pitch, 1e-7);
        }
    }

    @Test public void invalidAnglesNoTimeAndBadLimitsNeverAdvance() {        AdaptiveMotionController c = new AdaptiveMotionController(25, LIMITS);
        AdaptiveMotionController.Acquisition a = token(1, 1);
        assertFalse(step(c, a, 0, 0, 0, 0, 45, 12).candidate);
        assertFalse(step(c, a, 1, 0, 0, 0, 45, 12).candidate);
        assertFalse(step(c, a, 2, -10, 0, 0, 45, 12).candidate);
        assertNull(c.profile()); // Bad monotonic time invalidated the acquisition.
        assertFalse(step(c, a, 3, 100_000_000, 0, 0, 45, 12).candidate);
        AdaptiveMotionController.Acquisition b = token(1, 2);
        step(c, b, 0, 0, 0, 0, 45, 12);
        assertFalse(step(c, b, 1, 50_000_000, 0, 0, Double.NaN, 12).candidate);
        assertNull(c.profile());
        AdaptiveMotionController.Acquisition next = token(1, 3);
        step(c, next, 0, 0, 0, 0, 45, 12);
        assertFalse(step(c, next, 1, 50_000_000, Double.POSITIVE_INFINITY, 0, 45, 12).candidate);
        assertNull(c.profile());
        for (double invalid : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            try {
                new AdaptiveMotionController.Limits(invalid, 50, 300, 9000, .1, .1);
                fail("must reject invalid speed");
            } catch (IllegalArgumentException expected) { /* expected */ }
        }
        try {
            new AdaptiveMotionController.Limits(100, 50, 300, 9000, .8, .1);
            fail("must bound variation");
        } catch (IllegalArgumentException expected) { /* expected */ }
    }

    @Test public void staticTargetConvergesAndMaintainsSpeedAccelerationAndJerkLimits() {
        for (long seed = 0; seed < 64; seed++) {
            AdaptiveMotionController c = new AdaptiveMotionController(seed, LIMITS);
            AdaptiveMotionController.Acquisition a = token(2, 8);
            double yaw = -155, pitch = -40;
            double previousYawA = 0, previousPitchA = 0;
            step(c, a, 0, 0, yaw, pitch, 90, 30);
            for (int tick = 1; tick <= 180; tick++) {
                AdaptiveMotionController.Result r = step(c, a, tick, tick * 50_000_000L,
                        yaw, pitch, 90, 30);
                assertTrue(Double.isFinite(r.yaw) && Double.isFinite(r.pitch));
                assertTrue(Math.abs(r.yawVelocity) <= LIMITS.maxYawSpeed + 1e-8);
                assertTrue(Math.abs(r.pitchVelocity) <= LIMITS.maxPitchSpeed + 1e-8);
                assertTrue(Math.abs(r.yawAcceleration) <= LIMITS.maxAcceleration * 1.18 + 1e-8);
                assertTrue(Math.abs(r.pitchAcceleration) <= LIMITS.maxAcceleration * 1.18 + 1e-8);
                // The explicit settle-at-target case is a zero-motion safety hold.
                if (r.candidate && Math.abs(wrap(90 - yaw)) > 15
                        && Math.abs(30 - pitch) > 15) {
                    assertTrue(Math.abs(r.yawAcceleration - previousYawA) <= LIMITS.maxJerk * .05 + 1e-8);
                    assertTrue(Math.abs(r.pitchAcceleration - previousPitchA) <= LIMITS.maxJerk * .05 + 1e-8);
                }
                previousYawA = r.yawAcceleration;
                previousPitchA = r.pitchAcceleration;
                yaw = r.yaw;
                pitch = r.pitch;
            }
            assertEquals("seed " + seed, 0, wrap(90 - yaw), 1e-5);
            assertEquals("seed " + seed, 30, pitch, 1e-5);
        }
    }

    @Test public void profilesVaryByAcquisitionButRemainBoundedAndSeedReproducible() {
        double minSpeed = 9, maxSpeed = -9, minAccel = 9, maxAccel = -9;
        boolean positiveCurve = false, negativeCurve = false;
        for (long seed = 0; seed < 64; seed++) {
            AdaptiveMotionController a = new AdaptiveMotionController(seed, LIMITS);
            AdaptiveMotionController b = new AdaptiveMotionController(seed, LIMITS);
            AdaptiveMotionController.Acquisition ta = token(4, 25), tb = token(4, 25);
            step(a, ta, 0, 0, -20, -10, 100, 35);
            step(b, tb, 0, 0, -20, -10, 100, 35);
            AdaptiveMotionController.Profile p = a.profile(), q = b.profile();
            assertNotNull(p);
            assertEquals(p.speedScale, q.speedScale, 0);
            assertEquals(p.accelerationScale, q.accelerationScale, 0);
            assertEquals(p.brakingScale, q.brakingScale, 0);
            assertEquals(p.lateralBias, q.lateralBias, 0);
            assertTrue(p.speedScale >= .85 && p.speedScale <= 1.15);
            assertTrue(p.accelerationScale >= .82 && p.accelerationScale <= 1.18);
            assertTrue(p.brakingScale >= .78 && p.brakingScale <= 1.22);
            assertTrue(Math.abs(p.lateralBias) <= LIMITS.curvature);
            minSpeed = Math.min(minSpeed, p.speedScale); maxSpeed = Math.max(maxSpeed, p.speedScale);
            minAccel = Math.min(minAccel, p.accelerationScale); maxAccel = Math.max(maxAccel, p.accelerationScale);
            positiveCurve |= p.lateralBias > .01;
            negativeCurve |= p.lateralBias < -.01;
        }
        assertTrue(maxSpeed - minSpeed > .15);
        assertTrue(maxAccel - minAccel > .15);
        assertTrue(positiveCurve && negativeCurve);
    }

    @Test public void noiseIsCorrelatedAndBoundedOverManyMotionSchedules() {
        double lagProducts = 0, energy = 0;
        for (long seed = 0; seed < 64; seed++) {
            AdaptiveMotionController c = new AdaptiveMotionController(seed, LIMITS);
            AdaptiveMotionController.Acquisition a = token(5, 1);
            double yaw = 0, prevNoise = 0;
            step(c, a, 0, 0, 0, 0, 90, 0);
            for (int tick = 1; tick <= 100; tick++) {
                AdaptiveMotionController.Result r = step(c, a, tick, tick * 50_000_000L,
                        yaw, 0, wrap(90 + tick * 9), 0);
                assertTrue(Math.abs(r.noise) <= 1 + 1e-10);
                if (tick > 1) lagProducts += r.noise * prevNoise;
                energy += r.noise * r.noise;
                prevNoise = r.noise;
                yaw = r.yaw;
            }
        }
        assertTrue("OU-like lag correlation should be positive", lagProducts > energy * .4);
    }

    @Test public void renderFrameRateAndDuplicateOrOldSequenceNeverAdvanceNoiseOrPose() {
        for (int fps : new int[]{20, 60, 144, 240}) {
            AdaptiveMotionController rendered = new AdaptiveMotionController(657, LIMITS);
            AdaptiveMotionController reference = new AdaptiveMotionController(657, LIMITS);
            AdaptiveMotionController.Acquisition a = token(6, 1), b = token(6, 1);
            double yawA = -5, yawB = -5;
            for (int motion = 0; motion <= 80; motion++) {
                long nanos = motion * 50_000_000L;
                AdaptiveMotionController.Result first = step(rendered, a, motion, nanos,
                        yawA, 2, 100, 2);
                AdaptiveMotionController.Result expected = step(reference, b, motion, nanos,
                        yawB, 2, 100, 2);
                assertEquals(expected.yaw, first.yaw, 0);
                assertEquals(expected.noise, first.noise, 0);
                for (int frame = 1; frame <= fps / 20 + 2; frame++) {
                    AdaptiveMotionController.Result repeat = step(rendered, a, motion,
                            nanos + frame * 1_000L, yawA, 2, 130, 40);
                    assertSame("cache the complete first motion result", first, repeat);
                }
                AdaptiveMotionController.Result stale = step(rendered, a, motion - 1,
                        nanos + 1, yawA, 2, 0, 0);
                if (motion > 0) assertFalse(stale.candidate);
                yawA = first.yaw;
                yawB = expected.yaw;
            }
        }
    }

    @Test public void movingTargetTracksAndTeleportRequiresFreshAcquisition() {
        AdaptiveMotionController c = new AdaptiveMotionController(87, LIMITS);
        Object owner = new Object(), scene = new Object(), target = new Object();
        AdaptiveMotionController.Acquisition a = new AdaptiveMotionController.Acquisition(owner, scene, target, 8, 40);
        double yaw = 0;
        step(c, a, 0, 0, yaw, 0, 70, 0);
        for (int tick = 1; tick <= 120; tick++) {
            double desired = 70 + .35 * tick;
            AdaptiveMotionController.Result r = step(c, a, tick, tick * 50_000_000L,
                    yaw, 0, desired, 0);
            assertTrue(Math.abs(wrap(r.yaw - yaw)) <= LIMITS.maxYawSpeed * .05 + 1e-8);
            yaw = r.yaw;
        }
        assertTrue("tracking must converge near its moving target", Math.abs(wrap(112 - yaw)) < 8);
        AdaptiveMotionController.Acquisition reusedOrdinal =
                new AdaptiveMotionController.Acquisition(owner, scene, new Object(), 8, 40);
        assertFalse(step(c, reusedOrdinal, 121, 6_050_000_000L, yaw, 0, -120, 0).candidate);
        assertNotNull(c.profile()); // Old target is not replaced by reused entity ID/ordinal.
        AdaptiveMotionController.Acquisition reacquired =
                new AdaptiveMotionController.Acquisition(owner, scene, reusedOrdinal.target, 8, 41);
        assertFalse(step(c, reacquired, 121, 6_050_000_000L, yaw, 0, -120, 0).candidate);
        AdaptiveMotionController.Result next = step(c, reacquired, 122, 6_100_000_000L, yaw, 0, -120, 0);
        assertTrue(next.candidate);
        assertTrue(Math.abs(wrap(next.yaw - yaw)) <= LIMITS.maxYawSpeed * .05 + 1e-8);
    }

    @Test public void cancellationAndStaleOwnerInvalidationCannotResurrectGhostTurns() {
        AdaptiveMotionController c = new AdaptiveMotionController(72, LIMITS);
        AdaptiveMotionController.Acquisition old = token(12, 7);
        step(c, old, 0, 0, 0, 0, 90, 0);
        assertTrue(step(c, old, 1, 50_000_000L, 0, 0, 90, 0).candidate);
        assertFalse(c.advance(old, 1, 50_000_000L, 0, 0, 90, 0, false).candidate);
        assertNull(c.profile());
        assertFalse(step(c, old, 2, 100_000_000L, 0, 0, 90, 0).candidate);
        AdaptiveMotionController.Acquisition current = token(12, 8);
        step(c, current, 2, 100_000_000L, 0, 0, 90, 0);
        assertFalse(c.invalidateIfCurrent(old));
        assertTrue(step(c, current, 3, 150_000_000L, 0, 0, 90, 0).candidate);
        // Wrong owner/scene/target with the same ordinal is HOLD, not a new owner.
        assertFalse(step(c, token(12, 8), 4, 200_000_000L, 0, 0, -120, 0).candidate);
        assertNotNull(c.profile());
        c.reset();
        assertFalse(step(c, current, 4, 200_000_000L, 0, 0, 90, 0).candidate);
        assertFalse(c.invalidateIfCurrent(current));
        AdaptiveMotionController.Acquisition nextScene = token(13, 0);
        assertFalse(step(c, nextScene, 0, 200_000_000L, 0, 0, -120, 0).candidate);
        assertTrue(step(c, nextScene, 1, 250_000_000L, 0, 0, -120, 0).candidate);
        assertFalse(c.invalidateIfCurrent(current));
        assertNotNull(c.profile());
    }

    @Test public void pauseSpikeDoesNotAccumulateUnboundedMotionBudget() {
        AdaptiveMotionController c = new AdaptiveMotionController(5, LIMITS);
        AdaptiveMotionController.Acquisition a = token(1, 0);
        step(c, a, 0, 0, 0, 0, 90, 0);
        AdaptiveMotionController.Result capped = step(c, a, 1, 200_000_000L, 0, 0, 90, 0);
        assertTrue(capped.candidate);
        assertTrue(Math.abs(capped.yaw) <= LIMITS.maxYawSpeed * .075 + 1e-8);
        assertFalse(step(c, a, 2, 500_000_000L, capped.yaw, 0, 90, 0).candidate);
        assertNull(c.profile());
        assertFalse(step(c, a, 3, 550_000_000L, capped.yaw, 0, 90, 0).candidate);
    }

    /** NaN keep caps must be a byte-for-byte alias of the legacy uniform limits. */
    @Test public void nanKeepCapsRunTheExactLegacyTrajectory() {
        AdaptiveMotionController.Limits base = new AdaptiveMotionController.Limits(
                240, 120, 1800, 36000, 0.1, 0.1);
        AdaptiveMotionController legacy = new AdaptiveMotionController(11, base);
        AdaptiveMotionController aliased = new AdaptiveMotionController(11,
                new AdaptiveMotionController.Limits(240, 120, 1800, 36000, 0.1, 0.1,
                        Double.NaN, Double.NaN, Double.NaN));
        AdaptiveMotionController.Acquisition a = token(4, 1), b = token(4, 1);
        double yaw = 0, pitch = 0;
        for (int i = 0; i <= 120; i++) {
            AdaptiveMotionController.Result first = step(legacy, a, i, i * 50_000_000L,
                    yaw, pitch, 137, -41);
            AdaptiveMotionController.Result second = step(aliased, b, i, i * 50_000_000L,
                    yaw, pitch, 137, -41);
            assertEquals(first.candidate, second.candidate);
            assertEquals(first.yaw, second.yaw, 0);
            assertEquals(first.pitch, second.pitch, 0);
            assertEquals(first.yawVelocity, second.yawVelocity, 0);
            assertEquals(first.pitchVelocity, second.pitchVelocity, 0);
            yaw = first.yaw; pitch = first.pitch;
            if (!first.candidate && i > 8) break;
        }
        assertEquals(0, wrap(137 - yaw), 1e-6);
    }

    /** Optional keeping caps only calm the final approach; convergence stays exact. */
    @Test public void keepingPhaseCapsEngageOnlyNearTheTarget() {
        AdaptiveMotionController.Limits phased = new AdaptiveMotionController.Limits(
                240, 120, 1800, 36000, 0, 0, 54, 30, .55);
        AdaptiveMotionController c = new AdaptiveMotionController(11, phased);
        AdaptiveMotionController.Acquisition a = token(2, 1);
        double initialDistance = Math.hypot(150, 75);
        double yaw = 0, pitch = 0, progress = 0;
        boolean sawFastTravel = false;
        boolean sawCalmKeeping = false;
        for (int i = 0; i <= 260; i++) {
            AdaptiveMotionController.Result r = step(c, a, i, i * 50_000_000L, yaw, pitch, 150, 75);
            assertTrue(Double.isFinite(r.yaw) && Double.isFinite(r.pitch));
            double previousProgress = progress;
            if (previousProgress >= phased.keepEnterProgress) {
                // The step after crossing the threshold travels under the keeping caps.
                assertTrue("keep yaw cap violated: yawVelocity=" + r.yawVelocity + " step=" + i,
                        Math.abs(r.yawVelocity) <= phased.keepYawSpeed + 1e-6);
                assertTrue("keep pitch cap violated: pitchVelocity=" + r.pitchVelocity + " step=" + i,
                        Math.abs(r.pitchVelocity) <= phased.keepPitchSpeed + 1e-6);
                sawCalmKeeping = true;
            }
            yaw = r.yaw; pitch = r.pitch;
            progress = 1 - Math.hypot(wrap(150 - yaw), 75 - pitch) / initialDistance;
            if (r.candidate && progress < phased.keepEnterProgress
                    && Math.abs(r.yawVelocity) > phased.keepYawSpeed + 1e-6
                    && Math.abs(r.pitchVelocity) > phased.keepPitchSpeed + 1e-6) sawFastTravel = true;
        }
        assertTrue("initial phase must travel faster than the keeping caps", sawFastTravel);
        assertTrue("keeping phase must engage for a calm hold", sawCalmKeeping);
        assertEquals(0, wrap(150 - yaw), 1e-6);
        assertEquals(75, pitch, 1e-6);
    }

    @Test public void invalidKeepingLimitsAreRejected() {
        for (double keep : new double[]{0, -1, Double.POSITIVE_INFINITY}) {
            try { new AdaptiveMotionController.Limits(240, 120, 1800, 36000, 0, 0, keep, 30, .4); fail(); }
            catch (IllegalArgumentException expected) { }
            try { new AdaptiveMotionController.Limits(240, 120, 1800, 36000, 0, 0, 54, keep, .4); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        for (double enter : new double[]{-0.5, 1.0, Double.NaN, Double.POSITIVE_INFINITY}) {
            try { new AdaptiveMotionController.Limits(240, 120, 1800, 36000, 0, 0, 54, 30, enter); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        try { new AdaptiveMotionController.Limits(240, 120, 1800, 36000, 0, 0, Double.NaN, Double.NaN, .4); fail(); }
        catch (IllegalArgumentException expected) { }
        // A valid single-axis phase stays legal.
        new AdaptiveMotionController.Limits(240, 120, 1800, 36000, 0, 0, 54, Double.NaN, .4);
    }
}
