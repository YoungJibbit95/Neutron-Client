package me.youngjibbit.core.diagnostics;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Bounded scalar observations; no packets, entities, callbacks or gameplay authority. Client thread only. */
public final class NetworkResearchTrace {
    public enum Mode { BACKTRACK, LAGRANGE, TICKBASE }
    public enum Origin { CLIENT_OBSERVATION, SIMULATION, SERVER_EVIDENCE }
    public enum Kind { RECEIVED, APPLIED, LOCAL_ACCEPTED, ENTITY, TICK, QUEUE, CORRECTION, SERVER_FLAG }
    private static final Gson JSON = new Gson();
    private final int capacity;
    private final ArrayDeque<Entry> entries = new ArrayDeque<>();
    private Mode mode = Mode.BACKTRACK;
    private Origin origin = Origin.CLIENT_OBSERVATION;
    private long run, sequence, overwritten, rejected, lastTick;
    private boolean tickSeen;

    public NetworkResearchTrace(int capacity) {
        if (capacity < 1 || capacity > 4096) throw new IllegalArgumentException("capacity must be 1..4096");
        this.capacity = capacity;
    }

    public void startRun(Mode mode, Origin origin) {
        if (origin == Origin.SERVER_EVIDENCE) throw new IllegalArgumentException("server evidence needs an explicit marker");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.origin = Objects.requireNonNull(origin, "origin");
        run++; sequence = overwritten = rejected = 0; tickSeen = false; lastTick = 0; entries.clear();
    }

    public void packet(long now, Kind kind, String type) {
        if (kind != Kind.RECEIVED && kind != Kind.APPLIED && kind != Kind.LOCAL_ACCEPTED && kind != Kind.CORRECTION)
            throw new IllegalArgumentException("packet observation kind required");
        append(now, kind, origin, type, -1, 0, 0, 0, 0, 0, 0, 0, 0, "");
    }

    public void tick(long now, int executedSteps, boolean withheld) {
        if (executedSteps < -1) { rejected++; return; }
        long interval = tickSeen ? now - lastTick : 0;
        if (tickSeen && interval < 0) { rejected++; tickSeen = false; return; }
        append(now, Kind.TICK, origin, "", -1, 0, 0, 0, 0, 0, 0,
                interval, tickSeen ? interval - 50_000_000L : 0,
                "steps=" + executedSteps + ";withheld=" + withheld + ";baseline=" + !tickSeen);
        lastTick = now; tickSeen = true;
    }

    /** Receipt coordinates and rendered coordinates are different observations, neither is current server truth. */
    public void entity(long now, int id, double x, double y, double z, double renderedX, double renderedY, double renderedZ) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(renderedX) || !Double.isFinite(renderedY) || !Double.isFinite(renderedZ)) {
            rejected++; return;
        }
        append(now, Kind.ENTITY, origin, "", id, x, y, z, renderedX, renderedY, renderedZ, 0, 0, "");
    }

    public void queue(long now, int inbound, int outbound, long oldestMs) {
        if (inbound < 0 || outbound < 0 || oldestMs < 0) { rejected++; return; }
        append(now, Kind.QUEUE, origin, "", -1, 0, 0, 0, 0, 0, 0, 0, 0,
                "inbound=" + inbound + ";outbound=" + outbound + ";oldestAgeMs=" + oldestMs);
    }

    /** Caller supplies a reference to an actual server log; a correction/chat message never creates a flag. */
    public void serverFlag(long now, String evidenceReference, String check) {
        if (evidenceReference == null || evidenceReference.trim().isEmpty() || check == null || check.trim().isEmpty())
            throw new IllegalArgumentException("server log reference and check label required");
        append(now, Kind.SERVER_FLAG, Origin.SERVER_EVIDENCE, check, -1, 0, 0, 0, 0, 0, 0, 0, 0, evidenceReference);
    }

    private void append(long now, Kind kind, Origin source, String type, int entity, double x, double y, double z,
                        double rx, double ry, double rz, long interval, long deviation, String detail) {
        Entry entry = new Entry(run, ++sequence, now, mode, source, kind, bounded(type), entity,
                x, y, z, rx, ry, rz, interval, deviation, bounded(detail));
        if (entries.size() == capacity) { entries.removeFirst(); overwritten++; }
        entries.addLast(entry);
    }

    private static String bounded(String text) {
        if (text == null) return "";
        return text.substring(0, Math.min(160, text.length())).replace('\n', ' ').replace('\r', ' ');
    }

    public List<Entry> snapshot() { return Collections.unmodifiableList(new ArrayList<>(entries)); }
    /** Explicit export boundary. Preserve loss counters alongside the immutable retained samples. */
    public NetworkResearchRecording recording(long captureFailures, String scenario, long simulationRejected) {
        return new NetworkResearchRecording(run, mode, origin, capacity, sequence, overwritten, rejected,
                captureFailures, scenario, simulationRejected, snapshot());
    }
    /** Exports a bounded snapshot; caller chooses the destination. No per-packet disk IO. */
    public void writeJsonLines(Writer destination) throws IOException {
        for (Entry entry : snapshot()) { destination.write(JSON.toJson(entry)); destination.write('\n'); }
    }
    public long getRun() { return run; }
    public long getOverwritten() { return overwritten; }
    public long getRejected() { return rejected; }

    public static final class Entry {
        public final long run, sequence, nanos;
        public final Mode mode;
        public final Origin origin;
        public final Kind kind;
        public final String packetType, detail;
        public final int entityId;
        public final double x, y, z, renderedX, renderedY, renderedZ;
        public final long intervalNanos, deviationNanos;
        Entry(long run, long sequence, long nanos, Mode mode, Origin origin, Kind kind, String type, int id,
                      double x, double y, double z, double rx, double ry, double rz, long interval, long deviation, String detail) {
            this.run=run; this.sequence=sequence; this.nanos=nanos; this.mode=mode; this.origin=origin; this.kind=kind;
            packetType=type; entityId=id; this.x=x; this.y=y; this.z=z; renderedX=rx; renderedY=ry; renderedZ=rz;
            intervalNanos=interval; deviationNanos=deviation; this.detail=detail;
        }
    }
}
