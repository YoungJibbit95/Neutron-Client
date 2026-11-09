# WAVE07 TECH T6c — Tickbase Stable Frame (opt-in)

Base `main@8cd765e61512bc8c111bee9cdd61c94e0f208b19`; independent of Backtrack #127, LagRange #128, Scaffold #126, T1 and T2–T4 AI and all VIS branches. PR #96's existing `CombatTimingCoordinator`, `TickbaseBudget`, actual player-body interception, local impulse callbacks and C02 acceptance gates remain the only authority. No FDP/Vibe implementation or uncleared third-party code is copied.

## Compatibility and productive call path

A new `Tickbase.Charge Policy` ComboValue is **off by default**, index0 `Legacy`, index1 `Stable Frame`. Existing `Release On`, charge ticks, release speed, charge/release ranges, cooldowns, default toggled states and **all existing Config IDs** are preserved. No timer, packet or attack scheduling overrides.

Under `Stable Frame` only, `Tickbase.beginFrame` demands exactly one elapsed Minecraft player tick, unpaused elapsed wall duration between 20 and 75 ms, ordinary timerSpeed, original target/lifecycle eligibility and exclusive existing combat-timing ownership. The same original `TickbaseBudget.frame` refuses all credit for missing, catch-up or jittered samples. `Tickbase.runPlayerStepDecision` refuses skipped/extra body simulation after an unhealthy/stale frame and executes the normal player body once. Only valid actually withheld prior player updates can earn credit; ordinary accepted C02 still triggers existing release, with a strict maximum of one extra step per ordinary opportunity. Local knockback/reconnect/target switch still discards the original budget.

## Tests

`TickbaseStableFrameFlowTest` uses the actual embedded `ExploitHookFixture` and `Tickbase.runPlayerStep` to test no sample, 0/2/3 elapsed ticks, 19-/76-/250-ms durations, valid credit/accepted C02 release, local S12 velocity/knockback invalidation and default config/mode IDs. Existing PR #96 timing safety/golden tests remain unchanged.

Full Temurin 8u482/Maven 3.9.15 JUnit with exact GitHub merge SHA, Repository Security, CodeQL, Release Bundle **PENDING**; native Minecraft, multiplayer, real latency/TPS, CPU/render-heap performance **NOT_RUN**. Separate draft, no force pushes, merges, releases or Windows checkout changes.
