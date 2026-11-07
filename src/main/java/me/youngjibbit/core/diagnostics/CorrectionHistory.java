package me.youngjibbit.core.diagnostics;

import java.util.*;

/** Bounded scalar evidence. A correction is observable; a server check, violation level or cause is not. */
public final class CorrectionHistory {
    private final Deque<Entry> entries = new ArrayDeque<>();
    private long sequence;
    public void clear() { entries.clear(); sequence = 0; }
    public Entry latest() { return entries.peekFirst(); }
    public List<Entry> snapshot() { return Collections.unmodifiableList(new ArrayList<>(entries)); }
    public Entry record(long now, double distance, float angle, boolean initialization, String context, String values, String advice) {
        return record(now,distance,angle,initialization,false,context,values,advice);
    }
    public Entry record(long now, double distance, float angle, boolean initialization, boolean replay, String context, String values, String advice) {
        if (!Double.isFinite(distance) || !Float.isFinite(angle)) return null;
        boolean possibleSetback = !initialization && !replay && (distance > .05 || angle > 2);
        Entry entry = new Entry(++sequence, now, distance, angle, possibleSetback, initialization, replay, context, values, advice);
        if (entries.size() == 24) entries.removeLast(); entries.addFirst(entry); return entry;
    }
    public static final class Entry {
        public final long id, nanos;
        public final double distance;
        public final float angle;
        public final boolean possibleSetback, initialization, replay;
        public final String context, values, advice;
        Entry(long id, long nanos, double distance, float angle, boolean possible, boolean initialization, boolean replay, String context, String values, String advice) {
            this.id = id; this.nanos = nanos; this.distance = distance; this.angle = angle;
            possibleSetback = possible; this.initialization = initialization; this.replay=replay; this.context = context; this.values = values; this.advice = advice;
        }
        public String kind() { return replay ? "Silent / replay application" : initialization ? "World synchronization" : possibleSetback ? "Possible setback" : "Minor correction"; }
    }
}
