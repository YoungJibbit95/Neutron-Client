package me.youngjibbit.gui;

import me.youngjibbit.module.impl.client.island.IslandLayout;

/**
 * Thin multi-scalar adapter for the existing exact MenuSpring oscillator.
 * No scheduler, GL state, input capture, configuration writes or resource ownership.
 * Call once per render frame with a single, monotonic, bounded dt sample.
 */
public final class IslandMorphMotion {
    private final MenuSpring width = new MenuSpring();
    private final MenuSpring height = new MenuSpring();
    private final MenuSpring radius = new MenuSpring();
    private final MenuSpring opacity = new MenuSpring();
    private final MenuSpring content = new MenuSpring();
    private boolean initialized;
    private float contentAlpha = 1;

    public IslandLayout.Frame sample(IslandLayout.Frame desired, float seconds,
                                     boolean enabled, boolean reduced, String intensity) {
        MenuMotion preset = "Calm".equals(intensity) ? MenuMotion.CALM
                : "Expressive".equals(intensity) ? MenuMotion.EXPRESSIVE : MenuMotion.BALANCED;
        // A freshly entered world/viewport starts in a complete readable state.
        if (!initialized) {
            initialized = true;
            width.step(desired.width, 0, false, false, preset);
            height.step(desired.height, 0, false, false, preset);
            radius.step(desired.radius, 0, false, false, preset);
            opacity.step(desired.opacity, 0, false, false, preset);
            contentAlpha = content.step(1, 0, false, false, preset);
            return desired;
        }
        // Ignore impossible and backward clock samples; focus loss cannot advance by minutes.
        float dt = Float.isFinite(seconds) ? Math.max(0, Math.min(.05f, seconds)) : 0;
        // Reduced Motion is an immediately readable static presentation.
        boolean animate = enabled && !reduced;
        float w = width.step(desired.width, dt, animate, false, preset);
        float h = height.step(desired.height, dt, animate, false, preset);
        float r = radius.step(desired.radius, dt, animate, false, preset);
        float a = opacity.step(desired.opacity, dt, animate, false, preset);
        contentAlpha = content.step(1, dt, animate, false, preset);
        return desired.withGeometry(w, h, r, a);
    }

    /** Content swaps do not reset the shape oscillator's position or velocity. */
    public void contentChanged() {
        if (initialized) {
            content.step(0, 0, false, false, MenuMotion.BALANCED);
            contentAlpha = 0;
        }
    }

    public float contentAlpha() { return Math.max(0, Math.min(1, contentAlpha)); }

    public void reset() {
        initialized = false;
        width.reset(); height.reset(); radius.reset(); opacity.reset(); content.reset();
        contentAlpha = 1;
    }
}
