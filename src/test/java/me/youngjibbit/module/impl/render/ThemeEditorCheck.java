package me.youngjibbit.module.impl.render;
import me.youngjibbit.clickgui.ThemeEditorScreen;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.theme.*;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.Pulsar;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.input.Keyboard;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.concurrent.atomic.AtomicLong;
import static org.lwjgl.opengl.GL11.*;

/** Real editor draw/click/key handlers in the existing isolated pbuffer; navigation alone is intercepted. */
public final class ThemeEditorCheck {
 static class Screen extends ThemeEditorScreen {
  boolean returned,ctrl,shift;
  Screen(ThemeCatalog c,Consumer<String> selected){super(null,c,selected);}
  @Override public void revealControl(int id){super.revealControl(id);}
  @Override public ClipRect actionBounds(int id){return super.actionBounds(id);}
  @Override public ClipRect previewBounds(){return super.previewBounds();}
  @Override protected void leave(){returned=true;}
  @Override protected boolean ctrlDown(){return ctrl;}@Override protected boolean shiftDown(){return shift;}
  void click(int id)throws Exception{revealControl(id);ClipRect r=actionBounds(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),0);mouseReleased(0,0,0);}
  void field(int id,String text)throws Exception{click(id);for(char c:text.toCharArray())keyTyped(c,Keyboard.KEY_NONE);keyTyped(' ',Keyboard.KEY_RETURN);}
  void slider(int id,float progress)throws Exception{revealControl(id);ClipRect r=sliderTrack(id);mouseClicked(Math.round(r.x+r.width*progress),Math.round(r.y+1),0);mouseReleased(0,0,0);}
  void type(String text)throws Exception{for(char c:text.toCharArray())keyTyped(c,Keyboard.KEY_NONE);}
  void number(int id,String value)throws Exception{revealControl(id);ClipRect r=actionBounds(id);mouseClicked((int)(r.x+r.width/2),(int)(r.y+r.height/2),1);type(value);keyTyped(' ',Keyboard.KEY_RETURN);}
  void key(int k)throws Exception{keyTyped(' ',k);}
  void settlePresentation(){for(int i=0;i<16;i++)advanceMotion(.25f,-1,-1);}
 }
 static final File OUT=new File("theme-preview");
 static void init(Screen screen,int w,int h,int gui){SessionInfoRenderCheck.mc.displayWidth=w;SessionInfoRenderCheck.mc.displayHeight=h;SessionInfoRenderCheck.mc.gameSettings.guiScale=gui;SessionInfoRenderCheck.mc.getFramebuffer().createBindFramebuffer(w,h);ScaledResolution sr=new ScaledResolution(SessionInfoRenderCheck.mc);screen.setWorldAndResolution(SessionInfoRenderCheck.mc,sr.getScaledWidth(),sr.getScaledHeight());}
 static void draw(Screen screen){SessionInfoRenderCheck.backdrop();screen.drawScreen(-1,-1,0);}
 static void save(Screen screen,String name)throws Exception{draw(screen);javax.imageio.ImageIO.write(SessionInfoRenderCheck.capture(),"png",new File(OUT,name+".png"));}
 public static void run()throws Exception{
  OUT.mkdirs();ThemeCatalog previous=ThemeCatalog.global();Path file=Files.createTempDirectory(new File(OUT,"data").toPath().toAbsolutePath().getParent(),"theme-check-").resolve("themes.json");ThemeCatalog c=new ThemeCatalog(new ThemeStore(file));ThemeCatalog.install(c);c.select("SynthWave");java.util.List<String> selections=new ArrayList<>();Consumer<String> select=ref->{selections.add(ref);c.select(ref);};
  try{
   Screen screen=new Screen(c,select);init(screen,1280,960,2);save(screen,"01-preset");screen.click(2);screen.field(9,"Unsaved preview");screen.field(10,"FA246080");screen.click(7);screen.click(8);screen.slider(20,.6f);double previousSpeed=c.current().getAnimationSpeed();screen.key(Keyboard.KEY_RETURN);SessionInfoRenderCheck.require(c.current().getAnimationSpeed()==previousSpeed,"Enter moved slider");screen.type("0.375");screen.key(Keyboard.KEY_RETURN);SessionInfoRenderCheck.require(Math.abs(c.current().getAnimationSpeed()-.375)<.0001,"Exact keyboard parameter input");
   screen.number(20,"-");SessionInfoRenderCheck.require(Math.abs(c.current().getAnimationSpeed()-.375)<.0001,"Invalid number committed");screen.key(Keyboard.KEY_ESCAPE);screen.number(20,"0.625");SessionInfoRenderCheck.require(Math.abs(c.current().getAnimationSpeed()-.625)<.0001,"Exact right-click parameter input");
   screen.key(Keyboard.KEY_RETURN);screen.type("0.8");screen.key(Keyboard.KEY_ESCAPE);SessionInfoRenderCheck.require(Math.abs(c.current().getAnimationSpeed()-.625)<.0001,"Numeric cancel changed parameter");screen.slider(22,.4f);save(screen,"02-draft");
   SessionInfoRenderCheck.require(c.current().isCustom()&&c.current().isVertical()&&c.current().isAnimated()&&c.current().getPrimaryGradientColor().getAlpha()==128,"Real RGBA/direction/motion input");
   String draftReference=c.current().name();screen.click(25);screen.settlePresentation();save(screen,"02a-palette");screen.click(26);screen.settlePresentation();save(screen,"02b-motion");screen.key(Keyboard.KEY_F6);screen.settlePresentation();save(screen,"02c-light");
   SessionInfoRenderCheck.require(c.current().name().equals(draftReference)&&Math.abs(c.current().getAnimationSpeed()-.625)<.0001,"Theme sections changed draft values");
   SessionInfoRenderCheck.require((int)SessionInfoRenderCheck.get(screen,"section")==2&&(int)SessionInfoRenderCheck.get(screen,"focus")==27,"Theme section keyboard focus");
   SessionInfoRenderCheck.require(!Files.exists(file)&&selections.isEmpty(),"Preview persisted");RenderDiagnostics.enabled=true;RenderDiagnostics.reset();for(int i=0;i<8;i++)draw(screen);SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0&&RenderDiagnostics.shaderCompiles==0,"Editor steady resource creation");System.out.println("Editor steady 8 frames: "+RenderDiagnostics.snapshot());RenderDiagnostics.enabled=false;
   screen.click(4);screen.onGuiClosed();SessionInfoRenderCheck.require(screen.returned&&c.current()==Theme.SynthWave&&!Files.exists(file),"Cancel did not restore active theme");
   screen=new Screen(c,select);init(screen,1280,960,2);screen.click(2);screen.field(9,"Studio theme");screen.field(10,"A020F080");screen.field(12,"17243A90");screen.field(13,"EEF6FFFF");screen.field(14,"8EF5FFFF");screen.click(7);checkStaticPresentation(screen,c);screen.click(3);screen.onGuiClosed();String id=c.selectedReference();SessionInfoRenderCheck.require(Files.exists(file)&&id.startsWith("custom:")&&selections.size()==1&&c.current().getDisplayName().equals("Studio theme"),"Save/use handler");
   ThemeCatalog reload=new ThemeCatalog(new ThemeStore(file));reload.select(id);SessionInfoRenderCheck.require(reload.current().getBackground().getAlpha()==144&&reload.current().getDisplayName().equals("Studio theme"),"Definition reload RGBA");
   screen=new Screen(c,select);init(screen,1280,960,2);screen.field(9,"Resize preview");draw(screen);
   int liveBefore=liveFramebuffers().size();RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
   for(int i=0;i<3;i++){init(screen,i==0?640:1101,i==0?480:733,i==2?3:2);draw(screen);
    System.out.println("Editor resize "+i+": "+RenderDiagnostics.snapshot()+" live="+liveFramebuffers().size()+" owned="+ownedFramebuffers(screen).size());
    for(String owner:new String[]{"surface","previewScene","textEffects"})requireNoBufferReferences(SessionInfoRenderCheck.get(screen,owner),"Hidden preview keeps "+owner+" targets");
    for(int control:new int[]{0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,20,21,22,23,24,25,26,27}){screen.revealControl(control);ClipRect r=screen.actionBounds(control);ScaledResolution sr=new ScaledResolution(SessionInfoRenderCheck.mc);SessionInfoRenderCheck.require(r.x>=0&&r.y>=0&&r.x+r.width<=sr.getScaledWidth()&&r.y+r.height<=sr.getScaledHeight(),"Editor bounds/resize");}SessionInfoRenderCheck.require(c.current().getDisplayName().equals("Resize preview"),"Resize lost draft");if(i==2)save(screen,"04-small-scale3");}
   requireFramebufferBalance("Editor 3 resizes",liveBefore);
   // Re-enter the wide layout as part of the same lifecycle: released preview
   // targets must be recreated successfully and subsequently closed by the editor.
   init(screen,1280,960,2);draw(screen);Set<Framebuffer> closing=ownedFramebuffers(screen);
   Set<Integer> closingTextures=new HashSet<>();for(Framebuffer buffer:closing)closingTextures.add(buffer.framebufferTexture);
   SessionInfoRenderCheck.require(!closing.isEmpty(),"Wide editor must exercise owned render resources");requireFramebufferBalance("Editor wide return",liveBefore);
   screen.click(4);screen.onGuiClosed();requireFramebufferBalance("Editor close after resize",liveBefore);
   for(String owner:new String[]{"rootSurface","surface","previewScene","textEffects"})requireNoBufferReferences(SessionInfoRenderCheck.get(screen,owner),"Closed editor retains "+owner+" targets");
   for(Framebuffer buffer:closing)SessionInfoRenderCheck.require(buffer.framebufferObject<0&&buffer.framebufferTexture<0,"Closed editor did not delete an owned GPU target");
   for(int texture:closingTextures)SessionInfoRenderCheck.require(!glIsTexture(texture),"Closed editor texture still exists on the GPU");
   RenderDiagnostics.enabled=false;
   screen=new Screen(c,select);init(screen,1280,960,2);screen.field(9,"Renamed studio");screen.click(3);screen.onGuiClosed();SessionInfoRenderCheck.require(c.selectedReference().equals(id)&&c.current().getDisplayName().equals("Renamed studio"),"Rename changed ID");
   screen=new Screen(c,select);init(screen,1280,960,2);screen.click(9);screen.key(Keyboard.KEY_BACK);draw(screen);screen.click(4);screen.onGuiClosed();SessionInfoRenderCheck.require(c.current().getDisplayName().equals("Renamed studio"),"Invalid field blocked Cancel");
   screen=new Screen(c,select);init(screen,1280,960,2);screen.field(9,"Discard rename");screen.click(5);SessionInfoRenderCheck.require(c.current().getDisplayName().equals("Renamed studio"),"Reset handler");screen.key(Keyboard.KEY_ESCAPE);screen.onGuiClosed();
   checkMasks(c.current());
   screen=new Screen(c,select);init(screen,1280,960,2);screen.click(6);SessionInfoRenderCheck.require(c.find(id)!=null,"Delete skipped confirmation");screen.click(6);screen.onGuiClosed();SessionInfoRenderCheck.require(c.current()==Theme.PinkLemonade&&c.find(id)==null&&selections.get(selections.size()-1).equals("PinkLemonade"),"Delete/fallback handler");
   SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Theme editor GL error");System.out.println("PASS: editor UI RGBA/name/direction/motion/sliders/exact-invalid-cancelled-numbers/save/cancel/reset/delete, stable IDs, resize/scales, cached effects, custom mask and transparent glyph pixels");
  }finally{RenderDiagnostics.enabled=false;c.cancelPreview();ThemeCatalog.install(previous);}
 }
 static Set<Framebuffer> ownedFramebuffers(Screen screen)throws Exception{
  Set<Framebuffer> owned=Collections.newSetFromMap(new IdentityHashMap<Framebuffer,Boolean>());
  for(String name:new String[]{"rootSurface","surface","previewScene"}){
   Framebuffer[] buffers=(Framebuffer[])SessionInfoRenderCheck.get(SessionInfoRenderCheck.get(screen,name),"buffers");
   // ArrayListSurface slots 0..2 borrow the renderer's shared effect trio.
   for(int i=3;i<buffers.length;i++)addFramebuffers(owned,buffers[i]);
  }
  addFramebuffers(owned,SessionInfoRenderCheck.get(SessionInfoRenderCheck.get(screen,"textEffects"),"buffers"));return owned;
 }
 static Set<Framebuffer> liveFramebuffers()throws Exception{
  Set<Framebuffer> live=Collections.newSetFromMap(new IdentityHashMap<Framebuffer,Boolean>());
  addFramebuffers(live,SessionInfoRenderCheck.mc.getFramebuffer());
  for(Class<?> type:new Class<?>[]{ArrayListSurface.class,TextEffects.class}){
   Collection<?> owners=(Collection<?>)SessionInfoRenderCheck.field(type,"owners").get(null);
   for(Object owner:new ArrayList<Object>(owners))addFramebuffers(live,SessionInfoRenderCheck.get(owner,"buffers"));
  }
  addFramebuffers(live,SessionInfoRenderCheck.field(ArrayListSurface.class,"effects").get(null));
  Object halo=SessionInfoRenderCheck.field(ArrayListSurface.class,"haloCache").get(null);
  addFramebuffers(live,SessionInfoRenderCheck.get(halo,"outputs"));
  addFramebuffers(live,SessionInfoRenderCheck.field(Blur.class,"buffers").get(null));
  // Do not initialize an unused bloom renderer merely to inspect the fixture.
  if((Boolean)SessionInfoRenderCheck.field(GuiShaderBloom.class,"used").get(null)){
   Class<?> bloom=me.youngjibbit.utils.render.shader.KawaseBloom.class;
   for(String name:new String[]{"framebuffer","bloomFramebuffer","framebufferList"})addFramebuffers(live,SessionInfoRenderCheck.field(bloom,name).get(null));
  }
  return live;
 }
 static void addFramebuffers(Set<Framebuffer> buffers,Object value){
  if(value instanceof Framebuffer){Framebuffer buffer=(Framebuffer)value;if(buffer.framebufferObject>=0)buffers.add(buffer);}
  else if(value instanceof Framebuffer[])for(Framebuffer buffer:(Framebuffer[])value)addFramebuffers(buffers,buffer);
  else if(value instanceof Iterable<?>)for(Object buffer:(Iterable<?>)value)addFramebuffers(buffers,buffer);
 }
 static void requireNoBufferReferences(Object owner,String message)throws Exception{
  for(Framebuffer buffer:(Framebuffer[])SessionInfoRenderCheck.get(owner,"buffers"))SessionInfoRenderCheck.require(buffer==null,message);
 }
 static void requireFramebufferBalance(String stage,int liveBefore)throws Exception{
  int live=liveFramebuffers().size();long balance=RenderDiagnostics.framebufferCreates-RenderDiagnostics.framebufferDeletes;
  System.out.println(stage+": "+RenderDiagnostics.snapshot()+" trackedLive="+liveBefore+" -> "+live+" expectedDelta="+(live-liveBefore));
  // Different GUI scales may retain a bounded shared halo entry, and compact
  // mode releases preview targets. Every create/delete must still be accounted
  // for by an actual live GPU owner; a lost or unclosed allocation fails here.
  SessionInfoRenderCheck.require(balance==live-liveBefore,"Unaccounted framebuffer during "+stage);
  SessionInfoRenderCheck.require(RenderDiagnostics.programCreates==0&&RenderDiagnostics.shaderCompiles==0,"Editor resize recreated shader programs");
 }
 static void checkStaticPresentation(Screen screen,ThemeCatalog catalog)throws Exception{
  java.lang.reflect.Field clockField=UiEffects.class.getDeclaredField("clock");clockField.setAccessible(true);LongSupplier originalClock=(LongSupplier)clockField.get(null);
  java.lang.reflect.Method source=UiEffects.class.getDeclaredMethod("timeSource",LongSupplier.class);source.setAccessible(true);AtomicLong nanos=new AtomicLong();
  try{
   source.invoke(null,(LongSupplier)nanos::get);screen.settlePresentation();save(screen,"03-custom-static");BufferedImage frozen=SessionInfoRenderCheck.capture();
   draw(screen);SessionInfoRenderCheck.require(WatermarkCheck.equal(frozen,SessionInfoRenderCheck.capture()),"Settled editor changes with a fixed ambient clock");
   SessionInfoRenderCheck.require(!catalog.current().isAnimated(),"Static fixture must select a static draft");
   float before=UiEffects.time();nanos.addAndGet(100_000_000L);draw(screen);BufferedImage movingBackdrop=SessionInfoRenderCheck.capture();
   javax.imageio.ImageIO.write(movingBackdrop,"png",new File(OUT,"03-static-moving-backdrop.png"));
   SessionInfoRenderCheck.require(UiEffects.time()>before,"Static preview check must advance the ambient clock");
   SessionInfoRenderCheck.require(previewChanges(frozen,movingBackdrop,screen.previewBounds())==0,"Static theme preview changes while the ambient menu scene moves");
   SessionInfoRenderCheck.require(!WatermarkCheck.equal(frozen,movingBackdrop),"The moving scene must remain outside the static preview");
   screen.click(8);nanos.addAndGet(100_000_000L);draw(screen);BufferedImage animated=SessionInfoRenderCheck.capture();
   nanos.addAndGet(100_000_000L);draw(screen);
   SessionInfoRenderCheck.require(previewChanges(animated,SessionInfoRenderCheck.capture(),screen.previewBounds())>0,"Animated preview positive control did not animate");
   screen.click(8);SessionInfoRenderCheck.require(!catalog.current().isAnimated(),"Animation check must restore the static draft");
   System.out.println("PASS: settled editor is pixel-stable; static preview stays pixel-stable over a moving ambient scene; animated preview changes");
  }finally{source.invoke(null,originalClock);UiEffects.invalidate();}
 }
 static int previewChanges(BufferedImage first,BufferedImage second,ClipRect bounds){
  int scale=new ScaledResolution(SessionInfoRenderCheck.mc).getScaleFactor(),changed=0;
  int left=(int)Math.ceil(bounds.x*scale),top=(int)Math.ceil(bounds.y*scale),right=(int)Math.floor((bounds.x+bounds.width)*scale),bottom=(int)Math.floor((bounds.y+bounds.height)*scale);
  SessionInfoRenderCheck.require(right>left&&bottom>top,"Static preview pixel region must be nonempty");
  for(int y=top;y<bottom;y++)for(int x=left;x<right;x++)if(first.getRGB(x,y)!=second.getRGB(x,y))changed++;
  return changed;
 }
 static void checkMasks(Theme theme)throws Exception{
  ArrayListSurface surface=new ArrayListSurface();ArrayListSurface.Shape shape=new ArrayListSurface.Shape(30,new float[]{40},new float[]{110},new float[]{220},6);
  SessionInfoRenderCheck.backdrop();surface.render(shape,theme,false,false,false,.75f,()->{});BufferedImage off=SessionInfoRenderCheck.capture();SessionInfoRenderCheck.backdrop();surface.render(shape,theme,false,false,true,.75f,()->{});BufferedImage glow=SessionInfoRenderCheck.capture();
  int scale=new ScaledResolution(SessionInfoRenderCheck.mc).getScaleFactor();for(int y=50*scale;y<100*scale;y++)for(int x=40*scale;x<210*scale;x++)SessionInfoRenderCheck.require(off.getRGB(x,y)==glow.getRGB(x,y),"Custom glow leaked into interior");
  SessionInfoRenderCheck.backdrop();surface.render(shape,theme,false,true,false,.75f,()->{});BufferedImage blur=SessionInfoRenderCheck.capture();int changed=0;for(int y=0;y<blur.getHeight();y++)for(int x=0;x<blur.getWidth();x++){boolean outside=x<28*scale||x>222*scale||y<38*scale||y>112*scale;if(outside)SessionInfoRenderCheck.require(off.getRGB(x,y)==blur.getRGB(x,y),"Custom blur changed exterior");else if(off.getRGB(x,y)!=blur.getRGB(x,y))changed++;}SessionInfoRenderCheck.require(changed>100,"Custom blur absent");surface.close();
  TextEffects effects=new TextEffects();SessionInfoRenderCheck.backdrop();BufferedImage scene=SessionInfoRenderCheck.capture();effects.draw(Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Bold 28"),"Transparent",30,40,0x00ffffff,0x00ffffff,2,false,0,true,1,4,false);SessionInfoRenderCheck.require(WatermarkCheck.equal(scene,SessionInfoRenderCheck.capture()),"Zero-alpha glyph palette still visible");effects.close();
 }
}
