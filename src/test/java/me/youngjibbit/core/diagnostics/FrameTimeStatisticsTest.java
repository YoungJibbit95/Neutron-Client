package me.youngjibbit.core.diagnostics;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

public class FrameTimeStatisticsTest {
    @Test public void averageFpsUsesElapsedTimeAndNearestRankHasAnExplicitDefinition() {
        long[] intervals = {30_000_000L, 10_000_000L, 20_000_000L};
        FrameTimeStatistics result = FrameTimeStatistics.fromNanos(intervals, 20_000_000L);
        assertEquals(3, result.frames);
        assertEquals(60, result.durationMilliseconds, 0);
        assertEquals(50, result.averageFps, 0);
        assertEquals(20, result.p50Milliseconds, 0);
        assertEquals(30, result.p95Milliseconds, 0);
        assertEquals(30, result.p99Milliseconds, 0);
        assertArrayEquals(new long[] {30_000_000L, 10_000_000L, 20_000_000L}, intervals);
    }

    @Test public void onePercentLowUsesSlowestMeanAndIsNotReciprocalP99() {
        long[] intervals = new long[200];
        Arrays.fill(intervals, 1_000_000L);
        intervals[0] = 90_000_000L;
        intervals[1] = 10_000_000L;
        FrameTimeStatistics result = FrameTimeStatistics.fromNanos(intervals, 10_000_000L);
        assertEquals(2, result.onePercentSampleCount);
        assertEquals(20, result.onePercentLowFps, 0);
        assertEquals(1, result.p99Milliseconds, 0);
        assertNotEquals(1000 / result.p99Milliseconds, result.onePercentLowFps, 0);
        assertEquals(1, result.longFrames);
        assertEquals(0.005, result.longFrameRate, 0);
        assertEquals(200_000.0 / 298, result.averageFps, 1e-10);
    }

    @Test public void fractionalOnePercentUsesCeilingAndSmallSamplesUseTheirSlowestFrame() {
        long[] intervals = new long[101];
        Arrays.fill(intervals, 1_000_000L);
        intervals[100] = 9_000_000L;
        FrameTimeStatistics result = FrameTimeStatistics.fromNanos(intervals, 5_000_000L);
        assertEquals(2, result.onePercentSampleCount);
        assertEquals(200, result.onePercentLowFps, 0);
        FrameTimeStatistics single = FrameTimeStatistics.fromNanos(new long[] {9_000_000L}, 9_000_000L);
        assertEquals(1, single.onePercentSampleCount);
        assertEquals(1000.0 / 9, single.onePercentLowFps, 1e-10);
        assertEquals(0, single.longFrames);
    }

    @Test public void emptyInputIsUnmeasuredAndLargeDurationsDoNotOverflowSignedLong() {
        FrameTimeStatistics empty = FrameTimeStatistics.fromNanos(new long[0], 1);
        assertEquals(0, empty.frames);
        assertEquals(0, empty.durationMilliseconds, 0);
        assertTrue(Double.isNaN(empty.averageFps));
        assertTrue(Double.isNaN(empty.p99Milliseconds));
        assertTrue(Double.isNaN(empty.onePercentLowFps));
        assertTrue(Double.isNaN(empty.longFrameRate));
        FrameTimeStatistics large = FrameTimeStatistics.fromNanos(new long[] {Long.MAX_VALUE, Long.MAX_VALUE}, 1);
        assertTrue(Double.isFinite(large.durationMilliseconds));
        assertTrue(large.averageFps > 0);
        assertEquals(1_000_000_000.0 / Long.MAX_VALUE, large.averageFps, 1e-25);
    }

    @Test public void invalidIntervalsAndThresholdsAreRejectedRatherThanHidden() {
        expectInvalid(new long[] {0}, 1);
        expectInvalid(new long[] {-1}, 1);
        expectInvalid(new long[] {1}, 0);
        expectInvalid(new long[] {1}, -1);
    }

    private static void expectInvalid(long[] intervals, long threshold) {
        try {
            FrameTimeStatistics.fromNanos(intervals, threshold);
            fail("Invalid measurement must not produce statistics");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
