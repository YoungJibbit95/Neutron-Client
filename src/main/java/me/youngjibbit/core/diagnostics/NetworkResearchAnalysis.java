package me.youngjibbit.core.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Entry;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Kind;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure analysis of recorded scalars. No game classes, clocks, callbacks or transport access. */
public final class NetworkResearchAnalysis {
    private static final long TICK_REFERENCE_NANOS = 50_000_000L;
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();

    private NetworkResearchAnalysis() { }

    public enum Issue {
        EMPTY_RECORDING, OVERWRITTEN_PREFIX, REJECTED_OBSERVATIONS, CAPTURE_FAILURES,
        SIMULATION_REJECTIONS, CLOCK_REGRESSION, DURATION_OVERFLOW, UNPAIRED_TICK_INTERVAL,
        TICK_INTERVAL_MISMATCH, INVALID_TICK_DETAIL, INVALID_QUEUE_DETAIL,
        DERIVED_COORDINATE_OVERFLOW
    }

    public static Report analyze(NetworkResearchRecording recording) {
        Objects.requireNonNull(recording, "recording");
        EnumSet<Issue> issues = EnumSet.noneOf(Issue.class);
        EnumMap<Kind, Integer> counts = new EnumMap<>(Kind.class);
        for (Kind kind : Kind.values()) counts.put(kind, 0);
        if (recording.entries.isEmpty()) issues.add(Issue.EMPTY_RECORDING);
        if (recording.overwritten != 0) issues.add(Issue.OVERWRITTEN_PREFIX);
        if (recording.rejected != 0) issues.add(Issue.REJECTED_OBSERVATIONS);
        if (recording.captureFailures != 0) issues.add(Issue.CAPTURE_FAILURES);
        if (recording.simulationRejected != 0) issues.add(Issue.SIMULATION_REJECTIONS);

        List<Long> intervals = new ArrayList<>();
        Long previousNanos = null, previousTick = null;
        long span = 0, maxOldestAgeMs = 0;
        int baselineTicks = 0, unknownStepTicks = 0, unpairedIntervals = 0;
        int maxInbound = 0, maxOutbound = 0, queueSamples = 0, offsetSamples = 0;
        Double maxOffset = null;
        boolean validClock = true;

        for (Entry entry : recording.entries) {
            counts.put(entry.kind, counts.get(entry.kind) + 1);
            if (previousNanos != null) {
                // Subtraction is intentional: nanoTime's signed wrap is valid for intervals < 2^63 ns.
                long elapsed = entry.nanos - previousNanos;
                if (elapsed < 0) {
                    issues.add(Issue.CLOCK_REGRESSION);
                    validClock = false;
                    previousTick = null;
                } else if (validClock) {
                    try {
                        span = Math.addExact(span, elapsed);
                    } catch (ArithmeticException overflow) {
                        issues.add(Issue.DURATION_OVERFLOW);
                        validClock = false;
                    }
                }
            }
            previousNanos = entry.nanos;

            if (entry.kind == Kind.TICK) {
                TickDetail detail = TickDetail.parse(entry.detail);
                if (detail == null) {
                    issues.add(Issue.INVALID_TICK_DETAIL);
                    previousTick = null;
                    continue;
                }
                if (detail.steps == -1) unknownStepTicks++;
                if (detail.baseline) {
                    baselineTicks++;
                    if (entry.intervalNanos != 0 || entry.deviationNanos != 0) {
                        issues.add(Issue.TICK_INTERVAL_MISMATCH);
                    }
                } else if (previousTick == null) {
                    unpairedIntervals++;
                    issues.add(Issue.UNPAIRED_TICK_INTERVAL);
                } else {
                    long actualInterval = entry.nanos - previousTick;
                    if (actualInterval < 0 || entry.intervalNanos != actualInterval
                            || entry.deviationNanos != actualInterval - TICK_REFERENCE_NANOS) {
                        issues.add(Issue.TICK_INTERVAL_MISMATCH);
                    } else {
                        intervals.add(actualInterval);
                    }
                }
                previousTick = entry.nanos;
            } else if (entry.kind == Kind.QUEUE) {
                QueueDetail detail = QueueDetail.parse(entry.detail);
                if (detail == null) {
                    issues.add(Issue.INVALID_QUEUE_DETAIL);
                } else {
                    queueSamples++;
                    maxInbound = Math.max(maxInbound, detail.inbound);
                    maxOutbound = Math.max(maxOutbound, detail.outbound);
                    maxOldestAgeMs = Math.max(maxOldestAgeMs, detail.oldestAgeMs);
                }
            } else if (entry.kind == Kind.ENTITY) {
                double offset = Math.hypot(Math.hypot(entry.x - entry.renderedX,
                        entry.y - entry.renderedY), entry.z - entry.renderedZ);
                if (Double.isFinite(offset)) {
                    maxOffset = maxOffset == null ? offset : Math.max(maxOffset, offset);
                    offsetSamples++;
                } else {
                    issues.add(Issue.DERIVED_COORDINATE_OVERFLOW);
                }
            }
        }

        return new Report(recording, counts, issues,
                validClock && recording.entries.size() >= 2 ? span : null,
                new Distribution(intervals), baselineTicks, unknownStepTicks, unpairedIntervals,
                queueSamples, maxInbound, maxOutbound, maxOldestAgeMs, offsetSamples, maxOffset);
    }

    /** Nearest-rank percentiles of adjacent, retained tick observations; no inferred server ticks. */
    public static final class Distribution {
        public final int samples;
        public final Long minNanos, p50Nanos, p95Nanos, maxNanos;
        public final Double meanNanos;

        private Distribution(List<Long> values) {
            samples = values.size();
            if (values.isEmpty()) {
                minNanos = p50Nanos = p95Nanos = maxNanos = null;
                meanNanos = null;
                return;
            }
            Collections.sort(values);
            minNanos = values.get(0);
            maxNanos = values.get(samples - 1);
            p50Nanos = values.get((int) Math.ceil(samples * 0.50) - 1);
            p95Nanos = values.get((int) Math.ceil(samples * 0.95) - 1);
            // A double accumulator avoids overflowing a long when imported intervals are very large.
            double total = 0;
            for (Long value : values) total += value;
            meanNanos = total / samples;
        }
    }

    public static final class Report {
        public final int schemaVersion = 1;
        public final String format = "neutron-network-research-analysis";
        public final long run;
        public final NetworkResearchTrace.Mode mode;
        public final NetworkResearchTrace.Origin origin;
        public final String scenario;
        public final int retainedSamples;
        public final long appended, overwritten, rejected, captureFailures, simulationRejected;
        public final Map<Kind, Integer> counts;
        public final List<Issue> issues;
        public final Long retainedSpanNanos;
        public final Distribution tickIntervals;
        public final int baselineTicks, unknownStepTicks, unpairedTickIntervals;
        public final int queueSamples, maximumInboundQueue, maximumOutboundQueue;
        public final long maximumOldestQueueAgeMs;
        public final int renderedOffsetSamples;
        public final Double maximumRenderedPositionOffset;
        public final String serverOutcome = "UNKNOWN";
        public final String serverEvidence;

        private Report(NetworkResearchRecording recording, EnumMap<Kind, Integer> counts,
                       EnumSet<Issue> issues, Long span, Distribution intervals, int baselineTicks,
                       int unknownStepTicks, int unpairedIntervals, int queueSamples, int maxInbound,
                       int maxOutbound, long maxOldestAgeMs, int offsetSamples, Double maxOffset) {
            run = recording.run;
            mode = recording.mode;
            origin = recording.origin;
            scenario = recording.scenario;
            retainedSamples = recording.entries.size();
            appended = recording.appended;
            overwritten = recording.overwritten;
            rejected = recording.rejected;
            captureFailures = recording.captureFailures;
            simulationRejected = recording.simulationRejected;
            this.counts = Collections.unmodifiableMap(new EnumMap<>(counts));
            this.issues = Collections.unmodifiableList(new ArrayList<>(issues));
            retainedSpanNanos = span;
            tickIntervals = intervals;
            this.baselineTicks = baselineTicks;
            this.unknownStepTicks = unknownStepTicks;
            unpairedTickIntervals = unpairedIntervals;
            this.queueSamples = queueSamples;
            maximumInboundQueue = maxInbound;
            maximumOutboundQueue = maxOutbound;
            maximumOldestQueueAgeMs = maxOldestAgeMs;
            renderedOffsetSamples = offsetSamples;
            maximumRenderedPositionOffset = maxOffset;
            serverEvidence = counts.get(Kind.SERVER_FLAG) == 0 ? "UNKNOWN" : "REFERENCES_PRESENT_UNVERIFIED";
        }

        public void write(Writer destination) throws IOException {
            destination.write(JSON.toJson(this));
            destination.write('\n');
        }
    }

    private static String[] fields(String detail, String first, String second, String third) {
        String[] parts = detail.split(";", -1);
        if (parts.length != 3 || !parts[0].startsWith(first + "=")
                || !parts[1].startsWith(second + "=") || !parts[2].startsWith(third + "=")) return null;
        return new String[]{parts[0].substring(first.length() + 1),
                parts[1].substring(second.length() + 1), parts[2].substring(third.length() + 1)};
    }

    private static long integer(String value) {
        if (!value.matches("-?(0|[1-9][0-9]*)")) throw new NumberFormatException("integer required");
        return Long.parseLong(value);
    }

    private static final class TickDetail {
        final long steps;
        final boolean baseline;

        private TickDetail(long steps, boolean baseline) { this.steps = steps; this.baseline = baseline; }

        static TickDetail parse(String detail) {
            String[] fields = fields(detail, "steps", "withheld", "baseline");
            if (fields == null || !(fields[1].equals("true") || fields[1].equals("false"))
                    || !(fields[2].equals("true") || fields[2].equals("false"))) return null;
            try {
                long steps = integer(fields[0]);
                return steps < -1 || steps > Integer.MAX_VALUE ? null : new TickDetail(steps, fields[2].equals("true"));
            } catch (NumberFormatException invalid) { return null; }
        }
    }

    private static final class QueueDetail {
        final int inbound, outbound;
        final long oldestAgeMs;

        private QueueDetail(int inbound, int outbound, long oldestAgeMs) {
            this.inbound = inbound; this.outbound = outbound; this.oldestAgeMs = oldestAgeMs;
        }

        static QueueDetail parse(String detail) {
            String[] fields = fields(detail, "inbound", "outbound", "oldestAgeMs");
            if (fields == null) return null;
            try {
                long in = integer(fields[0]), out = integer(fields[1]), age = integer(fields[2]);
                if (in < 0 || in > Integer.MAX_VALUE || out < 0 || out > Integer.MAX_VALUE || age < 0) return null;
                return new QueueDetail((int) in, (int) out, age);
            } catch (NumberFormatException invalid) { return null; }
        }
    }
}
