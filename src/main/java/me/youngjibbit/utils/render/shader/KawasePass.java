package me.youngjibbit.utils.render.shader;

import me.youngjibbit.utils.RenderUtil;
import me.youngjibbit.utils.render.RenderDiagnostics;
import me.youngjibbit.utils.render.ShaderUtil;
import net.minecraft.client.shader.Framebuffer;

/** Intermediate downsample/upsample shared by HUD bloom and entity glow. */
public final class KawasePass {
    private KawasePass() {}

    public static void draw(Framebuffer target, int inputTexture, ShaderUtil shader, float offset) {
        if (RenderDiagnostics.enabled) RenderDiagnostics.blurPasses++;
        target.framebufferClear();
        target.bindFramebuffer(false);
        shader.init();
        shader.setUniformi("uiEnabled", 0);
        shader.setUniformf("lastPass", 0);
        RenderUtil.bindTexture(inputTexture);
        shader.setUniformf("offset", offset, offset);
        shader.setUniformi("inTexture", 0);
        shader.setUniformi("check", 0);
        shader.setUniformf("halfpixel", 1f / target.framebufferWidth, 1f / target.framebufferHeight);
        shader.setUniformf("iResolution", target.framebufferWidth, target.framebufferHeight);
        ShaderUtil.drawQuads();
        shader.unload();
    }
}
