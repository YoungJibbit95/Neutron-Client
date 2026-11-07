package me.youngjibbit.config;

import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.module.impl.combat.AimAssist;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ArsenicConfigTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    @Test public void optInMotionAndAuraHitpointsSurviveRoundTripButDoNotLeakIntoOlderProfiles() throws Exception {
        Aura aura = new Aura(); AimAssist assist = new AimAssist(); Scaffold scaffold = new Scaffold();
        Path dir = temp.newFolder().toPath();
        ConfigManager config = new ConfigManager(dir, Arrays.<Module>asList(aura, assist, scaffold), () -> false, m -> {});
        aura.getModeSetting("Rot Mode").setValue("Arsenic Lazy");
        assist.getModeSetting("Rotations").setValue("Arsenic Heuristics");
        scaffold.getModeSetting("Scaffold Approach").setValue("Arsenic");
        for (Module module : Arrays.<Module>asList(aura, assist, scaffold)) {
            assertNotNull(module.getBooleanSetting("Arsenic Soft Motion"));
            assertTrue(module.getBooleanSetting("Arsenic Soft Motion").isVisible());
            assertFalse(module.getBooleanSetting("Arsenic Soft Motion").getValue());
            module.getBooleanSetting("Arsenic Soft Motion").setValue(true);
        }
        assertNull(assist.getValueByName("Hitpoint Variation"));
        assertFalse(aura.getValueByName("Hitpoint Profile").isVisible());
        aura.getBooleanSetting("Hitpoint Variation").setValue(true);
        aura.getModeSetting("Hitpoint Profile").setValue("Dynamisch");
        assertTrue(aura.getValueByName("Hitpoint Speed").isVisible());
        assertTrue(config.saveConfig("Soft"));
        Files.write(dir.resolve("Old.json"), "{\"Aura\":{\"Rot Mode\":\"Arsenic Lazy\"},\"AimAssist\":{},\"Scaffold\":{}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(), config.loadConfig("Old"));
        for (Module module : Arrays.<Module>asList(aura, assist, scaffold))
            assertFalse(module.getBooleanSetting("Arsenic Soft Motion").getValue());
        assertFalse(aura.getBooleanSetting("Hitpoint Variation").getValue());
        assertTrue(config.getLastError(), config.loadConfig("Soft"));
        assertTrue(aura.getBooleanSetting("Hitpoint Variation").getValue());
        assertEquals("Dynamisch", aura.getModeSetting("Hitpoint Profile").getValue());
        for (Module module : Arrays.<Module>asList(aura, assist, scaffold))
            assertTrue(module.getBooleanSetting("Arsenic Soft Motion").getValue());
    }

    @Test public void newModesRoundTripAndLegacyConfigsCannotInheritTheirActivation() throws Exception {
        Aura aura=new Aura();AimAssist assist=new AimAssist();Scaffold scaffold=new Scaffold();
        Path dir=temp.newFolder().toPath();ConfigManager config=new ConfigManager(dir,Arrays.<Module>asList(aura,assist,scaffold),()->false,m->{});
        aura.getModeSetting("Rot Mode").setValue("Arsenic Lazy");assist.getModeSetting("Rotations").setValue("Arsenic Heuristics");
        scaffold.getModeSetting("Scaffold Approach").setValue("Arsenic");
        assertFalse(aura.getValueByName("Speed Curve").isVisible());assertTrue(assist.getValueByName("Miss Chance").isVisible());
        assertFalse(scaffold.getValueByName("Tower").isVisible());assertTrue(scaffold.getValueByName("Safety").isVisible());
        assertTrue(config.saveConfig("Arsenic"));
        aura.getModeSetting("Rot Mode").setValue("Basic");scaffold.getModeSetting("Scaffold Approach").setValue("Neutron");
        assertTrue(config.getLastError(),config.loadConfig("Arsenic"));
        assertEquals("Arsenic Lazy",aura.getModeSetting("Rot Mode").getValue());assertEquals("Arsenic",scaffold.getModeSetting("Scaffold Approach").getValue());
        Files.write(dir.resolve("Legacy.json"),"{\"Aura\":{\"Reach\":3},\"AimAssist\":{\"Range\":4},\"Scaffold\":{\"Rotations\":true}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(),config.loadConfig("Legacy"));
        assertEquals("Basic",aura.getModeSetting("Rot Mode").getValue());assertEquals("Normal",assist.getModeSetting("Rotations").getValue());
        assertEquals("Neutron",scaffold.getModeSetting("Scaffold Approach").getValue());
    }
}
