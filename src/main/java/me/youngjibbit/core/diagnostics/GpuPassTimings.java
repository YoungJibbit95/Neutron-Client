package me.youngjibbit.core.diagnostics;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.lwjgl.opengl.ARBTimerQuery;
import org.lwjgl.opengl.ContextCapabilities;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import org.lwjgl.opengl.GLContext;

/**
 * Opt-in, bounded asynchronous GPU timestamp pairs. All methods belong to the
 * starting render thread. Disabled markers/polling call neither GL nor a clock.
 * Timestamp pairs allow nesting and do not occupy GL_TIME_ELAPSED query state.
 * No result is read before both queries report availability, and no wait/flush
 * or glFinish is introduced. Results are GPU command completion intervals, not
 * CPU frame time, presentation latency or an estimate of exclusive GPU work.
 *
 * <p>Use {@code long token = begin("WORLD")} and {@code end(token)} in a finally
 * block; zero is an inert token. Call {@code poll()} once per frame, including
 * after stop, to drain completed pairs. Abort/reset before destroying the GL
 * context. If another context is current, query deletion is deferred: no ID is
 * deleted in the wrong namespace and a new capture cannot allocate another pool
 * until cleanup succeeds in the original context.</p>
 */
public final class GpuPassTimings {
    public static final int MAX_PENDING = 128;
    public static final int MAX_SAMPLES = 131072;
    public static final int DEFAULT_MAX_SAMPLES = MAX_SAMPLES;
    public static final long NONE = 0L;
    public static final GpuPassTimings INSTANCE = new GpuPassTimings();

    public enum State {
        IDLE, RECORDING, DRAINING, STOPPED, CAPACITY_REACHED, ABORTED,
        NO_CONTEXT, UNSUPPORTED, CONTEXT_LOST, ERROR
    }

    private final QueryBackend backend;
    private final int maxPending;
    private volatile Thread owner;
    private volatile Session session;
    private long nextEpoch, tokenSequence;

    public GpuPassTimings() { this(new LwjglQueries(), MAX_PENDING); }

    /** Injectable driver boundary: tests prove scheduling without pretending to measure a GPU. */
    GpuPassTimings(QueryBackend backend, int maxPending) {
        this.backend = Objects.requireNonNull(backend, "backend");
        if (maxPending < 1 || maxPending > MAX_PENDING) throw new IllegalArgumentException("Pending capacity must be 1..128");
        this.maxPending = maxPending;
    }

    /** Start a fresh capture, replacing retained evidence only after old queries are safely deleted. */
    public void start(int maxSamples) {
        requireOwner();
        if (maxSamples < 1 || maxSamples > MAX_SAMPLES) {
            throw new IllegalArgumentException("GPU sample capacity must be 1.." + MAX_SAMPLES);
        }
        Session previous = session;
        if (previous != null) {
            abandonPending(previous);
            if (!deleteQueries(previous)) {
                previous.state = State.CONTEXT_LOST;
                previous.reason = "Start deferred: query cleanup requires the original current GL context";
                return;
            }
        }
        owner = Thread.currentThread();
        Session current = new Session(++nextEpoch, maxSamples);
        session = current;
        try {
            Object context = backend.currentContext();
            if (context == null) {
                current.state = State.NO_CONTEXT;
                current.reason = "No current GL context; no GPU samples measured";
                return;
            }
            String api = backend.supportedApi(context);
            if (api == null) {
                current.state = State.UNSUPPORTED;
                current.reason = "GL33 or ARB_timer_query with query-object support is unavailable";
                return;
            }
            current.api = api;
            current.counterBits = backend.counterBits();
            if (current.counterBits < 1 || current.counterBits > 64) {
                current.state = State.UNSUPPORTED;
                current.reason = "GL_TIMESTAMP has no usable counter bits";
                return;
            }
            current.supported = true;
            current.pool = new QueryPool(context, Math.min(maxSamples, maxPending));
            for (Slot slot : current.pool.slots) {
                slot.startQuery = backend.createQuery();
                if (slot.startQuery == 0) throw new IllegalStateException("GL returned query ID zero");
                slot.endQuery = backend.createQuery();
                if (slot.endQuery == 0) throw new IllegalStateException("GL returned query ID zero");
            }
            current.state = State.RECORDING;
        } catch (RuntimeException error) {
            fail(current, error);
        }
    }

    /**
     * Begin a named pass. Tokens are primitive, unique across pool reuse/captures,
     * and do not retain a session. Saturation drops a marker without overwriting
     * an outstanding pair or any raw result. Names must have 1..64 characters.
     */
    public long begin(String phase) {
        return begin(phase, -1L);
    }

    /** Attach the caller's game-loop sequence; -1 means no frame mapping was supplied. */
    public long begin(String phase, long gameFrameSequence) {
        Session current = session;
        if (current == null || current.state != State.RECORDING) return NONE;
        requireOwner();
        Objects.requireNonNull(phase, "phase");
        if (phase.isEmpty() || phase.length() > 64) throw new IllegalArgumentException("GPU phase must have 1..64 characters");
        if (current.count + current.pending >= current.capacity) {
            current.dropped++;
            current.state = State.CAPACITY_REACHED;
            current.reason = "Raw sample capacity reached; pending pairs continue draining";
            return NONE;
        }
        if (current.pending == current.pool.slots.length) {
            current.dropped++;
            return NONE;
        }
        try {
            if (!hasContext(current)) return NONE;
            Slot[] slots = current.pool.slots;
            int index = current.pool.cursor;
            while (slots[index].token != NONE) index = (index + 1) % slots.length;
            current.pool.cursor = (index + 1) % slots.length;
            Slot slot = slots[index];
            long token = (++tokenSequence << 7) | index;
            if (token == NONE) token = (++tokenSequence << 7) | index;
            backend.timestamp(slot.startQuery);
            slot.token = token;
            slot.sequence = ++current.issued;
            slot.phase = phase;
            slot.frameSequence = gameFrameSequence;
            slot.issuedPoll = current.polls;
            current.pending++;
            if (current.count + current.pending == current.capacity) {
                current.state = State.CAPACITY_REACHED;
                current.reason = "Raw sample capacity reserved; pending pairs continue draining";
            }
            return token;
        } catch (RuntimeException error) {
            fail(current, error);
            return NONE;
        }
    }

    /** End exactly the matching pair, including when stop was requested inside the pass. */
    public void end(long token) {
        if (token == NONE) return;
        Session current = session;
        if (current == null || current.pool == null || current.pending == 0) return;
        requireOwner();
        int index = (int) (token & (MAX_PENDING - 1));
        if (index >= current.pool.slots.length) return;
        Slot slot = current.pool.slots[index];
        if (slot.token != token || slot.ended) return;
        try {
            if (!hasContext(current)) return;
            backend.timestamp(slot.endQuery);
            slot.ended = true;
        } catch (RuntimeException error) {
            fail(current, error);
        }
    }

    /** One bounded nonblocking scan. Open passes remain pending and never get an invented end. */
    public void poll() {
        Session current = session;
        if (current == null || current.pool == null) return;
        requireOwner();
        if (current.state != State.RECORDING && current.state != State.DRAINING
                && current.state != State.CAPACITY_REACHED) {
            deleteQueries(current); // deferred cleanup only in the pool's original context
            return;
        }
        try {
            if (!hasContext(current)) return;
            current.polls++;
            for (Slot slot : current.pool.slots) {
                if (slot.token == NONE || !slot.ended) continue;
                if (!backend.isAvailable(slot.endQuery) || !backend.isAvailable(slot.startQuery)) continue;
                long start = backend.result(slot.startQuery);
                long end = backend.result(slot.endQuery);
                int index = current.count++;
                current.sequence[index] = slot.sequence;
                current.phases[index] = slot.phase;
                current.frames[index] = slot.frameSequence;
                current.issuedPolls[index] = slot.issuedPoll;
                current.resolvedPolls[index] = current.polls;
                current.startNanos[index] = start;
                current.endNanos[index] = end;
                long elapsed = end - start;
                // No correction is guessed for short-counter wrap, resets or invalid ranges.
                if (elapsed < 0 || (current.counterBits < 64 && Long.compareUnsigned(end, start) < 0)) {
                    current.elapsedNanos[index] = -1;
                    current.invalidResults++;
                } else {
                    current.elapsedNanos[index] = elapsed;
                }
                slot.clear();
                current.pending--;
            }
            finishDrain(current);
        } catch (RuntimeException error) {
            fail(current, error);
        }
    }

    /** Stop new markers; callers keep polling while pending > 0. This never waits for a result. */
    public void stop() {
        requireOwner();
        Session current = session;
        if (current == null) return;
        if (current.state == State.RECORDING) {
            current.state = State.DRAINING;
            current.reason = "Stopped explicitly; pending GPU pairs continue draining";
        }
        finishDrain(current);
    }

    /** Retain already resolved samples and visibly discard pending work before a lifecycle change. */
    public void abort(String reason) {
        requireOwner();
        Objects.requireNonNull(reason, "reason");
        Session current = session;
        if (current == null) return;
        if (current.state == State.RECORDING || current.state == State.DRAINING
                || (current.state == State.CAPACITY_REACHED && current.pending > 0)) {
            current.state = State.ABORTED;
            current.reason = bounded(reason);
            abandonPending(current);
        }
        deleteQueries(current);
    }

    /** Discard evidence only after query IDs are deleted in the original context. */
    public void reset() {
        requireOwner();
        Session current = session;
        if (current != null) {
            abandonPending(current);
            if (!deleteQueries(current)) {
                current.state = State.CONTEXT_LOST;
                current.reason = "Reset deferred: query cleanup requires the original current GL context";
                return;
            }
        }
        session = null;
        owner = null;
    }

    public boolean isRecording() {
        Session current = session;
        return current != null && current.state == State.RECORDING;
    }

    /** Immutable owned copy, sorted by issue sequence. No GL call occurs during snapshot/export. */
    public Snapshot snapshot() {
        requireOwner();
        Session current = session;
        if (current == null) return new Snapshot(nextEpoch);
        List<Sample> rows = new ArrayList<>(current.count);
        for (int i = 0; i < current.count; i++) {
            rows.add(new Sample(current.sequence[i], current.frames[i], current.phases[i], current.issuedPolls[i],
                    current.resolvedPolls[i], current.startNanos[i], current.endNanos[i], current.elapsedNanos[i]));
        }
        rows.sort(Comparator.comparingLong(row -> row.sequence));
        return new Snapshot(current, rows);
    }

    private boolean hasContext(Session current) {
        if (backend.currentContext() == current.pool.context) return true;
        current.state = State.CONTEXT_LOST;
        current.reason = "Current GL context changed; pending samples discarded and original query IDs retained for cleanup";
        abandonPending(current);
        return false;
    }

    private void finishDrain(Session current) {
        if (current.pending != 0 || (current.state != State.DRAINING && current.state != State.CAPACITY_REACHED)) return;
        if (deleteQueries(current) && current.state == State.DRAINING) current.state = State.STOPPED;
    }

    private void abandonPending(Session current) {
        current.abandoned += current.pending;
        current.pending = 0;
        if (current.pool != null) for (Slot slot : current.pool.slots) slot.clear();
    }

    /** Never deletes an integer query name against a different/unbound GL context. */
    private boolean deleteQueries(Session current) {
        QueryPool pool = current.pool;
        if (pool == null) return true;
        try {
            if (backend.currentContext() != pool.context) return false;
            for (Slot slot : pool.slots) {
                if (slot.startQuery != 0) { backend.deleteQuery(slot.startQuery); slot.startQuery = 0; }
                if (slot.endQuery != 0) { backend.deleteQuery(slot.endQuery); slot.endQuery = 0; }
            }
            current.pool = null;
            return true;
        } catch (RuntimeException error) {
            current.state = State.ERROR;
            current.reason = bounded("Query cleanup failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return false;
        }
    }

    private void fail(Session current, RuntimeException error) {
        current.state = State.ERROR;
        current.reason = bounded("GPU measurement failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
        abandonPending(current);
        deleteQueries(current);
    }

    private void requireOwner() {
        if (owner != null && Thread.currentThread() != owner) {
            throw new IllegalStateException("GPU capture belongs to its starting render thread");
        }
    }

    private static String bounded(String value) { return value.length() <= 512 ? value : value.substring(0, 512); }

    interface QueryBackend {
        Object currentContext();
        String supportedApi(Object context);
        int counterBits();
        int createQuery();
        void deleteQuery(int query);
        void timestamp(int query);
        boolean isAvailable(int query);
        long result(int query);
    }

    private static final class LwjglQueries implements QueryBackend {
        private boolean arb;
        public Object currentContext() {
            try { return GLContext.getCapabilities(); }
            // LWJGL 2 throws RuntimeException (not IllegalStateException) when unbound.
            catch (RuntimeException noContext) { return null; }
        }
        public String supportedApi(Object context) {
            ContextCapabilities capabilities = (ContextCapabilities) context;
            if (!capabilities.OpenGL15) return null;
            arb = !capabilities.OpenGL33;
            return capabilities.OpenGL33 ? "GL33" : capabilities.GL_ARB_timer_query ? "ARB_timer_query" : null;
        }
        public int counterBits() { return GL15.glGetQueryi(GL33.GL_TIMESTAMP, GL15.GL_QUERY_COUNTER_BITS); }
        public int createQuery() { return GL15.glGenQueries(); }
        public void deleteQuery(int query) { GL15.glDeleteQueries(query); }
        public void timestamp(int query) {
            if (arb) ARBTimerQuery.glQueryCounter(query, ARBTimerQuery.GL_TIMESTAMP);
            else GL33.glQueryCounter(query, GL33.GL_TIMESTAMP);
        }
        public boolean isAvailable(int query) {
            return GL15.glGetQueryObjecti(query, GL15.GL_QUERY_RESULT_AVAILABLE) != 0;
        }
        public long result(int query) {
            return arb ? ARBTimerQuery.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT)
                    : GL33.glGetQueryObjectui64(query, GL15.GL_QUERY_RESULT);
        }
    }

    private static final class QueryPool {
        final Object context;
        final Slot[] slots;
        int cursor;
        QueryPool(Object context, int capacity) {
            this.context = context;
            slots = new Slot[capacity];
            for (int i = 0; i < capacity; i++) slots[i] = new Slot();
        }
    }

    private static final class Slot {
        int startQuery, endQuery;
        long token, sequence, frameSequence, issuedPoll;
        String phase;
        boolean ended;
        void clear() { token = NONE; phase = null; ended = false; }
    }

    private static final class Session {
        final long epoch;
        final int capacity;
        final long[] sequence, frames, issuedPolls, resolvedPolls, startNanos, endNanos, elapsedNanos;
        final String[] phases;
        State state = State.IDLE;
        String reason = "", api = "NONE";
        boolean supported;
        int counterBits, count, pending;
        long polls, issued, dropped, abandoned, invalidResults;
        QueryPool pool;
        Session(long epoch, int capacity) {
            this.epoch = epoch;
            this.capacity = capacity;
            sequence = new long[capacity]; frames = new long[capacity];
            issuedPolls = new long[capacity]; resolvedPolls = new long[capacity];
            startNanos = new long[capacity]; endNanos = new long[capacity]; elapsedNanos = new long[capacity];
            phases = new String[capacity];
        }
    }

    public static final class Sample {
        public final long sequence, frameSequence, issuedPoll, resolvedPoll, startNanos, endNanos, elapsedNanos;
        public final String phase;
        public final boolean valid;
        private Sample(long sequence, long frameSequence, String phase, long issuedPoll, long resolvedPoll,
                long startNanos, long endNanos, long elapsedNanos) {
            this.sequence = sequence; this.frameSequence = frameSequence;
            this.phase = phase; this.issuedPoll = issuedPoll; this.resolvedPoll = resolvedPoll;
            this.startNanos = startNanos; this.endNanos = endNanos; this.elapsedNanos = elapsedNanos;
            valid = elapsedNanos >= 0;
        }
    }

    public static final class Snapshot {
        public final long epoch, polls, issued, dropped, abandoned, invalidResults;
        public final State state;
        public final String reason, api;
        public final boolean supported;
        public final int capacity, pending, queryObjectsRemaining, counterBits;
        public final List<Sample> samples;
        private Snapshot(long epoch) {
            this.epoch = epoch;
            polls = issued = dropped = abandoned = invalidResults = 0;
            state = State.IDLE; reason = ""; api = "NONE"; supported = false;
            capacity = pending = queryObjectsRemaining = counterBits = 0;
            samples = Collections.emptyList();
        }
        private Snapshot(Session current, List<Sample> samples) {
            epoch = current.epoch; polls = current.polls; issued = current.issued; dropped = current.dropped;
            abandoned = current.abandoned; invalidResults = current.invalidResults;
            state = current.state; reason = current.reason; api = current.api; supported = current.supported;
            capacity = current.capacity; pending = current.pending; counterBits = current.counterBits;
            int remaining = 0;
            if (current.pool != null) for (Slot slot : current.pool.slots) {
                if (slot.startQuery != 0) remaining++;
                if (slot.endQuery != 0) remaining++;
            }
            queryObjectsRemaining = remaining;
            this.samples = Collections.unmodifiableList(samples);
        }

        /** Raw unsigned GPU timestamps; blank elapsed time explicitly marks an invalid interval. */
        public void writeCsv(Writer out) throws IOException {
            Objects.requireNonNull(out, "out");
            out.write("epoch,sequence,game_frame_sequence,phase,issued_poll,resolved_poll,gpu_start_ns_unsigned,gpu_end_ns_unsigned,gpu_elapsed_ns,status\n");
            for (Sample row : samples) {
                out.write(Long.toString(epoch)); out.write(','); out.write(Long.toString(row.sequence)); out.write(',');
                out.write(Long.toString(row.frameSequence)); out.write(',');
                out.write('"'); out.write(row.phase.replace("\"", "\"\"")); out.write("\",");
                out.write(Long.toString(row.issuedPoll)); out.write(','); out.write(Long.toString(row.resolvedPoll)); out.write(',');
                out.write(Long.toUnsignedString(row.startNanos)); out.write(','); out.write(Long.toUnsignedString(row.endNanos)); out.write(',');
                if (row.valid) out.write(Long.toString(row.elapsedNanos));
                out.write(row.valid ? ",VALID\n" : ",COUNTER_WRAP_OR_RESET\n");
            }
        }
    }
}
