package me.youngjibbit.module.impl.player;

import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.Pulsar;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import java.util.function.Predicate;

/** One vanilla mode-two inventory swap. A local prediction is never a healing authorization. */
final class AutoHealInventoryTransfer implements Subscriber {
    private final Minecraft mc;
    private EntityPlayerSP player;
    private WorldClient world;
    private NetHandlerPlayClient connection;
    private me.youngjibbit.core.network.PacketPipeline pipeline;
    private Pending pending;
    private boolean sending, observing;
    private long revision;
    private static final long TIMEOUT_NANOS = 5_000_000_000L;
    private static final class Pending {
        int source, destination, selected;
        Container container;
        PlayerControllerMP controller;
        ItemStack originalSource, originalDestination, sourceSnapshot, destinationSnapshot;
        ItemStack predictedSource, predictedDestination;
        C0EPacketClickWindow accepted;
        long deadline, revision;
        boolean quarantine, terminal;
    }
    @Subscribe private final Listener<EventPacket> packets = new Listener<>(this::packet);
    @Subscribe private final Listener<WorldEvent> worlds = new Listener<>(e -> clear());
    @Subscribe private final Listener<TickEvent> cleanup = new Listener<>(e -> {
        bind();
        if (pending != null && !pending.terminal && System.nanoTime() >= pending.deadline) {
            pending.quarantine = true;
        }
    });
    private void observe(boolean value) {
        if (observing == value) return;
        observing = value;
        if (value) Pulsar.BUS.subscribe(this); else Pulsar.BUS.unsubscribe(this);
    }
    private void finish() { pending = null; observe(false); }
    AutoHealInventoryTransfer(Minecraft mc) { this.mc = mc; }
    void bind() {
        EntityPlayerSP current = mc.thePlayer;
        if (current != null && current.sendQueue != null && !current.sendQueue.getNetworkManager().isChannelOpen()) { clear(); return; }
        if (current != player || mc.theWorld != world || (current == null ? null : current.sendQueue) != connection ||
                (current == null || current.sendQueue == null ? null : current.sendQueue.getNetworkManager().getClientPackets()) != pipeline) {
            finish(); revision++; player = current; world = mc.theWorld; connection = current == null ? null : current.sendQueue;
            pipeline = connection == null ? null : connection.getNetworkManager().getClientPackets();
        }
    }
    void invalidate() { revision++; if (pending != null) pending.quarantine = true; }
    void clear() { finish(); revision++; player = null; world = null; connection = null; pipeline = null; }
    boolean blocked() {
        bind();
        if (pending == null) return false;
        if (!contextValid(pending) || (pending.accepted != null && !predictedValid(pending)) || (!pending.terminal && System.nanoTime() >= pending.deadline)) pending.quarantine = true;
        return true;
    }
    String status() { return pending == null ? "" : pending.quarantine ? "Waiting for inventory resync" : "Waiting for inventory confirmation"; }
    private boolean contextValid(Pending p) {
        return pending == p && player != null && mc.thePlayer == player && mc.theWorld == world && player.sendQueue == connection &&
                mc.playerController == p.controller && player.openContainer == p.container && p.container == player.inventoryContainer &&
                p.container.windowId == 0 && player.inventory.currentItem == p.selected && player.inventory.getItemStack() == null &&
                p.revision == revision;
    }
    private boolean originalValid(Pending p) {
        return mc.currentScreen == null && mc.inGameHasFocus && player.isEntityAlive() && !player.isSpectator() && !player.isUsingItem() && contextValid(p) && player.inventory.getStackInSlot(p.source) == p.originalSource &&
                player.inventory.getStackInSlot(p.destination) == p.originalDestination &&
                ItemStack.areItemStacksEqual(p.sourceSnapshot, p.originalSource) && ItemStack.areItemStacksEqual(p.destinationSnapshot, p.originalDestination);
    }
    private boolean predictedValid(Pending p) {
        if (!contextValid(p) || (sending && (mc.currentScreen != null || !mc.inGameHasFocus || !player.isEntityAlive() || player.isSpectator() || player.isUsingItem())) || !ItemStack.areItemStacksEqual(p.sourceSnapshot, player.inventory.getStackInSlot(p.destination)) ||
                !ItemStack.areItemStacksEqual(p.destinationSnapshot, player.inventory.getStackInSlot(p.source))) return false;
        if (p.predictedDestination == null) {
            p.predictedSource = player.inventory.getStackInSlot(p.source);
            p.predictedDestination = player.inventory.getStackInSlot(p.destination);
        }
        return player.inventory.getStackInSlot(p.source) == p.predictedSource && player.inventory.getStackInSlot(p.destination) == p.predictedDestination;
    }
    boolean begin(Predicate<ItemStack> healingItem) {
        bind();
        if (pending != null || player == null || world == null || connection == null || mc.playerController == null ||
                player.openContainer == null || player.openContainer != player.inventoryContainer || player.openContainer.windowId != 0 ||
                player.inventory.currentItem < 0 || player.inventory.currentItem >= 9 || player.inventory.getItemStack() != null) return false;
        int source = -1, destination = -1;
        for (int slot = 9; slot < 36; slot++) if (healingItem.test(player.inventory.getStackInSlot(slot))) { source = slot; break; }
        if (source < 0) return false;
        for (int slot = 0; slot < 9; slot++) if (slot != player.inventory.currentItem && player.inventory.getStackInSlot(slot) == null) { destination = slot; break; }
        if (destination < 0) for (int slot = 0; slot < 9; slot++) if (slot != player.inventory.currentItem) { destination = slot; break; }
        final EntityPlayerSP owner = player;
        final WorldClient ownerWorld = world;
        final NetHandlerPlayClient ownerConnection = connection;
        Pending p = new Pending();p.source = source;p.destination = destination;p.selected = player.inventory.currentItem;
        p.container = player.openContainer;p.controller = mc.playerController;p.originalSource = player.inventory.getStackInSlot(source);
        p.originalDestination = player.inventory.getStackInSlot(destination);p.sourceSnapshot = p.originalSource.copy();
        p.destinationSnapshot = p.originalDestination == null ? null : p.originalDestination.copy();p.revision = revision;
        p.deadline = System.nanoTime() + TIMEOUT_NANOS;pending = p;observe(true);sending = true;
        try {
            p.accepted = p.controller.windowClickGuarded(0, source, destination, 2, player, () -> originalValid(p), () -> predictedValid(p));
            if (p.accepted == null) {
                if (pending == p && p.predictedDestination == null && mc.thePlayer == owner && mc.theWorld == ownerWorld && owner.sendQueue == ownerConnection &&
                        owner.inventory.getStackInSlot(p.source) == p.originalSource && owner.inventory.getStackInSlot(p.destination) == p.originalDestination &&
                        ItemStack.areItemStacksEqual(p.sourceSnapshot, p.originalSource) && ItemStack.areItemStacksEqual(p.destinationSnapshot, p.originalDestination)) {
                    finish(); // Rejected before prediction: no packet, mutation, or remote ACK to await.
                }
                // No transport acceptance: undo only our exact untouched local prediction.
                else if (pending == p && mc.thePlayer == owner && mc.theWorld == ownerWorld && owner.sendQueue == ownerConnection &&
                        owner.inventory.getStackInSlot(p.source) == p.predictedSource && owner.inventory.getStackInSlot(p.destination) == p.predictedDestination &&
                        ItemStack.areItemStacksEqual(p.sourceSnapshot, p.predictedDestination) && ItemStack.areItemStacksEqual(p.destinationSnapshot, p.predictedSource) &&
                        ItemStack.areItemStacksEqual(p.sourceSnapshot, p.originalSource)) {
                    owner.inventory.setInventorySlotContents(p.source, p.originalSource);
                    owner.inventory.setInventorySlotContents(p.destination, p.originalDestination);
                    finish();
                } else p.quarantine = true;
            } else if (!predictedValid(p)) p.quarantine = true;
        } finally { sending = false; }
        return true;
    }
    void packet(EventPacket e) {
        bind();Pending p = pending;
        if (p == null || e.getPipeline() != connection.getNetworkManager().getClientPackets()) return;
        if (e.isOutbound() && e.isPre() && e.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow click = (C0EPacketClickWindow)e.getPacket();
            if (!sending || click.getWindowId() != 0 || click.getSlotId() != p.source || click.getUsedButton() != p.destination || click.getMode() != 2) { invalidate(); return; }
        }
        if (!e.isInbound() || !e.isPost()) return;
        if (e.getPacket() instanceof S30PacketWindowItems && ((S30PacketWindowItems)e.getPacket()).func_148911_c() == 0) {
            // A full sync arriving before the click ACK is not that ACK.
            if (p.terminal || p.accepted == null) finish();
            else p.quarantine = true;
            return;
        }
        if (e.getPacket() instanceof net.minecraft.network.play.server.S2FPacketSetSlot) {
            net.minecraft.network.play.server.S2FPacketSetSlot slot = (net.minecraft.network.play.server.S2FPacketSetSlot)e.getPacket();
            if (slot.func_149175_c() == 0 && (slot.func_149173_d() == p.source || slot.func_149173_d() == p.destination + 36)) p.quarantine = true;
        }
        if (e.getPacket() instanceof S32PacketConfirmTransaction && p.accepted != null) {
            S32PacketConfirmTransaction ack = (S32PacketConfirmTransaction)e.getPacket();
            if (ack.getWindowId() == 0 && ack.getActionNumber() == p.accepted.getActionNumber()) {
                if (!p.terminal && System.nanoTime() >= p.deadline) p.quarantine = true;
                p.terminal = true;
                if (ack.func_148888_e() && !p.quarantine && predictedValid(p)) finish();
                else p.quarantine = true;
            }
        }
    }
}
