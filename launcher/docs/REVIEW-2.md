# Vorläufiges Review-Paket — Abschnitt 2

**Teilprüfung; Abschnitt 2 ist nicht vollständig bestanden. Abschnitt 3 bleibt gesperrt.** Der Nutzer hat Computer Use mit der physischen Escape-Taste gestoppt. Danach erfolgten ausschließlich nichtinteraktive Dokumentation, statische/isolierte Unit-Prüfungen und lesende Prozess-/Speicherabfragen. Kein weiterer Clientstart, keine UI-Interaktion, kein Schließen oder Beenden eines Prozesses.

## Änderungen und Schutz paralleler Arbeit

Vor dem Eingriff wurden README, aktueller POM und Client-Einstieg erneut gelesen. Von den 150 erfassten bestehenden Projekt-Fingerprints änderte sich ausschließlich der beabsichtigte POM. Außerhalb `launcher/` sind genau der POM und die zwei neuen Bootstrap-Dateien als neue Änderungen hinzugekommen. Persönliche `test_run/`-Daten und fremde Änderungen blieben unverändert. Keine Combat-/Scaffold- oder GUI-Klasse wurde bearbeitet. Keine Commits, Staging, Pushes, Veröffentlichung oder automatische Issues.

| Absoluter Dateipfad | Änderung |
| --- | --- |
| `F:\Puslar Backup - Copy\pom.xml` | Profil `launcher-dist` für unminimierte produktive JAR/Runtime-Abhängigkeiten; korrigierte `javax.vecmath`-Koordinate; Central/Minecraft vor Legacy-Fallbacks. |
| `F:\Puslar Backup - Copy\src\main\java\me\neutron\launcher\ClientBootstrap.java` | Produktiver Java-8-x64-Einstieg mit expliziten Assets/Natives/CWD, Argumentprüfung und `--validateOnly`. |
| `F:\Puslar Backup - Copy\src\test\java\me\neutron\launcher\ClientBootstrapTest.java` | Sechs isolierte Prüfungen des Bootstrap-Vertrags. |
| `F:\Puslar Backup - Copy\launcher\tools\client_contract.py` | Manifest-/Hash-/Plattformprüfung und strukturierte Startparameter; ausschließlich pro Kindprozess konfigurierte Umgebung. |
| `F:\Puslar Backup - Copy\launcher\tools\build_client.py` | Eigener Quell-Snapshot, Java-8-/Maven-Aufruf ohne Shell-Wrapper, eigener Workspace/Temp, Speicherprüfung und begrenzter Maven-Heap. |
| `F:\Puslar Backup - Copy\launcher\tools\package_client.py` | Neues unveränderliches Distributionsziel, gehashter Classpath und gezielter Shader-Ressourcenalias, ohne GUI-Code zu ändern. |
| `F:\Puslar Backup - Copy\launcher\tools\provision_minecraft.py` | Entwicklerfixture für offizielle 1.8.9-Assets und plattformspezifisch gepaarte LWJGL-Java-/Native-Dateien. |
| `F:\Puslar Backup - Copy\launcher\tools\run_client.py` | Expliziter Entwickler-Start-/Validierungsaufruf. |
| `F:\Puslar Backup - Copy\launcher\tools\tests\test_contract.py` | Acht Offline-Prüfungen zu beschädigten Dateien, Pfaden/Archiven, Plattform/RAM, Downloads und Shader-Alias. |
| `F:\Puslar Backup - Copy\launcher\docs\STARTABILITY.md` | Vertrag, reproduzierbare Entwicklerkommandos, Plattformgrenzen und noch nötige Veröffentlichung in der Quell-Repository. |
| `F:\Puslar Backup - Copy\launcher\docs\PLAN.md` | Teilprüfungsstatus und ausdrückliche Sperre für Abschnitt 3. |
| `F:\Puslar Backup - Copy\launcher\docs\GATES.md` | Tatsächliche Nachweise und offene Plattform-/UI-/Release-Gates. |
| `F:\Puslar Backup - Copy\launcher\docs\REVIEW-2.md` | Dieses vorläufige Paket. |
| `F:\Puslar Backup - Copy\launcher\docs\evidence\section-2\` | Vollständige tatsächliche Build-/Testprotokolle, Manifeste, Hash-/Prozess-/Speichernachweise, Crash und Screenshots. Einzeldateien mit absoluten Pfaden: `changed-files.json`. |

Die Python-Werkzeuge sind Entwicklerfixtures, keine Electron-Integration oder Frischsystem-Installation. Der endgültige produktive Quell-/POM-Stand entspricht dem erfolgreich kompilierten Cold-Cache-Snapshot.

## Build- und Testnachweise

Umgebung: Windows 11 x64; explizites Temurin-JDK 8u482 x64 und Maven 3.9.15, Python 3.13.16. Globale Umgebungs-/Registry-Einstellungen wurden nicht geschrieben. `JAVA_HOME`, Temp und bereinigte JVM-Variablen gelten ausschließlich für Kindprozesse.

| Prüfung | Tatsächliches Ergebnis | Nachweis unter `evidence/section-2/` |
| --- | --- | --- |
| Warmer Cache, isolierter Build mit Leerzeichen/Umlaut im Pfad | BUILD SUCCESS; 400 Tests, 0 Failures, 0 Errors, 3 Skipped. Ursprünglicher Paketierungs-POM vor den Repository-/Koordinatenkorrekturen. | `warm-maven-output.txt`, `warm-build-command.json` |
| Neuer leerer Cache auf F:, endgültiger POM | BUILD SUCCESS; 400 Tests, 0 Failures, 0 Errors, 3 Skipped. | `cold-maven-output.txt`, `cold-build-command.json` |
| Neue Bootstrap-Tests | Je Build 6 Tests, 0 Failures/Errors/Skipped. | `warm-bootstrap-tests.txt`, `cold-bootstrap-tests.txt` |
| Werkzeugtests | 8 Tests, Exit 0; kein Client-/UI-Prozess gestartet. Temp ausschließlich auf F:. | `python-unit-tests.txt` |
| Endgültige Distribution | Alle 63 Classpath-Dateien stimmen mit Manifest-Hashes überein; produktiver Einstieg vorhanden; `Start.class` fehlt; Main-Class korrekt. | `final-launch-manifest.json`, `verification.json` |
| Ressourcenalias | Genau ein `Tenacity/shaders/vertex.vsh`-Eintrag, bytegleich zum vorhandenen `Tenacity/Shaders/vertex.vsh`; idempotenter Unit-Test. | `verification.json`, `python-unit-tests.txt` |
| Offizielle Assets | Alle 722 eindeutigen Objekte anhand Größe/SHA-1 geprüft; keine übernommenen persönlichen Assets/Profile. | `verification.json`, `windows-x64-runtime-manifest.json` |
| Windows-/Linux-/macOS-Native-Dateien | Offizielle Archive und Java-Paarung verifiziert; PE-/ELF-/Mach-O-Header untersucht. | `*-runtime-manifest.json`, `native-architecture-inspection.json` |
| Tatsächliches Windows-Clientmenü | Endgültiges Cold-Cache-Paket mit offiziellen Assets/Natives und synthetischem `LauncherSmoke`-Profil sichtbar. Kein Test-Classpath. | `final-client-menu.png/.json`, `final-client-run.json`, `final-client-output.txt` |
| Settings erneut öffnen | **NICHT ausgeführt/abgeschlossen: Nutzer stoppte Computer Use; Ausgang des unterbrochenen Klicks nicht verifiziert.** | `uiChecksNotExecuted` in `verification.json` |
| Reguläres Beenden des endgültigen Laufs | **NICHT ausgeführt: Nutzerabbruch.** Später nur lesend PID 72872 nicht mehr vorhanden und protokollierter Exitcode 0 festgestellt. Keine Aussage, wer oder welche Aktion den Prozess beendet hat. | `finalClientProcessReadOnlyObservation` in `verification.json` |
| Linux/macOS/Apple Silicon, Frischsystem, Installer | **NICHT ausgeführt.** | Gate-Matrix |

Der erste zusätzliche Kopierversuch auf C: scheiterte mit `MemoryError`, der nächste mit `No space left on device`, jeweils vor Maven. Dies sind fehlgeschlagene Ressourcenversuche und keine erfolgreichen Cold-Cache-Builds. Transkribierte Tool-Fehler und die gemeinsame Lösch-Ablehnung: `resource-failures.json`.

Der Cold-Cache-Build emittierte 16 Warnungen zu fehlenden Repository-Prüfsummen. Sein Classpath enthält fünf zusätzliche Twitch-Native-Archive gegenüber dem warmen Cache. Alle gemeinsamen Bibliotheks-JARs sind per SHA-256 gleich; die Client-JAR unterscheidet sich erwartbar durch Build/Paketierung. Ein vollständiger Dependency-Lock-/Lieferkettennachweis ist damit nicht erbracht.

## Endgültiger Build und Screenshot-Zuordnung

| Artefakt | Absoluter Pfad |
| --- | --- |
| Erfolgreicher Cold-Cache-Workspace | `F:\Neutron Launcher Section2 20261028\Cold Cache Build` |
| Exakter Quell-Snapshot | `F:\Neutron Launcher Section2 20261028\Cold Cache Build\source-snapshot.json` |
| Leerer, für diesen Lauf neu gefüllter Maven-Cache | `F:\Neutron Launcher Section2 20261028\Empty Maven Cache` |
| Endgültiges Paket | `F:\Neutron Launcher Section2 20261028\Final Distribution` |
| JAR | `F:\Neutron Launcher Section2 20261028\Final Distribution\neutron-client.jar` |
| Launch-Manifest | `F:\Neutron Launcher Section2 20261028\Final Distribution\launch-manifest.json` |
| Tatsächlicher Start-/Exitdatensatz | `F:\Neutron Launcher Section2 20261028\final-client-run.json` |
| Isoliertes Spielverzeichnis | `F:\Neutron Launcher Section2 20261028\Final Spiel Prüfung` |
| Verwendete Windows-Runtime | `C:\Users\Administrator\AppData\Local\Temp\neutron-launcher-section2-20261028\Runtime Windows Prüfung` |
| Endgültiger Menü-Screenshot | `F:\Puslar Backup - Copy\launcher\docs\evidence\section-2\final-client-menu.png` |

Quell-Snapshot: `266eb684ed33b3607ac484e1dd0dc31868612bc10bcae1448e97a06dbef13fd9`.

Finale JAR SHA-256: `3696f2c7c41c5db7de6e99f342210861a55a4f4df4901a179de3546494fbe3d8`.

Finales Manifest SHA-256: `065feab0692fe5d58c0b704d6a2bb2ace5ad6132d1e801b3d58325e333fbde03`.

Die Zuordnung verbindet Startkommando/Classpath, finales Paket und Manifest, beobachtetes Java-8-Fenster mit `LauncherSmoke`-Titel und gespeicherte Screenshot-Bytes. Der Screenshot selbst enthält keine PID. Sein ursprüngliches Metadatenfeld `capturedAtUtc` wurde beim Speichern des bereits beobachteten Bildpuffers gesetzt; es ist die Persistierungszeit, kein exakter Bildaufnahmezeitpunkt. Daher kann dieser Wert nach dem gelesenen Prozessende liegen. Diese Grenze wird in den Metadaten ausdrücklich ergänzt; das Bild wird nicht verändert.

`windows-client-menu.png` gehört zum früheren Warm-Cache-Paket vor dem Alias. Dessen anschließend protokollierter Settings-Crash steht in `before-alias-settings-crash.txt` und `windows-client-output.txt`. Dieses frühere Bild ist kein Nachweis für die finale Shader-Korrektur. Der Alias ist bisher statisch/unit-geprüft; der abschließende Settings-UI-Test fehlt.

## Ressourcenlage und belassene Verzeichnisse

Lesende Aufnahme vom 28. Oktober 2026, 16:58:54 Uhr Berlin: C: rund **51 MiB frei**, F: rund **92,4 GiB frei**. Frühere Aufnahme und vollständige Größen aller eigenen Prüfverzeichnisse: `disk-space-initial.json` und `disk-space-inspection.json`. Die Werte sind Momentaufnahmen auf einem gemeinsam genutzten Rechner.

Die C:-Prüfverzeichnisse liegen unter `C:\Users\Administrator\AppData\Local\Temp\neutron-launcher-section2-20261028\`:

| Verzeichnis | Tatsächliche Dateibytes |
| --- | ---: |
| `Build mit Umlaut Prüfung` | 442.663.802 |
| `Cold Cache Build` | 19.197.601 |
| `Cold Cache Build Retry` | 8.970.218 |
| `Runtime Windows Prüfung` | 150.161.557 |
| `Runtime Linux Candidate` | 3.575.510 |
| `Runtime macOS Candidate` | 5.691.389 |
| `Spiel ohne persönliche Daten Prüfung` | 34.245 |

Die F:-Prüfverzeichnisse liegen unter `F:\Neutron Launcher Section2 20261028\`:

| Verzeichnis | Tatsächliche Dateibytes |
| --- | ---: |
| `Cold Cache Build` | 472.513.673 |
| `Empty Maven Cache` | 100.539.740 |
| `Final Distribution` | 136.475.443 |
| `Warm Distribution Casefix` | 121.853.027 |
| `Final Spiel Prüfung` | 288.709 |
| `Spiel Prüfung mit Leerzeichen` | 295.024 |
| `Unit Test Temp` | 0 |

Dies sind Summen der Dateilängen, keine belegten NTFS-Cluster. Build-Snapshots enthalten ausschließlich POM und benötigte Quellen/Ressourcen/Tests unter `src/`; kein `test_run`, keine Welten, kein bestehendes gemeinsames `target/` und kein `.git`. Neue Build-/Testartefakte entstanden im jeweils eigenen Workspace. Seit der Ressourcenstörung keine großen Kopien/Downloads mehr auf C:.

Die automatische Freigabeprüfung lehnte einen gemeinsamen rekursiven Löschaufruf für diese beiden Ziele mit **„blocked by policy“** ab:

- `C:\Users\Administrator\AppData\Local\Temp\neutron-launcher-section2-20261028\Cold Cache Build`
- `C:\Users\Administrator\AppData\Local\Temp\neutron-launcher-section2-20261028\Cold Cache Build Retry`

Der Aufruf wurde nicht ausgeführt. Beide Verzeichnisse wurden belassen; keine Wiederholung oder Umgehung. Der aktuelle Paketierer kopiert ausschließlich benötigte Dateien in ein neues Ziel und benötigt keine rekursive Löschung.

## Grenzen und nächste Review-Entscheidung

Noch offen: Settings-Nachtest, reguläres Beenden als geprüfte Interaktion, Linux- und macOS-Intel-Start, Apple-Silicon-x64-Pfad, frische Betriebssysteme ohne Entwicklerwerkzeuge, automatisches lokales JDK/Maven-Setup, Installer/CI, Signierung und Notarisierung. Legacy-Repository-Prüfsummen und Dependency-Lock müssen vor Auslieferung weiter abgesichert werden. Impeccables Engine bleibt vor einem später freizugebenden Abschnitt 3 ungeprüft.

Es wurde ein lokaler Dirty-Snapshot von HEAD `eeddb7da58978f04fad95ad8042175b7ad848d6a` gebaut. Der in Abschnitt 1 read-only ermittelte Neutron-Remote-`main`-Commit `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9` wurde nicht gebaut. Die produktiven POM-/Bootstrap-/Paketierungsänderungen müssten für spätere Spieler-Builds erst in die vereinbarte GitHub-Quelle veröffentlicht werden; hier wurde nichts gepusht.

**Abschnitt 2 nicht vollständig bestanden. Abschnitt 3 bleibt gesperrt bis zur Entscheidung des Review-Chats.** Weitere UI-Interaktion, Neustart oder Schließen erfolgen erst nach erneuter Nutzerfreigabe.

REVIEW BEREIT – Abschnitt 2 (Teilprüfung; UI-Prüfung vom Nutzer gestoppt)
