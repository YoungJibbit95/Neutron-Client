package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.clickgui.component.impl.*;
import me.youngjibbit.gui.iosUi.iOS;
import me.youngjibbit.utils.render.UiEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
public final class MotionRenderCheck {
 static void run()throws Exception{
  ModuleManager old=Pulsar.INSTANCE.getMm();Object oldIos=SessionInfoRenderCheck.get(Pulsar.INSTANCE,"iOS");Minecraft mc=SessionInfoRenderCheck.mc;
  SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());ClientSettings c=ClientSettings.current();iOS ios=new iOS();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"iOS",ios);
  try{
   SessionInfoRenderCheck.set(mc,"renderEngine",new net.minecraft.client.renderer.texture.TextureManager(mc.getResourceManager()));mc.fontRendererObj=new net.minecraft.client.gui.FontRenderer(mc.gameSettings,new net.minecraft.util.ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
   mc.displayWidth=1000;mc.displayHeight=700;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1000,700);ScaledResolution sr=new ScaledResolution(mc);ios.setWorldAndResolution(mc,sr.getScaledWidth(),sr.getScaledHeight());
   for(boolean reduced:new boolean[]{false,true}){
    c.animations.setValue(reduced);c.reducedMotion.setValue(reduced);UiEffects.beginFrame();
    ClickModule module=new ClickModule(c,10,20,240,24);SessionInfoRenderCheck.set(module,"open",true);module.updateAnimation();SessionInfoRenderCheck.require((float)SessionInfoRenderCheck.get(module,"openProgress")==1,"Module expansion ignores reduced/off motion");SessionInfoRenderCheck.set(module,"open",false);module.updateAnimation();SessionInfoRenderCheck.require((float)SessionInfoRenderCheck.get(module,"openProgress")==0,"Module collapse ignores reduced/off motion");
    ClickCombo combo=new ClickCombo(c.direction,10,20,240,24);SessionInfoRenderCheck.set(combo,"open",true);combo.updateAnimation();SessionInfoRenderCheck.require((float)SessionInfoRenderCheck.get(combo,"progress")==1,"Combo expansion ignores reduced/off motion");
    me.youngjibbit.gui.iosUi.component.impl.iOSModule iosRow=new me.youngjibbit.gui.iosUi.component.impl.iOSModule(c,10,20,300,24);SessionInfoRenderCheck.set(iosRow,"open",true);float expanded=iosRow.getHeight();SessionInfoRenderCheck.require(expanded>24,"iOS expansion ignores reduced/off motion");SessionInfoRenderCheck.set(iosRow,"open",false);SessionInfoRenderCheck.require(iosRow.getHeight()==24,"iOS collapsed layout still animates");
    me.youngjibbit.gui.iosUi.component.impl.iOSCombo iosCombo=new me.youngjibbit.gui.iosUi.component.impl.iOSCombo(c.direction,10,20,300,24);SessionInfoRenderCheck.set(iosCombo,"open",true);SessionInfoRenderCheck.require(iosCombo.getHeight()>24,"iOS option list still animates");
    ios.initGui();SessionInfoRenderCheck.backdrop();ios.drawScreen(-1,-1,0);SessionInfoRenderCheck.require(Math.abs((float)SessionInfoRenderCheck.get(ios,"renderedScale")-Math.min(1,Math.min((sr.getScaledWidth()-16)/350f,(sr.getScaledHeight()-16)/200f)))<.001,"iOS opening scale did not snap");
    java.util.List<?> stars=(java.util.List<?>)SessionInfoRenderCheck.get(ios,"particles");Object star=stars.get(0);float x=(float)SessionInfoRenderCheck.get(star,"x"),y=(float)SessionInfoRenderCheck.get(star,"y");Thread.sleep(20);SessionInfoRenderCheck.backdrop();ios.drawScreen(99,55,0);SessionInfoRenderCheck.require(x==(float)SessionInfoRenderCheck.get(star,"x")&&y==(float)SessionInfoRenderCheck.get(star,"y"),"Reduced/off particle motion still advances");
   }
   System.out.println("PASS: actual module/combo expansion, iOS opening and particles respect motion off and reduced motion without resetting on reversal");
  }finally{ios.onGuiClosed();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"iOS",oldIos);SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
}
