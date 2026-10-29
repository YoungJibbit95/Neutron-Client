# CORE-I01e — Active-Outer-Tick-Vertrag

Stand: 28.10.2026. Basis: `ae23848a7a33a28e8b860f43ed5662d52de5fbde`.
Phase A ist auf `70a4dbbbedb32d14df60eb76f146975761910264` separat nachvollziehbar.

## Tatsächliche API

`RuntimeObservation` erhält genau diese öffentliche Methode:

```java
/** Client-thread read: current outer tick ID only during its active lifetime; otherwise -1. */
public long getActiveOuterTickId() { return outerTickActive ? outerTickId : -1L; }
```

Sie liest ausschließlich die beiden vorhandenen Felder. Sie erfasst keine Scene,
erzeugt keinen Step, verändert keine Historie und besitzt keinen zweiten Zähler,
Aktivitätsowner, Cache, Timer oder Listener. Es gibt keine API-Abweichung vom Auftrag.
`getOuterTickId()` bleibt unverändert eine historische Diagnose.

| Zustand | Active ID | Historische ID | Zusätzliche Scene-Bedingung |
| --- | ---: | ---: | --- |
| Vor erstem `runTick` | -1 | -1 | Observer ist zunächst gestoppt |
| Erster aktiver Tick, auch vor dem Playerbody | 0 | 0 | ACTIVE und gültig separat prüfen |
| Weitere echte Tickeintritte | jeweils nächste ID | dieselbe ID | separat frisch prüfen |
| Nach `endOuterTick()` | -1 | letzte ID bleibt | Scene kann weiterhin ACTIVE sein |
| Nach `reset()` / Shutdown | -1 | letzte ID bleibt | INVALIDATED bis zum nächsten Tick |
| `invalidateScene()` während eines Ticks | aktive ID bleibt | unverändert | frühere Scene durch Generation ungültig |
| Tatsächlicher World-Unload während eines Ticks | aktive ID bleibt | unverändert | fehlender Player/World macht neue Scene INVALIDATED |

Die API ist client-thread-only. Sie ist kein Synchronisationsmechanismus und
erteilt allein kein Aktionsrecht. World-/Scene-Invalidierung beendet nach dem
bestehenden Vertrag keine laufende `runTick`-Dauer. Deshalb verlangt AutoHeal
zusätzlich frische, übereinstimmende gültige ACTIVE-Scenes.

## Code-Gate und kanonischer Owner

Vor der Änderung wurden die tatsächlichen Observer-, Scene-, Runtime-, Minecraft-
und Tickbasequellen gelesen. Nur `Minecraft.runTick` ruft in Produktion
`beginOuterTick()` auf; sein bestehendes `finally` ruft `endOuterTick()` auf.
Renderframes und Playercallbacks eröffnen keinen Outer-Tick. Reset beendet ihn.
Die Tick-ID wird bei Reset nicht wiederverwendet. Keiner dieser Hooks wurde geändert.

`ClientRuntime` erzeugt genau einen Observer mit `MinecraftSceneSource`.
`RuntimeServices.observation()` liefert diese Instanz oder null, ohne Fallback.
Die Produktionssuche nach `new RuntimeObservation` bestätigt genau diese eine
Konstruktion. Der neue Hooktest prüft die Identität zum Runtime-Accessor explizit.
Observer und Scene behalten ihre bestehenden schwachen Identitätsreferenzen.

## Phase-A-Nachweise

Externe Logs/XMLs: `F:\neutron-core-i01e-sources`.
Alle Mavenaufrufe verwenden JDK `C:\Program Files\Java\jdk-1.8` und `-o`.

| Lauf | Tatsächliches Ergebnis |
| --- | --- |
| `01-phase-a-red` | `testCompile` scheitert an 19 Verwendungen der fehlenden Methode; keine Surefiretests ausgeführt |
| `02-phase-a-green` | 32 Tests, 0 Failures, 0 Errors, 0 Skips; BUILD SUCCESS |

Filter: `RuntimeObservationActiveTickTest,RuntimeObservationTest,RuntimeObservationHookTest`.
Die neue Klasse enthält sechs Tests; die beiden bestehenden Klassen enthalten 15
und 11 Tests. `git diff --check` ist vor Gate A sauber. `javap -verbose` bestätigt
major version 52. Erst danach begann Phase B.

| Pflichtfall | Konkreter Nachweis |
| --- | --- |
| A01–A03 | `firstIdZeroIsActiveOnlyBetweenEntryAndEndAndHistorySurvives`: -1 vor Eintritt, gültige 0, -1 nach Ende, erhaltene History-ID, nächste ID 1 |
| A04 | `actualRunTickFinallyEndsLifetimeAndFramesNeverOpenIt`: echte runTick-Aufrufe in synthetischen Frames 0/3/0/2; Frameaufruf allein öffnet nichts |
| A05 | erster Test und tatsächlicher Profiler-Hook prüfen aktive ID bei null CurrentStep; TickEvent-Konsument wird separat in Phase B geprüft |
| A06 | `realBudgetWithheldKeepsOuterLifetimeWithoutInventingABody`: echtes Budget, kein Body, separate Withheld-Historie, aktive Tickdauer |
| A07 | `realEarnedExtraAndOrdinaryShareOneActiveOuterIdWithoutBudgetConsumptionByReads`: echtes Releasebudget, Ordinary und ein Extra, gemeinsame ID |
| A08 | echter runTick-Profiler-Hook wirft deterministisch nach Eintritt; unverändertes finally beendet den Tick und propagiert denselben Throwable |
| A09 | `invalidationPreservesLifetimeButRejectsOldSceneAndResetEndsLifetime`; zusätzliche Assertions in den bestehenden echten World-Unload-/Shutdown-Hooktests |
| A10 | 100 wiederholte Getteraufrufe im Body und bei History/Withheld verändern weder Step-/Listenidentität noch ID oder Budget; echter runTick-Test bleibt paketfrei; bestehender Walking-/Packet-Reihenfolgetest läuft ebenfalls grün |

Die zwei Änderungen am bestehenden `RuntimeObservationHookTest` ergänzen nur
Active-ID-Assertions: World-Unload lässt die tatsächliche Tickdauer aktiv,
Shutdown beendet sie sofort. Sämtliche bisherigen Assertions bleiben erhalten.

## Reichweite

Die Tests verwenden echte Runtime-/Observer-/Tickbase-/runTick-/Walkingpfade mit
synthetischer Welt und EmbeddedChannel. Der Profiler wird ausschließlich im Test
per Reflection durch eine deterministische Abbruchstelle ersetzt. Produktcode
verwendet keine Reflection. Es gibt keine neue Gameplay-, Server-, GL- oder
Thread-Sicherheitsgarantie. `CORE-OUTER-LIFETIME` ist damit geschlossen; die
GUI-Fachgates bleiben offen.
