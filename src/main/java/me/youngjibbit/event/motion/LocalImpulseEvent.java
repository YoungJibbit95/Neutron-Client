package me.youngjibbit.event.motion;

import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.Event;
import net.minecraft.client.entity.EntityPlayerSP;

/** Accepted local velocity/explosion, immediately before applying it. Notification only. */
public final class LocalImpulseEvent extends Event {
    private final EntityPlayerSP player;
    private final PacketPipeline pipeline;

    public LocalImpulseEvent(EntityPlayerSP player, PacketPipeline pipeline) {
        this.player = player;
        this.pipeline = pipeline;
    }
    public EntityPlayerSP getPlayer() { return player; }
    public PacketPipeline getPipeline() { return pipeline; }
}
