package me.youngjibbit.utils.render.shader.blur;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Render-thread cache of the unchanged, normalized Gaussian weights (at most 33 KiB). */
final class GaussianKernelCache {
    static final int MAX_RADIUS = 127;
    private final FloatBuffer[] kernels = new FloatBuffer[MAX_RADIUS + 1];

    /** The returned direct buffer is borrowed until the next call; callers must not modify its values. */
    FloatBuffer get(int radius) {
        if (radius < 1 || radius > MAX_RADIUS) {
            throw new IllegalArgumentException("Gaussian radius must be between 1 and " + MAX_RADIUS);
        }
        FloatBuffer kernel = kernels[radius];
        if (kernel == null) {
            kernel = ByteBuffer.allocateDirect((radius + 1) * Float.BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            float sum = 0;
            for (int i = 0; i <= radius; i++) {
                // Preserve the original float operation order and normalization exactly.
                float value = (float)Math.exp(-i * i / (2f * (radius / 2f) * (radius / 2f)));
                kernel.put(value);
                sum += i == 0 ? value : 2 * value;
            }
            for (int i = 0; i <= radius; i++) kernel.put(i, kernel.get(i) / sum);
            kernels[radius] = kernel;
        }
        kernel.clear();
        return kernel;
    }
}
