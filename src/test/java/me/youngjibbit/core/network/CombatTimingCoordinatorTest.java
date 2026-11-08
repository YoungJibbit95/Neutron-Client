package me.youngjibbit.core.network;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class CombatTimingCoordinatorTest {
    @Test public void onlyOneWindowRunsAndItsGapAlsoBlocksOtherModules() {
        AtomicLong now = new AtomicLong();
        CombatTimingCoordinator timing = new CombatTimingCoordinator(now::get, 17);
        Object back = new Object(), lag = new Object(), tick = new Object();
        assertTrue(timing.acquire(back));
        assertFalse(timing.acquire(lag));
        timing.release(tick, 0);
        assertTrue(timing.owns(back));
        timing.release(back, 20_000);
        now.set(19_999_000_000L);
        assertFalse(timing.acquire(lag));
        assertFalse(timing.acquire(tick));
        now.set(20_000_000_000L);
        assertTrue(timing.acquire(tick));
        assertFalse(timing.acquire(back));
    }

    @Test public void expiredOwnerCannotReleaseANewerWindowAndSceneResetClearsTheGap() {
        AtomicLong now = new AtomicLong();
        CombatTimingCoordinator timing = new CombatTimingCoordinator(now::get, 17);
        Object first = new Object(), next = new Object();
        assertTrue(timing.acquire(first));
        now.set(250_000_000L);
        assertTrue(timing.acquire(next));
        timing.release(first, 20_000);
        assertTrue(timing.owns(next));
        timing.release(next, 20_000);
        timing.reset();
        assertTrue(timing.acquire(first));
    }

    @Test public void seededNoiseVariesWithinConfiguredEndpointsAndNeverChangesZero() {
        AtomicLong now = new AtomicLong();
        CombatTimingCoordinator a = new CombatTimingCoordinator(now::get, 17);
        CombatTimingCoordinator b = new CombatTimingCoordinator(now::get, 17);
        Set<Integer> samples = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            now.set(i * 50_000_000L);
            int value = a.sampleMillis(21, 91);
            assertEquals(value, b.sampleMillis(21, 91));
            assertTrue(value >= 21 && value <= 91);
            samples.add(value);
        }
        assertTrue(samples.size() > 10);
        assertEquals(0, a.cooldown(0));
        assertEquals(2, a.sampleMillis(2, 2));
        for (int i = 0; i < 20; i++) {
            long gap = a.cooldown(20_000);
            assertTrue(gap >= 18_000 && gap <= 20_000);
        }
    }
}
