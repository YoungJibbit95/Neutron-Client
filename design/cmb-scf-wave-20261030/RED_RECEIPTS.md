# RED receipts before production edits

Basis for every behavioral RED: `07b2e827ba935e31b9fb73125471ef8060d71f2e`,
unchanged `src/main`. Java Temurin 8u482, Maven 3.9.15, offline Maven.
Raw logs and fresh XML copies:
`F:\Pulsar-Recovery\analysis\cmb-scf-wave-20261030\evidence\tests`.

| Run | Tests/F/E/S | Actual evidence |
|---|---|---|
| baseline.log, baseline-xml | 724/0/0/3 | Existing suite before new tests |
| packet-red.log, packet-red-xml | 11/8/0/0 | Four C09 selection REDs; four C08 envelope REDs; three positive controls |
| qa-red.log | 0 executed | New fixture wrong EventPhase import; corrected to existing Alpine type. Not a product RED |
| qa-red-behavior.log | 8/4/0/0 | Three AutoJump timer REDs; final scene-sampling exception leaves accepted authority |
| restore-red.log, restore-red-xml | 1/1/0/0 | Old C09 restore transports slot 0 after PRE manually selects slot 7 |
| c03-red-priority.log, c03-red-priority-xml | 4/4/0/0 | Generation, epoch, GUI and sampling exception after actual C03 transport |

Unique final new cases: 20 tests, 16 assertion REDs, 4 positive controls. The
earlier 8-case QA run is not added again. Three initially green C03 probes were
masked by Scaffold cleanup: Alpine inserts equal-priority listeners via binary
search, so subscription order alone did not select the first Motion POST consumer.
The new test now uses explicit `EventPriority.HIGHEST`; all three fail before the
fix. This is a test-fixture correction, not a product change or weakened assertion.

## Exact failures and approved minimal corrections

* `ScaffoldPacketWaveTest.c09DisableInPacketPreCannotSendTheOldAutomatedSlotSelection`,
  `c09GuiInPacketPreCannotSendTheOldAutomatedSlotSelection`,
  `c09ManualSelectionInPacketPreWinsBeforeTheOldSlotTransports`: expected 0 old-slot
  transports, actual 1. `c09ReplacementCannotBorrowTheAutomatedSelectionAuthority`:
  expected 0 foreign-slot transports, actual 1. Add guarded automated slot sync.
* `manualSelectionDuringRestorePreWinsBeforeTheOldRestoreTransports`: expected 0
  obsolete slot-0 restores, actual 1. Guard cleanup with scene/selected-slot facts.
* `c08MutablePacketStackCannotChangeTheSealedIntent`,
  `c08InPlacePositionRewriteCannotChangeTheSealedIntent`,
  `c08InPlaceFaceRewriteCannotChangeTheSealedIntent`,
  `c08InPlaceHitRewriteCannotChangeTheSealedIntent`: expected 0 C08, actual 1.
  Seal the existing guarded controller packet's envelope before PRE.
* `C03PostWaveTest.observerGenerationChangedInPacketPostCannotRenewSourceAuthority`,
  `packetEpochChangedInPacketPostCannotRenewSourceAuthority`,
  `guiSuspensionInPacketPostCannotRenewSourceAuthority`: obsolete source true at
  the first Motion POST consumer. Recheck existing ticket lifetime before finish.
* `postTransportSceneSamplingFailureRevokesOwnFrameAndPropagates`: exception does
  propagate later, but accepted action authority remains true. Sample/reject within
  the walking frame's exception boundary while retaining the actual sent C03.
* `ScaffoldLifecycleWaveTest.disableInsideAutoJumpStopsTheOldPreBeforePlanAndTimerWrites`,
  `disableAndReenableInsideAutoJumpCannotResumeTheOldGeneration`,
  `epochChangeInsideAutoJumpStopsTheOldPreBeforePlanAndTimerWrites`: timer expected
  1.0, actual 2.0. Stop old callback continuation using lifecycle/scene/frame checks.

The new real Aura consumer equal-angle test is a GREEN control, not a new combat
fix. No missing-API compile RED and no lease timing RED is claimed. H1-06 stays
OPEN. Gate A authorizes only the five production files listed in INVENTORY.md.

## Additional review boundary (before its production correction)

After first-green (102/0/0/0), independent QA found that the walking caller still
dereferences `this.sendQueue` after Packet POST. A new executable probe sets that
field to null in the real Packet POST callback. `disconnect-red.log` and its XML:
1 test, 0 failures, 1 error (NullPointerException in EntityPlayerSP after send).
This is a runtime behavioral RED, not a compilation failure. The approved minimal
correction uses the already captured connection to read the actual transport fact.
Source authority still expires through the ticket. Original 20 tests stay intact;
three additional C08 field-only mutation controls isolate position/face/hit without
the serialization helper's incidental 1/16 hit quantization. Final new case count
is 24; 16 assertion REDs plus this one runtime-error RED, 7 controls.
