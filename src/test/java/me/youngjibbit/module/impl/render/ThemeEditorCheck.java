package me.youngjibbit.module.impl.render;
import me.youngjibbit.clickgui.ThemeEditorScreen;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.Pulsar;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Keyboard;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.lwjgl.opengl.GL11.*;

/** Real editor draw/click/key handlers in the existing isolated pbuffer; navigation alone is intercepted. */
public final class ThemeEditorCheck {
 static class Screen extends ThemeEditorScreen {
  boolean returned,ctrl,shift;
  Screen(ThemeCatalog c,Consumer<String> selected){super(null,c,selected);}
  @Override public ClipRect actionBounds(int id){return super.actionBounds(id);}
  @Override protected void leave(){returned=true;}
  @Override protected boolean ctrlDown(){return ctrl;}@Override protected boolean shiftDown(){return shift;}
  void click(int id)throws Exception{ClipRect r=actionBounds(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);mouseReleased(0,0,0);}
  void field(int id,String text)throws Exception{click(id);for(char c:text.toCharArray())keyTyped(c,Keyboard.KEY_NONE);keyTyped(' ',Keyboard.KEY_RETURN);}
  void slider(int id,float progress)throws Exception{ClipRect r=actionBounds(id);mouseClicked((int)(r.x+r.width*progress),(int)(r.y+r.height/2),0);mouseReleased(0,0,0);}
  void type(String text)throws Exception{for(char c:text.toCharArray())keyTyped(c,Keyboard.KEY_NONE);}
  void number(int id,String value)throws Exception{ClipRect r=actionBounds(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),1);type(value);keyTyped(' ',Keyboard.KEY_RETURN);}
  void key(int k)throws Exception{keyTyped(' ',k);}
 }
 static final File OUT=new File("theme-preview");
 static void init(Screen screen,int w,int h,int gui){SessionInfoRenderCheck.mc.displayWidth=w;SessionInfoRenderCheck.mc.displayHeight=h;SessionInfoRenderCheck.mc.gameSettings.guiScale=gui;SessionInfoRenderCheck.mc.getFramebuffer().createBindFramebuffer(w,h);ScaledResolution sr=new ScaledResolution(SessionInfoRenderCheck.mc);screen.setWorldAndResolution(SessionInfoRenderCheck.mc,sr.getScaledWidth(),sr.getScaledHeight());}
 static void save(Screen screen,String name)throws Exception{screen.drawScreen(-1,-1,0);javax.imageio.ImageIO.write(SessionInfoRenderCheck.capture(),"png",new File(OUT,name+".png"));}
 public static void run()throws Exception{
  OUT.mkdirs();ThemeCatalog previous=ThemeCatalog.global();Path file=Files.createTempDirectory(new File(OUT,"data").toPath().toAbsolutePath().getParent(),"theme-check-").resolve("themes.json");ThemeCatalog c=new ThemeCatalog(new ThemeStore(file));ThemeCatalog.install(c);c.select("SynthWave");java.util.List<String> selections=new ArrayList<>();Consumer<String> select=ref->{selections.add(ref);c.select(ref);};
  try{
   Screen screen=new Screen(c,select);init(screen,1280,960,2);save(screen,"01-preset");screen.click(2);screen.field(9,"Unsaved preview");screen.field(10,"FA246080");screen.click(7);screen.click(8);screen.slider(20,.6f);double previousSpeed=c.current().getAnimationSpeed();screen.key(Keyboard.KEY_RETURN);SessionInfoRenderCheck.require(c.current().getAnimationSpeed()==previousSpeed,"Enter moved slider");screen.type("0.375");screen.key(Keyboard.KEY_RETURN);SessionInfoRenderCheck.require(Math.abs(c.current().getAnimationSpeed()-.375)<.0001,"Exact keyboard parameter input");
   screen.number(20,"-");SessionInfoRenderCheck.require(Math.abs(c.current().getAnimationSpeed()-.375)<.0001,"Invalid number committed");screen.key(Keyboard.KEY_ESCAPE);screen.number(20,"0.625");SessionInfoRenderCheck.require(Math.abs(c.current().getAnimationSpeed()-.625)<.0001,"Exact right-click parameter input");
   screen.key(Keyboard.KEY_RETURN);screen.type("0.8");screen.key(Keyboard.KEY_ESCAPE);SessionInfoRenderCheck.require(Math.abs(c.current().getAnimationSpeed()-.625)<.0001,"Numeric cancel changed parameter");screen.slider(22,.4f);save(screen,"02-draft");
   SessionInfoRenderCheck.require(c.current().isCustom()&&c.current().isVertical()&&c.current().isAnimated()&&c.current().getPrimaryGradientColor().getAlpha()==128,"Real RGBA/direction/motion input");
   SessionInfoRenderCheck.require(!Files.exists(file)&&selections.isEmpty(),"Preview persisted");RenderDiagnostics.enabled=true;RenderDiagnostics.reset();for(int i=0;i<8;i++)screen.drawScreen(-1,-1,0);SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0&&RenderDiagnostics.shaderCompiles==0,"Editor steady resource creation");System.out.println("Editor steady 8 frames: "+RenderDiagnostics.snapshot());RenderDiagnostics.enabled=false;
   screen.click(4);screen.onGuiClosed();SessionInfoRenderCheck.require(screen.returned&&c.current()==Theme.SynthWave&&!Files.exists(file),"Cancel did not restore active theme");
   screen=new Screen(c,select);init(screen,1280,960,2);screen.click(2);screen.field(9,"Studio theme");screen.field(10,"A020F080");screen.field(12,"17243A90");screen.field(13,"EEF6FFFF");screen.field(14,"8EF5FFFF");screen.click(7);save(screen,"03-custom-static");BufferedImage frozen=SessionInfoRenderCheck.capture();screen.drawScreen(-1,-1,0);SessionInfoRenderCheck.require(WatermarkCheck.equal(frozen,SessionInfoRenderCheck.capture()),"Static preview still changes");screen.click(3);screen.onGuiClosed();String id=c.selectedReference();SessionInfoRenderCheck.require(Files.exists(file)&&id.startsWith("custom:")&&selections.size()==1&&c.current().getDisplayName().equals("Studio theme"),"Save/use handler");
   ThemeCatalog reload=new ThemeCatalog(new ThemeStore(file));reload.select(id);SessionInfoRenderCheck.require(reload.current().getBackground().getAlpha()==144&&reload.current().getDisplayName().equals("Studio theme"),"Definition reload RGBA");
   screen=new Screen(c,select);init(screen,1280,960,2);screen.field(9,"Resize preview");screen.drawScreen(-1,-1,0);RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
   for(int i=0;i<3;i++){init(screen,i==0?640:1101,i==0?480:733,i==2?3:2);screen.drawScreen(-1,-1,0);for(int control:new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,20,21,22,23,24}){ClipRect r=screen.actionBounds(control);ScaledResolution sr=new ScaledResolution(SessionInfoRenderCheck.mc);SessionInfoRenderCheck.require(r.x>=0&&r.y>=0&&r.x+r.width<=sr.getScaledWidth()&&r.y+r.height<=sr.getScaledHeight(),"Editor bounds/resize");}SessionInfoRenderCheck.require(c.current().getDisplayName().equals("Resize preview"),"Resize lost draft");if(i==2)save(screen,"04-small-scale3");}
   SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes&&RenderDiagnostics.programCreates==0,"Editor resize resource leak");System.out.println("Editor 3 resizes: "+RenderDiagnostics.snapshot());RenderDiagnostics.enabled=false;screen.click(4);screen.onGuiClosed();
   screen=new Screen(c,select);init(screen,1280,960,2);screen.field(9,"Renamed studio");screen.click(3);screen.onGuiClosed();SessionInfoRenderCheck.require(c.selectedReference().equals(id)&&c.current().getDisplayName().equals("Renamed studio"),"Rename changed ID");
   screen=new Screen(c,select);init(screen,1280,960,2);screen.click(9);screen.key(Keyboard.KEY_BACK);screen.drawScreen(-1,-1,0);screen.click(4);screen.onGuiClosed();SessionInfoRenderCheck.require(c.current().getDisplayName().equals("Renamed studio"),"Invalid field blocked Cancel");
   screen=new Screen(c,select);init(screen,1280,960,2);screen.field(9,"Discard rename");screen.click(5);SessionInfoRenderCheck.require(c.current().getDisplayName().equals("Renamed studio"),"Reset handler");screen.key(Keyboard.KEY_ESCAPE);screen.onGuiClosed();
   checkMasks(c.current());
   screen=new Screen(c,select);init(screen,1280,960,2);screen.click(6);screen.onGuiClosed();SessionInfoRenderCheck.require(c.current()==Theme.PinkLemonade&&c.find(id)==null&&selections.get(selections.size()-1).equals("PinkLemonade"),"Delete/fallback handler");
   SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Theme editor GL error");System.out.println("PASS: editor UI RGBA/name/direction/motion/sliders/exact-invalid-cancelled-numbers/save/cancel/reset/delete, stable IDs, resize/scales, cached effects, custom mask and transparent glyph pixels");
  }finally{RenderDiagnostics.enabled=false;c.cancelPreview();ThemeCatalog.install(previous);}
 }
 static void checkMasks(Theme theme)throws Exception{
  ArrayListSurface surface=new ArrayListSurface();ArrayListSurface.Shape shape=new ArrayListSurface.Shape(30,new float[]{40},new float[]{110},new float[]{220},6);
  SessionInfoRenderCheck.backdrop();surface.render(shape,theme,false,false,false,.75f,()->{});BufferedImage off=SessionInfoRenderCheck.capture();SessionInfoRenderCheck.backdrop();surface.render(shape,theme,false,false,true,.75f,()->{});BufferedImage glow=SessionInfoRenderCheck.capture();
  int scale=new ScaledResolution(SessionInfoRenderCheck.mc).getScaleFactor();for(int y=50*scale;y<100*scale;y++)for(int x=40*scale;x<210*scale;x++)SessionInfoRenderCheck.require(off.getRGB(x,y)==glow.getRGB(x,y),"Custom glow leaked into interior");
  SessionInfoRenderCheck.backdrop();surface.render(shape,theme,false,true,false,.75f,()->{});BufferedImage blur=SessionInfoRenderCheck.capture();int changed=0;for(int y=0;y<blur.getHeight();y++)for(int x=0;x<blur.getWidth();x++){boolean outside=x<28*scale||x>222*scale||y<38*scale||y>112*scale;if(outside)SessionInfoRenderCheck.require(off.getRGB(x,y)==blur.getRGB(x,y),"Custom blur changed exterior");else if(off.getRGB(x,y)!=blur.getRGB(x,y))changed++;}SessionInfoRenderCheck.require(changed>100,"Custom blur absent");surface.close();
  TextEffects effects=new TextEffects();SessionInfoRenderCheck.backdrop();BufferedImage scene=SessionInfoRenderCheck.capture();effects.draw(Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 28"),"Transparent",30,40,0x00ffffff,0x00ffffff,2,false,0,true,1,4,false);SessionInfoRenderCheck.require(WatermarkCheck.equal(scene,SessionInfoRenderCheck.capture()),"Zero-alpha glyph palette still visible");effects.close();
 }
}
