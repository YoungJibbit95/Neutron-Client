# Combat input, retained knockback and click timing

This follow-up investigates Aura movement during knockback, WTap2 Legit and click
setting parity with AutoClicker. Local acceptance means that the controller and
outbound packet path completed; it does not mean a server confirmed damage.

## Reproduced faults and fixes

The initial six production-flow probes produced three assertion failures and no
fixture errors. WTap Legit changed physical forward/sprint bindings even when C02
was cancelled, and did not consume its release in the next living input sample.
AutoClicker's Test mode returned 0.188 CPS with bounds set to 9–12. Its old raw noise,
Smoothstep input outside [0,1], and unnormalized Sin & Cos could escape the bounds.

WTap now captures eligibility before an attack without mutating input, arms only
after the matching `AttackResultEvent`, and consumes one next `MovementInputEvent`.
Only positive forward input is released; backward/sideways input and the physical
bindings remain intact. `MovementReadyEvent` suppresses sprint after vanilla sprint
admission for that sample, including when corrected sideways input would admit
sprint. Cooldown is recorded on acceptance using monotonic time. Disable, settings,
focus loss, world/player and connection-epoch changes discard pending state.
Spoof retains its existing movement-ready path. STap retains its timed key path;
only that mode owns physical key restoration on disable. Duration applies to STap;
Legit is deliberately one input sample, including with extra player steps.

## Retained knockback result and limits

No additional knockback scaling/rotation fault was reproduced. No new knockback
product code or generic Aura hurt-time lock was added. The tests retain nonzero
velocity through real `moveEntityWithHeading`, `moveFlying`, friction and gravity
over multiple steps, all five enabled Movefix modes, ground/air and forward/side/
diagonal input. Real inbound S12 replaces velocity; S27 adds its impulse. Target
loss and a rotation-only smooth return do not overwrite it.

Native horizontal ×0.6 reduction occurs only when local native damage succeeds
and the knockback modifier is positive (sprint or a knockback enchantment). Y is
unchanged. A nonsprinting, unenchanted hit and a locally rejected damage callback
retain the impulse even if C02 was accepted. Cancelled AttackEvent/C02 retain it
too. The remote-player class normally returns true locally; the rejected callback
is an explicit test double, not a claim of server damage confirmation.

The fixture uses actual module/controller/packet consumers and an EmbeddedChannel.
Input, world storage, a flat-floor collision integrator and the movement-speed
input (.1/.13) are synthetic; it does not run the whole game collision engine,
an actual server, native keyboard/GL, or an anti-cheat. These bounds do not establish
Intave/Polar acceptance or explain every reported server flag.

## Saved Mineblaze1v1 fixture

Only seven relevant module records were extracted into
`src/test/resources/combat/mineblaze1v1-modules.json`; the user's saved profile is
unchanged. Its original SHA-256 is
`fa3926960ba5b7285faff96ddfb890f709b16f518cc6007263d766ffcc04c9e5`.
The saved Aura-off state is tested as a negative control. Reported gameplay uses an
explicit Aura-on test override with the saved Advanced/Legacy Strict/Bezier/Noise
settings, CPS 9–12, reach 2.95 and hit-vector radius 1.38.

WTap, JumpReset, Sprint and HitDelayFix begin enabled as saved. The same seeded
scene and three eight-step knockback episodes run with WTap off, JumpReset off,
both off and Velocity separately enabled. Each variant must reach real Aura C02
attacks; there is no pass based solely on inactivity. JumpReset's chance draw is
fixed below the saved 60% threshold for reproducibility. Between impulses, flight
and retained motion continue; resetting to the floor on every tick was rejected
as an invalid positive-control setup for Velocity's jump behavior.

Several saved modules share key 11 (including WTap, JumpReset, Velocity and
HitDelayFix). The module manager toggles all matching bindings. Tests change modules
individually; the saved file alone does not establish which modules were active
during a user's game test.

## Click options and persistence

Both modules use `ClickRandomizer` with independent state. All eight modes are
bounded to sorted CPS limits: Basic, Next, Secure, Pi, Sin & Cos, Smoothstep, Perlin
and Test. Equal bounds produce the fixed CPS. Perlin and Test map bounded noise
into CPS; Test varies a second noise coordinate per sample. Enable Randomization
keeps its AutoClicker meaning: add up to the configured milliseconds to an interval;
the CPS mode is still active when that extra delay is off.

| AutoClicker ID | Aura ID | Default / range |
| --- | --- | --- |
| `min_cps`, `max_cps` | same | Existing module defaults; integer 1–20 |
| `enable_randomization` | `click_randomization_enabled` | true |
| `randomization_mode` | `click_randomization_mode` | Next / eight modes |
| `randomization` | `click_randomization_amount` | 3 / .5–5 ms |
| `debugging` | `click_debugging` | false |
| `block_frequency` | `block_frequency` | 4 / integer 0–10 |

Aura's existing **boolean** rotation `randomization` is separate from numeric click
randomization. Values register before UI construction and round-trip even when
hidden. Bounds normalize on the completed settings snapshot, so reversed JSON field
order cannot discard one bound. Legacy Aura configs without click keys select Basic,
no extra delay and debugging off; explicitly supplied new keys take precedence.

Aura's scheduler samples a pending interval once, then again after an accepted
attack. Rechecks/cancelled attempts do not keep drawing new intervals; reset does
not erase the shared one-attack tick budget or last accepted attack timestamp.
AutoClicker caches its own interval between clicks. Neither sampler creates packets
or bypasses admission, accepted rotation, raycast or controller gates.
Denied extra steps leave the pending interval untouched. A completed click retains
its immutable sample through synchronous setting callbacks; disabling during the
callback stops subsequent debug/block effects. Both cache boundaries have explicit
RED-to-green production-listener probes.

## Block frequency mapping

Aura adds `custom_block_frequency`, default **false**, preserving existing
Watchdog/Verus/Fake defaults and all legacy profiles. When explicitly enabled,
the existing AutoblockController may resume blocking in POST only on ticks divisible
by `max(1, 10 - frequency)`. AutoClicker uses the same tick predicate; frequency 10
now means every tick instead of a zero-divisor remainder that never succeeds.

This is option/tick-predicate parity, not identical block transport. AutoClicker
sets its existing use binding after a click. Aura retains one packet-block owner,
releases before attacks and on existing mode boundaries, and only gates resumption.
Manual use wins, and Fake sends no block packets. No second use-item owner was added.

## Validation

`CombatFollowupTest`, `CombatProfileTest` and `ClickRandomizerTest` cover the faults,
native attack conditions, retained impulses, saved-profile ablations, lifecycle,
all mode persistence, legacy migration, scheduler cadence and optional autoblock.
Each production-flow case uses a fresh normal-JIT Java 8 process. Local RED/green
logs, unchanged-source/foreign-file receipts and the final regression summary are
under `target/combat-followup-20261102`; runtime/account data is not committed.

Temurin 8u482 validation passed 678 tests across 109 core/config/movement suites,
with zero failures, errors or skips. After the final two cache guards and the
advancing profile-test clock, the five affected suites passed another 41 tests
with zero failures, errors or skips. Only AutoClicker and its profile test changed
between those runs. All 6763 source/POM hashes remained stable during each run;
the five pre-existing Performance WIP files and the original profile stayed
unchanged. This is scoped combat validation, not a full native/GL or gameplay gate.
