package me.youngjibbit.module.impl.render;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.Pulsar;
import me.youngjibbit.event.EventFlow;
import me.youngjibbit.event.packet.EventPacket;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.GuiShaderBloom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.renderer.*;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.*;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.util.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import java.net.Proxy;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;

/** Synthetic-clock/data, isolated pbuffer, structured backdrop. Never starts the game/client bootstrap. */
public final class SessionInfoRenderCheck {
    static Minecraft mc;
    static final File OUT=new File("session-preview");
    static final AtomicLong CLOCK=new AtomicLong();
    static SessionStatistics stats;
    static SessionInfo hud;
    static final String NAME="MoblerVomDienst";
    public static void main(String[] args) throws Exception {
        OUT.mkdirs();System.setProperty("user.home",new File(OUT,"home").getAbsolutePath());
        File data=java.nio.file.Files.createTempDirectory(OUT.toPath(),"fixture-").toFile();
        try (PbufferTestContext context=new PbufferTestContext()) {
            mc=new Minecraft(new GameConfiguration(new GameConfiguration.UserInformation(new Session(NAME,"00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                    new GameConfiguration.DisplayInformation(1100,640,false,false),new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                    new GameConfiguration.GameInformation(false,"SessionInfo regression check"),new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);
            IMetadataSerializer metadata=new IMetadataSerializer();
            metadata.registerMetadataSectionType(new TextureMetadataSectionSerializer(),TextureMetadataSection.class);
            metadata.registerMetadataSectionType(new AnimationMetadataSectionSerializer(),AnimationMetadataSection.class);
            metadata.registerMetadataSectionType(new PackMetadataSectionSerializer(),PackMetadataSection.class);
            SimpleReloadableResourceManager resources=new SimpleReloadableResourceManager(metadata);
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc,"mcResourceManager",resources);set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
            set(mc,"framebufferMc",new Framebuffer(mc.displayWidth,mc.displayHeight,true));
            Class<?> fixture=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck");
            Method fonts=fixture.getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
            Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"Poppins-Bold 23","Product-Sans-Bold 20","Product-Sans-Bold 16","icon 22","icon1 22","Product-Sans-Regular 20"});
            if(Boolean.getBoolean("pulsar.theme.sync.check")){ThemeSyncRenderCheck.run();context.check();return;}
            stats=new SessionStatistics(CLOCK::get);stats.observe(new Object(),true);CLOCK.set(3_723_000_000_000L);
            hud=new SessionInfo(stats);hud.getModeSetting("Effects Source").setValue("Local");set(hud,"x",16f);set(hud,"y",20f);
            Theme.setDefaultTheme(Theme.SynthWave);
            checkEvents();checkDesignsAndClock();checkEffects();checkDraggingAndResize();
            if(Boolean.getBoolean("pulsar.effects.check")) EffectConsistencyCheck.run();
            if(Boolean.getBoolean("pulsar.fonts.check")) FontResourcesCheck.run();
            if(Boolean.getBoolean("pulsar.cleanup.check")) RenderUtilsCleanupCheck.run();
            if(Boolean.getBoolean("pulsar.theme.check")) ThemeEditorCheck.run();
            if(Boolean.getBoolean("pulsar.theme.gui.check")) ThemeGuiRenderCheck.run();
            if(Boolean.getBoolean("pulsar.watermark.check")) WatermarkCheck.run();
            if(Boolean.getBoolean("pulsar.armor.check")) ArmorHudCheck.run();
            if(Boolean.getBoolean("pulsar.menu.check")) me.youngjibbit.gui.MainMenuCheck.run(mc);
            if(Boolean.getBoolean("pulsar.settings.check")) ClientSettingsMenuCheck.run();
            if(Boolean.getBoolean("pulsar.global.check")) GlobalEffectsCheck.run();
            if(Boolean.getBoolean("pulsar.notifications.check")) NotificationRenderCheck.run();
            if(Boolean.getBoolean("pulsar.motion.check")) MotionRenderCheck.run();
            if(Boolean.getBoolean("pulsar.hud.check")) IngameHudRenderCheck.run();
            require(glGetError()==GL_NO_ERROR,"OpenGL error");
            if(Boolean.getBoolean("pulsar.nested.check")) NestedSurfaceCheck.run();
            if(Boolean.getBoolean("pulsar.native.check")) NativeSurfaceCheck.run();
            if(Boolean.getBoolean("pulsar.composition.check")) SharedCompositionCheck.run();
            if(Boolean.getBoolean("pulsar.consumers.check")) SubstanceConsumersCheck.run();
            if(Boolean.getBoolean("pulsar.glass.refinement.check")) LiquidGlassRefinementCheck.run();
            if(Boolean.getBoolean("pulsar.resources.check")) SharedResourceCheck.run();
            context.check();
        }
        System.out.println("PASS [Pbuffer/FBO only]: four designs; read-only clock/HUD toggles; confirmed chat/replay/actionbar/disconnect; exact metrics; effect switches/masks; theme; chat-only drag; bounds/simulated resize/GUI scales");
    }

    static SessionStatistics.Snapshot sample() {return new SessionStatistics.Snapshot(3_723_000_000_000L,12345,678,true);}
    static SessionInfo.Frame layout(String username,SessionStatistics.Snapshot data) {
        ScaledResolution sr=new ScaledResolution(mc);return hud.layout(data,username,sr.getScaledWidth(),sr.getScaledHeight());
    }
    static BufferedImage render(String design,String username,SessionStatistics.Snapshot data) {
        hud.getModeSetting("Mode").setValue(design);backdrop();SessionInfo.Frame frame=layout(username,data);
        SessionInfo.Frame frozen=new SessionInfo.Frame(frame.x,frame.y,frame.width,frame.height,frame.radius,frame.design,frame.snapshot,
                frame.theme,frame.background,frame.blur,frame.glow,frame.blurRadius,0.25f);
        frozen.text.addAll(frame.text);frozen.ring=frame.ring;
        GuiShaderBloom.beginBatch();hud.paint(frozen);GuiShaderBloom.flushBatch(2,3);return capture();
    }
    static void checkDesignsAndClock() throws Exception {
        List<BufferedImage> images=new ArrayList<>();List<String> captions=new ArrayList<>();
        long before=stats.snapshot().elapsedNanos;
        for(String design:new String[]{"Basic","Outline","Tenacity","Rue (Rise)"}) {
            hud.getBooleanSetting("Blur").setValue(true);
            images.add(render(design,NAME,sample()));captions.add(design+" / 1h 2m 3s / multi-digit statistics");
            SessionInfo.Frame frame=layout(NAME,sample());
            for(SessionInfo.TextRun text:frame.text) {
                require(Math.abs(text.width-text.font.getWidth(text.text))<0.0001,"Text width mismatch");
                float advance=0;
                for(int i=0;i<text.text.length();i++){require(Math.abs(text.positions[i]-advance)<0.0001,"Glyph spacing mismatch");advance+=text.font.getWidth(String.valueOf(text.text.charAt(i)))-2;}
                require(text.x>=0 && text.x+text.width<=frame.width+0.01,"Text overflow: "+text.text);
            }
            hud.setToggled(true);hud.setToggled(false);
            CLOCK.addAndGet(1_000_000_000L);before+=1_000_000_000L;
            require(stats.snapshot().elapsedNanos==before && hud.getSessionLengthString().equals(stats.snapshot().time()),"Hidden HUD must keep the session running");
        }
        sheet("01-designs.png",captions,images);
        // No render method may reset the timer, even at a fixed clock over many frames.
        for(int i=0;i<20;i++) render("Outline",NAME,stats.snapshot());
        require(stats.snapshot().elapsedNanos==before,"Rendering reset clock");
    }
    static void checkEvents() throws Exception {
        SessionInfoLifecycle tracker=new SessionInfoLifecycle(()->NAME);tracker.install();
        try(me.youngjibbit.core.network.SessionConsumerIntegrationTest.Fixture fixture=new me.youngjibbit.core.network.SessionConsumerIntegrationTest.Fixture()) {
            fixture.join();
            S02PacketChat chat=new S02PacketChat(new ChatComponentText("Victim was slain by "+NAME));
            fixture.network.receivePacket(chat,false);require(tracker.statistics.snapshot().kills==1,"Applied chat not counted");
            fixture.network.receivePacketSilent(chat);require(tracker.statistics.snapshot().kills==1,"Replay counted twice");
            fixture.network.receivePacket(new S02PacketChat(new ChatComponentText("Victim was slain by "+NAME),(byte)2),false);
            require(tracker.statistics.snapshot().kills==1,"Actionbar counted");
            fixture.network.closeChannel(new ChatComponentText("end"));require(!tracker.statistics.snapshot().active,"Disconnect did not end session");
        } finally {Pulsar.BUS.unsubscribe(tracker);}
    }
    static void checkEffects() throws Exception {
        List<BufferedImage> pictures=new ArrayList<>();List<String> captions=new ArrayList<>();
        for(String design:new String[]{"Basic","Outline","Tenacity","Rue (Rise)"}) {
            hud.getBooleanSetting("Background").setValue(false);hud.getBooleanSetting("Blur").setValue(false);hud.getBooleanSetting("Bloom").setValue(false);
            BufferedImage off=render(design,NAME,sample());
            hud.getBooleanSetting("Bloom").setValue(true);BufferedImage glow=render(design,NAME,sample());
            SessionInfo.Frame frame=layout(NAME,sample());int scale=new ScaledResolution(mc).getScaleFactor();
            for(int y=(int)((frame.y+20)*scale);y<(frame.y+frame.height-8)*scale;y++)for(int x=(int)((frame.x+10)*scale);x<(frame.x+frame.width-10)*scale;x++)
                require(off.getRGB(x,y)==glow.getRGB(x,y),"Glow leaked into transparent panel: "+design);
            hud.getBooleanSetting("Bloom").setValue(false);hud.getBooleanSetting("Blur").setValue(true);BufferedImage blurred=render(design,NAME,sample());
            int changed=0;
            for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++) {
                float gx=(x+0.5f)/scale,gy=(y+0.5f)/scale;
                boolean outside=gx<frame.x-2||gx>frame.x+frame.width+2||gy<frame.y-2||gy>frame.y+frame.height+2;
                if(outside)require(off.getRGB(x,y)==blurred.getRGB(x,y),"Blur changed exterior: "+design);
                if(!outside && off.getRGB(x,y)!=blurred.getRGB(x,y))changed++;
            }
            require(changed>100,"Blur switch has no effect: "+design);
            if(design.equals("Outline")) {
                pictures.add(off);captions.add("All effects/background off");pictures.add(glow);captions.add("Exterior glow, transparent interior");pictures.add(blurred);captions.add("Blur only / structured scene");
                hud.getBooleanSetting("Background").setValue(true);pictures.add(render(design,NAME,sample()));captions.add("Background + blur");
                hud.getBooleanSetting("Bloom").setValue(true);pictures.add(render(design,NAME,sample()));captions.add("Background + blur + glow");
                Theme.setDefaultTheme(Theme.Neon);pictures.add(render(design,NAME,sample()));captions.add("Theme changed on existing module");Theme.setDefaultTheme(Theme.SynthWave);
            }
        }
        hud.getModeSetting("Mode").setValue("Outline");hud.getBooleanSetting("Blur").setValue(true);
        hud.getNumberSetting("Blur Strength").setValue(1.0);BufferedImage low=render("Outline",NAME,sample());
        hud.getNumberSetting("Blur Strength").setValue(10.0);BufferedImage high=render("Outline",NAME,sample());
        require(low.getRGB(140,95)!=high.getRGB(140,95),"Blur strength setting ignored");
        hud.getNumberSetting("Blur Strength").setValue(3.0);
        sheet("02-effects.png",captions,pictures);
    }
    static void checkDraggingAndResize() throws Exception {
        hud.getBooleanSetting("Background").setValue(true);hud.getBooleanSetting("Blur").setValue(true);hud.getBooleanSetting("Bloom").setValue(true);
        for(String design:new String[]{"Basic","Outline","Tenacity","Rue (Rise)"}) {
            hud.getModeSetting("Mode").setValue(design);SessionInfo.Frame frame=layout(NAME,sample());
            ScaledResolution sr=new ScaledResolution(mc);float oldX=frame.x,oldY=frame.y;
            hud.interact(false,true,oldX+12,oldY+10,frame,sr.getScaledWidth(),sr.getScaledHeight());
            hud.interact(true,true,oldX+70,oldY+70,frame,sr.getScaledWidth(),sr.getScaledHeight());
            require((float)get(hud,"x")==oldX && (float)get(hud,"y")==oldY,"Gameplay press armed drag");
            hud.interact(true,false,oldX+12,oldY+10,frame,sr.getScaledWidth(),sr.getScaledHeight());
            hud.interact(true,true,oldX+12,oldY+10,frame,sr.getScaledWidth(),sr.getScaledHeight());
            hud.interact(true,true,oldX+42,oldY+40,frame,sr.getScaledWidth(),sr.getScaledHeight());
            require((float)get(hud,"x")==oldX+30 && (float)get(hud,"y")==oldY+30,"Chat drag failed");
            hud.interact(false,true,9999,9999,frame,sr.getScaledWidth(),sr.getScaledHeight());require(!(boolean)get(hud,"dragging"),"Chat close failed to stop drag");
            hud.interact(true,false,0,0,frame,sr.getScaledWidth(),sr.getScaledHeight());
            require(!frame.contains(frame.x-1,frame.y+5)&&!frame.contains(frame.x+frame.width+1,frame.y+5),"Hitbox extends beyond panel");
        }
        hud.setToggled(true);set(hud,"dragging",true);
        Pulsar.BUS.post(new me.youngjibbit.event.packet.TickEvent());
        require(!(boolean)get(hud,"dragging"),"Client tick did not stop dragging after chat close");hud.setToggled(false);
        List<BufferedImage> pictures=new ArrayList<>();List<String> captions=new ArrayList<>();
        for(int scale:new int[]{1,2,3})for(String design:new String[]{"Outline","Tenacity"}) {
            mc.displayWidth=scale==2?854:1101;mc.displayHeight=scale==2?480:733;mc.gameSettings.guiScale=scale;
            mc.getFramebuffer().createBindFramebuffer(mc.displayWidth,mc.displayHeight);
            set(hud,"x",9999f);set(hud,"y",9999f);Theme.setDefaultTheme(Theme.Neon);
            SessionStatistics.Snapshot large=new SessionStatistics.Snapshot(100L*3600*1_000_000_000L,2147483647,1234567890,true);
            pictures.add(render(design,"An_Extraordinarily_Long_Synthetic_Player_Name_123456789",large));captions.add(design+" / long data / GUI scale "+scale);
            SessionInfo.Frame frame=layout("An_Extraordinarily_Long_Synthetic_Player_Name_123456789",large);ScaledResolution sr=new ScaledResolution(mc);
            require(frame.x>=4 && frame.y>=4 && frame.x+frame.width<=sr.getScaledWidth()-4.0f && frame.y+frame.height<=sr.getScaledHeight()-4.0f,"Unreachable resized panel");
            for(SessionInfo.TextRun text:frame.text)require(text.x+text.width<=frame.width+0.01,"Long data overlap");
        }
        sheet("03-long-data-resize.png",captions,pictures);
    }

    static void backdrop() {
        mc.getFramebuffer().bindFramebuffer(true);glDisable(GL_SCISSOR_TEST);glDisable(GL_STENCIL_TEST);glDisable(GL_DEPTH_TEST);GlStateManager.disableDepth();glDisable(GL_ALPHA_TEST);GlStateManager.disableAlpha();glDisable(GL_BLEND);GlStateManager.disableBlend();glColorMask(true,true,true,true);GlStateManager.colorMask(true,true,true,true);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL20.glUseProgram(0);glDisable(GL_TEXTURE_2D);GlStateManager.disableTexture2D();
        ScaledResolution sr=new ScaledResolution(mc);glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);glMatrixMode(GL_MODELVIEW);glLoadIdentity();
        glClearColor(0.12f,0.17f,0.24f,1);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glBegin(GL_QUADS);
        for(int y=0;y<sr.getScaledHeight();y+=5)for(int x=0;x<sr.getScaledWidth();x+=5){boolean light=((x/5+y/5)&1)==0;glColor3f(light?0.28f:0.08f,light?0.34f:0.14f,light?0.43f:0.22f);glVertex2f(x,y);glVertex2f(x,y+5);glVertex2f(x+5,y+5);glVertex2f(x+5,y);}glEnd();glEnable(GL_TEXTURE_2D);GlStateManager.enableTexture2D();glColor4f(1,1,1,1);GlStateManager.color(1,1,1,1);
    }
    static BufferedImage capture(){glFinish();ByteBuffer pixels=BufferUtils.createByteBuffer(mc.displayWidth*mc.displayHeight*4);glReadPixels(0,0,mc.displayWidth,mc.displayHeight,GL_RGBA,GL_UNSIGNED_BYTE,pixels);BufferedImage image=new BufferedImage(mc.displayWidth,mc.displayHeight,BufferedImage.TYPE_INT_RGB);for(int y=0;y<mc.displayHeight;y++)for(int x=0;x<mc.displayWidth;x++){int i=(y*mc.displayWidth+x)*4;image.setRGB(x,mc.displayHeight-y-1,(pixels.get(i)&255)<<16|(pixels.get(i+1)&255)<<8|(pixels.get(i+2)&255));}return image;}
    static void sheet(String name,List<String> captions,List<BufferedImage> images)throws Exception {
        boolean full=name.contains("resize");int w=720,h=full?420:240;
        BufferedImage sheet=new BufferedImage(w*2,(h+28)*((images.size()+1)/2),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=sheet.createGraphics();g.setFont(new Font("SansSerif",Font.PLAIN,15));
        for(int i=0;i<images.size();i++) {
            int x=i%2*w,y=i/2*(h+28);g.setColor(new Color(18,20,25));g.fillRect(x,y,w,h+28);
            g.setColor(Color.WHITE);g.drawString(captions.get(i),x+10,y+20);
            BufferedImage im=images.get(i);
            if(full) {double scale=Math.min((double)w/im.getWidth(),(double)h/im.getHeight());g.drawImage(im,x,y+28,(int)(im.getWidth()*scale),(int)(im.getHeight()*scale),null);}
            else g.drawImage(im,x,y+28,x+w,y+28+h,0,0,w,h,null);
        }
        g.dispose();ImageIO.write(sheet,"png",new File(OUT,name));
    }

    static Field field(Class<?> c,String n)throws Exception{while(c!=null){try{Field f=c.getDeclaredField(n);f.setAccessible(true);return f;}catch(NoSuchFieldException e){c=c.getSuperclass();}}throw new NoSuchFieldException(n);}
    static Object get(Object o,String n)throws Exception{return field(o.getClass(),n).get(o);}static void set(Object o,String n,Object v)throws Exception{field(o.getClass(),n).set(o,v);}static void setStatic(Class<?> c,String n,Object v)throws Exception{field(c,n).set(null,v);}static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
