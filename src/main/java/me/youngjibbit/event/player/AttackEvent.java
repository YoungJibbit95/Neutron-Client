package me.youngjibbit.event.player;

import lombok.AllArgsConstructor;
import me.youngjibbit.event.Event;
import net.minecraft.entity.EntityLivingBase;

@AllArgsConstructor
public class AttackEvent extends Event
{

    private final EntityLivingBase targetEntity;

    public EntityLivingBase getTargetEntity() {
        return targetEntity;
    }
}
