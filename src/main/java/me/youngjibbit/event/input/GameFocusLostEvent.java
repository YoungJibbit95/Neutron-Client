package me.youngjibbit.event.input;

import me.youngjibbit.event.Event;
import me.zero.alpine.event.EventPhase;
import net.minecraft.client.Minecraft;

/** Published after Minecraft has released gameplay input, even between two ticks. */
public final class GameFocusLostEvent extends Event {
    public final Minecraft client;

    public GameFocusLostEvent(Minecraft client) {
        this.client = client;
        setEventPhase(EventPhase.POST);
    }
}
