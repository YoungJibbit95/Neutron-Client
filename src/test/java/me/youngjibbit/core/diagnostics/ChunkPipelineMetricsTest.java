package me.youngjibbit.core.diagnostics;

import org.junit.Test;

import java.io.StringWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class ChunkPipelineMetricsTest {
    @Test public void disabledAndSaturatedPathsDoNotReadClockAndCapacityDoesNotDiscardPendingTasks() {
        AtomicLong now = new AtomicLong(100), reads = new AtomicLong();
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(() -> { reads.incrementAndGet(); return now.get(); });
        metrics.dataAvailable(1, 2);
        assertNull(metrics.beginTask(1, 0, 2, "REBUILD_CHUNK", 0));
        assertEquals(0, reads.get());
        metrics.start(1);
        ChunkPipelineMetrics.Trace first = metrics.beginTask(1, 0, 2, "REBUILD_CHUNK", 0);
        long before = reads.get();
        assertNull(metrics.beginTask(2, 0, 2, "REBUILD_CHUNK", 1));
        assertEquals(before, reads.get());
        now.set(200); first.meshStarted(); now.set(300); first.meshed(); first.drawn();
        ChunkPipelineMetrics.Snapshot result = metrics.snapshot();
        assertEquals(1, result.traces.size());
        assertEquals(2, result.taskRequests);
        assertEquals(1, result.droppedTasks);
        assertEquals(ChunkPipelineMetrics.Outcome.DRAW_SUBMITTED, result.traces.get(0).outcome);
    }

    @Test public void exactStagesTrackLatestDecodedColumnAndFirstSubmissionWithoutClaimingGpuCompletion() {
        AtomicLong now = new AtomicLong(1000);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(4);
        now.set(1010); metrics.dataAvailable(-2, 7);
        now.set(1020); metrics.dataAvailable(-2, 7);
        now.set(1030);
        ChunkPipelineMetrics.Trace trace = metrics.beginTask(-2, 3, 7, "REBUILD_CHUNK", 4);
        now.set(1040); trace.meshStarted();
        now.set(1050); trace.meshStarted();
        now.set(1060); trace.meshed();
        now.set(1070); trace.uploadStarted();
        now.set(1080); trace.uploaded(128);
        now.set(1090); trace.uploadStarted(); trace.uploaded(64);
        now.set(1100); trace.drawn();
        now.set(1110); trace.drawn(); trace.cancelled("Renderer recycled later");
        ChunkPipelineMetrics.TraceSnapshot row = metrics.snapshot().traces.get(0);
        assertEquals(-2, row.chunkX); assertEquals(3, row.sectionY); assertEquals(7, row.chunkZ);
        assertEquals(2, row.columnDataSequence);
        assertEquals(20, row.dataAvailableNanos); assertEquals(30, row.queuedNanos);
        assertEquals(40, row.meshStartNanos); assertEquals(60, row.meshEndNanos);
        assertEquals(70, row.uploadStartNanos); assertEquals(90, row.uploadEndNanos);
        assertEquals(100, row.firstDrawSubmitNanos); assertEquals(100, row.finishedNanos);
        assertEquals(192, row.uploadedBytes); assertEquals(4, row.compileQueueDepth);
        assertEquals(ChunkPipelineMetrics.Outcome.DRAW_SUBMITTED, row.outcome);
    }

    @Test public void boundedColumnHistoryEvictsOldestHandoffAndNeverAliasesSignedCoordinates() {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(2);
        now.set(110); metrics.dataAvailable(-1, -1);
        now.set(120); metrics.dataAvailable(-1, 0);
        now.set(130); metrics.dataAvailable(-1, -1);
        now.set(140); metrics.dataAvailable(0, -1);
        metrics.beginTask(-1, 0, 0, "REBUILD_CHUNK", 0);
        metrics.beginTask(-1, 1, -1, "REBUILD_CHUNK", 1);
        ChunkPipelineMetrics.Snapshot result = metrics.snapshot();
        assertEquals(1, result.evictedDataColumns);
        assertEquals(4, result.dataUpdates);
        assertEquals(-1, result.traces.get(0).dataAvailableNanos);
        assertEquals(-1, result.traces.get(0).columnDataSequence);
        assertEquals(30, result.traces.get(1).dataAvailableNanos);
        assertEquals(3, result.traces.get(1).columnDataSequence);
    }

    @Test public void firstDrawRetiresOnlyTheDrawnSectionAndLaterRebuildsStayUnobserved() {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(4);
        now.set(110); metrics.dataAvailable(2, 3, (1 << 4) | (1 << 5));
        ChunkPipelineMetrics.Trace drawn = metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        now.set(120); drawn.drawn();
        now.set(60_000_000_000L);
        metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        metrics.beginTask(2, 5, 3, "REBUILD_CHUNK", 0);
        metrics.beginTask(2, 6, 3, "REBUILD_CHUNK", 0);
        java.util.List<ChunkPipelineMetrics.TraceSnapshot> rows = metrics.snapshot().traces;
        assertEquals(10, rows.get(0).dataAvailableNanos);
        assertEquals(-1, rows.get(1).dataAvailableNanos);
        assertEquals(-1, rows.get(1).columnDataSequence);
        assertEquals(10, rows.get(2).dataAvailableNanos);
        assertEquals(-1, rows.get(3).dataAvailableNanos);
    }

    @Test public void drawingOlderTraceCannotRetireAReplacementPacketMark() {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(3);
        now.set(110); metrics.dataAvailable(2, 3, 1 << 4);
        ChunkPipelineMetrics.Trace old = metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        now.set(120); metrics.dataAvailable(2, 3, 1 << 4);
        now.set(130); old.drawn();
        ChunkPipelineMetrics.Trace replacement = metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        now.set(140); replacement.drawn();
        metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        java.util.List<ChunkPipelineMetrics.TraceSnapshot> rows = metrics.snapshot().traces;
        assertEquals(1, rows.get(0).columnDataSequence);
        assertEquals(2, rows.get(1).columnDataSequence);
        assertEquals(20, rows.get(1).dataAvailableNanos);
        assertEquals(-1, rows.get(2).dataAvailableNanos);
    }

    @Test public void rejectedOrFailedAttemptsPreserveTheMarkForRetryUntilItDraws() {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(4);
        now.set(110); metrics.dataAvailable(2, 3, 1 << 4);
        metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 100).cancelled("Queue admission rejected");
        metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0).failed("Retry failed while meshing");
        ChunkPipelineMetrics.Trace retry = metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        retry.drawn();
        metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        java.util.List<ChunkPipelineMetrics.TraceSnapshot> rows = metrics.snapshot().traces;
        for (int i = 0; i < 3; i++) assertEquals(10, rows.get(i).dataAvailableNanos);
        assertEquals(-1, rows.get(3).dataAvailableNanos);
    }

    @Test public void cancelAndFailureAreTerminalAndMissingStagesRemainUnobserved() {
        AtomicLong now = new AtomicLong();
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(2);
        ChunkPipelineMetrics.Trace cancelled = metrics.beginTask(0, 0, 0, "REBUILD_CHUNK", 0);
        ChunkPipelineMetrics.Trace failed = metrics.beginTask(0, 0, 0, "RESORT_TRANSPARENCY", 1);
        now.set(10); cancelled.cancelled("Queue full");
        now.set(20); cancelled.meshed(); cancelled.uploaded(100); cancelled.drawn();
        now.set(30); failed.meshStarted();
        now.set(40); failed.failed("Mesher exception");
        now.set(50); failed.cancelled(); failed.uploaded(100);
        ChunkPipelineMetrics.TraceSnapshot a = metrics.snapshot().traces.get(0), b = metrics.snapshot().traces.get(1);
        assertEquals(ChunkPipelineMetrics.Outcome.CANCELLED, a.outcome);
        assertEquals("Queue full", a.reason); assertEquals(10, a.finishedNanos);
        assertEquals(-1, a.meshEndNanos); assertEquals(-1, a.firstDrawSubmitNanos); assertEquals(0, a.uploadedBytes);
        assertEquals(ChunkPipelineMetrics.Outcome.FAILED, b.outcome);
        assertEquals("Mesher exception", b.reason); assertEquals(40, b.finishedNanos);
        assertEquals(30, b.meshStartNanos); assertEquals(-1, b.meshEndNanos);
    }

    @Test public void transparencySortNeitherAssociatesNorConsumesDecodedSectionData() {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(2);
        now.set(110); metrics.dataAvailable(2, 3, 1 << 4);
        metrics.beginTask(2, 4, 3, "RESORT_TRANSPARENCY", 0).drawn();
        metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        assertEquals(-1, metrics.snapshot().traces.get(0).dataAvailableNanos);
        assertEquals(-1, metrics.snapshot().traces.get(0).columnDataSequence);
        assertEquals(10, metrics.snapshot().traces.get(1).dataAvailableNanos);
    }

    @Test public void partialPacketUpdatesPreserveOtherSectionsWithoutClaimingWholeWorldRevision() {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(3);
        now.set(110); metrics.dataAvailable(2, 3, (1 << 4) | (1 << 5));
        now.set(120); metrics.dataAvailable(2, 3, 1 << 4);
        metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        metrics.beginTask(2, 5, 3, "REBUILD_CHUNK", 0);
        metrics.beginTask(2, 6, 3, "REBUILD_CHUNK", 0);
        java.util.List<ChunkPipelineMetrics.TraceSnapshot> rows = metrics.snapshot().traces;
        assertEquals(2, rows.get(0).columnDataSequence);
        assertEquals(20, rows.get(0).dataAvailableNanos);
        assertEquals(1, rows.get(1).columnDataSequence);
        assertEquals(10, rows.get(1).dataAvailableNanos);
        assertEquals(-1, rows.get(2).dataAvailableNanos);
    }

    @Test public void emptyMaskReadsNoClockAndOutOfWorldSectionsNeverAliasValidBits() {
        AtomicLong reads = new AtomicLong();
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(reads::incrementAndGet);
        metrics.start(3);
        long before = reads.get();
        metrics.dataAvailable(2, 3, 0);
        assertEquals(before, reads.get());
        assertEquals(0, metrics.snapshot().dataUpdates);
        for (int invalidMask : new int[] {-1, 0x10000}) {
            try { metrics.dataAvailable(2, 3, invalidMask); fail("Invalid section mask accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        metrics.dataAvailable(2, 3, 0xffff);
        metrics.beginTask(2, -1, 3, "REBUILD_CHUNK", 0);
        metrics.beginTask(2, 16, 3, "REBUILD_CHUNK", 0);
        metrics.beginTask(2, 15, 3, "REBUILD_CHUNK", 0);
        java.util.List<ChunkPipelineMetrics.TraceSnapshot> rows = metrics.snapshot().traces;
        assertEquals(-1, rows.get(0).dataAvailableNanos);
        assertEquals(-1, rows.get(1).dataAvailableNanos);
        assertTrue(rows.get(2).dataAvailableNanos >= 0);
    }

    @Test public void fullColumnReplacementAndUnloadDiscardOmittedOldSectionsWithoutReadingClock() {
        AtomicLong now = new AtomicLong(100), reads = new AtomicLong();
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(() -> { reads.incrementAndGet(); return now.get(); });
        metrics.forgetColumn(2, 3);
        assertEquals(0, reads.get());
        metrics.start(4);
        now.set(110); metrics.dataAvailable(2, 3, 0xffff);
        ChunkPipelineMetrics.Trace old = metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        long before = reads.get(); metrics.forgetColumn(2, 3);
        assertEquals(before, reads.get());
        now.set(120); metrics.dataAvailable(2, 3, 1 << 5);
        old.drawn(); // prior observation stays attached only to the old task
        metrics.beginTask(2, 4, 3, "REBUILD_CHUNK", 0);
        metrics.beginTask(2, 5, 3, "REBUILD_CHUNK", 0);
        before = reads.get(); metrics.forgetColumn(2, 3); // full-column mask-zero unload
        assertEquals(before, reads.get());
        metrics.beginTask(2, 5, 3, "REBUILD_CHUNK", 0);
        java.util.List<ChunkPipelineMetrics.TraceSnapshot> rows = metrics.snapshot().traces;
        assertEquals(10, rows.get(0).dataAvailableNanos);
        assertEquals(-1, rows.get(1).dataAvailableNanos);
        assertEquals(20, rows.get(2).dataAvailableNanos);
        assertEquals(-1, rows.get(3).dataAvailableNanos);
    }

    @Test public void stoppedAbortedAndReplacedEpochsIgnoreLateCallbacksAndSnapshotsAreImmutable() {
        AtomicLong now = new AtomicLong();
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(4);
        ChunkPipelineMetrics.Trace previous = metrics.beginTask(0, 0, 0, "REBUILD_CHUNK", 0);
        now.set(10); previous.meshStarted();
        metrics.abort("World changed");
        ChunkPipelineMetrics.Snapshot frozen = metrics.snapshot();
        now.set(20); previous.meshed(); previous.drawn();
        metrics.start(4);
        ChunkPipelineMetrics.Trace current = metrics.beginTask(0, 0, 0, "REBUILD_CHUNK", 0);
        previous.failed();
        now.set(30); current.meshStarted();
        metrics.stop();
        now.set(40); current.meshed(); current.drawn();
        assertEquals(ChunkPipelineMetrics.State.ABORTED, frozen.state);
        assertEquals("World changed", frozen.reason);
        assertEquals(-1, frozen.traces.get(0).meshEndNanos);
        assertEquals(-1, metrics.snapshot().traces.get(0).meshEndNanos);
        assertTrue(current.epoch > previous.epoch);
        assertEquals(1, current.sequence);
        try { frozen.traces.clear(); fail("Snapshot list must be immutable"); }
        catch (UnsupportedOperationException expected) { }
        metrics.reset();
        assertEquals(ChunkPipelineMetrics.State.IDLE, metrics.snapshot().state);
        assertEquals(1, frozen.traces.size());
    }

    @Test public void workerEventsAreVisibleButCaptureMutationBelongsToClientThread() throws Exception {
        AtomicLong now = new AtomicLong();
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(4);
        ChunkPipelineMetrics.Trace trace = metrics.beginTask(1, 2, 3, "REBUILD_CHUNK", 1);
        AtomicReference<Throwable> forbidden = new AtomicReference<>();
        CountDownLatch meshed = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            now.set(10); trace.meshStarted(); now.set(20); trace.meshed();
            try { metrics.reset(); } catch (Throwable error) { forbidden.set(error); }
            meshed.countDown();
        });
        worker.start(); meshed.await(); worker.join();
        now.set(30); trace.uploadStarted(); now.set(40); trace.uploaded(256); now.set(50); trace.drawn();
        ChunkPipelineMetrics.TraceSnapshot row = metrics.snapshot().traces.get(0);
        assertTrue(forbidden.get() instanceof IllegalStateException);
        assertEquals(10, row.meshStartNanos); assertEquals(20, row.meshEndNanos);
        assertEquals(256, row.uploadedBytes); assertEquals(50, row.firstDrawSubmitNanos);
    }

    @Test public void invalidClockAndByteOverflowRemainVisibleInsteadOfWrappingEvidence() {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(2);
        ChunkPipelineMetrics.Trace trace = metrics.beginTask(0, 0, 0, "REBUILD_CHUNK", 0);
        now.set(99); trace.meshStarted();
        now.set(110); trace.uploaded(Long.MAX_VALUE); trace.uploaded(1);
        ChunkPipelineMetrics.Snapshot snapshot = metrics.snapshot();
        assertEquals(1, snapshot.invalidClockEvents);
        assertEquals(-1, snapshot.traces.get(0).meshStartNanos);
        assertEquals(Long.MAX_VALUE, snapshot.traces.get(0).uploadedBytes);
        assertTrue(snapshot.traces.get(0).uploadByteCountOverflow);
    }

    @Test public void csvQuotesReasonsAndRetainsIncompleteRawRows() throws Exception {
        AtomicLong now = new AtomicLong(100);
        ChunkPipelineMetrics metrics = new ChunkPipelineMetrics(now::get);
        metrics.start(1);
        ChunkPipelineMetrics.Trace trace = metrics.beginTask(-1, 2, 3, "REBUILD_CHUNK", 4);
        now.set(120); trace.failed("failure, \"quoted\"\nnext line");
        metrics.stop();
        StringWriter writer = new StringWriter();
        metrics.snapshot().writeCsv(writer);
        String csv = writer.toString();
        assertTrue(csv.startsWith("epoch,sequence,chunk_x,section_y,chunk_z,kind,"));
        assertTrue(csv.contains("1,1,-1,2,3,\"REBUILD_CHUNK\",-1,-1,0,-1,-1,-1,-1,-1,20,0,false,4,FAILED,"));
        assertTrue(csv.endsWith("\"failure, \"\"quoted\"\"\nnext line\"\n"));
    }
}
