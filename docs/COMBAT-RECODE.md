# Combat recode, 2026-11-02

Implementation is in the main checkout. Arsenic was reviewed at the immutable
revision linked in [the attribution record](third-party/ARSENIC.md), including its
delay queues, timer balance, input hook, synthetic key display, ping tracking and
accepted action history. Neutron's existing packet, rotation and controller
contracts remain the integration points.

| Module | Behavior | Neutron integration |
| --- | --- | --- |
| Backtrack | By default, lock the last accepted attack for up to 1 second. Hold only while the received target position provides a distance advantage. Default delay 100 ms, cooldown 10 ticks. | Keep accumulated receipt positions separate from applied/rendered positions. Hold target status, positive health and sound feedback with movement. Release on nearer real position, target teleport, knockback or invalid target. Corrections, death and world changes discard obsolete queued work. |
| LagRange | Start beyond 5 blocks; grow delay over 600 ms towards a sampled 300–500 ms cap. Release on attack, retreat or target loss, then cool down for 500 ms. | Hold outbound movement only. Actions remain FIFO barriers, so movement arrives before the attack. Keepalive/transaction/action packets retain the existing immediate path. Incoming enemy movement belongs to Backtrack. |
| LagRange timing | Optional Speed Up, Slow Down and Dodge. Slow Down skips at most three consecutive player bodies while the target is still hurt. Speed Up runs only when earned steps cover the full approach to 3-block eye-to-box reach. Dodge changes strafe on these extra steps only. | Credits come only from actual withheld bodies, cap at 10 and expire after 2 seconds. Default 50 ms reserve, fresh normal-timer frame required. Tickbase has scheduling priority. No credit from inferred AC clock drift. A dig after withholding must match the last published look. |
| Velocity | Legit mode: one optional input jump after applied upward knockback, then up to 1–3 reduction opportunities (default 2). Choose a visible player in reach; rotate up to 18 degrees per step. | Use shared rotation arbitration below Aura/Scaffold priority, accepted-motion raycast, global attack budget and normal controller attack. No packet cancellation/scaling, timer manipulation or arbitrary motion multipliers. Aura retains its own attack cadence. |
| JumpReset | Require local hurt within 150 ms plus a nearby player's swing within 400 ms. Reject projectile items, water/lava, GUI and reused entity IDs. Chance defaults to 100%; each hurt confirmation is single use. | Reset before local S12 application; the server's Y replaces the reset immediately. Never append a sprint impulse or overwrite applied Y. When both modules are enabled, JumpReset owns jump handling. |

The standalone JumpReset deliberately follows the pre-application ordering of the
reference. Its reset flag is not evidence of server-side knockback reduction.
Velocity's jump is a separate normal input sample, handled by vanilla movement.

## Movement correction

The previous Simple transform produced non-keyboard axis strengths such as 0.707
and 1.414. Precise produced arbitrary analog axes. Their sprint eligibility was
still checked against physical W, and their sprint-jump impulse could use camera
yaw while strafing used server yaw.

Simple and Precise now select the nearest of eight keyboard directions, retaining
the original per-key slowdown (including sneak, item use and vanilla 0.98 damping).
Vanilla applies its diagonal cap once. Both labels remain readable from existing
configs and use the same corrected policy. Legacy Rounded retains round-to-key
mapping; Rotation Bound and Legacy Strict keep keys relative to confirmed yaw.
Every enabled mode derives sprint admission from the transformed forward key and
uses confirmed yaw for the vanilla sprint-jump impulse. Off retains vanilla input.
Physical key state and the camera are not overwritten.

## Compatibility and validation

Existing Backtrack target-source options and bounded delay settings remain
available. Legacy Velocity mode names import as Legit; removed packet/motion
parameters are ignored by the existing unknown-setting mechanism. Existing
LagRange millisecond/tick settings now cap the progressive delay rather than
opening repeated fixed-duration windows.

New tests cover actual S12 application and input consumers, hurt/swing evidence,
packet cancellation, context invalidation, same-tick attack arbitration, movement
before attack, retreat, feedback ordering, lethal-health discard, earned-step
accounting and old configuration imports. The Movefix regression was run against
the old implementation first: all three new invariants failed as expected.

Validation on 2026-11-02 used Temurin 8u482 and Maven 3.9.15 in the main checkout:

- Focused combat/network regression: 43 tests passed.
- Compatibility follow-up: 12 tests passed.
- Final `mvn test`: 882 tests, zero failures/errors, three existing opt-in native
  tests skipped. The repository's `check-java8-results.ps1` verifies the exact
  allowed skip list. This run also contains the concurrently integrated GUI work;
  the combat PR contains only the files listed in its own diff.
- Local evidence: `target/combat-recode-red.log`, `target/combat-recode-focused.log`,
  `target/combat-recode-compat.log`, `target/combat-recode-full-green.log` and
  `target/combat-recode-test-summary.json` (not committed).

Server/AC acceptance requires a separate game session with logs. Automated tests
verify client behavior and lifecycle contracts; they do not establish that every
server configuration accepts packet delay or tick timing. No multiplayer
acceptance or universal absence of flags is claimed.
