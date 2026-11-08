# W08-A3 — Placement cadence and Godbridge jump

Stacked on A2 `c802aef0ddb168aa9f0c3efffbffc3e27837f145`; main baseline
`c6612bf43ec2ce45bf686714374516f80788057e`.

| Adaptive Bridge setting | Values / default |
|---|---|
| Place Min CPS / Place Max CPS | 1–20 decimal; defaults 12 / 16 |
| Click Profile | Basic / Smoothstep / Perlin; default Smoothstep |
| Place Interpolation | Stable / Smooth / Dynamic; default Smooth |
| Variation Strength | 0–1; default 0.35 |
| Place Debug | Off by default |
| Godbridge Auto Jump | Off by default, Godbridge only |
| Godbridge Jump After Blocks | 1–20, default 8; visible when enabled |

Place Interpolation has a separate ID; the old numeric rotation setting remains.
Missing new keys reset to defaults. Reversed CPS endpoints normalize before sampling.

## Productive path

Real PRE starts BridgePlacementCadence with its own ClickRandomizer, seeds,
pending interval and counters. It shares only the existing monotonic clock source:
readMonotonicNanos reads time without changing rotation/curve/sequence state.
Attack budgets, render samples, player ticks and game timers are not placement credits.

POST consumes a due opportunity only after checking the original PRE intent and
accepted C03, then calls unchanged tryPlace/placeWithAuthority. Rejection consumes
the normal interval. Scheduling starts from the actual opportunity, without catch-up.
Duplicate POST cannot retry. Existing admission, raycast, slot/stack and Place Delay
remain additional gates, so successful placement rate may be below configured CPS.

Scoped reset protects replacement sessions. Lifecycle/config changes release pending
state; clock rollback requires a newer acquisition on a later PRE. Observation/debug
never redraws intervals. The next nanosecond interval rounds upward.
Selected slot, usable block slot and stack object identity are checked in PRE and
again immediately before an automatic jump. Normal stack depletion is allowed;
exhaustion, replacement or a manual slot change discards old credit. A successful
non-silent controller switch updates only its expected selected slot.

## Success and input

The block count requires a true controller result AND the expected block type at
the original destination in the same local scene. C08 transport alone is insufficient.
This is local evidence, not server acknowledgement; later server correction is possible.

Godbridge requests one fresh input.jump after the selected count, only with current
support, both safety sweeps clear, movement, usable blocks and no sneak/item-use/
flight permission/liquid/ladder/impulse recovery. Players allowed to fly receive no
automatic jump, preventing a synthetic edge from triggering Vanilla's flight toggle.
Vanilla owns actual jump execution and velocity.
Manual jump stays unchanged and cancels the automatic cycle. Guarded, airborne or
foreign-owner input cannot trigger it. No physical key, direct position, velocity,
packet or game timer is written.

## Evidence

Temurin 8u482 / Maven 3.9.15 local full-source gate: **199 tests / 0 failures /
0 errors / 0 skips** (prior 170 unchanged, scheduler 15, config 5, productive flow 9).
The scheduler includes stale scoped reset, CPS bounds, clock edges, duplicates,
no catch-up and FPS-independent observations. Flow tests exercise all nine profile/
interpolation combinations, exact deadlines, rejection, repeated POST, a real long
pause, Place Delay, eight local blocks, non-silent slot switching and fresh-input
ownership. A positive-control-derived sweep mutates inventory or flight permission
at every scene callback before the automatic jump and requires denial.

The first new 2-CPS fixture skipped nearly 500 ms without PRE, conflicting with the
existing rotation controller's 250-ms reacquisition boundary. Its deadline check now
uses ordinary steps of at most 50 ms and still requires zero early attempts and one
attempt at exactly the deadline. The separate five-second-pause test is unchanged.
No existing tests, build workflow, golden data or POM changed. Exact-merge full Windows,
Golden, Security, CodeQL, release and packaged-runtime CI are recorded on the PR.
Native Minecraft, multiplayer/server acknowledgement, actual FPS/TPS/latency and
performance gains remain **NOT_RUN**.
