package me.youngjibbit.gui;

import com.mojang.authlib.properties.PropertyMap;
import me.youngjibbit.rendercheck.PbufferTestContext;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.clickgui.utils.ClipRect;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfiguration;
import net.minecraft.client.resources.*;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.Session;
import java.io.File;
import java.lang.reflect.Field;
import java.net.Proxy;
import java.util.Collections;

/** External working directory only. Existing MainMenuCheck runs unchanged in a real Pbuffer. */
public final class MainMenuVisualCheck {
    public static void main(String[] args) throws Exception {
        File data = new File("fixture"); data.mkdirs();
        System.setProperty("user.home", new File(data,"home").getAbsolutePath());
        try (PbufferTestContext context = new PbufferTestContext()) {
            Minecraft mc = new Minecraft(new GameConfiguration(
                new GameConfiguration.UserInformation(new Session("VISFixture","00000000000000000000000000000000","0","legacy"),new PropertyMap(),new PropertyMap(),Proxy.NO_PROXY),
                new GameConfiguration.DisplayInformation(960,640,false,false),
                new GameConfiguration.FolderInformation(data,new File(data,"packs"),new File(data,"assets"),null),
                new GameConfiguration.GameInformation(false,"VIS-01 offscreen"),new GameConfiguration.ServerInformation(null,0)));
            context.initialize(mc,data);
            System.out.println("GL: "+org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VENDOR)+" / "+org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_RENDERER)+" / "+org.lwjgl.opengl.GL11.glGetString(org.lwjgl.opengl.GL11.GL_VERSION));
            SimpleReloadableResourceManager resources = new SimpleReloadableResourceManager(new IMetadataSerializer());
            resources.reloadResources(Collections.singletonList(new DefaultResourcePack(Collections.emptyMap())));
            set(mc,"mcResourceManager",resources);
            set(mc,"mcLanguageManager",new LanguageManager(new IMetadataSerializer(),"en_US"));
            set(mc,"framebufferMc",new Framebuffer(960,640,true));
            Theme.setDefaultTheme(Theme.SynthWave);
            MainMenuCheck.run(mc);
            if(!Boolean.getBoolean("pulsar.menu.baseline")) extraShots(mc);
            context.check();
            MainMenu.releaseResources();
        }
        System.out.println("PASS: VIS-01 standalone offscreen runner");
        System.exit(0);
    }
    private static void set(Object target,String name,Object value) throws Exception {
        Field f=target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target,value);
    }
    private static void extraShots(Minecraft mc) throws Exception {
        ClientSettings c=ClientSettings.current();MainMenuCheck.Screen screen=new MainMenuCheck.Screen();
        // Fixture-only cyan custom theme; production defaults and persisted themes are untouched.
        Theme custom=Theme.custom("custom:00000000-0000-0000-0000-000000000031","VIS Cyan",
            java.util.Arrays.asList(new java.awt.Color(52,232,247),new java.awt.Color(50,113,255)),false,false,.18f,
            new java.awt.Color(10,13,19),new java.awt.Color(241,247,255),new java.awt.Color(52,232,247),.75f,9,.8f,1);
        me.youngjibbit.theme.ThemeCatalog.global().preview(custom);
        MainMenuCheck.require(Theme.getDefaultTheme()==custom,"Custom theme fixture must actually be selected");
        c.surface.setValue("Standard");c.animations.setValue(true);
        mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);
        File out=new File("menu-preview");
        int[] previousPixels=null;
        for(String preset:new String[]{"Calm","Balanced","Expressive"}) {
            c.motionIntensity.setValue(preset);screen.setWorldAndResolution(mc,480,320);
            screen.drawScreen(-1,-1,0);MainMenuCheck.save(mc,new File(out,preset+"-focus.png"));
            ClipRect r=screen.actionBounds(1);int x=(int)(r.x+r.width/2),y=(int)(r.y+r.height/2);
            screen.key(Keyboard.KEY_DOWN);
            for(int i=0;i<30;i++)MainMenuCheck.advance(screen,x,y);
            java.awt.image.BufferedImage image=MainMenuCheck.capture(mc);
            int[] pixels=image.getRGB((int)(r.x*2),(int)(r.y*2),(int)(r.width*2),(int)(r.height*2),null,0,(int)(r.width*2));
            if(previousPixels!=null)MainMenuCheck.require(!java.util.Arrays.equals(previousPixels,pixels),"Preset must change actual button pixels");
            previousPixels=pixels;
            MainMenuCheck.save(mc,new File(out,preset+"-hover.png"));
            screen.mouseClicked(x,y,0);for(int i=0;i<15;i++)MainMenuCheck.advance(screen,x,y);
            MainMenuCheck.save(mc,new File(out,preset+"-pressed.png"));
            screen.key(Keyboard.KEY_ESCAPE);screen.mouseReleased(x,y,0);
            for(int i=0;i<30;i++)MainMenuCheck.advance(screen,-1,-1);
            MainMenuCheck.save(mc,new File(out,preset+"-cancel.png"));
            c.reducedMotion.setValue(true);for(int i=0;i<10;i++)MainMenuCheck.advance(screen,x,y);
            MainMenuCheck.save(mc,new File(out,preset+"-reduced.png"));c.reducedMotion.setValue(false);
        }
        c.blur.setValue(false);c.glow.setValue(false);c.border.setValue(false);
        screen.drawScreen(-1,-1,0);MainMenuCheck.save(mc,new File(out,"focus-effects-off.png"));
        for(int scale=1;scale<=4;scale++) {
            mc.displayWidth=scale==4?1280:1101;mc.displayHeight=scale==4?960:733;mc.gameSettings.guiScale=scale;
            mc.getFramebuffer().createBindFramebuffer(mc.displayWidth,mc.displayHeight);ScaledResolution sr=new ScaledResolution(mc);
            MainMenuCheck.require(sr.getScaleFactor()==scale,"Native check needs the effective requested scale");
            System.out.println("VIS image: display="+mc.displayWidth+"x"+mc.displayHeight+", effective GUI scale="+sr.getScaleFactor()+", GUI="+sr.getScaledWidth()+"x"+sr.getScaledHeight());
            screen.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());screen.drawScreen(-1,-1,0);
            for(int id=0;id<8;id++) {
                ClipRect r=screen.actionBounds(id);MainMenuCheck.require(r.x>=0&&r.y>=0&&r.x+r.width<=sr.getScaledWidth()&&r.y+r.height<=sr.getScaledHeight(),"VIS scale bounds");
            }
            MainMenuCheck.save(mc,new File(out,"cyan-scale-"+scale+".png"));
        }
        screen.onGuiClosed();
        MainMenuCheck.require(org.lwjgl.opengl.GL11.glGetError()==org.lwjgl.opengl.GL11.GL_NO_ERROR,"VIS native GL error");
        System.out.println("PASS: three real preset outputs, focus/hover/pressed/cancel/reduced, custom theme, effects OFF and GUI scales 1-4");
    }
}
