package me.youngjibbit.core.world;

import me.youngjibbit.core.contract.SceneContext;
import me.youngjibbit.core.observation.RuntimeObservation;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

/** Observed markers invalidate release evidence without changing scene/admission or sampling on reads. */
public class RuntimeReleaseEvidenceWave2Test {
    @Test public void markerQueriesNeverInvokeTheSceneSupplierOrInventAnOuterLifetime() {
        AtomicInteger captures = new AtomicInteger();
        Object connection = new Object(), player = new Object(), world = new Object(), pipeline = new Object();
        RuntimeObservation observation = new RuntimeObservation(() -> {
            captures.incrementAndGet();
            return new RuntimeObservation.SceneSample(connection, player, world, pipeline, 0, true, false);
        });
        for (int i = 0; i < 100; i++) {
            assertEquals(0, observation.getObservedConnectionGeneration());
            assertEquals(0, observation.getObservedSuspensionRevision());
            assertEquals(-1, observation.getActiveOuterTickId());
            assertEquals(-1, observation.getOuterTickId());
            assertNull(observation.getCurrentStep());
        }
        assertEquals(0, captures.get());
        observation.beginOuterTick();
        observation.endOuterTick();
        int afterCapture = captures.get();
        for (int i = 0; i < 100; i++) {
            observation.getObservedConnectionGeneration();
            observation.getObservedSuspensionRevision();
        }
        assertEquals(afterCapture, captures.get());
        assertEquals(0, observation.getOuterTickId());
        assertEquals(-1, observation.getActiveOuterTickId());
        assertTrue(observation.getLastSteps().isEmpty());
    }

    @Test public void observedSuspensionResumeAbaChangesOnlyItsMarkerAndPreservesSceneIdentity() {
        AtomicBoolean suspended = new AtomicBoolean();
        Object connection = new Object(), player = new Object(), world = new Object(), pipeline = new Object();
        RuntimeObservation observation = new RuntimeObservation(() ->
                new RuntimeObservation.SceneSample(connection, player, world, pipeline, 0, true, suspended.get()));
        observation.beginOuterTick();
        SceneContext active = observation.captureScene();
        long generation = active.getConnectionGeneration();
        suspended.set(true);
        SceneContext paused = observation.captureScene();
        assertTrue(paused.isSuspended());
        assertTrue(active.sameSceneAs(paused));
        assertEquals(1, observation.getObservedSuspensionRevision());
        observation.captureScene();
        assertEquals("repeated suspended samples are one observed entry", 1, observation.getObservedSuspensionRevision());
        suspended.set(false);
        SceneContext resumed = observation.captureScene();
        assertFalse(resumed.isSuspended());
        assertTrue(active.sameSceneAs(resumed));
        assertEquals(generation, observation.getObservedConnectionGeneration());
        assertEquals("resume preserves the evidence of the intervening suspension", 1, observation.getObservedSuspensionRevision());
        suspended.set(true);
        observation.captureScene();
        assertEquals(2, observation.getObservedSuspensionRevision());
        assertEquals(generation, observation.getObservedConnectionGeneration());
        assertEquals(0, observation.getActiveOuterTickId());
        assertNull(observation.getCurrentStep());
        observation.endOuterTick();
    }

    @Test public void invalidationIsVisibleThroughPureGenerationReadAndDoesNotReuseSuspensionMarkers() {
        AtomicInteger captures = new AtomicInteger();
        Object connection = new Object(), player = new Object(), world = new Object(), pipeline = new Object();
        RuntimeObservation observation = new RuntimeObservation(() -> {
            captures.incrementAndGet();
            return new RuntimeObservation.SceneSample(connection, player, world, pipeline, 0, true, false);
        });
        observation.beginOuterTick();
        long generation = observation.getObservedConnectionGeneration();
        long suspension = observation.getObservedSuspensionRevision();
        int samples = captures.get();
        observation.invalidateScene();
        assertEquals(generation + 1, observation.getObservedConnectionGeneration());
        assertEquals(suspension, observation.getObservedSuspensionRevision());
        assertEquals(samples, captures.get());
        assertEquals("invalidation does not end the real outer lifetime", 0, observation.getActiveOuterTickId());
        observation.reset();
        assertEquals(generation + 2, observation.getObservedConnectionGeneration());
        assertEquals(suspension, observation.getObservedSuspensionRevision());
        assertEquals(samples, captures.get());
        assertEquals(-1, observation.getActiveOuterTickId());
    }
}
