package me.youngjibbit.utils.render;

import java.nio.FloatBuffer;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;

interface UiDrawScope extends AutoCloseable {
    @Override void close();
}

/** Only for UIUtils' callback-free primitives: no target, matrix or resource lifecycle changes. */
final class UiDrawState implements UiDrawScope {
    // Calls run on the render thread. Copy immediately, so nested scopes retain independent colors.
    private static final FloatBuffer COLOR = BufferUtils.createFloatBuffer(16);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int active = glGetInteger(GL13.GL_ACTIVE_TEXTURE);
    private final boolean blend = glIsEnabled(GL_BLEND), alpha = glIsEnabled(GL_ALPHA_TEST);
    private final int src = glGetInteger(GL14.GL_BLEND_SRC_RGB), dst = glGetInteger(GL14.GL_BLEND_DST_RGB);
    private final int srcAlpha = glGetInteger(GL14.GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL14.GL_BLEND_DST_ALPHA);
    private final int alphaFunction = glGetInteger(GL_ALPHA_TEST_FUNC);
    private final float alphaReference = glGetFloat(GL_ALPHA_TEST_REF);
    private final float red, green, blue, opacity;
    private final int texture0, texture2, texture3;

    static UiDrawScope capture(ShaderUtil shader) {
        // The public legacy fields can contain subclasses with arbitrary rendering callbacks.
        if (shader.getClass() != ShaderUtil.class) {
            RenderState state = new RenderState();
            return state::close;
        }
        return new UiDrawState();
    }

    private UiDrawState() {
        glGetFloat(GL_CURRENT_COLOR, COLOR);
        red = COLOR.get(0); green = COLOR.get(1); blue = COLOR.get(2); opacity = COLOR.get(3);
        GL13.glActiveTexture(GL13.GL_TEXTURE0); texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE2); texture2 = glGetInteger(GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE3); texture3 = glGetInteger(GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(active);
        glPushAttrib(GL_ENABLE_BIT | GL_COLOR_BUFFER_BIT | GL_CURRENT_BIT | GL_TEXTURE_BIT);
    }

    @Override public void close() {
        // Switching away can finish deletion of an already deletion-pending caller program.
        glUseProgram(program == 0 || glIsProgram(program) ? program : 0);
        glPopAttrib();
        // Match RenderState's setters: these also honor OptiFine locks and shader uniforms.
        if (blend) GlStateManager.enableBlend(); else GlStateManager.disableBlend();
        if (alpha) GlStateManager.enableAlpha(); else GlStateManager.disableAlpha();
        GlStateManager.alphaFunc(alphaFunction, alphaReference);
        GlStateManager.tryBlendFuncSeparate(src, dst, srcAlpha, dstAlpha);
        GlStateManager.color(red, green, blue, opacity);
        restoreTexture(GL13.GL_TEXTURE0, texture0);
        restoreTexture(GL13.GL_TEXTURE2, texture2);
        restoreTexture(GL13.GL_TEXTURE3, texture3);
        GlStateManager.setActiveTexture(active);
        GL13.glActiveTexture(active);
    }

    private static void restoreTexture(int unit, int texture) {
        GlStateManager.setActiveTexture(unit);
        GL13.glActiveTexture(unit);
        GlStateManager.bindTexture(texture);
        glBindTexture(GL_TEXTURE_2D, texture);
    }
}
