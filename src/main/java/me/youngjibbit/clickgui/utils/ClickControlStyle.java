package me.youngjibbit.clickgui.utils;

import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
import me.youngjibbit.utils.font.TTFFontRenderer;
import me.youngjibbit.gui.LegacyUiStyle;
import me.youngjibbit.gui.LiquidGlass;
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
        if (width <= 20 || height <= 2) return;
        float radius = LiquidGlass.radius(4, width - 20, height);
        float opacity = UiEffects.options().surfaceOpacity;
        UIUtils.drawExactDecorationRound(x + 10, y, Math.max(0, width - 20), height, radius,
                LiquidGlass.fill(theme, Math.round((active ? 82 : 26) * opacity)));
        if (active) LiquidGlass.highlight(x + 10, y, width - 20, height, radius, opacity);
    }
    /** A hairline and a tinted fill inside the stronger outer module contour. */
    public static void panelField(float x, float y, float width, float height, Theme theme, boolean active) {
        // Shared glass plate: the same fill, top light and hairline as the Settings
        // screen, so value badges no longer fall back to an opaque control well.
        LiquidGlass.plate(x, y, width, height, 4, theme, active, 0);
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
