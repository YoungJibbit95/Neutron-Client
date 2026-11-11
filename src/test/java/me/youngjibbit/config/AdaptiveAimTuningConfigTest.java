package me.youngjibbit.config;

import me.youngjibbit.core.combat.*;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.combat.Aura;
import me.youngjibbit.value.Value;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class AdaptiveAimTuningConfigTest {
    @Rule public TemporaryFolder temp=new TemporaryFolder();

    private static RotationSettings settings(Aura aura)throws Exception {
        Field field=Aura.class.getDeclaredField("rotationSettings");field.setAccessible(true);
        return (RotationSettings)field.get(aura);
    }

    private ConfigManager manager(Aura aura,Path dir) {
        return new ConfigManager(dir,Collections.<Module>singletonList(aura),()->false,message->{});
    }

    private static String json(String values) {
        return "{\"version\":2,\"modules\":{\"aura\":{\"rotationVersion\":2,\"settings\":{"+values+"}}}}";
    }

    @Test public void newValuesAreOneAppendOnlyBlockAfterEveryExistingAuraSetting()throws Exception {
        Aura aura=new Aura();List<String> ids=new ArrayList<>();
        for(Value<?> value:aura.getValues())ids.add(value.getId());
        int start=ids.indexOf("adaptive_curve_strength");
        assertTrue(start>0);
        assertEquals("smart_disable_on_world_change",ids.get(start-1));
        assertEquals(Arrays.asList("adaptive_curve_strength","adaptive_path_amount","adaptive_path_tempo",
                "adaptive_initial_hitpoint_radius","adaptive_hitpoint_amount","adaptive_hitpoint_tempo",
                "adaptive_legacy_hitpoint_profile","adaptive_legacy_hitpoint_algorithm",
                "adaptive_legacy_hitpoint_radius","adaptive_legacy_hitpoint_travel_speed",
                "adaptive_legacy_hitpoint_travel_noise"),ids.subList(start,ids.size()));
        assertTrue(ids.indexOf("raycast")<start);assertTrue(ids.indexOf("attack_mode")<start);
        List<Value<?>> before=new ArrayList<>(aura.getValues());
        settings(aura).appendAdaptiveTuning(aura);
        assertEquals("duplicate registration must preserve every object/index",before,aura.getValues());
        assertEquals(ids.size(),new java.util.HashSet<>(ids).size());
    }

    @Test public void oldAndNewSpeedNoiseNamesResolveToTheSamePersistedValues() {
        Aura aura=new Aura();
        assertSame(aura.getValueByName("adaptive_variation_strength"),aura.getValueByName("Adaptive Noise Amount"));
        assertSame(aura.getValueByName("Adaptive Variation Strength"),aura.getValueByName("Adaptive Speed Noise Amount"));
        assertSame(aura.getValueByName("Adaptive Noise Profile"),aura.getValueByName("Adaptive Speed Noise Profile"));
        assertSame(aura.getValueByName("Adaptive Noise Tempo"),aura.getValueByName("Adaptive Speed Noise Tempo"));
        assertNotSame(aura.getValueByName("Hitpoint Profile"),aura.getValueByName("Adaptive Legacy Hitpoint Profile"));
        assertEquals("hitpoint_profile",aura.getValueByName("Hitpoint Profile").getId());
        assertNull(aura.getValueByName("adaptive_legacy_hitpoint_miss_chance"));
    }

    @Test public void visibilitySeparatesSpeedNoisePathAndLegacyHitpointControls() {
        Aura aura=new Aura();
        assertFalse(aura.getValueByName("adaptive_path_amount").isVisible());
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        aura.getModeSetting("Adaptive Speed Noise Profile").setValue("Off");
        assertFalse(aura.getValueByName("adaptive_variation_strength").isVisible());
        assertTrue(aura.getValueByName("adaptive_path_amount").isVisible());
        assertFalse(aura.getValueByName("adaptive_path_tempo").isVisible());
        aura.getNumberSetting("adaptive_path_amount").setValue(75D);
        assertTrue(aura.getValueByName("adaptive_path_tempo").isVisible());
        aura.getModeSetting("Adaptive Hitpoint").setValue("Organic");
        assertTrue(aura.getValueByName("adaptive_hitpoint_amount").isVisible());
        assertTrue(aura.getValueByName("adaptive_initial_hitpoint_radius").isVisible());
        aura.getModeSetting("Adaptive Aim Point").setValue("Legacy");
        assertFalse(aura.getValueByName("adaptive_hitpoint").isVisible());
        assertFalse(aura.getValueByName("adaptive_hitpoint_amount").isVisible());
        assertTrue(aura.getValueByName("adaptive_legacy_hitpoint_profile").isVisible());
        assertFalse(aura.getValueByName("adaptive_legacy_hitpoint_algorithm").isVisible());
        aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Strong");
        assertTrue(aura.getValueByName("adaptive_legacy_hitpoint_algorithm").isVisible());
        assertFalse(aura.getValueByName("adaptive_legacy_hitpoint_radius").isVisible());
        aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Custom");
        assertTrue(aura.getValueByName("adaptive_legacy_hitpoint_radius").isVisible());
        assertTrue(aura.getValueByName("adaptive_legacy_hitpoint_travel_speed").isVisible());
        assertTrue(aura.getValueByName("adaptive_legacy_hitpoint_travel_noise").isVisible());
        aura.getModeSetting("Adaptive Curve").setValue("Linear");
        assertFalse(aura.getValueByName("adaptive_curve_strength").isVisible());
        aura.getModeSetting("Rot Mode").setValue("Arsenic Heuristics");
        assertFalse(aura.getValueByName("adaptive_legacy_hitpoint_profile").isVisible());
    }

    @Test public void explicitIndependentControlsRoundTripIncludingExplicitZeroValues()throws Exception {
        Aura aura=new Aura();Path dir=temp.newFolder().toPath();ConfigManager config=manager(aura,dir);
        aura.getModeSetting("Rot Mode").setValue("Adaptive");
        aura.getModeSetting("Adaptive Aim Point").setValue("Legacy");
        aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Custom");
        aura.getModeSetting("Adaptive Legacy Hitpoint Algorithm").setValue("ValueCubic");
        aura.getNumberSetting("adaptive_curve_strength").setValue(37D);
        aura.getNumberSetting("adaptive_path_amount").setValue(0D);
        aura.getNumberSetting("adaptive_path_tempo").setValue(2.1);
        aura.getNumberSetting("adaptive_initial_hitpoint_radius").setValue(64D);
        aura.getNumberSetting("adaptive_hitpoint_amount").setValue(0D);
        aura.getNumberSetting("adaptive_hitpoint_tempo").setValue(.6);
        aura.getNumberSetting("adaptive_legacy_hitpoint_radius").setValue(85D);
        aura.getNumberSetting("adaptive_legacy_hitpoint_travel_speed").setValue(2.4);
        aura.getNumberSetting("adaptive_legacy_hitpoint_travel_noise").setValue(71D);
        assertTrue(config.getLastError(),config.saveConfig("Independent"));
        String serialized=config.serialize();
        AdaptivePresetMotion.Tuning expected=settings(aura).snapshot().adaptiveTuning;
        aura.getNumberSetting("adaptive_path_amount").setValue(95D);
        aura.getNumberSetting("adaptive_hitpoint_amount").setValue(95D);
        aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Off");
        assertTrue(config.getLastError(),config.loadConfig("Independent"));
        AdaptivePresetMotion.Tuning actual=settings(aura).snapshot().adaptiveTuning;
        assertTrue(expected.same(actual));assertEquals(0,actual.pathAmount,0);assertEquals(0,actual.hitpointAmount,0);
        assertEquals(serialized,config.serialize());
    }

    @Test public void oldNoiseImportsAreOrderIndependentAndDoNotInheritWarmIndependentSettings()throws Exception {
        String[] orders={"\"rot_mode\":\"Adaptive\",\"adaptive_variation_strength\":0.8,\"adaptive_noise_profile\":\"Organic\",\"adaptive_noise_tempo\":2",
                "\"adaptive_noise_tempo\":2,\"adaptive_noise_profile\":\"Organic\",\"adaptive_variation_strength\":0.8,\"rot_mode\":\"Adaptive\""};
        AdaptivePresetMotion.Tuning baseline=null;
        for(String values:orders)for(boolean warm:new boolean[]{false,true}) {
            Aura aura=new Aura();Path dir=temp.newFolder().toPath();ConfigManager config=manager(aura,dir);
            if(warm) {
                aura.getNumberSetting("adaptive_path_amount").setValue(99D);
                aura.getNumberSetting("adaptive_initial_hitpoint_radius").setValue(99D);
                aura.getNumberSetting("adaptive_hitpoint_tempo").setValue(.1);
                aura.getModeSetting("Adaptive Legacy Hitpoint Profile").setValue("Strong");
            }
            String original=json(values);Files.write(dir.resolve("Old.json"),original.getBytes(StandardCharsets.UTF_8));
            assertTrue(config.getLastError(),config.loadConfig("Old"));
            AdaptivePresetMotion.Tuning actual=settings(aura).snapshot().adaptiveTuning;
            assertEquals(.8,actual.pathAmount,1e-12);assertEquals(.8,actual.hitpointAmount,1e-12);
            assertEquals(2.3,actual.pathTempo,1e-12);assertEquals(2.3,actual.hitpointTempo,1e-12);
            assertEquals(0,actual.initialRadius,0);
            assertEquals(AdaptiveHitpointMotion.Profile.OFF,actual.legacyHitpoint.profile);
            if(baseline==null)baseline=actual;else assertTrue(baseline.same(actual));
            assertEquals(original,new String(Files.readAllBytes(dir.resolve("Old.json")),StandardCharsets.UTF_8));
        }
    }

    @Test public void oldOffNoiseCannotActivatePreviouslyCoupledChannelsOnImport()throws Exception {
        Aura aura=new Aura();Path dir=temp.newFolder().toPath();ConfigManager config=manager(aura,dir);
        Files.write(dir.resolve("Off.json"),json("\"rot_mode\":\"Adaptive\",\"adaptive_hitpoint\":\"Dynamic\","
                +"\"adaptive_noise_profile\":\"Off\",\"adaptive_variation_strength\":1").getBytes(StandardCharsets.UTF_8));
        assertTrue(config.getLastError(),config.loadConfig("Off"));
        RotationProfile p=settings(aura).snapshot();
        assertEquals(0,p.adaptiveTuning.pathAmount,0);assertEquals(0,p.adaptiveTuning.hitpointAmount,0);
        assertFalse(AdaptivePresetMotion.Configuration.fromProfile(p).changesHitpoint());
    }

    @Test public void hiddenLegacyHitpointAndMissValuesCannotAlterTheAdaptiveConfiguration()throws Exception {
        Aura aura=new Aura();aura.getModeSetting("Rot Mode").setValue("Adaptive");
        aura.getModeSetting("Adaptive Aim Point").setValue("Legacy");
        AdaptivePresetMotion.Configuration before=AdaptivePresetMotion.Configuration.fromProfile(settings(aura).snapshot());
        aura.getNumberSetting("Hit Vec Radius").setValue(5D);
        aura.getNumberSetting("Miss Chance").setValue(50D);
        aura.getModeSetting("Noise Mode").setValue("Cellular");
        aura.getBooleanSetting("Hitpoint Variation").setValue(true);
        aura.getModeSetting("Hitpoint Profile").setValue("Dynamisch");
        assertTrue(before.same(AdaptivePresetMotion.Configuration.fromProfile(settings(aura).snapshot())));
        aura.getModeSetting("Rot Mode").setValue("Arsenic Heuristics");
        RotationProfile legacy=settings(aura).snapshot();
        assertNull(legacy.adaptiveTuning);assertEquals(.5,legacy.missChance,1e-6);
        assertEquals(RotationProfile.HitpointNoise.DYNAMIC,legacy.hitpointNoise);
    }

    @Test public void invalidAlgorithmImportIsAtomicAndRetainsTheOriginalFile()throws Exception {
        Aura aura=new Aura();Path dir=temp.newFolder().toPath();ConfigManager config=manager(aura,dir);
        String before=config.serialize();
        String invalid=json("\"adaptive_initial_hitpoint_radius\":80,\"adaptive_legacy_hitpoint_algorithm\":\"Unknown\"");
        Files.write(dir.resolve("Invalid.json"),invalid.getBytes(StandardCharsets.UTF_8));
        assertFalse(config.loadConfig("Invalid"));assertEquals(before,config.serialize());
        assertEquals(invalid,new String(Files.readAllBytes(dir.resolve("Invalid.json")),StandardCharsets.UTF_8));
    }
}
