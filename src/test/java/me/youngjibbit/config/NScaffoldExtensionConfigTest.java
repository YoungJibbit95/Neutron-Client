package me.youngjibbit.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.NScaffold;
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

/** Demise's complete Scaffold profile and migration from the former bounded planner. */
public class NScaffoldExtensionConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ConfigManager manager(NScaffold module, Path directory) {
        return new ConfigManager(directory, Collections.<Module>singletonList(module), () -> false, message -> {});
    }
    private static String v2(String settings) {
        return "{\"version\":2,\"modules\":{\"nscaffold\":{\"settings\":{" + settings + "}}}}";
    }
    private static void write(Path directory, String name, String content) throws Exception {
        Files.write(directory.resolve(name + ".json"), content.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(Path directory, String name) throws Exception {
        return new String(Files.readAllBytes(directory.resolve(name + ".json")), StandardCharsets.UTF_8);
    }
    private static Map<String,Object> values(NScaffold module) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (Value<?> value : module.getValues()) result.put(value.getId(), value.getValue());
        return result;
    }
    private static void warmEverySetting(NScaffold module) {
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
            } else fail("Unhandled Scaffold setting: " + value.getId());
            assertNotEquals("Warm profile must differ: " + value.getId(), value.getDefaultValue(), value.getValue());
        }
    }
    private static Map<String,Object> originalDefaults() {
        Map<String,Object> defaults = new LinkedHashMap<>();
        Object[][] entries = {
                {"mode", "Normal"}, {"straight_telly_ticks", 4D}, {"diagonal_telly_ticks", 2D},
                {"rotations", "Normal"}, {"static_ify", false}, {"clutch", false},
                {"clutch_criteria", "MouseOver"}, {"clutch_rotation_mode", "Center"},
                {"instant_rot_always", false}, {"instant_rot_on_miss", false}, {"instant_rot_predict", false},
                {"auto_sneak_on_clutch", false}, {"ground_ticks_to_sneak", 5D},
                {"silent", true}, {"extra_smoothing_factor", (double) .15F}, {"smooth_mode", "Linear"},
                {"accelerate", false}, {"yaw_accel_factor", .25D}, {"pitch_accel_factor", .25D},
                {"imperfect_correlation", false}, {"yaw_rotation_speed_(min)", 180D},
                {"yaw_rotation_speed_(max)", 180D}, {"pitch_rotation_speed_(min)", 180D},
                {"pitch_rotation_speed_(max)", 180D}, {"movement_correction", "Silent"},
                {"short_stop", false}, {"duration", 50D}, {"rotation_diff_buildup_to_stop", 180D},
                {"max_threshold_attempts_to_stop", 1D}, {"sprint_mode", "Normal"}, {"speed_mode", "None"},
                {"swing", true}, {"raytrace", true}, {"placement_mode", "Default"},
                {"min_cps", 20D}, {"max_cps", 20D}, {"edge_safety", "Stop"}, {"keepy", false}, {"speed_keepy", false},
                {"jump", false}, {"safewalk", false}, {"safewalk_when_no_data", false},
                {"hover", false}, {"sneak", false}, {"target_block_esp", false}, {"strict_raytrace", false},
                {"blocks_to_jump", 6D}, {"only_straight", true}, {"blocks_to_sneak", 7D},
                {"only_sneak_on_ground", true}, {"tower", "Jump"}, {"tower_move", "Jump"},
                {"pulldown_motion", (double) .95f}
        };
        for (Object[] entry : entries) defaults.put((String) entry[0], entry[1]);
        return defaults;
    }

    @Test public void allScaffoldControlsAndOriginalChoicesAreRegisteredOnce() {
        NScaffold module = new NScaffold();
        assertEquals(originalDefaults(), values(module));
        List<String> ids = new ArrayList<>();
        for (Value<?> value : module.getValues()) ids.add(value.getId());
        assertEquals(53, ids.size());
        assertEquals(ids.size(), new HashSet<>(ids).size());
        assertArrayEquals(new String[]{"Normal", "Breezily", "Telly"}, module.getModeSetting("Mode").getOptions());
        assertArrayEquals(new String[]{"Normal", "Center", "GodBridge", "Reverse"}, module.getModeSetting("Rotations").getOptions());
        assertArrayEquals(new String[]{"MouseOver", "Prediction"}, module.getModeSetting("Clutch criteria").getOptions());
        assertArrayEquals(new String[]{"Normal", "Center"}, module.getModeSetting("Clutch rotation mode").getOptions());
        assertArrayEquals(new String[]{"Linear", "Relative"}, module.getModeSetting("Smooth mode").getOptions());
        assertArrayEquals(new String[]{"Silent", "Strict", "None"}, module.getModeSetting("Movement correction").getOptions());
        assertArrayEquals(new String[]{"Normal", "Silent", "Ground", "Air", "None"}, module.getModeSetting("Sprint mode").getOptions());
        assertArrayEquals(new String[]{"Tick", "Exempt", "Diagonal", "None"}, module.getModeSetting("Speed mode").getOptions());
        assertArrayEquals(new String[]{"Default", "Legit"}, module.getModeSetting("Placement mode").getOptions());
        assertArrayEquals(new String[]{"Off", "Stop", "Sneak"}, module.getModeSetting("Edge safety").getOptions());
        for (String tower : Arrays.asList("Tower", "Tower move"))
            assertArrayEquals(new String[]{"Jump", "Vanilla", "PullDown", "NCP"}, module.getModeSetting(tower).getOptions());
        for (String obsolete : Arrays.asList("Advanced Rotations", "Turn Speed", "Visible Rotation", "Build Mode",
                "Godbridge Pitch", "Look Ahead", "Auto Select Blocks", "Placement Delay",
                "Smart Disable on Death", "Smart Disable on World Change"))
            assertNull(obsolete, module.getValueByName(obsolete));
    }

    @Test public void coldAndWarmVersionTwoFlatAndArrayImportsMigrateEquivalentSettings() throws Exception {
        String[] profiles = {
                v2("\"visible_rotation\":\"Godbridge\",\"build_mode\":\"Telly\",\"keep_height\":true,"
                        + "\"fast_clutch_always\":true,\"fast_clutch_prediction\":true,\"short_stop_duration_(ms)\":250,"
                        + "\"turn_speed\":23,\"advanced_rotations\":false,\"smart_disable_on_death\":true"),
                "{\"NScaffold\":{\"Visible Rotation\":{\"value\":\"Godbridge\"},\"Build Mode\":\"Telly\",\"Keep Height\":true,"
                        + "\"Fast Clutch Always\":true,\"Fast Clutch Prediction\":true,\"Short Stop Duration (ms)\":250,"
                        + "\"Turn Speed\":23,\"Advanced Rotations\":false,\"Smart Disable on Death\":true}}",
                "{\"NScaffold\":{\"settings\":[{\"name\":\"Visible Rotation\",\"value\":\"Godbridge\"},"
                        + "{\"name\":\"Build Mode\",\"value\":\"Telly\"},{\"name\":\"Keep Height\",\"value\":true},"
                        + "{\"name\":\"Fast Clutch Always\",\"value\":true},{\"name\":\"Fast Clutch Prediction\",\"value\":true},"
                        + "{\"name\":\"Short Stop Duration (ms)\",\"value\":250},{\"name\":\"Turn Speed\",\"value\":23},"
                        + "{\"name\":\"Advanced Rotations\",\"value\":false},{\"name\":\"Smart Disable on Death\",\"value\":true}]}}"
        };
        Map<String,Object> expected = originalDefaults();
        expected.put("rotations", "GodBridge"); expected.put("mode", "Telly"); expected.put("keepy", true);
        expected.put("instant_rot_always", true); expected.put("instant_rot_predict", true); expected.put("duration", 250D);
        for (String profile : profiles) for (boolean warm : new boolean[]{false, true}) {
            NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath();
            ConfigManager config = manager(module, directory);
            if (warm) warmEverySetting(module);
            write(directory, "Old", profile);
            assertTrue(config.getLastError(), config.loadConfig("Old"));
            assertEquals(expected, values(module));
            assertEquals(profile, read(directory, "Old"));
        }
    }

    @Test public void standardAliasUsesNormalAndDoesNotTurnLegacyTurnSpeedIntoFrameSpeed() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module);
        write(directory, "Original", v2("\"visible_rotation\":\"Standard\",\"turn_speed\":1,"
                + "\"godbridge_pitch\":89,\"look_ahead\":1,\"auto_select_blocks\":false,\"placement_delay\":500,"
                + "\"advanced_rotations\":false,\"smart_disable_on_death\":false,\"smart_disable_on_world_change\":false"));
        assertTrue(config.getLastError(), config.loadConfig("Original"));
        assertEquals(originalDefaults(), values(module));
    }

    @Test public void everyOriginalSettingRoundTripsIncludingHiddenChoicesFalseAndZero() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module);
        module.getModeSetting("Mode").setValue("Telly");
        module.getModeSetting("Rotations").setValue("Reverse");
        module.getModeSetting("Tower move").setValue("PullDown");
        module.getNumberSetting("Ground ticks to sneak").setValue(0D);
        module.getNumberSetting("Blocks to jump").setValue(0D);
        module.getNumberSetting("Max threshold attempts to stop").setValue(0D);
        Map<String,Object> expected = values(module);
        assertTrue(config.getLastError(), config.saveConfig("Demise")); String serialized = config.serialize();
        for (Value<?> value : module.getValues()) value.reset();
        assertTrue(config.getLastError(), config.loadConfig("Demise"));
        assertEquals(expected, values(module)); assertEquals(serialized, config.serialize());
        assertFalse(module.getBooleanSetting("Silent").getValue());
        assertFalse(module.getBooleanSetting("RayTrace").getValue());
        assertEquals(0D, module.getNumberSetting("Blocks to jump").getValue(), 0);
    }

    @Test public void explicitOriginalKeysWinOverAliasesRegardlessOfJsonOrder() throws Exception {
        List<String> entries = Arrays.asList("\"build_mode\":\"Telly\"", "\"mode\":\"Normal\"",
                "\"visible_rotation\":\"Godbridge\"", "\"rotations\":\"Center\"",
                "\"keep_height\":true", "\"keepy\":false", "\"fast_clutch_always\":true", "\"instant_rot_always\":false",
                "\"fast_clutch_on_miss\":true", "\"instant_rot_on_miss\":false",
                "\"fast_clutch_prediction\":true", "\"instant_rot_predict\":false",
                "\"short_stop_duration_(ms)\":250", "\"duration\":25");
        Map<String,Object> expected = originalDefaults(); expected.put("rotations", "Center"); expected.put("duration", 25D);
        for (boolean reversed : new boolean[]{false, true}) {
            NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath();
            ConfigManager config = manager(module, directory); warmEverySetting(module);
            List<String> order = new ArrayList<>(entries); if (reversed) Collections.reverse(order);
            String profile = v2(String.join(",", order)); write(directory, "Mixed", profile);
            assertTrue(config.getLastError(), config.loadConfig("Mixed"));
            assertEquals(expected, values(module)); assertEquals(profile, read(directory, "Mixed"));
        }
    }

    @Test public void partialOriginalProfileRestoresOmittedDefaultsWithoutLeakingWarmState() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module);
        write(directory, "Partial", v2("\"mode\":\"Telly\",\"silent\":false,\"movement_correction\":\"Strict\","
                + "\"clutch\":true,\"instant_rot_on_miss\":true,\"ground_ticks_to_sneak\":0,\"raytrace\":false"));
        assertTrue(config.getLastError(), config.loadConfig("Partial"));
        Map<String,Object> expected = originalDefaults(); expected.put("mode", "Telly"); expected.put("silent", false);
        expected.put("movement_correction", "Strict"); expected.put("clutch", true);
        expected.put("instant_rot_on_miss", true); expected.put("ground_ticks_to_sneak", 0D); expected.put("raytrace", false);
        assertEquals(expected, values(module));
        write(directory, "Reset", v2("")); assertTrue(config.getLastError(), config.loadConfig("Reset"));
        assertEquals(originalDefaults(), values(module));
    }

    @Test public void absentModuleDoesNotChangeItsExistingProfile() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module); Map<String,Object> before = values(module);
        write(directory, "Absent", "{\"version\":2,\"modules\":{}}");
        assertTrue(config.getLastError(), config.loadConfig("Absent")); assertEquals(before, values(module));
    }

    @Test public void invalidOriginalAndLegacyChoicesRejectWholeImportWithoutChangingFiles() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module);
        String before = config.serialize(); assertTrue(config.getLastError(), config.saveConfig("Preserve"));
        List<String> modes = new ArrayList<>(Arrays.asList("build_mode", "visible_rotation"));
        for (Value<?> value : module.getValues()) if (value instanceof ComboValue) modes.add(value.getId());
        for (String id : modes) {
            String invalid = v2("\"ground_ticks_to_sneak\":0,\"" + id + "\":\"Unknown choice\"");
            write(directory, "Invalid", invalid);
            assertFalse(id, config.loadConfig("Invalid")); assertEquals(before, config.serialize());
            assertEquals(invalid, read(directory, "Invalid"));
            assertFalse(config.saveConfig("Preserve", invalid)); assertEquals(before, read(directory, "Preserve"));
        }
    }

    @Test public void retiredControlsAndAliasesStillRejectMalformedValuesAndDuplicates() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath();
        ConfigManager config = manager(module, directory); warmEverySetting(module); String before = config.serialize();
        String[] invalidSettings = {"\"advanced_rotations\":\"false\"", "\"turn_speed\":0", "\"look_ahead\":1.1",
                "\"smart_disable_on_death\":1", "\"fast_clutch_on_miss\":\"true\"",
                "\"short_stop_duration_(ms)\":24", "\"build_mode\":\"Normal\",\"Build Mode\":\"Telly\"",
                "\"rotations\":\"Normal\",\"Rotations\":\"Center\""};
        for (String settings : invalidSettings) {
            write(directory, "Invalid", v2(settings)); assertFalse(settings, config.loadConfig("Invalid"));
            assertEquals(before, config.serialize());
        }
    }

    @Test public void crossedSpeedEndpointsArePreservedExactlyAsStored() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath(); ConfigManager config = manager(module, directory);
        String profile = v2("\"extra_smoothing_factor\":1,\"yaw_rotation_speed_(min)\":80,\"yaw_rotation_speed_(max)\":10,"
                + "\"pitch_rotation_speed_(min)\":60,\"pitch_rotation_speed_(max)\":20");
        write(directory, "Crossed", profile); assertTrue(config.getLastError(), config.loadConfig("Crossed"));
        assertEquals(80D, module.getNumberSetting("Yaw rotation speed (min)").getValue(), 0);
        assertEquals(10D, module.getNumberSetting("Yaw rotation speed (max)").getValue(), 0);
        assertEquals(60D, module.getNumberSetting("Pitch rotation speed (min)").getValue(), 0);
        assertEquals(20D, module.getNumberSetting("Pitch rotation speed (max)").getValue(), 0);
        assertEquals(profile, read(directory, "Crossed"));
    }

    @Test public void fractionalSourceSettingsAreNotQuantizedToUiSteps() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath(); ConfigManager config = manager(module, directory);
        String profile = v2("\"straight_telly_ticks\":4.75,\"diagonal_telly_ticks\":2.75,\"ground_ticks_to_sneak\":4.5,"
                + "\"blocks_to_jump\":6.5,\"blocks_to_sneak\":7.5,\"duration\":37.5,"
                + "\"rotation_diff_buildup_to_stop\":180.5,\"max_threshold_attempts_to_stop\":1.5");
        write(directory, "Fractional", profile); assertTrue(config.getLastError(), config.loadConfig("Fractional"));
        Map<String,Object> expected = originalDefaults();
        expected.put("straight_telly_ticks", 4.75D); expected.put("diagonal_telly_ticks", 2.75D);
        expected.put("ground_ticks_to_sneak", 4.5D); expected.put("blocks_to_jump", 6.5D);
        expected.put("blocks_to_sneak", 7.5D); expected.put("duration", 37.5D);
        expected.put("rotation_diff_buildup_to_stop", 180.5D); expected.put("max_threshold_attempts_to_stop", 1.5D);
        assertEquals(expected, values(module));
        assertTrue(config.getLastError(), config.saveConfig("FractionalRoundtrip"));
        for (Value<?> value : module.getValues()) value.reset();
        assertTrue(config.getLastError(), config.loadConfig("FractionalRoundtrip")); assertEquals(expected, values(module));
    }

    @Test public void automaticStateDropsKnownLegacyFieldsAndPreservesUnknownData() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath(); ConfigManager config = manager(module, directory);
        JsonObject root = new JsonParser().parse(v2("\"Visible Rotation\":\"Reverse\",\"build_mode\":\"Telly\","
                + "\"turn_speed\":45,\"advanced_rotations\":false,\"smart_disable_on_world_change\":false,\"future_option\":17")).getAsJsonObject();
        root.addProperty("future_metadata", "keep"); root.getAsJsonObject("modules").add("future_module", new JsonObject());
        write(directory, ".last-state", root.toString()); config.initialize(Collections.emptyList());
        assertTrue(config.getLastError(), config.saveDefaultConfig());
        JsonObject saved = new JsonParser().parse(read(directory, ".last-state")).getAsJsonObject();
        JsonObject settings = saved.getAsJsonObject("modules").getAsJsonObject("nscaffold").getAsJsonObject("settings");
        assertEquals(54, settings.size()); assertEquals(17, settings.get("future_option").getAsInt());
        assertEquals("Reverse", settings.get("rotations").getAsString()); assertEquals("Telly", settings.get("mode").getAsString());
        for (String old : Arrays.asList("Visible Rotation", "build_mode", "turn_speed", "advanced_rotations", "smart_disable_on_world_change"))
            assertFalse(old, settings.has(old));
        assertEquals("keep", saved.get("future_metadata").getAsString()); assertTrue(saved.getAsJsonObject("modules").has("future_module"));
        NScaffold restored = new NScaffold(); ConfigManager reload = manager(restored, directory); reload.initialize(Collections.emptyList());
        assertEquals(values(module), values(restored)); assertTrue(reload.getLastError(), reload.saveDefaultConfig());
    }

    @Test public void knownLegacyAutomaticProfileRemainsWritable() throws Exception {
        NScaffold module = new NScaffold(); Path directory = temporary.newFolder().toPath(); ConfigManager config = manager(module, directory);
        write(directory, ".last-state", "{\"NScaffold\":{\"Visible Rotation\":\"Standard\",\"Advanced Rotations\":false,"
                + "\"Keep Height\":true,\"Smart Disable on Death\":true,\"Smart Disable on World Change\":true}}");
        config.initialize(Collections.emptyList()); assertTrue(config.getLastError(), config.saveDefaultConfig());
        JsonObject saved = new JsonParser().parse(read(directory, ".last-state")).getAsJsonObject();
        assertEquals(2, saved.get("version").getAsInt());
        assertTrue(saved.getAsJsonObject("modules").getAsJsonObject("nscaffold").getAsJsonObject("settings").get("keepy").getAsBoolean());
    }
}
