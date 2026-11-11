package me.youngjibbit.utils.render;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.shader.KawaseBloom;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.LanguageManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import static org.lwjgl.opengl.GL11.*;

/** Actual ported cube/mask drawing and host blur/glow programs in a real Pbuffer/FBO context. */
public final class DemiseScaffoldOverlayNativeCheck {
    private static final int WIDTH=128,HEIGHT=96;
    private static final BlockPos TARGET=new BlockPos(0,0,0);
    private static final File OUT=new File("demise-scaffold-overlay-preview");
    private static Minecraft mc;
    public static void main(String[] args) throws Exception {
        OUT.mkdirs(); File data=Files.createTempDirectory(OUT.toPath(),"fixture-").toFile();
        try(PbufferTestContext context=new PbufferTestContext()) {
            mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(
                    new Session("DemiseOverlayFixture","00000000000000000000000000000000","0","legacy"),
                    new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(WIDTH,HEIGHT,false,false),
                    new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                    new GameConfiguration.GameInformation(false,"Demise Scaffold overlay fixture"),
                    new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(new IMetadataSerializer());
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            field(Minecraft.class,"mcResourceManager").set(mc,resources);
            field(Minecraft.class,"mcLanguageManager").set(mc,new LanguageManager(new IMetadataSerializer(),"en_US"));
            mc.thePlayer=bare(EntityPlayerSP.class); mc.theWorld=bare(WorldClient.class); mc.thePlayer.worldObj=mc.theWorld;
            field(Minecraft.class,"renderManager").set(mc,bare(RenderManager.class));
            Framebuffer target=new Framebuffer(WIDTH,HEIGHT,true); field(Minecraft.class,"framebufferMc").set(mc,target);
            int texture=glGenTextures(); GlStateManager.bindTexture(texture); glBindTexture(GL_TEXTURE_2D,texture);
            ByteBuffer white=BufferUtils.createByteBuffer(4); white.putInt(-1).flip();
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,1,1,0,GL_RGBA,GL_UNSIGNED_BYTE,white);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST); glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
            ModuleManager previousModules=Pulsar.INSTANCE.getMm();
            Theme previousTheme=Theme.getDefaultTheme();
            ClientSettings settings=new ClientSettings();
            field(Pulsar.class,"mm").set(Pulsar.INSTANCE,new ModuleManager(Collections.singletonList(settings)));
            Theme.setDefaultTheme(Theme.SynthWave);
            settings.surface.setValue("Standard"); settings.blurMethod.setValue("Gaussian"); settings.blurStrength.setValue(6.0);
            UiEffects.invalidate();
            RenderDiagnostics.reset(); RenderDiagnostics.enabled=true;
            try {
                probe(target,texture,"world-fill",false,() -> DemiseScaffoldOverlay.render(TARGET));
                Sample blurMask=probe(target,texture,"blur-mask",true,() -> DemiseScaffoldOverlay.shader(TARGET,false));
                Sample glowMask=probe(target,texture,"glow-mask",true,() -> DemiseScaffoldOverlay.shader(TARGET,true));
                assertMaskColor(blurMask,0x000000,"blur-mask");
                assertMaskColor(glowMask,Theme.getDefaultTheme().getPrimaryGradientRGB(),"glow-mask");

                settings.blur.setValue(true); settings.glow.setValue(false);
                require(UiEffects.blurEnabled()&&!UiEffects.glowEnabled(),"Blur-only settings not applied");
                long blurPasses=RenderDiagnostics.blurPasses,blurComposites=RenderDiagnostics.composites;
                Sample blurOnly=probe(target,texture,"blur-only",true,true,
                        () -> require(DemiseScaffoldEffects.render(TARGET),"Blur-only pass declined"));
                require(RenderDiagnostics.blurPasses-blurPasses==2&&RenderDiagnostics.composites-blurComposites==1,
                        "Blur-only did not execute the two Gaussian passes and its masked composite");
                require(field(DemiseScaffoldEffects.class,"mask").get(null)==null,"Blur-only allocated the glow mask");
                assertBlurredCheckerboard(blurOnly);

                settings.blur.setValue(false); settings.glow.setValue(true);
                require(!UiEffects.blurEnabled()&&UiEffects.glowEnabled(),"Glow-only settings not applied");
                long glowPasses=RenderDiagnostics.blurPasses,glowComposites=RenderDiagnostics.composites;
                Sample glowOnly=probe(target,texture,"glow-only",true,
                        () -> require(DemiseScaffoldEffects.render(TARGET),"Glow-only pass declined"));
                require(RenderDiagnostics.blurPasses-glowPasses==6&&RenderDiagnostics.composites-glowComposites==1,
                        "Glow-only did not execute its three-level Kawase filter and composite");
                assertExteriorGlow(glowOnly,glowMask);

                settings.blur.setValue(true); settings.glow.setValue(true);
                require(UiEffects.blurEnabled()&&UiEffects.glowEnabled(),"Both mask consumers must run");
                long passes=RenderDiagnostics.blurPasses,composites=RenderDiagnostics.composites;
                probe(target,texture,"composited-effects",true,() -> require(DemiseScaffoldEffects.render(TARGET),"Effect pass declined"));
                require(RenderDiagnostics.blurPasses>passes&&RenderDiagnostics.composites>composites,"Real filter/composite programs did not execute");
                Framebuffer mask=(Framebuffer)field(DemiseScaffoldEffects.class,"mask").get(null);
                require(mask!=null&&mask.framebufferObject>0,"Owned glow mask allocated");
                int maskId=mask.framebufferObject,maskTexture=mask.framebufferTexture;
                probe(target,texture,"composited-repeat",true,() -> require(DemiseScaffoldEffects.render(TARGET),"Warm effect pass declined"));
                require(field(DemiseScaffoldEffects.class,"mask").get(null)==mask,"Warm render recreated its mask");
                require(mask.framebufferObject==maskId&&mask.framebufferTexture==maskTexture,"Warm render replaced mask GPU storage");
                State release=new State(); DemiseScaffoldEffects.releaseResources(); release.assertRestored("mask release");
                int api=RenderState.framebufferApi(GLContext.getCapabilities().OpenGL30,GLContext.getCapabilities().GL_ARB_framebuffer_object,
                        GLContext.getCapabilities().GL_EXT_framebuffer_object);
                require(!glIsTexture(maskTexture)&&!RenderState.framebufferExists(maskId,api),"Released mask GPU resources remain live");
                require(glIsTexture(target.framebufferTexture),"Release deleted borrowed main target");
                State empty=new State(); DemiseScaffoldOverlay.render(null); DemiseScaffoldOverlay.shader(null,false); empty.assertRestored("null target");
                require(glGetError()==GL_NO_ERROR,"Demise overlay GL error"); context.check();
            } finally {
                DemiseScaffoldEffects.releaseResources(); Blur.releaseResources(); KawaseBloom.releaseResources(); GaussianBlur.releaseResources();
                RenderDiagnostics.enabled=false; target.deleteFramebuffer(); GlStateManager.deleteTexture(texture);
                field(Pulsar.class,"mm").set(Pulsar.INSTANCE,previousModules);
                Theme.setDefaultTheme(previousTheme); UiEffects.invalidate();
            }
        }
        System.out.println("PASS: actual Demise cube, black/colored masks, blur/glow composition, GPU pixels, GL/matrix/FBO restoration, mask reuse and disposal.");
        System.out.println("NOT COVERED: native Display, a running world, player-model GL rendering, external shaderpacks or hardware FPS.");
    }
    private static Sample probe(Framebuffer target,int texture,String name,boolean screen,Runnable draw) throws Exception {
        return probe(target,texture,name,screen,false,draw);
    }
    private static Sample probe(Framebuffer target,int texture,String name,boolean screen,boolean patterned,Runnable draw) throws Exception {
        target.bindFramebuffer(true); GlStateManager.colorMask(true,true,true,true); glColorMask(true,true,true,true);
        GlStateManager.depthMask(true); glDepthMask(true); glDisable(GL_SCISSOR_TEST);
        glClearColor(.08f,.12f,.18f,1); glClearDepth(1); glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
        if(patterned) checkerboard();
        glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(-1,2,-1,2,-10,10);
        glMatrixMode(GL_MODELVIEW); glLoadIdentity(); glRotatef(8,0,1,0);
        ActiveRenderInfo.updateRenderInfo(mc.thePlayer,false);
        if(screen) {
            net.minecraft.client.gui.ScaledResolution sr=new net.minecraft.client.gui.ScaledResolution(mc);
            glMatrixMode(GL_PROJECTION); glLoadIdentity(); glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);
            glMatrixMode(GL_MODELVIEW); glLoadIdentity();
        }
        GlStateManager.setActiveTexture(GL13.GL_TEXTURE0); GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GlStateManager.bindTexture(texture); glBindTexture(GL_TEXTURE_2D,texture); GlStateManager.enableTexture2D(); glEnable(GL_TEXTURE_2D);
        GlStateManager.disableLighting(); glDisable(GL_LIGHTING); GlStateManager.disableCull(); glDisable(GL_CULL_FACE);
        GlStateManager.enableAlpha(); glEnable(GL_ALPHA_TEST); GlStateManager.alphaFunc(GL_GREATER,.13f); glAlphaFunc(GL_GREATER,.13f);
        GlStateManager.enableDepth(); glEnable(GL_DEPTH_TEST); GlStateManager.depthFunc(GL_ALWAYS); glDepthFunc(GL_ALWAYS);
        GlStateManager.depthMask(false); glDepthMask(false); GlStateManager.disableBlend(); glDisable(GL_BLEND);
        GlStateManager.tryBlendFuncSeparate(GL_ONE,GL_ZERO,GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);
        GL14.glBlendFuncSeparate(GL_ONE,GL_ZERO,GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA); GL14.glBlendEquation(GL14.GL_FUNC_ADD);
        GlStateManager.color(.7f,.2f,.9f,.6f); glColor4f(.7f,.2f,.9f,.6f);
        glEnable(GL_SCISSOR_TEST); glScissor(4,3,WIDTH-8,HEIGHT-6); glMatrixMode(GL_PROJECTION);
        State state=new State(); byte[] before=pixels(); draw.run(); state.assertRestored(name); byte[] after=pixels();
        int changed=0;
        for(int p=0;p<WIDTH*HEIGHT;p++) {
            int at=p*4;
            if(Math.abs((before[at]&255)-(after[at]&255))>2 || Math.abs((before[at+1]&255)-(after[at+1]&255))>2
                    || Math.abs((before[at+2]&255)-(after[at+2]&255))>2) changed++;
        }
        require(changed>50&&changed<WIDTH*HEIGHT-50,name+" did not draw bounded visible geometry: "+changed);
        if(patterned) save(before,name+"-before");
        save(after,name); require(glGetError()==GL_NO_ERROR,name+" GL error");
        return new Sample(before,after);
    }
    private static final class Sample {
        final byte[] before,after;
        Sample(byte[] before,byte[] after){this.before=before;this.after=after;}
    }
    private static void checkerboard() {
        // Scissored clears make a sharp, known input independent of the overlay's geometry/shaders.
        glEnable(GL_SCISSOR_TEST);
        for(int y=0;y<HEIGHT;y+=4)for(int x=0;x<WIDTH;x+=4) {
            float shade=((x/4+y/4)&1)==0?.1f:.9f;
            glScissor(x,y,4,4);glClearColor(shade,shade,shade,1);glClear(GL_COLOR_BUFFER_BIT);
        }
        glDisable(GL_SCISSOR_TEST);glClearColor(.08f,.12f,.18f,1);
    }
    private static void assertMaskColor(Sample sample,int rgb,String label) {
        // These pixels are strictly inside both projected front faces for the fixed ortho/8-degree camera.
        for(int y=44;y<52;y++)for(int x=60;x<68;x++) {
            int at=(y*WIDTH+x)*4;
            for(int channel=0;channel<3;channel++) {
                int expected=(rgb>>(16-channel*8))&255;
                require(Math.abs((sample.after[at+channel]&255)-expected)<=2,
                        label+" wrong interior channel "+channel+" at "+x+","+y);
            }
            require((sample.after[at+3]&255)==255,label+" interior is not opaque");
        }
        assertPixelUnchanged(sample,10,10,label+" exterior");
    }
    private static void assertBlurredCheckerboard(Sample sample) {
        double[] before=regionStatistics(sample.before),after=regionStatistics(sample.after);
        require(before[1]>90,"Blur input lost its high-contrast checkerboard");
        require(after[1]<before[1]*.65,"Blur-only did not reduce interior contrast: "+before[1]+" -> "+after[1]);
        require(Math.abs(after[0]-before[0])<20,"Blur-only tinted or replaced the scene's mean brightness");
        int softenedDark=0,softenedLight=0;
        for(int y=40;y<57;y++)for(int x=56;x<77;x++) {
            int at=(y*WIDTH+x)*4,old=sample.before[at]&255,value=sample.after[at]&255;
            require(Math.abs(value-(sample.after[at+1]&255))<=2&&Math.abs(value-(sample.after[at+2]&255))<=2,
                    "Blur-only recolored the neutral checkerboard");
            if(old<40&&value>50&&value<205)softenedDark++;
            if(old>215&&value>50&&value<205)softenedLight++;
        }
        require(softenedDark>40&&softenedLight>40,"Blur-only did not mix both sides of the checker edges");
        assertPixelUnchanged(sample,10,10,"Blur-only outside its mask");
        System.out.println("PASS: isolated Gaussian scene blur; interior contrast "+before[1]+" -> "+after[1]);
    }
    private static double[] regionStatistics(byte[] pixels) {
        double sum=0,squares=0;int count=0;
        for(int y=40;y<57;y++)for(int x=56;x<77;x++) {
            int at=(y*WIDTH+x)*4;
            double value=((pixels[at]&255)+(pixels[at+1]&255)+(pixels[at+2]&255))/3.0;
            sum+=value;squares+=value*value;count++;
        }
        double mean=sum/count;
        return new double[]{mean,Math.sqrt(Math.max(0,squares/count-mean*mean))};
    }
    private static void assertExteriorGlow(Sample sample,Sample solidMask) {
        int exterior=0,blueHalo=0;
        for(int at=0;at<sample.after.length;at+=4) {
            if(changed(sample.before,sample.after,at)&&!changed(solidMask.before,solidMask.after,at)) {
                exterior++;
                if((sample.after[at+2]&255)>(sample.before[at+2]&255)+3
                        &&(sample.after[at+2]&255)>(sample.after[at]&255)+10)blueHalo++;
            }
        }
        require(exterior>50&&blueHalo>20,"Glow-only did not produce a colored halo beyond its source mask: "+exterior+"/"+blueHalo);
        assertPixelUnchanged(sample,64,48,"Glow-only excluded source interior");
        System.out.println("PASS: isolated Kawase glow; "+exterior+" exterior pixels, "+blueHalo+" theme-colored pixels");
    }
    private static boolean changed(byte[] before,byte[] after,int at) {
        for(int channel=0;channel<3;channel++)if(Math.abs((before[at+channel]&255)-(after[at+channel]&255))>2)return true;
        return false;
    }
    private static void assertPixelUnchanged(Sample sample,int x,int y,String label) {
        require(!changed(sample.before,sample.after,(y*WIDTH+x)*4),label+" changed at "+x+","+y);
    }
    private static final int[] SCALARS={EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT,GL_MATRIX_MODE,
            GL_MODELVIEW_STACK_DEPTH,GL_PROJECTION_STACK_DEPTH,GL13.GL_ACTIVE_TEXTURE,GL_TEXTURE_BINDING_2D,
            GL20.GL_CURRENT_PROGRAM,GL_DEPTH_FUNC,GL_DEPTH_WRITEMASK,GL_ALPHA_TEST_FUNC,GL14.GL_BLEND_SRC_RGB,GL14.GL_BLEND_DST_RGB,
            GL14.GL_BLEND_SRC_ALPHA,GL14.GL_BLEND_DST_ALPHA};
    private static final int[] ENABLES={GL_DEPTH_TEST,GL_BLEND,GL_TEXTURE_2D,GL_ALPHA_TEST,GL_SCISSOR_TEST,GL_CULL_FACE};
    private static final class State {
        final float[] projection=floats(GL_PROJECTION_MATRIX,16),model=floats(GL_MODELVIEW_MATRIX,16),color=floats(GL_CURRENT_COLOR,4);
        final float alphaReference=glGetFloat(GL_ALPHA_TEST_REF);
        final int[] viewport=ints(GL_VIEWPORT),scissor=ints(GL_SCISSOR_BOX),scalar=new int[SCALARS.length];
        final boolean[] enabled=new boolean[ENABLES.length];
        State(){for(int i=0;i<SCALARS.length;i++)scalar[i]=glGetInteger(SCALARS[i]);for(int i=0;i<ENABLES.length;i++)enabled[i]=glIsEnabled(ENABLES[i]);}
        void assertRestored(String label) {
            equal(projection,floats(GL_PROJECTION_MATRIX,16),label+" projection");
            equal(model,floats(GL_MODELVIEW_MATRIX,16),label+" model");equal(color,floats(GL_CURRENT_COLOR,4),label+" color");
            require(Math.abs(alphaReference-glGetFloat(GL_ALPHA_TEST_REF))<.00001f,label+" alpha reference");
            require(Arrays.equals(viewport,ints(GL_VIEWPORT))&&Arrays.equals(scissor,ints(GL_SCISSOR_BOX)),label+" viewport/scissor");
            for(int i=0;i<SCALARS.length;i++)require(scalar[i]==glGetInteger(SCALARS[i]),label+" GL state "+SCALARS[i]);
            for(int i=0;i<ENABLES.length;i++)require(enabled[i]==glIsEnabled(ENABLES[i]),label+" GL enable "+ENABLES[i]);
        }
    }
    private static byte[] pixels() {
        ByteBuffer b=BufferUtils.createByteBuffer(WIDTH*HEIGHT*4);glReadPixels(0,0,WIDTH,HEIGHT,GL_RGBA,GL_UNSIGNED_BYTE,b);
        byte[] out=new byte[b.capacity()];b.get(out);return out;
    }
    private static void save(byte[] bytes,String name)throws Exception {
        BufferedImage image=new BufferedImage(WIDTH,HEIGHT,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            int at=(y*WIDTH+x)*4;image.setRGB(x,HEIGHT-y-1,(bytes[at+3]&255)<<24|(bytes[at]&255)<<16|(bytes[at+1]&255)<<8|(bytes[at+2]&255));
        }
        ImageIO.write(image,"png",new File(OUT,name+".png"));
    }
    private static float[] floats(int name,int size){FloatBuffer b=BufferUtils.createFloatBuffer(16);glGetFloat(name,b);float[] out=new float[size];b.get(out);return out;}
    private static int[] ints(int name){IntBuffer b=BufferUtils.createIntBuffer(16);glGetInteger(name,b);int[] out=new int[4];b.get(out);return out;}
    private static void equal(float[] a,float[] b,String label){for(int i=0;i<a.length;i++)require(Math.abs(a[i]-b[i])<.00001,label+" "+i);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static <T>T bare(Class<T> type)throws Exception{return type.cast(((sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null)).allocateInstance(type));}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
