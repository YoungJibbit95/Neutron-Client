# NScaffold: sichtbare Bauhilfe

NScaffold ist ein eigenständiges Modul in der Kategorie **World**. Es richtet die
tatsächliche Kamera auf eine erreichbare Platzierungsfläche aus und benutzt den
normalen Minecraft-Controller. Die gewählte Kameraausrichtung ist zugleich die
normale Bewegungsbasis. NScaffold dreht keine Eingabe heimlich dagegen und
versteckt weder die Kamera noch den ausgewählten Hotbar-Slot.

Die Bedienung unterscheidet sich deshalb vom bisherigen Scaffold: Nach einer
sichtbaren Drehung bewegen sich die Tasten entsprechend der neuen Blickrichtung.
Für eine rückwärts gebaute Brücke muss auch die entsprechende normale
Rückwärtseingabe **S**, bei Bedarf zusammen mit **A** oder **D**, verwendet werden.
W beziehungsweise reine Seitwärtseingabe pausiert mit **Hold back to bridge**.
Das verhindert eine Rückkopplung zwischen neuem Ziel und entgegengesetzter
sichtbarer Blickrichtung. Das Modul setzt keine Bewegungs-,
Sprint-, Sprung-, Sneak- oder Maustasten. Manuelle Eingaben bleiben Eingaben des
Spielers; ein widersprüchlicher frischer Bewegungsschritt verwirft den Plan.
Eine manuelle Kameradrehung pausiert mit **Manual view** und gibt die bisher
gehaltene Richtung frei. Solange die Kamera weiter manuell bewegt wird, setzt
NScaffold keine eigene Drehung oder Platzierung dagegen. Bei ruhender Kamera
kann es einen neuen passenden Plan aus dieser sichtbaren Ausgangsrichtung bilden.

## Einstellungen

| Einstellung | Wirkung |
| --- | --- |
| **Visible Rotation** | `Standard` hält einen brauchbaren Flächentreffer. `Godbridge` bevorzugt eine bewegungsbezogene 45°-Ausrichtung und den eingestellten Abwärtswinkel. Beide Varianten drehen die sichtbare Kamera. |
| **Turn Speed** | Maximale gemeinsame Winkeländerung von Yaw und Pitch pro regulärem Bewegungsschritt, in Grad. Kleinere Werte benötigen länger bis zu einer tatsächlich getroffenen Fläche; vor diesem Treffer wird nicht platziert. |
| **Godbridge Pitch** | Bevorzugter Abwärtswinkel für Godbridge. Standardwert: 75,7°. Eine reale begrenzte Blockfläche kann eine Korrektur erfordern; der Wunschwert ersetzt keine Sichtlinienprüfung. |
| **Look Ahead** | Vorschau entlang der normalen aktuellen Bewegung, von 0 bis höchstens einem Block. Sie vergrößert keine Controller-Reichweite. |
| **Keep Height** | Hält beim normalen Weiterbauen die begonnene Zielebene, solange sie höchstens drei Ebenen unter der aktuellen liegt. Beim Unterschreiten beziehungsweise bei größerem Höhenwechsel wird die Ebene neu erfasst. |
| **Auto Select Blocks** | Erlaubt einen normalen sichtbaren Wechsel zu einem brauchbaren Blockstack in der Hotbar. Ausgeschaltet verwendet NScaffold ausschließlich den bereits ausgewählten geeigneten Stack. |
| **Placement Delay** | Mindestzeit zwischen Controller-Versuchen in Millisekunden. Zusätzlich gilt das gemeinsame Platzierungsbudget von höchstens einem Versuch pro Client-Tick. |
| **Swing** | Normale sichtbare Handanimation nach lokal erfolgreicher Platzierung. |
| **Smart Disable on Death** | Schaltet das Modul bei Tod aus. Standardmäßig aus. |
| **Smart Disable on World Change** | Schaltet es bei einem beobachteten Welt-, Spieler- oder Verbindungswechsel aus. Standardmäßig aus; keine unsichere Same-world-Lobby-Heuristik. |

Der bestehende **Scaffold** hat Vorrang: Solange er eingeschaltet ist, pausiert
NScaffold. Ein aktives Aura-Ziel, eine Aura-Aktion im aktuellen Tick oder eine
laufende Itembenutzung pausiert die Bauhilfe ebenfalls. GUI, Fokusverlust,
Tod, Spectator, Reiten und eine ungültige Szene verhindern automatische Aktionen.
Nach einer Pause wird ein frischer Plan benötigt. Das Wiedereinschalten nach einem
Smart Disable bleibt eine ausdrückliche Nutzeraktion.

## Zielwahl und Godbridge

Der Planer prüft zunächst die tatsächlich benötigte Zelle unter dem Spieler oder
den nächsten kleinen Schritt entlang der Bewegung. Bei einem diagonalen Übergang
kommen höchstens die beiden Verbindungszellen hinzu. Für jede Zelle werden fünf
Nachbarflächen betrachtet: vier Seiten und eine Unterstützung von unten. Es gibt
damit höchstens **15 neue Flächenprüfungen** pro Suche; kein rekursiver Radius-Scan.
Eine zusätzliche Prüfung eines gehaltenen Plans oder der vorhandenen Blickrichtung
ist davon getrennt.

Eine gültige Unterstützung wird gehalten. Trifft die aktuelle Blickrichtung die
richtige Fläche bereits, kann dieser Treffer weiterverwendet werden. Standard
hält im Stillstand andernfalls seinen konkreten brauchbaren Punkt während der
sichtbaren Annäherung. Beim Rückwärtslaufen korrigiert es dieselbe Fläche aus
einer stabilen Abwärtspräferenz von 75,7°, damit ein vorheriger steiler Winkel
beim nächsten Schritt nicht unter die Blockfläche zeigt. Godbridge behält seine bevorzugte Richtung über normale
Blockübergänge und sucht nötige Korrekturen auf der bereits verwendeten Fläche.
Während einer gehaltenen Rückwärtseingabe bleiben Yaw-Korrekturen innerhalb
von 18° der aufgenommenen Richtung. Fehlt eine passende Fläche, wartet das
Modul, statt die Kamera zur gegenüberliegenden Blockseite zu drehen. Ein bereits
gültiger aktueller Sichtstrahl hält seine exakten Winkel; er wird nicht aus einem
gerundeten Schnittpunkt zurückgerechnet.
Geänderte Eingaben und ungültige Flächen erlauben eine neue Wahl.

Alle Ziele bleiben an reale Blockformen, die erste getroffene Weltfläche, die
normale Controller-Reichweite, gültige Slots/Stacks und die aktuelle Szene
gebunden. Ein blockierter oder unerreichbarer Punkt führt zu keiner Platzierung.
Eine noch erreichbare Fläche kann auch während eines Falls benutzt werden;
unmögliche Rettungsplätze oder eine erfolgreiche Rettung werden nicht garantiert.

Die Rotation wird zuerst sichtbar beantragt. Platziert wird erst nach der
lokalen Annahme des zugehörigen tatsächlichen Bewegungswinkels und einer erneuten
Sichtlinienprüfung von der zuletzt gemeldeten Augenposition. Ein lokaler
Controller-Erfolg ist keine Bestätigung des Servers.

## Island-Status

NScaffold liefert der Island einen unveränderlichen Aktivitäts-Snapshot mit dem
beobachteten Blocktyp, dessen Metadaten, dem brauchbaren Hotbar-Blockvorrat und
dem gewählten Rotationsmodus. **Searching**, **Aligning** und **Placing** stammen
aus den jeweiligen Modulschritten; **Waiting** und **Paused** kennzeichnen
Warten beziehungsweise eine bewusste Unterbrechung. Eine Pause wird damit nicht
als neue Suche oder erfolgreiche Platzierung ausgegeben.

Der Platzierungszähler wächst seit dem Einschalten ausschließlich nach einem
lokal erfolgreichen Controller-Aufruf in der weiterhin gültigen Szene.
Suchschritte, Drehungen, abgelehnte Versuche und doppelte POST-Ereignisse erhöhen
ihn nicht. Er ist keine Bestätigung des Servers. Die Island kann daraus ihre
Aktivitätsanzeige ableiten, ohne selbst auf den Builder zuzugreifen.

`getActivitySnapshot()` prüft lediglich den gecachten Datensatz, seine maximale
Lebensdauer von 600 ms sowie begrenzte Identitäts- und Inventarbedingungen. Der
Getter verändert den Cache nicht und führt weder Inventarscan noch Zielsuche,
Rotationsanforderung oder Controller-Aktion aus. Menüs, beobachtete
Unterbrechungen und Welt-/Verbindungswechsel verwerfen die Darstellung alter
Daten; eingehende Inventarkorrekturen löschen den Cache im Ereignispfad.
Neue Daten entstehen ausschließlich in den vorhandenen Modulschritten.

## TargetESP

Die Hitpoint-Auswahl **Active** berücksichtigt auch NScaffold. Die eigene Auswahl
**NScaffold** zeigt ausschließlich dessen aktuelle angenommene Rotation;
**Scaffold** bleibt dem bisherigen Modul zugeordnet. Die Aktivitätsflags werden
nicht zusammengelegt. Es gelten die bestehenden Frische- und Szenenprüfungen.

Der Marker bleibt eine reine Beobachtung: Er verändert keine Ziele, Winkel,
Eingaben oder Controller-Aktionen. Er folgt dem tatsächlichen Strahl ohne
Positionsglättung. Halo, Theme, Glow, Reduced Motion und Tiefenoptionen des
TargetESP behalten ihre bisherige Bedeutung; die Shader wurden dafür nicht
geändert.

## Herkunft und Abgrenzung

Referenz ist die vom Nutzer zur Nutzung freigegebene Demise-Quelle:

- Repository: `YoungJibbit95/DemisePrivate`
- Commit: `e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`
- Datei: `src/main/java/wtf/demise/features/modules/impl/player/Scaffold.java`
- Datei-Blob: `f5cbead2aa1d666af66946173fa358b626ac9244`
- [Gepinnte Referenzdatei](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/Scaffold.java)

NScaffold ist eine angepasste Neutron-Implementierung der normalen Nachbar- und
Flächenwahl sowie der geometrischen Godbridge-Präferenz. Bereits vorhandene
Neutron-Geometrie und Controller-Validierung werden weiterverwendet. Die globale
Demise-Slotdarstellung, Paketumschreiber, spezielle Bewegungssteuerung und
Eingabeüberschreibungen wurden nicht importiert. Das Modul ist kein 1:1-Port und
enthält keine Zusage über die Bewertung durch Serverprüfungen.

Die README der geprüften Demise-Revision nennt AGPL-3. Die bestehende
Neutron-Lizenzdatei nennt GPL v3. Dieser Herkunftshinweis hält die Quelle und die
angegebene Lizenz fest und ersetzt oder erweitert keine dieser Lizenzangaben.

## Regressionen und Prüfung

`NScaffoldPlannerTest` prüft Suchbudget, gehaltene Flächen, negative Koordinaten,
Zielverlust, erreichbare Godbridge-Geometrie, Abbruch während Weltabfragen und den
Wirkungsbereich des Turn-Speed-Werts. `NScaffoldPlacementTest` prüft die exakte
Rotationsquelle, sichtbare Slotwahl und das gemeinsame Tickbudget.
`NScaffoldFlowTest` prüft den echten Modulablauf mit synthetischer Welt- und
Controllergrenze, einschließlich Konflikten, sichtbarer Bewegungsbasis,
manuellen Kameradrehungen und Abbrüchen innerhalb der letzten Ray-Abfrage des
Controller-Gates. Die Aktivitätsprüfungen behandeln erfolgreiche und abgelehnte
Platzierungen, unveränderte Renderabfragen, ehrliche Pausen, Ablauf der
Cache-Lebensdauer und Szenen-/Inventarkorrekturen. Die TargetESP-Tests prüfen die unabhängige neue Quelle und
deren rein beobachtende Verarbeitung.

Diese Fixtures ersetzen keine Spielprüfung mit realer Kollision und tatsächlicher
Eingabe. Ausgeführte Ergebnisse gehören zum jeweiligen CI-/Abnahmeprotokoll;
diese Dokumentation behauptet keine bereits erfolgte Spiel- oder Serverabnahme.

Die Mehrtick-Sequenzprüfung beginnt mit einer ausgerichteten sichtbaren Kamera
bei 45° Yaw und 75,7° Pitch und prüft Standard/Godbridge jeweils mit gehaltenem S,
S+A und S+D über 80 reguläre geometrische Bewegungsschritte. Sie verwendet die
normalen kamerabezogenen Bewegungsachsen und die echte Placement-Service-Prüfung,
aber keine vollständige vertikale Spielphysik. Sie begründet keine allgemeine
Stabilitätszusage für beliebige Ausgangspositionen oder einen 1:1-Godbridge-Port.
