package me.youngjibbit.utils.render.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.EXTPackedDepthStencil;

import static org.lwjgl.opengl.GL11.*;

public class StencilUtil {
    static Minecraft mc = Minecraft.getMinecraft();

    /*
     * Given to me by igs
     *                    */

    public static void checkSetupFBO(Framebuffer framebuffer) {
        if(framebuffer!=null&&framebuffer.depthBuffer>=0)setupFBO(framebuffer);
    }

    /**
     * @param framebuffer
     * @implNote Sets up the Framebuffer for Stencil use
     */

    public static void setupFBO(Framebuffer framebuffer) {
        if(framebuffer==null||framebuffer.depthBuffer<0)return;
        int previous=glGetInteger(EXTFramebufferObject.GL_RENDERBUFFER_BINDING_EXT);
        try(me.youngjibbit.utils.render.RenderState state=new me.youngjibbit.utils.render.RenderState()){
            framebuffer.bindFramebuffer(false);
            int attached=EXTFramebufferObject.glGetFramebufferAttachmentParameteriEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,EXTFramebufferObject.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME_EXT);
            if(attached==framebuffer.depthBuffer)return;
            EXTFramebufferObject.glDeleteRenderbuffersEXT(framebuffer.depthBuffer);
            int packed=EXTFramebufferObject.glGenRenderbuffersEXT();
            EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT,packed);
            EXTFramebufferObject.glRenderbufferStorageEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT,EXTPackedDepthStencil.GL_DEPTH_STENCIL_EXT,framebuffer.framebufferTextureWidth,framebuffer.framebufferTextureHeight);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,EXTFramebufferObject.GL_STENCIL_ATTACHMENT_EXT,EXTFramebufferObject.GL_RENDERBUFFER_EXT,packed);
            EXTFramebufferObject.glFramebufferRenderbufferEXT(EXTFramebufferObject.GL_FRAMEBUFFER_EXT,EXTFramebufferObject.GL_DEPTH_ATTACHMENT_EXT,EXTFramebufferObject.GL_RENDERBUFFER_EXT,packed);
            // Keep the packed buffer in its existing owner's slot: resize/delete must release it.
            framebuffer.depthBuffer=packed;
        }finally{EXTFramebufferObject.glBindRenderbufferEXT(EXTFramebufferObject.GL_RENDERBUFFER_EXT,previous!=0&&EXTFramebufferObject.glIsRenderbufferEXT(previous)?previous:0);}
    }

    /**
     * @implNote Initializes the Stencil Buffer to write to
     */
    public static void initStencilToWrite() {
        //init
        mc.getFramebuffer().bindFramebuffer(false);
        checkSetupFBO(mc.getFramebuffer());
        glClear(GL_STENCIL_BUFFER_BIT);
        glEnable(GL_STENCIL_TEST);

        glStencilFunc(GL_ALWAYS, 1, 1);
        glStencilOp(GL_REPLACE, GL_REPLACE, GL_REPLACE);
        glColorMask(false, false, false, false);
    }

    /**
     * @param ref (usually 1)
     * @implNote Reads the Stencil Buffer and stencils it onto everything until
     * @see StencilUtil#uninitStencilBuffer()  is called
     */
    public static void readStencilBuffer(int ref) {
        glColorMask(true, true, true, true);
        glStencilFunc(GL_EQUAL, ref, 1);
        glStencilOp(GL_KEEP, GL_KEEP, GL_KEEP);
    }

    public static void uninitStencilBuffer() {
        glDisable(GL_STENCIL_TEST);
    }
}