package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.Assert.*;

public class AdaptiveAuraActionsConfigTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void newModesRoundTripAndOldImportCannotInheritTheNewExecutionStrategy() throws Exception {
        Aura aura = new Aura(); Path directory = temp.newFolder().toPath();
        ConfigManager config = new ConfigManager(directory, Collections.<Module>singletonList(aura), () -> false, ignored -> { });
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        aura.getModeSetting("Attack Mode").setValue("Packet");
        aura.getModeSetting("Mode").setValue("Switch");
        aura.getModeSetting("Sort Mode").setValue("Angle");
        aura.getModeSetting("Autoblock Mode").setValue("Force");
        aura.getBooleanSetting("Fail Swing").setValue(true);
        aura.getNumberSetting("Block Range").setValue(3.5);
        assertTrue(config.getLastError(), config.saveConfig("Modern"));
        String modern = config.serialize();
        assertTrue(config.getLastError(), config.loadConfig("Modern"));
        assertEquals(modern, config.serialize());
        Files.write(directory.resolve("Old.json"), "{\"Aura\":{\"Rot Mode\":\"Adaptive\",\"Reach\":3}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(), config.loadConfig("Old"));
        assertEquals("PlayerController", aura.getModeSetting("Attack Mode").getValue());
        assertEquals("Single", aura.getModeSetting("Mode").getValue());
        assertEquals("Range", aura.getModeSetting("Sort Mode").getValue());
        assertEquals("Watchdog", aura.getModeSetting("Autoblock Mode").getValue());
        assertFalse(aura.getBooleanSetting("Fail Swing").getValue());
        assertEquals(3, aura.getNumberSetting("Block Range").getValue(), 0);
    }

    @Test public void adaptiveOptionsHaveHonestVisibilityAndLegacyIndicesAreUnchanged() {
        Aura aura = new Aura();
        assertEquals("Single", aura.getModeSetting("Mode").getOptions()[0]);
        assertEquals("Multi", aura.getModeSetting("Mode").getOptions()[1]);
        assertEquals("Intave", aura.getModeSetting("Autoblock Mode").getOptions()[3]);
        assertFalse(aura.getModeSetting("Attack Mode").isVisible());
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        assertTrue(aura.getModeSetting("Attack Mode").isVisible());
        assertFalse(aura.getBooleanSetting("RayCast").isVisible());
        assertFalse(aura.getBooleanSetting("Best Aim Vec").isVisible());
        aura.getModeSetting("Adaptive Aim Point").setValue("Legacy");
        assertTrue(aura.getBooleanSetting("Best Aim Vec").isVisible());
        aura.getBooleanSetting("Autoblock").setValue(true);
        aura.getModeSetting("Autoblock Mode").setValue("Release");
        assertTrue(aura.getNumberSetting("Reblock Delay").isVisible());
        aura.getModeSetting("Autoblock Mode").setValue("Force");
        assertFalse(aura.getNumberSetting("Reblock Delay").isVisible());
    }
}
