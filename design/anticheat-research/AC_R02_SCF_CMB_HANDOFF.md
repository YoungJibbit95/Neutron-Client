# AC-R02 — H1 Scaffold-Owner und H2 Swing/Attack

Revision 2.0 · Implementierungsfreie Fachübergabe. Geprüfte Codebasis B1c `382e995496af336aab07ca1cf6f8a41e4e54160c`, Codecommit `b8c4b606dea386e7f62e30d9563f9950836ae44c`, `F:/neutron-core-i01c`. Quellen: Original-v1.0 §§2.3/3.1/5.2/9/11, lokale I01c-Admission-/Consumer-/Ergebnisdokumente, I01b-Hookvertrag. [Baseline](AC_R02_BASELINE_AND_GAPS.md), [Trace-Entwurf](AC_R02_TRACE_SCHEMA_DRAFT.md). Q/T-code/T-report/D/H/U gelten fort; sämtliche nachfolgenden Tests sind Vorschläge, keine AC-R02-Ausführung.

## H1 — Planende und Rotation-Owner trennen

**IDs:** AC-INT-SCF-001, unterstützend AC-CORE-DIAG-001, AC-GRI-SCF-001 und getrennt AC-POL-BEH-001. **Owner:** CMB führt Accepted-Frame-/Owner-Predicate-Vertrag, SCF Plan-/Modullifecycle; CORE Integration, NET bestehender Transport, MOV Input/MoveFix nur nach fachlicher Abstimmung. Hohe Priorität, gesonderter Fachauftrag erforderlich; I01d-Scope wird nicht erweitert.

### Verifizierter Consumer und offene Stelle

B1c `Scaffold.tryPlace(boolean pre)` verweigert vor Winkel-Diagnose, Slot und Placementservice bei fehlender Admission. `onMotionEvent` plant in PRE; `tryPlace(true)` bleibt vor applyRotations und Resolve/C03, POST benutzt denselben PRE-Plan nach finishMotion. `releasePlacement` setzt weiterhin Rotation/Correction, Plan und Timer/Tower-Zustand zurück und wird nach Erfolg oder Planverlust aufgerufen. I01c repariert diese fachliche Kopplung nicht. Der Placementservice prüft nutzbare accepted Winkel und Geometrie; diese Q-Aussage ist nicht gleich einem bereits implementierten strengeren Scaffold-Owner-Predicate.

Bestehend V: SceneContext/Observer/Admission, RotationManager-Requests/confirmed Owner/effective Winkel, Placementservice, Slot-/Raycast-/Controllerguards. Vorgeschlagen P: Trennung der Release-Verantwortlichkeiten und expliziter CMB-Vertrag für verwendbare Pose **mit** passendem Owner. Kein neuer Owner-/Rotationsmanager, keine parallele Leaseverwaltung, keine Änderung der Admission-Brücke als Research-Folge.

### Bindendes Ziel und Entwurfsgrenzen

| Zustand | Ziel aus v1.0 / nötige Fachabgrenzung |
|---|---|
| Aktiv, gültiger Spielkontext, Plan vorhanden | Scaffold vor Aura, Basis letzte für aktuelle Scene lokal akzeptierte Rotation; neue gültige Ziele sanft anfordern |
| Aktiv, gültiger Kontext, Plan beendet/fehlt | Plan freigeben, Ownership nicht dadurch freigeben; Priorität bleibt bis Disable. Ohne erste gültige Acceptance keinen bestätigten Owner erfinden |
| GUI/Pause/Fokusverlust | logisch aktiviert und Rotationswunsch erhalten; keine automatische Platzierung/synthetische Eingabemanipulation. Wunsch ist keine Actionberechtigung |
| Resume | Player/World/Connection/Generation/Epoch/Slot/Plan/Rotation frisch prüfen; I01c-Admission plus Fachguards. Alter Wunsch oder numerisch gleicher Winkel beweist keine neue Bestätigung |
| Disable bei gültigem Kontext | keine neuen Place-/Input-Aktionen; sanfter Rotationsrelease innerhalb 2–4 Ticks, danach garantiert Ownerende. Zählbasis/harte Endgrenze bei Withheld/fehlenden Bodies und Pause im Fachreview festlegen; Scope ist 2–4, kein neu gewählter Default |
| Death/World-/Session-Invalidierung | sofort Owner-/Planzustand invalidieren, kein Auslauf in nächste Scene; bei invalidem Kontext keine Release-Pakete/Platzierung erzeugen |

Begrenzter Disable-Auslauf darf Aura kurz blockieren, jedoch keine fremde Platzierung autorisieren. Plan-, Timer-/Tower- und Slotcleanup müssen weiterhin korrekt bleiben; das reine Auftrennen darf keinen anderen Zustand dauerhaft halten. CMB entscheidet den Mechanismus und C03-Drop/Rewrite-Semantik vor Fachcode. Dessen Freigabe ist kein neues Winkelprofil. Standard-Präzise ist Produktentscheidung; G-CMB-SPEED, G-SCF-ANGLES und G-MOV-SPEEDBASE bleiben getrennt und unberührt.

### Sieben synthetische Fachfälle für H1/T-03, vorgeschlagen

| Fall | Setup und erwartete Abnahme |
|---|---|
| H1-01 Planlücke | zwei gültige Planzyklen mit Erfolg und dazwischen keinem Ziel: Pläne enden, Scaffold-Priorität bleibt bei gültiger accepted Basis; kein neuer Placementversuch ohne Plan |
| H1-02 Aura-Konkurrenz | Aura requestet in Planlücke/bei Scaffoldaktivierung; Scaffold hat im gültigen Kontext Vorrang; Pending allein oder owner=null mit alten Winkeln ergibt keinen gültigen Scaffold-Frame |
| H1-03 PRE/POST/Transport | vorher akzeptierter Frame, gegensätzlicher Pending-Request, C03-Drop/Rewrite/Delay: PRE nur passende vorherige Pose+Owner; POST nur aktuelle; kein Erfolg aus ungeprüftem Request |
| H1-04 Slot/Raycast/Admission | Slot/Stack/Face/Ray invalidieren; Extra und NO_STEP denied vor Service/Slot/Diagnose; Ordinary erreicht weiterhin bestehendes Budget, kein erhöhter Durchsatz durch Owner-Hold |
| H1-05 Suspend/Resume | GUI/Fokus/Pause nach Plan: logischer Wunsch bleibt, keine Place-/Inputwirkung; Resume revalidiert alle aktuellen Identitäten und Acceptance, kein stale Owner als Berechtigung |
| H1-06 Disable | gültiger Kontext, begrenzter 2–4-Tick-Auslauf, vorübergehende Aura-Konkurrenz; keine Place-/Inputaktionen ab Disable, garantiertes Ende. Withheld/Pause während Auslauf gesondert nach beschlossenem Zählvertrag prüfen |
| H1-07 Harte Grenze | Death/World-/Connection-/Epochwechsel während aktivem Plan oder Auslauf: sofort abbrechen, keine Cross-Scene-Restores; Exceptioncleanup bleibt. Auch A→B→A mit beobachtbarer Invalidation |

Bereits vorhandene T-code-Anker: `AutomatedPlacementAdmissionTest.preUsesPreviousAcceptedAngleAndPostUsesCurrentAcceptedAngle`, `pendingFirstRotationIsNotAcceptanceAndCancelledPostCannotPlace`, `extraDenialConsumesNoSlotBudgetOrSuccessAndOrdinaryCanStillPlace`; `AutomatedActionModuleTest.deniedScaffoldExtraStillComputesRotationsButNeverPlacesOrSwitchesSlot`. Sie belegen nicht die künftige Owner-Hold-Migration und sind nicht von AC-R02 ausgeführt.

## H2 — Gelegenheit, Swing und Attack tatsächlich korrelieren

**IDs:** AC-INT-CMB-001, AC-CORE-DIAG-001; Grim/Polar nur getrennte spätere Messungen. **Owner:** CMB Opportunity/Scheduler/Controllerzuordnung, NET Selection/Hold/Barriere/lokale Annahme; MOD bei Slot-/manuellen Pfaden, VIS nur read-only. Hohe Priorität als Diagnose-/Testentwurf, kein neuer Autoclick-/Networkmodus und keine sichere CPS-Zahl.

### Verifizierter Consumer und Kausalgraph

Aura-B1c: POST-Admission → Motionaccepted/Owner/validHit → AttackScheduler.tryAttack → optionaler Recheck, Autoblock.beforeAttack und finaler validHit → temporäre Gameplay-Yaw/Pitch → swingItem/C0A → PlayerControllerMP.tryAttackEntity/C02 mit erneutem `validHit`/Packetguard → lokale AttackResultEvent-Rückmeldung. `finally` restauriert Kamera. AutoClicker2 (EventUpdate) und HitDelayFix (EventUpdate PRE) sind ebenso admission-gated; physische Taste macht ihre generierten Klicks nicht manuell. PRE-Aura-Rotation bleibt auf Extra aktiv; PRE-/POST-Autoblock-Motion-Nebenwirkungen sind gated, manuelles onAttack/Cleanup bleibt Bestand.

Darstellung als **partielle Reihenfolge**, nicht als künstliche Gesamtkette: Gelegenheit → Sendefreigabe → C0A-Aufruf → [C0A selected/ggf. lokale Annahme oder Abbruch] → C02-Versuch → [C02 selected/ggf. lokale Annahme oder Abbruch] → optional externe Serverbeobachtung. Pro Paket eigene Zeit/ID/Stufe. Opportunity kann ohne Swing, Swing ohne C02, C02 lokal ohne Server-Hit enden. Packet-Holds betreffen im aktuellen Outboundpfad regulär C03; Nicht-C03 löst Barriere aus. Kein generisches C02-Hold-Verhalten erfinden.

Bereits der C0A-Pfad kann eine bestehende C03-Queue freigeben; spätere effektive Pose/Scene/Hitgültigkeit kann dadurch anders sein. Verzögerte Auslieferung queued Movement aufzeichnen, aber bestehende FIFO/Actionbarriere nicht umgehen. Off-thread-Scheduling ist noch keine lokale Acceptance. Correction/Death/Disconnect können Entries invalidieren; spätere Events gehören zur frischen Scene und dürfen keine alte Aktionsberechtigung übernehmen.

V: Scheduler, PlayerControllerMP, Pipeline/Delay, Admission, Observer. P: skalarer Opportunity-/Packet-Korrelationsadapter und Export im bestehenden Diagnosestack, ohne erneute Guard-Abfragen fürs Logging. Keine neue Attackreservation, keine gleichzeitige API-/CPS-/Autoblockmigration. Eine Actorquelle außerhalb Body muss ausdrücklich MANUAL_VERIFIED, AUTOMATIC_OUTSIDE_BODY oder UNKNOWN sein; TickEvent/GUI nicht als Ordinary vortäuschen. Mehrere Gate-Abfragen dürfen weiterhin nichts reservieren.

### Sieben synthetische Fachfälle für H2/T-04, vorgeschlagen

| Fall | Setup und erwartete Abnahme |
|---|---|
| H2-01 Normale Aktion | Ordinary POST mit accepted Aura-Frame: echte Opportunity/C0A/C02/lokale Annahmen getrennt, kein serverConfirmed ohne externen Beleg |
| H2-02 Admission | Extra, NO_STEP, historische/Withheld-Diagnose, SUSPENDED: Pilotclicks/Attackversuch denied; keine künstlichen Folgeevents und kein Scheduler-/Clicktimerverbrauch bei Denial |
| H2-03 Zielverlust | Ziel/Owner/Scene zwischen Swing und Controllerguard verändert: C0A kann existieren, C02 entfällt; korrekte Gründe/Camera-finally, kein rückwirkender Swing-Delete |
| H2-04 Queue/Rewrite | C03 von zwei Holds gehalten; C0A-Barriere replayt und verändert Pose; C02-Guard entscheidet frisch. Identität/Selection/accepted nicht aus enqueue ableiten |
| H2-05 Manueller Pfad | clickMouse/Controller außerhalb Body plus synthetischer Klick bei gehaltener Taste: Origins unterscheidbar, vorhandene Budgets/Manualpfade erhalten; keine globale Admission am Controller |
| H2-06 Lifecycle/Callback | Epoch-/Worldwechsel, spätes Callback oder Exception: alte refs nicht im neuen Run wiederverwenden, tatsächlich transportierte Pakete separat; kein Servererfolg aus AttackResultEvent |
| H2-07 Abschluss/Drops | Body kann nach früherem Event abbrechen; Abschluss INTERRUPTED ohne Rücknahme vergangener Effekte. Bufferüberlauf/Trace-Off/Sampling sichtbar, keine verlorene Folge als fehlender C02 interpretieren |

T-code-Anker: `AutomatedActionModuleTest.auraOrdinaryCanSendAcceptedAttackAndExtraCannotRepeatAttempt`, `auraOrdinaryReachesSchedulerButRejectedMotionStillPreventsAttack`, `physicalClickAndManualWindowClickWithoutStepStillReachNativeController`; Admission-Test `repeatedQueriesReserveNothingAndExistingSchedulerCanStillReject`. Vorhandene Tests sind keine vollständige Implementierung des Trace-Schemas.

## Spätere Labormessung und Entwicklungsreview

Gemeinsam: autorisierter Server, exakter Client-/AC-/Servercommit/JAR-Hash, Konfiguration, Cloud-/Truststatus, Inputspur, RTT/Jitter/TPS, Run-ID und dokumentierte Kontrollkanäle. Vanilla und Neutron ohne Module zuerst, dann Einzelmodule und vorab festgelegte Kombination. H1 misst bestätigte Blöcke, Stürze/Ghostblocks, Ownerwechsel und reale C03/C08-Serverzeit; H2 misst Opportunity/C0A/C02 und bestätigten Schaden statt nur Setting-CPS. Dauer/Exposition/δ/ε vorab festlegen nach bestehendem AC_TEST_PROTOCOL, keine neue sichere Grenzzahl.

| Anticheat | Getrennte Hypothese / Messgrenze |
|---|---|
| Intave, AC-R01-Pin `b950bdf7570b8624b7907e2c42dfb3b6d63c40f6` | H1: Owner-/Pose-/Packetfolge kann mit Placementbefunden korrelieren; H2: Swing/Attackstatistik und spätere Mitigation getrennt. Kein Nachweis ohne installierte Config/Traces |
| Grim 2.0, `1299a2aa35b2138df2604f9c7d6bcae357afef31` | H1 Geometrie/RotationPlace getrennt; experimentelle Checks mit Aktivierung dokumentieren. Aus Intave-Swingbefund keine Grim-Regel ableiten |
| Polar, Build UNBEKANNT | erst T-08 PolarLogs-Kanalabdeckung; getrennte beobachtete Wirkung ohne Aussage über proprietären Check. Null Meldungen keine Negativbestätigung |

Freigabe je Handoff erst nach Review der CMB-/SCF-/NET-Verträge und konkretem separatem Auftrag. Synthetische Regressionen müssen aktuelle Admission/Budgets/Manualpfade erhalten; Live-Kompatibilität braucht eigene versionierte Evidenz. Numerische Gates, Rechteklärung und offene Timer-/Input-/Inventarpolicy bleiben gesondert. AC-R02 endet mit diesen Dokumenten.
