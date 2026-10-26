package me.youngjibbit.event.render;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

@Getter
@Setter
@AllArgsConstructor
public class Event3D extends Event {
    private final float partialTicks;
    private float ticks;
    /** Vanilla world pass: 0/1 for anaglyph, 2 for the normal view. */
    private final int pass;

    public Event3D(float partialTicks) {
        this(partialTicks, 2);
    }

    public Event3D(float partialTicks, int pass) {
        this.pass = pass;
        this.ticks = partialTicks;
        this.partialTicks = partialTicks;
    }

    public float getPartialTicks() {
        return partialTicks;
    }

    public float getTicks() {
        return ticks;
    }

    public void setTicks(float ticks) {
        this.ticks = ticks;
    }
}
