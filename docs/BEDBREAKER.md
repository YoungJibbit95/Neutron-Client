# BedBreaker: silent rotations and defense mining

BedBreaker finds beds within ordinary controller reach, compares mining routes through
their defenses, and removes the selected obstructions before the bed. Silent rotations
use the shared `RotationManager` used by Aura and Scaffold. The camera and crosshair
remain under mouse control; the accepted server direction appears on the third-person
player model. Shared **Einfach / SIMPLE MoveFix** is always enabled for BedBreaker.
No separate silent-rotation or MoveFix setting is required.

**Turn Speed** bounds combined server yaw/pitch change in degrees per player tick,
starting from the last finite effective rotation. **Swing** controls normal accepted
mining animation. Smart Disable on Death and World Change remain default-off options.

## Mining-time routes

The planner evaluates straight sightlines from the current eyes to both reachable bed
halves. Each route records successive native block hits ending at the bed. Estimated
remaining controller ticks account for the held tool, player mining modifiers, progress
on the current block, and post-break delay. Several fast blocks can beat one slow block.
Instant survival breaks have no post-break delay; progressive survival and creative
breaks have five ticks. Zero-hardness blocks with infinite relative mining speed count
as instant breaks. The module does not move the player or switch hotbar slots.

Planning never changes the world: it continues the ray past each hypothetically removed
voxel. Each actual action still requires the first real block hit. After destruction,
the next step is validated against the live world. Equal-cost alternatives retain the
current route; new searches can choose a cheaper opening. Changed blocks, missing beds,
tool changes, unloaded cells and unbreakable survival obstructions invalidate affected
routes. World-border and adventure restrictions are checked in both cost estimation
and the native controller. Creative keeps its ordinary mining semantics.

The result is the fastest **evaluated reachable route** under these cost estimates.
It does not guarantee a global optimum over every possible aim point, player movement
path or inventory change.

### Work and geometry bounds

- New acquisition and alternate-route comparison occur at most once per two player
  ticks; intermediate calls only revalidate the retained route.
- A scan visits at most 1,331 cells and retains the nearest 32 bed blocks. Negative
  coordinates use floor, and beds use their actual 9/16-high collision bounds.
- Each candidate has at most 16 inset aim samples. There are at most 1,024 native rays
  per selection, including final validation, and 16 steps per route. Dense arrangements
  can exhaust the budget, leaving farther or unsampled routes unexamined.
- Each ray preflight visits at most 24 voxel transitions, including bounded checks for
  touching edge/corner cells. Every crossed cell must already be loaded.
- Survival/adventure reach remains 4.5 blocks; creative reach remains 5.0. Damage and
  cooldowns are unchanged. One automatic controller attempt is allowed per player
  tick. Toggle/config changes cannot reset action, search or rotation budgets.

## Movement, rendering and action authority

Early movement preparation makes MoveFix available on the first physics step. Shared
movement, sprint and jump consumers use the selected server-yaw basis. Camera angles
and key bindings are never rewritten. `RotationCorrections` renders the accepted pose
using `BodyRotationController.DIRECT` without changing entity camera angles.

Minecraft's ordinary block-input stage precedes that tick's player movement packet.
Mining requires the preceding **accepted** BedBreaker rotation, never a pending
request. The resolved C03 ticket validates the exact pose and request lifetime at
transport admission. Cancelled, rewritten or stale delayed movement cannot authorize
mining. A lost scene or subsequent rejected motion revokes authority.

Automatic mining uses the accepted server look independently of camera picking. The
native controller receives an immutable look vector and repeats expected-block,
reach, hit-face and world-ray checks around packet/world callbacks. The existing
manual flow and camera-based guarded overload keep their behavior.

Manual Attack/Use, short clicks, GUI/focus loss, riding, item use, active Aura
activity, Scaffold and NScaffold take precedence. LagRange's input veto is retained.
The exact controller revision, block, scene, connection epoch, selected slot, held
stack, accepted motion sequence/pose and player position guard the operation. Defense
mining also requires its destination bed to remain present. Manual takeover at the
same block transfers ownership immediately; BedBreaker never resets that operation.

Action and lifecycle checks remain separate. Disable aborts only owned progress;
world/session loss discards it without old-world callbacks. START/STOP ownership is
published before POST listeners. Abort reentry, cancelled aborts, instant destruction
and manual callback takeover retain their existing protections.

## Source and adaptation

The original module adapted periodic bed search and retained targets from the user's
authorized [Demise BedBreaker.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/BedBreaker.java),
commit `e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`, blob
`f3eb8bab8cb2b087d87568f6184e10d0781275e2`. This revision replaces the earlier
visible-only, exposed-bed-only behavior with Neutron's shared silent services and a
local mining-time planner. No external runtime dependency or asset is added.

## Validation

`BedBreakerPlannerTest` covers real rays, bounded work, negative coordinates, bed
geometry, reach, multiple defenses, cheaper multi-block paths, instant delays,
progress retention, both halves, unloaded cells and stale plan/cost callbacks.

`BedBreakerFlowTest` covers the actual Minecraft input hook, native controller and
embedded transport: manual/short-click priority, duplicate/toggle budgets, late walls
and server-pose changes, lifecycle changes, stack replacement, exact ownership,
abort reentry, instant destruction and callback takeover.

`BedBreakerSilentRotationTest` covers actual movement preparation and walking C03
transport, first-step MoveFix, model pose, camera/crosshair independence, rejected
pending/cancelled/rewritten/delayed movement, late input changes and movement after
preparation. `BedBreakerDefenseFlowTest` covers automatic defense-to-bed progression
through the native controller and live invalidation/cost decisions.

These fixtures substitute the block store and game/audio environment. They do not
establish live-server acceptance or a visual in-game playtest, and make no anti-cheat
compatibility or measured FPS claim.
