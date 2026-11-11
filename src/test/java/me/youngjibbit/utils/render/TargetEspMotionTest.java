package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class TargetEspMotionTest {
    private final TargetEspMotion motion = new TargetEspMotion();
    private final Object world = new Object(), target = new Object();
    private long now=1_000_000_000L;
    private float time;
    private void sample(double x,double z,boolean animate) {
        now+=16_000_000;time+=.016f;motion.sample(world,target,now,time,x,z,animate,1);
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
        motion.sample(world,target,now+1_000_000,time,.15,.08,true,1);
        assertEquals(x,motion.leanX(),0);assertEquals(z,motion.leanZ(),0);
        sample(.3,.1,false);assertEquals(0,motion.leanX(),0);assertEquals(0,motion.leanZ(),0);
    }
    @Test public void targetWorldTeleportAndPauseDoNotCarryInertiaToTheNextScene() {
        sample(0,0,true);sample(.1,.1,true);assertTrue(motion.leanX()<0);
        motion.sample(world,new Object(),now,time,0,0,true,1);assertEquals(0,motion.leanX(),0);
        sample(0,0,true);sample(.1,.1,true);
        motion.sample(new Object(),target,now,time,0,0,true,1);assertEquals(0,motion.leanX(),0);
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
        motion.sample(world,target,now,time,Double.NaN,0,true,1);assertEquals(0,motion.leanX(),0);
    }
}
