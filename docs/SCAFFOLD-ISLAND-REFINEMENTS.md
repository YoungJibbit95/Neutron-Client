# Scaffold and Island refinements — 2026-11-10

Follow-up to merged PR #170 and the user's hands-on testing.

## Aim behavior

Adaptive Bridge keeps a valid preferred intent before another current-ray acquisition. Occupied route cells skip face enumeration. New targets must belong to the existing narrow route or bounded diagonal connector. Faces remain constrained to the approved destination and stack.

New face points project the held direction onto the visible face or nearest inset edge. Diagonal faces tend toward their shared edge instead of their centers. Angular continuity outweighs small reach differences. A valid accepted ray takes priority over variation.

Godbridge starts with a 45-degree heading-grid / 83.52-degree pitch preference, subject to actual reach and visible geometry. It approaches that solution directly within the speed cap, retaining its anchor across slow acquisition. Later blocks keep the accepted angle when valid and otherwise make small corrections. The world-space hitpoint moves with the player even when the angle remains fixed.

Diagonal input changes up to 60 degrees preserve acquisition, cadence and noise time; lane steering reanchors independently. Stops, restarts, perpendicular turns, reversals and lifecycle boundaries revoke acquisition. Current route checks still reject off-route targets.

Regression testing exposed near-edge points that passed continuous geometry checks but hit another face after sensitivity rounding. Refinement checks the representable ray and at most nine neighboring mouse counts on that same face, with one bounded fallback to the original point. It cannot change support, destination, stack or placement authority. Final PRE/C03/POST checks remain authoritative.

## Controls

| Setting | Default | Meaning |
| --- | --- | --- |
| Sprint Mode | Off | Off, On or Legit, across approaches and searching frames. |
| Bridge Rotation Profile | Adaptive | Adaptive or Godbridge geometry. |
| Min Aim Speed | 35 | Lower requested travel speed in degrees per nominal 50 ms; never a minimum angular step. |
| Max Aim Speed | 180 | Mathematical per-axis cap, including clutch recovery. |
| Aim Noise Profile | Natural | Off, Steady, Natural, Lively. |
| Aim Noise Amount | 0.25 | Correlated speed/easing variation strength. |
| Aim Noise Tempo | 1.0 | Profile pace, also scaled by global aim speed. |
| Place Min / Max CPS | 12 / 16 | Existing placement cadence bounds. |
| Advanced Bridge Settings | Off | Prediction, phase limits, interpolation, variation and diagnostics. |
| Phase Speed Limits | Off | Optional initial/keeping caps can narrow the global cap. |

FastNoiseLite OpenSimplex2S shapes travel speed/easing with temporal filtering and bounded change. Face changes do not restart the pattern. Urgent tasks remove avoidable easing delay within their caps. Settled aim receives no random angular drift. Final native mouse rounding can differ from the mathematical cap by half a quantum.

Min/Max endpoints normalize if reversed. Noise sliders hide when Off. Closing Advanced does not discard enabled phase limits. Legacy Neutron rotation controls no longer appear as competing bridge settings.

Old Aim Speed, Bridge Rotation Speed, Bridge Rotation Variation and Boolean Sprint keys still load. New saves store effective range/noise/sprint controls. Old Sprint=true maps to On, false to Off. Explicit modern settings win over aliases regardless of JSON order. Loading leaves source files unchanged.

## Sprint ownership

Off suppresses sprint throughout the active Scaffold context, including missing blocks, target loss and searching. On forces sprint. Legit requires corrected forward input at the vanilla threshold, food/creative-flight permission, no collision, sneak, item use or blindness. It does not borrow NoSlow item-use permission or simulate a pressed key.

The policy filters EntityPlayerSP sprint changes, runs before physics and finalizes after PRE listeners before the ordinary sprint action packet. No new packet path is introduced. Disable/reconfiguration or scene replacement during a query invalidates its decision.

## Island and evidence

[ISLAND-WIDGETS.md](ISLAND-WIDGETS.md) documents the optional modern clock, single notification timeline, CPS/FPS/exploit widgets with shared Inside/Outside choice, and fixed 224 x 46 Scaffold canvas. The modern base is 234 x 34 before optional inside widgets. Scaffold narrows and expands downward without morphing on status/count changes.

Scaffold CPS/history use confirmed placements, not searches or requested CPS. Base CPS measures real in-game left/right mouse button-down events. Exploit displays use owner-specific holds, earned credit or recently completed extra steps. Reading these views never pumps queues or performs gameplay actions.

Coverage includes physical movement/placement flows, low-speed Godbridge acquisition/retention, diagonal continuity, sensitivity, sprint without a plan and native setter/attribute behavior, reentrant owner withdrawal, config migration, notification fallback and widget geometry. Old sprint/visibility assertions reflect the requested Off semantics and Advanced controls; physical no-fall/placement contracts remain unchanged. Repeated synthetic clients use isolated JVMs where needed. Final totals and CI links are in the PR body.

Native gameplay/visual acceptance remains separate. This environment lacks an interactive Minecraft/GL session and the user's Windows IntelliJ checkout. Remaining checks: Godbridge/diagonal turns, low aim caps/clutch, sprint modes, practical GUI scales, stable Scaffold states, clock/widgets, F1/screen notification handoff, Reduced Motion, GL restoration and frame times. AGENTS.md requires Draft status while this acceptance is open.

Only source, tests and documentation belong to this change. One sparse source checkout was needed in the available workspace; no additional worktree or client runtime/world copy was created. Merge and release remain separate steps.
