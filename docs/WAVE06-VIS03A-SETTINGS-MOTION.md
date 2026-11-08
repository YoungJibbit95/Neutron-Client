# WAVE06-VIS-BROWSER03 / VIS03-A: Client Settings Motion

**Owner:** VIS. **Status:** isolated phase A candidate, runtime gates still open.

## Source pin and conflict review

- `start_sha` / `effective_base_sha`: `2bbd9c8ec6cb134628a0f6c00bc1110b364120a4` (after merges of VIS #98/#99 and TECH #100).
- Open TECH PR #101 modifies `RotationManager`, `RotationProfile`, `RotationSettings`, `Aura`, related combat tests/docs. **No overlap** with VIS03-A source/tests.
- Read current `AGENTS.md`, `docs/DEVELOPMENT-WORKFLOW.md`, `docs/NEUTRON-UI.md`, `docs/WAVE06-VIS-BROWSER01.md`, `docs/WAVE06-VIS-BROWSER02.md` and earlier user-provided WAVE06 visual archives, especially `05_MOTION_ARCHITECTURE`, `06_UI_HUD_ROLLOUT`, `09_ACCEPTANCE_MATRIX`, `shared/INTEGRATION_PROTOCOL` and `shared/SHARED_CONTRACTS`. Their historic SHA is not a new base.
- Read existing Panel `ClickGui` / `ClickCheckBox` / `ClickCombo` / `ClickSlider`, `DropdownGui`, `iOS`, `SideGuiScreen`, `GuiTransitions`, `MenuSpring`, `UiSpring`, `HudSpring`, `ClientSettings`, `ThemeMode` and relevant GUI/input/config tests.
- No new third-party assets, fonts, logos, buffers or dependency; Approach 2 PNGs remain design references only.
- No local Windows checkout, Minecraft runtime files, world, account data, POM, GitHub workflow or TECH file was touched.

## Delimited VIS03-A scope: SettingsMenu

Only the existing `gui/clientsettings/SettingsMenu` screen receives the focused rollout.
The Panel, Dropdown and iOS variants **do not** yet consume VIS03-A motion. Their own
independent animation/input contracts must be addressed in later phases, without
copying or replacing the existing MenuSpring oscillator.

1. `SettingsMenuMotion` is **not** a new global animation engine. It owns only
   screen-local `HudSpring` category position, disclosure and cursor scalars,
   plus `HudReveal` for a Neutron-2.0-only entry accent. All physics comes
   from the existing exact, interruptible `MenuSpring` and global motion
   settings `UI Animations`, `Reduced Motion`, `Calm/Balanced/Expressive`.
2. Category changes commit to the **logical selected category immediately**;
   the small accent marker tracks the new position without snapping position
   or velocity. The original category click hit areas, F6 navigation, keyboard
   focus and scroll resets remain authoritative. No decorative geometry
   substitutes for a click rectangle.
3. Combo dropdowns open and close reversibly. Their full logical input list is
   available immediately; during close the last popup remains only as a short
   **non-interactive** clipped fade. A rapid reopen of that same Value retargets
   the current spring; choosing another control/group discards the prior visual,
   and category changes never leak old popup labels. Keyboard arrows,
   Enter, Tab, Escape, click and wheel retain their synchronous semantics.
4. Toggles/sliders continue using the previously integrated `HudSpring`
   value presentation. Their stored Values and callbacks do not depend on
   spring progress. Their logical hitboxes and exact keyboard/number entry
   remain unchanged.
5. **Correctness fix:** `drawScreen` no longer calls `updateSlider` while
   mouse capture is held. Persistent numeric values are written only by
   `mouseClicked`, `mouseClickMove` or keyboard/explicit actions,
   never by a render callback. Drag lifetime is owned by the actual Value
   identity, cancelled on release or group changes.
6. The existing `GuiTransitions` remains owner of whole-screen exit/
   framebuffer crossfade and page reversal. `HudReveal` supplies a modest
   Neutron 2.0 entry accent without delaying navigation or blocking input.
   **No duplicate screen renderer or screen texture.** Full independent
   panel spring scale/slide is deferred until native GL and input checks.

## Acceptance matrix

| ID | Java 8 test | Gate |
| --- | --- | --- |
| `VIS03A-M01` | `SettingsMenuMotionTest` | exact FPS parity at 30/60/144/240, reversal, opening/closing, reduced/off, invalidation |
| `VIS03A-U01` | `SettingsMenuVis03AInteractionTest` | actual mouse/keyboard category, combo, slider capture, 30 close/reopen, no config changes from presentation |
| `VIS03A-C01` | `SettingsMenuMotionConfigTest` | real `ConfigManager` roundtrip, original defaults and no extra persisted motion state |
| `VIS03A-L01` | Existing `SettingsInteractionTest`, `SettingsMenuLifecycleTest`, `SettingsMenuToggleBounceTest`, `ClientSettingsConfigTest`, `ThemeModeApproachTwoTest`, `GuiTransitions` checks | legacy input/defaults/lifecycle |
| `VIS03A-P01` | **NOT_RUN** until actually executed | GL-state, LWJGL/native Pbuffer, real GUI input, game/world, shaderpack/resizing, FPS and p95/p99 |

Do not mark a pass without the actual GitHub pull-request merge SHA and
Maven suite/test/failure/error/skip totals. CodeQL and repository policy
checks are separate. The built-in Release Bundle workflow may run on PRs;
this is **not** authorization to publish any Minecraft binary.

## Next stages: separate branches, never automatically merged

- **VIS03-B:** transfer tested motion policy to **Panel**, then **Dropdown**, then **iOS** as independent, reviewable PRs, preserving keyboard/pointer/focus/scroll/Config semantics and the shared `MenuSpring`.
- **VIS03-C:** opt-in Neutron 2.0 geometry, global material/spacing and rounded accents through `ThemeMode`, `ThemeCatalog`, `UiEffects`/`ArrayListSurface` and pre-existing shader caches. `Neutron Legacy` stays default; no theme database migration without evidence.
- **VIS03-D:** only read-only UI snapshot proposals for `SCAFFOLD`, `MODULE_TOAST`, `POSSIBLE_SETBACK`, marked `DATA_PROVIDER_PENDING` until TECH signs off. No packet listeners, invented block counts, verified server flags, gameplay/TECH-file writes or claims.

**Rollback:** close the isolated VIS03-A draft PR if CI or native gates fail;
do not merge, publish, force-push or alter local user checkouts.
