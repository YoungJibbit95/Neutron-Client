package me.youngjibbit.core.combat.ai;

/**
 * Independent 40-value, target-relative physical feature schema (version 1).
 * No names, accounts, server addresses, chat, raw keypresses or mouse events.
 * Only the owning game-thread adapter may supply a validated observation.
 */
public final class NeutronAiFeatures {
    public static final int WIDTH = NeutronAiCheckpoint.WIDTH;
    private NeutronAiFeatures() { }

    /** Mutable input DTO, used only for the duration of a game-thread call. */
    public static final class Observation {
        public double eyeX, eyeY, eyeZ;
        public double targetX, targetY, targetZ;
        public double yaw, pitch;
        public double playerVx, playerVy, playerVz;
        public double targetVx, targetVy, targetVz;
        public double targetWidth = .6, targetHeight = 1.8, targetDepth = .6, reach = 3;
        public boolean visible, targetAlive, playerGrounded, targetGrounded;
    }

    public static boolean valid(Observation o) {
        return o != null && o.visible && o.targetAlive
                && coordinate(o.eyeX) && coordinate(o.eyeY) && coordinate(o.eyeZ)
                && coordinate(o.targetX) && coordinate(o.targetY) && coordinate(o.targetZ)
                && Double.isFinite(o.yaw) && Double.isFinite(o.pitch)
                && o.pitch >= -90 && o.pitch <= 90
                && velocity(o.playerVx) && velocity(o.playerVy) && velocity(o.playerVz)
                && velocity(o.targetVx) && velocity(o.targetVy) && velocity(o.targetVz)
                && dimension(o.targetWidth) && dimension(o.targetHeight)
                && dimension(o.targetDepth)
                && Double.isFinite(o.reach) && o.reach > 0 && o.reach <= 8
                && Math.hypot(Math.hypot(o.targetX - o.eyeX, o.targetZ - o.eyeZ),
                    o.targetY - o.eyeY) > 1.0e-6;
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 30_000_000;
    }
    private static boolean velocity(double value) {
        return Double.isFinite(value) && Math.abs(value) <= 4;
    }
    private static boolean dimension(double value) {
        return Double.isFinite(value) && value > 0 && value <= 4;
    }

    static double wrap(double a) {
        a %= 360;
        if (a <= -180) a += 360;
        if (a > 180) a -= 360;
        return a;
    }

    public static double targetYaw(Observation o) {
        return Math.toDegrees(Math.atan2(o.targetZ - o.eyeZ, o.targetX - o.eyeX)) - 90;
    }

    public static double targetPitch(Observation o) {
        return -Math.toDegrees(Math.atan2(o.targetY - o.eyeY,
                Math.hypot(o.targetX - o.eyeX, o.targetZ - o.eyeZ)));
    }

    /**
     * All values are finite in [-1,1]. Slots 0..39 are frozen for checkpoint v1;
     * history is maintained by NeutronAiCore, not by a render callback.
     */
    static boolean populate(Observation o, long ageNanos, float[] out) {
        if (out == null || out.length != WIDTH || !valid(o) || ageNanos < 0) return false;
        double dx = o.targetX - o.eyeX, dy = o.targetY - o.eyeY, dz = o.targetZ - o.eyeZ;
        double horizontal = Math.hypot(dx, dz), distance = Math.hypot(horizontal, dy);
        double goalYaw = targetYaw(o), goalPitch = targetPitch(o);
        double yawError = wrap(goalYaw - o.yaw), pitchError = goalPitch - o.pitch;
        double rvx = o.targetVx - o.playerVx, rvy = o.targetVy - o.playerVy, rvz = o.targetVz - o.playerVz;
        double yawRadians = Math.toRadians(o.yaw);
        double yawErrorRadians = Math.toRadians(yawError);
        double pitchErrorRadians = Math.toRadians(pitchError);

        out[0] = norm(yawError, 180);
        out[1] = norm(pitchError, 90);
        out[2] = normPositive(distance, 8);
        out[3] = normPositive(horizontal, 8);
        out[4] = norm(dx, 8); out[5] = norm(dy, 8); out[6] = norm(dz, 8);
        out[7] = norm(rvx, 4); out[8] = norm(rvy, 4); out[9] = norm(rvz, 4);
        out[10] = norm(wrap(o.yaw), 180);
        out[11] = norm(o.pitch, 90);
        out[12] = norm(wrap(goalYaw), 180);
        out[13] = norm(goalPitch, 90);
        out[14] = (float) Math.sin(yawRadians);
        out[15] = (float) Math.cos(yawRadians);
        out[16] = (float) Math.sin(Math.toRadians(goalYaw));
        out[17] = (float) Math.cos(Math.toRadians(goalYaw));
        out[18] = (float) Math.sin(yawErrorRadians);
        out[19] = (float) Math.cos(yawErrorRadians);
        out[20] = norm(o.playerVx, 4); out[21] = norm(o.playerVy, 4); out[22] = norm(o.playerVz, 4);
        out[23] = norm(o.targetVx, 4); out[24] = norm(o.targetVy, 4); out[25] = norm(o.targetVz, 4);
        out[26] = norm(rvx, 4); out[27] = norm(rvy, 4); out[28] = norm(rvz, 4);
        out[29] = normPositive(distance / o.reach, 3);
        out[30] = normPositive(o.targetWidth, 4);
        out[31] = normPositive(o.targetHeight, 4);
        out[32] = normPositive(o.targetDepth, 4);
        out[33] = norm((dx * rvx + dy * rvy + dz * rvz) / distance, 4);
        out[34] = (float) Math.sin(pitchErrorRadians);
        out[35] = normPositive(ageNanos * 1.0e-9, 4);
        out[36] = 1; // validated target-visible and target-alive gate
        out[37] = o.playerGrounded ? 1 : 0;
        out[38] = o.targetGrounded ? 1 : 0;
        out[39] = norm((dx * rvz - dz * rvx) / horizontalSafe(horizontal), 4);

        for (float value : out)
            if (!Float.isFinite(value) || value < -1.000001f || value > 1.000001f)
                return false;
        return true;
    }

    private static double horizontalSafe(double horizontal) {
        return Math.max(.01, horizontal);
    }
    private static float norm(double value, double scale) {
        return (float) Math.max(-1, Math.min(1, value / scale));
    }
    private static float normPositive(double value, double scale) {
        return (float) Math.max(0, Math.min(1, value / scale));
    }
}
