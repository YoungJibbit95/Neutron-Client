package me.youngjibbit.config;

import me.youngjibbit.core.world.NScaffoldRotationController;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.impl.world.NScaffold;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/** Real module/profile boundaries: older imports cannot inherit a warm extension setup. */
public class NScaffoldExtensionConfigTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    private ConfigManager manager(NScaffold module,Path dir) {
        return new ConfigManager(dir,Collections.<Module>singletonList(module),()->false,message->{});
    }
    private static String v2(String settings) {
        return "{\"version\":2,\"modules\":{\"nscaffold\":{\"settings\":{"+settings+"}}}}";
    }
    private static void write(Path dir,String name,String content)throws Exception {
        Files.write(dir.resolve(name+".json"),content.getBytes(StandardCharsets.UTF_8));
    }
    private static String read(Path dir,String name)throws Exception {
        return new String(Files.readAllBytes(dir.resolve(name+".json")),StandardCharsets.UTF_8);
    }
    private static Map<String,Object> values(NScaffold module) {
        Map<String,Object> result=new LinkedHashMap<>();
        for(Value<?> value:module.getValues())result.put(value.getId(),value.getValue());
        return result;
    }
    private static void warmEveryExtension(NScaffold module) {
        boolean extension=false;
        for(Value<?> value:module.getValues()) {
            if("build_mode".equals(value.getId()))extension=true;
            if(!extension)continue;
            if(value instanceof CheckBoxValue)((CheckBoxValue)value).setValue(!(Boolean)value.getDefaultValue());
            else if(value instanceof ComboValue) {
                ComboValue choice=(ComboValue)value;
                for(String option:choice.getOptions())if(!option.equals(value.getDefaultValue())) {
                    choice.setValue(option);break;
                }
            } else if(value instanceof SliderValue) {
                SliderValue slider=(SliderValue)value;
                double original=((Number)value.getDefaultValue()).doubleValue();
                slider.setValue(original==slider.getMin()?slider.getMax():slider.getMin());
            } else fail("Unhandled NScaffold extension value: "+value.getId());
            assertNotEquals("Warm profile must differ: "+value.getId(),value.getDefaultValue(),value.getValue());
        }
    }
    private static void assertExtensionDefaults(NScaffold module) {
        boolean extension=false;
        for(Value<?> value:module.getValues()) {
            if("build_mode".equals(value.getId()))extension=true;
            if(extension)assertEquals(value.getId(),value.getDefaultValue(),value.getValue());
        }
        assertEquals("Normal",module.getModeSetting("Build Mode").getValue());
        assertFalse(module.getBooleanSetting("Clutch").getValue());
        assertFalse(module.getBooleanSetting("Advanced Rotations").getValue());
    }

    @Test public void originalOrdinalsAndChoicesArePreservedBeforeOneUniqueExtensionBlock() {
        NScaffold module=new NScaffold();List<String> ids=new ArrayList<>();
        for(Value<?> value:module.getValues())ids.add(value.getId());
        assertEquals(Arrays.asList("visible_rotation","turn_speed","godbridge_pitch","look_ahead","keep_height",
                "auto_select_blocks","placement_delay","swing","smart_disable_on_death","smart_disable_on_world_change"),
                ids.subList(0,10));
        assertEquals(Arrays.asList("build_mode","straight_telly_ticks","diagonal_telly_ticks","static_ify","clutch",
                "clutch_criteria","clutch_rotation_mode","fast_clutch_always","fast_clutch_on_miss","fast_clutch_prediction",
                "auto_sneak_on_clutch","ground_ticks_to_sneak","advanced_rotations","smooth_mode","extra_smoothing_factor",
                "yaw_rotation_speed_(min)","yaw_rotation_speed_(max)","pitch_rotation_speed_(min)","pitch_rotation_speed_(max)",
                "accelerate","yaw_accel_factor","pitch_accel_factor","imperfect_correlation","short_stop",
                "short_stop_duration_(ms)","rotation_diff_buildup_to_stop","max_threshold_attempts_to_stop"),ids.subList(10,ids.size()));
        assertEquals(ids.size(),new HashSet<>(ids).size());
        ComboValue rotation=module.getModeSetting("Visible Rotation");
        assertEquals("Standard",rotation.getOptions()[0]);assertEquals("Godbridge",rotation.getOptions()[1]);
        rotation.setSelectedIndex(1);assertEquals("Godbridge",rotation.getValue());
        rotation.setSelectedIndex(0);assertEquals("Standard",rotation.getValue());
        assertArrayEquals(new String[]{"Normal","Breezily","Telly"},module.getModeSetting("Build Mode").getOptions());
    }

    @Test public void coldAndWarmOldVersionTwoAndLegacyImportsRestoreOriginalBehavior()throws Exception {
        String[] profiles={v2("\"visible_rotation\":\"Godbridge\",\"turn_speed\":23,\"keep_height\":true"),
                "{\"NScaffold\":{\"Visible Rotation\":\"Godbridge\",\"Turn Speed\":23,\"Keep Height\":true}}",
                "{\"NScaffold\":{\"settings\":[{\"name\":\"Visible Rotation\",\"value\":\"Godbridge\"},"
                        +"{\"name\":\"Turn Speed\",\"value\":23},{\"name\":\"Keep Height\",\"value\":true}]}}"};
        for(String profile:profiles)for(boolean warm:new boolean[]{false,true}) {
            NScaffold module=new NScaffold();Path dir=temporary.newFolder().toPath();ConfigManager config=manager(module,dir);
            if(warm)warmEveryExtension(module);
            write(dir,"Old",profile);assertTrue(config.getLastError(),config.loadConfig("Old"));
            assertExtensionDefaults(module);
            assertEquals("Godbridge",module.getModeSetting("Visible Rotation").getValue());
            assertEquals(1,module.getModeSetting("Visible Rotation").getSelectedIndex());
            assertEquals(23,module.getNumberSetting("Turn Speed").getValue(),0);
            assertTrue(module.getBooleanSetting("Keep Height").getValue());
            assertEquals(profile,read(dir,"Old"));
        }
    }

    @Test public void everyExplicitExtensionValueRoundTripsIncludingFalseAndZero()throws Exception {
        NScaffold module=new NScaffold();Path dir=temporary.newFolder().toPath();ConfigManager config=manager(module,dir);
        warmEveryExtension(module);
        module.getModeSetting("Build Mode").setValue("Telly");
        module.getModeSetting("Visible Rotation").setValue("Reverse");
        module.getBooleanSetting("Auto Select Blocks").setValue(false);
        module.getNumberSetting("Ground Ticks to Sneak").setValue(0D);
        module.getNumberSetting("Max Threshold Attempts to Stop").setValue(0D);
        Map<String,Object> expected=values(module);
        assertTrue(config.getLastError(),config.saveConfig("Extended"));String serialized=config.serialize();
        for(Value<?> value:module.getValues())value.reset();
        assertTrue(config.getLastError(),config.loadConfig("Extended"));
        assertEquals(expected,values(module));assertEquals(serialized,config.serialize());
        assertEquals("Telly",module.getModeSetting("Build Mode").getValue());
        assertEquals("Reverse",module.getModeSetting("Visible Rotation").getValue());
        assertFalse(module.getBooleanSetting("Auto Select Blocks").getValue());
        assertEquals(0,module.getNumberSetting("Ground Ticks to Sneak").getValue(),0);
        assertEquals(0,module.getNumberSetting("Max Threshold Attempts to Stop").getValue(),0);
    }

    @Test public void partialModernImportsKeepExplicitChoicesAndResetOnlyOmittedExtensions()throws Exception {
        List<String> entries=Arrays.asList("\"build_mode\":\"Telly\"","\"clutch\":true","\"advanced_rotations\":true",
                "\"smooth_mode\":\"Relative\"","\"extra_smoothing_factor\":0.33","\"short_stop\":true",
                "\"ground_ticks_to_sneak\":0","\"max_threshold_attempts_to_stop\":0");
        for(boolean reversed:new boolean[]{false,true}) {
            NScaffold module=new NScaffold();Path dir=temporary.newFolder().toPath();ConfigManager config=manager(module,dir);
            warmEveryExtension(module);module.getNumberSetting("Look Ahead").setValue(.83);
            List<String> order=new ArrayList<>(entries);if(reversed)Collections.reverse(order);
            String profile=v2(String.join(",",order));write(dir,"Partial",profile);
            assertTrue(config.getLastError(),config.loadConfig("Partial"));
            assertEquals("Telly",module.getModeSetting("Build Mode").getValue());
            assertTrue(module.getBooleanSetting("Clutch").getValue());
            assertTrue(module.getBooleanSetting("Advanced Rotations").getValue());
            assertEquals("Relative",module.getModeSetting("Smooth Mode").getValue());
            assertEquals(.33,module.getNumberSetting("Extra Smoothing Factor").getValue(),0);
            assertTrue(module.getBooleanSetting("Short Stop").getValue());
            assertEquals(0,module.getNumberSetting("Ground Ticks to Sneak").getValue(),0);
            assertEquals(0,module.getNumberSetting("Max Threshold Attempts to Stop").getValue(),0);
            assertFalse(module.getBooleanSetting("Static-ify").getValue());
            assertFalse(module.getBooleanSetting("Fast Clutch Always").getValue());
            assertFalse(module.getBooleanSetting("Auto Sneak on Clutch").getValue());
            assertEquals(.25,module.getNumberSetting("Yaw Accel Factor").getValue(),0);
            assertEquals(.83,module.getNumberSetting("Look Ahead").getValue(),0);
            assertEquals(profile,read(dir,"Partial"));
        }
    }

    @Test public void anAbsentModuleIsNotAccidentallyResetByAnotherProfile()throws Exception {
        NScaffold module=new NScaffold();Path dir=temporary.newFolder().toPath();ConfigManager config=manager(module,dir);
        warmEveryExtension(module);Map<String,Object> before=values(module);
        write(dir,"Absent","{\"version\":2,\"modules\":{}}");
        assertTrue(config.getLastError(),config.loadConfig("Absent"));assertEquals(before,values(module));
    }

    @Test public void invalidModesRejectTheWholeImportAndLeaveFilesUnchanged()throws Exception {
        NScaffold module=new NScaffold();Path dir=temporary.newFolder().toPath();ConfigManager config=manager(module,dir);
        warmEveryExtension(module);module.getNumberSetting("Turn Speed").setValue(17D);
        String before=config.serialize();
        assertTrue(config.getLastError(),config.saveConfig("Preserve"));String preserved=read(dir,"Preserve");
        for(String id:Arrays.asList("build_mode","visible_rotation","clutch_criteria","clutch_rotation_mode","smooth_mode")) {
            String invalid=v2("\"turn_speed\":99,\"advanced_rotations\":false,\""+id+"\":\"Unknown choice\"");
            write(dir,"Invalid",invalid);
            assertFalse(id,config.loadConfig("Invalid"));assertEquals(before,config.serialize());
            assertEquals(invalid,read(dir,"Invalid"));
            assertFalse(config.saveConfig("Preserve",invalid));assertEquals(preserved,read(dir,"Preserve"));
        }
    }

    @Test public void crossedImportedLimitsReachTheRealModuleControllerAsNormalizedRanges()throws Exception {
        NScaffold module=new NScaffold();Path dir=temporary.newFolder().toPath();ConfigManager config=manager(module,dir);
        String profile=v2("\"advanced_rotations\":true,\"extra_smoothing_factor\":1,"
                +"\"yaw_rotation_speed_(min)\":80,\"yaw_rotation_speed_(max)\":10,"
                +"\"pitch_rotation_speed_(min)\":60,\"pitch_rotation_speed_(max)\":20");
        write(dir,"Crossed",profile);assertTrue(config.getLastError(),config.loadConfig("Crossed"));
        Method method=NScaffold.class.getDeclaredMethod("rotationSettings");method.setAccessible(true);
        NScaffoldRotationController.Settings settings=(NScaffoldRotationController.Settings)method.invoke(module);
        assertEquals(10,settings.yawMinimum,0);assertEquals(80,settings.yawMaximum,0);
        assertEquals(20,settings.pitchMinimum,0);assertEquals(60,settings.pitchMaximum,0);
        NScaffoldRotationController.Step turn=new NScaffoldRotationController(()->0,()->0)
                .update(0,0,0,90,0,false,false,settings);
        assertNotNull(turn);assertEquals(10,turn.yaw,0);assertEquals(0,turn.pitch,0);
        assertEquals(80,module.getNumberSetting("Yaw Rotation Speed (min)").getValue(),0);
        assertEquals(10,module.getNumberSetting("Yaw Rotation Speed (max)").getValue(),0);
        assertEquals(profile,read(dir,"Crossed"));
    }
}
