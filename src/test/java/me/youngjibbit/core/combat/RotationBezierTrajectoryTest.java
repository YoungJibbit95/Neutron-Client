package me.youngjibbit.core.combat;

import org.junit.Test;
import java.lang.reflect.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class RotationBezierTrajectoryTest {
    @SuppressWarnings({"rawtypes","unchecked"}) private RotationProfile enabled() throws Exception {
        RotationProfile p=new RotationProfile();p.randomization=false;p.yawSpeed=8;p.pitchSpeed=9;
        try {
            Field mode=RotationProfile.class.getField("speedCurve");mode.set(p,Enum.valueOf((Class)mode.getType(),"BEZIER"));
            RotationProfile.class.getField("bezierStrength").setFloat(p,.85f);
            RotationProfile.class.getField("bezierDurationMs").setFloat(p,1000);
        } catch(ReflectiveOperationException missing){throw new AssertionError("Missing optional Bezier profile fields",missing);}
        return p;
    }
    private float[] step(RotationTrajectory path,Object target,float yaw,float pitch,float toYaw,float toPitch,RotationProfile p,long scope,long time) throws Exception {
        try{return (float[])RotationTrajectory.class.getMethod("advanceMotion",Object.class,float.class,float.class,float.class,float.class,double.class,RotationProfile.class,long.class,long.class)
                .invoke(path,target,yaw,pitch,toYaw,toPitch,2D,p,scope,time);}
        catch(NoSuchMethodException missing){throw new AssertionError("Missing motion-scoped trajectory entry point",missing);}
    }
    private void pause(RotationTrajectory path,Object target,long scope,long time) throws Exception {
        RotationTrajectory.class.getMethod("pauseMotion",Object.class,long.class,long.class).invoke(path,target,scope,time);
    }
    private float[] series(int fps,int seed) throws Exception {
        AtomicLong clock=new AtomicLong();RotationTrajectory path=new RotationTrajectory(clock::get,seed);RotationProfile p=enabled();
        Object target=new Object();float yaw=170,pitch=-85;float[] output=new float[80];
        for(int motion=0;motion<40;motion++) {
            long now=motion*50_000_000L;if(motion==14||motion==29)target=new Object();
            clock.set(now);
            float[] next;
            if(motion==8||motion==9){pause(path,target,motion,now);next=new float[]{yaw,pitch};}
            else next=step(path,target,yaw,pitch,motion%2==0?-155:110,motion%3==0?89:-70,p,motion,now);
            assertTrue(Float.isFinite(next[0])&&Float.isFinite(next[1]));assertTrue(next[1]>=-90&&next[1]<=90);
            assertTrue(Math.abs(MoveFixController.wrap(next[0]-yaw))<=8.00002f);assertTrue(Math.abs(next[1]-pitch)<=9.00002f);
            yaw=next[0];pitch=next[1];output[motion*2]=yaw;output[motion*2+1]=pitch;
            for(long frame=now+1_000_000_000L/fps;frame<now+50_000_000L;frame+=1_000_000_000L/fps) {
                clock.set(frame);path.sample(target,p,0);
                if(motion!=8&&motion!=9)assertArrayEquals("repeated calculation in one motion scope",next,step(path,target,yaw,pitch,0,0,p,motion,frame),0);
            }
        }
        return output;
    }
    @Test public void identicalMotionTimelineIgnoresRenderSamplingAndHasDeterministicSeedVariation() throws Exception {
        float[] first=series(20,42);assertArrayEquals(first,series(20,42),0);
        assertArrayEquals(first,series(60,42),0);assertArrayEquals(first,series(144,42),0);
        float[] other=series(60,99);double difference=0;for(int i=0;i<first.length;i++)difference+=Math.abs(first[i]-other[i]);
        assertTrue("seed changes only a bounded reproducible envelope",difference>.01);
    }
    @Test public void sameScopeTargetChangeCannotGrantFreshMotionCredit() throws Exception {
        AtomicLong clock=new AtomicLong();RotationTrajectory path=new RotationTrajectory(clock::get,42);RotationProfile p=enabled();
        Object a=new Object(),b=new Object();float[] first=step(path,a,0,0,120,60,p,1,0);
        clock.set(20_000_000L);
        assertArrayEquals(first,step(path,b,first[0],first[1],120,60,p,1,20_000_000L),0);
        clock.set(50_000_000L);float[] next=step(path,b,first[0],first[1],120,60,p,2,50_000_000L);
        assertTrue(next[0]>first[0]);assertTrue(next[0]-first[0]<=8);
    }
    @Test public void pauseConsumesTimeWithoutAccumulatingSpeedAndResetRearms() throws Exception {
        AtomicLong clock=new AtomicLong();RotationTrajectory path=new RotationTrajectory(clock::get,73);RotationProfile p=enabled();Object target=new Object();
        float[] current=step(path,target,0,0,120,60,p,1,0);
        clock.set(5_000_000_000L);pause(path,target,2,clock.get());
        clock.addAndGet(50_000_000L);float[] resumed=step(path,target,current[0],current[1],120,60,p,3,clock.get());
        assertTrue(resumed[0]-current[0]<=8);assertTrue(resumed[1]-current[1]<=9);
        RotationTrajectory.class.getMethod("resetSpeedCurve").invoke(path);
        clock.addAndGet(50_000_000L);float[] reset=step(path,target,resumed[0],resumed[1],120,60,p,4,clock.get());
        assertTrue(reset[0]>resumed[0]);assertTrue(reset[0]-resumed[0]<=8);
    }
    @Test public void completedCurveAndLongIntervalsNeverOvershootDesiredAngles() throws Exception {
        for(RotationProfile.Mode mode:RotationProfile.Mode.values()) {
            AtomicLong clock=new AtomicLong();RotationTrajectory path=new RotationTrajectory(clock::get,73);RotationProfile p=enabled();
            p.mode=mode;p.yawSpeed=p.pitchSpeed=p.minSpeed=p.maxSpeed=180;p.interpolation=1;
            RotationProfile.class.getField("bezierStrength").setFloat(p,0);
            Object target=new Object();step(path,target,0,0,0,0,p,1,0);
            clock.set(2_000_000_000L);
            float[] next=step(path,target,179,89,-179,90,p,2,clock.get());
            assertTrue(next[0]>=179 && next[0]<=181);assertTrue(next[1]>=89 && next[1]<=90);
        }
    }
}
