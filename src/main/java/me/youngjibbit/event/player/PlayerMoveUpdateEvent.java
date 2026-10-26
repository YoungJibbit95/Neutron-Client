package me.youngjibbit.event.player;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import me.youngjibbit.event.Event;
import me.youngjibbit.utils.player.MoveUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.MovementInput;

@Getter
@Setter
public class PlayerMoveUpdateEvent extends Event
{
    @Getter(lombok.AccessLevel.NONE) @Setter(lombok.AccessLevel.NONE)
    private boolean rotationCorrectionApplied;
    public boolean claimRotationCorrection() {
        if (rotationCorrectionApplied) return false;
        rotationCorrectionApplied=true;return true;
    }
    public PlayerMoveUpdateEvent(float strafe, float forward, float friction, float yaw, float pitch) { this.strafe=strafe; this.forward=forward; this.friction=friction; this.yaw=yaw; this.pitch=pitch; }


    private float strafe, forward, friction, yaw, pitch;

    public void applyMotion(double speed, float strafeMotion) {
        float remainder = 1 - strafeMotion;
        EntityPlayerSP player = Minecraft.getMinecraft().thePlayer;
        strafeMotion *= 0.91;
        if (player.onGround) {
            MoveUtil.setSpeedFromInput(player, speed, MovementInput.moveForward, MovementInput.moveStrafe);
        } else {
            player.motionX = player.motionX * strafeMotion;
            player.motionZ = player.motionZ * strafeMotion;
            friction = (float) speed * remainder;
        }
    }
}

