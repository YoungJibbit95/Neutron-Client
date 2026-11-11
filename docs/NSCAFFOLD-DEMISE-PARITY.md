# NScaffold und Demise Scaffold: Funktionsvergleich

Stand: 11. November 2026. Dieser Vergleich beschreibt die Erweiterung um
Bau-Modi, Clutch-Hilfe und lokale Rotationseinstellungen sowie die stille
Rotation mit Neutrons gemeinsamer Bewegungskorrektur. **NScaffold ist weiterhin weder eine identische Kopie noch ein
vollständiger Funktionsport des Demise Scaffold.** Gemeinsame Namen und
übernommene Teilideen bedeuten keine gleiche Eingabe-, Rotations- oder
Platzierungsfolge.

## Geprüfte Quellen

Die Originaldateien wurden über den GitHub-Connector gelesen. Reproduzierbare
Referenz ist Commit `e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb` in
`YoungJibbit95/DemisePrivate`:

- **[D1]** [Scaffold.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/Scaffold.java), Blob `f5cbead2aa1d666af66946173fa358b626ac9244`.
- **[D2]** [RotationManager.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationManager.java), Blob `e17d1fe4a7cd0702025e3f870f57ab454b91f3a2`. Dieser Manager registriert zusätzliche Werte am aufrufenden Modul.
- **[D3]** [README.md](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/README.md), Blob `ac3c47ef7c38a6180b201f2bea2b586a43f9de7b`.
- **[D4]** [RotationHandler.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationHandler.java), Blob `29555b7cfca3b1e5cccad94e4d5ed06902784a31`. Hier liegen die vom Manager verwendete Interpolation und die globale Ereignisanbindung.

Beim vorherigen Phase-2-Vergleich war die zusätzlich gelesene damalige
`main`-Fassung von Scaffold.java textgleich mit [D1]. Maßgeblich für dieses
Dokument bleiben die angegebenen gepinnten Quellen.

Die Neutron-Seite ergibt sich aus:

- **[N1]** [NScaffold.java](../src/main/java/me/youngjibbit/module/impl/world/NScaffold.java)
- **[N2]** [NScaffoldPlanner.java](../src/main/java/me/youngjibbit/core/world/NScaffoldPlanner.java)
- **[N3]** [ScaffoldPlacementService.java](../src/main/java/me/youngjibbit/core/world/ScaffoldPlacementService.java)
- **[N4]** [Bedienungs- und Einstellungsdokumentation](NSCAFFOLD.md)
- **[N5]** [pom.xml](../pom.xml)
- **[N6]** [NScaffoldModeController.java](../src/main/java/me/youngjibbit/core/world/NScaffoldModeController.java)
- **[N7]** [NScaffoldClutchPlanner.java](../src/main/java/me/youngjibbit/core/world/NScaffoldClutchPlanner.java)
- **[N8]** [NScaffoldRotationController.java](../src/main/java/me/youngjibbit/core/world/NScaffoldRotationController.java)
- **[N9]** [ConfigManager.java](../src/main/java/me/youngjibbit/config/ConfigManager.java)
- **[N10]** [RotationCorrections.java](../src/main/java/me/youngjibbit/core/combat/RotationCorrections.java)
- **[N11]** [RotationManager.java](../src/main/java/me/youngjibbit/core/combat/RotationManager.java)

**Adaptiert** bezeichnet eine vorhandene verwandte Neutron-Funktion mit den
angegebenen Unterschieden. **Fehlt** bedeutet, dass die betreffende
Originaloption nicht implementiert ist. Der Vergleich ist eine Quell- und
Funktionszuordnung; er belegt keine gleichen Spielergebnisse oder gleiche
Bewertung durch einen Server.

## Hauptfunktionen und Einstellungen

Die Originalbezeichnungen dieser Tabelle stammen aus [D1]. Die Neutron-Werte
und ihre Anbindung stehen in [N1–N3, N6–N8].

| Gruppe | Demise Scaffold | Aktueller NScaffold-Stand |
| --- | --- | --- |
| Hauptmodus | `Mode: Normal / Breezily / Telly` | **Adaptiert als Build Mode mit diesen drei Auswahlen.** Normal verwendet den normalen Controller mit stiller Rotation. Breezily verwendet eine feste Winkelpräferenz, aber keine automatische Folge seitlicher Eingaben. Telly verwendet Flugfenster, aber keinen automatischen Sprung. |
| Telly-Steuerung | `Straight telly ticks`, `Diagonal telly ticks` | **Adaptiert.** Separate Werte von 1–5, standardmäßig 4/2. Gezählt werden aufeinanderfolgende tatsächlich beobachtete Flugticks der aktuellen Sitzung. Landen und Beobachtungslücken setzen die Folge zurück; ein gültiger Clutch-Plan hat Vorrang vor dem Telly-Fenster. |
| Rotationsziel | `Rotations: Normal / Center / GodBridge / Reverse` | **Alle vier Zielarten in angepasster Form vorhanden.** Die bisherige Bezeichnung `Standard` bleibt für die normale Neutron-Zielwahl erhalten. Center benutzt tatsächliche Stützflächen-Bounds. Godbridge und Reverse behalten die beschriebenen Geometrie- und Reichweitengrenzen. Die ursprüngliche Ereignisfolge und dichte Punktabtastung werden nicht kopiert. |
| Feste Rotationspräferenz | `Static-ify` | **Adaptiert.** Godbridge fordert beim Bewegen den konfigurierten Pitch, im Stillstand 90° an; Reverse fordert 80° an. Ein fester Wunschstrahl muss selbst eine benötigte reale Fläche treffen. Die vorhandene Interpolation und die Prüfung des tatsächlich akzeptierten Zwischenwinkels bleiben bestehen. |
| Breezily-Geometrie | Eigene Ausrichtung und seitliche Eingabesteuerung | **Teilweise adaptiert.** Stille 45°-Präferenz mit 80° gerade, 75,7° diagonal und 90° im Stillstand. Seitliche Eingaben bleiben manuell; die Zielrotation wird nicht als neue Bewegungsrichtung zurückgeführt. |
| Clutch-Auswahl | `Clutch`; `Clutch criteria: MouseOver / Prediction`; `Clutch rotation mode: Normal / Center` | **Adaptiert.** Eigener begrenzter Planer für aktuelle und kurz vorausliegende ungestützte Zellen. MouseOver prüft einen nützlichen aktuellen Strahl; Prediction verwendet eine kurze Projektion der tatsächlichen Bewegung. Normal/Center wählen eine erreichbare Punktpräferenz beziehungsweise den realen Flächenmittelpunkt. |
| Clutch-Reaktion | `Instant rot criteria: Always / On miss / Predict` | **Adaptiert als drei kombinierbare Fast-Clutch-Schalter.** Nur bei Advanced Rotations wirksam: dringliche Pläne überspringen optionale Pausen, zusätzliche Verlangsamung, Trägheit und Achsvariation. Die eingestellten maximalen Achsgeschwindigkeiten werden weiterhin eingehalten. Keine Zusage unbegrenzter oder sofortiger Drehung. |
| Clutch-Sneak | `Auto sneak on clutch`, `Ground ticks to sneak` | **Adaptiert.** Eigene beobachtete Bodenticks, gültiger Clutch-Plan und kurze Anforderung über zwei Ticks. Der normale Eingabepfad prüft Bodenkontakt und manuelle Eingaben erneut und ergänzt gegebenenfalls Sneak mit normaler Verlangsamung. Keine global gehaltene Taste; keine identische Kopie des 100-ms-Timers. |
| Sprint | `Sprint mode: Normal / Silent / Ground / Air / None` | **Fehlt.** NScaffold erzwingt oder unterbindet Sprint nicht und bietet diese Modusauswahl nicht an. |
| Geschwindigkeit | `Speed mode: Tick / Exempt / Diagonal / None` | **Nicht übernommen.** Keine entsprechenden Geschwindigkeitsfunktionen oder Änderungen an Bewegungswerten. |
| Höhenhaltung | Addons `KeepY`, `Speed keepY` | **Adaptiert.** `Keep Height` hält eine begrenzte Zielebene mit eigenen Rücksetzbedingungen. Die Kopplung an das Demise-Speed-Modul fehlt. |
| Automatisches Springen | Addon `Jump`, `Blocks to jump`, `Only straight`; Telly-Sprungsteuerung | **Fehlt.** Keine blockzahlabhängige Sprungfolge und kein automatischer Telly-Sprung. |
| Automatisches Sneaken nach Blockzahl | Addon `Sneak`, `Blocks to sneak`, `Only sneak on ground` | **Fehlt.** Die separate Clutch-Sneak-Hilfe ersetzt dieses blockzählende Addon nicht. |
| Kanten- und Schwebefunktionen | Addons `Safewalk`, `Safewalk when no data`, `Hover` | **Fehlen als entsprechende Optionen.** Begrenzte Zielplanung und normale optionale Clutch-Sneak-Eingabe sind kein Port dieser Bewegungsfunktionen. |
| Tower | `Tower` und `Tower move`: `Jump / Vanilla / PullDown / NCP`; `PullDown motion` | **Nicht übernommen.** Keine entsprechende Tower-Modusauswahl oder eigene Tower-Bewegungssteuerung. |
| Raytrace | Addon `RayTrace`, `Strict rayTrace` | **Anders umgesetzt.** Erste Weltfläche, normale Controller-Reichweite und tatsächlicher Treffer sind immer erforderlich. Diese Prüfungen sind keine abschaltbaren Kopien der Originaloptionen. |
| Handanimation | Addon `Swing` | **Adaptiert.** Normale sichtbare Handanimation nach lokal erfolgreicher Platzierung. |
| Zielblockdarstellung | Addon `Target block ESP` mit Render-/Shaderanbindung | **Fehlt als gleichartige Darstellung.** Das separate TargetESP kann den tatsächlich angenommenen NScaffold-Strahl markieren. Ein Hitpoint-Marker ist keine Darstellung des geplanten Zielblocks. |
| Hotbar und Slotdarstellung | Automatische Blockwahl sowie eigene Slotdarstellung und Rücksetzung | **Anders umgesetzt.** `Auto Select Blocks` erlaubt normalen sichtbaren Slotwechsel. Der aktuelle brauchbare Stack wird bevorzugt, andernfalls der erste geeignete Hotbar-Stack. Die globale Original-Slotdarstellung fehlt. |
| Blocksuche und gehaltene Unterstützung | Direkte Nachbarprüfung, bevorzugte frühere Unterstützung und weiterreichende Suche | **Adaptiert.** Normaler Planer: höchstens drei konkrete Zellen mit fünf Nachbarflächen, also 15 neue Flächenprüfungen. Clutch: höchstens sieben konkrete Zellen und 35 neue Flächenprüfungen. Gehaltene Strahlen und Validierungen werden separat geprüft. Keine ursprüngliche Radius-/Nachbarsuchfolge. |
| Platzierungsablauf | Eigene Ereignisfolge für Zielwahl, Rotation und Platzierung | **Anders umgesetzt.** Neutrons bestehende Platzierungsautorität, frische Szenen-/Owner-Prüfungen und gemeinsames Tickbudget. `Placement Delay` bleibt eine zusätzliche eigene Einstellung. |
| Weltwechsel | `onWorld` schaltet das Originalmodul aus | **Anders umgesetzt.** Alte Pläne werden immer verworfen. `Smart Disable on World Change` steuert zusätzlich das Abschalten und ist standardmäßig aus. |

### Was die angeforderten Winkel tatsächlich bedeuten

Die normale Neutron-Suche hält nützliche aktuelle Strahlen und vermeidet unnötige
neue Flächen. Center fordert ausdrücklich den tatsächlichen Flächenmittelpunkt
an. Breezily und Static-ify werden nur angeboten, wenn ihr gewünschter fester
Strahl selbst zur benötigten Route passt. [N2–N3]

Diese **Zielwahl** ist nicht mit der **Platzierungsfreigabe** gleichzusetzen.
Während einer begrenzten stillen Annäherung kann bereits ein anderer
Zwischenwinkel dieselbe zulässige Stützfläche treffen. Nach Annahme dieses
realen Winkels darf der Controller dort platzieren. Center/Static-ify bedeuten
daher weder eine Platzierung mit nicht angewandten Wunschwerten noch eine
zusätzliche Garantie, dass jede Platzierung auf den numerisch exakt erreichten
Endwinkel wartet. [N1–N3]

Die ursprüngliche Demise-Godbridge-Haltung, ihre Eingabekopplung und alle
diagonalen Abläufe werden dadurch weiterhin nicht identisch reproduziert.
Die eigenständige Geometrie und ihre Controllergrenzen sind Teil des
implementierten Verhaltens, keine lediglich umbenannten Originalpfade.

## RotationManager und RotationHandler

Ein Vergleich nur der Werte in Scaffold.java wäre unvollständig.
`new RotationManager(this)` registriert die Gruppen aus [D2]; deren tatsächliche
Interpolation wird an [D4] weitergegeben. NScaffold ergänzt entsprechende
lokale Einstellungen hinter **Advanced Rotations** und führt die Zielrotation
über Neutrons gemeinsamen stillen Rotationspfad aus. Der globale
Original-Handler bleibt außerhalb der Integration. [N1, N8, N10–N11]

| Gruppe aus [D2–D4] | Originaleinstellungen / Ablauf | NScaffold |
| --- | --- | --- |
| Sichtbare oder verborgene Ausrichtung | `Silent` | **Als Standard über Neutrons gemeinsamen RotationManager angebunden.** Die Kamera bleibt unverändert; der tatsächlich angenommene Zielwinkel bestimmt die Platzierungsfreigabe. Kein zusätzlicher Silent-Schalter. |
| Glättung | `Extra smoothing factor`; `Smooth mode: Linear / Relative` | **Adaptiert.** Linear verteilt die Bewegung auf den restlichen Winkelweg; Relative verwendet zunächst 25 % des jeweiligen Restwinkels. Der zusätzliche Faktor skaliert den Schritt. Berechnung pro beobachtetem Spieler-Tick statt Original-Frame-/Mauseingabepfad. |
| Beschleunigung | `Accelerate`, `Yaw accel factor`, `Pitch accel factor` | **Adaptiert.** Getrennte Gewichte des vorherigen Achsschritts. Ein höheres Gewicht bedeutet mehr Trägheit. Überschießen und Fortsetzen entgegen dem aktuellen Ziel werden begrenzt. |
| Zusammenhang der Achsen | `Imperfect correlation` | **Adaptiert.** Unabhängige Variation der ausgewählten Achsgrenzen um bis zu ±10 %, mit weiterhin harten Benutzermaxima. |
| Getrennte Geschwindigkeiten | `Yaw rotation speed (min/max)`, `Pitch rotation speed (min/max)` | **Vorhanden in eigener tickbezogener Interpretation.** Je Achse 0,01–180 Grad pro Spieler-Tick; neue Basiswerte höchstens einmal pro Tick. Keine identische numerische Umsetzung des ursprünglichen zeit- und empfindlichkeitsbezogenen Mauspfads. |
| Bewegungskorrektur | `Movement correction: Silent / Strict / None` | **Über Neutrons vorhandenen MoveFix angebunden.** `SIMPLE` ist immer aktiv; Eingaben werden in die Zielausrichtung übertragen. Dieselbe Basis gilt für Bewegungs-, Sprung- und Sprintprüfungen. Keine Übernahme der ursprünglichen Modusauswahl oder Mathematik. |
| Kurze Unterbrechungen | `Short stop`, `Duration`, `Rotation diff buildup to stop`, `Max threshold attempts to stop` | **Adaptiert.** Aufsummiert wird tatsächlich angenommene Winkelstrecke. Eine konfigurierte Anzahl vollständiger Schwellenüberschreitungen darf übersprungen werden. Pausen dauern nach monotoner Zeit 25–1000 ms. Globale Paket-/Yaw-Akkumulation und Original-Zählerfolge werden nicht importiert. |
| Dringliche Clutch-Ausrichtung | Scaffold ändert bei ausgewählten Kriterien die Rotationsgeschwindigkeit | **Anders angebunden.** Dringlichkeit hebt optionale weiche Verlangsamungen auf, verwendet aber weiterhin die Benutzermaxima und den gewöhnlichen Controllerpfad mit stiller Rotation. |

Die Defaults dieser Erweiterung lauten: **Stille Rotation mit MoveFix aktiv**, **Advanced Rotations aus**;
bei Aktivierung zunächst Linear, Extra Smoothing Factor 0,15, alle vier
Geschwindigkeitswerte 180, Accelerate/Imperfect Correlation/Short Stop aus,
Beschleunigungsgewichte 0,25, Pausendauer 50 ms, Schwelle 180° und eine
übersprungene Schwelle. Ohne Advanced Rotations bleibt der bisherige
**Turn Speed** maßgeblich. Minima sind keine Mindestbewegung, denn Restwinkel,
Glättung und Variation dürfen einen Schritt verkleinern. [N1, N8]

Demise registriert außerdem `Distance based rotation speed`, `Min range`,
`Max range` und `Decrement per cycle`. In der geprüften Revision begrenzt deren
Sichtbarkeitsbedingung die Gruppe auf KillAura; sie wird deshalb nicht als
fehlende ursprüngliche Scaffold-Einstellungsgruppe gezählt. In Bedingungen
referenzierte Namen wie None/Polar erweitern auch nicht die tatsächlich
registrierte Auswahl Linear/Relative. [D2]

## Eigene Clutch-Grenzen und Eingabeanbindung

Die neue Clutch-Projektion verwendet aktuelle Position und tatsächliche
Bewegung. Sie reicht höchstens drei Ticks beziehungsweise zwei Blöcke horizontal
und drei Blöcke vertikal voraus. Aufsteigende Sprünge mit deutlicher
Aufwärtsbewegung werden nicht als Rettungsfall erfasst; bei Bodenkontakt wird
keine fiktive vertikale Fallbewegung angenommen. Auch ein schneller Fall darf
eine aktuell noch erreichbare Fläche benutzen, wenn die weitergehende Projektion
bereits räumlich begrenzt wird. Das ist eine eigene physische Kurzprojektion,
nicht die unveränderte originale Prediction-Abfrage. [N7]

Der Prediction-Nachlauf umfasst vier beobachtete Ticks einschließlich des
Auslösers. Die Sneak-Anforderung hält höchstens zwei Ticks einschließlich des
Auslösers an. Diese Fenster entsprechen nicht unter allen Bedingungen den
ursprünglichen Zeitmessern. Erst ein gültiger Plan kann eine Sneak-Anforderung
setzen; der Eingabepfad verlangt zusätzlich aktuelle Szene, Bodenkontakt und
passende wirkliche Eingaben. Er hält keine globale Taste fest. [N1, N7]

Normales Bauen berücksichtigt Stillstand sowie kamerabezogene Vorwärts-,
Rückwärts-, Seitwärts- und Diagonaleingaben. Kamerawinkel und Zielrotation sind
getrennt: Bewegungsvorschau verwendet die Eingabebasis, Trefferprüfungen die
angenommene Zielrotation. Die Kamera bleibt zwischen Ticks frei bedienbar;
eine innerhalb des vorbereiteten Schritts geänderte Basis verwirft nur den
veralteten Plan. GUI, Szenenverlust und konkurrierende Aktionen behalten Vorrang.
[N1–N2, N6–N7, N10–N11]

## Konfigurationskompatibilität

Die ersten zehn bisherigen NScaffold-Werte bis Smart Disable behalten ihre
Positionen und Identitäten. In Visible Rotation bleiben Standard/Godbridge die
Indizes 0/1; Center/Reverse sind angehängt. Der historische Name und die ID
`visible_rotation` bleiben für alte Profile erhalten; die Ausrichtung läuft
auch mit diesen Profilen still und mit MoveFix. Die Erweiterung beginnt erst danach
mit Build Mode. Neue Instanzen starten weiterhin mit Normal/Standard und
**Clutch aus**, **Advanced Rotations aus**. [N1]

Beim Import eines Profils mit einem NScaffold-Eintrag werden darin fehlende Erweiterungswerte auf ihre
Defaults gesetzt; explizit gespeicherte neue Werte bleiben maßgeblich. Ein
älteres Profil erbt deshalb nicht unbemerkt Clutch, Telly oder Advanced Rotations
aus einem vorher geladenen Profil. Die vorhandenen alten Einstellungen werden
dabei nicht auf neue Defaults zurückgesetzt. [N9]

## Herkunft und Laufzeitunterschiede

Die Referenz enthält neben normalen Bauabläufen auch auf Serverprüfungen
zugeschnittene Bewegungs- und Paketvarianten. Diese Varianten sowie der globale
Slot-, Paket- und Eingabeverbund wurden nicht portiert. Die Tabellen grenzen die
betroffenen Gruppen ab und dokumentieren keine Umgehungsverfahren. Eine Aussage
über geringere Erkennungs- oder Flag-Raten ist damit nicht belegt. [D1–D4, N1–N8]

Demise beschreibt in der README Java/JDK 17 und einen Gradle-Build. Neutron setzt
Java-Quell- und Zielversion 1.8 und verwendet seinen bestehenden Java-8-Workflow.
Ereignis-, Wert-, Rotations- und Controller-Schnittstellen unterscheiden sich
zusätzlich. Das bloße Einfügen der Originaldateien wäre kein abgeschlossener
Port. [D3, N5]

Der Nutzer hat die Nutzung der Quelle bestätigt. Die gelesene Demise-README
nennt AGPL-3, Neutrons bestehende [LICENSE](../LICENSE) nennt GPL v3. Dieser
Herkunftsnachweis hält diese Angaben fest; er erklärt weder die Lizenzen für
identisch noch zusätzliche Nutzungsbedingungen für entfallen. [D3]

## Historie und Aussagegrenzen

Der frühere Phase-2-Stand wurde nach einem erneuten ausdrücklichen Mergeauftrag
als abgegrenzte Implementierung über
[PR #181](https://github.com/YoungJibbit95/Neutron-Client/pull/181) mit Commit
[`fa24c93386daf61077772862b220f27817e07a99`](https://github.com/YoungJibbit95/Neutron-Client/commit/fa24c93386daf61077772862b220f27817e07a99)
in main integriert. Die zuvor in diesem Dokument enthaltene Aussage, jener PR
bleibe ohne Merge-Freigabe als Draft offen, beschrieb einen überholten Zwischenstand.

Die vorliegende Erweiterung umfasst die anschließenden Aufträge für die
fehlenden Modus-, Clutch- und Rotationseinstellungsgruppen sowie die stille
Rotation mit MoveFix. Sie ergänzt die hier als adaptiert beschriebenen Funktionen;
sie erneuert keine Behauptung vollständiger Demise-Gleichheit. Insbesondere fehlen
weiterhin die ursprüngliche Modusauswahl für Movement Correction,
Sprint-/Speed-/Tower-Systeme, mehrere Addons und der globale Originalverbund.
Auch die hinzugefügten Modi und Clutch-Abläufe besitzen die erläuterten Unterschiede.

Ausgeführte Tests, aktuelle CI-Ergebnisse und der Veröffentlichungsstand gehören
in das jeweilige Abnahmeprotokoll.
Quellvergleich und vorhandene Testfälle ersetzen keine Live-Spiel- oder
Serverprüfung.
