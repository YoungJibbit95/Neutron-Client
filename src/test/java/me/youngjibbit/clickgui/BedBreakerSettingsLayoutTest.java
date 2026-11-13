package me.youngjibbit.clickgui;

import me.youngjibbit.clickgui.utils.SettingSubcategories;
import me.youngjibbit.module.impl.player.BedBreaker;
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

/** Original mining controls keep their order; the indicator is a separate presentation group. */
public class BedBreakerSettingsLayoutTest {
    private static SubcategoryValue section(List<SubcategoryValue> sections, String name) {
        for (SubcategoryValue section : sections) if (section.getName().equals(name)) return section;
        throw new AssertionError("Missing section " + name);
    }
    private static void visible(BedBreaker module, boolean expected, String... names) {
        for (String name : names) {
            Value<?> value = module.getValueByName(name); assertNotNull(name, value);
            assertEquals(name, expected, value.isVisible());
        }
    }

    @Test public void semanticSectionsContainAllControlsExactlyOnceWithoutChangingValuesOrRegistration() {
        BedBreaker module = new BedBreaker(); List<Value<?>> original = new ArrayList<>(module.getValues());
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
        assertEquals(Arrays.asList("Targets", "Aiming", "Rotation Speed", "Rotation Shape", "Rotation Pauses", "Movement", "Safety", "Indicator"), names);
        assertEquals(23, seen.size()); assertTrue(seen.containsAll(original));
        assertEquals(original, module.getValues()); assertEquals(toggled, module.isToggled());
        for (Value<?> value : original) assertEquals(value.getId(), values.get(value), value.getValue());
        assertEquals("Targets", assignments.get("legit")); assertEquals("Aiming", assignments.get("silent"));
        for (String id : Arrays.asList("yaw_rotation_speed_(min)", "yaw_rotation_speed_(max)",
                "pitch_rotation_speed_(min)", "pitch_rotation_speed_(max)")) assertEquals(id, "Rotation Speed", assignments.get(id));
        for (String id : Arrays.asList("smooth_mode", "extra_smoothing_factor", "accelerate", "yaw_accel_factor",
                "pitch_accel_factor", "imperfect_correlation")) assertEquals(id, "Rotation Shape", assignments.get(id));
        for (String id : Arrays.asList("short_stop", "duration", "rotation_diff_buildup_to_stop", "max_threshold_attempts_to_stop"))
            assertEquals(id, "Rotation Pauses", assignments.get(id));
        assertEquals("Movement", assignments.get("movement_correction"));
        assertEquals("Safety", assignments.get("smart_disable_on_death"));
        assertEquals("Safety", assignments.get("smart_disable_on_world_change"));
        for (String id : Arrays.asList("break_indicator", "indicator_x", "indicator_y", "indicator_scale"))
            assertEquals(id, "Indicator", assignments.get(id));
        List<String> ids = new ArrayList<>(); for (Value<?> value : original) ids.add(value.getId());
        assertEquals(Arrays.asList("legit", "silent", "extra_smoothing_factor", "smooth_mode", "accelerate",
                "yaw_accel_factor", "pitch_accel_factor", "imperfect_correlation", "yaw_rotation_speed_(min)",
                "yaw_rotation_speed_(max)", "pitch_rotation_speed_(min)", "pitch_rotation_speed_(max)",
                "movement_correction", "short_stop", "duration", "rotation_diff_buildup_to_stop",
                "max_threshold_attempts_to_stop", "smart_disable_on_death", "smart_disable_on_world_change"), ids.subList(0, 19));
        assertEquals(Arrays.asList("break_indicator", "indicator_x", "indicator_y", "indicator_scale"), ids.subList(19, 23));
        assertFalse(section(sections, "Rotation Speed").description().contains("per player tick"));
        assertTrue(section(sections, "Rotation Shape").description().contains("Frame-based"));
    }

    @Test public void onlyAccelerationAndPauseParametersDependOnTheirOriginalSwitches() {
        BedBreaker module = new BedBreaker(); List<SubcategoryValue> sections = SettingSubcategories.create(module, module.getValues());
        assertNull(module.getValueByName("Advanced Rotations")); assertNull(module.getValueByName("Turn Speed"));
        assertNull(module.getValueByName("Swing"));
        assertEquals(1, section(sections, "Targets").visibleCount()); assertEquals(1, section(sections, "Aiming").visibleCount());
        assertEquals(4, section(sections, "Rotation Speed").visibleCount()); assertEquals(4, section(sections, "Rotation Shape").visibleCount());
        assertEquals(1, section(sections, "Rotation Pauses").visibleCount()); assertEquals(1, section(sections, "Movement").visibleCount());
        assertEquals(2, section(sections, "Safety").visibleCount());
        assertEquals(4, section(sections, "Indicator").visibleCount());
        visible(module, true, "Legit", "Silent", "Movement correction", "Smooth mode", "Extra smoothing factor",
                "Accelerate", "Imperfect correlation", "Short stop", "Smart Disable on Death", "Smart Disable on World Change");
        visible(module, false, "Yaw accel factor", "Pitch accel factor", "Duration",
                "Rotation diff buildup to stop", "Max threshold attempts to stop");

        module.getBooleanSetting("Accelerate").setValue(true); module.getBooleanSetting("Short stop").setValue(true);
        assertEquals(6, section(sections, "Rotation Shape").visibleCount()); assertEquals(4, section(sections, "Rotation Pauses").visibleCount());
        visible(module, true, "Yaw accel factor", "Pitch accel factor", "Duration",
                "Rotation diff buildup to stop", "Max threshold attempts to stop");

        module.getBooleanSetting("Legit").setValue(true); module.getBooleanSetting("Silent").setValue(false);
        module.getModeSetting("Smooth mode").setValue("Relative"); module.getModeSetting("Movement correction").setValue("None");
        assertEquals(4, section(sections, "Rotation Speed").visibleCount());
        visible(module, true, "Movement correction", "Extra smoothing factor", "Yaw accel factor", "Pitch accel factor", "Duration");
    }

    @Test public void hidingDependentParametersPreservesTheirStoredChoices() {
        BedBreaker module = new BedBreaker(); List<SubcategoryValue> sections = SettingSubcategories.create(module, module.getValues());
        module.getBooleanSetting("Accelerate").setValue(true); module.getBooleanSetting("Short stop").setValue(true);
        module.getNumberSetting("Yaw accel factor").setValue(.375D); module.getNumberSetting("Pitch accel factor").setValue(.625D);
        module.getNumberSetting("Duration").setValue(37.5D); module.getNumberSetting("Rotation diff buildup to stop").setValue(220.5D);
        module.getNumberSetting("Max threshold attempts to stop").setValue(0D);
        module.getBooleanSetting("Accelerate").setValue(false); module.getBooleanSetting("Short stop").setValue(false);
        assertEquals(4, section(sections, "Rotation Shape").visibleCount()); assertEquals(1, section(sections, "Rotation Pauses").visibleCount());
        visible(module, false, "Yaw accel factor", "Pitch accel factor", "Duration",
                "Rotation diff buildup to stop", "Max threshold attempts to stop");
        assertEquals(.375D, module.getNumberSetting("Yaw accel factor").getValue(), 0);
        assertEquals(.625D, module.getNumberSetting("Pitch accel factor").getValue(), 0);
        assertEquals(37.5D, module.getNumberSetting("Duration").getValue(), 0);
        assertEquals(220.5D, module.getNumberSetting("Rotation diff buildup to stop").getValue(), 0);
        assertEquals(0D, module.getNumberSetting("Max threshold attempts to stop").getValue(), 0);
    }

    @Test public void disablingTheIndicatorHidesOnlyItsPlacementControlsAndPreservesTheirValues() {
        BedBreaker module = new BedBreaker(); List<SubcategoryValue> sections = SettingSubcategories.create(module, module.getValues());
        module.getNumberSetting("Indicator X").setValue(12.5D); module.getNumberSetting("Indicator Y").setValue(81.25D);
        module.getNumberSetting("Indicator Scale").setValue(1.25D);
        module.getBooleanSetting("Break Indicator").setValue(false);
        assertEquals(1, section(sections, "Indicator").visibleCount());
        visible(module, false, "Indicator X", "Indicator Y", "Indicator Scale");
        visible(module, true, "Break Indicator", "Legit", "Silent", "Movement correction");
        module.getBooleanSetting("Break Indicator").setValue(true);
        assertEquals(4, section(sections, "Indicator").visibleCount());
        assertEquals(12.5D, module.getNumberSetting("Indicator X").getValue(), 0);
        assertEquals(81.25D, module.getNumberSetting("Indicator Y").getValue(), 0);
        assertEquals(1.25D, module.getNumberSetting("Indicator Scale").getValue(), 0);
    }
}
