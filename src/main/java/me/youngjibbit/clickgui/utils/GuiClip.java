package me.youngjibbit.clickgui.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.BufferUtils;
import java.nio.IntBuffer;
import static org.lwjgl.opengl.GL11.*;

/** Intersects with, and restores, the actual parent scissor, including external callers. */
public final class GuiClip implements AutoCloseable {
    private final boolean enabled;
    private final IntBuffer previous = BufferUtils.createIntBuffer(16);

    private GuiClip(ClipRect bounds) {
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc).getScaleFactor();
        enabled = glIsEnabled(GL_SCISSOR_TEST);
        glGetInteger(GL_SCISSOR_BOX, previous);
        int left = Math.max(0, (int) Math.ceil(bounds.x * scale));
        int bottom = Math.max(0, (int) Math.ceil(mc.displayHeight - (bounds.y + bounds.height) * scale));
        int right = Math.min(mc.displayWidth, (int) Math.floor((bounds.x + bounds.width) * scale));
        int top = Math.min(mc.displayHeight, (int) Math.floor(mc.displayHeight - bounds.y * scale));
        if (enabled) {
            left = Math.max(left, previous.get(0));
            bottom = Math.max(bottom, previous.get(1));
            right = Math.min(right, previous.get(0) + previous.get(2));
            top = Math.min(top, previous.get(1) + previous.get(3));
        }
        glEnable(GL_SCISSOR_TEST);
        glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
    }

    public static GuiClip pushTransformed(ClipRect bounds) {
        float[] r=me.youngjibbit.utils.render.ScreenRect.project(bounds.x,bounds.y,bounds.width,bounds.height);
        return push(new ClipRect(r[0],r[1],r[2],r[3]));
    }

    public static GuiClip push(ClipRect bounds) {
        return new GuiClip(bounds);
    }

    @Override
    public void close() {
        glScissor(previous.get(0), previous.get(1), previous.get(2), previous.get(3));
        if (!enabled) glDisable(GL_SCISSOR_TEST);
    }
}
