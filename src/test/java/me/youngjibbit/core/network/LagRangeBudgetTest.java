package me.youngjibbit.core.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class LagRangeBudgetTest {
    @Test public void noCreditComesFromElapsedTimeAndEveryExtraCostsAnActualWithheldStep() {
        LagRangeBudget budget = new LagRangeBudget();
        assertEquals(0, budget.available(5_000_000_000L, 0));
        for (int tick = 0; tick < 3; tick++) assertTrue(budget.withhold(tick * 50_000_000L, 1));
        assertFalse(budget.withhold(150_000_000L, 1));
        assertEquals(2, budget.available(150_000_000L, 1));
        assertTrue(budget.spend(150_000_000L, 1)); assertTrue(budget.spend(150_000_000L, 1));
        assertFalse(budget.spend(150_000_000L, 1));
    }
    @Test public void stalledFramesRepeatedCallsAndExpiryCannotAccumulateUnboundedCredit() {
        LagRangeBudget budget = new LagRangeBudget();
        assertTrue(budget.withhold(0, 0)); assertFalse(budget.withhold(0, 0));
        assertEquals(1, budget.available(0, 0));
        assertEquals(0, budget.available(2_000_000_001L, 0));
        for (int tick = 0; tick < 40; tick++) budget.withhold(3_000_000_000L + tick * 50_000_000L, 0);
        assertEquals(10, budget.available(4_950_000_000L, 0));
        budget.reset(); assertFalse(budget.spend(4_950_000_000L, 0));
    }
}
