package me.youngjibbit.module.impl.render;

import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/** Native pixel comparison against individually guarded draws, including clip and transform changes. */
public final class BloomMaskBatchCheck {
    private BloomMaskBatchCheck() { }

    public static void run() throws Exception {
        ModuleManager old = Pulsar.INSTANCE.getMm();
        SessionInfoRenderCheck.set(Pulsar.INSTANCE, "mm", new ModuleManager());
        try {
            ClientSettings settings = ClientSettings.current();
            settings.gradientMotion.setValue("Static");
            settings.reducedMotion.setValue(true);
            settings.roundness.setValue(18.0);
            UiEffects.invalidate();
            List<float[]> rects = Arrays.asList(
                    rect(16, 20, 90, 55, 7, 0x90e91e63, false, false),
                    rect(50, 35, 95, 45, 9, 0xb012c9e0, true, false),
                    rect(145, 25, 55, 35, 0, 0xff18dd42, false, true),
                    rect(180, 52, 55, 28, 30, 0x501122ff, true, true));
            for (boolean transformed : new boolean[]{false, true}) {
                byte[] expected = draw(rects, false, transformed);
                byte[] actual = draw(rects, true, transformed);
                SessionInfoRenderCheck.require(Arrays.equals(expected, actual),
                        "Batched mask pixels differ; transformed=" + transformed);
            }
            try (RenderState state = new RenderState()) {
                glEnable(GL_SCISSOR_TEST);
                glScissor(7, 9, 83, 61);
                int program = glGetInteger(GL_CURRENT_PROGRAM);
                try {
                    UIUtils.drawBloomMaskRects(Arrays.asList(rects.get(0), new float[0]));
                    throw new AssertionError("Malformed mask accepted");
                } catch (ArrayIndexOutOfBoundsException expected) { }
                java.nio.IntBuffer clip = org.lwjgl.BufferUtils.createIntBuffer(16);
                glGetInteger(GL_SCISSOR_BOX, clip);
                SessionInfoRenderCheck.require(glIsEnabled(GL_SCISSOR_TEST)
                        && clip.get(0) == 7 && clip.get(1) == 9 && clip.get(2) == 83 && clip.get(3) == 61
                        && glGetInteger(GL_CURRENT_PROGRAM) == program,
                        "Batch exception leaked clip or shader state");
            }
            try (RenderState state = new RenderState()) {
                int attributes = glGetInteger(GL_ATTRIB_STACK_DEPTH);
                int matrices = glGetInteger(GL_MODELVIEW_STACK_DEPTH);
                try {
                    me.youngjibbit.utils.render.shader.KawaseBloom.drawRectsToFramebuffer(
                            Arrays.asList(rects.get(0), new float[0]));
                    throw new AssertionError("Malformed framebuffer mask accepted");
                } catch (ArrayIndexOutOfBoundsException expected) { }
                SessionInfoRenderCheck.require(glGetInteger(GL_ATTRIB_STACK_DEPTH) == attributes
                        && glGetInteger(GL_MODELVIEW_STACK_DEPTH) == matrices
                        && glGetInteger(org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) == 0,
                        "Framebuffer mask exception leaked stacks or left scratch target bound");
            }
            SessionInfoRenderCheck.require(glGetError() == GL_NO_ERROR, "Mask batch GL error");
            System.out.println("PASS: batched mask RGBA equals standalone draws, clips/transforms/exact radii and aborted-batch restoration");
            if (Boolean.getBoolean("pulsar.batch.benchmark")) benchmark();
        } finally {
            SessionInfoRenderCheck.set(Pulsar.INSTANCE, "mm", old);
            UiEffects.invalidate();
        }
    }

    private static float[] rect(float x, float y, float width, float height, float radius,
                                int color, boolean clip, boolean exact) {
        return new float[]{x, y, width, height, radius, Float.intBitsToFloat(color),
                clip ? 1 : 0, 35, 30, 380, 200, 0, exact ? 1 : 0};
    }

    private static byte[] draw(List<float[]> rects, boolean batch, boolean transformed) {
        SessionInfoRenderCheck.backdrop();
        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);
        submit(rects, batch, transformed);
        int width = SessionInfoRenderCheck.mc.displayWidth, height = SessionInfoRenderCheck.mc.displayHeight;
        java.nio.ByteBuffer pixels = org.lwjgl.BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] rgba = new byte[pixels.remaining()];
        pixels.get(rgba);
        return rgba;
    }

    private static void submit(List<float[]> rects, boolean batch, boolean transformed) {
        try (RenderState state = new RenderState()) {
            if (transformed) { glTranslatef(13, 9, 0); glScalef(.85f, 1.1f, 1); }
            glDisable(GL_DEPTH_TEST);
            if (batch) UIUtils.drawBloomMaskRects(rects);
            else for (float[] rect : rects) {
                if (rect[6] > 0) { glEnable(GL_SCISSOR_TEST); glScissor((int)rect[7], (int)rect[8], (int)rect[9], (int)rect[10]); }
                else glDisable(GL_SCISSOR_TEST);
                Color color = new Color(Float.floatToRawIntBits(rect[5]), true);
                if (rect[12] > 0) UIUtils.drawWorldMaskRound(rect[0], rect[1], rect[2], rect[3], (int)rect[4], color);
                else UIUtils.drawRound(rect[0], rect[1], rect[2], rect[3], (int)rect[4], color);
            }
        }
    }

    /** CPU submission timing, including the public calls' state guards; not GPU or whole-frame time. */
    private static void benchmark() throws Exception {
        List<float[]> rects = new java.util.ArrayList<>();
        for (int i = 0; i < 64; i++) {
            rects.add(rect(12 + (i % 8) * 26, 16 + (i / 8) * 18, 24, 16,
                    4, 0x9012c9e0, (i & 1) != 0, (i & 3) == 0));
        }
        final int warmup = 60, pairs = 200;
        long[] standalone = new long[pairs], batched = new long[pairs];
        SessionInfoRenderCheck.backdrop();
        glFinish();
        for (int pair = -warmup; pair < pairs; pair++) {
            // Alternate first path to limit order bias. No capture, file output, or explicit
            // completion wait occurs inside a timed submission. Each path starts idle.
            boolean batchFirst = (pair & 1) != 0;
            for (int order = 0; order < 2; order++) {
                boolean batch = order == 0 ? batchFirst : !batchFirst;
                glClear(GL_COLOR_BUFFER_BIT);
                glFinish();
                long start = System.nanoTime();
                submit(rects, batch, false);
                long elapsed = System.nanoTime() - start;
                glFinish();
                if (pair >= 0) (batch ? batched : standalone)[pair] = elapsed;
            }
        }
        try (java.io.PrintWriter out = new java.io.PrintWriter("bloom-mask-submission.csv", "UTF-8")) {
            out.println("pair,batch_first,standalone_ns,batched_ns,rectangles");
            for (int i = 0; i < pairs; i++)
                out.println(i + "," + ((i & 1) != 0) + "," + standalone[i] + "," + batched[i] + ",64");
        }
        Arrays.sort(standalone);
        Arrays.sort(batched);
        System.out.printf(java.util.Locale.ROOT,
                "BENCH mask CPU submission 64 rects; warmup=%d pairs=%d standalone p50=%.3f p95=%.3f ms batch p50=%.3f p95=%.3f ms%n",
                warmup, pairs, standalone[(pairs - 1) / 2] / 1e6, standalone[(int)Math.ceil(pairs * .95) - 1] / 1e6,
                batched[(pairs - 1) / 2] / 1e6, batched[(int)Math.ceil(pairs * .95) - 1] / 1e6);
        System.out.println("BENCH excludes glFinish, clear, readback and CSV output; includes guard restore; driver stalls may occur during submission. Not GPU duration or whole-frame FPS.");
        SessionInfoRenderCheck.require(glGetError() == GL_NO_ERROR, "Mask benchmark GL error");
    }
}
