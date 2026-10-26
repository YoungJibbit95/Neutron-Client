package me.youngjibbit.event.player;

import me.youngjibbit.event.Event;
import me.zero.alpine.event.EventPhase;
import net.minecraft.entity.EntityLivingBase;

/** Main thread, after the controller action and immediate local packet acceptance. No server hit confirmation. */
public final class AttackResultEvent extends Event {
    private final EntityLivingBase targetEntity;
    public AttackResultEvent(EntityLivingBase targetEntity) {
        this.targetEntity = targetEntity;
        setEventPhase(EventPhase.POST);
    }
    public EntityLivingBase getTargetEntity() { return targetEntity; }
}
