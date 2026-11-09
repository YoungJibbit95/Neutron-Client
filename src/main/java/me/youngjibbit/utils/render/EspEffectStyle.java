package me.youngjibbit.utils.render;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import net.minecraft.client.renderer.GlStateManager;
import static org.lwjgl.opengl.GL11.*;

/** Shared effect uniforms and procedural outlines; no per-entity framebuffer allocation. */
public final class EspEffectStyle {
    public static void upload(ShaderUtil shader, Theme theme, String mode, boolean useTheme, float intensity) {
        ClientSettings settings=ClientSettings.current();
        float rate=.18f, detail=.5f, segments=4f, strength=1f;
        if(settings!=null){
            if("Flame".equals(mode)){rate=settings.flameSpeed.getValue().floatValue();detail=settings.flameHeight.getValue().floatValue();}
            else if("Neon".equals(mode)){detail=settings.neonCore.getValue().floatValue();strength=settings.neonGlow.getValue().floatValue();}
            else if("Lines".equals(mode)){segments=settings.lineSegments.getValue().floatValue();detail=settings.lineTrail.getValue().floatValue();}
        }
        shader.setUniformf("phase",UiEffects.time()*rate);
        shader.setUniformf("speed",segments);
        shader.setUniformf("param2",detail);
        shader.setUniformf("intensity",Math.max(0,intensity*strength));
        shader.setUniformi("useTheme",useTheme?1:0);
        palette(shader,"primaryColor",theme.getPrimaryGradientRGB());
        palette(shader,"secondaryColor",theme.getSecondaryGradientRGB());
    }

    public static void drawRectangle(ShaderUtil shader, Theme theme, String mode,
                                     float x,float y,float width,float height,int primary,int secondary) {
        if(width<=0||height<=0)return;
        try(RenderState state=new RenderState()){
            GlStateManager.enableBlend();
            glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            shader.init();
            try{
                upload(shader,theme,mode,true,1);
                shader.setUniformi("rectangleMask",1);
                shader.setUniformf("rectSize",width,height);
                palette(shader,"primaryColor",primary);
                palette(shader,"secondaryColor",secondary);
                ShaderUtil.drawQuads(x,y,width,height);
            }finally{shader.unload();}
        }
    }
    private static void palette(ShaderUtil shader,String uniform,int rgb){
        shader.setUniformf(uniform,((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f);
    }
    private EspEffectStyle() { }
}
