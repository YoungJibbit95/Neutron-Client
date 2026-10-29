# CMB-1a — Ergebnis

28.10.2026: Die source-spezifische bestätigte Rotation-Owner-Predicate ist
testgetrieben umgesetzt. Der verpflichtende Abschlusslauf mit JDK 8 und Maven
offline, exakt `mvn -o clean test`, meldet **542 Tests, 0 Failures, 0 Errors,
3 Skips**. 91 frische Surefire-XMLs bestätigen diese Summe unabhängig vom Log.
539 nicht übersprungene Fälle haben keinen Failure/Error; die Skips bleiben offen.

## Geprüfte Basis und Git-Gates

| Punkt | Tatsächlicher Stand |
| --- | --- |
| Basisworktree / Branch | `F:\neutron-core-i01e` / `codex/neutron-core-i01e` |
| Exakter Basis-HEAD | `3554f7a9545fbc03b91fbdf9af453ba851bf0f2c` |
| Ausgangsstatus der Basis | `git status --short` leer; HEAD und Branch stimmen exakt |
| Kollisionsgate | `F:\neutron-cmb-1a` und `refs/heads/codex/neutron-cmb-1a` existierten nicht |
| Eigener Worktree / Branch | `F:\neutron-cmb-1a` / `codex/neutron-cmb-1a` |
| Initialer eigener HEAD vor Tests | derselbe Basis-Commit; `git status --short` und `git diff --name-only` beide leer |
| Code-/Test-Commit | `d1eb7f077496bdde746cf6485a779d7da62fc95c` — `feat(rotation): expose phase-bound confirmed owner predicate` |
| Finaler Branch | `codex/neutron-cmb-1a` |
| Berichtsabschluss | Ein zusätzlicher reiner Bericht-Commit mit genau den drei unten genannten Dateien; dessen eigener finaler HEAD wird erst extern in `final-state.json` und in der Abschlussantwort genannt |

Git erzeugte den neuen Worktree auf dem ausschließlich lokal vorhandenen exakten
I01e-Commit. `worktree add --no-checkout` und ausschließlich eigene Sparse-Checkout-
Einstellungen schließen `test_run/**` und `/logs/` aus; beide Verzeichnisse fehlen
auch nach den Tests. Kein Kopieren fremder Saves, Logs, Userconfigs oder Accounts.
Der alte Checkout und alle früheren Worktrees wurden ausschließlich gelesen.
Kein Fetch, Push, PR, Merge, Rebase, Tag, Stash, Reset oder Git-Clean wurde ausgeführt.
Mavens Clean betraf ausschließlich das eigene `target`.

Das Quellen-ZIP wurde außerhalb aller Git-Repositories nach
`C:\Users\Administrator\Downloads\neutron-cmb-1a-sources-20261028` entpackt.
Zuerst wurde daraus `00_START_HIER.md`, danach `MANIFEST.json` gelesen; alle
15 Manifest-SHA-256-Prüfungen stimmen. Der separate CMB-1a-Prompt ist der
ausgeführte Auftrag. Produktspezifikation, I01e-Verträge, Review und AC-R02/H1
sind Kontext mit der im Auftrag vorgegebenen Rangfolge, keine weiteren Aufträge.
Kein Fremdcode wurde portiert. Keine Subagents oder Delegation.

## Tatsächliche Codeänderung und Grenzen

`RotationManager.canUseRotation(String source, boolean pre)` prüft einen gültigen,
exakt passenden Owner und die numerische Accepted-Basis der verfügbaren Phase.
`pre == collecting` verhindert, dass der nach Finish überschriebene POST-Owner
wegen eines noch gesetzten `previousAccepted` als früherer PRE-Owner gilt.
`getConfirmedOwner()` delegiert an dieselbe Wahrheit; die neuen Tests verlangen
auch für einen leeren/Whitespace-Request-Owner null statt einer falschen Bestätigung.
Bestehende kontextgebundene Getterguards bleiben erhalten.

Es gibt keine neuen Zustandsfelder. `finishMotion`, Outbound-Beobachtung,
Prioritäten, Angle-Wrap/Toleranz, Pitch-Clamp, Reset-/Bindingpfade, Smoothing,
GCD und Noise bleiben unverändert. Der numerische Legacy-Aufruf bleibt für
unbesitzte akzeptierte Kamera-/Vanilla-Frames nutzbar. Pending Requests,
Reject/Cancel/Delay, abweichender Rewrite/Replay, Source-Reset und Kontextwechsel
erzeugen keine Sourceberechtigung. C01–C12 und die tatsächlichen Codeanker stehen
in `CMB_OWNER_CONTRACT.md`.

## Test-first und echte Laufzahlen

Alle Mavenaufrufe verwenden prozesslokal:

- `JAVA_HOME=C:\Program Files\Java\jdk-1.8`, Oracle **1.8.0_391**.
- Maven **3.8.5** aus
  `C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin\mvn.cmd`.
- Ausnahmslos `-o`; JUnit **4.13.2**, unverändertes POM mit `source/target 1.8`.
- Unveränderte Surefire-Umgebung mit eigenem `target/test-home`; keine permanente
  Windows-Umgebungsänderung und kein Dependency-/POM-/Config-Update.

`00-toolchain.log` weist `mvn -o -version` mit den genannten Versionen nach.
`javap -verbose` und die unabhängige Klassenheaderprüfung bestätigen für
RotationManager **major version 52** (Java 8).

Logs, exakte Kommandos, Laufzeiten, frische XML-Kopien und JSON-Summen liegen
außerhalb des Repositories im oben genannten Quellen-/Evidenzordner.
`run_maven.py` kopiert pro Lauf nur seit dessen Beginn neu geschriebene
`TEST-*.xml`; Compile-RED bekommt ausdrücklich keine Surefire-Testzahl.

| Lauf | Befehl nach `mvn -o` | Tests | Failures | Errors | Skips | XMLs / Ergebnis |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| `01-baseline` | `test -Dtest=<Bestand>` | 118 | 0 | 0 | 0 | 13, BUILD SUCCESS; vor neuen Test-/Produktionsdateien, 105.409 s |
| `02-owner-red` | `test -Dtest=RotationOwnerPredicateTest` | — | — | — | — | Compile-RED, keine Surefireausführung; fehlende API und zwei Zugriffsfehler im neuen Testentwurf, 27.483 s |
| `03-owner-api-red` | derselbe Filter | — | — | — | — | kanonisches API-Compile-RED ausschließlich wegen fehlendem Overload; keine Surefireausführung, 26.466 s |
| `04-owner-green` | derselbe Filter | 21 | 0 | 0 | 0 | 1, BUILD SUCCESS nach minimaler Implementierung, 90.810 s |
| `05-focused-green` | `test -Dtest=<Bestand>,RotationOwnerPredicateTest` | 139 | 0 | 0 | 0 | 14, BUILD SUCCESS, 44.206 s |
| `06-full-clean` | **`clean test`** | **542** | **0** | **0** | **3** | **91**, BUILD SUCCESS, 167.194 s |

Im ersten RED hatte der neue Test einen nicht öffentlichen Callback der vorhandenen
`PacketFlowTest.Observer` verwendet. Korrigiert wurde ausschließlich die neue
Testklasse: ein eigener kleiner Paketlistener ersetzt diesen Zugriff. Danach
scheitert der zweite RED nur an `canUseRotation(String,boolean)`. Produktionscode
blieb bis nach diesem bereinigten RED unverändert. Keine vorhandene Fixture oder
Assertion wurde hierfür geändert.

`<Bestand>` ist der verifizierte Filter:

```text
CombatFlowTest,RotationBehaviorHookTest,RotationCorrectionsTest,
RotationModuleBindingTest,ScaffoldPlacementTest,ScaffoldPipelineIntegrationTest,
MoveFixControllerTest,MoveFixProductionTest,NetworkMoveFixIntegrationTest,
PacketFlowTest,AutomatedPlacementAdmissionTest,AutomatedActionAdmissionTest,
AutomatedActionModuleTest
```

Die Namen wurden zuvor im tatsächlichen `src/test` verifiziert und ihre relevanten
Fixtures/Rotation-/MoveFix-/Placement-/Packet-/Admissionpfade gelesen. Zuvor wurde
der tatsächliche Produktionscode von RotationManager, RotationCorrections,
MoveFixController, ScaffoldPlacementService, Scaffold, Aura, PRE/C03/POST im Player,
PacketPipeline/Outbound-Beobachtung und den aktuellen Observer-/Admission-APIs gelesen.
Historische I01e-521/0/0/3 wurden nicht als neuer Baselinelauf ausgegeben.

Die drei unveränderten nativen Opt-in-Skips der Vollsuite sind:

- `WorldOverlayContractTest.nativeDispatchWhenRequested`
- `GuiChatInputTest.nativeInputAndDrawingWhenRequested`
- `GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings`

Der unveränderte Kamerafall
`ScaffoldPlacementTest.rotationsOffStillRequiresActualAcceptedView` und der
bewusst noch alte Releasefall
`scaffoldPriorityIsReleasedToAuraAfterTargetLossOrDisable` bestehen im Fokus und
in der Vollsuite. Die unveränderten `RuntimeObservationActiveTickTest`- und
AutoHeal-TickEvent-Tests laufen in der Vollsuite mit; keine neue Fachmigration.
Wrapper-Unterprozessfälle werden nicht zusätzlich zur Surefire-Summe gezählt.

## Exakte Dateiliste / Allowlist

```text
M src/main/java/me/youngjibbit/core/combat/RotationManager.java
A src/test/java/me/youngjibbit/core/combat/RotationOwnerPredicateTest.java
A design/neutron-codex-cmb-1a/CMB_OWNER_CONTRACT.md
A design/neutron-codex-cmb-1a/CMB_TO_SCF_HANDOFF.md
A design/neutron-codex-cmb-1a/RESULTS.md
```

Der Produktionsdiff besteht aus acht hinzugefügten Zeilen und einer ersetzten
Getterzeile. Die einzige neue Testklasse enthält 21 JUnitfälle. Bestehende Tests,
alle übrigen Produktionsdateien, `pom.xml`, Dependencies, Assets, Defaults und
Userdaten bleiben unverändert. Insbesondere keine Scaffold-/Aura-/Placement-/
MoveFix-/Observer-/AutoHeal-/Player-/PacketPipeline-Änderung.

Vor jeder der zwei Commitstufen werden ausschließlich die expliziten Dateien
gestaged und `git diff --cached --name-status` sowie `--check` geprüft. Der erste
Commit enthält nur Code/Test, der zweite ausschließlich diese drei Berichte.
Der abschließende `git diff --check BASE..HEAD`, die exakte Dateiliste und der leere
eigene `git status --short` werden nach dem Berichtcommit erneut extern erfasst.
`verify_final.py` prüft die fünf erlaubten Pfade, die XML-Summe und Java-8-Bytecode.
Ein Commit kann seinen eigenen Hash nicht in seinem Inhalt enthalten; der finale
Bericht-HEAD steht deshalb ausschließlich in der externen Übergabe.

## Schutz der vorhandenen Worktrees

Vor Erstellung des eigenen Worktrees wurden **29** bestehende Worktrees erfasst:
HEAD, Branch, vollständiger Porcelainstatus einschließlich untracked Dateinamen,
getrackte Diff-Dateiliste und SHA-256 des binären getrackten HEAD-Diffs.
Nach Umsetzung und erneut nach der Vollregression werden dieselben Werte geprüft.
`worktrees-before.json`, `worktrees-after.json` und `worktrees-comparison.json`
dokumentieren den Vergleich; die abgeschlossene Vergleichsprüfung meldet keine
Abweichung. Die Reads nutzen `GIT_OPTIONAL_LOCKS=0` und benötigen keine fremden
Indexwrites. Untracked Dateiinhalte werden damit nicht separat byteweise gehasht.

Das Original `F:\Puslar Backup - Copy` bleibt auf `prep/neutron-opencode-wave00`,
HEAD `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`, mit seinen **147** bestehenden
getrackten Änderungen und fremden untracked Forschungsdateien. Sein getrackter
binärer Diff-SHA-256 lautet unverändert:
`ea20915ab1fd52e816248910c54fb0c4731989e928e5d7d64eea59b4f0dcc88d`.
Auch I01a bis I01e und alle weiteren bestehenden Worktrees bleiben in den erfassten
Werten unverändert. Es wurden keine fremden Dateien überschrieben oder als eigene
Änderung übernommen.

## Restgrenzen und STOPP

Scaffold hält seine Priorität weiterhin nicht automatisch über Planende hinweg.
Die Trennung von Planende/Ownerfreigabe, fortgeführte Requests, Aura-Konkurrenz,
GUI-Suspend versus Scene-Invalidierung, Retry/Resume, 2–4-Tick-Disable-Auslauf und
Placement-/MoveFix-Consumer-Migration bleiben dem separaten SCF-Auftrag vorbehalten.
Rotation On braucht dort die neue Sourceprüfung; Rotation Off behält eine eigene
gültig akzeptierte Kameraprüfung. AC-R02/H1-01…07 bleiben Vorlagen, keine hier
bestandenen vollständigen SCF-Szenarien.

Keine Gameplay-/Server-/Anticheat-/GL-/Shader-/Performance-/Releaseabnahme;
keine Flagfreiheitsgarantie. G-CMB-SPEED, G-SCF-ANGLES, G-MOV-SPEEDBASE, Presets,
CPS/Noise und Config-Migration bleiben unberührt. Die neue API ist eine reine
Client-Thread-Abfrage des vorhandenen Lifecycle-Zustands und kein neuer Live-
Sceneprüfer oder Aktionsbudget. **STOPP nach CMB-1a; keine Scaffold-Folgeänderung.**
