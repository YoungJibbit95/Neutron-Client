package me.youngjibbit.module.impl.render;

import me.youngjibbit.core.diagnostics.CorrectionHistory;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;

import java.util.List;

import static org.lwjgl.opengl.GL11.*;

/** Paints scalar snapshots inside the same HUD panel and GL-state boundary as the text display. */
final class DiagnosticsPlots {
    static final int HEIGHT = 78;
    private static final long WINDOW_NANOS = 30_000_000_000L;

    interface Plot {
        void draw(Theme theme, TTFFontRenderer font, int ink);
    }

    private DiagnosticsPlots() {}

    /** Pure presentation view: raw samples/history are never rewritten or fabricated. */
    static double[] easedTail(double[] source,float displayed) {
        double[] view=source==null?new double[0]:source.clone();
        if(view.length>0 && Double.isFinite(view[view.length-1])
                && view[view.length-1]>=0 && Float.isFinite(displayed) && displayed>=0)
            view[view.length-1]=displayed;
        return view;
    }

    static void series(double[] values, double floor, Theme theme, TTFFontRenderer font, int ink, String axis) {
        double maximum = floor;
        for (double sample : values) if (sample >= 0 && Double.isFinite(sample)) maximum = Math.max(maximum, sample);
        final float left = 37, right = 242, top = 28, bottom = 60;
        font.drawString(Integer.toString((int)Math.ceil(maximum)), 9, top - 2, ink);
        font.drawString("0", 9, bottom - 4, ink);
        font.drawString(axis, left, 65, ink);
        prepare();
        color(ink, .23f);
        glBegin(GL_LINES);
        segment(left, top, left, bottom);
        segment(left, bottom, right, bottom);
        glEnd();
        // Missing ping is -1. Keep the gap so that reconnects never look like zero latency.
        color(theme.getPrimaryGradientRGB(), 1);
        float previousX = 0, previousY = 0;
        boolean connected = false;
        glBegin(GL_LINES);
        for (int i = 0; i < values.length; i++) {
            double sample = values[i];
            if (sample < 0 || !Double.isFinite(sample)) {
                connected = false;
                continue;
            }
            float x = values.length == 1 ? right : left + (right-left) * i / (values.length-1);
            float y = bottom - (float)(sample / maximum) * (bottom-top);
            if (connected) segment(previousX, previousY, x, y);
            else segment(x, y, x+1, y);
            previousX = x;
            previousY = y;
            connected = true;
        }
        glEnd();
    }

    static void direction(float yaw, float pitch, Theme theme, TTFFontRenderer font, int ink) {
        final float cx = 34, cy = 47, radius = 16;
        font.drawString("N", cx-2, 19, ink);
        font.drawString("S", cx-2, 65, ink);
        font.drawString("Heading", 58, 44, ink);
        font.drawString("Pitch", 117, 44, ink);
        font.drawString("-90", 155, 58, ink);
        font.drawString("0", 199, 58, ink);
        font.drawString("+90", 229, 58, ink);
        prepare();
        color(ink, .35f);
        glBegin(GL_LINE_LOOP);
        for (int i = 0; i < 32; i++) {
            double angle = 2 * Math.PI * i / 32;
            glVertex2d(cx + radius*Math.cos(angle), cy + radius*Math.sin(angle));
        }
        glEnd();
        glBegin(GL_LINES);
        segment(cx-3, cy, cx+3, cy);
        segment(cx, cy-3, cx, cy+3);
        segment(160, cy, 240, cy);
        segment(200, cy-3, 200, cy+3);
        glEnd();
        if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
            double radians = Math.toRadians(yaw);
            // Minecraft yaw 0 points south; positive yaw turns west.
            float dx = (float)-Math.sin(radians), dy = (float)Math.cos(radians);
            float px = 200 + 40 * Math.max(-90, Math.min(90, pitch)) / 90;
            color(theme.getPrimaryGradientRGB(), 1);
            glBegin(GL_TRIANGLES);
            glVertex2f(cx+radius*dx, cy+radius*dy);
            glVertex2f(cx-3*dy-3*dx, cy+3*dx-3*dy);
            glVertex2f(cx+3*dy-3*dx, cy-3*dx-3*dy);
            glVertex2f(px, cy+1);
            glVertex2f(px-3, cy-5);
            glVertex2f(px+3, cy-5);
            glEnd();
        }
    }

    static void timeline(List<CorrectionHistory.Entry> entries, long now, Theme theme, TTFFontRenderer font, int ink) {
        final float left = 10, right = 242, baseline = 53;
        font.drawString("-30 s", left, 62, ink);
        font.drawString("Now", right-font.getWidth("Now"), 62, ink);
        prepare();
        color(ink, .32f);
        glBegin(GL_LINES);
        segment(left, baseline, right, baseline);
        for (int i = 0; i <= 6; i++) {
            float x = left + (right-left)*i/6;
            segment(x, baseline, x, baseline+3);
        }
        glEnd();
        color(theme.getPrimaryGradientRGB(), 1);
        glBegin(GL_QUADS);
        for (CorrectionHistory.Entry entry : entries) {
            long age = now-entry.nanos;
            if (age < 0 || age > WINDOW_NANOS) continue;
            float x = right-(right-left)*age/WINDOW_NANOS;
            float top = baseline - 17;
            glVertex2f(x-1, top);
            glVertex2f(x+1, top);
            glVertex2f(x+1, baseline);
            glVertex2f(x-1, baseline);
            glVertex2f(x, top-3);
            glVertex2f(x+3, top);
            glVertex2f(x, top+3);
            glVertex2f(x-3, top);
        }
        glEnd();
    }

    private static void prepare() {
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_CULL_FACE);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glLineWidth(1);
    }

    private static void color(int rgb, float alpha) {
        glColor4f(((rgb >>> 16) & 255)/255f, ((rgb >>> 8) & 255)/255f, (rgb & 255)/255f, alpha);
    }

    private static void segment(float x0, float y0, float x1, float y1) {
        glVertex2f(x0, y0);
        glVertex2f(x1, y1);
    }
}
