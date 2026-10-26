package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import java.awt.Color;
import java.awt.image.BufferedImage;
import static org.lwjgl.opengl.GL11.*;
public final class SharedCompositionCheck {
 static void run()throws Exception{
  Minecraft mc=SessionInfoRenderCheck.mc;ModuleManager old=Pulsar.INSTANCE.getMm();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());ClientSettings c=ClientSettings.current();ArrayListSurface root=new ArrayListSurface();ArrayListSurface[] children=new ArrayListSurface[20];for(int i=0;i<children.length;i++)children[i]=new ArrayListSurface();
  try{
   mc.displayWidth=1000;mc.displayHeight=700;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(1000,700);c.surface.setValue("Blur");c.gradientMotion.setValue("Static");c.reducedMotion.setValue(true);c.glow.setValue(false);c.border.setValue(false);ArrayListSurface.releaseResources();
   RenderDiagnostics.enabled=true;RenderDiagnostics.reset();group(root,children,1);long passes=RenderDiagnostics.blurPasses;RenderDiagnostics.reset();group(root,children,20);SessionInfoRenderCheck.require(RenderDiagnostics.blurPasses==passes&&passes==2,"Sibling panels perform repeated scene blur");SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0,"Borrowed clean scenes allocate per sibling");
   c.glow.setValue(true);group(root,children,20);Framebuffer[] first=(Framebuffer[])SessionInfoRenderCheck.get(children[0],"buffers");for(ArrayListSurface child:children){Framebuffer[] b=(Framebuffer[])SessionInfoRenderCheck.get(child,"buffers");for(int i=0;i<3;i++)SessionInfoRenderCheck.require(b[i]==first[i],"Mask/scratch/halo not shared");SessionInfoRenderCheck.require(b[3]==null&&b[4]==null,"Borrower owns redundant scene targets");}
   RenderDiagnostics.enabled=false;
   // Existing glyph textures must carry their vertex color through the production vertex shader.
   SessionInfoRenderCheck.backdrop();TextEffects.direct(Theme.SynthWave,()->Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 20").drawString("Glyph color",30,30,-1));BufferedImage text=SessionInfoRenderCheck.capture();int bright=0;for(int y=60;y<85;y++)for(int x=60;x<260;x++){int rgb=text.getRGB(x,y);if((rgb>>16&255)>130||(rgb&255)>150)bright++;}SessionInfoRenderCheck.require(bright>50,"Direct gradient glyphs are black/invisible");
   c.roundness.setValue(18.0);c.glowIntensity.setValue(.5);BufferedImage low=world();c.glowIntensity.setValue(1.5);BufferedImage high=world();long lowGreen=0,highGreen=0;int fixed=0,themed=0;for(int y=20;y<150;y++)for(int x=20;x<500;x++){float gx=(x+.5f)/2,gy=(y+.5f)/2;boolean outside=gy<30||gy>60||gx<30||gx>100;if(!outside)continue;int a=low.getRGB(x,y),b=high.getRGB(x,y);if(gx<125){lowGreen+=a>>8&255;highGreen+=b>>8&255;if((b>>8&255)>(b>>16&255)+3&&(b>>8&255)>(b&255)+3)fixed++;}else if((b&255)>10||(b>>16&255)>10)themed++;}SessionInfoRenderCheck.require(highGreen>lowGreen&&fixed>50&&themed>50,"Fixed world/team bloom color, intensity or second role lost");
   net.minecraft.client.renderer.GlStateManager.enableFog();try(RenderState state=new RenderState()){net.minecraft.client.renderer.GlStateManager.disableFog();}SessionInfoRenderCheck.require(glIsEnabled(GL_FOG),"Fog state not restored");net.minecraft.client.renderer.GlStateManager.disableFog();SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Shared composition GL error");
   System.out.println("PASS: 1/20 sibling panels share one scene blur and effect targets; direct gradient glyph colors; fixed world/team plus themed bloom jobs, real intensity, exact geometry and fog restoration");
  }finally{RenderDiagnostics.enabled=false;root.close();for(ArrayListSurface child:children)child.close();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
 static void group(ArrayListSurface root,ArrayListSurface[] children,int count){SessionInfoRenderCheck.backdrop();root.withScene(Theme.SynthWave,()->{for(int i=0;i<count;i++){final int n=i;children[i].panel(15+n%5*90,20+n/5*60,80,45,5,Theme.SynthWave,true,true,true,5,.5f,.5f,()->{});}});}
 static BufferedImage world(){SessionInfoRenderCheck.backdrop();glClearColor(0,0,0,1);glClear(GL_COLOR_BUFFER_BIT);GuiShaderBloom.beginBatch();GuiShaderBloom.queueWorldRect(30,30,70,30,0,0xff00ff00,true);GuiShaderBloom.queueRoundedRect(160,30,70,30,5,0xffffffff);GuiShaderBloom.flushBatch(2,2);return SessionInfoRenderCheck.capture();}
}
