# NScaffold: Demise Scaffold in Neutron

NScaffold unter **World** verwendet den Scaffold-Ablauf aus
[DemisePrivate, Commit e219a97](https://github.com/YoungJibbit95/DemisePrivate/tree/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb).
Rotation, MoveFix und die Bewegungsmodi stammen weiterhin aus diesem Port.
Auf den anschließenden Nutzerauftrag wurden RayTrace-Planung, Platzierungszeitpunkt,
CPS und Kantenschutz gezielt erweitert. Die Folgekorrektur erhält Rotationen
zwischen Platzierungen und ergänzt nötige Zwischenschritte beim diagonalen Bridgen.
Quellen und Abweichungen stehen in
[NSCAFFOLD-DEMISE-PARITY.md](NSCAFFOLD-DEMISE-PARITY.md).

## Rotation und Kamera

**Silent** ist standardmäßig an. Der Mausblick bleibt frei; Rotation für Bewegung
und ausgehende Spielerpakete sowie Kopf-/Körperdarstellung folgen Demise. Die
Spielerhaltung ist in dritter Person sichtbar. Die normale Kamera wird nicht zum
Platzierungsblock gezwungen.

**Movement correction** verwendet auf ausdrücklichen Nutzerwunsch den Default
**Silent**; Demise selbst hat `None`. Silent sucht diskret unter acht
Tastenkombinationen, vor der normalen Sneak-Verlangsamung. Strict lässt die Tasten
unverändert und verwendet Rotations-Yaw für Strafe/Sprung. None korrigiert beides
nicht. Es gibt kein Advanced-Rotations-Gate mehr.

Die Originalregler für Linear/Relative, Extra smoothing factor, separate
Yaw-/Pitch-Geschwindigkeiten, Accelerate, Imperfect correlation und Short stop
sind direkt wirksam. Geschwindigkeiten werden pro Update gezogen. Pro Frame wird
zuerst das bisherige Ziel integriert, dann ein neues Scaffold-Ziel berechnet und
anschließend die physische Mausbewegung auf die Kamera angewendet, auch bei
Mausdelta null.

Hat NScaffold bereits einen Zielwinkel angefordert, bleibt diese Rotationsabsicht
getrennt vom gerade verfügbaren Klickplan erhalten. Ist die Fußzelle nach einer
Platzierung belegt, kann die Drehung deshalb weiterlaufen, etwa beim stehenden
Tower. Voraussetzung ist eine frisch geprüfte, geladene und geeignete Stütze
unter dem Spieler oder der weiterhin erreichbare letzte Stützblock. Dabei wird
der letzte angeforderte Zielwinkel erneut verwendet; die laufende Glättung behält
ihren Verlauf. Erst ein neuer gültiger Klickplan kann eine weitere Platzierung
erlauben. Ohne nachweisbare Stütze wird die Rotationsabsicht nicht weiter erneuert.

Telly erneuert das Ziel weiterhin erst ab seiner Flugtick-Schwelle; ein aktives
Aura-Ziel unterbricht die Erneuerung ebenfalls. Beim Ausschalten stoppt die
Zielaktualisierung. Erst nach mehr als 150 ms seit der letzten Anforderung beginnt
die originale Rückführung. Sie muss nach 150 ms nicht abgeschlossen
sein: Ganzzahl-Mausquantisierung kann bei kleinen diagonalen Restwinkeln
stehenbleiben. Dieser Quellfall bleibt erhalten; es gibt keinen neuen Timeout.
Profiländerungen, Ein-/Ausschalten und Szenenwechsel verwerfen die gespeicherte
Rotationsabsicht. Der beschriebene normale Ausschalt-Nachlauf bleibt davon getrennt;
ungültige Szenen und Verbindungen verwerfen auch die alte Engine-Pose.

## Brücke und Clutch

| Einstellung | Originalverhalten |
| --- | --- |
| Mode: Normal | Normales Bridgen mit der unten beschriebenen Platzierungsplanung. |
| Mode: Breezily | 45°-Ausrichtung, Pitch-Auswahl und seitliche Eingaben anhand Blickrichtung/Blockmitte. |
| Mode: Telly | Automatischer Sprung unter den Quellbedingungen; Rotation/Platzierung ab aufeinanderfolgenden Flugticks. |
| Straight / Diagonal telly ticks | Standard 4 / 2, Bereich 1–5, Richtung aus Demises Keybind-Yaw. |
| Rotations: Normal / Center / GodBridge / Reverse | Bei RayTrace: validierte Flächenpunkte bzw. echte Flächenmitte; originale Seitenauswahl bzw. Bewegungs-Yaw minus 180° für GodBridge/Reverse. |
| Static-ify | Originale feste Pitch-Werte für GodBridge/Reverse. |

Die RayTrace-Suche verwirft belegte Zielzellen, unerreichbare Stützen und verdeckte
Flächen. Sie prüft tatsächlich den gerade berechneten Strahl und verwendet eine
begrenzte Anzahl von Flächenpunkten sowie deduplizierte Nachbarpositionen.
Eine schon passende Ausrichtung wird bevorzugt. Die Vorschau auf den nächsten
Schritt bereitet die Drehung vor, ohne eine Platzierung zu bestätigen.
Die Wahl der nächsten Zielzelle folgt der beabsichtigten, begrenzten Bewegung
auch dann, wenn der Kantenschutz den tatsächlichen Schritt gerade anhält.
Überquert die Vorschau mehrere Zellgrenzen, darf sie die nötigen orthogonalen
Zwischenschritte im direkten Rechteck zwischen Ausgangs- und Zielzelle wählen.
Das umfasst diagonale Übergänge und Schritte über zwei Zellen bei höherer
Geschwindigkeit. Diese Vorschau bleibt auf derselben Bauhöhe und gibt keine
beliebigen seitlichen oder tiefer liegenden Ausweichzellen frei.

Für den beabsichtigten Laufpfad zählt die Entfernung von der tatsächlichen
Spielerposition zur Mitte der Zielzelle. Das gilt sowohl für die frühe Vorschau
(`findAhead`) als auch für die Suche bei bereits leerer Fußzelle und aktiver
physischer Bewegungseingabe (`findMoving`). Unter gleich nahen Kandidaten hat
ein bereits passender bevorzugter Strahl Vorrang. Nach der Erstausrichtung wird
unter den geprüften Alternativen die kleinste zusätzliche Drehung gewählt.
Ohne physische Bewegungseingabe behält die Recovery-Suche `find` ihre bisherige
Distanz- und Nachbarreihenfolge. Ein während der Suche gemerkter Kandidat wird
vor der Rückgabe erneut auf freie Zielzelle, unveränderte Stütze und gültige
Lebensdauer geprüft.

Bei geerdeter Bewegung ohne Sprungabsicht prüft die Vorschau außerdem, wo der
noch belegte Fußsupport tatsächlich endet. Kürzt der Kollisionsschutz diese
begrenzte Probe, verwendet die frühe Ausrichtung deren erreichbaren Endpunkt.
Die beabsichtigte Zielrichtung und die tatsächliche Bewegungsbegrenzung bleiben
eigenständig.

Sobald die Fußzelle leer ist, wird die Drehung aus der erwarteten, vom Kantenschutz
begrenzten nächsten Bewegungsposition geplant. Dabei wird berücksichtigt, ob
Minecraft die kleine Verschiebung im nächsten Spielerpaket überhaupt als neue
Position melden würde. Die Planung berücksichtigt damit auch den erwarteten
Ausgangspunkt eines an der Kante gestoppten Spielers. Die endgültige
Platzierungsprüfung verwendet weiterhin ausschließlich die später tatsächlich
angenommene Pose.

Bereits gültige Koordinaten auf der Fläche bleiben erhalten; nur außerhalb der
nutzbaren Fläche liegende Anteile werden nach innen korrigiert. Mit
**Rotations: Normal**, außerhalb von Breezily und bei gedrückten Bewegungstasten
wird ein rückwärts zur Eingaberichtung ausgerichteter Strahl bevorzugt, sofern
er die echte Fläche trifft. Das verhindert, dass ein
zufällig zuerst erreichter schräger Winkel dauerhaft seitliche Drift im diskreten
MoveFix erzeugt. Ein bereits angeforderter Pitch bleibt dabei die bevorzugte
Zielneigung. Der noch interpolierte Zwischenwert setzt dieses Ziel während einer
laufenden Drehung nicht fortwährend zurück. Weitere Flächenpunkte bleiben
begrenzte Alternativen.
Die eigentliche RayTrace-Abfrage bleibt in Minecrafts nativer Voxel- und
Blockgeometrie; ihre numerische Verlängerung überschreitet die Controller-Reichweite
nicht. Wenn ein belegbar gültiges geometrisches Ziel bereits in Demises
Ganzzahl-Totband liegt, der aktuelle Strahl aber noch verfehlt, fordert der Adapter
einen kleinen zusätzlichen Rotationsschritt an. Geschwindigkeit, Framezeit,
Glättung und Pausen folgen weiterhin der unveränderten Demise-Rechnung. Für
Accelerate berücksichtigt diese Wiederanlaufkorrektur auch die zweite
Ganzzahlrundung, nachdem ein tatsächlicher Mausframe ohne Rotationsfortschritt
beobachtet wurde. Der zusätzliche Beobachtungswert setzt bei einem Szenenwechsel
zusammen mit der Engine zurück.

Clutch bietet MouseOver/Prediction und Normal/Center. Die kombinierbaren
Originalkriterien erscheinen als Instant rot Always / On miss / Predict. Sie
setzen gezogene Achsgeschwindigkeiten auf 180; Short stop und Imperfect
correlation bleiben Teil der Rechnung. Prediction hat den originalen 200-ms-
Nachlauf, Auto-Sneak den 100-ms-Timer und Ground-Ticks. Sneak wird einmal normal
verlangsamt.

## Bewegung und Addons

| Gruppe | Auswahl / Funktion |
| --- | --- |
| Sprint mode | Normal, Silent, Ground, Air, None. Silent erhält lokalen Sprint und benutzt bei dessen Übergängen den originalen STOP-Paketpfad. |
| Speed mode | Tick, Exempt, Diagonal, None (Default), mit originalen Werten/Verzweigungen. |
| Tower / Tower move | Je Jump, Vanilla, PullDown, NCP; Default Jump, sichtbar in Mode Normal. |
| PullDown motion | Original-Float 0.95, Bereich 0.5–1. |
| KeepY / Speed keepY | Originale Bezugshöhe; Kopplung an aktives Speed-Modul. Physische Tower-Eingabe aktualisiert die Bezugshöhe. |
| Jump | Blockzähler, Blocks to jump (6), Only straight (an). |
| Sneak | Blockzähler, Blocks to sneak (7), Only sneak on ground (an). |
| Safewalk / Safewalk when no data | Zusätzlicher Vanilla-Kantenpfad bei Bodenkontakt bzw. fehlenden Daten; freigegebene Sprünge und Aufwärtsimpulse bleiben ausgenommen. |
| Swing | Default an; aus bedeutet nur Paketanimation. |
| RayTrace / Strict rayTrace | Echter Strahl aus der angenommenen Spielerpose, mit der aktuellen Controller-Reichweite. Support, Seite und Ziel müssen stimmen; Strict verlangt zusätzlich Abstand zum Flächenrand. |
| Target block ESP | Originaler Zielblockwürfel und projizierte Shader-Maske. |
| Hover | Originalregistrierung ohne ausführenden Consumer im gepinnten Java-Quellbaum; keine erfundene Zusatzfunktion. |

Die Zielblockfarbe folgt Neutrons Interface-Farbe. Die Shader-Masken benutzen die
vorhandenen globalen Blur-/Glow-Einstellungen und RenderState-Guards. Geometrie,
Alpha 100 für den Weltwürfel, schwarze Nicht-Glow-Maske und farbige Glow-Maske
stammen aus Demise. Das separate TargetESP bleibt eine andere Anzeige.

## Platzierung, CPS und Kantenschutz

| Einstellung | Verhalten / Default |
| --- | --- |
| Placement mode: Default | Ein gültiger Controller-Klick nach dem angenommenen Bewegungspaket, begrenzt durch CPS und höchstens einen Versuch pro Spielertick. Default. |
| Placement mode: Legit | Derselbe echte Blocktreffer plus die normale Rechtsklick-Pause von vier Spielerticks. RayTrace ist hier immer erforderlich. Kein Rechtsklick auf Luft oder Entities. |
| Min CPS / Max CPS | Ganze Werte von 1–20, Default 20 / 20. Pro Versuch wird ein Intervall innerhalb der sortierten Grenzen gewählt. |
| Edge safety: Stop | Default. Begrenzt nur den unsicheren Anteil der tatsächlich angeforderten horizontalen Bewegung. Ein durchgehend gestützter Weg bleibt unverändert. |
| Edge safety: Sneak | Sneak nur bei einer vorhergesagten ungesicherten Kante; die normale Sneak-Skalierung läuft einmal. Verbleibender Schwung wird zusätzlich durch dieselbe Bewegungsgrenze abgesichert. |
| Edge safety: Off | Schaltet den neuen Kantenschutz aus. Die separaten alten Safewalk-Schalter bleiben eigenständig. |

Die CPS-Werte begrenzen **Versuche**, auch wenn der Controller einen Versuch
ablehnt. Der erste gültige Treffer darf sofort platziert werden. Nach einer Pause
werden keine versäumten Klicks nachgeholt. Ein niedriger CPS-Wert, langsame
Rotationsregler, fehlende Blöcke oder eine andere aktive Rotationsquelle können
daher längere Wartezeiten an der Kante verursachen. In Legit begrenzt außerdem
die Pause von vier Ticks die Rate auf höchstens fünf Versuche pro Sekunde bei
normalen 20 Spielerticks/s. CPS garantiert keine Mindestanzahl erfolgreicher Blöcke.

Der Kantenschutz prüft reale Kollisionsflächen unter dem gesamten Fußabdruck
entlang des Weges. Ein geplantes Ziel oder ein abgeschickter Klick gilt noch
nicht als tragender Block. Erst die tatsächlich vorhandene lokale Stütze gibt
die Bewegung frei. Das gilt auch für eine bereits vorbereitete diagonale Drehung
und für eine zwischen Platzierungen gehaltene Rotation. Freiwillige Sprünge,
Telly-Sprünge, Tower-Aufwärtsbewegung,
Flug und bereits laufende Luftbewegung werden nicht auf den Boden gezwungen.
Der Schutz ist keine nachträgliche Rettung aus dem freien Fall.

## Slots und Lebensdauer

Der **letzte geeignete Hotbar-Slot** gewinnt, mit Demises Blacklist.
Hotbar-Markierung, Itemname und dargestellter Gegenstand zeigen den beim
Einschalten gespeicherten Slot; tatsächlich platziert wird der ausgewählte
Blockstack. Ausschalten stellt den gespeicherten Slot wieder her.

Am Anfang von EntityPlayerSP.onLivingUpdate werden Ziel und Rotationsgeschwindigkeit
vorbereitet. Der Klick folgt erst im POST des angenommenen Bewegungspakets:
RayTrace verwendet dessen wirklich berichtete Position und wirksame Rotation,
nicht einen noch ungesendeten Zielwinkel. NScaffold muss weiterhin Besitzer dieser
Rotation sein. Slot-, Welt-, Verbindungs-, Konfigurations- und Paket-Callbacks
werden vor dem normalen PlayerController-Klick und während dessen Transport erneut
validiert. Nur ein erfolgreicher lokaler Controller-Abschluss aktualisiert den
gemerkten vorherigen Block und die Platzierungszähler. Es gibt keinen zusätzlichen
Bewegungspaket-Pfad.
Unterbrochene/zurückgehaltene Simulationsschritte und ungültige Szenen erzeugen
keine Modulaktionen. Fehlende Stützblöcke führen zu Warten statt Nullzugriff.
Ein Weltwechsel schaltet NScaffold immer aus. Die bisherigen optionalen
Smart-Disable-Werte entfallen. Der Island-HUD liest weiterhin unveränderliche
Aktivitätsdaten und löst keine Suche oder Platzierung aus.

## Vorhandene Profile

Passende Altwerte werden übernommen: Build Mode → Mode, Visible Rotation →
Rotations, Standard → Normal, Keep Height → KeepY, Fast Clutch → Instant rot und
bisherige Short-Stop-Dauer → Duration. Neue Originalnamen haben Vorrang. Enthält
ein Profil NScaffold, werden fehlende Werte auf die dokumentierten Defaults gesetzt.

Turn Speed, Godbridge Pitch, Look Ahead, Auto Select Blocks, Placement Delay und
Advanced Rotations haben kein gleichwertiges Original-Gegenstück. Sie werden
als veraltet erkannt, ohne zusätzliche Ersatzrechnung. Fremde unbekannte
Auto-State-Daten bleiben erhalten. Float-Einstellungen werden an ihren
Original-Verwendungsstellen gecastet; es gibt keine zusätzliche Ganzzahlrundung.

## Nachweise

Tests prüfen unabhängige Original-Oracles für Rotation/MoveFix, echte
Produktions-Hooks für Kamera/Eingabe/Physik/Modell/Pakete, Geometrie/Inventar,
Modulabläufe mit echtem Controller, bewegte Brücken über mehrere Blöcke,
CPS-/POST-Grenzen, Kollisionsschutz, Konfigurationsmigration und native
Renderproben in CI. Synthetische Controller-/Pbuffer-Prüfungen sind keine
manuellen Tests einer laufenden Minecraft-Welt.

Die bewegten Brückenprüfungen verwenden je 100 Spielticks bei 30, 60 und 144 FPS
für Default und Legit. Die Erstausrichtung bleibt wegen Demises originalem
Framezeit-Limit von den Renderframes abhängig und wird gesondert zeitlich begrenzt.
Der Durchsatzvergleich verwendet für alle Fälle dieselben Ticks 21–100; reale
Treffer, freie Kamera, Bodenstütze, Gesamtstrecke und Klickreihenfolge werden
über den gesamten Lauf geprüft.

Zusätzliche Diagonaltests verwenden alle vier Kombinationen aus positiver und
negativer X-/Z-Bewegung, ebenfalls beide Platzierungsmodi und 30, 60 sowie 144 FPS.
Sie verlangen mindestens zwölf Blockeinheiten Fortschritt entlang der
beabsichtigten Diagonale, sechs auf jeder Achse und zwölf erfolgreiche
Platzierungen. In Ticks 21–100 darf kein Stillstand länger als drei Ticks dauern;
höchstens acht Stillstandsticks dürfen über die nachgewiesene Legit-Pause
hinausgehen. Für Default zählen dabei alle Stillstandsticks.

In Legit wird eine Wartezeit nur dann der normalen Vier-Tick-Pause zugerechnet,
wenn der angenommene Strahl die geplante Stützfläche trifft, das Ziel frei ist,
NScaffold die Rotation besitzt und die Rechtsklick-Pause noch läuft. Auch der
Tick mit einer tatsächlichen Platzierung zum frühestmöglichen Zeitpunkt zählt
zu diesen Wartezeiten: Seine Bewegung findet vor dem POST-Klick statt. Die
zusätzliche Wartezeit durch Legit wird damit für den einzelnen Tick belegt.
Fortschritt, längster Stillstand und übrige Stillstandsticks bleiben getrennte
Prüfbedingungen.

Der stationäre Tower-Test verwendet den Jump-Modus mit dessen
Sprungwartezeit, echten Sprung-, Reise- und Kollisionsmethoden sowie angenommenen
Bewegungspaketen. Er verlangt mehrere belegte Lücken von mehr als 150 ms ohne
Klickplan und prüft dort die aktive Zielerneuerung und den Pitch-Verlauf. Kamera
und vorhandene Blocksäule werden weiterhin getrennt geprüft. Weitere Fälle
prüfen Aura, Telly, Ausschalten und Epochenwechsel sowie ein Aura-Ziel, das erst
während der Stützblock-Abfrage entsteht.

Diese Beschreibung nennt den Prüfumfang, keine vorweggenommenen Erfolgszahlen.
Die Ergebnisse der jeweiligen integrierten Fassung stehen im zugehörigen Pull
Request. Die Bewegungsfälle verwenden synthetische Welten und lokalen Transport;
sie ersetzen keinen manuellen Spieltest und bestätigen keinen entfernten Server.
