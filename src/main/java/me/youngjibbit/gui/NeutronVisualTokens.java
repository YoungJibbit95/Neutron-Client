package me.youngjibbit.gui;

import me.youngjibbit.module.Category;
import me.youngjibbit.utils.Theme;
import java.awt.Color;

/**
 * Neutron 2.0 composition tokens. All base surfaces are neutral dark glass,
 * while the selected ThemeCatalog palette owns the restrained accent colors.
 * No GL state, shader, texture, mutable global cache or settings writes.
 */
public final class NeutronVisualTokens {
    private NeutronVisualTokens() {}
    public static final Color FRAME = new Color(17,19,29,239);
    public static final Color HEADER = new Color(24,25,36,242);
    public static final Color SIDEBAR = new Color(25,26,39,238);
    public static final Color PANEL = new Color(28,30,42,232);
    public static final Color CARD = new Color(34,35,47,241);
    public static final Color CARD_HOVER = new Color(41,41,55,243);
    public static final Color CONTROL = new Color(48,46,63,239);
    public static final Color TRACK = new Color(63,62,77,234);
    public static final Color DIM = new Color(9,11,18,105);
    public static final Color HAIRLINE = new Color(224,210,235,32);
    public static final int INK = 0xfff7f1fa;
    public static final int MUTED = 0xffc5bccd;
    public static final int FAINT = 0xffaaa2b5;
    public static final float RADIUS_FRAME = 14, RADIUS_SIDEBAR = 12,
            RADIUS_CARD = 8, RADIUS_CONTROL = 7;
    public static final float GUTTER = 12, ROW_GAP = 7, CARD_HEIGHT = 44;

    public static Color alpha(int argb, int alpha) {
        return new Color((argb & 0xffffff) | (Math.max(0,Math.min(255,alpha)) << 24),true);
    }
    public static Color accent(Theme theme,int opacity){
        return alpha(theme.getPrimaryGradientRGB(),opacity);
    }
    public static Color secondary(Theme theme,int opacity){
        return alpha(theme.getSecondaryGradientRGB(),opacity);
    }
    public static int ink(Theme theme){
        // A dark custom text theme cannot make labels unreadable on the dark glass.
        int rgb=theme.getTextColor().getRGB();
        int r=(rgb>>16)&255,g=(rgb>>8)&255,b=rgb&255;
        return r*299+g*587+b*114<146000?INK:rgb|0xff000000;
    }
    public static float width(int viewport){
        return Math.max(1,Math.min(Math.max(1,viewport-16),
                Math.min(1040,Math.max(640,viewport*.72f))));
    }
    public static float height(int viewport){
        return Math.max(1,Math.min(Math.max(1,viewport-16),
                Math.min(640,Math.max(440,viewport*.68f))));
    }
    public static float sidebar(float frameWidth){
        return Math.max(92,Math.min(190,frameWidth*.23f));
    }
    public static float header(){return 56;}
    public static float categoryPitch(float frameHeight,int count){
        return Math.max(14,Math.min(41,(frameHeight-header()-76)/Math.max(1,count)));
    }
    public static String categoryDescription(Category category){
        switch(category){
            case Combat:return "Combat modules and target interactions.";
            case Movement:return "Movement and traversal modules.";
            case Player:return "Player utilities and quality of life.";
            case Render:return "Visual, interface and world rendering modules.";
            case World:return "World and environment utilities.";
            case Client:return "Client, themes and UI preferences.";
            case Ghost:return "Subtle input and player utilities.";
            case Exploit:return "Specialized client features.";
            default:return "Module controls and settings.";
        }
    }
}
