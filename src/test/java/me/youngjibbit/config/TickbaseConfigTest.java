package me.youngjibbit.config;

import com.google.gson.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.exploit.Tickbase;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import static org.junit.Assert.*;

public class TickbaseConfigTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void currentPersistentIdsAndPartialConfigsRemainCompatible() throws Exception {
        Tickbase tick = new Tickbase(); Path dir = temporary.newFolder().toPath();
        assertEquals(300, tick.getNumberSetting("release_cooldown_ms").getValue(), 0);
        ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(tick), () -> false, message -> { });
        Files.write(dir.resolve("Synthetic.json"), "{\"Tickbase\":{\"Charge Ticks\":4,\"Min Charge\":2,\"Release Speed\":1.1,\"Require Target\":false,\"Debug\":true}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(), config.loadConfig("Synthetic"));
        assertEquals(4, tick.getNumberSetting("charge_ticks").getValue(), 0); assertEquals(2, tick.getNumberSetting("min_charge").getValue(), 0);
        assertEquals(1.1, tick.getNumberSetting("release_speed").getValue(), 0); assertFalse(tick.getBooleanSetting("require_target").getValue());
        assertNotNull(tick.getDiagnostics()); assertEquals("Attack", tick.getModeSetting("release_on").getValue());
        assertEquals(0, tick.getNumberSetting("release_cooldown_ms").getValue(), 0);
    }
    @Test public void historicalTicksAndDelayImportAsCapacityAndCooldownAndSaveCanonicalIds() throws Exception {
        Tickbase tick = new Tickbase(); Path dir = temporary.newFolder().toPath();
        ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(tick), () -> false, message -> { });
        Files.write(dir.resolve("Synthetic.json"), "{\"Tickbase\":{\"Ticks\":3,\"Delay\":450,\"Debugging\":false}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(), config.loadConfig("Synthetic")); assertTrue(config.saveConfig("Canonical"));
        JsonObject root = JsonParser.parseString(config.serialize()).getAsJsonObject();
        JsonObject settings = root.getAsJsonObject("modules").getAsJsonObject("tickbase").getAsJsonObject("settings");
        assertEquals(3, settings.get("charge_ticks").getAsInt()); assertEquals(450, settings.get("release_cooldown_ms").getAsInt());
        assertFalse(settings.has("Ticks")); assertFalse(settings.has("Delay")); assertEquals("release_speed", tick.getNumberSetting("Release Speed").getId());
    }
    @Test public void releaseModesRangesCooldownAndDiagnosticsRoundTripAndRejectInvalidRulesAtomically() throws Exception {
        Tickbase tick = new Tickbase(); Path dir = temporary.newFolder().toPath();
        ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(tick), () -> false, message -> { });
        for (String mode : new String[]{"Attack", "Attack / Approach"}) {
            tick.getModeSetting("release_on").setValue(mode); tick.getNumberSetting("charge_range").setValue(5.5);
            tick.getNumberSetting("release_range").setValue(3.1); tick.getNumberSetting("release_cooldown_ms").setValue(750.0);
            assertTrue(config.saveConfig("Synthetic")); tick.getNumberSetting("charge_range").setValue(6.0);
            assertTrue(config.getLastError(), config.loadConfig("Synthetic"));
            assertEquals(mode, tick.getModeSetting("release_on").getValue()); assertEquals(5.5, tick.getNumberSetting("charge_range").getValue(), 0);
            assertEquals(3.1, tick.getNumberSetting("release_range").getValue(), 0); assertEquals(750, tick.getNumberSetting("release_cooldown_ms").getValue(), 0);
        }
        String before = config.serialize();
        Files.write(dir.resolve("Synthetic.json"), "{\"Tickbase\":{\"Release On\":\"invalid\",\"Charge Range\":4.0}}".getBytes(StandardCharsets.UTF_8));
        assertFalse(config.loadConfig("Synthetic")); assertEquals(before, config.serialize());
    }
}
