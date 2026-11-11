package me.youngjibbit.gui;

import me.youngjibbit.module.impl.client.ClientSettings;
import me.youngjibbit.utils.Theme;
import me.youngjibbit.utils.render.UIUtils;
import me.youngjibbit.utils.render.UiEffects;
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
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);

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
        if (!Float.isFinite(width) || !Float.isFinite(height) || width <= 0 || height <= 0) return 0;
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
        if (!validRectangle(x, y, width, height)) return;
        theme = Theme.resolve(theme);
        float amount = clamp01(hover);
        float radius = radius(base, width, height);
        float opacity = surfaceOpacity();
        if (opacity <= 0) return;
        int fill = selected ? CARD_SELECTED : Math.round(CARD + (CARD_HOVER - CARD) * amount);
        UIUtils.drawExactDecorationRound(x, y, width, height, radius, fill(theme, opacityAlpha(fill, opacity)));
        if (selected) {
            UIUtils.drawExactDecorationRound(x, y, width, height, radius, accent(theme, opacityAlpha(30, opacity)));
        } else if (amount > .02f) {
            UIUtils.drawExactDecorationRound(x, y, width, height, radius,
                    ink(theme, opacityAlpha(16, amount * opacity)));
        }
        highlight(x, y, width, height, radius, opacity);
        border(x, y, width, height, radius, theme, selected, opacity);
        if (selected) rail(x, y, width, height, radius, theme, opacity);
    }

    /** A quieter nested surface: settings groups, popups and HUD plates. */
    public static void inset(float x, float y, float width, float height, float base, Theme theme) {
        if (!validRectangle(x, y, width, height)) return;
        theme = Theme.resolve(theme);
        float radius = radius(base, width, height);
        float opacity = surfaceOpacity();
        if (opacity <= 0) return;
        UIUtils.drawExactDecorationRound(x, y, width, height, radius, fill(theme, opacityAlpha(PANEL, opacity)));
        highlight(x, y, width, height, radius, opacity);
        border(x, y, width, height, radius, theme, false, opacity);
    }

    /** Editable field / chip / key plate. */
    public static void plate(float x, float y, float width, float height, float base,
                             Theme theme, boolean active, float hover) {
        if (!validRectangle(x, y, width, height)) return;
        theme = Theme.resolve(theme);
        float radius = radius(base, width, height);
        float opacity = surfaceOpacity();
        if (opacity <= 0) return;
        UIUtils.drawExactDecorationRound(x, y, width, height, radius, fill(theme, opacityAlpha(CONTROL, opacity)));
        if (active) {
            UIUtils.drawExactDecorationRound(x, y, width, height, radius, accent(theme, opacityAlpha(26, opacity)));
        } else if (hover > .02f) {
            UIUtils.drawExactDecorationRound(x, y, width, height, radius,
                    ink(theme, opacityAlpha(14, clamp01(hover) * opacity)));
        }
        if (width > 4 && height > 2) UIUtils.drawLiteralGradient(x + radius * .7f, y + .8f,
                Math.max(0, width - radius * 1.4f), .8f, .4f,
                new Color(255, 250, 255, opacityAlpha(active ? 52 : 30, opacity)),
                new Color(255, 250, 255, opacityAlpha(4, opacity)));
        border(x, y, width, height, radius, theme, active, opacity);
    }

    /** 1 px inner light that fades toward the right edge. */
    public static void highlight(float x, float y, float width, float height, float radius) {
        highlight(x, y, width, height, radius, 1f);
    }

    /** The caller supplies its reveal/material opacity once; text is not dimmed. */
    public static void highlight(float x, float y, float width, float height, float radius, float opacity) {
        if (!validRectangle(x, y, width, height) || width <= 4 || height <= 2 || clamp01(opacity) <= 0) return;
        if (!Float.isFinite(radius)) radius = 0;
        float inset = Math.max(0, Math.min(radius, width / 2f));
        if (width <= inset * 2) return;
        // Neutral optical light must not become the user's animated accent fill.
        UIUtils.drawLiteralGradient(x + inset, y + 1,
                Math.max(0, width - inset * 2), 1, .5f,
                new Color(255, 250, 255, opacityAlpha(HIGHLIGHT, opacity)),
                new Color(255, 250, 255, opacityAlpha(6, opacity)));
    }

    public static void border(float x, float y, float width, float height, float radius,
                              Theme theme, boolean focused) {
        border(x, y, width, height, radius, theme, focused, 1f);
    }

    public static void border(float x, float y, float width, float height, float radius,
                              Theme theme, boolean focused, float opacity) {
        if (!validRectangle(x, y, width, height) || width < 2 || height < 2 || clamp01(opacity) <= 0) return;
        // A disabled border setting must stay disabled; keyboard focus keeps its own
        // rails and fills, so no information depends on this hairline.
        if (!UiEffects.options().border || UiEffects.options().borderOpacity <= 0) return;
        theme = Theme.resolve(theme);
        float thickness = theme.isCustom() ? Math.min(.65f, Math.max(0, theme.getOutlineWidth() * .55f)) : .65f;
        int alpha = opacityAlpha(focused ? FOCUS : HAIRLINE, clamp01(opacity) * theme.getOutlineStrength());
        if (thickness <= 0 || alpha <= 0) return;
        Color edge = focused ? accent(theme, alpha) : ink(theme, alpha);
        float boundedRadius = Float.isFinite(radius) ? Math.max(0, Math.min(radius, Math.min(width, height) / 2f)) : 0;
        // UIUtils applies borderOpacity; applying it here as well would square the preference.
        UIUtils.drawExactDecorationOutline(x + .5f, y + .5f, Math.max(1, width - 1), Math.max(1, height - 1),
                Math.max(0, boundedRadius - .5f), thickness, TRANSPARENT, edge, focused ? 1 : 0);
    }

    /** Vertical magenta → orange rail for the selected navigation item. */
    public static void rail(float x, float y, float width, float height, float radius,
                            Theme theme, float amount) {
        if (!validRectangle(x, y, width, height) || width < 4 || height <= 10 || clamp01(amount) <= .001f) return;
        if (!Float.isFinite(radius)) radius = 0;
        radius = Math.max(0, Math.min(radius, Math.min(width, height) / 2f));
        float top = y + Math.max(4, radius * .55f);
        float length = Math.max(0, height - Math.max(8, radius * 1.1f));
        UIUtils.drawDecorationGradientVertical(x + 1.5f, top, 2, length, 1,
                accent(theme, Math.round(225 * clamp01(amount))),
                NeutronVisualTokens.secondary(Theme.resolve(theme),
                        Math.round(190 * clamp01(amount))));
    }

    public static void divider(float x, float y, float width, Theme theme) {
        if (!validRectangle(x, y, width, .7f)) return;
        UIUtils.drawLiteralGradient(x, y, width, .7f, .35f,
                ink(theme, 34), new Color(255, 255, 255, 4));
    }

    /** Thin accent underline used below headings and preview strips. */
    public static void accentLine(float x, float y, float width, Theme theme, float amount) {
        if (!validRectangle(x, y, width, 1.4f) || clamp01(amount) <= .001f) return;
        theme = Theme.resolve(theme);
        UIUtils.drawGradientHorizontal(x, y, Math.max(.01f, width * clamp01(amount)), 1.4f, .7f,
                accent(theme, 190), NeutronVisualTokens.secondary(theme, 96));
    }

    private static int clamp(int alpha) { return Math.max(0, Math.min(255, alpha)); }
    static int opacityAlpha(int alpha, float opacity) { return Math.round(clamp(alpha) * clamp01(opacity)); }
    static boolean validRectangle(float x, float y, float width, float height) {
        return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(width) && Float.isFinite(height)
                && width > 0 && height > 0 && Float.isFinite(x + width) && Float.isFinite(y + height);
    }
    private static float surfaceOpacity() {
        UiEffects.SceneContext scene = UiEffects.scene();
        return clamp01(scene == null ? UiEffects.options().surfaceOpacity : scene.policy.opacity);
    }
    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0;
    }
}
