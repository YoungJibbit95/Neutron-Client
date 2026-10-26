package me.youngjibbit.core.combat;

/** Movement basis only; never requests rotations or changes camera/packet angles.
 * SIMPLE/PRECISE preserve camera-space intent. ROTATION_BOUND keeps the original inputs
 * and uses the confirmed module yaw at the consumer, including the sprint-jump impulse. */
public final class MoveFixController {
    public enum Mode {
        OFF("Aus"), SIMPLE("Einfach"), PRECISE("Präzise"), ROTATION_BOUND("Rotationsgebunden");
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
        if (mode == Mode.OFF || mode == Mode.ROTATION_BOUND || !Float.isFinite(strafe) || !Float.isFinite(forward)
                || !Float.isFinite(inputYaw) || !Float.isFinite(effectiveYaw)) return new Input(strafe, forward);
        double magnitude = Math.hypot(strafe, forward);
        if (magnitude == 0) return new Input(0, 0);
        double delta = Math.toRadians(wrap(inputYaw - effectiveYaw));
        double s = strafe * Math.cos(delta) - forward * Math.sin(delta);
        double f = strafe * Math.sin(delta) + forward * Math.cos(delta);
        if (mode == Mode.PRECISE) return new Input((float) s, (float) f);

        double best = -Double.MAX_VALUE;
        int[] direction = DIRECTIONS[0];
        for (int[] candidate : DIRECTIONS) {
            double score = (s * candidate[0] + f * candidate[1]) / Math.hypot(candidate[0], candidate[1]);
            if (score > best + 1.0e-9) { best = score; direction = candidate; }
        }
        // Retain even sub-unit sneak/item input strength. Vanilla applies its one speed cap later.
        double scale = magnitude / Math.hypot(direction[0], direction[1]);
        return new Input((float) (direction[0] * scale), (float) (direction[1] * scale));
    }

    /** Vanilla's sprint impulse is camera-forward, even with diagonal keys. Keep that basis. */
    public static float jumpYaw(float cameraYaw, float effectiveYaw, Mode mode) {
        if (mode == Mode.ROTATION_BOUND) return effectiveYaw;
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
