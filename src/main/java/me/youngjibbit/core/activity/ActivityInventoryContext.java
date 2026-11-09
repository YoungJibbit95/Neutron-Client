package me.youngjibbit.core.activity;

import me.youngjibbit.core.network.PacketPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import java.lang.ref.WeakReference;

/**
 * Opaque guard for a producer's cache. A consumer checks context and at most two
 * already-captured affected stacks; it never searches candidates, counts inventory,
 * queries placement geometry, mutates state or reserves an action.
 */
public final class ActivityInventoryContext {
    private final WeakReference<Minecraft> client;
    private final WeakReference<EntityPlayerSP> player;
    private final WeakReference<Object> world, screen, controller, connection, pipeline;
    private final WeakReference<Container> container, inventoryContainer;
    private final boolean hadScreen, hadConnection, hadPipeline;
    private final long packetEpoch;
    private final int windowId, selectedSlot, slotCount;
    private final int[] slotNumbers;
    private final WeakReference<Slot>[] slots;
    private final WeakReference<ItemStack>[] identities;
    private final ItemStack[] contents;

    @SuppressWarnings("unchecked")
    private ActivityInventoryContext(Minecraft mc, int[] affectedSlots) {
        EntityPlayerSP owner = mc.thePlayer;
        Container observed = owner.openContainer;
        Object handler = owner.sendQueue;
        PacketPipeline packets = owner.sendQueue == null || owner.sendQueue.getNetworkManager() == null
                ? null : owner.sendQueue.getNetworkManager().getClientPackets();
        client = new WeakReference<>(mc); player = new WeakReference<>(owner);
        world = new WeakReference<>(mc.theWorld); screen = new WeakReference<>(mc.currentScreen);
        controller = new WeakReference<>(mc.playerController);
        connection = new WeakReference<>(handler); pipeline = new WeakReference<>(packets);
        hadScreen = mc.currentScreen != null; hadConnection = handler != null; hadPipeline = packets != null;
        packetEpoch = packets == null ? -1 : packets.getContextVersion();
        container = new WeakReference<>(observed); inventoryContainer = new WeakReference<>(owner.inventoryContainer);
        windowId = observed.windowId; selectedSlot = owner.inventory.currentItem;
        slotCount = observed.inventorySlots.size();
        slotNumbers = affectedSlots.clone();
        slots = new WeakReference[affectedSlots.length];
        identities = new WeakReference[affectedSlots.length];
        contents = new ItemStack[affectedSlots.length];
        for (int index = 0; index < affectedSlots.length; index++) {
            Slot slot = observed.getSlot(affectedSlots[index]);
            ItemStack stack = slot == null ? null : slot.getStack();
            slots[index] = new WeakReference<>(slot); identities[index] = new WeakReference<>(stack);
            contents[index] = stack == null ? null : stack.copy();
        }
    }
    /** Actual producer paths only; zero to two exact container slot numbers, never a candidate search. */
    public static ActivityInventoryContext capture(Minecraft mc, int... affectedSlots) {
        if (affectedSlots == null || affectedSlots.length > 2)
            throw new IllegalArgumentException("at most two affected slots");
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.playerController == null
                || mc.thePlayer.inventory == null || mc.thePlayer.openContainer == null
                || mc.thePlayer.inventoryContainer == null || mc.thePlayer.openContainer.inventorySlots == null) return null;
        for (int slot : affectedSlots)
            if (slot < 0 || slot >= mc.thePlayer.openContainer.inventorySlots.size()) return null;
        try {
            ActivityInventoryContext candidate = new ActivityInventoryContext(mc, affectedSlots);
            return candidate.isCurrent(mc) ? candidate : null;
        } catch (IndexOutOfBoundsException changedDuringCapture) { return null; }
    }
    /** Allows a producer to observe its own already-completed local content change. */
    public boolean sameScene(Minecraft mc) {
        EntityPlayerSP owner = player.get();
        Container observed = container.get();
        Object currentPipeline = owner == null || owner.sendQueue == null
                || owner.sendQueue.getNetworkManager() == null ? null
                : owner.sendQueue.getNetworkManager().getClientPackets();
        Object expectedPipeline = pipeline.get();
        return mc != null && mc == client.get() && owner != null && mc.thePlayer == owner
                && !owner.isDead
                && world.get() != null && mc.theWorld == world.get() && owner.worldObj == mc.theWorld
                && controller.get() != null && mc.playerController == controller.get()
                && (!hadScreen || screen.get() != null) && mc.currentScreen == screen.get()
                && (!hadConnection || connection.get() != null) && owner.sendQueue == connection.get()
                && (!hadPipeline || expectedPipeline != null) && currentPipeline == expectedPipeline
                && (!(expectedPipeline instanceof PacketPipeline)
                    || ((PacketPipeline) expectedPipeline).isActive()
                    && ((PacketPipeline) expectedPipeline).getContextVersion() == packetEpoch)
                && observed != null && owner.openContainer == observed && observed.windowId == windowId
                && inventoryContainer.get() != null && owner.inventoryContainer == inventoryContainer.get()
                && owner.inventory != null && owner.inventory.currentItem == selectedSlot
                && observed.inventorySlots != null && observed.inventorySlots.size() == slotCount;
    }
    /** Bounded validation only; inventory totals are calculated exclusively by producer updates. */
    public boolean isCurrent(Minecraft mc) {
        if (!sameScene(mc)) return false;
        Container observed = container.get();
        try {
            for (int index = 0; index < slots.length; index++) {
                Slot slot = slots[index].get();
                ItemStack identity = identities[index].get();
                if (slot == null || observed.getSlot(slotNumbers[index]) != slot || slot.getStack() != identity
                        || contents[index] != null && identity == null
                        || !ItemStack.areItemStacksEqual(contents[index], identity)) return false;
            }
        } catch (IndexOutOfBoundsException changedDuringValidation) { return false; }
        return sameScene(mc);
    }
}
