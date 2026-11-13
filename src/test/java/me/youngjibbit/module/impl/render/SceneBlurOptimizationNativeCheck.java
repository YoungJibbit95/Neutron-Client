package me.youngjibbit.module.impl.render;

import com.google.gson.*;
import com.mojang.authlib.properties.PropertyMap;
import com.sun.management.ThreadMXBean;
import me.youngjibbit.Pulsar;
import me.youngjibbit.core.diagnostics.PerformanceOptions;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import me.youngjibbit.utils.render.shader.blur.SceneBlurSize;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;

/** Current-only native oracles and samples. Never loaded into the immutable baseline harness. */
public final class SceneBlurOptimizationNativeCheck {
    private final Minecraft mc;
    private final ClientSettings settings;
    private final ArrayListSurface scene=new ArrayListSurface(),child=new ArrayListSurface();
    private final JsonArray checks=new JsonArray();
    private final Path output;

    private SceneBlurOptimizationNativeCheck(Minecraft mc,ClientSettings settings,Path output){this.mc=mc;this.settings=settings;this.output=output;}

    public static void main(String[] args)throws Exception{
        Path output=Paths.get(args.length==0?"scene-blur-preview":args[0]).toAbsolutePath();Files.createDirectories(output);
        boolean verify=args.length<2||"--verify".equals(args[1]);String mode=verify?"verify":args[1];
        int warmup=verify?0:args.length>2?Integer.parseInt(args[2]):40;
        int samples=verify?0:args.length>3?Integer.parseInt(args[3]):60;
        if(!verify&&(!Arrays.asList("full","paired","half","quarter").contains(mode)||warmup<1||warmup>200||samples<20||samples>500))throw new IllegalArgumentException("Mode full/paired/half/quarter, warmup 1..200, samples 20..500 required");
        System.setProperty("user.home",Files.createDirectories(output.resolve("home")).toString());
        JsonObject report=new JsonObject();report.addProperty("format_version",1);report.addProperty("mode",mode);
        report.addProperty("status","failed");
        try(PbufferTestContext context=new PbufferTestContext()){
            Minecraft mc=fixture(context,Files.createDirectories(output.resolve("fixture")).toFile());
            ClientSettings settings=new ClientSettings();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager(Collections.singletonList(settings)));
            settings.surface.setValue("Liquid Glass");settings.blurMethod.setValue("Gaussian");settings.blurStrength.setValue(11D);
            settings.reducedMotion.setValue(true);settings.gradientMotion.setValue("Static");settings.glow.setValue(false);
            Theme.setDefaultTheme(Theme.PinkLemonade);UiEffects.invalidate();UiEffects.beginFrame();
            SceneBlurOptimizationNativeCheck check=new SceneBlurOptimizationNativeCheck(mc,settings,output);
            report.addProperty("java",System.getProperty("java.runtime.version"));report.addProperty("os",System.getProperty("os.name")+" "+System.getProperty("os.arch"));
            report.addProperty("gl_vendor",glGetString(GL_VENDOR));report.addProperty("gl_renderer",glGetString(GL_RENDERER));report.addProperty("gl_version",glGetString(GL_VERSION));
            report.addProperty("scope","Synthetic Pbuffer scene blur and real UI/Glass consumers. Completed time includes glFinish; not game FPS or target-GPU evidence.");
            try{
                if(verify){check.verify();report.add("checks",check.checks);}
                else{report.addProperty("warmup_frames",warmup);report.addProperty("samples",samples);report.add("scenarios",check.measure(mode,warmup,samples));}
                context.check();require(glGetError()==GL_NO_ERROR,"Final scene blur GL error");report.addProperty("status","passed");
            }catch(Throwable failure){report.addProperty("failure",failure.toString());report.add("checks",check.checks);throw failure;}
            finally{
                Files.write(output.resolve(verify?"scene-blur-report.json":"results.json"),new GsonBuilder().setPrettyPrinting().create().toJson(report).getBytes(StandardCharsets.UTF_8));
                PerformanceOptions.defaults();RenderDiagnostics.enabled=false;
                ArrayListSurface.releaseResources();Blur.releaseResources();GaussianBlur.releaseResources();UIUtils.releaseResources();mc.getFramebuffer().deleteFramebuffer();
            }
        }
        System.out.println(verify?"PASS: scene blur optimization pixel/state/resource contracts":"PASS: current scene blur mode samples written");
    }

    private static Minecraft fixture(PbufferTestContext context,File data)throws Exception{
        Minecraft mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(new Session("SceneBlurCheck","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                new GameConfiguration.DisplayInformation(960,640,false,false),new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                new GameConfiguration.GameInformation(false,"Scene blur native check"),new GameConfiguration.ServerInformation(null,0)));
        context.initialize(mc,data);IMetadataSerializer metadata=new IMetadataSerializer();
        metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);metadata.registerMetadataSectionType(new AnimationMetadataSectionSerializer(),AnimationMetadataSection.class);metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(),PackMetadataSection.class);
        SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(metadata);resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
        SessionInfoRenderCheck.set(mc,"mcResourceManager",resources);SessionInfoRenderCheck.set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
        SessionInfoRenderCheck.set(mc,"framebufferMc",new Framebuffer(mc.displayWidth,mc.displayHeight,true));SessionInfoRenderCheck.mc=mc;return mc;
    }

    private void verify()throws Exception{
        for(int[] size:new int[][]{{129,97},{257,193}}){
            resize(size[0],size[1],1);
            Framebuffer input=target(size[0],size[1]),scratch=target(size[0],size[1]),result=target(size[0],size[1]);
            try{
                pattern(input);
                for(int radius:new int[]{1,2,7,11,31,127}){
                    PerformanceOptions.configure(false,false,false,1);GaussianBlur.renderScene(scratch,result,input.framebufferTexture,radius);byte[] legacy=read(result);
                    PerformanceOptions.configure(false,false,true,1);RenderDiagnostics.enabled=true;RenderDiagnostics.reset();GaussianBlur.renderScene(scratch,result,input.framebufferTexture,radius);byte[] paired=read(result);
                    String name="paired-"+size[0]+"x"+size[1]+"-r"+radius;png(name+"-legacy",legacy,size[0],size[1]);png(name+"-paired",paired,size[0],size[1]);
                    JsonObject delta=compare(legacy,paired,2,name);delta.addProperty("radius",radius);delta.addProperty("width",size[0]);delta.addProperty("height",size[1]);checks.add(delta);
                    require(RenderDiagnostics.pairedGaussianPasses==2&&RenderDiagnostics.pairedGaussianFallbacks==0,"Fast path did not execute two passes: "+name);
                    require(RenderDiagnostics.sceneBlurPixels==2L*size[0]*size[1],"Full pass work mismatch");
                }
                fallback(input,scratch,result,GL_NEAREST,GL12.GL_CLAMP_TO_EDGE,"nearest");
                fallback(input,scratch,result,GL_LINEAR,GL_CLAMP,"legacy-clamp");
                filters(input,GL_LINEAR,GL12.GL_CLAMP_TO_EDGE);filters(scratch,GL_LINEAR,GL12.GL_CLAMP_TO_EDGE);
                stateContract(input,scratch,result);
            }finally{input.deleteFramebuffer();scratch.deleteFramebuffer();result.deleteFramebuffer();}
        }
        resize(853,481,2);
        byte[] full=null;
        for(int divisor:new int[]{1,2,4,2,1}){
            PerformanceOptions.configure(false,false,false,divisor);SessionInfoRenderCheck.backdrop();byte[] backdrop=read(mc.getFramebuffer());
            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();draw(true,backdrop);
            byte[] pixels=read(mc.getFramebuffer());png("quality-"+divisor,pixels,mc.displayWidth,mc.displayHeight);
            require(RenderDiagnostics.sceneBlurPixels==expectedPixels(divisor),"Scaled Gaussian processed incorrect dimensions");
            require(!Arrays.equals(backdrop,pixels),"Quality mode erased the UI workload");
            assertCorners(backdrop,pixels,"Scaled surface changed unrelated screen corners");
            if(divisor==1){if(full!=null)require(Arrays.equals(full,pixels),"Returning to Full did not restore exact RGBA");full=pixels;}
            PerformanceOptions.configure(false,false,true,divisor);SessionInfoRenderCheck.backdrop();RenderDiagnostics.reset();draw(true,backdrop);
            byte[] paired=read(mc.getFramebuffer());png("quality-"+divisor+"-paired",paired,mc.displayWidth,mc.displayHeight);JsonObject row=compare(pixels,paired,2,"material-quality");
            require(RenderDiagnostics.pairedGaussianPasses==(divisor==1?2:1),"Actual material path missed paired work at divisor "+divisor);
            require(RenderDiagnostics.pairedGaussianFallbacks==(divisor==1?0:1),"First downsample pass was incorrectly paired");
            require(RenderDiagnostics.sceneBlurPixels==expectedPixels(divisor),"Paired material pass work mismatch");
            row.addProperty("divisor",divisor);row.addProperty("scene_blur_pixels",RenderDiagnostics.sceneBlurPixels);row.addProperty("paired_passes",RenderDiagnostics.pairedGaussianPasses);checks.add(row);
        }
        nestedQualityDeferral();borrowedCaptureInvalidation();legacyMaskAndKawase();resourceOverrides();resourcesAndResize();
    }

    private void fallback(Framebuffer input,Framebuffer scratch,Framebuffer result,int filter,int wrap,String name)throws Exception{
        filters(input,filter,wrap);filters(scratch,filter,wrap);
        PerformanceOptions.configure(false,false,false,1);GaussianBlur.renderScene(scratch,result,input.framebufferTexture,11);byte[] expected=read(result);
        PerformanceOptions.configure(false,false,true,1);RenderDiagnostics.reset();GaussianBlur.renderScene(scratch,result,input.framebufferTexture,11);
        require(Arrays.equals(expected,read(result)),"Unsupported sampler fallback changed pixels: "+name);
        require(RenderDiagnostics.pairedGaussianPasses==0&&RenderDiagnostics.pairedGaussianFallbacks==2,"Unsupported sampler entered paired shader: "+name);
        bind(input.framebufferTexture);require(glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER)==filter&&glGetTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S)==wrap,"Foreign input sampler was modified");
        JsonObject row=new JsonObject();row.addProperty("check","fallback-"+name);row.addProperty("exact_rgba",true);checks.add(row);
    }

    private void stateContract(Framebuffer input,Framebuffer scratch,Framebuffer result){
        try(RenderState outer=new RenderState();ShaderUtil caller=new ShaderUtil("Flauxy/Shaders/gaussian.frag")){
            mc.getFramebuffer().bindFramebuffer(true);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,input.framebufferObject);
            glViewport(3,5,mc.displayWidth-8,mc.displayHeight-10);glEnable(GL_SCISSOR_TEST);glScissor(7,9,33,27);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);GlStateManager.setActiveTexture(GL13.GL_TEXTURE3);GlStateManager.bindTexture(input.framebufferTexture);glBindTexture(GL_TEXTURE_2D,input.framebufferTexture);caller.init();
            int[] before=state();GaussianBlur.renderScene(scratch,result,input.framebufferTexture,7);require(Arrays.equals(before,state()),"Scene blur lost caller FBO/program/viewport/scissor/texture state");
            try{GaussianBlur.renderScene(scratch,scratch,input.framebufferTexture,7);throw new AssertionError("Aliased outputs accepted");}catch(IllegalArgumentException expected){}
            require(Arrays.equals(before,state()),"Rejected scene draw changed caller state");caller.unload();
        }
        require(glGetError()==GL_NO_ERROR,"State contract GL error");
        JsonObject row=new JsonObject();row.addProperty("check","caller-state-and-alias-rejection");row.addProperty("passed",true);checks.add(row);
    }

    private void nestedQualityDeferral(){
        PerformanceOptions.configure(false,false,false,1);SessionInfoRenderCheck.backdrop();
        scene.withScene(Theme.PinkLemonade,true,true,11,()->{
            UiEffects.SceneContext outer=UiEffects.scene();int soft=outer.soft;
            PerformanceOptions.configure(false,false,true,4);
            settings.blurStrength.setValue(13D);UiEffects.invalidate();
            try{
                scene.withScene(Theme.PinkLemonade,true,true,13,()->{
                    require(UiEffects.scene().soft==soft&&glIsTexture(soft),"Quality switch deleted an active owner scene");
                    try(RenderState ignored=new RenderState()){requireTextureSize(soft,mc.displayWidth,mc.displayHeight);}
                });
                require(UiEffects.scene()==outer&&glIsTexture(soft),"Outer scene did not survive nested quality switch");
                UIUtils.drawSceneRound(30,30,120,50,8);
            }finally{settings.blurStrength.setValue(11D);UiEffects.invalidate();}
        });
        SessionInfoRenderCheck.backdrop();RenderDiagnostics.reset();draw(true,read(mc.getFramebuffer()));
        require(RenderDiagnostics.sceneBlurPixels==expectedPixels(4),"Deferred quality did not apply on the next outer draw");
        JsonObject row=new JsonObject();row.addProperty("check","nested-quality-deferral");row.addProperty("passed",true);checks.add(row);
    }

    private void borrowedCaptureInvalidation(){
        PerformanceOptions.configure(false,false,false,1);SessionInfoRenderCheck.backdrop();draw(false,null);
        require(child.withCapturedScene(()->{
            int soft=UiEffects.scene().soft;
            PerformanceOptions.configure(false,false,false,4);settings.blurStrength.setValue(13D);UiEffects.invalidate();
            try{
                scene.withScene(Theme.PinkLemonade,true,true,13,()->{
                    require(glIsTexture(soft),"Borrowed capture lost its owner texture during quality switch");
                    try(RenderState ignored=new RenderState()){requireTextureSize(soft,mc.displayWidth,mc.displayHeight);}
                });
                require(glIsTexture(soft),"Borrowed outer capture texture was deleted");
            }finally{settings.blurStrength.setValue(11D);UiEffects.invalidate();}
        }),"Fresh child capture was unavailable");
        SessionInfoRenderCheck.backdrop();scene.withScene(Theme.PinkLemonade,true,true,11,()->{});
        require(!child.withCapturedScene(()->{throw new AssertionError("Stale borrowed soft capture was reused");}),"Quality switch did not invalidate a borrowed child capture");
        JsonObject row=new JsonObject();row.addProperty("check","borrowed-capture-invalidation");row.addProperty("passed",true);checks.add(row);
    }

    private static int[] state(){
        IntBuffer v=BufferUtils.createIntBuffer(16),s=BufferUtils.createIntBuffer(16);glGetInteger(GL_VIEWPORT,v);glGetInteger(GL_SCISSOR_BOX,s);
        return new int[]{glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),glGetInteger(GL20.GL_CURRENT_PROGRAM),glGetInteger(GL13.GL_ACTIVE_TEXTURE),glGetInteger(GL_TEXTURE_BINDING_2D),glIsEnabled(GL_SCISSOR_TEST)?1:0,v.get(0),v.get(1),v.get(2),v.get(3),s.get(0),s.get(1),s.get(2),s.get(3)};
    }

    private void legacyMaskAndKawase()throws Exception{
        for(int divisor:new int[]{1,2,4}){
            PerformanceOptions.configure(false,false,true,divisor);SessionInfoRenderCheck.backdrop();RenderDiagnostics.reset();
            Blur.startBlur();UIUtils.drawRound(20,20,180,100,8,Color.WHITE);Blur.endBlur(11,1);
            require(RenderDiagnostics.sceneBlurPixels==expectedPixels(divisor),"Legacy mask ignored scene resolution");
            require(RenderDiagnostics.pairedGaussianPasses==0&&RenderDiagnostics.pairedGaussianFallbacks==2,"Legacy CLAMP masks were silently altered");
            png("mask-quality-"+divisor,read(mc.getFramebuffer()),mc.displayWidth,mc.displayHeight);
            JsonObject row=new JsonObject();row.addProperty("check","legacy-mask-quality");row.addProperty("divisor",divisor);row.addProperty("scene_blur_pixels",RenderDiagnostics.sceneBlurPixels);checks.add(row);
        }
        settings.blurMethod.setValue("Kawase");UiEffects.invalidate();
        try{
            for(int divisor:new int[]{1,2,4}){
                PerformanceOptions.configure(false,false,true,divisor);SessionInfoRenderCheck.backdrop();RenderDiagnostics.reset();draw(false,null);
                require(RenderDiagnostics.sceneBlurPixels==(long)(2*UiEffects.options().quality)*SceneBlurSize.dimension(mc.displayWidth,divisor)*SceneBlurSize.dimension(mc.displayHeight,divisor),"Kawase pass dimensions wrong");
                require(RenderDiagnostics.pairedGaussianPasses==0,"Kawase unexpectedly used Gaussian shader");png("kawase-quality-"+divisor,read(mc.getFramebuffer()),mc.displayWidth,mc.displayHeight);
                JsonObject row=new JsonObject();row.addProperty("check","kawase-quality");row.addProperty("divisor",divisor);row.addProperty("scene_blur_pixels",RenderDiagnostics.sceneBlurPixels);checks.add(row);
            }
        }finally{settings.blurMethod.setValue("Gaussian");UiEffects.invalidate();}
    }

    private void resourceOverrides()throws Exception{
        IResourceManager original=mc.getResourceManager();ReloadingResources resources=new ReloadingResources(original);SessionInfoRenderCheck.set(mc,"mcResourceManager",resources);
        try{
            for(String source:new String[]{"gaussian.frag","gaussianPaired.frag","kawaseScene.frag","vertex.vsh"}){
                resources.source=source;resources.reloadResources(Collections.emptyList());
                PerformanceOptions.configure(false,false,false,1);SessionInfoRenderCheck.backdrop();draw(false,null);byte[] expected=read(mc.getFramebuffer());
                PerformanceOptions.configure(false,false,true,4);require(GaussianBlur.effectiveSceneBlurDivisor()==1,"Custom source changed its resolution contract: "+source);
                SessionInfoRenderCheck.backdrop();RenderDiagnostics.reset();draw(false,null);
                require(Arrays.equals(expected,read(mc.getFramebuffer())),"Resource override fallback changed exact RGBA: "+source);
                require(RenderDiagnostics.pairedGaussianPasses==0&&RenderDiagnostics.pairedGaussianFallbacks==2,"Custom source entered paired shader");
                require(RenderDiagnostics.sceneBlurPixels==expectedPixels(1),"Custom source entered reduced resolution");
                JsonObject row=new JsonObject();row.addProperty("check","resource-override");row.addProperty("source",source);row.addProperty("exact_rgba",true);checks.add(row);
            }
            resources.source=null;resources.reloadResources(Collections.emptyList());require(GaussianBlur.effectiveSceneBlurDivisor()==4,"Reload did not restore eligible quality mode");
            SessionInfoRenderCheck.backdrop();RenderDiagnostics.reset();draw(false,null);require(RenderDiagnostics.pairedGaussianPasses==1,"Reload did not re-enable paired second pass");
        }finally{SessionInfoRenderCheck.set(mc,"mcResourceManager",original);}
    }

    private void resourcesAndResize()throws Exception{
        for(int[] size:new int[][]{{960,640,2},{853,481,2},{320,241,1}}){
            resize(size[0],size[1],size[2]);PerformanceOptions.configure(false,false,true,4);SessionInfoRenderCheck.backdrop();draw(false,null);
            RenderDiagnostics.reset();for(int i=0;i<4;i++){SessionInfoRenderCheck.backdrop();draw(false,null);}
            require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.shaderCompiles==0,"Warm scene recreated native resources");
            List<Integer> textures=new ArrayList<>();for(ArrayListSurface owner:new ArrayListSurface[]{scene,child}){
                for(Framebuffer f:(Framebuffer[])field(owner,"buffers"))if(f!=null)textures.add(f.framebufferTexture);
                Framebuffer f=(Framebuffer)field(owner,"sceneScratch");if(f!=null)textures.add(f.framebufferTexture);
            }
            ShaderUtil shader=(ShaderUtil)staticField(GaussianBlur.class,"paired");int program=shader.getProgramId();
            ArrayListSurface.releaseResources();GaussianBlur.releaseResources();
            for(int texture:textures)require(!glIsTexture(texture),"Released surface texture survived");require(!GL20.glIsProgram(program),"Released paired program survived");
            JsonObject row=new JsonObject();row.addProperty("check","warm-resources-resize-release");row.addProperty("width",size[0]);row.addProperty("height",size[1]);row.addProperty("passed",true);checks.add(row);
        }
    }

    private JsonArray measure(String mode,int warmup,int samples)throws Exception{
        int divisor=mode.equals("half")?2:mode.equals("quarter")?4:1;boolean paired=mode.equals("paired");PerformanceOptions.configure(false,false,paired,divisor);
        JsonArray scenarios=new JsonArray();
        for(int[] size:new int[][]{{960,640,2},{853,481,2}}){
            resize(size[0],size[1],size[2]);SessionInfoRenderCheck.backdrop();long cold=System.nanoTime();draw(false,null);glFinish();double coldMs=(System.nanoTime()-cold)/1e6;
            for(int i=0;i<warmup;i++){SessionInfoRenderCheck.backdrop();draw(false,null);glFinish();}
            ThreadMXBean memory=memoryCounter();long thread=Thread.currentThread().getId();double[] submit=new double[samples],complete=new double[samples],allocated=new double[samples];
            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
            for(int i=0;i<samples;i++){
                SessionInfoRenderCheck.backdrop();glFinish();long bytes=memory==null?-1:memory.getThreadAllocatedBytes(thread),start=System.nanoTime();draw(false,null);long submitted=System.nanoTime();glFinish();long done=System.nanoTime();
                submit[i]=(submitted-start)/1e6;complete[i]=(done-start)/1e6;allocated[i]=memory==null?-1:memory.getThreadAllocatedBytes(thread)-bytes;
            }
            require(RenderDiagnostics.sceneBlurPixels==expectedPixels(divisor)*samples,"Benchmark did not submit expected pixel work");
            require(RenderDiagnostics.pairedGaussianPasses==(paired?2L*samples:0),"Benchmark paired mode was inactive");
            require(RenderDiagnostics.pairedGaussianFallbacks==0,"Benchmark unexpectedly took a fallback");
            require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.shaderCompiles==0,"Warm benchmark recreated resources");
            JsonObject row=new JsonObject();String name="glass_scene_"+size[0]+"x"+size[1];row.addProperty("name",name);row.addProperty("width",size[0]);row.addProperty("height",size[1]);row.addProperty("scale",size[2]);row.addProperty("divisor",divisor);
            row.addProperty("first_workload_completed_ms",coldMs);row.addProperty("scene_blur_pixels",RenderDiagnostics.sceneBlurPixels);row.addProperty("paired_passes",RenderDiagnostics.pairedGaussianPasses);row.addProperty("fallback_passes",RenderDiagnostics.pairedGaussianFallbacks);row.addProperty("framebuffer_creates",RenderDiagnostics.framebufferCreates);row.addProperty("shader_compiles",RenderDiagnostics.shaderCompiles);
            row.add("submission_ms",distribution(submit));row.add("completed_ms",distribution(complete));row.add("allocated_bytes",distribution(allocated));
            RenderDiagnostics.enabled=false;SessionInfoRenderCheck.backdrop();byte[] backdrop=read(mc.getFramebuffer());draw(false,null);byte[] image=read(mc.getFramebuffer());require(!Arrays.equals(backdrop,image),"Benchmark UI invisible");row.addProperty("visible_output_verified",true);
            png(name,image,size[0],size[1]);Files.write(output.resolve(name+".rgba"),image);scenarios.add(row);
        }
        return scenarios;
    }

    /** Real nested materials share the full clean scene and the selected soft texture. */
    private void draw(boolean inspect,byte[] backdrop){
        UiEffects.beginFrame();scene.withScene(Theme.PinkLemonade,true,true,11,()->{
            UiEffects.SceneContext parent=UiEffects.scene();
            if(inspect)try(RenderState ignored=new RenderState()){
                int divisor=GaussianBlur.effectiveSceneBlurDivisor();requireTextureSize(parent.clean,mc.displayWidth,mc.displayHeight);
                requireTextureSize(parent.soft,SceneBlurSize.dimension(mc.displayWidth,divisor),SceneBlurSize.dimension(mc.displayHeight,divisor));
                if(backdrop!=null)require(Arrays.equals(backdrop,readTexture(parent.clean,mc.displayWidth,mc.displayHeight)),"Scene snapshot lost full-resolution clean pixels");
            }
            child.panel(20,18,210,132,10,Theme.PinkLemonade,true,true,false,11,.65f,.7f,()->{
                if(inspect)require(UiEffects.scene().soft==parent.soft,"Nested panel recomputed or lost borrowed scene");
                UIUtils.drawSceneRound(36,35,160,38,7);UIUtils.drawGradientHorizontal(36,85,110,4,2,new Color(235,55,173),new Color(35,165,225));
            });
            UIUtils.drawSceneRound(252,45,125,88,9);
        });
        require(UiEffects.scene()==null,"Scene stack leaked");
    }

    private long expectedPixels(int divisor){return 2L*SceneBlurSize.dimension(mc.displayWidth,divisor)*SceneBlurSize.dimension(mc.displayHeight,divisor);}
    private void resize(int width,int height,int scale){mc.displayWidth=width;mc.displayHeight=height;mc.gameSettings.guiScale=scale;mc.getFramebuffer().createBindFramebuffer(width,height);}
    private static Framebuffer target(int width,int height){Framebuffer target=new Framebuffer(width,height,false);filters(target,GL_LINEAR,GL12.GL_CLAMP_TO_EDGE);return target;}
    private static void filters(Framebuffer framebuffer,int filter,int wrap){framebuffer.setFramebufferFilter(filter);bind(framebuffer.framebufferTexture);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,wrap);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,wrap);}
    private static void bind(int texture){GL13.glActiveTexture(GL13.GL_TEXTURE0);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GlStateManager.bindTexture(texture);glBindTexture(GL_TEXTURE_2D,texture);}
    private static void pattern(Framebuffer input){ByteBuffer bytes=BufferUtils.createByteBuffer(input.framebufferWidth*input.framebufferHeight*4);for(int y=0;y<input.framebufferHeight;y++)for(int x=0;x<input.framebufferWidth;x++){bytes.put((byte)((x*73+y*41)^(x*y)));bytes.put((byte)(((x/3+y/5)&1)==0?255:0));bytes.put((byte)(x*5-y*11));bytes.put((byte)(x+y));}bytes.flip();bind(input.framebufferTexture);glTexSubImage2D(GL_TEXTURE_2D,0,0,0,input.framebufferWidth,input.framebufferHeight,GL_RGBA,GL_UNSIGNED_BYTE,bytes);}
    private static byte[] read(Framebuffer target){target.bindFramebuffer(true);ByteBuffer buffer=BufferUtils.createByteBuffer(target.framebufferWidth*target.framebufferHeight*4);glReadPixels(0,0,target.framebufferWidth,target.framebufferHeight,GL_RGBA,GL_UNSIGNED_BYTE,buffer);byte[] result=new byte[buffer.capacity()];buffer.get(result);return result;}
    private static byte[] readTexture(int texture,int width,int height){bind(texture);ByteBuffer buffer=BufferUtils.createByteBuffer(width*height*4);glGetTexImage(GL_TEXTURE_2D,0,GL_RGBA,GL_UNSIGNED_BYTE,buffer);byte[] result=new byte[buffer.capacity()];buffer.get(result);return result;}
    private static void requireTextureSize(int texture,int width,int height){bind(texture);require(glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_WIDTH)==width&&glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_HEIGHT)==height,"Wrong clean/soft physical dimensions");}
    private JsonObject compare(byte[] expected,byte[] actual,int maxError,String name){require(expected.length==actual.length,"Pixel size mismatch");int max=0;long sum=0,changed=0;for(int i=0;i<expected.length;i++){int delta=Math.abs((expected[i]&255)-(actual[i]&255));if((i&3)==3)require(delta==0,"Alpha changed: "+name+" at "+i);else{max=Math.max(max,delta);sum+=delta;if(delta>0)changed++;}}require(max<=maxError,"RGB error exceeds fixed "+maxError+"/255 contract: "+name+" max="+max);JsonObject result=new JsonObject();result.addProperty("check",name);result.addProperty("max_rgb_error",max);result.addProperty("mean_rgb_error",sum/(expected.length*.75));result.addProperty("changed_channels",changed);result.addProperty("alpha_exact",true);return result;}
    private static void assertCorners(byte[] before,byte[] after,String message){for(int pixel:new int[]{0,1,before.length/4-2,before.length/4-1})for(int channel=0;channel<4;channel++)require(before[pixel*4+channel]==after[pixel*4+channel],message);}
    private void png(String name,byte[] pixels,int width,int height)throws IOException{BufferedImage image=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<height;y++)for(int x=0;x<width;x++){int p=(y*width+x)*4;image.setRGB(x,height-1-y,(pixels[p+3]&255)<<24|(pixels[p]&255)<<16|(pixels[p+1]&255)<<8|(pixels[p+2]&255));}ImageIO.write(image,"png",output.resolve(name+".png").toFile());}
    private static Object field(Object owner,String name){try{return SessionInfoRenderCheck.get(owner,name);}catch(Exception failure){throw new AssertionError(failure);}}
    private static Object staticField(Class<?> owner,String name){try{Field field=owner.getDeclaredField(name);field.setAccessible(true);return field.get(null);}catch(Exception failure){throw new AssertionError(failure);}}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static JsonObject distribution(double[] values){double[] sorted=values.clone();Arrays.sort(sorted);JsonObject result=new JsonObject();result.addProperty("p50",sorted[(int)Math.ceil(sorted.length*.5)-1]);result.addProperty("p95",sorted[(int)Math.ceil(sorted.length*.95)-1]);result.addProperty("p99",sorted[(int)Math.ceil(sorted.length*.99)-1]);JsonArray raw=new JsonArray();for(double value:values)raw.add(value);result.add("samples",raw);return result;}
    private static ThreadMXBean memoryCounter(){java.lang.management.ThreadMXBean raw=ManagementFactory.getThreadMXBean();if(!(raw instanceof ThreadMXBean))return null;ThreadMXBean bean=(ThreadMXBean)raw;if(!bean.isThreadAllocatedMemorySupported())return null;if(!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);return bean;}

    private static final class ReloadingResources implements IReloadableResourceManager {
        final IResourceManager delegate;final List<IResourceManagerReloadListener> listeners=new ArrayList<>();String source;
        ReloadingResources(IResourceManager delegate){this.delegate=delegate;}
        @Override public Set<String> getResourceDomains(){return delegate.getResourceDomains();}
        @Override public IResource getResource(ResourceLocation location)throws IOException{
            IResource actual=delegate.getResource(location);if(source==null||!location.getResourcePath().equals("Flauxy/Shaders/"+source))return actual;
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream input=actual.getInputStream()){byte[] block=new byte[4096];int n;while((n=input.read(block))!=-1)bytes.write(block,0,n);}
            bytes.write("\n// Deliberate native source override\n".getBytes(StandardCharsets.UTF_8));return new SimpleResource("scene-blur-override",location,new ByteArrayInputStream(bytes.toByteArray()),null,null);
        }
        @Override public List<IResource> getAllResources(ResourceLocation location)throws IOException{return Collections.singletonList(getResource(location));}
        @Override public void registerReloadListener(IResourceManagerReloadListener listener){listeners.add(listener);listener.onResourceManagerReload(this);}
        @Override public void reloadResources(List<IResourcePack> ignored){for(IResourceManagerReloadListener listener:new ArrayList<>(listeners))listener.onResourceManagerReload(this);}
    }
}
