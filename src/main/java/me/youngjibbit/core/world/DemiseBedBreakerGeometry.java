package me.youngjibbit.core.world;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

import java.util.function.BooleanSupplier;

/**
 * BedBreaker search, center aim and bed-only collision ray from DemisePrivate e219a97.
 * Sources: BedBreaker.java and RotationUtils.java (AGPL-3.0; see the pinned manifest).
 * Loaded-cell, height and caller-lifetime checks are Neutron adapter boundaries.
 */
public final class DemiseBedBreakerGeometry {
    public static final double REACH = 4.5;
    private static final double EPSILON = 1e-7;

    private DemiseBedBreakerGeometry() { }

    public interface Access {
        boolean loaded(BlockPos pos);
        IBlockState state(BlockPos pos);
    }

    public static final class Target {
        public final BlockPos pos;
        public final IBlockState state;
        public final Vec3 aim;

        public Target(BlockPos pos, IBlockState state) {
            if (pos == null || state == null) throw new IllegalArgumentException("bed target");
            this.pos = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
            this.state = state;
            aim = new Vec3(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        }
    }

    /**
     * The module calls this only on the source's even player ticks. Keep the double
     * loops, int truncation, duplicates and strict first-nearest ordering verbatim.
     * Search is cubic; the later action owns ray/reach admission.
     */
    public static Target find(Access access, Vec3 feet, float eyeHeight, BooleanSupplier current) {
        if (access == null || current == null || !finite(feet) || !Float.isFinite(eyeHeight)
                || eyeHeight < 0 || !scanCoordinate(feet.xCoord) || !scanCoordinate(feet.zCoord)
                || !scanCoordinate(feet.yCoord + (double) eyeHeight) || !current.getAsBoolean()) return null;
        Target bed = null;
        double nearestPos = Double.MAX_VALUE;
        for (double x = feet.xCoord - REACH; x <= feet.xCoord + REACH; x++) {
            for (double y = feet.yCoord + eyeHeight - REACH; y <= feet.yCoord + eyeHeight + REACH; y++) {
                for (double z = feet.zCoord - REACH; z <= feet.zCoord + REACH; z++) {
                    if (!current.getAsBoolean()) return null;
                    BlockPos pos = new BlockPos((int) x, (int) y, (int) z);
                    if (!validHeight(pos)) continue;
                    boolean loaded = access.loaded(pos);
                    if (!current.getAsBoolean()) return null;
                    if (!loaded) continue;
                    IBlockState state = access.state(pos);
                    if (!current.getAsBoolean()) return null;
                    if (state != null && state.getBlock() instanceof BlockBed) {
                        double distance = cornerDistance(pos, feet);
                        if (distance < nearestPos) {
                            bed = new Target(pos, state);
                            nearestPos = distance;
                        }
                    }
                }
            }
        }
        if (bed == null || !current.getAsBoolean()) return null;
        // A late world callback cannot publish a bed removed after its scan cell.
        boolean loaded = access.loaded(bed.pos);
        if (!current.getAsBoolean() || !loaded) return null;
        IBlockState state = access.state(bed.pos);
        return current.getAsBoolean() && bed.state.equals(state) ? bed : null;
    }

    /** Source Vec3i.distanceTo: Euclidean distance from block corner to player feet. */
    private static double cornerDistance(BlockPos pos, Vec3 feet) {
        double dx = (double) pos.getX() - feet.xCoord;
        double dy = (double) pos.getY() - feet.yCoord;
        double dz = (double) pos.getZ() - feet.zCoord;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Source RotationUtils.getRotations(BlockPos), including its float sqrt and unwrapped yaw. */
    public static float[] rotations(Vec3 eyes, BlockPos pos) {
        if (!finite(eyes) || pos == null) return null;
        double x = pos.getX() + .5 - eyes.xCoord;
        double y = pos.getY() + .5 - eyes.yCoord;
        double z = pos.getZ() + .5 - eyes.zCoord;
        double dist = MathHelper.sqrt_double(x * x + z * z);
        float yaw = (float) (Math.atan2(z, x) * 180.0 / Math.PI) - 90.0F;
        float pitch = (float) (-(Math.atan2(y, dist) * 180.0 / Math.PI));
        return new float[]{yaw, pitch};
    }

    /**
     * Non-Legit source ray: intersect only the selected bed's collision box along
     * the current rotation's native look vector. It is not a world-occlusion ray.
     */
    public static MovingObjectPosition rayTraceBed(World world, BlockPos pos, Vec3 eyes, Vec3 look) {
        if (world == null || pos == null || !validHeight(pos) || !finite(eyes) || !finite(look)
                || !world.isBlockLoaded(pos)) return null;
        IBlockState state = world.getBlockState(pos);
        if (state == null || !(state.getBlock() instanceof BlockBed)) return null;
        Vec3 to = eyes.addVector(look.xCoord * REACH, look.yCoord * REACH, look.zCoord * REACH);
        return finite(to) ? state.getBlock().collisionRayTrace(world, pos, eyes, to) : null;
    }

    /** Native interaction validation shared by the BedBreaker host adapter. */
    public static boolean matches(MovingObjectPosition hit, BlockPos pos, Vec3 eyes, double reach) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && pos != null && pos.equals(hit.getBlockPos()) && hit.sideHit != null && finite(hit.hitVec)
                && finite(eyes) && Double.isFinite(reach) && reach > 0 && reach <= 5
                && eyes.squareDistanceTo(hit.hitVec) <= reach * reach + EPSILON;
    }

    public static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.xCoord) && Double.isFinite(point.yCoord)
                && Double.isFinite(point.zCoord);
    }

    private static boolean validHeight(BlockPos pos) { return pos.getY() >= 0 && pos.getY() < 256; }

    private static boolean scanCoordinate(double coordinate) {
        // Out-of-range/non-progressing doubles cannot enter the source's ++ loops.
        return Double.isFinite(coordinate) && coordinate >= Integer.MIN_VALUE + 8D
                && coordinate <= Integer.MAX_VALUE - 8D;
    }
}
