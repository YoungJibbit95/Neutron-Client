package me.youngjibbit.utils.render;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Exact geometric display helpers. No aim planning, rotation smoothing or gameplay writes. */
public final class TargetEspRay {
    private TargetEspRay() { }
    public static Vec3 direction(float yaw, float pitch) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || pitch < -90 || pitch > 90) return null;
        // Same convention as Entity.getVectorForRotation; avoids any camera interpolation.
        float y = net.minecraft.util.MathHelper.cos(-yaw * .017453292F - (float)Math.PI);
        float ys = net.minecraft.util.MathHelper.sin(-yaw * .017453292F - (float)Math.PI);
        float p = -net.minecraft.util.MathHelper.cos(-pitch * .017453292F);
        float ps = net.minecraft.util.MathHelper.sin(-pitch * .017453292F);
        return new Vec3(ys * p, ps, y * p);
    }
    public static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.xCoord) && Double.isFinite(point.yCoord) && Double.isFinite(point.zCoord);
    }
    public static Vec3 endpoint(Vec3 eyes, Vec3 direction, double distance) {
        return finite(eyes) && finite(direction) && Double.isFinite(distance) && distance > 0
                ? eyes.addVector(direction.xCoord * distance, direction.yCoord * distance, direction.zCoord * distance) : null;
    }
    public static Vec3 intercept(AxisAlignedBB bounds, Vec3 eyes, Vec3 end) {
        if (bounds == null || !finite(eyes) || !finite(end)) return null;
        if (bounds.isVecInside(eyes)) return eyes;
        MovingObjectPosition hit = bounds.calculateIntercept(eyes, end);
        return hit == null ? null : hit.hitVec;
    }
    public static boolean nearer(Vec3 eyes, Vec3 candidate, Vec3 current) {
        return finite(eyes) && finite(candidate) && (!finite(current)
                || eyes.squareDistanceTo(candidate) < eyes.squareDistanceTo(current));
    }
}
