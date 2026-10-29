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

## Integration verification in the primary checkout

PR #11 was merged first (`85c5efa5389bc827131015d57083920d21cc0387`).
PR #12 was then merged with that main in `F:\Puslar Backup - Copy`; its
diff still contains only the seven intended diagnostics, hook, test and document files.

Temurin 8u482 and Maven 3.9.15, with process-local `JAVA_HOME`:

- PR #11 focused baseline: 66 tests passed.
- Integrated PR #12 focused suite, including `RotationTrajectoryTest`: 77 passed.
- Full suite with native graphics/chat/world-overlay checks enabled:
  **713 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS**.
- Added real-consumer coverage compares diagnostics OFF/ON with identical motion
  for all six configured MoveFix modes and four input combinations. No second
  movement correction or changed acceleration is introduced.
- A test invokes the real `handlePlayerPosLook` with mixed relative/absolute axes,
  verifies unchanged position, rotation and velocity semantics with diagnostics
  OFF/ON, checks the single C06 ACK payload, and observes that ACK in the outbound
  queue before optional diagnostic console output executes.

An initial focused run overlapped an IntelliJ build writing the same class output;
its isolated Speed JVM failed to find `GuiIngame`. The non-overlapping focused
rerun and full suite above both pass. No gameplay change was made to mask that
build-output conflict. Keep Maven compilation and IntelliJ Make serial.

Local verification logs are retained under
`F:\Pulsar-Recovery\analysis\diagnostics-pr11-pr12-20261029`.
Real multiplayer/server-check correlation remains untested; local handler and
native graphics tests do not establish Intave or Polar compatibility.

## Interactive smoke test

The existing IntelliJ configuration **Neutron Client (Java 8)** launched the
client as `NeutronTest` from the existing `test_run`. A separate creative,
superflat world was generated in `test_run/saves/New World-`; the existing
`New World` was not opened. No client, assets or existing world was copied.

Observed in the running game: the diagnostics HUD and raw/final input summary
render, F8 adds a timestamped marker, F9 removes the HUD, and spawn corrections
produce separate S08 receipt/application entries. The existing watermark,
array list and session HUD overlap some diagnostic lines when enabled together;
this is a remaining readability limitation, not a GL crash. Client-category
registration is covered by the registry test; ClickGUI category selection was
not visually verified in this session. Nonzero movement input, S12/knockback
values and lifecycle details have automated consumer/event coverage; a controlled
multiplayer reproduction is still required for server conclusions.

Small pre-smoke settings copies are retained with the local evidence. No existing
profile, account or world files are committed or removed. The new test world
remains local as an explicit runtime-test artifact.
