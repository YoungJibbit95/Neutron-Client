# CORE-I01b — Ergebnis

28.10.2026: Code-Gate bestanden, read-only Runtime-/Scene-Beobachtung implementiert
und synthetisch geprüft. Dieser Auftrag endet hier; I01c wurde nicht begonnen.

## Arbeitsbasis und Erhalt des ursprünglichen Checkouts

| Punkt | Tatsächliches Ergebnis |
| --- | --- |
| Ursprünglicher Checkout | `F:\Puslar Backup - Copy` |
| Dessen HEAD / Branch vor und nach der Arbeit | `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb` / `prep/neutron-opencode-wave00` |
| Verifizierter lokaler I01a-Branch | `codex/neutron-core-i01a` |
| Vollständig lokal aufgelöste Basis | `eae42f43757c344784efdb53492c06eaf34f39ba`; identisch zur Auflösung von `eae42f437` |
| Eigener Branch / Worktree | `codex/neutron-core-i01b` / `F:\neutron-core-i01b` |
| Kollisionsprüfung | Branch und Pfad waren frei; `git worktree add -b ... codex/neutron-core-i01a` erfolgreich |
| Initialstatus eigener Worktree | `git status --short` leer; HEAD identisch zur I01a-Basis |
| Code-/Test-Commit | `d059f827ed7120f64e92392b51d1fe5c5d3f38bd` — `feat(core): observe runtime player steps and scene context` |
| Berichtscommit | Direkter Folgecommit, ausschließlich `HOOK_CONTRACT.md` und diese `RESULTS.md`; voller Hash in der Chat-Übergabe bzw. `git rev-parse HEAD`. Ein Commit kann seinen eigenen Hash nicht in seinem Inhalt speichern. |
| Endstatus eigener Worktree | Nach dem getrennten Berichtscommit sauber; vollständiger Commitbereich mit `git diff --check` geprüft |

Der ursprüngliche Checkout enthält unverändert **147 vorbestehende Modifikationen**
und die zwei untracked Verzeichnisse `design/opencode-wave01/` und
`design/opencode-core-r01/`: insgesamt **149 Statuszeilen**. Vor/nach der Arbeit wurden
die vollständige Statusliste und der getrackte Binärdiff verglichen; beide sind identisch.
Zusätzlich sind die SHA-256-Fingerprints aller **153** gesicherten geänderten bzw.
untracked Dateien identisch: **0 Abweichungen**. Es wurden keine persönlichen
Laufzeitdateien in den neuen Worktree kopiert.

Diff-Fingerprint, mit `git diff --no-ext-diff --binary | git hash-object --stdin`
in derselben PowerShell-Umgebung ermittelt:
`a394935edbf56e31f30122e95de5c8214cd26a12`.
Der bestehende I01a-Worktree wurde ausschließlich gelesen und war sauber.
Kein Stash, Clean, Reset, Branchwechsel im Original, Fetch, Push, PR, Merge oder Tag.

Auftrag und ZIP wurden aus `C:\Users\Administrator\Downloads` gelesen.
`00_START_HIER.md` wurde vor den übrigen Paketdateien geöffnet; das Quellenpaket
liegt entpackt außerhalb des Repositories unter
`C:\Windows\Temp\neutron-core-i01b-20261028\sources`.
Die Unterlagen wurden als Quellen mit der im I01b-Auftrag festgelegten Rangfolge
behandelt, nicht als Erlaubnis für weitere Implementierungen.

## Code-Gate und endgültiger Umfang

Vor Produktionsänderungen wurden die tatsächlichen vier I01a-Vertragstypen,
ihre drei Tests sowie `CONTRACT_DECISIONS.md`/`RESULTS.md` gelesen.
Immutable Werte, schwache `==`-Identitäten, Epoch-/Generationsvergleiche,
ExecutedStep-Invarianten und zustandslose Eligibility erfüllen den beanspruchten Vertrag.
Die relevanten Runtime-/Budget-/Player-/Packet-Pfade und die fünf vorgegebenen
Referenz-Testklassen wurden ebenfalls gelesen. Der neue Gate-Lauf mit diesen fünf
und den drei I01a-Testklassen bestand mit **54/0/0/0**.

Die Integration ergänzt genau eine runtimegebundene Observerinstanz ohne
Fallback-Singleton. Der Observer zählt tatsächliche `runTick`-Eintritte, beschreibt
betretene Bodies, trennt null Callback-Aufrufe von `false` und räumt bei Exception,
Weltwechsel und Shutdown auf. Scene-Prüfungen lesen Identität und Pipeline-Epoch neu.
Es gibt weder eine neue Automationssperre noch einen Produktionsconsumer der Eligibility.

Genaue Dateiliste relativ zur Worktree-Wurzel:

```text
M src/main/java/me/youngjibbit/core/bootstrap/ClientRuntime.java
M src/main/java/me/youngjibbit/core/bootstrap/RuntimeServices.java
M src/main/java/me/youngjibbit/module/impl/exploit/Tickbase.java
M src/main/java/net/minecraft/client/Minecraft.java
A src/main/java/me/youngjibbit/core/observation/MinecraftSceneSource.java
A src/main/java/me/youngjibbit/core/observation/RuntimeObservation.java
A src/test/java/me/youngjibbit/core/observation/RuntimeObservationHookTest.java
A src/test/java/me/youngjibbit/core/observation/RuntimeObservationTest.java
A design/neutron-codex-core-i01b/HOOK_CONTRACT.md
A design/neutron-codex-core-i01b/RESULTS.md
```

Code-/Test-Commit: **8 Dateien, 591 Einfügungen, 1 entfernte Leerzeile**.
Die beiden Berichte stehen im getrennten Folgecommit.
Keine I01a-Vertragstypen oder bestehenden Tests wurden editiert.
`EntityPlayerSP`, `TickbaseBudget`, `PacketPipeline` und POM sind unverändert.
Der originale Tickbase-Entscheidungsrumpf und `runGameLoop` wurden zusätzlich
textuell gegen die Basis verglichen und sind nach Normalisierung der Zeilenenden identisch.

Die einzige Abweichung von der bisherigen Struktur ist die Extraktion des bestehenden
Tickbase-Rumpfs in einen privaten Helper für das Callback-Wrapping. Die ursprüngliche
Anzahl und Anwendungsreihenfolge der Simulationen bleibt erhalten. Exakte Hookzeilen,
API, Generation/Epoch, Lifecycle und Verhaltensmatrix stehen in `HOOK_CONTRACT.md`.

## Tatsächlich ausgeführte Tests

Alle Maven-Aufrufe liefen ausschließlich in `F:\neutron-core-i01b`, mit prozesslokalem
`JAVA_HOME`, ohne Änderung der dauerhaften Umgebung und ohne Netzdownloads:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-1.8'
$taskMaven = 'C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin\mvn.cmd'
& $taskMaven -version
& $taskMaven -o '-Dtest=SceneContextTest,ExecutedStepTest,AutomationEligibilityTest,PlayerStepHookTest,TickbaseBudgetTest,TickbaseBehaviorHookTest,PacketFlowTest,SessionConsumerIntegrationTest' test
& $taskMaven -o '-Dtest=RuntimeObservationTest,RuntimeObservationHookTest' test
& $taskMaven -o clean test
```

Verifiziert: **Oracle Java 1.8.0_391**, **Maven 3.8.5**, unveränderte
Compiler-Source/Target 1.8. `javap -verbose` bestätigt für `RuntimeObservation`
**major version 52**. Keine neuen Dependencies und keine POM-Änderung.

| Lauf | Run | Failures | Errors | Skipped | Exit / Maven-Dauer |
| --- | ---: | ---: | ---: | ---: | --- |
| Unverändertes Code-Gate: 8 bestehende Klassen | 54 | 0 | 0 | 0 | 0 / 01:14 min |
| Test-first RED, neue Tests vor Implementierung | — | — | — | — | 1 / 20.494 s |
| Erster fokussierter GREEN-Lauf | 23 | 0 | 0 | 0 | 0 / 01:23 min |
| Finaler fokussierter Lauf mit zusätzlicher realer Unload-/Exception-Abdeckung | 26 | 0 | 0 | 0 | 0 / 01:04 min |
| Vollständiger `mvn -o clean test` | **446** | **0** | **0** | **3** | **0 / 02:21 min** |

RED scheiterte erwartungsgemäß in `testCompile`: Die neue Klasse `RuntimeObservation`
fehlte an den Feldern von `RuntimeObservationTest` und `RuntimeObservationHookTest.Events`.
Surefire wurde nicht erreicht; für diesen Lauf werden keine ausgeführten Tests behauptet.
Anschließend wurden die Observerklassen und Hooks implementiert. Drei weitere Tests
ergänzten danach die reale World-Unload- und Exception-Abdeckung ohne weitere
Produktionsänderung. Es gab keine fehlgeschlagenen JUnit-Läufe oder Regressionen.

Die finalen fokussierten XML-Dateien wurden vor `clean` separat gesichert.
Der volle Lauf wurde aus **85** `target/surefire-reports/TEST-*.xml` unabhängig
aggregiert: **446/0/0/3**, also **443 nicht übersprungene Surefire-Testfälle**.
In beiden finalen grünen Läufen enthalten:

| Neue Klasse | Run | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `RuntimeObservationTest` | 15 | 0 | 0 | 0 |
| `RuntimeObservationHookTest` | 11 | 0 | 0 | 0 |

Die drei Skips betreffen jeweils einen nativen Opt-in-Fall:

- `WorldOverlayContractTest.nativeDispatchWhenRequested`: `pulsar.world.overlay.native` nicht aktiviert.
- `GuiChatInputTest.nativeInputAndDrawingWhenRequested`: `pulsar.chat.native` nicht aktiviert.
- `GuiOverlayDebugGlStringsTest`: `pulsar.test.glContext` nicht aktiviert.

Diese Skips sind keine Render-/GL-Abnahme. Die I01a-Angaben **420/0/0/3** stammen
aus dem gelesenen Vorbericht; sie werden hier nicht als neuer vollständiger
Basislauf ausgegeben. Der hier tatsächlich neu ausgeführte Basislauf hatte 54 Tests.
Interne JUnit-Unterprozesse bestehender Harness-Tests werden nicht nochmals auf
die Surefire-Summe addiert.

Evidenz außerhalb des Repositories:

```text
C:\Windows\Temp\neutron-core-i01b-20261028\00-code-gate.log
C:\Windows\Temp\neutron-core-i01b-20261028\01-test-first-red.log
C:\Windows\Temp\neutron-core-i01b-20261028\02-focused.log
C:\Windows\Temp\neutron-core-i01b-20261028\03-focused-final.log
C:\Windows\Temp\neutron-core-i01b-20261028\04-full-clean-test.log
C:\Windows\Temp\neutron-core-i01b-20261028\toolchain.log
C:\Windows\Temp\neutron-core-i01b-20261028\focused-reports\
C:\Windows\Temp\neutron-core-i01b-20261028\full-totals.json
C:\Windows\Temp\neutron-core-i01b-20261028\skipped-tests.json
C:\Windows\Temp\neutron-core-i01b-20261028\original-final-verification.json
```

## Unverändertes Verhalten und verbleibende Grenzen

Es gibt keine Input-, Timer-, Combat-, Scaffold-, Rotation-, Placement-, Inventory-,
Config- oder Packet-Migration. Die vier erlaubten Bestandsdateien enthalten nur
Beobachtungsanbindung bzw. Servicezugriff. Manuelle Vanilla-Aktionen werden nicht
interceptiert. Bestehende Attack-/Placement-Budgets und Tickbase-Credits bleiben maßgeblich.

Neu geprüft sind unter anderem tatsächliche Tickbase-Callbacks ohne aktives Modul,
Withhold/verdientes Extra, echte EventUpdate-Cancellation, Body-/Decision-Exception,
Weltentladung, Shutdown und reale Packet-Epoch/Close sowie reine Identitäts-/ABA-Tests.
GUI/Pause/Fokus suspendieren die beobachtete Eligibility, ohne die logische Scene
zu wechseln. Die WeakReference-Feldprüfung ist deterministisch und erzwingt keinen GC.

Der Packet-/Event-Vergleich nutzt den echten Walking-/Packet-Pfad mit einem
synthetisch geposteten Update-Boundary; er führt keine vollständige Player-Physik aus.
Die Framefälle 0/3/0/2 nutzen echte `runTick`-Eintritte, die ohne Renderer früh und
kontrolliert abbrechen; ein nativer `runGameLoop` wurde nicht gestartet.
Der manuelle Aktionsbeleg betrifft den bestehenden Scheduler-/Controller-API-Vertrag,
keinen vollständigen Maus-/Inventory-UI-Flow. Applet-Exit ist nur statisch geprüft.
Keine echten Server, Accounts, Spielwelten, Shader oder Release-/Performance-Abnahme.

Nicht beobachtbare direkte A→B→A-Feldschreibungen ohne Lifecycle-Hook oder Packet-Epoch
bleiben eine Polling-Grenze; reale Worldboundaries und beobachtete Identitätswechsel
haben monoton fortgeschriebene Generationen. Während eines laufenden Bodys ist sein
Abschluss noch unbekannt; der endgültige Interrupted-Wert steht erst nach dessen Ende fest.

**I01c kann technisch vorbereitet werden**, weil aktuelle Outer-ID, echte Body-Art
und frische Scene-Validierung nun am zentralen Pfad verfügbar sind. Ein nächster
gesonderter Auftrag muss zunächst automatische Modul-Consumers und ihre Aktionsphasen
einschließlich automatischer Versuche außerhalb des Player-Bodys prüfen und dann
modulspezifische Regressionstests vor jeder Anbindung der Eligibility erstellen.
Manuelle Aktionen und bestehende Fachbudgets müssen dabei erhalten bleiben.
Owner-/Rotation-/Slotannahmen und die offenen numerischen CMB-/SCF-/MOV-Gates sind
dadurch nicht freigegeben. Es wurde kein Folgeauftrag ausgeführt.

Abschlusskontrolle: tatsächliche Dateiliste gegen die Allowlist geprüft,
`git diff --check`, `git diff --cached --check` und Prüfung des gesamten eigenen
Commitbereichs ohne Befund. Reproduzierbare lokale Sichtung:

```powershell
git log --oneline eae42f43757c344784efdb53492c06eaf34f39ba..HEAD
git diff --name-status eae42f43757c344784efdb53492c06eaf34f39ba..HEAD
git diff --check eae42f43757c344784efdb53492c06eaf34f39ba..HEAD
git status --short
```
