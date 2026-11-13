package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import static org.lwjgl.opengl.GL11.*;

/**
 * One half-resolution nearest-contour field for the complete filled ESP mask.
 * RGBA8 stores two 16-bit coordinates: no floating render-target extension is required.
 * Two reusable targets and bounded jump steps serve every silhouette and line.
 */
public final class ContourMaskRenderer implements AutoCloseable {
    private final Framebuffer[] fields=new Framebuffer[2];
    private ShaderUtil seed,jump,composite;
    private int width,height;

    public void draw(Theme theme,String mode,int maskTexture,int width,int height,float scale,float intensity,
                     boolean useTheme,float motionX,float motionY,float energy) {
        draw(theme,mode,maskTexture,width,height,scale,ContourEffects.extent(),intensity,useTheme,motionX,motionY,energy);
    }
    public void draw(Theme theme,String mode,int maskTexture,int width,int height,float scale,float extent,float intensity,
                     boolean useTheme,float motionX,float motionY,float energy) {
        if(maskTexture<=0||width<=0||height<=0||ContourEffects.mode(mode)==0||intensity<=0||!UiEffects.effectsSupported())return;
        try(RenderState state=new RenderState()) {
            state.screenSpace();
            GlStateManager.disableBlend();glDisable(GL_BLEND);glDisable(GL_DITHER);glDisable(GL_CULL_FACE);
            glDepthMask(false);glColorMask(true,true,true,true);GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            ensure(width,height);
            target(fields[0]);texture(0,maskTexture);seed.init();
            seed.setUniformi("sourceMask",0);seed.setUniformf("sourceResolution",width,height);
            ShaderUtil.drawQuads();seed.unload();
            if(RenderDiagnostics.enabled)RenderDiagnostics.maskPasses++;
            int current=0;
            int radius=Math.max(1,(int)Math.ceil(ContourEffects.fieldPadding(mode,extent)*Math.max(.25f,scale)/2));
            int step=1;while(step<radius&&step<1024)step<<=1;
            jump.init();jump.setUniformi("nearestField",0);jump.setUniformf("fieldResolution",fields[0].framebufferWidth,fields[0].framebufferHeight);
            jump.setUniformf("sourceResolution",width,height);
            for(;step>=1;step>>=1) {
                int next=1-current;target(fields[next]);texture(0,fields[current].framebufferTexture);
                jump.setUniformf("jumpStep",step);ShaderUtil.drawQuads();current=next;
            }
            jump.unload();
            state.bindTarget();state.screenSpace();state.restoreClip();state.restoreColorMask();
            GlStateManager.enableBlend();glEnable(GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GL14.glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            texture(0,fields[current].framebufferTexture);texture(1,maskTexture);
            composite.init();
            try {
                ContourEffects.upload(composite,theme,mode,scale,extent,0,0,width,height,motionX,motionY,energy);
                composite.setUniformi("nearestField",0);composite.setUniformi("sourceMask",1);
                composite.setUniformf("sourceResolution",width,height);
                composite.setUniformf("fieldResolution",fields[0].framebufferWidth,fields[0].framebufferHeight);
                composite.setUniformf("effectOpacity",Math.max(0,intensity));composite.setUniformi("useTheme",useTheme?1:0);
                ShaderUtil.drawQuads();
                if(RenderDiagnostics.enabled)RenderDiagnostics.composites++;
            } finally {composite.unload();}
        }
    }
    private void ensure(int w,int h) {
        if(seed==null)seed=new ShaderUtil("pulsar/shader/esp/contourSeed.frag");
        if(jump==null)jump=new ShaderUtil("pulsar/shader/esp/contourJump.frag");
        if(composite==null)composite=new ShaderUtil("pulsar/shader/esp/contourMask.frag");
        if(width!=w||height!=h) {releaseFields();width=w;height=h;}
        for(int i=0;i<fields.length;i++)if(fields[i]==null) {
            fields[i]=new Framebuffer((w+1)/2,(h+1)/2,false);
            fields[i].setFramebufferFilter(GL_NEAREST);
            texture(0,fields[i].framebufferTexture);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        }
    }
    private static void target(Framebuffer f) {
        f.bindFramebuffer(true);glDisable(GL_SCISSOR_TEST);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
    }
    private static void texture(int unit,int id) {
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0+unit);GL13.glActiveTexture(GL13.GL_TEXTURE0+unit);
        GlStateManager.bindTexture(id);glBindTexture(GL_TEXTURE_2D,id);
    }
    private void releaseFields() {
        for(int i=0;i<fields.length;i++)if(fields[i]!=null){fields[i].deleteFramebuffer();fields[i]=null;}
    }
    @Override public void close() {
        if(seed==null&&jump==null&&composite==null&&fields[0]==null&&fields[1]==null)return;
        try(RenderState state=new RenderState()) {
            releaseFields();width=height=0;
            if(seed!=null)seed.close();if(jump!=null)jump.close();if(composite!=null)composite.close();
            seed=jump=composite=null;
        }
    }
}
