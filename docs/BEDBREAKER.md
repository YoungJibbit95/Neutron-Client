# BedBreaker: visible native mining

BedBreaker is a Player module. It finds exposed beds within the controller's normal
reach, turns the visible camera toward one, and mines through Minecraft's ordinary
block-input stage. Turn Speed bounds the combined yaw/pitch change in degrees per
player tick. Swing controls the normal accepted-mining arm animation. Smart Disable
on Death and Smart Disable on World Change are independent, default-off options.

## Source and adaptation

The user authorized use of their Demise source. The reviewed reference is
[Demise BedBreaker.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/BedBreaker.java),
commit `e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`, file blob
`f3eb8bab8cb2b087d87568f6184e10d0781275e2`.

This is an adaptation of its small periodic bed search and retained target concept,
not a byte-for-byte import. Neutron's implementation uses its own module lifecycle,
visible rotation arbitration and native controller. It does not import the reference's
target-only collision trace, unrelated cursor face, arbitrary-block Legit branch,
direct animation packets or private currentBlock access. No source dependency or
new asset is copied into the repository.

## Geometry and work limits

- New selection runs at most once per two player ticks. A still useful bed is retained.
- A scan visits at most 1,331 loaded candidate cells in the normal reach cube. Negative
  coordinates use floor. Only `BlockBed` qualifies, with its actual collision bounds.
- The nearest 32 in-reach candidates receive world visibility checks. The first visible
  candidate wins; a retained candidate adds at most one preliminary ray. Dense synthetic
  arrangements containing more than 32 nearer hidden beds can therefore yield no target.
- Survival/adventure uses the controller's ordinary 4.5 reach, creative its ordinary 5.0.
  A fresh first world hit must identify the selected bed and its own hit face before
  mining. The native crosshair pick must also identify that bed: entity, miss, unknown
  and another-block picks take precedence. Its identity is retained across the action.
  The guarded controller repeats the world ray around transport callbacks.
- A player/world/tick budget permits one automatic controller attempt per player tick.
  Toggle and configuration changes do not reset this budget. Search and visible turn
  budgets are separate and likewise cannot be reset by toggling.

Manual Attack/Use, a GUI, focus loss, item use, an active Aura action/target, Scaffold
and NScaffold take precedence. The existing LagRange block-input veto is retained.
A real `clickMouse` call also stamps the current tick, so a brief entity or miss click
cannot be followed by automatic bed mining merely because its button was released.
No mouse input, movement key, hotbar choice, extended reach or private packet sequence
is synthesized by the module.

## Ownership and callbacks

The module records the exact controller revision and block before entering guarded
mining. Normal controller entry points increment that revision without changing their
existing two-argument mining behavior. A manual operation, including one at the same
block, takes ownership immediately. BedBreaker never resets that replacement operation.

The guarded native sequence checks scene identity, connection epoch, selected slot,
held-stack identity/content, expected block, visible ray and the caller's generation.
Its action and lifecycle suppliers are separate: lost input permission may still clean
up an owned operation, while a revoked scene forbids both transport and old-world callbacks.
Local START/STOP state is published at transport acceptance before POST listeners.
Disable uses a guarded ordinary abort for its exact operation; world/session loss
discards only that owned local progress. Abort reentry is bounded, and a cancelled
abort cannot leave old local progress active. Instant destruction clears its local
operation before a destructive-world callback can start replacement mining.

## Validation

`BedBreakerPlannerTest` covers bounds, negative coordinates, real bed height, wall
occlusion, ordinary reach, retention, invalid input and bounded visible turns.
`BedBreakerFlowTest` runs isolated JVM cases using the actual Minecraft input hook,
rotation arbitration, `World.rayTraceBlocks`, native controller and embedded transport.
The fixture substitutes an explicit block store, audio and game environment. It covers
manual and short-click priority, duplicate/toggle budgets, late walls/camera changes,
Disable/reenable, unload/load, same-slot replacement, exact mining ownership, abort
reentry, instant destruction and an effect callback that takes over manual mining.

These tests do not establish live-server acceptance, anti-cheat behavior or FPS claims.
