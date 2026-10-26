package me.youngjibbit.utils.render;

import me.youngjibbit.Pulsar;
import me.youngjibbit.event.render.Event3D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;

public class CustomEntityRenderer extends EntityRenderer {

    public CustomEntityRenderer(Minecraft mc) {
        super(mc, mc.getResourceManager());
    }

    @Override
    protected void renderWorldOverlays(int pass, float partialTicks) {
        // This outer scope also restores the renderer if an event consumer fails.
        try (RenderState ignored = new RenderState()) {
            Pulsar.BUS.post(new Event3D(partialTicks, pass));
        }
    }
}
