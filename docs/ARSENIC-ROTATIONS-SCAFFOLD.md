# Arsenic rotations and Scaffold

Reference revision: `ArsenicClient/Arsenic@ee93d48a4b78b6383601c63b7269eb4c0e397e85`.
Attribution and the upstream MIT license: [ARSENIC.md](third-party/ARSENIC.md).

## Selecting the approaches

Aura's **Rot Mode** and AimAssist's **Rotations** now offer **Arsenic Lazy** and
**Arsenic Heuristics**. Existing defaults and legacy modes remain available.
Lazy combines a minimum-jerk flick with smoothly changing tracking gain.
Heuristics adds delayed target history, spring pursuit and optional excursions.
Both use the upstream inset hitbox, moving aim-point drift and relative-motion
prediction. Prediction Ticks defaults to 1 for Aura and 3 for AimAssist;
Miss Chance applies only to Heuristics. Yaw/Pitch Speed remain bounds, with the
upstream additional 8-degree pitch cap. Neutron's nearest mouse-grid rounding can
add at most half a mouse quantum to the pre-quantization angular limit.

These modes own their smoothing and aim point. Noise, Bezier and legacy hit-vector
controls are hidden and do not stack onto them. Each source has its own controller;
one motion sequence advances it once. Target object replacement, context loss,
rotation rejection, ownership loss and pauses longer than 250 ms discard history.
Prediction ignores discontinuous position samples and caps total lead at 1.5 blocks.
Flicks start with a two-tick budget and extend it according to angular distance
and the configured speed limit; Neutron retains its independent attack scheduler.
AimAssist uses the upstream additive concept: assistance adds to the current camera,
including later manual mouse input. Mouse override/buffering is not installed.
Aura's existing attack timing, selection and accepted-rotation/raycast gates remain.

**Scaffold Approach = Arsenic** enables the upstream predictive face search,
backwards/side heading, lane nudges and Eagle press cadence. Eagle defaults on,
Safety to 2 ticks and rotation speed to a random 180–360-degree budget. The upstream
hard-edge sneak protection remains active even when Eagle's optional cadence is off.
Arsenic's momentum/easing then limits actual turn increments. Existing Neutron approach and
configuration defaults remain available. Arsenic always uses ordinary POST
placement with Simple Movefix; legacy tower, timer, downwards, rotation style and
keep-Y controls are hidden in this approach.

## Neutron boundaries

Scaffold prepares one immutable support/face/stack intent in PRE. POST may refine
its hit vector using the accepted wire pose, but cannot switch to a different face
or replace its captured stack/state. A canceled/replaced/foreign movement cannot
grant placement authority. Existing reach, collision, loaded-world, border,
inventory, selected-slot, action-budget and controller checks still apply.
The upstream duplicate-look perturbation is omitted: quantization and accepted-ray
validation determine the actual placeable face. Inventory selection stays in the
existing checked placement service; no global slot or key-binding mutation is added.

Planning returns only a proposed face and angles. The module checks its lifecycle
again after world and scene-supplier callbacks, before publishing a rotation or
cleaning up an old plan. Disable/re-enable callbacks cannot authorize old work or
clear the replacement generation's state.

Lane and Eagle modify fresh movement input only. Physical sneak remains held and
is not slowed twice. Edge previews do not advance press cadence; the POST update
advances it once. Prediction uses the confirmed local movement basis separately
from the requested silent look. Sprint admission is disabled before ordinary
movement/jump physics while this approach owns an active scene.

## Validation and remaining acceptance

Tests cover the pure aim controller, relative-motion prediction, motion idempotence,
identity reuse, sensitivity-grid integration, visible manual input, configuration
roundtrips, actual walking/placement hooks, rejected movement and input cleanup.
These are automated client-side checks, not a live multiplayer acceptance report.
The maintainer explicitly deferred gameplay/AC testing while this logic is prepared;
no absence of Intave/Polar flags is claimed. The new implementation remains a
separate reviewable PR after the authorized merge of #26.
