package me.youngjibbit.core.combat;

import net.minecraft.util.MathHelper;

/** Pure input correction; never changes velocity, packets, camera or body rotations.
 * SIMPLE/PRECISE retain their existing camera-space contracts.
 * LEGACY_ROUNDED maps normal 1.8.9 keyboard inputs into rounded module-yaw inputs.
 * ROTATION_BOUND and LEGACY_STRICT use original keys relative to module yaw. */
public final class MoveFixController {
    public enum Mode {
        OFF("Aus"), SIMPLE("Einfach"), PRECISE("Präzise"), ROTATION_BOUND("Rotationsgebunden"),
        LEGACY_STRICT("Legacy Strict"), LEGACY_ROUNDED("Legacy Rounded");
        public final String label;
        Mode(String label) { this.label = label; }
        public static Mode fromSetting(String value) {
            for (Mode mode : values()) if (mode.label.equalsIgnoreCase(value)) return mode;
            throw new IllegalArgumentException("Unknown movement correction mode.");
        }
    }

    public static final class Input {
        public final float strafe, forward;
        Input(float strafe, float forward) { this.strafe = strafe; this.forward = forward; }
    }

    private static final int[][] DIRECTIONS = {
            {0, 1}, {1, 1}, {1, 0}, {1, -1}, {0, -1}, {-1, -1}, {-1, 0}, {-1, 1}
    };

    private MoveFixController() { }

    public static Input correct(float strafe, float forward, float inputYaw, float effectiveYaw, Mode mode) {
        if (mode == Mode.OFF || mode == Mode.ROTATION_BOUND || mode == Mode.LEGACY_STRICT
                || !Float.isFinite(strafe) || !Float.isFinite(forward)
                || !Float.isFinite(inputYaw) || !Float.isFinite(effectiveYaw)) return new Input(strafe, forward);
        if (strafe == 0 && forward == 0) return new Input(0, 0);
        // Wrap before subtraction to avoid overflowing opposite extreme finite float yaws.
        float yawDifference = wrap(wrap(inputYaw) - wrap(effectiveYaw));
        if (mode == Mode.LEGACY_ROUNDED) return legacyRounded(strafe, forward, yawDifference);

        double magnitude = Math.hypot(strafe, forward);
        double delta = Math.toRadians(yawDifference);
        double s = strafe * Math.cos(delta) - forward * Math.sin(delta);
        double f = strafe * Math.sin(delta) + forward * Math.cos(delta);
        if (mode == Mode.PRECISE) return new Input((float) s, (float) f);

        double best = -Double.MAX_VALUE;
        int[] direction = DIRECTIONS[0];
        for (int[] candidate : DIRECTIONS) {
            double score = (s * candidate[0] + f * candidate[1]) / Math.hypot(candidate[0], candidate[1]);
            if (score > best + 1.0e-9) { best = score; direction = candidate; }
        }
        // Keep the pre-existing SIMPLE semantics; this is not the rounded-key contract.
        double scale = magnitude / Math.hypot(direction[0], direction[1]);
        return new Input((float) (direction[0] * scale), (float) (direction[1] * scale));
    }

    /** LiquidBounce Legacy non-strict-style correction after vanilla's 0.98 and uniform
     * sneak/item slowdowns. Reconstruct discrete key signs, rotate/round, restore input
     * strength, and let moveFlying apply its single vanilla diagonal speed cap.
     * Unsupported (>1) axes are left unchanged rather than fabricating key states. */
    private static Input legacyRounded(float strafe, float forward, float yawDifference) {
        if (Math.abs(strafe) > 1f || Math.abs(forward) > 1f) return new Input(strafe, forward);
        float radians = (float) Math.toRadians(yawDifference);
        float cosine = MathHelper.cos(radians);
        float sine = MathHelper.sin(radians);
        float keyForward = Math.signum(forward), keyStrafe = Math.signum(strafe);
        // Kotlin round uses ties-to-even, as does Math.rint.
        int roundedForward = (int) Math.rint(keyForward * cosine + keyStrafe * sine);
        int roundedStrafe = (int) Math.rint(keyStrafe * cosine - keyForward * sine);
        float strength = forward != 0f ? Math.abs(forward) : Math.abs(strafe);
        return new Input(roundedStrafe * strength, roundedForward * strength);
    }

    /** Chooses a yaw basis for the existing jump consumer; does not simulate jump impulses. */
    public static float jumpYaw(float cameraYaw, float effectiveYaw, Mode mode) {
        if (mode == Mode.ROTATION_BOUND || mode == Mode.LEGACY_STRICT || mode == Mode.LEGACY_ROUNDED)
            return effectiveYaw;
        if (mode != Mode.SIMPLE) return cameraYaw;
        Input input = correct(0, 1, cameraYaw, effectiveYaw, mode);
        return effectiveYaw - (float) Math.toDegrees(Math.atan2(input.strafe, input.forward));
    }

    public static float wrap(float degrees) {
        float angle = degrees % 360;
        if (angle >= 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}
