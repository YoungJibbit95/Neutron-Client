package me.youngjibbit.core.combat;

/**
 * Vanilla 1.8.9 mouse-angle quantum. A rotation delta generated from integer
 * mouse counts is a whole number of these increments (before pitch clamping).
 *
 * Rounding is relative to the previously observed rotation, not absolute 0°:
 * two legitimate camera/packet angles need not be multiples of the quantum
 * when measured against the world's zero-angle origin.
 */
public final class RotationSensitivity {
    private RotationSensitivity() { }

    public static float mouseStep(float mouseSensitivity) {
        float scaled = mouseSensitivity * 0.6F + 0.2F;
        return scaled * scaled * scaled * 1.2F;
    }

    /**
     * Select the nearest representable angle on the previous-angle mouse grid.
     * Even a tiny desired delta must resolve to zero or a whole mouse count;
     * emitting the unrounded remainder contradicts sensitivity quantization.
     */
    public static float snap(float wanted, float previous, float mouseSensitivity) {
        if (!Float.isFinite(wanted) || !Float.isFinite(previous)
                || !Float.isFinite(mouseSensitivity)) return wanted;
        float quantum = mouseStep(mouseSensitivity);
        if (!Float.isFinite(quantum) || quantum <= 0F) return wanted;
        double mouseCounts = Math.rint(((double) wanted - previous) / quantum);
        if (!Double.isFinite(mouseCounts)) return wanted;
        return (float) (previous + mouseCounts * quantum);
    }
}
