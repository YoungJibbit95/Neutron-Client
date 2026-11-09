# AC-R02 — Minimaler lesender Trace-Vertrag, Entwurf

Revision 2.0 · H0 / AC-CORE-DIAG-001 / T-01. Baseline B1c `382e995496af336aab07ca1cf6f8a41e4e54160c`. **P vorgeschlagen**, keinerlei Logger/Export implementiert. CORE besitzt die vorhandene Beobachtung, NET Transportstufen, VIS nur Darstellung. Keine zweite Sessionverwaltung, Packetqueue, Rotation- oder Configinstanz.

## Minimaler Datensatz

| Feld | Entwurf / Regel |
|---|---|
| `schemaVersion`, `runId`, `eventSeq` | `AC-R02-DRAFT-1`, zufällige neue Lauf-ID bei Aktivierung und jeder Scene-Invalidierung; monotone Sequenz im Run, auch für später verworfene Samples reserviert. Keine Wiederverwendung zwischen Sessions |
| `clientMonotonicTime` | Nanosekunden seit Runbeginn; keine Host-Wanduhr als Kausalitätsbeweis |
| `outerTickId` | Observer-ID, null vor vorhandenem Tick. Historische ID nach Outer-Ende allein begründet keinen aktiven Scope |
| `hasCurrentStep`, `stepKind`, `stepIndex` | Kind `ORDINARY`/`EARNED_EXTRA` nur bei echter Current-/abgeschlossener Beobachtung; sonst null. Withheld erhält weder Kind noch Index |
| `stepState` | `CURRENT_IN_PROGRESS`, `COMPLETED`, `INTERRUPTED`, `WITHHELD_OBSERVATION`, `NO_STEP` |
| `sceneGeneration`, `packetContextEpoch`, `sceneAvailability` | frisch erfasste Observerwerte; Availability `ACTIVE`/`SUSPENDED`/`INVALIDATED`, null bei fehlendem Observer. Nicht aus historischem Step als aktuell übernehmen |
| `category`, `phase`, `origin` | Kategorie unten; Phase `UPDATE_PRE_PHYSICS`/`MOTION_PRE`/`MOTION_POST`/`TICK_OUTSIDE_BODY`/`CALLBACK`/`OUTSIDE_BODY`/`UNKNOWN`; Origin `AUTOMATIC_MODULE`, `MANUAL_VERIFIED`, `AUTOMATIC_OUTSIDE_BODY`, `UNKNOWN` |
| `actionRef`, `packetRef`, `causeEventSeq` | optionale run-lokale skalare Nummern; keine Entity-ID/UUID oder Referenz auf World/Player/Connection/Packet speichern. Nach Rungrenze keine Wiederanbindung alter IDs |
| `outcome`, `reason`, `data` | kleine Whitelist von Status-/Grundcodes und numerischen Feldern; kein `toString` von Objekten, keine freien Paket-/Chatdaten |

`hasCurrentStep=false` ist bei NO_STEP und Withheld zwingend; bei historischen COMPLETED/INTERRUPTED ebenfalls false. Ein historischer Step darf nur als Abschlussdatensatz mit eigener Abschlusszuordnung erscheinen, niemals als Kontext einer späteren Aktion. Ohne gegenwärtig zugänglichen Abschlusscallback wird höchstens die nach Decision-Return vorhandene `getLastSteps()`-Liste einmalig dedupliziert ausgewertet; keine Zwischen-Schrittabschlüsse erfinden. CURRENT_IN_PROGRESS ist vorläufig, späterer Abbruch kann frühere gültige Effekte nicht rückwirkend aufheben. NO_STEP bedeutet nicht automatisch manuell: AutoHeal/TickEvent ist ein Gegenbeispiel.

## Kategorien und beobachtbare Grenzen

Q = aktueller Code enthält den Entscheidungspunkt/Wert; **der Export aller Kategorien ist P**, kein vorhandenes vollständiges Event-Abonnement wird behauptet.

| Kategorie | Tatsächliche Quelle an B1c / Exportgrenze |
|---|---|
| `MOVEMENT_ROTATION` | RotationManager begin/resolve/finishMotion/observe und lokale Position; aktuelle Werte Q. Whitelist Yaw/Pitch, motionSequence, owner (`scaffold`/`aura`/null), phasePoseKind, poseUsable, requestedFlag, acceptedFlag. Kein fremder Zielpunkt oder genauer fremder Standort im Export |
| `AUTOMATION_REQUEST` | vier Modul-Gates und zustandslose Admission Q; nur bei echtem Modulaufruf Versuchsdatensatz. Denial-Grund nur soweit Beobachtung ihn belegt, sonst `UNSPECIFIED`; keine erneute mayAttempt-Abfrage allein fürs Logging |
| `SWING`, `ATTACK` | echter swingItem-/C0A- bzw. Controller-/C02-Pfad Q; Gelegenheit separat als AUTOMATION_REQUEST-Stufe. Animation ohne Transport nicht als gesendet labeln |
| `PLACEMENT` | tryPlace, PlacementService-Rejection/Controllerausgang Q; Planref, PRE/POST, Face, Slotindex und boolesche Geometriegültigkeit, keine Weltkarte |
| `ITEM_USE`, `SLOT` | tatsächliche Use/Release-/Slot-Sync-Pfade Q, Consumer-Quelle explizit; keine Behauptung vollständiger Admission für diese Kategorien |
| `PACKET_SELECTED` | finaler Pipeline-select nach Replacement Q; Richtung/Packettyp, nur positiver Selection-Punkt. Verworfene Selection separat als `PACKET_DISCARDED`, Grund sonst unbekannt |
| `PACKET_HELD` | erfolgreicher defer/Entry in PacketDelays Q; FIFO-/Holdzahl und run-lokale Packetref. Ein nicht direkt gematchtes Folgepaket kann wegen FIFO warten. Keine Payload |
| `PACKET_LOCALLY_ACCEPTED` | ausschließlich OUTBOUND nach Transportcallback und lokalem Annahmepfad Q. Inbound `apply` ist lokale Anwendung, nicht Outbound-Acceptance; im minimalen Schema keine zusätzliche Kategorie erfinden oder umetikettieren |
| `PACKET_DISCARDED` | tatsächlicher Queue-/Context-/Callback-Abbruch Q; getrennt `DISCARD`, `VALIDATION_REJECT`, `SELECTION_CANCEL`, jeweils nur wenn Quelle erreichbar. Aggregierter Discard ohne per-Packet-Identität bleibt aggregiert |
| `SERVER_OBSERVATION` | **kein Client-Q**: optionale externe Join-Daten eines autorisierten Server-/Pluginexports oder dokumentierten Gameplay-Nachweises. Quelle, Version, Zeitintervall/Unsicherheit und Belegref Pflicht |

Nicht zugängliche Consumerphase, Hold-Owner oder Packet-Kausalität bleiben null/UNKNOWN. Ein begrenzter lesender Messpunkt an bestehender Stelle wäre später fachlich zu beauftragen; keine neuen Gameplay-/Transport-Hooks in AC-R02. Raw/Applied-Input-Snapshot ist weiterhin keine fertige Observer-API.

## Pose-, Owner- und Action-Korrelation

1. PRE: `poseKind=PREVIOUS_LOCAL_ACCEPTED`; nur `canUseRotation(true)` plus für aktuelle Scene bestätigten Owner auswerten. Pending Request separat als requested; aktueller Resolve/C03 noch nicht geschehen. POST: `CURRENT_LOCAL_ACCEPTED`, `canUseRotation(false)` plus Owner. `hasEffectiveRotation` allein genügt nicht.
2. Alte gültige Winkel können bei `owner=null` weiter vorliegen. Trace hält Winkel, `owner=null` und fehlenden Ownernachweis getrennt; kein Auffüllen mit letztem Winner. Fachliche H1-Autorisierung darf daraus keinen Scaffold-Owner machen.
3. Alle Sequenzen beschreiben reale Aufrufe; eine Opportunity kann vor Swing enden, ein Swing vor C02. Keine künstlichen Folgeereignisse. Outbound C0A/C02/C08 kann vorher gehaltene C03 über Barriere ausspielen. Deshalb an jeder realen Stufe Zeit/Scene und späteren Output erneut zuordnen.
4. Verzögertes Paket trägt optional skalare Ursprungsreferenz innerhalb desselben Runs; der Auslieferungskontext ist der tatsächliche Zeitpunkt und kann NO_STEP sein. Nach Invalidierung keine IDs in neuen Run übernehmen: alte Entries verwerfen/als unzuordenbar aggregieren. Observer-/Pipeline-Generation bleibt die vorhandene Wahrheit.
5. MANUAL_VERIFIED nur wenn konkreter Aufrufer/Fixture dies belegt. Physisch gehaltene Attacktaste plus AutoClicker-Klick ist AUTOMATIC_MODULE. Historische CurrentStep-Werte nicht an TickEvent-/GUI-Controlleraktionen hängen.
6. SERVER_OBSERVATION wird extern über nachvollziehbare Belegkorrelation zugeordnet, sonst unverbunden. `serverConfirmed` höchstens für die belegte Wirkung, z. B. Schaden/Block, nicht für generelle Checkfreiheit. Polar zuerst T-08; fehlende Meldung bleibt keine Negativbestätigung.

## Begrenzung, Datenschutz und Abschalten — Vorschlag

Opt-in über vorhandenen globalen Developer-/Diagnosepfad, kein zweiter Configstore. Entwurf: maximal 1024 skalare Records und zusätzlich 256 KiB serialisiertes Datenbudget; tatsächlicher Heapbedarf separat messen. Bei Kapazität ältesten Record ersetzen und `overwrittenCount` erhöhen; Sampling verworfene Events in `sampledOutCount`, fehlgeschlagene Erfassung in `captureFailureCount`, Sequenzlücken und `firstRetainedSeq` sichtbar. Getrennte Zähler verhindern stilles Vortäuschen vollständiger Timeline. Werte sind technische Vorschläge, keine bereits beschlossene Performancepolicy.

Request/Admission/Packetphasen im aktivierten begrenzten Run möglichst vollständig, häufige Movement-Snapshots optional alle N tatsächlich ausgeführten Bodies (N im Runheader). Renderframes erzeugen keine künstlichen Steps. Export nur manuell außerhalb kritischer Update-/Motion-/Netzpfade aus einem begrenzten skalaren Snapshot; keine Datei-I/O im Listener. Disable beendet Aufnahme reproduzierbar, erzeugt keine Use-/Slot-/Release-Aktion und leert Buffer nach optionalem explizitem Export. Kein Nachsenden von Netzwerkpaketen durch Diagnose.

Keine IPs, Namen, UUIDs, Chat, Tokens, Zugangsdaten, Paketpayloads, Inventoryinhalte oder sensiblen fremden Spielerdaten. Keine starken Spiel-/Packetobjekte im Buffer; nur whitelisted primitive Werte/enums und run-lokale IDs. Optional externe Serverdaten vor Join redigieren; Artefaktbelegref ohne eingebetteten Inhalt. Vorschlag: nur RAM bis Abschalten/Runwechsel; manuelle Exporte aus autorisierten Laboren nach spätestens 24 Stunden löschen, sofern kein bewusst gewähltes Analysefenster dokumentiert wurde; jederzeit auf Wunsch löschen. Dies ist keine bestehende Aufbewahrungspolicy und keine automatische Löschimplementierung.

144-FPS-Ziel aus v1.0 auf benannter Referenzhardware im identischen Normal-Gameplay-Szenario messen: opt-out/opt-in mit gleicher Szene, Auflösung, Renderprofil und OptiFine-/Shaderkonfiguration; Durchschnitt, p95/p99-Framezeit, Allokation/GC und Dropzahlen berichten. G-QA-144FPS/Hardware-/Spikebudget sind offen; kein bestandener Performancewert. Bei Messung können niedrigeres Sampling oder Abschalten evaluiert werden, ohne Gameplay umzuschreiben.

## Zwölf vorgeschlagene Schema-/Korrelationstests, nicht ausgeführt

| Fall | Zuordnung / erwartete Aussage |
|---|---|
| R02-T01-01 Withhold | null Kind/Index, WITHHELD_OBSERVATION, kein Body/keine erfundene Action |
| R02-T01-02 Extra | Ordinary/0 und Extra/1 gleicher Outer-ID; echte Bodyanzahl, vier Pilotaktionen auf Extra denied, Rotation weiter beobachtbar |
| R02-T01-03 Update-Cancel | CURRENT_IN_PROGRESS vor Return, endgültig INTERRUPTED; nicht Withheld und keine rückwirkende Entfernung früherer Events |
| R02-T01-04 GUI/Resume | SUSPENDED ohne falschen Scenewechsel; Resume neu erfassen; keine Wiederverwendung alter positiver Admission |
| R02-T01-05 Welt-ABA | A→B→A mit vorhandener Invalidation ergibt neue Generation und Run-ID; kein alter Owner-/Actionjoin |
| R02-T01-06 Packet-Epoch | gleiche Objektidentitäten bei Correction/Discard, neue Epoch; alte Packet-/Actionref nicht in neuen Run übernehmen |
| R02-T01-07 PRE/POST | PRE vorherige accepted Pose, Pending separat; POST aktuelle accepted Pose; abgelehnter POST nicht als Erfolg |
| R02-T01-08 Null Owner | alte effektive Winkel bei owner=null sichtbar, keine falsche Scaffold-Autorisierung/Ownerergänzung |
| R02-T01-09 Hold/Discard | FIFO mit zwei Holds, C0A-Barriere, Contextdiscard; selected/held/accepted/discarded nicht verwechselt; aggregierte fehlende Details gekennzeichnet |
| R02-T01-10 No-Step | manueller Controller und AutoHeal/TickEvent getrennte Origins; keine historische lastStep als CurrentStep |
| R02-T01-11 Drops/Disable | Überlauf/Sampling ergeben passende Zähler/Sequenzlücken; Export außerhalb kritischer Pfade, Ausschalten/Runwechsel löscht gemäß gewähltem Vorschlag |
| R02-T01-12 Server/Overhead | optionale Serverjoins nur mit Beleg und Zeitunsicherheit, Polar-Kanallücke unbestimmt; Privacy-Whitelist und opt-in/opt-out-144-FPS-Messdesign prüfen |

Diese Fälle präzisieren H0/T-01; die AC-R01-IDs T-02…T-08 und ihre Server-/Fachnachweise bleiben zusätzlich bestehen.
