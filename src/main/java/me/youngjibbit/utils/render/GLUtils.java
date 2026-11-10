package me.youngjibbit.utils.render;

import me.youngjibbit.utils.GLUtil;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glDisable;

/**
 * Compatibility entry points for the former duplicate GL helper. Shared state changes now live
 * in GLUtil; the legacy public capability list stays independent for source/binary compatibility.
 */
@Deprecated
public class GLUtils {
    public static int[] enabledCaps = new int[32];

    public static void enableCaps(int... caps) {
        for (int cap : caps) glEnable(cap);
        enabledCaps = caps;
    }

    public static void disableCaps() {
        for (int cap : enabledCaps) glDisable(cap);
    }

    public static void enableDepth() { GLUtil.enableDepth(); }
    public static void disableDepth() { GLUtil.disableDepth(); }
    public static void startBlend() { GLUtil.startBlend(); }
    public static void endBlend() { GLUtil.endBlend(); }
    public static void setup2DRendering(boolean blend) { GLUtil.setup2DRendering(blend); }
    public static void setup2DRendering() { GLUtil.setup2DRendering(); }
    public static void end2DRendering() { GLUtil.end2DRendering(); }
    public static void startRotate(float x, float y, float rotate) { GLUtil.startRotate(x, y, rotate); }
    public static void endRotate() { GLUtil.endRotate(); }
}
