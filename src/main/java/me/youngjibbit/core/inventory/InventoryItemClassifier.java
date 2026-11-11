package me.youngjibbit.core.inventory;

import net.minecraft.block.*;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.*;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import java.util.List;

/** Converts current stack facts to immutable planner input. Unknown item data is retained. */
public final class InventoryItemClassifier {
    public static boolean protectedItem(ItemStack stack) {
        if (stack == null) return false;
        if (stack.hasDisplayName()) return true;
        if (!stack.hasTagCompound()) return false;
        // Enchantments and repair cost are ordinary equipment facts. Other NBT may
        // identify a server menu item, written book, map, custom potion or stored contents.
        for (String key : stack.getTagCompound().getKeySet())
            if (!"ench".equals(key) && !"RepairCost".equals(key)) return true;
        return false;
    }

    public static BlockStackPlanner.Kind blockKind(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || !(stack.getItem() instanceof ItemBlock)) return null;
        Block block = ((ItemBlock) stack.getItem()).getBlock();
        // Storage, workstations and other tile-entity blocks are utility items,
        // not expendable bridge stacks. Keep them outside the automatic block limit.
        if (block instanceof BlockContainer || block instanceof BlockWorkbench ||
                block == Blocks.web || block == Blocks.bed || block == Blocks.noteblock ||
                block == Blocks.cactus || block == Blocks.cake || block == Blocks.anvil ||
                block == Blocks.skull || block instanceof BlockDoor || block instanceof BlockFlower ||
                block instanceof BlockCarpet) return null;
        return block == Blocks.tnt ? BlockStackPlanner.Kind.SPECIAL : BlockStackPlanner.Kind.BUILDABLE;
    }

    public static InventoryPlanner.Stack describe(int slot, ItemStack stack, boolean blacklisted) {
        if (stack == null || stack.stackSize <= 0 || stack.getItem() == null) return null;
        Item item = stack.getItem();
        InventoryPlanner.Role role = InventoryPlanner.Role.NONE;
        BlockStackPlanner.Kind block = blockKind(stack);
        String group = "";
        double quality = 0;
        if (item instanceof ItemSword) {
            role = InventoryPlanner.Role.SWORD; group = "sword";
            quality = ((ItemSword)item).getDamageVsEntity() + enchant(stack, Enchantment.sharpness) * 1.25;
            quality += enchant(stack, Enchantment.fireAspect) * .12 + enchant(stack, Enchantment.knockback) * .05;
        } else if (item instanceof ItemBow) {
            role = InventoryPlanner.Role.BOW; group = "bow";
            quality = enchant(stack, Enchantment.power) * 10 + enchant(stack, Enchantment.infinity) * 2
                    + enchant(stack, Enchantment.flame) + enchant(stack, Enchantment.punch) * .25;
        } else if (item instanceof ItemTool) {
            if (item instanceof ItemPickaxe) { role = InventoryPlanner.Role.PICKAXE; group = "pickaxe"; }
            else if (item instanceof ItemAxe) { role = InventoryPlanner.Role.AXE; group = "axe"; }
            else if (item instanceof ItemSpade) group = "shovel";
            else group = "tool:" + Item.itemRegistry.getNameForObject(item);
            quality = ((ItemTool)item).getToolMaterial().getEfficiencyOnProperMaterial() +
                    enchant(stack, Enchantment.efficiency) * 10;
            // Silk Touch and Fortune serve different jobs; retain their best representatives.
            if (enchant(stack, Enchantment.silkTouch) > 0) group += ":silk";
            else if (enchant(stack, Enchantment.fortune) > 0) group += ":fortune";
        } else if (item instanceof ItemFishingRod) {
            role = InventoryPlanner.Role.ROD; group = "fishing_rod";
            quality = enchant(stack, Enchantment.luckOfTheSea) * 2 + enchant(stack, Enchantment.lure);
        } else if (item == Items.flint_and_steel) {
            role = InventoryPlanner.Role.FLINT; group = "flint_and_steel";
        } else if (item == Items.water_bucket) {
            role = InventoryPlanner.Role.WATER; group = "water_bucket";
        } else if (item == Items.lava_bucket) {
            role = InventoryPlanner.Role.LAVA; group = "lava_bucket";
        } else if (item instanceof ItemPotion) {
            List<PotionEffect> effects = ((ItemPotion)item).getEffects(stack);
            if (effects != null) for (PotionEffect effect : effects)
                if (effect.getPotionID() == Potion.heal.id) {
                    role = InventoryPlanner.Role.HEALING;
                    quality = (ItemPotion.isSplash(stack.getMetadata()) ? 1000 : 0) + effect.getAmplifier() * 100;
                }
            // Consumable doses are not duplicate equipment.
        } else if (item instanceof ItemFood) {
            if (safeFood(stack)) {
                role = InventoryPlanner.Role.FOOD;
                ItemFood food = (ItemFood)item;
                quality = food.getHealAmount(stack) + food.getSaturationModifier(stack) * 10;
                if (item instanceof ItemAppleGold) quality += 10000 + stack.getMetadata() * 100;
            }
        } else if (item instanceof ItemEnderPearl) {
            role = InventoryPlanner.Role.PEARLS;
        } else if (block != null) {
            role = InventoryPlanner.Role.BLOCKS;
            quality = stack.stackSize * 2 + (block == BlockStackPlanner.Kind.BUILDABLE ? 1 : 0);
        } else if (!(item instanceof ItemArmor) && stack.getMaxStackSize() == 1) {
            group = "item:" + Item.itemRegistry.getNameForObject(item) + ":" +
                    (item.getMaxDamage() > 0 ? 0 : stack.getMetadata());
        }
        int durability = item.getMaxDamage() > 0 ? Math.max(0, stack.getMaxDamage() - stack.getItemDamage()) : 0;
        quality += enchant(stack, Enchantment.unbreaking) * .001;
        return new InventoryPlanner.Stack(slot, stack.stackSize, role, quality, durability, group,
                protectedItem(stack), blacklisted, block);
    }
    private static boolean safeFood(ItemStack stack) {
        Item item = stack.getItem();
        return item != Items.rotten_flesh && item != Items.spider_eye && item != Items.poisonous_potato &&
                !(item == Items.fish && stack.getMetadata() == 3);
    }
    private static int enchant(ItemStack stack, Enchantment enchantment) {
        return EnchantmentHelper.getEnchantmentLevel(enchantment.effectId, stack);
    }
    private InventoryItemClassifier() { }
}
