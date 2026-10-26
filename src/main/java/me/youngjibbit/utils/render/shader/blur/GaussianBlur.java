package me.youngjibbit.utils.render.shader.blur;

import me.youngjibbit.utils.render.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;

/** Shared separable pass. Callers own scene snapshots, masks and framebuffers; this class owns only two programs. */
public final class GaussianBlur {
    private static ShaderUtil scene,halo,kawase;
    private static final FloatBuffer weights=BufferUtils.createFloatBuffer(256);
    private static int kernelRadius=-1;
    private GaussianBlur(){}
    /** Scene-only selection. Caller retains snapshot and two full-size ping-pong targets. */
    public static void renderScene(Framebuffer scratch,Framebuffer result,int input,float radius){
        if(scratch.framebufferTexture==input||result.framebufferTexture==input||scratch==result)throw new IllegalArgumentException("Scene blur requires distinct input and outputs");
        if(!UiEffects.options().kawase){int r=Math.max(1,Math.min(127,Math.round(radius)));renderPass(scratch,input,1,0,r,false,1);renderPass(result,scratch.framebufferTexture,0,1,r,false,1);return;}
        int passes=2*UiEffects.options().quality;
        for(int i=0;i<passes;i++){Framebuffer target=(i&1)==0?scratch:result;kawasePass(target,input,Math.max(.5f,radius*(i+1)/passes*.45f));input=target.framebufferTexture;}
    }
    private static void kawasePass(Framebuffer output,int input,float offset){
        try(RenderState state=new RenderState()){
            state.screenSpace();output.bindFramebuffer(true);GlStateManager.disableBlend();glDisable(GL_BLEND);GlStateManager.disableCull();glDisable(GL_CULL_FACE);GlStateManager.colorMask(true,true,true,true);glColorMask(true,true,true,true);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.bindTexture(input);glBindTexture(GL_TEXTURE_2D,input);
            if(kawase==null)kawase=new ShaderUtil("Flauxy/Shaders/kawaseScene.frag");kawase.init();kawase.setUniformi("textureIn",0);kawase.setUniformf("stepSize",offset/output.framebufferWidth,offset/output.framebufferHeight);ShaderUtil.drawQuads();kawase.unload();if(RenderDiagnostics.enabled)RenderDiagnostics.blurPasses++;
        }
    }
    public static void renderPass(Framebuffer output,int input,float dx,float dy,int radius,boolean glow,float exposure){
        radius=Math.max(1,Math.min(127,radius));
        try(RenderState state=new RenderState()){
            state.screenSpace();output.bindFramebuffer(true);
            GlStateManager.disableBlend();GlStateManager.disableCull();glDisable(GL_BLEND);glDisable(GL_CULL_FACE);
            GlStateManager.colorMask(true,true,true,true);glColorMask(true,true,true,true);
            GlStateManager.clearColor(0,0,0,0);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.bindTexture(input);glBindTexture(GL_TEXTURE_2D,input);
            ShaderUtil shader;
            if(glow){if(halo==null)halo=new ShaderUtil("Flauxy/Shaders/glow.frag");shader=halo;}
            else{if(scene==null)scene=new ShaderUtil("Flauxy/Shaders/gaussian.frag");shader=scene;}
            shader.init();shader.setUniformi("textureIn",0);shader.setUniformf("texelSize",1f/output.framebufferWidth,1f/output.framebufferHeight);shader.setUniformf("direction",dx,dy);shader.setUniformf("radius",radius);
            if(kernelRadius!=radius){weights.clear();float sum=0;for(int i=0;i<=radius;i++){float value=(float)Math.exp(-i*i/(2f*(radius/2f)*(radius/2f)));weights.put(value);sum+=i==0?value:2*value;}for(int i=0;i<=radius;i++)weights.put(i,weights.get(i)/sum);weights.flip();kernelRadius=radius;}
            weights.rewind();GL20.glUniform1(shader.getUniform("weights"),weights);
            if(glow){shader.setUniformi("avoidTexture",0);shader.setUniformf("color",1,1,1);shader.setUniformf("exposure",exposure);}
            ShaderUtil.drawQuads();shader.unload();if(RenderDiagnostics.enabled)RenderDiagnostics.blurPasses++;
        }
    }
    public static void releaseResources(){if(scene!=null){scene.close();scene=null;}if(halo!=null){halo.close();halo=null;}if(kawase!=null){kawase.close();kawase=null;}kernelRadius=-1;}
}
