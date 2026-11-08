# WAVE06-VIS-BROWSER02 – TargetHUD editor/motion + initial Neutron 2.0 theme

## Basis / Ownership
- GitHub-only task; no local Windows checkout, worktree, client JAR, GL launch, tag, merge, direct main push or release.
- `start_sha` / `effective_base_sha`: `aeabb4e9676c30d4b91b080df41b85aa101248b2` (PR #98 merged independently during preflight; current main then reviewed). Previous PR #98 code is inherited as a base, never duplicated.
- Checked current `AGENTS.md`, `docs/DEVELOPMENT-WORKFLOW.md`, relevant HUD/Drag/Motion/Theme classes and available tests.
- The user Approach-2 ZIP holds **PNG concept images**, 4 boards and 20 crops, no executable Minecraft/UI assets or reusable source. The TargetHUD concept uses a magenta/orange border, a head badge, a health strip and translucent dark material. No asset or external font has been redistributed.
- This PR touches no gameplay/network/packet code, no world hooks, shader systems, CI workflows, launcher, POM or releases.

## Completed implementation scope
1. `TargetHUD`: append `Position X/Y` settings **after existing values** (default zeros = legacy auto-anchor); capture only chat rising-edge clicks through the existing `HudDrag` global owner; use `HUD.snap` grid size, `HUD.bounded`, chat preview, click offset, exact saved values, and shared HUD reset. The decorative spring never changes the actual pointer capture rectangle.
2. Reuse `MenuSpring` through small per-instance `HudSpring`/`HudReveal`: spring show/hide, combined vertical slide / bounced scale / opacity, mid-flight reversal, bounded dt, no per-frame uploads. `ClientSettings.animations/reducedMotion/motionIntensity` are honored. World change and HUD disable clear state; deferred compositor paint checks current scene epoch.
3. TargetHUD health and absorption are finite-sanitized, with animated presentation values/percentages and no changes to actual player health. New targets snap to their own initial health; head rendering has a bounded switch pulse. Dead/lost targets fade out from a frozen view and clear; synthetic editor-only preview cannot leak from chat into gameplay. All seven design names, default, and prior health model/source are retained.
4. `ArmorHUD`: shared spring for visual durability bars and values per slot; item snapshots remain copies, with reset on world changes/disabling. Same item/slot semantics and client theme.
5. `Diagnostics`: smooth **only the latest** ping/frame interval graph point and number. The underlying raw snapshot arrays, missing values, correction events, and measurement semantics are unchanged. No server anticheat verification claims; no synthetic telemetry.
6. `ThemeModule`: appended `Theme Mode` selection `Neutron Legacy` (default) / `Neutron 2.0`. `ThemeMode` is an isolated global runtime presentation selector; `Theme.getDefaultTheme()` returns the approach-2 magenta→orange dark palette only in the new mode. The original `ThemeCatalog` selection, its 20 presets, existing theme library IDs and stored defaults remain unchanged. No automatic migration or rewriting of user colors.
7. Reuse the existing `ArrayListSurface`, `UiEffects`, existing shader/resource handling and text atlas. No new native shaders, fonts, textures, render scheduler or buffer pools.

## Test plan and open gates
- `W06-02-M01` `HudSpringAndRevealTest`: exact MenuSpring parity and preserved velocity on reversal, invalid dt, expressive bounce, Reduced Motion/Animations Off.
- `W06-02-E01` `HudPlacementTest`: 640×360 / 854×480 / 1280×720, grid 0/8 and boundaries, tiny GUI, nonfinite coordinates.
- `W06-02-T01` `TargetHudMotionContractTest`: 7 original designs, default/index, Gradient Speed, appended/resettable drag values.
- `W06-02-H01` `ThemeModeApproachTwoTest`: legacy 20 presets intact, palette opt-in/out, ThemeCatalog selected reference unchanged.
- `W06-02-G01` `DiagnosticsGraphMotionTest`: raw samples stable, missing ping preserved, only latest finite endpoint is eased.
- Java 8 regression: verify **actual PR merge-head run**, detailed suite/test/error/failure/skip counts and logs before declaring success. Older PR #98 CI success does **not** certify these changes.
- `NOT_RUN`: native Pbuffer/LWJGL, actual Minecraft GUI/chat mouse, visual screenshots, world/gameplay/server, performance/FPS profiling, controller and shaderpack compatibility.

## Remaining deliberate follow-up work
- **Global UI toggle animations across all ClickGUIs**: existing switches/checkboxes have distinct gesture and input contracts. This PR establishes reusable spring/reveal math but does not claim wiring every control; test pointer capture and keyboard/focus before mass rollout.
- **Other Render modules**: per-quantity type-safe smoothers for SessionInfo, all custom graph families, ArrayList labels, Armor item swaps, potions and diagnostics beyond the latest frame/ping samples; avoid smoothing factual event counts or masking missing telemetry. Do so by independent, small PRs with single-owner state and bounded buffers.
- **Theme 2.0 full styling**: live interactive Theme dropdown transitions, material look for *every* UI, true glass lighting, coordinated corner radii, font/spacing/clickGUI redesign, switch bounce, screenshot-based native verification. The new Theme Mode is an **initial palette layer** only; defaults stay Legacy.
- TargetHUD Native gate: chat editor with real world/no target, Scala GUI scaling, overlapping HUD drag ownership, pause/Alt-Tab, Target HUD seven modes, skin rendering, health=NaN/0, target death/switch/world changes, shaderpack and GL cache restoration.
