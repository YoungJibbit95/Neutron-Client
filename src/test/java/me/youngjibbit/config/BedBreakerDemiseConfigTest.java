package me.youngjibbit.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.player.BedBreaker;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Complete Demise BedBreaker profiles and migration of the former mining controls. */
public class BedBreakerDemiseConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private static ConfigManager manager(BedBreaker module, Path directory) {
        return new ConfigManager(directory, Collections.<Module>singletonList(module), () -> false, message -> {});
    }
    private static String v2(String settings) {
        return "{\"version\":2,\"modules\":{\"bedbreaker\":{\"settings\":{" + settings + "}}}}";
    }
    private static void write(Path directory, String name, String content) throws Exception {
        Files.write(directory.resolve(name + ".json"), content.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(Path directory, String name) throws Exception {
        return new String(Files.readAllBytes(directory.resolve(name + ".json")), StandardCharsets.UTF_8);
    }
    private static Map<String,Object> values(BedBreaker module) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (Value<?> value : module.getValues()) result.put(value.getId(), value.getValue());
        return result;
    }
    private static void warmEverySetting(BedBreaker module) {
        for (Value<?> value : module.getValues()) {
            if (value instanceof CheckBoxValue) ((CheckBoxValue) value).setValue(!(Boolean) value.getDefaultValue());
            else if (value instanceof ComboValue) {
                ComboValue choice = (ComboValue) value;
                for (String option : choice.getOptions()) if (!option.equals(value.getDefaultValue())) {
                    choice.setValue(option);
                    break;
                }
            } else if (value instanceof SliderValue) {
                SliderValue slider = (SliderValue) value;
                slider.setValue((Double) value.getDefaultValue() == slider.getMin() ? slider.getMax() : slider.getMin());
            } else fail("Unhandled BedBreaker setting: " + value.getId());
            assertNotEquals("Warm profile must differ: " + value.getId(), value.getDefaultValue(), value.getValue());
        }
    }
    private static Map<String,Object> defaults() {
        Map<String,Object> defaults = new LinkedHashMap<>();
        Object[][] entries = {
                {"legit", false}, {"silent", true}, {"extra_smoothing_factor", (double) .15F},
                {"smooth_mode", "Linear"}, {"accelerate", false}, {"yaw_accel_factor", .25D},
                {"pitch_accel_factor", .25D}, {"imperfect_correlation", false},
                {"yaw_rotation_speed_(min)", 180D}, {"yaw_rotation_speed_(max)", 180D},
                {"pitch_rotation_speed_(min)", 180D}, {"pitch_rotation_speed_(max)", 180D},
                {"movement_correction", "Silent"}, {"short_stop", false}, {"duration", 50D},
                {"rotation_diff_buildup_to_stop", 180D}, {"max_threshold_attempts_to_stop", 1D},
                {"smart_disable_on_death", false}, {"smart_disable_on_world_change", false}
        };
        for (Object[] entry : entries) defaults.put((String) entry[0], entry[1]);
        return defaults;
    }

    @Test public void allSeventeenDemiseControlsAndHostLifecycleChoicesAreRegisteredOnce() {
        BedBreaker module = new BedBreaker();
        assertEquals(defaults(), values(module));
        List<String> ids = new ArrayList<>();
        for (Value<?> value : module.getValues()) ids.add(value.getId());
        assertEquals(19, ids.size()); assertEquals(ids.size(), new HashSet<>(ids).size());
        assertArrayEquals(new String[]{"Linear", "Relative"}, module.getModeSetting("Smooth mode").getOptions());
        assertArrayEquals(new String[]{"Silent", "Strict", "None"}, module.getModeSetting("Movement correction").getOptions());
        assertNull(module.getValueByName("Turn Speed")); assertNull(module.getValueByName("Swing"));
        assertNotNull(module.getBooleanSetting("Smart Disable on Death"));
        assertNotNull(module.getBooleanSetting("Smart Disable on World Change"));
    }

    @Test public void coldAndWarmVersionTwoFlatAndArrayLegacyImportsProduceTheSameCompleteProfile() throws Exception {
        String[] profiles = {
                v2("\"turn_speed\":{\"value\":23.5},\"swing\":{\"value\":false},"
                        + "\"smart_disable_on_death\":true,\"smart_disable_on_world_change\":false"),
                "{\"BedBreaker\":{\"Turn Speed\":{\"value\":23.5},\"Swing\":false,"
                        + "\"Smart Disable on Death\":true,\"Smart Disable on World Change\":false}}",
                "{\"BedBreaker\":{\"settings\":[{\"name\":\"Turn Speed\",\"value\":23.5},"
                        + "{\"name\":\"Swing\",\"value\":false},{\"name\":\"Smart Disable on Death\",\"value\":true},"
                        + "{\"name\":\"Smart Disable on World Change\",\"value\":false}]}}"
        };
        Map<String,Object> expected = defaults(); expected.put("smart_disable_on_death", true);
        for (String profile : profiles) for (boolean warm : new boolean[]{false, true}) {
            BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath();
            List<String> messages = new ArrayList<>();
            ConfigManager config = new ConfigManager(directory, Collections.<Module>singletonList(module), () -> false, messages::add);
            if (warm) warmEverySetting(module);
            write(directory, "Old", profile);
            assertTrue(config.getLastError(), config.loadConfig("Old"));
            assertEquals(expected, values(module)); assertEquals(profile, read(directory, "Old"));
            String message = messages.get(messages.size() - 1);
            assertTrue(message, message.contains("BedBreaker uses Demise defaults where omitted"));
            assertTrue(message, message.contains("Turn Speed has no frame-speed equivalent"));
            assertTrue(message, message.contains("animation packets follow Demise; no local swing setting"));
            assertFalse(message, message.contains("unknown entries"));
        }
    }

    @Test public void explicitDemiseSettingsWinRegardlessOfLegacyFieldOrder() throws Exception {
        List<String> entries = Arrays.asList("\"turn_speed\":1", "\"swing\":false", "\"legit\":true",
                "\"silent\":false", "\"smooth_mode\":\"Relative\"", "\"movement_correction\":\"Strict\"",
                "\"yaw_rotation_speed_(min)\":71.125", "\"yaw_rotation_speed_(max)\":41.875",
                "\"short_stop\":true", "\"duration\":37.5", "\"smart_disable_on_world_change\":true");
        Map<String,Object> expected = defaults();
        expected.put("legit", true); expected.put("silent", false); expected.put("smooth_mode", "Relative");
        expected.put("movement_correction", "Strict"); expected.put("yaw_rotation_speed_(min)", 71.125D);
        expected.put("yaw_rotation_speed_(max)", 41.875D); expected.put("short_stop", true);
        expected.put("duration", 37.5D); expected.put("smart_disable_on_world_change", true);
        for (boolean reversed : new boolean[]{false, true}) {
            BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath();
            ConfigManager config = manager(module, directory); warmEverySetting(module);
            List<String> order = new ArrayList<>(entries); if (reversed) Collections.reverse(order);
            String profile = v2(String.join(",", order)); write(directory, "Mixed", profile);
            assertTrue(config.getLastError(), config.loadConfig("Mixed"));
            assertEquals(expected, values(module)); assertEquals(profile, read(directory, "Mixed"));
        }
    }

    @Test public void partialCurrentProfilesResetOmittedDemiseAndExistingLifecycleDefaults() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module);
        write(directory, "Partial", v2("\"legit\":true,\"movement_correction\":\"None\",\"smart_disable_on_death\":true"));
        assertTrue(config.getLastError(), config.loadConfig("Partial"));
        Map<String,Object> expected = defaults(); expected.put("legit", true);
        expected.put("movement_correction", "None"); expected.put("smart_disable_on_death", true);
        assertEquals(expected, values(module));
        write(directory, "Empty", v2("")); assertTrue(config.getLastError(), config.loadConfig("Empty"));
        assertEquals(defaults(), values(module));
    }

    @Test public void anAbsentBedBreakerProfileLeavesItsCurrentSettingsAlone() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module); Map<String,Object> before = values(module);
        write(directory, "Absent", "{\"version\":2,\"modules\":{}}");
        assertTrue(config.getLastError(), config.loadConfig("Absent")); assertEquals(before, values(module));
    }

    @Test public void everySettingRoundTripsIncludingHiddenValuesFalseAndZero() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module);
        module.getBooleanSetting("Accelerate").setValue(false); module.getBooleanSetting("Short stop").setValue(false);
        module.getModeSetting("Movement correction").setValue("None");
        module.getNumberSetting("Yaw accel factor").setValue(.3333333D);
        module.getNumberSetting("Pitch accel factor").setValue(.7777777D);
        module.getNumberSetting("Duration").setValue(37.5D);
        module.getNumberSetting("Max threshold attempts to stop").setValue(0D);
        Map<String,Object> expected = values(module);
        assertTrue(config.getLastError(), config.saveConfig("Demise")); String serialized = config.serialize();
        for (Value<?> value : module.getValues()) value.reset();
        assertTrue(config.getLastError(), config.loadConfig("Demise"));
        assertEquals(expected, values(module)); assertEquals(serialized, config.serialize());
        assertFalse(module.getBooleanSetting("Silent").getValue());
        assertFalse(module.getNumberSetting("Yaw accel factor").isVisible());
        assertFalse(module.getNumberSetting("Duration").isVisible());
        assertEquals(0D, module.getNumberSetting("Max threshold attempts to stop").getValue(), 0);
    }

    @Test public void invalidCurrentAndRetiredSettingsRejectTheWholeImportAndPreserveFiles() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module);
        String before = config.serialize(); assertTrue(config.getLastError(), config.saveConfig("Preserve"));
        String[] invalidSettings = {
                "\"legit\":\"true\"", "\"silent\":1", "\"smooth_mode\":\"Unknown\"",
                "\"movement_correction\":\"Unknown\"", "\"extra_smoothing_factor\":0",
                "\"yaw_accel_factor\":0.91", "\"pitch_accel_factor\":0",
                "\"yaw_rotation_speed_(min)\":0", "\"pitch_rotation_speed_(max)\":181",
                "\"duration\":24", "\"rotation_diff_buildup_to_stop\":721", "\"max_threshold_attempts_to_stop\":-1",
                "\"smart_disable_on_death\":\"false\"", "\"turn_speed\":0", "\"turn_speed\":181",
                "\"turn_speed\":\"30\"", "\"turn_speed\":null", "\"turn_speed\":1e1000",
                "\"turn_speed\":{}", "\"swing\":\"false\"", "\"swing\":1", "\"swing\":null"
        };
        for (String settings : invalidSettings) {
            String invalid = v2("\"imperfect_correlation\":false," + settings); write(directory, "Invalid", invalid);
            assertFalse(settings, config.loadConfig("Invalid")); assertEquals(before, config.serialize());
            assertEquals(invalid, read(directory, "Invalid"));
            assertFalse(config.saveConfig("Preserve", invalid)); assertEquals(before, read(directory, "Preserve"));
        }
    }

    @Test public void duplicateCurrentAndRetiredNamesAreRejectedAcrossLegacyFormats() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module); String before = config.serialize();
        String[] profiles = {
                v2("\"turn_speed\":30,\"Turn Speed\":40"), v2("\"swing\":true,\"Swing\":false"),
                v2("\"silent\":true,\"Silent\":false"),
                "{\"BedBreaker\":{\"Turn Speed\":30,\"turn_speed\":40}}",
                "{\"BedBreaker\":{\"settings\":[{\"name\":\"Swing\",\"value\":false},"
                        + "{\"name\":\"swing\",\"value\":true}]}}"
        };
        for (String profile : profiles) {
            write(directory, "Duplicate", profile); assertFalse(profile, config.loadConfig("Duplicate"));
            assertEquals(before, config.serialize()); assertEquals(profile, read(directory, "Duplicate"));
        }
    }

    @Test public void fractionalFloatPrecisionAndCrossedSpeedEndpointsSurviveRoundTrip() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath(); ConfigManager config = manager(module, directory);
        String profile = v2("\"extra_smoothing_factor\":0.014321,\"yaw_accel_factor\":0.249137,\"pitch_accel_factor\":0.794381,"
                + "\"yaw_rotation_speed_(min)\":80.125,\"yaw_rotation_speed_(max)\":10.375,"
                + "\"pitch_rotation_speed_(min)\":60.625,\"pitch_rotation_speed_(max)\":20.875,"
                + "\"duration\":37.5,\"rotation_diff_buildup_to_stop\":180.5,\"max_threshold_attempts_to_stop\":1.5");
        write(directory, "Fractional", profile); assertTrue(config.getLastError(), config.loadConfig("Fractional"));
        Map<String,Object> expected = defaults();
        expected.put("extra_smoothing_factor", (double) .014321F); expected.put("yaw_accel_factor", (double) .249137F);
        expected.put("pitch_accel_factor", (double) .794381F); expected.put("yaw_rotation_speed_(min)", 80.125D);
        expected.put("yaw_rotation_speed_(max)", 10.375D); expected.put("pitch_rotation_speed_(min)", 60.625D);
        expected.put("pitch_rotation_speed_(max)", 20.875D); expected.put("duration", 37.5D);
        expected.put("rotation_diff_buildup_to_stop", 180.5D); expected.put("max_threshold_attempts_to_stop", 1.5D);
        assertEquals(expected, values(module)); assertEquals(profile, read(directory, "Fractional"));
        assertTrue(config.getLastError(), config.saveConfig("RoundTrip"));
        for (Value<?> value : module.getValues()) value.reset();
        assertTrue(config.getLastError(), config.loadConfig("RoundTrip")); assertEquals(expected, values(module));
    }

    @Test public void serializedFloatBoundariesRemainValidWhenReloaded() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath(); ConfigManager config = manager(module, directory);
        write(directory, "Bounds", v2("\"extra_smoothing_factor\":0.01,\"yaw_accel_factor\":0.9,\"pitch_accel_factor\":0.01,"
                + "\"yaw_rotation_speed_(min)\":0.01,\"pitch_rotation_speed_(min)\":0.01,\"max_threshold_attempts_to_stop\":5"));
        assertTrue(config.getLastError(), config.loadConfig("Bounds")); Map<String,Object> expected = values(module);
        assertEquals((double) .01F, module.getNumberSetting("Extra smoothing factor").getValue(), 0);
        assertEquals((double) .9F, module.getNumberSetting("Yaw accel factor").getValue(), 0);
        assertTrue(config.getLastError(), config.saveConfig("RoundTrip")); warmEverySetting(module);
        assertTrue(config.getLastError(), config.loadConfig("RoundTrip")); assertEquals(expected, values(module));
    }

    @Test public void automaticStateRemovesRetiredFieldsAndPreservesHostChoicesAndUnknownData() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath(); ConfigManager config = manager(module, directory);
        JsonObject root = new JsonParser().parse(v2("\"Turn Speed\":45,\"Swing\":false,\"Silent\":false,"
                + "\"smart_disable_on_death\":true,\"smart_disable_on_world_change\":true,\"future_option\":17")).getAsJsonObject();
        root.addProperty("future_metadata", "keep"); root.getAsJsonObject("modules").add("future_module", new JsonObject());
        write(directory, ".last-state", root.toString()); config.initialize(Collections.emptyList());
        assertTrue(config.getLastError(), config.saveDefaultConfig());
        JsonObject saved = new JsonParser().parse(read(directory, ".last-state")).getAsJsonObject();
        JsonObject settings = saved.getAsJsonObject("modules").getAsJsonObject("bedbreaker").getAsJsonObject("settings");
        assertEquals(20, settings.size()); assertEquals(17, settings.get("future_option").getAsInt());
        assertFalse(settings.get("silent").getAsBoolean());
        assertTrue(settings.get("smart_disable_on_death").getAsBoolean());
        assertTrue(settings.get("smart_disable_on_world_change").getAsBoolean());
        for (String retired : Arrays.asList("Turn Speed", "turn_speed", "Swing", "swing", "Silent"))
            assertFalse(retired, settings.has(retired));
        assertEquals("keep", saved.get("future_metadata").getAsString()); assertTrue(saved.getAsJsonObject("modules").has("future_module"));
        BedBreaker restored = new BedBreaker(); ConfigManager reload = manager(restored, directory); reload.initialize(Collections.emptyList());
        assertEquals(values(module), values(restored)); assertTrue(reload.getLastError(), reload.saveDefaultConfig());
    }

    @Test public void recognizedLegacyAutomaticStateRemainsWritable() throws Exception {
        BedBreaker module = new BedBreaker(); Path directory = temporary.newFolder().toPath(); ConfigManager config = manager(module, directory);
        write(directory, ".last-state", "{\"BedBreaker\":{\"Turn Speed\":30,\"Swing\":false,\"Smart Disable on Death\":true}}");
        config.initialize(Collections.emptyList()); assertTrue(config.getLastError(), config.saveDefaultConfig());
        JsonObject saved = new JsonParser().parse(read(directory, ".last-state")).getAsJsonObject();
        assertEquals(2, saved.get("version").getAsInt());
        JsonObject settings = saved.getAsJsonObject("modules").getAsJsonObject("bedbreaker").getAsJsonObject("settings");
        assertEquals(19, settings.size()); assertTrue(settings.get("silent").getAsBoolean());
        assertEquals("Silent", settings.get("movement_correction").getAsString());
        assertEquals(180D, settings.get("yaw_rotation_speed_(min)").getAsDouble(), 0);
        assertTrue(settings.get("smart_disable_on_death").getAsBoolean());
        assertFalse(settings.has("turn_speed")); assertFalse(settings.has("swing"));
    }
}
