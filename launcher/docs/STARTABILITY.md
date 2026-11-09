# Produktiver Client-Start — Abschnitt 2

Diese Werkzeuge prüfen den Client-Vertrag vor der Electron-Integration. Sie sind Entwicklerwerkzeuge mit Python 3, einem expliziten Java-8-JDK und Maven 3. Der Spieler benötigt später weder Python noch globales Java/Maven/Git; der Erststart-Installer folgt in Abschnitt 4.

## Einstieg und Paket

`me.neutron.launcher.ClientBootstrap` liegt in den Hauptquellen. Er verlangt Java 8 x64 sowie absolute vorhandene Verzeichnisse für Spiel, Assets und Natives. Das Prozessarbeitsverzeichnis muss dem Spielverzeichnis entsprechen. Asset-Index `1.8`, Version und passende native Dateinamen sind Pflicht. `--validateOnly` prüft diese Bedingungen ohne Fenster. Die Anmeldung bleibt im Client; die initiale Offline-Sitzung erhält den Platzhalter-Token `0`.

Das opt-in Maven-Profil `launcher-dist` erzeugt eine unminimierte Client-JAR und vollständige Runtime-Abhängigkeiten in Repository-Verzeichnisstruktur. Im Profil sind die alten Assembly-/Shade-Executions aus dem Lifecycle genommen; außerhalb des Profils bleiben sie bestehen. Die Distributionswerkzeuge verwenden die JAR und die explizite Classpath-Liste, keinen Test-Classpath und keine Test-Startklasse. `java -jar` allein ist wegen der externen Bibliotheken kein vollständiger Startbefehl.

Im POM wurde außerdem die nicht existierende Maven-Koordinate `Javax.vecmath:vecmath:1.5.2` auf die veröffentlichte Koordinate `javax.vecmath:vecmath:1.5.2` korrigiert. Auf Windows konnte ein bereits gefüllter Cache die falsche Großschreibung verdecken. Central steht jetzt vor den Legacy-Fallbacks und verwendet `https://repo.maven.apache.org/maven2`.

## Isolierter Build

Beispiel in PowerShell, mit ausdrücklich gewählten Werkzeugpfaden und einem neuen Workspace außerhalb des gemeinsamen Repositorys:

```powershell
python -B launcher/tools/build_client.py --source 'F:\Puslar Backup - Copy' --workspace 'F:\Neutron Build' --java-home '<Java-8-JDK>' --maven-home '<Maven-3.9.15>' --repository 'F:\Neutron Maven Cache'
```

Für einen vorhandenen Cache ist `--offline` möglich. Das Ziel darf vorher nicht existieren. Der Build kopiert ausschließlich `pom.xml` und `src/`, prüft jede kopierte Datei und protokolliert den Quell-Snapshot. `target/`, persönliche Laufdaten, Git-Verzeichnis und Git-Zugangsdaten werden nicht kopiert. Maven wird direkt über seine Classworlds-Java-Klasse gestartet; kein Shell-Wrapper, kein globales `JAVA_HOME`, kein globaler PATH-Eingriff. JVM-Injection-Umgebungsvariablen werden ausschließlich für die Kindprozesse entfernt. Die Systemumgebung wird nicht gespeichert oder verändert.

Ausgaben im eigenen Workspace:

| Pfad | Bedeutung |
| --- | --- |
| `source-snapshot.json` | Lokaler Git-HEAD, Dirty-Markierung und SHA-256 jeder tatsächlich kopierten Quell-/POM-Datei. |
| `build-command.json` | Exakte Java-/Maven-Argumente, Arbeitsverzeichnis und Java-Version. |
| `maven-output.txt` | Ungekürzte lokale Buildausgabe. |
| `distribution/neutron-client.jar` | Produktive Klassen/Ressourcen, ohne `Start.class`. |
| `distribution/libraries/` | Runtime-Abhängigkeiten; keine JUnit-Testabhängigkeiten. |
| `distribution/launch-manifest.json` | Echter HEAD/Snapshot, Buildzeit, Version, Einstieg, Java-/Pfadanforderungen und gehashter Classpath. |

`package_client.py` ist der separat wiederholbare Paketierungsschritt. Er liest ausschließlich einen erfolgreichen isolierten Maven-Build und erzeugt immer ein neues Distributionsverzeichnis. Er kopiert nur benötigte Runtime-JARs und benötigt keine rekursiven Löschungen. Ein gezielter Ressourcen-Alias erhält das lose Windows-Verhalten im case-sensitiven JAR: `Tenacity/shaders/vertex.vsh` verweist auf dieselben Bytes wie die vorhandene Datei `Tenacity/Shaders/vertex.vsh`. Ohne diesen Alias stürzte der tatsächliche paketierte Client beim Öffnen der Einstellungen ab. Es wurde keine GUI-Klasse geändert. Alias und SHA-256 des Paketierungswerkzeugs stehen im Manifest; der Test prüft auch, dass kein doppelter ZIP-Eintrag entsteht.

Wenn nur die Paketierung geändert wurde, kann sie ohne erneute Kompilierung in ein neues Ziel ausgeführt werden:

```powershell
python -B launcher/tools/package_client.py --workspace '<erfolgreicher isolierter Build>' --destination '<neue Distribution>'
```

„Reproduzierbar“ bezeichnet hier festgehaltene Eingaben, Werkzeuge und Befehle. Byte-identische JARs sind noch kein behaupteter Nachweis: Buildzeit und JAR-Zeitstempel sind nicht normalisiert. Ein lokaler Dirty-Snapshot heißt entsprechend `Development-<HEAD>-local`; er wird nicht als unveränderter GitHub-Build bezeichnet.

Vor dem Kopieren wird der benötigte freie Platz aus Quellgröße und einer Reserve für Build/Cache geschätzt und geprüft. Maven-Heap und temporäre Verzeichnisse sind explizit auf den eigenen Workspace begrenzt. Freier Platz ist trotzdem kein dauerhafter Anspruch auf Speicher: Parallelprozesse können die Partition füllen. Im tatsächlichen Versuch gab es vor Maven einen `MemoryError` und beim nächsten Kopieren `No space left on device` auf C:. Danach wurden große Prüfungen auf F: verlegt. Die zwei unvollständigen Verzeichnisse blieben nach einer automatischen Lösch-Ablehnung bestehen; kein Umgehungsversuch. Kapazitäten und Größen stehen im Review-Nachweis.

## Offizielle Minecraft-Laufzeitdateien

```powershell
python -B launcher/tools/provision_minecraft.py --destination 'F:\Neutron Runtime' --platform windows-x64
```

Der Adapter verwendet die unveränderliche [Mojang-Metadatei für 1.8.9](https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json), prüft deren veröffentlichten SHA-1, anschließend Asset-Index, Asset-Objekte, Java-Bindings und Native-Archive anhand ihrer offiziellen Größen/Hashes. Lokale Manifeste halten zusätzlich SHA-256 der Java-/Native-Dateien fest. Downloads verwenden die offiziellen HTTPS-Hosts. Erst vollständig geprüfte temporäre Dateien ersetzen ein Ziel. Archive werden auf Zielausbruch und Symlinks geprüft. Dies ist die Validierungsfixture; robuste Wiederaufnahme, Download-UI, Speicherplatzplanung und Prozessabbruch des Spieler-Installers folgen in Abschnitt 4.

Die Fixture kopiert keine Assets oder persönlichen Profile aus `test_run/`. 722 eindeutige Asset-Objekte werden aus dem offiziellen Index geladen. Das Laufzeitverzeichnis enthält `runtime-manifest.json`, `assets/`, `natives/`, die geprüften Archive und genau ein plattformspezifisches LWJGL-Java-/Util-Paar.

| Kandidat | Java-Bindings und Natives | Zusätzliche Voraussetzungen und noch nötiger Nachweis |
| --- | --- | --- |
| Windows x64 | LWJGL `2.9.4-nightly-20150209`, JInput und Twitch x64 aus den offiziellen Regeln. | Tatsächlicher Client-Start; später Frischsystemprüfung. |
| Linux x64 | Dasselbe LWJGL-2.9.4-Paar, Linux-JInput/OpenAL; kein Twitch-Native. | Grafische X11-/kompatible Desktopumgebung, Systembibliotheken und Treiber auf echter Linux-Umgebung prüfen. |
| macOS Intel | LWJGL `2.9.2-nightly-20140822` gemäß Mojangs macOS-Regeln, JInput/OpenAL/Twitch. | x64-Java 8, `-XstartOnFirstThread`, echtes Clientfenster und native Abhängigkeiten prüfen. |
| Apple Silicon | Derselbe macOS-x64-Pfad, keine ARM-Natives. | Vorhandener x64-Kompatibilitätspfad und x64-Java-Prozess auf echtem Gerät prüfen; nicht als unterstützt bezeichnen. |

`--platform linux-x64 --natives-only` bzw. `macos-x64 --natives-only` lädt und untersucht die jeweiligen Lieferdateien ohne die gemeinsamen Assets. Solche Fixtures sind nicht startfähig. Die Runtime-Manifeste nennen sie ausdrücklich Kandidaten. Ein PE-/ELF-/Mach-O-Header mit x64-Code ist kein Laufnachweis.

Der Maven-Distributions-Classpath enthält absichtlich keine generischen LWJGL-JARs aus den alten `org.lwjgl`-Koordinaten. Beim Start wird das zum Betriebssystem passende offizielle Java-/Native-Paar gemeinsam eingesetzt. So verwendet macOS nicht versehentlich die Windows-/Linux-Java-Bindings.

## Validieren und starten

Das Spielverzeichnis zuerst separat anlegen; keine persönlichen Spieldaten verwenden:

```powershell
python -B launcher/tools/run_client.py --distribution 'F:\Neutron Build\distribution' --runtime 'F:\Neutron Runtime' --java-home '<Java-8-JDK>' --game-dir 'F:\Neutron Spiel' --validate-only
```

Ohne `--validate-only` öffnet sich der Client. Der Start prüft zuvor Classpath-Dateien, Natives, Index und alle Asset-Objekte vollständig. Arbeitsverzeichnis und Argumente werden als strukturierte Prozessparameter übergeben. `java.library.path`, LWJGL- und JInput-Pfad sind explizit. Der Entwickler-Smoke-Test nutzt 1024 MiB Heap, 960×640 und das synthetische Offline-Profil `LauncherSmoke`. Dies legt die späteren Spielereinstellungen/RAM-Automatik nicht vorzeitig fest.

## GitHub und Veröffentlichung

Die vereinbarte Quelle bleibt `YoungJibbit95/Neutron-Client`, `main`. Die lokalen Änderungen dieses Abschnitts liegen noch nicht automatisch auf GitHub. Vor einem künftig vom Launcher heruntergeladenen GitHub-Build müssen mindestens der finale POM, `ClientBootstrap.java` und die geprüften Distributions-/Runtime-Verträge in der vereinbarten Quelle veröffentlicht werden. Dieser Auftrag veröffentlicht oder pusht nichts.

Die Python-Buildfixture verwendet Git nur zur lokalen Entwickler-Provenienz. Der spätere Spieler-Build muss die Commit-ID aus dem GitHub-Archiv-/Buildauftrag übernehmen und darf kein Git voraussetzen. Die Electron-Integration entsteht erst in Abschnitt 4.

## Offene Auslieferungsbedingungen

Ein durchgelaufener Build mit lokal vorhandener Java-/Maven-Installation beweist keine automatische Toolchain-Einrichtung. Für Abschnitt 4 sind lokal verwaltete Java-8-JDKs mit Anbieter-Prüfsummen (beispielsweise Temurin x64 je OS) und eine gepinnte Maven-Distribution mit Apache-Prüfsumme einzubinden. Installer/Signierung, neue Rechner ohne Entwicklungswerkzeuge, Systembibliotheken und reale Linux-/macOS-/Apple-Silicon-Starts bleiben eigenständige Gates.

Die tatsächlichen Ergebnisse stehen im [vorläufigen Review-Paket](REVIEW-2.md) und in `evidence/section-2/`. Beide Builds bestanden mit jeweils 400 Tests, 0 Fehlern und 3 übersprungenen Tests; acht Werkzeugtests bestehen. Das endgültige Clientmenü ist fotografisch belegt. Der Settings-Nachtest und das reguläre Beenden wurden wegen des Escape-Abbruchs der Computersteuerung durch den Nutzer NICHT ausgeführt. Die Shader-Korrektur ist deshalb bisher statisch und als Paketierungs-Unit-Test geprüft. Der nachträglich gelesene Exitcode 0 ersetzt keinen abgeschlossenen Interaktionstest.

Der Cold-Cache-Build emittierte 16 Warnungen zu fehlenden Repository-Prüfsummen. Seine Distributionsliste enthält fünf zusätzliche Twitch-Native-Archive gegenüber dem vorhandenen Cache; alle gemeinsamen Bibliotheks-JARs sind per Hash gleich. Daraus folgt noch kein plattformübergreifender Dependency-Lock oder vollständig geprüfter Lieferweg für alle Maven-Abhängigkeiten. Nicht ausgeführte Plattformstarts bleiben offen, auch wenn ihre Native-Dateien bereits verifiziert wurden.

Abschnitt 2 ist nicht vollständig bestanden. Abschnitt 3 bleibt gesperrt. Nach Nutzerabbruch keine weitere Computersteuerung, kein Neustart und kein Schließen ohne erneute Nutzerfreigabe. Die obigen Startkommandos dokumentieren den Vertrag und sind keine Anweisung, die Unterbrechung zu umgehen.
