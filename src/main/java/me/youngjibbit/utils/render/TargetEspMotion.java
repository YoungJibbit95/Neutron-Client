package me.youngjibbit.utils.render;

import me.youngjibbit.gui.HudSpring;

/** Per-target cosmetic inertia. Positions used for ray markers never pass through these springs. */
public final class TargetEspMotion {
    private final HudSpring xSpring = new HudSpring(), zSpring = new HudSpring();
    private Object world, target;
    private long previous;
    private double x, z;
    private long frameSequence = Long.MIN_VALUE;
    private float leanX, leanZ;

    public void sample(Object world, Object target, long now, long frameSequence,
                       double x, double z, boolean animate, float strength) {
        if (world == null || target == null || !Double.isFinite(x) || !Double.isFinite(z)) { reset(); return; }
        boolean changed = this.world != world || this.target != target;
        double dt = changed ? 0 : (now - previous) / 1e9;
        if (changed || dt < 0 || dt > .25 || Math.abs(x - this.x) + Math.abs(z - this.z) > 4) {
            reset(); this.world = world; this.target = target;
            this.x = x; this.z = z; previous = now; this.frameSequence = frameSequence;
            xSpring.snap(0); zSpring.snap(0); return;
        }
        if (!animate) {
            leanX = xSpring.snap(0); leanZ = zSpring.snap(0);
            this.x = x; this.z = z; previous = now; this.frameSequence = frameSequence; return;
        }
        // The HUD decoration clock stops under F1. Use the game-render frame so
        // inertia keeps following the target while both anaglyph eyes agree.
        if (frameSequence == this.frameSequence) return;
        float amount = finiteClamp(strength, 0, 1);
        float dx = dt > .000001 ? (float)(-(x - this.x) / dt * .025) : 0;
        float dz = dt > .000001 ? (float)(-(z - this.z) / dt * .025) : 0;
        float goalX = finiteClamp(dx, -.24f, .24f) * amount;
        float goalZ = finiteClamp(dz, -.24f, .24f) * amount;
        // HudSpring limits a step to 50 ms. Consume the complete accepted frame
        // delta in at most five bounded steps instead of slowing down below 20 FPS.
        int steps = Math.max(1, (int)Math.ceil(dt / .05));
        float seconds = (float)(dt / steps);
        for (int i = 0; i < steps; i++) {
            leanX = xSpring.step(goalX, seconds, true, false, "Calm");
            leanZ = zSpring.step(goalZ, seconds, true, false, "Calm");
        }
        this.x = x; this.z = z; previous = now; this.frameSequence = frameSequence;
    }

    public static float lift(float time, float speed, boolean animate) {
        if (!animate || !Float.isFinite(time)) return .5f;
        return .14f + .72f * (.5f - .5f * (float)Math.cos(time * finiteClamp(speed, .1f, 2) * 2.1));
    }
    public static float direction(float time, float speed, boolean animate) {
        return animate && Float.isFinite(time) ? (float)Math.sin(time * finiteClamp(speed, .1f, 2) * 2.1) : 0;
    }
    public float leanX() { return leanX; }
    public float leanZ() { return leanZ; }
    public void reset() {
        world = target = null; previous = 0; x = z = 0; frameSequence = Long.MIN_VALUE; leanX = leanZ = 0;
        xSpring.reset(); zSpring.reset();
    }
    private static float finiteClamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
