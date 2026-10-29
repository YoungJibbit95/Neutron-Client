package me.youngjibbit.clickgui.utils;

import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import java.awt.Color;

/** Uses the existing rounded gradient shader; the inset keeps accent color out of the fill. */
public final class GuiStyle {
    private GuiStyle() { }

    /** Fixed outer bounds; only an inset decoration lifts/scales. Never used for hit testing. */
    public static void feedback(ClipRect bounds,me.youngjibbit.gui.UiSpring state){
        if(!state.enabled()||bounds.width<8||bounds.height<8)return;
        try(GuiClip ignored=GuiClip.pushTransformed(bounds)){
            float inset=Math.min(5,bounds.height*.22f),height=bounds.height-2*inset;
            float width=Math.min(bounds.width-2*inset,Math.max(1,bounds.width-4*inset)*state.scale());
            float x=bounds.x+(bounds.width-width)/2;
            float y=bounds.y+inset-Math.max(-inset/2,Math.min(inset/2,state.lift()));
            float alpha=Math.min(.22f,Math.max(0,state.glow())*.5f);
            if(alpha>0)UIUtils.drawDecorationRound(x,y,width,height,Math.min(3,height/2),new Color(255,255,255,Math.round(alpha*255)));
            if(state.focused()){
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
