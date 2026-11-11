package me.youngjibbit.module.impl.render;

import me.youngjibbit.core.diagnostics.CorrectionHistory;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.UiEffects;

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
        double maximum = seriesMaximum(values, floor);
        int count = values == null ? 0 : values.length;
        boolean hasSamples = false;
        if (values != null) for (double sample : values) hasSamples |= Diagnostics.graphSampleValid(sample);
        final float left = 37, right = 242, top = 28, bottom = 60;
        font.drawString(Integer.toString((int)Math.ceil(maximum)), 9, top - 2, ink);
        font.drawString("0", 9, bottom - 4, ink);
        font.drawString(axis, left, 65, ink);
        if (!hasSamples) font.drawString("No samples", left + 62, top + 11, ink);
        prepare();
        color(ink, .1f);
        glBegin(GL_LINES);
        segment(left, top, right, top);
        segment(left, (top + bottom) / 2, right, (top + bottom) / 2);
        glEnd();
        color(ink, .23f);
        glBegin(GL_LINES);
        segment(left, top, left, bottom);
        segment(left, bottom, right, bottom);
        glEnd();
        // Missing ping is -1. Keep the gap so that reconnects never look like zero latency.
        float previousX = 0, previousY = 0;
        int previousColor = theme.getPrimaryGradientRGB();
        boolean connected = false;
        glBegin(GL_LINES);
        for (int i = 0; i < count; i++) {
            double sample = values[i];
            if (sample < 0 || !Double.isFinite(sample)) {
                connected = false;
                continue;
            }
            float progress = count == 1 ? 1 : i / (float)(count - 1);
            float x = left + (right-left) * progress;
            float y = bottom - (float)(sample / maximum) * (bottom-top);
            int rgb = UiEffects.options().gradients ? Theme.interpolateRGB(theme.getPrimaryGradientRGB(),
                    theme.getSecondaryGradientRGB(), progress) : theme.getPrimaryGradientRGB();
            if (connected) {
                color(previousColor, 1); glVertex2f(previousX, previousY);
                color(rgb, 1); glVertex2f(x, y);
            } else {
                color(rgb, 1); segment(Math.max(left, x - 1), y, Math.min(right, x + 1), y);
            }
            previousX = x;
            previousY = y;
            previousColor = rgb;
            connected = true;
        }
        glEnd();
    }

    /** A zero/empty series always has a finite positive range; absent samples stay gaps. */
    static double seriesMaximum(double[] values, double floor) {
        double maximum = Double.isFinite(floor) && floor > 0 ? floor : 1;
        if (values != null) for (double sample : values)
            if (Diagnostics.graphSampleValid(sample)) maximum = Math.max(maximum, sample);
        return maximum;
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

    static void prepare() {
        // The caller owns a RenderState scope. Fixed-function plots cannot inherit
        // a shader or an enabled secondary texture unit from world/UI rendering.
        org.lwjgl.opengl.GL20.glUseProgram(0);
        for (int unit = 0; unit < 5; unit++) {
            org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + (unit == 4 ? 16 : unit));
            glDisable(GL_TEXTURE_2D);
        }
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        glDisable(GL_CULL_FACE);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_ALPHA_TEST);
        glDisable(GL_LIGHTING);
        glDisable(GL_FOG);
        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        org.lwjgl.opengl.GL14.glBlendEquation(org.lwjgl.opengl.GL14.GL_FUNC_ADD);
        glShadeModel(GL_SMOOTH);
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
