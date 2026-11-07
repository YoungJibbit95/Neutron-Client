package net.minecraft.util;

import org.junit.Test;
import static org.junit.Assert.*;

/** Real bundled translations must survive Commons IO API updates. */
public class StringTranslateDependencyTest {
    @Test public void loadsBundledLanguageAndFallsBackForMissingKeys() {
        StringTranslate translations = new StringTranslate();
        assertEquals("Singleplayer", translations.translateKey("menu.singleplayer"));
        assertTrue(translations.isKeyTranslated("menu.singleplayer"));
        assertEquals("neutron.missing.fixture", translations.translateKey("neutron.missing.fixture"));
        assertTrue(translations.getLastUpdateTimeInMilliseconds() > 0);
    }

    @Test public void convertsNumericPlaceholdersToAcceptMinecraftStringArguments() {
        StringTranslate translations = new StringTranslate();
        assertEquals("The number you have entered (one) is too small, it must be at least two",
                translations.translateKeyFormat("commands.generic.num.tooSmall", "one", "two"));
        assertEquals("'invalid' is not a valid number",
                translations.translateKeyFormat("commands.generic.num.invalid", "invalid"));
    }
}
