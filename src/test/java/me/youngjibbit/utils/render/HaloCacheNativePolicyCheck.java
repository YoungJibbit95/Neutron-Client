package me.youngjibbit.utils.render;

import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.client.shader.Framebuffer;
import static org.lwjgl.opengl.GL11.*;

/** Tiny real attachments exercise failed publication and reuse without waiting on wall time. */
public final class HaloCacheNativePolicyCheck {
    public static void run() {
        AtomicLong clock=new AtomicLong();HaloCache cache=new HaloCache(clock::get);
        String property=System.getProperty("pulsar.haloCache");System.setProperty("pulsar.haloCache","true");
        try(RenderState state=new RenderState()) {
            glPolygonMode(GL_FRONT_AND_BACK,GL_FILL);glDisable(GL_COLOR_LOGIC_OP);
            glDisable(GL_POLYGON_SMOOTH);glDisable(GL_POLYGON_STIPPLE);
            for(int plane=0;plane<glGetInteger(GL_MAX_CLIP_PLANES);plane++)glDisable(GL_CLIP_PLANE0+plane);
            try {
                request(cache,0,output->{throw new IllegalArgumentException("aborted population");});
                throw new AssertionError("Failed renderer accepted");
            } catch(IllegalArgumentException expected) { }
            require(cache.size()==1 && cache.allocations==1 && cache.hits==0,"Failed population storage");
            Framebuffer recovered=request(cache,0,HaloCacheNativePolicyCheck::paint);
            require(cache.size()==1 && cache.allocations==1 && cache.hits==0 && cache.misses==2,"Failed slot was published or lost");
            require(request(cache,0,output->{throw new AssertionError("Hit rendered again");})==recovered && cache.hits==1,"Published output missing");
            for(int i=1;i<32;i++)request(cache,i,HaloCacheNativePolicyCheck::paint);
            require(request(cache,32,output->{throw new AssertionError("Busy subset evicted");})==null,"Oversubscribed cache did not bypass");
            require(cache.size()==32 && cache.allocations==32,"Attachment capacity");
            clock.set(HaloCache.IDLE_NANOS+1);
            Framebuffer replaced=request(cache,32,HaloCacheNativePolicyCheck::paint);
            require(replaced!=null && cache.allocations==32 && cache.evictions==1,"Idle replacement allocated new storage");
            cache.clear();cache.clear();
            require(cache.size()==0 && cache.bytes()==0 && cache.allocations==cache.deletions,"Native cache ownership imbalance");
            require(glGetError()==GL_NO_ERROR,"Native cache policy GL error");
        } finally {
            cache.clear();
            if(property==null)System.clearProperty("pulsar.haloCache");else System.setProperty("pulsar.haloCache",property);
        }
    }
    private static Framebuffer request(HaloCache cache,int id,HaloCache.Renderer renderer) {
        return cache.get(16,16,1,2,new float[]{id,1,2,3},0,0,4,4,true,renderer);
    }
    private static void paint(Framebuffer output) {
        output.bindFramebuffer(true);glDisable(GL_SCISSOR_TEST);glColorMask(true,true,true,true);
        glClearColor(1,1,1,.5f);glClear(GL_COLOR_BUFFER_BIT);
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
