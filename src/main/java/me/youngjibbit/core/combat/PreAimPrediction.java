package me.youngjibbit.core.combat;

/** Horizontal relative-motion lead, in game ticks, bounded like Neutron's existing Arsenic aim. */
public final class PreAimPrediction {
    private PreAimPrediction() { }

    public static double[] offset(double targetX, double targetZ, double selfX, double selfZ, float ticks) {
        if (!Float.isFinite(ticks) || ticks <= 0) return new double[]{0, 0};
        double duration = Math.min(3, ticks);
        double x = (delta(targetX) - delta(selfX)) * duration;
        double z = (delta(targetZ) - delta(selfZ)) * duration;
        double length = Math.hypot(x, z);
        if (length > 1.5) { x *= 1.5 / length; z *= 1.5 / length; }
        return new double[]{x, z};
    }

    private static double delta(double value) { return Double.isFinite(value) && Math.abs(value) <= 2 ? value : 0; }
}
