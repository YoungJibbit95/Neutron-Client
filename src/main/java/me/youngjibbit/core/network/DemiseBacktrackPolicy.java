package me.youngjibbit.core.network;

/** Hitbox-distance gate adapted from Demise's only-needed / moving-away selection. */
public final class DemiseBacktrackPolicy {
    private DemiseBacktrackPolicy() { }
    public static boolean shouldHold(double received, double visible, double minimum, double maximum,
                                     double reach, boolean onlyNeeded, boolean extraCheck,
                                     boolean movingAway, boolean recentOutOfRange) {
        if (!Double.isFinite(received) || !Double.isFinite(visible) || received < 0 || visible < 0) return false;
        if (extraCheck && !movingAway) return false;
        if (onlyNeeded) return (received > reach || recentOutOfRange)
                && received < reach + 1.5 && visible <= reach;
        return received > Math.min(minimum, maximum) && received < Math.max(minimum, maximum);
    }
}
