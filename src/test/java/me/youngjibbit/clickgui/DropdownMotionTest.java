package me.youngjibbit.clickgui;

import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Test;
import static org.junit.Assert.*;

public class DropdownMotionTest {
    @Test public void thirtySixty144And240FpsMatch() {
        ClientSettings c=new ClientSettings();
        for(String intensity:c.motionIntensity.getOptions()){
            c.motionIntensity.setValue(intensity);
            float reference=Float.NaN;
            for(int fps:new int[]{30,60,144,240}){
                DropdownMotion spring=new DropdownMotion();
                float result=1;
                for(int i=0;i<fps;i++)result=spring.step(false,1f/fps,c);
                if(Float.isFinite(reference))assertEquals(intensity,reference,result,.002f);
                reference=result;
            }
            assertEquals(0,reference,.001f);
        }
    }

    @Test public void closingReopensFromCurrentValueAcrossThirtyReversals() {
        DropdownMotion spring=new DropdownMotion();
        ClientSettings settings=new ClientSettings();
        float closing=spring.step(false,.035f,settings);
        assertTrue(closing>0&&closing<1);
        assertEquals(closing,spring.step(true,0,settings),0);
        float reopening=spring.step(true,.025f,settings);
        assertTrue(reopening>0&&reopening<=1);
        for(int i=0;i<30;i++){
            float value=spring.step(i%2==0?false:true,.009f,settings);
            assertTrue(Float.isFinite(value)&&value>=0&&value<=1);
        }
    }

    @Test public void disabledOrReducedMotionSnapsAndKeepsSettings() {
        ClientSettings settings=new ClientSettings();
        DropdownMotion spring=new DropdownMotion();
        spring.step(false,.025f,settings);
        settings.animations.setValue(false);
        assertEquals(0,spring.step(false,0,settings),0);
        assertEquals(1,spring.step(true,0,settings),0);
        settings.animations.setValue(true);
        settings.reducedMotion.setValue(true);
        assertEquals(0,spring.step(false,0,settings),0);
        assertEquals(1,spring.step(true,0,settings),0);
        assertEquals("Balanced",settings.motionIntensity.getValue());
    }

    @Test public void invalidTimeAndPauseDoNotShiftOrInventProgress() {
        DropdownMotion spring=new DropdownMotion();
        float before=spring.step(false,.03f,null);
        for(float dt:new float[]{0,-12,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            assertEquals(before,spring.step(true,dt,null),0);
        DropdownMotion a=new DropdownMotion(),b=new DropdownMotion();
        assertEquals(a.step(false,.05f,null),b.step(false,999f,null),.000001f);
        assertEquals(0,DropdownMotion.clamp(Float.NaN),0);
        assertEquals(0,DropdownMotion.clamp(Float.POSITIVE_INFINITY),0);
    }

    @Test public void resetDropsOldCategoryVelocityWithoutPersistingAnything() {
        DropdownMotion spring=new DropdownMotion();
        spring.step(false,.05f,null);
        spring.reset(true);
        assertEquals(1,spring.step(true,0,null),0);
        spring.reset(false);
        assertEquals(0,spring.step(false,0,null),0);
        assertTrue(spring.step(true,.03f,null)>0);
    }
}
