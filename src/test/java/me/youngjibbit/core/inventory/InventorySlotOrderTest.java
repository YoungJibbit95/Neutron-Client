package me.youngjibbit.core.inventory;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class InventorySlotOrderTest {
    @Test public void everyProfileIsAStableCompletePermutationForBothChestsAndPlayerInventory() {
        for (int size : new int[]{27, 36, 54}) for (String profile : new String[]{"Sequential", "Shuffle", "Rows", "Nearby"}) {
            InventorySlotOrder ranks = new InventorySlotOrder(size, profile, 87);
            assertEquals(size, ranks.slots().size()); assertEquals(size, new HashSet<>(ranks.slots()).size());
            for (int slot = 0; slot < size; slot++) assertTrue(ranks.slots().contains(slot));
            assertEquals(ranks.slots(), new InventorySlotOrder(size, profile, 87).slots());
            for (int repeat = 0; repeat < 60; repeat++) assertEquals(ranks.slots(), ranks.slots());
        }
    }
    @Test public void filteringAndRefillingPreserveRelativeRanksAndNeverAddUnsafeCandidates() {
        InventorySlotOrder ranks = new InventorySlotOrder(36, "Shuffle", 122);
        List<Integer> safe = new ArrayList<>(Arrays.asList(3, 10, 14, 30)); ranks.sort(safe);
        List<Integer> reduced = new ArrayList<>(safe); reduced.remove(Integer.valueOf(14)); ranks.sort(reduced);
        List<Integer> expected = new ArrayList<>(safe); expected.remove(Integer.valueOf(14)); assertEquals(expected, reduced);
        reduced.add(14); ranks.sort(reduced); assertEquals(safe, reduced);
        assertEquals(new HashSet<>(Arrays.asList(3, 10, 14, 30)), new HashSet<>(reduced));
    }
    @Test public void newSessionsVaryAndRowsRemainContiguous() {
        assertNotEquals(new InventorySlotOrder(54, "Shuffle", 1).slots(), new InventorySlotOrder(54, "Shuffle", 2).slots());
        List<Integer> rows = new InventorySlotOrder(54, "Rows", 16).slots();
        for (int base = 0; base < 54; base += 9) for (int i = base; i < base + 9; i++) assertEquals(rows.get(base) / 9, rows.get(i) / 9);
    }
    @Test(expected = IllegalArgumentException.class) public void invalidSlotsCannotEnterTheApprovedSet() {
        new InventorySlotOrder(36, "Nearby", 0).sort(new ArrayList<>(Arrays.asList(4, 38)));
    }
    @Test public void advancingTheFixedPermutationPreventsRefillStarvationWithoutReshuffling() {
        InventorySlotOrder order = new InventorySlotOrder(27, "Shuffle", 13);
        List<Integer> fixed = new ArrayList<>(order.slots());
        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < 27; i++) {
            int next = order.pendingSlots().get(0); assertTrue(visited.add(next)); order.advanceAfter(next);
            assertEquals(fixed, order.slots());
        }
        assertEquals(27, visited.size()); assertEquals(fixed, order.pendingSlots());
    }
}
