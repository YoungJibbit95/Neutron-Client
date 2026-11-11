package me.youngjibbit.module.impl.render;

import org.junit.Test;
import static org.junit.Assert.*;

/** Observed transitions produce bounded, settling feedback; preferences suppress it immediately. */
public class CompactHudMotionTest {
    @Test public void firstObservationAndRepeatedIdleValuesNeverPulse(){
        CompactHudMotion motion=new CompactHudMotion();
        for(int i=0;i<300;i++)assertEquals(0,motion.step(12,.016f,true,false,"Expressive"),0);
    }
    @Test public void changesAnimateAndSettleAtSeveralFrameRates(){
        for(float dt:new float[]{1f/30,1f/60,1f/144}){
            CompactHudMotion motion=new CompactHudMotion();motion.step(10,dt,true,false,"Balanced");
            float first=motion.step(11,dt,true,false,"Balanced");assertTrue(first>0&&first<=1);
            float value=first;for(int i=0;i<Math.ceil(3/dt);i++){
                value=motion.step(11,dt,true,false,"Balanced");assertTrue(Float.isFinite(value)&&value>=0&&value<=1);
            }assertEquals(0,value,.001f);
        }
    }
    @Test public void preferencesAndResetDiscardResidualMotion(){
        CompactHudMotion motion=new CompactHudMotion();motion.step(0,.016f,true,false,"Balanced");
        assertTrue(motion.step(1,.016f,true,false,"Balanced")>0);
        assertEquals(0,motion.step(2,.016f,true,true,"Balanced"),0);
        assertEquals(0,motion.step(2,.016f,true,false,"Balanced"),0);
        assertEquals(0,motion.step(3,.016f,false,false,"Balanced"),0);
        motion.reset();assertEquals(0,motion.step(900,.016f,true,false,"Balanced"),0);
    }
    @Test public void elapsedClockAndLargeCountersRemainFactualAndBounded(){
        assertEquals(.25f,CompactHudMotion.fraction(15_000_000_000L,60_000_000_000L),.000001f);
        assertEquals(.25f,CompactHudMotion.fraction(75_000_000_000L,60_000_000_000L),.000001f);
        assertEquals(0,CompactHudMotion.fraction(-1,60),0);assertEquals(0,CompactHudMotion.fraction(100,0),0);
        assertEquals("999",CompactHudMotion.count(999));assertEquals("1k",CompactHudMotion.count(1000));
        assertEquals("12.3k",CompactHudMotion.count(12345));assertEquals("2.1b",CompactHudMotion.count(Integer.MAX_VALUE));
    }
}
