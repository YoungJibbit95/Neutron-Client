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

**GitHub-authored draft; full local Java-8 build and playtest required before merge.**
