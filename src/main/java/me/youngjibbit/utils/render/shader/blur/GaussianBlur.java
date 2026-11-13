package me.youngjibbit.utils.render.shader.blur;

import me.youngjibbit.core.diagnostics.PerformanceOptions;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.lwjgl.opengl.GL11.*;

/** Shared separable passes. Callers own their snapshots, masks and framebuffers. */
public final class GaussianBlur {
    private static ShaderUtil scene,halo,kawase,paired;
    private static final GaussianKernelCache kernels=new GaussianKernelCache();
    private static final PairedGaussianKernelCache pairedKernels=new PairedGaussianKernelCache();
    private static int sceneKernelRadius=-1,haloKernelRadius=-1,pairedKernelRadius=-1;
    private static IResourceManager sourceManager,registeredManager;
    private static boolean originalSources;
    private static final String[] SCENE_SOURCES={"gaussian.frag","gaussianPaired.frag","kawaseScene.frag","vertex.vsh"};
    private GaussianBlur(){}
    /** Scene-only selection. Caller retains snapshot and two full-size ping-pong targets. */
    public static void renderScene(Framebuffer scratch,Framebuffer result,int input,float radius){
        renderSceneAtResolution(scratch,result,input,radius,scratch.framebufferWidth,scratch.framebufferHeight);
    }

    /** Radius is in full-scene pixels; targets may have the requested reduced physical size. */
    public static void renderSceneAtResolution(Framebuffer scratch,Framebuffer result,int input,float radius,int fullWidth,int fullHeight){
        if(scratch.framebufferTexture==input||result.framebufferTexture==input||scratch==result)throw new IllegalArgumentException("Scene blur requires distinct input and outputs");
        if(scratch.framebufferWidth!=result.framebufferWidth||scratch.framebufferHeight!=result.framebufferHeight)throw new IllegalArgumentException("Scene blur targets must have matching dimensions");
        float radiusX=SceneBlurSize.radius(radius,result.framebufferWidth,fullWidth);
        float radiusY=SceneBlurSize.radius(radius,result.framebufferHeight,fullHeight);
        boolean requestPaired=PerformanceOptions.isFastGaussianEnabled();
        // No callbacks occur between these owned passes: preserve the caller once per scene.
        try(RenderState state=new RenderState()){
            state.screenSpace();
            if(!UiEffects.options().kawase){
                int rx=Math.max(1,Math.min(127,Math.round(radiusX))),ry=Math.max(1,Math.min(127,Math.round(radiusY)));
                prepareGaussianOutput(scratch,input);drawSceneGaussian(scratch,1,0,rx,requestPaired);
                prepareGaussianOutput(result,scratch.framebufferTexture);drawSceneGaussian(result,0,1,ry,requestPaired);
                return;
            }
            int passes=2*UiEffects.options().quality;
            for(int i=0;i<passes;i++){
                Framebuffer target=(i&1)==0?scratch:result;
                float fullOffset=Math.max(.5f,radius*(i+1)/passes*.45f);
                kawasePass(target,input,result.framebufferWidth==fullWidth?fullOffset:fullOffset*result.framebufferWidth/fullWidth,
                        result.framebufferHeight==fullHeight?fullOffset:fullOffset*result.framebufferHeight/fullHeight);
                input=target.framebufferTexture;
                if(RenderDiagnostics.enabled)RenderDiagnostics.sceneBlurPixels+=(long)target.framebufferWidth*target.framebufferHeight;
            }
        }
    }
    /** Unsupported resource overrides retain their original full-resolution contract. */
    public static int effectiveSceneBlurDivisor(){
        int divisor=PerformanceOptions.getSceneBlurDivisor();
        return divisor==1||!originalSceneSources()?1:divisor;
    }

    private static void kawasePass(Framebuffer output,int input,float offsetX,float offsetY){
        output.bindFramebuffer(true);GlStateManager.disableBlend();glDisable(GL_BLEND);GlStateManager.disableCull();glDisable(GL_CULL_FACE);GlStateManager.colorMask(true,true,true,true);glColorMask(true,true,true,true);
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.bindTexture(input);glBindTexture(GL_TEXTURE_2D,input);
        if(kawase==null)kawase=new ShaderUtil("Flauxy/Shaders/kawaseScene.frag");kawase.init();kawase.setUniformi("textureIn",0);kawase.setUniformf("stepSize",offsetX/output.framebufferWidth,offsetY/output.framebufferHeight);ShaderUtil.drawQuads();kawase.unload();if(RenderDiagnostics.enabled)RenderDiagnostics.blurPasses++;
    }

    private static void drawSceneGaussian(Framebuffer output,float dx,float dy,int radius,boolean requested){
        if(requested&&originalSceneSources()&&pairedInputEligible(output)){
            if(paired==null)paired=new ShaderUtil("Flauxy/Shaders/gaussianPaired.frag");
            paired.init();paired.setUniformi("textureIn",0);paired.setUniformf("texelSize",1f/output.framebufferWidth,1f/output.framebufferHeight);paired.setUniformf("direction",dx,dy);
            if(pairedKernelRadius!=radius){
                PairedGaussianKernelCache.Kernel kernel=pairedKernels.get(radius,kernels.get(radius));
                paired.setUniformf("centerWeight",kernel.center);paired.setUniformf("totalWeight",kernel.total);paired.setUniformi("pairCount",kernel.pairs());
                GL20.glUniform1(paired.getUniform("pairWeights"),kernel.weights);GL20.glUniform1(paired.getUniform("pairOffsets"),kernel.offsets);
                pairedKernelRadius=radius;
            }
            ShaderUtil.drawQuads();paired.unload();
            if(RenderDiagnostics.enabled){RenderDiagnostics.blurPasses++;RenderDiagnostics.pairedGaussianPasses++;}
        }else{
            drawGaussian(output,dx,dy,radius,false,1);
            if(requested&&RenderDiagnostics.enabled)RenderDiagnostics.pairedGaussianFallbacks++;
        }
        if(RenderDiagnostics.enabled)RenderDiagnostics.sceneBlurPixels+=(long)output.framebufferWidth*output.framebufferHeight;
    }

    /** Pairing is valid only for adjacent texels, never the first full-to-reduced pass. */
    private static boolean pairedInputEligible(Framebuffer output){
        return glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER)==GL_LINEAR
                &&glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER)==GL_LINEAR
                &&glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S)==GL12.GL_CLAMP_TO_EDGE
                &&glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T)==GL12.GL_CLAMP_TO_EDGE
                &&glGetTexParameteri(GL_TEXTURE_2D,GL12.GL_TEXTURE_BASE_LEVEL)==0
                &&glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_BORDER)==0
                &&glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)==output.framebufferWidth
                &&glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT)==output.framebufferHeight
                &&(!GLContext.getCapabilities().GL_EXT_texture_filter_anisotropic
                ||glGetTexParameterf(GL_TEXTURE_2D,EXTTextureFilterAnisotropic.GL_TEXTURE_MAX_ANISOTROPY_EXT)<=1f);
    }

    private static boolean originalSceneSources(){
        IResourceManager manager=Minecraft.getMinecraft().getResourceManager();
        if(manager!=registeredManager){
            registeredManager=manager;sourceManager=null;
            if(manager instanceof IReloadableResourceManager)((IReloadableResourceManager)manager).registerReloadListener(active->{
                if(registeredManager==active)releaseResources();
            });
        }
        if(manager!=sourceManager){originalSources=matchesOriginalSources(manager);sourceManager=manager;}
        return originalSources;
    }

    static boolean matchesOriginalSources(IResourceManager manager){
        if(manager==null)return false;
        try{
            for(String source:SCENE_SOURCES){
                String path="Flauxy/Shaders/"+source;
                try(InputStream actual=manager.getResource(new ResourceLocation(path)).getInputStream();
                    InputStream bundled=GaussianBlur.class.getResourceAsStream("/assets/minecraft/"+path)){
                    if(!readSource(actual).equals(readSource(bundled)))return false;
                }
            }
            return true;
        }catch(IOException|RuntimeException failure){return false;}
    }

    private static String readSource(InputStream stream)throws IOException{
        if(stream==null)throw new IOException("Missing scene shader source");
        BufferedReader reader=new BufferedReader(new InputStreamReader(stream,StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder();String line;while((line=reader.readLine())!=null)result.append(line).append('\n');return result.toString();
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
    public static void releaseResources(){if(scene!=null){scene.close();scene=null;}if(halo!=null){halo.close();halo=null;}if(kawase!=null){kawase.close();kawase=null;}if(paired!=null){paired.close();paired=null;}sceneKernelRadius=haloKernelRadius=pairedKernelRadius=-1;sourceManager=null;originalSources=false;}
}
