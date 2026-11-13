package me.youngjibbit.module.impl.render;

import me.youngjibbit.gui.HudSpring;

/** Cosmetic slot motion. Inventory selection always remains the game's authoritative value. */
final class HotbarMotion {
    private final HudSpring cursor = new HudSpring();
    private final HudSpring[] selection = new HudSpring[9];
    private final CompactHudMotion[] items = new CompactHudMotion[9];
    private final float[] weights = new float[9];
    private float position;

    HotbarMotion() {
        for (int i = 0; i < 9; i++) {
            selection[i] = new HudSpring();
            items[i] = new CompactHudMotion();
        }
    }

    void step(int slot, float seconds, boolean animateSelection, boolean animateItems,
              boolean reduced, String intensity) {
        slot = Math.max(0, Math.min(8, slot));
        position = Math.max(0, Math.min(8, cursor.step(slot, seconds, animateSelection, reduced, intensity)));
        for (int i = 0; i < 9; i++) {
            weights[i] = Math.max(0, Math.min(1,
                    selection[i].step(i == slot ? 1 : 0, seconds, animateItems, reduced, intensity)));
        }
    }

    float position() { return position; }
    float weight(int slot) { return weights[slot]; }

    float itemPulse(int slot, long observation, float seconds, boolean animations,
                    boolean reduced, String intensity) {
        return items[slot].step(observation, seconds, animations, reduced, intensity);
    }

    void reset() {
        cursor.reset();
        position = 0;
        for (int i = 0; i < 9; i++) {
            selection[i].reset();
            items[i].reset();
            weights[i] = 0;
        }
    }
}
