package me.youngjibbit.utils.render;

import me.youngjibbit.utils.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.BlockPos;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.glu.GLU;
import org.lwjgl.util.vector.Vector2f;

import java.awt.Color;
import java.nio.FloatBuffer;

import static org.lwjgl.opengl.GL11.glColor4ub;

/**
 * Original Target block ESP geometry from YoungJibbit95/DemisePrivate,
 * commit e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb:
 * src/main/java/wtf/demise/utils/render/RenderUtils.java (blob c9ffb4e28a4229761624ef89572021674e33cd76).
 * Public entry points retain the source's alpha and pass colors while adapting its
 * Interface palette to the host theme and protecting the host's surrounding GL state.
 */
public final class DemiseScaffoldOverlay {
    private DemiseScaffoldOverlay() { }

    public static void render(BlockPos pos) {
        Minecraft client = Minecraft.getMinecraft();
        if (pos == null || client == null || client.thePlayer == null || client.theWorld == null) return;
        int color = (Theme.getDefaultTheme().getPrimaryGradientRGB() & 0x00FFFFFF) | (100 << 24);
        try (RenderState ignored = new RenderState()) {
            renderBlock(pos, color, false, true);
        }
    }

    public static void shader(BlockPos pos, boolean glow) {
        Minecraft client = Minecraft.getMinecraft();
        if (pos == null || client == null || client.thePlayer == null || client.theWorld == null) return;
        int color = glow ? (Theme.getDefaultTheme().getPrimaryGradientRGB() | 0xFF000000) : Color.black.getRGB();
        try (RenderState ignored = new RenderState()) {
            draw2DCube(pos, color);
        }
    }

    private static void renderBlock(BlockPos blockPos, int color, boolean outline, boolean shade) {
        renderBox(blockPos.getX(), blockPos.getY(), blockPos.getZ(), 1, 1, 1, color, outline, shade);
    }

    private static void renderBox(int x, int y, int z, double x2, double y2, double z2, int color, boolean outline, boolean shade) {
        Minecraft mc = Minecraft.getMinecraft();
        double xPos = x - mc.getRenderManager().viewerPosX;
        double yPos = y - mc.getRenderManager().viewerPosY;
        double zPos = z - mc.getRenderManager().viewerPosZ;
        AxisAlignedBB axisAlignedBB = new AxisAlignedBB(xPos, yPos, zPos, xPos + x2, yPos + y2, zPos + z2);
        drawAxisAlignedBB(axisAlignedBB, shade, outline, color);
    }

    private static void drawAxisAlignedBB(AxisAlignedBB axisAlignedBB, boolean filled, boolean outline, int color) {
        drawSelectionBoundingBox(axisAlignedBB, outline, filled, color);
    }

    private static void drawOutlineBoundingBox(final AxisAlignedBB bb, Color color) {
        RenderGlobal.func_181563_a(bb, color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha());
    }

    private static void drawFilledBoundingBox(final AxisAlignedBB bb, Color color) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldrenderer = tessellator.getWorldRenderer();

        worldrenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        tessellator.draw();
        worldrenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        tessellator.draw();
        worldrenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        tessellator.draw();
        worldrenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        tessellator.draw();
        worldrenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        tessellator.draw();
        worldrenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.minX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.minZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.maxY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        worldrenderer.func_181662_b(bb.maxX, bb.minY, bb.maxZ).func_181669_b(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha()).func_181675_d();
        tessellator.draw();
    }

    private static void drawSelectionBoundingBox(final AxisAlignedBB bb, final boolean outline, final boolean filled, int color) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GL11.glLineWidth(2.0F);
        GlStateManager.disableTexture2D();
        GL11.glDisable(2929);
        GlStateManager.depthMask(false);
        GlStateManager.pushMatrix();

        if (outline) {
            drawOutlineBoundingBox(bb, new Color(color, true));
        }
        if (filled) {
            drawFilledBoundingBox(bb, new Color(color, true));
        }

        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GL11.glEnable(2929);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static Vector2f worldToScreen(float x, float y, float z, ScaledResolution sr, boolean ignoreInvisible) {
        Minecraft mc = Minecraft.getMinecraft();
        x -= (float) mc.getRenderManager().viewerPosX;
        y -= (float) mc.getRenderManager().viewerPosY;
        z -= (float) mc.getRenderManager().viewerPosZ;

        FloatBuffer winCoords = BufferUtils.createFloatBuffer(3);

        GLU.gluProject(
                x, y, z,
                ActiveRenderInfo.modelview(),
                ActiveRenderInfo.projection(),
                ActiveRenderInfo.viewport(),
                winCoords
        );

        float screenX = winCoords.get(0) / sr.getScaleFactor();
        float screenY = winCoords.get(1) / sr.getScaleFactor();
        float depth = winCoords.get(2);

        boolean isVisible = depth >= -0.01f && depth <= 1.01f && screenX >= 0.0f && screenX <= sr.getScaledWidth() && screenY >= 0.0f && screenY <= sr.getScaledHeight();

        if (isVisible || ignoreInvisible) {
            return new Vector2f(screenX, sr.getScaledHeight() - screenY);
        }

        return null;
    }

    private static void draw2DCube(BlockPos pos, int color) {
        draw2DCube(pos.getX(), pos.getY(), pos.getZ(), color);
    }

    private static void draw2DCube(double x, double y, double z, int color) {
        y += 0.03;

        Vector2f[] projected = new Vector2f[]{
                wts(x, y, z),
                wts(x + 1, y, z),
                wts(x + 1, y + 1, z),
                wts(x, y + 1, z),
                wts(x, y, z + 1),
                wts(x + 1, y, z + 1),
                wts(x + 1, y + 1, z + 1),
                wts(x, y + 1, z + 1)
        };

        int[][] faces = new int[][]{
                {0, 1, 2, 3},
                {5, 4, 7, 6},
                {0, 4, 5, 1},
                {3, 2, 6, 7},
                {1, 5, 6, 2},
                {4, 0, 3, 7}
        };

        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        for (int[] face : faces) {
            GL11.glBegin(GL11.GL_QUADS);
            color(color);

            for (int index : face) {
                Vector2f pos = projected[index];
                if (pos == null) continue;

                GL11.glVertex2f(pos.x, pos.y);
            }

            GL11.glEnd();
        }

        GL11.glEnable(GL11.GL_TEXTURE_2D);
    }

    private static Vector2f wts(double x, double y, double z) {
        return worldToScreen((float) x, (float) y, (float) z, new ScaledResolution(Minecraft.getMinecraft()), false);
    }

    private static void color(int color) {
        glColor4ub(
                (byte) (color >> 16 & 0xFF),
                (byte) (color >> 8 & 0xFF),
                (byte) (color & 0xFF),
                (byte) (color >> 24 & 0xFF)
        );
    }
}
