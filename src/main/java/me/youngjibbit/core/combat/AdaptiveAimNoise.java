package me.youngjibbit.core.combat;

import me.youngjibbit.utils.math.FastNoiseLite;

/** Acquisition-local coherent sampling. Reading a sample never advances a random stream. */
final class AdaptiveAimNoise {
    private final FastNoiseLite noise;
    private final double tempo;

    AdaptiveAimNoise(long seed, double tempo) {
        this(seed, tempo, FastNoiseLite.NoiseType.OpenSimplex2S);
    }

    AdaptiveAimNoise(long seed, double tempo, FastNoiseLite.NoiseType type) {
        if (!Double.isFinite(tempo) || tempo <= 0 || type == null)
            throw new IllegalArgumentException("invalid Adaptive noise sampler");
        this.tempo = tempo;
        noise = new FastNoiseLite((int) (seed ^ (seed >>> 32)));
        noise.SetNoiseType(type);
        noise.SetFrequency(1f);
    }

    double sample(double seconds, int channel) {
        float time = (float) (seconds * tempo);
        if (!Double.isFinite(seconds) || !Float.isFinite(time)) return 0;
        return Math.max(-1, Math.min(1,
                noise.GetNoise(time, 17.31f + channel * 43.17f)));
    }
}
