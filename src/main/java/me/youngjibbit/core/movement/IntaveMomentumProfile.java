package me.youngjibbit.core.movement;

/**
 * Pure, opt-in acceleration profile for a final ordinary moveFlying event.
 *
 * <p>The host supplies the already corrected axes/yaw and proves the current
 * scene, actual ordinary step, lifecycle and impulse eligibility. This class
 * owns no player, world, clock, packet, timer, input key or movement vector. A
 * result is only a proposed event-friction adjustment, never step authority.
 * The host must revalidate its generation before applying a result.</p>
 *
 * <p>One instance belongs to one RuntimeObservation lifetime. Its outer tick IDs
 * are monotone even across world/connection resets. reset/pause preserve the
 * consumed tick tombstone, so neither can manufacture a second opportunity in
 * one physical outer tick. A replacement observer requires a replacement profile.
 * Missing ordinary steps never accumulate acceleration credit.</p>
 *
 * <p>At base friction .1, the ground limit is .012 (12%), reached in three
 * eligible coherent steps. At sprint friction .13 this is about 9.23%. Wall is
 * capped at .006; air starts at at most .002 (10% of base .02) and decays over
 * four real air steps. Relative caps also respect smaller supplied friction.
 * These are model limits, not measured native speed or server-acceptance claims.</p>
 */
public final class IntaveMomentumProfile {
    public static final float GROUND_EXTRA = .012F;
    public static final float GROUND_RISE = .004F;
    public static final float WALL_EXTRA = .006F;
    public static final float AIR_EXTRA = .002F;
    public static final int AIR_STEPS = 4;
    public static final double MAX_PREDICTED_HORIZONTAL = .4;

    private static final double GROUND_RATIO = .12;
    private static final double WALL_RATIO = .06;
    private static final double AIR_RATIO = .10;
    private static final double MIN_INPUT_SQUARED = 1.0E-4;
    // A 45-degree digital diagonal change remains coherent; a 90/180 turn does not.
    private static final double COHERENT_DOT = .5;

    public enum Phase { IDLE, GROUND, WALL, AIR, LANDING, TURNING, PAUSED }
    public enum Reason {
        APPLIED, INACTIVE_GENERATION, NOT_ORDINARY, DUPLICATE_OR_OLD,
        PAUSED, INELIGIBLE, INVALID_INPUT, NO_INPUT, DIRECTION_CHANGE,
        AIR_EXHAUSTED, SPEED_BOUND, NO_REPRESENTABLE_EXTRA
    }

    /** Immutable facts from one current final event; eligibility is supplied by the host. */
    public static final class Input {
        public final long generation, outerTickId;
        public final boolean ordinary, eligible, grounded, wall;
        public final float strafe, forward, yaw, friction;
        public final double velocityX, velocityZ;

        public Input(long generation, long outerTickId, boolean ordinary, boolean eligible,
                     boolean grounded, boolean wall, float strafe, float forward,
                     float yaw, float friction, double velocityX, double velocityZ) {
            this.generation = generation;
            this.outerTickId = outerTickId;
            this.ordinary = ordinary;
            this.eligible = eligible;
            this.grounded = grounded;
            this.wall = wall;
            this.strafe = strafe;
            this.forward = forward;
            this.yaw = yaw;
            this.friction = friction;
            this.velocityX = velocityX;
            this.velocityZ = velocityZ;
        }
    }

    /**
     * Apply friction only when hasAdjustment() is true and host scope is still
     * current. For invalid nonfinite base friction, the diagnostic friction is
     * zero; a rejected result must never replace the original event value.
     */
    public static final class Result {
        public final long generation, outerTickId;
        public final float friction, extraFriction;
        public final Phase phase;
        public final Reason reason;

        private Result(Input input, float friction, float extraFriction, Phase phase, Reason reason) {
            this.generation = input == null ? -1 : input.generation;
            this.outerTickId = input == null ? -1 : input.outerTickId;
            this.friction = friction;
            this.extraFriction = extraFriction;
            this.phase = phase;
            this.reason = reason;
        }

        public boolean hasAdjustment() { return extraFriction > 0 && reason == Reason.APPLIED; }
    }

    private long generation = -1, lastOuterTick = -1;
    private boolean paused, hasDirection, hasGroundRun, previousGrounded;
    private double directionX, directionZ;
    private float previousExtra;
    private int airSteps;

    /** A strictly newer host generation resumes cold; stale resets have no effect. */
    public synchronized boolean reset(long newGeneration) {
        return replaceGeneration(newGeneration, false);
    }

    /** No timeout/tick credits: only reset with a newer generation can release this pause. */
    public synchronized boolean pause(long newGeneration) {
        return replaceGeneration(newGeneration, true);
    }

    private boolean replaceGeneration(long newGeneration, boolean pause) {
        if (newGeneration < 0 || newGeneration <= generation) return false;
        generation = newGeneration;
        paused = pause;
        clearMotionHistory();
        return true;
    }

    /** Consumes at most one current ordinary opportunity, including a rejected fresh input. */
    public synchronized Result step(Input input) {
        if (input == null) return unchanged(null, Phase.IDLE, Reason.INVALID_INPUT);
        if (generation < 0 || input.generation != generation)
            return unchanged(input, Phase.IDLE, Reason.INACTIVE_GENERATION);
        if (!input.ordinary || input.outerTickId < 0)
            return unchanged(input, Phase.IDLE, Reason.NOT_ORDINARY);
        if (input.outerTickId <= lastOuterTick)
            return unchanged(input, Phase.IDLE, Reason.DUPLICATE_OR_OLD);

        boolean contiguous = lastOuterTick >= 0 && input.outerTickId == lastOuterTick + 1;
        lastOuterTick = input.outerTickId;
        if (!contiguous) clearMotionHistory();
        if (paused) return unchanged(input, Phase.PAUSED, Reason.PAUSED);
        if (!input.eligible) return reject(input, Reason.INELIGIBLE);
        if (!valid(input)) return reject(input, Reason.INVALID_INPUT);

        double length = Math.hypot(input.strafe, input.forward);
        if (length * length < MIN_INPUT_SQUARED) return reject(input, Reason.NO_INPUT);
        double radians = Math.toRadians(input.yaw % 360.0);
        double sine = Math.sin(radians), cosine = Math.cos(radians);
        double worldX = (input.strafe * cosine - input.forward * sine) / Math.max(1, length);
        double worldZ = (input.forward * cosine + input.strafe * sine) / Math.max(1, length);
        double worldLength = Math.hypot(worldX, worldZ);
        double unitX = worldX / worldLength, unitZ = worldZ / worldLength;
        boolean changed = hasDirection && unitX * directionX + unitZ * directionZ < COHERENT_DOT;
        boolean landing = hasDirection && !previousGrounded && input.grounded;
        directionX = unitX;
        directionZ = unitZ;
        hasDirection = true;

        if (changed) {
            previousExtra = 0;
            airSteps = 0;
            hasGroundRun = input.grounded;
            previousGrounded = input.grounded;
            return unchanged(input, Phase.TURNING, Reason.DIRECTION_CHANGE);
        }

        Phase phase;
        double requested;
        if (input.grounded) {
            if (landing) previousExtra = 0;
            hasGroundRun = true;
            previousGrounded = true;
            airSteps = 0;
            phase = landing ? Phase.LANDING : input.wall ? Phase.WALL : Phase.GROUND;
            double limit = Math.min(input.wall ? WALL_EXTRA : GROUND_EXTRA,
                    input.friction * (input.wall ? WALL_RATIO : GROUND_RATIO));
            requested = Math.min(limit, previousExtra + (double) GROUND_RISE);
        } else {
            previousGrounded = false;
            phase = Phase.AIR;
            if (!hasGroundRun || input.wall || airSteps >= AIR_STEPS) {
                previousExtra = 0;
                if (input.wall) hasGroundRun = false;
                return unchanged(input, phase, Reason.AIR_EXHAUSTED);
            }
            airSteps++;
            double limit = Math.min(AIR_EXTRA, input.friction * AIR_RATIO);
            requested = limit * (AIR_STEPS - airSteps + 1) / AIR_STEPS;
        }

        // Never round a requested upper bound upward when storing event friction.
        float adjusted = (float) (input.friction + requested);
        if ((double) adjusted - input.friction > requested) adjusted = Math.nextDown(adjusted);
        float extra = adjusted - input.friction;
        if (!(extra > 0)) {
            previousExtra = 0;
            return unchanged(input, phase, Reason.NO_REPRESENTABLE_EXTRA);
        }

        double predictedX = input.velocityX + worldX * adjusted;
        double predictedZ = input.velocityZ + worldZ * adjusted;
        if (!Double.isFinite(predictedX) || !Double.isFinite(predictedZ)
                || Math.hypot(predictedX, predictedZ) > MAX_PREDICTED_HORIZONTAL) {
            previousExtra = 0;
            return unchanged(input, phase, Reason.SPEED_BOUND);
        }
        previousExtra = extra;
        return new Result(input, adjusted, extra, phase, Reason.APPLIED);
    }

    private static boolean valid(Input input) {
        return Float.isFinite(input.strafe) && Float.isFinite(input.forward)
                && Math.abs(input.strafe) <= 2 && Math.abs(input.forward) <= 2
                && Float.isFinite(input.yaw) && Float.isFinite(input.friction)
                && input.friction > 0 && input.friction <= .2F
                && Double.isFinite(input.velocityX) && Double.isFinite(input.velocityZ);
    }

    private Result reject(Input input, Reason reason) {
        clearMotionHistory();
        return unchanged(input, Phase.IDLE, reason);
    }

    private void clearMotionHistory() {
        hasDirection = hasGroundRun = previousGrounded = false;
        directionX = directionZ = 0;
        previousExtra = 0;
        airSteps = 0;
    }

    private static Result unchanged(Input input, Phase phase, Reason reason) {
        float friction = input != null && Float.isFinite(input.friction) ? input.friction : 0;
        return new Result(input, friction, 0, phase, reason);
    }
}
