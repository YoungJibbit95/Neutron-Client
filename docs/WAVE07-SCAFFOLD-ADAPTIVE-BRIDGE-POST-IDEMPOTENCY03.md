# WAVE07 TECH T5a — Adaptive Bridge POST idempotency / directional live flow

Parent `main@8cd765e61512bc8c111bee9cdd61c94e0f208b19`. This isolated Draft is deliberately **independent of T2/T3/T4** and the VIS worker. Previous #116 supplied the optional Adaptive Bridge third approach; T1 #121 supplies a separate six-test actual walking integration hotfix.

## Specific observed failure class

An accepted C03 can legitimately lead to a POST controller C08 attempt that is rejected by outbound packet transport. The existing Adaptive Bridge POST path retained its PRE intent after such a failure. If event consumers deliver a duplicate POST with the same motion sequence, the same accepted C03 could try C08 again. Multiple C08 attempts for one physical C03 must not occur.

**Fix:** `Scaffold.bridgeMotion` enforces `bridgeLastPostSequence != sequence` and records the first POST sequence *before* any controller callback. Reset this gate on Scaffold settings/lifecycle changes. PRE remains idempotent with `bridgeLastPreSequence`. The guard exists only in the `Adaptive Bridge` approach; Neutron and Arsenic code paths, defaults, config IDs, click, movement physics, sprint, MoveFix, tower, timer and packet-transport infrastructure are untouched. Every new accepted physical motion sequence can still take a separate genuine POST attempt; errors and rejection do not grant new authority.

## Real source and tests

`AdaptiveBridgePostIdempotencyTest` uses the real `EntityPlayerSP.onUpdateWalkingPlayer` → scaffold PRE → accepted C03 → controller C08 live flow. With an intentionally rejected C08, it sends six duplicate Motion POST callbacks, asserting no additional C08 attempt or stack/world mutation. A second test repeats actual walking with forward, backward, strafe left/right and diagonal physical input values, verifies C03-before-C08 order and the original placement destination. No fake packet-admission shortcuts; no third-party source.

Temurin8u482/Maven3.9.15 exact merge SHA full test suite, Repository Security, CodeQL, release bundle: **PENDING**. Legacy Neutron/Arsenic/config/packet/movement regression gate must stay green. Native Minecraft, multiplayer latency/TPS measurement and rotation/MoveFix performance: **NOT_RUN** until separately validated. No anti-cheat or indistinguishability claims.
