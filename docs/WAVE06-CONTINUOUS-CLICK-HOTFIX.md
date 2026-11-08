# WAVE06 TECH — Continuous Click Scheduler HOTFIX (Draft)

## Source and local test contract
Baseline at first production commit: main `e374cd86631f78851044a6c069b7d6a2e1f4050e`. The actual GitHub `AttackScheduler.java`, `Aura.java`, `ClickTimingSettings.java`, `core/bootstrap/RuntimeServices.java`, `ClientRuntime.java` and patched `PlayerControllerMP.java` were read before the change. No Windows checkout, user-world data, or locally untracked files have been touched.

**LOCAL TEST GAP:** The user's original untracked `ContinuousClickSchedulerTest.java` is absent from the full GitHub main tree and every uploaded WAVE06 ZIP. Its exact source, signatures, assertions, and the other locally untracked Combat/Network tests were not accessible. The tests added by THIS PR are independently authored against the actual committed API and must not be represented as copies or a completed read-only audit of the unknown local tests. Their future compatibility is an explicit outstanding gate.

## Actual production semantics and why tryClick is not a tryAttack alias
- Historical `tryAttack(motion,hitValidated,nextDelay,action)` remains available, using its existing one-motion, one-tick controller reservation and CPS timing after an accepted C02 attack.
- New `tryClick(long motion,boolean clickEligible,LongSupplier nextDelay,BooleanSupplier controllerAttack)` is an explicit, separate continuous-click contract. Eligibility comes from an actual current Aura target, accepted C03 motion and correct rotation owner; the raycast still controls whether a click becomes C02. A due eligible click reserves the current tick and physical motion **before callbacks**, with zero duplicate or reentrant click attempts.
- Every actual click consumes one sampled CPS interval, including if the **unchanged controller** refuses C02 (occluded, out-of-range, cancelled, replaced or lost target). False is a rejected/absent controller attack, never a fabricated packet success. Non-admitted clicks do not draw random delays or trigger Swing/C02. No catch-up burst is generated after lag.
- The accepted `PlayerControllerMP.tryAttackEntity` remains the only C02 sending authority and revalidates world, target reference, raycast and tracked packet admission. In Aura, a due but invalid-ray click may issue a *local swing only*. `advanceTarget`, successful Intave block handoff and diagnostic accepted-attack status are set **only** when the controller returns a successful attack.
- A manual accepted controller attack and a scheduled click use the **same** tick reservation and CPS reference. `AttackScheduler.reset()` clears the acquisition timing but preserves the last click/C02 time so disabling or switching world/target cannot trigger duplicate immediate attacks.
- Existing old modes/config keys/defaults, ClickTimingSettings sampling, RuntimeServices shared instance, RotationManager, MoveFix, PacketPipeline, C03/ C02, and Arsenic/Adaptive/AI pure sources are unchanged. No second scheduler or packet path is added.

## Exactly five changed paths
1. `src/main/java/me/youngjibbit/core/combat/AttackScheduler.java`
2. `src/main/java/me/youngjibbit/module/impl/combat/Aura.java`
3. `src/test/java/me/youngjibbit/core/combat/ContinuousClickSchedulerTest.java` — eight direct JUnit cases.
4. `src/test/java/me/youngjibbit/core/world/ContinuousClickAuraIntegrationTest.java` — five isolated real Aura/Controller/C03/C02 test cases, using committed production fixture.
5. `docs/WAVE06-CONTINUOUS-CLICK-HOTFIX.md` — this source, acceptance and gap record.

## Tests, CI and remaining runtime gates
- Source-test API linkage: `ContinuousClickSchedulerTest` explicitly compiles/calls the committed `tryClick(long,boolean,LongSupplier,BooleanSupplier)`, `tryAttack`, `reset`, `beginTick` and `beginControllerAttack`; no guessed production class/method. `ContinuousClickAuraIntegrationTest` uses real `Aura`, `Wave05AuraSelectionChecks.SelectionRun` and patched walking, scheduler, swing, controller and C02 transport, not a mock-only API.
- Unit regression: rejected-C02 click cadence, multiple POST callbacks in a physical tick, manual controller budget, invalid target and no CPS sample, reentrant/nested callbacks, target switch/reset/disable/world, time rollback, long-pause no catch-up and exception-safe cleanup.
- Real integration: five repeated accepted attacks, out-of-reach acquisition with swing but no C02, cancelled C02 with correct multi-target retention, disable/re-enable, and cancelled C03 without swing/C02.
- Full GitHub-hosted **Temurin 8u482 / Maven 3.9.15** regression, existing legacy C03/C02 golden cases, repository security and CodeQL **MUST** pass on exact PR synthetic merge SHA before `CODE_GREEN`. Initial status is PENDING. Actual test counts/run IDs will be added after validation.
- **NOT_RUN:** native GL, Minecraft launcher/input, multiplayer real server, latency profiling, and all other local untracked tests. No performance or anti-cheat bypass guarantees.

**Rollback:** close Draft. No merge, force push, release, direct main push or local user-file changes.
