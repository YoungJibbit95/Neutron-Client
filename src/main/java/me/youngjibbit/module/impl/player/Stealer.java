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
import me.zero.alpine.listener.Listener;
import me.zero.alpine.listener.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Override public void onValueChange() { clearActivity(); super.onValueChange(); }
    @Subscribe private final Listener<me.youngjibbit.event.world.WorldEvent> activityWorld =
            new Listener<>(event -> clearActivity());
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
    private final TimerUtil actionTimer = new TimerUtil();

    private final SliderValue delay = new SliderValue("Delay", this, 100.0, 0.0, 500.0);
    private final CheckBoxValue autoclose = new CheckBoxValue("Auto Close", this, true);
    private final SliderValue openDelay = new SliderValue("Open Delay", this, 500.0, 0.0, 1000.0, true);
    private final SliderValue autocloseDelay = new SliderValue("Close Delay", this, 300.0, 0.0, 500.0, true);
    private final CheckBoxValue randomItem = new CheckBoxValue("Randomness", this, true);

    public Stealer() {
        super.setKey(Keyboard.KEY_V);
    }

    @Override
    public void onEnable() {
        clearActivity();
        super.onEnable();
        if (mc.currentScreen instanceof GuiChest) {
            openDelayTimer.reset();
            actionTimer.reset();
        }
    }

    @Override
    public void onDisable() {
        clearActivity();
        super.onDisable();
        resetStealer();
    }

    @Subscribe
    private final Listener<EventUpdate> onUpdate = new Listener<>(e -> {
        if (mc.thePlayer == null || mc.theWorld == null || mc.playerController == null) { clearActivity(); return; }
        if (!isToggled() || !(mc.currentScreen instanceof GuiChest)
                || activityContext != null && !activityContext.isCurrent(mc)) clearActivity();
        final long updateActivityGeneration = activityGeneration;
        final ActivityInventoryContext updateActivity = ActivityInventoryContext.capture(mc);
        if (mc.thePlayer.openContainer != null && mc.thePlayer.openContainer instanceof ContainerChest) {
            ContainerChest chest = (ContainerChest) mc.thePlayer.openContainer;
            if (!openDelayTimer.hasTimeElapsed(openDelay.getValue().longValue(), false)) {
                return;
            }
            List<Integer> slots = getSlotsToSteal(chest);
            for (int slot : slots) {
                ItemStack candidate = chest.getLowerChestInventory().getStackInSlot(slot);
                if (candidate != null
                        && actionTimer.hasTimeElapsed(delay.getValue().longValue(), true)) {
                    final long generation = activityGeneration;
                    final int sourceCount = candidate.stackSize;
                    final Object actionPlayer = mc.thePlayer, actionWorld = mc.theWorld,
                            actionScreen = mc.currentScreen, actionController = mc.playerController;
                    final int actionWindow = chest.windowId;
                    mc.playerController.windowClick(chest.windowId, slot, 0, 1, mc.thePlayer);
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
                        }
                    }
                }
            }
            boolean observedEmpty = empty(chest);
            if (observedEmpty && updateActivityGeneration == activityGeneration && updateActivity != null
                    && updateActivity.sameScene(mc)) publishActivity(Action.COMPLETE, false);
            if (observedEmpty && autoclose.getValue()
                    && actionTimer.hasTimeElapsed(autocloseDelay.getValue().longValue(), true)) {
                mc.thePlayer.closeScreen();
                clearActivity();
            }
        } else {
            clearActivity();
            openDelayTimer.reset();
            actionTimer.reset();
        }
    });

    private boolean empty(Container c2) {
        int slot = c2.inventorySlots.size() == 90 ? 54 : 27;
        for (int i2 = 0; i2 < slot; ++i2) {
            if (!c2.getSlot(i2).getHasStack()) continue;
            return false;
        }
        return true;
    }
    private List<Integer> getSlotsToSteal(ContainerChest chest) {
        final long generation = activityGeneration;
        ActivityInventoryContext before = ActivityInventoryContext.capture(mc);
        int occupied = 0, firstOccupied = -1;
        int size = chest.getLowerChestInventory().getSizeInventory();
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            slots.add(i);
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
        if (randomItem.getValue()) {
            Collections.shuffle(slots);
        }

        return slots;
    }

    private void resetStealer() {
        openDelayTimer.reset();
        actionTimer.reset();
    }
}
