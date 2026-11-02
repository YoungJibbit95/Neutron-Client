# Optional Bezier rotation speed

Aura and AimAssist have an independent **Speed Curve** option. Its default is **Off**. Existing rotation modes and randomization presets retain their previous defaults and do not turn this option on.

| Setting | Default | Meaning |
| --- | --- | --- |
| Speed Curve | Off | Choose Off or Bezier. |
| Bezier Strength | 0.5 | Maximum fractional speed reduction used to select the curve controls; range 0 to 0.9. Zero gives a constant multiplier of 1. |
| Bezier Duration | 500 ms | Active motion time over which one acquisition's curve runs; range 100 to 2000 ms. This is not a guarantee that the target is reached within that time. |

Strength and duration are visible only while Bezier is selected. Scaffold does not register these settings. Existing yaw/pitch speeds remain degrees per 50 ms; duration is milliseconds. The new curve is independent of existing angular noise and Speed Variation. Selecting the existing randomization preset Aus does not override an explicitly selected Bezier speed curve.

## How speed changes

The implementation uses a bounded cubic velocity multiplier, not a Bezier position path or a CSS easing curve. For normalized acquisition time t:

```
v(t) = (1-t)^3*v0 + 3*t*(1-t)^2*v1 + 3*t^2*(1-t)*v2 + t^3*v3
v0 = 1 - strength*u0
v1 = 1 - strength*u1
v2 = 1 - strength*u2
v3 = 1
```

The three seeded values u0..u2 are selected once per acquisition. Source/scene epochs, target binding and the acquisition ordinal participate through the existing trajectory seed chain. With valid settings, each control and the complete multiplier stay within [0.1,1]. The multiplier can reduce the existing speed but cannot increase it. Its shape can accelerate or decelerate; no particular ease-in/ease-out shape is promised for every seed.

Each actual motion interval uses the average of this cubic over that interval. Exact Simpson evaluation provides that average without subtracting two nearly equal integrals. If an interval crosses the end, its remaining part uses v3=1. Completed acquisitions continue at multiplier 1. The helper also exposes the analytic integral for independent numeric verification.

Both axes use the same envelope multiplier in addition to their existing speed variation. Existing per-axis caps and pitch limits still apply. The enabled path additionally prevents a long Advanced-mode interval from stepping past its desired angle. Noise can still move the desired aim point according to its own existing settings.

## Motion and lifecycle

Real consumers use the RotationManager motion sequence. The first enabled calculation captures one timestamp for that sequence; further calculations reuse the cached candidate. Render sampling cannot spend another interval. A different target within an already consumed sequence receives no extra first-step allowance and arms on the next motion.

The first step uses the existing 50 ms allowance. Later elapsed intervals are limited to 100 ms. Stop on Target consumes the timing baseline while freezing curve progress, preventing catch-up after a pause. A target/source/scene reset invalidates the acquisition. Changing strength or duration rearms the curve when the next motion is calculated.

A canceled, held, unmatched, or foreign-owner motion consumes its attempted timing interval but does not commit candidate angles. Its Bezier acquisition is invalidated and seeded again on the next eligible motion. Resetting is idempotent; the acquisition ordinal advances when controls are selected, not when reset is called. The existing accepted C03 result remains the sole authority for effective rotation and owner state. No new attack, movement, or packet budget is introduced.

## Quantization and manual camera input

Existing final sensitivity quantization is unchanged. It rounds to the nearest mouse quantum, so the final rounded step can exceed the pre-quantization speed cap by at most half a quantum, apart from ordinary float tolerance. Vanilla pitch clamping is the existing boundary exception to the grid.

Aura's silent request is based on the last accepted wire pose and leaves the camera alone. AimAssist applies assistance to the current camera, preserving mouse input that arrives before resolution. That later manual input is outside the assistance speed bound; an arbitrary manual camera delta is not claimed to lie on the previous wire pose's mouse grid. The feature does not silently rewrite either contract.

## Compatibility and verification

Old config files that omit Speed Curve load it as Off, including a warm session where it was previously enabled. Explicit stored Bezier values still apply. Aura and AimAssist use the existing validated config batch hook; inactive strength/duration tuning is retained, and invalid config input is rejected before the batch begins.

The OFF branch retains the previous calculation and clock/noise sample order. Before implementation, frozen float-bit signatures were captured for all 60 combinations of three trajectory modes, four angular patterns and five presets, including pauses, target switches, wrapped yaw and irregular timestamps. A separate real-consumer signature records actual Aura/AimAssist C03 output, ownership and camera values. Both signatures match after implementation:

```
PURE      b1554771927979f60c19602df67ea374ff6d8abddf9a0c7098ce713999a1dc84
CONSUMERS 44c8af34b9eb0bbe4b3a24df070c0de5ee9568a9dda646a4a53923bc4a0d6bf4
```

Focused Java-8 evidence covers 2,048 independent numeric samples, de Casteljau evaluation, Gauss-Legendre integration, 20/60/144 render schedules with an identical motion timeline, actual visible/silent consumers, cancellation/hold/foreign-owner rearming, config compatibility and final rounding. These are automated synthetic-scene checks, not a live-server or anticheat result. Different simulation-step timelines may legitimately produce different packet sequences.

The first focused run passed 18 feature/golden tests (Config 3, Consumers 6, Math 3, Trajectory 4, Golden 2) with no failures, errors or skips. The broader run also contains separate work and is not described as fully green here. Exact final-commit full-suite, native and user-playtest status must come from the integration receipt.

No anticheat acceptance, human-like behavior, FPS improvement, or server-side outcome is claimed.
