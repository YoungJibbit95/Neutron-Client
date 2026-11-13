package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.youngjibbit.module.impl.world.Scaffold;
import com.google.gson.JsonParser;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import static org.junit.Assert.*;

/** Real config roundtrip and append-only defaults for Aura-only AI Learned. */
public class AiLearnedAuraConfigTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    @Test public void legacyOptionsAndOtherModulesRemainStable() {
        Aura a=new Aura();
        assertArrayEquals(new String[]{"Basic","Smooth","Advanced","Arsenic Lazy",
                "Arsenic Heuristics","Adaptive","AI Learned"},
                a.getModeSetting("Rot Mode").getOptions());
        assertEquals("Basic",a.getModeSetting("Rot Mode").getValue());
        assertEquals(0,a.getModeSetting("Rot Mode").getSelectedIndex());
        for(int i=0;i<6;i++) {
            a.getModeSetting("Rot Mode").setSelectedIndex(i);
            assertEquals(i,a.getModeSetting("Rot Mode").getSelectedIndex());
        }
        assertArrayEquals(new String[]{"Legit","Test","Normal","Arsenic Lazy","Arsenic Heuristics"},
                new AimAssist().getModeSetting("Rotations").getOptions());
        assertArrayEquals(new String[]{"Basic","Smooth","Advanced"},
                new Scaffold().getModeSetting("Rotation Smoothing").getOptions());
        a.getModeSetting("Rot Mode").setValue("AI Learned");
        assertFalse(a.getValueByName("Randomization Style").isVisible());
        assertFalse(a.getValueByName("Adaptive Speed").isVisible());
    }

    @Test public void namedModeRoundTripsOnExistingConfigId() throws Exception {
        Aura a=new Aura();Path dir=temp.newFolder().toPath();
        ConfigManager config=new ConfigManager(dir,Collections.<Module>singletonList(a),
                ()->false,message->{});
        a.getModeSetting("Rot Mode").setValue("AI Learned");
        assertTrue(config.getLastError(),config.saveConfig("AI"));
        String saved=config.serialize();
        assertEquals("AI Learned",new JsonParser().parse(saved).getAsJsonObject()
                .getAsJsonObject("modules").getAsJsonObject("aura")
                .getAsJsonObject("settings").get("rot_mode").getAsString());
        a.getModeSetting("Rot Mode").setValue("Smooth");
        assertTrue(config.getLastError(),config.loadConfig("AI"));
        assertEquals("AI Learned",a.getModeSetting("Rot Mode").getValue());
        assertEquals(saved,config.serialize());
    }

    @Test public void missingModeKeyResetsWarmSelectionWithoutModifyingFile() throws Exception {
        Aura a=new Aura();Path dir=temp.newFolder().toPath();
        ConfigManager config=new ConfigManager(dir,Collections.<Module>singletonList(a),
                ()->false,message->{});
        a.getModeSetting("Rot Mode").setValue("AI Learned");
        String json="{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,\"settings\":{}}}}";
        Files.write(dir.resolve("Legacy.json"),json.getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(),config.loadConfig("Legacy"));
        assertEquals("Basic",a.getModeSetting("Rot Mode").getValue());
        assertEquals(json,new String(Files.readAllBytes(dir.resolve("Legacy.json")),StandardCharsets.UTF_8));
    }

    @Test public void invalidModeCannotBeSaved() throws Exception {
        Aura a=new Aura();Path dir=temp.newFolder().toPath();
        ConfigManager config=new ConfigManager(dir,Collections.<Module>singletonList(a),
                ()->false,message->{});
        a.getModeSetting("Rot Mode").setValue("AI Learned");
        String before=config.serialize();
        String bad="{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,"
                +"\"settings\":{\"rot_mode\":\"AI Learned Untrusted\"}}}}";
        Files.write(dir.resolve("Bad.json"),bad.getBytes(StandardCharsets.UTF_8));
        assertFalse(config.saveConfig("Bad", config.readConfigData(config.defaultConfig.toPath().resolveSibling("Bad.json"))));
        assertEquals(before,config.serialize());
    }
}
