# Windows-Integration und Kandidatenvertrag

Stand: 28. Oktober 2026. Abschnitt 3 ist nach unabhängigen Typechecks und 94 Tests technisch freigegeben, weiterhin ohne visuelle Abnahme. Der Review-Chat hat echte Integration und Windows-Installer-Vorbereitung autorisiert. Kein Release-Go liegt vor.

## Echter Ablauf

Der normale Renderer lädt einen leeren echten Anfangszustand und danach einen Backend-Snapshot. Er zeigt keine installierten Fixture-Builds und keine simulierten Logzeilen. `App`-Seeds dienen ausschließlich Tests. Der Preload bietet Snapshot, validierte Domänenkommandos und abmeldbare Snapshot-/Schließereignisse. Main prüft WebContents, Main Frame und die exakte lokale Dokument-URL.

| Modul unter `electron/` | Verantwortung |
| --- | --- |
| `contracts.cts`, `preload.cts`, `main.cts` | Daten-/Kommandotypen, Laufzeitvalidierung, Main-Frame-Prüfung, lokale Protokollauslieferung, echte Datei-/Exportdialoge und Schließaktionen. |
| `files.cts`, `archives.cts`, `network.cts` | Atomare JSON-Dateien, SHA-Prüfungen, Root-/Junction-/Archivgrenzen, Größenlimits und geprüfte HTTPS-Weiterleitungen. |
| `toolchain.cts`, `minecraft.cts` | Eigene Toolchain, Java-8-x64-Prüfung, 1.8.9-Metadaten, Asset-Objekte und passende Windows-Natives einschließlich PE-x64-Prüfung. |
| `legacy-dependencies.cts`, `distribution.cts` | Offiziell gehashte Legacy-Artefakte, Commit-Archiv, enger POM-/Bootstrap-Adapter, Maven ohne Shell, gehashte Distribution und Aktivierungskandidat. |
| `processes.cts`, `service.cts` | Bereinigte Kindumgebung, Prozessbaum-Abbruch, persistente Versionen/Einstellungen, Aufgaben-/Clientschutz, Startargumente und Zustandsereignisse. |
| `logs.cts` | Begrenzte UI-Zeilen, gebündelte Dateischreibvorgänge, Rotation, Wiederherstellung vorheriger Ausgabe und redigierter Export. |

Setup prüft mindestens 3 GiB freien Platz, lädt Java/Maven und die offizielle Spiel-Laufzeit und speichert erst nach erfolgreicher Prüfung deren Auswahl. Abgebrochene Objekte bleiben als unvollständige Daten außerhalb aktiver Versionen; vollständige hashgeprüfte Cacheobjekte bleiben wiederverwendbar.

Build löst `main` auf einen vollständigen Commit auf. Ein neues Arbeitsverzeichnis erhält nur `pom.xml` und `src/` aus dem GitHub-Archiv. Keine lokalen Clientdateien, `.mvn`-Skripte, vorhandenen Targets, Git-Zugangsdaten oder Spielstände werden übernommen. Maven erhält explizit 64 MiB Startheap und maximal 1536 MiB. Die ausschließlich für den Build neu gesetzte `JAVA_TOOL_OPTIONS`-Variable begrenzt Surefire und weitere Test-JVMs auf 32–512 MiB; geerbte JVM-Injektionen werden vorher entfernt. Clientstart und Java-Prüfungen verwenden weiterhin die bereinigte Umgebung ohne diese Build-Variable. Archiv-SHA256, Adapter-SHA256 und Quell-Fingerprints bleiben nachvollziehbar. Maven wird direkt über Java/Classworlds, eigene Settings/Repository/Temp und strikte Download-Prüfsummen gestartet. Ein neuer Kandidat wird vollständig gehasht und dann in ein eigenes Versionsverzeichnis verschoben. Erst danach wird die Auswahl atomar gespeichert. Alte Versionen bleiben erhalten und können nach Prüfung zurückgewählt werden.

Clientstart prüft Distribution, Laufzeit, jedes Asset und Java-Version/Architektur erneut. Arbeitsverzeichnis, Assets, Natives, Heap, Fenstergröße und Vollbild werden explizit übergeben. Der Spielordner darf nicht innerhalb der installierten Programmdateien oder verwalteten Builddateien liegen. Zusätzliche JVM-Argumente sind auf die im UI genannten GC-Schalter beschränkt; freie Agenten-/Classpath-/Heap-Angaben werden abgelehnt. Benutzerdefiniertes Java wird separat auf Windows x64/Java 8 geprüft.

Der Client verwendet nach der Java-8-Prüfung das benachbarte `javaw.exe` und einen explizit sichtbaren Prozessstart (`windowsHide: false`). Dadurch entsteht kein separates Konsolenfenster, und das erste LWJGL-Spielfenster erhält kein `SW_HIDE`. Verwaltete Tools, Maven und Identitätsabfragen behalten den versteckten Standard. GUI-Sichtbarkeit ist eine interne Prozesseigenschaft, kein Renderer-/IPC-Kommando.

Normales Schließen bei aktiver Aufgabe/Client fragt nach Hintergrund, Beenden oder Zurück. Beenden bricht die Aufgabe ab und stoppt den bekannten Prozessbaum; Windows verwendet die eigene PID mit `taskkill /T /F`, ohne freie Shellargumente. Ein zweiter Electron-Launcher für denselben Datenbereich wird durch den Single-Instance-Lock verhindert.

Ein Stop-Versuch und das tatsächliche Prozessende sind getrennt. `exited` endet erst bei OS-`close` oder einem fehlgeschlagenen Spawn. Fehlgeschlagene oder nach zehn Sekunden nicht abgeschlossene Terminierung weist sichtbar zurück und lässt Prozesshandle, Lease und Schutz bestehen. Neue Versuche verwenden dasselbe Handle. Aufgaben behalten ihre Prozessgruppe bis zum tatsächlichen Ende; ein nach fehlgeschlagenem Abbruch fortlaufender Mavenprozess darf keine neue Version aktivieren. Shutdown meldet einen Stop-Fehler und erlaubt erneuten Versuch, statt Erfolg zu behaupten oder einen neuen Start freizugeben.

`lease.cts` hinterlegt für einen gestarteten Client PID, Programmdatei, Startzeit und Befehlszeilen-Hash. Nach unerwartetem Launcherabbruch kann ein passender Prozess wieder als geschützt erkannt werden. PID-Wiederverwendung oder abweichende Identität werden vor einem Stopp geprüft; ein nicht prüfbarer Datensatz blockiert neue Builds/Starts. Die Windows-Identitätsabfrage läuft ohne Fenster, mit fester CIM-Abfrage und Timeout. Identitätsprüfung und Abfrage des eigenen Testprozesses sind getestet; ein echter Client-Wiederanlauf bleibt offen. Neue stdout-Ausgabe eines übernommenen Prozesses kann nicht wieder angebunden werden; vorhandene Dateilogs bleiben erhalten.

## Gepinnte Lieferungen

- Eclipse Temurin **8u504-b01, Windows x64 JDK**, SHA256 `ea43d46ede95b51e44a12c66711706cddc762e0a766c54bccea18954e902b2aa`, 106.457.258 Bytes. Quelle: [Adoptium-Artefakt](https://github.com/adoptium/temurin8-binaries/releases/tag/jdk8u504-b01). Kein globales JAVA_HOME/PATH.
- Apache Maven **3.9.16**, ZIP-SHA512 `ed41650d42485cfc243fad22158caf9cbb5dc408ce7a09ddb94dd42a019de929ca43065bfa450612cf12bf78b5cafa3884b96c090de326ff590448c933454af3`. Quelle: [Maven Central](https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/).
- Minecraft 1.8.9: [offizielle Metadaten](https://piston-meta.mojang.com/v1/packages/d546f1707a3f2b7d034eece5ea2e311eda875787/1.8.9.json), festes SHA1 `d546f1707a3f2b7d034eece5ea2e311eda875787`; nachfolgende Größen/Hashes aus diesem Dokument. Aufgelöster Index 1.8, 722 eindeutige Objekte und zusammenpassendes LWJGL-2.9.4-Paar für Windows. Lokale Manifeste ergänzen SHA256.

Prüfsummen schützen die konkrete Lieferung; daraus wird kein Signierungs- oder Frischsystem-Pass abgeleitet. Installer-Zertifikat/Herausgeber und SmartScreen bleiben offen.

## Enger Quelladapter

Der unmodifizierte Remote-POM normalisiert auf SHA256 `5fc71918162988da5bcfc761e9fcc97febc82e84665793e5ba1203cb96e8a432`. Nur dieser bekannte POM wird adaptiert. Anderer Buildinhalt führt zu einem erklärten Fehler, ohne Aktivierung eines neuen Builds.

`resources/source-adapter/` enthält ausschließlich den bereits geprüften produktiven Bootstrap, dessen Tests und den opt-in Distributions-POM. Gegenüber Abschnitt 2 wurden nur die für einen strikten Cold-Build notwendigen Legacy-Korrekturen im Adapter ergänzt: ICU unter `com.ibm.icu`, Paulscode unter `com.paulscode`, LWJGL unter `org.lwjgl.lwjgl` mit offizieller Windows-Version. Das unbenutzte, offiziell nicht gelieferte `patchy:1.7.7` entfällt. Die gemeinsame Root-POM-/Clientquelle wurde nicht bearbeitet. Headless-Tests sind im Adapter explizit gesetzt.

Acht offizielle Legacy-JARs besitzen unvollständige Maven-Metadaten. Der Launcher lädt sie anhand der gepinnten Mojang-Hashes und erzeugt minimale lokale POMs mit Herkunfts-/Hashbeleg. Strikte Prüfsummen bleiben für alle übrigen Maven-Downloads aktiv. Der erste echte Versuch scheiterte genau an fehlenden Prüfsummen der falsch benannten Legacy-Artefakte; diese Sperre wurde nicht abgeschaltet. Nicht-JAR-Inhalte werden vor Aufnahme in die Distribution abgewiesen. Der Shader-Pfadalias bleibt eine ZIP-Paketierungsanpassung ohne GUI-Klassenänderung.

## Nachweise und Grenzen

Nichtinteraktive Tests arbeiten mit echten temporären Dateien, Archivdateien und eigenen Hilfsprozessen. Netzwerkfehler werden gezielt injiziert und entsprechend bezeichnet. Ein echter GitHub-Archiv-/Maven-Build mit eigener heruntergeladener Toolchain und Bootstrap-`--validateOnly` ist getrennt davon protokolliert. `--validateOnly` öffnet kein Spiel und ersetzt keinen Clientstart.

R3 wurde inzwischen real auf F: installiert; die 76 installierten App-Dateien stimmen bytegleich mit dem Paket überein. Installierter Launcher, echte Ersteinrichtung, vier Themes in DE/EN, 125 % UI-Skalierung und Schließdialog/Zurück wurden bedient. Offen bleiben Deinstallation ohne Datenverlust, tatsächlicher Clientstart, Client-Settings/Shader-Alias, vollständige Navigation/Scroll/DPI/Fokus/Schließpfade, erfolgreicher Export, Offline-Clientstart, Build-and-play und Wiederanlauf. Beim offenen Exportdialog stoppte der Nutzer die Computersteuerung erneut mit physischem Escape; danach keine weiteren UI-Eingaben. R4 wird ausschließlich nichtinteraktiv nachgeprüft. Linux/macOS werden nicht als unterstützt behauptet.

Ein unsignierter lokaler Installer-Kandidat darf unabhängig geprüft werden. Push und Veröffentlichung warten auf den konkreten sauberen Commitumfang, reale Stabilitätsnachweise, geklärte Signierung und technischen Release-Go. Keine erneute pauschale Nutzererlaubnis für den bereits autorisierten Playtest-Release verlangen.
