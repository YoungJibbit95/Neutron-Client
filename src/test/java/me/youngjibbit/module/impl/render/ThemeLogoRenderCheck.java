package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.RenderDiagnostics;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.ThemeLogoRenderer;
import me.youngjibbit.utils.render.UiEffects;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL20;

import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import static org.lwjgl.opengl.GL11.*;

/** Real original PNG and shared GLSL palette, in the existing native Pbuffer suite. */
final class ThemeLogoRenderCheck {
    private static final ResourceLocation ORIGINAL=new ResourceLocation("pulsar/Logo1.png");
    private static final Theme THEME=Theme.SynthWave;
    static void run() throws Exception {
        ModuleManager previous=Pulsar.INSTANCE.getMm();
        boolean diagnostics=RenderDiagnostics.enabled;
        ThemeLogoRenderer renderer=new ThemeLogoRenderer();
        Method time=UiEffects.class.getDeclaredMethod("timeSource",LongSupplier.class);time.setAccessible(true);
        AtomicLong nanos=new AtomicLong();
        try {
            SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());
            ClientSettings settings=ClientSettings.current();
            settings.gradients.setValue(true);settings.glow.setValue(true);settings.synchronize.setValue(true);
            settings.animations.setValue(true);settings.ambientMotion.setValue(true);settings.reducedMotion.setValue(false);
            settings.direction.setValue("Horizontal");settings.gradientMotion.setValue("Static");
            time.invoke(null,(LongSupplier)nanos::get);UiEffects.invalidate();UiEffects.beginFrame();
            BufferedImage halo=draw(renderer);
            SessionInfoRenderCheck.require(SessionInfoRenderCheck.get(renderer,"shader")!=null,
                    "Original logo fragment shader did not compile");
            settings.glow.setValue(false);BufferedImage stroke=draw(renderer);
            int outside=0,scale=new ScaledResolution(SessionInfoRenderCheck.mc).getScaleFactor();
            for(int y=0;y<halo.getHeight();y++)for(int x=0;x<halo.getWidth();x++)
                if((x<80*scale||x>=176*scale||y<50*scale||y>=146*scale)&&halo.getRGB(x,y)!=stroke.getRGB(x,y))outside++;
            SessionInfoRenderCheck.require(outside>30,"Original logo halo remains cropped to the old stroke quad");
            settings.glow.setValue(true);
            Theme noThemeGlow=Theme.custom("custom:00000000-0000-0000-0000-000000000001","Logo strength zero",
                    java.util.Arrays.asList(THEME.getPrimaryGradientColor(),THEME.getSecondaryGradientColor()),
                    false,false,.18f,java.awt.Color.BLACK,java.awt.Color.WHITE,java.awt.Color.WHITE,0,9,.8f,1);
            SessionInfoRenderCheck.require(differences(stroke,draw(renderer,noThemeGlow))==0,
                    "Logo ignores the custom theme's zero Glow strength");
            try(UiEffects.SceneContext local=UiEffects.scene(0,0,false,THEME,
                    UiEffects.surface(null,THEME,true,false,false,0,0))){
                SessionInfoRenderCheck.require(differences(stroke,draw(renderer))==0,
                        "Logo ignores the Watermark's resolved local Glow setting");
            }
            List<BufferedImage> images=new ArrayList<>();List<String> labels=new ArrayList<>();
            images.add(halo);labels.add("Original alpha and halo · shared theme tint");
            images.add(stroke);labels.add("Glow off · original stroke retained");
            settings.direction.setValue("Vertical");BufferedImage vertical=draw(renderer);
            SessionInfoRenderCheck.require(differences(halo,vertical)>100,"Logo ignores shared gradient direction");
            images.add(vertical);labels.add("Vertical palette");
            settings.direction.setValue("Horizontal");settings.gradientMotion.setValue("Animated");settings.gradientSpeed.setValue(.7);
            for(String mode:new String[]{"Flow","Ping-Pong","Pulse","Perimeter Orbit"}){
                settings.gradientMethod.setValue(mode);
                BufferedImage start=draw(renderer);advance(nanos,4);BufferedImage end=draw(renderer);
                SessionInfoRenderCheck.require(differences(start,end)>40,"Logo animation is static: "+mode);
                images.add(end);labels.add(mode+" · source texture, animated ink and glow");
            }
            settings.reducedMotion.setValue(true);BufferedImage still=draw(renderer);advance(nanos,8);
            SessionInfoRenderCheck.require(differences(still,draw(renderer))==0,"Logo ignores reduced motion");
            SessionInfoRenderCheck.sheet("island-logo-gradients.png",labels,images,true);

            Object oldProgram=SessionInfoRenderCheck.get(renderer,"shader");
            ((SimpleReloadableResourceManager)SessionInfoRenderCheck.mc.getResourceManager()).reloadResources(
                    Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            draw(renderer);
            SessionInfoRenderCheck.require(SessionInfoRenderCheck.get(renderer,"shader")!=oldProgram,
                    "Logo shader did not refresh with resource reload");
            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
            try(RenderState state=new RenderState()){
                state.screenSpace();glEnable(GL_CULL_FACE);glEnable(GL_ALPHA_TEST);glShadeModel(GL_FLAT);glMatrixMode(GL_PROJECTION);
                int program=glGetInteger(GL20.GL_CURRENT_PROGRAM);
                for(int frame=0;frame<120;frame++)renderer.draw(ORIGINAL,80,50,96,THEME,1);
                SessionInfoRenderCheck.require(glIsEnabled(GL_CULL_FACE)&&glIsEnabled(GL_ALPHA_TEST)
                                &&glGetInteger(GL_SHADE_MODEL)==GL_FLAT&&glGetInteger(GL_MATRIX_MODE)==GL_PROJECTION
                                &&glGetInteger(GL20.GL_CURRENT_PROGRAM)==program,"Logo leaked caller GL state");
            }
            SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0
                    &&RenderDiagnostics.shaderCompiles==0,"Logo allocated GPU resources after warmup");
            boolean shaders=OpenGlHelper.shadersSupported;
            try { OpenGlHelper.shadersSupported=false;draw(renderer); }
            finally { OpenGlHelper.shadersSupported=shaders; }
            SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Logo shader/fallback OpenGL error");
        } finally {
            renderer.close();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",previous);
            time.invoke(null,(LongSupplier)System::nanoTime);UiEffects.invalidate();RenderDiagnostics.enabled=diagnostics;
        }
        System.out.println("PASS: original Island logo halo, shared palette modes, reduced motion, reload, fallback and zero steady GPU allocations");
    }
    private static void advance(AtomicLong nanos,int frames){
        for(int i=0;i<frames;i++){nanos.addAndGet(100_000_000L);UiEffects.beginFrame();}
    }
    private static BufferedImage draw(ThemeLogoRenderer renderer){
        return draw(renderer,THEME);
    }
    private static BufferedImage draw(ThemeLogoRenderer renderer,Theme theme){
        try(RenderState state=new RenderState()){
            state.screenSpace();glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
            renderer.draw(ORIGINAL,80,50,96,theme,1);
        }
        return SessionInfoRenderCheck.capture();
    }
    private static int differences(BufferedImage a,BufferedImage b){
        int count=0;
        for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))count++;
        return count;
    }
}
