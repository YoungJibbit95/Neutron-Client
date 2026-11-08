# WAVE06 — SCAFFOLD-RESEARCH01 (Neutron / Vibe / FDPClient)

**Status:** RESEARCH_ONLY — no Scaffold runtime changes, no imported client code or assets. Prepared 2026-11-08 at Neutron `main=2bbd9c8ec6cb134628a0f6c00bc1110b364120a4`. PR #100 (Adaptive pure math) had merged; PR #101 (Adaptive Aura) and #102 (Adaptive presets) are independent open Draft branches. Neutron default and VIS files are out of scope.

## Sources pinned to exact commits and blob SHA

| Source (immutable browser link) | Blob SHA | What was reviewed |
|---|---|---|
| [Neutron Scaffold](https://github.com/YoungJibbit95/Neutron-Client/blob/2bbd9c8ec6cb134628a0f6c00bc1110b364120a4/src/main/java/me/youngjibbit/module/impl/world/Scaffold.java) | `66c29fc8d1d7f3727fc90d863468836ef655babe` | Base architecture and PRE/POST lifetime |
| [Neutron placement service](https://github.com/YoungJibbit95/Neutron-Client/blob/2bbd9c8ec6cb134628a0f6c00bc1110b364120a4/src/main/java/me/youngjibbit/core/world/ScaffoldPlacementService.java) | `bb5ebe4449b30e9a04b93f6ae2d3ebf3aaa7888a` | Validations, checked slot, face/stack intent |
| [Neutron placement target](https://github.com/YoungJibbit95/Neutron-Client/blob/2bbd9c8ec6cb134628a0f6c00bc1110b364120a4/src/main/java/me/youngjibbit/core/world/PlacementTarget.java) | `e2bc529e9f87e5395fe47c8e010c7d4e7e66bc06` | Immutable identity, state and stack snapshot |
| [Neutron rotation plan](https://github.com/YoungJibbit95/Neutron-Client/blob/2bbd9c8ec6cb134628a0f6c00bc1110b364120a4/src/main/java/me/youngjibbit/core/world/ScaffoldRotationPlan.java) | `a0da27a68e837e1a7969ef6fc59e1752ee3e9e81` | Face-local rotations, Godbridge search |
| [Neutron Arsenic controller](https://github.com/YoungJibbit95/Neutron-Client/blob/2bbd9c8ec6cb134628a0f6c00bc1110b364120a4/src/main/java/me/youngjibbit/core/world/ArsenicScaffoldController.java) | `fbf15e9a1f8f84a668e41a69333a8942a785655f` | Predictive face, acceptedPlan, input/Eagle |
| [Neutron Arsenic docs](https://github.com/YoungJibbit95/Neutron-Client/blob/2bbd9c8ec6cb134628a0f6c00bc1110b364120a4/docs/ARSENIC-ROTATIONS-SCAFFOLD.md) | `1ae2dbcc42da834d535a7849343f3660407301d5` | Upstream MIT/provenance and contracts |
| [Vibe ScaffoldModule](https://github.com/SkidderClub/Vibe/blob/584729c09ceafa8f1541260abc51bed3f97e91f4/src/main/java/dev/vibe/module/impl/world/ScaffoldModule.java) | `21285ce3a68b33f458182853ef55d46611f67bc4` | Mixed provenance, **BLOCKED FOR COPY** |
| [Vibe Gothaj provenance](https://github.com/SkidderClub/Vibe/blob/584729c09ceafa8f1541260abc51bed3f97e91f4/LICENSES/GOTHAJ-SCAFFOLD.md) | `6ed41c91bea8bb5afdf591c58a9d24c09aba3416` | Decompiled private source & assets, no upstream license |
| [FDP Scaffold](https://github.com/SkidderMC/FDPClient/blob/07d03078b3fe12ac7278c678f56d0e7f607fbec2/src/main/java/net/ccbluex/liquidbounce/features/module/modules/player/scaffolds/Scaffold.kt) | `ae20e0188739482052312cebad28008770a4eac1` | Multi-mode Kotlin, **RESEARCH_ONLY** |
| [FDP Tower](https://github.com/SkidderMC/FDPClient/blob/07d03078b3fe12ac7278c678f56d0e7f607fbec2/src/main/java/net/ccbluex/liquidbounce/features/module/modules/player/scaffolds/Tower.kt) | `542bb2094e824469f6ae890e86f693eef7c107e7` | Separate vertical movement family, **RESEARCH_ONLY** |

Vibe's current pinned source file really lives in **`module/impl/world/ScaffoldModule.java`**, whereas its older `LICENSES/GOTHAJ-SCAFFOLD.md` mapping says `module/impl/ScaffoldModule.java`. This documentation/path mismatch is not evidence of a clean provenance chain. Source blobs are pinned to avoid conflating changing `main` branches. No copying of code from any source took place.

## Current Neutron invariants (verified in code)

1. `Scaffold Approach` is exactly `["Neutron","Arsenic"]` with default **Neutron**, index 0; the two current modes and their config IDs/defaults must remain unchanged. `Arsenic` is opt-in, uses the separate `ArsenicScaffoldController` and POST placement.
2. `Scaffold.handleMotion` obtains a valid scene, connection and physical `RotationManager.getMotionSequence()` before planning. Legacy Neutron PRE can place in the user's configured `Place Timing`, while its POST operates only on `frameTarget` from that PRE and the same sequence; it cannot start a new search in POST.
3. `PlacementTarget` binds **destination/support/EnumFacing/hit position** and selected/actual slot, support state, world, player and stack snapshot. `withHit` may refine only the hit vector. The service's `planFace`, `isValid`, `alignAcceptedHit`, `stillNeeded`, and checked `getBlockSlot` preserve reach, block-state, collision, world border, loaded chunk, entity/slot and raycast requirements. `alignAcceptedHit` requires **locally accepted** scaffold rotation and exactly the same support/face.
4. Existing `RotationManager` is the only priority/quantization owner. `Scaffold` owns priority 30 (higher than Aura), `MoveFixController` controls physical input/rotation basis, and the C03 acceptance/ticket gate is distinct from a proposed candidate. A rejected/foreign movement cannot promote a placement. No new C08 attempt may be emitted from an invented POST face/stack.
5. `ArsenicScaffoldController.prepare` offers a bounded candidate once per motion sequence; `acceptedPlan` verifies the PRE support/face and accepted rotation; Eagle modifies only fresh input and there is an explicit lifecycle/epoch recheck after callbacks. Legacy `TowerController` and timer/slot resources belong to their existing owners.

**Integration consequence:** A third approach must live behind these same contracts, not invent a second placement service, timer, inventory spoof, attack pipeline or packet bypass.

## Comparison matrix (features are not proofs of server compatibility)

| Domain | Neutron / Arsenic present behavior | Vibe source and provenance | FDPClient source and provenance | Independent proposed improvement |
|---|---|---|---|---|
| Support / face | Neutron bounded grid search; Arsenic predictive support/face, accepted `planFace` | Normal/Telly, ray-based face, bridging heading; parts mapped to private Gothaj | `SearchMode=Area/Center`, multiple movement-specific search strategies | Bound candidate count, one-tick predicted feet, prioritize feasible support/face while retaining acceptance checks |
| Rotation | Shared `RotationManager` and sensitivity grid; immutable pre-plan | Vibe acceleration and named rotation profiles use a distinct MoveFix | RotationSettingsWithRotationModes, high priority, timing variants | One bounded candidate through existing Neutron rotation owner; no duplicate GCD/MoveFix |
| Edge / Eagle | Arsenic hard-edge safety and optional cadence; Neutron fresh input sneak | Sneak/SafeWalk and movement helpers carry Gothaj provenance | Eagle Normal/Silent/Off and edge distance/timing | Reuse Neutron fresh-input ownership, never alter permanent key state or double slow motion |
| Slot / blocks | Scoped inventory and slot checks in `ScaffoldPlacementService` | Special inventory and spoof helper provenance blocked | Off/Pick/Spoof/other modes; separate slot flow | No new spoof; use checked selected/approved block slot and existing stack snapshot |
| Vertical / movement | Legacy NCP/Watchdog tower and sprint/keepY controls; Arsenic separated | Timer/NCP/Intave tower families and patched input hooks | `Tower.kt` has independent jump/motion/teleport/timer policies | **Defer** all tower, timer, packet and movement modes into later single-purpose PRs |
| Status / rights | Neutron GPL-3.0, Arsenic upstream MIT attribution documented | Vibe GPL-3.0 repository but private decompiled Gothaj source no license | FDP GPL-3.0; README warns other build-time sources may lack rights | Mathematical ideas from public descriptions only, independently authored code and tests |

### Hard legal block

At pinned Vibe `LICENSES/GOTHAJ-SCAFFOLD.md` (blob `6ed41c91bea8bb5afdf591c58a9d24c09aba3416`), numerous Scaffold implementation portions, math/rotation/inventory/movement helpers, UI counter, font and shaders are expressly attributed to a **private decompiled Gothaj JAR with no evidenced upstream license**. Its listing includes hooked `Minecraft.runTick`, `EntityPlayerSP.onUpdateWalkingPlayer`, `MovementInputFromOptions`, `NetworkManager.sendPacketSilentEvent`, and renderer paths. **BLOCKED_THIRD_PARTY:** do not copy the Vibe Scaffold implementation or helpers, assets, fonts, shaders, hook patches or data, regardless of Vibe's top-level GPL-3.0. The overlapping mathematical goals may be independently specified, but no Vibe/Gothaj source migration can be approved from this report.

FDPClient's own direct Scaffold/Tower Kotlin files appear under GPL-3.0 and its README explicitly limits that license to code in its clean repository, not unlicensed external build-time sources. This is **RESEARCH_ONLY** for architecture comparison: an own Java-8 implementation is required; no blind Kotlin module port or new global packet pipeline.

## New optional approach design — SCAFFOLD-APPROACH02

**Name chosen for a later reviewed implementation:** **Adaptive Bridge**. Append `Adaptive Bridge` to `Scaffold Approach` at index 2. Existing `Neutron`/ `Arsenic` indices and default `Neutron` remain identical. Not enabled by config migration, mode inference or absent key. This report **does not implement it**.

### Proposed minimal first code PR

- Add a small pure Java-8 `AdaptiveBridgePlanner` (independent mathematical geometry) with an input-only snapshot: player/feet/previous accepted yaw, fresh physical direction and bounded velocity, target block level, finite reach, candidate support/face references and a monotonic motion sequence. The planner returns at most **one immutable candidate** or `NO_PLAN`, not a packet/action. Use one-tick forward/side/diagonal prediction with a bounded displacement, e.g. ≤0.6 block horizontal; never extrapolate across large position jumps, unloaded chunks or invalid transforms.
- Reuse `ScaffoldPlacementService.findTarget/planFace` with **bounded search** (a proposed ≤24 candidates only if the host service can validate them), and rank only available valid faces by distance, geometry alignment and rotation to the previous accepted yaw; no random unvalidated support or arbitrary hitpoint outside the selected face. If the service API cannot represent this cleanly, add a **small service adapter in a separate reviewed diff**, rather than making the planner mutate world/slot state.
- In Scaffold's existing **PRE**, capture exactly one `PlacementTarget` intent along with world/player/connection/packet epoch, suspension revision, original stack and selected slot, PRE support/face, session generation and `RotationManager.getMotionSequence()`. Offer planned yaw/pitch via `RotationManager.request("scaffold",SCAFFOLD_PRIORITY,...)`; **do not place until the current C03 pose is locally accepted**.
- In **POST** use only that PRE intent. Reject if scene, world, placement need, support state, player, inventory, slot, stack, reach, raycast, owner or sequence changed. The only permitted refinement is `PlacementTarget.withHit` via `alignAcceptedHit` **for the original support/face**. No alternative support/face/stack, no second packet allowance, no transmuted accepted C03 receipt. Placement attempt goes through existing `tryPlace(false)` and `ScaffoldPlacementService` with its admission/action budget.
- Optional edge / Eagle in this first quality bundle **only through Neutron's existing MovementInputEvent and current scene/generation gates**, never direct KeyBinding writes. Prevent duplicate sneak slowdown, budget one decision per fresh input, and stop after disable, respawn, world switch, lost focus or owner loss. Keep Tower/Timer/Sprint global behavior untouched; treat ambiguous cross-module combinations as BLOCKED until tested.
- GCD sensitivity, MoveFix, rotation return after accepted last packet and camera/physics basis stay under `RotationManager`/existing `RotationReturnSettings`. Distinguish a visual preview or held pose from an accepted C03. Use current `AutomationEligibility` and pipeline to authorize any C08.

**File allowlist for later PR:** new pure `src/main/java/me/youngjibbit/core/world/AdaptiveBridgePlanner.java`, its JUnit tests, narrowly amended `Scaffold.java`, an integration/config test and one gate document. `ScaffoldPlacementService` may only be touched in a separate explicitly scoped follow-up if its current immutable intent API is proven insufficient. No visual, external client, movement or packet classes.

## Reproducible acceptance matrix before merge

1. **Pure math:** 0/90/180/270-degree headings; forward/back/side/diagonal bridge, stationary and slow/fast turn, negative coordinates, block borders and steep pitch, ±180 wrap, invalid/NaN state, discontinuous teleport, no loaded support; bounded candidate list and deterministic ordering.
2. **Placement/transport:** PRE face/stack frozen, POST equal or allowable `withHit` only; accepted C03 causes exactly one checked C08 placement; canceled/replaced/foreign C03, wrong owner, invalid raycast, support replaced, late inventory change, slot not accepted, action budget exhausted and POST replay all yield **no placement**. Existing PRE-only `Neutron` behavior must remain exactly unchanged.
3. **Lifecycle:** disable/re-enable, GUI/focus loss, WorldEvent unload/reload, respawn, reconnect, client suspend, correction packet, same numeric entity ID with new identity, changed `MoveFix` owner. Stale callback cannot release a new valid generation or retain sneak/sprint.
4. **Config:** old `Scaffold Approach` values/indices/default + old key, invalid mode atomic rejection, explicit opt-in roundtrip and older missing setting. Existing Arsenic/Neutron golden signatures.
5. **Performance:** no copy of player/world/packet queues; bounded ray tests, no allocations proportional to scene contents, test 20/60/144/240 FPS invariance, same-sequence cache and elapsed-time caps; actual Java-8 profiling pending.
6. **Runtime:** authoritative Java 8 hosted CI and real Minecraft native OpenGL, user key movement, first/third person, block collision, server acceptance and inventory race scenarios on recorded environment. No general anticheat bypass, human indistinguishability or release claim.

## Sequencing, outcomes and open gates

- **This Draft PR**: report and architecture decision only, no gameplay code. Review source/provenance and confirm the exact first-bundle scope. Runtime tests **NOT_RUN** because there is no implementation.
- **SCAFFOLD-APPROACH02**: separate next Draft PR only *after research review*, with Java-8 pure planner and fixtures, then live bridge hook in a bounded diff if the PRE/POST and ownership fixtures pass. Keep unmerged until full CI and user native/server gates.
- **SCAFFOLD-TOWER03 / MOVEMENT04**: distinct later PRs only after proven need, licensing review and interference tests. Backtrack/LagRange/Tickbase and Velocity/Speed remain unrelated later phases.

**Rollback:** close this report draft. No main push, automatic merge, release, local checkout/runtime-file change, or Gothaj import.
