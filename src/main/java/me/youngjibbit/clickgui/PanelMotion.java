package me.youngjibbit.clickgui;

import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;

/**
 * Panel-screen-only presentation adapter. MenuSpring remains the sole oscillator,
 * owned via HudSpring; layout, input, config and GL resources belong to ClickGui.
 */
final class PanelMotion {
    private final HudSpring category = new HudSpring();
    private final HudSpring entry = new HudSpring();

    /** Reopening a cached GUI starts from its actual category, never a stale offset. */
    void reset(float categoryOffset) {
        category.snap(categoryOffset);
        entry.snap(0);
    }

    float category(float target, float dt, ClientSettings settings) {
        return category.step(target, dt, enabled(settings), reduced(settings), intensity(settings));
    }

    /** Screen's entrance accent only; GuiTransitions owns exit/page capture. */
    float entrance(float dt, ClientSettings settings) {
        return clamp(entry.step(1, dt, enabled(settings), reduced(settings), intensity(settings)), 0, 1);
    }

    /** Testable reversal without scheduling a separate render loop on screen close. */
    float retreat(float dt, ClientSettings settings) {
        return clamp(entry.step(0, dt, enabled(settings), reduced(settings), intensity(settings)), 0, 1);
    }

    static boolean enabled(ClientSettings settings) {
        return settings == null || settings.animations.getValue();
    }

    static boolean reduced(ClientSettings settings) {
        return settings != null && settings.reducedMotion.getValue();
    }

    static String intensity(ClientSettings settings) {
        return settings == null ? "Balanced" : settings.motionIntensity.getValue();
    }

    static float clamp(float x, float low, float high) {
        return Float.isFinite(x) ? Math.max(low, Math.min(high, x)) : low;
    }
}
