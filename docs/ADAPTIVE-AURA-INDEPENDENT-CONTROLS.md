# Adaptive Aura: independent motion and in-box points

These controls describe `Adaptive Rotation Engine = Neutron`. The later
[Demise engine and explicit Neutron Noise choice](RELEASE-OPERATIONS-AURA-20261113.md)
separate smoothing from point variation. Legacy retains the hold behavior below;
Neutron Noise continues moving while Keep Valid Aim is enabled.

This change extends the existing Adaptive mathematical candidate planner. It does
not replace the action pipeline, change reach, expand a target box, add a packet
path, or provide server-specific movement patterns.

## Speed and curve

`Adaptive Min Aim Speed` and `Adaptive Max Aim Speed` remain degrees per second.
They choose the preferred travel speed; the maximum also bounds the combined yaw
and pitch displacement. The minimum is not a minimum step near a target. Arrival,
acceleration, the mouse sensitivity grid and `Keep Valid Aim` can all require a
smaller step or no movement.

`Adaptive Response` retains Calm/Balanced/Swift and controls acceleration and
braking. It does not multiply the selected maximum speed.

The Relative curve previously had a separate fixed response rate. At a 50 ms
interval, that cap was about 5.184 times the remaining angle per second, which
often hid changes to the speed sliders. Relative now derives its response from
the selected travel speed and the acquisition's angular span. `Adaptive Curve
Strength` sets the fraction of that span used for easing. Increasing it produces
a longer easing region; zero removes the extra curve shaping. Adaptive curve
strength blends its braking envelope, while Linear has no extra curve-strength
control. All three retain the common acceleration, jerk, residual-distance and
final sensitivity-grid limits.

## Three independent kinds of variation

| Controls | Effect |
| --- | --- |
| Adaptive Speed Noise Profile / Amount / Tempo | Continuous speed preference within the selected range. Off or zero amount removes this variation. Equal min/max speeds also remove its effect on speed. |
| Adaptive Path Amount / Tempo | Bounded deviation in the direction of an approach. Zero is a direct path; it does not disable speed or point variation. The envelope is zero at acquisition and arrival. |
| Adaptive Initial Hitpoint Radius | A point offset selected once per genuine acquisition and expressed relative to the real box half-extents. Zero retains the anchor. |

The historic persisted IDs `adaptive_noise_profile`,
`adaptive_variation_strength` and `adaptive_noise_tempo` remain unchanged. Their
previous display names and the clearer Speed Noise names resolve to the same
values. There is no new global noise master that silently scales every channel.

For non-Legacy anchors, the existing Stable/Organic/Dynamic point styles remain.
`Adaptive Hitpoint Amount` scales their radius and `Adaptive Hitpoint Tempo`
scales their movement tempo independently of speed noise. Stable has no ongoing
point movement, but an explicitly selected initial radius can still choose a
different initial point.

`Adaptive Keep Valid Aim` still holds the accepted rotation whenever the current
ray hits the selected target. It intentionally takes precedence over continued
path or point movement. To inspect continuous point movement, turn this option
off; the existing ray and action checks remain mandatory.

## Legacy anchor point controls

These controls are available only with `Rot Mode = Adaptive` and `Adaptive Aim
Point = Legacy`. They do not alter Arsenic, Basic, Smooth, Advanced or AimAssist.

| Profile | Radius (box half-extents) | Travel tempo | Tempo variation |
| --- | ---: | ---: | ---: |
| Off | 0% | — | 0% |
| Subtle | 10% | 0.35 | 10% |
| Balanced | 28% | 0.70 | 25% |
| Strong | 60% | 1.30 | 45% |
| Custom | 0–100% | 0.1–3.0 | 0–100% |

OpenSimplex2S, Perlin and ValueCubic are available as ordinary coherent noise
algorithms. Travel speed is a noise-coordinate tempo per second, not rotation
speed or an attack rate. Travel noise varies that tempo; it does not expand the
radius. The initial offset and traveling offset are combined around the anchor,
and the result is always constrained to the actual interior of the current box.

The planner stores acquisition-local state and advances from motion time. The
same time sample cannot consume additional movement. A long interval contributes
at most 75 ms to point movement; RotationManager still owns the existing pause,
sequence and scene invalidation rules. Noise objects are created per acquisition,
not per rendered frame. The helper performs a fixed number of scalar samples and
introduces no new world searches.

There is no added Miss Chance setting. The pre-existing Arsenic off-box excursion
behavior is not imported into Adaptive. A candidate point must still pass the
existing target-ray checks, and a candidate never grants action permission.

## Configuration and registration compatibility

New values are registered by `RotationSettings.appendAdaptiveTuning` at the end
of Aura's constructor, after its existing Smart Disable values. Inserting them
into the RotationSettings constructor would shift later Aura value indices;
that registration path is deliberately avoided. Calling the append hook twice
does not register duplicate values.

The new persisted IDs are:

```text
adaptive_curve_strength
adaptive_path_amount
adaptive_path_tempo
adaptive_initial_hitpoint_radius
adaptive_hitpoint_amount
adaptive_hitpoint_tempo
adaptive_legacy_hitpoint_profile
adaptive_legacy_hitpoint_algorithm
adaptive_legacy_hitpoint_radius
adaptive_legacy_hitpoint_travel_speed
adaptive_legacy_hitpoint_travel_noise
```

On fresh modules, path amount and initial radius are zero, the Legacy point
profile is Off, and ordinary Hitpoint Amount is 100% of the selected point style.
During an old-config import, missing path/ordinary-point amounts and tempos are
derived from the incoming coupled Adaptive noise values, bounded to their new
visible ranges. Thus an old global Noise Off setting does not unexpectedly turn
on the formerly coupled channels. Explicit new zero values take precedence.
Other missing new controls reset to their defaults. Import does not rewrite the
user's source file and does not read hidden old Legacy/Arsenic noise settings.

The explicit Adaptive trajectory intentionally changes because the speed/curve
coupling has been corrected. The historical no-Control motion path and old
non-Adaptive settings retain their existing contracts.

## Integration and validation

RotationManager constructs its snapshot through
`AdaptivePresetMotion.Configuration.fromProfile(profile)` and uses
`preset.changesHitpoint()` to determine whether an initial or traveling point
should be considered. Its existing scene checks, same-sequence cache, target-ray
validation, ownership and quantization remain in place. Invalid candidate points
fall back through the existing path; they do not expand hit permission.

Focused tests:

- `AdaptiveAimTuningTest`: useful min/max and curve changes, independent channels,
  physical speed limits, stable-point initial offsets and snapshot identity.
- `AdaptiveHitpointMotionTest`: actual-box bounds, acquisition-local initial
  sampling, independent radius/tempo, repeated queries, invalid input and pauses.
- `AdaptiveAimTuningConfigTest`: append-only values, aliases, visibility,
  round-trip, explicit zero precedence, old-config migration and atomic rejection.
- `AdaptiveAuraIntegrationTest`: added isolated real-walking cases for the
  accepted mouse grid, combined cap, valid-ray holding, rejected transport and
  reconfiguration with independent Legacy controls enabled.

These tests do not establish live-server acceptance or a measured FPS gain.
The real-walking fixtures run in separate JVMs because their synthetic Minecraft
references use Unsafe and otherwise may become stale under Java 8 JIT folding.
