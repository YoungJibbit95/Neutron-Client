package me.youngjibbit.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.Scaffold;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ScaffoldRefinementConfigTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    @Test public void friendlyControlsAreOrderedAndAdvancedLimitsRemainReachable() {
        Scaffold s=new Scaffold();s.getModeSetting("Scaffold Approach").setValue("Adaptive Bridge");
        assertEquals("Sprint Mode",s.getValues().get(1).getName());
        assertArrayEquals(new String[]{"Off","On","Legit"},s.getModeSetting("Sprint Mode").getOptions());
        assertEquals("Off",s.getModeSetting("Sprint Mode").getValue());
        assertEquals("Natural",s.getModeSetting("Aim Noise Profile").getValue());
        assertTrue(s.getNumberSetting("Min Aim Speed").isVisible());
        assertTrue(s.getNumberSetting("Max Aim Speed").isVisible());
        assertFalse(s.getNumberSetting("Yaw Speed").isVisible());
        assertFalse(s.getNumberSetting("Bridge Prediction").isVisible());
        assertFalse(s.getNumberSetting("Initial Yaw Speed").isVisible());
        s.getBooleanSetting("Advanced Bridge Settings").setValue(true);
        assertTrue(s.getNumberSetting("Bridge Prediction").isVisible());
        s.getBooleanSetting("Phase Speed Limits").setValue(true);
        assertTrue(s.getNumberSetting("Initial Yaw Speed").isVisible());
        s.getModeSetting("Aim Noise Profile").setValue("Off");
        assertFalse(s.getNumberSetting("Aim Noise Amount").isVisible());
        assertFalse(s.getNumberSetting("Aim Noise Tempo").isVisible());
    }

    @Test public void explicitRangeAndSprintModeWinOverLegacyKeysInEitherOrder() throws Exception {
        for(boolean legacyFirst:new boolean[]{false,true}) {
            Scaffold s=new Scaffold();Path dir=temp.newFolder().toPath();
            ConfigManager configs=new ConfigManager(dir,Collections.<Module>singletonList(s),()->false,m->{});
            String old="\"aim_speed\":\"Custom\",\"sprint\":true";
            String current="\"min_aim_speed\":23,\"max_aim_speed\":67,\"sprint_mode\":\"Legit\",\"phase_speed_limits\":false,\"aim_noise_profile\":\"Steady\"";
            String document="{\"version\":2,\"modules\":{\"scaffold\":{\"settings\":{"+
                    (legacyFirst?old+","+current:current+","+old)+"}}}}";
            Files.write(dir.resolve("Mixed.json"),document.getBytes(StandardCharsets.UTF_8));
            assertTrue(configs.getLastError(),configs.loadConfig("Mixed"));
            assertEquals(23,s.getNumberSetting("Min Aim Speed").getValue(),0);
            assertEquals(67,s.getNumberSetting("Max Aim Speed").getValue(),0);
            assertEquals("Legit",s.getModeSetting("Sprint Mode").getValue());
            assertFalse(s.getBooleanSetting("Phase Speed Limits").getValue());
            assertEquals("Steady",s.getModeSetting("Aim Noise Profile").getValue());
            assertEquals(document,new String(Files.readAllBytes(dir.resolve("Mixed.json")),StandardCharsets.UTF_8));
        }
    }

    @Test public void rangesNoiseAndAllSprintModesRoundTripWithoutHiddenPresets() throws Exception {
        Scaffold s=new Scaffold();Path dir=temp.newFolder().toPath();
        ConfigManager configs=new ConfigManager(dir,Collections.<Module>singletonList(s),()->false,m->{});
        for(String mode:new String[]{"Off","On","Legit"})for(String noise:new String[]{"Off","Steady","Natural","Lively"}) {
            s.getModeSetting("Sprint Mode").setValue(mode);s.getModeSetting("Aim Noise Profile").setValue(noise);
            s.getNumberSetting("Min Aim Speed").setValue(27D);s.getNumberSetting("Max Aim Speed").setValue(98D);
            s.getNumberSetting("Aim Noise Amount").setValue(.61);s.getNumberSetting("Aim Noise Tempo").setValue(1.7);
            String before=configs.serialize();assertFalse(before.contains("\"aim_speed\""));
            assertTrue(configs.saveConfig("Current"));s.getModeSetting("Sprint Mode").setValue("Off");
            assertTrue(configs.loadConfig("Current"));assertEquals(before,configs.serialize());
        }
    }
}
