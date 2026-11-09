package me.youngjibbit.core.combat;

import java.util.Random;

/**
 * Pure Java 8 rotation candidate planner. It never owns a rotation, packet, attack or input.
 * A caller must supply an authorized owner/acquisition and an accepted or visible base pose.
 * No sensitivity quantization is performed: RotationManager remains the sole final authority.
 */
public final class AdaptiveMotionController {
    private static final long MAX_PAUSE_NANOS = 250_000_000L;
    private static final double MAX_DT_SECONDS = 0.075;
    private static final double EPSILON_DEGREES = 1.0e-7;

    /** Tokens are compared by object identity, never by an entity's reusable numeric ID. */
    public static final class Acquisition {
        public final Object owner, scene, target;
        public final long connectionEpoch, ordinal;

        public Acquisition(Object owner, Object scene, Object target, long connectionEpoch, long ordinal) {
            this.owner = owner;
            this.scene = scene;
            this.target = target;
            this.connectionEpoch = connectionEpoch;
            this.ordinal = ordinal;
        }

        private boolean usable() {
            return owner != null && scene != null && target != null
                    && connectionEpoch >= 0 && ordinal >= 0;
        }

        private boolean same(Acquisition other) {
            return other != null && owner == other.owner && scene == other.scene
                    && target == other.target && connectionEpoch == other.connectionEpoch
                    && ordinal == other.ordinal;
        }
    }

    /** Physical limits in degrees/second, degrees/second^2 and degrees/second^3. */
    public static final class Limits {
        public final double maxYawSpeed, maxPitchSpeed, maxAcceleration, maxJerk;
        public final double variation, curvature;
        /** Optional calmer keeping phase. NaN keeps the uniform legacy limits byte for byte. */
        public final double keepYawSpeed, keepPitchSpeed, keepEnterProgress;

        public Limits(double maxYawSpeed, double maxPitchSpeed, double maxAcceleration,
                      double maxJerk, double variation, double curvature) {
            this(maxYawSpeed, maxPitchSpeed, maxAcceleration, maxJerk, variation, curvature,
                    Double.NaN, Double.NaN, Double.NaN);
        }

        public Limits(double maxYawSpeed, double maxPitchSpeed, double maxAcceleration,
                      double maxJerk, double variation, double curvature,
                      double keepYawSpeed, double keepPitchSpeed, double keepEnterProgress) {
            if (!positive(maxYawSpeed) || !positive(maxPitchSpeed)
                    || !positive(maxAcceleration) || !positive(maxJerk)
                    || !Double.isFinite(variation) || variation < 0 || variation > 0.3
                    || !Double.isFinite(curvature) || curvature < 0 || curvature > 0.25) {
                throw new IllegalArgumentException("invalid Adaptive physical limits");
            }
            boolean yawPhased = !Double.isNaN(keepYawSpeed);
            boolean pitchPhased = !Double.isNaN(keepPitchSpeed);
            if (yawPhased || pitchPhased) {
                if ((yawPhased && !positive(keepYawSpeed)) || (pitchPhased && !positive(keepPitchSpeed))
                        || !Double.isFinite(keepEnterProgress)
                        || keepEnterProgress < 0 || keepEnterProgress >= 1)
                    throw new IllegalArgumentException("invalid Adaptive keeping-phase limits");
            } else if (!Double.isNaN(keepEnterProgress)) {
                throw new IllegalArgumentException("keeping progress without keeping caps");
            }
            this.maxYawSpeed = maxYawSpeed;
            this.maxPitchSpeed = maxPitchSpeed;
            this.maxAcceleration = maxAcceleration;
            this.maxJerk = maxJerk;
            this.variation = variation;
            this.curvature = curvature;
            this.keepYawSpeed = keepYawSpeed;
            this.keepPitchSpeed = keepPitchSpeed;
            this.keepEnterProgress = keepEnterProgress;
        }

        double yawSpeedCap(double progress) {
            if (Double.isNaN(keepYawSpeed) || Double.isNaN(keepEnterProgress)
                    || progress < keepEnterProgress) return maxYawSpeed;
            return Math.min(maxYawSpeed, keepYawSpeed);
        }

        double pitchSpeedCap(double progress) {
            if (Double.isNaN(keepPitchSpeed) || Double.isNaN(keepEnterProgress)
                    || progress < keepEnterProgress) return maxPitchSpeed;
            return Math.min(maxPitchSpeed, keepPitchSpeed);
        }

        public static Limits balanced() {
            return new Limits(180, 90, 900, 18000, 0.14, 0.12);
        }
    }

    /** Immutable seed-selected parameters: sampled once per genuine acquisition. */
    public static final class Profile {
        public final double speedScale, accelerationScale, brakingScale, lateralBias;

        private Profile(Random random, double curvature) {
            speedScale = 0.85 + random.nextDouble() * 0.30;
            accelerationScale = 0.82 + random.nextDouble() * 0.36;
            brakingScale = 0.78 + random.nextDouble() * 0.44;
            lateralBias = (random.nextDouble() * 2 - 1) * curvature;
        }
    }

    /** Candidate is mathematical only; HOLD never grants a C03 or C02 permission. */
    public static final class Result {
        public final double yaw, pitch;
        public final boolean candidate;
        public final double yawVelocity, pitchVelocity, yawAcceleration, pitchAcceleration, noise;

        private Result(double yaw, double pitch, boolean candidate,
                       double yawVelocity, double pitchVelocity,
                       double yawAcceleration, double pitchAcceleration, double noise) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.candidate = candidate;
            this.yawVelocity = yawVelocity;
            this.pitchVelocity = pitchVelocity;
            this.yawAcceleration = yawAcceleration;
            this.pitchAcceleration = pitchAcceleration;
            this.noise = noise;
        }
    }

    private static final class State {
        final Acquisition acquisition;
        final Random random;
        final Profile profile;
        long lastSequence, lastNanos;
        double yawVelocity, pitchVelocity, yawAcceleration, pitchAcceleration;
        double noise, initialDistance;
        Result cached;

        State(Acquisition acquisition, long seed, double curvature, long sequence, long nanos,
              Result initial) {
            this.acquisition = acquisition;
            this.random = new Random(seed);
            this.profile = new Profile(random, curvature);
            this.lastSequence = sequence;
            this.lastNanos = nanos;
            this.cached = initial;
        }
    }

    private final long seed;
    private final Limits limits;
    private State current;
    // High-water tombstone prevents an invalidated generation from rearming via late callbacks.
    private long lastEpoch = -1, lastOrdinal = -1;

    public AdaptiveMotionController(long seed, Limits limits) {
        if (limits == null) throw new IllegalArgumentException("limits required");
        this.seed = seed;
        this.limits = limits;
    }

    /** Null when no authorized acquisition is active. Read only; no random draws. */
    public synchronized Profile profile() {
        return current == null ? null : current.profile;
    }

    /**
     * One state transition per distinct motion sequence, independently of render FPS.
     * The caller must set authorized=false on cancel, focus/GUI loss, foreign owner,
     * disable, invalid scene or transport rejection. It must use a strictly newer
     * epoch/ordinal for reacquisition; a numeric entity ID alone is not sufficient.
     */
    public synchronized Result advance(Acquisition acquisition, long sequence, long nanos,
                                       double currentYaw, double currentPitch,
                                       double targetYaw, double targetPitch, boolean authorized) {
        Result hold = hold(currentYaw, currentPitch);
        if (acquisition == null || !acquisition.usable() || !authorized
                || !validAngles(currentYaw, currentPitch, targetYaw, targetPitch)
                || nanos < 0) {
            invalidateIfCurrent(acquisition);
            return hold;
        }
        int order = order(acquisition);
        if (order < 0) return hold;
        if (order > 0) {
            // An explicit higher generation is the only way to take ownership.
            lastEpoch = acquisition.connectionEpoch;
            lastOrdinal = acquisition.ordinal;
            current = new State(acquisition, mixSeed(seed, lastEpoch, lastOrdinal),
                    limits.curvature, sequence, nanos, hold);
            return hold;
        }
        State state = current;
        if (state == null || !state.acquisition.same(acquisition)) return hold;
        if (sequence == state.lastSequence) return state.cached;
        if (sequence < state.lastSequence) return hold;
        long gap = nanos - state.lastNanos;
        if (nanos < state.lastNanos || gap >= MAX_PAUSE_NANOS) {
            invalidateIfCurrent(acquisition);
            return hold;
        }
        state.lastSequence = sequence;
        if (gap <= 0) {
            state.cached = hold;
            return hold;
        }
        state.lastNanos = nanos;
        double dt = Math.min(MAX_DT_SECONDS, gap * 1.0e-9);
        double yawError = wrap(targetYaw - currentYaw);
        double pitchError = clamp(targetPitch, -90, 90) - clamp(currentPitch, -90, 90);
        double distance = Math.hypot(yawError, pitchError);
        if (distance <= EPSILON_DEGREES) {
            // Exactly settled: no residual turn or random walk at the target.
            state.yawVelocity = state.pitchVelocity = 0;
            state.yawAcceleration = state.pitchAcceleration = 0;
            state.cached = hold;
            return hold;
        }
        if (state.initialDistance < EPSILON_DEGREES) state.initialDistance = distance;
        // Moving target: broaden the progress reference, but never redraw the profile.
        state.initialDistance = Math.max(state.initialDistance, distance);
        double progress = clamp(1 - distance / state.initialDistance, 0, 1);
        double sideways = state.profile.lateralBias * 4 * progress * (1 - progress);        double x = yawError / distance - sideways * pitchError / distance;
        double y = pitchError / distance + sideways * yawError / distance;
        double norm = Math.hypot(x, y);
        x /= norm;
        y /= norm;

        // Stable, bounded first-order correlated noise; a draw occurs only per motion ID.
        double alpha = Math.exp(-dt / 0.22);
        state.noise = alpha * state.noise + (1 - alpha) * (state.random.nextDouble() * 2 - 1);
        double yawCap = limits.yawSpeedCap(progress);
        double pitchCap = limits.pitchSpeedCap(progress);
        double directionalCap = Math.min(yawCap / Math.max(Math.abs(x), 1e-9),
                pitchCap / Math.max(Math.abs(y), 1e-9));
        double accelCap = limits.maxAcceleration * state.profile.accelerationScale;
        double brakeCap = limits.maxAcceleration * state.profile.brakingScale;
        double speed = Math.min(directionalCap * state.profile.speedScale
                        * (1 + limits.variation * state.noise),
                Math.sqrt(2 * brakeCap * distance) * 0.88);
        // Every axis shares the same dt, phase, acquisition profile and noise state.
        state.yawAcceleration = evolveAcceleration(state.yawAcceleration, state.yawVelocity,
                speed * x, accelCap, limits.maxJerk, dt);
        state.pitchAcceleration = evolveAcceleration(state.pitchAcceleration, state.pitchVelocity,
                speed * y, accelCap, limits.maxJerk, dt);
        state.yawVelocity = clamp(state.yawVelocity + state.yawAcceleration * dt, -yawCap, yawCap);
        state.pitchVelocity = clamp(state.pitchVelocity + state.pitchAcceleration * dt, -pitchCap, pitchCap);
        double yawStep = boundStep(state.yawVelocity * dt, yawError);
        double pitchStep = boundStep(state.pitchVelocity * dt, pitchError);
        // Respect actual emitted displacement when the residual-distance clamp engages.
        state.yawVelocity = yawStep / dt;
        state.pitchVelocity = pitchStep / dt;
        double yaw = wrap(currentYaw + yawStep);
        double pitch = clamp(currentPitch + pitchStep, -90, 90);
        state.cached = new Result(yaw, pitch, Math.abs(yawStep) > EPSILON_DEGREES
                || Math.abs(pitchStep) > EPSILON_DEGREES,
                state.yawVelocity, state.pitchVelocity,
                state.yawAcceleration, state.pitchAcceleration, state.noise);
        return state.cached;
    }

    /** Invalidate only the currently matching acquisition; stale callbacks are harmless. */
    public synchronized boolean invalidateIfCurrent(Acquisition acquisition) {
        if (current == null || !current.acquisition.same(acquisition)) return false;
        current = null; // Keep high-water marker to block same-generation reentry.
        return true;
    }

    /** Owner-only full disable: a subsequent acquisition must use a newer ordinal. */
    public synchronized void reset() {
        current = null;
    }

    private int order(Acquisition token) {
        if (token.connectionEpoch != lastEpoch)
            return Long.compare(token.connectionEpoch, lastEpoch);
        return Long.compare(token.ordinal, lastOrdinal);
    }

    private static Result hold(double yaw, double pitch) {
        return new Result(Double.isFinite(yaw) ? wrap(yaw) : 0,
                Double.isFinite(pitch) ? clamp(pitch, -90, 90) : 0,
                false, 0, 0, 0, 0, 0);
    }

    private static double evolveAcceleration(double previous, double velocity, double desired,
                                             double accelerationCap, double jerkCap, double dt) {
        double requested = clamp((desired - velocity) / dt, -accelerationCap, accelerationCap);
        return clamp(requested, previous - jerkCap * dt, previous + jerkCap * dt);
    }

    private static double boundStep(double delta, double remaining) {
        // A moving target may demand braking in the opposite direction; never jump sign.
        if (delta * remaining > 0 && Math.abs(delta) > Math.abs(remaining)) return remaining;
        return delta;
    }

    private static boolean validAngles(double... angles) {
        for (double angle : angles) if (!Double.isFinite(angle)) return false;
        return true;
    }

    private static boolean positive(double value) {
        return Double.isFinite(value) && value > 0;
    }

    private static double clamp(double v, double min, double max) {
        return Math.min(max, Math.max(min, v));
    }

    private static double wrap(double angle) {
        double value = angle % 360;
        if (value <= -180) value += 360;
        if (value > 180) value -= 360;
        return value;
    }

    private static long mixSeed(long base, long epoch, long ordinal) {
        long z = base ^ epoch * 0x9E3779B97F4A7C15L ^ ordinal * 0xC2B2AE3D27D4EB4FL;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
