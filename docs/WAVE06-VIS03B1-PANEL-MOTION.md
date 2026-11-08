# WAVE06 VIS03-B1 – Panel ClickGUI motion (isolated)

**Owner:** VIS. **Scope:** Panel ClickGUI only. **Status:** Draft – CI/native gates open.

## Exact source and conflict preflight

- `start_sha` / `effective_base_sha`: `9f40ae0366463a3c93a59f4749bd723607b6bf99`.
- VIS #104 is merged externally and its final head `da472695d10aacedc2b7fd5d5bd24df0f7b0abd3` passed the full Java 8 regression on PR merge commit `c129853f78976760e49722abad73c464b9a4e035`. That test merge and actual `main` merge `9f40ae0366463a3c93a59f4749bd723607b6bf99` have identical tree `73bed3c6ac92138025007fea5422915fffb6b76b`. CI run #42484298124: 1,271 tests, 0 failures, 0 errors, 3 skipped, `BUILD SUCCESS`.
- Open TECH #105 (Scaffold research docs) and #106 (combat AI core/tests) are **disjoint**. Their gameplay/combat/network/packet sources were not edited or imported.
- Read `AGENTS.md`, `docs/DEVELOPMENT-WORKFLOW.md`, `docs/NEUTRON-UI.md`, `docs/WAVE06-VIS03A-SETTINGS-MOTION.md`, previous VIS contracts, `GuiTransitions`, `MenuSpring`, `UiSpring`, `HudSpring`, `ClientSettings`, `ThemeMode`, and the concrete Panel/Dropdown/iOS component paths. Approach-2 PNGs are nonredistributed design references only.
- GitHub API branch only: no local checkout/worktree, binary, font, image, shader, new dependency, POM or workflow modification.

## VIS03-B1 product change

- `PanelMotion` is a **small per-screen state adapter**, not a second engine: two `HudSpring`s backed by existing exact `MenuSpring`, one for category marker geometry and one for entrance accent. The existing `GuiTransitions` still exclusively owns whole-screen exit/page fading and GL texture capture.
- `ClickGui` uses the category spring every frame. Logical category selection and its scroll/reset/input owner change immediately through original mouse/PageUp/PageDown; the highlighted row follows with the spring. Window hit/drag/resize geometry, viewport bounds, tooltip, Tab focus and module-cache semantics are unchanged.
- For `Neutron 2.0` only, Panel uses a slightly more rounded/tinted `ArrayListSurface` and a spring-driven magenta-orange header accent. `Neutron Legacy` retains its original material/radius/default; there is no change to ThemeCatalog or its 20 legacy presets.
- `ClickModule` keeps existing panel `UiSpring` settings reveal, adds a `HudSpring` for the status marker and recomputes settings height from the **same frame** as its combo disclosure. Module activation remains immediate and is driven only by mouse/keyboard. Content callbacks, config, ModuleManager, Game/Packet events are unchanged.
- **Panel-only** `ClickCombo` reveal uses a reversible `HudSpring`, with geometry clamped to the visible option region and a small bounded end-pulse. Key navigation always acts on the actual ComboValue. A closing tail paints a clipped list but cannot receive input because logical `open` is already false. Compact Dropdown retains its original `ClickAnimation` path.
- **Panel-only** `ClickCheckBox` thumb travels with `HudSpring`; a brief bounded bounce is decorative. Its mouse/key callbacks and Value setters are unchanged. Dropdown still uses its existing animation.
- **Panel-only** `ClickSlider` replaces the previous exponential paint interpolation with the shared `HudSpring`. Keyboard/exact entry update the real value instantly; pointer dragging snaps the displayed thumb to the committed fraction so it never lags behind the cursor. Clamps prevent geometry overshoot beyond the track. No Value writes from draw/advance.
- Original `UiSpring` hover/press/focus and per-Value presentation ownership remain, including visually immediate keyboard focus, even when global Glow/Border are off.
- `UI Animations = Off` and `Reduced Motion = On` snap decorative scalar Values; movement uses frame-sampled dt capped to 50 ms. Stable click rectangles, clip, pointer ownership, scroll and config lifecycle are *not* based on spring progress. For a panel that is logically closed, existing reveal clipping retains input safety.

## Validation / acceptance

| ID | Test | Required evidence |
| --- | --- | --- |
| VIS03B1-M01 | `PanelMotionTest` | 30/60/144/240 FPS parity, category/entry reversal, 60 rapid retargets, finite times, reduced/off and reset |
| VIS03B1-U01 | `PanelControlMotionTest` | Real module/combo/checkbox/slider handlers, key focus, pointer, 30 reversals, category/scroll, disabled motion |
| VIS03B1-C01 | `PanelMotionConfigTest` | Actual ConfigManager Value roundtrip and original defaults; presentation must not persist |
| VIS03B1-L01 | Existing `Wave05UiInputTest`, `Qa02ScreenMotionTest`, `NavigationLifecycleTest`, `ThemeModelTest`, `ThemeModeApproachTwoTest` and complete Java 8 suite | No regression and no impact on Dropdown/iOS input |
| VIS03B1-N01 | Native Pbuffer/OpenGL, real Minecraft keyboard/mouse, GL resource/state, DPI/GUI-scale/screenshots, shaderpacks, p95/p99 FPS | **NOT_RUN** until actually observed |

**No native/GL/performance pass may be asserted from headless Java 8 tests.** Actual GitHub CI run ID, exact checked-out pull merge SHA, suites, test totals, fails/errors/skips and CodeQL/security checks must be recorded after the run finishes. Automatically triggered release-bundle build is not a release authorization.

## Separate follow-ups only after B1 CI

- VIS03-B2: DropdownGui/DropdownModule/compact control motion, preserving existing overlapping panel drag, search, focus, scroll and keyboard contracts. Separate branch and Draft PR.
- VIS03-B3: iOS animation pipeline (legacy AnimationEngine), category/search and nested values. Separate branch and Draft PR. No duplicate `MenuSpring` physics.
- VIS03-C: deeper Neutron-2.0-only materials/spacings/glass through existing `ThemeMode`, `UiEffects`, `ArrayListSurface`/shader caches. Legacy remains default.
- VIS03-D: read-only data contracts for Scaffold/toasts/local correction; no fabricated telemetry or TECH gameplay source edits.

**Rollback:** close this PR and discard this isolated task branch. Do not merge, force-push, tag or release from this work.
