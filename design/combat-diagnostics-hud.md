# CombatDiagnostics — opt-in local rotation/attack trace

This is a small **read-only development HUD** for investigating whether an observed
server-side warning is correlated with movement, aiming, target switching, or an
attack attempt. It does not inspect anticheat internals, suppress warnings, or
change game behavior.

## Usage

1. Build and run the client from a verified Java 8 / Minecraft 1.8.9 environment.
2. Toggle the `CombatDiagnostics` module via the Client category or **F9**.
   Recording is disabled by default until the module is enabled.
3. Reproduce a short issue while standing still, with Aura or AimAssist
   enabled separately. The HUD shows the most recent seven events.
4. Press **F8** immediately after seeing a server-side warning to add a
   **manual timestamp marker**. F8 does not detect the server flag.
5. Enable the `Console Attack Log` option explicitly if console export is needed
   (off by default). When enabled, it
   prints local attack attempts, locally accepted attacks, manual markers,
   and S08 position-correction packet receipts. It does **not** print every
   movement update to the console.
6. Export the relevant client console lines and the authoritative Intave
   server check name / severity / timestamp; correlate by `epochMs` and
   approximate client tick. Never upload account tokens or private server data.

## Fields and limitations

- `camera`: player camera yaw/pitch. During synchronous motion-POST combat,
  sampled before Aura temporarily substitutes its attack pose; outside that
  dispatch, sampled directly. `cameraSource` distinguishes these observations.
- `playerPose`: raw player angles at the event, which can temporarily differ
  from the camera during Aura's local attack/knockback calculation.
- `localWire`: most recently accepted local movement rotation stored by
  RotationManager; not a server acknowledgement. Missing or invalid observations
  are `NaN` with `wireKnown=false`, never fabricated from camera angles.
- `motionAccepted`: whether the current movement frame was locally accepted;
  this is separate from retaining an older observed wire rotation.
- `owner`: confirmed local rotation owner, or `none`.
- `track`: module associated with the last tracked target.
- `seq`: local motion sequence; `tick`: client tick counter. `event` is the
  recording order within the current trace, even when timestamps are equal.
- `delta`: stored local movement rotation minus camera rotation.
- `targetId`: local entity ID, not the target's username.
- `centerErr`: approximate error from stored movement rotation to target
  **center**. It is not a hit-validity or anticheat prediction.
- `ATTACK_PRE_ATTEMPT`: local controller attack event occurred. It may be
  canceled or rejected before sending an attack packet.
- `ATTACK_LOCAL_ACCEPTED`: controller locally accepted and sent an attack
  packet; this is not proof the server processed a hit.
- `S08_RECEIVED_NOT_A_FLAG`: server position-correction packet received;
  S08 can also be a normal teleport, so it does not establish a violation.

The POST observer runs before default-priority combat listeners, and its camera
sample expires at the end of that dispatch. It does not reorder the combat
listeners themselves. The HUD summary keeps current owner/target/angles visible
even when long event lines are trimmed. Rendering restores the previous GL state.

The HUD stores up to 120 event strings in memory and clears on disable/world
change. It creates no background tasks, sends no packets, and changes neither
rotation algorithms nor Aura's attack scheduling. A screenshot alone cannot
identify a server-side violation category.

## Validation

Trace and module integration have Java-8 JUnit coverage, including reflection
discovery, Client-category listing, F9/F8 dispatch, lifecycle cleanup, bounded
history, unknown wire angles, and nested attack events in both listener orders.
The new regression tests fail on the original PR module before its correction.
They verify that diagnostic recording preserves angles/ownership and emits no
packets. Live HUD rendering, key conflicts in personal configs, and multiplayer
behavior still need manual verification; no Intave/Polar check is diagnosed here.

Run in the existing main checkout, without creating another client copy:
```powershell
mvn -o '-Dtest=CombatDiagnosticTraceTest,CombatDiagnosticsIntegrationTest,CombatFlowTest,RotationTrajectoryTest,RotationBehaviorHookTest,RotationModuleBindingTest,NetworkMoveFixIntegrationTest' test
```
