package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.clickgui.*;
import me.youngjibbit.gui.MainMenu;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.account.*;
import me.youngjibbit.altmanager.GuiAltManager;
import me.youngjibbit.altmanager.ui.AccountFormScreen;
import me.youngjibbit.notifications.*;
import me.youngjibbit.theme.ThemeCatalog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.*;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;
/** Actual screen consumers under identical global settings; disconnected synthetic data only. */
public final class SubstanceConsumersCheck {
 static final String[] MODES={"Clear","Gradient","Blur","Liquid Glass"};
 static void run()throws Exception{
  Minecraft mc=SessionInfoRenderCheck.mc;ModuleManager old=Pulsar.INSTANCE.getMm();FontManager fonts=Pulsar.INSTANCE.fontManager;Theme oldTheme=Theme.getDefaultTheme();Object oldAccounts=Pulsar.INSTANCE.getAccountManager();
  SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());Pulsar.INSTANCE.fontManager=new FontManager();ClientSettings c=ClientSettings.current();
  SessionInfoRenderCheck.set(mc,"renderEngine",new TextureManager(mc.getResourceManager()));mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
  AccountManager accounts=new AccountManager(Files.createTempDirectory("surface-accounts-").toFile());accounts.addAccount(new Account("Aster","","Aster"));accounts.addAccount(new Account("Solstice","","Solstice"));SessionInfoRenderCheck.set(Pulsar.INSTANCE,"accountManager",accounts);
  GuiScreen[] screens={new ClickGui(),new iOS(),new SettingsMenu(null),new ThemeEditorScreen(null,new ThemeCatalog(null),id->{}),new MainMenu(),new GuiAltManager(accounts),new AccountFormScreen(null,AccountFormScreen.Mode.ADD),new GuiScreen(){}};
  String[] names={"ClickGUI + controls","iOS GUI + controls","Client Settings","Theme editor","Main menu","Account library","Account form","Global notification"};
  try{
   mc.displayWidth=1280;mc.displayHeight=800;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1280,800);c.reducedMotion.setValue(true);c.gradientMotion.setValue("Static");c.direction.setValue("Horizontal");c.gradientFillStrength.setValue(.45);c.surfaceOpacity.setValue(.7);c.notificationDesign.setValue("Global");c.notificationPosition.setValue("Top Left");c.notificationDuration.setValue(15.0);Theme.setDefaultTheme(Theme.SynthWave);
   for(int group=0;group<2;group++){
    BufferedImage sheet=new BufferedImage(2560,1712,BufferedImage.TYPE_INT_RGB);Graphics2D g=sheet.createGraphics();g.setFont(new Font("SansSerif",Font.PLAIN,16));
    for(int row=0;row<4;row++){
     int index=group*4+row;GuiScreen screen=screens[index];screen.setWorldAndResolution(mc,640,400);mc.currentScreen=screen;
     if(screen instanceof ClickGui){java.util.List<?> rows=(java.util.List<?>)SessionInfoRenderCheck.get(screen,"modules");if(!rows.isEmpty())SessionInfoRenderCheck.set(rows.get(0),"open",true);}
     if(screen instanceof iOS){java.util.List<?> rows=(java.util.List<?>)SessionInfoRenderCheck.get(screen,"MODULES");if(!rows.isEmpty())SessionInfoRenderCheck.set(rows.get(0),"open",true);}
     if(index==7){NotificationManager.releaseResources();NotificationManager.show(new Notification(NotificationType.SUCCESS,"Global surface","Same Rectangle Substance, theme and RGBA settings.",15));}
     BufferedImage previous=null;
     for(int mode=0;mode<4;mode++){
      c.surface.setValue(MODES[mode]);for(int warm=0;warm<2;warm++)draw(screen,index==7);
      RenderDiagnostics.enabled=true;RenderDiagnostics.reset();draw(screen,index==7);SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"Warm consumer allocations: "+names[index]);RenderDiagnostics.enabled=false;
      BufferedImage image=SessionInfoRenderCheck.capture();if(previous!=null)SessionInfoRenderCheck.require(!GlobalEffectsCheck.same(previous,image),"Consumer ignored substance: "+names[index]+" / "+MODES[mode]);previous=image;
      g.setColor(new Color(18,20,25));g.fillRect(mode*640,row*428,640,428);g.setColor(Color.WHITE);g.drawString(names[index]+" / "+MODES[mode],mode*640+8,row*428+20);g.drawImage(image,mode*640,row*428+28,640,400,null);
      SessionInfoRenderCheck.require(UiEffects.scene()==null&&glGetError()==GL_NO_ERROR,"Consumer scene/state leaked: "+names[index]);
     }
    }
    g.dispose();ImageIO.write(sheet,"png",new java.io.File(SessionInfoRenderCheck.OUT,"16-consumers-"+group+".png"));
   }
   // True RGBA/theme/axis/blur toggles on actual controls and nested panels, including resize.
   Theme rgba=Theme.custom("custom:00000000-0000-0000-0000-000000000999","RGBA fixture",Arrays.asList(new Color(20,230,180,90),new Color(230,30,180,180)),false,false,.2f,new Color(20,25,35,100),Color.WHITE,Color.CYAN,.75f,6,.7f,1.2f);Theme.setDefaultTheme(rgba);
   for(String axis:new String[]{"Horizontal","Vertical"})for(String method:new String[]{"Gaussian","Kawase"})for(int[] size:new int[][]{{1001,701,3},{800,600,1}}){c.direction.setValue(axis);c.blurMethod.setValue(method);c.surface.setValue("Liquid Glass");mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=size[2];mc.getFramebuffer().createBindFramebuffer(size[0],size[1]);ScaledResolution sr=new ScaledResolution(mc);screens[0].setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());c.roundness.setValue(axis.equals("Horizontal")?0.0:18.0);c.glow.setValue(method.equals("Gaussian"));c.blur.setValue(method.equals("Kawase"));draw(screens[0],false);SessionInfoRenderCheck.require(UiEffects.scene()==null&&glGetError()==GL_NO_ERROR,"RGBA/axis/algorithm/resize state");}
   System.out.println("PASS: eight actual consumers adopt all four global substances, sharp content, nested controls; warm allocation-free; RGBA/theme, both axes, algorithms, masters, radius extrema, odd resize/scales");
  }finally{RenderDiagnostics.enabled=false;for(GuiScreen screen:screens)screen.onGuiClosed();NotificationManager.releaseResources();mc.currentScreen=null;Pulsar.INSTANCE.fontManager.close();Pulsar.INSTANCE.fontManager=fonts;Theme.setDefaultTheme(oldTheme);SessionInfoRenderCheck.set(Pulsar.INSTANCE,"accountManager",oldAccounts);SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
 static void draw(GuiScreen screen,boolean notification){SessionInfoRenderCheck.backdrop();screen.drawScreen(-1,-1,0);if(notification)NotificationManager.render();}
}
