package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.Scaffold;
import me.youngjibbit.module.impl.exploit.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.Assert.*;

public class LegacyModuleConfigTest {
    @Rule public TemporaryFolder folder=new TemporaryFolder();
    @Test public void oldNetworkOptionsMigrateExplicitlyWithoutPacketLossOrTimerRollbackSemantics() throws Exception {
        Backtrack back=new Backtrack();LagRange lag=new LagRange();Tickbase tick=new Tickbase();Path dir=folder.newFolder().toPath();List<String> messages=new ArrayList<>();
        ConfigManager configs=new ConfigManager(dir,Arrays.<Module>asList(back,lag,tick),()->false,messages::add);
        String old="{\"BackTrack\":{\"Delay\":1700,\"Min range\":2.8,\"Delay ping\":true,\"Delay velocity\":true,\"Enemy distance\":4.5},\"LagRange\":{\"Ticks delay between packets\":16,\"Distance enemy blocks\":5},\"Tickbase\":{\"Ticks\":3,\"Delay\":300,\"Debugging\":false}}";
        Path file=dir.resolve("OldNetwork.json");Files.write(file,old.getBytes(StandardCharsets.UTF_8));boolean loaded=configs.loadConfig("OldNetwork");assertTrue(configs.getLastError(),loaded);
        assertEquals("Fixed",back.getModeSetting("Delay Mode").getValue());assertEquals(500,back.getNumberSetting("Delay").getValue(),0);assertEquals(2.8,back.getNumberSetting("Min Range").getValue(),0);assertEquals(4.5,back.getNumberSetting("Acquire Range").getValue(),0);assertEquals("Combat / Cursor",back.getModeSetting("Target Source").getValue());assertTrue(back.getBooleanSetting("Active On No Hit").getValue());
        assertTrue(back.getBooleanSetting("Animals").getValue());assertTrue(back.getBooleanSetting("Mobs").getValue());
        assertEquals("Ticks",lag.getModeSetting("Delay Mode").getValue());assertEquals(10,lag.getNumberSetting("Window Ticks").getValue(),0);assertEquals(5,lag.getNumberSetting("Max Target Range").getValue(),0);assertEquals(2,lag.getNumberSetting("Preactivation").getValue(),0);assertTrue(lag.getBooleanSetting("Only Swinging").getValue());assertFalse(lag.getBooleanSetting("Animals").getValue());
        assertEquals(3,tick.getNumberSetting("Charge Ticks").getValue(),0);assertEquals(3,tick.getNumberSetting("Min Charge").getValue(),0);assertEquals(300,tick.getNumberSetting("Delay").getValue(),0);
        assertTrue(messages.toString(),messages.toString().contains("500 ms bound"));assertTrue(messages.toString().contains("corrections remain immediate"));assertEquals(old,new String(Files.readAllBytes(file),StandardCharsets.UTF_8));
        assertTrue(configs.saveConfig("Canonical"));String expected=configs.serialize();back.getModeSetting("Delay Mode").setValue("Range");lag.getModeSetting("Delay Mode").setValue("MS");tick.getNumberSetting("Delay").setValue(0D);assertTrue(configs.loadConfig("Canonical"));assertEquals(expected,configs.serialize());
        String modern="{\"version\":2,\"modules\":{\"backtrack\":{\"settings\":{\"min_ms\":5,\"max_ms\":10}},\"lagrange\":{\"settings\":{\"min_ms\":30}},\"tickbase\":{\"settings\":{\"require_target\":false}}}}";
        Files.write(dir.resolve("PriorIntegrated.json"),modern.getBytes(StandardCharsets.UTF_8));assertTrue(configs.loadConfig("PriorIntegrated"));assertEquals("Range",back.getModeSetting("Delay Mode").getValue());assertEquals("Combat / Nearest",back.getModeSetting("Target Source").getValue());assertEquals("MS",lag.getModeSetting("Delay Mode").getValue());assertEquals(0,lag.getNumberSetting("Preactivation").getValue(),0);assertFalse(lag.getBooleanSetting("Only Swinging").getValue());assertTrue(lag.getBooleanSetting("Animals").getValue());assertEquals(0,tick.getNumberSetting("Delay").getValue(),0);
    }
    @Test public void modernInvalidDelayIsRejectedRatherThanSilentlyClamped() throws Exception {
        Backtrack back=new Backtrack();Path dir=folder.newFolder().toPath();ConfigManager configs=new ConfigManager(dir,Collections.<Module>singletonList(back),()->false,message->{});
        String before=configs.serialize(),invalid="{\"version\":2,\"modules\":{\"backtrack\":{\"settings\":{\"delay_mode\":\"Fixed\",\"delay\":2000}}}}";
        Files.write(dir.resolve("Invalid.json"),invalid.getBytes(StandardCharsets.UTF_8));assertFalse(configs.loadConfig("Invalid"));assertEquals(before,configs.serialize());assertEquals(invalid,new String(Files.readAllBytes(dir.resolve("Invalid.json")),StandardCharsets.UTF_8));
    }
    @Test public void oldGodbridgeOptionsUseTheSharedPlanAndCannotDisableTheFinalRaycast() throws Exception {
        Scaffold scaffold=new Scaffold();Path dir=folder.newFolder().toPath();List<String> messages=new ArrayList<>();
        ConfigManager configs=new ConfigManager(dir,Collections.<Module>singletonList(scaffold),()->false,messages::add);
        String old="{\"Scaffold\":{\"SameY\":true,\"Round Rots\":true,\"Tower\":true,\"Movefix\":true,\"Raytrace\":false}}";
        Files.write(dir.resolve("Old.json"),old.getBytes(StandardCharsets.UTF_8));boolean loaded=configs.loadConfig("Old");assertTrue(configs.getLastError(),loaded);
        assertEquals("Godbridge",scaffold.getModeSetting("Rotation Mode").getValue());assertTrue(scaffold.getBooleanSetting("Keep Y").getValue());assertTrue(scaffold.getBooleanSetting("Round Rots").getValue());assertTrue(scaffold.getBooleanSetting("Tower").getValue());assertEquals("Einfach",scaffold.getModeSetting("Movefix").getValue());
        assertTrue(messages.toString(),messages.toString().contains("raycasts remain mandatory"));assertEquals(old,new String(Files.readAllBytes(dir.resolve("Old.json")),StandardCharsets.UTF_8));
        assertTrue(configs.saveConfig("New"));String expected=configs.serialize();scaffold.getModeSetting("Rotation Mode").setValue("Back");assertTrue(configs.loadConfig("New"));assertEquals(expected,configs.serialize());
        for(String ordered:Arrays.asList("{\"Scaffold\":{\"Movefix\":false,\"SameY\":false}}","{\"Scaffold\":{\"settings\":[{\"name\":\"Movefix\",\"value\":true},{\"name\":\"SameY\",\"value\":true}]}}")) {
            Files.write(dir.resolve("Ordered.json"),ordered.getBytes(StandardCharsets.UTF_8));boolean migrated=configs.loadConfig("Ordered");assertTrue(configs.getLastError(),migrated);assertEquals("Godbridge",scaffold.getModeSetting("Rotation Mode").getValue());
        }
    }
}
