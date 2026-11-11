package me.youngjibbit.core.diagnostics;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Opt-in bounded observations of client chunk work, never server/network latency.
 * Lifecycle, data handoff and task creation belong to the starting client thread;
 * each returned trace accepts worker/render-thread events safely. A null trace
 * means diagnostics were disabled or the explicit capacity was reached.
 *
 * <pre>
 * metrics.start(8192);
 * metrics.dataAvailable(chunkX, chunkZ); // after decoded data reaches the client thread
 * Trace trace = metrics.beginTask(chunkX, sectionY, chunkZ, "REBUILD_CHUNK", queueDepth);
 * if (trace != null) trace.meshStarted();
 * </pre>
 * All event times are nanoseconds relative to this capture's start; -1 means
 * unobserved. First draw is CPU draw submission, not GPU completion or display.
 * An epoch isolates observation sessions; it is not an immutable world snapshot.
 */
public final class ChunkPipelineMetrics {
    public static final int DEFAULT_MAX_TRACES = 8192;
    public static final int MAX_TRACES = 65536;
    public static final int MAX_DATA_COLUMNS = 8192;
    public static final long UNOBSERVED = -1L;
    public static final ChunkPipelineMetrics INSTANCE = new ChunkPipelineMetrics(System::nanoTime);

    public enum State { IDLE, RECORDING, STOPPED, ABORTED }
    public enum Outcome { QUEUED, MESHING, MESHED, UPLOADING, UPLOADED, DRAW_SUBMITTED, CANCELLED, FAILED }

    private final LongSupplier clock;
    private volatile Thread owner;
    private volatile Session session;
    private long nextEpoch;

    /** Construct without reading the injectable monotonic clock. */
    public ChunkPipelineMetrics(LongSupplier clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Begin a fresh capture; old traces cannot contribute to this new epoch. */
    public void start(int maxTraces) {
        requireOwner();
        if (maxTraces < 1 || maxTraces > MAX_TRACES) {
            throw new IllegalArgumentException("Trace capacity must be between 1 and " + MAX_TRACES);
        }
        Session replacement = new Session(new Epoch(nextEpoch + 1, clock, clock.getAsLong()), maxTraces);
        Session previous = session;
        if (previous != null) previous.epoch.active = false;
        owner = Thread.currentThread();
        nextEpoch++;
        session = replacement;
    }

    /** Freeze the capture, retaining completed and incomplete tasks for export. */
    public void stop() {
        finish(State.STOPPED, "Stopped explicitly; pending tasks remain incomplete");
    }

    /** Freeze and retain evidence when the world/resources/configuration invalidate a comparison. */
    public void abort(String reason) {
        finish(State.ABORTED, boundedReason(reason));
    }

    private void finish(State state, String reason) {
        requireOwner();
        Session current = session;
        if (current != null && current.epoch.active) {
            current.epoch.active = false;
            current.state = state;
            current.reason = reason;
        }
    }

    /** Explicitly discard the retained capture; old trace handles stay inert. */
    public void reset() {
        requireOwner();
        Session current = session;
        if (current != null) current.epoch.active = false;
        session = null;
        owner = null;
    }

    public boolean isRecording() {
        Session current = session;
        return current != null && current.epoch.active;
    }

    /**
     * Forget pending associations when a column unloads or is replaced in full.
     * Call before registering the replacement packet's actual section mask;
     * omitted sections then cannot inherit an earlier packet. Reads no clock.
     * Existing traces retain their original observation, not a world revision.
     */
    public void forgetColumn(int chunkX, int chunkZ) {
        Session current = session;
        if (current == null || !current.epoch.active) return;
        requireOwner();
        current.data.remove(columnKey(chunkX, chunkZ));
    }

    /**
     * Observe a decoded-data handoff affecting all 16 sections in a chunk column.
     * Use the section-mask overload when the decoded packet exposes its mask.
     */
    public void dataAvailable(int chunkX, int chunkZ) {
        dataAvailable(chunkX, chunkZ, 0xffff);
    }

    /**
     * Observe decoded sections (bits 0..15) after client-thread packet handoff.
     * Only rebuilds of those sections can inherit the observation, until a matching
     * mesh first reaches main-camera draw submission. Cancelled/failed attempts
     * retain the association for retries. Coordinates are chunk indices, not block
     * positions. This measures neither server generation nor network latency and
     * does not prove that no later local edit occurred.
     */
    public void dataAvailable(int chunkX, int chunkZ, int sectionMask) {
        Session current = session;
        if (current == null || !current.epoch.active) return;
        requireOwner();
        if ((sectionMask & ~0xffff) != 0) {
            throw new IllegalArgumentException("Section mask must contain only bits 0..15");
        }
        if (sectionMask == 0) return;
        long key = columnKey(chunkX, chunkZ);
        DataMark mark = new DataMark(++current.dataSequence, current.epoch.now(), sectionMask);
        // Reinsert an updated column as newest, keeping eviction tied to handoff age.
        ColumnData column = current.data.remove(key);
        if (column == null) column = new ColumnData();
        for (int section = 0; section < 16; section++) {
            if ((sectionMask & (1 << section)) != 0) column.sections[section] = mark;
        }
        current.data.put(key, column);
        if (current.data.size() > current.maxDataColumns) {
            current.data.remove(current.data.keySet().iterator().next());
            current.evictedDataColumns++;
        }
    }

    /**
     * Create one task trace. x/z and y are chunk/section indices (block origin >> 4).
     * Kind identifies rebuild versus transparency work. Queue depth is an observed
     * queue size, not a scheduling prediction. Disabled/saturated capture reads no clock.
     */
    public Trace beginTask(int chunkX, int sectionY, int chunkZ, String kind, int compileQueueDepth) {
        Session current = session;
        if (current == null || !current.epoch.active) return null;
        requireOwner();
        Objects.requireNonNull(kind, "kind");
        if (kind.isEmpty() || kind.length() > 64 || compileQueueDepth < 0) {
            throw new IllegalArgumentException("Task kind must have 1..64 characters and queue depth be non-negative");
        }
        long sequence = ++current.taskSequence;
        if (current.count == current.traces.length) {
            current.droppedTasks++;
            return null;
        }
        DataMark data = null;
        if ("REBUILD_CHUNK".equals(kind) && sectionY >= 0 && sectionY < 16) {
            ColumnData column = current.data.get(columnKey(chunkX, chunkZ));
            if (column != null) {
                DataMark candidate = column.sections[sectionY];
                if (candidate != null && candidate.isPending(sectionY)) data = candidate;
            }
        }
        Trace trace = new Trace(current.epoch, sequence, chunkX, sectionY, chunkZ, kind,
                compileQueueDepth, data, current.epoch.now());
        current.traces[current.count++] = trace;
        return trace;
    }

    /** Copy all retained tasks on explicit request. Snapshot/export are outside the hot path. */
    public Snapshot snapshot() {
        requireOwner();
        Session current = session;
        if (current == null) return Snapshot.empty(nextEpoch);
        List<TraceSnapshot> rows = new ArrayList<>(current.count);
        for (int i = 0; i < current.count; i++) rows.add(current.traces[i].snapshot());
        return new Snapshot(current.epoch.id, current.state, current.reason, current.traces.length,
                current.maxDataColumns, current.taskSequence, current.dataSequence, current.droppedTasks,
                current.evictedDataColumns, current.epoch.invalidClockEvents.get(), rows);
    }

    private void requireOwner() {
        if (owner != null && Thread.currentThread() != owner) {
            throw new IllegalStateException("Chunk capture lifecycle belongs to its starting thread");
        }
    }

    private static long columnKey(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xffffffffL);
    }

    private static String boundedReason(String value) {
        Objects.requireNonNull(value, "reason");
        return value.length() <= 256 ? value : value.substring(0, 256);
    }

    /** No reference back to Session: a trace on a resident mesh must not retain every capture row. */
    private static final class Epoch {
        final long id, originNanos;
        final LongSupplier clock;
        final AtomicLong invalidClockEvents = new AtomicLong();
        volatile boolean active = true;

        Epoch(long id, LongSupplier clock, long originNanos) {
            this.id = id;
            this.clock = clock;
            this.originNanos = originNanos;
        }

        long now() {
            long elapsed = clock.getAsLong() - originNanos;
            if (elapsed < 0) {
                invalidClockEvents.incrementAndGet();
                return UNOBSERVED;
            }
            return elapsed;
        }
    }

    private static final class Session {
        final Epoch epoch;
        final Trace[] traces;
        final int maxDataColumns;
        final Map<Long, ColumnData> data = new LinkedHashMap<>();
        State state = State.RECORDING;
        String reason = "";
        int count;
        long taskSequence, dataSequence, droppedTasks, evictedDataColumns;

        Session(Epoch epoch, int capacity) {
            this.epoch = epoch;
            traces = new Trace[capacity];
            maxDataColumns = Math.min(capacity, MAX_DATA_COLUMNS);
        }
    }

    private static final class ColumnData {
        final DataMark[] sections = new DataMark[16];
    }

    /** Shared only by affected sections/traces, with no reference to the column map or session. */
    private static final class DataMark {
        final long sequence, nanos;
        final AtomicInteger pendingSections;

        DataMark(long sequence, long nanos, int sectionMask) {
            this.sequence = sequence;
            this.nanos = nanos;
            pendingSections = new AtomicInteger(sectionMask);
        }

        boolean isPending(int sectionY) {
            return (pendingSections.get() & (1 << sectionY)) != 0;
        }

        void drawn(int sectionY) {
            int previous, replacement;
            do {
                previous = pendingSections.get();
                replacement = previous & ~(1 << sectionY);
                if (previous == replacement) return;
            } while (!pendingSections.compareAndSet(previous, replacement));
        }
    }

    /**
     * Bounded per-task observer. Events are idempotent where a stage happens once;
     * uploaded(bytes) adds each actual upload once and records the latest upload end.
     * Worker callbacks must not call uploaded twice for the same data transfer.
     */
    public static final class Trace {
        public final long epoch, sequence;
        private final Epoch capture;
        private final DataMark dataMark;
        private final int chunkX, sectionY, chunkZ, compileQueueDepth;
        private final String kind;
        private final long columnDataSequence, dataAvailableNanos, queuedNanos;
        private volatile long meshStartNanos = UNOBSERVED, meshEndNanos = UNOBSERVED;
        private volatile long uploadStartNanos = UNOBSERVED, uploadEndNanos = UNOBSERVED;
        private volatile long firstDrawSubmitNanos = UNOBSERVED, finishedNanos = UNOBSERVED, uploadedBytes;
        private volatile boolean uploadByteCountOverflow;
        private volatile Outcome outcome = Outcome.QUEUED;
        private volatile String reason = "";

        private Trace(Epoch capture, long sequence, int chunkX, int sectionY, int chunkZ,
                      String kind, int queueDepth, DataMark data, long queuedNanos) {
            this.capture = capture;
            epoch = capture.id;
            this.sequence = sequence;
            this.chunkX = chunkX;
            this.sectionY = sectionY;
            this.chunkZ = chunkZ;
            this.kind = kind;
            compileQueueDepth = queueDepth;
            dataMark = data;
            columnDataSequence = data == null ? UNOBSERVED : data.sequence;
            dataAvailableNanos = data == null ? UNOBSERVED : data.nanos;
            this.queuedNanos = queuedNanos;
        }

        public synchronized void meshStarted() {
            if (!accept() || meshStartNanos != UNOBSERVED) return;
            meshStartNanos = capture.now();
            progress(Outcome.MESHING);
        }

        public synchronized void meshed() {
            if (!accept() || meshEndNanos != UNOBSERVED) return;
            meshEndNanos = capture.now();
            progress(Outcome.MESHED);
        }

        public synchronized void uploadStarted() {
            if (!accept() || uploadStartNanos != UNOBSERVED) return;
            uploadStartNanos = capture.now();
            progress(Outcome.UPLOADING);
        }

        public synchronized void uploaded(long bytes) {
            if (!accept()) return;
            if (bytes < 0) throw new IllegalArgumentException("Uploaded byte count must be non-negative");
            if (uploadedBytes > Long.MAX_VALUE - bytes) {
                uploadedBytes = Long.MAX_VALUE;
                uploadByteCountOverflow = true;
            } else {
                uploadedBytes += bytes;
            }
            uploadEndNanos = capture.now();
            progress(Outcome.UPLOADED);
        }

        /** Only the first actual CPU draw submission is retained, without waiting for the GPU. */
        public void drawn() {
            if (!capture.active || firstDrawSubmitNanos != UNOBSERVED) return;
            synchronized (this) {
                if (!accept()) return;
                firstDrawSubmitNanos = finishedNanos = capture.now();
                outcome = Outcome.DRAW_SUBMITTED;
                // Retire this exact handoff only. A newer packet's mark remains pending.
                if (dataMark != null) dataMark.drawn(sectionY);
            }
        }

        public void cancelled() { cancelled("Task cancelled"); }
        public void failed() { failed("Task failed"); }

        public synchronized void cancelled(String reason) {
            terminal(Outcome.CANCELLED, reason);
        }

        public synchronized void failed(String reason) {
            terminal(Outcome.FAILED, reason);
        }

        private void terminal(Outcome result, String detail) {
            if (!accept()) return;
            reason = boundedReason(detail);
            finishedNanos = capture.now();
            outcome = result;
        }

        private boolean accept() {
            return capture.active && outcome.ordinal() < Outcome.DRAW_SUBMITTED.ordinal();
        }

        private void progress(Outcome stage) {
            if (stage.ordinal() > outcome.ordinal()) outcome = stage;
        }

        private synchronized TraceSnapshot snapshot() {
            return new TraceSnapshot(this);
        }
    }

    /** Immutable raw evidence; unobserved timestamps remain -1 rather than fabricated zero durations. */
    public static final class TraceSnapshot {
        public final long epoch, sequence, columnDataSequence;
        public final int chunkX, sectionY, chunkZ, compileQueueDepth;
        public final String kind;
        public final long dataAvailableNanos, queuedNanos, meshStartNanos, meshEndNanos;
        public final long uploadStartNanos, uploadEndNanos, firstDrawSubmitNanos, finishedNanos, uploadedBytes;
        public final boolean uploadByteCountOverflow;
        public final Outcome outcome;
        public final String reason;

        private TraceSnapshot(Trace trace) {
            epoch = trace.epoch;
            sequence = trace.sequence;
            chunkX = trace.chunkX;
            sectionY = trace.sectionY;
            chunkZ = trace.chunkZ;
            compileQueueDepth = trace.compileQueueDepth;
            kind = trace.kind;
            columnDataSequence = trace.columnDataSequence;
            dataAvailableNanos = trace.dataAvailableNanos;
            queuedNanos = trace.queuedNanos;
            meshStartNanos = trace.meshStartNanos;
            meshEndNanos = trace.meshEndNanos;
            uploadStartNanos = trace.uploadStartNanos;
            uploadEndNanos = trace.uploadEndNanos;
            firstDrawSubmitNanos = trace.firstDrawSubmitNanos;
            finishedNanos = trace.finishedNanos;
            uploadedBytes = trace.uploadedBytes;
            uploadByteCountOverflow = trace.uploadByteCountOverflow;
            outcome = trace.outcome;
            reason = trace.reason;
        }
    }

    public static final class Snapshot {
        public final long epoch;
        public final State state;
        public final String reason;
        public final int capacity, maxDataColumns;
        public final long taskRequests, dataUpdates, droppedTasks, evictedDataColumns, invalidClockEvents;
        public final List<TraceSnapshot> traces;

        private Snapshot(long epoch, State state, String reason, int capacity, int maxDataColumns,
                         long taskRequests, long dataUpdates, long droppedTasks, long evictedDataColumns,
                         long invalidClockEvents, List<TraceSnapshot> traces) {
            this.epoch = epoch;
            this.state = state;
            this.reason = reason;
            this.capacity = capacity;
            this.maxDataColumns = maxDataColumns;
            this.taskRequests = taskRequests;
            this.dataUpdates = dataUpdates;
            this.droppedTasks = droppedTasks;
            this.evictedDataColumns = evictedDataColumns;
            this.invalidClockEvents = invalidClockEvents;
            this.traces = Collections.unmodifiableList(traces);
        }

        private static Snapshot empty(long epoch) {
            return new Snapshot(epoch, State.IDLE, "", 0, 0, 0, 0, 0, 0, 0, Collections.emptyList());
        }

        /** Write complete ordered raw rows; the caller closes the writer and retains capture provenance. */
        public void writeCsv(Writer writer) throws IOException {
            Objects.requireNonNull(writer, "writer");
            writer.write("epoch,sequence,chunk_x,section_y,chunk_z,kind,column_data_sequence,data_available_ns,queued_ns,mesh_start_ns,mesh_end_ns,upload_start_ns,upload_end_ns,first_draw_submit_ns,finished_ns,uploaded_bytes,upload_byte_count_overflow,compile_queue_depth,outcome,reason\n");
            for (TraceSnapshot row : traces) {
                writer.write(row.epoch + "," + row.sequence + "," + row.chunkX + "," + row.sectionY + "," + row.chunkZ + ",");
                csvText(writer, row.kind);
                writer.write("," + row.columnDataSequence + "," + row.dataAvailableNanos + "," + row.queuedNanos + "," + row.meshStartNanos + "," + row.meshEndNanos + "," + row.uploadStartNanos + "," + row.uploadEndNanos + "," + row.firstDrawSubmitNanos + "," + row.finishedNanos + "," + row.uploadedBytes + "," + row.uploadByteCountOverflow + "," + row.compileQueueDepth + "," + row.outcome.name() + ",");
                csvText(writer, row.reason);
                writer.write('\n');
            }
        }

        private static void csvText(Writer writer, String value) throws IOException {
            writer.write('"');
            writer.write(value.replace("\"", "\"\""));
            writer.write('"');
        }
    }
}
