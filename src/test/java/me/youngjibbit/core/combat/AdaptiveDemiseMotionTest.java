package me.youngjibbit.core.combat;

import me.youngjibbit.core.world.DemiseRotationEngine;
import me.youngjibbit.core.world.DemiseRotationSettings;
import org.junit.Test;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.Assert.*;

public class AdaptiveDemiseMotionTest {
    private AdaptiveDemiseSettings.Configuration settings(DemiseRotationEngine.Smoothing mode, float smoothing,
                                                         boolean acceleration, boolean stop, boolean distance) {
        return new AdaptiveDemiseSettings.Configuration(new DemiseRotationSettings.Snapshot(true,smoothing,mode,
                acceleration,.25f,.25f,false,180,180,180,180,DemiseRotationEngine.Correction.None,
                stop,50,180,1),distance,0,8,.5f);
    }

    private float[] frame(AdaptiveDemiseMotion motion, AtomicLong time, long sequence, long millis) {
        time.addAndGet(millis*1_000_000L);
        motion.framePre();motion.frameUpdate(0,0,.5f);
        return motion.target(sequence,time.get(),0,0,90,30,4,false);
    }

    @Test public void referenceLinearRelativeSmoothingAndDistanceControlsChangeRealFrameCandidates() {
        float[][] positions=new float[3][];int index=0;
        for(AdaptiveDemiseSettings.Configuration settings:new AdaptiveDemiseSettings.Configuration[]{
                settings(DemiseRotationEngine.Smoothing.Linear,.15f,false,false,false),
                settings(DemiseRotationEngine.Smoothing.Relative,.15f,false,false,false),
                settings(DemiseRotationEngine.Smoothing.Linear,.05f,false,false,false)}) {
            AtomicLong time=new AtomicLong(1_000_000_000L);
            AdaptiveDemiseMotion motion=new AdaptiveDemiseMotion(time::get,17,settings,0,0);
            assertArrayEquals(new float[]{0,0},motion.target(1,time.get(),0,0,90,30,4,false),0);
            positions[index++]=frame(motion,time,2,10);
        }
        assertTrue(positions[0][0]>positions[1][0]);assertTrue(positions[0][0]>positions[2][0]);
        AtomicLong time=new AtomicLong(1_000_000_000L);
        AdaptiveDemiseSettings.Configuration distance=settings(DemiseRotationEngine.Smoothing.Linear,.15f,false,false,true);
        assertEquals(200,distance.reduction(4),0);assertEquals(0,distance.reduction(9),0);
        AdaptiveDemiseMotion motion=new AdaptiveDemiseMotion(time::get,17,distance,0,0);
        motion.target(1,time.get(),0,0,90,30,4,false);
        assertArrayEquals("original distance formula can reduce the speed to zero",new float[]{0,0},frame(motion,time,2,10),0);
    }

    @Test public void aFrameUpdateNeedsPreAndCannotRunTwiceOrBankAHold() {
        AtomicLong time=new AtomicLong(1_000_000_000L);
        AdaptiveDemiseMotion motion=new AdaptiveDemiseMotion(time::get,9,
                settings(DemiseRotationEngine.Smoothing.Linear,.15f,true,false,false),0,0);
        motion.target(1,time.get(),0,0,90,30,4,false);motion.frameUpdate(0,0,.5f);
        assertArrayEquals(new float[]{0,0},motion.target(2,time.get(),0,0,90,30,4,false),0);
        float[] moved=frame(motion,time,3,17);assertTrue(moved[0]>0);motion.frameUpdate(0,0,.5f);
        assertArrayEquals(moved,motion.target(4,time.get(),0,0,90,30,4,false),0);
        motion.target(5,time.get(),5,2,5,2,4,true);
        time.addAndGet(100_000_000L);motion.framePre();motion.frameUpdate(0,0,.5f);
        assertArrayEquals(new float[]{5,2},motion.target(6,time.get(),5,2,5,2,4,true),0);
        assertTrue(motion.fresh(time.get()+150_000_000L));assertFalse(motion.fresh(time.get()+150_000_001L));
        assertFalse(motion.fresh(time.get()-1));
    }

    @Test public void originalShortStopDurationIsConsumedBeforeMotionResumes() {
        AtomicLong time=new AtomicLong(1_000_000_000L);
        AdaptiveDemiseMotion motion=new AdaptiveDemiseMotion(time::get,7,
                settings(DemiseRotationEngine.Smoothing.Linear,.15f,false,true,false),0,0);
        motion.target(1,time.get(),0,0,90,30,4,false);
        assertArrayEquals(new float[]{0,0},frame(motion,time,2,10),0);
        time.addAndGet(50_000_000L);motion.target(3,time.get(),0,0,90,30,4,false);
        assertTrue(frame(motion,time,4,10)[0]>0);
    }

    @Test public void preAimLeadUsesTicksAndRejectsTeleportDeltas() {
        assertArrayEquals(new double[]{0,0},PreAimPrediction.offset(.2,.1,0,0,0),0);
        assertArrayEquals(new double[]{.2,.2},PreAimPrediction.offset(.2,.1,.1,0,2),1e-9);
        assertArrayEquals(new double[]{0,0},PreAimPrediction.offset(3,Double.NaN,0,0,3),0);
        double[] bounded=PreAimPrediction.offset(2,2,-2,-2,3);
        assertEquals(1.5,Math.hypot(bounded[0],bounded[1]),1e-9);
    }
}
