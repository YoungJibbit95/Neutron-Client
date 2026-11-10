package me.youngjibbit.core.combat;

import org.junit.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import static org.junit.Assert.*;

public class AttackPreparationBudgetTest {
    @Test public void preparationHintsDoNotDrawRandomnessReserveBudgetOrAccumulateClicks() {
        AtomicLong now = new AtomicLong(1000); AtomicInteger draws = new AtomicInteger();
        AttackScheduler scheduler = new AttackScheduler(now::get, (a,b) -> 10);
        LongSupplier delay = () -> { draws.incrementAndGet(); return 100; };
        scheduler.beginTick();
        for (int i = 0; i < 20; i++) assertTrue(scheduler.isClickDue(1));
        assertEquals(0, draws.get());
        assertTrue(scheduler.tryClick(1, true, delay, () -> true));
        int sampled = draws.get(); assertFalse(scheduler.isClickDue(1));
        scheduler.beginTick(); now.addAndGet(50);
        for (int i = 0; i < 20; i++) assertFalse(scheduler.isClickDue(2));
        assertEquals(sampled, draws.get());
        now.addAndGet(10000); assertTrue(scheduler.isClickDue(2));
        assertFalse(scheduler.tryClick(2, true, delay, () -> false));
        assertFalse(scheduler.isClickDue(3)); scheduler.reset(); assertFalse(scheduler.isClickDue(3));
    }
}
