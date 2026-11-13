package me.youngjibbit.utils.render;

/** Presentation-only position filter. Never writes an entity, ray or rotation. */
public final class TargetEspPositionMotion {
    private Object scene, subject;
    private long previous;
    private long frameSequence = Long.MIN_VALUE;
    private double x, y, z, sampleX, sampleY, sampleZ;
    private boolean ready;

    public boolean sample(Object scene, Object subject, long now, long frameSequence,
                          double x, double y, double z, boolean animate, float smoothing) {
        if (scene == null || subject == null || !finite(x, y, z)) {
            reset(); return false;
        }
        double dt = ready ? (now - previous) / 1e9 : 0;
        double dx = x - sampleX, dy = y - sampleY, dz = z - sampleZ;
        float amount = Float.isFinite(smoothing) ? Math.max(0, Math.min(1, smoothing)) : 0;
        if (!ready || this.scene != scene || this.subject != subject || dt < 0 || dt > .25
                || dx * dx + dy * dy + dz * dz > 16 || !animate || amount == 0) {
            this.scene = scene; this.subject = subject; ready = true;
            this.x = x; this.y = y; this.z = z;
        } else {
            // Anaglyph eyes share a game-render frame. The decoration clock may
            // stand still while F1 hides the HUD, so it cannot identify frames.
            if (frameSequence == this.frameSequence) return true;
            // Exponential damping is independent of FPS, including frames slower
            // than 20 Hz. A longer pause resets instead of replaying old motion.
            double alpha = -Math.expm1(-dt / (.015 + .065 * amount));
            this.x += (x - this.x) * alpha;
            this.y += (y - this.y) * alpha;
            this.z += (z - this.z) * alpha;
        }
        sampleX = x; sampleY = y; sampleZ = z;
        previous = now; this.frameSequence = frameSequence;
        return true;
    }

    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public void reset() {
        scene = subject = null; previous = 0; frameSequence = Long.MIN_VALUE;
        x = y = z = sampleX = sampleY = sampleZ = 0; ready = false;
    }
    private static boolean finite(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
