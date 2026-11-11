package me.youngjibbit.core.inventory;

import me.youngjibbit.module.Module;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;

/** Shared labels/units; created after all historical values, including the Smart Disable pair. */
public final class InventoryInteractionSettings {
    public final ComboValue interaction, speed;
    public final SliderValue minimum, maximum, rate, preview, resume;
    public InventoryInteractionSettings(Module owner, double min, double max) {
        interaction = new ComboValue("Interaction Mode", owner, "Legacy", new String[]{"Legacy", "Click"});
        speed = new ComboValue("Speed Profile", owner, "Legacy", new String[]{"Legacy", "Uniform", "Smooth", "Waves"});
        minimum = new SliderValue("Min Action Delay MS", owner, min, 0, 1500, true);
        maximum = new SliderValue("Max Action Delay MS", owner, max, 0, 1500, true);
        rate = new SliderValue("Variation Rate", owner, .5, .1, 2, false);
        preview = new SliderValue("Preview Time MS", owner, 100D, 50, 500, true);
        resume = new SliderValue("Resume Delay MS", owner, 300D, 250, 1000, true);
        minimum.setVisibility(m -> !legacySpeed()); maximum.setVisibility(m -> !legacySpeed());
        rate.setVisibility(m -> "Smooth".equals(speed.getValue()) || "Waves".equals(speed.getValue()));
        preview.setVisibility(m -> click()); resume.setVisibility(m -> click());
    }
    public boolean click() { return "Click".equals(interaction.getValue()); }
    public boolean legacySpeed() { return "Legacy".equals(speed.getValue()); }
    public boolean ready(InventoryPacing pacing, double legacyMin, double legacyMax) {
        return pacing.ready(speed.getValue(), legacySpeed() ? legacyMin : minimum.getValue(),
                legacySpeed() ? legacyMax : maximum.getValue(), rate.getValue());
    }
}
