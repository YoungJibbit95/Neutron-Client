package me.youngjibbit.core.diagnostics;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.Collections;
import me.youngjibbit.core.diagnostics.NetworkResearchAnalysis.Issue;
import me.youngjibbit.core.diagnostics.NetworkResearchAnalysis.Report;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Entry;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Kind;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Mode;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.Origin;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkResearchAnalysisTest {
    private static final long TICK = 50_000_000L;

    private static NetworkResearchTrace client(int capacity) {
        NetworkResearchTrace trace = new NetworkResearchTrace(capacity);
        trace.startRun(Mode.BACKTRACK, Origin.CLIENT_OBSERVATION);
        return trace;
    }

    private static Report analyze(NetworkResearchTrace trace) {
        return NetworkResearchAnalysis.analyze(trace.recording(0, "", 0));
    }

    private static NetworkResearchRecording simulation(NetworkResearchSimulation.Scenario scenario, Mode mode) {
        NetworkResearchTrace trace = new NetworkResearchTrace(512);
        NetworkResearchSimulation simulation = new NetworkResearchSimulation();
        simulation.run(scenario, mode, trace);
        return trace.recording(0, scenario.name(), simulation.getRejected());
    }

    private static String reportJson(Report report) throws IOException {
        StringWriter writer = new StringWriter();
        report.write(writer);
        return writer.toString();
    }

    private static Entry entry(long sequence, long nanos, Kind kind, long interval, long deviation, String detail) {
        return new Entry(1, sequence, nanos, Mode.BACKTRACK, Origin.CLIENT_OBSERVATION,
                kind, "", -1, 0, 0, 0, 0, 0, 0, interval, deviation, detail);
    }

    private static Report analyze(Entry... entries) {
        return NetworkResearchAnalysis.analyze(new NetworkResearchRecording(1, Mode.BACKTRACK,
                Origin.CLIENT_OBSERVATION, 512, entries.length, 0, 0, 0, "", 0, Arrays.asList(entries)));
    }

    private static String tickDetail(boolean baseline) {
        return "steps=-1;withheld=false;baseline=" + baseline;
    }

    @Test public void everyScenarioAndProfileProducesAnIdenticalReportAfterRecordingRoundTrip() throws Exception {
        for (NetworkResearchSimulation.Scenario scenario : NetworkResearchSimulation.Scenario.values()) {
            for (Mode mode : Mode.values()) {
                NetworkResearchRecording original = simulation(scenario, mode);
                StringWriter encoded = new StringWriter(); original.write(encoded);
                NetworkResearchRecording decoded = NetworkResearchRecording.read(new StringReader(encoded.toString()));
                Report report = NetworkResearchAnalysis.analyze(original);
                assertArrayEquals(reportJson(report).getBytes("UTF-8"),
                        reportJson(NetworkResearchAnalysis.analyze(decoded)).getBytes("UTF-8"));
                assertEquals(61, report.retainedSamples);
                assertEquals(61, report.appended);
                assertEquals(0, report.overwritten);
                assertEquals(Integer.valueOf(12), report.counts.get(Kind.RECEIVED));
                assertEquals(Integer.valueOf(12), report.counts.get(Kind.APPLIED));
                assertEquals(Integer.valueOf(12), report.counts.get(Kind.TICK));
                assertEquals(Integer.valueOf(12), report.counts.get(Kind.ENTITY));
                assertEquals(Integer.valueOf(13), report.counts.get(Kind.QUEUE));
                assertEquals(11, report.tickIntervals.samples);
                assertEquals(1, report.baselineTicks);
                assertEquals(0, report.unknownStepTicks);
                assertEquals(mode, report.mode);
                assertEquals(scenario.name(), report.scenario);
                assertEquals(Origin.SIMULATION, report.origin);
                assertEquals("UNKNOWN", report.serverOutcome);
                assertTrue(report.issues.toString(), report.issues.isEmpty());
            }
        }
    }

    @Test public void pauseUsesRetainedTickIntervalsAndNearestRankPercentiles() {
        Report report = NetworkResearchAnalysis.analyze(simulation(NetworkResearchSimulation.Scenario.PAUSE, Mode.TICKBASE));
        assertEquals(Long.valueOf(TICK), report.tickIntervals.minNanos);
        assertEquals(Long.valueOf(TICK), report.tickIntervals.p50Nanos);
        assertEquals(Long.valueOf(300_000_000L), report.tickIntervals.p95Nanos);
        assertEquals(Long.valueOf(300_000_000L), report.tickIntervals.maxNanos);
        assertEquals(800_000_000D / 11D, report.tickIntervals.meanNanos, 0.000001);
        assertEquals(Long.valueOf(1_800_000_000L), report.retainedSpanNanos);
    }

    @Test public void aTruncatedPrefixDoesNotInventTheMissingTickPair() {
        NetworkResearchTrace trace = client(2);
        trace.tick(0, -1, false); trace.tick(TICK, -1, false); trace.tick(TICK * 2, -1, false);
        Report report = analyze(trace);
        assertEquals(1, report.overwritten);
        assertEquals(2, report.retainedSamples);
        assertEquals(0, report.baselineTicks);
        assertEquals(2, report.unknownStepTicks);
        assertEquals(1, report.unpairedTickIntervals);
        assertEquals(1, report.tickIntervals.samples);
        assertEquals(Long.valueOf(TICK), report.tickIntervals.p50Nanos);
        assertEquals(Long.valueOf(TICK), report.retainedSpanNanos);
        assertTrue(report.issues.contains(Issue.OVERWRITTEN_PREFIX));
        assertTrue(report.issues.contains(Issue.UNPAIRED_TICK_INTERVAL));
    }

    @Test public void signedNanoTimeWrapPreservesShortPositiveIntervals() {
        NetworkResearchTrace trace = client(4);
        long before = Long.MAX_VALUE - TICK / 2;
        trace.tick(before, 1, false); trace.tick(before + TICK, 1, false);
        Report report = analyze(trace);
        assertEquals(Long.valueOf(TICK), report.retainedSpanNanos);
        assertEquals(Long.valueOf(TICK), report.tickIntervals.minNanos);
        assertEquals(1, report.tickIntervals.samples);
        assertTrue(report.issues.isEmpty());
    }

    @Test public void aClockRegressionInvalidatesSpanAndBreaksTheAdjacentTickPair() {
        NetworkResearchTrace trace = client(8);
        trace.tick(0, 1, false); trace.tick(TICK, 1, false);
        trace.packet(TICK - 1, Kind.RECEIVED, "clock regression");
        trace.tick(TICK * 2, 1, false); trace.tick(TICK * 3, 1, false);
        Report report = analyze(trace);
        assertNull(report.retainedSpanNanos);
        assertTrue(report.issues.contains(Issue.CLOCK_REGRESSION));
        assertTrue(report.issues.contains(Issue.UNPAIRED_TICK_INTERVAL));
        assertEquals(1, report.unpairedTickIntervals);
        assertEquals(2, report.tickIntervals.samples);
        assertEquals(Long.valueOf(TICK), report.tickIntervals.maxNanos);
    }

    @Test public void cumulativeDurationOverflowLeavesFiniteIntervalStatistics() {
        NetworkResearchTrace trace = client(4);
        trace.tick(0, 1, false); trace.tick(Long.MAX_VALUE, 1, false); trace.tick(-2, 1, false);
        Report report = analyze(trace);
        assertNull(report.retainedSpanNanos);
        assertTrue(report.issues.contains(Issue.DURATION_OVERFLOW));
        assertFalse(report.issues.contains(Issue.CLOCK_REGRESSION));
        assertEquals(2, report.tickIntervals.samples);
        assertEquals(Long.valueOf(Long.MAX_VALUE), report.tickIntervals.maxNanos);
        assertTrue(Double.isFinite(report.tickIntervals.meanNanos));
        assertEquals((double) Long.MAX_VALUE, report.tickIntervals.meanNanos, 0);
    }

    @Test public void emptyStatisticsAreNullAndReportsRemainImmutableSnapshots() throws Exception {
        NetworkResearchTrace trace = client(4);
        Report empty = analyze(trace);
        assertEquals(Collections.singletonList(Issue.EMPTY_RECORDING), empty.issues);
        assertNull(empty.retainedSpanNanos);
        assertEquals(0, empty.tickIntervals.samples);
        assertNull(empty.tickIntervals.minNanos);
        assertNull(empty.tickIntervals.p50Nanos);
        assertNull(empty.tickIntervals.p95Nanos);
        assertNull(empty.tickIntervals.maxNanos);
        assertNull(empty.tickIntervals.meanNanos);
        assertNull(empty.maximumRenderedPositionOffset);
        for (Kind kind : Kind.values()) assertEquals(Integer.valueOf(0), empty.counts.get(kind));
        assertThrows(UnsupportedOperationException.class, () -> empty.counts.put(Kind.TICK, 2));
        assertThrows(UnsupportedOperationException.class, () -> empty.issues.clear());
        String before = reportJson(empty);
        trace.tick(0, 1, false); trace.tick(TICK, 1, false);
        trace.startRun(Mode.LAGRANGE, Origin.CLIENT_OBSERVATION);
        assertEquals(before, reportJson(empty));
        assertTrue(before.contains("\"meanNanos\": null"));
    }

    @Test public void localAcceptanceCorrectionsAndManualReferencesNeverEstablishServerOutcome() {
        NetworkResearchTrace trace = client(8);
        trace.packet(0, Kind.RECEIVED, "sample");
        trace.packet(1, Kind.LOCAL_ACCEPTED, "sample");
        trace.packet(2, Kind.CORRECTION, "sample");
        Report clientOnly = analyze(trace);
        assertEquals("UNKNOWN", clientOnly.serverOutcome);
        assertEquals("UNKNOWN", clientOnly.serverEvidence);
        assertEquals(Integer.valueOf(1), clientOnly.counts.get(Kind.LOCAL_ACCEPTED));
        assertEquals(Integer.valueOf(1), clientOnly.counts.get(Kind.CORRECTION));
        trace.serverFlag(3, "server-session.log#line-42", "manual-check-label");
        Report referenced = analyze(trace);
        assertEquals("UNKNOWN", referenced.serverOutcome);
        assertEquals("REFERENCES_PRESENT_UNVERIFIED", referenced.serverEvidence);
        assertEquals(Integer.valueOf(1), referenced.counts.get(Kind.SERVER_FLAG));
    }

    @Test public void queueMaximaUseOnlyRecordedQueueSamples() {
        NetworkResearchTrace trace = client(8);
        trace.queue(0, 2, 8, 15);
        trace.queue(1, 5, 3, Long.MAX_VALUE);
        trace.queue(2, 0, 0, 0);
        Report report = analyze(trace);
        assertEquals(3, report.queueSamples);
        assertEquals(5, report.maximumInboundQueue);
        assertEquals(8, report.maximumOutboundQueue);
        assertEquals(Long.MAX_VALUE, report.maximumOldestQueueAgeMs);
        assertTrue(report.issues.isEmpty());
    }

    @Test public void overflowingDifferencesBetweenFiniteCoordinatesAreExcludedFromOffsets() {
        NetworkResearchTrace trace = client(4);
        trace.entity(0, 1, Double.MAX_VALUE, 0, 0, -Double.MAX_VALUE, 0, 0);
        trace.entity(1, 1, 3, 4, 0, 0, 0, 0);
        Report report = analyze(trace);
        assertEquals(Integer.valueOf(2), report.counts.get(Kind.ENTITY));
        assertEquals(1, report.renderedOffsetSamples);
        assertEquals(5D, report.maximumRenderedPositionOffset, 0);
        assertTrue(report.issues.contains(Issue.DERIVED_COORDINATE_OVERFLOW));
    }

    @Test public void malformedDetailsAndInconsistentIntervalsProduceIssuesWithoutFabricatedSamples() {
        for (String invalid : Arrays.asList("", "steps=1;withheld=FALSE;baseline=false",
                "steps=1.0;withheld=false;baseline=false", "steps=-2;withheld=false;baseline=false",
                "steps=2147483648;withheld=false;baseline=false", "steps=1;withheld=false;baseline=false;extra=1")) {
            Report report = analyze(entry(1, 0, Kind.TICK, 0, 0, tickDetail(true)),
                    entry(2, TICK, Kind.TICK, TICK, 0, invalid),
                    entry(3, TICK * 2, Kind.TICK, TICK, 0, tickDetail(false)));
            assertTrue(invalid, report.issues.contains(Issue.INVALID_TICK_DETAIL));
            assertEquals(0, report.tickIntervals.samples);
            assertEquals(1, report.unpairedTickIntervals);
        }
        for (String invalid : Arrays.asList("", "inbound=-1;outbound=0;oldestAgeMs=0",
                "inbound=1.0;outbound=0;oldestAgeMs=0", "inbound=2147483648;outbound=0;oldestAgeMs=0",
                "inbound=1;outbound=2;oldestAgeMs=-1", "inbound=1;outbound=2;oldestAgeMs=9223372036854775808")) {
            Report report = analyze(entry(1, 0, Kind.QUEUE, 0, 0, invalid));
            assertTrue(invalid, report.issues.contains(Issue.INVALID_QUEUE_DETAIL));
            assertEquals(0, report.queueSamples);
            assertEquals(0, report.maximumInboundQueue);
        }
        Report mismatched = analyze(entry(1, 0, Kind.TICK, 1, 0, tickDetail(true)),
                entry(2, TICK, Kind.TICK, TICK - 1, -1, tickDetail(false)),
                entry(3, TICK * 2, Kind.TICK, TICK, 1, tickDetail(false)),
                entry(4, TICK * 3, Kind.TICK, TICK, 0, tickDetail(false)));
        assertTrue(mismatched.issues.contains(Issue.TICK_INTERVAL_MISMATCH));
        assertEquals(1, mismatched.tickIntervals.samples);
        assertEquals(Long.valueOf(TICK), mismatched.tickIntervals.minNanos);
    }

    @Test public void rejectionAndCaptureCountersRemainExplicitQualityIssues() {
        NetworkResearchTrace trace = client(4);
        trace.entity(0, 1, Double.NaN, 0, 0, 0, 0, 0);
        trace.queue(0, -1, 0, 0); trace.tick(0, -2, false);
        trace.tick(0, -1, false);
        Report report = NetworkResearchAnalysis.analyze(trace.recording(5, "", 0));
        assertEquals(3, report.rejected);
        assertEquals(5, report.captureFailures);
        assertTrue(report.issues.contains(Issue.REJECTED_OBSERVATIONS));
        assertTrue(report.issues.contains(Issue.CAPTURE_FAILURES));
        assertEquals(1, report.retainedSamples);
        trace.startRun(Mode.TICKBASE, Origin.SIMULATION);
        trace.tick(0, 1, false);
        Report simulation = NetworkResearchAnalysis.analyze(trace.recording(0, "BASELINE", 4));
        assertEquals(4, simulation.simulationRejected);
        assertTrue(simulation.issues.contains(Issue.SIMULATION_REJECTIONS));
        assertEquals("UNKNOWN", simulation.serverOutcome);
    }
}
