# Smooth rotation stop and test profiles

Aura and Scaffold now expose **Smooth Rotation Stop** (on by default) and
**Return Ticks** (default 6, range 3–12; one ordinary client tick is nominally 50 ms).
The return follows a minimum-jerk curve from the last accepted look to the live
camera, taking the short path across the yaw wrap. Moving the mouse changes the
destination without changing the camera itself.

Aura returns after target death/loss, module disable, or turning Rotations off.
Scaffold returns on disable or turning its Neutron Rotations option off. An active
Scaffold keeps its existing planless hold behavior. Arsenic always uses rotations;
Smooth Rotation Stop remains visible for both Scaffold approaches.

A return has its own low-priority rotation-only owner. It cannot authorize attacks,
autoblock, placement, inventory changes, or swings. A new rotation request takes
over immediately. Movefix consumes only accepted return frames, using the
captured correction mode. Direct/Natural body rendering retains the previous pose
between disable and the next movement, then blends back to the vanilla body.
Vanilla body rendering remains vanilla.

The return is bound to the original accepted scene/connection receipt. It ends
on world/connection changes, observed suspension, player death, re-enable, clock
reversal, or its tick/time deadline. Rejected/replaced movement never grants return
authority. Extra player steps within one outer tick do not advance its progress.
The duration is independent of acquisition speed controls; larger angular gaps
over the same Return Ticks require larger intermediate steps.

## Included profiles

Packaged JSON files live in `src/main/resources/neutron/configs`. The same new
files are installed into the main test checkout's `test_run/pulsar/configs`.
Existing named configs and `.last-state.json` are preserved. Load from the config
library or use:

```text
.config load Intave-Arsenic-Combat
.config load Intave-Arsenic-Assist
.config load Polar-Arsenic-Organic
.config load Polar-Noise-Organic
```

| Profile | Enabled on load | Aura / AimAssist rotation | Scaffold |
| --- | --- | --- | --- |
| Intave-Arsenic-Combat | Aura | Arsenic Lazy | Arsenic, speed budget 100–180, Eagle |
| Intave-Arsenic-Assist | AimAssist | Arsenic Lazy | Arsenic, speed budget 100–180, Eagle |
| Polar-Arsenic-Organic | Aura | Arsenic Heuristics | Arsenic, speed budget 140–240, Eagle |
| Polar-Noise-Organic | AimAssist | Advanced / Legit with custom noise | Neutron Smooth with custom face/speed variation |

All four configure Scaffold but leave it disabled until toggled by the user.
Keybinds and visual modules are preserved. Backtrack, LagRange, Tickbase, Velocity,
JumpReset, AutoClicker, Speed and NoSlow are disabled when loading these profiles.
Aura and AimAssist are not enabled together. Both remain fully configured if the
user switches between them.

The Intave profiles use 2.95 reach, raycast checks, no through-walls, single-target
Aura at 8–11 CPS, pitch cap 8, modest prediction, six-tick returns, ordinary POST
placement, timer 1, no tower/sprint/downwards automation and no angle rounding.
Arsenic's actual turn increments are eased; its speed budget is not a constant
degrees-per-tick output.

The Polar Arsenic profile uses delayed target history and spring pursuit, 1.2/1.4
prediction ticks, 6%/5% miss chance, independent yaw/pitch caps and seven/eight-tick
returns. Arsenic supplies its own drifting aim point and variation. The legacy
noise controls are inactive in these modes and are explicitly off.

The Polar Noise alternative uses the live legacy controls: OpenSimplex2S/FBm,
two octaves, 0.75 Hz, restrained aim-point and angular variation, speed variation
and a 450 ms Bezier speed curve for combat. Scaffold varies the actual face point
and speed rather than adding angular noise away from its valid placement ray.
This is a separate approach, not noise layered over Arsenic.

## Source basis and acceptance

The Intave reference reviewed is
[intave/intave@cc7d30b294b11403db401924bb59a744d766bb53](https://github.com/intave/intave/tree/cc7d30b294b11403db401924bb59a744d766bb53).
The relevant primary sources are
[AttackRaytrace](https://github.com/intave/intave/blob/cc7d30b294b11403db401924bb59a744d766bb53/src/main/java/de/jpx3/intave/check/combat/AttackRaytrace.java),
[combat heuristics](https://github.com/intave/intave/blob/cc7d30b294b11403db401924bb59a744d766bb53/src/main/java/de/jpx3/intave/check/combat/Heuristics.java),
[rotation sensitivity](https://github.com/intave/intave/blob/cc7d30b294b11403db401924bb59a744d766bb53/src/main/java/de/jpx3/intave/check/combat/heuristics/combatpatterns/rotation/RotationSensitivityHeuristic.java),
[placement analysis](https://github.com/intave/intave/blob/cc7d30b294b11403db401924bb59a744d766bb53/src/main/java/de/jpx3/intave/check/world/PlacementAnalysis.java),
[placement rotation activity](https://github.com/intave/intave/blob/cc7d30b294b11403db401924bb59a744d766bb53/src/main/java/de/jpx3/intave/check/world/placementanalysis/RotationSpeed.java)
and [placement pitch changes](https://github.com/intave/intave/blob/cc7d30b294b11403db401924bb59a744d766bb53/src/main/java/de/jpx3/intave/check/world/placementanalysis/RotationFlick.java).
They motivate consistent accepted raycasts, mouse-grid rotations, restrained
prediction, normal movement and fewer competing automation paths.

These are source-informed test configurations for Neutron's 1.8.9 client.
The [Intave README](https://github.com/intave/intave/blob/cc7d30b294b11403db401924bb59a744d766bb53/README.md)
also describes optional cloud-based detection. Neither that service nor Polar's
classifier has been evaluated here. No profile is certified flag-free or
indistinguishable from human input. Randomization alone does not establish either
claim. Live server/version/configuration and gameplay acceptance remain pending.

Automated coverage exercises real selection/walking/C03 transport, target loss,
disable and settings changes, moving-camera returns, re-enable callbacks,
rejection, reserved-source protection, expiry, scene invalidation and body
presentation. Profile tests reject unknown setting IDs and check exact values
and activation states through repeated cross-profile imports.
