package me.youngjibbit.core.combat;

import java.util.Random;

/** Limits acceleration on acquisition and brakes as the remaining angular distance shrinks. */
public final class ArsenicMotionCurve {
    private final Random random;
    private double phase, rate;
    private float yawVelocity, pitchVelocity;

    public ArsenicMotionCurve(Random random) {
        this.random = random;
        reset();
    }

    public void reset() {
        yawVelocity = pitchVelocity = 0;
        phase = random.nextDouble() * Math.PI * 2;
        rate = .14 + random.nextDouble() * .15;
    }

    public float[] advance(float yawError, float pitchError, float yawLimit, float pitchLimit) {
        // One sample per distinct client movement step, shared by both axes.
        phase += rate;
        float variation = (float) (1 + .14 * Math.sin(phase));
        yawVelocity = step(yawVelocity, yawError, yawLimit, 2.0f * variation);
        pitchVelocity = step(pitchVelocity, pitchError, pitchLimit, 1.15f * variation);
        return new float[]{yawVelocity, pitchVelocity};
    }

    private static float step(float velocity, float remaining, float cap, float acceleration) {
        if (remaining == 0 || cap <= 0) return 0;
        float distance = Math.abs(remaining);
        float forward = Math.max(0, velocity * Math.signum(remaining));
        // v^2 = 2ad bounds the stopping distance near the end of the turn.
        float brakeLimit = (float) Math.sqrt(2 * acceleration * distance);
        float speed = Math.min(Math.min(distance, cap), Math.min(forward + acceleration, brakeLimit));
        return Math.copySign(speed, remaining);
    }
}
