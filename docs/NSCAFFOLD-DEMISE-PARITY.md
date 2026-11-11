# NScaffold und Demise Scaffold: Funktionsvergleich

Stand: 11. November 2026. Dieser Vergleich beschreibt den aktuellen
Phase-2-Implementierungsstand von NScaffold und die unten gepinnte Demise-Quelle.

**NScaffold ist weder eine identische Kopie noch ein vollständiger Funktionsport
des Demise Scaffold.** Es ist eine eigenständige sichtbare Bauhilfe, die normale
Nachbar- und Flächenwahl sowie eine geometrische Godbridge-Präferenz auf Neutrons
vorhandenen Controller- und Rotationspfaden umsetzt. Gemeinsame Begriffe und
ähnliche Teilfunktionen bedeuten keine Verhaltensgleichheit.

## Geprüfte Quellen

Die Originaldateien wurden über den GitHub-Connector gelesen. Bezugsstand ist
Commit `e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb` in
`YoungJibbit95/DemisePrivate`:

- **[D1]** [Scaffold.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/Scaffold.java),
  Blob `f5cbead2aa1d666af66946173fa358b626ac9244`.
- **[D2]** [RotationManager.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationManager.java),
  Blob `e17d1fe4a7cd0702025e3f870f57ab454b91f3a2`. Dieser Manager registriert
  zusätzliche Einstellungen am aufrufenden Modul und gehört deshalb zum Vergleich.
- **[D3]** [README.md](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/README.md),
  Blob `ac3c47ef7c38a6180b201f2bea2b586a43f9de7b`.

Die im selben Durchgang erneut gelesene aktuelle `main`-Fassung von
`Scaffold.java` war textgleich mit [D1]. Der gepinnte Commit bleibt die
reproduzierbare Referenz dieses Dokuments.

Die Neutron-Seite des Vergleichs ergibt sich aus:

- **[N1]** [NScaffold.java](../src/main/java/me/youngjibbit/module/impl/world/NScaffold.java)
- **[N2]** [NScaffoldPlanner.java](../src/main/java/me/youngjibbit/core/world/NScaffoldPlanner.java)
- **[N3]** [ScaffoldPlacementService.java](../src/main/java/me/youngjibbit/core/world/ScaffoldPlacementService.java)
- **[N4]** [Bedienungs- und Herkunftsdokumentation](NSCAFFOLD.md)
- **[N5]** [pom.xml](../pom.xml)

Der Vergleich beschreibt deklarierte Einstellungen und den gelesenen Ablauf.
Er enthält keinen Nachweis gleicher Spielergebnisse, gleicher Bewegung oder
gleichen Verhaltens gegenüber einem Server.

## Hauptfunktionen und Einstellungen

Die Originalbezeichnungen in dieser Tabelle stammen aus [D1]. **Adaptiert**
bedeutet, dass NScaffold eine eigene verwandte Funktion besitzt. **Fehlt**
bedeutet, dass die betreffende Originaloption in NScaffold nicht implementiert
ist. Beide Angaben schließen eine Behauptung vollständiger Gleichheit aus.

| Gruppe | Demise Scaffold | Aktueller NScaffold-Stand |
| --- | --- | --- |
| Hauptmodus | `Mode`: `Normal`, `Breezily`, `Telly` | **Fehlt als ursprüngliche Modellauswahl.** NScaffold hat `Visible Rotation: Standard / Godbridge`. `Standard` ist kein umbenanntes, identisches Demise-`Normal`; Breezily- und Telly-Abläufe fehlen. |
| Telly-Steuerung | `Straight telly ticks`, `Diagonal telly ticks` | **Fehlt.** Keine Telly-Taktung und keine automatische Telly-Eingabefolge. |
| Rotationsziel | `Rotations`: `Normal`, `Center`, `GodBridge`, `Reverse` | **Teilweise adaptiert.** Nur eigene Standard-/Godbridge-Zielgeometrie. Die ursprüngliche Vierer-Auswahl und ihre Abläufe werden nicht abgebildet. |
| Feste Rotationspräferenz | `Static-ify` | **Fehlt als Originaloption.** Das Halten eines gültigen aktuellen Sichtstrahls und einer aufgenommenen Richtung ist eine eigene Planerregel und kein gleichwertiger Ersatz für diese Einstellung. |
| Clutch-Auswahl | `Clutch`; `Clutch criteria: MouseOver / Prediction`; `Clutch rotation mode: Normal / Center` | **Fehlt als eigenständiges Clutch-System.** Eine während eines Falls normal erreichbare Fläche kann im gewöhnlichen Platzierungspfad benutzt werden. Daraus folgt keine Gleichheit mit dem Demise-Clutch und keine Rettungsgarantie. |
| Clutch-Reaktion | `Instant rot criteria`: `Always`, `On miss`, `Predict` | **Fehlt.** NScaffold besitzt keine entsprechenden Sofortrotationskriterien. `Turn Speed` ist eine eigene begrenzte sichtbare Winkeländerung. |
| Clutch-Sneak | `Auto sneak on clutch`, `Ground ticks to sneak` | **Fehlt.** NScaffold setzt keine Sneak-Taste und keine Clutch-Sneak-Sequenz. |
| Sprint | `Sprint mode`: `Normal`, `Silent`, `Ground`, `Air`, `None` | **Fehlt.** NScaffold erzwingt und unterbindet Sprint nicht. Die normalen Eingaben und sonstigen Clientregeln bleiben maßgeblich; es gibt keine entsprechende Sprint-Modusauswahl. |
| Geschwindigkeit | `Speed mode`: `Tick`, `Exempt`, `Diagonal`, `None` | **Nicht übernommen.** NScaffold besitzt keine dieser Geschwindigkeitsfunktionen und verändert die Bewegungswerte nicht für einen solchen Modus. |
| Höhenhaltung | Addons `KeepY`, `Speed keepY` | **Adaptiert.** `Keep Height` hält eine begrenzte Zielebene mit eigenen Rücksetzbedingungen. Eine Kopplung an das Demise-Speed-Modul und dessen Höhenregel fehlt. |
| Automatisches Springen | Addon `Jump`, `Blocks to jump`, `Only straight` | **Fehlt.** Keine blockzahlabhängige Sprungfolge und keine automatische Sprungtaste. |
| Automatisches Sneaken | Addon `Sneak`, `Blocks to sneak`, `Only sneak on ground` | **Fehlt.** Keine blockzahlabhängige Sneak-Folge. |
| Kanten- und Schwebefunktionen | Addons `Safewalk`, `Safewalk when no data`, `Hover` | **Fehlen.** Der NScaffold-Planer begrenzt seine Platzierungsziele; er stellt damit keine entsprechende Bewegungs- oder Kantenautomatik bereit. |
| Tower | `Tower` und `Tower move`, jeweils `Jump`, `Vanilla`, `PullDown`, `NCP`; zusätzlich `PullDown motion` | **Nicht übernommen.** NScaffold hat weder diese beiden Modusauswahlen noch eine eigene Tower-Bewegungssteuerung. |
| Raytrace | Addon `RayTrace`, zusätzlich `Strict rayTrace` | **Anders umgesetzt.** NScaffold verlangt immer eine gültige erste Weltfläche in normaler Controller-Reichweite und den dazugehörigen Treffer. Diese Prüfungen sind keine wahlweise abschaltbaren Kopien der Originaloptionen. |
| Handanimation | Addon `Swing` | **Adaptiert.** `Swing` steuert eine normale sichtbare Handanimation nach lokal erfolgreicher Platzierung. Die weiteren Originalpfade werden dadurch nicht übernommen. |
| Zielblockdarstellung | Addon `Target block ESP` mit Render-/Shaderanbindung | **Fehlt als gleichartige Darstellung.** Neutrons separates `TargetESP` kann den tatsächlich angewandten NScaffold-Sichtstrahl markieren; ein Hitpoint-Marker ist nicht dieselbe Anzeige wie ein geplanter Zielblock. |
| Hotbar und Slotdarstellung | Automatische Blockwahl sowie eigene Slotdarstellung und Rücksetzung; kein gleichnamiger `Auto Select Blocks`-Schalter in [D1] | **Anders umgesetzt.** `Auto Select Blocks` erlaubt normalen sichtbaren Slotwechsel. Der aktuelle brauchbare Stack wird bevorzugt, andernfalls der erste geeignete Hotbar-Stack. Die Originalwahl und die globale Slotdarstellung sind nicht übernommen. |
| Blocksuche und gehaltene Unterstützung | Direkte Nachbarprüfung, bevorzugte frühere Unterstützung und eine weiterreichende Suche | **Adaptiert.** NScaffold prüft die benötigte Zelle und höchstens zwei diagonale Verbindungszellen mit fünf Nachbarflächen. Die daraus folgenden höchstens 15 neuen Flächenprüfungen sind ein eigenes Suchbudget; die Originalsuche wird nicht reproduziert. |
| Platzierungsablauf | Eigene Ereignisfolge für Zielwahl, Rotation und Platzierung | **Anders umgesetzt.** NScaffold verwendet Neutrons bestehende Platzierungsautorität, frische Szenen-/Owner-Prüfungen und ein gemeinsames Tickbudget. `Placement Delay` ist eine zusätzliche eigene Einstellung. |
| Weltwechsel | `onWorld` schaltet das Originalmodul aus | **Anders umgesetzt.** Alte NScaffold-Pläne werden immer verworfen. `Smart Disable on World Change` entscheidet zusätzlich über das Abschalten und ist standardmäßig aus. |

## Zusätzliche Einstellungen des Demise-RotationManager

Ein Vergleich nur der Deklarationen in `Scaffold.java` wäre unvollständig:
`new RotationManager(this)` registriert die folgenden Gruppen aus [D2]. Ihre
Algorithmen und Einstellungssätze wurden nicht in NScaffold kopiert.

| Gruppe aus [D2] | Registrierte Originaleinstellungen | NScaffold |
| --- | --- | --- |
| Sichtbare oder verborgene Ausrichtung | `Silent` | Ausschließlich tatsächliche sichtbare Kameraausrichtung; kein entsprechender Schalter. |
| Glättung | `Extra smoothing factor`; `Smooth mode: Linear / Relative` | Keine gleichnamigen Optionen und keine Übernahme dieser Interpolation. |
| Beschleunigung | `Accelerate`, `Yaw accel factor`, `Pitch accel factor` | Fehlt. `Turn Speed` ersetzt keine getrennten Beschleunigungsmodelle. |
| Zusammenhang der Achsen | `Imperfect correlation` | Fehlt. |
| Getrennte Rotationsgeschwindigkeiten | `Yaw rotation speed (min/max)` und `Pitch rotation speed (min/max)` | Fehlen als vier separate Grenzen. NScaffold besitzt eine gemeinsame maximale Winkeländerung pro regulärem Schritt. Die Originalvariation wird nicht reproduziert. |
| Bewegungskorrektur | `Movement correction: Silent / Strict / None` | Nicht übernommen. Die tatsächliche Kamera ist die normale Bewegungsbasis; NScaffold schreibt keine gegengerichteten Bewegungseingaben. |
| Kurze Unterbrechungen | `Short stop`, `Duration`, `Rotation diff buildup to stop`, `Max threshold attempts to stop` | Fehlen. |

Der Manager registriert außerdem `Distance based rotation speed`, `Min range`,
`Max range` und `Decrement per cycle`. In der geprüften Revision beschränkt deren
Sichtbarkeitsbedingung diese Gruppe auf `KillAura`. Sie wird hier deshalb nicht
als ursprünglich sichtbare Scaffold-Einstellungsgruppe gezählt. Verweise auf
weitere Namen in Sichtbarkeitsbedingungen erweitern auch nicht automatisch die
tatsächlich deklarierte Auswahl `Linear / Relative`. [D2]

## Was die aktuelle sichtbare Bauhilfe tatsächlich bietet

Die vollständige eigene NScaffold-Einstellungsliste lautet: `Visible Rotation`,
`Turn Speed`, `Godbridge Pitch`, `Look Ahead`, `Keep Height`, `Auto Select Blocks`,
`Placement Delay`, `Swing`, `Smart Disable on Death` und
`Smart Disable on World Change`. Die beiden Smart-Disable-Optionen sind
standardmäßig aus. [N1, N4]

Standard und Godbridge benutzen denselben normalen Controllerpfad mit gültiger
Reichweite, tatsächlich getroffener Weltfläche und frischer Rotationszuordnung.
Ein gültiger aktueller Sichtstrahl behält seine exakten Winkel. Godbridge ergänzt
eine bevorzugte Richtungsgeometrie und einen einstellbaren Abwärtswinkel; bei
fehlender passender Fläche wartet das Modul. Das ist keine Zusage, die originale
Godbridge-Körperhaltung, alle diagonalen Abläufe oder dessen Clutch zu kopieren.
[N1–N3]

Die Bedienung ist sichtbar und kamerabezogen: Stillstand oder Rückwärtsbewegung
mit **S**, gegebenenfalls zusammen mit **A/D**, sind die vorgesehenen Eingaben.
**W** und reine Seitwärtsbewegung pausieren mit `Hold back to bridge`. Manuelle
Kamerabewegung pausiert mit `Manual view`; nach ruhender Kamera kann eine neue
Richtung aufgenommen werden. NScaffold setzt keine Bewegungs-, Sprint-, Sprung-
oder Sneak-Tasten. Ein aktiver bisheriger Scaffold, ein aktives Aura-Ziel oder
eine Aura-Aktion sowie ungeeignete GUI-/Szenenkontexte haben Vorrang. [N1, N4]

Der gecachte Island-Status und der getrennte TargetESP-Hitpoint sind zusätzliche
Neutron-Anbindungen. Sie verändern keine Spielaktion. Ein lokal angenommener
Rotationswinkel oder ein erfolgreicher Controller-Aufruf ist keine
Serverbestätigung. [N1, N4]

## Ausgelassene Subsysteme und Laufzeitunterschiede

Die Referenz enthält neben normalen Bauabläufen auch auf Serverprüfungen
zugeschnittene Bewegungs- und Paketvarianten. Diese Varianten sowie der globale
Slot-, Paket- und Eingabeüberschreibungsverbund wurden nicht portiert. Die Tabellen
nennen betroffene Einstellungsgruppen zur Abgrenzung; sie dokumentieren keine
Umgehungsverfahren. Aus ihrer Auslassung folgt unmittelbar, dass eine vollständige
Verhaltensgleichheit mit [D1–D2] nicht besteht. Eine Aussage über geringere
Erkennungs- oder Flag-Raten ist damit ebenfalls nicht belegt.

Die Demise-README verlangt **Java/JDK 17** und beschreibt einen Gradle-Build.
Neutron setzt in `pom.xml` Java-Quell- und Zielversion **1.8**; der bestehende
Projektworkflow verwendet Java 8. Hinzu kommen unterschiedliche Ereignis-,
Wert-, Rotations- und Controller-Schnittstellen. Unverändertes Einfügen der
Originaldatei wäre deshalb auch technisch kein abgeschlossener Port. [D3, N5]

Der Nutzer hat die Nutzung der Quelle bestätigt. Die gelesene Demise-README
nennt **AGPL-3**, Neutrons bestehende [LICENSE](../LICENSE) nennt GPL v3.
Der Herkunftsnachweis hält diese Angaben fest und erklärt weder die Lizenzen für
identisch noch zusätzliche Nutzungsbedingungen für entfallen. [D3]

## Aktueller Stand der verlangten Gleichheit und Mergebedingung

Der Nutzer hat zuletzt verlangt, dass **alle Demise-Scaffold-Einstellungen und
Abläufe vor einem Merge identisch übernommen sein müssen**. Diese Bedingung ist
mit dem hier beschriebenen NScaffold **nicht erfüllt**. Das gilt auch unabhängig
von den ausgelassenen prüfungsspezifischen Varianten: Bereits Hauptmodi,
Clutch-Auswahl, Sprint, Tower und große Teile der Rotationseinstellungen fehlen
oder unterscheiden sich wesentlich.

Ein grüner Build, bestandene Regressionen und ein technisch mergebarer PR können
die implementierten Neutron-Funktionen bestätigen. Sie belegen keine
Demise-Gleichheit und erfüllen diese zusätzliche Nutzerbedingung nicht. Der
Phase-2-PR bleibt daher eine überprüfbare Lieferung des abgegrenzten
Implementierungsstands; die verlangte vollständige Gleichheit und die daran
geknüpfte Freigabe zum Merge in `main` bleiben offen. Ein Merge nach `main` ist
unter der aktuellen Nutzerbedingung damit nicht freigegeben.

Dieses Dokument beschreibt den Vergleich und den offenen Abnahmepunkt. Es
behauptet weder eine abgeschlossene Spielprüfung noch eine erfolgreiche aktuelle
CI-Ausführung oder einen bereits erfolgten Merge.
