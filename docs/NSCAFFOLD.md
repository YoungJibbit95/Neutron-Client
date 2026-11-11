# NScaffold: sichtbare Bauhilfe

NScaffold ist ein eigenständiges Modul in der Kategorie **World**. Es richtet die
tatsächliche Kamera auf eine erreichbare Platzierungsfläche aus und verwendet
den normalen Minecraft-Controller. Die Kamera bleibt die normale Bewegungsbasis;
Hotbar-Auswahl, Drehung und Handanimation sind sichtbar. Der folgende Stand
ergänzt die Bau-Modi, Clutch-Hilfe und Rotationsoptionen auf der in
[PR #181](https://github.com/YoungJibbit95/Neutron-Client/pull/181) integrierten
Grundlage. Er ist weiterhin **kein vollständiger oder identischer Demise-Port**.
Die Herkunft und die verbleibenden Unterschiede stehen im
[Funktionsvergleich](NSCAFFOLD-DEMISE-PARITY.md).

## Bedienung und Vorrang manueller Eingaben

Normales Weiterbauen ist im Stillstand oder mit **S**, bei Bedarf zusammen mit
**A/D**, vorgesehen. **W** und reine Seitwärtseingabe pausieren den normalen
Bauplan mit **Hold back to bridge**. Diese reine Richtungspause erhält die
beobachteten Boden-/Flugticks: Eine anschließend benötigte Clutch-Hilfe und ein
Telly-Flugfenster verlieren dadurch nicht ihre bereits beobachtete Flugphase.
Ein aktivierter Clutch-Plan kann auch bei Vorwärts- oder Seitwärtsbewegung
übernommen werden, wenn er eine tatsächlich erreichbare Rettungsfläche findet.

**Breezily benötigt die seitlichen Eingaben des Spielers; Telly benötigt dessen
normalen Sprung.** NScaffold setzt keine automatischen Sprint-, Sprung- oder
Strafe-Sequenzen. Die einzige optionale Eingabehilfe ist **Auto Sneak on Clutch**:
Sie ergänzt im laufenden normalen Eingabeereignis Sneaken mit dessen üblicher
Verlangsamung und hält keine globale Taste fest.

Eine manuelle Kameradrehung pausiert mit **Manual view** und löst die gehaltene
Richtung. Solange die Kamera weiter manuell bewegt wird, fordert das Modul keine
eigene Drehung oder Platzierung dagegen an. Bei ruhender Kamera kann ein neuer
Plan aus der sichtbaren Ausgangsrichtung entstehen. Widersprüchliche oder
abgebrochene Eingabeereignisse verwerfen einen vorbereiteten Plan.

Der bestehende **Scaffold** hat Vorrang. Ein aktives Aura-Ziel, eine Aura-Aktion
im aktuellen Tick oder laufende Itembenutzung pausiert NScaffold ebenfalls.
GUI, Fokusverlust, Tod, Spectator, Reiten und ungültige Szenen verhindern
automatische Aktionen. Nach einem Smart Disable bleibt das Wiedereinschalten
eine ausdrückliche Nutzeraktion.

## Bau-Modi und bisherige Einstellungen

Die folgenden Werte werden in
[NScaffold.java](../src/main/java/me/youngjibbit/module/impl/world/NScaffold.java)
registriert. Sichtbarkeitsbedingungen blenden Einstellungen aus, die im
gewählten Modus keine Wirkung haben.

| Einstellung | Standard / Bereich | Wirkung |
| --- | --- | --- |
| **Build Mode** | `Normal`; außerdem `Breezily`, `Telly` | Normal baut ohne zusätzliche Flugphase. Breezily verwendet seine feste sichtbare Winkelpräferenz. Telly wartet beim normalen Bauen auf das gewählte Flugfenster. |
| **Straight Telly Ticks** | 4; 1–5 | Benötigte beobachtete Flugticks bei gerader Bewegung. Nur bei Telly sichtbar. |
| **Diagonal Telly Ticks** | 2; 1–5 | Benötigte beobachtete Flugticks, wenn Vorwärts-/Rückwärts- und Seitwärtsachse gleichzeitig benutzt werden. |
| **Visible Rotation** | `Standard`; außerdem `Godbridge`, `Center`, `Reverse` | Bestimmt das sichtbare Rotationsziel in Normal und Telly. Breezily verwendet stattdessen seine eigene Geometrie. |
| **Static-ify** | Aus | Fordert bei Godbridge oder Reverse die feste Winkelpräferenz selbst an. Fehlt auf diesem Strahl eine benötigte reale Fläche, wartet der normale Planer. |
| **Turn Speed** | 45°; 1–180° | Bisherige gemeinsame Winkelgrenze für Yaw/Pitch pro regulärem Bewegungsschritt. Gilt bei ausgeschalteten Advanced Rotations. |
| **Godbridge Pitch** | 75,7°; 65–89° | Abwärtspräferenz bei Godbridge. Mit Static-ify ist dies beim Bewegen der feste angeforderte Pitch; im Stillstand gilt dann 90°. |
| **Look Ahead** | 0,65; 0–1 Block | Vorschau entlang der aktuellen normalen Bewegung. Vergrößert keine Controller-Reichweite. |
| **Keep Height** | Aus | Hält die begonnene Zielebene bis höchstens drei Ebenen unter der aktuellen Position. Beim Unterschreiten oder größerem Höhenwechsel wird sie neu erfasst. |
| **Auto Select Blocks** | An | Erlaubt normalen sichtbaren Wechsel zu einem brauchbaren Hotbar-Stack. Ausgeschaltet ist nur der bereits ausgewählte geeignete Stack zulässig. |
| **Placement Delay** | 0; 0–500 ms | Mindestzeit zwischen Controller-Versuchen. Das gemeinsame Budget von höchstens einem Platzierungsversuch pro äußerem Client-Tick bleibt zusätzlich verbindlich. |
| **Swing** | An | Normale sichtbare Handanimation nach lokal erfolgreicher Platzierung. |
| **Smart Disable on Death** | Aus | Schaltet das Modul bei Tod aus. |
| **Smart Disable on World Change** | Aus | Schaltet bei beobachtetem Welt-, Spieler- oder Verbindungswechsel aus; keine Same-world-Lobby-Heuristik. Alte Pläne werden unabhängig davon verworfen. |

### Telly und tatsächliche Flugticks

[NScaffoldModeController](../src/main/java/me/youngjibbit/core/world/NScaffoldModeController.java)
zählt die in diesem Modulablauf beobachteten aufeinanderfolgenden Boden- und
Flugticks. Während Telly am Boden oder vor dem gewählten Flugtick wartet,
bleiben die Zähler erhalten. Landen setzt den Flugzähler zurück; mehrere kurze
Sprünge ergeben daher kein gemeinsames langes Flugfenster. Ein Wechsel zwischen
geraden und diagonalen Eingaben verwendet den entsprechenden Schwellwert, ohne
einen zusätzlichen Flugtick zu erzeugen.

Identische wiederholte Ereignisse desselben Ticks zählen nicht erneut.
Widersprüchliche Wiederholungen, rückläufige Ticknummern und ungültige Eingaben
erteilen keine Freigabe. Fehlende Beobachtungen starten die Folge konservativ
neu. Ein Einschalten mitten im Flug übernimmt deshalb nicht rückwirkend die
gesamte bisherige Flugzeit. Toggle-/Config-Resets kaufen keinen zweiten Schritt
im gleichen Tick; eine tatsächlich neue Szene darf eine neue Tickfolge beginnen.

Ein gültiger **Clutch-Plan hat Vorrang vor dem Telly-Wartefenster**. Diese
Rettungspriorität ist eine eigene Neutron-Regel und kein Nachweis gleicher
Telly-Abläufe wie in Demise.

### Zielgeometrie und feste Winkel

Der [normale Planer](../src/main/java/me/youngjibbit/core/world/NScaffoldPlanner.java)
prüft die benötigte Zelle unter dem Spieler oder den nächsten kleinen Schritt
entlang der Bewegung. Bei diagonalen Übergängen kommen höchstens zwei
Verbindungszellen hinzu. Je Zelle werden fünf Nachbarflächen betrachtet; damit
bleibt die gewöhnliche Suche auf **15 neue Flächenprüfungen** begrenzt.
Gehaltene Pläne und vorhandene Blickstrahlen werden zusätzlich gezielt geprüft.
Es gibt keinen rekursiven Radius-Scan.

- **Standard** hält einen brauchbaren Flächentreffer. Im Stillstand bleibt ein
  gültiger konkreter Punkt während der sichtbaren Annäherung erhalten. Beim
  Rückwärtslaufen wird dieselbe Fläche aus einer stabilen Abwärtspräferenz von
  75,7° korrigiert, damit ein früher steiler Winkel nicht unter die nächste Fläche
  zeigt. Ein bereits gültiger Strahl behält seine tatsächlichen Winkel.
- **Godbridge** bevorzugt eine bewegungsbezogene 45°-Richtung und den
  eingestellten Abwärtswinkel. Ohne Static-ify darf eine passende reale Fläche
  begrenzte Korrekturen erfordern. Die bevorzugte Richtung bleibt über gewöhnliche
  Blockübergänge erhalten.
- **Center** fordert den Mittelpunkt der tatsächlichen Stützflächen-Bounds an.
  Ein bereits sichtbarer Randpunkt ersetzt diesen Mittelpunkt nicht. Beim
  Bewegen können deshalb größere sichtbare Zielkorrekturen nötig werden.
- **Reverse** bevorzugt eine Richtung entgegen der aufgenommenen Bewegung und
  behält eine nutzbare reale Fläche bei. Ohne Static-ify ist das eine geometrische
  Präferenz mit begrenzter Korrektur. Mit Static-ify wird ein fester Pitch von
  **80°** angefordert.
- **Breezily** verwendet ein 45°-Raster mit **80°** Pitch bei gerader Bewegung,
  **75,7°** diagonal und **90°** im Stillstand. Die seitlichen Eingaben bleiben
  manuell. Die eigene sichtbare Drehung wird nicht fortlaufend als neue Richtung
  zurückgeführt.

Standard/Godbridge und dynamisches Reverse begrenzen die Korrektur bei gehaltener
Rückwärtseingabe auf 18° um die aufgenommene Richtung. Center ist eine ausdrückliche
Mittelpunktanforderung und hat diese Richtungsbegrenzung nicht. Feste Breezily-
beziehungsweise Static-ify-Ziele werden nur angeboten, wenn ihr **eigener
Wunschstrahl** bereits eine benötigte reale Fläche auf der begrenzten Route trifft.
Andernfalls gibt es für diesen normalen Bauplan kein beliebiges Ersatzziel.

**Rotationsziel und Platzierungspunkt sind unterschiedliche Schritte.** Center
oder ein fester Winkel bezeichnet das angeforderte Ziel. Die sichtbare
Interpolation kann mehrere Ticks brauchen. Sobald ihr tatsächlich angenommener
Zwischenwinkel dieselbe zulässige Stützfläche trifft, darf der normale
Controllerpfad bereits dort platzieren. Es wird also weder eine Platzierung
mit einem nur gedachten Winkel erlaubt noch garantiert, dass jede Platzierung
erst beim numerisch exakt erreichten Zielwinkel erfolgt.

## Clutch-Hilfe

**Clutch ist standardmäßig aus.** Der
[Clutch-Planer](../src/main/java/me/youngjibbit/core/world/NScaffoldClutchPlanner.java)
prüft eine kurze körperbezogene Route aus aktueller Position und tatsächlicher
Bewegung. Er fordert keine Bewegungsgeschwindigkeit an und erweitert die
normale Reichweite nicht.

| Einstellung | Standard | Wirkung |
| --- | --- | --- |
| **Clutch** | Aus | Aktiviert die zusätzliche Rettungsplanung vor dem gewöhnlichen Bauplan. |
| **Clutch Criteria** | `MouseOver` | MouseOver reagiert, wenn kein brauchbarer aktueller Strahl auf die Rettungsroute zeigt oder der Spieler stillsteht. Prediction berücksichtigt bevorstehende Lücken beziehungsweise einen aktuellen ungestützten Fall. |
| **Clutch Rotation Mode** | `Center` | Center benutzt den realen Flächenmittelpunkt. Normal bevorzugt einen brauchbaren Blickstrahl beziehungsweise einen nahen sichtbaren Punkt und kann dessen Winkel halten. |
| **Fast Clutch Always** | Aus | Behandelt jeden gefundenen Clutch-Plan als dringlich. Nur mit Advanced Rotations wirksam und sichtbar. |
| **Fast Clutch on Miss** | Aus | Dringlichkeit, wenn der aktuelle Strahl nicht dieselbe gewählte Stützfläche trifft. |
| **Fast Clutch Prediction** | Aus | Dringlichkeit bei vorausliegender Lücke oder einem aktuellen ungestützten Fall. Die drei Fast-Schalter können kombiniert werden. |
| **Auto Sneak on Clutch** | Aus | Fordert nach einer gültigen Clutch-Planung eine kurze normale Sneak-Unterstützung an. Der Eingabepfad prüft Szene, Bewegung und Bodenkontakt erneut. |
| **Ground Ticks to Sneak** | 5; 0–10 | Vor einer solchen Anforderung benötigte beobachtete zusammenhängende Bodenticks. |

Die Suche beginnt nur bei einer ersetzbaren Zelle zwei Blöcke unter der
Spielerposition und einer aktuellen oder kurz vorausliegenden Lücke. Ein
aufsteigender Sprung mit noch deutlicher Aufwärtsbewegung wird nicht als
Clutch-Fall behandelt. Am Boden bleibt die Projektion auf gleicher Höhe, sodass
die Schwerkraft keine fiktive Lücke in durchgehendem Boden erzeugt.

Die Projektion ist auf **drei Ticks**, **zwei Blöcke horizontal** und **drei
Blöcke vertikal** begrenzt. Sie enthält höchstens sieben konkrete Zielzellen
einschließlich nötiger diagonaler Verbindungen und damit höchstens **35 neue
Flächenprüfungen**. Bei einem schnellen Fall bleibt die aktuelle ungestützte
Zelle prüfbar, auch wenn weitere Projektion die räumliche Grenze überschreiten
würde. Bereits gehaltene Strahlen und Validierungen sind separate Prüfungen.
Findet Clutch keinen Plan und folgt im selben Planungsdurchlauf die gewöhnliche
Suche, können beide Budgets zusammen höchstens 50 neue Flächenprüfungen ergeben.
Das ist keine Obergrenze für sämtliche zusätzlichen Strahl- und Validierungsabfragen.

Prediction darf einen noch gültigen Plan kurz halten: vier Ticks einschließlich
des auslösenden Ticks. Eine erneute tatsächliche Auslösung erneuert dieses
Fenster. Die optionale Sneak-Anforderung hat einen Nachlauf von zwei Ticks
einschließlich des auslösenden Ticks. Diese tickbezogenen Fenster sind keine
identische Kopie der wall-clock-Timer des Originals.

Die Sneak-Hilfe wirkt nur bei weiterhin gültigem Kontext, tatsächlicher Bewegung
und aktuellem Bodenkontakt. Ein abgebrochenes Eingabeereignis, bereits manuell
gesetztes Sneaken, Springen, manuelle Kamerabewegung oder ein Modulkonflikt hat
Vorrang. Bei Anwendung wird die normale Vorwärts-/Seitwärtsachse mit 0,3
skaliert. Es wird keine Taste über mehrere Ereignisse festgehalten.

Ein Clutch-Ziel bleibt an reale Blockformen, erste Weltfläche, passenden Stack,
normale Controller-Reichweite und aktuelle Szene gebunden. Es gibt keine
Platzierung durch eine verdeckende Fläche oder außerhalb der Reichweite. Ein
gefundenes Ziel und ein erfolgreicher lokaler Controller-Aufruf garantieren
weder eine Rettung noch eine Serverbestätigung.

## Erweiterte Rotationseinstellungen

**Advanced Rotations ist standardmäßig aus.** Solange es aus ist, gilt der
bisherige Turn-Speed-Pfad. Aktiviert verwendet es den lokalen
[NScaffoldRotationController](../src/main/java/me/youngjibbit/core/world/NScaffoldRotationController.java).
Die Geschwindigkeitseinstellungen sind **Grad pro beobachtetem Spieler-Tick**,
nicht Maus-Pixel pro Frame. Er erhält pro Tick höchstens eine neue Anfrage;
wiederholte Ereignisse würfeln weder Geschwindigkeiten neu noch geben sie ein
zweites Drehbudget frei.

| Einstellung | Standard / Bereich | Wirkung |
| --- | --- | --- |
| **Smooth Mode** | `Linear`; außerdem `Relative` | Linear verteilt den Schritt entlang des verbleibenden Yaw-/Pitch-Wegs. Relative beginnt mit 25 % des jeweiligen Restwinkels und begrenzt ihn anschließend auf die Achsgeschwindigkeit. |
| **Extra Smoothing Factor** | 0,15; 0,01–1 | Multipliziert den berechneten Schritt. Kleinere Werte bremsen stärker; 1 fügt diese zusätzliche Verlangsamung nicht hinzu. |
| **Yaw Rotation Speed (min/max)** | Je 180°; 0,01–180° | Zufällig gewählte Basisgrenze für die horizontale Achse pro neuer Tick-Anfrage. |
| **Pitch Rotation Speed (min/max)** | Je 180°; 0,01–180° | Unabhängige Basisgrenze für die vertikale Achse. Vertauschte Min-/Max-Werte werden intern geordnet. |
| **Accelerate** | Aus | Mischt vorherige und aktuelle Winkeländerung, getrennt nach Achse. |
| **Yaw / Pitch Accel Factor** | Je 0,25; 0,01–0,9 | Gewicht des vorherigen Schritts. Ein größerer Wert bedeutet mehr Trägheit, nicht sofort höhere Geschwindigkeit. Gegenläufige oder überschießende Schritte werden begrenzt. |
| **Imperfect Correlation** | Aus | Variiert die beiden Achsgrenzen unabhängig um bis zu ±10 % ihres gewählten Basiswerts. Die jeweiligen eingestellten Maxima bleiben bindend. |
| **Short Stop** | Aus | Ermöglicht kurze Pausen nach tatsächlich auf der Kamera beobachteter Drehstrecke. Nicht ausgeführte Vorschläge bauen keine Strecke auf. |
| **Short Stop Duration (ms)** | 50; 25–1000 ms | Pausenlänge nach monotoner Zeit. Die Fortsetzung erfolgt bei einer späteren regulären Tick-Anfrage. |
| **Rotation Diff Buildup to Stop** | 180°; 50–720° | Aufsummierte tatsächlich beobachtete Winkelstrecke bis zur nächsten Schwelle. |
| **Max Threshold Attempts to Stop** | 1; 0–5 | Anzahl übersprungener Schwellenüberschreitungen vor einer Pause. 0 pausiert bereits an der ersten Schwelle. |

Minima sind keine garantierte Mindestbewegung: Restwinkel, Wegverteilung,
Glättungsfaktor, Trägheit und optionale Variation können den tatsächlichen
Schritt verkleinern. Die jeweiligen **Maxima bleiben harte Achsgrenzen**. Yaw
nimmt den kurzen Winkelweg; Pitch bleibt innerhalb seiner normalen Grenzen.
Ein bereits passend gehaltener Strahl bleibt exakt erhalten und löscht nicht
mehr benötigte Interpolations- und Pausenhistorie.

Ein dringlicher Clutch-Plan verwendet die eingestellten Maxima und übergeht
optionale Short Stops, Relative-Easing, zusätzliche Verlangsamung, Trägheit und Achsvariation.
Er überschreitet diese Maxima nicht. **Fast Clutch ist daher keine Zusage einer
sofortigen oder unbegrenzt schnellen Drehung.** Ohne Advanced Rotations bleibt
auch für Clutch der normale Turn-Speed-Wert maßgeblich.

## Bestehende Konfigurationen

Die bisherigen **ersten zehn Einstellungen** bis einschließlich der beiden
Smart-Disable-Werte behalten ihre Reihenfolge und Identitäten. Die alten
Visible-Rotation-Indizes 0/1 bleiben Standard/Godbridge; Center und Reverse
wurden angehängt. Alle weiteren Erweiterungswerte beginnen erst mit Build Mode.

Beim Laden eines älteren Profils mit einem NScaffold-Eintrag setzt
[ConfigManager](../src/main/java/me/youngjibbit/config/ConfigManager.java) darin
fehlende Erweiterungsfelder auf ihre jeweiligen Defaults zurück. Ein zuvor
geladenes Profil kann deshalb keine versteckte Clutch-, Telly- oder Advanced-
Rotations-Einstellung in ein altes Profil hineintragen. Explizit gespeicherte
neue Werte gewinnen. Die bestehenden Turn-Speed- und übrigen alten Werte
werden dadurch nicht auf neue Defaults umgestellt.

## Island und TargetESP

NScaffold liefert der Island einen unveränderlichen Aktivitäts-Snapshot mit
Blocktyp, Metadaten, brauchbarem Hotbar-Blockvorrat und aktuellem Profil.
Searching, Aligning und Placing stammen aus den jeweiligen Modulschritten;
Waiting und Paused kennzeichnen Warten beziehungsweise Unterbrechung. Telly-Warten
wird als Waiting, Clutch-Ausrichtung als Aligning dargestellt. Das Profil nennt
`Breezily` beziehungsweise `Telly / <Visible Rotation>`.

Der Platzierungszähler steigt seit dem Einschalten nur nach einem lokal
erfolgreichen Controller-Aufruf in der weiterhin gültigen Szene. Suchen,
Drehen, abgelehnte Versuche und doppelte POST-Ereignisse erhöhen ihn nicht.
`getActivitySnapshot()` prüft ausschließlich den Cache, dessen maximale
Lebensdauer von 600 ms sowie begrenzte Identitäts-/Inventarbedingungen. Es
führt keine Zielsuche, Inventarsuche, Rotation oder Controller-Aktion aus.

TargetESP berücksichtigt NScaffold in **Active** und seiner eigenen Auswahl
**NScaffold**. **Scaffold** bleibt dem bisherigen Modul zugeordnet. Der Marker
folgt dem tatsächlich angenommenen Strahl ohne Positionsglättung und verändert
keine Ziele oder Aktionen. Die bestehenden Theme-, Glow-, Reduced-Motion- und
Tiefenoptionen bleiben für diese reine Beobachtung maßgeblich.

## Herkunft, Unterschiede und Prüfung

Referenz ist die vom Nutzer zur Nutzung freigegebene Demise-Quelle bei Commit
`e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`:

- [Scaffold.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/Scaffold.java), Blob `f5cbead2aa1d666af66946173fa358b626ac9244`.
- [RotationManager.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationManager.java), Blob `e17d1fe4a7cd0702025e3f870f57ab454b91f3a2`.
- [RotationHandler.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationHandler.java), Blob `29555b7cfca3b1e5cccad94e4d5ed06902784a31`.

Die Neutron-Implementierung übernimmt verwandte Einstellungen und geometrische
Ideen in den vorhandenen Controllerpfad. Sie verwendet eine eigene begrenzte
Suche, physische Clutch-Kurzprojektion und tickbezogene sichtbare Interpolation.
Der globale Demise-Slot-/Paket-/Eingabeverbund, verborgene Rotation,
Bewegungskorrektur und prüfungsspezifische Varianten wurden nicht importiert.
Sprint-, Tower-, blockzählende Sprung-/Sneak- und weitere Originaloptionen bleiben
im [Funktionsvergleich](NSCAFFOLD-DEMISE-PARITY.md) als fehlend oder abweichend
ausgewiesen. Gemeinsame Namen belegen keine identischen Spielabläufe.

Die gelesene Demise-README nennt AGPL-3; Neutrons bestehende Lizenzdatei nennt
GPL v3. Dieser Herkunftsnachweis ersetzt oder erweitert diese Angaben nicht.

Die Testquellen umfassen die bisherigen Planer-, Placement- und Modulabläufe
sowie `NScaffoldModeControllerTest`, `NScaffoldModesPlannerTest`,
`NScaffoldClutchPlannerTest` und `NScaffoldRotationControllerTest`. Sie behandeln
unter anderem Tickbudgets, Flug-/Bodenfolgen, echte Flächen und Reichweite,
fest angeforderte Winkel, Lifecycle-Abbruch, Achsgrenzen und Short Stops.
Die vorhandene Mehrtick-Geometrieprüfung für Standard/Godbridge mit S, S+A und
S+D verwendet keine vollständige vertikale Spielphysik. Synthetische Fixtures
ersetzen keine Live-Spielprüfung mit realer Kollision und Eingabe.

Ausgeführte Ergebnisse gehören zum jeweiligen CI-/Abnahmeprotokoll. Dieses
Dokument behauptet für die Erweiterung weder bereits bestandene CI-Läufe noch
eine abgeschlossene Spiel- oder Serverabnahme.
