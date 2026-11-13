package me.youngjibbit.utils.render.shader.blur;

/** Physical output dimensions; ceil division preserves the final row/column at odd sizes. */
public final class SceneBlurSize {
    private SceneBlurSize() { }

    public static int dimension(int fullSize, int divisor) {
        if (fullSize < 1 || divisor != 1 && divisor != 2 && divisor != 4) {
            throw new IllegalArgumentException("Positive scene size and divisor 1, 2 or 4 required");
        }
        return 1 + (fullSize - 1) / divisor;
    }

    public static float radius(float fullRadius, int outputSize, int fullSize) {
        if (outputSize < 1 || fullSize < 1 || outputSize > fullSize) {
            throw new IllegalArgumentException("Scene blur output must fit its source dimensions");
        }
        return fullRadius * ((float)outputSize / fullSize);
    }
}
