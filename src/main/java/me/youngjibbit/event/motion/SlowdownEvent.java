package me.youngjibbit.event.motion;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

/** Item-use input scaling. Sprint permission bypasses only the item-use start gate;
 * the consumer still applies the scaled forward threshold and other vanilla sprint limits. */
public class SlowdownEvent extends Event {
    private float forward;
    private float strafe;
    private boolean allowedSprinting;

    public float getForward() {
        return this.forward;
    }

    public float getStrafe() {
        return this.strafe;
    }

    public boolean isAllowedSprinting() {
        return this.allowedSprinting;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public void setAllowedSprinting(boolean allowedSprinting) {
        this.allowedSprinting = allowedSprinting;
    }

    public SlowdownEvent(float forward, float strafe, boolean allowedSprinting) {
        this.forward = forward;
        this.strafe = strafe;
        this.allowedSprinting = allowedSprinting;
    }
}


