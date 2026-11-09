# Intave: Bypass-Forschungs- und Konzeptplan

Stand: 28.10.2026. Grundlage: Pulsar `prep/neutron-opencode-wave00` / `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb` und Intave-Quellstand [`b950bdf`](https://github.com/intave/intave/tree/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6). Ergänzt den [Rotations-/Scaffold-Research](research-rotation-aura-scaffold-2026-10-28.md). Ziel sind funktionale, möglichst unauffällige und flüssige Konzepte für Rotation, Combat, Movement und Network einschließlich Erforschung eines möglichen Disablers. Dies ist ein Forschungsplan, keine Implementierung und kein Nachweis eines funktionierenden Bypasses.

Fortsetzung mit vertieften Quellpfaden, Korrekturen und erweiterten Testkonzepten: [Intave-Research, zweite Runde](intave-research-round-2-2026-10-28.md).

**Evidenzstatus:** A = im referenzierten Quellcode beobachtet; H = daraus abgeleitete Hypothese; U = ungeprüft; V = auf einer exakt dokumentierten Kombination durch Messung bestätigt. Kein nachfolgendes Bypass-Konzept hat Status V. Herstellerdokumentation und Quellstand können von einer installierten Serverversion, deren Einstellungen und optionaler Cloud-Auswertung abweichen. „Kein sichtbarer Flag“ ist nur eine Beobachtung im jeweiligen Messfenster.

## 0. Was Intave gemeinsam auswertet

| Ebene | Konkreter Anhaltspunkt | Konsequenz für Konzepte |
|---|---|---|
| Rotationsgeometrie und Combat | [AttackRaytrace](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/AttackRaytrace.java) und mehrere Rotationsheuristiken | Einen Aim-Punkt, lokales/empfangenes Ziel, Timing und Blickposition zusammen betrachten. |
| Klicktempo und Muster | [ClickSpeedLimiter](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/ClickSpeedLimiter.java), [ClickPatterns](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/ClickPatterns.java) | Maximalwert und zeitliche Statistik sind getrennt; Attack- und Swing-Pakete nicht gleichsetzen. |
| Itemnutzung und Blocking | [BlockingHeuristic](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/heuristics/other/BlockingHeuristic.java), [UseItemBrancher](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/physics/branch/UseItemBrancher.java) | Sichtbarer Itemzustand, Aktionen und tatsächliche Bewegung müssen über Tickgrenzen zusammenpassen. |
| Movement | [Physics](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/Physics.java) simuliert Kandidaten einschließlich Umgebungs-/Protokollzustand | Ein allgemein „sicherer“ Multiplikator für Speed/Fly lässt sich aus einem einfachen Grenzwert nicht ableiten. |
| Timing | [Timer](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/Timer.java) registriert `PlayerTime` und `MicroBlink`; [MicroBlink](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/movement/timer/MicroBlink.java) betrachtet u. a. Bewegungs-Timing und Angriffe | Langfristige Paketanzahl allein beschreibt das Risiko von Delay-/Release-Mustern nicht. `Balance`- und `PacketLoss`-Klassen sind im untersuchten Stand deprecated; nicht ohne Registrierung als aktive Checks behaupten. |
| Scaffold | [PlacementAnalysis/RotationSpeed](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/world/placementanalysis/RotationSpeed.java) und [PacketOrder](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/world/placementanalysis/PacketOrder.java) | Rotationsverlauf und Platzierung gehören in eine gemeinsame Timeline. |

Intave beschreibt selbst Bewegungssimulation, Heuristiken und optionales Cloud-ML als getrennte Säulen ([Quelle](https://github.com/intave/intave/tree/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6)). Aus lokal sichtbaren Check-Implementierungen lassen sich Cloud-Entscheidungen nicht herleiten.

## 1. Gemeinsame Versuchsbasis

### Testlabor

Ein eigener oder ausdrücklich zur Forschung freigegebener Server mit dokumentierter Server-JAR, Intave-Build und Checks/Config, gegebenenfalls ViaVersion und Cloud-Status. Minecraft 1.8.9 und Pulsar-Commit festhalten. Für jede Variante: gleiche Welt, Pfad, Ziele, Input-Spur, Sensitivity, Equipment, Tickrate und Testdauer; Seed und Profil-ID aufzeichnen. Vergleich über mehrere Läufe und nach Intave-Neustart; Reihenfolge der Varianten variieren, um aufgebaute Check-Historien und Trainings-/Aufwärmeffekte nicht mit dem Effekt des Modus zu verwechseln.

Auf Clientseite monotone Zeit, äußerer Tick, Player-Step, Sequenz, lokale und zuletzt tatsächlich angenommene Position/Rotation, Owner/Requests, Input, Paket-Queue, Attack, Swing, Block, Slot und Placement mitschreiben. Auf Serverseite Ankunftsreihenfolge, Timestamps, Check-/Violation-/Mitigation-Ereignisse, bestätigte Treffer und Blöcke, Position-Korrekturen und verfügbare Debug-Metadaten erfassen. Sichtbarkeit von Cloud- oder verzögerten Sanktionen gesondert dokumentieren. Akzeptanzbegriffe: lokal geplant → lokal freigegeben → Transport → serverseitig empfangen → Wirkung bestätigt → ggf. spätere Erkennung.

Pro Run immer Vanilla ohne Module, dann eine Variable pro Experiment, anschließend Kombinationen. Erst wenn ein Einzelmodus nachvollziehbar funktioniert, über Modulkombinationen urteilen. Verzögerungen/Drop-Szenarien mit begrenztem, reproduzierbarem Netzsimulator nur im Testlabor erzeugen; sicherstellen, dass Logs auch bei fehlenden Ereignissen tatsächlich aktiv sind.

### Metriken und Promotion

Rotation: Winkeldeltas und -beschleunigung, Quantisierungsreste, Wechsel/Reset/Ownership, Trefferquote, Zielerfassung und Kamerafehler. Scaffold: geplante vs. bestätigte Blöcke/min, lokale Ablehnungsgründe, Ghostblocks, Stürze, Setbacks, Rotationsaktivität und Pausen. Combat: tatsächliche Attack-/Swing-CPS pro Serverfenster, Intervalle und Verteilung, Trefferrate, blockierte/nerfed Treffer, Damage-Taken und Block-Uptime. Movement: Distanz/Zeit, vertikaler Gewinn, Dauer, Setbacks und Verlust gegenüber Vanilla. Network: gehaltene/ausgespielte Paketfolgen, Timing-Verteilung, Datenalter, Hit-Rate, verursachte Replays/Bursts und Kontextwechsel.

Vor jedem Experiment schriftlich festlegen: Vorteil gegenüber Vanilla, ab wann ein Run fehlschlägt, benötigte Mindestdauer und wie viele unabhängige Wiederholungen. Beispiel für **ein erstes internes Gate**, keine statistische Garantie: mehrere 10-minütige Läufe je Variante plus mindestens ein längerer Session-Test; keine Setbacks/Ghostblocks oder unerwarteten Check-Events im sichtbaren Log und ein vorab definiertes messbares Nutzensignal. Ein Nullereignis in kurzer Laufzeit beweist keine Flaglosigkeit. Scheitert eine Hypothese oder zeigt sich kein Vorteil, bleibt der Modus experimentell oder wird verworfen.

## 2. Rotation: ein gemeinsames Modell für Aura, AimAssist, Scaffold

### R1 — Kontinuierliche, zustandsgebundene Trajektorie

**Idee H:** Auf dem vorhandenen `RotationManager` aufbauen: getrennte Kamera-, angeforderte und lokal angenommene Blickwerte, Owner samt Kontextgeneration; pro Player-Step eindeutige Planung und pro Aktion dieselbe nach Paketbearbeitung gültige Pose. Beginn, Zielwechsel, Halten, Auslaufen und Abbruch als explizite Zustände modellieren. Für Scaffold Planfreigabe von Rotationsfreigabe trennen, wie in C4/M5/D3 beschlossen. Eine Haltephase muss bei Welt-/Connection-Wechsel, Paketänderung oder fehlendem gültigen Owner enden.

**Nutzen:** Reduziert vermeidbare Owner-Lücken und Fehlaktionen; kann Rotationssprünge verringern. Die Existenz einer glatten Trajektorie beweist keine Umgehung. **Risiko:** Ein unbedacht gehaltener Owner kann Aura verdrängen; Telemetrie muss handoff, abgelehnte Requests und Eingabefolgen zeigen. **Test:** Einzelmodule, Scaffold + Aura, kurzer Zielverlust, Bewegungspaket gecancelt/verzögert/ersetzt, Disable/Disconnect. Ergebnisvergleich mit aktuellem Verhalten und bestätigten Blöcken/Treffern.

### R2 — Sensitivity-Konsistenz und echte Geometrie

**Befund A:** Pulsar übernimmt letzte Sub-GCD-Änderungen direkt. Intaves [Sensitivity-Heuristik](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/heuristics/combatpatterns/rotation/RotationSensitivityHeuristic.java) beobachtet bei bestimmten Angriffssituationen Pitch-Deltas. **Hypothese:** Ein durchgängiges Raster könnte Inkonsistenzen verringern, aber auch Zielpunkte verfehlen oder andere Muster erzeugen.

**Versuchskandidaten:** (a) Ist-Verhalten; (b) Raster-Rundung bezogen auf letzten tatsächlich angenommenen Winkel; (c) Residual-Akkumulator, der nicht ausgegebene Kleinstbewegungen über Folgeschritte mitnimmt. Für jede Variante finalen Strahl nach Quantisierung gegen zulässige Hitbox/Blockseite testen; kein Aktionserfolg bei bloß geplantem Winkel. Bewegung bei Pitch-Clamp, Yaw-Wrap und manueller Mausänderung getrennt prüfen. Kandidat verwerfen, wenn er zu dauerhaftem Stillstand, Fehlzielen, wiederholtem Oszillieren oder neuen Check-Events führt. Keine numerischen Defaults ohne das bestehende CMB-/SCF-Gate.

### R3 — Zielpunkt- und Kontextplanung

**Aura:** Kandidaten auf sichtbarer Zielhitbox wählen, nach Winkelkosten, tatsächlichem Raycast, Entfernung und Stabilität im nächsten Schritt bewerten. Nicht jede Bewegung auf denselben mathematischen Box-Punkt zwingen. FOV, Zielbindung und tatsächliche Target-Switches explizit erfassen. **AimAssist:** sichtbare Kamera-Änderung als begrenzter Beitrag zur physischen Eingabe; Fokus und manuelle Korrektur haben Vorrang; ohne Spielerinput und im GUI gesondert testen. **Scaffold:** mehrere erreichbare Flächenpunkte und Seiten bewerten; Rotationskosten, Bewegungsfortsetzung, Platzierungsmöglichkeit und Folgeschritt berücksichtigen. Vergleiche Ist vs. Kandidatenauswahl, nicht unkontrollierte große Suchraster.

**H:** Zeitlich kohärente, zielgebundene Abweichung kann weniger regelmäßig sein als ausschließlich auf einen idealen Punkt zu zielen. Das ist nicht gleichbedeutend mit menschlichen Eingaben. Tests gegen Intaves Genauigkeits-, Streuungs-, Snap- und Placement-Checks plus Holdout-Situationen mit bewegten Zielen, verdeckten Flächen und mehreren Sensitivities. Noise-Frequenz und Stärke bleiben zunächst unabhängige Testfaktoren, keine angeblich sicheren Werte.

## 3. Attack, CPS, Blocking und NoSlow

### A1 — CPS-Versuchsplan und Scheduler

**Befund A:** Pulsar berechnet aktuell Delay aus zufällig gewähltem CPS und begrenzt Controller-Angriffe auf einen äußeren Tick (`AttackScheduler`). Ein gleichverteiltes CPS-Sample ergibt durch `1000/CPS` keine gleichverteilten Intervalle; Tick-Quantisierung und Zielverlust verändern die tatsächliche Verteilung zusätzlich. Intaves ClickSpeedLimiter liest im betrachteten Code `max-cps` mit Default 20 und zulässiger Konfigurationsspanne 8–40; die konkrete Servereinstellung kann abweichen. Das ist **keine sichere CPS-Zahl**. [Quelle](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/ClickSpeedLimiter.java).

**Versuchsmatrix U:** Vanilla-Handklicks als Baseline; feste Zielwerte 6, 8 und 10 CPS, zusätzlich 12 CPS als expliziter Belastungstest; jeweils tatsächliche serverseitige Attack- und Swing-Intervalle und Check-Reaktionen dokumentieren. Das sind Laborpunkte, keine empfohlenen oder garantierten Produkt-Presets. Niedrige CPS verhindern weder Mustererkennung noch Rotations-Flags. Bei ≤20 Client-Ticks/s und höchstens einem Angriff pro äußerem Tick bestimmen Tickgrenzen und Zustand die wirklich erreichbare Rate.

**Scheduler-Kandidaten:**

1. Ist: unabhängiges Uniform-CPS-Sampling als Kontrollgruppe.
2. Empirische Intervallverteilung: eigene, freiwillig aufgezeichnete manuelle Sessions in Trainings- und getrennte Prüfsegmente teilen; Zeitintervalle direkt samplen, Mindest-/Maximalwerte festlegen und nur echte anstehende Angriffsgelegenheiten ausführen. Keine Aufzeichnung persönlicher Daten ohne bewusst aktivierte Diagnose.
3. Zustandsmodell: langsame, begrenzte Änderung einer aktuellen Zielrate plus kurzzeitige Schwankung; Phasen `idle`, `acquire`, `engaged`, `recover` beeinflussen Wartezeit und werden nicht jedes Tick neu ausgewürfelt. Parameter aus Trainingsdaten, nicht aus einem einzelnen Flag-Schwellenwert. Seed und Input reproduzierbar halten.

Verglichen werden Mittelwert, Perzentile, Varianz, Autokorrelation, lange Sequenzen, Tick-Buckets und Kombinationen mit Aim-/Targetwechseln. Bei der untersuchten Intave-Version analysieren `Deviation`, `Entropy` und `Repetitive` überwiegend ARM_ANIMATION-Folgen; `EqualDelay` enthält einen auskommentierten Flag-Aufruf. Einzelne Klassen oder ein optisch zufälliger Verlauf reichen nicht für eine Umgehungsbehauptung. [ClickPatterns-Registrierung](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/ClickPatterns.java).

### A2 — Autoblock als gemeinsamer Item-State

**Befund A:** `AutoblockController` und `NoSlow` besitzen getrennte Pfade, die C07/C08 und teilweise C09 erzeugen. Der betrachtete Intave-[Blocking-Check](https://github.com/intave/intave/blob/b950bdf7570b8624b7907e2c42dfb3b6d63c40f6/src/main/java/de/jpx3/intave/check/combat/heuristics/other/BlockingHeuristic.java) prüft unter Voraussetzungen unter anderem schnelle Release-/Use-Folgen und Itemoperationen. Das aktuelle Verhalten ist noch nicht als Flag reproduziert.

**Kandidaten H:** `Off` als Kontrolle; `Manual` mit ausschließlich physischer Use-Taste; `Hold` mit tatsächlichem länger gehaltenem Block und explizitem Angriffsfenster; `Coordinated` mit gemeinsamem Zustandsautomaten für Idle/Use/Release/Attack/Resume. Das Steuerziel ist ein begrenzter Block-/Angriffskonflikt mit dokumentiertem Trade-off zwischen Block-Uptime und Trefferfrequenz. Ein manuell ausgelöster Use hat Vorrang; Slot- und Consumable-Wechsel, Weltwechsel und gelöschte/verzögerte Pakete invalidieren den Zustand. Kein unabhängiger NoSlow- oder Aura-Writer darf in derselben Phase inkonsistente Use-Events erzeugen.

Test je Variante: C07/C08/C09/C02/C0A/C03-Reihenfolge, wirklicher Blockstatus und Schaden, Itemwechsel, Hotbar-Mangel, GUI, Latenz, Packet-Replay und Wechsel Scaffold/Aura. Die Zahl „ein Paketabstand“ ist keine pauschale Freigabe; mehrere Prüfungen und deren Aktivierungsbedingungen greifen zusammen.

### A3 — NoSlow nach Itemtyp

**Befund A:** Aktuelles Intave-NoSlow setzt im SlowdownEvent volle Bewegung; für Consumables sendet es beim Beginn Release, für Schwerter Use in PRE. `UseItemBrancher` unterscheidet u. a. Legacy-Itemzustand und Unsicherheit; eine durch Konfiguration abgeschaltete NoSlow-Erkennung hat andere Bedeutung als ein Bypass bei aktivierter Erkennung.

**Kandidaten H:** (1) Kontrollgruppe Vanilla-Slowdown; (2) begrenzte, profilierbare Reduktion mit separat aufgezeichneter realer Serverbewegung; (3) koordinierter Use-/Release-Versuch mit Item-State, getrennt für Schwert, Essen/Trank und Bogen. Erfolg verlangt zugleich messbare Gehgeschwindigkeit, tatsächliche Wirkung des Items und keine beobachtete Mitigation. Bei Consumables ist unvollständiger Verbrauch kein Erfolg. Ein Slotwechsel als möglicher Ambiguitätsfall ist lediglich Hypothese und muss gegen genau die Legacy-Verzweigung und weitere Checks validiert werden; keine Annahme, dass er Intave generell verwirrt.

## 4. Movement: Speed und Fly

### M1 — Physiknahe Speed-Kandidaten

**Befund A:** Der bestehende Intave-Speed springt bei Bodenkontakt, setzt bei Sprint horizontale Bewegung und hat optional einen zusätzlichen Air-Faktor; seine Basisformel hat den im MOV-Audit dokumentierten 1.2-Faktor. Intave simuliert Bewegungszustände, Effekte, Kollisionen und Umwelt. Ein kleiner Faktor ist deshalb nicht automatisch unsichtbar.

**Experiment:** Zuerst Vanilla-Sprint und Vanilla-Sprintjump auf definiertem Pfad messen; danach getrennt (a) automatisierter Vanilla-Sprintjump ohne direkte Motion-Verstärkung, (b) bestehender Intave-Modus, (c) begrenzte Variation der horizontalen Änderung als Testparameter. Nicht die Speed-Formel oder bestehende Defaults aus dieser Planung ändern. Die Kandidaten müssen pro Tick Input, letzte Motion, Boden, Sprint, Jump, Potion, Reibung, Block, Velocity und simulierte bzw. serverseitige Abweichung loggen. Testfälle: gerade/diagonal, Start/Stop, Rand/Kollision, Slabs/Treppen, Speed/Slowness, Velocity, Wasser/Leiter und Scaffold. Erfolg nur bei realem Distanzgewinn **nach** Korrekturen gegenüber Vanilla, stabiler Steuerbarkeit und dokumentierten Check-Daten.

### M2 — Fly / vertikale Mobilität

**Status U:** Kein belastbarer Universal-Fly-Bypass aus den gesichteten Quellen ableitbar. Tests sollen unterschiedliche Mechanismen ausdrücklich trennen: legitime Flugberechtigung als Positivkontrolle; Scaffold/Tower als platzierungsbasierte vertikale Mobilität; zeitlich begrenzte Hypothesen zu Server-/Clientzustandsabweichung nach erlaubten externen Impulsen oder Kollisionen. Höherer lokaler Y-Wert ohne serverbestätigten Positionsgewinn ist kein Fly-Erfolg. Modellannahmen, Bedingungen und exakte Wirkung dokumentieren; eine kurze Toleranzphase ist kein dauerhafter Flug. Break/Stop bei Korrektur und kontrollierter Reset gehören ins Versuchsdesign.

### M3 — Velocity und MoveFix

Vor neuer Velocity-Abstimmung Impuls, Quelle, Zeitpunkt, relevante Achsen, tatsächlichen ersten Simulationsschritt und resultierende Position auf beiden Seiten messen. Varianten nach Nutzen (Steuerbarkeit und Position nach Knockback), nicht nur lokalem Motion-Wert beurteilen. MoveFix muss weiter nur aus einem für aktuelle Welt/Connection lokal bestätigten Owner arbeiten. Timer-, Input- und Sprint-Konflikte folgen der CORE-Planung; Prioritäten und noch offene Defaults nicht als erledigt behandeln.

## 5. Network: Backtrack, Tickbase und LagRange

### N1 — Backtrack „Observed Position“

**Idee H:** Den vorhandenen Backtrack beibehalten, aber eine klar begrenzte experimentelle Variante gegen die Pipeline-Metadaten messen: empfangene Serverposition, zuletzt angewendete Entityposition und deren Alter gleichzeitig führen. Eine verzögerte Zielposition nur innerhalb eines vorab definierten Zeitfensters und gültiger Ziel-/World-/Connection-Generation verwenden; bei Teleport, Destroy, Velocity, Targetwechsel oder Korrektur die Warteschlange geordnet freigeben/invalidieren. Keine neue parallele Queue neben `PacketPipeline`.

**Hypothese:** Bei Annäherung/Entfernung kann ein älterer lokaler Zielzustand kurzfristig einen funktionalen Unterschied erzeugen. Intave `AttackRaytrace` berücksichtigt eigene Ziel- und Feedbackzustände; daraus folgt kein sicherer Reichweitengewinn. **Vergleich:** off vs. mehrere Zeitfenster, Zielrichtung und Ping; serverseitiger Schaden und AttackRaytrace-Reaktionen sind wichtiger als lokale Trefferanimation. Reject bei zunehmender Trefferablehnung, stale target oder Queue-Wachstum.

### N2 — Tickbase „Earned Step“ und „Simulation Only“

**Kontrolle:** Bereits vorhandenes Guthabenmodell; `TickbaseBudget` zählt zurückgehaltene Player-Update-Bodies und begrenzt Freigaben. **Kandidaten H:** `Simulation Only` prognostiziert ohne echte Zusatzschritte und dient als Placebo; `Earned Step` variiert konservativ Bedingungen und Freigabezeitpunkt innerhalb nachweisbar vorher zurückgehaltener Schritte. Movement/Rotation bleiben pro Player-Step, Attack/Placement/Inventory höchstens pro äußerem Tick entsprechend D10. `StepContext` und Timer-Neutralität müssen vor Kombinationsfreigabe sauber geklärt werden.

Vergleich: frühere erreichte **serverbestätigte** Position und Trefferchance gegen Stillstandskosten, C03-Ankunftsverteilung, MicroBlink-/Timer-Signale, Setbacks und Server-Reach. Ein über längere Zeit ausgeglichener Tickzähler beweist nicht, dass Burst-/Aktions-Timing akzeptiert wird. Keine Werte als Anticheat-Toleranz aus hypothetischen Buffergrößen vermarkten.

### N3 — LagRange „Bounded Window“

**Befund A:** Bestehendes LagRange hält ausgehende C03 und eingehende S14 des Ziels; die bestehende `PacketPipeline` löst eine Outbound-Barriere vor Aktionen aus. Damit können zurückgehaltene Bewegungen vor einem Angriff gebündelt freigegeben werden. Ein Konzept, das behauptet, ein Angriff überhole grundsätzlich diese Bewegung, widerspräche dem Ist-Code.

**Kandidaten H:** `Inbound-only` als Messkontrolle; `Bounded Window` mit einer kleinen, ausdrücklich experimentellen Verzögerung und definiertem Release-Ereignis; `No Burst` als Steuerungsversuch, der Aktionen bei ausstehender Bewegung verschiebt statt die Barriere zu umgehen. Zeitfenster z. B. 0/50/100/150 ms nur als Laborparameter prüfen; echte Netzlaufzeit, Serverkonfiguration und Nutzwert separat auswerten. Reihenfolge, Höchstalter, Queuegröße und Lifecycle sind Teil der Akzeptanz. Prüfen, ob die Barriere ein sofortiges Release erzwingt; kein unbelegtes Versprechen eines sanften Ausspielens machen.

## 6. Full Disabler: Forschungsauftrag, kein erfundener Modus

**Status: U / keine bestätigte Methode.** Ein Full Disabler müsste auf einer konkret benannten Version und Konfiguration gleichzeitig nachweisen, dass die relevanten Serverprüfungen bzw. ihre Mitigationspfade trotz aktivierter Checks unwirksam werden. Die bloße Abwesenheit von Alerts, ein temporärer Toleranzzustand, eine deaktivierte Konfiguration oder ein clientseitiges Desync genügt nicht.

**Drei zu prüfende Hypothesenklassen:**

1. **Zustandsübergang:** Werden auf dem kontrollierten Server bei Join, Respawn, Teleport, Welt- oder Itemwechsel einzelne Tracker unterschiedlich initialisiert? Quellpfade und gültige Ereignisreihenfolge dokumentieren; jede vermutete Wirkung nach Ablauf der Übergangszeit erneut prüfen.
2. **Feedback-/Latenzgrenzen:** Entsteht bei reproduzierbarer, begrenzter Paketverzögerung zwischen Serverfeedback und Clientaktionen eine inkonsistente Check-Sicht? Nebenwirkung gegen Timer, AttackRaytrace, Physics und PlacementAnalysis gemeinsam beobachten. Kein Check wird als deaktiviert bezeichnet, solange nur eine Teilprüfung toleriert.
3. **Versions-/Übersetzungsinteraktion:** Unterschiede zwischen nativer 1.8.9-Verbindung und 1.8.9 über genau benannte ViaVersion-/Serverkombination separat testen; die in `UseItemBrancher` dokumentierten Unsicherheiten sind Anlass für einzelne Experimente, keine Bestätigung für einen übergreifenden Disabler.

**Nachweisdesign:** Positivkontrollen erzeugen für jede Checkfamilie eine unabhängig reproduzierbare serverseitige Reaktion ohne Kandidaten. Anschließend Kandidat aktivieren und dieselben Kontrollen bei unveränderter Konfiguration wiederholen, mit vollständigem Serverlog, Paketfolge und mehreren Neustarts; schließlich nach Deaktivierung des Kandidaten muss die Kontrolle wieder ansprechen. Ergebnisse je Checkfamilie als Matrix `aktiviert / Kontrolle ausgelöst / Kandidat-Effekt / Dauer / Reproduzierbarkeit` berichten. Teilweise Wirkung heißt Teilbypass, nicht Full Disabler. Bei fehlender zugänglicher Check- oder Cloud-Telemetrie bleibt die entsprechende Zeile unbestimmt.

## 7. Empfohlene Reihenfolge und Abhängigkeiten

| Phase | Ergebnis | Voraussetzung |
|---|---|---|
| 0 | Testserver-/Versionsmatrix, aktivierte Logging-Kanäle und wiederholbare Kontroll-Szenarien | Serverzugang; Cloud-Status bekannt oder unbekannt markiert |
| 1 | Gemeinsame Trace-Semantik, Rotation-/Action-/Packet-Reihenfolge, reproduzierbare Vanilla-Baselines | Keine Gameplay-Defaults ändern |
| 2 | Scaffold-Owner-Vertrag, R1/R2/R3 als isolierte Experimente | CORE-C4 und bestehende Action-Gates; offener Mechanismus dokumentiert |
| 3 | CPS-Scheduler-Studie und gemeinsamer Item-State mit Autoblock/NoSlow | Tatsächliche Swing-/Attack-Erfassung und Slot-/Use-Lifecycle |
| 4 | Speed/Fly-/Velocity-Experimente | Input-/Timer-Ownership und Simulator-Beobachtbarkeit |
| 5 | Backtrack/Tickbase/LagRange-Einzel- und Kombinationstests | PacketPipeline-Barrieren und StepContext verstanden |
| 6 | Disabler-Hypothesen und Kontrollmatrix | Positivkontrolle pro Checkfamilie |

Die Reihenfolge ordnet Forschungsabhängigkeiten; sie ist kein automatischer Auftrag, Produktmodi oder die in CORE-R01 als offen markierten Zahlen zu ändern. Der nächste konkrete Schritt ist die Spezifikation der Trace-Felder und eines wiederholbaren Testlaufs auf einer bekannten Intave-Version.
