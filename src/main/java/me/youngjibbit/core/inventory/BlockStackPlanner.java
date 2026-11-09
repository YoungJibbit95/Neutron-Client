package me.youngjibbit.core.inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure deterministic block-stack ranking for the inventory manager.
 * The host supplies immutable snapshots and keeps every window click;
 * this layer owns no timing, no randomness and no Minecraft classes.
 *
 * Ranking contract (W09-T2a):
 * - Usability before stack size: build-slot candidates are the stacks a host marks
 *   usable for placing; {@link Kind#OTHER} never becomes a build stack.
 * - Bigger stacks win the build slot; a plain buildable stack beats an equally sized
 *   special one; the lowest slot index breaks remaining ties.
 * - {@code maxBlockStacks} counts every kept block stack including the build slot.
 * - Overflow is dropped weakest-first: smallest count, then lowest slot index.
 * - Randomness may never change a ranking decision; tie-breakers are total.
 */
public final class BlockStackPlanner {

    /** Host-declared use of the stack: plain bridge material, placeable special block, other item. */
    public enum Kind { BUILDABLE, SPECIAL, OTHER }

    /** Immutable item view. Slot indexes follow the host's container numbering. */
    public static final class Snapshot {
        public final int slot, count;
        public final Kind kind;

        public Snapshot(int slot, int count, Kind kind) {
            if (slot < 0 || count < 0 || kind == null)
                throw new IllegalArgumentException("invalid stack snapshot");
            this.slot = slot; this.count = count; this.kind = kind;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Snapshot)) return false;
            Snapshot snap = (Snapshot) other;
            return slot == snap.slot && count == snap.count && kind == snap.kind;
        }
        @Override public int hashCode() { return (slot * 31 + count) * 31 + kind.ordinal(); }
        @Override public String toString() {
            return "Snapshot" + "{slot=" + slot + ", count=" + count + ", kind=" + kind + '}';
        }
    }

    /** Immutable plan view. Order matters and is part of the contract. */
    public static final class Plan {
        public final int maxBlockStacks;
        /** All usable stacks ranked best-first. May be empty. */
        public final List<Snapshot> usableBlocks;
        /** Reported even when the limit is 0: consumers still need a build candidate. */
        public final Snapshot bestBuildStack;
        /** Usable stacks kept under the limit, best-first. Never more than the limit. */
        public final List<Snapshot> keptBlockStacks;
        /** Overflow, weakest-first. Empty when the limit already covers every usable stack. */
        public final List<Snapshot> trashBlockStacks;

        Plan(int maxBlockStacks, List<Snapshot> usableBlocks, Snapshot bestBuildStack,
             List<Snapshot> keptBlockStacks, List<Snapshot> trashBlockStacks) {
            this.maxBlockStacks = maxBlockStacks;
            this.usableBlocks = usableBlocks;
            this.bestBuildStack = bestBuildStack;
            this.keptBlockStacks = keptBlockStacks;
            this.trashBlockStacks = trashBlockStacks;
        }
    }

    /** Sizes first, then buildability, then the lowest slot; ties are always total. */
    private static int bestFirst(Snapshot a, Snapshot b) {
        if (a.count != b.count) return b.count - a.count;
        if (a.kind != b.kind) return a.kind == Kind.BUILDABLE ? -1 : 1;
        return a.slot - b.slot;
    }

    /** Weakest first: smallest count, then lowest slot index. */
    private static int weakestFirst(Snapshot a, Snapshot b) {
        if (a.count != b.count) return a.count - b.count;
        return a.slot - b.slot;
    }

    public static Plan plan(List<Snapshot> stacks, int maxBlockStacks) {
        if (maxBlockStacks < 0)
            throw new IllegalArgumentException("maxBlockStacks must not be negative");
        List<Snapshot> usable = new ArrayList<>();
        for (Snapshot s : stacks)
            if (s.kind != Kind.OTHER) usable.add(s);
        usable.sort(BlockStackPlanner::bestFirst);
        // The limit counts every kept stack including the build slot; the reported
        // build candidate stays available even when the limit is 0.
        int keptCount = Math.min(usable.size(), maxBlockStacks);
        List<Snapshot> kept = new ArrayList<>(usable.subList(0, keptCount));
        List<Snapshot> trash = new ArrayList<>(usable.subList(keptCount, usable.size()));
        trash.sort(BlockStackPlanner::weakestFirst);
        return new Plan(maxBlockStacks, usable, usable.isEmpty() ? null : usable.get(0), kept, trash);
    }

    private BlockStackPlanner() { }
}
