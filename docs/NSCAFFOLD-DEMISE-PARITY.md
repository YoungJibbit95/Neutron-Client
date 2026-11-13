# Demise Scaffold: Quellenzuordnung des NScaffold-Ports

Referenz ist **YoungJibbit95/DemisePrivate**, Commit
`e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`. Der ursprüngliche Port ersetzte die
vorherigen NScaffold-Planer. Der anschließende Nutzerauftrag erweitert ausdrücklich
RayTrace, Platzierungstiming, CPS und Kantenschutz. Ein weiterer Folgeauftrag
behebt blockierte Diagonalschritte und den Verlust der Rotationsabsicht zwischen
Tower-Platzierungen. Diese Anpassungen sind unten getrennt dokumentiert;
Rotationsrechnung und MoveFix bleiben an die Referenz gebunden.

## Quellen

| Original | Git-Blob / Verwendung |
| --- | --- |
| [Scaffold.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/player/Scaffold.java) | `f5cbead2aa1d666af66946173fa358b626ac9244`: Modul, Geometrie, Slots, Addons. |
| [RotationManager.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationManager.java) | `e17d1fe4a7cd0702025e3f870f57ab454b91f3a2`: Einstellungen, Zufallswerte, Short stop. |
| [RotationHandler.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationHandler.java) | `29555b7cfca3b1e5cccad94e4d5ed06902784a31`: Frameintegration, Korrektur, Ablauf/Rückführung. |
| MoveUtil / RotationUtils / InventoryUtils / TimerUtils / MathUtils | Blob- und SHA256-Werte in [demise-e219a97.json](sources/demise-e219a97.json). |
| Minecraft-Entity-/Input-/Renderer-Dateien | Originale Produzenten für Groundcounter, Mouse PRE/UPDATE, Angle, Update, Move, Input und sichtbare Pose. |
| ItemRenderer / GuiIngame / Sprint | Slotdarstellung, Itemname, Hotbar und Vorrang der Scaffold-Sprintsteuerung. |
| RenderUtils | `c9ffb4e28a4229761624ef89572021674e33cd76`: Zielwürfel, Projektion, sechs Maskenflächen. |

Die Quell-README bezeichnet Demise als AGPL-3; eine Root-LICENSE-Datei war im
gepinnten Stand nicht vorhanden. Herkunft und vorhandene Hinweise bleiben
benannt. Der Auftraggeber hat die Nutzung ausdrücklich gestattet. Gepinnte
Originalmethoden dienen in Testressourcen als unabhängige Oracles.

## Ausführende Zuordnung

| Quellfunktion | Umsetzung im Host |
| --- | --- |
| Scaffold-Ereignisse | NScaffold.java, scoped Frame-/Input-/Move-Hooks; Vorbereitung im Update und Platzierung nach akzeptiertem C03-POST. |
| RotationManager / RotationHandler | DemiseRotationSettings, DemiseRotationEngine, DemiseRotationHooks; NScaffold verwendet den Standardpfad, BedBreaker seinen getrennten Continuous-Pfad. |
| MoveUtil.fixMovement | DemiseMoveFix: acht Kandidaten, originale Tie-Reihenfolge, vor Sneak-Skalierung. |
| Keybind-Richtung / Speed-Helfer | DemiseScaffoldMovement, mit gespeichertem Richtungswert bei losgelassenen Tasten. |
| findBlock / getBestRotation / rayTraceSafe | DemiseScaffoldGeometry bleibt für den expliziten RayTrace-aus-Pfad erhalten; bei RayTrace übernimmt NScaffoldRaycast die unten dokumentierte begrenzte Suche. |
| Blockslot / Blockzahl / Blacklist | DemiseScaffoldInventory: letzter gültiger Slot, Containerplätze 36–44, originale Blockliste. |
| SpoofSlotUtils-Consumer | displayedSlot/displayedStack in Hotbar, Itemname und ItemRenderer; echte Auswahl für Platzierung. |
| SafeWalkEvent | Scoped Ergänzung der normalen Kantenkollision für den lokalen Spieler. |
| Silent Sprint | Bei lokalem Übergang STOP-Paket, danach serverSprintState=lokaler Flag. |
| sichtbare Rotation | Originale Kopf-/Pitch-Felder, Körperbegrenzung und Renderer-Interpolation; keine Kameragleichheitsbedingung. |
| LookEvent | AbstractClientPlayer.getLook verwendet die aktuelle Handler-Rotation, ohne die Kamerawinkel zu verändern. |
| Target block ESP | DemiseScaffoldOverlay/Effects; Originalgeometrie mit Hostfarbe und Blur-/Glow-Compositor. |
| SliderComponent | DemiseSliderValue und GUI-Adapter: Float-Config ohne Quantisierung, Originalinkremente bei Bedienung. |

## Bewusst erhaltene Quelldetails

Die unveränderte Referenzgeometrie testet im inneren Suchlauf den **bisher besten**
Strahl statt des gerade untersuchten Kandidaten und dedupliziert ihre Offsets
nicht. Diese Quirks bleiben im expliziten RayTrace-aus-Pfad und dessen
Original-Oracles nachvollziehbar. Sie sind seit dem RayTrace-Folgeauftrag nicht
mehr der aktive Standardpfad. Es gewinnt weiterhin der letzte geeignete Hotbarslot.

Tick-Speed behält `ticksExisted - 2 % 15 == 0` mit der originalen
Operatorpriorität. Tower-move Vanilla verändert nur motionY, während der
stehende Vanilla-Tower zusätzlich EventMove.y verändert. On-miss-Clutch prüft
weiterhin gegen den Stützblock. Das originale rotateCheck vergleicht mit der
Zielzelle `data.blockPos.offset(data.facing)`. Trifft der Blick diese inzwischen
belegte Zelle, behält Scaffold seine bisherigen Zielwinkel und erneuert trotzdem
setRotation. Die Hostprüfung eines Platzierungsstrahls verlangt Support und Seite;
die unten beschriebene Rotationsabsicht erhält die Kontinuität unabhängig davon,
ob gerade eine freie Zielzelle zum Klicken vorliegt.

Zufall, Float-/Integer-Zwischenschritte, Sensitivitätsquantisierung, Beschleunigung,
Short-stop-Versuche, strikte Timer-Grenzen und der 150-ms-Ablauf bleiben erhalten.
Die originale Zufallsfunktion hat bei nicht ausschließlich unter eins liegenden
Grenzen die obere Schranke max+1 (außer min==max). Beschleunigung prüft den
abgeschnittenen 16.67-ms-Wert. Rückführung kann in einer diagonalen
Subgrad-Quantisierungslücke stehenbleiben; ein neuer Timeout wäre eine Abweichung.

Hover ist im gepinnten Scaffold registriert. Eine Suche über alle 2.367
Java-Dateien des gepinnten src-Baums mit verifizierten Git-Blob-IDs fand keinen
weiteren Setting-Consumer. Es wird deshalb kein eigener Hover-Algorithmus ergänzt.
Die [Audit-Zusammenfassung](sources/demise-hover-audit-e219a97.md) hält den
Quellbaum, die Consumer und die Prüfsummen des vollständigen Suchmanifests fest.

## Explizite Hostanpassungen

1. **Nutzer-Default:** Silent bleibt an; Movement correction startet auf Silent
   statt Demises None. Die auswählbaren Algorithmen bleiben die originalen.
2. **Java 8 und GUI:** Sprachsyntax, MCP-Namen und Werte-/Eventtypen werden auf
   Neutron abgebildet. MultiBool-Unteroptionen erscheinen als einzelne persistente
   Checkboxen. KillAura-only Distance-Regler sind keine NScaffold-Funktionen und
   werden nicht registriert.
3. **Szenen- und Transportvertrag:** Alte Welt-/Spieler-/Verbindungsepochen,
   unterbrochene Schritte, Menüs und reentrante Slot-/Paket-Callbacks dürfen keine
   neue Modulaktion bestätigen. Der gewöhnliche Controller bleibt mit seinen
   vorhandenen Akzeptanzprüfungen erhalten. Der Folgeauftrag ergänzt die unten
   beschriebenen geometrischen und zeitlichen Bedingungen; die Demise-
   Rotationskurve selbst bleibt unverändert.
4. **Fehlende Daten:** Statt Nullzugriffen des Originals warten die Adapter bei
   fehlendem Spieler, Inventar, Renderer, Stützblock oder noch unbekannten
   gesendeten Blickwinkeln für die dichte Suche. Akzeptierte C03-Winkel werden
   auch bei ausgeschaltetem Modul oder fehlendem Fokus beobachtet und nur für
   dieselbe Spieler-/Welt-/Verbindungsepoche wiederverwendet. Normales Abschalten
   lässt den originalen Rotationsnachlauf zu; Welt-/Szenenverlust verwirft ihn.
5. **Gemeinsame Rotation:** Neutrons vorhandene Arbitration erhält getrennte
   Eigentümer. NScaffold korrigiert Achsen genau einmal und überschreibt keine
   Pose eines anderen aktiven Gewinners. Aura behält seinen eigenen Pfad;
   BedBreaker besitzt eine eigene Engine-Instanz mit Continuous-Verhalten.
   Die Korrektur für diagonale Schritte und Rotationskontinuität verändert weder
   NScaffolds Rotationsrechnung noch BedBreakers getrennten Pfad.
6. **Externe Module:** Speed keepY verwendet den vorhandenen Speed-Schalter.
   Neutron hat kein Demise-TargetStrafe und kein omniSprint-Feld; deren externe
   Zusatzpfade werden nicht als neue unabhängige Module erfunden. Das originale
   TargetStrafe unterbindet seine Aktivität selbst, solange Scaffold aktiv ist.
   Die normale Keybind-Richtung und der ursprüngliche None-Sprint-Nachlauf sind
   implementiert. Demises FastPlace-Guard schützt einen zusätzlichen
   Frame-Platzierungspfad, den der Host nicht besitzt; der originale Timerpfad
   bleibt parallel zu Scaffold aktiv. Die zwei Scaffold-Abfragen in Demise-Speed
   gehören ausschließlich zu dessen BMC/Low-Zweig. Der Port ersetzt keine
   separaten Host-Speed-Modi; [Quellbereiche](sources/demise-external-consumers-e219a97.json)
   belegen diese Abgrenzung.
7. **Darstellung:** Demises Interface-Farbe wird auf Neutrons Interface-Farbe
   abgebildet. Die Originalmasken werden in vorhandene Blur-/Glow-Pässe eingefügt;
   Demises separates globales Shaders-Modul wird nicht vollständig ersetzt.
   RenderState schützt Matrizen, GL-Zustand und Framebuffer. Die immutable
   Island-Aktivitätsanzeige bleibt eine passive Hostintegration.

Diese Punkte begrenzen eine Aussage über binär identische Clients. Es wird kein
identisches Ergebnis auf fremden Servern und kein manueller Spieltest behauptet.
Die erhaltenen Algorithmen und Quelldetails werden gegen den festgehaltenen
Stand geprüft. Die folgenden Erweiterungen haben eigene Verhaltensprüfungen.

## RayTrace-Folgeauftrag: bewusste Änderungen

Der Nutzer meldete, dass RayTrace fast alle Platzierungen verhindert und zu
Stürzen führt, und beauftragte ausdrücklich eine schnellere Planung, Kantenschutz,
CPS sowie die Platzierungsmodi Default/Legit. Damit gelten für diesen Bereich
folgende zusätzliche Regeln:

1. **Begrenzte echte Zielgeometrie:** NScaffoldRaycast dedupliziert die Suche,
   prüft freie Zielzellen, reale Stützblock-Bounds und eine kleine Auswahl von
   Innenpunkten. Jeder geprüfte Strahl gehört zum jeweiligen Kandidaten;
   verdeckte oder unerreichbare frühe Nachbarn blockieren keine erreichbare Alternative.
   Die bevorzugte passende Ausrichtung wird übernommen. Gültige Tangentialanteile
   bleiben erhalten; die Korrektur verfehlter Anteile erzwingt keine unnötige
   seitliche Drehung. Rotations: Normal bevorzugt außerhalb von Breezily bei
   Bewegung einen geprüften rückwärts zur physischen Eingabe gerichteten Strahl
   und behält dabei einen schon
   angeforderten Pitch als Zielneigung bei. Der interpolierte Zwischenstand
   senkt das Ziel während der laufenden Drehung nicht wieder ab.
   Weitere Innenpunkte einschließlich des augennächsten Punktes bleiben
   begrenzte Alternativen. Bei noch ungültigem
   Iststrahl und tatsächlich gültigem Ziel innerhalb des Integer-Totbands darf
   ein separates Engine-Ziel einen weiteren Count anfordern. Diese Zielkorrektur
   nutzt denselben begrenzten Vorschau-Augenpunkt wie der geometrische Plan;
   feste Bridge-Winkel erhalten sie nicht. Nur die spätere Aktionsprüfung anhand
   der tatsächlich berichteten Pose kann eine Platzierung freigeben.
   Für Accelerate ermittelt der Adapter nach einem tatsächlich beobachteten
   stillstehenden Mausframe, wie viele rohe Counts die originale Float-Rundung
   überleben. Die Engine ergänzt hierfür nur einen rücksetzbaren Beobachtungswert;
   ihre Arithmetik, Geschwindigkeiten, Framezeit und Pausenkriterien bleiben erhalten.
   Ein vorausberechneter Augenpunkt dient ausschließlich der frühzeitigen Rotation.
   Bei einer neuen Brücke hat die echte Flächenmitte Vorrang vor randnahen
   Kandidaten; nach der ersten erfolgreichen Platzierung zählt die kleinste
   Winkeldifferenz. Ein bereits passender bevorzugter Strahl bleibt der schnelle Pfad.
   Die eigentliche Abfrage nutzt Vanillas Voxel-DDA. Ein numerischer Überstand
   entlang der Flächennormalen behebt zu kurze Reststrahlen innerhalb der
   unveränderten Reichweite. Chunk-Verfügbarkeit und Zustände des tatsächlichen
   Strahlpfads werden auch über Block-Callbacks hinweg überprüft.
2. **Passender Platzierungszeitpunkt:** Update bereitet vor; erst ein angenommenes
   Bewegungspaket des eigenen Rotationsbesitzers erlaubt im POST einen Versuch.
   Der Aktionsstrahl beginnt bei getLastReportedPosition plus Augenhöhe und benutzt
   die wirksamen Winkel dieses Pakets. Er muss Support, Seite, Ziel und Reichweite
   bestätigen; MISS, veränderte Zustände und neue Epochen bleiben ausgeschlossen.
   Es werden keine zusätzlichen C03-Pakete für Platzierung erzeugt.
3. **Erfolgszustand:** previousBlock wird nur nach erfolgreicher lokaler
   Controller-Platzierung fortgeschrieben, nicht nach einem MISS oder einer
   Ablehnung. Das ist keine Bestätigung eines entfernten Servers.
4. **CPS und Placement mode:** BridgePlacementCadence begrenzt Versuche mit
   monotoner Uhr, höchstens einem Versuch pro Spielertick/Bewegungsfolge und
   ohne nachgeholte Bursts. Default startet mit 20/20 CPS; Legit verlangt auch
   bei ausgeschalteter RayTrace-Checkbox einen echten Treffer und zusätzlich
   mindestens vier Spielerticks zwischen Versuchen. Die normalen Controller-,
   Slot-, Item- und Animationspfade bleiben bestehen.
5. **Kantenschutz:** NScaffoldEdgeGuard begrenzt nur den ungestützten Rest einer
   geerdeten Bewegung anhand tatsächlicher Kollisionsoberflächen. Stop ist
   Default, Sneak eine alternative frühzeitige Verlangsamung, Off deaktiviert
   diese Erweiterung. Sprungabsicht und Luftbewegung werden nicht abgefangen.
   Plan, Ray, CPS-Bereitschaft und Paketannahme ersetzen keinen realen Boden.
6. **Strict rayTrace:** Auch ohne Strict muss die tatsächliche Seite zur geplanten
   Zielzelle passen. Strict fordert darüber hinaus einen kleinen Innenabstand
   vom Rand der realen Stützfläche, ohne einen einzelnen Zielpixel vorzuschreiben.

Die 49 portierten Einstellungen bleiben erhalten; vier neue Einstellungen
heißen Placement mode, Min CPS, Max CPS und Edge safety. Alte Profile erhalten
deren Defaults, ausdrücklich gespeicherte neue Werte bleiben erhalten.

## Diagonale Schritte und Rotationskontinuität

Die erste RayTrace-Erweiterung koppelte die Erneuerung der Rotation an einen
freien Klickplan. Nach einer Tower-Platzierung war die Fußzelle belegt, die
horizontale Vorschau leer und damit kein erneutes setRotation möglich. Der
originale Handler leitete daraufhin nach mehr als 150 ms seine Kamerarückführung
ein. Außerdem konnte die Vorschau eine diagonal angrenzende Zielzelle wählen,
aber den zum Erreichen nötigen orthogonalen Zwischenschritt verwerfen. Eine vorab
vom Kantenschutz auf null gekürzte Vorschau verhinderte dann weitere Planung.

Die Folgekorrektur trennt diese Aufgaben:

1. **Gespeicherte Rotationsabsicht:** Der letzte angeforderte Zielwinkel wird
   unabhängig vom freien Klickplan gespeichert. Bei fehlendem Klickplan kann er
   erneut angefordert werden, wenn die aktuelle Fußzelle geeigneten geladenen
   Support enthält oder der letzte Support frisch bestätigt und weiterhin in
   Reichweite ist. Wiederholte Anforderungen führen die ursprüngliche Glättung
   zum gespeicherten Ziel fort. Die aktuelle interpolierte Pose wird dabei nicht
   zum neuen Ziel gemacht. Diese Prüfung liefert keine Platzierungsberechtigung.
2. **Grenzen und Lebensdauer:** Aura mit aktivem Ziel und Telly vor seiner
   Flugtick-Schwelle verhindern weiterhin die Erneuerung. Sie erzwingen keinen
   unmittelbaren Engine-Reset. Ein-/Ausschalten, Profil- und Szenenwechsel
   verwerfen den gespeicherten Intent; normales Abschalten belässt den originalen
   Rotationsnachlauf, eine ungültige Szene verwirft ihn. Ohne aktuellen
   Geometrienachweis gibt es keine unbegrenzte Erneuerung in leerer Umgebung.
   Das Original hat keinen eigenen KeepRotation-Regler und keinen abgesicherten
   `data == null`-Fallback. Die begrenzte Retention ist eine Hostanpassung an die
   strengere Trennung von Rotation und Klickplan.
3. **Vorschau und echte Bewegung:** Die Wahl der Zielzelle verwendet die
   beabsichtigte begrenzte Bewegung vor ihrer Einschränkung durch Edge safety.
   findAhead begrenzt zusätzliche Zwischenziele auf das direkte Rechteck zwischen
   Ausgangs- und Zielzelle, auf derselben Bauhöhe und mit begrenzter Suchtiefe.
   Das erlaubt die nötigen orthogonalen Schritte sowohl an diagonalen Übergängen
   als auch bei einem Vorschritt über zwei Zellen. Beliebige seitliche oder
   tiefer liegende Ziele außerhalb dieses Pfades werden nicht freigegeben.
   Bei noch belegter Fußzelle prüft eine begrenzte Kollisionsprobe für geerdete
   Bewegung ohne Sprungabsicht das erreichbare Ende der vorhandenen Stütze.
   Begrenzt der Move-Guard diese Probe, stammt der frühe Planungs-Augenpunkt
   von diesem Endpunkt. Die beabsichtigte Richtung für die Zielzellenauswahl
   bleibt davon getrennt; eine bereits gestoppte Bewegung nimmt der Suche
   nicht ihr nächstes Ziel.
   Sobald die Fußzelle leer ist, stammt der Planungs-Augenpunkt aus dem nächsten
   vom Move-Guard begrenzten Bewegungsstand. EntityPlayerSP.getPlannedMovementPosition
   berücksichtigt dabei die vorhandene Vanilla-Positionsschwelle: Eine zu kleine
   lokale Verschiebung erzeugt keinen fiktiven neuen Ursprung für das nächste
   Bewegungspaket. Als weitere Probe bleibt die entsprechende Position ohne
   horizontalen Vorschritt verfügbar. Diese Punkte dienen weiterhin nur der
   Planung; der spätere Aktionsstrahl verwendet die wirklich angenommene Pose.
   Der tatsächliche Move-Guard arbeitet unverändert mit realen Kollisionsflächen
   und gibt erst bei vorhandener Stütze Bewegung frei.
4. **Auswahl für den beabsichtigten Laufpfad:** findAhead und findMoving messen
   die Entfernung von der ungerundeten tatsächlichen Spielerposition zur
   Zellmitte. findMoving setzt diese Kontinuitätsauswahl bei bereits leerer
   Fußzelle fort, wenn eine physische Bewegungseingabe vorliegt. Die rechteckige
   Pfadbegrenzung der frühen Vorschau gehört weiterhin ausschließlich zu
   findAhead. Unter gleich nahen Kandidaten derselben Suchstufe hat ein bereits
   passender bevorzugter Strahl Vorrang. Die weiteren geprüften Flächenziele
   werden nach der nötigen zusätzlichen Drehung verglichen; die oben beschriebene
   Flächenmitte bei der Erstausrichtung bleibt erhalten. Ein gemerkter bester
   Kandidat wird nach weiteren Such-Callbacks vor der Rückgabe erneut auf freie
   Zielzelle, unveränderten Supportzustand und Bounds sowie gültige Lebensdauer
   geprüft. Ohne physische Bewegungseingabe verwendet die Recovery-Suche find
   weiterhin die am Original orientierte bisherige Distanz- und Nachbarreihenfolge.
5. **Unveränderte Aktionsprüfung:** Im RayTrace-Pfad benötigt der Klick weiterhin
   die aktuelle angenommene C03-Pose, einen gültigen Treffer auf Support und Seite,
   die freie Zielzelle, Controller-Reichweite und verfügbare CPS-/Legit-Kadenz.
   Die Lebensdauer wird nach Welt-, Block- und Paket-Callbacks erneut geprüft.
   DemiseRotationEngine, MoveFix, die Rotationseinstellungen und BedBreakers
   Continuous-Pfad erhalten für diese Korrektur keine neue Rotationsrechnung.

Eine erste Aktivierung ohne zuvor angeforderten Zielwinkel kann aus der bloßen
Retention keine Ausrichtung erzeugen. Es wird dafür kein Kamera- oder Nullwinkel
als vermeintlich vorhandenes Scaffold-Ziel eingesetzt.

## Regressionen und Reproduktion

- DemiseRotationParityTest vergleicht gegen unabhängige extrahierte Original-
  Oracles: Framezeiten, Ziele, Kamera, Sensitivität, Linear/Relative,
  Beschleunigung, Stop-/Ablaufgrenzen, MoveFix-Kandidaten und Rückführungsquirks.
- DemiseRotationHookTest verbindet reale Angle-, Input-, Physik-, Paket- und
  Modellkonsumenten; freie Kamera, None/Strict/Silent, Nachlauf, fremde Owner,
  Groundcounter, frühe Zielplanung ohne erfundene C03-Historie sowie Fokuswechsel
  und ungültige Epochen sind gesonderte Fälle.
- NScaffoldFlowTest verwendet reale Modul-/Controllerpfade mit synthetischer Welt
  und lokalem Transport. Annahmegebundene POST-Platzierung, echte bewegte Brücken,
  CPS, Legit-Pause, Slotdarstellung, Telly, Clutch, Zähler, Sprint-Kopplung,
  Tower-/Speed-Zweige, RayTrace/Strict, Lifecycle und Aktivitätsdaten werden geprüft.
  Zusätzliche Bewegungsfälle führen Default und Legit in allen vier X-/Z-
  Vorzeichenquadranten bei 30, 60 und 144 FPS aus. Beide Achsen müssen vorankommen;
  reale Treffer, Bodenstütze, Kamera, C03-vor-C08 und Kadenz bleiben eigene Gates.
  Pro Lauf sind mindestens zwölf Blockeinheiten Fortschritt entlang der
  Diagonale, sechs je Achse und zwölf Platzierungen erforderlich. In Ticks
  21–100 gelten höchstens drei aufeinanderfolgende Stillstandsticks und acht
  Stillstandsticks zusätzlich zu belegten Legit-Wartezeiten. Eine solche
  Wartezeit erfordert während der nativen Vier-Tick-Sperre einen passenden
  angenommenen Ray, eine freie Zielzelle und den eigenen Rotationsbesitz.
  Der frühestmögliche erfolgreiche POST-Klick darf ebenfalls einen letzten
  Stillstandstick erklären, da die Physik bereits vor diesem Klick lief.
  Die Legit-Pause wird damit anhand des einzelnen Ticks belegt; die übrigen
  Fortschritts-, Zeit- und Kollisionsbedingungen bleiben eigenständig.
  Der stationäre Jump-Tower verwendet den ursprünglichen Sprungwartezyklus und
  echte Sprung-/Reise-/Kollisionsmethoden. Er verlangt wiederholte Platzierungen
  sowie mehrere belegte Abschnitte von mehr als 150 ms ohne Klickplan und prüft
  die aktive Zielerneuerung und den Pitch-Verlauf während dieser Abschnitte.
  Retentionsfälle prüfen Aura-/Telly-Grenzen, Ausschalten, Epochenwechsel und
  eine Aura-Zielübernahme während der tatsächlichen Stützblock-Abfrage.
- NScaffoldRaycastTest / NScaffoldEdgeGuardTest prüfen reale Treffer, Verdeckung,
  begrenzte Sucharbeit, die Vorschau über direkte Zwischenzellen, die Auswahl
  zwischen gleich nahen Kandidaten, aktuelle Callback-Lebensdauer und den
  zusammenhängend gestützten Weg über reale Fuß-Kollisionsflächen.
- DemiseScaffoldGeometryTest / InventoryTest belegen die Quellsuchreihenfolge,
  Float-Abtastung, Ray-Miss-Fallback, Blacklist und letzte Slotwahl.
- Config-/Layout-Tests prüfen Migration, Originalnamen-Vorrang, Defaults,
  Float-Erhaltung und versteckte Auswahlwerte.
- DemiseScaffoldOverlayNativeCheck läuft im bestehenden nativen WorldOverlay-
  Gate: tatsächliche Pixel, Weltwürfel, schwarze/farbige Maske, Compositor,
  GL-/Matrix-/FBO-Wiederherstellung, Wiederverwendung und Ressourcenfreigabe.

Buildvoraussetzung: Temurin 8u482 und Maven 3.9.15 laut
[DEVELOPMENT-WORKFLOW.md](DEVELOPMENT-WORKFLOW.md). Die vollständige Windows-Suite
und die nativen Linux-Gates bleiben unverändert erforderlich. Die konkreten
Laufergebnisse und Commit-SHAs stehen im zugehörigen Pull Request; die Beschreibung
des Prüfumfangs nimmt deren Erfolg nicht vorweg. Synthetische Welt-, Controller-
und Transportprüfungen bestätigen weder einen manuellen Spieltest noch Verhalten
auf einem entfernten Server.
