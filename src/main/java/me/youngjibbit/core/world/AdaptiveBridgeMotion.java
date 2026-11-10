package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.AdaptiveMotionController;
import me.youngjibbit.utils.math.FastNoiseLite;
import java.lang.ref.WeakReference;

/** Scaffold-only mathematics. The host keeps every rotation, input and placement permission. */
public final class AdaptiveBridgeMotion {
    public enum Mode { ADAPTIVE, GODBRIDGE }
    /** Urgency of the host's checked placement task; it never grants placement authority. */
    public enum Task { TRACKING, APPROACH, EDGE, CLUTCH }
    private static final double MOTION_SECONDS = .05;
    private static final long MAX_PAUSE_NANOS = 250_000_000L;
    /**
     * Preset fractions of the host yaw/pitch slider base (degrees per 50 ms).
     * The keeping multipliers reproduce the accepted W08 pace exactly, so a
     * preset change cannot silently alter the settled micro-correction speed.
     */
    public enum Speed {
        CALM(.5, .38, .75), BALANCED(.75, .55, 1), SWIFT(1, .8, 1.15), CUSTOM(1, 1, 1);
        final double initial, keeping, acceleration;
        Speed(double initial, double keeping, double acceleration) {
            this.initial = initial; this.keeping = keeping; this.acceleration = acceleration;
        }
    }

    /** Original yaw/pitch sliders remain degrees per 50 ms; caps are degrees per second. */
    public static final class Settings {
        public final Mode mode;
        public final Speed speed;
        public final double variation;
        /** Initial-phase slider base (degrees per 50 ms). */
        public final double initialYawTicks, initialPitchTicks;
        /** Keeping-phase slider base, or null for the flat W08 limits of the preset constructor. */
        public final Double keepingYawTicks, keepingPitchTicks;
        public final double maxYawSpeed, maxPitchSpeed;
        /** Degrees per second caps of the settled keeping phase; equal to the base caps when flat. */
        public final double keepingYawSpeed, keepingPitchSpeed;
        public final double maxAcceleration, maxJerk;
        /** The keeping phase engages once this fraction of the initial distance is covered. */
        static final double KEEPING_PROGRESS = .4;

        public Settings(Mode mode, Speed speed, double variation, double yawTicks, double pitchTicks) {
            this(mode, speed, variation, yawTicks, pitchTicks, null, null);
        }

        public Settings(Mode mode, Speed speed, double variation,
                double initialYawTicks, double initialPitchTicks,
                double keepingYawTicks, double keepingPitchTicks) {
            this(mode, speed, variation, initialYawTicks, initialPitchTicks,
                    Double.valueOf(keepingYawTicks), Double.valueOf(keepingPitchTicks));
        }

        private Settings(Mode mode, Speed speed, double variation,
                double initialYawTicks, double initialPitchTicks,
                Double keepingYawTicks, Double keepingPitchTicks) {
            if (mode == null || speed == null || !finite(variation) || variation < 0 || variation > 1
                    || !positive(initialYawTicks) || !positive(initialPitchTicks)
                    || initialYawTicks > 90 || initialPitchTicks > 90
                    || keepingYawTicks != null && (!positive(keepingYawTicks) || keepingYawTicks > 90)
                    || keepingPitchTicks != null && (!positive(keepingPitchTicks) || keepingPitchTicks > 90))
                throw new IllegalArgumentException("invalid bridge rotation settings");
            this.mode = mode; this.speed = speed; this.variation = variation;
            this.initialYawTicks = initialYawTicks; this.initialPitchTicks = initialPitchTicks;
            this.keepingYawTicks = keepingYawTicks; this.keepingPitchTicks = keepingPitchTicks;
            double profileScale = mode == Mode.GODBRIDGE ? .8 : 1;
            // The flat preset constructor keeps the accepted W08 caps as uniform limits;
            // the split constructor strengthens only the initial phase.
            double initialMultiplier = keepingYawTicks == null ? speed.keeping : speed.initial;
            maxYawSpeed = clamp(initialYawTicks * 20 * initialMultiplier * profileScale, 20, 720);
            maxPitchSpeed = clamp(initialPitchTicks * 20 * initialMultiplier * profileScale, 20, 720);
            keepingYawSpeed = keepingYawTicks == null ? maxYawSpeed
                    : clamp(keepingYawTicks * 20 * speed.keeping * profileScale, 20, 720);
            keepingPitchSpeed = keepingPitchTicks == null ? maxPitchSpeed
                    : clamp(keepingPitchTicks * 20 * speed.keeping * profileScale, 20, 720);
            maxAcceleration = clamp(Math.max(maxYawSpeed, maxPitchSpeed) * 6 * speed.acceleration, 300, 4200);
            maxJerk = clamp(maxAcceleration * 20, 9000, 84000);
        }
        private AdaptiveMotionController.Limits limits() {
            // Reserve the reused engine's immutable acceleration-scale maximum.
            // It adds no second angular random walk or lateral curvature here.
            if (keepingYawTicks == null)
                return new AdaptiveMotionController.Limits(maxYawSpeed, maxPitchSpeed,
                        maxAcceleration / 1.18, maxJerk, 0, 0);
            return new AdaptiveMotionController.Limits(maxYawSpeed, maxPitchSpeed,
                    maxAcceleration / 1.18, maxJerk, 0, 0,
                    keepingYawSpeed, keepingPitchSpeed, KEEPING_PROGRESS);
        }
        public boolean same(Settings other) {
            return other != null && mode == other.mode && speed == other.speed
                    && Double.compare(variation, other.variation) == 0
                    && Double.compare(initialYawTicks, other.initialYawTicks) == 0
                    && Double.compare(initialPitchTicks, other.initialPitchTicks) == 0
                    && java.util.Objects.equals(keepingYawTicks, other.keepingYawTicks)
                    && java.util.Objects.equals(keepingPitchTicks, other.keepingPitchTicks);
        }

        /** Degrees/second; emergency presets reserve at most 90/180 degrees per nominal tick. */
        public double yawSpeed(Task task) {
            double base = task == Task.TRACKING ? keepingYawSpeed : maxYawSpeed;
            if (speed == Speed.CUSTOM) return base;
            return task == Task.CLUTCH ? Math.max(base, 3600)
                    : task == Task.EDGE ? Math.max(base, 1800) : base;
        }
        /** Degrees/second; CUSTOM always retains the configured initial/keeping limits. */
        public double pitchSpeed(Task task) {
            double base = task == Task.TRACKING ? keepingPitchSpeed : maxPitchSpeed;
            return speed != Speed.CUSTOM && (task == Task.EDGE || task == Task.CLUTCH)
                    ? Math.max(base, 1800) : base;
        }
    }

    public static final class Point {
        public final double x, y, z;
        private Point(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    }
    /** One task-aware turn proposal, before the manager's final sensitivity quantization. */
    public static final class Turn {
        public final double yaw, pitch, yawVelocity, pitchVelocity;
        public final boolean candidate;
        private Turn(double yaw, double pitch, double yawVelocity, double pitchVelocity) {
            this.yaw = yaw; this.pitch = pitch;
            this.yawVelocity = yawVelocity; this.pitchVelocity = pitchVelocity;
            candidate = Math.abs(yawVelocity) > 1e-7 || Math.abs(pitchVelocity) > 1e-7;
        }
    }

    /** Time continuity belongs to the bridge manoeuvre, not to its next block face. */
    private static final class TaskTrajectory {
        final AdaptiveBridgePlanner.Scope scope;
        final Settings settings;
        long lastSequence, lastNanos;
        TaskTrajectory(AdaptiveBridgePlanner.Scope scope, Settings settings, long sequence, long nanos) {
            this.scope = scope; this.settings = settings;
            lastSequence = sequence; lastNanos = nanos;
        }
    }
    /** Weak tombstone: reset releases the active scene without reopening old generations. */
    private static final class Lifetime {
        final WeakReference<Object> owner, world, player, connection;
        final long epoch, generation;
        Lifetime(AdaptiveBridgePlanner.Scope scope) {
            owner = new WeakReference<>(scope.owner); world = new WeakReference<>(scope.world);
            player = new WeakReference<>(scope.player); connection = new WeakReference<>(scope.connection);
            epoch = scope.connectionEpoch; generation = scope.acquisitionGeneration;
        }
        boolean same(AdaptiveBridgePlanner.Scope scope) {
            return scope != null && owner.get() == scope.owner && world.get() == scope.world
                    && player.get() == scope.player && connection.get() == scope.connection
                    && epoch == scope.connectionEpoch && generation == scope.acquisitionGeneration;
        }
    }
    private static final class Acquisition {
        final AdaptiveBridgePlanner.Scope scope;
        final Object identity;
        final Settings settings;
        final AdaptiveMotionController dynamics;
        final AdaptiveMotionController.Acquisition token;
        final FastNoiseLite noise;
        final long startNanos;
        long lastSequence, lastNanos, pointSequence = Long.MIN_VALUE, rotationSequence = Long.MIN_VALUE;
        double[] geometry;
        AdaptiveBridgePlanner.Face face;
        Point cachedPoint;
        AdaptiveMotionController.Result cachedRotation;
        Turn cachedTurn;
        Acquisition(AdaptiveBridgePlanner.Scope scope, Object identity, Settings settings,
                    long seed, long ordinal, long sequence, long nanos) {
            this.scope = scope; this.identity = identity; this.settings = settings;
            startNanos = lastNanos = nanos; lastSequence = sequence;
            dynamics = new AdaptiveMotionController(seed, settings.limits());
            // Pipeline epochs restart. Only this wrapper's own ordinal is globally ordered here.
            token = new AdaptiveMotionController.Acquisition(this, scope.world, identity, 0, ordinal);
            noise = new FastNoiseLite((int)(seed ^ ordinal * 0x9e3779b97f4a7c15L));
            noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
            noise.SetFractalType(FastNoiseLite.FractalType.None); noise.SetFrequency(1);
        }
    }

    private final Thread owningThread = Thread.currentThread();
    private final long seed;
    private Lifetime highest;
    private Acquisition current;
    private TaskTrajectory taskTrajectory;
    private long ordinal, lastSequence = Long.MIN_VALUE, blockedThrough = Long.MIN_VALUE;
    private boolean retired;
    public AdaptiveBridgeMotion(long seed) { this.seed = seed; }

    /**
     * The original anchor is held for the full face identity. Its normal coordinate never changes.
     * Tangential displacement is at most .08 blocks (Adaptive) or .035 blocks (Godbridge).
     * A duplicate motion returns its first point, independently of render time or call count.
     */
    public synchronized Point point(AdaptiveBridgePlanner.Scope scope, Object identity,
            Settings settings, long sequence, long nanos, AdaptiveBridgePlanner.Face face,
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            double anchorX, double anchorY, double anchorZ, boolean authorized) {
        Acquisition a = acquire(scope, identity, settings, sequence, nanos, authorized);
        if (a == null) return null;
        double[] geometry = { minX, minY, minZ, maxX, maxY, maxZ, anchorX, anchorY, anchorZ };
        if (!validGeometry(face, geometry)) { reset(scope); return null; }
        if (a.geometry == null) { a.geometry = geometry; a.face = face; }
        else if (a.face != face || !sameGeometry(a.geometry, geometry)) {
            reset(scope); return null;
        }
        if (a.pointSequence == sequence) return a.cachedPoint;
        double seconds = (a.lastNanos - a.startNanos) * 1e-9;
        double fade = 1 - Math.exp(-seconds / .2);
        double rate = settings.mode == Mode.GODBRIDGE ? .35 : .65;
        double radius = (settings.mode == Mode.GODBRIDGE ? .035 : .08) * settings.variation * fade;
        double[] point = { anchorX, anchorY, anchorZ };
        int normal = face.x != 0 ? 0 : face.y != 0 ? 1 : 2;
        double[] noise = new double[3];
        double norm = 0;
        for (int axis = 0; axis < 3; axis++) {
            if (axis == normal) continue;
            noise[axis] = clamp(a.noise.GetNoise((float)(seconds * rate), 17.371f + axis * 31.719f), -1, 1);
            norm += noise[axis] * noise[axis];
        }
        norm = Math.max(1, Math.sqrt(norm));
        for (int axis = 0; axis < 3; axis++) {
            if (axis == normal) continue;
            double inset = Math.min(.02, (geometry[axis + 3] - geometry[axis]) / 4);
            double low = geometry[axis] + inset, high = geometry[axis + 3] - inset;
            // An already validated edge anchor stays exact; do not jump it inward on acquisition.
            if (point[axis] < low || point[axis] > high) continue;
            double radiusOnAxis = Math.min(radius, (geometry[axis + 3] - geometry[axis]) * .15);
            point[axis] = clamp(point[axis] + noise[axis] / norm * radiusOnAxis, low, high);
        }
        a.pointSequence = sequence;
        return a.cachedPoint = new Point(point[0], point[1], point[2]);
    }

    /** One dynamics step per motion. A genuine acquisition's first step is an inert HOLD. */
    public synchronized AdaptiveMotionController.Result advance(AdaptiveBridgePlanner.Scope scope,
            Object identity, Settings settings, long sequence, long nanos,
            double fromYaw, double fromPitch, double targetYaw, double targetPitch, boolean authorized) {
        Acquisition a = acquire(scope, identity, settings, sequence, nanos, authorized);
        if (a == null) return null;
        if (!finite(fromYaw) || !finite(fromPitch) || !finite(targetYaw) || !finite(targetPitch)
                || fromPitch < -90 || fromPitch > 90) {
            rejectIfCurrent(scope, identity, sequence); return null;
        }
        if (a.rotationSequence == sequence) return a.cachedRotation;
        AdaptiveMotionController.Result result = a.dynamics.advance(a.token, sequence,
                a.lastNanos - a.startNanos, fromYaw, fromPitch, targetYaw, targetPitch, true);
        a.rotationSequence = sequence; a.cachedRotation = result;
        if (a.dynamics.profile() == null) { rejectIfCurrent(scope, identity, sequence); return null; }
        return result;
    }

    /**
     * A real host motion supplies a nominal 50 ms first-step budget. Looking at a
     * face does not spend it. Later steps use elapsed motion time, capped at 75 ms;
     * duplicate callbacks and a new face cannot buy another budget. Safe tracking
     * eases into the target; an imminent edge or falling catch removes that delay.
     * Physical motions without a task accrue no credit: resuming after them gets
     * one nominal step, while a stalled consecutive task is still rejected.
     * Custom sliders remain hard speed caps, even when a late catch is impossible.
     */
    public synchronized Turn advanceTask(AdaptiveBridgePlanner.Scope scope, Object identity,
            Settings settings, Task task, long sequence, long nanos,
            double fromYaw, double fromPitch, double targetYaw, double targetPitch, boolean authorized) {
        Acquisition a = acquire(scope, identity, settings, sequence, nanos, authorized);
        if (a == null) return null;
        if (task == null || !finite(fromYaw) || !finite(fromPitch) || !finite(targetYaw)
                || !finite(targetPitch) || fromPitch < -90 || fromPitch > 90
                || targetPitch < -90 || targetPitch > 90) {
            rejectIfCurrent(scope, identity, sequence); return null;
        }
        if (a.rotationSequence == sequence) return a.cachedTurn;
        double dt = MOTION_SECONDS;
        TaskTrajectory trajectory = taskTrajectory;
        if (trajectory != null && trajectory.scope.same(scope) && trajectory.settings.same(settings)) {
            long elapsed = a.lastNanos - trajectory.lastNanos;
            long motionGap = sequence - trajectory.lastSequence;
            if (motionGap <= 0 || elapsed < 0 || motionGap == 1 && elapsed >= MAX_PAUSE_NANOS) {
                rejectIfCurrent(scope, identity, sequence); return null;
            }
            dt = motionGap > 1 ? MOTION_SECONDS : Math.min(.075, elapsed * 1e-9);
        } else {
            trajectory = new TaskTrajectory(scope, settings, sequence, a.lastNanos);
            taskTrajectory = trajectory;
        }
        double yawError = wrap(wrap(targetYaw) - wrap(fromYaw)), pitchError = targetPitch - fromPitch;
        double yawCap = settings.yawSpeed(task), pitchCap = settings.pitchSpeed(task);
        double response = task == Task.TRACKING ? .10 : task == Task.APPROACH ? .04 : 0;
        double yawStep = taskStep(yawError, yawCap, dt, response);
        double pitchStep = taskStep(pitchError, pitchCap, dt, response);
        Turn turn = new Turn(wrap(wrap(fromYaw) + yawStep), fromPitch + pitchStep,
                dt > 0 ? yawStep / dt : 0, dt > 0 ? pitchStep / dt : 0);
        trajectory.lastSequence = sequence; trajectory.lastNanos = a.lastNanos;
        a.rotationSequence = sequence; a.cachedTurn = turn;
        return turn;
    }

    private static double taskStep(double error, double speed, double dt, double response) {
        if (dt <= 0 || Math.abs(error) <= 1e-7) return 0;
        double remaining = Math.abs(error);
        double eased = response == 0 ? remaining
                : Math.max(.2, remaining * -Math.expm1(-dt / response));
        return Math.copySign(Math.min(remaining, Math.min(speed * dt, eased)), error);
    }

    /** Read-only validity of the exact task proposal; a cached angle is not an active proposal. */
    public synchronized boolean isTaskCurrent(AdaptiveBridgePlanner.Scope scope, Object identity, long sequence) {
        return Thread.currentThread() == owningThread && !retired && current != null
                && current.scope.same(scope) && current.identity == identity
                && current.lastSequence == sequence && current.rotationSequence == sequence
                && current.cachedTurn != null && sequence > blockedThrough;
    }

    /** A rejected movement can restart only on a later physical motion. */
    public synchronized void rejectMotion(long sequence) {
        if (Thread.currentThread() != owningThread || current == null || current.lastSequence != sequence) return;
        blockedThrough = Math.max(blockedThrough, sequence); current = null; taskTrajectory = null;
    }
    /** Context-query rejection may return after a newer acquisition replaced this one. */
    public synchronized void rejectMotion(AdaptiveBridgePlanner.Scope scope, Object identity, long sequence) {
        if (Thread.currentThread() != owningThread || current == null || !current.scope.same(scope)
                || current.identity != identity) return;
        rejectMotion(sequence);
    }
    /** Explicit lifecycle invalidation requires a newer host generation to resume. */
    public synchronized boolean reset(AdaptiveBridgePlanner.Scope expected) {
        if (Thread.currentThread() != owningThread || highest == null || !highest.same(expected)) return false;
        current = null; taskTrajectory = null; retired = true; return true;
    }
    public synchronized void reset() {
        if (Thread.currentThread() != owningThread) return;
        current = null; taskTrajectory = null; retired = true;
    }

    private Acquisition acquire(AdaptiveBridgePlanner.Scope scope, Object identity, Settings settings,
                                long sequence, long nanos, boolean authorized) {
        if (Thread.currentThread() != owningThread) return null;
        if (scope == null || !scope.usable() || identity == null || settings == null || sequence < 0) return null;
        int order = order(scope);
        if (order < 0 || order == 0 && sequence < lastSequence) return null;
        if (!authorized) { if (order == 0) rejectIfCurrent(scope, identity, sequence); return null; }
        if (order > 0) {
            highest = new Lifetime(scope); current = null; taskTrajectory = null; retired = false;
            lastSequence = blockedThrough = Long.MIN_VALUE;
        }
        if (retired || sequence <= blockedThrough) return null;
        if (current != null && (current.identity != identity || !current.settings.same(settings))) {
            if (sequence == current.lastSequence) { rejectMotion(sequence); return null; }
            if (!current.settings.same(settings)) taskTrajectory = null;
            current = null;
        }
        if (current == null) {
            if (ordinal == Long.MAX_VALUE) return null;
            current = new Acquisition(scope, identity, settings, seed, ++ordinal, sequence, nanos);
        } else if (sequence == current.lastSequence) {
            return current; // The first call owns this motion's timestamp, including a point-only preview.
        } else if (nanos - current.lastNanos < 0 || nanos - current.startNanos < 0) {
            rejectIfCurrent(scope, identity, sequence); return null;
        }
        current.lastSequence = lastSequence = sequence; current.lastNanos = nanos;
        return current;
    }
    private void rejectIfCurrent(AdaptiveBridgePlanner.Scope scope, Object identity, long sequence) {
        if (current != null && current.scope.same(scope) && current.identity == identity
                && sequence >= current.lastSequence) {
            blockedThrough = Math.max(blockedThrough, sequence); lastSequence = Math.max(lastSequence, sequence);
            current = null; taskTrajectory = null;
        }
    }
    private int order(AdaptiveBridgePlanner.Scope scope) {
        if (highest == null) return 1;
        if (highest.owner.get() != scope.owner) return -1;
        int generation = Long.compare(scope.acquisitionGeneration, highest.generation);
        if (generation < 0) return -1;
        if (highest.connection.get() != scope.connection) return generation > 0 ? 1 : -1;
        if (scope.connectionEpoch < highest.epoch) return -1;
        if (generation > 0) return 1;
        if (highest.world.get() != scope.world || highest.player.get() != scope.player) return -1;
        return Long.compare(scope.connectionEpoch, highest.epoch);
    }
    private static boolean validGeometry(AdaptiveBridgePlanner.Face face, double[] g) {
        if (face == null) return false;
        for (double value : g) if (!finite(value) || Math.abs(value) > 30_000_001) return false;
        for (int axis = 0; axis < 3; axis++)
            if (g[axis + 3] <= g[axis] || g[axis + 3] - g[axis] > 4
                    || g[axis + 6] < g[axis] || g[axis + 6] > g[axis + 3]) return false;
        int normal = face.x != 0 ? 0 : face.y != 0 ? 1 : 2;
        int direction = face.x != 0 ? face.x : face.y != 0 ? face.y : face.z;
        return Math.abs(g[normal + 6] - g[normal + (direction > 0 ? 3 : 0)]) <= 1e-7;
    }
    private static boolean sameGeometry(double[] first, double[] next) {
        for (int i = 0; i < first.length; i++) if (Double.compare(first[i], next[i]) != 0) return false;
        return true;
    }
    private static boolean finite(double value) { return Double.isFinite(value); }
    private static double wrap(double value) {
        value %= 360;
        if (value > 180) value -= 360;
        if (value <= -180) value += 360;
        return value;
    }
    private static boolean positive(double value) { return finite(value) && value > 0; }
    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
