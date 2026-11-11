package me.youngjibbit.module.impl.render;

import me.youngjibbit.theme.ThemeMode;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UiEffects;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

/** Existing HUD helper contracts stay package-private and survive the 2.0 material update. */
public class Neutron20HudCompatibilityTest {
    @After public void restore() {
        ThemeMode.select(ThemeMode.LEGACY);
        UiEffects.invalidate();
    }

    @Test public void preservedHudHelpersKeepTheirSemantics() {
        assertNull(TargetHUD.migrateMode(null));
        assertEquals("Modern", TargetHUD.migrateMode("Neutron 2.0"));
        assertEquals("Modern", TargetHUD.migrateMode("Neutron White"));
        assertEquals("Basic", TargetHUD.migrateMode("Neutron Default"));
        assertEquals("Rue", TargetHUD.migrateMode("Rue (Rise)"));
        assertEquals("Custom", TargetHUD.migrateMode("Custom"));

        assertEquals("Neutron 2.0", SessionInfo.modernFor("Outline", true));
        assertEquals("Outline", SessionInfo.modernFor("Outline", false));
        assertEquals("Modern", SessionInfo.modernFor("Modern", true));
        assertEquals("Creative", SessionInfo.modernFor("Creative", true));

        assertEquals("LMB", Keystrokes.keyLabel(-100));
        assertEquals("RMB", Keystrokes.keyLabel(-99));

        int low = ArmorHUD.durabilityColor(Theme.PinkLemonade, 0f);
        int middle = ArmorHUD.durabilityColor(Theme.PinkLemonade, .5f);
        int high = ArmorHUD.durabilityColor(Theme.PinkLemonade, 1f);
        assertEquals(0xFF, low >>> 24);
        assertEquals(0xFF, middle >>> 24);
        assertEquals(0xFF, high >>> 24);
        assertEquals(UiEffects.semanticColor(UiEffects.SemanticRole.ERROR, 0, true) & 0xFFFFFF,
                low & 0xFFFFFF);
        assertEquals(UiEffects.semanticColor(UiEffects.SemanticRole.SUCCESS, 0, true) & 0xFFFFFF,
                high & 0xFFFFFF);
        assertNotEquals(low, high);
        assertEquals(low, ArmorHUD.durabilityColor(Theme.PinkLemonade, Float.NaN));
    }
}
