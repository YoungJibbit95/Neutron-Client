# NScaffold: Demise Scaffold in Neutron

NScaffold unter **World** verwendet den Scaffold-Ablauf aus
[DemisePrivate, Commit e219a97](https://github.com/YoungJibbit95/DemisePrivate/tree/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb).
Rotation, MoveFix und die Bewegungsmodi stammen weiterhin aus diesem Port.
Auf den anschließenden Nutzerauftrag wurden RayTrace-Planung, Platzierungszeitpunkt,
CPS und Kantenschutz gezielt erweitert. Quellen und Abweichungen stehen in
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

Beim Ausschalten stoppt die Zielaktualisierung. Nach Ablauf des originalen
150-ms-Timers beginnt die Rückführung. Sie muss nach 150 ms nicht abgeschlossen
sein: Ganzzahl-Mausquantisierung kann bei kleinen diagonalen Restwinkeln
stehenbleiben. Dieser Quellfall bleibt erhalten; es gibt keinen neuen Timeout.
Ungültige Szenen und Verbindungen verwerfen alte Zustände.

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
Bereits gültige Koordinaten auf der Fläche bleiben erhalten; nur außerhalb der
nutzbaren Fläche liegende Anteile werden nach innen korrigiert. Bei Normal und
gedrückten Bewegungstasten wird ein rückwärts zur Eingaberichtung ausgerichteter
Strahl bevorzugt, sofern er die echte Fläche trifft. Das verhindert, dass ein
zufällig zuerst erreichter schräger Winkel dauerhaft seitliche Drift im diskreten
MoveFix erzeugt. Weitere Flächenpunkte bleiben begrenzte Alternativen.
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
die Bewegung frei. Freiwillige Sprünge, Telly-Sprünge, Tower-Aufwärtsbewegung,
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
