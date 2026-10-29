# CORE-I01e — AutoHeal-TickEvent-Admission

Stand: 28.10.2026. Umsetzung erst nach bestandenem Gate A (32/0/0/0).
Geändert wird ausschließlich die Initiierung im bestehenden AutoHeal-TickEvent-
Listener. `AutoHealInventoryTransfer`, Controller, Runtime und PacketPipeline
bleiben unverändert. Vollständige Laufzahlen stehen in `RESULTS.md`.

## Tatsächliche Startregel

Der Listener führt zuerst unverändert `bindContext()` / `transfer.bind()` und
`transfer.blocked()` samt Statuspflege aus. Danach gelten weiterhin Fokus,
Playerstatus, Controller, Health, Cursor, GUI, `using`, Cooldown und Itemsuche.
Nur das tatsächliche PRE-TickEvent darf einen neuen Start auslösen.

Direkt vor `useHealingItem(potion)` beziehungsweise `transfer.begin(predicate)`
prüft die neue private statische Methode `mayStartTickAction()`:

1. Der kanonische `RuntimeServices.observation()` existiert.
2. `getActiveOuterTickId() >= 0`; die erste ID 0 ist erlaubt.
3. Kein CurrentStep, keine abgeschlossene Bodyhistorie und kein Withheld dieser
   Tickentscheidung: Das echte TickEvent liegt vor dem Playerbody.
4. Zwei neu erfasste Scenes sind gültig, ACTIVE und über `sameSceneAs` identisch.
   Connection/Player/World, Generation und Packet-Epoch müssen übereinstimmen;
   Pipelineidentität wird vom bestehenden Observer in der Generation abgebildet.
5. Nach dem Sampling sind derselbe kanonische Observer, dieselbe aktive Tick-ID
   und dieselbe zulässige Vor-Body-Phase weiterhin vorhanden.

Die Regel besitzt keine Felder, Reservation, Lease, Clock, Listener oder Budgets.
Sie liest keine historische Tick-ID als Berechtigung und erzeugt keinen Step.
History/Withheld werden ausschließlich zur Ablehnung eines phasenfalschen
synthetischen TickEvents benutzt. `AutomationEligibility` wird hierfür nicht
verwendet, und dessen Action-Enum bleibt unverändert.

Ablehnung startet weder C08 noch C0E, wählt keinen Slot, erzeugt kein Pending
und konsumiert keinen neuen Cooldown. Die bisherigen Kontext-/Cleanupregeln
dürfen unabhängig davon weiterarbeiten. Der Guard ruft weder `reset()` noch
`transfer.clear()` auf. Itemtypen, Stärken, Defaults und Timings bleiben gleich.

## Sieben ursprüngliche I01d-Lücken

Die tatsächlich vorhandene externe Sonde wurde vor Übernahme vollständig gelesen:
`F:\neutron-core-i01d-sources\AutoHealTickEventGateProbeTest.java`, SHA-256
`7e9cacba99b8eddda4b071f023f4757e1cb43197143510e63d848d6e2cfb48a2`.
Ihr historischer Lauf 23/7/0/0 wird nicht als neuer I01e-Lauf ausgegeben.
Die dauerhafte gleichnamige Testklasse erweitert die 23 Fälle auf 40.
Die folgenden sieben Assertions scheitern tatsächlich vor dem AutoHeal-Fix und
bestehen danach im fokussierten 124er-Lauf:

| Ursprünglicher Fall | Dauerhafter Test in `AutoHealTickEventGateProbeTest` |
| --- | --- |
| Kein beobachteter Tick | `requirementNoObservedOuterTickMustDenyItemUse` |
| Beendeter Tick, positive historische ID und ACTIVE-Scene | `requirementEndedOuterTickMustDenyItemUseDespitePositiveIdAndActiveScene` (ID 1) |
| Fehlender kanonischer Observer | `requirementMissingCanonicalObserverMustDenyItemUse` |
| Pause ohne GUI bei Fokus | `requirementPauseWithNoGuiAndFocusPresentMustDenyItemUse` |
| Inkonsistente Player-/Worldidentität | `requirementInvalidPlayerWorldIdentityMustDenyItemUse` |
| Shutdown | `requirementShutdownMustDenyNewItemUse` |
| Optionaler Transfer vor erstem Tick | `requirementNoObservedOuterTickMustDenyTransferStart` |

## Pflichtmatrix B01–B14

Alle nachfolgend benannten neuen Tests gehören zu `AutoHealTickEventGateProbeTest`.
Sie laufen über den echten Listener, den kanonischen Observer, native Controller
und die bestehende EmbeddedChannel-Pipeline. Die Welt-/Health-/Inputdaten sind
synthetisch. Es wird keine Serverheilung behauptet.

| ID | Nachweis und tatsächlich geprüfte Grenze |
| --- | --- |
| B01 | `requirementNoObservedOuterTickMustDenyItemUse`: paketfrei, Slot/Cooldown/Timerspeed unverändert |
| B02 | `requirementEndedOuterTickMustDenyItemUseDespitePositiveIdAndActiveScene`: ID 1 bleibt historisch erhalten; Start verweigert |
| B03 | `requirementMissingCanonicalObserverMustDenyItemUse`; zusätzlich `reentrantCanonicalObserverRemovalDenies` nach dem zweiten Sampling |
| B04 | `requirementPauseWithNoGuiAndFocusPresentMustDenyItemUse`: frische SUSPENDED-Scene verweigert |
| B05 | `requirementInvalidPlayerWorldIdentityMustDenyItemUse`, `missingConnectionAndClosedPipelineDenyStarts`: falsche World, fehlende sendQueue und geschlossene Pipeline |
| B06 | Shutdownfall und `resetDeniesUntilAFreshOuterEntry`: Reset verweigert, neuer tatsächlicher Observer-Eintritt erlaubt frische Prüfung |
| B07 | `requirementNoObservedOuterTickMustDenyTransferStart`, `transferSamplingEndDeniesWithoutPredictionPendingOrCooldown`: kein C0E, keine Prediction/Subscription, Quellstack bleibt gleich |
| B08 | `activeTickWithoutCurrentStepUsesOnceAndRestoresSlot` sowie aktive Transferstarts in ACK-/Rejecttests: gültige ID 0 ohne CurrentStep erreicht C08/C0E; Cooldown und Restore bleiben |
| B09 | `laterWithheldDoesNotRevokeTheTickEventAttempt`: bereits gestarteter Versuch bleibt, kein erfundener Body; `syntheticTickInsideEitherBodyOrAfterWithheldCannotStart` verweigert spätere falsche Starts |
| B10 | `ordinaryAndExtraEventUpdatesDoNotReplayTickEventEvenWhenCooldownReady`: beide Bodies posten Update und Motion PRE/POST bei bereiter Cadence ohne weiteren Heal; zusätzlicher Test verweigert TickEvents in Bodies/History und `postTickEventCannotStartAnAction` verweigert POST |
| B11 | GUI-/Fokus-/Tod-/Cursor-/ItemUsing-Tests, `manualSlotChangeDuringUseIsNeverRestoredOver`, `stackMutationBeforeUseCannotPassExistingPacketGuard`, `usingReentrancyDoesNotStartASecondHeal`; fremder Scaffold-Rotationowner bleibt erhalten, keine neue Slot-/Cooldown-/Timerwirkung bei Denial; bestehende Safety-/Transfer-/GuardedClicktests zusätzlich grün |
| B12 | Deterministische Änderungen im zweiten Sampling: `reentrantEpochChangeDeniesThenFreshCheckCanResume`, `reentrantExplicitGenerationBoundaryDeniesThenFreshCheckCanResume`, `reentrantPipelineReplacementDeniesThenFreshCheckCanResume`, `reentrantWorldReplacementDeniesThenFreshCheckCanResume`; neue Prüfung erlaubt danach Resume; `suspendedResumeRequiresANewCheckAndDoesNotCacheDenialOrPermission` prüft Fokuswechsel |
| B13 | `transferAckCanSettleAfterOuterTickEndsWithoutCurrentStep`, `transferRejectAfterTickEndsStillRequiresAppliedResync`, `transferTimeoutCleanupWithoutCurrentStepDoesNotAuthorizeUse`, `transferAckOrRejectSettlesAfterTickEndAndDisable`, `transferTimeoutWhileDisabledAfterEndWaitsForAppliedResync`; bestehende Transfer-Suite prüft zusätzlich falsche/cancelte ACKs, frühes S30, Quarantäne, Rollback und Contextcleanup |
| B14 | `manualRightClickAndWindowClickWorkWithoutObserverTickOrStep`: echtes privates Minecraft.rightClickMouse via Testreflection mit Stick und MISS, anschließend natives windowClick; C08/C0E ohne aktiven Tick oder Step; kein AutoHeal-Cooldown |

`reentrantEndDuringSecondSceneSampleCannotLeaveALateStartRight` prüft explizit
`endOuterTick()` während des Samplings. `reentrantNewTickDuringSceneSamplingCannotReuseTheOldTick`
prüft Ende plus Neubeginn; die geänderte aktive ID verweigert den alten Versuch.
Der Testwrapper injiziert die deterministische Grenze ausschließlich in die
bestehende Source und ruft danach weiterhin die echte `MinecraftSceneSource` auf.
Produktcode enthält keine Reflection und keine ersetzte Sceneimplementierung.

## Laufende Transaktionen und spätere Callbacks

Der Guard betrifft ausschließlich neue Starts. ACK/Reject/Resync/Timeout und
World-/Disconnectcleanup bleiben im eigenständigen Transfer-Observer erreichbar,
auch nach EndTick und AutoHeal-Disable. Ein Timeout quarantiniert weiterhin;
er akzeptiert keine Prediction und erlaubt keinen blinden Retry.
`bindContext`, `transfer.blocked()` und `info` laufen vor der neuen Startregel.

`alreadyStartedUseMayCompleteItsPacketGuardAfterEndTick` beendet den Outer-Tick
deterministisch in C08 PRE: Der gültig gestartete Versuch darf über seine
bestehenden Slot-/Stack-/Scene-/Controllerguards lokal angenommen werden und
seinen eigenen Slot restaurieren. Die neue Methode wird in keinem Callback
aufgerufen. `sent == use` bleibt lokale Transportannahme, kein Server-ACK.

## Notwendiger Bestandsfixture-Abgleich

Der erste gemeinsame 124er-Lauf zeigt sieben Transfer-Fixturefailures, weil deren
TickEvent-Helper automatische Starts ohne Observer-Tick modelliert. Zwei vorzeitig
abgebrochene Fälle lassen subscribed Heal-Instanzen zurück; die folgende neue
Klasse zeigt dadurch elf Folgefailures (unter anderem drei statt einem C08).
Der nächste frische Prozess mit korrigiertem Helper ist vollständig grün.

Geändert werden nur der Helper (echtes Begin/End im finally um jedes TickEvent)
und die Zuordnung `player.worldObj = mc.theWorld` beim existierenden simulierten
Weltwechsel. Alle bestehenden Assertions, ACK-/Timeoutregeln und Erwartungen
bleiben erhalten. `AutoHealSafetyTest` bleibt unverändert; seine reflektiven
direkten privaten Use-Aufrufe prüfen weiterhin den Paket-/Restoremechanismus,
während die neue Klasse ausschließlich die Listener-Startgrenze prüft.

Der neue ACK-/Reject-Schleifentest muss zwischen seinen zwei unabhängigen
Durchläufen das Inventar leeren. Ohne diese Vorbereitung liegt noch die erste
Suppe in der Hotbar und der zweite Durchlauf heilt statt einen Transfer zu
starten. Dieser Testfehler wurde separat korrigiert; keine Assertion wurde gelockert.

## Bewusste Grenzen und offene GUI-Gates

Zwei Samples und abschließende Identitäts-/Tickprüfung erkennen die getesteten
sichtbaren Reentranzgrenzen. Sie bieten keine atomare Garantie gegen beliebige
Änderungen nach der letzten Prüfung oder direkte unsichtbare A→B→A-Schreibungen.
Eine Boundary vor dem ersten vollständigen Sample kann nur anhand des dann
aktuellen Zustands bewertet werden. Spätere Packet-/Slotguards bleiben erforderlich;
es entsteht keine neue Epoch-/Tickanforderung für beliebig verzögerte Callbacks.
Keine native Maus-/GL-/Spielsession oder kontrollierte Serverabnahme wurde ausgeführt.

I01e schließt ausschließlich `CORE-OUTER-LIFETIME`. Offen bleiben
`MOD-SEQUENCE`, `MOD-GUI-CONTEXT`, `MOD-SUSPEND-REASONS`, `MOD-MANUAL-PRIORITY`,
`MOD-ACK-RETRY`, `MOD-DELAY-CLOSE` und `MOD-PHASE-CANCEL` samt den 33 vorgeschlagenen
GUI-Fällen aus I01d. Es gibt keine GUI-Ausnahme und keine neue öffentliche
modulübergreifende Inventory-API. CMB-/SCF-/MOV-Numerikgates bleiben unberührt.
