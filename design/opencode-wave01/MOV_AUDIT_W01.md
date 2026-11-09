# MOV-W01 — Movement-Reaudit (Speed, Sprint, Velocity, MoreKB, NoSlow, JumpReset, WTap2, AutoBridger, BridgeAssist, Scaffold/MoveFix)

**Projekt:** Pulsar-Client / Neutron 2.0
**Welle:** W01 (schreibgeschützter Reaudit)
**Datum:** 2026-10-27
**Branch:** `prep/neutron-opencode-wave00`
**HEAD:** `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`
**Arbeitsbaum:** 147 bereits vorhandene Änderungen (`test_run/`, `.gitignore`, `.vscode/settings.json`) blieben unberührt.
**Technik:** Minecraft 1.8.9, Java 8 (`1.8.0_391`), Maven 3.8.5, offline (`-o`).
**Änderungen dieser Welle:** ausschließlich die drei neuen Dateien unter `design/opencode-wave01/`. Kein Produktivcode, keine Tests, keine Configs, keine Commits, keine Pushes.

---

## 0. Lesehinweis, Evidenzklassen und Prioritäten

Dieser Bericht trennt **Codebefund**, **Testbefund** und **Spielwirkung**. Ein Codepfad ist kein Laufzeitbeweis; ein grüner synthetischer Test ist kein Serverbeweis.

| Klasse | Bedeutung |
|---|---|
| **A — im Code bestätigt (statisch)** | Der Befund ist aus Kontrollfluss, Datenfluss und den angegebenen Zeilen am HEAD direkt ableitbar. |
| **B — selbst ausgeführt (synthetisch)** | Ein vorhandener Test wurde von mir in dieser Welle ausgeführt; das Ergebnis ist unten belegt. |
| **C — Testcode gelesen** | Der Test wurde gelesen, aber nicht als eigener Nachweis für die konkrete Spielwirkung gewertet (auch wenn er im Gesamtlauf enthalten war). |
| **D — älterer Testbericht** | Nur aus vorhandenen Berichten/Dokumenten übernommen, nicht in dieser Welle reproduziert. |
| **E — nicht geprüft** | Keine belastbare Prüfung; ausdrücklich offen. |

**Prioritäten**

- **P1:** benutzersichtbarer Funktionsfehler oder Verstoß gegen eine verbindliche Spec-Regel; ohne Fix ist das jeweilige Modul/der Vertrag nicht verlässlich.
- **P2:** erhebliches Risiko, Inkonsistenz oder fehlende Abdeckung; es gibt einen Workaround, aber der Zustand ist nicht sauber.
- **P3:** Aufräum-, Randfall- oder Benennungsbefund; keine akute Fehlfunktion.

**Widerlegt** heißt: Die Behauptung ist am HEAD durch Code oder Test nicht haltbar. **Nicht geprüft** heißt nicht „widerlegt“.

---

## 1. Ergebnis in Kürze

Die **MoveFix-Mathematik ist nicht defekt**. `MoveFixController.correct` implementiert die erforderliche Inverse `R(cameraYaw − ownerYaw)` mit Wrap, Größen­erhalt und einer dokumentierten 8-Richtungs-Näherung für `EINFACH`. Die vorhandenen Tests (u. a. `MoveFixControllerTest`, `MoveFixProductionChecks`, `NetworkMoveFixIntegrationTest`) bestehen. Die Probleme liegen **nicht in der Formel**, sondern in **Phasenvertrag, Besitz, Lifecycle und konkurrierenden Schreibern**.

Die fünf wichtigsten Befunde:

1. **MOV-W01-001 (P1):** `EventMotion.PRE` ist **nicht vor der Bewegung**, sondern **nach der Bewegung und vor Sprint-/Sneak-Paket und C03**. Das ist der zentrale Architekturvertrag; mehrere Module dokumentieren oder nutzen ihn unterschiedlich.
2. **MOV-W01-002 (P1):** Fast alle Bewegungsmodule haben **keinen GUI-/Fokus-/Pause-Guard**. Im Mehrspieler laufen `Speed`, `Sprint`, `NoSlow`, `Velocity`, `WTap2`, `JumpReset`, `MoreKB`, `BridgeAssist` und `AutoBridger` auch bei geöffnetem Inventar/Chat weiter.
3. **MOV-W01-003 (P1):** `timerSpeed` hat **keinen globalen Eigentümer**. `Scaffold` besitzt eine wertbasierte Lease, `Speed` und `Velocity` schreiben unbedingt `1.0`; ein Moduswechsel kann `1.03` dauerhaft stehen lassen; `Tickbase` verlangt exakt `1.0` und wird dadurch still abgeschaltet.
4. **MOV-W01-004 (P1):** `Scaffold` gibt den **Rotations-Owner nach jeder Platzierung** frei (`releasePlacement` → `reset("scaffold")`), obwohl die Spec 3.1 „bis Disable halten“ verlangt. Dadurch ist MoveFix nach jedem Block für mindestens einen Bewegungsschritt aus; bei 180°-Scaffold ist die Kamerarichtung in diesen Schritten nicht mehr garantiert.
5. **MOV-W01-005 (P1):** `Speed.getModeBaseSpeed()` multipliziert **immer** mit `1.2` und verschiebt die Potion-Stufe; Speed I ergibt `1.4`, Slow I ergibt `1.0`. `Vulcan`/`Vulcan2` skalieren den Amplifier ein zweites Mal. Der vorhandene Test **fixiert** dieses Verhalten, statt es fachlich zu prüfen.

Weitere P1: Sprint-Besitzkonflikt (`Sprint` vs. `WTap2` vs. `Scaffold`), AutoBridger-Sneak-Override und die doppelte G-Taste, BridgeAssist ohne RotationManager. Details in Abschnitt 4.

**Nicht behauptet:** Anti-Cheat-Kompatibilität, „unflaggbar“, Servererfolg, Performance, visuelle Qualität. Es gab keinen Live-Spieltest, keinen echten Server und keinen Account.

---

## 2. Verifizierter Ist-Zustand

### 2.1 Tick- und Phasenmodell (der wichtigste Vertrag)

Aufrufkette für den lokalen Spieler in einem Client-Tick:

```text
Minecraft.runTick
  └─ World.updateEntities
       └─ EntityPlayerSP.onUpdate                         (EntityPlayerSP 168–173)
            └─ Tickbase.runPlayerStep(this, runLocalPlayerStep)   (171)
                 └─ runLocalPlayerStep                    (175–200)
                      ├─ EventUpdate                       (176–177)
                      ├─ super.onUpdate()                 (187)
                      │    └─ EntityLivingBase.onUpdate / onLivingUpdate
                      │         ├─ MovementInput.updatePlayerMoveState()   (868)
                      │         ├─ Scaffold.applyDownwardsInput(...)        (869)
                      │         ├─ SlowdownEvent + input scaling            (872–884)
                      │         ├─ Vanilla-Sprintentscheidung               (892–912)
                      │         └─ moveEntityWithHeading → Entity.moveFlying
                      │              └─ PlayerMoveUpdateEvent + RotationCorrections.movement
                      └─ onUpdateWalkingPlayer            (196, 205–333)
                           ├─ EventMotion PRE             (222–223)
                           ├─ Sprint-C0B-Sync             (230–240)
                           ├─ Sneak-C0B-Sync              (242–251)
                           ├─ RotationManager.resolve     (264)
                           ├─ C03 bauen/senden            (275–299)
                           ├─ RotationManager.finishMotion(304)
                           └─ EventMotion POST            (328–331)
```

**Daraus folgt verbindlich:**

- `EventUpdate` ist **vor** der Bewegung (aber nach dem Input-Update des vorherigen Schritts).
- `EventMotion.PRE` ist **nach** der Bewegung. Es liegt vor dem Sprint-/Sneak-Aktionspaket, vor `resolve`, vor C03 und vor POST.
- `EventMotion.POST` ist **nach** C03 und nach `finishMotion`.
- `Scaffold` mit `Place Timing = Pre` platziert daher **nach** dem Bewegungsschritt, aber **vor** der Rotationsauflösung und vor C03. Es verwendet die **zuletzt akzeptierte** Rotation (`canUseRotation(true)` → `previousAccepted`). Das ist im Code konsistent, aber der Name „Pre“ ist irreführend.
- `Scaffold` mit `Post` platziert nach C03. Der PRE-Request wird in diesem Tick noch akzeptiert, dann gibt POST den Owner frei (siehe MOV-W01-004).
- `Speed`, `Velocity`, `NoSlow`, `WTap2` und `BridgeAssist` schreiben in PRE **nach** der Bewegung. Ihre Motion-Änderungen wirken auf den **nächsten** Bewegungsschritt; die bereits gebaute C03-Position enthält sie nicht. Pakete, die in PRE gesendet werden, liegen jedoch **vor** C03.
- Die Sprint-C0B-Synchronisation liegt **nach** PRE. Deshalb kann `Scaffold.setSprinting(false)` im PRE das Sprintpaket desselben Ticks noch beeinflussen. Genau das bestätigt `SprintOrderingChecks`.

**Beweislage:** A (Code), C (Testcode), B (SprintOrdering ausgeführt). Kein Live-Test (E) für die tatsächliche Serverwahrnehmung.

### 2.2 Bewegungs-Consumer

| Consumer | Einstieg | Wirkung | Beleg |
|---|---|---|---|
| `Entity.moveFlying` | `EntityLivingBase.moveEntityWithHeading` | postet `PlayerMoveUpdateEvent`, danach `RotationCorrections.movement`, dann Vanilla-Acceleration | `Entity.java` 1124–1160; `EntityLivingBase.java` 1620–1690 |
| `EntityLivingBase.jump` | Vanilla-/Modul-Sprung | `JumpFixEvent` + `RotationCorrections.jump` nur bei `isSprinting()`; Impuls `(-sin(yaw), +cos(yaw)) * 0.2` | `EntityLivingBase.java` 1585–1604 |
| `SlowdownEvent` | `EntityPlayerSP.onLivingUpdate` | skaliert `moveStrafe`/`moveForward` beim Item-Use; `allowedSprinting` öffnet das Start-Gate | `EntityPlayerSP.java` 872–884 |
| `KnockbackEvent` | `NetHandlerPlayClient.handleEntityVelocity` | PRE vor `setVelocity`, POST danach; `cancel` verhindert `setVelocity` vollständig | `NetHandlerPlayClient.java` 508–533 |
| `RotationCorrections` | `moveFlying` / `jump` | einziger MoveFix-Consumer; `claimRotationCorrection()` verhindert Doppelanwendung | `RotationCorrections.java` 56–88; `PlayerMoveUpdateEvent.java` 16–21; `JumpFixEvent.java` 12–17 |
| `Tickbase` | `runPlayerStep` | kann den gesamten lokalen Spielerschritt mehrfach ausführen bzw. aussetzen | `Tickbase.java` 83–104 |
| `TowerController` | `Scaffold` PRE | schreibt `motionY` (0.42 / 0.24 / 0.32) | `TowerController.java` 21–40 |

**Wichtig:** `PlayerMoveUpdateEvent.applyMotion` ist **toter Code** (nur Definition, kein Aufrufer). Er würde zudem die **statischen** `MovementInput`-Felder lesen und damit an MoveFix vorbei schreiben. Er ist kein aktiver Consumer, aber eine gefährliche Altlast (MOV-W01-017).

### 2.3 Rotationsbesitz und MoveFix

`RotationManager` ist der einzige bestätigte Besitzermechanismus:

- `beginMotion` setzt `baseYaw/Pitch` auf die letzte effektive Rotation (falls gültig) und `previousAccepted = accepted`, `accepted = false`, `winner = null`, `sequence++` (`57–68`).
- `request`/`requestVisible` sammeln Kandidaten; höchste Priorität gewinnt, bei Gleichstand die lexikografisch kleinere Quelle (`78–83`).
- `resolve` schreibt die Gewinner-Rotation in `EventMotion`; sichtbare Requests addieren die Differenz zur aktuellen Kamera und verlangen u. a. `currentScreen == null`, `inGameHasFocus`, `renderViewEntity == thePlayer` und keinen bestätigten Aura-/Scaffold-Besitzer (`85–100`).
- Der **Outbound-Pfad** ruft `observeOutboundRotation` für jedes rotierende C03 auf (`NetworkManager.java` 176; `NetHandlerPlayClient.java` 877). Dadurch ist `getEffectiveYaw()` beim späteren `finishMotion` bereits die tatsächlich gesendete Rotation; der Test `NetworkMoveFixIntegrationTest.accept` bildet genau das nach (`30–33`).
- `finishMotion` akzeptiert nur endliche, plausible Winkel und setzt den Owner nur, wenn der Gewinner exakt der gesendeten Rotation entspricht (`102–117`). Das ist ein **sauberer** Vertrag; die Tests bestehen.
- `canUseRotation(pre)` nutzt `hasEffective && (pre ? previousAccepted : accepted)` (`130–134`). **Es prüft nicht den Owner.** `RotationCorrections.select` prüft dagegen `source.equals(rotation.getConfirmedOwner())` (`28–32`). Diese beiden Begriffe sind nach einem `reset("scaffold")` **nicht deckungsgleich**: `reset(String)` entfernt den Owner, lässt aber `hasEffective`/`accepted`/`previousAccepted` stehen (`297–304`). Das ist die mechanische Grundlage von MOV-W01-004.
- `RotationCorrections.current()` verlangt zusätzlich `mc.currentScreen == null`, `inGameHasFocus`, `renderViewEntity == thePlayer`, lebend, nicht reitend, offene Verbindung (`34–38`). MoveFix ist damit in GUI/Third-Person/Freecam **aus**; die direkten Speed-Schreiber umgehen diese Prüfung jedoch teilweise (siehe 4.2).

**MoveFix-Formel (verifiziert, nicht defekt):**

Für Input `(s, f)`, Kamera-Yaw `y_c` und akzeptierten Owner-Yaw `y_o` gilt in `Entity.moveFlying`:

```text
motionX += a * (s*cos(y) − f*sin(y)) / max(1, hypot(s,f))
motionZ += a * (s*sin(y) + f*cos(y)) / max(1, hypot(s,f))
```

Setzt man `Δ = y_c − y_o` und `(s', f') = R(Δ)(s, f)`, dann ist `R(y_o)(s', f') = R(y_c)(s, f)`. `MoveFixController.correct` berechnet genau diese Rotation (`28–48`), erhält die Magnitude (`46–47`) und setzt anschließend `event.setYaw(y_o)` (`RotationCorrections.java` 75–79). `SIMPLE` ersetzt `R(Δ)` durch die beste der acht Richtungen und skaliert auf die Ausgangsmagnitude; der maximale Richtungsfehler ist 22,5°. `jumpYaw` übernimmt für `PRECISE`/`OFF` die Kamera, für `ROTATION_BOUND` den Owner und für `SIMPLE` die Oktanten-Näherung (`50–56`). Das ist konsistent mit `design/movefix-verification.md` und mit den bestandenen Tests.

**Beweislage:** A (Code), B (`MoveFixControllerTest` 7, `MoveFixProductionTest` 1, `NetworkMoveFixIntegrationTest` 6, `RotationCorrectionsTest` 6, `RotationBehaviorHookTest` 3, `MovementHelperContractTest` 5). **Kein** Live-Server-Test (E).

### 2.4 Timer

`Timer.timerSpeed` ist ein öffentliches Feld; `Timer.updateTimer` akzeptiert jeden endlichen Wert `>= 0` und clampt nur die **elapsed ticks**, nicht den Speed (`Timer.java` 96–114). Es gibt **keinen** zentralen Timer-Owner.

Schreiber am HEAD:

| Schreiber | Stellen | Werte |
|---|---|---|
| `Scaffold.TimerControl` | `Scaffold.java` 74–91, 134, 164–165 | Wunschwert 0.2–3.0; Lease mit `before`/`written`/`owned`/`blocked` |
| `Speed` | 105, 127, 134, 145, 154, 181, 235 | `1.0`; Polar zusätzlich `1.03` |
| `Velocity` | 62, 165–171 | `1.0`; Ground Grim Rampe `0.8 … 1.0` |

`Scaffold.TimerControl.apply` erkennt einen Fremdschreiber nur, wenn `current != written` (`79`). Schreibt ein anderes Modul **denselben** Wert (typisch `1.0`), ist das nicht erkennbar. `release` stellt `before` nur wieder her, wenn `current == written` (`86–90`). `Speed`/`Velocity` schreiben bei `onDisable` unbedingt `1.0` und überschreiben damit eine fremde Lease. `Tickbase.usable()` verlangt `|timerSpeed − 1| < 0.001` (`130`); jede Speed-Polar-Phase (`1.03`) oder Scaffold-Lease macht Tickbase still unbrauchbar.

**Beweislage:** A. Kein Test deckt die Interleavings ab; `TickbaseBudgetTest` testet nur den Budgetkern, nicht die Timer-Kollision.

### 2.5 MovementInput

`MovementInput.moveStrafe` und `moveForward` sind **statisch** (`MovementInput.java` 8, 13). Das weicht von Vanilla 1.8.9 ab (dort Instanzfelder). `MovementInputFromOptions.updatePlayerMoveState` schreibt über `this` in diese statischen Felder (`16–37`); `EntityPlayerSP` liest sie über `this.movementInput.moveForward` (`867`, `892`, `904`, `909`); `Scaffold.suppressSneak` schreibt sie direkt (`208–213`); der tote `PlayerMoveUpdateEvent.applyMotion` liest sie (`32`). Es gibt nur einen lokalen Spieler, deshalb funktioniert der Einzelspielerfall; die Felder sind aber **prozessweit** und nicht pro Instanz, sie überleben Weltwechsel und machen synthetische/mehrere Input-Instanzen unsicher. Die Tests setzen sie teilweise explizit zurück (z. B. `MovementHelperContractTest`), was den Sonderstatus bestätigt.

**Beweislage:** A; kein Laufzeitfehler im Einzelspieler reproduziert (E für konkrete Spielwirkung).

### 2.6 Sprint- und Paketpfad

- Vanilla entscheidet Sprint in `onLivingUpdate` (`892–912`): Start nur bei `moveForward >= 0.8`, Food, kein Item-Use (außer `noslowAllowSprint`), keine Blindness und Sprint-Taste bzw. Toggle-Timer; Stop bei `moveForward < 0.8`, Kollision oder Food.
- `EntityPlayerSP.onUpdateWalkingPlayer` synchronisiert `serverSprintState` mit `isSprinting()` **nach** PRE (`230–240`) und sendet genau bei Zustandswechsel ein C0B. Ein PRE-Cancel verhindert diese Synchronisation nicht (Kommentar 230; `finishMotion(false)` bei 261).
- `Sprint` setzt `setSprinting(true)` in `EventUpdate` (`37–43`), also **vor** der Vanilla-Entscheidung. Vanilla kann es im selben Tick wieder zurücksetzen, wenn die Bedingungen nicht passen.
- `WTap2.Legit` setzt `keyBindSprint.pressed = false` und `keyBindForward.pressed = false` (`70–75`, `143–147`); `WTap2.Spoof` ruft `setSprinting(false)` (`120–129`), was über den Sync ein **echtes** C0B-STOP erzeugt, kein reines „Spoof“.
- `Scaffold` setzt bei gültigem Plan `setSprinting(false)` in PRE (`157`), also **nach** `Sprint` und nach der Vanilla-Entscheidung, aber **vor** dem C0B-Sync. Das ist die einzige Stelle, die Sprint zuverlässig vor dem Paket unterdrückt.
- `MoreKB.Packet` sendet C0B STOP/START direkt über `PacketUtils` (`47–49`) und umgeht damit `serverSprintState`; der Cache kann danach falsch stehen.

**Beweislage:** A; `SprintOrderingChecks` (B) bestätigt die Unterdrückung durch Scaffold für den getesteten Fall; kein Live-Server-Test (E).

---

## 3. Modul- und Modusmatrix

### 3.1 Speed

Default-Modus: **Intave** (Index 1, `Speed.java` 19). Key `C` (28). `Air Speed` default aus (20). Kein `onValueChange`.

Gemeinsame Basis (`210–215`):

```text
base = sneak ? 0.0663 : (sprinting ? 0.2873 : 0.221)
activeSpeed     = Speed     ? (level + 1) : 0
activeSlowness  = Slowness  ? (level + 1) : 0
amplifier       = activeSpeed − activeSlowness
return base * (1.0 + 0.2 * (amplifier + 1))
```

Werte: kein Potion `amplifier = 0` → `1.2`; Speed I (Level 0) `1` → `1.4`; Speed II `2` → `1.6`; Slowness I `−1` → `1.0`; Slowness II `−2` → `0.8`.

`getSpeedAmplifier()` liefert dagegen nur den **rohen** Speed-Level (`217–220`); `Vulcan`/`Vulcan2` multiplizieren damit zusätzlich.

| Modus | Boden | Luft | Timer | Relevanter State | Test | Risiko |
|---|---|---|---|---|---|---|
| **BMC** | `MoveUtil.strafe(base * 1.08)`; Sprung bei `groundTicks % 2 == 0` | `speedInAir = 0.03/0.02`; ab 4 Luftticks `×1.003` (nur mit Air Speed) | schreibt `1.0` (105) | `groundTicks`, `offGroundTicks` | Matrix-Test (B) | Timer-Kollision; Parität überlebt Input-Pausen; `speedInAir` bleibt bei Moduswechsel |
| **Intave** | `jump()`; nur wenn `isSprinting()`: `max(0.29, base)` | `×1.0015` wenn Air Speed, `motionY > 0`, sprintend | schreibt **nicht** | `offGroundTicks` | Matrix-Test (B) | hängt an `isSprinting()`; Scaffold kann Sprint vorher unterdrücken → kein Boost; `0.29`-Floor |
| **Polar** | `jump()`; `strafe(base * 1.18)` | erster Lufttick `max(horizontalSpeed * 0.98, base)`; Timer `1.03` bei Luftticks 3–6 | schreibt `1.0`/`1.03` (127, 134, 145) | `polarAirTicks` | Matrix-Test (B) | `1.03` kann nach Moduswechsel stehen bleiben; Tickbase wird unbrauchbar |
| **Vulcan** | `jump()`; `strafe(base * (1.30 + amp * 0.10))` | erster Lufttick `max(horizontalSpeed * 0.92, base * 1.02)`; ab 4 Luftticks `motionY -= 0.03` bei `fallDistance < 1` | schreibt `1.0` (154) | `offGroundTicks` | Matrix-Test (B) | doppelte Potion-Skalierung; Fast-Fall verändert Flugbahn |
| **Vulcan2** | `jump()`; `base * (1.34 + amp * 0.08)`, −0.01 bei Strafe | erster Lufttick `motionY = −0.13`; mit Air Speed bis Tick 2 `max(horizontalSpeed * 0.62, base)` | schreibt `1.0` (181) | `offGroundTicks`, `vulcan2DropApplied` | Matrix-Test (B) | harter Drop; doppelte Potion-Skalierung; `keyBindJump` blockiert |

**Beobachtungen:**

- Die Bodenwerte sind **nicht** Vanilla: `BMC` ist `base * 1.08`, `Polar` `*1.18`, `Vulcan` `*1.30`, `Vulcan2` `*1.34`, jeweils auf einer bereits `×1.2`-Basis. Das ist als Modultuning lesbar, aber die Potion-Stufe ist doppelt und der `+1`-Offset verschiebt jede Stufe (MOV-W01-005).
- `isSprinting()` ist ein **mutable Fremdzustand**: `Scaffold` setzt es in PRE (`157`), `WTap2`/`Sprint` ändern es. Wenn Speed nach Scaffold läuft, nutzt es den Walk-Basiswert; wenn Speed vor Scaffold läuft, den Sprint-Basiswert. Die Listener-Reihenfolge ist nicht fachlich festgelegt (keine Prioritäts-API; sie folgt der Subscribe-/Enable-Reihenfolge). Das ist MOV-W01-006/005.
- `Speed` schreibt `speedInAir` nur in BMC (`118`); ein Wechsel zu Intave/Polar/Vulcan lässt den Wert bis zum nächsten Reset-Pfad stehen. `resetMovementOverrides` setzt ihn auf `0.02` (`237`), aber nur bei No-Input, Liquid/Ladder oder Disable.
- `Speed` schreibt `timerSpeed` in fast allen Modi unbedingt `1.0`; das ist MOV-W01-003.
- `resetMovementOverrides` schreibt `keyBindJump.pressed` aus `Keyboard.isKeyDown` (`234`) und `jumpMovementFactor = 0.02` (`236`). Das kann einen synthetischen Jump eines anderen Moduls löschen und ist für Speed funktional nicht nötig (MOV-W01-017).
- Kein `WorldEvent`/Death-Reset: `groundTicks`, `offGroundTicks`, `polarAirTicks`, `vulcan2DropApplied` überleben Welt-/Respawn-Wechsel (MOV-W01-013).

**Testabdeckung:** `SpeedMovementBasisTest` startet `SpeedMovementBasisChecks` in einem Kind-JVM; der Test prüft Richtung und Basis für alle fünf Modi, beide Owner und die Inputs −1/0/1. Er prüft **nicht** Timer, GUI, Potion-Stufen, Moduswechsel, Weltwechsel oder Kollisionen. Der Test besteht (B), fixiert aber die `1.2`-Basis (Zeile ~103).

### 3.2 Sprint

- Listener: `EventUpdate` (`37–43`); Bedingungen: nicht horizontal kollidierend, Bewegungsinput, `moveForward >= 0`, kein Item-Use, nicht sneaking. Dann `setSprinting(true)`.
- Kein GUI-/Fokus-/Pause-Guard, kein Food-/Blindness-Check, kein Paketbesitz. Vanilla kann im selben Tick zurücksetzen; `Scaffold` kann in PRE unterdrücken.
- `onDisable` setzt nichts zurück; Sprint bleibt bis zur nächsten Vanilla-Entscheidung gesetzt.
- **Konflikt:** `WTap2.Legit` setzt Sprint im AttackEvent auf false; `Sprint` setzt es im selben Tick (EventUpdate liegt nach dem Angriff) wieder auf true. Damit ist WTaps Kernfunktion bei aktivem Sprint-Modul wirkungslos (MOV-W01-006).
- Test: `SprintOrderingTest`/`SprintOrderingChecks` (B) prüft Sprint-Pakete mit Scaffold und gehaltener Taste; kein Test für Sprint+WTap.

### 3.3 Velocity

Default-Modus: **Packet** (Index 1, `Velocity.java` 33). Key `0` (50). `Jump Chance` default 60 und **gated alle Modi** (`102–104`), nicht nur „Jump“.

| Modus | Auslöser | Wirkung | Status |
|---|---|---|---|
| **Jump** | `KnockbackEvent` PRE, am Boden | `jump()` | aktiv, aber 40 % Bypass durch Chance |
| **Packet** | `KnockbackEvent` PRE | `x/z *= horizontal/100`, `y *= vertical/100`; kein Cancel | Default 100 % = **No-op**; nur Skalierung |
| **Verus** | `EventMotion` PRE | `motionX/Y/Z = 0`, wenn `hurtTime == 10 − random(3,4)` | Bedingung praktisch unerreichbar (siehe unten) |
| **Ground Grim** | `KnockbackEvent` PRE | bei Input + Boden `cancel`; setzt `reset` | `checkAir` sendet C03/C07 und `setBlockToAir`; Timer-Rampe 0.8–1.0 |
| **Minemen** | `KnockbackEvent` PRE | `ticks > 14` → `adjustKnockBack`, cancel, `ticks = 0` | **nie erreichbar**: `ticks` wird nur auf 2 gesetzt, nirgends erhöht |
| **Intave** | `KnockbackEvent` PRE | `reset = true` | `onMotion` PRE: bei `hurtTime == 2`, Boden, geradem `intaveTick` → `jump()` |
| **Intave2** | `AttackEvent` + `KnockbackEvent` POST | Angriff mit `hurtTime > 0`: Motion ×0.6; POST: optional Jump, `reduced = false` | nutzt `noAction()` (Speed-/Jump-Potion-Bypass) |

**Mathematische Befunde:**

- `Minemen`: `adjustKnockBack(e, 1/42000, 1/8000)` teilt in `233–237` **nochmals** durch 100 → Faktor `1/4.200.000` horizontal, `1/800.000` vertikal. Der Zweig wird wegen `ticks` nie betreten; selbst wenn, wäre die Wirkung praktisch null. Zusätzlich `setCancelled(true)` → `NetHandler` kehrt vor `setVelocity` zurück (`522–524`), die skalierten Werte wären also ohnehin ungenutzt.
- `Verus`: `MathUtils.getRandomFloat(max, min)` ist als `(max, min)` implementiert (`nextFloat() * (max − min) + min`). Der Aufruf `getRandomFloat(3, 4)` liefert `(3, 4]`; `10 -` ergibt `[6, 7)`. `hurtTime` ist `int`; der Vergleich mit einem `float` wird nur bei exakt `6.0` oder `7.0` wahr. `6.0` ist nur bei `nextFloat() == 0` möglich (ca. 6 × 10⁻⁸), `7.0` ist unmöglich (`nextFloat() < 1`). Der Modus ist praktisch tot. **A + Rechnung; nicht im Spiel reproduziert.**
- `jumpChance` wird vor dem Modus-Switch geprüft (`102–104`). Bei Default 60 werden 40 % aller Knockbacks **unverändert** durchgelassen, auch bei Packet/Ground Grim/Minemen. Der Name „Jump Chance“ beschreibt nur einen Teil der Wirkung.
- `onDisable` setzt `timerSpeed = 1.0` (`62`) und überschreibt eine fremde Lease; `onEnable` setzt keinen Timer zurück. `Ground Grim` schreibt `timerSpeed` in `165–171`.
- `checkAir` (`210–226`) verändert die Client-Welt (`setBlockToAir`) und sendet `C03`/`C07`; das ist mehr als ein Velocity-Modus und kann mit Scaffold/AntiCheat/Netzwerk interagieren. Nicht als „falsch“ bewiesen, aber außerhalb des Modulnamens.
- Toter Code: `adjustPlayerMovement` (`229–231`), `isValidMotion` (`245–247`), `adjustKnockBack` (nur im unerreichbaren Minemen-Zweig).
- Kein GUI-Guard; nur `PlayerUtil.inGame()` (`98`, `184`) prüft Spieler/Welt, nicht den Screen.

**Testabdeckung:** **keine** Velocity-Tests (weder Unit noch Integration). Die Modusmatrix ist ungetestet. Das ist eine der größten Lücken.

### 3.4 NoSlow

Default-Modus: **Vanilla** (Index 0, `NoSlow.java` 29). `Reduce X`/`Reduce Z` default 100.

`SlowdownEvent` wird nur gepostet, wenn `isUsingItem()` (`EntityPlayerSP` 872). `shouldHandleSlowdown()` verlangt zusätzlich ein unterstütztes Item (Schwert, Bogen, Food, Potion; `144–159`).

| Modus | `forward`/`strafe` | Zusatzpakete | Bewertung |
|---|---|---|---|
| **Vanilla** (Default) | `1.0/1.0`, `allowedSprinting = true` | keine | **entfernt den Vanilla-Slowdown vollständig**; der Name ist irreführend |
| **Intave** | `1.0/1.0` | Food/Potion: `releaseUse()` einmal beim ersten Bewegungs-PRE; Schwert: `C08 placeUse` **jeden** PRE-Tick | Paketspam; Skalierung identisch zu Vanilla |
| **Vulcan** | `0.92/0.92` | keine | einziger Modus mit echter Teil-Skalierung |
| **Reduced** | `forward = reduceX`, `strafe = reduceZ` | keine | X/Z-Zuordnung wirkt vertauscht (X ist üblicherweise Strafe, Z Forward); Entscheidung nötig |
| **Polar** | `1.0/1.0` | Schwert blockend: PRE `releaseUse`, POST `placeUse` | Skalierung identisch; Paketspam |
| **Item Switch** | `1.0/1.0` | Schwert blockend: zwei `C09` (nächster/aktueller Slot) jeden PRE-Tick | Skalierung identisch; Paketspam |

**Wichtig:** Die lokale Verlangsamung wird **nicht** durch die Pakete, sondern ausschließlich durch `SlowdownEvent.setForward/Strafe` beeinflusst. Die Modi „Intave“, „Polar“ und „Item Switch“ ändern die Skalierung daher **nicht** gegenüber „Vanilla“; sie fügen nur Pakete hinzu. Nur „Reduced“ und „Vulcan“ unterscheiden sich. Das ist eine fachliche Mode-Überzeichnung (MOV-W01-011).

**Testabdeckung:** `NoSlowSprintTest`/`NoSlowSprintChecks` (B) prüft die Sprint-Erlaubnis und die Skalierung für „Vanilla“; die Paketmodi und die X/Z-Zuordnung sind nicht abgedeckt.

### 3.5 WTap2

Default-Modus: **Legit** (Index 0, `WTap2.java` 24). Key `0`.

| Modus | Auslöser | Wirkung | Risiko |
|---|---|---|---|
| **Legit** | `AttackEvent` und `TickEvent` PRE | bei Ziel, `hurtTime > 0`, Boden, sprintend: `keyBindSprint.pressed = false`, `keyBindForward.pressed = false`; Restore im nächsten Tick aus `Keyboard.isKeyDown` | Sprint-Modul kann es im selben Tick rückgängig machen; GUI-/Fokusproblem; fremde Key-Schreiber |
| **Spoof** | `EventMotion` PRE (nur PRE) | bei Ziel, `hurtTime > 0`, Boden, sprintend: `setSprinting(false)`; Resume nur bei gedrückter Sprinttaste + Input | **kein reiner Spoof**: `setSprinting` erzeugt ein echtes C0B-STOP über den Sync (`EntityPlayerSP` 230–240) |
| **STap** | `AttackEvent` oder linker Mausklick als Edge | 140 ms Fenster: `keyBindForward` false, `keyBindBack` true; danach Restore aus physischer Taste; Cooldown 260 ms; Distanz 1.2–3.6 | synthetischer Rückwärts-Input; läuft ohne GUI-Guard; `System.currentTimeMillis`-Fenster überlebt Weltwechsel |

- `onDisable` stellt Forward/Back/Sprint aus `Keyboard.isKeyDown` wieder her (`41–52`). Das ist besser als nichts, aber es kann einen fremden synthetischen Zustand überschreiben und startet Sprint auch ohne Bewegungsinput.
- `resolveTarget()` nutzt `CombatTargetTracker` (350 ms) oder `objectMouseOver`; kein Welt-/Alive-Check des Ziels außer in `canActivate`.
- `STap` nutzt `KeyBinding.setKeyBindState(keyCode, …)`; das setzt **alle** Bindings mit demselben Keycode, nicht nur Forward/Back. Bei Kollisionen ist die Wirkung breiter als beabsichtigt.
- Kein Test für WTap2 (weder Unit noch Integration). **C/E.**

### 3.6 MoreKB

Default-Modus: **Packet** (Index 1, `MoreKB.java` 22). `Only Ground` default an.

- `AttackEvent` setzt `target` und `ticks = 2` (`29–34`).
- `EventUpdate` (vor der Bewegung): wenn `target != null` und Bewegungsinput und (`onlyGround` → Boden):
  - **Legit Fast**: `sprintingTicksLeft = 0` (`43`). Das führt beim nächsten `onLivingUpdate` zu `setSprinting(false)` (`EntityPlayerSP` 795–805), aber der Server-Sync läuft erst in `onUpdateWalkingPlayer`.
  - **Packet**: sendet rohe `C0B STOP` + `C0B START` (`47–49`) über `PacketUtils`; `serverSprintState` bleibt unverändert. Der nächste Vanilla-Sync kann dadurch eine Änderung verpassen.
  - Danach `target = null` (`53`); ohne Bewegungsinput bleibt der Target stehen und feuert später (stale target).
- `ticks` wird nirgends verwendet; `Pulsar.debugMessage` schreibt bei jedem Angriff/Tick ins Chat/Log (`44`, `48`). Kein GUI-Guard. Kein Test.
- **Wirkung:** Der Modus ist ein Sprint-Reset, kein „mehr KB“ im Sinne einer KB-Skalierung. Der Name ist irreführend; die Paketvariante kann den Sprint-Cache desynchronisieren (MOV-W01-010).

### 3.7 JumpReset

- `EventPacket` PRE/inbound; bei `S12PacketEntityVelocity` für den lokalen Spieler und `onGround` → `jump()` (`35–58`).
- `jumpChance` default 60; `Math.random` pro Paket. Kein Cooldown, kein GUI-Guard, kein Welt-/Alive-Check außer `thePlayer != null`.
- **Konflikt:** `Velocity.Jump` springt ebenfalls auf `KnockbackEvent` (PRE), `Speed` springt in PRE, `Scaffold.Auto Jump` springt in PRE. Mehrere `jump()`-Aufrufe im selben Tick addieren sich **nicht** (jeder setzt `motionY = 0.42`), aber der letzte gewinnt; `isAirBorne`/`fallDistance` werden mehrfach gesetzt. Meist redundant, nicht katastrophal.
- Kein Test. **C/E.**

### 3.8 AutoBridger

- `EventUpdate`: `keyBindSneak.pressed = playerOverAir()` (`17–20`). Das ist eine **direkte, bedingungslose** Überschreibung des physischen Sneak-Zustands, jeden Tick.
- `onEnable` setzt `pressed = false` (`29`); `onDisable` setzt `pressed = false` (`35`). Wer Shift hält, verliert den Sneak logisch, bis ein echtes Key-Event den Zustand wieder setzt.
- Key `G` (`23`) kollidiert mit `Scaffold` (`Scaffold` 94). `ModuleManager.toggleByKey` toggelt **alle** Module mit diesem Key (`66–84`) → G schaltet Scaffold **und** AutoBridger.
- Kein GUI-Guard; `playerOverAir` dereferenziert `thePlayer/theWorld`, was nur während eines Spieler-Ticks gilt.
- **Bestätigt SCF-001/SCF-002** (P1). Kein Test.

### 3.9 BridgeAssist

- `EventMotion`-Listener ohne `isPre()`/`isPost()`-Prüfung (`61–93`). Der Produzent postet PRE **und** POST (`EntityPlayerSP` 222/330) → `performGlide()` kann **zweimal pro Tick** laufen. Das verdoppelt die Annäherungsgeschwindigkeit (`SCF-003`, P1).
- Schreibt `thePlayer.rotationYaw/rotationPitch` direkt (`102–111`) und umgeht `RotationManager` vollständig. Damit ist die Rotation für MoveFix nicht als Owner sichtbar; sie kann mit Aura/Scaffold konkurrieren und die Kamera sichtbar drehen (`SCF-004`, P1 für Arbitration).
- `performGlide` nutzt lineare Differenzen ohne `wrapAngleTo180` (`100–111`); die Ziel-Arrays enthalten ±360, aber die Bereichsprüfung `targetYaw >= currentYaw - range && <= currentYaw + range` (`156–162`) behandelt den ±180-Sprung nicht. Nahe der Naht wird die Ausrichtung übersprungen (P3).
- Keine Null-/GUI-/Fokus-Prüfung; `Minecraft.getMinecraft().thePlayer` wird ohne Guard dereferenziert (`64`, `69`, `97`). Ein Weltwechsel während eines Glides kann auf den neuen Spieler wirken (P2/P3).
- `onSneak` prüft `thePlayer.isSneaking()`, also den Entity-Zustand, nicht die physische Taste. Bei AutoBridger/Downwards kann das anders sein als erwartet.
- `aimAt` ist public und ohne Validierung; `glideTime = 1` ergibt Faktor 50 und damit einen Sprung.
- Kein Test. **C/E.**

### 3.10 Scaffold (nur Bewegungsanteile)

Scaffold ist primär SCF, wird hier nur für Bewegung/Timer/Rotation geprüft.

| Aspekt | Ist | Beleg |
|---|---|---|
| `Place Timing` | Default `Pre` | 40 |
| `Movefix` | Default **Aus** (Index 0) | 42; `MoveFixValue` 13 |
| `Rotations` | Default an | 41 |
| `Sprint` | Default aus → PRE `setSprinting(false)` bei gültigem Plan | 52, 157 |
| `Timer` | Default 1.0; 0.2–3.0; `TimerControl` | 50, 74–91, 164–165 |
| `Downwards` | Default aus; `applyDownwardsInput` vor der Bewegung, `suppressSneak` teilt durch 0.3 und setzt `input.sneak = false` | 54, 201–214, `EntityPlayerSP` 869 |
| `Auto Jump` | Default aus; PRE `jump()` bei Boden/Bewegung/ohne Sprungtaste | 58, 158 |
| `Tower` | Default aus; `TowerController` schreibt `motionY` in PRE | 59–60, 159, `TowerController` 21–40 |
| `Rotationsfreigabe` | `releasePlacement()` bei jedem Planende/Erfolg/Settingwechsel/ungültigem Ziel; `reset("scaffold")` | 98, 127–135, 154, 161–163, 171, 175–176, 183–184, 196 |
| `MoveFix-Basis` | `canUseRotation(true)` nutzt `previousAccepted`, nicht den Owner | `RotationManager` 130-134; `ScaffoldPlacementService` 217 |

**Kernproblem:** `releasePlacement` ist ein **gemeinsamer** Reset für Plan, Rotation, Timer, Tower und Downwards. Es wird nicht zwischen „Plan abgeschlossen“ und „Owner freigeben“ unterschieden. Die Spec 3.1 verlangt genau diese Trennung. Zusätzlich bleibt nach `reset("scaffold")` die effektive Rotation als Platzierungsbasis erhalten (`hasEffective`/`previousAccepted`), während `RotationCorrections.select` den Owner verlangt. Scaffold kann also mit der alten Basis weiter platzieren, während MoveFix für den Bewegungsschritt aus ist.

**Testabdeckung:** `ScaffoldClientFlowTest`, `ScaffoldPlacementTest`, `ScaffoldNetworkIntegrationTest`, `LegacyScaffoldTest` (B im Gesamtlauf) prüfen Planung, Slots, Delay, Weltwechsel und Pipeline. **Kein** Test fordert den Rotations-Owner über eine Platzierung hinweg oder prüft MoveFix-Kontinuität nach `releasePlacement`. Das ist die zentrale Testlücke zu MOV-W01-004.

### 3.11 Weitere Schreiber (Abgrenzung)

- **Aura**: kein direkter `motion`-/`timer`-Schreiber; nutzt `RotationManager` (Priorität 20) und `RotationCorrections`. `TickEvent` verwirft ein verlorenes Ziel vor der Bewegung (`Aura` 121–131), damit MoveFix nicht auf ein altes Ziel zeigt. Korrekt im Rahmen des Owner-Vertrags.
- **TowerController**: `Scaffold`-Hilfsklasse; schreibt `motionY` (`21–40`), abhängig von `jumpPressed`, `moving`, `hasTarget`, `onGround`, `mode`. Kein MoveFix, keine eigene Rotation. Kann mit Speed/JumpReset kollidieren.
- **Tickbase**: schreibt keine Bewegung direkt, führt den lokalen Schritt aber mehrfach/ausgesetzt aus (`84–104`). `usable()` verlangt Timer 1.0 (`130`), GUI zu, Fokus, lebend, nicht reitend, `hurtTime <= 2`.
- **PlayerMoveUpdateEvent.applyMotion**: toter Code; würde statische Inputs lesen und Motion setzen. Nicht aktiv.
- **Movefix-Event** (`event/motion/Movefix.java`): wird nirgends gepostet; tot.

---

## 4. Findings

### 4.1 P1

#### MOV-W01-001 — „PRE“ ist Nach-Bewegung, nicht Vor-Bewegung

- **Status:** A (Code), C (Testcode), E (Spielwirkung).
- **Priorität:** P1 (Architekturvertrag; betrifft alle Bewegungsmodule und Scaffold-Timing).
- **Beleg:** `EntityPlayerSP.java` 171, 175–197, 222–223, 230–251, 264–304, 328–331; `Scaffold.java` 148–185; `SprintOrderingChecks` (B).
- **Ist:** `super.onUpdate()` (187) führt Input, Sprintentscheidung und `moveEntityWithHeading`/`moveFlying` aus. Erst danach läuft `onUpdateWalkingPlayer` mit PRE (222). PRE liegt vor Sprint-/Sneak-C0B, `resolve`, C03 und POST. POST ist nach C03.
- **Auswirkung:** Motion-Änderungen in PRE (Speed, Velocity, NoSlow, WTap2, BridgeAssist) wirken auf den **nächsten** Bewegungsschritt, nicht auf die bereits berechnete C03-Position. Wer PRE als „vor der Bewegung“ behandelt, baut falsche Annahmen (insbesondere bei „Velocity im selben Tick“). Scaffold-„Pre“ platziert nach der Bewegung und nutzt die vorherige akzeptierte Rotation; das ist korrekt, aber nicht selbsterklärend. Die Sprintunterdrückung funktioniert nur, weil der C0B-Sync **nach** PRE liegt.
- **Ursache:** Die Eventnamen beschreiben den Paketzyklus, nicht den Bewegungszyklus; es gibt keinen zentralen `PhaseClock`/Kommentarvertrag an den Modulen.
- **Minimal:** Keine Verhaltensänderung in W01. Dokumentation an `EventMotion` und in jedem Modul; ein zentraler Test, der die Reihenfolge (Input → Bewegung → PRE → Sprint-C0B → C03 → POST) festnagelt.
- **Akzeptanz:** Ein synthetischer Tick-Test protokolliert die Reihenfolge und schlägt fehl, wenn ein Modul Motion vor der Bewegung erwartet; `SprintOrderingChecks` bleibt grün.
- **Owner:** CORE/MOV. **Abhängig:** MOV-W01-004, -006, -014.

#### MOV-W01-002 — Kein GUI-/Fokus-/Pause-Guard in den Bewegungsmodulen

- **Status:** A (Code), E (Live).
- **Priorität:** P1 (Verstoß gegen die Suspension-Regel; im Mehrspieler benutzersichtbar).
- **Beleg:** `EntityPlayerSP.walkingContextValid` (335–340) prüft nur Client/Welt/Connection/Pipeline, **nicht** `currentScreen`/`inGameHasFocus`. `EventUpdate` wird in `runLocalPlayerStep` (176–177) gepostet, `EventMotion` PRE in `onUpdateWalkingPlayer` (222). Keine Guards in: `Speed` 45–48; `Sprint` 37–43; `NoSlow` 72–117; `Velocity` 96–104/181–186; `WTap2` 55–99/101–131; `JumpReset` 35–58; `MoreKB` 37–54; `BridgeAssist` 61–93; `AutoBridger` 17–20. Dagegen: `Scaffold.validContext` 115–118, `RotationCorrections.current` 34–38, `Tickbase.usable` 123–130, `RotationManager.resolve` 89–91.
- **Auswirkung:** Im Mehrspieler laufen diese Module bei geöffnetem Inventar/Chat weiter (Singleplayer pausiert). `Speed` bhop/strafe, `BridgeAssist` dreht die Kamera, `WTap2` drückt Forward/Back, `AutoBridger` setzt Sneak, `JumpReset`/`Velocity` springen. Das ist synthetische Eingabe während einer Suspension und kann Movement/Rotation/Pakete verändern, obwohl der Nutzer nichts steuert.
- **Ursache:** Kein gemeinsamer `SuspensionGate`; jeder Modulautor hat nur `thePlayer != null` geprüft.
- **Minimal:** Zentraler Guard (eine Methode/Utility) in jedem Bewegungslistener; `EventMotion`/`EventUpdate`-Producer könnten bei GUI optional gar nicht publizieren, aber das würde RotationManager/Scaffold-Lifecycle beeinflussen — daher besser pro Modul prüfen.
- **Akzeptanz:** Synthetischer Test setzt `currentScreen != null` bzw. `inGameHasFocus = false`; kein Modul darf Motion, Keys, Rotation oder Sprint ändern; Paketpuffer bleibt leer (außer erlaubter Lifecycle-Pakete).
- **Owner:** CORE/MOV. **Abhängig:** MOV-W01-007, -008.

#### MOV-W01-003 — `timerSpeed` ohne globalen Eigentümer

- **Status:** A (Code); C (Tickbase-Test); E (Interleaving).
- **Priorität:** P1 (fremde Zustände werden überschrieben; Tickbase wird still deaktiviert).
- **Beleg:** `Scaffold.TimerControl` 74–91; `Scaffold` 134, 164–165; `Speed` 105, 127, 134, 145, 154, 181, 235; `Velocity` 62, 165–171; `Timer` 96–114; `Tickbase` 130.
- **Ist:** Drei Module schreiben `mc.timer.timerSpeed` direkt. Scaffold besitzt eine **wertbasierte** Lease: Ein Fremdschreiber wird nur erkannt, wenn der Wert vom zuletzt geschriebenen abweicht. Schreibt ein anderes Modul denselben Wert (typisch 1.0), ist die Lease blind. `release` stellt `before` nur wieder her, wenn `current == written`; sonst bleibt der Fremdwert. `Speed`/`Velocity` setzen bei Disable unbedingt 1.0. `Speed.Polar` schreibt 1.03 und hat keinen `onValueChange`; ein Wechsel zu Intave lässt 1.03 stehen, während BMC/Vulcan/Vulcan2 1.0 setzen. `Velocity.Ground Grim` kann 0.8–1.0 schreiben und beim Moduswechsel stehen lassen.
- **Auswirkung:** Der endgültige Timer hängt von der Listener-/Enable-Reihenfolge ab. Scaffold-Timer kann mitten in der Platzierung überschrieben werden; Speed/Velocity-Disable kann eine aktive 0.9-Lease zerstören; Tickbase verweigert bei `|timer−1| >= 0.001` die Arbeit. Die Werte sind alle „plausibel“, daher fällt der Fehler nicht als Exception auf. Zusätzlich gibt `releasePlacement()` den Timer bei erfolgreicher Platzierung im selben Tick wieder frei (134, aufgerufen 175/183); da `Timer.updateTimer` erst am Tickbeginn liest, wirkt der Scaffold-Timer nach Codefluss auf keinen Movement-Schritt. Er bleibt nur stehen, wenn kein Ziel/keine Platzierung vorliegt.
- **Ursache:** Kein zentraler Timer-Owner; Wertvergleich statt Token/Generation.
- **Minimal:** Ein `TimerLease`-Dienst mit Token, Priorität, `before`, `written`, `generation`; alle drei Module schreiben nur über die Lease; Restore nur bei eigenem Token; kein direktes `timerSpeed =`. Priorität fachlich festlegen (Vorschlag: Scaffold-Platzierung > Speed > Velocity; oder ein explizites Delta-Budget).
- **Akzeptanz:** Interleaving-Tests: A schreibt 0.9, B schreibt 1.0, B released, A released → Basiswert; gleicher Wert zweier Writer; Moduswechsel Polar→Intave; Disable während fremder Lease; Tickbase bleibt bei sauberem 1.0 nutzbar.
- **Owner:** CORE (Dienst), MOV/SCF (Politik). **Abhängig:** MOV-W01-014.

#### MOV-W01-004 — Scaffold gibt den Rotations-Owner nach jeder Platzierung frei

- **Status:** A (Code); C (Scaffold-Tests); E (Spielwirkung).
- **Priorität:** P1 (Spec-3.1-Verstoß; MoveFix-Kontinuität).
- **Beleg:** `Scaffold.releasePlacement` 127-135; Aufrufe 98, 146, 154, 163, 171, 175-176, 183-184, 189, 193-197; `RotationManager.reset(String)` 297-304; `canUseRotation` 130-134; `RotationCorrections.select` 28-32; `ScaffoldPlacementService.place` 217; `EntityPlayerSP` POST 328-331.
- **Ist:** `releasePlacement()` ruft `RuntimeServices.rotation().reset("scaffold")` und löscht `currentTarget`, `frameSequence`, `downwardsState`, Tower und Timer. Es wird bei **erfolgreicher Platzierung** (175/183), bei fehlendem/ungültigem Ziel (154/163/171/176/184/196), bei jedem `onValueChange` (98), bei Weltwechsel/Disable (137–142, 189) und bei ungültigem Kontext (146) aufgerufen. `reset("scaffold")` entfernt den Owner, lässt aber `hasEffective`/`accepted`/`previousAccepted` stehen. `canUseRotation(true)` kann danach weiterhin true sein, während `RotationCorrections.select` wegen `owner == null` null liefert.
- **Auswirkung:** Nach jedem platzierten Block ist der Owner weg. Der nächste Bewegungsschritt (`moveFlying`) sieht keinen Owner → MoveFix ist aus. Bei `Pre` passiert der Erfolg **vor** `applyRotations`, also wird die gerade verwendete Rotation gelöscht und in diesem Tick nicht neu akzeptiert; bei `Post` wird sie nach C03 gelöscht. Zusätzlich platziert `Pre` mit der vorherigen akzeptierten Rotation, während der C03-Look desselben Ticks die Kamera sein kann (kein Scaffold-Winner, weil `applyRotations` übersprungen wird) — eine Platzierungs-/Look-Diskrepanz. Bei 180°-Scaffold ist die Weltbewegungsrichtung in diesen Schritten nicht mehr kameratreu; das ist genau das in der Spec beschriebene Szenario. Zusätzlich kann Scaffold mit der alten effektiven Basis weiter platzieren, während MoveFix aus ist — eine inkonsistente Paarung.
- **Ursache:** Ein einziger Reset für Plan und Owner; `reset(String)` ist kein „Plan-Reset“, sondern ein Owner-Reset; die Spec-Trennung `releasePlacement()` ≠ `reset("scaffold")` fehlt.
- **Minimal:** Zwei Operationen: `resetPlan()` (Ziel, Sequenz, Downwards, Tower; Timer nur nach eigener Lease) und `releaseOwner()` (nur bei Disable/Welt/Connection/Spielerwechsel oder explizitem Owner-Verlust). Nach erfolgreicher Platzierung nur `resetPlan()`; `owner` bleibt. Optionale sichtbare Freigabe mit 2–4 Tick-Auslauf, aber ohne MoveFix-Basisverlust.
- **Akzeptanz:** Synthetisch: Scaffold akzeptiert 180°, platziert einen Block, der nächste `moveFlying`-Event sieht weiterhin `owner = scaffold` und `PRECISE` erhält die Kamerarichtung; über N Platzierungen bleibt der Owner; Disable/Weltwechsel gibt frei. Ein Test muss `canUseRotation` und `getConfirmedOwner` **gemeinsam** prüfen.
- **Owner:** SCF (Implementierung), CMB/MOV (Owner-Vertrag). **Abhängig:** MOV-W01-001, -015; SCF-007.

#### MOV-W01-005 — Speed-Basis ist unbedingt ×1.2 und Potion-Stufen sind verschoben/doppelt

- **Status:** A (Code); B (`SpeedMovementBasisChecks` fixiert 1.2); E (Server).
- **Priorität:** P1 (alle fünf Modi; Potion- und Scaffold-Kombinationen).
- **Beleg:** `Speed.getModeBaseSpeed` 210–215; `getSpeedAmplifier` 217–220; `Vulcan` 161; `Vulcan2` 190; Test `SpeedMovementBasisChecks` ~103 (erwartet `1.2`); `Scaffold` 157.
- **Ist:** `base * (1.0 + 0.2 * (amplifier + 1))` mit `amplifier = (Speed ? level+1 : 0) − (Slowness ? level+1 : 0)`. Ohne Potion ist `amplifier = 0` → Faktor **1.2**. Speed I: `amplifier = 1` → **1.4**. Slowness I: `amplifier = −1` → **1.0** (keine Verlangsamung). `Vulcan`/`Vulcan2` multiplizieren zusätzlich `1.30 + amp*0.10` bzw. `1.34 + amp*0.08`, wobei `amp` der rohe Speed-Level ist. Die Basis hängt außerdem von `isSprinting()`/`isSneaking()` ab; `Scaffold` ändert `isSprinting()` in PRE.
- **Auswirkung:** Jeder Modus, der die Basis verwendet (BMC/Polar/Vulcan/Vulcan2; Intave nur beim Sprinten), ist mindestens 20 % schneller als die nominale Basis; die Potion-Skalierung ist gegenüber Vanilla systematisch verschoben und in Vulcan/Vulcan2 doppelt. Bei Scaffold+Sprint ist der Basiswert reihenfolgeabhängig (Sprint/Walk). Der Test „besteht“, weil er die 1.2 als Sollwert übernommen hat — ein grüner Test ist hier **kein** Korrektheitsnachweis.
- **Ursache:** Wahrscheinlich ein Off-by-one in `(amplifier + 1)` plus ein pauschaler Tuning-Faktor, der als Vanilla-Basis missverstanden wurde; keine Spezifikation der gewünschten Formel.
- **Minimal:** Formel fachlich festlegen: entweder Vanilla-Basis (`0.221`/`0.2873`/`0.0663`) und Potion separat (`1 + 0.2*level` für Speed, `1 − 0.15*(level+1)` für Slowness), oder bewusstes Modultuning mit dokumentierten Faktoren. `amp` nicht doppelt anwenden. Basis vor mutierenden Sprintentscheidungen einfrieren oder als Parameter übergeben.
- **Akzeptanz:** Testmatrix: kein Potion, Speed I/II, Slowness I/II, Sneak, Sprint, Scaffold-Sprint aus/an, alle fünf Modi; erwartete Werte aus der Spezifikation, nicht aus der aktuellen Implementierung. Kein Server-/AC-Anspruch.
- **Owner:** MOV (Formel), SCF (Sprint-Basis). **Abhängig:** MOV-W01-006.

#### MOV-W01-006 — Sprint-Besitz: `Sprint`, `WTap2`, `Scaffold`, Vanilla und MoreKB schreiben gegeneinander

- **Status:** A (Code); B (`SprintOrderingChecks` für Scaffold); E (Kombinationen).
- **Priorität:** P1 (WTap2 Legit funktioniert mit Sprint nicht; Paketstatus hängt von Reihenfolge ab).
- **Beleg:** `Sprint` 37–43; `WTap2` 70–75/120–129/143–147; `Scaffold` 157; `EntityPlayerSP` 230–240, 892–912; `MoreKB` 43/47–49; `RotationManager`/`MoveFix` nicht beteiligt.
- **Ist:** Es gibt fünf Schreiber: Vanilla-Entscheidung, `Sprint` (EventUpdate, setzt true), `WTap2.Legit` (AttackEvent, setzt Key false), `WTap2.Spoof` (PRE, setzt false), `Scaffold` (PRE, setzt false), `MoreKB` (Packet-C0B / `sprintingTicksLeft`). Keiner besitzt einen priorisierten Sprint-Vertrag. `Sprint` läuft nach dem AttackEvent und setzt Sprint wieder true; `Scaffold` läuft später und setzt false; `MoreKB` sendet direkt.
- **Auswirkung:** WTap2 Legit ist mit aktivem Sprint-Modul wirkungslos. Scaffold unterdrückt Sprint nur bei gültigem Plan; bei ungültigem Plan kann Sprint im selben Tick wieder an sein. Der Server-Sprintzustand hängt von der Enable-Reihenfolge ab; `MoreKB.Packet` kann den `serverSprintState`-Cache desynchronisieren. Das ist ein zentraler funktionaler Konflikt, nicht nur ein Schönheitsfehler.
- **Ursache:** `setSprinting`/Key-`pressed` werden als frei beschreibbarer globaler Zustand behandelt; es gibt keinen `SprintRequest`-Besitzer.
- **Minimal:** Ein `SprintController` mit Requests (Quelle, Priorität, TTL): WTap2 fordert Stop für 1–2 Ticks, Scaffold Stop solange Plan aktiv, Sprint Auto-Start, Vanilla als Fallback. Ein einziger Resolver schreibt `setSprinting` **nach** allen PRE-Requests und **vor** dem C0B-Sync. `MoreKB` nutzt denselben Controller statt roher C0B.
- **Akzeptanz:** Kombinationsmatrix (Sprint×WTap2, Scaffold×WTap2, Sprint×Scaffold, MoreKB×alle); Paket-Trace zeigt genau einen START/STOP pro Zustandswechsel; WTap2-Fenster bleibt sprintfrei, auch mit Sprint-Modul.
- **Owner:** CORE/MOV. **Abhängig:** MOV-W01-001, -003, -010.

#### MOV-W01-007 — AutoBridger überschreibt physisches Sneak und kollidiert mit Scaffold auf G

- **Status:** A (Code); SCF-001/002 erneut bestätigt; E (Live).
- **Priorität:** P1 (physischer Eingabeverlust; Doppel-Toggle).
- **Beleg:** `AutoBridger` 17–20, 26–36; `Scaffold` 94; `ModuleManager.toggleByKey` 66–84; `Minecraft.setIngameNotInFocus`/`unPressAllKeys` 1458–1462; SCF-001 (P1), SCF-002 (P1).
- **Ist:** Jeder `EventUpdate` setzt `keyBindSneak.pressed = playerOverAir()`. Enable/Disable setzen hart `false`. G ist gleichzeitig Scaffold- und AutoBridger-Key; `toggleByKey` toggelt beide.
- **Auswirkung:** Wer Shift hält, verliert Sneak auf dem Boden und muss neu drücken; beim Deaktivieren bleibt ein gehaltener Shift logisch aus. G schaltet unerwartet zwei Bridging-Module. AutoBridger beeinflusst zudem `Scaffold.applyDownwardsInput` (das `keyBindSneak.isKeyDown()` liest) und BridgeAssist (`isSneaking`).
- **Ursache:** Key-`pressed` ist kein Besitzobjekt; kein exklusiver Default-Key.
- **Minimal:** AutoBridger schreibt keinen Key-Zustand, sondern fordert eine `SneakLease` an (priorisiert, TTL, nur eigene Freigabe). Default-Key auf `KEY_NONE`; bestehende explizite Nutzerbindungen nicht überschreiben; Konflikt im UI anzeigen.
- **Akzeptanz:** Physisches Shift halten + Modul an/aus → Sneak bleibt, solange physisch gehalten; G toggelt im Frischprofil nur Scaffold; Legacy-Profil mit zwei G-Bindings zeigt Konflikt statt stiller Änderung.
- **Owner:** SCF/MOD (Key), MOV (Sneak-Lease). **Abhängig:** MOV-W01-002.

#### MOV-W01-008 — BridgeAssist umgeht den RotationManager und läuft doppelt pro Tick

- **Status:** A (Code); SCF-003/004 bestätigt; E (Kamera).
- **Priorität:** P1 (Rotationsarbitration/Bedienkonflikt); P2 für den ±180-Randfall.
- **Beleg:** `BridgeAssist` 61–93 (keine Phasenprüfung), 95–119 (direkte `rotationYaw/Pitch`-Writes), 121–129, 156–174; `EntityPlayerSP` 222/330; `RotationManager` 85–100; `RotationCorrections.current` 34–38.
- **Ist:** Der Listener reagiert auf PRE und POST, schreibt die Kamera direkt und kennt keinen Owner. `performGlide` kann pro Tick zweimal laufen. Die Zielsuche ist linear und wrap-frei; `aimAt` ist public.
- **Auswirkung:** Die Annäherung ist doppelt so schnell wie die eingestellte „Align speed“. Die Kamera dreht sichtbar und ist für MoveFix nicht als Owner sichtbar; bei aktivem Aura/Scaffold kann sie die aufgelöste Rotation überschreiben oder mit ihr oszillieren. Nahe ±180 wird die Ausrichtung übersprungen. Kein GUI-Guard.
- **Ursache:** Eigenständige Rotation statt Request an den gemeinsamen Manager; keine Phasen-/Wrap-/Suspension-Prüfung.
- **Minimal:** Entweder als **rein visuellen** Assist (Render-Pose, keine Kamera-/Packet-Rotation) oder als `requestVisible`/`request` mit Priorität und Owner. PRE-only; `MathHelper.wrapAngleTo180_float`; nächster Kandidat statt erster linearer; Null-/GUI-Guard.
- **Akzeptanz:** Test: PRE+POST → genau ein Schritt pro Tick; Owner sichtbar; Aura/Scaffold-Konflikt aufgelöst; 350°→0°-Fall; GUI off → keine Rotation.
- **Owner:** SCF/CMB. **Abhängig:** MOV-W01-001, -002.

### 4.2 P2

#### MOV-W01-009 — Velocity: Minemen tot, Verus praktisch tot, Chance gated alle Modi

- **Status:** A; Rechnung; E (Spiel).
- **Priorität:** P2 (zwei klar defekte/tote Modi; Default-Packet ist ein No-op).
- **Beleg:** `Velocity` 102–104, 147–153, 190–196, 233–237; `MathUtils.getRandomFloat(max,min)`; `NetHandlerPlayClient` 522–524.
- **Ist:** `ticks` wird nur auf 2 gesetzt, nie erhöht → `ticks > 14` nie wahr → Minemen führt nichts aus. `adjustKnockBack` teilt doppelt durch 100 (toter Zweig). Verus’ Bedingung ist ein Float-Vergleich mit einem Zufallswert und praktisch unerreichbar. `jumpChance` gilt für alle Modi; Default-Packet skaliert mit 100 % und ist damit wirkungslos.
- **Auswirkung:** Nutzer wählen Modi, die nichts tun; „Minemen“ und „Verus“ sind irreführend. Der Default „Packet“ ist ein No-op, solange X/Y auf 100 stehen.
- **Ursache:** Kein Test pro Modus; tote Felder/Zweige; Chance-Semantik nicht auf den Modus abgestimmt.
- **Minimal:** Pro Modus entscheiden: reparieren (Tickzähler, Faktor, Integer-HurtTime, Chance nur für Jump) oder **klar als defekt entfernen** (nur nach Entscheidung, mit Config-Migration). Keine stillen Änderungen an bestehenden Configs.
- **Akzeptanz:** Unit-Test je Modus mit definiertem KnockbackEvent und erwartetem `x/y/z`/`cancelled`; Minemen/Verus entweder getestet oder entfernt.
- **Owner:** MOV (Module), NET (KB-Event). **Abhängig:** MOV-W01-010.

#### MOV-W01-010 — MoreKB: rohe C0B-Pakete, stale Target, Debug-Spam

- **Status:** A; E (Server).
- **Priorität:** P2 (Sprint-Cache-Desync; Paketduplikate).
- **Beleg:** `MoreKB` 26–55; `EntityPlayerSP` 230–240; `PacketUtils`.
- **Ist:** Packet-Modus sendet STOP/START bei jedem EventUpdate mit Target und Input, ohne den `serverSprintState`-Cache zu aktualisieren. `target` wird nur bei Bewegungsinput gelöscht. `ticks` ist unbenutzt; Debug-Nachrichten bei jeder Aktion.
- **Auswirkung:** Der nächste Vanilla-Sync kann eine Änderung verpassen; der Server sieht möglicherweise einen anderen Sprintzustand als der Client. Ein späterer Angriff kann ein altes Target triggern. Chat/Log-Spam.
- **Ursache:** Direkter Paketweg statt Sprint-Controller; kein Timeout.
- **Minimal:** Sprint-Controller nutzen; Target nach Angriff/Timeout löschen; Debug nur hinter einem Debug-Setting; `ticks` entfernen oder nutzen.
- **Akzeptanz:** Paket-Trace: keine zwei C0B pro Tick, Cache konsistent; Target wird nach 1 Tick ohne Input gelöscht.
- **Owner:** MOV/NET. **Abhängig:** MOV-W01-006.

#### MOV-W01-011 — NoSlow: „Vanilla“ entfernt den Slowdown; Modi unterscheiden sich kaum

- **Status:** A; C (`NoSlowSprintChecks`); E (Paketwirkung).
- **Priorität:** P2 (irreführender Default; Mode-Versprechen).
- **Beleg:** `NoSlow` 29–31, 47–70, 85–114, 119–134; `EntityPlayerSP` 872–884.
- **Ist:** Der Default-Modus „Vanilla“ setzt `forward/strafe = 1.0` und `allowedSprinting = true`, entfernt also den Item-Slowdown. „Intave“, „Polar“, „Item Switch“ skalieren ebenfalls 1.0 und unterscheiden sich nur durch Pakete. „Reduced“ mappt `reduceX → forward` und `reduceZ → strafe` (potenziell vertauscht). Packet-Modi senden pro PRE-Tick.
- **Auswirkung:** Ein Nutzer, der „Vanilla“ erwartet, bekommt volle Bewegungsfreiheit; die Modusauswahl suggeriert Differenzierung, die lokal nicht existiert. Paketspam kann auffallen (ohne AC-Aussage).
- **Ursache:** `default`-Zweig als „alles auf 1.0“; keine Matrix/Tests.
- **Minimal:** „Vanilla“ als echten No-op (Event unverändert) oder umbenennen; X/Z-Zuordnung dokumentieren/korrigieren; Pakete nur bei Zustandswechsel senden; Modusmatrix testen.
- **Akzeptanz:** Test pro Modus: erwartete `forward/strafe/allowedSprinting` und Paketanzahl; „Vanilla“ verändert den Event nicht.
- **Owner:** MOV. **Abhängig:** keine.

#### MOV-W01-012 — `MovementInput.moveForward/moveStrafe` sind statisch

- **Status:** A; E (konkrete Fehlfunktion).
- **Priorität:** P2 (Architektur/Isolation; derzeit Einzelspieler-kompatibel).
- **Beleg:** `MovementInput` 8/13; `MovementInputFromOptions` 16–37; `EntityPlayerSP` 867/892/904/909; `Scaffold` 208–213; `PlayerMoveUpdateEvent` 32; Tests resetten die Statics.
- **Ist:** Die Felder sind prozessweit; jede `MovementInput`-Instanz teilt sie. `Scaffold.suppressSneak` schreibt sie global. `applyMotion` (tot) liest sie.
- **Auswirkung:** Keine Isolation zwischen Instanzen/Welten; ein späterer zweiter Input (Test, Freecam, Fahrzeug, mehrere Spieler) überschreibt den lokalen Input. Stale-Werte nach Weltwechsel möglich. Der Einzelspielerfall funktioniert nur, weil genau eine Instanz pro Tick schreibt.
- **Ursache:** Abweichung von Vanilla (dort Instanzfelder); unklarer Grund.
- **Minimal:** Instanzfelder wiederherstellen und alle Zugriffe auf `player.movementInput.moveForward` umstellen; `Scaffold.suppressSneak` arbeitet dann auf der Instanz. Breite, aber mechanische Änderung; nur mit Testabdeckung.
- **Akzeptanz:** Zwei Input-Instanzen beeinflussen sich nicht; Weltwechsel setzt den Input zurück; `MovementHelperContractTest` und `Scaffold`-Tests bleiben grün.
- **Owner:** CORE/MOV. **Abhängig:** MOV-W01-017.

#### MOV-W01-013 — Bewegungsmodule resetten ihren Zustand nicht bei Welt/Death/Respawn

- **Status:** A (fehlende Listener); E.
- **Priorität:** P2 (stale Zustände; einige harmlos, einige sichtbar).
- **Beleg:** `WorldEvent`-Listener nur in `AimAssist`, `Aura`, `Backtrack`, `Tickbase`, `AutoHeal`, `AutoHealInventoryTransfer`, `AutoTool`, `GlowESP`, `Scaffold`. **Keine** in `Speed`, `Velocity`, `NoSlow`, `WTap2`, `AutoBridger`, `BridgeAssist`, `JumpReset`, `MoreKB`, `Sprint`.
- **Auswirkung:** `Speed.groundTicks`/`polarAirTicks`, `Velocity.timerTicks`/`reset`/`intaveTick`, `NoSlow.usingItemLastTick`, `WTap2.releaseAt`, `BridgeAssist.gliding`/`waitingForAim`, `MoreKB.target` können in die neue Welt/den Respawn übergehen. `WTap2`/`BridgeAssist` sind zeitbasiert und können im neuen Kontext weiterlaufen; `Velocity.reset` kann einen veralteten Jump auslösen; `Speed` kann mit falscher Sprungparität starten.
- **Ursache:** Nur `onEnable`/`onDisable` räumen auf; Weltwechsel ruft kein `onDisable` auf.
- **Minimal:** Zentraler `MovementLifecycle.reset(context)` bei `WorldEvent`/Respawn/Death/Connection; jedes Modul ruft ihn oder registriert einen Reset. Keine Modul-Logik in den Event-Handler duplizieren.
- **Akzeptanz:** Test: Modul mit halbem Zustand, Weltwechsel, Zustand ist neutral; keine Aktion im neuen Kontext.
- **Owner:** CORE/MOV. **Abhängig:** MOV-W01-002.

#### MOV-W01-014 — Tickbase kann per-Tick-Zähler und EventUpdate-Aktionen vervielfachen

- **Status:** A; C (`TickbaseBudgetTest`); E (Kombination).
- **Priorität:** P2 (Burst-/Zählerfehler; keine Tests mit Speed/WTap).
- **Beleg:** `Tickbase.runPlayerStep` 83–104; `budget.step` 96; `EntityPlayerSP` 171/175; `Speed` 108/117/140/165/198; `WTap2` 82–99; `MoreKB` 37–54; `Scaffold` 191–198.
- **Ist:** Beim Release führt `Tickbase` den lokalen Schritt mehrfach aus; `EventUpdate`, Bewegung und PRE/POST laufen mehrfach pro Frame. `Speed`-Zähler und `MoreKB`-Aktionen sind per Schritt, nicht per Client-Tick. `WTap2` nutzt `System.currentTimeMillis`, also Wanduhr, während der Schritt mehrfach läuft. `Tickbase.usable` verlangt Timer 1.0, was mit MOV-W01-003 kollidiert.
- **Auswirkung:** Während eines Releases können Speed-Bodenparität, Luftticks, NoSlow-Pakete und WTap-Key-Writes in einem Frame mehrfach ausgeführt werden; der Burst ist nicht durch die Modulsettings begrenzt. `TickbaseBudgetTest` prüft nur den Budgetkern, nicht die Bewegungsmodule.
- **Ursache:** Kein Unterschied zwischen „simuliertem Spielerschritt“ und „Client-Tick“ in den Modulen.
- **Minimal:** Ein `StepContext` (step index, client tick, simulated?) an die Events; Module, die per Tick zählen, nutzen den Client-Tick; Aktionen mit Außenwirkung (Pakete, Keys) werden pro Client-Tick dedupliziert. Tests mit Speed/WTap/MoreKB unter Release.
- **Akzeptanz:** Bei 1×, 2×, 3× Release bleibt die Gesamtbewegung plausibel; keine doppelten Key-/Paketaktionen pro Frame; Zähler entsprechen der Spezifikation.
- **Owner:** CORE (StepContext), MOV. **Abhängig:** MOV-W01-001, -003.

#### MOV-W01-015 — Scaffold-Default-MoveFix ist „Aus“, die Spec verlangt „Präzise“

- **Status:** A; D (Spec); E (Nutzerwirkung).
- **Priorität:** P2 (Spec-Abweichung; Config-Migration nötig).
- **Beleg:** `MoveFixValue` 13 (Default-Index 0 = „Aus“); `Scaffold` 42; `Aura` 69; Spec 3.1 (Standard-MoveFix Präzise); `Scaffold` 255.
- **Ist:** Beide Module defaulten auf „Aus“. Die Spec beschreibt „Präzise“ als Standard und als Kern der 180°-Scaffold-Funktion.
- **Auswirkung:** Wer Scaffold einschaltet und MoveFix nicht manuell umstellt, erhält **keine** Kamerarichtungs-Erhaltung; bei gedrehtem Scaffold ist die Bewegung nicht wie spezifiziert. Das ist besonders kritisch, weil MOV-W01-004 die Korrektur ohnehin nach jeder Platzierung verliert.
- **Ursache:** Default-Index 0 aus der Legacy-Konfiguration; keine Migration.
- **Minimal:** Default für **neue** Profile auf „Präzise“; bestehende explizite „Aus“-Werte nicht still überschreiben. Config-Migration mit Kennzeichnung; UI-Hinweis.
- **Akzeptanz:** Frischprofil: Scaffold MoveFix = Präzise; Altprofil mit „Aus“ bleibt „Aus“ und wird nicht still geändert; Test der Decode-Migration.
- **Owner:** SCF/CMB, Config (MOD). **Abhängig:** MOV-W01-004.

#### MOV-W01-016 — JumpReset kollidiert mit Velocity-Jump/Speed/Scaffold-AutoJump

- **Status:** A; E.
- **Priorität:** P2 (vertikaler Impuls nicht additiv; horizontaler Sprint-Impuls kann addieren; kein zentraler Resolver).
- **Beleg:** `JumpReset` 35–58; `Velocity` 108–111; `Speed` 83/110/135/160/188; `Scaffold` 158; `EntityLivingBase.jump` 1585–1604.
- **Ist:** Mehrere Module rufen `jump()` im selben Tick auf. Jeder Aufruf setzt `motionY = 0.42` (plus Jump-Boost), addiert sich also nicht; der letzte gewinnt. `JumpReset` hat keinen Cooldown und keinen GUI-Guard.
- **Auswirkung:** Kein Doppelsprung, aber unnötige Zustandsschreibungen und eine nicht deterministische Reihenfolge; bei `JumpReset` + `Velocity.Jump` kann der Sprint-Impuls zweimal berechnet werden (jeweils `motionX -= sin*0.2`), was den horizontalen Impuls **addiert**. Das ist ein realer Unterschied.
- **Ursache:** Kein zentraler Jump-Resolver.
- **Minimal:** Ein `JumpRequest`-Resolver (eine Ausführung pro Tick, Priorität), oder `JumpReset` prüft `isAirBorne`/eine gemeinsame Sperre. Kein neues Modul.
- **Akzeptanz:** Test: zwei Jump-Quellen im selben Tick → genau ein `jump()`, ein Impuls.
- **Owner:** MOV. **Abhängig:** MOV-W01-006.

### 4.3 P3

#### MOV-W01-017 — Toter Code und unnötige Überschreibungen

- **Status:** A (Referenzsuche).
- **Priorität:** P3.
- **Beleg:** `event/motion/Movefix.java` (nie gepostet); `PlayerMoveUpdateEvent.applyMotion` (nur Definition); `Velocity.adjustPlayerMovement` 229–231, `isValidMotion` 245–247, `adjustKnockBack` (toter Zweig); `Scaffold.isDownwards` 263–265 (kein Consumer); `Speed.resetMovementOverrides` 234/236 (Jump-Key, `jumpMovementFactor`); `Speed` ohne `onValueChange`.
- **Auswirkung:** Irreführende APIs, zukünftige Fehlbenutzung (besonders `applyMotion` mit statischem Input), unnötige Key-/Faktor-Überschreibung. `Speed`-Moduswechsel lässt `speedInAir`/Timer bis zum nächsten Reset stehen.
- **Ursache:** Legacy-Reste; kein Aufräumen.
- **Minimal:** Entfernen oder als `@Deprecated`/Kommentar klarstellen; `resetMovementOverrides` auf Timer/Speed beschränken; `onValueChange` für Speed ergänzen.
- **Akzeptanz:** Referenzsuche findet keine Aufrufer; kein Verhalten ändert sich außer dem beabsichtigten Key-Leak.
- **Owner:** MOV. **Abhängig:** MOV-W01-012.

#### MOV-W01-018 — Paketspam und irreführende Modusnamen

- **Status:** A; E.
- **Priorität:** P3.
- **Beleg:** `NoSlow` 93–95 (C08 jeden PRE-Tick), 99–101/119–124 (zwei C09 jeden PRE-Tick), 103–110 (Polar PRE/POST); `WTap2` 120–129 („Spoof“ ist echtes `setSprinting(false)`); `MoreKB` 44/48 (Debug); `BridgeAssist.aimAt` public.
- **Auswirkung:** Mehr Pakete als nötig; Namensversprechen („Spoof“, „Vanilla“, „MoreKB“) stimmt nicht mit dem Code überein. Keine AC-Aussage.
- **Ursache:** Keine Zustands-/Rate-Limit-Prüfung; historische Namen.
- **Minimal:** Nur bei Zustandswechsel senden; Debug hinter Setting; Modusnamen an tatsächliches Verhalten anpassen oder Verhalten implementieren.
- **Akzeptanz:** Paketzähler pro Tick; Namensdokumentation.
- **Owner:** MOV/NET. **Abhängig:** MOV-W01-011.

---

## 5. Testabdeckung

### 5.1 Selbst ausgeführt

**Gezielter Lauf (JDK 8, Maven offline):**

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk-1.8"
& "...\apache-maven-3.8.5\bin\mvn.cmd" -o -q `
  "-Dtest=MovementHelperContractTest,MoveFixControllerTest,MoveFixProductionTest,MoveFixConfigTest,RotationCorrectionsTest,RotationBehaviorHookTest,NetworkMoveFixIntegrationTest,SpeedMovementBasisTest,NoSlowSprintTest,SprintOrderingTest,TickbaseBudgetTest" test
```

Ergebnis: **50 Tests in den 11 Klassen, 0 Failures, 0 Errors** (Summe der Einzelklassen im Gesamtlauf; inkl. der Kind-JVM-Suiten `SpeedMovementBasisChecks`, `NoSlowSprintChecks`, `SprintOrderingChecks`, `MoveFixProductionChecks`). Der fokussierte Lauf wurde mit `-q` ausgeführt; sein Log enthält daher nur die Kind-JVM-Ausgaben.

**Gesamtlauf:**

```powershell
& $mvn -o test
```

Ergebnis: **396 Tests, 0 Failures, 0 Errors, 3 Skipped, BUILD SUCCESS, 56,4 s** (`C:\Windows\Temp\opencode\w01-full-tests.log`).

### 5.2 Was die Tests tatsächlich belegen

| Test | Status | Belegt | Belegt **nicht** |
|---|---|---|---|
| `MoveFixControllerTest` (7) | selbst ausgeführt (fokussiert + Gesamtlauf) | Formel, Wrap, Offsets, SIMPLE-Oktanten, Jump-Yaw | echten Tick, Server, Timer |
| `MoveFixProductionTest`/`Checks` | selbst ausgeführt (fokussiert + Gesamtlauf) | Matrix über Owner/Mode/Inputs in isolierter JVM, Owner-Akzeptanz | Live-Render, Packet-Reihenfolge |
| `NetworkMoveFixIntegrationTest` (6) | selbst ausgeführt (fokussiert + Gesamtlauf) | Pipeline, `observeOutboundRotation`, Owner, Delay/Barrier, Tickbase-Idempotenz | echten Server |
| `RotationCorrectionsTest` (6) | selbst ausgeführt (fokussiert + Gesamtlauf) | Owner-Auswahl, pending vs. accepted, Source-Priorität | GUI, Weltwechsel |
| `RotationBehaviorHookTest` (3) | selbst ausgeführt (fokussiert + Gesamtlauf) | Fixture/Hook, Annahme | Produktionspfad vollständig |
| `MovementHelperContractTest` (5) | selbst ausgeführt (fokussiert + Gesamtlauf) | Geometrie, Grad/Radiant, Sneak-Sign | reale Kollisionen |
| `SpeedMovementBasisTest` | selbst ausgeführt (fokussiert + Gesamtlauf) | fünf Modi, Owner, Inputs −1/0/1 | Timer, Potion, GUI, Moduswechsel |
| `NoSlowSprintTest` | selbst ausgeführt (fokussiert + Gesamtlauf) | Vanilla-Slowdown/Sprint-Gate | Intave/Polar/Item Switch, Pakete |
| `SprintOrderingTest` | selbst ausgeführt (fokussiert + Gesamtlauf) | Scaffold unterdrückt Sprint vor C0B | Sprint×WTap, GUI |
| `TickbaseBudgetTest` | selbst ausgeführt (fokussiert + Gesamtlauf) | Budget/Release-Kern | Speed/WTap/MoreKB unter Release |
| `Scaffold*Test` | selbst ausgeführt (Gesamtlauf) | Planung, Slots, Delay, Weltwechsel, Pipeline | **Owner über Platzierung**, MoveFix-Kontinuität |
| Velocity/WTap2/AutoBridger/BridgeAssist/JumpReset/MoreKB | **nicht geprüft** (kein Test vorhanden) | — | gesamtes Modulverhalten |

### 5.3 Lücken (ausdrücklich)

- **Keine** Tests für `Velocity` (alle Modi), `WTap2`, `AutoBridger`, `BridgeAssist`, `JumpReset`, `MoreKB`.
- **Keine** Tests für `timerSpeed`-Interleavings, Moduswechsel, Disable während fremder Lease.
- **Keine** Tests für GUI/Fokus/Pause in Bewegungsmodulen.
- **Keine** Tests für Welt-/Death-/Respawn-Reset der Bewegungsmodule.
- **Keine** Tests für den Scaffold-Rotationsowner über eine Platzierung hinweg (MOV-W01-004).
- **Keine** Tests für `MovementInput`-Instanzisolation.
- **Keine** Tests für Tickbase × Speed/WTap/MoreKB.
- **Keine** Tests für Potion-Stufen/Sneak/Sprint-Basis in Speed.

### 5.4 Nicht geprüft

- Kein Live-Spiel, kein Server, kein Account, kein Anti-Cheat.
- Keine Aussage über Serverakzeptanz, Flags, Schaden, Reichweite oder „unflaggbar“.
- Keine Performance-/Render-/UI-Prüfung.
- Keine vollständige Prüfung aller übrigen Module; Fokus war die genannte Bewegungsarchitektur.

---

## 6. Altbefunde: bestätigt, qualifiziert, widerlegt

| Altbefund / Hypothese | Status am HEAD | Beleg |
|---|---|---|
| **MoveFix ist mathematisch falsch** | **widerlegt** | `MoveFixController` 28–56; `RotationCorrections` 75–88; 7+1+6+5 Tests (B); Formelherleitung in 2.3 |
| **MoveFix wird doppelt angewendet** | **widerlegt** | `claimRotationCorrection` in `PlayerMoveUpdateEvent` 16–21 und `JumpFixEvent` 12–17; nur `RotationCorrections` als Consumer; `applyMotion` ist tot |
| **SIMPLE erhält die exakte Kamerarichtung** | **qualifiziert/widerlegt als „exakt“** | 8 Richtungen, max. 22,5° Fehler; `MoveFixController` 22–24/39–47; `design/movefix-verification.md` |
| **Scaffold hält die Rotation bis Disable** | **widerlegt (Ist) / offen (Soll)** | `releasePlacement` 127–135; Aufrufe 175/183; Spec 3.1 verlangt das Gegenteil |
| **Speed BMC entspricht Vanilla-Sprint** | **widerlegt** | `getModeBaseSpeed` ×1.2; BMC ×1.08 → 0.3723 statt 0.2873 (bei keinem Potion) |
| **NoSlow-Modi entfernen den Slowdown unterschiedlich** | **widerlegt für Intave/Polar/Item Switch** | alle `default` 1.0; nur Reduced/Vulcan ändern die Skalierung |
| **WTap2 „Spoof“ spoofed nur** | **widerlegt** | `setSprinting(false)` → echtes C0B-STOP über `EntityPlayerSP` 230–240 |
| **AutoBridger erhält physisches Sneak** | **widerlegt** | `pressed = playerOverAir()` jeden Tick; Disable setzt false |
| **BridgeAssist ist nur visuell** | **widerlegt** | direkte `rotationYaw/Pitch`-Writes 102–111; kein Owner |
| **Velocity Minemen funktioniert** | **widerlegt** | `ticks` nie erhöht; Faktor 1/4.2e6; `cancel` verhindert `setVelocity` |
| **Velocity Verus funktioniert** | **widerlegt/praktisch tot** | Float-Vergleich mit Zufall; nur bei `nextFloat()==0` |
| **SCF-001 doppelte G-Taste** | **bestätigt** | `Scaffold` 94; `AutoBridger` 23; `ModuleManager` 66–84 |
| **SCF-002 AutoBridger-Sneak** | **bestätigt** | `AutoBridger` 17–20/26–36 |
| **SCF-003 BridgeAssist PRE+POST** | **bestätigt** | `BridgeAssist` 61–93; `EntityPlayerSP` 222/330 |
| **SCF-004 BridgeAssist ohne RotationManager** | **bestätigt** | `BridgeAssist` 102–111; `RotationManager` 85–100 |
| **SCF-006 PRE-Placement vor Sprint/Sneak-Paketen** | **bestätigt (Reihenfolge)**; Serverfolge offen | `EntityPlayerSP` 222/230/242; `Scaffold` 175 |
| **SCF-007 TimerControl ohne globalen Vertrag** | **bestätigt** | `Scaffold` 74–91; `Speed`/`Velocity` Timer-Writes |
| **SCF-013 Downwards nutzt vorherigen Plan** | **bestätigt** | `EntityPlayerSP` 869; `Scaffold` 201–206; `currentTarget` aus vorherigem PRE |
| **W00 MOD-010/MOD-011** | bereits in W00 widerlegt; hier nicht erneut geprüft | `design/opencode-wave00/RESULTS.md` |

---

## 7. Offene Entscheidungen (keine Implementierung in W01)

| ID | Entscheidung | Optionen | Empfehlung |
|---|---|---|---|
| D1 | Was bedeutet „PRE“ fachlich? | (a) Paketphase belassen, überall dokumentieren; (b) neues echtes Pre-Movement-Event einführen | (a) kurzfristig; (b) nur mit CORE-Design, weil Vanilla-Hook tief greift |
| D2 | Timer-Priorität | Scaffold > Speed > Velocity; Speed > Scaffold; globales Delta-Budget | Scaffold-Platzierung > Speed > Velocity, mit Lease und harter Obergrenze |
| D3 | Scaffold-Rotation zwischen Platzierungen | halten bis Disable (Spec); mit 2–4 Tick-Auslauf; pro Plan | halten bis Disable, Auslauf nur sichtbar, MoveFix-Basis bleibt |
| D4 | Speed-Basisformel | Vanilla-Basis + separate Potion; bewusstes Tuning; konfigurierbar | Vanilla-Basis + separate Potion, Tuning nur als dokumentierter Faktor |
| D5 | Sprint-Besitz | zentraler Controller; Priorität pro Quelle; TTL | zentraler Controller, WTap-TTL 2 Ticks, Scaffold solange Plan, Sprint Fallback |
| D6 | AutoBridger/BridgeAssist | Key/Sneak-Lease + Rotation-Request; oder Module entfernen | Lease/Request; Default-Key frei; keine stillen Config-Änderungen |
| D7 | Velocity Minemen/Verus | reparieren; entfernen; als „legacy/defekt“ markieren | zuerst Tests; dann reparieren oder klar entfernen (nur nach Entscheidung) |
| D8 | NoSlow „Vanilla“ | echter No-op; umbenennen; Default ändern | echter No-op oder Umbenennung; Default nicht still ändern |
| D9 | Scaffold-MoveFix-Default | „Präzise“ für neue Profile; Migration; UI-Hinweis | neue Profile „Präzise“, Altprofile unverändert |
| D10 | Tickbase-Semantik | Schritt-zentriert; Client-Tick-zentriert; Hybrid | Hybrid: Bewegung pro Schritt, Außenwirkungen pro Client-Tick |

---

## 8. Empfohlene Reihenfolge (ohne Produktivänderung in W01)

1. **CORE-Vertrag** (MOV-W01-001, -002, -013): PhaseClock/SuspensionGate/Lifecycle, Tests.
2. **Timer-Lease** (MOV-W01-003): zentraler Dienst, Interleaving-Tests.
3. **Sprint-Controller** (MOV-W01-006, -010): ein Resolver, Paket-Trace.
4. **Scaffold-Owner-Trennung** (MOV-W01-004, -015): Plan-Reset vs. Owner-Release, Kontinuitätstest.
5. **Speed-Formel** (MOV-W01-005): Entscheidung + Testmatrix.
6. **BridgeAssist/AutoBridger** (MOV-W01-007, -008): Lease/Request, Phase/Wrap.
7. **Velocity/NoSlow/MoreKB/WTap2** (MOV-W01-009..011, -016..018): Modusmatrix, tote Zweige, Paketdisziplin.
8. **MovementInput-Instanzfelder** (MOV-W01-012) als eigene, breite Aufräumwelle.

---

## 9. Grenzen und Nicht-Aussagen

- Dieser Bericht ist **kein** Server- oder Anti-Cheat-Test. „Lokal konsistent“ heißt nicht „serverakzeptiert“.
- Die MoveFix-Formel ist geprüft; die **Interaktion** mit fremden Schreibern ist es nicht.
- Die meisten Befunde sind **statisch** (A). Nur die genannten Tests sind selbst ausgeführt (B).
- Die 147 vorhandenen Arbeitsbaumänderungen wurden nicht bewertet, nicht bereinigt und nicht überschrieben.
- Es wurden keine Commits, Pushes, Branches, PRs, Tags oder Main-Änderungen erzeugt.
- Die drei W01-Dateien sind die einzigen neuen Dateien; `git diff --name-only` bleibt gegenüber dem Ausgangszustand unverändert (siehe Abschlussvermerk im Chat).

---

## Anhang A — Belegindex (Datei:Zeile)

| Thema | Datei:Zeile |
|---|---|
| Tick/Phase | `src/main/java/net/minecraft/client/entity/EntityPlayerSP.java` 168–197, 205–333, 335–340 |
| Input/Slowdown/Sprint | `EntityPlayerSP.java` 864–912 |
| `moveFlying` | `src/main/java/net/minecraft/entity/Entity.java` 1124–1160 |
| `jump` | `src/main/java/net/minecraft/entity/EntityLivingBase.java` 1585–1604 |
| `MovementInput` | `src/main/java/net/minecraft/util/MovementInput.java` 8–19; `MovementInputFromOptions.java` 14–47 |
| Timer | `src/main/java/net/minecraft/util/Timer.java` 96–114 |
| KB | `src/main/java/net/minecraft/client/network/NetHandlerPlayClient.java` 508–533 |
| RotationManager | `src/main/java/me/youngjibbit/core/combat/RotationManager.java` 48–134, 147–159, 297–316 |
| MoveFix | `src/main/java/me/youngjibbit/core/combat/MoveFixController.java` 28–63; `RotationCorrections.java` 28–88 |
| Speed | `src/main/java/me/youngjibbit/module/impl/movement/Speed.java` 19–238 |
| Sprint | `src/main/java/me/youngjibbit/module/impl/movement/Sprint.java` 36–45 |
| Velocity | `src/main/java/me/youngjibbit/module/impl/combat/Velocity.java` 33–248 |
| NoSlow | `src/main/java/me/youngjibbit/module/impl/player/NoSlow.java` 29–164 |
| WTap2 | `src/main/java/me/youngjibbit/module/impl/ghost/WTap2.java` 24–207 |
| MoreKB | `src/main/java/me/youngjibbit/module/impl/combat/MoreKB.java` 22–55 |
| JumpReset | `src/main/java/me/youngjibbit/module/impl/ghost/JumpReset.java` 35–59 |
| AutoBridger | `src/main/java/me/youngjibbit/module/impl/ghost/AutoBridger.java` 17–44 |
| BridgeAssist | `src/main/java/me/youngjibbit/module/impl/world/BridgeAssist.java` 61–174 |
| Scaffold | `src/main/java/me/youngjibbit/module/impl/world/Scaffold.java` 40–214, 216–265 |
| Tower | `src/main/java/me/youngjibbit/core/world/TowerController.java` 21–40 |
| Tickbase | `src/main/java/me/youngjibbit/module/impl/exploit/Tickbase.java` 73–136 |
| Tests | `src/test/java/me/youngjibbit/core/combat/*`, `utils/MovementHelperContractTest.java`, `core/network/TickbaseBudgetTest.java`, `core/world/Scaffold*Test.java` |
| Spec | `C:\Windows\Temp\opencode\neutron-planning\01_VERBINDLICH\Neutron_2_0_Universelle_Produktspezifikation_v1.0.md`, 3.1/4 |

## Anhang B — Abkürzungen

- **A/B/C/D/E:** Evidenzklassen aus Abschnitt 0.
- **Owner:** bestätigter Rotationsbesitzer (`RotationManager.owner`), nicht der Modulname.
- **Lease:** zeitlich begrenzte Schreibberechtigung auf einen globalen Zustand.
- **PRE/POST:** `EventMotion`-Phasen (Paketzyklus, siehe 2.1).
- **MoveFix:** Bewegungskorrektur in `Entity.moveFlying`/`EntityLivingBase.jump` über `RotationCorrections`.
