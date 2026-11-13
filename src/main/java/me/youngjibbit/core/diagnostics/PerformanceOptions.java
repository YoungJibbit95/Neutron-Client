package me.youngjibbit.core.diagnostics;

/**
 * Effective performance choices, published together for render and chunk-worker readers.
 * Setting callbacks only replace this immutable snapshot: no module lookup, GL call or
 * capture teardown occurs here. Capture observes its monotone revision on the owner thread.
 */
public final class PerformanceOptions {
    private static volatile Snapshot current = new Snapshot(false, false, false, 1,
            ChunkFrameBudget.DEFAULT, 0L);

    private PerformanceOptions() { }

    public static Snapshot current() { return current; }
    public static boolean isFastParticleCleanupEnabled() { return current.fastParticleCleanup; }
    public static boolean isFastVisibilityGraphEnabled() { return current.fastVisibilityGraph; }
    public static boolean isFastGaussianEnabled() { return current.fastGaussian; }
    public static int getSceneBlurDivisor() { return current.sceneBlurDivisor; }

    /** Replace strategies while retaining the current upload limits, e.g. for an isolated comparison. */
    public static synchronized boolean configure(boolean fastParticles, boolean fastVisibility,
                                                  boolean fastGaussian, int sceneBlurDivisor) {
        return publish(fastParticles, fastVisibility, fastGaussian, sceneBlurDivisor, current.uploadLimits);
    }

    /** Publish a complete module configuration in one volatile write. Validation precedes publication. */
    public static synchronized boolean configure(boolean fastParticles, boolean fastVisibility,
                                                  boolean fastGaussian, int sceneBlurDivisor,
                                                  double uploadMilliseconds, double uploadMebibytes) {
        ChunkFrameBudget.Limits limits = ChunkFrameBudget.createLimits(uploadMilliseconds, uploadMebibytes);
        return publish(fastParticles, fastVisibility, fastGaussian, sceneBlurDivisor, limits);
    }

    /** Module-off reset; persisted UI choices are owned by the module and are not overwritten. */
    public static synchronized boolean defaults() {
        return publish(false, false, false, 1, ChunkFrameBudget.DEFAULT);
    }

    /** Preserve the historical budget-only API without resetting unrelated strategy choices. */
    static synchronized void configureUploadLimits(ChunkFrameBudget.Limits limits) {
        Snapshot previous = current;
        publish(previous.fastParticleCleanup, previous.fastVisibilityGraph, previous.fastGaussian,
                previous.sceneBlurDivisor, limits);
    }

    private static boolean publish(boolean fastParticles, boolean fastVisibility, boolean fastGaussian,
                                   int sceneBlurDivisor, ChunkFrameBudget.Limits limits) {
        if (sceneBlurDivisor != 1 && sceneBlurDivisor != 2 && sceneBlurDivisor != 4) {
            throw new IllegalArgumentException("Scene blur divisor must be 1, 2 or 4");
        }
        Snapshot previous = current;
        if (previous.fastParticleCleanup == fastParticles && previous.fastVisibilityGraph == fastVisibility
                && previous.fastGaussian == fastGaussian && previous.sceneBlurDivisor == sceneBlurDivisor
                && previous.uploadLimits.nanoseconds == limits.nanoseconds && previous.uploadLimits.bytes == limits.bytes) {
            return false;
        }
        // Never alias a later configuration to an earlier revision, even when values are restored.
        long revision = Math.addExact(previous.revision, 1L);
        current = new Snapshot(fastParticles, fastVisibility, fastGaussian, sceneBlurDivisor, limits, revision);
        return true;
    }

    public static final class Snapshot {
        public final boolean fastParticleCleanup;
        public final boolean fastVisibilityGraph;
        public final boolean fastGaussian;
        public final int sceneBlurDivisor;
        public final ChunkFrameBudget.Limits uploadLimits;
        public final long revision;

        private Snapshot(boolean fastParticles, boolean fastVisibility, boolean fastGaussian,
                         int sceneBlurDivisor, ChunkFrameBudget.Limits uploadLimits, long revision) {
            this.fastParticleCleanup = fastParticles;
            this.fastVisibilityGraph = fastVisibility;
            this.fastGaussian = fastGaussian;
            this.sceneBlurDivisor = sceneBlurDivisor;
            this.uploadLimits = uploadLimits;
            this.revision = revision;
        }
    }
}
