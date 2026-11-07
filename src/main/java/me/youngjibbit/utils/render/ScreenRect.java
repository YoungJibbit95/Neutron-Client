package me.youngjibbit.utils.render;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;

/** Captures GUI rectangles in screen space, including the current modelview transform. */
public final class ScreenRect {
    private static final Projection single = new Projection();

    private ScreenRect() { }

    public static float[] project(float x, float y, float width, float height) {
        single.capture();
        return single.project(x, y, width, height);
    }

    /** A transform shared only by consecutive draws with unchanged matrices and viewport. */
    public static final class Projection {
        private final FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
        private final FloatBuffer projection = BufferUtils.createFloatBuffer(16);
        private final FloatBuffer point = BufferUtils.createFloatBuffer(3);
        private final IntBuffer viewport = BufferUtils.createIntBuffer(16);
        private float guiScale;
        private int displayHeight;

        public void capture() {
            GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelView);
            GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection);
            GL11.glGetInteger(GL11.GL_VIEWPORT, viewport);
            Minecraft mc = Minecraft.getMinecraft();
            guiScale = new ScaledResolution(mc).getScaleFactor();
            displayHeight = mc.displayHeight;
        }

        public float[] project(float x, float y, float width, float height) {
            float minX = Float.POSITIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (int corner = 0; corner < 4; corner++) {
                float cornerX = x + ((corner & 1) == 0 ? 0 : width);
                float cornerY = y + ((corner & 2) == 0 ? 0 : height);
                GLU.gluProject(cornerX, cornerY, 0, modelView, projection, viewport, point);
                float projectedX = point.get(0);
                float projectedY = point.get(1);
                minX = Math.min(minX, projectedX);
                maxX = Math.max(maxX, projectedX);
                minY = Math.min(minY, projectedY);
                maxY = Math.max(maxY, projectedY);
            }
            return new float[]{minX / guiScale, (displayHeight - maxY) / guiScale,
                    (maxX - minX) / guiScale, (maxY - minY) / guiScale};
        }
    }
}
