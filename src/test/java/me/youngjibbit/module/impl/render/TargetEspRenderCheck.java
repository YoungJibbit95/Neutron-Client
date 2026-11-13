package me.youngjibbit.module.impl.render;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import static org.lwjgl.opengl.GL11.*;

/** Production world-overlay geometry/GLSL on a synthetic scene; never a full gameplay or FPS claim. */
public final class TargetEspRenderCheck {
    private static final File OUT = new File("target-esp-preview");
    private static final JsonObject REPORT = new JsonObject();
    private static final int WIDTH = 640, HEIGHT = 480, BACKGROUND = 0x060912;
    private static Minecraft mc;
    private static ClientSettings settings;
    private static final TargetEspRenderer renderer = new TargetEspRenderer();

    public static void main(String[] args) throws Exception {
        OUT.mkdirs();
        File data=Files.createTempDirectory(OUT.toPath(),"fixture-").toFile();
        System.setProperty("user.home",new File(data,"home").getAbsolutePath());
        REPORT.addProperty("format_version",1);
        REPORT.addProperty("status","incomplete");
        REPORT.addProperty("scope","Production TargetESP GLSL and bounded world geometry, synthetic positions and Pbuffer/FBO. No game-world bootstrap, server acceptance, shader-pack compatibility, native Display or hardware FPS claim.");
        try(PbufferTestContext context=new PbufferTestContext()) {
            initialize(context,data);
            try {
                appearanceAndMotion();
                exactMarkerAndControls();
                interpolatedMarkerKeepsTheObservationExact();
                depthAndClipping();
                stateRestoration();
                fallbackAndEmptyFrames();
                boundedResources();
                context.check();
                require(glGetError()==GL_NO_ERROR,"TargetESP left an OpenGL error");
            } finally {
                renderer.close();
                mc.getFramebuffer().deleteFramebuffer();
                UiEffects.invalidate();
            }
            require(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"Framebuffer lifetime imbalance");
            require(RenderDiagnostics.programCreates==RenderDiagnostics.programDeletes,"Program lifetime imbalance");
            require(RenderDiagnostics.shaderCompiles==RenderDiagnostics.shaderDeletes,"Shader lifetime imbalance");
            require(glGetError()==GL_NO_ERROR,"TargetESP cleanup left an OpenGL error");
            REPORT.addProperty("program_creates",RenderDiagnostics.programCreates);
            REPORT.addProperty("program_deletes",RenderDiagnostics.programDeletes);
            REPORT.addProperty("framebuffer_creates",RenderDiagnostics.framebufferCreates);
            REPORT.addProperty("framebuffer_deletes",RenderDiagnostics.framebufferDeletes);
            REPORT.addProperty("status","passed");
            report();context.check();
        } catch(Throwable error) {
            REPORT.addProperty("status","failed");REPORT.addProperty("error",error.toString());report();throw error;
        }
        System.out.println("PASS: TargetESP production halo/marker, exact placement, depth, clip, state, fallback and bounded resource lifetime");
    }

    private static void initialize(PbufferTestContext context,File data)throws Exception {
        mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(
                new Session("TargetEspFixture","00000000000000000000000000000000","0","legacy"),
                new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                new GameConfiguration.DisplayInformation(WIDTH,HEIGHT,false,false),
                new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                new GameConfiguration.GameInformation(false,"TargetESP Pbuffer check"),
                new GameConfiguration.ServerInformation(null,0)));
        context.initialize(mc,data);
        IMetadataSerializer metadata=new IMetadataSerializer();
        metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);
        metadata.registerMetadataSectionType(new AnimationMetadataSectionSerializer(),AnimationMetadataSection.class);
        metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(),PackMetadataSection.class);
        SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(metadata);
        resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
        SessionInfoRenderCheck.set(mc,"mcResourceManager",resources);
        RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
        SessionInfoRenderCheck.set(mc,"framebufferMc",new Framebuffer(WIDTH,HEIGHT,true));
        settings=new ClientSettings();
        SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager(Collections.singletonList(settings)));
        Theme.setDefaultTheme(Theme.SynthWave);
        settings.gradients.setValue(true);settings.gradientMotion.setValue("Static");
        settings.animations.setValue(true);settings.reducedMotion.setValue(false);
        UiEffects.invalidate();UiEffects.beginFrame();
        REPORT.addProperty("java",System.getProperty("java.runtime.version"));
        REPORT.addProperty("gl_vendor",glGetString(GL_VENDOR));
        REPORT.addProperty("gl_renderer",glGetString(GL_RENDERER));
        REPORT.addProperty("gl_version",glGetString(GL_VERSION));
    }

    private static TargetEspRenderer.Frame halo(float time,float lift) {
        TargetEspRenderer.Frame frame=new TargetEspRenderer.Frame();
        frame.halo=true;frame.radiusX=.53;frame.radiusZ=.53;frame.height=1.8;
        frame.time=time;frame.lift=lift;frame.direction=1;return frame;
    }
    private static TargetEspRenderer.Frame marker() {
        TargetEspRenderer.Frame frame=new TargetEspRenderer.Frame();
        frame.marker=true;frame.markerHit=true;frame.markerX=.31;frame.markerY=.91;frame.markerZ=0;return frame;
    }
    private static void appearanceAndMotion()throws Exception {
        TargetEspRenderer.Frame frame=halo(0,.24f);
        TargetEspRenderer.Style style=new TargetEspRenderer.Style();
        begin(true);renderer.render(frame,style);BufferedImage low=capture();save(low,"halo-lower.png");
        require(changed(low)>250,"Halo has no visible gradient ribbons");
        require(colored(low)>100,"Halo lost its colored gradient glow");
        frame.time=2;begin(true);renderer.render(frame,style);BufferedImage rotated=capture();
        require(differences(low,rotated)>75,"Orbit head/wake does not rotate");
        frame.lift=.78f;frame.leanX=.12f;frame.leanZ=-.05f;
        begin(true);renderer.render(frame,style);BufferedImage high=capture();save(high,"halo-upper-inertia.png");
        require(differences(rotated,high)>350,"Halo lift/inertia has no visible effect");
        require(centroidY(high)<centroidY(rotated)-60,"Ascending halo does not follow target height");
        style.trail=0;begin(true);renderer.render(frame,style);BufferedImage noTrail=capture();save(noTrail,"halo-no-wake.png");
        require(differences(noTrail,high)>50,"Halo Trail does not control the dissipating ribbons");
        style.trail=.65f;style.glow=0;begin(true);renderer.render(frame,style);BufferedImage noGlow=capture();
        style.glow=2;begin(true);renderer.render(frame,style);BufferedImage glow=capture();
        require(energy(glow)>energy(noGlow)*1.05,"Glow Strength does not affect shader falloff");
        REPORT.addProperty("halo_pixels",changed(low));
        System.out.println("PASS: gradient halo, orbit/wake, height, inertia, trail and glow controls");
    }
    private static void exactMarkerAndControls()throws Exception {
        TargetEspRenderer.Frame frame=marker();TargetEspRenderer.Style style=new TargetEspRenderer.Style();
        style.markerSize=.16f;
        begin(false);renderer.render(frame,style);BufferedImage hit=capture();save(hit,"hitpoint-hit.png");
        int[] peak=brightest(hit);
        double expectedX=(frame.markerX+2)*WIDTH/4,expectedY=HEIGHT-(frame.markerY+.5)*HEIGHT/3;
        require(Math.abs(peak[0]+.5-expectedX)<2&&Math.abs(peak[1]+.5-expectedY)<2,"Marker bright core moved away from exact projected ray point");
        require(changed(hit)>100&&colored(hit)>40,"Layered hitpoint glow is missing");
        frame.markerHit=false;begin(false);renderer.render(frame,style);BufferedImage miss=capture();save(miss,"hitpoint-miss.png");
        require(brightness(hit.getRGB(peak[0],peak[1]))>brightness(miss.getRGB(peak[0],peak[1]))+80,"Miss marker incorrectly claims a bright hit core");
        frame.markerHit=true;style.markerSize=.045f;begin(false);renderer.render(frame,style);BufferedImage small=capture();
        require(changed(hit)>changed(small)*5,"Hitpoint Size does not change the marker footprint");
        frame.markerX+=.4;style.markerSize=.16f;begin(false);renderer.render(frame,style);BufferedImage moved=capture();
        int[] next=brightest(moved);
        require(Math.abs(next[0]-peak[0]-64)<=1&&Math.abs(next[1]-peak[1])<=1,"Marker motion was smoothed or delayed instead of following the input point");
        style.customColor=true;style.color=0xffff3030;begin(false);renderer.render(frame,style);BufferedImage red=capture();save(red,"hitpoint-custom-red.png");
        require(differences(red,moved)>100,"Custom Color does not reach production marker shader");
        // Translation invariance checks world-to-view subtraction at distant coordinates.
        frame.markerX+=10000;frame.markerY+=64;frame.markerZ-=9000;
        frame.cameraX=10000;frame.cameraY=64;frame.cameraZ=-9000;
        begin(false);renderer.render(frame,style);
        require(differences(red,capture())==0,"World-to-camera translation shifts the exact marker");
        REPORT.addProperty("hitpoint_pixels",changed(hit));
        System.out.println("PASS: exact unsmoothed hitpoint projection, hit/miss, size, custom color and camera translation");
    }
    private static void depthAndClipping()throws Exception {
        TargetEspRenderer.Frame frame=marker();TargetEspRenderer.Style style=new TargetEspRenderer.Style();style.markerSize=.2f;
        begin(false);occluder();BufferedImage background=capture();
        renderer.render(frame,style);BufferedImage hidden=capture();
        require(differences(background,hidden)==0,"Depth-aware marker leaks through an opaque surface");
        style.throughWalls=true;renderer.render(frame,style);BufferedImage visible=capture();save(visible,"hitpoint-through-walls.png");
        require(differences(background,visible)>100,"Through Walls does not reveal the occluded marker");
        begin(false);glEnable(GL_SCISSOR_TEST);glScissor(0,0,WIDTH/2,HEIGHT);renderer.render(frame,style);
        require(changed(capture())==0,"TargetESP ignores inherited scissor clipping");glDisable(GL_SCISSOR_TEST);
        System.out.println("PASS: scene depth option and inherited scissor clipping");
    }

    private static void interpolatedMarkerKeepsTheObservationExact() throws Exception {
        TargetEspRenderer.Frame frame=marker();TargetEspRenderer.Style style=new TargetEspRenderer.Style();
        style.markerSize=.16f;
        frame.markerInterpolated=true;frame.displayMarkerX=frame.markerX-.4;
        frame.displayMarkerY=frame.markerY;frame.displayMarkerZ=frame.markerZ;
        double observedX=frame.markerX;
        begin(false);renderer.render(frame,style);BufferedImage presented=capture();
        int[] smooth=brightest(presented);
        frame.markerInterpolated=false;begin(false);renderer.render(frame,style);int[] raw=brightest(capture());
        require(Math.abs(raw[0]-smooth[0]-64)<=1&&raw[1]==smooth[1],"Renderer did not use the separate interpolated presentation point");
        require(frame.markerX==observedX,"Cosmetic interpolation changed the observed hitpoint");
        save(presented,"hitpoint-interpolated.png");
        frame.markerInterpolated=true;frame.displayMarkerX=Double.NaN;
        begin(false);renderer.render(frame,style);require(changed(capture())==0,"Invalid presentation coordinates left stale geometry");
        frame.clear();require(!frame.markerInterpolated,"Cleared frame retained the presentation flag");
        System.out.println("PASS: interpolated presentation is independent of the exact read-only observation and clears safely");
    }
    private static void stateRestoration() {
        begin(false);
        try(ShaderUtil sentinel=new ShaderUtil("#version 120\nvoid main(){gl_FragColor=vec4(.3,.6,.9,1.0);}",true);
            RenderState restore=new RenderState()) {
            glEnable(GL_SCISSOR_TEST);glScissor(70,80,350,240);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_GREATER);glDepthMask(true);
            glEnable(GL_FOG);glEnable(GL_CULL_FACE);glEnable(GL_LIGHTING);glEnable(GL_ALPHA_TEST);
            glEnable(GL_BLEND);GL14.glBlendEquation(GL14.GL_FUNC_REVERSE_SUBTRACT);
            GL14.glBlendFuncSeparate(GL_ONE,GL_ZERO,GL_ZERO,GL_ONE);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);glBindTexture(GL_TEXTURE_2D,mc.getFramebuffer().framebufferTexture);glEnable(GL_TEXTURE_2D);
            glViewport(3,7,WIDTH-11,HEIGHT-15);glColor4f(.2f,.3f,.4f,.5f);
            sentinel.init();State before=new State();
            TargetEspRenderer.Frame frame=halo(1,.5f);frame.marker=true;frame.markerY=.9;
            renderer.render(frame,new TargetEspRenderer.Style());
            before.requireSame(new State());
        }
        System.out.println("PASS: program, FBO, viewport, textures, depth, blend, color, clipping and matrices restored");
    }
    private static void fallbackAndEmptyFrames()throws Exception {
        boolean shaders=OpenGlHelper.shadersSupported;
        long programs=RenderDiagnostics.programCreates,fbos=RenderDiagnostics.framebufferCreates;
        try(TargetEspRenderer fallback=new TargetEspRenderer()) {
            OpenGlHelper.shadersSupported=false;
            TargetEspRenderer.Frame frame=halo(0,.5f);frame.marker=true;frame.markerY=1.3;
            begin(true);fallback.render(frame,new TargetEspRenderer.Style());BufferedImage image=capture();save(image,"geometric-fallback.png");
            require(changed(image)>100,"No-shader fallback is invisible");
        } finally {OpenGlHelper.shadersSupported=shaders;}
        require(programs==RenderDiagnostics.programCreates&&fbos==RenderDiagnostics.framebufferCreates,"Fallback allocates shader or framebuffer resources");
        TargetEspRenderer.Frame invalid=halo(0,.5f);invalid.radiusX=Double.NaN;
        begin(false);renderer.render(invalid,new TargetEspRenderer.Style());
        invalid=marker();invalid.markerZ=Double.NaN;renderer.render(invalid,new TargetEspRenderer.Style());
        invalid=marker();invalid.cameraX=Double.NaN;renderer.render(invalid,new TargetEspRenderer.Style());
        invalid=halo(0,.5f);invalid.leanX=Float.NaN;renderer.render(invalid,new TargetEspRenderer.Style());
        renderer.render(new TargetEspRenderer.Frame(),new TargetEspRenderer.Style());
        require(changed(capture())==0,"Invalid or cleared observations leave stale geometry");
        System.out.println("PASS: geometric shader fallback and no drawing for empty/invalid frames");
    }
    private static void boundedResources() {
        TargetEspRenderer.Frame frame=halo(0,.5f);frame.marker=true;frame.markerY=1;frame.markerHit=true;
        TargetEspRenderer.Style style=new TargetEspRenderer.Style();
        long programs=RenderDiagnostics.programCreates,fbos=RenderDiagnostics.framebufferCreates;
        long draws=RenderDiagnostics.shaderDraws;
        for(int i=0;i<120;i++){begin(true);frame.time=i*.03f;renderer.render(frame,style);}
        require(RenderDiagnostics.programCreates==programs,"Warm rendering recompiles TargetESP");
        require(RenderDiagnostics.framebufferCreates==fbos,"TargetESP allocates framebuffers");
        require(RenderDiagnostics.shaderDraws-draws==120*4,"TargetESP exceeded three halo ribbons plus one marker draw");
        renderer.close();
        require(RenderDiagnostics.programCreates==RenderDiagnostics.programDeletes,"Renderer close retained a GL program");
        begin(true);renderer.render(frame,style);
        require(RenderDiagnostics.programCreates==programs+1,"Renderer cannot reopen after disable");
        REPORT.addProperty("warm_frames",120);REPORT.addProperty("draws_per_combined_frame",4);
        System.out.println("PASS: 120 warm frames use four draws, no FBOs/recompile and a balanced close/reopen lifecycle");
    }

    private static void begin(boolean perspective) {
        mc.getFramebuffer().bindFramebuffer(true);glViewport(0,0,WIDTH,HEIGHT);
        GL20.glUseProgram(0);glDisable(GL_SCISSOR_TEST);glDisable(GL_STENCIL_TEST);glDisable(GL_FOG);
        glDisable(GL_BLEND);glDisable(GL_ALPHA_TEST);glDisable(GL_LIGHTING);glDisable(GL_CULL_FACE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);glDisable(GL_TEXTURE_2D);glDepthMask(true);glDepthFunc(GL_LESS);glEnable(GL_DEPTH_TEST);
        glColorMask(true,true,true,true);glClearColor(6/255f,9/255f,18/255f,1);glClearDepth(1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(-2,2,-.5,2.5,-10,10);
        glMatrixMode(GL_MODELVIEW);glLoadIdentity();
        if(perspective){glRotatef(18,1,0,0);glRotatef(-25,0,1,0);}
    }
    private static void occluder() {
        glColor4f(.08f,.09f,.11f,1);glBegin(GL_QUADS);
        glVertex3d(-.5,.3,.6);glVertex3d(.9,.3,.6);glVertex3d(.9,1.5,.6);glVertex3d(-.5,1.5,.6);glEnd();
    }
    private static BufferedImage capture() {
        ByteBuffer pixels=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);
        glReadPixels(0,0,WIDTH,HEIGHT,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
            int offset=(y*WIDTH+x)*4;
            image.setRGB(x,HEIGHT-1-y,0xff000000|(pixels.get(offset)&255)<<16|(pixels.get(offset+1)&255)<<8|(pixels.get(offset+2)&255));
        }
        return image;
    }
    private static int delta(int rgb) {return Math.max(Math.abs((rgb>>16&255)-6),Math.max(Math.abs((rgb>>8&255)-9),Math.abs((rgb&255)-18)));}
    private static int changed(BufferedImage image) {int n=0;for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)if(delta(image.getRGB(x,y))>3)n++;return n;}
    private static int colored(BufferedImage image) {
        int n=0;for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++) {
            int c=image.getRGB(x,y),r=c>>16&255,g=c>>8&255,b=c&255;
            if(Math.max(r,Math.max(g,b))>45&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>25)n++;
        }return n;
    }
    private static int differences(BufferedImage a,BufferedImage b) {int n=0;for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)if((a.getRGB(x,y)&0xffffff)!=(b.getRGB(x,y)&0xffffff))n++;return n;}
    private static double energy(BufferedImage image) {double sum=0;for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++)sum+=Math.max(0,brightness(image.getRGB(x,y))-brightness(BACKGROUND));return sum;}
    private static int brightness(int color) {return (color>>16&255)+(color>>8&255)+(color&255);}
    private static double centroidY(BufferedImage image) {double sum=0,weight=0;for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){double w=Math.max(0,brightness(image.getRGB(x,y))-brightness(BACKGROUND));sum+=y*w;weight+=w;}return sum/Math.max(1,weight);}
    private static int[] brightest(BufferedImage image) {int best=-1,bx=0,by=0;for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){int b=brightness(image.getRGB(x,y));if(b>best){best=b;bx=x;by=y;}}return new int[]{bx,by};}
    private static void save(BufferedImage image,String name)throws Exception {ImageIO.write(image,"png",new File(OUT,name));}
    private static void report()throws Exception {Files.write(new File(OUT,"target-esp.json").toPath(),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT).getBytes(StandardCharsets.UTF_8));}
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}

    private static final class State {
        private final int[] values={glGetInteger(GL20.GL_CURRENT_PROGRAM),glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),
                glGetInteger(GL13.GL_ACTIVE_TEXTURE),glGetInteger(GL_TEXTURE_BINDING_2D),glGetInteger(GL_MATRIX_MODE),
                glGetInteger(GL_DEPTH_FUNC),glGetInteger(GL14.GL_BLEND_EQUATION),glGetInteger(GL14.GL_BLEND_SRC_RGB),
                glGetInteger(GL14.GL_BLEND_DST_RGB),glGetInteger(GL14.GL_BLEND_SRC_ALPHA),glGetInteger(GL14.GL_BLEND_DST_ALPHA)};
        private final boolean[] flags={glIsEnabled(GL_DEPTH_TEST),glGetBoolean(GL_DEPTH_WRITEMASK),glIsEnabled(GL_BLEND),
                glIsEnabled(GL_ALPHA_TEST),glIsEnabled(GL_CULL_FACE),glIsEnabled(GL_LIGHTING),glIsEnabled(GL_FOG),glIsEnabled(GL_SCISSOR_TEST),glIsEnabled(GL_TEXTURE_2D)};
        private final int[] viewport=integers(GL_VIEWPORT),clip=integers(GL_SCISSOR_BOX);
        private final float[] model=floats(GL_MODELVIEW_MATRIX),projection=floats(GL_PROJECTION_MATRIX),color=floats(GL_CURRENT_COLOR);
        private void requireSame(State other) {
            require(Arrays.equals(values,other.values)&&Arrays.equals(flags,other.flags)&&Arrays.equals(viewport,other.viewport)
                    &&Arrays.equals(clip,other.clip)&&Arrays.equals(model,other.model)&&Arrays.equals(projection,other.projection)
                    &&Arrays.equals(color,other.color),"TargetESP leaked inherited OpenGL state");
        }
        private static int[] integers(int parameter) {IntBuffer values=BufferUtils.createIntBuffer(16);glGetInteger(parameter,values);int[] result=new int[4];for(int i=0;i<4;i++)result[i]=values.get(i);return result;}
        private static float[] floats(int parameter) {FloatBuffer values=BufferUtils.createFloatBuffer(16);glGetFloat(parameter,values);float[] result=new float[parameter==GL_CURRENT_COLOR?4:16];for(int i=0;i<result.length;i++)result[i]=values.get(i);return result;}
    }
}
