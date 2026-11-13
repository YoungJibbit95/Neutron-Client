# Demise geometry adapted for Neutron

The geometry-only adaptation below is historical. The later
[Adaptive Demise engine and Neutron Noise option](RELEASE-OPERATIONS-AURA-20261113.md)
complete Aura smoothing while retaining this geometry and ownership contract.

Reference revision: [`YoungJibbit95/DemisePrivate@e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`](https://github.com/YoungJibbit95/DemisePrivate/commit/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb), supplied by the maintainer for this integration on 2026-11-10.

## Source mapping

| Reference source | Examined behavior | Neutron adaptation |
| --- | --- | --- |
| [`features/modules/impl/player/Scaffold.java`](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/Scaffold.java) | GodBridge's reverse movement heading, a cell-side-dependent 45-degree offset for cardinal movement, and 75.7-degree static pitch | `ScaffoldRotationPlan.godbridgeMovementYaw`, `ScaffoldPlacementService.movementGodbridgeHit`, and the first Godbridge acquisition in `Scaffold` |
| [`utils/player/MoveUtil.java`](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/MoveUtil.java) | Movement direction and nearest 45-degree cardinal/diagonal classification | Derive heading from Neutron's captured raw forward/strafe input and camera yaw; the movement correction controller retains ownership of physical input |
| [`features/modules/impl/combat/KillAura.java`](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/combat/KillAura.java) | Straight aim point: horizontal box centre and eye height clamped above the lower 45% of the box; update rotation only after losing a useful hit | `AdaptivePresetMotion.aimPoint`, the Adaptive Aim Point selector, and Adaptive Keep Valid Aim inside the existing `RotationManager.adaptiveRotations` pipeline |
| [`utils/player/rotation/RotationManager.java`](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationManager.java), [`RotationHandler.java`](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationHandler.java) | Per-axis speed bounds, relative correction, motion continuity and settled rotation | Neutron retains its existing bounded, motion-clocked Adaptive engine, sensitivity rounding and ownership arbitration; the new valid-hit hold advances that engine with zero angular error |

The implementation translates these small geometry and retention rules into the existing Java 8 architecture. It does not add another client runtime or event bus.

## Godbridge behavior

The initial heading comes from the intended world-space movement direction. Cardinal movement selects a diagonal view using the player's side of the current cell; diagonal movement uses the opposite diagonal heading directly. The desired heading is rounded to a 45-degree grid and the first pitch preference is 75.7 degrees.

The preference must intersect the actual selected support face. Neutron projects it onto that face, makes the smallest bounded correction needed by its bounds, and checks the resulting point against the player's real mouse-sensitivity grid. Unusual supports and recovery use the existing validated geometric fallback. A theoretical angle never authorizes placement.

After acquisition, the existing valid-ray retention takes priority. Movement between cells does not by itself reacquire another canonical pose. The prior PRE/C03/POST checks, current inventory identity, placement cadence, lane correction and recovery limits remain authoritative.

## Adaptive Aura controls

- **Adaptive Aim Point:** `Straight` (default), `Nearest`, or `Legacy`. Straight uses the horizontal centre and a torso-to-eye vertical interval. Nearest uses the closest interior point. Legacy preserves the prior anchor choice.
- **Adaptive Keep Valid Aim:** enabled by default. A ray must still hit the exact current target within the existing acquisition range and obstruction policy. A target moving away from that ray resumes correction. Target replacement, ownership changes, scene transitions and suspension retain the existing invalidation rules.

During a useful-ray hold, the same engine consumes the real motion sequence with zero angular error. It clears residual turn momentum and advances its time boundary, so waiting on a target cannot accumulate extra rotation credit or manufacture an attack. The attack scheduler and accepted-pose ray checks remain separate consumers.

New fields participate in normal configuration serialization. Importing an older config without these fields restores the new-mode defaults, while the non-Adaptive modes keep their previous settings and behavior.

## Regression coverage

- `DemiseGodbridgeGeometryTest`: cardinal side selection, diagonal continuity, negative coordinates, and native face validity at mouse sensitivity 0, 0.5 and 1.
- `DemiseAdaptiveAimGeometryTest`: upper-body anchor bounds, eye-height adaptation, nearest interior points and invalid-input rejection.
- `AdaptiveAuraIntegrationTest`: an accepted target ray stays fixed under strong Dynamic hitpoint variation; losing that ray resumes correction through the ordinary C03 path.
- `AdaptivePresetConfigTest`: new option defaults, visibility, serialization and missing-key reset.
- Existing Adaptive Bridge rotation, physical straight/diagonal placement, clutch, lifecycle, Movefix and timing-coexistence checks remain required.

Final execution results are recorded in the run's PR validation section. Angle-speed settings continue to operate before the ordinary nearest mouse-count rounding; the native grid's rounding tolerance still applies.
