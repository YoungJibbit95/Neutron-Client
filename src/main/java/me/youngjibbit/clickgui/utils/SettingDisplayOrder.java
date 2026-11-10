package me.youngjibbit.clickgui.utils;

import me.youngjibbit.module.Module;
import me.youngjibbit.value.Value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/** Presentation order only. Persistent registration order and IDs remain untouched. */
public final class SettingDisplayOrder {
    private SettingDisplayOrder() { }

    private static final List<String> TARGET = Arrays.asList(
            "rotations", "rot_mode", "mode", "sort_mode", "switch_delay_ms", "multi_targets",
            "reach", "aim_range_margin", "players", "animals", "mobs", "invisibles", "teams", "through_walls");
    private static final List<String> AIM = Arrays.asList(
            "adaptive_aim_point", "adaptive_keep_valid_aim", "adaptive_speed", "adaptive_curve",
            "adaptive_min_aim_speed", "adaptive_max_aim_speed", "adaptive_noise_profile",
            "adaptive_variation_strength", "adaptive_noise_tempo", "adaptive_hitpoint",
            "adaptive_advanced_aim", "adaptive_yaw_scale", "adaptive_pitch_scale");
    private static final List<String> MOVEMENT = Arrays.asList(
            "movefix", "body_rotation", "smooth_rotation_stop", "return_ticks");
    private static final List<String> ATTACK = Arrays.asList(
            "attack_mode", "min_cps", "max_cps", "fail_swing", "click_while_aiming",
            "click_randomization_enabled", "click_randomization_mode", "click_randomization_amount");
    private static final List<String> BLOCK = Arrays.asList(
            "autoblock", "autoblock_mode", "block_range", "reblock_delay", "custom_block_frequency", "block_frequency");

    public static List<Value<?>> values(Module module) {
        List<Value<?>> result = new ArrayList<>(module.getValues());
        if ("aura".equals(module.getId())) result.sort(Comparator.comparingInt(value -> rank(value.getId())));
        return result;
    }

    private static int rank(String id) {
        if (TARGET.contains(id)) return TARGET.indexOf(id);
        if (AIM.contains(id)) return 100 + AIM.indexOf(id);
        if (MOVEMENT.contains(id)) return 600 + MOVEMENT.indexOf(id);
        if (ATTACK.contains(id)) return 700 + ATTACK.indexOf(id);
        if (BLOCK.contains(id)) return 800 + BLOCK.indexOf(id);
        if (id.contains("debug")) return 900;
        return 400; // Stable sorting preserves the remaining legacy rotation controls.
    }
}
