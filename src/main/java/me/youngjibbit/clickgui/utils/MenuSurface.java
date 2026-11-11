package me.youngjibbit.clickgui.utils;

import me.youngjibbit.gui.LiquidGlass;
import me.youngjibbit.utils.Theme;

/**
 * Local readable backing. The enclosing surface still owns glass, blur, glow and
 * theme material; this class only supplies the Neutron 2.0 card layers.
 */
public final class MenuSurface {
    private MenuSurface() { }

    public static void card(float x, float y, float width, float height, Theme theme,
                            boolean selected, boolean hovered) {
        card(x, y, width, height, theme, selected, hovered ? 1 : 0);
    }

    public static void card(float x, float y, float width, float height, Theme theme,
                            boolean selected, float hover) {
        LiquidGlass.card(x, y, width, height, 6, theme, selected, hover);
    }

    /** Shared field/chip plate for search boxes, value badges and compact controls. */
    public static void plate(float x, float y, float width, float height, Theme theme,
                             boolean active, float hover) {
        LiquidGlass.plate(x, y, width, height, 5, theme, active, hover);
    }
}
