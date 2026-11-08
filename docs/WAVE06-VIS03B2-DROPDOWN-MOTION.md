# WAVE06 VIS03-B2 — Dropdown ClickGUI motion (separate, stacked)

**Owner:** VIS. **Status:** Draft; native Minecraft/GL/FPS gates NOT_RUN. Final B2 Java 8 evidence belongs to the last PR head only.

## Source/CI dependency receipt

- At creation B2 was stacked on earlier B1 head `d672d5716c674575f5d9f20ca4992e413d223d7d`. B1 PR #107 later moved to `b2ae434371d76dd06215c635a73b9b06bc88b9d0` and **was merged externally** into main `e374cd86631f78851044a6c069b7d6a2e1f4050e`.
- B2 was **fast-forward merged, without rewrite**, first with the final B1 head and then with the actual main merge. Both B1/main have the same Git tree `d8e41c2137d6b583051aad90574768a6f9f8df60`. The merge preserved all eleven B2 files, inherited existing TECH/docs commits without editing them, and PR #109 was retargeted from the obsolete stacked parent to `main` after #107 merged.
- Duplicate PR #110 used the **same B2 head** but showed B1+B2 against an older main. Documented as a duplicate in its conversation and **closed without merge**. PR #109 is the sole B2 integration path.
- Final B1 Java 8 CI [#42488791108](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42488791108): head `b2ae434371d76dd06215c635a73b9b06bc88b9d0`, checked-out merge `38894c3e6298a3edfa20d0a4694f9628570dad5a`, **1305 tests, 0 failures, 0 errors, 3 skips; BUILD SUCCESS**. CodeQL, Repository Security and release-bundle CI success (not release authorization). B1 native/real Minecraft still NOT_RUN.
- Historical VIS03 root `9f40ae0366463a3c93a59f4749bd723607b6bf99`; **actual B2 integration base** `e374cd86631f78851044a6c069b7d6a2e1f4050e`. The overlay from B1 contained only eight disjoint, already integrated TECH/docs paths; the B2 diff against final B1/main remains exactly eleven VIS paths.
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
| VIS03B2-U01 | `DropdownControlMotionTest` | Real column click/reversal and closure denial, keyboard module toggle, scroll route/captured drag, visible Combo option, checkbox, slider, Legacy field compatibility; tests logical cleanup without opening GL |
| VIS03B2-C01 | `DropdownMotionConfigTest` | Real ConfigManager roundtrip, 90 changes, callback count, old defaults |
| VIS03B2-L01 | Existing `DropdownGuiRenderCheck` (native opt-in), `Qa02ScreenMotionTest`, `Wave05UiInputTest`, `ThemeModeApproachTwoTest` | Preserve Legacy old clocks/input, no Panel/iOS regressions |
| VIS03B2-N01 | **NOT_RUN** | Native Pbuffer/FBO/OpenGL state, actual Minecraft mouse/keyboard, low FPS, fullscreen/resize, shaderpacks, GPU performance |

**B2 CI investigation:** old head `049302eb4b8b1f688f22cf2b1c6e33e29e8b6aea` had 1295 tests / 0 failures / **2 errors** / 3 skips: `DropdownControlMotionTest` called `DropdownGui.onGuiClosed()`, which calls `TextEffects.close → RenderState → GL11.glGetInteger → GLContext`. Without `lwjgl64` in Java-8 headless CI, the first test raised `UnsatisfiedLinkError` and the next a `NoClassDefFoundError`. This is **native GPU resource teardown**, not category/scroll/spring initialization. **Repair:** headless input tests use the same real `DropdownGui.cancelInteraction()` method called first by `onGuiClosed()`; that package-local method handles focus, pointer capture, search, binding and control animations. Native `TextEffects`/surface close remains unchanged and explicitly awaits separate Pbuffer/GL checks. No tests were ignored, removed or skipped. Added real scroll, keyboard focus and visible option click tests.
Only the **actual PR-B2 merge ref** Java 8 CI (exact SHA, suites/tests/failures/errors/skips) can authorize a Green claim. CodeQL and security are separate. The Release Bundle PR workflow does not authorize a release.

### Next stage (separate branch/PR after CI)
VIS03-B3: iOS ClickGUI. Preserve existing `AnimationEngine`'s Legacy open timing, iOS combo/slider/checkbox input and scale-inverse handling. Opt-in Neutron 2.0 spring presentation via the same `HudSpring`, not a duplicate MenuSpring. Then later VIS03-C (materials) and VIS03-D (read-only Island providers); do not wire gameplay sources without TECH approval.
