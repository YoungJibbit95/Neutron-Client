package me.youngjibbit.core.world;

import net.minecraft.block.BlockBed;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.BooleanSupplier;

/** Bounded, read-only mining routes. A plan never authorizes an action or changes the world. */
public final class BedBreakerPlanner {
    public static final int MAX_CELLS = 11 * 11 * 11;
    public static final int MAX_CANDIDATES = 32;
    public static final int MAX_AIM_POINTS = 16;
    public static final int MAX_ROUTE_STEPS = 16;
    public static final int MAX_RAY_CELLS = 24;
    public static final int MAX_RAYS = 1024;
    public static final int BREAK_DELAY_TICKS = 5;
    private static final double EPSILON = 1e-7;

    public interface Access {
        boolean loaded(BlockPos pos);
        IBlockState state(BlockPos pos);
        AxisAlignedBB bounds(BlockPos pos, IBlockState state);
        MovingObjectPosition ray(Vec3 from, Vec3 to);

        /** Remaining controller ticks, including START when needed, excluding the following break delay. */
        default double miningTicks(BlockPos pos, IBlockState state) {
            return state != null && state.getBlock() instanceof BlockBed ? 1 : Double.POSITIVE_INFINITY;
        }

        /** Native delay after destruction; instant survival blocks do not incur the five-tick delay. */
        default double postBreakDelayTicks(BlockPos pos, IBlockState state) { return BREAK_DELAY_TICKS; }
    }

    public static final class Target {
        /** The next reachable block; this can be a defense block rather than the bed itself. */
        public final BlockPos pos;
        public final IBlockState state;
        /** A point on the selected sightline. The native first hit, not this point, supplies the mining face. */
        public final Vec3 aim;
        public final BlockPos bedPos;
        public final IBlockState bedState;
        public final List<BlockPos> path;
        public final double estimatedTicks;
        private final List<Step> steps;

        private Target(Bed bed, Vec3 aim, List<Step> steps, double estimatedTicks) {
            this.pos = steps.get(0).pos;
            this.state = steps.get(0).state;
            this.aim = aim;
            this.bedPos = bed.pos;
            this.bedState = bed.state;
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
            List<BlockPos> path = new ArrayList<>();
            for (Step step : steps) path.add(step.pos);
            this.path = Collections.unmodifiableList(path);
            this.estimatedTicks = estimatedTicks;
        }
    }

    private static final class Step {
        final BlockPos pos;
        final IBlockState state;
        Step(BlockPos pos, IBlockState state) { this.pos = pos; this.state = state; }
    }
    private static final class Bed {
        final BlockPos pos;
        final IBlockState state;
        final AxisAlignedBB box;
        final Vec3 nearest;
        final double distance;
        Bed(BlockPos pos, IBlockState state, AxisAlignedBB box, Vec3 nearest, double distance) {
            this.pos = pos; this.state = state; this.box = box; this.nearest = nearest; this.distance = distance;
        }
    }
    private static final class Budget {
        // Reserve the final ray for a fresh first-hit check after all world/cost callbacks.
        int remaining = MAX_RAYS - 1;
    }
    private static final Comparator<Bed> NEAREST = Comparator.comparingDouble((Bed bed) -> bed.distance)
            .thenComparingInt(bed -> bed.pos.getX()).thenComparingInt(bed -> bed.pos.getY())
            .thenComparingInt(bed -> bed.pos.getZ());
    private final Access access;

    public BedBreakerPlanner(Access access) {
        if (access == null) throw new IllegalArgumentException("access");
        this.access = access;
    }

    /**
     * Compare mining time along sampled straight sightlines to reachable bed halves. Each route removes
     * only the successive native first hits on that line, so it does not assume automatic player travel
     * through a bent tunnel. On search ticks a cheaper opening can replace the old route; equal cost
     * retains the old route and its mining progress. Intermediate ticks only validate its continuation.
     */
    public Target select(Vec3 eyes, double reach, Target previous, boolean search, BooleanSupplier current) {
        if (!finite(eyes) || Math.abs(eyes.xCoord) > 30_000_000 || Math.abs(eyes.zCoord) > 30_000_000
                || !Double.isFinite(reach) || reach <= 0 || reach > 5 || current == null || !current.getAsBoolean()) return null;
        Budget budget = new Budget();
        Target best = null;
        if (previous != null) {
            Bed held = candidate(previous.bedPos, eyes, reach, current);
            if (!current.getAsBoolean()) return null;
            if (held != null && held.state.equals(previous.bedState) && inside(previous.aim, held.box))
                best = route(held, previous.aim, eyes, reach, Double.POSITIVE_INFINITY, budget, current);
            if (!current.getAsBoolean()) return null;
        }
        if (search) {
            PriorityQueue<Bed> nearest = new PriorityQueue<>(MAX_CANDIDATES, NEAREST.reversed());
            int minX = MathHelper.floor_double(eyes.xCoord - reach), maxX = MathHelper.floor_double(eyes.xCoord + reach);
            int minY = Math.max(0, MathHelper.floor_double(eyes.yCoord - reach));
            int maxY = Math.min(255, MathHelper.floor_double(eyes.yCoord + reach));
            int minZ = MathHelper.floor_double(eyes.zCoord - reach), maxZ = MathHelper.floor_double(eyes.zCoord + reach);
            int visited = 0;
            for (int x = minX; x <= maxX; x++) for (int y = minY; y <= maxY; y++) for (int z = minZ; z <= maxZ; z++) {
                if (++visited > MAX_CELLS || !current.getAsBoolean()) return null;
                Bed bed = candidate(new BlockPos(x, y, z), eyes, reach, current);
                if (!current.getAsBoolean()) return null;
                if (bed == null) continue;
                if (nearest.size() < MAX_CANDIDATES) nearest.add(bed);
                else if (NEAREST.compare(bed, nearest.peek()) < 0) { nearest.poll(); nearest.add(bed); }
            }
            List<Bed> sorted = new ArrayList<>(nearest); sorted.sort(NEAREST);
            List<List<Vec3>> points = new ArrayList<>();
            for (Bed bed : sorted) points.add(points(bed));
            // Give every candidate its nearest sightline before spending the budget on alternate faces.
            for (int point = 0; point < MAX_AIM_POINTS && budget.remaining > 0; point++) {
                for (int index = 0; index < sorted.size() && budget.remaining > 0; index++) {
                    if (!current.getAsBoolean()) return null;
                    if (point >= points.get(index).size()) continue;
                    Bed bed = sorted.get(index);
                    Vec3 aim = points.get(index).get(point);
                    if (best != null && bed.pos.equals(best.bedPos) && same(aim, best.aim)) continue;
                    Target option = route(bed, aim, eyes, reach,
                            best == null ? Double.POSITIVE_INFINITY : best.estimatedTicks, budget, current);
                    if (!current.getAsBoolean()) return null;
                    if (option != null && (best == null || option.estimatedTicks < best.estimatedTicks - EPSILON)) best = option;
                }
            }
        }
        if (best == null || !validateFirst(best, eyes, reach, current)) return null;
        return equivalent(previous, best) ? previous : best;
    }

    /** Recheck this before each controller action, including after callbacks. */
    public boolean hasBed(Target target, BooleanSupplier current) {
        if (target == null || current == null || !current.getAsBoolean()) return false;
        boolean loaded = access.loaded(target.bedPos);
        if (!current.getAsBoolean() || !loaded) return false;
        IBlockState state = access.state(target.bedPos);
        return current.getAsBoolean() && target.bedState.equals(state) && state.getBlock() instanceof BlockBed;
    }

    private Bed candidate(BlockPos pos, Vec3 eyes, double reach, BooleanSupplier current) {
        if (!current.getAsBoolean()) return null;
        boolean loaded = access.loaded(pos);
        if (!current.getAsBoolean() || !loaded) return null;
        IBlockState state = access.state(pos);
        if (!current.getAsBoolean() || state == null || !(state.getBlock() instanceof BlockBed)) return null;
        AxisAlignedBB box = access.bounds(pos, state);
        if (!current.getAsBoolean() || box == null || !finite(box)) return null;
        Vec3 point = new Vec3(inset(eyes.xCoord, box.minX, box.maxX), inset(eyes.yCoord, box.minY, box.maxY),
                inset(eyes.zCoord, box.minZ, box.maxZ));
        double distance = eyes.squareDistanceTo(point);
        return distance <= reach * reach && distance > 1e-10 ? new Bed(pos, state, box, point, distance) : null;
    }

    private Target route(Bed bed, Vec3 aim, Vec3 eyes, double reach, double maximum, Budget budget, BooleanSupplier current) {
        if (budget.remaining <= 0 || !finite(aim) || eyes.squareDistanceTo(aim) > reach * reach
                || !loadedRay(eyes, aim, current)) return null;
        List<Step> steps = new ArrayList<>();
        Vec3 cursor = eyes;
        double ticks = 0, previousDelay = 0;
        for (int count = 0; count < MAX_ROUTE_STEPS && budget.remaining > 0; count++) {
            if (!current.getAsBoolean()) return null;
            budget.remaining--;
            MovingObjectPosition hit = access.ray(cursor, aim);
            if (!current.getAsBoolean() || hit == null || !matches(hit, hit.getBlockPos(), eyes, reach)) return null;
            BlockPos pos = hit.getBlockPos();
            for (Step step : steps) if (step.pos.equals(pos)) return null;
            boolean loaded = access.loaded(pos);
            if (!current.getAsBoolean() || !loaded) return null;
            IBlockState state = access.state(pos);
            if (!current.getAsBoolean() || state == null) return null;
            // A different bed is an independent destination, never an arbitrary defense to destroy.
            if (state.getBlock() instanceof BlockBed && !pos.equals(bed.pos)) return null;
            double cost = access.miningTicks(pos, state);
            if (!current.getAsBoolean() || !Double.isFinite(cost) || cost <= 0) return null;
            ticks += cost + previousDelay;
            if (!Double.isFinite(ticks) || ticks > maximum + EPSILON) return null;
            steps.add(new Step(pos, state));
            if (pos.equals(bed.pos)) {
                if (!bed.state.equals(state)) return null;
                return new Target(bed, aim, steps, ticks);
            }
            previousDelay = access.postBreakDelayTicks(pos, state);
            if (!current.getAsBoolean() || !Double.isFinite(previousDelay) || previousDelay < 0) return null;
            if (ticks + previousDelay >= maximum) return null;
            cursor = afterCell(eyes, aim, pos, hit.hitVec);
            if (cursor == null) return null;
        }
        return null;
    }

    private boolean validateFirst(Target target, Vec3 eyes, double reach, BooleanSupplier current) {
        if (!hasBed(target, current) || !loadedRay(eyes, target.aim, current)) return false;
        for (Step step : target.steps) {
            if (!current.getAsBoolean()) return false;
            boolean loaded = access.loaded(step.pos);
            if (!current.getAsBoolean() || !loaded) return false;
            IBlockState actual = access.state(step.pos);
            if (!current.getAsBoolean() || !step.state.equals(actual)) return false;
        }
        MovingObjectPosition hit = access.ray(eyes, target.aim);
        if (!current.getAsBoolean() || !matches(hit, target.pos, eyes, reach)) return false;
        IBlockState actual = access.state(target.pos);
        return current.getAsBoolean() && target.state.equals(actual) && hasBed(target, current);
    }

    /** Check every crossed voxel (including cells touching an edge/corner) before a native world ray. */
    private boolean loadedRay(Vec3 from, Vec3 to, BooleanSupplier current) {
        int x = MathHelper.floor_double(from.xCoord), y = MathHelper.floor_double(from.yCoord), z = MathHelper.floor_double(from.zCoord);
        int endX = MathHelper.floor_double(to.xCoord), endY = MathHelper.floor_double(to.yCoord), endZ = MathHelper.floor_double(to.zCoord);
        double dx = to.xCoord - from.xCoord, dy = to.yCoord - from.yCoord, dz = to.zCoord - from.zCoord;
        int sx = dx > 0 ? 1 : dx < 0 ? -1 : 0, sy = dy > 0 ? 1 : dy < 0 ? -1 : 0, sz = dz > 0 ? 1 : dz < 0 ? -1 : 0;
        double tx = boundary(from.xCoord, x, dx), ty = boundary(from.yCoord, y, dy), tz = boundary(from.zCoord, z, dz);
        double deltaX = sx == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(dx);
        double deltaY = sy == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(dy);
        double deltaZ = sz == 0 ? Double.POSITIVE_INFINITY : 1 / Math.abs(dz);
        for (int visited = 0; visited < MAX_RAY_CELLS; visited++) {
            if (!loaded(new BlockPos(x, y, z), current)) return false;
            if (x == endX && y == endY && z == endZ) return true;
            double next = Math.min(tx, Math.min(ty, tz));
            if (!Double.isFinite(next) || next > 1 + EPSILON) return false;
            int cross = (tx <= next + EPSILON ? 1 : 0) | (ty <= next + EPSILON ? 2 : 0) | (tz <= next + EPSILON ? 4 : 0);
            for (int mask = 1; mask <= 7; mask++) if ((mask & cross) == mask) {
                if (!loaded(new BlockPos(x + ((mask & 1) != 0 ? sx : 0), y + ((mask & 2) != 0 ? sy : 0),
                        z + ((mask & 4) != 0 ? sz : 0)), current)) return false;
            }
            if ((cross & 1) != 0) { x += sx; tx += deltaX; }
            if ((cross & 2) != 0) { y += sy; ty += deltaY; }
            if ((cross & 4) != 0) { z += sz; tz += deltaZ; }
        }
        return false;
    }

    private boolean loaded(BlockPos pos, BooleanSupplier current) {
        if (!current.getAsBoolean()) return false;
        boolean loaded = access.loaded(pos);
        return current.getAsBoolean() && loaded;
    }
    private static double boundary(double from, int cell, double direction) {
        return direction > 0 ? (cell + 1D - from) / direction
                : direction < 0 ? (cell - from) / direction : Double.POSITIVE_INFINITY;
    }

    /** Continue the same ray just past a virtually removed cell, without changing the world. */
    private static Vec3 afterCell(Vec3 from, Vec3 to, BlockPos pos, Vec3 hit) {
        double dx = to.xCoord - from.xCoord, dy = to.yCoord - from.yCoord, dz = to.zCoord - from.zCoord;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (!Double.isFinite(lengthSquared) || lengthSquared < 1e-10) return null;
        double hitT = ((hit.xCoord - from.xCoord) * dx + (hit.yCoord - from.yCoord) * dy + (hit.zCoord - from.zCoord) * dz) / lengthSquared;
        double exit = Math.min(boundary(from.xCoord, pos.getX(), dx),
                Math.min(boundary(from.yCoord, pos.getY(), dy), boundary(from.zCoord, pos.getZ(), dz)));
        double next = exit + 1e-6 / Math.sqrt(lengthSquared);
        if (!Double.isFinite(next) || exit < hitT - EPSILON || next >= 1 || next <= hitT) return null;
        return new Vec3(from.xCoord + dx * next, from.yCoord + dy * next, from.zCoord + dz * next);
    }

    private static List<Vec3> points(Bed bed) {
        List<Vec3> result = new ArrayList<>(MAX_AIM_POINTS);
        add(result, bed.nearest);
        AxisAlignedBB box = bed.box;
        double lowX = inset(box.minX, box.minX, box.maxX), highX = inset(box.maxX, box.minX, box.maxX);
        double lowY = inset(box.minY, box.minY, box.maxY), highY = inset(box.maxY, box.minY, box.maxY);
        double lowZ = inset(box.minZ, box.minZ, box.maxZ), highZ = inset(box.maxZ, box.minZ, box.maxZ);
        double midX = (lowX + highX) / 2, midY = (lowY + highY) / 2, midZ = (lowZ + highZ) / 2;
        add(result, new Vec3(midX, midY, midZ));
        add(result, new Vec3(lowX, midY, midZ)); add(result, new Vec3(highX, midY, midZ));
        add(result, new Vec3(midX, lowY, midZ)); add(result, new Vec3(midX, highY, midZ));
        add(result, new Vec3(midX, midY, lowZ)); add(result, new Vec3(midX, midY, highZ));
        for (double x : new double[]{lowX, highX}) for (double y : new double[]{lowY, highY}) for (double z : new double[]{lowZ, highZ})
            add(result, new Vec3(x, y, z));
        return result;
    }
    private static void add(List<Vec3> points, Vec3 point) {
        for (Vec3 previous : points) if (same(previous, point)) return;
        points.add(point);
    }
    private static boolean same(Vec3 first, Vec3 second) { return first.squareDistanceTo(second) < 1e-14; }
    private static boolean inside(Vec3 point, AxisAlignedBB box) {
        return finite(point) && point.xCoord > box.minX && point.xCoord < box.maxX
                && point.yCoord > box.minY && point.yCoord < box.maxY && point.zCoord > box.minZ && point.zCoord < box.maxZ;
    }
    private static boolean equivalent(Target first, Target second) {
        if (first == null || !first.bedPos.equals(second.bedPos) || !first.bedState.equals(second.bedState)
                || !same(first.aim, second.aim) || first.steps.size() != second.steps.size()
                || Double.compare(first.estimatedTicks, second.estimatedTicks) != 0) return false;
        for (int i = 0; i < first.steps.size(); i++) {
            Step a = first.steps.get(i), b = second.steps.get(i);
            if (!a.pos.equals(b.pos) || !a.state.equals(b.state)) return false;
        }
        return true;
    }

    public static boolean matches(MovingObjectPosition hit, BlockPos pos, Vec3 eyes, double reach) {
        return hit != null && hit.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                && pos != null && pos.equals(hit.getBlockPos()) && hit.sideHit != null && finite(hit.hitVec)
                && finite(eyes) && Double.isFinite(reach) && reach > 0 && reach <= 5
                && eyes.squareDistanceTo(hit.hitVec) <= reach * reach + EPSILON;
    }

    public static float[] rotations(Vec3 eyes, Vec3 point) {
        if (!finite(eyes) || !finite(point)) return null;
        double x = point.xCoord - eyes.xCoord, y = point.yCoord - eyes.yCoord, z = point.zCoord - eyes.zCoord;
        if (x * x + y * y + z * z < 1e-10) return null;
        return new float[]{(float) (Math.toDegrees(Math.atan2(z, x)) - 90),
                (float) -Math.toDegrees(Math.atan2(y, Math.hypot(x, z)))};
    }

    /** The user-facing speed bounds the combined yaw/pitch change, in degrees per player tick. */
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
