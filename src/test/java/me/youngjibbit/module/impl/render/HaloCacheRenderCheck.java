package me.youngjibbit.module.impl.render;

import java.io.*;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.*;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL11.*;

/** Full native RGBA comparisons through the same compositor with cache disabled/cold/hot. */
public final class HaloCacheRenderCheck {
    private static Minecraft mc;
    private static ArrayListSurface surface;
    private static ClientSettings settings;
    private static Object cache;
    private static float shift;

    public static void run() throws Exception {
        mc=SessionInfoRenderCheck.mc;
        ModuleManager oldModules=Pulsar.INSTANCE.getMm();
        IReloadableResourceManager oldResources=(IReloadableResourceManager)mc.getResourceManager();
        int width=mc.displayWidth,height=mc.displayHeight,scale=mc.gameSettings.guiScale;
        Theme oldTheme=Theme.getDefaultTheme();
        String property=System.getProperty("pulsar.haloCache");
        boolean diagnostics=RenderDiagnostics.enabled;
        ArrayListSurface.releaseResources();GaussianBlur.releaseResources();
        SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());
        settings=ClientSettings.current();settings.surface.setValue("Gradient");settings.blur.setValue(false);
        settings.glow.setValue(true);settings.glowRadius.setValue(4.0);settings.reducedMotion.setValue(true);
        settings.gradientMotion.setValue("Static");UiEffects.invalidate();
        surface=new ArrayListSurface();cache=SessionInfoRenderCheck.get(surface,"haloCache");
        long allocations=counter("allocations"),deletions=counter("deletions");
        try(RenderState state=new RenderState()) {
            RenderDiagnostics.enabled=true;
            me.youngjibbit.utils.render.HaloCacheNativePolicyCheck.run();
            resize(641,359,2);
            byte[] reference=frame(false,13,0,false);
            RenderDiagnostics.reset();long misses=counter("misses");
            same(reference,frame(true,13,0,false),"cold thirteen panels");
            require(counter("misses")-misses==13 && RenderDiagnostics.maskPasses==13 && RenderDiagnostics.blurPasses==26,"Cold masks/pass count");
            RenderDiagnostics.reset();long hits=counter("hits"),creates=counter("allocations");
            same(reference,frame(true,13,0,false),"warm thirteen panels");
            require(counter("hits")-hits==13 && RenderDiagnostics.maskPasses==0 && RenderDiagnostics.blurPasses==0
                    && counter("allocations")==creates,"Warm halos performed masks, blur or allocation");
            for(int variant=1;variant<=3;variant++) {
                Theme.setDefaultTheme(variant==2?Theme.Cherry:Theme.SynthWave);
                settings.surfaceOpacity.setValue(.35+variant*.15);settings.glowIntensity.setValue(.5+variant*.25);UiEffects.invalidate();
                same(frame(false,13,variant,true),frame(true,13,variant,true),"live scene/theme/opacity + nested panel "+variant);
            }
            for(String style:new String[]{"Blur","Liquid Glass"}) {
                settings.surface.setValue(style);settings.blur.setValue(true);UiEffects.invalidate();
                same(frame(false,3,2,true),frame(true,3,2,true),style+" scene remains live");
            }
            settings.surface.setValue("Gradient");settings.blur.setValue(false);UiEffects.invalidate();
            for(int guiScale:new int[]{1,3}) {
                resize(1001+guiScale,741+guiScale,guiScale);
                same(frame(false,13,guiScale,true),frame(true,13,guiScale,true),"odd resize/scale "+guiScale);
            }
            checkRasterFallback();
            resize(641,359,2);
            frame(true,40,0,false);
            creates=counter("allocations");long bypasses=counter("bypasses");
            same(frame(false,40,0,false),frame(true,40,0,false),"oversubscribed retained subset");
            require(cacheInt("size")<=32 && cacheLong("bytes")<=128L*1024*1024,"Halo budget exceeded");
            require(counter("allocations")==creates && counter("bypasses")>bypasses,"Oversubscription allocated or failed to bypass");
            ageEntries();long evictions=counter("evictions");shift=71.125f;
            same(frame(false,13,0,false),frame(true,13,0,false),"idle FBO reuse clears previous geometry");
            require(counter("allocations")==creates && counter("evictions")>evictions,"Idle replacement failed to reuse FBO storage");
            shift=0;
            for(double radius:new double[]{1,24}) {
                settings.glowRadius.setValue(radius);UiEffects.invalidate();
                same(frame(false,3,1,true),frame(true,3,1,true),"effective radius "+radius);
            }
            settings.glow.setValue(false);UiEffects.invalidate();
            same(frame(false,3,1,true),frame(true,3,1,true),"glow disabled");
            settings.glow.setValue(true);settings.glowRadius.setValue(4.0);UiEffects.invalidate();
            checkSourcesAndReload(oldResources);
            ArrayListSurface.releaseResources();ArrayListSurface.releaseResources();GaussianBlur.releaseResources();
            require(cacheInt("size")==0 && cacheLong("bytes")==0,"Cache survived release");
            require(counter("allocations")-allocations==counter("deletions")-deletions,"Cache FBO allocation/deletion imbalance");
            same(frame(false,3,1,true),frame(true,3,1,true),"reopen after idempotent release");
            require(glGetError()==GL_NO_ERROR,"Halo native GL error");
            System.out.println("PASS: exact cold/hot/bypass halo RGBA, thirteen static panels skip all 26 glow passes; live scene/theme, nested callbacks, budget, raster fallback, shader override/reload and reopen");
        } finally {
            surface.close();ArrayListSurface.releaseResources();GaussianBlur.releaseResources();
            SessionInfoRenderCheck.set(mc,"mcResourceManager",oldResources);
            mc.displayWidth=width;mc.displayHeight=height;mc.gameSettings.guiScale=scale;mc.getFramebuffer().createBindFramebuffer(width,height);
            SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",oldModules);Theme.setDefaultTheme(oldTheme);UiEffects.invalidate();
            if(property==null)System.clearProperty("pulsar.haloCache");else System.setProperty("pulsar.haloCache",property);
            RenderDiagnostics.enabled=diagnostics;
        }
    }

    private static byte[] frame(boolean enabled,int count,int variant,boolean nested) {
        System.setProperty("pulsar.haloCache",Boolean.toString(enabled));
        SessionInfoRenderCheck.backdrop();
        glClearColor(.08f+variant*.05f,.13f+variant*.025f,.21f-variant*.02f,.6f);glClear(GL_COLOR_BUFFER_BIT);
        Theme theme=Theme.getDefaultTheme();
        for(int panel=0;panel<count;panel++) {
            float x=4.25f+(panel%8)*25+shift,y=5.75f+(panel/8)*21;
            surface.panel(x,y,22,17,3,theme,true,true,true,5,.65f,.5f,()->{});
        }
        // Exercise shared scratch reuse before a nested callback and concave/fractional contours.
        if(nested) {
            ArrayListSurface.Shape shape=new ArrayListSurface.Shape(10.25f,new float[]{17.5f,29.5f},new float[]{29.5f,43.75f},new float[]{58.25f,44.5f},4);
            surface.render(shape,theme,true,true,true,.7f,()->surface.panel(73.5f,31.25f,34,21,5,theme,true,true,true,4,.5f,.2f,()->{}));
        }
        ByteBuffer pixels=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);
        glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);
        byte[] rgba=new byte[pixels.remaining()];pixels.get(rgba);return rgba;
    }

    private static void checkRasterFallback() throws Exception {
        for(int cap:new int[]{GL_COLOR_LOGIC_OP,GL_POLYGON_SMOOTH,GL_POLYGON_STIPPLE,GL_CLIP_PLANE0}) {
            try(RenderState saved=new RenderState()) {
                glEnable(cap);long bypasses=counter("bypasses");
                same(frame(false,2,0,false),frame(true,2,0,false),"noncanonical raster "+cap);
                require(counter("bypasses")>=bypasses+4,"Raster state cached unexpectedly");
            }
        }
        try(RenderState saved=new RenderState()) {
            glPolygonMode(GL_FRONT_AND_BACK,GL_LINE);
            same(frame(false,2,0,false),frame(true,2,0,false),"wireframe raster fallback");
        }
        try(RenderState saved=new RenderState()) {
            glDisable(GL_DITHER);
            same(frame(false,2,0,false),frame(true,2,0,false),"dither disabled key");
            glEnable(GL_DITHER);
            same(frame(false,2,0,false),frame(true,2,0,false),"dither enabled key");
        }
    }

    private static void checkSourcesAndReload(IReloadableResourceManager delegate) throws Exception {
        ReloadingResources resources=new ReloadingResources(delegate);
        SessionInfoRenderCheck.set(mc,"mcResourceManager",resources);
        // First shader use occurs inside an already-created parent scene: registration must
        // happen before capture, so the child's shader initialization cannot delete the parent.
        System.setProperty("pulsar.haloCache","true");SessionInfoRenderCheck.backdrop();
        surface.withScene(Theme.SynthWave,()->{
            int clean=UiEffects.scene().clean;
            surface.panel(10,15,50,30,4,Theme.SynthWave,true,true,true,5,.5f,.5f,()->{});
            require(glIsTexture(clean),"First child shader deleted live parent scene");
        });
        for(String source:new String[]{"arraylistSurface.frag","vertex.vsh","glow.frag","uiGradient.glsl","uiGlass.glsl"}) {
            resources.source=source;resources.reloadResources(Collections.emptyList());
            require(cacheInt("size")==0,"Reload retained halos");
            long hits=counter("hits"),bypasses=counter("bypasses");
            same(frame(false,3,1,true),frame(true,3,1,true),"overridden "+source);
            require(counter("hits")==hits && counter("bypasses")>bypasses && cacheInt("size")==0,"Overridden shader entered cache: "+source);
        }
        resources.source=null;resources.reloadResources(Collections.emptyList());
        frame(true,3,0,false);long hits=counter("hits");
        same(frame(false,3,0,false),frame(true,3,0,false),"bundled sources restored");
        require(counter("hits")>=hits+3,"Bundled reload did not reenable cache");
        SessionInfoRenderCheck.set(mc,"mcResourceManager",delegate);
    }

    private static final class ReloadingResources implements IReloadableResourceManager {
        final IResourceManager delegate;final List<IResourceManagerReloadListener> listeners=new ArrayList<>();String source;
        ReloadingResources(IResourceManager delegate){this.delegate=delegate;}
        @Override public Set<String> getResourceDomains(){return delegate.getResourceDomains();}
        @Override public IResource getResource(ResourceLocation location)throws IOException {
            IResource actual=delegate.getResource(location);
            if(source==null||!location.getResourcePath().equals("Flauxy/Shaders/"+source))return actual;
            byte[] bytes;try(InputStream in=actual.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] block=new byte[4096];int n;while((n=in.read(block))!=-1)out.write(block,0,n);bytes=out.toByteArray();
            }
            String text=new String(bytes,StandardCharsets.UTF_8);
            if(source.equals("arraylistSurface.frag"))text=text.replace("vec4(1.0,1.0,1.0,coverage)","vec4(1.0,1.0,1.0,coverage*.75)");
            text+="\n// Native regression resource override\n";
            return new SimpleResource("halo-native-override",location,new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)),null,null);
        }
        @Override public List<IResource> getAllResources(ResourceLocation location)throws IOException{return Collections.singletonList(getResource(location));}
        @Override public void registerReloadListener(IResourceManagerReloadListener listener){listeners.add(listener);listener.onResourceManagerReload(this);}
        @Override public void reloadResources(List<IResourcePack> ignored){for(IResourceManagerReloadListener listener:new ArrayList<>(listeners))listener.onResourceManagerReload(this);}
    }
    private static void resize(int width,int height,int scale){mc.displayWidth=width;mc.displayHeight=height;mc.gameSettings.guiScale=scale;mc.getFramebuffer().createBindFramebuffer(width,height);}
    private static void ageEntries()throws Exception{
        Field policy=cache.getClass().getDeclaredField("retention");policy.setAccessible(true);Object retention=policy.get(cache);
        Field slots=retention.getClass().getDeclaredField("slots");slots.setAccessible(true);
        for(Object slot:(List<?>)slots.get(retention)){Field last=slot.getClass().getDeclaredField("lastUse");last.setAccessible(true);last.setLong(slot,System.nanoTime()-2_000_000_000L);}
    }
    private static long counter(String field)throws Exception{Field f=cache.getClass().getDeclaredField(field);f.setAccessible(true);return f.getLong(cache);}
    private static long cacheLong(String name)throws Exception{Method m=cache.getClass().getDeclaredMethod(name);m.setAccessible(true);return ((Number)m.invoke(cache)).longValue();}
    private static int cacheInt(String name)throws Exception{return (int)cacheLong(name);}
    private static void same(byte[] expected,byte[] actual,String message){require(Arrays.equals(expected,actual),"Halo RGBA differs: "+message);}
    private static void require(boolean condition,String message){SessionInfoRenderCheck.require(condition,message);}
}
