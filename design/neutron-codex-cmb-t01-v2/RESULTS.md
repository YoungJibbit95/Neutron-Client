# CMB-T01-v2 — Gate A PASS / Gate B GREEN

28.10.2026. Neutron-Client. Aktive Scaffold-C03 wird jetzt an der vorhandenen
späten Predicate-Grenze geschützt. Die Originalsonde erreicht weiter den echten
GUI+Disable-PRE mit Yaw 9.900001 bei Kamera 0; danach werden **0 stale C03**
transportiert, `accepted=false`, `owner=null`.

**Keine Release-Lease implementiert. H1-06 bleibt OPEN. STOPP nach CMB-T01-v2.**

## Basis, HEAD und Isolation

| Punkt | Nachweis |
| --- | --- |
| BASE | `ce7015cb8d8cc74ef4a0b17065167ff2bb98f4d4` |
| Basisworktree | `F:\neutron-scf-1a`, sauber vor/nach Arbeit, Branch codex/neutron-scf-1a |
| CMB-Mergebase | `88ee497d8fe2dca16e5eaae9e48548c7d46e7050` |
| Eigener Branch / Worktree | `codex/neutron-cmb-t01-v2-c03` / `F:\neutron-cmb-t01-v2-c03`, beide vor Anlage frei |
| Produkt-/Test-HEAD | `7fc3a6a1323c4f7c7017cfe66b63024b8ff77b35` |
| Abschließender Dokumentations-HEAD | Exakt im externen `final-state.json`, nach dem zweiten selektiven Commit; dadurch keine Selbstreferenz des Dokumentationscommits |
| Origin | historisch `https://github.com/YoungJibbit95/Pulsar-Client.git`; ausschließlich gelesen, nicht geändert |
| Quellen/Evidenz | `F:\neutron-cmb-t01-v2-quellen-c03`, frei angelegt außerhalb aller Worktrees |
| Quellenprüfung | zuerst 00_START_HIER, danach 31/31 Manifestgrößen/SHA-256; separate Master-/Reviewdateien bytegleich; Manifest nicht signiert |
| Persönliche Daten | eigener Sparsecheckout ohne test_run/logs, eigenes target/test-home, keine Übernahme persönlicher Assets, Accounts oder Saves |

Der Benutzer erlaubte einen freien externen Entpackpfad; der oben genannte
Evidenzpfad ist bewusst unabhängig vom Git-Worktree. Alte CMB-T01-/SCF-1b-STOPP-
Worktrees wurden nicht weiterverwendet. Initialer eigener Checkout mit
`--no-checkout`, worktree-lokalen Sparsemustern und eigenem `read-tree -mu HEAD`;
danach leerer Status. Kein Reset/Stash/Clean; Maven clean betrifft ausschließlich
das verifizierte eigene target. Kein Fetch/Push/Merge/Rebase/Tag/PR/Remoteedit.
Keine Subagents und keine automatische Integration in Original/REL/VIS.

## Endgültige Dateiliste

Nur diese sechs Code-/Testdateien im ersten Commit:

```text
src/main/java/me/youngjibbit/core/combat/RotationManager.java
src/main/java/net/minecraft/client/entity/EntityPlayerSP.java
src/test/java/me/youngjibbit/core/combat/ResolvedC03SendTicketTest.java          [neu]
src/test/java/me/youngjibbit/core/world/ScaffoldLateC03TransportTest.java        [neu]
src/test/java/me/youngjibbit/core/combat/RotationModuleBindingChecks.java       [1 Aufruf]
src/test/java/me/youngjibbit/core/world/LegacyScaffoldTest.java                 [1 Aufruf]
```

Nur vier Berichte im zweiten Commit:

- [TRANSPORT_CODE_GATE.md](TRANSPORT_CODE_GATE.md): reale APIs/Callgraph, F01/F02,
  weitere Walking-Aufrufer, Rewrite-/Scene-/Fehlervertrag und Gate-A-Freigabe.
- [TEST_FIXTURE_MIGRATION.md](TEST_FIXTURE_MIGRATION.md): exakte Substitutionen,
  unveränderte Assertions/Methoden/Toleranzen, kanonischer Outer-/Step-Lifecycle.
- [C03_TICKET_CONTRACT.md](C03_TICKET_CONTRACT.md): tatsächlich implementierte
  API, immutable Bindungen, T01–T18 mit konkreten Testnamen und Grenzen.
- dieser Ergebnisbericht.

`ScaffoldClientFlowTest` ist bereits öffentlich zugänglich und bleibt bytegleich.
POM und alle anderen Produktions-/Test-/Dependency-/Assetdateien bleiben außerhalb
des Diffs. Vorher-/Nachhervergleich der gesamten F01/F02-Dateien erlaubt jeweils
exakt die eine Aufrufsubstitution: 109 bzw. 22 erfasste Assertionausdrücke und
19 bzw. 5 Testmethoden bleiben textidentisch. Keine Skip-/Toleranzänderung.

## RED / Fixture-first / GREEN

Alle Mavenaufrufe verwenden JDK Oracle 1.8.0_391 und Maven 3.9.15 mit `-o`.
Unverändertes POM source/target 1.8. Die JSON-Runreceipts enthalten exakte
Argumente, CWD, Exitcodes, Laufzeiten und ausschließlich frisch erzeugte XMLs.
Äußere Wrapper, innere Checks und externe Sonden werden nicht doppelt gezählt.

| Lauf im externen Evidenzordner | Ergebnis / Aussage |
| --- | --- |
| baseline-full | Kompilierung erfolgreich, Surefire-Fork wegen vollem C:-Temp fehlgeschlagen; **0 ausgeführte Tests / 0 XMLs**, kein fachlicher RED |
| baseline-full-retry | **567/0/0/3**, Exit 0, **93 frische XMLs**, noch keine Repository-Edits |
| t01-original.log/.xml | bytegleiche Original-B08/B20-Sonde: **1 Assertionfailure**, staleSyntheticLooks=1, Yaw 9.900001, Kamera 0, accepted=true, owner=null |
| predicate-control.log/.xml | vorhandene Predicate-Überladung separat **1/0/0/0**, keine Behebung des alten Walkingcallers behauptet |
| f01-t05-original.log/.xml | Original-F01 unverändert besteht; PRE active=-1/history=-1/INVALIDATED, yaw=0.90000004, 1 transportierte C03; zusätzliche T05-Assertion RED |
| f02-t05-original.log/.xml | Original-F02 unverändert besteht; PRE active=-1/history=-1/INVALIDATED, yaw=32.100002, 1 transportierte C03; zusätzliche T05-Assertion RED |
| fixtures-before-production | **4/0/0/0**, 2 XMLs, nach genau zwei Fixtureaufrufmigrationen, Produktion noch unverändert; Wrapper enthalten 19 bzw. 2 innere Checks |
| ticket-pre-red | **24 ausgeführte Tests**, 13 Failures, 7 Errors, 0 Skips, 2 XMLs; davon **12 reale Walking-Assertion-REDs**. Direkte APItests: 7 NoSuchMethod-Errors und 1 entsprechend eingepackter Failure; ausdrücklich keine fachlichen Policy-REDs aus fehlender API ableiten |
| ticket-first-green | **28/0/0/0**, 4 XMLs, erster Kandidat samt Legacyfixtures |
| late-capture-red | zusätzliche echte Lebensdauerprobe auf erstem Kandidaten: **1 Test / 1 Assertionfailure**, alter Resolve konnte beim späten Getter an neuen Tick gebunden werden |
| ticket-focused-green | nach Bindung im Resolvezeitpunkt **171/0/0/0**, 18 XMLs, breite Owner-/Placement-/NET-/Observer-/Sprintregression |
| final-full | nach zusätzlicher normaler Delayrelease-Probe und 100 Capturegetterreads **593/0/0/3**, 95 XMLs, mvn -o clean test |
| final-reviewed-full | nach Erhaltung des exakten ursprünglichen neutralen this.sendQueue-Aufrufs erneut **593/0/0/3**, **95 frische XMLs**, Exit 0, mvn -o clean test; maßgeblicher endgültiger Quellstand |
| t01-original-green.log/.xml | **1/0/0/0**; identischer Original-PRE mit Yaw 9.900001, danach staleSyntheticLooks=0, accepted=false, owner=null |
| predicate-control-green.log/.xml | **1/0/0/0**, bestehende Kontrollgrenze unverändert |

Die letzte Vollsuite besteht aus 567 bestehenden und **26 neuen** Surefirefällen
(9 Ticket-APIprüfungen, 17 reale Walking-/Transportmethoden mit weiteren
Einzelszenarien). Die ursprünglichen externen Sonden sind separate JUnit-XMLs,
keine zusätzlichen Surefirefälle. Ihr JDK8-javac hatte Exit 0.

Der Tempfehler wurde ausschließlich durch prozesslokale TEMP/TMP und
`MAVEN_OPTS=-Djava.io.tmpdir=F:\neutron-cmb-t01-v2-quellen-c03\tmp` behoben.
Keine fremden Dateien zur Platzgewinnung gelöscht und kein POM geändert.
Testarbeitsverzeichnis/Home bleiben durch das vorhandene POM im eigenen target.

Die drei unveränderten Opt-in-Skips:

- WorldOverlayContractTest.nativeDispatchWhenRequested
- GuiChatInputTest.nativeInputAndDrawingWhenRequested
- GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings

Klassenheaderprüfung: **3.317 Hauptklassen, 278 Testklassen, 5 externe Sondenklassen,
alle Major 52**. RotationManager und EntityPlayerSP zusätzlich über JDK8-javap
gebunden. `final-static-checks.json`, beide javap-Dateien und das
`fixture-assertions-verification.json` enthalten Einzelbelege/Hashes.

## Git-Schutz und Review

`worktrees-before.json` erfasst 40 bereits registrierte Worktrees mit HEAD,
Branch, vollständigem Porcelainstatus einschließlich untracked Namen und
Bytezahl/SHA-256 des rohen `git diff --binary HEAD`. `GIT_OPTIONAL_LOCKS=0`,
`--no-optional-locks` und prozesslokal abgeschalteter Fsmonitor für fremde Reads.
Keine fremden Index-/Konfigurations-/Dateiedits. Die Nachheraufnahme umfasst
41 Worktrees einschließlich des eigenen; dessen Endmetadaten werden nach beiden
Commits abschließend aktualisiert. Aufnahmen sind zeitlich gestaffelt, nicht atomar;
untracked Namen beweisen keine Inhaltsidentität ihrer Dateien.

**37/40 bestehende Worktrees unverändert.** Ausschließlich beobachtete parallele
Änderungen, nicht übernommen oder korrigiert:

- Original `F:\Puslar Backup - Copy`: HEAD/Branch gleich; Status und Rohdiff
  geändert (28.967.537 -> 28.968.987 Bytes; Hashes im Vergleichsreceipt).
- `F:\neutron-rel-p02`: HEAD von cea64143cc6ec5f42eb2c830965b40a66162c208 nach
  a580114ab13634dee82953fabacc718fa12a2d9d, zusätzliche untracked Dokumente.
- `F:\neutron-vis-qa02-motion-toast`: zusätzliche Test-/Berichtspfade und
  Rohdiffänderung (42.831 -> 47.502 Bytes), HEAD/Branch gleich.

Basis, alter CMB-T01-STOPP und SCF-1b-STOPP sind im Vergleich unverändert.
Vollständige Metadaten stehen in `worktree-comparison.json` und den zwei Snapshots.

Vor jedem selektiven Commit: diff --check, exakte Namensallowlist und cached
--check. Erster Commit ausschließlich sechs Code-/Testdateien, zweiter ausschließlich
vier Berichte. Endstatus/Index leer, zwei lokale Commits. Der rohe abschließende
`git diff --binary BASE HEAD` liegt außerhalb des Worktrees als
`CMB_T01_V2_REVIEW.patch`; Größe/SHA-256, exakter End-HEAD, alle zehn Dateihashes,
Reverse-Apply-Check und Exitcodes sind im abschließenden **final-state.json**
gebunden. Der Reviewpatch enthält implementierten Code, Tests und diese Berichte;
er ist nicht der historische STOPP-Dokumentationspatch. Keine Selbsthashreferenz
innerhalb des eigenen Reviewpatches.

## Grenze und Ende

T01–T18 und F01–F04 sind für diese lokale aktive C03-Sendebasis nachgewiesen;
native Opt-in-Skips bleiben offen. Kein Anspruch auf Server-/Anticheat-/native
Gameplay-/GL-/Performance-/Releaseabnahme. Scene-Sampling besitzt die bestehende
Observerbuchhaltung, reine Getter erzeugen keinen Tick oder Step. Numerisch
äquivalente Rewrites folgen dem dokumentierten bisherigen Winkelvertrag.

Keine Release-Lease, keine 3-Outer-Tick-/200-ms-Implementierung, kein Disable-
Smoothing und kein weiterer SCF-/NET-/MOV-/VIS-/REL-Scope. Ein möglicher
SCF-1b-v2-Folgeauftrag benötigt eine neue ausdrückliche Freigabe auf dem neuen
Commit; er wurde hier nicht begonnen. **STOPP nach CMB-T01-v2; H1-06 OPEN.**
