package me.youngjibbit.core.world;

import org.junit.Test;
import static org.junit.Assert.*;

/** Actual mouse-envelope outputs: hard caps, continuity, useful settling and recovery. */
public class AdaptiveBridgeAimRangeTest {
    private static final long STEP = 50_000_000L;
    private static AdaptiveBridgePlanner.Scope scope() {
        return new AdaptiveBridgePlanner.Scope(new Object(), new Object(), new Object(), new Object(), 0, 1);
    }
    private static AdaptiveBridgeMotion.Settings settings(AdaptiveBridgeMotion.Mode mode,
            AdaptiveBridgeMotion.NoiseProfile profile, double min, double max, double amount) {
        return AdaptiveBridgeMotion.Settings.aimRange(mode, profile, min, max, amount, 1);
    }
    private static AdaptiveBridgeMotion.Turn step(AdaptiveBridgeMotion motion, AdaptiveBridgePlanner.Scope scope,
            Object identity, AdaptiveBridgeMotion.Settings settings, AdaptiveBridgeMotion.Task task,
            long tick, double yaw, double pitch, double targetYaw, double targetPitch) {
        return motion.advanceTask(scope, identity, settings, task, tick, tick * STEP,
                yaw, pitch, targetYaw, targetPitch, true);
    }
    private static double wrap(double angle) {
        angle %= 360; if (angle > 180) angle -= 360; if (angle <= -180) angle += 360; return angle;
    }

    @Test public void rangeAndPhaseLimitsApplyToEveryTaskIncludingClutch() {
        for (AdaptiveBridgeMotion.Mode mode : AdaptiveBridgeMotion.Mode.values())
            for (AdaptiveBridgeMotion.NoiseProfile profile : AdaptiveBridgeMotion.NoiseProfile.values()) {
                AdaptiveBridgeMotion.Settings settings = settings(mode, profile, 5, 12, 1);
                assertTrue(settings.same(settings(mode, profile, 12, 5, 1)));
                for (AdaptiveBridgeMotion.Task task : AdaptiveBridgeMotion.Task.values()) {
                    AdaptiveBridgeMotion.Turn result = step(new AdaptiveBridgeMotion(7), scope(), new Object(),
                            settings, task, 0, 0, 0, 150, 80);
                    assertTrue(result.candidate);
                    assertTrue(result.yaw > 0 && result.yaw <= 12);
                    assertTrue(result.pitch > 0 && result.pitch <= 12);
                    AdaptiveBridgeMotion.Settings limited = settings.withPhaseLimits(3, 4, 2, 1);
                    result = step(new AdaptiveBridgeMotion(7), scope(), new Object(), limited, task,
                            0, 0, 0, 150, 80);
                    assertEquals(task == AdaptiveBridgeMotion.Task.TRACKING ? 2 : 3, result.yaw, 1e-9);
                    assertEquals(task == AdaptiveBridgeMotion.Task.TRACKING ? 1 : 4, result.pitch, 1e-9);
                }
                AdaptiveBridgeMotion.Settings widePhases = settings.withPhaseLimits(180, 180, 180, 180);
                assertEquals(240, widePhases.yawSpeed(AdaptiveBridgeMotion.Task.CLUTCH), 0);
                assertEquals(240, widePhases.pitchSpeed(AdaptiveBridgeMotion.Task.TRACKING), 0);
            }
    }

    @Test public void correlatedProfilesAffectBothInitialAndKeepingTravelWithoutLeavingTheirRange() {
        for (AdaptiveBridgeMotion.Task task : new AdaptiveBridgeMotion.Task[]{
                AdaptiveBridgeMotion.Task.APPROACH, AdaptiveBridgeMotion.Task.TRACKING})
            for (AdaptiveBridgeMotion.NoiseProfile profile : AdaptiveBridgeMotion.NoiseProfile.values()) {
                AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(1349);
                AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
                AdaptiveBridgeMotion.Settings settings = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE,
                        profile, 5, 20, 1);
                double low = 180, high = 0, previous = Double.NaN, largestChange = 0;
                for (int tick = 0; tick < 180; tick++) {
                    // Long-distance samples isolate the speed envelope from endpoint braking.
                    AdaptiveBridgeMotion.Turn result = step(motion, scope, key, settings, task,
                            tick, 0, 0, 150, 80);
                    assertTrue(result.yaw >= 5 && result.yaw <= 20);
                    assertTrue(result.pitch >= 5 && result.pitch <= 20);
                    low = Math.min(low, result.yaw); high = Math.max(high, result.yaw);
                    if (!Double.isNaN(previous)) largestChange = Math.max(largestChange, Math.abs(result.yaw - previous));
                    previous = result.yaw;
                }
                if (profile == AdaptiveBridgeMotion.NoiseProfile.OFF) assertEquals(low, high, 0);
                else {
                    assertTrue(profile.name(), high - low > .05);
                    assertTrue("adjacent physical motions share a smooth noise field", largestChange < 1);
                }
            }
    }

    @Test public void faceChangesAndRenderDuplicatesCannotRestartOrResampleTheSpeedPattern() {
        AdaptiveBridgeMotion stable = new AdaptiveBridgeMotion(91), changing = new AdaptiveBridgeMotion(91);
        AdaptiveBridgePlanner.Scope a = scope(), b = scope(); Object held = new Object();
        AdaptiveBridgeMotion.Settings settings = settings(AdaptiveBridgeMotion.Mode.ADAPTIVE,
                AdaptiveBridgeMotion.NoiseProfile.LIVELY, 6, 25, 1);
        for (int tick = 0; tick < 200; tick++) {
            Object face = new Object();
            AdaptiveBridgeMotion.Turn expected = step(stable, a, held, settings, AdaptiveBridgeMotion.Task.APPROACH,
                    tick, 0, 0, 150, 80);
            AdaptiveBridgeMotion.Turn actual = step(changing, b, face, settings, AdaptiveBridgeMotion.Task.APPROACH,
                    tick, 0, 0, 150, 80);
            assertEquals(expected.yaw, actual.yaw, 0); assertEquals(expected.pitch, actual.pitch, 0);
            assertSame(actual, changing.advanceTask(b, face, settings, AdaptiveBridgeMotion.Task.CLUTCH,
                    tick, Long.MAX_VALUE, -100, -50, 100, 70, true));
        }
    }

    @Test public void settlingNeverCreatesOvershootOrMovementAtAnAlreadyUsefulAim() {
        for (AdaptiveBridgeMotion.Mode mode : AdaptiveBridgeMotion.Mode.values())
            for (AdaptiveBridgeMotion.NoiseProfile profile : AdaptiveBridgeMotion.NoiseProfile.values()) {
                AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(14);
                AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
                AdaptiveBridgeMotion.Settings settings = settings(mode, profile, 35, 180, 1);
                double yaw = 179, pitch = 70;
                for (int tick = 0; tick < 80; tick++) {
                    double errorYaw = wrap(-179 - yaw), errorPitch = 83.52 - pitch;
                    AdaptiveBridgeMotion.Turn result = step(motion, scope, key, settings,
                            AdaptiveBridgeMotion.Task.TRACKING, tick, yaw, pitch, -179, 83.52);
                    assertTrue(wrap(result.yaw - yaw) * errorYaw >= -1e-12);
                    assertTrue((result.pitch - pitch) * errorPitch >= -1e-12);
                    assertTrue(Math.abs(wrap(result.yaw - yaw)) <= Math.abs(errorYaw) + 1e-10);
                    assertTrue(Math.abs(result.pitch - pitch) <= Math.abs(errorPitch) + 1e-10);
                    yaw = result.yaw; pitch = result.pitch;
                }
                assertEquals(-179, yaw, 1e-7); assertEquals(83.52, pitch, 1e-7);
                AdaptiveBridgeMotion.Turn held = step(motion, scope, key, settings,
                        AdaptiveBridgeMotion.Task.TRACKING, 80, -179, 83.52, -179, 83.52);
                assertFalse(held.candidate); assertEquals(-179, held.yaw, 0); assertEquals(83.52, held.pitch, 0);
            }
    }

    @Test public void godbridgeAcquiresCheckedGeometryDirectlyWhileRespectingTheGlobalMaximum() {
        for (AdaptiveBridgeMotion.NoiseProfile profile : AdaptiveBridgeMotion.NoiseProfile.values()) {
            AdaptiveBridgeMotion.Settings fast = settings(AdaptiveBridgeMotion.Mode.GODBRIDGE, profile, 35, 180, 1);
            AdaptiveBridgeMotion.Turn result = step(new AdaptiveBridgeMotion(3), scope(), new Object(), fast,
                    AdaptiveBridgeMotion.Task.APPROACH, 0, 0, 0, 135, 83.52);
            assertEquals(135, result.yaw, 0); assertEquals(83.52, result.pitch, 0);
            AdaptiveBridgeMotion.Settings limited = settings(AdaptiveBridgeMotion.Mode.GODBRIDGE, profile, 10, 20, 1);
            result = step(new AdaptiveBridgeMotion(3), scope(), new Object(), limited,
                    AdaptiveBridgeMotion.Task.APPROACH, 0, 0, 0, 135, 83.52);
            assertEquals(20, result.yaw, 0); assertEquals(20, result.pitch, 0);
        }
    }

    @Test public void newSpeedNoiseNeverMovesTheAcquiredFaceAnchor() {
        for (AdaptiveBridgeMotion.Mode mode : AdaptiveBridgeMotion.Mode.values()) {
            AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(93);
            AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
            AdaptiveBridgeMotion.Settings settings = settings(mode, AdaptiveBridgeMotion.NoiseProfile.LIVELY, 35, 180, 1);
            for (int tick = 0; tick < 120; tick++) {
                AdaptiveBridgeMotion.Point point = motion.point(scope, key, settings, tick, tick * STEP,
                        AdaptiveBridgePlanner.Face.SOUTH, 0, 63, -1, 1, 64, 0, .995, 63.8, 0, true);
                assertNotNull(point); assertEquals(.995, point.x, 0);
                assertEquals(63.8, point.y, 0); assertEquals(0, point.z, 0);
            }
        }
    }

    @Test public void invalidRangeInputsAreRejectedBeforeTheyCanAffectMotion() {
        for (double invalid : new double[]{0, -1, 181, Double.NaN, Double.POSITIVE_INFINITY}) {
            try { settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, AdaptiveBridgeMotion.NoiseProfile.NATURAL, invalid, 40, .2); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        for (double invalid : new double[]{0, .24, 3.01, Double.NaN}) {
            try { AdaptiveBridgeMotion.Settings.aimRange(AdaptiveBridgeMotion.Mode.ADAPTIVE,
                    AdaptiveBridgeMotion.NoiseProfile.NATURAL, 10, 40, .2, invalid); fail(); }
            catch (IllegalArgumentException expected) { }
        }
    }
}
