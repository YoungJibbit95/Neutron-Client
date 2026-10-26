package me.youngjibbit.event.render;

import me.youngjibbit.event.Event;

public class JumpCircleEvent extends Event
{
    private float ticks;

    public JumpCircleEvent(float ticks)
    {
        this.ticks = ticks;
    }

    public float getTicks(float ticks)
    {
        return ticks;
    }

    public void setTicks(float ticks)
    {
        this.ticks = ticks;
    }
}

