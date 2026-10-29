package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.module.impl.client.ClientSettings;
import org.junit.Test;
import java.awt.Color;
import static org.junit.Assert.*;

public class Vis02GradientTest {
    private static final String[] METHODS={"Flow","Ping-Pong","Pulse"};
    private static UiEffects.Gradient g(int mode,float phase){return new UiEffects.Gradient(1,mode,phase,.25f);}
    @Test public void methodsOnlyReplaceDynamicModesAndFlowRetainsModeTwo() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            for(int method=0;method<3;method++) {
                f.settings.gradientMethod.setValue(METHODS[method]);
                assertEquals(2+method,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).mode);
                f.settings.gradientMotion.setValue("Solid");assertEquals(0,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).mode);
                f.settings.gradientMotion.setValue("Static");assertEquals(1,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).mode);
                f.settings.gradientMotion.setValue("Theme");assertEquals(1,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).mode);
                f.settings.gradientMotion.setValue("Animated");
            }
        }
    }
    @Test public void flowExactlyMatchesOldCosineIncludingWrapAndLargeCoordinates() {
        for(float phase:new float[]{0,.1f,.5f,.999999f,1})for(float t:new float[]{-10,0,.0005f,.5f,1,1.5f,1000})
            assertEquals(.5f-.5f*(float)Math.cos(2*Math.PI*(t-phase)),g(2,phase).position(t,0,1,1),0);
    }
    @Test public void pingPongReversesSmoothlyAtBothTurningPoints() {
        for(float t:new float[]{0,.13f,.5f,.89f,1}) {
            assertEquals(g(3,.2f).position(t,0,1,1),g(3,.8f).position(t,0,1,1),.000001);
            for(float turning:new float[]{0,.5f,1}) {
                float a=g(3,turning-.0001f).position(t,0,1,1),b=g(3,turning).position(t,0,1,1),c=g(3,turning+.0001f).position(t,0,1,1);
                assertEquals(a,c,.000001);assertEquals(a,b,.000001);
            }
        }
        assertNotEquals(g(2,.2f).position(.15f,0,1,1),g(3,.2f).position(.15f,0,1,1),.01);
    }
    @Test public void pulseUsesCoherentWeightsAndRetainsOpaqueStopAlpha() {
        for(float t:new float[]{0,.13f,.5f,.89f,1}) {
            assertEquals(t,g(4,0).position(t,0,1,1),.000001);
            assertEquals(.5,g(4,.25f).position(t,0,1,1),.000001);
            assertEquals(1-t,g(4,.5f).position(t,0,1,1),.000001);
            // The unchanged legacy integer interpolator truncates at most one alpha unit.
            int alpha=Theme.interpolateRGB(0xff129bda,0xffed8c12,g(4,.1f).position(t,0,1,1))>>>24;
            assertTrue(alpha>=254&&alpha<=255);
        }
        assertNotEquals(g(2,.1f).position(.2f,0,1,1),g(4,.1f).position(.2f,0,1,1),.01);
    }
    @Test public void axesEdgesHalfPixelsAndAllModesAreFiniteAndBounded() {
        for(int mode=0;mode<5;mode++)for(float phase:new float[]{0,.1f,.5f,1})for(float t:new float[]{-10000,0,.5f,1,10000}) {
            float x=new UiEffects.Gradient(1,mode,phase,1).position(t,0,100,100);
            assertTrue(Float.isFinite(x)&&x>=0&&x<=1);
            assertEquals(x,new UiEffects.Gradient(2,mode,phase,1).position(0,t,100,100),0);
            assertEquals(new UiEffects.Gradient(-1,mode,phase,1).position(t,0,100,100),new UiEffects.Gradient(1,mode,phase,1).position(100-t,0,100,100),.00002);
            assertEquals(new UiEffects.Gradient(-2,mode,phase,1).position(0,t,100,100),new UiEffects.Gradient(-1,mode,phase,1).position(t,0,100,100),0);
            assertTrue(Float.isFinite(new UiEffects.Gradient(1,mode,phase,1).position(t,0,0,0)));
        }
    }
    @Test public void synchronizedChannelsShareMethodPhaseDirectionAndFillPolicy() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            for(String method:METHODS) {
                f.settings.gradientMethod.setValue(method);f.settings.direction.setValue("Up");f.advance(.3);
                UiEffects.Gradient text=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT);
                for(UiEffects.Channel c:UiEffects.Channel.values()) {
                    UiEffects.Gradient other=UiEffects.gradient(Theme.SynthWave,c);
                    assertEquals(text.mode,other.mode);assertEquals(text.phase,other.phase,0);assertEquals(-2,other.axis);
                }
            }
        }
    }
    @Test public void independentChannelsRetainOldSpeedDirectionAndMotionChoices() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            ClientSettings c=f.settings;c.synchronize.setValue(false);c.textMotion.setValue("Animated");c.textSpeed.setValue(.2);
            c.borderMotion.setValue("Static");c.borderDirection.setValue("Up");c.glowMotion.setValue("Animated");c.glowSpeed.setValue(.6);c.glowDirection.setValue("Left");
            UiEffects.beginFrame();c.gradientMethod.setValue("Ping-Pong");f.advance(.5);
            assertEquals(.1,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,.000001);
            assertEquals(.3,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW).phase,.000001);
            assertEquals(1,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.BORDER).mode);
            assertEquals(-2,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.BORDER).axis);
            assertEquals(-1,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.GLOW).axis);
        }
    }
    @Test public void ambientOffFreezesTimeAndPhaseButLeavesInteractiveResponse() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            f.advance(.3);float phase=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,time=UiEffects.time();
            f.settings.ambientMotion.setValue(false);f.tick(60);
            assertEquals(time,UiEffects.time(),0);assertEquals(phase,UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT).phase,0);
            assertTrue(UiEffects.motion());assertTrue(UiEffects.response(.016f,20)<1);
            f.settings.ambientMotion.setValue(true);f.tick(.02);assertEquals(time+.02,UiEffects.time(),.000001);
        }
    }
    @Test public void motionPriorityMatrixKeepsReducedAndAnimationOffContracts() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            for(boolean animation:new boolean[]{false,true})for(boolean reduced:new boolean[]{false,true})for(boolean ambient:new boolean[]{false,true}) {
                f.settings.animations.setValue(animation);f.settings.reducedMotion.setValue(reduced);f.settings.ambientMotion.setValue(ambient);
                float time=UiEffects.time();f.tick(.02);
                assertEquals(time+(animation&&!reduced&&ambient?.02:0),UiEffects.time(),.000001);
                assertEquals(animation&&!reduced,UiEffects.motion());
            }
        }
    }
    @Test public void reinitializationInvalidationThemeChangesAndScenesNeverResetClock() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            f.advance(.4);UiEffects.Gradient before=UiEffects.gradient(Theme.SynthWave,UiEffects.Channel.TEXT);
            UiEffects.invalidate();UiEffects.beginFrame();
            assertEquals(before.phase,UiEffects.gradient(Theme.Cherry,UiEffects.Channel.TEXT).phase,0);
            UiEffects.SurfacePolicy policy=UiEffects.surface(null,Theme.Cherry,true,false,false,1,-1);
            try(UiEffects.SceneContext scene=UiEffects.scene(0,0,false,Theme.Cherry,policy)) {
                UiEffects.independentScene(()->assertNull(UiEffects.scene()));assertSame(scene,UiEffects.scene());
                assertEquals(before.phase,UiEffects.gradient(Theme.Cherry,UiEffects.Channel.TEXT).phase,0);
            }
            assertNull(UiEffects.scene());assertEquals(.4,UiEffects.time(),.000001);
        }
    }
    @Test public void localLegacyRateAndCustomPaletteUsePausedCanonicalTime() throws Exception {
        try(Vis02TestContext f=new Vis02TestContext()) {
            me.youngjibbit.module.impl.client.ArrayListHUDModule owner=new me.youngjibbit.module.impl.client.ArrayListHUDModule();
            UiEffects.LocalSurface local=new UiEffects.LocalSurface(owner);local.source.setValue("Local");
            // An existing owner's optional legacy gradient_speed is deliberately preserved.
            me.youngjibbit.value.impl.SliderValue speed=new me.youngjibbit.value.impl.SliderValue("Gradient Speed",owner,2000.0,1,10000);
            f.advance(.4);UiEffects.Gradient override=local.gradient(Theme.SynthWave);
            assertEquals(2,override.mode);assertEquals(.2,override.phase,.000001);assertEquals(.5,override.speed,0);
            f.settings.ambientMotion.setValue(false);f.tick(30);assertEquals(override.phase,local.gradient(Theme.SynthWave).phase,0);
            Theme custom=Theme.custom("custom:00000000-0000-0000-0000-000000000042","Order",java.util.Arrays.asList(Color.RED,Color.BLUE),true,true,.3f,Color.BLACK,Color.WHITE,Color.GREEN,.7f,9,.8f,1);
            f.settings.gradientMotion.setValue("Theme");
            assertEquals(.12,UiEffects.gradient(custom,UiEffects.Channel.TEXT).phase,.000001);
            assertEquals(Color.RED,custom.getPrimaryGradientColor());assertEquals(Color.BLUE,custom.getSecondaryGradientColor());assertEquals(Color.WHITE,custom.getTextColor());
        }
    }
    @Test public void semanticResolverIsPureAndErrorCannotBecomeDecorative() {
        for(int decorative:new int[]{0xffffffff,0xff000000,0xffff00ff,0xff65e0ad}) {
            assertEquals(0xffff667f,UiEffects.semanticColor(UiEffects.SemanticRole.ERROR,decorative,false));
            assertEquals(0xffff667f,UiEffects.semanticColor(UiEffects.SemanticRole.ERROR,decorative,true));
            assertEquals(0xffffc969,UiEffects.semanticColor(UiEffects.SemanticRole.WARNING,decorative,true));
            assertEquals(0xff65e0ad,UiEffects.semanticColor(UiEffects.SemanticRole.SUCCESS,decorative,true));
            assertEquals(0xff71caff,UiEffects.semanticColor(UiEffects.SemanticRole.INFO,decorative,true));
        }
        assertNotEquals(UiEffects.semanticColor(UiEffects.SemanticRole.INFO,0xffff00ff,false),UiEffects.semanticColor(UiEffects.SemanticRole.INFO,0xffff00ff,true));
    }
}
