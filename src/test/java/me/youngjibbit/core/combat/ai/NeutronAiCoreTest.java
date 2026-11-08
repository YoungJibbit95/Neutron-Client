package me.youngjibbit.core.combat.ai;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;

/** Pure JUnit 4 motion, model, owner and negative-fixture coverage. */
public class NeutronAiCoreTest {
    private static NeutronAiCore.Acquisition acquisition(long epoch, long generation) {
        return new NeutronAiCore.Acquisition(new Object(), new Object(), new Object(),
                new Object(), new Object(), epoch, generation);
    }

    private static NeutronAiCheckpoint model(float yaw, float pitch) throws Exception {
        NeutronAiCheckpoint parsed = NeutronAiCheckpoint.decode(
                NeutronAiCheckpointTest.fixture(yaw, pitch));
        assertNotNull(parsed);
        return parsed;
    }

    private static NeutronAiCore.Candidate run(NeutronAiCore c,
            NeutronAiCore.Acquisition a, NeutronAiFeatures.Observation o,
            long seq, long nanos) {
        return c.advance(a, o, seq, nanos, true);
    }

    @Test public void missingModelIsHoldAndCannotRearmOldGenerationAfterInstallation() throws Exception {
        NeutronAiCore c = new NeutronAiCore();
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        NeutronAiCore.Acquisition token = acquisition(1, 1);
        for (int t = 0; t < 10; t++)
            assertFalse(run(c, token, o, t, t * 50_000_000L).hasRotation);
        assertEquals(0, c.inferenceCount());
        assertFalse(c.hasModel());
        assertTrue(c.install(model(2, 0)));
        assertTrue(c.hasModel());
        assertFalse("same generation may not resurrect", run(c, token, o, 11,
                550_000_000L).hasRotation);
        NeutronAiCore.Acquisition fresh = acquisition(1, 2);
        assertFalse(run(c, fresh, o, 12, 600_000_000L).hasRotation);
        NeutronAiCore.Candidate moved = run(c, fresh, o, 13, 650_000_000L);
        assertTrue(moved.hasRotation);
        assertTrue(moved.yaw > o.yaw);
        assertEquals(1, c.inferenceCount());
    }

    @Test public void firstSampleHoldsAndEveryDistinctMotionConsumesAtMostOneInference() throws Exception {
        NeutronAiCore core = new NeutronAiCore();
        assertTrue(core.install(model(1.4f, 0)));
        NeutronAiCore.Acquisition key = acquisition(2, 1);
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        assertFalse(run(core, key, o, 100, -10_000_000L).hasRotation);
        assertEquals(0, core.inferenceCount());
        NeutronAiCore.Candidate result = run(core, key, o, 101, 40_000_000L);
        assertTrue(result.hasRotation);
        for (int i = 0; i < 100; i++) {
            NeutronAiCore.Candidate repeat = run(core, key, o, 101,
                    40_000_001L + i * 10L);
            assertSame(result, repeat);
        }
        assertEquals(1, core.inferenceCount());
        assertFalse(run(core, key, o, 99, 60_000_000L).hasRotation);
        assertEquals(1, core.inferenceCount());
    }

    @Test public void abortedOrOccludedFrameMustHoldAndInvalidateAcquisition() throws Exception {
        NeutronAiCore c = new NeutronAiCore();
        c.install(model(2, 0));
        NeutronAiCore.Acquisition key = acquisition(3, 1);
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        run(c, key, o, 0, 0);
        assertTrue(run(c, key, o, 1, 50_000_000L).hasRotation);
        assertFalse(c.advance(key, o, 2, 100_000_000L, false).hasRotation);
        assertFalse(run(c, key, o, 3, 150_000_000L).hasRotation);
        NeutronAiCore.Acquisition next = acquisition(3, 2);
        assertFalse(run(c, next, o, 4, 200_000_000L).hasRotation);
        o.visible = false;
        assertFalse(run(c, next, o, 5, 250_000_000L).hasRotation);
        o.visible = true;
        assertFalse(run(c, next, o, 6, 300_000_000L).hasRotation);
        assertEquals(1, c.inferenceCount());
    }

    @Test public void wrongIdentityCannotStealOrInvalidateSameNumericEpochGeneration() throws Exception {
        NeutronAiCore c = new NeutronAiCore();
        c.install(model(2, 0));
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        NeutronAiCore.Acquisition old = acquisition(4, 12), next = acquisition(4, 13);
        run(c, old, o, 0, 0);
        assertFalse(run(c, acquisition(4, 12), o, 1, 50_000_000L).hasRotation);
        assertTrue(run(c, old, o, 1, 50_000_000L).hasRotation);
        assertFalse(run(c, next, o, 2, 100_000_000L).hasRotation);
        assertFalse(c.invalidateIfCurrent(old));
        assertFalse(run(c, old, o, 3, 150_000_000L).hasRotation);
        assertTrue(run(c, next, o, 3, 150_000_000L).hasRotation);
        assertFalse(c.invalidateIfCurrent(old));
        assertTrue(c.invalidateIfCurrent(next));
        assertFalse(run(c, next, o, 4, 200_000_000L).hasRotation);
    }

    @Test public void replacingTargetInSamePhysicalSequenceCannotSpendSecondBudget() throws Exception {
        NeutronAiCore c = new NeutronAiCore();
        c.install(model(2, 0));
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        NeutronAiCore.Acquisition first = acquisition(5, 10);
        run(c, first, o, 0, 0);
        NeutronAiCore.Candidate move = run(c, first, o, 1, 50_000_000L);
        assertTrue(move.hasRotation);
        long count = c.inferenceCount();
        NeutronAiCore.Acquisition second = acquisition(5, 11);
        assertFalse(run(c, second, o, 1, 50_000_001L).hasRotation);
        assertEquals(count, c.inferenceCount());
        assertTrue(run(c, second, o, 2, 100_000_000L).hasRotation);
        assertEquals(count + 1, c.inferenceCount());
    }

    @Test public void backwardClockPauseSpikeAndOldEpochInvalidateWithoutCatchup() throws Exception {
        NeutronAiCore c = new NeutronAiCore();
        c.install(model(2, 0));
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        NeutronAiCore.Acquisition a = acquisition(6, 0);
        run(c, a, o, 0, 0);
        NeutronAiCore.Candidate step = run(c, a, o, 1, 200_000_000L);
        assertTrue(step.hasRotation);
        assertTrue(step.yaw <= NeutronAiCore.MAX_YAW_DPS * .075 + 1e-6);
        assertFalse(run(c, a, o, 2, 100_000_000L).hasRotation);
        assertFalse(run(c, a, o, 3, 150_000_000L).hasRotation);
        NeutronAiCore.Acquisition b = acquisition(7, 0);
        assertFalse(run(c, b, o, 0, 250_000_000L).hasRotation);
        assertFalse(run(c, b, o, 1, 600_000_000L).hasRotation);
        assertFalse(run(c, b, o, 2, 650_000_000L).hasRotation);
        assertFalse(run(c, a, o, 4, 700_000_000L).hasRotation);
    }

    @Test public void rotationIsClampedToPhysicalSpeedAndActualTargetWithoutOvershoot() throws Exception {
        NeutronAiCore c = new NeutronAiCore();
        c.install(model(3, 3));
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        NeutronAiCore.Acquisition key = acquisition(8, 1);
        run(c, key, o, 0, 0);
        double beforeYaw = o.yaw, beforePitch = o.pitch;
        for (int i = 1; i < 120; i++) {
            NeutronAiCore.Candidate next = run(c, key, o, i, i * 50_000_000L);
            double dyaw = NeutronAiFeatures.wrap(next.yaw - beforeYaw);
            double dpitch = next.pitch - beforePitch;
            assertTrue(Double.isFinite(next.yaw) && Double.isFinite(next.pitch));
            assertTrue(next.pitch >= -90 && next.pitch <= 90);
            assertTrue(Math.abs(dyaw) <= NeutronAiCore.MAX_YAW_DPS * .05 + 1e-8);
            assertTrue(Math.abs(dpitch) <= NeutronAiCore.MAX_PITCH_DPS * .05 + 1e-8);
            assertTrue(Math.abs(next.yawVelocity) <= NeutronAiCore.MAX_YAW_DPS + 1e-6);
            assertTrue(Math.abs(next.pitchVelocity) <= NeutronAiCore.MAX_PITCH_DPS + 1e-6);
            assertTrue(Math.abs(NeutronAiFeatures.wrap(
                    NeutronAiFeatures.targetYaw(o) - next.yaw))
                    <= Math.abs(NeutronAiFeatures.wrap(NeutronAiFeatures.targetYaw(o) - beforeYaw)) + 1e-7);
            o.yaw = beforeYaw = next.yaw;
            o.pitch = beforePitch = next.pitch;
        }
        assertEquals(90, o.yaw, 1e-3);
        assertEquals(NeutronAiFeatures.targetPitch(o), o.pitch, 1e-3);
    }

    @Test public void opposingModelCommandCanNeverTurnAwayIntoEmptySpace() throws Exception {
        NeutronAiCore c = new NeutronAiCore();
        c.install(model(2, 0));
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        NeutronAiCore.Acquisition a = acquisition(10, 1);
        run(c, a, o, 0, 0);
        double yaw = 0;
        for (int i = 1; i < 6; i++) {
            NeutronAiCore.Candidate next = run(c, a, o, i, i * 50_000_000L);
            yaw = next.yaw; o.yaw = yaw;
        }
        assertTrue(yaw > 0);
        o.targetX = 3;
        NeutronAiCore.Candidate reverse = run(c, a, o, 6, 300_000_000L);
        assertFalse("positive learned command must not drag away from negative goal",
                reverse.hasRotation);
        assertEquals(yaw, reverse.yaw, 1e-10);
    }

    private static double[] trace(int fps) throws Exception {
        NeutronAiCore c = new NeutronAiCore();
        c.install(model(1.5f, 0));
        NeutronAiCore.Acquisition a = acquisition(11, 1);
        NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        double[] trace = new double[80];
        for (int i = 0; i < trace.length; i++) {
            long nanos = i * 50_000_000L;
            NeutronAiCore.Candidate result = run(c, a, o, i, nanos);
            trace[i] = result.yaw;
            for (int frame = 0; frame < fps / 20; frame++) {
                NeutronAiCore.Candidate reentry = run(c, a, o, i, nanos + frame + 1);
                assertSame(result, reentry);
            }
            o.yaw = result.yaw;
            o.pitch = result.pitch;
        }
        // Settled motion deliberately stops invoking the model.
        assertTrue(c.inferenceCount() > 0 && c.inferenceCount() <= trace.length - 1);
        return trace;
    }

    @Test public void twentySixtyOneFortyFourAndTwoFortyFpsAreBitExact() throws Exception {
        double[] baseline = trace(20);
        for (int fps : new int[]{60, 144, 240}) assertArrayEquals(baseline, trace(fps), 0);
    }

    @Test public void unknownModelOrWrongThreadDoesNotAdvanceState() throws Exception {
        final NeutronAiCore c = new NeutronAiCore();
        c.install(model(2, 0));
        final NeutronAiCore.Acquisition a = acquisition(12, 1);
        final NeutronAiFeatures.Observation o = NeutronAiFeaturesTest.visibleTarget();
        run(c, a, o, 0, 0);
        final AtomicReference<NeutronAiCore.Candidate> response = new AtomicReference<>();
        Thread foreign = new Thread(() -> {
            response.set(c.advance(a, o, 1, 50_000_000L, true));
            c.reset();
            c.install(null);
        }, "ai-test-foreign-thread");
        foreign.start();
        foreign.join();
        assertNotNull(response.get());
        assertFalse(response.get().hasRotation);
        assertTrue(c.hasModel());
        assertEquals(0, c.inferenceCount());
        assertTrue(run(c, a, o, 1, 50_000_000L).hasRotation);
        c.dispose();
        assertFalse(c.hasModel());
        assertFalse(run(c, acquisition(12, 2), o, 2, 100_000_000L).hasRotation);
    }

    @Test public void fixedNetworkAndHistoryHaveHardWorkAndMemoryBounds() {
        assertEquals(160, NeutronAiCheckpoint.INPUTS);
        assertEquals(40, NeutronAiCheckpoint.WIDTH);
        assertEquals(4, NeutronAiCheckpoint.HISTORY);
        assertEquals(16, NeutronAiCheckpoint.HIDDEN);
        assertEquals(2, NeutronAiCheckpoint.OUTPUTS);
        assertEquals(2592, NeutronAiCheckpoint.MULTIPLY_ADDS);
        assertTrue(NeutronAiCheckpoint.SERIALIZED_BYTES <= NeutronAiCheckpoint.MAX_CHECKPOINT_BYTES);
        int workspaceFloats = NeutronAiCheckpoint.INPUTS + NeutronAiCheckpoint.WIDTH
                + NeutronAiCheckpoint.HIDDEN + NeutronAiCheckpoint.OUTPUTS;
        assertTrue(workspaceFloats * 4 < 1024);
    }
}
