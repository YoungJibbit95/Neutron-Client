# WAVE07 VIS-C3 – Compact Dynamic Island, Watermark and ArrayList

**Ownership:** VIS-only; **Draft** until Java8/CI and native gates are separately reviewed.
**Source base:** `1338d0a692d00ee65cba017744558aab37bedaba`, GitHub-only. No Windows checkout or worktree.

## Source and dependency/conflict review
- The immediately preceding **C2** has its own active [Draft PR #115](https://github.com/YoungJibbit95/Neutron-Client/pull/115), which owns `NeutronVisualTokens`, `ThemeMode`, `ClickGui`, `SettingsMenu`, `ClickLibraryScreen` and tests. The last C2 CI #42502254314 has 1367 tests / 0 failures / **1 test fixture error** / 3 skips (`Wave07VisualTokensTest.darkGlassAndTypographyMaintainContrast`: `Theme.custom` rejected an invalid ID). Error diagnosis was commented on PR #115; **do not merge C2 or claim C2 green until fixed and re-tested**. C3 intentionally uses no C2-owned files; its final integration must recheck main and C2's palette authority before release.
- TECH PRs #114 (AI Aura) and #116 (Scaffold/Adaptive Bridge) are open; all ownership boundaries checked. This PR touches no combat, Scaffold, packet or world data.
- Read `AGENTS.md`, `docs/DEVELOPMENT-WORKFLOW.md`, WAVE06 VIS contracts, `WatermarkHUDModule`, `IslandLayout/Presentation/StateMachine`, `IslandMorphMotion` consumer, `ArrayListHUDModule`, `HudSpring`, `ThemeMode`, `ArrayListSurface` and the previous HUD tests.
- User's four WAVE07 PNG references: two *actual current* IST screenshots and two *generated target concepts*. Original Approach-2 crops `03_ArrayList_Originalstil`, `16_ArrayList_Schmal`, `06_Dynamic_Island_Zustaende`, `19_Watermark_Dynamic_Island` inspected. These are reference-only PNGs. **No screenshot/gradient bitmap copied into Java assets.**

## Visual composition – reference → production
| Element | IST composition problem | C3 implemented visual change | Product invocation |
| --- | --- | --- | --- |
| Idle Watermark/Island | bright translucent magenta/orange pill; world bleeds through; wide halo | compact 234×34 logical capsule, graphite ink plate, controlled single-theme 1-pixel accent, readable white and secondary ink; glow disabled in modern, blur from existing one surface | `WatermarkHUDModule.renderWatermark → islandSelected → renderIsland` |
| Target Island | same translucent palette with busy outline | 266×52 layout that only grows for genuinely observed Aura/AimAssist target; health/absorption bars from frozen snapshot | `islandTargetSnapshot → IslandPresentation → paintIsland` |
| Legacy Watermark | nine mode names, default Glass historically | **no config rewrite**; 2.0 global mode upgrades only the historical Glass default to the modern Island presentation. Explicitly selected Simple/Rise/Logo/etc remain legacy; all ten existing Mode names remain loadable | `islandSelected` |
| ArrayList HUD | tinted box with large padding and visible leading `|` characters | narrow stacked neutral graphite rows, smaller dynamic spacing, 2-pixel selected-palette accent per row, high-contrast neutral glyphs, no full-panel glow, no forced font/shader change. Modern labels omit the leading literal separator; real module info still works | `ArrayListHUDModule.renderArrayList → buildRows → deferred labels` |
| Module reveal | decelerate timer potentially ends abruptly | per-module `HudSpring` backed by existing `MenuSpring`, real toggled state, bounded dt, interruptions and global `UI Animations` / `Reduced Motion` settings | `buildRows` |
| Legacy ArrayList | 6 historical modes and "None" functional hide | unchanged old `DecelerateAnimation`, original pad/default/position and local overrides | `buildRows / pad / getLabel` |

The design draws through existing `ArrayListSurface` and `GuiShaderBloom.afterBatch`, no extra framebuffer, shader, worker, font or GL resources. Deferred label geometry and selected theme are frozen in the calling frame. Editor-only dragging, HUD Grid, stored X/Y, bounding and right anchoring unchanged.

## Data honesty and 2-design migration gate
- `SCAFFOLD`, `MODULE_TOAST`, `POSSIBLE_SETBACK` are **DATA_PROVIDER_PENDING**. C3 does **not** wire TECH Scaffold classes, invent block counts or assert anticheat flags. The only live Island inputs remain truthful clock and target snapshots. An illustrative target-concept image is **not** a source of telemetry.
- Historic cosmetic Watermark and ArrayList mode names are preserved. **The requested two-visible-design migration is not yet completed here**: it requires a tested old-name/index translation and a separate UI visibility transition (per WAVE07 migration matrix), without corrupting existing user configuration. Do not claim this staged consumer improvement satisfies every final two-design option.
- The selected ThemeCatalog palette is authoritative for the modern accents **after C2's ThemeMode fix integrates**; C3 does not edit palette choice or invent an extra global Theme catalog.
- SessionInfo/Diagnostics/CPS/Performance/ArmorHUD/Keystrokes/ESP et al. are **not implemented** in C3. They require separate C4, C5 and C6 source/CI gates.

## Tests, evidence and open native gates
- `Wave07IslandVisualTest`: 4 tests, exact Legacy geometry across states/small screens, modern 1×1–Full HD clipping, target/idle size hierarchy, unknown != warning.
- `Wave07CompactHudModeTest`: 5 tests, *real product methods* for mode selection, current legacy name/spacing, functional None and local padding.
- `Wave07CompactHudConfigTest`: 2 tests, actual `ConfigManager` save/reload of legacy Watermark/ArrayList named modes, position, padding, unchanged defaults.
- Existing `WatermarkIslandModeTest`, `IslandLayoutTest`, `IslandStateMachineTest`, `IslandPresentationTest`, `ArrayListModeVisibilityTest` and full regression must remain intact.
- **PENDING**: Java 8 PR synthetic merge commit, suite/test/failure/error/skip total, CodeQL, security, built JAR CI. No CI Green claim before exact log evidence.
- **RUNTIME_PENDING**: Pbuffer/native Minecraft GUI at 640×360, 854×480, 1280×720, Full HD, Chat on/off, HUD collisions, shaderpacks, P95/P99 GPU/CPU FPS, GL state/leak, resource reload, live World switch. No rendered before/after screenshots were generated from Minecraft. Pure geometry viewports are JUnit only.

**No direct main push, release, auto-merge, local F:\\ checkout, worktree or TECH changes.** Staged rollout C4–C6 in separate Draft PRs only after their independent gates.
