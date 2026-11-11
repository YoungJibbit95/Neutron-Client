package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import java.lang.ref.WeakReference;
import static org.lwjgl.opengl.GL11.*;

/** Colors the original white logo and its baked halo in one draw; no FBO or blur. */
public final class ThemeLogoRenderer implements AutoCloseable {
    private static final int[] SECONDARY_TEXTURE_UNITS = {1, 2, 3, 16};
    private final float[] bounds = new float[4];
    private ShaderUtil shader;
    private IResourceManager resources;
    private boolean dirty, failed;

    public void draw(ResourceLocation texture, float x, float y, float size, Theme requested, float opacity) {
        if (texture == null || !Float.isFinite(x) || !Float.isFinite(y) ||
                !Float.isFinite(size) || size <= 0 || !Float.isFinite(opacity) || opacity <= 0) return;
        Minecraft client = Minecraft.getMinecraft();
        Theme theme = UiEffects.theme(requested);
        float alpha = Math.min(1, opacity);
        // The previous 20px badge sampled u=.30..70 / v=.33..73.
        // Keep that exact source-to-screen scale, but include the full PNG so
        // the original faint halo reaches transparent pixels without a hard crop.
        float sourceSize = size / .4f;
        float leftX = x - sourceSize * .30f, rightX = leftX + sourceSize;
        float topY = y - sourceSize * .33f, bottomY = topY + sourceSize;
        try (RenderState state = new RenderState()) {
            for (int unit : SECONDARY_TEXTURE_UNITS) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit); glDisable(GL_TEXTURE_2D);
            }
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GL13.glActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.enableTexture2D(); glEnable(GL_TEXTURE_2D);
            GlStateManager.disableAlpha(); glDisable(GL_ALPHA_TEST);
            GlStateManager.disableLighting(); glDisable(GL_LIGHTING);
            GlStateManager.disableDepth(); glDisable(GL_DEPTH_TEST);
            GlStateManager.disableCull(); glDisable(GL_CULL_FACE);
            GlStateManager.enableBlend(); glEnable(GL_BLEND);
            GL14.glBlendEquation(GL14.GL_FUNC_ADD);
            GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glTexEnvi(GL_TEXTURE_ENV, GL_TEXTURE_ENV_MODE, GL_MODULATE);
            client.getTextureManager().bindTexture(texture);
            ScreenRect.project(x, y, size, size, bounds);
            if (OpenGlHelper.shadersSupported) {
                watch(client.getResourceManager());
                if (dirty) { close(); dirty = false; }
                if (shader == null && !failed) try {
                    shader = new ShaderUtil("pulsar/shader/ui/logoInk.frag");
                } catch (RuntimeException error) {
                    failed = true;
                    System.err.println("[Island] Logo shader unavailable; using original tinted texture: " + error.getMessage());
                }
            }
            try (UiEffects.GradientSpace area = UiEffects.gradientSpace(bounds[0], bounds[1], bounds[2], bounds[3]);
                 UiEffects.GradientSpace domain = UiEffects.perimeterSpace(bounds[0], bounds[1], bounds[2], bounds[3], bounds[2] * .5f)) {
                boolean programmable = shader != null && OpenGlHelper.shadersSupported;
                if (programmable) {
                    shader.init(); shader.setUniformi("logo", 0);
                    shader.setUniformf("opacity", alpha);
                    boolean glow = UiEffects.scene() == null ? UiEffects.glowEnabled() : UiEffects.scene().policy.glow;
                    shader.setUniformf("haloStrength", glow ? UiEffects.glowAlpha(1.5f * theme.getGlowStrength()) : 0);
                    UiEffects.upload(shader, theme);
                    color("first", theme.getPrimaryGradientRGB()); color("second", theme.getSecondaryGradientRGB());
                    glColor4f(1, 1, 1, 1);
                } else {
                    GL20.glUseProgram(0); GlStateManager.shadeModel(GL_SMOOTH); glShadeModel(GL_SMOOTH);
                }
                // Resolve fallback coordinates before glBegin: the shared
                // gradient may consult viewport state, which is illegal inside it.
                int left = programmable ? -1 : UiEffects.gradientColor(theme, UiEffects.Channel.TEXT, bounds[0], bounds[1]);
                int right = programmable ? -1 : UiEffects.gradientColor(theme, UiEffects.Channel.TEXT,
                        bounds[0] + bounds[2], bounds[1] + bounds[3]);
                // Preserve the old stroke's exact placement while exposing more
                // of the original alpha falloff around it. Never replace the N.
                glBegin(GL_QUADS);
                if (!programmable) tint(left, alpha);
                glTexCoord2f(0, 0); glVertex2f(leftX, topY);
                glTexCoord2f(0, 1); glVertex2f(leftX, bottomY);
                if (!programmable) tint(right, alpha);
                glTexCoord2f(1, 1); glVertex2f(rightX, bottomY);
                glTexCoord2f(1, 0); glVertex2f(rightX, topY);
                glEnd();
                if (RenderDiagnostics.enabled) RenderDiagnostics.shaderDraws++;
            }
        }
    }

    private static void tint(int color, float alpha) {
        glColor4f((color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f, alpha);
    }
    private void color(String name, int color) {
        shader.setUniformf(name, (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f,
                (color & 255) / 255f, (color >>> 24) / 255f);
    }
    private void watch(IResourceManager current) {
        if (resources == current) return;
        resources = current; dirty = true;
        if (current instanceof IReloadableResourceManager) {
            final WeakReference<ThemeLogoRenderer> owner = new WeakReference<>(this);
            ((IReloadableResourceManager) current).registerReloadListener(manager -> {
                ThemeLogoRenderer renderer = owner.get();
                if (renderer != null && renderer.resources == manager) renderer.dirty = true;
            });
        }
    }
    @Override public void close() {
        if (shader != null) { shader.close(); shader = null; }
        failed = false;
    }
}
