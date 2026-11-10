package me.youngjibbit.core.network;

import me.youngjibbit.utils.math.FastNoiseLite;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;

/** Client-thread ownership of one timing window per connection, including its recovery gap. */
public final class CombatTimingCoordinator {
    private static final long LEASE_NS = 250_000_000L;
    private final LongSupplier clock;
    private final FastNoiseLite windowNoise, recoveryNoise;
    private Object owner;
    private long leaseUntil, readyAt;
    private boolean cooling;
    private int sample;

    public CombatTimingCoordinator(LongSupplier clock) {
        this(clock, ThreadLocalRandom.current().nextInt());
    }
    public CombatTimingCoordinator(LongSupplier clock, int seed) {
        this.clock = clock;
        windowNoise = profile(seed, FastNoiseLite.NoiseType.OpenSimplex2S, .35f);
        recoveryNoise = profile(seed ^ 0x51ed270b, FastNoiseLite.NoiseType.Perlin, .17f);
    }
    private static FastNoiseLite profile(int seed, FastNoiseLite.NoiseType type, float frequency) {
        FastNoiseLite noise = new FastNoiseLite(seed);
        noise.SetNoiseType(type);
        noise.SetFractalType(FastNoiseLite.FractalType.FBm);
        noise.SetFractalOctaves(2);
        noise.SetFrequency(frequency);
        return noise;
    }
    private void expire() {
        long now = clock.getAsLong();
        if (owner != null && now - leaseUntil >= 0) owner = null;
        if (cooling && now - readyAt >= 0) cooling = false;
    }
    public boolean acquire(Object candidate) {
        if (candidate == null) throw new IllegalArgumentException("Timing owner is required");
        expire();
        if (cooling || owner != null && owner != candidate) return false;
        owner = candidate;
        leaseUntil = clock.getAsLong() + LEASE_NS;
        return true;
    }
    public boolean owns(Object candidate) { expire(); return owner == candidate; }
    /** HUD reads must not expire a lease or otherwise advance the gameplay coordinator. */
    public boolean observesOwner(Object candidate) {
        long now=clock.getAsLong();
        return candidate!=null&&owner==candidate&&now-leaseUntil<0;
    }
    public void release(Object candidate, long cooldownMs) {
        // An expired owner must not release a newer owner's window or extend its gap.
        if (owner != candidate) return;
        owner = null;
        long delay = Math.max(0, Math.min(20_000, cooldownMs));
        readyAt = clock.getAsLong() + delay * 1_000_000L;
        cooling = delay > 0;
    }
    public void interrupt() { owner = null; }
    public void reset() { owner = null; cooling = false; }

    /** Sample once when opening a window; configured endpoints remain hard limits. */
    public int sampleMillis(int minimum, int maximum) {
        return sample(windowNoise, Math.min(minimum, maximum), Math.max(minimum, maximum));
    }
    private int sample(FastNoiseLite noise, int low, int high) {
        if (low == high) return low;
        float value = noise.GetNoise(++sample * .73f, (float)(clock.getAsLong() / 1_000_000_000D));
        double unit = Math.max(0, Math.min(1, (value + 1D) * .5));
        return low + (int)Math.round(unit * ((long)high - low));
    }
    /** Mild variation below the configured maximum; zero remains zero. */
    public long cooldown(long maximumMs) {
        int high = (int)Math.max(0, Math.min(20_000, maximumMs));
        return sample(recoveryNoise, (int)Math.ceil(high * .9), high);
    }
}
