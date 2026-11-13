package me.youngjibbit.clickgui;

import me.youngjibbit.clickgui.utils.SettingSubcategories;
import me.youngjibbit.module.impl.render.BedESP;
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

public class BedESPSettingsLayoutTest {
    @Test public void everyOverlayControlHasExactlyOneSemanticSectionWithoutRegistrationOrStateChanges() {
        BedESP module = new BedESP(); List<Value<?>> original = new ArrayList<>(module.getValues());
        Map<Value<?>, Object> values = new IdentityHashMap<>(); for (Value<?> value : original) values.put(value, value.getValue());
        List<SubcategoryValue> sections = SettingSubcategories.create(module, original);
        List<String> names = new ArrayList<>(); Map<String, String> group = new LinkedHashMap<>();
        Set<Value<?>> seen = Collections.newSetFromMap(new IdentityHashMap<Value<?>, Boolean>());
        for (SubcategoryValue section : sections) {
            names.add(section.getName()); assertSame(module, section.owner()); assertFalse(original.contains(section));
            int previous = -1;
            for (Value<?> value : section.children()) {
                assertTrue(value.getId(), seen.add(value)); assertTrue(original.indexOf(value) > previous);
                previous = original.indexOf(value); group.put(value.getId(), section.getName());
            }
            section.setValue(true); section.setValue(false);
        }
        assertEquals(Arrays.asList("Targets", "Appearance", "Widgets"), names); assertEquals(14, seen.size());
        assertEquals(original, module.getValues()); assertFalse(module.isToggled());
        for (Value<?> value : original) assertEquals(value.getId(), values.get(value), value.getValue());
        assertEquals("Targets", group.get("range"));
        for (String id : Arrays.asList("esp_mode", "color_source", "glow_strength", "glow_radius", "box_opacity", "line_width"))
            assertEquals(id, "Appearance", group.get(id));
        for (String id : Arrays.asList("widgets", "max_widgets", "widget_scale", "widget_height", "show_layers", "show_team", "show_exposure"))
            assertEquals(id, "Widgets", group.get(id));
        assertArrayEquals(new String[]{"Glow", "Flame", "Lines", "Outline"}, module.mode.getOptions());
        assertArrayEquals(new String[]{"Theme", "Team hint"}, module.colors.getOptions());
    }

    @Test public void hidingGlowAndWidgetControlsPreservesTheirChoices() {
        BedESP module = new BedESP(); module.glowRadius.setValue(11D); module.widgetScale.setValue(1.25D);
        module.showTeam.setValue(false); module.mode.setValue("Outline"); module.widgets.setValue(false);
        assertFalse(module.glowStrength.isVisible()); assertFalse(module.glowRadius.isVisible());
        for (Value<?> value : new Value<?>[]{module.maxWidgets, module.widgetScale, module.widgetHeight, module.showLayers, module.showTeam, module.showExposure})
            assertFalse(value.getId(), value.isVisible());
        assertTrue(module.widgets.isVisible()); assertTrue(module.colors.isVisible()); assertTrue(module.range.isVisible());
        module.mode.setValue("Glow"); module.widgets.setValue(true);
        assertTrue(module.glowRadius.isVisible()); assertTrue(module.widgetScale.isVisible());
        assertEquals(11, module.glowRadius.getValue(), 0); assertEquals(1.25, module.widgetScale.getValue(), 0); assertFalse(module.showTeam.getValue());
    }
}
