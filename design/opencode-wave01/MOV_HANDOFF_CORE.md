# MOV-W01 — Handoff an CORE

**Projekt:** Pulsar-Client / Neutron 2.0
**Welle:** W01, schreibgeschützt
**HEAD:** `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`
**Datum:** 2026-10-27
**Bezug:** `MOV_AUDIT_W01.md`, `MOV_WRITE_ACCESS_MATRIX.md`
**Adressat:** CORE (Bewegungs-/Tick-/Ownership-Architektur), mit Schnittstellen zu MOV, SCF, CMB, NET, MOD

---

## 0. Zweck dieses Dokuments

Dieses Dokument ist **keine** Implementierungsanweisung für W01 und enthält **keinen** Produktivcode. Es übergibt die in W01 verifizierten Befunde als **Arbeitspakete, Invarianten und Abnahmekriterien** an CORE. Die Reihenfolge ist so gewählt, dass zuerst der Vertrag und die Testbarkeit stehen, dann die globalen Zustände, dann die Module.

**Ausgangslage:** Die MoveFix-Mathematik ist korrekt. Die Probleme sind:

- kein einheitlicher Phasenvertrag,
- kein zentraler Besitz für Timer, Sprint, Sneak, Jump,
- kein Suspension-/Lifecycle-Vertrag,
- Scaffold gibt den Rotations-Owner pro Platzierung frei,
- mehrere Module schreiben globale Zustände direkt.

**Leitregel:** Erst den Vertrag und den Test dafür bauen, dann den ersten Writer migrieren. Keine „große“ Bewegungsreform in einem Schritt.

---

## 1. Fünf CORE-Entscheidungen vor der ersten Zeile Code

| ID | Entscheidung | Warum CORE | Empfehlung |
|---|---|---|---|
| **C1** | Bleibt `EventMotion.PRE` die Paketphase (nach Bewegung), oder kommt ein echtes Pre-Movement-Event? | Jede Modulmigration hängt daran | **Bleibt**; Namen dokumentieren, `StepContext` einführen. Kein neuer Mixin/Hook in W02 ohne separaten Beschluss. |
| **C2** | Bekommt `timerSpeed` einen zentralen Lease-Dienst? | Drei Writer, Tickbase-Abhängigkeit | **Ja**; Token/Generation, Priorität Scaffold > Speed > Velocity. |
| **C3** | Wird Sprint/Sneak/Jump zentral aufgelöst? | Fünf Sprint-Quellen, Key-Overrides, Jump-Additivität | **Ja**; ein Resolver pro Zustand, TTL für WTap, physische Taste als Default. |
| **C4** | Wird der Scaffold-Rotations-Owner bis Disable gehalten? | Spec 3.1; MoveFix-Kontinuität | **Ja**; `resetPlan()` und `releaseOwner()` trennen. |
| **C5** | Wird `MovementInput` wieder instanzbasiert? | static ist prozessweit; Tests resetten bereits | **Ja**, aber als eigene, spätere Welle (breite mechanische Änderung). |

Ohne diese fünf Entscheidungen riskiert jede Moduländerung, nur einen weiteren Writer zu einem bereits mehrdeutigen Zustand hinzuzufügen.

---

## 2. Invarianten (müssen nach W02 gelten)

### I1 — Phase

- `EventUpdate` ist **vor** der Bewegung.
- `EventMotion.PRE` ist **nach** der Bewegung und **vor** Sprint-/Sneak-C0B, `resolve`, C03 und POST.
- `EventMotion.POST` ist **nach** C03 und `finishMotion`.
- Motion-Writes in PRE wirken auf den **nächsten** Schritt; Sprint-/Sneak-Writes in PRE wirken auf das C0B **dieses** Ticks.
- Kein Modul darf aus PRE eine „Bewegung dieses Ticks“ ableiten, ohne den `StepContext` zu berücksichtigen.

### I2 — Ein Writer pro globalem Zustand

Für `timerSpeed`, `setSprinting`, `keyBind*.pressed` (synthetisch), `rotationYaw/Pitch` (Kamera), `motionX/Y/Z` (modulare Anteile) gilt: **ein** verantwortlicher Writer/Resolver. Andere Module stellen **Requests**, keine direkten Writes.

### I3 — Suspension

Bei `currentScreen != null`, `!inGameHasFocus`, `isGamePaused`, fehlendem Spieler/Welt/Connection oder totem Spieler darf **kein** Modul Motion, Keys, Sprint, Sneak, Jump oder Kamera verändern. Lifecycle-/Owner-Aufräumarbeiten sind erlaubt, aber keine neuen Aktionen. Der Guard gehört an den Listener-Eintritt, **nicht** an den Event-Produzenten (sonst bleibt `RotationManager`/`Scaffold` in einem halben Zustand).

### I4 — MoveFix-Basis

MoveFix korrigiert nur auf Basis eines für **diese** Welt/Connection/diesen Spieler **lokal akzeptierten** Owners. Eine angeforderte, aber nicht bestätigte Rotation begründet keine Korrektur. `canUseRotation` (Platzierung) und `getConfirmedOwner` (MoveFix) müssen **denselben** Begriff von „verwendbar“ haben.

### I5 — Timer

`timerSpeed` wird nur über eine Lease geändert. Restore nur bei eigenem Token. Ein Fremdschreiber invalidiert die Lease, aber die Lease schreibt nicht über ihn zurück. `Tickbase` liest „neutral“ (kein Lease), nicht `== 1.0`.

### I6 — Sprint

Sprint wird nach allen PRE-Requests **einmal** entschieden und vor dem C0B-Sync angewendet. WTap2-Requests haben eine TTL von 1–2 Ticks; Scaffold hält Stop, solange ein gültiger Plan aktiv ist; Sprint ist der Fallback; Vanilla bleibt die letzte Instanz.

### I7 — Sneak/Keys

Physische Tasten bleiben Default. Synthetische Requests werden als Lease mit Quelle/TTL gehalten und nur eigene Requests freigegeben. `AutoBridger` schreibt keinen `pressed`-Zustand mehr. `WTap2` schreibt nur, wenn kein anderer Lease-Inhaber höher priorisiert ist.

### I8 — Jump

Pro Tick höchstens **ein** `jump()` über den Resolver. Der Sprint-Impuls darf nicht durch mehrere Quellen addiert werden. `JumpReset`, `Velocity.Jump`, `Speed`, `Scaffold.AutoJump` werden Requests.

### I9 — Input

`MovementInput` ist pro Spieler instanzbasiert. `Scaffold.suppressSneak` arbeitet auf der Instanz, nicht auf statischen Feldern. `MoveFix` liest die Rohwerte aus dem Event/Snapshot, nicht aus globalen Statics.

### I10 — Lifecycle

Weltwechsel, Respawn, Death, Disconnect und Disable rufen einen zentralen, idempotenten `MovementLifecycle.reset(reason)` auf. Jedes Bewegungsmodul registriert seinen Reset; kein Modul verlässt sich darauf, dass `onDisable` beim Weltwechsel kommt.

### I11 — Schritte

`Tickbase` markiert jeden simulierten Schritt mit einem `StepContext` (`clientTick`, `stepIndex`, `simulated`, `reason`). Bewegung darf pro Schritt laufen; Außenwirkungen (Pakete, Key-Writes, Chat) werden pro Client-Tick dedupliziert, sofern sie nicht ausdrücklich pro Schritt gewünscht sind.

### I12 — Keine stillen Config-Änderungen

Neue Defaults (z. B. Scaffold-MoveFix „Präzise“) gelten für **neue** Profile. Bestehende explizite Werte bleiben erhalten. Entfernte/defekte Velocity-Modi werden gekennzeichnet und migriert, nicht still umbenannt.

---

## 3. Zielarchitektur (konzeptionell)

Keine Produktivimplementierung; nur Verantwortlichkeiten und minimale Schnittstellen. Java 8, keine neuen Bibliotheken.

```text
CORE
├─ MovementContext
│    ├─ SuspensionGate        (GUI/Fokus/Pause/Welt/Spieler)
│    ├─ StepContext           (clientTick, stepIndex, simulated, reason)
│    └─ MovementLifecycle     (Welt/Death/Respawn/Disconnect/Disable)
├─ StateArbiters
│    ├─ TimerArbiter          (Lease, Token, Priorität, before/written)
│    ├─ SprintController      (Request, Priorität, TTL, ein Resolver)
│    ├─ SneakController       (Lease für AutoBridger/Scaffold)
│    ├─ JumpResolver          (eine Ausführung pro Tick)
│    └─ RotationOwnership     (bestehender RotationManager, erweitert)
├─ MovementBasis
│    ├─ InputSnapshot         (Instanzwerte, unveränderlich pro Schritt)
│    └─ MoveFix                (bestehend; nutzt Owner + Snapshot)
└─ Diagnostics
     ├─ Owner-/Lease-Trace     (nur Debug)
     └─ Konfliktanzeige        (z. B. doppelte Keys)
```

### 3.1 `TimerArbiter` — minimales Vertragsbild

```java
// Konzeptionell; keine Implementierung in W01.
interface TimerLease {
    long acquire(String owner, int priority, float desired, long now);
    void update(long token, float desired);
    void release(long token);
    boolean isNeutral();
    float current();
}
```

Regeln:

- Ein Token ist die Identität, nicht der Wert.
- `before` wird beim Acquire gespeichert; `written` bei jedem Update.
- Ein höher priorisierter Lease darf einen niedrigeren verdrängen; der niedrigere wird **invalidiert**, nicht wiederhergestellt.
- `release` stellt nur wieder her, wenn `token` aktuell und `current == written`.
- Welt/Disable invalidiert alle Leases und setzt `1.0` nur, wenn kein Lease aktiv ist.
- `Tickbase` fragt `isNeutral()`, nicht `timerSpeed == 1.0`.

### 3.2 `SprintController` — minimales Vertragsbild

```java
enum SprintIntent { START, STOP }

interface SprintController {
    void request(String source, int priority, SprintIntent intent, int ttlTicks);
    boolean resolve(boolean vanillaWantsSprint);
    void tick();
}
```

Regeln:

- Requests werden pro Tick gesammelt; `resolve` wählt die höchste Priorität, bei Gleichstand die ältere Quelle.
- WTap2: `STOP` mit TTL 1–2; Scaffold: `STOP`, solange Plan gültig; Sprint: `START` als Fallback; Vanilla: Basis.
- Nur der Controller ruft `setSprinting`; der Vanilla-Sync bleibt unverändert.
- `MoreKB` sendet keine rohen C0B mehr, sondern nutzt denselben Controller (oder wird entfernt).

### 3.3 `SneakController`

- `AutoBridger` fordert `SNEAK` für den Luftzustand an, mit TTL.
- `Scaffold.Downwards` liest die **physische** Taste und die eigene Downwards-Absicht, nicht den synthetischen `pressed`-Zustand.
- `BridgeAssist` liest die physische/Entity-Sneak-Absicht über den Controller.
- Kein Modul setzt `keyBindSneak.pressed` direkt.

### 3.4 `JumpResolver`

- Quellen: Vanilla, Speed, Scaffold.AutoJump, Velocity.Jump/Intave, JumpReset.
- Eine Ausführung pro Tick; Priorität nach Modul (Vorschlag: Vanilla/expliziter Nutzer > Scaffold > Velocity > Speed > JumpReset).
- `isAirBorne`/`onGround` werden vor der Ausführung geprüft; der Sprint-Impuls wird nur einmal berechnet.
- Kein Modul ruft `jump()` direkt, außer Vanilla.

### 3.5 `RotationOwnership` (Erweiterung des bestehenden `RotationManager`)

- `beginMotion`/`request`/`resolve`/`finishMotion` bleiben.
- **Neu:** `resetPlan(source)` und `releaseOwner(source)`.
  - `resetPlan` löscht Ziel/Sequenz/Frame, **nicht** den Owner.
  - `releaseOwner` löscht den Owner (und optional die sichtbare Basis nach 2–4 Ticks).
- **Neu:** eine gemeinsame Prädikatsfunktion `usableOwner(pre)` für Platzierung und MoveFix; `canUseRotation` und `getConfirmedOwner` dürfen nicht auseinanderlaufen.
- **Neu:** optionaler `OwnerLease`-Trace (Quelle, Priorität, letzter akzeptierter Zeitpunkt) für Diagnose.
- `Scaffold` ruft nach erfolgreicher Platzierung nur `resetPlan`; `releaseOwner` nur bei Disable/Welt/Connection/Spielerwechsel.

### 3.6 `MovementLifecycle`

```java
interface MovementLifecycle {
    void reset(ResetReason reason, Object player, Object world, Object connection);
}
```

- Auslöser: `WorldEvent` (Load/Unload), Respawn, Death, Disconnect, Modul-Disable, Config-Reset.
- Idempotent; jedes Modul registriert sich.
- Kein Modul speichert Spieler/Welt/Connection über den Reset hinaus.

### 3.7 `StepContext`

- `clientTick`, `stepIndex`, `simulated`, `reason` (`ordinary`, `withheld`, `release`, `replay`).
- Events tragen den Kontext (oder einen `ThreadLocal`-freien Parameter).
- Per-Tick-Zähler nutzen `clientTick`; pro-Schritt-Aktionen prüfen `simulated`/`stepIndex`.
- `Tickbase` ist der einzige Produzent von `simulated=true`.

### 3.8 `SuspensionGate`

- Prüft: `mc != null`, `thePlayer != null`, `theWorld != null`, `currentScreen == null`, `inGameHasFocus`, `!isGamePaused`, `connection open`, `player alive`, `!riding`, `!spectator`.
- Wird am Anfang jedes Bewegungslisteners aufgerufen.
- **Nicht** im Event-Produzenten, damit `finishMotion`/Owner/Lifecycle weiterlaufen.

---

## 4. Arbeitspakete

Reihenfolge = empfohlene Implementierungsreihenfolge. Größen: S (≤ 1 Tag), M (2–4 Tage), L (> 1 Woche). Aufwände sind Schätzungen, keine Zusagen.

### WP1 — Phasenvertrag + SuspensionGate (S/M)

**Findings:** MOV-W01-001, -002
**Ziel:** Ein dokumentierter, getesteter Tick-Vertrag; kein Bewegungsmodul reagiert bei GUI/Fokus/Pause.
**Schritte:**

1. `EventMotion`/`EventUpdate`-Kommentare an den tatsächlichen Aufrufpfad anpassen (`EntityPlayerSP` 168–333).
2. `SuspensionGate` (oder Utility) einführen; in jedem Bewegungslistener am Eintritt prüfen.
3. Test: geskripteter Tick protokolliert die Reihenfolge; GUI/Fokus-Fälle setzen keine Aktion.
4. Keine Änderung an `RotationManager.finishMotion`/Scaffold-Lifecycle.

**Abnahme:**

- Ein Test schlägt fehl, wenn ein Modul Motion vor der Bewegung erwartet.
- Bei `currentScreen != null`/`!inGameHasFocus` bleiben Motion, Keys, Sprint, Sneak, Jump und Kamera unverändert; erlaubte Lifecycle-Resets laufen.
- `SprintOrderingChecks`, `Scaffold*Test`, `NetworkMoveFixIntegrationTest` bleiben grün.

**Risiko:** Ein zu breiter Gate blockiert legitime Rotation-/Owner-Aufräumarbeiten. Deshalb nur Aktionen gaten, nicht Events.

### WP2 — TimerArbiter (M)

**Findings:** MOV-W01-003
**Ziel:** Ein Token-basierter Timer-Lease; keine direkten `timerSpeed`-Writes mehr.
**Schritte:**

1. `TimerArbiter`/`TimerLease` einführen (keine neue Lib).
2. `Scaffold.TimerControl` auf den Arbiter migrieren; `releasePlacement` nutzt nur `release(token)`.
3. `Speed`/`Velocity` migrieren; `onDisable` gibt nur den eigenen Lease frei.
4. `Tickbase.usable()` auf `isNeutral()` umstellen.
5. Interleaving-Tests: gleicher Wert, Fremdschreiber, Moduswechsel, Disable, Weltwechsel.

**Abnahme:**

- Kein `timerSpeed =` außerhalb des Arbiters (Grep-Test).
- `Scaffold`-Timer bleibt während einer Platzierung stabil, auch wenn Speed aktiv ist.
- `Speed` Polar 1.03 → Intave **muss** auf 1.0 zurücksetzen.
- `Velocity` Disable überschreibt keine fremde Lease.
- Tickbase bleibt bei neutralem Timer nutzbar.

**Risiko:** Prioritätsentscheidung ändert Speed-/Scaffold-Timing. Deshalb zuerst Telemetrie/Debug-Trace, dann Politik.

### WP3 — Sprint/Sneak/Jump-Controller (M/L)

**Findings:** MOV-W01-006, -007, -016
**Ziel:** Ein Resolver pro Zustand; keine direkten `setSprinting`/`keyBind*`-Writes.
**Schritte:**

1. `SprintController` mit Requests/TTL; `Sprint`, `WTap2`, `Scaffold`, `MoreKB` migrieren.
2. `SneakController`; `AutoBridger` fordert an, statt `pressed` zu setzen; `Scaffold.Downwards` liest physisch.
3. `JumpResolver`; `Speed`, `Scaffold.AutoJump`, `Velocity.Jump/Intave`, `JumpReset` migrieren.
4. Key-Konflikt G: AutoBridger-Default auf `KEY_NONE`; bestehende explizite Bindings nicht überschreiben; UI-Hinweis.
5. Kombinationsmatrix testen.

**Abnahme:**

- WTap2 Legit funktioniert mit aktivem Sprint-Modul.
- Scaffold unterdrückt Sprint nur bei gültigem Plan; danach Vanilla/Sprint.
- Physisches Shift bleibt bei AutoBridger an/aus erhalten; G toggelt im Frischprofil nur Scaffold.
- Zwei Jump-Quellen im selben Tick → ein `jump()`, ein Impuls.
- Paket-Trace: genau ein C0B pro Zustandswechsel.

**Risiko:** TTL/Prioritäten sind fachlich; falsche Priorität kann WTap oder Scaffold fühlbar verändern. Deshalb Matrix + Debug-Trace.

### WP4 — Scaffold-Rotations-Owner trennen (S/M)

**Findings:** MOV-W01-004, -015
**Ziel:** Owner bleibt über Platzierungen; Plan-Reset und Owner-Release sind getrennt.
**Schritte:**

1. `RotationManager.resetPlan(source)` und `releaseOwner(source)` einführen.
2. `Scaffold.releasePlacement` in `resetPlan` (Ziel/Sequenz/Downwards/Tower/Timer) und `releaseOwner` (nur Disable/Welt/Connection/Spielerwechsel) aufteilen.
3. `onValueChange` ruft nur `resetPlan`, nicht `releaseOwner`.
4. `canUseRotation(pre)` und `getConfirmedOwner()` über ein gemeinsames Prädikat verbinden.
5. MoveFix-Default für neue Profile auf „Präzise“; Altprofile unverändert.
6. Test: 180°-Scaffold, Platzierung, nächster `moveFlying`-Event hat Owner; N Platzierungen; Disable/Welt release.

**Abnahme:**

- Nach jeder Platzierung bleibt `owner = scaffold`, solange Scaffold aktiv und Kontext gültig ist.
- `PRECISE` erhält die Kamerarichtung über die Platzierung hinweg.
- `canUseRotation` und `getConfirmedOwner` liefern im selben Tick konsistente Ergebnisse.
- `onValueChange` beendet den Plan, nicht den Owner.
- Frischprofil: MoveFix „Präzise“; Altprofil „Aus“ bleibt „Aus“.

**Risiko:** Sichtbare Rotation kann zwischen Platzierungen stehen bleiben; optional 2–4 Tick-Auslauf, aber ohne MoveFix-Basisverlust.

### WP5 — Speed-Basis korrigieren (S/M)

**Findings:** MOV-W01-005
**Ziel:** Eine spezifizierte, getestete Basis; Potion nicht doppelt; Sprint-Basis stabil.
**Schritte:**

1. Formelentscheidung (D4): Vanilla-Basis + separate Potion, oder dokumentiertes Tuning.
2. `getModeBaseSpeed`/`getSpeedAmplifier` bereinigen; `Vulcan`/`Vulcan2` nicht doppelt skalieren.
3. Basis vor mutierenden Sprintentscheidungen einfrieren oder als Parameter übergeben.
4. Testmatrix: kein Potion, Speed I/II, Slowness I/II, Sneak, Sprint, Scaffold-Sprint aus/an, alle fünf Modi.

**Abnahme:**

- Erwartete Werte stammen aus der Spezifikation, nicht aus der aktuellen Implementierung.
- Kein Modus ist ohne Potion unbemerkt 20 % schneller.
- Scaffold+Sprint liefert unabhängig von der Listener-Reihenfolge denselben Basiswert.
- Die alten Tests werden bewusst angepasst, nicht „grün gehalten“.

**Risiko:** Ändert das Spielgefühl aller Speed-Nutzer. Deshalb explizite Entscheidung + Config-/Release-Hinweis.

### WP6 — MovementInput instanzbasiert + toten Code entfernen (M/L)

**Findings:** MOV-W01-012, -017
**Ziel:** Keine prozessweiten Inputs; keine toten APIs, die MoveFix umgehen.
**Schritte:**

1. `MovementInput.moveStrafe/moveForward` von `static` auf Instanzfelder umstellen; alle Zugriffe auf `player.movementInput.*` prüfen.
2. `Scaffold.suppressSneak` auf der Instanz; `applyDownwardsInput` unverändert im Aufrufpfad.
3. `PlayerMoveUpdateEvent.applyMotion` entfernen oder klar als tot markieren/entfernen; `Movefix`-Event entfernen.
4. `Speed.resetMovementOverrides` auf Timer/Speed begrenzen; `onValueChange` ergänzen.
5. Tests: zwei Input-Instanzen, Weltwechsel, Sneak-Scaling, Scaffold-Test.

**Abnahme:**

- Zwei `MovementInput`-Instanzen beeinflussen sich nicht.
- Kein Grep-Treffer für `MovementInput.moveForward` als statischer Zugriff außerhalb der Instanz.
- `MovementHelperContractTest` und `Scaffold*Test` grün.
- Kein toter `applyMotion`-Aufruf mehr.

**Risiko:** Breite, mechanische Änderung; kann subtile Input-/Sneak-Effekte ändern. Deshalb eigener Commit/PR und vollständige Testmatrix.

### WP7 — Paketdisziplin (S/M)

**Findings:** MOV-W01-010, -011, -018
**Ziel:** Keine rohen C0B/C07/C08/C09 ohne Zustands-/Cache-Bezug; keine No-op-Modi.
**Schritte:**

1. `MoreKB` auf SprintController migrieren (oder Modul entfernen); Target-Timeout; Debug hinter Setting.
2. `NoSlow`: „Vanilla“ als echten No-op oder umbenennen; X/Z-Zuordnung klären; Pakete nur bei Zustandswechsel.
3. `WTap2.Spoof` umbenennen oder echtes Spoofen implementieren; Key-Restore prüfen.
4. `BridgeAssist` PRE-only, Wrap, Owner (siehe WP10).
5. Paket-Trace-Tests.

**Abnahme:**

- Kein C0B außerhalb des SprintControllers/Vanilla-Sync.
- NoSlow-Paketzähler pro Tick begrenzt; „Vanilla“ verändert den Event nicht.
- Modusnamen entsprechen dem Verhalten.

**Risiko:** Paketänderungen können Serververhalten ändern; keine AC-Zusage, nur lokale Konsistenz.

### WP8 — Tickbase-StepContext (M)

**Findings:** MOV-W01-014
**Ziel:** Simulierte Schritte sind markiert; per-Tick-Zähler und Außenwirkungen sind klar.
**Schritte:**

1. `StepContext` einführen; `Tickbase` markiert `simulated=true`.
2. `Speed`-Zähler auf Client-Tick oder explizite Schritt-Semantik umstellen.
3. `WTap2`/`MoreKB`/`NoSlow` Außenwirkungen pro Client-Tick deduplizieren.
4. Tests: Release 1×/2×/3× mit Speed/WTap/MoreKB.
5. `Tickbase.usable()` mit TimerArbiter koppeln.

**Abnahme:**

- Keine doppelten Key-/Paketaktionen pro Frame.
- Speed-Zähler verhalten sich spezifikationsgemäß.
- Tickbase bleibt mit neutralem Timer nutzbar.

**Risiko:** Tickbase ist ein Exploit-Modul; Semantikänderungen können die beabsichtigte Wirkung verändern. Deshalb zuerst Dokumentation/Tests, dann Verhalten.

### WP9 — Lifecycle-Reset (S)

**Findings:** MOV-W01-013
**Ziel:** Kein stale Bewegungszustand über Welt/Death/Respawn.
**Schritte:**

1. `MovementLifecycle.reset(reason)` zentral; Module registrieren.
2. `WorldEvent`, Respawn, Death, Disconnect, Disable anschließen.
3. Tests: halber Zustand + Weltwechsel → neutral.

**Abnahme:**

- `Speed`/`Velocity`/`NoSlow`/`WTap2`/`BridgeAssist`/`MoreKB`/`JumpReset`/`AutoBridger`/`Sprint` starten neutral.
- Kein Modul speichert Spieler/Welt/Connection über den Reset.

**Risiko:** Gering; kann aber bestehende „zufällig funktionierende“ Zustände verändern.

### WP10 — BridgeAssist (S/M)

**Findings:** MOV-W01-008
**Ziel:** Entweder rein visuell oder sauberer Rotation-Request.
**Schritte:**

1. Entscheidung: visuell (Render-Pose) vs. Kamera-Assist (`requestVisible`).
2. PRE-only; `wrapAngleTo180`; nächster Kandidat statt erster linearer.
3. SuspensionGate; Null-Guard; `aimAt` nur intern.
4. Test: PRE+POST → ein Schritt; Owner; 350°→0°; GUI.

**Abnahme:**

- Ein Schritt pro Tick.
- Kein direkter Kamera-Write außerhalb des RotationManagers (falls Kamera-Assist).
- ±180-Fall korrekt.
- Keine Aktion bei GUI.

**Risiko:** Sichtbare Kameraassistenz kann als „fremdgesteuert“ empfunden werden; visuelle Variante ist safer.

---

## 5. Reihenfolge und Abhängigkeiten

```text
WP1 (Phase/Suspension)
 ├─ WP2 (Timer)
 ├─ WP4 (Rotation/Owner)
 │    └─ WP5 (Speed-Basis, braucht stabile Sprint-/Owner-Basis)
 ├─ WP3 (Sprint/Sneak/Jump)
 │    ├─ WP7 (Pakete, braucht SprintController)
 │    └─ WP8 (Tickbase, braucht StepContext + Timer)
 ├─ WP9 (Lifecycle)
 └─ WP10 (BridgeAssist, braucht Suspension + Rotation)
WP6 (Input) nach WP1/WP3/WP10, weil es die breiteste mechanische Änderung ist.
```

**Meilensteine:**

- **M1:** WP1 + WP4 + WP2 — Vertrag, Owner, Timer; die drei P1-Blöcke.
- **M2:** WP3 + WP5 — Sprint/Sneak/Jump und Speed-Basis.
- **M3:** WP7 + WP10 + WP9 — Pakete, BridgeAssist, Lifecycle.
- **M4:** WP6 + WP8 — Input-Instanz und Tickbase-Semantik.

---

## 6. Testplan

### 6.1 Unit-/Vertragstests (synthetisch, offline)

| Test | Prüft | Ort |
|---|---|---|
| `TimerArbiterTest` | Acquire/Update/Release, gleicher Wert, Fremdschreiber, Priorität, Welt-Reset | neu, `core/combat` oder `core/timer` |
| `SprintControllerTest` | Requests, TTL, Priorität, Vanilla-Fallback, ein Resolve | neu |
| `SneakControllerTest` | physisch vs. synthetisch, TTL, Enable/Disable | neu |
| `JumpResolverTest` | eine Ausführung, Impuls nicht additiv | neu |
| `SuspensionGateTest` | GUI/Fokus/Pause/Welt/Dead | neu |
| `MovementLifecycleTest` | Reset pro Modul, idempotent | neu |
| `ScaffoldOwnerContinuityTest` | Owner über N Platzierungen, 180°, Disable/Welt | neu |
| `SpeedBasisMatrixTest` | Potion/Sneak/Sprint/Scaffold, alle Modi | erweitert |
| `MovementInputInstanceTest` | zwei Instanzen, Weltwechsel | neu |
| `TickbaseStepContextTest` | 1×/2×/3× Release, Key-/Paketdedupe | erweitert |
| `PacketTraceTest` | C0B/C07/C08/C09 pro Tick | neu |

### 6.2 Bestehende Tests, die grün bleiben müssen

- `MoveFixControllerTest`, `MoveFixProductionTest`, `MoveFixConfigTest`
- `NetworkMoveFixIntegrationTest`, `RotationCorrectionsTest`, `RotationBehaviorHookTest`
- `MovementHelperContractTest`
- `SpeedMovementBasisTest` (nach bewusster Anpassung der Sollwerte)
- `NoSlowSprintTest`, `SprintOrderingTest`
- `TickbaseBudgetTest`
- `Scaffold*Test`, `TickbaseBehaviorHookTest`, `ScaffoldPipelineIntegrationTest`

### 6.3 Testdesign-Regeln

- Kein echter Server, kein Account, kein öffentliches Netzwerk.
- Keine `Thread.sleep`-Abhängigkeit, wenn eine Clock injizierbar ist; WTap/BridgeAssist brauchen eine testbare Uhr.
- Kinder-JVM nur, wenn die statische Minecraft-Fixture es erzwingt (bestehendes Muster).
- Jeder Test muss **eine** Invariante prüfen; keine „alles grün“-Sammeltests.
- Fehlschlag muss die Ursache nennen (Owner, Token, Phase, Key), nicht nur `assertTrue`.

### 6.4 Späterer Live-Test (nicht W01)

Nur nach separater Freigabe:

- lokaler Testserver, keine öffentlichen Server,
- synthetischer Account/keine persönlichen Daten,
- Fokus: Paketreihenfolge, Sprint-/Sneak-Sync, Scaffold-Platzierung, GUI-Suspension,
- **keine** Anti-Cheat-Aussage, keine „unflaggbar“-Behauptung.

---

## 7. Migrations- und Config-Regeln

| Bereich | Regel |
|---|---|
| MoveFix-Default | Neue Profile „Präzise“; bestehende explizite „Aus“-Werte bleiben; Migration im Decoder dokumentieren. |
| AutoBridger-Key | Default `KEY_NONE`; bestehende explizite G-Bindung nicht still überschreiben; UI zeigt Konflikt. |
| Velocity-Modi | Defekte Modi (Minemen/Verus) zuerst testen; dann reparieren oder klar entfernen; Config-Wert migrieren oder als „legacy/defekt“ markieren. |
| NoSlow „Vanilla“ | Nicht still den Default ändern; entweder echtes No-op oder Label migrieren. |
| Timer | Keine neuen Slider; bestehende Werte bleiben; Lease ist intern. |
| Sprint/Sneak | Keine neuen Pflicht-Settings; TTL/Priorität intern, Debug optional. |
| Alte Configs | Unbekannte Werte dürfen nicht crashen; `decodeConfig` muss weiterhin unbekannte/legacy Werte verständlich behandeln. |

---

## 8. Risiken und Gegenmaßnahmen

| Risiko | Wirkung | Gegenmaßnahme |
|---|---|---|
| Zu breiter SuspensionGate | Rotation/Owner bleibt hängen | Nur Aktionen gaten, Events/Lifecycle weiterlaufen lassen |
| Timer-Priorität falsch | Speed/Scaffold fühlt sich anders an | Debug-Trace, Matrix, schrittweise Migration |
| Scaffold-Owner bleibt zu lange | sichtbare Rotation zwischen Blöcken | optionaler 2–4-Tick-Auslauf, aber MoveFix-Basis halten |
| SprintController zu starr | WTap/Scaffold unerwartet | TTL + Priorität + Kombinationsmatrix |
| JumpResolver ändert Impuls | Sprunghöhe/Weite | Impuls nur einmal, Tests |
| Input-Instanzfelder | breite Regression | eigener Meilenstein, vollständige Tests |
| Tickbase-Semantik | Exploit-Wirkung ändert sich | StepContext + explizite Tests, keine stillen Änderungen |
| Test-Fixture (static Minecraft) | falsche Sicherheit | isolierte JVM beibehalten, Produktionspfad testen |
| Config-Migration | Nutzer verliert Einstellungen | Defaults nur für neue Profile, Legacy-Werte erhalten |

---

## 9. Offene Fragen an CORE / SCF / CMB / NET / MOD

| # | Frage | Adressat | Blockiert |
|---|---|---|---|
| Q1 | Bleibt PRE die Paketphase, oder kommt ein echtes Pre-Movement-Event? | CORE | WP1, alle Motion-Writes |
| Q2 | Welche Timer-Priorität gilt? | CORE/SCF | WP2 |
| Q3 | Darf Scaffold den Owner zwischen Platzierungen sichtbar halten? | SCF/CMB | WP4 |
| Q4 | Wird der MoveFix-Default migriert oder nur für neue Profile? | CMB/MOD | WP4 |
| Q5 | Wird `MovementInput` in W02 oder erst W03 instanzbasiert? | CORE | WP6 |
| Q6 | Werden Minemen/Verus repariert oder entfernt? | MOV | WP7 |
| Q7 | Wird BridgeAssist visuell oder als Kamera-Request geführt? | SCF/CMB | WP10 |
| Q8 | Wie wird „neutraler Timer“ für Tickbase definiert? | CORE | WP2/WP8 |
| Q9 | Werden simulierte Schritte pro Client-Tick dedupliziert? | CORE | WP8 |
| Q10 | Welche Key-Defaults bleiben aus Kompatibilitätsgründen? | MOD/SCF | WP3 |

---

## 10. Definition of Done (MOV/CORE)

Ein Arbeitspaket ist fertig, wenn:

1. Die zugehörigen Findings (mindestens die P1) geschlossen oder ausdrücklich mit Begründung zurückgestellt sind.
2. Es **einen** verantwortlichen Writer/Resolver gibt; kein direkter Global-Write außerhalb.
3. Unit-/Vertragstests die Invariante prüfen und ohne echten Server laufen.
4. Die bestehenden MoveFix-/Rotation-/Scaffold-Tests grün bleiben (oder bewusst und dokumentiert angepasst sind).
5. Kein neuer Default still bestehende Configs verändert.
6. Keine Anti-Cheat-/Serverkompatibilitätsbehauptung im Code/Kommentar steht.
7. `git diff` nur die beabsichtigten Dateien enthält; keine Massenformatierung, keine Renames ohne Not.
8. Die Änderung ist Java 8 / MC 1.8.9 konform; keine neuen Bibliotheken.
9. Ein kurzer Debug-Trace (Owner, Lease, Step) vorhanden ist, aber standardmäßig aus.
10. Die Dokumentation (`design/...`) den neuen Vertrag widerspiegelt.

---

## 11. Do-not-do-Liste

- **Kein** neuer Mixin/Hook für „Pre-Movement“, bevor C1 entschieden ist.
- **Kein** direkter `timerSpeed`-, `setSprinting`-, `keyBind*.pressed`- oder Kamera-Write in einem neuen Modul.
- **Kein** `reset("scaffold")` nach einer erfolgreichen Platzierung.
- **Kein** pauschales `timerSpeed = 1.0` in `onDisable`.
- **Kein** rohes C0B aus einem Modul, solange der SprintController existiert.
- **Keine** Änderung der MoveFix-Formel ohne mathematischen Test und ohne neuen Testfall.
- **Keine** stillen Config-Migrationen.
- **Keine** Anti-Cheat-Aussage („unflaggbar“, „bypass“, „serverkompatibel“).
- **Keine** Massenformatierung, keine Renames, keine Dependency-Upgrades.
- **Keine** öffentlichen Server/Accounts in Tests.

---

## 12. Übergabe-Checkliste

- [ ] C1–C5 entschieden und dokumentiert.
- [ ] WP1 (Phase/Suspension) abgenommen.
- [ ] WP4 (Scaffold-Owner) abgenommen; MoveFix-Kontinuitätstest grün.
- [ ] WP2 (Timer) abgenommen; Grep findet keinen direkten Write.
- [ ] WP3 (Sprint/Sneak/Jump) abgenommen; Kombinationsmatrix grün.
- [ ] WP5 (Speed) abgenommen; Sollwerte spezifiziert.
- [ ] WP7/WP10/WP9 abgenommen; Paket-/Rotation-/Lifecycle-Tests grün.
- [ ] WP6/WP8 geplant oder abgeschlossen.
- [ ] Alle P1-Findings geschlossen oder begründet zurückgestellt.
- [ ] Testlauf: gezielte Suite + Gesamtlauf grün.
- [ ] `git status`/`git diff` geprüft; keine fremden Änderungen überschrieben.
- [ ] Keine Server-/AC-Behauptung in Code, Kommentar oder Release-Notes.

---

## 13. Kurzfassung für den CORE-Start

1. **Phase zuerst:** PRE ist Nach-Bewegung. Dokumentieren, testen, nicht umbenennen.
2. **Drei globale Zustände zuerst:** Timer, Sprint, Rotation. Jeweils ein Owner, ein Resolver, ein Test.
3. **Scaffold:** `resetPlan` ≠ `releaseOwner`. Owner bis Disable halten.
4. **Speed:** Formel entscheiden, Potion nicht doppelt, Testmatrix.
5. **Module:** erst nach den Verträgen migrieren; keine neuen direkten Writes.
6. **Nicht** die MoveFix-Formel „reparieren“ — sie ist korrekt. Repariere die Basis, auf der sie arbeitet.
