package me.youngjibbit.gui;

import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Test;
import static org.junit.Assert.*;

public class UiSpringTest {
    @Test public void exactPresetAndLegacySpringParity() {
        ClientSettings c=new ClientSettings();
        for(String preset:c.motionIntensity.getOptions()){
            c.motionIntensity.setValue(preset);UiSpring ui=new UiSpring();MenuSpring old=new MenuSpring();
            for(boolean[] flags:new boolean[][]{{true,false,false},{false,true,false},{true,true,true},{false,false,false}})
                for(int i=0;i<20;i++){
                    float target=flags[2]?-.45f:flags[0]?1:flags[1]?.55f:0;
                    assertEquals(old.step(target,.016f,true,false,MenuMotion.resolve(c)),ui.step(.016f,flags[0],flags[1],flags[2],true,c),0);
                    assertEquals(MenuMotion.resolve(c).lift(ui.amount()),ui.lift(),0);assertEquals(MenuMotion.resolve(c).scale(ui.amount()),ui.scale(),0);assertEquals(MenuMotion.resolve(c).glow(ui.amount()),ui.glow(),0);
                }
        }
    }
    @Test public void thirtySixty144And360FpsHaveEqualResponseAfterEqualDuration() {
        ClientSettings c=new ClientSettings();for(String preset:c.motionIntensity.getOptions()){
            c.motionIntensity.setValue(preset);float prior=Float.NaN;
            for(int fps:new int[]{30,60,144,360}){UiSpring ui=new UiSpring();for(int i=0;i<fps;i++)ui.step(1f/fps,true,false,false,true,c);if(Float.isFinite(prior))assertEquals(prior,ui.amount(),.00001);prior=ui.amount();}
        }
    }
    @Test public void reducedIs120msLinearReversesAtCurrentVisualValue() {
        ClientSettings c=new ClientSettings();c.reducedMotion.setValue(true);UiSpring ui=new UiSpring();
        assertEquals(.25,ui.step(.03f,true,false,false,true,c),.00001);assertEquals(.5,ui.step(.03f,true,false,false,true,c),.00001);
        assertEquals(.5,ui.step(0,false,false,false,true,c),.00001);assertEquals(.375,ui.step(.03f,false,false,false,true,c),.00001);assertEquals(0,ui.step(.09f,false,false,false,true,c),.00001);
    }
    @Test public void animationOffSnapsAndAmbientOffStillSprings() {
        ClientSettings c=new ClientSettings();c.ambientMotion.setValue(false);UiSpring ui=new UiSpring();float a=ui.step(.03f,true,false,false,true,c);assertTrue(a>0&&a<1);
        c.animations.setValue(false);assertEquals(-.45,ui.step(0,true,true,true,true,c),.00001);assertEquals(1,ui.step(Float.NaN,true,false,false,true,c),0);
    }
    @Test public void disabledHasNoActiveAppearanceAndFocusIsImmediateEvenAtZeroDt() {
        ClientSettings c=new ClientSettings();UiSpring ui=new UiSpring();ui.step(.1f,true,true,true,true,c);ui.step(0,true,true,true,false,c);
        assertEquals(0,ui.amount(),0);assertFalse(ui.focused());assertFalse(ui.pressed());assertFalse(ui.enabled());
        ui.step(0,false,true,false,true,c);assertTrue(ui.focused());assertTrue(ui.enabled());assertEquals(0,ui.amount(),0);
    }
    @Test public void invalidDtPauseAndRapidReversalsStayFinite() {
        ClientSettings c=new ClientSettings();UiSpring ui=new UiSpring();float a=ui.step(.02f,true,false,false,true,c);
        for(float dt:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})assertEquals(a,ui.step(dt,false,false,false,true,c),0);
        UiSpring normal=new UiSpring(),paused=new UiSpring();assertEquals(normal.step(.25f,true,false,false,true,c),paused.step(900,true,false,false,true,c),0);
        for(int i=0;i<500;i++){ui.step(.005f,i%2==0,i%3==0,i%5==0,true,c);assertTrue(Float.isFinite(ui.amount()));assertTrue(Math.abs(ui.lift())<3.1);}
    }
    @Test public void resetClearsResponseFocusAndHeldState() {
        UiSpring ui=new UiSpring();ui.step(.1f,true,true,true,true,null);ui.reset();assertEquals(0,ui.amount(),0);assertFalse(ui.focused());assertFalse(ui.pressed());
        assertEquals(new MenuSpring().step(1,.02f,true),ui.step(.02f,true,false,false,true,null),0);
    }
}
