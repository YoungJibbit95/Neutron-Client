package me.youngjibbit.clickgui.utils;

import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.gui.LegacyUiStyle;
import me.youngjibbit.theme.ThemeMode;

/** Shared geometry and metric-aware text for compact, 24px settings rows. */
public final class ClickControlStyle {
    private ClickControlStyle() { }
    public static TTFFontRenderer font() { return Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18"); }
    public static float right(float x, float width) { return x + width - 14; }
    public static float fieldWidth(float width) { return Math.max(24, Math.min(144, width * .47f)); }
    public static boolean legacy() { return !ThemeMode.APPROACH_TWO.equals(ThemeMode.selected()); }
    public static int ink(Theme theme) { return legacy() ? LegacyUiStyle.ink(theme) : theme.getTextColor().getRGB(); }
    /** Settings stay visually quieter than module cards, with a stable hover/focus lane. */
    public static void row(float x, float y, float width, float height, Theme theme, boolean active) {
        if (!legacy() || !active) return;
        UIUtils.drawDecorationRound(x + 10, y, Math.max(0, width - 20), height, 4,
                LegacyUiStyle.alpha(LegacyUiStyle.ink(theme), .035f));
    }
    /** A hairline and a tinted fill inside the stronger outer module contour. */
    public static void panelField(float x, float y, float width, float height, Theme theme, boolean active) {
        if (legacy()) {
            UIUtils.drawDecorationRound(x, y, width, height, 4,
                    LegacyUiStyle.alpha(active ? theme.getPrimaryGradientRGB() : LegacyUiStyle.ink(theme), active ? .54f : .14f));
            UIUtils.drawDecorationRound(x + .65f, y + .65f, Math.max(0, width - 1.3f),
                    Math.max(0, height - 1.3f), 3.5f, LegacyUiStyle.control(theme, active));
            return;
        }
        int ink = theme.getTextColor().getRGB();
        int background = Theme.interpolateRGB(theme.getBackground().getRGB(), ink, .085f);
        float inset = 0;
        if (UiEffects.options().border) {
            inset = theme.isCustom() ? Math.min(.65f, theme.getOutlineWidth() * .55f) : .65f;
            UIUtils.drawDecorationRound(x, y, width, height, 4,
                    UiEffects.accent(active ? theme.getPrimaryGradientRGB() : ink,
                            (active ? .7f : .25f) * UiEffects.options().borderOpacity * theme.getOutlineStrength()));
        }
        UIUtils.drawDecorationRound(x + inset, y + inset, width - 2 * inset, height - 2 * inset,
                4, UiEffects.accent(background, .98f));
    }

    /** A local halo stays inside the row clip and needs no extra framebuffer pass. */
    public static void halo(float x, float y, float width, float height, Theme theme, float strength) {
        strength *= theme.getGlowStrength();
        if (strength <= .001f || !UiEffects.glowEnabled()) return;
        float spread = Math.min(3, Math.min(theme.getGlowRadius(), UiEffects.glowRadius(8)) * .25f);
        if (spread <= 0) return;
        UIUtils.drawDecorationRound(x - spread, y - spread, width + 2 * spread, height + 2 * spread,
                height / 2 + spread, UiEffects.accent(theme.getPrimaryGradientRGB(), UiEffects.glowAlpha(strength)));
    }

    public static String fit(TTFFontRenderer font, String text, float width) {
        if (text == null || width <= 0) return "";
        if (font.getWidth(text) <= width) return text;
        String suffix = "...";
        if (font.getWidth(suffix) > width) return "";
        int end = text.length();
        while (end > 0 && font.getWidth(text.substring(0, end) + suffix) > width) end=text.offsetByCodePoints(end,-1);
        return text.substring(0, end) + suffix;
    }
    public static void label(String text, float x, float y, float available, int color) {
        font().drawString(fit(font(), text, available), x, y, color);
    }
}
