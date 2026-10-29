# SCF-1a → SCF-1b

Stand: 28.10.2026. Basis `88ee497d8fe2dca16e5eaae9e48548c7d46e7050`.
Diese Übergabe startet keinen Folgeauftrag. Abschlusszahlen stehen in `RESULTS.md`.
`C` bezeichnet `me.youngjibbit.core.world.ScaffoldOwnerContinuityTest`,
`P` bezeichnet `me.youngjibbit.core.world.ScaffoldOwnerPlacementIntegrationTest`.
Alle Nachweise sind synthetische JUnit-/EmbeddedChannel-Prüfungen.

## A01–A16

| ID | Status SCF-1a | Tatsächlicher Testname / Grenze |
| --- | --- | --- |
| A01 | pass | C.a01TwoNativePlanCyclesAndIdleKeepAcceptedOwnerForBothTimings: zwei echte lokale Controllerzyklen je PRE/POST, anschließend mehrere planlose C03; keine Serverbestätigung |
| A02 | pass | C.a02HoldBeatsAuraInBothListenerOrdersOnlyAfterAcceptance; unverändert RotationOwnerPredicateTest.c04FirstPendingAndResolvedRequestCannotAuthorizePre: Requestkonkurrenz und fehlende erste Acceptance getrennt |
| A03 | pass | C.a03FirstPlanlessFrameNeverInventsAnOwner |
| A04 | pass | C.a04PreSuccessStillRenewsRequestForTheFollowingC03: C08 vor aktuellem C03, trotzdem danach bestätigte Source |
| A05 | pass | P.a05PendingRejectedRewrittenAndWrongPhaseNeverAuthorizeOn; C.a05NativePostGuardRechecksSourceAfterC09AndC08Listeners; C.a15CancelledDelayedRewrittenAndReplayedMovementCannotSustainStaleHold |
| A06 | pass | P.a06CameraAndAuraNumbersCannotAuthorizeOnBeforeSlotOrBudget; P.a06SourceIsRecheckedAfterSlotSynchronizationAndInsideControllerGuard; C.a05NativePostGuardRechecksSourceAfterC09AndC08Listeners |
| A07 | pass | P.a07OffRetainsActuallyAcceptedUnownedCameraForBothPhases; unverändert ScaffoldPlacementTest.rotationsOffStillRequiresActualAcceptedView |
| A08 | pass | P.a08SlotStackRangeAndRayChangesRetainTheirGuardsWithOnOwnership; bestehende ScaffoldPlacementTest-/ScaffoldNetworkIntegrationTest-Geometrie-/Stack-/Slotfälle bleiben erhalten |
| A09 | pass | C.a09PlanlessHoldInExtraAndNoStepCannotEmitPlacementOrSlotPackets; unverändert AutomatedPlacementAdmissionTest.extraDenialConsumesNoSlotBudgetOrSuccessAndOrdinaryCanStillPlace, noStepWithheldAndNewPacketEpochCannotAttemptPlacement; AutomatedActionModuleTest.deniedScaffoldExtraStillComputesRotationsButNeverPlacesOrSwitchesSlot |
| A10 | pass | C.a10GuiPauseAndFocusSuspendWithoutRequestsAndResumeRevalidates; C.a10SuspensionKeepsOnlyTheWishUntilFreshPlanlessAcceptance: Wunsch bleibt, Anwendungsrecht erlischt, neue Acceptance nach Resume |
| A11 | pass | C.a11PlayerWorldConnectionPipelineAndDeathInvalidateOldBasis (Connectionverlust, Pipelineersatz); C.a11EpochInvalidatesOldHoldWithoutRestoringAForeignTimer; C.a11ObserverGenerationBoundaryCannotReviveAnOldPlanlessOwner; RotationOwnerPredicateTest.c09ConnectionReplacementInvalidationAndResetNeverRestoreSourceRights und c09PlayerAndWorldBindingsInvalidatePreIncludingReturningToOldScene |
| A12 | pass für Interim | C.a12DisableReleasesImmediatelyAndCannotPlaceAgain; **2–4-Tick-Auslauf deferred**, keine Vollabnahme von v1.0 §3.1 |
| A13 | pass | C.a13PlanAndUnrelatedSettingCleanupKeepSourceButOffReleasesIt; C.a13EpochCleanupReleasesOnlyTheSameOwnedTimerObject; LegacyScaffoldTest.BindingChecks.settingChangesInvalidateThePlanButOnlyRotationsOffReleasesOwnership |
| A14 | pass | C.a14ExistingMovementAndJumpConsumersUseTheHeldAcceptedYaw; RotationModuleBindingChecks.invalidScaffoldSupportKeepsAcceptedMovementUntilDisable; unverändert MoveFixProductionTest, NetworkMoveFixIntegrationTest und RotationBehaviorHookTest für Präzise/Rotation-bound/Aus, WADS/Diagonalen, Wrap, Pitch und Claim-Idempotenz |
| A15 | pass | C.a15CancelledDelayedRewrittenAndReplayedMovementCannotSustainStaleHold; C.a15RejectedHoldRequiresFreshAcceptanceButKeepsItsSceneBoundWish; unveränderte echte Transportfälle in RotationOwnerPredicateTest |
| A16 | pass | P.a16ManualSlotAndExceptionCleanupStayIntact; P.a16EpochChangeInCallbackCannotClickOrRestoreIntoReplacementScene; C.a16SettingsCallbackEndsThePlanEvenWhileItsAcceptedOwnerSurvives; bestehende Service-/Controller-/Pipeline-Finalguards |

## AC-R02 / H1-01–07

| ID | Ergebnis | Evidenz und verbleibende Grenze |
| --- | --- | --- |
| H1-01 | erfüllt | C.a01TwoNativePlanCyclesAndIdleKeepAcceptedOwnerForBothTimings; a04PreSuccessStillRenewsRequestForTheFollowingC03 |
| H1-02 | erfüllt | C.a02HoldBeatsAuraInBothListenerOrdersOnlyAfterAcceptance; a03FirstPlanlessFrameNeverInventsAnOwner; P.a06CameraAndAuraNumbersCannotAuthorizeOnBeforeSlotOrBudget |
| H1-03 | erfüllt für SCF-1a | P.a05PendingRejectedRewrittenAndWrongPhaseNeverAuthorizeOn; C.a05NativePostGuardRechecksSourceAfterC09AndC08Listeners; a15CancelledDelayedRewrittenAndReplayedMovementCannotSustainStaleHold; kein neuer C03-/Serververtrag |
| H1-04 | erfüllt | P.a08SlotStackRangeAndRayChangesRetainTheirGuardsWithOnOwnership; a16ManualSlotAndExceptionCleanupStayIntact; C.a09PlanlessHoldInExtraAndNoStepCannotEmitPlacementOrSlotPackets; bestehende Admissiontests |
| H1-05 | erfüllt für aktive Phase | C.a10GuiPauseAndFocusSuspendWithoutRequestsAndResumeRevalidates; a10SuspensionKeepsOnlyTheWishUntilFreshPlanlessAcceptance; Release-Lease während Suspension erst SCF-1b |
| H1-06 | **SCF-1b deferred** | Nur harter sofortiger Interim-Disable durch C.a12DisableReleasesImmediatelyAndCannotPlaceAgain geprüft; 2–4-Tick-Auslauf nicht implementiert |
| H1-07 | erfüllt für aktive Phase | C.a11PlayerWorldConnectionPipelineAndDeathInvalidateOldBasis; a11EpochInvalidatesOldHoldWithoutRestoringAForeignTimer; a11ObserverGenerationBoundaryCannotReviveAnOldPlanlessOwner; P.a16EpochChangeInCallbackCannotClickOrRestoreIntoReplacementScene; a16ManualSlotAndExceptionCleanupStayIntact; Lease-/Auslaufgrenzen deferred |

## Vorschlag für den späteren Disable-Zählvertrag

SCF-1a gibt sofort hart frei. Für SCF-1b wird folgendes Modell zur gemeinsamen
CORE/CMB/SCF-Entscheidung vorgeschlagen, **ohne Implementierung oder Freigabe**:

1. Disable sperrt neue Place-/Input-/Slot-/Timeraktionen sofort. Die optionale
   Rotationsrelease-Lease darf nur in exakt derselben Scene weiterlaufen.
2. Die beschlossene Länge bleibt im Bereich 2–4; ein konkreter Default wird hier
   nicht eingeführt. Deadline anhand eindeutiger realer Outer-Tick-IDs, keine Zählung
   von PRE/POST oder Earned-Extra-Bodies. Withheld zählt als tatsächlicher Outer-Tick,
   erzeugt jedoch keine nachzuholende Motion-/Placementaktion.
3. Für Pause ohne weitere Tickeintritte zusätzlich eine monotone Ablauffrist aus
   höchstens vier nominalen 50-ms-Ticks erwägen. Ihre Prüfung muss an einem echten
   verfügbaren Lifecycle-/Frame-/Owner-Lesepunkt stattfinden; keine synthetischen
   Ticks und keine Timerbeschleunigung. CMB muss Ablauf auch bei fehlender Mutation
   als Entzug der Authority definieren. Ohne ausgeführten Callback kann kein Code
   eine zeitgenaue Mutation garantieren; dieser Unterschied gehört ausdrücklich in
   den gemeinsamen API-Vertrag.
4. Interpolation nur in tatsächlich betretenen zulässigen Motionframes. Pause,
   Focus/GUI und fehlende Bodies erlauben keine synthetischen Writes; Sceneverlust
   beendet die Lease sofort. Acceptance und Deadline sind getrennte Bedingungen.

Zu beschließen sind Default innerhalb 2–4, Tick-vs.-Monotonzeit-Deadline unter Pause,
Render-/Lifecycle-Prüfpunkt, Camera-Basis und garantierter Endzustand. Dafür können
spätere freigegebene CMB-/CORE-Änderungen nötig sein; SCF-1a ändert diese Dateien nicht.

## Offene Eigentümer- und Livefragen

- MoveFix-/RotationCorrections-Consumer-Migration bleibt CMB/SCF-1b. SCF-1a erneuert
  nur gültige Wünsche und nutzt den bestehenden bestätigten Consumerpfad; keine
  veränderte Formel, zweite Engine oder neue globale Reservation.
- Aura-Konkurrenz in aktiven Planlücken ist getestet. Aura-eigener Lifecycle,
  direkte isOwner-Consumer und Konkurrenz während einer künftigen Disable-Lease
  benötigen einen gesonderten CMB-Auftrag.
- Resume erfordert eine neue bestätigte Source. Ein gespeicherter Wunsch bleibt
  Scene-gebunden und ist kein Placement-/MoveFixrecht. Welt-/Connection-/Pipeline-/
  Epoch-/Observergenerationgrenzen verwerfen ihn vollständig.
- TimerControl bleibt wertbasiert. Gleichwertige Fremdwrites sind nicht erkennbar;
  anderer Wert oder anderes Timerobjekt gewinnt. InputResolver/TimerArbiter und
  allgemeine MOD-Slottransaktionen sind nicht Teil dieses Schnitts.
- Bei abweichendem externem Replay kann wegen der bestehenden lastReportedYaw-
  Kopplung erst ein weiterer tatsächlich rotierender C03 den Wunsch bestätigen.
  Bis dahin bleibt Source-Authority gesperrt; keine Änderung an EntityPlayerSP/NET.
- `G-CMB-SPEED`, `G-SCF-ANGLES`, `G-MOV-SPEEDBASE` bleiben unberührt/offen.
  Keine neuen Grad/s-, Profil-, GCD-, Noise- oder Speed-Potion-Werte.
- **AC-R03:** In den gezielt geprüften lokalen Research-/Downloadquellen lag kein
  fertiges Reviewurteil vor; vorhanden waren dessen Eingabeprompts/Quellen-ZIP.
  Weder GO noch unabhängige AC-R03-Abnahme wird behauptet. Kein Research-Folgeauftrag.
- Keine native Spiel-, Server-, Anticheat-, OptiFine-/Shader-, GPU-/Framezeit- oder
  Releaseabnahme. JUnit-/EmbeddedChannel-Erfolg belegt diese Wirkungen nicht.

Nach SCF-1a STOPP; keine SCF-1b-Implementierung in diesem Branch.
