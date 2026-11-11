package me.youngjibbit.core.inventory;

import net.minecraft.init.*;
import net.minecraft.item.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.BeforeClass;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class InventoryItemClassifierTest {
    @BeforeClass public static void bootstrap() { Bootstrap.register(); }
    private InventoryPlanner.Stack item(ItemStack stack) { return InventoryItemClassifier.describe(9, stack, false); }
    @Test public void toolPurposesAndBucketContentsAreDistinctWhileCopiesAreComparable() {
        ItemStack plain = new ItemStack(Items.diamond_pickaxe), silk = plain.copy(), fortune = plain.copy();
        silk.addEnchantment(Enchantment.silkTouch, 1); fortune.addEnchantment(Enchantment.fortune, 3);
        assertNotEquals(item(plain).duplicateKey, item(silk).duplicateKey);
        assertNotEquals(item(silk).duplicateKey, item(fortune).duplicateKey);
        assertEquals(item(new ItemStack(Items.wooden_axe)).duplicateKey, item(new ItemStack(Items.diamond_axe)).duplicateKey);
        assertNotEquals(item(new ItemStack(Items.water_bucket)).duplicateKey, item(new ItemStack(Items.lava_bucket)).duplicateKey);
        assertEquals("", item(new ItemStack(Items.bucket, 8)).duplicateKey);
        assertFalse(item(silk).protectedItem);
    }
    @Test public void namedCustomAndStoredDataSurviveButOrdinaryEnchantmentsAreRanked() {
        ItemStack named = new ItemStack(Items.fishing_rod); named.setStackDisplayName("Saved rod");
        assertTrue(item(named).protectedItem);
        ItemStack custom = new ItemStack(Items.flint_and_steel);
        NBTTagCompound tag = new NBTTagCompound(); tag.setString("server_action", "menu"); custom.setTagCompound(tag);
        assertTrue(item(custom).protectedItem);
        ItemStack storage = new ItemStack(Items.chest_minecart);
        NBTTagCompound stored = new NBTTagCompound(); stored.setTag("BlockEntityTag", new NBTTagCompound()); storage.setTagCompound(stored);
        assertTrue(item(storage).protectedItem);
        ItemStack enchanted = new ItemStack(Items.diamond_sword); enchanted.addEnchantment(Enchantment.sharpness, 4);
        assertFalse(item(enchanted).protectedItem);
        assertTrue(item(enchanted).quality > item(new ItemStack(Items.diamond_sword)).quality);
    }
    @Test public void bestFoodAndHealingRespectActualEffectsAndConsumableQuantities() {
        assertTrue(item(new ItemStack(Items.golden_apple)).quality > item(new ItemStack(Items.cooked_beef)).quality);
        assertEquals(InventoryPlanner.Role.NONE, item(new ItemStack(Items.fish, 1, 3)).role);
        assertEquals("", item(new ItemStack(Items.mushroom_stew)).duplicateKey);
        ItemStack drink = new ItemStack(Items.potionitem, 1, 8197);
        ItemStack splash = new ItemStack(Items.potionitem, 1, 16389);
        assertEquals(InventoryPlanner.Role.HEALING, item(drink).role);
        assertEquals(InventoryPlanner.Role.HEALING, item(splash).role);
        assertTrue(item(splash).quality > item(drink).quality);
        assertEquals("", item(splash).duplicateKey);
    }
    @Test public void retainsOneRodFlintAndEachFilledBucketWithoutDroppingTheirBestCopy() {
        List<InventoryPlanner.Stack> items = new ArrayList<>();
        Item[] kinds = {Items.fishing_rod, Items.flint_and_steel, Items.water_bucket, Items.lava_bucket};
        for (int i = 0; i < kinds.length; i++) {
            items.add(InventoryItemClassifier.describe(9 + i, new ItemStack(kinds[i]), false));
            items.add(InventoryItemClassifier.describe(20 + i, new ItemStack(kinds[i]), false));
        }
        InventoryPlanner.Plan p = InventoryPlanner.plan(items, InventoryPlanner.organizedRoles(), 2, true, InventoryPlanner.Vacant.LEAVE);
        assertEquals(Arrays.asList(20, 21, 22, 23), p.discard);
    }
    @Test public void utilityBlocksDoNotEnterBuildingStackLimits() {
        List<InventoryPlanner.Stack> items = new ArrayList<>();
        net.minecraft.block.Block[] utility = {Blocks.chest, Blocks.trapped_chest, Blocks.ender_chest,
                Blocks.enchanting_table, Blocks.beacon, Blocks.furnace, Blocks.crafting_table,
                Blocks.hopper, Blocks.dispenser, Blocks.dropper, Blocks.mob_spawner, Blocks.jukebox};
        for (int i = 0; i < utility.length; i++) {
            InventoryPlanner.Stack stack = InventoryItemClassifier.describe(9 + i, new ItemStack(utility[i]), false);
            assertNull(stack.blockKind); assertEquals(InventoryPlanner.Role.NONE, stack.role); items.add(stack);
        }
        assertTrue(InventoryPlanner.plan(items, InventoryPlanner.organizedRoles(), 0, true, InventoryPlanner.Vacant.LEAVE).discard.isEmpty());
    }
}
