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
        for (String name : Arrays.asList("General", "Targets", "Bridge", "Clutch", "Interaction", "Aiming", "Rotation Speed", "Rotation Shape", "Rotation Pauses", "Movement", "Attacks", "Blocking", "Placement", "Selection", "Hotbar", "Cleanup", "Halo", "Hitpoint", "Safety", "Timing", "Appearance", "Advanced"))
            groups.put(name, new ArrayList<>());
        for (Value<?> value : values) groups.get(group(module.getId(), value.getId())).add(value);
        for (Map.Entry<String,List<Value<?>>> entry : groups.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            result.add(new SubcategoryValue(entry.getKey(), description(module.getId(), entry.getKey()), module, entry.getValue()));
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
        if (id.startsWith("smart_disable_on_") || is(id,"stop_on_damage","exclusive_timing")) return "Safety";
        if (is(module,"invmanager","stealer")) {
            if (is(id,"interaction_mode","preview_time_ms","resume_delay_ms")) return "Interaction";
            if (is(id,"speed_profile","min_action_delay_ms","max_action_delay_ms","variation_rate")) return "Timing";
            if (is(id,"pick_profile","randomness")) return "Selection";
            if ("drop_order".equals(id)) return "Cleanup";
        }
        if ("nscaffold".equals(module)) {
            if (is(id,"mode","keepy","speed_keepy","straight_telly_ticks","diagonal_telly_ticks")) return "Bridge";
            if (has(id,"clutch") || id.startsWith("instant_rot_") || "ground_ticks_to_sneak".equals(id)) return "Clutch";
            if (has(id,"rotation_speed")) return "Rotation Speed";
            if (is(id,"smooth_mode","extra_smoothing_factor","accelerate","yaw_accel_factor","pitch_accel_factor","imperfect_correlation")) return "Rotation Shape";
            if ("duration".equals(id) || has(id,"short_stop","buildup_to_stop","threshold_attempts_to_stop")) return "Rotation Pauses";
            if (is(id,"movement_correction","sprint_mode","speed_mode","jump","blocks_to_jump","only_straight",
                    "safewalk","safewalk_when_no_data","hover","sneak","blocks_to_sneak","only_sneak_on_ground",
                    "tower","tower_move","pulldown_motion")) return "Movement";
            if (is(id,"swing","raytrace","strict_raytrace","placement_mode","min_cps","max_cps")) return "Placement";
            if ("edge_safety".equals(id)) return "Safety";
            if ("target_block_esp".equals(id)) return "Appearance";
            return "Aiming";
        }
        if ("bedbreaker".equals(module)) {
            if (has(id,"aim","rotat","turn")) return "Aiming";
            if ("swing".equals(id)) return "Interaction";
            return "Targets";
        }
        if ("targetesp".equals(module)) {
            if ("target_halo".equals(id) || id.startsWith("halo_")) return "Halo";
            if (id.startsWith("hitpoint") || "show_ray_misses".equals(id)) return "Hitpoint";
            return "Appearance";
        }
        if ("invmanager".equals(module)) {
            if (is(id,"layout","vacant_slots") || id.startsWith("slot_") || id.endsWith("_slot")) return "Hotbar";
            if (has(id,"trash","keep_one","drop","armor")) return "Cleanup";
        }
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
    private static String description(String module, String name) {
        if ("nscaffold".equals(module)) {
            if ("Clutch".equals(name)) return "Clutch detection, instant rotations and grounded sneak";
            if ("Rotation Speed".equals(name)) return "Yaw and pitch speed ranges sampled each update";
            if ("Rotation Shape".equals(name)) return "Frame-based smoothing and acceleration";
            if ("Rotation Pauses".equals(name)) return "Rotation buildup, stop duration and threshold attempts";
            if ("Placement".equals(name)) return "Click mode, CPS, swing and placement ray tracing";
            if ("Safety".equals(name)) return "Stop or sneak only at an unsupported edge";
        }
        switch (name) {
            case "Targets": return "Selection, reach and filters";
            case "Bridge": return "Bridge behavior and inventory";
            case "Clutch": return "Reachable recovery, prediction and grounded sneak";
            case "Rotation Speed": return "Independent yaw and pitch limits per player tick";
            case "Rotation Shape": return "Interpolation and acceleration of the target rotation";
            case "Rotation Pauses": return "Optional pauses after confirmed rotation travel";
            case "Interaction": return "Visible preview and manual input priority";
            case "Selection": return "Stable order within the current container";
            case "Aiming": return "Rotation, precision and motion";
            case "Movement": return "Input and movement behavior";
            case "Attacks": return "Execution and click timing";
            case "Blocking": return "Sword use and release";
            case "Placement": return "Placement cadence and actions";
            case "Hotbar": return "Nine slots, priorities and fallback items";
            case "Cleanup": return "Protected items, duplicates and trash";
            case "Halo": return "Active target, trail and motion";
            case "Hitpoint": return "Accepted rotation and exact ray position";
            case "Safety": return "Lifecycle, recovery and safeguards";
            case "Timing": return "Delays and timing windows";
            case "Appearance": return "Style, colors and visibility";
            case "Advanced": return "Specialized controls and diagnostics";
            default: return "Core behavior and modes";
        }
    }
}
