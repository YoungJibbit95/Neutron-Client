package me.youngjibbit.core.inventory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Deterministic ranking contract for the inventory manager's build-block stacks. */
public class BlockStackPlannerTest {

    private static BlockStackPlanner.Snapshot b(int slot, int count) {
        return new BlockStackPlanner.Snapshot(slot, count, BlockStackPlanner.Kind.BUILDABLE);
    }
    private static BlockStackPlanner.Snapshot special(int slot, int count) {
        return new BlockStackPlanner.Snapshot(slot, count, BlockStackPlanner.Kind.SPECIAL);
    }
    private static BlockStackPlanner.Snapshot other(int slot, int count) {
        return new BlockStackPlanner.Snapshot(slot, count, BlockStackPlanner.Kind.OTHER);
    }

    /** Regression: 48 wood must win the build slot before 4 TNT, in both slot orders. */
    @Test public void wood48BeatsTnt4OnTheBuildSlotRegardlessOfSlotOrder() {
        BlockStackPlanner.Snapshot tnt4 = special(3, 4), wood48 = b(12, 48);
        for (List<BlockStackPlanner.Snapshot> layout : Arrays.asList(
                Arrays.asList(tnt4, wood48), Arrays.asList(wood48, tnt4))) {
            BlockStackPlanner.Plan plan = BlockStackPlanner.plan(layout, 8);
            assertNotNull(plan.bestBuildStack);
            assertEquals(12, plan.bestBuildStack.slot);
            assertEquals(48, plan.bestBuildStack.count);
            assertEquals(wood48, plan.usableBlocks.get(0));
            assertEquals(2, plan.keptBlockStacks.size());
            assertTrue("the 48-stack survives a limited block budget",
                    !plan.trashBlockStacks.contains(wood48));
        }
        // Pre-sorted and shuffled input must not change the decision.
        List<BlockStackPlanner.Snapshot> shuffled = new ArrayList<>(Arrays.asList(tnt4, wood48));
        Collections.shuffle(shuffled);
        assertEquals(12, BlockStackPlanner.plan(shuffled, 8).bestBuildStack.slot);
    }

    /** Beyond the limit the WEAKEST stack goes first; smaller stacks never win over large ones. */
    @Test public void weakestStacksDropFirstWhenTheLimitCountsTotalKeptStacks() {
        BlockStackPlanner.Plan plan = BlockStackPlanner.plan(
                Arrays.asList(b(0, 64), b(5, 48), special(9, 4)), 2);
        assertEquals(2, plan.keptBlockStacks.size());
        assertEquals(64, plan.keptBlockStacks.get(0).count);
        assertEquals(48, plan.keptBlockStacks.get(1).count);
        assertEquals(1, plan.trashBlockStacks.size());
        assertEquals(9, plan.trashBlockStacks.get(0).slot);
        // Slot order alone must not decide: TNT listed first stays the weakest.
        BlockStackPlanner.Plan reversed = BlockStackPlanner.plan(
                Arrays.asList(special(9, 4), b(0, 64), b(5, 48)), 2);
        assertEquals(64, reversed.keptBlockStacks.get(0).count);
        assertEquals(48, reversed.keptBlockStacks.get(1).count);
        assertEquals(9, reversed.trashBlockStacks.get(0).slot);
    }

    /** The limit counts every kept block stack (including the build slot); smaller inputs stay intact. */
    @Test public void limitSemanticsAreTotalAndNeverDropWithoutOverflow() {
        assertEquals(2, BlockStackPlanner.plan(Arrays.asList(b(0, 64), b(5, 48)), 2)
                .keptBlockStacks.size());
        for (int limit : new int[]{0, 1, 2, 8}) {
            BlockStackPlanner.Plan plan = BlockStackPlanner.plan(
                    Arrays.asList(b(0, 64), b(5, 48), special(9, 4)), limit);
            assertEquals("kept stacks must never exceed the limit", Math.min(3, limit),
                    plan.keptBlockStacks.size());
            assertNotNull("the build candidate stays reported", plan.bestBuildStack);
        }
        BlockStackPlanner.Plan zero = BlockStackPlanner.plan(
                Arrays.asList(b(0, 64), b(5, 48), special(9, 4)), 0);
        assertEquals(0, zero.keptBlockStacks.size());
        assertEquals(3, zero.trashBlockStacks.size());
        // Weakest-first trash: 4 before 48 before 64.
        assertEquals(9, zero.trashBlockStacks.get(0).slot);
        assertEquals(5, zero.trashBlockStacks.get(1).slot);
        assertEquals(0, zero.trashBlockStacks.get(2).slot);
    }

    /** Equal sizes prefer the plain buildable stack; ties end on the lowest slot. */
    @Test public void categoryAndSlotTieBreakersAreTotal() {
        List<BlockStackPlanner.Snapshot> woodVsTnt = Arrays.asList(special(2, 48), b(6, 48));
        assertEquals(6, BlockStackPlanner.plan(woodVsTnt, 8).bestBuildStack.slot);
        assertEquals(2, BlockStackPlanner.plan(Arrays.asList(special(2, 48), b(6, 48)), 0)
                .trashBlockStacks.get(0).slot);
        // Same kind, same size: deterministic lowest slot.
        BlockStackPlanner.Plan tie = BlockStackPlanner.plan(
                Arrays.asList(b(15, 16), b(3, 16)), 1);
        assertEquals(3, tie.keptBlockStacks.get(0).slot);
        assertEquals(15, tie.trashBlockStacks.get(0).slot);
    }

    /** Other items never become build stacks and are invisible to the block ranking. */
    @Test public void otherItemsNeverWinOrDisplaceTheBuildSlot() {
        BlockStackPlanner.Plan plan = BlockStackPlanner.plan(
                Arrays.asList(other(0, 64), b(7, 4)), 8);
        assertEquals(7, plan.bestBuildStack.slot);
        assertEquals(1, plan.usableBlocks.size());
        assertEquals(1, plan.keptBlockStacks.size());
        assertEquals(0, plan.trashBlockStacks.size());
    }

    /** Empty, invalid and repeated executions are deterministic and bounded. */
    @Test public void planIsDeterministicValidatedAndRepeatable() {
        BlockStackPlanner.Plan empty = BlockStackPlanner.plan(Collections.<BlockStackPlanner.Snapshot>emptyList(), 2);
        assertNotNull(empty.usableBlocks);
        assertEquals(0, empty.usableBlocks.size());
        assertNull(empty.bestBuildStack);
        try { BlockStackPlanner.plan(Arrays.asList(b(0, 4)), -1); fail(); }
        catch (IllegalArgumentException expected) { }
        List<BlockStackPlanner.Snapshot> layout = Arrays.asList(b(2, 30), special(4, 30), b(8, 1));
        BlockStackPlanner.Plan first = BlockStackPlanner.plan(new ArrayList<>(layout), 2);
        BlockStackPlanner.Plan second = BlockStackPlanner.plan(new ArrayList<>(layout), 2);
        assertEquals(first.keptBlockStacks, second.keptBlockStacks);
        assertEquals(first.trashBlockStacks, second.trashBlockStacks);
        assertEquals(first.bestBuildStack.slot, second.bestBuildStack.slot);
        assertEquals(2, first.keptBlockStacks.size());
        assertEquals(1, first.trashBlockStacks.size());
        assertEquals(8, first.trashBlockStacks.get(0).slot);
    }

    @Test public void publishedPlanListsCannotBeMutatedOrAliased() {
        List<BlockStackPlanner.Snapshot> input = new ArrayList<>(Arrays.asList(b(0, 64), b(1, 4)));
        BlockStackPlanner.Plan plan = BlockStackPlanner.plan(input, 1);
        input.clear();
        assertEquals(2, plan.usableBlocks.size());
        for (List<BlockStackPlanner.Snapshot> view : Arrays.asList(
                plan.usableBlocks, plan.keptBlockStacks, plan.trashBlockStacks)) {
            try { view.clear(); fail("published plan lists must be immutable"); }
            catch (UnsupportedOperationException expected) { }
        }
        assertEquals(Arrays.asList(b(0, 64), b(1, 4)), plan.usableBlocks);
        assertEquals(Collections.singletonList(b(0, 64)), plan.keptBlockStacks);
        assertEquals(Collections.singletonList(b(1, 4)), plan.trashBlockStacks);
        assertEquals(b(0, 64), plan.bestBuildStack);
    }

    @Test public void emptyStacksAreNotBuildCandidatesOrBudgetConsumers() {
        BlockStackPlanner.Plan plan = BlockStackPlanner.plan(
                Arrays.asList(b(0, 0), special(1, 0), b(2, 4)), 1);
        assertEquals(Collections.singletonList(b(2, 4)), plan.usableBlocks);
        assertEquals(Collections.singletonList(b(2, 4)), plan.keptBlockStacks);
        assertTrue(plan.trashBlockStacks.isEmpty());
        assertEquals(2, plan.bestBuildStack.slot);
        assertNull(BlockStackPlanner.plan(Collections.singletonList(b(0, 0)), 1).bestBuildStack);
    }
}
