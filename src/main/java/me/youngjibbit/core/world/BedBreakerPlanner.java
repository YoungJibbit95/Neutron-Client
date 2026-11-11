package me.youngjibbit.core.world;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;

/** Bounded, read-only bed selection. A plan never authorizes mining or changes the camera. */
public final class BedBreakerPlanner {
    public static final int MAX_CELLS = 11 * 11 * 11;
    public static final int MAX_CANDIDATES = 32;

    public interface Access {
        boolean loaded(BlockPos pos);
        IBlockState state(BlockPos pos);
        AxisAlignedBB bounds(BlockPos pos, IBlockState state);
        MovingObjectPosition ray(Vec3 from, Vec3 to);
    }

    public static final class Target {
        public final BlockPos pos;
        public final IBlockState state;
        public final Vec3 aim;
        private final double distance;
        private Target(BlockPos pos, IBlockState state, Vec3 aim, double distance) {
            this.pos = pos; this.state = state; this.aim = aim; this.distance = distance;
        }
    }

    private static final Comparator<Target> NEAREST = Comparator.comparingDouble((Target target) -> target.distance)
            .thenComparingInt(target -> target.pos.getX()).thenComparingInt(target -> target.pos.getY())
            .thenComparingInt(target -> target.pos.getZ());
    private final Access access;

    public BedBreakerPlanner(Access access) {
        if (access == null) throw new IllegalArgumentException("access");
        this.access = access;
    }

    /** Retain a useful bed; new acquisition is separately throttled by the caller's player tick. */
    public Target select(Vec3 eyes, double reach, Target previous, boolean search, BooleanSupplier current) {
        if (!finite(eyes) || Math.abs(eyes.xCoord) > 30_000_000 || Math.abs(eyes.zCoord) > 30_000_000
                || !Double.isFinite(reach) || reach <= 0 || reach > 5 || current == null || !current.getAsBoolean()) return null;
        if (previous != null) {
            Target held = candidate(previous.pos, eyes, reach, current);
            if (!current.getAsBoolean()) return null;
            if (held != null && visible(held, eyes, reach, current)) return held;
            if (!current.getAsBoolean()) return null;
        }
        if (!search) return null;
        PriorityQueue<Target> nearest = new PriorityQueue<>(MAX_CANDIDATES, NEAREST.reversed());
        int minX = MathHelper.floor_double(eyes.xCoord - reach), maxX = MathHelper.floor_double(eyes.xCoord + reach);
        int minY = Math.max(0, MathHelper.floor_double(eyes.yCoord - reach));
        int maxY = Math.min(255, MathHelper.floor_double(eyes.yCoord + reach));
        int minZ = MathHelper.floor_double(eyes.zCoord - reach), maxZ = MathHelper.floor_double(eyes.zCoord + reach);
        int visited = 0;
        for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
            if (++visited > MAX_CELLS || !current.getAsBoolean()) return null;
            Target target = candidate(new BlockPos(x, y, z), eyes, reach, current);
            if (!current.getAsBoolean()) return null;
            if (target == null) continue;
            if (nearest.size() < MAX_CANDIDATES) nearest.add(target);
            else if (NEAREST.compare(target, nearest.peek()) < 0) { nearest.poll(); nearest.add(target); }
        }
        List<Target> sorted = new ArrayList<>(nearest); sorted.sort(NEAREST);
        for (Target target : sorted) {
            if (!current.getAsBoolean()) return null;
            if (visible(target, eyes, reach, current)) return target;
        }
        return null;
    }

    private Target candidate(BlockPos pos, Vec3 eyes, double reach, BooleanSupplier current) {
        if (!current.getAsBoolean()) return null;
        boolean loaded = access.loaded(pos);
        if (!current.getAsBoolean() || !loaded) return null;
        IBlockState state = access.state(pos);
        if (!current.getAsBoolean() || state == null || !(state.getBlock() instanceof BlockBed)) return null;
        AxisAlignedBB box = access.bounds(pos, state);
        if (!current.getAsBoolean() || box == null || !finite(box)) return null;
        // Use the actual 9/16-high bed bounds, including at negative coordinates.
        // The inset avoids asking for a ray exactly on a shared block edge.
        Vec3 point = new Vec3(inset(eyes.xCoord, box.minX, box.maxX), inset(eyes.yCoord, box.minY, box.maxY),
                inset(eyes.zCoord, box.minZ, box.maxZ));
        double distance = eyes.squareDistanceTo(point);
        return distance <= reach * reach && distance > 1e-10 ? new Target(pos, state, point, distance) : null;
    }

    private boolean visible(Target target, Vec3 eyes, double reach, BooleanSupplier current) {
        if (!current.getAsBoolean()) return false;
        MovingObjectPosition hit = access.ray(eyes, target.aim);
        if (!current.getAsBoolean() || !matches(hit, target.pos, eyes, reach)) return false;
        IBlockState actual = access.state(target.pos);
        return current.getAsBoolean() && target.state.equals(actual);
    }

    public static boolean matches(MovingObjectPosition hit, BlockPos pos, Vec3 eyes, double reach) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && pos.equals(hit.getBlockPos()) && hit.sideHit != null && finite(hit.hitVec)
                && finite(eyes) && Double.isFinite(reach) && reach > 0 && reach <= 5
                && eyes.squareDistanceTo(hit.hitVec) <= reach * reach + 1e-7;
    }

    public static float[] rotations(Vec3 eyes, Vec3 point) {
        if (!finite(eyes) || !finite(point)) return null;
        double x = point.xCoord - eyes.xCoord, y = point.yCoord - eyes.yCoord, z = point.zCoord - eyes.zCoord;
        if (x * x + y * y + z * z < 1e-10) return null;
        return new float[]{(float) (Math.toDegrees(Math.atan2(z, x)) - 90),
                (float) -Math.toDegrees(Math.atan2(y, Math.hypot(x, z)))};
    }

    /** The user-facing speed is an actual upper bound on the combined visible turn, in degrees/tick. */
    public static float[] turn(float yaw, float pitch, float targetYaw, float targetPitch, float speed) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(targetYaw)
                || !Float.isFinite(targetPitch) || !Float.isFinite(speed) || speed <= 0) return null;
        float dy = MathHelper.wrapAngleTo180_float(targetYaw - yaw), dp = targetPitch - pitch;
        double distance = Math.hypot(dy, dp), fraction = distance <= speed ? 1 : speed / distance;
        return new float[]{yaw + (float) (dy * fraction), Math.max(-90, Math.min(90, pitch + (float) (dp * fraction)))};
    }

    private static double inset(double value, double min, double max) {
        double pad = Math.min(.04, (max - min) * .1);
        return Math.max(min + pad, Math.min(max - pad, value));
    }
    public static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.xCoord) && Double.isFinite(point.yCoord) && Double.isFinite(point.zCoord);
    }
    private static boolean finite(AxisAlignedBB box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
                && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ)
                && box.maxX > box.minX && box.maxY > box.minY && box.maxZ > box.minZ;
    }
}
