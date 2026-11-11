package me.youngjibbit.core.diagnostics;

import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChunkFrameBudgetTest {
    @After public void restoreDefaults() { ChunkFrameBudget.defaults(); }

    @Test public void callerDeadlineIsNeverExtendedAndExpiredBudgetIsNotRenewed() {
        ChunkFrameBudget.Limits limits = ChunkFrameBudget.current();
        assertEquals(2_000_000L, limits.deadline(101_000_000L, 1_000_000L));
        assertEquals(1_200_000L, limits.deadline(1_200_000L, 1_000_000L));
        assertEquals(1_000_000L, limits.deadline(900_000L, 1_000_000L));
        assertEquals(0L, limits.deadline(0L, 1_000_000L));
    }

    @Test public void configurationIsAnAtomicPairAndDefaultsAreReversible() {
        ChunkFrameBudget.configure(.25, .25);
        assertEquals(250_000L, ChunkFrameBudget.current().nanoseconds);
        assertEquals(262_144L, ChunkFrameBudget.current().bytes);
        ChunkFrameBudget.configure(8, 32);
        assertEquals(8_000_000L, ChunkFrameBudget.current().nanoseconds);
        assertEquals(33_554_432L, ChunkFrameBudget.current().bytes);
        ChunkFrameBudget.defaults();
        assertSame(ChunkFrameBudget.DEFAULT, ChunkFrameBudget.current());
    }

    @Test public void invalidSettingsLeaveTheExistingLimitsIntact() {
        for (double invalid : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -.1, 100}) {
            try { ChunkFrameBudget.configure(invalid, 4); fail(); }
            catch (IllegalArgumentException expected) { }
            try { ChunkFrameBudget.configure(1, invalid); fail(); }
            catch (IllegalArgumentException expected) { }
        }
        assertSame(ChunkFrameBudget.DEFAULT, ChunkFrameBudget.current());
    }

    @Test public void monotonicClockWrapUsesRelativeDurations() {
        long now = Long.MAX_VALUE - 100;
        long requested = now + 1_000;
        assertEquals(requested, ChunkFrameBudget.current().deadline(requested, now));
    }
}
