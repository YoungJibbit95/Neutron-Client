# Neutron 1.8.9 — LiquidBounce Legacy-inspired MoveFix modes

This patch adds two input contracts without modifying Aura targeting, RotationManager,
packet ordering, the body renderer, or the existing movement mode labels.

| Mode | Input at `Entity.moveFlying` | Movement yaw |
| --- | --- | --- |
| Aus | Original W/A/S/D | Camera |
| Einfach | Old nearest-octant approximation preserving the original length | Module |
| Präzise | Old continuous inverse rotation | Module |
| Rotationsgebunden | Original W/A/S/D | Module |
| Legacy Strict | Original W/A/S/D | Module |
| Legacy Rounded | Sign-based inverse input rotation, rounded to -1/0/1 | Module |

**Legacy Rounded** is intended for normal 1.8.9 keyboard movement after the
`0.98` multiplier and uniform sneak/item slowdowns. It reconstructs the original
key signs, rotates them into the confirmed module-yaw basis, rounds the two
components with ties-to-even and reapplies the original input strength.
`Entity.moveFlying` owns the one-and-only vanilla diagonal speed cap.
Non-vanilla axes above 1 are passed through unmodified.

The old Simple/Precise contracts are preserved, including their known distinction:
*Precise* retains an exact world acceleration direction but can produce analog
inputs a physical W/A/S/D keyboard cannot directly produce. Rounded prioritizes
discrete keys but only approximates the original direction.

Jump physics and sprint decisions remain outside MoveFixController. The existing
jump consumer uses the confirmed module yaw for both new modes, matching the
LiquidBounce Legacy strafe convention; this change does not simulate impulses.

## Validation

The input implementation was checked independently with a Java-8-compatible
standalone harness (36,288 assertions involving discrete inputs and vanilla
acceleration). The repository JUnit tests were extended with a separate
LiquidBounce-style reference implementation and the real `moveFlying` integration
matrix; **the full Maven suite and a live Intave server have not been executed
by this patch author**.

Recommended project commands (use the project's configured Java 8 toolchain):

```powershell
mvn -o '-Dtest=MoveFixControllerTest,MoveFixProductionTest,RotationCorrectionsTest,NetworkMoveFixIntegrationTest,MoveFixConfigTest,SpeedMovementBasisTest' test
```

For isolated debugging, disable Speed, NoSlow, Scaffold and Tickbase when testing
Aura. Compare the same W/A/S/D sequences with Aura Off, MoveFix Off,
Legacy Strict and Legacy Rounded. Record packet yaw, camera yaw, movement input,
sprint/jump states, server corrections and the named anticheat check.

Input plausibility alone does **not** guarantee acceptance by Intave, Polar or
other anticheats, which may check state transitions, packet timing, combat,
rotations, and other movement modules.

Behavioral references:
- LiquidBounce Legacy `Rotation.kt::applyStrafeToPlayer`
- LiquidBounce Legacy `RotationUtils.kt::onStrafe`
- Minecraft 1.8.9 `Entity.moveFlying`
- Intave `KeypressBrancher`, `RotationBrancher`, `BaseSimulator`

LiquidBounce Legacy is GPL-3.0; Intave is source-available under PolyForm
Perimeter. This change implements the input transformation independently.
