package me.youngjibbit.core.diagnostics;

import java.util.Arrays;
import java.util.Objects;

/**
 * Statistics for completed frame intervals, including frame limiting and VSync.
 * This is neither a GPU timer nor CPU render-submission time. For example,
 * {@code fromNanos(new long[] { 10_000_000L, 30_000_000L }, 16_666_667L)}
 * reports 50 average FPS, not the arithmetic mean of the two instantaneous FPS.
 * Instances are immutable; calculation allocates and sorts only when requested.
 */
public final class FrameTimeStatistics {
    public final int frames;
    public final double durationMilliseconds;
    public final double averageFps;
    public final double p50Milliseconds;
    public final double p95Milliseconds;
    public final double p99Milliseconds;
    public final double onePercentLowFps;
    public final int onePercentSampleCount;
    public final long longFrameThresholdNanos;
    public final int longFrames;
    /** Fraction of measured intervals strictly above the stated threshold. */
    public final double longFrameRate;

    private FrameTimeStatistics(long[] sorted, double totalNanos, long threshold, int longFrames) {
        frames = sorted.length;
        durationMilliseconds = totalNanos / 1_000_000.0;
        longFrameThresholdNanos = threshold;
        this.longFrames = longFrames;
        onePercentSampleCount = (int) ((frames + 99L) / 100L);
        if (frames == 0) {
            averageFps = p50Milliseconds = p95Milliseconds = p99Milliseconds =
                    onePercentLowFps = longFrameRate = Double.NaN;
            return;
        }
        averageFps = frames * 1_000_000_000.0 / totalNanos;
        p50Milliseconds = percentile(sorted, 50);
        p95Milliseconds = percentile(sorted, 95);
        p99Milliseconds = percentile(sorted, 99);
        double slowestNanos = 0;
        for (int i = frames - onePercentSampleCount; i < frames; i++) {
            slowestNanos += sorted[i];
        }
        onePercentLowFps = onePercentSampleCount * 1_000_000_000.0 / slowestNanos;
        longFrameRate = longFrames / (double) frames;
    }

    /**
     * Calculate nearest-rank percentiles and 1%-low from positive intervals.
     * The 1%-low denominator is the mean of the slowest {@code ceil(N / 100)}
     * intervals; it is deliberately different from the reciprocal of p99.
     * Empty input has zero frames/duration and NaN metrics, never a passing score.
     * The caller's array is not modified or retained.
     *
     * @throws IllegalArgumentException if the threshold or an interval is not positive
     */
    public static FrameTimeStatistics fromNanos(long[] intervals, long longFrameThresholdNanos) {
        Objects.requireNonNull(intervals, "intervals");
        if (longFrameThresholdNanos <= 0) {
            throw new IllegalArgumentException("Long-frame threshold must be positive");
        }
        long[] sorted = intervals.clone();
        double totalNanos = 0;
        int longFrames = 0;
        for (long interval : sorted) {
            if (interval <= 0) {
                throw new IllegalArgumentException("Frame intervals must be positive");
            }
            // A double sum also avoids signed-long overflow for unusually long captures.
            totalNanos += interval;
            if (interval > longFrameThresholdNanos) longFrames++;
        }
        Arrays.sort(sorted);
        return new FrameTimeStatistics(sorted, totalNanos, longFrameThresholdNanos, longFrames);
    }

    private static double percentile(long[] sorted, int percent) {
        int index = (int) ((sorted.length * (long) percent + 99L) / 100L) - 1;
        return sorted[index] / 1_000_000.0;
    }
}
