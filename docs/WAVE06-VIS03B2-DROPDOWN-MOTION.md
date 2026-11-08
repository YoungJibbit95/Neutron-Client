# WAVE06 VIS03-B2 — Dropdown ClickGUI motion (separate, stacked)

**Owner:** VIS. **Status:** Draft pending Java 8 and native gates.

## Source/CI dependency receipt

- B1 PR #107 **still Draft/open at B2 creation**, B1 last head `d672d5716c674575f5d9f20ca4992e413d223d7d`, therefore this phase uses a **stacked branch with B1 as PR base**, not a second copy of Panel implementation.
- B1 Java 8 CI #42487336732, checked-out test merge `fa6c0d6ec660d3cccd35d3f9b509baa95093bf12`: **1283 tests, 0 failures, 0 errors, 3 existing skips, BUILD SUCCESS**. B1 CodeQL and Repository Security passed. B1 native/Pbuffer, real Minecraft, GL state/performance still NOT_RUN.
- Source root main at VIS03 preflight `9f40ae0366463a3c93a59f4749bd723607b6bf99`. Later main update `f205314b2593f2362a73e71f32f15b1f1bb85654` adds only `docs/WAVE06-SCAFFOLD-RESEARCH01.md`; no VIS conflict.
- TECH PRs #106 and #108 touch AI/Bridge combat source and tests, **no gameplay/packet/workflow/POM changes in VIS**. AGENTS.md, DEVELOPMENT-WORKFLOW.md, VIS01/02/03A contracts, DropdownGuiRenderCheck native-opt-in contracts and old frozen ClickAnimation tests reviewed.
- Approach-2 images are non-distributable mockups, no new shaders/fonts/assets/texture allocations/GL core.

## Product, strictly dropdown-only

- `DropdownMotion`: one small per-category `HudSpring` wrapper. This uses the already accepted exact `MenuSpring` physics and global `ClientSettings` animation/reduced/intensity policy. It is **not** a new animation engine.
- `DropdownGui` CategoryPanel: opt-in `Neutron 2.0` spring body height/reversible category open/close, while `Neutron Legacy` uses the unchanged old `ClickAnimation` reveal path. The old `reveal` field and timings remain available for native regression tests.
- `ClickModule.updateCompactMotion`: only DropdownGui in 2.0 mode calls this method. Reuses existing per-module `HudSpring` from B1 for compact row reveal/toggle and updates Combo disclosure and measured row heights in one frame. The usual `updateAnimation()` is unchanged for Legacy.
- Compact `ClickCombo`, `ClickCheckBox`, `ClickSlider`: use existing `HudSpring` in **Neutron 2.0 compact mode only**; old timer/toggle field names and Legacy visual paths are retained. The actual mouse/keyboard/Value handlers do not read springs to authorize commands, and dragging keeps an exact cursor-aligned slider thumb. Closing options may paint clipped but are not interactive.
- The existing category overlap ordering, search, keyboard focus, pointer capture, scrolling destinations, x/y, module/value persistence, shader and surface owner remain untouched. Reset/close/reopen aligns compact springs to the current logical state with no stale bounce.

## Acceptance and follow-ups

| Gate | Test | Coverage |
| --- | --- | --- |
| VIS03B2-M01 | `DropdownMotionTest` | 30/60/144/240 FPS, 30 reversals, Reduced Motion, Animation Off, invalid dt, reset |
| VIS03B2-U01 | `DropdownControlMotionTest` | Actual column click/close/reopen, child input denial on close, Combo, checkbox, slider, old field compatibility |
| VIS03B2-C01 | `DropdownMotionConfigTest` | Real ConfigManager roundtrip, 90 changes, callback count, old defaults |
| VIS03B2-L01 | Existing `DropdownGuiRenderCheck` (native opt-in), `Qa02ScreenMotionTest`, `Wave05UiInputTest`, `ThemeModeApproachTwoTest` | Preserve Legacy old clocks/input, no Panel/iOS regressions |
| VIS03B2-N01 | **NOT_RUN** | Native Pbuffer/FBO/OpenGL state, actual Minecraft mouse/keyboard, low FPS, fullscreen/resize, shaderpacks, GPU performance |

Only the **actual PR-B2 merge ref** Java 8 CI (exact SHA, suites/tests/failures/errors/skips) can authorize a Green claim. CodeQL and security are separate. The Release Bundle PR workflow does not authorize a release.

### Next stage (separate branch/PR after CI)
VIS03-B3: iOS ClickGUI. Preserve existing `AnimationEngine`'s Legacy open timing, iOS combo/slider/checkbox input and scale-inverse handling. Opt-in Neutron 2.0 spring presentation via the same `HudSpring`, not a duplicate MenuSpring. Then later VIS03-C (materials) and VIS03-D (read-only Island providers); do not wire gameplay sources without TECH approval.
