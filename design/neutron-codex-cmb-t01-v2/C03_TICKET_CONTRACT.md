# CMB-T01-v2 — gebundenes spätes Scaffold-Sendeticket

28.10.2026. Gilt ausschließlich für die aktive Scaffold-Source auf SCF-1a.
Lokale Transportannahme ist keine Serverbestätigung. H1-06 bleibt offen.

## Tatsächliche API

```java
RotationManager.ResolvedC03SendTicket
    captureResolvedC03SendTicket(float wireYaw, float wirePitch);

// öffentliche finale innere Klasse, unveränderliche Bindungen
Predicate<Packet<?>> ResolvedC03SendTicket.guard(C03PacketPlayer original);
```

`resolve(EventMotion)` zeichnet erst nach `event.setRotations` für exakt
`scaffold` einen eindeutigen Resolveorigin auf und bindet die Beobachtung dieses
Zeitpunkts. Das sind der ausgewählte Request, Managerorigin, Motionsequenz,
Player/World, NetHandler, Pipeline, kanonischer Observer, aktive Outer-ID und
Scene einschließlich Connectiongeneration/Packetepoch. Der anschließende
Capture-Getter kopiert diese unveränderlichen Belege und ergänzt ausschließlich
die vom echten Caller gelieferten bisherigen Wirewinkel. Er sampled keine neue
Scene, führt keinen zweiten Resolve aus und schreibt keinen Managerzustand.

Der Caller erfasst das Ticket unmittelbar nach Resolve, vor dem C03-Bau.
`guard(original)` versiegelt vor PRE die tatsächliche C03-Klasse, Moving-/Rotating-
Flags, Groundstatus und, wenn moving, die Position. Nach PRE wird ausschließlich
das tatsächlich ausgewählte Paket geprüft. Objektidentität des Pakets ist keine
Voraussetzung; Klassen- und Poseäquivalenz sind es.

Kein aufgelöster Scaffoldrequest -> null, bestehender neutraler Sendepfad.
Aufgelöster Scaffoldrequest mit fehlendem/ungültigem Scope -> nicht-neutrales,
verweigerndes Ticket. Ein zuvor bestätigter Owner ist weder erforderlich noch
ausreichend. Pending ohne Resolve erzeugt kein Ticket. ID 0 ist gültig; -1 und
historisch positive IDs außerhalb der aktiven Dauer sind ungültig.

## Apply-Vertrag

Der echte `EntityPlayerSP`-Caller benutzt für dieses Ticket die vorhandene
`addToSendQueueTracked(Packet, Predicate<Packet<?>>)`-Überladung. Die vorhandene
Pipeline ruft das Predicate nach normalem Packet-PRE im Apply auf. Delayed-Apply
behält dasselbe Predicate. Es gibt keinen neuen Listener, Transportpfad, Timer,
Queuebesitzer oder automatischen Flush.

Die Prüfung verlangt gleichzeitig:

- denselben Manager im Runtimeaccessor, Request, Resolveorigin und Motionsequenz;
- collecting-Phase, dieselben Motion-/Clientobjekte, Handler, aktive Pipeline
  und Viewentity; NetworkManager steckt im Handler und im Scenevergleich;
- dieselbe kanonische Observerinstanz und aktive Outer-ID >=0;
- die ursprüngliche und frisch gesampelte Scene gültig und ACTIVE, mit gleichen
  Identitäten, Generation und Packetepoch;
- den versiegelten Paketenvelope und tatsächliche Winkel jeweils strikt unter
  0.001 Grad Abstand zum Resolve; Yaw mit vorhandenem Wrap, Pitch innerhalb
  [-90,90] und beide endlich.

Bei nichtrotierender C03 ist die ursprüngliche Wirepose maßgeblich. Die C03 kann
so eine unveränderte bereits berichtete Scaffoldpose halten, aber keine neue
ungesendete Pose erfinden. Ein gleichklassiger äquivalenter Rewrite einschließlich
180/-180, ganzzahliger Vollumdrehung und bestehender Untertoleranz bleibt möglich.
Numerisch äquivalente Rewritepose allein ist nach dem vorhandenen Vertrag kein
beweisbarer fremder Sourcewechsel. Klassen-, Positions-, Flags-, Ground- oder
abweichende Winkeländerungen erben das alte Recht nicht.

`beginMotion`, `finishMotion`, voller Reset und passender Sourcereset löschen die
aktuellen Resolvebelege. Fremder Sourcereset lässt Scaffold unberührt. Neue
gleichnamige/gleichwinklige Requests besitzen andere Request-/Resolveidentitäten.
Objekt-/Generation-/Epoch-/Tick-/Shutdown-/Deathgrenzen können alte Captures
nicht wiederbeleben. Ein späterer Capture-Getter legitimiert auch keinen alten
Resolve unter einer inzwischen neuen Outer-ID: Dafür gibt es einen zusätzlichen
vor der Korrektur ausgeführten Assertion-RED (`late-capture-red`).

## Reads, Reentranz und Fehler

Ticket, Predicate und Capturegetter schreiben keine Request-/Owner-/Rotation-
oder Spielzustände, eröffnen keine Ticks/Steps, verbrauchen kein Admissionbudget
und erzeugen keine Pakete. Die späte Freshnessprüfung benutzt ausdrücklich
`RuntimeObservation.captureScene`: dessen bestehende Weakreferenz-/Generations-
Buchhaltung ist Scene-Sampling, kein reiner IDgetter. Bei stabiler Scene bleibt
die Generation stabil; diese Buchhaltung wird nicht als quotaverbrauchender
Playerstep ausgegeben. Nach Sampling werden Frame/Objekte/Observer/ID nochmals
gelesen, damit reentrante Scenequellen keine vorherige Prüfung überholen.

Sourceticket-Denial liefert null aus tracked und damit finishMotion false. Kein
Ersatz-C03 und kein neues Placement-/Slot-/Attackrecht. Delay liefert synchron
null; das anschließende Finish entzieht dem späteren Apply den alten Frame,
auch innerhalb derselben Outer-ID. Eine erneute reguläre Bewegung benötigt
einen neuen echten Begin/Request/Resolve, kein Wiederverwenden des Tickets.

Reentrantes Walking in Packet-PRE kann einen neueren Frame vollständig senden.
Der ältere Frame wird danach am Guard abgewiesen und darf den neueren Frame
nicht nochmals finishen, Reportingfelder überschreiben oder Motion POST posten.
Synchroner RuntimeException/Error bei der Ticket-Sendung wird unverändert
weitergeworfen; ein noch eigener Frame wird abgelehnt beendet. Ein neuerer Frame
wird dabei nicht angefasst. Das echte Observerfinally endet Step/Tick. Kein
catch-and-allow. Vor Transport werfende PRE-/Validierungsfehler senden nichts.
Ein Fehler erst in Packet-POST kann bereits geschehenen Transport nicht rückgängig
machen; dies wird nicht als verhinderter PRE-Transport behauptet. Die vorhandenen
Pipeline-Pump-/Drain-Failurepfade bleiben unverändert.

Tickets sind interne Belege für den einen bestehenden Caller-Sendversuch,
keine öffentliche Mehrfachsendungs- oder Serveracceptancegarantie. Pure Reads
verbrauchen kein Token; der bestehende Pipeline-Apply läuft pro Envelope einmal.
Unbeobachtbare beliebige A-B-A-Feldwrites ohne vorhandenen Lifecycle-/Samplehook
werden nicht als neu überwachtes Ereignissystem versprochen.

## T01–T18: konkrete Abnahmeanker

`L` = ScaffoldLateC03TransportTest; `R` = ResolvedC03SendTicketTest.
Endgültige frische Offline-XMLs und Laufzahlen stehen in RESULTS.

| ID | Ausgeführter Prüfanker |
| --- | --- |
| T01 | L.t01OriginalGuiAndDisablePreRejectsNinePointNineDegreeLook; zusätzlich bytegleiche externe B08/B20-Originalsonde mit PRE-Yaw 9.900001/Kamera 0 |
| T02 | L.t02DisableAloneRejectsTheResolvedRequest |
| T03 | L.t03EachLateSuspensionRejectsButUnchangedActiveFrameAccepts: GUI, Fokus, Pause einzeln im Packet-PRE; unveränderter Positivframe |
| T04 | L.t04FirstRequestAtRealOuterIdZeroConfirmsOnlyAfterTransport; R.t04PendingIsNotResolvedAndCaptureCannotResolveOrCreateAcceptance; migrierte F01/F02 |
| T05 | L.t05OriginalMissingOuterAndHistoricalTickNeverGrantScaffoldTransport; L.t05NullInvalidatedAndSuspendedObserverNeverGrantSyntheticTransport; R.t05NullObserverCannotBecomeNeutralAfterARealResolve; R.t05LateCaptureCannotRebindAResolveToANewOuterTick; eigenständige ID0-Positivprobe |
| T06 | L.t06LivePlayerWorldConnectionPipelineAndEpochChangesRejectSamePose; unveränderte ScaffoldOwnerContinuityTest.a11PlayerWorldConnectionPipelineAndDeathInvalidateOldBasis und a11EpochInvalidatesOldHoldWithoutRestoringAForeignTimer |
| T07 | L.t07GenerationShutdownDeathAndSampledIdentityAbaRejectOldCapture; R.t07ObserverReplacementAndGenerationAbaCannotRestoreOldTicket; vorhandene RuntimeObservationHookTest-WorldUnload-/Shutdownregressionen |
| T08 | L.t08OwnResetFullResetAndManagerReplacementRevokeButForeignResetDoesNot; R.t15OwnResetAndSameNumbersInANewResolveCannotReviveOldGuard |
| T09 | L.t09PacketCancelAndMotionCancelPreserveTheirDistinctPostContracts: Motioncancel ohne POST, Packetcancel mit rejected Motion POST |
| T10 | L.t10DelayedApplyAndActionBarrierCannotReviveFinishedFrame; L.t10NormalDelayedReleaseCannotReplayAnEndedWalkingCapture; R.t10EvenSameTickReplayAfterFinishHasNoLateFramePermission; bestehende NetworkMoveFixIntegrationTest-/RotationOwnerPredicateTest-Delay-/Barrierfälle |
| T11 | L.t11SelectedRewriteChecksPoseClassAndExistingAngularEquivalence; R.t11WrappedHalfTurnToleranceAndNonRotatingWirePoseAreExplicit |
| T12 | L.t12VanillaAndT14OtherOwnersRemainNeutralWithoutAnObserverTick; bestehende unownedAcceptedCameraFrameRemainsNumericallyUsable |
| T13 | unverändert AutoHealTickEventGateProbeTest.manualRightClickAndWindowClickWorkWithoutObserverTickOrStep; volle Input-/Controllerregression |
| T14 | L.t12VanillaAndT14OtherOwnersRemainNeutralWithoutAnObserverTick; RotationOwnerPredicateTest.c10ExistingPrioritiesChooseScaffoldInEitherOrderButRequireRenewedRequests; RotationModuleBindingTest und SprintOrderingTest mit unveränderten inneren Checks |
| T15 | L.t15IdenticalRequestAfterReenableOrNewFrameCannotReauthorizeOldPacket; R.t15OwnResetAndSameNumbersInANewResolveCannotReviveOldGuard |
| T16 | L.t16NestedWalkingCannotSendOrFinishTheOuterStaleFrameTwice; L.t16PacketPreThrowPreservesThrowableAndObserverFinallyWithoutTransport; R.t16ValidationThrowPropagatesBeforeTransportAndReentryIsRechecked |
| T17 | R.t17HundredPermissionReadsPreserveOwnerRequestTickStepQuotaAndTransport; Vergleich sämtlicher Managerfelder, identische Step-/Listenreferenzen, unveränderte ID/Queue/Owner und null Outbound |
| T18 | JDK 1.8.0_391, mvn -o clean test, frische komplette XMLs, Klassenheader major52, unveränderte 3 Opt-in-Skips, selektiver Diff und Assertionsreceipt |

## Explizite Grenze

Keine Release-Lease, Disableglättung, neue 3-Outer-Tick-/200-ms-Implementierung,
Scaffold-/PacketPipeline-/NetHandler-/Scene-/Observer-/Minecraftänderung,
Aura-/AimAssistpolicy, MoveFix-, VIS-, REL-, Launcher-, Dependency- oder
POMänderung. Manual/Vanilla behalten den bestehenden neutralen Pfad. Keine
Server-, Anticheat-, native Gameplay-, GL- oder Releaseabnahme. STOPP nach CMB-T01-v2.
