package me.youngjibbit.gui;

import org.junit.Test;
import java.lang.reflect.Field;
import static org.junit.Assert.*;

public class MainMenuMotionTest {
    @Test public void missingSettingsUseBalanced() { assertEquals(MenuMotion.BALANCED, MenuMotion.resolve(null)); }

    @Test public void presetsDriveDistinctBoundedLiftScaleAndGlow() {
        float previousLift=0, previousScale=1, previousGlow=0;
        for(MenuMotion preset:MenuMotion.values()) {
            assertTrue(preset.lift(1)>previousLift); assertTrue(preset.scale(1)>previousScale);
            assertTrue(preset.glow(1)>previousGlow);
            previousLift=preset.lift(1); previousScale=preset.scale(1); previousGlow=preset.glow(1);
            for(float value:new float[]{-100,-.45f,0,.55f,1,100,Float.NaN,Float.POSITIVE_INFINITY}) {
                assertTrue(Float.isFinite(preset.lift(value))); assertTrue(Math.abs(preset.lift(value))<=3);
                assertTrue(preset.scale(value)>=.97f && preset.scale(value)<=1.04f);
                assertTrue(preset.glow(value)>=0 && preset.glow(value)<=.4f);
            }
        }
    }
    @Test public void exactSpringMatchesAcrossFpsIncludingVelocity() throws Exception {
        for(MenuMotion preset:MenuMotion.values()) {
            double reference=Double.NaN, speed=0;
            for(int fps:new int[]{30,60,144,240}) {
                MenuSpring spring=new MenuSpring(); float value=0;
                for(int frame=0;frame<fps;frame++) value=spring.step(1,1f/fps,true,false,preset);
                if(!Double.isNaN(reference)){assertEquals(reference,value,.00001);assertEquals(speed,velocity(spring),.00001);}
                reference=value; speed=velocity(spring);
            }
        }
    }
    @Test public void interruptionStartsAtCurrentDisplayValueAndKeepsVelocity() throws Exception {
        MenuSpring spring=new MenuSpring();
        for(MenuMotion preset:MenuMotion.values()) for(float target:new float[]{1,-.45f,0,.55f,1,0}) {
            float value=spring.step(target,.037f,true,false,preset); double speed=velocity(spring);
            assertEquals(value,spring.step(1-target,0,true,false,preset),0); assertEquals(speed,velocity(spring),0);
            assertEquals(value,spring.step(target,.0000001f,true,false,preset),.00001);
        }
    }
    @Test public void invalidTimesAndLongPausesRemainFiniteAndClamped() {
        for(MenuMotion preset:MenuMotion.values()) for(boolean reduced:new boolean[]{false,true}) {
            for(float dt:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
                MenuSpring spring=new MenuSpring(); float start=spring.step(1,.04f,true,reduced,preset);
                assertEquals(start,spring.step(0,dt,true,reduced,preset),0);
                assertTrue(Float.isFinite(spring.step(1,.016f,true,reduced,preset)));
            }
            MenuSpring paused=new MenuSpring(),bounded=new MenuSpring();
            assertEquals(bounded.step(1,.25f,true,reduced,preset),paused.step(1,1000,true,reduced,preset),0);
        }
    }
    @Test public void reducedMotionIsFiniteLinearAndReversibleWithoutOvershoot() {
        for(MenuMotion preset:MenuMotion.values()) {
            MenuSpring spring=new MenuSpring();
            assertEquals(.25,spring.step(1,.03f,true,true,preset),.00001);
            assertEquals(.5,spring.step(1,.03f,true,true,preset),.00001);
            float value=spring.step(0,0,true,true,preset); assertEquals(.5,value,.00001);
            assertEquals(.375,spring.step(0,.03f,true,true,preset),.00001);
            assertEquals(0,spring.step(0,.09f,true,true,preset),.00001);
            assertEquals(1,spring.step(1,.12f,true,true,preset),0);
            assertEquals(1,spring.step(1,.12f,true,true,preset),0);
        }
    }
    @Test public void legacyApiRemainsBalancedAndAnimationOffSnaps() {
        MenuSpring old=new MenuSpring(),current=new MenuSpring();
        for(float target:new float[]{1,-.45f,.55f,0,1}) for(int i=0;i<20;i++)
            assertEquals(old.step(target,.016f,true),current.step(target,.016f,true,false,MenuMotion.BALANCED),0);
        for(MenuMotion preset:MenuMotion.values()) {
            assertEquals(-.45,current.step(-.45f,Float.NaN,false,true,preset),.00001);
            assertEquals(1,current.step(1,0,false,false,preset),0);
        }
        assertEquals(0,old.step(0,.01f,false),0);
    }
    @Test public void expressiveBouncesButReducedNeverBounces() {
        MenuSpring dynamic=new MenuSpring(),linear=new MenuSpring(); float peak=0;
        for(int i=0;i<100;i++) {
            peak=Math.max(peak,dynamic.step(1,.01f,true,false,MenuMotion.EXPRESSIVE));
            float value=linear.step(1,.01f,true,true,MenuMotion.EXPRESSIVE); assertTrue(value>=0 && value<=1);
        }
        assertTrue(peak>1.01 && peak<1.1);
    }
    private static double velocity(MenuSpring spring) throws Exception {
        Field field=MenuSpring.class.getDeclaredField("velocity");field.setAccessible(true);return field.getDouble(spring);
    }
}
