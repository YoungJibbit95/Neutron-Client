package me.youngjibbit.core.activity;

import java.util.Objects;

/** Immutable display facts only; never placement admission or a retained game object. */
public final class ScaffoldActivitySnapshot {
    public enum Status { SEARCHING, ALIGNING, READY, PLACING, EDGE_GUARD, RECOVERING }
    public static final long TTL_NANOS = 600_000_000L;
    private final long sequence, observedAtNanos;
    private final int itemId, metadata, blockCount;
    private final String approach, profile;
    private final Status status;
    public ScaffoldActivitySnapshot(long sequence, long observedAtNanos, int itemId, int metadata,
                                    int blockCount, String approach, String profile, Status status) {
        if (sequence < 0 || itemId < -1 || metadata < -1 || blockCount < -1)
            throw new IllegalArgumentException("invalid activity fact");
        this.sequence = sequence; this.observedAtNanos = observedAtNanos;
        this.itemId = itemId; this.metadata = metadata; this.blockCount = blockCount;
        this.approach = Objects.requireNonNull(approach, "approach");
        this.profile = Objects.requireNonNull(profile, "profile");
        this.status = Objects.requireNonNull(status, "status");
    }
    public long getSequence() { return sequence; }
    public long getObservedAtNanos() { return observedAtNanos; }
    /** Original Item registry ID, or -1 when no usable stack was observed. */
    public int getItemId() { return itemId; }
    public int getMetadata() { return metadata; }
    /** Original placement service's usable hotbar count, or -1 if unknown. */
    public int getBlockCount() { return blockCount; }
    public String getApproach() { return approach; }
    public String getProfile() { return profile; }
    public Status getStatus() { return status; }
    public boolean isFresh(long now) { long age = now - observedAtNanos; return age >= 0 && age <= TTL_NANOS; }
    public boolean hasSameFacts(int itemId, int metadata, int blockCount,
                                String approach, String profile, Status status) {
        return this.itemId == itemId && this.metadata == metadata && this.blockCount == blockCount
                && this.approach.equals(approach) && this.profile.equals(profile) && this.status == status;
    }
}
