package me.youngjibbit.core.world;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Independent Java 8 PRE-only geometry scorer for a future optional Adaptive
 * Bridge approach. It knows no Minecraft, inventory, packet, event or GL class.
 * Results are proposals, NEVER a placement authorization or accepted C03.
 */
public final class AdaptiveBridgePlanner {
    public static final int MAX_OPTIONS = 24;
    public static final double MAX_PREDICTION_BLOCKS = .6;
    public static final double MAX_PREDICTION_TICKS = 3;
    private static final double EPS = 1.0e-6;

    public enum Face {
        DOWN(0, -1, 0), UP(0, 1, 0),
        NORTH(0, 0, -1), SOUTH(0, 0, 1),
        WEST(-1, 0, 0), EAST(1, 0, 0);
        public final int x, y, z;
        Face(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
    }

    /** A lifetime key; never substitute a reused numeric player/world ID. */
    public static final class Scope {
        public final Object owner, world, player, connection;
        public final long connectionEpoch, acquisitionGeneration;
        public Scope(Object owner, Object world, Object player, Object connection,
                     long connectionEpoch, long acquisitionGeneration) {
            this.owner = owner; this.world = world; this.player = player;
            this.connection = connection;
            this.connectionEpoch = connectionEpoch;
            this.acquisitionGeneration = acquisitionGeneration;
        }
        boolean usable() {
            return owner != null && world != null && player != null && connection != null
                    && connectionEpoch >= 0 && acquisitionGeneration >= 0;
        }
        boolean same(Scope other) {
            return other != null && owner == other.owner && world == other.world
                    && player == other.player && connection == other.connection
                    && connectionEpoch == other.connectionEpoch
                    && acquisitionGeneration == other.acquisitionGeneration;
        }
    }

    /** Snapshot captured by a future existing ScaffoldPlacementService adapter. */
    public static final class SupportFace {
        public final int x, y, z, slot;
        public final Face face;
        public final double hitX, hitY, hitZ;
        public final Object world, player, stack, supportState;
        public final boolean loaded, collisionClear, acceptedByHostService;

        public SupportFace(int x, int y, int z, Face face, double hitX, double hitY,
                           double hitZ, int slot, Object world, Object player,
                           Object stack, Object supportState, boolean loaded,
                           boolean collisionClear, boolean acceptedByHostService) {
            this.x = x; this.y = y; this.z = z; this.face = face;
            this.hitX = hitX; this.hitY = hitY; this.hitZ = hitZ;
            this.slot = slot; this.world = world; this.player = player;
            this.stack = stack; this.supportState = supportState;
            this.loaded = loaded; this.collisionClear = collisionClear;
            this.acceptedByHostService = acceptedByHostService;
        }
    }

    /** Pure input-only snapshot, normally built once per fresh motion PRE. */
    public static final class Frame {
        public Scope scope;
        public Object approvedStack;
        public long sequence;
        public int selectedSlot, destinationX, destinationY, destinationZ;
        public double feetX, feetY, feetZ, eyeX, eyeY, eyeZ;
        public double yaw, velocityX, velocityY, velocityZ, forward, strafe, reach = 4.5;
        /** Optional accepted aim basis; raw camera yaw above remains the input heading. */
        public double rotationYaw, rotationPitch;
        public boolean hasRotation, grounded;
        /** Nearest point on the raw-input lane, supplied by the independent input controller. */
        public boolean hasLane;
        public double laneX, laneZ, laneDirectionX, laneDirectionZ;
        /** A host with an explicit fixed-height policy can disallow the extra recovery layer. */
        public boolean allowLowerRecovery = true;
        public double predictionTicks = 1;
        public boolean authorized, eagleEnabled, physicalEdgeRisk;
    }

    /** The exact proposed support/face/stack identity: no POST search is provided. */
    public static final class Plan {
        public final boolean hasCandidate, suggestSneak;
        public final SupportFace intent;
        public final double yaw, pitch, predictedFeetX, predictedFeetZ;
        public final long sequence;

        private Plan(boolean hasCandidate, boolean suggestSneak, SupportFace intent,
                     double yaw, double pitch, double predictedFeetX,
                     double predictedFeetZ, long sequence) {
            this.hasCandidate = hasCandidate;
            this.suggestSneak = suggestSneak;
            this.intent = intent;
            this.yaw = yaw; this.pitch = pitch;
            this.predictedFeetX = predictedFeetX; this.predictedFeetZ = predictedFeetZ;
            this.sequence = sequence;
        }
    }

    private static final class State {
        final Scope scope;
        long lastSequence = Long.MIN_VALUE;
        int destinationX, destinationY, destinationZ, slot;
        double yaw, forward, strafe, velocityX, velocityY, velocityZ, predictionTicks;
        double rotationYaw, rotationPitch;
        boolean hasRotation, grounded, allowLowerRecovery;
        boolean hasLane;
        double laneX, laneZ, laneDirectionX, laneDirectionZ;
        Object stack;
        Plan cached;
        State(Scope scope) { this.scope = scope; }
        void snapshot(Frame f, Plan result) {
            lastSequence = f.sequence;
            destinationX = f.destinationX; destinationY = f.destinationY;
            destinationZ = f.destinationZ; slot = f.selectedSlot;
            stack = f.approvedStack;
            yaw = f.yaw; forward = f.forward; strafe = f.strafe;
            velocityX = f.velocityX; velocityY = f.velocityY; velocityZ = f.velocityZ;
            predictionTicks = f.predictionTicks;
            rotationYaw = f.rotationYaw; rotationPitch = f.rotationPitch;
            hasRotation = f.hasRotation; grounded = f.grounded;
            allowLowerRecovery = f.allowLowerRecovery;
            hasLane = f.hasLane; laneX = f.laneX; laneZ = f.laneZ;
            laneDirectionX = f.laneDirectionX; laneDirectionZ = f.laneDirectionZ;
            cached = result;
        }
        boolean sameIntent(Frame f) {
            return destinationX == f.destinationX && destinationY == f.destinationY
                    && destinationZ == f.destinationZ && slot == f.selectedSlot
                    && stack == f.approvedStack
                    && Double.compare(yaw, f.yaw) == 0
                    && Double.compare(forward, f.forward) == 0
                    && Double.compare(strafe, f.strafe) == 0
                    && Double.compare(velocityX, f.velocityX) == 0
                    && Double.compare(velocityY, f.velocityY) == 0
                    && Double.compare(velocityZ, f.velocityZ) == 0
                    && hasRotation == f.hasRotation && grounded == f.grounded
                    && allowLowerRecovery == f.allowLowerRecovery
                    && hasLane == f.hasLane
                    && (!hasLane || Double.compare(laneX, f.laneX) == 0
                        && Double.compare(laneZ, f.laneZ) == 0
                        && Double.compare(laneDirectionX, f.laneDirectionX) == 0
                        && Double.compare(laneDirectionZ, f.laneDirectionZ) == 0)
                    && (!hasRotation || Double.compare(rotationYaw, f.rotationYaw) == 0
                        && Double.compare(rotationPitch, f.rotationPitch) == 0)
                    && Double.compare(predictionTicks, f.predictionTicks) == 0;
        }
    }

    private static final class Lifetime {
        final WeakReference<Object> owner, world, player, connection;
        final long connectionEpoch, acquisitionGeneration;
        Lifetime(Scope scope) {
            owner = new WeakReference<>(scope.owner);
            world = new WeakReference<>(scope.world);
            player = new WeakReference<>(scope.player);
            connection = new WeakReference<>(scope.connection);
            connectionEpoch = scope.connectionEpoch;
            acquisitionGeneration = scope.acquisitionGeneration;
        }
    }

    private final Thread owningThread = Thread.currentThread();
    private State state;
    // Epochs belong to one connection; the host generation spans acquisitions.
    // Keep this complete lifetime tombstone when the active state is reset.
    private Lifetime highestScope;

    public synchronized void reset() {
        if (Thread.currentThread() == owningThread) state = null;
    }

    public synchronized boolean invalidateIfCurrent(Scope expected) {
        if (Thread.currentThread() != owningThread || state == null
                || !state.scope.same(expected)) return false;
        state = null;
        return true;
    }

    /** One bounded search per physical motion scope. Never call from POST. */
    public synchronized Plan prepare(Frame frame, List<SupportFace> options) {
        Plan hold = hold(frame);
        if (Thread.currentThread() != owningThread) return hold;
        // Late invalid callbacks cannot revoke a newer PRE in the same scope.
        if (frame != null && state != null && state.scope.same(frame.scope)
                && frame.sequence < state.lastSequence) return hold;
        if (!valid(frame)) { invalidateIfCurrent(frame == null ? null : frame.scope); return hold; }

        int order = order(frame.scope);
        if (order < 0) return hold;
        if (order > 0) {
            state = new State(frame.scope);
            highestScope = new Lifetime(frame.scope);
        }
        State active = state;
        if (active == null || !active.scope.same(frame.scope)) return hold;
        if (frame.sequence == active.lastSequence) {
            if (!active.sameIntent(frame)) {
                state = null; // changed target/stack in same sequence: no second plan
                return hold;
            }
            return active.cached;
        }
        if (frame.sequence < active.lastSequence) return hold;

        Plan best = choose(frame, options);
        active.snapshot(frame, best);
        return best;
    }

    private Plan choose(Frame frame, List<SupportFace> options) {
        if (options == null || options.isEmpty() || options.size() > MAX_OPTIONS)
            return hold(frame);

        double[] predicted = predict(frame);
        double eyeDeltaX = predicted[0] - frame.feetX;
        double eyeDeltaY = predictFeetY(frame) - frame.feetY;
        double eyeDeltaZ = predicted[1] - frame.feetZ;
        SupportFace selected = null;
        double bestScore = Double.POSITIVE_INFINITY;
        double chosenYaw = 0, chosenPitch = 0;

        for (SupportFace face : options) {
            if (!validFace(frame, face, eyeDeltaX, eyeDeltaY, eyeDeltaZ)) continue;
            double dx = face.hitX - frame.eyeX, dy = face.hitY - frame.eyeY;
            double dz = face.hitZ - frame.eyeZ;
            double horiz = Math.hypot(dx, dz);
            double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
            double pitch = -Math.toDegrees(Math.atan2(dy, horiz));
            double dist = Math.hypot(horiz, dy);
            double towardsX = frame.destinationX + .5 - predicted[0];
            double towardsZ = frame.destinationZ + .5 - predicted[1];
            double turn = frame.hasRotation
                    ? Math.hypot(wrap(yaw - frame.rotationYaw), pitch - frame.rotationPitch)
                    : Math.abs(wrap(yaw - frame.yaw));
            double score = Math.hypot(towardsX, towardsZ)
                    // All options belong to one destination. Once a useful aim
                    // exists, a shorter mouse path matters more than centimetres
                    // of reach; centre-biased scoring causes diagonal face swings.
                    + (frame.hasRotation ? .01 * turn : .12 * turn / 180)
                    + (frame.hasRotation ? .001 : .025) * dist;
            if (score < bestScore) { // stable first-candidate tie order
                selected = face; bestScore = score;
                chosenYaw = wrap(yaw); chosenPitch = pitch;
            }
        }
        if (selected == null) return hold(frame);
        boolean sneak = frame.eagleEnabled && frame.physicalEdgeRisk;
        return new Plan(true, sneak, selected, chosenYaw, chosenPitch,
                predicted[0], predicted[1], frame.sequence);
    }

    /** Stateless preview. This never consumes a motion sequence or a scope. */
    public static double[] predict(Frame f) {
        if (!validMotion(f)) return new double[]{
                f != null && coordinate(f.feetX) ? f.feetX : 0,
                f != null && coordinate(f.feetZ) ? f.feetZ : 0};
        double forwards = f.forward, sideways = f.strafe;
        double keyboardLength = Math.hypot(forwards, sideways);
        if (keyboardLength > 1) {
            forwards /= keyboardLength; sideways /= keyboardLength;
        }
        double radians = Math.toRadians(f.yaw);
        double keyX = -Math.sin(radians) * forwards + Math.cos(radians) * sideways;
        double keyZ = Math.cos(radians) * forwards + Math.sin(radians) * sideways;
        double dx = (f.velocityX + keyX * .18) * f.predictionTicks;
        double dz = (f.velocityZ + keyZ * .18) * f.predictionTicks;
        double length = Math.hypot(dx, dz);
        if (length > MAX_PREDICTION_BLOCKS) {
            dx = dx / length * MAX_PREDICTION_BLOCKS;
            dz = dz / length * MAX_PREDICTION_BLOCKS;
        }
        return new double[]{f.feetX + dx, f.feetZ + dz};
    }

    /** Falling is measured independently of raw keys; an airborne jump is not a clutch. */
    public static boolean falling(Frame f) {
        return f != null && !f.grounded && Double.isFinite(f.velocityY) && f.velocityY < -.03;
    }

    /** Short, bounded fall preview. It is geometry only, never a collision or placement claim. */
    public static double predictFeetY(Frame f) {
        if (f == null || !coordinate(f.feetY)) return 0;
        if (!validMotion(f) || !falling(f)) return f.feetY;
        double ticks = Math.max(1, f.predictionTicks);
        double displacement = f.velocityY * ticks - .04 * ticks * ticks;
        return f.feetY + Math.max(-1, Math.min(0, displacement));
    }

    private static boolean valid(Frame f) {
        if (f == null || f.scope == null || !f.scope.usable() || !f.authorized
                || f.approvedStack == null || f.sequence < 0
                || f.selectedSlot < 0 || f.selectedSlot > 8
                || f.destinationY < 0 || f.destinationY > 255
                || !coordinate(f.destinationX) || !coordinate(f.destinationZ))
            return false;
        return validMotion(f);
    }

    static boolean validMotion(Frame f) {
        if (f == null || !coordinate(f.feetX) || !coordinate(f.feetY) || !coordinate(f.feetZ)
                || !coordinate(f.eyeX) || !coordinate(f.eyeY) || !coordinate(f.eyeZ)
                || f.eyeY < f.feetY || f.eyeY - f.feetY > 2.4
                || !Double.isFinite(f.yaw) || !Double.isFinite(f.forward)
                || !Double.isFinite(f.strafe) || Math.abs(f.forward) > 1
                || Math.abs(f.strafe) > 1
                || !Double.isFinite(f.velocityX) || !Double.isFinite(f.velocityZ)
                || !Double.isFinite(f.velocityY) || Math.abs(f.velocityY) > 4
                || Math.abs(f.velocityX) > 2 || Math.abs(f.velocityZ) > 2
                || f.hasRotation && (!Double.isFinite(f.rotationYaw)
                    || !Double.isFinite(f.rotationPitch) || Math.abs(f.rotationPitch) > 90)
                || f.hasLane && (!coordinate(f.laneX) || !coordinate(f.laneZ)
                    || !Double.isFinite(f.laneDirectionX) || !Double.isFinite(f.laneDirectionZ)
                    || Math.abs(Math.hypot(f.laneDirectionX, f.laneDirectionZ) - 1) > 1e-5)
                || !Double.isFinite(f.reach) || f.reach < 1 || f.reach > 6
                || !Double.isFinite(f.predictionTicks) || f.predictionTicks < 0
                || f.predictionTicks > MAX_PREDICTION_TICKS)
            return false;
        return true;
    }

    private static boolean validFace(Frame f, SupportFace o, double leadX, double leadY, double leadZ) {
        if (o == null || o.face == null || o.world != f.scope.world
                || o.player != f.scope.player || o.stack != f.approvedStack
                || o.supportState == null || o.slot != f.selectedSlot
                || !o.loaded || !o.collisionClear || !o.acceptedByHostService
                || o.y < 0 || o.y > 255 || !coordinate(o.x) || !coordinate(o.z)
                || o.x + o.face.x != f.destinationX
                || o.y + o.face.y != f.destinationY
                || o.z + o.face.z != f.destinationZ
                || !coordinate(o.hitX) || !coordinate(o.hitY) || !coordinate(o.hitZ))
            return false;
        if (!between(o.hitX, o.x, o.x + 1) || !between(o.hitY, o.y, o.y + 1)
                || !between(o.hitZ, o.z, o.z + 1)) return false;
        if (o.face.x != 0 && Math.abs(o.hitX - (o.x + (o.face.x > 0 ? 1 : 0))) > EPS
                || o.face.y != 0 && Math.abs(o.hitY - (o.y + (o.face.y > 0 ? 1 : 0))) > EPS
                || o.face.z != 0 && Math.abs(o.hitZ - (o.z + (o.face.z > 0 ? 1 : 0))) > EPS)
            return false;
        return distance(o.hitX - f.eyeX, o.hitY - f.eyeY, o.hitZ - f.eyeZ) <= f.reach
                && distance(o.hitX - f.eyeX - leadX, o.hitY - f.eyeY - leadY,
                    o.hitZ - f.eyeZ - leadZ) <= f.reach;
    }

    private int order(Scope token) {
        if (highestScope == null) return 1;
        if (token.owner != highestScope.owner.get()) return -1;
        int generation = Long.compare(token.acquisitionGeneration,
                highestScope.acquisitionGeneration);
        if (generation < 0) return -1;
        if (token.connection != highestScope.connection.get())
            return generation > 0 ? 1 : -1;
        // New acquisitions cannot move one connection's epoch backwards.
        if (token.connectionEpoch < highestScope.connectionEpoch) return -1;
        if (generation > 0) return 1;
        if (token.world != highestScope.world.get() || token.player != highestScope.player.get())
            return -1;
        return Long.compare(token.connectionEpoch, highestScope.connectionEpoch);
    }

    private static boolean coordinate(double d) {
        return Double.isFinite(d) && Math.abs(d) <= 30_000_000;
    }
    private static boolean between(double a, double min, double max) {
        return a >= min - EPS && a <= max + EPS;
    }
    private static double distance(double x, double y, double z) {
        return Math.hypot(Math.hypot(x, z), y);
    }
    private static double wrap(double a) {
        a %= 360;
        if (a >= 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }
    private static Plan hold(Frame f) {
        return new Plan(false, false, null, 0, 0,
                f != null && Double.isFinite(f.feetX) ? f.feetX : 0,
                f != null && Double.isFinite(f.feetZ) ? f.feetZ : 0,
                f == null ? -1 : f.sequence);
    }
}
