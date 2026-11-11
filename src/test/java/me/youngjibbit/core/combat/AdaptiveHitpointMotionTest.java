package me.youngjibbit.core.combat;

import org.junit.Test;

import static org.junit.Assert.*;

public class AdaptiveHitpointMotionTest {
    private static AdaptiveHitpointMotion.Configuration custom(AdaptiveHitpointMotion.Algorithm algorithm,
                                                               double radius, double speed, double noise) {
        return new AdaptiveHitpointMotion.Configuration(AdaptiveHitpointMotion.Profile.CUSTOM,
                algorithm, radius, speed, noise);
    }

    private static double[] point(AdaptiveHitpointMotion motion, double seconds) {
        return motion.point(.5, .5, .5, 0, 0, 0, 1, 1, 1, seconds);
    }

    private static double distance(double[] a, double[] b) {
        double x=a[0]-b[0],y=a[1]-b[1],z=a[2]-b[2];
        return Math.sqrt(x*x+y*y+z*z);
    }

    @Test public void offAndZeroInitialRadiusKeepTheExactBaseForEverySeedAndAlgorithm() {
        for (AdaptiveHitpointMotion.Algorithm algorithm : AdaptiveHitpointMotion.Algorithm.values()) {
            for (long seed : new long[]{0, 1, 94815}) {
                AdaptiveHitpointMotion motion = new AdaptiveHitpointMotion(seed,
                        new AdaptiveHitpointMotion.Configuration(AdaptiveHitpointMotion.Profile.OFF,
                                algorithm, 1, 3, 1), 0);
                for (int tick=0;tick<40;tick++)
                    assertArrayEquals(new double[]{.4,.7,.6},
                            motion.point(.4,.7,.6,0,0,0,1,1,1,tick*.05),0);
            }
        }
    }

    @Test public void initialPointIsChosenOnceAndMovesInLocalCoordinatesWithTheBox() {
        AdaptiveHitpointMotion motion = new AdaptiveHitpointMotion(1234,
                custom(AdaptiveHitpointMotion.Algorithm.PERLIN,0,1,0),.7);
        double[] initial=point(motion,0);
        assertTrue(distance(initial,new double[]{.5,.5,.5})>1e-4);
        assertTrue(distance(initial,new double[]{.5,.5,.5})<=.35+1e-12);
        for(int tick=0;tick<60;tick++) {
            double x=100+tick*.2;
            double[] moved=motion.point(x+1,64.5,-3.75,x,64,-4,x+2,65,-3.5,tick*.05);
            assertEquals(initial[0],(moved[0]-x)/2,1e-12);
            assertEquals(initial[1],moved[1]-64,1e-12);
            assertEquals(initial[2],(moved[2]+4)/.5,1e-12);
        }
        assertArrayEquals(initial,point(new AdaptiveHitpointMotion(1234,
                custom(AdaptiveHitpointMotion.Algorithm.PERLIN,0,1,0),.7),0),0);
        assertTrue(distance(initial,point(new AdaptiveHitpointMotion(5678,
                custom(AdaptiveHitpointMotion.Algorithm.PERLIN,0,1,0),.7),0))>1e-4);
    }

    @Test public void everyProfileAndAlgorithmStaysInsideMovingThinAndLargeBoxes() {
        for(AdaptiveHitpointMotion.Profile profile:AdaptiveHitpointMotion.Profile.values())
            for(AdaptiveHitpointMotion.Algorithm algorithm:AdaptiveHitpointMotion.Algorithm.values()) {
                AdaptiveHitpointMotion motion=new AdaptiveHitpointMotion(47,
                        new AdaptiveHitpointMotion.Configuration(profile,algorithm,1,3,1),1);
                for(int tick=0;tick<120;tick++) {
                    double x=1e6+tick*.03,width=tick%2==0?.06:2;
                    double[] p=motion.point(x,65.8,.6,x,64,0,x+width,65.8,.6,tick*.05);
                    assertNotNull(p);
                    assertTrue(p[0]>x&&p[0]<x+width);
                    assertTrue(p[1]>64&&p[1]<65.8);
                    assertTrue(p[2]>0&&p[2]<.6);
                }
            }
    }

    @Test public void radiusChangesExcursionWithoutChangingPhaseOrTempo() {
        AdaptiveHitpointMotion low=new AdaptiveHitpointMotion(81,custom(AdaptiveHitpointMotion.Algorithm.OPEN_SIMPLEX_2S,.1,1,0),0);
        AdaptiveHitpointMotion high=new AdaptiveHitpointMotion(81,custom(AdaptiveHitpointMotion.Algorithm.OPEN_SIMPLEX_2S,.6,1,0),0);
        double energy=0;
        for(int tick=0;tick<160;tick++) {
            double[] a=point(low,tick*.05),b=point(high,tick*.05);
            for(int axis=0;axis<3;axis++)assertEquals((a[axis]-.5)*6,b[axis]-.5,1e-12);
            energy+=distance(a,new double[]{.5,.5,.5});
        }
        assertTrue(energy>.01);
    }

    @Test public void travelSpeedAndTravelNoiseHaveSeparateVisibleEffects() {
        AdaptiveHitpointMotion baseline=new AdaptiveHitpointMotion(52,custom(AdaptiveHitpointMotion.Algorithm.PERLIN,.8,.5,0),0);
        AdaptiveHitpointMotion fast=new AdaptiveHitpointMotion(52,custom(AdaptiveHitpointMotion.Algorithm.PERLIN,.8,2,0),0);
        AdaptiveHitpointMotion variable=new AdaptiveHitpointMotion(52,custom(AdaptiveHitpointMotion.Algorithm.PERLIN,.8,.5,1),0);
        double speedDifference=0,noiseDifference=0;
        for(int tick=0;tick<160;tick++) {
            double time=tick*.05;double[] base=point(baseline,time);
            speedDifference+=distance(base,point(fast,time));
            noiseDifference+=distance(base,point(variable,time));
        }
        assertTrue("travel speed must change the trajectory",speedDifference>.01);
        assertTrue("travel noise must change phase rather than radius",noiseDifference>.01);
    }

    @Test public void repeatedQueriesDoNotChangeTheNextMotionOrTheInitialPoint() {
        AdaptiveHitpointMotion.Configuration settings=custom(AdaptiveHitpointMotion.Algorithm.VALUE_CUBIC,.8,2,.8);
        AdaptiveHitpointMotion busy=new AdaptiveHitpointMotion(123,settings,.5);
        AdaptiveHitpointMotion quiet=new AdaptiveHitpointMotion(123,settings,.5);
        for(int tick=0;tick<100;tick++) {
            double seconds=tick*.05;
            double[] expected=point(quiet,seconds);
            assertArrayEquals(expected,point(busy,seconds),0);
            for(int query=0;query<24;query++)assertArrayEquals(expected,point(busy,seconds),0);
        }
    }

    @Test public void longGapHasAtMostOneBoundedStepAndBackwardTimeDoesNotAdvance() {
        AdaptiveHitpointMotion.Configuration settings=custom(AdaptiveHitpointMotion.Algorithm.OPEN_SIMPLEX_2S,.8,2,0);
        AdaptiveHitpointMotion delayed=new AdaptiveHitpointMotion(66,settings,0);
        AdaptiveHitpointMotion regular=new AdaptiveHitpointMotion(66,settings,0);
        point(delayed,0);point(regular,0);
        assertArrayEquals(point(regular,.075),point(delayed,1000),1e-12);
        assertNull(point(delayed,999));
        assertArrayEquals(point(regular,.125),point(delayed,1000.05),1e-11);
    }

    @Test public void invalidGeometryOrTimeCannotConsumeAValidStep() {
        AdaptiveHitpointMotion.Configuration settings=custom(AdaptiveHitpointMotion.Algorithm.PERLIN,.5,1,.5);
        AdaptiveHitpointMotion checked=new AdaptiveHitpointMotion(23,settings,0);
        AdaptiveHitpointMotion baseline=new AdaptiveHitpointMotion(23,settings,0);
        point(checked,0);point(baseline,0);
        assertNull(checked.point(Double.NaN,.5,.5,0,0,0,1,1,1,.05));
        assertNull(checked.point(.5,.5,.5,0,0,0,0,1,1,.05));
        assertNull(checked.point(.5,.5,.5,0,0,0,Double.POSITIVE_INFINITY,1,1,.05));
        assertNull(point(checked,-1));assertNull(point(checked,Double.NaN));
        assertArrayEquals(point(baseline,.05),point(checked,.05),0);
    }

    @Test public void invalidRadiusTempoAndNoiseAreRejectedBeforeSampling() {
        for(double bad:new double[]{-.1,1.1,Double.NaN,Double.POSITIVE_INFINITY}) {
            try { custom(AdaptiveHitpointMotion.Algorithm.PERLIN,bad,1,0);fail("radius "+bad); }
            catch(IllegalArgumentException expected) { }
            try { custom(AdaptiveHitpointMotion.Algorithm.PERLIN,.2,1,bad);fail("noise "+bad); }
            catch(IllegalArgumentException expected) { }
            try { new AdaptiveHitpointMotion(1,custom(AdaptiveHitpointMotion.Algorithm.PERLIN,0,1,0),bad);fail("initial "+bad); }
            catch(IllegalArgumentException expected) { }
        }
        for(double bad:new double[]{0,-1,3.01,Double.NaN,Double.POSITIVE_INFINITY}) {
            try { custom(AdaptiveHitpointMotion.Algorithm.PERLIN,.2,bad,0);fail("tempo "+bad); }
            catch(IllegalArgumentException expected) { }
        }
    }
}
