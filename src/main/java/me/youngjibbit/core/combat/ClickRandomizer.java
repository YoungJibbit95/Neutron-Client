package me.youngjibbit.core.combat;

import me.youngjibbit.utils.math.FastNoiseLite;
import java.security.SecureRandom;
import java.util.Random;
import java.util.function.LongSupplier;

/** Per-module bounded CPS sampling. Randomization adds milliseconds, not extra attacks. */
public final class ClickRandomizer {
    public static String[] modes() {
        return new String[]{"Basic", "Next", "Secure", "Pi", "Sin & Cos", "Smoothstep", "Perlin", "Test"};
    }

    public static final class Settings {
        public final double min, max, extraMillis;
        public final String mode;
        public Settings(double first, double second, String mode, boolean extra, double amount) {
            first = bounded(first, 1, 20); second = bounded(second, 1, 20);
            min = Math.min(first, second); max = Math.max(first, second);
            this.mode = mode;
            extraMillis = extra ? bounded(amount, .5, 5) : 0;
        }
    }

    public static final class Sample {
        public final double cps;
        public final long delayMillis;
        private Sample(double cps, double extra) {
            this.cps = cps;
            delayMillis = Math.max(1, (long) (1000 / cps + extra));
        }
    }

    private final Random random, secure;
    private final LongSupplier clock;
    private final FastNoiseLite noise;
    private final long origin;
    private long samples;

    public ClickRandomizer() { this(new Random(), new SecureRandom(), () -> System.nanoTime() / 1_000_000L); }
    public ClickRandomizer(Random random, Random secure, LongSupplier clock) {
        this.random = random; this.secure = secure; this.clock = clock; origin = clock.getAsLong();
        noise = new FastNoiseLite(random.nextInt());
        noise.SetNoiseType(FastNoiseLite.NoiseType.Perlin);
        noise.SetFrequency(1);
    }

    public Sample sample(Settings settings) {
        double factor;
        double time = (clock.getAsLong() - origin) * .001;
        switch (settings.mode == null ? "Basic" : settings.mode) {
            case "Next":
                int count = (int) Math.floor(settings.max - settings.min);
                factor = count == 0 ? 0 : random.nextInt(count + 1) / (settings.max - settings.min);
                break;
            case "Secure": factor = secure.nextDouble(); break;
            case "Pi": factor = Math.sin(random.nextDouble() * Math.PI); break;
            case "Sin & Cos":
                factor = (Math.sin(secure.nextDouble() * Math.PI) + Math.cos(secure.nextDouble() * Math.PI) + 1) / 3;
                break;
            case "Smoothstep":
                double t = random.nextDouble(); factor = t * t * (3 - 2 * t); break;
            case "Perlin": factor = .5 + .5 * noise.GetNoise((float) (time * .5), 0); break;
            case "Test":
                factor = .5 + .5 * noise.GetNoise((float) (time * .5), (float) (samples * .17)); break;
            default: factor = random.nextDouble();
        }
        samples++;
        double cps = settings.min + (settings.max - settings.min) * bounded(factor, 0, 1);
        return new Sample(cps, settings.extraMillis == 0 ? 0 : random.nextDouble() * settings.extraMillis);
    }

    /** Frequency 10 means every tick; never a zero divisor. */
    public static boolean blockAtTick(int tick, double frequency) {
        return tick % Math.max(1, 10 - (int) Math.round(bounded(frequency, 0, 10))) == 0;
    }

    private static double bounded(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }
}
