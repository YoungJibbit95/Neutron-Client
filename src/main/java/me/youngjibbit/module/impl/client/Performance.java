package me.youngjibbit.module.impl.client;

import me.youngjibbit.core.diagnostics.ChunkFrameBudget;
import me.youngjibbit.core.diagnostics.PerformanceCaptureSession;
import me.youngjibbit.module.Category;
import me.youngjibbit.module.Module;
import me.youngjibbit.module.ModuleInfo;
import me.youngjibbit.value.impl.SliderValue;

/** Optional limits; correctness, persistent scheduling and default limits always apply. */
@ModuleInfo(name="Performance", description="Custom chunk upload limits. Disabled uses 1 ms / 4 MiB defaults.", category=Category.Client)
public final class Performance extends Module {
    public final SliderValue uploadTime = new SliderValue("Upload Budget Ms", this, 1D, .25, 8);
    public final SliderValue uploadBytes = new SliderValue("Upload Budget MiB", this, 4D, .25, 32);

    @Override public void onEnable() { super.onEnable(); apply(); }
    @Override public void onDisable() {
        PerformanceCaptureSession.INSTANCE.abort("Chunk upload limits changed");
        ChunkFrameBudget.defaults(); super.onDisable();
    }
    @Override public void onValueChange() { if (isToggled()) apply(); }

    private void apply() {
        // Value construction can notify the owner before the next field is initialized.
        if (uploadTime != null && uploadBytes != null) {
            PerformanceCaptureSession.INSTANCE.abort("Chunk upload limits changed");
            ChunkFrameBudget.configure(uploadTime.getValue(), uploadBytes.getValue());
        }
    }
}
