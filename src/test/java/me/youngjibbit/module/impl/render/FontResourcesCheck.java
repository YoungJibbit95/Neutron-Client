package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.font.*;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.renderer.GlStateManager;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.lwjgl.opengl.GL11.*;

/** Compares the unchanged eager rasterizer against lazy production fonts, with actual texture lifetime checks. */
public final class FontResourcesCheck {
 static class Manager extends FontManager {
  final Map<String,Integer> opens=new HashMap<>(),closes=new HashMap<>();
  @Override protected InputStream openFont(String file){opens.put(file,opens.getOrDefault(file,0)+1);InputStream stream=super.openFont(file);if(stream==null)return null;return new FilterInputStream(stream){@Override public void close()throws IOException{super.close();closes.put(file,closes.getOrDefault(file,0)+1);}};}
 }
 static BufferedImage draw(TTFFontRenderer renderer,String text,boolean shadow){SessionInfoRenderCheck.backdrop();GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);if(shadow)renderer.drawStringWithShadow(text,30,40,-1);else renderer.drawString(text,30,40,-1);return SessionInfoRenderCheck.capture();}
 public static void run()throws Exception{
  FontManager reference=Pulsar.INSTANCE.fontManager;RenderDiagnostics.enabled=true;RenderDiagnostics.reset();Manager manager=new Manager();
  SessionInfoRenderCheck.require(RenderDiagnostics.fontRendererCreates==0&&RenderDiagnostics.fontFileLoads==0&&RenderDiagnostics.glyphTexturesCreated==0,"FontManager eagerly creates resources");
  TTFFontRenderer lazy=manager.getFont("Poppins-Regular 18");SessionInfoRenderCheck.require(RenderDiagnostics.fontFileLoads==1&&RenderDiagnostics.glyphTexturesCreated==0,"Font lookup rasterized unused glyphs");
  lazy.getWidth("Regular");SessionInfoRenderCheck.require(RenderDiagnostics.glyphTexturesCreated==0,"Metrics allocate glyph textures");draw(lazy,"Regular",false);SessionInfoRenderCheck.require(RenderDiagnostics.glyphTexturesCreated==256,"Unused bold/italic styles were allocated");draw(lazy,"\u00a7lBold",false);SessionInfoRenderCheck.require(RenderDiagnostics.glyphTexturesCreated==512,"Bold style is not lazy");draw(lazy,"\u00a7oItalic",false);SessionInfoRenderCheck.require(RenderDiagnostics.glyphTexturesCreated==768,"Italic style is not lazy");
  for(String key:new String[]{"Poppins-Regular 18","Product-Sans-Regular 20"}){
   TTFFontRenderer expected=reference.getFont(key),actual=manager.getFont(key);
   for(String text:new String[]{"Neutron 123", "\u00a7lBold \u00a7rRegular", "\u00a7oItalic \u00a7aColor", "\u00c4\u00d6\u00dc\u00df + - |", "tail\u00a7"}){
    SessionInfoRenderCheck.require(Math.abs(expected.getWidth(text)-actual.getWidth(text))<.0001&&Math.abs(expected.getHeight(text)-actual.getHeight(text))<.0001,"Font metrics changed: "+key+" / "+text);
    for(boolean shadow:new boolean[]{false,true})SessionInfoRenderCheck.require(WatermarkCheck.equal(draw(expected,text,shadow),draw(actual,text,shadow)),"Glyph pixels/formatting/shadow changed: "+key+" / "+text);
   }
  }
  TTFFontRenderer thirty=manager.getFont("Poppins-Regular 30");SessionInfoRenderCheck.require(thirty==manager.getFont("Poppins-Regular 30")&&manager.opens.get("Poppins-Regular.ttf")==1,"Duplicate size or repeated font file load");
  SessionInfoRenderCheck.require(manager.opens.equals(manager.closes),"Font file stream leak");SessionInfoRenderCheck.require(manager.getFont("missing-face 20")==manager.getFont("missing-face 30"),"Unknown font fallback changed");
  AtomicReference<Throwable> workerFailure=new AtomicReference<>();AtomicBoolean metricsWorked=new AtomicBoolean();Thread worker=new Thread(()->{try{manager.getFont("Poppins-Regular 18").getWidth("Worker metrics");metricsWorked.set(true);lazy.drawString("Forbidden GPU upload",0,0,-1);}catch(Throwable ex){workerFailure.set(ex);}});worker.start();worker.join();SessionInfoRenderCheck.require(metricsWorked.get()&&workerFailure.get() instanceof IllegalStateException,"Off-thread font drawing accepted");
  long warm=RenderDiagnostics.glyphTexturesCreated;for(int i=0;i<8;i++)draw(lazy,"Warm \u00a7lBold \u00a7oItalic",true);SessionInfoRenderCheck.require(RenderDiagnostics.glyphTexturesCreated==warm,"Font textures grow per frame");
  Set<Integer> owned=(Set<Integer>)SessionInfoRenderCheck.get(lazy,"ownedTextures");int id=owned.iterator().next();GlStateManager.bindTexture(id);glBindTexture(GL_TEXTURE_2D,id);manager.close();manager.close();SessionInfoRenderCheck.require(!glIsTexture(id)&&glGetInteger(GL_TEXTURE_BINDING_2D)==0&&RenderDiagnostics.glyphTexturesCreated==RenderDiagnostics.glyphTexturesDeleted,"Font textures not released");
  try{lazy.getWidth("closed");throw new AssertionError("Closed renderer still usable");}catch(IllegalStateException expected){}
  System.out.println("Fonts used: files="+RenderDiagnostics.fontFileLoads+" renderers="+RenderDiagnostics.fontRendererCreates+" glyph textures="+RenderDiagnostics.glyphTexturesCreated+" deleted="+RenderDiagnostics.glyphTexturesDeleted);
  RenderDiagnostics.reset();java.util.concurrent.ExecutorService preparation=java.util.concurrent.Executors.newFixedThreadPool(2);java.util.concurrent.ConcurrentLinkedQueue<TextureData> queue=new java.util.concurrent.ConcurrentLinkedQueue<>();TTFFontRenderer pending=new TTFFontRenderer(preparation,queue,new java.awt.Font("Verdana",java.awt.Font.PLAIN,18),8);pending.close();preparation.shutdown();SessionInfoRenderCheck.require(queue.isEmpty()&&RenderDiagnostics.glyphTexturesCreated==24&&RenderDiagnostics.glyphTexturesDeleted==24,"Pending legacy uploads outlived their owner");
  RenderDiagnostics.reset();for(int i=0;i<3;i++){FontManager reopened=new FontManager();draw(reopened.getFont("Poppins-Regular 18"),"Reopen",false);reopened.close();}SessionInfoRenderCheck.require(RenderDiagnostics.glyphTexturesCreated==768&&RenderDiagnostics.glyphTexturesDeleted==768,"Font reopen lifetime leak");RenderDiagnostics.enabled=false;
  SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Font resource GL error");System.out.println("PASS: zero eager fonts, one file load per face, lazy styles, exact metrics/pixels/formatting/shadows, owner thread, bounded texture cache, close/reopen");
 }
}
