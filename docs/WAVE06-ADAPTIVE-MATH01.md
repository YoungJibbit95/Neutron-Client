# WAVE06 / ADAPTIVE-MATH01 — independent rotation candidate math

Status: isolated Java 8 pure-math implementation, **not an enabled Aura mode**.
No Minecraft, packets, settings, attack or visual code is changed by this wave.

## Gate A / source comparison (2026-11-08)

- Initial main `b9b8221bbbc77c1ae6dfd4e9d8e1139069df16c5` included merged PRs #95 and #96 (PR #96 merged as `178b9a2a3957864878d939c29d04017e706ccf23`). While researching, VIS PR #98 merged and main moved to `aeabb4e9676c30d4b91b080df41b85aa101248b2`. This task must start from a freshly verified main SHA, not an old pin.
- PR #98 changed only VIS-owned HUD/island/GUI/test files. A newly opened PR #99 edits HUD, TargetHUD, theme and GUI helpers; no overlap with the three paths in this task. Neither PR's files are modified.
- Neutron [ArsenicAimTrajectory.java](https://github.com/YoungJibbit95/Neutron-Client/blob/b9b8221bbbc77c1ae6dfd4e9d8e1139069df16c5/src/main/java/me/youngjibbit/core/combat/ArsenicAimTrajectory.java) uses per-acquisition seeded `AimCore` and a motion-sequence cache. [ArsenicMotionCurve.java](https://github.com/YoungJibbit95/Neutron-Client/blob/b9b8221bbbc77c1ae6dfd4e9d8e1139069df16c5/src/main/java/me/youngjibbit/core/combat/ArsenicMotionCurve.java) uses acceleration/braking with a periodic speed variation. [RotationManager.java](https://github.com/YoungJibbit95/Neutron-Client/blob/b9b8221bbbc77c1ae6dfd4e9d8e1139069df16c5/src/main/java/me/youngjibbit/core/combat/RotationManager.java) owns winner, C03 acceptance, quantization, scene and movement-window semantics. Existing classes are unchanged.
- Vibe [AuraRotation.java](https://github.com/SkidderClub/Vibe/blob/584729c09ceafa8f1541260abc51bed3f97e91f4/src/main/java/dev/vibe/combat/AuraRotation.java) has axis acceleration/braking and quantizes by truncation; neither its source nor its differing quantizer was ported. Vibe GPL-3.0; its Scaffold includes separately documented unlicensed private Gothaj material and is **BLOCKED** for reuse.
- FDPClient [Backtrack.kt](https://github.com/SkidderMC/FDPClient/blob/07d03078b3fe12ac7278c678f56d0e7f607fbec2/src/main/java/net/ccbluex/liquidbounce/features/module/modules/combat/Backtrack.kt) and [TimerRange.kt](https://github.com/SkidderMC/FDPClient/blob/07d03078b3fe12ac7278c678f56d0e7f607fbec2/src/main/java/net/ccbluex/liquidbounce/features/module/modules/combat/TimerRange.kt) are **research only** for later network waves; FDPClient GPL-3.0. This math PR includes no FDP code, queue or timer paths.

**Provenance decision:** MATH_CONCEPT_ONLY for Vibe acceleration/braking, independent implementation with Java 8 standard library. RESEARCH_ONLY for FDP; BLOCKED_THIRD_PARTY for Gothaj Scaffold. No upstream file, asset, license-incompatible code, shader or game binary is copied. Licensing/distribution of the complete client remains a separate gate.

## Deterministic motion contract

- `AdaptiveMotionController` has no dependency on Minecraft. Its `Acquisition` key contains owner/scene/target **object identities**, connection epoch and strictly increasing acquisition ordinal. Epoch/ordinal high-water tombstones prevent old callbacks from rearming after reset. Changing a target without a new ordinal holds safely; consumer must acquire a new generation.
- The first valid sample establishes seeded parameters but does not move. Subsequent distinct motion sequences advance once, even if `advance` is also called during render/POST. Same-sequence queries return the original immutable result unchanged; older sequences HOLD.
- Motion time uses monotonic nanoseconds. Zero time delta produces HOLD. Per-step time is capped at 75 ms; a gap of 250 ms or more invalidates the active acquisition, rather than replaying withheld time.
- Yaw errors use shortest-angle wrap; pitch targets and results are clamped to [-90, 90] degrees. A common `dt`, progress and correlated noise sample serve both axes. Limit units are °/s, °/s² and °/s³. A braking envelope based on `sqrt(2*a*distance)` reduces desired velocity as the error closes; desired acceleration changes are jerk-limited. Residual-distance clamp prevents sign-preserving overshoot. At exact settlement the planner emits HOLD and clears internal momentum; this terminal safety clamp is not a physical jerk proof.
- Per-acquisition seeded speed, acceleration and braking scales and a bounded nonperiodic lateral bend vary the candidate path. A first-order low-pass random process changes only on motion progression; no per-render RNG, periodic infinite jitter or target-crossing random walk.
- Invalid angles/time, revoked authorization, focus/GUI/disable/world/connection or packet-rejection (as supplied by the future consumer) must invalidate the matching acquisition. Stale callbacks cannot invalidate the newer owner. The caller must **never interpret a mathematical candidate as an accepted C03 or permission for C02**. `RotationManager` retains the final sensitivity grid and action authority.

## Focused verification

Test class: `src/test/java/me/youngjibbit/core/combat/AdaptiveMotionControllerTest.java` (JUnit 4). Ten deterministic tests cover zero error, wrap/pitch bounds, nonfinite/time invalidation, 64-seed convergence and bounded derivatives, 64-seed profile diversity, correlated noise, render schedules at 20/60/144/240 fps, tracking/reacquisition, stale owner/callback/cancel, and pause spikes.

- Test-first RED: test class compiled in isolation against the old tree without `AdaptiveMotionController` -> missing-symbol compilation errors (expected).
- Provisional isolated local check: OpenJDK 21 `javac --release 8` produced Java class major version 52. All 10 test methods passed using a *local JUnit-API compatibility shim*; this is **not** an actual JDK 8 / Maven / JUnit run. The compatibility shim is **not** committed.
- Authoritative gate: repository Java 8 PR regression GitHub workflow, JUnit 4 Maven tests and tested commit/merge SHA must be reported separately once run. Legacy `RotationLegacyGoldenTest` remains untouched. Native offscreen tests (three opt-ins), actual Minecraft, UI and server: **NOT_RUN** until performed.

## File allowlist and follow-ups

Allowed changes only: `src/main/java/me/youngjibbit/core/combat/AdaptiveMotionController.java`, `src/test/java/me/youngjibbit/core/combat/AdaptiveMotionControllerTest.java`, this document. No existing modes, defaults, config IDs, rotation owner, attack scheduler, packet pipeline, movement or visual paths change.

**Next gate (not implemented here):** WAVE06 ADAPTIVE-AURA02 is a separate stacked Draft PR only after verified Phase 1 CI. It must gate the controller by current Motion/Scene/Connection ownership; distinguish C03 candidate from transport-accepted rotation; retain current Neutron/Arsenic defaults and golden traces. Speed/Hitpoint/Variation presets are another independent subsequent PR. Backtrack/LagRange/Tickbase and Velocity/Speed remain deferred to their own #96-aware research and reproduction gates.
