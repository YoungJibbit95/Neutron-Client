package me.youngjibbit.core.observation;

import me.youngjibbit.core.contract.*;
import me.youngjibbit.core.network.TickbaseBudget;
import org.junit.Test;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class RuntimeObservationTest {
    private Object connection = new Object(), player = new Object(), world = new Object(), pipeline = new Object();
    private long epoch;
    private boolean live = true, suspended;
    private final RuntimeObservation observer = new RuntimeObservation(() ->
            new RuntimeObservation.SceneSample(connection, player, world, pipeline, epoch, live, suspended));

    private void ordinary(java.util.function.BooleanSupplier body) {
        observer.observePlayerStep(player, body, callback -> callback.getAsBoolean());
    }
    private boolean eligible() {
        return AutomationEligibility.mayAttempt(AutomationEligibility.ModuleAction.ATTACK,
                observer.getCurrentStep(), observer.captureScene(), observer.getOuterTickId());
    }
    @Test public void ordinaryIsVisibleOnlyInsideTheActuallyEnteredCallback() {
        observer.beginOuterTick(); AtomicInteger calls = new AtomicInteger();
        ordinary(() -> {
            calls.incrementAndGet(); ExecutedStep step = observer.getCurrentStep();
            assertNotNull(step); assertEquals(ExecutedStep.Kind.ORDINARY, step.getKind());
            assertEquals(0, step.getStepIndex()); assertEquals(0, step.getOuterTickId());
            assertTrue(eligible()); return true;
        });
        assertEquals(1, calls.get()); assertNull(observer.getCurrentStep()); assertFalse(eligible());
        assertFalse(observer.getLastSteps().get(0).isInterrupted()); assertNull(observer.getLastWithheld());
        observer.endOuterTick(); assertNull(observer.getCurrentStep());
    }
    @Test public void actualBudgetWithholdDoesNotEnterABodyOrCreateAnExecutedStep() {
        TickbaseBudget budget = new TickbaseBudget(() -> 0); budget.configure(1, 1, 2);
        budget.bind(player, world, connection); budget.frame(true, 1, 50, 1);
        observer.beginOuterTick(); AtomicInteger calls = new AtomicInteger();
        observer.observePlayerStep(player, () -> { calls.incrementAndGet(); return true; },
                callback -> budget.step(true, () -> true, callback));
        assertEquals(0, calls.get()); assertTrue(observer.getLastSteps().isEmpty());
        assertNotNull(observer.getLastWithheld()); assertEquals(0, observer.getLastWithheld().getOuterTickId());
        assertEquals(1, budget.getCredit()); assertFalse(eligible());
    }
    @Test public void actualBudgetEarnedCallbackHasIndexOneAndSharesTheOuterTick() {
        TickbaseBudget budget = new TickbaseBudget(() -> 0); budget.configure(1, 1, 2);
        budget.bind(player, world, connection); budget.frame(true, 1, 50, 1);
        budget.step(true, () -> true, () -> true); assertTrue(budget.release());
        observer.beginOuterTick(); List<ExecutedStep> entered = new ArrayList<>();
        observer.observePlayerStep(player, () -> { entered.add(observer.getCurrentStep()); return true; },
                callback -> budget.step(false, () -> true, callback));
        assertEquals(2, entered.size()); assertEquals(ExecutedStep.Kind.ORDINARY, entered.get(0).getKind());
        assertEquals(ExecutedStep.Kind.EARNED_EXTRA, entered.get(1).getKind());
        assertEquals(1, entered.get(1).getStepIndex());
        assertEquals(entered.get(0).getOuterTickId(), entered.get(1).getOuterTickId());
        assertEquals(0, observer.getOuterTickId()); assertEquals(1, budget.getExtraSteps());
        assertNull(observer.getCurrentStep()); assertNull(observer.getLastWithheld());
    }
    @Test public void falseCallbackIsInterruptedAndNeverWithheld() {
        observer.beginOuterTick(); ordinary(() -> false);
        assertEquals(1, observer.getLastSteps().size()); assertTrue(observer.getLastSteps().get(0).isInterrupted());
        assertNull(observer.getLastWithheld()); assertNull(observer.getCurrentStep());
    }
    @Test public void throwingBodyKeepsTheOriginalExceptionAndAlwaysCleansUp() {
        observer.beginOuterTick(); IllegalStateException failure = new IllegalStateException("body");
        assertSame(failure, assertThrows(IllegalStateException.class, () -> ordinary(() -> { throw failure; })));
        assertNull(observer.getCurrentStep()); assertTrue(observer.getLastSteps().get(0).isInterrupted());
        assertNull(observer.getLastWithheld());
    }
    @Test public void exceptionBeforeDecisionIsNotAPhantomWithhold() {
        observer.beginOuterTick();
        assertThrows(IllegalStateException.class, () -> observer.observePlayerStep(player, () -> true,
                callback -> { throw new IllegalStateException("before decision"); }));
        assertTrue(observer.getLastSteps().isEmpty()); assertNull(observer.getLastWithheld());
        assertNull(observer.getCurrentStep());
    }
    @Test public void exceptionBetweenBodiesDoesNotUndoTheCompletedOrdinaryStep() {
        observer.beginOuterTick();
        assertThrows(IllegalStateException.class, () -> observer.observePlayerStep(player, () -> true,
                callback -> { callback.getAsBoolean(); throw new IllegalStateException("decision tail"); }));
        assertEquals(1, observer.getLastSteps().size()); assertFalse(observer.getLastSteps().get(0).isInterrupted());
        assertNull(observer.getCurrentStep()); assertNull(observer.getLastWithheld());
    }
    @Test public void everyIdentityAndEpochChangeRejectsTheOldSnapshotIncludingAba() {
        observer.beginOuterTick();
        for (int change = 0; change < 5; change++) {
            SceneContext old = observer.captureScene(); Object a;
            switch (change) {
                case 0: a = connection; connection = new Object(); observer.captureScene(); connection = a; break;
                case 1: a = player; player = new Object(); observer.captureScene(); player = a; break;
                case 2: a = world; world = new Object(); observer.captureScene(); world = a; break;
                case 3: a = pipeline; pipeline = new Object(); observer.captureScene(); pipeline = a; break;
                default: epoch++;
            }
            assertFalse(observer.isCurrent(old));
            assertTrue(observer.captureScene().getConnectionGeneration() > old.getConnectionGeneration());
            assertTrue(old.isValid()); // historical values never invalidate themselves
        }
    }
    @Test public void anExplicitBoundaryRejectsAbaEvenWithoutAnIntermediateSample() {
        observer.beginOuterTick(); SceneContext old = observer.captureScene();
        observer.invalidateScene(); assertFalse(observer.isCurrent(old));
        assertTrue(observer.captureScene().isValid());
    }
    @Test public void suspensionPreservesLogicalSceneAndResumeRechecksFreshState() {
        observer.beginOuterTick(); SceneContext active = observer.captureScene(); suspended = true;
        SceneContext paused = observer.captureScene(); assertTrue(paused.isValid()); assertTrue(paused.isSuspended());
        assertTrue(active.sameSceneAs(paused)); ordinary(() -> { assertFalse(eligible()); return true; });
        suspended = false; ordinary(() -> { assertTrue(eligible()); epoch++;
            assertFalse(eligible()); return true; });
        assertFalse(observer.isCurrent(active));
    }
    @Test public void unavailableOrMissingSceneNeverPassesTheAutomationPrerequisite() {
        observer.beginOuterTick(); SceneContext old = observer.captureScene(); live = false;
        assertFalse(observer.captureScene().isValid()); assertFalse(observer.isCurrent(old));
        ordinary(() -> { assertFalse(eligible()); return true; });
        live = true; world = null; assertFalse(observer.captureScene().isValid());
    }
    @Test public void resetDuringABodyClearsCurrentStepAndDoesNotRestoreItInFinally() {
        observer.beginOuterTick(); ordinary(() -> { observer.reset();
            assertNull(observer.getCurrentStep()); return true; });
        assertNull(observer.getCurrentStep()); assertTrue(observer.getLastSteps().get(0).isInterrupted());
        assertFalse(observer.captureScene().isValid());
        observer.beginOuterTick(); assertTrue(observer.captureScene().isValid());
    }
    @Test public void callsOutsideAnOuterTickAndForeignPlayersDoNotInventTicksOrObservations() {
        AtomicInteger calls = new AtomicInteger(); ordinary(() -> { calls.incrementAndGet(); return true; });
        assertEquals(-1, observer.getOuterTickId()); assertTrue(observer.getLastSteps().isEmpty());
        observer.beginOuterTick(); observer.observePlayerStep(new Object(), () -> {
            calls.incrementAndGet(); assertNull(observer.getCurrentStep()); return true;
        }, callback -> callback.getAsBoolean());
        observer.endOuterTick(); ordinary(() -> { calls.incrementAndGet(); return true; });
        assertEquals(3, calls.get()); assertTrue(observer.getLastSteps().isEmpty());
        assertEquals(0, observer.getOuterTickId());
    }
    @Test public void onlyOuterTickEntryIncrementsTheIdAndHistoryIsBoundedToTheLastInvocation() {
        assertEquals(-1, observer.getOuterTickId()); observer.beginOuterTick(); ordinary(() -> true);
        observer.endOuterTick(); assertEquals(0, observer.getOuterTickId());
        observer.beginOuterTick(); assertEquals(1, observer.getOuterTickId());
        assertTrue(observer.getLastSteps().isEmpty()); assertNull(observer.getLastWithheld());
        ordinary(() -> true); ordinary(() -> false); assertEquals(1, observer.getLastSteps().size());
        assertThrows(UnsupportedOperationException.class, () -> observer.getLastSteps().clear());
    }
    @Test public void observerRetainsIdentityTokensOnlyThroughWeakReferences() throws Exception {
        observer.beginOuterTick(); ordinary(() -> true);
        int weakIdentities = 0;
        for (Field field : RuntimeObservation.class.getDeclaredFields()) {
            field.setAccessible(true); Object value = field.get(observer);
            assertNotSame(connection, value); assertNotSame(player, value);
            assertNotSame(world, value); assertNotSame(pipeline, value);
            if (value instanceof WeakReference) weakIdentities++;
        }
        assertEquals(4, weakIdentities);
    }
}
