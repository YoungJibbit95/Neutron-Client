package me.youngjibbit.clickgui.utils;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import java.awt.Color;

/** Uses the existing rounded gradient shader; the inset keeps accent color out of the fill. */
public final class GuiStyle {
    private GuiStyle() { }

    public static void panel(float x, float y, float width, float height, float radius,
                             float accent, Color fill) {
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        Color neutral = new Color(58, 58, 60);
        Color left = new Color(Theme.interpolateRGB(neutral.getRGB(), theme.getPrimaryGradientRGB(), accent), true);
        left=me.youngjibbit.utils.render.UiEffects.accent(left.getRGB(),theme.isCustom()?theme.getOutlineStrength():1);
        UIUtils.drawRoundOutline(x,y,width,height,radius,theme.isCustom()?theme.getOutlineWidth():1,fill,left,accent);
    }
}
