package me.youngjibbit.module.impl.render;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.ClickGui;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.*;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.*;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import org.lwjgl.opengl.GL20;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.net.Proxy;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Real six-consumer Pbuffer probes. Fixture data and every output stay in the external working directory. */
public final class VisQa01RenderCheck {
    static Minecraft mc;
    static ClientSettings c;
    static ModuleManager manager;
    static ArrayListHUDModule array;
    static WatermarkHUDModule watermark;
    static ArmorHUD armor;
    static GuiIngame hotbar;
    static ClickGui panel;
    static iOS ios;
    static final AtomicLong clock=new AtomicLong();
    static Method timeSource;
    static final String[] CONSUMERS={"hotbar","arraylist","watermark","armor","panel","ios"};
    static final java.util.List<Map<String,Object>> samples=new ArrayList<>();
    static final Map<BufferedImage,String> imageNames=new WeakHashMap<>();
    static final java.util.List<String> violations=new ArrayList<>();
    static final File OUT=new File("images");
    static final Theme A=Theme.SynthWave,B=Theme.Bonfire;
    public static void main(String[] args)throws Exception {
        OUT.mkdirs();File data=new File("fixture");data.mkdirs();System.setProperty("user.home",new File(data,"home").getAbsolutePath());
        try(PbufferTestContext context=new PbufferTestContext()) {
            mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(new Session("QA01Fixture","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),new GameConfiguration.DisplayInformation(960,640,false,false),new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),new GameConfiguration.GameInformation(false,"QA01 Pbuffer"),new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);SessionInfoRenderCheck.mc=mc;
            IMetadataSerializer meta=new IMetadataSerializer();meta.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);meta.registerMetadataSectionType(new AnimationMetadataSectionSerializer(),AnimationMetadataSection.class);meta.registerMetadataSectionType(new PackMetadataSectionSerializer(),PackMetadataSection.class);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(meta);resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc,"mcResourceManager",resources);set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));set(mc,"framebufferMc",new Framebuffer(960,640,true));
            manager=new ModuleManager();set(Pulsar.INSTANCE,"mm",manager);c=ClientSettings.current();
            ArmorHudCheck.run(); // Existing real atlas/model/glint setup and item-state assertions.
            Pulsar.INSTANCE.fontManager.close();
            Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
            Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"DmSansMedium 20","DmSansMedium 18","Product-Sans-Bold 16","Product-Sans-Regular 12","Poppins-Bold 23","SFProText-Bold 20","icon1 19","KhmerOureang 19","icon 23","Poppins-Medium 20","Product-Sans-Medium 20","SFProText-Medium 22","SFProText-Medium 17","Poppins-Regular 19","Poppins-Regular 21"});
            mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(resources);
            Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);mc.thePlayer=(EntityPlayerSP)((sun.misc.Unsafe)uf.get(null)).allocateInstance(EntityPlayerSP.class);mc.thePlayer.inventory=new InventoryPlayer(mc.thePlayer);
            // Nine real slots and item draw paths, no world, network handler or account files.
            for(int i=0;i<9;i++)mc.thePlayer.inventory.mainInventory[i]=new ItemStack(Items.apple,i+1);
            mc.thePlayer.inventory.currentItem=4;
            mc.thePlayer.inventory.armorInventory=new ItemStack[]{new ItemStack(Items.diamond_boots),new ItemStack(Items.diamond_leggings),new ItemStack(Items.diamond_chestplate),new ItemStack(Items.diamond_helmet)};
            mc.thePlayer.inventory.armorInventory[0].setItemDamage(Items.diamond_boots.getMaxDamage());mc.thePlayer.inventory.armorInventory[1].setItemDamage(Items.diamond_leggings.getMaxDamage()/2);
            hotbar=new GuiIngame(mc);array=(ArrayListHUDModule)manager.getModule(ArrayListHUDModule.class);watermark=(WatermarkHUDModule)manager.getModule(WatermarkHUDModule.class);armor=(ArmorHUD)manager.getModule(ArmorHUD.class);
            // Keep synthetic rows deterministic; no real gameplay module is enabled.
            for(Module m:new ArrayList<>(manager.getAllModules()))if(!(m instanceof ClientSettings)&&!(m instanceof ArrayListHUDModule)&&!(m instanceof WatermarkHUDModule)&&!(m instanceof ArmorHUD)&&!(m instanceof ClickGUI))manager.unregisterModule(m);
            Module rowA=new me.youngjibbit.clickgui.ArrayListRenderCheck.A(),rowB=new me.youngjibbit.clickgui.ArrayListRenderCheck.B(),rowC=new me.youngjibbit.clickgui.ArrayListRenderCheck.C();manager.registerModule(rowA);manager.registerModule(rowB);manager.registerModule(rowC);rowA.setToggled(true);rowB.setToggled(true);rowC.setToggled(true);
            watermark.getBooleanSetting("Player").setValue(false);watermark.getBooleanSetting("FPS").setValue(false);watermark.getModeSetting("Mode").setValue("Wordmark");watermark.getBooleanSetting("Text Glow").setValue(false);
            armor.getNumberSetting("Position X").setValue(100.0);armor.getNumberSetting("Position Y").setValue(100.0);
            panel=new ClickGui();ios=new iOS();set(Pulsar.INSTANCE,"iOS",ios);panel.setWorldAndResolution(mc,480,320);ios.setWorldAndResolution(mc,480,320);
            ((java.util.List<?>)get(ios,"particles")).clear();set(get(ios,"animationEngine"),"animationValue",1f);set(get(ios,"animationEngine"),"isDrawAnimation",false);
            timeSource=UiEffects.class.getDeclaredMethod("timeSource",LongSupplier.class);timeSource.setAccessible(true);
            TextEffects.releaseResources();ArrayListSurface.releaseResources();UIUtils.releaseResources();GuiShaderBloom.releaseResources();
            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
            configure();reset();draw("arraylist");draw("ios");draw("ios");Thread.sleep(250);
            unconfiguredHotbar();liveThemes();ratesAndMethods();policiesAndLocal();durability();parity();resourcesAndFallback();
            writeJson("pixel-evidence.json",samples);writeJson("violations.json",violations);
            require(violations.isEmpty(),"Consumer behavior violations: "+violations);
            cleanup();check(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"QA01 FBO lifetime imbalance");
            System.out.println("RESOURCE_COUNTS: "+RenderDiagnostics.snapshot());require(violations.isEmpty(),"Resource violations: "+violations);context.check();
            System.out.println("PASS: QA01 six real consumers; inspect pixel-evidence.json. Pbuffer only; live Display/world/shaderpack/144FPS NOT_RUN.");
        }
        System.exit(0);
    }
    static void configure(){c.gradientMotion.setValue("Animated");c.gradientSpeed.setValue(.25);c.direction.setValue("Horizontal");c.gradientMethod.setValue("Flow");c.surface.setValue("Gradient");c.gradientFillStrength.setValue(.28);c.surfaceOpacity.setValue(1.0);c.blur.setValue(false);c.glow.setValue(false);c.synchronize.setValue(true);c.gradients.setValue(true);Theme.setDefaultTheme(A);}
    static void reset()throws Exception{clock.set(0);timeSource.invoke(null,(LongSupplier)clock::get);UiEffects.invalidate();UiEffects.beginFrame();}
    static void advance(double seconds){int n=Math.max(1,(int)Math.ceil(seconds/.025));for(int i=0;i<n;i++){clock.addAndGet(Math.round(seconds/n*1e9));UiEffects.beginFrame();}}
    static BufferedImage draw(String consumer)throws Exception {
        UiEffects.beginFrame(); // Matches the real GameOverlay / EntityRenderer entry, same timestamp is a no-op.
        SessionInfoRenderCheck.backdrop();mc.currentScreen=null;
        try(RenderState state=new RenderState()) {
            state.screenSpace();
            switch(consumer){
                case "hotbar":invoke(hotbar,"renderTooltip",new Class[]{ScaledResolution.class,float.class},new ScaledResolution(mc),0f);break;
                case "arraylist":invoke(array,"renderArrayList",new Class[0]);break;
                case "watermark":invoke(watermark,"renderWatermark",new Class[0]);break;
                case "armor":armor.paint(armor.layout(mc.thePlayer.inventory.armorInventory,mc.thePlayer.getHeldItem(),new ScaledResolution(mc).getScaledWidth(),new ScaledResolution(mc).getScaledHeight()));break;
                case "panel":mc.currentScreen=panel;panel.drawScreen(-1,-1,0);break;
                case "ios":mc.currentScreen=ios;ios.drawScreen(-1,-1,0);GuiShaderBloom.flushBatch(2,3);break;
                default:throw new IllegalArgumentException(consumer);
            }
        }
        require(UiEffects.scene()==null&&!GuiShaderBloom.isBatching(),"Leaked scene/batch "+consumer);require(glGetError()==GL_NO_ERROR,"GL state "+consumer);
        return SessionInfoRenderCheck.capture();
    }
    static Rectangle region(String consumer)throws Exception {
        int s=new ScaledResolution(mc).getScaleFactor();
        switch(consumer){
            case "hotbar":return new Rectangle((480/2-90)*s,(320-25)*s,178*s,3*s);
            case "arraylist":return new Rectangle(10*s,20*s,125*s,45*s);
            case "watermark":return new Rectangle(10*s,10*s,95*s,25*s);
            case "armor":return new Rectangle(102*s,102*s,90*s,2*s);
            case "panel":return new Rectangle(((Number)get(panel,"x")).intValue()*s+9*s,((Number)get(panel,"y")).intValue()*s+10*s,80*s,16*s);
            // The header icon is a direct colored glyph, not a root fill or category label.
            default:return new Rectangle((((Number)get(ios,"x")).intValue()+104)*s,(((Number)get(ios,"y")).intValue()+4)*s,15*s,17*s);
        }
    }
    static void liveThemes()throws Exception {
        advance(.47);
        for(String consumer:CONSUMERS){Theme.setDefaultTheme(A);BufferedImage a=shot(consumer,"live-A");Theme.setDefaultTheme(B);BufferedImage b=shot(consumer,"live-B");Theme.setDefaultTheme(A);BufferedImage back=shot(consumer,"live-A-return");Rectangle r=region(consumer);check(changes(a,b,r)>12,consumer+" A-B palette unchanged");check(changes(a,back,r)==0,consumer+" A-B-A did not restore same region");}
        System.out.println("PASS: live A-B-A drawn without reopening screens/HUDs; region RGB deltas recorded");
    }
    static void unconfiguredHotbar()throws Exception {
        // Legacy/unconfigured rendering also owes the canonical static Theme policy.
        manager.unregisterModule(c);UiEffects.invalidate();reset();Theme.setDefaultTheme(A);
        BufferedImage first=shot("hotbar","unconfigured-static-first");Thread.sleep(180);BufferedImage after=shot("hotbar","unconfigured-static-after");
        check(changes(first,after,new Rectangle(370,570,210,65))==0,"Hotbar uses wallclock despite static canonical Theme");
        manager.registerModule(c);UiEffects.invalidate();reset();
    }
    static void ratesAndMethods()throws Exception {
        Map<String,BufferedImage> slow=new LinkedHashMap<>();
        for(Theme theme:new Theme[]{A,B})for(String method:new String[]{"Flow","Ping-Pong","Pulse"})for(double rate:new double[]{.1,.8}){
            Theme.setDefaultTheme(theme);
            c.gradientMethod.setValue(method);c.gradientSpeed.setValue(rate);reset();advance(.2);
            Map<String,BufferedImage> before=new LinkedHashMap<>();
            for(String consumer:CONSUMERS)before.put(consumer,shot(consumer,theme.name()+"-"+method+"-rate-"+rate+"-t0.2"));
            advance(.4);check(Math.abs(UiEffects.gradient(A,UiEffects.Channel.TEXT).phase-.6*rate)<.000001,"canonical rate "+rate);
            for(String consumer:CONSUMERS){BufferedImage after=shot(consumer,theme.name()+"-"+method+"-rate-"+rate+"-t0.6");check(changes(before.get(consumer),after,region(consumer))>6,consumer+" static for "+method+" rate "+rate);String key=theme.name()+method+consumer;if(rate==.1)slow.put(key,after);else check(changes(slow.get(key),after,region(consumer))>6,consumer+" ignores global speed at identical dt: "+theme.name()+"/"+method);}
        }
        c.gradientSpeed.setValue(.25);c.gradientMethod.setValue("Flow");
        for(Theme theme:new Theme[]{A,B}){Theme.setDefaultTheme(theme);c.gradients.setValue(false);Map<String,BufferedImage> first=new LinkedHashMap<>();for(String x:CONSUMERS)first.put(x,shot(x,theme.name()+"-gradients-off-t0"));advance(.4);for(String x:CONSUMERS)check(changes(first.get(x),shot(x,theme.name()+"-gradients-off-t1"),region(x))==0,x+" gradient OFF changes");}c.gradients.setValue(true);Theme.setDefaultTheme(A);
    }
    static void policiesAndLocal()throws Exception {
        for(String policy:new String[]{"ambient","reduced","animations","Static","Solid"}){
            if(policy.equals("ambient"))c.ambientMotion.setValue(false);else if(policy.equals("reduced"))c.reducedMotion.setValue(true);else if(policy.equals("animations"))c.animations.setValue(false);else c.gradientMotion.setValue(policy);
            for(String x:CONSUMERS){BufferedImage a=shot(x,"freeze-"+policy+"-start");advance(.3);BufferedImage b=shot(x,"freeze-"+policy);check(changes(a,b,region(x))==0,x+" moves during "+policy);}
            c.ambientMotion.setValue(true);c.reducedMotion.setValue(false);c.animations.setValue(true);c.gradientMotion.setValue("Animated");
        }
        c.synchronize.setValue(false);c.textMotion.setValue("Animated");c.textSpeed.setValue(.12);c.borderMotion.setValue("Static");c.glowMotion.setValue("Animated");c.glowSpeed.setValue(.7);reset();advance(.5);
        check(Math.abs(UiEffects.gradient(A,UiEffects.Channel.TEXT).phase-.06)<.000001,"independent TEXT rate");check(Math.abs(UiEffects.gradient(A,UiEffects.Channel.GLOW).phase-.35)<.000001,"independent GLOW rate");
        for(String x:CONSUMERS)shot(x,"sync-off");c.synchronize.setValue(true);
        for(String style:new String[]{"Horizontal","Vertical","Solid","Theme"})for(String design:new String[]{"Wordmark","Status"})for(boolean glow:new boolean[]{false,true}){watermark.getModeSetting("Text Style").setValue(style);watermark.getModeSetting("Mode").setValue(design);watermark.getBooleanSetting("Text Glow").setValue(glow);c.glow.setValue(glow);shot("watermark","style-"+style+"-"+design+"-glow-"+glow);}
        c.glow.setValue(false);watermark.getBooleanSetting("Text Glow").setValue(false);watermark.getModeSetting("Text Style").setValue("Theme");watermark.getModeSetting("Mode").setValue("Wordmark");
        array.getModeSetting("Effects Source").setValue("Local");array.getModeSetting("Local Rectangle Substance").setValue("Legacy");shot("arraylist","local-legacy");array.getModeSetting("Effects Source").setValue("Global");
        float phase=UiEffects.gradient(A,UiEffects.Channel.TEXT).phase;for(int i=0;i<12;i++)for(String x:CONSUMERS)draw(x);check(phase==UiEffects.gradient(A,UiEffects.Channel.TEXT).phase,"multiple consumers advance identical timestamp");
    }
    static void durability()throws Exception {
        int[] first={ArmorHUD.durabilityColor(A,0),ArmorHUD.durabilityColor(A,.5f),ArmorHUD.durabilityColor(A,1)};advance(.47);
        check(Arrays.equals(first,new int[]{ArmorHUD.durabilityColor(A,0),ArmorHUD.durabilityColor(A,.5f),ArmorHUD.durabilityColor(A,1)}),"Durability phase changed semantic information");
        c.semanticColorPriority.setValue(true);check(ArmorHUD.durabilityColor(A,0)==0xffff667f&&ArmorHUD.durabilityColor(A,.5f)==0xffffc969&&ArmorHUD.durabilityColor(A,1)==0xff65e0ad,"Durability priority ON loses red/yellow/green meaning");shot("armor","semantic-priority-on");c.semanticColorPriority.setValue(false);shot("armor","semantic-priority-off");c.semanticColorPriority.setValue(true);
    }
    static void parity()throws Exception {
        int count=0,max=0;
        try(ShaderUtil shader=new ShaderUtil("#version 120\n// UI_GRADIENT\nvoid main(){float t=uiPosition(uiText);gl_FragColor=vec4(t,t,t,1.0);}",true)) {
            for(int scale=1;scale<=4;scale++){
                mc.displayWidth=1280;mc.displayHeight=960;mc.gameSettings.guiScale=scale;mc.getFramebuffer().createBindFramebuffer(1280,960);
                check(new ScaledResolution(mc).getScaleFactor()==scale,"effective scale "+scale);
                for(String method:new String[]{"Flow","Ping-Pong","Pulse"})for(double t:new double[]{.13,.61}){c.gradientMethod.setValue(method);reset();advance(t);try(RenderState state=new RenderState()){state.screenSpace();shader.init();UiEffects.upload(shader,A);ShaderUtil.drawQuads();shader.unload();}BufferedImage img=SessionInfoRenderCheck.capture();UiEffects.Gradient g=UiEffects.gradient(A,UiEffects.Channel.TEXT);
                    for(int py:new int[]{0,211,959})for(int px:new int[]{0,379,1279}){int cpu=Math.round(g.position((px+.5f)/scale,(py+.5f)/scale,1280f/scale,960f/scale)*255),gpu=img.getRGB(px,py)&255;max=Math.max(max,Math.abs(cpu-gpu));check(Math.abs(cpu-gpu)<=1,"CPU-GLSL scale="+scale);count++;}ImageIO.write(img,"png",new File(OUT,"parity-"+scale+"-"+method+"-"+t+".png"));}
            }
        }
        System.out.println("PASS: scaled CPU/GLSL samples="+count+", maxDeviation="+max);
        mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);c.gradientMethod.setValue("Flow");
    }
    static void resourcesAndFallback()throws Exception {
        float phase=UiEffects.gradient(A,UiEffects.Channel.TEXT).phase;TextEffects.releaseResources();ArrayListSurface.releaseResources();UIUtils.releaseResources();GuiShaderBloom.releaseResources();for(String x:CONSUMERS)shot(x,"reload");check(phase==UiEffects.gradient(A,UiEffects.Channel.TEXT).phase,"resources reset canonical phase");
        for(boolean shaders:new boolean[]{false,true}){boolean old=OpenGlHelper.shadersSupported,oldFbo=mc.gameSettings.fboEnable;OpenGlHelper.shadersSupported=shaders;mc.gameSettings.fboEnable=!shaders;try{for(String x:new String[]{"arraylist","watermark","armor","hotbar","panel"})shot(x,shaders?"no-fbo":"no-shader");}finally{OpenGlHelper.shadersSupported=old;mc.gameSettings.fboEnable=oldFbo;}}
        // iOS backdrop requires shaders; total iOS NoShader is outside this fixture's supported contract.
        System.out.println("COVERAGE: real consumer FBO release/recreate and limited readable fallback; iOS backdrop NoShader NOT_RUN");
    }
    static BufferedImage shot(String consumer,String label)throws Exception {
        BufferedImage img=draw(consumer);String file=consumer+"-"+label+".png";ImageIO.write(img,"png",new File(OUT,file));imageNames.put(img,file);Rectangle r=region(consumer);Map<String,Object> item=new LinkedHashMap<>();item.put("file",file);item.put("consumer",consumer);item.put("region",new int[]{r.x,r.y,r.width,r.height});item.put("theme",Theme.getDefaultTheme().name());item.put("stops",new int[]{Theme.getDefaultTheme().getPrimaryGradientRGB(),Theme.getDefaultTheme().getSecondaryGradientRGB()});item.put("method",c.gradientMethod.getValue());item.put("motion",c.gradientMotion.getValue());item.put("rate",c.gradientSpeed.getValue());item.put("phase",UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.TEXT).phase);item.put("time",UiEffects.time());item.put("gradients",c.gradients.getValue());item.put("sync",c.synchronize.getValue());item.put("fillStrength",c.gradientFillStrength.getValue());item.put("sampleRGB",img.getRGB(r.x+r.width/2,r.y+r.height/2));samples.add(item);if(consumer.equals("ios"))verifyIosGlyph(img,item);if(consumer.equals("panel")&&UiEffects.effectsSupported())verifyPanelTitle(img,item);return img;
    }
    static void verifyPanelTitle(BufferedImage actual,Map<String,Object> record)throws Exception {
        Rectangle r=region("panel");int scale=new ScaledResolution(mc).getScaleFactor();
        try(RenderState state=new RenderState()) {
            state.screenSpace();GL20.glUseProgram(0);GlStateManager.disableDepth();GlStateManager.disableAlpha();GlStateManager.enableBlend();glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
            Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 28").drawString(Pulsar.INSTANCE.getName(),(int)(((Number)get(panel,"x")).floatValue()+9),(int)(((Number)get(panel,"y")).floatValue()+10),-1);
        }
        BufferedImage mask=SessionInfoRenderCheck.capture();long error=0;int pixels=0;
        for(int y=r.y;y<r.y+r.height;y++)for(int x=r.x;x<r.x+r.width;x++)if((mask.getRGB(x,y)&255)>250){int expected=UiEffects.gradientColor(Theme.getDefaultTheme(),UiEffects.Channel.TEXT,(x+.5f)/scale,(y+.5f)/scale),got=actual.getRGB(x,y);for(int shift:new int[]{0,8,16})error+=Math.abs((expected>>shift&255)-(got>>shift&255));pixels++;}
        double mean=pixels==0?999:error/(pixels*3.0);record.put("titleOpaquePixels",pixels);record.put("titleMeanRgbError",mean);check(pixels>100,"Panel title mask absent");check(mean<=3,"Panel real title diverges from canonical TEXT sampler: "+mean);
    }
    static void verifyIosGlyph(BufferedImage actual,Map<String,Object> record)throws Exception {
        Rectangle r=region("ios");int scale=new ScaledResolution(mc).getScaleFactor();
        try(RenderState state=new RenderState()) {
            state.screenSpace();GL20.glUseProgram(0);GlStateManager.disableDepth();GlStateManager.disableAlpha();GlStateManager.enableBlend();glBlendFunc(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA);glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);
            Pulsar.INSTANCE.fontManager.getFont("icon1 19").drawString("l",(int)(((Number)get(ios,"x")).floatValue()+105),((Number)get(ios,"y")).floatValue()+6,-1);
        }
        BufferedImage mask=SessionInfoRenderCheck.capture();long error=0;int pixels=0;
        for(int y=r.y;y<r.y+r.height;y++)for(int x=r.x;x<r.x+r.width;x++)if((mask.getRGB(x,y)&255)>250){int expected=UiEffects.gradientColor(Theme.getDefaultTheme(),UiEffects.Channel.TEXT,(x+.5f)/scale,(y+.5f)/scale),got=actual.getRGB(x,y);for(int shift:new int[]{0,8,16})error+=Math.abs((expected>>shift&255)-(got>>shift&255));pixels++;}
        double mean=pixels==0?999:error/(pixels*3.0);record.put("iconOpaquePixels",pixels);record.put("iconMeanRgbError",mean);
        check(pixels>10,"iOS real header icon mask is absent");check(mean<=14,"iOS header icon diverges from canonical per-pixel TEXT sampler: "+mean);
    }
    static int changes(BufferedImage a,BufferedImage b,Rectangle r){int n=0;for(int y=r.y;y<r.y+r.height;y++)for(int x=r.x;x<r.x+r.width;x++)if(a.getRGB(x,y)!=b.getRGB(x,y))n++;Map<String,Object> row=new LinkedHashMap<>();row.put("first",imageNames.get(a));row.put("second",imageNames.get(b));row.put("comparisonRegion",new int[]{r.x,r.y,r.width,r.height});row.put("changedPixels",n);samples.add(row);return n;}
    static void check(boolean ok,String message){if(!ok){violations.add(message);System.out.println("BEHAVIOR_RED: "+message);}}
    static void writeJson(String name,Object value)throws Exception{try(Writer out=new OutputStreamWriter(new FileOutputStream(name),"UTF-8")){new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(value,out);}}
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static Object get(Object target,String name)throws Exception{return SessionInfoRenderCheck.get(target,name);}
    static void set(Object target,String name,Object value)throws Exception{SessionInfoRenderCheck.set(target,name,value);}
    static Object invoke(Object target,String name,Class<?>[] types,Object...args)throws Exception{Method m=target.getClass().getDeclaredMethod(name,types);m.setAccessible(true);return m.invoke(target,args);}
    static void cleanup(){panel.onGuiClosed();ios.onGuiClosed();array.onDisable();watermark.onDisable();armor.onDisable();TextEffects.releaseResources();ArrayListSurface.releaseResources();UIUtils.releaseResources();GuiShaderBloom.releaseResources();me.youngjibbit.gui.MenuShader.releaseResources();}
}
