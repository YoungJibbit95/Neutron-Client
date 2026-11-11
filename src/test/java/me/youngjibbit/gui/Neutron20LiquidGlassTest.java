package me.youngjibbit.gui;

import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UiEffects;
import org.junit.After;
import org.junit.Test;

import java.awt.Color;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Pure invariants of the shared 2.0 glass recipe. */
public class Neutron20LiquidGlassTest {
    @After public void restore() {
        ThemeMode.select(ThemeMode.LEGACY);
        UiEffects.invalidate();
    }

    @Test public void roundnessMapsFromTheThemeSettingWithoutEscapingTheShape() {
        assertEquals(4f, LiquidGlass.radius(7f, 0f), .0001f);
        assertEquals(7f, LiquidGlass.radius(7f, 5f), .0001f);
        assertEquals(14.8f, LiquidGlass.radius(7f, 18f), .0001f);
        assertEquals(4f, LiquidGlass.radius(7f, -20f), .0001f);
        assertEquals(14.8f, LiquidGlass.radius(7f, 99f), .0001f);
        assertEquals(7f, LiquidGlass.radius(7f, Float.NaN), .0001f);
        assertEquals(0f, LiquidGlass.radius(1f, 0f), .0001f);
        // The same mapping, additionally clamped to the actual rectangle.
        assertEquals(4f, LiquidGlass.radius(10f, 18f, 20f, 8f), .0001f);
        assertEquals(0f, LiquidGlass.radius(10f, 18f, 0f, 8f), .0001f);
        assertEquals(0f, LiquidGlass.radius(10f, 18f, -4f, 8f), .0001f);
        assertEquals(7f, LiquidGlass.radius(7f, 5f, 400f, 300f), .0001f);
    }

    @Test public void glassAlphasStayTransparentAndOrdered() {
        assertTrue(LiquidGlass.CARD < LiquidGlass.CARD_HOVER);
        assertTrue(LiquidGlass.CARD_HOVER < LiquidGlass.CARD_SELECTED);
        for (int alpha : new int[]{LiquidGlass.CARD, LiquidGlass.CARD_HOVER,
                LiquidGlass.CARD_SELECTED, LiquidGlass.CONTROL, LiquidGlass.PANEL,
                LiquidGlass.HIGHLIGHT, LiquidGlass.HAIRLINE, LiquidGlass.FOCUS}) {
            assertTrue(alpha > 0 && alpha < 255);
        }
        assertEquals(LiquidGlass.HIGHLIGHT, LiquidGlass.HIGHLIGHT_INK.getAlpha());
        assertEquals(255, glassFill(999).getAlpha());
        assertEquals(0, glassFill(-9).getAlpha());
    }

    @Test public void invalidGeometryCannotReachNativeDrawingOrProduceNonfiniteRadii() {
        for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY}) {
            assertEquals(0,LiquidGlass.radius(7,5,invalid,20),0);
            assertEquals(0,LiquidGlass.radius(7,5,20,invalid),0);
            assertTrue(Float.isFinite(LiquidGlass.radius(invalid,18)));
            assertTrue(!LiquidGlass.validRectangle(invalid,0,20,10));
            assertTrue(!LiquidGlass.validRectangle(0,invalid,20,10));
            LiquidGlass.card(invalid,0,20,10,4,Theme.PinkLemonade,false,0);
            LiquidGlass.highlight(0,0,invalid,10,4);
        }
        assertTrue(!LiquidGlass.validRectangle(Float.MAX_VALUE,0,Float.MAX_VALUE,10));
        assertTrue(!LiquidGlass.validRectangle(0,0,-1,10));
        assertTrue(LiquidGlass.validRectangle(-20,-10,20,10));
    }

    @Test public void revealOpacityIsBoundedAndAppliedOnceToOpticalChrome() {
        assertEquals(46,LiquidGlass.opacityAlpha(LiquidGlass.HIGHLIGHT,1));
        assertEquals(23,LiquidGlass.opacityAlpha(LiquidGlass.HIGHLIGHT,.5f));
        assertEquals(0,LiquidGlass.opacityAlpha(LiquidGlass.HIGHLIGHT,0));
        assertEquals(255,LiquidGlass.opacityAlpha(1000,2));
        assertEquals(0,LiquidGlass.opacityAlpha(-1,1));
        for(float invalid:new float[]{-1,Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY})
            assertEquals(0,LiquidGlass.opacityAlpha(46,invalid));
        LiquidGlass.highlight(0,0,20,10,4,0);
        LiquidGlass.border(0,0,20,10,4,Theme.PinkLemonade,true,0);
    }

    @Test public void paletteEndpointsRemainThePinkLemonadeTheme() {
        assertEquals(0xFFFF00EA, Theme.PinkLemonade.getPrimaryGradientRGB());
        assertEquals(0xFFFF6200, Theme.PinkLemonade.getSecondaryGradientRGB());
        assertEquals(0xFF00EA, LiquidGlass.accent(Theme.PinkLemonade, 200).getRGB() & 0xFFFFFF);
        assertEquals(200, LiquidGlass.accent(Theme.PinkLemonade, 200).getAlpha());
        assertEquals(0xFFFF00EA, LiquidGlass.accent(Theme.PinkLemonade, 999).getRGB());
        assertTrue(Theme.PinkLemonade.isCustom() == false);
        // Null is resolved to the active default, never dereferenced.
        assertEquals(Theme.resolve(null), Theme.getDefaultTheme());
    }

    private static Color glassFill(int alpha) {
        return LiquidGlass.fill(Theme.PinkLemonade, alpha);
    }
}
