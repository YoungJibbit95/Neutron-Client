package me.youngjibbit.clickgui.utils;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import java.awt.Color;

/** Local readable backing. The enclosing surface still owns glass, blur, glow and theme material. */
public final class MenuSurface {
    private MenuSurface() { }

    public static void card(float x, float y, float width, float height, Theme theme,
                            boolean selected, boolean hovered) {
        card(x, y, width, height, theme, selected, hovered ? 1 : 0);
    }

    public static void card(float x, float y, float width, float height, Theme theme,
                            boolean selected, float hover) {
        if (width <= 0 || height <= 0) return;
        float amount = Math.max(0, Math.min(1, hover));
        int base = Theme.interpolateRGB(0xff18202c, theme.getBackground().getRGB() | 0xff000000, .14f);
        int fill = Theme.interpolateRGB(base, selected ? theme.getPrimaryGradientRGB() : 0xffc6d0df,
                selected ? .11f + amount * .035f : amount * .045f);
        UIUtils.drawDecorationRound(x, y, width, height, Math.min(6, Math.min(width, height) / 2),
                new Color((fill & 0xffffff) | 0xe3000000, true));
        if (selected) UIUtils.drawDecorationRound(x + 1, y + 5, 2, Math.max(1, height - 10), 1,
                new Color((theme.getPrimaryGradientRGB() & 0xffffff) | 0xb8000000, true));
    }
}
