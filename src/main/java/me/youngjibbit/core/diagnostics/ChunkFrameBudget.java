package me.youngjibbit.core.diagnostics;

/** Render-thread upload limits. Deadlines are capped, never extended. */
public final class ChunkFrameBudget {
    public static final Limits DEFAULT = new Limits(1_000_000L, 4L * 1024 * 1024);
    private static volatile Limits limits = DEFAULT;

    private ChunkFrameBudget() { }

    public static Limits current() { return limits; }

    public static void configure(double milliseconds, double mebibytes) {
        if (!Double.isFinite(milliseconds) || milliseconds < .25 || milliseconds > 8
                || !Double.isFinite(mebibytes) || mebibytes < .25 || mebibytes > 32) {
            throw new IllegalArgumentException("Upload limits must be 0.25–8 ms and 0.25–32 MiB");
        }
        limits = new Limits(Math.round(milliseconds * 1_000_000), Math.round(mebibytes * 1024 * 1024));
    }

    public static void defaults() { limits = DEFAULT; }

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
