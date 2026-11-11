package me.youngjibbit.utils.render;

import me.youngjibbit.gui.HudSpring;

/** Per-target cosmetic inertia. Positions used for ray markers never pass through these springs. */
public final class TargetEspMotion {
    private final HudSpring xSpring = new HudSpring(), zSpring = new HudSpring();
    private Object world, target;
    private long previous;
    private double x, z;
    private float frameTime = Float.NaN;
    private float leanX, leanZ;

    public void sample(Object world, Object target, long now, float visualTime,
                       double x, double z, boolean animate, float strength) {
        if (world == null || target == null || !Double.isFinite(x) || !Double.isFinite(z)
                || !Float.isFinite(visualTime)) { reset(); return; }
        boolean changed = this.world != world || this.target != target;
        double dt = previous == 0 ? 0 : (now - previous) / 1e9;
        if (changed || dt < 0 || dt > .25 || Math.abs(x - this.x) + Math.abs(z - this.z) > 4) {
            reset(); this.world = world; this.target = target;
            this.x = x; this.z = z; previous = now; frameTime = visualTime;
            xSpring.snap(0); zSpring.snap(0); return;
        }
        if (!animate) {
            leanX = xSpring.snap(0); leanZ = zSpring.snap(0);
            this.x = x; this.z = z; previous = now; frameTime = visualTime; return;
        }
        // Both anaglyph eyes share UiEffects.time; do not step twice for one frame.
        if (visualTime == frameTime) return;
        float amount = finiteClamp(strength, 0, 1), seconds = (float)Math.min(.05, Math.max(0, dt));
        float dx = dt > .000001 ? (float)(-(x - this.x) / dt * .025) : 0;
        float dz = dt > .000001 ? (float)(-(z - this.z) / dt * .025) : 0;
        leanX = xSpring.step(finiteClamp(dx, -.24f, .24f) * amount, seconds, true, false, "Calm");
        leanZ = zSpring.step(finiteClamp(dz, -.24f, .24f) * amount, seconds, true, false, "Calm");
        this.x = x; this.z = z; previous = now; frameTime = visualTime;
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
        world = target = null; previous = 0; x = z = 0; frameTime = Float.NaN; leanX = leanZ = 0;
        xSpring.reset(); zSpring.reset();
    }
    private static float finiteClamp(float value, float min, float max) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
