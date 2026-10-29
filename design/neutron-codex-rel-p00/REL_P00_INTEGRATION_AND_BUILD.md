# REL-P00 — Integration und Build

28.10.2026: **Gate A PASS / SOURCE_INTEGRATED**. Offline-Regression und
Packaging bestehen. Ein vollständiger Spielstart ist nicht nachgewiesen.

| Referenz | Vollständiger Commit |
|---|---|
| CMB-1a-Ausgangspunkt | `88ee497d8fe2dca16e5eaae9e48548c7d46e7050` |
| Gemeinsamer I01e-Vorfahr, lokal per merge-base geprüft | `3554f7a9545fbc03b91fbdf9af453ba851bf0f2c` |
| VIS-01-Implementierung | `e3492be7a0a415491e87fda23b2cb7f282fa2a4c` |
| VIS-01-Berichtsabschluss, Präfix lokal eindeutig aufgelöst | `7f79895f4033ba7e2e58af95bec2a3775d082ab0` |
| Eigener Implementierungs-Cherry-Pick | `a8ffbee2b76ef8f7e2b52a20407fcb111999be2e` |
| Eigener Berichts-Cherry-Pick / getesteter Integrationsstand | `36ca09f541fb8974f181ddaa8b0137f3dcc64a84` |

Eigener Branch: `codex/neutron-rel-p00`, Worktree: `F:\neutron-rel-p00`.
Branch und Pfad waren frei; beide Quellworktrees waren sauber.
VIS-01 besitzt seit dem gemeinsamen Vorfahren genau die beiden genannten
Commits. Sie wurden in Originalreihenfolge konfliktfrei übernommen.
Alle elf übernommenen Dateien stimmen byteweise mit VIS-01 überein.
Keine SCF-1a-/VIS-02-Commits und keine sonstigen Gameplayänderungen wurden integriert.

Der Checkout wurde ohne persönliche `test_run/**`- und `/logs/`-Bäume
materialisiert. Nach `worktree add --no-checkout` war der Index zunächst leer;
`sparse-checkout set` allein materialisierte ihn nicht. Ausschließlich im
eigenen Worktree initialisierte `git read-tree -mu HEAD` den Sparse-Checkout.
Der Status war anschließend vor den Cherry-Picks leer. Keine Dateilöschungen
wurden committed; keine fremden Saves, Logs oder Kontodateien kopiert.

## Frische ausgeführte Prüfungen

Oracle JDK **1.8.0_391**, `C:\Program Files\Java\jdk-1.8`, Maven **3.8.5**
aus dem vorhandenen Wrapper-Cache, prozesslokales `MAVEN_OPTS=-Xmx512m`.
Compiler-/Plugin-/Dependencyversionen und POM bleiben unverändert.

| Befehl, jeweils Maven offline | Dauer | Frische XMLs | Tests / Failures / Errors / Skips | Ergebnis |
|---|---:|---:|---|---|
| `mvn -o clean test` | 151,775 s | 94 | **565 / 0 / 0 / 3** | PASS |
| `mvn -o package` | 148,305 s | 94 | **565 / 0 / 0 / 3** | PASS |
| `dependency:build-classpath dependency:tree`, runtime | 3,870 s | 0 | kein Testlauf | PASS |

Die Summen stammen aus allen seit dem jeweiligen Laufstart frisch erzeugten
`TEST-*.xml`, separat extern gesichert und gehasht. Historische Branchsummen
wurden nicht addiert; Unterprozess-JUnitfälle zählen nicht nochmals zur
Surefire-Summe. 562 nicht übersprungene Fälle ohne Failure/Error je Vollsuite.
Die drei unveränderten Skips sind:

- `WorldOverlayContractTest.nativeDispatchWhenRequested`
- `GuiChatInputTest.nativeInputAndDrawingWhenRequested`
- `GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings`

Zusätzlich bestehen zwei gezielte Paketabhängigkeitsproben mit je einem Fall:
`NativeDependencyTest` läuft separat gegen Assembly bzw. Shade plus JUnit und
Hamcrest, ohne die Maven-Runtimebibliotheken. Diese zwei Prüfungen werden
nicht zu den 565 addiert. Kein Display-/Pbuffer-/Spielstart wird dadurch belegt.

`git diff --check` besteht. Der reine REL-Berichtscommit folgt nach diesem
getesteten Integrationsstand; sein Endhash steht in der externen
[final-state.json](F:/neutron-rel-p00-evidence-20261028/final-state.json).

## Packaging-Grenze

`jar tf`, Manifestanalyse, `javap -verbose` und tatsächlich ausgeführte
Java-8-`java -jar`-Proben sind für alle drei Ausgaben gesichert.
Assembly scheitert an fehlendem Launchwrapper-Main; Shade und dünnes JAR
besitzen kein Main-Class-Attribut. Der vorhandene Minecraft-Main ist Major 52.
**B1 Build PASS, B1 direkter JAR-Start FAIL.** Es wurde kein vollständiger
Client gestartet und keine Launcherfreigabe erteilt. Die bloße Korrektur eines
Manifestattributs würde die offenen Auth-, Classpath-, Isolations- und Rechtegates
nicht schließen; es wurde kein scheinbar fertiges Launcherartefakt hergestellt.

Vollständige Befunde: [LOCAL_RUNTIME_GATE.md](LOCAL_RUNTIME_GATE.md).
Belege: [Integration](F:/neutron-rel-p00-evidence-20261028/integration-proof.json),
[Toolchain](F:/neutron-rel-p00-evidence-20261028/toolchain.log),
[Testlog](F:/neutron-rel-p00-evidence-20261028/01-clean-test.log),
[unabhängige XML-Summe](F:/neutron-rel-p00-evidence-20261028/01-clean-test-summary.json),
[Packaginglog](F:/neutron-rel-p00-evidence-20261028/02-package.log),
[Packaging-XML-Summe](F:/neutron-rel-p00-evidence-20261028/02-package-summary.json),
[JAR-Audit](F:/neutron-rel-p00-evidence-20261028/jar-audit.json).
