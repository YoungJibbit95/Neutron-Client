package me.youngjibbit.clickgui;

import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;

/** One category-column reveal, preserving its logical open flag and scroll owner. */
final class DropdownMotion {
    private final HudSpring height = new HudSpring();

    DropdownMotion() { reset(true); }

    void reset(boolean open) { height.snap(open ? 1 : 0); }

    float step(boolean open, float dt, ClientSettings settings) {
        float value = height.step(open ? 1 : 0, dt,
                settings == null || settings.animations.getValue(),
                settings != null && settings.reducedMotion.getValue(),
                settings == null ? "Balanced" : settings.motionIntensity.getValue());
        return clamp(value);
    }

    static float clamp(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
}
