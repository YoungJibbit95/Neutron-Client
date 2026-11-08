package me.youngjibbit.core.combat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Frozen five-argument source/test contract from WAVE07; synthetic timers and
 * callbacks only. The original untracked local test file is never copied.
 */
public class ContinuousClickCallbackContractTest {
    private static final class Fixture {
        final AtomicLong now=new AtomicLong();
        final AttackScheduler scheduler=new AttackScheduler(now::get,(min,max)->10);
        final AtomicInteger swing=new AtomicInteger(),controller=new AtomicInteger(),samples=new AtomicInteger();
        final LongSupplier next=()->{samples.incrementAndGet();return 100;};
        boolean click(long motion, boolean hit, boolean controllerResult) {
            return scheduler.tryClick(motion,hit,next,()->{
                controller.incrementAndGet();return controllerResult;
            },swing::incrementAndGet);
        }
    }

    @Test public void swingsAndHitsShareOneCadenceWithoutBurstsOrMissAuthority() {
        Fixture f=new Fixture();
        f.scheduler.beginTick();
        assertFalse(f.click(1,false,true)); // ray miss: swing, not C02
        assertEquals(1,f.swing.get());
        assertEquals(0,f.controller.get());
        assertEquals(2,f.samples.get());
        assertFalse(f.scheduler.hasAttackThisTick());
        assertFalse(f.scheduler.allowControllerAttack(true));
        assertFalse(f.click(2,true,true)); // same tick
        f.scheduler.beginTick();f.now.set(50);
        assertFalse(f.click(3,true,true));
        assertEquals(1,f.swing.get());
        f.scheduler.beginTick();f.now.set(100);
        assertTrue(f.click(4,true,true));
        assertEquals(2,f.swing.get());assertEquals(1,f.controller.get());
        assertEquals(3,f.samples.get());
        f.scheduler.beginTick();f.now.set(101);
        assertFalse(f.click(5,true,true)); // no post-pause catch-up
        f.scheduler.beginTick();f.now.set(201);
        assertFalse(f.click(6,true,false)); // rejected controller, still a real click
        assertEquals(3,f.swing.get());assertEquals(2,f.controller.get());
        assertEquals(4,f.samples.get());
        f.scheduler.beginTick();f.now.set(202);
        assertFalse(f.click(7,true,true)); // rejected C02 cannot bypass cadence
        f.scheduler.beginTick();f.now.set(302);
        assertTrue(f.click(8,true,true));
        assertEquals(4,f.swing.get());assertEquals(3,f.controller.get());
    }

    @Test public void resetInsideSwingCannotResampleStaleCadenceOrEraseTickBudget() {
        Fixture f=new Fixture();
        f.scheduler.beginTick();
        assertFalse(f.scheduler.tryClick(10,true,f.next,()->{
            f.controller.incrementAndGet();return true;
        },()->{
            f.swing.incrementAndGet();
            f.scheduler.reset(); // e.g. target/world loss during swing callback
            // Reset cannot authorize the same tick/motion or nested automation.
            assertFalse(f.scheduler.tryClick(10,true,f.next,()->true,()->{}));
            assertFalse(f.scheduler.tryClick(11,false,f.next,()->true,()->{}));
            assertFalse(f.scheduler.tryAttack(12,true,f.next,()->true));
            assertFalse(f.scheduler.beginControllerAttack(true));
        }));
        assertEquals(1,f.swing.get());assertEquals(0,f.controller.get());
        assertEquals("only the first initial interval is drawn",1,f.samples.get());
        assertFalse(f.scheduler.hasAttackThisTick());
        assertFalse(f.scheduler.allowControllerAttack(true));
        f.scheduler.beginTick();f.now.set(50);
        assertFalse(f.click(13,true,true));
        assertEquals("reset did not re-sample timing",1,f.samples.get());
        f.scheduler.beginTick();f.now.set(100);
        assertTrue(f.click(14,true,true));
        assertEquals(2,f.samples.get());
    }

    @Test public void resetPreservesManualReservationAndTimestamp() {
        Fixture f=new Fixture();
        f.scheduler.beginTick();
        assertTrue(f.scheduler.beginControllerAttack(true));
        f.scheduler.reset(); // do not release in-flight controller ownership
        assertFalse(f.scheduler.beginControllerAttack(true));
        f.scheduler.recordControllerAttack();
        f.scheduler.endControllerAttack();
        assertTrue(f.scheduler.hasAttackThisTick());
        assertFalse(f.click(1,true,true));
        assertEquals(0,f.swing.get());
        f.scheduler.reset();
        assertFalse(f.click(2,false,true)); // same physical tick still spent
        f.scheduler.beginTick();f.now.set(50);
        assertFalse(f.click(3,true,true)); // accepted manual C02 retains its time
        assertEquals(0,f.swing.get());
        f.scheduler.beginTick();f.now.set(100);
        assertTrue(f.click(4,true,true));
        assertEquals(1,f.swing.get());assertEquals(1,f.controller.get());
    }

    @Test public void thrownSwingStillConsumesClickAndReleasesAutomaticScope() {
        Fixture f=new Fixture();
        f.scheduler.beginTick();
        try {
            f.scheduler.tryClick(1,true,f.next,()->{f.controller.incrementAndGet();return true;},
                    ()->{throw new IllegalStateException("swing cancelled");});
            fail("exception expected");
        } catch(IllegalStateException expected) { }
        assertEquals(0,f.controller.get());
        assertEquals(2,f.samples.get());
        assertFalse(f.scheduler.hasAttackThisTick());
        assertFalse(f.scheduler.allowControllerAttack(true));
        f.scheduler.beginTick();f.now.set(99);
        assertFalse(f.click(2,true,true));
        f.scheduler.beginTick();f.now.set(100);
        assertTrue(f.click(3,true,true));
    }

    @Test public void originalFourArgumentApiPreservesIneligibleNoSampleSemantics() {
        Fixture f=new Fixture();
        f.scheduler.beginTick();
        assertFalse(f.scheduler.tryClick(1,false,f.next,()->{
            f.controller.incrementAndGet();return true;
        }));
        assertEquals(0,f.samples.get());
        assertEquals(0,f.controller.get());
        assertFalse(f.scheduler.tryClick(1,true,f.next,()->true));
        assertTrue(f.scheduler.tryClick(2,true,f.next,()->{
            f.controller.incrementAndGet();return true;
        }));
        assertEquals(1,f.controller.get());
        assertEquals(0,f.swing.get());
    }
}
