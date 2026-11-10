package me.youngjibbit.core.activity;

import java.lang.ref.WeakReference;

/** Read-only input telemetry: one second of actual in-game mouse button-down events. */
public final class InputClickJournal {
    private static final InputClickJournal GLOBAL = new InputClickJournal();
    private static final int CAPACITY = 256;
    private static final long WINDOW = 1_000_000_000L;
    private final long[] times = new long[CAPACITY];
    private final int[] buttons = new int[CAPACITY];
    private WeakReference<Object> scene = new WeakReference<>(null);
    private int start, size;
    private long lastNow;
    private boolean clockSet;

    public static InputClickJournal global() { return GLOBAL; }

    public static final class Snapshot {
        public final int left, right;
        private Snapshot(int left, int right) { this.left = left; this.right = right; }
        public int total() { return left + right; }
    }

    /** Called by the existing mouse event drain, never by a renderer or an attack scheduler. */
    public synchronized void observe(Object world, boolean inGame, int button, boolean pressed, long now) {
        if (!prepare(world, inGame, now) || !pressed || button < 0 || button > 1) return;
        if (size == CAPACITY) { start = (start + 1) % CAPACITY; size--; }
        int at = (start + size++) % CAPACITY;
        times[at] = now; buttons[at] = button;
    }

    public synchronized Snapshot snapshot(Object world, boolean inGame, long now) {
        if (!prepare(world, inGame, now)) return new Snapshot(0, 0);
        int left = 0, right = 0;
        for (int i = 0; i < size; i++) {
            if (buttons[(start + i) % CAPACITY] == 0) left++; else right++;
        }
        return new Snapshot(left, right);
    }

    private boolean prepare(Object world, boolean inGame, long now) {
        if (!inGame || world == null) { reset(); return false; }
        if (scene.get() != world || clockSet && now < lastNow) {
            reset(); scene = new WeakReference<>(world);
        }
        lastNow = now; clockSet = true;
        while (size > 0 && now - times[start] >= WINDOW) { start = (start + 1) % CAPACITY; size--; }
        return true;
    }

    public synchronized void reset() {
        scene.clear(); start = size = 0; lastNow = 0; clockSet = false;
    }
}
