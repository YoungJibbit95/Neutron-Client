package me.youngjibbit.module.impl.client;

import me.youngjibbit.value.impl.ComboValue;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * MOD-010: resolving the Mode visibility must never dereference a missing setting.
 * A fresh module has to answer for every Effects Source and Mode combination.
 */
public class ArrayListModeVisibilityTest {

    @Test
    public void modeVisibilityResolvesForFreshModuleAndEveryMode() {
        ArrayListHUDModule hud = new ArrayListHUDModule();
        ComboValue mode = hud.getModeSetting("Mode");
        ComboValue source = hud.getModeSetting("Effects Source");
        assertNotNull("Mode setting must exist", mode);
        assertNotNull("Effects Source setting is registered by the shared surface", source);

        assertNotEquals(0, mode.getOptions().length);
        // Default pair Global/Glass resolves without throwing; Mode is only offered for the local legacy surface.
        assertFalse(mode.isVisible());

        for (String sourceValue : source.getOptions()) {
            source.setValue(sourceValue);
            for (String modeValue : mode.getOptions()) {
                mode.setValue(modeValue);
                boolean expected = "Local".equals(sourceValue) || "None".equals(modeValue);
                assertEquals("source=" + sourceValue + ", mode=" + modeValue, expected, mode.isVisible());
            }
        }
    }
}
