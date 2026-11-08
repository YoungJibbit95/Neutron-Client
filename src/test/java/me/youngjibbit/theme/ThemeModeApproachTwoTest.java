package me.youngjibbit.theme;

import me.youngjibbit.module.impl.client.ThemeModule;
import me.youngjibbit.utils.Theme;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class ThemeModeApproachTwoTest {
    @After public void restore(){ThemeMode.select(ThemeMode.LEGACY);}

    @Test public void originalTwentyPresetsRemainUnmodifiedAndSelectDefaultsLegacy(){
        ThemeMode.select(ThemeMode.LEGACY);
        assertEquals(20,Theme.values().length);
        assertSame(Theme.Neutron,Theme.values()[19]);
        assertEquals("PinkLemonade",Theme.values()[6].name());
        assertSame(Theme.PinkLemonade,ThemeMode.global(Theme.PinkLemonade));
        ThemeModule module=new ThemeModule();
        assertEquals(ThemeMode.LEGACY,module.themeMode.getValue());
        assertEquals(0,module.themeMode.getSelectedIndex());
        assertArrayEquals(new String[]{ThemeMode.LEGACY,ThemeMode.APPROACH_TWO},
                module.themeMode.getOptions());
    }
    @Test public void approachTwoAffectsOnlyPresentationNotThemeCatalogSelection(){
        ThemeCatalog catalog=ThemeCatalog.global();
        String prior=catalog.selectedReference();
        ThemeMode.select(ThemeMode.APPROACH_TWO);
        Theme styled=ThemeMode.global(Theme.Purple);
        assertSame(Theme.Purple,styled);
        assertEquals(Theme.Purple.getPrimaryGradientRGB(),styled.getPrimaryGradientRGB());
        assertEquals(Theme.Purple.getSecondaryGradientRGB(),styled.getSecondaryGradientRGB());
        for(Theme palette:Theme.values())
            assertSame("Neutron 2.0 must preserve every selected palette",palette,
                    ThemeMode.global(palette));
        assertEquals(prior,catalog.selectedReference());
        ThemeMode.select("unknown-mode");
        assertSame(Theme.Purple,ThemeMode.global(Theme.Purple));
        assertEquals(prior,catalog.selectedReference());
    }
    @Test public void themeModuleModeValueKeepsTheExistingThemeSelection(){
        ThemeCatalog catalog=ThemeCatalog.global();
        String old=catalog.selectedReference();
        ThemeModule module=new ThemeModule();
        try {
            module.themeMode.setValue(ThemeMode.APPROACH_TWO);
            assertEquals(ThemeMode.APPROACH_TWO,ThemeMode.selected());
            assertEquals(old,module.selectedTheme.getValue());
            assertEquals(old,catalog.selectedReference());
            module.themeMode.setValue(ThemeMode.LEGACY);
            assertEquals(ThemeMode.LEGACY,ThemeMode.selected());
            assertEquals(old,catalog.selectedReference());
        }finally {module.themeMode.setValue(ThemeMode.LEGACY);}
    }
}
