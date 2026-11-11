package me.youngjibbit.module.impl.player;

import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.core.activity.ActivityInventoryContext;
import me.youngjibbit.core.activity.InventoryActivitySnapshot;
import me.youngjibbit.core.activity.InventoryActivitySnapshot.Action;
import net.minecraft.item.ItemStack;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.utils.TimerUtil;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.SliderValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.core.inventory.*;
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.Slot;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.world.World;
import org.lwjgl.input.Mouse;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Random;
import java.util.List;
import java.lang.ref.WeakReference;

@ModuleInfo(
        name = "Stealer",
        description = "Steals items from chests with delays",
        category = Category.Player
)
public class Stealer extends Module {

    private final Minecraft mc = Minecraft.getMinecraft();
    private InventoryActivitySnapshot activitySnapshot;
    private ActivityInventoryContext activityContext;
    private final java.util.function.LongSupplier activityClock = System::nanoTime;
    private long activityGeneration, activitySequence;
    private int activityProcessed, activityTotal = -1;

    public InventoryActivitySnapshot getActivitySnapshot() {
        InventoryActivitySnapshot cached = activitySnapshot;
        ActivityInventoryContext context = activityContext;
        long now = activityClock.getAsLong();
        if (cached == null || cached != activitySnapshot || mc == null || !isToggled()
                || !(mc.currentScreen instanceof GuiChest) || !cached.isFresh(now)
                || context == null || !context.isCurrent(mc)) {
            if (cached != null && cached == activitySnapshot) clearActivity();
            return null;
        }
        return cached == activitySnapshot ? cached : null;
    }

    private void clearActivity() {
        activityGeneration++;
        activitySnapshot = null; activityContext = null;
        activityProcessed = 0; activityTotal = -1;
    }

    private void publishActivity(Action action, boolean actualAction, int... affectedSlots) {
        long generation = activityGeneration, now = activityClock.getAsLong();
        if (generation != activityGeneration || mc == null || !isToggled() || !(mc.currentScreen instanceof GuiChest)) return;
        ActivityInventoryContext captured = ActivityInventoryContext.capture(mc, affectedSlots);
        if (captured == null || generation != activityGeneration || !isToggled()) return;
        InventoryActivitySnapshot previous = activitySnapshot;
        if (actualAction || previous == null || !previous.hasSameFacts(action, activityProcessed, activityTotal))
            activitySequence++;
        activityContext = captured;
        activitySnapshot = new InventoryActivitySnapshot(InventoryActivitySnapshot.Producer.STEALER,
                action, activityProcessed, activityTotal, activitySequence, now);
    }

    @Override public void onValueChange() { clearActivity(); resetStealer(); super.onValueChange(); }
    @Subscribe private final Listener<me.youngjibbit.event.world.WorldEvent> activityWorld =
            new Listener<>(event -> { clearActivity(); resetStealer(); this.assistance.clear(); });
    @Subscribe private final Listener<me.youngjibbit.event.packet.EventPacket> activityPackets =
            new Listener<>(event -> {
                if (event.isPost() && event.isInbound()
                        && (event.getPacket() instanceof net.minecraft.network.play.server.S2FPacketSetSlot
                        || event.getPacket() instanceof net.minecraft.network.play.server.S30PacketWindowItems
                        || event.getPacket() instanceof net.minecraft.network.play.server.S2DPacketOpenWindow
                        || event.getPacket() instanceof net.minecraft.network.play.server.S2EPacketCloseWindow))
                    clearActivity();
            });
    private final TimerUtil openDelayTimer = new TimerUtil();
    private final InventoryPacing actionPacing = new InventoryPacing();
    private final InventoryAssistSession assistance = new InventoryAssistSession("Stealer");
    private final Random selectionSeeds = new Random();
    private InventorySlotOrder slotOrder;
    private InventoryInteractionSettings interaction;
    private ComboValue pickProfile;
    private final TimerUtil emptyTimer = new TimerUtil();
    private ActivityInventoryContext transferScene;
    private boolean observedEmpty, actionInProgress, updateInProgress;
    private int lastTransferTick = Integer.MIN_VALUE;
    private WeakReference<EntityPlayerSP> lastTransferPlayer = new WeakReference<>(null);
    private WeakReference<World> lastTransferWorld = new WeakReference<>(null);

    private final SliderValue delay = new SliderValue("Delay", this, 100.0, 0.0, 500.0);
    private final CheckBoxValue autoclose = new CheckBoxValue("Auto Close", this, true);
    private final SliderValue openDelay = new SliderValue("Open Delay", this, 500.0, 0.0, 1000.0, true);
    private final SliderValue autocloseDelay = new SliderValue("Close Delay", this, 300.0, 0.0, 500.0, true);
    private final CheckBoxValue randomItem = new CheckBoxValue("Randomness", this, true);

    public Stealer() {
        super.setKey(Keyboard.KEY_V);
        enableSmartDisable();
        interaction = new InventoryInteractionSettings(this, 80, 120);
        pickProfile = new ComboValue("Pick Profile", this, "Shuffle", new String[]{"Shuffle", "Rows", "Nearby"});
        pickProfile.setVisibility(m -> randomItem.getValue());
        delay.setVisibility(m -> interaction.legacySpeed());
    }

    @Override
    public void onEnable() {
        assistance.resume();
        clearActivity();
        resetStealer();
        super.onEnable();
        if (mc.currentScreen instanceof GuiChest) {
            openDelayTimer.reset();
            actionPacing.reset();
        }
    }

    @Override
    public void onDisable() {
        clearActivity();
        super.onDisable();
        resetStealer(); assistance.suspend();
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e -> {
        if (updateInProgress || actionInProgress) return;
        updateInProgress = true;
        try { updateStealer(); }
        finally { updateInProgress = false; }
    });

    private void updateStealer() {
        if (!validChest()) { clearActivity(); resetStealer(); return; }
        if (transferScene == null || !transferScene.sameScene(mc)) {
            clearActivity(); resetStealer(); transferScene = ActivityInventoryContext.capture(mc);
        }
        if (transferScene == null || !transferScene.sameScene(mc)) return;
        if (assistance.busy(mc)) return;
        // A held cursor stack or physical mouse/keyboard/drag action belongs to the user.
        if (mc.thePlayer.inventory.getItemStack() != null || mouseBusy()) {
            actionPacing.reset(); assistance.cancelPreview(); observedEmpty = false; return;
        }
        if (activityContext != null && !activityContext.isCurrent(mc)) clearActivity();
        final ActivityInventoryContext scene = transferScene;
        final long updateActivityGeneration = activityGeneration;
        {
            final ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;
            if (!openDelayTimer.hasTimeElapsed(openDelay.getValue().longValue(), false)) {
                return;
            }
            if (attemptedThisTick()) return;
            List<Integer> slots = getSlotsToSteal(chest);
            if (!validChest() || scene != transferScene || !scene.sameScene(mc) || !isToggled()) return;
            boolean empty = empty(chest);
            if (scene != transferScene || !scene.sameScene(mc)) return;
            if (empty) {
                if (!observedEmpty) { observedEmpty = true; emptyTimer.reset(); }
                if (updateActivityGeneration == activityGeneration) publishActivity(Action.COMPLETE, false);
                if (autoclose.getValue() && emptyTimer.hasTimeElapsed(autocloseDelay.getValue().longValue(), false)
                        && validChest() && scene == transferScene && scene.sameScene(mc) && mc.thePlayer.inventory.getItemStack() == null
                        && !mouseBusy()) {
                    mc.thePlayer.closeScreen(); clearActivity(); resetStealer();
                }
                return;
            }
            observedEmpty = false;
            if (!interaction.ready(actionPacing, delay.getValue(), delay.getValue())) return;
            for (int slot : slots) {
                ItemStack candidate = chest.getLowerChestInventory().getStackInSlot(slot);
                if (candidate != null && canReceive(chest, candidate)) {
                    final long generation = activityGeneration;
                    final int sourceCount = candidate.stackSize;
                    final ActivityInventoryContext exact = ActivityInventoryContext.capture(mc, slot);
                    if (exact == null) return;
                    if (interaction.click() && !assistance.ready(mc, "Transfer", slot, 0, 1, -1,
                            interaction.preview.getValue(), interaction.resume.getValue())) return;
                    final Object actionPlayer = mc.thePlayer, actionWorld = mc.theWorld,
                            actionScreen = mc.currentScreen, actionController = mc.playerController;
                    final int actionWindow = chest.windowId;
                    if (!validChest() || scene != transferScene || !scene.sameScene(mc) || mc.thePlayer.inventory.getItemStack() != null
                            || mouseBusy() || chest.getLowerChestInventory().getStackInSlot(slot) != candidate
                            || scene != transferScene || !scene.sameScene(mc) || attemptedThisTick()) return;
                    boolean attempted;
                    actionInProgress = true;
                    try {
                        if (interaction.click()) {
                            attempted = assistance.execute(mc,
                                    () -> exact.isCurrent(mc) && canReceive(chest, candidate),
                                    () -> validChest() && scene == transferScene && scene.sameScene(mc),
                                    this::claimTransferAttempt);
                        } else {
                            attempted = claimTransferAttempt();
                            if (attempted) mc.playerController.windowClick(chest.windowId, slot, 0, 1, mc.thePlayer);
                        }
                    } finally { actionInProgress = false; }
                    if (!attempted) return;
                    if (slotOrder != null && scene == transferScene) slotOrder.advanceAfter(slot);
                    if (generation == activityGeneration && isToggled() && mc.thePlayer != null
                            && mc.thePlayer == actionPlayer && mc.theWorld == actionWorld && mc.currentScreen == actionScreen
                            && mc.playerController == actionController && mc.thePlayer.openContainer == chest
                            && chest.windowId == actionWindow) {
                        ItemStack after = chest.getLowerChestInventory().getStackInSlot(slot);
                        if (generation == activityGeneration && isToggled() && mc.thePlayer == actionPlayer
                                && mc.theWorld == actionWorld && mc.currentScreen == actionScreen
                                && mc.playerController == actionController && mc.thePlayer.openContainer == chest
                                && chest.windowId == actionWindow) {
                            // Count completed source slots, not click attempts or a partial transfer.
                            boolean changed = sourceCount > 0 && (after == null
                                    || after == candidate && after.stackSize < sourceCount);
                            if (changed && after == null && activityTotal >= 0 && activityProcessed < activityTotal)
                                activityProcessed++;
                            publishActivity(changed ? Action.STEALING : Action.SCANNING, changed, slot);
                            if (changed && empty(chest)) publishActivity(Action.COMPLETE, false);
                        }
                    }
                    // One attempted transfer per client tick, including a rejected/full transfer.
                    // Never continue a stale plan after controller or event callbacks.
                    return;
                }
            }
        }
    }

    private boolean claimTransferAttempt() {
        if (!validChest() || attemptedThisTick() || mouseBusy() || mc.thePlayer.inventory.getItemStack() != null) return false;
        lastTransferTick = mc.thePlayer.ticksExisted;
        lastTransferPlayer = new WeakReference<>(mc.thePlayer); lastTransferWorld = new WeakReference<>(mc.theWorld);
        actionPacing.reset(); return true;
    }

    private boolean attemptedThisTick() {
        return mc.thePlayer != null && lastTransferPlayer.get() == mc.thePlayer
                && lastTransferWorld.get() == mc.theWorld && lastTransferTick == mc.thePlayer.ticksExisted;
    }

    private boolean validChest() {
        if (!isToggled() || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                || mc.thePlayer.isDead || !mc.thePlayer.isEntityAlive() || mc.thePlayer.inventory == null
                || !(mc.currentScreen instanceof GuiChest) || !(mc.thePlayer.openContainer instanceof ContainerChest)) return false;
        ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;
        if (chest.getLowerChestInventory() == null) return false;
        int size = chest.getLowerChestInventory().getSizeInventory();
        if (((GuiContainer) mc.currentScreen).inventorySlots != chest || chest.inventorySlots == null
                || size <= 0 || size > 54 || size % 9 != 0 || chest.inventorySlots.size() != size + 36) return false;
        for (int index = 0; index < size; index++) {
            Slot slot = chest.getSlot(index);
            if (slot == null || !slot.isHere(chest.getLowerChestInventory(), index) || slot.slotNumber != index) return false;
        }
        for (int index = size; index < chest.inventorySlots.size(); index++) {
            Slot slot = chest.getSlot(index);
            if (slot == null || slot.inventory != mc.thePlayer.inventory || slot.slotNumber != index) return false;
        }
        return true;
    }

    private boolean mouseBusy() {
        return mc.currentScreen instanceof GuiContainer && ((GuiContainer)mc.currentScreen).inventoryAssistUserBusy()
                || Mouse.isCreated() && (Mouse.isButtonDown(0) || Mouse.isButtonDown(1));
    }

    private boolean empty(ContainerChest chest) {
        int size = chest.getLowerChestInventory().getSizeInventory();
        for (int slot = 0; slot < size; slot++)
            if (chest.getLowerChestInventory().getStackInSlot(slot) != null) return false;
        return true;
    }

    /** Read-only capacity check; avoid repeatedly issuing clicks against a full inventory. */
    private boolean canReceive(ContainerChest chest, ItemStack candidate) {
        for (int index = chest.getLowerChestInventory().getSizeInventory(); index < chest.inventorySlots.size(); index++) {
            Slot slot = chest.getSlot(index);
            if (slot == null || !slot.isItemValid(candidate)) continue;
            ItemStack current = slot.getStack();
            if (current == null) return true;
            if (ItemStack.areItemsEqual(current, candidate) && ItemStack.areItemStackTagsEqual(current, candidate)
                    && current.stackSize < Math.min(current.getMaxStackSize(), slot.getItemStackLimit(candidate))) return true;
        }
        return false;
    }
    private List<Integer> getSlotsToSteal(ContainerChest chest) {
        final long generation = activityGeneration;
        ActivityInventoryContext before = ActivityInventoryContext.capture(mc);
        int occupied = 0, firstOccupied = -1;
        int size = chest.getLowerChestInventory().getSizeInventory();
        if (slotOrder == null) slotOrder = new InventorySlotOrder(size,
                randomItem.getValue() ? pickProfile.getValue() : "Sequential", selectionSeeds.nextLong());
        List<Integer> slots = slotOrder.pendingSlots();
        for (int i = 0; i < size; i++) {
            if (chest.getLowerChestInventory().getStackInSlot(i) != null) {
                occupied++;
                if (firstOccupied < 0) firstOccupied = i;
            }
        }
        if (before != null && generation == activityGeneration && before.sameScene(mc) && isToggled()
                && mc.thePlayer.openContainer == chest) {
            // A changed observed workload starts a new count; external changes are not our progress.
            if (activityTotal < 0 || occupied != activityTotal - activityProcessed) {
                activityProcessed = 0; activityTotal = occupied;
            }
            if (firstOccupied >= 0) publishActivity(Action.SCANNING, false, firstOccupied);
            else publishActivity(Action.COMPLETE, false);
        }
        return slots;
    }

    private void resetStealer() {
        transferScene = null; observedEmpty = false; slotOrder = null; assistance.cancelPreview();
        openDelayTimer.reset();
        actionPacing.reset();
        emptyTimer.reset();
    }
}
