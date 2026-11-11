package me.youngjibbit.core.world;

import net.minecraft.util.MathHelper;

/** DemisePrivate e219a97 MoveUtil.direction/fixMovement, including candidate order and float ties. */
public final class DemiseMoveFix {
    private DemiseMoveFix() { }

    public static final class Input {
        public final float strafe, forward;
        private Input(float strafe, float forward) { this.strafe = strafe; this.forward = forward; }
    }

    public static double direction(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0F) rotationYaw += 180F;
        float forward = 1F;
        if (moveForward < 0F) forward = -0.5F;
        else if (moveForward > 0F) forward = 0.5F;
        if (moveStrafing > 0F) rotationYaw -= 90F * forward;
        if (moveStrafing < 0F) rotationYaw += 90F * forward;
        return Math.toRadians(rotationYaw);
    }

    /** Called on keyboard axes before the original sneak/item slowdown consumers. */
    public static Input correct(float strafe, float forward, float yaw, float playerYaw) {
        if (forward == 0 && strafe == 0) return new Input(strafe, forward);
        double angle = MathHelper.wrapAngleTo180_double(Math.toDegrees(direction(playerYaw, forward, strafe)));
        float closestForward = 0, closestStrafe = 0;
        float closestDifference = Float.MAX_VALUE;
        for (float predictedForward = -1F; predictedForward <= 1F; predictedForward += 1F) {
            for (float predictedStrafe = -1F; predictedStrafe <= 1F; predictedStrafe += 1F) {
                if (predictedForward == 0 && predictedStrafe == 0) continue;
                double predictedAngle = MathHelper.wrapAngleTo180_double(Math.toDegrees(direction(yaw, predictedForward, predictedStrafe)));
                double difference = Math.abs(MathHelper.wrapAngleTo180_double(angle - predictedAngle));
                if (difference < closestDifference) {
                    closestDifference = (float) difference;
                    closestForward = predictedForward;
                    closestStrafe = predictedStrafe;
                }
            }
        }
        return new Input(closestStrafe, closestForward);
    }
}
