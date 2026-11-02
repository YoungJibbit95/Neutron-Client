package me.youngjibbit.core.combat;

import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;

/** Accepted action history. A new movement ends the action group, not an ongoing item use. */
public final class CombatPacketState {
    private boolean action;
    private long usingUntil;

    public void accepted(Packet<?> packet, long now) {
        if (packet instanceof C03PacketPlayer) { action = false; return; }
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement use = (C08PacketPlayerBlockPlacement) packet;
            ItemStack stack = use.getStack();
            if (use.getPlacedBlockDirection() == 255 && stack != null && stack.getItemUseAction() != EnumAction.NONE)
                usingUntil = now + stack.getMaxItemUseDuration() * 50_000_000L;
        } else if (packet instanceof C09PacketHeldItemChange
                || packet instanceof C07PacketPlayerDigging
                && ((C07PacketPlayerDigging) packet).getStatus() == C07PacketPlayerDigging.Action.RELEASE_USE_ITEM) {
            usingUntil = 0;
        }
        if (packet instanceof C02PacketUseEntity || packet instanceof C0APacketAnimation
                || packet instanceof C07PacketPlayerDigging || packet instanceof C08PacketPlayerBlockPlacement
                || packet instanceof C09PacketHeldItemChange || packet instanceof C0EPacketClickWindow
                || packet instanceof C0DPacketCloseWindow || packet instanceof C16PacketClientStatus)
            action = true;
    }

    public boolean mayAttack(long now) { return !action && !usingItem(now); }
    public boolean usingItem(long now) { return usingUntil != 0 && now - usingUntil < 0; }
    public void reset() { action = false; usingUntil = 0; }
}
