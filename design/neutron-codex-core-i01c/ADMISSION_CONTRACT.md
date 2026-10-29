# CORE-I01c — Admission-Vertrag und Nachweise

## Geprüfte API / bestandenes Architektur-Gate

Lokale Basis `6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666` enthält die originalen
I01a-/I01b-Ergebnisberichte und die tatsächlichen Vertrags-/Observerklassen.
Vor Produktion wurden `SceneContext`, `ExecutedStep`, `WithheldObservation`,
`AutomationEligibility`, `RuntimeObservation`, `MinecraftSceneSource`,
`RuntimeServices`, `ClientRuntime`, die Vertrags-/Observertests, die Controller-/
Walking-/Tickbase-Hooks sowie `CONTRACT_DECISIONS.md` und beide bereitgestellten
I01b-Berichte gelesen. Die lokalen Originalberichte wurden zusätzlich gelesen
und nach Normalisierung der Zeilenenden/Endleerzeilen als textgleich bestätigt.
Die kopierten Zusatzberichte ersetzen die Codeprüfung nicht. Historische
R01-Skizzen von `StepContext`, `ActionDomain.claim` und PRE nach C03 werden nicht übernommen.

Die tatsächlich vorhandene reine API lautet:

```java
AutomationEligibility.mayAttempt(ModuleAction action, ExecutedStep step,
                                 SceneContext currentScene, long currentOuterTickId)
```

Sie verlangt ORDINARY, nicht unterbrochen, dieselbe aktuelle Outer-ID,
gültige und nicht suspendierte Step-/aktuelle Scene sowie `sameSceneAs`.
Scene vergleicht schwache Connection-/Player-/World-Identitäten per `==`,
Generation und Packet-Epoch; der Observer bildet Pipelinewechsel über Generation ab.
Die Runtime stellt `RuntimeServices.observation()` bereit, vor Bootstrap null.
`getCurrentStep()` gilt nur im betretenen Body; `getLastSteps()` und
`getLastWithheld()` werden von der Produktionsbrücke überhaupt nicht gelesen.

Das Gate ist ohne zusätzliche Minecraft-/Controller-/Packet-Hooks erfüllbar.
Keine Änderungen an den I01a-Verträgen, Observerklassen, Runtime-Services,
Fachbudgets, Controller-/Minecraft-/Player-, Build- oder Packetdateien sind nötig.

## Kleine zustandslose Brücke

`me.youngjibbit.core.observation.AutomatedActionAdmission` besitzt keine Felder:

```java
public static boolean mayAttempt(ModuleAction action)
// package-private: gleicher Algorithmus mit explizitem Observer für isolierte Tests
static boolean mayAttempt(ModuleAction action, RuntimeObservation observation)
```

Jeder Aufruf holt den kanonischen Observer, liest den aktuellen Step, erfasst die
Scene neu und prüft die aktuelle Outer-ID mit der bestehenden Eligibility-API.
Der zuerst gelesene Step muss nach der Erfassung noch derselbe aktive Step sein;
eine während capture auftretende Lifecycle-Grenze darf ihn nicht wiederbeleben.
Fehlender Observer/Step, Extra, falsche ID, Scene-/Epochwechsel, ungültige Identität,
GUI/Pause/Fokusverlust und Shutdown verweigern den automatischen Versuch.
Es gibt keinen Fallback-Observer, keine Lease, Zähler, Clock, Eventsubscription,
Reservation, eigenen Budgetstore oder Aktion in der Brücke. Exceptions werden nicht gefangen.

Wiederholte Abfragen im selben gültigen Ordinary sind erlaubt und verbrauchen
nichts. Ein true ist nur eine notwendige Voraussetzung für einen Versuch:
AttackScheduler, Placementservice, Controller, Raycast, Slot-/Callback-/Packetguards
können danach weiterhin ablehnen. Kein Wert bedeutet „Server hat getroffen/platziert“.

## Vier konkrete Schnittstellen

| Modul / Phase | Gate und erhaltenes Verhalten | Nachweis |
| --- | --- | --- |
| Aura, Motion POST | Vor `tryAttack`, erneut vor POST-Autoblock und in `validHit` auch für bestehende späte Controller-/Packet-Callbacks. Kein Extra-Scheduler-Versuch und keine POST-Blockwiederholung. | `auraExtraDoesNotEvenEnterSchedulerAndKeepsAcceptedForeignOwner`, `auraOrdinaryCanSendAcceptedAttackAndExtraCannotRepeatAttempt`, `auraOrdinaryReachesSchedulerButRejectedMotionStillPreventsAttack` |
| Aura, Motion PRE | Targeting, Tracking und Rotationsanforderung bleiben; nur Motion-Autoblock-Update benötigt Admission. Das verhindert Extra-C07/C08-Release/Resume im gemeinsamen Motionpfad. | `auraExtraPreStillTargetsAndCalculatesRotationWithoutAutoblockEffects`; injizierter echter AutoblockController mit zählendem Transportcallback, echte Walking-/Movement-Rotation |
| AutoClicker2, EventUpdate | Vor CPS-Auflösung/Timer und allen direkt zugehörigen künstlichen Use-Key-Writes; gültiges Ordinary nutzt dieselben Formeln, Settings und clickMouse. | Ordinary/Extra-C02, Keyzustand mit/ohne Attacktaste, unverbrauchter Clicktimer; Pause/GUI/Fokus/Epoch/Identity/Shutdown/Outer-ID/Withheld/History |
| HitDelayFix, EventUpdate PRE | Direkt vor dem Listener-Klick nach vorhandenen Picking-/Keychecks. Normaler Minecraft-HitDelay-/Controllerpfad unverändert. | Separate Ordinary/Extra-C02 und gemeinsame Invalid-Kontextfälle mit AutoClicker2 |
| Scaffold, Motion PRE/POST | Ein Gate am Anfang `tryPlace`, vor Winkel-Diagnose und `ScaffoldPlacementService.place`; Denial liefert false, ohne Slotwahl, Servicebudget oder erfolgreiche Placementdiagnose. Plan-/Rotation-/Timer-/Towerverarbeitung und sämtliche release-Bedingungen bleiben. | `AutomatedPlacementAdmissionTest` (7 Fälle), native Extra-Walkingprüfung, bestehende 31 Placement-/2 Clientflowtests |

Aura `onAttack`/manueller AttackEvent und bestehende World-/Disable-/Tick-Cleanup-
Pfade bleiben unverändert. Eine Admission-Verweigerung ruft keinen Reset/Release
auf, setzt keinen fremden Owner zurück und erzeugt keine Animation. Bereits
vorhandene Lifecyclebedingungen dürfen weiterhin ihre eigenen Zustände bereinigen.
AutoClicker2 schreibt bei Denial keinen neuen Use-Key-Wert; eine allgemeine
Reparatur schon vorhandener künstlicher Keyzustände ist ausdrücklich nicht Bestandteil.

## PRE / POST und Abschlussgrenzen

```text
PRE:  Physik -> Motion PRE -> Plan -> Admission
      -> Placementservice prüft VORHERIGE akzeptierte Rotation
      -> Rotationsanforderung -> C0B -> Resolve -> C03 -> finishMotion
POST: gleicher PRE-Plan -> Admission
      -> Placementservice prüft AKTUELL akzeptierte Rotation -> Controller/Packetguard
```

Keine neue geometrische oder Ownerannahme wird hinzugefügt. Die Tests prüfen
vorherige akzeptierte Winkel gegen eine neue gegensätzliche Pending-Anforderung,
fehlende erste Acceptance, abgelehntes POST, Raycastablehnung sowie das intakte
Tick-/Motionbudget und Slot-Restore bei Exception. PRE wird nicht nach C03 verschoben.
Keep-Rotation-Lifecycle, `releasePlacement()`, MoveFix/Timer/Slot-Spoof bleiben fachlich offen.

Ein laufender Step ist **noch nicht abgeschlossen**. Sein vorläufiges
`interrupted=false` erlaubt keine Vorhersage späterer Cancellation/Exception.
Ein später abgebrochener Body kann vorher bereits eine gültige Aktion ausgelöst
haben; Admission macht das nicht rückwirkend ungeschehen. Die Live-Prüfung erfolgt
vor dem jeweiligen Modulversuch; die existierenden Fach-/Packetguards bleiben für
Callbacks und Transport maßgeblich. Scaffold erhält keinen zusätzlichen Callback
im unveränderten Service. Insbesondere ist dies keine atomare globale Garantie
gegen beliebige reentrante Sceneänderungen oder unsichtbare A→B→A-Feldwechsel.

## Test- und Migrationsgrenzen

Die neuen Modulefixtures verwenden echte Listener, Observer-Bodys, Walking-Hook,
native Controller und EmbeddedChannel; Worldstorage/Input sind synthetisch.
Ordinary/Extra-Paare werden bewusst über die echte Observer-API mit einer
synthetischen Zwei-Callback-Entscheidung betreten; es wird kein Extra-Spieltick
erfunden. Die vorhandenen I01b-Hooktests prüfen zusätzlich den tatsächlichen
Tickbase-/Withhold-/Release-/Cancellation-Callgraph mit dem echten Budget.
Die Placement-Komposition verwendet den unveränderten realen Service und dessen
bestehendes `ScaffoldPlacementTest.WorldAccess`-Fixture; der native Walkingtest
prüft darüber hinaus C08-/C09-Freiheit im Extra und erhaltenen Rotationsbesitz.
Keine API wird in Produktion ersetzt; Testobjekte/Globals werden wie in den
vorhandenen Fixtures isoliert eingerichtet und im Cleanup wiederhergestellt.

Notwendige Änderungen an drei bestehenden Testdateien:
`ScaffoldClientFlowTest.everyModeAndTimingPlacesThroughTheNativeFlowBeforePeriodicPositionRefresh`,
`LegacyScaffoldTest.BindingChecks.roundedAndGodbridgeModesPlaceThroughNativePreAndPostWithoutRemappingInput`
und `RotationModuleBindingChecks.scaffoldPresetsKeepPointsOnTheFaceAndPlaceThroughTheNativeController`
riefen `onUpdateWalkingPlayer` direkt ohne Observer-Body auf. Nach Einführung
des beauftragten No-Step-Denials können diese Aufrufe keine automatische Platzierung
mehr erwarten. Nur diese Placement-Fixtures benutzen nun den gemeinsamen Helper
`ScaffoldClientFlowTest.ordinaryWalking`: Er betritt einen echten aktuellen
Ordinary-Scope und beendet den Outer-Tick im finally;
**alle bisherigen Assertions bleiben unverändert**. Der erste Gesamtlauf hat die
beiden zusätzlichen veralteten Fixtures als Failures der jeweiligen Prozesswrapper
nachgewiesen. Reine Rotations-/Diagnosefixtures ohne Aktion bleiben unverändert.
Keine Änderung produktiver APIs oder gefährlicher Test-Fallback ist erforderlich.

Die manuellen Belege führen `Minecraft.clickMouse` und
`PlayerControllerMP.windowClick` tatsächlich ohne CurrentStep durch; für den
Inventarklick ist nur der Containerinhalt synthetisch. Es sind keine nativen
Maus-/GUI-Interaktionen und keine Serverbestätigungen.
Die neun Brückentests prüfen außerdem Wiederholungen ohne Budgetverbrauch,
Sceneidentitäten/Generation/Epoch, Suspend/Resume, falsche Outer-ID, interrupted,
Withheld/History, early false, Exception, Shutdown und Invalidation während capture.

AutoHeal/TickEvent, InventoryManager/GuiInventory, Stealer/GuiChest, AutoTool,
SlotHandler, FastPlace, MoreKB, NoSlow und sonstige Input-/Packetmodifikatoren
sind **nicht migriert**. Gründe, manuelle Grenzen und gesonderte Teststrategie
stehen in `ACTION_CONSUMER_MATRIX.md`; die gesamte Pipeline ist nicht vollständig geschützt.
Keine Spiel-/GL-/Account-/öffentliche Server-/Shader-/Release-Abnahme.
