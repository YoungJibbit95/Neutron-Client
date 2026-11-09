# MOV-W01 — Write-Access-Matrix der Bewegungsarchitektur

**Projekt:** Pulsar-Client / Neutron 2.0
**Welle:** W01, schreibgeschützt
**HEAD:** `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`
**Datum:** 2026-10-27
**Bezug:** `MOV_AUDIT_W01.md` (Befunde MOV-W01-001 … -018)

---

## 0. Zweck und Methode

Diese Matrix beantwortet für jeden bewegungsrelevanten Zustand:

1. **Wer schreibt?** (Datei:Zeile, Modul)
2. **In welcher Phase?** (`EventUpdate`, `EventMotion.PRE`, Vanilla-Movement, `EventMotion.POST`, Paketphase)
3. **Unter welcher Bedingung und mit welchem Wert?**
4. **Wer liest/konsumiert?**
5. **Gibt es einen Besitzer/Arbitrator?**
6. **Wie wird zurückgesetzt?**
7. **Welche Kollision entsteht?**
8. **Wie ist der Befund belegt?**

**Evidenz:** `A` = im Code bestätigt (statisch); `B` = selbst ausgeführter Test; `C` = Testcode gelesen; `D` = älterer Bericht; `E` = nicht geprüft. Details in `MOV_AUDIT_W01.md`, Abschnitt 0.

**Phasenmodell (kurz):**

```text
EventUpdate
  → super.onUpdate() = Input, Slowdown, Sprintentscheidung, moveFlying
  → EventMotion.PRE
  → Sprint-C0B / Sneak-C0B
  → RotationManager.resolve
  → C03 senden
  → RotationManager.finishMotion
  → EventMotion.POST
```

`PRE` ist **nach** der Bewegung. Das ist für jede Zeile der Matrix entscheidend: Motion-Writes in PRE wirken auf den nächsten Schritt; Sprint-/Sneak-Writes in PRE wirken auf das C0B desselben Ticks; Key-Writes in `EventUpdate` wirken auf den Input desselben Ticks.

---

## 1. Master-Matrix

> Lesart: „Phase“ ist der Ausführungszeitpunkt des Schreibers. „Arbitration“ ist der **tatsächliche** Mechanismus, nicht der Wunsch. „Risiko“ verweist auf die Findings.

| Zustand | Schreiber (Datei:Zeile) | Modul | Phase | Bedingung / Wert | Consumer | Arbitration | Reset | Risiko | Evidenz |
|---|---|---|---|---|---|---|---|---|---|
| `mc.timer.timerSpeed` | `Scaffold.java` 134, 164–165; `TimerControl` 74–91 | Scaffold | PRE | gewünschter Timer 0.2–3.0; Lease `before`/`written` | `Timer.updateTimer`, `Tickbase.usable` | **wertbasierte Lease**, kein Token | `releasePlacement` 134; `onDisable` via `clearState` | **P1** MOV-W01-003 | A |
| `mc.timer.timerSpeed` | `Speed.java` 105, 127, 134, 145, 154, 181, 235 | Speed | PRE / Disable | `1.0`; Polar `1.03` | dito | **keine**; direkter Write | `resetMovementOverrides` 235; `onDisable` 39 | **P1** MOV-W01-003 | A |
| `mc.timer.timerSpeed` | `Velocity.java` 62, 166–170 | Velocity | TickEvent / Disable | `0.8…1.0` Rampe; `1.0` | dito | **keine**; `<=1`-Guard | `onDisable` 62 | **P1** MOV-W01-003 | A |
| `keyBindJump.pressed` | `Speed.java` 234 | Speed | Reset/Disable | `Keyboard.isKeyDown(jump)` | `MovementInputFromOptions` 39 | physische Taste als „Owner“ | dito | kann fremden synthetischen Jump löschen; P3 MOV-W01-017 | A |
| `keyBindSneak.pressed` | `AutoBridger.java` 19, 29, 35 | AutoBridger | EventUpdate / Enable / Disable | `playerOverAir()`; Enable/Disable `false` | `MovementInputFromOptions` 40; `Scaffold` 149/181/204; `BridgeAssist` 69 | **keine**; Überschreibt physisch | Enable/Disable `false` | **P1** MOV-W01-007 | A |
| `keyBindForward.pressed` | `WTap2.java` 48, 73, 135, 145, 160, 165 | WTap2 | Attack / TickEvent / Disable | `false` im WTap-Fenster; Restore `Keyboard.isKeyDown` | `MovementInputFromOptions` 19 | physische Taste, aber fremde Schreiber möglich | Restore im nächsten Tick / Disable | P2 MOV-W01-006 | A |
| `keyBindBack.pressed` | `WTap2.java` 49, 161, 166 | WTap2 | TickEvent / Disable | `true` im STap-Fenster; Restore physisch | `MovementInputFromOptions` 24 | dito | dito | P2 MOV-W01-006 | A |
| `keyBindSprint.pressed` | `WTap2.java` 72, 144 | WTap2 | Attack / TickEvent | `false` bei Legit | Vanilla 894/904 (`isKeyDown`) | **keine** | Restore über `Keyboard.isKeyDown` / Disable | **P1** MOV-W01-006 | A |
| `setSprinting` | `Sprint.java` 43 | Sprint | EventUpdate | `true`, wenn Input, `moveForward>=0`, kein Item/Sneak/Kollision | Vanilla 892–912; C0B 230–240 | **keine** | Vanilla entscheidet neu | **P1** MOV-W01-006 | A |
| `setSprinting` | `WTap2.java` 50, 114, 122, 127 | WTap2 | Attack / PRE / Disable | Legit: Key false; Spoof: `false` bei Hurt; Resume `true` | dito | **keine** | Disable / Resume | **P1** MOV-W01-006 | A |
| `setSprinting` | `Scaffold.java` 157 | Scaffold | PRE | `false`, wenn gültiger Plan und `Sprint=false` | dito | **keine** (nur Reihenfolge) | kein expliziter Restore; Vanilla | P1 (nur bei Plan) | A |
| `sprintingTicksLeft` | `MoreKB.java` 43 | MoreKB | EventUpdate | `0` | Vanilla 795–805 → Stop | **keine** | Vanilla | P2 MOV-W01-010 | A |
| `movementInput.moveForward/moveStrafe` | `MovementInput.java` 8/13 (static); `MovementInputFromOptions` 16–45; `Scaffold` 208–213 | Vanilla / Scaffold | Input / Downwards | ±1, Sneak ×0.3; Scaffold ÷0.3, `sneak=false` | `EntityPlayerSP` 776–777; `moveFlying` | **keine**; global static | neuer Input jeden Tick | P2 MOV-W01-012 | A |
| `player.moveForward/moveStrafing` | `EntityPlayerSP.java` 776–777 | Vanilla | `updateEntityActionState` | Kopie aus `movementInput` | `MoveUtil.hasMovementInput` 12–14; `moveEntityWithHeading` | **keine** | jeden Tick neu | P3 (Folge von static) | A |
| `player.motionX/Y/Z` | `Entity.moveFlying` 1154–1155 | Vanilla | Movement | Beschleunigung aus Input/Yaw | Kollision/Position | Vanilla | Kollision/Reibung | Basis | A |
| `player.motionX/Y/Z` | `EntityLivingBase.jump` 1602–1603 | Vanilla | Jump | Sprint-Impuls 0.2 in Yaw-Richtung | Position | Vanilla | nächster Tick | P2 (mehrere Jump-Quellen) | A |
| `player.motionX/Y/Z` | `Speed.java` 94–95, 120–121, 171, 200 | Speed | PRE | Multiplikatoren; `motionY -= 0.03`; `motionY = -0.13` | nächster Schritt | **keine** | nächster Schritt | P1/P2 | A |
| `player.motionX/Y/Z` | `Velocity.java` 82–86, 192–194 | Velocity | Attack / PRE | ×0.6 (Intave2); `0` (Verus) | nächster Schritt | **keine** | nächster Schritt | P2 MOV-W01-009 | A |
| `player.motionX/Y/Z` | `MoveUtil.setHorizontalMotion` 40–43; `strafe` 46–49; `setSpeedFromInput` 52–59 | Speed/MoveUtil | PRE | absolute horizontale Geschwindigkeit | nächster Schritt | **keine** | nächster Schritt | P1 (MoveFix-Basis) | A |
| `player.motionY` | `TowerController.java` 29/31/34/35 | Scaffold Tower | PRE | `.42` / `.24` / `.32` | nächster Schritt | **keine** | `reset()` bei Bedingung | P3 (Tower) | A |
| `mc.thePlayer.speedInAir` | `Speed.java` 118, 237 | Speed | PRE / Reset | `0.03` (Air Speed) / `0.02` | `EntityPlayer.updateEntityActionState` 628/632; `jumpMovementFactor` | **keine** | nur Reset-Pfad/Disable | P2 (Moduswechsel) | A |
| `mc.thePlayer.jumpMovementFactor` | `Speed.java` 236 | Speed | Reset | `0.02` | `EntityLivingBase` 1649; `EntityPlayer` 1800 | **keine** | dito | P3 | A |
| `rotationYaw/rotationPitch` (Kamera) | `BridgeAssist.java` 102–111 | BridgeAssist | PRE **und** POST | lineare Annäherung an Ziel | C03, Movement, Render | **kein Owner** | `onDisable` nur Flags | **P1** MOV-W01-008 | A |
| `rotationYaw/rotationPitch` (Kamera) | `RotationManager.resolve` 95–96 | Aura/AimAssist | PRE | sichtbare Request-Differenz | C03/Render | `RotationManager` | `finishMotion` | P1/P2 (CMB) | A |
| `rotationYaw/rotationPitch` (Kamera) | Vanilla Maus | Spieler | Input | Maus-Delta | C03/Render | Spieler | — | — | A |
| Rotations-Owner/-State | `RotationManager.request/finish/reset` 70–117/297–304 | Aura/Scaffold | PRE/POST | Priorität 20/30; Owner nur bei exaktem Match | `RotationCorrections` | **vorhanden**, aber Scaffold released | `reset(source)` / `reset()` | **P1** MOV-W01-004 | A |
| Rotations-Freigabe | `Scaffold.releasePlacement` 127–135; Aufrufe 98/146/154/163/171/175/183/196 | Scaffold | PRE/POST/Tick/Setting | `reset("scaffold")` bei Erfolg/Planende/Setting | MoveFix/Placement | **kein Plan-vs-Owner-Trennung** | dito | **P1** MOV-W01-004 | A |
| `serverSprintState` (Cache) | `EntityPlayerSP` 230–240 | Vanilla | PRE→C03 | Sync bei `isSprinting()`-Änderung | C0B | **Vanilla-Cache** | bei Zustandswechsel | P2 (MoreKB umgeht) | A |
| C0B STOP/START | `MoreKB.java` 47–49 | MoreKB | EventUpdate | rohe Pakete | Server | **keine**; Cache bleibt | — | P2 MOV-W01-010 | A |
| C07/C08/C09 | `NoSlow.java` 93–95, 99–101, 119–134 | NoSlow | PRE/POST | Item-Use-Pakete | Server | **keine** | — | P3 MOV-W01-018 | A |
| C08 Place | `ScaffoldPlacementService.place` 209–244; `Scaffold` 232–242 | Scaffold | PRE/POST | nach `canUseRotation`/Raycast | Server | RotationManager + Delay | pro Plan | P1 (Owner) | A |
| C03 Rotation | `EntityPlayerSP` 275–299; `NetworkManager` 176; `NetHandler` 877 | Vanilla | PRE | Event-Rotation | Server | `RotationManager`/Observe | pro Tick | Basis | A |
| Tick-Schritte | `Tickbase.runPlayerStep` 84–104 | Tickbase | vor `EventUpdate` | `budget.step` kann simulieren/aussetzen | alle Events | **Budget**, aber kein StepContext | Budget | P2 MOV-W01-014 | A |
| Welt-/Death-Zustand | `WorldEvent`-Listener: Aura 133, Scaffold 189, Tickbase 171, … | nur wenige | Event | Reset | Module | **keine zentrale** | modulweise | P2 MOV-W01-013 | A |

---

## 2. Detaillierte Zustandsverträge

### 2.1 `timerSpeed`

**Ist-Vertrag:**

- `Timer.timerSpeed` ist öffentlich und wird von `Timer.updateTimer` nur auf `finite && >= 0` geprüft (`Timer.java` 105). Es gibt keine Obergrenze und keinen Besitzer.
- `Scaffold.TimerControl` ist die einzige Lease:
  - `apply(current, desired)`: erkennt Fremdschreiber nur bei `current != written`; setzt `before = current`, `written = desired`.
  - `release(current)`: gibt `before` nur zurück, wenn `current == written`.
- `Speed` schreibt `1.0` in BMC/Polar/Vulcan/Vulcan2 und im Reset; Polar schreibt `1.03` in den Luftticks 3–6.
- `Velocity` schreibt `1.0` im Disable und eine Rampe `0.8 + 0.2*(20-timerTicks)/20` in Ground Grim.
- `Tickbase` verlangt `|timerSpeed − 1| < 0.001` (`Tickbase.java` 130) und wird sonst nicht „usable“.

**Kollisionsfälle:**

1. **Scaffold will 1.03, Speed schreibt 1.0:** Reihenfolge entscheidet. Schreibt Speed nach Scaffold, ist der Timer 1.0; Scaffold erkennt beim nächsten `apply` `current != written` (1.0 ≠ 1.03), setzt `blocked` und schreibt nicht mehr. Der gewünschte Scaffold-Timer ist für den Rest der Platzierung aus.
2. **Speed will 1.0, Scaffold will 1.0:** `apply` sieht `current == desired` und beansprucht keine Lease. Ein späteres `release` hat nichts zu restoren. Kein Problem, solange beide 1.0 wollen; sobald Scaffold 1.2 will, ist die Kollision da.
3. **Speed Polar 1.03 → Moduswechsel Intave:** Intave schreibt keinen Timer; 1.03 bleibt stehen, bis ein anderer Modus/Disable `1.0` schreibt. Tickbase bleibt derweil unbrauchbar.
4. **Velocity Ground Grim → Moduswechsel:** Die Rampe bleibt stehen; `onDisable` setzt 1.0, aber ein Moduswechsel ruft kein Disable.
5. **Speed/Velocity Disable während Scaffold-Lease:** `onDisable` schreibt 1.0 und überschreibt die Lease, obwohl Scaffold noch aktiv ist.
6. **Gleicher Wert, fremder Writer:** `Scaffold` kann 1.0 nicht von einem fremden 1.0 unterscheiden; die Lease ist blind.

**Erforderlicher Vertrag (Vorschlag):**

| Feld | Wert |
|---|---|
| Owner | `TimerLease`-Dienst, ein aktiver Lease-Träger pro Zeit |
| Identität | Token/Generation, nicht Wert |
| Priorität | Scaffold-Platzierung > Speed > Velocity (Entscheidung D2) |
| Restore | nur eigener Token; sonst `current` unverändert |
| Leser | `Tickbase` liest `isNeutral()` statt `== 1.0` |
| Reset | Welt/Disable/Setting nur eigener Lease |

**Akzeptanz:** Interleaving-Tests (siehe Audit, MOV-W01-003). **Evidenz:** A; kein Test (E).

### 2.2 Sprint

| Quelle | Phase | Richtung | Bedingung | Paketweg |
|---|---|---|---|---|
| Vanilla | Movement | Start/Stop | `moveForward >= 0.8`, Food, Item-Use, Blindness, Taste/Toggle | C0B-Sync 230–240 |
| `Sprint` | EventUpdate | Start | Input, `moveForward>=0`, kein Item/Sneak/Kollision | kein eigener Paketweg; Vanilla-Sync |
| `WTap2.Legit` | Attack/Tick | Stop | Ziel, Hurt, Boden, sprintend | Key false; Vanilla-Sync |
| `WTap2.Spoof` | PRE | Stop/Start | Ziel, Hurt, Boden; Resume bei Taste+Input | `setSprinting` → Vanilla-Sync |
| `Scaffold` | PRE | Stop | gültiger Plan, `Sprint=false` | `setSprinting(false)` → Vanilla-Sync |
| `MoreKB` | EventUpdate | Stop/Start | Target + Input + Boden | **rohe C0B** (Cache-Umgehung) |

**Reihenfolgekonflikt:** `Sprint` (EventUpdate) läuft **nach** `WTap2.Legit` (AttackEvent liegt früher) und setzt Sprint wieder true; außerdem läuft `Scaffold` (PRE) **nach** `Sprint`. Deshalb:

- `Scaffold` kann Sprint zuverlässig unterdrücken, solange der Plan gültig ist.
- `WTap2.Legit` kann Sprint **nicht** unterdrücken, wenn `Sprint` aktiv ist.
- `MoreKB.Packet` kann den Cache desynchronisieren.

**Erforderlicher Vertrag:** ein `SprintController` mit Requests (Quelle, Priorität, TTL), ein Resolver nach allen PRE-Requests, ein C0B-Sync. Details in `MOV_HANDOFF_CORE.md`, WP3.

### 2.3 Sneak

| Quelle | Phase | Wirkung | Risiko |
|---|---|---|---|
| Physische Taste | Input | `movementInput.sneak` | Basis |
| `AutoBridger` | EventUpdate | `keyBindSneak.pressed = playerOverAir()` | überschreibt physisch; `onDisable=false` |
| `Scaffold.Downwards` | Input (nach `updatePlayerMoveState`) | `input.sneak=false`, `moveForward/Strafe /= 0.3` | nutzt vorherigen Plan; globale Statics |
| `BridgeAssist.onSneak` | PRE/POST | liest `isSneaking()` | indirekt durch AutoBridger beeinflusst |

**Kollision:** AutoBridger setzt `pressed=true`, obwohl der Nutzer nicht sneakt. `Scaffold.applyDownwardsInput` liest `keyBindSneak.isKeyDown()` und hält den Downwards-Pfad für aktiv, wenn `Downwards` an ist und ein gültiger `currentTarget` existiert. Damit kann AutoBridger den Downwards-Input aktivieren, ohne dass der Nutzer sneakt.

**Erforderlicher Vertrag:** `SneakLease` (Quelle, Priorität, TTL); physische Taste bleibt Default; kein Modul setzt `pressed` direkt; `Scaffold` liest die **physische** Taste, nicht den synthetischen Zustand.

### 2.4 Jump

| Quelle | Phase | Bedingung | Wirkung |
|---|---|---|---|
| Vanilla | Input | Sprungtaste | `jump()` |
| `Speed` | PRE | Modus, Boden, Input, ggf. `isSprinting` | `jump()` |
| `Scaffold.Auto Jump` | PRE | Boden, Bewegung, keine Sprungtaste | `jump()` |
| `Scaffold.Tower` | PRE | Sprungtaste, Ziel, Modus | `motionY` (kein `jump()`) |
| `Velocity.Jump` | KB PRE | Boden | `jump()` |
| `Velocity.Intave` | PRE | `reset`, `hurtTime==2`, Boden, gerade | `jump()` |
| `JumpReset` | S12 PRE | Boden | `jump()` |

**Kollision:** Mehrere `jump()` im selben Tick addieren sich nicht im vertikalen Impuls (jeder setzt `motionY = 0.42`), aber der Sprint-Impuls (`motionX/Z -=/+= 0.2`) wird bei jedem Aufruf **addiert**, wenn `isSprinting()` true ist (`EntityLivingBase` 1592–1603). Zwei Quellen können daher den horizontalen Impuls verdoppeln. Das ist der wichtigste Grund für einen zentralen Jump-Resolver.

### 2.5 MovementInput und Achsen

- `MovementInput.moveStrafe` / `moveForward` sind **static** (`MovementInput.java` 8/13). `MovementInputFromOptions` schreibt sie; `Scaffold.suppressSneak` schreibt sie; `EntityPlayerSP` kopiert sie nach `moveStrafing`/`moveForward` (776–777).
- `MoveUtil.directionYaw` und `setHorizontalMotion` verwenden die Konvention `forward = (-sin(yaw), +cos(yaw))`, `strafe` positiv = links (`MoveUtil.java` 17–25, 40–43). Das ist konsistent mit `moveFlying` (1152–1155).
- `Scaffold.suppressSneak` teilt beide Achsen durch 0.3 und setzt `sneak=false`; bei diagonalem Input wird die Diagonale erhalten, die Magnitude steigt auf bis zu 1.0. Das ist beabsichtigt (Downwards), aber es ist ein **globaler** Eingriff in die statischen Felder.

**Risiko:** Jede zweite `MovementInput`-Instanz (Test, Fahrzeug, Freecam, zukünftige Multi-Player-Ansicht) teilt den Zustand. Der Einzelspielerfall ist nur deshalb stabil, weil genau eine Instanz pro Tick schreibt.

### 2.6 Motion

| Writer | Phase | Art | MoveFix-Interaktion |
|---|---|---|---|
| `Entity.moveFlying` | Movement | additive Beschleunigung | MoveFix transformiert Input/Yaw vor der Addition |
| `EntityLivingBase.jump` | Jump | vertikaler Impuls + Sprint-Impuls | `JumpFixEvent` transformiert Yaw |
| `Speed` | PRE | absolute horizontale Geschwindigkeit (`MoveUtil.strafe`) | nutzt `RotationCorrections.directMotionYaw`; überschreibt die zuvor berechnete Bewegung |
| `Velocity` | Attack/PRE | multiplikativ/null | keine MoveFix-Beteiligung |
| `TowerController` | PRE | `motionY` | keine MoveFix-Beteiligung |
| `MoveUtil.setSpeedFromInput` | tot (`applyMotion`) | absolute Geschwindigkeit | würde statische Inputs lesen; **nicht aktiv** |

**Wichtig:** `Speed` und `MoveUtil.strafe` setzen die horizontale Motion **absolut** in PRE, also nach dem MoveFix-korrigierten `moveFlying` des gerade beendeten Schritts. Für den **nächsten** Schritt ist die Richtung aus `confirmedDirectionRadians` konsistent mit MoveFix (`PRECISE` = Kamera, `SIMPLE` = Oktant, `ROTATION_BOUND` = Owner-Basis). Für den **aktuellen** Schritt wurde die MoveFix-Korrektur jedoch bereits angewendet und dann von Speed überschrieben; die C03-Position enthält die MoveFix-Bewegung, die nächste Bewegung die Speed-Motion. Das ist kein Formelfehler, aber ein Phasen-/Konsistenzthema (MOV-W01-001/005).

### 2.7 `speedInAir` / `jumpMovementFactor`

- `Speed` setzt `speedInAir` in BMC (`118`) und im Reset (`237`); `Speed` setzt `jumpMovementFactor` im Reset (`236`).
- `EntityPlayer.updateEntityActionState` setzt `jumpMovementFactor = speedInAir` und addiert bei Sprint `speedInAir * 0.3` (`EntityPlayer.java` 628–632).
- `EntityLivingBase` nutzt `jumpMovementFactor` in der Luftbewegung (`1649`).
- **Risiko:** Ein Moduswechsel von BMC zu Intave/Polar/Vulcan lässt `speedInAir = 0.03` stehen; der nächste Reset-Pfad setzt 0.02. Kein `onValueChange`. **P3.**

### 2.8 Rotation

| Aspekt | Ist | Beleg |
|---|---|---|
| Owner | `RotationManager.owner`, nur bei exaktem Match | 102–117 |
| Request | Priorität 10/20/30; Tie lexikografisch | 70–83 |
| Sichtbar | `resolve` addiert Kameradelta; GUI/Fokus/RenderView-Guard | 85–100 |
| PRE-Basis | `previousAccepted` | 130–134 |
| POST-Basis | `accepted` | 130–134 |
| Effektiv | `observeOutboundRotation` beim C03 | `NetworkManager` 176; `NetHandler` 877 |
| Freigabe | `reset(source)` entfernt Owner, lässt Basis | 297–304 |
| Scaffold | `releasePlacement` ruft `reset("scaffold")` bei Erfolg/Planende | `Scaffold` 127–135/175/183 |
| BridgeAssist | schreibt Kamera direkt, kein Owner | 102–111 |

**Kerninkonsistenz:** `canUseRotation(pre)` (Platzierung) und `getConfirmedOwner()` (MoveFix) sind nach `reset("scaffold")` unterschiedlich. `canUseRotation` kann true bleiben, während MoveFix keinen Owner sieht. Das ist die mechanische Lücke in MOV-W01-004.

### 2.9 Pakete und Caches

| Paket | Schreiber | Phase | Cache | Risiko |
|---|---|---|---|---|
| C0B START/STOP | Vanilla | PRE→C03 | `serverSprintState` | korrekt bei einem Writer |
| C0B START/STOP | `MoreKB` | EventUpdate | **umgangen** | Desync (P2) |
| C0B START/STOP | `Scaffold`/`WTap2` | PRE | Vanilla-Sync | korrekt, aber Reihenfolge |
| C0B START/STOP SNEAK | Vanilla | PRE→C03 | `serverSneakState` | AutoBridger beeinflusst |
| C07/C08/C09 | `NoSlow` | PRE/POST | keiner | Spam (P3) |
| C08 Place | `ScaffoldPlacementService` | PRE/POST | Delay/Sequence | Owner/Plan |
| C03 | Vanilla | PRE | `lastReported*`, `observeOutboundRotation` | Basis |

**Wichtig:** `MoreKB` sendet C0B über `PacketUtils`, ohne `serverSprintState` zu aktualisieren. Der nächste Vanilla-Sync vergleicht `isSprinting()` mit dem **alten** Cache und kann eine notwendige Korrektur unterlassen.

### 2.10 Tickbase und simulierte Schritte

- `Tickbase.runPlayerStep` ruft `budget.step(canCharge, validStep, simulation)`; bei Release kann `simulation` mehrfach laufen (`Tickbase` 84–104; `EntityPlayerSP` 171/175).
- Jeder simulierte Schritt postet `EventUpdate`, führt `super.onUpdate()` und `onUpdateWalkingPlayer()` aus.
- `Speed`-Zähler (`groundTicks`, `offGroundTicks`, `polarAirTicks`) sind per Schritt, nicht per Client-Tick.
- `MoreKB`/`NoSlow`/`WTap2` senden/keys per Schritt.
- `Tickbase.usable()` verlangt Timer 1.0 (`130`), GUI zu, Fokus, lebend, nicht reitend, `hurtTime <= 2`.

**Risiko:** Ein Release kann mehrere Boden-/Luftticks, Pakete und Key-Writes in einem Frame ausführen. Es gibt keinen `StepContext`, der „simuliert“ vs. „Client-Tick“ unterscheidet. Kein Test deckt Speed/WTap/MoreKB unter Release ab.

### 2.11 Lifecycle

| Modul | Reset bei Welt/Death/Respawn? | Beleg |
|---|---|---|
| Scaffold | ja (`WorldEvent` 189; `validContext` 146) | A |
| Aura | ja (`WorldEvent` 133; Tick 127) | A |
| Tickbase | ja (`WorldEvent` 171; `bindContext` 107–122) | A |
| Speed | **nein** | keine Listener |
| Sprint | **nein** (kein Zustand) | — |
| Velocity | **nein** | keine Listener |
| NoSlow | **nein** | keine Listener |
| WTap2 | **nein** | keine Listener |
| MoreKB | **nein** | keine Listener |
| JumpReset | **nein** | keine Listener |
| AutoBridger | **nein** | keine Listener |
| BridgeAssist | **nein** | keine Listener |

**Folge:** `Speed.groundTicks`, `Velocity.reset/intaveTick/timerTicks`, `NoSlow.usingItemLastTick`, `WTap2.releaseAt`, `BridgeAssist.gliding`, `MoreKB.target` können in den neuen Kontext übergehen.

---

## 3. Reihenfolgeabhängigkeit — konkrete Szenarien

### S1 — Scaffold vs. Speed (Timer)

Beide sind `EventMotion`-PRE-Listener. Die Reihenfolge ist die Subscribe-/Enable-Reihenfolge; es gibt keine Prioritätsangabe.

- **Scaffold zuerst:** `timerControl.apply(1.0, 1.03)` → 1.03; Speed schreibt 1.0 → Timer 1.0. Nächster Tick: Scaffold sieht `current (1.0) != written (1.03)` → `blocked`; kein Scaffold-Timer mehr.
- **Speed zuerst:** Speed schreibt 1.0; Scaffold `apply(1.0, 1.03)` → 1.03; Timer 1.03. Nächster Tick: Speed schreibt 1.0; Scaffold erkennt und blockt.

In beiden Fällen ist der Scaffold-Timer nach einem Tick aus; nur der Zeitpunkt unterscheidet sich. **P1.**

### S2 — Sprint vs. WTap2

- `AttackEvent` (WTap2 Legit) setzt `keyBindSprint.pressed=false`, `keyBindForward.pressed=false`.
- `EventUpdate` (Sprint) prüft `moveForward` (noch der alte Wert? Der Angriff liegt vor dem Input-Update; `moveForward` ist der letzte Tick) und setzt `setSprinting(true)`.
- Vanilla entscheidet danach; `Scaffold` kann in PRE erneut stoppen.
- Ergebnis: WTap2 Legit ist mit Sprint aktiv wirkungslos; ohne Sprint wirkt es, bis Vanilla/Scaffold eingreift. **P1.**

### S3 — Scaffold vs. Speed (Sprint-Basis)

- `Scaffold` setzt in PRE `setSprinting(false)`.
- `Speed` liest `isSprinting()` in `getModeBaseSpeed` und in Intave (`84`), BMC (nur `MoveUtil`), Vulcan/Vulcan2.
- Wenn Speed **nach** Scaffold läuft: Walk-Basis (`0.221`), Intave springt nicht, weil `isSprinting()` false.
- Wenn Speed **vor** Scaffold läuft: Sprint-Basis (`0.2873`), Intave springt und boostet; Scaffold stoppt Sprint erst danach.
- Ergebnis: Die effektive Speed-Bewegung hängt von der Enable-Reihenfolge ab. **P1/P2.**

### S4 — AutoBridger vs. Scaffold (Sneak)

- `AutoBridger` setzt `keyBindSneak.pressed = playerOverAir()` in `EventUpdate`.
- `Scaffold.applyDownwardsInput` läuft in `onLivingUpdate` (nach `updatePlayerMoveState`) und liest `keyBindSneak.isKeyDown()`.
- Wenn AutoBridger über Luft true setzt und `Downwards` an ist, hält Scaffold den Downwards-Pfad für aktiv, obwohl der Nutzer nicht sneakt.
- `AutoBridger` beeinflusst außerdem `BridgeAssist.onSneak` über `isSneaking()`.
- **P1.**

### S5 — BridgeAssist vs. RotationManager

- `RotationManager.beginMotion` liest die Kamera zu Beginn von PRE.
- `BridgeAssist` kann in PRE die Kamera schreiben; `resolve` sieht die geänderte Kamera.
- `RotationManager` berechnet die sichtbare Differenz zur Kamera; BridgeAssist kann sie danach in POST erneut ändern.
- Wenn Aura/Scaffold Owner ist, ist BridgeAssist kein Owner, schreibt aber trotzdem die Kamera; die nächste `beginMotion`-Basis ist die effektive Owner-Rotation, nicht die Kamera.
- Ergebnis: sichtbare Oszillation/Doppelbewegung; MoveFix sieht keinen BridgeAssist-Owner. **P1.**

### S6 — MoreKB vs. Vanilla-Sprint-Cache

- `MoreKB` sendet in `EventUpdate` STOP+START.
- `EntityPlayerSP.onUpdateWalkingPlayer` vergleicht `isSprinting()` mit `serverSprintState`; der Cache wurde nicht geändert.
- Wenn der Client weiter sprintet, sendet Vanilla kein START; der Server hat aber STOP/START gesehen.
- Nächste echte Änderung kann unterdrückt werden.
- **P2.**

### S7 — Tickbase-Release vs. per-Tick-Zähler

- `Tickbase` ruft `simulation` mehrfach auf.
- `Speed` erhöht `groundTicks`/`offGroundTicks` pro Simulation.
- `MoreKB` sendet pro Simulation.
- `WTap2` nutzt `System.currentTimeMillis`, also Wanduhr; das Fenster kann während eines Releases mehrfach Key-Writes auslösen.
- **P2.**

---

## 4. Paarweise Konfliktmatrix

Legende: **T** Timer, **S** Sprint, **K** Keys, **I** Input, **M** Motion, **R** Rotation, **P** Paket/Cache, **L** Lifecycle, **J** Jump.

| A \ B | Speed | Sprint | Velocity | NoSlow | WTap2 | MoreKB | JumpReset | AutoBridger | BridgeAssist | Scaffold |
|---|---|---|---|---|---|---|---|---|---|---|
| **Speed** | — | S, M | T, M, J | S, I | S, K | P, S | J | I | R, M | T, S, R, J |
| **Sprint** | S, M | — | S, J | S | **S, K** | P, S | J | I | R | S |
| **Velocity** | T, M, J | S, J | — | — | S | P | J | — | R | T, P |
| **NoSlow** | S, I | S | — | — | S, K | P | — | I | R | S, I |
| **WTap2** | S, K | **S, K** | S | S, K | — | P, S | J | K, I | R | S, K |
| **MoreKB** | P, S | P, S | P | P | P, S | — | J | I | R | P, S |
| **JumpReset** | J | J | J | — | J | J | — | — | — | J |
| **AutoBridger** | I | I | — | I | K, I | I | — | — | I, R | **I, K** |
| **BridgeAssist** | R, M | R | R | R | R | R | — | I, R | — | **R, I** |
| **Scaffold** | **T, S, R, J** | S | **T, P** | S, I | S, K | P, S | J | **I, K** | **R, I** | — |

**Die vier kritischsten Paare:**

1. **Speed × Scaffold:** Timer, Sprint-Basis, Rotation, Jump.
2. **Sprint × WTap2:** Sprintstatus und Key-Zustand.
3. **AutoBridger × Scaffold:** Sneak, G-Taste, Downwards.
4. **BridgeAssist × Scaffold/Aura:** Kamera und Owner.

---

## 5. Zielvertrag (Soll, noch nicht implementiert)

| Zustand | Soll-Owner | Erlaubte Writer | Leser | Reset | Verstoß heute |
|---|---|---|---|---|---|
| `timerSpeed` | `TimerLease` | nur Lease-Inhaber | Timer, Tickbase | eigener Token; Welt/Disable | 3 direkte Writer, Wertvergleich |
| Sprint | `SprintController` | Resolver | Vanilla-Sync, Module | pro Tick; TTL | 5 Quellen, direkte Writes |
| Sneak | `SneakLease` | Lease-Inhaber | Input, Scaffold, BridgeAssist | eigene Freigabe | AutoBridger direkt |
| Jump | `JumpResolver` | Resolver | `EntityLivingBase.jump` | pro Tick | 6 Quellen |
| `MovementInput` | eine Instanz pro Spieler | Vanilla/Scaffold über Instanz | `EntityPlayerSP`, MoveUtil | jeden Tick | static |
| Kamera-Rotation | `RotationManager` | Owner-Request | C03, Render, MoveFix | `finishMotion`/Owner-Reset | BridgeAssist direkt |
| `MoveFix`-Basis | `RotationManager` | akzeptierter Owner | `RotationCorrections` | nur Owner-Reset | Scaffold released pro Platzierung |
| Paket-/Sprint-Cache | Vanilla | nur Vanilla/`SprintController` | C0B | Zustandswechsel | MoreKB roh |
| Schritte | `StepContext` | Tickbase | Module | pro Frame | kein StepContext |
| Lifecycle | `MovementLifecycle` | Welt/Death/Respawn | Module | zentral | viele Module ohne Reset |
| Suspension | `SuspensionGate` | GUI/Fokus/Pause | alle Module | Kontextwechsel | fast alle Module ohne Guard |

**Wichtig:** Das ist ein Zielbild, keine W01-Implementierung. Die Reihenfolge und die Abhängigkeiten stehen in `MOV_HANDOFF_CORE.md`.

---

## 6. Abgleich mit den Befunden

| Matrixzeile | Finding |
|---|---|
| Timer (3 Writer, Wert-Lease) | MOV-W01-003 |
| Sprint (5 Quellen) | MOV-W01-006 |
| Sneak (AutoBridger) | MOV-W01-007 |
| Kamera (BridgeAssist) | MOV-W01-008 |
| Rotation-Freigabe (Scaffold) | MOV-W01-004 |
| `MovementInput` static | MOV-W01-012 |
| Motion/Phase | MOV-W01-001, -005 |
| `speedInAir`/Mode-Wechsel | MOV-W01-017 |
| Pakete/Cache | MOV-W01-010, -018 |
| Tickbase | MOV-W01-014 |
| Lifecycle | MOV-W01-013 |
| Suspension | MOV-W01-002 |

---

## 7. Test- und Evidenzstatus dieser Matrix

- **A** für alle Codezeilen; die Matrix ist aus dem HEAD abgeleitet.
- **B** für die genannten bestandenen Tests (50 gezielt, 396 gesamt).
- **C** für Testcode, der die Matrix stützt, aber die konkrete Kombination nicht abdeckt (z. B. `SprintOrderingChecks` für Scaffold, nicht für WTap2).
- **E** für alle Live-/Server-/AC-Aussagen.
- **Nicht geprüft:** Interleavings der Timer-Writer, GUI-Suspension, Weltwechsel-Resets, `MovementInput`-Instanzisolation, Tickbase × Speed/WTap/MoreKB.

---

## 8. Offene Entscheidungen (Matrix-spezifisch)

| ID | Frage | Optionen | Empfehlung |
|---|---|---|---|
| M1 | Wer darf `timerSpeed` besitzen? | Scaffold > Speed > Velocity; Speed > Scaffold; Delta-Budget | Scaffold > Speed > Velocity, Lease mit Token |
| M2 | Darf ein Modul `keyBind*.pressed` schreiben? | nein, nur Lease; ja, aber mit Restore | nein; Lease/Controller |
| M3 | Bleibt `MovementInput` static? | ja, dokumentieren; nein, Instanzfelder | nein (Vanilla), breite mechanische Welle |
| M4 | Bekommt BridgeAssist einen Owner? | nein, rein visuell; ja, `requestVisible` | ja, `requestVisible` mit Priorität, PRE-only |
| M5 | Wird Scaffold-Owner pro Platzierung gehalten? | ja (Spec); nein (Ist) | ja; Plan-Reset vs. Owner-Release trennen |
| M6 | Wie werden simulierte Schritte markiert? | StepContext; Client-Tick-Dedupe; Hybrid | Hybrid (Bewegung pro Schritt, Außenwirkung pro Tick) |
| M7 | Wird `Tickbase` auf Timer 1.0 festgelegt? | ja; Lease-basiert; Delta erlauben | Lease-basiert, nicht Wertvergleich |

---

## 9. Kurzfazit

Die Matrix zeigt **keinen einzigen zentralen Bewegungs-Owner**. Jeder Zustand hat mehrere direkte Schreiber; die Reihenfolge ist die Subscribe-/Enable-Reihenfolge. Die drei gefährlichsten globalen Zustände sind:

1. **`timerSpeed`** (drei Module, Wertvergleich statt Token),
2. **Sprint** (fünf Quellen, inkl. roher C0B),
3. **Kamera/Rotation** (RotationManager für Aura/Scaffold, BridgeAssist daneben, Scaffold released pro Platzierung).

Die MoveFix-Formel ist davon **nicht** betroffen; sie ist korrekt. Betroffen ist die **Basis**, auf der sie arbeitet: Owner, Phase, Input und Timer.
