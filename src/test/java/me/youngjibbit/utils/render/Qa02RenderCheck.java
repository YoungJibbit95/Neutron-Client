package me.youngjibbit.utils.render;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.*;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.notifications.*;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.*;
import me.youngjibbit.value.impl.ComboValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.lang.reflect.*;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.lwjgl.opengl.GL11.*;

/** Same fixture runs BEFORE product edits and after them. Real production draw paths and readbacks. */
public final class Qa02RenderCheck {
    static Minecraft mc;static ClientSettings c;static final AtomicLong clock=new AtomicLong();
    static final File OUT=new File("images");static final List<String> violations=new ArrayList<>();
    static final List<Map<String,Object>> evidence=new ArrayList<>();
    static final List<Map<String,Object>> shots=new ArrayList<>(),statusEvidence=new ArrayList<>();
    static final String[] THEMES={"Follow Global","Obsidian","Aurora","Neon Edge","Minimal"};
    static class Settings extends SettingsMenu {
        ClipRect box(int id){return actionBounds(id);}void click(int id)throws Exception{ClipRect r=box(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);}
        void release(){mouseReleased(-1,-1,0);}int actions;@Override protected void activate(int id){actions++;}
    }
    static class Editor extends ThemeEditorScreen {
        Editor(ThemeCatalog catalog){super(null,catalog,s->{});}ClipRect box(int id){return actionBounds(id);}
        void click(int id)throws Exception{ClipRect r=box(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);}void release(){mouseReleased(-1,-1,0);}
        @Override protected void leave(){}
    }
    static class Panel extends ClickGui {
        int themes;@Override protected void openThemeEditor(){themes++;}ClipRect box(){return themeBounds();}
        void click(int x,int y)throws Exception{mouseClicked(x,y,0);}void release(){mouseReleased(-1,-1,0);}
    }
    static class IOS extends iOS {void click(int x,int y)throws Exception{mouseClicked(x,y,0);}void release(){mouseReleased(-1,-1,0);}}
    public static void main(String[] args)throws Exception {
        OUT.mkdirs();File data=new File("fixture");data.mkdirs();System.setProperty("user.home",new File(data,"home").getAbsolutePath());
        try(PbufferTestContext context=new PbufferTestContext()) {
            mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(new Session("QA02Fixture","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),new GameConfiguration.DisplayInformation(960,640,false,false),new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),new GameConfiguration.GameInformation(false,"QA02 Pbuffer"),new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(new IMetadataSerializer());resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc,"mcResourceManager",resources);set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));set(mc,"framebufferMc",new Framebuffer(960,640,true));
            c=new ClientSettings();set(Pulsar.INSTANCE,"mm",new ModuleManager(Arrays.asList(c,new me.youngjibbit.gui.Qa02ScreenMotionTest.Dummy())));
            Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
            Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"Product-Sans-Regular 18","Product-Sans-Bold 20","Product-Sans-Bold 28","Poppins-Regular 18","Poppins-Medium 18","Poppins-Regular 20","icon1 23","SFProText-Bold 20","icon1 19","KhmerOureang 19","icon 23","Poppins-Medium 20","Product-Sans-Medium 20","SFProText-Medium 22","SFProText-Medium 17","Poppins-Regular 19","Poppins-Regular 21"});
            set(mc,"renderEngine",new TextureManager(resources));mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(resources);
            Theme.setDefaultTheme(Theme.SynthWave);c.surface.setValue("Standard");c.blur.setValue(false);c.glow.setValue(false);c.ambientMotion.setValue(false);c.animations.setValue(false);
            UiEffects.timeSource(clock::get);UiEffects.invalidate();UiEffects.beginFrame();
            System.out.println("GPU "+glGetString(GL_VENDOR)+" / "+glGetString(GL_RENDERER)+" / "+glGetString(GL_VERSION));
            if(Boolean.getBoolean("qa02.edgeOnly")){borderColorPolicy();}
            else if(Boolean.getBoolean("qa02.baseline")){for(String design:c.notificationDesign.getOptions())card("Follow Global",design,NotificationType.INFO,"baseline-"+slug(design));}
            else {themeMatrix();statusMatrix();borderColorPolicy();liveDecoration();}
            if(!Boolean.getBoolean("qa02.edgeOnly"))motionScreens();
            NotificationManager.releaseResources();TextEffects.releaseResources();ArrayListSurface.releaseResources();me.youngjibbit.gui.MainMenu.releaseResources();UIUtils.releaseResources();GuiShaderBloom.releaseResources();me.youngjibbit.utils.render.shader.blur.GaussianBlur.releaseResources();
            if(!Boolean.getBoolean("qa02.baseline"))resourcesAndFallback();
            mc.getFramebuffer().deleteFramebuffer();context.check();check(UiEffects.scene()==null,"Scene stack leaked");check(glGetError()==GL_NO_ERROR,"Final GL error");
            try(Writer w=new FileWriter("pixel-evidence.json")){new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(evidence,w);}
            try(Writer w=new FileWriter("violations.json")){new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(violations,w);}
            try(Writer w=new FileWriter("screenshot-settings.json")){new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(shots,w);}
            try(Writer w=new FileWriter("status-matrix.json")){new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(statusEvidence,w);}
            if(!violations.isEmpty())throw new AssertionError(violations.toString());
        }
        System.out.println("PASS QA02 Pbuffer. Display/world/shaderpack/144FPS NOT_RUN");System.exit(0);
    }
    static String slug(String s){return s.toLowerCase(java.util.Locale.ROOT).replace(' ','-');}
    static void check(boolean ok,String reason){if(!ok){violations.add(reason);System.out.println("VIOLATION "+reason);}}
    static Object get(Object target,String name)throws Exception{for(Class<?> t=target.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);return f.get(target);}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static void set(Object target,String name,Object value)throws Exception{for(Class<?> t=target.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
    static void clear(){mc.getFramebuffer().bindFramebuffer(true);glClearColor(.025f,.035f,.055f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glViewport(0,0,mc.displayWidth,mc.displayHeight);}
    static BufferedImage capture(){int w=mc.displayWidth,h=mc.displayHeight;ByteBuffer bytes=BufferUtils.createByteBuffer(w*h*4);glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,bytes);BufferedImage out=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=(y*w+x)*4;out.setRGB(x,h-1-y,0xff000000|(bytes.get(p)&255)<<16|(bytes.get(p+1)&255)<<8|(bytes.get(p+2)&255));}return out;}
    static void save(BufferedImage image,String name)throws Exception{
        ImageIO.write(image,"png",new File(OUT,name+".png"));Map<String,Object> row=new LinkedHashMap<>();
        row.put("path","images/"+name+".png");row.put("width",image.getWidth());row.put("height",image.getHeight());row.put("guiScale",new ScaledResolution(mc).getScaleFactor());row.put("dpi","Pbuffer; no physical display DPI");row.put("globalTheme",Theme.getDefaultTheme().name());row.put("motionPreset",c.motionIntensity.getValue());row.put("notificationTheme",c.getValueByName("notification_theme")==null?"Follow Global":c.getValueByName("notification_theme").getValue());row.put("design",c.notificationDesign.getValue());row.put("border",c.border.getValue());row.put("glow",c.glow.getValue());row.put("semanticPriority",c.semanticColorPriority.getValue());shots.add(row);
    }
    static int diff(BufferedImage a,BufferedImage b,int x,int y,int w,int h){int n=0;for(int py=y;py<y+h;py++)for(int px=x;px<x+w;px++)if(a.getRGB(px,py)!=b.getRGB(px,py))n++;return n;}
    static void record(String label,int pixels){Map<String,Object> row=new LinkedHashMap<>();row.put("label",label);row.put("changedPixels",pixels);row.put("phase",UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.BORDER).phase);evidence.add(row);System.out.println(label+" pixels="+pixels);}
    static void select(String name){me.youngjibbit.value.Value<?> value=c.getValueByName("notification_theme");ComboValue v=(ComboValue)value;if(v!=null)v.setValue(name);else check(name.equals("Follow Global"),"Notification Theme absent");}
    static void seed(NotificationType type)throws Exception {
        NotificationManager.releaseResources();NotificationManager.show(new Notification(type,"Synthetic status","Readable detail / long Unicode: Grüße ✓",15));
        NotificationManager.update();Object timeline=getStatic(NotificationManager.class,"timeline");List<?> entries=(List<?>)get(timeline,"visible");
        for(Object e:entries){set(e,"alpha",1f);set(e,"started",1_000_000_000L);set(e,"expires",16_000_000_000L);}set(timeline,"now",2_000_000_000L);set(timeline,"dt",0f);
    }
    static Object getStatic(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    static BufferedImage card(String theme,String design,NotificationType type,String name)throws Exception {
        select(theme);c.notificationDesign.setValue(design);seed(type);clear();
        Method draw=NotificationManager.class.getDeclaredMethod("drawCards",int.class,ClientSettings.class,ScaledResolution.class,String.class,String.class);draw.setAccessible(true);draw.invoke(null,1,c,new ScaledResolution(mc),c.notificationPosition.getValue(),design);
        Object timeline=getStatic(NotificationManager.class,"timeline");Object e=((List<?>)get(timeline,"visible")).get(0);Object layout=get(e,"layout");float h=(float)get(layout,"height"),y=(float)get(e,"y");ScaledResolution sr=new ScaledResolution(mc);check(y>=8&&y+h<=sr.getScaledHeight()-8,"Card outside resized height "+name);
        BufferedImage shot=capture();save(shot,name);return shot;
    }
    static void themeMatrix()throws Exception {
        for(String design:c.notificationDesign.getOptions()) {
            List<BufferedImage> shots=new ArrayList<>();
            for(String theme:THEMES){BufferedImage shot=card(theme,design,NotificationType.INFO,"theme-"+slug(theme)+"-"+slug(design));shots.add(shot);}
            for(int i=0;i<shots.size();i++)for(int j=0;j<i;j++){int n=diff(shots.get(i),shots.get(j),0,0,mc.displayWidth,mc.displayHeight);record(design+" "+THEMES[i]+" != "+THEMES[j],n);check(n>20,"Theme presets indistinguishable "+design+" "+THEMES[i]+"/"+THEMES[j]);}
            String base=System.getProperty("qa02.baseline.dir");if(base!=null){BufferedImage prior=ImageIO.read(new File(base,"images/baseline-"+slug(design)+".png"));int n=diff(prior,shots.get(0),0,0,mc.displayWidth,mc.displayHeight);record("Follow Global baseline "+design,n);check(n==0,"Follow Global differs from baseline "+design+": "+n);}
        }
        for(String position:c.notificationPosition.getOptions())for(int[] size:new int[][]{{960,640,2},{320,224,1}}){resize(size);c.notificationPosition.setValue(position);card("Minimal","Dark",NotificationType.WARNING,"position-"+slug(position)+"-"+size[0]);}
        resize(new int[]{960,640,2});c.notificationPosition.reset();
    }
    static void statusMatrix()throws Exception {
        c.border.setValue(false);c.glow.setValue(false);
        Theme original=Theme.getDefaultTheme();Theme custom=Theme.custom("custom:00000000-0000-0000-0000-000000000002","Dark low contrast fixture",Arrays.asList(new java.awt.Color(15,15,17),new java.awt.Color(22,22,26)),false,false,.18f,new java.awt.Color(8,8,10),new java.awt.Color(15,15,17),new java.awt.Color(20,20,22),.5f,5,.5f,1);
        ThemeCatalog.global().preview(custom);check(Theme.getDefaultTheme()==custom&&Theme.getDefaultTheme().getPrimaryGradientColor().getRed()==15,"Custom fixture must be the actual active palette, not a missing-ID fallback");
        for(boolean priority:new boolean[]{true,false}){
            c.semanticColorPriority.setValue(priority);Map<NotificationType,String> masks=new EnumMap<>(NotificationType.class);
            for(String theme:THEMES)for(NotificationType type:new NotificationType[]{NotificationType.ERROR,NotificationType.WARNING,NotificationType.SUCCESS,NotificationType.INFO}){
                BufferedImage shot=card(theme,"Dark",type,"status-"+slug(theme)+"-"+type+"-"+priority);Method color=NotificationManager.class.getDeclaredMethod("iconColor",NotificationType.class);color.setAccessible(true);int rgb=(int)color.invoke(null,type);StringBuilder mask=new StringBuilder();int ink=0;
                for(int y=537;y<569;y++)for(int x=438;x<470;x++){int pixel=shot.getRGB(x,y);boolean hit=true;for(int shift:new int[]{0,8,16})if(Math.abs((pixel>>shift&255)-(rgb>>shift&255))>5)hit=false;if(hit)ink++;mask.append(hit?'1':'0');}
                check(ink>12,"Status symbol absent "+theme+" "+type);if(masks.containsKey(type))check(masks.get(type).equals(mask.toString()),"Theme altered semantic glyph "+theme+" "+type);else masks.put(type,mask.toString());
                Map<String,Object> row=new LinkedHashMap<>();row.put("theme",theme);row.put("type",type.name());row.put("priority",priority);row.put("iconRGB",Integer.toHexString(rgb));row.put("opaqueInkPixels",ink);statusEvidence.add(row);
                if(type==NotificationType.ERROR)check(rgb==0xffff667f,"Critical error changed");
            }
            check(new HashSet<>(masks.values()).size()==4,"Status masks are not distinct");
        }
        Theme.setDefaultTheme(original);
        c.border.setValue(true);c.semanticColorPriority.reset();
    }
    static void advance(double seconds){int n=Math.max(1,(int)Math.ceil(seconds/.025));for(int i=0;i<n;i++){clock.addAndGet(Math.round(seconds/n*1e9));UiEffects.beginFrame();}}
    static void borderColorPolicy()throws Exception {
        c.borderColor.setValue("Accent");BufferedImage bare=card("Follow Global","Dark",NotificationType.INFO,"border-accent-follow");BufferedImage neon=card("Neon Edge","Dark",NotificationType.INFO,"border-accent-neon");
        int ink=Theme.getDefaultTheme().getAccentColor().getRGB(),background=bare.getRGB(444,530),actual=neon.getRGB(444,530);float opacity=c.borderOpacity.getValue().floatValue();
        for(int shift:new int[]{0,8,16}){int expected=Math.round((ink>>shift&255)*opacity+(background>>shift&255)*(1-opacity));check(Math.abs((actual>>shift&255)-expected)<=3,"Neon ignored existing Accent border policy");}
        c.border.setValue(false);bare=card("Follow Global","Dark",NotificationType.INFO,"border-off-follow");neon=card("Neon Edge","Dark",NotificationType.INFO,"border-off-neon");int n=diff(bare,neon,0,0,mc.displayWidth,mc.displayHeight);record("Neon Border OFF vs Follow Global",n);check(n==0,"Neon escaped global Border OFF");c.border.setValue(true);c.borderColor.reset();
        c.borderOpacity.setValue(0.0);bare=card("Obsidian","Dark",NotificationType.INFO,"obsidian-opacity-zero-border-on");c.border.setValue(false);neon=card("Obsidian","Dark",NotificationType.INFO,"obsidian-opacity-zero-border-off");n=diff(bare,neon,0,0,mc.displayWidth,mc.displayHeight);record("Obsidian Border Opacity zero",n);check(n==0,"Obsidian ignored global Border Opacity zero");c.border.setValue(true);c.borderOpacity.reset();
    }
    static BufferedImage live(String name)throws Exception {
        NotificationManager.releaseResources();NotificationManager.show(new Notification(NotificationType.INFO,"Synthetic status","Global live decoration",15));
        c.animations.setValue(false);NotificationManager.update();c.animations.setValue(true);clear();UiEffects.beginFrame();NotificationManager.render();BufferedImage s=capture();save(s,name);return s;
    }
    static void liveDecoration()throws Exception {
        select("Aurora");c.gradientMotion.setValue("Animated");c.gradientSpeed.setValue(.25);c.direction.setValue("Horizontal");c.ambientMotion.setValue(true);c.animations.setValue(true);c.surface.setValue("Standard");c.notificationDesign.setValue("Dark");
        for(String method:new String[]{"Flow","Ping-Pong","Pulse"}){
            c.gradientMethod.setValue(method);Theme.setDefaultTheme(Theme.SynthWave);BufferedImage a=live("live-"+method+"-a");Theme.setDefaultTheme(Theme.Bonfire);BufferedImage b=live("live-"+method+"-b");Theme.setDefaultTheme(Theme.SynthWave);BufferedImage back=live("live-"+method+"-back");
            int n=diff(a,b,434,520,506,40),same=diff(a,back,434,520,506,40);record(method+" A-B",n);record(method+" A-B-A",same);check(n>20&&same==0,"Live theme A-B-A "+method);
            advance(.6);BufferedImage phase=live("live-"+method+"-phase");n=diff(back,phase,434,520,506,40);record(method+" phase",n);check(n>10,"Global phase invisible "+method);
            for(double speed:new double[]{.1,.8}){c.gradientSpeed.setValue(speed);UiEffects.timeSource(clock::get);UiEffects.beginFrame();BufferedImage start=live("rate-"+method+"-"+speed+"-start");advance(.6);BufferedImage end=live("rate-"+method+"-"+speed+"-end");float p=UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.BORDER).phase;check(Math.abs(p-.6*speed)<.00001,"Speed transport "+method);n=diff(start,end,434,520,506,40);record(method+" speed="+speed,n);check(n>10,"Rate invisible "+method);}
        }
        c.ambientMotion.setValue(false);c.animations.setValue(false);c.gradientMotion.setValue("Static");
    }
    static void resize(int[] size){mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=size[2];mc.getFramebuffer().createBindFramebuffer(size[0],size[1]);}
    static BufferedImage screen(GuiScreen screen,int mx,int my,String name)throws Exception{clear();screen.drawScreen(mx,my,0);BufferedImage result=capture();save(result,name);return result;}
    static void step(GuiScreen screen,float dt,int x,int y)throws Exception {
        try{Method m=screen.getClass().getSuperclass().getDeclaredMethod("advanceMotion",float.class,int.class,int.class);m.setAccessible(true);m.invoke(screen,dt,x,y);}catch(NoSuchMethodException ignored){}
    }
    static void motionScreens()throws Exception {
        c.animations.setValue(false);c.ambientMotion.setValue(false);c.gradientMotion.setValue("Static");
        Settings s=new Settings();s.setWorldAndResolution(mc,480,320);ClipRect b=s.box(2);int mx=(int)(b.x+b.width/2),my=(int)(b.y+b.height/2);
        c.motionIntensity.setValue("Calm");step(s,.12f,mx,my);BufferedImage calm=screen(s,mx,my,"settings-calm");c.motionIntensity.setValue("Expressive");step(s,.12f,mx,my);BufferedImage expressive=screen(s,mx,my,"settings-expressive");int n=diff(calm,expressive,(int)b.x*2,(int)b.y*2,(int)b.width*2,(int)b.height*2);record("Settings preset",n);check(n>5,"Settings preset has no pixel consumer");
        s.click(2);step(s,0,mx,my);screen(s,mx,my,"settings-pressed");check(s.actions==1,"Settings dispatch changed");s.release();check(s.actions==1,"Settings release dispatch duplicated");s.onGuiClosed();
        Editor e=new Editor(new ThemeCatalog(null));e.setWorldAndResolution(mc,480,320);b=e.box(4);mx=(int)(b.x+b.width/2);my=(int)(b.y+b.height/2);
        c.motionIntensity.setValue("Calm");step(e,.12f,mx,my);calm=screen(e,mx,my,"editor-calm");c.motionIntensity.setValue("Expressive");step(e,.12f,mx,my);expressive=screen(e,mx,my,"editor-expressive");n=diff(calm,expressive,(int)b.x*2,(int)b.y*2,(int)b.width*2,(int)b.height*2);record("Editor preset",n);check(n>5,"Editor preset has no pixel consumer");
        if(!Boolean.getBoolean("qa02.baseline")){e.click(6);check(!((ThemeDraft)get(e,"draft")).isEditable(),"Disabled Delete acted");e.click(4);e.release();}
        e.onGuiClosed();if(!Boolean.getBoolean("qa02.baseline"))for(me.youngjibbit.gui.UiSpring state:(me.youngjibbit.gui.UiSpring[])get(e,"controlMotion"))check(state.amount()==0&&!state.pressed(),"Editor close retained visual state");
        if(!Boolean.getBoolean("qa02.baseline")){
            Panel p=new Panel();p.setWorldAndResolution(mc,480,320);b=p.box();mx=(int)(b.x+b.width/2);my=(int)(b.y+b.height/2);
            c.motionIntensity.setValue("Calm");step(p,.12f,mx,my);calm=screen(p,mx,my,"panel-calm");c.motionIntensity.setValue("Expressive");step(p,.12f,mx,my);expressive=screen(p,mx,my,"panel-expressive");n=diff(calm,expressive,(int)b.x*2,(int)b.y*2,(int)b.width*2,(int)b.height*2);record("Panel preset",n);check(n>5,"Panel preset has no pixel consumer");
            p.click(mx,my);p.release();p.release();check(p.themes==1,"Native Panel route duplicated");p.click((int)Math.ceil(b.x+b.width),my);p.release();check(p.themes==1,"Panel expanded hitbox");
            List<?> rows=(List<?>)get(p,"modules");me.youngjibbit.clickgui.component.impl.ClickModule row=(me.youngjibbit.clickgui.component.impl.ClickModule)rows.get(0);row.setOpen(true);row.updateAnimation();screen(p,-1,-1,"panel-values-layout");
            for(me.youngjibbit.clickgui.component.Component.Sub<?> value:row.presentationValues()){
                ClipRect v=value.presentationBounds();if(value.getClip().intersect(v).isEmpty())continue;int vx=(int)(v.x+v.width/2),vy=(int)(v.y+v.height/2);c.motionIntensity.setValue("Calm");step(p,.12f,vx,vy);BufferedImage a=screen(p,vx,vy,"panel-value-calm-"+value.getClass().getSimpleName());c.motionIntensity.setValue("Expressive");step(p,.12f,vx,vy);BufferedImage z=screen(p,vx,vy,"panel-value-expressive-"+value.getClass().getSimpleName());int pixels=diff(a,z,Math.max(0,(int)v.x*2),Math.max(0,(int)v.y*2),(int)v.width*2,(int)v.height*2);record("Panel "+value.getClass().getSimpleName(),pixels);check(pixels>2,"Value motion invisible "+value.getClass());
            }p.onGuiClosed();for(me.youngjibbit.gui.UiSpring state:(me.youngjibbit.gui.UiSpring[])get(p,"chromeMotion"))check(state.amount()==0&&!state.pressed(),"Panel close retained visual state");
            IOS ios=new IOS();set(Pulsar.INSTANCE,"iOS",ios);ios.setWorldAndResolution(mc,480,320);((List<?>)get(ios,"particles")).clear();screen(ios,-1,-1,"ios-warm");screen(ios,-1,-1,"ios-idle");float ix=(float)get(ios,"x"),iy=(float)get(ios,"y");mx=(int)ix+45;my=(int)iy+50;
            c.motionIntensity.setValue("Calm");step(ios,.12f,mx,my);calm=screen(ios,mx,my,"ios-calm");c.motionIntensity.setValue("Expressive");step(ios,.12f,mx,my);expressive=screen(ios,mx,my,"ios-expressive");n=diff(calm,expressive,((int)ix+4)*2,((int)iy+42)*2,172,34);record("iOS category preset",n);check(n>5,"iOS category motion invisible");
            ios.click(mx,my);ios.release();check(get(ios,"selectedCategory")==me.youngjibbit.module.Category.Movement,"Native iOS inverse hit changed");
            resize(new int[]{320,224,1});ios.setWorldAndResolution(mc,320,224);screen(ios,-1,-1,"ios-small-idle");ix=(float)get(ios,"x");iy=(float)get(ios,"y");float iw=(float)get(ios,"width"),ih=(float)get(ios,"height"),scale=(float)get(ios,"renderedScale");
            float lx=ix+45,ly=iy+25+me.youngjibbit.module.Category.Client.ordinal()*17+8;int rawX=Math.round(ix+iw/2+(lx-ix-iw/2)*scale),rawY=Math.round(iy+ih/2+(ly-iy-ih/2)*scale);
            screen(ios,rawX,rawY,"ios-small-hover");ios.click(rawX,rawY);ios.release();check(get(ios,"selectedCategory")==me.youngjibbit.module.Category.Client,"Native fit-scaled iOS category hit changed");ios.onGuiClosed();resize(new int[]{960,640,2});
            Settings focus=new Settings();focus.setWorldAndResolution(mc,480,320);set(focus,"focus",7);c.border.setValue(false);c.glow.setValue(false);step(focus,0,-1,-1);screen(focus,-1,-1,"settings-focus-border-glow-off");focus.onGuiClosed();c.border.setValue(true);
            for(String preset:c.motionIntensity.getOptions()){Settings three=new Settings();three.setWorldAndResolution(mc,480,320);c.motionIntensity.setValue(preset);b=three.box(2);step(three,.12f,(int)b.x+20,(int)b.y+8);screen(three,(int)b.x+20,(int)b.y+8,"preset-"+slug(preset));three.onGuiClosed();}
        }
        c.motionIntensity.reset();
    }
    static void resourcesAndFallback()throws Exception {
        c.animations.setValue(false);select("Neon Edge");c.notificationDesign.setValue("Dark");seed(NotificationType.ERROR);
        RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
        Method draw=NotificationManager.class.getDeclaredMethod("drawCards",int.class,ClientSettings.class,ScaledResolution.class,String.class,String.class);draw.setAccessible(true);
        for(int i=0;i<10;i++){clear();draw.invoke(null,1,c,new ScaledResolution(mc),"Bottom Right","Dark");}
        long warm=RenderDiagnostics.framebufferCreates;for(int i=0;i<10;i++){clear();draw.invoke(null,1,c,new ScaledResolution(mc),"Bottom Right","Dark");}
        check(warm==RenderDiagnostics.framebufferCreates,"Toast steady FBO allocation");
        int blend=glIsEnabled(GL_BLEND)?1:0;java.nio.IntBuffer box=BufferUtils.createIntBuffer(16);glGetInteger(GL_SCISSOR_BOX,box);int scissor=glIsEnabled(GL_SCISSOR_TEST)?1:0;
        try(RenderState state=new RenderState()){state.screenSpace();me.youngjibbit.gui.UiSpring visual=new me.youngjibbit.gui.UiSpring();visual.step(.12f,true,true,true,true,c);me.youngjibbit.clickgui.utils.GuiStyle.feedback(new ClipRect(10,10,80,24),visual);}
        java.nio.IntBuffer after=BufferUtils.createIntBuffer(16);glGetInteger(GL_SCISSOR_BOX,after);check(blend==(glIsEnabled(GL_BLEND)?1:0)&&scissor==(glIsEnabled(GL_SCISSOR_TEST)?1:0),"Feedback GL enable state leaked");for(int i=0;i<4;i++)check(box.get(i)==after.get(i),"Feedback scissor leaked");
        float phase=UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.BORDER).phase;NotificationManager.releaseResources();ArrayListSurface.releaseResources();card("Neon Edge","Dark",NotificationType.ERROR,"recreated-toast");check(phase==UiEffects.gradient(Theme.getDefaultTheme(),UiEffects.Channel.BORDER).phase,"Recreate changed global phase");
        boolean shader=net.minecraft.client.renderer.OpenGlHelper.shadersSupported,fbo=mc.gameSettings.fboEnable;
        try{net.minecraft.client.renderer.OpenGlHelper.shadersSupported=false;card("Minimal","Dark",NotificationType.ERROR,"fallback-no-shader");net.minecraft.client.renderer.OpenGlHelper.shadersSupported=shader;mc.gameSettings.fboEnable=false;card("Minimal","Dark",NotificationType.ERROR,"fallback-no-fbo");}finally{net.minecraft.client.renderer.OpenGlHelper.shadersSupported=shader;mc.gameSettings.fboEnable=fbo;}
        NotificationManager.releaseResources();ArrayListSurface.releaseResources();UIUtils.releaseResources();GuiShaderBloom.releaseResources();
        System.out.println("QA02 RESOURCES "+RenderDiagnostics.snapshot());check(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"Toast FBO unbalanced");RenderDiagnostics.enabled=false;
    }
}
