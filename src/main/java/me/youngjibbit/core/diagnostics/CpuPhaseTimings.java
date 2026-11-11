package me.youngjibbit.core.diagnostics;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Bounded raw main-thread wall times, including driver submission, never GPU completion. */
public final class CpuPhaseTimings {
    public enum Phase { WORLD, HUD, GUI }
    private final LongSupplier clock;
    private Thread owner;
    private boolean recording;
    private long[] durations = new long[0], frames = new long[0];
    private byte[] phases = new byte[0];
    private int count;
    private long dropped, invalid;

    public CpuPhaseTimings(LongSupplier clock) { this.clock = Objects.requireNonNull(clock, "clock"); }

    public void start(int capacity) {
        requireOwner();
        if (capacity < 1 || capacity > 3_000_000) throw new IllegalArgumentException("Invalid CPU sample capacity");
        owner = Thread.currentThread();
        durations = new long[capacity]; frames = new long[capacity]; phases = new byte[capacity];
        count = 0; dropped = invalid = 0; recording = true;
    }

    /** Long.MIN_VALUE is a disabled token; valid nanoTime values may be negative. */
    public long begin() { return recording && Thread.currentThread() == owner ? clock.getAsLong() : Long.MIN_VALUE; }

    public void end(Phase phase, long token, long frame) {
        if (!recording || token == Long.MIN_VALUE || Thread.currentThread() != owner) return;
        long duration = clock.getAsLong() - token;
        if (duration < 0) { invalid++; return; }
        if (count == durations.length) { dropped++; return; }
        durations[count] = duration; frames[count] = frame; phases[count++] = (byte)phase.ordinal();
    }

    public void stop() { requireOwner(); recording = false; }
    public Snapshot snapshot() {
        requireOwner();
        return new Snapshot(Arrays.copyOf(durations, count), Arrays.copyOf(frames, count),
                Arrays.copyOf(phases, count), dropped, invalid, durations.length);
    }
    private void requireOwner() {
        if (owner != null && Thread.currentThread() != owner) throw new IllegalStateException("CPU capture belongs to its starting thread");
    }

    public static final class Snapshot {
        public final int count, capacity;
        public final long dropped, invalid;
        private final long[] durations, frames;
        private final byte[] phases;
        private Snapshot(long[] durations, long[] frames, byte[] phases, long dropped, long invalid, int capacity) {
            this.durations = durations; this.frames = frames; this.phases = phases;
            this.count = durations.length; this.capacity = capacity; this.dropped = dropped; this.invalid = invalid;
        }
        public void writeCsv(Writer writer) throws IOException {
            writer.write("sample_index,game_frame_sequence,phase,cpu_wall_ns\n");
            for (int i = 0; i < count; i++) {
                writer.write(i + "," + frames[i] + "," + Phase.values()[phases[i]].name() + "," + durations[i] + "\n");
            }
        }
    }
}
