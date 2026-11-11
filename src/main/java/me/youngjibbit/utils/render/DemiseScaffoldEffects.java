package me.youngjibbit.utils.render;

import me.youngjibbit.utils.render.shader.KawaseBloom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.BlockPos;
import org.lwjgl.opengl.GL11;

/** Host compositor for Demise Scaffold's ShaderEvent masks. Geometry stays in the source port. */
public final class DemiseScaffoldEffects {
    private static Framebuffer mask;
    private DemiseScaffoldEffects() { }

    /** Returns true only when a real shader pass consumed the target this HUD frame. */
    public static boolean render(BlockPos target) {
        if (!UiEffects.effectsSupported()) return false;
        boolean called = false;
        if (UiEffects.blurEnabled()) {
            Blur.startBlur();
            try { DemiseScaffoldOverlay.shader(target, false); }
            finally { Blur.endBlur(25, 1); }
            called = true;
        }
        if (UiEffects.glowEnabled()) {
            Minecraft mc = Minecraft.getMinecraft();
            try (RenderState state = new RenderState()) {
                state.screenSpace();
                if (mask == null || mask.framebufferWidth != mc.displayWidth || mask.framebufferHeight != mc.displayHeight) {
                    if (mask != null) mask.deleteFramebuffer();
                    mask = new Framebuffer(mc.displayWidth, mc.displayHeight, false);
                    mask.setFramebufferFilter(GL11.GL_LINEAR);
                }
                mask.bindAndClear();
                GlStateManager.disableDepth();
                GlStateManager.disableLighting();
                GlStateManager.disableAlpha();
                DemiseScaffoldOverlay.shader(target, true);
                state.bindTarget();
                KawaseBloom.renderBlur(mask.framebufferTexture, 3, 1, false);
            }
            called = true;
        }
        return called;
    }

    public static void releaseResources() {
        if (mask == null) return;
        try (RenderState state = new RenderState()) { mask.deleteFramebuffer(); mask = null; }
    }
}
