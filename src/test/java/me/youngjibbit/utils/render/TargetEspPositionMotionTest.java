package me.youngjibbit.utils.render;

import org.junit.Test;
import static org.junit.Assert.*;

public class TargetEspPositionMotionTest {
    private final Object world = new Object(), target = new Object();

    @Test public void markerMovesBetweenTickSamplesWithoutOvershoot() {
        TargetEspPositionMotion motion = new TargetEspPositionMotion();
        motion.sample(world,target,1_000_000_000L,0,0,64,0,true,.6f);
        motion.sample(world,target,1_016_000_000L,1,1,65,1,true,.6f);
        double first=motion.x();
        assertTrue(first>0&&first<1);assertEquals(64+first,motion.y(),1e-12);
        motion.sample(world,target,1_032_000_000L,2,1,65,1,true,.6f);
        assertTrue(motion.x()>first&&motion.x()<1);
        for(int i=3;i<=60;i++)motion.sample(world,target,1_000_000_000L+i*16_000_000L,i,1,65,1,true,.6f);
        assertEquals(1,motion.x(),.000001);assertEquals(65,motion.y(),.000001);
    }

    @Test public void sameElapsedTimeHasTheSameResultAtDifferentFrameRates() {
        TargetEspPositionMotion slow=new TargetEspPositionMotion(),fast=new TargetEspPositionMotion();
        slow.sample(world,target,1_000_000_000L,0,0,0,0,true,.8f);
        fast.sample(world,target,1_000_000_000L,0,0,0,0,true,.8f);
        for(int i=1;i<=4;i++)slow.sample(world,target,1_000_000_000L+i*50_000_000L,i,1,2,3,true,.8f);
        for(int i=1;i<=40;i++)fast.sample(world,target,1_000_000_000L+i*5_000_000L,i,1,2,3,true,.8f);
        assertEquals(slow.x(),fast.x(),1e-12);assertEquals(slow.y(),fast.y(),1e-12);assertEquals(slow.z(),fast.z(),1e-12);
    }

    @Test public void contextLossTeleportPauseAndAccessibilitySnapWithoutOldMotion() {
        TargetEspPositionMotion motion=new TargetEspPositionMotion();
        motion.sample(world,target,1_000_000_000L,0,0,0,0,true,1);
        motion.sample(world,target,1_016_000_000L,1,1,0,0,true,1);
        motion.sample(world,target,1_017_000_000L,1,1,0,0,true,1);
        double position=motion.x();
        motion.sample(world,target,1_018_000_000L,1,2,0,0,true,1);
        assertEquals(position,motion.x(),0);
        motion.sample(world,new Object(),1_032_000_000L,2,2,0,0,true,1);assertEquals(2,motion.x(),0);
        motion.sample(new Object(),target,1_048_000_000L,3,3,0,0,true,1);assertEquals(3,motion.x(),0);
        motion.sample(world,target,1_050_000_000L,4,3,0,0,true,1);
        motion.sample(world,target,1_064_000_000L,5,20,0,0,true,1);assertEquals(20,motion.x(),0);
        motion.sample(world,target,2_064_000_000L,6,21,0,0,true,1);assertEquals(21,motion.x(),0);
        motion.sample(world,target,2_080_000_000L,7,22,0,0,false,1);assertEquals(22,motion.x(),0);
        motion.sample(world,target,2_096_000_000L,8,23,0,0,true,0);assertEquals(23,motion.x(),0);
        assertFalse(motion.sample(world,target,2_100_000_000L,9,Double.NaN,0,0,true,1));
        motion.sample(world,target,2_116_000_000L,10,24,0,0,true,1);assertEquals(24,motion.x(),0);
        motion.reset();motion.sample(world,target,2_132_000_000L,11,25,0,0,true,1);assertEquals(25,motion.x(),0);
    }
}
