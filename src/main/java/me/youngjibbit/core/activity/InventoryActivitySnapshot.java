package me.youngjibbit.core.activity;

import java.util.Objects;

/** Immutable local inventory observations. Counts never imply server acknowledgement. */
public final class InventoryActivitySnapshot {
    public enum Producer { INVENTORY_MANAGER, STEALER }
    public enum Action { SCANNING, DROPPING, SORTING, EQUIPPING, STEALING, COMPLETE }
    public static final long TTL_NANOS = 600_000_000L;
    private final Producer producer;
    private final Action action;
    private final int processed, total;
    private final long sequence, observedAtNanos;
    public InventoryActivitySnapshot(Producer producer, Action action, int processed, int total,
                                     long sequence, long observedAtNanos) {
        if (processed < -1 || total < -1 || sequence < 0
                || processed >= 0 && total >= 0 && processed > total)
            throw new IllegalArgumentException("invalid observed progress");
        this.producer = Objects.requireNonNull(producer, "producer");
        this.action = Objects.requireNonNull(action, "action");
        this.processed = processed; this.total = total;
        this.sequence = sequence; this.observedAtNanos = observedAtNanos;
    }
    public Producer getProducer() { return producer; }
    public Action getAction() { return action; }
    public boolean isComplete() { return action == Action.COMPLETE; }
    public boolean isRunning() { return !isComplete(); }
    /** -1 means unknown. Display code must not invent a denominator. */
    public int getProcessed() { return processed; }
    public int getTotal() { return total; }
    public long getSequence() { return sequence; }
    public long getObservedAtNanos() { return observedAtNanos; }
    public boolean isFresh(long now) { long age = now - observedAtNanos; return age >= 0 && age <= TTL_NANOS; }
    public boolean hasSameFacts(Action action, int processed, int total) {
        return this.action == action && this.processed == processed && this.total == total;
    }
}
