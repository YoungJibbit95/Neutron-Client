package me.youngjibbit.core.observation;

import me.youngjibbit.core.bootstrap.RuntimeServices;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.contract.WithheldObservation;
import me.youngjibbit.core.network.TickbaseBudget;
import net.minecraft.profiler.Profiler;
import org.junit.Test;

import java.util.List;
import static org.junit.Assert.*;

public class RuntimeObservationActiveTickTest {
    private final Object connection = new Object(), player = new Object(), world = new Object(), pipeline = new Object();
    private final RuntimeObservation observer = new RuntimeObservation(() ->
            new RuntimeObservation.SceneSample(connection, player, world, pipeline, 0, true, false));

    @Test public void firstIdZeroIsActiveOnlyBetweenEntryAndEndAndHistorySurvives() {
        assertEquals(-1, observer.getActiveOuterTickId()); assertEquals(-1, observer.getOuterTickId());
        observer.beginOuterTick();
        assertEquals(0, observer.getActiveOuterTickId()); assertEquals(0, observer.getOuterTickId());
        assertNull(observer.getCurrentStep());
        observer.endOuterTick();
        assertEquals(-1, observer.getActiveOuterTickId()); assertEquals(0, observer.getOuterTickId());
        observer.beginOuterTick(); assertEquals(1, observer.getActiveOuterTickId());
        observer.endOuterTick(); assertEquals(1, observer.getOuterTickId());
    }

    @Test public void readsAndPlayerCallbacksNeverIncrementTheOuterIdOrChangeHistory() {
        observer.beginOuterTick();
        observer.observePlayerStep(player, () -> {
            ExecutedStep step = observer.getCurrentStep();
            for (int i = 0; i < 100; i++) {
                assertEquals(0, observer.getActiveOuterTickId()); assertSame(step, observer.getCurrentStep());
            }
            return true;
        }, callback -> callback.getAsBoolean());
        List<ExecutedStep> history = observer.getLastSteps();
        SceneContext scene = observer.captureScene();
        for (int i = 0; i < 100; i++) {
            assertEquals(0, observer.getActiveOuterTickId()); assertSame(history, observer.getLastSteps());
            assertNull(observer.getLastWithheld()); assertNull(observer.getCurrentStep());
        }
        assertTrue(observer.isCurrent(scene)); observer.endOuterTick();
        assertEquals(-1, observer.getActiveOuterTickId()); assertSame(history, observer.getLastSteps());
    }

    @Test public void realBudgetWithheldKeepsOuterLifetimeWithoutInventingABody() {
        TickbaseBudget budget = new TickbaseBudget(() -> 0); budget.configure(1, 1, 2);
        budget.bind(player, world, connection); budget.frame(true, 1, 50, 1);
        observer.beginOuterTick(); assertNull(observer.getCurrentStep());
        observer.observePlayerStep(player, () -> { fail("withheld body"); return true; },
                callback -> budget.step(true, () -> true, callback));
        WithheldObservation withheld = observer.getLastWithheld(); assertNotNull(withheld);
        for (int i = 0; i < 100; i++) assertEquals(0, observer.getActiveOuterTickId());
        assertSame(withheld, observer.getLastWithheld()); assertTrue(observer.getLastSteps().isEmpty());
        assertNull(observer.getCurrentStep()); assertEquals(1, budget.getCredit());
        observer.endOuterTick(); assertEquals(-1, observer.getActiveOuterTickId());
    }

    @Test public void realEarnedExtraAndOrdinaryShareOneActiveOuterIdWithoutBudgetConsumptionByReads() {
        TickbaseBudget budget = new TickbaseBudget(() -> 0); budget.configure(1, 1, 2);
        budget.bind(player, world, connection); budget.frame(true, 1, 50, 1);
        budget.step(true, () -> true, () -> true); assertTrue(budget.release());
        observer.beginOuterTick(); int[] calls = {0};
        observer.observePlayerStep(player, () -> {
            assertEquals(calls[0]++ == 0 ? ExecutedStep.Kind.ORDINARY : ExecutedStep.Kind.EARNED_EXTRA,
                    observer.getCurrentStep().getKind());
            for (int i = 0; i < 100; i++) assertEquals(0, observer.getActiveOuterTickId());
            assertEquals(0, observer.getCurrentStep().getOuterTickId()); return true;
        }, callback -> budget.step(false, () -> true, callback));
        assertEquals(2, calls[0]); assertEquals(1, budget.getExtraSteps()); assertEquals(0, budget.getCredit());
        assertEquals(0, observer.getOuterTickId()); observer.endOuterTick();
    }

    @Test public void invalidationPreservesLifetimeButRejectsOldSceneAndResetEndsLifetime() {
        observer.beginOuterTick(); SceneContext old = observer.captureScene();
        observer.invalidateScene();
        assertEquals(0, observer.getActiveOuterTickId()); assertFalse(observer.isCurrent(old));
        observer.reset(); assertEquals(-1, observer.getActiveOuterTickId());
        assertEquals(0, observer.getOuterTickId()); assertFalse(observer.captureScene().isValid());
        observer.beginOuterTick(); assertEquals(1, observer.getActiveOuterTickId());
        assertTrue(observer.captureScene().isValid()); observer.endOuterTick();
    }

    @Test public void actualRunTickFinallyEndsLifetimeAndFramesNeverOpenIt() throws Exception {
        try (RotationBehaviorHookTest.Fixture f = new RotationBehaviorHookTest.Fixture()) {
            RuntimeObservation o = f.runtime.getObservation(); assertSame(o, RuntimeServices.observation());
            IllegalStateException failure = new IllegalStateException("stop after actual tick entry");
            java.lang.reflect.Field profiler = net.minecraft.client.Minecraft.class.getDeclaredField("mcProfiler");
            profiler.setAccessible(true); profiler.set(f.mc, new Profiler() {
                @Override public void startSection(String name) {
                    assertEquals(o.getOuterTickId(), o.getActiveOuterTickId());
                    assertTrue(o.getActiveOuterTickId() >= 0); assertNull(o.getCurrentStep()); throw failure;
                }
            });
            for (int frameTicks : new int[] {0, 3, 0, 2}) {
                long before = o.getOuterTickId();
                me.youngjibbit.module.impl.exploit.Tickbase.beginFrame(f.mc, false, frameTicks, 50);
                assertEquals(-1, o.getActiveOuterTickId()); assertEquals(before, o.getOuterTickId());
                for (int tick = 0; tick < frameTicks; tick++) {
                    assertSame(failure, assertThrows(IllegalStateException.class, f.mc::runTick));
                    assertEquals(-1, o.getActiveOuterTickId()); assertNull(o.getCurrentStep());
                }
                assertEquals(before + frameTicks, o.getOuterTickId());
            }
            assertNull(f.channel.readOutbound());
        }
    }
}
