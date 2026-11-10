package me.youngjibbit.module.impl.render;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.ShaderUtil;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.render.shader.KawaseBloom;
import me.youngjibbit.utils.render.shader.blur.GaussianBlur;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.EXTFramebufferObject;
import org.lwjgl.opengl.GL13;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/** Native pixel/state contracts for allocation and upload optimizations; sampling stays unchanged. */
public final class ShaderPerformanceCheck {
    private ShaderPerformanceCheck() { }

    public static void run() throws Exception {
        try (RenderState state = new RenderState()) {
            checkUniformOverloads();
            checkUniformSubclassDispatch();
            checkFramebufferClear();
            checkGaussianProgramKernels();
            checkBloomAttachments();
            checkRenderStateSnapshots();
            checkBloomQueue();
        }
        SessionInfoRenderCheck.require(glGetError() == GL_NO_ERROR, "Shader performance GL error");
        System.out.println("PASS: fixed/array uniform parity and subclass overrides, bound framebuffer clear pixels/state, cached Gaussian pixels/reload, legacy bloom depth attachments, nested state storage and bloom queue failure cleanup");
    }

    private static void checkUniformOverloads() {
        String source = "#version 120\n"
                + "uniform float f1; uniform vec2 f2; uniform vec3 f3; uniform vec4 f4;"
                + "uniform int i1; uniform ivec2 i2;"
                + "void main(){gl_FragColor=f4+vec4(f3,f1)+vec4(f2,float(i1),float(i2.x+i2.y));}";
        try (ShaderUtil shader = new ShaderUtil(source, true)) {
            shader.init();
            shader.setUniformf("f1", .25f);
            shader.setUniformf("f2", .5f, -.25f);
            shader.setUniformf("f3", .125f, .75f, .5f);
            shader.setUniformf("f4", .125f, .25f, .5f, .75f);
            shader.setUniformi("i1", 7);
            shader.setUniformi("i2", 4, -3);
            float[][] expected = {{.25f}, {.5f, -.25f}, {.125f, .75f, .5f}, {.125f, .25f, .5f, .75f}};
            FloatBuffer floats = BufferUtils.createFloatBuffer(16);
            for (int i = 0; i < expected.length; i++) {
                String name = "f" + (i + 1);
                glGetUniform(shader.getProgramId(), shader.getUniform(name), floats);
                for (int j = 0; j < expected[i].length; j++)
                    SessionInfoRenderCheck.require(floats.get(j) == expected[i][j], "Fixed uniform differs: " + name);
                // Existing dynamic-array callers still resolve to the compatible entry point.
                shader.setUniformf(name, expected[i]);
                glGetUniform(shader.getProgramId(), shader.getUniform(name), floats);
                for (int j = 0; j < expected[i].length; j++)
                    SessionInfoRenderCheck.require(floats.get(j) == expected[i][j], "Array uniform differs: " + name);
            }
            IntBuffer ints = BufferUtils.createIntBuffer(16);
            glGetUniform(shader.getProgramId(), shader.getUniform("i1"), ints);
            SessionInfoRenderCheck.require(ints.get(0) == 7, "Scalar integer uniform differs");
            glGetUniform(shader.getProgramId(), shader.getUniform("i2"), ints);
            SessionInfoRenderCheck.require(ints.get(0) == 4 && ints.get(1) == -3, "Pair integer uniform differs");
            shader.setUniformi("i2", new int[]{-2, 8});
            glGetUniform(shader.getProgramId(), shader.getUniform("i2"), ints);
            SessionInfoRenderCheck.require(ints.get(0) == -2 && ints.get(1) == 8, "Array integer uniform differs");
            shader.setUniformf("absent", 1f);
            shader.unload();
        }
    }

    private static void checkUniformSubclassDispatch() {
        String source = "#version 120\nvoid main(){gl_FragColor=vec4(1.0);}";
        final int[] baseCalls = new int[2], facadeCalls = new int[2];
        try (ShaderUtil custom = new ShaderUtil(source, true) {
            @Override public void setUniformf(String name, float... values) {
                baseCalls[0]++;super.setUniformf(name, values);
            }
            @Override public void setUniformi(String name, int... values) {
                baseCalls[1]++;super.setUniformi(name, values);
            }
        }; ShaderUtil facade = new me.youngjibbit.utils.render.shader.ShaderUtil(source, true) {
            @Override public void setUniformf(String name, float... values) {
                facadeCalls[0]++;super.setUniformf(name, values);
            }
            @Override public void setUniformi(String name, int... values) {
                facadeCalls[1]++;super.setUniformi(name, values);
            }
        }) {
            for (ShaderUtil shader : new ShaderUtil[]{custom, facade}) {
                shader.init();
                shader.setUniformf("absent", 1f);shader.setUniformf("absent", 1f, 2f);
                shader.setUniformf("absent", 1f, 2f, 3f);shader.setUniformf("absent", 1f, 2f, 3f, 4f);
                shader.setUniformi("absent", 1);shader.setUniformi("absent", 1, 2);
                shader.setUniformf("absent", new float[]{1f, 2f});
                shader.setUniformi("absent", new int[]{1, 2});
                shader.unload();
            }
            SessionInfoRenderCheck.require(Arrays.equals(baseCalls, new int[]{5, 3})
                            && Arrays.equals(facadeCalls, new int[]{5, 3}),
                    "Fixed arity bypassed a legacy/custom varargs override");
        }
    }

    private static void checkFramebufferClear() {
        try (RenderState state = new RenderState()) {
            for (boolean depth : new boolean[]{false, true}) {
                Framebuffer target = new Framebuffer(97, 61, depth);
                try {
                    target.setFramebufferColor(.25f, .5f, .75f, .125f);
                    prepareClear(target);
                    target.framebufferClear();
                    SessionInfoRenderCheck.require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) == 0,
                            "Legacy framebufferClear no longer unbinds");
                    target.bindFramebuffer(false);
                    byte[] expected = readRgba(target);
                    float[] expectedDepth = depth ? readDepth(target) : null;
                    prepareClear(target);
                    target.bindAndClear();
                    IntBuffer viewport = BufferUtils.createIntBuffer(16);
                    glGetInteger(GL_VIEWPORT, viewport);
                    SessionInfoRenderCheck.require(glGetInteger(EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT) == target.framebufferObject
                                    && viewport.get(0) == 0 && viewport.get(1) == 0
                                    && viewport.get(2) == 97 && viewport.get(3) == 61
                                    && glIsEnabled(GL_SCISSOR_TEST) && !glIsEnabled(GL_DEPTH_TEST),
                            "Bound clear destination/viewport/state changed");
                    SessionInfoRenderCheck.require(Arrays.equals(expected, readRgba(target)), "Bound clear RGBA changed");
                    if (depth) SessionInfoRenderCheck.require(Arrays.equals(expectedDepth, readDepth(target)), "Bound depth clear changed");
                } finally { target.deleteFramebuffer(); }
            }
        }
    }

    private static void prepareClear(Framebuffer target) {
        target.bindFramebuffer(true);
        GlStateManager.disableDepth();glDisable(GL_DEPTH_TEST);
        GlStateManager.depthMask(true);glDepthMask(true);
        GlStateManager.colorMask(true, true, true, true);glColorMask(true, true, true, true);
        glDisable(GL_SCISSOR_TEST);
        GlStateManager.clearColor(.8f, .1f, .2f, .9f);glClearColor(.8f, .1f, .2f, .9f);
        GlStateManager.clearDepth(.25);glClearDepth(.25);
        glClear(GL_COLOR_BUFFER_BIT | (target.useDepth ? GL_DEPTH_BUFFER_BIT : 0));
        glEnable(GL_SCISSOR_TEST);glScissor(7, 9, 41, 23);
        GlStateManager.colorMask(true, false, true, true);glColorMask(true, false, true, true);
        glViewport(3, 5, 21, 19);
    }

    private static void checkGaussianProgramKernels() {
        try (RenderState state = new RenderState();
             ShaderUtil scene = new ShaderUtil("Flauxy/Shaders/gaussian.frag");
             ShaderUtil halo = new ShaderUtil("Flauxy/Shaders/glow.frag")) {
            Framebuffer input = new Framebuffer(127, 83, false), result = new Framebuffer(127, 83, false);
            try {
                input.setFramebufferFilter(GL_LINEAR);
                input.bindFramebuffer(true);
                glDisable(GL_SCISSOR_TEST);
                GlStateManager.colorMask(true, true, true, true);glColorMask(true, true, true, true);
                GlStateManager.clearColor(0, 0, 0, 0);glClearColor(0, 0, 0, 0);glClear(GL_COLOR_BUFFER_BIT);
                for (int i = 0; i < 15; i++) {
                    glEnable(GL_SCISSOR_TEST);glScissor((i * 23) % 127, (i * 17) % 83, 19, 13);
                    glClearColor((i % 3) / 2f, (i % 5) / 4f, (i % 7) / 6f, (i + 1) / 16f);
                    glClear(GL_COLOR_BUFFER_BIT);
                }
                FloatBuffer weights = BufferUtils.createFloatBuffer(256);
                for (int round = 0; round < 2; round++) {
                    for (int radius : new int[]{1, 7, 42, 7, 127, 1}) {
                        for (boolean glow : new boolean[]{false, true}) {
                            for (int direction = 0; direction < 2; direction++) {
                                float dx = direction == 0 ? 1 : 0, dy = direction == 0 ? 0 : 1;
                                drawUncachedReference(result, input.framebufferTexture, glow ? halo : scene,
                                        weights, radius, dx, dy, glow);
                                byte[] expected = readRgba(result);
                                GaussianBlur.renderPass(result, input.framebufferTexture, dx, dy, radius, glow, 1.4f);
                                SessionInfoRenderCheck.require(Arrays.equals(expected, readRgba(result)),
                                        "Cached Gaussian differs: radius=" + radius + " glow=" + glow + " direction=" + direction);
                            }
                        }
                    }
                    // A fresh linked program must receive its weights even if its radius is unchanged.
                    GaussianBlur.releaseResources();
                }
            } finally { result.deleteFramebuffer();input.deleteFramebuffer(); }
        }
    }

    private static void drawUncachedReference(Framebuffer target, int input, ShaderUtil shader, FloatBuffer weights,
                                               int radius, float dx, float dy, boolean glow) {
        try (RenderState state = new RenderState()) {
            state.screenSpace();target.bindFramebuffer(true);
            GlStateManager.disableBlend();glDisable(GL_BLEND);GlStateManager.disableCull();glDisable(GL_CULL_FACE);
            GlStateManager.colorMask(true, true, true, true);glColorMask(true, true, true, true);
            GlStateManager.clearColor(0, 0, 0, 0);glClearColor(0, 0, 0, 0);glClear(GL_COLOR_BUFFER_BIT);
            GlStateManager.setActiveTexture(GL13.GL_TEXTURE0);GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GlStateManager.enableTexture2D();glEnable(GL_TEXTURE_2D);GlStateManager.bindTexture(input);glBindTexture(GL_TEXTURE_2D, input);
            shader.init();shader.setUniformi("textureIn", 0);
            shader.setUniformf("texelSize", 1f / target.framebufferWidth, 1f / target.framebufferHeight);
            shader.setUniformf("direction", dx, dy);shader.setUniformf("radius", radius);
            weights.clear();float sum = 0;
            for (int i = 0; i <= radius; i++) {
                float value = (float)Math.exp(-i * i / (2f * (radius / 2f) * (radius / 2f)));
                weights.put(value);sum += i == 0 ? value : 2 * value;
            }
            for (int i = 0; i <= radius; i++) weights.put(i, weights.get(i) / sum);
            weights.flip();glUniform1(shader.getUniform("weights"), weights);
            if (glow) {
                shader.setUniformi("avoidTexture", 0);shader.setUniformf("color", 1, 1, 1);shader.setUniformf("exposure", 1.4f);
            }
            ShaderUtil.drawQuads();shader.unload();
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkBloomAttachments() throws Exception {
        SessionInfoRenderCheck.backdrop();
        Framebuffer mask = KawaseBloom.drawRectsToFramebuffer(Collections.singletonList(
                new float[]{15, 19, 45, 27, 5, Float.intBitsToFloat(0xc0e91e63)}));
        try (RenderState state = new RenderState()) {
            state.screenSpace();
            KawaseBloom.renderBlur(mask.framebufferTexture, 2, 2);
            SessionInfoRenderCheck.require(mask.useDepth && mask.depthBuffer > 0, "Bloom mask lost its legacy depth attachment");
            java.lang.reflect.Field field = KawaseBloom.class.getDeclaredField("framebufferList");field.setAccessible(true);
            for (Framebuffer target : (List<Framebuffer>)field.get(null))
                SessionInfoRenderCheck.require(target.useDepth && target.depthBuffer > 0, "Bloom filter lost its legacy depth attachment");
        }
    }

    private static void checkRenderStateSnapshots() throws Exception {
        try (RenderState original = new RenderState()) {
            SessionInfoRenderCheck.backdrop();
            seedSnapshot(0);
            List<Integer> initial = snapshot();
            int attributes = glGetInteger(GL_ATTRIB_STACK_DEPTH);
            RenderState outer = new RenderState();
            try {
                Object outerBuffers = SessionInfoRenderCheck.get(outer, "buffers");
                seedSnapshot(1);
                List<Integer> nestedEntry = snapshot();
                Object innerBuffers;
                try (RenderState inner = new RenderState()) {
                    innerBuffers = SessionInfoRenderCheck.get(inner, "buffers");
                    SessionInfoRenderCheck.require(innerBuffers != outerBuffers, "Nested state borrowed the outer snapshot");
                    seedSnapshot(2);
                }
                SessionInfoRenderCheck.require(nestedEntry.equals(snapshot()), "Inner close lost its own entry state");
                try (RenderState warm = new RenderState()) {
                    SessionInfoRenderCheck.require(SessionInfoRenderCheck.get(warm, "buffers") == innerBuffers,
                            "Completed state did not reuse its direct storage");
                    seedSnapshot(3);
                }
                SessionInfoRenderCheck.require(nestedEntry.equals(snapshot()), "Warm reuse overwrote the outer snapshot");
            } finally { outer.close(); }
            SessionInfoRenderCheck.require(initial.equals(snapshot()) && glGetInteger(GL_ATTRIB_STACK_DEPTH) == attributes,
                    "Outer close failed to restore its original snapshot");
            outer.close();
            SessionInfoRenderCheck.require(initial.equals(snapshot()) && glGetInteger(GL_ATTRIB_STACK_DEPTH) == attributes,
                    "Second close changed GL state or popped a second attribute frame");
            try (RenderState first = new RenderState(); RenderState second = new RenderState()) {
                SessionInfoRenderCheck.require(SessionInfoRenderCheck.get(first, "buffers") != SessionInfoRenderCheck.get(second, "buffers"),
                        "Double close returned one snapshot to two live scopes");
            }
        }
    }

    private static void seedSnapshot(int seed) {
        glMatrixMode(GL_MODELVIEW);glLoadIdentity();glTranslatef(seed + 1, seed + 3, seed + 5);
        glMatrixMode(GL_PROJECTION);glLoadIdentity();glScalef(seed + 1, seed + 2, seed + 3);
        GlStateManager.color(seed / 8f, .25f + seed / 8f, .75f - seed / 8f, 1f);
        glColor4f(seed / 8f, .25f + seed / 8f, .75f - seed / 8f, 1f);
        GlStateManager.clearColor(seed / 8f, .125f, .5f, .75f);glClearColor(seed / 8f, .125f, .5f, .75f);
        GlStateManager.colorMask(true, (seed & 1) == 0, false, true);glColorMask(true, (seed & 1) == 0, false, true);
        glEnable(GL_SCISSOR_TEST);glScissor(7 + seed, 9 + seed, 41 - seed, 23 + seed);
        int unit = (seed & 1) == 0 ? GL13.GL_TEXTURE0 : GL13.GL_TEXTURE1;
        GlStateManager.setActiveTexture(unit);GL13.glActiveTexture(unit);
        GlStateManager.bindTexture(SessionInfoRenderCheck.mc.getFramebuffer().framebufferTexture);
        glBindTexture(GL_TEXTURE_2D, SessionInfoRenderCheck.mc.getFramebuffer().framebufferTexture);
    }

    private static List<Integer> snapshot() {
        List<Integer> result = new ArrayList<>();
        for (int name : new int[]{GL_MATRIX_MODE, GL_CURRENT_PROGRAM, GL13.GL_ACTIVE_TEXTURE,
                GL_TEXTURE_BINDING_2D, EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT}) result.add(glGetInteger(name));
        for (int cap : new int[]{GL_BLEND, GL_DEPTH_TEST, GL_ALPHA_TEST, GL_SCISSOR_TEST, GL_STENCIL_TEST})
            result.add(glIsEnabled(cap) ? 1 : 0);
        FloatBuffer floats = BufferUtils.createFloatBuffer(16);
        for (int name : new int[]{GL_MODELVIEW_MATRIX, GL_PROJECTION_MATRIX, GL_CURRENT_COLOR, GL_COLOR_CLEAR_VALUE}) {
            glGetFloat(name, floats);
            int length = name == GL_MODELVIEW_MATRIX || name == GL_PROJECTION_MATRIX ? 16 : 4;
            for (int i = 0; i < length; i++) result.add(Float.floatToIntBits(floats.get(i)));
        }
        IntBuffer ints = BufferUtils.createIntBuffer(16);
        for (int name : new int[]{GL_VIEWPORT, GL_SCISSOR_BOX}) {
            glGetInteger(name, ints);
            for (int i = 0; i < 4; i++) result.add(ints.get(i));
        }
        ByteBuffer mask = BufferUtils.createByteBuffer(16);glGetBoolean(GL_COLOR_WRITEMASK, mask);
        for (int i = 0; i < 4; i++) result.add((int)mask.get(i));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static void checkBloomQueue() throws Exception {
        ModuleManager previous = Pulsar.INSTANCE.getMm();
        SessionInfoRenderCheck.set(Pulsar.INSTANCE, "mm", new ModuleManager());
        try (RenderState guard = new RenderState()) {
            ClientSettings settings = ClientSettings.current();
            settings.glow.setValue(true);settings.gradientMotion.setValue("Static");settings.reducedMotion.setValue(true);
            UiEffects.invalidate();SessionInfoRenderCheck.backdrop();
            List<float[]> queued = (List<float[]>)staticField(GuiShaderBloom.class, "queuedRects");
            List<Runnable> after = (List<Runnable>)staticField(GuiShaderBloom.class, "afterBatch");
            int[] callbacks = {0};
            GuiShaderBloom.beginBatch();
            glEnable(GL_SCISSOR_TEST);glScissor(7, 9, 41, 23);
            GuiShaderBloom.queueRoundedRect(15, 19, 45, 27, 5, 0xc0e91e63);
            GuiShaderBloom.beginBatch();
            glScissor(11, 13, 27, 19);
            GuiShaderBloom.queueWorldRect(18, 24, 37, 21, 4, 0xc012c9e0, true);
            SessionInfoRenderCheck.require(queued.size() == 2, "Nested queue lost its parent work");
            float[] first = queued.get(0), second = queued.get(1);
            SessionInfoRenderCheck.require(first[6] == 1 && first[7] == 7 && first[8] == 9
                            && first[9] == 41 && first[10] == 23 && first[11] == 0 && first[12] == 0
                            && second[7] == 11 && second[8] == 13 && second[9] == 27 && second[10] == 19
                            && second[11] == 1 && second[12] == 1
                            && Float.floatToRawIntBits(first[5]) == 0xc0e91e63
                            && Float.floatToRawIntBits(second[5]) == 0xc012c9e0,
                    "Queue did not capture each rectangle's clip, color and geometry role");
            GuiShaderBloom.afterBatch(() -> callbacks[0]++);
            GuiShaderBloom.flushBatch(1, 1);
            SessionInfoRenderCheck.require(GuiShaderBloom.isBatching() && queued.size() == 2 && callbacks[0] == 0,
                    "Nested flush consumed its parent batch");
            glDisable(GL_SCISSOR_TEST);
            List<Integer> caller = snapshot();
            GuiShaderBloom.flushBatch(1, 1);
            SessionInfoRenderCheck.require(!GuiShaderBloom.isBatching() && !GuiShaderBloom.isCapturing()
                            && queued.isEmpty() && after.isEmpty() && callbacks[0] == 1 && caller.equals(snapshot()),
                    "Outer flush failed to reset queues/callbacks or restore caller state");
            GuiShaderBloom.beginBatch();
            GuiShaderBloom.queueRoundedRect(22, 33, 14, 9, 2, 0x80223344);
            float[] unclipped = queued.get(0);
            SessionInfoRenderCheck.require(unclipped[6] == 0 && unclipped[7] == 0 && unclipped[8] == 0
                            && unclipped[9] == 0 && unclipped[10] == 0 && unclipped[11] == 0 && unclipped[12] == 0,
                    "Unclipped rectangle inherited clip/role data");
            settings.glow.setValue(false);UiEffects.invalidate();GuiShaderBloom.flushBatch(1, 1);
            SessionInfoRenderCheck.require(queued.isEmpty() && after.isEmpty() && !GuiShaderBloom.isBatching(),
                    "Disabled-effect flush retained work");
            settings.glow.setValue(true);UiEffects.invalidate();
            UIUtils.drawRound(0, 0, 1, 1, 0, java.awt.Color.WHITE);
            ShaderUtil original = UIUtils.roundedShader;
            try (ShaderUtil failing = new ShaderUtil("roundedRect") {
                @Override public void init() { throw new IllegalStateException("bloom queue fixture"); }
            }) {
                UIUtils.roundedShader = failing;
                GuiShaderBloom.beginBatch();GuiShaderBloom.queueRoundedRect(10, 12, 30, 20, 3, -1);
                GuiShaderBloom.afterBatch(() -> callbacks[0]++);
                caller = snapshot();
                try {
                    GuiShaderBloom.flushBatch(1, 1);
                    throw new AssertionError("Expected bloom mask failure");
                } catch (IllegalStateException expected) {
                    SessionInfoRenderCheck.require("bloom queue fixture".equals(expected.getMessage()), "Unexpected bloom failure");
                }
                SessionInfoRenderCheck.require(!GuiShaderBloom.isBatching() && !GuiShaderBloom.isCapturing()
                                && queued.isEmpty() && after.isEmpty() && callbacks[0] == 1 && caller.equals(snapshot()),
                        "Aborted bloom retained work, ran composites, or leaked caller state");
            } finally { UIUtils.roundedShader = original; }
        } finally {
            GuiShaderBloom.releaseResources();
            SessionInfoRenderCheck.set(Pulsar.INSTANCE, "mm", previous);UiEffects.invalidate();
        }
    }

    private static Object staticField(Class<?> owner, String name) throws Exception {
        java.lang.reflect.Field field = owner.getDeclaredField(name);field.setAccessible(true);return field.get(null);
    }

    private static byte[] readRgba(Framebuffer target) {
        target.bindFramebuffer(true);
        ByteBuffer pixels = BufferUtils.createByteBuffer(target.framebufferWidth * target.framebufferHeight * 4);
        glReadPixels(0, 0, target.framebufferWidth, target.framebufferHeight, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] rgba = new byte[pixels.remaining()];pixels.get(rgba);return rgba;
    }

    private static float[] readDepth(Framebuffer target) {
        FloatBuffer pixels = BufferUtils.createFloatBuffer(target.framebufferWidth * target.framebufferHeight);
        glReadPixels(0, 0, target.framebufferWidth, target.framebufferHeight, GL_DEPTH_COMPONENT, GL_FLOAT, pixels);
        float[] depth = new float[pixels.remaining()];pixels.get(depth);return depth;
    }
}
