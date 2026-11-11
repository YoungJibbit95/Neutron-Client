package me.youngjibbit.gui;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import java.awt.Color;

/**
 * Neutron 2.0 liquid-glass recipe for local UI surfaces.
 *
 * <p>The enclosing {@link me.youngjibbit.utils.render.ArrayListSurface} scene owns
 * blur, refraction and global material. This helper only adds the readable layers
 * on top: a translucent tinted fill, a fading inner top highlight, a hairline
 * border and an optional accent rail. It allocates no textures, framebuffers or
 * shaders and never writes a Value.</p>
 */
public final class LiquidGlass {
    private LiquidGlass() { }

    /** Card alpha at rest (77 %); the scene supplies the remaining depth. */
    public static final int CARD = 196;
    public static final int CARD_HOVER = 208;
    public static final int CARD_SELECTED = 214;
    public static final int CONTROL = 176;
    public static final int PANEL = 168;
    public static final int HIGHLIGHT = 46;
    public static final int HAIRLINE = 42;
    public static final int FOCUS = 118;

    public static final Color HIGHLIGHT_INK = new Color(255, 248, 255, HIGHLIGHT);

    /** Maps the user's 0–18 roundness onto a token base radius. */
    public static float radius(float base) {
        return radius(base, configuredRoundness());
    }

    /** Pure mapping shared by tests and call sites that already own the setting. */
    public static float radius(float base, float setting) {
        if (!Float.isFinite(base)) base = 0;
        if (!Float.isFinite(setting)) setting = 5f;
        setting = Math.max(0, Math.min(18, setting));
        return Math.max(0, base + (setting - 5f) * .6f);
    }

    public static float radius(float base, float width, float height) {
        return radius(base, configuredRoundness(), width, height);
    }

    /** Pure variant that additionally keeps the radius inside its rectangle. */
    public static float radius(float base, float setting, float width, float height) {
        float limit = Math.max(0, Math.min(width, height) / 2f);
        return Math.max(0, Math.min(limit, radius(base, setting)));
    }

    private static float configuredRoundness() {
        float setting = 5f;
        try {
            ClientSettings settings = ClientSettings.current();
            if (settings != null) setting = settings.roundness.getValue().floatValue();
        } catch (RuntimeException ignored) {
            // Headless tools have no module manager; the historical default stays valid.
        }
        return Float.isFinite(setting) ? setting : 5f;
    }

    /** Neutral night tint that still carries a hint of the selected palette. */
    public static Color fill(Theme theme, int alpha) {
        theme = Theme.resolve(theme);
        int background = theme.getBackground().getRGB() & 0xffffff;
        float mix = theme.isCustom() ? .28f : .16f;
        int rgb = Theme.interpolateRGB(0xff121722, background, mix);
        return new Color((rgb & 0xffffff) | (clamp(alpha) << 24), true);
    }

    public static Color accent(Theme theme, int alpha) {
        return NeutronVisualTokens.accent(Theme.resolve(theme), clamp(alpha));
    }

    public static Color ink(Theme theme, int alpha) {
        return NeutronVisualTokens.alpha(NeutronVisualTokens.ink(Theme.resolve(theme)), clamp(alpha));
    }

    /**
     * One complete card. {@code base} is the historical radius of the call site so
     * unmodified layouts keep their proportions; the user roundness scales it.
     */
    public static void card(float x, float y, float width, float height, float base,
                            Theme theme, boolean selected, float hover) {
        if (!(width > 0) || !(height > 0)) return;
        theme = Theme.resolve(theme);
        float amount = clamp01(hover);
        float radius = radius(base, width, height);
        int fill = selected ? CARD_SELECTED : amount > .55f ? CARD_HOVER : CARD;
        UIUtils.drawDecorationRound(x, y, width, height, radius, fill(theme, fill));
        if (selected) {
            UIUtils.drawDecorationRound(x, y, width, height, radius, accent(theme, 30));
        } else if (amount > .02f) {
            UIUtils.drawDecorationRound(x, y, width, height, radius,
                    ink(theme, Math.round(16 * amount)));
        }
        highlight(x, y, width, height, radius);
        border(x, y, width, height, radius, theme, selected);
        if (selected) rail(x, y, width, height, radius, theme, 1f);
    }

    /** A quieter nested surface: settings groups, popups and HUD plates. */
    public static void inset(float x, float y, float width, float height, float base, Theme theme) {
        if (!(width > 0) || !(height > 0)) return;
        theme = Theme.resolve(theme);
        float radius = radius(base, width, height);
        UIUtils.drawDecorationRound(x, y, width, height, radius, fill(theme, PANEL));
        highlight(x, y, width, height, radius);
        border(x, y, width, height, radius, theme, false);
    }

    /** Editable field / chip / key plate. */
    public static void plate(float x, float y, float width, float height, float base,
                             Theme theme, boolean active, float hover) {
        if (!(width > 0) || !(height > 0)) return;
        theme = Theme.resolve(theme);
        float radius = radius(base, width, height);
        UIUtils.drawDecorationRound(x, y, width, height, radius, fill(theme, CONTROL));
        if (active) {
            UIUtils.drawDecorationRound(x, y, width, height, radius, accent(theme, 26));
        } else if (hover > .02f) {
            UIUtils.drawDecorationRound(x, y, width, height, radius,
                    ink(theme, Math.round(14 * clamp01(hover))));
        }
        UIUtils.drawGradientHorizontal(x + radius * .7f, y + .8f,
                Math.max(0, width - radius * 1.4f), .8f, .4f,
                new Color(255, 250, 255, active ? 52 : 30),
                new Color(255, 250, 255, 4));
        border(x, y, width, height, radius, theme, active);
    }

    /** 1 px inner light that fades toward both horizontal ends. */
    public static void highlight(float x, float y, float width, float height, float radius) {
        if (width <= 4 || height <= 2) return;
        float inset = Math.max(0, Math.min(radius, width / 2f));
        UIUtils.drawGradientHorizontal(x + inset, y + 1,
                Math.max(0, width - inset * 2), 1, .5f,
                new Color(255, 250, 255, HIGHLIGHT),
                new Color(255, 250, 255, 6));
    }

    public static void border(float x, float y, float width, float height, float radius,
                              Theme theme, boolean focused) {
        if (width < 2 || height < 2) return;
        // A disabled border setting must stay disabled; keyboard focus keeps its own
        // rails and fills, so no information depends on this hairline.
        if (!me.youngjibbit.utils.render.UiEffects.options().border) return;
        Color edge = focused ? accent(theme, FOCUS) : ink(Theme.resolve(theme), HAIRLINE);
        UIUtils.drawRoundOutline(x + .5f, y + .5f, Math.max(1, width - 1), Math.max(1, height - 1),
                Math.max(0, radius - .5f), .65f, new Color(0, 0, 0, 1), edge);
    }

    /** Vertical magenta → orange rail for the selected navigation item. */
    public static void rail(float x, float y, float width, float height, float radius,
                            Theme theme, float amount) {
        if (height <= 10 || amount <= .001f) return;
        float top = y + Math.max(4, radius * .55f);
        float length = Math.max(0, height - Math.max(8, radius * 1.1f));
        UIUtils.drawDecorationGradientVertical(x + 1.5f, top, 2, length, 1,
                accent(theme, Math.round(225 * clamp01(amount))),
                NeutronVisualTokens.secondary(Theme.resolve(theme),
                        Math.round(190 * clamp01(amount))));
    }

    public static void divider(float x, float y, float width, Theme theme) {
        if (width <= 0) return;
        UIUtils.drawGradientHorizontal(x, y, width, .7f, .35f,
                ink(theme, 34), new Color(255, 255, 255, 4));
    }

    /** Thin accent underline used below headings and preview strips. */
    public static void accentLine(float x, float y, float width, Theme theme, float amount) {
        if (width <= 0 || amount <= .001f) return;
        theme = Theme.resolve(theme);
        UIUtils.drawLiteralGradient(x, y, Math.max(.01f, width * clamp01(amount)), 1.4f, .7f,
                accent(theme, 190), NeutronVisualTokens.secondary(theme, 96));
    }

    private static int clamp(int alpha) { return Math.max(0, Math.min(255, alpha)); }
    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
}
