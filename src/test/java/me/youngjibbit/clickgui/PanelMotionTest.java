package me.youngjibbit.clickgui;

import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Test;
import static org.junit.Assert.*;

/** Pure per-frame motion; native GUI/GL are separate acceptance gates. */
public class PanelMotionTest {
    @Test public void categoryIsFrameRateIndependentAndReversible() {
        ClientSettings c=new ClientSettings();
        for(String preset:c.motionIntensity.getOptions()){
            c.motionIntensity.setValue(preset);
            float reference=Float.NaN;
            for(int fps:new int[]{30,60,144,240}){
                PanelMotion motion=new PanelMotion();motion.reset(24);
                float result=24;
                for(int i=0;i<fps;i++) result=motion.category(168,1f/fps,c);
                if(Float.isFinite(reference)) assertEquals(preset+" "+fps,reference,result,.003);
                reference=result;
            }
            assertEquals(168,reference,.02);
        }
        PanelMotion motion=new PanelMotion();motion.reset(0);
        float open=motion.category(168,.035f,c);
        assertTrue(open>0&&open<168);
        assertEquals(open,motion.category(0,0,c),0);
        float back=motion.category(0,.02f,c);
        assertTrue(Float.isFinite(back));
        for(int i=0;i<60;i++)
            assertTrue(Float.isFinite(motion.category(i%2==0?0:168,.008f,c)));
    }

    @Test public void entranceAndRetreatReverseWithoutResettingVelocity() {
        ClientSettings c=new ClientSettings();
        PanelMotion motion=new PanelMotion();motion.reset(12);
        assertEquals(0,motion.entrance(0,c),0);
        float entering=motion.entrance(.032f,c);
        assertTrue(entering>0&&entering<1);
        assertEquals(entering,motion.retreat(0,c),0);
        float retreat=motion.retreat(.022f,c);
        assertTrue(Float.isFinite(retreat));
        assertEquals(retreat,motion.entrance(0,c),0);
        for(int i=0;i<20;i++){
            float response=i%2==0?motion.retreat(.01f,c):motion.entrance(.01f,c);
            assertTrue(response>=0&&response<=1);
        }
    }

    @Test public void animationsOffAndReducedMotionSnapAndDoNotWriteSettings() {
        ClientSettings c=new ClientSettings();
        PanelMotion motion=new PanelMotion();motion.reset(0);
        motion.category(100,.02f,c);
        c.animations.setValue(false);
        assertEquals(100,motion.category(100,0,c),0);
        assertEquals(1,motion.entrance(0,c),0);
        assertEquals(0,motion.retreat(0,c),0);
        c.animations.setValue(true);
        c.reducedMotion.setValue(true);
        assertEquals(0,motion.category(0,0,c),0);
        assertEquals(1,motion.entrance(0,c),0);
        assertEquals("Balanced",c.motionIntensity.getValue());
        assertEquals(5.0,c.roundness.getValue(),0);
    }

    @Test public void invalidClockCannotMoveCoordinatesOrCreateNaN() {
        PanelMotion motion=new PanelMotion();motion.reset(32);
        float before=motion.category(400,0,null);
        for(float bad:new float[]{Float.NaN,Float.POSITIVE_INFINITY,-10,Float.NEGATIVE_INFINITY})
            assertEquals(before,motion.category(400,bad,null),0);
        PanelMotion a=new PanelMotion(),b=new PanelMotion();
        a.reset(0);b.reset(0);
        assertEquals(a.category(200,.05f,null),b.category(200,2000f,null),.00001);
    }

    @Test public void resetIsFreshAndLegacyRemainsTheDefaultMode() {
        PanelMotion motion=new PanelMotion();motion.reset(18);
        motion.entrance(.08f,null);
        motion.category(100,.08f,null);
        motion.reset(70);
        assertEquals(70,motion.category(70,0,null),0);
        assertEquals(0,motion.entrance(0,null),0);
        assertEquals(me.youngjibbit.theme.ThemeMode.LEGACY,
                me.youngjibbit.theme.ThemeMode.LEGACY);
    }
}
