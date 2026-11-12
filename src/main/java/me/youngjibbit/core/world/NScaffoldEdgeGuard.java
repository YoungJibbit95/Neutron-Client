package me.youngjibbit.core.world;

import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.border.WorldBorder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Local collision support for one grounded movement. The caller owns jump intent
 * and lifecycle admission; this helper neither retains a scene nor changes input,
 * velocity, vertical motion or player state.
 */
public final class NScaffoldEdgeGuard {
    private static final double FOOT_INSET = 1.0E-5;
    private static final double EDGE_MARGIN = 1.0E-5;
    private static final double HEIGHT_EPSILON = 1.0E-7;
    private static final double MAX_STEP_DOWN = .6;
    private static final double MAX_STEP_UP = .6;
    private static final double MAX_HORIZONTAL = 2;
    private static final int MAX_COLLISIONS = 512;

    private NScaffoldEdgeGuard() { }

    public static final class Result {
        public final double x, z;
        /** False for an inapplicable or revoked sample; the caller must not apply it. */
        public final boolean active;
        public final boolean limited;

        private Result(double x, double z, boolean active, boolean limited) {
            this.x = x;
            this.z = z;
            this.active = active;
            this.limited = limited;
        }
    }

    /**
     * Keep the connected, supported prefix of the actual horizontal displacement.
     * Safe requests inside the bounded query horizon are returned bit for bit.
     * Unknown terrain fails closed; a revoked scene returns an inactive result.
     * Intentional jumps should be excluded by the caller before invoking this API.
     */
    public static Result limit(World world, EntityPlayerSP player,
                               double dx, double dy, double dz, BooleanSupplier current) {
        Result inactive = new Result(dx, dz, false, false);
        if (world == null || player == null || current == null || dy > 0
                || !eligible(world, player)) return inactive;
        AxisAlignedBB body = player.getEntityBoundingBox();
        float stepHeight = player.stepHeight;
        if (!current(world, player, body, stepHeight, current)) return inactive;
        if (dx == 0 && dz == 0) return new Result(dx, dz, true, false);
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                || !validBody(body) || !Float.isFinite(stepHeight))
            return finish(world, player, body, stepHeight, current, dx, dz, 0, 0);

        // A corrupt or unusually large request must not expand an unbounded world
        // scan, and the unchecked remainder must never be declared supported.
        double scale = Math.min(1, MAX_HORIZONTAL / Math.max(Math.abs(dx), Math.abs(dz)));
        double boundedX = dx * scale, boundedZ = dz * scale;
        double stepUp = Math.max(0, Math.min(MAX_STEP_UP, stepHeight));
        // Include the body so an overhanging or tall side wall cannot redirect a
        // seemingly safe diagonal into an unsupported vanilla X-then-Z slide.
        AxisAlignedBB query = new AxisAlignedBB(body.minX, body.minY - MAX_STEP_DOWN - HEIGHT_EPSILON,
                body.minZ, body.maxX, body.maxY + stepUp + HEIGHT_EPSILON, body.maxZ)
                .addCoord(boundedX, 0, boundedZ);

        WorldBorder border = world.getWorldBorder();
        if (!current(world, player, body, stepHeight, current)) return inactive;
        if (border == null) return finish(world, player, body, stepHeight, current, dx, dz, 0, 0);
        double minX = border.minX();
        if (!current(world, player, body, stepHeight, current)) return inactive;
        double maxX = border.maxX();
        if (!current(world, player, body, stepHeight, current)) return inactive;
        double minZ = border.minZ();
        if (!current(world, player, body, stepHeight, current)) return inactive;
        double maxZ = border.maxZ();
        if (!current(world, player, body, stepHeight, current)) return inactive;
        if (!Double.isFinite(minX) || !Double.isFinite(maxX) || !Double.isFinite(minZ)
                || !Double.isFinite(maxZ) || query.minX < minX || query.maxX > maxX
                || query.minZ < minZ || query.maxZ > maxZ)
            return finish(world, player, body, stepHeight, current, dx, dz, 0, 0);

        // Match the horizontal cells enumerated by vanilla's collision query,
        // including its boundary cell. isBlockLoaded does not load a chunk.
        int fromX = MathHelper.floor_double(query.minX), toX = MathHelper.floor_double(query.maxX);
        int fromZ = MathHelper.floor_double(query.minZ), toZ = MathHelper.floor_double(query.maxZ);
        for (int x = fromX; x <= toX; x++) {
            for (int z = fromZ; z <= toZ; z++) {
                boolean loaded = world.isBlockLoaded(new BlockPos(x, 64, z));
                if (!current(world, player, body, stepHeight, current)) return inactive;
                if (!loaded) return finish(world, player, body, stepHeight, current, dx, dz, 0, 0);
            }
        }

        List<AxisAlignedBB> collisions = world.getCollidingBoundingBoxes(player, query);
        if (!current(world, player, body, stepHeight, current)) return inactive;
        if (collisions == null || collisions.size() > MAX_COLLISIONS)
            return finish(world, player, body, stepHeight, current, dx, dz, 0, 0);
        double[] slide = supportedWallSlide(body, boundedX, dy, boundedZ, stepUp, collisions);
        if (slide != null)
            return finish(world, player, body, stepHeight, current, dx, dz, slide[0], slide[1]);
        double supported = supportedFraction(body, boundedX, boundedZ, stepUp, collisions);
        if (supported == 1 && scale == 1)
            return finish(world, player, body, stepHeight, current, dx, dz, dx, dz);
        return finish(world, player, body, stepHeight, current, dx, dz,
                boundedX * supported, boundedZ * supported);
    }

    private static boolean eligible(World world, EntityPlayerSP player) {
        return player.worldObj == world && player.onGround && !player.noClip
                && player.ridingEntity == null
                && (player.capabilities == null || !player.capabilities.isFlying);
    }

    private static boolean current(World world, EntityPlayerSP player, AxisAlignedBB body,
                                   float stepHeight, BooleanSupplier current) {
        // Evaluate the external callback first; it can itself revoke the scene.
        return current.getAsBoolean() && eligible(world, player)
                && player.getEntityBoundingBox() == body
                && Float.floatToIntBits(player.stepHeight) == Float.floatToIntBits(stepHeight);
    }

    private static Result finish(World world, EntityPlayerSP player, AxisAlignedBB body,
                                 float stepHeight, BooleanSupplier current,
                                 double dx, double dz, double x, double z) {
        if (!current(world, player, body, stepHeight, current)) return new Result(dx, dz, false, false);
        return new Result(x, z, true, x != dx || z != dz);
    }

    private static boolean validBody(AxisAlignedBB body) {
        return finite(body) && body.minX >= -30000000 && body.maxX <= 30000000
                && body.minZ >= -30000000 && body.maxZ <= 30000000
                && body.minY >= 0 && body.minY <= 257
                && body.maxX - body.minX > 2 * FOOT_INSET && body.maxX - body.minX <= 2
                && body.maxZ - body.minZ > 2 * FOOT_INSET && body.maxZ - body.minZ <= 2
                && body.maxY > body.minY && body.maxY - body.minY <= 4;
    }

    private static boolean finite(AxisAlignedBB box) {
        return box != null && Double.isFinite(box.minX) && Double.isFinite(box.maxX)
                && Double.isFinite(box.minY) && Double.isFinite(box.maxY)
                && Double.isFinite(box.minZ) && Double.isFinite(box.maxZ);
    }

    private static AxisAlignedBB footprint(AxisAlignedBB body) {
        return new AxisAlignedBB(body.minX + FOOT_INSET, body.minY, body.minZ + FOOT_INSET,
                body.maxX - FOOT_INSET, body.minY, body.maxZ - FOOT_INSET);
    }

    /**
     * Only a wall too high to step over admits this conservative axis check.
     * Without such a collision, diagonal bridges retain their ordinary straight
     * sweep. Keep the original blocked-axis request when its real displacement is
     * safe, so vanilla still observes the wall and clears that velocity itself.
     */
    private static double[] supportedWallSlide(AxisAlignedBB body, double dx, double dy, double dz,
                                               double stepUp, List<AxisAlignedBB> collisions) {
        double y = dy;
        for (AxisAlignedBB box : collisions) if (finite(box)) y = box.calculateYOffset(body, y);
        AxisAlignedBB lowered = body.offset(0, y, 0);
        double wallTop = body.minY + stepUp + HEIGHT_EPSILON;
        double actualX = dx;
        for (AxisAlignedBB box : collisions)
            if (finite(box) && box.maxY > wallTop) actualX = box.calculateXOffset(lowered, actualX);
        AxisAlignedBB afterX = lowered.offset(actualX, 0, 0);
        double actualZ = dz;
        for (AxisAlignedBB box : collisions)
            if (finite(box) && box.maxY > wallTop) actualZ = box.calculateZOffset(afterX, actualZ);
        if (actualX == dx && actualZ == dz) return null;

        double allowedStep = stepUp + body.minY - lowered.minY;
        double xFraction = supportedFraction(lowered, actualX, 0, allowedStep, collisions);
        double resultX = dx;
        if (xFraction < 1) {
            resultX = actualX * xFraction;
            actualX = resultX;
        }
        // Recompute Z at the constrained X endpoint. A shorter X leg can change
        // which wall is hit during vanilla's second horizontal collision pass.
        afterX = lowered.offset(actualX, 0, 0);
        actualZ = dz;
        for (AxisAlignedBB box : collisions)
            if (finite(box) && box.maxY > wallTop) actualZ = box.calculateZOffset(afterX, actualZ);
        double zFraction = supportedFraction(afterX, 0, actualZ, allowedStep, collisions);
        double resultZ = zFraction < 1 ? actualZ * zFraction : dz;
        return new double[]{resultX, resultZ};
    }

    /** Exact swept rectangle intervals, rather than endpoint or spaced point probes. */
    static double supportedFraction(AxisAlignedBB body, double dx, double dz, double stepUp,
                                    List<AxisAlignedBB> collisions) {
        if (collisions == null || collisions.size() > MAX_COLLISIONS) return 0;
        AxisAlignedBB foot = footprint(body);
        List<Interval> intervals = new ArrayList<>();
        for (AxisAlignedBB box : collisions) {
            // Only a reachable top face is a floor. A tall side wall intersecting
            // the swept feet is not support, unlike a lowered whole-body test.
            if (!finite(box) || box.maxX <= box.minX || box.maxZ <= box.minZ || box.maxY <= box.minY
                    || box.maxY < body.minY - MAX_STEP_DOWN - HEIGHT_EPSILON
                    || box.maxY > body.minY + stepUp + HEIGHT_EPSILON) continue;
            Interval x = overlap(foot.minX, foot.maxX, box.minX, box.maxX, dx);
            Interval z = overlap(foot.minZ, foot.maxZ, box.minZ, box.maxZ, dz);
            if (x == null || z == null) continue;
            double start = Math.max(x.start, z.start), end = Math.min(x.end, z.end);
            if (start < end && end > 0 && start < 1) intervals.add(new Interval(start, end));
        }
        intervals.sort(Comparator.comparingDouble(interval -> interval.start));
        double reach = 0;
        for (Interval interval : intervals) {
            // Open intervals require positive overlap. Merely touching the next
            // support cannot join two platforms across an unsupported point.
            if (interval.start >= reach) break;
            reach = Math.max(reach, interval.end);
            if (reach > 1) return 1;
        }
        if (reach <= 0) return 0;
        double distance = Math.max(Math.abs(dx), Math.abs(dz));
        if (distance == 0) return 1;
        return Math.max(0, Math.min(1, reach - EDGE_MARGIN / distance));
    }

    private static Interval overlap(double min, double max, double supportMin, double supportMax,
                                     double movement) {
        if (movement == 0) return max > supportMin && min < supportMax
                ? new Interval(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY) : null;
        double a = (supportMin - max) / movement, b = (supportMax - min) / movement;
        return new Interval(Math.min(a, b), Math.max(a, b));
    }

    private static final class Interval {
        final double start, end;
        Interval(double start, double end) { this.start = start; this.end = end; }
    }
}
