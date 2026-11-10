package me.youngjibbit.utils.font;

import org.junit.Test;

import java.awt.Font;
import java.lang.reflect.Field;
import java.util.Arrays;
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
            FontTextCache widths = widths(font);
            for (int i = 0; i < 2048; i++) font.getWidth("Frame " + i);
            assertEquals(256, widths.size());
            assertTrue(Float.isNaN(widths.get("Frame 0")));
            assertFalse(Float.isNaN(widths.get("Frame 2047")));
            font.getWidth(repeat('W', 257));
            assertEquals(256, widths.size());
            assertTrue(Float.isNaN(widths.get(repeat('W', 257))));
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
        assertEquals(0, widths(font).size());
        try { font.getWidth(LABELS[0]); fail("Closed renderer accepted a cached label"); }
        catch (IllegalStateException expectedFailure) { }
    }

    @Test public void collidingLabelsUseFullEqualityAndEvictTheLeastRecentlyReadSlot() {
        String[] colliding = {"AaAaAa", "AaAaBB", "AaBBAa", "AaBBBB", "BBAaAa"};
        FontTextCache cache = new FontTextCache();
        for (int i = 0; i < colliding.length; i++) assertEquals(colliding[0].hashCode(), colliding[i].hashCode());
        for (int i = 0; i < 4; i++) cache.put(colliding[i], i + 1);
        assertEquals(1, cache.get(new String(colliding[0])), 0);
        cache.put(colliding[4], 5);
        assertTrue(Float.isNaN(cache.get(colliding[1])));
        assertEquals(1, cache.get(colliding[0]), 0);
        assertEquals(3, cache.get(colliding[2]), 0);
        assertEquals(4, cache.get(colliding[3]), 0);
        assertEquals(5, cache.get(colliding[4]), 0);
        cache.put(colliding[0], 9);
        assertEquals(9, cache.get(colliding[0]), 0);
        assertEquals(4, cache.size());
        cache.clear();
        assertEquals(0, cache.size());
        assertTrue(Float.isNaN(cache.get(colliding[0])));
    }

    private static TTFFontRenderer renderer() {
        return new TTFFontRenderer(new Font(Font.DIALOG, Font.PLAIN, 20));
    }

    private static String repeat(char character, int length) {
        char[] characters = new char[length];
        Arrays.fill(characters, character);
        return new String(characters);
    }

    private static FontTextCache widths(TTFFontRenderer font) throws Exception {
        Field field = TTFFontRenderer.class.getDeclaredField("widths");
        field.setAccessible(true);
        return (FontTextCache) field.get(font);
    }
}
