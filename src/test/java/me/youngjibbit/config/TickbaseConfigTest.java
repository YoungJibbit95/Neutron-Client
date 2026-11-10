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

    @Test public void legacyWarmImportClearsPreviouslySelectedPacketModeAndWindow() throws Exception {
        for (String previous : new String[]{"Demise Pulse", "Demise Approach"}) {
            Tickbase tick = new Tickbase(); Path dir = temporary.newFolder().toPath();
            ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(tick), () -> false, message -> { });
            tick.getModeSetting("Mode").setValue(previous);
            tick.getNumberSetting("Packet Hold MS").setValue(240D);
            tick.getNumberSetting("Pulse Gap MS").setValue(700D);
            Files.write(dir.resolve("Legacy.json"), "{\"Tickbase\":{\"Charge Ticks\":4}}".getBytes(StandardCharsets.UTF_8));

            assertTrue(config.getLastError(), config.loadConfig("Legacy"));
            assertEquals("Player Steps", tick.getModeSetting("Mode").getValue());
            assertEquals(200, tick.getNumberSetting("Packet Hold MS").getValue(), 0);
            assertEquals(60, tick.getNumberSetting("Pulse Gap MS").getValue(), 0);
            assertEquals(4, tick.getNumberSetting("Charge Ticks").getValue(), 0);
        }
    }

    @Test public void explicitPacketModesAndWindowsSurviveModernRoundTrip() throws Exception {
        Tickbase tick = new Tickbase(); Path dir = temporary.newFolder().toPath();
        ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(tick), () -> false, message -> { });
        for (String mode : new String[]{"Demise Pulse", "Demise Approach"}) {
            tick.getModeSetting("Mode").setValue(mode);
            tick.getNumberSetting("Packet Hold MS").setValue(150D);
            tick.getNumberSetting("Pulse Gap MS").setValue(90D);
            assertTrue(config.saveConfig("Modern"));
            tick.getModeSetting("Mode").setValue("Player Steps");
            tick.getNumberSetting("Packet Hold MS").setValue(250D);
            tick.getNumberSetting("Pulse Gap MS").setValue(1000D);

            assertTrue(config.getLastError(), config.loadConfig("Modern"));
            assertEquals(mode, tick.getModeSetting("Mode").getValue());
            assertEquals(150, tick.getNumberSetting("Packet Hold MS").getValue(), 0);
            assertEquals(90, tick.getNumberSetting("Pulse Gap MS").getValue(), 0);
        }
    }

    @Test public void directSettingsEditDoesNotApplyFullImportDefaults() {
        Tickbase tick = new Tickbase();
        tick.getModeSetting("Mode").setValue("Demise Approach");
        tick.getNumberSetting("Packet Hold MS").setValue(150D);
        tick.getNumberSetting("Pulse Gap MS").setValue(90D);
        tick.beginSettingsUpdate();
        tick.getNumberSetting("Charge Ticks").setValue(4D);
        tick.endConfigUpdate();
        assertEquals("Demise Approach", tick.getModeSetting("Mode").getValue());
        assertEquals(150, tick.getNumberSetting("Packet Hold MS").getValue(), 0);
        assertEquals(90, tick.getNumberSetting("Pulse Gap MS").getValue(), 0);
    }

    @Test public void invalidPacketModeImportDoesNotPartiallyResetWarmState() throws Exception {
        Tickbase tick = new Tickbase(); Path dir = temporary.newFolder().toPath();
        ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(tick), () -> false, message -> { });
        tick.getModeSetting("Mode").setValue("Demise Pulse");
        tick.getNumberSetting("Packet Hold MS").setValue(150D);
        String before = config.serialize();
        Files.write(dir.resolve("Invalid.json"), "{\"Tickbase\":{\"Mode\":\"missing\",\"Pulse Gap MS\":90}}".getBytes(StandardCharsets.UTF_8));
        assertFalse(config.loadConfig("Invalid"));
        assertEquals(before, config.serialize());
    }
}
