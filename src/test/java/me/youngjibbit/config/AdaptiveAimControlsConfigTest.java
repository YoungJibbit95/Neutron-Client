package me.youngjibbit.config;

import me.youngjibbit.core.combat.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Collections;
import static org.junit.Assert.*;

public class AdaptiveAimControlsConfigTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();
    private RotationSettings settings(Aura aura)throws Exception {
        Field field=Aura.class.getDeclaredField("rotationSettings");field.setAccessible(true);
        return (RotationSettings)field.get(aura);
    }
    @Test public void adaptiveSnapshotIgnoresHiddenLegacySettingsAndNormalizesTheRange()throws Exception {
        Aura aura=new Aura();RotationSettings s=settings(aura);
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        aura.getModeSetting("Adaptive Rotation Engine").setValue("Neutron");
        aura.getBooleanSetting("Smart Aim").setValue(true);aura.getBooleanSetting("Heuristics").setValue(true);
        aura.getBooleanSetting("Stop on Target").setValue(true);
        aura.getBooleanSetting("Adaptive Keep Valid Aim").setValue(false);
        aura.getNumberSetting("adaptive_min_aim_speed").setValue(800D);
        aura.getNumberSetting("adaptive_max_aim_speed").setValue(300D);
        RotationProfile p=s.snapshot();
        assertTrue(p.adaptiveExplicitControls);assertFalse(p.smartAim);assertFalse(p.heuristics);
        assertFalse(p.stopOnTarget);assertFalse(p.adaptiveKeepAim);
        assertEquals(300,p.adaptiveMinAimSpeed,0);assertEquals(800,p.adaptiveMaxAimSpeed,0);
        assertFalse(aura.getValueByName("Yaw Speed").isVisible());
        assertFalse(aura.getValueByName("Stop on Target").isVisible());
        assertTrue(aura.getValueByName("adaptive_max_aim_speed").isVisible());
        aura.getModeSetting("Rot Mode").setValue("Smooth");
        assertTrue(s.snapshot().smartAim);assertTrue(s.snapshot().heuristics);assertTrue(s.snapshot().stopOnTarget);
        assertTrue(aura.getValueByName("Yaw Speed").isVisible());
    }
    @Test public void oldConfigMigratesIncomingAxisSpeedsAndDoesNotInheritWarmNoiseOrCurve()throws Exception {
        Aura aura=new Aura();Path dir=temp.newFolder().toPath();
        ConfigManager manager=new ConfigManager(dir,Collections.<Module>singletonList(aura),()->false,m->{});
        aura.getModeSetting("Adaptive Noise Profile").setValue("Off");
        aura.getModeSetting("Adaptive Curve").setValue("Linear");
        aura.getNumberSetting("adaptive_max_aim_speed").setValue(1400D);
        String json="{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,\"settings\":{"+
                "\"yaw_speed\":20,\"pitch_speed\":10,\"rot_mode\":\"Adaptive\",\"adaptive_speed\":\"Swift\"}}}}";
        Files.write(dir.resolve("Old.json"),json.getBytes(StandardCharsets.UTF_8));
        assertTrue(manager.getLastError(),manager.loadConfig("Old"));
        RotationProfile p=settings(aura).snapshot();
        assertEquals(496,p.adaptiveMaxAimSpeed,0);assertEquals(.5,p.adaptivePitchScale,0);
        assertEquals(AdaptivePresetMotion.Noise.SOFT,p.adaptiveNoise);
        assertEquals(AdaptiveMotionController.Curve.RELATIVE,p.adaptiveCurve);
        assertEquals(json,new String(Files.readAllBytes(dir.resolve("Old.json")),StandardCharsets.UTF_8));
    }
    @Test public void explicitNewControlsRoundTripAndOverrideLegacySpeeds()throws Exception {
        Aura aura=new Aura();Path dir=temp.newFolder().toPath();
        ConfigManager manager=new ConfigManager(dir,Collections.<Module>singletonList(aura),()->false,m->{});
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        aura.getModeSetting("Adaptive Rotation Engine").setValue("Neutron");
        aura.getNumberSetting("adaptive_min_aim_speed").setValue(260D);
        aura.getNumberSetting("adaptive_max_aim_speed").setValue(1100D);
        aura.getNumberSetting("Adaptive Pitch Scale").setValue(.7);
        aura.getModeSetting("Adaptive Aim Point").setValue("Head");
        aura.getModeSetting("Adaptive Noise Profile").setValue("Organic");
        aura.getNumberSetting("Adaptive Noise Tempo").setValue(1.8);
        assertTrue(manager.saveConfig("New"));
        aura.getNumberSetting("adaptive_max_aim_speed").setValue(200D);
        assertTrue(manager.getLastError(),manager.loadConfig("New"));
        RotationProfile p=settings(aura).snapshot();
        assertEquals(260,p.adaptiveMinAimSpeed,0);assertEquals(1100,p.adaptiveMaxAimSpeed,0);
        assertEquals(.7,p.adaptivePitchScale,1e-6);assertEquals(1.8,p.adaptiveNoiseTempo,1e-6);
        assertEquals(AdaptivePresetMotion.AimPoint.HEAD,p.adaptiveAimPoint);
        assertEquals(AdaptivePresetMotion.Noise.ORGANIC,p.adaptiveNoise);
    }

    @Test public void missingOneOrBothLegacyAxesMatchFreshImportWithoutChangingOtherModes()throws Exception {
        for(String axes:new String[]{"",",\"yaw_speed\":20",",\"pitch_speed\":10"}) {
            RotationProfile[] profiles=new RotationProfile[2];
            for(int warm=0;warm<2;warm++) {
                Aura aura=new Aura();Path dir=temp.newFolder().toPath();
                if(warm==1) {
                    aura.getNumberSetting("Yaw Speed").setValue(90D);
                    aura.getNumberSetting("Pitch Speed").setValue(1D);
                    aura.getModeSetting("Adaptive Noise Profile").setValue("Off");
                }
                ConfigManager manager=new ConfigManager(dir,Collections.<Module>singletonList(aura),()->false,m->{});
                String json="{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,\"settings\":{"+
                        "\"rot_mode\":\"Adaptive\""+axes+"}}}}";
                Files.write(dir.resolve("Partial.json"),json.getBytes(StandardCharsets.UTF_8));
                assertTrue(manager.getLastError(),manager.loadConfig("Partial"));
                profiles[warm]=settings(aura).snapshot();
            }
            assertEquals(axes,profiles[0].adaptiveMinAimSpeed,profiles[1].adaptiveMinAimSpeed,0);
            assertEquals(axes,profiles[0].adaptiveMaxAimSpeed,profiles[1].adaptiveMaxAimSpeed,0);
            assertEquals(axes,profiles[0].adaptiveYawScale,profiles[1].adaptiveYawScale,0);
            assertEquals(axes,profiles[0].adaptivePitchScale,profiles[1].adaptivePitchScale,0);
        }
        Aura legacy=new Aura();Path dir=temp.newFolder().toPath();
        legacy.getNumberSetting("Yaw Speed").setValue(90D);legacy.getNumberSetting("Pitch Speed").setValue(1D);
        ConfigManager manager=new ConfigManager(dir,Collections.<Module>singletonList(legacy),()->false,m->{});
        Files.write(dir.resolve("Legacy.json"),
                "{\"Aura\":{\"Rot Mode\":\"Smooth\"}}".getBytes(StandardCharsets.UTF_8));
        assertTrue(manager.getLastError(),manager.loadConfig("Legacy"));
        assertEquals(90,legacy.getNumberSetting("Yaw Speed").getValue(),0);
        assertEquals(1,legacy.getNumberSetting("Pitch Speed").getValue(),0);
    }
}
