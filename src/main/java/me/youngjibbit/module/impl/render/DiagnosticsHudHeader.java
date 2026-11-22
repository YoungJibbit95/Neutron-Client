package me.youngjibbit.module.impl.render;

import me.youngjibbit.core.activity.InputClickJournal;
import me.youngjibbit.gui.LegacyUiStyle;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.utils.render.RenderState;
import me.youngjibbit.utils.render.UIUtils;
import java.awt.Color;

/** Presentation of the cached performance sample and the real mouse button-down counts. */
final class DiagnosticsHudHeader {
    final String fps, ping, cps;

    DiagnosticsHudHeader(int fps, int ping, InputClickJournal.Snapshot clicks) {
        this.fps = fps > 0 ? Integer.toString(fps) : "--";
        this.ping = ping >= 0 ? ping + " ms" : "--";
        this.cps = clicks == null ? "--" : clicks.left + " / " + clicks.right;
    }

    void draw(TTFFontRenderer heading, TTFFontRenderer body, Theme theme, int ink, int muted, float scale) {
        int accent = theme.getPrimaryGradientRGB();
        try (RenderState ignored = new RenderState()) {
            CompactHudVisuals.begin(scale);
            for (int bar = 0; bar < 4; bar++) {
                float x = 11 + 3 * bar;
                CompactHudVisuals.line(x, 16, x, 14 - 2 * bar, 1.7f, accent, 1);
            }
            CompactHudVisuals.line(78, 8, 78, 17, 1, muted, .35f);
            CompactHudVisuals.line(151, 8, 151, 17, 1, muted, .35f);
            // Split buttons and a rounded shell keep the mouse distinct from the performance bars.
            CompactHudVisuals.arc(163, 10, 3.8f, .75f, .5f, 1.1f, accent, 1);
            CompactHudVisuals.arc(163, 14, 3.8f, .25f, .5f, 1.1f, accent, 1);
            CompactHudVisuals.line(159.2f, 10, 159.2f, 14, 1.1f, accent, 1);
            CompactHudVisuals.line(166.8f, 10, 166.8f, 14, 1.1f, accent, 1);
            CompactHudVisuals.line(163, 6.5f, 163, 11, 1, accent, 1);
            CompactHudVisuals.line(159.5f, 11, 166.5f, 11, 1, accent, 1);
        }
        metric(heading, body, "FPS", fps, 26, 73, ink, muted);
        metric(heading, body, "Ping", ping, 84, 146, ink, muted);
        metric(heading, body, "CPS", cps, 173, 243, ink, muted);
    }

    private static void metric(TTFFontRenderer heading, TTFFontRenderer body, String label, String value,
                               float start, float end, int ink, int muted) {
        float valueX = start + body.getWidth(label) + 4;
        body.drawString(label, start, 12 - body.getLineHeight() / 2, muted);
        heading.drawString(Diagnostics.fit(value, heading, end - valueX), valueX, 12 - heading.getLineHeight() / 2, ink);
    }

    static void correction(String status, TTFFontRenderer heading, TTFFontRenderer body, Theme theme, int ink, int muted) {
        LegacyUiStyle.icon("armor", 10, 5, 14, theme.getPrimaryGradientRGB());
        heading.drawString("Local corrections", 29, 12 - heading.getLineHeight() / 2, ink);
        String label;
        int indicator;
        switch (status) {
            case "CORRECTION": label = "Observed"; indicator = 0xffffb665; break;
            case "LISTENING": label = "Listening"; indicator = 0xff78ceb7; break;
            case "SYNC": label = "Sync"; indicator = theme.getPrimaryGradientRGB(); break;
            default: label = "Off"; indicator = muted; break;
        }
        float labelX = 243 - body.getWidth(label);
        UIUtils.drawDecorationRound(labelX - 9, 10, 4, 4, 2, new Color(indicator, true));
        body.drawString(label, labelX, 12 - body.getLineHeight() / 2, muted);
    }
}
