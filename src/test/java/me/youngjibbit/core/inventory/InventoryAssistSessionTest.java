package me.youngjibbit.core.inventory;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.world.Wave2IsolatedCase;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.world.WorldEvent;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.init.*;
import net.minecraft.inventory.*;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;
import net.minecraft.network.play.server.*;
import org.junit.Test;
import org.lwjgl.input.Keyboard;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

/** Ordinary Java 8 JIT, one real client lifetime per integration case. */
public class InventoryAssistSessionTest {
    @Test public void aRenderedMatureProposalIsRequiredForOneNativeSwap() throws Exception { Wave2IsolatedCase.run(Checks.class, "aRenderedMatureProposalIsRequiredForOneNativeSwap"); }
    @Test public void actualKeyboardAndTouchscreenInputPreemptTheProposal() throws Exception { Wave2IsolatedCase.run(Checks.class, "actualKeyboardAndTouchscreenInputPreemptTheProposal"); }
    @Test public void changedStacksResizeAndChangedScreenRequireAFreshProposal() throws Exception { Wave2IsolatedCase.run(Checks.class, "changedStacksResizeAndChangedScreenRequireAFreshProposal"); }
    @Test public void acknowledgementIsExactAndDisableCannotDiscardAnOutstandingOperation() throws Exception { Wave2IsolatedCase.run(Checks.class, "acknowledgementIsExactAndDisableCannotDiscardAnOutstandingOperation"); }
    @Test public void rejectionAndTimeoutWaitForAppliedResynchronization() throws Exception { Wave2IsolatedCase.run(Checks.class, "rejectionAndTimeoutWaitForAppliedResynchronization"); }
    @Test public void lateInputAndLifecycleCallbacksCannotTransportAPrediction() throws Exception { Wave2IsolatedCase.run(Checks.class, "lateInputAndLifecycleCallbacksCannotTransportAPrediction"); }
    @Test public void closingTheSceneReleasesThePassiveObserverWithoutAnotherAction() throws Exception { Wave2IsolatedCase.run(Checks.class, "closingTheSceneReleasesThePassiveObserverWithoutAnotherAction"); }
    @Test public void lateSlotCallbacksCannotHideInputUnloadOrAuthoritativeChanges() throws Exception { Wave2IsolatedCase.run(Checks.class, "lateSlotCallbacksCannotHideInputUnloadOrAuthoritativeChanges"); }

    private static final class Screen extends GuiInventory {
        final AtomicLong clock;
        Screen(RotationBehaviorHookTest.Fixture f, AtomicLong clock) {
            super(f.player); this.clock = clock; mc = f.mc; width = 640; height = 480;
            guiLeft = (width - xSize) / 2; guiTop = (height - ySize) / 2;
        }
        boolean physicalBusy;
        @Override protected long inventoryAssistNow() { return clock.get(); }
        @Override protected long containerInputTimeMillis() { return clock.get() / 1_000_000L; }
        @Override protected boolean inventoryAssistPhysicalInputBusy() { return physicalBusy; }
        void key(int key) throws Exception { keyTyped(' ', key); }
        void pressSlot(int index) throws Exception {
            Slot slot = inventorySlots.getSlot(index); mouseClicked(guiLeft + slot.xDisplayPosition + 2, guiTop + slot.yDisplayPosition + 2, 0);
        }
    }
    private static final class Watch implements Subscriber {
        java.util.function.Consumer<EventPacket> action = e -> { };
        @Subscribe final Listener<EventPacket> packet = new Listener<>(e -> action.accept(e));
    }
    private static void set(Class<?> type, Object owner, String name, Object value) throws Exception {
        java.lang.reflect.Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(owner, value);
    }
    private static final class Run implements AutoCloseable {
        final RotationBehaviorHookTest.Fixture f;
        final AtomicLong clock = new AtomicLong(1_000_000_000L);
        final AtomicInteger attempts = new AtomicInteger();
        final Screen screen;
        final InventoryAssistSession session = new InventoryAssistSession("Test", clock::get);
        Run() throws Exception {
            Bootstrap.register(); f = new RotationBehaviorHookTest.Fixture();
            set(Minecraft.class, f.mc, "mcThread", Thread.currentThread());
            set(net.minecraft.client.network.NetHandlerPlayClient.class, f.player.sendQueue, "gameController", f.mc);
            set(net.minecraft.client.entity.EntityPlayerSP.class, f.player, "mc", f.mc);
            set(net.minecraft.entity.EntityLivingBase.class, f.player, "activePotionsMap", new HashMap<>());
            f.mc.gameSettings = new net.minecraft.client.settings.GameSettings();
            f.player.capabilities = new PlayerCapabilities();
            f.player.inventoryContainer = new ContainerPlayer(f.player.inventory, false, f.player);
            f.player.openContainer = f.player.inventoryContainer;
            f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
            screen = new Screen(f, clock); f.mc.currentScreen = screen; f.mc.inGameHasFocus = false;
        }
        boolean ready() { return session.ready(f.mc, "Swap", 12, 0, 2, 36, 100, 300); }
        void present() { screen.inventoryAssistWasRendered(screen.inventoryAssistPreview().token, clock.get()); }
        void mature() { assertFalse(ready()); present(); clock.addAndGet(100_000_000L); assertTrue(ready()); }
        boolean execute() { return session.execute(f.mc, () -> true, () -> true, () -> { attempts.incrementAndGet(); return true; }); }
        C0EPacketClickWindow click() {
            Object packet; C0EPacketClickWindow result = null;
            while ((packet = f.channel.readOutbound()) != null) if (packet instanceof C0EPacketClickWindow) {
                assertNull("More than one native click", result); result = (C0EPacketClickWindow)packet;
            }
            return result;
        }
        void sync() {
            List<ItemStack> stacks = new ArrayList<>(); for (Slot slot : f.player.openContainer.inventorySlots) stacks.add(slot.getStack());
            f.channel.writeInbound(new S30PacketWindowItems(0, stacks));
        }
        @Override public void close() throws Exception { try { session.clear(); } finally { f.close(); } }
    }
    public static final class Checks {
        @Test public void aRenderedMatureProposalIsRequiredForOneNativeSwap() throws Exception {
            try (Run r = new Run()) {
                assertFalse(r.execute()); assertFalse(r.ready());
                r.clock.addAndGet(10_000_000_000L); assertFalse(r.ready()); assertFalse(r.execute());
                r.present(); r.clock.addAndGet(99_000_000L); assertFalse(r.ready()); assertFalse(r.execute());
                r.clock.addAndGet(1_000_000L); assertTrue(r.ready());
                long inputRevision = r.screen.inventoryAssistInputRevision(); int selected = r.f.player.inventory.currentItem;
                assertTrue(r.execute()); C0EPacketClickWindow click = r.click(); assertNotNull(click);
                assertEquals(2, click.getMode()); assertEquals(12, click.getSlotId()); assertEquals(0, click.getUsedButton());
                assertEquals(Items.diamond_sword, r.f.player.inventory.mainInventory[0].getItem()); assertNull(r.f.player.inventory.mainInventory[12]);
                assertNull(r.f.player.inventory.getItemStack()); assertEquals(selected, r.f.player.inventory.currentItem);
                assertEquals(inputRevision, r.screen.inventoryAssistInputRevision()); assertEquals(1, r.attempts.get());
                assertFalse(r.execute()); assertTrue(r.session.busy(r.f.mc)); assertNull(r.click());
            }
        }
        @Test public void actualKeyboardAndTouchscreenInputPreemptTheProposal() throws Exception {
            try (Run r = new Run()) {
                r.mature(); r.screen.physicalBusy = true;
                assertFalse(r.execute()); assertEquals(0, r.attempts.get());
                r.screen.physicalBusy = false; r.screen.key(Keyboard.KEY_LSHIFT);
                assertFalse(r.execute()); assertNull(r.screen.inventoryAssistPreview()); assertNull(r.click());
                r.clock.addAndGet(299_000_000L); assertFalse(r.ready());
                r.clock.addAndGet(1_000_000L); r.mature();
                r.f.mc.gameSettings.touchscreen = true; r.screen.pressSlot(12);
                assertNull("Touchscreen mousedown has not picked up the cursor stack", r.f.player.inventory.getItemStack());
                r.clock.addAndGet(10_000_000_000L);
                assertTrue(r.screen.inventoryAssistUserBusy()); assertFalse(r.ready()); assertFalse(r.execute());
                assertEquals(0, r.attempts.get()); assertNull(r.click()); assertNotNull(r.f.player.inventory.mainInventory[12]);
            }
        }
        @Test public void changedStacksResizeAndChangedScreenRequireAFreshProposal() throws Exception {
            try (Run r = new Run()) {
                r.mature(); r.f.player.inventory.mainInventory[12] = new ItemStack(Items.iron_sword);
                assertFalse(r.execute()); assertFalse(r.ready()); r.present(); r.clock.addAndGet(100_000_000L); assertTrue(r.ready());
                r.screen.initGui(); assertFalse(r.execute()); r.mature();
                r.f.mc.currentScreen = new Screen(r.f, r.clock); assertFalse(r.execute()); assertFalse(r.ready());
                assertEquals(0, r.attempts.get()); assertNull(r.click());
            }
        }
        @Test public void acknowledgementIsExactAndDisableCannotDiscardAnOutstandingOperation() throws Exception {
            try (Run r = new Run()) {
                r.mature(); assertTrue(r.execute()); C0EPacketClickWindow click = r.click();
                r.session.suspend(); assertNull(r.screen.inventoryAssistPreview()); r.session.resume();
                assertTrue(r.session.busy(r.f.mc)); assertFalse(r.execute());
                r.f.channel.writeInbound(new S32PacketConfirmTransaction(0, (short)(click.getActionNumber() + 1), true));
                assertTrue(r.session.busy(r.f.mc));
                r.session.suspend(); r.f.channel.writeInbound(new S32PacketConfirmTransaction(0, click.getActionNumber(), true));
                assertFalse(r.session.busy(r.f.mc)); assertNull(r.screen.inventoryAssistPreview()); assertNull(r.click());
                assertEquals(1, r.attempts.get());
            }
        }
        @Test public void rejectionAndTimeoutWaitForAppliedResynchronization() throws Exception {
            try (Run r = new Run()) {
                r.mature(); r.execute(); C0EPacketClickWindow click = r.click();
                r.f.channel.writeInbound(new S32PacketConfirmTransaction(0, click.getActionNumber(), false)); r.click();
                assertTrue(r.session.busy(r.f.mc)); assertTrue(r.screen.inventoryAssistPreview().warning);
                r.session.cancelPreview(); assertFalse(r.execute()); r.sync(); assertFalse(r.session.busy(r.f.mc));
                r.f.player.inventory.mainInventory[12] = new ItemStack(Items.iron_sword); r.mature(); r.execute(); click = r.click();
                r.clock.addAndGet(6_000_000_000L);
                // No update/tick observes this timeout before the ACK is applied.
                r.f.channel.writeInbound(new S32PacketConfirmTransaction(0, click.getActionNumber(), true));
                assertTrue(r.session.busy(r.f.mc)); assertFalse(r.execute()); r.sync(); assertFalse(r.session.busy(r.f.mc));
                assertEquals(2, r.attempts.get());
            }
        }
        @Test public void lateInputAndLifecycleCallbacksCannotTransportAPrediction() throws Exception {
            try (Run r = new Run()) {
                Watch watch = new Watch(); Pulsar.BUS.subscribe(watch);
                try {
                    r.mature(); watch.action = e -> { if (e.isPre() && e.getPacket() instanceof C0EPacketClickWindow)
                        try { r.screen.key(Keyboard.KEY_LSHIFT); } catch (Exception failure) { throw new RuntimeException(failure); } };
                    assertTrue(r.execute()); assertNull(r.click()); assertTrue(r.session.busy(r.f.mc));
                    assertFalse(r.execute()); assertEquals(1, r.attempts.get()); r.sync();
                    r.f.player.inventory.mainInventory[12] = new ItemStack(Items.iron_sword); r.clock.addAndGet(300_000_000L); r.mature();
                    watch.action = e -> { if (e.isPre() && e.getPacket() instanceof C0EPacketClickWindow)
                        Pulsar.BUS.post(new WorldEvent.Unload(r.f.world)); };
                    assertTrue(r.execute()); assertNull(r.click()); assertFalse(r.session.busy(r.f.mc));
                    assertEquals(2, r.attempts.get());
                } finally { Pulsar.BUS.unsubscribe(watch); }
            }
        }
        @Test public void closingTheSceneReleasesThePassiveObserverWithoutAnotherAction() throws Exception {
            try (Run r = new Run()) {
                r.mature(); r.execute(); assertNotNull(r.click()); r.session.suspend();
                r.f.mc.currentScreen = null; Pulsar.BUS.post(new TickEvent());
                assertFalse(r.session.busy(r.f.mc)); assertFalse(r.execute()); assertNull(r.click());
                assertNull(r.screen.inventoryAssistPreview()); assertEquals(1, r.attempts.get());
            }
        }
        @Test public void lateSlotCallbacksCannotHideInputUnloadOrAuthoritativeChanges() throws Exception {
            try (Run r = new Run()) {
                Watch watch = new Watch(); Pulsar.BUS.subscribe(watch);
                try {
                    for (int scenario = 0; scenario < 4; scenario++) {
                        r.session.clear(); r.f.player.inventory.mainInventory[12] = new ItemStack(Items.diamond_sword);
                        r.f.player.inventory.mainInventory[0] = null; r.f.player.inventory.mainInventory[9] = null;
                        r.clock.addAndGet(1_000_000_000L);
                        final int kind = scenario; AtomicBoolean armed = new AtomicBoolean(), fired = new AtomicBoolean();
                        Runnable callback = () -> {
                            if (!armed.compareAndSet(true, false)) return;
                            fired.set(true);
                            try {
                                if (kind == 0 || kind == 3) r.screen.key(Keyboard.KEY_LSHIFT);
                                else if (kind == 1) Pulsar.BUS.post(new WorldEvent.Unload(r.f.world));
                                else r.f.channel.writeInbound(new S2FPacketSetSlot(0, 9, new ItemStack(Items.apple)));
                            } catch (Exception failure) { throw new RuntimeException(failure); }
                        };
                        Slot old20 = r.screen.inventorySlots.getSlot(20), old12 = r.screen.inventorySlots.getSlot(12);
                        Slot lateRead = new Slot(r.f.player.inventory, 20, old20.xDisplayPosition, old20.yDisplayPosition) {
                            @Override public ItemStack getStack() { if (kind != 3) callback.run(); return super.getStack(); }
                        }; lateRead.slotNumber = 20; r.screen.inventorySlots.inventorySlots.set(20, lateRead);
                        Slot lateHover = new Slot(r.f.player.inventory, 12, old12.xDisplayPosition, old12.yDisplayPosition) {
                            @Override public boolean canBeHovered() { if (kind == 3) callback.run(); return super.canBeHovered(); }
                        }; lateHover.slotNumber = 12; r.screen.inventorySlots.inventorySlots.set(12, lateHover);
                        r.mature();
                        watch.action = event -> { if (event.isPre() && event.getPacket() instanceof C0EPacketClickWindow) armed.set(true); };
                        assertTrue(r.execute()); assertTrue("Late callback did not run: " + kind, fired.get());
                        assertNull("A stale final guard transported a click: " + kind, r.click());
                        if (kind == 2) assertEquals(Items.apple, r.f.player.inventory.mainInventory[9].getItem());
                        if (kind == 1) assertSame("Unload callback did not change the raw world pointer", r.f.world, r.f.mc.theWorld);
                        assertFalse(r.execute());
                        watch.action = event -> { }; r.screen.inventorySlots.inventorySlots.set(20, old20);
                        r.screen.inventorySlots.inventorySlots.set(12, old12);
                    }
                    assertEquals(4, r.attempts.get());
                } finally { Pulsar.BUS.unsubscribe(watch); }
            }
        }
    }
}
