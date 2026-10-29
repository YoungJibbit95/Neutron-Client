# CORE-I01a — Vertragsentscheidungen

Stand: 28.10.2026. Basis: `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`.
Dieses Gate gilt ausschließlich für die unangebundenen CORE-0-Werttypen und deren Tests.
Es erteilt keine globale Freigabe des R01-Entwurfs.

## Quellen und Prüfung vor der Implementierung

Rangfolge: Nutzerauftrag / Produktentscheidungen v1.0, RV-Korrekturen, geprüfter
lokaler Code, R01-Vorschläge, historische W00/W01-Berichte.
Gelesen: `00_START_HIER.md`, `06_Codex/ARBEITSREGELN.md`, Architekturreview,
Spezifikation (insbesondere §§0, 3.1, 4–5, 7, 9, 11), alle drei R01-Berichte,
W01-Zusammenfassung und gezielte Abschnitte der drei lokalen W01-Vollberichte.
Zusätzlich README, POM, W00-Ergebnisse, MoveFix-Verifikation und betroffene
Step-/Attack-/Placement-/Motion-Consumers. Das Quellenpaket bleibt außerhalb des Repositories.

Vor dem Coden wurden alle zwölf Punkte wie folgt abgegrenzt:

| RV | Status | Korrektur für diesen Scope / verbleibende Grenze |
| --- | --- | --- |
| RV-01 | DEFERRED_RUNTIME | PRE ist nach Physik und vor Resolve/C03; getrennte Sequenzen unten. Keine neue Phasen-API, kein Hook-Umbau. S01/S13/S16 benötigen später fachliche Owner-/Placement-Tests. |
| RV-02 | CORE0_RESOLVED | Nur automatische Modulaktionen werden auf Eligibility geprüft. Keine globale Domain-Reservation, kein Vanilla-/Controller-Interceptor. Vorhandene Budgets bleiben zusätzlich maßgeblich. |
| RV-03 | CORE0_RESOLVED | `ExecutedStep.Kind` enthält nur ORDINARY/EARNED_EXTRA. `WithheldObservation` ist ein separater, nicht als ExecutedStep verwendbarer Typ ohne Simulations-Callback. |
| RV-04 | DEFERRED_RUNTIME | Frischen Movement-Input vor Physik und Sprint-/Sneak-Finalisierung vor C0B getrennt halten; keine gemeinsame Apply-API. |
| RV-05 | DEFERRED_RUNTIME | `Timer.updateTimer` läuft vor runTick. Spätere Requests können erst künftige Frames beeinflussen; Resolve/TTL/Null-Tick-Frames/Fokus bleiben offen. |
| RV-06 | CORE0_RESOLVED | Scene bindet Connection-, Player-, World-Referenzidentität sowie Connection-Generation und Packet-Context-Epoch. Fehlende/abgelaufene Identitäten und negative Versionen sind ungültig. Keine globalen starken Scene-Referenzen. |
| RV-07 | DEFERRED_RUNTIME | Neutral heißt kein synthetischer Besitz UND tatsächlich gültige neutrale Rate UND keine ungeklärte Fremdschreibung. Identische Fremd-Writes sind per Wertvergleich nicht nachweisbar. Kein Timer-Code. |
| RV-08 | REQUIRES_USER_DECISION | G-CMB-SPEED bleibt vier Yaw-/Pitch-°/s-Werten vorbehalten. G-MOV-SPEEDBASE führt separat Basis-/Potion-/Legacy-Mathematik; keine Werte oder bestehenden Erwartungen ändern. |
| RV-09 | DEFERRED_RUNTIME | Optionalen Input-Snapshot nach MOV-1 verschoben: Raw/Applied-Achsen, analoge Stärke, Sampling-/C0B-Phasen und Arbitration müssen zuerst zusammenpassen. Keine WADS-Booleans oder Key-Writes. |
| RV-10 | CORE0_RESOLVED | Hier nur reine Scene-/Step-/Eligibility-Verträge; keine pauschale S01–S29-Abnahme. Phasenzuordnung siehe unten. |
| RV-11 | CORE0_RESOLVED | ACTIVE/SUSPENDED/INVALIDATED sind getrennte Beobachtungszustände. Suspend verändert die Scene-Identität nicht. Jeder Eligibility-Aufruf prüft den frisch gelieferten aktuellen Kontext; kein Cache/Resume-Automatismus. Owner-/Intent-Revalidierung bleibt Runtime-Aufgabe. |
| RV-12 | CORE0_RESOLVED | Neue Maven-/Surefire-Ergebnisse separat in RESULTS.md mit Run/Failure/Error/Skip-Zahlen. W00/W01-Zahlen sind historische Berichte, keine hier neu ausgeführten Läufe. |

## Korrigierte PRE-/POST-Sequenzen

Codebasis: `EntityPlayerSP` 171–187, 222–330; `Scaffold` 145–183;
`Minecraft` 1067–1094, 1734–1737; `TickbaseBudget.step`.

```text
PRE-Placement:
frischer Input -> Physik -> EventMotion.PRE -> Scaffold-Plan
-> vorherige lokal akzeptierte Rotation UND zugehörigen Owner prüfen
-> ggf. tryPlace(true) VOR applyRotations/aktuellem Resolve/C03
-> finale Sprint-/Sneak-C0B-Zustände
-> RotationManager.resolve -> C03 lokal akzeptiert/abgelehnt
-> finishMotion -> EventMotion.POST

POST-Placement:
frischer Input -> Physik -> EventMotion.PRE -> Scaffold-Plan/Request
-> finale Sprint-/Sneak-C0B-Zustände
-> RotationManager.resolve -> C03 lokal akzeptiert/abgelehnt
-> finishMotion -> EventMotion.POST
-> aktuelle lokal akzeptierte Rotation UND Owner prüfen -> ggf. place(false)
```

PRE darf keine erst später akzeptierte Rotation voraussetzen. POST-Ablehnung
macht ein früheres PRE-Placement nicht rückwirkend zu einer POST-Aktion.
Scene, Slot, Face, Raycast und Placement-Budget müssen zusätzlich gelten.
Lokale Transportannahme ist keine Serverbestätigung.
`WITHHELD` wird am späteren Tickbase-Entscheidungspunkt beobachtet, ohne einen
Player-Body oder künstliche Begin/End-Step-Ereignisse zu erzeugen.

## Reiner API-Vertrag

Vier neue Klassen im Paket `me.youngjibbit.core.contract`:

- `SceneContext`: unveränderlicher Beobachtungssnapshot mit schwachen Identitätsreferenzen,
  Generation/Epoch und Verfügbarkeitszustand. `sameSceneAs` verwendet `==`, niemals
  Entity-/World-`equals`. Der Zustand ist keine Lifecycle-Steuerung; aktuelle Identitäten
  müssen während eines Aufrufs durch den tatsächlichen Scene-Besitzer lebendig gehalten werden.
- `ExecutedStep`: ORDINARY mit Index 0 oder EARNED_EXTRA mit Index >0, nichtnegative
  Outer-Tick-ID, Scene-Snapshot und explizites Interrupted-Merkmal. Der Typ beschreibt
  einen tatsächlich betretenen Body; er führt ihn nicht aus und beweist keine Runtime-Ausführung.
- `WithheldObservation`: nur Scene und Outer-Tick-ID einer ausgelassenen Gelegenheit;
  kein ausgeführter Step-Index, keine gemeinsame ExecutedStep-Hierarchie.
- `AutomationEligibility`: notwendige Vorprüfung für ATTACK/PLACEMENT/INVENTORY,
  kein Aktionsrecht oder Budget. Nur nicht unterbrochenes ORDINARY im explizit
  aktuellen Outer-Tick, mit gültigem aktivem Step- UND aktuellem Scene-Kontext.

Bei jedem Versuch ist der aktuelle Kontext neu zu erfassen und gegen den Step zu prüfen.
Historische Snapshots invalidieren sich nicht durch Magie; der spätere Runtime-Adapter
muss Wechsel/Correction/Disconnect im aktuellen Kontext abbilden und Epochen monoton
fortschreiben, auch bei einer Rückkehr zu denselben Objekten (ABA).
Suspend bewahrt logische Modulabsichten außerhalb dieser API. Resume verlangt neue
Scene-/Slot-/Plan-/Owner-Prüfungen und frisch akzeptierte Rotation für MoveFix/Placement.
Ein aktiver Snapshot allein bestätigt keine Rotation.

Eine positive Prüfung führt keine Aktion aus, verbraucht nichts und reserviert nichts.
Aufrufer müssen danach weiterhin `AttackScheduler`, `ScaffoldPlacementService` bzw.
den geprüften Inventory-/Controllerpfad mit dessen Budget/Guards benutzen.
Mehrere Module/Abfragen im ORDINARY erhalten keine neue globale Domain-Sperre.
Manuelle Vanilla-Klicks, manuelle Inventory-Aktionen und externe Controlleraktionen
liegen außerhalb der API; ein fehlender Step verweigert nur die Automationsvorprüfung.
Es gibt keine Callbacks, Timer-/Input-Werte oder Fließkommaeingaben in den neuen Typen;
NaN-/Infinity-Prüfungen sind deshalb hier nicht anwendbar.

## Testphasen und offene Entscheidungen

| Phase | Umfang |
| --- | --- |
| CORE-0 / dieser Auftrag | Reine Anteile S02–S06/S07: Step-Art, Withheld-Trennung, Scene-Wechsel, Suspend, side-effect-freie Eligibility, bestehendes AttackScheduler-Budget als Kompositionsbeispiel. Keine Integration dieser Fälle behauptet. |
| CORE-1 / spätere Hooks | Tatsächlicher Decision-/Body-Boundary, aktuelle Tick-/Scene-Erfassung, Suspend/Lifecycle, Exception- und Abbruchpfade. |
| Fachänderungen | S01/S13–S16 Rotation/Placement, S08–S12 Timer, S17–S28 Input/Module/Config/NET; S29 fachübergreifendes Teardown. S06/S07 zusätzlich in Runtime prüfen. |
| LIVE | R1–R6, Spiel-/Server-/OptiFine-/Shader-/Config-Abnahme; hier nicht ausgeführt. |

Unverändert PROPOSED/offen: C1/D1 Bewegungshook-Alternativen, C2/M1/D2/M7
Timer-Priorität/TTL/Restore/Neutralität, C3/M2/D5 Input-Arbitration, C5/M3
Instanzmigration, M4/D6 BridgeAssist-/AutoBridger-Details, D4 Speed-Formel,
D7/D8 Legacy-Modi, D9 Altkonfig-Migration und Release-Gates.
Die Produktprinzipien C4/M5/D3 sowie M6/D10 bleiben bindend; ihre Runtime-Migration
wird hier nicht umgesetzt. G-MOV-AUDIT wird durch diesen Auftrag nicht global freigegeben.

G-CMB-SPEED (vier Rotationsgeschwindigkeiten) und G-SCF-ANGLES (acht Scaffold-Profile)
benötigen weiterhin gesonderte Nutzerfreigabe; G-MOV-SPEEDBASE ist davon getrennt.
Keine neuen numerischen Defaults, kein Timer-/Input-/Rotation-Service, keine Hooks,
keine POM-/Config-/Bestandstest-Änderung. Ende nach CORE-I01a.
