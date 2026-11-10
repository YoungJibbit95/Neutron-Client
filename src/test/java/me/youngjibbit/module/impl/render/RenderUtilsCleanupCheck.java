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
    /** Compare the batched scene with the former independently guarded pass sequence. */
    private static void checkSceneBatch() throws Exception {
        me.youngjibbit.module.ModuleManager previous = me.youngjibbit.Pulsar.INSTANCE.getMm();
        SessionInfoRenderCheck.set(me.youngjibbit.Pulsar.INSTANCE, "mm", new me.youngjibbit.module.ModuleManager());
        me.youngjibbit.module.impl.client.ClientSettings settings = me.youngjibbit.module.impl.client.ClientSettings.current();
        java.lang.reflect.Method kawase = GaussianBlur.class.getDeclaredMethod("kawasePass", Framebuffer.class, int.class, float.class);
        kawase.setAccessible(true);
        try (RenderState original = new RenderState()) {
            Framebuffer scratch = new Framebuffer(127, 83, false), result = new Framebuffer(127, 83, false);
            try {
                SessionInfoRenderCheck.backdrop();
                int input = SessionInfoRenderCheck.mc.getFramebuffer().framebufferTexture;
                for (String method : new String[]{"Gaussian", "Kawase"}) {
                    settings.blurMethod.setValue(method);
                    for (int quality = 1; quality <= (method.equals("Kawase") ? 4 : 1); quality++) {
                        settings.blurQuality.setValue((double)quality);
                        UiEffects.invalidate();
                        for (float radius : new float[]{.25f, 7.4f, 127f}) {
                            if (method.equals("Gaussian")) {
                                int r = Math.max(1, Math.min(127, Math.round(radius)));
                                GaussianBlur.renderPass(scratch, input, 1, 0, r, false, 1);
                                GaussianBlur.renderPass(result, scratch.framebufferTexture, 0, 1, r, false, 1);
                            } else {
                                int source = input, passes = 2 * quality;
                                for (int i = 0; i < passes; i++) {
                                    Framebuffer target = (i & 1) == 0 ? scratch : result;
                                    try (RenderState pass = new RenderState()) {
                                        pass.screenSpace();
                                        kawase.invoke(null, target, source, Math.max(.5f, radius * (i + 1) / passes * .45f));
                                    }
                                    source = target.framebufferTexture;
                                }
                            }
                            byte[] expected = readRgba(result);
                            SessionInfoRenderCheck.mc.getFramebuffer().bindFramebuffer(true);
                            glViewport(3, 5, 97, 61);
                            GlStateManager.enableDepth();GlStateManager.enableAlpha();GlStateManager.enableBlend();
                            glEnable(GL_SCISSOR_TEST);glScissor(7, 9, 80, 60);
                            GlStateManager.depthMask(false);
                            glMatrixMode(GL_PROJECTION);glLoadIdentity();glTranslatef(.1f, .2f, .3f);
                            java.nio.FloatBuffer projection = org.lwjgl.BufferUtils.createFloatBuffer(16);
                            glGetFloat(GL_PROJECTION_MATRIX, projection);
                            GlStateManager.setActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);
                            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE1);
                            GlStateManager.bindTexture(input);glBindTexture(GL_TEXTURE_2D, input);
                            GaussianBlur.renderScene(scratch, result, input, radius);
                            java.nio.IntBuffer viewport = org.lwjgl.BufferUtils.createIntBuffer(16);
                            glGetInteger(GL_VIEWPORT, viewport);
                            java.nio.FloatBuffer restored = org.lwjgl.BufferUtils.createFloatBuffer(16);
                            glGetFloat(GL_PROJECTION_MATRIX, restored);
                            SessionInfoRenderCheck.require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) == SessionInfoRenderCheck.mc.getFramebuffer().framebufferObject
                                    && glIsEnabled(GL_DEPTH_TEST) && glIsEnabled(GL_ALPHA_TEST) && glIsEnabled(GL_BLEND)
                                    && glIsEnabled(GL_SCISSOR_TEST) && !glGetBoolean(GL_DEPTH_WRITEMASK)
                                    && glGetInteger(GL_MATRIX_MODE) == GL_PROJECTION && projection.equals(restored)
                                    && viewport.get(0) == 3 && viewport.get(1) == 5 && viewport.get(2) == 97 && viewport.get(3) == 61
                                    && glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE) == org.lwjgl.opengl.GL13.GL_TEXTURE1
                                    && glGetInteger(GL_TEXTURE_BINDING_2D) == input, "Scene batch leaked caller state: " + method);
                            SessionInfoRenderCheck.require(java.util.Arrays.equals(expected, readRgba(result)), "Scene batch pixels differ: " + method + "/" + quality + "/" + radius);
                        }
                    }
                }
            } finally { result.deleteFramebuffer();scratch.deleteFramebuffer(); }
        } finally {
            SessionInfoRenderCheck.set(me.youngjibbit.Pulsar.INSTANCE, "mm", previous);
            UiEffects.invalidate();
        }
    }

    private static byte[] readRgba(Framebuffer target) {
        target.bindFramebuffer(true);
        java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(target.framebufferWidth * target.framebufferHeight * 4);
        glReadPixels(0, 0, target.framebufferWidth, target.framebufferHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] rgba = new byte[pixels.capacity()];pixels.get(rgba);return rgba;
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
    // The surface composite reads only halo alpha. Compare the entire attachment, including
    // pixels belonging to the previous panel when the shared scratch buffers are reused.
    private static void checkMaskGlow() {
        try (RenderState guard = new RenderState()) {
            int width = 257, height = 149;
            Framebuffer mask = new Framebuffer(width, height, false);
            Framebuffer scratch = new Framebuffer(width, height, false);
            Framebuffer result = new Framebuffer(width, height, false);
            try {
                for (Framebuffer target : new Framebuffer[]{mask, scratch}) {
                    target.setFramebufferFilter(GL_LINEAR);
                    target.bindFramebufferTexture();
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
                }
                int[][] cases = {{0, 0, 32, 19, 1}, {213, 125, 49, 31, 12}, {72, 49, 19, 9, 120}};
                for (int[] box : cases) {
                    int x = box[0], y = box[1], w = box[2], h = box[3], r = box[4];
                    mask.bindFramebuffer(true);
                    glDisable(GL_SCISSOR_TEST);
                    glColorMask(true, true, true, true);
                    glClearColor(0, 0, 0, 0);
                    glClear(GL_COLOR_BUFFER_BIT);
                    glEnable(GL_SCISSOR_TEST);
                    glScissor(x, height - y - h, w, h);
                    glClearColor(1, 1, 1, 1);
                    glClear(GL_COLOR_BUFFER_BIT);
                    GaussianBlur.renderPass(scratch, mask.framebufferTexture, 1, 0, r, true, 1.4f);
                    GaussianBlur.renderPass(result, scratch.framebufferTexture, 0, 1, r, true, 1.4f);
                    byte[] expected = readAlpha(result);
                    mask.bindFramebuffer(true);
                    GaussianBlur.renderMaskGlow(scratch, result, mask.framebufferTexture, r, 1.4f,
                            x - .25f, y - .75f, x + w + .5f, y + h + .25f);
                    SessionInfoRenderCheck.require(glIsEnabled(GL_SCISSOR_TEST)
                            && glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) == mask.framebufferObject,
                            "Bounded glow leaked caller state");
                    SessionInfoRenderCheck.require(expected[(height - y - 1) * width + x] != 0
                            && java.util.Arrays.equals(expected, readAlpha(result)), "Halo alpha differs at radius " + r);
                }
            } finally {
                result.deleteFramebuffer();
                scratch.deleteFramebuffer();
                mask.deleteFramebuffer();
            }
        }
    }

    private static byte[] readAlpha(Framebuffer target) {
        target.bindFramebuffer(true);
        int pixels = target.framebufferWidth * target.framebufferHeight;
        java.nio.ByteBuffer rgba = org.lwjgl.BufferUtils.createByteBuffer(pixels * 4);
        glReadPixels(0, 0, target.framebufferWidth, target.framebufferHeight, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        byte[] alpha = new byte[pixels];
        for (int i = 0; i < pixels; i++) alpha[i] = rgba.get(i * 4 + 3);
        return alpha;
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
  checkMenuResources();checkGaussian();checkSceneBatch();checkMaskGlow();checkStencil();BloomMaskBatchCheck.run();ShaderPerformanceCheck.run();
  RenderDiagnostics.enabled=false;SessionInfoRenderCheck.require(glGetError()==GL_NO_ERROR,"Shader cleanup GL error");System.out.println("PASS: shader UV/dimensions, stream/program ownership, native/six menu resources and warm reuse, shared Gaussian state, stencil masks/resize/release");
 }
}
