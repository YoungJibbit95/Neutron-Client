# Demise Scaffold: Quellenzuordnung des NScaffold-Ports

Referenz ist **YoungJibbit95/DemisePrivate**, Commit
`e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`. Der Port ersetzt die bisherigen
eigenständigen NScaffold-Planer. Die folgenden Dateien sind die ausführende
Quelle; passende Einstellungsnamen allein gelten nicht als Paritätsnachweis.

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
| Scaffold-Ereignisse | NScaffold.java, scoped Aufrufe an den originalen Minecraft-Positionen und passende Alpine-Listener. |
| RotationManager / RotationHandler | DemiseRotationSettings, DemiseRotationEngine, DemiseRotationHooks; nur für NScaffold. |
| MoveUtil.fixMovement | DemiseMoveFix: acht Kandidaten, originale Tie-Reihenfolge, vor Sneak-Skalierung. |
| Keybind-Richtung / Speed-Helfer | DemiseScaffoldMovement, mit gespeichertem Richtungswert bei losgelassenen Tasten. |
| findBlock / getBestRotation / rayTraceSafe | DemiseScaffoldGeometry: sechs Stufen, stabile Sortierung, Duplikate und dichte Float-Suche. |
| Blockslot / Blockzahl / Blacklist | DemiseScaffoldInventory: letzter gültiger Slot, Containerplätze 36–44, originale Blockliste. |
| SpoofSlotUtils-Consumer | displayedSlot/displayedStack in Hotbar, Itemname und ItemRenderer; echte Auswahl für Platzierung. |
| SafeWalkEvent | Scoped Ergänzung der normalen Kantenkollision für den lokalen Spieler. |
| Silent Sprint | Bei lokalem Übergang STOP-Paket, danach serverSprintState=lokaler Flag. |
| sichtbare Rotation | Originale Kopf-/Pitch-Felder, Körperbegrenzung und Renderer-Interpolation; keine Kameragleichheitsbedingung. |
| LookEvent | AbstractClientPlayer.getLook verwendet die aktuelle Handler-Rotation, ohne die Kamerawinkel zu verändern. |
| Target block ESP | DemiseScaffoldOverlay/Effects; Originalgeometrie mit Hostfarbe und Blur-/Glow-Compositor. |
| SliderComponent | DemiseSliderValue und GUI-Adapter: Float-Config ohne Quantisierung, Originalinkremente bei Bedienung. |

## Bewusst erhaltene Quelldetails

Die Normal-Rotation testet im inneren Suchlauf den **bisher besten** Strahl statt
des gerade untersuchten Kandidaten. Die Float-Schrittweite beträgt .01; die
Schleifen werden nicht durch einen gröberen Planer ersetzt. Die Nachbarsuche
dedupliziert ihre Offsets nicht. Vorherige Unterstützung wird vor dem Material-
und Interaktionsfilter berücksichtigt. Es gewinnt der letzte geeignete Hotbarslot.

Tick-Speed behält `ticksExisted - 2 % 15 == 0` mit der originalen
Operatorpriorität. Tower-move Vanilla verändert nur motionY, während der
stehende Vanilla-Tower zusätzlich EventMove.y verändert. On-miss-Clutch prüft
gegen den Stützblock, der normale rotateCheck gegen dessen Zielnachbar.

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
   vorhandenen Akzeptanzprüfungen erhalten. Keine zusätzliche Zielgeometrie,
   Platzierungsverzögerung oder eigene Bewegungskurve wird vorgeschaltet.
4. **Fehlende Daten:** Statt Nullzugriffen des Originals warten die Adapter bei
   fehlendem Spieler, Inventar, Renderer, Stützblock oder noch unbekannten
   gesendeten Blickwinkeln für die dichte Suche. Akzeptierte C03-Winkel werden
   auch bei ausgeschaltetem Modul oder fehlendem Fokus beobachtet und nur für
   dieselbe Spieler-/Welt-/Verbindungsepoche wiederverwendet. Normales Abschalten
   lässt den originalen Rotationsnachlauf zu; Welt-/Szenenverlust verwirft ihn.
5. **Gemeinsame Rotation:** Neutrons vorhandene Arbitration erhält getrennte
   Eigentümer. NScaffold korrigiert Achsen genau einmal und überschreibt keine
   Pose eines anderen aktiven Gewinners. Aura und BedBreaker werden nicht auf
   Demises Handler umgestellt.
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
Die algorithmischen Scaffold-Pfade und die dokumentierten Quelldetails werden
gegen den festgehaltenen Stand geprüft.

## Regressionen und Reproduktion

- DemiseRotationParityTest vergleicht gegen unabhängige extrahierte Original-
  Oracles: Framezeiten, Ziele, Kamera, Sensitivität, Linear/Relative,
  Beschleunigung, Stop-/Ablaufgrenzen, MoveFix-Kandidaten und Rückführungsquirks.
- DemiseRotationHookTest verbindet reale Angle-, Input-, Physik-, Paket- und
  Modellkonsumenten; freie Kamera, None/Strict/Silent, Nachlauf, fremde Owner,
  Groundcounter, Normal vor/nach dem ersten akzeptierten C03 sowie Fokuswechsel
  und ungültige Epochen sind gesonderte Fälle.
- NScaffoldFlowTest verwendet reale Modul-/Controllerpfade mit synthetischer Welt
  und lokalem Transport. Platzierung vor Input, Slotdarstellung, Telly, Clutch,
  Zähler, Sprint-Kopplung, Tower-/Speed-Zweige, RayTrace/Strict, Lifecycle und
  Aktivitätsdaten werden geprüft.
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
Laufergebnisse und Commit-SHAs stehen im zugehörigen Pull Request.
