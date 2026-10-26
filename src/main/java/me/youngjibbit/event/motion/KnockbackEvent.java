package me.youngjibbit.event.motion;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

@Getter
@Setter
public class KnockbackEvent extends Event {

    private double x;
    private double y;
    private double z;

    public KnockbackEvent(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}
