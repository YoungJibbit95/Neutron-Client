package me.youngjibbit.module.impl.player;

import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * W09-T2b: the real InventoryManager must rank build blocks exactly like the pure
 * planner — biggest stack on the build slot and inside the kept set, weakest-first
 * drop, and a strict hard limit counting every kept block stack.
 */
public class InventoryManagerBlockRankingTest {

    static Object field(Object o, String n) throws Exception {
        Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o);
    }
    static void call(Object o, String n) throws Exception {
        Method m = o.getClass().getDeclaredMethod(n); m.setAccessible(true); m.invoke(o);
    }
    static void scan(InventoryManager m) throws Exception {
        call(m, "searchForItems"); call(m, "searchForBestArmor"); call(m, "searchForTrash");
    }
    @SuppressWarnings("unchecked")
    static List<Integer> trashOf(InventoryManager m) throws Exception {
        return (List<Integer>) field(m, "trash");
    }
    @SuppressWarnings("unchecked")
    static List<Integer> allBlocksOf(InventoryManager m) throws Exception {
        return (List<Integer>) field(m, "allBlocks");
    }

    /** Regression W09-T2b: 48 wood survives the block limit, TNT drops first — in both slot orders. */
    @Test public void wood48BeatsTnt4BeyondTheBlockLimitInBothSlotOrders() throws Exception {
        net.minecraft.init.Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            InventoryManager m = new InventoryManager();
            m.random.setValue(false);
            m.maxBlocks.setValue(1.0);
            for (int[] slots : new int[][]{{3, 12}, {12, 3}}) {
                final int tntSlot = slots[0], woodSlot = slots[1];
                Arrays.fill(f.player.inventory.mainInventory, null);
                f.player.inventory.mainInventory[tntSlot] = new ItemStack(Blocks.tnt, 4);
                f.player.inventory.mainInventory[woodSlot] = new ItemStack(Blocks.planks, 48);
                scan(m);
                assertEquals("48 wood owns the build slot from either slot order",
                        woodSlot, ((Number) field(m, "bestBlockSlot")).intValue());
                final List<Integer> trash = trashOf(m);
                assertTrue("TNT is dropped beyond the small limit", trash.contains(tntSlot));
                assertFalse("48 wood stays kept", trash.contains(woodSlot));
                assertTrue("48 wood remains a usable block for Scaffold",
                        allBlocksOf(m).contains(woodSlot));
            }
        }
    }

    /** The hard limit counts every kept block stack; overflow drops weakest-first. */
    @Test public void maximumBlockStacksIsStrictAndDropsWeakestFirst() throws Exception {
        net.minecraft.init.Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            InventoryManager m = new InventoryManager();
            m.random.setValue(false);
            for (int limit = 0; limit <= 3; limit++) {
                Arrays.fill(f.player.inventory.mainInventory, null);
                f.player.inventory.mainInventory[0] = new ItemStack(Blocks.planks, 64);
                f.player.inventory.mainInventory[5] = new ItemStack(Blocks.planks, 48);
                f.player.inventory.mainInventory[9] = new ItemStack(Blocks.tnt, 4);
                m.maxBlocks.setValue((double) limit);
                scan(m);
                final List<Integer> trash = trashOf(m);
                assertEquals("kept block stacks must equal the strict limit",
                        3 - Math.min(3, limit), trash.size());
                if (limit == 0) assertTrue(trash.containsAll(Arrays.asList(0, 5, 9)));
                if (limit == 1) {
                    assertTrue(trash.contains(9)); assertTrue(trash.contains(5));
                    assertFalse(trash.contains(0));
                }
                if (limit == 2) {
                    assertTrue("the weakest 4-stack drops first", trash.contains(9));
                    assertFalse(trash.contains(0)); assertFalse(trash.contains(5));
                }
                if (limit == 3) assertTrue(trash.isEmpty());
                // The build candidate stays reported for Scaffold even at limit 0.
                assertEquals(0, ((Number) field(m, "bestBlockSlot")).intValue());
            }
            // Destructive-click revalidation needs snapshots of exactly the trashed stacks.
            m.maxBlocks.setValue(1.0);
            Arrays.fill(f.player.inventory.mainInventory, null);
            f.player.inventory.mainInventory[0] = new ItemStack(Blocks.planks, 64);
            f.player.inventory.mainInventory[5] = new ItemStack(Blocks.planks, 48);
            f.player.inventory.mainInventory[9] = new ItemStack(Blocks.tnt, 4);
            scan(m);
            Map<?, ?> snapshots = (Map<?, ?>) field(m, "trashSnapshots");
            assertTrue(snapshots.containsKey(9));
            assertTrue(snapshots.containsKey(5));
            assertFalse(snapshots.containsKey(0));
            assertEquals(4, ((ItemStack) snapshots.get(9)).stackSize);
            assertEquals(48, ((ItemStack) snapshots.get(5)).stackSize);
        }
    }
}
