package me.youngjibbit.core.world;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.activity.ActivityInventoryContext;
import me.youngjibbit.core.activity.InventoryActivitySnapshot;
import me.youngjibbit.core.activity.ScaffoldActivitySnapshot;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.impl.player.InventoryManager;
import me.youngjibbit.module.impl.player.Stealer;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.utils.TimerUtil;
import me.zero.alpine.listener.Listener;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.util.BlockPos;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import static org.junit.Assert.*;

/**
 * Actual module update/controller and Scaffold walking/placement paths.
 * Existing fixtures provide synthetic world storage and embedded transport.
 * Read-only assertions never call a producer scan or an action helper.
 */
@RunWith(Parameterized.class)
public class ActivityProducerFlowTest {
    @Parameterized.Parameters(name = "{0}") public static Collection<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"cacheValidationReadsOnlyItsTwoAffectedStacks"},
                {"inventoryActualSwapHasUnknownTotalAndReadOnlyLifecycle"},
                {"inventoryOpenLifecycleCallbackCannotPublishAnAction"},
                {"stealerCountsActualEmptiedSlotsAndDoesNotInventProgress"},
                {"scaffoldPublishesOriginalIconAndActualPlacementWithoutReaderActions"}
        });
    }
    private final String method;
    public ActivityProducerFlowTest(String method) { this.method = method; }
    @Test public void checkInFreshJvm() throws Exception { Wave2IsolatedCase.run(Checks.class, method); }

    public static final class ReadyTimer extends TimerUtil {
        @Override public boolean hasTimeElapsed(long time, boolean reset) { return true; }
        @Override public boolean hasTimeElapsed(double time, boolean reset) { return true; }
    }
    public static class CountingScene extends ScaffoldClientFlowTest.Scene {
        int reads;
        @Override public IBlockState getBlockState(BlockPos position) { reads++; return super.getBlockState(position); }
    }
    public static class CountingSlot extends Slot {
        int reads;
        CountingSlot(InventoryBasic inventory, int index) { super(inventory, index, 0, 0); }
        @Override public ItemStack getStack() { reads++; return super.getStack(); }
    }
    public static class CountingContainer extends Container {
        final List<CountingSlot> counted = new ArrayList<>();
        CountingContainer(InventoryBasic inventory) {
            for (int index = 0; index < 45; index++) {
                CountingSlot slot = new CountingSlot(inventory, index);
                counted.add(slot); addSlotToContainer(slot);
            }
        }
        @Override public boolean canInteractWith(EntityPlayer player) { return true; }
    }
    public static class RejectingController extends PlayerControllerMP {
        int clicks;
        Runnable onClick;
        RejectingController(RotationBehaviorHookTest.Fixture f) { super(f.mc, f.player.sendQueue); }
        @Override public ItemStack windowClick(int window, int slot, int button, int mode, EntityPlayer player) {
            clicks++;
            if (onClick != null) onClick.run();
            return null;
        }
    }

    public static class Checks {
        private static void inventory(RotationBehaviorHookTest.Fixture f) {
            f.player.capabilities = new PlayerCapabilities();
            f.player.inventoryContainer = new ContainerPlayer(f.player.inventory, false, f.player);
            f.player.openContainer = f.player.inventoryContainer;
        }
        private static int drain(RotationBehaviorHookTest.Fixture f, Class<?> kind) {
            int count = 0; Object packet;
            while ((packet = f.channel.readOutbound()) != null) if (kind.isInstance(packet)) count++;
            return count;
        }
        private static InventoryManager sorter(RotationBehaviorHookTest.Fixture f, AtomicLong clock) throws Exception {
            inventory(f);
            f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
            f.player.inventory.mainInventory[3] = new ItemStack(Blocks.planks, 48, 2);
            f.player.inventory.mainInventory[8] = new ItemStack(Blocks.tnt, 4);
            InventoryManager manager = new InventoryManager();
            manager.layout.setValue("Legacy");
            manager.random.setValue(false); manager.maxBlocks.setValue(8D); manager.autoArmor.setValue(false);
            ScaffoldClientFlowTest.set(InventoryManager.class, manager, "activityClock", (LongSupplier) clock::get);
            ScaffoldClientFlowTest.set(InventoryManager.class, manager, "startDelayTimer", new ReadyTimer());
            manager.minDelay.setValue(0D); manager.maxDelay.setValue(0D);
            return manager;
        }

        @Test public void cacheValidationReadsOnlyItsTwoAffectedStacks() throws Exception {
            Bootstrap.register();
            try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
                InventoryBasic storage = new InventoryBasic("guard", false, 45);
                storage.setInventorySlotContents(3, new ItemStack(Blocks.planks, 48, 2));
                storage.setInventorySlotContents(8, new ItemStack(Blocks.stone, 4));
                CountingContainer container = new CountingContainer(storage);
                f.player.inventoryContainer = container; f.player.openContainer = container;
                ActivityInventoryContext cache = ActivityInventoryContext.capture(f.mc, 3, 8);
                assertNotNull(cache);
                for (CountingSlot slot : container.counted) slot.reads = 0;
                for (int read = 0; read < 100; read++) assertTrue(cache.isCurrent(f.mc));
                for (int index = 0; index < container.counted.size(); index++)
                    assertEquals("unrelated slots are never rescanned by a reader", index == 3 || index == 8 ? 100 : 0,
                            container.counted.get(index).reads);
                storage.getStackInSlot(3).stackSize--;
                assertFalse("in-place contents invalidate the affected fingerprint", cache.isCurrent(f.mc));
                cache = ActivityInventoryContext.capture(f.mc, 3, 8);
                storage.setInventorySlotContents(8, storage.getStackInSlot(8).copy());
                assertFalse("equal replacement values do not preserve stack identity", cache.isCurrent(f.mc));
                cache = ActivityInventoryContext.capture(f.mc, 3, 8);
                f.player.inventory.currentItem = 1;
                assertFalse(cache.isCurrent(f.mc));
                f.player.inventory.currentItem = 0;
                cache = ActivityInventoryContext.capture(f.mc, 3, 8);
                container.windowId++;
                assertFalse(cache.isCurrent(f.mc));
                cache = ActivityInventoryContext.capture(f.mc, 3, 8);
                f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
                assertFalse(cache.isCurrent(f.mc));
                assertNull(f.channel.readOutbound());
            }
        }

        @Test public void inventoryActualSwapHasUnknownTotalAndReadOnlyLifecycle() throws Exception {
            Bootstrap.register();
            try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
                AtomicLong clock = new AtomicLong();
                InventoryManager manager = sorter(f, clock);
                manager.setToggled(true);
                try {
                    assertNull("enable does not fabricate a scan", manager.getActivitySnapshot());
                    Pulsar.BUS.post(new EventUpdate());
                    InventoryActivitySnapshot observed = manager.getActivitySnapshot();
                    assertNotNull(observed);
                    assertEquals(InventoryActivitySnapshot.Producer.INVENTORY_MANAGER, observed.getProducer());
                    assertEquals(InventoryActivitySnapshot.Action.SORTING, observed.getAction());
                    assertEquals(1, observed.getProcessed()); assertEquals(-1, observed.getTotal());
                    assertEquals(48, f.player.inventory.mainInventory[8].stackSize);
                    assertEquals(1, drain(f, C0EPacketClickWindow.class));
                    ItemStack after = f.player.inventory.mainInventory[3];
                    for (int read = 0; read < 100; read++) assertSame(observed, manager.getActivitySnapshot());
                    assertNull("reader sends no C0E or other packet", f.channel.readOutbound());
                    assertSame(after, f.player.inventory.mainInventory[3]);

                    f.player.inventory.mainInventory[3] = after.copy();
                    assertNull(manager.getActivitySnapshot());
                    f.player.inventory.mainInventory[3] = after;
                    assertNull("observed invalidation cannot revive on an ABA restoration", manager.getActivitySnapshot());
                    assertEquals("historical data remain immutable", 1, observed.getProcessed());

                    // The first swap consumed this tick; a fresh producer update belongs to the next one.
                    f.player.ticksExisted++;
                    Pulsar.BUS.post(new EventUpdate());
                    InventoryActivitySnapshot complete = manager.getActivitySnapshot();
                    assertNotNull(complete); assertTrue(complete.isComplete()); assertEquals(-1, complete.getTotal());
                    assertEquals(0, drain(f, C0EPacketClickWindow.class));
                    clock.set(InventoryActivitySnapshot.TTL_NANOS + 1);
                    assertNull("no producer heartbeat means no stale display", manager.getActivitySnapshot());
                    f.player.ticksExisted++;
                    Pulsar.BUS.post(new EventUpdate());
                    assertNotNull(manager.getActivitySnapshot());
                    f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiInventory.class);
                    assertNull("even the same screen type is a different lifetime", manager.getActivitySnapshot());
                    f.player.ticksExisted++;
                    Pulsar.BUS.post(new EventUpdate());
                    assertNotNull(manager.getActivitySnapshot());
                    f.mc.theWorld = RotationBehaviorHookTest.bare(WorldClient.class);
                    assertNull(manager.getActivitySnapshot());
                    f.mc.theWorld = f.world;
                    assertNull(manager.getActivitySnapshot());
                    manager.setToggled(false);
                    assertNull(manager.getActivitySnapshot());
                } finally { manager.setToggled(false); }
            }
        }

        @Test public void inventoryOpenLifecycleCallbackCannotPublishAnAction() throws Exception {
            Bootstrap.register();
            try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
                InventoryManager manager = sorter(f, new AtomicLong());
                manager.openInv.setValue(false);
                Listener<EventPacket> disable = new Listener<>(EventPacket.class, event -> {
                    if (event.isPre() && event.getPacket() instanceof C0BPacketEntityAction)
                        manager.setToggled(false);
                });
                manager.setToggled(true); Pulsar.BUS.subscribe(disable);
                try {
                    Pulsar.BUS.post(new EventUpdate());
                    assertNull(manager.getActivitySnapshot());
                    assertEquals(0, drain(f, C0EPacketClickWindow.class));
                    assertEquals(48, f.player.inventory.mainInventory[3].stackSize);
                } finally { Pulsar.BUS.unsubscribe(disable); manager.setToggled(false); }
            }
        }

        @Test public void stealerCountsActualEmptiedSlotsAndDoesNotInventProgress() throws Exception {
            Bootstrap.register();
            try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
                inventory(f);
                InventoryBasic storage = new InventoryBasic("Chest", true, 27);
                storage.setInventorySlotContents(0, new ItemStack(Items.diamond, 3));
                storage.setInventorySlotContents(1, new ItemStack(Items.gold_ingot, 2));
                ContainerChest chest = new ContainerChest(f.player.inventory, storage, f.player);
                f.player.openContainer = chest;
                f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiChest.class);
                ((GuiChest) f.mc.currentScreen).inventorySlots = chest;
                Stealer stealer = new Stealer();
                stealer.getBooleanSetting("Auto Close").setValue(false);
                stealer.getBooleanSetting("Randomness").setValue(false);
                AtomicLong clock = new AtomicLong();
                ScaffoldClientFlowTest.set(Stealer.class, stealer, "activityClock", (LongSupplier) clock::get);
                ScaffoldClientFlowTest.set(Stealer.class, stealer, "openDelayTimer", new ReadyTimer());
                stealer.getNumberSetting("Delay").setValue(0D);
                stealer.setToggled(true);
                try {
                    assertNull(stealer.getActivitySnapshot());
                    Pulsar.BUS.post(new EventUpdate());
                    InventoryActivitySnapshot first = stealer.getActivitySnapshot();
                    assertNotNull(first); assertFalse(first.isComplete());
                    assertEquals(InventoryActivitySnapshot.Producer.STEALER, first.getProducer());
                    assertEquals(1, first.getProcessed()); assertEquals(2, first.getTotal());
                    assertNull(storage.getStackInSlot(0)); assertNotNull(storage.getStackInSlot(1));
                    assertEquals(1, drain(f, C0EPacketClickWindow.class));
                    Pulsar.BUS.post(new EventUpdate());
                    assertNotNull("the second stack must survive repeated updates in the same client tick", storage.getStackInSlot(1));
                    assertEquals(1, stealer.getActivitySnapshot().getProcessed());
                    assertEquals(0, drain(f, C0EPacketClickWindow.class));
                    f.player.ticksExisted++;
                    Pulsar.BUS.post(new EventUpdate());
                    InventoryActivitySnapshot completed = stealer.getActivitySnapshot();
                    assertNotNull(completed); assertTrue(completed.isComplete());
                    assertEquals(InventoryActivitySnapshot.Producer.STEALER, completed.getProducer());
                    assertEquals(2, completed.getProcessed()); assertEquals(2, completed.getTotal());
                    assertNull(storage.getStackInSlot(0)); assertNull(storage.getStackInSlot(1));
                    assertEquals(1, drain(f, C0EPacketClickWindow.class));
                    for (int read = 0; read < 100; read++) assertSame(completed, stealer.getActivitySnapshot());
                    assertNull(f.channel.readOutbound());
                    f.mc.currentScreen = RotationBehaviorHookTest.bare(GuiChest.class);
                    ((GuiChest) f.mc.currentScreen).inventorySlots = chest;
                    assertNull(stealer.getActivitySnapshot());

                    storage.setInventorySlotContents(0, new ItemStack(Items.diamond, 3));
                    RejectingController rejected = new RejectingController(f);
                    f.mc.playerController = rejected;
                    f.player.ticksExisted++;
                    Pulsar.BUS.post(new EventUpdate());
                    InventoryActivitySnapshot attempt = stealer.getActivitySnapshot();
                    assertNotNull(attempt); assertFalse(attempt.isComplete());
                    assertEquals("a rejected local click does not advertise a successful transfer",
                            InventoryActivitySnapshot.Action.SCANNING, attempt.getAction());
                    assertEquals(0, attempt.getProcessed()); assertEquals(1, attempt.getTotal());
                    assertEquals(1, rejected.clicks);
                    assertEquals(3, storage.getStackInSlot(0).stackSize);
                    for (int read = 0; read < 100; read++) assertSame(attempt, stealer.getActivitySnapshot());
                    assertEquals(1, rejected.clicks);
                    rejected.onClick = () -> stealer.setToggled(false);
                    f.player.ticksExisted++;
                    Pulsar.BUS.post(new EventUpdate());
                    assertNull("an action callback cannot republish after disable", stealer.getActivitySnapshot());
                    assertEquals(0, attempt.getProcessed());
                } finally { stealer.setToggled(false); }
            }
        }

        @Test public void scaffoldPublishesOriginalIconAndActualPlacementWithoutReaderActions() throws Exception {
            try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
                ScaffoldClientFlowTest.Scene prepared = ScaffoldClientFlowTest.prepare(f, .5, .48);
                CountingScene scene = RotationBehaviorHookTest.bare(CountingScene.class);
                scene.blocks = prepared.blocks; scene.border = prepared.border; scene.motionTime = prepared.motionTime;
                f.mc.theWorld = scene; f.player.worldObj = scene;
                inventory(f);
                f.player.inventory.mainInventory[3] = new ItemStack(Blocks.planks, 48, 2);
                ScaffoldClientFlowTest.set(Scaffold.class, f.scaffold, "activityClock", (LongSupplier) scene.motionTime::get);
                f.scaffold.onEnable();
                try {
                    assertNull("enable preview is not a completed producer update", f.scaffold.getActivitySnapshot());
                    int placements = 0;
                    for (int tick = 0; tick < 12 && !scene.blocks.containsKey(new BlockPos(0, 63, 0)); tick++) {
                        scene.motionTime.addAndGet(50_000_000L);
                        f.runtime.getScaffoldPlacementService().beginTick();
                        ScaffoldClientFlowTest.ordinaryWalking(f);
                        placements += drain(f, C08PacketPlayerBlockPlacement.class);
                        ScaffoldActivitySnapshot pending = f.scaffold.getActivitySnapshot();
                        if (!scene.blocks.containsKey(new BlockPos(0, 63, 0)) && pending != null)
                            assertNotEquals("legacy plans never invent accepted readiness",
                                    ScaffoldActivitySnapshot.Status.READY, pending.getStatus());
                    }
                    assertEquals(1, placements);
                    assertTrue(scene.blocks.containsKey(new BlockPos(0, 63, 0)));
                    ScaffoldActivitySnapshot placed = f.scaffold.getActivitySnapshot();
                    assertNotNull(placed);
                    assertEquals(ScaffoldActivitySnapshot.Status.PLACING, placed.getStatus());
                    assertEquals(Item.getIdFromItem(Item.getItemFromBlock(Blocks.planks)), placed.getItemId());
                    assertEquals(2, placed.getMetadata()); assertEquals(47, placed.getBlockCount());
                    assertEquals("Neutron", placed.getApproach());
                    int reads = scene.reads;
                    for (int read = 0; read < 100; read++) assertSame(placed, f.scaffold.getActivitySnapshot());
                    assertEquals("cache reads perform no world or placement query", reads, scene.reads);
                    assertNull("cache reads send no C03/C08 or other packet", f.channel.readOutbound());
                    assertEquals(47, f.player.inventory.mainInventory[3].stackSize);

                    f.player.inventory.mainInventory[3].setItemDamage(3);
                    assertNull(f.scaffold.getActivitySnapshot());
                    assertEquals(2, placed.getMetadata()); assertEquals(47, placed.getBlockCount());
                    f.player.inventory.mainInventory[3].setItemDamage(2);
                    assertNull(f.scaffold.getActivitySnapshot());
                    scene.motionTime.addAndGet(50_000_000L);
                    f.runtime.getScaffoldPlacementService().beginTick(); ScaffoldClientFlowTest.ordinaryWalking(f);
                    ScaffoldActivitySnapshot idle = f.scaffold.getActivitySnapshot();
                    assertNotNull(idle);
                    scene.motionTime.addAndGet(ScaffoldActivitySnapshot.TTL_NANOS + 1);
                    assertNull(f.scaffold.getActivitySnapshot());
                    f.scaffold.onDisable();
                    assertNull(f.scaffold.getActivitySnapshot());
                } finally { f.scaffold.onDisable(); }
            }
        }
    }
}
