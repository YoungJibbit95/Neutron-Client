# NScaffold: Demise Scaffold in Neutron

NScaffold unter **World** verwendet den Scaffold-Ablauf aus
[DemisePrivate, Commit e219a97](https://github.com/YoungJibbit95/DemisePrivate/tree/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb).
Die bisherigen eigenen NScaffold-Planer wurden ersetzt. Quellen und
Hostanpassungen stehen in [NSCAFFOLD-DEMISE-PARITY.md](NSCAFFOLD-DEMISE-PARITY.md).

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
| Mode: Normal | Originale Zielsuche und Platzierung. |
| Mode: Breezily | 45°-Ausrichtung, Pitch-Auswahl und seitliche Eingaben anhand Blickrichtung/Blockmitte. |
| Mode: Telly | Automatischer Sprung unter den Quellbedingungen; Rotation/Platzierung ab aufeinanderfolgenden Flugticks. |
| Straight / Diagonal telly ticks | Standard 4 / 2, Bereich 1–5, Richtung aus Demises Keybind-Yaw. |
| Rotations: Normal / Center / GodBridge / Reverse | Dichte Flächensuche, Flächenmitte, originale Seitenauswahl bzw. Bewegungs-Yaw minus 180°. |
| Static-ify | Originale feste Pitch-Werte für GodBridge/Reverse. |

Die Suche behält feste Nachbarreihenfolge, sechs Offset-Stufen, stabile
Entfernungssortierung, Duplikate und die Priorität des vorherigen Blocks.

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
| Safewalk / Safewalk when no data | Originaler Kantenpfad bei Bodenkontakt bzw. fehlenden Platzierungsdaten. |
| Swing | Default an; aus bedeutet nur Paketanimation. |
| RayTrace / Strict rayTrace | Standardstrahl mit Reichweite 4.5; Strict verlangt dieselbe Blockseite. |
| Target block ESP | Originaler Zielblockwürfel und projizierte Shader-Maske. |
| Hover | Originalregistrierung ohne ausführenden Consumer im gepinnten Java-Quellbaum; keine erfundene Zusatzfunktion. |

Die Zielblockfarbe folgt Neutrons Interface-Farbe. Die Shader-Masken benutzen die
vorhandenen globalen Blur-/Glow-Einstellungen und RenderState-Guards. Geometrie,
Alpha 100 für den Weltwürfel, schwarze Nicht-Glow-Maske und farbige Glow-Maske
stammen aus Demise. Das separate TargetESP bleibt eine andere Anzeige.

## Slots, Platzierung und Lebensdauer

Der **letzte geeignete Hotbar-Slot** gewinnt, mit Demises Blacklist.
Hotbar-Markierung, Itemname und dargestellter Gegenstand zeigen den beim
Einschalten gespeicherten Slot; tatsächlich platziert wird der ausgewählte
Blockstack. Ausschalten stellt den gespeicherten Slot wieder her.

Platzierung läuft am Anfang von EntityPlayerSP.onLivingUpdate vor neuem Input,
über den normalen PlayerController mit Neutrons vorhandener Validierung nach
Slot-/Paket-Callbacks. Eigene POST-C03-Planung und Placement Delay entfallen.
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
Modulabläufe mit echtem Controller, Konfigurationsmigration und native
Renderproben in CI. Synthetische Controller-/Pbuffer-Prüfungen sind keine
manuellen Tests einer laufenden Minecraft-Welt.
