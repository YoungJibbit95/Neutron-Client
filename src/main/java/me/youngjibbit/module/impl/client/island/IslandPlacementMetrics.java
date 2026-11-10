package me.youngjibbit.module.impl.client.island;

/** A bounded measured placement rate and history. State transitions never count as clicks. */
public final class IslandPlacementMetrics {
    private static final long SECOND = 1_000_000_000L, SAMPLE = 125_000_000L;
    private static final int HISTORY = 16, CAPACITY = 128;
    private final long[] events = new long[CAPACITY];
    private final float[] history = new float[HISTORY];
    private int start, size;
    private long previousCount = -1, lastNow, lastSample;
    private boolean initialized;

    public static final class Snapshot {
        public final int cps;
        public final boolean known;
        private final float[] history;
        private Snapshot(int cps, boolean known, float[] history) {
            this.cps = cps; this.known = known; this.history = history;
        }
        public int samples() { return history.length; }
        public float sample(int index) { return history[index]; }
        public float ceiling() {
            float peak = 1;
            for (float value : history) peak = Math.max(peak, value);
            return peak;
        }
    }

    public Snapshot sample(long placements, long now) {
        if (placements < 0) { reset(); return new Snapshot(0, false, history.clone()); }
        if (!initialized || now < lastNow || placements < previousCount || now - lastNow > 600_000_000L) {
            reset(); initialized = true; previousCount = placements; lastSample = now;
        }
        long change = Math.min(CAPACITY, placements - previousCount);
        for (int i = 0; i < change; i++) {
            if (size == CAPACITY) { start = (start + 1) % CAPACITY; size--; }
            events[(start + size++) % CAPACITY] = now;
        }
        previousCount = placements; lastNow = now;
        while (size > 0 && now - events[start] >= SECOND) { start = (start + 1) % CAPACITY; size--; }
        long intervals = (now - lastSample) / SAMPLE;
        if (intervals > 0) {
            int move = (int) Math.min(HISTORY, intervals);
            System.arraycopy(history, move, history, 0, HISTORY - move);
            // A render pause does not invent observations in the missing interval.
            java.util.Arrays.fill(history, HISTORY - move, HISTORY, 0);
            history[HISTORY - 1] = size;
            lastSample = now;
        }
        return new Snapshot(size, true, history.clone());
    }

    public void reset() {
        start = size = 0; previousCount = -1; lastNow = lastSample = 0; initialized = false;
        java.util.Arrays.fill(history, 0);
    }
}
