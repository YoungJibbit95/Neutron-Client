package me.youngjibbit.utils.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.resources.IResourceManager;
import net.optifine.shaders.Shaders;

public class CustomEntityRenderer extends EntityRenderer {
    private ShaderWorldOverlayBridge shaderWorldOverlays;

    public CustomEntityRenderer(Minecraft mc) {
        super(mc, mc.getResourceManager());
    }

    @Override
    protected void renderWorldOverlays(int pass, float partialTicks) {
        // Some isolated native probes intentionally precede Minecraft/OptiFine startup.
        boolean shaderpack = OpenGlHelper.shadersSupported && Shaders.shaderPackLoaded;
        if (shaderpack && Shaders.isShadowPass) return;
        if (!Pulsar.hasWorldOverlayListeners()) {
            releaseClientRenderResources();
            return;
        }
        if (shaderpack) {
            if (shaderWorldOverlays == null) shaderWorldOverlays = new ShaderWorldOverlayBridge();
            shaderWorldOverlays.capture(Minecraft.getMinecraft(), pass, partialTicks);
            return;
        }
        releaseClientRenderResources();
        // This outer scope also restores the renderer if an event consumer fails.
        try (RenderState ignored = new RenderState()) {
            Pulsar.BUS.post(new Event3D(partialTicks, pass));
        }
    }

    @Override
    protected void renderShaderpackWorldOverlays(int pass, float partialTicks) {
        if (shaderWorldOverlays == null) return;
        if (!OpenGlHelper.shadersSupported || !Shaders.shaderPackLoaded
                || Shaders.isShadowPass || !Pulsar.hasWorldOverlayListeners()) {
            releaseClientRenderResources();
            return;
        }
        shaderWorldOverlays.render(Minecraft.getMinecraft(), pass, partialTicks,
                () -> Pulsar.BUS.post(new Event3D(partialTicks, pass)));
    }

    @Override
    public void releaseClientRenderResources() {
        if (shaderWorldOverlays == null) return;
        ShaderWorldOverlayBridge owned = shaderWorldOverlays;
        shaderWorldOverlays = null;
        owned.close();
    }

    @Override
    public void onResourceManagerReload(IResourceManager resources) {
        try { super.onResourceManagerReload(resources); }
        finally { releaseClientRenderResources(); }
    }
}
