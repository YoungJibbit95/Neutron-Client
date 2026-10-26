package me.youngjibbit.event.packet;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;
import net.minecraft.network.Packet;

@Getter
@Setter
/**
 * Play connections only, on the client thread. PRE may cancel or replace the packet.
 * INBOUND POST follows completed application; OUTBOUND POST means local transport acceptance,
 * never remote acknowledgement. POST mutation/cancellation has no effect. Silent calls omit both.
 */
public final class EventPacket extends Event {

    private Packet<?> packet;
    @Getter private final me.youngjibbit.core.network.PacketPipeline pipeline;

    public EventPacket(Packet<?> packet) {
        this(packet, null);
    }
    public EventPacket(Packet<?> packet, me.youngjibbit.core.network.PacketPipeline pipeline) {
        this.packet = packet;
        this.pipeline = pipeline;
    }
}
