package me.youngjibbit.core.combat;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.LongSupplier;
import static org.junit.Assert.*;

public class ClickRandomizerTest {
    @Test public void allModesHaveFinitePositiveOrderedBoundsAndFixedCps() {
        AtomicLong clock=new AtomicLong();
        ClickRandomizer random=new ClickRandomizer(new Random(7),new Random(8),clock::get);
        for(String mode:ClickRandomizer.modes())for(double[] range:new double[][]{{9,12},{12,9},{1,20},{11,11},{Double.NaN,Double.POSITIVE_INFINITY}})
            for(boolean extra:new boolean[]{false,true})for(int i=0;i<1000;i++) {
                clock.addAndGet(17);
                ClickRandomizer.Settings settings=new ClickRandomizer.Settings(range[0],range[1],mode,extra,3);
                ClickRandomizer.Sample sample=random.sample(settings);
                assertTrue(mode,Double.isFinite(sample.cps)&&sample.cps>=settings.min&&sample.cps<=settings.max);
                assertTrue(sample.delayMillis>=50&&sample.delayMillis<=1003);
                if(settings.min==settings.max)assertEquals(settings.min,sample.cps,0);
                if(!extra)assertEquals((long)(1000/sample.cps),sample.delayMillis);
            }
    }
    @Test public void independentSamplersAndJitterSemanticsAreDeterministic() {
        AtomicLong clock=new AtomicLong();
        ClickRandomizer one=new ClickRandomizer(new Random(1),new Random(2),clock::get);
        ClickRandomizer two=new ClickRandomizer(new Random(1),new Random(2),clock::get);
        for(String mode:ClickRandomizer.modes())for(int i=0;i<20;i++) {
            clock.addAndGet(50);
            ClickRandomizer.Settings settings=new ClickRandomizer.Settings(9,12,mode,false,3);
            assertEquals(one.sample(settings).cps,two.sample(settings).cps,0);
        }
        for(int i=0;i<100;i++) {
            ClickRandomizer.Sample sample=one.sample(new ClickRandomizer.Settings(10,10,"Pi",true,3));
            assertEquals(10,sample.cps,0);assertTrue(sample.delayMillis>=100&&sample.delayMillis<=102);
        }
    }
    @Test public void pendingCadenceIsSampledOnceAndCancellationCannotBurstOrEraseGlobalBudget() {
        AtomicLong clock=new AtomicLong();AtomicInteger samples=new AtomicInteger(),actions=new AtomicInteger();
        AttackScheduler scheduler=new AttackScheduler(clock::get,(min,max)->10);
        LongSupplier interval=()->{samples.incrementAndGet();return 100;};
        scheduler.beginTick();
        assertFalse(scheduler.tryAttack(1,false,interval,()->true));assertEquals(0,samples.get());
        assertFalse(scheduler.tryAttack(2,true,interval,()->{actions.incrementAndGet();return false;}));
        assertEquals(1,samples.get());
        assertFalse(scheduler.tryAttack(3,true,interval,()->true));
        scheduler.beginTick();
        assertTrue(scheduler.tryAttack(4,true,interval,()->{actions.incrementAndGet();return true;}));assertEquals(2,samples.get());
        for(int t=1;t<100;t++) { clock.set(t);scheduler.beginTick();assertFalse(scheduler.tryAttack(4+t,true,interval,()->true)); }
        assertEquals(2,samples.get());clock.set(100);scheduler.beginTick();
        assertTrue(scheduler.tryAttack(104,true,interval,()->true));assertEquals(3,samples.get());
        scheduler.reset();assertFalse(scheduler.tryAttack(105,true,interval,()->true));
        clock.set(10000);scheduler.beginTick();assertTrue(scheduler.tryAttack(106,true,interval,()->true));
        assertFalse(scheduler.tryAttack(107,true,interval,()->true));assertEquals(2,actions.get());
    }
    @Test public void optionalBlockFrequencyUsesTheExistingOwnerAndKeepsManualUseAndFakeUntouched() {
        for(AutoblockController.Mode mode:AutoblockController.Mode.values())for(int frequency=0;frequency<=10;frequency++) {
            AtomicInteger tick=new AtomicInteger(1);AtomicBoolean manual=new AtomicBoolean();List<Boolean> packets=new ArrayList<>();
            AutoblockController controller=new AutoblockController(()->true,manual::get,tick::get,packets::add);
            int blocks=0;
            for(int i=1;i<=20;i++) {
                tick.set(i);controller.beforeAttack();controller.update(true,mode,false,true,true,frequency);
                if(mode!=AutoblockController.Mode.FAKE && ClickRandomizer.blockAtTick(i,frequency))blocks++;
            }
            assertEquals(blocks,packets.stream().filter(Boolean::booleanValue).count());
            controller.release();int size=packets.size();manual.set(true);
            controller.update(true,mode,false,true,true,10);assertEquals(size,packets.size());
            controller.reset();assertEquals(size,packets.size());
        }
    }
}
