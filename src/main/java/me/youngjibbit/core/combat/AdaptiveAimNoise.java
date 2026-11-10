package me.youngjibbit.core.combat;

import me.youngjibbit.utils.math.FastNoiseLite;

/** Acquisition-local coherent sampling. Reading a sample never advances a random stream. */
final class AdaptiveAimNoise {
    private final FastNoiseLite noise;
    private final double tempo;

    AdaptiveAimNoise(long seed, double tempo) {
        this.tempo = tempo;
        noise = new FastNoiseLite((int) (seed ^ (seed >>> 32)));
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noise.SetFrequency(1f);
    }

    double sample(double seconds, int channel) {
        return Math.max(-1, Math.min(1,
                noise.GetNoise((float) (seconds * tempo), 17.31f + channel * 43.17f)));
    }
}
