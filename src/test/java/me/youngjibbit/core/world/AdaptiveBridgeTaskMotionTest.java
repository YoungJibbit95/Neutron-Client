package me.youngjibbit.core.world;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

/** Task deadlines, continuity and rejection boundaries of the real bridge-only turn planner. */
public class AdaptiveBridgeTaskMotionTest {
    private static final long STEP = 50_000_000L;
    private static AdaptiveBridgePlanner.Scope scope() {
        return new AdaptiveBridgePlanner.Scope(new Object(), new Object(), new Object(), new Object(), 0, 1);
    }
    private static AdaptiveBridgeMotion.Settings settings(AdaptiveBridgeMotion.Speed speed) {
        return new AdaptiveBridgeMotion.Settings(AdaptiveBridgeMotion.Mode.ADAPTIVE, speed, 0, 35, 30, 35, 30);
    }
    private static AdaptiveBridgeMotion.Turn turn(AdaptiveBridgeMotion motion, AdaptiveBridgePlanner.Scope scope,
            Object key, AdaptiveBridgeMotion.Settings settings, AdaptiveBridgeMotion.Task task,
            long sequence, long nanos, double yaw, double pitch, double targetYaw, double targetPitch) {
        return motion.advanceTask(scope, key, settings, task, sequence, nanos,
                yaw, pitch, targetYaw, targetPitch, true);
    }
    private static double wrap(double angle) {
        angle %= 360; if (angle > 180) angle -= 360; if (angle <= -180) angle += 360; return angle;
    }

    @Test public void firstNecessaryMotionProgressesAndUrgencyShortensTheTurn() {
        double previousYaw = -1;
        for (AdaptiveBridgeMotion.Task task : AdaptiveBridgeMotion.Task.values()) {
            AdaptiveBridgeMotion.Turn result = turn(new AdaptiveBridgeMotion(11), scope(), new Object(),
                    settings(AdaptiveBridgeMotion.Speed.BALANCED), task, 0, 0, 0, 0, 150, 80);
            assertNotNull(result);
            assertTrue("a checked PRE task may use its first physical motion", result.candidate);
            assertTrue(task.name(), result.yaw > previousYaw);
            assertTrue(result.pitch > 0 && result.pitch <= 80);
            previousYaw = result.yaw;
        }
        assertEquals("a late reachable catch has no decorative startup delay", 150, previousYaw, 0);
    }

    @Test public void everyPresetCanAcquireAReachableClutchInOneNominalMotion() {
        for (AdaptiveBridgeMotion.Mode mode : AdaptiveBridgeMotion.Mode.values())
            for (AdaptiveBridgeMotion.Speed speed : new AdaptiveBridgeMotion.Speed[]{
                    AdaptiveBridgeMotion.Speed.CALM, AdaptiveBridgeMotion.Speed.BALANCED, AdaptiveBridgeMotion.Speed.SWIFT}) {
                AdaptiveBridgeMotion.Settings p = new AdaptiveBridgeMotion.Settings(mode, speed, 1, 35, 35, 35, 35);
                AdaptiveBridgeMotion.Turn result = turn(new AdaptiveBridgeMotion(7), scope(), new Object(), p,
                        AdaptiveBridgeMotion.Task.CLUTCH, 1, 0, -90, 0, 90, 83);
                assertEquals(0, wrap(90 - result.yaw), 1e-9);
                assertEquals(83, result.pitch, 1e-9);
                assertTrue(Math.abs(result.yawVelocity) <= p.yawSpeed(AdaptiveBridgeMotion.Task.CLUTCH));
                assertTrue(Math.abs(result.pitchVelocity) <= p.pitchSpeed(AdaptiveBridgeMotion.Task.CLUTCH));
            }
    }

    @Test public void customEmergencyMotionHonoursExplicitSliderLimits() {
        AdaptiveBridgeMotion.Settings p = new AdaptiveBridgeMotion.Settings(AdaptiveBridgeMotion.Mode.ADAPTIVE,
                AdaptiveBridgeMotion.Speed.CUSTOM, 0, 4, 3, 2, 2);
        for (AdaptiveBridgeMotion.Task task : new AdaptiveBridgeMotion.Task[]{
                AdaptiveBridgeMotion.Task.EDGE, AdaptiveBridgeMotion.Task.CLUTCH}) {
            AdaptiveBridgeMotion.Turn result = turn(new AdaptiveBridgeMotion(5), scope(), new Object(), p,
                    task, 0, 0, 0, 0, 150, 80);
            assertEquals(4, result.yaw, 1e-9);
            assertEquals(3, result.pitch, 1e-9);
            assertEquals(p.maxYawSpeed, p.yawSpeed(task), 0);
            assertEquals(p.maxPitchSpeed, p.pitchSpeed(task), 0);
        }
    }

    @Test public void smallErrorsAndYawWrapConvergeWithoutOvershootOrOscillation() {
        for (AdaptiveBridgeMotion.Task task : AdaptiveBridgeMotion.Task.values())
            for (double targetYaw : new double[]{-179, 170, .001}) {
                AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(99);
                AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
                double yaw = targetYaw == .001 ? 0 : 179, pitch = -20;
                for (int tick = 0; tick < 100; tick++) {
                    double yawError = wrap(targetYaw - yaw), pitchError = 70 - pitch;
                    AdaptiveBridgeMotion.Turn result = turn(motion, scope, key,
                            settings(AdaptiveBridgeMotion.Speed.BALANCED), task, tick, tick * STEP,
                            yaw, pitch, targetYaw, 70);
                    double yawStep = wrap(result.yaw - yaw), pitchStep = result.pitch - pitch;
                    assertTrue(yawStep * yawError >= -1e-10);
                    assertTrue(pitchStep * pitchError >= -1e-10);
                    assertTrue(Math.abs(yawStep) <= Math.abs(yawError) + 1e-10);
                    assertTrue(Math.abs(pitchStep) <= Math.abs(pitchError) + 1e-10);
                    yaw = result.yaw; pitch = result.pitch;
                }
                assertEquals(0, wrap(targetYaw - yaw), 1e-8);
                assertEquals(70, pitch, 1e-8);
            }
    }

    @Test public void faceChangesKeepElapsedBudgetWithoutAnotherHoldOrFullTick() {
        AdaptiveBridgeMotion changing = new AdaptiveBridgeMotion(1), stable = new AdaptiveBridgeMotion(1);
        AdaptiveBridgePlanner.Scope a = scope(), b = scope(); Object stableKey = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Speed.BALANCED);
        double yaw = 0, pitch = 0;
        for (int tick = 0; tick < 20; tick++) {
            long nanos = tick * 16_000_000L;
            double targetYaw = 100 + tick * .1, targetPitch = 65 + tick * .05;
            AdaptiveBridgeMotion.Turn actual = turn(changing, a, new Object(), p, AdaptiveBridgeMotion.Task.APPROACH,
                    tick, nanos, yaw, pitch, targetYaw, targetPitch);
            AdaptiveBridgeMotion.Turn expected = turn(stable, b, stableKey, p, AdaptiveBridgeMotion.Task.APPROACH,
                    tick, nanos, yaw, pitch, targetYaw, targetPitch);
            assertNotNull(actual); assertTrue(actual.candidate);
            assertEquals(expected.yaw, actual.yaw, 0); assertEquals(expected.pitch, actual.pitch, 0);
            if (tick > 0) assertTrue(Math.abs(wrap(actual.yaw - yaw)) <= p.maxYawSpeed * .016 + 1e-9);
            yaw = actual.yaw; pitch = actual.pitch;
        }
    }

    @Test public void pointPreviewDoesNotConsumeOrAccumulateTheFirstTaskBudget() {
        AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(13);
        AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Speed.BALANCED);
        for (int tick = 0; tick < 20; tick++)
            assertNotNull(motion.point(scope, key, p, tick, tick * STEP, AdaptiveBridgePlanner.Face.SOUTH,
                    0, 63, -1, 1, 64, 0, .5, 63.5, 0, true));
        AdaptiveBridgeMotion.Turn result = turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.APPROACH,
                20, 20 * STEP, 0, 0, 150, 80);
        assertTrue(result.candidate);
        assertTrue(result.yaw <= p.maxYawSpeed * .05);
        assertTrue(result.pitch <= p.maxPitchSpeed * .05);
    }

    @Test public void duplicatesCannotUpgradeTaskOrBuyTimeAndRejectionRevokesTheMotion() {
        AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(7);
        AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Speed.BALANCED);
        AdaptiveBridgeMotion.Turn first = turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.TRACKING,
                4, 0, 0, 0, 90, 70);
        assertSame(first, turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.CLUTCH,
                4, Long.MAX_VALUE, 0, 0, -90, -70));
        motion.rejectMotion(scope, key, 4);
        assertNull(turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.CLUTCH, 4, STEP, 0, 0, 90, 70));
        assertNotNull(turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.EDGE, 5, STEP, 0, 0, 90, 70));
        assertTrue(motion.reset(scope));
        assertNull(turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.CLUTCH, 6, 2 * STEP, 0, 0, 90, 70));
    }

    @Test public void faceSwitchCannotHideClockRollbackOrAStall() {
        for (long invalid : new long[]{-1, 250_000_000L}) {
            AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(7);
            AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object(), next = new Object();
            AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Speed.BALANCED);
            assertNotNull(turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.EDGE, 0, 0, 0, 0, 90, 70));
            assertNull(turn(motion, scope, next, p, AdaptiveBridgeMotion.Task.CLUTCH, 1, invalid, 0, 0, 90, 70));
            assertNull(turn(motion, scope, next, p, AdaptiveBridgeMotion.Task.CLUTCH, 1, STEP, 0, 0, 90, 70));
            assertNotNull(turn(motion, scope, next, p, AdaptiveBridgeMotion.Task.CLUTCH, 2, 500_000_000L, 0, 0, 90, 70));
        }
    }

    @Test public void taskAfterIdlePhysicalMotionsGetsOneNominalBudgetWithoutAccumulatedCredit() {
        for (boolean newFace : new boolean[]{false, true}) {
            AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(7);
            AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
            AdaptiveBridgeMotion.Settings p = new AdaptiveBridgeMotion.Settings(AdaptiveBridgeMotion.Mode.ADAPTIVE,
                    AdaptiveBridgeMotion.Speed.CUSTOM, 0, 4, 3, 2, 2);
            AdaptiveBridgeMotion.Turn first = turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.EDGE,
                    6, 6 * STEP, 0, 0, 150, 80);
            for (long sequence : new long[]{11, 111}) {
                if (newFace) key = new Object();
                AdaptiveBridgeMotion.Turn resumed = turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.CLUTCH,
                        sequence, sequence * STEP, first.yaw, first.pitch, 150, 80);
                assertNotNull("ordinary target-free physical motions are not a stalled task", resumed);
                assertEquals("idle time cannot buy a larger yaw budget", 4, wrap(resumed.yaw - first.yaw), 1e-9);
                assertEquals("idle time cannot buy a larger pitch budget", 3, resumed.pitch - first.pitch, 1e-9);
                assertSame(resumed, turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.CLUTCH,
                        sequence, Long.MAX_VALUE, first.yaw, first.pitch, -150, -80));
                first = resumed;
            }
            AdaptiveBridgeMotion.Turn next = turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.CLUTCH,
                    112, 111 * STEP + 16_000_000L, first.yaw, first.pitch, 150, 80);
            assertEquals("continuous tasks return to the actual elapsed budget", 4 * .016 / .05,
                    wrap(next.yaw - first.yaw), 1e-9);
            assertEquals(3 * .016 / .05, next.pitch - first.pitch, 1e-9);
        }
    }

    @Test public void idlePhysicalMotionsDoNotHideClockRollback() {
        for (boolean newFace : new boolean[]{false, true}) {
            AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(7);
            AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
            AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Speed.BALANCED);
            assertNotNull(turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.EDGE, 6, 6 * STEP, 0, 0, 90, 70));
            if (newFace) key = new Object();
            assertNull(turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.CLUTCH, 11, 6 * STEP - 1, 0, 0, 90, 70));
            assertNull("rollback revokes that motion even after its clock is corrected",
                    turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.CLUTCH, 11, 11 * STEP, 0, 0, 90, 70));
        }
    }

    @Test public void settledAimStaysExactAndOppositeMovingTargetCannotKeepWrongTurnMomentum() {
        AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(9);
        AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Speed.BALANCED);
        AdaptiveBridgeMotion.Turn first = turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.APPROACH,
                0, 0, 0, 0, 100, 70);
        AdaptiveBridgeMotion.Turn reversed = turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.APPROACH,
                1, STEP, first.yaw, first.pitch, -100, -70);
        assertTrue(reversed.yaw < first.yaw); assertTrue(reversed.pitch < first.pitch);
        for (int tick = 2; tick < 20; tick++) {
            AdaptiveBridgeMotion.Turn settled = turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.TRACKING,
                    tick, tick * STEP, 25, 63, 25, 63);
            assertFalse(settled.candidate); assertEquals(25, settled.yaw, 0); assertEquals(63, settled.pitch, 0);
        }
    }

    @Test public void staleScopesAndForeignThreadsCannotEraseAnActiveTask() throws Exception {
        AdaptiveBridgeMotion motion = new AdaptiveBridgeMotion(9);
        AdaptiveBridgePlanner.Scope scope = scope(); Object key = new Object();
        AdaptiveBridgePlanner.Scope next = new AdaptiveBridgePlanner.Scope(scope.owner, scope.world,
                scope.player, new Object(), 0, 2);
        AdaptiveBridgeMotion.Settings p = settings(AdaptiveBridgeMotion.Speed.BALANCED);
        turn(motion, scope, key, p, AdaptiveBridgeMotion.Task.APPROACH, 1, 0, 0, 0, 100, 70);
        AdaptiveBridgeMotion.Turn active = turn(motion, next, key, p, AdaptiveBridgeMotion.Task.APPROACH,
                1, 0, 0, 0, 100, 70);
        motion.rejectMotion(scope, key, 1); assertFalse(motion.reset(scope));
        AtomicReference<AdaptiveBridgeMotion.Turn> foreign = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            foreign.set(turn(motion, next, key, p, AdaptiveBridgeMotion.Task.CLUTCH, 2, STEP, 0, 0, 100, 70));
            motion.reset();
        });
        thread.start(); thread.join(); assertNull(foreign.get());
        assertSame(active, turn(motion, next, key, p, AdaptiveBridgeMotion.Task.CLUTCH, 1, STEP, 0, 0, 100, 70));
        assertNotNull(turn(motion, next, key, p, AdaptiveBridgeMotion.Task.APPROACH, 2, STEP, 0, 0, 100, 70));
    }
}
