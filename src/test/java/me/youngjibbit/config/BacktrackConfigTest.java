package me.youngjibbit.config;

import com.google.gson.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.exploit.Backtrack;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import static org.junit.Assert.*;

public class BacktrackConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void currentIdsAndOlderPartialConfigsKeepTheirValuesAndNewDefaults() throws Exception {
        Backtrack back = new Backtrack(); Path dir = temporary.newFolder().toPath();
        assertEquals("Attack / Cursor", back.getModeSetting("target_source").getValue());
        ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(back), () -> false, message -> { });
        Files.write(dir.resolve("Synthetic.json"), ("{\"BackTrack\":{\"Active On Hit\":false,\"Active On No Hit\":true,\"Acquire Range\":5.0,\"Min MS\":110,\"Max MS\":250,\"Release On Velocity\":false,\"Render\":\"Off\"}}").getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(), config.loadConfig("Synthetic"));
        assertEquals(110, back.getNumberSetting("min_ms").getValue(), 0); assertEquals(250, back.getNumberSetting("max_ms").getValue(), 0);
        assertFalse(back.getBooleanSetting("active_on_hit").getValue()); assertTrue(back.getBooleanSetting("active_on_no_hit").getValue());
        assertFalse(back.getBooleanSetting("release_on_velocity").getValue()); assertEquals("Off", back.getModeSetting("render").getValue());
        assertEquals("Combat / Nearest", back.getModeSetting("target_source").getValue()); assertEquals(500, back.getNumberSetting("target_hold_ms").getValue(), 0);
        assertNull(back.getDiagnostics());
        assertSame(back.getValueByName("release_on_velocity"), back.getValueByName("Release On Velocity"));
        assertEquals("Pause after velocity (150 ms)", back.getBooleanSetting("release_on_velocity").getName());
        assertEquals("While target hurt", back.getBooleanSetting("Active On Hit").getName());
        assertEquals("While target unhurt", back.getBooleanSetting("Active On No Hit").getName());
    }
    @Test public void newRulesAndExplicitNearestSelectionRoundTripWithoutIdChanges() throws Exception {
        Backtrack back = new Backtrack(); Path dir = temporary.newFolder().toPath();
        ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(back), () -> false, message -> { });
        back.getModeSetting("target_source").setValue("Nearest"); back.getNumberSetting("target_hold_ms").setValue(200.0);
        back.getNumberSetting("min_server_distance").setValue(2.8); back.getNumberSetting("min_distance_gain").setValue(.2);
        assertTrue(config.saveConfig("Synthetic"));
        back.getModeSetting("target_source").setValue("Attack / Cursor"); back.getNumberSetting("target_hold_ms").setValue(0.0);
        assertTrue(config.getLastError(), config.loadConfig("Synthetic"));
        assertEquals("Nearest", back.getModeSetting("target_source").getValue()); assertEquals(200, back.getNumberSetting("target_hold_ms").getValue(), 0);
        JsonObject settings = JsonParser.parseString(config.serialize()).getAsJsonObject().getAsJsonObject("modules").getAsJsonObject("backtrack").getAsJsonObject("settings");
        assertEquals(.2, settings.get("min_distance_gain").getAsDouble(), 0); assertEquals(2.8, settings.get("min_server_distance").getAsDouble(), 0);
        assertTrue(settings.has("acquire_range")); assertTrue(settings.has("release_on_velocity"));
    }
}
