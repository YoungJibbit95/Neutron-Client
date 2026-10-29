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
5. The `Console Attack Log` option (on by default when the module is active)
   prints local attack attempts, locally accepted attacks, manual markers,
   and S08 position-correction packet receipts. It does **not** print every
   movement update to the console.
6. Export the relevant client console lines and the authoritative Intave
   server check name / severity / timestamp; correlate by `epochMs` and
   approximate client tick. Never upload account tokens or private server data.

## Fields and limitations

- `camera`: current visible camera yaw/pitch at sampling time.
- `localWire`: most recently accepted local movement rotation stored by
  RotationManager; not a server acknowledgement.
- `owner`: confirmed local rotation owner, or `none`.
- `track`: module associated with the last tracked target.
- `seq`: local motion sequence; `tick`: client tick counter.
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

The HUD stores up to 120 event strings in memory and clears on disable/world
change. It creates no background tasks, sends no packets, and changes neither
rotation algorithms nor Aura's attack scheduling. A screenshot alone cannot
identify a server-side violation category.

## Validation

The new pure-Java trace has JUnit coverage. The full Java 8 Maven test suite,
live HUD rendering, and multiplayer behavior **must be verified locally**
before this draft PR is merged.

Tests to run in an isolated worktree:
```powershell
mvn -o '-Dtest=CombatDiagnosticTraceTest,CombatFlowTest,RotationBehaviorHookTest,RotationModuleBindingTest,NetworkMoveFixIntegrationTest' test
```
