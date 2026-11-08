# WAVE06 — ADAPTIVE-AURA02 (stacked Draft; optional Aura consumer only)

## Gate A / immutable base

- At integration start (2026-11-08), `main=aeabb4e9676c30d4b91b080df41b85aa101248b2`, parent [Draft PR #100](https://github.com/YoungJibbit95/Neutron-Client/pull/100) open at `5637d43a5bf7306c8858c26a6fe807010abafef8`, [VIS PR #99](https://github.com/YoungJibbit95/Neutron-Client/pull/99) open. Phase 2 **starts at PR #100's head**; its own PR base must be `codex/wave06-adaptive-math01-20261108`, never `main` until parent is merged and the stack revalidated. VIS #99 owns GUI, HUD, theme and render paths; no overlap.
- Phase 1's GitHub [Java 8 run 42477326102](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42477326102) passed on tested merge SHA `5b3e2978761ed04e6524b5dbb38cec1ad38a742b`: 1,214 tests, no failures/errors, 3 pre-existing native opt-in skips. Ten Adaptive math tests passed. This does not certify Phase 2.

## Path allowlist (Phase 2 only)

- `src/main/java/me/youngjibbit/core/combat/RotationProfile.java` (default-`false` approach flag, no enum/old mode changes)
- `src/main/java/me/youngjibbit/core/combat/RotationSettings.java` (append `Adaptive` only to Aura's `Rot Mode`; hide legacy randomization knobs while Adaptive is selected)
- `src/main/java/me/youngjibbit/module/impl/combat/Aura.java` (flush Adaptive session at approach/settings change, default-off on missing warm-import key)
- `src/main/java/me/youngjibbit/core/combat/RotationManager.java` (candidate consumer and acquisition lifetime, existing final acceptance unchanged)
- `src/test/java/me/youngjibbit/core/combat/AdaptiveAuraIntegrationTest.java`
- `src/test/java/me/youngjibbit/config/AdaptiveAuraConfigTest.java`
- This document.

No `AttackScheduler`, `MoveFixController`, raycast-service implementation, `PacketPipeline`, `net.minecraft`, config manager, visuals, network/timing/movement modules, global profiles or resources are edited.

## Candidate contract, ownership and invalidation

The Aura listener still calls `RotationManager.calculateRotations("aura", ... )` and then `request("aura", AURA_PRIORITY, ...)`. The new math-only `AdaptiveMotionController` is reached only when the new Aura-only `RotationProfile.adaptive` flag is true. Its candidate is unwrapped around the previous accepted rotation and quantized with **existing** nearest mouse-grid rounding before being offered to unchanged priority arbitration. The mathematical candidate is **not** a grant of C03 acceptance, ownership, attack permission or movement acceleration.

An Adaptive session is scoped to RotationState/source, observed live scene (weak player/world/connection identities, packet epoch and connection generation), suspension revision, target **reference identity**, acquisition ordinal and motion sequence. A new target or observed context creates a fresh ordinal. Invalid setting/angle, cancellation, GUI, focus, invalid/suspended scene, wrong player/world/transport, owner loss, rejected rotation and pause invalidate the existing token. A stale same-generation callback cannot silently rearm an invalidated session. `curveMotionTime()` gives one immutable time sample per movement sequence; the controller receives elapsed nonnegative nanoseconds from its first acquisition, avoiding assumptions about the origin of `System.nanoTime()`. Render-duplicate queries cannot draw new noise.

The old configurable `Yaw Speed`/`Pitch Speed` sliders are converted from °/50ms to capped °/s when creating an Adaptive acquisition. Phase 3 alone will introduce dedicated Speed/Variation/Hitpoint presets. Prior randomization, Arsenic hitpoint variation and Bezier settings are not secretly reapplied to Adaptive.

## Config and regression contract

Old `Rot Mode` options retain indices 0-4 (`Basic/Smooth/Advanced/Arsenic Lazy/Arsenic Heuristics`) and **Basic** default. Appended index 5 = **Adaptive**, selected explicitly only. Neither AimAssist nor Scaffold gets Adaptive. Existing config key remains `rot_mode`; no migration of user files. Warm legacy/v2 config with missing `rot_mode` resets any session-only Adaptive selection to Basic; invalid option remains an atomic rejection in existing ConfigManager. Legacy golden traces remain untouched.

New tests: real Aura event/walking/accepted-C03 fixture, target switch and disappearance, repeated PRE queries at 20/60/144/240 frame cadences, rejected transport vs owner acceptance, foreign scaffold owner, GUI/focus/scene invalidation, disable/re-enable, config option indices/roundtrip/missing-key/invalid value. Full existing Java 8 golden and network tests must pass in hosted regression.

## Provenance and runtime gates

Independent Java 8 integration of the Phase 1 controller, no copied Vibe/FDP source or assets. Sources consulted:
- Neutron Arsenic acquisition: [ArsenicAimTrajectory](https://github.com/YoungJibbit95/Neutron-Client/blob/b9b8221bbbc77c1ae6dfd4e9d8e1139069df16c5/src/main/java/me/youngjibbit/core/combat/ArsenicAimTrajectory.java), [RotationManager](https://github.com/YoungJibbit95/Neutron-Client/blob/aeabb4e9676c30d4b91b080df41b85aa101248b2/src/main/java/me/youngjibbit/core/combat/RotationManager.java).
- Vibe GPL-3.0 [AuraRotation](https://github.com/SkidderClub/Vibe/blob/584729c09ceafa8f1541260abc51bed3f97e91f4/src/main/java/dev/vibe/combat/AuraRotation.java) — concept comparison **only**, no code or quantizer copy.
- FDPClient GPL-3.0 [ModernRotationEngine](https://github.com/SkidderMC/FDPClient/blob/07d03078b3fe12ac7278c678f56d0e7f607fbec2/src/main/java/net/ccbluex/liquidbounce/utils/rotation/ModernRotationEngine.kt) — research **only**, different overshoot/acceleration contracts.

At PR creation: **Phase 2 CI = pending**; PR branch/merge SHA and actual Java 8 result must be appended to its PR review notes after the GitHub workflow concludes. Native GL opt-ins, Minecraft client start, focus/GUI/manual rotation feeling, real multiplayer and any server-specific compatibility: **NOT_RUN** unless independently evidenced. No claims of human indistinguishability, undetectability or anti-cheat bypass.

Rollback: close Phase 2 Draft without merging, keep Phase 1 independent; old modes/defaults are unaffected by the appended opt-in.
