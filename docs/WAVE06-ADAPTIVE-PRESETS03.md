# WAVE06 — ADAPTIVE-PRESETS03 (stacked Draft)

## Source pins and scope

2026-11-08 initial verification: main `2bbd9c8ec6cb134628a0f6c00bc1110b364120a4` (PR #100 merged by another actor); PR #101 open Draft at `01547cfa520610683577a10f447fe9ebf000e1d5`, itself based on the same main after PR #100. Phase 2 Java 8 previously passed on merge tree `d572ad79e5977cefaa6ceac683011cb9b2c1e310`: 1,246 tests / 0 failures / 0 errors / 3 native opt-in skips, but this does not certify the new PHASE 3 revision. Use the current #101 branch as this PR's base while it remains open.

This PR appends only three user-facing settings to Aura **when Rot Mode=Adaptive**:
- **Adaptive Speed**: Calm / Balanced / Swift. Default Balanced.
- **Adaptive Hitpoint**: Stable / Organic / Dynamic. Default Stable.
- **Adaptive Variation Strength**: continuous 0–1 slider. Default 0.5.

Settings keys `adaptive_speed`, `adaptive_hitpoint`, `adaptive_variation_strength`. Legacy options retain the exact order, IDs and defaults. Missing keys in older configurations reset to new defaults without editing the saved file. Existing yaw/pitch sliders continue controlling the base physical limits only in Adaptive; the legacy path remains untouched.

## Mathematical and lifecycle contract

- All three speed profiles use exactly the same Java-8 `AdaptiveMotionController` and bounded acceleration/braking/jerk. The effective max angular speed is clamped to 20–720 degrees/s, acceleration 300–4,200 degrees/s² and jerk 9,000–84,000 degrees/s³. Calm multiplies base speed/accel by 0.68/0.72; Balanced by 1/1; Swift by 1.24/1.22. Older speed slider values are not overwritten.
- Variation maps to bounded correlated noise 0–0.28 and curvature 0–0.24. Strength 0 disables these *additional* terms, but the Phase-1 planner's once-per-acquisition seeded profile scale still exists. At default 0.5, noise/curvature equal Phase 2 (0.14/0.12).
- Stable uses the old base hitpoint exactly (within an interior clamp). Organic and Dynamic use immutable per-acquisition phases, continuous two-frequency sine differences and time-based fade-in; no new phases are drawn per frame. Normalized excursion is at most 9% / 20% of each box dimension, respectively; samples are clamped within each currently valid AABB. Invalid coordinates, boxes or time produce no candidate. Changed target/scene/reset destroys the sampler and prevents resurrection of an old token.
- For non-Stable profiles a sampled point must pass the **existing** raycast `hitsPoint` predicate; otherwise the previous valid base point is retained. This check cannot grant an attack. The resulting angular candidate still goes through Neutron's existing `RotationSensitivity.snap`, priority ownership, C03 transport acceptance and the unchanged C02/AttackScheduler path. No new packet, attack scheduler or placement control is added.
- Once a speed/hitpoint/variation change is observed, the old Adaptive acquisition and its immutable limits are invalidated. The same movement sequence may not reacquire a second budget. All old Basic/Smooth/Advanced/Arsenic/AimAssist/Scaffold pathways are unchanged; Adaptive remains opt-in.

## Tests and open gates

- Pure Java-8 JUnit 4: all speed bounds, invalid/NaN input, variation bounds, stable baseline, continuous moving-box point/no teleports, 20/60/144/240 FPS invariance, abrupt steering and opposing velocity, target restart and acquisition token separation.
- Real Aura/Rotation hooks: accepted C03 and no new attack, three speed presets, Dynamic target switch/disable, plus the existing Phase 2 lifecycle/rejection/FPS tests.
- ConfigManager: mode options/defaults, exact keys, roundtrip, older missing-key defaults, atomic invalid-value rejection.
- Existing legacy Golden and Java 8 regression suites MUST stay green. The GitHub PR workflow tests the synthetic PR merge tree; record actual run ID/SHA, 1,246+ tests, 0 failures/errors and existing native skips after completion.
- **OPEN:** GL native opt-ins, launcher/game startup, FPS/frame-time profiling, real moving targets, target occlusion, attacks and server-specific tests. No blanket anti-cheat, “humanlike” or undetectable claim. Third-party Vibe/FDP code/models/assets are not included.

## Files and rollback

Allowed file paths: `AdaptivePresetMotion.java`, `RotationSettings.java`, `RotationProfile.java`, `RotationManager.java`, `Aura.java`, pure math test, real Aura integration test, config test, this doc. Only the optional Adaptive path changes; no VIS, Scaffold, other network modules, local worlds or personal files.

Rollback: close this Draft PR. No merge or release.
