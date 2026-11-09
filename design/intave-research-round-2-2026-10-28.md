# Intave: zweite Analyserunde und verbesserte Konzepte

Stand 28.10.2026. Pulsar: `prep/neutron-opencode-wave00`, HEAD `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`; Intave: [`b950bdf7570b8624b7907e2c42dfb3b6d63c40f6`](https://github.com/intave/intave/tree/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6). Fortsetzung des [Forschungsplans](intave-bypass-research-plan-2026-10-28.md) und des [Rotations-Audits](research-rotation-aura-scaffold-2026-10-28.md). Statische Codeanalyse, keine Serverausführung und keine Änderung an Produktcode oder Defaults. Repositories und Konfigurationen anderer Server können sich unterscheiden; Cloud-ML bleibt außerhalb der hier untersuchten Quellpfade.

Evidenz: **A** = konkret im Quellstand gelesen, **H** = daraus entwickelte Hypothese, **U** = nicht getestet. Alle nachstehenden Konzepte sind H/U, keine bestätigten oder flaglosen Bypasses.

## Neue Befunde, die die erste Planung verändern

1. **MoveFix muss zusammen mit der 1.8-Inputrekonstruktion untersucht werden.** Intaves `KeypressBrancher` führt, wenn der Client seine Eingaben nicht sendet, Kombinationen aus diskretem Forward/Strafe durch und schränkt sie abhängig vom Sprintzustand ein. Pulsars PRECISE transformiert dagegen den Eingabevektor kontinuierlich. SIMPLE nähert die Richtung in acht Sektoren an, erhält aber die Eingabelänge und erzeugt nicht zwangsläufig exakt Vanilla-Tastenwerte; der Sprungimpuls hat eine eigene Yaw-Logik. Das ist eine konkrete Ursache für mögliche Simulationsabweichungen, jedoch kein Beweis für einen Flag. Quelle: [KeypressBrancher](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/physics/branch/KeypressBrancher.java), Pulsar `MoveFixController.correct/jumpYaw`.
2. **Scaffold-Paketzeit ist ein eigener Prüfpfad.** Intaves [PacketOrder](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/world/placementanalysis/PacketOrder.java) protokolliert den Abstand zwischen zuletzt empfangenem Movement und einer tatsächlichen Blockplatzierung; vier Abstände bilden jeweils einen Mittelwert. Bedingungen, Zähler, Konfiguration und Servertiming bestimmen die Wirkung. Pulsars PRE-/POST-Optionen liegen auf unterschiedlichen Seiten des C03, aber die tatsächlichen Serverabstände müssen gemessen werden. Die Codezahl des Checks ist kein universelles „sicheres Delay“.
3. **Attack-Versuch und Swing-Erfolg sind in Pulsar getrennt.** Aura sendet `swingItem()` vor `tryAttackEntity`; danach kann das Controller-Gate einen Angriff noch verwerfen. Durch die Outbound-Barriere kann bereits der Swing zurückgehaltene C03 ausspielen; danach wird die aktuelle Hit-Gültigkeit erneut geprüft. Ein Swing ohne Angriff ist daher möglich, ohne einen Bug oder eine Intave-Erkennung zu beweisen. Intave hat gesonderte [Swing-Order-](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/heuristics/other/PacketOrderSwingHeuristic.java), [NoSwing-](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/heuristics/other/NoSwingHeuristic.java) und Swing-Intervallpfade. Auswertung muss C0A und C02 einzeln verfolgen.
4. **Die Legacy-Item-Verzweigung erlaubt nicht beliebig viele Zustandswechsel.** In [UseItemBrancher.branchLegacy](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/physics/branch/UseItemBrancher.java) wird bei aktiver Hand der verlangsamte Kandidat mit deaktiviertem Sprint betrachtet. Unter Zusatzbedingungen existiert ein Kandidat für einen zunächst nicht angekündigten Slotwechsel, der anschließend aufgelöst werden muss. Ein möglicher einzelner Mehrdeutigkeitszweig ist kein bestätigter dauerhaftes NoSlow-Fenster; Blocking- und Protokollchecks laufen zusätzlich.
5. **Timer-Forschung braucht Feedback und reale Uhr.** [PlayerTime](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/timer/PlayerTime.java) aktualisiert ein Bewegungszeitmodell pro Bewegungspaket und verwendet über Feedback synchronisierte Grenzen. Bei bestimmten Violation-Bedingungen kann es zurücksetzen und anschließende Angriffe zeitweise verhindern. [MicroBlink](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/timer/MicroBlink.java) betrachtet zusätzlich u. a. Zusammenhänge zwischen Bewegungsabstand, Zeitabstand, Entity-Nähe und Combat-Historie. Eine über viele Ticks ausgeglichene Zahl alleine ist deshalb kein ausreichendes Modell.
6. **Backtrack-Position ist nicht dasselbe wie Intaves angreifbare Historie.** Pulsar vergleicht empfangene und lokal angewendete Entityposition und hält gezielt S14 zurück; `AttackRaytrace` verfolgt eigene Entity-/Feedbackzustände und kann zu alte Zielzustände wegen ausstehender Feedbackpakete behandeln. Daher muss ein Versuch `received`, `applied` und serverseitigen Angriffs-/Feedbackzustand separat erfassen. Ein Render-Box-Abstand ist kein bestätigter Reach-Gewinn. [AttackRaytrace](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/AttackRaytrace.java).

## 1. Gemeinsamer Analysevertrag: drei Perspektiven

Jede Untersuchungszeile verbindet drei Zustände, ohne sie gleichzusetzen:

| Zeitpunkt | Client-Aktionssicht | Netzwerk | Intave-Sicht |
|---|---|---|---|
| Vor Bewegung | physische Eingabe, angewendeter Input, aktuelle Velocity, Kamera und bestätigter Owner | ggf. vorhandene FIFO-Holds | zuletzt bekannte Position, Rotation, Item und Sprint; offene Feedbacks |
| Motion PRE | neuer Plan/Request; Bewegung des Schritts bereits erfolgt | Aktionen können vor dem aktuellen C03 liegen | letztes verarbeitetes Movement und Aktions-Historie |
| Nach Motion | lokal angenommene Position/Rotation, gewählter Owner | C03-Variante, evtl. Delay/Rewrite/Replay | ankommende Bewegung und aktualisierte Simulationskandidaten |
| Aktion | geplantes Ziel/Slot/Fläche, Swing oder Release/Use | tatsächliche C0A/C02/C07/C08/C09-Reihenfolge | Action-Snapshot, Queue, Kandidaten, Check-/Mitigation-Zustand |
| Bestätigung | lokale Animation und Controller-Rückgabe | Serverantworten, Position-/Inventarupdates | tatsächlich angenommener Treffer, Block oder Korrektur |

Client-Trace um `runId`, Sequenz, Outer-Tick, StepIndex (bis StepContext vorhanden ist experimentell aus dem Test-Harness), Monotonic-Nanos und Connection-Generation erweitern. Für jedes Paket **enqueue, gehalten, barrier-ausgelöst, transportiert, verworfen** mit Ursache und Position im FIFO getrennt erfassen. Auf dem Server, sofern verfügbar, Packet-Receive, Check-Part, bestätigten Treffer/Block, Violation und Mitigation festhalten. Uhrendrift über gemeinsame Ereignisse korrigieren; künstlich exakte Zeitabstände zwischen Hosts nicht behaupten.

Kritischer Umsetzungsrahmen: Pulsars `PacketPipeline` erzwingt vor jeder ausgehenden Nicht-C03-Aktion eine Barriere. `PacketDelays` ist FIFO pro Richtung; nach einem gehaltenen Paket können auch nicht direkt passende Folgepakete in der Queue warten. Mehrere Owner halten ein Entry gemeinsam; Lease-Ablauf, maximale Dauer und Aktionen können Freigabe auslösen. Ein Konzept mit unabhängigem parallelem Queue-Store oder einer angeblichen garantierten Aktion vor früherem Movement ist mit dem Ist-Vertrag nicht kompatibel.

## 2. Verbesserte Rotations- und Scaffold-Konzepte

### R4 — Rotation und diskrete Eingabe gemeinsam planen

**Hypothese:** Ein präziser kameraraumerhaltender MoveFix-Vektor kann bei bestimmten relativen Yaws/Inputs Bewegungsbeschleunigung ergeben, die in der 1.8-Tastensuche schlechter repräsentierbar ist als ein diskreter, konsistenter Kandidat. SIMPLE ist nicht automatisch Vanilla-konform: Eingabestärke, Diagonalskalierung, Sprintbedingung, Sprungimpuls und Vorrotation müssen ebenfalls passen.

**Konzept:** Neben aktueller PRECISE-/SIMPLE-/ROTATION_BOUND-Kontrolle ein Forschungskandidat, der zulässige diskrete Richtungen sowie vorhandene langsame Eingabemodifikatoren durch die tatsächlich verwendete 1.8-Movement-Funktion simuliert. Er bewertet Kandidaten nach Abweichung vom gewünschten Weltvektor und sichtbarer Steuerbarkeit; bei Sprung wird der Sprintimpuls gesondert berücksichtigt. Nur mit tatsächlich lokal angenommenem Rotationsowner und korrektem Phasenzeitpunkt. Keine Behauptung, dass Nachbau einzelner Intave-Verzweigungen vollständige Servervorhersage ersetzt.

**Prüfung:** alle acht Richtungen plus Stillstand, Kamera/Owner-Winkel in mehreren relativen Quadranten, Sprint an/aus, Boden/Luft/Sprung, normale/item-/sneak-verlangsamte Eingabe, Yaw-Wrap und Verzögerung von Look/Position. Pro Fall sichtbare Richtung und Geschwindigkeit, C03 und `Physics`-Debug/Setback aufzeichnen. Nullhypothese: kein Unterschied in serverseitigem Physics-Ergebnis; Ablehnungsgrund auch dann berichten, wenn das Bewegungsgefühl schlechter ist. Aktuell beschlossener Scaffold-Default PRECISE für neue Profile wird nicht still geändert.

### R5 — Sensitivity als Sequenz- statt Einzelwertproblem

Pulsars Restwinkel kann die geschätzte GCD-Folge beeinflussen. Präzisierung der ersten Analyse: Die untersuchte Intave-Heuristik erhöht ihren internen Wert nur unter weiteren Bedingungen, darunter eine hinreichend große Pitchänderung; ein einzelner Substep muss diese Bedingung nicht selbst erfüllen. Ob ein kleiner Rest einen späteren Anstieg verursacht, ist ein Sequenzexperiment, keine unmittelbare Folgerung.

**Versuch:** Ist vs. Rundung vs. Residual-Akkumulator mit identischen Anfangs-/Zielwinkeln und Sensitivity. Nach jedem lokal akzeptierten C03 tatsächlichen ΔYaw/ΔPitch, Rest, Zielwechsel und Quantisierungsfehler erfassen. Replay mit bewegtem Ziel, kürzlichen Attacken und Teleport-Ausschlussphase sowie getrenntem Kalibrier-/Holdout-Seed. Finale Aura-/Scaffold-Raycasts und Reaktionszeit sind Co-Primärmetriken. Einen Kandidaten verwerfen, wenn seine künstliche Regelmäßigkeit, Zielverfehlung oder Pitch-Clamp-Oszillation zunimmt.

### S1 — Zwei-Phasen-Placement mit messbarer Packet-Timeline

**Konzept:** `plan → accepted pose → validated action → confirmation` als klarer Planzyklus. In beiden Platzierungsmodi nach der jeweiligen Queue-Barriere Block/Seite/Slot und effektive Pose erneut prüfen. Verglichen werden bestehendes PRE (vorheriger akzeptierter Look) und POST (aktueller akzeptierter Look), plus nur im Harness eine terminierte Variante mit unverändert gültigem Plan. Der Serverabstand zwischen Movement-Receive und C08 wird aufgezeichnet. C08-Platzierungsvektor lokal in Blockkoordinaten und tatsächlich transportiertes Paket vergleichen.

Intaves [Facing](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/world/placementanalysis/Facing.java) prüft u. a. den Wertebereich des Platzierungsvektors; `PacketOrder` nutzt Zeitfolgen; `RotationSpeed`, `BlockRotation` und `SharpRotation` verwenden andere Bedingungen. Die lokale Forderung in Pulsar, dass der reale Strahlpunkt höchstens 0,05 Blöcke vom Planpunkt entfernt liegt, ist zusätzlich. **H:** Ein realer, zur Face passender Zielpunkt mit kontinuierlichem Owner kann Fehlversuche und Rotationsarbeit reduzieren. Test: gerade/diagonal, Kante, langsamer/hoher Durchsatz, Sneak, Jump, Inventory-Switch, Timer und Netz-Jitter. Auch Richtung, Blöcke/min und Stürze messen, um ein scheinbar ruhigeres Profil nicht durch unspielbar langsames Platzieren zu erkaufen.

## 3. Verbessertes Combat-/Item-Konzept

### A4 — Klickgelegenheiten und tatsächlich gesendete Pakete

Ersetze in der Forschungsbewertung „gewählte CPS“ durch drei Zeitreihen: generierte Klickgelegenheit, versendeter C0A und akzeptierter C02. Pulsars Scheduler samplet CPS, rechnet in Millisekunden um und erlaubt pro äußerem Tick höchstens einen Controller-Angriff; der reale Ablauf ist durch Motion-Frequenz, Zielgültigkeit, C0A-Barriere und spätere Controller-Gates diskretisiert. Intaves `ClickSpeedLimiter` verwendet bei ≤1.8 eine Attack-Zählung in einem gleitenden Array; `ClickPatterns` wertet hingegen Swing-Folgen aus. Das ist kein einzelner gemeinsamer CPS-Zähler.

**Neue Varianten:** `opportunity-driven` erzeugt nur bei gültigem Ziel und aktivem Kontext einen Terminvorschlag; `empirical intervals` replayt eine getrennte, freiwillig erstellte Input-Verteilung; `stateful intervals` hat langsame Sitzungsdrift und begrenzte kurzfristige Abweichung. Der Mechanismus soll keine nachträgliche Paketflut erzeugen: Bei ungültigem Ziel verfallene Termine verwerfen, nicht als Rückstand ansammeln. **Kausaltest:** Ziel verschwindet zwischen Swing und C02, Queue-Flush verändert Pose, Manual-Attack im gleichen Tick, AutoBlock-Release, Delays. Erfolgsmaße: echte Hits/sek, Leerschwünge, Abstände, Angriffslatenz und serverseitige Mitigations; keine garantierte „menschliche“ Verteilung.

### A5 — ItemUse-FSM als Voraussetzung für AutoBlock/NoSlow

**Modell:** `IDLE`, `USE_PENDING`, `USING`, `RELEASE_PENDING`, `ATTACK_ELIGIBLE`, `RESUME_PENDING`, `UNKNOWN`. Requests von Aura, NoSlow, physischem Use und Slotwechsel werden pro Kontext/Sequenz zusammengeführt; ein Transport-/Queue-Ereignis ersetzt keine Bestätigung durch den Server. Bei widersprüchlichem oder verspätetem Ereignis auf `UNKNOWN`, keine neuen synthetischen Aktionen, Reset bei Disconnect/World/Death. Das ist ein vorgeschlagenes gemeinsames Konzept, noch kein bestehender Service.

**Vergleichshypothesen:** (1) kein synthetisches Blocking; (2) dauerhaftes, durch Nutzer eingeleitetes Blocking; (3) Angriff nach sauberem Release und Re-Use erst in einer späteren, beobachteten Phase; (4) einmaliger Legacy-Slotwechsel als gesonderter Test der Zustandsmehrdeutigkeit. Protokolliere je Bewegungsgrenze aktives Item, echten/gesendeten Slot, Use-Dauer und Sprint. Ein zeitweiser Bewegungsvorteil bei unterbrochener Nahrungs-/Bogenaktion ist kein voller NoSlow-Erfolg. Mehrfaches schnelles Re-Use im selben Intervall kann zusätzlich `BlockingHeuristic` betreffen; der Slotbranch allein sagt darüber nichts aus.

## 4. Verbesserte Movement-Konzepte

### M4 — Kandidatenorientierte Simulationsdifferenz

Intaves [RedoSimulationSearch](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/physics/search/RedoSimulationSearch.java) kann bei hinreichender Abweichung bzw. Evaluatorbefund eine genauere Suche wiederholen. Deswegen eignet sich ein vermeintlicher einzelner Fehler-Schwellenwert nicht als Speed-Konstante.

**Versuch:** Auf lokaler Clientseite Vanilla-Next-Step aus identischem Vorzustand berechnen; Ist-Intave-Speed, ungeänderter Vanilla-Sprintjump und experimentelle kleine Abweichung gegen diese Referenz vergleichen. Differenzvektor, gewählten Input und physikalischen Zustand dokumentieren; serverseitige Simulator-/Violation-Daten nur aus zugänglichem Labor-Debug. Ein lokales Modell ist ein Diagnoseinstrument, keine Kopie des vollständigen Intave-Modells. Auf Nutzwert bewerten: bestätigte Blockdistanz/Zeit nach Setbacks und gleichbleibende Steuerbarkeit. So lässt sich herausfinden, ob ein beobachteter Geschwindigkeitsgewinn real ist und an welche Zustände er gebunden ist.

### M5 — Übergänge statt pauschaler Fly-Aussage

Einzelne vorher definierte Szenarien: Sprung → Kollision → Fall, legitimer externer Velocity-Impuls → Landung, wechselnde Blockkollision und Tower-Platzierung. Für jede Hypothese erwartete Vorbedingung und Endzustand festhalten und nach dem Ereignis zeitlich begrenzte vs. anhaltende Effekte auswerten. Für einen Fly-Anspruch braucht es wiederholbaren serverbestätigten Höhen-/Distanzgewinn ohne externe Flugberechtigung. Kein entsprechender Effekt wurde hier nachgewiesen.

## 5. Verbesserte Netzwerk-Konzepte

### N4 — Backtrack mit Positionsalter und Feedback-Hypothese

**Konzept:** Vor jeder geplanten Attacke target ID, letzte lokal angewendete S14, letzte empfangene Zielposition, Queue-Alter und relative Bewegung festhalten. Experimentelle Verzögerung deaktivieren, wenn Kontext, Zielidentität, Teleport-/Destroy-/Velocity-Ereignis oder Zeitfenster ungültig werden. Intave-seitig bei verfügbarer Telemetrie zusätzlich Pending-Feedbacks, Raytrace-Timeout, Zwischen-/Endentscheidung und tatsächlich erzielten Schaden messen. Vergleich kontrollierter Annäherung, Entfernung und Richtungswechsel.

**H:** Ein begrenztes Positionsalter kann unter einzelnen Latenz- und Zielbewegungsbedingungen einen anderen lokalen Trefferzeitpunkt ergeben. Die Positionen auf beiden Maschinen sind aber nicht unmittelbar dasselbe Koordinaten-/Zeitabbild. Ein Treffer am Client oder `AttackResultEvent` allein belegt keinen Server-Hit. Auch nicht vermuten, dass absichtlich verzögertes S14 die serverseitigen Feedbackzähler notwendigerweise identisch verändert.

### N5 — Tickbase als zeitliche Verlagerung mit Gesamtbilanz

`PlayerTime` und `MicroBlink` erfassen unterschiedliche Aspekte. Deshalb jedes Experiment mit `Simulation Only` als Placebo, bestehendem Guthabenmodell und einer zeitlich verteilten Freigabe vergleichen. Je äußerem Tick Player-Step-Anzahl, Bewegungsfrequenz auf dem Server, Feedback-Stand, tatsächliche Angriffszeit, Treffer, Timer-Violation und ggf. dessen nachfolgende Attack-Mitigation erfassen. Eine zeitlich ausgeglichene Gesamtbilanz kann trotzdem kurzfristig zu häufige Pakete oder auffällige Kampfkorrelation verursachen.

**H:** Eine Verlagerung tatsächlich eingesparter Schritte kann in einzelnen Situationen das Eintreffen einer Aktion beeinflussen. Erfolg setzt voraus, dass der serverseitig bestätigte Vorteil die Kosten durch vorherige Stillstandszeit übersteigt; dauerhaft höhere Durchschnittsgeschwindigkeit folgt daraus nicht. `StepContext` und per-Outer-Tick-Actionbudget bleiben Architekturvoraussetzung für Modulkombinationen.

### N6 — LagRange mit expliziter Barrieren-Auswertung

Getrennte Gegenproben: (a) kein Delay, (b) nur eingehendes Ziel-Movement, (c) ausgehendes Movement, (d) beide Richtungen. Bei (c)/(d) vor C0A, C02 und C08 beobachten, welche zuvor gehaltenen C03 durch die Pipeline freigegeben werden; danach effektive Position/Rotation und finale Aktion prüfen. Ein Forschungskandidat kann bei ausstehendem Movement eine Aktion bewusst aussetzen, um den Effekt erzwungener Freigabe zu messen. **H:** Eine reduzierte Korrelation zwischen Delay-Fenster und Kampfsituation kann bestimmte beobachtbare Muster beeinflussen; allein daraus folgt kein Bypass oder garantierter Vorteil. `MicroBlink` sammelt Zustände über Zeit und prüft mehrere Bedingungen; kurze Einzelbeispiele reichen nicht.

## 6. Disabler: gezieltere Suchstrategie

Ziel der Analyse ist eine Abhängigkeitskarte je Checkfamilie: Eingangsereignisse → getrackte Metadaten → Feedback/Transition → tatsächliche Entscheidung und Rücksetzpfad. Priorisieren: (1) Legacy-Item-/Slotwechsel, (2) Bewegungspaket-/Feedback-Reihenfolge, (3) Join/Teleport-/Respawn-Wechsel und (4) versionsübersetzte Aktionen. Diese Auswahl benennt beobachtete Zustandskomplexität, keine bekannte Lücke.

Pro Kandidat muss ein Testserver zuerst eine **positive Kontrollreaktion** von Physics, Timer, Combat-Raytrace, Combat-Heuristik und Placement nachweisen. Danach genau eine Transition variieren und identische Kontrollen innerhalb und außerhalb des vermuteten Zustandsfensters wiederholen. Je Familie: Check aktiviert, Eingangsereignis empfangen, Auswertung sichtbar, Violation/Mitigation, Wiederherstellung nach Reset. Fehlende Cloud-Telemetrie bleibt unbekannt. Ein partieller oder kurzlebiger Effekt ist entsprechend zu benennen; ohne vollständige reproduzierte Evidenz gibt es keinen Full Disabler.

## 7. Präzise nächste Testpakete

| ID | Szenario | Primärer Erkenntnisgewinn | Mindestnachweis |
|---|---|---|---|
| T1 | MoveFix OFF/SIMPLE/PRECISE/ROTATION_BOUND × 8 Inputs × Sprint/Jump | lokale Richtung vs. diskrete 1.8-Inputrekonstruktion | je Fall gleiche Szene, C03-Pose und Physics-Ergebnis |
| T2 | Aura mit/ohne PacketDelay, Zielverlust kurz vor Aktion | C0A ohne C02, Flush und erneut gültiger Raycast | vollständige Reihenfolge und Anzahl der serverbestätigten Hits |
| T3 | Scaffold PRE vs. POST und Owner-Hold | C03→C08-Abstand, Ownerlücken, Placement-Erfolg | Zeitreihe auf beiden Seiten und bestätigte Blöcke/Stürze |
| T4 | Schwert/Consumable/Bogen × AutoBlock/NoSlow | Itemzustand, Sprint und Use-/Release-Konflikte | Wirkung des Items und Bewegung zugleich |
| T5 | Sensitivity und Substep mit bewegtem Ziel | GCD-Historie statt einzelner Winkel | gesendete Look-Deltas, Attackkontext und Checkausgabe |
| T6 | Backtrack bei Annäherung/Entfernung, gleicher Ping | Nutzen vs. alte Entity-/Feedbacksicht | received/applied/Server-Hit, Feedback soweit verfügbar |
| T7 | Tickbase/LagRange mit und ohne Combat | PlayerTime, MicroBlink, Barrier-Bursts | tatsächliche Serverankunft und Angriffsmitigation |
| T8 | kontrollierte Join-/Teleport-/Slot-Transition | Zustandssynchronisation und mögliche Teilbypässe | Check-Positivkontrollen vor, während und nach Transition |

Testdauer, Versions-/Config-Matrix, Seed, erfasste Check-Kanäle und Ausschlusskriterien **vor** jedem Lauf fixieren. Vorhandene Unit-/Integrationstests decken einzelne Clientverträge ab; sie wurden in dieser Runde nicht ausgeführt und ersetzen keinen Intave-Test.
