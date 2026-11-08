package me.youngjibbit.gui;

/**
 * Shared interruptible slide/bounce/fade geometry. Logical HUD capture is independent
 * of this presentation state; only the caller controls input and last-frame lifetimes.
 */
public final class HudReveal {
    private final HudSpring spring = new HudSpring();
    private boolean entered;
    public static final class Frame {
        public final float opacity, scale, slideY, response;
        Frame(float response) {
            this.response = response;
            opacity = clamp(response, 0, 1);
            scale = clamp(.87f + .13f * response, .82f, 1.085f);
            slideY = clamp((1 - response) * 13, -5, 15);
        }
        public boolean drawn() { return opacity > .003f; }
    }
    public Frame step(boolean visible, float seconds, boolean animations, boolean reduced, String intensity) {
        if (!entered) { spring.snap(0); entered = true; }
        float progress = spring.step(visible ? 1 : 0, seconds, animations, reduced, intensity);
        if (!visible && progress <= .002f) { spring.snap(0); progress = 0; }
        return new Frame(progress);
    }
    public void reset() { entered = false; spring.reset(); }
    private static float clamp(float x,float lo,float hi) { return Math.max(lo,Math.min(hi,x)); }
}
