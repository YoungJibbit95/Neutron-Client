package me.youngjibbit.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.movement.Speed;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.*;

/** The opt-in V2 choice preserves every old Speed label, index and saved setting. */
public class IntaveMomentumV2ConfigTest {
    private static final String V2 = "Intave Momentum V2";
    private static final String[] OLD_MODES = {
            "BMC", "Intave", "Polar", "Vulcan", "Vulcan2", "Intave Momentum", "Intave Boat"
    };

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private ConfigManager configs(Path directory, Speed speed) {
        return new ConfigManager(directory, Collections.<Module>singletonList(speed),
                () -> false, message -> { });
    }

    @Test public void v2IsAppendedWithoutChangingTheDefaultOrLegacyIndices() {
        Speed speed = new Speed();
        assertArrayEquals(new String[]{"BMC", "Intave", "Polar", "Vulcan", "Vulcan2",
                "Intave Momentum", "Intave Boat", V2}, speed.getModeSetting("Mode").getOptions());
        assertEquals("Intave", speed.getModeSetting("Mode").getValue());
        assertEquals(1, speed.getModeSetting("Mode").getSelectedIndex());
        assertFalse(speed.isToggled());
        assertFalse(speed.getBooleanSetting("Air Speed").getValue());
        assertTrue(speed.getBooleanSetting("Air Speed").isVisible());

        for (int index = 0; index < OLD_MODES.length; index++) {
            speed.getModeSetting("Mode").setSelectedIndex(index);
            assertEquals(OLD_MODES[index], speed.getModeSetting("Mode").getValue());
            assertEquals(index, speed.getModeSetting("Mode").getSelectedIndex());
            assertEquals(index < 5, speed.getBooleanSetting("Air Speed").isVisible());
        }
        speed.getModeSetting("Mode").setSelectedIndex(7);
        assertEquals(V2, speed.getModeSetting("Mode").getValue());
        assertFalse("the legacy air-speed writer is not an option of the V2 profile",
                speed.getBooleanSetting("Air Speed").isVisible());
        assertFalse("choosing the profile never enables the module", speed.isToggled());
    }

    @Test public void v2RoundTripsByItsOwnLabelAndPreservesTheHiddenLegacyAirSpeedValue() throws Exception {
        Path directory = temp.newFolder().toPath();
        Speed speed = new Speed();
        ConfigManager configs = configs(directory, speed);
        speed.getBooleanSetting("Air Speed").setValue(true);
        speed.getModeSetting("Mode").setValue(V2);
        String saved = configs.serialize();
        JsonObject settings = new JsonParser().parse(saved).getAsJsonObject()
                .getAsJsonObject("modules").getAsJsonObject("speed").getAsJsonObject("settings");
        assertEquals(V2, settings.get("mode").getAsString());
        assertTrue(settings.get("air_speed").getAsBoolean());
        assertTrue(configs.getLastError(), configs.saveConfig("MomentumV2"));

        speed.getModeSetting("Mode").setValue("Intave");
        speed.getBooleanSetting("Air Speed").setValue(false);
        assertTrue(configs.getLastError(), configs.loadConfig("MomentumV2"));
        assertEquals(saved, configs.serialize());
        assertEquals(7, speed.getModeSetting("Mode").getSelectedIndex());
        assertTrue(speed.getBooleanSetting("Air Speed").getValue());
        assertFalse(speed.getBooleanSetting("Air Speed").isVisible());
        assertFalse(speed.isToggled());

        speed.getModeSetting("Mode").setValue("Intave");
        assertTrue("the old mode retains its stored option", speed.getBooleanSetting("Air Speed").getValue());
        assertTrue(speed.getBooleanSetting("Air Speed").isVisible());
    }

    @Test public void everyPreV2SavedModeKeepsItsMeaningWithoutImplicitlySelectingV2() throws Exception {
        for (int index = 0; index < OLD_MODES.length; index++) {
            Path directory = temp.newFolder().toPath();
            Speed speed = new Speed();
            ConfigManager configs = configs(directory, speed);
            speed.getModeSetting("Mode").setValue(V2);
            boolean airSpeed = index % 2 == 0;
            String saved = "{\"version\":2,\"modules\":{\"speed\":{\"settings\":{"
                    + "\"mode\":\"" + OLD_MODES[index] + "\",\"air_speed\":" + airSpeed + "}}}}";
            Path file = directory.resolve("LegacySpeed.json");
            Files.write(file, saved.getBytes(StandardCharsets.UTF_8));
            assertTrue(configs.getLastError(), configs.loadConfig("LegacySpeed"));
            assertEquals(OLD_MODES[index], speed.getModeSetting("Mode").getValue());
            assertEquals(index, speed.getModeSetting("Mode").getSelectedIndex());
            assertEquals(airSpeed, speed.getBooleanSetting("Air Speed").getValue());
            assertFalse(speed.isToggled());
            assertEquals("loading preserves the original saved configuration", saved,
                    new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
        }
    }
}
