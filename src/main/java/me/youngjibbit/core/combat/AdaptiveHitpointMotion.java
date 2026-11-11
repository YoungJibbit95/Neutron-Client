package me.youngjibbit.core.combat;

import me.youngjibbit.utils.math.FastNoiseLite;

import java.util.Random;

/**
 * Acquisition-local point movement in the real hitbox. This class knows no world,
 * entity, ray, input or action. Its result is only a candidate for the existing ray checks.
 */
public final class AdaptiveHitpointMotion {
    private static final double MAX_STEP_SECONDS = .075;

    public enum Algorithm {
        OPEN_SIMPLEX_2S(FastNoiseLite.NoiseType.OpenSimplex2S),
        PERLIN(FastNoiseLite.NoiseType.Perlin),
        VALUE_CUBIC(FastNoiseLite.NoiseType.ValueCubic);

        final FastNoiseLite.NoiseType type;
        Algorithm(FastNoiseLite.NoiseType type) { this.type = type; }
    }

    public enum Profile { OFF, SUBTLE, BALANCED, STRONG, CUSTOM }

    /** Radius is a fraction of each box half-extent; tempo is noise-coordinate units/second. */
    public static final class Configuration {
        public final Profile profile;
        public final Algorithm algorithm;
        public final double radius, travelSpeed, travelNoise;

        public Configuration(Profile profile, Algorithm algorithm, double radius,
                             double travelSpeed, double travelNoise) {
            if (profile == null || algorithm == null || !unit(radius)
                    || !Double.isFinite(travelSpeed) || travelSpeed <= 0 || travelSpeed > 3
                    || !unit(travelNoise)) throw new IllegalArgumentException("invalid in-box hitpoint controls");
            this.profile = profile;
            this.algorithm = algorithm;
            switch (profile) {
                case OFF: this.radius = 0; this.travelSpeed = 1; this.travelNoise = 0; break;
                case SUBTLE: this.radius = .10; this.travelSpeed = .35; this.travelNoise = .10; break;
                case BALANCED: this.radius = .28; this.travelSpeed = .70; this.travelNoise = .25; break;
                case STRONG: this.radius = .60; this.travelSpeed = 1.30; this.travelNoise = .45; break;
                default: this.radius = radius; this.travelSpeed = travelSpeed; this.travelNoise = travelNoise;
            }
        }

        public boolean same(Configuration other) {
            return other != null && profile == other.profile && algorithm == other.algorithm
                    && Double.compare(radius, other.radius) == 0
                    && Double.compare(travelSpeed, other.travelSpeed) == 0
                    && Double.compare(travelNoise, other.travelNoise) == 0;
        }
    }

    private final Configuration settings;
    private final AdaptiveAimNoise pointNoise, tempoNoise;
    private final double[] initial = new double[3], origin = new double[3];
    private double previousSeconds = Double.NaN, phase;

    public AdaptiveHitpointMotion(long seed, Configuration settings, double initialRadius) {
        if (settings == null || !unit(initialRadius))
            throw new IllegalArgumentException("invalid initial hitpoint controls");
        this.settings = settings;
        pointNoise = settings.radius > 0 ? new AdaptiveAimNoise(seed, 1, settings.algorithm.type) : null;
        tempoNoise = settings.radius > 0 && settings.travelNoise > 0
                ? new AdaptiveAimNoise(seed ^ 0x4b53ad37d2e6191bL, .7) : null;
        if (initialRadius > 0) {
            // One acquisition-local offset. Never redraw it from a motion/render query.
            Random random = new Random(seed ^ 0x21b35f8b85b9c1e3L);
            double x = random.nextDouble() * 2 - 1, y = random.nextDouble() * 2 - 1;
            double z = random.nextDouble() * 2 - 1;
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length > 1e-12) {
                double scale = initialRadius * Math.cbrt(random.nextDouble()) / length;
                initial[0] = x * scale; initial[1] = y * scale; initial[2] = z * scale;
            }
        }
        if (pointNoise != null) for (int axis = 0; axis < 3; axis++) origin[axis] = pointNoise.sample(0, axis + 1);
    }

    /**
     * Repeated time samples remap the same local offset into the supplied box without
     * advancing its phase. Long intervals cannot accumulate a catch-up movement budget.
     * The caller owns acquisition invalidation and keeps its existing pause/scene gates.
     */
    public double[] point(double baseX, double baseY, double baseZ,
                          double minX, double minY, double minZ,
                          double maxX, double maxY, double maxZ, double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0 || !Double.isFinite(baseX)
                || !Double.isFinite(baseY) || !Double.isFinite(baseZ)
                || !box(minX, maxX) || !box(minY, maxY) || !box(minZ, maxZ)) return null;
        if (!Double.isNaN(previousSeconds)) {
            double gap = seconds - previousSeconds;
            if (!Double.isFinite(gap) || gap < 0) return null;
            if (gap > 0 && pointNoise != null) {
                double dt = Math.min(MAX_STEP_SECONDS, gap);
                double variation = tempoNoise == null ? 0
                        : tempoNoise.sample(previousSeconds + dt * .5, 0) * settings.travelNoise;
                phase += dt * settings.travelSpeed * (1 + variation);
            }
        }
        previousSeconds = seconds;
        double[] offset = new double[3];
        double lengthSquared = 0;
        if (pointNoise != null) for (int axis = 0; axis < 3; axis++) {
            offset[axis] = (pointNoise.sample(phase, axis + 1) - origin[axis]) * .5;
            lengthSquared += offset[axis] * offset[axis];
        }
        // One ellipsoid radius, not three independently full-radius corner offsets.
        double scale = settings.radius / Math.max(1, Math.sqrt(lengthSquared));
        for (int axis = 0; axis < 3; axis++) offset[axis] = initial[axis] + offset[axis] * scale;
        return new double[]{axis(baseX, minX, maxX, offset[0]),
                axis(baseY, minY, maxY, offset[1]), axis(baseZ, minZ, maxZ, offset[2])};
    }

    private static double axis(double base, double min, double max, double offset) {
        double width = max - min, inset = width * .001;
        double anchor = Math.max(min + inset, Math.min(max - inset, base));
        return Math.max(min + inset, Math.min(max - inset, anchor + width * .5 * offset));
    }

    private static boolean box(double min, double max) {
        return Double.isFinite(min) && Double.isFinite(max) && max > min && Double.isFinite(max - min);
    }

    private static boolean unit(double value) {
        return Double.isFinite(value) && value >= 0 && value <= 1;
    }
}
