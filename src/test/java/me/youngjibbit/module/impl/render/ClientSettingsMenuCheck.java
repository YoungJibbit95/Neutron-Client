package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.gui.clientsettings.SettingsMenu;
import me.youngjibbit.clickgui.utils.ClipRect;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Keyboard;
import java.io.File;
import javax.imageio.ImageIO;
import static org.lwjgl.opengl.GL11.*;
/** Real menu handlers and Module Values, including footer keyboard navigation; no personal config. */
public final class ClientSettingsMenuCheck {
 static class Screen extends SettingsMenu {
  boolean control;GuiScreen destination;
  Screen(GuiScreen parent){super(parent);}
  void click(float x,float y,int b)throws Exception{mouseClicked((int)x,(int)y,b);}void key(char c,int k)throws Exception{keyTyped(c,k);}void release(){mouseReleased(0,0,0);}
  @Override protected boolean ctrl(){return control;}
  @Override protected void open(GuiScreen screen){destination=screen;}
  ClipRect row(int i){return controlBounds(i);}ClipRect action(int i){return actionBounds(i);}void actionClick(int id)throws Exception{ClipRect r=action(id);click(r.x+r.width/2,r.y+r.height/2,0);release();}
  void exact(int row,String number)throws Exception{ClipRect r=row(row);click(r.x+4,r.y+8,1);control=true;key('a',Keyboard.KEY_A);control=false;for(char c:number.toCharArray())key(c,Keyboard.KEY_NONE);key('\n',Keyboard.KEY_RETURN);}
 }
 public static void run()throws Exception {
  ModuleManager old=Pulsar.INSTANCE.getMm();net.minecraft.client.Minecraft mc=SessionInfoRenderCheck.mc;
  SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());ClientSettings settings=ClientSettings.current();GuiScreen parent=new GuiScreen(){};Screen menu=new Screen(parent);
  SessionInfoRenderCheck.set(mc,"renderEngine",new TextureManager(mc.getResourceManager()));mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
  try{
   mc.displayWidth=1100;mc.displayHeight=720;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1100,720);menu.setWorldAndResolution(mc,550,360);SessionInfoRenderCheck.backdrop();menu.drawScreen(-1,-1,0);
   menu.exact(0,"13.75");SessionInfoRenderCheck.require(settings.roundness.getValue()==13.75,"Exact settings value");menu.exact(0,"NaN");SessionInfoRenderCheck.require(settings.roundness.getValue()==13.75,"Invalid settings value applied");menu.key(' ',Keyboard.KEY_ESCAPE);
   menu.actionClick(2);SessionInfoRenderCheck.require(settings.roundness.getValue()==5,"Reset group");menu.actionClick(1);menu.actionClick(1); // Glow
   ClipRect r=menu.row(0);menu.click(r.x+4,r.y+8,0);SessionInfoRenderCheck.require(!settings.glow.getValue(),"Global checkbox not bound");menu.actionClick(1); // Gradients
   r=menu.row(1);menu.click(r.x+4,r.y+8,0);SessionInfoRenderCheck.require(!settings.synchronize.getValue(),"Sync checkbox");menu.key(' ',Keyboard.KEY_END);SessionInfoRenderCheck.require((float)SessionInfoRenderCheck.get(menu,"targetScroll")>0,"Long settings group does not scroll");
   menu.key(' ',Keyboard.KEY_TAB); // group previous button, then next/reset/preview/options/done
   for(int i=0;i<5;i++)menu.key(' ',Keyboard.KEY_TAB);menu.key(' ',Keyboard.KEY_RETURN);SessionInfoRenderCheck.require(menu.destination==parent,"Footer Done unreachable by keyboard");
   menu.control=true;menu.key('r',Keyboard.KEY_R);menu.control=false;SessionInfoRenderCheck.require(settings.glow.getValue()&&settings.synchronize.getValue(),"Reset all");
   for(int[] size:new int[][]{{1100,720,2},{640,480,1},{1001,701,3}}){mc.displayWidth=size[0];mc.displayHeight=size[1];mc.gameSettings.guiScale=size[2];mc.getFramebuffer().createBindFramebuffer(size[0],size[1]);ScaledResolution sr=new ScaledResolution(mc);menu.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());for(int i=0;i<5;i++){SessionInfoRenderCheck.backdrop();menu.drawScreen(-1,-1,0);}for(int id=0;id<6;id++){ClipRect a=menu.action(id);SessionInfoRenderCheck.require(a.x>=0&&a.y>=0&&a.x+a.width<=sr.getScaledWidth()&&a.y+a.height<=sr.getScaledHeight(),"Settings action outside resized screen");}}
   RenderDiagnostics.enabled=true;RenderDiagnostics.reset();for(int i=0;i<6;i++){SessionInfoRenderCheck.backdrop();menu.drawScreen(-1,-1,0);}SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"Settings menu reallocates steady resources");RenderDiagnostics.enabled=false;
   File out=new File("settings-preview");out.mkdirs();ImageIO.write(SessionInfoRenderCheck.capture(),"png",new File(out,"client-settings.png"));
   menu.actionClick(4);SessionInfoRenderCheck.require(menu.destination instanceof GuiOptions,"Vanilla game options lost");
   SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Settings menu GL error");System.out.println("PASS: real client settings exact/invalid/cancel/reset, conditional groups, scrolling, mouse and keyboard footer navigation, global Values, responsive actions and cached resources");
  }finally{menu.onGuiClosed();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
}
