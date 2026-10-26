package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.ResourceLocation;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.lwjgl.opengl.GL11.*;

/** Actual shaders/control composition on deterministic Pbuffer frames; no player session. */
public final class ThemeSyncRenderCheck {
    static final Minecraft mc=SessionInfoRenderCheck.mc;
    static final AtomicLong nanos=new AtomicLong();
    static ClientSettings c;
    static void run() throws Exception {
        ModuleManager old=Pulsar.INSTANCE.getMm();ThemeCatalog catalog=ThemeCatalog.global();
        ArrayListSurface surface=new ArrayListSurface();
        Method clock=UiEffects.class.getDeclaredMethod("timeSource",java.util.function.LongSupplier.class);clock.setAccessible(true);
        SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());c=ClientSettings.current();
        ThemeCatalog.install(new ThemeCatalog(null));Theme.setDefaultTheme(Theme.Retro);
        try {
            mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);
            c.surface.setValue("Gradient");c.gradientFillStrength.setValue(1.0);c.border.setValue(false);c.blur.setValue(false);c.glow.setValue(false);
            c.gradientMotion.setValue("Animated");c.gradientSpeed.setValue(.25);c.synchronize.setValue(true);c.direction.setValue("Horizontal");
            clock.invoke(null,(java.util.function.LongSupplier)nanos::get);nanos.set(0);UiEffects.beginFrame();
            java.util.List<BufferedImage> images=new ArrayList<>();java.util.List<String> labels=new ArrayList<>();
            for(String direction:new String[]{"Horizontal","Vertical","Up","Left"}) {
                c.direction.setValue(direction);nanos.set(0);clock.invoke(null,(java.util.function.LongSupplier)nanos::get);UiEffects.beginFrame();
                BufferedImage first=panel(surface,Theme.Retro),firstDecorations=decorations(surface,Theme.Retro);
                nanos.set(1000000000);UiEffects.beginFrame();BufferedImage next=panel(surface,Theme.Retro),nextDecorations=decorations(surface,Theme.Retro);
                require(!sameRegion(firstDecorations,nextDecorations,200,59,400,4),"Global border ignores configured animation: "+direction);
                require(!sameRegion(firstDecorations,nextDecorations,140,160,350,28),"Global text ignores configured animation: "+direction);
                require(!same(first,next),"Animated production gradient is static: "+direction);
                float x=100.25f,y=100.25f;int expected=UiEffects.gradientColor(Theme.Retro,UiEffects.Channel.BORDER,x,y);
                near(expected,next.getRGB(200,200),3,"GPU gradient phase/direction "+direction);
                for(UiEffects.Channel channel:UiEffects.Channel.values())require(UiEffects.gradient(Theme.Retro,channel).phase==.25f,"global channels have independent clocks");
                images.add(next);labels.add(direction+" / shared phase .25");
                c.reducedMotion.setValue(true);nanos.set(3000000000L);UiEffects.beginFrame();require(same(nextDecorations,decorations(surface,Theme.Retro)),"Reduced Motion changes Global text/border pixels");require(same(next,panel(surface,Theme.Retro)),"Reduced Motion changes production pixels");c.reducedMotion.setValue(false);
                c.gradientMotion.setValue("Static");BufferedImage still=panel(surface,Theme.Retro);nanos.set(4000000000L);UiEffects.beginFrame();require(same(still,panel(surface,Theme.Retro)),"Static production pixels change");c.gradientMotion.setValue("Animated");
            }
            SessionInfoRenderCheck.sheet("21-theme-sync-directions.png",labels,images);
            ESP owner=new ESP();ArrayListSurface owned=(ArrayListSurface)SessionInfoRenderCheck.get(owner,"surfaces");
            try {
                owner.getModeSetting("Theme").setValue("Neon");owner.getModeSetting("Effects Source").setValue("Global");
                require(owned.theme(Theme.Cherry)==Theme.Retro,"Global inherits a stale/requested local palette");
                near(UiEffects.gradientColor(Theme.Retro,UiEffects.Channel.BORDER,100.25f,100.25f),panel(owned,Theme.Cherry).getRGB(200,200),3,"Global production palette");
                owner.getModeSetting("Effects Source").setValue("Local");require(owned.theme(Theme.Cherry)==Theme.Neon,"Explicit Local palette lost");
                owner.getModeSetting("Local Rectangle Substance").setValue("Gradient");
                near(UiEffects.gradientColor(Theme.Neon,UiEffects.Channel.BORDER,100.25f,100.25f),panel(owned,Theme.Cherry).getRGB(200,200),3,"Local production palette");
                owner.getModeSetting("Effects Source").setValue("Global");require(owner.getModeSetting("Theme").getValue().equals("Neon"),"Global rewrote saved local palette");
            } finally {owned.close();}
            c.gradientMotion.setValue("Static");c.direction.setValue("Horizontal");
            Theme translucent=Theme.custom("custom:00000000-0000-0000-0000-000000000021","RGBA test",Arrays.asList(new Color(255,0,0,128),new Color(0,0,255,128)),false,true,.25f,new Color(0,0,0,0),Color.WHITE,new Color(0,255,0,64),.75f,9,1,1);
            ThemeCatalog.global().preview(translucent);
            SessionInfoRenderCheck.backdrop();BufferedImage original=SessionInfoRenderCheck.capture();
            BufferedImage rgba=panel(surface,translucent);int palette=UiEffects.gradientColor(translucent,UiEffects.Channel.BORDER,100.25f,100.25f);
            near(Theme.interpolateRGB(original.getRGB(200,200),palette,(palette>>>24)/255f),rgba.getRGB(200,200),3,"Material RGBA applied once");
            int armor=ArmorHUD.durabilityColor(translucent,.5f);require((armor>>>24)==128,"Durability dropped stop alpha");
            c.reducedMotion.setValue(true);float frozen=translucent.phase();nanos.addAndGet(9000000000L);UiEffects.beginFrame();require(translucent.phase()==frozen,"Theme phase ignores Reduced Motion");c.reducedMotion.setValue(false);
            c.border.setValue(true);c.borderColor.setValue("Accent");
            SessionInfoRenderCheck.backdrop();surface.panel(20,30,200,100,5,translucent,false,false,false,5,0,1,()->{});BufferedImage accent=SessionInfoRenderCheck.capture();
            c.borderColor.setValue("Gradient");SessionInfoRenderCheck.backdrop();surface.panel(20,30,200,100,5,translucent,false,false,false,5,0,1,()->{});require(!same(accent,SessionInfoRenderCheck.capture()),"Explicit Accent source has no production effect");
            ThemeCatalog.global().cancelPreview();Theme.setDefaultTheme(Theme.Retro);
            c.glow.setValue(true);c.glowIntensity.setValue(1.0);c.border.setValue(false);
            BufferedImage ordinary=halo(false),mismatch=halo(true);require(same(ordinary,mismatch),"Cached/raw blend mismatch corrupts glow pixels");
            SessionInfoRenderCheck.backdrop();BufferedImage clean=SessionInfoRenderCheck.capture();
            near(clean.getRGB(10,10),ordinary.getRGB(10,10),0,"Glow changed unrelated viewport corner");
            int visible=0;for(int y=0;y<ordinary.getHeight();y++)for(int x=0;x<ordinary.getWidth();x++){
                int a=ordinary.getRGB(x,y),b=clean.getRGB(x,y);int delta=0;
                for(int shift:new int[]{0,8,16})delta=Math.max(delta,Math.abs((a>>shift&255)-(b>>shift&255)));
                if(delta>3)visible++;
            }
            require(visible>100,"Glow disappeared instead of being repaired");
            Theme invisible=Theme.custom("custom:00000000-0000-0000-0000-000000000023","Transparent glow",Arrays.asList(new Color(255,0,0,0),new Color(0,0,255,0)),false,false,.18f,new Color(0,0,0,0),Color.WHITE,Color.GREEN,.75f,9,1,1);
            ThemeCatalog.global().preview(invisible);require(same(clean,halo(false)),"Glow ignores zero palette alpha");ThemeCatalog.global().cancelPreview();
            javax.imageio.ImageIO.write(ordinary,"png",new java.io.File(SessionInfoRenderCheck.OUT,"22-bloom-full.png"));
            images=Arrays.asList(ordinary,mismatch);labels=Arrays.asList("Glow / matching blend state","Glow / cached-enabled, raw-disabled entry");SessionInfoRenderCheck.sheet("22-bloom-state.png",labels,images);
            float[] model=matrix(GL_MODELVIEW_MATRIX),projection=matrix(GL_PROJECTION_MATRIX);int oldMode=glGetInteger(GL_MATRIX_MODE);
            GlStateManager.enableDepth();glEnable(GL_DEPTH_TEST);GlStateManager.depthMask(false);glDepthMask(false);
            GuiShaderBloom.beginBatch();UiEffects.drawGlow(100,100,100,35,5,0xffffffff,.35f,2,2);GuiShaderBloom.flushBatch(2,2);
            require(Arrays.equals(model,matrix(GL_MODELVIEW_MATRIX))&&Arrays.equals(projection,matrix(GL_PROJECTION_MATRIX))&&oldMode==glGetInteger(GL_MATRIX_MODE),"Bloom changed matrices/mode");
            require(glIsEnabled(GL_DEPTH_TEST)&&!glGetBoolean(GL_DEPTH_WRITEMASK),"Bloom changed depth state");
            // Actual pause menu on a world-presence fixture: no world simulation is invoked.
            SessionInfoRenderCheck.set(mc,"renderEngine",new TextureManager(mc.getResourceManager()));
            mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
            Field unsafe=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);WorldClient oldWorld=mc.theWorld;
            mc.theWorld=(WorldClient)((sun.misc.Unsafe)unsafe.get(null)).allocateInstance(WorldClient.class);
            try {
                c.border.setValue(true);
                // Narrow known English fixture labels; production translations are unchanged.
                java.util.Map<String,String> english=new LinkedHashMap<>();
                english.put("menu.game","Game menu");english.put("menu.returnToGame","Back to Game");english.put("menu.options","Options...");english.put("menu.shareToLan","Open to LAN");english.put("gui.achievements","Achievements");english.put("gui.stats","Statistics");english.put("menu.disconnect","Disconnect");
                net.minecraft.client.resources.I18n.getLocaleProperties().putAll(english);
                GuiIngameMenu pause=new GuiIngameMenu();pause.setWorldAndResolution(mc,480,320);
                SessionInfoRenderCheck.backdrop();BufferedImage world=SessionInfoRenderCheck.capture();pause.drawDefaultBackground();BufferedImage background=SessionInfoRenderCheck.capture();
                require(background.getRGB(10,10)!=background.getRGB(20,10),"Pause replaced world checker with an opaque shader");
                require((background.getRGB(10,10)&255)<(world.getRGB(10,10)&255),"Pause should dim the existing world");
                for(String material:new String[]{"Clear","Gradient","Blur","Liquid Glass"}){
                    c.surface.setValue(material);c.blur.setValue(true);SessionInfoRenderCheck.backdrop();pause.drawScreen(-1,-1,0);BufferedImage shot=SessionInfoRenderCheck.capture();
                    require(shot.getRGB(10,10)==background.getRGB(10,10),"Menu control/glow corrupts unrelated corner: "+material);
                    java.awt.Graphics2D caption=shot.createGraphics();caption.setFont(new java.awt.Font("SansSerif",java.awt.Font.PLAIN,13));caption.setColor(new Color(0,0,0,180));caption.fillRect(5,5,285,24);caption.setColor(Color.WHITE);caption.drawString("Synthetic checker backdrop / Pbuffer",12,22);caption.dispose();
                    javax.imageio.ImageIO.write(shot,"png",new java.io.File(SessionInfoRenderCheck.OUT,"23-pause-full-"+material.replace(" ","-")+".png"));
                    SessionInfoRenderCheck.sheet("23-pause-"+material.replace(" ","-")+".png",Collections.singletonList(material+" / actual pause controls on synthetic world"),Collections.singletonList(shot));
                }
            } finally {mc.theWorld=oldWorld;}
            require(glGetError()==GL_NO_ERROR,"Theme sync composition GL error");
            System.out.println("PASS: production animated/static/reduced gradients in four directions, Global/Local palette preservation, explicit border Accent, durability RGBA/frozen phase, bloom cache/raw state parity and viewport bounds, actual pause controls in all four materials. Pbuffer/synthetic world only.");
        } finally {surface.close();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);ThemeCatalog.install(catalog);UiEffects.invalidate();clock.invoke(null,(java.util.function.LongSupplier)System::nanoTime);}
    }
    static BufferedImage panel(ArrayListSurface surface,Theme theme){SessionInfoRenderCheck.backdrop();surface.panel(20,30,400,220,5,theme,true,false,false,5,1,0,true,()->{});return SessionInfoRenderCheck.capture();}
    static BufferedImage decorations(ArrayListSurface surface,Theme theme){
        c.border.setValue(true);SessionInfoRenderCheck.backdrop();surface.panel(20,30,400,220,5,theme,false,false,false,5,0,1,()->TextEffects.direct(theme,()->Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 20").drawString("Global text + border",70,80,-1)));
        BufferedImage image=SessionInfoRenderCheck.capture();c.border.setValue(false);return image;
    }
    static BufferedImage halo(boolean mismatch){SessionInfoRenderCheck.backdrop();GlStateManager.enableBlend();if(mismatch)glDisable(GL_BLEND);GuiShaderBloom.beginBatch();UiEffects.drawGlow(100,100,100,35,5,Theme.Retro.getPrimaryGradientRGB(),.35f,2,2);GuiShaderBloom.flushBatch(2,2);return SessionInfoRenderCheck.capture();}
    static float[] matrix(int name){java.nio.FloatBuffer buffer=org.lwjgl.BufferUtils.createFloatBuffer(16);glGetFloat(name,buffer);float[] values=new float[16];buffer.get(values);return values;}
    static boolean sameRegion(BufferedImage a,BufferedImage b,int x,int y,int w,int h){return Arrays.equals(a.getRGB(x,y,w,h,null,0,w),b.getRGB(x,y,w,h,null,0,w));}
    static boolean same(BufferedImage a,BufferedImage b){return Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
    static void near(int expected,int actual,int tolerance,String message){for(int shift:new int[]{0,8,16})require(Math.abs((expected>>shift&255)-(actual>>shift&255))<=tolerance,message+": "+Integer.toHexString(expected)+" != "+Integer.toHexString(actual));}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
