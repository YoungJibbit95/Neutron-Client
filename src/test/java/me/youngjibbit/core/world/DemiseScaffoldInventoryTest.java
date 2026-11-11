package me.youngjibbit.core.world;

import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/** Original-source inventory fixtures; no client singleton or native game session is installed. */
public class DemiseScaffoldInventoryTest {
    @BeforeClass public static void bootstrapBlocks() { Bootstrap.register(); }

    private static EntityPlayerSP player() throws Exception {
        EntityPlayerSP player = DemiseScaffoldGeometryTest.player(DemiseScaffoldGeometryTest.scene());
        player.inventory = new InventoryPlayer(player);
        player.inventoryContainer = new ContainerPlayer(player.inventory, false, player);
        return player;
    }

    @Test public void everyRegisteredBlockMatchesThePinnedBlacklistWithoutExtraFilters() throws Exception {
        Set<Block> excluded = new HashSet<>();
        InputStream input = getClass().getResourceAsStream("/demise/scaffold-blacklist-e219a97.txt");
        assertNotNull(input);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty() && !line.startsWith("#")) excluded.add((Block) Blocks.class.getField(line).get(null));
            }
        }
        assertEquals(105, excluded.size());
        int checked = 0;
        for (Block block : Block.blockRegistry) {
            assertEquals(String.valueOf(Block.blockRegistry.getNameForObject(block)), !excluded.contains(block),
                    DemiseScaffoldInventory.isValidBlock(new ItemStack(new ItemBlock(block), 1)));
            checked++;
        }
        assertTrue(checked > 190);
        assertFalse(DemiseScaffoldInventory.isValidBlock(null));
        assertFalse(DemiseScaffoldInventory.isValidBlock(new ItemStack(Items.stick)));
    }

    @Test public void lastEligibleHotbarSlotWinsRegardlessOfHeldSlotOrStackSize() throws Exception {
        EntityPlayerSP player = player();
        player.inventory.currentItem = 1;
        player.inventory.mainInventory[0] = new ItemStack(Blocks.stone, 64);
        player.inventory.mainInventory[1] = new ItemStack(Blocks.dirt, 32);
        player.inventory.mainInventory[3] = new ItemStack(Blocks.wool, 2);
        player.inventory.mainInventory[7] = new ItemStack(Blocks.ice, 64);
        player.inventory.mainInventory[8] = new ItemStack(Blocks.glass, 1);
        player.inventory.mainInventory[9] = new ItemStack(Blocks.stone, 64);
        assertEquals(99, DemiseScaffoldInventory.blockCount(player));
        assertEquals(8, DemiseScaffoldInventory.blockSlot(player));
        assertEquals("selection is a query; the module performs the actual switch", 1, player.inventory.currentItem);
        player.inventory.mainInventory[8] = new ItemStack(Blocks.chest, 64);
        assertEquals(3, DemiseScaffoldInventory.blockSlot(player));
        assertEquals(98, DemiseScaffoldInventory.blockCount(player));
    }

    @Test public void anEmptyOrZeroCountHotbarRetainsTheCurrentSlot() throws Exception {
        EntityPlayerSP player = player();
        player.inventory.currentItem = 5;
        player.inventory.mainInventory[9] = new ItemStack(Blocks.stone, 64);
        player.inventory.mainInventory[1] = new ItemStack(Blocks.stone, 0);
        player.inventory.mainInventory[3] = new ItemStack(Blocks.tnt, 64);
        assertTrue(DemiseScaffoldInventory.isValidBlock(player.inventory.mainInventory[1]));
        assertEquals(0, DemiseScaffoldInventory.blockCount(player));
        assertEquals(5, DemiseScaffoldInventory.blockSlot(player));
    }

    @Test public void originalBlockPolicyPreservesThinAndTransparentExceptions() {
        for (Block allowed : new Block[]{Blocks.glass, Blocks.leaves, Blocks.farmland}) {
            assertTrue(DemiseScaffoldInventory.isValidBlock(new ItemStack(new ItemBlock(allowed), 1)));
        }
        for (Block denied : new Block[]{Blocks.packed_ice, Blocks.diamond_ore, Blocks.slime_block, Blocks.glass_pane}) {
            assertFalse(DemiseScaffoldInventory.isValidBlock(new ItemStack(new ItemBlock(denied), 1)));
        }
    }
}
