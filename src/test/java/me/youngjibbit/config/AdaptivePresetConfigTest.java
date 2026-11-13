package me.youngjibbit.config;

import com.google.gson.JsonParser;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import static org.junit.Assert.*;

/** Full ConfigManager roundtrip, missing-key policy and append-only approach options. */
public class AdaptivePresetConfigTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void newModesAreAuraOnlyAndLegacyIndicesStayUnchanged() {
        Aura a = new Aura();
        assertArrayEquals(new String[]{"Basic","Smooth","Advanced","Arsenic Lazy","Arsenic Heuristics","Adaptive"},
                java.util.Arrays.copyOf(a.getModeSetting("Rot Mode").getOptions(),6));
        assertEquals("AI Learned",a.getModeSetting("Rot Mode").getOptions()[6]);
        assertEquals("Basic", a.getModeSetting("Rot Mode").getValue());
        assertEquals("Balanced", a.getModeSetting("Adaptive Speed").getValue());
        assertEquals("Stable", a.getModeSetting("Adaptive Hitpoint").getValue());
        assertEquals("Straight",a.getModeSetting("Adaptive Aim Point").getValue());
        assertTrue(a.getBooleanSetting("Adaptive Keep Valid Aim").getValue());
        assertEquals(.5, a.getNumberSetting("Adaptive Variation Strength").getValue(), 0);
        assertFalse(a.getValueByName("Adaptive Speed").isVisible());
        a.getModeSetting("Rot Mode").setValue("Adaptive");
        assertTrue(a.getValueByName("Adaptive Speed").isVisible());
        assertTrue(a.getValueByName("Adaptive Hitpoint").isVisible());
        assertTrue(a.getValueByName("Adaptive Variation Strength").isVisible());
        assertTrue(a.getValueByName("Adaptive Aim Point").isVisible());
        assertTrue(a.getValueByName("Adaptive Keep Valid Aim").isVisible());
        a.getModeSetting("Rot Mode").setValue("Arsenic Heuristics");
        assertFalse(a.getValueByName("Adaptive Hitpoint").isVisible());
    }

    @Test public void allNewPresetKeysRoundTripWithoutReplacingOldRotModeId() throws Exception {
        Aura a = new Aura();
        ConfigManager c = new ConfigManager(temp.newFolder().toPath(),
                Collections.<Module>singletonList(a), () -> false, msg -> {});
        a.getModeSetting("Rot Mode").setValue("Adaptive");
        a.getModeSetting("Adaptive Speed").setValue("Swift");
        a.getModeSetting("Adaptive Hitpoint").setValue("Dynamic");
        a.getNumberSetting("Adaptive Variation Strength").setValue(.75);
        a.getModeSetting("Adaptive Aim Point").setValue("Nearest");
        a.getBooleanSetting("Adaptive Keep Valid Aim").setValue(false);
        assertTrue(c.getLastError(), c.saveConfig("Preset"));
        String serialized = c.serialize();
        com.google.gson.JsonObject values = new JsonParser().parse(serialized).getAsJsonObject()
                .getAsJsonObject("modules").getAsJsonObject("aura").getAsJsonObject("settings");
        assertEquals("Adaptive", values.get("rot_mode").getAsString());
        assertEquals("Swift", values.get("adaptive_speed").getAsString());
        assertEquals("Dynamic", values.get("adaptive_hitpoint").getAsString());
        assertEquals(.75, values.get("adaptive_variation_strength").getAsDouble(), 1e-9);
        assertEquals("Nearest",values.get("adaptive_aim_point").getAsString());
        assertFalse(values.get("adaptive_keep_valid_aim").getAsBoolean());
        a.getModeSetting("Rot Mode").setValue("Basic");
        a.getModeSetting("Adaptive Speed").setValue("Calm");
        a.getModeSetting("Adaptive Hitpoint").setValue("Stable");
        a.getNumberSetting("Adaptive Variation Strength").setValue(0D);
        assertTrue(c.getLastError(), c.loadConfig("Preset"));
        assertEquals("Adaptive", a.getModeSetting("Rot Mode").getValue());
        assertEquals("Swift", a.getModeSetting("Adaptive Speed").getValue());
        assertEquals("Dynamic", a.getModeSetting("Adaptive Hitpoint").getValue());
        assertEquals(.75, a.getNumberSetting("Adaptive Variation Strength").getValue(), 1e-9);
        assertEquals("Nearest",a.getModeSetting("Adaptive Aim Point").getValue());
        assertFalse(a.getBooleanSetting("Adaptive Keep Valid Aim").getValue());
        assertEquals(serialized, c.serialize());
    }

    @Test public void absentKeysResetToSafeAdaptiveDefaultsWithoutEditingUserFile() throws Exception {
        Aura a=new Aura();
        Path directory=temp.newFolder().toPath();
        ConfigManager config=new ConfigManager(directory,Collections.<Module>singletonList(a),
                ()->false,message->{});
        a.getModeSetting("Rot Mode").setValue("Adaptive");
        a.getModeSetting("Adaptive Speed").setValue("Swift");
        a.getModeSetting("Adaptive Hitpoint").setValue("Dynamic");
        a.getNumberSetting("Adaptive Variation Strength").setValue(1D);
        a.getModeSetting("Adaptive Aim Point").setValue("Nearest");
        a.getBooleanSetting("Adaptive Keep Valid Aim").setValue(false);
        String original="{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,"
                +"\"settings\":{\"rot_mode\":\"Adaptive\"}}}}";
        Path saved=directory.resolve("Older.json");
        Files.write(saved,original.getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(),config.loadConfig("Older"));
        assertEquals("Adaptive",a.getModeSetting("Rot Mode").getValue());
        assertEquals("Balanced",a.getModeSetting("Adaptive Speed").getValue());
        assertEquals("Stable",a.getModeSetting("Adaptive Hitpoint").getValue());
        assertEquals(.5,a.getNumberSetting("Adaptive Variation Strength").getValue(),0);
        assertEquals("Straight",a.getModeSetting("Adaptive Aim Point").getValue());
        assertTrue(a.getBooleanSetting("Adaptive Keep Valid Aim").getValue());
        assertEquals(original,new String(Files.readAllBytes(saved),StandardCharsets.UTF_8));
    }

    @Test public void invalidPresetSelectionCannotBeSavedOrApplyChanges() throws Exception {
        Aura a=new Aura();
        Path dir=temp.newFolder().toPath();
        ConfigManager config=new ConfigManager(dir,Collections.<Module>singletonList(a),
                ()->false,message->{});
        a.getModeSetting("Rot Mode").setValue("Adaptive");
        a.getModeSetting("Adaptive Speed").setValue("Balanced");
        String before=config.serialize();
        String invalid="{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,"
                +"\"settings\":{\"rot_mode\":\"Adaptive\",\"adaptive_speed\":\"WarpSpeed\"}}}}";
        Files.write(dir.resolve("Invalid.json"),invalid.getBytes(StandardCharsets.UTF_8));
        assertFalse(config.saveConfig("Invalid", config.readConfigData(config.defaultConfig.toPath().resolveSibling("Invalid.json"))));
        assertEquals(before,config.serialize());
    }
}
