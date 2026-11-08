package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.ClickRandomizer;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class BridgePlacementCadenceTest {
    private static final long SECOND = 1_000_000_000L;

    private static BridgePlacementCadence.Scope scope(long epoch, long generation) {
        return new BridgePlacementCadence.Scope(new Object(), new Object(), new Object(),
                new Object(), epoch, generation);
    }

    private static BridgePlacementCadence.Scope next(BridgePlacementCadence.Scope old) {
        return new BridgePlacementCadence.Scope(old.owner, old.world, old.player,
                old.connection, old.epoch, old.generation + 1);
    }

    private static BridgePlacementCadence.Settings fixed(double cps) {
        return new BridgePlacementCadence.Settings(cps, cps, BridgePlacementCadence.Profile.BASIC,
                BridgePlacementCadence.Interpolation.SMOOTH, 1);
    }

    @Test public void reversedBoundsAndNonfiniteSettingsStayInsideExistingClickBounds() {
        BridgePlacementCadence.Settings settings = new BridgePlacementCadence.Settings(
                40, -3, null, null, Double.NaN);
        assertEquals(1, settings.minCps, 0);
        assertEquals(20, settings.maxCps, 0);
        assertEquals(0, settings.variation, 0);
        BridgePlacementCadence.Settings swapped = new BridgePlacementCadence.Settings(
                15, 5, BridgePlacementCadence.Profile.PERLIN,
                BridgePlacementCadence.Interpolation.STABLE, 4);
        assertEquals(5, swapped.minCps, 0);
        assertEquals(15, swapped.maxCps, 0);
        assertEquals(1, swapped.variation, 0);
        BridgePlacementCadence.Settings invalid = new BridgePlacementCadence.Settings(
                Double.NaN, Double.POSITIVE_INFINITY, null, null, -1);
        assertEquals(1, invalid.minCps, 0);
        assertEquals(1, invalid.maxCps, 0);
    }

    @Test public void onePendingSampleSurvivesPollingUntilItsExactDeadline() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(3);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        BridgePlacementCadence.Settings settings = fixed(10);
        assertTrue(cadence.start(scope, settings, 0));
        for (long now = 0; now < 100_000_000L; now += 10_000L)
            assertFalse(cadence.isDue(scope, settings, now));
        assertEquals(1, cadence.getSampleCount());
        assertTrue(cadence.isDue(scope, settings, 100_000_000L));
        BridgePlacementCadence.Attempt attempt = cadence.tryConsume(scope, settings, 1, 100_000_000L, true);
        assertNotNull(attempt);
        assertEquals(100_000_000L, attempt.intervalNanos);
        assertEquals(10, attempt.plannedCps, 0);
        assertEquals(2, cadence.getSampleCount());
        assertEquals(1, cadence.getAttemptCount());
    }

    @Test public void overdueWindowsNeverCreateCatchUpAttempts() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(4);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        BridgePlacementCadence.Settings settings = fixed(10);
        cadence.start(scope, settings, 0);
        assertNotNull(cadence.tryConsume(scope, settings, 40, 10 * SECOND, true));
        assertNull(cadence.tryConsume(scope, settings, 41, 10 * SECOND, true));
        assertNull(cadence.tryConsume(scope, settings, 42, 10 * SECOND + 99_999_999L, true));
        assertNotNull(cadence.tryConsume(scope, settings, 43, 10 * SECOND + 100_000_000L, true));
        assertEquals(2, cadence.getAttemptCount());
        assertEquals(3, cadence.getSampleCount());
    }

    @Test public void rejectedOrEarlyOpportunityCannotBeReusedAfterItsGateChanges() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(5);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        BridgePlacementCadence.Settings settings = fixed(20);
        cadence.start(scope, settings, 0);
        assertNull(cadence.tryConsume(scope, settings, 1, 25_000_000L, true));
        assertNull(cadence.tryConsume(scope, settings, 1, 50_000_000L, true));
        assertNull(cadence.tryConsume(scope, settings, 2, 50_000_000L, false));
        assertNull(cadence.tryConsume(scope, settings, 2, 50_000_000L, true));
        assertEquals(1, cadence.getSampleCount());
        assertNotNull(cadence.tryConsume(scope, settings, 3, 50_000_000L, true));
        assertNull(cadence.tryConsume(scope, settings, 3, SECOND, true));
        assertEquals(1, cadence.getAttemptCount());
    }

    @Test public void controllerFailureDoesNotReturnAnAttemptToTheClock() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(6);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        BridgePlacementCadence.Settings settings = fixed(10);
        cadence.start(scope, settings, 0);
        int controllerCalls = 0;
        for (int callback = 0; callback < 12; callback++) {
            BridgePlacementCadence.Attempt attempt = cadence.tryConsume(scope, settings, 1, SECOND, true);
            if (attempt != null) {
                controllerCalls++;
                // The host's local controller rejects this attempt; no success is reported.
            }
        }
        assertEquals(1, controllerCalls);
        assertEquals(1, cadence.getAttemptCount());
        assertNull(cadence.tryConsume(scope, settings, 2, SECOND + 1, true));
    }

    @Test public void settingsSnapshotsDoNotRestartCadenceAndChangesRequireNewGeneration() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(7);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        cadence.start(scope, fixed(10), 0);
        assertFalse(cadence.start(scope, fixed(10), 90_000_000L));
        assertNotNull(cadence.tryConsume(scope, fixed(10), 1, 100_000_000L, true));
        assertNull(cadence.tryConsume(scope, fixed(20), 2, 150_000_000L, true));
        assertFalse(cadence.isActive());
        assertFalse(cadence.start(scope, fixed(20), 150_000_000L));
        BridgePlacementCadence.Scope replacement = next(scope);
        assertTrue(cadence.start(replacement, fixed(20), 150_000_000L));
        assertFalse(cadence.isDue(replacement, fixed(20), 199_999_999L));
        assertTrue(cadence.isDue(replacement, fixed(20), 200_000_000L));
    }

    @Test public void clockRollbackRevokesOnlyTheMatchingCurrentSession() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(8);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        BridgePlacementCadence.Settings settings = fixed(10);
        assertTrue(cadence.start(scope, settings, -2 * SECOND));
        assertNotNull(cadence.tryConsume(scope, settings, 1, -SECOND, true));
        assertNull(cadence.tryConsume(scope, settings, 2, -SECOND - 1, true));
        assertFalse(cadence.isActive());
        BridgePlacementCadence.Scope replacement = next(scope);
        assertTrue(cadence.start(replacement, settings, 0));
        assertNull(cadence.tryConsume(scope, fixed(20), 999, -SECOND, true));
        assertTrue(cadence.isActive());
        assertNotNull(cadence.tryConsume(replacement, settings, 1, SECOND, true));
    }

    @Test public void reconnectWithLowerEpochRejectsOldCallbacksAndForeignOwners() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(9);
        BridgePlacementCadence.Scope old = scope(7, 4);
        BridgePlacementCadence.Settings settings = fixed(10);
        assertTrue(cadence.start(old, settings, 0));
        cadence.reset();
        assertFalse(cadence.start(old, settings, SECOND));
        BridgePlacementCadence.Scope current = new BridgePlacementCadence.Scope(old.owner,
                new Object(), new Object(), new Object(), 0, 5);
        assertTrue(cadence.start(current, settings, SECOND));
        assertNull(cadence.tryConsume(old, settings, 999, 100 * SECOND, true));
        assertFalse(cadence.start(new BridgePlacementCadence.Scope(new Object(), current.world,
                current.player, current.connection, 100, 100), settings, SECOND));
        assertNotNull(cadence.tryConsume(current, settings, 1, SECOND + 100_000_000L, true));
        BridgePlacementCadence.Scope higherEpoch = new BridgePlacementCadence.Scope(current.owner,
                current.world, current.player, current.connection, 5, 6);
        assertTrue(cadence.start(higherEpoch, settings, 2 * SECOND));
        BridgePlacementCadence.Scope olderEpoch = new BridgePlacementCadence.Scope(current.owner,
                current.world, current.player, current.connection, 4, 7);
        assertFalse(cadence.start(olderEpoch, settings, 2 * SECOND));
        assertTrue(cadence.isActive());
    }

    @Test public void foreignThreadCannotConsumeResetOrReplaceTheLiveClock() throws Exception {
        BridgePlacementCadence cadence = new BridgePlacementCadence(10);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        BridgePlacementCadence.Settings settings = fixed(10);
        cadence.start(scope, settings, 0);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try {
                assertNull(cadence.tryConsume(scope, settings, 1, SECOND, true));
                assertFalse(cadence.start(next(scope), settings, SECOND));
                cadence.reset();
            } catch (Throwable thrown) { failure.set(thrown); }
        });
        other.start(); other.join();
        assertNull(failure.get());
        assertTrue(cadence.isActive());
        assertEquals(1, cadence.getSampleCount());
        assertNotNull(cadence.tryConsume(scope, settings, 1, SECOND, true));
    }

    @Test public void staleScopedResetCannotDisposeReplacementAndCurrentResetKeepsItsTombstone() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(77);
        BridgePlacementCadence.Scope old = scope(7, 4);
        BridgePlacementCadence.Settings settings = fixed(10);
        assertTrue(cadence.start(old, settings, 0));
        BridgePlacementCadence.Scope replacement = new BridgePlacementCadence.Scope(old.owner,
                old.world, old.player, new Object(), 0, 5);
        assertTrue(cadence.start(replacement, settings, SECOND));
        long interval = cadence.getPendingIntervalNanos();
        double cps = cadence.getPendingCps();
        long samples = cadence.getSampleCount(), attempts = cadence.getAttemptCount();

        assertFalse("a returning old callback cannot reset the new session", cadence.reset(old));
        assertTrue(cadence.isActive());
        assertEquals(interval, cadence.getPendingIntervalNanos());
        assertEquals(cps, cadence.getPendingCps(), 0);
        assertEquals(samples, cadence.getSampleCount());
        assertEquals(attempts, cadence.getAttemptCount());
        assertNotNull("the original replacement deadline is still usable",
                cadence.tryConsume(replacement, settings, 1, SECOND + interval, true));

        assertTrue(cadence.reset(replacement));
        assertFalse(cadence.isActive());
        assertEquals(0, cadence.getPendingIntervalNanos());
        assertEquals(0, cadence.getPendingCps(), 0);
        assertFalse(cadence.reset(replacement));
        assertFalse("reset must not reopen its retired generation",
                cadence.start(replacement, settings, 2 * SECOND));
        assertTrue(cadence.start(next(replacement), settings, 2 * SECOND));
    }

    @Test public void renderPollingAtFourFrameRatesHasIdenticalPhysicalOpportunityTraces() {
        for (BridgePlacementCadence.Profile profile : BridgePlacementCadence.Profile.values()) {
            for (BridgePlacementCadence.Interpolation interpolation : BridgePlacementCadence.Interpolation.values()) {
                List<Long> expected = trace(20, profile, interpolation);
                for (int fps : new int[]{60, 144, 240})
                    assertEquals(profile + "/" + interpolation + "/" + fps,
                            expected, trace(fps, profile, interpolation));
            }
        }
    }

    private static List<Long> trace(int fps, BridgePlacementCadence.Profile profile,
                                    BridgePlacementCadence.Interpolation interpolation) {
        BridgePlacementCadence cadence = new BridgePlacementCadence(111);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        BridgePlacementCadence.Settings settings = new BridgePlacementCadence.Settings(5, 19,
                profile, interpolation, .8);
        cadence.start(scope, settings, 0);
        List<Long> trace = new ArrayList<>();
        long frame = 1;
        for (int tick = 1; tick <= 400; tick++) {
            long now = tick * 50_000_000L;
            while (frame * SECOND / fps <= now) {
                cadence.isDue(scope, settings, frame * SECOND / fps);
                frame++;
            }
            BridgePlacementCadence.Attempt attempt = cadence.tryConsume(scope, settings, tick, now, true);
            if (attempt != null) {
                trace.add(attempt.consumedAtNanos);
                trace.add(Double.doubleToLongBits(attempt.plannedCps));
                trace.add(attempt.intervalNanos);
            }
        }
        assertTrue(trace.size() > 100);
        assertEquals(cadence.getAttemptCount() + 1, cadence.getSampleCount());
        return trace;
    }

    @Test public void variationZeroAndAllProfilesKeepCpsAndIntervalsBounded() {
        for (BridgePlacementCadence.Profile profile : BridgePlacementCadence.Profile.values()) {
            for (BridgePlacementCadence.Interpolation interpolation : BridgePlacementCadence.Interpolation.values()) {
                for (double variation : new double[]{0, 1}) {
                    BridgePlacementCadence cadence = new BridgePlacementCadence(12);
                    BridgePlacementCadence.Scope scope = scope(0, 1);
                    BridgePlacementCadence.Settings settings = new BridgePlacementCadence.Settings(17, 3,
                            profile, interpolation, variation);
                    cadence.start(scope, settings, 0);
                    for (int i = 1; i <= 300; i++) {
                        BridgePlacementCadence.Attempt attempt = cadence.tryConsume(scope, settings, i,
                                i * SECOND, true);
                        assertNotNull(attempt);
                        assertTrue(attempt.plannedCps >= 3 && attempt.plannedCps <= 17);
                        assertTrue(attempt.intervalNanos >= (long) Math.ceil(SECOND / 17.0));
                        assertTrue(attempt.intervalNanos <= (long) Math.ceil(SECOND / 3.0));
                        if (variation == 0) assertEquals(10, attempt.plannedCps, 0);
                    }
                }
            }
        }
    }

    @Test public void stableInterpolationMovesLessThanSmoothAndDynamicAtEqualElapsedTime() {
        double[] changes = new double[3];
        int index = 0;
        for (BridgePlacementCadence.Interpolation interpolation : BridgePlacementCadence.Interpolation.values()) {
            BridgePlacementCadence cadence = new BridgePlacementCadence(13);
            BridgePlacementCadence.Scope scope = scope(0, 1);
            BridgePlacementCadence.Settings settings = new BridgePlacementCadence.Settings(2, 20,
                    BridgePlacementCadence.Profile.BASIC, interpolation, 1);
            cadence.start(scope, settings, 0);
            double before = cadence.getPendingCps();
            assertNotNull(cadence.tryConsume(scope, settings, 1, SECOND, true));
            changes[index++] = Math.abs(cadence.getPendingCps() - before);
        }
        assertTrue(changes[0] > 0);
        assertTrue(changes[0] < changes[1]);
        assertTrue(changes[1] < changes[2]);
    }

    @Test public void unrelatedLeftClickSamplingCannotPerturbThePlacementSession() {
        BridgePlacementCadence one = new BridgePlacementCadence(14);
        BridgePlacementCadence two = new BridgePlacementCadence(14);
        BridgePlacementCadence.Scope first = scope(0, 1), second = scope(0, 1);
        BridgePlacementCadence.Settings settings = new BridgePlacementCadence.Settings(4, 18,
                BridgePlacementCadence.Profile.SMOOTHSTEP, BridgePlacementCadence.Interpolation.SMOOTH, 1);
        ClickRandomizer left = new ClickRandomizer(new Random(991), new Random(992), () -> 1234L);
        one.start(first, settings, 0); two.start(second, settings, 0);
        for (int i = 1; i <= 100; i++) {
            BridgePlacementCadence.Attempt a = one.tryConsume(first, settings, i, i * SECOND, true);
            for (int clicks = 0; clicks < 30; clicks++)
                left.sample(new ClickRandomizer.Settings(1, 20, "Basic", true, 3));
            BridgePlacementCadence.Attempt b = two.tryConsume(second, settings, i, i * SECOND, true);
            assertNotNull(a); assertNotNull(b);
            assertEquals(a.plannedCps, b.plannedCps, 0);
            assertEquals(a.intervalNanos, b.intervalNanos);
        }
    }

    @Test public void pureQueriesAndExtremeMonotonicTimesCannotChangePendingSample() {
        BridgePlacementCadence cadence = new BridgePlacementCadence(15);
        BridgePlacementCadence.Scope scope = scope(0, 1);
        BridgePlacementCadence.Settings settings = fixed(10);
        cadence.start(scope, settings, 0);
        assertTrue(cadence.isDue(scope, settings, Long.MAX_VALUE));
        assertFalse(cadence.isDue(scope, settings, 99_999_999L));
        assertNotNull(cadence.tryConsume(scope, settings, 1, 100_000_000L, true));
        assertEquals(2, cadence.getSampleCount());
        BridgePlacementCadence.Scope replacement = next(scope);
        assertTrue(cadence.start(replacement, settings, Long.MIN_VALUE));
        assertNotNull(cadence.tryConsume(replacement, settings, 1, Long.MAX_VALUE, true));
        assertFalse("overflow cannot create an immediately due next interval",
                cadence.isDue(replacement, settings, Long.MAX_VALUE));
    }
}
