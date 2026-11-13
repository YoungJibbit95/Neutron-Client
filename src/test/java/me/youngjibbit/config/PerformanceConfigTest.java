package me.youngjibbit.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.youngjibbit.core.diagnostics.ChunkFrameBudget;
import me.youngjibbit.core.diagnostics.PerformanceOptions;
import me.youngjibbit.module.impl.client.Performance;
import me.youngjibbit.value.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

/** Real Performance module lifecycle, values and the existing ConfigManager codec. */
public class PerformanceConfigTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    private final List<Performance> modules = new ArrayList<>();
    private PerformanceOptions.Snapshot previous;

    @Before public void defaults() {
        previous = PerformanceOptions.current();
        PerformanceOptions.defaults();
    }

    @After public void cleanup() {
        for (Performance module : modules) module.setToggled(false);
        PerformanceOptions.configure(previous.fastParticleCleanup, previous.fastVisibilityGraph,
                previous.fastGaussian, previous.sceneBlurDivisor,
                previous.uploadLimits.nanoseconds / 1_000_000D, previous.uploadLimits.bytes / 1_048_576D);
    }

    @Test public void menuRegistersSixUniquePersistedValuesWithLegacyDefaults() {
        Performance module = module();
        assertFalse(module.isToggled());
        Set<String> ids = new HashSet<>();
        for (Value<?> value : module.getValues()) {
            assertTrue(value.isVisible());
            assertTrue(ids.add(value.getId()));
        }
        assertEquals(new HashSet<>(Arrays.asList("upload_budget_ms", "upload_budget_mib", "fast_particle_cleanup",
                "fast_visibility_graph", "fast_gaussian", "scene_blur_quality")), ids);
        assertEquals(1D, module.uploadTime.getValue(), 0);
        assertEquals(4D, module.uploadBytes.getValue(), 0);
        assertFalse(module.fastParticleCleanup.getValue());
        assertFalse(module.fastVisibilityGraph.getValue());
        assertFalse(module.fastGaussian.getValue());
        assertEquals("Full", module.sceneBlurQuality.getValue());
        assertArrayEquals(new String[]{"Full", "Half", "Quarter"}, module.sceneBlurQuality.getOptions());
    }

    @Test public void disabledEditsStayInactiveAndEnableDisablePreservesTheUsersChoices() {
        Performance module = module();
        PerformanceOptions.Snapshot initial = PerformanceOptions.current();
        chooseFast(module, "Quarter");
        assertSame(initial, PerformanceOptions.current());
        module.setToggled(true);
        assertFast(4);
        PerformanceOptions.Snapshot enabled = PerformanceOptions.current();
        module.onValueChange();
        assertSame("Unchanged apply must not invalidate a capture", enabled, PerformanceOptions.current());
        module.setToggled(false);
        assertLegacy();
        assertTrue(module.fastParticleCleanup.getValue());
        assertTrue(module.fastVisibilityGraph.getValue());
        assertTrue(module.fastGaussian.getValue());
        assertEquals("Quarter", module.sceneBlurQuality.getValue());
        assertEquals(2.5, module.uploadTime.getValue(), 0);
        assertEquals(12, module.uploadBytes.getValue(), 0);
        module.setToggled(true);
        assertFast(4);
        assertTrue(PerformanceOptions.current().revision > enabled.revision);
    }

    @Test public void batchedMenuEditsPublishOnceAndResetRestoresAllEffectiveDefaults() {
        Performance module = module();
        module.setToggled(true);
        PerformanceOptions.Snapshot initial = PerformanceOptions.current();
        module.beginSettingsUpdate();
        chooseFast(module, "Half");
        assertSame(initial, PerformanceOptions.current());
        module.endConfigUpdate();
        assertFast(2);
        assertEquals(initial.revision + 1, PerformanceOptions.current().revision);
        module.beginSettingsUpdate();
        for (Value<?> value : module.getValues()) value.reset();
        module.endConfigUpdate();
        assertLegacy();
        assertEquals(initial.revision + 2, PerformanceOptions.current().revision);
    }

    @Test public void allBlurChoicesAndTogglesRoundTripThroughTheRealCodecAndReactivate() throws Exception {
        for (String quality : new String[]{"Full", "Half", "Quarter"}) {
            Path directory = folder.newFolder().toPath();
            Performance original = module();
            ConfigManager first = manager(directory, original);
            chooseFast(original, quality);
            original.setToggled(true);
            assertTrue(first.saveConfig("selected"));
            original.setToggled(false);
            Performance restored = module();
            ConfigManager second = manager(directory, restored);
            assertTrue(second.getLastError(), second.loadConfig("selected"));
            assertTrue(restored.isToggled());
            assertTrue(restored.fastParticleCleanup.getValue());
            assertTrue(restored.fastVisibilityGraph.getValue());
            assertTrue(restored.fastGaussian.getValue());
            assertEquals(quality, restored.sceneBlurQuality.getValue());
            assertFast("Quarter".equals(quality) ? 4 : "Half".equals(quality) ? 2 : 1);
            JsonObject settings = settings(second);
            assertTrue(settings.get("fast_gaussian").getAsBoolean());
            assertEquals(quality, settings.get("scene_blur_quality").getAsString());
            restored.setToggled(false);
        }
    }

    @Test public void oldProfileDoesNotInheritNewOptInsFromWarmState() throws Exception {
        Performance module = module();
        ConfigManager codec = manager(folder.newFolder().toPath(), module);
        JsonObject legacy = JsonParser.parseString(codec.serialize()).getAsJsonObject();
        JsonObject values = legacy.getAsJsonObject("modules").getAsJsonObject("performance").getAsJsonObject("settings");
        for (String key : new String[]{"fast_particle_cleanup", "fast_visibility_graph", "fast_gaussian", "scene_blur_quality"}) {
            values.remove(key);
        }
        values.addProperty("upload_budget_ms", 1.75);
        values.addProperty("upload_budget_mib", 8);
        assertTrue(codec.saveConfig("old", legacy.toString()));
        chooseFast(module, "Quarter");
        module.setToggled(true);
        assertTrue(codec.getLastError(), codec.loadConfig("old"));
        assertFalse(module.fastParticleCleanup.getValue());
        assertFalse(module.fastVisibilityGraph.getValue());
        assertFalse(module.fastGaussian.getValue());
        assertEquals("Full", module.sceneBlurQuality.getValue());
        assertEquals(1.75, module.uploadTime.getValue(), 0);
        assertEquals(8, module.uploadBytes.getValue(), 0);
        assertFalse(module.isToggled());
        assertLegacy();
        module.setToggled(true);
        assertFalse(PerformanceOptions.isFastGaussianEnabled());
        assertEquals(1, PerformanceOptions.getSceneBlurDivisor());
        assertEquals(1_750_000L, ChunkFrameBudget.current().nanoseconds);
        assertEquals(8 * 1_048_576L, ChunkFrameBudget.current().bytes);
    }

    @Test public void invalidProfileCannotPartiallyApplyNewSettings() throws Exception {
        Performance module = module();
        ConfigManager codec = manager(folder.newFolder().toPath(), module);
        chooseFast(module, "Half");
        module.setToggled(true);
        PerformanceOptions.Snapshot before = PerformanceOptions.current();
        String serialized = codec.serialize();
        JsonObject invalid = JsonParser.parseString(serialized).getAsJsonObject();
        JsonObject values = invalid.getAsJsonObject("modules").getAsJsonObject("performance").getAsJsonObject("settings");
        values.addProperty("fast_gaussian", false);
        values.addProperty("scene_blur_quality", "Eighth");
        assertFalse(codec.saveConfig("bad", invalid.toString()));
        assertEquals(serialized, codec.serialize());
        assertSame(before, PerformanceOptions.current());
    }

    private Performance module() {
        Performance module = new Performance();
        modules.add(module);
        return module;
    }

    private static ConfigManager manager(Path path, Performance module) {
        return new ConfigManager(path, Collections.singletonList(module), () -> true, ignored -> { });
    }

    private static JsonObject settings(ConfigManager codec) {
        return JsonParser.parseString(codec.serialize()).getAsJsonObject().getAsJsonObject("modules")
                .getAsJsonObject("performance").getAsJsonObject("settings");
    }

    private static void chooseFast(Performance module, String quality) {
        module.fastParticleCleanup.setValue(true);
        module.fastVisibilityGraph.setValue(true);
        module.fastGaussian.setValue(true);
        module.sceneBlurQuality.setValue(quality);
        module.uploadTime.setValue(2.5);
        module.uploadBytes.setValue(12D);
    }

    private static void assertFast(int divisor) {
        assertTrue(PerformanceOptions.isFastParticleCleanupEnabled());
        assertTrue(PerformanceOptions.isFastVisibilityGraphEnabled());
        assertTrue(PerformanceOptions.isFastGaussianEnabled());
        assertEquals(divisor, PerformanceOptions.getSceneBlurDivisor());
        assertEquals(2_500_000L, ChunkFrameBudget.current().nanoseconds);
        assertEquals(12 * 1_048_576L, ChunkFrameBudget.current().bytes);
    }

    private static void assertLegacy() {
        assertFalse(PerformanceOptions.isFastParticleCleanupEnabled());
        assertFalse(PerformanceOptions.isFastVisibilityGraphEnabled());
        assertFalse(PerformanceOptions.isFastGaussianEnabled());
        assertEquals(1, PerformanceOptions.getSceneBlurDivisor());
        assertSame(ChunkFrameBudget.DEFAULT, ChunkFrameBudget.current());
    }
}
