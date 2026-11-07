package me.youngjibbit.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.combat.Velocity;
import me.youngjibbit.module.impl.exploit.Backtrack;
import me.youngjibbit.module.impl.exploit.LagRange;
import me.youngjibbit.module.impl.ghost.WTap2;
import me.youngjibbit.module.impl.movement.Speed;
import me.youngjibbit.module.impl.player.NoSlow;
import me.youngjibbit.module.impl.render.Diagnostics;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class FinalPlaytestConfigsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void bundledProfilesInstallLoadAndPreserveExistingUserFiles() throws Exception {
        List<Module> modules = Arrays.asList(new Aura(), new AimAssist(), new Scaffold(), new WTap2(),
                new Velocity(), new Speed(), new NoSlow(), new Backtrack(), new LagRange(), new Diagnostics());
        Map<String, Module> byId = new HashMap<>();
        for (Module module : modules) byId.put(module.getId(), module);
        Path directory = temp.newFolder().toPath();
        ConfigManager manager = new ConfigManager(directory, modules, () -> false, message -> {});
        assertTrue(manager.getLastError(), manager.installBundledPlaytestConfigs());
        for (String name : new String[]{"HypeMC-Final-0.2.1", "Mineblaze-Final-0.2.1"}) {
            JsonObject profile = new JsonParser().parse(new String(Files.readAllBytes(directory.resolve(name + ".json")),
                    StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("modules");
            for (Map.Entry<String, JsonElement> entry : profile.entrySet()) {
                Module module = byId.get(entry.getKey());
                assertNotNull("Unknown module " + entry.getKey(), module);
                for (Map.Entry<String, JsonElement> value : entry.getValue().getAsJsonObject().getAsJsonObject("settings").entrySet())
                    assertNotNull(entry.getKey() + "." + value.getKey(), module.getValueByName(value.getKey()));
            }
            assertTrue(manager.getLastError(), manager.loadConfig(name));
            JsonObject loaded = new JsonParser().parse(manager.serialize()).getAsJsonObject().getAsJsonObject("modules");
            for (Map.Entry<String, JsonElement> entry : profile.entrySet()) {
                JsonObject wanted = entry.getValue().getAsJsonObject().getAsJsonObject("settings");
                JsonObject actual = loaded.getAsJsonObject(entry.getKey()).getAsJsonObject("settings");
                for (Map.Entry<String, JsonElement> value : wanted.entrySet())
                    assertEquals(name + ": " + entry.getKey() + "." + value.getKey(), value.getValue(), actual.get(value.getKey()));
            }
            assertTrue(byId.get("aura").getBooleanSetting("Arsenic Soft Motion").getValue());
            assertTrue(byId.get("scaffold").getBooleanSetting("Arsenic Soft Motion").getValue());
            assertEquals(.35, byId.get("aura").getNumberSetting("Aim Range Margin").getValue(), 1e-8);
            assertEquals(3, byId.get("aura").getNumberSetting("Reach").getValue(), 0);
            assertEquals("Simple", byId.get("diagnostics").getModeSetting("Display Mode").getValue());
        }
        Path existing = directory.resolve("HypeMC-Final-0.2.1.json");
        byte[] userData = "user-edited configuration".getBytes(StandardCharsets.UTF_8);
        Files.write(existing, userData);
        assertTrue(manager.getLastError(), manager.installBundledPlaytestConfigs());
        assertArrayEquals(userData, Files.readAllBytes(existing));
        assertFalse(Files.exists(directory.resolve(".last-state.json")));
        try (java.util.stream.Stream<Path> files = Files.list(directory)) {
            assertEquals(2, files.count());
        }
    }
}
