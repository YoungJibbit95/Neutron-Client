# WAVE06-VIS-BROWSER01: Dynamic Island V1 / Motion foundation

Status: **PARTIAL — GitHub PR candidate; native and live gates NOT_RUN.**
Owner: VIS only; TECH network, gameplay and packet code untouched.

## Source and integration receipt

- `start_sha`: `8f2ded1185cfe210b529c5839cb5b281dfbfaa36`.
- `effective_base_sha`: `178b9a2a3957864878d939c29d04017e706ccf23` (PR #96 merged while the read-only preflight was running; 22 changed paths reviewed, zero overlap with VIS paths).
- PR #97, launcher/CI scope: no overlap with VIS source and tests. No change to workflows, `pom.xml`, `ClientSettings`, `ThemeModule`, other GUIs, world/combat/network code or Minecraft hooks.
- Read `AGENTS.md`, `docs/DEVELOPMENT-WORKFLOW.md`, `docs/NEUTRON-UI.md` and WAVE06 design/reference/integration contracts from the user materials.
- Five user screenshots R01–R05 inspected only for inspiration: compact pill, bounded metrics, controlled accent and dark background. **No screenshot, font, brand icon or third-party visual asset added.** The badge is a typographic N using the existing font/Neutron branding.
- No local Windows checkout, worktree, external assets, binaries or release data touched.
- Updated `main` during follow-up: `b9b8221bbbc77c1ae6dfd4e9d8e1139069df16c5`; only launcher paths were merged from PR #97, no VIS source overlap. The VIS branch was merged forward without changing its scoped PR file list.
- **Approach 2 reference (08.11.2026):** user-provided `Neutron_Approach_2_Alle_Designs(1).zip` contains four concept boards and 20 cropped PNG mockups, plus `README_DE.txt`. Its concept is nearer the original Neutron layout: compact dropdown, slim ArrayList, magenta–orange accents, Liquid Glass, stateful island, session panel, mini-metrics and TargetHUD. These are design direction, **not Java code, licensed UI assets, or verified telemetry**; no binary or texture is imported. The separately supplied current-client screenshot is a baseline of legacy HUD widgets, not a native rendering proof of this PR. Global ThemeStyle, font and GUI redesign remain explicitly out of scope.

## Product change

The existing `Watermark / Mode` ComboValue appends **Dynamic Island** after
`Simple, Glass, Outline, Rise, iOS, Logo 1, Logo 2, Wordmark, Status`.
Default index **1 / Glass**, existing names and legacy render branch are unchanged.
New values `Island Target` and `Island Clock` register **after** the old
`ArrayListSurface`/local values; there is no config migration.

- `IDLE`: real opt-in capsule at top center, client name/version and local
  minute-precision clock, shared `ArrayListSurface`, `UiEffects`, `GuiShaderBloom`,
  existing theme, font and GPU resource handling.
- `TARGET`: real **read-only** data only from `CombatTargetTracker.getPrimaryTarget(250)`,
  explicitly sourced from `aura` or `aimassist`, and accepted only if the same
  living entity is present in `mc.theWorld` by identity, with a player and a
  valid name. Entity-id reuse between worlds is fenced by a new scene epoch.
  Health/max/absorption are finite-clamped for display; bar and absorption are
  visual only, no gameplay mutation. Target identity/name switches do not retain
  the previous target's label in the content crossfade.
- `SCAFFOLD` / `MODULE_TOAST` / `POSSIBLE_SETBACK`:
  **DATA_PROVIDER_PENDING**. These are modeled, prioritized and exercised with
  synthetic value fixtures, with display templates, but **not live wired** to
  Scaffold, module lifecycle or packet events. No fabricated block counts, toggle
  events, anticheat flags or server confirmations. A simulated warning is labelled
  `Possible setback`, strictly a *local, unconfirmed* diagnostic indication.
- Events are bounded to one active presentation (no growing queue), expire by
  monotonic nanoseconds, keep a **fixed-size 32-ID per-epoch replay window** to
  suppress interleaved duplicate toggles, and use a warning hold/cooldown.
  On expiry the live priority is recomputed from *fresh* input.
- `IslandLayout` bounds the capsule to 85% of GUI width and viewport-safe
  geometry; `IslandMorphMotion` is a thin adapter built out of the existing
  `MenuSpring` exact oscillator, maintaining x/v across interruptions.
  Width/height/radius/opacity retarget without global animation state; content
  fades over a short existing-spring transition. One nanosecond sample per render.
  Focus loss, world changes and disabling reset/transient-stop the instance.
  `UI Animations Off` and `Reduced Motion On` snap to fully readable content.
- `IslandPresentation` prepares immutable title/detail/clock/health-bar content
  **before** the deferred `GuiShaderBloom` callback. It removes Minecraft control
  formatting from fixture strings, distinguishes simulated/pending provider states,
  and honors original `Client Name`/`Version` plus new `Island Clock` checkboxes,
  including when all are off. Content is never fetched from a live entity inside
  the deferred render pass.
- No UI paint code changes `Value`s, config, combat, targeting, packets, world state
  or player state. It creates no per-frame texture uploads or separate GL resources.

## Tests / acceptance matrix

| ID | Test / source | Scope |
|---|---|---|
| `W06-I01` | `IslandStateMachineTest` | Priority, epoch, expiry, source priority, stale target, 30 toggles, cooldown, finite health, unknown state |
| `W06-I02` | `IslandLayoutTest` | 640×360, 854×480, 1280×720 plus tiny viewports, 85% cap, clipping, Unicode and nonfinite geometry |
| `W06-I03` | `IslandPresentationTest` | No forced branding, frozen target contents, formatting sanitation, no stale labels, pending-only event claims |
| `W06-I04` | `IslandStateMachineTest` | Interleaved duplicate suppression and 32-ID bounded replay across 90 rapid events |
| `W06-M01` | `IslandMorphMotionTest` | Reversal preserving velocity, 30/60/144/240 FPS parity, invalid dt, 30 interrupt retargets, reduced/off content |
| `W06-L01` | `WatermarkIslandModeTest` | Exactly 9 legacy modes + 1 appended, mode default Glass, indices unchanged |
| `W06-RUNTIME` | **NOT_RUN** | Native Pbuffer, LWJGL/GL context, real Minecraft window, input, world, shader packs, server, FPS and GL perf |

Java 8 GitHub Actions must run on the **actual PR merge commit**, not an old pin.
A CI failure/block caused by hosted dependency downloads cannot be represented
as a successful Maven test. Only exact runs, logs and Suite/Test counts authorize
a green declaration. No new CI workflow is introduced here.
Prior PR-head `a6c483d28c8d3b301ce22952d0d47efd11e460cb`: GitHub Java 8 PR regression
[run #42473020932](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42473020932)
completed **1196 tests / 0 failures / 0 errors / 3 skips, BUILD SUCCESS**
according to its job log. This applies to that older head only; the updated
presentation and replay tests require a **new** PR-merge CI pass.

## Deliberately deferred: VIS-BROWSER02

1. `src/main/java/me/youngjibbit/module/impl/render/TargetHUD.java`:
   isolate presentation alpha/scale/slide from its existing health interpolation,
   then reversible show/hide and target-switch transitions. Do not change the
   seven existing designs, stored mode/default, TargetTracker ownership or
   `getHealth/getMaxHealth` semantics. Verify target death/loss/world switch,
   GUI chat/debug filtering, and deferred `GuiShaderBloom` closure ownership.
2. Add `src/test/java/me/youngjibbit/module/impl/render/TargetHUDMotionTest.java`
   with headless transition/clear tests; add native GUI Pbuffer plus real game
   validation only when an authorized renderer/runtime environment is available.
3. Obtain TECH-approved immutable Scaffold, post-lifecycle module toggle and
   local correction observations **before** wiring the three pending island
   providers; write independent epoch, duplicate/TTL and payload-provenance tests.
4. Run native and real-window tests at 640×360 / 854×480 / 1280×720 with
   configured GUI scales, all nine old modes, different color themes, pause/resume,
   lost-focus, world changes, unicode, HUD visibility and GL resource reclamation.

**Rollback:** close the draft PR and discard its isolated task branch; no direct
main push, merge, force push, tag or release was performed.
