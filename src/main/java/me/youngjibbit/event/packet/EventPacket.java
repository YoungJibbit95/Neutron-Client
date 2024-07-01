package me.youngjibbit.event.packet;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.network.Packet;
import me.youngjibbit.event.Event;

@Getter
@Setter
@AllArgsConstructor
public final class EventPacket extends Event
{
    private Packet<?> packet;
}
