# WAVE07 VIS-C2 — Actual GUI composition revision

**Source main:** `1338d0a692d00ee65cba017744558aab37bedaba`.
**User references:** `Neutron20_VISUAL_WORKER_DESIGN_REVISION_2026-11-08(1).zip`, `01_IST_HUD.png`, `02_IST_CLICKGUI.png`, `03_ZIEL_HUD_KONZEPT.png`, `04_ZIEL_CLICKGUI_KONZEPT.png`; Approach-2 PNG crops `01_ClickGUI_Main`, `02_Client_Settings_LiquidGlass`, `04_Library_Configs`. The IST images are user-supplied real client screenshots; the ZIEL/Approach crops are AI/drawn mockups, **not game proof**.

## C2 before/after — source-verified change, not native screenshot acceptance

| Observed IST or current source contract | Target reference feature | C2 actual product change |
| --- | --- | --- |
| Nearly transparent pink/orange full-panel fill; moving world brick/sky patterns under white text | Dark neutral translucent glass with independent inner panes and readable labels | `NeutronVisualTokens.FRAME/HEADER/SIDEBAR/PANEL` alpha 232–242, dedicated two-pane mask under controls; outer `ArrayListSurface` retained, per-window glow disabled in 2.0 |
| Existing client GUI 640×420 fixed start, 148 px sidebar, 44 px header and 32 px nav pitch | Centered broad window, balanced header/sidebar/content, generous but bounded spacing | Responsive width to 1040 / height to 640 (bounded by viewport−16), sidebar 23% capped at 190, 56 header and up to 41 category pitch, 48 content subheader |
| Full-width saturated gradient category selection | Small subdued selected row with one precise accent line | Dark active capsule plus 2 px line in selected preset color; spring positioning unchanged |
| 24 px module rows with translucent colored bands/no descriptions | Individual compact dark card with 2-line title/description, slender enabled indication and switch | `ClickModule.forPanel(...,modern)` creates 44 px card, dark fixed contrast, 0.55 px contour, spring switch inside non-interactive visual area, real Module events unchanged |
| Prominent and sometimes messy Glow/Blur outlines | Controlled, mostly neutral outlines and blur | Modern exterior `ArrayListSurface.panel` disables glow and uses neutral backing; no extra shader/FBO per card; hairline alpha 32, palette alpha 175–235 only on selected widgets |
| Header branding and library not separated | Branded header, search/Library distinct, narrow palette cue | Separate header, N brand tile, small 2.0 tag, dark search and Library controls, tiny spring entry accent only |
| No category helper text and visually weak scroll | Strong heading/description/count and visible narrow thumb | Category description from `NeutronVisualTokens.categoryDescription`, lower-secondary text, 2 px theme-colored thumb and footer |
| Settings sliders/toggles floating on world color | Row cards in a separate pane, dark controls, single theme accent | `SettingsMenu` modern-only nav rail and content pane, row cards, subdued toggle/slider, dark scroll/popup; the earlier Spring/input-only Value pipeline is reused |
| Library list/cards share the bright outer surface | Configs and Themes in a consistent shared material with local selection | `ClickLibraryScreen` modern-only 780×480 bounded screen, separate list/details panes, dark theme/config item cards, thin active swatch and dark focus fields; old save/load/theme handlers unchanged |
| ThemeMode forces a fixed magenta-orange palette | All 20 chosen palettes in both Layout modes | `ThemeMode.global` passes through the actual `ThemeCatalog` palette. `PinkLemonade` remains default selected theme (magenta/orange); Purple, Neutron and all other presets now supply their own accents in 2.0. Legacy untouched. |

## Motion & input invariants

- Exactly one scalar oscillator (`MenuSpring` via `HudSpring`) and existing `PanelMotion`, `UiSpring`, `GuiTransitions`. Frame delta clamped by original motion consumers, no new worker, texture, FBO, shader or queue.
- Theme choice no longer overwrites palette. All categories/selection, search, panel drag and resize, module toggles, Combo/Slider/Checkbox, library config/theme actions, Settings editing/scroll/popup and focus are owned by the **same existing event methods**.
- Render callbacks never `Value.setValue`; the only new code performs position/material presentation and text labels.
- Legacy mode retains the old Panel/Dropdown module rows, menu sizing, material/interaction logic. No dependency on dormant iOS GUI added.

## Important before/after LIMITATIONS

- **NO generated AFTER screenshot of Minecraft**: GitHub access can inspect source/CI but cannot launch the user's Windows 1.8.9 game, Pbuffer or GPU. No pixel-match, contrast measurement on the composited actual GL scene, shaderpack compatibility or FPS gain is claimed. This is a source-verified design-system revision.
- `NOT_RUN / RUNTIME_PENDING`: Minecraft native screen at 640×360, 854×480, 1280×720, 1920×1080; GUI Scale 1/2/Auto, mixed German/Russian text, real shader state/caches, transparent-world backgrounds, keyboard/drag/scroll, chat overlays, HUD collisions and p95/p99 FPS. Ground truth must be fresh screenshots (actual game) rather than target mockups.
- Theme matrix golden: Java8 tests run the 20 actual themes through `ThemeMode.global`, validate dark text contrast, alpha, responsive geometry, existing Settings/Library consumers and config/lifecycle tests. Native tests required for cross-theme visual polish.

## Separate phases, not yet completed by this PR

- C3: Watermark/Dynamic Island/ArrayList materials, genuine read-only provider gating.
- C4: SessionInfo/SessionStatistics/Diagnostics/CPS, real data and sparse plots.
- C5: TargetHUD/ArmorHUD/Keystrokes/Hotbar and genuine positioning/collision.
- C6: ESP/Nametags/GlowESP/world 2D overlays and FX, safe GL depth/occlusion. No functional/packet changes.
- C7: actual native screenshot/performance and all 20 palettes across the two designs.
- `docs/WAVE07-RENDER-MODULE-MATRIX.md` enumerates all 17 actual Category.Render modules plus auxiliaries and adjacent Client owners, with old modes and safe migration targets.
