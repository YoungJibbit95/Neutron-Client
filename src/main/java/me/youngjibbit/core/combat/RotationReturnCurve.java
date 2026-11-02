package me.youngjibbit.core.combat;

/** A minimum-jerk return with zero velocity/acceleration at both endpoints. */
public final class RotationReturnCurve {
    private RotationReturnCurve() { }
    public static float progress(float value) {
        float t = Math.max(0, Math.min(1, value));
        return t * t * t * (10 + t * (-15 + 6 * t));
    }
    public static float angle(float from, float to, float progress) {
        return from + MoveFixController.wrap(to - from) * progress;
    }
}
