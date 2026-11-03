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
    @Test public void everyBacktrackModeRoundTripsAndMissingLegacyModeResetsDistance() throws Exception {
        Backtrack back=new Backtrack();Path dir=temporary.newFolder().toPath();
        ConfigManager config=new ConfigManager(dir,Collections.<Module>singletonList(back),()->false,message->{});
        for(String mode:new String[]{"Distance","Timed","Pulse"}) {
            back.getModeSetting("Backtrack Mode").setValue(mode);assertTrue(config.saveConfig("Mode"));
            back.getModeSetting("Backtrack Mode").setValue("Distance");assertTrue(config.loadConfig("Mode"));
            assertEquals(mode,back.getModeSetting("Backtrack Mode").getValue());
        }
        Files.write(dir.resolve("Legacy.json"),"{\"version\":2,\"modules\":{\"backtrack\":{\"networkVersion\":1,\"settings\":{\"delay\":70}}}}".getBytes(StandardCharsets.UTF_8));
        for(String previous:new String[]{"Timed","Pulse"}) {
            back.getModeSetting("Backtrack Mode").setValue(previous);
            assertTrue(config.loadConfig("Legacy"));assertEquals("Distance",back.getModeSetting("Backtrack Mode").getValue());
        }
        assertEquals(70,back.getNumberSetting("Delay").getValue(),0);
    }
    @Test public void invalidConfigDoesNotResetCurrentModeOrOtherValues() throws Exception {
        Backtrack back=new Backtrack();Path dir=temporary.newFolder().toPath();
        ConfigManager config=new ConfigManager(dir,Collections.<Module>singletonList(back),()->false,message->{});
        back.getModeSetting("Backtrack Mode").setValue("Pulse");back.getNumberSetting("Delay").setValue(80D);
        String before=config.serialize();
        for(String settings:new String[]{"\"delay\":-1", "\"backtrack_mode\":\"Unknown\"", "\"backtrack_mode\":\"Timed\",\"delay\":\"invalid\""}) {
            Files.write(dir.resolve("Invalid.json"),("{\"version\":2,\"modules\":{\"backtrack\":{\"networkVersion\":1,\"settings\":{"+settings+"}}}}").getBytes(StandardCharsets.UTF_8));
            assertFalse(config.loadConfig("Invalid"));assertEquals(before,config.serialize());
        }
    }
    @Test public void currentIdsAndOlderPartialConfigsKeepTheirValuesAndNewDefaults() throws Exception {
        Backtrack back = new Backtrack(); Path dir = temporary.newFolder().toPath();
        assertEquals("Attack", back.getModeSetting("target_source").getValue());
        ConfigManager config = new ConfigManager(dir, Collections.<Module>singletonList(back), () -> false, message -> { });
        Files.write(dir.resolve("Synthetic.json"), ("{\"BackTrack\":{\"Active On Hit\":false,\"Active On No Hit\":true,\"Acquire Range\":5.0,\"Min MS\":110,\"Max MS\":250,\"Release On Velocity\":false,\"Render\":\"Off\"}}").getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(), config.loadConfig("Synthetic"));
        assertEquals(110, back.getNumberSetting("min_ms").getValue(), 0); assertEquals(250, back.getNumberSetting("max_ms").getValue(), 0);
        assertFalse(back.getBooleanSetting("active_on_hit").getValue()); assertTrue(back.getBooleanSetting("active_on_no_hit").getValue());
        assertFalse(back.getBooleanSetting("release_on_velocity").getValue()); assertEquals("Off", back.getModeSetting("render").getValue());
        assertEquals("Combat / Nearest", back.getModeSetting("target_source").getValue()); assertEquals(500, back.getNumberSetting("target_hold_ms").getValue(), 0);
        assertNull(back.getDiagnostics());
        assertSame(back.getValueByName("release_on_velocity"), back.getValueByName("Release On Velocity"));
        assertEquals("Release on local knockback", back.getBooleanSetting("release_on_velocity").getName());
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
