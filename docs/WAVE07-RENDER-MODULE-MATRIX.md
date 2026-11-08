# WAVE07 — Vollständiges Render-/HUD-Inventar und Migrationsmatrix

Source: `src/main/java/me/youngjibbit/module/impl/render/*.java`, `render/esp/ESPRenderUtil.java`, adjacent `impl/client/*.java`, inspected on main `1338d0a692d00ee65cba017744558aab37bedaba` (2026-11-08). **Inventory, not a claim that C3–C6 are already implemented.** Exact support is assigned per planned PR.

## Design-vs-palette contract

- **Theme** = selected existing `ThemeCatalog` preset (all 20 preserved) or a user-created custom theme; primary/secondary accents stay the theme's colors even when `ThemeMode` = `Neutron 2.0`.
- **Design** = explicit per-widget `Legacy` or `Neutron 2.0`, eventually exactly two visible **cosmetic** options where the widget has two meaningful visual layouts. No extra fake Design control for functional-only modules.
- **Priority when migration is complete:** explicit local `Design` selection controls the widget layout; otherwise the global ThemeMode controls it. Existing saved `Mode` values are mapped by name before any index mapping. No automatic config write-back during load/draw. `Legacy` is the fallback for unknown historic names. **This priority is the migration target; do not claim it is fully implemented in C2.**
- Old index loading must be verified against `ConfigManager` before replacing a `ComboValue`. Existing `ComboValue.setValue` currently coerces an unknown string to option 0. Never blindly replace a historic enum with two options without an explicit translator and legacy tests.
- A functional `None` / OFF / orientation / visibility option stays independently available. Module toggled status and world/packet semantics do not become cosmetic controls.
- Current C2 only changes palette independence and the modern presentation of ClickGUI, Client Settings and Config Library. Rows labelled `C3/C4/C5/C6 PENDING` have no new consumer in this phase.

## All 17 `Category.Render` modules

| Module and Java owner | Current named modes / functional settings | Future design / safe migration | Consumer phase |
|---|---|---|---|
| Animations — `render/Animations.java` | `Mode`: Stella, Middle, 1.7, Exhi 1–5, Shred, Smooth, Sigma, Custom etc.; Speed/X/Y/Size/Smoother/Small Swing | **Functional viewmodel kinematics**, KEEP entire `Mode` enum; no cosmetic switch without separate, proven UI owner | C6 cosmetic only, animation choice unchanged |
| ArmorHUD — `render/ArmorHUD.java` | `Layout`: Vertical/Horizontal; `Durability`: Percent/Value/Both/None; bars, held item, empty slots, scale, X/Y, Blur/Glow | Keep **all functional** layout/durability/slot options, append independent `Design=Legacy/Neutron 2.0` with safe default | C5 PENDING |
| Breadcrumbs — `render/Breadcrumbs.java` | Duration/Width | Cosmetics inherit global by default; only add local Design if truly useful; movement trail data unchanged | C6 FX PENDING |
| ChinaHat — `render/ChinaHat.java` | Radius/Opacity | Cosmetic cone silhouette may inherit global; third-person and depth rules unchanged | C6 FX PENDING |
| Crosshair — `render/Crosshair.java` | Length/Gap/Thickness; Dynamic/Center Dot | Functional crosshair switches remain, optional two cosmetic designs, no changing aiming input | C6 FX PENDING |
| Diagnostics — `render/Diagnostics.java` | `Display Mode`: Text/Simple; Performance/Rotation/Network/Flag widgets and all telemetry flags | Map Text/Simple to **Legacy** only when separate display semantics preserved; add modern cards; preserve exact correction/unknown data | C4 PENDING |
| ESP — `render/ESP.java` | `Mode`: Static/Morden/None; `Theme` Combo; Health Bar/Blur | **Review world projection first**: keep `None` as functional off and preserve entity filters/depth/team colors; migrate only proven cosmetic 2D panel variants | C6 world PENDING |
| FullBright — `render/FullBright.java` | No display modes | **Functional only**, no invented Design Value | C6 settings appearance only |
| GlowESP — `render/GlowESP.java` | `Color Mode`: Sync/Random/Custom; Kawase Glow/Players/Animals/Mobs/Chests/Radius/Iterations/Offset/Intensity | Keep all functional entity filters/color modes and occlusion semantics; optional two cosmetic glow presets after native check | C6 world PENDING |
| JumpCircle — `render/JumpCircle.java` | Radius/Duration | Render FX may inherit global or use two cosmetic styles; no fake movement/jump values | C6 FX PENDING |
| Keystrokes — `render/Keystrokes.java` | X/Y, Mouse Buttons | Independent `Design=Legacy/Neutron 2.0` only, retain live actual key capture and X/Y | C5 PENDING |
| Nametags — `render/Nametags.java` | No listed `ComboValue Mode`; team semantics | Cosmetic surface two styles; preserve projected position, visibility, teams, depth | C6 world PENDING |
| NoFovChange — `render/NoFovChange.java` | No visual modes | **Functional only**, no new Design Value | C6 settings appearance only |
| NoHurtCam — `render/NoHurtCam.java` | No visual modes | **Functional only**, no new Design Value | C6 settings appearance only |
| Session Info — `render/SessionInfo.java` | `Mode`: Basic, Outline, Tenacity, Rue (Rise); default Outline index 1; X/Y/Blur/Bloom/Gradient Speed | All four previous names → `Legacy`; new `Neutron 2.0` modern statistics card. Preserve saved position and optional panel material switches | C4 PENDING |
| TargetHUD — `render/TargetHUD.java` | `Mode`: Neutron Default, Neutron White, Neutron Outline, Rise, Tenacity, Basic, Test, Neutron 2.0; **default White index 1** | Seven previous names → `Legacy` (historical White baseline); eighth → `Neutron 2.0`. Preserve exact health/absorption target, cursor drag/grid and preview | C5 PENDING |
| VB (ViewBobbing) — `render/ViewBobbing.java` | No design modes | **Functional only**, no new Design Value | C6 settings appearance only |

## Render package supporting classes (not user-toggleable modules)

| Java owner | Responsibility | Plan |
|---|---|---|
| `render/DiagnosticsEditor.java` | Diagnostics widget editor | C4 styling, no independent Design Value |
| `render/DiagnosticsPlots.java` | Pure telemetry plot presentation | C4 visual change, raw arrays immutable |
| `render/SessionInfoLifecycle.java` | Session boundaries and reset | C4 no mutation of event meanings |
| `render/SessionStatistics.java` | Source-backed session data | C4 no synthetic kills/deaths |
| `render/esp/ESPRenderUtil.java` | ESP world drawing helper | C6 GL/depth validation, not another module |

## Adjacent `Category.Client` modules and UI owners

| Owner | Current modes / functions | Future design / mapping | Phase |
|---|---|---|---|
| `client/ArrayListHUDModule.java` | Mode Glass/Compact/BasicV2/Basic/Rounded/None; fonts, categories, padding, X/Y | Five historic visual modes→Legacy; `None` stays functional visibility/off; modern slim list, existing category filters retained | C3 PENDING |
| `client/WatermarkHUDModule.java` | Mode Simple/Glass/Outline/Rise/iOS/Logo 1/Logo 2/Wordmark/Status/Dynamic Island; name/version/FPS, status, clock | Historical visual modes→Legacy; `Dynamic Island` stays a **functional content state/provider** selectable independently from 2.0 styling; preserve all data switches | C3 PENDING |
| `client/HUD.java` | HUD visible, edit in chat, bounds, grid snap | Editor behavior only, no new Design; reset grid/drag preserved | C5 layout contract |
| `client/ClientSettings.java` | Surface Style Standard/Clear/Gradient/Blur/Liquid Glass, blur, glow, animation, font, notifications | **Functional UI policy**, keep all settings and defaults; modern Settings consumer in C2 | **C2 PRODUCT** |
| `client/ThemeModule.java` | ThemeSelectionValue; Theme Mode Neutron Legacy / Neutron 2.0 | 20 palette entries unchanged; both designs use selection | **C2 palette independence** |
| `client/CombatDiagnostics.java` | Console attack log, read-only local trace | Functional/telemetry only; no fabricated flag data | C4/C6 read-only |
| `module/impl/render/ClickGUI.java` | Legacy Panel/Dropdown (historic iOS already removed), modern dedicated 2.0 ClickGUI | Two actual GUI routes, legacy defaults preserved | **C2 PRODUCT** |

## Live product routes and acceptance (C2)

- `module/impl/render/ClickGUI.resolveScreen` → `clickgui/NeutronTwoGui` → existing `ClickGui.drawScreen`, `ClickModule.drawPanelHeader`, `ClickCombo/ClickSlider/ClickCheckBox` and `PanelMotion`. New `NeutronVisualTokens` controls only 2.0 rendering/initial sizing; no settings mutation in paint.
- `clientsettings/SettingsMenu.drawScreen` consumes the same tokens for 2.0 nav, labels, rows, switches, sliders and popup; existing input methods own writes.
- `clickgui/ClickLibraryScreen.drawScreen` consumes the tokens for 2.0 glass, tabs, selected config/theme cards and controls; `ConfigLibrary` and `ThemeCatalog` keep all I/O authority.
- **Legacy:** unchanged visual branch in these consumers. `ThemeMode.global()` now keeps `ThemeCatalog.current()` palette rather than injecting one fixed magenta/orange scheme, verified for all 20 presets.

## Required next acceptance gates

C2 (this PR) tests Java 8 type-safe tokens/geometry/contrast, 20 palette independence, actual GUI/Settings/Library layouts and all prior Value/Config/Focus/Lifecycle regressions. **Native Minecraft screenshot pixel comparison at 640×360, 854×480, 1280×720, 1920×1080 remains RUNTIME_PENDING** without a real game window. Future C3–C6 must each amend this matrix with the **actual PR** and verified design consumer, not just planning labels.
