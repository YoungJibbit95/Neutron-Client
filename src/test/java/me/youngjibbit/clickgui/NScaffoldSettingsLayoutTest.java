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

/** The complete Demise settings stay discoverable under their original conditions. */
public class NScaffoldSettingsLayoutTest {
    private static SubcategoryValue section(List<SubcategoryValue> sections, String name) {
        for (SubcategoryValue section : sections) if (section.getName().equals(name)) return section;
        throw new AssertionError("Missing section " + name);
    }
    private static void visible(NScaffold module, boolean expected, String... names) {
        for (String name : names) {
            Value<?> value = module.getValueByName(name); assertNotNull(name, value);
            assertEquals(name, expected, value.isVisible());
        }
    }

    @Test public void semanticSectionsContainEveryValueOnceWithoutChangingSettingsOrRegistration() {
        NScaffold module = new NScaffold(); List<Value<?>> original = new ArrayList<>(module.getValues());
        List<SubcategoryValue> sections = SettingSubcategories.create(module, original);
        List<String> names = new ArrayList<>(); Map<String,String> assignments = new LinkedHashMap<>();
        Set<Value<?>> seen = Collections.newSetFromMap(new IdentityHashMap<Value<?>,Boolean>());
        boolean toggled = module.isToggled();
        Map<Value<?>,Object> values = new IdentityHashMap<>();
        for (Value<?> value : original) values.put(value, value.getValue());
        for (SubcategoryValue section : sections) {
            names.add(section.getName()); assertSame(module, section.owner()); assertNull(section.getParent());
            assertFalse(original.contains(section)); assertFalse(section.description().isEmpty());
            int previous = -1;
            for (Value<?> value : section.children()) {
                assertTrue("Exactly one presentation owner: " + value.getId(), seen.add(value));
                int position = original.indexOf(value); assertTrue(position > previous); previous = position;
                assignments.put(value.getId(), section.getName());
            }
            section.setValue(true); section.setValue(false);
        }
        assertEquals(Arrays.asList("Bridge", "Clutch", "Aiming", "Rotation Speed", "Rotation Shape",
                "Rotation Pauses", "Movement", "Placement", "Appearance"), names);
        assertEquals(original.size(), seen.size()); assertTrue(seen.containsAll(original));
        assertEquals(original, module.getValues()); assertEquals(toggled, module.isToggled());
        for (Value<?> value : original) assertEquals(value.getId(), values.get(value), value.getValue());
        assertEquals("Bridge", assignments.get("mode"));
        assertEquals("Bridge", assignments.get("keepy"));
        assertEquals("Bridge", assignments.get("speed_keepy"));
        assertEquals("Clutch", assignments.get("instant_rot_predict"));
        assertEquals("Clutch", assignments.get("ground_ticks_to_sneak"));
        assertEquals("Aiming", assignments.get("rotations"));
        assertEquals("Aiming", assignments.get("silent"));
        assertEquals("Aiming", assignments.get("static_ify"));
        assertEquals("Rotation Speed", assignments.get("yaw_rotation_speed_(min)"));
        assertEquals("Rotation Speed", assignments.get("pitch_rotation_speed_(max)"));
        assertEquals("Rotation Shape", assignments.get("extra_smoothing_factor"));
        assertEquals("Rotation Shape", assignments.get("pitch_accel_factor"));
        assertEquals("Rotation Pauses", assignments.get("duration"));
        assertEquals("Rotation Pauses", assignments.get("rotation_diff_buildup_to_stop"));
        assertEquals("Movement", assignments.get("movement_correction"));
        assertEquals("Movement", assignments.get("tower_move"));
        assertEquals("Movement", assignments.get("pulldown_motion"));
        assertEquals("Movement", assignments.get("blocks_to_sneak"));
        assertEquals("Movement", assignments.get("safewalk_when_no_data"));
        assertEquals("Placement", assignments.get("raytrace"));
        assertEquals("Placement", assignments.get("strict_raytrace"));
        assertEquals("Appearance", assignments.get("target_block_esp"));
        assertFalse(section(sections, "Rotation Speed").description().contains("per player tick"));
    }

    @Test public void rotationControlsAreAvailableImmediatelyAndOnlyDependentChildrenAreHidden() {
        NScaffold module = new NScaffold(); List<SubcategoryValue> sections = SettingSubcategories.create(module, module.getValues());
        assertNull(module.getValueByName("Advanced Rotations"));
        assertEquals(4, section(sections, "Rotation Speed").visibleCount());
        assertEquals(4, section(sections, "Rotation Shape").visibleCount());
        assertEquals(1, section(sections, "Rotation Pauses").visibleCount());
        visible(module, true, "Silent", "Movement correction", "Smooth mode", "Extra smoothing factor",
                "Accelerate", "Imperfect correlation", "Short stop");
        visible(module, false, "Yaw accel factor", "Pitch accel factor", "Duration",
                "Rotation diff buildup to stop", "Max threshold attempts to stop");

        module.getBooleanSetting("Accelerate").setValue(true);
        module.getBooleanSetting("Short stop").setValue(true);
        assertEquals(6, section(sections, "Rotation Shape").visibleCount());
        assertEquals(4, section(sections, "Rotation Pauses").visibleCount());
        visible(module, true, "Yaw accel factor", "Pitch accel factor", "Duration",
                "Rotation diff buildup to stop", "Max threshold attempts to stop");

        module.getBooleanSetting("Silent").setValue(false);
        module.getModeSetting("Smooth mode").setValue("Relative");
        assertEquals(4, section(sections, "Rotation Speed").visibleCount());
        visible(module, true, "Movement correction", "Extra smoothing factor", "Yaw accel factor", "Duration");
    }

    @Test public void instantClutchCriteriaDependOnlyOnClutchAndGroundTicksAlsoRequireAutoSneak() {
        NScaffold module = new NScaffold(); List<SubcategoryValue> sections = SettingSubcategories.create(module, module.getValues());
        assertEquals(1, section(sections, "Clutch").visibleCount()); assertTrue(section(sections, "Clutch").isVisible());
        visible(module, false, "Clutch criteria", "Clutch rotation mode", "Instant rot Always",
                "Instant rot On miss", "Instant rot Predict", "Auto sneak on clutch", "Ground ticks to sneak");
        module.getBooleanSetting("Clutch").setValue(true);
        assertEquals(7, section(sections, "Clutch").visibleCount());
        visible(module, true, "Clutch criteria", "Clutch rotation mode", "Instant rot Always",
                "Instant rot On miss", "Instant rot Predict", "Auto sneak on clutch");
        visible(module, false, "Ground ticks to sneak");
        module.getBooleanSetting("Auto sneak on clutch").setValue(true);
        module.getBooleanSetting("Instant rot Predict").setValue(true);
        assertEquals(8, section(sections, "Clutch").visibleCount()); visible(module, true, "Ground ticks to sneak");
        module.getBooleanSetting("Clutch").setValue(false);
        assertEquals(1, section(sections, "Clutch").visibleCount());
        visible(module, false, "Instant rot Predict", "Ground ticks to sneak");
        assertTrue(module.getBooleanSetting("Instant rot Predict").getValue());
    }

    @Test public void bridgeRotationAndTowerChoicesFollowOriginalDisplayConditions() {
        NScaffold module = new NScaffold();
        visible(module, true, "Mode", "Rotations", "Tower", "Tower move");
        visible(module, false, "Straight telly ticks", "Diagonal telly ticks", "Static-ify", "PullDown motion");
        module.getModeSetting("Rotations").setValue("GodBridge");
        module.getModeSetting("Tower").setValue("PullDown");
        visible(module, true, "Static-ify");
        // Demise exposes this parameter for Tower move, not stationary Tower.
        visible(module, false, "PullDown motion");
        module.getModeSetting("Tower move").setValue("PullDown");
        visible(module, true, "PullDown motion");
        module.getModeSetting("Mode").setValue("Breezily");
        visible(module, false, "Rotations", "Static-ify", "Tower", "Tower move", "PullDown motion",
                "Straight telly ticks", "Diagonal telly ticks");
        assertEquals("GodBridge", module.getModeSetting("Rotations").getValue());
        assertEquals("PullDown", module.getModeSetting("Tower move").getValue());
        module.getModeSetting("Mode").setValue("Telly");
        visible(module, true, "Rotations", "Static-ify", "Straight telly ticks", "Diagonal telly ticks");
        visible(module, false, "Tower", "Tower move", "PullDown motion");
        module.getModeSetting("Rotations").setValue("Reverse"); visible(module, true, "Static-ify");
        module.getModeSetting("Rotations").setValue("Center"); visible(module, false, "Static-ify");
        module.getModeSetting("Mode").setValue("Normal");
        visible(module, true, "Tower", "Tower move", "PullDown motion");
        visible(module, false, "Straight telly ticks", "Diagonal telly ticks");
    }

    @Test public void addonParametersFollowTheirOwnSwitchAndKeepHiddenChoices() {
        NScaffold module = new NScaffold();
        visible(module, true, "Swing", "RayTrace", "KeepY", "Speed keepY", "Jump", "Safewalk",
                "Safewalk when no data", "Hover", "Sneak", "Target block ESP", "Strict rayTrace");
        visible(module, false, "Blocks to jump", "Only straight", "Blocks to sneak", "Only sneak on ground");
        module.getBooleanSetting("Strict rayTrace").setValue(true);
        module.getBooleanSetting("RayTrace").setValue(false);
        visible(module, false, "Strict rayTrace"); assertTrue(module.getBooleanSetting("Strict rayTrace").getValue());
        module.getBooleanSetting("Jump").setValue(true); module.getBooleanSetting("Sneak").setValue(true);
        visible(module, true, "Blocks to jump", "Only straight", "Blocks to sneak", "Only sneak on ground");
        module.getNumberSetting("Blocks to jump").setValue(0D); module.getBooleanSetting("Only sneak on ground").setValue(false);
        module.getBooleanSetting("Jump").setValue(false); module.getBooleanSetting("Sneak").setValue(false);
        visible(module, false, "Blocks to jump", "Only straight", "Blocks to sneak", "Only sneak on ground");
        assertEquals(0D, module.getNumberSetting("Blocks to jump").getValue(), 0);
        assertFalse(module.getBooleanSetting("Only sneak on ground").getValue());
    }
}
