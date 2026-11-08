# WAVE06-VIS03C – Neutron 2.0 ClickGUI and TargetHUD, first playable slice

**Owner VIS, GitHub-only.** 08 November 2026. **Draft until native gates are verified.**

## Basis and source provenance

- Historical reviewed main `4fe763cc1a92e5d1c9a042b5d828649f62bc4cf6`; `start_sha` / `effective_base_sha` **`869d1b111f2e1f5e22b57cee4099f2bf7ccd9ec3`**, after TECH #111 was independently merged. The intervening five TECH-only paths were verified disjoint.
- VIS PRs #104 SettingsMenu, #107 Panel and #109 Dropdown are already on main. Duplicate #110 is closed, unmerged. No previous VIS work reimplemented.
- Read AGENTS.md, docs/DEVELOPMENT-WORKFLOW.md, VIS01/02/03A/B1/B2 contracts, ClickGui/ClickModule/DropdownGui, ClickGUI module, TargetHUD, HudSpring/MenuSpring/GuiTransitions, ThemeMode/ThemeCatalog, ComboValue/ConfigManager and existing real input/native-opt-in tests.
- **User's Approach 2 ZIP:** `Neutron_Approach_2_Alle_Designs(2).zip` contains 4 full PNG concept boards, 20 cropped component PNGs and `README_DE.txt`. PNGs are **non-transparent screenshot mockups**, no Java/Figma source, reusable images or validated telemetry. No binary/image/font is imported or redistributed.

## Phase C1 – actual product changes

- **Legacy GUI mode:** the ClickGui Module's visible `Mode` dropdown now has exactly `Panel` and `Dropdown`, with original **Panel default index 0**. Saved `Dropdown` by **name** still loads; old `iOS` string falls back to Panel. The old iOS source classes remain dormant for compatibility of existing Java/native regression tests, but there is no user-facing selector or UI route that opens them.
- **Neutron 2.0 GUI:** when and only when `ThemeMode.APPROACH_TWO` is selected, `ClickGUI.resolveScreen()` returns a lazily initialized, cached `NeutronTwoGui`. It reuses actual `ClickGui` category, scroll, search, Tab/keybind, module/checkbox/combo/slider input, drag/resize, hover and click capture, `PanelMotion`, and the existing `ArrayListSurface` render and `GuiTransitions` page transitions. **No parallel GUI logic, input scheduler, GL buffer or second spring core.**
- The 2.0 screen starts at a viewport-capped **640×420**, with wider **148-unit sidebar**, 44-unit header, 32-unit category pitch, translucent deep-violet glass, magenta/orange separator and bottom sheen. The category marker glides on the existing spring; each card has a spring-driven gradient switch and hover/keyboard cues. The layout is based on `01_ClickGUI_Main.png` and the relevant full concept board, not on extracted bitmap sprites. The original Panel (and Dropdown) retain their legacy geometry and default mode.
- **TargetHUD:** appends exactly one eighth choice **`Neutron 2.0`** after the original seven and leaves `Neutron White` as saved default index 1. The persisted White mode gets a **presentation-only 2.0 overlay** when global Theme Mode is Neutron 2.0; switching back restores White without rewriting the Value. Explicit selections of the other six legacy designs remain unchanged, and the eighth can be selected manually.
- TargetHUD 2.0 frame: 240–310 viewport-bounded width, 64-unit intended height, 16-unit corner radius, enlarged 40-unit actual player-skin portrait, magenta contour, dark glass, magenta→orange 5-unit health bar, existing health/absorption spring and portrait bounce, plus **real locally measured and spring-smoothed entity distance**. Never synthesize server data, flags, damage or target health. The existing target-source validation, world epoch, input-safe chat dragging, HUD Grid, disabled/reduced motion and deferred render closure remain intact.

## Scope/compatibility, tests and gates

- Tests must validate `ClickGUI.resolveScreen` and the dedicated screen's **actual subclass/consumer route**, original mode/default, old iOS fallback, ComboValue name persistence through **real ConfigManager**, category click/keyboard, capture/resize lifecycle, seven TargetHUD design indices and preview geometry.
- The existing `TargetHudMotionContractTest` is extended without removing any assertions of original modes; `DropdownGuiRenderCheck` native-opt-in contract is updated for the deliberate two-option Mode selector. Former screenshot PNGs are only design references.
- Java 8 CI on synthetic **PR merge SHA** is required; record exact head/base, suite/test/fail/error/skip, CodeQL/Security/build status before declaring **CODE_GREEN**.
- **NOT_RUN:** native LWJGL/Pbuffer, Minecraft 1.8.9 GUI input/render scales, GUI Shader/GL state/resource leaks, framebuffer caching, native mouse/keyboard focus and FPS p95/p99, multiplayer/world, external shaderpacks. A green Java-8 run alone does not pass these gates.
- No gameplay/combat/Scaffold/Aura/PacketPipeline changes, no TECH ownership overlap, no `pom.xml`, workflows, network, release or extra assets. No local Windows checkout/worktree touched, no force push or direct main modification.
- Rollback: close this Draft PR without merge and discard this isolated branch; legacy users keep Panel/Dropdown and the original HUD designs.

## Next visual parts — independent sequenced PRs

1. **C2 controls and library:** `02_Client_Settings_LiquidGlass.png`, `04_Library_Configs.png`, `14_Dropdown_ClickGUI_klassisch.png`, `05_Dropdown_Movement.png`. Bring consistent editable toggles, sliders, popup motion, focus, config library cards to 2.0, preserving Legacy and current native pointer/scroll bounds.
2. **C3 watermark/list:** `03_ArrayList_Originalstil.png`, `16_ArrayList_Schmal.png`, `06_Dynamic_Island_Zustaende.png`, `19_Watermark_Dynamic_Island.png`. Keep Scaffold/notifications/setback **DATA_PROVIDER_PENDING** until read-only TECH-approved inputs exist; never invent anticheat status.
3. **C4 stat cards:** `07_Session_Info.png`, `17_Session_Info_originalnah.png`, `18_Statistics.png`, `08_CPS_Anzeige.png`, `13_Performance_FPS_Ping.png`, `20_CPS_Performance_Flag_Detector.png`. Animate only genuine observed values, never synthetic telemetry or unverified server flags.
4. **C5 equipment/overlays:** `11_ArmorHUD.png`, `12_Hotbar.png`, `15_Render_ESP_Nametags_Tracers.png`. Reuse existing HUD/module render paths, avoid game/network writes, verify GL state and FPS.
5. **C6 refined TargetHUD:** `10_TargetHUD.png` skin clipping, animated level/absorption/distance numerals, optional compact variant, native comparison at 640×360, 854×480 and 1280×720.

**No later phase is claimed implemented by this PR.** Each receives an independent task branch, test scope, Draft PR and native-gate receipt.
