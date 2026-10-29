package me.youngjibbit.core.world;

import me.youngjibbit.core.bootstrap.ClientRuntime;
import me.youngjibbit.core.combat.RotationBehaviorHookTest;
import me.youngjibbit.core.contract.ExecutedStep;
import me.youngjibbit.core.observation.RuntimeObservation;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.*;
import java.lang.reflect.*;
import static org.junit.Assert.*;

/** Actual Scaffold attempt entry and unchanged placement service, using its existing Access fixture. */
public class AutomatedPlacementAdmissionTest {
    private static RotationBehaviorHookTest.Fixture client;
    private ScaffoldPlacementTest.Fixture placement;
    private RuntimeObservation observer;
    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field f = type.getDeclaredField(name); f.setAccessible(true); return f;
    }
    @BeforeClass public static void open() throws Exception { client = new RotationBehaviorHookTest.Fixture(); }
    @AfterClass public static void close() throws Exception { client.close(); }
    @Before public void setup() throws Exception {
        placement = new ScaffoldPlacementTest.Fixture();
        field(ClientRuntime.class, "scaffoldPlacementService").set(client.runtime, placement.service);
        field(ClientRuntime.class, "rotationManager").set(client.runtime, placement.rotations);
        observer = client.runtime.getObservation(); observer.reset(); observer.beginOuterTick();
        placement.align();
        field(Scaffold.class, "frameTarget").set(client.scaffold, placement.plan);
    }
    @After public void cleanup() { observer.reset(); }
    private boolean attempt(boolean pre) {
        try {
            field(Scaffold.class, "frameSequence").setLong(client.scaffold, placement.rotations.getMotionSequence());
            Method method = Scaffold.class.getDeclaredMethod("tryPlace", boolean.class); method.setAccessible(true);
            return (Boolean) method.invoke(client.scaffold, pre);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException) throw (RuntimeException) failure.getCause();
            if (failure.getCause() instanceof Error) throw (Error) failure.getCause();
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private void ordinary(java.util.function.BooleanSupplier body) {
        observer.observePlayerStep(client.player, body, step -> step.getAsBoolean());
    }
    @Test public void ordinaryReachesServiceAndItsExistingBudgetStillRejectsSecondAttempt() {
        placement.world.success = false;
        ordinary(() -> {
            assertFalse(attempt(false)); assertEquals(1, placement.world.clicks);
            assertEquals(ScaffoldPlacementService.Rejection.CONTROLLER, placement.service.getLastRejection());
            assertFalse(attempt(false)); assertEquals(1, placement.world.clicks);
            assertEquals(ScaffoldPlacementService.Rejection.BUDGET, placement.service.getLastRejection());
            return true;
        });
    }
    @Test public void extraDenialConsumesNoSlotBudgetOrSuccessAndOrdinaryCanStillPlace() {
        observer.observePlayerStep(client.player, () -> {
            if (observer.getCurrentStep().getKind() == ExecutedStep.Kind.EARNED_EXTRA) {
                assertFalse(attempt(false)); assertEquals(0, placement.world.clicks);
                assertTrue(placement.world.slots.isEmpty()); assertEquals(0, placement.world.selected);
                assertEquals(ScaffoldPlacementService.Rejection.NONE, placement.service.getLastRejection());
            }
            return true;
        }, step -> { step.getAsBoolean(); step.getAsBoolean(); });
        ordinary(() -> { assertTrue(attempt(false)); return true; });
        assertEquals(1, placement.world.clicks); assertEquals(1, placement.world.swings);
    }
    @Test public void extraAfterOrdinaryDoesNotEnterServiceOrReplaceItsRejection() {
        placement.world.success = false;
        observer.observePlayerStep(client.player, () -> {
            assertFalse(attempt(false));
            assertEquals(1, placement.world.clicks);
            assertEquals(ScaffoldPlacementService.Rejection.CONTROLLER, placement.service.getLastRejection());
            return true;
        }, step -> { step.getAsBoolean(); step.getAsBoolean(); });
    }
    @Test public void preUsesPreviousAcceptedAngleAndPostUsesCurrentAcceptedAngle() {
        placement.rotations.beginMotion(0, 0);
        placement.rotations.request("aura", 20, -90, 0);
        ordinary(() -> { assertTrue(attempt(true)); return true; });
        assertEquals(1, placement.world.clicks);
        setupNewPlan();
        placement.rotations.beginMotion(0, 0); placement.rotations.request("scaffold", 30, 0, 0);
        placement.rotations.finishMotion(0, 0, true);
        ordinary(() -> { assertFalse(attempt(false)); return true; });
        assertEquals(0, placement.world.clicks); assertTrue(placement.world.slots.isEmpty());
    }
    private void setupNewPlan() { try { setup(); } catch (Exception error) { throw new AssertionError(error); } }
    @Test public void pendingFirstRotationIsNotAcceptanceAndCancelledPostCannotPlace() {
        placement.rotations.reset(); placement.rotations.beginMotion(0, 0);
        ScaffoldRotationPlan aim = ScaffoldRotationPlan.build("Back", placement.world.eyes(), placement.plan.getHitVec());
        placement.rotations.request("scaffold", 30, aim.getYaw(), aim.getPitch());
        ordinary(() -> { assertFalse(attempt(true)); return true; });
        placement.rotations.finishMotion(aim.getYaw(), aim.getPitch(), false);
        ordinary(() -> { assertFalse(attempt(false)); return true; });
        assertEquals(0, placement.world.clicks); assertTrue(placement.world.slots.isEmpty());
    }
    @Test public void noStepWithheldAndNewPacketEpochCannotAttemptPlacement() {
        assertFalse(attempt(false));
        observer.observePlayerStep(client.player, () -> { fail("withheld"); return true; }, step -> {});
        assertNotNull(observer.getLastWithheld()); assertFalse(attempt(false));
        ordinary(() -> {
            client.network.getClientPackets().worldChanged(); assertFalse(attempt(false)); return true;
        });
        assertEquals(0, placement.world.clicks); assertTrue(placement.world.slots.isEmpty());
    }
    @Test public void placementExceptionPropagatesAndCleansStepAndOwnedSlot() {
        placement.world.throwClick = true;
        assertThrows(IllegalStateException.class, () -> ordinary(() -> attempt(false)));
        assertNull(observer.getCurrentStep()); assertTrue(observer.getLastSteps().get(0).isInterrupted());
        assertEquals(0, placement.world.selected); assertFalse(attempt(false));
    }
}
