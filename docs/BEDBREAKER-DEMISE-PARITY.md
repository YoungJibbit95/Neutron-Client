# Demise BedBreaker: Quellen, Funktionen und Einstellungen

Die Referenz ist **YoungJibbit95/DemisePrivate**, Commit
`e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`. Dieser Commit war beim erneuten
Abgleich auch der aktuelle `main` der Referenz. Die Dateigrößen, Git-Blobs und
SHA-256-Prüfsummen stehen in
[demise-bedbreaker-e219a97.json](sources/demise-bedbreaker-e219a97.json).

Die Quell-README bezeichnet Demise als AGPL-3. Im gepinnten Quellbaum befindet
sich keine Root-LICENSE-Datei. Der Auftraggeber hat die Übernahme ausdrücklich
beauftragt; vorhandene Herkunfts- und Lizenzhinweise werden beibehalten.

## Ausführende Quellenzuordnung

| Originalquelle | Aufgabe und Hostzuordnung |
| --- | --- |
| [BedBreaker.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/BedBreaker.java) | Legit-Auswahl, Update-/Angle-Ablauf, periodische Bettsuche, Controlleraufruf und Animationspaket. Moduladapter: `BedBreaker`; Such- und Zielmathematik: `DemiseBedBreakerGeometry`. |
| [RotationManager.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationManager.java) | Die 16 für BedBreaker wirksamen Rotationseinstellungen, Geschwindigkeitsziehung, Imperfect correlation und Short stop. `DemiseRotationSettings` und instanzgebundene Rotation Engine. |
| [RotationHandler.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationHandler.java) | Framezeit, Linear/Relative, Sensitivitätsbezug, Beschleunigung, Movement correction und Rotationsnachlauf. BedBreaker besitzt einen getrennten Kontext; die NScaffold-Instanz bleibt unabhängig. |
| MoveUtil.fixMovement | Acht diskrete Richtungskandidaten mit der originalen Reihenfolge und striktem `<`-Vergleich; `DemiseMoveFix`. |
| RotationUtils / Vec3i | Ungewrappte Zielwinkel, Float-Zwischenschritt der horizontalen Wurzel und Abstand von Blockecke zu Spielerfüßen. |
| EntityRenderer / Entity / EntityPlayerSP | Mouse PRE, Mouse UPDATE, AngleEvent, UpdateEvent, Bewegungsbasis sowie gesendete und dargestellte Rotation. |
| AbstractClientPlayer.getLook | Der Pick-/Look-Strahl folgt bei aktiver stiller Rotation dem Handler. `objectMouseOver` ist deshalb im Original nicht grundsätzlich der unveränderte Kamerastrahl. |
| PlayerControllerMP / PacketUtils | Gewöhnlicher Blockabbau und C0A-Animationspaket. Der BedBreaker besitzt keinen eigenen Damage-Multiplikator. |

## Vollständige wirksame BedBreaker-Einstellungen

`Legit` ist die einzige direkt in Demises BedBreaker deklarierte Einstellung.
Die übrigen 16 stammen aus dessen eigener RotationManager-Instanz. Namen,
Auswahlwerte, Float-Bereiche, Bedieninkremente und bedingte Sichtbarkeit folgen
der Referenz.

| Einstellung | Demise-Standard | Wertebereich / Sichtbarkeit |
| --- | --- | --- |
| Legit | Aus | Checkbox; immer sichtbar. |
| Silent | An | Checkbox; immer sichtbar. Aus aktiviert die ebenfalls in Demise vorhandene sichtbare Kamerarotation. |
| Extra smoothing factor | 0.15 | 0.01–1, Schritt 0.01. |
| Smooth mode | Linear | Linear / Relative. |
| Accelerate | Aus | Checkbox. |
| Yaw accel factor | 0.25 | 0.01–0.9, Schritt 0.01; bei Accelerate. |
| Pitch accel factor | 0.25 | 0.01–0.9, Schritt 0.01; bei Accelerate. |
| Imperfect correlation | Aus | Checkbox. |
| Yaw rotation speed (min) | 180 | 0.01–180, Schritt 0.01. |
| Yaw rotation speed (max) | 180 | 0.01–180, Schritt 0.01. |
| Pitch rotation speed (min) | 180 | 0.01–180, Schritt 0.01. |
| Pitch rotation speed (max) | 180 | 0.01–180, Schritt 0.01. |
| Movement correction | None | Silent / Strict / None. **Neutron startet gemäß Nutzerauftrag mit Silent.** |
| Short stop | Aus | Checkbox. |
| Duration | 50 ms | 25–1000, Schritt 25; bei Short stop. |
| Rotation diff buildup to stop | 180 | 50–720, Schritt 1; bei Short stop. |
| Max threshold attempts to stop | 1 | 0–5, Schritt 1; bei Short stop. |

Der generische originale RotationManager legt zusätzlich `Distance based rotation
speed`, `Min range`, `Max range` und `Decrement per cycle` an. Sichtbarkeit und
Ausführung sind ausdrücklich auf **KillAura** beschränkt. Diese vier Regler sind
keine BedBreaker-Funktionen und werden hier nicht als solche angeboten. Auch
`None` und `Polar` in den Sichtbarkeitsbedingungen sind keine auswählbaren
Smooth-Modi: Das gepinnte Enum enthält ausschließlich Linear und Relative.

## Such- und Abbauverhalten der Referenz

### Suche und Zielwechsel

Das originale UpdateEvent aktualisiert die zufälligen Yaw-/Pitch-Geschwindigkeiten
bei jedem Spielerupdate. Eine neue Bettsuche erfolgt, wenn
`ticksExisted % 2 == 0`. Dazwischen bleibt das gespeicherte Ziel erhalten.

Die Suche läuft mit Double-Schleifen in der Reihenfolge **X, Y, Z** jeweils in
Einerschritten von −4.5 bis +4.5 um X/Z der Spielerfüße und Y der Augen. Die
Konvertierung jedes Schleifenwerts erfolgt mit `(int)`, also mit Abschneiden
gegen null. Das bewahrt auch wiederholt besuchte Nullzellen bei negativen
Koordinaten. Es erfolgt keine Deduplizierung oder zusätzliche Sortierung.

Verglichen wird der euklidische Abstand von der **Blockecke** zur
**Spielerposition an den Füßen**. Bei gleichem Abstand gewinnt das zuerst
gefundene Bett. Beide Betthälften sind gewöhnliche BlockBed-Kandidaten. Die
Suche selbst prüft weder einen kugelförmigen Aktionsradius noch eine freie
Sichtlinie. Der Aktionspfad prüft den tatsächlich verwendeten Strahl.

Der AngleEvent fordert die Rotation auf `(x + .5, y + .5, z + .5)` des Betts an.
Die originale Rotationsfunktion wrappt den Yaw nicht vorab und verwendet den
Float-Rückgabewert von `MathHelper.sqrt_double` für die horizontale Distanz.

### Legit aus

Ein 4.5 Blöcke langer Strahl entlang der aktuellen Handler-Rotation wird direkt
mit der Kollisionsbox des gewählten Betts geschnitten. Dieser Test läuft gegen
das Bett selbst und nicht gegen sämtliche davorliegenden Weltblöcke. Bei
einem Treffer adressiert der Controller das Bett. Die Referenz verwendet
dabei die Seite von `objectMouseOver`, nicht die Seite des separaten Bettstrahls.
Bei fehlendem Treffer setzt sie den laufenden Abbau zurück.

### Legit an

Der Controller verwendet Position und Seite des aktuellen Blocktreffers in
`objectMouseOver`. Durch den umgeleiteten Look-Strahl werden dabei die vor dem
Bett liegenden Blöcke getroffen und nacheinander entfernt. Bei einem Miss
oder Entitätstreffer erfolgt kein neuer Blockabbau; eigener laufender Abbau
wird beendet.

Nach einem ausgewählten Controlleraufruf sendet die Referenz ein
`C0APacketAnimation`, auch während der nativen Block-Hit-Delay-Phase. Sie ruft
dafür nicht `player.swingItem()` auf und besitzt keinen eigenen `Swing`-Regler.
Der bisherige Neutron-Kostenplaner, Hotbar-Wechsel, Tool-Spoofing und zusätzliche
ESP- oder Reach-Einstellungen sind keine Bestandteile dieses Demise-Moduls.
Die nachfolgend ergänzte [Bed-Zeitanzeige und BedESP](BED-VISUALS.md) liest diesen
Aktionspfad nur aus. Vier zusätzliche Indicator-Werte werden nach den
ursprünglichen 19 BedBreaker-/Host-Werten angehängt; BedESP ist ein getrenntes
Render-Modul und verändert die Demise-Ausführung nicht.

## Rotationen, MoveFix und ausdrücklich gewünschte Übergänge

Die Referenz integriert die Rotation pro Renderframe. Mouse PRE misst die
Framezeit; Mouse UPDATE integriert den bisherigen Auftrag; erst der spätere
AngleEvent in `Entity.setAngles` erneuert das Ziel. Die Framezeit wird bei
1/90 Sekunde begrenzt und mit 120 skaliert. Geschwindigkeiten werden pro Update
gezogen und für die folgenden Frames wiederverwendet. Float-Rechnung,
Sensitivität, ganzzahlige Maus-Schritte, Linear/Relative, Beschleunigung und
Short-stop-Einstellungen bleiben eigenständige wirksame Bestandteile.

- **Silent Movement correction:** Die originalen acht Richtungs-Kandidaten
  rekonstruieren Eingaben relativ zur stillen Rotation. Strafe und Sprung
  verwenden diese Rotationsbasis.
- **Strict:** Strafe und Sprung verwenden die Handler-Rotation; die Eingaben
  werden nicht durch den Silent-Kandidatenvergleich umgerechnet.
- **None:** Keine Umrechnung von Strafe-/Sprungbasis; die originale
  Sprint-Richtungsprüfung bleibt als separater Pfad relevant.

Das Original beginnt nach mehr als 150 ms ohne erneuerten Auftrag den Rücklauf
zur Kamera. Unter einem Gesamtwinkelfehler von einem Grad setzt es die Rotation
direkt auf die Kamera. Seine ganzzahligen Maus-Schritte können außerdem in einer
kleinen diagonalen Restlücke stehenbleiben. Bei sehr hohen Einstellwerten kann
der ursprüngliche Framefaktor einen Zielwinkel überschreiten.

**Der Nutzerauftrag verlangt hier ausdrücklich eine Weiterentwicklung:**
BedBreaker verwendet `DemiseRotationEngine.continuous()` für Anfang, Zielwechsel
und Ende. Der erste Auftrag knüpft an den vorhandenen Rotationsstand an; ein
Aktionsende startet unmittelbar den geglätteten Rücklauf zur Kamera. Zielwechsel,
Wertänderungen und erneutes Einschalten erhalten die vorhandene Bewegung.
Die folgenden Grenzen gelten ausdrücklich nur für diese BedBreaker-Variante:

| Übergang / Randfall | Verhalten in Neutron |
| --- | --- |
| Einzelner Renderframe | Höchstens 12° kombinierte Yaw-/Pitch-Änderung bei maximaler Framezeit; bei höherer Bildrate sinkt die Grenze proportional zur Framezeit. Der Yaw-Abstand wird über den kürzesten Winkel gemessen. |
| Abstand zur gesendeten Rotation | Höchstens 45° kombinierter Abstand von der letzten tatsächlich akzeptierten rotierenden C03-Pose. Das ist eine zusätzliche Grenze zum bestätigten Serverstand, keine neue Grad-pro-Tick-Einstellung. |
| Annäherung und Richtungswechsel | Ein Schritt in Zielrichtung verbraucht höchstens die Hälfte des verbleibenden Achsenwinkels. Das verhindert den direkten Sprung auf ein noch entferntes Anfangs- oder Endziel. |
| Quantisierungsrest | Ein kleiner gebrochener Schritt schließt die Restlücke, in der die originalen ganzzahligen Maus-Schritte stehenbleiben können. Der Rücklauf endet erst bei einem Gesamtfehler von höchstens `max(0.0001°, Float-ULP)`; der originale Ein-Grad-Sprung entfällt. |
| Rücklauf nach Aktionsende | Die gezogene Geschwindigkeit besitzt dabei eine Untergrenze von 15° und der Extra-smoothing-Faktor eine Untergrenze von 0.15. Diese Untergrenzen gelten nur beim Rücklauf; Short stop und sehr kleine Geschwindigkeiten bleiben bei aktiver Zielverfolgung wirksam. |
| Silent an → aus | Die tatsächliche Kamera nähert sich der laufenden Modell-/Serverrotation mit derselben 12°-Framegrenze und halben Restlücke. Ein Wechsel kopiert die entfernte stille Pose nicht sofort in die Kamera. Die 45°-Grenze bleibt eine Grenze der Engine zur gesendeten Rotation. |
| Gekreuzte Min-/Max-Regler | Die Geschwindigkeitsziehung ordnet die beiden Grenzen zur Laufzeit. So bleibt das Zufallsintervall gültig, wenn Min beim Bearbeiten über Max liegt; gespeicherte Reglerwerte werden dafür nicht überschrieben. |
| Besitzerwechsel | Nur ein tatsächlich bestätigter anderer Rotationsbesitzer liefert eine neue Ausgangspose für die Übergabe. Ein abgelehntes C03 oder fehlender bestätigter Besitzer verwirft die laufende Animation nicht. |

Der BedBreaker-Rücklauf bleibt von neuen Abbauaktionen getrennt. Eine Pause in
derselben Szene erhält die Engine und beginnt ihren Rücklauf; beim Fortsetzen
kann die tatsächlich bestätigte C03-Pose als Ausgang dienen. Erst ein Verlust
des Spieler-/Welt-/Modul-/Verbindungskontexts oder seiner Netzwerkepoche verwirft
die Engine. Bewegungskorrektur und Modellrotation richten sich nach dem
tatsächlich wirksamen Rotationsbesitzer.

Diese Regeln sind benannte Hostabweichungen. Der normale Konstruktor
`new DemiseRotationEngine()` behält die ursprüngliche Arithmetik für NScaffold;
BedBreakers kontinuierliche Übergänge verändern dessen Referenzpfad nicht.

## Hostgrenzen und Abnahme

Java-8-Syntax, MCP-Namen, persistente Werte und Alpine-Ereignisse werden an
Neutron angepasst. Ungeladene Zellen und ungültige Welthöhen werden nicht gelesen.
Jeder Such-Callback kann den aktuellen Kontext entziehen; ein am Ende nicht
mehr vorhandenes Bett wird nicht als neuer gültiger Treffer veröffentlicht.

Spieler-, Welt-, Verbindungs- und Controllerwechsel, manuelle Übernahme und
konkurrierende Rotationsbesitzer behalten Neutrons vorhandene Gültigkeitsprüfungen.
Der gewöhnliche Rotationsnachlauf darf nicht alte Abbauaktionen erneut freigeben.
Bei einer Menü-/Fokusunterbrechung derselben Szene bleibt der bereits begonnene
BedBreaker-Rücklauf auch für Frameintegration, Bewegungsbasis und C03 gültig.
Dieser eng begrenzte Vorbereitungspfad überspringt `EventRotation`,
`MovementReadyEvent` und `beforePhysics` und erteilt kein Mining-Budget. Szenen-, Verbindungs-,
Frame-, Paket- und Unterbrechungsversionen bleiben an die einzelne Freigabe
gebunden; ein später Übergang kann kein altes Paket wiederbeleben.
Ein tatsächlicher Szenenverlust verwirft den alten Kontext; er überträgt keinen
Nachlauf auf einen neuen Spieler oder eine andere Verbindung. Nullzugriffe und
die originale Objektidentitätsprüfung `currentBlock == cachedBedPos` werden
durch konkrete Eigentums- und Zustandsprüfungen ersetzt.

`DemiseBedBreakerGeometryTest` vergleicht die Suche gegen den unveränderten
originalen Methodenkörper mit minimalen Minecraft-Zugriffen. Die gepinnten
Originaldateien liegen als Testressourcen vor und werden anhand ihrer Git-Blobs
geprüft. Fälle umfassen negative Abschneidung, Duplikate, Suchreihenfolge,
Gleichstände, Eck-/Fußabstand, beide Hälften, den kubischen Suchraum, originale
Winkelrechnung, Bettstrahl versus Verteidigungsblock sowie Kontext- und
Loaded-Grenzen. `BedBreakerDefenseFlowTest` prüft mit echten Update-, Input-,
Controller- und Transportpfaden den Legit-Verteidigungsabbau, erhaltenen nativen
Fortschritt über erneute Suchen, den Neustart bei ausgetauschtem Blockzustand,
den verdeckten Bettabbau samt ursprünglicher Mouse-over-Seite, Bettverlust im
START-Callback, unzerstörbare Hindernisse, sofort zerbrechende Blöcke und einen
Legit-Wechsel während des Abbaus. Dabei liegt die Aktion vor dem nächsten C03,
und ein doppelter Input darf denselben Spielertick nicht zweimal verbrauchen.
Weitere Modul- und Rotationstests prüfen Kontexte, Besitzer und geglättete
Übergänge; die konkreten Laufresultate gehören zum PR.

Ein automatisierter Test mit synthetischem Weltinhalt ist kein manueller
Spieltest und kein Nachweis der Annahme durch einen bestimmten Server.
