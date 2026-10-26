package me.youngjibbit.utils.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event3D;
import me.zero.alpine.listener.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import java.lang.reflect.Field;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL11.*;

/** Isolated native matrix/state dispatch probe; no game world or shader pack. */
public final class WorldOverlayNativeCheck {
    static class Consumer implements Subscriber {
        int calls, pass; boolean fail;
        @Subscribe final Listener<Event3D> listener=new Listener<>(event -> {
            calls++;pass=event.getPass();
            glMatrixMode(GL_PROJECTION);glLoadIdentity();
            glMatrixMode(GL_MODELVIEW);glLoadIdentity();
            glDisable(GL_DEPTH_TEST);glDepthMask(false);
            if(fail)throw new IllegalStateException("synthetic listener failure");
        });
    }
    public static void main(String[] args) throws Exception {
        Pbuffer buffer=new Pbuffer(32,32,new PixelFormat().withDepthBits(24),null,null);
        try {
            buffer.makeCurrent();
            // Minimal GL helper unit constants normally set during client startup.
            // This fixture deliberately does not claim to execute that bootstrap.
            net.minecraft.client.renderer.OpenGlHelper.defaultTexUnit=GL13.GL_TEXTURE0;
            net.minecraft.client.renderer.OpenGlHelper.lightmapTexUnit=GL13.GL_TEXTURE1;
            net.minecraft.client.renderer.OpenGlHelper.GL_TEXTURE2=GL13.GL_TEXTURE2;
            // Dispatch itself uses no renderer fields. Do not bootstrap a client or allocate lightmaps here.
            Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
            CustomEntityRenderer renderer=(CustomEntityRenderer)((sun.misc.Unsafe)field.get(null)).allocateInstance(CustomEntityRenderer.class);
            Consumer consumer=new Consumer();Pulsar.BUS.subscribe(consumer);
            try {
                for(int pass=0;pass<3;pass++) {
                    glMatrixMode(GL_PROJECTION);glLoadIdentity();glFrustum(-.3,.4,-.2,.3,.1,200);
                    glMatrixMode(GL_MODELVIEW);glLoadIdentity();glRotatef(37,0,1,0);glTranslatef(0,-1,-4);
                    glEnable(GL_DEPTH_TEST);glDepthMask(true);
                    float[] projection=matrix(GL_PROJECTION_MATRIX),model=matrix(GL_MODELVIEW_MATRIX);
                    renderer.renderWorldOverlays(pass,.5f);
                    require(consumer.calls==pass+1 && consumer.pass==pass,"exactly once per eye");
                    equal(projection,matrix(GL_PROJECTION_MATRIX));equal(model,matrix(GL_MODELVIEW_MATRIX));
                    require(glIsEnabled(GL_DEPTH_TEST)&&glGetBoolean(GL_DEPTH_WRITEMASK),"depth restored");
                }
                consumer.fail=true;
                float[] model=matrix(GL_MODELVIEW_MATRIX);
                java.io.PrintStream originalError=System.err;
                java.io.ByteArrayOutputStream expectedDiagnostic=new java.io.ByteArrayOutputStream();
                try (java.io.PrintStream sink=new java.io.PrintStream(expectedDiagnostic,true,"UTF-8")) {
                    System.setErr(sink);
                    try {renderer.renderWorldOverlays(2,.5f);throw new AssertionError("exception lost");}
                    catch(IllegalStateException expected) {require(expected.getMessage().contains("synthetic"),"unexpected failure");}
                } finally {System.setErr(originalError);}
                require(expectedDiagnostic.toString("UTF-8").contains("synthetic listener failure"),"expected Alpine diagnostic");
                equal(model,matrix(GL_MODELVIEW_MATRIX));
                require(glIsEnabled(GL_DEPTH_TEST)&&glGetBoolean(GL_DEPTH_WRITEMASK),"exception depth restored");
                require(glGetError()==GL_NO_ERROR,"GL error");
            } finally {Pulsar.BUS.unsubscribe(consumer);}
            System.out.println("PASS: Pbuffer world-camera dispatch, each eye once, state restoration including exception. No actual world, shaderpack, resource reload or native Display coverage.");
        } finally {buffer.destroy();}
    }
    static float[] matrix(int name){FloatBuffer b=BufferUtils.createFloatBuffer(16);glGetFloat(name,b);float[] a=new float[16];b.get(a);return a;}
    static void equal(float[] a,float[] b){for(int i=0;i<a.length;i++)require(Math.abs(a[i]-b[i])<.00001,"matrix restore "+i);}
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
