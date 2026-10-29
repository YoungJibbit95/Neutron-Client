package me.youngjibbit.module.impl.player;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.event.packet.TickEvent;
import me.youngjibbit.event.update.EventUpdate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import org.junit.*;

import java.lang.reflect.Field;
import java.util.List;
import static org.junit.Assert.*;

/** Permanent I01e regression, derived from the external I01d diagnostic. */
public class AutoHealTickEventGateProbeTest {
    private RotationBehaviorHookTest.Fixture f;
    private AutoHeal heal;
    private RuntimeObservation observer;

    private static Field field(Class<?> type, String name) throws Exception {
        Field value = type.getDeclaredField(name); value.setAccessible(true); return value;
    }
    @Before public void prepare() throws Exception {
        Bootstrap.register();
        f = new RotationBehaviorHookTest.Fixture();
        AutoHealInventoryTransferTest.prepare(f);
        observer = f.runtime.getObservation(); observer.reset();
        f.player.inventory.currentItem = 3;
        f.player.inventory.mainInventory[8] = new ItemStack(Items.mushroom_stew);
        heal = new AutoHeal(); heal.onEnable(); drain();
    }
    @After public void close() throws Exception {
        heal.onDisable(); AutoHealInventoryTransferTest.transfer(heal).clear(); f.close();
    }
    private List<Object> drain() { return AutoHealSafetyTest.drain(f); }
    private void tick() { Pulsar.BUS.post(new TickEvent()); }
    private long uses(List<Object> packets) { return packets.stream().filter(p -> p instanceof C08PacketPlayerBlockPlacement).count(); }
    private void denied() throws Exception {
        float speed = f.mc.timer.timerSpeed;
        tick(); assertTrue("No synthetic packets may start in this context", drain().isEmpty());
        assertEquals(speed, f.mc.timer.timerSpeed, 0);
        assertEquals(0, AutoHealSafetyTest.deadline(heal)); assertEquals(3, f.player.inventory.currentItem);
    }
    private C0EPacketClickWindow startTransfer() throws Exception {
        f.player.inventory.mainInventory[8] = null;
        f.player.inventory.mainInventory[12] = new ItemStack(Items.mushroom_stew);
        heal.getBooleanSetting("Inventory Transfers").setValue(true);
        tick(); C0EPacketClickWindow click = AutoHealInventoryTransferTest.click(drain());
        assertNotNull(click); assertTrue(AutoHealInventoryTransferTest.observing(heal)); return click;
    }
    @Test public void activeTickWithoutCurrentStepUsesOnceAndRestoresSlot() throws Exception {
        observer.beginOuterTick(); assertNull(observer.getCurrentStep());
        assertTrue(observer.captureScene().isValid()); assertFalse(observer.captureScene().isSuspended());
        tick(); assertEquals(1, uses(drain())); assertEquals(3, f.player.inventory.currentItem);
        assertTrue(AutoHealSafetyTest.deadline(heal) > System.nanoTime()); tick(); assertTrue(drain().isEmpty());
    }
    @Test public void laterWithheldDoesNotRevokeTheTickEventAttempt() throws Exception {
        observer.beginOuterTick(); tick(); assertEquals(1, uses(drain()));
        long deadline = AutoHealSafetyTest.deadline(heal);
        observer.observePlayerStep(f.player, () -> { fail("withheld body"); return true; }, callback -> {});
        assertNotNull(observer.getLastWithheld()); assertNull(observer.getCurrentStep());
        assertEquals(deadline, AutoHealSafetyTest.deadline(heal)); assertTrue(drain().isEmpty());
    }
    @Test public void ordinaryAndExtraEventUpdatesDoNotReplayTickEventEvenWhenCooldownReady() throws Exception {
        observer.beginOuterTick(); tick(); assertEquals(1, uses(drain())); AutoHealSafetyTest.set(heal, "nextUseNanos", 0L);
        int[] calls = {0}; observer.observePlayerStep(f.player, () -> {
            assertEquals(calls[0]++ == 0 ? ExecutedStep.Kind.ORDINARY : ExecutedStep.Kind.EARNED_EXTRA,
                    observer.getCurrentStep().getKind());
            Pulsar.BUS.post(new EventUpdate());
            me.youngjibbit.event.motion.EventMotion motion = new me.youngjibbit.event.motion.EventMotion(0, 0, 0, 0, 0, true);
            Pulsar.BUS.post(motion); motion.setEventPhase(me.zero.alpine.event.EventPhase.POST);
            Pulsar.BUS.post(motion); return true;
        }, callback -> { callback.getAsBoolean(); callback.getAsBoolean(); });
        assertEquals(2, calls[0]); assertTrue(drain().isEmpty()); assertEquals(0, AutoHealSafetyTest.deadline(heal));
    }
    @Test public void requirementNoObservedOuterTickMustDenyItemUse() throws Exception {
        assertEquals(-1, observer.getOuterTickId()); assertFalse(observer.captureScene().isValid()); denied();
    }
    @Test public void requirementEndedOuterTickMustDenyItemUseDespitePositiveIdAndActiveScene() throws Exception {
        observer.beginOuterTick(); observer.endOuterTick(); observer.beginOuterTick(); observer.endOuterTick();
        assertTrue(observer.getOuterTickId() > 0); assertTrue(observer.captureScene().isValid());
        assertFalse(observer.captureScene().isSuspended()); denied();
    }
    @Test public void requirementMissingCanonicalObserverMustDenyItemUse() throws Exception {
        field(Pulsar.class, "runtime").set(Pulsar.INSTANCE, null);
        try { assertNull(RuntimeServices.observation()); denied(); }
        finally { field(Pulsar.class, "runtime").set(Pulsar.INSTANCE, f.runtime); }
    }
    @Test public void requirementPauseWithNoGuiAndFocusPresentMustDenyItemUse() throws Exception {
        observer.beginOuterTick(); field(Minecraft.class, "isGamePaused").setBoolean(f.mc, true);
        assertTrue(observer.captureScene().isSuspended()); denied();
    }
    @Test public void guiDenialPreservesCooldownSlotAndForeignRotationOwner() throws Exception {
        observer.beginOuterTick(); f.accept("scaffold", 30); f.mc.currentScreen = new GuiChat(); denied();
        assertTrue(f.runtime.getRotationManager().isOwner("scaffold"));
        assertFalse(f.runtime.getAttackScheduler().hasAttackThisTick());
    }
    @Test public void focusLossDeniesWithoutConsumingCooldown() throws Exception {
        observer.beginOuterTick(); f.mc.inGameHasFocus = false; denied();
    }
    @Test public void deadPlayerDeniesWithoutConsumingCooldown() throws Exception {
        observer.beginOuterTick(); f.player.isDead = true; denied();
    }
    @Test public void cursorStackDeniesWithoutConsumingCooldown() throws Exception {
        observer.beginOuterTick(); f.player.inventory.setItemStack(new ItemStack(Items.bread)); denied();
    }
    @Test public void usingItemDeniesWithoutConsumingCooldown() throws Exception {
        observer.beginOuterTick(); f.player.usingItem = true; denied();
    }
    @Test public void requirementInvalidPlayerWorldIdentityMustDenyItemUse() throws Exception {
        observer.beginOuterTick(); f.player.worldObj = null; assertFalse(observer.captureScene().isValid()); denied();
    }
    @Test public void requirementShutdownMustDenyNewItemUse() throws Exception {
        observer.beginOuterTick(); f.mc.shutdown(); assertFalse(observer.captureScene().isValid()); denied();
    }
    @Test public void requirementNoObservedOuterTickMustDenyTransferStart() throws Exception {
        f.player.inventory.mainInventory[8] = null; ItemStack source = new ItemStack(Items.mushroom_stew);
        f.player.inventory.mainInventory[12] = source; heal.getBooleanSetting("Inventory Transfers").setValue(true);
        tick(); assertTrue("No new C0E outside a real outer tick", drain().isEmpty());
        assertSame(source, f.player.inventory.mainInventory[12]); assertFalse(AutoHealInventoryTransferTest.observing(heal));
    }
    @Test public void activeApiDistinguishesLifetimeWhileHistoricalApisRemainUnchanged() {
        observer.beginOuterTick(); SceneContext active = observer.captureScene(); long tick = observer.getOuterTickId();
        assertEquals(tick, observer.getActiveOuterTickId());
        assertNull(observer.getCurrentStep()); assertTrue(observer.getLastSteps().isEmpty()); assertNull(observer.getLastWithheld());
        observer.endOuterTick(); SceneContext ended = observer.captureScene();
        assertEquals(-1, observer.getActiveOuterTickId());
        assertEquals(tick, observer.getOuterTickId()); assertTrue(active.sameSceneAs(ended));
        assertEquals(active.getAvailability(), ended.getAvailability()); assertTrue(observer.isCurrent(active));
        assertNull(observer.getCurrentStep()); assertTrue(observer.getLastSteps().isEmpty()); assertNull(observer.getLastWithheld());
    }
    @Test public void repeatedSamplingReservesNothingAndEpochChangeInvalidatesHistoricalScene() {
        observer.beginOuterTick(); SceneContext old = observer.captureScene();
        for (int i = 0; i < 100; i++) assertTrue(observer.isCurrent(old));
        f.network.getClientPackets().worldChanged(); assertFalse(observer.isCurrent(old));
        assertTrue(observer.captureScene().isValid());
    }
    @Test public void transferAckCanSettleAfterOuterTickEndsWithoutCurrentStep() throws Exception {
        observer.beginOuterTick(); C0EPacketClickWindow click = startTransfer(); observer.endOuterTick();
        f.channel.writeInbound(new S32PacketConfirmTransaction(0, click.getActionNumber(), true));
        assertFalse(AutoHealInventoryTransferTest.observing(heal)); assertTrue(drain().isEmpty());
    }
    @Test public void transferRejectAfterTickEndsStillRequiresAppliedResync() throws Exception {
        observer.beginOuterTick(); C0EPacketClickWindow click = startTransfer(); observer.endOuterTick();
        f.channel.writeInbound(new S32PacketConfirmTransaction(0, click.getActionNumber(), false)); drain();
        assertTrue(AutoHealInventoryTransferTest.observing(heal));
        AutoHealInventoryTransferTest.resync(f); assertFalse(AutoHealInventoryTransferTest.observing(heal));
    }
    @Test public void transferTimeoutCleanupWithoutCurrentStepDoesNotAuthorizeUse() throws Exception {
        observer.beginOuterTick(); startTransfer(); observer.endOuterTick();
        AutoHealInventoryTransfer transfer = AutoHealInventoryTransferTest.transfer(heal);
        Object pending = field(AutoHealInventoryTransfer.class, "pending").get(transfer);
        field(pending.getClass(), "deadline").setLong(pending, System.nanoTime()-1);
        tick(); assertTrue(transfer.blocked()); assertTrue(transfer.status().contains("resync")); assertTrue(drain().isEmpty());
    }
    @Test public void manualSlotChangeDuringUseIsNeverRestoredOver() throws Exception {
        observer.beginOuterTick(); AutoHealSafetyTest.Watch watch = new AutoHealSafetyTest.Watch();
        watch.action = event -> { if (event.isPre() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) f.player.inventory.currentItem = 2; };
        Pulsar.BUS.subscribe(watch);
        try { tick(); assertEquals(0, uses(drain())); assertEquals(2, f.player.inventory.currentItem); }
        finally { Pulsar.BUS.unsubscribe(watch); }
    }
    @Test public void stackMutationBeforeUseCannotPassExistingPacketGuard() throws Exception {
        observer.beginOuterTick(); AutoHealSafetyTest.Watch watch = new AutoHealSafetyTest.Watch();
        watch.action = event -> { if (event.isPre() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) f.player.inventory.mainInventory[8].stackSize++; };
        Pulsar.BUS.subscribe(watch);
        try { tick(); assertEquals(0, uses(drain())); assertEquals(0, AutoHealSafetyTest.deadline(heal)); assertEquals(3, f.player.inventory.currentItem); }
        finally { Pulsar.BUS.unsubscribe(watch); }
    }
    @Test public void usingReentrancyDoesNotStartASecondHeal() throws Exception {
        observer.beginOuterTick(); AutoHealSafetyTest.Watch watch = new AutoHealSafetyTest.Watch();
        watch.action = event -> { if (event.isPre() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) tick(); };
        Pulsar.BUS.subscribe(watch);
        try { tick(); assertEquals(1, uses(drain())); }
        finally { Pulsar.BUS.unsubscribe(watch); }
    }

    @Test public void resetDeniesUntilAFreshOuterEntry() throws Exception {
        observer.beginOuterTick(); observer.reset(); denied();
        observer.beginOuterTick(); tick(); assertEquals(1, uses(drain()));
    }
    @Test public void missingConnectionAndClosedPipelineDenyStarts() throws Exception {
        observer.beginOuterTick();
        net.minecraft.client.network.NetHandlerPlayClient connection = f.player.sendQueue;
        field(net.minecraft.client.entity.EntityPlayerSP.class, "sendQueue").set(f.player, null);
        denied();
        field(net.minecraft.client.entity.EntityPlayerSP.class, "sendQueue").set(f.player, connection);
        f.network.getClientPackets().close(); denied();
    }
    @Test public void suspendedResumeRequiresANewCheckAndDoesNotCacheDenialOrPermission() throws Exception {
        observer.beginOuterTick(); f.mc.inGameHasFocus = false; denied();
        f.mc.inGameHasFocus = true; tick(); assertEquals(1, uses(drain()));
        AutoHealSafetyTest.set(heal, "nextUseNanos", 0L);
        f.mc.inGameHasFocus = false; denied();
    }
    @Test public void syntheticTickInsideEitherBodyOrAfterWithheldCannotStart() throws Exception {
        observer.beginOuterTick();
        observer.observePlayerStep(f.player, () -> {
            try { denied(); } catch (Exception error) { throw new AssertionError(error); }
            return true;
        }, callback -> { callback.getAsBoolean(); callback.getAsBoolean(); });
        denied(); // Completed bodies are history, not the pre-body TickEvent phase.
        observer.beginOuterTick();
        observer.observePlayerStep(f.player, () -> { fail("withheld body"); return true; }, callback -> {});
        denied();
    }
    @SuppressWarnings("unchecked")
    private void duringSampling(int read, Runnable action) throws Exception {
        Field source = field(RuntimeObservation.class, "source");
        java.util.function.Supplier<RuntimeObservation.SceneSample> original =
                (java.util.function.Supplier<RuntimeObservation.SceneSample>) source.get(observer);
        int[] reads = {0};
        source.set(observer, (java.util.function.Supplier<RuntimeObservation.SceneSample>) () -> {
            if (++reads[0] == read) action.run();
            return original.get(); // Still the real MinecraftSceneSource, with a deterministic boundary.
        });
    }
    @Test public void reentrantEndDuringSecondSceneSampleCannotLeaveALateStartRight() throws Exception {
        observer.beginOuterTick(); duringSampling(2, observer::endOuterTick); denied();
        assertEquals(-1, observer.getActiveOuterTickId());
    }
    @Test public void reentrantNewTickDuringSceneSamplingCannotReuseTheOldTick() throws Exception {
        observer.beginOuterTick(); duringSampling(2, () -> { observer.endOuterTick(); observer.beginOuterTick(); });
        denied(); assertEquals(1, observer.getActiveOuterTickId());
    }
    @Test public void reentrantEpochChangeDeniesThenFreshCheckCanResume() throws Exception {
        observer.beginOuterTick(); duringSampling(2, () -> f.network.getClientPackets().worldChanged()); denied();
        tick(); assertEquals(1, uses(drain()));
    }
    @Test public void reentrantExplicitGenerationBoundaryDeniesThenFreshCheckCanResume() throws Exception {
        observer.beginOuterTick(); duringSampling(2, observer::invalidateScene); denied();
        tick(); assertEquals(1, uses(drain()));
    }
    @Test public void reentrantPipelineReplacementDeniesThenFreshCheckCanResume() throws Exception {
        observer.beginOuterTick(); duringSampling(2, () ->
                f.network.configureClientPackets(new me.youngjibbit.core.network.PacketFlowTest.MainThread()));
        denied(); tick(); assertEquals(1, uses(drain()));
    }
    @Test public void reentrantCanonicalObserverRemovalDenies() throws Exception {
        observer.beginOuterTick(); duringSampling(2, () -> {
            try { field(Pulsar.class, "runtime").set(Pulsar.INSTANCE, null); }
            catch (Exception error) { throw new AssertionError(error); }
        });
        try { denied(); } finally { field(Pulsar.class, "runtime").set(Pulsar.INSTANCE, f.runtime); }
    }
    @Test public void transferSamplingEndDeniesWithoutPredictionPendingOrCooldown() throws Exception {
        observer.beginOuterTick(); duringSampling(2, observer::endOuterTick);
        f.player.inventory.mainInventory[8] = null;
        ItemStack source = new ItemStack(Items.mushroom_stew); f.player.inventory.mainInventory[12] = source;
        heal.getBooleanSetting("Inventory Transfers").setValue(true); denied();
        assertSame(source, f.player.inventory.mainInventory[12]); assertNull(f.player.inventory.mainInventory[0]);
        assertFalse(AutoHealInventoryTransferTest.observing(heal));
    }
    @Test public void alreadyStartedUseMayCompleteItsPacketGuardAfterEndTick() throws Exception {
        observer.beginOuterTick(); AutoHealSafetyTest.Watch watch = new AutoHealSafetyTest.Watch();
        watch.action = event -> { if (event.isPre() && event.getPacket() instanceof C08PacketPlayerBlockPlacement) observer.endOuterTick(); };
        Pulsar.BUS.subscribe(watch);
        try { tick(); assertEquals(1, uses(drain())); assertEquals(-1, observer.getActiveOuterTickId());
            assertEquals(3, f.player.inventory.currentItem); assertTrue(AutoHealSafetyTest.deadline(heal) > System.nanoTime()); }
        finally { Pulsar.BUS.unsubscribe(watch); }
    }
    @Test public void transferAckOrRejectSettlesAfterTickEndAndDisable() throws Exception {
        for (boolean reject : new boolean[] {false, true}) {
            java.util.Arrays.fill(f.player.inventory.mainInventory, null);
            observer.beginOuterTick(); C0EPacketClickWindow click = startTransfer(); observer.endOuterTick();
            heal.onDisable(); assertTrue(AutoHealInventoryTransferTest.observing(heal));
            f.channel.writeInbound(new S32PacketConfirmTransaction(0, click.getActionNumber(), !reject)); drain();
            if (reject) { assertTrue(AutoHealInventoryTransferTest.observing(heal)); AutoHealInventoryTransferTest.resync(f); }
            assertFalse(AutoHealInventoryTransferTest.observing(heal)); assertTrue(drain().isEmpty());
            heal.onEnable();
        }
    }
    @Test public void manualRightClickAndWindowClickWorkWithoutObserverTickOrStep() throws Exception {
        assertEquals(-1, observer.getActiveOuterTickId()); assertNull(observer.getCurrentStep());
        f.player.inventory.mainInventory[3] = new ItemStack(Items.stick);
        f.mc.objectMouseOver = new net.minecraft.util.MovingObjectPosition(
                net.minecraft.util.MovingObjectPosition.MovingObjectType.MISS, new net.minecraft.util.Vec3(0, 0, 0),
                net.minecraft.util.EnumFacing.UP, new net.minecraft.util.BlockPos(0, 0, 0));
        java.lang.reflect.Method rightClick = Minecraft.class.getDeclaredMethod("rightClickMouse");
        rightClick.setAccessible(true); rightClick.invoke(f.mc);
        assertEquals(1, uses(drain()));
        f.mc.playerController.windowClick(0, 12, 0, 0, f.player);
        assertNotNull(AutoHealInventoryTransferTest.click(drain()));
        assertEquals(0, AutoHealSafetyTest.deadline(heal));
    }

    @Test public void postTickEventCannotStartAnAction() throws Exception {
        observer.beginOuterTick(); TickEvent event = new TickEvent();
        event.setEventPhase(me.zero.alpine.event.EventPhase.POST); Pulsar.BUS.post(event);
        assertTrue(drain().isEmpty()); assertEquals(0, AutoHealSafetyTest.deadline(heal));
        assertEquals(3, f.player.inventory.currentItem);
    }
    @Test public void reentrantWorldReplacementDeniesThenFreshCheckCanResume() throws Exception {
        observer.beginOuterTick(); duringSampling(2, () -> {
            try { f.mc.theWorld = RotationBehaviorHookTest.bare(net.minecraft.client.multiplayer.WorldClient.class); }
            catch (Exception error) { throw new AssertionError(error); }
            f.player.worldObj = f.mc.theWorld;
        });
        denied(); tick(); assertEquals(1, uses(drain()));
    }
    @Test public void transferTimeoutWhileDisabledAfterEndWaitsForAppliedResync() throws Exception {
        observer.beginOuterTick(); C0EPacketClickWindow click = startTransfer(); observer.endOuterTick(); heal.onDisable();
        AutoHealInventoryTransfer transfer = AutoHealInventoryTransferTest.transfer(heal);
        Object pending = field(AutoHealInventoryTransfer.class, "pending").get(transfer);
        field(pending.getClass(), "deadline").setLong(pending, System.nanoTime() - 1);
        tick(); assertTrue(transfer.blocked()); assertTrue(transfer.status().contains("resync"));
        f.channel.writeInbound(new S32PacketConfirmTransaction(0, click.getActionNumber(), true)); drain();
        assertTrue(AutoHealInventoryTransferTest.observing(heal)); AutoHealInventoryTransferTest.resync(f);
        assertFalse(AutoHealInventoryTransferTest.observing(heal)); assertTrue(drain().isEmpty());
    }
}
