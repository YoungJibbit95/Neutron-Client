package me.youngjibbit.module.impl.player;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.inventory.*;
import me.youngjibbit.event.update.EventUpdate;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.*;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import org.junit.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.Assert.*;

public class InventoryManagerOrganizedTest {
    private static final class Clicks extends PlayerControllerMP {
        int calls, mode, wire; boolean fail, recurse;
        Clicks(net.minecraft.client.Minecraft mc) { super(mc, null); }
        @Override public ItemStack windowClick(int window, int slot, int button, int mode, EntityPlayer player) {
            calls++; this.mode = mode; wire = slot;
            if (recurse) Pulsar.BUS.post(new EventUpdate());
            if (fail) throw new IllegalStateException("Synthetic click failure");
            if (mode == 4) { player.inventory.setInventorySlotContents(slot >= 36 ? slot - 36 : slot, null); return null; }
            return player.inventoryContainer.slotClick(slot, button, mode, player);
        }
    }
    private static InventoryManager manager() throws Exception {
        InventoryManager m = new InventoryManager(TrashItemStore.memory());
        m.autoArmor.setValue(false); m.minDelay.setValue(0.0); m.maxDelay.setValue(0.0); m.startDelay.setValue(0.0);
        for (String name : new String[]{"delayTimer", "startDelayTimer"}) {
            Field timer = InventoryManager.class.getDeclaredField(name); timer.setAccessible(true);
            timer.set(m, new InventoryManagerBlockConsumerTest.ZeroDelay());
        }
        return m;
    }
    private static Clicks prepare(RotationBehaviorHookTest.Fixture f) throws Exception {
        f.player.inventoryContainer = new ContainerPlayer(f.player.inventory, false, f.player);
        f.player.openContainer = f.player.inventoryContainer;
        f.mc.currentScreen = new GuiInventory(f.player);
        f.mc.inGameHasFocus = false; // An ordinary open inventory releases gameplay focus.
        Clicks c = new Clicks(f.mc); f.mc.playerController = c; return c;
    }
    @Test public void actualContainerSwapsConvergeToNineConfiguredRolesOneActionPerUpdate() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Clicks clicks = prepare(f); InventoryManager m = manager();
            ItemStack[] items = {new ItemStack(Items.diamond_sword), new ItemStack(Items.bow), new ItemStack(Items.diamond_pickaxe),
                    new ItemStack(Items.diamond_axe), new ItemStack(Blocks.planks, 64), new ItemStack(Blocks.stone, 48),
                    new ItemStack(Items.potionitem, 1, 16389), new ItemStack(Items.golden_apple, 4), new ItemStack(Items.ender_pearl, 16)};
            ItemStack[] expected = new ItemStack[items.length];
            for (int i = 0; i < items.length; i++) expected[i] = items[i].copy();
            for (int i = 0; i < 9; i++) f.player.inventory.mainInventory[9 + i] = items[i];
            ItemStack rod = new ItemStack(Items.fishing_rod); f.player.inventory.mainInventory[25] = rod;
            m.setToggled(true); clicks.recurse = true;
            try {
                for (int i = 0; i < 9; i++) {
                    f.player.ticksExisted++;
                    int before = clicks.calls; Pulsar.BUS.post(new EventUpdate());
                    assertEquals(before + 1, clicks.calls); assertEquals(2, clicks.mode);
                    Pulsar.BUS.post(new EventUpdate()); assertEquals("Second event in the same tick", before + 1, clicks.calls);
                }
                for (int i = 0; i < 9; i++) assertTrue("Complete stack contents on role " + i,
                        ItemStack.areItemStacksEqual(expected[i], f.player.inventory.mainInventory[i]));
                assertSame(rod, f.player.inventory.mainInventory[25]);
                f.player.ticksExisted++; Pulsar.BUS.post(new EventUpdate()); assertEquals(9, clicks.calls);
                assertNull("Organized mode emitted an extra packet", f.channel.readOutbound());
            } finally { m.setToggled(false); }
        }
    }
    @Test public void customRoleReservationMovesStrayItemToFreeMainSlotWithoutDroppingIt() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Clicks clicks = prepare(f); InventoryManager m = manager(); m.layout.setValue("Custom"); m.vacantSlots.setValue("Reserve");
            for (int i = 0; i < 9; i++) m.hotbarRoles[i].setValue("Unassigned"); m.hotbarRoles[3].setValue("Fishing Rod");
            ItemStack bread = new ItemStack(Items.bread, 5); f.player.inventory.mainInventory[3] = bread;
            m.setToggled(true); try {
                Pulsar.BUS.post(new EventUpdate()); assertEquals(1, clicks.calls); assertEquals(2, clicks.mode);
                assertNull(f.player.inventory.mainInventory[3]); assertSame(bread, f.player.inventory.mainInventory[9]);
                f.player.inventory.mainInventory[12] = new ItemStack(Items.fishing_rod);
                f.player.ticksExisted++;
                Pulsar.BUS.post(new EventUpdate()); assertEquals(2, clicks.calls); assertEquals(Items.fishing_rod, f.player.inventory.mainInventory[3].getItem());
            } finally { m.setToggled(false); }
        }
    }
    @Test public void keepArmorDoesNotDisableExplicitTrashAndNamedVariantRemainsProtected() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Clicks clicks = prepare(f); InventoryManager m = manager(); m.keepArmor.setValue(true); m.sort.setValue(false);
            m.getTrashStore().setBlacklisted("minecraft:wool@14", true);
            ItemStack named = new ItemStack(Blocks.wool, 1, 14); named.setStackDisplayName("Protected");
            f.player.inventory.mainInventory[9] = new ItemStack(Blocks.wool, 8, 14);
            f.player.inventory.mainInventory[10] = new ItemStack(Blocks.wool, 8, 0);
            f.player.inventory.mainInventory[11] = named;
            m.setToggled(true); try {
                Pulsar.BUS.post(new EventUpdate()); assertEquals(1, clicks.calls); assertEquals(4, clicks.mode); assertEquals(9, clicks.wire);
                assertNull(f.player.inventory.mainInventory[9]); assertNotNull(f.player.inventory.mainInventory[10]); assertSame(named, f.player.inventory.mainInventory[11]);
            } finally { m.setToggled(false); }
        }
    }
    @Test public void cursorDeathNonInventoryAndForeignContainerPreventActions() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Clicks clicks = prepare(f); InventoryManager m = manager(); f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
            m.setToggled(true); try {
                f.player.inventory.setItemStack(new ItemStack(Items.bread)); Pulsar.BUS.post(new EventUpdate()); assertEquals(0, clicks.calls);
                f.player.inventory.setItemStack(null); f.player.isDead = true; Pulsar.BUS.post(new EventUpdate()); assertEquals(0, clicks.calls);
                f.player.isDead = false; f.mc.currentScreen = null; Pulsar.BUS.post(new EventUpdate()); assertEquals(0, clicks.calls);
                f.mc.currentScreen = new GuiInventory(f.player);
                f.player.openContainer = new ContainerPlayer(f.player.inventory, false, f.player); Pulsar.BUS.post(new EventUpdate()); assertEquals(0, clicks.calls);
                f.player.openContainer = f.player.inventoryContainer;
                ((GuiInventory)f.mc.currentScreen).inventorySlots = new ContainerPlayer(f.player.inventory, false, f.player);
                Pulsar.BUS.post(new EventUpdate()); assertEquals(0, clicks.calls);
                f.mc.currentScreen = new GuiInventory(f.player); Pulsar.BUS.post(new EventUpdate()); assertEquals(1, clicks.calls);
            } finally { m.setToggled(false); }
        }
    }
    @Test public void deferredDropRechecksCursorWorldAndRuleRemoval() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Clicks clicks = prepare(f); InventoryManager m = manager(); m.getTrashStore().setBlacklisted("minecraft:stick@0", true);
            f.player.inventory.mainInventory[12] = new ItemStack(Items.stick, 4);
            InventoryManagerSafetyTest.scan(m); f.player.inventory.setItemStack(new ItemStack(Items.bread));
            assertFalse(InventoryManagerSafetyTest.drop(m, 12)); f.player.inventory.setItemStack(null);
            InventoryManagerSafetyTest.scan(m); f.mc.theWorld = null;
            assertFalse(InventoryManagerSafetyTest.drop(m, 12)); f.mc.theWorld = f.world;
            InventoryManagerSafetyTest.scan(m); m.getTrashStore().setBlacklisted("minecraft:stick@0", false);
            assertFalse(InventoryManagerSafetyTest.drop(m, 12)); assertEquals(0, clicks.calls);
        }
    }
    @Test public void protectedBlacklistedWeaponCannotMakeAnAllowedWeaponTrashInEitherLayout() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); InventoryManager m = manager(); m.getTrashStore().setBlacklisted("minecraft:diamond_sword@*", true);
            ItemStack custom = new ItemStack(Items.diamond_sword); custom.setStackDisplayName("Protected menu item");
            f.player.inventory.mainInventory[10] = custom; f.player.inventory.mainInventory[11] = new ItemStack(Items.iron_sword);
            for (String layout : new String[]{"Organized", "Legacy"}) {
                m.layout.setValue(layout); InventoryManagerSafetyTest.scan(m);
                assertTrue(InventoryManagerBlockRankingTest.trashOf(m).isEmpty());
                assertEquals(11, InventoryManagerSafetyTest.field(m, "bestSwordSlot"));
            }
        }
    }
    @Test public void clickFailureReleasesReentrancyGuardAndNeverQueuesFollowupAction() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Clicks clicks = prepare(f); InventoryManager m = manager(); f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
            m.setToggled(true); clicks.fail = true;
            try {
                // Invoke the real listener directly so event-bus exception handling cannot hide the assertion.
                Object listener = InventoryManagerSafetyTest.field(m, "onUpdate");
                try { ((me.zero.alpine.listener.Listener<EventUpdate>) listener).accept(new EventUpdate()); fail("Expected synthetic click failure"); }
                catch (IllegalStateException expected) { }
                assertEquals(1, clicks.calls); assertEquals(false, InventoryManagerSafetyTest.field(m, "inventoryClickInProgress"));
                clicks.fail = false; Pulsar.BUS.post(new EventUpdate()); assertEquals(1, clicks.calls);
                m.setToggled(false); m.setToggled(true); m.vacantSlots.setValue("Leave");
                Pulsar.BUS.post(new EventUpdate()); assertEquals("Toggle/settings must not restore the spent budget", 1, clicks.calls);
                f.player.ticksExisted++; Pulsar.BUS.post(new EventUpdate()); assertEquals(2, clicks.calls);
                assertEquals(Items.diamond_sword, f.player.inventory.mainInventory[0].getItem());
            } finally { m.setToggled(false); }
        }
    }
    private static final class ClockTimer extends me.youngjibbit.utils.TimerUtil {
        long now = 10000, start; int resets;
        @Override public void reset() { start = now; resets++; }
        @Override public boolean hasTimeElapsed(double duration, boolean reset) { return now - start >= duration; }
    }
    @Test public void startDelayBeginsWithAnEligibleInventoryContextAndDoesNotRestartPerAction() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Clicks clicks = prepare(f); InventoryManager m = manager(); m.startDelay.setValue(20.0);
            ClockTimer clock = new ClockTimer(); Field timer = InventoryManager.class.getDeclaredField("startDelayTimer");
            timer.setAccessible(true); timer.set(m, clock);
            f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
            f.player.inventory.mainInventory[13] = new ItemStack(Items.bow);
            f.player.inventory.mainInventory[14] = new ItemStack(Items.diamond_pickaxe);
            m.setToggled(true); try {
                Pulsar.BUS.post(new EventUpdate()); assertEquals(1, clock.resets); assertEquals(0, clicks.calls);
                clock.now += 199; f.player.ticksExisted++; Pulsar.BUS.post(new EventUpdate()); assertEquals(0, clicks.calls);
                clock.now += 5; f.player.ticksExisted++; Pulsar.BUS.post(new EventUpdate()); assertEquals(1, clicks.calls);
                f.player.ticksExisted++; Pulsar.BUS.post(new EventUpdate()); assertEquals(2, clicks.calls); assertEquals(1, clock.resets);
                f.mc.currentScreen = new GuiInventory(f.player); f.player.ticksExisted++; Pulsar.BUS.post(new EventUpdate());
                assertEquals(2, clock.resets); assertEquals(2, clicks.calls);
                clock.now += 204; f.player.ticksExisted++; Pulsar.BUS.post(new EventUpdate()); assertEquals(3, clicks.calls);
            } finally { m.setToggled(false); }
        }
    }
    @Test public void unloadRevokesLegacyClickEvenBeforeWorldReferenceChangesWithSmartDisableOff() throws Exception {
        Bootstrap.register(); try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            Clicks clicks = prepare(f); InventoryManager m = manager();
            m.layout.setValue("Legacy"); m.openInv.setValue(false);
            ItemStack sword = new ItemStack(Items.diamond_sword); f.player.inventory.mainInventory[12] = sword;
            GuardedInventoryClickTest.Watch watch = new GuardedInventoryClickTest.Watch(); final boolean[] observed = {false};
            watch.action = event -> {
                if (event.isPre() && event.getPacket() instanceof net.minecraft.network.play.client.C0BPacketEntityAction) {
                    observed[0] = true;
                    Pulsar.BUS.post(new me.youngjibbit.event.world.WorldEvent.Unload(f.world));
                    assertSame("Unload notification precedes the world pointer update", f.world, f.mc.theWorld);
                }
            };
            Pulsar.BUS.subscribe(watch); m.setToggled(true);
            try {
                Pulsar.BUS.post(new EventUpdate()); assertTrue(observed[0]);
                assertTrue("Smart Disable remains opt-in", m.isToggled()); assertEquals(0, clicks.calls);
                assertSame(sword, f.player.inventory.mainInventory[12]); assertNull(f.player.inventory.mainInventory[0]);
                assertNull(InventoryManagerSafetyTest.field(m, "trashOwner"));
            } finally { Pulsar.BUS.unsubscribe(watch); m.setToggled(false); }
        }
    }
}
