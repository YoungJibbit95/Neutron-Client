package me.youngjibbit.core.world;

import me.youngjibbit.core.combat.ClickRandomizer;

import java.lang.ref.WeakReference;
import java.security.SecureRandom;
import java.util.Random;

/**
 * A per-module placement clock, with no input, inventory, rotation or packet access.
 * The host calls tryConsume only at its original PRE/C03/POST placement boundary.
 * Consuming an interval is an attempted opportunity, never a successful block.
 */
public final class BridgePlacementCadence {
    public enum Profile { BASIC, SMOOTHSTEP, PERLIN }

    /** Temporal CPS half-lives, not render-frame interpolation weights. */
    public enum Interpolation {
        STABLE(1.2), SMOOTH(.45), DYNAMIC(.12);
        final double halfLifeSeconds;
        Interpolation(double seconds) { halfLifeSeconds = seconds; }
    }

    public static final class Settings {
        public final double minCps, maxCps, variation;
        public final Profile profile;
        public final Interpolation interpolation;
        private final ClickRandomizer.Settings sampling;

        public Settings(double first, double second, Profile profile,
                        Interpolation interpolation, double variation) {
            this.profile = profile == null ? Profile.BASIC : profile;
            this.interpolation = interpolation == null ? Interpolation.SMOOTH : interpolation;
            String mode = this.profile == Profile.PERLIN ? "Perlin"
                    : this.profile == Profile.SMOOTHSTEP ? "Smoothstep" : "Basic";
            sampling = new ClickRandomizer.Settings(first, second, mode, false, 0);
            minCps = sampling.min;
            maxCps = sampling.max;
            this.variation = finiteClamp(variation, 0, 1);
        }

        private boolean same(Settings other) {
            return other != null && minCps == other.minCps && maxCps == other.maxCps
                    && variation == other.variation && profile == other.profile
                    && interpolation == other.interpolation;
        }
    }

    /** Generation belongs to the host module and must advance after invalidation. */
    public static final class Scope {
        public final Object owner, world, player, connection;
        public final long epoch, generation;

        public Scope(Object owner, Object world, Object player, Object connection,
                     long epoch, long generation) {
            this.owner = owner; this.world = world; this.player = player;
            this.connection = connection; this.epoch = epoch; this.generation = generation;
        }

        private boolean usable() {
            return owner != null && world != null && player != null && connection != null
                    && epoch >= 0 && generation >= 0;
        }

        private boolean same(Scope other) {
            return other != null && owner == other.owner && world == other.world
                    && player == other.player && connection == other.connection
                    && epoch == other.epoch && generation == other.generation;
        }
    }

    public static final class Attempt {
        public final long opportunity, consumedAtNanos, intervalNanos;
        public final double plannedCps;

        private Attempt(long opportunity, long now, long intervalNanos, double cps) {
            this.opportunity = opportunity;
            consumedAtNanos = now;
            this.intervalNanos = intervalNanos;
            plannedCps = cps;
        }
    }

    private static final class Lifetime {
        final WeakReference<Object> owner, world, player, connection;
        final long epoch, generation;

        Lifetime(Scope scope) {
            owner = new WeakReference<>(scope.owner);
            world = new WeakReference<>(scope.world);
            player = new WeakReference<>(scope.player);
            connection = new WeakReference<>(scope.connection);
            epoch = scope.epoch; generation = scope.generation;
        }
    }

    private final Thread owningThread = Thread.currentThread();
    private final long seed;
    private Lifetime lifetime;
    private Scope scope;
    private Settings settings;
    private ClickRandomizer randomizer;
    private long sessionCount, sampledTimeMillis, lastObservedNanos, lastSampleNanos;
    private long intervalStartNanos, intervalNanos, lastOpportunity = -1;
    private long samples, attempts;
    private double pendingCps;

    public BridgePlacementCadence() { this(new SecureRandom().nextLong()); }
    public BridgePlacementCadence(long seed) { this.seed = seed; }

    /**
     * Repeated activation cannot restart an interval. A replacement session needs
     * the same owner and a strictly newer generation, even after reset().
     */
    public synchronized boolean start(Scope next, Settings nextSettings, long nowNanos) {
        if (Thread.currentThread() != owningThread || next == null || !next.usable()
                || nextSettings == null || !newLifetime(next)) return false;
        lifetime = new Lifetime(next);
        scope = next;
        settings = nextSettings;
        lastOpportunity = -1;
        samples = attempts = 0;
        lastObservedNanos = lastSampleNanos = intervalStartNanos = nowNanos;
        sampledTimeMillis = nowNanos / 1_000_000L;
        long sessionSeed = mix(seed ^ mix(next.generation) ^ mix(++sessionCount));
        randomizer = new ClickRandomizer(new Random(sessionSeed),
                new Random(sessionSeed ^ 0x6a09e667f3bcc909L), () -> sampledTimeMillis);
        sampleNext(nowNanos, true);
        return true;
    }

    /** Pure observation: rendering or repeated polling never samples an interval. */
    public synchronized boolean isDue(Scope token, Settings current, long nowNanos) {
        return current(token) && settings.same(current) && nowNanos >= lastObservedNanos
                && elapsed(nowNanos, intervalStartNanos) >= intervalNanos;
    }

    /**
     * One observation per monotonically increasing physical placement opportunity.
     * A rejected controller result must not be fed back as another tryConsume call.
     * Even an ineligible or not-yet-due opportunity cannot later be reused.
     */
    public synchronized Attempt tryConsume(Scope token, Settings current, long opportunity,
                                           long nowNanos, boolean eligible) {
        return tryConsume(token, current, opportunity, nowNanos, eligible, false);
    }

    /**
     * A newly acquired urgent placement may spend its first attempt immediately.
     * This is one startup opportunity, not an emergency rate override: a failed
     * controller still consumes it, and every later attempt waits a full interval.
     * The host must have already checked its current accepted ray and task.
     */
    public synchronized Attempt tryConsume(Scope token, Settings current, long opportunity,
                                           long nowNanos, boolean eligible, boolean immediateFirst) {
        if (!current(token) || opportunity < 0 || opportunity <= lastOpportunity) return null;
        if (!settings.same(current) || nowNanos < lastObservedNanos) {
            deactivate();
            return null;
        }
        lastOpportunity = opportunity;
        lastObservedNanos = nowNanos;
        if (!eligible || elapsed(nowNanos, intervalStartNanos) < intervalNanos
                && !(immediateFirst && attempts == 0)) return null;
        Attempt result = new Attempt(opportunity, nowNanos, intervalNanos, pendingCps);
        attempts++;
        // Schedule from this actual opportunity. Missed windows create no debt.
        intervalStartNanos = nowNanos;
        sampleNext(nowNanos, false);
        return result;
    }

    /** An old host callback may release only its exact session, never a replacement. */
    public synchronized boolean reset(Scope expected) {
        if (!current(expected)) return false;
        deactivate();
        return true;
    }

    /** Owner-only disposal; release strong scene references and keep the weak lifetime tombstone. */
    public synchronized void reset() {
        if (Thread.currentThread() == owningThread) deactivate();
    }

    public synchronized boolean isActive() { return scope != null; }
    public synchronized long getSampleCount() { return samples; }
    public synchronized long getAttemptCount() { return attempts; }
    public synchronized long getPendingIntervalNanos() { return scope == null ? 0 : intervalNanos; }
    public synchronized double getPendingCps() { return scope == null ? 0 : pendingCps; }

    private boolean current(Scope token) {
        return Thread.currentThread() == owningThread && scope != null && scope.same(token);
    }

    private boolean newLifetime(Scope next) {
        if (lifetime == null) return true;
        return next.owner == lifetime.owner.get() && next.generation > lifetime.generation
                && (next.connection != lifetime.connection.get() || next.epoch >= lifetime.epoch);
    }

    private void deactivate() {
        scope = null; settings = null; randomizer = null;
        intervalNanos = 0; pendingCps = 0;
    }

    private void sampleNext(long nowNanos, boolean first) {
        sampledTimeMillis = nowNanos / 1_000_000L;
        double raw = randomizer.sample(settings.sampling).cps;
        double middle = (settings.minCps + settings.maxCps) * .5;
        double target = middle + (raw - middle) * settings.variation;
        if (first) pendingCps = target;
        else {
            double seconds = elapsed(nowNanos, lastSampleNanos) / 1_000_000_000.0;
            double alpha = 1 - Math.exp(-Math.log(2) * seconds / settings.interpolation.halfLifeSeconds);
            pendingCps += (target - pendingCps) * alpha;
        }
        pendingCps = finiteClamp(pendingCps, settings.minCps, settings.maxCps);
        // Ceil avoids exceeding the requested CPS by millisecond truncation.
        intervalNanos = (long) Math.ceil(1_000_000_000.0 / pendingCps);
        lastSampleNanos = nowNanos;
        samples++;
    }

    private static long elapsed(long now, long then) {
        if (now < then) return 0;
        long difference = now - then;
        return difference < 0 ? Long.MAX_VALUE : difference;
    }

    private static double finiteClamp(double value, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : min;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        value = (value ^ (value >>> 27)) * 0x94d049bb133111ebL;
        return value ^ (value >>> 31);
    }
}
