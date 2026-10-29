# CORE-I01d — TickEvent-Admission und Code-Gate

Stand: 28.10.2026. Geprüfte lokale Basis:
`382e995496af336aab07ca1cf6f8a41e4e54160c`, Branch `codex/neutron-core-i01c`.
Arbeitsverzeichnis: `F:\neutron-core-i01d`, Branch `codex/neutron-core-i01d`.

## Ergebnis und Freigabegrenze

**STOPP am ausdrücklich beauftragten Code-Gate; kein Produktionsfix.**
Der Bestand erfüllt die verlangte Initiierungsregel nicht vollständig.
Insbesondere fehlen AutoHeal die Prüfung eines aktiven Observer-Outer-Ticks,
der kanonischen Scene und der Pause. Dies ist kein begründeter „bereits sicher“-No-Op.
Die hier dokumentierten Lücken bleiben offen.

Die freigegebene zustandslose Consumer-Schicht kann einen aktiven Outer-Tick
nicht von einem gerade beendeten unterscheiden. `RuntimeObservation.outerTickActive`
ist privat; `getOuterTickId()` liefert nach `endOuterTick()` weiterhin dieselbe
nichtnegative ID. `captureScene()` und `isCurrent()` prüfen Scene-Liveness,
Identität und Versionen, aber keine aktive Tickdauer. Ein gültiger Scene-Snapshot
kann außerhalb des Tickkontexts weiterhin ACTIVE sein. `getCurrentStep()` ist
sowohl beim gültigen TickEvent als auch nach Tickende null. Diagnosehistorie
ist keine Berechtigung; im Gegenbeispiel ist sie auf beiden Seiten leer.

Eine vollständige Korrektur benötigt einen vom zuständigen Observer verlässlich
bereitgestellten Nachweis der aktiven Tickdauer. Die konkrete API ist offen;
ein bloßer zusätzlicher ID-Vergleich löst das Problem nicht. Änderungen an
`RuntimeObservation` oder anderen Hot Files sind ausdrücklich ausgeschlossen.
Deshalb entstehen weder Teilfix noch Hilfsklasse, Reflexionszugriff im Produkt,
Stacktrace-Heuristik, Pseudo-Player-Step oder ein zweiter Tickzustandsbesitzer.
Auch ein absichtliches Always-Deny wäre falsch: es würde den gültigen
TickEvent-Heal ohne CurrentStep abschalten.

## Tatsächlich gelesene lokale Quellen

Alle folgenden Zeilen beziehen sich auf die unveränderte I01c-Basis im neuen Worktree.

| Quelle | Nachweis |
| --- | --- |
| `F:\neutron-core-i01d\src\main\java\net\minecraft\client\Minecraft.java:1739` | `runTick` beginnt Observer, AttackScheduler und Placementbudget; TickEvent bei 1758/1760, Observer-Ende im finally bei 2282 |
| `F:\neutron-core-i01d\src\main\java\net\minecraft\client\entity\EntityPlayerSP.java:167` | `onUpdate` → Tickbase-Wrapper; im Body EventUpdate vor Physik; Motion PRE/C03/POST im Walkingpfad |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\module\impl\exploit\Tickbase.java:84` | kanonischer Observer umschließt die bestehende Entscheidung und nur wirklich aufgerufene Bodies |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\core\observation\RuntimeObservation.java:34` | Tickbeginn, Tickende 45, Reset 59, Scene-Sampling 66/71/80, Body-Scope 101 und öffentliche Lesemethoden 141 |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\core\observation\MinecraftSceneSource.java:16` | Player/World-Konsistenz, NetworkManager/Pipeline, Liveness; GUI/Pause/Fokus → SUSPENDED |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\core\contract\SceneContext.java` | `isValid`, `isSuspended`, `sameSceneAs`; schwache Identitäten, Generation und Packet-Epoch |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\core\contract\AutomationEligibility.java` | `mayAttempt(ModuleAction, ExecutedStep, SceneContext, long)` verlangt ORDINARY; kein ITEMUSE-Enumwert |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\core\bootstrap\RuntimeServices.java:32` | `observation()` liefert ausschließlich den kanonischen Observer bzw. null |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\module\impl\player\AutoHeal.java:42` | Bind/Cleanup, TickEvent 49, Startpunkte 57/58, Itemuse 81, Packetguard 99, finally 103, Fachvalidierung 112 |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\module\impl\player\AutoHealInventoryTransfer.java:43` | eigener Pending-Cleanup; bind 56, blocked 67, Context/Prediction 74–93, begin 94, ACK/Resync 133 |
| `F:\neutron-core-i01d\src\main\java\net\minecraft\client\multiplayer\PlayerControllerMP.java:594` | manuelles `windowClick`; additive guarded Variante bei 607, unverändert |
| `F:\neutron-core-i01d\src\main\java\me\youngjibbit\core\network\PacketPipeline.java:69` | OUT PRE/Selektion/Guard/Transport/POST; `appliedScene` und Epoch sind unterschiedliche Nachweise |

Zusätzlich geprüft: vollständiges I01b→I01c-Diff, die drei lokalen I01c-Berichte,
I01a `CONTRACT_DECISIONS.md`, I01b `HOOK_CONTRACT.md`, Produktspezifikation v1.0,
Review und Arbeitsregeln aus dem extern entpackten Quellenpaket.
Die drei I01c-Fixture-Anpassungen ergänzen nur echte Ordinary-Scopes;
Assertions, Schleifenlimits und Erwartungen wurden im Diff nicht abgeschwächt.
Historische R01-`ActionDomain.claim`-/StepContext-Skizzen sind keine lokale API.

## Callgraph und bestehende Fachguards

```text
Minecraft.runTick
  observer.beginOuterTick()                 // nicht pro Renderframe
  AttackScheduler.beginTick()
  ScaffoldPlacementService.beginTick()
  TickEvent einmal posten                  // auch bei Pause; kein CurrentStep
    AutoHeal.bindContext()
      transfer.bind()                      // vor Eintrittsentscheidung erhalten
      ggf. eigener Contextreset
    transfer.blocked() / info               // laufende Transaktion weiter beachten
    bisherige Heal-Fachguards
    Hotbar-Heal vorhanden? useHealingItem(potion)
      bindContext / pending / using / cooldown / slot prüfen
      Stackidentität + Kopie, generation, Player/World/Connection/Controller merken
      using=true; Slot setzen; C09-Sync prüfen
      valid(...) → guarded C08 → bestehender finaler Callback
      nur bei lokal akzeptiertem selben C08: 1s-Cadence setzen
      finally: nur unveränderten eigenen Slot in identischem Kontext restaurieren
      using=false
    sonst bei aktiviertem Inventory Transfers: transfer.begin(predicate)
      mode-2 C0E; lokale Prediction; Pending/ACK-/Resync-Lifecycle
  ggf. EntityPlayerSP.onUpdate → Tickbase.runPlayerStep
    WITHHELD: kein Body
    ORDINARY, ggf. EARNED_EXTRA: EventUpdate → Physik → Motion PRE → C0B → C03 → POST
  finally observer.endOuterTick()
```

AutoHeal verlangt derzeit Player, World und Controller, Fokus, lebenden
nichtspektierenden Player, kein laufendes Itemuse, keinen Cursorstack, keine GUI,
Health unter/gleich Schwelle und abgelaufene Cadence. Soup muss Mushroom Stew sein;
Potion muss ein nichtleerer Splash-Heal-Stack mit Amplifier mindestens 1 sein.
Die Suche beschränkt sich für Itemuse auf Hotbar 0–8. Optionaler Transfer sucht
Quelle 9–35 und Ziel 0–8, bevorzugt leer und niemals den ausgewählten Slot.

`using` verhindert reentranten Itemuse. `valid` verlangt unter anderem denselben
ausgewählten Slot, dieselbe Stackreferenz und gleichen Stackinhalt, Contextgeneration,
Player/World/Controller und Connection; PRE-Cancellation/Replacement können weiter
ablehnen. `finally` überschreibt keine inzwischen fremde Slotwahl und restauriert
nicht über einen Player-/World-/Connection-/Controllerwechsel hinweg.
Diese Guards prüfen keine aktive Outer-Tick-Dauer und keine Observer-Epoch.
Sie sind auch nach einer künftigen Admission weiterhin erforderlich.

## Phasenmatrix vor Produktcode

Die erste Fassung liegt extern als `pre-code-phase-matrix.json`; Produktionscode
wurde zu keinem Zeitpunkt geändert. „Soll“ ist die gewünschte Policy,
„Ist“ bezeichnet lokale Code-/Test-Evidenz, keine bereits implementierte Admission.

| Fall | Soll am Startpunkt / Abschluss | Ist und Nachweis |
| --- | --- | --- |
| ACTIVE TickEvent, kein CurrentStep | Fachlich gültiger Heal/Transfer darf starten | reale AutoHeal-TickEvent-Sonde; Observer/Listener/EmbeddedChannel, synthetische Welt |
| TickEvent, später WITHHELD | früherer gültiger Versuch bleibt gültig | Sonde mit separatem Withheld ohne Body; bestehende echte Tickbase-Hooktests zusätzlich |
| Ordinary + Earned Extra im selben Outer-Tick | Player-Body wiederholt kein TickEvent und keinen Heal | Sonde postet zwei EventUpdates mit bereiter Cadence; keine zusätzlichen C08; echter Budget-/Hooktest prüft Steparten |
| Vor erstem Tick / fehlender Observer | kein synthetischer Start, kein Cooldownverbrauch | unerfüllte Anforderungen der externen Sonde; Code ignoriert Observer |
| Nach `endOuterTick`, ID ≥ 0, Scene ACTIVE | kein neuer Start | zentrale unerfüllte Anforderung; öffentliche lesende APIzustände bleiben identisch |
| GUI / Fokusverlust | keine neue Heilaktion; Cleanup bleibt erreichbar | bestehende Guards und Sonde prüfen Packetfreiheit, Cadence/Slot und fremden Rotationowner |
| Pause ohne GUI bei vorhandenen Fokus | kein neuer Start | Observer SUSPENDED, AutoHeal ohne Pauseguard; separat reproduziert |
| Tot / disconnect / inkonsistente World / Shutdown | keine neue Aktion und kein sessionfremdes Restore | Dead-Guard vorhanden; falsches Player.worldObj und Observer-Shutdown gesondert als Lücken geprüft; Disconnect-/World-Cleanup im Bestand |
| Neue Scene / Packet-Epoch | frische Startvalidierung; alte Berechtigung ungültig | Observer erkennt Epoch-/Identitywechsel; AutoHeal verwendet diese Beobachtung derzeit nicht |
| Später Outbound-Callback eines gültigen Starts | bestehende Packet-/Stack-/Slot-/Sessionguards entscheiden | kein neuer Same-OuterTick-Guard am Callback; unten beschriebene Reichweite beachten |
| Pending Transfer ACK / Reject / Resync / Timeout | unabhängig von CurrentStep abwickeln | bestehende Transfer-Suite und Sonde nach Tickende; Timeout quarantiniert statt Heilung freizugeben |
| Manueller rightClickMouse / windowClick | unabhängig von Automationsgate erreichbar | native windowClick im bestehenden I01c-Test; rightClickMouse statisch gelesen, kein neuer nativer Maus-/GL-Test |
| Expliziter Slot-/Stackwechsel / Cursor / using-Reentranz | bisherige Fachprüfung erhalten | Sonde und unveränderte AutoHealSafetyTest-/GuardedInventoryClickTest-Suiten |
| Wiederholtes / fehlendes Scene-Sampling | keine Cacheberechtigung, kein Budgetverbrauch | wiederholtes `isCurrent` zustandslos in Bezug auf Aktionsbudgets; Epoch invalidiert alte Scene; fehlende Admission bleibt Lücke |

## Transaktions- und Callbackgrenze

Ein laufender Transfer kann auch nach Disable weiter beobachten, bis ACK oder
Resync seinen Pending-Zustand beendet. Reject, Timeout, manuelle Veränderung oder
unklare Prediction führen zur Quarantäne; ein Timeout ist kein erfolgreicher
Transfer und erlaubt keinen neuen Heal. Ein S30 vor dem passenden ACK ersetzt
dieses ACK nicht. Der Abschluss verlangt angewandte IN POST-Rückmeldungen aus
der richtigen Pipeline und passenden Window-/Actionnummern. World-/Disconnect-
Cleanup bleibt unabhängig vom Startrecht.

Ein künftig mögliches Eintrittsgate muss hinter `bindContext`/`transfer.blocked`
und vor den beiden neuen Startpunkten liegen. Es darf den eigenständigen
Transfer-Observer, seine Tick-Cleanup-Routine, ACK/Reject/Resync und sicheres Restore
nicht sperren. Die bestehenden Transfer-Fixtures posten TickEvent ohne aktiven
Outer-Tick; bei einer später tatsächlich freigegebenen Migration müssten allein
die Start-Fixtures echte Scopes erhalten. Das ist hier nicht geschehen.

Eine Admission ist ausschließlich eine frische Vorprüfung. Sie reserviert kein
domainübergreifendes Budget und verbraucht keine Cadence, keinen Slot oder
Attack-/Placementzähler. `sent == use` und OUT POST belegen lokale Annahme,
keine Serverheilung, keinen Inventar-ACK und keinen akzeptierten Block.

Der vorhandene Outboundpfad prüft beim Transport Scene-Anwendung, Pipelineaktivität
und den vorhandenen Actioncallback. Eine Initial-Admission darf nicht als
pauschaler Zwang ausgelegt werden, dass spätere Transporte noch dieselbe Tick-ID
sehen müssen. C08/C0E wirken im aktuellen Pipelinecode als Barrier, nicht als
verzögerte C03-Bewegung. Beliebige zeitlich verzögerte Callbackvarianten und
unsichtbare A→B→A-Feldwechsel erhalten durch diese Analyse keine neue Garantie.
Ein reiner Packet-Epochwechsel ist auch nicht identisch mit `appliedScene`-Wechsel;
AutoHeals eigener Callback enthält derzeit keinen Epochvergleich.

## Testnachweis und offene Abnahme

Die ausgeführten Läufe und frischen Surefire-Summen stehen in `RESULTS.md`.
Die unveränderten Basistests ersetzen die neue Initiierungsanforderung nicht.
Die externe JUnit-Sonde bleibt mit ihren unerfüllten Sicherheitsassertions erhalten;
sie wird weder abgeschwächt noch als grüne Produktregression eingecheckt.
Nach dem Code-Gate ist der Review-Diff ausschließlich Dokumentation.

Offen bleibt ein explizit freizugebender Observervertrag für aktive Tickdauer
und die benötigte frische Scene-/Epochbindung. Dieser Bericht startet keinen
Folgeauftrag. GUI-/Inventarzulassung ist separat in `GUI_INVENTORY_HANDOFF.md`
spezifiziert; Owner-/Slot-/Input-/Timer-/Movement-/NET-Umgestaltung bleibt außerhalb.
Keine Gameplay-, Account-, Server-, GL-, Shader- oder Release-Abnahme.
