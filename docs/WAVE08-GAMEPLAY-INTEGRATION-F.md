# W08-F — Gameplay integration evidence

Main baseline: `c6612bf43ec2ce45bf686714374516f80788057e`.
Stacked base: W08-B #137, `1bb958d428d1702b5ac7b557cf1a671f831ac28f`.
This phase adds integration tests and this contract, with no new gameplay controller.

## Scope and ownership

Allowlist:

```text
docs/WAVE08-GAMEPLAY-INTEGRATION-F.md
src/test/java/me/youngjibbit/core/world/AdaptiveBridgeTimingCoexistenceFlowTest.java
```

Root owns documentation, review and publication; C owns the new test; E owns
compiler, local regression and CI evidence. Production sources, existing tests,
POM, workflows and golden fixtures remain outside this phase's edit scope.

## Delivered stack

| Phase | Draft PR | Published head | Exact tested synthetic merge |
|---|---|---|---|
| A1 prediction/input safety | [#134](https://github.com/YoungJibbit95/Neutron-Client/pull/134) | `b14e0aa09b27f023a3b6d145edc3608d490454d8` | `a3e27fa28fa440bbd10c75fe9db0b36d07acd5be` |
| A2 rotation profiles | [#135](https://github.com/YoungJibbit95/Neutron-Client/pull/135) | `c802aef0ddb168aa9f0c3efffbffc3e27837f145` | `22084f8fd419694f7978f6262c654903093b6b19` |
| A3 placement cadence/jump | [#136](https://github.com/YoungJibbit95/Neutron-Client/pull/136) | `98b8119088fb1192608b3aa9d77f8b28882f86d7` | `efa42b12aed6af7e72919f0769933eaa7bbc82f1` |
| B optional V2 | [#137](https://github.com/YoungJibbit95/Neutron-Client/pull/137) | `1bb958d428d1702b5ac7b557cf1a671f831ac28f` | `e57b824869af51d050c90d47ae337f1480cb83a7` (full results recorded in #137) |

A1's final successful Java and Release totals are 1486/0/0/3. Its first Java
attempt had one failure in the unchanged NeutronAiTrainerTest heap-limit case;
one rerun on the identical merge passed. Both attempts remain documented in #134.
A2 totals are 1512/0/0/3 and A3 totals 1541/0/0/3, each on the first Java/Release
attempt. Completed phases also passed Golden (2), the separate packaged runtime
check (6), Repository Security and CodeQL. Native skips remain explicit.

B's initial merge `99510931d16ae169c02a9f6dadb550e4042484ca` passed the Release
1572/0/0/3 gate but its Java gate reported 1572/0/1/3: the new lifecycle test hit
the unchanged old Momentum listener while reusing headless static-final client
fixtures. The follow-up uses the existing fresh-JVM runner for all eight new Flow
methods. Their entire fixture/test body is byte-identical, and all eight passed
locally with normal Java-8 JIT. Production source and Speed.class are unchanged.
Fresh full CI runs on the current B merge above; the initial failure is retained.

## Combined consumers

Every case must prove the original timing module actually became active. A toggle
alone cannot pass. The new fixture runs current input, native moveFlying and native
walking transport through the original consumers in observed player bodies.

| Combination | Required positive evidence |
|---|---|
| Adaptive + V2 + LagRange Bounded Frame | Original coordinator ownership, held native C03, immediate C0F, ordinary V2 consumer, expired held Scaffold ticket rejected without late C08, subsequent fresh C03 before actual local placement |
| Godbridge + V2 + Receipt Safe | Real S14 receipt/applied-position divergence and queue, real S12 application/FIFO drain, jump and V2 suppression during recovery, lower-epoch reconnect with fresh receipt required |
| Adaptive + V2 + existing Tickbase | Actual omitted bodies earn credit, a stalled frame has no catch-up, accepted controller event releases ordinary plus earned extra, extra body earns no Speed ramp or Scaffold placement credit |

## Fixture limits

The class reuses AdaptiveBridgeInputSafetyFlowTest.Run with controlled keys, time,
block storage and incoming physical snapshots. Its existing input sentinel stops
before full EntityLivingBase physics; only that exact sentinel is caught.
Original LagRange.beginFrame, Tickbase.beginFrame and Tickbase.runPlayerStep own
all timing decisions. No private credit, cadence state, accepted rotation or packet
authority is written. Native local placement must change block state and inventory;
accepted C08 alone is insufficient and local confirmation is not server acknowledgement.

The recovery case preserves the actual S12 horizontal impulse and supplies a
grounded snapshot before the existing deadline. The delayed pump runs outside a
body. A 250 ms frame is a controlled elapsed-time input, not measured server TPS.
The Tickbase controller event targets a synthetic local entity without server
contact. Its established spectator fixture omits only unrelated local damage;
real C02 acceptance and AttackResultEvent remain, with game type restored first.

## Executed validation

First F source SHA-256: `8117efdb6c34c7400e7b43e182148f02550345a95a8d974ad27b81e981f52a2a`.
Full-source test compilation passed. Three methods ran in separate normal Java-8
JVMs: one passed, two assertions failed. Receipt/S12/recovery/reconnect passed.
LagRange incorrectly expected its delayed Scaffold C03 to be accepted after the
original motion window. The existing strict ticket expires when that window ends.
The Tickbase case reached actual earned release and both Speed consumers but
expected C08 before rotation reacquisition. An omitted body plus its 250 ms stall
made the actual rotation-step gap 300 ms; the existing controller invalidates at
250 ms. The fixture must demonstrate a fresh reachable controller intent before
expecting placement, while preserving its physically earned credits.

The corrected fixture retains the strict ordinary-placement-before-extra assertion.
Real ordinary bodies reacquire a rejected controller C08 while physical sneak is
held. Every such body must preserve the two earned credits, add no extra body or
V2 friction, and leave block/stack state unchanged. After fresh sneak release,
the original accepted controller event must produce the actual packet order
ordinary C03, one C08, extra C03 and a single local block with one-item decrement.

Final source SHA-256:
`c0672733adab3ea4a2e7339ab9577cd11a9590b08e771c549e2ac64a8699e46e`.
On published B base `1bb958d428d1702b5ac7b557cf1a671f831ac28f`, full-source
test compilation passed with Temurin 8u482-b08 / Maven 3.9.15. All three new
methods passed in three fresh normal Java-8 JVMs through the unchanged
Wave2IsolatedCase.main: **3 tests / 0 failures / 0 errors / 0 skips**.

| New F case | Actual local result |
|---|---|
| LagRange held/expired movement and fresh placement | PASS, 1.720 s |
| Receipt Safe, actual S12, recovery and reconnect | PASS, 1.619 s |
| Tickbase actual withhold, reacquisition and earned release | PASS, 1.568 s |

These are test-process durations, not client performance measurements. Production
sources and all prior tests are unchanged relative to B. Speed.class remains
`8a3898f0a15d63dd12d6387f6d9229b06f55ced1c28a120e13282b782e17c416`.
The earlier B 245-case gate and its eight-case isolation follow-up remain separate
base evidence; no newly executed 248-case local gate is claimed.

The F pull request records the subsequently executed full Windows totals, exact
synthetic merge, Golden compatibility, Repository Security, CodeQL, Release Build
and separate PackagedRuntimeCheck. Those results are not inferred from local F3.

## Coverage across the complete stack

F supplements earlier production-connected tests; their assertions remain.

| Requested boundary | Checked-in evidence |
|---|---|
| Early preparation from occupied floor | AdaptiveBridgeInputSafetyFlowTest future-face case; planner/acquisition tests |
| Fresh direction; physical versus guard sneak | InputSafety saved-Downwards, actual edge-collision, counter-steering and cleanup cases |
| Both profiles and accepted ownership | AdaptiveBridgeRotationFlowTest placement, keeping, direction, foreign owner, reconnect and return |
| Current C03 and duplicate POST | RotationFlow cancelled/rewritten C03, AdaptiveBridgePostIdempotencyTest, ScaffoldPipelineIntegrationTest |
| Rejected C08, cadence, confirmation and jump | AdaptiveBridgeCadenceFlowTest rejection, duplicate POST, pause, delay, eight applied blocks and false confirmation |
| Stack/slot/context and callback changes | CadenceFlow unsafe/foreign-input and every-scene-callback cases; placement/client-flow tests |
| V2 and final input/MoveFix | IntaveMomentumV2FlowTest all directions, accepted owners, final listeners, manual input and actual Scaffold guard |
| Body kinds, impulse and S08 recovery | V2Flow dispatch, applied/cancelled S12, five S08 variants, lifecycle and observer replacement |
| Rotation sampling and bounds | AdaptiveBridgeMotionTest controlled 20/60/144/240 sampling, wrap, finite bounds and duplicate steps; not rendered-FPS benchmarks |
| Legacy/configuration | Existing Neutron/Arsenic/Scaffold/Speed/Boat/MoveFix suites, new configuration tests and full Windows regression |

The unchanged controller and final sensitivity grid remain authoritative. Strict
finite-difference packet acceleration/jerk is not claimed: existing settling,
residual clamp and grid quantization may change those derivatives.

## C–E dependency decision

The source/CI audit found no evidenced additional benefit requiring another named
network variant. No Adaptive Receipt, Adaptive Window or Adaptive Charge was added.

| Existing work | Observed state | Dependency treatment |
|---|---|---|
| #127 Receipt Safe | Merged as `62c9b837e63d7dc5d260585993dd22de087281be` | Mainline implementation exercised by F. |
| #128 Bounded Frame | Merged as baseline `c6612bf43ec2ce45bf686714374516f80788057e` | Mainline implementation exercised by F. |
| #129 Stable Frame | Open draft, head `ea717683f5d313873046ec43e9a2ce189727bf6a`; successful CI on `c3f9fb6e3f6e421cc6ee892d77a7c079d9cf241a` | Review/merge stays separate. F uses mainline Tickbase, without importing this draft. |
| #130 Impulse Guard | Open draft, head `91bd7c1bce8ac084912b420a6b2dfb84e0544183`; successful CI on `a2599007bb27bb5412a24b1f61804296f7f9f3e2` | Stacked on #129; F preserves this dependency without importing or merging it. |

Current successful #129/#130 checks supersede stale pending/error description text.
Their successful synthetic merges do not mean either draft reached main. The
redundant closed/unmerged #131 is not a dependency.

## Open native gates

Native Minecraft gameplay, rendered FPS, full jump/collision trajectories,
multiplayer/server acknowledgement, actual low TPS/high RTT, sustained CPU/heap
and overall gameplay speed remain **NOT_RUN**. Controlled times and native Java
method calls do not establish those outcomes. No anti-cheat compatibility, bypass
or undetectability conclusion is drawn.

All deliveries remain separate draft PRs. No direct main push, merge, release,
Windows-checkout change or second complete client copy is part of this work.
