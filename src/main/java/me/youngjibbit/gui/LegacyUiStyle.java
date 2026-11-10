package me.youngjibbit.gui;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import java.awt.Color;
import java.util.Locale;
import org.lwjgl.opengl.GL11;

/** Shared Legacy chrome. Uses existing material controls; icons allocate no textures or framebuffers. */
public final class LegacyUiStyle {
    private LegacyUiStyle() { }
    public static final int INK = 0xffedf2f8, MUTED = 0xffaab6c7, FAINT = 0xff7f8da2;
    public static final int PREFIX = 0xffc6c6c6;
    public static final float FRAME_RADIUS = 9, CONTROL_RADIUS = 5;

    public static int ink(Theme theme) {
        return NeutronVisualTokens.ink(theme);
    }
    public static Color alpha(int argb, float opacity) {
        return new Color((argb & 0xffffff) | (Math.round(Math.max(0, Math.min(1, opacity)) * 255) << 24), true);
    }
    public static Color accent(Theme theme, float opacity) { return alpha(theme.getPrimaryGradientRGB(), opacity); }
    public static Color panel(Theme theme) {
        return new Color(Theme.interpolateRGB(0xff171d28, theme.getBackground().getRGB(), .12f), true);
    }
    public static Color control(Theme theme, boolean active) {
        return new Color(Theme.interpolateRGB(0xff252e3d, theme.getPrimaryGradientRGB(), active ? .13f : .025f), true);
    }
    public static Color hairline(Theme theme) { return alpha(ink(theme), .1f); }
    public static void rule(float x, float y, float width, Theme theme) {
        if (width > 0) UIUtils.drawDecorationRound(x, y, width, .7f, .35f, hairline(theme));
    }
    public static void card(float x, float y, float width, float height, Theme theme, boolean selected, boolean hover) {
        if (width <= 0 || height <= 0) return;
        UIUtils.drawRound(x, y, width, height, CONTROL_RADIUS,
                selected ? control(theme, true) : hover ? new Color(45, 55, 71) : control(theme, false));
        if (selected) UIUtils.drawDecorationRound(x + 1, y + 7, 2, Math.max(0, height - 14), 1, accent(theme, .9f));
    }

    /** Small stroke icons share the control ink and remain independent of installed icon fonts. */
    public static void icon(String name, float x, float y, float size, int color) {
        if (name == null || !(size > 0)) return;
        try (me.youngjibbit.utils.render.RenderState state = new me.youngjibbit.utils.render.RenderState()) {
            // One geometry batch per icon; avoid direction-dependent SDF bounds and per-stroke state snapshots.
            org.lwjgl.opengl.GL20.glUseProgram(0);
            for (int i=0;i<5;i++) {
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + (i==4?16:i));
                GL11.glDisable(GL11.GL_TEXTURE_2D);
            }
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            GL11.glDisable(GL11.GL_DEPTH_TEST); GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_LIGHTING); GL11.glDisable(GL11.GL_CULL_FACE); GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_BLEND); GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
            org.lwjgl.opengl.GL14.glBlendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
            GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glTranslatef(x, y, 0); GL11.glScalef(size / 16, size / 16, 1);
            Color c = new Color(color, true);
            GL11.glBegin(GL11.GL_TRIANGLES);
            try {
            switch (name.toLowerCase(Locale.ROOT)) {
                case "save":
                    box(2, 2, 12, 12, c); box(5, 2, 6, 4, c); box(5, 9, 6, 5, c); break;
                case "load": case "download":
                    line(8, 1, 8, 10, c); line(4, 7, 8, 11, c); line(8, 11, 12, 7, c);
                    line(2, 10, 2, 14, c); line(2, 14, 14, 14, c); line(14, 10, 14, 14, c); break;
                case "rename": case "edit":
                    line(3, 12, 11, 4, c); line(5, 14, 13, 6, c); line(11, 4, 13, 6, c);
                    line(3, 12, 3, 14, c); line(3, 14, 5, 14, c); line(8, 14, 14, 14, c); break;
                case "delete":
                    line(3, 4, 13, 4, c); line(6, 2, 10, 2, c); line(4, 5, 5, 14, c);
                    line(5, 14, 11, 14, c); line(11, 14, 12, 5, c); line(7, 7, 7, 11, c); line(9, 7, 9, 11, c); break;
                case "refresh": case "reset":
                    line(3, 5, 6, 2, c); line(6, 2, 12, 3, c); line(12, 3, 14, 7, c);
                    line(14, 3, 14, 7, c); line(10, 7, 14, 7, c);
                    line(13, 11, 10, 14, c); line(10, 14, 4, 13, c); line(4, 13, 2, 9, c); break;
                case "duplicate": case "copy":
                    box(2, 2, 9, 9, c); box(6, 6, 8, 8, c); break;
                case "search":
                    box(2, 2, 8, 8, c); line(9, 9, 14, 14, c); break;
                case "back":
                    line(11, 3, 6, 8, c); line(6, 8, 11, 13, c); break;
                case "close":
                    line(4, 4, 12, 12, c); line(12, 4, 4, 12, c); break;
                case "check":
                    line(3, 8, 6, 11, c); line(6, 11, 13, 4, c); break;
                case "chevron": case "down":
                    line(4, 6, 8, 10, c); line(8, 10, 12, 6, c); break;
                case "palette": case "theme":
                    box(2, 2, 12, 12, c); dot(5, 5, c); dot(10, 5, c); dot(5, 10, c); dot(10, 10, c); break;
                case "settings":
                    for (int i = 0; i < 3; i++) { float yy = 3 + i * 5; line(2, yy, 14, yy, c); box(i == 1 ? 9 : 4, yy - 1.5f, 3, 3, c); } break;
                case "user": case "accounts":
                    box(5, 2, 6, 5, c); line(2, 14, 3, 10, c); line(3, 10, 13, 10, c); line(13, 10, 14, 14, c); break;
                case "plus": case "new":
                    line(8, 2, 8, 14, c); line(2, 8, 14, 8, c); break;
                default:
                    box(2, 2, 5, 5, c); box(9, 2, 5, 5, c); box(2, 9, 5, 5, c); box(9, 9, 5, 5, c);
            }
            } finally { GL11.glEnd(); }
        }
    }
    private static void dot(float x, float y, Color color) { disc(x,y,1,color); }
    private static void box(float x, float y, float w, float h, Color c) {
        line(x, y, x + w, y, c); line(x + w, y, x + w, y + h, c);
        line(x + w, y + h, x, y + h, c); line(x, y + h, x, y, c);
    }
    private static void line(float x1, float y1, float x2, float y2, Color color) {
        float length = (float)Math.hypot(x2 - x1, y2 - y1);
        if (length <= 0) return;
        float nx=-(y2-y1)/length,ny=(x2-x1)/length;
        band(x1,y1,x2,y2,nx,ny,-.55f,.55f,1,1,color);
        band(x1,y1,x2,y2,nx,ny,.55f,.9f,1,0,color);
        band(x1,y1,x2,y2,nx,ny,-.9f,-.55f,0,1,color);
        disc(x1,y1,.55f,color);disc(x2,y2,.55f,color);
    }
    private static final float[] CIRCLE_X={1,.70710678f,0,-.70710678f,-1,-.70710678f,0,.70710678f,1};
    private static final float[] CIRCLE_Y={0,.70710678f,1,.70710678f,0,-.70710678f,-1,-.70710678f,0};
    private static void band(float x1,float y1,float x2,float y2,float nx,float ny,float inner,float outer,float a,float b,Color c) {
        vertex(x1+nx*inner,y1+ny*inner,c,a);vertex(x2+nx*inner,y2+ny*inner,c,a);vertex(x2+nx*outer,y2+ny*outer,c,b);
        vertex(x1+nx*inner,y1+ny*inner,c,a);vertex(x2+nx*outer,y2+ny*outer,c,b);vertex(x1+nx*outer,y1+ny*outer,c,b);
    }
    private static void disc(float x,float y,float r,Color c) {
        for(int i=0;i<8;i++) {
            float ax=CIRCLE_X[i],ay=CIRCLE_Y[i],bx=CIRCLE_X[i+1],by=CIRCLE_Y[i+1];
            vertex(x,y,c,1);vertex(x+ax*r,y+ay*r,c,1);vertex(x+bx*r,y+by*r,c,1);
            vertex(x+ax*r,y+ay*r,c,1);vertex(x+ax*(r+.35f),y+ay*(r+.35f),c,0);vertex(x+bx*(r+.35f),y+by*(r+.35f),c,0);
            vertex(x+ax*r,y+ay*r,c,1);vertex(x+bx*(r+.35f),y+by*(r+.35f),c,0);vertex(x+bx*r,y+by*r,c,1);
        }
    }
    private static void vertex(float x,float y,Color c,float alpha) {
        GL11.glColor4f(c.getRed()/255f,c.getGreen()/255f,c.getBlue()/255f,c.getAlpha()/255f*alpha);
        GL11.glVertex2f(x,y);
    }
}
