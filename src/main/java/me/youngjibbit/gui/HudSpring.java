package me.youngjibbit.gui;

/**
 * Per-widget scalar motion backed by the existing MenuSpring oscillator.
 * No GL operations, global animation clock, queue, settings mutations or allocations per step.
 */
public final class HudSpring {
    private final MenuSpring spring = new MenuSpring();
    private boolean initialized;
    private float value;

    public float snap(float target) {
        target = Float.isFinite(target) ? target : 0;
        value = spring.step(target, 0, false, false, MenuMotion.BALANCED);
        initialized = true;
        return value;
    }

    public float step(float target, float seconds, boolean animations, boolean reduced, String intensity) {
        target = Float.isFinite(target) ? target : 0;
        if (!initialized) return snap(target);
        float dt = Float.isFinite(seconds) ? Math.max(0, Math.min(.05f, seconds)) : 0;
        MenuMotion preset = "Calm".equals(intensity) ? MenuMotion.CALM
                : "Expressive".equals(intensity) ? MenuMotion.EXPRESSIVE : MenuMotion.BALANCED;
        // Reduced motion is an immediate, readable change for HUD values.
        value = spring.step(target, dt, animations && !reduced, false, preset);
        return value;
    }

    public float value() { return value; }
    public void reset() { spring.reset(); value = 0; initialized = false; }
}
