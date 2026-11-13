package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class TargetEspMotionTest {
    private final TargetEspMotion motion = new TargetEspMotion();
    private final Object world = new Object(), target = new Object();
    private long now=1_000_000_000L;
    private long frame;
    private void sample(double x,double z,boolean animate) {
        now+=16_000_000;frame++;motion.sample(world,target,now,frame,x,z,animate,1);
    }
    @Test public void cosmeticInertiaOpposesMovementStaysBoundedAndSettles() {
        sample(0,0,true);
        for(int i=1;i<=30;i++)sample(i*.08,0,true);
        assertTrue(motion.leanX()<-.01);assertTrue(motion.leanX()>=-.241);assertEquals(0,motion.leanZ(),0);
        for(int i=0;i<240;i++)sample(2.4,0,true);
        assertEquals(0,motion.leanX(),.0001);
    }
    @Test public void secondEyeDoesNotStepInertiaAgainAndReducedMotionIsImmediate() {
        sample(0,0,true);sample(.15,.08,true);
        float x=motion.leanX(),z=motion.leanZ();
        motion.sample(world,target,now+1_000_000,frame,.15,.08,true,1);
        assertEquals(x,motion.leanX(),0);assertEquals(z,motion.leanZ(),0);
        sample(.3,.1,false);assertEquals(0,motion.leanX(),0);assertEquals(0,motion.leanZ(),0);
    }
    @Test public void targetWorldTeleportAndPauseDoNotCarryInertiaToTheNextScene() {
        sample(0,0,true);sample(.1,.1,true);assertTrue(motion.leanX()<0);
        motion.sample(world,new Object(),now,frame,0,0,true,1);assertEquals(0,motion.leanX(),0);
        sample(0,0,true);sample(.1,.1,true);
        motion.sample(new Object(),target,now,frame,0,0,true,1);assertEquals(0,motion.leanX(),0);
        sample(0,0,true);sample(.1,.1,true);sample(10,10,true);assertEquals(0,motion.leanX(),0);
        sample(10.1,10.1,true);now+=1_000_000_000;sample(10.2,10.2,true);assertEquals(0,motion.leanX(),0);
        sample(10.3,10.3,true);motion.reset();assertEquals(0,motion.leanX(),0);assertEquals(0,motion.leanZ(),0);
    }
    @Test public void liftHasSmoothBoundedMotionAndNonMovingAccessibilityState() {
        float first=TargetEspMotion.lift(0,1,true),later=TargetEspMotion.lift(1,1,true);
        assertTrue(later>first);
        for(int i=0;i<500;i++) {
            float lift=TargetEspMotion.lift(i*.04f,2,true);
            assertTrue(lift>=.1399&&lift<=.8601);
            assertTrue(Math.abs(TargetEspMotion.direction(i*.04f,2,true))<=1);
        }
        assertEquals(.5,TargetEspMotion.lift(5,2,false),0);assertEquals(0,TargetEspMotion.direction(5,2,false),0);
        assertEquals(.5,TargetEspMotion.lift(Float.NaN,2,true),0);assertEquals(0,TargetEspMotion.direction(Float.NaN,2,true),0);
        motion.sample(world,target,now,frame,Double.NaN,0,true,1);assertEquals(0,motion.leanX(),0);
    }

    @Test public void lowAndHighFrameRatesConsumeTheSameElapsedMovementAndSettlingTime() {
        TargetEspMotion slow=new TargetEspMotion(),fast=new TargetEspMotion();
        slow.sample(world,target,now,0,0,0,true,1);
        fast.sample(world,target,now,0,0,0,true,1);
        // The same constant movement at 10 FPS and 100 FPS must produce the
        // same lean; a 50 ms clamp used to discard half the slower frame's time.
        for(int i=1;i<=5;i++)slow.sample(world,target,now+i*100_000_000L,i,i*.1,i*.05,true,1);
        for(int i=1;i<=50;i++)fast.sample(world,target,now+i*10_000_000L,i,i*.01,i*.005,true,1);
        assertTrue(slow.leanX()<-.02);
        assertEquals(fast.leanX(),slow.leanX(),.000001);
        assertEquals(fast.leanZ(),slow.leanZ(),.000001);
        for(int i=1;i<=5;i++)slow.sample(world,target,now+500_000_000L+i*100_000_000L,5+i,.5,.25,true,1);
        for(int i=1;i<=50;i++)fast.sample(world,target,now+500_000_000L+i*10_000_000L,50+i,.5,.25,true,1);
        assertEquals(fast.leanX(),slow.leanX(),.000001);
        assertEquals(fast.leanZ(),slow.leanZ(),.000001);
        assertEquals(0,slow.leanX(),.0001);
    }
}
