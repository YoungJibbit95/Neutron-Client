package me.youngjibbit.module.impl.client;

import me.youngjibbit.core.diagnostics.PerformanceOptions;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.CheckBoxValue;
import me.youngjibbit.value.impl.ComboValue;
import me.youngjibbit.value.impl.SliderValue;

/** Optional strategies; persistent scheduling and correctness fixes always apply. */
@ModuleInfo(name="Performance", description="Optional CPU and scene-blur improvements. Half/Quarter blur trades detail for speed. Disabled restores standard rendering and 1 ms / 4 MiB limits.", category=Category.Client)
public final class Performance extends Module {
    public final SliderValue uploadTime = new SliderValue("Upload Budget Ms", this, 1D, .25, 8);
    public final SliderValue uploadBytes = new SliderValue("Upload Budget MiB", this, 4D, .25, 32);
    public final CheckBoxValue fastParticleCleanup = new CheckBoxValue("Fast Particle Cleanup", this, false);
    public final CheckBoxValue fastVisibilityGraph = new CheckBoxValue("Fast Visibility Graph", this, false);
    public final CheckBoxValue fastGaussian = new CheckBoxValue("Fast Gaussian", this, false);
    public final ComboValue sceneBlurQuality = new ComboValue("Scene Blur Quality", this, "Full",
            new String[]{"Full", "Half", "Quarter"});

    @Override public void onEnable() { super.onEnable(); apply(); }
    @Override public void onDisable() {
        PerformanceOptions.defaults(); super.onDisable();
    }
    @Override public void onValueChange() { if (isToggled()) apply(); }

    /** Old profiles must not silently inherit newly introduced opt-in strategies from warm state. */
    @Override public void beginConfigUpdate() {
        super.beginConfigUpdate();
        fastParticleCleanup.reset();
        fastVisibilityGraph.reset();
        fastGaussian.reset();
        sceneBlurQuality.reset();
    }

    private void apply() {
        // Value construction can notify the owner before the next field is initialized.
        if (uploadTime != null && uploadBytes != null && fastParticleCleanup != null
                && fastVisibilityGraph != null && fastGaussian != null && sceneBlurQuality != null) {
            int divisor = "Quarter".equals(sceneBlurQuality.getValue()) ? 4
                    : "Half".equals(sceneBlurQuality.getValue()) ? 2 : 1;
            PerformanceOptions.configure(fastParticleCleanup.getValue(), fastVisibilityGraph.getValue(),
                    fastGaussian.getValue(), divisor, uploadTime.getValue(), uploadBytes.getValue());
        }
    }
}
