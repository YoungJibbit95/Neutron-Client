# WAVE07 - AI-AURA-AURA03 (runtime Aura candidate integration, Draft)

Starting main base: 1338d0a692d00ee65cba017744558aab37bedaba (independent merges of Adaptive #100-102, AI-Core #106, Scaffold planner #108, continuous-click #113, VIS #112).
This PR does not duplicate neural math, Scaffold or VIS implementations.

## Append-only optional settings
Rot Mode adds AI Learned at index 6; original Basic/Smooth/Advanced/Arsenic Lazy/Arsenic Heuristics/Adaptive indices remain 0-5. Basic remains default, existing rot_mode JSON ID unchanged, old config unknown values reject, warm missing rot_mode reverts Basic.
AimAssist and Scaffold do not offer this mode. Legacy/Adaptive settings are hidden while AI Learned is selected, without changing their persistent values.

## Actual production pipeline
Aura PRE target selection and scene checks -> RotationSettings.snapshot().aiLearned -> RotationManager.calculateRotations(aura) -> merged pure NeutronAiCore/Features/Checkpoint -> original RotationSensitivity quantize -> RotationManager.request -> existing priority and locally accepted C03.
Aura POST checks isAiAuraMotionAccepted for the *same* physical motion (an actual AI candidate offered AND accepted under Aura ownership) before the original shared AttackScheduler.tryClick, PlayerControllerMP.tryAttackEntity, tracked C02 and raycast gates. No second packet, attack, scheduler, input, MoveFix, timer or placement engine.
Missing model, invalid target, invisible/dead entity, first acquisition warm-up or invalid context -> no candidate/HOLD and no Aura C03/C02. No hidden Adaptive fallback or camera-only Rotations-off attack. Model is dropped on world/player/connection/account changes; target switches reacquire on a fresh motion; rejected or preempted C03 invalidates offered motion. GUI/focus/disable also invalidates.

## Privacy and licensing
No actual personal/trusted checkpoint exists in this revision. A package-private synthetic JUnit injection method is the ONLY model entry. There is no production loader, automatic recording/training/import/telemetry, user data, Vibe or Gothaj code, third-party weights or assets. SHA-256 core validation is integrity, not provenance/consent. Production loader and account consent require separate reviewed stages.

## CI and rollback
Scope: RotationSettings, RotationProfile, RotationManager, Aura; update only two old append-only option-count assertions; new real config and live Aura/Controller integration tests, this document. All existing legacy golden, Adaptive, Arsenic and packet suites must remain green.
Fresh Temurin Java 8u482/Maven3.9.15, CodeQL, Security and bundle checks are PENDING until actual tested synthetic merge SHA and JUnit totals are captured. NOT_RUN: native Minecraft/GL, multiplayer/server, user consent/training/model-quality, heap/latency profiling.
Independent follow-ups: local opt-in recorder, bounded trainer, and optional Scaffold Adaptive Bridge live approach. No main merge, release, force-push or local checkout edits. Rollback by closing Draft.