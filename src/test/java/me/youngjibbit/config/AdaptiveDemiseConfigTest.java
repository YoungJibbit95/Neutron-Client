package me.youngjibbit.config;

import me.youngjibbit.core.combat.AdaptiveDemiseSettings;
import me.youngjibbit.core.combat.RotationSettings;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.value.impl.DemiseSliderValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.*;

public class AdaptiveDemiseConfigTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    private RotationSettings settings(Aura aura) throws Exception {
        Field field=Aura.class.getDeclaredField("rotationSettings");field.setAccessible(true);
        return (RotationSettings)field.get(aura);
    }

    @Test public void freshAdaptiveSelectsDemiseAndShowsOnlyItsEffectiveSmoothingControls() throws Exception {
        Aura aura=new Aura();RotationSettings settings=settings(aura);
        assertEquals("Basic",aura.getModeSetting("Rot Mode").getValue());
        assertEquals("Demise",aura.getModeSetting("Adaptive Rotation Engine").getValue());
        assertFalse(aura.getValueByName("Demise Smooth Mode").isVisible());
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        assertNotNull(settings.snapshot().adaptiveDemise);
        assertTrue(aura.getValueByName("Demise Smooth Mode").isVisible());
        assertFalse(aura.getValueByName("Adaptive Curve").isVisible());
        assertFalse(aura.getValueByName("Demise Yaw Acceleration").isVisible());
        aura.getBooleanSetting("Demise Accelerate").setValue(true);
        assertTrue(aura.getValueByName("Demise Yaw Acceleration").isVisible());
        assertEquals(.01,((DemiseSliderValue)aura.getNumberSetting("Demise Extra Smoothing")).getIncrement(),1e-7);
        aura.getModeSetting("Adaptive Aim Point").setValue("Neutron Noise");
        assertTrue(aura.getValueByName("Adaptive Legacy Hitpoint Profile").isVisible());
        aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Custom");
        assertTrue(aura.getValueByName("Adaptive Legacy Hitpoint Travel Speed").isVisible());
        aura.getModeSetting("Adaptive Rotation Engine").setValue("Neutron");
        assertNull(settings.snapshot().adaptiveDemise);
        assertFalse(aura.getValueByName("Demise Smooth Mode").isVisible());
        assertTrue(aura.getValueByName("Adaptive Curve").isVisible());
    }

    @Test public void originalControlsAndExplicitNoiseRoundTripThroughTheRealConfigManager() throws Exception {
        Aura aura=new Aura();Path directory=temporary.newFolder().toPath();
        ConfigManager config=new ConfigManager(directory,Collections.<Module>singletonList(aura),()->false,m->{});
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        aura.getModeSetting("Demise Smooth Mode").setValue("Relative");
        aura.getBooleanSetting("Demise Accelerate").setValue(true);
        aura.getBooleanSetting("Demise Imperfect Correlation").setValue(true);
        aura.getBooleanSetting("Demise Distance Speed").setValue(true);
        aura.getNumberSetting("Demise Extra Smoothing").setValue(.31);
        aura.getNumberSetting("Demise Min Yaw Speed").setValue(70D);
        aura.getNumberSetting("Demise Max Yaw Speed").setValue(25D);
        aura.getNumberSetting("Demise Speed Decrement").setValue(.7);
        aura.getBooleanSetting("Demise Short Stop").setValue(true);
        aura.getNumberSetting("Demise Stop Duration").setValue(125D);
        aura.getModeSetting("Adaptive Aim Point").setValue("Neutron Noise");
        aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Balanced");
        AdaptiveDemiseSettings.Configuration before=settings(aura).snapshot().adaptiveDemise;
        assertEquals(25,before.rotation.yawRotationSpeedMin,0);
        assertEquals(70,before.rotation.yawRotationSpeedMax,0);
        assertTrue(config.getLastError(),config.saveConfig("Demise"));
        String saved=config.serialize();
        aura.getModeSetting("Adaptive Rotation Engine").setValue("Neutron");
        aura.getNumberSetting("Demise Extra Smoothing").setValue(.9);
        assertTrue(config.getLastError(),config.loadConfig("Demise"));
        assertTrue(before.same(settings(aura).snapshot().adaptiveDemise));
        assertEquals("Neutron Noise",aura.getModeSetting("Adaptive Aim Point").getValue());
        assertEquals("Balanced",aura.getModeSetting("Adaptive Legacy Hitpoint Profile").getValue());
        assertEquals(saved,config.serialize());
    }

    @Test public void oldAdaptiveProfileKeepsNeutronAndMissingDemiseKeysDoNotInheritWarmValues() throws Exception {
        Aura aura=new Aura();Path directory=temporary.newFolder().toPath();
        ConfigManager config=new ConfigManager(directory,Collections.<Module>singletonList(aura),()->false,m->{});
        aura.getNumberSetting("Demise Extra Smoothing").setValue(.9);
        aura.getBooleanSetting("Demise Short Stop").setValue(true);
        String original="{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,\"settings\":{"+
                "\"rot_mode\":\"Adaptive\",\"adaptive_aim_point\":\"Legacy\",\"adaptive_legacy_hitpoint_profile\":\"Strong\"}}}}";
        Files.write(directory.resolve("Old.json"),original.getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(),config.loadConfig("Old"));
        assertEquals("Neutron",aura.getModeSetting("Adaptive Rotation Engine").getValue());
        assertNull(settings(aura).snapshot().adaptiveDemise);
        assertEquals(.15,aura.getNumberSetting("Demise Extra Smoothing").getValue(),1e-7);
        assertFalse(aura.getBooleanSetting("Demise Short Stop").getValue());
        assertEquals("Legacy",aura.getModeSetting("Adaptive Aim Point").getValue());
        assertEquals("Strong",aura.getModeSetting("Adaptive Legacy Hitpoint Profile").getValue());
        assertEquals(original,new String(Files.readAllBytes(directory.resolve("Old.json")),StandardCharsets.UTF_8));
    }
}
