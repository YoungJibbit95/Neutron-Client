package me.youngjibbit.event.motion;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

@Getter
@Setter
@AllArgsConstructor
public final class EventMove extends Event {
    private double x, y, z;
}
