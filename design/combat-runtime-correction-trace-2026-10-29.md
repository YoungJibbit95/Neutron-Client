# Combat runtime correction trace — 2026-10-29

## Evidence and scope

On a test server, both Aura Legacy Strict and Legacy Rounded are reported to trigger
warnings while moving/attacking or receiving knockback. The client console previously
showed long bursts of `S08_RECEIVED_NOT_A_FLAG`, including some after Aura ownership
was released. The exact server-side anticheat check and selected mode per earlier
console event are **not known**, so these data cannot prove that MoveFix caused a flag.

AimAssist does not participate in Legacy MoveFix: `RotationCorrections.current()`
selects only Aura/Scaffold. AimAssist-only failures are therefore a strong comparison
case, not evidence that the same MoveFix formula is involved.

**This patch changes diagnostics only, not game behavior.** The existing
`CombatDiagnostics` module must be enabled (F9); `Console Attack Log` must be
explicitly enabled for exportable text lines (default OFF).

## New observations

- `moveRaw=S/F@yaw`: original `Entity.moveFlying` arguments and original
  camera yaw, before event listeners and the MoveFix adapter.
- `moveApplied=S/F@yaw`, `moveFriction`: final input and yaw **after** those
  listeners/adapter, immediately before vanilla acceleration. Recorded once
  at the real local player consumer, not as a separate gameplay event.
- `moveAgeTicks` and `moveAgeMs`: age of that last movement sample when an
  attack, correction, velocity or F8 marker is logged; a sample can be stale.
- `auraConfiguredMoveFix`: saved Aura mode if enabled, not proof that Aura owned
  a specific tick. Combine it with `owner`.
- `motionXYZ`, `onGround`, `sprint`: local velocity and state at the logging
  instant, **not** the server's simulated velocity.
- `S08_RECEIVED_NOT_A_FLAG`: packet's raw position/angles and relative flags,
  plus a **receipt-time preview** of how they would combine with the current
  local pose. `recvDistance` is a preview, not a measured applied setback;
  receipt can precede delayed application.
- `S08_APPLIED_NOT_A_FLAG`: exact local position/yaw **before and after vanilla
  S08 application**. `appliedDistance` is the measured client-side positional
  jump at that instant, not a server-side violation level.
- `S12_SELF_VELOCITY_RECEIVED`: incoming velocity packet in blocks/tick
  (int fields divided by 8000), only if its entity ID is the local player;
  receipt might precede application or cancellation.
- `KB_SELF_APPLIED_POST`: effective knockback vector reported by the
  existing post-application event, only if the packet was not canceled.
- `ATTACK_PRE_ATTEMPT` and `ATTACK_LOCAL_ACCEPTED`: local client observations,
  not server acknowledgements. `F8_MANUAL_MARK` is a user-generated marker.

Only the opt-in diagnostics module and two passive observers were added to
`Entity.moveFlying` and `NetHandlerPlayClient.handlePlayerPosLook`. They do
not modify friction, strafe/forward, yaw/pitch, MotionEvent, S08, S12, C03, C02,
or the attack scheduler. Inactive modules perform only a lookup and return.

## Controlled server protocol

Do **not** compare multiple variables at once. Disable Speed, NoSlow, Velocity,
JumpReset, Tickbase, Scaffold, and packet-timer modifiers; use one fixed target
and a short test area.

| Trial | Aura | AimAssist | MoveFix | Movement | Attack |
|---|---|---|---|---|---|
| A | OFF | OFF | N/A | Still / W / W+D | Manual |
| B | OFF | ON | N/A | Still / W / W+D | Manual |
| C | ON | OFF | Aus | Still / W / W+D | Auto |
| D | ON | OFF | Legacy Strict | Still / W / W+D | Auto |
| E | ON | OFF | Legacy Rounded | Still / W / W+D | Auto |
| F | ON | OFF | Legacy Rounded | Same | Knockback from test partner, then separate attacks |

Repeat each with a second fixed target only if the baseline is stable.
Capture `epochMs` around F8, S08_RECEIVED, S08_APPLIED, S12, KB_POST and
attacks. Server-side check names/VLs and plugin/server version are necessary to
claim why an anticheat flagged. Client logs do not establish a Polar/Intave
check result.

## Local verification gate

Before merging, run Java 8u482 + Maven 3.9.x tests including:

```powershell
mvn -o '-Dtest=CombatCorrectionPreviewTest,CombatDiagnosticsIntegrationTest,CombatDiagnosticTraceTest,MoveFixControllerTest,MoveFixProductionTest,CombatFlowTest,RotationBehaviorHookTest,RotationModuleBindingTest,NetworkMoveFixIntegrationTest,SpeedMovementBasisTest,NetworkPolicyIntegrationTest' test
```

The full Maven suite and a real GUI/network test should also be checked. If
`Entity.moveFlying` or `NetHandlerPlayClient.handlePlayerPosLook` regress,
fix only the observer; **never** change their gameplay math/packet semantics
to make a diagnostic test pass.

This patch was authored via GitHub without local Windows Maven or live game
access. It must remain a draft PR until Codex verifies it.
