package me.youngjibbit.utils.font;

import org.junit.Test;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** Cache correctness, memory bounds and worker access; no native/OpenGL context required. */
public class FontTextCacheTest {
    private static final String[] LABELS = {
            "Neutron | 144 FPS", "§lBold §oItalic §rRegular", "§aColor §lBold §cReset",
            "§nUnderline §mStrike §kObfuscated", "§LUppercase §zUnknown tail§",
            "Привет Ω 中文 😀", "A\ud800B\udc00C", "short\nlonger label\n", "a\tb\r\u0000c", "   "
    };

    @Test public void cachedWidthsKeepExactFormattingUnicodeAndControlSemantics() {
        try (TTFFontRenderer font = renderer()) {
            // Invisible control prefixes bypass retention while preserving all width/style rules.
            String uncachedPrefix = repeat('\u0000', 300);
            for (String label : LABELS) {
                float expected = font.getWidth(uncachedPrefix + label);
                assertEquals(expected, font.getWidth(label), 0);
                for (int i = 0; i < 20; i++) assertEquals(expected, font.getWidth(new String(label)), 0);
            }
            assertEquals(0, font.getWidth(null), 0);
            assertEquals(0, font.getWidth(""), 0);
        }
    }

    @Test public void changingLabelsAndLongTextCannotGrowTheCacheWithoutBound() throws Exception {
        try (TTFFontRenderer font = renderer()) {
            Map<?, ?> widths = widths(font);
            for (int i = 0; i < 2048; i++) font.getWidth("Frame " + i);
            assertEquals(256, widths.size());
            assertFalse(widths.containsKey("Frame 0"));
            assertTrue(widths.containsKey("Frame 2047"));
            font.getWidth(repeat('W', 257));
            assertEquals(256, widths.size());
            assertFalse(widths.containsKey(repeat('W', 257)));
            // A recently used entry survives eviction by a newly arriving dynamic label.
            font.getWidth("Frame 1792");
            font.getWidth("Frame 2048");
            assertTrue(widths.containsKey("Frame 1792"));
            assertFalse(widths.containsKey("Frame 1793"));
            assertEquals(font.getWidth(repeat('\u0000', 300) + "Frame 0"), font.getWidth("Frame 0"), 0);
        }
    }

    @Test public void parallelMetricReadersShareTheCacheAndCloseReleasesRetainedLabels() throws Exception {
        TTFFontRenderer font = renderer();
        float[] expected = new float[LABELS.length];
        for (int i = 0; i < LABELS.length; i++) expected[i] = font.getWidth(LABELS[i]);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch start = new CountDownLatch(1);
        Thread[] workers = new Thread[4];
        try {
            for (int worker = 0; worker < workers.length; worker++) {
                workers[worker] = new Thread(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < 1000; i++) {
                            int label = i % LABELS.length;
                            assertEquals(expected[label], font.getWidth(LABELS[label]), 0);
                        }
                    } catch (Throwable error) {
                        failure.compareAndSet(null, error);
                    }
                });
                workers[worker].start();
            }
            start.countDown();
            for (Thread worker : workers) worker.join();
            assertNull(failure.get());
        } finally {
            font.close();
        }
        assertTrue(widths(font).isEmpty());
        try { font.getWidth(LABELS[0]); fail("Closed renderer accepted a cached label"); }
        catch (IllegalStateException expectedFailure) { }
    }

    private static TTFFontRenderer renderer() {
        return new TTFFontRenderer(new Font(Font.DIALOG, Font.PLAIN, 20));
    }

    private static String repeat(char character, int length) {
        char[] characters = new char[length];
        Arrays.fill(characters, character);
        return new String(characters);
    }

    private static Map<?, ?> widths(TTFFontRenderer font) throws Exception {
        Field field = TTFFontRenderer.class.getDeclaredField("widths");
        field.setAccessible(true);
        return (Map<?, ?>) field.get(font);
    }
}
