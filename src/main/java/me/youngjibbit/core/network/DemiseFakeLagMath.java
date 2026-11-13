package me.youngjibbit.core.network;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

/** Pinned FakeLag/PlayerUtils/RotationUtils geometry; see docs/FAKELAG-DEMISE-PARITY.md. */
public final class DemiseFakeLagMath {
    private DemiseFakeLagMath() { }
    public static AxisAlignedBB hitbox(Entity entity) {
        double border = entity.getCollisionBorderSize();
        return entity.getEntityBoundingBox().expand(border, border, border);
    }
    public static Vec3 closest(Vec3 currentEyes, AxisAlignedBB box) {
        return new Vec3(MathHelper.clamp_double(currentEyes.xCoord, box.minX, box.maxX),
                MathHelper.clamp_double(currentEyes.yCoord, box.minY, box.maxY),
                MathHelper.clamp_double(currentEyes.zCoord, box.minZ, box.maxZ));
    }
    /** The source's custom measurement still clamps from current eyes, not from the supplied historical eyes. */
    public static double boxDistance(Vec3 currentEyes, Vec3 measuredEyes, AxisAlignedBB box) {
        Vec3 nearest = closest(currentEyes, box);
        double x = Math.abs(nearest.xCoord - measuredEyes.xCoord), y = Math.abs(nearest.yCoord - measuredEyes.yCoord),
                z = Math.abs(nearest.zCoord - measuredEyes.zCoord);
        return Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2));
    }
    public static float entityDistance(Vec3 from, Entity target) {
        float x = (float) (from.xCoord - target.posX), y = (float) (from.yCoord - target.posY),
                z = (float) (from.zCoord - target.posZ);
        return MathHelper.sqrt_float(x * x + y * y + z * z);
    }
    public static double mouseDistance(Vec3 eyes, Vec3 look, AxisAlignedBB box, boolean ridingTarget) {
        MovingObjectPosition hit = box.calculateIntercept(eyes, eyes.addVector(look.xCoord * 64, look.yCoord * 64, look.zCoord * 64));
        Vec3 intercept = null;
        if (box.isVecInside(eyes)) intercept = hit == null ? eyes : hit.hitVec;
        else if (hit != null && eyes.distanceTo(hit.hitVec) < 64 && !ridingTarget) intercept = hit.hitVec;
        return intercept == null ? Double.MAX_VALUE : eyes.distanceTo(intercept);
    }
    public static boolean aimsAtServer(EntityPlayer target, Vec3 client, Vec3 server) {
        float clientYaw = (float) (MathHelper.func_181159_b(client.zCoord - target.posZ, client.xCoord - target.posX) * 180 / Math.PI - 90);
        float serverYaw = (float) (MathHelper.func_181159_b(server.zCoord - target.posZ, server.xCoord - target.posX) * 180 / Math.PI - 90);
        return Math.abs(MathHelper.wrapAngleTo180_float(clientYaw - target.rotationYaw)) > 25
                && Math.abs(MathHelper.wrapAngleTo180_float(serverYaw - target.rotationYaw)) < 25;
    }
    public static boolean sameTeam(EntityPlayer player, EntityPlayer candidate) {
        if (player.getDisplayName() == null || candidate.getDisplayName() == null) return false;
        String own = player.getDisplayName().getFormattedText().replace("\u00a7r", "");
        String other = candidate.getDisplayName().getFormattedText().replace("\u00a7r", "");
        return own.length() > 1 && other.startsWith("\u00a7" + own.charAt(1));
    }
    public static boolean finite(Vec3 position) {
        return position != null && Double.isFinite(position.xCoord) && Double.isFinite(position.yCoord) && Double.isFinite(position.zCoord);
    }
}
