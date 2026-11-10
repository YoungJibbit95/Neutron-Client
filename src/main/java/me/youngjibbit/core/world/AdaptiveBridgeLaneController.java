package me.youngjibbit.core.world;

/**
 * Small camera-frame steering correction for a straight Adaptive Bridge route.
 * The host supplies fresh, uncorrected input and owns all lifetime/scene checks.
 * No velocity, key binding, camera, world or packet state is changed here.
 */
public final class AdaptiveBridgeLaneController {
    public static final double DEADBAND = .025;
    public static final double MAX_STEERING = .18;
    private static final double MIN_INPUT = 1.0e-5;
    private static final double DIRECTION_COSINE = Math.cos(Math.toRadians(1));
    private static final double MAX_STEP_DISTANCE = 2;

    /** Immutable route captured from raw camera-relative intention, never aim yaw. */
    public static final class Lane {
        public final double anchorX, anchorZ, directionX, directionZ;

        private Lane(double x, double z, double directionX, double directionZ) {
            anchorX = x; anchorZ = z;
            this.directionX = directionX; this.directionZ = directionZ;
        }

        public double projectX(double x, double z) {
            return anchorX + along(x, z) * directionX;
        }

        public double projectZ(double x, double z) {
            return anchorZ + along(x, z) * directionZ;
        }

        private double along(double x, double z) {
            return (x - anchorX) * directionX + (z - anchorZ) * directionZ;
        }
    }

    public static final class Input {
        public final float forward, strafe;
        public final Lane lane;
        public final double crossTrackError;

        private Input(float forward, float strafe, Lane lane, double error) {
            this.forward = forward; this.strafe = strafe;
            this.lane = lane; crossTrackError = error;
        }
    }

    private Lane lane;
    private double lastX, lastZ;

    /** Read-only PRE view. Planning must not run the fresh-input correction again. */
    public Lane current() { return lane; }

    public void reset() { lane = null; }

    /**
     * Invoke once per fresh input sample, after remembering the raw direction and
     * before support probing. A stopped/off/invalid sample cannot create movement.
     * The result preserves input vector length, including vanilla slowdowns; its
     * axes are deliberately continuous and must not be rounded by MoveFix again.
     */
    public Input update(double x, double z, double velocityX, double velocityZ,
                        float cameraYaw, float forward, float strafe, boolean enabled) {
        double magnitude = Math.hypot(forward, strafe);
        if (!enabled || !finite(x, z, velocityX, velocityZ, cameraYaw, forward, strafe)
                || Math.abs(x) > 30000000 || Math.abs(z) > 30000000
                || magnitude < MIN_INPUT || magnitude > 2) {
            reset();
            return new Input(forward, strafe, null, 0);
        }

        double yaw = Math.toRadians(Math.IEEEremainder(cameraYaw, 360));
        double sine = Math.sin(yaw), cosine = Math.cos(yaw);
        double intendedX = (strafe * cosine - forward * sine) / magnitude;
        double intendedZ = (forward * cosine + strafe * sine) / magnitude;
        if (lane == null || intendedX * lane.directionX + intendedZ * lane.directionZ < DIRECTION_COSINE
                || Math.hypot(x - lastX, z - lastZ) > MAX_STEP_DISTANCE) {
            lane = new Lane(x, z, intendedX, intendedZ);
        }
        lastX = x; lastZ = z;

        double normalX = -lane.directionZ, normalZ = lane.directionX;
        double error = (x - lane.anchorX) * normalX + (z - lane.anchorZ) * normalZ;
        double displacement = Math.copySign(Math.max(0, Math.abs(error) - DEADBAND), error);
        double lateralVelocity = velocityX * normalX + velocityZ * normalZ;
        // Position restores the route; velocity damping arrests sideways drift
        // before it crosses the route and would demand the opposite correction.
        double steering = Math.max(-MAX_STEERING,
                Math.min(MAX_STEERING, -1.4 * displacement - 1.8 * lateralVelocity));
        if (Math.abs(steering) < 1.0e-9) return new Input(forward, strafe, lane, error);

        double worldX = intendedX + normalX * steering;
        double worldZ = intendedZ + normalZ * steering;
        double scale = magnitude / Math.hypot(worldX, worldZ);
        worldX *= scale; worldZ *= scale;
        float correctedStrafe = (float) (worldX * cosine + worldZ * sine);
        float correctedForward = (float) (-worldX * sine + worldZ * cosine);
        return new Input(correctedForward, correctedStrafe, lane, error);
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
