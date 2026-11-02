package me.youngjibbit.core.combat;

import me.youngjibbit.module.Module;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;

/** Equal click options, separate values and RNG state for each owning module. */
public final class ClickTimingSettings {
    public final CheckBoxValue enabled, debugging;
    public final ComboValue mode;
    public final SliderValue amount;
    private final SliderValue min, max;
    private final ClickRandomizer randomizer = new ClickRandomizer();

    public ClickTimingSettings(Module parent, SliderValue min, SliderValue max, boolean aura) {
        this.min = min; this.max = max;
        enabled = new CheckBoxValue(aura ? "Click Randomization Enabled" : "Enable Randomization", parent, true);
        mode = new ComboValue(aura ? "Click Randomization Mode" : "Randomization Mode", parent, ClickRandomizer.modes(), 1);
        amount = new SliderValue(aura ? "Click Randomization Amount" : "Randomization", parent, 3D, .5, 5);
        debugging = new CheckBoxValue(aura ? "Click Debugging" : "Debugging", parent, false);
        amount.setVisibility(m -> enabled.getValue());
    }

    public ClickRandomizer.Settings snapshot() {
        // Normalize only after both settings have loaded, independent of JSON field order.
        return new ClickRandomizer.Settings(min.getValue(), max.getValue(), mode.getValue(), enabled.getValue(), amount.getValue());
    }
    public ClickRandomizer.Sample sample() { return randomizer.sample(snapshot()); }

    /** Invoked before incoming Aura settings, so explicit new keys win over legacy cadence. */
    public void legacyDefaults() {
        enabled.setValue(false); mode.setValue("Basic"); amount.reset(); debugging.setValue(false);
    }
}
