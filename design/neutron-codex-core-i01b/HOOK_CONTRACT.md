# CORE-I01b — Read-only Runtime-Beobachtung

Stand: 28.10.2026. Geprüfte Basis: `eae42f43757c344784efdb53492c06eaf34f39ba`,
lokaler Branch `codex/neutron-core-i01a`. Umsetzung ausschließlich in
`F:\neutron-core-i01b`, Branch `codex/neutron-core-i01b`.

## Code-Gate und Quellen

Das Gate ist bestanden. Die vier tatsächlichen Java-Klassen und drei Tests unter
`me.youngjibbit.core.contract` wurden vor Produktionsänderungen gelesen:
schwache Referenzidentität, immutable Werte, Generation/Epoch-Vergleich,
ORDINARY/Index 0, EARNED_EXTRA/Index >0, getrenntes Withheld und reine Eligibility
entsprechen dem I01a-Vertrag. Die I01a-Abschlussdokumente ersetzen diese Prüfung nicht.
Die acht ausgewählten bestehenden Testklassen liefen vor der Implementierung neu:
54 Tests, 0 Failures, 0 Errors, 0 Skips, JDK 8/Maven offline.

Gelesen wurden zuerst `00_START_HIER.md`, anschließend das Architekturreview
RV-01 bis RV-12, Produktspezifikation v1.0, die auf Phase/Step/Scene bezogenen
R01-Abschnitte sowie der lokale Budget-, Player-, Minecraft-, Packet- und Runtime-Code.
Der aktuelle I01b-Auftrag begrenzt die Umsetzung; ältere R01-API-Skizzen sind Vorschläge.
Insbesondere entstehen weder `StepContext`, globale Domain-Sperre, Timer-Arbiter noch
Input-Resolver. RV-01/04/05/07/08/09 und die fachlichen Teile von RV-10/11 bleiben
außerhalb dieser Implementierung. RV-02/03/06 werden durch die Beobachtungsgrenzen
beachtet; RV-12 durch getrennte echte Testzahlen in RESULTS.md.

## Tatsächliche Hook-Reihenfolge

Zeilenangaben beziehen sich auf den finalen I01b-Quellstand; Pfade unter `src/main/java`.

1. `net/minecraft/client/Minecraft.java:1055`: `runGameLoop` bleibt unverändert.
   `Timer.updateTimer` läuft vor der bestehenden `elapsedTicks`-Schleife.
   Kein Beobachtungsaufruf wurde an Renderframes oder Timer-Sampling angehängt.
2. `Minecraft.java:1739–1746`: Ein tatsächlicher `runTick`-Eintritt ruft einmal
   `beginOuterTick()` auf. Danach folgen unverändert zuerst
   `AttackScheduler.beginTick`, dann `ScaffoldPlacementService.beginTick`.
   ID -1 bedeutet noch keinen beobachteten Tick; der erste echte Tick erhält ID 0.
   `Minecraft.java:2281–2283` beendet den Kontext im `finally`, auch bei Exception.
3. `net/minecraft/client/entity/EntityPlayerSP.java:167–187` bleibt unverändert:
   geladener Block → `Tickbase.runPlayerStep(this, this::runLocalPlayerStep)`.
4. `me/youngjibbit/module/impl/exploit/Tickbase.java:84–93`: Der neue äußere Wrapper
   übergibt die bestehende Entscheidung an den Observer. Der nach
   `runPlayerStepDecision` verschobene Entscheidungsrumpf ist textgleich zur Basis
   (Zeilenenden normalisiert); Konfiguration, Guards, Diagnose und Budget bleiben gleich.
5. `me/youngjibbit/core/observation/RuntimeObservation.java:102–139` umschließt
   ausschließlich tatsächlich aufgerufene `simulation.getAsBoolean()`-Callbacks.
   Der erste betretene Body erhält ORDINARY/0, der zweite EARNED_EXTRA/1.
   `TickbaseBudget.java:41–65` bleibt unverändert und erlaubt höchstens ein Extra.
6. Im Body bleibt die Reihenfolge:
   EventUpdate (`EntityPlayerSP.java:176–177`) → Cancellation-Return oder
   `super.onUpdate`/Physik (187) → EventMotion.PRE (222–223) → Sprint-/Sneak-C0B
   (235–249) → Rotation-Resolve (264) → C03 → `finishMotion` (304) → POST (330–331).
   Die bestehende Riding-Verzweigung bleibt ebenfalls unangetastet.
7. Der Observer entfernt `currentStep` im inneren `finally`, bevor die Budgetlogik
   fortsetzt. Erst nach Rückkehr aus der Entscheidung sind die endgültigen Werte
   über `getLastSteps()` lesbar.

`currentStep` beschreibt einen betretenen, noch laufenden Body. Sein anfängliches
`interrupted=false` ist kein Abschlussnachweis. Ein `false`-Return, eine Body-Exception
oder eine explizite Kontextinvalidierung während des Bodys erzeugt einen endgültigen
Wert mit `interrupted=true`. Der ursprüngliche Boolean/Throwable wird weitergereicht.
I01c muss die konkrete Aktionsphase zusätzlich zur Eligibility prüfen.

**Withheld:** Nur normaler Return aus der bestehenden Entscheidung mit null
Callback-Eintritten erzeugt genau eine `WithheldObservation`. Es entstehen kein
ExecutedStep und kein Begin/End-Ereignis. Eine Exception vor der Entscheidung erzeugt
kein Withheld. Außerhalb eines aktiven Outer-Ticks oder für einen fremden Player
läuft die bestehende Entscheidung unverändert und ohne erfundene Tick-ID weiter.

Scaffold `tryPlace(true)` bleibt in PRE vor `applyRotations`
(`module/impl/world/Scaffold.java:175–177`) und damit vor dem aktuellen C03.
I01b bestätigt dafür keine Rotation. POST-Placement bleibt bei Zeile 183.

## Scene-Identität und Lifecycle

`MinecraftSceneSource.capture(Minecraft)` liest pro Erfassung lokal starke Referenzen:
aktueller Player, World, dessen `sendQueue`, NetworkManager und dessen PacketPipeline.
Connection-Identität ist der NetworkManager, Pipeline-Identität ein zusätzlicher
Vergleichsbestandteil. Es gibt keine neue PlaySession-Verwaltung oder Eventsubscription.

`RuntimeObservation.capture` vergleicht Connection, Player, World und Pipeline per `==`
mit vier schwachen Referenzen. Ein beobachteter Wechsel einer dieser Identitäten,
der Pipeline-Epoch oder der Live-Verfügbarkeit erhöht `connectionGeneration`.
Der erste erfasste Kontext beginnt bei Generation 0. GUI/Pause/Fokus verändern
die Generation nicht. Pipeline-Identität ist über die Generation in SceneContext
abgebildet, ohne dessen I01a-API zu erweitern.

`packetContextEpoch` kommt unverändert aus `PacketPipeline.getContextVersion()`
(`core/network/PacketPipeline.java:162–163`). Die Pipeline erhöht sie bereits bei
Join/Respawn, Correction, Death/Disconnect-Paketen und `worldChanged`/Discard.
I01b liest ausschließlich; es ruft weder Pump, Replay, Discard noch Packet-Send auf.

Ein alter SceneContext bleibt als historischer Wert unverändert. `captureScene()`
erfasst neu; `isCurrent(SceneContext)` vergleicht mit einer neuen Erfassung und hält
deren starke lokale Identitäten während des Vergleichs über einen lokalen Monitor
erreichbar. Snapshot und Observer speichern keine starken Scene-Objekte. `SceneSample`
ist ausschließlich ein kurzlebiger interner Transportwert für diese lokalen Referenzen.
Die Source des Runtime-Observers liest den bestehenden Minecraft-Singleton und hält
selbst keine zusätzliche Client-/World-/Player-Instanz fest.

GUI, `isGamePaused()` oder fehlender Ingame-Fokus ergeben SUSPENDED bei weiterhin
gültiger logischer Scene. Tod, fehlende/inkonsistente Identitäten, fehlende/inaktive
Pipeline, unbekannte Epoch oder Shutdown ergeben INVALIDATED. Resume verlangt eine
neue Erfassung; es wird keine frühere Eligibility zwischengespeichert.

`Minecraft.loadWorld(...):2367–2371` invalidiert die Beobachtung vor der bisherigen
Packet-/World-Behandlung. Die explizite Generationserhöhung deckt auch eine
A→B→A-Rückkehr zwischen Erfassungen ab. Beobachtete Player-/Connection-/Pipeline-ABA
und Epochwechsel werden ebenfalls erkannt. Beliebige direkte A→B→A-Feldschreibungen
ohne Erfassung, Lifecycle-Hook oder Pipeline-Epoch sind durch Polling grundsätzlich
nicht beweisbar; I01b instrumentiert dafür keine fremden Netzwerk-/Player-Dateien.

`Minecraft.shutdown():1434–1435` und `shutdownMinecraftApplet():1017–1018` rufen
`reset()` auf. Reset/Worldboundary löschen `currentStep` sofort; ein äußeres `finally`
stellt ihn nach einer solchen Grenze nicht wieder her. Reset beendet auch die
Outer-Tick-Beobachtung und liefert bis zum nächsten echten Tick einen invaliden Kontext.
Body- und Decision-Exceptions räumen ihren Scope auf; `runTick` hat zusätzlich sein
eigenes `finally`. Die monotone Tick-ID wird bei Reset nicht wiederverwendet.

## Neue API und Consumer

Eine Instanz wird in `ClientRuntime.java:20–21` angelegt und über
`getObservation():56` sowie `RuntimeServices.observation():32` bereitgestellt.
Vor Bootstrap gibt der Accessor null zurück; es gibt keinen Fallback-Observer.

```java
RuntimeObservation(Supplier<SceneSample> source)
void beginOuterTick()
void endOuterTick()
void invalidateScene()
void reset()
void observePlayerStep(Object player, BooleanSupplier simulation, Consumer<BooleanSupplier> decision)
SceneContext captureScene()
boolean isCurrent(SceneContext historical)
long getOuterTickId()
ExecutedStep getCurrentStep()
List<ExecutedStep> getLastSteps()
WithheldObservation getLastWithheld()
```

Die letzten Beobachtungen sind Diagnosewerte der letzten beobachteten Entscheidung,
keine aktuelle Aktionsberechtigung; die Liste ist unveränderlich und bei der realen
Budgetpolitik auf höchstens zwei Bodies begrenzt. Es gibt keine Tick-Historie oder
Tick-Logs. Nur Minecraft und der zentrale Tickbase-Wrapper schreiben Observerzustand.
Produktionsmodule konsumieren weder die neue Eligibility noch die Diagnosewerte.
Tests lesen sie; spätere read-only Consumer müssen Scene und Outer-Tick frisch prüfen.

## Verhaltensmatrix und Evidenzgrenzen

| Bestehendes Verhalten | Neue Beobachtung | Neuer Test / Beleg | Grenze |
| --- | --- | --- | --- |
| Ohne aktives Tickbase ein Body | ORDINARY/0 nur im Callback | `actualCentralHookObservesOneBodyWithoutAnActiveTickbase` | kein Live-Spiel |
| Withhold: null Bodies, ein Credit | ein separates Withheld | `actualTickbaseWithholdsThenReleasesOneEarnedBodyWithTheSameTickId` | Creditpolitik unverändert |
| Release: Ordinary plus höchstens ein Extra | Indizes 0/1, gleiche Outer-ID | gleicher Hooktest und echter Budgettest | kein zusätzlicher runTick |
| EventUpdate-Cancel: false | betreten und interrupted | `actualPlayerOnUpdateCancellationIsEnteredInterruptedAndNotWithheld` | keine Withheld-Umdeutung |
| Body-/Decision-Exception | Cleanup, ursprünglicher Throwable | zwei reale Exception-Hooktests und drei reine Exceptiontests | keine Fehlerunterdrückung |
| Welt-/Packetwechsel/Close | neue Generation/Epoch, frischer Vergleich | realer Unload-/Packettest, Identity-/ABA-Tests | direkte unsichtbare Feld-ABA siehe oben |
| GUI/Pause/Fokus | SUSPENDED, gleiche Scene | `guiPauseAndFocusAreFreshSuspensionObservationsWithoutSceneChanges` | keine Modulmigration |
| Frame mit 0/mehreren Ticks | nur echte Tick-Eintritte zählen | `actualRunTickEntryCountsEachCallAndFinallyCleansAnEarlyException` | echte runTick-Eintritte, synthetische Frames; kein nativer GameLoop |
| Update/PRE/C0B/C03/POST | Reihenfolge und Anzahl bleiben gleich | `realWalkingEventsAndPacketsHaveTheSameOrderWithAndWithoutObservation` | echter Walking-/Packet-Hook, synthetischer Update-Boundary; keine volle Physik |
| Shutdown innerhalb Body | sofort kein currentStep | `shutdownResetsInsideABodyAndManualControllerBudgetRemainsUsableOutsideSteps` | Applet-Exit nur statisch geprüft |
| Manueller Controller außerhalb Step | kein Interceptor | Scheduler-API-Test, unveränderte Controllerdateien | kein neuer vollständiger clickMouse-/Inventory-Test |

PRE-/POST-/C0B-/C03-, MoveFix-, Rotation-, Attack-, Placement-, Input-, Timer-,
Inventory-, Config- und Packet-Implementierungen bleiben erhalten. Insbesondere ist
das Produktziel „keine doppelten automatischen Aktionen in Extras“ hier noch nicht
durchgesetzt. I01c benötigt gesonderte Tests und einen ausdrücklich beauftragten
Consumer-Scope; dieser Auftrag endet bei der read-only Beobachtung.
