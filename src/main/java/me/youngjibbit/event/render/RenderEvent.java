package me.youngjibbit.event.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

@Getter
@Setter
public class RenderEvent extends Event
{
    private float partialTicks;

    public float getPartialTicks() {
        return this.partialTicks;
    }

    public RenderEvent(float partialTicks) {
        this.partialTicks = partialTicks;
    }
}

