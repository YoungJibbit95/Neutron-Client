package me.youngjibbit.event.motion;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

public class EventStrafe extends Event
{

    @Getter
    @Setter
    float yaw;
    public EventStrafe(float yaw){
        this.yaw = yaw;
    }
}

