# Integration pass 7 — 2026-10-26

Base: `ca9d6792f090f6ee3409bb895d1cad49023a23c8` (pass 6).
Branch: `codex/integration-pass-7`. The main agent owns review, integration and shared contracts.

## Delegation

Five GPT 6.1 Sol agents have separate worktrees and bounded ownership:

| Agent | Ownership | First reviewed result |
| --- | --- | --- |
| Visual | Menu, UI shaders and render state | Neutron-star menu, spring feedback, FBO restoration |
| Network / Exploit | Packet owners, queues, Backtrack, LagRange, Tickbase | Release LagRange holds on local death |
| Rotations / Realism | AimAssist, Aura, Scaffold and rotation consumers | Release stale rotation ownership before movement |
| Client performance | Client hot paths, preserving selection semantics | Stable minimum scan for single-target selection |
| Minecraft client performance | Display-only source optimizations | Context-aware F3 driver-string cache |

Shared Minecraft hooks require coordination. Agents do not rewrite simulation, packet contracts or another agent's files as incidental cleanup.

## Integrated evidence

- `d9a2bca1a`: late server packets after shutdown no longer fill the preconnection queue and crash world exit. The original local crash stack was traced through integrated-server player removal. Seven real NetworkManager regression tests cover terminal state and preconnection behavior.
- `2336612cd`: LagRange releases its owner on local death; the new embedded-network regression failed before the change.
- `d0b54520c`: Aura target loss and Scaffold support loss release their accepted rotation during TickEvent, before movement. Productive binding regressions failed before the fix.
- `bcb3f77ee`: single-best targeting avoids candidate-list allocation and sorting. Fixed 1,024-entity fixture: 8,974 comparisons before, 1,023 after; identical filters and stable ties. Aura's multi-target collection remains sorted.
- `3f7798331`: F3 caches only vendor, renderer and version per GL-context identity. 1,001 accesses require three queries instead of 3,003. Dynamic display/memory data remain fresh. Real context recreation is tested.
- `f5fb495ad`: fixed snapshot of shared visual settings, surfaces, notifications and account UI. Merged with the existing ConfigManager migration logic, not substituted for it.
- `236ee6c13`: GLSL120 neutron star and interruptible, time-based spring feedback for menu buttons.
- `e6fa27536`, `0d75cb57f`: framebuffer validity checks select Core/ARB/EXT correctly. When a saved framebuffer was deleted, attribute restoration uses a temporary framebuffer and releases it, preserving the default draw/read buffers. The actual COLOR_ATTACHMENT0 regression failed with GL1282 before the latter fix.
- `95bfd5467`: the original dark ClickGUI reference test explicitly selects its Standard material. The separate global-effects matrix verifies configurable materials. The darkness assertion was retained.

## Validation

Temurin 8u482 / Maven 3.9.15. A clean build and the final full `mvn -o test` run passed: 339 tests, zero failures/errors/skips. Native context tests were enabled with `pulsar.test.glContext=true` and the project's Windows LWJGL library path. Child JVMs additionally passed 32 productive binding checks.

Twenty separate render/statistics programs were exercised: ClickGUI, Arraylist, Accounts, Statistics, SessionInfo, Effects, Fonts, Cleanup, Theme editor, GUI themes, Watermarks, ArmorHUD, Menu, Client settings, Global effects, Notifications, Motion, Combined HUDs, Native controls and Shared resources. Following the final render-state changes, affected ClickGUI/menu/global/HUD/native/resource programs were repeated.

The shared-resource check exercised all three available FBO APIs and two complete release/reopen cycles, each releasing 16 tracked FBOs and 11 programs. No GL errors were suppressed. The initial ClickGUI fixture failure is retained as before-evidence alongside the corrected reference run.

Build logs: `F:/Pulsar-Recovery/analysis/integration-pass-7-build.log` and `integration-pass-7-final-build.log`. Render evidence: project `target/integration-visual/`. The 149 pre-existing local change paths were hashed before and after integration; contents and deletions were preserved. No runtime data entered these commits.

## Remaining acceptance and boundaries

- Offscreen Pbuffer/FBO checks do not establish native-window, fullscreen, shaderpack or ingame correctness. No personal game session or login was started; no push was performed.
- Main-menu production shader quality is currently fixed at level 2. Separate background quality/intensity/speed controls remain a later settings integration; global reduced-motion is honored.
- Work committed after the fixed visual snapshot, and uncommitted work in the separate visual chat, is not part of this pass.
- Performance evidence describes the measured operations, not an asserted ingame FPS gain.
- Event/network completion is not claimed beyond the tested contracts and fixes.

Manual acceptance: exit a local singleplayer world to the main menu; check menu hover/press/release and reopen; verify camera north + accepted module rotation west + W moves west, then returns to camera movement after target/support loss; test local death with LagRange; compare F3 dynamic values; exercise shared materials, overlapping HUD drag, config save/load and restart.
