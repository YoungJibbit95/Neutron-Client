package me.youngjibbit.utils.render;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.gui.MainMenu;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.*;
import me.youngjibbit.notifications.*;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.lwjgl.opengl.GL11.*;

/** Standalone real GPU readback. Run only in an external directory with disposable fixture data. */
public final class Vis02RenderCheck {
    private static Minecraft mc;
    private static ClientSettings settings;
    private static ArrayListHUDModule hud;
    private static MainMenu menu;
    private static final AtomicLong clock=new AtomicLong();
    private static final String[] METHODS={"Flow","Ping-Pong","Pulse"};
    private static final File OUT=new File("images");
    public static void main(String[] args) throws Exception {
        OUT.mkdirs();File data=new File("fixture");data.mkdirs();
        System.setProperty("user.home",new File(data,"home").getAbsolutePath());
        try(PbufferTestContext context=new PbufferTestContext()) {
            mc=new Minecraft(new GameConfiguration(
                new GameConfiguration.UserInformation(new Session("VIS02Fixture","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                new GameConfiguration.DisplayInformation(960,640,false,false),
                new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                new GameConfiguration.GameInformation(false,"VIS-02 Pbuffer"),new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);
            System.out.println("GPU: "+glGetString(GL_VENDOR)+" / "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(new IMetadataSerializer());
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc,"mcResourceManager",resources);set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
            RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
            set(mc,"framebufferMc",new Framebuffer(960,640,true));
            ModuleManager manager=new ModuleManager();set(Pulsar.INSTANCE,"mm",manager);
            for(Module m:new ArrayList<>(manager.getAllModules()))manager.unregisterModule(m);
            settings=new ClientSettings();manager.registerModule(settings);hud=new ArrayListHUDModule();manager.registerModule(hud);
            Module a=new me.youngjibbit.clickgui.ArrayListRenderCheck.A(),b=new me.youngjibbit.clickgui.ArrayListRenderCheck.B();
            manager.registerModule(a);manager.registerModule(b);a.setToggled(true);b.setToggled(true);
            Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
            Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"DmSansMedium 44","DmSansMedium 25","DmSansMedium 20","Product-Sans-Regular 18","Product-Sans-Bold 20"});
            Theme.setDefaultTheme(Theme.Cotton_candy);
            settings.gradientMotion.setValue("Animated");settings.gradientSpeed.setValue(.25);settings.direction.setValue("Horizontal");
            settings.surface.setValue("Gradient");settings.blur.setValue(false);settings.glow.setValue(false);
            UiEffects.timeSource(clock::get);UiEffects.invalidate();UiEffects.beginFrame();
            menu=new MainMenu();menu.setWorldAndResolution(mc,480,320);
            shaderParity();consumerShots();freezeAndReload();semanticShots();fallbackShots();performanceAndLifetime();
            menu.onGuiClosed();hud.onDisable();NotificationManager.releaseResources();
            TextEffects.releaseResources();ArrayListSurface.releaseResources();MainMenu.releaseResources();UIUtils.releaseResources();GuiShaderBloom.releaseResources();
            me.youngjibbit.utils.render.shader.blur.GaussianBlur.releaseResources();
            mc.getFramebuffer().deleteFramebuffer();
            require(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"FBO lifetime imbalance: "+RenderDiagnostics.snapshot());
            require(UiEffects.scene()==null,"Leaked scene scope");require(glGetError()==GL_NO_ERROR,"Final GL error");context.check();
            System.out.println("PASS: FBO ownership balanced: "+RenderDiagnostics.snapshot());
        }
        System.out.println("PASS: VIS-02 Pbuffer only. NOT_RUN: live Display/fullscreen, world, shaderpack, 144 FPS.");
        System.exit(0);
    }
    private static void shaderParity() throws Exception {
        String[] consumers={"Flauxy/Shaders/textDirect.frag","Flauxy/Shaders/textEffect.frag","Flauxy/Shaders/arraylistSurface.frag","roundRectOutline","kawaseUpBloom","roundedRectGradient","roundedRect"};
        for(String path:consumers)try(ShaderUtil shader=new ShaderUtil(path)){require(GL20.glGetProgrami(shader.getProgramId(),GL20.GL_LINK_STATUS)!=0,"Compile "+path);}
        int samples=0,maxError=0;
        try(ShaderUtil shader=new ShaderUtil("#version 120\n// UI_GRADIENT\nvoid main(){float t=uiPosition(uiText);gl_FragColor=vec4(t,t,t,1.0);}",true);RenderState state=new RenderState()) {
            state.screenSpace();
            for(int mode=0;mode<5;mode++)for(int axis:new int[]{1,2,-1,-2})for(float phase:new float[]{0,.13f,.5f,.9999f}) {
                clear();shader.init();UiEffects.upload(shader,Theme.Cotton_candy);shader.setUniformf("uiText",axis,mode,phase);ShaderUtil.drawQuads();shader.unload();
                BufferedImage image=capture();UiEffects.Gradient gradient=new UiEffects.Gradient(axis,mode,phase,.25f);
                for(int y:new int[]{0,1,319,639})for(int x:new int[]{0,1,479,959}) {
                    int value=image.getRGB(x,y)&255;int expected=Math.round(gradient.position(x+.5f,y+.5f,960,640)*255);
                    int error=Math.abs(value-expected);maxError=Math.max(maxError,error);require(error<=1,"CPU/GLSL mismatch mode="+mode+" axis="+axis+" error="+error);samples++;
                }
                if(axis==1&&phase==.13f)save(image,"parity-mode-"+mode+".png");
            }
        }
        System.out.println("PASS: GLSL 1.20 all 7 include consumers; "+samples+" real reference pixels, max 8-bit deviation="+maxError+" (limit 1)");
    }
    private static void consumerShots() throws Exception {
        drawHud();Thread.sleep(250); // finish the existing visibility transition; no substitute gradient timer
        for(String method:METHODS) {
            settings.gradientMethod.setValue(method);BufferedImage lastMenu=null,lastHud=null;
            double previous=0;
            for(double phase:new double[]{.07,.21,.41}) {
                advance((phase-previous)*4);previous=phase;
                menu.drawScreen(-1,-1,0);BufferedImage gui=capture();verifyTitle(gui);
                BufferedImage title=titleCrop(gui);if(lastMenu!=null)require(differences(lastMenu,title)>150,"Title method phase invisible "+method);lastMenu=title;
                save(gui,"menu-"+method+"-"+phase+".png");
                clear();drawHud();BufferedImage array=capture();if(lastHud!=null)require(differences(lastHud,array)>100,"Actual HUD method phase invisible "+method);lastHud=array;
                save(array,"hud-"+method+"-"+phase+".png");
            }
            advance((1-previous)*4);
        }
        // Same phase, same layout: compare methods through actual consumers, not only uniforms.
        advance(.52);BufferedImage priorTitle=null,priorHud=null;
        for(String method:METHODS) {
            settings.gradientMethod.setValue(method);menu.drawScreen(-1,-1,0);BufferedImage title=titleCrop(capture());
            clear();drawHud();BufferedImage array=capture();
            if(priorTitle!=null){require(differences(priorTitle,title)>100,"Methods share title pixels");require(differences(priorHud,array)>100,"Methods share HUD pixels");}
            priorTitle=title;priorHud=array;
        }
        System.out.println("PASS: real MainMenu title and ArrayListHUD GLOBAL at three phases/method; CPU color matching in title; fixed-phase method pixel differences");
    }
    private static void freezeAndReload() throws Exception {
        for(String flag:new String[]{"Ambient","Reduced","Animations"}) {
            settings.ambientMotion.setValue(!flag.equals("Ambient"));settings.reducedMotion.setValue(flag.equals("Reduced"));settings.animations.setValue(!flag.equals("Animations"));
            float phase=UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.TEXT).phase,time=UiEffects.time();
            menu.drawScreen(-1,-1,0);BufferedImage before=titleCrop(capture());advance(2);menu.drawScreen(-1,-1,0);
            require(differences(before,titleCrop(capture()))==0,"Frozen title changes "+flag);
            require(time==UiEffects.time()&&phase==UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.TEXT).phase,"Frozen clock changes "+flag);
            clear();drawHud();BufferedImage hudBefore=capture();advance(2);clear();drawHud();require(differences(hudBefore,capture())==0,"Frozen HUD changes "+flag);
            save(capture(),"freeze-"+flag+"-hud.png");
            clear();MainMenu.drawBackdrop(Theme.getDefaultTheme());BufferedImage backdrop=capture();
            advance(2);clear();MainMenu.drawBackdrop(Theme.getDefaultTheme());require(differences(backdrop,capture())==0,"Frozen backdrop changes "+flag);
        }
        settings.animations.setValue(true);settings.reducedMotion.setValue(false);settings.ambientMotion.setValue(true);
        float phase=UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.TEXT).phase;
        clear();drawHud();BufferedImage before=capture();TextEffects.releaseResources();ArrayListSurface.releaseResources();MainMenu.releaseResources();
        clear();drawHud();require(differences(before,capture())==0,"Shader reinitialization changed HUD phase/image");
        // Consumers restore the actual target/program and clipping, even when entered with a clip.
        glEnable(GL_SCISSOR_TEST);glScissor(0,0,960,640);glEnable(GL_DEPTH_TEST);
        int target=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),program=glGetInteger(GL20.GL_CURRENT_PROGRAM);
        drawHud();require(glIsEnabled(GL_SCISSOR_TEST)&&glIsEnabled(GL_DEPTH_TEST),"HUD leaked clipping/depth state");
        require(target==glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)&&program==glGetInteger(GL20.GL_CURRENT_PROGRAM),"HUD leaked target/program");
        glDisable(GL_SCISSOR_TEST);glDisable(GL_DEPTH_TEST);
        require(phase==UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.TEXT).phase,"Reload reset phase");
        mc.displayWidth=800;mc.displayHeight=600;clear();drawHud();mc.displayWidth=960;mc.displayHeight=640;clear();drawHud();
        require(phase==UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.TEXT).phase,"Resize reset phase");
        System.out.println("PASS: Ambient/Reduced/Animations freeze actual title and HUD; shader release/recreate and buffer resize preserve canonical phase");
    }
    private static void semanticShots() throws Exception {
        settings.border.setValue(false);settings.glow.setValue(false);settings.animations.setValue(false);settings.notificationDesign.setValue("Dark");
        Theme custom=Theme.custom("custom:00000000-0000-0000-0000-000000000043","Saturated notification fixture",Arrays.asList(Color.MAGENTA,Color.GREEN),false,true,.3f,new Color(10,12,18),Color.WHITE,Color.MAGENTA,.7f,9,.8f,1);
        me.youngjibbit.theme.ThemeCatalog.global().preview(custom);
        Method icon=NotificationManager.class.getDeclaredMethod("icon",NotificationType.class,float.class,float.class,float.class);icon.setAccessible(true);
        NotificationType[] types={NotificationType.WARNING,NotificationType.ERROR,NotificationType.SUCCESS,NotificationType.INFO};
        for(boolean priority:new boolean[]{true,false})for(boolean reduced:new boolean[]{true,false}) {
            settings.semanticColorPriority.setValue(priority);settings.reducedMotion.setValue(reduced);clear();
            java.util.List<BufferedImage> shapes=new ArrayList<>();
            try(RenderState state=new RenderState()) {
                state.screenSpace();
                for(int i=0;i<types.length;i++) {
                    icon.invoke(null,types[i],50f+i*80,60f,1f);
                    BufferedImage image=capture();BufferedImage crop=image.getSubimage((50+i*80)*2-18,120-18,36,36);require(lit(crop)>40,"Missing semantic icon");
                    shapes.add(crop);
                    Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Regular 18").drawString(types[i].name(),35+i*80,80,-1);
                }
            }
            for(int a=0;a<4;a++)for(int b=a+1;b<4;b++)require(maskDifferences(shapes.get(a),shapes.get(b))>10,"Semantic geometry collapsed in grayscale");
            save(capture(),"semantic-icons-"+priority+"-reduced-"+reduced+".png");
            NotificationManager.releaseResources();settings.notificationVisible.setValue(4.0);settings.notificationWidth.setValue(260.0);
            for(NotificationType type:types)NotificationManager.show(new Notification(type,type.name(),"Original status details remain readable",5));
            // Scale 1 permits all four cards under the unchanged viewport-dependent visible limit.
            mc.gameSettings.guiScale=1;
            try {
                clear();NotificationManager.render();
                Object timeline=field(NotificationManager.class,"timeline").get(null);
                require(((java.util.List<?>)field(timeline.getClass(),"visible").get(timeline)).size()==4,"All four real status cards must be visible");
                save(capture(),"notification-cards-"+priority+"-reduced-"+reduced+".png");
            } finally {mc.gameSettings.guiScale=2;}
        }
        NotificationManager.releaseResources();me.youngjibbit.theme.ThemeCatalog.global().cancelPreview();
        settings.semanticColorPriority.setValue(true);settings.reducedMotion.setValue(false);settings.animations.setValue(true);settings.border.setValue(true);
        System.out.println("PASS: actual NotificationManager icon geometries differ in grayscale with saturated custom theme, priority ON/OFF, Reduced ON/OFF, Border/Glow OFF; real card images exported");
    }
    private static void fallbackShots() throws Exception {
        boolean shaders=OpenGlHelper.shadersSupported,fbo=mc.gameSettings.fboEnable;
        settings.animations.setValue(false);ArrayListSurface surface=new ArrayListSurface();
        try {
            for(String disabled:new String[]{"NoShader","NoFBO"}) {
                clear();OpenGlHelper.shadersSupported=!disabled.equals("NoShader")&&shaders;mc.gameSettings.fboEnable=!disabled.equals("NoFBO")&&fbo;
                long creates=RenderDiagnostics.framebufferCreates;
                surface.panel(24,30,200,70,6,Theme.Cotton_candy,true,true,true,5,.9f,.8f,()->TextEffects.direct(Theme.Cotton_candy,()->Pulsar.INSTANCE.fontManager.getFont("DmSansMedium 25").drawString("Readable fallback",34,48,-1)));
                BufferedImage image=capture();require(lit(image.getSubimage(48,60,400,140))>200,"Fallback missing");
                require(RenderDiagnostics.framebufferCreates==creates,"Fallback allocates FBO");require(glGetError()==GL_NO_ERROR,"Fallback GL error");save(image,"fallback-"+disabled+".png");
                OpenGlHelper.shadersSupported=shaders;mc.gameSettings.fboEnable=fbo;
            }
        } finally {OpenGlHelper.shadersSupported=shaders;mc.gameSettings.fboEnable=fbo;surface.close();settings.animations.setValue(true);}
        System.out.println("PASS: explicit shadersSupported=false / fboEnable=false fallback, real readable glyph/contour pixels, zero new FBOs, GL clean");
    }
    private static void performanceAndLifetime() throws Exception {
        settings.blur.setValue(false);settings.glow.setValue(false);settings.animations.setValue(false);
        for(String method:METHODS) {
            settings.gradientMethod.setValue(method);for(int i=0;i<5;i++){clear();drawHud();}glFinish();
            long creates=RenderDiagnostics.framebufferCreates,start=System.nanoTime();
            for(int i=0;i<30;i++){clear();drawHud();}glFinish();
            require(RenderDiagnostics.framebufferCreates==creates,"Steady HUD allocated FBOs");
            System.out.println("OFFSCREEN HUD "+method+" 30 frames mean ms="+(System.nanoTime()-start)/30e6+", steady FBO allocations=0");
        }
        settings.animations.setValue(true);
    }
    private static void drawHud() throws Exception {Method draw=ArrayListHUDModule.class.getDeclaredMethod("renderArrayList");draw.setAccessible(true);draw.invoke(hud);}
    private static void advance(double seconds){int count=(int)Math.ceil(seconds/.05);for(int i=0;i<count;i++){clock.addAndGet(Math.round(seconds/count*1e9));UiEffects.beginFrame();}}
    private static BufferedImage titleCrop(BufferedImage image) throws Exception {
        float x=number(menu,"panelX"),y=number(menu,"panelY"),w=number(menu,"panelWidth");TTFFontRenderer font=Pulsar.INSTANCE.fontManager.getFont("DmSansMedium 44");
        return image.getSubimage((int)((x+(w-font.getWidth(Pulsar.INSTANCE.getName()))/2)*2),(int)((y+9)*2),(int)(font.getWidth(Pulsar.INSTANCE.getName())*2),(int)(font.getHeight(Pulsar.INSTANCE.getName())*2));
    }
    private static void verifyTitle(BufferedImage image) throws Exception {
        BufferedImage crop=titleCrop(image);float x=number(menu,"panelX"),y=number(menu,"panelY"),w=number(menu,"panelWidth");TTFFontRenderer font=Pulsar.INSTANCE.fontManager.getFont("DmSansMedium 44");
        int ox=(int)((x+(w-font.getWidth(Pulsar.INSTANCE.getName()))/2)*2),oy=(int)((y+9)*2);Theme theme=Theme.getDefaultTheme();
        Color a=theme.getPrimaryGradientColor(),b=theme.getSecondaryGradientColor();double[] first={a.getRed(),a.getGreen(),a.getBlue()},delta={b.getRed()-a.getRed(),b.getGreen()-a.getGreen(),b.getBlue()-a.getBlue()};
        double norm=0,error=0;int count=0;for(double d:delta)norm+=d*d;
        for(int py=0;py<crop.getHeight();py++)for(int px=0;px<crop.getWidth();px++) {
            int rgb=crop.getRGB(px,py);double[] value={rgb>>16&255,rgb>>8&255,rgb&255};double t=0,residual=0;
            for(int k=0;k<3;k++)t+=(value[k]-first[k])*delta[k];t/=norm;
            for(int k=0;k<3;k++)residual+=Math.pow(value[k]-first[k]-t*delta[k],2);
            if(residual>9||t<0||t>1)continue;
            int expected=UiEffects.gradientColor(theme,UiEffects.Channel.TEXT,(ox+px+.5f)/2,(oy+py+.5f)/2);
            for(int k=0;k<3;k++)error+=Math.abs(value[k]-(expected>>(16-k*8)&255));count++;
        }
        require(count>150&&error/(count*3)<2,"Real title/CPU parity count="+count+" mean error="+error/Math.max(1,count*3));
    }
    private static void clear(){mc.getFramebuffer().bindFramebuffer(true);try(RenderState state=new RenderState()){state.screenSpace();glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);glClearColor(.018f,.022f,.035f,1);glClear(GL_COLOR_BUFFER_BIT);}}
    private static BufferedImage capture(){glFinish();ByteBuffer pixels=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int i=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-y-1,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}return image;}
    private static int differences(BufferedImage a,BufferedImage b){int n=0;require(a.getWidth()==b.getWidth()&&a.getHeight()==b.getHeight(),"Image dimensions");for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(a.getRGB(x,y)!=b.getRGB(x,y))n++;return n;}
    private static int maskDifferences(BufferedImage a,BufferedImage b){int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(bright(a.getRGB(x,y))!=bright(b.getRGB(x,y)))n++;return n;}
    private static int lit(BufferedImage a){int n=0;for(int y=0;y<a.getHeight();y++)for(int x=0;x<a.getWidth();x++)if(bright(a.getRGB(x,y)))n++;return n;}
    private static boolean bright(int rgb){return Math.max(rgb>>16&255,Math.max(rgb>>8&255,rgb&255))>60;}
    private static void save(BufferedImage image,String name) throws Exception {ImageIO.write(image,"png",new File(OUT,name));}
    private static Field field(Class<?> c,String name) throws Exception {for(;c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    private static void set(Object target,String name,Object value) throws Exception {field(target.getClass(),name).set(target,value);}
    private static float number(Object target,String name) throws Exception {return field(target.getClass(),name).getFloat(target);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
