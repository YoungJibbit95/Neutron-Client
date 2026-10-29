# REL-P01 — Ergebnis und Abschluss

28.10.2026, Europe/Berlin. **Gate A PARTIAL; Paket LOCAL_PACKAGING_VERIFIED.**
**B0 BLOCKED; B1/B2/B3/B4 NOT_RUN. LOCAL_ONLY / DISTRIBUTION_BLOCKED.**
Kein Spielstart, kein Testerrelease. Die einzige POM-Korrektur ersetzt den fehlenden
Launchwrapper durch den vorhandenen direkten Main im Assembly-Manifest.

| Referenz | Exakter Wert |
|---|---|
| Unveränderte lokale Basis | `b313c397359e6f4ba65601d87ab797e8049e9871` |
| Eigener getesteter Implementierungscommit | `c863c79efe786dff9a7b8404e674932783e188a8` |
| Eigener Branch/Worktree | `codex/neutron-rel-p01`, `F:\neutron-rel-p01` |
| Finaler Berichtscommit/HEAD | Exakt in [final-state.json](F:/neutron-rel-p01-evidence/final-state.json) |
| Finaler Review-Patch/SHA256 | [rel-p01-review.patch](F:/neutron-rel-p01-evidence/rel-p01-review.patch), [rel-p01-review.patch.sha256](F:/neutron-rel-p01-evidence/rel-p01-review.patch.sha256) |

Der finale Berichtscommit kann seinen eigenen Hash nicht enthalten. End-HEAD,
sauberer vollständiger Porcelainstatus, BASE..HEAD-Dateiliste, Patchhash/-größe und
erfolgreicher Reverse-Apply-Check stehen deshalb ausschließlich im externen Receipt.
Der hier festgehaltene Implementierungscommit enthält genau die getesteten fünf Dateien.

## Frische Testbelege

| Lauf | XMLs / Fälle | Ergebnis |
|---|---|---|
| Unveränderte Basis mvn -o clean package | 94 / 565 | 0 Failures, 0 Errors, 3 Skips |
| Korrigiertes Test-first gegen Baselinepaket | 7 | 2 erwartete Main-Class-Assertionfehler |
| Final mvn -o clean test | 95 / 566 | 0 Failures, 0 Errors, 3 Skips |
| Final mvn -o package | 95 / 566 | 0 Failures, 0 Errors, 3 Skips |
| Separater finaler Paket-/JNI-Runner | 8 | alle bestanden; nicht zu Surefire addiert |
| Negativer read-only Preflight | 9 Szenarien | alle erwarteten BLOCKED-Checks, Fixturehashes gleich |
| Echte JAR-Proben, vor/nach | je 3 | Baseline Entrypointfehler; final Assembly Pflichtargumentfehler, Shade/dünn weiter ohne Main |

Die drei unveränderten Opt-in-Skips sind WorldOverlayContractTest.nativeDispatchWhenRequested,
GuiChatInputTest.nativeInputAndDrawingWhenRequested und
GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings.
Neue Post-Package-Checks sind explizite Runner und keine versteckten zusätzlichen Skips.
Toolchain: Oracle JDK1.8.0_391, Maven3.8.5, -o bei jedem Mavenaufruf.
Die finale Suite begann nach allen POM-/Test-/Helferänderungen;
[tested-input-hashes.json](F:/neutron-rel-p01-evidence/tested-input-hashes.json) bindet deren Bytes. Danach wurden nur Berichte ergänzt.
Artefakthashes identifizieren diesen Lauf, keine Behauptung byteidentischer Neubuilds.

## 22 einzeln bewertete Gates

| ID | Status | Tatsächliche Bewertung | Präziser Beleg |
|---|---|---|---|
| R01-01 | PASS | Exakter sauberer REL-P00-Pin; Branch/Pfad vor Erstellung frei. | [git-preflight.json](F:/neutron-rel-p01-evidence/git-preflight.json) |
| R01-02 | PASS | 35 vorherige Worktrees geschützt; parallelen VIS-QA01-Commit und neuen SCF-1b-Worktree separat ausgewiesen. | [worktrees-comparison.json](F:/neutron-rel-p01-evidence/worktrees-comparison.json) |
| R01-03 | PASS | Oracle JDK8 1.8.0_391, Maven3.8.5; alle Mavenaufrufe -o, identischer Scope. | [00-toolchain.log](F:/neutron-rel-p01-evidence/00-toolchain.log) |
| R01-04 | PASS | Drei echte Baseline-JAR-Fehler; gezieltes Runtime-/POM-RED vor POM-Write. | [04-red-corrected.log](F:/neutron-rel-p01-evidence/04-red-corrected.log) |
| R01-05 | PASS | Tatsächliches Assembly-Main Major52, JarFile/Services/Resources/Discovery geprüft. | [10-final-packaged.log](F:/neutron-rel-p01-evidence/10-final-packaged.log) |
| R01-06 | PASS | Genau eine Strategie: unminimierte Assembly mit direktem Main; Shade/dünn keine Alternativfreigabe. | [final-jar-audit.json](F:/neutron-rel-p01-evidence/final-jar-audit.json) |
| R01-07 | BLOCKED | Kritische Klassen effektiv einmal vorhanden; 199 unterschiedliche Maven-Überlappungen, insbesondere Netty/Patchy-Semantik nicht im Client qualifiziert. | [dependency-overlap-audit.json](F:/neutron-rel-p01-evidence/dependency-overlap-audit.json) |
| R01-08 | PASS | Echter Windows64/JDK8-LWJGL-Timer und DLL-Linksmoke, PE-/Hashinventar; kein Display-/Audiogeräte-Smoke. | [10-final-packaged.log](F:/neutron-rel-p01-evidence/10-final-packaged.log) |
| R01-09 | BLOCKED | 734 Assets und 35 Bibliotheken hashkorrekt; reguläre Benutzer-Vanilla-Voraussetzung nicht belegt, keine Runtimeverknüpfung installiert. | [installed-runtime-inventory.json](F:/neutron-rel-p01-evidence/installed-runtime-inventory.json) |
| R01-10 | PASS | Ein Check-only-Helfer, Default lesend und BLOCKED, keine Account-/Profil-/Startfunktion. | [preflight-default.json](F:/neutron-rel-p01-evidence/preflight-default.json) |
| R01-11 | BLOCKED | Headless-CWD/Home isoliert; echte GameDir/Config/Account-Consumer im Client nicht ausgeführt. | [isolation-proof.json](F:/neutron-rel-p01-evidence/isolation-proof.json) |
| R01-12 | PASS | Final mvn -o clean test: 95 frische XMLs, 566/0/0/3. | [08-final-clean-test-summary.json](F:/neutron-rel-p01-evidence/08-final-clean-test-summary.json) |
| R01-13 | PASS | Final mvn -o package: 566/0/0/3; erneute Artefakt-/Major-/Entrypointproben. | [09-final-package-summary.json](F:/neutron-rel-p01-evidence/09-final-package-summary.json) |
| R01-14 | PASS | Neun konkrete Fehlerfälle fail-closed; Fixturehashes unverändert. | [preflight-negative-tests.json](F:/neutron-rel-p01-evidence/preflight-negative-tests.json) |
| R01-15 | BLOCKED | Nutzer installiert den offiziellen Launcher ausdrücklich nicht; kein bestätigter Vanilla-Start. | [launcher-detection.json](F:/neutron-rel-p01-evidence/launcher-detection.json) |
| R01-16 | NOT_RUN | Kein B2-Opt-in angefordert, keine Versions-/Profilinstallation; B0 fehlt. | [launcher-detection.json](F:/neutron-rel-p01-evidence/launcher-detection.json) |
| R01-17 | BLOCKED | Kein echter Neutron-Display/MainMenu/Config-Start; Main-Validierungsfehler ersetzt ihn nicht. | [final-jar-audit.json](F:/neutron-rel-p01-evidence/final-jar-audit.json) |
| R01-18 | NOT_RUN | Keine Welt, ClickGUI/Settings, Fokus/Resize/Fullscreen oder Shaderinteraktion. | [launcher-detection.json](F:/neutron-rel-p01-evidence/launcher-detection.json) |
| R01-19 | NOT_RUN | Kein Spiel-Disconnect/Shutdown/Neustart; nur headless Validierungs-Exit1. | [isolation-proof.json](F:/neutron-rel-p01-evidence/isolation-proof.json) |
| R01-20 | NOT_RUN | Kein autorisierter Server-/Zweikonten-Test. | [launcher-detection.json](F:/neutron-rel-p01-evidence/launcher-detection.json) |
| R01-21 | BLOCKED | Vollständige Minecraft-/OptiFine-Bestandteile; DISTRIBUTION_BLOCKED. | [final-jar-audit.json](F:/neutron-rel-p01-evidence/final-jar-audit.json) |
| R01-22 | PASS | Finaler binärer BASE..HEAD-Patch extern, SHA256 und Reverse-Check, genaue Allowlist und sauberer End-HEAD im Abschlussreceipt. | [final-state.json](F:/neutron-rel-p01-evidence/final-state.json) |

PASS gilt jeweils nur für die beschriebene Prüfung. R01-08 ist ein nativer
Linktest, R01-13 ein Packagingtest; beide ersetzen keine R01-17..19-Spielsitzung.
Die offene semantische Dependencyqualifikation verhindert eine umfassende Gate-A-
Runtimefreigabe. B0 fehlt ausdrücklich nach Nutzerentscheidung, nicht bloß historisch.
Distribution bleibt auch unabhängig davon gesperrt.

## Exakte Datei-Allowlist

- `pom.xml`
- `src/test/java/me/youngjibbit/release/PackagingConfigurationTest.java`
- `src/test/java/me/youngjibbit/release/PackagedRuntimeCheck.java`
- `src/test/java/me/youngjibbit/release/WindowsNativeLinkCheck.java`
- `tools/neutron-rel-local/check-runtime.ps1`
- `design/neutron-codex-rel-p01/REL_P01_ENTRYPOINT_AND_CLASSPATH.md`
- `design/neutron-codex-rel-p01/REL_P01_LAUNCHER_SMOKE.md`
- `design/neutron-codex-rel-p01/REL_P01_DISTRIBUTION_GATE.md`
- `design/neutron-codex-rel-p01/RESULTS.md`

Genau neun geänderte/neue Dateien. Bestehende Tests und Java-Produktionsdateien
unverändert; insbesondere Main/Minecraft/Pulsar/Start, RuntimeServices und alle
Gameplay-/OptiFine-/NET-/SCF-/VIS-Dateien. Kein Dependency-/Repository-/Versionwechsel,
keine .bat-Hülle, kein weiterer Helper. Logs, XMLs, JSON-Diagnosen, Testfixtures und Patchreceipts sind extern gesichert.
Alle drei finalen JARs liegen SHA-geprüft zusätzlich unter dem externen
final/local-only-build; target bleibt ignorierter lokaler Buildoutput.

## Schutz bestehender Arbeit und Quellbindung

Alle 19 Manifestdateien mit Bytes/SHA256 verifiziert; separate Master-/Reviewdownloads
sind bytegleich. Quellen liegen außerhalb aller Worktrees unter
`F:\neutron-rel-p01-sources`, Evidenz unter `F:\neutron-rel-p01-evidence`.
Historische P00-/CMB-/VIS-Prompts sind Kontext und wurden nicht als neue Arbeitsaufträge ausgeführt.
Der alte Review-Patch wurde geprüft, nicht angewandt.

35 vorhandene Worktrees wurden vorher/nachher lesend erfasst (HEAD, Branch,
vollständiger Porcelainstatus, SHA256 des getrackten binären HEAD-Diffs).
34 von 35 waren beim dokumentierten Vergleich identisch.
Beobachtete parallele Änderung, nicht durch REL-P01 ausgeführt oder integriert:

- `F:/neutron-vis-qa01-gradients`: 534b8c4db8d3a6633eea6e663a74be17d18aadda → a4142772ab3ff64747f22215f922c5891153fe47; vorheriger Arbeitsdiff extern committed, Status jetzt ''.

Zusätzlich erschien der fremde neue Worktree `F:/neutron-scf-1b`; der eigene
REL-P01 ist separat. Der dirty Originalcheckout blieb in den Schutzfingerprints
unverändert. Untracked-Dateiinhalte wurden nicht pauschal gehasht; der Snapshot
beweist keine globale Schreibsperre gegen parallele Prozesse.
Git-Leseaufrufe liefen ohne optionale Locks; für zuverlässige Snapshots wurde
der native Gitpfad gepinnt und Fsmonitor/Untrackedcache nur pro Aufruf deaktiviert.
Die initiale hängende eigene Snapshotabfrage wurde beendet und vollständig wiederholt.
Fremde Gitkonfiguration und Indizes wurden nicht geschrieben.

Keine Subagents, Fetches, Pushes, Merges, PRs, Rebases, Resets, Stashes, Tags oder
Git-Cleans. Eigene neue Worktree-/Branchanlage direkt am exakten Basiscommit;
Sparse-Checkout materialisiert keine persönlichen test_run-/logs-Bäume.
Maven clean betraf nur den eigenen target. Modell/Reasoning sind Hosteinstellungen;
eine Umstellung oder unabhängige Bestätigung wurde nicht behauptet.

## Übergabegrenze

[source-verification.json](F:/neutron-rel-p01-evidence/source-verification.json), [git-preflight.json](F:/neutron-rel-p01-evidence/git-preflight.json),
[worktrees-before.json](F:/neutron-rel-p01-evidence/worktrees-before.json), [worktrees-after.json](F:/neutron-rel-p01-evidence/worktrees-after.json),
[worktrees-comparison.json](F:/neutron-rel-p01-evidence/worktrees-comparison.json), [artifact-manifest.json](F:/neutron-rel-p01-evidence/artifact-manifest.json),
[final-state.json](F:/neutron-rel-p01-evidence/final-state.json) führen die externen Belege.
Die drei fachlichen Detailberichte stehen neben dieser Datei.
Ein gesonderter späterer Auftrag wäre für einen anderen rechtmäßigen Launcher-/
Runtimevertrag, semantische Dependencykorrekturen oder eine Mod-only-Architektur nötig.
Nichts davon wurde begonnen. **STOPP nach REL-P01.**
