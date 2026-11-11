package me.youngjibbit.core.inventory;

import java.util.*;

/** Stable ranks for the lifetime of one visible container; filtering/refilling never reshuffles survivors. */
public final class InventorySlotOrder {
    private final int[] rank;
    private final List<Integer> slots;
    private int cursor;
    private List<Integer> pendingOrder;
    public InventorySlotOrder(int size, String profile, long seed) {
        if (size < 0 || size > 90) throw new IllegalArgumentException("Unsupported inventory size");
        rank = new int[size];
        List<Integer> order = new ArrayList<>();
        Random random = new Random(seed);
        for (int i = 0; i < size; i++) order.add(i);
        if ("Shuffle".equals(profile)) Collections.shuffle(order, random);
        else if ("Rows".equals(profile)) {
            List<Integer> rows = new ArrayList<>();
            for (int i = 0; i < (size + 8) / 9; i++) rows.add(i);
            Collections.shuffle(rows, random); order.clear();
            for (int row : rows) {
                boolean reverse = random.nextBoolean();
                for (int i = 0; i < 9; i++) {
                    int slot = row * 9 + (reverse ? 8 - i : i);
                    if (slot < size) order.add(slot);
                }
            }
        } else if ("Nearby".equals(profile) && size > 0) {
            List<Integer> remaining = new ArrayList<>(order); order.clear();
            int last = remaining.remove(random.nextInt(remaining.size())); order.add(last);
            while (!remaining.isEmpty()) {
                int best = 0; double cost = Double.MAX_VALUE;
                for (int i = 0; i < remaining.size(); i++) {
                    int next = remaining.get(i), dx = next % 9 - last % 9, dy = next / 9 - last / 9;
                    double candidate = dx * dx + dy * dy + random.nextDouble() * .75;
                    if (candidate < cost) { cost = candidate; best = i; }
                }
                last = remaining.remove(best); order.add(last);
            }
        }
        for (int i = 0; i < order.size(); i++) rank[order.get(i)] = i;
        slots = Collections.unmodifiableList(order); pendingOrder = slots;
    }
    public List<Integer> slots() { return slots; }
    public List<Integer> pendingSlots() { return pendingOrder; }
    /** Advance only after a real attempt; a refill cannot repeatedly jump ahead of unvisited slots. */
    public void advanceAfter(int slot) {
        if (slot < 0 || slot >= rank.length || rank.length == 0) return;
        cursor = (rank[slot] + 1) % rank.length;
        List<Integer> order = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) order.add(slots.get((cursor + i) % slots.size()));
        pendingOrder = Collections.unmodifiableList(order);
    }
    /** Only reorders the caller's already-approved set. Invalid indices cannot become candidates. */
    public void sort(List<Integer> candidates) {
        for (Integer slot : candidates)
            if (slot == null || slot < 0 || slot >= rank.length) throw new IllegalArgumentException("Invalid candidate slot");
        candidates.sort(Comparator.comparingInt(slot -> (rank[slot] - cursor + rank.length) % rank.length));
    }
}
