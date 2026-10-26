package me.youngjibbit.module.impl.player;

import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.block.Block;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.lwjgl.input.Keyboard;

@ModuleInfo(name = "AutoTool", category = Category.Player)
public class AutoTool extends Module {
    private EntityPlayerSP miningPlayer;
    private World miningWorld;
    private int originalSlot = -1;
    private int selectedSlot = -1;
    private boolean manualOverride;

    public AutoTool() {
        super.setKey(Keyboard.KEY_0);
    }

    @Override public void onEnable() {
        discardSelection();
        super.onEnable();
    }

    @Override public void onDisable() {
        finishSelection();
        super.onDisable();
    }

    @Subscribe private final Listener<EventMotion> motion = new Listener<>(event -> {
        if (!event.isPre() || event.isCancelled() || !reconcileSelection()) return;
        Block block = mc.theWorld.getBlockState(mc.objectMouseOver.getBlockPos()).getBlock();
        int current = mc.thePlayer.inventory.currentItem;
        ItemStack held = mc.thePlayer.inventory.getStackInSlot(current);
        float bestSpeed = held == null ? 1f : held.getStrVsBlock(block);
        int bestSlot = current;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
            float speed = stack == null ? 1f : stack.getStrVsBlock(block);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = slot;
            }
        }
        if (bestSpeed <= 1f) {
            finishSelection();
        } else if (bestSlot != current) {
            if (originalSlot < 0) {
                originalSlot = current;
                miningPlayer = mc.thePlayer;
                miningWorld = mc.theWorld;
            }
            selectedSlot = bestSlot;
            mc.thePlayer.inventory.currentItem = bestSlot;
        }
    });

    // Tick/lifecycle cleanup still runs when Tickbase withholds the player motion step.
    @Subscribe private final Listener<TickEvent> tick = new Listener<>(event -> reconcileSelection());
    @Subscribe private final Listener<WorldEvent> world = new Listener<>(event -> discardSelection());

    private boolean reconcileSelection() {
        if (miningPlayer != null && (miningPlayer != mc.thePlayer || miningWorld != mc.theWorld)) {
            discardSelection();
        }
        if (!canMine()) {
            finishSelection();
            return false;
        }
        if (selectedSlot >= 0 && mc.thePlayer.inventory.currentItem != selectedSlot) {
            // A deliberate hotbar change wins until this mining gesture ends.
            discardSelection();
            miningPlayer = mc.thePlayer;
            miningWorld = mc.theWorld;
            manualOverride = true;
        }
        return !manualOverride;
    }

    private boolean canMine() {
        return mc.thePlayer != null && mc.theWorld != null && mc.thePlayer.isEntityAlive()
                && !mc.thePlayer.isSpectator() && mc.currentScreen == null && mc.inGameHasFocus
                && mc.gameSettings != null && mc.gameSettings.keyBindAttack != null
                && mc.gameSettings.keyBindAttack.pressed
                && mc.thePlayer.inventory.currentItem >= 0 && mc.thePlayer.inventory.currentItem < 9
                && mc.objectMouseOver != null
                && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && mc.objectMouseOver.getBlockPos() != null;
    }

    private void finishSelection() {
        if (miningPlayer == mc.thePlayer && miningWorld == mc.theWorld && miningPlayer != null
                && originalSlot >= 0 && originalSlot < 9
                && miningPlayer.inventory.currentItem == selectedSlot) {
            miningPlayer.inventory.currentItem = originalSlot;
        }
        discardSelection();
    }

    private void discardSelection() {
        originalSlot = selectedSlot = -1;
        miningPlayer = null;
        miningWorld = null;
        manualOverride = false;
    }
}
