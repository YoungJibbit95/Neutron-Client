package me.youngjibbit.utils.font;

import java.util.Arrays;

/** Fixed storage: changing HUD labels cannot allocate map nodes or boxed widths on a miss. */
final class FontTextCache {
    private static final int WAYS = 4, SETS = 64;
    private final String[] keys = new String[SETS * WAYS];
    private final float[] values = new float[keys.length];
    private final long[] used = new long[keys.length];
    private long sequence;
    private int size;

    float get(String key) {
        int start = start(key);
        for (int slot = start; slot < start + WAYS; slot++) {
            if (key.equals(keys[slot])) {
                used[slot] = ++sequence;
                return values[slot];
            }
        }
        return Float.NaN;
    }

    void put(String key, float value) {
        int start = start(key), victim = start;
        for (int slot = start; slot < start + WAYS; slot++) {
            if (keys[slot] == null || key.equals(keys[slot])) { victim = slot; break; }
            if (used[slot] < used[victim]) victim = slot;
        }
        if (keys[victim] == null) size++;
        keys[victim] = key;
        values[victim] = value;
        used[victim] = ++sequence;
    }

    int size() { return size; }

    void clear() {
        Arrays.fill(keys, null);
        Arrays.fill(values, 0);
        Arrays.fill(used, 0);
        sequence = 0;
        size = 0;
    }

    private static int start(String key) {
        int hash = key.hashCode();
        return ((hash ^ (hash >>> 16)) & (SETS - 1)) * WAYS;
    }
}
