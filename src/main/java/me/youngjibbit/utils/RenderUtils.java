package me.youngjibbit.utils;

import me.youngjibbit.Pulsar;
import me.youngjibbit.module.impl.render.ESP;
import me.youngjibbit.utils.animations.ContinualAnimation;
import me.youngjibbit.utils.render.GuiShaderBloom;
import me.youngjibbit.utils.render.UIUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.AbstractClientPlayer;
import net.minecraft.client.gui.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.util.Vec3;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Timer;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import static org.lwjgl.opengl.GL11.*;

public class RenderUtils {
    public static final int WHITE = new Color(255, 255, 255).getRGB();
    private static Minecraft mc = Minecraft.getMinecraft();
    public static boolean ring_c = false;
    private static final float renderPartialTicks = 0.0f;

    public static void scissor(double x, double y, double width, double height) {
        final ScaledResolution sr = new ScaledResolution(mc);
        final double scale = sr.getScaleFactor();

        y = sr.getScaledHeight() - y;

        x *= scale;
        y *= scale;
        width *= scale;
        height *= scale;

        GL11.glScissor((int) x, (int) (y - height), (int) width, (int) height);
    }

    /** This will set the alpha limit to a specified value ranging from 0-1
     * @param limit a value between 0 and 1
     */
    public static void setAlphaLimit(float limit) {
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(GL_GREATER, (float) (limit * .01));
    }

    public static void renderMainMenuBackground(GuiScreen screen, ScaledResolution sr) {
        int topColor = (new Color(143, 148, 251)).getRGB();
        int bottomColor = (new Color(78, 84, 200)).getRGB();
        screen.drawGradientRect(0, 0, sr.getScaledWidth(), sr.getScaledHeight(), topColor, bottomColor);
    }

    public static void drawBoxAroundEntity(Entity e, int type, double expand, double shift, int color, boolean damage) {
        if (e instanceof EntityLivingBase) {
            double x = (e.lastTickPosX + ((e.posX - e.lastTickPosX) * (double) mc.timer.renderPartialTicks))
                    - mc.getRenderManager().viewerPosX;
            double y = (e.lastTickPosY + ((e.posY - e.lastTickPosY) * (double) mc.timer.renderPartialTicks))
                    - mc.getRenderManager().viewerPosY;
            double z = (e.lastTickPosZ + ((e.posZ - e.lastTickPosZ) * (double) mc.timer.renderPartialTicks))
                    - mc.getRenderManager().viewerPosZ;
            float d = (float) expand / 40.0F;
            if ((e instanceof EntityPlayer) && damage && (((EntityPlayer) e).hurtTime != 0))
                color = Color.RED.getRGB();

            GlStateManager.pushMatrix();
            int teamColor = -1; //getTeamColor((EntityPlayer) e);
            if (type == 3) {
                GL11.glTranslated(x, y - 0.2D, z);
                GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
                GlStateManager.disableDepth();
                GL11.glScalef(0.03F + d, 0.03F + d, 0.03F + d);
                int outline = Color.black.getRGB();

                Gui.drawRect(-20, -1, -26, 75, outline);
                Gui.drawRect(20, -1, 26, 75, outline);
                Gui.drawRect(-20, -1, 21, 5, outline);
                Gui.drawRect(-20, 70, 21, 75, outline);
                if (color != 0) {
                    Gui.drawRect(-21, 0, -25, 74, color);
                    Gui.drawRect(21, 0, 25, 74, color);
                    Gui.drawRect(-21, 0, 24, 4, color);
                    Gui.drawRect(-21, 71, 25, 74, color);
                } else {
                    int st = ColorUtil.rainbowDraw(2L, 0L);
                    int en = ColorUtil.rainbowDraw(2L, 1000L);
                    dGR(-21, 0, -25, 74, st, en);
                    dGR(21, 0, 25, 74, st, en);
                    Gui.drawRect(-21, 0, 21, 4, en);
                    Gui.drawRect(-21, 71, 21, 74, st);
                }

                GlStateManager.enableDepth();
            } else {
                int i;
                if (type == 4) {
                    EntityLivingBase en = (EntityLivingBase) e;
                    double r = en.getHealth() / en.getMaxHealth();
                    int b = (int) (74.0D * r);
                    int hc = r < 0.3D ? Color.red.getRGB()
                            : (r < 0.5D ? Color.orange.getRGB()
                            : (r < 0.7D ? Color.yellow.getRGB() : Color.green.getRGB()));
                    GL11.glTranslated(x, y - 0.2D, z);
                    GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
                    GlStateManager.disableDepth();
                    GL11.glScalef(0.03F + d, 0.03F + d, 0.03F + d);
                    i = (int) (21.0D + (shift * 2.0D));
                    Gui.drawRect(i, -1, i + 5, 75, Color.black.getRGB());
                    Gui.drawRect(i + 1, b, i + 4, 74, Color.darkGray.getRGB());
                    Gui.drawRect(i + 1, 0, i + 4, b, hc);
                    GlStateManager.enableDepth();
                } else if (type == 6)
                    d3p(x, y, z, 0.699999988079071D, 45, 1.5F, color, color == 0);
                else {
                    if (color == 0)
                        color = ColorUtil.rainbowDraw(2L, 0L);

                    float a = (float) ((color >> 24) & 255) / 255.0F;
                    float r = (float) ((color >> 16) & 255) / 255.0F;
                    float g = (float) ((color >> 8) & 255) / 255.0F;
                    float b = (float) (color & 255) / 255.0F;
                    if (type == 5) {
                        GL11.glTranslated(x, y - 0.2D, z);
                        GL11.glRotated(-mc.getRenderManager().playerViewY, 0.0D, 1.0D, 0.0D);
                        GlStateManager.disableDepth();
                        GL11.glScalef(0.03F + d, 0.03F, 0.03F + d);
                        d2p(0.0D, 95.0D, 10, 3, Color.black.getRGB());

                        for (i = 0; i < 6; ++i)
                            d2p(0.0D, 95 + (10 - i), 3, 4, Color.black.getRGB());

                        for (i = 0; i < 7; ++i)
                            d2p(0.0D, 95 + (10 - i), 2, 4, color);

                        d2p(0.0D, 95.0D, 8, 3, color);
                        GlStateManager.enableDepth();
                    } else {
                        AxisAlignedBB bbox = e.getEntityBoundingBox().expand(0.1D + expand, 0.1D + expand,
                                0.1D + expand);
                        AxisAlignedBB axis = new AxisAlignedBB((bbox.minX - e.posX) + x, (bbox.minY - e.posY) + y,
                                (bbox.minZ - e.posZ) + z, (bbox.maxX - e.posX) + x, (bbox.maxY - e.posY) + y,
                                (bbox.maxZ - e.posZ) + z);
                        GL11.glBlendFunc(770, 771);
                        GL11.glEnable(3042);
                        GL11.glDisable(3553);
                        GL11.glDisable(2929);
                        GL11.glDepthMask(false);
                        GL11.glLineWidth(2.0F);
                        GL11.glColor4f(r, g, b, a);
                        if (type == 1)
                            RenderGlobal.func_181561_a(axis);
                        else if (type == 2)
                            dbb(axis, r, g, b);
                        else if (type == 7)
                            dsbbt(axis, teamColor);

                        GL11.glEnable(3553);
                        GL11.glEnable(2929);
                        GL11.glDepthMask(true);
                        GL11.glDisable(3042);
                    }
                }
            }

            GlStateManager.popMatrix();
        }
    }

    public static void drawAxisAlignedBB(AxisAlignedBB axisAlignedBB, boolean outline, int color) {
        drawSelectionBoundingBox(axisAlignedBB, outline, !outline, color);
    }

    private static int getTeamColor(EntityPlayer player) {
        Scoreboard scoreboard = player.getWorldScoreboard();
        ScorePlayerTeam playerTeam = scoreboard.getPlayersTeam(player.getName());

        if (playerTeam != null) {
            String color = playerTeam.getColorPrefix();
            if (color.length() < 2) {
                return Color.WHITE.getRGB();
            }
            char colorChar = color.charAt(1);
            if (colorChar == '4' || colorChar == 'c') {
                return Color.RED.getRGB();
            }
            if (colorChar == '6' || colorChar == 'e') {
                return Color.YELLOW.getRGB();
            }
            if (colorChar == '2' || colorChar == 'a') {
                return Color.GREEN.getRGB();
            }
            if (colorChar == 'b' || colorChar == '3') {
                return Color.CYAN.getRGB();
            }
            if (colorChar == '9' || colorChar == '1') {
                return Color.BLUE.getRGB();
            }
            if (colorChar == 'd' || colorChar == '5') {
                return Color.MAGENTA.getRGB();
            }
            if (colorChar == 'f' || colorChar == '7') {
                return Color.WHITE.getRGB();
            }
            if (colorChar == '8' || colorChar == '0') {
                return Color.BLACK.getRGB();
            }
        }
        return Color.WHITE.getRGB();

    }

    private static void dGR(int left, int top, int right, int bottom, int startColor, int endColor) {
        int j;
        if (left < right) {
            j = left;
            left = right;
            right = j;
        }

        if (top < bottom) {
            j = top;
            top = bottom;
            bottom = j;
        }

        float f = (float) ((startColor >> 24) & 255) / 255.0F;
        float f1 = (float) ((startColor >> 16) & 255) / 255.0F;
        float f2 = (float) ((startColor >> 8) & 255) / 255.0F;
        float f3 = (float) (startColor & 255) / 255.0F;
        float f4 = (float) ((endColor >> 24) & 255) / 255.0F;
        float f5 = (float) ((endColor >> 16) & 255) / 255.0F;
        float f6 = (float) ((endColor >> 8) & 255) / 255.0F;
        float f7 = (float) (endColor & 255) / 255.0F;
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.shadeModel(7425);
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldRenderer.func_181662_b(right, top, 0.0D).func_181666_a(f1, f2, f3, f).func_181675_d();
        worldRenderer.func_181662_b(left, top, 0.0D).func_181666_a(f1, f2, f3, f).func_181675_d();
        worldRenderer.func_181662_b(left, bottom, 0.0D).func_181666_a(f5, f6, f7, f4).func_181675_d();
        worldRenderer.func_181662_b(right, bottom, 0.0D).func_181666_a(f5, f6, f7, f4).func_181675_d();
        tessellator.draw();
        GlStateManager.shadeModel(7424);
        GlStateManager.disableBlend();
        GlStateManager.enableAlpha();
        GlStateManager.enableTexture2D();
    }

    private static void d2p(double x, double y, int radius, int sides, int color) {
        float a = (float) ((color >> 24) & 255) / 255.0F;
        float r = (float) ((color >> 16) & 255) / 255.0F;
        float g = (float) ((color >> 8) & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(r, g, b, a);
        worldRenderer.func_181668_a(6, DefaultVertexFormats.field_181705_e);

        for (int i = 0; i < sides; ++i) {
            double angle = ((6.283185307179586D * (double) i) / (double) sides) + Math.toRadians(180.0D);
            worldRenderer.func_181662_b(x + (Math.sin(angle) * (double) radius), y + (Math.cos(angle) * (double) radius), 0.0D)
                    .func_181675_d();
        }

        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void dbb(AxisAlignedBB abb, float r, float g, float b) {
        //float a = 0.25F;
        ESP esp = (ESP) Pulsar.INSTANCE.getMm().getModule(ESP.class);

        Tessellator ts = Tessellator.getInstance();
        WorldRenderer worldRenderer = ts.getWorldRenderer();
        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        ts.draw();
        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        ts.draw();
        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        ts.draw();
        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        ts.draw();
        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        ts.draw();
        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181706_f);
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.minX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.minZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.maxY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        worldRenderer.func_181662_b(abb.maxX, abb.minY, abb.maxZ).func_181666_a(r, g, b, 225f).func_181675_d();
        ts.draw();
    }

    public static void drawSelectionBoundingBox(final AxisAlignedBB bb, final boolean outline, final boolean filled,int color) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GL11.glLineWidth(2.0F);
        GlStateManager.disableTexture2D();
        GL11.glDisable(2929);
        GlStateManager.depthMask(false);
        GlStateManager.pushMatrix();

        int red = (color >> 16) & 0xFF;
        int green = (color >> 8) & 0xFF;
        int blue = color & 0xFF;

        if (outline) {

            glEnable(GL_LINE_SMOOTH);

            dbb(bb, red, green, blue);

            glDisable(GL_LINE_SMOOTH);
        }
        if (filled) {
            dbb(bb, red, green, blue);
        }

        GlStateManager.popMatrix();
        GlStateManager.depthMask(true);
        GL11.glEnable(2929);
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private static void d3p(double x, double y, double z, double radius, int sides, float lineWidth, int color,
                            boolean chroma) {
        float a = (float) ((color >> 24) & 255) / 255.0F;
        float r = (float) ((color >> 16) & 255) / 255.0F;
        float g = (float) ((color >> 8) & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        mc.entityRenderer.disableLightmap();
        GL11.glDisable(3553);
        GL11.glEnable(3042);
        GL11.glBlendFunc(770, 771);
        GL11.glDisable(2929);
        GL11.glEnable(2848);
        GL11.glDepthMask(false);
        GL11.glLineWidth(lineWidth);
        if (!chroma)
            GL11.glColor4f(r, g, b, a);

        GL11.glBegin(1);
        long d = 0L;
        long ed = 15000L / (long) sides;
        long hed = ed / 2L;

        for (int i = 0; i < (sides * 2); ++i) {
            if (chroma) {
                if ((i % 2) != 0) {
                    if (i == 47)
                        d = hed;

                    d += ed;
                }

                int c = ColorUtil.rainbowDraw(2L, d);
                float r2 = (float) ((c >> 16) & 255) / 255.0F;
                float g2 = (float) ((c >> 8) & 255) / 255.0F;
                float b2 = (float) (c & 255) / 255.0F;
                GL11.glColor3f(r2, g2, b2);
            }

            double angle = ((6.283185307179586D * (double) i) / (double) sides) + Math.toRadians(180.0D);
            GL11.glVertex3d(x + (Math.cos(angle) * radius), y, z + (Math.sin(angle) * radius));
        }

        GL11.glEnd();
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        GL11.glDepthMask(true);
        GL11.glDisable(2848);
        GL11.glEnable(2929);
        GL11.glDisable(3042);
        GL11.glEnable(3553);
        mc.entityRenderer.enableLightmap();
    }

    private static void dsbbt(AxisAlignedBB var0, int teamColor) {
        Tessellator var1 = Tessellator.getInstance();
        WorldRenderer worldRenderer = var1.getWorldRenderer();

        // Set color based on teamColor
        float red = ((teamColor >> 16) & 0xFF) / 255.0f;
        float green = ((teamColor >> 8) & 0xFF) / 255.0f;
        float blue = (teamColor & 0xFF) / 255.0f;

        GlStateManager.color(red, green, blue, 1.0F); // Set the color

        // Draw bottom face
        worldRenderer.func_181668_a(3, DefaultVertexFormats.field_181705_e);
        worldRenderer.func_181662_b(var0.minX, var0.minY, var0.minZ).func_181675_d();
        worldRenderer.func_181662_b(var0.maxX, var0.minY, var0.minZ).func_181675_d();
        worldRenderer.func_181662_b(var0.maxX, var0.minY, var0.maxZ).func_181675_d();
        worldRenderer.func_181662_b(var0.minX, var0.minY, var0.maxZ).func_181675_d();
        worldRenderer.func_181662_b(var0.minX, var0.minY, var0.minZ).func_181675_d();
        var1.draw();

        // Draw top face
        worldRenderer.func_181668_a(3, DefaultVertexFormats.field_181705_e);
        worldRenderer.func_181662_b(var0.minX, var0.maxY, var0.minZ).func_181675_d();
        worldRenderer.func_181662_b(var0.maxX, var0.maxY, var0.minZ).func_181675_d();
        worldRenderer.func_181662_b(var0.maxX, var0.maxY, var0.maxZ).func_181675_d();
        worldRenderer.func_181662_b(var0.minX, var0.maxY, var0.maxZ).func_181675_d();
        worldRenderer.func_181662_b(var0.minX, var0.maxY, var0.minZ).func_181675_d();
        var1.draw();

        // Draw vertical edges
        worldRenderer.func_181668_a(1, DefaultVertexFormats.field_181705_e);
        worldRenderer.func_181662_b(var0.minX, var0.minY, var0.minZ).func_181675_d();
        worldRenderer.func_181662_b(var0.minX, var0.maxY, var0.minZ).func_181675_d();
        worldRenderer.func_181662_b(var0.maxX, var0.minY, var0.minZ).func_181675_d();
        worldRenderer.func_181662_b(var0.maxX, var0.maxY, var0.minZ).func_181675_d();
        worldRenderer.func_181662_b(var0.maxX, var0.minY, var0.maxZ).func_181675_d();
        worldRenderer.func_181662_b(var0.maxX, var0.maxY, var0.maxZ).func_181675_d();
        worldRenderer.func_181662_b(var0.minX, var0.minY, var0.maxZ).func_181675_d();
        worldRenderer.func_181662_b(var0.minX, var0.maxY, var0.maxZ).func_181675_d();
        var1.draw();

        // Reset color to default
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    public static void drawBox(Entity entity, Vec3 realPos, Vec3 lastPos, Color color) {
        final RenderManager renderManager = mc.getRenderManager();
        final Timer timer = mc.timer;

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glEnable(GL_BLEND);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glDepthMask(false);

        final double x = lastPos.xCoord + (realPos.xCoord - lastPos.xCoord) * timer.renderPartialTicks
                - renderManager.renderPosX;
        final double y = lastPos.yCoord + (realPos.yCoord - lastPos.yCoord) * timer.renderPartialTicks
                - renderManager.renderPosY;
        final double z = lastPos.zCoord + (realPos.zCoord - lastPos.zCoord) * timer.renderPartialTicks
                - renderManager.renderPosZ;

        final AxisAlignedBB entityBox = entity.getEntityBoundingBox();
        final AxisAlignedBB axisAlignedBB = new AxisAlignedBB(
                entityBox.minX - entity.posX + x - 0.05D,
                entityBox.minY - entity.posY + y,
                entityBox.minZ - entity.posZ + z - 0.05D,
                entityBox.maxX - entity.posX + x + 0.05D,
                entityBox.maxY - entity.posY + y + 0.15D,
                entityBox.maxZ - entity.posZ + z + 0.05D
        );


        glColor(color.getRed(), color.getGreen(), color.getBlue(), 35);
        drawFilledBox(axisAlignedBB);
        GlStateManager.resetColor();
        glDepthMask(true);
        glDisable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
    }


    public static void drawFilledBox(AxisAlignedBB axisAlignedBB) {
        final Tessellator tessellator = Tessellator.getInstance();
        final WorldRenderer worldRenderer = tessellator.getWorldRenderer();

        worldRenderer.func_181668_a(7, DefaultVertexFormats.field_181705_e);
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();

        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();

        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();

        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();

        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();

        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.minX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.minZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.maxY, axisAlignedBB.maxZ).func_181675_d();
        worldRenderer.func_181662_b(axisAlignedBB.maxX, axisAlignedBB.minY, axisAlignedBB.maxZ).func_181675_d();
        tessellator.draw();
    }

    public static void drawBoundingBox(AxisAlignedBB box) {
        Tessellator tessellator = Tessellator.getInstance();

        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.minZ);

        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);

        GL11.glVertex3d(box.minX, box.minY, box.minZ);
        GL11.glVertex3d(box.minX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.minZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.minZ);
        GL11.glVertex3d(box.maxX, box.minY, box.maxZ);
        GL11.glVertex3d(box.maxX, box.maxY, box.maxZ);
        GL11.glVertex3d(box.minX, box.minY, box.maxZ);
        GL11.glVertex3d(box.minX, box.maxY, box.maxZ);
        GL11.glEnd();
    }

    private static Class<?>[] getParameterTypes(Object[] args)
    {
        Class<?>[] parameterTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++)
        {
            parameterTypes[i] = args[i].getClass();
        }
        return parameterTypes;
    }

    public static void db(int w, int h, int r) {
        int c = r == -1 ? -1089466352 : r;
        net.minecraft.client.gui.Gui.drawRect(0, 0, w, h, c);
    }

    public static void drawArrow(float x, float y, int color, double width, double length) {
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        RenderUtils.glColor(color);
        GL11.glLineWidth((float) width);
        float halfWidth = (float) (width / 2.0);
        float xOffset = halfWidth / 2.0f;
        float yOffset = halfWidth / 2.0f;
        GL11.glBegin(GL11.GL_LINES);
        GL11.glVertex2d(x - xOffset, y + yOffset);
        GL11.glVertex2d(x + length - xOffset, y - length + yOffset);
        GL11.glVertex2d(x + length - xOffset, y - length + yOffset);
        GL11.glVertex2d(x + 2 * length - xOffset, y + yOffset);
        GL11.glEnd();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
        GL11.glPopMatrix();
    }

    public static void glColor(final int n) { // credit to the creator of raven b4
        GL11.glColor4f((float) (n >> 16 & 0xFF) / 255.0f, (float) (n >> 8 & 0xFF) / 255.0f, (float) (n & 0xFF) / 255.0f, (float) (n >> 24 & 0xFF) / 255.0f);
    }

    public static void drawRoundedGradientOutlinedRectangle(float n, float n2, float n3, float n4, final float n5, final int n6, final int n7, final int n8) { // credit to the creator of raven b4
        n *= 2.0;
        n2 *= 2.0;
        n3 *= 2.0;
        n4 *= 2.0;
        GL11.glPushAttrib(1);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glBegin(9);
        glColor(n6);
        for (int i = 0; i <= 90; i += 3) {
            final double n9 = (double) (i * 0.017453292f);
            GL11.glVertex2d((double) (n + n5) + Math.sin(n9) * n5 * -1.0, (double) (n2 + n5) + Math.cos(n9) * n5 * -1.0);
        }
        for (int j = 90; j <= 180; j += 3) {
            final double n10 = (double) (j * 0.017453292f);
            GL11.glVertex2d((double) (n + n5) + Math.sin(n10) * n5 * -1.0, (double) (n4 - n5) + Math.cos(n10) * n5 * -1.0);
        }
        for (int k = 0; k <= 90; k += 3) {
            final double n11 = (double) (k * 0.017453292f);
            GL11.glVertex2d((double) (n3 - n5) + Math.sin(n11) * n5, (double) (n4 - n5) + Math.cos(n11) * n5);
        }
        for (int l = 90; l <= 180; l += 3) {
            final double n12 = (double) (l * 0.017453292f);
            GL11.glVertex2d((double) (n3 - n5) + Math.sin(n12) * n5, (double) (n2 + n5) + Math.cos(n12) * n5);
        }
        GL11.glEnd();
        GL11.glPushMatrix();
        GL11.glShadeModel(7425);
        GL11.glLineWidth(2.0f);
        GL11.glBegin(2);
        if (n7 != 0L) {
            glColor(n7);
        }
        for (int n13 = 0; n13 <= 90; n13 += 3) {
            final double n14 = (double) (n13 * 0.017453292f);
            GL11.glVertex2d((double) (n + n5) + Math.sin(n14) * n5 * -1.0, (double) (n2 + n5) + Math.cos(n14) * n5 * -1.0);
        }
        for (int n15 = 90; n15 <= 180; n15 += 3) {
            final double n16 = (double) (n15 * 0.017453292f);
            GL11.glVertex2d((double) (n + n5) + Math.sin(n16) * n5 * -1.0, (double) (n4 - n5) + Math.cos(n16) * n5 * -1.0);
        }
        if (n8 != 0) {
            glColor(n8);
        }
        for (int n17 = 0; n17 <= 90; n17 += 3) {
            final double n18 = (double) (n17 * 0.017453292f);
            GL11.glVertex2d((double) (n3 - n5) + Math.sin(n18) * n5, (double) (n4 - n5) + Math.cos(n18) * n5);
        }
        for (int n19 = 90; n19 <= 180; n19 += 3) {
            final double n20 = (double) (n19 * 0.017453292f);
            GL11.glVertex2d((double) (n3 - n5) + Math.sin(n20) * n5, (double) (n2 + n5) + Math.cos(n20) * n5);
        }
        GL11.glEnd();
        GL11.glPopMatrix();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        GL11.glLineWidth(1.0f);
        GL11.glShadeModel(7424);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawRoundedRectangle(float n, float n2, float n3, float n4, final float n5, final int n6) {
        n *= 2.0;
        n2 *= 2.0;
        n3 *= 2.0;
        n4 *= 2.0;
        GL11.glPushAttrib(0);
        GL11.glScaled(0.5, 0.5, 0.5);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glEnable(2848);
        GL11.glBegin(9);
        glColor(n6);
        for (int i = 0; i <= 90; i += 3) {
            final double n7 = (double) (i * 0.017453292f);
            GL11.glVertex2d((double) (n + n5) + Math.sin(n7) * n5 * -1.0, (double) (n2 + n5) + Math.cos(n7) * n5 * -1.0);
        }
        for (int j = 90; j <= 180; j += 3) {
            final double n8 = (double) (j * 0.017453292f);
            GL11.glVertex2d((double) (n + n5) + Math.sin(n8) * n5 * -1.0, (double) (n4 - n5) + Math.cos(n8) * n5 * -1.0);
        }
        for (int k = 0; k <= 90; k += 3) {
            final double n9 = (double) (k * 0.017453292f);
            GL11.glVertex2d((double) (n3 - n5) + Math.sin(n9) * n5, (double) (n4 - n5) + Math.cos(n9) * n5);
        }
        for (int l = 90; l <= 180; l += 3) {
            final double n10 = (double) (l * 0.017453292f);
            GL11.glVertex2d((double) (n3 - n5) + Math.sin(n10) * n5, (double) (n2 + n5) + Math.cos(n10) * n5);
        }
        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void drawRoundedGradientRect(float x, float y, float x2, float y2, final float n5, final int n6, final int n7, final int n8, final int n9) {
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(2848);
        GL11.glShadeModel(7425);
        GL11.glPushAttrib(0);
        GL11.glScaled(0.5, 0.5, 0.5);
        x *= 2.0;
        y *= 2.0;
        x2 *= 2.0;
        y2 *= 2.0;
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        glColor(n6);
        GL11.glEnable(2848);
        GL11.glShadeModel(7425);
        GL11.glBegin(9);
        for (int i = 0; i <= 90; i += 3) {
            final double n10 = i * 0.017453292f;
            GL11.glVertex2d((double) (x + n5) + Math.sin(n10) * n5 * -1.0, (double) (y + n5) + Math.cos(n10) * n5 * -1.0);
        }
        glColor(n7);
        for (int j = 90; j <= 180; j += 3) {
            final double n11 = j * 0.017453292f;
            GL11.glVertex2d((double) (x + n5) + Math.sin(n11) * n5 * -1.0, (double) (y2 - n5) + Math.cos(n11) * n5 * -1.0);
        }
        glColor(n8);
        for (int k = 0; k <= 90; k += 3) {
            final double n12 = k * 0.017453292f;
            GL11.glVertex2d((double) (x2 - n5) + Math.sin(n12) * n5, (double) (y2 - n5) + Math.cos(n12) * n5);
        }
        glColor(n9);
        for (int l = 90; l <= 180; l += 3) {
            final double n13 = l * 0.017453292f;
            GL11.glVertex2d((double) (x2 - n5) + Math.sin(n13) * n5, (double) (y + n5) + Math.cos(n13) * n5);
        }
        GL11.glEnd();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glDisable(3042);
        GL11.glEnable(3553);
        GL11.glScaled(2.0, 2.0, 2.0);
        GL11.glPopAttrib();
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glDisable(2848);
        GL11.glShadeModel(7424);
    }

    public static int setAlpha(int rgb, double alpha) {
        if (alpha < 0 || alpha > 1) {
            alpha = 0.5;
        }

        int red = (rgb >> 16) & 0xFF;
        int green = (rgb >> 8) & 0xFF;
        int blue = rgb & 0xFF;

        int alphaInt = (int) (alpha * 255);

        int rgba = (alphaInt << 24) | (red << 16) | (green << 8) | blue;

        return rgba;
    }

    public static void glColor(int red, int green, int blue, int alpha) {
        GlStateManager.color(red / 255F, green / 255F, blue / 255F, alpha / 255F);
    }

    public static void drawImage2(ResourceLocation image, float x, float y, int width, int height,float alpha) {
        GlStateManager.disableAlpha();
        GlStateManager.disableRescaleNormal();
        GlStateManager.disableLighting();
        GL11.glDisable(2929);
        GL11.glEnable(3042);
        GL11.glDepthMask(false);
        OpenGlHelper.glBlendFunc(770, 771, 1, 0);
        GL11.glColor4f(1.0f, 1.0f, 1.0f, alpha);
        Minecraft.getMinecraft().getTextureManager().bindTexture(image);
        Gui.drawModalRectWithCustomSizedTexture((int) x, (int) y, 0.0f, 0.0f, width, height, width, height);
        GL11.glDepthMask(true);
        GL11.glEnable(2929);
    }


    public static int limit(double i) {
        if (i > 255.0) {
            return 255;
        }
        if (i < 0.0) {
            return 0;
        }
        return (int) i;
    }

    private static final int TOOLTIP_BACKGROUND = new Color(0, 0, 0, 100).getRGB();
    private static final int TOOLTIP_TEXT = new Color(229, 229, 229, 255).getRGB();


    public static void renderItemIcon(final double x, final double y, final ItemStack itemStack) {
        if (itemStack != null) {
            GlStateManager.pushMatrix();
            GlStateManager.enableRescaleNormal();
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            RenderHelper.enableGUIStandardItemLighting();

            mc.getRenderItem().renderItemAndEffectIntoGUI(itemStack, (int) x, (int) y);

            GlStateManager.disableRescaleNormal();
            GlStateManager.disableBlend();
            RenderHelper.disableStandardItemLighting();
            GlStateManager.popMatrix();
        }
    }
    public static void renderPlayer2D(float x, float y, float width, float height, AbstractClientPlayer player) {
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        mc.getTextureManager().bindTexture(player.getLocationSkin());
        Gui.drawScaledCustomSizeModalRect((int) x, (int) y, 8.0F, 8.0F, 8, 8, (int) width, (int) height, 64.0F, 64.0F);
        GlStateManager.disableBlend();
    }

    public static void drawBloomShadow(float x, float y, float width, float height, int blurRadius, Color color, boolean scissor) {
        drawBloomShadow(x, y, width, height, blurRadius, 0, color.getRGB(), scissor, false, false, false, false);
    }

    public static void drawBloomShadow(float x, float y, float width, float height, int blurRadius, int color, boolean scissor) {
        drawBloomShadow(x, y, width, height, blurRadius, 0, color, scissor, false, false, false, false);
    }

    public static void drawBloomShadow(float x, float y, float width, float height, int blurRadius, int roundRadius, int color, boolean scissor) {
        drawBloomShadow(x, y, width, height, blurRadius, roundRadius, color, scissor, false, false, false, false);
    }

    public static void drawGradientBloom(
            float x, float y, float width, float height,
            int blurRadius, int roundRadius, int baseColor,
            boolean scissor, boolean cutTop, boolean cutBottom,
            boolean cutLeft, boolean cutRight, int gradientStartColor,
            int gradientEndColor, ContinualAnimation continualAnimation, float angle) {
        long now = System.currentTimeMillis();
        float phase = (float) (((now % 2000L) / 2000.0D) + (angle / 360.0D));
        float blend = (float) (Math.sin(phase * Math.PI * 2.0D) * 0.5D + 0.5D);

        int animatedGradientColor = mixColor(gradientStartColor, gradientEndColor, blend);
        int finalColor = ((baseColor >>> 24) == 0) ? animatedGradientColor : mixColor(animatedGradientColor, baseColor, 0.5f);

        drawBloomShadow(x, y, width, height, blurRadius, roundRadius, finalColor, scissor, cutTop, cutBottom, cutLeft, cutRight);

        if (continualAnimation != null) {
            continualAnimation.animate(1.0f, 1000);
        }
    }

    public static void drawBloomShadow(float x, float y, float width, float height, int blurRadius, int roundRadius, int color, boolean scissor, boolean cut_top, boolean cut_bottom, boolean cut_left, boolean cut_right) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        float expandedX = x - blurRadius + 0.5f;
        float expandedY = y - blurRadius + 0.5f;
        float expandedWidth = width + blurRadius * 2.0f;
        float expandedHeight = height + blurRadius * 2.0f;

        if (cut_top) {
            expandedY += blurRadius;
            expandedHeight -= blurRadius;
        }
        if (cut_bottom) {
            expandedHeight -= blurRadius;
        }
        if (cut_left) {
            expandedX += blurRadius;
            expandedWidth -= blurRadius;
        }
        if (cut_right) {
            expandedWidth -= blurRadius;
        }

        if (expandedWidth <= 0.0f || expandedHeight <= 0.0f) {
            return;
        }

        int shaderIterations = Math.max(1, Math.min(6, blurRadius / 4 + 1));
        int shaderOffset = Math.max(1, Math.min(5, blurRadius / 6 + 1));
        int shaderRadius = Math.max(0, roundRadius);

        if (GuiShaderBloom.isBatching()) {
            GuiShaderBloom.queueRoundedRect(expandedX, expandedY, expandedWidth, expandedHeight, shaderRadius, color);
        } else {
            GuiShaderBloom.renderRect(expandedX, expandedY, expandedWidth, expandedHeight, shaderRadius, color, shaderIterations, shaderOffset);
        }
    }

    private static int mixColor(int first, int second, float ratio) {
        float clampedRatio = Math.max(0.0f, Math.min(1.0f, ratio));
        float inverse = 1.0f - clampedRatio;

        int a = (int) (((first >>> 24) & 0xFF) * inverse + ((second >>> 24) & 0xFF) * clampedRatio);
        int r = (int) (((first >>> 16) & 0xFF) * inverse + ((second >>> 16) & 0xFF) * clampedRatio);
        int g = (int) (((first >>> 8) & 0xFF) * inverse + ((second >>> 8) & 0xFF) * clampedRatio);
        int b = (int) ((first & 0xFF) * inverse + (second & 0xFF) * clampedRatio);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void color(int color) {
        float f = (color >> 24 & 0xFF) / 255.0F;
        float f1 = (color >> 16 & 0xFF) / 255.0F;
        float f2 = (color >> 8 & 0xFF) / 255.0F;
        float f3 = (color & 0xFF) / 255.0F;
        GL11.glColor4f(f1, f2, f3, f);
    }

    public static void drawImage(int image, float x, float y, float width, float height, int color) {
        enableGL2D();
        glPushMatrix();
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.01f);
        glEnable(GL11.GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glEnable(GL11.GL_ALPHA_TEST);
        GlStateManager.enableBlend();
        GlStateManager.bindTexture(image);

        color(color);

        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); // top left
        GL11.glVertex2f(x, y);

        GL11.glTexCoord2f(0, 1); // bottom left
        GL11.glVertex2f(x, y + height);

        GL11.glTexCoord2f(1, 1); // bottom right
        GL11.glVertex2f(x + width, y + height);

        GL11.glTexCoord2f(1, 0); // top right
        GL11.glVertex2f(x + width, y);
        GL11.glEnd();

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
        GlStateManager.resetColor();

        glEnable(GL_CULL_FACE);
        glPopMatrix();
        disableGL2D();
    }

    public static void enableGL2D() {
        GL11.glDisable(2929);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glBlendFunc(770, 771);
        GL11.glDepthMask(true);
        GL11.glEnable(2848);
        GL11.glHint(3154, 4354);
        GL11.glHint(3155, 4354);
    }

    public static void disableGL2D() {
        GL11.glEnable(3553);
        GL11.glDisable(3042);
        GL11.glEnable(2929);
        GL11.glDisable(2848);
        GL11.glHint(3154, 4352);
        GL11.glHint(3155, 4352);
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer) {
        return createFrameBuffer(framebuffer, false);
    }

    public static Framebuffer createFrameBuffer(Framebuffer framebuffer, boolean depth) {
        if (needsNewFramebuffer(framebuffer)) {
            if (framebuffer != null) {
                framebuffer.deleteFramebuffer();
            }
            return new Framebuffer(mc.displayWidth, mc.displayHeight, depth);
        }
        return framebuffer;
    }

    public static boolean needsNewFramebuffer(Framebuffer framebuffer) {
        return framebuffer == null || framebuffer.framebufferWidth != mc.displayWidth || framebuffer.framebufferHeight != mc.displayHeight;
    }

    /**
     * Bind a texture using the specified integer refrence to the texture.
     *
     * @see org.lwjgl.opengl.GL13 for more information about texture bindings
     */
    public static void bindTexture(int texture) {
        glBindTexture(GL_TEXTURE_2D, texture);
    }
}
