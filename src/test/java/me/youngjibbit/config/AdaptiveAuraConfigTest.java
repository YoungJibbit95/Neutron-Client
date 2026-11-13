package me.youngjibbit.config;

import com.google.gson.JsonParser;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

/** Real ConfigManager serialization and warm-load policy for the appended Aura-only mode. */
public class AdaptiveAuraConfigTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void appendOnlyAuraChoicePreservesLegacyIndicesDefaultsAndOtherModuleChoices() {
        Aura aura = new Aura();
        AimAssist assist = new AimAssist();
        Scaffold scaffold = new Scaffold();
        assertArrayEquals(new String[]{"Basic", "Smooth", "Advanced", "Arsenic Lazy", "Arsenic Heuristics", "Adaptive"},
                Arrays.copyOf(aura.getModeSetting("Rot Mode").getOptions(),6));
        assertEquals("AI Learned",aura.getModeSetting("Rot Mode").getOptions()[6]);
        assertEquals("Basic", aura.getModeSetting("Rot Mode").getValue());
        assertEquals(0, aura.getModeSetting("Rot Mode").getSelectedIndex());
        assertArrayEquals(new String[]{"Legit", "Test", "Normal", "Arsenic Lazy", "Arsenic Heuristics"},
                assist.getModeSetting("Rotations").getOptions());
        assertEquals("Normal", assist.getModeSetting("Rotations").getValue());
        assertArrayEquals(new String[]{"Basic", "Smooth", "Advanced"},
                scaffold.getModeSetting("Rotation Smoothing").getOptions());
        for (int i = 0; i < 5; i++) {
            aura.getModeSetting("Rot Mode").setSelectedIndex(i);
            assertEquals(i, aura.getModeSetting("Rot Mode").getSelectedIndex());
            assertNotEquals("Adaptive", aura.getModeSetting("Rot Mode").getValue());
        }
    }

    @Test public void activeAdaptiveIsOptInSerializesWithTheOldSettingIdAndRoundTrips() throws Exception {
        Aura aura = new Aura();
        ConfigManager config = new ConfigManager(temp.newFolder().toPath(),
                Collections.<Module>singletonList(aura), () -> false, message -> {});
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        assertTrue(aura.getValueByName("Rot Mode").isVisible());
        assertFalse(aura.getValueByName("Randomization Style").isVisible());
        assertFalse(aura.getValueByName("Speed Curve").isVisible());
        assertNotNull(aura.getNumberSetting("Yaw Speed"));
        assertNotNull(aura.getNumberSetting("Pitch Speed"));
        assertTrue(config.getLastError(), config.saveConfig("Adaptive"));
        String stored = config.serialize();
        assertEquals("Adaptive", new JsonParser().parse(stored).getAsJsonObject()
                .getAsJsonObject("modules").getAsJsonObject("aura")
                .getAsJsonObject("settings").get("rot_mode").getAsString());
        aura.getModeSetting("Rot Mode").setValue("Smooth");
        assertTrue(config.getLastError(), config.loadConfig("Adaptive"));
        assertEquals("Adaptive", aura.getModeSetting("Rot Mode").getValue());
        assertEquals(stored, config.serialize());
    }

    @Test public void missingNewModeKeyInWarmReloadReturnsToBasicWithoutWritingOriginal() throws Exception {
        Aura aura = new Aura();
        Path directory = temp.newFolder().toPath();
        ConfigManager config = new ConfigManager(directory, Collections.<Module>singletonList(aura), () -> false, message -> {});
        String[] oldConfigs = {
                "{\"Aura\":{\"Reach\":3}}",
                "{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":1,\"settings\":{}}}}",
                "{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,\"settings\":{}}}}"
        };
        for (String text : oldConfigs) {
            aura.getModeSetting("Rot Mode").setValue("Adaptive");
            Path path = directory.resolve("Legacy.json");
            Files.write(path, text.getBytes(StandardCharsets.UTF_8));
            assertTrue(config.getLastError(), config.loadConfig("Legacy"));
            assertEquals("Basic", aura.getModeSetting("Rot Mode").getValue());
            assertEquals(text, new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
        }
    }

    @Test public void invalidModesCannotBeSavedAndDoNotMigrateLegacyArsenic() throws Exception {
        Aura aura = new Aura();
        Path directory = temp.newFolder().toPath();
        ConfigManager config = new ConfigManager(directory, Arrays.<Module>asList(aura),
                () -> false, message -> {});
        String legacy = "{\"Aura\":{\"Rot Mode\":\"Arsenic Heuristics\"}}";
        Files.write(directory.resolve("Legacy.json"), legacy.getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(), config.loadConfig("Legacy"));
        assertEquals("Arsenic Heuristics", aura.getModeSetting("Rot Mode").getValue());
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        String before = config.serialize();
        String bad = "{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,"
                + "\"settings\":{\"rot_mode\":\"Adaptive-ish\"}}}}";
        Files.write(directory.resolve("Bad.json"), bad.getBytes(StandardCharsets.UTF_8));
        assertFalse(config.saveConfig("Bad", config.readConfigData(config.defaultConfig.toPath().resolveSibling("Bad.json"))));
        assertEquals(before, config.serialize());
        assertEquals(bad, new String(Files.readAllBytes(directory.resolve("Bad.json")), StandardCharsets.UTF_8));
        assertEquals("Adaptive", aura.getModeSetting("Rot Mode").getValue());
    }
}
