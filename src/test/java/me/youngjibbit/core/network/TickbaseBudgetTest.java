package me.youngjibbit.core.network;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.player.TimerManipulationEvent;
import me.youngjibbit.module.impl.exploit.Tickbase;
import me.zero.alpine.listener.*;
import net.minecraft.util.Timer;
import org.junit.Test;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

public class TickbaseBudgetTest {
    private TickbaseBudget ready(AtomicLong now) {
        TickbaseBudget budget=new TickbaseBudget(now::get);budget.configure(5,2,1.35);
        budget.bind(new Object(),new Object());budget.frame(true,1,50,1);return budget;
    }
    @Test public void onlyWithheldLoadedPlayerStepsEarnCreditAndReleaseNeverExceedsThatCredit() {
        AtomicLong now=new AtomicLong();AtomicInteger simulation=new AtomicInteger();TickbaseBudget budget=ready(now);
        for(int i=0;i<50;i++)budget.step(false,()->true,()->{simulation.incrementAndGet();return true;});
        assertEquals(0,budget.getCredit());assertFalse(budget.release());assertEquals(50,simulation.get());
        for(int i=0;i<10;i++)budget.step(true,()->true,()->{simulation.incrementAndGet();return true;});
        assertEquals(5,budget.getCredit());assertEquals(55,simulation.get());assertTrue(budget.release());
        int regular=0,extras=0;
        while(budget.isReleasing()) {
            int before=simulation.get();
            budget.step(true,()->true,()->{simulation.incrementAndGet();return true;});
            int performed=simulation.get()-before;assertTrue(performed==1 || performed==2);
            regular++;extras+=performed-1;assertTrue(regular<20);
        }
        assertEquals(5,extras);assertEquals(0,budget.getCredit());assertEquals(55+regular+5,simulation.get());
    }
    @Test public void pauseLongFrameCatchupForeignTimerWorldChangeAndCancelledStepsDiscardBudget() {
        for(int mode=0;mode<5;mode++) {
            AtomicLong now=new AtomicLong();TickbaseBudget budget=ready(now);
            for(int i=0;i<4;i++)budget.step(true,()->true,()->true);
            assertEquals(2,budget.getCredit());
            if(mode==0)budget.frame(false,1,50,1);
            if(mode==1)budget.frame(true,1,500,1);
            if(mode==2)budget.frame(true,10,50,1);
            if(mode==3)budget.frame(true,1,50,1.2f);
            if(mode==4)budget.bind(new Object(),new Object());
            assertEquals(0,budget.getCredit());assertFalse(budget.release());
        }
        AtomicLong now=new AtomicLong();TickbaseBudget budget=ready(now);
        for(int i=0;i<4;i++)budget.step(true,()->true,()->true);
        assertTrue(budget.release());budget.step(false,()->true,()->false);
        assertFalse(budget.isReleasing());assertEquals(0,budget.getCredit());
        budget=ready(now);budget.step(true,()->true,()->true);now.set(5000000001L);
        assertFalse(budget.release());assertEquals(0,budget.getCredit());
    }
    @Test public void lowestReleaseSettingMakesProgressAndLifecycleAbortDoesNotPerformAnExtraStep() {
        AtomicLong now=new AtomicLong();TickbaseBudget budget=ready(now);budget.configure(2,1,1);
        budget.step(true,()->true,()->true);assertTrue(budget.release());AtomicInteger calls=new AtomicInteger();
        budget.step(false,()->false,()->{calls.incrementAndGet();return true;});
        assertEquals(1,calls.get());assertEquals(0,budget.getCredit());
        budget=ready(now);budget.configure(2,1,1);budget.step(true,()->true,()->true);assertTrue(budget.release());
        budget.step(false,()->true,()->true);assertEquals(1,budget.getCredit());
        budget.step(false,()->true,()->true);assertEquals(0,budget.getCredit());
    }
    @Test public void aContextInvalidatedAfterTheFrameSampleCannotEarnOrReleaseCredit() {
        AtomicLong now=new AtomicLong();TickbaseBudget budget=ready(now);budget.configure(2,1,2);
        AtomicInteger simulation=new AtomicInteger();
        budget.step(true,()->false,()->{simulation.incrementAndGet();return true;});
        assertEquals(0,budget.getCredit());assertFalse(budget.release());assertEquals(1,simulation.get());
    }
    @Test public void enablingTheActualModuleRequiresAFreshFrameSample() throws Exception {
        Tickbase module=new Tickbase();java.lang.reflect.Field field=Tickbase.class.getDeclaredField("budget");field.setAccessible(true);
        TickbaseBudget budget=(TickbaseBudget)field.get(module);budget.frame(true,1,50,1);
        module.setToggled(true);
        try {
            AtomicInteger simulation=new AtomicInteger();budget.step(true,()->true,()->{simulation.incrementAndGet();return true;});
            assertEquals(0,budget.getCredit());assertEquals(1,simulation.get());
            budget.frame(true,1,50,1);budget.step(true,()->true,()->true);assertEquals(1,budget.getCredit());
        }finally {module.setToggled(false);}
    }
    @Test public void changingPlayerOrWorldAfterSamplingRequiresANewFrameBeforeCharging() {
        for(int change=0;change<2;change++) {
            TickbaseBudget budget=new TickbaseBudget(()->0);
            Object player=new Object(),world=new Object();
            budget.bind(player,world);budget.frame(true,1,50,1);
            budget.step(true,()->true,()->true);assertEquals(1,budget.getCredit());
            budget.bind(change==0?new Object():player,change==1?new Object():world);
            AtomicInteger calls=new AtomicInteger();
            budget.step(true,()->true,()->{calls.incrementAndGet();return true;});
            assertEquals(0,budget.getCredit());assertEquals(1,calls.get());assertFalse(budget.release());
            budget.frame(true,1,50,1);budget.step(true,()->true,()->true);assertEquals(1,budget.getCredit());
        }
    }
    @Test public void invalidationInsideAPlayerStepAlsoRequiresAFreshFrameSample() {
        TickbaseBudget budget=ready(new AtomicLong());AtomicBoolean valid=new AtomicBoolean(true);
        budget.step(false,valid::get,()->{valid.set(false);return true;});
        valid.set(true);AtomicInteger calls=new AtomicInteger();
        budget.step(true,valid::get,()->{calls.incrementAndGet();return true;});
        assertEquals(0,budget.getCredit());assertEquals(1,calls.get());
        budget.frame(true,1,50,1);budget.step(true,valid::get,()->true);assertEquals(1,budget.getCredit());
    }
    @Test public void timerClampsCatchupAndRejectsNegativeOrNonfiniteDeltasWithoutOverwritingOwners() {
        AtomicLong now=new AtomicLong();Timer timer=new Timer(20,now::get);
        now.set(50);timer.updateTimer();assertEquals(1,timer.elapsedTicks);
        now.set(100000);timer.updateTimer();assertEquals(10,timer.elapsedTicks);assertEquals(99950,timer.lastFrameDurationMs);
        now.set(99900);timer.updateTimer();assertEquals(0,timer.elapsedTicks);assertTrue(timer.renderPartialTicks>=0);
        timer.timerSpeed=Float.NaN;now.addAndGet(50);timer.updateTimer();assertEquals(1,timer.elapsedTicks);
        assertTrue(Float.isNaN(timer.timerSpeed)); // sampling never steals another owner's setting
        timer.timerSpeed=1.7f;Tickbase module=new Tickbase();module.setToggled(true);module.setToggled(false);
        assertEquals(1.7f,timer.timerSpeed,0);
    }
    public static final class Manipulation implements Subscriber {
        boolean cancel;
        @Subscribe final Listener<TimerManipulationEvent> event=new Listener<>(e->{e.setTime(e.getTime()+5000);if(cancel)e.cancel();});
    }
    @Test public void timerCancellationUsesTheRealSampleAndRawFrameDurationIgnoresReplacement() {
        AtomicLong now=new AtomicLong();Timer timer=new Timer(20,now::get);Manipulation manipulation=new Manipulation();
        Pulsar.BUS.subscribe(manipulation);
        try {now.set(50);timer.updateTimer();assertEquals(10,timer.elapsedTicks);assertEquals(50,timer.lastFrameDurationMs);
            manipulation.cancel=true;now.set(100);timer.updateTimer();assertEquals(0,timer.elapsedTicks);
            now.set(150);timer.updateTimer();assertEquals(1,timer.elapsedTicks);
        }finally {Pulsar.BUS.unsubscribe(manipulation);}
    }
    @Test public void releaseLabelKeepsItsOldPersistentIdAndLookupAlias() {
        Tickbase module=new Tickbase();
        me.youngjibbit.value.Value<?> setting=module.getValueByName("release_speed");
        assertNotNull(setting);assertSame(setting,module.getValueByName("Release Speed"));
        assertEquals("release_speed",setting.getId());assertEquals("Extra steps / 2 ticks",setting.getName());
    }
    @Test public void extraPlayerSimulationSharesOneAutomaticAttackBudget() {
        me.youngjibbit.core.combat.AttackScheduler attacks=new me.youngjibbit.core.combat.AttackScheduler(()->1000,(min,max)->10);
        TickbaseBudget budget=new TickbaseBudget(()->0);budget.configure(1,1,2);budget.bind(new Object(),new Object());budget.frame(true,1,50,1);
        java.util.concurrent.atomic.AtomicInteger steps=new java.util.concurrent.atomic.AtomicInteger(),hits=new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.BooleanSupplier simulation=()->{int sequence=steps.incrementAndGet();attacks.tryAttack(sequence,true,10,10,()->{hits.incrementAndGet();return true;});return true;};
        attacks.beginTick();budget.step(true,()->true,simulation);assertTrue(budget.release());budget.step(false,()->true,simulation);
        assertEquals(2,steps.get());assertEquals(1,hits.get());
    }

    @Test public void unchangedPlayerAndWorldCannotCarryCreditAcrossConnectionsOrAnOldFrameSample() {
        AtomicLong now=new AtomicLong();TickbaseBudget budget=new TickbaseBudget(now::get);
        Object player=new Object(),world=new Object();budget.bind(player,world,new Object());budget.frame(true,1,50,1);
        budget.step(true,()->true,()->true);assertEquals(1,budget.getCredit());
        budget.bind(player,world,new Object());budget.step(true,()->true,()->true);assertEquals(0,budget.getCredit());
        budget.frame(true,1,50,1);budget.step(true,()->true,()->true);assertEquals(1,budget.getCredit());
        now.set(150000001L);assertFalse(budget.release());assertEquals(0,budget.getCredit());
        budget.step(true,()->true,()->true);assertEquals(0,budget.getCredit());
        budget.frame(true,1,50,1);budget.step(true,()->true,()->true);assertEquals(1,budget.getCredit());
    }
    @Test public void aFrameThatExpiresDuringTheOrdinarySimulationCannotAuthorizeAnExtraStep() {
        AtomicLong now=new AtomicLong();TickbaseBudget budget=ready(now);budget.configure(2,1,2);
        budget.step(true,()->true,()->true);assertTrue(budget.release());AtomicInteger calls=new AtomicInteger();
        budget.step(false,()->true,()->{calls.incrementAndGet();now.set(151000000L);return true;});
        assertEquals(1,calls.get());assertEquals(0,budget.getCredit());assertEquals(0,budget.getExtraSteps());
    }

}
