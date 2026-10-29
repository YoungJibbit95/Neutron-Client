package me.youngjibbit.core.observation;

import me.youngjibbit.core.combat.AttackScheduler;
import me.youngjibbit.core.contract.AutomationEligibility.ModuleAction;
import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class AutomatedActionAdmissionTest {
    private Object connection = new Object(), player = new Object(), world = new Object(), pipeline = new Object();
    private long epoch;
    private boolean live = true, suspended, failCapture;
    private final IllegalStateException failure = new IllegalStateException("source");
    private final RuntimeObservation observer = new RuntimeObservation(() -> {
        if (failCapture) throw failure;
        return new RuntimeObservation.SceneSample(connection, player, world, pipeline, epoch, live, suspended);
    });
    private boolean allowed(ModuleAction action) { return AutomatedActionAdmission.mayAttempt(action, observer); }
    private void ordinary(java.util.function.BooleanSupplier body) {
        observer.observePlayerStep(player, body, step -> step.getAsBoolean());
    }
    private void denied() { assertFalse(allowed(ModuleAction.ATTACK)); assertFalse(allowed(ModuleAction.PLACEMENT)); }
    @Test public void missingObserverNoStepWithheldAndFinishedHistoryNeverGrantAdmission() {
        assertFalse(AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK, null)); denied();
        observer.beginOuterTick();
        observer.observePlayerStep(player, () -> { fail("withheld body"); return true; }, step -> {});
        assertNotNull(observer.getLastWithheld()); denied();
        ordinary(() -> true); assertFalse(observer.getLastSteps().isEmpty()); denied();
    }
    @Test public void repeatedQueriesReserveNothingAndExistingSchedulerCanStillReject() {
        observer.beginOuterTick(); AttackScheduler scheduler = new AttackScheduler(() -> 1000, (min, max) -> 10);
        scheduler.beginTick();
        ordinary(() -> {
            for (int i = 0; i < 100; i++) {
                assertTrue(allowed(ModuleAction.ATTACK)); assertTrue(allowed(ModuleAction.PLACEMENT));
            }
            assertFalse(scheduler.hasAttackThisTick());
            assertTrue(scheduler.tryAttack(1, true, 10, 10, () -> true));
            assertTrue(allowed(ModuleAction.ATTACK));
            assertFalse(scheduler.tryAttack(2, true, 10, 10, () -> { fail("budget"); return true; }));
            return true;
        });
    }
    @Test public void sameOuterTickExtraDeniesBothDomainsAfterOrdinary() {
        observer.beginOuterTick(); int[] calls = {0};
        observer.observePlayerStep(player, () -> {
            if (calls[0]++ == 0) { assertTrue(allowed(ModuleAction.ATTACK)); assertTrue(allowed(ModuleAction.PLACEMENT)); }
            else denied();
            return true;
        }, step -> { step.getAsBoolean(); step.getAsBoolean(); });
        assertEquals(2, calls[0]); denied();
    }
    @Test public void everyIdentityGenerationAndPacketEpochIsFreshlyChecked() {
        observer.beginOuterTick();
        for (int mode = 0; mode < 7; mode++) {
            final int scenario = mode;
            ordinary(() -> {
                assertTrue(allowed(ModuleAction.ATTACK));
                switch (scenario) {
                    case 0: connection = new Object(); break;
                    case 1: player = new Object(); break;
                    case 2: world = new Object(); break;
                    case 3: pipeline = new Object(); break;
                    case 4: epoch++; break;
                    case 5: live = false; break;
                    default: observer.invalidateScene();
                }
                denied(); return true;
            });
            live = true;
        }
    }
    @Test public void suspensionAndResumeNeverReuseAPositiveResultOrASuspendedStep() {
        observer.beginOuterTick();
        ordinary(() -> { assertTrue(allowed(ModuleAction.ATTACK)); suspended = true; denied(); return true; });
        ordinary(() -> { suspended = false; denied(); return true; });
        ordinary(() -> { assertTrue(allowed(ModuleAction.ATTACK)); return true; });
    }
    @Test public void unknownIdentityWrongOuterIdAndInterruptedStepDeny() throws Exception {
        observer.beginOuterTick();
        ordinary(() -> {
            try {
                Field tick = RuntimeObservation.class.getDeclaredField("outerTickId"); tick.setAccessible(true);
                tick.setLong(observer, observer.getOuterTickId() + 1);
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            denied(); return true;
        });
        world = null; ordinary(() -> { denied(); return true; });
        world = new Object();
        ordinary(() -> {
            try {
                Field current = RuntimeObservation.class.getDeclaredField("currentStep"); current.setAccessible(true);
                me.youngjibbit.core.contract.ExecutedStep step = observer.getCurrentStep();
                current.set(observer, new me.youngjibbit.core.contract.ExecutedStep(step.getScene(), step.getOuterTickId(),
                        0, me.youngjibbit.core.contract.ExecutedStep.Kind.ORDINARY, true));
            } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            denied(); return true;
        });
    }
    @Test public void falseReturnExceptionAndShutdownAlwaysLoseCurrentRights() {
        observer.beginOuterTick(); ordinary(() -> false); denied();
        assertTrue(observer.getLastSteps().get(0).isInterrupted());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> ordinary(() -> { throw failure; })));
        denied(); assertNull(observer.getCurrentStep());
        ordinary(() -> { observer.reset(); denied(); return true; }); denied();
        observer.beginOuterTick(); ordinary(() -> { assertTrue(allowed(ModuleAction.PLACEMENT)); return true; });
    }
    @Test public void sourceExceptionIsNotSwallowedAndBodyFinallyStillCleansUp() {
        observer.beginOuterTick();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> ordinary(() -> {
            failCapture = true; return allowed(ModuleAction.ATTACK);
        })));
        failCapture = false; denied(); assertNull(observer.getCurrentStep());
        assertTrue(observer.getLastSteps().get(0).isInterrupted());
    }
    @Test public void aCaptureThatInvalidatesTheScopeCannotAuthorizeThePreviouslyReadStep() {
        Object p = new Object(); RuntimeObservation[] current = {null}; boolean[] reset = {false};
        current[0] = new RuntimeObservation(() -> {
            if (reset[0]) current[0].reset();
            return new RuntimeObservation.SceneSample(connection, p, world, pipeline, epoch, true, false);
        });
        current[0].beginOuterTick();
        current[0].observePlayerStep(p, () -> {
            reset[0] = true;
            assertFalse(AutomatedActionAdmission.mayAttempt(ModuleAction.ATTACK, current[0])); return true;
        }, step -> step.getAsBoolean());
        assertNull(current[0].getCurrentStep());
    }
}
