package me.youngjibbit.core.diagnostics;

import org.junit.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class FrameTimeCaptureTest {
    @Test public void disabledAndLifecyclePathsDoNotReadTheClockOrIncludePartialFrames() {
        AtomicLong now = new AtomicLong(), reads = new AtomicLong();
        FrameTimeCapture capture = new FrameTimeCapture(() -> { reads.incrementAndGet(); return now.get(); });
        capture.frame();
        capture.stop();
        assertEquals(0, reads.get());
        capture.start(4, 20);
        now.set(100);
        capture.frame();
        assertEquals(0, capture.snapshot().statistics.frames);
        now.set(130);
        capture.frame();
        now.set(145);
        capture.stop();
        capture.frame();
        assertEquals(2, reads.get());
        assertArrayEquals(new long[] {30}, capture.snapshot().frameIntervalsNanos());
        assertEquals(FrameTimeCapture.State.STOPPED, capture.snapshot().state);
        capture.reset();
        assertEquals(FrameTimeCapture.State.IDLE, capture.snapshot().state);
        assertEquals(0, capture.snapshot().capacity);
        assertEquals(2, reads.get());
    }

    @Test public void durationStopRetainsTheCompleteCrossingFrameAndDoesNotOverwriteAtCapacity() {
        AtomicLong now = new AtomicLong(100);
        FrameTimeCapture capture = new FrameTimeCapture(now::get);
        capture.start(8, 5, 10);
        capture.frame();
        now.addAndGet(6); capture.frame();
        now.addAndGet(7); capture.frame();
        now.addAndGet(20); capture.frame();
        FrameTimeCapture.Snapshot timed = capture.snapshot();
        assertFalse(capture.isRecording());
        assertEquals(FrameTimeCapture.State.DURATION_REACHED, timed.state);
        assertEquals(10, timed.requestedDurationNanos);
        assertArrayEquals(new long[] {6, 7}, timed.frameIntervalsNanos());
        capture.start(2, 5);
        capture.frame();
        now.addAndGet(8); capture.frame();
        now.addAndGet(9); capture.frame();
        now.addAndGet(10); capture.frame();
        assertEquals(FrameTimeCapture.State.CAPACITY_REACHED, capture.snapshot().state);
        assertArrayEquals(new long[] {8, 9}, capture.snapshot().frameIntervalsNanos());
    }

    @Test public void foreignThreadsCannotAddIntervalsResetOrStopCapture() throws Exception {
        AtomicLong now = new AtomicLong(), reads = new AtomicLong();
        FrameTimeCapture capture = new FrameTimeCapture(() -> { reads.incrementAndGet(); return now.get(); });
        capture.start(5, 10);
        capture.frame();
        AtomicReference<Throwable> observed = new AtomicReference<>();
        Thread foreign = new Thread(() -> {
            capture.frame();
            try { capture.stop(); } catch (Throwable error) { observed.set(error); }
        });
        foreign.start(); foreign.join();
        assertTrue(observed.get() instanceof IllegalStateException);
        assertTrue(capture.isRecording());
        assertEquals(1, reads.get());
        now.set(20); capture.frame();
        assertArrayEquals(new long[] {20}, capture.snapshot().frameIntervalsNanos());
    }

    @Test public void lifecycleAbortPreservesEvidenceAndSnapshotsHaveIndependentOwnership() {
        AtomicLong now = new AtomicLong();
        FrameTimeCapture capture = new FrameTimeCapture(now::get);
        capture.start(5, 10);
        capture.frame(); now.set(25); capture.frame();
        FrameTimeCapture.Snapshot earlier = capture.snapshot();
        long[] external = earlier.frameIntervalsNanos();
        external[0] = 999;
        now.set(50); capture.frame();
        capture.abort("World changed");
        capture.abort("Resource changed later");
        FrameTimeCapture.Snapshot aborted = capture.snapshot();
        assertEquals(FrameTimeCapture.State.ABORTED, aborted.state);
        assertEquals("World changed", aborted.reason);
        assertArrayEquals(new long[] {25}, earlier.frameIntervalsNanos());
        assertArrayEquals(new long[] {25, 25}, aborted.frameIntervalsNanos());
        capture.reset();
        assertArrayEquals(new long[] {25, 25}, aborted.frameIntervalsNanos());
    }

    @Test public void invalidClockEndsCaptureVisiblyAndNanoTimeWrapUsesElapsedDifference() {
        AtomicLong now = new AtomicLong(100);
        FrameTimeCapture capture = new FrameTimeCapture(now::get);
        capture.start(4, 10); capture.frame();
        now.set(125); capture.frame();
        now.set(124); capture.frame();
        FrameTimeCapture.Snapshot invalid = capture.snapshot();
        assertEquals(FrameTimeCapture.State.CLOCK_DISCONTINUITY, invalid.state);
        assertEquals(1, invalid.rejectedIntervals);
        assertArrayEquals(new long[] {25}, invalid.frameIntervalsNanos());
        now.set(Long.MAX_VALUE - 5);
        capture.start(1, 10); capture.frame();
        now.addAndGet(10); capture.frame();
        assertArrayEquals(new long[] {10}, capture.snapshot().frameIntervalsNanos());
        assertEquals(FrameTimeCapture.State.CAPACITY_REACHED, capture.snapshot().state);
    }

    @Test public void rejectedParametersLeaveAnExistingCaptureIntact() {
        AtomicLong now = new AtomicLong();
        FrameTimeCapture capture = new FrameTimeCapture(now::get);
        capture.start(4, 10); capture.frame();
        expectInvalid(capture, 0, 10, 0);
        expectInvalid(capture, FrameTimeCapture.MAX_SAMPLES + 1, 10, 0);
        expectInvalid(capture, 4, 0, 0);
        expectInvalid(capture, 4, 10, -1);
        now.set(20); capture.frame();
        assertTrue(capture.isRecording());
        assertEquals(4, capture.snapshot().capacity);
        assertArrayEquals(new long[] {20}, capture.snapshot().frameIntervalsNanos());
    }

    @Test public void csvRetainsOrderAndUsesInvariantDecimalsWithoutClosingTheWriter() throws Exception {
        AtomicLong now = new AtomicLong();
        FrameTimeCapture capture = new FrameTimeCapture(now::get);
        capture.start(2, 2_500_000);
        capture.frame(); now.set(2_500_000); capture.frame(); now.set(5_500_000); capture.frame();
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            StringWriter result = new StringWriter();
            capture.snapshot().writeCsv(result);
            result.write("still-open");
            assertEquals("frame_index,interval_ns,interval_ms,long_frame\n0,2500000,2.5,0\n1,3000000,3.0,1\nstill-open", result.toString());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test public void exportFailurePropagatesWithoutDiscardingEvidence() throws Exception {
        AtomicLong now = new AtomicLong();
        FrameTimeCapture capture = new FrameTimeCapture(now::get);
        capture.start(1, 10); capture.frame(); now.set(20); capture.frame();
        try {
            capture.snapshot().writeCsv(new Writer() {
                @Override public void write(char[] value, int offset, int length) throws IOException { throw new IOException("disk full"); }
                @Override public void flush() { }
                @Override public void close() { fail("Exporter must not own the writer"); }
            });
            fail("Export must report I/O failure");
        } catch (IOException expected) {
            assertEquals("disk full", expected.getMessage());
        }
        assertEquals(FrameTimeCapture.State.CAPACITY_REACHED, capture.snapshot().state);
        assertArrayEquals(new long[] {20}, capture.snapshot().frameIntervalsNanos());
    }

    private static void expectInvalid(FrameTimeCapture capture, int capacity, long threshold, long duration) {
        try {
            capture.start(capacity, threshold, duration);
            fail("Invalid capture parameters must be rejected");
        } catch (IllegalArgumentException expected) {
            assertNotNull(expected.getMessage());
        }
    }
}
