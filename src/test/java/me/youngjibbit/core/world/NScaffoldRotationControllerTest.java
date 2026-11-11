package me.youngjibbit.core.world;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.DoubleSupplier;

import static org.junit.Assert.*;

public class NScaffoldRotationControllerTest {
    private static final class Options {
        NScaffoldRotationController.Smoothing smoothing=NScaffoldRotationController.Smoothing.LINEAR;
        double extra=1,yawMin=40,yawMax=40,pitchMin=40,pitchMax=40,yawAccel=.25,pitchAccel=.25,threshold=180;
        boolean accelerate,correlation,shortStop;
        long duration=50;
        int attempts;
        NScaffoldRotationController.Settings settings() {
            return new NScaffoldRotationController.Settings(smoothing,extra,yawMin,yawMax,pitchMin,pitchMax,
                    accelerate,yawAccel,pitchAccel,correlation,shortStop,duration,threshold,attempts);
        }
    }
    private static final class Samples implements DoubleSupplier {
        final double[] values;
        int calls;
        Samples(double... values){this.values=values;}
        public double getAsDouble(){return values[calls++%values.length];}
    }
    private static NScaffoldRotationController controller() {
        return new NScaffoldRotationController(()->.5,()->0);
    }
    private static NScaffoldRotationController.Step step(NScaffoldRotationController c,long tick,
                                                        float yaw,float pitch,float targetYaw,float targetPitch,Options o) {
        return c.update(tick,yaw,pitch,targetYaw,targetPitch,false,false,o.settings());
    }
    private static Options stopOptions() {
        Options o=new Options();o.yawMin=o.yawMax=60;o.shortStop=true;o.threshold=50;o.duration=100;
        return o;
    }

    @Test public void linearSharesThePathAndRetainsIndependentAxisLimits() {
        Options o=new Options();o.yawMin=o.yawMax=20;o.pitchMin=o.pitchMax=10;
        NScaffoldRotationController.Step s=step(controller(),0,0,0,90,90,o);
        assertNotNull(s);assertEquals(20/Math.sqrt(2),s.yaw,.00001);
        assertEquals(10/Math.sqrt(2),s.pitch,.00001);assertFalse(s.held);assertFalse(s.stopped);
    }

    @Test public void relativeEasesByRemainingErrorAndExtraFactorHasARealEffect() {
        Options o=new Options();o.smoothing=NScaffoldRotationController.Smoothing.RELATIVE;
        o.yawMin=o.yawMax=o.pitchMin=o.pitchMax=180;o.extra=.5;
        NScaffoldRotationController.Step s=step(controller(),0,0,0,40,20,o);
        assertEquals(5,s.yaw,0);assertEquals(2.5,s.pitch,0);
        o.extra=1;
        NScaffoldRotationController.Step faster=step(controller(),0,0,0,40,20,o);
        assertEquals(10,faster.yaw,0);assertEquals(5,faster.pitch,0);
    }

    @Test public void configuredSpeedsChangeTheActualVisibleTurn() {
        Options slow=new Options();slow.yawMin=slow.yawMax=5;
        Options fast=new Options();fast.yawMin=fast.yawMax=60;
        assertEquals(5,step(controller(),0,0,0,100,0,slow).yaw,0);
        assertEquals(60,step(controller(),0,0,0,100,0,fast).yaw,0);
    }

    @Test public void crossedRangesSortAndEachAxisSamplesItsOwnRange() {
        Options o=new Options();o.yawMin=40;o.yawMax=10;o.pitchMin=80;o.pitchMax=20;
        Samples random=new Samples(0,1);
        NScaffoldRotationController.Step s=step(new NScaffoldRotationController(random,()->0),0,0,0,90,90,o);
        assertEquals(10/Math.sqrt(2),s.yaw,.00001);assertEquals(80/Math.sqrt(2),s.pitch,.00001);
        assertEquals(2,random.calls);
    }

    @Test public void imperfectCorrelationVariesAxesButNeverExceedsUserMaximum() {
        Options o=new Options();o.yawMin=o.yawMax=20;o.pitchMin=o.pitchMax=10;o.correlation=true;
        Samples random=new Samples(1,0);
        NScaffoldRotationController.Step s=step(new NScaffoldRotationController(random,()->0),0,0,0,90,90,o);
        assertEquals(20/Math.sqrt(2),s.yaw,.00001);
        assertEquals(9/Math.sqrt(2),s.pitch,.00001);assertEquals(2,random.calls);
        NScaffoldRotationController.Step capped=step(new NScaffoldRotationController(()->1,()->0),0,10,20,170,80,o);
        assertTrue(capped.yaw-10<=20);assertTrue(capped.pitch-20<=10);
    }

    @Test public void accelerationFactorsAreSeparateInertiaWeights() {
        Options o=new Options();o.accelerate=true;o.yawAccel=.9;o.pitchAccel=.1;
        NScaffoldRotationController c=controller();
        NScaffoldRotationController.Step first=step(c,0,0,0,90,90,o);
        assertEquals(4/Math.sqrt(2),first.yaw,.00001);
        assertEquals(36/Math.sqrt(2),first.pitch,.00001);
        NScaffoldRotationController.Step second=step(c,1,first.yaw,first.pitch,90,90,o);
        assertTrue(second.yaw-first.yaw>first.yaw);
        assertTrue(second.pitch>first.pitch);assertTrue(second.pitch<=90);
    }

    @Test public void changingDirectionCannotContinueRotatingAwayOrOvershoot() {
        Options o=new Options();o.accelerate=true;o.yawAccel=.5;o.yawMin=o.yawMax=20;
        NScaffoldRotationController c=controller();
        NScaffoldRotationController.Step first=step(c,0,0,0,90,0,o);
        assertEquals(10,first.yaw,0);
        NScaffoldRotationController.Step reversed=step(c,1,first.yaw,0,9,0,o);
        assertTrue(reversed.yaw<=first.yaw);assertTrue(reversed.yaw>=9);
        NScaffoldRotationController.Step onward=step(c,2,reversed.yaw,0,9,0,o);
        assertTrue(onward.yaw<reversed.yaw);assertTrue(onward.yaw>=9);
    }

    @Test public void yawUsesTheShortPathAndPitchStaysAtItsRealLimits() {
        Options o=new Options();o.yawMin=o.yawMax=1;o.pitchMin=o.pitchMax=1;
        NScaffoldRotationController.Step s=step(controller(),0,179,0,-179,0,o);
        assertEquals(180,s.yaw,0);
        NScaffoldRotationController.Step pitch=step(controller(),0,0,89.9f,0,90,o);
        assertEquals(90,pitch.pitch,0);
        assertNull(step(controller(),0,0,0,0,90.1f,o));
    }

    @Test public void aValidRayIsHeldBitExactlyWithoutRandomSampling() {
        AtomicInteger calls=new AtomicInteger();
        NScaffoldRotationController c=new NScaffoldRotationController(()->{calls.incrementAndGet();return .3;},()->0);
        Options o=new Options();o.yawMin=1;o.yawMax=180;o.correlation=true;o.shortStop=true;
        float yaw=-0.0f,pitch=75.70001f;
        NScaffoldRotationController.Step s=c.update(0,yaw,pitch,120,80,true,false,o.settings());
        assertEquals(Float.floatToIntBits(yaw),Float.floatToIntBits(s.yaw));
        assertEquals(Float.floatToIntBits(pitch),Float.floatToIntBits(s.pitch));
        assertTrue(s.held);assertEquals(0,calls.get());
    }

    @Test public void repeatedEventsCannotRerollOrBuyAnotherTurnInOneTick() {
        Samples random=new Samples(.1,.8,.2,.7);
        NScaffoldRotationController c=new NScaffoldRotationController(random,()->0);
        Options o=new Options();o.yawMin=10;o.yawMax=50;o.pitchMin=10;o.pitchMax=50;
        NScaffoldRotationController.Step first=step(c,20,0,0,90,60,o);
        assertSame(first,step(c,20,0,0,90,60,o));assertEquals(2,random.calls);
        assertNull(step(c,20,first.yaw,first.pitch,90,60,o));
        assertNull(step(c,20,0,0,100,60,o));assertNull(step(c,19,0,0,90,60,o));
        assertEquals(2,random.calls);
    }

    @Test public void resetClearsMotionButPreservesConsumedTickAndSceneResetStartsNewTimeline() {
        NScaffoldRotationController c=controller();Options o=stopOptions();
        assertNotNull(step(c,10,0,0,170,0,o));
        c.reset();assertNull(step(c,10,0,0,170,0,o));
        NScaffoldRotationController.Step next=step(c,11,0,0,170,0,o);
        assertNotNull(next);assertFalse(next.stopped);assertEquals(60,next.yaw,0);
        c.resetForScene();assertNotNull(step(c,0,0,0,170,0,o));
    }

    @Test public void largeTickGapsNeverReplayMissedRotationBudgets() {
        Options o=new Options();o.yawMin=o.yawMax=10;
        NScaffoldRotationController c=controller();
        NScaffoldRotationController.Step first=step(c,0,0,0,170,0,o);
        NScaffoldRotationController.Step afterGap=step(c,1_000_000,first.yaw,0,170,0,o);
        assertEquals(10,afterGap.yaw-first.yaw,0);
    }

    @Test public void invalidValuesDoNotPublishOrConsumeAValidAttempt() {
        NScaffoldRotationController c=controller();Options o=new Options();
        assertNull(step(c,0,Float.NaN,0,90,0,o));
        assertNull(step(c,0,0,Float.POSITIVE_INFINITY,90,0,o));
        assertNull(step(c,-1,0,0,90,0,o));
        o.extra=Double.NaN;assertNull(step(c,0,0,0,90,0,o));
        o.extra=1;o.smoothing=null;assertNull(step(c,0,0,0,90,0,o));
        o.smoothing=NScaffoldRotationController.Smoothing.LINEAR;
        assertNotNull(step(c,0,0,0,90,0,o));
    }

    @Test public void nonfiniteOrOutOfRangeRandomSamplesRemainFiniteAndBounded() {
        Options o=new Options();o.yawMin=10;o.yawMax=30;o.pitchMin=10;o.pitchMax=30;
        for(double random:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-100,100}) {
            NScaffoldRotationController.Step s=step(new NScaffoldRotationController(()->random,()->0),0,0,0,90,90,o);
            assertTrue(Float.isFinite(s.yaw));assertTrue(Float.isFinite(s.pitch));
            assertTrue(s.yaw>0 && s.yaw<=30);assertTrue(s.pitch>0 && s.pitch<=30);
        }
    }

    @Test public void shortStopStartsOnlyAfterAnActuallyObservedTurnAndHonorsDuration() {
        AtomicLong clock=new AtomicLong();NScaffoldRotationController c=new NScaffoldRotationController(()->.5,clock::get);
        Options o=stopOptions();
        NScaffoldRotationController.Step first=step(c,0,0,0,170,0,o);
        clock.set(10_000_000);
        NScaffoldRotationController.Step paused=step(c,1,first.yaw,0,170,0,o);
        assertTrue(paused.stopped);assertEquals(first.yaw,paused.yaw,0);
        clock.set(109_999_999);assertTrue(step(c,2,paused.yaw,0,170,0,o).stopped);
        clock.set(110_000_000);
        NScaffoldRotationController.Step resumed=step(c,3,paused.yaw,0,170,0,o);
        assertFalse(resumed.stopped);assertTrue(resumed.yaw>paused.yaw);
        NScaffoldRotationController unapplied=controller();
        for(int tick=0;tick<8;tick++)assertFalse(step(unapplied,tick,0,0,170,0,o).stopped);
    }

    @Test public void thresholdAttemptsSkipWholeThresholdCrossings() {
        NScaffoldRotationController c=controller();Options o=stopOptions();o.attempts=1;
        NScaffoldRotationController.Step first=step(c,0,0,0,170,0,o);
        NScaffoldRotationController.Step second=step(c,1,first.yaw,0,170,0,o);
        assertFalse(second.stopped);assertEquals(120,second.yaw,0);
        NScaffoldRotationController.Step third=step(c,2,second.yaw,0,240,0,o);
        assertTrue(third.stopped);assertEquals(second.yaw,third.yaw,0);
    }

    @Test public void urgentClutchCancelsAnActiveStopWithoutBreakingAxisCaps() {
        NScaffoldRotationController c=controller();Options o=stopOptions();o.pitchMin=o.pitchMax=10;
        NScaffoldRotationController.Step first=step(c,0,0,0,170,0,o);
        NScaffoldRotationController.Step paused=step(c,1,first.yaw,0,170,0,o);
        assertTrue(paused.stopped);
        NScaffoldRotationController.Step urgent=c.update(2,paused.yaw,0,170,60,false,true,o.settings());
        assertFalse(urgent.stopped);assertTrue(urgent.yaw>paused.yaw);assertTrue(urgent.pitch>0);
        assertTrue(urgent.yaw-paused.yaw<=60);assertTrue(urgent.pitch<=10);
    }

    @Test public void urgentAcquisitionBypassesSoftEasingAndInertiaButKeepsMaximumSpeeds() {
        Options o=new Options();o.smoothing=NScaffoldRotationController.Smoothing.RELATIVE;
        o.extra=.01;o.accelerate=true;o.yawAccel=o.pitchAccel=.9;
        o.yawMin=o.yawMax=12;o.pitchMin=o.pitchMax=6;
        NScaffoldRotationController.Step slow=step(controller(),0,0,0,100,50,o);
        NScaffoldRotationController.Step urgent=controller().update(0,0,0,100,50,false,true,o.settings());
        assertTrue(urgent.yaw>slow.yaw*100);assertTrue(urgent.pitch>slow.pitch*100);
        assertEquals(12*100/Math.hypot(100,50),urgent.yaw,.00001);
        assertEquals(6*50/Math.hypot(100,50),urgent.pitch,.00001);
        assertTrue(urgent.yaw<=12);assertTrue(urgent.pitch<=6);
    }

    @Test public void tinyDifferencesNeverCauseALateSnapOrFloatOvershoot() {
        Options o=new Options();o.smoothing=NScaffoldRotationController.Smoothing.RELATIVE;
        o.extra=.01;o.accelerate=true;o.yawAccel=o.pitchAccel=.9;
        o.yawMin=o.yawMax=o.pitchMin=o.pitchMax=.01;
        float start=45.123f,target=Math.nextAfter(start,Double.POSITIVE_INFINITY);
        NScaffoldRotationController.Step tiny=step(controller(),0,start,89.99999f,target,90,o);
        assertTrue(tiny.yaw>start);assertTrue(tiny.yaw<=target);assertTrue(tiny.pitch<=90);
        assertTrue((double)tiny.yaw-start<=.01);
        NScaffoldRotationController.Step bounded=controller().update(0,10,0,10.025f,0,false,true,o.settings());
        assertTrue(bounded.yaw<10.025f);assertTrue((double)bounded.yaw-10<=.01);
    }

    @Test public void floatRoundingAtDifferentCameraMagnitudesKeepsTheHardCap() {
        for(float yaw:new float[]{0,45.123f,-179.999f,99999f})
            for(double cap:new double[]{.01,.1,1,179.99,180}) {
                Options o=new Options();o.yawMin=o.yawMax=cap;
                NScaffoldRotationController.Step s=controller().update(0,yaw,0,yaw+123f,0,false,true,o.settings());
                assertNotNull(s);assertTrue(Float.isFinite(s.yaw));
                assertTrue("yaw="+yaw+", cap="+cap,Math.abs((double)s.yaw-yaw)<=cap);
                assertTrue(s.yaw<=yaw+123f);
            }
    }

    @Test public void clockRollbackCannotLeaveAnOldPausePermanentlyActive() {
        AtomicLong clock=new AtomicLong(100_000_000);
        NScaffoldRotationController c=new NScaffoldRotationController(()->.5,clock::get);Options o=stopOptions();
        NScaffoldRotationController.Step first=step(c,0,0,0,170,0,o);
        assertTrue(step(c,1,first.yaw,0,170,0,o).stopped);
        clock.set(1);
        NScaffoldRotationController.Step resumed=step(c,2,first.yaw,0,170,0,o);
        assertFalse(resumed.stopped);assertTrue(resumed.yaw>first.yaw);
    }

    @Test public void resetFromASupplierCannotPublishOrRepeatAnOldAttempt() {
        AtomicReference<NScaffoldRotationController> ref=new AtomicReference<>();
        AtomicBoolean once=new AtomicBoolean(true);
        NScaffoldRotationController c=new NScaffoldRotationController(()->{
            if(once.getAndSet(false))ref.get().reset();return .5;
        },()->0);ref.set(c);
        Options o=new Options();o.yawMin=10;o.yawMax=20;
        assertNull(step(c,0,0,0,90,0,o));assertNull(step(c,0,0,0,90,0,o));
        assertNotNull(step(c,1,0,0,90,0,o));
    }
}
