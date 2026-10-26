package me.youngjibbit.event.motion;

import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

@Getter
@Setter
public class EventMotion extends Event {

    private double x, y, z;
    private float yaw, pitch, prevYaw, prevPitch;
    private boolean onGround;
    /** POST: whether a movement packet was locally accepted; PRE cancellation has no POST. */
    private boolean accepted;

    public EventMotion(double x, double y, double z, float yaw, float pitch, boolean onGround) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.onGround = onGround;
    }

    public void setRotations(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
