# Rotation sensitivity-grid correctness (2026-10-30)

## Confirmed bug

`RotationManager.quantize` historically bypassed quantization for a requested
yaw/pitch delta whose absolute value was less than the mouse-sensitivity GCD.
At sensitivity 1.0 in Minecraft 1.8.9, the smallest mouse-angle increment
is about 0.6144°. The old code could emit 0.03° or 0.02° as if they represented
real integer mouse counts. It could not simultaneously provide exact fractional
targets and guarantee the GCD invariant.

LiquidBounce Legacy's `Rotation.fixedSensitivity` snaps relative to the
previous server rotation. This is a **mathematical contract** worth following;
it is not proof that a particular Polar/Intave check flagged.

## Fix

- Extract Java 8 pure `RotationSensitivity.mouseStep` and `snap`.
- Round the requested delta to the **nearest integer mouse count** relative
  to the prior observed/accepted rotation, never to an absolute world-zero grid.
- Use this at the existing `RotationManager.quantize` call, affecting the
  common Aura/AimAssist/Scaffold rotation generation.
- Preserve vanilla pitch `[-90°,90°]` clamp, where the final clamp can
  mathematically yield a smaller last step.
- Adjust tests that incorrectly demanded exact arbitrary fractional yaw/pitch
  targets; nearest-grid tolerance is the correct contract.
- No packet, attack, rendering/body rotation, or MoveFix math is changed.

A rounded trajectory can hold still for a sub-half-GCD target error; the
quantized representation cannot encode that smaller angle. This change does
not add artificial random jitter or claim anticheat immunity.

## Verification gate

Test `RotationSensitivityTest`, `CombatFlowTest`,
`RotationTrajectoryTest`, `RotationModuleBindingTest`,
`RotationModuleBindingChecks`, `RotationOwnerPredicateTest`,
`MoveFixControllerTest` and the full Java 8 Maven suite. Check that
no first-frame/target-switch or cancellation causes an unexpected jump,
that pitch remains clamped, and that AimAssist camera/packet rotations
remain consistent.

## Half-step correction and mathematical test expectations

The pinned [Legacy RotationUtils lines 589-598](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/utils/rotation/RotationUtils.kt#L589-L598)
uses Float subtraction/division followed by `roundToInt`. Kotlin's
[documented half-step rule](https://kotlinlang.org/api/core/kotlin-stdlib/kotlin.math/round-to-int.html)
rounds ties toward positive infinity. The original PR used double arithmetic and
`Math.rint`, whose ties-to-even behavior differs. The added regression failed
on the original PR: -1.5 counts produced -2 instead of -1. The correction uses
Float counts and Java 8 `Math.round(float)`: +0.5 -> 1, -0.5 -> 0,
+1.5 -> 2, -1.5 -> -1, +2.5 -> 3, -2.5 -> -2.

Tests check exact halves, their neighboring Float values, both axes anchored to
the last accepted pose despite a different camera/new owner/rejected candidate,
and the actual `Entity.setAngles` consumer using EntityRenderer's one-count
mouse factor. Existing wrap, target-switch and pitch-clamp checks remain active.

Exact continuous endpoints such as 93.17 degrees cannot generally be demanded
on a .15 degree grid: the nearest point is 93.15. Endpoint expectations therefore
allow at most half a quantum plus Float precision. A continuous 10 degree limit
is 66.666... counts, which rounds to 67 counts or 10.05 degrees; that test now
asserts the exact expected result instead of a loosened inequality. This pipeline
limits the continuous proposal before quantization, so the quantized result may
exceed that proposal by up to half a count. Unnecessary relaxations of the
12 degree/new-scene bounds from the original PR were removed.

## Local Java 8 validation

After integrating merged sprint PR #13, the focused suite passed with 112 tests.
The full suite passed with 724 tests, zero failures/errors/skips, including
native graphics/chat/world-overlay checks (Temurin 8u482, Maven 3.9.15).
Before correction, the new half-step regression failed in the original PR's
six-test sensitivity suite; afterward all eight sensitivity tests pass.
PacketPipeline, AttackScheduler, MoveFix arithmetic and CombatDiagnostics/hook
files are unchanged. No server anticheat conclusion follows from these tests.
See [the playtest protocol](../docs/PLAYTEST-2026-10-30.md).
