package me.youngjibbit.module.impl.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.clickgui.ClickGui;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ThemeModule;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.lang.reflect.*;
import java.util.Arrays;

/** Real draws of both GUIs and the confirmed-session HUD with a disposable custom palette; no client bootstrap. */
public final class ThemeGuiRenderCheck {
    private static Theme custom(String id,Color primary,Color background) {
        return Theme.custom(id,"Integrated GUI palette",Arrays.asList(primary,primary),false,false,.2f,
                background,Color.WHITE,primary,.75f,6,.7f,1.2f);
    }
    private static BufferedImage draw(GuiScreen screen) throws Exception {
        ScaledResolution sr=new ScaledResolution(SessionInfoRenderCheck.mc);
        screen.setWorldAndResolution(SessionInfoRenderCheck.mc,sr.getScaledWidth(),sr.getScaledHeight());
        SessionInfoRenderCheck.mc.currentScreen=screen;
        for(int i=0;i<35;i++) {SessionInfoRenderCheck.backdrop();screen.drawScreen(-1,-1,0);Thread.sleep(10);}
        if(screen instanceof iOS) {
            Field scale=iOS.class.getDeclaredField("renderedScale");scale.setAccessible(true);
            SessionInfoRenderCheck.require(scale.getFloat(screen)>.98f,"iOS opening animation did not reach a visible, interactive scale");
        }
        return SessionInfoRenderCheck.capture();
    }
    static void changed(BufferedImage before,BufferedImage after,String consumer,java.awt.Rectangle region) {
        SessionInfoRenderCheck.require(before.getWidth()==after.getWidth() && before.getHeight()==after.getHeight(),"Palette probe dimensions changed");
        SessionInfoRenderCheck.require(new java.awt.Rectangle(0,0,before.getWidth(),before.getHeight()).contains(region),"Palette probe region outside framebuffer");
        int pixels=0,accents=0;long paletteShift=0;
        for(int y=region.y;y<region.y+region.height;y++)for(int x=region.x;x<region.x+region.width;x++) {
            int a=before.getRGB(x,y),b=after.getRGB(x,y);if(a!=b)pixels++;
            int ar=(a>>16)&255,ag=(a>>8)&255,br=(b>>16)&255,bg=(b>>8)&255;
            paletteShift+=bg-br-ag+ar;
            if(ar>ag+8 && bg>br+8) accents++;
        }
        SessionInfoRenderCheck.require(pixels>100,consumer+" did not draw the selected custom palette");
        SessionInfoRenderCheck.require(paletteShift>1000,consumer+" did not apply the red-to-green palette update");
        SessionInfoRenderCheck.require(accents>100,consumer+" did not change the actual accent pixels from red to green");
    }
    private static java.awt.Rectangle full(BufferedImage image) { return new java.awt.Rectangle(0,0,image.getWidth(),image.getHeight()); }
    private static java.awt.Rectangle iosAccent(iOS screen) throws Exception {
        int scale=new ScaledResolution(SessionInfoRenderCheck.mc).getScaleFactor();
        Field x=iOS.class.getDeclaredField("x"),y=iOS.class.getDeclaredField("y");x.setAccessible(true);y.setAccessible(true);
        // Central top-border/glow strip: exclude shader backdrop, particles, sidebar icons and traffic lights.
        return new java.awt.Rectangle((int)((x.getFloat(screen)+125)*scale),(int)((y.getFloat(screen)-3)*scale),180*scale,7*scale);
    }
    private static Field field(Class<?> type,String name) throws Exception {
        Field field=type.getDeclaredField(name);field.setAccessible(true);return field;
    }
    private static void iosVisibility(iOS screen) throws Exception {
        me.youngjibbit.module.impl.combat.Aura aura=(me.youngjibbit.module.impl.combat.Aura)Pulsar.INSTANCE.getMm().getModule(me.youngjibbit.module.impl.combat.Aura.class);
        me.youngjibbit.gui.iosUi.component.impl.iOSModule row=null;
        for(Object candidate:(java.util.List<?>)field(iOS.class,"MODULES").get(screen)) {
            if(field(me.youngjibbit.gui.iosUi.component.impl.iOSModule.class,"module").get(candidate)==aura)
                row=(me.youngjibbit.gui.iosUi.component.impl.iOSModule)candidate;
        }
        SessionInfoRenderCheck.require(row!=null,"Aura controls missing from iOS Combat category");
        Class<?> rowType=row.getClass();field(rowType,"open").setBoolean(row,true);
        me.youngjibbit.utils.animations.Animation opening=(me.youngjibbit.utils.animations.Animation)field(rowType,"openAnimation").get(row);
        opening.setDirection(me.youngjibbit.utils.animations.Direction.FORWARDS);opening.timerUtil.setTime(System.currentTimeMillis()-1000);
        me.youngjibbit.gui.iosUi.component.impl.iOSSlider frequency=null;
        for(Object control:(java.util.List<?>)field(rowType,"VALUES").get(row)) {
            if(control instanceof me.youngjibbit.gui.iosUi.component.impl.iOSSlider) {
                me.youngjibbit.gui.iosUi.component.impl.iOSSlider slider=(me.youngjibbit.gui.iosUi.component.impl.iOSSlider)control;
                if(slider.getValue().getId().equals("frequency")) frequency=slider;
            }
        }
        SessionInfoRenderCheck.require(frequency!=null,"Frequency control missing");
        aura.getModeSetting("Randomization Style").setValue("Benutzerdefiniert");
        screen.scrollOffset=-100000;SessionInfoRenderCheck.backdrop();screen.drawScreen(-1,-1,0);
        float expanded=row.getHeight(),expandedScroll=field(iOS.class,"maxScroll").getFloat(screen);
        SessionInfoRenderCheck.require(frequency.getValue().isVisible() && frequency.getX()!=0,"Visible expert control was not laid out/drawn");
        for(Object control:(java.util.List<?>)field(rowType,"VALUES").get(row)) {
            me.youngjibbit.gui.iosUi.component.iOSComponent.Sub<?> child=(me.youngjibbit.gui.iosUi.component.iOSComponent.Sub<?>)control;
            if(((me.youngjibbit.value.Value<?>)child.getValue()).isVisible())
                SessionInfoRenderCheck.require(child.getY()+child.getHeight()<=row.getY()+row.getHeight(),"Visible control clipped by its own module height");
        }
        SessionInfoRenderCheck.require(screen.scrollOffset==-expandedScroll && expandedScroll>0,"Expanded content did not clamp scroll offset");
        double previous=frequency.getValue().getValue();frequency.updateComponent(-12345,-12345);
        field(frequency.getClass(),"dragging").setBoolean(frequency,true);
        aura.getModeSetting("Randomization Style").setValue("Sanft");
        SessionInfoRenderCheck.backdrop();screen.drawScreen(500,500,0);
        float collapsedScroll=field(iOS.class,"maxScroll").getFloat(screen);
        SessionInfoRenderCheck.require(row.getHeight()<expanded && collapsedScroll<expandedScroll,"Preset did not remove hidden controls from layout height");
        SessionInfoRenderCheck.require(screen.scrollOffset==-collapsedScroll,"Visibility change left scroll outside current content");
        SessionInfoRenderCheck.require(frequency.getX()==-12345 && !field(frequency.getClass(),"dragging").getBoolean(frequency),"Hidden slider was drawn or retained input capture");
        SessionInfoRenderCheck.require(frequency.getValue().getValue()==previous,"Hidden slider mutated its setting during drawing");
        screen.scrollOffset=50;SessionInfoRenderCheck.backdrop();screen.drawScreen(-1,-1,0);
        SessionInfoRenderCheck.require(screen.scrollOffset==0,"Positive scroll offset was not clamped");
        System.out.println("PASS: iOS preset visibility controls real layout/draw/input capture; shrinking content clamps scroll");
    }
    public static void run() throws Exception {
        ThemeCatalog previous=ThemeCatalog.global();ModuleManager oldManager=Pulsar.INSTANCE.getMm();iOS oldIos=Pulsar.INSTANCE.getIOS();
        FontManager oldFonts=Pulsar.INSTANCE.getFontManager();GuiScreen oldScreen=SessionInfoRenderCheck.mc.currentScreen;
        ThemeCatalog catalog=new ThemeCatalog(new ThemeStore(Files.createTempDirectory(SessionInfoRenderCheck.OUT.toPath(),"theme-gui-").resolve("themes.json")));
        ClickGui click=null;iOS ios=null;
        try {
            ThemeCatalog.install(catalog);
            SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());
            ThemeModule selected=(ThemeModule)Pulsar.INSTANCE.getMm().getModule(ThemeModule.class);
            Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
            Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"SFProText-Bold 20","icon1 19","KhmerOureang 19","icon 23","Poppins-Medium 20","Product-Sans-Medium 20","Poppins-Bold 23","Product-Sans-Bold 16","icon 22","icon1 22","SFProText-Medium 22","SFProText-Medium 17","Poppins-Regular 19","Poppins-Regular 21"});
            TextureManager textures=new TextureManager(SessionInfoRenderCheck.mc.getResourceManager());SessionInfoRenderCheck.set(SessionInfoRenderCheck.mc,"renderEngine",textures);
            SessionInfoRenderCheck.mc.fontRendererObj=new FontRenderer(SessionInfoRenderCheck.mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),textures,false);
            SessionInfoRenderCheck.mc.fontRendererObj.onResourceManagerReload(SessionInfoRenderCheck.mc.getResourceManager());
            SessionInfoRenderCheck.mc.displayWidth=1280;SessionInfoRenderCheck.mc.displayHeight=960;SessionInfoRenderCheck.mc.gameSettings.guiScale=2;
            SessionInfoRenderCheck.mc.getFramebuffer().createBindFramebuffer(1280,960);
            String id="custom:"+java.util.UUID.randomUUID();catalog.save(custom(id,Color.RED,new Color(60,15,20,180)));selected.selectedTheme.setValue(id);
            click=new ClickGui();ios=new iOS();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"iOS",ios);
            BufferedImage clickA=draw(click),iosA=draw(ios);
            java.awt.Rectangle iosRegion=iosAccent(ios);
            BufferedImage hudA=SessionInfoRenderCheck.render("Outline",SessionInfoRenderCheck.NAME,SessionInfoRenderCheck.sample());
            long generation=SessionInfoRenderCheck.stats.generation();
            catalog.save(custom(id,Color.GREEN,new Color(15,60,20,180)));
            changed(clickA,draw(click),"ClickGUI",full(clickA));changed(iosA,draw(ios),"iOS GUI",iosRegion);
            changed(hudA,SessionInfoRenderCheck.render("Outline",SessionInfoRenderCheck.NAME,SessionInfoRenderCheck.sample()),"SessionInfo",full(hudA));
            SessionInfoRenderCheck.require(id.equals(selected.selectedTheme.getValue())&&SessionInfoRenderCheck.stats.generation()==generation,"Theme redraw changed selection/session identity");
            iosVisibility(ios);
            System.out.println("PASS: custom palettes render in both GUIs and SessionInfo; shared immutable definition updates keep selection/session identity");
        } finally {
            if(click!=null)click.onGuiClosed();if(ios!=null)ios.onGuiClosed();
            SessionInfoRenderCheck.mc.currentScreen=oldScreen;Pulsar.INSTANCE.fontManager=oldFonts;
            SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",oldManager);SessionInfoRenderCheck.set(Pulsar.INSTANCE,"iOS",oldIos);ThemeCatalog.install(previous);
        }
    }
}
