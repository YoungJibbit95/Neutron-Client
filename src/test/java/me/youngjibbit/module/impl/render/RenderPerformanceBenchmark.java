package me.youngjibbit.module.impl.render;

import com.google.gson.*;
import com.mojang.authlib.properties.PropertyMap;
import com.sun.management.ThreadMXBean;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.*;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.LongSupplier;
import static org.lwjgl.opengl.GL11.*;

/** Real shaders/fonts in an isolated Pbuffer. Timings describe synthetic work, never game FPS. */
public final class RenderPerformanceBenchmark {
    private static final Color PANEL=new Color(24,28,40,210), FIRST=new Color(221,54,177,195), SECOND=new Color(31,160,216,210);
    private static final String[] LABELS={"Neutron","§lSettings","Ω 😀","§aVisuals","§oPoppins","Glow","Blur","§nOutline"};
    private static final String[] SCENES={"ui_text","clipped_bloom","gaussian_blur","glass_surface"};
    private final Minecraft client;
    private final TTFFontRenderer font;
    private final ArrayListSurface surface=new ArrayListSurface();
    private final ArrayListSurface.Shape shape=ArrayListSurface.Shape.roundedRectangle(24,24,320,218,9);
    private Framebuffer scratch,blurred;
    private RenderPerformanceBenchmark(Minecraft client,TTFFontRenderer font){this.client=client;this.font=font;}

    public static void main(String[] args)throws Exception{
        if(args.length<1)throw new IllegalArgumentException("Output directory required");
        Path output=new File(args[0]).toPath().toAbsolutePath();Files.createDirectories(output);
        Files.createDirectories(output.resolve("home"));System.setProperty("user.home",output.resolve("home").toString());
        boolean verify=args.length>1&&"--verify-current".equals(args[1]);
        int warmup=verify?0:args.length>1?Integer.parseInt(args[1]):20;
        int samples=verify?0:args.length>2?Integer.parseInt(args[2]):60;
        if(!verify&&(warmup<1||warmup>200||samples<20||samples>500))throw new IllegalArgumentException("Warmup 1..200 / samples 20..500 required");
        File data=Files.createDirectories(output.resolve("fixture")).toFile();
        try(PbufferTestContext context=new PbufferTestContext()){
            Minecraft mc=fixture(context,data);FontManager fonts=new FontManager();Pulsar.INSTANCE.fontManager=fonts;
            ClientSettings settings=new ClientSettings();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager(Collections.singletonList(settings)));
            settings.gradientMotion.setValue("Static");settings.reducedMotion.setValue(true);settings.textGlow.setValue("On");
            settings.surface.setValue("Liquid Glass");settings.blurMethod.setValue("Gaussian");Theme.setDefaultTheme(Theme.PinkLemonade);
            Method clock=UiEffects.class.getDeclaredMethod("timeSource",LongSupplier.class);clock.setAccessible(true);
            clock.invoke(null,(LongSupplier)()->123456789L);UiEffects.invalidate();UiEffects.beginFrame();
            if(verify){LegacyRenderFacadeCheck.run();context.check();fonts.close();return;}
            RenderPerformanceBenchmark benchmark=new RenderPerformanceBenchmark(mc,fonts.getLocalFont("Poppins-Regular 18"));
            JsonObject report=new JsonObject();report.addProperty("format_version",1);
            report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("os",System.getProperty("os.name")+" "+System.getProperty("os.arch"));
            report.addProperty("gl_vendor",glGetString(GL_VENDOR));report.addProperty("gl_renderer",glGetString(GL_RENDERER));report.addProperty("gl_version",glGetString(GL_VERSION));
            report.addProperty("warmup_frames",warmup);report.addProperty("samples",samples);
            report.addProperty("timing_scope","Synthetic UI submission; completed_ms includes glFinish. Backdrop/readback/files excluded. Not client FPS, GPU timestamps or startup time.");
            report.addProperty("allocation_scope","Render-thread Java heap only; native/direct/GPU memory excluded.");
            JsonArray scenarios=new JsonArray();
            try{
                for(int[] dimensions:new int[][]{{960,640,2},{854,480,1}}){
                    benchmark.resize(dimensions[0],dimensions[1],dimensions[2]);
                    for(String scene:SCENES)scenarios.add(benchmark.measure(output,scene,warmup,samples));
                }
                report.add("scenarios",scenarios);Files.write(output.resolve("results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));context.check();
            }finally{
                benchmark.surface.close();if(benchmark.scratch!=null)benchmark.scratch.deleteFramebuffer();if(benchmark.blurred!=null)benchmark.blurred.deleteFramebuffer();
                GuiShaderBloom.releaseResources();ArrayListSurface.releaseResources();GaussianBlur.releaseResources();TextEffects.releaseResources();UIUtils.releaseResources();fonts.close();mc.getFramebuffer().deleteFramebuffer();
            }
        }
        System.out.println("PASS: native synthetic render samples and exact RGBA captures written");
    }

    private static Minecraft fixture(PbufferTestContext context,File data)throws Exception{
        Minecraft mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(new Session("RenderBench","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                new GameConfiguration.DisplayInformation(960,640,false,false),new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                new GameConfiguration.GameInformation(false,"Synthetic render benchmark"),new GameConfiguration.ServerInformation(null,0)));
        context.initialize(mc,data);IMetadataSerializer metadata=new IMetadataSerializer();
        metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);metadata.registerMetadataSectionType(new AnimationMetadataSectionSerializer(),AnimationMetadataSection.class);metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(),PackMetadataSection.class);
        SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(metadata);resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
        SessionInfoRenderCheck.set(mc,"mcResourceManager",resources);SessionInfoRenderCheck.set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
        SessionInfoRenderCheck.set(mc,"framebufferMc",new Framebuffer(mc.displayWidth,mc.displayHeight,true));SessionInfoRenderCheck.mc=mc;return mc;
    }
    private void resize(int width,int height,int scale){
        client.displayWidth=width;client.displayHeight=height;client.gameSettings.guiScale=scale;client.getFramebuffer().createBindFramebuffer(width,height);
        if(scratch!=null)scratch.deleteFramebuffer();if(blurred!=null)blurred.deleteFramebuffer();scratch=new Framebuffer(width,height,false);blurred=new Framebuffer(width,height,false);
    }

    private JsonObject measure(Path output,String scene,int warmup,int samples)throws Exception{
        String name=scene+"_"+client.displayWidth+"x"+client.displayHeight+"_scale"+client.gameSettings.guiScale;
        SessionInfoRenderCheck.backdrop();byte[] backdrop=readRgba();RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
        long coldStart=System.nanoTime();draw(scene,0);glFinish();double cold=(System.nanoTime()-coldStart)/1e6;
        long coldMasks=RenderDiagnostics.maskPasses;RenderDiagnostics.enabled=false;
        if(Arrays.equals(backdrop,capture(output,name+"_first")))throw new AssertionError("First draw produced only its backdrop: "+name);
        for(int frame=0;frame<warmup;frame++){SessionInfoRenderCheck.backdrop();draw(scene,frame);glFinish();}
        ThreadMXBean memory=memoryCounter();long thread=Thread.currentThread().getId();
        double[] submission=new double[samples],completed=new double[samples],allocated=new double[samples];
        RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
        for(int frame=0;frame<samples;frame++){
            SessionInfoRenderCheck.backdrop();glFinish();long beforeBytes=memory==null?-1:memory.getThreadAllocatedBytes(thread);
            long start=System.nanoTime();draw(scene,frame);long submitted=System.nanoTime();glFinish();long finished=System.nanoTime();
            submission[frame]=(submitted-start)/1e6;completed[frame]=(finished-start)/1e6;
            allocated[frame]=memory==null?-1:memory.getThreadAllocatedBytes(thread)-beforeBytes;
        }
        JsonObject counters=new JsonObject();counters.addProperty("framebuffer_creates",RenderDiagnostics.framebufferCreates);counters.addProperty("framebuffer_deletes",RenderDiagnostics.framebufferDeletes);
        counters.addProperty("shader_compiles",RenderDiagnostics.shaderCompiles);counters.addProperty("mask_passes",RenderDiagnostics.maskPasses);counters.addProperty("blur_passes",RenderDiagnostics.blurPasses);counters.addProperty("glyph_uploads",RenderDiagnostics.glyphUploads);
        counters.addProperty("composites",RenderDiagnostics.composites);counters.addProperty("font_batches",RenderDiagnostics.fontBatches);
        if(("clipped_bloom".equals(scene)||"gaussian_blur".equals(scene)||"glass_surface".equals(scene))&&RenderDiagnostics.blurPasses<=0)throw new AssertionError("No actual blur passes: "+name);
        if("clipped_bloom".equals(scene)&&RenderDiagnostics.maskPasses<=0)throw new AssertionError("No actual bloom masks: "+name);
        if("glass_surface".equals(scene)&&(coldMasks<=0||RenderDiagnostics.composites<=0))throw new AssertionError("No actual glass mask/composition: "+name);
        if(("ui_text".equals(scene)||"glass_surface".equals(scene))&&RenderDiagnostics.fontBatches<=0)throw new AssertionError("No actual glyph batches: "+name);
        RenderDiagnostics.enabled=false;SessionInfoRenderCheck.backdrop();draw(scene,0);
        if(Arrays.equals(backdrop,capture(output,name+"_warm")))throw new AssertionError("Warm draw produced only its backdrop: "+name);
        if(glGetError()!=GL_NO_ERROR)throw new AssertionError("OpenGL error in "+name);
        JsonObject result=new JsonObject();result.addProperty("name",name);result.addProperty("width",client.displayWidth);result.addProperty("height",client.displayHeight);result.addProperty("scale",client.gameSettings.guiScale);result.addProperty("first_workload_completed_ms",cold);
        result.addProperty("visible_output_verified",true);result.addProperty("first_mask_passes",coldMasks);
        result.add("submission_ms",distribution(submission));result.add("completed_ms",distribution(completed));result.add("allocated_bytes",distribution(allocated));result.add("warm_counters",counters);
        System.out.println("Measured "+name+": submission p50="+result.getAsJsonObject("submission_ms").get("p50"));return result;
    }

    private void draw(String scene,int frame){
        UiEffects.beginFrame();
        if("ui_text".equals(scene)){
            for(int i=0;i<24;i++){
                float x=10+(i%4)*115,y=10+(i/4)*49;UIUtils.drawRound(x,y,108,43,6,PANEL);
                UIUtils.drawGradientHorizontal(x+4,y+4,100,3,1,FIRST,SECOND);String label=LABELS[i%LABELS.length];
                font.drawStringWithShadow(label,x+(108-font.getWidth(label))/2,y+14,-1);
            }
        }else if("clipped_bloom".equals(scene)){
            GuiShaderBloom.beginBatch();
            try(RenderState ignored=new RenderState()){
                glTranslatef(9.25f,7.5f,0);glScalef(.92f,1.05f,1);
                for(int i=0;i<32;i++){
                    if((i&1)==0){glEnable(GL_SCISSOR_TEST);glScissor(33,41,client.displayWidth-91,client.displayHeight-73);}else glDisable(GL_SCISSOR_TEST);
                    float x=16+(i%8)*52,y=20+(i/8)*49;
                    if((i&3)==0)GuiShaderBloom.queueWorldRect(x,y,46,34,7,0xa020d982,true);else GuiShaderBloom.queueRoundedRect(x,y,46,34,7,0x90dc40b5);
                }
            }
            GuiShaderBloom.flushBatch((frame&1)==0?2:3,2);
        }else if("gaussian_blur".equals(scene)){
            GaussianBlur.renderScene(scratch,blurred,client.getFramebuffer().framebufferTexture,(frame&1)==0?7:11);
            try(RenderState state=new RenderState()){
                state.screenSpace();state.bindTarget();GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
                GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.disableBlend();glDisable(GL_BLEND);
                GlStateManager.bindTexture(blurred.framebufferTexture);glBindTexture(GL_TEXTURE_2D,blurred.framebufferTexture);ShaderUtil.drawQuads();
            }
        }else if("glass_surface".equals(scene)){
            surface.render(shape,Theme.PinkLemonade,true,true,true,.7f,()->{
                UIUtils.drawRoundOutline(33,33,302,200,6,1,PANEL,FIRST);
                for(int i=0;i<LABELS.length;i++)font.drawStringWithShadow(LABELS[i],44,45+i*21,-1);
            });
        }else throw new IllegalArgumentException(scene);
    }

    private byte[] readRgba(){
        glFinish();client.getFramebuffer().bindFramebuffer(true);ByteBuffer pixels=BufferUtils.createByteBuffer(client.displayWidth*client.displayHeight*4);
        glReadPixels(0,0,client.displayWidth,client.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);byte[] rgba=new byte[pixels.capacity()];pixels.get(rgba);return rgba;
    }
    private byte[] capture(Path output,String name)throws Exception{
        byte[] rgba=readRgba();Files.write(output.resolve(name+".rgba"),rgba);
        BufferedImage image=new BufferedImage(client.displayWidth,client.displayHeight,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<client.displayHeight;y++)for(int x=0;x<client.displayWidth;x++){
            int offset=(y*client.displayWidth+x)*4;int argb=(rgba[offset+3]&255)<<24|(rgba[offset]&255)<<16|(rgba[offset+1]&255)<<8|rgba[offset+2]&255;
            image.setRGB(x,client.displayHeight-y-1,argb);
        }
        ImageIO.write(image,"png",output.resolve(name+".png").toFile());return rgba;
    }
    private static JsonObject distribution(double[] samples){
        double[] sorted=samples.clone();Arrays.sort(sorted);JsonObject result=new JsonObject();
        result.addProperty("p50",sorted[(int)Math.ceil(sorted.length*.50)-1]);result.addProperty("p95",sorted[(int)Math.ceil(sorted.length*.95)-1]);result.addProperty("p99",sorted[(int)Math.ceil(sorted.length*.99)-1]);
        JsonArray raw=new JsonArray();for(double sample:samples)raw.add(sample);result.add("samples",raw);return result;
    }
    private static ThreadMXBean memoryCounter(){
        java.lang.management.ThreadMXBean bean=ManagementFactory.getThreadMXBean();if(!(bean instanceof ThreadMXBean))return null;
        ThreadMXBean memory=(ThreadMXBean)bean;if(!memory.isThreadAllocatedMemorySupported())return null;
        if(!memory.isThreadAllocatedMemoryEnabled())memory.setThreadAllocatedMemoryEnabled(true);return memory;
    }
}
