package me.youngjibbit.core.inventory;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class InventoryPacingTest {
    @Test public void repeatedPollsCannotResampleOrMoveTheDeadline() {
        AtomicLong now = new AtomicLong(); InventoryPacing pacing = new InventoryPacing(now::get, 19);
        assertFalse(pacing.ready("Uniform", 100, 200, .5)); double sampled = pacing.latchedDelayMs();
        for (int i = 0; i < 1000; i++) assertFalse(pacing.ready("Waves", 900, 1200, 2));
        assertEquals(sampled, pacing.latchedDelayMs(), 0);
        long deadline = Math.round(sampled * 1_000_000);
        now.set(deadline - 1); assertFalse(pacing.ready("Uniform", 100, 200, .5));
        now.incrementAndGet(); assertTrue(pacing.ready("Uniform", 100, 200, .5));
    }
    @Test public void everyProfileIsBoundedReproducibleAndIndependentOfPollFrequency() {
        for (String profile : new String[]{"Legacy", "Uniform", "Smooth", "Waves"}) {
            AtomicLong now = new AtomicLong(); InventoryPacing a = new InventoryPacing(now::get, 200), b = new InventoryPacing(now::get, 200);
            double previous = -1; boolean varied = false;
            for (int step = 0; step < 400; step++) {
                a.ready(profile, 80, 260, .8); b.ready(profile, 80, 260, .8);
                for (int extra = 0; extra < 23; extra++) a.ready(profile, 80, 260, .8);
                assertEquals(a.latchedDelayMs(), b.latchedDelayMs(), 0);
                assertTrue(a.latchedDelayMs() >= 80 && a.latchedDelayMs() <= 260);
                if (previous >= 0 && previous != a.latchedDelayMs()) varied = true;
                previous = a.latchedDelayMs(); now.addAndGet(1_000_000_000L); a.reset(); b.reset();
            }
            assertTrue(profile, varied);
        }
    }
    @Test public void resetStartsOneNewIntervalAndNeverAccumulatesCatchupActions() {
        AtomicLong now = new AtomicLong(); InventoryPacing pacing = new InventoryPacing(now::get, 1);
        assertFalse(pacing.ready("Smooth", 200, 200, .5));
        now.set(20_000_000_000L); assertTrue(pacing.ready("Smooth", 200, 200, .5));
        pacing.reset(); assertFalse(pacing.ready("Smooth", 200, 200, .5));
        now.addAndGet(199_000_000); assertFalse(pacing.ready("Smooth", 200, 200, .5));
        now.addAndGet(1_000_000); assertTrue(pacing.ready("Smooth", 200, 200, .5));
    }
    @Test public void fixedZeroReversedAndInvalidLimitsRemainFiniteAndSafe() {
        AtomicLong now = new AtomicLong(); InventoryPacing pacing = new InventoryPacing(now::get, 4);
        assertTrue(pacing.ready("Uniform", 0, 0, .5)); assertEquals(0, pacing.latchedDelayMs(), 0);
        pacing.reset(); pacing.ready("Smooth", 350, 100, .5);
        assertTrue(pacing.latchedDelayMs() >= 100 && pacing.latchedDelayMs() <= 350);
        pacing.reset(); assertTrue(pacing.ready("Waves", Double.NaN, Double.POSITIVE_INFINITY, Double.NaN));
        assertEquals(0, pacing.latchedDelayMs(), 0);
    }
    @Test public void changingRateAndBoundsHasARealEffectOnTheNextInterval() {
        AtomicLong now = new AtomicLong(); InventoryPacing slow = new InventoryPacing(now::get, 7), fast = new InventoryPacing(now::get, 7);
        slow.ready("Smooth", 100, 200, .1); fast.ready("Smooth", 100, 200, 2);
        assertEquals(slow.latchedDelayMs(), fast.latchedDelayMs(), 0);
        now.addAndGet(1_000_000_000); slow.reset(); fast.reset();
        slow.ready("Smooth", 100, 200, .1); fast.ready("Smooth", 100, 200, 2);
        assertNotEquals(slow.latchedDelayMs(), fast.latchedDelayMs(), .00001);
        slow.reset(); slow.ready("Uniform", 700, 700, .5); assertEquals(700, slow.latchedDelayMs(), 0);
    }
}
