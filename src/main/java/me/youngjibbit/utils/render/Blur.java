package me.youngjibbit.utils.render;

import net.minecraft.client.Minecraft;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;

/** Legacy start/end API backed by a color mask, without consuming the caller's stencil buffer. */
public final class Blur {
    private static ShaderUtil composite;
    private static final Framebuffer[] buffers=new Framebuffer[4];
    private static RenderState state;
    private static int depth;
    public static boolean isCapturing() { return depth>0; }
    public static void startBlur() {
        if(depth++>0)return;
        state=new RenderState();Minecraft mc=Minecraft.getMinecraft();
        if(buffers[0]==null||buffers[0].framebufferWidth!=mc.displayWidth||buffers[0].framebufferHeight!=mc.displayHeight) {
            for(int i=0;i<4;i++){if(buffers[i]!=null)buffers[i].deleteFramebuffer();buffers[i]=new Framebuffer(mc.displayWidth,mc.displayHeight,false);buffers[i].setFramebufferFilter(GL_LINEAR);}
        }
        state.bindTarget();bind(0,buffers[1].framebufferTexture);
        glCopyTexSubImage2D(GL_TEXTURE_2D,0,0,0,0,0,mc.displayWidth,mc.displayHeight);
        buffers[0].bindFramebuffer(true);glDisable(GL_SCISSOR_TEST);glDisable(GL_STENCIL_TEST);
        glColorMask(true,true,true,true);GlStateManager.colorMask(true,true,true,true);
        glClearColor(0,0,0,0);GlStateManager.clearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
        if(state.enabledScissor())state.restoreClip();
        glDisable(GL_DEPTH_TEST);GlStateManager.disableDepth();
    }
    public static void endBlur(float radius,float compression) {
        if(depth==0||--depth>0)return;
        Minecraft mc=Minecraft.getMinecraft();
        try {
            if(!UiEffects.blurEnabled()&&!UiEffects.glass())return;
            screen();
            float r=UiEffects.options().configured?UiEffects.blurRadius(radius)*new ScaledResolution(mc).getScaleFactor():radius*compression;
            if(UiEffects.blurEnabled())GaussianBlur.renderScene(buffers[2],buffers[3],buffers[1].framebufferTexture,r);
            state.bindTarget();state.restoreClip();
            GlStateManager.enableBlend();glEnable(GL_BLEND);GlStateManager.blendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            if(RenderDiagnostics.enabled)RenderDiagnostics.composites++;
            if(composite==null)composite=new ShaderUtil("mask");composite.init();
            UiEffects.upload(composite,me.youngjibbit.utils.Theme.getDefaultTheme());composite.setUniformi("glass",UiEffects.glass()?1:0);composite.setUniformi("snapshot",2);composite.setUniformf("resolution",mc.displayWidth,mc.displayHeight);composite.setUniformf("glassStyle",UiEffects.options().refraction,UiEffects.options().tint,UiEffects.options().edge);java.awt.Color palette=me.youngjibbit.utils.Theme.getDefaultTheme().getPrimaryGradientColor();composite.setUniformf("tintColor",palette.getRed()/255f,palette.getGreen()/255f,palette.getBlue()/255f);composite.setUniformi("u_texture",0);composite.setUniformi("u_texture2",1);
            bind(0,buffers[0].framebufferTexture);bind(1,buffers[UiEffects.blurEnabled()?3:1].framebufferTexture);bind(2,buffers[1].framebufferTexture);ShaderUtil.drawQuads();composite.unload();
        } finally {state.close();state=null;}
    }
    public static void releaseResources(){if(state!=null){state.close();state=null;}depth=0;try(RenderState guard=new RenderState()){for(int i=0;i<buffers.length;i++)if(buffers[i]!=null){buffers[i].deleteFramebuffer();buffers[i]=null;}if(composite!=null){composite.close();composite=null;}}}
    private static void screen(){Minecraft mc=Minecraft.getMinecraft();ScaledResolution sr=new ScaledResolution(mc);glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);glMatrixMode(GL_MODELVIEW);glLoadIdentity();glDisable(GL_DEPTH_TEST);GlStateManager.disableDepth();glDisable(GL_STENCIL_TEST);glDisable(GL_SCISSOR_TEST);glDisable(GL_ALPHA_TEST);GlStateManager.disableAlpha();glDisable(GL_BLEND);GlStateManager.disableBlend();}
    private static void bind(int unit,int texture){GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);GlStateManager.bindTexture(texture);glBindTexture(GL_TEXTURE_2D,texture);}
}
