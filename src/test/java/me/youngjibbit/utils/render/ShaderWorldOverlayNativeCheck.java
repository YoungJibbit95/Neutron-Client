package me.youngjibbit.utils.render;

import com.mojang.authlib.properties.PropertyMap;
import java.io.*;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.*;
import java.nio.file.Files;
import java.util.Arrays;
import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event3D;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.zero.alpine.listener.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import net.optifine.shaders.Shaders;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.filter.AbstractFilter;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL11.*;

/** Real GL and production deferred dispatch; synthetic camera/hand depth/final color, no external pack. */
public final class ShaderWorldOverlayNativeCheck {
    private static final float PARTIAL=.375f;
    private static final File OUT=new File("shader-world-overlay-preview");
    private static Minecraft mc;
    private static CustomEntityRenderer renderer;
    private static int frame;

    private static final class Overlay implements Subscriber {
        int calls,eye;
        boolean fail,release;
        float[] projection,model;
        @Subscribe final Listener<Event3D> event=new Listener<>(e->{
            calls++;
            require(e.getPass()==eye&&e.getPartialTicks()==PARTIAL,"Captured event parameters");
            equal(projection,matrix(GL_PROJECTION_MATRIX),"world projection");
            equal(model,matrix(GL_MODELVIEW_MATRIX),"world modelview");
            require(glGetInteger(GL_DEPTH_FUNC)==GL_LEQUAL&&glIsEnabled(GL_DEPTH_TEST),"World depth contract");
            require(Arrays.equals(mask(),new boolean[]{eye!=0,eye!=1,eye!=1,true}),"Final eye color mask");
            require(glGetInteger(GL13.GL_ACTIVE_TEXTURE)==GL13.GL_TEXTURE0&&glGetInteger(GL_TEXTURE_BINDING_2D)==0,
                    "Attachment feedback at callback entry");
            pixel(mc.displayWidth-3,mc.displayHeight-3,51,102,153); // input is the pack's final image
            GlStateManager.depthMask(false);glDepthMask(false);
            GlStateManager.enableBlend();glEnable(GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GlStateManager.blendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
            glColor4f(1,0,0,.5f);quad();
            GlStateManager.blendFunc(GL_ONE,GL_ONE);glBlendFunc(GL_ONE,GL_ONE);
            glColor4f(0,0,.2f,1);quad();
            GlStateManager.depthMask(true);glDepthMask(true);glClearDepth(0);glClear(GL_DEPTH_BUFFER_BIT);
            if(release)renderer.releaseClientRenderResources();
            if(fail)throw new IllegalStateException("synthetic deferred overlay failure");
        });
    }
    private static final class Scene implements AutoCloseable {
        final Framebuffer source,destination;
        final int depth,sw,sh,dw,dh;
        Scene(int dw,int dh,int sw,int sh)throws Exception{
            this.dw=dw;this.dh=dh;this.sw=sw;this.sh=sh;
            mc.displayWidth=dw;mc.displayHeight=dh;
            destination=new Framebuffer(dw,dh,true);source=new Framebuffer(sw,sh,false);
            depth=glGenTextures();texture(depth);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D,GL14.GL_TEXTURE_COMPARE_MODE,GL_NONE);
            glTexImage2D(GL_TEXTURE_2D,0,GL14.GL_DEPTH_COMPONENT24,sw,sh,0,GL_DEPTH_COMPONENT,GL_FLOAT,(ByteBuffer)null);
            source.bindFramebuffer(true);
            OpenGlHelper.glFramebufferTexture2D(OpenGlHelper.GL_FRAMEBUFFER,OpenGlHelper.GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth,0);
            require(OpenGlHelper.glCheckFramebufferStatus(OpenGlHelper.GL_FRAMEBUFFER)==OpenGlHelper.GL_FRAMEBUFFER_COMPLETE,
                    "Fixture source framebuffer");
            field(Minecraft.class,"framebufferMc").set(mc,destination);
            field(Shaders.class,"dfb").setInt(null,source.framebufferObject);
            ((IntBuffer)field(Shaders.class,"dfbDepthTextures").get(null)).put(0,depth);
            field(Shaders.class,"usedDepthBuffers").setInt(null,1);
            Shaders.renderWidth=sw;Shaders.renderHeight=sh;
        }
        void capture(Overlay overlay,int eye)throws Exception{
            source.bindFramebuffer(true);Shaders.isRenderingWorld=Shaders.isRenderingDfb=true;
            field(Shaders.class,"frameCounter").setInt(null,++frame);
            glDisable(GL_SCISSOR_TEST);GlStateManager.depthMask(true);glDepthMask(true);
            glClearDepth(1);glClear(GL_DEPTH_BUFFER_BIT);
            // Shaders.setViewport enables every gbuffer channel even for anaglyph eyes.
            GlStateManager.colorMask(true,true,true,true);glColorMask(true,true,true,true);
            glMatrixMode(GL_PROJECTION);glLoadIdentity();
            glMatrixMode(GL_MODELVIEW);glLoadIdentity();glTranslatef(.25f,0,0);
            overlay.projection=matrix(GL_PROJECTION_MATRIX);overlay.model=matrix(GL_MODELVIEW_MATRIX);overlay.eye=eye;
            int before=overlay.calls;renderer.renderWorldOverlays(eye,PARTIAL);
            require(overlay.calls==before,"Client color rendered before pack final");
            // A late hand/depth contribution arrives after camera capture.
            source.bindFramebuffer(true);glEnable(GL_SCISSOR_TEST);
            glScissor(0,0,sw/2,sh);glClearDepth(.2);glClear(GL_DEPTH_BUFFER_BIT);
            glScissor(sw/2,0,sw-sw/2,sh);glClearDepth(.8);glClear(GL_DEPTH_BUFFER_BIT);glDisable(GL_SCISSOR_TEST);
            destination.bindFramebuffer(true);Shaders.isRenderingDfb=false;
            GlStateManager.colorMask(true,true,true,true);glColorMask(true,true,true,true);
            glClearColor(.2f,.4f,.6f,1);glClearDepth(.9);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            glMatrixMode(GL_PROJECTION);glLoadIdentity();glScalef(2,3,1);
            glMatrixMode(GL_MODELVIEW);glLoadIdentity();glTranslatef(-.7f,.3f,0);
            GlStateManager.depthFunc(GL_ALWAYS);glDepthFunc(GL_ALWAYS);GlStateManager.depthMask(false);glDepthMask(false);
            glViewport(1,2,dw-2,dh-4);
        }
        @Override public void close(){source.deleteFramebuffer();destination.deleteFramebuffer();GlStateManager.deleteTexture(depth);}
    }

    public static void main(String[] args)throws Exception{
        OUT.mkdirs();System.setProperty("user.home",new File(OUT,"home").getAbsolutePath());
        File data=Files.createTempDirectory(OUT.toPath(),"fixture-").toFile();
        try(PbufferTestContext context=new PbufferTestContext()){
            mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(
                    new Session("WorldOverlayFixture","00000000000000000000000000000000","0","legacy"),
                    new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(64,48,false,false),
                    new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                    new GameConfiguration.GameInformation(false,"Deferred world overlay fixture"),
                    new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);
            Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
            renderer=(CustomEntityRenderer)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(CustomEntityRenderer.class);
            Overlay overlay=new Overlay();Pulsar.BUS.subscribe(overlay);
            Shaders.shaderPackLoaded=true;RenderDiagnostics.reset();RenderDiagnostics.enabled=true;
            try{
                try(Scene scene=new Scene(64,48,64,48)){
                    eyePassFlags(scene);
                    frame(scene,overlay,2,"native");int[] stable=owned();
                    frame(scene,overlay,0,"native-repeat");require(Arrays.equals(stable,owned()),"Warm GPU resource churn");
                    frame(scene,overlay,1,"native-eye");
                    frame(scene,overlay,2,"native-no-hand",true);
                    invalidation(scene,overlay);
                    Shaders.isShadowPass=true;int calls=overlay.calls;renderer.renderWorldOverlays(0,PARTIAL);
                    require(overlay.calls==calls,"Shadow-camera event");Shaders.isShadowPass=false;
                }
                try(Scene scene=new Scene(80,60,40,30)){
                    frame(scene,overlay,2,"scaled-half");
                    require(owned()[2]!=0&&owned()[1]!=owned()[2],"Scaled depth feedback");
                    frame(scene,overlay,2,"scaled-half-repeat");exception(scene,overlay);
                    int[] ids=owned();overlay.release=true;frame(scene,overlay,2,"release-in-callback");overlay.release=false;
                    deleted(ids);require(glIsTexture(scene.destination.framebufferTexture)&&glIsTexture(scene.depth),"Borrowed textures deleted");
                }
                try(Scene scene=new Scene(64,48,96,72)){
                    frame(scene,overlay,2,"scaled-up");int[] ids=owned();int calls=overlay.calls;
                    renderer.releaseClientRenderResources();renderer.renderShaderpackWorldOverlays(2,PARTIAL);
                    require(overlay.calls==calls,"Released snapshot replay");deleted(ids);
                    require(glIsTexture(scene.destination.framebufferTexture)&&glIsTexture(scene.depth),"Borrowed lifetime");
                }
                require(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"FBO lifetime imbalance");
                context.check();
            }finally{
                Pulsar.BUS.unsubscribe(overlay);renderer.releaseClientRenderResources();RenderDiagnostics.enabled=false;
                Shaders.shaderPackLoaded=false;Shaders.isRenderingWorld=Shaders.isRenderingDfb=Shaders.isShadowPass=false;
                field(Shaders.class,"dfb").setInt(null,0);((IntBuffer)field(Shaders.class,"dfbDepthTextures").get(null)).put(0,0);
            }
        }
        System.out.println("PASS: actual per-eye final-pass flags, deferred world camera/eyes, late hand depth, direct alpha/additive final-color blending, "
                +"source/main depth unchanged, native/half/supersampled sizes, reuse, expired captures, exceptions, detachment, lifecycle.");
        System.out.println("NOT COVERED: actual world or external shaderpack programs, full bootstrap, native Display, hardware FPS.");
    }

    private static void frame(Scene scene,Overlay overlay,int eye,String name)throws Exception{
        frame(scene,overlay,eye,name,false);
    }
    private static void eyePassFlags(Scene scene)throws Exception{
        net.minecraft.client.renderer.texture.ITextureObject previous=Shaders.defaultTexture;
        net.optifine.shaders.DefaultTexture dummy=new net.optifine.shaders.DefaultTexture();
        Shaders.defaultTexture=dummy;
        IntBuffer outputs=(IntBuffer)field(Shaders.class,"dfbDrawBuffers").get(null);
        int outputPosition=outputs.position(),outputLimit=outputs.limit(),firstOutput=outputs.get(0);
        outputs.position(0).limit(1);outputs.put(0,OpenGlHelper.GL_COLOR_ATTACHMENT0);
        try{
            // Actual production pass entry and endRender are executed. Empty pack programs
            // leave one real fixed-function final quad; this is not an external-pack fixture.
            Shaders.isRenderingWorld=false;Shaders.isCompositeRendered=true;
            for(int eye=0;eye<2;eye++){
                Shaders.beginRenderPass(eye,PARTIAL,0);
                require(Shaders.isRenderingWorld&&!Shaders.isCompositeRendered,"New eye inherited completed final pass");
                Shaders.setViewport(0,0,scene.dw,scene.dh);
                long draws=RenderDiagnostics.drawSubmissions;
                Shaders.endRender();
                require(Shaders.isCompositeRendered&&!Shaders.isRenderingWorld,"No-hand eye did not finish");
                require(RenderDiagnostics.drawSubmissions==draws+1,"No-hand eye missed its actual final draw");
                Shaders.endRender();
                require(RenderDiagnostics.drawSubmissions==draws+1,"Repeated endRender duplicated final draw");
            }
            Shaders.beginRenderPass(2,PARTIAL,0);Shaders.setViewport(0,0,scene.dw,scene.dh);
            long draws=RenderDiagnostics.drawSubmissions;
            Shaders.renderCompositeFinal();Shaders.endRender();
            require(RenderDiagnostics.drawSubmissions==draws+1,"Normal hand path duplicated final draw");
            Shaders.isShadowPass=true;
            int target=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
            Shaders.beginRenderPass(0,PARTIAL,0);
            require(!Shaders.isRenderingWorld&&Shaders.isCompositeRendered
                    &&target==glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),"Shadow pass reset main-eye state");
            require(glGetError()==GL_NO_ERROR,"Actual per-eye flags fixture GL error");
        }finally{
            Shaders.isShadowPass=false;Shaders.useProgram(Shaders.ProgramNone);
            outputs.limit(outputLimit);outputs.put(0,firstOutput);outputs.position(outputPosition);
            Shaders.defaultTexture=previous;dummy.deleteGlTexture();
        }
    }
    private static void frame(Scene scene,Overlay overlay,int eye,String name,boolean worldEnded)throws Exception{
        scene.capture(overlay,eye);
        if(worldEnded)Shaders.isRenderingWorld=false; // no-hand hook follows endRender's world-flag reset
        float[] sourceDepth=depth(scene.source),mainDepth=depth(scene.destination);
        float[] projection=matrix(GL_PROJECTION_MATRIX),model=matrix(GL_MODELVIEW_MATRIX);int[] viewport=viewport();
        int calls=overlay.calls;renderer.renderShaderpackWorldOverlays(eye,PARTIAL);
        require(overlay.calls==calls+1,"Deferred dispatch missing");
        renderer.renderShaderpackWorldOverlays(eye,PARTIAL);require(overlay.calls==calls+1,"Deferred dispatch duplicated");
        equal(projection,matrix(GL_PROJECTION_MATRIX),"final projection");equal(model,matrix(GL_MODELVIEW_MATRIX),"final model");
        require(Arrays.equals(viewport,viewport()),"Final viewport");
        require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)==scene.destination.framebufferObject,"Final target");
        require(glGetInteger(GL_DEPTH_FUNC)==GL_ALWAYS&&!glGetBoolean(GL_DEPTH_WRITEMASK),"Final depth state");
        require(Arrays.equals(mask(),new boolean[]{true,true,true,true}),"Final color mask");
        require(Arrays.equals(sourceDepth,depth(scene.source)),"Client wrote pack depth");
        require(Arrays.equals(mainDepth,depth(scene.destination)),"Client wrote main depth");
        scene.destination.bindFramebuffer(false);
        pixel(scene.dw*3/8,scene.dh/2,51,102,153);
        pixel(scene.dw*5/8,scene.dh/2,eye==0?51:153,eye==1?102:51,eye==1?153:128);
        pixel(scene.dw*7/8,scene.dh/2,51,102,153);
        save(scene,name);
        if(bridge()!=null){
            int[] ids=owned();
            require(ids[1]!=scene.depth&&ids[1]!=scene.destination.framebufferTexture,"Private depth ownership");
            try(RenderState ignored=new RenderState()){
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,ids[0]);
                int color=EXTFramebufferObject.glGetFramebufferAttachmentParameteriEXT(OpenGlHelper.GL_FRAMEBUFFER,
                        OpenGlHelper.GL_COLOR_ATTACHMENT0,EXTFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME_EXT);
                int depth=EXTFramebufferObject.glGetFramebufferAttachmentParameteriEXT(OpenGlHelper.GL_FRAMEBUFFER,
                        OpenGlHelper.GL_DEPTH_ATTACHMENT,EXTFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME_EXT);
                require(color==0&&depth==0,"Borrowed attachments retained after callback");
            }
        }
        require(glGetError()==GL_NO_ERROR,"Deferred overlay GL error: "+name);
    }
    private static void exception(Scene scene,Overlay overlay)throws Exception{
        scene.capture(overlay,2);
        float[] sourceDepth=depth(scene.source),mainDepth=depth(scene.destination);
        float[] projection=matrix(GL_PROJECTION_MATRIX),model=matrix(GL_MODELVIEW_MATRIX);int[] viewport=viewport();overlay.fail=true;
        PrintStream previous=System.err;ByteArrayOutputStream expected=new ByteArrayOutputStream();
        try(PrintStream sink=new PrintStream(expected,true,"UTF-8")){
            System.setErr(sink);
            try{renderer.renderShaderpackWorldOverlays(2,PARTIAL);throw new AssertionError("Callback failure swallowed");}
            catch(IllegalStateException failure){require(failure.getMessage().startsWith("synthetic"),"Unexpected callback failure");}
        }finally{System.setErr(previous);overlay.fail=false;}
        require(expected.toString("UTF-8").contains("synthetic deferred overlay failure"),"Expected Alpine diagnostic");
        equal(projection,matrix(GL_PROJECTION_MATRIX),"exception projection");equal(model,matrix(GL_MODELVIEW_MATRIX),"exception model");
        require(Arrays.equals(viewport,viewport()),"Exception viewport");
        require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)==scene.destination.framebufferObject,"Exception target");
        require(glGetInteger(GL_DEPTH_FUNC)==GL_ALWAYS&&!glGetBoolean(GL_DEPTH_WRITEMASK),"Exception depth state");
        require(Arrays.equals(sourceDepth,depth(scene.source))&&Arrays.equals(mainDepth,depth(scene.destination)),"Exception depth ownership");
        int calls=overlay.calls;renderer.renderShaderpackWorldOverlays(2,PARTIAL);require(overlay.calls==calls,"Failed frame replay");
    }
    private static void invalidation(Scene scene,Overlay overlay)throws Exception{
        // The global native diagnostic guard must still reject every unexpected
        // warning/error. Capture only this exact, deliberately triggered message.
        final String expected="Shaderpack world overlay unavailable: captured camera/depth expired before the final pass. "
                +"Compatibility is unverified for this state.";
        final int[] diagnosticCount={0};
        LoggerConfig logger=((org.apache.logging.log4j.core.Logger)LogManager.getLogger(ShaderWorldOverlayBridge.class)).get();
        AbstractFilter filter=new AbstractFilter(){
            @Override public Filter.Result filter(LogEvent event){
                if(event.getLevel()==Level.WARN&&ShaderWorldOverlayBridge.class.getName().equals(event.getLoggerName())
                        &&expected.equals(event.getMessage().getFormattedMessage())){
                    diagnosticCount[0]++;return Filter.Result.DENY;
                }
                return Filter.Result.NEUTRAL;
            }
        };
        logger.addFilter(filter);
        try{invalidationCases(scene,overlay);}finally{logger.removeFilter(filter);}
        require(diagnosticCount[0]==2,"Expected scoped/rate-limited expired-capture diagnostics");
        Files.write(new File(OUT,"expected-invalidation-diagnostics.txt").toPath(),
                ("Expected occurrences: "+diagnosticCount[0]+"\n"+expected+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
    private static void invalidationCases(Scene scene,Overlay overlay)throws Exception{
        scene.capture(overlay,2);
        Field generation=field(Shaders.class,"renderResourceGeneration");
        generation.setLong(null,generation.getLong(null)+1);
        reject(overlay,2,PARTIAL,"generation");
        long deletes=RenderDiagnostics.framebufferDeletes;
        frame(scene,overlay,2,"generation-rebuilt");
        require(RenderDiagnostics.framebufferDeletes==deletes+1,"Generation did not retire old overlay target");

        scene.capture(overlay,2);
        net.minecraft.client.multiplayer.WorldClient previous=mc.theWorld;
        Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);
        mc.theWorld=(net.minecraft.client.multiplayer.WorldClient)((sun.misc.Unsafe)unsafe.get(null))
                .allocateInstance(net.minecraft.client.multiplayer.WorldClient.class);
        try{reject(overlay,2,PARTIAL,"world");}finally{mc.theWorld=previous;}

        scene.capture(overlay,2);mc.displayWidth++;
        try{reject(overlay,2,PARTIAL,"resize");}finally{mc.displayWidth=scene.dw;}
        scene.capture(overlay,2);field(Shaders.class,"frameCounter").setInt(null,++frame);
        reject(overlay,2,PARTIAL,"frame");
        scene.capture(overlay,2);reject(overlay,1,PARTIAL,"eye");
        scene.capture(overlay,2);reject(overlay,2,PARTIAL+.1f,"partial ticks");

        scene.capture(overlay,2);int[] ids=owned();int calls=overlay.calls;
        Shaders.shaderPackLoaded=false;
        try{renderer.renderShaderpackWorldOverlays(2,PARTIAL);}finally{Shaders.shaderPackLoaded=true;}
        require(overlay.calls==calls&&bridge()==null,"Disabled pack retained a pending overlay");deleted(ids);
        require(glIsTexture(scene.depth)&&glIsTexture(scene.destination.framebufferTexture),"Disabled pack deleted borrowed textures");
        frame(scene,overlay,2,"pack-reactivated");
    }
    private static void reject(Overlay overlay,int eye,float partial,String reason)throws Exception{
        int calls=overlay.calls;float[] projection=matrix(GL_PROJECTION_MATRIX),model=matrix(GL_MODELVIEW_MATRIX);
        int target=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);int[] viewport=viewport();
        renderer.renderShaderpackWorldOverlays(eye,partial);
        require(overlay.calls==calls,"Expired "+reason+" capture dispatched");
        require(bridge()!=null&&bridge().failure()!=null&&bridge().failure().contains("expired"),"Missing "+reason+" diagnostic");
        renderer.renderShaderpackWorldOverlays(2,PARTIAL);require(overlay.calls==calls,"Rejected capture replayed: "+reason);
        equal(projection,matrix(GL_PROJECTION_MATRIX),"rejected projection");equal(model,matrix(GL_MODELVIEW_MATRIX),"rejected model");
        require(Arrays.equals(viewport,viewport())&&target==glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),
                "Rejected capture changed GL targets: "+reason);
    }
    private static ShaderWorldOverlayBridge bridge()throws Exception{
        return (ShaderWorldOverlayBridge)field(CustomEntityRenderer.class,"shaderWorldOverlays").get(renderer);
    }
    private static int[] owned()throws Exception{
        ShaderWorldOverlayBridge bridge=bridge();require(bridge!=null,"Bridge absent");
        return new int[]{field(ShaderWorldOverlayBridge.class,"aliasFramebuffer").getInt(bridge),
                field(ShaderWorldOverlayBridge.class,"copiedDepth").getInt(bridge),
                field(ShaderWorldOverlayBridge.class,"scaledDepth").getInt(bridge)};
    }
    private static void deleted(int[] ids){
        require(!EXTFramebufferObject.glIsFramebufferEXT(ids[0])&&!glIsTexture(ids[1])&&(ids[2]==0||!glIsTexture(ids[2])),"Owned GPU resource leak");
    }
    private static float[] depth(Framebuffer framebuffer){
        try(RenderState ignored=new RenderState()){
            framebuffer.bindFramebuffer(false);
            FloatBuffer buffer=BufferUtils.createFloatBuffer(framebuffer.framebufferWidth*framebuffer.framebufferHeight);
            glReadPixels(0,0,framebuffer.framebufferWidth,framebuffer.framebufferHeight,GL_DEPTH_COMPONENT,GL_FLOAT,buffer);
            float[] values=new float[buffer.capacity()];buffer.get(values);return values;
        }
    }
    private static void pixel(int x,int y,int r,int g,int b){
        ByteBuffer value=BufferUtils.createByteBuffer(4);glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,value);
        require(Math.abs((value.get(0)&255)-r)<=2&&Math.abs((value.get(1)&255)-g)<=2&&Math.abs((value.get(2)&255)-b)<=2,
                "Final-color/occlusion pixel "+x+","+y+": "+(value.get(0)&255)+","+(value.get(1)&255)+","+(value.get(2)&255));
    }
    private static void save(Scene scene,String name)throws Exception{
        ByteBuffer pixels=BufferUtils.createByteBuffer(scene.dw*scene.dh*4);
        glReadPixels(0,0,scene.dw,scene.dh,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        java.awt.image.BufferedImage image=new java.awt.image.BufferedImage(scene.dw,scene.dh,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<scene.dh;y++)for(int x=0;x<scene.dw;x++){
            int p=(y*scene.dw+x)*4;image.setRGB(x,scene.dh-1-y,0xff000000|(pixels.get(p)&255)<<16
                    |(pixels.get(p+1)&255)<<8|(pixels.get(p+2)&255));
        }
        javax.imageio.ImageIO.write(image,"png",new File(OUT,name+".png"));
    }
    private static Field field(Class<?> type,String name)throws Exception{
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field;
    }
    private static float[] matrix(int name){
        FloatBuffer buffer=BufferUtils.createFloatBuffer(16);glGetFloat(name,buffer);float[] values=new float[16];buffer.get(values);return values;
    }
    private static int[] viewport(){
        IntBuffer buffer=BufferUtils.createIntBuffer(16);glGetInteger(GL_VIEWPORT,buffer);
        return new int[]{buffer.get(0),buffer.get(1),buffer.get(2),buffer.get(3)};
    }
    private static boolean[] mask(){
        ByteBuffer buffer=BufferUtils.createByteBuffer(16);glGetBoolean(GL_COLOR_WRITEMASK,buffer);
        return new boolean[]{buffer.get(0)!=0,buffer.get(1)!=0,buffer.get(2)!=0,buffer.get(3)!=0};
    }
    private static void equal(float[] a,float[] b,String name){
        for(int i=0;i<a.length;i++)require(Math.abs(a[i]-b[i])<=.00001f,"Changed "+name+"["+i+"]");
    }
    private static void texture(int id){
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(id);glBindTexture(GL_TEXTURE_2D,id);
    }
    private static void quad(){
        glBegin(GL_QUADS);glVertex3f(-.75f,-.5f,0);glVertex3f(.25f,-.5f,0);glVertex3f(.25f,.5f,0);glVertex3f(-.75f,.5f,0);glEnd();
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
