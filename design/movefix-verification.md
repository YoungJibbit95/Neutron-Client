# MoveFix verification (2026-10-27)

Ordinary movement uses the correct rotation signs and vanilla speed cap. The modes have different direction contracts:

| Mode | World movement direction | Sprint-jump impulse |
| --- | --- | --- |
| OFF | Original camera and keys | Camera forward |
| PRECISE | Preserves camera-space intent continuously | Camera forward |
| SIMPLE | Nearest of eight directions relative to accepted module yaw; up to 22.5 degrees of heading error | The same octant approximation of camera forward |
| ROTATION_BOUND | Original keys relative to accepted module yaw | Accepted module yaw forward |

For strafe `s`, forward `f`, yaw `y`, and acceleration factor `a`, vanilla `Entity.moveFlying` adds

```text
motionX += a * (s*cos(y) - f*sin(y)) / max(1, hypot(s, f))
motionZ += a * (s*sin(y) + f*cos(y)) / max(1, hypot(s, f))
```

Inputs below vanilla's squared-length threshold of `1e-4` produce no acceleration. For other inputs, write the rotation above as `R(y)`. Preserving the original world vector requires `u' = R(cameraYaw - acceptedYaw) u`, since `R(acceptedYaw) u' = R(cameraYaw) u`. Rotation preserves length, so vanilla's cap and the strength of sneak/item input stay intact. PRECISE implements this transform. SIMPLE chooses the octant with the greatest normalized dot product and retains the original input length before vanilla applies its cap. SIMPLE produces float input values, including scaled cardinal values; it does not produce literal keyboard-only {-1, 0, 1} inputs.

Minecraft's float trigonometric lookup table and rounding introduce small differences from exact real-number arithmetic. The tests allow for that error. The formulas describe acceleration and the sprint impulse, not the direction of total velocity after inertia, collisions, water, or other movement modules.

`RotationCorrections` runs at the actual `Entity.moveFlying` and `EntityLivingBase.jump` consumers. It changes the movement event's input/yaw, or the sprint impulse yaw. It leaves the camera and accepted packet angles alone. A north-facing camera (180 degrees), accepted west-facing module yaw (90 degrees), and W accelerate west in ROTATION_BOUND. Body rendering is a separate consumer.

Correction selects the confirmed Aura or Scaffold owner for the current player, world, and connection. A pending request grants no ownership. During PRE, consumers use the preceding accepted rotation; after completion they use the current accepted result. Cancellation, delayed movement without acceptance, incompatible outbound rotations, module release, and scene/connection changes remove correction eligibility. "Accepted" means local transport acceptance; it does not mean server approval.

Two defensive defects were reproduced and fixed: subtracting opposite extreme finite float yaws could overflow before wrapping; an invalid observed packet rotation could poison the next smoothing base. The first fix wraps before subtracting. The second chooses the camera as the next smoothing base when the observed rotation is invalid, while preserving the invalid observation so packet completion still rejects it. Neither defect changes the normal-range mode contract.

Verification uses Java 8 and offline Maven. `MoveFixControllerTest` checks the numerical transform against vanilla acceleration, all WADS combinations, yaw wrap, analog strengths, and sprint impulse strength. `MoveFixProductionTest` runs 12,096 cases through real `moveFlying` for both owners, all modes, camera/module quadrants, and normal/sneak/item/combined strengths after vanilla's .98 damping. It also checks real sprint jumps for 336 owner/mode/heading combinations. Assertions cover acceleration magnitude/direction, silent camera, unchanged accepted yaw/owner, and absence of packets emitted by correction. A separate JVM keeps the legacy static-final client fixture stable under JIT.

Related suites cover ownership handoffs and release, rejected/rewritten/delayed packets, scene lifecycle, idempotence, module setting bindings, direct velocity consumers, and saved mode settings:

```powershell
mvn -o '-Dtest=MoveFixControllerTest,RotationCorrectionsTest,MoveFixProductionTest,RotationBehaviorHookTest,NetworkMoveFixIntegrationTest,SpeedMovementBasisTest,RotationModuleBindingTest,MoveFixConfigTest' test
```

These are deterministic synthetic client and embedded-transport tests. No live server, full rendered gameplay, or anti-cheat compatibility testing was performed. Correct movement mathematics cannot guarantee that a server will accept a modified client or avoid flagging it.
