package me.youngjibbit.utils.render;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL14;

import static org.lwjgl.opengl.GL11.*;

/** Shared, bounded contour lighting. Distances/positions in the shader are physical pixels. */
public final class ContourEffects {
    private static ShaderUtil uiRectShader;
    private ContourEffects() { }

    public static int mode(String name) {
        return "Flame".equals(name)?1:"Neon".equals(name)?2:"Lines".equals(name)?3:0;
    }
    public static String uiMode() {
        ClientSettings c=ClientSettings.current();
        String name=c==null?"Standard":c.uiGlowMode.getValue();
        return "Flame".equals(name)||"Neon".equals(name)?name:"Standard";
    }
    public static boolean specialUi() { return UiEffects.glowEnabled()&&mode(uiMode())!=0; }
    public static float extent() { return UiEffects.glowRadius(9); }
    public static float padding(String name,float logicalExtent) {
        float e=finite(logicalExtent,9,0.5f,64);
        ClientSettings c=ClientSettings.current();
        if(mode(name)==3) {
            float count=c==null?4:finite(c.lineSegments.getValue().floatValue(),4,1,16);
            float spacing=c==null?3:finite(c.lineSpacing.getValue().floatValue(),3,2,8);
            return (count-1)*spacing+5;
        }
        // Includes maximum wind and buoyancy. Text callers use their own compact extent.
        return mode(name)==1?e*8+4:e*2.7f+5;
    }
    /** Provision all supported Lines contours without adding passes when count changes. */
    public static float fieldPadding(String name,float logicalExtent) {
        if(mode(name)!=3)return padding(name,logicalExtent);
        ClientSettings c=ClientSettings.current();
        float spacing=c==null?3:finite(c.lineSpacing.getValue().floatValue(),3,2,8);
        return 15*spacing+5;
    }
    public static void upload(ShaderUtil s,Theme theme,String name,float scale,float logicalExtent,
                              float pxX,float pxY,float pxW,float pxH,
                              float motionX,float motionY,float energy) {
        ClientSettings c=ClientSettings.current();
        int m=mode(name);s.setUniformi("fxMode",m);
        if(m==0)return;
        theme=Theme.resolve(theme);
        scale=finite(scale,1,.25f,8);
        float e=finite(logicalExtent,9,.5f,64)*scale;
        float core=c==null?.5f:finite(c.neonCore.getValue().floatValue(),.5f,0,1);
        float height=c==null?.5f:finite(c.flameHeight.getValue().floatValue(),.5f,0,1);
        float rate=c==null?.18f:finite(c.flameSpeed.getValue().floatValue(),.18f,.02f,2);
        float response=c==null?.65f:finite(c.flameMotion.getValue().floatValue(),.65f,0,1);
        float strength=c==null?1:finite(c.neonGlow.getValue().floatValue(),1,0,2);
        float count=c==null?4:finite(c.lineSegments.getValue().floatValue(),4,1,16);
        float gap=c==null?3:finite(c.lineSpacing.getValue().floatValue(),3,2,8);
        float trail=c==null?.5f:finite(c.lineTrail.getValue().floatValue(),.5f,0,1);
        boolean moving=UiEffects.motion()&&UiEffects.options().ambient;
        s.setUniformf("fxScale",scale);
        s.setUniformf("fxTime",UiEffects.time());
        s.setUniformf("fxShape",pxX,pxY,Math.max(scale,pxW),Math.max(scale,pxH));
        s.setUniformf("fxStyle",e,(.65f+1.75f*core)*scale,Math.round(count),gap*scale);
        s.setUniformf("fxTuning",e*(.55f+3.0f*height),rate,strength,trail);
        s.setUniformf("fxFlow",moving?finite(motionX,0,-1,1):0,moving?finite(motionY,0,-1,1):0,
                moving?finite(energy,0,0,1):0,response);
        palette(s,"fxPrimary",theme.getPrimaryGradientRGB());
        palette(s,"fxSecondary",theme.getSecondaryGradientRGB());
        UiEffects.Gradient gradient=UiEffects.gradient(theme,UiEffects.Channel.GLOW);
        s.setUniformf("fxGradient",gradient.axis,UiEffects.options().gradients?gradient.mode:0,gradient.phase);
        s.setUniformf("fxOrbit",UiEffects.options().roundness*scale,gradient.param2);
    }
    public static void palette(ShaderUtil s,String name,int rgb) {
        s.setUniformf(name,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);
    }
    /** Geometry is already projected by the bloom queue; caller restores its saved scissor. */
    public static void drawUiRect(float pxX,float pxY,float pxW,float pxH,float pxRadius,int argb,
                                  boolean fixedColor,float motionX,float motionY,float energy) {
        if(!specialUi()||pxW<=0||pxH<=0||!UiEffects.effectsSupported())return;
        if(uiRectShader==null)uiRectShader=new ShaderUtil("pulsar/shader/esp/contourRect.frag");
        Minecraft mc=Minecraft.getMinecraft();
        float scale=GuiResolution.current().getScaleFactor();
        String style=uiMode();float extent=extent(),pad=padding(style,extent)*scale;
        try(RenderState state=new RenderState()) {
            state.screenSpace();state.restoreClip();
            GlStateManager.enableBlend();glEnable(GL_BLEND);glDisable(GL_CULL_FACE);glDepthMask(false);
            glColorMask(true,true,true,true);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            uiRectShader.init();
            try {
                upload(uiRectShader,Theme.getDefaultTheme(),style,scale,extent,pxX,pxY,pxW,pxH,motionX,motionY,energy);
                UiEffects.upload(uiRectShader,Theme.getDefaultTheme());
                uiRectShader.setUniformf("uiOrbitBounds",pxX,pxY,pxW,pxH);
                uiRectShader.setUniformf("uiGradientRadius",Math.max(0,pxRadius));
                if(fixedColor){palette(uiRectShader,"fxPrimary",argb);palette(uiRectShader,"fxSecondary",argb);}
                uiRectShader.setUniformf("effectResolution",mc.displayWidth,mc.displayHeight);
                uiRectShader.setUniformf("effectRect",pxX,pxY,pxW,pxH);
                uiRectShader.setUniformf("effectRadius",Math.max(0,pxRadius));
                uiRectShader.setUniformf("effectOpacity",UiEffects.glowAlpha(((argb>>>24)&255)/255f));
                ShaderUtil.drawQuads((pxX-pad)/scale,(pxY-pad)/scale,(pxW+2*pad)/scale,(pxH+2*pad)/scale);
            } finally {uiRectShader.unload();}
        }
    }
    public static void releaseResources() {if(uiRectShader!=null){uiRectShader.close();uiRectShader=null;}}
    private static float finite(float v,float fallback,float min,float max) {
        return Float.isFinite(v)?Math.max(min,Math.min(max,v)):fallback;
    }
}
