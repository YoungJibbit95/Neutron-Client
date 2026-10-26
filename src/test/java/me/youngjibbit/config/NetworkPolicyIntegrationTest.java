package me.youngjibbit.config;

import com.google.gson.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.exploit.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

public class NetworkPolicyIntegrationTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private void write(Path dir,String name,String content) throws Exception {Files.write(dir.resolve(name+".json"),content.getBytes(StandardCharsets.UTF_8));}
    private JsonObject settings(ConfigManager config,String id) {return JsonParser.parseString(config.serialize()).getAsJsonObject().getAsJsonObject("modules").getAsJsonObject(id).getAsJsonObject("settings");}

    @Test public void completedHistoricalProfilesUseOneCanonicalSettingPerTaskAndRestoreFreshInstances() throws Exception {
        Path dir=temporary.newFolder().toPath();Backtrack back=new Backtrack();Tickbase tick=new Tickbase();List<String> messages=new ArrayList<>();
        ConfigManager config=new ConfigManager(dir,Arrays.<Module>asList(back,tick),()->false,messages::add);
        String old="{\"version\":2,\"modules\":{\"backtrack\":{\"settings\":{\"delay_mode\":\"Fixed\",\"delay\":125,\"min_range\":2.8,\"target_fallback\":\"Cursor\",\"animals\":true}},\"tickbase\":{\"settings\":{\"delay\":450,\"charge_ticks\":3,\"min_charge\":2}}}}";
        write(dir,"Completed",old);assertTrue(config.getLastError(),config.loadConfig("Completed"));
        assertEquals("Combat / Cursor",back.getModeSetting("Target Source").getValue());assertEquals(500,back.getNumberSetting("Target Hold MS").getValue(),0);
        assertSame(back.getValueByName("Min Range"),back.getValueByName("Min Server Distance"));assertEquals(2.8,back.getNumberSetting("Min Server Distance").getValue(),0);
        assertSame(tick.getValueByName("Delay"),tick.getValueByName("Release Cooldown MS"));assertEquals(450,tick.getNumberSetting("Release Cooldown MS").getValue(),0);
        assertEquals(old,new String(Files.readAllBytes(dir.resolve("Completed.json")),StandardCharsets.UTF_8));assertTrue(messages.toString().contains("no additional enable wait"));
        assertTrue(config.saveConfig("Canonical"));JsonObject b=settings(config,"backtrack"),t=settings(config,"tickbase");
        assertFalse(b.has("min_range"));assertFalse(b.has("target_fallback"));assertFalse(t.has("delay"));assertEquals(125,b.get("delay").getAsInt());
        Backtrack freshBack=new Backtrack();Tickbase freshTick=new Tickbase();ConfigManager fresh=new ConfigManager(dir,Arrays.<Module>asList(freshBack,freshTick),()->false,message->{});
        assertTrue(fresh.loadConfig("Canonical"));assertEquals(config.serialize(),fresh.serialize());assertTrue(freshBack.getBooleanSetting("Animals").getValue());
    }
    @Test public void fixedNetworkProfilesAndPriorPartialProfilesRestoreDistinctDocumentedDefaults() throws Exception {
        Path dir=temporary.newFolder().toPath();Backtrack back=new Backtrack();Tickbase tick=new Tickbase();
        ConfigManager config=new ConfigManager(dir,Arrays.<Module>asList(back,tick),()->false,message->{});
        assertEquals("Attack / Cursor",back.getModeSetting("Target Source").getValue());assertEquals(300,tick.getNumberSetting("Delay").getValue(),0);
        write(dir,"Old","{\"BackTrack\":{\"Min MS\":80},\"Tickbase\":{\"Charge Ticks\":4}}");assertTrue(config.loadConfig("Old"));
        assertEquals("Combat / Nearest",back.getModeSetting("Target Source").getValue());assertEquals(0,tick.getNumberSetting("Delay").getValue(),0);
        write(dir,"Network","{\"BackTrack\":{\"Target Source\":\"Attack / Cursor\",\"Min Server Distance\":3.2},\"Tickbase\":{\"Release On\":\"Attack / Approach\"}}");assertTrue(config.loadConfig("Network"));
        assertEquals("Attack / Cursor",back.getModeSetting("Target Source").getValue());assertEquals(350,back.getNumberSetting("Target Hold MS").getValue(),0);assertEquals(300,tick.getNumberSetting("Delay").getValue(),0);
        write(dir,"Versioned","{\"version\":2,\"modules\":{\"backtrack\":{\"networkVersion\":1,\"settings\":{}},\"tickbase\":{\"networkVersion\":1,\"settings\":{}}}}");assertTrue(config.loadConfig("Versioned"));
        assertEquals("Attack / Cursor",back.getModeSetting("Target Source").getValue());assertEquals("Attack",tick.getModeSetting("Release On").getValue());assertEquals(300,tick.getNumberSetting("Delay").getValue(),0);
    }
    @Test public void conflictingAliasesAreRejectedBeforeAnyModuleChanges() throws Exception {
        Path dir=temporary.newFolder().toPath();Backtrack back=new Backtrack();Tickbase tick=new Tickbase();ConfigManager config=new ConfigManager(dir,Arrays.<Module>asList(back,tick),()->false,message->{});
        String before=config.serialize();
        for(String data:Arrays.asList("{\"BackTrack\":{\"Min Range\":2,\"Min Server Distance\":3}}","{\"BackTrack\":{\"Target Fallback\":\"Cursor\",\"Target Source\":\"Nearest\"}}","{\"Tickbase\":{\"Delay\":100,\"Release Cooldown MS\":200}}")) {
            write(dir,"Conflict",data);assertFalse(config.loadConfig("Conflict"));assertEquals(before,config.serialize());assertEquals(data,new String(Files.readAllBytes(dir.resolve("Conflict.json")),StandardCharsets.UTF_8));
        }
    }
    @Test public void futureNetworkPolicyCannotBeOverwrittenByAutomaticStartupOrShutdown() throws Exception {
        Path dir=temporary.newFolder().toPath();Backtrack back=new Backtrack();ConfigManager config=new ConfigManager(dir,Collections.<Module>singletonList(back),()->false,message->{});
        String future="{\"version\":2,\"modules\":{\"backtrack\":{\"networkVersion\":2,\"settings\":{}}}}";
        Files.write(config.defaultConfig.toPath(),future.getBytes(StandardCharsets.UTF_8));config.initialize(Collections.emptyList());
        assertTrue(config.getLastError().contains("Unsupported network policy"));back.getNumberSetting("Delay").setValue(250D);config.shutdown();assertFalse(config.saveDefaultConfig());
        assertEquals(future,new String(Files.readAllBytes(config.defaultConfig.toPath()),StandardCharsets.UTF_8));
    }
}
