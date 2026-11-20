package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.inventory.InventorySlotOrder;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.event.world.WorldEvent;
import me.youngjibbit.module.impl.player.Stealer;
import me.youngjibbit.utils.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

/** Normal update and chest-transfer paths with embedded transport; no synthetic mouse clicks. */
public class StealerSafetyTest {
    public static final class ReadyTimer extends TimerUtil {
        @Override public boolean hasTimeElapsed(long time, boolean reset) { return true; }
        @Override public boolean hasTimeElapsed(double time, boolean reset) { return true; }
    }

    private static final class Controller extends PlayerControllerMP {
        final List<Integer> attempts = new ArrayList<>();
        boolean reject;
        Runnable afterClick;
        Controller(RotationBehaviorHookTest.Fixture f) { super(f.mc, f.player.sendQueue); }
        @Override public ItemStack windowClick(int window, int slot, int button, int mode, EntityPlayer player) {
            attempts.add(slot);
            ItemStack result = reject ? null : super.windowClick(window, slot, button, mode, player);
            if (afterClick != null) afterClick.run();
            return result;
        }
    }

    private static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f;
        final InventoryBasic storage;
        final ContainerChest chest;
        final GuiChest screen;
        final Controller controller;
        final Stealer stealer;
        Run(int size) throws Exception {
            Bootstrap.register();
            f = new RotationBehaviorHookTest.Fixture();
            f.player.capabilities = new PlayerCapabilities();
            f.player.inventoryContainer = new ContainerPlayer(f.player.inventory, false, f.player);
            storage = new InventoryBasic("Safety chest", false, size);
            chest = new ContainerChest(f.player.inventory, storage, f.player);
            chest.windowId = 4;
            f.player.openContainer = chest;
            screen = RotationBehaviorHookTest.bare(GuiChest.class);
            screen.inventorySlots = chest;
            f.mc.currentScreen = screen;
            ScaffoldClientFlowTest.set(Minecraft.class, f.mc, "mcThread", Thread.currentThread());
            controller = new Controller(f); f.mc.playerController = controller;
            stealer = new Stealer();
            stealer.getNumberSetting("Delay").setValue(0D);
            stealer.getNumberSetting("Open Delay").setValue(0D);
            stealer.getBooleanSetting("Randomness").setValue(false);
            stealer.getBooleanSetting("Auto Close").setValue(false);
            ScaffoldClientFlowTest.set(Stealer.class, stealer, "openDelayTimer", new ReadyTimer());
            ScaffoldClientFlowTest.set(Stealer.class, stealer, "emptyTimer", new ReadyTimer());
            stealer.setToggled(true);
        }
        void put(int slot, Item item, int count) { storage.setInventorySlotContents(slot, new ItemStack(item, count)); }
        void update() { Pulsar.BUS.post(new EventUpdate()); }
        void nextTick() { f.player.ticksExisted++; }
        int inventoryCount(Item item) {
            int total = 0;
            for (ItemStack stack : f.player.inventory.mainInventory)
                if (stack != null && stack.getItem() == item) total += stack.stackSize;
            return total;
        }
        int drainClicks() {
            int total = 0; Object packet;
            while ((packet = f.channel.readOutbound()) != null)
                if (packet instanceof C0EPacketClickWindow) total++;
            return total;
        }
        @Override public void close() throws Exception {
            try { stealer.setToggled(false); }
            finally { f.close(); }
        }
    }

    @Test public void buildingBlocksFirstUsesBothChestSizesAndPreservesPickOrderWithinEachGroup() throws Exception {
        for (int size : new int[]{27,54}) try (Run r = new Run(size)) {
            r.stealer.getBooleanSetting("Building Blocks First").setValue(true);
            r.put(0, Items.diamond, 2);
            r.put(1, Item.getItemFromBlock(Blocks.tnt), 64);
            r.put(2, Item.getItemFromBlock(Blocks.chest), 64);
            r.put(3, Item.getItemFromBlock(Blocks.sand), 64);
            r.put(size - 2, Item.getItemFromBlock(Blocks.planks), 48);
            r.put(size - 1, Item.getItemFromBlock(Blocks.wool), 64);
            for (int expected : new int[]{size - 2, size - 1, 0, 1, 2, 3}) {
                r.update(); r.update();
                assertEquals(Integer.valueOf(expected), r.controller.attempts.get(r.controller.attempts.size() - 1));
                assertEquals(1, r.drainClicks());
                assertNull(r.storage.getStackInSlot(expected));
                r.nextTick();
            }
        }
    }

    @Test public void rowsStillBreakTiesAndOldProfilesKeepTheirMixedPickOrder() throws Exception {
        for (boolean prefer : new boolean[]{false,true}) try (Run r = new Run(27)) {
            long seed = 143;
            r.stealer.getBooleanSetting("Randomness").setValue(true);
            r.stealer.getModeSetting("Pick Profile").setValue("Rows");
            if (prefer) r.stealer.getBooleanSetting("Building Blocks First").setValue(true);
            ScaffoldClientFlowTest.set(Stealer.class, r.stealer, "selectionSeeds", new Random(seed));
            List<Integer> order = new InventorySlotOrder(27, "Rows", new Random(seed).nextLong()).slots();
            r.put(order.get(0), Items.diamond, 1);
            r.put(order.get(1), Item.getItemFromBlock(Blocks.planks), 16);
            r.put(order.get(2), Item.getItemFromBlock(Blocks.wool), 32);
            r.update();
            assertEquals(Collections.singletonList(order.get(prefer ? 1 : 0)), r.controller.attempts);
            assertEquals(1, r.drainClicks());
        }
    }

    @Test public void unavailableBuildingBlocksYieldToOneItemThatFitsAndFullInventoryStops() throws Exception {
        try (Run r = new Run(54)) {
            r.stealer.getBooleanSetting("Building Blocks First").setValue(true);
            r.put(0, Item.getItemFromBlock(Blocks.planks), 48);
            r.put(53, Items.diamond, 3);
            for (int i = 0; i < 36; i++) r.f.player.inventory.mainInventory[i] = new ItemStack(Items.stick,64);
            r.f.player.inventory.mainInventory[8] = new ItemStack(Items.diamond, 63);
            r.update(); r.update();
            assertEquals(Collections.singletonList(53), r.controller.attempts);
            assertEquals(1, r.drainClicks());
            assertEquals(2, r.storage.getStackInSlot(53).stackSize);
            assertEquals(48, r.storage.getStackInSlot(0).stackSize);
            r.nextTick(); r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
        }
    }

    @Test public void zeroDelayStillAllowsOnlyOneSourceTransferPerClientTick() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3); r.put(1, Items.iron_ingot, 4); r.put(2, Items.gold_ingot, 5);
            r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(1, r.drainClicks());
            assertNull(r.storage.getStackInSlot(0)); assertNotNull(r.storage.getStackInSlot(1));
            for (int repeat = 0; repeat < 8; repeat++) r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
            r.nextTick(); r.update();
            assertEquals(2, r.controller.attempts.size()); assertEquals(1, r.drainClicks());
            assertNull(r.storage.getStackInSlot(1)); assertNotNull(r.storage.getStackInSlot(2));
            assertEquals(3, r.inventoryCount(Items.diamond)); assertEquals(4, r.inventoryCount(Items.iron_ingot));
        }
    }

    @Test public void aRejectedTransferAlsoConsumesTheCurrentTickBudget() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3); r.controller.reject = true;
            for (int repeat = 0; repeat < 5; repeat++) r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(3, r.storage.getStackInSlot(0).stackSize);
            assertEquals(0, r.drainClicks());
            r.nextTick(); r.update(); assertEquals(2, r.controller.attempts.size());
        }
    }

    @Test public void aHeldCursorStackPausesWithoutMovingOrDroppingTheUsersStack() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3);
            ItemStack cursor = new ItemStack(Items.apple, 2);
            r.f.player.inventory.setItemStack(cursor);
            r.update(); r.nextTick(); r.update();
            assertSame(cursor, r.f.player.inventory.getItemStack()); assertEquals(2, cursor.stackSize);
            assertEquals(0, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
            assertEquals(3, r.storage.getStackInSlot(0).stackSize);
            r.f.player.inventory.setItemStack(null); r.nextTick(); r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(3, r.inventoryCount(Items.diamond));
        }
    }

    @Test public void missingUnboundAndMismatchedScreensCannotAuthorizeAChestTransfer() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3);
            r.f.mc.currentScreen = null; r.update();
            r.f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiChest.class); r.update();
            GuiChest mismatch = RotationBehaviorHookTest.bare(GuiChest.class);
            mismatch.inventorySlots = new ContainerChest(r.f.player.inventory, new InventoryBasic("Other", false, 9), r.f.player);
            r.f.mc.currentScreen = mismatch; r.update();
            GuiInventory wrongType = RotationBehaviorHookTest.bare(GuiInventory.class);
            wrongType.inventorySlots = r.chest; r.f.mc.currentScreen = wrongType; r.update();
            r.f.mc.currentScreen = r.screen; r.f.player.openContainer = r.f.player.inventoryContainer; r.update();
            assertEquals(0, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
            assertEquals(3, r.storage.getStackInSlot(0).stackSize);
            r.f.player.openContainer = r.chest; r.nextTick(); r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(1, r.drainClicks());
        }
    }

    @Test public void fullInventoryPreventsAttemptsAndFreeingOneSlotAllowsOneTransfer() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3);
            for (int slot = 0; slot < r.f.player.inventory.mainInventory.length; slot++)
                r.f.player.inventory.mainInventory[slot] = new ItemStack(Items.stick, 64);
            r.update(); r.nextTick(); r.update();
            assertEquals(0, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
            assertEquals(3, r.storage.getStackInSlot(0).stackSize);
            r.f.player.inventory.mainInventory[8] = null; r.nextTick(); r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(3, r.inventoryCount(Items.diamond));
        }
    }

    @Test public void partialCapacityDoesNotTurnOneAttemptIntoRepeatedModuleClicks() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 6);
            for (int slot = 0; slot < r.f.player.inventory.mainInventory.length; slot++)
                r.f.player.inventory.mainInventory[slot] = new ItemStack(Items.stick, 64);
            r.f.player.inventory.mainInventory[8] = new ItemStack(Items.diamond, 63);
            r.update(); r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(1, r.drainClicks());
            assertEquals(64, r.inventoryCount(Items.diamond)); assertEquals(5, r.storage.getStackInSlot(0).stackSize);
            r.nextTick(); r.update(); assertEquals(1, r.controller.attempts.size());
        }
    }

    @Test public void controllerCallbacksCannotReenterTheActiveTransfer() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3); r.put(1, Items.iron_ingot, 4);
            r.controller.afterClick = () -> { r.update(); r.update(); };
            r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(1, r.drainClicks());
            assertNotNull(r.storage.getStackInSlot(1));
        }
    }

    @Test public void disableDuringControllerCallbackStopsFurtherWorkAndActivityPublication() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3); r.put(1, Items.iron_ingot, 4);
            r.controller.afterClick = () -> { r.stealer.setToggled(false); r.update(); };
            r.update(); r.update(); r.nextTick(); r.update();
            assertFalse(r.stealer.isToggled()); assertNull(r.stealer.getActivitySnapshot());
            assertEquals(1, r.controller.attempts.size()); assertEquals(1, r.drainClicks());
            assertNotNull(r.storage.getStackInSlot(1));
        }
    }

    @Test public void sameTickBudgetSurvivesGuiPlanResetsAndModuleToggles() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3); r.put(1, Items.iron_ingot, 4);
            r.update(); assertEquals(1, r.controller.attempts.size());
            r.f.mc.currentScreen = null; r.update(); r.f.mc.currentScreen = r.screen; r.update();
            assertEquals("returning to the same GUI cannot renew this tick", 1, r.controller.attempts.size());
            r.stealer.setToggled(false); r.stealer.setToggled(true); r.update();
            assertEquals("a toggle cannot renew this tick", 1, r.controller.attempts.size());
            r.f.player.inventory.currentItem = 1; r.update();
            assertEquals("selected-slot context reset cannot renew this tick", 1, r.controller.attempts.size());
            r.nextTick(); r.update(); assertEquals(2, r.controller.attempts.size());
        }
    }

    @Test public void aReentrantCapacityQueryCannotCreateTwoRejectedAttemptsInOneTick() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3); r.controller.reject = true;
            Slot callback = new Slot(r.f.player.inventory, 9, 0, 0) {
                boolean entered;
                @Override public boolean isItemValid(ItemStack candidate) {
                    if (!entered) { entered = true; r.update(); }
                    return super.isItemValid(candidate);
                }
            };
            callback.slotNumber = 27; r.chest.inventorySlots.set(27, callback);
            r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
            assertEquals(3, r.storage.getStackInSlot(0).stackSize);
            r.nextTick(); r.update(); assertEquals(2, r.controller.attempts.size());
        }
    }

    @Test public void unloadDuringCapacityQueryDiscardsThePlanBeforeWorldIdentityChanges() throws Exception {
        try (Run r = new Run(27)) {
            r.put(0, Items.diamond, 3);
            ItemStack source = r.storage.getStackInSlot(0);
            assertFalse(r.stealer.getBooleanSetting("Smart Disable on World Change").getValue());
            int[] capacityReads = {0};
            Slot callback = new Slot(r.f.player.inventory, 9, 0, 0) {
                @Override public boolean isItemValid(ItemStack candidate) {
                    if (++capacityReads[0] == 1) {
                        Pulsar.BUS.post(new WorldEvent.Unload(r.f.mc.theWorld));
                    }
                    return super.isItemValid(candidate);
                }
            };
            callback.slotNumber = 27; r.chest.inventorySlots.set(27, callback);
            r.update();
            assertEquals("the unload callback must actually be reached", 1, capacityReads[0]);
            assertSame("Unload is published before Minecraft changes its world reference", r.f.world, r.f.mc.theWorld);
            assertTrue("the opt-out leaves the module enabled but discards its in-flight plan", r.stealer.isToggled());
            assertEquals(0, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
            assertSame(source, r.storage.getStackInSlot(0)); assertEquals(3, source.stackSize);
            assertEquals(0, r.inventoryCount(Items.diamond));
        }
    }

    @Test public void chestRefreshDuringCapacityCheckRequiresAFreshPlan() throws Exception {
        try (Run r = new Run(54)) {
            ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, r.f.player.sendQueue, "gameController", r.f.mc);
            r.put(0, Items.diamond, 3);
            Slot destination = new Slot(r.f.player.inventory, 9, 0, 0) {
                boolean refreshed;
                @Override public boolean isItemValid(ItemStack candidate) {
                    if (!refreshed) {
                        refreshed = true;
                        r.f.channel.writeInbound(new S2FPacketSetSlot(r.chest.windowId, 53, new ItemStack(Items.apple)));
                    }
                    return super.isItemValid(candidate);
                }
            };
            destination.slotNumber = 54;
            r.chest.inventorySlots.set(54, destination);
            r.update();
            assertSame(Items.apple, r.storage.getStackInSlot(53).getItem());
            assertTrue("a window refresh invalidates the selection in progress", r.controller.attempts.isEmpty());
            assertEquals(0, r.drainClicks());
            assertEquals(3, r.storage.getStackInSlot(0).stackSize);
            r.nextTick(); r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(1, r.drainClicks());
            assertEquals(3, r.inventoryCount(Items.diamond));
            assertEquals(2, r.stealer.getActivitySnapshot().getTotal());
        }
    }

    @Test public void refillingAnEmptyChestDuringTheCloseDelayKeepsTheWindowOpen() throws Exception {
        try (Run r = new Run(27)) {
            ScaffoldClientFlowTest.set(NetHandlerPlayClient.class, r.f.player.sendQueue, "gameController", r.f.mc);
            r.stealer.getBooleanSetting("Auto Close").setValue(true);
            TimerUtil closeTimer = new TimerUtil() {
                @Override public boolean hasTimeElapsed(long time, boolean reset) {
                    r.f.channel.writeInbound(new S2FPacketSetSlot(r.chest.windowId, 26, new ItemStack(Items.apple)));
                    return true;
                }
            };
            ScaffoldClientFlowTest.set(Stealer.class, r.stealer, "emptyTimer", closeTimer);
            r.update();
            assertSame(r.chest, r.f.player.openContainer);
            assertSame(r.screen, r.f.mc.currentScreen);
            assertSame(Items.apple, r.storage.getStackInSlot(26).getItem());
            assertNull(r.stealer.getActivitySnapshot());
            r.nextTick(); r.update();
            assertEquals(1, r.inventoryCount(Items.apple));
        }
    }

    @Test public void aNineSlotChestUsesItsLastChestSlotAndTheNativeTransfer() throws Exception {
        try (Run r = new Run(9)) {
            r.put(8, Items.diamond, 3); r.update();
            assertEquals(1, r.controller.attempts.size()); assertEquals(Integer.valueOf(8), r.controller.attempts.get(0));
            assertEquals(1, r.drainClicks()); assertNull(r.storage.getStackInSlot(8));
            assertEquals(3, r.inventoryCount(Items.diamond));
        }
    }

    @Test public void malformedChestRowsOrLowerSlotMappingsCannotClickPlayerInventory() throws Exception {
        try (Run r = new Run(10)) {
            r.put(9, Items.diamond, 3); r.f.player.inventory.mainInventory[9] = new ItemStack(Items.iron_ingot, 4);
            r.update();
            assertEquals(0, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
            assertEquals(4, r.f.player.inventory.mainInventory[9].stackSize);
            assertEquals(3, r.storage.getStackInSlot(9).stackSize);
        }
        try (Run r = new Run(9)) {
            r.put(0, Items.diamond, 3);
            Slot wrongSource = new Slot(r.f.player.inventory, 9, 0, 0);
            wrongSource.slotNumber = 0; r.chest.inventorySlots.set(0, wrongSource); r.update();
            assertEquals(0, r.controller.attempts.size()); assertEquals(0, r.drainClicks());
            assertEquals(3, r.storage.getStackInSlot(0).stackSize);
        }
    }
}
