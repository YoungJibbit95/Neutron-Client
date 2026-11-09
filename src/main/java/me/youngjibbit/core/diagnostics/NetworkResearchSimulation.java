package me.youngjibbit.core.diagnostics;

import java.util.Comparator;
import java.util.PriorityQueue;
import me.youngjibbit.core.diagnostics.NetworkResearchTrace.*;

/** Offline metadata scheduler. Virtual time only; cannot send or apply a Minecraft packet. */
public final class NetworkResearchSimulation {
    public enum Scenario { BASELINE, JITTER, BURST, PAUSE }
    private static final int CAPACITY = 64;
    private static final long MAX_AGE = 1_000_000_000L;
    private final PriorityQueue<Sample> pending = new PriorityQueue<>(Comparator
            .comparingLong((Sample s) -> s.due).thenComparingLong(s -> s.sequence));
    private long sequence, rejected;

    public boolean schedule(long now, long delayNanos, String label) {
        if (pending.size() == CAPACITY || delayNanos < 0 || delayNanos > MAX_AGE || now > Long.MAX_VALUE - delayNanos) {
            rejected++; return false;
        }
        pending.add(new Sample(now + delayNanos, ++sequence, label)); return true;
    }

    public void advance(long now, NetworkResearchTrace trace) {
        while (!pending.isEmpty() && pending.peek().due <= now) {
            Sample sample = pending.remove();
            if (now - sample.due > MAX_AGE) { rejected++; continue; }
            trace.packet(now, Kind.APPLIED, sample.label);
        }
        trace.queue(now, pending.size(), 0, 0);
    }

    public void reset() { pending.clear(); sequence = rejected = 0; }
    public int pending() { return pending.size(); }
    public long getRejected() { return rejected; }

    public void run(Scenario scenario, Mode mode, NetworkResearchTrace trace) {
        reset(); trace.startRun(mode, Origin.SIMULATION);
        long now = 0;
        for (int tick = 0; tick < 12; tick++) {
            now += scenario == Scenario.PAUSE && tick == 6 ? 300_000_000L : 50_000_000L;
            trace.tick(now, 1, false);
            trace.packet(now, Kind.RECEIVED, "SyntheticEntityMove");
            long delay = scenario == Scenario.JITTER ? tick % 3 * 25_000_000L
                    : scenario == Scenario.BURST ? (2 - tick % 3) * 50_000_000L : 0;
            schedule(now, delay, "SyntheticEntityMove");
            trace.entity(now, 1, tick / 4D, 0, 0, (tick - 1) / 4D, 0, 0);
            advance(now, trace);
        }
        advance(now + MAX_AGE, trace);
    }

    private static final class Sample {
        final long due, sequence;
        final String label;
        Sample(long due, long sequence, String label) {
            this.due = due; this.sequence = sequence;
            this.label = label == null ? "" : label.substring(0, Math.min(160, label.length()));
        }
    }
}
