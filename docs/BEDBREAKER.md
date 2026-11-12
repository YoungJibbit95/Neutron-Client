# BedBreaker: Demise-Funktionen und Neutron-Integration

BedBreaker übernimmt die Bettsuche, beide `Legit`-Abbaupfade und sämtliche 17 dafür
wirksamen Einstellungen aus DemisePrivate. Die Standardkonfiguration verwendet
**Silent = an** und **Movement correction = Silent**. Die Kamera bleibt bei Silent
unter Maussteuerung; Serverrotation, Look-/Pickstrahl und die Darstellung des
eigenen Spielers verwenden die wirksame BedBreaker-Rotation.

Neutron ergänzt kontinuierliche Rotation beim Start, Zielwechsel und Beenden.
Abbauaktionen sind an die tatsächlich angenommene Rotation und den aktuellen
Spieler-, Welt- und Verbindungskontext gebunden. NScaffold und BedBreaker besitzen
getrennte Rotationsinstanzen.

Die gepinnte Referenz ist
[DemisePrivate BedBreaker.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/BedBreaker.java),
Commit `e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`. Quellenzuordnung, originale
Eigenschaften und ausdrücklich benannte Hostabweichungen stehen in
[BEDBREAKER-DEMISE-PARITY.md](BEDBREAKER-DEMISE-PARITY.md). Das
[Quellenmanifest](sources/demise-bedbreaker-e219a97.json) enthält Dateigrößen,
Git-Blobs und SHA-256-Prüfsummen. Die Demise-/AGPL-3.0-Herkunft bleibt dokumentiert.

## Bettsuche und Zielwinkel

Ein zugelassenes gewöhnliches Spielerupdate aktualisiert die gezogenen Yaw- und
Pitch-Geschwindigkeiten. Auf geraden Spieler-Ticks (`ticksExisted % 2 == 0`)
sucht der BedBreaker erneut das nächste Bett. Dazwischen behält er das vorhandene
Ziel, solange dessen Blockzustand und Kontext gültig bleiben.

Die Suche folgt dem Original:

- X und Z reichen von Spielerposition minus 4.5 bis plus 4.5; Y verwendet dieselbe
  Spanne um die Augenhöhe. Die Schleifen laufen in X-/Y-/Z-Reihenfolge mit
  Einerschritten und maximal 1.000 besuchten Schleifenzellen.
- Double-Schleifenwerte werden mit `(int)` gegen null abgeschnitten. Auch doppelte
  Nullzellen und die Reihenfolge bei negativen Koordinaten bleiben erhalten.
- Der Abstand wird von der Blockecke zu den Spielerfüßen gemessen. Bei Gleichstand
  gewinnt der zuerst gefundene Kandidat. Beide Betthälften sind BlockBed-Kandidaten.
- Ungeladene Zellen und Höhen außerhalb der Weltgrenzen werden ausgelassen.
  Such-Callbacks können einen veralteten Kontext jederzeit verwerfen.

Der Suchraum ist kubisch. Die spätere Aktion prüft ihren eigenen Strahl und ihre
Reichweite. Der Angle-Hook erneuert ausschließlich den Zielwinkel auf das Zentrum
`(x + .5, y + .5, z + .5)` des Bettblocks. Suche und Abbau erfolgen im Update-Hook.

## Die beiden Abbaupfade

| Einstellung | Strahlprüfung | Adressierter Block |
| --- | --- | --- |
| **Legit aus** — Standard | Ein 4.5 Blöcke langer Strahl entlang der aktuellen Rotation schneidet direkt die Kollisionsform des gewählten Betts. Davorliegende Blöcke sind kein Hindernis für diesen separaten Bettstrahl. | Das gewählte Bett. Die Paket-Seite stammt wie im Original aus `objectMouseOver.sideHit`. Bei fehlender Seite oder fehlendem Bett-Treffer wartet der Adapter und beendet eigenen laufenden Abbau. |
| **Legit an** | Der tatsächliche Blocktreffer von `objectMouseOver` bestimmt Position und Seite. Der native Controller prüft den ersten Weltstrahl-Treffer erneut. | Der getroffene Block. Verteidigungsblöcke vor dem Bett werden dadurch nacheinander abgebaut, bis der Strahl das Bett erreicht. Ein Miss oder Entitätstreffer erzeugt keinen neuen Blockabbau. |

Bei aktiver stiller Rotation folgt der globale Look-/Pickstrahl der wirksamen
Rotation. `objectMouseOver` ist deshalb auch mit unveränderten Kamerawinkeln die
Demise-Auswahl. Eine unabhängige Auswahl allein entlang des sichtbaren
Kamerawinkels wäre ein anderes Verhalten.

Die native Abbaulogik bestimmt Schaden, Werkzeugwirkung, Block-Hit-Delay,
Adventure-Berechtigung, Weltgrenze und Creative-Verhalten. Der Bettstrahl bei
Legit aus bleibt auf 4.5 Blöcke begrenzt; Legit an verwendet die gewöhnliche
Controllerreichweite. Das Modul wechselt weder Hotbar-Slot noch Werkzeug.

Jeder angenommene Controlleraufruf erzeugt ein gesichert gesendetes
`C0APacketAnimation`, auch während einer nativen Abbaupause. Dafür wird kein
zusätzliches lokales `swingItem()` und kein eigener Block-Hit-Partikeleffekt
ausgeführt. Beim entfernten Zielbett schließen dessen native Werkzeug- und
Zerstörungs-Callbacks zuerst ab; anschließend beginnt der Rotationsrücklauf.

Der frühere Neutron-Kostenplaner und seine ausschließlich zugehörigen Tests wurden
entfernt. Das aktuelle Modul bewertet keine alternativen Abbaukosten und berechnet
keinen schnellsten Weg. Die Reihenfolge der Verteidigungsblöcke entsteht im
Legit-Pfad aus den tatsächlichen Weltstrahl-Treffern.

## Einstellungen und Oberfläche

`Legit` stammt direkt aus dem ursprünglichen BedBreaker. Die weiteren 16
Einstellungen stammen aus dessen RotationManager-Instanz.

| Einstellung | Neutron-Standard | Bereich oder Auswahl |
| --- | --- | --- |
| Legit | Aus | An / Aus |
| Silent | An | An / Aus; Aus aktiviert sichtbare Kamerarotation. |
| Extra smoothing factor | 0.15 | 0.01–1, Bedieninkrement 0.01 |
| Smooth mode | Linear | Linear / Relative |
| Accelerate | Aus | An / Aus |
| Yaw accel factor | 0.25 | 0.01–0.9, Inkrement 0.01; sichtbar bei Accelerate. |
| Pitch accel factor | 0.25 | 0.01–0.9, Inkrement 0.01; sichtbar bei Accelerate. |
| Imperfect correlation | Aus | An / Aus |
| Yaw rotation speed (min) | 180 | 0.01–180, Inkrement 0.01 |
| Yaw rotation speed (max) | 180 | 0.01–180, Inkrement 0.01 |
| Pitch rotation speed (min) | 180 | 0.01–180, Inkrement 0.01 |
| Pitch rotation speed (max) | 180 | 0.01–180, Inkrement 0.01 |
| Movement correction | Silent | Silent / Strict / None |
| Short stop | Aus | An / Aus |
| Duration | 50 ms | 25–1000, Inkrement 25; sichtbar bei Short stop. |
| Rotation diff buildup to stop | 180 | 50–720, Inkrement 1; sichtbar bei Short stop. |
| Max threshold attempts to stop | 1 | 0–5, Inkrement 1; sichtbar bei Short stop. |

Der originale Movement-correction-Standard ist **None**. Neutron verwendet gemäß
Auftrag **Silent**. Zusätzlich bleiben `Smart Disable on Death` und
`Smart Disable on World Change` als vorhandene Hostoptionen erhalten; beide sind
standardmäßig ausgeschaltet.

Die Oberfläche gliedert die Werte in **Targets**, **Aiming**, **Rotation Speed**,
**Rotation Shape**, **Rotation Pauses**, **Movement** und **Safety**. Gruppen dienen
nur der Darstellung. Jeder gespeicherte Wert gehört genau einmal zu einer Gruppe.
Das Ausblenden abhängiger Regler erhält deren gespeicherte Werte.

Die Regler speichern die Float-Werte der Quelle. Konfigurationsimporte runden sie
nicht auf die Bedieninkremente der Oberfläche. Vertauschte Min-/Max-Grenzen bleiben
gespeichert; die BedBreaker-Engine normalisiert ihre Reihenfolge nur für die
Geschwindigkeitsziehung.

## Kontinuierliche Rotation und MoveFix

Mouse PRE misst die Framezeit. Mouse UPDATE integriert den bisherigen Auftrag;
der anschließende Angle-Hook erneuert das Bettziel. Die Framezeit wird wie in
Demise auf 1/90 Sekunde begrenzt und mit 120 skaliert. Die im Spielerupdate
gezogenen Geschwindigkeiten gelten für die folgenden Frames. Linear/Relative,
Sensitivität, Beschleunigung, Imperfect correlation und Short stop bleiben wirksam.

BedBreaker verwendet eine ausdrücklich angepasste kontinuierliche Engine:

- Die kombinierte Yaw-/Pitch-Änderung beträgt bei maximaler Framezeit höchstens
  12 Grad pro Frame. Bei kürzeren Frames sinkt dieser Grenzwert entsprechend.
  Ein Schritt je Achse geht höchstens über die halbe verbleibende Differenz.
- Der Verlauf bleibt innerhalb von 45 Grad kombinierter Differenz zur letzten
  angenommenen rotierenden C03-Pose. Ein verworfenes Paket setzt die Animation
  nicht zurück und erzeugt keine aufgestaute große Nachholrotation.
- Ein tatsächlich bestätigter fremder Rotationsbesitzer liefert beim Wechsel
  zurück die neue faktische Ausgangspose. Fehlende Bestätigung allein löst keine
  Rücksetzung auf einen älteren Stand aus.
- Abschalten, Zielverlust, Bettzerstörung und Konfigurationsänderungen beenden
  Abbauaktionen sofort und beginnen den Rücklauf. Die bestehende Pose bleibt
  beim Rücklauf und beim erneuten Aktivieren in derselben Szene erhalten.
- Der Rücklauf verwendet mindestens die Geschwindigkeit 15 und den
  Glättungsfaktor 0.15. Kleine Geschwindigkeiten oder Short stop halten einen
  ausgeschalteten Besitzer dadurch nicht dauerhaft fest. Restwinkel werden bis
  zur Genauigkeitsgrenze von 0.0001 Grad beziehungsweise der Float-ULP beendet.
- Der Wechsel von Silent zu sichtbarer Rotation führt die Kamera ebenfalls
  schrittweise mit dem Frame-/Restwinkel-Limit an die bestehende Pose heran.

Diese Regeln betreffen die BedBreaker-Instanz. NScaffold verwendet seine eigene
Engine mit dem ursprünglichen Rechenpfad. Die Abweichungen zur Referenz sind im
[Paritätsdokument](BEDBREAKER-DEMISE-PARITY.md) aufgeführt.

| Movement correction | Verhalten |
| --- | --- |
| Silent | Die ursprünglichen acht Richtungskandidaten rechnen rohe Bewegungsachsen vor der Sneak-Skalierung zur stillen Rotation um. Strafe und Sprung verwenden diese Rotationsbasis. |
| Strict | Die rohen Bewegungsachsen bleiben erhalten; Strafe und Sprung verwenden die Rotationsbasis. |
| None | BedBreaker korrigiert weder Eingabeachsen noch Strafe-/Sprungbasis; diese verwenden die Kamerarichtung. Die separate Sprint-Richtungsprüfung bleibt bestehen. |

Die gemeinsamen Hooks wählen den wirksamen Besitzer für Look, Modell und Bewegung.
Der Rücklauf bleibt ein Rotationsauftrag mit der dazugehörigen MoveFix-Basis,
auch wenn das Modul bereits ausgeschaltet ist. Eine vorübergehende Unterbrechung
in derselben Szene erhält den Rücklauf auch in den gesendeten C03-Paketen.
Renderframes ohne physische Mausverarbeitung integrieren ausschließlich diesen
BedBreaker-Rücklauf. Die vorbereitete Bewegungsbasis und das Rotationspaket
dürfen während einer Menü-/Fokusunterbrechung nur den bestehenden Rücklauf
fortsetzen. Dieser zusätzliche Vorbereitungspfad überspringt `EventRotation`,
`MovementReadyEvent` und die `beforePhysics`-Callbacks.
Eine spätere Änderung der Unterbrechung oder Verbindung entwertet alte
Paketfreigaben weiterhin. Ein verlorener Spieler-/Welt-/Verbindungskontext
verwirft den Rücklauf.

## Aktionshoheit und native Abbaubesitzverhältnisse

Automatische Abbauversuche laufen ausschließlich im zugelassenen gewöhnlichen
Update. Ein Budget nach Spieler, Welt und `ticksExisted` verhindert doppelte
Versuche. Zusätzliche simulierte Spielerupdates und erneutes Aus-/Einschalten
geben kein weiteres Aktionsbudget frei. Die Minecraft-Eingabephase beobachtet
manuelle Angriffe und bewahrt ausschließlich noch exakt zugeordneten laufenden
Abbau; sie startet selbst keine automatische BedBreaker-Aktion.

Kurze Mausklicks werden sowohl am Spieler-Tick als auch am aktiven äußeren
Minecraft-Tick erkannt. Ein Klick vor dem Erhöhen von `ticksExisted` kann daher
nicht später innerhalb desselben äußeren Ticks von BedBreaker überholt werden.
Manueller Angriff oder Benutzung, GUI/Fokusverlust, Reiten, Itembenutzung, aktive
Aura-Aktionen, Scaffold und NScaffold behalten Vorrang. Der LagRange-Veto gilt
weiterhin für die Aktion.

Mining benötigt die vorherige **angenommene BedBreaker-C03-Rotation**. Ein
vorbereiteter Rotationsauftrag allein reicht nicht aus. Die Native-Controller-
Überladung erhält einen unveränderlichen akzeptierten Look-Vektor. Paketguards
prüfen den aktuellen Kontext auch nach PRE-Callbacks und vor dem Transport.

Der Adapter bindet einen Versuch an seine Aktionengeneration, Controllerrevision,
Ziel und Blockzustand, Szene und Verbindungsepoche, Slot, Werkzeug, Spielerposition
sowie angenommene Rotationssequenz und Winkel. Bei einem Verteidigungsblock muss
das zugehörige Zielbett weiterhin vorhanden sein. Die nativen Zerstörungs- und
Werkzeug-Callbacks des Zielbetts oder seiner passenden anderen Hälfte dürfen
auch dann abschließen, wenn der Bettblock bereits entfernt ist.

Eine Änderung von Zielposition oder erwartetem Blockzustand löst den eigenen
alten Abbau, bevor ein neuer beginnt. Ein manuell übernommener Controllerzustand
wird weder übernommen noch abgebrochen. Abschalten beendet ausschließlich den
eigenen Abbau. Verlorene Szenen verwerfen lokalen eigenen Fortschritt ohne
alte Welt-Callbacks. Der verbleibende Rotationsrücklauf besitzt kein Abbaurecht.

## Migration älterer Konfigurationen

Ein vorhandener BedBreaker-Eintrag erhält für ausgelassene Einstellungen die
vollständigen Demise-Vorgaben. Explizite neue Werte haben Vorrang, unabhängig von
der Reihenfolge im JSON. Die Smart-Disable-Importregeln bleiben bestehen:
ausgelassene Werte sind aus, ausdrücklich gespeicherte Werte gelten. Ein vollständig
fehlender BedBreaker-Eintrag verändert das aktuelle Profil nicht.

Die bisherigen Felder `Turn Speed` und `Swing` werden beim Import erkannt und
validiert. Der alte gemeinsame Tick-Winkel lässt sich nicht exakt auf die neuen
Frame-/Achsen-Regler abbilden. Die neuen Regler erhalten daher ihre Vorgaben,
soweit das Profil sie nicht ausdrücklich angibt. Das frühere Swing-Flag wird
entfernt; angenommene Abbauversuche verwenden die originale Paketanimation.
Die Lademeldung nennt diese Bedeutungsänderungen.

Benannte importierte Dateien werden beim Laden nicht überschrieben. Eine spätere
automatische Speicherung schreibt die aktuellen Namen und entfernt die beiden
bekannten veralteten Felder. Unbekannte Daten eines versionierten Profils bleiben
erhalten. Ungültige Werte oder doppelte normalisierte Namen verwerfen den Import,
bevor Moduleinstellungen angewendet werden.

## Testabdeckung und Grenzen

| Tests | Geprüfter Vertrag |
| --- | --- |
| `DemiseBedBreakerGeometryTest` | Vergleich mit dem gepinnten Original-Suchkörper, negative Abschneidung und Duplikate, Suchreihenfolge/Gleichstände, Ecke-/Fußabstand, Betthälften, Zielwinkel, Bettstrahl sowie Kontext-/Loaded-Grenzen. |
| `BedBreakerContinuousRotationTest` | Kontinuierlicher Start, Tracking und Rücklauf, Frame-/Drahtgrenzen, Restwinkel, Geschwindigkeitsbereiche und sichtbare Übergänge. |
| `BedBreakerDemiseHookTest` | Tatsächliche Look-, Bewegungs-, Sprung-, Modell- und C03-Hooks, unabhängige Modulinstanzen und deaktivierter Rücklauf. |
| `BedBreakerFlowTest`, `BedBreakerSilentRotationTest` | Nativer Controller und eingebetteter Pakettransport, manuelle Priorität, Aktionsbudgets, angenommene Rotationshoheit, Abbruch, Szenenverlust und Callback-Übernahme. |
| `BedBreakerDefenseFlowTest` | Tatsächliche Legit-Abbaupfade mit Verteidigungsblöcken und anschließendem Bettabbau sowie lebende Zustandsänderungen. |
| `BedBreakerDemiseConfigTest`, `BedBreakerSettingsLayoutTest` | Vollständige Registrierung, Legacy-Import, Speicherung und Präzision, transaktionale Ablehnung, Gruppen und bedingte Sichtbarkeit. |

Die konkret ausgeführten Tests und ihre Ergebnisse gehören zur jeweiligen
PR-Abnahme. Die Fixtures ersetzen den Weltinhalt sowie Teile der Spiel-/Audio-
Umgebung. Sie belegen keinen manuellen visuellen Spieltest und keine Annahme der
Aktionen durch einen bestimmten Server.
