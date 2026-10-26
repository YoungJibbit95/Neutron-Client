package me.youngjibbit.clickgui.utils;

import me.youngjibbit.Pulsar;
import me.youngjibbit.utils.font.TTFFontRenderer;

/** Shared geometry and metric-aware text for compact, 24px settings rows. */
public final class ClickControlStyle {
    private ClickControlStyle() { }
    public static TTFFontRenderer font() { return Pulsar.INSTANCE.getFontManager().getFont("Poppins-Regular 18"); }
    public static float right(float x, float width) { return x + width - 14; }
    public static float fieldWidth(float width) { return Math.max(24, Math.min(144, width * .47f)); }
    public static String fit(TTFFontRenderer font, String text, float width) {
        if (text == null || width <= 0) return "";
        if (font.getWidth(text) <= width) return text;
        String suffix = "...";
        if (font.getWidth(suffix) > width) return "";
        int end = text.length();
        while (end > 0 && font.getWidth(text.substring(0, end) + suffix) > width) end--;
        return text.substring(0, end) + suffix;
    }
    public static void label(String text, float x, float y, float available, int color) {
        font().drawString(fit(font(), text, available), x, y, color);
    }
}
