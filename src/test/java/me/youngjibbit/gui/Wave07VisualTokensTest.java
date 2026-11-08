package me.youngjibbit.gui;

import me.youngjibbit.module.Category;
import me.youngjibbit.theme.ThemeCatalog;
import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.Theme;
import org.junit.After;
import org.junit.Test;
import java.awt.Color;
import static org.junit.Assert.*;

public class Wave07VisualTokensTest {
    @After public void restore(){ThemeMode.select(ThemeMode.LEGACY);}

    @Test public void selectedColorPaletteSurvivesBothLayoutModesForAll20Presets(){
        assertEquals(20,Theme.values().length);
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        for(Theme palette:Theme.values()){
            assertSame(palette,ThemeMode.global(palette));
            assertEquals(palette.getPrimaryGradientRGB(),
                    ThemeMode.global(palette).getPrimaryGradientRGB());
            assertEquals(palette.getSecondaryGradientRGB(),
                    ThemeMode.global(palette).getSecondaryGradientRGB());
        }
        ThemeCatalog catalog=ThemeCatalog.global();
        String saved=catalog.selectedReference();
        assertNotNull(saved);
        ThemeMode.select(ThemeMode.LEGACY);
        assertEquals(saved,catalog.selectedReference());
        assertEquals("PinkLemonade",Theme.values()[6].name());
    }

    @Test public void darkGlassAndTypographyMaintainContrast(){
        Color[] solids={NeutronVisualTokens.FRAME,NeutronVisualTokens.HEADER,
                NeutronVisualTokens.SIDEBAR,NeutronVisualTokens.PANEL,
                NeutronVisualTokens.CARD,NeutronVisualTokens.CARD_HOVER,
                NeutronVisualTokens.CONTROL};
        for(Color surface:solids){
            assertTrue(surface.getAlpha()>=230);
            assertTrue(surface.getRed()<75);
            assertTrue(surface.getGreen()<75);
            assertTrue(surface.getBlue()<85);
            assertTrue(contrast(NeutronVisualTokens.INK,surface.getRGB())>5.0);
        }
        assertTrue(NeutronVisualTokens.MUTED!=NeutronVisualTokens.INK);
        assertEquals(Theme.Purple.getPrimaryGradientRGB()&0xffffff,
                NeutronVisualTokens.accent(Theme.Purple,200).getRGB()&0xffffff);
        assertEquals(200,NeutronVisualTokens.accent(Theme.Purple,200).getAlpha());
        assertEquals(NeutronVisualTokens.INK,NeutronVisualTokens.ink(
                Theme.custom("dark","dark",
                        java.util.Arrays.asList(Color.BLACK,Color.WHITE),
                        false,false,.18f,Color.BLACK,new Color(11,13,20),
                        Color.RED,.3f,5,.4f,1)));
    }

    @Test public void layoutFitsAllRequestedViewports(){
        for(int[] viewport:new int[][]{{640,360},{854,480},{1280,720},{1920,1080}}){
            float w=NeutronVisualTokens.width(viewport[0]);
            float h=NeutronVisualTokens.height(viewport[1]);
            float side=NeutronVisualTokens.sidebar(w);
            assertTrue(w>=1&&w<=viewport[0]-16);
            assertTrue(h>=1&&h<=viewport[1]-16);
            assertTrue(side>=92&&side<w);
            assertTrue(NeutronVisualTokens.categoryPitch(h,8)>14);
        }
        assertEquals(1040,NeutronVisualTokens.width(1920),0);
        assertEquals(640,NeutronVisualTokens.height(1080),0);
        assertEquals(640,NeutronVisualTokens.width(854),0);
        assertEquals(440,NeutronVisualTokens.height(480),0);
        assertTrue(NeutronVisualTokens.width(320)<=304);
        assertTrue(NeutronVisualTokens.height(240)<=224);
        assertTrue(NeutronVisualTokens.categoryDescription(Category.Render).contains("render"));
    }
    private static double channel(int c){
        double v=c/255d;return v<=.04045?v/12.92:Math.pow((v+.055)/1.055,2.4);
    }
    private static double luminance(int rgb){
        return .2126*channel(rgb>>16&255)+.7152*channel(rgb>>8&255)+.0722*channel(rgb&255);
    }
    private static double contrast(int a,int b){
        double x=luminance(a),y=luminance(b);
        return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);
    }
}
