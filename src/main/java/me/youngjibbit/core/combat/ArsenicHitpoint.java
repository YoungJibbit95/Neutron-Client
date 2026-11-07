package me.youngjibbit.core.combat;

import java.util.Random;
import me.youngjibbit.core.arsenic.aim.AimCore;

/** Continuous, seeded offsets in normalized box coordinates. The clock starts at acquisition. */
final class ArsenicHitpoint {
    private final double[] phases = new double[6];
    private double phase;

    ArsenicHitpoint(long seed) {
        Random random = new Random(seed);
        for (int i = 0; i < phases.length; i++) phases[i] = random.nextDouble() * Math.PI * 2;
    }

    void sample(AimCore.Input in, RotationProfile profile, double elapsedSeconds) {
        in.hitpointWeight = Math.max(0, Math.min(1, profile.hitpointStrength));
        if (in.hitpointWeight == 0) return;
        RotationProfile.HitpointNoise noise = profile.hitpointNoise;
        phase += Math.max(0, elapsedSeconds) * profile.hitpointSpeed * noise.rate * 2 * Math.PI;
        in.hitX = coordinate(in.minX, in.maxX, .5, .45 * noise.extent, 0);
        in.hitY = coordinate(in.minY, in.maxY, .58, .4 * noise.extent, 1);
        in.hitZ = coordinate(in.minZ, in.maxZ, .5, .45 * noise.extent, 2);
    }

    private double coordinate(double min, double max, double centre, double radius, int axis) {
        double wave = .7 * Math.sin(phase + phases[axis])
                + .3 * Math.sin(phase * 1.73 + phases[axis + 3]);
        return min + (max - min) * (centre + radius * wave);
    }
}
