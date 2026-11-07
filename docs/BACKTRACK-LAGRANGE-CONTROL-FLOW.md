# Backtrack and LagRange control flow

## Confirmed early release

The shared packet pipeline previously treated transaction confirmations and their
replies as movement barriers. An incoming rejected S32 transaction could release
Backtrack's inbound movement queue. Its real NetHandler reply (C0F), or a keepalive
reply (C00), also released LagRange's outgoing C03 queue. A server sending these
controls frequently could therefore shorten an otherwise valid delay window.

S32, C0F and C00 now pass immediately without releasing unrelated held movement.
The existing incoming S00 exception remains. These packets are never delayed.
Attack, use, velocity, teleport and lifecycle barriers remain unchanged. Other
conservative barriers, including S1C metadata and S04 equipment, can still shorten
a window; this change does not classify all unrelated entity packets as safe.

## Backtrack modes

`Backtrack Mode` is separate from the existing `Delay Mode` (Fixed/Range).

| Mode | Behavior |
| --- | --- |
| Distance | Existing default: start when received target distance exceeds rendered distance plus Min Distance Gain; release when the target returns toward its last applied position. |
| Timed | Delay each eligible target packet by the sampled window delay, independent of distance gain. Later packets retain their later deadlines. |
| Pulse | Give a batch a shared deadline; late arrivals receive only the remaining delay. Release the batch, then observe the existing cooldown before rearming. |

All modes retain target identity/source, acquisition range, minimum server
distance, hurt-state/input filters, stale-position and lifecycle checks. Delay is
bounded to 500 ms by the existing pipeline. FIFO, queue capacity, owner leases and
maximum queue age are unchanged. Pulse expiry is checked from both Tick PRE and
target movement receipt, so receipt does not require a preceding tick to end it.
Old configs without the new mode load Distance, even after Timed or Pulse was
selected. Invalid config values are rejected before settings are applied.

## Understanding activation

Backtrack's default source is Attack: an accepted attack must establish a target.
Its default lock lasts 1000 ms; Distance additionally requires a gain above 0.08
blocks and server distance above 2.2 blocks. No suitable target, hurt filters,
cooldown or a zero delay can legitimately produce no held packets.

LagRange starts beyond Start Distance, with an eligible target within Max Target
Range plus Preactivation. A Start Distance at or beyond that selection limit
leaves no activation interval. Delay ramps toward the configured value over
600 ms. Attack releases previous movement in order and starts the existing
500 ms release cooldown. These behaviors are unchanged.

Extra player steps still require credit from actual withheld player-step bodies.
Tickbase owns the scheduler when active. Fresh frame, multiplayer, movement,
closing-distance and other existing checks continue to restrict spending.

Enable **ArrayList → Show Module Info** to see Backtrack's mode, the active delay
and the number of packets held by that module (for example, `Pulse 100ms Q2`).
LagRange uses the same delay and queue display. Each frame reads the current
owner count, including packets queued or released since the last game tick.
The option defaults off. The detailed Diagnostics notifications work independently.
`getStatus()`
exposes the detailed reason without pumping or modifying the queue. Enabling
Diagnostics uses the existing notification system for changed reasons, at most
once per two seconds. LagRange additionally reports withheld/spent step counts
and Tickbase ownership. Notifications can be hidden by existing user settings;
Diagnostics defaults off and does not write chat messages or packet contents.

## Verification boundary

Hook tests exercise real S32-to-C0F and S00-to-C00 handler responses, held inbound
movement and outgoing C03, and the unchanged attack release ordering. Separate
cases compare stationary target activation, rolling versus shared deadlines,
cooldown, legacy config migration and invalid config preservation.

Local evidence is kept in `target/backtrack-lagrange-20261103`. The initial two
regression tests failed against the old pipeline. The first broader subset also
exposed a pre-existing test-order dependency: KnockbackRecodeTest accessed Items
before Bootstrap.register(), poisoning later Items access in PlayerSpawnProfileTest.
The test now registers its own required bootstrap explicitly; assertions and
production item initialization are unchanged. This is a local hook/config
verification, not a live server or anti-cheat acceptance test.
