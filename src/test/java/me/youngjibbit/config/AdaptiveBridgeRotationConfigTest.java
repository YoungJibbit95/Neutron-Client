package me.youngjibbit.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.*;

/** Saved settings remain opt-in and preserve the existing approach defaults. */
public class AdaptiveBridgeRotationConfigTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private ConfigManager configs(Path directory, Scaffold scaffold) {
        return new ConfigManager(directory, Collections.<Module>singletonList(scaffold),
                () -> false, message -> { });
    }

    @Test public void exactProfilesAndDefaultsAreVisibleOnlyForAdaptiveBridge() {
        Scaffold scaffold = new Scaffold();
        assertEquals("Neutron", scaffold.getModeSetting("Scaffold Approach").getValue());
        assertEquals("Back", scaffold.getModeSetting("Rotation Mode").getValue());
        assertEquals(35, scaffold.getNumberSetting("Yaw Speed").getValue(), 0);
        assertEquals(35, scaffold.getNumberSetting("Pitch Speed").getValue(), 0);
        assertArrayEquals(new String[]{"Adaptive", "Godbridge"},
                scaffold.getModeSetting("Bridge Rotation Profile").getOptions());
        assertArrayEquals(new String[]{"Calm", "Balanced", "Swift", "Custom"},
                scaffold.getModeSetting("Aim Speed").getOptions());
        assertEquals("Adaptive", scaffold.getModeSetting("Bridge Rotation Profile").getValue());
        assertEquals("Balanced", scaffold.getModeSetting("Aim Speed").getValue());
        assertEquals(.25, scaffold.getNumberSetting("Bridge Rotation Variation").getValue(), 0);
        assertEquals(0, scaffold.getNumberSetting("Bridge Rotation Variation").getMin(), 0);
        assertEquals(1, scaffold.getNumberSetting("Bridge Rotation Variation").getMax(), 0);
        // The W08 combo id stays retired: no registered value carries it anymore.
        boolean retiredRegistered = false;
        for (me.youngjibbit.value.Value<?> value : scaffold.getValues())
            if ("bridge_rotation_speed".equals(value.getId())) retiredRegistered = true;
        assertFalse(retiredRegistered);
        for (String approach : new String[]{"Neutron", "Arsenic", "Adaptive Bridge"}) {
            scaffold.getModeSetting("Scaffold Approach").setValue(approach);
            boolean bridge = "Adaptive Bridge".equals(approach);
            assertEquals(bridge, scaffold.getModeSetting("Bridge Rotation Profile").isVisible());
            assertFalse(scaffold.getModeSetting("Aim Speed").isVisible());
            assertEquals(bridge,scaffold.getNumberSetting("Min Aim Speed").isVisible());
            assertEquals(bridge,scaffold.getNumberSetting("Max Aim Speed").isVisible());
            assertEquals(bridge, scaffold.getNumberSetting("Bridge Rotation Variation").isVisible());
            boolean custom = bridge && "Custom".equals(scaffold.getModeSetting("Aim Speed").getValue());
            for (String slider : new String[]{"Initial Yaw Speed", "Initial Pitch Speed",
                    "Keeping Yaw Speed", "Keeping Pitch Speed"}) {
                assertEquals(slider, custom, scaffold.getNumberSetting(slider).isVisible());
            }
            if (bridge) {
                assertFalse(scaffold.getNumberSetting("Yaw Speed").isVisible());
                assertFalse(scaffold.getNumberSetting("Pitch Speed").isVisible());
            }
        }
        assertEquals(55, scaffold.getNumberSetting("Initial Yaw Speed").getValue(), 0);
        assertEquals(55, scaffold.getNumberSetting("Initial Pitch Speed").getValue(), 0);
        assertEquals(30, scaffold.getNumberSetting("Keeping Yaw Speed").getValue(), 0);
        assertEquals(30, scaffold.getNumberSetting("Keeping Pitch Speed").getValue(), 0);
        assertEquals(1, scaffold.getNumberSetting("Initial Yaw Speed").getMin(), 0);
        assertEquals(90, scaffold.getNumberSetting("Keeping Yaw Speed").getMax(), 0);
        assertFalse(scaffold.getBooleanSetting("Arsenic Soft Motion").getValue());
        assertEquals("Back", scaffold.getModeSetting("Rotation Mode").getValue());
        assertEquals(35, scaffold.getNumberSetting("Yaw Speed").getValue(), 0);
        assertEquals(35, scaffold.getNumberSetting("Pitch Speed").getValue(), 0);
    }

    @Test public void bothProfilesAndEveryAimPresetRoundTripWithTheirIndependentSavedValues() throws Exception {
        for (String profile : new String[]{"Adaptive", "Godbridge"}) {
            for (String aim : new String[]{"Calm", "Balanced", "Swift", "Custom"}) {
                Scaffold scaffold = new Scaffold();
                Path directory = temp.newFolder().toPath();
                ConfigManager configs = configs(directory, scaffold);
                scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
                scaffold.getModeSetting("Bridge Rotation Profile").setValue(profile);
                scaffold.getModeSetting("Aim Speed").setValue(aim);
                scaffold.getNumberSetting("Bridge Rotation Variation").setValue(.73);
                scaffold.getNumberSetting("Yaw Speed").setValue(11D);
                scaffold.getNumberSetting("Pitch Speed").setValue(17D);
                scaffold.getModeSetting("Rotation Mode").setValue("NCP");
                if ("Custom".equals(aim)) {
                    scaffold.getNumberSetting("Initial Yaw Speed").setValue(21D);
                    scaffold.getNumberSetting("Initial Pitch Speed").setValue(23D);
                    scaffold.getNumberSetting("Keeping Yaw Speed").setValue(27D);
                    scaffold.getNumberSetting("Keeping Pitch Speed").setValue(29D);
                }
                String saved = configs.serialize();
                JsonObject settings = new JsonParser().parse(saved).getAsJsonObject()
                        .getAsJsonObject("modules").getAsJsonObject("scaffold").getAsJsonObject("settings");
                assertEquals(profile, settings.get("bridge_rotation_profile").getAsString());
                assertNull(settings.get("aim_speed"));
                assertEquals("Calm".equals(aim)?18D:"Swift".equals(aim)?55D:35D,
                        settings.get("min_aim_speed").getAsDouble(),0);
                assertEquals("Calm".equals(aim)?90D:180D,settings.get("max_aim_speed").getAsDouble(),0);
                assertEquals(.73, settings.get("aim_noise_amount").getAsDouble(), 0);
                assertTrue(settings.get("bridge_rotation_speed") == null);
                if ("Custom".equals(aim)) {
                    assertEquals(21D, settings.get("initial_yaw_speed").getAsDouble(), 0);
                    assertEquals(23D, settings.get("initial_pitch_speed").getAsDouble(), 0);
                    assertEquals(27D, settings.get("keeping_yaw_speed").getAsDouble(), 0);
                    assertEquals(29D, settings.get("keeping_pitch_speed").getAsDouble(), 0);
                }
                assertTrue(configs.getLastError(), configs.saveConfig("BridgeMotion"));
                scaffold.getModeSetting("Scaffold Approach").setValue("Neutron");
                scaffold.getModeSetting("Bridge Rotation Profile").setValue("Adaptive".equals(profile) ? "Godbridge" : "Adaptive");
                scaffold.getModeSetting("Aim Speed").setValue("Calm".equals(aim) ? "Swift" : "Calm");
                scaffold.getNumberSetting("Bridge Rotation Variation").setValue(0D);
                assertTrue(configs.getLastError(), configs.loadConfig("BridgeMotion"));
                assertEquals(saved, configs.serialize());
                assertEquals("NCP", scaffold.getModeSetting("Rotation Mode").getValue());
                assertEquals("Custom".equals(aim),scaffold.getBooleanSetting("Phase Speed Limits").getValue());
                assertEquals("Calm".equals(aim)?18D:"Swift".equals(aim)?55D:35D,
                        scaffold.getNumberSetting("Min Aim Speed").getValue(),0);
                assertEquals(11, scaffold.getNumberSetting("Yaw Speed").getValue(), 0);
                assertEquals(17, scaffold.getNumberSetting("Pitch Speed").getValue(), 0);
            }
        }
    }

    /** W08 configs keep loading without a rewrite; the retired combo maps onto Aim Speed. */
    @Test public void legacyBridgeRotationSpeedLabelsMigrateIntoAimSpeed() throws Exception {
        for (String[] pair : new String[][]{{"Calm", "Calm"}, {"Balanced", "Balanced"}, {"Fast", "Swift"}}) {
            Scaffold scaffold = new Scaffold();
            Path directory = temp.newFolder().toPath();
            ConfigManager configs = configs(directory, scaffold);
            String saved = "{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":{"
                    + "\"scaffold_approach\":\"Adaptive Bridge\",\"bridge_rotation_speed\":\"" + pair[0] + "\"}}}}";
            Files.write(directory.resolve("LegacySpeed.json"), saved.getBytes(StandardCharsets.UTF_8));
            assertTrue(configs.getLastError(), configs.loadConfig("LegacySpeed"));
            assertEquals("Adaptive Bridge", scaffold.getModeSetting("Scaffold Approach").getValue());
            assertEquals(pair[1], scaffold.getModeSetting("Aim Speed").getValue());
            assertEquals(saved, new String(Files.readAllBytes(directory.resolve("LegacySpeed.json")),
                    StandardCharsets.UTF_8));
        }
    }

    @Test public void a1ConfigGetsMotionDefaultsWithoutRewritingOrLosingItsOldSettings() throws Exception {
        Scaffold scaffold = new Scaffold();
        Path directory = temp.newFolder().toPath();
        ConfigManager configs = configs(directory, scaffold);
        scaffold.getModeSetting("Bridge Rotation Profile").setValue("Godbridge");
        scaffold.getModeSetting("Bridge Rotation Speed").setValue("Fast");
        scaffold.getNumberSetting("Bridge Rotation Variation").setValue(1D);
        String saved = "{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":{"
                + "\"scaffold_approach\":\"Adaptive Bridge\",\"bridge_edge_safety\":\"Stop\","
                + "\"bridge_prediction\":2.25,\"downwards\":true,\"yaw_speed\":13,\"pitch_speed\":19}}}}";
        Path file = directory.resolve("A1.json");
        Files.write(file, saved.getBytes(StandardCharsets.UTF_8));
        assertTrue(configs.getLastError(), configs.loadConfig("A1"));
        assertEquals("Adaptive Bridge", scaffold.getModeSetting("Scaffold Approach").getValue());
        assertEquals("Adaptive", scaffold.getModeSetting("Bridge Rotation Profile").getValue());
        assertEquals("Balanced", scaffold.getModeSetting("Bridge Rotation Speed").getValue());
        assertEquals(.25, scaffold.getNumberSetting("Bridge Rotation Variation").getValue(), 0);
        assertEquals("Stop", scaffold.getModeSetting("Bridge Edge Safety").getValue());
        assertEquals(2.25, scaffold.getNumberSetting("Bridge Prediction").getValue(), 0);
        assertTrue(scaffold.getBooleanSetting("Downwards").getValue());
        assertEquals(13, scaffold.getNumberSetting("Yaw Speed").getValue(), 0);
        assertEquals(19, scaffold.getNumberSetting("Pitch Speed").getValue(), 0);
        assertEquals(saved, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test public void invalidProfileOrSpeedIsRejectedAtomically() throws Exception {
        for (String invalid : new String[]{"\"bridge_rotation_profile\":\"Unsafe\"",
                "\"bridge_rotation_speed\":\"Turbo\""}) {
            Scaffold scaffold = new Scaffold();
            scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
            scaffold.getModeSetting("Bridge Rotation Profile").setValue("Godbridge");
            Path directory = temp.newFolder().toPath();
            ConfigManager configs = configs(directory, scaffold);
            String before = configs.serialize();
            String saved = "{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":{" + invalid + "}}}}";
            Files.write(directory.resolve("Invalid.json"), saved.getBytes(StandardCharsets.UTF_8));
            assertFalse(configs.loadConfig("Invalid"));
            assertEquals(before, configs.serialize());
        }
    }
}
