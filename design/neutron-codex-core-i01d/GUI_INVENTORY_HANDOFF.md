# CORE-I01d — GUI-/Inventar-Handoff für MOD

Stand: 28.10.2026. Basis `382e995496af336aab07ca1cf6f8a41e4e54160c`.
**Vorschlag und Prüfvertrag, keine implementierte GUI-Zulassung.**
InventoryManager, Stealer, AutoHealInventoryTransfer und Controller bleiben
bytegleich zur Basis. Dieser Bericht startet keinen MOD-Auftrag und erweitert
die Freigabe nicht auf GUI-Automation oder neue Produkteinstellungen.

## Herkunft und präziser Ist-Befund

Primärquelle ist der lokal geprüfte Java-Code im neuen Worktree; ZIP-Berichte
und v1.0 erklären Anforderungen, ersetzen aber keine Runtime-Evidenz.
Die neun Basissuiten wurden neu ausgeführt; die neuen GUI-Regeln wurden
weder implementiert noch mit nativer GUI oder Server abgenommen.

| Lokale Quelle | Tatsächlicher Befund |
| --- | --- |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\module\impl\player\InventoryManager.java:99` | EventUpdate-Listener verlangt toggled und `GuiInventory`; `closeInvServerSide()` läuft davor, bevor weitere Fachprüfungen zurückkehren |
| dieselbe Datei, 111–270 | OnlyStill/AutoBridger, Scans, Trashschleife, Armor Drop/Equip und fünf Sortzweige; mehrere gekoppelte `windowClick` sind möglich, abhängig von erneuten Delayprüfungen |
| dieselbe Datei, 276/285/294/338 | Open/Close-Status mit C0B/C0D, Start-/Actiondelay, Trash-Revalidierung nach Open-Callbacks; Hotbarslot wird auf Containerslot `slot+36` abgebildet |
| dieselbe Datei, 87 | Enable bei GuiChest ruft geerbtes `Module.onUpdate()` auf, **nicht** den gleichnamigen `Listener<EventUpdate>`; Basismethode ist leer |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\module\impl\player\Stealer.java:44` | Enable setzt Timer nur bei GuiChest zurück |
| dieselbe Datei, 59 | Listener prüft tatsächlich `openContainer instanceof ContainerChest`, **nicht ausdrücklich GuiChest** und nicht zuerst null-Player; Beschreibung „bei GuiChest“ ist allein keine vollständige Ist-Bedingung |
| dieselbe Datei, 63–75 | OpenDelay, frisch erzeugte ggf. gemischte Slotliste, nichtnull Stack, Actiondelay mit Reset, mode-1 Click, danach Empty/AutoClose/CloseDelay und `closeScreen()` |
| dieselbe Datei, 82 | Empty-Prüfung nimmt 54 Chestslots bei 90 Containerslots, sonst 27 an; andere Containerformen benötigen eigenen Nachweis |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\core\observation\MinecraftSceneSource.java:22` | jede offene GUI bedeutet allgemein SUSPENDED; Pause und Fokusverlust ebenfalls, ohne unterscheidbaren Reason im SceneContext |
| `F:\neutron-core-i01d\src\main\java\net\minecraft\client\entity\EntityPlayerSP.java:175` | EventUpdate ist PRE vor Physik und vor Cancelprüfung; kein reguläres EventUpdate POST |
| `F:\neutron-core-i01d\src\main\java\net\minecraft\client\multiplayer\PlayerControllerMP.java:594` | Vanilla windowClick predictet und sendet; gleicher Pfad für manuelle UI und Module; keine modulbezogene Admission |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\module\impl\player\AutoHealInventoryTransfer.java:94` | ein mode-2 Swap in Window 0; ACK-/Quarantäne-/Resync-Lifecycle ab 133; optionaler Transfer ist kein GUI-Stealer |

`SceneContext` enthält `Availability`, schwache Player-/World-/Connectionidentität,
Generation und Packet-Epoch; es enthält weder GUI-/Window-/Containeridentität
noch Suspendgründe. `AutomationEligibility` verlangt ACTIVE ORDINARY und ist
deshalb unverändert **keine** GUI-Zulassungsfunktion. Ein pauschales Umetikettieren
von SUSPENDED in ACTIVE würde GUI, Pause und Fokusverlust unzulässig vermischen.
Auch die im TickEvent-Vertrag belegte fehlende öffentliche aktive Tickdauer
ist bei einer späteren GUI-Policy als API-Abhängigkeit zu berücksichtigen.

## Vorgeschlagene Zulassungsgrenze

Neue Modulstarts benötigen einen eng gefassten GUI-Fachkontext mit mindestens:

- derselben lebenden Playerinstanz, World, Connection/Pipeline und gültiger
  Generation/Epoch; im Zweifel verweigern und frisch planen;
- derselben vom Benutzer geöffneten GUIinstanz und dem erwarteten GUItyp;
  für InventoryManager GuiInventory/Playerinventory, für Stealer GuiChest/Chest;
- exakt demselben `openContainer` und erwarteter Window-ID, zugehöriger
  Slotliste, Source-/Destinationstack, Cursorstack und Controller;
- erlaubter tatsächlicher Eventphase und aktivem Outer-Tick; bei EventUpdate
  Ordinary als **Startphase**, Earned Extra ohne neue Starts und ohne AutoClose;
- eigenständiger Prüfung von Pause und Fokus. Vorschlag: bei Pause/Fokusverlust
  keine neuen Automationsstarts; abweichendes GUIverhalten bleibt ein explizites
  Fachentscheidungs-Gate und ist hier nicht als Nutzerfreigabe gesetzt;
- Prüfung manueller Konkurrenz vor Planung, vor jedem nächsten Klick und nach
  reentranten Packet-/Open-Callbacks; keine Wiederaufnahme eines alten Plans
  allein durch Rückkehr derselben Window-ID.

Die allgemeine Scene bleibt dabei SUSPENDED. Die Ausnahme betrifft ausschließlich
den ausdrücklich passenden GUI-Consumer und erlaubt weder AutoHeal-Itemuse,
Scaffold-Placement noch andere synthetische Aktionen in dieser Scene.
Cursor-/Drag-/Shiftclick-/Hotbar-Gesten müssen als manuelle Konkurrenz behandelt
werden; deren genauer Arbitrationvertrag ist unten offen. Physische Controller-
und GUI-Aktionen dürfen ohne Ordinary oder aktiven Automationskontext weiterlaufen.
Kein Gate an globalem `windowClick`, `rightClickMouse` oder PacketPipeline.

WITHHELD führt keinen EventUpdate-Body aus. Ein späterer Withhold widerruft keine
vorher zulässige TickEvent-Aktion. Ein Earned Extra wiederholt den Player-Body,
aber nicht den äußeren TickEvent. Eine nachträgliche EventUpdate-Cancellation
kann vor ihr bereits ausgeführte Listenerwirkungen nicht rückgängig machen;
die spätere MOD-Implementierung muss diese Listenerreihenfolge ausdrücklich testen.

## Logical transaction / sequence gegenüber C0E

Die Produktaussage „maximal einmal pro Outer-Tick“ darf hier nicht als
„höchstens ein C0E insgesamt“ implementiert werden. InventoryManager kann zuerst
alte Rüstung droppen und danach neue equippen oder mehrere Sort-/Trashzweige
bearbeiten. Stealers Schleife kann bei sehr kleiner/Null-Delay mehrere Slots
anfassen; `hasTimeElapsed`-Vergleich und tatsächlich vergangene Zeit entscheiden.
Weder alle Klicks eines Listeners noch genau ein einzelner Klick sind bereits
als korrekte fachliche Transaktion beschlossen.

**Vor einer MOD-Änderung zu prüfen:** Welche Operation bildet eine logische
Sequenz, welche Teilklicks gehören untrennbar dazu, welche sind unabhängig,
wie viele Sequenzstarts sind je Modul bzw. gemeinsam je Outer-Tick zulässig,
welche Delay-/ACK-Grenzen liegen zwischen den Teilklicks und wann muss ein
begonnener Ablauf ohne weiteren Start vollständig/sicher abgewickelt werden?
Für Armor Drop→Equip muss etwa geklärt werden, ob erst eine Serverbestätigung
benötigt wird und wie bei inzwischen verändertem Ersatzstack fortgesetzt wird.
Ein hartes Einklicklimit darf die sichere Fortsetzung nicht still halbieren.

Vorgeschlagene Identität einer Sequenz ist konzeptionell Consumer + Session/
Sceneversion + GUI-/Containerinstanz + Window-ID + frische Planidentität;
jedem C0E bleiben seine echte Actionnummer, Slot, Button, Mode und Stacknachweise
zugeordnet. Diese Beschreibung führt keine neue Java-API, keinen Manager und
keine globale Domainreservation ein. Window-ID oder Short-Actionnummer allein
reichen wegen Wiederverwendung nicht. Startzählung und noch zulässige
Fortsetzung einer bereits gestarteten Sequenz sind getrennte Entscheidungen.

Bei Denial keine neuen C0E-/C0B-/C0D-/CloseScreen-Wirkungen, keine konsumierte
Actioncadence und keine fremde Slot-/Key-/Timer-Rücksetzung. Bereits nötige
Session-/Prediction-/Pending-Bereinigung folgt ihren eigenen Nachweisen.
Lokale Prediction ist kein ACK. Reject und Timeout dürfen kein automatisches
Retry auf eine unbestätigte Prediction freigeben; erst autoritativer angewandter
Resync plus gültiger neuer Kontext erlaubt Neuplanung nach Fachvertrag.

## Stealer und AutoHeal-Transfer

Stealer muss OpenDelay an die tatsächliche GUI-/Containeröffnung binden;
Enable bei bereits geöffneter Chest ist ein eigener Übergang. Slotanzahl,
Slotreihenfolge/Randomness, Source-Stack und Zielkapazität sind neu zu prüfen.
ActionDelay und CloseDelay teilen derzeit `actionTimer`; ein vorgeschlagenes
Gate darf Extra-Denial nicht als Aktion zählen oder damit AutoClose auslösen.
AutoClose ist selbst eine automatisierte GUIwirkung mit Identitätsprüfung
unmittelbar vor `closeScreen()`. Ein inzwischen vom Nutzer gewechseltes Window
darf niemals über einen alten Chestplan geschlossen werden. Defaults, Delays
und Settings werden in I01d nicht geändert.

AutoHealInventoryTransfer bleibt ein separater Transaktionslebenszyklus:
Start → exakte lokale Prediction → lokal akzeptierter C0E → Pending →
passender angewandter ACK oder Reject/Quarantäne → autoritativer Resync.
Der Timeout beträgt aktuell 5 Sekunden und quarantiniert; er akzeptiert nichts.
Slot-Restore betrifft weiterhin nur nachweislich eigene unveränderte Zustände.
Der Transfer-Observer kann bei AutoHeal Disable absichtlich subscribed bleiben,
bis die offene Transaktion abgewickelt ist. ACK/Reject/Timeout/Restore benötigen
keinen CurrentStep und dürfen auch bei verweigertem **Neustart** weiterarbeiten.
World-/Disconnect-/Pipelinewechsel invalidieren Kontext; kein Retry darf eine
alte Prediction oder alte Slotlease in eine neue Session übernehmen.

## Konkrete MOD-Testmatrix

Status: **V** = vorgeschlagener vollständiger Test, nicht ausgeführt;
**B** = nur der ausdrücklich benannte Teilaspekt durch neu ausgeführten Bestandstest
belegt; **S** = extern ausgeführte I01d-Sonde, keine implementierte GUI-Policy.
Jede Zeile benennt Kontext, Phase, Operation und Soll. Ein B/S ist kein Pass
für die gesamte vorgeschlagene GUI-Zulassung. Zahlen der Läufe siehe RESULTS.

| ID | Kontext | Phase | Operation | Erwartetes Ergebnis | Tatsächlicher Nachweisstatus |
| --- | --- | --- | --- | --- | --- |
| G01 | GuiInventory, gültiger Player/Window 0, Fokus, nicht pausiert, generisch SUSPENDED | Ordinary EventUpdate PRE | ein unabhängiger Trash-/Sortstart | GUI-spezifische Prüfung kann Start zulassen; Scene bleibt SUSPENDED | V; nur statischer Consumerbefund |
| G02 | derselbe Outer-Tick wie G01 | Earned Extra EventUpdate | erneuter Trash-/Sortstart | kein neuer Sequenzstart und kein konsumierter Actiondelay | V |
| G03 | GuiChest und derselbe Container | Ordinary dann Extra | Stealer C0E | Ordinary nach Fachdelays möglich, Extra startet nichts | V |
| G04 | leere Chest, AutoClose bereit | Earned Extra | closeScreen | keine Extra-Schließung, kein Timerreset | V |
| G05 | GUI geöffnet, Tickbase hält zurück | WITHHELD | EventUpdate-/C0E-Erzeugung | kein Player-Body, keine Modulaktion; physische GUI bleibt erreichbar | B: RuntimeObservationHookTest prüft echtes Withhold, GUIkombination V |
| G06 | GuiInventory/GuiChest ohne aktiven Outer-Tick | direkter Listener-/EventUpdateaufruf | neuer Modulstart | verweigern; kein historischer Tick als Freigabe | V; Observer-API-Gate im TickEvent-Vertrag separat belegt |
| G07 | erwartete GUI, Pause=true | Ordinary GUI-Event | neuer C0E oder AutoClose | Vorschlag verweigert; keine pauschale SUSPENDED-Ausnahme | V |
| G08 | erwartete GUI, Fokus=false | Ordinary GUI-Event | neuer C0E oder AutoClose | Vorschlag verweigert; nach Resume frisch prüfen | V |
| G09 | Inventory→Chest oder Chest→andere GUI zwischen Planung und Klick | Ordinary/reentrant Callback | weiterer Sequenzklick | alten Plan abbrechen/quarantinieren, neues GUI nicht anfassen | V |
| G10 | neue Containerinstanz mit wiederverwendeter Window-ID | Ordinary/zwischen Teilklicks | C0E/CloseScreen | Instanz-/Sessionvergleich verweigert alten Plan | V |
| G11 | Player/World/Connection/Pipeline oder Packet-Epoch gewechselt | Start/Callback | C0E oder Slot-Restore | alte Sequenz ungültig; kein Restore in neue Session | B: Observer-Suite für Identität/Epoch; GUI-C0E V |
| G12 | Cursor hält Stack oder Benutzer beginnt Drag | Ordinary vor Start | sort/drop/steal | manuelle Konkurrenz gewinnt nach offenem Arbitrationvertrag; keine Fremdprediction überschreiben | V; S prüft nur AutoHeal-Cursor-Denial |
| G13 | manueller Click zwischen Scan und destruktivem Drop verändert Stack | Ordinary vor C0E | dropTrashSlot | nach neuem Scan kein Drop eines fremden/geänderten Stacks | B: InventoryManagerSafetyTest.dropRevalidatesStackAndCompetitorsAndUsesCurrentMappedSlot |
| G14 | Kandidatenstack unverändert, besserer Konkurrent verschwunden | Ordinary nach Open-Callback | Trashdrop | Klassifikation erneut berechnen; benötigtes Tool behalten | B: gleicher Bestandstest |
| G15 | Hotbarslot 8 statt Hauptinventarslot | Ordinary Drop | C0E Containerslot | korrekte Abbildung auf 44; kein falscher Inventarslot | B: gleicher Bestandstest, Controllerstub |
| G16 | gleichwertige Tools in Slot 0/8, unterschiedliche Haltbarkeit | Scan vor Aktion | Toolauswahl/Trash | mehr Resthaltbarkeit, dann niedrigster Slot; nicht Extra-bedingt neu zerstören | B: InventoryManagerSafetyTest.equalEfficiencyKeepsMoreRemainingDurabilityThenLowestSlot; Extraanteil V |
| G17 | alte Rüstung vorhanden und Ersatz eingeplant | Ordinary Start und Teilfortsetzung | Drop→Equip | erst nach Sequenzentscheidung migrieren; Gate darf erforderlichen zweiten Klick nicht still abschneiden | V; Produkt-/Transaktions-Gate offen |
| G18 | mehrere unabhängige Trash-/Sortkandidaten, Delay minimal | ein Listener + später Extra | mehrere C0E | gewählte Sequenzgrenze/Startanzahl explizit testen; Extra keine zusätzlichen Starts | V; keine Einklickregel als beschlossen |
| G19 | Chest gerade geöffnet bzw. Modul darin enabled | erster Ordinary vor OpenDelay | Stealer click | vor Delay keine Aktion; Timer gehört derselben Öffnung | V |
| G20 | Chest mit gemischten Slots, Randomness an/aus, kleine ActionDelay | Ordinary Schleife | Stealer shiftclick | aktuelle Slotliste/Stacks/Delay prüfen; reale Zahl der Sequenzen/C0E gemäß offener Semantik | V |
| G21 | nach letztem Chestclick GUI/Window manuell gewechselt | Ordinary AutoClose | closeScreen | neue GUI bleibt offen; alter Container darf nicht schließen | V |
| G22 | leere 27-/54-Slot-Chest und abweichende Containergröße | Ordinary AutoClose | empty/Slotprüfung | korrekte LowerChest-Grenze, keine Spieleritems als Chest werten; fremde Größe explizit behandeln | V |
| G23 | physischer GUIclick, kein Step, Scene SUSPENDED | manueller Controller | windowClick | ohne Modul-Admission weiter im Vanilla-Pfad | B: AutomatedActionModuleTest.physicalClickAndManualWindowClickWithoutStepStillReachNativeController; echte GUIinteraktion V |
| G24 | optionaler AutoHeal-Transfer, Quelle 9–35, Zielhotbar leer/voll | TickEvent-Start | mode-2 Swap | ausgewählten Slot nie als Ziel nehmen, keine Drops; ACK vor Heilung | B: AutoHealInventoryTransferTest.defaultOffHotbarFirstAndAckGatesEmptyOrFullHotbarTransferWithoutDrops; neue Tick-Admission fehlt |
| G25 | Transfer Pending, AutoHeal disabled, kein CurrentStep | späteres IN POST | passender ACK | Observer darf abschließen und unsubscribe; Neustart unabhängig neu prüfen | B: pendingObserverSettlesWhileDisabledAndReleasesOnContextCleanup; S nach Outer-Ende |
| G26 | Transfer Pending ohne Step | Reject / falscher oder cancelter ACK | Abschlussversuch | kein Heal; Reject wartet auf angewandten Vollresync, fremder ACK ignoriert | B: bestehende Transfer-Suite; S prüft Reject nach Tickende |
| G27 | Transferdeadline überschritten, später positiver ACK | Tick-Cleanup/IN POST | Timeout/ACK | Quarantäne statt Erfolg; keine blinde Retryfreigabe | B: timeoutAndContextChangesCannotReleaseUnacknowledgedPrediction; S ohne aktiven Tick |
| G28 | S30 kommt vor passendem ACK | IN POST→ACK→Resync | Transaktionsabschluss | frühes S30 ist kein ACK; erst terminaler autoritativer Resync löst Quarantäne | B: fullSyncBeforeMatchingAckDoesNotAuthorizeUseAndWorldUnloadDuringPreDoesNotThrow |
| G29 | C0E PRE wird ersetzt/cancelt oder Slot manuell verändert | Prediction/Transportcallback | guarded click | kein falscher Transport; nur eigene exakte Prediction rückrollen | B: GuardedInventoryClickTest und Transfer latePreCancelReplacementAndMutationRollbackOnlyOwnPrediction |
| G30 | World unload/Disconnect bei Pending, auch disabled | World/Tick-Cleanup | Observerfreigabe/Restore | Pending sauber lösen, keine fremden Slots restaurieren | B: Transfer-Suite World-/Closefälle; nativer Sessionwechsel V |
| G31 | EventUpdate später von weiterem Listener cancelt | PRE-Listenerreihenfolge | schon gestartete Sequenz | keine rückwirkende Erfolgsannahme; fachlich definierte Fortsetzung/Abbruch prüfen | V |
| G32 | passend aussehender Container ohne GuiChest, oder null Player | EventUpdate | Stealerstart | vorgeschlagene GUI-/Playerbedingung verweigert sicher; heutige Lücke statisch dokumentiert | V; kein Produktionsfix |
| G33 | neue GUI bei laufender Armor-/Sortsequenz, danach Rückkehr zur selben ID | mehrere Outer-Ticks | Fortsetzung/Retry | kein Wiederbeleben alter Berechtigung; frischer Plan nach autoritativer Klärung | V |

## Benannte offene Entscheidungen vor einer MOD-Migration

| Gate | Noch zu entscheiden / nachzuweisen |
| --- | --- |
| MOD-SEQUENCE | genaue Grenze logischer Transaktionen für Armor, Sort, Trash und Chest; Anzahl Starts pro Modul/Domain; erlaubte Teilfortsetzung über Tick-/ACK-Grenzen |
| MOD-GUI-CONTEXT | zwingende GUIinstanz zusätzlich Container; Window-ID-Wiederverwendung, LowerChestgröße, Player-/Session-/Controlleridentität |
| MOD-SUSPEND-REASONS | separate GUI-, Pause- und Fokusbedingungen; Vorschlag verweigert Pause/Fokus-Neustarts, Ausnahme nicht freigegeben |
| MOD-MANUAL-PRIORITY | manuelle Drag-/Cursor-/Shiftclick-/Hotbargesten und Konkurrenz AutoHeal/InventoryManager/Stealer; sichere Fortsetzung vs. Quarantäne |
| MOD-ACK-RETRY | autoritativer Abschluss, Reject-/Timeout-/Resyncregel, ACK-Zuordnung, Retry ohne Doppelaktion oder verlorene Prediction |
| MOD-DELAY-CLOSE | Open-/Action-/CloseDelay bei GUIwechsel, Denial und Extra; AutoClose als eigene Start-/Sequenzwirkung |
| CORE-OUTER-LIFETIME | verlässliche lesende API für aktive Tickdauer; aktuelle öffentliche API reicht nach Tickende nicht |
| MOD-PHASE-CANCEL | zulässige Eventphase und Verhalten bei späterer Cancellation/reentrantem Open-/Packetcallback |

## Deferred-/Owner-Grenzen

| Consumer | Grenze |
| --- | --- |
| AutoTool | Mininggestus/Slotowner; Restore nur eigenen unveränderten Slot, manueller Wechsel gewinnt; Tick-/World-Cleanup unabhängig von neuen Starts |
| SlotHandler | gemeinsame Hotbar-/Silentslotansicht und Cooldown; kein zweiter Slotmanager und kein pauschaler Reset |
| FastPlace | verändert Rightclickdelay bei Enable/EventUpdate; Input-/Delayvertrag mit manuellem Pfad, keine neue Endaktion-Admission |
| MoreKB | AttackEvent kann manuell sein; Update erzeugt Sprint-/C0B-Wirkungen, eigener MOV/NET-Vertrag |
| NoSlow | aktiver Itemuse und Motion PRE/POST mit C07/C08/C09; bestehende manuelle Slowdown-/Slotabhängigkeit gesondert behandeln |
| WTap2 | Attack/Tick/Motion, physische Tasten und Sprintresume; eigener MOV-Owner-/Inputvertrag, keine Inventory-Zulassung |

Keine dieser Grenzen ist hier implementiert oder als abgenommen markiert.
Es entstehen keine Settings, Configmigration, allgemeine GUI-Automation oder
Permission-Caches. CORE-I01d endet nach dem beschriebenen Dokumentationspaket.
