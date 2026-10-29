package me.youngjibbit.core.observation;

import me.youngjibbit.Pulsar;
import me.youngjibbit.core.bootstrap.*;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.contract.*;
import me.youngjibbit.core.network.*;
import me.youngjibbit.event.motion.EventMotion;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.event.update.EventUpdate;
import me.youngjibbit.module.impl.exploit.Tickbase;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import net.minecraft.util.Timer;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.*;
import static org.junit.Assert.*;

public class RuntimeObservationHookTest {
    private static Field field(Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    public static class LoadedWorld extends AttackHookTest.WorldStub {
        Entity target;
        public LoadedWorld() { super(); }
        @Override public boolean isBlockLoaded(BlockPos pos) { return true; }
        @Override public Entity getEntityByID(int id) { return target != null && target.getEntityId() == id ? target : null; }
    }
    private static LoadedWorld prepare(RotationBehaviorHookTest.Fixture f) throws Exception {
        LoadedWorld world = RotationBehaviorHookTest.bare(LoadedWorld.class);
        f.mc.theWorld = world; f.player.worldObj = world; f.player.onGround = true;
        f.player.setEntityBoundingBox(new AxisAlignedBB(-.3, 0, -.3, .3, 1.8, .3));
        field(EntityPlayerSP.class, "mc").set(f.player, f.mc);
        f.player.sendQueue.clientWorldController = world;
        f.mc.timer = new Timer(20, () -> 0); return world;
    }
    public static final class Events implements Subscriber {
        final List<String> order = new ArrayList<>(); RuntimeObservation observer; boolean cancelUpdate;
        @Subscribe final Listener<EventUpdate> update = new Listener<>(event -> {
            order.add("UPDATE"); if (observer != null) assertNotNull(observer.getCurrentStep());
            if (cancelUpdate) event.cancel();
        });
        @Subscribe final Listener<EventMotion> motion = new Listener<>(event -> {
            order.add(event.isPre() ? "PRE" : "POST");
            if (observer != null) assertNotNull(observer.getCurrentStep());
        });
        @Subscribe final Listener<EventPacket> packets = new Listener<>(event -> {
            if (event.isOutbound() && event.isPre()) {
                if (event.getPacket() instanceof C0BPacketEntityAction) order.add("C0B");
                if (event.getPacket() instanceof C03PacketPlayer) order.add("C03");
            }
        });
    }
    @Test public void actualCentralHookObservesOneBodyWithoutAnActiveTickbase() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); assertSame(o, RuntimeServices.observation());
            o.beginOuterTick(); int[] calls = {0};
            Tickbase.runPlayerStep(f.player, () -> { calls[0]++; assertNotNull(o.getCurrentStep()); return true; });
            assertEquals(1, calls[0]); assertEquals(1, o.getLastSteps().size()); assertNull(o.getCurrentStep());
        }
    }
    @Test public void actualPlayerOnUpdateCancellationIsEnteredInterruptedAndNotWithheld() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); o.beginOuterTick();
            Events events = new Events(); events.observer = o; events.cancelUpdate = true; Pulsar.BUS.subscribe(events);
            try { f.player.onUpdate(); assertEquals(Collections.singletonList("UPDATE"), events.order);
                assertEquals(1, o.getLastSteps().size()); assertTrue(o.getLastSteps().get(0).isInterrupted());
                assertNull(o.getLastWithheld()); assertNull(o.getCurrentStep()); assertNull(f.channel.readOutbound());
            } finally { Pulsar.BUS.unsubscribe(events); }
        }
    }
    @Test public void actualCentralHookPropagatesABodyExceptionAndClearsCurrentStep() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); o.beginOuterTick();
            IllegalStateException failure = new IllegalStateException("synthetic body");
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> Tickbase.runPlayerStep(f.player, () -> { throw failure; })));
            assertNull(o.getCurrentStep()); assertNull(o.getLastWithheld());
            assertEquals(1, o.getLastSteps().size()); assertTrue(o.getLastSteps().get(0).isInterrupted());
        }
    }
    @Test public void actualTickbaseExceptionBeforeTheDecisionNeverInventsWithheld() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); o.beginOuterTick();
            boolean[] failClock = {false}; IllegalStateException failure = new IllegalStateException("synthetic clock");
            Tickbase module = new Tickbase(() -> { if (failClock[0]) throw failure; return 0; }, f.mc);
            module.getBooleanSetting("Require Target").setValue(false); module.setToggled(true);
            try { failClock[0] = true;
                assertSame(failure, assertThrows(IllegalStateException.class,
                        () -> Tickbase.runPlayerStep(f.player, () -> { fail("unexpected body"); return true; })));
                assertTrue(o.getLastSteps().isEmpty()); assertNull(o.getLastWithheld()); assertNull(o.getCurrentStep());
            } finally { failClock[0] = false; module.setToggled(false); }
        }
    }
    @Test public void actualTickbaseWithholdsThenReleasesOneEarnedBodyWithTheSameTickId() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            LoadedWorld world = prepare(f); RuntimeObservation o = f.runtime.getObservation();
            RotationBehaviorHookTest.Player enemy = RotationBehaviorHookTest.bare(RotationBehaviorHookTest.Player.class);
            enemy.worldObj = world; enemy.setEntityId(9); enemy.serverPosX = 64; world.target = enemy;
            Tickbase module = new Tickbase(() -> 0, f.mc);
            module.getBooleanSetting("Require Target").setValue(false);
            module.getNumberSetting("Min Charge").setValue(1.0);
            module.getNumberSetting("Release Speed").setValue(2.0); module.setToggled(true);
            try {
                o.beginOuterTick(); Tickbase.beginFrame(f.mc, false, 1, 50);
                Tickbase.runPlayerStep(f.player, () -> { fail("withheld body entered"); return true; });
                assertNotNull(o.getLastWithheld()); assertTrue(o.getLastSteps().isEmpty());
                TickbaseBudget budget = (TickbaseBudget) field(Tickbase.class, "budget").get(module);
                assertEquals(1, budget.getCredit()); assertTrue(budget.release());
                field(Tickbase.class, "releaseTarget").set(module, enemy);
                List<ExecutedStep> entered = new ArrayList<>();
                Tickbase.runPlayerStep(f.player, () -> { entered.add(o.getCurrentStep()); return true; });
                assertEquals(2, entered.size()); assertEquals(0, entered.get(0).getStepIndex());
                assertEquals(ExecutedStep.Kind.EARNED_EXTRA, entered.get(1).getKind());
                assertEquals(1, entered.get(1).getStepIndex()); assertEquals(0, entered.get(1).getOuterTickId());
                assertEquals(0, o.getOuterTickId()); assertEquals(0, budget.getCredit());
                assertNull(o.getCurrentStep()); assertNull(o.getLastWithheld());
            } finally { module.setToggled(false); }
        }
    }
    @Test public void realWalkingEventsAndPacketsHaveTheSameOrderWithAndWithoutObservation() throws Exception {
        List<String> baseline = walkingTrace(false), observed = walkingTrace(true);
        assertEquals(Arrays.asList("UPDATE", "PRE", "C0B", "C03", "POST"), baseline);
        assertEquals(baseline, observed);
    }
    private List<String> walkingTrace(boolean enabled) throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); Events events = new Events();
            o.beginOuterTick(); if (enabled) events.observer = o;
            else field(Pulsar.class, "runtime").set(Pulsar.INSTANCE, null);
            f.player.sprint = true; Pulsar.BUS.subscribe(events);
            try {
                // Actual walking hook, synthetic update boundary; full physics needs a live world.
                Tickbase.runPlayerStep(f.player, () -> { Pulsar.BUS.post(new EventUpdate());
                    f.player.onUpdateWalkingPlayer(); return true; });
                assertTrue(f.channel.readOutbound() instanceof C0BPacketEntityAction);
                assertTrue(f.channel.readOutbound() instanceof C03PacketPlayer); assertNull(f.channel.readOutbound());
                assertNull(o.getCurrentStep()); return new ArrayList<>(events.order);
            } finally { Pulsar.BUS.unsubscribe(events); RuntimeServices.rotation().reset();
                field(Pulsar.class, "runtime").set(Pulsar.INSTANCE, f.runtime); }
        }
    }
    @Test public void guiPauseAndFocusAreFreshSuspensionObservationsWithoutSceneChanges() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); o.beginOuterTick(); SceneContext active = o.captureScene();
            for (int mode = 0; mode < 3; mode++) {
                if (mode == 0) f.mc.currentScreen = new GuiChat();
                if (mode == 1) f.mc.inGameHasFocus = false;
                if (mode == 2) field(Minecraft.class, "isGamePaused").setBoolean(f.mc, true);
                SceneContext suspended = o.captureScene(); assertTrue(suspended.isSuspended());
                assertTrue(suspended.isValid()); assertTrue(active.sameSceneAs(suspended));
                Tickbase.runPlayerStep(f.player, () -> {
                    assertFalse(AutomationEligibility.mayAttempt(AutomationEligibility.ModuleAction.PLACEMENT,
                            o.getCurrentStep(), o.captureScene(), o.getOuterTickId())); return true;
                });
                f.mc.currentScreen = null; f.mc.inGameHasFocus = true;
                field(Minecraft.class, "isGamePaused").setBoolean(f.mc, false);
                assertFalse(o.captureScene().isSuspended()); assertTrue(o.isCurrent(active));
            }
        }
    }
    @Test public void realPacketEpochAndConnectionCloseRejectHistoricalScenes() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); o.beginOuterTick(); SceneContext old = o.captureScene();
            f.network.getClientPackets().worldChanged(); assertFalse(o.isCurrent(old));
            SceneContext next = o.captureScene(); assertTrue(next.getPacketContextEpoch() > old.getPacketContextEpoch());
            f.network.closeChannel(new ChatComponentText("synthetic")); assertFalse(o.isCurrent(next));
            assertFalse(o.captureScene().isValid());
        }
    }
    @Test public void actualWorldUnloadClearsAnEnteredStepAndInvalidatesThePreviousScene() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); o.beginOuterTick(); SceneContext old = o.captureScene();
            Tickbase.runPlayerStep(f.player, () -> { f.mc.loadWorld(null); assertNull(o.getCurrentStep()); return true; });
            assertNull(o.getCurrentStep()); assertTrue(o.getLastSteps().get(0).isInterrupted());
            assertEquals(0, o.getActiveOuterTickId()); // World invalidation does not end runTick.
            assertFalse(o.isCurrent(old)); assertFalse(o.captureScene().isValid());
            assertNull(f.mc.theWorld); assertNull(f.mc.thePlayer); assertTrue(old.isValid());
        }
    }
    @Test public void actualRunTickEntryCountsEachCallAndFinallyCleansAnEarlyException() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); assertEquals(-1, o.getOuterTickId());
            // No renderer is initialized: fail at mcProfiler after the real tick entry.
            for (int frameTicks : new int[] {0, 3, 0, 2}) {
                long before = o.getOuterTickId(); Tickbase.beginFrame(f.mc, false, frameTicks, 50);
                for (int tick = 0; tick < frameTicks; tick++) assertThrows(NullPointerException.class, f.mc::runTick);
                assertEquals(before + frameTicks, o.getOuterTickId()); assertNull(o.getCurrentStep());
                Tickbase.runPlayerStep(f.player, () -> { assertNull(o.getCurrentStep()); return true; });
            }
        }
    }
    @Test public void shutdownResetsInsideABodyAndManualControllerBudgetRemainsUsableOutsideSteps() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            prepare(f); RuntimeObservation o = f.runtime.getObservation(); o.beginOuterTick();
            Tickbase.runPlayerStep(f.player, () -> { f.mc.shutdown(); assertNull(o.getCurrentStep());
                assertEquals(-1, o.getActiveOuterTickId()); return true; });
            assertNull(o.getCurrentStep()); assertTrue(o.getLastSteps().get(0).isInterrupted());
            assertFalse(o.captureScene().isValid());
            f.runtime.getAttackScheduler().beginTick(); assertTrue(f.runtime.getAttackScheduler().allowControllerAttack(false));
            f.runtime.getAttackScheduler().recordControllerAttack(); assertTrue(f.runtime.getAttackScheduler().hasAttackThisTick());
        }
    }
}
