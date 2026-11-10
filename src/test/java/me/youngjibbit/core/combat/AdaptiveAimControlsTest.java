package me.youngjibbit.core.combat;

import org.junit.Test;
import static org.junit.Assert.*;

/** Production Adaptive controls, including coherent noise and physical limits. */
public class AdaptiveAimControlsTest {
    private static AdaptivePresetMotion.Configuration config(AdaptiveMotionController.Curve curve,
            AdaptivePresetMotion.Noise noise,double amount,double min,double max) {
        return new AdaptivePresetMotion.Configuration(AdaptivePresetMotion.Speed.BALANCED,
                AdaptivePresetMotion.Hitpoint.DYNAMIC,amount,min,max,.7,curve,noise,1,
                AdaptivePresetMotion.AimPoint.STRAIGHT,true);
    }
    private static AdaptiveMotionController.Acquisition token() {
        return new AdaptiveMotionController.Acquisition(new Object(),new Object(),new Object(),1,1);
    }
    private static double wrap(double angle) {
        angle%=360;if(angle<=-180)angle+=360;if(angle>180)angle-=360;return angle;
    }

    @Test public void noiseOffAndZeroAmountRemoveAllAcquisitionRandomness() {
        for(AdaptivePresetMotion.Noise noise:AdaptivePresetMotion.Noise.values()) {
            double amount=noise==AdaptivePresetMotion.Noise.OFF?1:0;
            AdaptivePresetMotion.Configuration settings=config(AdaptiveMotionController.Curve.RELATIVE,noise,amount,160,420);
            AdaptiveMotionController a=new AdaptiveMotionController(1,settings.limits());
            AdaptiveMotionController b=new AdaptiveMotionController(98765,settings.limits());
            AdaptiveMotionController.Acquisition ta=token(),tb=token();
            double yaw=0,pitch=0;
            for(int i=0;i<100;i++) {
                double desired=i<40?100:-80;
                AdaptiveMotionController.Result x=a.advance(ta,i,i*50_000_000L,yaw,pitch,desired,30,true);
                AdaptiveMotionController.Result y=b.advance(tb,i,i*50_000_000L,yaw,pitch,desired,30,true);
                assertEquals(x.yaw,y.yaw,0);assertEquals(x.pitch,y.pitch,0);assertEquals(0,x.noise,0);
                yaw=x.yaw;pitch=x.pitch;
            }
            AdaptivePresetMotion.HitpointTrajectory path=new AdaptivePresetMotion.HitpointTrajectory(123,settings);
            assertArrayEquals(new double[]{.5,.6,.5},path.point(.5,.6,.5,0,0,0,1,1,1,12),0);
        }
    }

    @Test public void everyCurveHonorsCapsConvergesAndHoldsWithoutResidualNoise() {
        for(AdaptiveMotionController.Curve curve:AdaptiveMotionController.Curve.values()) {
            AdaptivePresetMotion.Configuration settings=config(curve,AdaptivePresetMotion.Noise.ORGANIC,.8,200,900);
            AdaptiveMotionController.Limits limits=settings.limits();
            assertEquals(900,limits.maxYawSpeed,0);assertEquals(630,limits.maxPitchSpeed,0);
            AdaptiveMotionController motion=new AdaptiveMotionController(78,limits);
            AdaptiveMotionController.Acquisition target=token();
            double yaw=-150,pitch=-30;
            for(int i=0;i<300;i++) {
                AdaptiveMotionController.Result next=motion.advance(target,i,i*50_000_000L,yaw,pitch,20,25,true);
                assertTrue(curve.toString(),Math.abs(wrap(next.yaw-yaw))<=limits.maxYawSpeed*.05+1e-8);
                assertTrue(Math.abs(next.pitch-pitch)<=limits.maxPitchSpeed*.05+1e-8);
                assertTrue(Math.hypot(wrap(next.yaw-yaw),next.pitch-pitch)<=settings.maxAimSpeed*.05+1e-8);
                assertTrue(Math.abs(next.yawAcceleration)<=limits.maxAcceleration+1e-8);
                assertTrue(Math.abs(next.pitchAcceleration)<=limits.maxAcceleration+1e-8);
                yaw=next.yaw;pitch=next.pitch;
            }
            assertEquals(curve.toString(),20,yaw,1e-5);assertEquals(25,pitch,1e-5);
            AdaptiveMotionController.Result hold=motion.advance(target,300,15_000_000_000L,yaw,pitch,yaw,pitch,true);
            assertFalse(hold.candidate);assertEquals(yaw,hold.yaw,0);assertEquals(pitch,hold.pitch,0);
            assertEquals(0,hold.yawVelocity,0);assertEquals(0,hold.pitchVelocity,0);
        }
    }

    @Test public void minAndMaxChangeTravelSpeedWithoutBecomingMinimumTargetSteps() {
        double[] travel=new double[3];double[][] ranges={{80,400},{300,400},{500,1000}};
        for(int j=0;j<ranges.length;j++) {
            AdaptiveMotionController motion=new AdaptiveMotionController(11,
                    config(AdaptiveMotionController.Curve.LINEAR,AdaptivePresetMotion.Noise.OFF,0,ranges[j][0],ranges[j][1]).limits());
            AdaptiveMotionController.Acquisition target=token();double yaw=0;
            for(int i=0;i<30;i++) {
                AdaptiveMotionController.Result step=motion.advance(target,i,i*50_000_000L,yaw,0,yaw+150,0,true);
                if(i>10)travel[j]+=Math.abs(wrap(step.yaw-yaw));yaw=step.yaw;
            }
        }
        assertTrue(travel[1]>travel[0]);assertTrue(travel[2]>travel[1]);
    }

    @Test public void coherentNoiseUsesMotionTimeAndRepeatedQueriesCannotResample() {
        AdaptivePresetMotion.Configuration settings=config(AdaptiveMotionController.Curve.ADAPTIVE,
                AdaptivePresetMotion.Noise.ORGANIC,.8,180,480);
        AdaptiveMotionController a=new AdaptiveMotionController(13,settings.limits());
        AdaptiveMotionController b=new AdaptiveMotionController(13,settings.limits());
        AdaptiveMotionController.Acquisition ta=token(),tb=token();double yaw=0;
        double energy=0,change=0,last=0;
        for(int i=0;i<80;i++) {
            long nanos=i*50_000_000L;
            AdaptiveMotionController.Result expected=b.advance(tb,i,nanos,yaw,0,yaw+100,20,true);
            AdaptiveMotionController.Result actual=a.advance(ta,i,nanos,yaw,0,yaw+100,20,true);
            assertEquals(expected.yaw,actual.yaw,0);assertEquals(expected.noise,actual.noise,0);
            for(int render=0;render<12;render++)assertSame(actual,
                    a.advance(ta,i,nanos+render*1_000_000L,yaw,0,-130,70,true));
            energy+=Math.abs(actual.noise);change+=Math.abs(actual.noise-last);last=actual.noise;yaw=actual.yaw;
        }
        assertTrue(energy>.1);assertTrue("coherent signal should vary smoothly",change<energy);
    }

    @Test public void addedAnchorsAndCoherentOffsetsStayWithinMovingRealBoxes() {
        for(AdaptivePresetMotion.AimPoint anchor:AdaptivePresetMotion.AimPoint.values()) {
            if(anchor==AdaptivePresetMotion.AimPoint.LEGACY)continue;
            AdaptivePresetMotion.HitpointTrajectory path=new AdaptivePresetMotion.HitpointTrajectory(7,
                    config(AdaptiveMotionController.Curve.RELATIVE,AdaptivePresetMotion.Noise.ORGANIC,1,150,500));
            for(int tick=0;tick<100;tick++) {
                double x=1e6+tick*.1;
                double[] point=AdaptivePresetMotion.aimPoint(anchor,x-3,65.62,0,x,64,0,x+.6,65.8,.6);
                double[] moved=path.point(point[0],point[1],point[2],x,64,0,x+.6,65.8,.6,tick*.05);
                assertTrue(moved[0]>x && moved[0]<x+.6);assertTrue(moved[1]>64 && moved[1]<65.8);
                assertTrue(moved[2]>0 && moved[2]<.6);
            }
        }
    }
}
