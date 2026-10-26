package me.youngjibbit.utils.render;

import me.youngjibbit.utils.render.shader.KawaseBloom;
import net.minecraft.client.shader.Framebuffer;

import java.util.ArrayList;
import java.util.List;

public final class GuiShaderBloom {

    private static final List<float[]> queuedRects = new ArrayList<>();
    private static int batchDepth;
    private static boolean used,capturing;
    public static boolean isCapturing(){return capturing;}
    private static final List<Runnable> afterBatch = new ArrayList<>();

    private GuiShaderBloom() {
    }

    public static void beginBatch() {
        // Nested owners must not erase their parent's pending work.
        if (batchDepth++ == 0) {
            queuedRects.clear();
            afterBatch.clear();
        }
    }

    public static boolean isBatching() {
        return batchDepth > 0;
    }

    public static void queueRoundedRect(float x, float y, float width, float height, int radius, int color) {
        queueRect(x,y,width,height,radius,color,false,false);
    }
    public static void queueWorldRect(float x,float y,float width,float height,int radius,int color,boolean fixedColor){queueRect(x,y,width,height,radius,color,fixedColor,true);}
    private static void queueRect(float x,float y,float width,float height,int radius,int color,boolean fixedColor,boolean exactGeometry){
        if (!UiEffects.effectsSupported() || !UiEffects.glowEnabled() || width <= 0.0f || height <= 0.0f) {
            return;
        }

        if (!isBatching()) {
            beginBatch();queueRect(x,y,width,height,radius,color,fixedColor,exactGeometry);flushBatch(2,2);return;
        }

        float[] bounds=ScreenRect.project(x,y,width,height);
        java.nio.IntBuffer clip=org.lwjgl.BufferUtils.createIntBuffer(16);
        org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_SCISSOR_BOX,clip);
        queuedRects.add(new float[]{bounds[0],bounds[1],bounds[2],bounds[3],
                radius*Math.min(bounds[2]/width,bounds[3]/height),Float.intBitsToFloat(color),
                org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_SCISSOR_TEST)?1:0,
                clip.get(0),clip.get(1),clip.get(2),clip.get(3),fixedColor?1:0,exactGeometry?1:0});
    }

    public static void flushBatch(int iterations, int offset) {
        if (batchDepth > 0 && --batchDepth > 0) return;
        List<Runnable> composites = new ArrayList<>(afterBatch);
        afterBatch.clear();
        try {
            if (UiEffects.effectsSupported() && UiEffects.glowEnabled() && !queuedRects.isEmpty()) {
                try (RenderState state=new RenderState()) {
                    state.screenSpace();
                    for(int role=0;role<2;role++){
                        List<float[]> group=new ArrayList<>();for(float[] rect:queuedRects)if((rect.length>11&&rect[11]>0?1:0)==role)group.add(rect);if(group.isEmpty())continue;
                        used=true;Framebuffer bloomFramebuffer;capturing=true;try{bloomFramebuffer=KawaseBloom.drawRectsToFramebuffer(group);}finally{capturing=false;}
                        KawaseBloom.renderBlur(bloomFramebuffer.framebufferTexture,Math.max(1,iterations),Math.max(1,UiEffects.options().configured?Math.round(UiEffects.glowRadius(offset)/4):offset),role==0);
                    }
                }
            }
        } finally {
            queuedRects.clear();
            batchDepth = 0;
        }
        // Surface compositors capture the scene here, after ordinary bloom, before their own glow.
        for (Runnable composite : composites) composite.run();
    }

    public static void afterBatch(Runnable composite) {
        if (isBatching()) afterBatch.add(composite);
        else composite.run();
    }

    public static void releaseResources(){queuedRects.clear();afterBatch.clear();batchDepth=0;if(used){KawaseBloom.releaseResources();used=false;}}
    public static void renderRect(float x, float y, float width, float height, int radius, int color, int iterations, int offset) {
        beginBatch();
        queueRoundedRect(x, y, width, height, radius, color);
        flushBatch(iterations, offset);
    }
}
