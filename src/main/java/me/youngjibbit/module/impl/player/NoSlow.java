package me.youngjibbit.module.impl.player;

import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.motion.SlowdownEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.PacketUtils;
import me.youngjibbit.utils.player.MoveUtil;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C09PacketHeldItemChange;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

@ModuleInfo(name = "NoSlow", category = Category.Player)
public class NoSlow extends Module {

    private final ComboValue mode = new ComboValue("Mode", this, new String[]{"Vanilla", "Intave", "Vulcan", "Reduced", "Polar", "Item Switch"}, 0);
    private final SliderValue reduceX = new SliderValue("Reduce X", this, 100.0, 0, 100, true);
    private final SliderValue reduceZ = new SliderValue("Reduce Z", this, 100.0, 0, 100, true);

    private boolean usingItemLastTick;

    @Override
    public void onEnable() {
        usingItemLastTick = false;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        usingItemLastTick = false;
        super.onDisable();
    }

    @Subscribe
    public final Listener<SlowdownEvent> onSlowdown = new Listener<>(event -> {
        if (!shouldHandleSlowdown()) {
            return;
        }

        switch (mode.getValue()) {
            case "Reduced":
                event.setForward(clampPercent(reduceX.getValue().floatValue() / 100.0f));
                event.setStrafe(clampPercent(reduceZ.getValue().floatValue() / 100.0f));
                event.setAllowedSprinting(true);
                break;
            case "Vulcan":
                event.setForward(0.92f);
                event.setStrafe(0.92f);
                event.setAllowedSprinting(true);
                break;
            default:
                event.setForward(1.0f);
                event.setStrafe(1.0f);
                event.setAllowedSprinting(true);
                break;
        }
    });

    @Subscribe
    public final Listener<EventMotion> onMotion = new Listener<>(event -> {
        if (mc.thePlayer == null) {
            usingItemLastTick = false;
            return;
        }

        ItemStack heldItem = getActiveItemStack();
        if (!shouldHandleSlowdown() || heldItem == null) {
            usingItemLastTick = false;
            return;
        }

        switch (mode.getValue()) {
            case "Intave":
                if (event.isPre() && MoveUtil.hasMovementInput(mc.thePlayer)) {
                    Item item = heldItem.getItem();
                    if (isConsumable(item)) {
                        if (!usingItemLastTick) {
                            releaseUse();
                        }
                    } else if (item instanceof ItemSword) {
                        placeUse(heldItem);
                    }
                }
                break;
            case "Item Switch":
                if (event.isPre() && heldItem.getItem() instanceof ItemSword && mc.thePlayer.isBlocking()) {
                    spoofSwitchSlot();
                }
                break;
            case "Polar":
                if (heldItem.getItem() instanceof ItemSword && mc.thePlayer.isBlocking()) {
                    if (event.isPre()) {
                        releaseUse();
                    } else if (event.isPost()) {
                        placeUse(heldItem);
                    }
                }
                break;
            default:
                break;
        }

        usingItemLastTick = true;
    });

    private void spoofSwitchSlot() {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        int nextSlot = (currentSlot + 1) % 9;
        PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(nextSlot));
        PacketUtils.sendPacketNoEvent(new C09PacketHeldItemChange(currentSlot));
    }

    private void releaseUse() {
        PacketUtils.sendPacketNoEvent(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, EnumFacing.DOWN));
    }

    private void placeUse(ItemStack stack) {
        if (stack != null) {
            PacketUtils.sendPacketNoEvent(new C08PacketPlayerBlockPlacement(stack));
        }
    }

    private ItemStack getActiveItemStack() {
        ItemStack stack = SlotHandler.getHeldItem();
        if (stack == null && mc.thePlayer != null) {
            stack = mc.thePlayer.getHeldItem();
        }
        return stack;
    }

    private boolean shouldHandleSlowdown() {
        if (mc.thePlayer == null || mc.theWorld == null || !mc.thePlayer.isUsingItem()) {
            return false;
        }

        ItemStack stack = getActiveItemStack();
        return stack != null && isSupportedItem(stack.getItem());
    }

    private boolean isSupportedItem(Item item) {
        return item instanceof ItemSword || item instanceof ItemBow || isConsumable(item);
    }

    private boolean isConsumable(Item item) {
        return item instanceof ItemFood || item instanceof ItemPotion;
    }

    private float clampPercent(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
