# Neutron 2.0 – Liquid-Glass UI Implementation (2026-11-11)

> **Rescue annotation — 2026-11-11:** This document preserves the interrupted
> implementation's design notes and planned verification. Its descriptions are
> not evidence that native or full-game acceptance passed. See the
> [rescue coverage and acceptance record](NEUTRON-2-RESCUE-2026-11-11.md)
> for the ten requested UI areas, six animations, subsequent repairs, source
> provenance, external credits and the updated integration decision. The latest
> user instruction requests merge after exact final-head CI, native image
> review and repository requirements pass. Unperformed full-game/hardware
> measurements remain disclosed limitations.

## 1. Goal

Unify the eleven screenshot-defined surfaces into one Neutron 2.0 language:
translucent glass, soft scene blur, a restrained magenta → orange accent
(`PinkLemonade` `#FF00EA` → `#FF6200` from the active theme), hairline borders,
an inner top highlight, one stroke-icon family, and shared spring motion.

The repository still renders the **legacy** path by default
(`ThemeMode.selected` is `LEGACY` outside tests). All changes therefore land in
the legacy paths first and reuse the same tokens in the modern paths, so the
two presentations cannot drift apart again.

## 2. Scope

| # | Area | Primary files |
|---|------|---------------|
| 1 | Client Settings | `gui/clientsettings/SettingsMenu.java`, `SettingsMenuMotion.java` |
| 2 | Panel ClickGUI | `clickgui/ClickGui.java` |
| 3 | Expanded Aura settings | `clickgui/component/impl/ClickModule.java`, `ClickSubcategory`, `ClickSlider`, `ClickCombo`, `ClickCheckBox` |
| 4 | Dropdown ClickGUI | `clickgui/DropdownGui.java`, `DropdownModule.java` |
| 5 | Nametags | `module/impl/render/Nametags.java` |
| 6 | Target Preview / TargetHUD | `module/impl/render/TargetHUD.java` |
| 7 | Session Info | `module/impl/render/SessionInfo.java` |
| 8 | Keystrokes | `module/impl/render/Keystrokes.java` |
| 9 | ArmorHUD (Modern + Basic) | `module/impl/render/ArmorHUD.java` |
| 10 | Corrections / Diagnostics | `module/impl/render/Diagnostics.java`, `DiagnosticsPlots.java` |
| 11 | Shared foundation | `gui/LiquidGlass.java` (new), `NeutronVisualTokens`, `LegacyUiStyle`, `MenuSurface` |

Out of scope and untouched: ArrayList, Watermark, Config Library layout,
Hotbar, ESP, combat, movement and anticheat behaviour.

## 3. Shared recipe

`gui/LiquidGlass` is a pure drawing helper (no GL state, shader, texture or
settings write):

1. tinted translucent fill (`theme.getBackground()` interpolated toward a
   neutral night blue),
2. hover/selection wash in the theme accent,
3. 1 px inner top highlight that fades at both ends,
4. hairline border, accent border when selected or focused,
5. a 2 px vertical gradient rail for selected navigation items,
6. radius derived from `ClientSettings.roundness` (5 → token value, 0 → −3,
   18 → +7.8, always clamped to half the surface).

`MenuSurface.card` and `LegacyUiStyle.card` delegate to this recipe, so every
settings row, module card, dropdown row, library item and theme editor row
shares one material. No new framebuffer or shader pass is introduced; the
existing `ArrayListSurface` / `UiEffects` scene remains the only glass owner.

## 4. Per-area changes

### 4.1 Client Settings
- Frame fill/outline slightly lighter; stronger blur radius; legacy header gets
  the same gradient entry line as the modern path.
- Sidebar becomes a glass inset; selected section gets the gradient rail.
- Rows, combo fields and the preview strip use the shared card/plate recipe.
- Switch and slider keep their existing interaction rectangles; only track,
  thumb and highlight colours change.
- Popup uses a translucent glass fill (was near-opaque), hairline border and the
  existing cursor spring.

### 4.2 Panel ClickGUI
- Legacy frame inner fills become translucent; the flat `(18,24,34)` sidebar
  and `(13,19,28)` content backing become glass insets with a top highlight.
- Selected category uses the gradient rail instead of a flat accent block.
- Search field gains the magnifier glyph and a focus ring.
- Module cards (`ClickModule`) reuse `MenuSurface`, so headers and expanded
  settings inherit the recipe automatically; bind chips become glass.

### 4.3 Expanded Aura settings
- Subcategory headers get a glass rail, count pill and animated disclosure.
- Slider tracks, combo fields and checkboxes are retuned to glass control
  tokens; values and hit boxes are unchanged.
- Disclosure still uses the spring + clip that already existed; reduced motion
  snaps to the end state.

### 4.4 Dropdown ClickGUI
- Columns keep their gradient header, but the body becomes glass; the toolbar
  and its fields use the shared plate.
- Row hover/active states use the card wash; the enabled dot remains the
  non-colour cue.

### 4.5 HUD surfaces
- Nametags (Static): translucent glass plate + hairline + health bar with a
  smoothed width per player (`HudSpring`, cleaned on world change).
- TargetHUD (Modern): plate + inner highlight + gradient border; heart, range
  and HP bar keep their CompactHudVisuals geometry.
- Session Info (Modern): inner highlight, hairline, accent rule; the clock ring
  and counters are unchanged.
- Keystrokes (Modern): plates become glass with a top highlight and a pressed
  gradient; key rectangles are unchanged.
- ArmorHUD (Modern + Basic): glass plate, per-slot hairline, existing durability
  colours and item rendering unchanged.
- Diagnostics: modern panels get inner highlight + hairline; graphs, meters and
  data flow are untouched.

## 5. Motion and reduced motion

| Sequence | Spring | Reduced motion |
|----------|--------|----------------|
| Settings section transition | `SettingsMenuMotion.category` + `UiSpring` | snap |
| Settings slider drag | `HudSpring` follow | instant value |
| Dropdown expand | `ClickModule` reveal / `DropdownMotion` | snap |
| Category toggle | `ClickModule` toggle spring | snap |
| Nametag health change | per-player `HudSpring` | instant width |
| Corrections data update | `HudSpring` graph tail | instant tail |

Every path already consults `ClientSettings.animations` and
`ClientSettings.reducedMotion`; new motion uses the same helpers.

## 6. Verification

- `mvn -q test` with Temurin 8u482 / Maven 3.9.15.
- Headless unit tests for radius clamping, token contrast bounds, palette
  endpoints and the untouched interaction helpers.
- Native Pbuffer render checks (`*RenderCheck`) for Settings, PanelGUI,
  Dropdown and the compact HUDs where a GL context is available.
- Frame-time comparison: same world, same GUI screen, baseline commit vs
  candidate, p50/p95/p99 of 600 frames; the change adds only rounded quads to
  existing passes, so the budget is “no measurable regression”.
- Manual matrix: both ClickGUI presentations × default/`Surface Opacity` 0.5/1.0,
  reduced motion on/off, GUI scales 1–4.

## 7. Risks

- Lower card alpha can reduce contrast over bright scenes; mitigated by the
  neutral tint, hairline and top highlight, and by keeping parent frames
  governed by the existing material/opacity settings.
- Shared `MenuSurface` also affects library/theme-editor rows (same family,
  intended consistency).
- Per-entity nametag springs add one map lookup per rendered player; bounded by
  the existing player loop and cleaned on world change.
