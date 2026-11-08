# WAVE07 TECH T6b — LagRange Bounded Frame (opt-in)

Parent: `main@8cd765e61512bc8c111bee9cdd61c94e0f208b19`. Independent of #127 Backtrack, AI, VIS and T5 Scaffold. PR #96 remains the authoritative existing `PacketPipeline`, `PingSpoofComponent.Owner`, `CombatTimingCoordinator`, `LagRangeBudget`, local-impulse and target lifecycle basis.

## Compatibility

`LagRange.Delay Mode` **appends** `Bounded Frame` at ordinal 3. All existing `MS`, `Ticks`, `Intave Approach` IDs and the ordinal-0 default `MS` are unmodified. No legacy mode logic or reference config ID changes. No third-party code copied (FDPClient public source is GPLv3; Vibe licensing not cleared).

## Production path

With `Bounded Frame` explicitly selected, `LagRange.update()` only opens/renews the original outgoing C03 delay-owner request after `LagRange.beginFrame()` records a healthy recent real Minecraft frame, requiring unpaused elapsedTicks<=1, frameMs<=150, normal timerSpeed and context ownership. It clamps the configured windowTicks to <=100 ms and releases all delayed C03s on frame invalidation or after an absolute 120-ms window, independent of downstream packets. The existing accepted-transport and shared timing coordinator still control every delayed packet. The remaining optional `LagRange.runPlayerStep` always calls the vanilla body for this mode and resets credit rather than withholding/accelerating extra steps; `runExtraSteps` explicitly refuses the mode. No sprint/rotation, attack, inventory, C0F control, timer or PacketPipeline code changed.

## Evidence and tests

`LagRangeBoundedFrameFlowTest` drives actual embedded packet-pipeline C03 delay, real `TickEvent`, explicit normal vs missing/paused/stalled frames, serial C03 release, C0F immediate confirmation, S12 local knockback recovery, old combo-value IDs/defaults and direct vanilla player-body callback. All PR #96 and legacy module suites run unchanged.

Exact GitHub synthetic merge SHA, Temurin Java8u482 Maven3.9.15 full suite/coverage, Repository Security, CodeQL and Release bundle **PENDING**. Native Minecraft, multiplayer, real high RTT and sustained performance **NOT_RUN**. Draft only, no merges/releases/force-push/local Windows checkout.
