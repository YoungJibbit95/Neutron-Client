package me.youngjibbit.value.impl;

import com.google.gson.JsonElement;
import me.youngjibbit.core.combat.MoveFixController;
import me.youngjibbit.module.Module;

/** Stable config choices: Aus uses camera yaw; Einfach/Präzise preserve camera direction
 * by inverse input correction; Rotationsgebunden follows the confirmed module yaw.
 * Only Aura's former boolean has a legacy mapping; existing choices retain their meaning. */
public final class MoveFixValue extends ComboValue {
    private final boolean legacyBoolean;
    public MoveFixValue(Module parent, boolean legacyBoolean) {
        super("Movefix", parent, new String[]{"Aus", "Einfach", "Präzise", "Rotationsgebunden"}, 0);
        this.legacyBoolean = legacyBoolean;
    }
    public String decodeConfig(JsonElement json) {
        if (json != null && json.isJsonObject()) json = json.getAsJsonObject().get("value");
        if (json != null && json.isJsonPrimitive()) {
            if (json.getAsJsonPrimitive().isBoolean() && legacyBoolean)
                return json.getAsBoolean() ? "Einfach" : "Aus";
            if (json.getAsJsonPrimitive().isString()) return MoveFixController.Mode.fromSetting(json.getAsString()).label;
        }
        throw new IllegalArgumentException("Invalid movement correction setting.");
    }
}
