package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.client.WatermarkHUDModule;
import me.youngjibbit.utils.font.*;
import me.youngjibbit.utils.render.*;
import java.io.File;
import java.lang.reflect.*;
import java.awt.image.BufferedImage;
import java.util.*;
import static org.lwjgl.opengl.GL11.*;
public final class WatermarkCheck {
 public static void run()throws Exception{
  Method fonts=Class.forName("me.youngjibbit.clickgui.ClickGuiRenderCheck").getDeclaredMethod("minimalFonts",String[].class);fonts.setAccessible(true);
  Pulsar.INSTANCE.fontManager=(FontManager)fonts.invoke(null,(Object)new String[]{"Product-Sans-Medium 41","Poppins-Bold 23","SFProText-Medium 18","KhmerOureang 20","DmSansMedium 18"});
  SessionInfoRenderCheck.set(SessionInfoRenderCheck.mc,"renderEngine",new net.minecraft.client.renderer.texture.TextureManager(SessionInfoRenderCheck.mc.getResourceManager()));
  WatermarkHUDModule hud=new WatermarkHUDModule();hud.getModeSetting("Effects Source").setValue("Local");Method draw=WatermarkHUDModule.class.getDeclaredMethod("renderWatermark");draw.setAccessible(true);
  java.util.List<BufferedImage> images=new ArrayList<>();java.util.List<String> captions=new ArrayList<>();
  for(String mode:hud.getModeSetting("Mode").getOptions()){
   hud.getModeSetting("Mode").setValue(mode);SessionInfoRenderCheck.backdrop();GuiShaderBloom.beginBatch();draw.invoke(hud);GuiShaderBloom.flushBatch(2,3);
   SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Watermark GL error: "+mode);
   images.add(SessionInfoRenderCheck.capture());captions.add(mode);
  }
  SessionInfoRenderCheck.sheet(Boolean.getBoolean("pulsar.watermark.baseline")?"watermarks-before.png":"watermarks.png",captions,images);
  if(Boolean.getBoolean("pulsar.watermark.baseline"))return;
  for(String field:new String[]{"Client Name","Version","FPS","Player"})hud.getBooleanSetting(field).setValue(false);
  hud.getModeSetting("Mode").setValue("Status");SessionInfoRenderCheck.backdrop();BufferedImage off=SessionInfoRenderCheck.capture();draw.invoke(hud);SessionInfoRenderCheck.require(equal(off,SessionInfoRenderCheck.capture()),"Empty watermark left a panel");
  for(String field:new String[]{"Client Name","Version","FPS","Player"})hud.getBooleanSetting(field).setValue(true);
  TTFFontRenderer font=Pulsar.INSTANCE.getFontManager().getFont("Product-Sans-Medium 41");SessionInfoRenderCheck.require(Math.abs(font.getWidth("\u00a7lA\u00a7cB")-(font.getWidth("\u00a7lA")+font.getWidth("B")-2))<.001,"Formatting width reset");font.getHeight("tail\u00a7");
  TextEffects effects=new TextEffects();images.clear();captions.clear();
  for(int style=0;style<3;style++){
   SessionInfoRenderCheck.backdrop();effects.drawLocal(font,"Neutron \u00a7lB \u00a7r\u00a7oI \u00a7r\u00a7aGreen \u00a7r\u00b7",20,40,0xffff00ff,0xff00cfff,style,false,0,true,.6f,4,true,false,true);
   SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Text style GL error: "+style);
   images.add(SessionInfoRenderCheck.capture());captions.add(new String[]{"Solid","Horizontal","Vertical"}[style]);
  }
  SessionInfoRenderCheck.sheet("text-effects.png",captions,images);
  SessionInfoRenderCheck.backdrop();effects.drawLocal(font,"MMMMM",20,40,0xffff00ff,0xff00ffff,2,false,0,false,0,4,true,false,false);BufferedImage vertical=SessionInfoRenderCheck.capture();
  double top=0,bottom=0;int nt=0,nb=0;int sf=new net.minecraft.client.gui.ScaledResolution(SessionInfoRenderCheck.mc).getScaleFactor();
  for(int y=40*sf;y<65*sf;y++)for(int x=20*sf;x<120*sf;x++){int rgb=vertical.getRGB(x,y);int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(b>180&&r+g>180){if(y<50*sf){top+=r;nt++;}else{bottom+=r;nb++;}}}
  SessionInfoRenderCheck.require(nt>10&&nb>10&&top/nt>bottom/nb+30,"Gradient is not vertical inside glyphs");
  RenderDiagnostics.enabled=true;effects.draw(font,"Warm",20,40,-1,0xff00ffff,1,false,0,true,.5f,4,false);RenderDiagnostics.reset();
  for(int i=0;i<8;i++)effects.draw(font,i%2==0?"Short":"A considerably longer text",20,40,-1,0xff00ffff,1,false,0,true,.5f,4,false);
  SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0&&RenderDiagnostics.shaderCompiles==0&&RenderDiagnostics.maskPasses==8&&RenderDiagnostics.blurPasses==16&&RenderDiagnostics.composites==8,"Text effects resource/pass growth");
  System.out.println("Text steady 8 strings: "+RenderDiagnostics.snapshot());
  java.util.List<RenderState> nested=new ArrayList<>();for(int i=0;i<7;i++)nested.add(new RenderState());for(int i=nested.size()-1;i>=0;i--)nested.get(i).close();
  RenderDiagnostics.reset();
  for(int i=0;i<4;i++){SessionInfoRenderCheck.mc.displayWidth=i%2==0?960:1100;SessionInfoRenderCheck.mc.displayHeight=i%2==0?600:640;SessionInfoRenderCheck.mc.getFramebuffer().createBindFramebuffer(SessionInfoRenderCheck.mc.displayWidth,SessionInfoRenderCheck.mc.displayHeight);SessionInfoRenderCheck.backdrop();effects.draw(font,"Resize",20,40,-1,0xff00ffff,2,false,0,true,.5f,4,false);}
  SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"Text resize targets not released");
  System.out.println("Text 4 resizes: "+RenderDiagnostics.snapshot());effects.close();hud.onDisable();RenderDiagnostics.enabled=false;
  SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Text/watermark GL error");System.out.println("PASS: nine watermark modes, optional fields/empty state, formatted glyph masks, vertical gradient pixels, constant passes and cached targets");
 }
 static boolean equal(BufferedImage a,BufferedImage b){return Arrays.equals(a.getRGB(0,0,a.getWidth(),a.getHeight(),null,0,a.getWidth()),b.getRGB(0,0,b.getWidth(),b.getHeight(),null,0,b.getWidth()));}
}
