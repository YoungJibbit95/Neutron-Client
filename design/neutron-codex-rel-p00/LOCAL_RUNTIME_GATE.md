# REL-P00 — Build- und Laufzeitmatrix

Stand: 28.10.2026, Europe/Berlin. Ergebnis: **SOURCE_INTEGRATED; LOCAL_ONLY / DISTRIBUTION_BLOCKED**.
Lokale Buildausgaben sind keine spielbare Alpha und kein Testerpaket.

| Prüfpunkt | Tatsächlicher Befund | Gate |
|---|---|---|
| Integration | Exakte CMB-1a-Basis plus ausschließlich zwei bestätigte VIS-01-Cherry-Picks; elf VIS-Dateien bytegleich mit VIS-01 | PASS |
| Java/Maven | Oracle JDK 1.8.0_391, Maven 3.8.5, alle Maven-Aufrufe offline | PASS |
| Gemeinsame Regression | 94 frische XMLs: 565 Tests, 0 Failures, 0 Errors, 3 Skips | PASS |
| Packaging-Ausführung | `mvn -o package`, nochmals 565/0/0/3, Assembly und Shade erzeugt | PASS |
| Assembly-Entrypoint | `Main-Class: net.minecraft.launchwrapper.Launch`; Klasse fehlt; echte `java -jar`-Probe endet mit Exit 1 | FAIL |
| Shade-Entrypoint | Kein Main-Class-Manifestattribut; echte `java -jar`-Probe endet mit Exit 1 | FAIL |
| Vorhandener Main | `net.minecraft.client.main.Main` in allen drei JARs; `javap` bestätigt public static main und Major 52 | PASS |
| Entwicklungsstarter | `Start` nur im Testclasspath; festes `--accessToken 0`; nicht als authentifizierter Spielstart ausgeführt | BLOCKED |
| Installierte Vanilla-Dateien | Client und Index SHA1 korrekt; 734/734 Assets sowie 35/35 unter Windows ausgewählte Library-/Native-Dateien korrekt | PASS |
| Maven-Laufzeitabhängigkeiten | 63 aufgelöste Artefakte lokal vorhanden und SHA256-erfasst; Versionskonflikte gegenüber Vanilla beachten | PASS für Bestand, Laufzeit nicht freigegeben |
| Natives | Repository enthält LWJGL2-/OpenAL-/JInput-/Twitch-DLLs; kein Display-/Clientstart durchgeführt | NOT_RUN für End-to-End |
| OptiFine | Quellenkennung `OptiFine_1.8.9_HD_U_L5`; je JAR 402 `net/optifine`-Klassen | PASS für Bestandsnachweis, keine Runtime-Abnahme |
| Offizieller Launcher/Auth | Nutzer meldet ausdrücklich: Minecraft Launcher nicht installiert. Keine Accountdateien gelesen, keine Tokens beschafft oder erzeugt | BLOCKED |
| Separates GameDir | Vorgesehen `F:\neutron-private-playtest`; wegen Startblockade nicht angelegt | NOT_RUN |
| Launcher-JSON | Offizielle vorhandene 1.8.9-Metadaten gelesen, keine eigene Version erzeugt/installiert | NOT_RUN |
| Rechte/Installer | Alle Ausgaben enthalten vollständige Spielbestandteile; kein lizenzgeprüfter eigenständiger Loader/Patcher vorhanden | BLOCKED |

## Tatsächliche JAR-Ausgaben

| Datei unter dem eigenen `target` | Bytes | Main-Class | Minecraft-/OptiFine-Klassen | Asseteinträge unter assets/minecraft |
|---|---:|---|---:|---:|
| `Neutron-dev.jar` (zusätzliche dünne Basis-JAR) | 73530021 | fehlt | 2510 / 402 | 4240 |
| `Neutron-dev-jar-with-dependencies.jar` | 123660723 | Launch, fehlt im JAR | 2510 / 402 | 4240 |
| `Pulsar b1.0.jar` | 92506488 | fehlt | 2510 / 402 | 4240 |

SHA256, vollständige Manifeste, Klassenlisten, Services und Bytecodehistogramme:
[jar-audit.json](F:/neutron-rel-p00-evidence-20261028/jar-audit.json).
Die Hashes identifizieren genau diesen Lauf; byteidentische Wiederholungsbuilds wurden nicht nachgewiesen.
Nach Abschluss der Proben wurden ausschließlich diese drei erzeugten JAR-Dateien
aus dem eigenen target nach `F:\neutron-rel-p00-evidence-20261028\local-only-build`
verschoben. Dieser lokale Verwahrungsordner ist kein Distributionspaket und
darf nicht an Tester weitergegeben werden. Die Tabellenpfade nennen den ursprünglichen Buildoutput.

## Abhängigkeiten und Isolation

Die vorhandene offizielle JSON nennt `mainClass=net.minecraft.client.main.Main`,
Assetindex `1.8` und `jre-legacy`/Java 8. Drei nur für macOS freigegebene
LWJGL-Artefakte wurden beim Windows-Bestand korrekt ausgeschlossen;
Windows-64-Twitch-Classifiers wurden berücksichtigt. Ein Dateihash beweist
weder Kontobesitz noch eine aktive gültige Anmeldung.

Das Maven-POM verwendet unter anderem LWJGL 2.9.4-nightly sowie transitiv 2.8.3,
OSHI 6.4.5/JNA 5.13.0, Log4j 2.17.1 und Netty 4.0.25.Final.
Vanilla 1.8.9 nennt teilweise andere Versionen. Die Vanilla-Libraryliste darf
deshalb nicht unverändert als Neutron-Classpath ausgegeben werden.
Shade meldet überlappende Klassen; diese wurden nicht durch Dependency-Updates bereinigt.

Die beiden großen JARs enthalten die geprüften JNA-/OSHI-/Log4j-/Reflections-
und Ogg/OpenAL-Einstiege sowie alle 47 oberen Implementierungsklassen im Modulpfad.
Das ist kein Nachweis ihrer erfolgreichen Discovery/Registrierung im Spiel.
Je ein separat ausgeführter `NativeDependencyTest` besteht direkt gegen Assembly
und Shade ohne Maven-Libraryclasspath: JNA-Eindeutigkeit, Version und CPU-Erkennung.
Shade entfernt `Pbuffer` und `CodecWav`; das aktuelle SoundManager-Setup
registriert nur Ogg. Daraus folgt kein nachgewiesener WAV-Spielabsturz.
Shade behält Lombok-Prozessorservices ohne deren beiden Bootstrapklassen;
andere Lombok-Provider liegen im speziellen `.SCL.lombok`-Format vor.
Diese Buildtool-Reste sind kein beobachteter Clientfehler. Log4js Pluginindex
liegt korrekt unter `META-INF/org/apache/logging/log4j/core/config/plugins/`.
Major-53-Einträge betreffen Modul-/Java-9-Metadaten und Multi-Release-Inhalte;
sie werden nicht pauschal als Java-8-Clientfehler gewertet.

`--gameDir` allein isoliert den Bestand nicht vollständig: Pulsar nutzt
zusätzlich das Arbeitsverzeichnis für `Neutron/alts` und `user.home` für
`MinecraftAccounts`; ConfigManager nutzt `<gameDir>/pulsar/configs`.
Ein späterer regulärer Launcher-Test muss alle drei Pfade isolieren, ohne alte
Konten/Configs zu kopieren. In REL-P00 wurde kein solcher Client gestartet.

Belege: [installierte Dateien](F:/neutron-rel-p00-evidence-20261028/installed-runtime-inventory.json),
[Maven-Dependencybaum](F:/neutron-rel-p00-evidence-20261028/runtime-dependency-tree.txt),
[63 Artefakthashes](F:/neutron-rel-p00-evidence-20261028/runtime-classpath-hashes.json),
[Paketabhängigkeitstests](F:/neutron-rel-p00-evidence-20261028/packaged-dependency-probes.json).
