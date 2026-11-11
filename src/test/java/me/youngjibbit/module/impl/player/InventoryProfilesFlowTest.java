package me.youngjibbit.module.impl.player;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.inventory.*;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.Module;
import me.youngjibbit.utils.TimerUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.*;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class InventoryProfilesFlowTest {
    @Test public void stealerTimingIsLatchedWhileItsSessionOrderStaysStable() throws Exception { Wave2IsolatedCase.run(Checks.class, "stealerTimingIsLatchedWhileItsSessionOrderStaysStable"); }
    @Test public void refilledFirstSlotCannotStarveUnvisitedChestContents() throws Exception { Wave2IsolatedCase.run(Checks.class, "refilledFirstSlotCannotStarveUnvisitedChestContents"); }
    @Test public void stealerClickWaitsForPresentationAndAckAcrossToggle() throws Exception { Wave2IsolatedCase.run(Checks.class, "stealerClickWaitsForPresentationAndAckAcrossToggle"); }
    @Test public void inventoryClickUsesNativeSwapWithoutLegacyOpenOrClosePackets() throws Exception { Wave2IsolatedCase.run(Checks.class, "inventoryClickUsesNativeSwapWithoutLegacyOpenOrClosePackets"); }
    @Test public void dropProfilesOnlyReorderTheFinalSafeSet() throws Exception { Wave2IsolatedCase.run(Checks.class, "dropProfilesOnlyReorderTheFinalSafeSet"); }

    private static Object field(Object owner, String name) throws Exception {
        Field f = owner.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(owner);
    }
    private static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); f.set(owner, value);
    }
    private static final class ReadyTimer extends TimerUtil {
        @Override public boolean hasTimeElapsed(long time, boolean reset) { return true; }
        @Override public boolean hasTimeElapsed(double time, boolean reset) { return true; }
    }
    private static final class ChestScreen extends GuiChest {
        final AtomicLong clock; boolean physicalBusy;
        ChestScreen(RotationBehaviorHookTest.Fixture f, InventoryBasic storage, AtomicLong clock) {
            super(f.player.inventory, storage); this.clock = clock; mc = f.mc;
            width = 640; height = 480; guiLeft = (width - xSize) / 2; guiTop = (height - ySize) / 2;
        }
        @Override protected long inventoryAssistNow() { return clock.get(); }
        @Override protected boolean inventoryAssistPhysicalInputBusy() { return physicalBusy; }
    }
    private static final class PlayerScreen extends GuiInventory {
        final AtomicLong clock;
        PlayerScreen(RotationBehaviorHookTest.Fixture f, AtomicLong clock) {
            super(f.player); this.clock = clock; mc = f.mc;
            width = 640; height = 480; guiLeft = (width - xSize) / 2; guiTop = (height - ySize) / 2;
        }
        @Override protected long inventoryAssistNow() { return clock.get(); }
        @Override protected boolean inventoryAssistPhysicalInputBusy() { return false; }
    }
    private static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f;
        final AtomicLong clock = new AtomicLong(1_000_000_000L);
        Module module;
        Run() throws Exception {
            Bootstrap.register(); f = new RotationBehaviorHookTest.Fixture(); f.mc.gameSettings = new GameSettings();
            set(Minecraft.class, f.mc, "mcThread", Thread.currentThread());
            set(NetHandlerPlayClient.class, f.player.sendQueue, "gameController", f.mc);
            set(EntityPlayerSP.class, f.player, "mc", f.mc);
            f.player.capabilities = new PlayerCapabilities();
            f.player.inventoryContainer = new ContainerPlayer(f.player.inventory, false, f.player);
            f.player.openContainer = f.player.inventoryContainer;
        }
        Stealer chest(int size) throws Exception {
            InventoryBasic storage = new InventoryBasic("Profile fixture", false, size);
            ChestScreen screen = new ChestScreen(f, storage, clock); screen.inventorySlots.windowId = 4;
            f.mc.currentScreen = screen; f.player.openContainer = screen.inventorySlots;
            Stealer stealer = new Stealer(); module = stealer;
            stealer.getNumberSetting("Delay").setValue(0D); stealer.getNumberSetting("Open Delay").setValue(0D);
            stealer.getBooleanSetting("Auto Close").setValue(false); stealer.getBooleanSetting("Randomness").setValue(false);
            set(Stealer.class, stealer, "openDelayTimer", new ReadyTimer());
            clocks(stealer); return stealer;
        }
        InventoryManager inventory() throws Exception {
            InventoryManager manager = new InventoryManager(TrashItemStore.memory()); module = manager;
            f.mc.currentScreen = new PlayerScreen(f, clock); f.mc.inGameHasFocus = false;
            manager.minDelay.setValue(0D); manager.maxDelay.setValue(0D); manager.startDelay.setValue(0D); manager.autoArmor.setValue(false);
            set(InventoryManager.class, manager, "startDelayTimer", new ReadyTimer()); clocks(manager); return manager;
        }
        void clocks(Module owner) throws Exception {
            set(owner.getClass(), owner, "actionPacing", new InventoryPacing(clock::get, 99));
            set(owner.getClass(), owner, "assistance", new InventoryAssistSession(owner.getName(), clock::get));
        }
        InventoryBasic storage() { return (InventoryBasic)((ContainerChest)f.player.openContainer).getLowerChestInventory(); }
        void update() { Pulsar.BUS.post(new EventUpdate()); }
        void next() { f.player.ticksExisted++; }
        void present() {
            GuiContainer screen = (GuiContainer)f.mc.currentScreen; assertNotNull(screen.inventoryAssistPreview());
            screen.inventoryAssistWasRendered(screen.inventoryAssistPreview().token, clock.get()); clock.addAndGet(100_000_000L);
        }
        List<Object> packets() { List<Object> out = new ArrayList<>(); Object p; while ((p = f.channel.readOutbound()) != null) out.add(p); return out; }
        C0EPacketClickWindow onlyClick() { List<Object> packets = packets(); assertEquals(packets.toString(), 1, packets.size()); assertTrue(packets.get(0) instanceof C0EPacketClickWindow); return (C0EPacketClickWindow)packets.get(0); }
        @Override public void close() throws Exception {
            try { if (module != null) { module.setToggled(false); ((InventoryAssistSession)field(module, "assistance")).clear(); } }
            finally { f.close(); }
        }
    }
    public static final class Checks {
        @Test public void stealerTimingIsLatchedWhileItsSessionOrderStaysStable() throws Exception {
            try (Run r = new Run()) {
                Stealer m = r.chest(27); m.getBooleanSetting("Randomness").setValue(true);
                m.getModeSetting("Speed Profile").setValue("Smooth");
                m.getNumberSetting("Min Action Delay MS").setValue(100D); m.getNumberSetting("Max Action Delay MS").setValue(100D);
                r.storage().setInventorySlotContents(0, new ItemStack(Items.diamond, 4));
                r.storage().setInventorySlotContents(8, new ItemStack(Items.iron_ingot, 4));
                r.storage().setInventorySlotContents(17, new ItemStack(Items.gold_ingot, 4)); m.setToggled(true); r.update();
                InventorySlotOrder order = (InventorySlotOrder)field(m, "slotOrder"); List<Integer> initial = new ArrayList<>(order.pendingSlots());
                for (int i = 0; i < 50; i++) r.update();
                assertEquals(initial, order.pendingSlots()); assertTrue(r.packets().isEmpty());
                r.clock.addAndGet(99_000_000L); r.update(); assertTrue(r.packets().isEmpty());
                r.clock.addAndGet(1_000_000L); r.update(); C0EPacketClickWindow first = r.onlyClick(); assertEquals(1, first.getMode());
                for (int i = 0; i < 20; i++) r.update(); assertTrue(r.packets().isEmpty());
                r.next(); r.clock.addAndGet(99_000_000L); r.update(); assertTrue(r.packets().isEmpty());
                r.clock.addAndGet(1_000_000L); r.update(); assertNotEquals(first.getSlotId(), r.onlyClick().getSlotId());
            }
        }
        @Test public void refilledFirstSlotCannotStarveUnvisitedChestContents() throws Exception {
            try (Run r = new Run()) {
                Stealer m = r.chest(27); r.storage().setInventorySlotContents(0, new ItemStack(Items.diamond));
                r.storage().setInventorySlotContents(1, new ItemStack(Items.iron_ingot)); m.setToggled(true);
                r.update(); assertEquals(0, r.onlyClick().getSlotId()); r.storage().setInventorySlotContents(0, new ItemStack(Items.diamond));
                r.next(); r.update(); assertEquals(1, r.onlyClick().getSlotId()); assertNotNull(r.storage().getStackInSlot(0));
            }
        }
        @Test public void stealerClickWaitsForPresentationAndAckAcrossToggle() throws Exception {
            try (Run r = new Run()) {
                Stealer m = r.chest(54); m.getModeSetting("Interaction Mode").setValue("Click");
                r.storage().setInventorySlotContents(0, new ItemStack(Items.diamond)); r.storage().setInventorySlotContents(1, new ItemStack(Items.iron_ingot));
                m.setToggled(true); r.update(); assertTrue(r.packets().isEmpty());
                r.clock.addAndGet(2_000_000_000L); r.next(); r.update(); assertTrue(r.packets().isEmpty());
                r.present(); r.update(); C0EPacketClickWindow first = r.onlyClick(); assertEquals(0, first.getSlotId()); assertEquals(1, first.getMode());
                m.setToggled(false); m.setToggled(true); r.next(); r.update(); assertTrue(r.packets().isEmpty());
                r.f.channel.writeInbound(new S32PacketConfirmTransaction(4, first.getActionNumber(), true));
                r.next(); r.update(); assertTrue(r.packets().isEmpty()); r.present();
                ((ChestScreen)r.f.mc.currentScreen).physicalBusy = true; r.next(); r.update();
                assertTrue(r.packets().isEmpty()); assertNotNull(r.storage().getStackInSlot(1));
            }
        }
        @Test public void inventoryClickUsesNativeSwapWithoutLegacyOpenOrClosePackets() throws Exception {
            try (Run r = new Run()) {
                InventoryManager m = r.inventory(); m.layout.setValue("Legacy"); m.openInv.setValue(false);
                m.getModeSetting("Interaction Mode").setValue("Click");
                r.f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword); m.setToggled(true);
                r.update(); assertTrue(r.packets().isEmpty()); r.present(); r.next(); r.update();
                C0EPacketClickWindow first = r.onlyClick(); assertEquals(2, first.getMode()); assertEquals(12, first.getSlotId());
                assertEquals(Items.diamond_sword, r.f.player.inventory.mainInventory[0].getItem());
                r.f.player.inventory.mainInventory[18] = new ItemStack(Items.bow); r.next(); r.update(); assertTrue(r.packets().isEmpty());
                // The extra item is a real external change; the outstanding prediction must wait for resync.
                r.f.channel.writeInbound(new S32PacketConfirmTransaction(0, first.getActionNumber(), true));
                r.next(); r.update(); assertTrue(r.packets().isEmpty());
                assertTrue(((GuiContainer)r.f.mc.currentScreen).inventoryAssistPreview().warning);
            }
        }
        @Test public void dropProfilesOnlyReorderTheFinalSafeSet() throws Exception {
            try (Run r = new Run()) {
                InventoryManager m = r.inventory(); m.maxBlocks.setValue(8D); m.keepArmor.setValue(true);
                m.getTrashStore().setBlacklisted("minecraft:wool@14", true);
                r.f.player.inventory.mainInventory[9] = new ItemStack(Blocks.wool, 4, 14);
                r.f.player.inventory.mainInventory[10] = new ItemStack(Blocks.wool, 4, 0);
                ItemStack named = new ItemStack(Blocks.wool, 4, 14); named.setStackDisplayName("Keep me"); r.f.player.inventory.mainInventory[11] = named;
                r.f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
                r.f.player.inventory.mainInventory[13] = new ItemStack(Items.wooden_sword);
                Set<Integer> expected = new HashSet<>(Arrays.asList(9, 13));
                for (String profile : new String[]{"Priority", "Shuffle", "Rows", "Nearby"}) {
                    m.getModeSetting("Drop Order").setValue(profile); InventoryManagerSafetyTest.scan(m);
                    List<Integer> order = new ArrayList<>(InventoryManagerBlockRankingTest.trashOf(m));
                    assertEquals(expected, new HashSet<>(order));
                    for (int i = 0; i < 12; i++) { InventoryManagerSafetyTest.scan(m); assertEquals(order, InventoryManagerBlockRankingTest.trashOf(m)); }
                    assertSame(named, r.f.player.inventory.mainInventory[11]);
                }
                assertTrue(r.packets().isEmpty());
            }
        }
    }
}
