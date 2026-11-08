package me.youngjibbit.gui;

import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class HudSpringAndRevealTest {
    @Test public void preservesMenuSpringPositionAndVelocityAcrossReversals() throws Exception {
        HudSpring hud=new HudSpring();MenuSpring raw=new MenuSpring();hud.snap(0);
        for(float target:new float[]{1,0,1,0,1}){
            for(int i=0;i<8;i++)
                assertEquals(raw.step(target,.012f,true,false,MenuMotion.BALANCED),
                        hud.step(target,.012f,true,false,"Balanced"),.00001f);
            float at=hud.value();double velocity=velocity(hud);
            assertEquals(at,hud.step(1-target,0,true,false,"Balanced"),0);
            assertEquals(velocity,velocity(hud),0);
        }
    }
    @Test public void invalidTimesAndRapidRetargetsRemainFinite() {
        HudSpring spring=new HudSpring();spring.snap(0);
        for(int i=0;i<100;i++){
            float v=spring.step(i%2==0?1:0,.005f,true,false,"Expressive");
            assertTrue(Float.isFinite(v));assertTrue(v>-1&&v<2);
        }
        float before=spring.value();
        for(float dt:new float[]{Float.NaN,Float.NEGATIVE_INFINITY,-10,Float.POSITIVE_INFINITY})
            assertEquals(before,spring.step(1,dt,true,false,"Calm"),0);
        HudSpring a=new HudSpring(),b=new HudSpring();
        a.snap(0);b.snap(0);
        assertEquals(a.step(1,.05f,true,false,"Balanced"),
                b.step(1,1000,true,false,"Balanced"),.00001f);
    }
    @Test public void reducedAndAnimationsOffAreImmediateForValues() {
        HudSpring spring=new HudSpring();spring.snap(20);
        assertEquals(5,spring.step(5,0,false,false,"Expressive"),0);
        assertEquals(20,spring.step(20,0,true,true,"Balanced"),0);
        assertEquals(0,spring.step(0,0,false,false,"Calm"),0);
    }
    @Test public void revealBouncesWithoutBlockingInputAndClosesFully() {
        HudReveal motion=new HudReveal();
        HudReveal.Frame first=motion.step(true,0,true,false,"Expressive");
        assertEquals(0,first.opacity,0);
        assertFalse(first.drawn());
        float highestScale=0;
        for(int i=0;i<90;i++){
            HudReveal.Frame view=motion.step(true,.016f,true,false,"Expressive");
            highestScale=Math.max(highestScale,view.scale);
            assertTrue(view.opacity>=0&&view.opacity<=1);
            assertTrue(view.scale>=.82f&&view.scale<=1.085f);
            assertTrue(view.slideY>=-5&&view.slideY<=15);
        }
        assertTrue(highestScale>1.001f);
        HudReveal.Frame outgoing=null;
        for(int i=0;i<150;i++)outgoing=motion.step(false,.016f,true,false,"Expressive");
        assertFalse(outgoing.drawn());assertEquals(0,outgoing.opacity,0);
        HudReveal.Frame instant=motion.step(true,0,false,false,"Calm");
        assertTrue(instant.drawn());assertEquals(1,instant.opacity,0);
        assertEquals(0,motion.step(false,0,true,true,"Calm").opacity,0);
    }
    private static double velocity(HudSpring hud)throws Exception {
        Field fld=HudSpring.class.getDeclaredField("spring");fld.setAccessible(true);
        MenuSpring spring=(MenuSpring)fld.get(hud);
        fld=MenuSpring.class.getDeclaredField("velocity");fld.setAccessible(true);
        return fld.getDouble(spring);
    }
}
