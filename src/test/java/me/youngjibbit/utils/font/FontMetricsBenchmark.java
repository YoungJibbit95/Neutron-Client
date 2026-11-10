package me.youngjibbit.utils.font;

import com.sun.management.ThreadMXBean;

import java.awt.Font;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Locale;

/**
 * Opt-in CPU microbenchmark. Run the same class with baseline/current production
 * classes and the same Java runtime; this reports getWidth work, not client FPS.
 * Inputs and fonts are constructed before timing. No OpenGL context is needed.
 * Allocation counters cover this thread's Java heap, not native font/GPU memory.
 */
public final class FontMetricsBenchmark {
    private static volatile double checksum;

    public static void main(String[] args) {
        Locale.setDefault(Locale.ROOT);
        int iterations = args.length == 0 ? 250000 : Integer.parseInt(args[0]);
        if (iterations < 1) throw new IllegalArgumentException("Iterations must be positive");
        System.out.println("Font getWidth CPU microbenchmark; java=" + System.getProperty("java.runtime.version")
                + "; iterations=" + iterations + "; rounds=7; warmup=" + Math.max(100000, iterations));
        run("repeated-ui-labels", new String[]{
                "Neutron Client", "Client Settings", "Visuals / General", "Animation speed",
                "Performance diagnostics", "Right click to expand", "Enabled modules",
                "§lBold §oItalic §rRegular", "§aColor §lBold §cReset", "Привет Ω 中文 😀",
                "Session Info", "FPS: 144", "Backtrack: 120 ms", "Theme: PinkLemonade",
                "ClickGUI", "Glow strength", "Blur radius", "Rounded corners",
                "§nUnderline §mStrike", "line one\nline two", "Tab\tLabel", "tail§",
                "Module settings", "Poppins / Original"
        }, iterations);
        run("changing-hud-labels-192", labels(192), iterations);
        run("cache-churn-512", labels(512), iterations);
        char[] text = new char[300];
        Arrays.fill(text, 'W');
        run("long-text-without-retention", new String[]{new String(text)}, Math.max(1000, iterations / 10));
        System.out.printf("checksum=%.3f%n", checksum);
    }

    private static String[] labels(int count) {
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) labels[i] = "Backtrack: " + i + " ms | FPS " + (60 + i);
        return labels;
    }

    private static void run(String name, String[] labels, int iterations) {
        try (TTFFontRenderer font = new TTFFontRenderer(new Font(Font.DIALOG, Font.PLAIN, 20))) {
            measure(font, labels, Math.max(100000, iterations));
            double[] nanoseconds = new double[7], allocations = new double[7];
            ThreadMXBean memory = allocationCounter();
            long thread = Thread.currentThread().getId();
            for (int round = 0; round < nanoseconds.length; round++) {
                long beforeBytes = memory == null ? -1 : memory.getThreadAllocatedBytes(thread);
                long before = System.nanoTime();
                double total = measure(font, labels, iterations);
                long elapsed = System.nanoTime() - before;
                long bytes = memory == null ? -1 : memory.getThreadAllocatedBytes(thread) - beforeBytes;
                checksum += total;
                nanoseconds[round] = elapsed / (double) iterations;
                allocations[round] = bytes < 0 ? -1 : bytes / (double) iterations;
            }
            Arrays.sort(nanoseconds);
            Arrays.sort(allocations);
            System.out.printf("%s: median_ns_per_call=%.3f median_allocated_bytes_per_call=%.3f%n",
                    name, nanoseconds[3], allocations[3]);
        }
    }

    private static double measure(TTFFontRenderer font, String[] labels, int iterations) {
        double total = 0;
        int index = 0;
        for (int i = 0; i < iterations; i++) {
            total += font.getWidth(labels[index]);
            if (++index == labels.length) index = 0;
        }
        return total;
    }

    private static ThreadMXBean allocationCounter() {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        if (!(bean instanceof ThreadMXBean)) return null;
        ThreadMXBean memory = (ThreadMXBean) bean;
        if (!memory.isThreadAllocatedMemorySupported()) return null;
        if (!memory.isThreadAllocatedMemoryEnabled()) memory.setThreadAllocatedMemoryEnabled(true);
        return memory;
    }
}
