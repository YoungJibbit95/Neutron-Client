package me.youngjibbit.event.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;

@Getter
@Setter
public class JumpFixEvent extends Event
{
    @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private boolean rotationCorrectionApplied;
    public boolean claimRotationCorrection() {
        if (rotationCorrectionApplied) return false;
        rotationCorrectionApplied=true;return true;
    }
    public JumpFixEvent(float yaw) { this.yaw=yaw; }

    private float yaw;
}
