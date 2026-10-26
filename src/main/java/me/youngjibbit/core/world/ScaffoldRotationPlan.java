package me.youngjibbit.core.world;

import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Vec3;

public final class ScaffoldRotationPlan {
    private final float yaw, pitch;
    private ScaffoldRotationPlan(float yaw, float pitch) { this.yaw = yaw; this.pitch = pitch; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }

    /** Modes choose a point on the face; all rotations actually look at that point. */
    public static Vec3 hitPoint(String mode, AxisAlignedBB box, EnumFacing face, Vec3 eyes) {
        double x = (box.minX + box.maxX) / 2, y = (box.minY + box.maxY) / 2, z = (box.minZ + box.maxZ) / 2;
        if ("Down".equalsIgnoreCase(mode)) {
            // Nearest inset point gives the steepest reachable downward view.
            x = inset(eyes.xCoord, box.minX, box.maxX);
            y = inset(eyes.yCoord, box.minY, box.maxY);
            z = inset(eyes.zCoord, box.minZ, box.maxZ);
        } else if ("NCP".equalsIgnoreCase(mode)) {
            x = (x + inset(eyes.xCoord, box.minX, box.maxX)) / 2;
            y = (y + inset(eyes.yCoord, box.minY, box.maxY)) / 2;
            z = (z + inset(eyes.zCoord, box.minZ, box.maxZ)) / 2;
        }
        switch (face) {
            case WEST: x = box.minX; break;
            case EAST: x = box.maxX; break;
            case DOWN: y = box.minY; break;
            case UP: y = box.maxY; break;
            case NORTH: z = box.minZ; break;
            case SOUTH: z = box.maxZ; break;
        }
        Vec3 base=new Vec3(x,y,z);
        if(!"Godbridge".equalsIgnoreCase(mode))return base;
        ScaffoldRotationPlan desired=build(mode,eyes,base);
        return desired==null?null:roundedFacePoint(box,face,eyes,base,45,Math.copySign(83.52,desired.pitch));
    }

    /** Snap a real face point, not an angle detached from its placement geometry. */
    public static Vec3 roundedFacePoint(AxisAlignedBB box,EnumFacing face,Vec3 eyes,Vec3 preferred,int step,double pitch) {
        if(step!=45 && step!=90)throw new IllegalArgumentException("Scaffold yaw grid must be 45 or 90 degrees");
        ScaffoldRotationPlan desired=build("Back",eyes,preferred);if(desired==null)return null;
        Vec3 best=null;double score=Double.POSITIVE_INFINITY;
        double margin=Math.min(.02,Math.min(box.maxX-box.minX,Math.min(box.maxY-box.minY,box.maxZ-box.minZ))/4);
        for(int yaw=-180;yaw<180;yaw+=step) {
            double dx=-Math.sin(Math.toRadians(yaw)),dz=Math.cos(Math.toRadians(yaw)),distance,x,y,z;
            switch(face) {
                case WEST:case EAST:
                    if(Math.abs(dx)<1e-8)continue;
                    x=face==EnumFacing.WEST?box.minX:box.maxX;distance=(x-eyes.xCoord)/dx;z=eyes.zCoord+dz*distance;
                    if(distance<=0 || z<box.minZ+margin || z>box.maxZ-margin)continue;
                    y=Math.max(box.minY+margin,Math.min(box.maxY-margin,eyes.yCoord-Math.tan(Math.toRadians(pitch))*distance));break;
                case NORTH:case SOUTH:
                    if(Math.abs(dz)<1e-8)continue;
                    z=face==EnumFacing.NORTH?box.minZ:box.maxZ;distance=(z-eyes.zCoord)/dz;x=eyes.xCoord+dx*distance;
                    if(distance<=0 || x<box.minX+margin || x>box.maxX-margin)continue;
                    y=Math.max(box.minY+margin,Math.min(box.maxY-margin,eyes.yCoord-Math.tan(Math.toRadians(pitch))*distance));break;
                default:
                    y=face==EnumFacing.UP?box.maxY:box.minY;
                    double[] a=interval(eyes.xCoord,dx,box.minX+margin,box.maxX-margin),b=interval(eyes.zCoord,dz,box.minZ+margin,box.maxZ-margin);
                    if(a==null || b==null)continue;
                    double low=Math.max(1e-5,Math.max(a[0],b[0])),high=Math.min(a[1],b[1]);if(high<low)continue;
                    distance=Math.max(low,Math.min(high,(eyes.yCoord-y)/Math.tan(Math.toRadians(pitch))));
                    x=eyes.xCoord+dx*distance;z=eyes.zCoord+dz*distance;
            }
            Vec3 point=new Vec3(x,y,z);ScaffoldRotationPlan actual=build("Back",eyes,point);if(actual==null)continue;
            double dy=net.minecraft.util.MathHelper.wrapAngleTo180_float(actual.yaw-desired.yaw),dp=actual.pitch-pitch,current=dy*dy+dp*dp;
            if(current<score){score=current;best=point;}
        }
        return best;
    }
    private static double[] interval(double origin,double direction,double min,double max) {
        if(Math.abs(direction)<1e-8)return origin>=min&&origin<=max?new double[]{Double.NEGATIVE_INFINITY,Double.POSITIVE_INFINITY}:null;
        double a=(min-origin)/direction,b=(max-origin)/direction;return new double[]{Math.min(a,b),Math.max(a,b)};
    }

    private static double inset(double value, double min, double max) {
        double margin = Math.min(.1, (max - min) / 4);
        return Math.max(min + margin, Math.min(max - margin, value));
    }

    public static ScaffoldRotationPlan build(String mode, Vec3 eyes, Vec3 hitVec) {
        if (eyes == null || hitVec == null) return null;
        double x = hitVec.xCoord - eyes.xCoord, y = hitVec.yCoord - eyes.yCoord, z = hitVec.zCoord - eyes.zCoord;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || x*x+y*y+z*z < 1e-12) return null;
        return new ScaffoldRotationPlan((float)(Math.toDegrees(Math.atan2(z, x)) - 90),
                (float)-Math.toDegrees(Math.atan2(y, Math.sqrt(x*x+z*z))));
    }
}
