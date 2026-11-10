# Adaptive Bridge stability and placement timing

## Problem and scope

This change addresses sideways drift, unrelated placement rows, unnecessary face
changes and missed ordinary falling catches in the **Adaptive Bridge** Scaffold
approach. The investigation started at
`1d675ea03cf9c54366b6aa9e41eeac194bddd0b0`.

The old Adaptive path called the legacy radius-two placement search before its
bounded lookahead. Its retention rule also admitted destinations two cells away
on either horizontal axis. A valid click could therefore belong to the wrong
route. Discrete Movefix directions changed as placement yaw changed, and a new
face restarted rotation with an inert first step.

The timing defect was equally significant: the early rotation hook ran before
player physics, whereas C03 and POST placement used the position after physics.
A ray aimed precisely at a face could miss it after movement. A straight bridge's
outward face may only become visible after that movement, so a faster early
rotation alone cannot fix acquisition.

## Input and route

Adaptive Bridge keeps movement in the camera frame for every enabled correction
mode. The normal `moveFlying` path remains responsible for acceleration, diagonal
normalization and existing sneak/item slowdown. Sprint admission, jump impulses
and direct movement helpers use the same basis. Other Scaffold approaches and
other rotation owners retain their existing mode behavior.
The optional rotation return after disabling Adaptive Bridge also keeps camera
movement, so the return cannot reintroduce aim-relative input rounding.

A small independent lane controller anchors the raw intended line, uses a
0.025-block positional deadband, and applies bounded proportional correction with
sideways-velocity damping. It preserves input-vector length and never writes
velocity or physical keys. Stop, Off, deliberate direction changes, teleports and
lifecycle changes release the old lane. Corrected axes are not fed back as a new
raw direction. The route projection is used only while the actual player still
overlaps the lane; falling recovery follows the actual body path.

## Target selection

Destination selection precedes support-face selection. Normal acquisition checks
the current route cell, its midpoint to the bounded predicted position, and the
predicted route cell. Duplicate cells are removed. This is at most three cells
and eighteen face attempts, with no legacy radial fallback.

An exact diagonal crossing may need an orthogonal connector before its diagonal
destination has any supporting face. If the immediate route has no valid face,
the previous corner block supplies at most two explicit connector-face proposals.
The chosen connector must overlap the actual footprint, follow the raw intended
direction and fill an actual underfoot gap. This adds at most two face attempts,
not another surrounding-cell scan. Once the diagonal cell has a valid face, it
takes priority over either connector.
The connector gets urgent aiming because two placements must fit before the
remaining support ends. Both placements still spend the normal cadence budget.

During a measured fall, the search may include cells overlapped by a conservative
0.28-block half-width footprint at current and predicted positions, plus at most
one lower layer. It is capped at twelve unique destination cells and seventy-two
face attempts. `Keep Y` disables the additional lower layer. Selecting another
face of the same approved destination adds at most five face attempts.

A still-needed support and face retain their identity. If the existing accepted
view already hits that face, its actual ray intersection is retained; point
variation does not displace a useful aim. The immutable acquisition anchor is
kept separately from that intersection. Loss of visibility, reach, inventory or
route relevance still invalidates the candidate.

Normal lookahead is an aim preview. It does not authorize filling an unrelated
future or parallel cell while the actual foot cell is occupied. The POST check
uses the same narrow route/footprint policy.

## Timing and rotation

Early input preparation preserves route intention and the movement-owner basis.
The real target/rotation calculation runs once in walking PRE, after physics and
before the original movement packet. It uses the position that the movement
packet will actually report, including the existing sub-threshold position rule.

The rotation manager explicitly distinguishes this Adaptive camera-frame
preparation from ordinary rotation-bound movement. Only a current, matching
Adaptive scope may finalize its packet angle; the consumed movement frame is not
rewritten. A different owner, revoked scope, duplicate opportunity or changed
scene cannot inherit the exception.
Rejected calculations revoke their exact candidate. The host publishes a POST
placement intent only after the current task and finalization both succeed; an
early held rotation cannot rescue a deliberately rejected or stalled task.

| Task | Behavior |
| --- | --- |
| Tracking a held face | Keeping speed limits, 100 ms exponential response |
| Acquiring a new safe face | Initial speed limits, 40 ms exponential response |
| Edge adjustment | Presets allow up to 90 degrees per nominal tick |
| Imminent loss of support or falling catch | Presets allow up to 180 degrees yaw and 90 degrees pitch per nominal tick |

A nominal tick is 50 ms. The first real motion receives a bounded useful step;
there is no per-block HOLD. Later steps use bounded elapsed motion time, and a
face change does not grant another first-step budget. Yaw takes the shortest
path, pitch stays within its valid range, and steps do not overshoot. Sensitivity
quantization still runs at the existing final boundary.

Actual physical motions without any placement task do not accumulate turn
credit. Resuming after those idle motions gets one nominal 50 ms step. A genuine
250 ms stall between consecutive task motions still rejects that motion, as does
clock rollback. This prevents an ordinary gap between diagonal corners from
being mistaken for a stalled player step.

Custom Initial/Keeping sliders remain hard limits. Choosing very low limits may
make a late catch impossible. Preset rescue urgency can increase speed before
`fallDistance` grows: the next predicted loss of collision support is sufficient.
This is independent of the optional Edge Safety sneak/stop setting.

A first urgent placement may consume its initial cadence opportunity immediately
after the accepted face ray has been verified. This credit exists only before the
first attempt in that cadence session. A rejected attempt consumes it too. The
next opportunity waits a full configured interval from the real attempt; there
is no recurring catch-up burst or extra opportunity for a duplicate POST.

## Authority and limits

Placement still requires the original accepted C03, the exact intended support
and face, current world/player/connection and inventory identities, normal reach,
and the native placement controller. Rotation proposals and preview targets are
not proof of placement. Counters advance only after the expected block is
observed locally.

These changes do not make every fall recoverable. A missing reachable support,
an already crossed usable placement height, exhausted blocks, a rejected motion,
or deliberately restrictive Custom/cadence settings can prevent a catch. Tests
use deterministic local world storage and transport, not a live server or a
native rendered game session.

## Research provenance

Two research agents inspected the following primary sources. The implementation
is independent; no upstream source was transplanted.

| Source and pinned revision | Useful observation and explicit caution |
| --- | --- |
| [LiquidBounce Legacy Scaffold](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/features/module/modules/world/scaffolds/Scaffold.kt), `03bc91a3` | Direct underfoot priority, valid-ray reuse, next-step fall urgency. Its broad cuboid fallback is unsuitable for the reported row churn. |
| [FDP Scaffold](https://github.com/SkidderMC/FDPClient/blob/07d03078b3fe12ac7278c678f56d0e7f607fbec2/src/main/java/net/ccbluex/liquidbounce/features/module/modules/player/scaffolds/Scaffold.kt), `07d03078` | Separate effective rotation from desired rotation, preserve an appropriate face, and revalidate placement geometry. Discrete movement rounding remains a source of direction changes. |
| [Vibe Scaffold](https://github.com/SkidderClub/Vibe/blob/b6a5392948325334b6a9747e37494f88c55be46e/src/main/java/dev/vibe/module/impl/world/ScaffoldModule.java), `b6a53929` | Retain a useful current yaw/pitch and aim inside a valid face region. Its broad support scan and permissive alternative-side placement are not adopted. |
| [Arsenic LaneNudge](https://github.com/ArsenicClient/Arsenic/blob/1ec158a8cc2ae19df2a80182361541a60a352346/src/main/java/arsenic/utils/scaffoldcore/LaneNudge.java), [ScaffoldCore](https://github.com/ArsenicClient/Arsenic/blob/1ec158a8cc2ae19df2a80182361541a60a352346/src/main/java/arsenic/utils/scaffoldcore/ScaffoldCore.java), [ClutchCore](https://github.com/ArsenicClient/Arsenic/blob/1ec158a8cc2ae19df2a80182361541a60a352346/src/main/java/arsenic/utils/scaffoldcore/ClutchCore.java), `1ec158a8` | Anchor raw movement intention, filter by predicted footprint, and treat a catch as a bounded geometric task. Discrete 45-degree lane nudges and knockback-only activation are not adopted. |

LiquidBounce Legacy and FDP declare GPL-3.0. Arsenic declares MIT. Vibe declares
GPL-3.0 and also documents historical decompiled, unlicensed Scaffold provenance
in [its provenance note](https://github.com/SkidderClub/Vibe/blob/b6a5392948325334b6a9747e37494f88c55be46e/LICENSES/GOTHAJ-SCAFFOLD.md).
The name Vibe is treated as the Forge 1.8.9 `SkidderClub/Vibe` implementation.

## Regression coverage and game acceptance

Focused tests cover exact movement/slowdown matrices, damped lateral recovery,
single-row destination selection, held and invalid faces, bounded falling
footprints, task speed limits, clock/duplicate/lifecycle boundaries, and the
one-time urgent cadence opportunity. Production-flow tests place through the real
controller after accepted movement. The stability regression executes
`moveFlying` and `moveEntity` between input preparation and C03, including a
normal falling catch and successive bridge cells in every correction mode.
Diagonal corner transitions also execute real movement at positive and negative
coordinates and must build only the connected staircase along that route.

Before declaring a native gameplay acceptance, run a private world with default
Balanced settings and Edge Safety Off: straight cardinal routes, diagonal routes,
negative coordinates, a deliberate turn, a small sideways disturbance, jumping,
and a recoverable fall with blocks available. Repeat with the enabled correction
modes and verify Off leaves input untouched. Check Custom Initial/Keeping values,
Keep Y, depleted/replaced stacks, disable/re-enable and world change. Record the
tested commit and observed result; automated local physics is not a claim that
this manual run occurred.
