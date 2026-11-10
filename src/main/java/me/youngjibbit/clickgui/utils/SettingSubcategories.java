package me.youngjibbit.clickgui.utils;

import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;
import me.youngjibbit.value.impl.SubcategoryValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stable semantic presentation over existing values; no setting or config mutation. */
public final class SettingSubcategories {
    private SettingSubcategories() { }

    public static List<SubcategoryValue> create(Module module, List<Value<?>> values) {
        List<SubcategoryValue> result = new ArrayList<>();
        if (values.size() <= 5) return result;
        Map<String,List<Value<?>>> groups = new LinkedHashMap<>();
        for (String name : Arrays.asList("General", "Targets", "Bridge", "Aiming", "Movement", "Attacks", "Blocking", "Placement", "Safety", "Timing", "Appearance", "Advanced"))
            groups.put(name, new ArrayList<>());
        for (Value<?> value : values) groups.get(group(module.getId(), value.getId())).add(value);
        for (Map.Entry<String,List<Value<?>>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            result.add(new SubcategoryValue(entry.getKey(), description(entry.getKey()), module, entry.getValue()));
        }
        return result;
    }

    private static boolean has(String id, String... fragments) {
        for (String fragment : fragments) if (id.contains(fragment)) return true;
        return false;
    }
    private static boolean is(String id, String... names) {
        for (String name : names) if (name.equals(id)) return true;
        return false;
    }
    static String group(String module, String id) {
        if (has(id,"debug","diagnostic","file_path","recording","training")) return "Advanced";
        if ("scaffold".equals(module)) {
            if (is(id,"scaffold_approach","advanced_bridge_settings","keep_y","downwards","item_spoof")) return "Bridge";
            if (has(id,"edge_safety","prediction","safety","eagle","sneak","clutch")) return "Safety";
            if (has(id,"movefix","movement","sprint","tower","jump","timer","body_rotation","return_ticks","smooth_rotation_stop")) return "Movement";
            if (has(id,"place","click","variation_strength","swing")) return "Placement";
            return "Aiming";
        }
        if ("aura".equals(module) || "aimassist".equals(module) || "aim_assist".equals(module)) {
            if (is(id,"mode","sort_mode","switch_delay_ms","multi_targets","reach","range","fov","aim_range_margin","players","animals","mobs","invisibles","teams","through_walls")) return "Targets";
            if (has(id,"autoblock","block_frequency","block_range","reblock")) return "Blocking";
            if (has(id,"movefix","body_rotation","smooth_rotation_stop","return_ticks")) return "Movement";
            if (has(id,"attack","cps","click","fail_swing")) return "Attacks";
            return "Aiming";
        }
        if (is(module,"backtrack","tickbase","lagrange","lag_range")) {
            if (has(id,"range","target","player","animal","mob","invisible","team","fov","swinging")) return "Targets";
            if (has(id,"delay","time","_ms","tick","pulse","charge","release","duration","preactivation")) return "Timing";
            if (has(id,"render","color","outline","box","display")) return "Appearance";
            return "General";
        }
        if (has(id,"advanced")) return "Advanced";
        if (has(id,"font","color","colour","glow","blur","shadow","opacity","background","border","outline","theme","gradient","text","visual","display","widget","round","size","scale")) return "Appearance";
        if (has(id,"rotat","yaw","pitch","aim","jitter","noise","heuristic","hitpoint","bezier")) return "Aiming";
        if (has(id,"target","range","reach","fov") || is(id,"players","animals","mobs","invisibles","teams","through_walls")) return "Targets";
        if (has(id,"movefix","movement","sprint","strafe","jump","speed","friction")) return "Movement";
        if (has(id,"autoblock","reblock","block_frequency")) return "Blocking";
        if (has(id,"attack","cps","click")) return "Attacks";
        if (has(id,"delay","time","duration","interval","_ms","ticks")) return "Timing";
        return "General";
    }
    private static String description(String name) {
        switch (name) {
            case "Targets": return "Selection, reach and filters";
            case "Bridge": return "Bridge behavior and inventory";
            case "Aiming": return "Rotation, precision and motion";
            case "Movement": return "Input and movement behavior";
            case "Attacks": return "Execution and click timing";
            case "Blocking": return "Sword use and release";
            case "Placement": return "Placement cadence and actions";
            case "Safety": return "Edges, recovery and prediction";
            case "Timing": return "Delays and timing windows";
            case "Appearance": return "Style, colors and visibility";
            case "Advanced": return "Specialized controls and diagnostics";
            default: return "Core behavior and modes";
        }
    }
}
