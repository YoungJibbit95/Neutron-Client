# Neutron 2.0 rescue: coverage and acceptance record

Date: 2026-11-11. Recovery PR: [#182](https://github.com/YoungJibbit95/Neutron-Client/pull/182).
Branch: `codex/neutron-2-liquid-glass-rescue-20261111`; target: `main`.

**Status: implementation repairs are present. Keep the PR Draft until the exact
final head passes CI, native image review and repository requirements; then
complete the requested merge.** The latest user instruction authorizes that
integration decision. Full-game and hardware measurements remain unperformed
limitations, not a permanent Draft requirement. Source review, compilation,
unit tests and offscreen render checks establish different things and do not
claim full-game validation.

This record supplements the preserved
[original implementation notes](NEUTRON-2-IMPLEMENTATION-2026-11-11.md).
The original screenshots, generated concept images, masterplan and acceptance
matrix were not available in this workspace. Consequently, exact visual parity
with those references has not been assessed. The supplied rescue brief and
existing repository implementation define the review scope below.

## 1. Provenance

| Reference | Identified source |
|---|---|
| Main/base after PR #180 | [`97c263c792864d03c2271670121fd72d53bbfeb1`](https://github.com/YoungJibbit95/Neutron-Client/commit/97c263c792864d03c2271670121fd72d53bbfeb1) |
| New main for final integration after PR #181 | [`fa24c93386daf61077772862b220f27817e07a99`](https://github.com/YoungJibbit95/Neutron-Client/commit/fa24c93386daf61077772862b220f27817e07a99) |
| Original interrupted implementation | [`3a1902a0efebf075ba96b2911e2c6aed7f44d8ed`](https://github.com/YoungJibbit95/Neutron-Client/commit/3a1902a0efebf075ba96b2911e2c6aed7f44d8ed) |
| Initial Java 8 test-compilation repair | [`8e6211d387bd36b1022e994b929b2a0dfea9745a`](https://github.com/YoungJibbit95/Neutron-Client/commit/8e6211d387bd36b1022e994b929b2a0dfea9745a) |
| Published implementation candidate | [`95a2aee00f2a8ec36f309f4d499a9a84a4c10670`](https://github.com/YoungJibbit95/Neutron-Client/commit/95a2aee00f2a8ec36f309f4d499a9a84a4c10670) |
| Before artifact | GitHub Actions artifact `13848698306`, retained locally as `evidence/neutron-before-180-native.zip` |
| Commit tested by before artifact | `18f56b8159a0404189aa556849d16f3e34dc36e5` |
| Baseline tree equivalence | The artifact commit and the `97c263c` base have tree `b5e722a40900a40ddfab5da83c4cbae5c61a113d` |

**Integration update:** main advanced to `fa24c933` with PR #181 after the
`95a2aee` checkpoint checks. The recovery work is being integrated with that
new main through a normal merge. All `95a2aee` results below retain their
original scope: merge `2450385b` against `97c263c`. They do not validate the
new combined source tree. Exact final-head CI and native feature/image review
must therefore run again before the requested merge; their outcomes will be
recorded in PR #182. The historical evidence is preserved.

The artifact's `logs/environment.log` and `logs/summary.log` describe its
historical run: Temurin 8u482-b08, Maven 3.9.15, Linux Mesa 25.2.8 llvmpipe
software rendering, and eight completed native JVM suites. Its stated limits
include game-world rendering, native Display/fullscreen/VSync, multiplayer and
full client bootstrap. Those results belong to the baseline, not the rescue
candidate. Tree equivalence makes it a useful source baseline; it does not
establish matching hardware conditions for a performance comparison.

The baseline has valid same-path `compact-{Modern,Creative}-*.png` images for TargetHUD, SessionInfo and ArmorHUD. The empty `target-living-matrix.png` backdrops are excluded from TargetHUD evidence.
It has no Keystrokes native image. Its `18-projected-surfaces-resize.png` must
not be counted as a Nametag-before image: that fixture never enabled Nametags,
which the production replacement predicate requires. The old caption is
insufficient evidence. The corrected independent suite will initially supply
after-only Nametag/Keystrokes captures; authentic before captures remain missing.

## 2. Implementation coverage

The requested classifications are used literally:

- **Complete and verified:** implementation and relevant acceptance evidence exist.
- **Implemented but untested:** source exists, but the relevant acceptance remains untested.
- **Partially implemented:** required behavior is only partly present.
- **Missing:** required implementation or evidence is absent.
- **Defective or inconsistent:** an observed defect remains unresolved.

In the two matrices, **Implemented but untested** refers to full-game
acceptance. Tests named in the evidence column are coverage locations, not a
claim that they passed. Narrower results are recorded in section 5.

### Ten requested UI areas

| Area | Recovered implementation and rescue changes | Source / focused evidence | Classification and remaining acceptance |
|---|---|---|---|
| A. Client Settings | Existing sections, real values, preview, switches, sliders and popups retained. Compact header accent moved above navigation. Reopening clears stale control/category motion; resizing releases slider capture. Captured slider feedback follows the committed value immediately. Popup fill, highlight and border share reveal, opacity and radius. | [SettingsMenu](../src/main/java/me/youngjibbit/gui/clientsettings/SettingsMenu.java); `LegacySettingsLayoutTest`, `SettingsMenuToggleBounceTest`, `SettingsMenuVis03AInteractionTest` | **Implemented but untested.** Accept all sections, editing, preview, focus, scrolling and compact/regular layouts in-game. |
| B. Panel ClickGUI | Original category/sidebar, search and module structure retained. Category marker and disclosure geometry are bounded. Assigned keybind chips render in both presentations with conflict indication and reserved title space. | [ClickGui](../src/main/java/me/youngjibbit/clickgui/ClickGui.java), [ClickModule](../src/main/java/me/youngjibbit/clickgui/component/impl/ClickModule.java); `PanelControlMotionTest` | **Implemented but untested.** Accept long names/binds, search, toggles, scrolling, expanded content and repeated reopening. |
| C. Dropdown ClickGUI | Independent category columns and original compact interaction model retained. Search has its own spring; toolbar hover states advance once before painting instead of sharing state. Columns still own their independent reveal, scrolling and dragging. | [DropdownGui](../src/main/java/me/youngjibbit/clickgui/DropdownGui.java), [DropdownModule](../src/main/java/me/youngjibbit/clickgui/component/impl/DropdownModule.java); `DropdownControlMotionTest` | **Implemented but untested.** Accept overlapping columns, header dragging, keyboard binding and controls during expansion. |
| D. Aura settings presentation | Existing `Value` objects, targets, filters, mode selection and numeric controls retained. Shared subcategory rail/count/disclosure and control styling apply without changing combat behavior. Reveal stays within its allocated height. | [ClickSubcategory](../src/main/java/me/youngjibbit/clickgui/component/impl/ClickSubcategory.java), `ClickModule`, `ClickControlStyle`; dedicated Aura fixture added to `ClickGuiRenderCheck`, Java 8 test compilation passed, native rerun pending | **Implemented but untested.** Accept every visible/conditional Aura control and its actual setting relationship in both ClickGUIs. |
| E. Nametags | Restored existing scene material/blur ownership. Compact centered panel, bounded health rail and finite health ratio. Optional `Player Head` and `Distance` settings default off and use actual skin/distance. Matrix/state cleanup and per-entity/world lifecycle cleanup added. | [Nametags](../src/main/java/me/youngjibbit/module/impl/render/Nametags.java); `Neutron20HudCompatibilityTest`, `Neutron20HudRenderCheck` | **Implemented but untested.** Accept real-world projection, movement, depth, damage/regeneration, long names and shaderpacks. |
| F. TargetHUD | Familiar compact layout and actual target health/range retained. Highlight and border follow reveal times surface opacity. Effective radius matches the material; world changes close the old surface. | [TargetHUD](../src/main/java/me/youngjibbit/module/impl/render/TargetHUD.java); existing `IngameHudRenderCheck` and compact HUD fixtures | **Implemented but untested.** Accept target switching/loss, previews, positioning and game-world transitions. |
| G. Session Info | Existing elapsed time, kill/death data, clock and minimal layouts retained. Chrome follows opacity. Material draws avoid an extra explicit blur where the material already supplies it. World reset invalidates deferred paints. | [SessionInfo](../src/main/java/me/youngjibbit/module/impl/render/SessionInfo.java); `SessionInfoRenderCheck`, `Neutron20HudRenderCheck` | **Implemented but untested.** Accept actual session events, counters, positioning and unload/re-entry. |
| H. Keystrokes | W/A/S/D, Space and optional LMB/RMB use actual bindings. Modern/Creative key plates inherit scene material/theme/opacity. Basic feedback now snaps for disabled/reduced motion. World/disable paths clear springs and surfaces. | [Keystrokes](../src/main/java/me/youngjibbit/module/impl/render/Keystrokes.java); `Neutron20HudRenderCheck` | **Implemented but untested.** Accept physical input/rebinding, all modes, mouse-button visibility and edge positioning. |
| I. ArmorHUD | Requested Modern and Basic layouts and existing variants retained, including item rendering and semantic durability. Matching inset radii/opacity and duplicate-blur avoidance applied. World reset invalidates queued paints and clears slot motion. | [ArmorHUD](../src/main/java/me/youngjibbit/module/impl/render/ArmorHUD.java); compact HUD fixtures, `Neutron20HudCompatibilityTest`, `Neutron20HudRenderCheck` | **Implemented but untested.** Accept both layouts with empty, damaged, changed and enchanted equipment at multiple scales. |
| J. Corrections / Diagnostics | Glass framing and theme text contrast applied. Axes retain a finite positive range; zero remains valid data and absent/invalid samples remain gaps. Theme-gradient traces interpolate only display geometry; numeric labels report measured values. World resets invalidate deferred paints. | [Diagnostics](../src/main/java/me/youngjibbit/module/impl/render/Diagnostics.java), [DiagnosticsPlots](../src/main/java/me/youngjibbit/module/impl/render/DiagnosticsPlots.java); `DiagnosticsGraphMotionTest`, `DiagnosticsRenderCheck` | **Implemented but untested.** Accept empty/zero/bursty/live data, labels, scrolling and world lifecycle in-game. |

Shared drawing code also reaches Library, Theme Editor and other existing
surfaces. They are regression subjects. This work does not independently
redesign ArrayList, Watermark, Hotbar, Config Library or ESP; the inspected
production diff contains no combat or movement implementation changes.

### Six required animations

| Behavior | Mechanism and boundary rule | Reset / reduced-motion policy | Classification |
|---|---|---|---|
| Settings section navigation | `SettingsMenuMotion.category` uses `HudSpring`; logical section changes immediately. Displayed marker is clamped to the first/last category. | Shared animation/intensity settings; reopen clears presentation/category state. | **Implemented but untested** in-game. |
| Slider dragging and value feedback | Captured Settings thumb snaps to its committed value. External/keyboard changes retain existing `HudSpring` feedback. Panel/Dropdown keep their real slider controls. | Reduced/disabled motion snaps; resize releases pointer capture without inventing values. | **Implemented but untested** in-game. |
| Dropdown expansion/collapse | Existing `DropdownMotion`, module reveal and subcategory `HudSpring`; rendered reveal is bounded to 0–1 and drives clipping/layout from the same sample. | Shared animation/intensity settings; reopen/cancel clears transient input/motion state. | **Implemented but untested** in-game. |
| Category hover and selection | Existing `UiSpring` feedback and `PanelMotion.category`; independent search/toolbar state and bounded selection offset. | Disabled/reduced motion follows shared helper policy; state is reset on reopen. | **Implemented but untested** in-game. |
| Nametag health changes | Per-entity `HudSpring` follows a finite actual-health/max-health ratio. Rendering clamps the rail inside the panel. | Initial/reduced/disabled state snaps; despawn, world change and disable clear entity state. | **Implemented but untested** in-game. |
| Diagnostics graph data changes | `HudSpring` eases the presentation tail; history is copied for display, leaving raw samples and numeric labels intact. | Reduced/disabled motion snaps; invalid/missing data is not replaced with an invented measurement. | **Implemented but untested** in-game. |

Existing delta-time helpers remain the motion infrastructure. Bounded display
geometry prevents decorative spring overshoot from changing control hit boxes
or available layout height. These are implementation properties; consistent
timing across actual frame-rate conditions still needs native observation.

## 3. Shared rendering architecture and repairs

The original `LiquidGlass`, `MenuSurface`, `LegacyUiStyle` and
`NeutronVisualTokens` integration was continued. PinkLemonade remains the
default identity through the theme system; custom themes and existing material
preferences remain the source of colors and policy.

The shared repair has four parts:

1. **Geometry:** reject non-finite/nonpositive rectangles; clamp radius to the
   surface; use exact decoration primitives after mapping roundness so the
   global setting does not remap it again. Manual fill/border pairs use the
   same effective geometry.
2. **Opacity and theme:** fill, inner highlight and border follow surface
   opacity; popup/target reveal contributes its own factor. Neutral highlights
   remain neutral instead of passing through the user gradient mapper.
   Border toggles, width/strength and existing border opacity remain effective;
   border opacity is not multiplied twice.
3. **Material ownership:** `ArrayListSurface` / `UiEffects` continue to own
   scene capture and material rendering. `LiquidGlass` introduces no
   framebuffer, shader program or texture owner. It does issue drawing calls;
   the original description of it as having “no GL state” should not be read
   as proof of state safety. Existing state scopes and native checks establish
   that narrower contract.
4. **Lifetime and data:** world/disable cleanup closes affected surface owners
   and resets motion. Epoch/run guards prevent queued HUD callbacks from
   recreating released resources after a reset. Nametag entity state is pruned.
   Health, equipment, input, session and diagnostic values remain game data;
   smoothing is presentation-only.

These changes reduce identified risks such as duplicate material work and
stale callbacks. They do not establish a measured performance improvement.

## 4. Tests and native fixture coverage

Focused regression additions target finite geometry/opacity, compact Settings
header spacing, captured slider feedback, stale motion on reopen, independent
Dropdown hover states, reveal bounds, health-ratio sanitization and graph
empty/zero/invalid data.

The native runner [`run-ui-render-checks.sh`](../.github/scripts/run-ui-render-checks.sh)
adds dedicated Diagnostics and Neutron 2.0 HUD suites. After integration with
PR #181, the runner has eleven isolated suites, including main's inventory
assistance suite and ThemeLogo checks. `Neutron20HudRenderCheck` isolates
Nametags and Keystrokes, changes
actual fixture health/input, checks optional details, themes, bright/dark
backgrounds, scales, reduced motion, lifecycle cleanup, GL state restoration
and warm resource counts. It also checks that queued SessionInfo/ArmorHUD
paints are discarded after world unload and fresh paints can resume.

The Diagnostics fixture is now invoked explicitly. Screenshot collection
includes `diagnostics-preview` and `neutron20-preview`. The render-performance
workflow path filters now include GUI, ClickGUI and render-module changes so
these changes are eligible for that workflow.

The old expanded Panel screenshots used AimAssist. They do not establish
Aura-specific coverage. A dedicated production Aura fixture has been added to
`ClickGuiRenderCheck` for Legacy/Modern/Creative expanded and collapsed states.
It checks the registered module's real Reach setting through exact-value UI
input, restores the value and requires Aura to remain disabled with its keybind
unchanged. Java 8 test compilation passed; the native rerun and image inspection
remain pending.

Fixture existence and a success string in source are not execution evidence.
Pbuffer readback checks exercise native rendering with synthetic fixture state;
they do not launch a full game or establish real-world shaderpack compatibility.

## 5. Verification evidence and open gates

| Gate | Evidence at this record's drafting point | Acceptance |
|---|---|---|
| Exact local Java/Maven versions | Executables reported Temurin `1.8.0_482-b08`, `javac 1.8.0_482`, Maven `3.9.15`; official checksums matched. | Toolchain verified. |
| Java 8 compilation | Integrated production and test compilation completed. The final focused run recompiled the frozen production sources with Temurin 8u482-b08 and Maven 3.9.15. | Compilation passed. |
| Native fixture corrections: Java 8 test compilation | Corrected Nametag placement/preconditions and the new Aura fixture compiled successfully in 9.194 s. Log: `evidence/maven-native-fixture-test-compile-20261111.log`. | Test compilation passed; native execution still required. |
| Candidate Windows Java 8 regression | `95a2aee` CI passed: **2,179 tests, 0 failures, 0 errors, 3 existing skips**, 12 min 27 s. Actual tested merge: `2450385bce759737143e34da7b67824c0edbf5a3` against base `97c263c`. `SmartDisableLifecycleTest` passed all 10 tests. | Candidate Windows run passed; final fixture/documentation head requires its own checks. |
| Full local `mvn test` | Completed in 9 min 22 s: **371 suites, 2,179 tests, 11 failures, 0 errors, 3 existing skips**. Log: `evidence/maven-test-configured-20261111.log`. Breakdown below. | **Failed; no full-suite pass claim.** |
| Focused local Maven regression run | **54 suites, 229 tests, 0 failures, 0 errors, 0 skips**, 32.677 s, with corrected task-local environment and unchanged source/test expectations. Log: `evidence/maven-ui-focused-20261111.log`. Selection below. | Focused regression run passed; full-suite result remains failed. |
| `mvn -Prelease-bundle package` | Not attempted locally after the failed full test run. Candidate Windows CI ran `mvn -Prelease-bundle clean package` successfully in 13 min 5 s: 2,179 tests, 0 failures, 0 errors, 3 skips; packaged runtime check 6/6 passed. | Candidate release validation passed; final-head checks remain in PR #182. |
| Candidate native render suites | `95a2aee`: **9 of 10 suites passed**, including Diagnostics. The new `neutron20` suite stopped at its first Nametag pixel assertion because the fixture target was offscreen. Test-only correction and stronger preconditions are compiled; rerun pending. | **Workflow failed; corrected fixture rerun open.** |
| Candidate synthetic render-performance harness | `95a2aee` workflow succeeded: four balanced pairs, eight scenarios, 64 exact RGBA comparisons passed; median render-thread heap allocation unchanged; warm framebuffer/shader allocations zero. Timings below. | Synthetic harness result only; no full-game FPS claim. |
| Full-game visual/interaction matrix | No full-game session accepted in this workspace. | **Unperformed limitation; disclosed alongside the available native evidence.** |
| Hardware frametimes / average FPS | No comparable full-game measurements collected. | **Unavailable.** |
| Merge | Latest user instruction requests merge after implemented features, green exact-head CI, native image review and all current repository protections, approvals and review requirements. | **Requested after those gates pass; no source-only merge claim.** |

### Full local run: failure accounting

| Failure group | Full-run failures | Follow-up evidence / interpretation |
|---|---|---|
| `JavaFxRuntimeTest` | 1 | The bundled Java 8 sign-in probe requires Windows x64. This Linux run does not satisfy that platform requirement. |
| `FontAtlasMetricsTest` | 1 | Linux Java 8 font fallback/mapping differed. The task-local environment was corrected for follow-up validation. |
| `NetworkResearchHarnessTest` | 2 | `JAVA_TOOL_OPTIONS` injected a JVM startup message into subprocess stderr, violating exact-output assertions. The task-local invocation was corrected. Together with `FontAtlasMetricsTest`, all 13 related isolated cases then passed. |
| `SmartDisableLifecycleTest` | 7 | Failed in the full warmed JVM. The unchanged class passed all 10 tests in isolation, in the focused run and in candidate Windows CI. A separate Java 8 probe reproduced static-final/JIT fixture-rebinding susceptibility; attribution of **all seven original failures is not proven**. |

No production source or test expectation was changed to accommodate these local
failure investigations. The isolated/focused results provide narrower evidence
and do not erase the 11 failures from the full run.

The scratch-only diagnostic warmed the `Module` client supplier, then performed
the fixture's ordinary `Unsafe` rebind. The compiled supplier retained the
previous `ExploitHookFixture` client instead of the current client
(`current=false`, `previous=true`). This demonstrates a concrete test-fixture
susceptibility under Java 8 optimization. It does not prove that this mechanism
caused every original lifecycle assertion failure. The diagnostic log is
`evidence/smart-disable-jit-fixture-probe.log`; no related production/test
expectations were changed.

The successful focused run used this test selection with the pinned tools:

```text
mvn test -Dtest=Neutron20*Test,Diagnostics*Test,*Settings*Test,Dropdown*Test,*Panel*Test,*Hud*Test,*HUD*Test,SessionInfo*Test,Armor*Test,Wave07*Test,ClientSettings*Test,Ui*Test,ArrayList*Test,FontAtlasMetricsTest,NetworkResearchHarnessTest,SmartDisableLifecycleTest
```

### CI checkpoint history

The initial test-compilation checkpoint `8e6211` had six successful workflow
checks. It precedes the published implementation candidate `95a2aee`; its
success is historical evidence for that earlier commit only.

| Workflow | Initial checkpoint `8e6211` | Implementation candidate `95a2aee` |
|---|---|---|
| Java 8 / Windows regression | [43018693258](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43018693258) — success | [43021194808](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43021194808) — success: 2,179 tests, 0 failures, 0 errors, 3 skips |
| Native UI rendering | [43018693367](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43018693367) — success | [43021194829](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43021194829) — failed: 9/10 suites passed; corrected fixture rerun pending |
| Render performance harness | [43018693215](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43018693215) — success | [43021194873](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43021194873) — success; synthetic measurements below |
| Release bundle | [43018693231](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43018693231) — success | [43021194861](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43021194861) — success: 2,179 tests and 6/6 packaged runtime checks |

Final-head CI outcomes and artifacts will be recorded in PR #182. A successful
offscreen or CI result narrows the remaining uncertainty; it does not
establish full-game validation or game FPS.

### Candidate native findings and fixture correction

The `95a2aee` artifact is retained under
`evidence/after/run.kWX9I6DV`. Its summary reports that all ten native suites
were attempted and only `neutron20` failed. The first independent Nametag
visible-pixels assertion failed because the fixture used the absolute target
position `(1.5, 0, 1)` while the cached local-player camera was at
`(8.5, 65, 8.5)`. This invalid placement put the target outside the visible
view. Later Nametag, Keystrokes and lifecycle checks in that new suite were not
reached and are not claimed as passed.

The correction changes only the test fixture: place the target relative to the
cached camera, synchronize its interpolated position, and explicitly require a
valid Nametag replacement candidate and an on-screen projected rectangle before
painting. All existing visibility, pixel-change, motion, lifecycle, GL-state and
resource assertions remain. The correction's successful Java 8 test compilation
is evidence of compilation, not a substitute for the required native rerun.

Before/after fixture images for Settings, Panel, Dropdown and inherited
Library/Theme Editor/Trash were inspected at wide and small sizes. No clipping,
layout or missing-content regressions were observed in those captured states;
the new neutral contours/highlights are more visible. This limited image review
does not cover the missing full-game, Aura-rerun or hardware acceptance.

The Diagnostics suite passed at `95a2aee`, but its `simple-zero.png` reused cached panels from the empty-data state after direct fixture ping injection. That image is not accepted as proof of zero-value rendering. The test-only correction clears `cachedPanels`, requires the real `0 ms` panel status, and compares the trace band against the empty-state capture while excluding status/empty-label text. This corrected fixture passed Java 8 test compilation; native validation of the stronger zero-specific assertion remains pending. The inspected `simple-editor-small.png` already shows axes, a missing-ping gap, trace and correction marker without overlap.

Valid compact Modern/Creative before/after pairs for TargetHUD, SessionInfo and ArmorHUD were also inspected. Face, health, distance, session counters, item/durability layout and text remain stable in those captures; new chrome is aligned. These selected captures do not replace full-game acceptance.

The candidate release artifact is `13854914886` (387,782,738 bytes), with reported SHA-256 `bf7eabf490e93adbeb70062a7305f9918d478e1e4a461204fa529f6d72363caa`. The retained CI summary is `evidence/ci-release-95a2-summary.log`.

### Synthetic render-performance measurements

Run [43021194873](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/43021194873) compared main `97c263c` with merge `2450385bce759737143e34da7b67824c0edbf5a3` for head `95a2aee`. Four balanced before/after JVM pairs covered eight scenarios under Mesa llvmpipe software rendering. All 64 exact RGBA comparisons of the benchmark fixtures passed. Median render-thread heap bytes per frame were unchanged in every scenario. In both revisions, warm framebuffer creates/deletes, shader compiles and glyph uploads were zero across all four trials and eight scenarios.

The table reports the **median of four per-JVM quantiles**, not pooled frame quantiles. `completed_ms` includes `glFinish` and excludes backdrop setup, readback and file output. Paired changes are calculated within each before/after pair, so their median need not equal the ratio of the two aggregate medians.

| Scenario / viewport | Baseline p50 / p95 / p99, ms | Candidate p50 / p95 / p99, ms | Paired p50 change: median [range] |
|---|---|---|---|
| Text, 960x640, scale 2 | 7.016 / 11.013 / 13.056 | 6.947 / 11.300 / 12.419 | -0.58% [-8.16%, +1.10%] |
| Clipped bloom, 960x640, scale 2 | 43.437 / 50.410 / 54.664 | 43.054 / 49.100 / 53.342 | -0.04% [-1.83%, +0.67%] |
| Gaussian blur, 960x640, scale 2 | 30.417 / 35.929 / 43.736 | 32.795 / 36.442 / 41.100 | +6.96% [-6.66%, +18.22%] |
| Glass surface, 960x640, scale 2 | 92.287 / 99.572 / 103.243 | 92.462 / 95.337 / 102.440 | +0.12% [-1.57%, +0.85%] |
| Text, 854x480, scale 1 | 3.128 / 4.845 / 6.284 | 3.120 / 4.308 / 5.080 | -0.24% [-3.15%, +1.91%] |
| Clipped bloom, 854x480, scale 1 | 28.815 / 30.860 / 35.704 | 28.909 / 30.610 / 35.605 | +0.32% [+0.27%, +0.38%] |
| Gaussian blur, 854x480, scale 1 | 19.074 / 23.616 / 28.119 | 19.968 / 23.933 / 28.406 | +8.24% [-20.13%, +24.74%] |
| Glass surface, 854x480, scale 1 | 33.088 / 35.518 / 39.047 | 33.320 / 36.556 / 44.139 | +0.70% [-0.84%, +1.67%] |

This benchmark measures low-level workload parity, not complete redesigned screens or total UI overhead. Its measurements show mixed changes and substantial spread in the Gaussian cases. They establish neither a universal speedup nor hardware performance parity. Workflow success reflects its synthetic checks, not a timing acceptance gate. These values are not game frame times and are not converted to game FPS. Source: the run's `comparison.json` and retained `evidence/performance-95a2-summary.json`.

### Unperformed full-game and hardware observations

The latest integration instruction permits merge after the available final-head
CI/native review and repository gates pass. The observations below remain
explicit limitations; their absence alone does not keep the PR permanently Draft.

| Dimension | Unperformed observation | Current status |
|---|---|---|
| Ten areas A–J | Before/after captures in matching state; inspect spacing, clipping, icon size, text alignment, contrast and glow. | Pending; original concept references unavailable. |
| UI sizes | Panel/Dropdown and Settings at compact and regular resolutions, GUI scales 1–4, long labels and expanded content. | Pending. |
| Themes/material | PinkLemonade plus another preset and a custom theme; opacity 0/0.5/1; roundness extremes; border/glow/blur options; bright/dark world. | Pending. |
| Motion/input | Six required animations with reduced motion on/off, animations disabled and intensity variants; rapid retarget, drag/release, keyboard control and reopen. | Pending. |
| HUD data | Real damage/regeneration, target transitions, input, session events, equipment changes and zero/empty/live diagnostics. | Pending. |
| Lifecycle | World entry/exit, entity despawn, module disable/re-enable, resize and HUD positioning. | Pending. |
| Compatibility | Shaderpack enabled/disabled, native display/fullscreen/VSync and relevant GPU/driver configurations. | Pending. |
| Performance | Same world, scene, settings, resolution, Java, driver and capture procedure before/after; warmup then at least 600 frames; record p50/p95/p99 and measured average FPS. | Pending; no numbers inferred from helper changes. |
| Shared-helper regressions | Existing Library/Theme Editor, ArrayList, Watermark, Hotbar and ESP paths affected indirectly by drawing helpers. | Native/full-game review pending. |

## 6. Pinned toolchain and reproducibility

The project target remains Minecraft 1.8.9, Java 8 and LWJGL 2. Local tools were
installed only under the task's `toolchains` directory; no global installation
or new client runtime was introduced.

| Tool | Version / primary source | Verified archive digest |
|---|---|---|
| Eclipse Temurin JDK, Linux x64 HotSpot | `8u482-b08`; [Adoptium release archive](https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u482-b08/OpenJDK8U-jdk_x64_linux_hotspot_8u482b08.tar.gz), checksum from the official Adoptium version API | SHA-256: `e74becad56b4cc01f1556a671e578d3788789f5257f9499f6fbed84e63a55ecf` |
| Apache Maven | `3.9.15`; [Apache archive on Maven Central](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.15/apache-maven-3.9.15-bin.zip), [official SHA-512](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.15/apache-maven-3.9.15-bin.zip.sha512), also pinned in repository CI | SHA-512: `251c676eb88684d6dd86c666817979d256a335c31ad34ab0e1df17eeca1cd504a6c890e906a46d04e51cc7a4fb02f33a75971ef750558034e0a81c257be02267` |

Required commands remain:

```text
mvn test
mvn -Prelease-bundle package
```

Use the pinned Java/Maven executables, preserve logs and identify the tested
commit. Network/proxy/trust configuration used for this workspace is task-local;
it is not a project dependency or a reason to alter test expectations. Windows
release validation follows the existing
[development workflow](DEVELOPMENT-WORKFLOW.md) and repository CI.

## 7. External design guidance and license credits

Primary repository instructions and license files were reviewed on 2026-11-11.
They informed the review; no external runtime, web framework, shader system or
skill source was bundled or executed. The Java/LWJGL implementation uses the
existing Neutron infrastructure.

| Source and author | Inspected guidance | License observed | Applied interpretation / limit |
|---|---|---|---|
| [OpenMotion](https://github.com/Yuan-ManX/open-motion), Yuan-Man | Repository README and project workflow/material | [MIT, copyright 2026 Yuan-Man](https://github.com/Yuan-ManX/open-motion/blob/main/LICENSE) | Reusable motion primitives and preview/refinement loop. Its browser/video runtime is not a Java 8 dependency. No claim of a complete standalone skill-directory review. |
| [Unslop](https://github.com/heydamir/unslop), Damir Sokolovsky | [Unslop skill](https://github.com/heydamir/unslop/blob/main/skills/unslop/SKILL.md) | [CC BY 4.0](https://github.com/heydamir/unslop/blob/main/LICENSE) | Consistent spacing, icon/text roles, deliberate hierarchy and iterative inspection. Guidance adapted to Neutron's identity; blanket gradient/glow bans were not adopted. |
| [ECC](https://github.com/affaan-m/ECC), Affaan Mustafa | [Verification loop](https://github.com/affaan-m/ECC/blob/main/skills/verification-loop/SKILL.md) | [MIT, copyright 2026 Affaan Mustafa](https://github.com/affaan-m/ECC/blob/main/LICENSE) | Separate build, test, diff and acceptance evidence. Translate workflow to Maven/Java; avoid unrelated web commands or arbitrary coverage quotas. |
| [Taste Skill](https://github.com/Leonxlnx/taste-skill), Leonxlnx | [Taste skill](https://github.com/Leonxlnx/taste-skill/blob/main/skills/taste-skill/SKILL.md) | [MIT, copyright 2026 Leonxlnx](https://github.com/Leonxlnx/taste-skill/blob/main/LICENSE) | Preserve product-specific identity and coherent visual rules; inspect rendered results at multiple sizes. Website layout/runtime prescriptions do not apply directly. |
| [Emil Kowalski Skills](https://github.com/emilkowalski/skills), Emil Kowalski | `emil-design-eng`, [animate](https://github.com/emilkowalski/skills/blob/main/skills/animate/SKILL.md), [animation-performance](https://github.com/emilkowalski/skills/blob/main/skills/animation-performance/SKILL.md), [break-ui](https://github.com/emilkowalski/skills/blob/main/skills/break-ui/SKILL.md) | [MIT, copyright 2026 Emil Kowalski](https://github.com/emilkowalski/skills/blob/main/LICENSE) | Immediate feedback, interruptible motion from current state, restrained functional movement, reduced motion and realistic edge cases. Browser compositor advice is not an OpenGL performance guarantee. |

This section credits the adapted Unslop guidance to Damir Sokolovsky and links
its source and CC BY 4.0 license; the changes are its translation into an
existing Minecraft UI review and the omission of prescriptions that conflict
with the requested visual identity. The linked licenses are the authority for
any later copying or redistribution of source material.

## 8. Integration decision

Continue the existing PR and preserve the interrupted implementation's history.
The rescue is a scoped repair and completion pass over its existing design.
The latest user instruction is to finish the implementation and merge after
green CI. Keep the PR Draft until the exact final head passes its required CI,
the native images and feature coverage have been reviewed, and all current
repository protections, approvals and review requirements are satisfied. Then
mark it ready and merge as requested, without bypassing protections.

Native Pbuffer and synthetic performance evidence are available. Full-game,
shaderpack and hardware measurements remain unperformed and must stay explicit
in the PR and final report; their absence alone is not a permanent merge block
under the updated instruction. Final-head outcomes and the actual merge result
will be recorded in PR #182. This record does not itself assert that a merge
or release occurred.
