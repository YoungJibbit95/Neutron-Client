package me.youngjibbit.event.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.youngjibbit.event.Event;
import net.minecraft.client.renderer.entity.RendererLivingEntity;
import net.minecraft.entity.EntityLivingBase;

@Getter
@AllArgsConstructor
public class RendererLivingEntityEvent extends Event
{
    private final EntityLivingBase entity;
    private final RendererLivingEntity<?> renderer;
    private final float partialTicks;
    private final double x, y, z;
}

