package me.youngjibbit.core.contract;

import me.youngjibbit.core.combat.AttackScheduler;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static me.youngjibbit.core.contract.AutomationEligibility.ModuleAction.*;
import static me.youngjibbit.core.contract.ExecutedStep.Kind.*;
import static me.youngjibbit.core.contract.SceneContext.Availability.*;
import static org.junit.Assert.*;

public class AutomationEligibilityTest {
    private final Object connection = new Object(), player = new Object(), world = new Object();
    private final SceneContext current = scene(ACTIVE);

    private SceneContext scene(SceneContext.Availability availability) {
        return new SceneContext(connection, player, world, 7, 12, availability);
    }

    private ExecutedStep ordinary(SceneContext context) {
        return new ExecutedStep(context, 42, 0, ORDINARY, false);
    }

    @Test public void ordinaryMayAttemptEveryAutomaticDomainWithACurrentValidScene() {
        for (AutomationEligibility.ModuleAction action : AutomationEligibility.ModuleAction.values()) {
            assertTrue(AutomationEligibility.mayAttempt(action, ordinary(current), scene(ACTIVE), 42));
        }
    }

    @Test public void earnedExtrasNeverGrantAdditionalAutomaticRights() {
        for (AutomationEligibility.ModuleAction action : AutomationEligibility.ModuleAction.values()) {
            assertFalse(AutomationEligibility.mayAttempt(action,
                    new ExecutedStep(current, 42, 1, EARNED_EXTRA, false), current, 42));
        }
    }

    @Test public void withheldObservationCannotBeSuppliedAsAnExecutedBody() throws Exception {
        assertFalse(new WithheldObservation(current, 42).isExecuted());
        // The only policy entry point requires ExecutedStep, which has no WITHHELD kind.
        assertNotNull(AutomationEligibility.class.getMethod("mayAttempt",
                AutomationEligibility.ModuleAction.class, ExecutedStep.class, SceneContext.class, long.class));
        assertFalse(AutomationEligibility.mayAttempt(ATTACK, null, current, 42));
    }

    @Test public void suspendedAndInvalidatedContextsDenyBothAtCaptureAndAtAttempt() {
        for (SceneContext.Availability availability : new SceneContext.Availability[] {SUSPENDED, INVALIDATED}) {
            for (AutomationEligibility.ModuleAction action : AutomationEligibility.ModuleAction.values()) {
                assertFalse(AutomationEligibility.mayAttempt(action, ordinary(current), scene(availability), 42));
                assertFalse(AutomationEligibility.mayAttempt(action, ordinary(scene(availability)), current, 42));
            }
        }
    }

    @Test public void everySceneIdentityOrEpochChangeDeniesTheOldStep() {
        SceneContext[] replacements = {
                new SceneContext(new Object(), player, world, 7, 12, ACTIVE),
                new SceneContext(connection, new Object(), world, 7, 12, ACTIVE),
                new SceneContext(connection, player, new Object(), 7, 12, ACTIVE),
                new SceneContext(connection, player, world, 8, 12, ACTIVE),
                new SceneContext(connection, player, world, 7, 13, ACTIVE)
        };
        for (SceneContext replacement : replacements) {
            assertFalse(AutomationEligibility.mayAttempt(PLACEMENT, ordinary(current), replacement, 42));
        }
    }

    @Test public void missingUnknownInterruptedAndStaleContextsFailClosed() {
        assertFalse(AutomationEligibility.mayAttempt(null, ordinary(current), current, 42));
        assertFalse(AutomationEligibility.mayAttempt(ATTACK, ordinary(current), null, 42));
        assertFalse(AutomationEligibility.mayAttempt(ATTACK, ordinary(current), current, -1));
        assertFalse(AutomationEligibility.mayAttempt(ATTACK, ordinary(current), current, 41));
        assertFalse(AutomationEligibility.mayAttempt(ATTACK, ordinary(current), current, 43));
        assertFalse(AutomationEligibility.mayAttempt(ATTACK,
                new ExecutedStep(current, 42, 0, ORDINARY, true), current, 42));
        SceneContext[] invalid = {
                new SceneContext(null, player, world, 7, 12, ACTIVE),
                new SceneContext(connection, null, world, 7, 12, ACTIVE),
                new SceneContext(connection, player, null, 7, 12, ACTIVE),
                new SceneContext(connection, player, world, -1, 12, ACTIVE),
                new SceneContext(connection, player, world, 7, -1, ACTIVE)
        };
        for (SceneContext context : invalid) {
            for (AutomationEligibility.ModuleAction action : AutomationEligibility.ModuleAction.values()) {
                assertFalse(AutomationEligibility.mayAttempt(action, ordinary(context), current, 42));
                assertFalse(AutomationEligibility.mayAttempt(action, ordinary(current), context, 42));
            }
        }
    }

    @Test public void resumeRequiresANewCurrentValidityCheckAndDoesNotEraseSceneIdentity() {
        ExecutedStep old = ordinary(current);
        assertFalse(AutomationEligibility.mayAttempt(PLACEMENT, old, scene(SUSPENDED), 42));
        SceneContext resumed = scene(ACTIVE);
        assertTrue(current.sameSceneAs(resumed));
        assertTrue(AutomationEligibility.mayAttempt(PLACEMENT, ordinary(resumed), resumed, 42));
        assertFalse(AutomationEligibility.mayAttempt(PLACEMENT, old,
                new SceneContext(connection, player, world, 7, 13, ACTIVE), 42));
        assertFalse(AutomationEligibility.mayAttempt(PLACEMENT, old, resumed, 43));
    }

    @Test public void repeatedQueriesAreDeterministicAndReserveNoGlobalDomainBudget() {
        ExecutedStep step = ordinary(current);
        for (int attempt = 0; attempt < 100; attempt++) {
            for (AutomationEligibility.ModuleAction action : AutomationEligibility.ModuleAction.values()) {
                assertTrue(AutomationEligibility.mayAttempt(action, step, current, 42));
            }
        }
        assertTrue(AutomationEligibility.mayAttempt(ATTACK, ordinary(current), current, 42));
        assertFalse(step.isInterrupted());
        assertTrue(current.isValid());
    }

    @Test public void eligibilityNeitherConsumesNorOverridesTheExistingAttackSchedulerBudget() {
        AttackScheduler scheduler = new AttackScheduler(() -> 1000, (min, max) -> 10);
        AtomicInteger attacks = new AtomicInteger();
        ExecutedStep step = ordinary(current);
        scheduler.beginTick();
        assertTrue(AutomationEligibility.mayAttempt(ATTACK, step, current, 42));
        assertFalse(scheduler.hasAttackThisTick());
        assertTrue(scheduler.tryAttack(1, true, 10, 10, () -> { attacks.incrementAndGet(); return true; }));
        assertTrue(AutomationEligibility.mayAttempt(ATTACK, step, current, 42));
        assertFalse(scheduler.tryAttack(2, true, 10, 10, () -> { attacks.incrementAndGet(); return true; }));
        assertEquals(1, attacks.get());
        assertTrue(scheduler.hasAttackThisTick());
    }

    @Test public void manualActionsWithoutAStepRemainOutsideTheAutomationPolicy() {
        AttackScheduler scheduler = new AttackScheduler(() -> 1000, (min, max) -> 10);
        scheduler.beginTick();
        assertFalse(AutomationEligibility.mayAttempt(ATTACK, null, null, 42));
        assertFalse(scheduler.hasAttackThisTick());
        // The existing manual controller path needs no ExecutedStep or policy result.
        assertTrue(scheduler.allowControllerAttack(false));
        scheduler.recordControllerAttack();
        assertTrue(scheduler.hasAttackThisTick());
        // This is an API boundary example, not a Minecraft.clickMouse integration test.
    }
}
