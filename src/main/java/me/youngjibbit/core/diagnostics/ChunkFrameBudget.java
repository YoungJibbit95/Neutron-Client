package me.youngjibbit.core.diagnostics;

/** Render-thread upload limits. Deadlines are capped, never extended. */
public final class ChunkFrameBudget {
    public static final Limits DEFAULT = new Limits(1_000_000L, 4L * 1024 * 1024);

    private ChunkFrameBudget() { }

    public static Limits current() { return PerformanceOptions.current().uploadLimits; }

    public static void configure(double milliseconds, double mebibytes) {
        PerformanceOptions.configureUploadLimits(createLimits(milliseconds, mebibytes));
    }

    static Limits createLimits(double milliseconds, double mebibytes) {
        if (!Double.isFinite(milliseconds) || milliseconds < .25 || milliseconds > 8
                || !Double.isFinite(mebibytes) || mebibytes < .25 || mebibytes > 32) {
            throw new IllegalArgumentException("Upload limits must be 0.25–8 ms and 0.25–32 MiB");
        }
        long nanoseconds = Math.round(milliseconds * 1_000_000);
        long bytes = Math.round(mebibytes * 1024 * 1024);
        return nanoseconds == DEFAULT.nanoseconds && bytes == DEFAULT.bytes
                ? DEFAULT : new Limits(nanoseconds, bytes);
    }

    public static void defaults() { PerformanceOptions.configureUploadLimits(DEFAULT); }

    public static final class Limits {
        public final long nanoseconds;
        public final long bytes;

        private Limits(long nanoseconds, long bytes) {
            this.nanoseconds = nanoseconds;
            this.bytes = bytes;
        }

        /** Zero retains the dispatcher's explicit one-batch mode. nanoTime subtraction handles wrap. */
        public long deadline(long requested, long now) {
            if (requested == 0L) return 0L;
            long remaining = requested - now;
            return remaining <= 0 ? now : now + Math.min(remaining, nanoseconds);
        }
    }
}
