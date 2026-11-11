package me.youngjibbit.clickgui;

import me.youngjibbit.clickgui.utils.SettingSubcategories;
import me.youngjibbit.module.impl.world.NScaffold;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.SubcategoryValue;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/** The real module's disclosure model stays complete while conditional controls change. */
public class NScaffoldSettingsLayoutTest {
    private static SubcategoryValue section(List<SubcategoryValue> sections,String name) {
        for(SubcategoryValue section:sections)if(section.getName().equals(name))return section;
        throw new AssertionError("Missing section "+name);
    }
    private static void visible(NScaffold module,boolean expected,String... names) {
        for(String name:names) {
            Value<?> value=module.getValueByName(name);assertNotNull(name,value);
            assertEquals(name,expected,value.isVisible());
        }
    }

    @Test public void semanticSectionsContainEachActualValueOnceWithoutMutatingRegistration() {
        NScaffold module=new NScaffold();List<Value<?>> original=new ArrayList<>(module.getValues());
        List<SubcategoryValue> sections=SettingSubcategories.create(module,original);
        List<String> names=new ArrayList<>();Map<String,String> assignments=new LinkedHashMap<>();
        Set<Value<?>> seen=Collections.newSetFromMap(new IdentityHashMap<Value<?>,Boolean>());
        NScaffold.Status before=module.getStatus();
        Map<Value<?>,Object> values=new IdentityHashMap<>();
        for(Value<?> value:original)values.put(value,value.getValue());
        for(SubcategoryValue section:sections) {
            names.add(section.getName());assertSame(module,section.owner());assertNull(section.getParent());
            assertFalse(original.contains(section));assertFalse(section.description().isEmpty());
            int previous=-1;
            for(Value<?> value:section.children()) {
                assertTrue("Exactly one presentation owner: "+value.getId(),seen.add(value));
                int position=original.indexOf(value);assertTrue(position>previous);previous=position;
                assignments.put(value.getId(),section.getName());
            }
            section.setValue(true);section.setValue(false);
        }
        assertEquals(Arrays.asList("Bridge","Clutch","Aiming","Rotation Speed","Rotation Shape","Rotation Pauses",
                "Placement","Safety"),names);
        assertEquals(original.size(),seen.size());assertTrue(seen.containsAll(original));
        assertEquals(original,module.getValues());assertEquals(before,module.getStatus());
        for(Value<?> value:original)assertEquals(value.getId(),values.get(value),value.getValue());
        assertEquals("Bridge",assignments.get("build_mode"));
        assertEquals("Clutch",assignments.get("fast_clutch_prediction"));
        assertEquals("Clutch",assignments.get("ground_ticks_to_sneak"));
        assertEquals("Aiming",assignments.get("advanced_rotations"));
        assertEquals("Rotation Speed",assignments.get("yaw_rotation_speed_(min)"));
        assertEquals("Rotation Speed",assignments.get("pitch_rotation_speed_(max)"));
        assertEquals("Rotation Shape",assignments.get("extra_smoothing_factor"));
        assertEquals("Rotation Shape",assignments.get("pitch_accel_factor"));
        assertEquals("Rotation Pauses",assignments.get("rotation_diff_buildup_to_stop"));
        assertEquals("Placement",assignments.get("placement_delay"));
        assertEquals("Safety",assignments.get("smart_disable_on_death"));
        assertEquals("Safety",assignments.get("smart_disable_on_world_change"));
    }

    @Test public void advancedAndClutchGroupsExposeTheirSwitchesAndOnlyApplicableChildren() {
        NScaffold module=new NScaffold();List<SubcategoryValue> sections=SettingSubcategories.create(module,module.getValues());
        assertEquals(1,section(sections,"Clutch").visibleCount());
        assertTrue(section(sections,"Clutch").isVisible());
        assertFalse(section(sections,"Rotation Speed").isVisible());
        assertFalse(section(sections,"Rotation Shape").isVisible());
        assertFalse(section(sections,"Rotation Pauses").isVisible());
        visible(module,true,"Clutch","Advanced Rotations","Turn Speed");
        visible(module,false,"Clutch Criteria","Fast Clutch Always","Ground Ticks to Sneak","Short Stop");

        module.getBooleanSetting("Advanced Rotations").setValue(true);
        assertEquals(4,section(sections,"Rotation Speed").visibleCount());
        assertEquals(4,section(sections,"Rotation Shape").visibleCount());
        assertEquals(1,section(sections,"Rotation Pauses").visibleCount());
        visible(module,false,"Turn Speed","Yaw Accel Factor","Pitch Accel Factor","Short Stop Duration (ms)");
        visible(module,true,"Smooth Mode","Extra Smoothing Factor","Accelerate","Imperfect Correlation","Short Stop");
        module.getBooleanSetting("Accelerate").setValue(true);
        module.getBooleanSetting("Short Stop").setValue(true);
        assertEquals(6,section(sections,"Rotation Shape").visibleCount());
        assertEquals(4,section(sections,"Rotation Pauses").visibleCount());
        visible(module,true,"Yaw Accel Factor","Pitch Accel Factor","Short Stop Duration (ms)",
                "Rotation Diff Buildup to Stop","Max Threshold Attempts to Stop");

        module.getBooleanSetting("Clutch").setValue(true);
        assertEquals(7,section(sections,"Clutch").visibleCount());
        visible(module,true,"Clutch Criteria","Clutch Rotation Mode","Fast Clutch Always","Fast Clutch on Miss",
                "Fast Clutch Prediction","Auto Sneak on Clutch");
        visible(module,false,"Ground Ticks to Sneak");
        module.getBooleanSetting("Auto Sneak on Clutch").setValue(true);
        assertEquals(8,section(sections,"Clutch").visibleCount());visible(module,true,"Ground Ticks to Sneak");

        module.getBooleanSetting("Advanced Rotations").setValue(false);
        visible(module,true,"Turn Speed","Advanced Rotations","Clutch Criteria","Ground Ticks to Sneak");
        visible(module,false,"Fast Clutch Always","Fast Clutch on Miss","Fast Clutch Prediction","Yaw Accel Factor",
                "Pitch Accel Factor","Short Stop","Short Stop Duration (ms)");
        assertFalse(section(sections,"Rotation Shape").isVisible());
        assertFalse(section(sections,"Rotation Pauses").isVisible());
        module.getBooleanSetting("Clutch").setValue(false);
        assertEquals(1,section(sections,"Clutch").visibleCount());visible(module,false,"Ground Ticks to Sneak");
    }

    @Test public void buildAndTargetStylesRevealRelevantSettingsWithoutErasingHiddenChoices() {
        NScaffold module=new NScaffold();
        visible(module,true,"Build Mode","Visible Rotation");
        visible(module,false,"Straight Telly Ticks","Diagonal Telly Ticks","Static-ify","Godbridge Pitch");
        module.getModeSetting("Visible Rotation").setValue("Godbridge");
        visible(module,true,"Static-ify","Godbridge Pitch");
        module.getModeSetting("Build Mode").setValue("Breezily");
        visible(module,false,"Visible Rotation","Static-ify","Godbridge Pitch","Straight Telly Ticks","Diagonal Telly Ticks");
        assertEquals("Godbridge",module.getModeSetting("Visible Rotation").getValue());
        module.getModeSetting("Build Mode").setValue("Telly");
        visible(module,true,"Visible Rotation","Static-ify","Godbridge Pitch","Straight Telly Ticks","Diagonal Telly Ticks");
        module.getModeSetting("Visible Rotation").setValue("Reverse");
        visible(module,true,"Static-ify");visible(module,false,"Godbridge Pitch");
        module.getModeSetting("Visible Rotation").setValue("Center");
        visible(module,false,"Static-ify","Godbridge Pitch");
        module.getModeSetting("Build Mode").setValue("Normal");
        visible(module,false,"Straight Telly Ticks","Diagonal Telly Ticks");
        assertEquals("Center",module.getModeSetting("Visible Rotation").getValue());
    }
}
