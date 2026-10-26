package me.youngjibbit.utils.render;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.nio.*;
import static org.lwjgl.opengl.GL11.*;
    /** Raw attributes plus cache synchronization: legacy helpers/fonts mix GL calls and GlStateManager. */
public final class RenderState implements AutoCloseable {
        final int framebuffer=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT), program=glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final boolean fog=glIsEnabled(GL_FOG);
        final boolean stencil=glIsEnabled(GL_STENCIL_TEST);
        final boolean scissor=glIsEnabled(GL_SCISSOR_TEST);
        final IntBuffer scissorBox=BufferUtils.createIntBuffer(16);
        final int matrix=glGetInteger(GL_MATRIX_MODE), active=glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final boolean blend=glIsEnabled(GL_BLEND), depth=glIsEnabled(GL_DEPTH_TEST), alpha=glIsEnabled(GL_ALPHA_TEST), lighting=glIsEnabled(GL_LIGHTING);
        final boolean rescale=glIsEnabled(GL12.GL_RESCALE_NORMAL), normalize=glIsEnabled(GL_NORMALIZE), material=glIsEnabled(GL_COLOR_MATERIAL), cull=glIsEnabled(GL_CULL_FACE), depthWrite=glGetBoolean(GL_DEPTH_WRITEMASK);
        final boolean[] lights=new boolean[8];
        final int depthFunction=glGetInteger(GL_DEPTH_FUNC), alphaFunction=glGetInteger(GL_ALPHA_TEST_FUNC), shade=glGetInteger(GL_SHADE_MODEL), materialFace=glGetInteger(GL_COLOR_MATERIAL_FACE), materialMode=glGetInteger(GL_COLOR_MATERIAL_PARAMETER);
        final float alphaReference=glGetFloat(GL_ALPHA_TEST_REF);
        final int src=glGetInteger(GL14.GL_BLEND_SRC_RGB), dst=glGetInteger(GL14.GL_BLEND_DST_RGB), srcA=glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA=glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        // Vanilla living brightness uses unit 2, menu samplers use 3, bloom exclusion uses 16.
        final int[] units={GL13.GL_TEXTURE0,GL13.GL_TEXTURE1,GL13.GL_TEXTURE2,GL13.GL_TEXTURE3,GL13.GL_TEXTURE16};
        final int[] binding=new int[units.length]; final boolean[] texture=new boolean[units.length];
        final FloatBuffer savedProjection=BufferUtils.createFloatBuffer(16), savedModel=BufferUtils.createFloatBuffer(16);
        final FloatBuffer color=BufferUtils.createFloatBuffer(16), clear=BufferUtils.createFloatBuffer(16);
        final ByteBuffer mask=BufferUtils.createByteBuffer(16);
        public RenderState() {
            for(int i=0;i<8;i++)lights[i]=glIsEnabled(GL_LIGHT0+i);
            glGetInteger(GL_SCISSOR_BOX,scissorBox);
            glGetFloat(GL_CURRENT_COLOR,color); glGetFloat(GL_COLOR_CLEAR_VALUE,clear); glGetBoolean(GL_COLOR_WRITEMASK,mask);
            for (int i=0;i<units.length;i++) { GL13.glActiveTexture(units[i]); binding[i]=glGetInteger(GL_TEXTURE_BINDING_2D); texture[i]=glIsEnabled(GL_TEXTURE_2D); }
            GL13.glActiveTexture(active); glPushAttrib(GL_ALL_ATTRIB_BITS);
            glGetFloat(GL_PROJECTION_MATRIX,savedProjection);glGetFloat(GL_MODELVIEW_MATRIX,savedModel);glMatrixMode(GL_MODELVIEW);
        }
        public boolean enabledScissor(){return scissor;}
        public void restoreClip(){if(stencil)glEnable(GL_STENCIL_TEST);else glDisable(GL_STENCIL_TEST);glScissor(scissorBox.get(0),scissorBox.get(1),scissorBox.get(2),scissorBox.get(3));if(scissor)glEnable(GL_SCISSOR_TEST);else glDisable(GL_SCISSOR_TEST);}
        public void screenSpace(){Minecraft mc=Minecraft.getMinecraft();ScaledResolution sr=new ScaledResolution(mc);glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);glMatrixMode(GL_MODELVIEW);glLoadIdentity();glDisable(GL_DEPTH_TEST);GlStateManager.disableDepth();glDisable(GL_ALPHA_TEST);GlStateManager.disableAlpha();glDisable(GL_LIGHTING);GlStateManager.disableLighting();glDisable(GL_STENCIL_TEST);glDisable(GL_SCISSOR_TEST);}
        public void bindTarget() { OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,validFramebuffer()); glViewport(0,0,Minecraft.getMinecraft().displayWidth,Minecraft.getMinecraft().displayHeight); }
        private int validFramebuffer(){
            ContextCapabilities caps=GLContext.getCapabilities();
            return framebuffer==0||framebufferExists(framebuffer,framebufferApi(caps.OpenGL30,caps.GL_ARB_framebuffer_object,caps.GL_EXT_framebuffer_object))?framebuffer:0;
        }
        // Match OpenGlHelper.initializeTextures: core first, then ARB, then EXT.
        static int framebufferApi(boolean core,boolean arb,boolean ext){return core?0:arb?1:ext?2:-1;}
        static boolean framebufferExists(int id,int api){
            switch(api){case 0:return GL30.glIsFramebuffer(id);case 1:return ARBFramebufferObject.glIsFramebuffer(id);case 2:return EXTFramebufferObject.glIsFramebufferEXT(id);default:return false;}
        }
        @Override public void close() {
            glMatrixMode(GL_MODELVIEW);glLoadMatrix(savedModel);glMatrixMode(GL_PROJECTION);glLoadMatrix(savedProjection);glMatrixMode(matrix);
            int target=validFramebuffer();
            // A deleted offscreen target may have saved COLOR_ATTACHMENT draw/read buffers.
            // Those cannot be popped onto the default framebuffer. Use a fresh temporary
            // object for that pop rather than implicitly recreating the stale name.
            int scratch=framebuffer!=0&&target==0?OpenGlHelper.glGenFramebuffers():0;
            try {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,scratch!=0?scratch:target);
                GL20.glUseProgram(program==0||GL20.glIsProgram(program)?program:0);
                glPopAttrib();
            } finally {
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,target);
                if(scratch!=0)OpenGlHelper.glDeleteFramebuffers(scratch);
            }
            if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
            if (depth) GlStateManager.enableDepth(); else GlStateManager.disableDepth();
            if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
            if(fog)GlStateManager.enableFog();else GlStateManager.disableFog();
            if (lighting) GlStateManager.enableLighting(); else GlStateManager.disableLighting();
            for(int i=0;i<8;i++){if(lights[i])GlStateManager.enableLight(i);else GlStateManager.disableLight(i);}
            if(rescale)GlStateManager.enableRescaleNormal();else GlStateManager.disableRescaleNormal();
            if(normalize)GlStateManager.enableNormalize();else GlStateManager.disableNormalize();
            if(material)GlStateManager.enableColorMaterial();else GlStateManager.disableColorMaterial();
            if(cull)GlStateManager.enableCull();else GlStateManager.disableCull();
            GlStateManager.colorMaterial(materialFace,materialMode);GlStateManager.shadeModel(shade);
            GlStateManager.depthFunc(depthFunction);GlStateManager.depthMask(depthWrite);GlStateManager.alphaFunc(alphaFunction,alphaReference);
            GlStateManager.tryBlendFuncSeparate(src,dst,srcA,dstA);
            GlStateManager.colorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
            GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
            GlStateManager.clearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
            for (int i=0;i<units.length;i++) {
                GlStateManager.setActiveTexture(units[i]); GL13.glActiveTexture(units[i]);
                int saved=binding[i]==0||glIsTexture(binding[i])?binding[i]:0;GlStateManager.bindTexture(saved); glBindTexture(GL_TEXTURE_2D,saved);
                if (texture[i]) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            }
            GlStateManager.setActiveTexture(active); GL13.glActiveTexture(active);
        }
    }
