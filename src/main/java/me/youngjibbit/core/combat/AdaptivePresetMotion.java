package me.youngjibbit.core.combat;

import java.util.Random;

/**
 * Pure Java 8 Adaptive-only settings mathematics. These values never own
 * rotation packets, attacks, hit permissions or an entity/scene lifecycle.
 */
public final class AdaptivePresetMotion {
    private AdaptivePresetMotion() { }

    public enum Speed {
        CALM(.68, .72), BALANCED(1, 1), SWIFT(1.24, 1.22);
        final double speed, acceleration;
        Speed(double speed, double acceleration) {
            this.speed = speed; this.acceleration = acceleration;
        }
    }

    public enum Hitpoint {
        STABLE(0, 0), ORGANIC(.09, .33), DYNAMIC(.20, .72);
        final double extent, cyclesPerSecond;
        Hitpoint(double extent, double cyclesPerSecond) {
            this.extent = extent; this.cyclesPerSecond = cyclesPerSecond;
        }
    }

    public enum AimPoint { STRAIGHT, NEAREST, LEGACY }

    /**
     * Demise's Straight anchor keeps horizontal box centre and a useful torso-to-eye
     * height. Nearest minimizes the correction distance. Neither expands the hitbox.
     */
    public static double[] aimPoint(AimPoint style,double eyeX,double eyeY,double eyeZ,
            double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        if(style==null || style==AimPoint.LEGACY || !Double.isFinite(eyeX) || !Double.isFinite(eyeY)
                || !Double.isFinite(eyeZ) || !finiteBox(minX,maxX) || !finiteBox(minY,maxY)
                || !finiteBox(minZ,maxZ))return null;
        double ix=(maxX-minX)*.001,iy=(maxY-minY)*.001,iz=(maxZ-minZ)*.001;
        if(style==AimPoint.STRAIGHT)return new double[]{minX+(maxX-minX)*.5,
                clamp(eyeY,minY+(maxY-minY)*.45,maxY-iy),minZ+(maxZ-minZ)*.5};
        return new double[]{clamp(eyeX,minX+ix,maxX-ix),clamp(eyeY,minY+iy,maxY-iy),
                clamp(eyeZ,minZ+iz,maxZ-iz)};
    }

    /** A snapshot; changing it requires a fresh rotation acquisition. */
    public static final class Configuration {
        public final Speed speed;
        public final Hitpoint hitpoint;
        public final double variation, yawTicks, pitchTicks;

        public Configuration(Speed speed, Hitpoint hitpoint, double variation,
                             double yawTicks, double pitchTicks) {
            if (speed == null || hitpoint == null || !Double.isFinite(variation)
                    || variation < 0 || variation > 1 || !positive(yawTicks)
                    || !positive(pitchTicks)) {
                throw new IllegalArgumentException("invalid Adaptive preset");
            }
            this.speed = speed; this.hitpoint = hitpoint;
            this.variation = variation;
            this.yawTicks = yawTicks; this.pitchTicks = pitchTicks;
        }

        public boolean same(Configuration other) {
            return other != null && speed == other.speed && hitpoint == other.hitpoint
                    && Double.compare(variation, other.variation) == 0
                    && Double.compare(yawTicks, other.yawTicks) == 0
                    && Double.compare(pitchTicks, other.pitchTicks) == 0;
        }

        public AdaptiveMotionController.Limits limits() {
            // Original sliders express degrees/50ms. Same engine for all presets.
            double yaw = clamp(yawTicks * 20 * speed.speed, 20, 720);
            double pitch = clamp(pitchTicks * 20 * speed.speed, 20, 720);
            double accel = clamp(Math.max(yaw, pitch) * 6 * speed.acceleration, 300, 4200);
            // At Balanced + strength .5, keep Phase 2's exact variation/curvature.
            return new AdaptiveMotionController.Limits(yaw, pitch, accel,
                    clamp(accel * 20, 9000, 84000), .28 * variation, .24 * variation);
        }
    }

    /**
     * Immutable phases per acquisition; sample at motion time, not render count.
     * New acquisition starts at its unchanged base point (fade-in = 0).
     */
    public static final class HitpointTrajectory {
        private final Hitpoint style;
        private final double[] phases = new double[6];

        public HitpointTrajectory(long seed, Hitpoint style) {
            if (style == null) throw new IllegalArgumentException("hitpoint style");
            this.style = style;
            Random random = new Random(seed);
            for (int i = 0; i < phases.length; i++)
                phases[i] = random.nextDouble() * Math.PI * 2;
        }

        /**
         * @return valid in-box point, or null when input bounds/time/point is invalid.
         * Position coordinates may change with a moving box; the noise is continuous
         * in normalized local coordinates and does not draw per-frame randomness.
         */
        public double[] point(double baseX, double baseY, double baseZ,
                              double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ, double seconds) {
            if (!Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(baseX)
                    || !Double.isFinite(baseY) || !Double.isFinite(baseZ)
                    || !finiteBox(minX, maxX) || !finiteBox(minY, maxY)
                    || !finiteBox(minZ, maxZ)) return null;
            double fade = 1 - Math.exp(-seconds * 4);
            return new double[] {
                axis(baseX, minX, maxX, seconds, fade, 0),
                axis(baseY, minY, maxY, seconds, fade, 1),
                axis(baseZ, minZ, maxZ, seconds, fade, 2)
            };
        }

        private double axis(double base, double min, double max, double time, double fade, int i) {
            double width = max - min;
            double angle = time * style.cyclesPerSecond * 2 * Math.PI;
            double primary = Math.sin(angle + phases[i]) - Math.sin(phases[i]);
            double secondary = Math.sin(angle * 1.71 + phases[i + 3]) - Math.sin(phases[i + 3]);
            double offset = (.6 * primary + .4 * secondary) * .5 * style.extent * fade;
            double inset = width * 0.001;
            return clamp(base + width * offset, min + inset, max - inset);
        }
    }

    private static boolean positive(double d) { return Double.isFinite(d) && d > 0; }
    private static boolean finiteBox(double lo, double hi) {
        return Double.isFinite(lo) && Double.isFinite(hi)
                && hi > lo && Double.isFinite(hi - lo);
    }
    private static double clamp(double d, double lo, double hi) {
        return Math.max(lo, Math.min(hi, d));
    }
}
