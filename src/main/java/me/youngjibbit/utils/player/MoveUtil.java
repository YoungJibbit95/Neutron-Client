package me.youngjibbit.utils.player;

import me.youngjibbit.core.combat.RotationCorrections;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.EntityLivingBase;

/** Explicit local-motion operations. No cached client, packets, input-key or world changes. */
public final class MoveUtil {
    private MoveUtil() { }

    /** Tests the player's movement fields, after the current movement step has processed input. */
    public static boolean hasMovementInput(EntityLivingBase player) {
        return player.moveForward != 0.0F || player.moveStrafing != 0.0F;
    }

    /** Sign-based eight-way direction in degrees; sneak input does not reduce the requested speed. */
    public static float directionYaw(float rotationYaw, float forward, float strafe) {
        if (forward < 0f) rotationYaw += 180f;
        float f = 1f;
        if (forward < 0f) f = -0.5f;
        if (forward > 0f) f = 0.5f;
        if (strafe > 0f) rotationYaw -= 90f * f;
        if (strafe < 0f) rotationYaw += 90f * f;
        return rotationYaw;
    }

    public static double directionRadians(float yawDegrees, float forward, float strafe) {
        return Math.toRadians(directionYaw(yawDegrees, forward, strafe));
    }

    /** Current player fields are raw step inputs; MoveFix changes only moveFlying's event.
     * Keep this separate from raw-input/event helpers to avoid applying their transform twice.
     */
    public static double confirmedDirectionRadians(EntityPlayerSP player) {
        float cameraDirection = directionYaw(player.rotationYaw, player.moveForward, player.moveStrafing);
        return Math.toRadians(RotationCorrections.directMotionYaw(player.rotationYaw, cameraDirection));
    }

    /** Assigns horizontal velocity, in blocks per tick, without changing vertical motion or rotation. */
    public static void setHorizontalMotion(EntityPlayerSP player, double speed, double yawRadians) {
        player.motionX = -Math.sin(yawRadians) * speed;
        player.motionZ = Math.cos(yawRadians) * speed;
    }

    /** Reorients raw current-step fields in the confirmed basis, retaining the requested speed. */
    public static void strafe(EntityPlayerSP player, double speed) {
        if (!hasMovementInput(player)) return;
        setHorizontalMotion(player, speed, confirmedDirectionRadians(player));
    }

    /** Assigns a speed from raw input; no input stops motion instead of retaining the old velocity. */
    public static void setSpeedFromInput(EntityPlayerSP player, double speed, float forward, float strafe) {
        if (forward == 0.0F && strafe == 0.0F) {
            player.motionX = 0.0;
            player.motionZ = 0.0;
            return;
        }
        setHorizontalMotion(player, speed, directionRadians(player.rotationYaw, forward, strafe));
    }

    public static double horizontalSpeed(EntityPlayerSP player) {
        return Math.hypot(player.motionX, player.motionZ);
    }
}
