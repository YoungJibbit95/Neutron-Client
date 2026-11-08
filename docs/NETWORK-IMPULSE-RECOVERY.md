# Combat timing and local impulse recovery

Date: 2026-11-08. Runtime: Minecraft 1.8.9 / Temurin 8u482.

The reported reproduction used the local `Mineblaze1v1Test` profile on HypeMC
and Mineblaze. Keep that file as the gameplay reference; this change does not
rewrite it or retune its combat values.

## Packet and simulation ordering

Accepted local S12 velocity and nonzero S27 explosion impulses enter one
connection-scoped recovery path immediately before vanilla changes motion.
Cancelled velocity events, cancelled inbound packets and another entity's
velocity do not enter this path. An event invalidates earned Tickbase/LagRange
credit and Backtrack holds before queued outgoing movement is published. This
keeps pre-impulse packets ahead of the applied impulse. Context changes during
release abort old-scene application.

During recovery the pipeline bypasses inbound/outbound movement delays, even
if a stale or foreign delay request remains registered. Recovery requires at
least the existing 500 ms settling interval, ground contact, finite nonascending
vertical motion and no local hurt state. Settings changes and module toggles do
not clear connection-level recovery. Scene changes and disconnect do clear it.

Backtrack holds only the selected entity's S14 movement in every mode. Local
health and selected/local entity status feedback first release Backtrack's earlier
movement. Health, damage feedback and sounds are no longer selected by its delay
matcher. Other packet owners retain their ownership and normal FIFO semantics.

## Window ownership, cooldowns and noise

Backtrack, Tickbase and LagRange share one timing-window owner per connection.
The current owner renews a bounded lease. Other modules run ordinary player
steps and cannot open another window. Releasing a window blocks every other
timing module for the sampled recovery gap, so alternating modules cannot skip
that gap. Context loss clears ownership; expired credit requires a fresh frame.

User-requested cooldown ranges:

| Module | Existing/config ID | Range | Default |
| --- | --- | --- | --- |
| Backtrack | `cooldown_ticks` | 0–400 ticks (0–20 s) | 10 ticks |
| Tickbase | `release_cooldown_ms` | 0–20,000 ms | 300 ms |
| LagRange | `release_cooldown_ms` | 0–20,000 ms | 500 ms |

Older LagRange profiles retain the prior 500 ms default. Existing keys and
reference Min/Max delays, charge counts, release speed and acquisition limits
remain valid. Production ranged-delay selection uses a seeded FastNoiseLite
OpenSimplex2S/FBm profile; cooldown selection uses a separate Perlin/FBm profile.
Each profile has two octaves. A delay is sampled inside the configured Min/Max;
a cooldown is sampled once between 90% and 100% of its configured maximum.
Zero remains zero. Already queued packet deadlines are not redrawn.

Reach, ownership, impulse recovery, packet identity and simulation-credit limits
are fixed correctness boundaries. They are not randomized. Noise does not
establish compatibility with an anticheat.

## Source reference and acceptance

Reviewed Intave's source-available
[Physics.java at 4f18a06](https://github.com/intave/intave/blob/4f18a06dce4066fa433d44cdd637472a928a73b0/src/main/java/de/jpx3/intave/check/movement/Physics.java),
especially its per-movement simulation commit and end-movement state transition.
This supports preserving movement/impulse ordering and ordinary physics rather
than treating hurt animation or elapsed time alone as synchronization evidence.
No Intave implementation is copied into Neutron.

Regression coverage exercises actual embedded packet handling, velocity during
an armed extra step, outgoing movement before velocity application, airborne
recovery, all four Backtrack modes, health-feedback ordering, mutual exclusion,
long cooldown persistence and bounded seeded noise. Live replay of
`Mineblaze1v1Test` with server-side logs remains a gameplay acceptance gate.

The final focused Java 8 run covered all network and configuration tests:
225 tests in 53 classes, zero failures, errors or skips. The isolated legacy
binding checks also pass with serialized windows and an independent packet
owner retaining the old-world discard proof. The earlier full working-tree
run had 1,181 tests, six failures and three skips: its four network fixture
failures are resolved by this change. Its MoveFix production-matrix and
continuous-Aura-click failures involve separately uncommitted work and remain
outside this patch. A new full-suite success is not claimed.

The shutdown-listener ordering and stale Tickbase diagnostic cleanup from
PR #95 are preserved. This patch is published on that open PR's branch as a
stacked Draft, pending gameplay acceptance and CI.
