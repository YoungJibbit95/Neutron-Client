package me.youngjibbit.utils.render.shader.blur;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Render-thread cache of adjacent Gaussian taps combined for linear texture sampling. */
final class PairedGaussianKernelCache {
    private final Kernel[] kernels = new Kernel[GaussianKernelCache.MAX_RADIUS + 1];

    Kernel get(int radius, FloatBuffer original) {
        if (radius < 1 || radius > GaussianKernelCache.MAX_RADIUS || original.limit() < radius + 1) {
            throw new IllegalArgumentException("Gaussian radius/weights do not match");
        }
        Kernel kernel = kernels[radius];
        if (kernel == null) {
            int count = (radius + 1) / 2;
            FloatBuffer weights = floats(count), offsets = floats(count);
            float total = original.get(0);
            for (int i = 1; i <= radius; i++) total += original.get(i) * 2f;
            for (int first = 1; first <= radius; first += 2) {
                float a = original.get(first), b = first < radius ? original.get(first + 1) : 0;
                float combined = a + b;
                weights.put(combined);
                offsets.put(first + b / combined);
            }
            weights.flip(); offsets.flip();
            kernel = new Kernel(original.get(0), total, weights, offsets);
            kernels[radius] = kernel;
        }
        kernel.weights.clear(); kernel.offsets.clear();
        return kernel;
    }

    private static FloatBuffer floats(int count) {
        return ByteBuffer.allocateDirect(count * Float.BYTES).order(ByteOrder.nativeOrder()).asFloatBuffer();
    }

    static final class Kernel {
        final float center, total;
        final FloatBuffer weights, offsets;
        Kernel(float center, float total, FloatBuffer weights, FloatBuffer offsets) {
            this.center = center; this.total = total; this.weights = weights; this.offsets = offsets;
        }
        int pairs() { return weights.limit(); }
        int textureSamples() { return 1 + 2 * pairs(); }
    }
}
