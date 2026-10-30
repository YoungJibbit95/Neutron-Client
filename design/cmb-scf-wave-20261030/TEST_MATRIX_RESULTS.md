# CMB/SCF-Wave: Testmatrix und Evidenzgrenzen

Stand vor Produktionsfix, 30.10.2026. QA: `qa_regression` (A5), Fachreviews:
`cmb_owner_transport` (A1), `scf_plan_packets` (A2), `scf_lifecycle` (A3),
`combat_arbitration` (A4); JVM-Ausführungen ausschließlich durch den Lead.
Die Baseline wurde vom Lead mit 724/0/0/3 gemeldet. Neue Tests sind hier
zunächst **neu anzulegen / geschrieben, noch nicht ausgeführt**; ein Testname
ist kein PASS. Finale RED/GREEN-Zahlen stehen im Lead-Ergebnisbericht.

Alle 42 Fälle des Bundles sind unten zugeordnet. `bereits_testbar` bezeichnet
vorhandene ausführbare Regressionen, nicht eine neue oder native Spielabnahme.
`neu anzulegen` bezeichnet zusätzliche Wave-Abdeckung oder einen ausdrücklich
zurückgestellten Vertrag. Die Lease ist nach Entscheidung D1 nicht freigegeben;
deren Timingfälle werden daher nicht als bestanden ausgegeben.

| ID | Agent | Ausgangsklasse | Konkreter Prüfanker / neue Ergänzung | Grenze |
| --- | --- | --- | --- | --- |
| A01 | A1 | bereits_testbar | `RotationOwnerPredicateTest.c03PreKeepsPreviousScaffoldDespitePendingAuraAndPostChangesOwner` | PRE vorherige Acceptance |
| A02 | A1 | bereits_testbar | gleicher Test; `c02OnlyTheAcceptedSourceOwnsPost` | POST tatsächlicher Winner |
| A03 | A1 | bereits_testbar | `c04FirstPendingAndResolvedRequestCannotAuthorizePre` | Kein Recht aus Request |
| A04 | A1 | bereits_testbar | `ScaffoldLateC03TransportTest.t09PacketCancelAndMotionCancelPreserveTheirDistinctPostContracts` | Cancel-Verträge verschieden |
| A05 | A1 | bereits_testbar | `t10NormalDelayedReleaseCannotReplayAnEndedWalkingCapture` | Kein stale Replay |
| A06 | A1 | bereits_testbar | `t11SelectedRewriteChecksPoseClassAndExistingAngularEquivalence` | Klasse, Pose, Ground |
| A07 | A1 | bereits_testbar | `t16NestedWalkingCannotSendOrFinishTheOuterStaleFrameTwice` | Neuer Frame bleibt erhalten |
| A08 | A1/A5 | bereits_testbar + neu anzulegen | `ResolvedC03SendTicketTest.t07ObserverReplacementAndGenerationAbaCannotRestoreOldTicket`; neu `C03PostWaveTest` | POST-Invalidierung bisherige Lücke |
| A09 | A1 | bereits_testbar | `MoveFixControllerTest.legacyRoundedSprintAdmissionUsesTheActualForwardKeyInModuleYawSpace`, `SprintOrderingTest` | PR13 bewahren |
| A10 | A1 | bereits_testbar | `RotationSensitivityTest.exactHalfCountsMatchPinnedLegacyRoundToIntRatherThanTiesToEven`, `positiveNegativeAndSlowIncrementSeriesStayOnMouseGrid` | PR14 bewahren |
| B01 | A2 | bereits_testbar | `AutomatedPlacementAdmissionTest.preUsesPreviousAcceptedAngleAndPostUsesCurrentAcceptedAngle`; `ScaffoldOwnerContinuityTest.a04PreSuccessStillRenewsRequestForTheFollowingC03` | PRE vor aktuellem C03 |
| B02 | A2 | bereits_testbar | `ScaffoldOwnerPlacementIntegrationTest.a05PendingRejectedRewrittenAndWrongPhaseNeverAuthorizeOn` | Kein erster Pending-Slot |
| B03 | A2 | bereits_testbar + neu anzulegen | `ScaffoldClientFlowTest.everyModeAndTimingPlacesThroughTheNativeFlowBeforePeriodicPositionRefresh`; neu `ScaffoldPacketWaveTest.unchangedNativePreAndPostKeepOnePlacementAndOrderedSlotRestore` | C09→C08→Restore, lokales Resultat |
| B04 | A2 | bereits_testbar | `ScaffoldPlacementTest.planIsNotReplacedInPostAfterItsSupportChanges`, `a08SlotStackRangeAndRayChangesRetainTheirGuardsWithOnOwnership` | Kein neues POST-Target |
| B05 | A2 | bereits_testbar | `ScaffoldOwnerContinuityTest.a01TwoNativePlanCyclesAndIdleKeepAcceptedOwnerForBothTimings` | Hold ohne Aktionen |
| B06 | A2 | bereits_testbar | `a03FirstPlanlessFrameNeverInventsAnOwner` | Kein erfundener Hold |
| B07 | A3 | bereits_testbar | `a10GuiPauseAndFocusSuspendWithoutRequestsAndResumeRevalidates`, `a10SuspensionKeepsOnlyTheWishUntilFreshPlanlessAcceptance` | Resume benötigt C03 |
| B08 | A2 | bereits_testbar + neu anzulegen | `ScaffoldPlacementTest.manualSelectionDuringSyncOrClickWinsOverSpoofRestore`; neu `ScaffoldPacketWaveTest.c09ManualSelectionInPacketPreWinsBeforeTheOldSlotTransports` | Finaler Packet-PRE |
| B09 | A2 | bereits_testbar + neu anzulegen | `ScaffoldPipelineIntegrationTest.acceptedSlotCacheSurvivesCancellationReplacementAndNestedPostSends`; neu C09-Disable/GUI/Epoch/Replacement in `ScaffoldPacketWaveTest` | Scene-Epoch kann schon Pipeline sperren; übrige echte REDs separat |
| B10 | A2 | bereits_testbar + neu anzulegen | `ScaffoldPlacementTest.samePlanCannotPlaceTwiceAcrossPreAndPostEvenOnLocalFailure`; neu C08-in-place-Stack/Position/Face/Hit in `ScaffoldPacketWaveTest` | C08/C09 sind Actionbarrieren, keine C03-Delaykandidaten |
| B11 | A2/A5 | bereits_testbar + neu anzulegen | `ScaffoldOwnerPlacementIntegrationTest.a16EpochChangeInCallbackCannotClickOrRestoreIntoReplacementScene`; neue Packet-Wave | Keine fremde Restore-Scene |
| B12 | A2 | bereits_testbar | `ScaffoldPlacementTest.exceptionAndCancelledSlotSyncRestoreOriginalWithoutSwing`, `a16ManualSlotAndExceptionCleanupStayIntact` | finally und manuelle Auswahl |
| B13 | A2 | bereits_testbar | `a07OffRetainsActuallyAcceptedUnownedCameraForBothPhases` | OFF bleibt numerischer Kamerapfad |
| B14 | A3/A5 | bereits_testbar + neu anzulegen | `ScaffoldPlacementTest.towerPositionChangesStayCollisionCheckedAndMatchTheMotionEvent`; neu `ScaffoldLifecycleWaveTest` | AutoJump-Callback-Lifetime |
| C01 | A3/A5 | bereits_testbar + neu anzulegen | `ScaffoldOwnerContinuityTest.a12DisableReleasesImmediatelyAndCannotPlaceAgain`; neu Disable-AutoJump/C09 | Hard-release, keine Lease |
| C02 | A3 | neu anzulegen, deferred | 3-Outer-Tick-Lease nicht freigegeben | Kein Timing-PASS behauptet |
| C03 | A3 | neu anzulegen, deferred | 200ms-Lease nicht freigegeben | Kein Timer-/synthetischer Callback |
| C04 | A3 | bereits_testbar (CORE), Lease deferred | `RuntimeObservationActiveTickTest.realEarnedExtraAndOrdinaryShareOneActiveOuterIdWithoutBudgetConsumptionByReads` | CORE-ID ist keine Leaseabnahme |
| C05 | A3 | bereits_testbar (CORE), Lease deferred | `realBudgetWithheldKeepsOuterLifetimeWithoutInventingABody` | Withheld echter Outer, kein Body |
| C06 | A3/A5 | bereits_testbar + neu anzulegen | `a11PlayerWorldConnectionPipelineAndDeathInvalidateOldBasis`, neue Lifecycle-Epochprobe | Hard-release-Subset; keine Lease |
| C07 | A3/A4 | bereits_testbar (aktiver Owner), Lease deferred | `CombatFlowTest.releasedScaffoldHandsOverFromTheLastWireAngleNotTheCamera` | Keine disabled Ownerübernahme |
| C08 | A3/A5 | neu anzulegen | `ScaffoldLifecycleWaveTest.disableAndReenableInsideAutoJumpCannotResumeTheOldGeneration` | Alte Callbackgeneration stoppen |
| D01 | A4/A5 | bereits_testbar + neu anzulegen | `ScaffoldOwnerContinuityTest.a02HoldBeatsAuraInBothListenerOrdersOnlyAfterAcceptance`; neu `CombatArbitrationWaveTest` | Echter Aura-POST-Consumer, seeded lokale Acceptance |
| D02 | A4/A5 | neu anzulegen | `CombatArbitrationWaveTest.equalAngleForeignScaffoldOwnerCannotAuthorizeAuraInEitherRequestOrder` | Gleiche Zahlen verleihen keine Source |
| D03 | A4 | bereits_testbar | `AutomatedActionModuleTest.physicalClickAndManualWindowClickWithoutStepStillReachNativeController`, `AttackHookTest` | Synthetischer physischer Input; keine native Mausabnahme |
| D04 | A4 | bereits_testbar | `AutomatedActionModuleTest.auraOrdinaryCanSendAcceptedAttackAndExtraCannotRepeatAttempt`, `auraExtraDoesNotEvenEnterSchedulerAndKeepsAcceptedForeignOwner` | Extra kein Schedulerbudget |
| D05 | A4 | bereits_testbar | `AttackHookTest.resultRequiresSuccessfulControllerGuardAndAcceptedAttackPacket`, `replacementAndSceneOrDeathChangesCannotSendStaleAttacksOrReportSuccess` | C02 lokal accepted, kein Hit-ACK |
| D06 | A4 | bereits_testbar | `RotationBehaviorHookTest.sprintJumpUsesAcceptedOwnerAndInvalidOrUnownedStateFallsBackToCamera`, `CombatFlowTest.attackListenerChangingWireRotationVetoesTheControllerAction` | Kein neuer Knockback-Write; kein Serverbeleg |
| E01 | A5 | bereits_testbar + neu anzulegen | `RuntimeObservationHookTest.actualWorldUnloadClearsAnEnteredStepAndInvalidatesThePreviousScene`; neue C03-POST- und C09-Epochproben | Beobachtete Generation/Epoch; beliebige unsichtbare Feld-ABA nicht behauptet |
| E02 | A5 | bereits_testbar + Codeprüfung | `ScaffoldClientFlowTest.diagnosticsDefaultOffExplainNoBlocksAndAreRateLimitedWithControlledTime` | Kein Allocations-/Framezeitbenchmark; keine neuen Packetpfade zulassen |
| E03 | Lead/A5 | kein Livezugang durch QA | IntelliJ `Neutron Client (Java 8)`, vorhandenes `test_run` | Lead protokolliert tatsächlichen Start und Gameplay separat |
| E04 | Lead/A5 | kein Livezugang | Autorisierter Server, Nutzerlogin, Serverbuild/Checkname/VL fehlen QA | Kein Intave-/Polar-PASS |

Neue QA-Dateien wurden ohne Produktionsedit und ohne Build angelegt. A5 führt
keine Maven-/IntelliJ-Läufe parallel zum Lead aus. Kein Worktree, Klon, Spielprofil
oder temporärer Ergebnisordner wurde von QA angelegt. Produktionsänderungen und
finale Testzählung bleiben dem Lead vorbehalten.
