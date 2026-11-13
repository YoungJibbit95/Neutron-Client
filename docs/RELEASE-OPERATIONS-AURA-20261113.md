# Operation interruption, Adaptive Demise and Pre Aim

Scope: original release items **1, 4 and 6**. Starting main:
`bf3ccf495eaea3612dd0641fbd60fde9c1b20936`, including #193. Parallel PR #194
handles the other release changes.

## 1. Keep activation separate from operation state

The shared guard previously disabled its owner on damage or an exclusive-timing
conflict. Backtrack, Tickbase and LagRange now use explicit abort callbacks while
retaining enabled state, subscriptions and activation order. Cleanup blocks
reentrant work and same-tick restart. A new operation requires the usual fresh
scene, target, ownership and frame conditions.

Backtrack discards acquisition/receipt state; Tickbase discards credit, pending
release and both module/budget scene bindings; LagRange clears its active window
and budget. Same-world LagRange cleanup retains the existing unpublished-look
barrier until actual movement is published. World reset clears all scene state.
Unload blocks rebinding while Minecraft still exposes old pointers. Manual
off/on remains normal. FakeLag retains the shared guard's default disable policy.

**Abort on Damage** keeps `stop_on_damage` and the old name alias. Backtrack's
**Abort on Knockback** keeps `release_on_velocity` and its aliases. Defaults are
unchanged. Details: [operation safety](MODULE-ACTION-SAFETY.md).

## 4. Demise smoothing and Neutron point motion

Reference: [DemisePrivate e219a97](https://github.com/YoungJibbit95/DemisePrivate/commit/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb),
especially KillAura, RotationManager and RotationHandler; recorded AGPL-3.0
provenance is retained. As documented by the existing adaptation, the reference
README declares AGPL-3 and the pinned tree has no root LICENSE file; this change
does not establish a new distribution clearance. Original adaptation
`9e15630e55b0d3926b66a98fee7ae21ae21d4db3`
and inspected follow-ups `24fa090c80bf768b1560adcf454f7e8dd4d19d70` and
`dff5dcdbd71e5c72bce4874e42e48b8cd6800ad1` retained Neutron's controller. Evidence
supports an incomplete original adaptation, not a proven later removal.

| Selection | Result |
| --- | --- |
| Adaptive Rotation Engine: Demise | Fresh Adaptive default; reference frame smoothing, per-axis speed, acceleration/correlation, short stops and distance-speed reduction. |
| Adaptive Rotation Engine: Neutron | Existing motion-clocked speed/response/curve/path controller. |
| Adaptive Aim Point: Neutron Noise | Independent continuous in-box trajectory; choose a non-Off Legacy Hitpoint profile such as Balanced. Works with either engine and continues under valid-ray conditions. |
| Existing point options | Keep indices and behavior, including Legacy's former valid-ray hold. |

Demise controls use Rotation Speed, Rotation Shape and Rotation Pauses groups.
Point controls use Hitpoint. Hidden Neutron speed controls do not steer Demise.
Existing silent rotation, MoveFix and Rotation Return retain their roles.

Each acquisition owns its engine. Existing mouse hooks advance candidates using
the source frame step `min(dt, 1/90) * 120`. Frames cannot send packets or attack;
normal PRE requests, accepted C03, ownership, mouse-grid rounding and exact-target
checks remain authoritative. Scene/target changes, suspension, rejected movement,
competing ownership, settings changes and gaps over 150 ms invalidate old work.
Holds do not bank acceleration. Other engine consumers use zero distance reduction.

Source distance reduction is `((distance - minimum) / .01) * decrement` inside
the selected interval, then speed is clamped to 0–180; zero speed is possible.
Snapshots bound invalid values and normalize crossed ranges. New values append
after historical settings. Old configs missing the engine key choose Neutron;
explicit engine values win. Missing Demise entries reset to defaults.

## 6. Functional Pre Aim controls

| Slider | Default | Range | Meaning |
| --- | ---: | ---: | --- |
| Pre Aim Range (blocks) | 3 | 1–9 | Absolute acquisition range with Rotations enabled: `max(Reach, Pre Aim Range)`. Ordinary in-range selection remains available. |
| Pre Aim Prediction (ticks) | 0 | 0–3 | Horizontal relative-motion lead outside attack Reach. One tick is 50 ms; zero removes preparatory lead. |

Ordinary decimal slider conventions apply: two-decimal dragging, arrow steps of
1/100 span (0.08 blocks / 0.03 ticks), and bounded numeric input. Range default 3
and maximum 9 retain the former default/maximum `Reach + Aim Range Margin`.
Demise also separates search/attack distances; its search slider used default 4,
range 1–8 and step .1. The prediction range follows Neutron's existing 0–3 ticks.

Prediction scales target XZ displacement minus local-player XZ displacement.
Non-finite/discontinuous samples are rejected and lead is capped at 1.5 blocks,
following existing Arsenic conventions. The live entity box is never changed.
Noise is sampled inside the live box before preparatory lead is applied.

Basic/Smooth/Advanced and both Adaptive engines use this horizon. Arsenic uses it
instead of ordinary Prediction Ticks during preparation, including at zero;
inside Reach ordinary Arsenic prediction remains. AI Learned keeps its model
inputs unchanged: Prediction is hidden there, while Range still applies.
Prediction is visible when Rotations is on and Pre Aim Range exceeds Reach in
a supported mode. Rotations off uses Reach alone. All attack checks continue
to use the current target and attack Reach; prediction grants no extra reach.

The hidden `aim_range_margin` retains its registration index. Imports missing
`pre_aim_range` derive it from final incoming Reach plus margin, independent of
member order; explicit new values win. Missing prediction resets to zero.
Old direct margin setters update the new range. Imports do not rewrite profiles.

## Validation and remaining acceptance

Exact commands, tested revisions and results are recorded in the PR. Regressions
cover activation-preserving aborts, queue/credit cleanup, world boundaries,
reference-engine parity, real frame hooks, moving Neutron points, config
migration and acquisition/attack separation. Existing generic safety, FakeLag,
non-Adaptive, Scaffold and BedBreaker contracts remain covered.

Fixtures exercise real patched walking/packet hooks with controlled scenes and
transports. They do not establish live-server acceptance. For game/server
acceptance, check
stationary/moving targets, switches, Aura/Scaffold ownership changes, GUI/focus,
damage/knockback, disconnect and fresh world load. Record revision and environment.
CI alone does not prove anti-cheat compatibility. Merge requires the repository's
current-main CI and resolved-review gates; keep Draft while an applicable required
gate is open. Release publication and server-compatibility claims have additional
game/server acceptance requirements, which this source PR does not claim to fulfill.
