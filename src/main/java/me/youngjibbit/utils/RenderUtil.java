package me.youngjibbit.utils;

import java.awt.Color;
import me.youngjibbit.utils.render.RUtil;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;

/**
 * Legacy entry points backed by the shared renderer. Explicit delegates preserve the original
 * overload set, endpoint/size conventions and rounding enum for existing callers.
 */
@Deprecated
public class RenderUtil {
    public static void drawImage(int x, int y, int width, int height, ResourceLocation image) { RUtil.drawImage(x, y, width, height, image); }
    public static void drawRoundedRect3(double x, double y, double x2, double y2, double radius, int color) { RUtil.drawRoundedRect3(x, y, x2, y2, radius, color); }
    public static void drawRoundedRect(double x, double y, double width, double height, double radius, int color) { RUtil.drawRoundedRect(x, y, width, height, radius, color); }
    public static void drawCircle(double x, double y, double radius, int c) { RUtil.drawCircle(x, y, radius, c); }
    public static void drawCheckMark(float x, float y, int width, int color) { RUtil.drawCheckMark(x, y, width, color); }
    public static void drawRoundedRectangle(double left, double top, double right, double bottom, double radius, int color) { RUtil.drawRoundedRectangle(left, top, right, bottom, radius, color); }
    public static void glDrawFilledEllipse(double x, double y, double radius, int startIndex, int endIndex, int polygons, boolean smooth, int colour) { RUtil.glDrawFilledEllipse(x, y, radius, startIndex, endIndex, polygons, smooth, colour); }
    public static void glDrawRoundedRectEllipse(double x, double y, double width, double height, RoundingMode roundingMode, int roundingDef, double roundingLevel, int colour) { RUtil.glDrawRoundedRectEllipse(x, y, width, height, RUtil.RoundingMode.valueOf(roundingMode.name()), roundingDef, roundingLevel, colour); }
    public static void drawUnfilledRectangle(double left, double top, double right, double bottom, double radius, int color) { RUtil.drawUnfilledRectangle(left, top, right, bottom, radius, color); }
    public static void drawFace(double x, double y, float u, float v, int uWidth, int vHeight, int width, int height, float tileWidth, float tileHeight, AbstractClientPlayer target) { RUtil.drawFace(x, y, u, v, uWidth, vHeight, width, height, tileWidth, tileHeight, target); }
    public static void drawUnfilledRectangle(double left, double top, double right, double bottom, double radius, int color, float lineWidth) { RUtil.drawUnfilledRectangle(left, top, right, bottom, radius, color, lineWidth); }
    public static void drawGradientRect(float x, float y, float x1, float y1, int topColor, int bottomColor) { RUtil.drawGradientRect(x, y, x1, y1, topColor, bottomColor); }
    public static void drawGradientSideways(double left, double top, double right, double bottom, int col1, int col2) { RUtil.drawGradientSideways(left, top, right, bottom, col1, col2); }
    public static void prepareScissorBox(float x, float y, float x2, float y2) { RUtil.prepareScissorBox(x, y, x2, y2); }
    public static void drawFilledCircle(int xx, int yy, float radius, Color color) { RUtil.drawFilledCircle(xx, yy, radius, color); }
    public static void drawUnfilledCircle(int xx, int yy, float radius, Color color) { RUtil.drawUnfilledCircle(xx, yy, radius, color); }
    public static void glColor(Color color) { RUtil.glColor(color); }
    public static boolean hover(int x, int y, int mouseX, int mouseY, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    public static void bindTexture(int texture) { RUtil.bindTexture(texture); }
    public static void glDrawFilledQuad(double x, double y, double width, double height, int colour) { RUtil.glDrawFilledQuad(x, y, width, height, colour); }
    public static void glDrawFramebuffer(int framebufferTexture, int width, int height) { RUtil.glDrawFramebuffer(framebufferTexture, width, height); }
    public static void glColour(int color) { RUtil.glColour(color); }
    public static boolean glEnableBlend() { return RUtil.glEnableBlend(); }
    public static void glRestoreBlend(boolean wasEnabled) { RUtil.glRestoreBlend(wasEnabled); }
    public static void glColor(int hex) { RUtil.glColor(hex); }
    public static void color(Color color) { RUtil.color(color); }
    public static void colorint(int r, int g, int b, int a) { RUtil.colorint(r, g, b, a); }
    public static Framebuffer createFrameBuffer(Framebuffer framebuffer) { return RUtil.createFrameBuffer(framebuffer); }
    public static void setAlphaLimit(float limit) { RUtil.setAlphaLimit(limit); }
    public static void resetColor() { RUtil.resetColor(); }
    public static boolean isEntityInFrustum(Entity entity) { return RUtil.isEntityInFrustum(entity); }
    public static double interpolate(double current, double old, double scale) { return RUtil.interpolate(current, old, scale); }
    public static float interpolateFloat(float current, float old, float scale) { return RUtil.interpolateFloat(current, old, scale); }
    public static void pre3D() { RUtil.pre3D(); }
    public static void post3D() { RUtil.post3D(); }

    public enum RoundingMode {
        TOP_LEFT, BOTTOM_LEFT, TOP_RIGHT, BOTTOM_RIGHT, LEFT, RIGHT, TOP, BOTTOM, FULL
    }
}
