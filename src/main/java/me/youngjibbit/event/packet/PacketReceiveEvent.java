package me.youngjibbit.event.packet;

import me.youngjibbit.core.network.PacketPipeline;
import me.youngjibbit.event.Event;
import net.minecraft.network.Packet;

/** Main-thread receipt of the final, non-cancelled inbound packet, before delay/application. Not emitted on replay. */
public final class PacketReceiveEvent extends Event {
    private final Packet<?> packet;
    private final PacketPipeline pipeline;
    public PacketReceiveEvent(Packet<?> packet, PacketPipeline pipeline) { this.packet=packet;this.pipeline=pipeline; }
    public Packet<?> getPacket() { return packet; }
    public PacketPipeline getPipeline() { return pipeline; }
}
