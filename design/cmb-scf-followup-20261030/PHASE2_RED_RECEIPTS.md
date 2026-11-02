# Phase 2 feature RED receipts

Before any phase-2 production edit, on PR-1 code
`5504fbb50ea8e2cbfec72abcbea260a0c1ac2025`, Temurin 8u482/Maven 3.9.15:

| Run | Tests / failures / errors / skips | Meaning |
|---|---|---|
| h2-feature-red | 7 / 6 / 0 / 0 | Actual Aura/C0A/C02 and existing late guards work, but correlated diagnostic facts are missing; OFF control passes |
| h1-feature-red | 13 / 11 / 0 / 0 | Existing hard-release cannot produce the newly approved bounded release source; reserved source is not yet internal-only; two existing-safe controls pass |

These are new-feature acceptance REDs, **not 17 additional gameplay bugs**.
They use existing Java APIs and execute real walking/controller/pipeline paths;
there is no missing-API compile RED. Raw logs and fresh XML copies are outside Git
under the followup evidence folder.

H2 cases: normal correlated opportunity/swing/attack; target or owner loss after
actual swing; C03 held by two owners and flushed by the C0A barrier; denied Extra;
outside-Aura UNKNOWN origin; OFF. The historical attack and movement semantics
already hold. Only new observer facts are required.

H1 cases: two intermediate C03-confirmed release frames and third real tick
expiry; 200ms expiry at a query with no packet; Extra sharing one outer ID;
Withheld without a body; cancel/delay/rewrite and stale replay; Packet-POST epoch
invalidation; observed GUI/focus/pause/scene boundaries; re-enable; foreign Aura
and visible AimAssist; no prior acceptance; reserved-source spoof prevention;
Aura rotations OFF cannot borrow a release pose even with a positive wire ray.

Before production, the last Aura-OFF fixture was corrected to explicitly run its
target-seeding Motion POST listener at HIGHEST priority. Equal-priority insertion
order is not a reliable way to put it before Aura; this correction prevents a
false pass from setting the target only after the real consumer. The same
assertions remain, with a positive target/raycast precondition. The final combined
baseline rerun is saved separately as `phase2-feature-red`.

The corrected combined feature baseline ran **20/17/0/0** before production.
The first H2 implementation then passed **111/0/0/0**, including all seven H2
feature cases and existing diagnostic/combat/observer/pipeline regressions.

## Independent H2 review REDs, before their corrections

QA and Combat review independently found stale step-run attribution and missing
final-selected packet evidence. The new `CombatSequenceBoundaryWave2Test` ran
**10 tests, 5 failures, 0 errors, 0 skips** (`h2-review-red.log` and fresh XML):

* `oldStepCompletionCannotMigrateToANewGenerationOrEpochRun`: old body completion
  was written into the successor run after epoch change.
* `oldObserverFinallyCannotWriteAStepIntoItsReplacementRun`: an old observer's
  finally was associated with its replacement observer/run.
* `relevantSelectedRewriteGetsExactlyOneReceiptEvenWhenOriginalPacketWasOther`:
  real C09-to-C0A transport had zero corresponding local-accepted trace records.
* `packetPreThrowKeepsItsThrowableAndGetsATerminalFailureReceipt` and
  `finalPredicateThrowRunsOnceKeepsItsThrowableAndGetsATerminalFailureReceipt`:
  actual exceptions propagated, but terminal FAILED evidence was missing.

The five controls already pass: actual accepted swing survives a POST throw with
an interrupted body; bounded primitive-only schema/overwrite accounting; nested
scope/run reset; identical ON/OFF scene/guard/packet counts; trace-internal failure
cannot alter real packets or the original gameplay Throwable. These review REDs
concern the new diagnostic implementation, not additional historical game bugs.
Correction scope is the same H2 files: entry-bound scalar StepToken, capture the
original OTHER packet attempt while tracing, and observe actual selection/guard
exceptions once before rethrowing the identical Throwable.

## H1 review REDs and GREEN

The first integrated H1 focus ran 267/14/0/0. All 13 release feature cases
and three pure observer-marker cases passed; the failures were missing H2 facts
in the shared Unsafe Minecraft fixture. Added fixture preconditions reproduced
an inactive diagnostic adapter before gameplay despite correct registry,
recording, reflected client and singleton (267/17/0/0; one later legacy failure
followed the failed constructor). An isolated 17-case run left one interrupted
step failure; that same method alone passed 1/0/0/0.

This matches the project's documented static-final/HotSpot fixture limitation in
RotationModuleBindingTest. The new tests now use the existing child-JVM pattern,
once per assertion method with normal Java8 JIT. No interpreter/compiler-disable
flag, production workaround, assertion removal or POM change is used. Constructor
failures clean up their fixture. Each outer Surefire case executes exactly one
original child case; child counts are not added a second time. The scalar-schema
check also covers StepToken. Only obsolete generated classes from these new
renamed tests were removed before recompilation.

With isolation and 11 independent H1 boundary cases, the focus ran
**278/2/0/0** (`h1-review-red`):

* `nestedWalkingInsideResolveCannotSendOrPostTheOlderNeutralFrame` observed two
  Motion POSTs instead of one after a real connected-supplier callback entered a
  newer walking frame during release validation.
* `nestedWalkingInsideFinishCannotOverwriteReportingOrPostTheOlderFrame` observed
  the same old-caller continuation after a clock callback inside finishMotion.

The manager already returned on sequence replacement; EntityPlayerSP continued.
The correction is an unconditional sequence AND runtime-manager identity boundary
immediately after resolve and after finishMotion, before old ticket/send or
reporting/POST work. Packet, camera, sprint and physics ordering is unchanged.
The identical focus then passed **278/0/0/0, 30 fresh suites** at 13:30:11 CET
(`h1-review-green`). Both actual nested cases also assert real C03 counts,
lastReportedYaw, the newer release owner and absence of automatic actions.

Other boundary controls cover generation loss before disable, a synthetic numeric
finish without a new resolved ticket, re-enable in packet PRE/POST, losing foreign
offers, Aura rotations-OFF camera intent, re-enable during arm capture, clock
reversal, shutdown/replacement/tick wrap, and observed GUI ABA with exactly two
scene captures and no scene sampling from 100 repeated owner queries.

The pending-lease control deliberately retains the existing numeric wire basis
before any release C03 was accepted. It grants no Scaffold/release source and no
new acceptance. Once a release source has actually been accepted, expiry/cancel
also revokes numeric accepted flags. This is a documented legacy boundary, not a
new unowned release right.
