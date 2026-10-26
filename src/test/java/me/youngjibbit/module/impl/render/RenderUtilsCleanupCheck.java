package me.youngjibbit.module.impl.render;
import me.youngjibbit.utils.render.*;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import me.youngjibbit.utils.render.shader.KawaseBloom;
import me.youngjibbit.utils.render.shader.StencilUtil;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.EXTFramebufferObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/** Contracts that differ across the former shader families: ownership, UV origin and rectangle dimensions. */
public final class RenderUtilsCleanupCheck {
 private static void drawMenu(int mode,long time){
  switch(mode){case 1:me.youngjibbit.gui.MenuShader.mm1(time);break;case 2:me.youngjibbit.gui.MenuShader.mm2(time);break;case 3:me.youngjibbit.gui.MenuShader.mm3(time);break;case 4:me.youngjibbit.gui.MenuShader.mm4(time);break;case 5:me.youngjibbit.gui.MenuShader.mm5(time);break;case 6:me.youngjibbit.gui.MenuShader.mm6(time);break;default:throw new AssertionError(mode);}
 }
 private static void checkMenuResources()throws Exception{
  try(RenderState state=new RenderState()){
   RenderDiagnostics.reset();
   for(int i=0;i<3;i++){
    SessionInfoRenderCheck.backdrop();BufferedImage before=SessionInfoRenderCheck.capture();int id;
    try(me.youngjibbit.utils.shader.GLSLSandboxShaders shader=new me.youngjibbit.utils.shader.GLSLSandboxShaders("/assets/minecraft/pulsar/shader/mainmenu/shader.fsh")){
     shader.useShader(SessionInfoRenderCheck.mc.displayWidth+i,SessionInfoRenderCheck.mc.displayHeight+i,17,23,4);id=glGetInteger(GL_CURRENT_PROGRAM);
     SessionInfoRenderCheck.require(glGetProgrami(id,GL_ATTACHED_SHADERS)==0,"Native menu retained shader objects");
     glDisable(GL_DEPTH_TEST);glDisable(GL_ALPHA_TEST);glDisable(GL_BLEND);glBegin(GL_QUADS);glVertex2f(-1,-1);glVertex2f(-1,1);glVertex2f(1,1);glVertex2f(1,-1);glEnd();glUseProgram(0);
     BufferedImage after=SessionInfoRenderCheck.capture();SessionInfoRenderCheck.require(before.getRGB(40,40)!=after.getRGB(40,40),"Native menu resource drew no pixels");
    }
    SessionInfoRenderCheck.require(!glIsProgram(id),"Native menu program survived close/reopen");
   }
   SessionInfoRenderCheck.require(RenderDiagnostics.programCreates==3&&RenderDiagnostics.programDeletes==3&&RenderDiagnostics.shaderCompiles==RenderDiagnostics.shaderDeletes,"Native menu resource leak");
   me.youngjibbit.gui.MenuShader.releaseResources();RenderDiagnostics.reset();state.screenSpace();long time=System.currentTimeMillis()-1000;
   for(int mode=1;mode<=6;mode++)drawMenu(mode,time);
   SessionInfoRenderCheck.require(RenderDiagnostics.programCreates==6&&RenderDiagnostics.framebufferCreates==1,"Menu resources not lazy/shared");
   long programs=RenderDiagnostics.programCreates,fbos=RenderDiagnostics.framebufferCreates;
   for(int mode=1;mode<=6;mode++)drawMenu(mode,time);
   SessionInfoRenderCheck.require(RenderDiagnostics.programCreates==programs&&RenderDiagnostics.framebufferCreates==fbos,"Menu warm draws reallocate");
   me.youngjibbit.gui.MenuShader.releaseResources();me.youngjibbit.gui.MenuShader.releaseResources();
   SessionInfoRenderCheck.require(RenderDiagnostics.programCreates==RenderDiagnostics.programDeletes&&RenderDiagnostics.shaderCompiles==RenderDiagnostics.shaderDeletes&&RenderDiagnostics.framebufferCreates==RenderDiagnostics.framebufferDeletes,"Menu resources survived release");
  }
 }
 private static void checkGaussian(){
  try(RenderState guard=new RenderState()){
   SessionInfoRenderCheck.backdrop();Framebuffer target=new Framebuffer(SessionInfoRenderCheck.mc.displayWidth,SessionInfoRenderCheck.mc.displayHeight,false);SessionInfoRenderCheck.mc.getFramebuffer().bindFramebuffer(true);
   GaussianBlur.renderPass(target,SessionInfoRenderCheck.mc.getFramebuffer().framebufferTexture,1,0,7,false,1);
   GaussianBlur.renderPass(target,SessionInfoRenderCheck.mc.getFramebuffer().framebufferTexture,0,1,7,true,1.4f);
   GlStateManager.enableDepth();GlStateManager.enableAlpha();GlStateManager.enableBlend();GlStateManager.depthMask(false);glEnable(GL_SCISSOR_TEST);glScissor(7,9,80,60);
   int fbo=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);RenderDiagnostics.reset();
   for(int i=0;i<8;i++)GaussianBlur.renderPass(target,SessionInfoRenderCheck.mc.getFramebuffer().framebufferTexture,1,0,7,i%2==0,1);
   SessionInfoRenderCheck.require(glIsEnabled(GL_DEPTH_TEST)&&glIsEnabled(GL_ALPHA_TEST)&&glIsEnabled(GL_BLEND)&&glIsEnabled(GL_SCISSOR_TEST)&&!glGetBoolean(GL_DEPTH_WRITEMASK)&&fbo==glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT),"Gaussian pass leaked caller state");
   SessionInfoRenderCheck.require(RenderDiagnostics.framebufferCreates==0&&RenderDiagnostics.programCreates==0&&RenderDiagnostics.blurPasses==8,"Gaussian pass allocates per call");
   target.deleteFramebuffer();GaussianBlur.releaseResources();
  }
  int down=KawaseBloom.kawaseDown.getProgramId(),up=KawaseBloom.kawaseUp.getProgramId();RenderDiagnostics.reset();ESP visual=new ESP();for(int i=0;i<3;i++){visual.setToggled(true);visual.setToggled(false);}
  SessionInfoRenderCheck.require(KawaseBloom.kawaseDown.getProgramId()==down&&KawaseBloom.kawaseUp.getProgramId()==up&&RenderDiagnostics.programCreates==0,"ESP replaced the shared bloom owner");
 }
 private static void checkStencil(){
  try(RenderState state=new RenderState()){
   Framebuffer small=new Framebuffer(96,64,true);int other=EXTFramebufferObject.glGenRenderbuffersEXT();
   for(int i=0;i<3;i++){
    if(i>0)small.createBindFramebuffer(96+i*7,64+i*5);
    SessionInfoRenderCheck.mc.getFramebuffer().bindFramebuffer(true);int caller=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT);
    EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT,other);StencilUtil.checkSetupFBO(small);int packed=small.depthBuffer;
    SessionInfoRenderCheck.require(packed>0&&EXTFramebufferObject.glIsRenderbufferEXT(packed),"Packed stencil owner lost");
    SessionInfoRenderCheck.require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT)==caller&&glGetInteger(EXTFramebufferObject.GL_RENDERBUFFER_BINDING_EXT)==other,"Stencil allocation leaked bindings");
    StencilUtil.checkSetupFBO(small);SessionInfoRenderCheck.require(small.depthBuffer==packed,"Stencil allocated every use");
    small.bindFramebuffer(false);SessionInfoRenderCheck.require(EXTFramebufferObject.glCheckFramebufferStatusEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT)==EXTFramebufferObject.GL_FRAMEBUFFER_COMPLETE_EXT,"Stencil dimensions do not match owning FBO");
   }
   int packed=small.depthBuffer;small.deleteFramebuffer();SessionInfoRenderCheck.require(!EXTFramebufferObject.glIsRenderbufferEXT(packed),"Packed stencil survived framebuffer deletion");EXTFramebufferObject.glDeleteRenderbuffersEXT(other);
   SessionInfoRenderCheck.backdrop();BufferedImage before=SessionInfoRenderCheck.capture();StencilUtil.initStencilToWrite();net.minecraft.client.gui.Gui.drawRect(30,40,90,70,-1);StencilUtil.readStencilBuffer(1);net.minecraft.client.gui.Gui.drawRect(0,0,200,140,0xffff0000);StencilUtil.uninitStencilBuffer();BufferedImage after=SessionInfoRenderCheck.capture();int scale=new net.minecraft.client.gui.ScaledResolution(SessionInfoRenderCheck.mc).getScaleFactor();
   SessionInfoRenderCheck.require((after.getRGB(50*scale,50*scale)&0xffffff)==0xff0000&&before.getRGB(10,10)==after.getRGB(10,10),"Stencil write/read contract changed");
  }
 }
 private static class Stream extends ByteArrayInputStream {boolean closed;Stream(String text){super(text.getBytes(StandardCharsets.UTF_8));}@Override public void close(){closed=true;}}
 public static void run()throws Exception{
  RenderDiagnostics.enabled=true;RenderDiagnostics.reset();
  Stream fragment=new Stream("#version 120\nvoid main(){gl_FragColor=vec4(gl_TexCoord[0].st,0.0,1.0);}"),vertex=new Stream("#version 120\nvoid main(){gl_TexCoord[0]=gl_MultiTexCoord0;gl_Position=ftransform();}");
  int program;
  try(ShaderUtil shader=new ShaderUtil(fragment,vertex)){
   program=shader.getProgramId();SessionInfoRenderCheck.require(fragment.closed&&vertex.closed,"Shader streams not closed");SessionInfoRenderCheck.require(glGetProgrami(program,GL_ATTACHED_SHADERS)==0,"Compiled shader objects retained");
   SessionInfoRenderCheck.backdrop();shader.init();ShaderUtil.drawQuads(10,20,40,30);shader.unload();BufferedImage positioned=SessionInfoRenderCheck.capture();int scale=new net.minecraft.client.gui.ScaledResolution(SessionInfoRenderCheck.mc).getScaleFactor();int rgb=positioned.getRGB(15*scale,25*scale);SessionInfoRenderCheck.require((rgb>>16&255)<80&&(rgb>>8&255)<80,"Positioned quad UV/dimensions changed");
   shader.init();ShaderUtil.drawQuads();shader.unload();BufferedImage full=SessionInfoRenderCheck.capture();SessionInfoRenderCheck.require((full.getRGB(10,10)>>8&255)>240,"Framebuffer quad lost vertical UV flip");
  }
  SessionInfoRenderCheck.require(!glIsProgram(program)&&RenderDiagnostics.programCreates==RenderDiagnostics.programDeletes&&RenderDiagnostics.shaderCompiles==RenderDiagnostics.shaderDeletes,"Successful shader ownership leak");
  RenderDiagnostics.reset();Stream bad=new Stream("not GLSL"),good=new Stream("#version 120\nvoid main(){gl_Position=ftransform();}");
  try{new ShaderUtil(bad,good);throw new AssertionError("Invalid shader accepted");}catch(IllegalStateException expected){}
  SessionInfoRenderCheck.require(bad.closed&&good.closed&&RenderDiagnostics.programCreates==RenderDiagnostics.programDeletes&&RenderDiagnostics.shaderCompiles==RenderDiagnostics.shaderDeletes,"Failed shader ownership leak");
  checkMenuResources();checkGaussian();checkStencil();
  RenderDiagnostics.enabled=false;SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Shader cleanup GL error");System.out.println("PASS: shader UV/dimensions, stream/program ownership, native/six menu resources and warm reuse, shared Gaussian state, stencil masks/resize/release");
 }
}
