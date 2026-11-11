package me.youngjibbit.core.diagnostics;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Opt-in bounded capture of consecutive completed game-loop intervals.
 * Start, stop, abort, reset and snapshot belong to the thread that starts capture;
 * {@link #frame()} ignores other threads. The frame hook performs no allocation,
 * I/O, GL call or GPU wait. Disabled capture does not read the clock.
 *
 * <pre>
 * capture.start(131072, 50_000_000L, 120_000_000_000L);
 * // At the same boundary after every completed game loop:
 * capture.frame();
 * // On an explicit export action, after the capture stops:
 * capture.snapshot().writeCsv(writer);
 * </pre>
 */
public final class FrameTimeCapture {
    public static final int MAX_SAMPLES = 1_000_000;
    public static final int DEFAULT_MAX_SAMPLES = 131072;
    public static final long DEFAULT_LONG_FRAME_NANOS = 50_000_000L;
    public static final FrameTimeCapture INSTANCE = new FrameTimeCapture(System::nanoTime);

    public enum State {
        IDLE, RECORDING, STOPPED, DURATION_REACHED, CAPACITY_REACHED, ABORTED, CLOCK_DISCONTINUITY
    }

    private static final long[] EMPTY = new long[0];
    private final LongSupplier clock;
    private volatile Thread owner;
    private volatile boolean recording;
    private State state = State.IDLE;
    private String reason = "";
    private long[] intervals = EMPTY;
    private int count, rejectedIntervals;
    private long thresholdNanos = DEFAULT_LONG_FRAME_NANOS, durationNanos, previousBoundary;
    private double recordedNanos;
    private boolean hasBoundary;

    /** Uses an injectable monotonic clock; construction does not read it. */
    public FrameTimeCapture(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Start a manually stopped capture; the first frame hook establishes its boundary. */
    public void start(int maxSamples, long longFrameThresholdNanos) {
        start(maxSamples, longFrameThresholdNanos, 0);
    }

    /**
     * Begin a fresh capture without reading the clock or including the partial start frame.
     * A positive duration stops after the first complete interval reaching that duration;
     * zero requests manual stop. Reaching capacity stops rather than overwriting evidence.
     * Starting a new capture intentionally replaces the previous capture.
     */
    public void start(int maxSamples, long longFrameThresholdNanos, long requestedDurationNanos) {
        requireOwner();
        if (maxSamples < 1 || maxSamples > MAX_SAMPLES) {
            throw new IllegalArgumentException("Capture capacity must be between 1 and " + MAX_SAMPLES);
        }
        if (longFrameThresholdNanos <= 0 || requestedDurationNanos < 0) {
            throw new IllegalArgumentException("Threshold must be positive and duration non-negative");
        }
        long[] storage = new long[maxSamples];
        recording = false;
        owner = Thread.currentThread();
        intervals = storage;
        thresholdNanos = longFrameThresholdNanos;
        durationNanos = requestedDurationNanos;
        count = rejectedIntervals = 0;
        recordedNanos = 0;
        hasBoundary = false;
        reason = "";
        state = State.RECORDING;
        recording = true;
    }

    /**
     * Observe one completed frame at a consistent game-loop boundary. Never call this
     * separately for HUD draws, shadow passes or additional scene rendering passes.
     */
    public void frame() {
        if (!recording || Thread.currentThread() != owner) return;
        long now = clock.getAsLong();
        if (!hasBoundary) {
            previousBoundary = now;
            hasBoundary = true;
            return;
        }
        long elapsed = now - previousBoundary;
        if (elapsed <= 0) {
            rejectedIntervals++;
            finish(State.CLOCK_DISCONTINUITY, "Non-positive frame interval; capture is incomplete");
            return;
        }
        previousBoundary = now;
        intervals[count++] = elapsed;
        recordedNanos += elapsed;
        if (durationNanos > 0 && recordedNanos >= durationNanos) {
            finish(State.DURATION_REACHED, "Requested duration reached at a complete frame boundary");
        } else if (count == intervals.length) {
            finish(State.CAPACITY_REACHED, "Sample capacity reached; no samples were overwritten");
        }
    }

    /** Stop without adding the partly rendered interval or reading the clock. */
    public void stop() {
        requireOwner();
        if (recording) finish(State.STOPPED, "Stopped explicitly at the last complete frame boundary");
    }

    /**
     * End a running capture on a context/lifecycle change while retaining completed
     * intervals and the reason. Does not replace the result of an already ended capture.
     */
    public void abort(String reason) {
        requireOwner();
        Objects.requireNonNull(reason, "reason");
        if (recording) finish(State.ABORTED, reason);
    }

    /** Explicitly discard captured evidence and release its backing storage. */
    public void reset() {
        requireOwner();
        recording = false;
        state = State.IDLE;
        reason = "";
        intervals = EMPTY;
        count = rejectedIntervals = 0;
        recordedNanos = 0;
        durationNanos = 0;
        thresholdNanos = DEFAULT_LONG_FRAME_NANOS;
        hasBoundary = false;
        owner = null;
    }

    /** Cheap status usable by the render hook without generating a snapshot. */
    public boolean isRecording() {
        return recording;
    }

    /**
     * Copy completed intervals and calculate statistics on explicit request only.
     * The immutable result can subsequently be exported on an I/O worker.
     * Snapshotting does not stop, clear or split the current frame.
     */
    public Snapshot snapshot() {
        requireOwner();
        return new Snapshot(state, reason, intervals.length, thresholdNanos, durationNanos,
                rejectedIntervals, Arrays.copyOf(intervals, count));
    }

    private void requireOwner() {
        if (owner != null && Thread.currentThread() != owner) {
            throw new IllegalStateException("Capture lifecycle belongs to its starting thread");
        }
    }

    private void finish(State terminalState, String terminalReason) {
        state = terminalState;
        reason = terminalReason;
        recording = false;
    }

    /** Immutable evidence for one explicit snapshot, with ordered raw intervals. */
    public static final class Snapshot {
        public final State state;
        public final String reason;
        public final int capacity;
        public final long longFrameThresholdNanos;
        public final long requestedDurationNanos;
        public final int rejectedIntervals;
        public final FrameTimeStatistics statistics;
        private final long[] intervals;

        private Snapshot(State state, String reason, int capacity, long threshold, long duration,
                         int rejectedIntervals, long[] intervals) {
            this.state = state;
            this.reason = reason;
            this.capacity = capacity;
            longFrameThresholdNanos = threshold;
            requestedDurationNanos = duration;
            this.rejectedIntervals = rejectedIntervals;
            this.intervals = intervals;
            statistics = FrameTimeStatistics.fromNanos(intervals, threshold);
        }

        /** Return an owned copy in capture order; no warmup or outlier is silently removed. */
        public long[] frameIntervalsNanos() {
            return intervals.clone();
        }

        /**
         * Write ordered raw data using locale-independent decimals and a zero-based index.
         * The caller owns/closes the writer and supplies separate hardware/settings/commit
         * provenance. No export happens automatically from the game-loop hook.
         */
        public void writeCsv(Writer writer) throws IOException {
            Objects.requireNonNull(writer, "writer");
            writer.write("frame_index,interval_ns,interval_ms,long_frame\n");
            for (int i = 0; i < intervals.length; i++) {
                writer.write(Integer.toString(i));
                writer.write(',');
                writer.write(Long.toString(intervals[i]));
                writer.write(',');
                writer.write(Double.toString(intervals[i] / 1_000_000.0));
                writer.write(',');
                writer.write(intervals[i] > longFrameThresholdNanos ? "1\n" : "0\n");
            }
        }
    }
}
