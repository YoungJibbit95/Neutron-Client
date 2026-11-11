package me.youngjibbit.core.combat;

import org.junit.Test;

import static org.junit.Assert.*;

public class AdaptiveAimTuningTest {
    private static AdaptivePresetMotion.Configuration config(AdaptiveMotionController.Curve curve,
            double strength,double min,double max,double speedNoise,double pathNoise,double initial,
            AdaptiveHitpointMotion.Profile pointProfile) {
        AdaptivePresetMotion.Tuning tuning=new AdaptivePresetMotion.Tuning(strength,pathNoise,1,initial,1,1,
                new AdaptiveHitpointMotion.Configuration(pointProfile,AdaptiveHitpointMotion.Algorithm.PERLIN,.6,1,.5));
        return new AdaptivePresetMotion.Configuration(AdaptivePresetMotion.Speed.BALANCED,
                AdaptivePresetMotion.Hitpoint.STABLE,speedNoise,min,max,1,1,curve,
                AdaptivePresetMotion.Noise.ORGANIC,1,AdaptivePresetMotion.AimPoint.LEGACY,true,tuning);
    }

    private static AdaptiveMotionController.Acquisition token() {
        return new AdaptiveMotionController.Acquisition(new Object(),new Object(),new Object(),1,1);
    }

    private static double wrap(double angle) {
        angle%=360;if(angle<=-180)angle+=360;if(angle>180)angle-=360;return angle;
    }

    private static int settlingTicks(AdaptivePresetMotion.Configuration config,double target) {
        AdaptiveMotionController motion=new AdaptiveMotionController(17,config.limits());
        AdaptiveMotionController.Acquisition token=token();double yaw=0;
        for(int tick=0;tick<800;tick++) {
            AdaptiveMotionController.Result result=motion.advance(token,tick,tick*50_000_000L,yaw,0,target,0,true);
            assertTrue(Math.abs(wrap(result.yaw-yaw))<=config.maxAimSpeed*.05+1e-8);
            yaw=result.yaw;
            if(Math.abs(wrap(target-yaw))<.05)return tick;
        }
        fail("configured curve did not settle");return -1;
    }

    @Test public void relativeSpeedRangeMattersForSmallAndLargeCorrections() {
        for(double angle:new double[]{18,90}) {
            int slow=settlingTicks(config(AdaptiveMotionController.Curve.RELATIVE,.5,20,40,0,0,0,AdaptiveHitpointMotion.Profile.OFF),angle);
            int medium=settlingTicks(config(AdaptiveMotionController.Curve.RELATIVE,.5,160,200,0,0,0,AdaptiveHitpointMotion.Profile.OFF),angle);
            int fast=settlingTicks(config(AdaptiveMotionController.Curve.RELATIVE,.5,600,900,0,0,0,AdaptiveHitpointMotion.Profile.OFF),angle);
            assertTrue("min/max must affect a "+angle+" degree correction: "+slow+","+medium+","+fast,slow>medium&&medium>fast);
        }
    }

    @Test public void minimumSpeedAffectsRelativeTravelEvenWithAnUnchangedMaximum() {
        int low=settlingTicks(config(AdaptiveMotionController.Curve.RELATIVE,.75,20,600,0,0,0,AdaptiveHitpointMotion.Profile.OFF),90);
        int high=settlingTicks(config(AdaptiveMotionController.Curve.RELATIVE,.75,500,600,0,0,0,AdaptiveHitpointMotion.Profile.OFF),90);
        assertTrue("minimum preference must no longer disappear behind fixed easing",low>high);
    }

    @Test public void curveStrengthChangesArrivalAndZeroStrengthHasLinearSemantics() {
        int calm=settlingTicks(config(AdaptiveMotionController.Curve.RELATIVE,1,100,100,0,0,0,AdaptiveHitpointMotion.Profile.OFF),90);
        int direct=settlingTicks(config(AdaptiveMotionController.Curve.RELATIVE,0,100,100,0,0,0,AdaptiveHitpointMotion.Profile.OFF),90);
        int linear=settlingTicks(config(AdaptiveMotionController.Curve.LINEAR,1,100,100,0,0,0,AdaptiveHitpointMotion.Profile.OFF),90);
        assertTrue(calm>direct);assertEquals(linear,direct);
    }

    @Test public void equalSpeedBoundsRemoveSpeedVariationWithoutDisablingTheIndependentPath() {
        AdaptivePresetMotion.Configuration a=config(AdaptiveMotionController.Curve.LINEAR,.5,120,120,0,.7,0,AdaptiveHitpointMotion.Profile.OFF);
        AdaptivePresetMotion.Configuration b=config(AdaptiveMotionController.Curve.LINEAR,.5,120,120,1,.7,0,AdaptiveHitpointMotion.Profile.OFF);
        AdaptiveMotionController left=new AdaptiveMotionController(19,a.limits()),right=new AdaptiveMotionController(19,b.limits());
        AdaptiveMotionController.Acquisition ta=token(),tb=token();double yaw=0,pitch=0,noise=0;
        for(int tick=0;tick<80;tick++) {
            AdaptiveMotionController.Result x=left.advance(ta,tick,tick*50_000_000L,yaw,pitch,100,35,true);
            AdaptiveMotionController.Result y=right.advance(tb,tick,tick*50_000_000L,yaw,pitch,100,35,true);
            assertEquals(x.yaw,y.yaw,0);assertEquals(x.pitch,y.pitch,0);
            noise+=Math.abs(y.noise);yaw=x.yaw;pitch=x.pitch;
        }
        assertTrue(noise>0);assertNotEquals(0,left.profile().lateralBias,0);
    }

    @Test public void pathAmountWorksWithSpeedNoiseOffAndStillHonorsCombinedSpeedCaps() {
        AdaptivePresetMotion.Configuration a=config(AdaptiveMotionController.Curve.RELATIVE,.5,80,260,0,0,0,AdaptiveHitpointMotion.Profile.OFF);
        AdaptivePresetMotion.Configuration b=config(AdaptiveMotionController.Curve.RELATIVE,.5,80,260,0,1,0,AdaptiveHitpointMotion.Profile.OFF);
        AdaptiveMotionController straight=new AdaptiveMotionController(77,a.limits()),curved=new AdaptiveMotionController(77,b.limits());
        AdaptiveMotionController.Acquisition ta=token(),tb=token();double ay=0,ap=0,by=0,bp=0,difference=0;
        for(int tick=0;tick<100;tick++) {
            AdaptiveMotionController.Result x=straight.advance(ta,tick,tick*50_000_000L,ay,ap,100,40,true);
            AdaptiveMotionController.Result y=curved.advance(tb,tick,tick*50_000_000L,by,bp,100,40,true);
            assertEquals(0,x.noise,0);assertEquals(0,y.noise,0);
            assertTrue(Math.hypot(wrap(y.yaw-by),y.pitch-bp)<=260*.05+1e-8);
            difference+=Math.hypot(wrap(y.yaw-x.yaw),y.pitch-x.pitch);
            ay=x.yaw;ap=x.pitch;by=y.yaw;bp=y.pitch;
        }
        assertTrue("path amount must have an independent effect",difference>1e-4);
    }

    @Test public void speedNoiseDoesNotChangeTheInitialOrTravelingHitpoint() {
        AdaptivePresetMotion.Configuration low=config(AdaptiveMotionController.Curve.RELATIVE,.5,80,260,0,0,.8,AdaptiveHitpointMotion.Profile.STRONG);
        AdaptivePresetMotion.Configuration high=config(AdaptiveMotionController.Curve.RELATIVE,.5,80,260,1,1,.8,AdaptiveHitpointMotion.Profile.STRONG);
        AdaptivePresetMotion.HitpointTrajectory a=new AdaptivePresetMotion.HitpointTrajectory(382,low);
        AdaptivePresetMotion.HitpointTrajectory b=new AdaptivePresetMotion.HitpointTrajectory(382,high);
        for(int tick=0;tick<100;tick++)assertArrayEquals(
                a.point(.5,.5,.5,0,0,0,1,1,1,tick*.05),
                b.point(.5,.5,.5,0,0,0,1,1,1,tick*.05),0);
    }

    @Test public void initialOrLegacyPointCanVaryWithTheOldStableHitpointPreset() {
        AdaptivePresetMotion.Configuration off=config(AdaptiveMotionController.Curve.LINEAR,.5,80,260,0,0,0,AdaptiveHitpointMotion.Profile.OFF);
        AdaptivePresetMotion.Configuration initial=config(AdaptiveMotionController.Curve.LINEAR,.5,80,260,0,0,.8,AdaptiveHitpointMotion.Profile.OFF);
        AdaptivePresetMotion.Configuration moving=config(AdaptiveMotionController.Curve.LINEAR,.5,80,260,0,0,0,AdaptiveHitpointMotion.Profile.STRONG);
        assertFalse(off.changesHitpoint());assertTrue(initial.changesHitpoint());assertTrue(moving.changesHitpoint());
        double[] p=new AdaptivePresetMotion.HitpointTrajectory(517,initial).point(.5,.5,.5,0,0,0,1,1,1,0);
        assertTrue(Math.abs(p[0]-.5)+Math.abs(p[1]-.5)+Math.abs(p[2]-.5)>1e-5);
    }

    @Test public void profileFactoryIncludesIndependentControlsInTheAcquisitionFingerprint() {
        RotationProfile p=new RotationProfile();p.adaptiveExplicitControls=true;
        p.adaptiveTuning=config(AdaptiveMotionController.Curve.RELATIVE,.5,80,260,0,0,.2,AdaptiveHitpointMotion.Profile.SUBTLE).tuning;
        AdaptivePresetMotion.Configuration first=AdaptivePresetMotion.Configuration.fromProfile(p);
        assertTrue(first.same(AdaptivePresetMotion.Configuration.fromProfile(p)));
        p.adaptiveTuning=config(AdaptiveMotionController.Curve.RELATIVE,.5,80,260,0,0,.6,AdaptiveHitpointMotion.Profile.SUBTLE).tuning;
        assertFalse(first.same(AdaptivePresetMotion.Configuration.fromProfile(p)));
        p.adaptiveExplicitControls=false;
        assertNull(AdaptivePresetMotion.Configuration.fromProfile(p).tuning);
        assertNull(AdaptivePresetMotion.Configuration.fromProfile(p).limits().control);
    }
}
