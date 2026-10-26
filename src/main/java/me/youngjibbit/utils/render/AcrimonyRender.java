package me.youngjibbit.utils.render;

import me.youngjibbit.utils.GLUtil;
import me.youngjibbit.utils.Wrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class AcrimonyRender {
    public static Frustum frustrum = new Frustum();

    public static void setColor(int color) {
        float a = (float)(color >> 24 & 0xFF) / 255.0f;
        float r = (float)(color >> 16 & 0xFF) / 255.0f;
        float g = (float)(color >> 8 & 0xFF) / 255.0f;
        float b = (float)(color & 0xFF) / 255.0f;
        GL11.glColor4f(r, g, b, a);
    }

    public static void setAlphaLimit(float limit) {
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, (float)((double)limit * 0.01));
    }

    public static boolean isInViewFrustrum(Entity entity) {
        return isInViewFrustrum(entity.getEntityBoundingBox()) || entity.ignoreFrustumCheck;
    }

    private static boolean isInViewFrustrum(AxisAlignedBB bb) {
        Entity current = Minecraft.getMinecraft().getRenderViewEntity();
        frustrum.setPosition(current.posX, current.posY, current.posZ);
        return frustrum.isBoundingBoxInFrustum(bb);
    }

    public static void drawLoadingCircleFast(float x, float y, Color color) {
        for (int i = 0; i < 2; ++i) {
            int rot = (int)(System.nanoTime() / 1200000L * (long)i % 360L);
            drawCircle(x, y, (float)i * 7.0f, rot - 180, rot, color);
        }
    }

    public static void drawLoadingCircleNormal(float x, float y, Color color) {
        for (int i = 0; i < 2; ++i) {
            int rot = (int)(System.nanoTime() / 1200000L * (long)i % 360L);
            drawCircle(x, y, (float)i * 7.0f, rot - 180, rot, color);
        }
    }

    public static void drawLoadingCircleSlow(float x, float y, Color color) {
        for (int i = 0; i < 2; ++i) {
            int rot = (int)(System.nanoTime() / 8200000L * (long)i % 360L);
            drawCircle(x, y, (float)i * 7.0f, rot - 180, rot, color);
        }
    }

    public static void drawCircle(float x, float y, float radius, int start, int end, Color color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        glColor(color.getRGB());
        GL11.glEnable(2848);
        GL11.glLineWidth(2.5f);
        GL11.glBegin(3);
        for (float i = (float)end; i >= (float)start; i -= 4.0f) {
            GL11.glVertex2f((float)((double)x + Math.cos(i * MathHelper.PI / 180.0f) * (double)(radius * 1.001f)), (float)((double)y + Math.sin(i * MathHelper.PI / 180.0f) * (double)(radius * 1.001f)));
        }
        GL11.glEnd();
        GL11.glDisable(2848);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    public static void prepareBoxRender(float lineWidth, double red, double green, double blue, double alpha) {
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glLineWidth(lineWidth);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glEnable(2848);
        GL11.glDepthMask(false);
        GL11.glColor4d(red, green, blue, alpha);
    }

    public static void drawFilledCircleNoGL(int x, int y, double r, int c, int quality) {
        resetColor();
        setAlphaLimit(0.0f);
        GLUtil.setup2DRendering();
        color(c);
        GL11.glBegin(6);
        for (int i = 0; i <= 360 / quality; ++i) {
            double x2 = Math.sin((double)(i * quality) * Math.PI / 180.0) * r;
            double y2 = Math.cos((double)(i * quality) * Math.PI / 180.0) * r;
            GL11.glVertex2d((double)x + x2, (double)y + y2);
        }
        GL11.glEnd();
        GLUtil.end2DRendering();
    }

    public static void renderBlockBox(RenderManager rm, float partialTicks, double x, double y, double z) {
        AxisAlignedBB bb = new AxisAlignedBB(x, y, z, x + 1.0, y + 1.0, z + 1.0);
        RenderGlobal.func_181561_a(new AxisAlignedBB(bb.minX - x + (x - rm.renderPosX), bb.minY - y + (y - rm.renderPosY), bb.minZ - z + (z - rm.renderPosZ), bb.maxX - x + (x - rm.renderPosX), bb.maxY - y + (y - rm.renderPosY), bb.maxZ - z + (z - rm.renderPosZ)));
    }

    public static void renderEntityBox(RenderManager rm, float partialTicks, Entity entity) {
        AxisAlignedBB bb = entity.getEntityBoundingBox();
        double posX = interpolate(entity.posX, entity.lastTickPosX, partialTicks);
        double posY = interpolate(entity.posY, entity.lastTickPosY, partialTicks);
        double posZ = interpolate(entity.posZ, entity.lastTickPosZ, partialTicks);
        RenderGlobal.func_181561_a(new AxisAlignedBB(bb.minX - 0.05 - entity.posX + (posX - rm.renderPosX), bb.minY - 0.05 - entity.posY + (posY - rm.renderPosY), bb.minZ - 0.05 - entity.posZ + (posZ - rm.renderPosZ), bb.maxX + 0.05 - entity.posX + (posX - rm.renderPosX), bb.maxY + 0.1 - entity.posY + (posY - rm.renderPosY), bb.maxZ + 0.05 - entity.posZ + (posZ - rm.renderPosZ)));
    }

    public static void renderCustomPlayerBox(RenderManager rm, float partialTicks, double x, double y, double z) {
        renderCustomPlayerBox(rm, partialTicks, x, y, z, x, y, z);
    }

    public static void renderCustomPlayerBox(RenderManager rm, float partialTicks, double x, double y, double z, double lastX, double lastY, double lastZ) {
        AxisAlignedBB bb = new AxisAlignedBB(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
        double posX = interpolate(x, lastX, partialTicks);
        double posY = interpolate(y, lastY, partialTicks);
        double posZ = interpolate(z, lastZ, partialTicks);
        RenderGlobal.func_181561_a(new AxisAlignedBB(bb.minX - 0.05 - x + (posX - rm.renderPosX), bb.minY - 0.05 - y + (posY - rm.renderPosY), bb.minZ - 0.05 - z + (posZ - rm.renderPosZ), bb.maxX + 0.05 - x + (posX - rm.renderPosX), bb.maxY + 0.1 - y + (posY - rm.renderPosY), bb.maxZ + 0.05 - z + (posZ - rm.renderPosZ)));
    }

    public static void stopBoxRender() {
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GL11.glColor4d(1.0, 1.0, 1.0, 1.0);
    }

    public static void drawSoiledEntityESP(double x, double y, double z, double width, double height, int color) {
        GlStateManager.pushMatrix();
        pre3D();
        Wrapper.mc.entityRenderer.setupCameraTransform(Wrapper.mc.timer.renderPartialTicks, 2);
        glColor(color);
       drawnlbBoundingBox(new AxisAlignedBB(x - width, y, z - width, x + width, y + height, z + width));
        post3D();
        GlStateManager.popMatrix();
    }

    public static void drawnlbBoundingBox(AxisAlignedBB aa) {
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181705_e);
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.minX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.minZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.maxY, aa.maxZ).func_181675_d();
        worldRenderer.func_181662_b(aa.maxX, aa.minY, aa.maxZ).func_181675_d();
        tessellator.draw();
    }

    public static void resetColor() {
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static double interpolate(double current, double old, double scale) {
        return old + (current - old) * scale;
    }

    public static double interpolate(double old, double now, float partialTicks) {
        return old + (now - old) * (double)partialTicks;
    }

    public static float interpolate(float old, float now, float partialTicks) {
        return old + (now - old) * partialTicks;
    }

    public static void color(int color, float alpha) {
        float r = (float)(color >> 16 & 0xFF) / 255.0f;
        float g = (float)(color >> 8 & 0xFF) / 255.0f;
        float b = (float)(color & 0xFF) / 255.0f;
        GlStateManager.color(r, g, b, alpha);
    }

    public static void color(int color) {
        color(color, (float)(color >> 24 & 0xFF) / 255.0f);
    }

    public static void glColor(int hex) {
        float alpha = (float)(hex >> 24 & 0xFF) / 255.0f;
        float red = (float)(hex >> 16 & 0xFF) / 255.0f;
        float green = (float)(hex >> 8 & 0xFF) / 255.0f;
        float blue = (float)(hex & 0xFF) / 255.0f;
        GL11.glColor4f(red, green, blue, alpha);
    }

    public static void pre3D() {
        GL11.glPushMatrix();
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glShadeModel(7425);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glDisable(2929);
        GL11.glDisable(2896);
        GL11.glDepthMask(false);
        GL11.glHint(3154, 4354);
    }

    public static void post3D() {
        GL11.glDepthMask(true);
        GL11.glEnable(2929);
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glPopMatrix();
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
    }
}
