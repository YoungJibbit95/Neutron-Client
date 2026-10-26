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
import java.util.*;
import static org.junit.Assert.*;

public class RotationProfileConfigTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();
    @Test public void oldProfilesCannotInheritNewOptionalFeaturesAndFutureProfilesAreNotOverwritten() throws Exception {
        AimAssist assist=new AimAssist();Path dir=temporary.newFolder().toPath();List<String> messages=new ArrayList<>();
        ConfigManager configs=new ConfigManager(dir,Collections.<Module>singletonList(assist),()->false,messages::add);
        for(String name:Arrays.asList("Smart Aim","Stop on Target","Heuristics","Interpolate Rotation","Advanced Pitch"))assist.getBooleanSetting(name).setValue(true);
        assist.getModeSetting("Random Mode").setValue("Doubled");
        Files.write(dir.resolve("Old.json"),"{\"AimAssist\":{\"Rotations\":\"Normal\"}}".getBytes(StandardCharsets.UTF_8));assertTrue(configs.loadConfig("Old"));
        for(String name:Arrays.asList("Smart Aim","Stop on Target","Heuristics","Interpolate Rotation","Advanced Pitch"))assertFalse(name,assist.getBooleanSetting(name).getValue());
        assertEquals("Noise",assist.getModeSetting("Random Mode").getValue());assertTrue(messages.toString().contains("features defaulted off"));
        String before=configs.serialize(),future="{\"version\":2,\"modules\":{\"aimassist\":{\"rotationVersion\":999,\"settings\":{}}}}";
        Files.write(dir.resolve("Future.json"),future.getBytes(StandardCharsets.UTF_8));assertFalse(configs.loadConfig("Future"));assertEquals(before,configs.serialize());assertEquals(future,new String(Files.readAllBytes(dir.resolve("Future.json")),StandardCharsets.UTF_8));
    }
    @Test public void historicalOptionNamesAndRestoredFlagsRoundTripIndependentlyWithoutRewritingTheOldFile() throws Exception {
        AimAssist assist=new AimAssist();Aura aura=new Aura();Path dir=temporary.newFolder().toPath();List<String> messages=new ArrayList<>();
        ConfigManager configs=new ConfigManager(dir,Arrays.<Module>asList(aura,assist),()->false,messages::add);
        String old="{\"AimAssist\":{\"Rotations\":\"Smooth\",\"FOV\":90,\"Min Rotation Speed\":2,\"Max Rotation Speed\":9,\"Smoothness\":7,\"Jitter Strength\":1.5,\"Jitter Speed\":25,\"smartAim\":true,\"stopOnTarget\":true,\"heuristics\":true,\"randomMode\":\"Doubled\"},\"Aura\":{\"Rot Mode\":\"Basic\",\"Range\":2,\"Interpolation Toggle\":true,\"AdvancedRots\":true,\"Random Mode\":\"OnlyRotation\",\"Random Strength\":0.2}}";
        Path file=dir.resolve("Historic.json");Files.write(file,old.getBytes(StandardCharsets.UTF_8));assertTrue(configs.getLastError(),configs.loadConfig("Historic"));
        assertEquals(old,new String(Files.readAllBytes(file),StandardCharsets.UTF_8));assertEquals("Legit",assist.getModeSetting("Rotations").getValue());
        assertTrue(messages.toString().contains("Smooth mapped to Legit"));assertEquals(25,assist.getNumberSetting("Frequency").getValue(),0);assertEquals(2,aura.getNumberSetting("Reach").getValue(),0);
        assertEquals("Doubled",assist.getModeSetting("Random Mode").getValue());assertEquals("OnlyRotation",aura.getModeSetting("Random Mode").getValue());
        assertTrue(assist.getBooleanSetting("Smart Aim").getValue());assertFalse(aura.getBooleanSetting("Smart Aim").getValue());assertTrue(aura.getBooleanSetting("Interpolate Rotation").getValue());assertFalse(assist.getBooleanSetting("Interpolate Rotation").getValue());
        assertTrue(configs.saveConfig("Roundtrip"));String expected=configs.serialize();
        for(Module m:Arrays.<Module>asList(aura,assist))for(me.youngjibbit.value.Value<?> v:m.getValues())v.reset();
        assertTrue(configs.loadConfig("Roundtrip"));assertEquals(expected,configs.serialize());
    }
    @Test public void oldNamedPresetsPreserveTheirTimingAndStoredInactiveValuesWithAnExplicitSwitch() throws Exception {
        for(Module module:Arrays.<Module>asList(new Aura(),new AimAssist())) {
            Path dir=temporary.newFolder().toPath();List<String> messages=new ArrayList<>();ConfigManager configs=new ConfigManager(dir,Collections.singletonList(module),()->false,messages::add);
            String mode=module instanceof Aura?"rot_mode":"rotations",choice=module instanceof Aura?"Advanced":"Test";
            Files.write(dir.resolve("Old.json"),("{\"version\":2,\"modules\":{\""+module.getId()+"\":{\"settings\":{\""+mode+"\":\""+choice+"\",\"randomization_style\":\"Sanft\",\"min_speed\":7,\"max_speed\":80,\"interpolation\":17}}}}").getBytes(StandardCharsets.UTF_8));
            assertTrue(configs.getLastError(),configs.loadConfig("Old"));assertTrue(module.getBooleanSetting("Preset Timing").getValue());
            assertEquals(7,module.getNumberSetting("Min Speed").getValue(),0);assertEquals(17,module.getNumberSetting("Interpolation").getValue(),0);
            java.lang.reflect.Field field=module.getClass().getDeclaredField("rotationSettings");field.setAccessible(true);
            me.youngjibbit.core.combat.RotationSettings settings=(me.youngjibbit.core.combat.RotationSettings)field.get(module);
            assertEquals(30,settings.snapshot().minSpeed,0);assertEquals(5,settings.snapshot().interpolation,0);assertTrue(messages.toString().contains("preset timing preserved"));
            module.getBooleanSetting("Preset Timing").setValue(false);assertEquals(7,settings.snapshot().minSpeed,0);assertEquals(17,settings.snapshot().interpolation,0);
            assertTrue(configs.saveConfig("New"));module.getBooleanSetting("Preset Timing").setValue(true);assertTrue(configs.loadConfig("New"));assertFalse(module.getBooleanSetting("Preset Timing").getValue());
        }
    }
    @Test public void independentProfilesAndRotationBoundChoicesRoundTripThroughRealConfigFiles() throws Exception {
        Aura aura=new Aura();AimAssist assist=new AimAssist();Scaffold scaffold=new Scaffold();
        ConfigManager configs=new ConfigManager(temporary.newFolder().toPath(),Arrays.<Module>asList(aura,assist,scaffold),()->false,message->{});
        aura.getModeSetting("Rot Mode").setValue("Advanced");assist.getModeSetting("Rotations").setValue("Legit");
        aura.getModeSetting("Noise Mode").setValue("Perlin");assist.getModeSetting("Noise Mode").setValue("Cellular");
        aura.getNumberSetting("Frequency").setValue(2.7);assist.getNumberSetting("Frequency").setValue(.4);
        aura.getModeSetting("Movefix").setValue("Rotationsgebunden");scaffold.getModeSetting("Movefix").setValue("Präzise");
        assertTrue(configs.saveConfig("Synthetic"));String expected=configs.serialize();
        aura.getModeSetting("Rot Mode").setValue("Basic");assist.getModeSetting("Rotations").setValue("Normal");
        aura.getNumberSetting("Frequency").setValue(1D);assist.getNumberSetting("Frequency").setValue(1D);
        assertTrue(configs.getLastError(),configs.loadConfig("Synthetic"));assertEquals(expected,configs.serialize());
        assertEquals("Rotationsgebunden",aura.getModeSetting("Movefix").getValue());assertEquals("Präzise",scaffold.getModeSetting("Movefix").getValue());
    }
    @Test public void historicalAimAssistModuleNamesAndSettingsMigrateToOneCurrentModule() throws Exception {
        for(String alias:new String[]{"AimAssist2","TestAimAssist"}) {
            Aura aura=new Aura();AimAssist assist=new AimAssist();Path dir=temporary.newFolder().toPath();List<String> messages=new ArrayList<>();
            ConfigManager configs=new ConfigManager(dir,Arrays.<Module>asList(aura,assist),()->false,messages::add);
            String legacy="{\""+alias+"\":{\"Rotations\":\"Test\",\"Noise Mode\":\"Cellular\",\"Frequency\":2.5,\"Cellular Distance Mode\":\"Hybrid\",\"Cellular Return Mode\":\"Distance2Div\",\"Cellular Jitter\":1.2,\"Fractal Mode\":\"DomainWarpIndependent\",\"Gain\":5,\"Octaves\":4,\"Lacunarity\":2.2,\"Weighted Strength\":5,\"Domain Warp Mode\":\"BasicGrid\",\"Domain Warp Amp\":2,\"Jitter Range\":1.7,\"Target Filter Method\":\"Health\"},\"Aura\":{\"Rot Mode\":\"Advanced\",\"Min Speed\":30,\"Max Speed\":40,\"Yaw Offset\":1,\"Pitch Offset\":2,\"Interpolation\":5,\"Hit Vec Radius\":0.5,\"Movefix\":true}}";
            legacy=legacy.replace("\"Octaves\":4","\"Octaves\":"+(alias.equals("AimAssist2")?"4.7":"0.2"));
            Files.write(dir.resolve("Old.json"),legacy.getBytes(StandardCharsets.UTF_8));
            boolean loaded=configs.loadConfig("Old");assertTrue(configs.getLastError(),loaded);
            assertEquals("Benutzerdefiniert",assist.getModeSetting("Randomization Style").getValue());
            assertTrue(messages.toString(),messages.get(messages.size()-1).contains("migrated"));
            assertEquals(alias.equals("AimAssist2")?4:1,assist.getNumberSetting("Octaves").getValue(),0);
            assertTrue(messages.toString(),messages.get(messages.size()-1).contains("octave"));
            assertEquals("Test",assist.getModeSetting("Rotations").getValue());assertEquals("Cellular",assist.getModeSetting("Noise Mode").getValue());
            assertEquals(2.5,assist.getNumberSetting("Frequency").getValue(),0);assertEquals(1.7,assist.getNumberSetting("Jitter").getValue(),0);
            assertEquals("Health",assist.getModeSetting("Sort Mode").getValue());assertEquals(5,assist.getNumberSetting("Weighted Strength").getValue(),0);
            assertEquals("Advanced",aura.getModeSetting("Rot Mode").getValue());assertEquals("Einfach",aura.getModeSetting("Movefix").getValue());
            assertTrue(messages.toString(),messages.get(messages.size()-1).contains("loaded"));
            assertTrue(configs.serialize().contains("\"aimassist\""));assertFalse(configs.serialize().contains(alias));
        }
    }
    @Test public void conflictingHistoricalAliasesAndUnknownChoicesRejectTheWholeLoad() throws Exception {
        AimAssist assist=new AimAssist();Path dir=temporary.newFolder().toPath();
        ConfigManager configs=new ConfigManager(dir,Collections.<Module>singletonList(assist),()->false,message->{});
        String before=configs.serialize();
        for(String content:new String[]{"{\"AimAssist\":{},\"AimAssist2\":{}}","{\"AimAssist2\":{\"Noise Mode\":\"imaginary\"}}"}) {
            Files.write(dir.resolve("Invalid.json"),content.getBytes(StandardCharsets.UTF_8));
            assertFalse(configs.loadConfig("Invalid"));assertEquals(before,configs.serialize());assertFalse(configs.getLastError().isEmpty());
        }
    }
    @Test public void evenMinimalOldFilesPreserveEachExplicitManualRotationParameter() throws Exception {
        for(Module module:Arrays.<Module>asList(new Aura(),new AimAssist(),new Scaffold())) {
            Path dir=temporary.newFolder().toPath();List<String> messages=new ArrayList<>();
            ConfigManager configs=new ConfigManager(dir,Collections.singletonList(module),()->false,messages::add);
            Map<String,String> parameters=new LinkedHashMap<>();
            parameters.put("yaw_offset","1");parameters.put("pitch_offset","2");parameters.put("min_speed","12");parameters.put("max_speed","65");
            parameters.put("yaw_speed","12");parameters.put("pitch_speed","15");parameters.put("interpolation","7");parameters.put("frequency","2.5");
            parameters.put("randomization","false");parameters.put("noise_strength","0.4");parameters.put("speed_variation","0.3");
            parameters.put("jitter","0");parameters.put("hit_vec_radius","0.2");parameters.put("random_hit_vec","true");
            for(Map.Entry<String,String> parameter:parameters.entrySet()) {
                module.getModeSetting("Randomization Style").setValue("Sanft");
                String json="{\"version\":2,\"modules\":{\""+module.getId()+"\":{\"settings\":{\""+parameter.getKey()+"\":"+parameter.getValue()+"}}}}";
                Files.write(dir.resolve("Minimal.json"),json.getBytes(StandardCharsets.UTF_8));
                boolean loaded=configs.loadConfig("Minimal");assertTrue(configs.getLastError(),loaded);
                assertEquals(module.getId()+"."+parameter.getKey(),"Benutzerdefiniert",module.getModeSetting("Randomization Style").getValue());
                assertTrue(messages.toString(),messages.get(messages.size()-1).contains("migrated"));
            }
        }
    }

}
