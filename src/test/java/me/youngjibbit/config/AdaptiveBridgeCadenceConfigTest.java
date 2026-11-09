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

/** Cadence configuration does not replace legacy interpolation or opt users into jumping. */
public class AdaptiveBridgeCadenceConfigTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private ConfigManager configs(Path directory, Scaffold scaffold) {
        return new ConfigManager(directory, Collections.<Module>singletonList(scaffold),
                () -> false, message -> { });
    }

    @Test public void cadenceDefaultsAndBoundsAreOptInAndKeepLegacyInterpolationNumeric() {
        Scaffold scaffold = new Scaffold();
        assertEquals("Neutron", scaffold.getModeSetting("Scaffold Approach").getValue());
        assertEquals(12, scaffold.getNumberSetting("Place Min CPS").getValue(), 0);
        assertEquals(16, scaffold.getNumberSetting("Place Max CPS").getValue(), 0);
        for (String name : new String[]{"Place Min CPS", "Place Max CPS"}) {
            assertEquals(1, scaffold.getNumberSetting(name).getMin(), 0);
            assertEquals(20, scaffold.getNumberSetting(name).getMax(), 0);
            assertFalse(scaffold.getNumberSetting(name).isInteger());
        }
        assertArrayEquals(new String[]{"Basic", "Smoothstep", "Perlin"},
                scaffold.getModeSetting("Click Profile").getOptions());
        assertArrayEquals(new String[]{"Stable", "Smooth", "Dynamic"},
                scaffold.getModeSetting("Place Interpolation").getOptions());
        assertEquals("Smoothstep", scaffold.getModeSetting("Click Profile").getValue());
        assertEquals("Smooth", scaffold.getModeSetting("Place Interpolation").getValue());
        assertEquals(.35, scaffold.getNumberSetting("Variation Strength").getValue(), 0);
        assertEquals(0, scaffold.getNumberSetting("Variation Strength").getMin(), 0);
        assertEquals(1, scaffold.getNumberSetting("Variation Strength").getMax(), 0);
        assertFalse(scaffold.getBooleanSetting("Place Debug").getValue());
        assertEquals(5, scaffold.getNumberSetting("Interpolation").getValue(), 0);
        for (String approach : new String[]{"Neutron", "Arsenic", "Adaptive Bridge"}) {
            scaffold.getModeSetting("Scaffold Approach").setValue(approach);
            boolean bridge = "Adaptive Bridge".equals(approach);
            for (String name : new String[]{"Place Min CPS", "Place Max CPS", "Click Profile",
                    "Place Interpolation", "Variation Strength", "Place Debug"})
                assertEquals(name + " in " + approach, bridge, scaffold.getValueByName(name).isVisible());
            if (bridge) assertFalse(scaffold.getNumberSetting("Interpolation").isVisible());
        }
        assertEquals(5, scaffold.getNumberSetting("Interpolation").getValue(), 0);
    }

    @Test public void godbridgeJumpRequiresBothTheProfileAndExplicitEnableSetting() {
        Scaffold scaffold = new Scaffold();
        assertFalse(scaffold.getBooleanSetting("Godbridge Auto Jump").getValue());
        assertEquals(8, scaffold.getNumberSetting("Godbridge Jump After Blocks").getValue(), 0);
        assertEquals(1, scaffold.getNumberSetting("Godbridge Jump After Blocks").getMin(), 0);
        assertEquals(20, scaffold.getNumberSetting("Godbridge Jump After Blocks").getMax(), 0);
        assertTrue(scaffold.getNumberSetting("Godbridge Jump After Blocks").isInteger());
        scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
        assertFalse(scaffold.getBooleanSetting("Godbridge Auto Jump").isVisible());
        assertFalse(scaffold.getNumberSetting("Godbridge Jump After Blocks").isVisible());
        scaffold.getModeSetting("Bridge Rotation Profile").setValue("Godbridge");
        assertTrue(scaffold.getBooleanSetting("Godbridge Auto Jump").isVisible());
        assertFalse(scaffold.getNumberSetting("Godbridge Jump After Blocks").isVisible());
        scaffold.getBooleanSetting("Godbridge Auto Jump").setValue(true);
        assertTrue(scaffold.getNumberSetting("Godbridge Jump After Blocks").isVisible());
        for (String approach : new String[]{"Neutron", "Arsenic"}) {
            scaffold.getModeSetting("Scaffold Approach").setValue(approach);
            assertFalse(scaffold.getBooleanSetting("Godbridge Auto Jump").isVisible());
            assertFalse(scaffold.getNumberSetting("Godbridge Jump After Blocks").isVisible());
        }
    }

    @Test public void everyProfileAndInterpolationRoundTripsWithoutReplacingTheOldSlider() throws Exception {
        for (String profile : new String[]{"Basic", "Smoothstep", "Perlin"}) {
            for (String interpolation : new String[]{"Stable", "Smooth", "Dynamic"}) {
                Scaffold scaffold = new Scaffold();
                Path directory = temp.newFolder().toPath();
                ConfigManager configs = configs(directory, scaffold);
                scaffold.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
                scaffold.getModeSetting("Bridge Rotation Profile").setValue("Godbridge");
                scaffold.getNumberSetting("Place Min CPS").setValue(18.5);
                scaffold.getNumberSetting("Place Max CPS").setValue(7.25);
                scaffold.getModeSetting("Click Profile").setValue(profile);
                scaffold.getModeSetting("Place Interpolation").setValue(interpolation);
                scaffold.getNumberSetting("Variation Strength").setValue(.625);
                scaffold.getBooleanSetting("Place Debug").setValue(true);
                scaffold.getBooleanSetting("Godbridge Auto Jump").setValue(true);
                scaffold.getNumberSetting("Godbridge Jump After Blocks").setValue(9D);
                scaffold.getNumberSetting("Interpolation").setValue(7.5);
                String saved = configs.serialize();
                JsonObject settings = new JsonParser().parse(saved).getAsJsonObject()
                        .getAsJsonObject("modules").getAsJsonObject("scaffold").getAsJsonObject("settings");
                assertEquals(7.5, settings.get("interpolation").getAsDouble(), 0);
                assertEquals(interpolation, settings.get("place_interpolation").getAsString());
                assertEquals(profile, settings.get("click_profile").getAsString());
                assertTrue(configs.getLastError(), configs.saveConfig("Cadence"));
                scaffold.getModeSetting("Scaffold Approach").setValue("Neutron");
                scaffold.getNumberSetting("Interpolation").setValue(3D);
                scaffold.getNumberSetting("Variation Strength").setValue(0D);
                scaffold.getBooleanSetting("Godbridge Auto Jump").setValue(false);
                assertTrue(configs.getLastError(), configs.loadConfig("Cadence"));
                assertEquals(saved, configs.serialize());
                assertEquals(18.5, scaffold.getNumberSetting("Place Min CPS").getValue(), 0);
                assertEquals(7.25, scaffold.getNumberSetting("Place Max CPS").getValue(), 0);
            }
        }
    }

    @Test public void a2ConfigKeepsItsValuesAndCannotImplicitlyEnableTheNewGodbridgeJump() throws Exception {
        Scaffold scaffold = new Scaffold();
        Path directory = temp.newFolder().toPath();
        ConfigManager configs = configs(directory, scaffold);
        scaffold.getNumberSetting("Place Min CPS").setValue(2D);
        scaffold.getModeSetting("Click Profile").setValue("Basic");
        scaffold.getModeSetting("Place Interpolation").setValue("Dynamic");
        scaffold.getBooleanSetting("Godbridge Auto Jump").setValue(true);
        String saved = "{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":{"
                + "\"scaffold_approach\":\"Adaptive Bridge\",\"bridge_rotation_profile\":\"Godbridge\","
                + "\"bridge_rotation_speed\":\"Fast\",\"bridge_rotation_variation\":0.73,"
                + "\"interpolation\":7.5,\"auto_jump\":true,\"rotations\":false}}}}";
        Path file = directory.resolve("A2.json");
        Files.write(file, saved.getBytes(StandardCharsets.UTF_8));
        assertTrue(configs.getLastError(), configs.loadConfig("A2"));
        assertEquals("Godbridge", scaffold.getModeSetting("Bridge Rotation Profile").getValue());
        // The retired W08 speed combo migrates into Aim Speed: Fast keeps its pace as Swift.
        assertEquals("Swift", scaffold.getModeSetting("Aim Speed").getValue());
        assertEquals("Balanced", scaffold.getModeSetting("Aim Speed").getDefaultValue());
        assertEquals(.73, scaffold.getNumberSetting("Bridge Rotation Variation").getValue(), 0);
        assertEquals(7.5, scaffold.getNumberSetting("Interpolation").getValue(), 0);
        assertTrue(scaffold.getBooleanSetting("Auto Jump").getValue());
        assertFalse(scaffold.getBooleanSetting("Rotations").getValue());
        assertEquals(12, scaffold.getNumberSetting("Place Min CPS").getValue(), 0);
        assertEquals(16, scaffold.getNumberSetting("Place Max CPS").getValue(), 0);
        assertEquals("Smoothstep", scaffold.getModeSetting("Click Profile").getValue());
        assertEquals("Smooth", scaffold.getModeSetting("Place Interpolation").getValue());
        assertEquals(.35, scaffold.getNumberSetting("Variation Strength").getValue(), 0);
        assertFalse(scaffold.getBooleanSetting("Place Debug").getValue());
        assertFalse(scaffold.getBooleanSetting("Godbridge Auto Jump").getValue());
        assertEquals(8, scaffold.getNumberSetting("Godbridge Jump After Blocks").getValue(), 0);
        assertEquals(saved, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    @Test public void invalidCadenceModesAreAtomicAndCannotRetypeLegacyInterpolation() throws Exception {
        for (String invalid : new String[]{"\"click_profile\":\"Gaussian\"",
                "\"place_interpolation\":\"Unlimited\"", "\"interpolation\":\"Dynamic\""}) {
            Scaffold scaffold = new Scaffold();
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
