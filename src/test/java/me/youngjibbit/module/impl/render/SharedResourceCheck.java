package me.youngjibbit.module.impl.render;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import static org.lwjgl.opengl.GL11.*;
public final class SharedResourceCheck {
 static void release(){ArrayListSurface.releaseResources();TextEffects.releaseResources();Blur.releaseResources();GuiShaderBloom.releaseResources();UIUtils.releaseResources();GaussianBlur.releaseResources();}
 static void frame(ArrayListSurface surface,TextEffects text){SessionInfoRenderCheck.backdrop();surface.panel(25,30,130,70,5,Theme.SynthWave,true,true,true,5,.5f,1,()->text.draw(Pulsar.INSTANCE.fontManager.getFont("Product-Sans-Bold 20"),"Resources",35,45,-1,0xff00ffff,1,false,0,true,1,4,false));Blur.startBlur();UIUtils.drawRound(190,30,100,50,5,java.awt.Color.WHITE);Blur.endBlur(5,1);GuiShaderBloom.renderRect(200,120,80,25,5,0xff00ffff,2,2);}
 static void run()throws Exception{
  RenderStateFramebufferCheck.run();
  Minecraft mc=SessionInfoRenderCheck.mc;ModuleManager old=Pulsar.INSTANCE.getMm();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",new ModuleManager());ClientSettings c=ClientSettings.current();
  try{c.surface.setValue("Liquid Glass");c.blurMethod.setValue("Kawase");mc.displayWidth=960;mc.displayHeight=640;mc.gameSettings.guiScale=2;mc.getFramebuffer().createBindFramebuffer(960,640);release();
   for(int cycle=0;cycle<2;cycle++){
    ArrayListSurface surface=new ArrayListSurface();TextEffects text=new TextEffects();RenderDiagnostics.enabled=true;RenderDiagnostics.reset();frame(surface,text);long creates=RenderDiagnostics.framebufferCreates,programs=RenderDiagnostics.programCreates;
    for(int i=0;i<5;i++)frame(surface,text);SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==creates&&RenderDiagnostics.programCreates==programs,"Shared warm effects allocate");
    Framebuffer[] targets=(Framebuffer[])SessionInfoRenderCheck.get(surface,"buffers");int[] textures=new int[targets.length];for(int i=0;i<textures.length;i++)textures[i]=targets[i].framebufferTexture;
    org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);net.minecraft.client.renderer.GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,textures[4]);net.minecraft.client.renderer.GlStateManager.bindTexture(textures[4]);surface.close();
    for(int i=3;i<textures.length;i++)SessionInfoRenderCheck.require(!glIsTexture(textures[i]),"Deleted owned scene texture resurrected by state restoration");
    release();for(int id:textures)SessionInfoRenderCheck.require(!glIsTexture(id),"Shared effect texture survived its owner release");SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"Shared framebuffer leak on release");SessionInfoRenderCheck.require(RenderDiagnostics.programCreates==RenderDiagnostics.programDeletes,"Shared program leak on release");SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Release/reopen GL error");System.out.println("Shared resource cycle "+cycle+": "+RenderDiagnostics.snapshot());RenderDiagnostics.enabled=false;
   }
   System.out.println("PASS: shared surfaces/text/legacy masks/bloom/scene shaders release idempotently and reopen; no texture resurrection; steady resource counts");
  }finally{RenderDiagnostics.enabled=false;release();SessionInfoRenderCheck.set(Pulsar.INSTANCE,"mm",old);UiEffects.invalidate();}
 }
}
