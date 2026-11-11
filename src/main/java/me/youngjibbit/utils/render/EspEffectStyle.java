package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL14;
import static org.lwjgl.opengl.GL11.*;

/** Analytic outlines use the same contour lighting as silhouette and UI compositors. */
public final class EspEffectStyle {
    private EspEffectStyle() { }
    public static void upload(ShaderUtil shader,Theme theme,String mode,boolean useTheme,float intensity) {
        Minecraft mc=Minecraft.getMinecraft();float scale=GuiResolution.current().getScaleFactor();
        ContourEffects.upload(shader,theme,mode,scale,ContourEffects.extent(),0,0,mc.displayWidth,mc.displayHeight,0,0,0);
        shader.setUniformi("useTheme",useTheme?1:0);
        shader.setUniformf("effectOpacity",Math.max(0,intensity));
    }
    public static void drawRectangle(ShaderUtil shader,Theme theme,String mode,float x,float y,float width,float height,int primary,int secondary) {
        drawRectangle(shader,theme,mode,x,y,width,height,primary,secondary,0,0,0);
    }
    public static void drawRectangle(ShaderUtil shader,Theme theme,String mode,float x,float y,float width,float height,
                                     int primary,int secondary,float motionX,float motionY,float energy) {
        if(shader==null||width<=0||height<=0||!Float.isFinite(x+y+width+height))return;
        // ESP and Nametags already supply untransformed overlay coordinates.
        // Re-projecting here repeats matrix readbacks for every visible entity.
        float scale=GuiResolution.current().getScaleFactor();
        float pxX=x*scale,pxY=y*scale,pxW=width*scale,pxH=height*scale;
        float extent=ContourEffects.extent(),padding=ContourEffects.padding(mode,extent);
        Minecraft mc=Minecraft.getMinecraft();
        try(RenderState state=new RenderState()) {
            state.screenSpace();state.bindTarget();state.restoreClip();
            GlStateManager.enableBlend();glEnable(GL_BLEND);GlStateManager.disableAlpha();glDisable(GL_ALPHA_TEST);
            GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);glDisable(GL_CULL_FACE);glDepthMask(false);
            glColorMask(true,true,true,true);GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            shader.init();
            try {
                ContourEffects.upload(shader,theme,mode,scale,extent,pxX,pxY,pxW,pxH,motionX,motionY,energy);
                ContourEffects.palette(shader,"fxPrimary",primary);ContourEffects.palette(shader,"fxSecondary",secondary);
                shader.setUniformf("effectResolution",mc.displayWidth,mc.displayHeight);
                shader.setUniformf("effectRect",pxX,pxY,pxW,pxH);
                shader.setUniformf("effectRadius",Math.min(2*scale,Math.min(pxW,pxH)*.5f));
                shader.setUniformf("fxOrbit",Math.min(2*scale,Math.min(pxW,pxH)*.5f),UiEffects.gradient(theme,UiEffects.Channel.GLOW).param2);
                shader.setUniformf("effectOpacity",1);
                ShaderUtil.drawQuads(x-padding,y-padding,width+padding*2,height+padding*2);
            } finally {shader.unload();}
        }
    }
}
