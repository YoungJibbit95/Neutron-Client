package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.*;
import me.youngjibbit.clickgui.ClickGui;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.FontManager;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;
/** Actual Arraylist and expanded ClickGUI controls on the same colorful, structured scene. */
public final class LiquidGlassRefinementCheck {
 static Minecraft mc;static ArrayListHUDModule array;static ClickGui gui;
 static void run()throws Exception{
  mc=SessionInfoRenderCheck.mc;ModuleManager old=Pulsar.INSTANCE.getMm();FontManager fonts=Pulsar.INSTANCE.fontManager;Theme oldTheme=Theme.getDefaultTheme();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());Pulsar.INSTANCE.fontManager=new FontManager();ClientSettings c=ClientSettings.current();
  SessionInfoRenderCheck.set(mc,"renderEngine",new TextureManager(mc.getResourceManager()));mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());gui=new ClickGui();
  try{
   mc.displayWidth=1280;mc.displayHeight=800;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1280,800);gui.setWorldAndResolution(mc,640,400);SessionInfoRenderCheck.set(gui,"x",190f);SessionInfoRenderCheck.set(gui,"y",20f);SessionInfoRenderCheck.set(gui,"width",430f);SessionInfoRenderCheck.set(gui,"height",360f);java.util.List<?> rows=(java.util.List<?>)SessionInfoRenderCheck.get(gui,"modules");if(!rows.isEmpty())SessionInfoRenderCheck.set(rows.get(0),"open",true);
   array=(ArrayListHUDModule)Pulsar.INSTANCE.getMm().getModule(ArrayListHUDModule.class);array.setToggled(true);Pulsar.INSTANCE.getMm().getModule(WatermarkHUDModule.class).setToggled(true);Pulsar.INSTANCE.getMm().getModule(SessionInfo.class).setToggled(true);Pulsar.INSTANCE.getMm().getModule(ArmorHUD.class).setToggled(true);Pulsar.INSTANCE.getMm().getModule(TargetHUD.class).setToggled(true);array.getBooleanSetting("Show Render").setValue(true);array.getBooleanSetting("Background").setValue(false);array.getBooleanSetting("Blur").setValue(false);array.getBooleanSetting("Glow").setValue(false);
   c.reducedMotion.setValue(true);c.gradientMotion.setValue("Static");c.glow.setValue(false);c.border.setValue(false);c.roundness.setValue(12.0);Theme.setDefaultTheme(Theme.SynthWave);
   java.util.List<BufferedImage> pictures=new ArrayList<>();java.util.List<String> labels=new ArrayList<>();
   for(String mode:new String[]{"Clear","Gradient","Blur","Liquid Glass"}){
    c.surface.setValue(mode);array.getModeSetting("Effects Source").setValue("Global");draw();draw();BufferedImage global=SessionInfoRenderCheck.capture();pictures.add(global);labels.add("Global "+mode+" / actual Arraylist + ClickGUI controls");
    c.surface.setValue("Clear");array.getModeSetting("Effects Source").setValue(mode);draw();BufferedImage direct=SessionInfoRenderCheck.capture();for(int y=0;y<360;y++)for(int x=0;x<350;x++)SessionInfoRenderCheck.require(global.getRGB(x,y)==direct.getRGB(x,y),"Arraylist direct material differs from global "+mode);
   }
   SessionInfoRenderCheck.sheet("20-arraylist-substances-resize.png",labels,pictures);pictures.clear();labels.clear();array.getModeSetting("Effects Source").setValue("Global");c.surface.setValue("Liquid Glass");
   for(String method:new String[]{"Gaussian","Kawase","Blur off"}){
    c.blur.setValue(!method.equals("Blur off"));c.blurMethod.setValue(method.equals("Kawase")?"Kawase":"Gaussian");BufferedImage regular=null;
    for(String finish:new String[]{"Regular","Clear"}){c.glassAppearance.setValue(finish);draw();BufferedImage image=SessionInfoRenderCheck.capture();pictures.add(image);labels.add(finish+" / "+method+" / border + glow off");if(finish.equals("Regular"))regular=image;else if(!Boolean.getBoolean("pulsar.glass.baseline"))SessionInfoRenderCheck.require(!GlobalEffectsCheck.same(regular,image),"Glass finishes are aliases");}
   }
   SessionInfoRenderCheck.sheet(Boolean.getBoolean("pulsar.glass.baseline")?"19-glass-before-resize.png":"19-glass-after-resize.png",labels,pictures);
   checkSelector();
   c.blur.setValue(true);c.glow.setValue(true);c.border.setValue(true);c.glassAppearance.setValue("Regular");draw();RenderDiagnostics.enabled=true;RenderDiagnostics.reset();for(int i=0;i<4;i++)draw();SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"Glass refinement allocates warm resources");RenderDiagnostics.enabled=false;
   for(int[] size:new int[][]{{1001,701,3},{800,600,1},{1280,800,2}}){mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=size[2];mc.getFramebuffer().createBindFramebuffer(size[0],size[1]);ScaledResolution sr=new ScaledResolution(mc);gui.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());draw();SessionInfoRenderCheck.require(UiEffects.scene()==null&&glGetError()==GL_NO_ERROR,"Glass resize/state error");}
   System.out.println("PASS: actual Arraylist adopts identical global/direct substances despite old switches; Regular/Clear glass, both blur algorithms/off, expanded GUI controls, warm resources, scales and resize");
  }finally{RenderDiagnostics.enabled=false;gui.onGuiClosed();for(me.youngjibbit.module.Module m:Pulsar.INSTANCE.getMm().getAllModules())if(m.isToggled())m.setToggled(false);Pulsar.INSTANCE.fontManager.close();Pulsar.INSTANCE.fontManager=fonts;Theme.setDefaultTheme(oldTheme);SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
 static void checkSelector()throws Exception{
  SessionInfoRenderCheck.set(gui,"search","ArrayList");java.lang.reflect.Method setup=ClickGui.class.getDeclaredMethod("setupModules");setup.setAccessible(true);setup.invoke(gui);Object row=((java.util.List<?>)SessionInfoRenderCheck.get(gui,"modules")).get(0);SessionInfoRenderCheck.set(row,"open",true);
  me.youngjibbit.clickgui.component.impl.ClickCombo selector=null;for(Object control:(java.util.List<?>)SessionInfoRenderCheck.get(row,"values"))if(control instanceof me.youngjibbit.clickgui.component.impl.ClickCombo&&SessionInfoRenderCheck.get(control,"value")==array.getModeSetting("Effects Source"))selector=(me.youngjibbit.clickgui.component.impl.ClickCombo)control;
  SessionInfoRenderCheck.require(selector!=null,"Arraylist substance control missing");SessionInfoRenderCheck.set(selector,"open",true);SessionInfoRenderCheck.set(gui,"targetScrollOffset",-10000f);draw();javax.imageio.ImageIO.write(SessionInfoRenderCheck.capture(),"png",new java.io.File(SessionInfoRenderCheck.OUT,"21-arraylist-selector.png"));
  java.lang.reflect.Method click=ClickGui.class.getDeclaredMethod("mouseClicked",int.class,int.class,int.class);click.setAccessible(true);click.invoke(gui,(int)selector.getX()+30,(int)selector.getY()+24+5*24+12,0);SessionInfoRenderCheck.require(array.getModeSetting("Effects Source").getValue().equals("Liquid Glass"),"Actual arraylist dropdown click did not choose Liquid Glass");
 }
 static void draw(){try{backdrop();UiEffects.beginFrame();gui.drawScreen(-1,-1,0);java.lang.reflect.Method draw=ArrayListHUDModule.class.getDeclaredMethod("renderArrayList");draw.setAccessible(true);GuiShaderBloom.beginBatch();draw.invoke(array);GuiShaderBloom.flushBatch(2,3);}catch(Exception e){throw new RuntimeException(e);}}
 static void backdrop(){SessionInfoRenderCheck.backdrop();GlStateManager.disableTexture2D();glDisable(GL_TEXTURE_2D);glShadeModel(GL_SMOOTH);glBegin(GL_QUADS);glColor3f(.06f,.35f,.48f);glVertex2f(0,0);glColor3f(.65f,.22f,.42f);glVertex2f(640,0);glColor3f(.86f,.65f,.27f);glVertex2f(640,400);glColor3f(.08f,.2f,.25f);glVertex2f(0,400);glEnd();glBegin(GL_LINES);for(int x=0;x<640;x+=12){glColor3f(.16f,.38f,.42f);glVertex2f(x,0);glVertex2f(x,400);}for(int y=0;y<400;y+=12){glVertex2f(0,y);glVertex2f(640,y);}glEnd();GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.color(1,1,1,1);glColor4f(1,1,1,1);}
}
