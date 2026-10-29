# REL-P02 — Ergebnis und Abschluss

28.10.2026, Europe/Berlin. **Gate A ABI_PARTIAL / UNRESOLVED.**
Die tatsächlich geprüften Windows-Anwendungsreferenzen und Headless-Proben
bestehen; Gesamt-Client, optionale GLES-/macOS-Flächen und Patchy-Semantik bleiben
offen. **Gate B NOT_RUN**, zusätzlich fehlende Voraussetzungen. Gate C ist eine
dokumentierte Launcherstrategie. **LOCAL_ONLY / DISTRIBUTION_BLOCKED.**

Es wurden ausschließlich drei neue Testdateien und diese vier Berichte ergänzt.
`pom.xml`, Produktionscode und der check-only Runtimehelfer sind unverändert.
Es gibt keinen Grund für eine Dependencyänderung auf Verdacht. Ein früherer
Scaffold-Suitefehler und zwei Umgebungsabbrüche bleiben sichtbar; ein späterer
grüner Lauf gilt nicht als Reparatur ihrer Ursache.

## Exakte Git- und Quellenbindung

| Referenz | Wert |
|---|---|
| Basisworktree / Branch | `F:\neutron-rel-p01` / `codex/neutron-rel-p01`, zu Beginn sauber |
| Exakte Basis | `cea64143cc6ec5f42eb2c830965b40a66162c208` |
| Eigener Worktree / Branch | `F:\neutron-rel-p02` / `codex/neutron-rel-p02` |
| Getesteter Testcommit | `a580114ab13634dee82953fabacc718fa12a2d9d` |
| Finaler Berichtscommit / sauberer End-HEAD | [final-state.json](F:/neutron-rel-p02-evidence/final-state.json), Feld `final_head` |
| Historischer Remote | `https://github.com/YoungJibbit95/Pulsar-Client.git`; unverändert |
| Quellen / Evidenz | `F:\neutron-rel-p02-sources` / `F:\neutron-rel-p02-evidence`, außerhalb aller Worktrees |

Zuerst wurde `00_START_HIER.md` gelesen. Größe und SHA256 aller 18 manifestierten
ZIP-Dateien stimmen; Manifest selbst ist ausgenommen. Masterprompt und separate
Reviewdatei entsprechen ihren ZIP-Einträgen. Alle ZIP-Dokumente wurden gelesen;
historische Produkt-/REL-P00-/P01-Aufträge wurden als Kontext behandelt und
erweiterten REL-P02 nicht. Der vorhandene lokale REL-P01-Reviewpatch stimmt
mit Größe/Hash seines Receipts überein und besteht den Reverse-Apply-Check auf
der exakten REL-P01-Basis; er wurde nicht angewandt.
Belege: [source-verification.json](F:/neutron-rel-p02-evidence/source-verification.json),
[source-bindings.json](F:/neutron-rel-p02-evidence/source-bindings.json).

Der eigene Worktree wurde direkt auf dem Pin erstellt, nachdem Pfad und Ref
frei waren. Der eigene Sparse-Checkout materialisiert weder `test_run/**` noch
`logs/**`. Git-Reads fremder Worktrees liefen ohne optionale Locks sowie mit
nur pro Aufruf deaktiviertem Fsmonitor/Untrackedcache. Keine Subagents, Fetches,
Pushes, PRs, Merges, Rebases, Tags, Resets, Stashes, Git-Cleans oder Remote-
Renames. Mavens Clean betraf ausschließlich das eigene `target`.
Das gewünschte Modell-/Reasoningprofil ist eine Hosteinstellung; eine Umstellung
oder unabhängige Bestätigung wurde nicht behauptet.

## Wirklich ausgeführte Läufe

Oracle JDK **1.8.0_391 x64**, Maven **3.8.5** aus dem vorhandenen Cache;
**jeder Mavenaufruf mit `-o`**. Keine neuen Downloads, Repositories oder Versionen.

| Lauf | XMLs / Tests | Failures / Errors / Skips | Ergebnis |
|---|---:|---|---|
| Unveränderte Basis: mvn -o clean package | 95 / 566 | 0 / 0 / 3 | Exit 0; 293.769 s |
| Erster Lauf mit neuen Tests: mvn -o clean test | 97 / 573 | 0 / 1 / 3 | Exit 1; 312.141 s |
| Diagnose: mvn -o test -Dtest=ScaffoldPlacementTest | 1 / 31 | 0 / 0 / 0 | Exit 0; 27.432 s |
| Finaler Kontrolllauf: mvn -o clean test | 97 / 573 | 0 / 0 / 3 | Exit 0; 246.058 s |
| Erster Paketlauf nach Kontrollsuite | 97 / 573 | 0 / 0 / 3 | Exit 1; 123.074 s |
| Final: mvn -o package, temporärer Ordner auf F: | 97 / 573 | 0 / 0 / 3 | Exit 0; 164.268 s |

Die finalen Summen stammen aus **je 97 frischen XMLs**. Jeder Summand ist mit
Name und SHA256 extern gesichert. `independent-final-validation.json` berechnet
die Summen aus den gesicherten XMLs erneut und bindet sämtliche getesteten
Quellbytes. Sieben neue Surefire-Fälle erklären 573 statt 566; keine historischen
Zahlen werden addiert. Der Post-Package-Runner heißt `*Check`, ist kein
versteckter Surefiretest und erzeugt keine neuen Skips.

Die drei unveränderten Opt-in-Skips:

* WorldOverlayContractTest.nativeDispatchWhenRequested
* GuiChatInputTest.nativeInputAndDrawingWhenRequested
* GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings

| Separater finaler Nachweis | Ergebnis | Beleg |
|---|---|---|
| Bisherige POM-/Paket-/Resource-/Discovery-/JNA-/JNI-Fälle | 8/8 | `13-final-packaged.log` |
| Neue ABI-/Klassenherkunfts-/Signaturfälle gegen Assembly allein | 9/9 | `14-final-abi.log` |
| Read-only Negativfälle | 9/9, erneut nach finalem Package | `preflight-negative-tests.json` |
| Default und geprüfter Hash / Isolationsplan | Exit2/BLOCKED, alte Policy erhalten | `preflight-final-policy.json` |
| No-Auth-Assembly-Start | Exit1, fehlende accessToken/version | `final-jar-audit.json`; MAIN_ARGUMENTS_ONLY |

Die neun neuen Paketfälle enthalten die sieben Surefire-Proben erneut plus zwei
explizite JAR-Checks; sie werden nicht zusätzlich zu 573 gezählt. Es gibt keinen
Fenster-, Gameplay-, Audio- oder Auth-PASS aus diesen Zahlen.

## Sämtliche vorläufigen Fehler und Grenzen

1. Ein externer Vorbereitungs-Einzeiler hatte einen Python-Quoting-Syntaxfehler;
   die Vorbereitung wurde als externe Datei korrekt wiederholt. Kein Runtime-RED.
2. `03-draft-abi-compile.log`: Die neue externe Fixture verwendete anfangs zwei
   paketprivate Netty-Futurekonstruktoren. Vor Repoübernahme durch die öffentliche
   DefaultChannelPromise-API ersetzt. Kein produktiver RED und keine Dependencyänderung.
3. Der erste statische Resolver konnte geerbte JDK-Methoden nicht auflösen.
   `dependency-abi-provisional.json` bleibt erhalten; die korrekte Auflösung
   gegen JDK8-rt.jar beseitigt diese Diagnosefehler, nicht Produktklassen.
   Zwei echte fehlende macOS-Synthetic-Member bleiben in der finalen Matrix.
4. `04-draft-abi.log`: Breite Memberreflexion unbenutzter GLES-Implementierungen
   scheitert an `PowerManagementEventException`. Der Befund wurde nicht repariert
   oder für grün erklärt. Die spätere Anwendungsprobe prüft einen ausdrücklich
   engeren, anhand der Callgraphen begründeten Vertrag. Drei GLES-Flächen bleiben
   UNKNOWN; alle 621 Klassen sind ohne Initialisierung ladbar.
5. `05-draft-scoped-abi.log`: Eine separate Prüf-JVM brach bei nativer
   Speicherallokation ab. Der neue Inventurtest hält danach nur SHA256-Werte
   statt aller Klassenbytes; der externe Runner verwendet 256 MiB Heap und zwei
   Compilerthreads. `07-draft-scoped-abi.log` besteht 9/9. Keine Änderung an
   Maven-/Produktions-/Pluginoptionen dafür.
6. `08-final-clean-test`: 573/0/1/3, echte NPE in
   `Scaffold.lambda$new$3`, Zeile195, aus
   `ScaffoldPlacementTest.scaffoldPriorityIsReleasedToAuraAfterTargetLossOrDisable`,
   Zeile300. Die unveränderte Klasse besteht isoliert 31/31; danach bestehen die
   unveränderten vollständigen Kontroll- und Paketläufe. Bestehende Fixtures
   schreiben mittels Unsafe in `Module.mc` (static final). Initialisierung/JIT
   ist deshalb eine begründete Hypothese, keine bewiesene Ursache oder Reparatur.
   Weder Scaffold noch vorhandene Testassertions wurden verändert. Die Regression
   ist aktuell grün, ihre beobachtete Instabilität bleibt offen (R02-13 PARTIAL).
7. `11-final-package`: Tests 573/0/0/3, Packaging Exit1 wegen fehlendem
   Datenträgerspeicher beim Zipping. C: hatte nur rund 54 MiB frei, F: rund
   93 GiB. Für `12-final-package` wurde ausschließlich prozesslokal
   `MAVEN_OPTS=-Xmx512m -Djava.io.tmpdir=F:\neutron-rel-p02-evidence\maven-temp`
   gewählt. Derselbe normale Offline-Paketbefehl inklusive Vollsuite besteht.
   Keine fremden Dateien oder Systemtempdateien wurden gelöscht; die vorläufige
   unvollständige Assembly wurde vom erfolgreichen eigenen Paketlauf ersetzt.
8. Maven-Shade-Überlappungswarnungen gehören zum unveränderten, unfreigegebenen
   Shade-/Dependencybestand. Identische versus verschiedene Klassen werden
   klassenweise bewertet. Der absichtliche Fehlerlog aus dem vorhandenen
   synthetischen Skin-Cache-Negativtest ist kein zusätzlicher Testerror.

Für einen notwendigen Fix außerhalb der Allowlist gilt weiterhin STOPP.
Es wurde keine solche Reparatur vorgenommen und kein fehlgeschlagener relevanter
Spielstart durch Ausfiltern, Fake-Token oder einen Manifest-PASS ersetzt.

## Paket- und Erhaltungskontrolle

| Lokale target-Ausgabe | Bytes | SHA256 | Freigabe |
|---|---:|---|---|
| `Neutron-dev-jar-with-dependencies.jar` | 123660770 | `406bf9b2cf79a6edf81600527eafd0b9b3fc6b3d6ef4e008a37636061a7cbf4d` | Nur gewählte lokale Assembly |
| `Neutron-dev.jar` | 73530072 | `51399a1a3668b38bdaccc90c680e99f1b35344caaf6e5cada635825afc96c33f` | Keine Startfreigabe |
| `Pulsar b1.0.jar` | 92506539 | `6e9f000215def7b950f70b5ca043cc852c09a2cd4e64e55abd9826b14307248b` | Keine Startfreigabe |

Die kompletten Eintragsmengen sowie **sämtliche Klassen- und Ressourcenbytes**
aller drei Ausgaben stimmen mit dem hier frisch gebauten REL-P01-Ausgangspunkt
überein. Nur der generierte Zeitstempel in `pom.properties` unterscheidet sich.
Alle 14 Windows-DLLs und alle ursprünglich vorhandenen Prüf-/Starter-/POM-Bytes
sind unverändert. Java8-Main und Minecraft-/OptiFine-Klassen sind Major52 oder
älter; die separaten Major53-Modul-/Multi-Release-Metadaten sind inventarisiert.
Alle drei JARs bleiben ausschließlich lokale Buildausgaben; kein JAR-/DLL-
Weitergabeordner wurde angelegt.

Klassenweise Daten, 63 Artefakthashes, 621 Owner-/Winnerzeilen und die erwarteten
JVM-Descriptoren stehen in [DEPENDENCY_ABI_MATRIX.md](DEPENDENCY_ABI_MATRIX.md).
Die unabhängige [Erhaltungskontrolle](F:/neutron-rel-p02-evidence/independent-final-validation.json)
nennt jede einzige Byteabweichung. Artefakthashes identifizieren diesen Lauf;
byteidentische Neubuilds werden nicht behauptet.

## Alle 22 R02-Gates

PASS gilt jeweils nur für das beschriebene Orakel. PARTIAL ist keine Freigabe
der offenen Runtimeflächen. B wurde weder ausgeführt noch durch einen
alternativen Fake-Auth-Start ersetzt.

| ID | Status | Tatsächlicher Befund | Evidenz unter F:/neutron-rel-p02-evidence, sofern kein Report |
|---|---|---|---|
| R02-01 | PASS | Exakte saubere Basis; Branch/Pfad vor Anlage frei. 39 fremde Worktrees vorher vollständig erfasst; parallele Änderungen ausdrücklich ausgewiesen. | source-bindings.json; worktrees-before.json; worktrees-final-comparison.json |
| R02-02 | PASS | Neutron-Client als Repositoryname; historischer Pulsar-Client-Remote nur gelesen und unverändert. | source-bindings.json; final-state.json |
| R02-03 | PASS | Oracle JDK8 x64 / Maven3.8.5 offline; Assembly direkter Main, Major52; kein Toolchain-/Pluginwechsel. | 00-toolchain.log; final/*-javap.txt; final-jar-audit.json |
| R02-04 | PASS | 63 Runtimeartefakte, 621 überlappende Namen, 199 Bytekonflikte frisch ermittelt; Quellen/Winner/Hashes und Descriptoren vollständig. | runtime-dependency-tree.txt; dependency-abi-audit.json; DEPENDENCY_ABI_MATRIX.md |
| R02-05 | PARTIAL | LWJGL-Winner, Anwendungssignaturen, Utilities und Windows64-Timer-JNI geprüft; GLES-Reflexion, macOS und Display-/Treiberpfade offen. | 14-final-abi.log; 13-final-packaged.log; reflection-surface.log |
| R02-06 | PARTIAL | Netty-Bootstrap und echte innere Callbacks mit synthetischen Channels geprüft; Patchy-Zusatzverhalten wird vom Winner überschattet und bleibt ungeklärt. | patchy-callgraph.json; netty-bootstrap-javap.txt; patchy-bootstrap-javap.txt |
| R02-07 | PASS | Vecmath-Konstruktoren, geerbte Tuple-Felder, Matrixoperationen und relevante Anwendungssignaturen geprüft; kein Forge-Spielbetrieb behauptet. | 14-final-abi.log; dependency-abi-audit.json |
| R02-08 | PASS | Identische SoundSystem-Redundanz; reale Ogg/Wav-Registrykonstruktion/Cleanup; Assembly erhält beide Codecs. Kein Audiogerätetest. | 14-final-abi.log; final-jar-audit.json |
| R02-09 | PASS | Eine wirksame Paketklasse je geprüftem FQN; tatsächliche JAR-Herkunft, Log4j-Plugins, SLF4J, Gson/Authlib-Adapter, JNA/OSHI und SPI-/Resourcebestand geprüft. | 13-final-packaged.log; 14-final-abi.log; final-jar-audit.json |
| R02-10 | NOT_RUN | Keine POM-Exclusion oder Dependencyänderung; daher kein vorgetäuschtes produktives RED/GREEN-Paar. Der breite GLES-RED bleibt getrennt dokumentiert. | git BASE..HEAD; 04-draft-abi.log |
| R02-11 | PASS | NO_CHANGE_NEEDED für die Dependencyauswahl der geprüften Windows-Anwendung; Gesamt-ABI bleibt PARTIAL. Keine künstliche POM-Änderung. | tested-input-hashes.json; independent-final-validation.json |
| R02-12 | PARTIAL | Unveränderte acht Paket-/JNI-Fälle und neun neue Paketproben bestehen; optionale GLES-Memberreflexion und vollständige Native-/Clientflächen bleiben unqualifiziert. | 13-final-packaged.log; 14-final-abi.log; reflection-surface.log |
| R02-13 | PARTIAL | Finales mvn -o clean test besteht frisch mit 573/0/0/3; ein früherer vollständiger Lauf hatte einen nicht reparierten Scaffold-Error. | 08-final-clean-test-summary.json; 09-scaffold-diagnostic-summary.json; 10-final-clean-test-summary.json |
| R02-14 | PASS | Finales mvn -o package besteht mit 573/0/0/3; echte JAR-/Major-/Entrypointprüfung. MAIN_ARGUMENTS_ONLY bleibt von einem Fensterstart getrennt. | 11-final-package.log; 12-final-package-summary.json; final-jar-audit.json |
| R02-15 | PASS | Check-only-Plan für getrennte game/cwd/home, Root gegen 42 Schutzwurzeln geprüft; keine Developer-Verzeichnisse oder persönlichen Datenkopien angelegt. | isolation-plan.json; preflight-reviewed.json; DEV_WINDOW_SMOKE.md |
| R02-16 | PASS | Alle neun negativen Preflights erneut gegen die finale Assembly: erwarteter Exit2/BLOCKED, Fixturehashes gleich; alte harte Policy unverändert. | preflight-negative-tests.json; preflight-final-policy.json |
| R02-17 | NOT_RUN | Kein ausdrückliches Nutzer-Opt-in für sichtbaren Start; legitimer Datenbestand und äußere Egress-Sperre nicht bestätigt. Start.java unverändert und nicht ausgeführt. | DEV_WINDOW_SMOKE.md; isolation-plan.json |
| R02-18 | NOT_RUN | Keine laufende Client-/GUI-/Resize-/Quit-/Neustartsitzung. OptiFine-Versionscheck als Netzgrenze statisch dokumentiert, nicht ausgeführt. | DEV_WINDOW_SMOKE.md |
| R02-19 | PASS | Read-only-Prismstrategie mit Microsoft-Anmeldung, Java8 und separatem Loader-/Mapping-/Instanzvertrag; keine behauptete direkte Fat-JAR-Modkompatibilität. | LAUNCHER_STRATEGY_AND_DISTRIBUTION.md |
| R02-20 | PASS | Eigenes Desktop-Launcherziel nur als öffentliche-App-/PKCE-/Entitlement-/Credential-Architektur beschrieben; keine Implementierung oder Account-/Tokenlektüre. | LAUNCHER_STRATEGY_AND_DISTRIBUTION.md |
| R02-21 | PASS | 2510/402/4240 je JAR frisch bestätigt; LOCAL_ONLY/DISTRIBUTION_BLOCKED bleibt. Kein Installer, keine Versions-JSON, kein Testerpaket. | final-jar-audit.json; LAUNCHER_STRATEGY_AND_DISTRIBUTION.md |
| R02-22 | PASS | Exakte sieben erlaubte Dateien, lokale getrennte Test-/Berichtscommits; externer Binärdiff, SHA256, Reverse-Check und sauberer End-HEAD im Receipt. | rel-p02-review.patch; rel-p02-review.patch.sha256; final-state.json |

## Fremde Worktrees und exakte Dateigrenze

39 vorher registrierte fremde Worktrees wurden mit Branch, vollem HEAD,
vollständigem Porcelainstatus einschließlich untracked Namen und SHA256 des
getrackten binären HEAD-Diffs erfasst. Im ersten Nachvergleich waren 37/39
identisch. Beobachtete parallele Änderungen, nicht durch REL-P02 ausgeführt
oder übernommen:

* `F:/Puslar Backup - Copy`: HEAD weiter
  `eeddb7da58978f04fad95ad8042175b7ad848d6a`, Status und getrackter Diff geändert.
* `F:/neutron-vis-qa02-motion-toast`: HEAD weiter
  `a4142772ab3ff64747f22215f922c5891153fe47`, Status und getrackter Diff geändert.
* Zusätzlich erschien `F:/neutron-cmb-t01-v2-c03`; der eigene REL-P02 ist separat.

Ein zwischenzeitlicher Kommentar hatte den dirty Originalcheckout irrtümlich
als unverändert bezeichnet; er wurde nach Lesen des Vergleichs ausdrücklich
korrigiert. Der tatsächliche vollständige Abschlussvergleich ist maßgeblich:
[worktrees-final-comparison.json](F:/neutron-rel-p02-evidence/worktrees-final-comparison.json)
und `final-state.json`. Parallele weitere Änderungen erscheinen dort zusätzlich.
REL-P02 führte dort ausschließlich die dokumentierten Git-/Dateileseaufrufe
aus. Fingerprints sind keine globale Sperre gegen andere Prozesse; untracked
Dateiinhalte wurden nicht pauschal gelesen oder gehasht.

Genau sieben erlaubte Dateien werden committed:

* `src/test/java/me/youngjibbit/release/RelP02DependencyAbiTest.java`
* `src/test/java/me/youngjibbit/release/RelP02AssemblyContractTest.java`
* `src/test/java/me/youngjibbit/release/RelP02PackagedAbiCheck.java`
* `design/neutron-codex-rel-p02/DEPENDENCY_ABI_MATRIX.md`
* `design/neutron-codex-rel-p02/DEV_WINDOW_SMOKE.md`
* `design/neutron-codex-rel-p02/LAUNCHER_STRATEGY_AND_DISTRIBUTION.md`
* `design/neutron-codex-rel-p02/RESULTS.md`

Commit1 enthält nur die drei getesteten Fixtures, Commit2 nur die vier Berichte.
Keine Produktions-, POM-, Starter-, Helper-, Asset-, Account- oder bestehende
Testdatei wird geändert. Externe JSONs/Logs/XMLs/Runner und der Reviewpatch
werden nicht committed. Der finale eigene HEAD kann nicht seinen eigenen Hash
enthalten; sein sauberer Status, Commitliste und Patchhash stehen ausschließlich
im externen Receipt.

## Anschlussentscheidung und STOPP

Der offizielle Launcher wird nicht installiert. Die
[Prism-/Launcherstrategie](LAUNCHER_STRATEGY_AND_DISTRIBUTION.md) beschreibt einen
rechtmäßigen späteren Instanz-/Auth-/Loaderpfad und die offene Mod-only-Arbeit.
Der [Developer-Fensterplan](DEV_WINDOW_SMOKE.md) benötigt freiwilliges Opt-in,
legitime Dateien und eine überprüfte Egress-Isolation; unter anderem versucht
der vorhandene OptiFine-Displaypfad einen HTTP-Versionscheck. Es gab keinen
Client-, MainMenu-, Welt-, Multiplayer- oder Zweit-PC-Test.

Review: [rel-p02-review.patch](F:/neutron-rel-p02-evidence/rel-p02-review.patch),
[SHA256-Sidecar](F:/neutron-rel-p02-evidence/rel-p02-review.patch.sha256),
[final-state.json](F:/neutron-rel-p02-evidence/final-state.json).
Der externe Patch ist ein binärer `git diff BASE..HEAD` über die sieben Dateien,
mit erfolgreichem Reverse-Apply-Check; er enthält keine Spiel-JAR oder Assets.
`artifact-manifest.json` inventarisiert die externen Belege ohne Selbsthash.

**STOPP nach REL-P02. DISTRIBUTION_BLOCKED bleibt bestehen.**
Keine REL-P03, kein Fachbranch-Merge und keine Auth-/Launcherimplementierung
wurden begonnen.
