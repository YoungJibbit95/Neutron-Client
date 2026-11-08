package me.youngjibbit.core.combat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

/** Committed source contract; not a copy of the inaccessible local untracked test. */
public class ContinuousClickSchedulerTest {
    private static final class Fixture {
        final AtomicLong now=new AtomicLong();
        final AttackScheduler scheduler=new AttackScheduler(now::get,(min,max)->10);
        final AtomicInteger samples=new AtomicInteger(),actions=new AtomicInteger();
        final LongSupplier next=()->{samples.incrementAndGet();return 100L;};
        boolean click(long motion,boolean eligible,boolean attacked) {
            return scheduler.tryClick(motion,eligible,next,()->{
                actions.incrementAndGet();return attacked;
            });
        }
    }
    @Test public void failedControllerStillConsumesOneFullCpsInterval() {
        Fixture f=new Fixture();f.scheduler.beginTick();
        assertFalse(f.click(1,true,false));
        assertEquals(1,f.actions.get());assertEquals(2,f.samples.get());
        assertFalse(f.click(1,true,true));assertFalse(f.click(2,true,true));
        f.scheduler.beginTick();f.now.set(50);
        assertFalse(f.click(3,true,true));assertEquals(1,f.actions.get());
        f.scheduler.beginTick();f.now.set(100);
        assertTrue(f.click(4,true,true));assertEquals(2,f.actions.get());
        assertEquals(3,f.samples.get());
    }
    @Test public void invalidHitDoesNotClaimAClickOrSampleCpsAndSameMotionCannotReenter() {
        Fixture f=new Fixture();f.scheduler.beginTick();
        assertFalse(f.click(1,false,true));assertFalse(f.click(1,true,true));
        assertEquals(0,f.samples.get());assertEquals(0,f.actions.get());
        assertTrue(f.click(2,true,true));assertEquals(1,f.actions.get());
    }
    @Test public void controllerAcceptanceIsStillTheOnlyPositiveAttackResult() {
        Fixture f=new Fixture();f.scheduler.beginTick();
        assertFalse(f.scheduler.tryClick(1,true,f.next,()->{
            assertTrue(f.scheduler.beginControllerAttack(true));
            try {assertFalse(f.scheduler.beginControllerAttack(true));return false;}
            finally {f.scheduler.endControllerAttack();}
        }));
        assertFalse(f.scheduler.allowControllerAttack(true));
        f.scheduler.beginTick();f.now.set(100);
        assertTrue(f.scheduler.tryClick(2,true,f.next,()->{
            assertTrue(f.scheduler.beginControllerAttack(true));
            try {f.scheduler.recordControllerAttack();return true;}
            finally {f.scheduler.endControllerAttack();}
        }));
        assertFalse(f.scheduler.allowControllerAttack(true));
    }
    @Test public void manualControllerAndAuraUseOneSharedTickAndCpsBudget() {
        Fixture f=new Fixture();f.scheduler.beginTick();
        assertTrue(f.scheduler.beginControllerAttack(true));
        f.scheduler.recordControllerAttack();f.scheduler.endControllerAttack();
        assertFalse(f.click(1,true,true));assertEquals(0,f.actions.get());
        f.scheduler.beginTick();f.now.set(50);assertFalse(f.click(2,true,true));
        f.scheduler.beginTick();f.now.set(100);assertTrue(f.click(3,true,true));
        assertFalse(f.scheduler.tryAttack(4,true,()->100L,()->true));
    }
    @Test public void disableWorldResetAndTargetSwapNeverBypassBudget() {
        Fixture f=new Fixture();f.scheduler.beginTick();assertTrue(f.click(1,true,true));
        f.scheduler.reset();assertFalse(f.click(1,true,true));
        assertFalse(f.click(2,true,true));
        f.scheduler.beginTick();f.now.set(75);assertFalse(f.click(3,true,true));
        f.scheduler.reset();f.scheduler.beginTick();f.now.set(100);
        assertTrue(f.click(4,true,true));assertEquals(2,f.actions.get());
    }
    @Test public void longPauseDoesNotReplayMissedClicksAndClockRollbackBlocks() {
        Fixture f=new Fixture();f.scheduler.beginTick();assertFalse(f.click(1,true,false));
        f.scheduler.beginTick();f.now.set(1000);
        assertFalse(f.click(2,true,false));assertEquals(2,f.actions.get());
        assertFalse(f.click(3,true,true));
        f.scheduler.beginTick();f.now.set(1001);assertFalse(f.click(4,true,true));
        f.scheduler.beginTick();f.now.set(1100);assertTrue(f.click(5,true,true));
        f.scheduler.beginTick();f.now.set(1050);assertFalse(f.click(6,true,true));
        assertEquals(3,f.actions.get());
    }
    @Test public void rejectedCallbackOrZeroDelayCannotLeaveAutomaticOwnershipStuck() {
        AtomicLong now=new AtomicLong();
        AttackScheduler s=new AttackScheduler(now::get,(min,max)->10);
        s.beginTick();
        try {s.tryClick(1,true,()->0,()->{throw new IllegalStateException("cancel");});
             fail("expected exception");
        } catch(IllegalStateException expected) { }
        assertTrue(s.hasAttackThisTick());assertFalse(s.allowControllerAttack(true));
        s.beginTick();assertFalse(s.tryClick(2,true,()->0,()->true));
        now.set(1);s.beginTick();assertTrue(s.tryClick(3,true,()->0,()->true));
    }
    @Test public void nestedAttackOrSecondClickCannotStealCallbackBudget() {
        Fixture f=new Fixture();f.scheduler.beginTick();
        assertTrue(f.scheduler.tryClick(1,true,f.next,()->{
            f.actions.incrementAndGet();
            assertFalse(f.scheduler.tryClick(2,true,f.next,()->true));
            assertFalse(f.scheduler.tryAttack(3,true,()->100L,()->true));
            assertTrue(f.scheduler.beginControllerAttack(true));
            try {f.scheduler.recordControllerAttack();return true;}
            finally {f.scheduler.endControllerAttack();}
        }));
        assertEquals(1,f.actions.get());
        f.scheduler.beginTick();f.now.set(50);
        assertFalse(f.scheduler.tryAttack(4,true,()->100L,()->true));
        f.scheduler.beginTick();f.now.set(100);
        assertTrue(f.scheduler.tryAttack(5,true,()->100L,()->true));
    }
}
