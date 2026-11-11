package me.youngjibbit.core.inventory;

import me.youngjibbit.utils.math.FastNoiseLite;
import java.util.Random;
import java.util.function.LongSupplier;

/** One bounded sample and one monotonic deadline per action. Polling never advances the pattern. */
public final class InventoryPacing {
    private final LongSupplier clock;
    private final Random random;
    private final FastNoiseLite noise;
    private long started, delayNanos;
    private double position;
    private boolean latched;

    public InventoryPacing() { this(System::nanoTime, new Random().nextLong()); }
    public InventoryPacing(LongSupplier clock, long seed) {
        this.clock = clock;
        random = new Random(seed);
        noise = new FastNoiseLite((int)(seed ^ (seed >>> 32)));
        noise.SetNoiseType(FastNoiseLite.NoiseType.OpenSimplex2S);
        noise.SetFrequency(1F);
        position = random.nextDouble() * 1000;
        reset();
    }
    public void reset() { started = clock.getAsLong(); latched = false; delayNanos = 0; }
    public boolean ready(String profile, double minimumMs, double maximumMs, double rate) {
        if (!latched) {
            double a = bounded(minimumMs, 0, 1500, 0), b = bounded(maximumMs, 0, 1500, a);
            double min = Math.min(a, b), max = Math.max(a, b);
            double t;
            if ("Smooth".equals(profile)) t = .5 + .8 * noise.GetNoise((float)position, 17.3F);
            else if ("Waves".equals(profile))
                t = .5 + .4 * Math.sin(position) + .15 * noise.GetNoise((float)position, 51.7F);
            else t = random.nextDouble();
            delayNanos = Math.round((min + (max - min) * bounded(t, 0, 1, .5)) * 1_000_000D);
            position += bounded(rate, .1, 2, .5) * .35;
            latched = true;
        }
        return clock.getAsLong() - started >= delayNanos;
    }
    public double latchedDelayMs() { return latched ? delayNanos / 1_000_000D : -1; }
    private static double bounded(double value, double min, double max, double fallback) {
        return Double.isNaN(value) || Double.isInfinite(value) ? fallback : Math.max(min, Math.min(max, value));
    }
}
