package me.youngjibbit.module.impl.player;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.update.EventUpdate;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import me.youngjibbit.utils.TimerUtil;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.client.C0DPacketCloseWindow;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

/** Exercises the real scanner, deferred actions, container prediction and C0E consumer. */
public class InventoryManagerBlockConsumerTest {
    private static InventoryManager manager() {
        InventoryManager manager = new InventoryManager();
        manager.layout.setValue("Legacy");
        manager.random.setValue(false);
        manager.maxBlocks.setValue(1.0);
        return manager;
    }

    private static void scan(InventoryManager manager) throws Exception {
        InventoryManagerBlockRankingTest.scan(manager);
    }

    private static boolean move(InventoryManager manager) throws Exception {
        Method method = InventoryManager.class.getDeclaredMethod("moveBestBlockStack", int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(manager, 8);
    }

    private static void container(EntityPlayer player) {
        player.inventoryContainer = new ContainerPlayer(player.inventory, false, player);
        player.openContainer = player.inventoryContainer;
    }

    private static void blocks(EntityPlayer player) {
        Arrays.fill(player.inventory.mainInventory, null);
        player.inventory.mainInventory[0] = new ItemStack(Blocks.planks, 48);
        player.inventory.mainInventory[8] = new ItemStack(Blocks.tnt, 4);
    }

    @Test public void equalCountWoodWinsBothBuildAndKeepDecisionsInEitherSlotOrder() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            InventoryManager manager = manager();
            for (int[] slots : new int[][]{{3, 12}, {12, 3}}) {
                Arrays.fill(f.player.inventory.mainInventory, null);
                f.player.inventory.mainInventory[slots[0]] = new ItemStack(Blocks.tnt, 48);
                f.player.inventory.mainInventory[slots[1]] = new ItemStack(Blocks.planks, 48);
                scan(manager);
                assertEquals(slots[1], InventoryManagerBlockRankingTest.field(manager, "bestBlockSlot"));
                assertEquals(Arrays.asList(slots[0]), InventoryManagerBlockRankingTest.trashOf(manager));
            }
        }
    }

    @Test public void overflowKeepsWeakestFirstOrderAndRandomCannotChangeTheCandidateSet() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            InventoryManager manager = manager();
            f.player.inventory.mainInventory[1] = new ItemStack(Blocks.planks, 64);
            f.player.inventory.mainInventory[5] = new ItemStack(Blocks.planks, 48);
            f.player.inventory.mainInventory[9] = new ItemStack(Blocks.tnt, 4);
            manager.maxBlocks.setValue(0.0);
            scan(manager);
            assertEquals(Arrays.asList(9, 5, 1), InventoryManagerBlockRankingTest.trashOf(manager));
            manager.maxBlocks.setValue(1.0);
            manager.random.setValue(true);
            for (int repeat = 0; repeat < 24; repeat++) {
                scan(manager);
                List<Integer> trash = InventoryManagerBlockRankingTest.trashOf(manager);
                assertEquals(2, trash.size());
                assertEquals(new HashSet<>(Arrays.asList(5, 9)), new HashSet<>(trash));
                assertEquals(1, InventoryManagerBlockRankingTest.field(manager, "bestBlockSlot"));
            }
        }
    }

    @Test public void emptyAndHistoricallyExcludedStacksDoNotEnterBlockPlans() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            InventoryManager manager = manager();
            f.player.inventory.mainInventory[0] = new ItemStack(Blocks.planks, 0);
            f.player.inventory.mainInventory[1] = new ItemStack(Blocks.web, 64);
            f.player.inventory.mainInventory[2] = new ItemStack(Blocks.planks, 4);
            scan(manager);
            assertEquals(Arrays.asList(2), InventoryManagerBlockRankingTest.allBlocksOf(manager));
            assertEquals(2, InventoryManagerBlockRankingTest.field(manager, "bestBlockSlot"));
            assertTrue(InventoryManagerBlockRankingTest.trashOf(manager).isEmpty());
            manager.maxBlocks.setValue(8.0);
            scan(manager);
            assertTrue(InventoryManagerBlockRankingTest.trashOf(manager).isEmpty());
        }
    }

    @Test public void copiedReplacementMovedSlotAndChangedCompetitorAbortAPlannedDrop() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            InventoryManager manager = manager();
            InventoryManagerSafetyTest.Drops clicks = new InventoryManagerSafetyTest.Drops(f.mc);
            f.mc.playerController = clicks;
            container(f.player);
            blocks(f.player);
            scan(manager);
            ItemStack planned = f.player.inventory.mainInventory[8];
            f.player.inventory.mainInventory[8] = planned.copy();
            assertFalse("equal values do not authorize a replacement object", InventoryManagerSafetyTest.drop(manager, 8));
            assertEquals(0, clicks.calls);
            f.player.inventory.mainInventory[8] = planned;
            scan(manager);
            f.player.inventory.mainInventory[12] = planned;
            f.player.inventory.mainInventory[8] = null;
            assertFalse(InventoryManagerSafetyTest.drop(manager, 8));
            assertEquals(0, clicks.calls);
            blocks(f.player);
            scan(manager);
            f.player.inventory.mainInventory[0] = null;
            assertFalse("the remaining useful stack becomes the best", InventoryManagerSafetyTest.drop(manager, 8));
            assertEquals(0, clicks.calls);
        }
    }

    @Test public void scanAuthorityRejectsWorldPlayerControllerAndContainerChanges() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            InventoryManager manager = manager();
            InventoryManagerSafetyTest.Drops clicks = new InventoryManagerSafetyTest.Drops(f.mc);
            f.mc.playerController = clicks;
            container(f.player);
            blocks(f.player);
            scan(manager);
            f.mc.theWorld = RotationBehaviorHookTest.bare(WorldClient.class);
            assertFalse(InventoryManagerSafetyTest.drop(manager, 8));
            f.mc.theWorld = f.world;
            scan(manager);
            f.mc.thePlayer = null;
            assertFalse(InventoryManagerSafetyTest.drop(manager, 8));
            f.mc.thePlayer = f.player;
            scan(manager);
            f.mc.playerController = new PlayerControllerMP(f.mc, null);
            assertFalse(InventoryManagerSafetyTest.drop(manager, 8));
            f.mc.playerController = clicks;
            scan(manager);
            Container original = f.player.inventoryContainer;
            f.player.openContainer = new ContainerPlayer(f.player.inventory, false, f.player);
            assertFalse("another container with the same window ID is still foreign", InventoryManagerSafetyTest.drop(manager, 8));
            f.player.openContainer = original;
            scan(manager);
            container(f.player);
            assertFalse("replacing both container references invalidates an old scan", InventoryManagerSafetyTest.drop(manager, 8));
            scan(manager);
            f.player.inventoryContainer.windowId++;
            assertFalse(InventoryManagerSafetyTest.drop(manager, 8));
            assertEquals(0, clicks.calls);
        }
    }

    @Test public void inventoryOpenCallbacksCannotSubstituteTheStackOrReenterTheDrop() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            InventoryManagerSafetyTest.Drops clicks = new InventoryManagerSafetyTest.Drops(f.mc);
            f.mc.playerController = clicks;
            container(f.player);
            blocks(f.player);
            GuardedInventoryClickTest.Watch watch = new GuardedInventoryClickTest.Watch();
            Pulsar.BUS.subscribe(watch);
            try {
                InventoryManager substituted = manager();
                substituted.openInv.setValue(false);
                scan(substituted);
                watch.action = event -> {
                    if (event.isPre() && event.getPacket() instanceof C0BPacketEntityAction)
                        f.player.inventory.mainInventory[8] = f.player.inventory.mainInventory[8].copy();
                };
                assertFalse(InventoryManagerSafetyTest.drop(substituted, 8));
                assertEquals(0, clicks.calls);
                InventoryManager reentered = manager();
                reentered.openInv.setValue(false);
                scan(reentered);
                final boolean[] nested = {true};
                watch.action = event -> {
                    if (event.isPre() && event.getPacket() instanceof C0BPacketEntityAction) {
                        try { nested[0] = InventoryManagerSafetyTest.drop(reentered, 8); }
                        catch (Exception error) { throw new AssertionError(error); }
                    }
                };
                assertTrue(InventoryManagerSafetyTest.drop(reentered, 8));
                assertFalse(nested[0]);
                assertEquals(1, clicks.calls);
            } finally {
                Pulsar.BUS.unsubscribe(watch);
            }
        }
    }

    public static class DropPlayer extends RotationBehaviorHookTest.Player {
        ItemStack dropped;
        @Override public EntityItem dropPlayerItemWithRandomChoice(ItemStack stack, boolean unused) {
            // Keep actual Container.slotClick removal; omit only native world entity creation.
            dropped = stack;
            return null;
        }
    }

    @Test public void realControllerDropsOnlyOverflowAndSendsOneMappedC0E() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            DropPlayer player = RotationBehaviorHookTest.bare(DropPlayer.class);
            player.worldObj = f.world;
            player.inventory = new InventoryPlayer(player);
            f.mc.thePlayer = player;
            container(player);
            blocks(player);
            InventoryManager manager = manager();
            scan(manager);
            ItemStack best = player.inventory.mainInventory[0];
            ItemStack overflow = player.inventory.mainInventory[8];
            assertTrue(InventoryManagerSafetyTest.drop(manager, 8));
            assertSame(best, player.inventory.mainInventory[0]);
            assertNull(player.inventory.mainInventory[8]);
            assertSame(overflow, player.dropped);
            Object packet = f.channel.readOutbound();
            assertTrue(packet instanceof C0EPacketClickWindow);
            C0EPacketClickWindow click = (C0EPacketClickWindow) packet;
            assertEquals(0, click.getWindowId());
            assertEquals(44, click.getSlotId());
            assertEquals(1, click.getUsedButton());
            assertEquals(4, click.getMode());
            assertFalse(InventoryManagerSafetyTest.drop(manager, 8));
            assertNull(f.channel.readOutbound());
        }
    }

    @Test public void realControllerReplacesASmallerHotbarStackWithTheBestBuildStack() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            container(f.player);
            blocks(f.player);
            InventoryManager manager = manager();
            scan(manager);
            ItemStack best = f.player.inventory.mainInventory[0];
            ItemStack displaced = f.player.inventory.mainInventory[8];
            assertTrue(move(manager));
            assertTrue(ItemStack.areItemStacksEqual(best, f.player.inventory.mainInventory[8]));
            assertSame(displaced, f.player.inventory.mainInventory[0]);
            Object packet = f.channel.readOutbound();
            assertTrue(packet instanceof C0EPacketClickWindow);
            C0EPacketClickWindow click = (C0EPacketClickWindow) packet;
            assertEquals(36, click.getSlotId());
            assertEquals(8, click.getUsedButton());
            assertEquals(2, click.getMode());
            assertFalse(move(manager));
            assertNull(f.channel.readOutbound());
        }
    }

    @Test public void inventoryOpenCallbackCannotReplaceEitherSideOfABuildSlotSwap() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            container(f.player);
            GuardedInventoryClickTest.Watch watch = new GuardedInventoryClickTest.Watch();
            Pulsar.BUS.subscribe(watch);
            try {
                for (int changedSlot : new int[]{0, 8}) {
                    blocks(f.player);
                    InventoryManager manager = manager();
                    manager.openInv.setValue(false);
                    scan(manager);
                    watch.action = event -> {
                        if (event.isPre() && event.getPacket() instanceof C0BPacketEntityAction)
                            f.player.inventory.mainInventory[changedSlot] = f.player.inventory.mainInventory[changedSlot].copy();
                    };
                    assertFalse(move(manager));
                    Object packet;
                    while ((packet = f.channel.readOutbound()) != null)
                        assertFalse(packet instanceof C0EPacketClickWindow);
                    assertEquals(48, f.player.inventory.mainInventory[0].stackSize);
                    assertEquals(4, f.player.inventory.mainInventory[8].stackSize);
                }
            } finally {
                Pulsar.BUS.unsubscribe(watch);
            }
        }
    }

    @Test public void disablingOrChangingConfigDuringOpenInvalidatesDropAndSwapWithoutUnlockingReentry() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            container(f.player);
            InventoryManagerSafetyTest.Drops clicks = new InventoryManagerSafetyTest.Drops(f.mc);
            f.mc.playerController = clicks;
            GuardedInventoryClickTest.Watch watch = new GuardedInventoryClickTest.Watch();
            Pulsar.BUS.subscribe(watch);
            try {
                for (int scenario = 0; scenario < 4; scenario++) {
                    final boolean sort = scenario >= 2, disable = scenario % 2 == 0;
                    blocks(f.player);
                    InventoryManager manager = manager();
                    manager.openInv.setValue(false);
                    manager.setToggled(true);
                    scan(manager);
                    watch.action = event -> {
                        if (event.isPre() && event.getPacket() instanceof C0BPacketEntityAction) {
                            if (disable) manager.setToggled(false);
                            else manager.maxBlocks.setValue(2.0);
                            try {
                                assertTrue((Boolean) InventoryManagerBlockRankingTest.field(manager, "inventoryClickInProgress"));
                                assertFalse(InventoryManagerSafetyTest.drop(manager, 8));
                                assertFalse(move(manager));
                            } catch (Exception failure) { throw new AssertionError(failure); }
                        }
                    };
                    try {
                        assertFalse(sort ? move(manager) : InventoryManagerSafetyTest.drop(manager, 8));
                        assertEquals(0, clicks.calls);
                        for (String context : new String[]{"trashOwner", "trashWorld", "trashScreen", "trashContainer", "trashController"})
                            assertNull(InventoryManagerBlockRankingTest.field(manager, context));
                        assertTrue(InventoryManagerBlockRankingTest.trashOf(manager).isEmpty());
                        assertTrue(((java.util.Map<?, ?>) InventoryManagerBlockRankingTest.field(manager, "trashSnapshots")).isEmpty());
                        assertFalse((Boolean) InventoryManagerBlockRankingTest.field(manager, "invOpen"));
                        assertEquals(48, f.player.inventory.mainInventory[0].stackSize);
                        assertEquals(4, f.player.inventory.mainInventory[8].stackSize);
                    } finally {
                        watch.action = event -> { };
                        manager.setToggled(false);
                    }
                }
            } finally { Pulsar.BUS.unsubscribe(watch); }
        }
    }

    @Test public void closeInventoryCallbackInvalidatesTheWholeUpdateBeforeItsScans() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            container(f.player);
            f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
            InventoryManagerSafetyTest.Drops clicks = new InventoryManagerSafetyTest.Drops(f.mc);
            f.mc.playerController = clicks;
            GuardedInventoryClickTest.Watch watch = new GuardedInventoryClickTest.Watch();
            Pulsar.BUS.subscribe(watch);
            try {
                for (int scenario = 0; scenario < 5; scenario++) {
                    final int change = scenario;
                    blocks(f.player);
                    InventoryManager manager = manager();
                    manager.openInv.setValue(false);
                    manager.setToggled(true);
                    scan(manager);
                    Field open = InventoryManager.class.getDeclaredField("invOpen");
                    open.setAccessible(true); open.setBoolean(manager, true);
                    final boolean[] callback = {false};
                    watch.action = event -> {
                        if (event.isPre() && event.getPacket() instanceof C0DPacketCloseWindow) {
                            callback[0] = true;
                            if (change == 0) manager.setToggled(false);
                            if (change == 1) f.mc.theWorld = null;
                            if (change == 2) manager.maxBlocks.setValue(2.0);
                            if (change == 3) f.mc.currentScreen = null;
                            if (change == 4) {
                                try { f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class); }
                                catch (Exception failure) { throw new AssertionError(failure); }
                            }
                        }
                    };
                    try {
                        Pulsar.BUS.post(new EventUpdate());
                        assertTrue(callback[0]);
                        assertEquals(0, clicks.calls);
                        assertTrue(InventoryManagerBlockRankingTest.trashOf(manager).isEmpty());
                        assertNull(InventoryManagerBlockRankingTest.field(manager, "trashOwner"));
                        assertNull(InventoryManagerBlockRankingTest.field(manager, "trashWorld"));
                    } finally {
                        watch.action = event -> { };
                        f.mc.theWorld = f.world;
                        f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
                        manager.setToggled(false);
                    }
                }
            } finally { Pulsar.BUS.unsubscribe(watch); }
        }
    }

    public static class ZeroDelay extends TimerUtil {
        @Override public boolean hasTimeElapsed(double time, boolean reset) { return true; }
    }

    private static void zeroDelay(InventoryManager manager) throws Exception {
        manager.minDelay.setValue(0.0);
        manager.maxDelay.setValue(0.0);
        manager.startDelay.setValue(0.0);
        // Deterministic zero-delay boundary; the wall clock cannot hide a second click.
        for (String name : new String[]{"delayTimer", "startDelayTimer"}) {
            Field timer = InventoryManager.class.getDeclaredField(name);
            timer.setAccessible(true); timer.set(manager, new ZeroDelay());
        }
    }

    @Test public void rejectedDropEndsTheRealUpdateBeforeArmorAndSortAfterDisableOrWorldChange() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            container(f.player);
            f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
            InventoryManagerSafetyTest.Drops clicks = new InventoryManagerSafetyTest.Drops(f.mc);
            f.mc.playerController = clicks;
            GuardedInventoryClickTest.Watch watch = new GuardedInventoryClickTest.Watch();
            Pulsar.BUS.subscribe(watch);
            try {
                for (boolean disable : new boolean[]{true, false}) {
                    blocks(f.player);
                    f.player.inventory.armorInventory[3] = new ItemStack(Items.leather_helmet);
                    f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_helmet);
                    f.player.inventory.mainInventory[13] = new ItemStack(Items.diamond_sword);
                    InventoryManager manager = manager();
                    zeroDelay(manager);
                    manager.openInv.setValue(false);
                    manager.setToggled(true);
                    final boolean[] callback = {false};
                    watch.action = event -> {
                        if (event.isPre() && event.getPacket() instanceof C0BPacketEntityAction) {
                            callback[0] = true;
                            if (disable) manager.setToggled(false);
                            else f.mc.theWorld = null;
                        }
                    };
                    try {
                        Pulsar.BUS.post(new EventUpdate());
                        assertTrue("the production update reached its planned drop", callback[0]);
                        assertEquals("no armor or sort fallback may follow the rejected drop", 0, clicks.calls);
                        assertEquals(48, f.player.inventory.mainInventory[0].stackSize);
                        assertEquals(4, f.player.inventory.mainInventory[8].stackSize);
                        assertEquals(Items.leather_helmet, f.player.inventory.armorInventory[3].getItem());
                    } finally {
                        watch.action = event -> { };
                        f.mc.theWorld = f.world;
                        manager.setToggled(false);
                    }
                }
            } finally { Pulsar.BUS.unsubscribe(watch); }
        }
    }

    @Test public void zeroDelayUpdateMakesOneRealSwapAndRescansTheDisplacedNextCandidate() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            container(f.player);
            f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
            f.player.inventory.mainInventory[0] = new ItemStack(Items.bow);
            f.player.inventory.mainInventory[1] = new ItemStack(Items.diamond_sword);
            InventoryManager manager = manager();
            zeroDelay(manager);
            manager.autoArmor.setValue(false);
            manager.setToggled(true);
            try {
                Pulsar.BUS.post(new EventUpdate());
                assertEquals(Items.diamond_sword, f.player.inventory.mainInventory[0].getItem());
                assertEquals(Items.bow, f.player.inventory.mainInventory[1].getItem());
                Object packet = f.channel.readOutbound();
                assertTrue(packet instanceof C0EPacketClickWindow);
                assertEquals(37, ((C0EPacketClickWindow) packet).getSlotId());
                assertEquals(0, ((C0EPacketClickWindow) packet).getUsedButton());
                assertNull("at most one actual action per update", f.channel.readOutbound());
                f.player.ticksExisted++;
                Pulsar.BUS.post(new EventUpdate());
                assertNull("the bow is already correctly placed after the first swap", f.channel.readOutbound());
                assertEquals(Items.diamond_sword, f.player.inventory.mainInventory[0].getItem());
                assertEquals(Items.bow, f.player.inventory.mainInventory[1].getItem());
            } finally { manager.setToggled(false); }
        }
    }

    @Test public void everyLegacyArmorToolAndGapActionRechecksBothAffectedSlotsAfterOpen() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            container(f.player);
            f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
            InventoryManagerSafetyTest.Drops clicks = new InventoryManagerSafetyTest.Drops(f.mc);
            f.mc.playerController = clicks;
            GuardedInventoryClickTest.Watch watch = new GuardedInventoryClickTest.Watch();
            Pulsar.BUS.subscribe(watch);
            try {
                for (int action = 0; action < 6; action++) {
                    for (boolean replaceCompanion : new boolean[]{false, true}) {
                        final int kind = action;
                        Arrays.fill(f.player.inventory.mainInventory, null);
                        Arrays.fill(f.player.inventory.armorInventory, null);
                        if (kind == 0) f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
                        if (kind == 1) f.player.inventory.mainInventory[12] = new ItemStack(Items.bow);
                        if (kind == 2) f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_pickaxe);
                        if (kind == 3) f.player.inventory.mainInventory[12] = new ItemStack(Items.golden_apple);
                        if (kind >= 4) f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_helmet);
                        if (kind == 4) f.player.inventory.armorInventory[3] = new ItemStack(Items.leather_helmet);
                        final int hotbar = kind == 3 ? 7 : kind;
                        if (kind < 4) f.player.inventory.mainInventory[hotbar] = new ItemStack(Items.stick);
                        InventoryManager manager = manager();
                        zeroDelay(manager);
                        manager.openInv.setValue(false);
                        manager.autoArmor.setValue(kind >= 4);
                        manager.sort.setValue(kind < 4);
                        manager.setToggled(true);
                        final boolean[] callback = {false};
                        watch.action = event -> {
                            if (event.isPre() && event.getPacket() instanceof C0BPacketEntityAction) {
                                callback[0] = true;
                                if (kind == 4 && !replaceCompanion)
                                    f.player.inventory.armorInventory[3] = f.player.inventory.armorInventory[3].copy();
                                else if (kind == 5 && replaceCompanion)
                                    f.player.inventory.armorInventory[3] = new ItemStack(Items.leather_helmet);
                                else {
                                    int slot = replaceCompanion && kind < 4 ? hotbar : 12;
                                    f.player.inventory.mainInventory[slot] = f.player.inventory.mainInventory[slot].copy();
                                }
                            }
                        };
                        try {
                            Pulsar.BUS.post(new EventUpdate());
                            assertTrue("the real action path must reach the callback", callback[0]);
                            assertEquals("a replacement identity invalidates the pending click", 0, clicks.calls);
                        } finally {
                            watch.action = event -> { };
                            manager.setToggled(false);
                        }
                    }
                }
            } finally { Pulsar.BUS.unsubscribe(watch); }
        }
    }

    @Test public void realUpdateRejectsClosedOrReplacedScreenAtEveryInventoryOpenBoundary() throws Exception {
        Bootstrap.register();
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            container(f.player);
            InventoryManagerSafetyTest.Drops clicks = new InventoryManagerSafetyTest.Drops(f.mc);
            f.mc.playerController = clicks;
            GuardedInventoryClickTest.Watch watch = new GuardedInventoryClickTest.Watch();
            Pulsar.BUS.subscribe(watch);
            try {
                for (int action = 0; action < 3; action++) {
                    for (boolean closeScreen : new boolean[]{true, false}) {
                        blocks(f.player);
                        f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
                        final GuiInventory replacement = closeScreen ? null : RotationBehaviorHookTest.bare(GuiInventory.class);
                        InventoryManager manager = manager();
                        zeroDelay(manager);
                        manager.openInv.setValue(false);
                        manager.autoArmor.setValue(false);
                        if (action == 1) manager.maxBlocks.setValue(8.0);
                        if (action == 2) {
                            Arrays.fill(f.player.inventory.mainInventory, null);
                            f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
                            f.player.inventory.mainInventory[0] = new ItemStack(Items.stick);
                        }
                        manager.setToggled(true);
                        final boolean[] callback = {false};
                        watch.action = event -> {
                            if (event.isPre() && event.getPacket() instanceof C0BPacketEntityAction) {
                                callback[0] = true;
                                f.mc.currentScreen = replacement;
                            }
                        };
                        try {
                            Pulsar.BUS.post(new EventUpdate());
                            assertTrue("drop, block swap and legacy sort must each reach their real open boundary", callback[0]);
                            assertEquals("screen identity change revokes the update's click authority", 0, clicks.calls);
                        } finally {
                            watch.action = event -> { };
                            manager.setToggled(false);
                        }
                    }
                }
            } finally { Pulsar.BUS.unsubscribe(watch); }
        }
    }
}
