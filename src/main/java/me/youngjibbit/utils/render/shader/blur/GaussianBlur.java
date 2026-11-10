package me.youngjibbit.utils.render.shader.blur;

import me.youngjibbit.utils.render.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL11.*;

/** Shared separable pass. Callers own scene snapshots, masks and framebuffers; this class owns only two programs. */
public final class GaussianBlur {
    private static ShaderUtil scene,halo,kawase;
    private static final GaussianKernelCache kernels=new GaussianKernelCache();
    private static int sceneKernelRadius=-1,haloKernelRadius=-1;
    private GaussianBlur(){}
    /** Scene-only selection. Caller retains snapshot and two full-size ping-pong targets. */
    public static void renderScene(Framebuffer scratch,Framebuffer result,int input,float radius){
        if(scratch.framebufferTexture==input||result.framebufferTexture==input||scratch==result)throw new IllegalArgumentException("Scene blur requires distinct input and outputs");
        // No callbacks occur between these owned passes: preserve the caller once per scene.
        try(RenderState state=new RenderState()){
            state.screenSpace();
            if(!UiEffects.options().kawase){
                int r=Math.max(1,Math.min(127,Math.round(radius)));
                prepareGaussianOutput(scratch,input);drawGaussian(scratch,1,0,r,false,1);
                prepareGaussianOutput(result,scratch.framebufferTexture);drawGaussian(result,0,1,r,false,1);
                return;
            }
            int passes=2*UiEffects.options().quality;
            for(int i=0;i<passes;i++){Framebuffer target=(i&1)==0?scratch:result;kawasePass(target,input,Math.max(.5f,radius*(i+1)/passes*.45f));input=target.framebufferTexture;}
        }
    }
    private static void kawasePass(Framebuffer output,int input,float offset){
        output.bindFramebuffer(true);GlStateManager.disableBlend();glDisable(GL_BLEND);GlStateManager.disableCull();glDisable(GL_CULL_FACE);GlStateManager.colorMask(true,true,true,true);glColorMask(true,true,true,true);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.bindTexture(input);glBindTexture(GL_TEXTURE_2D,input);
        if(kawase==null)kawase=new ShaderUtil("Flauxy/Shaders/kawaseScene.frag");kawase.init();kawase.setUniformi("textureIn",0);kawase.setUniformf("stepSize",offset/output.framebufferWidth,offset/output.framebufferHeight);ShaderUtil.drawQuads();kawase.unload();if(RenderDiagnostics.enabled)RenderDiagnostics.blurPasses++;
    }
    public static void renderPass(Framebuffer output,int input,float dx,float dy,int radius,boolean glow,float exposure){
        try (RenderState state = new RenderState()) {
            state.screenSpace();
            prepareGaussianOutput(output, input);
            drawGaussian(output, dx, dy, radius, glow, exposure);
        }
    }

    /** The cleared mask has support only inside these top-left-origin pixel bounds. */
    public static void renderMaskGlow(Framebuffer scratch, Framebuffer result, int mask, int radius, float exposure,
                                      float left, float top, float right, float bottom) {
        radius = Math.max(1, Math.min(127, radius));
        // Include the linear-filter footprint and round outward at fractional GUI coordinates.
        int x0 = Math.max(0, (int)Math.floor(left - radius - 2));
        int x1 = Math.min(result.framebufferWidth, (int)Math.ceil(right + radius + 2));
        int y0 = Math.max(0, (int)Math.floor(top - radius - 2));
        int y1 = Math.min(result.framebufferHeight, (int)Math.ceil(bottom + radius + 2));
        int width = Math.max(0, x1 - x0);
        int height = Math.max(0, y1 - y0);

        try (RenderState state = new RenderState()) {
            state.screenSpace();
            // Both targets match the mask size. Keep the full quad and its original UVs.
            glScissor(x0, result.framebufferHeight - y1, width, height);
            prepareGaussianOutput(scratch, mask);
            glEnable(GL_SCISSOR_TEST);
            drawGaussian(scratch, 1, 0, radius, true, exposure);
            glDisable(GL_SCISSOR_TEST);
            prepareGaussianOutput(result, scratch.framebufferTexture);
            glEnable(GL_SCISSOR_TEST);
            drawGaussian(result, 0, 1, radius, true, exposure);
        }
    }

    private static void prepareGaussianOutput(Framebuffer output, int input) {
        output.bindFramebuffer(true);
        GlStateManager.disableBlend();
        GlStateManager.disableCull();
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        GlStateManager.colorMask(true, true, true, true);
        glColorMask(true, true, true, true);
        GlStateManager.clearColor(0, 0, 0, 0);
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.enableTexture2D();
        glEnable(GL_TEXTURE_2D);
        GlStateManager.bindTexture(input);
        glBindTexture(GL_TEXTURE_2D, input);
    }

    private static void drawGaussian(Framebuffer output, float dx, float dy, int radius, boolean glow, float exposure) {
        radius = Math.max(1, Math.min(127, radius));
        ShaderUtil shader;
        if(glow){if(halo==null)halo=new ShaderUtil("Flauxy/Shaders/glow.frag");shader=halo;}
        else{if(scene==null)scene=new ShaderUtil("Flauxy/Shaders/gaussian.frag");shader=scene;}
        shader.init();shader.setUniformi("textureIn",0);shader.setUniformf("texelSize",1f/output.framebufferWidth,1f/output.framebufferHeight);shader.setUniformf("direction",dx,dy);shader.setUniformf("radius",radius);
        // Uniform values belong to the linked program, not to a framebuffer or draw call.
        // The second direction and subsequent frames reuse the exact uploaded kernel.
        if((glow?haloKernelRadius:sceneKernelRadius)!=radius){
            GL20.glUniform1(shader.getUniform("weights"),kernels.get(radius));
            if(glow)haloKernelRadius=radius;else sceneKernelRadius=radius;
        }
        if(glow){shader.setUniformi("avoidTexture",0);shader.setUniformf("color",1,1,1);shader.setUniformf("exposure",exposure);}
        ShaderUtil.drawQuads();shader.unload();if(RenderDiagnostics.enabled)RenderDiagnostics.blurPasses++;

    }
    public static void releaseResources(){if(scene!=null){scene.close();scene=null;}if(halo!=null){halo.close();halo=null;}if(kawase!=null){kawase.close();kawase=null;}sceneKernelRadius=haloKernelRadius=-1;}
}
