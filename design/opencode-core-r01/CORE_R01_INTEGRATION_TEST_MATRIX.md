# CORE-R01 — Entscheidungen, Finding-Disposition und Testmatrix

**Status:** Architektur-/Integrationsplanung, **keine Implementierung, kein Testlauf**  
**Datum:** 2026-10-28  
**Branch/HEAD:** `prep/neutron-opencode-wave00` / `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`

---

## 1. Entscheidungslog

### 1.1 Bereits bindend

Diese Punkte sind durch Spezifikation oder W01-Vertrag so weit entschieden, dass CORE sie als Invarianten führen darf:

| Entscheidung | Quelle | CORE-Konsequenz |
|---|---|---|
| Scaffold hält die Rotation bis Disable; Planende ist keine Ownerfreigabe | Spec 3.1, C4/M5/D3 | getrennte `resetTrajectory`/`releaseOwner`-Semantik |
| Tickbase-Extras dürfen Bewegung/Rotation vervielfachen, aber keine Außenwirkung pro Extra | M6/D10 | `StepContext` + `ActionDomain`-Gate |
| Physische Eingabe ist Standard; Overrides nur bei gültiger, aktiver Aktion | Spec 4 | Input-Basis + TTL-Intents |
| GUI/Pause/Fokus/Tod/Weltwechsel suspendieren neue Aktionen | Spec 3/4; W01-002 | zentrale Suspension |
| Bestehende Configs und Legacy-Aliase bleiben lesbar | W00/MOD-011 | Append-only-Migration |
| Numerische CMB-/SCF-Werte brauchen Nutzerfreigabe | G-CMB-SPEED/G-SCF-ANGLES | keine geratenen 4/8-Werte |

### 1.2 Nicht bindend

C1/D1, C2/M1/D2/M7, C3/M2/D5, C5/M3, M4, D4, D6, D7/D8 und die D9-Migration sind **offen**. Die Empfehlungen in `CORE_R01_ROLLOUT_AND_DECISIONS.md` § 3 sind Architekturvorschläge, keine Freigaben.

---

## 2. W01-Findings: Disposition in CORE

Legende: **V** = Vertrag/API, **M** = Modulmigration, **T** = Test, **G** = Gate. Die Welle verweist auf den Plan in `CORE_R01_ROLLOUT_AND_DECISIONS.md` § 4.

| Finding | W01-Status | Evidenz | Prio | Disposition | Paket | CORE-Bezug | Welle | Gate/Status |
|---|---|---|---|---|---|---|---|---|
| **MOV-W01-001** PRE ist Nach-Bewegung | bestätigt (Code); nicht geprüft (Live) | A, C, E | P1 | **V + T** | CORE | `StepContext`, Phase-Doku, kein neuer Hook | CORE-0/1 | C1/D1 offen |
| **MOV-W01-002** GUI-/Fokus-/Pause-Guard fehlt | bestätigt (Code); nicht geprüft (Live) | A, E | P1 | **V + M + T** | CORE/MOV | zentrale Suspension in `MovementContext` | CORE-1, MOV-2 | – |
| **MOV-W01-003** `timerSpeed` ohne globalen Owner | bestätigt (Code); nicht geprüft (Interleaving) | A, C, E | P1 | **V + M + T** | CORE/MOV/CMB | `TimerArbiter` | MOV-2 | C2/M1/D2 offen |
| **MOV-W01-004** Scaffold gibt Owner pro Platzierung frei | bestätigt (Code); nicht geprüft (Spielwirkung) | A, C, E | P1 | **V + M + T** | SCF/CMB | `canUseRotation(String,pre)`, Plan/Owner-Trennung | CMB-1, SCF-1/2 | C4/M5/D3 bindend |
| **MOV-W01-005** Speed-Basis/Potion | bestätigt (Code/Test); nicht geprüft (Server) | A, B, E | P1 | **M + T + G** | MOV | keine CORE-Formel; D4 blockiert | MOV-2 | G-CMB-SPEED |
| **MOV-W01-006** Sprint-Besitz | bestätigt (Code/Test); nicht geprüft (Kombination) | A, B, E | P1 | **V + M + T** | MOV/CORE | `InputResolver` | MOV-2 | C3/M2/D5 offen |
| **MOV-W01-007** AutoBridger-Sneak/G-Kollision | bestätigt (Code); nicht geprüft (Live) | A, E | P1 | **M + T** | MOV/MOD | Input-Lease; MOD-Key-Konflikt | MOV-2, MOD-1 | D6 offen |
| **MOV-W01-008** BridgeAssist umgeht RotationManager | bestätigt (Code); nicht geprüft (Kamera) | A, E | P1 | **V + M + T** | SCF/CMB | `requestVisible` | SCF-1 | M4 offen |
| **MOV-W01-009** Velocity Minemen/Verus/Chance | bestätigt (Code); Hypothese (Spielwirkung) | A, Rechnung, E | P2 | **M + T** | MOV | Timer-Lease; Modus-Disposition | MOV-2 | D7 offen |
| **MOV-W01-010** MoreKB C0B/Target/Spam | bestätigt (Code); nicht geprüft (Server) | A, E | P2 | **M + T** | MOV/NET | Packet-/Input-Vertrag, keine neue Queue | MOV-2 | – |
| **MOV-W01-011** NoSlow „Vanilla“/Modi | bestätigt (Code/Test); nicht geprüft (Paket) | A, C, E | P2 | **M + T** | MOV | Slowdown bleibt Vanilla-Consumer | MOV-2 | D8 offen |
| **MOV-W01-012** `MovementInput` statisch | bestätigt (Code); nicht geprüft (Fehlfunktion) | A, E | P2 | **V + T** | MOV | Snapshot; Instanz später | MOV-1 | C5/M3 offen |
| **MOV-W01-013** Reset bei Welt/Death/Respawn fehlt | bestätigt (Code); nicht geprüft (Live) | A, E | P2 | **V + M + T** | CORE/MOV | Lifecycle/Generation | CORE-1, MOV-2 | – |
| **MOV-W01-014** Tickbase vervielfacht Aktionen | bestätigt (Code/Test); nicht geprüft (Kombination) | A, C, E | P2 | **V + T** | NET/CORE | `StepContext` + ActionGate | NET-1 | M6/D10 bindend |
| **MOV-W01-015** Scaffold-MoveFix-Default „Aus“ | bestätigt (Code/Spec); nicht geprüft (Nutzerwirkung) | A, D, E | P2 | **M + T** | MOD/SCF | neue Profile „Präzise“ | MOD-1 | D9-Migration offen |
| **MOV-W01-016** JumpReset-Kollision | bestätigt (Code); nicht geprüft (Live) | A, E | P2 | **V + M + T** | MOV | Jump-Intent, ein Jump pro Step | MOV-2 | D5 offen |
| **MOV-W01-017** Toter Code/Overrides | bestätigt (Code) | A | P3 | **M** | MOV/SCF | erst nach Verhaltensmigration | MOV-2/SCF-2 | – |
| **MOV-W01-018** Paketspam/irreführende Namen | bestätigt (Code); nicht geprüft (Live) | A, E | P3 | **M + T** | NET/MOD | Trace, Namensdoku, keine stille Umbenennung | NET-2/MOD-1 | D7 offen |

**Wichtig:** Kein Finding wird durch CORE-R01 „behoben“. Die Tabelle ist eine Planungszuordnung.

---

## 3. Synthetische Testmatrix (Java 8, JUnit 4)

Alle Fälle sind **S** (synthetisch). Sie laufen ohne echte Minecraft-Welt, ohne Server, ohne OptiFine/Shader. „Neu“ heißt: Klasse existiert noch nicht. „Erweitern“ heißt: bestehende Klasse wird um Fälle ergänzt. R01 hat **keinen** dieser Tests ausgeführt. W00-Baseline: 396 Tests, 3 skipped, NET-001 behoben, MOD-010/011 als widerlegt geprüft; Details in `CORE_R01_ROLLOUT_AND_DECISIONS.md` § 5. Die 396/3-Zahl ist **nicht** neu von R01 ausgeführt.

| ID | Finding | Vorbedingung | Phase/Folge | Owner/Zustand | Erwartung | Testklasse | Testklasse-Status | Nachweis |
|---|---|---|---|---|---|---|---|---|
| **S01** | 001 | Zwei akzeptierte Frames, Kamera gedreht | PRE nach Bewegung, vor C03 | Rotation: vorheriger Owner | PRE nutzt `previousAccepted`, nicht den neuen Request; keine „Bewegung dieses Ticks“ | `PlayerStepHookTest` + `RotationOwnerContinuityTest` | Erweitern/neu | S |
| **S02** | 014 | Outer-Tick 42, Step 0 | Ordinary Step | ActionGate frei | `claim(ATTACK)` und `claim(PLACEMENT)` jeweils einmal wahr | `StepContextTest` | Neu | S |
| **S03** | 014 | Outer-Tick 42, Step 0 | Withheld Step | ActionGate | `claim(...)` falsch; keine Außenwirkung | `StepContextTest` | Neu | S |
| **S04** | 014 | Outer-Tick 42, Step 0, Earned Step 1 | Earned Extra | ActionGate | Bewegung/Rotation erlaubt, `claim(...)` falsch | `StepContextTest` | Neu | S |
| **S05** | 014 | Ordinary + Earned im selben Outer-Tick | Step 0 → Step 1 | ActionGate | erste Aktion erlaubt, zweite pro Domäne abgelehnt | `ActionGateTest` | Neu | S |
| **S06** | 013 | Welt A mit aktiven Leases, Wechsel zu Welt B | Reset vor neuem Step | Timer/Input/Rotation | keine Lease aus A in B; kein Restore über Weltgrenze; `connectionGeneration` neu | `MinecraftWorldLifecycleTest` + `MovementContextTest` | Erweitern/neu | S |
| **S07** | 002 | Scaffold aktiv, `currentScreen != null` | GUI offen, PRE | Suspension | kein Placement, keine synthetische Rotation, Timer neutral; logisches Scaffold-Ziel bleibt | `MovementContextTest` + `ScaffoldPlacementTest` | Neu/erweitern | S |
| **S08** | 003 | Speed hält 1.0, Scaffold requestet 1.0 | Timer-Resolve | Timer: zwei Token, gleicher Wert | Scaffold gewinnt; Release Scaffold lässt Speed-Lease bestehen; kein Wert-Owner-Verlust | `TimerArbiterTest` | Neu | S |
| **S09** | 003 | Lease schreibt 1.5, fremder Write 1.7 | Timer-Resolve | Timer: fremder Write | Lease wird invalidiert; Arbiter schreibt nicht zurück; Diagnose `hasForeignWrite` | `TimerArbiterTest` | Neu | S |
| **S10** | 003 | Scaffold 1.5, Speed 1.03, Velocity 1.2 | Request → Resolve → Release | Timer-Prioritäten | Scaffold > Speed > Velocity; nach Release jeweils nächste Lease; am Ende `before` nur bei sicherem Write | `TimerArbiterTest` | Neu | S |
| **S11** | 003 | Lease TTL 1, kein Renew; GUI dazwischen | Tick → Suspend → Resume | Timer | Lease läuft ab; Suspend restauriert sicher; Resume ohne automatische Wiederanwendung | `TimerArbiterTest` | Neu | S |
| **S12** | 003/014 | Timer-Lease Wert 1.0 aktiv | Tickbase-Frame | Tickbase | `isNeutral()` false ⇒ kein Release/keine Extra-Schritte; neutral ⇒ normale Prüfung | `TickbaseBudgetTest` + `TimerArbiterTest` | Erweitern/neu | S |
| **S13** | 004 | Scaffold platziert erfolgreich, kein neues Ziel | Platzierung → 2–3 Ticks ohne Target | Rotation: Scaffold | Owner bleibt bestätigt, MoveFix nutzt ihn weiter; kein `reset("scaffold")` | `RotationOwnerContinuityTest` | Neu | S |
| **S14** | 004 | Zielverlust, dann Disable vs. Weltwechsel | Planende → Disable/World | Rotation/RotationCorrections | Planende hält Owner; Disable 2–4 Tick Smoothing (Vorschlag), dann frei; Welt hart reset | `ScaffoldPlacementTest` + `ScaffoldClientFlowTest` | Erweitern (Freigabe nötig) | S |
| **S15** | 004 | Aura und Scaffold requesten gleichzeitig | PRE/POST | Rotation: Priorität 30 > 20 | bestätigter Owner ist Scaffold; Aura-MoveFix greift nicht; kein Owner-Diebstahl | `RotationBehaviorHookTest` | Erweitern | S |
| **S16** | 004 | C03 wird verworfen/ersetzt/nicht gesendet | PRE → C03 → finishMotion | Rotation: kein Owner | `canUseRotation("scaffold", pre)` false; Placement `STALE`/`ROTATION`; kein effektiver Winkel aus altem Frame | `ScaffoldPlacementTest` + `NetworkMoveFixIntegrationTest` | Erweitern | S |
| **S17** | 008 | BridgeAssist will gliden, Kamera manuell bewegt | PRE/POST | Rotation: visible Request | kein direkter `rotationYaw/Pitch`-Write; `requestVisible` mit TTL; GUI/Fokus verweigert; Release kehrt zu physischer Kamera zurück | `BridgeAssistLeaseTest` | Neu | S |
| **S18** | 006 | Physischer Sneak/Stop, WTap-Stop, Scaffold-Stop, Sprint-Start | Input-Boundary | Input: Konflikt | physischer Stop > Scaffold-Stop > WTap-Stop > Sprint-Start; keine Key-Writes; TTL 1–2 Ticks | `InputResolverTest` + `SprintOrderingTest` | Neu/erweitern | S |
| **S19** | 007 | AutoBridger über Luft, physischer Sneak gehalten | Input-Boundary | Input: Sneak-Lease | synthetischer Sneak nur bei aktivem Lease; Release liest physischen Zustand; Disable schreibt nicht pauschal false | `AutoBridgerLeaseTest` | Neu | S |
| **S20** | 006 | WTap2 Stopped-Fenster, Sprint gehalten | Attack → 2 Ticks → Release | Input/Sprint/C0B | genau ein C0B-Sync pro Zustandswechsel; kein Key-`pressed`-Write; physischer Sprint nach Release | `WTap2LeaseTest` + `SprintOrderingTest` | Neu/erweitern | S |
| **S21** | 005 | Kein Potion, Speed I/II, Slowness I/II | Speed-Modus | Speed-Basis | Werte 1.2/1.4/1.6/1.0/0.8 bleiben als Ist-Tuning sichtbar; keine doppelte Potion; D4 nicht vorweggenommen | `SpeedMovementBasisTest` | Erweitern, nur nach D4-Freigabe | S |
| **S22** | 011 | Alle NoSlow-Modi, Item aktiv | SlowdownEvent → Sprint | NoSlow/Sprint | Modi liefern dokumentierte Slowdown-/Sprint-Allow-Werte; kein Timer-/Input-Owner-Konflikt | `NoSlowSprintTest` | Erweitern | S |
| **S23** | 009 | Velocity-Modus, Treffer, Timer-Lease | AttackEvent → Tick | Velocity/Timer | kein direkter Timer-Write; Lease nur bei aktivem Modus; Minemen/Verus nicht still entfernt | `VelocityLeaseTest` | Neu | S |
| **S24** | 010/014/018 | Attack + Placement + Inventar im selben Outer-Tick | Ordinary Step | ActionGate + PacketPipeline | pro Domäne max. 1; C0B/C03/C02/C0E-Reihenfolge dokumentiert; kein doppelter C02/C0E | `ActionGateTest` + `PacketFlowTest` + `ScaffoldNetworkIntegrationTest` | Neu/erweitern | S |
| **S25** | 012 | Zwei Input-Instanzen, statische Felder | Input-Snapshot | `MovementInput` | Snapshot liest pro Step konsistent; statische Felder werden nicht als zweite Wahrheit benutzt; Instanzumbau nicht vorweggenommen | `MovementInputContractTest` | Neu | S |
| **S26** | 015/018 | Alte Config mit `MoveFix=false`, Legacy-Aliase | Config-Load → neues Profil | Config | alter expliziter Wert bleibt; neues Profil „Präzise“; Aliase `Min Range`/`Delay` bleiben; kein stiller Rebind | `LegacyModuleConfigTest` + `ModuleManagerTest` + `ConfigManagerTest` | Erweitern | S |
| **S27** | 001/004 | Kamera 0°, Owner 0/90/180/270°; W/A/S/D kombiniert; `PRECISE`, `SIMPLE`, `ROTATION_BOUND`, `OFF` | Input-Boundary → PRE | Rotation/MoveFix | `PRECISE` erhält den Weltvektor; `SIMPLE` bleibt innerhalb 22,5°; `ROTATION_BOUND` nutzt Owner; `OFF` nutzt Kamera; keine Kamera-Fälschung | `MoveFixProductionTest` + `MovementHelperContractTest` | Erweitern | S |
| **S28** | 006/016 | Sneak gehalten, Sprint-Jump, JumpReset-/Scaffold-Jump-Request | Input-Boundary → Jump → C0B | Input/Jump | physischer Sneak/Sprint bleibt Basis; höchstens ein Jump pro Step; kein doppelter Sprint-/Jump-Side-Effect | `InputResolverTest` + `MoveFixProductionTest` | Neu/erweitern | S |
| **S29** | 002/003/013 | Exception im Placement, `PacketDelays.close`, Timer-Lease aktiv | Teardown/Reset | alle Leases | Timer neutral, Queue leer, Owner frei, keine Aktion; kein Restore in neue Session | `NetworkShutdownTest` + `TimerArbiterTest` + `ScaffoldPlacementTest` | Erweitern | S |

### 3.1 Warum diese Fälle ausreichen

- Sie decken jede der drei Ebenen ab: Input-Boundary, Paketphase, Outer-Tick.
- Sie prüfen Owner **und** Wert: Timer-Token, Rotation-Owner, Input-Lease.
- Sie prüfen das schwierigste Lebenszykluspaar: Planende vs. Ownerfreigabe.
- Sie prüfen die gefährlichste Außenwirkung: Doppelaktion durch Tickbase-Extras.
- Sie prüfen die GUI-/Welt-/Disconnect-Grenzen, die W01 als offen markiert.
- Sie erfinden keine numerischen CMB-/SCF-Werte.

### 3.2 Bestehende Tests, die bewusst geändert werden müssen

| Test | Warum | Freigabe |
|---|---|---|
| `ScaffoldPlacementTest.scaffoldPriorityIsReleasedToAuraAfterTargetLossOrDisable` | schreibt die aktuelle, spec-widrige Freigabe fest | **erforderlich**, SCF-1/2 |
| `ScaffoldPlacementTest.timerRestoreHonorsExternalOwnershipChanges` | prüft wertbasiertes `TimerControl`; Token-Semantik ersetzt es | **erforderlich**, MOV-2 |
| `SpeedMovementBasisTest` | fixiert 1.2; darf nur mit D4-Freigabe geändert werden | **erforderlich**, nur G-CMB-SPEED |
| `SprintOrderingTest` | prüft heutige Scaffold-Sprint-Unterdrückung; Input-Resolver verschiebt den Ort | **erforderlich**, MOV-2 |
| `LegacyModuleConfigTest` | darf **nicht** geändert werden, um Aliase zu „reparieren“ | **nein**; Aliase bleiben |
| `TickbaseBehaviorHookTest` | prüft `timerSpeed == 1`-Pfad; wird auf `isNeutral` erweitert | **erforderlich**, NET-1/MOV-2 |

---

## 4. Reale Abnahmematrix (nicht ausgeführt)

Alle Einträge sind **R** und in CORE-R01 **E** (nicht geprüft). Sie sind keine JUnit-Ersatzfälle.

| ID | Finding/Gate | Umgebung | Ablauf | Erwartung | Nachweis |
|---|---|---|---|---|---|
| **R1** | 002/013 | Singleplayer, echte Welt | Scaffold + Aura + Speed + Tickbase; GUI öffnen/schließen; Welt wechseln; sterben/respawnen | keine Aktion im GUI; Timer neutral; keine alte Rotation/Lease in neuer Welt; nach Resume neue Requests | manuelles Protokoll + Log |
| **R2** | 001/003/014/018 | Testserver (Multiplayer) | Platzieren, angreifen, Sprint/WTap, Tickbase-Extra; Packet-Reihenfolge beobachten | C0B vor C03; keine Doppel-C02/C0E; Timer nur ein Owner; keine Client-Desync-Behauptung | Server-/Client-Log, Packet-Trace |
| **R3** | G-SCF-ANGLES | Reale Terrain-Szenen | **8** Scaffold-Profile: Face, Winkel, Hitpoint, Fallback | alle 8 Profile treffen die geplante Fläche; Fallback dokumentiert; keine erfundenen Winkel | Freigabetabelle + Video/Log |
| **R4** | G-CMB-SPEED | Singleplayer + Testserver | **4** Yaw-/Pitch-Min-/Max-Defaults in °/s, reale Bounds, MoveFix | Werte entsprechen freigegebener Tabelle; keine Überschreitung; Rotation bleibt visuell stabil | Freigabetabelle + Messprotokoll |
| **R5** | 004/008 | OptiFine + Shader, F5 | Scaffold/Aura/BridgeAssist, Körperrotation, Kamera | Gameplay-Rotation und Körperpose getrennt; kein Kamera-Drift; Shader ohne Zustandsänderung | Video/Screenshot + Log |
| **R6** | 015 | Bestehende Configs | Alte Profile laden, neues Profil anlegen, speichern, neu laden | alte Werte/Aliase erhalten; neues Profil „Präzise“; keine stillen Rebinds | Diff der JSON + UI-Prüfung |

**Regel:** R3 und R4 dürfen erst starten, wenn die Nutzerfreigabe für die 8 Profile bzw. 4 Werte vorliegt. R1/R2 dürfen keine Anticheat-/Serverkompatibilität behaupten, die nicht gemessen wurde.

---

## 5. Testklassen-Plan

### 5.1 Neu

- `MovementContextTest`
- `StepContextTest`
- `TimerArbiterTest`
- `InputResolverTest`
- `RotationOwnerContinuityTest`
- `ActionGateTest`
- `BridgeAssistLeaseTest`
- `AutoBridgerLeaseTest`
- `WTap2LeaseTest`
- `VelocityLeaseTest`
- `MovementInputContractTest`
- `ScaffoldProfileContractTest` (nur nach G-SCF-ANGLES)
- `CombatRotationSpeedContractTest` (nur nach G-CMB-SPEED)

### 5.2 Bestehend, geplant zu erweitern

- `PlayerStepHookTest`, `TickbaseBehaviorHookTest`, `TickbaseBudgetTest`
- `ScaffoldPlacementTest`, `ScaffoldClientFlowTest`, `ScaffoldNetworkIntegrationTest`
- `RotationBehaviorHookTest`, `MoveFixProductionTest`, `NetworkMoveFixIntegrationTest`
- `SpeedMovementBasisTest`, `NoSlowSprintTest`, `SprintOrderingTest`
- `PacketFlowTest`, `PacketDelayTest`, `SessionConsumerIntegrationTest`, `NetworkShutdownTest`
- `MinecraftWorldLifecycleTest`, `ModuleManagerTest`, `LegacyModuleConfigTest`, `ConfigManagerTest`

### 5.3 Nicht anfassen ohne separaten Auftrag

- `LegacyModuleConfigTest`: Aliase sind Ist-Vertrag.
- `CompressionPipelineTest`: NET-001-Regression, nicht Teil von CORE.
- `ArrayListModeVisibilityTest`, `UiEffectsTest`: W00/Mod-010-Vertrag.
- `GuiChatInputTest`, `GuiOverlayDebugGlStringsTest`, `WorldOverlayContractTest`: umgebungsabhängig, nicht CORE.

### 5.4 Minimale Test-Doubles

Keine Mocking-Library, keine neuen Dependencies. Die vorhandenen Muster reichen:

| Double | Vorbild | Zweck |
|---|---|---|
| `LongSupplier`-Uhr | `RotationManager(LongSupplier, long)` | Timer-TTL/Generation ohne `Thread.sleep` |
| `PacketPipeline` + EmbeddedChannel | `PacketFlowTest`, `NetworkShutdownTest` | Requested/Selected/Held/Locally Accepted prüfen |
| `ScaffoldPlacementService.Access`-Stub | `ScaffoldPlacementTest` | Placement-Plan, Raycast, Slot-Restore ohne echte Welt |
| `MinecraftAccess`/`AttackHookFixture` | `AttackHookTest`, `TickbaseBehaviorHookTest` | Player-/Welt-/Connection-Identität |
| `Object`-Player/Welt/Connection | `RotationManager`-Tests | WeakReference-/Generation-Wechsel |
| Fake-`Minecraft`-Kontext | `MinecraftWorldLifecycleTest` | GUI/Fokus/Pause/Disconnect |
| `Packet`-Testobjekte | `PacketDelayTest` | Delay/Release/Close ohne Netty-Server |

**Regel:** Ein Double darf keine echte Minecraft-Physik vortäuschen. Wenn ein Fall Kollision, Raycast oder Serververhalten braucht, gehört er in die reale Matrix (R1–R6), nicht in einen JUnit-Fall mit Attrappe.

---

## 6. Abnahmekriterien und statische Prüfungen

### 6.1 Technische Gates

| Gate | Nachweis | Status in R01 |
|---|---|---|
| **T-SYN** | alle S01–S29 grün, JDK 8, Maven offline | offen |
| **T-REG** | bestehende 396 Tests + neue Tests grün, 0 Failures/Errors | offen |
| **T-LIVE** | R1–R6 protokolliert | offen |
| **G-CMB-SPEED** | 4 Werte freigegeben und getestet | **blockiert** |
| **G-SCF-ANGLES** | 8 Profile freigegeben und getestet | **blockiert** |
| **G-MOV-AUDIT** | W01-Review formal akzeptiert | offen |

### 6.2 Statische Prüfungen nach der Implementierung

Diese Prüfungen sind Review-Regeln, keine R01-Ausführung:

```text
1. mc.timer.timerSpeed wird nur in TimerArbiter geschrieben.
2. keyBind*.pressed wird nur in InputResolver/definierten Vanilla-Adaptern geschrieben.
3. rotationYaw/rotationPitch werden nur in RotationManager/RotationCorrections
   (und nach Migration in BridgeAssist als Request) geschrieben.
4. Kein Modul erzeugt new RotationManager, new PacketPipeline, new ConfigManager
   oder einen zweiten Input-/Timer-Service.
5. canUseRotation(String, pre) ist der Produktionspfad für Placement/MoveFix;
   canUseRotation(boolean) bleibt höchstens Test-/Legacy-API.
6. Scaffold ruft kein reset("scaffold") pro Platzierung auf.
7. ActionGate wird vor Attack/Placement/Inventory geprüft.
8. Jede Lease prüft connectionGeneration und wird bei Reset invalidiert.
```

### 6.3 Release-/Merge-Gates

- Kein Merge ohne T-SYN + T-REG.
- Kein Merge von SCF-1/SCF-2 ohne CMB-1.
- Kein Merge von MOV-2 ohne MOV-1 (Input-Snapshot).
- Kein Merge von NET-1 ohne CORE-1 (StepContext).
- Kein Merge von MOD-1 vor D9-Entscheidung und Config-Backup.
- Kein Merge von R3/R4 vor G-SCF-ANGLES/G-CMB-SPEED.
- Keine Änderung an `main`, `pom.xml`, Dependencies oder Branch-Historie in R01.

---

## 7. Empfehlung an die sieben Fachbereiche

| Bereich | Start nach | Erste Aufgabe | Nicht anfassen |
|---|---|---|---|
| **CORE** | sofort | Contracts + Tests, Default-off | Fachlogik, numerische Werte |
| **MOV** | CORE-1 | Input-Snapshot und Suspension | Rotation, Packet, Config |
| **SCF** | CMB-1 + MOV-1 | Owner halten, Plan/Owner trennen | Timer-Direktwrites, RotationManager-Bau |
| **CMB** | CORE-1 | source-spezifische Owner-Predicate | Packet/Config, Speed-Werte |
| **NET** | CORE-1 | StepContext/ActionGate, `isNeutral` | zweite Queue, Vanilla-Timer |
| **MOD** | SCF-2 + MOV-2 | Config-Defaults, Key-Konflikte | stille Migration, Moduslöschung |
| **VIS** | CMB-1 + NET-2 | Pose/Diagnose read-only | Gameplay-Rotation, Input |

---

## 8. Schlussfolgerung

Der Vertrag ist bewusst **klein**: ein Step-Kontext, ein Input-Resolver, ein Timer-Arbiter, eine geschärfte Rotations-Owner-Predicate und ein Action-Gate. Die vorhandenen Dienste bleiben die einzigen Paket-, Config- und Platzierungsmanager. Die kritischsten Implementierungen sind C4 (Scaffold-Owner), M1/D2 (Timer-Priorität) und C1 (PRE-Semantik); die kritischsten Freigaben sind G-CMB-SPEED und G-SCF-ANGLES. R01 selbst hat keinen Produktionscode und keinen Test ausgeführt.
