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
        assertArrayEquals(new String[]{"Calm", "Balanced", "Fast"},
                scaffold.getModeSetting("Bridge Rotation Speed").getOptions());
        assertEquals("Adaptive", scaffold.getModeSetting("Bridge Rotation Profile").getValue());
        assertEquals("Balanced", scaffold.getModeSetting("Bridge Rotation Speed").getValue());
        assertEquals(.25, scaffold.getNumberSetting("Bridge Rotation Variation").getValue(), 0);
        assertEquals(0, scaffold.getNumberSetting("Bridge Rotation Variation").getMin(), 0);
        assertEquals(1, scaffold.getNumberSetting("Bridge Rotation Variation").getMax(), 0);
        for (String approach : new String[]{"Neutron", "Arsenic", "Adaptive Bridge"}) {
            scaffold.getModeSetting("Scaffold Approach").setValue(approach);
            boolean bridge = "Adaptive Bridge".equals(approach);
            assertEquals(bridge, scaffold.getModeSetting("Bridge Rotation Profile").isVisible());
            assertEquals(bridge, scaffold.getModeSetting("Bridge Rotation Speed").isVisible());
            assertEquals(bridge, scaffold.getNumberSetting("Bridge Rotation Variation").isVisible());
            if (bridge) {
                assertTrue(scaffold.getNumberSetting("Yaw Speed").isVisible());
                assertTrue(scaffold.getNumberSetting("Pitch Speed").isVisible());
            }
        }
        assertFalse(scaffold.getBooleanSetting("Arsenic Soft Motion").getValue());
        assertEquals("Back", scaffold.getModeSetting("Rotation Mode").getValue());
        assertEquals(35, scaffold.getNumberSetting("Yaw Speed").getValue(), 0);
        assertEquals(35, scaffold.getNumberSetting("Pitch Speed").getValue(), 0);
    }

    @Test public void bothProfilesAndEverySpeedRoundTripWithTheirIndependentSavedValues() throws Exception {
        for (String profile : new String[]{"Adaptive", "Godbridge"}) {
            for (String speed : new String[]{"Calm", "Balanced", "Fast"}) {
                Scaffold scaffold = new Scaffold();
                Path directory = temp.newFolder().toPath();
                ConfigManager configs = configs(directory, scaffold);
                scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
                scaffold.getModeSetting("Bridge Rotation Profile").setValue(profile);
                scaffold.getModeSetting("Bridge Rotation Speed").setValue(speed);
                scaffold.getNumberSetting("Bridge Rotation Variation").setValue(.73);
                scaffold.getNumberSetting("Yaw Speed").setValue(11D);
                scaffold.getNumberSetting("Pitch Speed").setValue(17D);
                scaffold.getModeSetting("Rotation Mode").setValue("NCP");
                String saved = configs.serialize();
                JsonObject settings = new JsonParser().parse(saved).getAsJsonObject()
                        .getAsJsonObject("modules").getAsJsonObject("scaffold").getAsJsonObject("settings");
                assertEquals(profile, settings.get("bridge_rotation_profile").getAsString());
                assertEquals(speed, settings.get("bridge_rotation_speed").getAsString());
                assertEquals(.73, settings.get("bridge_rotation_variation").getAsDouble(), 0);
                assertTrue(configs.getLastError(), configs.saveConfig("BridgeMotion"));
                scaffold.getModeSetting("Scaffold Approach").setValue("Neutron");
                scaffold.getModeSetting("Bridge Rotation Profile").setValue("Adaptive".equals(profile) ? "Godbridge" : "Adaptive");
                scaffold.getNumberSetting("Bridge Rotation Variation").setValue(0D);
                assertTrue(configs.getLastError(), configs.loadConfig("BridgeMotion"));
                assertEquals(saved, configs.serialize());
                assertEquals("NCP", scaffold.getModeSetting("Rotation Mode").getValue());
                assertEquals(11, scaffold.getNumberSetting("Yaw Speed").getValue(), 0);
                assertEquals(17, scaffold.getNumberSetting("Pitch Speed").getValue(), 0);
            }
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
