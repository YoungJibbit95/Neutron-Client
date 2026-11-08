package me.youngjibbit.gui.clientsettings;

import me.youngjibbit.gui.HudReveal;
import me.youngjibbit.gui.HudSpring;
import me.youngjibbit.module.impl.client.ClientSettings;

/**
 * Screen-local composition of the existing MenuSpring-based HUD adapters.
 * No input ownership, GL calls, configuration writes or independent timeline.
 */
final class SettingsMenuMotion {
    private final HudSpring category = new HudSpring();
    private final HudSpring disclosure = new HudSpring();
    private final HudSpring popupCursor = new HudSpring();
    private final HudReveal screen = new HudReveal();
    private boolean popupOpen;

    void reset() {
        category.reset();
        disclosure.snap(0);
        popupCursor.reset();
        screen.reset();
        popupOpen = false;
    }

    /** Logical category changes immediately; this is only the visual marker. */
    float category(float y, float dt, ClientSettings settings) {
        return category.step(y, dt, enabled(settings), reduced(settings), intensity(settings));
    }

    void resetPopup() {
        disclosure.snap(0);
        popupCursor.reset();
        popupOpen = false;
    }

    void popup(boolean open) { popupOpen = open; }
    boolean popupOpen() { return popupOpen; }

    float popup(float dt, ClientSettings settings) {
        return clamp(disclosure.step(popupOpen ? 1 : 0, dt,
                enabled(settings), reduced(settings), intensity(settings)));
    }

    void resetCursor(float y) { popupCursor.snap(y); }
    float cursor(float y, float dt, ClientSettings settings) {
        return popupCursor.step(y, dt, enabled(settings), reduced(settings), intensity(settings));
    }

    /** Screen presentation; GuiTransitions still owns cross-screen closing/FB capture. */
    HudReveal.Frame screen(boolean open, float dt, ClientSettings settings) {
        return screen.step(open, dt, enabled(settings), reduced(settings), intensity(settings));
    }

    private static boolean enabled(ClientSettings c) {
        return c == null || c.animations.getValue();
    }
    private static boolean reduced(ClientSettings c) {
        return c != null && c.reducedMotion.getValue();
    }
    private static String intensity(ClientSettings c) {
        return c == null ? "Balanced" : c.motionIntensity.getValue();
    }
    private static float clamp(float value) {
        return Math.max(0, Math.min(1, Float.isFinite(value) ? value : 0));
    }
}
