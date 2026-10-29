# CORE-I01c — Action-Consumer und negative Abdeckung

Geprüfte lokale Basis: `6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666`,
`codex/neutron-core-i01b`; Implementierung nur in `F:\neutron-core-i01c`.
Auftrag I01c ist die Änderungsfreigabe. Zusatzquellen, insbesondere v1.0 und
RV-01–12, sind Kontext; ihre weitergehenden Produktziele erweitern diese Freigabe nicht.
Diese Matrix wurde vor Produktionsänderungen aus dem lokalen Callgraph erarbeitet.

## Tatsächliche Ereignisgrenzen

`Minecraft.runTick` beginnt den Outer-Tick und beide vorhandenen Fachbudgets.
`TickEvent` folgt außerhalb eines Player-Bodys einmal pro tatsächlichem runTick.
`EntityPlayerSP.onUpdate` delegiert über `Tickbase.runPlayerStep` an den Observer:
im wirklich betretenen `runLocalPlayerStep` liegt `EventUpdate` vor Cancellation
und Physik; danach `EventMotion PRE`, C0B, Rotation-Resolve, C03,
`finishMotion`, `EventMotion POST`. Ein Extra wiederholt den Player-Body;
Withheld betritt ihn überhaupt nicht. Maus-/GUI-Controlleraktionen liegen nicht
notwendig im Body. `getLastSteps` und `getLastWithheld` sind ausschließlich Diagnose.

## Consumer-Matrix

O = aktuelles ORDINARY mit frisch ACTIVE/identischer Scene und aktueller Outer-ID;
E = EARNED_EXTRA; W = WITHHELD (kein Body/Step). „Bestand“ bedeutet unverändert,
keine neue Zusicherung, dass dieser Pfad bereits die Produktrechte erfüllt.
Pfadwurzeln: `src/main/java/me/youngjibbit/module/impl/`, sofern nicht anders angegeben.

| Consumer / tatsächlicher Aufrufpfad | Phase / Kategorie | O / E / W nach I01c | Manuelle Beteiligung | Bestehendes Budget / Guard | Freigegebener Schnitt / Deferred-Grund |
| --- | --- | --- | --- | --- | --- |
| `combat/Aura.onMotion` → `AttackScheduler.tryAttack` → `PlayerControllerMP.tryAttackEntity` → C02 | Motion POST, ATTACK | O nur Vorprüfung; E kein Scheduler-Versuch; W kein Listener | automatisches Targeting; Controller/AttackEvent auch manuell benutzt | CPS, Tick, Motion, Owner, C03-Acceptance, Raycast, finaler Controller-/Packet-Callback | Gate vor POST-Versuch und erneut in `validHit`; PRE-Targeting/Rotation bleiben aktiv |
| Aura → `AutoblockController.update/beforeAttack` → C07/C08 | Motion PRE/POST, zugehörige ITEMUSE-Nebenwirkung | O bestehende Logik; E keine PRE-/POST-Autoblock-Writes; W kein Motion | `onAttack` reagiert auch auf manuelle AttackEvent; Lifecycle-Release bleibt | owned/manualUse/playerReady, Modus, Tick | Nur Motion-Nebenwirkungen im Piloten; `onAttack`, Disable-/World-/Tick-Cleanup bleiben Bestand; keine generelle Autoblock-/Input-Reparatur |
| `ghost/AutoClicker2.onUpdate` → `mc.clickMouse` | EventUpdate, ATTACK (Picking kann auch Mining auslösen) | O bisherige CPS/Timer; E kein Klick, kein Timerverbrauch oder künstlicher Use-Key-Write; W kein Body | physisch gehaltene Attacktaste ist Voraussetzung, generierter Klick ist synthetisch | Timer/CPS, clickMouse-/Controller- und Scheduler-Guards | Admission vor Klick und unmittelbar zugehörigen Key-Eingriffen; CPS-Verteilungen/Settings unverändert |
| `ghost/HitDelayFix.onUpdate` → `Wrapper.mc.clickMouse` | EventUpdate PRE, ATTACK | O bisheriger Klick; E kein synthetischer Klick; W kein Body | physische Attacktaste + Entity-Picking nötig; Client-HitDelay-Eingriff im manuellen clickMouse bleibt | Picking, Player, Key, bestehender Controller | Nur Listener-Aufruf gated; keine Minecraft-Änderung |
| `world/Scaffold.onMotionEvent` → `tryPlace(true)` → `ScaffoldPlacementService.place` | Motion PRE, PLACEMENT | O Fachservice entscheidet; E false vor Service/Slot/Diagnose; W kein Body | automatischer Plan; physische Jump-/Sneakzustände fließen ein | Delay, Tick/Motion, Slot/Stack, World/Face/Raycast, alter akzeptierter Winkel | PRE bleibt **vor** aktueller Rotationsanforderung/Resolve/C03; Lifecycle/`releasePlacement` unverändert |
| Scaffold → `tryPlace(false)` → gleicher Service | Motion POST, PLACEMENT | O Fachservice entscheidet; E kein Versuch; W kein Body | wie PRE | identischer PRE-Plan/Sequence, aktuelle akzeptierte Rotation, gleiche Fachbudgets | Ein gemeinsamer Gate am Anfang `tryPlace`; kein Erfolgs-/Budgetsignal bei Denial |
| `player/AutoHeal.onTick` → `useHealingItem` → Slot, sync, guarded C08 | TickEvent außerhalb Body, ITEMUSE/SLOT | O/E keine Body-Zuordnung; W kann TickEvent haben; Bestand | kein manueller Use-Interceptor; kein Cursorstack/GUI/Itemuse zulässig | Identität, generation, reentrancy, 1s-Cadence, Stack-/Slot- und Packet-Guard, finally-Restore | **DEFERRED**: braucht eigene Outer-Tick-/MOD-Policy; Ordinary-only würde Funktion abschalten |
| `AutoHealInventoryTransfer.begin` → `windowClickGuarded`, ACK/Resync | TickEvent + Packet-/World-Listener, INVENTORY | Bestand auch ohne Step | Vanilla `windowClick` bleibt unabhängig | Pending-Transfer, Original/Prediction, ACK/Resync/Timeout und Context | **DEFERRED**: Transaktionsvertrag getrennt testen |
| `player/InventoryManager.onUpdate` → Trash/Armor/Sort → `windowClick`, C0B open inventory | EventUpdate mit `GuiInventory`, INVENTORY | O/E Bestand, Scene SUSPENDED; W kein Update; Enable-Zweig separat | GUI ist ausdrücklich Voraussetzung; Controller auch manuell | Start-/Actiondelay, onlyStill/openInv, Slot-/Stack-Rechecks | **DEFERRED**: GUI-kompatible MOD-/Outer-Tick-Rechte nötig; keine globale Suspension-Sperre |
| `player/Stealer.onUpdate` → Chest-`windowClick`, `closeScreen` | EventUpdate, INVENTORY/GUI | O/E Bestand; GuiChest suspendiert; W kein Update | Chest-/Container-Kontext, AutoClose | openDelay/actionTimer, Slotliste/Container, vorhandener Controller | **DEFERRED**: GUI-Transaktionen/AutoClose separat; Pipeline weiterhin nicht vollständig geschützt |
| `player/AutoTool.motion` → `inventory.currentItem`; Tick/Lifecycle → restore/discard | Motion PRE + TickEvent/World, SLOT | O/E Auswahl Bestand; W Tick-Cleanup möglich | physisches Mining; manuelle Hotbarauswahl gewinnt | selected/originalSlot, identity, manualOverride, canMine | **DEFERRED**: eigener Slot-/Gesturevertrag, Restore nicht durch Ordinary-Guard blockieren |
| `player/SlotHandler.onPreUpdate` → `currentItem`/silent-slot cooldown | EventUpdate PRE, SLOT | O/E Bestand; W kein Update | gemeinsame Slotansicht, Controller/Render-Consumers | gültiger Slot, CoolDown, currentSlot | **DEFERRED**: MOD-Slot-/Spoofvertrag; kein Piloteingriff |
| `player/FastPlace` → `rightClickDelayTimer=0` | Enable + EventUpdate, DELAY | O/E Bestand; W kein Update | beeinflusst normalen manuellen Rightclickpfad | eigenes Toggle, Vanilla-Delay sonst | **DEFERRED**: Delay-/Inputwirkung statt Endaktion; kein neues Placement-Gate |
| `combat/MoreKB` AttackEvent merkt Target; Update → C0B STOP/START oder Sprinttimer | AttackEvent + EventUpdate, SPRINT/PACKET | O/E Bestand; W kein Update | AttackEvent kann manuell sein | target/onlyGround/movement; Target wird nach Update gelöscht | **DEFERRED**: MOV-/NET-Sprintvertrag; C0B-Pipeline ausdrücklich nicht vollständig geschützt |
| `player/NoSlow.onMotion` → C07/C08 oder zwei C09 | Motion PRE/POST, ITEMUSE/SLOT | O/E Bestand; W kein Motion | aktiver physischer Itemuse, Slowdown/Sprint | Modus/Stack/usingItemLastTick | **DEFERRED**: Item-/Slot-/Slowdown-Fachvertrag; kein vollständiger C08-Schutz beansprucht |
| `ghost/WTap2` → Key-/Sprintzustand, Spoof-Sprint | AttackEvent, TickEvent, Motion PRE, INPUT/SPRINT | O/E/äußerer Tick Bestand; W Tick-Cleanup | physische Tasten/AttackEvent beteiligt | mode, target, gesture/resume guards | **DEFERRED**: eigener MOV-Input-/Sprintvertrag |
| `combat/Velocity` → Bewegungs-/Timerwerte, `checkAir` C03/C07 + lokaler Blockabbau | Attack/Knockback/Tick/Motion, MOV/PACKET | Bestand | Treffer-/Bewegungskontext | jeweiliger Modus/Context | **DEFERRED**: außerhalb Attack-/Placement-Endaktionen |
| `ghost/AutoBridger`, `world/BridgeAssist` und sonstige Movementlistener | Update/Motion, INPUT/ROTATION | Bestand | Sneak-/Kamera-/Movementbasis | bestehende Modulbedingungen | **DEFERRED**: keine neue Input-/Owner-/Timerwelle; kein Endaktions-Consumer dieses Piloten |
| `Minecraft.clickMouse` → `PlayerControllerMP.attackEntity/clickBlock` | äußeres Inputsampling/manueller Aufruf, auch ohne Step | unverändert O/E/W/kein Step | physisch/manuell | vorhandener HitDelay, Picking, Aura-Scheduler-/Controller-Guard | **MANUAL EXCLUDED**: kein Interceptor |
| `PlayerControllerMP.windowClick` und GUI-Aufrufer | GUI/Input, auch ohne Step | unverändert, auch SUSPENDED | manuell | Container-Transaktion, vorhandener Packetpfad | **MANUAL EXCLUDED**: kein globales Eligibility-Gate |
| `Minecraft.rightClickMouse` → Itemuse/Placement/Interact, Hotbar-/Scroll-/GUI-Slotauswahl | äußerer Input/GUI | unverändert, kein Step erforderlich | manuell | Vanilla-/Controller-/Slot-/Packetguards | **MANUAL EXCLUDED**: Pilotscope ausschließlich Modul-Endaktionen |

## Negative Abdeckung und eigenständige Teststrategie

Die grep-/rg-Sichtung umfasst alle direkten `clickMouse`, `tryAttackEntity`,
`attackEntity`, `windowClick`, `windowClickGuarded`, C02/C08/C09 und `currentItem`
Writes in den Modulquellen sowie ihre konkreten Listener und Controllerziele.
`PacketUtils` enthält weitere allgemeine C02/C07/C08/C0B-Helfer; die untersuchten
Modulquellen rufen deren `attackFakePlayer`/`sendInteract` nicht auf. Helfer sind
keine pauschal migrierten automatischen Consumer. Allgemeine Packet-Sender bleiben offen.

AutoHeal: TickEvent ohne Body, Withheld, Cooldown/Slot-Restore, Callback-Contextwechsel,
Transfer-ACK/Reject/Timeout jeweils separat testen. InventoryManager/Stealer:
GuiInventory/GuiChest trotz SUSPENDED, wiederholte Extras im selben Outer-Tick,
Delay, manuelle konkurrierende Klicks, Window-/Slot-/Stackwechsel und AutoClose
brauchen eine eigene GUI-/Transaktionspolitik. Diese Fälle werden hier weder
als migriert noch als neu vollständig getestet ausgegeben.
AutoTool/SlotHandler brauchen Mining-/Hotbarpriorität und sicheres Restore bei
Withheld/Worldwechsel; FastPlace Input-/Delaytests; MoreKB/WTap2/NoSlow benötigen
C0B/C07/C08/C09-Traces einschließlich manuellem Input und Extra-Steps.
Bestehende Regressionstests bleiben erforderlich, ersetzen diese Fachabnahmen aber nicht.

I01c schützt folglich vier ausgewählte Endaktionsquellen. Es ist keine globale
ActionDomain-Reservation und keine Fertigstellung der NET-/MOD-/MOV-Pipeline.
