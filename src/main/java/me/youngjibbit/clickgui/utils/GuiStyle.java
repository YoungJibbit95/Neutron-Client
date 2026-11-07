package me.youngjibbit.clickgui.utils;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import java.awt.Color;

/** Uses the existing rounded gradient shader; the inset keeps accent color out of the fill. */
public final class GuiStyle {
    private GuiStyle() { }

    /** Hover changes the existing ink; no separate highlight surface covers the control. */
    public static int ink(int base,me.youngjibbit.gui.UiSpring state){
        return Theme.interpolateRGB(base,Theme.resolve(Theme.getDefaultTheme()).getPrimaryGradientRGB(),Math.min(.55f,state.glow()*1.5f));
    }

    /** Animate paint bounds only. Layout and capture keep using the original rectangle. */
    public static ClipRect buttonBounds(ClipRect hit, me.youngjibbit.gui.UiSpring state) {
        if (!state.enabled() || Math.abs(state.amount()) < .005f) return hit;
        boolean motion = me.youngjibbit.utils.render.UiEffects.motion();
        float grow = motion ? (state.scale() - 1) : 0;
        float dx = Math.min(2, hit.width * grow / 2), dy = hit.height * grow / 2;
        ClipRect paint = new ClipRect(hit.x - dx, hit.y - dy - (motion ? state.lift() : 0),
                hit.width + 2 * dx, hit.height + 2 * dy);
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        if (state.glow() > .01f)
            me.youngjibbit.utils.render.UiEffects.drawGlow(paint.x, paint.y, paint.width, paint.height, 4,
                    theme.getPrimaryGradientRGB(), state.glow() * theme.getGlowStrength(), 2, 2);
        return paint;
    }

    /** Keyboard/press cues only. Hover belongs to the control's own contour and ink. */
    public static void feedback(ClipRect bounds,me.youngjibbit.gui.UiSpring state){
        if(!state.enabled()||bounds.width<8||bounds.height<8)return;
        try(GuiClip ignored=GuiClip.pushTransformed(bounds)){
            float inset=Math.min(5,bounds.height*.22f);
            if(state.keyboardFocused()){
                // Shape cue is immediate, including Border/Glow OFF and dt=0.
                UIUtils.drawDecorationRound(bounds.x+2,bounds.y+3,1,bounds.height-6,.5f,new Color(231,237,248));
                UIUtils.drawDecorationRound(bounds.x+bounds.width-3,bounds.y+3,1,bounds.height-6,.5f,new Color(231,237,248));
            }
            if(state.pressed())UIUtils.drawDecorationRound(bounds.x+inset,bounds.y+bounds.height-3,bounds.width-2*inset,1,.5f,new Color(231,237,248));
        }
    }

    public static void panel(float x, float y, float width, float height, float radius,
                             float accent, Color fill) {
        Theme theme = Theme.resolve(Theme.getDefaultTheme());
        Color neutral = new Color(58, 58, 60);
        Color left = new Color(Theme.interpolateRGB(neutral.getRGB(), theme.getPrimaryGradientRGB(), accent), true);
        left=me.youngjibbit.utils.render.UiEffects.accent(left.getRGB(),theme.isCustom()?theme.getOutlineStrength():1);
        UIUtils.drawRoundOutline(x,y,width,height,radius,theme.isCustom()?theme.getOutlineWidth():1,fill,left,accent);
    }
}
