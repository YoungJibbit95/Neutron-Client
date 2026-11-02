package me.youngjibbit.event.player;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

/** One local input sample, before item slowdown and vanilla's 0.98 multiplier. */
@Getter
@Setter
public final class MovementInputEvent extends Event {
    private float strafe, forward;
    private boolean jump, sneak;

    public MovementInputEvent(float strafe, float forward, boolean jump, boolean sneak) {
        this.strafe = strafe;
        this.forward = forward;
        this.jump = jump;
        this.sneak = sneak;
    }
}
