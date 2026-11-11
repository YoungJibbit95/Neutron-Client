package me.youngjibbit.core.inventory;

import me.youngjibbit.core.activity.ActivityInventoryContext;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.Pulsar;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Visible GUI assistance. No mouse/key injection; one outstanding native operation at a time. */
public final class InventoryAssistSession implements Subscriber {
    private static final AtomicLong TOKENS = new AtomicLong();
    private static final long TIMEOUT = 5_000_000_000L;
    private final LongSupplier clock;
    private final String owner;
    private Pending pending;
    private long inventoryRevision;
    private boolean sending, observing, visible = true;
    @Subscribe private final Listener<EventPacket> packets = new Listener<>(this::packet);
    @Subscribe private final Listener<WorldEvent> worlds = new Listener<>(e -> clear());
    @Subscribe private final Listener<TickEvent> cleanup = new Listener<>(e -> {
        Pending p = pending;
        if (p != null) busy(p.client);
    });
    private void observe(boolean value) {
        if (observing == value) return;
        observing = value;
        if (value) Pulsar.BUS.subscribe(this); else Pulsar.BUS.unsubscribe(this);
    }
    public void resume() { visible = true; }
    public void suspend() {
        visible = false; cancelPreview();
        if (pending != null) pending.screen.clearInventoryAssist(pending.preview.token);
    }

    /** Immutable renderer input. It carries no executable action or inventory search. */
    public static final class Preview {
        public final long token, durationNanos;
        public final String owner, label;
        public final int slot, companion;
        public final boolean waiting, warning;
        private Preview(long token, String owner, String label, int slot, int companion,
                        long durationNanos, boolean waiting, boolean warning) {
            this.token = token; this.owner = owner; this.label = label; this.slot = slot;
            this.companion = companion; this.durationNanos = durationNanos;
            this.waiting = waiting; this.warning = warning;
        }
    }
    private static final class Pending {
        Minecraft client;
        EntityPlayerSP player;
        Object world, controller, connection;
        GuiContainer screen;
        Container container;
        ActivityInventoryContext before, scene;
        long inputRevision, quietNanos, deadline;
        int slot, companion, button, mode, window, selected;
        Preview preview;
        C0EPacketClickWindow accepted;
        ItemStack[] predictedIdentities, predictedCopies;
        boolean sent, failed, terminal, predictionStarted;
    }
    public InventoryAssistSession(String owner) { this(owner, System::nanoTime); }
    public InventoryAssistSession(String owner, LongSupplier clock) { this.owner = owner; this.clock = clock; }

    /** Settings/input cancel a proposal, but cannot erase an outstanding transaction. */
    public void cancelPreview() { if (pending != null && !pending.sent) clear(); }
    public void clear() {
        Pending old = pending; pending = null; observe(false);
        if (old != null) old.screen.clearInventoryAssist(old.preview.token);
    }
    public boolean busy(Minecraft mc) {
        Pending p = pending;
        if (p == null) return false;
        if (!p.scene.sameScene(mc)) { clear(); return false; }
        if (!p.sent) return false;
        if (!p.terminal && clock.getAsLong() - p.deadline >= 0) p.failed = true;
        showWaiting(p);
        return true;
    }
    public boolean ready(Minecraft mc, String label, int slot, int button, int mode, int companion,
                         double previewMs, double resumeMs) {
        if (busy(mc)) return false;
        if (mc == null || !(mc.currentScreen instanceof GuiContainer)) { cancelPreview(); return false; }
        GuiContainer screen = (GuiContainer)mc.currentScreen;
        long quiet = (long)(Math.max(250, Math.min(1000, resumeMs)) * 1_000_000D);
        if (!screen.canInventoryAssist(slot, quiet) || companion >= 0 && !screen.inventoryAssistSlotVisible(companion)) {
            cancelPreview(); return false;
        }
        Pending p = pending;
        if (p == null || p.screen != screen || p.slot != slot || p.companion != companion || p.button != button
                || p.mode != mode || p.inputRevision != screen.inventoryAssistInputRevision() || !p.before.isCurrent(mc)
                || screen.inventoryAssistPreview() == null || screen.inventoryAssistPreview().token != p.preview.token) {
            cancelPreview();
            p = new Pending(); p.client = mc; p.screen = screen; p.container = screen.inventorySlots;
            p.player = mc.thePlayer; p.world = mc.theWorld; p.controller = mc.playerController;
            p.connection = p.player.sendQueue; p.selected = p.player.inventory.currentItem;
            p.window = p.container.windowId; p.slot = slot; p.companion = companion; p.button = button; p.mode = mode;
            p.before = companion < 0 || companion == slot ? ActivityInventoryContext.capture(mc, slot)
                    : ActivityInventoryContext.capture(mc, slot, companion);
            p.scene = ActivityInventoryContext.capture(mc);
            if (p.before == null || p.scene == null) return false;
            p.inputRevision = screen.inventoryAssistInputRevision(); p.quietNanos = quiet;
            p.preview = new Preview(TOKENS.incrementAndGet(), owner, label, slot, companion,
                    (long)(Math.max(50, Math.min(500, previewMs)) * 1_000_000D), false, false);
            pending = p;
            screen.showInventoryAssist(p.preview);
        }
        return proposalMature(p) && beforeValid(p);
    }
    private boolean proposalMature(Pending p) {
        long shown = p.screen.inventoryAssistPresentedAt(p.preview.token);
        return visible && shown != Long.MIN_VALUE && clock.getAsLong() - shown >= p.preview.durationNanos;
    }
    private boolean beforeValid(Pending p) {
        return pending == p && p.before.isCurrent(p.client) && inputValid(p);
    }
    private boolean inputValid(Pending p) {
        return inputStampValid(p) && p.scene.sameScene(p.client)
                && p.screen.canInventoryAssist(p.slot, p.quietNanos)
                && p.scene.sameScene(p.client) && inputStampValid(p);
    }
    /** Callback-free tail: previous slot/eligibility reads may have delivered input or a lifecycle event. */
    private boolean inputStampValid(Pending p) {
        return pending == p && !p.failed && visible && p.client.thePlayer == p.player && p.client.theWorld == p.world
                && p.player.worldObj == p.world && p.client.playerController == p.controller && p.player.sendQueue == p.connection
                && p.client.currentScreen == p.screen && p.player.openContainer == p.container && p.container.windowId == p.window
                && p.player.inventory != null && p.player.inventory.currentItem == p.selected && !p.player.isDead
                && p.screen.inventoryAssistInputRevision() == p.inputRevision
                && p.screen.inventoryAssistPreview() != null && p.screen.inventoryAssistPreview().token == p.preview.token
                && p.screen.inventorySlots == p.container;
    }
    private boolean predictedValid(Pending p) {
        if (!inputValid(p) || p.client.thePlayer.inventory.getItemStack() != null) return false;
        int size = p.container.inventorySlots.size();
        if (size < 1 || size > 90) return false;
        if (p.predictedCopies == null) {
            p.predictedCopies = new ItemStack[size]; p.predictedIdentities = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                ItemStack stack = p.container.getSlot(i).getStack();
                p.predictedIdentities[i] = stack; p.predictedCopies[i] = stack == null ? null : stack.copy();
            }
        }
        return predictionUnchanged(p) && inputValid(p);
    }
    private boolean predictionUnchanged(Pending p) {
        long revision = inventoryRevision;
        if (p.predictedCopies == null || !p.scene.sameScene(p.client)
                || p.container.inventorySlots.size() != p.predictedCopies.length
                || p.client.thePlayer.inventory.getItemStack() != null) return false;
        for (int i = 0; i < p.predictedCopies.length; i++) {
            ItemStack stack = p.container.getSlot(i).getStack();
            if (stack != p.predictedIdentities[i] || !ItemStack.areItemStacksEqual(stack, p.predictedCopies[i])) return false;
        }
        return pending == p && p.scene.sameScene(p.client) && pending == p && revision == inventoryRevision;
    }
    /** Caller supplies its full selection/lifecycle policy and consumes its existing per-tick budget here. */
    public boolean execute(Minecraft mc, BooleanSupplier eligible, BooleanSupplier lifetime, BooleanSupplier claimAttempt) {
        Pending p = pending;
        if (p == null || p.sent || p.client != mc || !proposalMature(p) || !beforeValid(p) || !eligible.getAsBoolean()
                || !beforeValid(p) || !claimAttempt.getAsBoolean()) return false;
        if (!beforeValid(p) || !lifetime.getAsBoolean()) return false;
        p.sent = true; p.deadline = clock.getAsLong() + TIMEOUT; sending = true; observe(true);
        try {
            p.accepted = p.screen.performInventoryAssist(p.slot, p.button, p.mode,
                    () -> {
                        if (!beforeValid(p) || !lifetime.getAsBoolean() || !eligible.getAsBoolean() || !beforeValid(p)) return false;
                        p.predictionStarted = true; return true;
                    }, () -> lifetime.getAsBoolean() && predictedValid(p) && lifetime.getAsBoolean() && inputStampValid(p));
            if (pending != p) return true;
            if (p.accepted == null && !p.predictionStarted) { clear(); return true; }
            if (p.accepted == null) { p.failed = true; p.terminal = true; }
            showWaiting(p);
            return true;
        } catch (RuntimeException failure) {
            if (pending == p) { p.failed = true; p.terminal = true; showWaiting(p); }
            throw failure;
        } finally { sending = false; }
    }
    private void showWaiting(Pending p) {
        String status = p.failed ? "Waiting for resync · reopen inventory" : "Waiting for confirmation";
        if (!p.preview.waiting || p.preview.warning != p.failed) {
            p.preview = new Preview(p.preview.token, owner, status, p.slot, p.companion, 0, true, p.failed);
        }
        if (visible) p.screen.showInventoryAssist(p.preview);
    }
    /** Passive observation only. Vanilla owns ACKs, corrections and all network writes. */
    public void packet(EventPacket event) {
        Pending p = pending;
        if (p == null || !p.sent || p.client.thePlayer == null || p.client.thePlayer.sendQueue == null
                || p.client.thePlayer.sendQueue.getNetworkManager() == null
                || event.getPipeline() != p.client.thePlayer.sendQueue.getNetworkManager().getClientPackets()) return;
        if (!p.scene.sameScene(p.client)) { clear(); return; }
        if (sending && event.isOutbound() && event.isPost() && event.getPacket() instanceof C0EPacketClickWindow) {
            C0EPacketClickWindow click = (C0EPacketClickWindow)event.getPacket();
            if (click.getWindowId() == p.window && click.getSlotId() == p.slot && click.getUsedButton() == p.button && click.getMode() == p.mode)
                p.accepted = click;
        }
        if (event.isOutbound() && event.isPre() && event.getPacket() instanceof C0EPacketClickWindow && !sending) {
            p.failed = true; // A real manual click wins; it is never cancelled by this observer.
        }
        if (!event.isPost() || !event.isInbound()) return;
        if (event.getPacket() instanceof S2FPacketSetSlot) {
            int window = ((S2FPacketSetSlot)event.getPacket()).func_149175_c();
            if (window == p.window || window == -1 || window == -2) { inventoryRevision++; p.failed = true; }
        }
        if (event.getPacket() instanceof S32PacketConfirmTransaction && p.accepted != null) {
            S32PacketConfirmTransaction ack = (S32PacketConfirmTransaction)event.getPacket();
            if (ack.getWindowId() == p.window && ack.getActionNumber() == p.accepted.getActionNumber()) {
                if (!p.terminal && clock.getAsLong() - p.deadline >= 0) p.failed = true;
                p.terminal = true;
                if (ack.func_148888_e() && !p.failed && predictionUnchanged(p) && pending == p && !p.failed) clear();
                else p.failed = true;
            }
        } else if (event.getPacket() instanceof S30PacketWindowItems
                && ((S30PacketWindowItems)event.getPacket()).func_148911_c() == p.window) {
            inventoryRevision++;
            if (p.terminal || p.accepted == null) clear();
            else p.failed = true; // A full snapshot before our ACK is not proof of this action's outcome.
        }
    }
}
