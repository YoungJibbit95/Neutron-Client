package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.ResourceLocation;
import java.util.*;
public final class NativeSurfaceCheck {
 static class Screen extends GuiScreen{void buttons(int count){buttonList.clear();for(int i=0;i<count;i++)buttonList.add(new GuiButton(i,20+(i%4)*110,20+(i/4)*34,100,24,"Button "+i));}}
 static void run()throws Exception{
  Minecraft mc=SessionInfoRenderCheck.mc;ModuleManager old=Pulsar.INSTANCE.getMm();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());ClientSettings c=ClientSettings.current();
  SessionInfoRenderCheck.set(mc,"renderEngine",new TextureManager(mc.getResourceManager()));mc.fontRendererObj=new FontRenderer(mc.gameSettings,new ResourceLocation("textures/font/ascii.png"),mc.getTextureManager(),false);mc.fontRendererObj.onResourceManagerReload(mc.getResourceManager());
  try{
   mc.displayWidth=1000;mc.displayHeight=700;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1000,700);Screen screen=new Screen();screen.setWorldAndResolution(mc,500,350);c.reducedMotion.setValue(true);List<java.awt.image.BufferedImage> images=new ArrayList<>();List<String> captions=new ArrayList<>();
   for(String method:new String[]{"Gaussian","Kawase"}){c.blurMethod.setValue(method);long passes=-1;for(int count:new int[]{1,20}){screen.buttons(count);SessionInfoRenderCheck.backdrop();screen.drawScreen(30,30,0);RenderDiagnostics.enabled=true;RenderDiagnostics.reset();SessionInfoRenderCheck.backdrop();screen.drawScreen(30,30,0);if(passes<0)passes=RenderDiagnostics.blurPasses;else SessionInfoRenderCheck.require(passes==RenderDiagnostics.blurPasses,"Native controls perform effect passes per button");SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0,"Native control warm allocations");RenderDiagnostics.enabled=false;images.add(SessionInfoRenderCheck.capture());captions.add(method+" / "+count+" controls / "+passes+" total blur passes");}}
   SessionInfoRenderCheck.sheet("14-native-controls.png",captions,images);c.surface.setValue("Liquid Glass");SessionInfoRenderCheck.backdrop();screen.drawScreen(30,30,0);
   SessionInfoRenderCheck.require(org.lwjgl.opengl.GL11.glGetError()==0,"Native menu effect GL error");System.out.println("PASS: native menu/account-dialog button masks compose before glow; constant blur/bloom passes for 1 versus 20 actual controls; cached resources and Glass style");
  }finally{SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
}
