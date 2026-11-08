# WAVE07 — Continuous Click Callback Contract HOTFIX (separate Draft)

## Live source gate and exact requested API

Initial verified main: `869d1b111f2e1f5e22b57cee4099f2bf7ccd9ec3` (includes merged #100/#101/#102, AI-AURA-CORE02 #106, Scaffold planner #108 and continuous-click #111). Open VIS #112 owns only unrelated UI/TargetHUD paths. Do not copy or modify any local checkout, untracked test, assets, models, worlds, or accounts.

The WAVE07 user-provided local-test *contract*, not the inaccessible original file bytes, specifies:
`tryClick(long motion, boolean hitValidated, LongSupplier nextDelay, BooleanSupplier controllerAttack, Runnable swing)`.
Its three named tests are `swingsAndHitsShareOneCadenceWithoutBurstsOrMissAuthority`, `resetInsideSwingCannotResampleStaleCadenceOrEraseTickBudget`, and `resetPreservesManualReservationAndTimestamp`. The prior merged #111 API had only four args, and Aura performed local swing inside its controller callback. This PR adds independent unit fixtures; it does not claim to have read/ported the unseen original local test.

## Functional boundaries

- The five-arg API admits a *click* after existing motion/tick/CPS gates, runs swing once, then may call the original controller only when `hitValidated=true` and no reset occurred. A hit-invalid click may swing but never calls the controller/C02. The bool is **hit validity**, NOT general target/scene eligibility; the Aura caller is obliged to gate owner, accepted C03, scene and selected target before entering the five-arg overload.
- Four-arg overload remains source/binary compatible with its **old meaning** (`clickEligible`), including its no-sampling false path. A shared internal implementation enforces the same budget and CPS timestamps. No silent alias with mismatched semantics.
- `reset()` revokes an in-progress swing→attack handoff, retains the admitted click's motion/tick, last real click time, accepted C02 time and existing delay. It never draws an interval in the stale callback. A manually reserved controller in-flight is neither completed nor released by reset.
- Both methods refuse nested automation. A reset during a swing forbids a second controller request in that click. `tryAttack` remains independent and its callback still determines accepted attacks. `hasAttackThisTick()` remains reserved for accepted controller/legacy auto attack; a visual miss cannot claim sprint physics.
- Aura Motion POST remains the sole consumer and uses five-arg `tryClick` for actually eligible target/C03/owner situations, with its separate raycast `hit` flag. Ineligible situations call the previous four-arg false gate so motion tombstone/RNG behavior remains. The Swing callback revalidates current scene/target/owner, uses the actual player's existing `swingItem()`, and restores the camera pose; the independent controller callback revalidates the same state and only invokes `PlayerControllerMP.tryAttackEntity` and its original tracked C02/Raycast. No new packet, attack or MoveFix authority.
- Original `ClickTimingSettings.sample()`, `RuntimeServices.attackScheduler()` and all existing settings/defaults remain unchanged.

## Tests, CI, and open gates

Exactly five new JUnit 4 tests in `ContinuousClickCallbackContractTest` cover all three user-named contracts, thrown Swing, and old overload compatibility. All pre-existing `ContinuousClickSchedulerTest` 8 cases, real `ContinuousClickAuraIntegrationTest` 5 cases, controller C02 and Legacy/Adaptive/Arsenic golden traces must pass on exact synthetic PR merge SHA. This is a source-test compile audit, not an inspection of unknown local file bytes.

**PENDING:** hosted Temurin 8u482 / Maven 3.9.15 Java-8 CI, CodeQL, Security and release-compile. **NOT_RUN:** native LWJGL/OpenGL/Minecraft startup, multiplayer/server, frame-time/CPS profiling and original untracked Windows test file. No anticheat bypass claim.

Path allowlist: `AttackScheduler.java`, `Aura.java`, new test `ContinuousClickCallbackContractTest.java`, this document. Rollback: close Draft. Never main push, merge, release, force-push or touch local user files.
