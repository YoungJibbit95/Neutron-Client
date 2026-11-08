# WAVE07 TECH T6a — Backtrack Receipt Safe (opt-in)

Base `main@8cd765e61512bc8c111bee9cdd61c94e0f208b19`. This branch is independent of Visual, T2–T4 AI branches, T5a Scaffold, and old PR #96. It touches only Backtrack's explicit opt-in mode value/packet timing checks and dedicated tests; no existing Neutron default, mode ordinal, config ID, HUD/render, PacketPipeline, NetHandler, Tickbase, LagRange, Velocity or Speed implementation changes.

## Upstream and provenance

PR #96 merged `CombatTimingCoordinator`, local impulse recovery, S14 FIFO/packet barriers, Backtrack/LagRange/Tickbase regression tests and config guarantees. This patch keeps `PingSpoofComponent.Owner` and `CombatTimingCoordinator` as the only timing and packet-delay arbiters. No direct queue mutation, packet cancellation or use of an alternative framework. Source comparison considered the public FDPClient family and original Neutron concepts. FDPClient repository source is GPLv3, thus **no FDP source is copied or ported** without a separate compatibility/license review. No Vibe implementation is copied or otherwise adopted.

## Real consumer

`Backtrack.backtrackMode` **appends** `Receipt Safe` at index 4. Default remains index 0 `Distance`; original `Distance`, `Timed`, `Pulse`, `Intave` values and all other config IDs remain unchanged.

The optional mode retains original target selection, reach/gain gates, request owner, exclusive timing window, accepted packet flow and local impulse handling. Additional restrictions, **only when this mode is selected**:

- No delay before a real S14 movement receipt belonging to the current target, nor when that receipt is older than 90 ms on the monotonic local clock. Context, target, mode, or world changes clear receipt provenance.
- One delay window at most 100 ms even when the user configured a larger fixed or range delay; each subsequent S14 uses the remaining time in the **same** window rather than renewing it.
- At most 12 pending held S14s for this owner. A 13th movement receipt releases the earlier FIFO packet batch before the next packet is handled. New movement, transactions, local velocity, knockback and disconnect safety barriers remain under the original PacketPipeline and PR #96.
- No changes to outbound C03, login, keepalive, transaction acknowledgement, S08 correction, attack scheduler or user input. Bounded release and cooldown keep ownership with existing coordinator.

## Tests/gates

`BacktrackReceiptSafeFlowTest` verifies no pre-receipt delay, real S14-driven timing, a configured 450-ms request clamped to 100 ms, strict FIFO drain, outbound C03 not delayed, long no-packet/low-TPS gap and actual S12 impulse release, 12-packet cap, unchanged legacy defaults and old-mode selection. The existing PR #96 regression matrix remains in full suite. No claimed anticheat bypass.

Full Temurin8u482, Maven3.9.15 exact merge SHA, Repository Security, CodeQL, Release bundle: **PENDING**. Native Minecraft/multiplayer, high RTT/TPS performance measurements: **NOT_RUN**. Changes remain Draft-only until all required gates and license/privacy review clear.
