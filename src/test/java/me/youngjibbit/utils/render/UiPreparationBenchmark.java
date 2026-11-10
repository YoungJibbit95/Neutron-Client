package me.youngjibbit.utils.render;

import java.lang.management.ManagementFactory;
import me.youngjibbit.utils.Theme;

/**
 * Headless CPU/allocation probe for repeated UI theme/gradient preparation. Run the same class
 * against baseline and changed production classes; this does not measure GPU or game FPS.
 */
public final class UiPreparationBenchmark {
    private static final int DRAWS_PER_FRAME = 250;
    private static volatile long checksum;

    private UiPreparationBenchmark() { }

    public static void main(String[] args) throws Exception {
        int frames = args.length == 0 ? 4000 : Integer.parseInt(args[0]);
        UiEffectsTest fixture = new UiEffectsTest();
        fixture.setup();
        try {
            long[] now = {0};
            UiEffects.timeSource(() -> now[0]);
            run(now, 1000);
            com.sun.management.ThreadMXBean threads =
                    (com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
            boolean allocation = threads.isThreadAllocatedMemorySupported();
            if (allocation && !threads.isThreadAllocatedMemoryEnabled()) threads.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().getId();
            long bytes = allocation ? threads.getThreadAllocatedBytes(thread) : 0;
            long started = System.nanoTime();
            run(now, frames);
            long nanos = System.nanoTime() - started;
            long allocated = allocation ? threads.getThreadAllocatedBytes(thread) - bytes : -1;
            long draws = (long)frames * DRAWS_PER_FRAME;
            System.out.println("frames,draws,nanos_per_draw,allocated_bytes_per_draw,checksum");
            System.out.println(frames + "," + draws + "," + (nanos / (double)draws) + ","
                    + (allocated < 0 ? "unsupported" : allocated / (double)draws) + "," + checksum);
        } finally {
            fixture.restore();
        }
    }

    private static void run(long[] now, int frames) {
        long value = 0;
        for (int frame = 0; frame < frames; frame++) {
            now[0] += 16666667L;
            UiEffects.beginFrame();
            for (int draw = 0; draw < DRAWS_PER_FRAME; draw++) {
                Theme theme = Theme.getDefaultTheme();
                value += token(UiEffects.gradient(theme, UiEffects.Channel.TEXT));
                value += token(UiEffects.gradient(theme, UiEffects.Channel.BORDER));
                value += token(UiEffects.gradient(theme, UiEffects.Channel.GLOW));
            }
        }
        checksum = value;
    }

    private static long token(UiEffects.Gradient gradient) {
        return Float.floatToRawIntBits(gradient.phase) * 31L + gradient.mode * 17L + gradient.axis;
    }
}
