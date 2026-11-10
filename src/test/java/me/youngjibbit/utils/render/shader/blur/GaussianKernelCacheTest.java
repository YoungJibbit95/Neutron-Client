package me.youngjibbit.utils.render.shader.blur;

import java.nio.FloatBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class GaussianKernelCacheTest {
    @Test public void everySupportedRadiusMatchesTheOriginalKernelBitForBit() {
        GaussianKernelCache cache = new GaussianKernelCache();
        for (int radius = 1; radius <= 127; radius++) {
            FloatBuffer actual = cache.get(radius);
            float[] expected = original(radius);
            assertTrue(actual.isDirect());
            assertEquals(radius + 1, actual.remaining());
            float sum = 0;
            for (int i = 0; i <= radius; i++) {
                assertEquals("radius=" + radius + ", sample=" + i,
                        Float.floatToIntBits(expected[i]), Float.floatToIntBits(actual.get(i)));
                sum += actual.get(i) * (i == 0 ? 1 : 2);
            }
            assertEquals(1f, sum, 0.000001f);
        }
    }

    @Test public void alternatingRadiiReuseBuffersAndRestoreTheirUploadRange() {
        GaussianKernelCache cache = new GaussianKernelCache();
        FloatBuffer seven = cache.get(7);
        seven.position(5);
        seven.limit(6);
        FloatBuffer larger = cache.get(42);
        assertSame(seven, cache.get(7));
        assertEquals(0, seven.position());
        assertEquals(8, seven.limit());
        assertSame(larger, cache.get(42));
        assertEquals(original(7)[7], seven.get(7), 0f);
    }

    @Test public void rejectsRadiiOutsideTheShaderContract() {
        GaussianKernelCache cache = new GaussianKernelCache();
        for (int radius : new int[]{Integer.MIN_VALUE, -1, 0, 128, Integer.MAX_VALUE}) {
            try {
                cache.get(radius);
                fail("Accepted radius " + radius);
            } catch (IllegalArgumentException expected) { }
        }
    }

    private static float[] original(int radius) {
        float[] weights = new float[radius + 1];
        float sum = 0;
        for (int i = 0; i <= radius; i++) {
            float value = (float)Math.exp(-i * i / (2f * (radius / 2f) * (radius / 2f)));
            weights[i] = value;
            sum += i == 0 ? value : 2 * value;
        }
        for (int i = 0; i <= radius; i++) weights[i] /= sum;
        return weights;
    }
}
