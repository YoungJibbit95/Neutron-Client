# Neutron 2.0 — AC-R01 Research Register

Revision 1.0 · 28.10.2026 · Research und Testplanung. Keine Gameplay-Freigabe. Zuständiger Einstieg: [Übergabe](AC_DEVELOPMENT_HANDOFF.md), [Testprotokoll](AC_TEST_PROTOCOL.md), [offene Fragen](AC_OPEN_QUESTIONS.md).

## Geprüfte Basis

| Referenz | Lokal verifiziert | Bedeutung |
|---|---|---|
| B0 | `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`, `F:/Puslar Backup - Copy`, Branch `prep/neutron-opencode-wave00` | Ausgecheckte Research-/Gameplay-Basis aller drei Altberichte |
| B1a | Implementierung `14d9ab4b39945510c2d6eddbe75230122a393ba6`, Abschluss `eae42f43757c344784efdb53492c06eaf34f39ba` | CORE-I01a: reine Scene-/Step-/Eligibility-Verträge |
| B1b | Implementierung `d059f827ed7120f64e92392b51d1fe5c5d3f38bd`, Abschluss `6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666`, `F:/neutron-core-i01b` | CORE-I01b: Beobachtung tatsächlich betretener Player-Bodies und aktueller Scene |
| I01c | `F:/neutron-core-i01c` auf B1b; bei Erfassung uncommittete Änderungen an Consumer-/Admission-Dateien und Tests | Laufende Arbeit, keine abgeschlossene Referenz oder bestandene Abnahme in AC-R01 |

`git log --all`, `git worktree list`, Commit-Diff und direkte Codelektüre bestätigen die Verfügbarkeit. B1a ist Vorfahr von B1b; B1b ist kein Vorfahr des Research-HEAD B0. Deshalb gelten API-Aussagen zu B1b nicht automatisch für den Research-Checkout. Zwischen B0 und B1b sind RotationManager, Aura, Scaffold, PacketPipeline und PacketDelays unverändert; Tickbase erhielt den Beobachtungswrapper. Kein Checkout/Merge vorgenommen.

Die universelle Spezifikation v1.0 und das vollständige externe RV-Paket liegen nicht als eigenständige Quelle in den geprüften Projekt-Designverzeichnissen vor. Ihre Wiedergabe in `CONTRACT_DECISIONS.md` und `HOOK_CONTRACT.md` wurde gelesen; das ersetzt keine unabhängige Prüfung des Originalpakets. Daraus entsteht keine globale Entwicklungsblockade.

## Evidenzmodell

- **Q:** Quellcode an benannter Version selbst gelesen; statischer Ablauf, kein Laufzeitnachweis.
- **T-code:** vorhandenen Testcode gelesen, in AC-R01 nicht ausgeführt.
- **T-report:** Ergebnis eines anderen Auftrags mit Version und Bericht; kein eigener Testlauf.
- **D:** externe Dokumentation, begrenzt auf deren Aussage und Version.
- **H:** technische Hypothese mit möglichem Widerlegungsfall.
- **U:** ungetestete Idee oder fehlende Quelle/Messung.

Kein Registereintrag hat einen Anticheat-Live-Nachweis. Entwicklungspriorität P1 bedeutet zuerst begrenzt untersuchen, keine automatische Produktionsfreigabe.

## Quellenkatalog

| Kürzel | Genaue Quelle | Verwendbarer Umfang |
|---|---|---|
| INT | [Intave b950bdf7570b8624b7907e2c42dfb3b6d63c40f6](https://github.com/intave/intave/tree/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6) | Gelesene Checks/Brancher, keine Annahme über aktive Zielserver-Config oder Cloud-Modelle |
| GRI | [Grim 2.0, 1299a2aa35b2138df2604f9c7d6bcae357afef31](https://github.com/GrimAnticheat/Grim/tree/1299a2aa35b2138df2604f9c7d6bcae357afef31) | Ausgewählte Placement-Checks; keine getestete Serverkonfiguration |
| POLLOG | [PolarLogs e02b9e78769dd403ac8d42f720a1fa53687d48b7, README](https://github.com/ShortyDev/PolarLogs/blob/e02b9e78769dd403ac8d42f720a1fa53687d48b7/README.md) | Serverseitige Diagnostik, Berechtigungen, MySQL, standardmäßig deaktivierte Mitigation-Speicherung; keine Polar-Check-Implementierung |
| LB | [LiquidBounce Legacy 03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e](https://github.com/CCBlueX/LiquidBounce/tree/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e) | Historischer Implementierungsvergleich, kein Intave-Kompatibilitätsbeleg |
| NCP | Ursprünglich genannter Updated-NoCheatPlus-Link | Kein gepinnter Quellstand und keine Config konsolidiert; keine NCP-spezifische Schlussfolgerung oder Freigabe |

Intave ist source-available; geprüftes [LICENSE.md](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/LICENSE.md) nennt PolyForm Perimeter 1.0.0. AC-R01 übernimmt keinen fremden Code. Jede spätere Übernahme braucht gesonderte Klärung der konkreten Lizenzrechte; Gleiches gilt für Grim/LiquidBounce/NCP. Verlinkte Analyse ist keine pauschale Lizenzfreigabe.

## Konsolidierung der drei Altberichte

Alle drei Dateien vollständig gelesen: `../research-rotation-aura-scaffold-2026-10-28.md`, `../intave-bypass-research-plan-2026-10-28.md`, `../intave-research-round-2-2026-10-28.md`. Diese bleiben historische B0-Unterlagen. Die folgenden Korrekturen gelten für jede Weiterverwendung:

| Bisherige Aussage/Skizze | AC-R01-Bewertung |
|---|---|
| Ein neuer `StepContext` sei Voraussetzung | Durch B1a/B1b konkretisiert: `ExecutedStep` ORDINARY/EARNED_EXTRA plus eigenständige `WithheldObservation`, `SceneContext`, `RuntimeObservation`; keinen zweiten Kontext bauen |
| Außenwirkungen pro Outer-Tick deduplizieren / globales ActionGate | RV-02: `AutomationEligibility.mayAttempt` ist eine notwendige Vorprüfung ausschließlich für automatische Modulaktionen, keine Domain-Reservation. Budgets/Controller bleiben. Keine pauschale Sperre manueller Aktionen |
| WITHHELD als simulierter Step | Falsch: kein Body, kein ExecutedStep, kein künstliches Begin/End; nur separate Beobachtung bei normalem Decision-Return ohne Callback |
| Beobachter bestätigt eine Aktion oder Rotation | Falsch: I01b beobachtet; `currentStep` ist während des Bodys vorläufig, `lastSteps` sind Diagnosewerte. Phasen-/Owner-/Raycast-/Slot-/Budgetprüfung bleibt zusätzlich |
| Vier G-CMB-SPEED-Werte beträfen auch Movement-Basis | RV-08 trennt vier Rotationsgeschwindigkeiten von `G-MOV-SPEEDBASE` (Basis/Potion/Legacy). Acht Scaffold-Profile: `G-SCF-ANGLES`; unverändert offene numerische Gates |
| Scaffold-Owner-/Timer-/Input-Probleme bereits erledigt | Durch B1b nicht geändert. Keine pauschale Timer-/Input-/Rotation-Migration behaupten |
| Sub-GCD-Ausnahme sei durch den benannten RotationTrajectory-Test ausdrücklich abgesichert | Zu weitgehend: gelesener Test ruft `RotationTrajectory.advance`, nicht `RotationManager.quantize`, auf. Q für Ausnahme; T-code für Trajektorie; direkter Quantisierungs-/Transportnachweis bleibt offen |
| PRECISE erzeuge automatisch Intave-Flags; SIMPLE sei garantiert passend | Unbelegt. Kameraraumerhaltende Mathematik und serverseitige Inputrekonstruktion sind verschiedene Fragen; SIMPLE erhält Stärke und hat eigene Sprungbasis |
| CPS 6/8/10/12, Delay 0/50/100/150 seien sichere Einstellungen | Ausschließlich alte Laborideen U; keine Produktdefaults oder aus Checks ableitbare universelle Grenzen |
| `Constraint`, `RoundedRotation`, `EqualDelay` oder deprecated Timer-Klassen bewiesen aktive Erkennung | Nicht aus Klassenname/Existenz ableitbar: manche Verdachtszweige leer/auskommentiert, Registrierung und Konfiguration separat prüfen |
| Full Disabler/Fly aus Übergangsmehrdeutigkeit | U; keine nachgewiesene Methode. Kein Implementierungspaket in AC-R01 |

## Priorisierte Forschungsaufträge

Für alle Einträge gelten B0/B1b, Quelle wie angegeben und [Protokoll](AC_TEST_PROTOCOL.md). Schnittstellenstatus V = an B1b vorhanden, P = vorgeschlagene Erweiterung bestehender Dienste, nicht implementiert. Test-IDs stehen im Protokoll. Abschluss einer Untersuchung erlaubt nur eine eng begrenzte Fachänderung nach den angegebenen Kriterien.

### AC-CORE-DIAG-001 · P1 · Diagnose/Step-Lifecycle · CORE + NET, VIS lesend

- **Version/Evidenz:** B1a/B1b Q, `RuntimeObservationHookTest` und `AutomationEligibilityTest` T-code; I01b-RESULTS T-report: 446 Tests, 0 Failures/Errors, 3 Skips. Keine eigene Ausführung.
- **Ist/H:** ein kanonischer Observer, vor Bootstrap null, keine Observer-Fallbackinstanz. Scene bindet Identitäten, Generation und Packet-Epoch. H: Trace-Zuordnung kann Withheld, Ordinary, Extra und Abbruch zuverlässig unterscheiden.
- **Aufbau/Widerlegung:** T-01, echte Hook-Reihenfolge mit Withhold, Extra, GUI/Resume, Epochwechsel und Exception; falscher Step oder altes Scene-Label widerlegt die Zuordnung.
- **API/Architektur:** V `RuntimeServices.observation`, `captureScene`, `getCurrentStep`, `getLastSteps`, `getLastWithheld`; P begrenzter Exportadapter. Observer erzeugt keine Tick-Historie; Reads können frische Scene-Erfassung auslösen. Keine Gameplay- oder Lifecycle-Steuerung durch Trace.
- **Freigabekriterium:** tatsächliche Body-Anzahl und Ereignisfolge bleiben mit/ohne Diagnose gleich; aktive/historische Werte korrekt beschriftet, kein behaltenes Scene-Objekt, keine globale Aktionssperre.

### AC-INT-ROT-001 · P1 · Rotation/Sensitivität · CMB, CORE unterstützend

- **Version/Evidenz:** B0/B1b `RotationManager.quantize` Q; INT `RotationSensitivityHeuristic` Q. Trajektorientests T-code, direkter Test des letzten Quantisierungsrests nicht belegt.
- **Ist/H:** Sub-GCD-Rest wird direkt übernommen. H: komplette Delta-Sequenzen unterscheiden sich zwischen Ist und durchgängiger Rasterbehandlung; eine Intave-Erkennung folgt nicht schon aus einem kleinen Rest.
- **Aufbau/Widerlegung:** T-02, identische Sensitivity und bewegtes Ziel, gleiche Vorrotation/Seed; transportierte Deltas und Raycast erfassen. Gleiche Check-/Zielergebnisse oder schlechtere Konvergenz widerlegen den behaupteten Nutzen.
- **API/Architektur:** V RotationManager/RotationTrajectory; P reine Messwerte daraus. Bestehenden Owner und lokalen Annahmepfad nutzen; keine zweite Rotation. G-CMB-SPEED bleibt eigenständig.
- **Freigabekriterium:** direkte numerische und Paketpfad-Regression, Konvergenz/Pitch-Clamp/Wrap ohne Regression, versionierter Serververgleich; keine unbewiesene CPS-/Noise-/Sensitivitätsfreigabe.

### AC-INT-SCF-001 · P1 · Scaffold/Keep Rotations · SCF + CMB, CORE Vertrag

- **Version/Evidenz:** B0/B1b `releasePlacement`, `tryPlace`, `RotationManager` Q; I01a-Vertragsentscheidungen Q/D-lokal; `ScaffoldNetworkIntegrationTest` T-code.
- **Ist/H:** Erfolg oder Planverlust gibt Owner frei; C4/M5/D3 verlangt getrennte Plan-/Owner-Laufzeit. PRE nutzt vorherige akzeptierte Pose, POST aktuelle. H: ein fachlich korrekt gehaltener Owner verringert unnötige Wechsel; keine Flag-Evidenz.
- **Aufbau/Widerlegung:** T-03, PRE/POST, zwei aufeinanderfolgende Pläne, Lücke, Aura-Konkurrenz, Delay/Rewrite, Disable und Resume. Fehlender Nutzen oder stale Aktion widerlegt die Verbesserung.
- **API/Architektur:** V Request/resolve/finishMotion, Placement-Gates und B1b-Scene; P getrennte Release-Verantwortung im bestehenden Modul. Positive Eligibility ersetzt keinen bestätigten Owner/Slot/Raycast.
- **Freigabekriterium:** keine stale/Extra-Automation, korrekte PRE-/POST-Pose, manuelle Aktionen erhalten; gleiche oder bessere bestätigte Platzierung, keine ungeklärten Defaults.

### AC-INT-CMB-001 · P1 · Attack/Swing/CPS · CMB + NET, MOD Controls

- **Version/Evidenz:** B0/B1b Aura→Swing→Controller, `AttackScheduler`, `PacketPipeline` Q; INT `ClickSpeedLimiter`, `ClickPatterns`, `PacketOrderSwingHeuristic` Q; `AttackHookTest` T-code.
- **Ist/H:** C0A kann vor später verworfenem C02 gesendet werden; Nicht-C03 löst Outbound-Barriere aus. H: gewählte CPS, versendete Swings und Server-Hits weichen systematisch voneinander ab.
- **Aufbau/Widerlegung:** T-04, Zielverlust zwischen C0A/C02, Manual-Attack im selben Tick, Queue-Flush und Itemwechsel; gleiche vier Zeitreihen bzw. fehlender Nutzen eines Scheduler-Kandidaten widerlegen dessen Vorteil.
- **API/Architektur:** V Scheduler/Controller/PacketPipeline; Eligibility V als reine API, Fachanbindung an B1b ausstehend; P Trace der Klickgelegenheit. Kein globales Budget oder Vanilla-Interceptor.
- **Freigabekriterium:** klare Gelegenheit/Swing/Attack/Serverwirkung-Zuordnung, vorhandene Budgets bleiben maßgeblich; keine Rückstandsflut, keine manuelle Regression, reproduzierbare Vergleichsdaten.

### AC-INT-CMB-002 · P2 · Item-Use/Autoblock/NoSlow · CMB + MOV + MOD, NET

- **Version/Evidenz:** B0/B1b `AutoblockController`, NoSlow C07/C08/C09 Q; INT `BlockingHeuristic`, `UseItemBrancher.branchLegacy` Q.
- **Ist/H:** getrennte Writer; Legacy-Slotwechsel ist eine begrenzte Mehrdeutigkeit, kein Dauer-NoSlow-Beleg. H: eine abgestimmte Folge reduziert widersprüchliche Zustände.
- **Aufbau/Widerlegung:** T-05 für Schwert/Essen/Trank/Bogen mit Slotwechsel, GUI/Resume und Delay. Unterbrochener Verbrauch oder kein realer Bewegungs-/Blocknutzen widerlegt Erfolg.
- **API/Architektur:** V Controller/Slot-Sync/Pipeline; P gemeinsam abgestimmter Zustandsvertrag innerhalb vorhandener Verantwortlichkeiten. Kein neuer Packet- oder Input-Store.
- **Freigabekriterium:** Itemwirkung und Bewegung gleichzeitig bestätigt; keine doppelten Use-Writer, Kontext-/Hand-/Slotvalidierung und physischer Nutzer-Use bleiben korrekt. D7/D8 nicht still ändern.

### AC-INT-MOV-001 · P2 · MoveFix/Speed/Sprint/Jump/Velocity/Kollision · MOV + CMB

- **Version/Evidenz:** B0/B1b `MoveFixController`, Speed-Basis/Impuls Q; INT `KeypressBrancher`, Physics Q; MoveFix-Verifikation T-report und numerische Testklassen T-code.
- **Ist/H:** PRECISE transformiert kontinuierlich; SIMPLE ist eine Richtungsschätzung mit Stärkeerhalt; Sprintimpuls hat eigene Basis. H: einige Kombinationen passen verschieden gut zu Intaves Kandidatensuche.
- **Aufbau/Widerlegung:** T-06 acht Inputs plus Stillstand, vier MoveFix-Modi, Boden/Luft, Sprint/Jump, Slowdown/Potions, kontrollierter Velocity-Impuls und Kollision. Kein Physics-Unterschied oder schlechtere Steuerbarkeit widerlegt den behaupteten Vorteil.
- **API/Architektur:** V tatsächliche MoveFlying-/Jump-Consumer; Input-Snapshot/TimerArbiter weiter P. G-MOV-SPEEDBASE getrennt von G-CMB-SPEED. Kein Server-Simulator als paralleles Produkt-Movement-System.
- **Freigabekriterium:** Client-Mathematik und Serverabweichung separat belegt; kein numerischer Default ohne fachliches Gate; netto serverbestätigte Strecke nach Korrekturen messen. Fly bleibt U.

### AC-INT-NET-001 · P2 · Backtrack/LagRange/Tickbase · NET + CMB/SCF

- **Version/Evidenz:** B0/B1b PacketDelays FIFO/Leases, Pipeline-Barrieren, TickbaseBudget und Backtrack Q; INT `PlayerTime`, `MicroBlink`, `AttackRaytrace` Q; Queue-/Scaffold-/Observer-Tests T-code.
- **Ist/H:** fremde Holds, Lease-Ablauf und Aktionen können Release beeinflussen. Withheld ist keine Simulation; Extra wird beobachtet, automatische Eligibility in B1a verneint Extra. H: Delay-/Step-Muster ändern Nutzen und Check-Korrelation; lokale Zielalterung ist keine Server-Reach-Garantie.
- **Aufbau/Widerlegung:** T-07 getrennt inbound/outbound/beide/off; Withhold→Ordinary+Extra, Zielwechsel/Correction, mehrere Owner; Server-Hits/Strecke netto, Ankunftsmuster und folgende Mitigation messen. Mehr lokale Animationen ohne Servernutzen widerlegen Erfolg.
- **API/Architektur:** V PacketPipeline/PacketDelays/Observer; P begrenzter Trace, spätere fachliche Eligibility-Anbindung. Keine neue Queue, kein Credit aus Withheld als ExecutedStep, keine automatische Aktion auf Extra.
- **Freigabekriterium:** FIFO, Barrieren und Invalidierung erhalten; aktuelle Scene/Phase zusätzlich prüfen; messbarer Nutzen ohne neue stale Aktionen. Live-Checkdaten nur für dokumentierten Build/Config gültig.

### AC-GRI-SCF-001 · P2 · Grim-Platzierungsgeometrie · SCF + NET

- **Version/Evidenz:** GRI `RotationPlace` Q; B0/B1b enger lokaler Punktabstand Q. Grim-Serverconfig und Testlauf fehlen.
- **Ist/H:** Grim untersucht mehrere mögliche Augen-/Blickzustände; Pulsars lokaler geometrischer Gate ist nicht identisch. H: einige lokale Ablehnungen sind strenger als ein erreichbarer Flächentreffer, ohne dass Intave/Polar daraus folgen.
- **Aufbau/Widerlegung:** T-03 ausschließlich mit Grim-Build/Config und Kanten-/Teilblockfällen; gleiche Ablehnungen oder serverseitige Ablehnung widerlegen einen Zuverlässigkeitsgewinn.
- **API/Architektur:** V PlacementService und angenommene Pose; P Vergleichstrace, kein Grim-Codeimport.
- **Freigabekriterium:** richtige Seite, Range und Serverblockbestätigung erhalten; getrennte Grim-Abnahme, keine Änderung der Punktbindung allein aufgrund der fremden Check-Toleranz.

### AC-GRI-ROT-001 · P3 · Grim-Rotationsfolgen · CMB + SCF

- **Version/Evidenz:** GRI `DuplicateRotPlace` Q, `experimental=true`; keine installierte Config.
- **Ist/H:** gleicher Delta-Vergleich in diesem Check. H: aktuelle Trajektorien können in einzelnen Fällen Wiederholungen erzeugen; Aktivierung und tatsächliche Flags unbekannt.
- **Aufbau/Widerlegung:** T-02/T-03 mit experimentellem Check an/aus und Positivkontrolle, sonst gleicher Build. Fehlende Reaktion bei aktiver und belegter Exposition widerlegt die konkrete Flag-Hypothese für diesen Lauf.
- **API/Architektur:** V RotationManager; P Sequenzexport. Keine Übertragung auf Intave oder Polar.
- **Freigabekriterium:** Check-Aktivierung, transportierte Deltas und Serverereignisse korrelierbar; keine neue Randomisierung ohne nachgewiesenen Bedarf.

### AC-POL-DIAG-001 · P1 · Polar-Logging-Abdeckung · NET + CORE, VIS lesend

- **Version/Evidenz:** POLLOG README D, gepinnter Inhalt erneut gelesen. Polar selbst: Build/Config/innere Checks unbekannt.
- **Ist/H:** kontextbezogene Logs/Export mit Berechtigungen; Mitigation-Speicherung standardmäßig aus. H: fehlende Meldung kann unvollständige Beobachtung sein.
- **Aufbau/Widerlegung:** T-08, eigener Polar-Testserver; bekannte Kontrollereignisse und erlaubte Exporte, Speicherung für benötigte Ereignisse prüfen. Erfolgreicher Kontroll-Export beweist nur diese Kanalabdeckung, keine vollständige Polar-Sicht.
- **API/Architektur:** V vorhandene lokale Clientpfade; P externer serverseitiger Export/Run-Korrelation. Keine Polar-Check-Emulation im Client.
- **Freigabekriterium:** exakter Polar-/PolarLogs-Build und Config, Kanalberechtigungen, Zeitabdeckung und Datenverlust dokumentiert; sonst nur U-Ergebnis.

### AC-POL-BEH-001 · P3 · Polar: sechs Verhaltensbereiche · CMB/SCF/MOV/NET

- **Version/Evidenz:** B0/B1b Q für lokale Aktionen; Polar-Algorithmus U, POLLOG D.
- **Ist/H:** Intave-/Grim-Erkenntnisse erklären keine Polar-Checks. H: einzelne reproduzierbare Szenarien erzeugen beobachtbare Unterschiede.
- **Aufbau/Widerlegung:** T-02 bis T-07 erst nach T-08, jeweils Vanilla, Einzelmodul und eine Kombination; unterschiedliche Hypothesen pro Bereich dokumentieren. Ohne Serverwirkung oder ausreichende Logs unbestimmt.
- **API/Architektur:** gleiche kanonischen Clientdienste; P Testkonfiguration ausschließlich im autorisierten Labor, kein neues Clientprofil freigegeben.
- **Freigabekriterium:** Ergebnis bleibt Polar-build-/config-/szenariogebunden; keine Aussage über proprietären Mechanismus oder universelle Kompatibilität.

## Zurückgestellte Ideen und Versionspflege

Full Disabler, allgemeines Fly, neue dauerhafte NoSlow-Fenster sowie universelle sichere CPS/Noise/Delay-Werte bleiben U ohne Entwicklungsauftrag. LB-Kandidatensuche ist ein Vergleichsdesign, kein Kompatibilitätsnachweis. NCP erhält erst nach Commit-/Config-Pin einen eigenen Befund. Diese Restpunkte werden unter [offene Fragen](AC_OPEN_QUESTIONS.md) geführt, nicht als unbegrenzter Backlog.

Revision 1.0 konsolidiert die drei B0-Berichte gegen B1a/B1b. Künftige Revisionen behalten IDs bei; jede neue Code-/AC-Version erhält getrennte Evidenz und Run-IDs. Live-Ergebnisse dürfen Q/H nicht still überschreiben.
