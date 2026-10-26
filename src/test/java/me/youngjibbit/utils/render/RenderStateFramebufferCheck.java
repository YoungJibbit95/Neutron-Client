package me.youngjibbit.utils.render;

import java.lang.reflect.Field;
import net.minecraft.client.renderer.OpenGlHelper;
import org.junit.Test;
import org.lwjgl.opengl.*;
import static org.lwjgl.opengl.GL11.*;

/** Actual entry points on supported APIs; selection is checked for capability sets unavailable on this GPU. */
public final class RenderStateFramebufferCheck {
    @Test public void selection(){
        for(int bits=0;bits<8;bits++){
            boolean core=(bits&1)!=0,arb=(bits&2)!=0,ext=(bits&4)!=0;
            require(RenderState.framebufferApi(core,arb,ext)==(core?0:arb?1:ext?2:-1),"FBO capability precedence");
        }
    }
    public static void run()throws Exception{
        new RenderStateFramebufferCheck().selection();
        ContextCapabilities caps=GLContext.getCapabilities();
        boolean[] available={caps.OpenGL30,caps.GL_ARB_framebuffer_object,caps.GL_EXT_framebuffer_object};
        Field type=OpenGlHelper.class.getDeclaredField("framebufferType");type.setAccessible(true);int original=type.getInt(null);
        int target=glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        try{
            for(int api=0;api<3;api++){
                if(!available[api]){System.out.println("SKIP native FBO API "+api+": capability unavailable");continue;}
                type.setInt(null,api);
                net.minecraft.client.shader.Framebuffer probe=new net.minecraft.client.shader.Framebuffer(16,16,false);
                int id=probe.framebufferObject;probe.bindFramebuffer(false);
                require(glGetInteger(GL_DRAW_BUFFER)==GL30.GL_COLOR_ATTACHMENT0,"Probe must use real color attachment draw buffer");
                require(glGetInteger(GL_READ_BUFFER)==GL30.GL_COLOR_ATTACHMENT0,"Probe must use real color attachment read buffer");
                require(RenderState.framebufferExists(id,api),"Live FBO missing for API "+api);
                OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,0);
                int defaultDraw=glGetInteger(GL_DRAW_BUFFER),defaultRead=glGetInteger(GL_READ_BUFFER);
                probe.bindFramebuffer(false);
                try(RenderState live=new RenderState()){OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,0);}
                require(glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)==id,"Live close did not restore FBO");
                require(glGetInteger(GL_DRAW_BUFFER)==GL30.GL_COLOR_ATTACHMENT0,"Live close changed draw buffer");
                require(glGetInteger(GL_READ_BUFFER)==GL30.GL_COLOR_ATTACHMENT0,"Live close changed read buffer");
                require(glGetError()==GL_NO_ERROR,"Before snapshot API "+api);
                try(RenderState state=new RenderState()){
                    require(glGetError()==GL_NO_ERROR,"Snapshot API "+api);
                    OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,0);
                    state.bindTarget();require(glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)==id,"Live target not restored");
                    require(glGetError()==GL_NO_ERROR,"Live bind API "+api);
                    probe.deleteFramebuffer();
                    require(!RenderState.framebufferExists(id,api),"Deleted FBO remains live");
                    require(glGetError()==GL_NO_ERROR,"Delete API "+api);
                    state.bindTarget();require(glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)==0,"bindTarget resurrected deleted FBO");
                    require(glGetError()==GL_NO_ERROR,"Deleted bind API "+api);
                }
                require(glGetInteger(GL30.GL_FRAMEBUFFER_BINDING)==0,"close resurrected deleted FBO");
                require(!RenderState.framebufferExists(id,api),"Restoration recreated deleted name");
                require(glGetInteger(GL_DRAW_BUFFER)==defaultDraw,"Deleted close changed default draw buffer");
                require(glGetInteger(GL_READ_BUFFER)==defaultRead,"Deleted close changed default read buffer");
                int error=glGetError();require(error==GL_NO_ERROR,"FBO dispatch GL error "+error+" for API "+api);
                System.out.println("PASS native FBO API "+api+": live restore, deleted bindTarget/close, no resurrection");
            }
        }finally{type.setInt(null,original);OpenGlHelper.glBindFramebuffer(OpenGlHelper.GL_FRAMEBUFFER,target);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
