# W08-A1 — Adaptive Bridge early acquisition and edge safety

Base: `c6612bf43ec2ce45bf686714374516f80788057e`. Delivery: draft PR #134.
Backtrack #127 and LagRange #128 are integrated; Tickbase #129 and Velocity #130
remain separate work. No main push, merge, release or Windows checkout change.

## Productive path

| Consumer | Behavior | Authority |
|---|---|---|
| `onEnable` → `previewBridgeOnEnable` | Capture bounded, validated geometry | Inert preview; no PRE or rotation budget |
| `handleMotion` → `bridgeMotion(PRE)` | Current physical keys, kept preview or bounded service search, immutable intent | Existing RotationManager collection, trajectory and mouse grid |
| Existing `applyDownwardsInput` → `applyBridgeSafety` | Fresh input, ownership and real foot collision checks after input listeners | Normal input and vanilla grounded sneak collision |
| `bridgeMotion(POST)` | Revalidate original support/face/stack and accepted C03, attempt once | Existing `alignAcceptedHit`, `tryPlace`, `placeWithAuthority` |

A prepared local movement pose is not an accepted C03. A successful local
controller application is not a remote server acknowledgement. POST never
searches for replacement geometry or inventory. Duplicate PRE/POST callbacks
cannot create a second placement budget, including after a rejected C08.

## Lifetime fixes

Epoch ordering belongs to a real connection identity. A new connection needs
the same owner and a strictly newer acquisition generation, and may start at
epoch zero. Same-connection epoch rollback is rejected. Weak identity tombstones
survive reset without retaining old worlds or players. Invalid older sequences,
old scopes and foreign threads cannot revoke a newer plan. Changed input in the
current sequence requires a new acquisition instead of a second planner pass.

Adaptive Bridge is excluded from legacy Downwards suppression. A saved hidden
`Downwards=true` therefore cannot remove its physical or guard sneak. Neutron's
explicit Downwards behavior remains unchanged and has a positive regression.

## Prediction and safety

`Bridge Prediction` is Adaptive-only, defaults to 1 tick and is bounded to 0–3.
Preview displacement is capped at 0.6 blocks. The adapter examines at most three
distinct foot/prediction cells with six faces each through existing `planFace`
and `isValid` checks. Both actual and predicted eye reach must be valid. A face
which is currently occluded is not promoted into a valid placement candidate.
The existing checked gap search remains available for an actual current gap.

The early hook reads current key bindings before vanilla refresh; the later
fresh-input hook invalidates an inconsistent PRE direction without replanning
the same sequence. Movement prediction uses the real prepared MoveFix basis
without claiming or applying another correction. Foreign movement owners keep
their inputs unchanged.

`Bridge Edge Safety` is Adaptive-only and defaults to `Smart`:

| Setting | Unsafe grounded edge |
|---|---|
| Off | No input request |
| Sneak | Sneak for the current input sample |
| Stop | Limit horizontal input and sneak against residual velocity |
| Smart | Slow sneaking approach for acquisition; stop on lost support/recovery |

States are `SEARCHING`, `ALIGNING`, `READY`, `PLACING`, `EDGE_GUARD`, `RECOVERING`.
They describe the controller and never replace existing placement authority.
The host probes a thin actual foot collision box and three path points for each
of residual velocity and velocity plus estimated input acceleration. Both paths
must be supported: opposite input cannot hide existing outward momentum. The
current-foot query is shared, for at most seven real collision queries per fresh
sample. Every query checks loaded cells and world borders; horizontal coordinates
are bounded before integer conversion. Probe distance is capped at 0.6 blocks;
a larger unexamined displacement is not declared safe. Four ULPs of rounding
tolerance avoid a false limit crossing.

Null input alone does not stop existing velocity. Grounded sneak uses normal
vanilla edge collision. No position, collision, timer or packet state is changed.
Existing physical sneak is never released or slowed twice. Physical jump remains
the user's choice; an automatic jump without the physical key cannot defeat an
active guard. Requests expire with the fresh sample. Safe terrain releases the
request; airborne input is not locked. Lifecycle, context, inventory and owner
changes revoke the acquisition. Missing usable blocks invalidate placement but
do not disable freshly measured edge protection. Successful placement still
requires a fresh terrain check before subsequent movement is considered safe.

## Validation and remaining gates

The final workspace passed Temurin 8u482 / Maven 3.9.15 full-source compilation
and 144 selected tests: 0 failures, 0 errors, 0 skips. These include the 19 planner,
16 safety, 6 acquisition and 12 production input/collision/reconnect tests, plus
existing placement, rotation, lifecycle, config and portable legacy regressions.
The counter-steering regression first failed against the previous compiled
consumer and passed after the independent residual-velocity sweep was added.
An isolated direct run also passed both existing legacy BindingChecks; their
unchanged Windows process wrapper remains part of Windows CI.

The initial four-file bugfix checkpoint passed all four GitHub workflows; its
Windows regression counted 1,454 tests, 0 failures, 0 errors and exactly the three
allowed native opt-in skips. That checkpoint is not final-consumer CI. The final
consumer/Windows JUnit/Golden, Security, CodeQL, release bundle and separate
PackagedRuntimeCheck evidence is recorded on the PR against its exact merge SHA.
Headless fixtures exercise production input, placement, transport and collision
methods; world storage and key/transport inputs remain synthetic.

Native Minecraft, multiplayer, server acceptance and FPS/TPS/latency metrics:
**NOT_RUN**. No performance gain or server/anti-cheat compatibility is claimed.

A owns planner/adapter; B owns safety and the exclusive Scaffold edit; E owns
independent QA. The integrator reviews and publishes serially. No shared hook,
RotationManager, EntityPlayerSP, packet pipeline, renderer, HUD or theme changes.
No Vibe/Gothaj code or external assets were copied.

The local prerequisite commit `ea157f6498fef6d636981c1ab8842a7c40f19ea5` and
GitHub checkpoint `9a4b1d3812a204cd96cbe42b501be7de2f8373f0` have identical tree
`63a7beee04e73d1a31c5c13142ba93aefc227f5f`. Authenticated connector publication
was used because local Git has no push credential. Final head/merge SHAs and
exact test counts are recorded in the PR, independently of this initial record.
