package me.youngjibbit.module.impl.render;

import java.awt.Color;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import me.youngjibbit.Pulsar;
import me.youngjibbit.module.ModuleManager;
import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.*;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.optifine.render.GlAlphaState;
import net.optifine.render.GlBlendState;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

/** Uses a delegating shader subclass to select the old full guard without changing draw bodies. */
public final class UiDrawStateCheck {
    private static final String[] FIELDS = {"roundedShader", "roundedGradientShader", "roundedOutlineShader", "roundedTexturedShader"};
    private static final int[] UNITS = {GL13.GL_TEXTURE0, GL13.GL_TEXTURE1, GL13.GL_TEXTURE2, GL13.GL_TEXTURE3, GL13.GL_TEXTURE16};
    private UiDrawStateCheck() { }

    public static void run() throws Exception {
        ModuleManager old = Pulsar.INSTANCE.getMm();
        SessionInfoRenderCheck.set(Pulsar.INSTANCE, "mm", new ModuleManager());
        ArrayListSurface surface = new ArrayListSurface();
        int[] textures = new int[UNITS.length];
        try (RenderState guard = new RenderState(); ShaderUtil caller = new ShaderUtil("roundedRect")) {
            ClientSettings settings = ClientSettings.current();
            settings.gradientMotion.setValue("Static"); settings.reducedMotion.setValue(true);
            settings.surface.setValue("Liquid Glass"); UiEffects.invalidate();
            // Earlier native checks may have released every lazy UI program.
            SessionInfoRenderCheck.backdrop(); UIUtils.drawRound(0, 0, 1, 1, 0, Color.WHITE);
            for (int i = 0; i < textures.length; i++) {
                textures[i] = glGenTextures(); glBindTexture(GL_TEXTURE_2D, textures[i]);
                ByteBuffer pixel = BufferUtils.createByteBuffer(4); pixel.putInt(-1).flip();
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, 1, 1, 0, GL_RGBA, GL_UNSIGNED_BYTE, pixel);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            }
            for (int mode = 0; mode < FIELDS.length; mode++) {
                Field field = UIUtils.class.getDeclaredField(FIELDS[mode]); field.setAccessible(true);
                ShaderUtil shader = (ShaderUtil)field.get(null);
                try (Delegate legacy = new Delegate(shader)) {
                    byte[] expected = frame(surface, mode, textures, caller, field, legacy, false);
                    byte[] actual = frame(surface, mode, textures, caller, field, shader, false);
                    require(Arrays.equals(expected, actual), "Primitive RGBA differs: " + FIELDS[mode]);
                    expected = frame(surface, mode, textures, caller, field, legacy, true);
                    actual = frame(surface, mode, textures, caller, field, shader, true);
                    require(Arrays.equals(expected, actual), "Locked primitive RGBA differs: " + FIELDS[mode]);
                } finally { field.set(null, shader); }
            }
            checkSubclassFallback(textures, caller);
            checkNestedAndException(textures, caller);
            checkDeletedProgram();
            require(glGetError() == GL_NO_ERROR, "UI draw guard GL error");
            System.out.println("PASS: four UI primitives match full-guard RGBA; scene textures, caller GL/cache state, locks, subclass fallback and deletion-pending program");
        } finally {
            for (int texture : textures) if (texture != 0) GlStateManager.deleteTexture(texture);
            surface.close(); SessionInfoRenderCheck.set(Pulsar.INSTANCE, "mm", old); UiEffects.invalidate();
        }
    }

    private static byte[] frame(ArrayListSurface surface, int mode, int[] textures, ShaderUtil caller,
                                Field field, ShaderUtil shader, boolean locked) throws Exception {
        field.set(null, shader);
        SessionInfoRenderCheck.backdrop();
        surface.withScene(Theme.getDefaultTheme(), () -> {
            seed(textures, caller);
            if (locked) {
                GlStateManager.lockAlpha(new GlAlphaState(true, GL_GREATER, .27f));
                GlStateManager.lockBlend(new GlBlendState(true, GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO));
            }
            try {
                List<Integer> before = snapshot();
                float[] depth = readDepth();
                draw(mode);
                require(before.equals(snapshot()), "Primitive leaked caller GL state: " + mode);
                require(Arrays.equals(depth, readDepth()), "Primitive changed depth attachment: " + mode);
            } finally {
                if (locked) { GlStateManager.unlockAlpha(); GlStateManager.unlockBlend(); }
            }
            checkCaches(textures);
        });
        int width = SessionInfoRenderCheck.mc.displayWidth, height = SessionInfoRenderCheck.mc.displayHeight;
        ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
        glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        byte[] result = new byte[pixels.remaining()]; pixels.get(result); return result;
    }

    private static void seed(int[] textures, ShaderUtil caller) {
        GlStateManager.disableDepth(); glDisable(GL_DEPTH_TEST);
        GlStateManager.depthMask(false); glDepthMask(false);
        GlStateManager.enableCull(); glEnable(GL_CULL_FACE);
        GlStateManager.disableLighting(); glDisable(GL_LIGHTING);
        GlStateManager.enableBlend(); glEnable(GL_BLEND);
        GlStateManager.tryBlendFuncSeparate(GL_ONE, GL_ZERO, GL_DST_ALPHA, GL_ONE_MINUS_DST_ALPHA);
        GL14.glBlendFuncSeparate(GL_ONE, GL_ZERO, GL_DST_ALPHA, GL_ONE_MINUS_DST_ALPHA);
        GlStateManager.enableAlpha(); glEnable(GL_ALPHA_TEST);
        GlStateManager.alphaFunc(GL_LESS, .37f); glAlphaFunc(GL_LESS, .37f);
        GlStateManager.color(.25f, .5f, .75f, .625f); glColor4f(.25f, .5f, .75f, .625f);
        glEnable(GL_SCISSOR_TEST); glScissor(3, 5, SessionInfoRenderCheck.mc.displayWidth - 6, SessionInfoRenderCheck.mc.displayHeight - 10);
        for (int i = 0; i < UNITS.length; i++) {
            GlStateManager.setActiveTexture(UNITS[i]); GL13.glActiveTexture(UNITS[i]);
            GlStateManager.bindTexture(textures[i]); glBindTexture(GL_TEXTURE_2D, textures[i]);
            if ((i & 1) == 0) { GlStateManager.enableTexture2D(); glEnable(GL_TEXTURE_2D); }
            else { GlStateManager.disableTexture2D(); glDisable(GL_TEXTURE_2D); }
        }
        glMatrixMode(GL_PROJECTION); // Old full guard temporarily changes this; drawing must not depend on it.
        caller.init();
    }

    private static void draw(int mode) {
        switch (mode) {
            case 0: UIUtils.drawRound(25, 35, 130, 50, 7, new Color(55, 90, 160, 190)); break;
            case 1: UIUtils.drawGradientHorizontal(25, 35, 130, 50, 7, new Color(200, 35, 95, 180), new Color(20, 150, 240, 210)); break;
            case 2: UIUtils.drawRoundOutline(25, 35, 130, 50, 7, 1.25f, new Color(55, 90, 160, 190), new Color(230, 90, 40, 180)); break;
            case 3: UIUtils.drawRoundTextured(25, 35, 130, 50, 7, .7f); break;
            default: throw new AssertionError(mode);
        }
    }

    private static List<Integer> snapshot() {
        List<Integer> values = new ArrayList<>();
        for (int name : new int[]{GL_CURRENT_PROGRAM, GL13.GL_ACTIVE_TEXTURE,
                org.lwjgl.opengl.EXTFramebufferObject.GL_FRAMEBUFFER_BINDING_EXT, GL_MATRIX_MODE,
                GL14.GL_BLEND_SRC_RGB, GL14.GL_BLEND_DST_RGB, GL14.GL_BLEND_SRC_ALPHA, GL14.GL_BLEND_DST_ALPHA,
                GL_ALPHA_TEST_FUNC, GL_DEPTH_FUNC, GL_ATTRIB_STACK_DEPTH}) values.add(glGetInteger(name));
        values.add(Float.floatToIntBits(glGetFloat(GL_ALPHA_TEST_REF)));
        values.add(glGetBoolean(GL_DEPTH_WRITEMASK) ? 1 : 0);
        for (int cap : new int[]{GL_BLEND, GL_ALPHA_TEST, GL_DEPTH_TEST, GL_LIGHTING, GL_CULL_FACE, GL_SCISSOR_TEST, GL_STENCIL_TEST}) values.add(glIsEnabled(cap) ? 1 : 0);
        FloatBuffer floats = BufferUtils.createFloatBuffer(16);
        for (int name : new int[]{GL_CURRENT_COLOR, GL_MODELVIEW_MATRIX, GL_PROJECTION_MATRIX}) {
            glGetFloat(name, floats); int count = name == GL_CURRENT_COLOR ? 4 : 16;
            for (int i = 0; i < count; i++) values.add(Float.floatToIntBits(floats.get(i)));
        }
        IntBuffer ints = BufferUtils.createIntBuffer(16);
        for (int name : new int[]{GL_VIEWPORT, GL_SCISSOR_BOX}) { glGetInteger(name, ints); for (int i = 0; i < 4; i++) values.add(ints.get(i)); }
        int active = glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int unit : UNITS) { GL13.glActiveTexture(unit); values.add(glGetInteger(GL_TEXTURE_BINDING_2D)); values.add(glIsEnabled(GL_TEXTURE_2D) ? 1 : 0); }
        GL13.glActiveTexture(active); return values;
    }

    private static void checkCaches(int[] textures) {
        GlStateManager.disableBlend(); require(!glIsEnabled(GL_BLEND), "Blend cache stale");
        GlStateManager.enableBlend(); require(glIsEnabled(GL_BLEND), "Blend enable cache stale");
        GlStateManager.disableAlpha(); require(!glIsEnabled(GL_ALPHA_TEST), "Alpha cache stale");
        GlStateManager.enableAlpha(); require(glIsEnabled(GL_ALPHA_TEST), "Alpha enable cache stale");
        GlStateManager.alphaFunc(GL_GREATER, .13f); require(glGetInteger(GL_ALPHA_TEST_FUNC) == GL_GREATER && glGetFloat(GL_ALPHA_TEST_REF) == .13f, "Alpha factors cache stale");
        GlStateManager.tryBlendFuncSeparate(GL_ZERO, GL_ONE, GL_ONE, GL_ZERO);
        require(glGetInteger(GL14.GL_BLEND_SRC_RGB) == GL_ZERO && glGetInteger(GL14.GL_BLEND_DST_ALPHA) == GL_ZERO, "Blend factors cache stale");
        GlStateManager.color(.1f, .2f, .3f, .4f); FloatBuffer color = BufferUtils.createFloatBuffer(16); glGetFloat(GL_CURRENT_COLOR, color);
        require(color.get(0) == .1f && color.get(3) == .4f, "Color cache stale");
        for (int i = 0; i < UNITS.length; i++) {
            GlStateManager.setActiveTexture(UNITS[i]); require(glGetInteger(GL13.GL_ACTIVE_TEXTURE) == UNITS[i], "Active texture cache stale");
            GlStateManager.bindTexture(0); require(glGetInteger(GL_TEXTURE_BINDING_2D) == 0, "Texture cache stale");
            GlStateManager.bindTexture(textures[i]); require(glGetInteger(GL_TEXTURE_BINDING_2D) == textures[i], "Texture rebind cache stale");
        }
    }

    private static void checkSubclassFallback(int[] textures, ShaderUtil caller) throws Exception {
        ShaderUtil original = UIUtils.roundedShader;
        Framebuffer temporary = new Framebuffer(32, 32, true);
        try (Delegate custom = new Delegate(original) {
            @Override public void init() {
                temporary.bindFramebuffer(true); glMatrixMode(GL_MODELVIEW); glLoadIdentity(); glTranslatef(5, 6, 7);
                GlStateManager.enableLighting(); glEnable(GL_LIGHTING); super.init();
            }
        }) {
            UIUtils.roundedShader = custom; SessionInfoRenderCheck.backdrop(); seed(textures, caller);
            List<Integer> before = snapshot(); UIUtils.drawRound(25, 35, 100, 30, 5, Color.WHITE);
            require(before.equals(snapshot()), "Custom shader fallback leaked broad state");
        } finally { UIUtils.roundedShader = original; temporary.deleteFramebuffer(); }
        // An exception after draw state changes must restore the same caller state.
        try (Delegate custom = new Delegate(original) { @Override public void init() { throw new IllegalStateException("fixture"); } }) {
            UIUtils.roundedShader = custom; seed(textures, caller); List<Integer> before = snapshot();
            try { draw(0); throw new AssertionError("Expected shader exception"); } catch (IllegalStateException expected) { }
            require(before.equals(snapshot()), "Custom shader exception leaked state");
        } finally { UIUtils.roundedShader = original; }
    }

    private static float[] readDepth() {
        int count = SessionInfoRenderCheck.mc.displayWidth * SessionInfoRenderCheck.mc.displayHeight;
        FloatBuffer buffer = BufferUtils.createFloatBuffer(count);
        glReadPixels(0, 0, SessionInfoRenderCheck.mc.displayWidth, SessionInfoRenderCheck.mc.displayHeight,
                GL_DEPTH_COMPONENT, GL_FLOAT, buffer);
        float[] result = new float[count]; buffer.get(result); return result;
    }

    private static void checkNestedAndException(int[] textures, ShaderUtil caller) throws Exception {
        seed(textures, caller); List<Integer> before = snapshot();
        java.lang.reflect.Method capture = Class.forName("me.youngjibbit.utils.render.UiDrawState")
                .getDeclaredMethod("capture", ShaderUtil.class);
        capture.setAccessible(true);
        try (AutoCloseable scope = (AutoCloseable)capture.invoke(null, caller)) {
            GlStateManager.color(.7f, .6f, .5f, .4f);
            GlStateManager.disableBlend();
            List<Integer> nested = snapshot(); draw(0);
            require(nested.equals(snapshot()), "Nested narrow scope lost its own entry state");
        }
        require(before.equals(snapshot()), "Outer narrow scope reused nested color scratch");
        ShaderUtil original = UIUtils.roundedShader;
        ShaderUtil closed = new ShaderUtil("roundedRect"); closed.close();
        try {
            UIUtils.roundedShader = closed; seed(textures, caller); before = snapshot();
            try { draw(0); throw new AssertionError("Expected closed shader failure"); }
            catch (IllegalStateException expected) { }
            require(before.equals(snapshot()), "Canonical shader exception leaked narrow state");
        } finally { UIUtils.roundedShader = original; }
    }

    private static void checkDeletedProgram() {
        int vertex = glCreateShader(GL_VERTEX_SHADER), fragment = glCreateShader(GL_FRAGMENT_SHADER), program = glCreateProgram();
        glShaderSource(vertex, "#version 120\nvoid main(){gl_Position=gl_Vertex;}"); glCompileShader(vertex);
        glShaderSource(fragment, "#version 120\nvoid main(){gl_FragColor=vec4(1.0);}"); glCompileShader(fragment);
        glAttachShader(program, vertex); glAttachShader(program, fragment); glLinkProgram(program);
        require(glGetProgrami(program, GL_LINK_STATUS) != 0, "Fixture program link failed");
        glDetachShader(program, vertex); glDetachShader(program, fragment); glDeleteShader(vertex); glDeleteShader(fragment);
        glUseProgram(program); glDeleteProgram(program); draw(0);
        require(!glIsProgram(program) && glGetInteger(GL_CURRENT_PROGRAM) == 0, "Deletion-pending caller program restored");
    }

    private static class Delegate extends ShaderUtil {
        private final ShaderUtil target;
        Delegate(ShaderUtil target) { super("roundedRect"); this.target = target; }
        @Override public void init() { target.init(); }
        @Override public void unload() { target.unload(); }
        @Override public int getUniform(String name) { return target.getUniform(name); }
    }
    private static void require(boolean condition, String message) { SessionInfoRenderCheck.require(condition, message); }
}
