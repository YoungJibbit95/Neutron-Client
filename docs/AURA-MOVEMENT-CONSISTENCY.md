# Aura movement and sprint consistency

The reported symptom was an Intave/Polar flag while moving during Aura rotation or
attacks, across multiple settings. No server check name or matching server trace
was supplied. The findings below prove client inconsistencies; they do not identify
the exact server check or establish anticheat acceptance.

## Reproduced causes

Before this change, `EntityPlayerSP.onLivingUpdate` completed movement before
`onUpdateWalkingPlayer` requested the next rotation. Movefix, sprint admission and
the sprint-jump impulse used the last accepted yaw. The next C03 then described that
same displacement using the new yaw. A production-hook probe observed acceleration
at 0 degrees and a C03 at 35.55 degrees in each enabled Movefix mode.

Two related sprint boundaries also needed correction:

- WTap2 Spoof changed sprint in Motion PRE, after the physical movement. The probe
  observed sprint acceleration followed by a non-sprinting movement state.
- Moving Scaffold planning before physics exposed a held-sprint restart: its plan
  stopped sprint, but vanilla sprint admission subsequently enabled it again.
  Auto Jump must also wait for current input and the final sprint decision.

Aura's ordinary successful survival attack already followed the native attack
path: horizontal motion is multiplied by 0.6 and sprint is cleared. A canceled
C02 does not apply that local reduction. Both counterchecks passed before the fix;
there is no additional forced attack slowdown or permanent Aura sprint lock.
Sprinting can legitimately resume on a subsequent movement step.

## Integration

`EventRotation` prepares and arbitrates Aura, AimAssist, Velocity and Scaffold before
living input/physics. A bounded movement frame supplies one yaw and captured Movefix
mode to movement, sprint admission, direct-motion helpers and sprint jumps. Motion
PRE resumes that frame and sends the pose already consumed by physics. Visible
AimAssist applies its camera delta only once.

The preparation is local prediction, not packet acceptance. Rendering and action
authority continue to use accepted rotations. Attacks and placements retain their
existing transport, lifecycle, raycast and scene gates. Ordinary Aura packets keep
their previous queue/replay behavior; strict Scaffold and rotation-return receipts
remain strict. Direct walking calls without a physical preparation retain the
existing Motion PRE fallback.

`MovementReadyEvent` runs after vanilla input/slowdown/sprint decisions and before
physics. WTap Spoof suppresses sprint here without overriding vanilla's eligibility
on recovery. Scaffold retains a valid plan's sprint suppression for that step,
including when PRE placement consumes the plan. Deferred Auto Jump then uses fresh
keys, the final sprint state and the resolved rotation.

A prepared frame is claimed before querying the scene supplier, so a reentrant
walking callback cannot send it twice. If Aura/Scaffold is disabled after physics,
an existing valid smooth-return lease sends the consumed pose as a rotation-only
frame and eases back from that accepted pose. It cannot gain attack/placement rights.

## Regression evidence

`AuraMovementConsistencyTest` runs each case in a fresh Java 8 JVM. It uses the real
living input/sprint branches, `moveFlying`, `jump`, module listeners, walking packet
flow, native attack/controller and an EmbeddedChannel. World storage, input and time
are synthetic. The fixture stops before unrelated horse/base-world physics and
explicitly integrates a free-space displacement; it is not a full game session.

The original nine probes produced seven failures and two passes. The expanded
suite covers all five enabled Movefix modes in eight keyboard directions, first
acquisition, sprint jumps, both Arsenic Aura modes, visible AimAssist, Velocity,
both Scaffold approaches, sprint suppression, Auto Jump, rejected C03, recursive
walking and disable during Motion PRE. All 23 cases pass after the corrections.
The combined focused run, including existing sprint, NoSlow, Velocity, Scaffold
and smooth-stop tests, passed 47 tests with no failures, errors or skips on
Temurin 8u482.

The final broader run passed 652 tests across 105 core, config and movement-helper
suites with zero failures, errors or skips. All 6751 source/POM hashes and the
18 pre-existing foreign WIP file hashes remained unchanged. Unrelated in-progress
account/UI, targeting-performance and resource-pack tests are outside this task's
validation claim. Local detailed logs and source/hash receipts are retained under
`target/aura-movement-20261102`; they are not committed.

## Gameplay follow-up

Movefix `Aus` deliberately retains camera-relative input while silent rotation is
active. These changes do not make that combination consistent with server yaw.
Speed/timer modes can independently change vanilla displacement; this fix does not
certify every combination of movement modules or packet-delay features.

For an attributable retest, record the revision, profile, enabled modules, Movefix
mode and exact server check. Compare rotation without attacks, attacks while still,
all eight moving directions and sprint jumps, then add WTap/Scaffold separately.
The existing four test profiles remain available; their names do not imply proven
Intave/Polar acceptance or indistinguishability from a human player.
