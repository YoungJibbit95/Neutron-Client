package me.youngjibbit.utils.render;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.optifine.shaders.Shaders;
import org.lwjgl.opengl.*;
import java.nio.*;
import static org.lwjgl.opengl.GL11.*;
    /** Raw attributes plus cache synchronization: legacy helpers/fonts mix GL calls and GlStateManager. */
public final class RenderState implements AutoCloseable {
        private final ContextCapabilities capabilities=GLContext.getCapabilities();
        private final int framebufferApi=framebufferApi(capabilities.OpenGL30,capabilities.GL_ARB_framebuffer_object,capabilities.GL_EXT_framebuffer_object);
        private final boolean separateTargets=framebufferApi==0||framebufferApi==1||capabilities.GL_EXT_framebuffer_blit;
        final int framebuffer=glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT), program=glGetInteger(GL20.GL_CURRENT_PROGRAM);
        final int readFramebuffer=separateTargets?glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING):framebuffer;
        final boolean fog=glIsEnabled(GL_FOG);
        final boolean stencil=glIsEnabled(GL_STENCIL_TEST);
        final boolean scissor=glIsEnabled(GL_SCISSOR_TEST);
        final int matrix=glGetInteger(GL_MATRIX_MODE), active=glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        final boolean blend=glIsEnabled(GL_BLEND), depth=glIsEnabled(GL_DEPTH_TEST), alpha=glIsEnabled(GL_ALPHA_TEST), lighting=glIsEnabled(GL_LIGHTING);
        final boolean rescale=glIsEnabled(GL12.GL_RESCALE_NORMAL), normalize=glIsEnabled(GL_NORMALIZE), material=glIsEnabled(GL_COLOR_MATERIAL), cull=glIsEnabled(GL_CULL_FACE), depthWrite=glGetBoolean(GL_DEPTH_WRITEMASK);
        final int depthFunction=glGetInteger(GL_DEPTH_FUNC), alphaFunction=glGetInteger(GL_ALPHA_TEST_FUNC), shade=glGetInteger(GL_SHADE_MODEL), materialFace=glGetInteger(GL_COLOR_MATERIAL_FACE), materialMode=glGetInteger(GL_COLOR_MATERIAL_PARAMETER);
        final float alphaReference=glGetFloat(GL_ALPHA_TEST_REF);
        final int src=glGetInteger(GL14.GL_BLEND_SRC_RGB), dst=glGetInteger(GL14.GL_BLEND_DST_RGB), srcA=glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstA=glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        // Vanilla living brightness uses unit 2, menu samplers use 3, bloom exclusion uses 16.
        private static final int[] units={GL13.GL_TEXTURE0,GL13.GL_TEXTURE1,GL13.GL_TEXTURE2,GL13.GL_TEXTURE3,GL13.GL_TEXTURE16};
        private final RenderStateBuffers buffers=RenderStateBuffers.acquire();
        final IntBuffer scissorBox=buffers.scissor, viewport=buffers.viewport;
        private final Shaders.ClientRenderState shaderState;
        final boolean[] lights=buffers.lights;
        final int[] binding=buffers.bindings; final boolean[] texture=buffers.textures;
        final FloatBuffer savedProjection=buffers.projection, savedModel=buffers.model;
        final FloatBuffer color=buffers.color, clear=buffers.clear;
        final ByteBuffer mask=buffers.mask;
        private boolean closed;
        public RenderState() {
            for(int i=0;i<8;i++)lights[i]=glIsEnabled(GL_LIGHT0+i);
            glGetInteger(GL_SCISSOR_BOX,scissorBox);glGetInteger(GL_VIEWPORT,viewport);
            glGetFloat(GL_CURRENT_COLOR,color); glGetFloat(GL_COLOR_CLEAR_VALUE,clear); glGetBoolean(GL_COLOR_WRITEMASK,mask);
            for (int i=0;i<units.length;i++) { GL13.glActiveTexture(units[i]); binding[i]=glGetInteger(GL_TEXTURE_BINDING_2D); texture[i]=glIsEnabled(GL_TEXTURE_2D); }
            GL13.glActiveTexture(active); glPushAttrib(GL_ALL_ATTRIB_BITS);
            glGetFloat(GL_PROJECTION_MATRIX,savedProjection);glGetFloat(GL_MODELVIEW_MATRIX,savedModel);glMatrixMode(GL_MODELVIEW);
            // Minimal native probes can own a GL context before Minecraft/OptiFine startup.
            shaderState=OpenGlHelper.shadersSupported?Shaders.beginClientRender():null;
        }
        public boolean enabledScissor(){return scissor;}
        public void restoreClip(){if(stencil)glEnable(GL_STENCIL_TEST);else glDisable(GL_STENCIL_TEST);glScissor(scissorBox.get(0),scissorBox.get(1),scissorBox.get(2),scissorBox.get(3));if(scissor)glEnable(GL_SCISSOR_TEST);else glDisable(GL_SCISSOR_TEST);}
        /** Offscreen passes write all channels; caller output keeps the captured eye/channel mask. */
        public void restoreColorMask(){
            boolean red=mask.get(0)!=0,green=mask.get(1)!=0,blue=mask.get(2)!=0,alpha=mask.get(3)!=0;
            GlStateManager.colorMask(red,green,blue,alpha);glColorMask(red,green,blue,alpha);
        }
        public void screenSpace(){Minecraft mc=Minecraft.getMinecraft();ScaledResolution sr=new ScaledResolution(mc);glMatrixMode(GL_PROJECTION);glLoadIdentity();glOrtho(0,sr.getScaledWidth_double(),sr.getScaledHeight_double(),0,-1000,1000);glMatrixMode(GL_MODELVIEW);glLoadIdentity();glDisable(GL_DEPTH_TEST);GlStateManager.disableDepth();glDisable(GL_ALPHA_TEST);GlStateManager.disableAlpha();glDisable(GL_LIGHTING);GlStateManager.disableLighting();glDisable(GL_STENCIL_TEST);glDisable(GL_SCISSOR_TEST);}
        /** Restore the drawing destination and make it the source for scene copies in this pass. */
        public void bindTarget() {
            OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,validFramebuffer(framebuffer));
            glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
        }
        private int validFramebuffer(int id){
            return id==0||framebufferExists(id,framebufferApi)?id:0;
        }
        private void bindTargets(int draw,int read){
            if(!separateTargets||draw==read){OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,draw);return;}
            switch(framebufferApi){
                case 0:GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);break;
                case 1:ARBFramebufferObject.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER,draw);ARBFramebufferObject.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,read);break;
                default:EXTFramebufferObject.glBindFramebufferEXT(GL30.GL_DRAW_FRAMEBUFFER,draw);EXTFramebufferObject.glBindFramebufferEXT(GL30.GL_READ_FRAMEBUFFER,read);
            }
            if(RenderDiagnostics.enabled)RenderDiagnostics.framebufferBinds+=2;
        }
        // Match OpenGlHelper.initializeTextures: core first, then ARB, then EXT.
        static int framebufferApi(boolean core,boolean arb,boolean ext){return core?0:arb?1:ext?2:-1;}
        static boolean framebufferExists(int id,int api){
            switch(api){case 0:return GL30.glIsFramebuffer(id);case 1:return ARBFramebufferObject.glIsFramebuffer(id);case 2:return EXTFramebufferObject.glIsFramebufferEXT(id);default:return false;}
        }
        @Override public void close() {
            if(closed)return;
            closed=true;
            try {
                restore();
            } finally {
                try {if(shaderState!=null)shaderState.close();}
                finally {RenderStateBuffers.release(buffers);}
            }
        }
        private void restore() {
            glMatrixMode(GL_MODELVIEW);glLoadMatrix(savedModel);glMatrixMode(GL_PROJECTION);glLoadMatrix(savedProjection);glMatrixMode(matrix);
            int target=validFramebuffer(framebuffer),readTarget=readFramebuffer==framebuffer?target:validFramebuffer(readFramebuffer);
            // Some compatibility drivers recreate a deleted texture name while
            // popping GL_TEXTURE_BIT. Check before that pop can make it look alive.
            int deletedTextures=0;
            for(int i=0;i<units.length;i++)if(binding[i]!=0&&!glIsTexture(binding[i]))deletedTextures|=1<<i;
            // A deleted offscreen target may have saved COLOR_ATTACHMENT draw/read buffers.
            // Those cannot be popped onto the default framebuffer. Use a fresh temporary
            // object for that pop rather than implicitly recreating the stale name.
            int scratch=framebuffer!=0&&target==0?OpenGlHelper.glGenFramebuffers():0;
            int readScratch=readFramebuffer!=0&&readTarget==0?
                    (scratch!=0?scratch:OpenGlHelper.glGenFramebuffers()):0;
            try {
                bindTargets(scratch!=0?scratch:target,readScratch!=0?readScratch:readTarget);
                GL20.glUseProgram(program==0||GL20.glIsProgram(program)?program:0);
                if(RenderDiagnostics.enabled)RenderDiagnostics.programBinds++;
                glPopAttrib();
                if(deletedTextures!=0)for(int i=0;i<units.length;i++)if((deletedTextures&(1<<i))!=0){
                    GlStateManager.deleteTexture(binding[i]);binding[i]=0;
                }
            } finally {
                bindTargets(target,readTarget);
                if(scratch!=0)OpenGlHelper.glDeleteFramebuffers(scratch);
                if(readScratch!=0&&readScratch!=scratch)OpenGlHelper.glDeleteFramebuffers(readScratch);
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
            restoreColorMask();
            GlStateManager.color(color.get(0),color.get(1),color.get(2),color.get(3));
            GlStateManager.clearColor(clear.get(0),clear.get(1),clear.get(2),clear.get(3));
            for (int i=0;i<units.length;i++) {
                GlStateManager.setActiveTexture(units[i]); GL13.glActiveTexture(units[i]);
                int saved=binding[i];GlStateManager.bindTexture(saved); glBindTexture(GL_TEXTURE_2D,saved);
                if (texture[i]) GlStateManager.enableTexture2D(); else GlStateManager.disableTexture2D();
            }
            GlStateManager.setActiveTexture(active); GL13.glActiveTexture(active);
        }
    }
