# Adaptive Aura — Alpha 2

This iteration develops the Adaptive rotation approach into a coordinated aiming,
attack and sword-use flow. The reference is DemisePrivate commit
`e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`, used with the repository owner's
permission. The relevant source is `KillAura`, `ClickManager`, `ClickHandler`,
`RotationManager` and `RotationHandler` under `wtf.demise`.

The previous Straight/Nearest anchors and exact-target Keep Valid Aim remain the
starting point. Demise's global render-time rotation state, click backlog, and
any-entity mouse-over check are not introduced into Neutron's action pipeline.

## Aiming

Select **Rot Mode → Adaptive**. The normal controls are ordered by target,
aiming, movement correction, attacks and blocking in both ClickGUI presentations.
Advanced per-axis controls are hidden until **Adaptive Advanced Aim** is enabled.
Settings are reordered only for presentation; persistent registration order and
configuration IDs remain unchanged.

- Explicit minimum and maximum aim speeds use degrees per second. The minimum is
  a travel-speed preference; it does not force movement after acquiring a valid
  ray. The maximum remains meaningful across response presets and noise profiles.
  At intervals too short for one mouse count, at most one count of time credit
  carries forward. Holding, retargeting and suspension clear that credit.
- Adaptive, Linear and Relative curves share the existing motion clock,
  acceleration limits and final mouse-sensitivity handling.
- Adaptive Response (Calm/Balanced/Swift) changes acceleration response. It retains
  the old `adaptive_speed` ID; the speed sliders supply the actual maximum.
- Off, Soft and Organic noise profiles use acquisition-local sampling.
  FastNoiseLite supplies coherent temporal variation. Off and zero amount remove
  both temporal and acquisition-based random variation from this Adaptive path.
- Straight, Nearest, Head, Torso and Legs select anchors inside the real target
  box. Legacy remains available for compatible configurations.
- Keep Valid Aim belongs to the exact target and context. A valid hold does not
  accumulate future rotation time or drift because of noise.
- Hidden Smart Aim, Heuristics and Stop on Target values from other rotation
  approaches do not affect the Adaptive settings snapshot.

The new controller is still a candidate generator. Only the existing rotation
owner, accepted movement and final exact-target ray can authorize an action.

## Attacks and sword use

**Attack Mode** offers PlayerController (default), Legit and Packet. Legit also
requires the native picked entity to be the selected target and respects the
native click cooldown. With silent rotations, the real picker must still match;
this mode does not substitute its own entity into the mouse-over result.
Packet omits the controller's local damage/exhaustion/
knockback simulation while retaining its accepted-action and cancellation contract.
**Fail Swing** is opt-in and disabled by default.

Adaptive attack strategies share the normal tick/CPS budget and final action
validation. A visual miss swing does not become an entity attack, block-digging
action, target advance or successful-hit signal.

Controller and packet execution retain their documented local-effect distinction.
Every strategy uses cancellable attempt events and the same current-context
checks. A local transport result is not a server damage acknowledgement.

Adaptive sword-use ownership is tied to the current player, world, connection and
held sword. The flow confirms release before admitting the next attack, and
rechecks the target and accepted rotation after callbacks. Manual use remains
authoritative. Fake blocking changes presentation only.

Target loss, slot change, settings changes, GUI/focus loss, disable and connection
changes revoke obsolete follow-up work. A block request rejected by the pipeline
does not become confirmed ownership. Reblock delay is measured in client ticks.

The existing non-Adaptive Watchdog, Verus and Intave approaches keep their
compatibility path. In Adaptive, the old Watchdog and Verus choices map to the
guarded Vanilla and Release flows respectively. Explicit Vanilla, Release and
Force choices also use the new flow outside Adaptive. No new shared packet
buffer or server-specific bypass mode is part of this iteration.

New sword-use choices are **Vanilla**, **Release** and **Force**, alongside Fake.
Vanilla can begin holding a valid in-range target without waiting for a hit;
Release rearms after an accepted attack and the configured delay; Force rearms
immediately after an accepted attack. Existing Intave sequencing remains separate.
Autoblock itself remains disabled by default. The new block range defaults to 3
and reblock delay to one client tick.

**Switch** adds timed target cycling with a default 500 ms delay. Invalid targets
are replaced immediately. Adaptive Single retains its current valid target, while
Multi continues to advance through its configured set after an accepted attack.
**Angle** prioritizes the smallest horizontal turn from the camera. Aim range
margin, attack reach and block range remain separate controls; finding an aim
target does not authorize an out-of-range attack or block.

### Starting settings

| Control | New-client default |
| --- | --- |
| Aim speed | 360–560 degrees/second |
| Response / curve | Balanced / Relative |
| Noise | Soft, amount 0.5, tempo 1 |
| Aim anchor / hitpoint | Straight / Stable |
| Keep Valid Aim | On |
| Attack Mode / Fail Swing | PlayerController / Off |
| Autoblock | Off; block range 3, reblock delay 1 tick |
| Switch delay | 500 ms when Switch is selected |

Existing configs migrate their incoming axis speeds instead of replacing them
with the new-client speed defaults.

## Configuration compatibility

New setting IDs are appended to the existing registration contract. Full config
imports migrate missing Adaptive speed values from the incoming legacy axis
speeds, independently of JSON member order. Explicit new values take precedence.
Normal setting edits do not run legacy-import defaults.

Tickbase full config imports now reset absent `mode`, `packet_hold_ms` and
`pulse_gap_ms` to `Player Steps`, `200` and `60`. Loading an older config therefore
does not inherit a previously selected packet mode. Explicit current settings
continue to round-trip.

## Integration contracts

- Scaffold retains its higher rotation priority. Aura's pending or active sword
  use must not survive an incompatible owner/slot transition.
- A held movement packet does not count as accepted Aura motion. Releasing a
  movement queue alone must not perform a new attack.
- Tickbase, Backtrack and LagRange continue to consume the actual selected target
  and existing action results. They receive no independent attack budget.
- World, player, connection and target identity are checked at callback boundaries;
  a replacement entity with the same numeric ID is a different acquisition.

## Validation and game checks

Regression coverage includes motion limits, noise-off determinism, exact holds,
config migration, action cancellation, guarded sword use, target identity and real
walking flows with Scaffold and the timing modules. The PR records the actual test
results and the immutable tested commit; the existence of a test is not a claim
that a game or server scenario has been exercised.

For the game check, compare the same stationary, strafing, target-crossing and
partially occluded scenes with the previous alpha. Observe acquisition time,
reacquisition after losing the ray, unwanted target switches, residual rotation,
block release/reblock and manual slot changes. Exercise Aura → Scaffold → Aura,
GUI/focus transitions, disconnect and a fresh world load.

Server damage, corrections and any server-side detection feedback require actual
game/server observations. Local accepted actions and build success do not prove
those outcomes.
