package me.youngjibbit.core.world;

import org.junit.Test;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class AdaptiveBridgeSafetyControllerTest {
    private final Object owner = new Object(), world = new Object(), player = new Object();

    private AdaptiveBridgePlanner.Scope scope(Object connection, long epoch, long generation) {
        return new AdaptiveBridgePlanner.Scope(owner, world, player, connection, epoch, generation);
    }

    private static AdaptiveBridgeSafetyController.Snapshot sample(AdaptiveBridgePlanner.Scope scope,
                                                                 long sequence) {
        AdaptiveBridgeSafetyController.Snapshot s = new AdaptiveBridgeSafetyController.Snapshot();
        s.scope = scope;
        s.inputSequence = sequence;
        s.enabled = s.contextValid = s.ownerAllowed = s.grounded = true;
        s.currentSupported = true;
        s.pathSupported = false;
        return s;
    }

    private static void noRequest(AdaptiveBridgeSafetyController.Decision decision) {
        assertFalse(decision.requestSneak);
        assertFalse(decision.limitInput);
    }

    @Test public void guardStartsBeforeLosingSupportAndReleasesOnFreshSafeTerrain() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgeSafetyController.Snapshot s = sample(scope(new Object(), 0, 1), 1);
        AdaptiveBridgeSafetyController.Decision edge = controller.update(s);
        assertEquals(AdaptiveBridgeSafetyController.State.EDGE_GUARD, edge.state);
        assertTrue(edge.requestSneak);
        assertFalse("a slow approach is needed to expose an initially occluded face", edge.limitInput);
        AdaptiveBridgeSafetyController.Input slowed = edge.apply(1, -1, false);
        assertTrue(slowed.sneak);
        assertEquals(.3f, slowed.forward, 0);
        assertEquals(-.3f, slowed.strafe, 0);

        s.inputSequence++;
        s.pathSupported = true;
        AdaptiveBridgeSafetyController.Decision released = controller.update(s);
        noRequest(released);
        AdaptiveBridgeSafetyController.Input normal = released.apply(1, -1, false);
        assertFalse(normal.sneak);
        assertEquals(1, normal.forward, 0);
        assertEquals(-1, normal.strafe, 0);
    }

    @Test public void acceptedRotationAndSuccessfulControllerResultCannotInventTerrain() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgePlanner.Scope token = scope(new Object(), 0, 1);
        controller.observePlan(token, 8, true);
        assertEquals(AdaptiveBridgeSafetyController.State.ALIGNING, controller.getState());
        assertFalse(controller.ready(token, 7));
        assertTrue(controller.ready(token, 8));
        assertEquals(AdaptiveBridgeSafetyController.State.READY, controller.getState());
        assertTrue(controller.placing(token, 8));
        assertEquals(AdaptiveBridgeSafetyController.State.PLACING, controller.getState());
        controller.placementResult(token, 8, true);
        assertEquals(AdaptiveBridgeSafetyController.State.SEARCHING, controller.getState());

        AdaptiveBridgeSafetyController.Snapshot s = sample(token, 1);
        s.hasCandidate = s.acceptedIntent = true;
        assertTrue("even an accepted intent needs currently supported movement", controller.update(s).requestSneak);
        s.inputSequence++;
        s.pathSupported = true;
        noRequest(controller.update(s));
    }

    @Test public void rejectedAttemptRecoversAndCannotBeRearmedByRepeatedReadyOrPost() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgePlanner.Scope token = scope(new Object(), 0, 1);
        controller.observePlan(token, 4, true);
        assertTrue(controller.ready(token, 4));
        assertTrue(controller.placing(token, 4));
        assertFalse(controller.ready(token, 4));
        assertFalse(controller.placing(token, 4));
        controller.placementResult(token, 4, false);
        assertEquals(AdaptiveBridgeSafetyController.State.RECOVERING, controller.getState());
        controller.placementResult(token, 4, true);
        controller.observePlan(token, 4, true);
        assertEquals(AdaptiveBridgeSafetyController.State.RECOVERING, controller.getState());
        assertFalse(controller.ready(token, 4));
        controller.observePlan(token, 5, true);
        assertEquals(AdaptiveBridgeSafetyController.State.ALIGNING, controller.getState());
        assertTrue(controller.ready(token, 5));
    }

    @Test public void physicalAndForeignSneakAreNeverReleasedOrSlowedTwice() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgeSafetyController.Snapshot s = sample(scope(new Object(), 0, 1), 1);
        AdaptiveBridgeSafetyController.Decision decision = controller.update(s);
        AdaptiveBridgeSafetyController.Input physical = decision.apply(.3f, -.3f, true);
        assertTrue(physical.sneak);
        assertEquals(.3f, physical.forward, 0);
        assertEquals(-.3f, physical.strafe, 0);
        AdaptiveBridgeSafetyController.Input first = decision.apply(1, 1, false);
        AdaptiveBridgeSafetyController.Input repeated = decision.apply(first.forward, first.strafe, first.sneak);
        assertEquals(first.forward, repeated.forward, 0);
        assertEquals(first.strafe, repeated.strafe, 0);

        s.inputSequence++;
        s.pathSupported = true;
        AdaptiveBridgeSafetyController.Input released = controller.update(s).apply(.3f, 0, true);
        assertTrue("releasing our request cannot release the user's key", released.sneak);
        assertEquals(.3f, released.forward, 0);
    }

    @Test public void stopUsesGroundedSneakAsWellAsInputLimiting() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgeSafetyController.Snapshot s = sample(scope(new Object(), 0, 1), 1);
        s.mode = AdaptiveBridgeSafetyController.Mode.STOP;
        AdaptiveBridgeSafetyController.Decision d = controller.update(s);
        assertTrue("zero input cannot remove residual velocity", d.requestSneak);
        assertTrue(d.limitInput);
        AdaptiveBridgeSafetyController.Input stopped = d.apply(-1, 1, false);
        assertEquals(0, stopped.forward, 0);
        assertEquals(0, stopped.strafe, 0);
        assertTrue(stopped.sneak);
        s.inputSequence++;
        s.grounded = false;
        noRequest(controller.update(s));
    }

    @Test public void disabledInvalidForeignOrAirborneInputsReleaseOnlyOurRequest() {
        for (int scenario = 0; scenario < 4; scenario++) {
            AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
            AdaptiveBridgeSafetyController.Snapshot s = sample(scope(new Object(), 0, 1), 1);
            assertTrue(controller.update(s).requestSneak);
            s.inputSequence++;
            if (scenario == 0) s.enabled = false;
            if (scenario == 1) s.contextValid = false;
            if (scenario == 2) s.ownerAllowed = false;
            if (scenario == 3) s.grounded = false;
            AdaptiveBridgeSafetyController.Decision d = controller.update(s);
            noRequest(d);
            AdaptiveBridgeSafetyController.Input physical = d.apply(.3f, -.3f, true);
            assertTrue(physical.sneak);
            assertEquals(.3f, physical.forward, 0);
        }
    }

    @Test public void offLeavesInputUntouchedEvenAtAnUnsafeEdge() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgeSafetyController.Snapshot s = sample(scope(new Object(), 0, 1), 1);
        s.mode = AdaptiveBridgeSafetyController.Mode.OFF;
        AdaptiveBridgeSafetyController.Decision d = controller.update(s);
        noRequest(d);
        AdaptiveBridgeSafetyController.Input input = d.apply(1, -1, false);
        assertFalse(input.sneak);
        assertEquals(1, input.forward, 0);
        assertEquals(-1, input.strafe, 0);
    }

    @Test public void duplicateInputCannotRearmAfterItsContextChanges() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgeSafetyController.Snapshot s = sample(scope(new Object(), 0, 1), 12);
        AdaptiveBridgeSafetyController.Decision first = controller.update(s);
        assertSame(first, controller.update(s));
        s.ownerAllowed = false;
        noRequest(controller.update(s));
        s.ownerAllowed = true;
        noRequest(controller.update(s));
        s.inputSequence++;
        assertTrue(controller.update(s).requestSneak);
        s.inputSequence = 11;
        noRequest(controller.update(s));
        assertEquals(AdaptiveBridgeSafetyController.State.EDGE_GUARD, controller.getState());
    }

    @Test public void newConnectionWithLowerEpochWorksAndOldCallbacksCannotChangeIt() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgePlanner.Scope old = scope(new Object(), 7, 1);
        assertTrue(controller.update(sample(old, 50)).requestSneak);
        AdaptiveBridgePlanner.Scope next = scope(new Object(), 0, 2);
        controller.observePlan(next, 1, true);
        assertTrue(controller.ready(next, 1));
        controller.invalidate(old);
        controller.observePlan(old, 100, false);
        noRequest(controller.update(sample(old, 100)));
        assertEquals(AdaptiveBridgeSafetyController.State.READY, controller.getState());
        assertTrue(controller.update(sample(next, 1)).requestSneak);
    }

    @Test public void resetTombstoneForeignOwnerAndSameConnectionEpochCannotBeBypassed() {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        Object connection = new Object();
        AdaptiveBridgePlanner.Scope first = scope(connection, 4, 3);
        assertTrue(controller.update(sample(first, 1)).requestSneak);
        controller.reset();
        noRequest(controller.update(sample(first, 2)));
        noRequest(controller.update(sample(scope(connection, 3, 4), 1)));
        AdaptiveBridgePlanner.Scope foreign = new AdaptiveBridgePlanner.Scope(new Object(), world,
                player, new Object(), 0, 100);
        noRequest(controller.update(sample(foreign, 1)));
        assertTrue(controller.update(sample(scope(connection, 4, 4), 1)).requestSneak);
    }

    @Test public void backgroundThreadCannotAcquireClearOrInvalidateTheController() throws Exception {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        AdaptiveBridgePlanner.Scope token = scope(new Object(), 0, 1);
        controller.observePlan(token, 1, true);
        AtomicReference<AdaptiveBridgeSafetyController.Decision> result = new AtomicReference<>();
        Thread thread = new Thread(() -> {
            result.set(controller.update(sample(token, 1)));
            controller.invalidate(token);
            controller.reset();
        });
        thread.start();
        thread.join();
        noRequest(result.get());
        assertEquals(AdaptiveBridgeSafetyController.State.ALIGNING, controller.getState());
        assertTrue(controller.update(sample(token, 1)).requestSneak);
    }

    @Test public void resetRetainsBarriersWithoutStronglyRetainingTheUnloadedScene() throws Exception {
        AdaptiveBridgeSafetyController controller = new AdaptiveBridgeSafetyController();
        Object connection = new Object();
        AdaptiveBridgePlanner.Scope token = scope(connection, 7, 4);
        assertTrue(controller.update(sample(token, 1)).requestSneak);
        controller.reset();
        for (Object unloaded : new Object[]{token, world, player, connection})
            assertFalse("reset must release strong scene references",
                    stronglyRetains(controller, unloaded, new IdentityHashMap<>()));
        // Releasing references does not erase the stale-scope barrier.
        noRequest(controller.update(sample(token, 2)));
        AdaptiveBridgePlanner.Scope next = scope(new Object(), 0, 5);
        assertTrue(controller.update(sample(next, 1)).requestSneak);
        noRequest(controller.update(sample(token, 3)));
    }

    private static boolean stronglyRetains(Object object, Object target,
                                           IdentityHashMap<Object, Boolean> visited) throws Exception {
        if (object == target) return true;
        if (object == null || object instanceof Reference<?> || visited.put(object, Boolean.TRUE) != null
                || !object.getClass().getName().startsWith("me.youngjibbit.core.world.")) return false;
        for (Field field : object.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) continue;
            field.setAccessible(true);
            if (stronglyRetains(field.get(object), target, visited)) return true;
        }
        return false;
    }

    @Test public void sweptGroundProbeFindsAHoleEvenWithSupportedEndpoints() {
        AtomicInteger calls = new AtomicInteger();
        AdaptiveBridgeSafetyController.Ground ground = AdaptiveBridgeSafetyController.probe((x, z) -> {
            calls.incrementAndGet();
            return Math.abs(x - .2) > 1e-8;
        }, .6, 0);
        assertTrue(ground.currentSupported);
        assertFalse(ground.pathSupported);
        assertEquals(4, calls.get());
    }

    @Test public void probeBoundsEveryDirectionAndNeverApprovesClippedVelocity() {
        for (double[] direction : new double[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}}) {
            List<double[]> queries = new ArrayList<>();
            AdaptiveBridgeSafetyController.Ground ground = AdaptiveBridgeSafetyController.probe((x, z) -> {
                queries.add(new double[]{x, z});
                return true;
            }, direction[0], direction[1]);
            assertTrue(ground.currentSupported);
            assertFalse("a clamped probe must not certify the unexamined movement", ground.pathSupported);
            assertEquals(4, queries.size());
            for (double[] query : queries) assertTrue(Math.hypot(query[0], query[1]) <= .6000000001);
        }
    }

    @Test public void rotationRoundingAtTheProbeLimitDoesNotInventAnUnsafeEdge() {
        for (int degrees = 0; degrees < 360; degrees++) {
            double angle = Math.toRadians(degrees);
            AdaptiveBridgeSafetyController.Ground ground = AdaptiveBridgeSafetyController.probe(
                    (x, z) -> true, .6 * Math.cos(angle), .6 * Math.sin(angle));
            assertTrue("rounding at heading " + degrees, ground.pathSupported);
            assertTrue(Math.hypot(ground.offsetX, ground.offsetZ) <= .6 + Math.ulp(.6) * 4);
        }
        assertTrue(AdaptiveBridgeSafetyController.probe((x, z) -> true,
                Math.nextUp(.6), 0).pathSupported);
        assertFalse(AdaptiveBridgeSafetyController.probe((x, z) -> true,
                .6000001, 0).pathSupported);
    }

    @Test public void invalidAndStationaryProbesHaveFiniteBoundedWork() {
        AtomicInteger calls = new AtomicInteger();
        AdaptiveBridgeSafetyController.SupportProbe probe = (x, z) -> { calls.incrementAndGet(); return true; };
        assertFalse(AdaptiveBridgeSafetyController.probe(probe, Double.NaN, 0).pathSupported);
        assertFalse(AdaptiveBridgeSafetyController.probe(probe, 0, Double.POSITIVE_INFINITY).pathSupported);
        assertEquals(0, calls.get());
        assertTrue(AdaptiveBridgeSafetyController.probe(probe, 0, 0).pathSupported);
        assertEquals(1, calls.get());
    }
}
