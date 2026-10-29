# REL-P01 — Entrypoint und Classpath

28.10.2026, Europe/Berlin. **LOCAL_PACKAGING_VERIFIED** für das lokale Assembly-Paket.
Gate A insgesamt **PARTIAL**: semantische Classpath-Konflikte und vollständige Clientlaufzeit bleiben offen.
Basis `b313c397359e6f4ba65601d87ab797e8049e9871`; getesteter Implementierungscommit `c863c79efe786dff9a7b8404e674932783e188a8`.

## Einzige gewählte Strategie

`target/Neutron-dev-jar-with-dependencies.jar` mit direktem Main
`net.minecraft.client.main.Main`. Genau das vorhandene Assembly-Manifest wurde
korrigiert. Weder Versionen noch Repositories, Compiler, Pluginversionen oder
Shade-Minimierung wurden geändert. Kein Java-Produktionscode wurde verändert.
Dies ist eine lokale Paketstrategie; kein neuer Launcher, Installer oder Loginclient.

| Tatsächliche Ausgabe | Bytes vorher | Bytes nachher | Main-Class nachher |
|---|---:|---:|---|
| `Neutron-dev-jar-with-dependencies.jar` | 123660723 | 123660770 | `net.minecraft.client.main.Main` |
| `Neutron-dev.jar` | 73530021 | 73530072 | fehlt; nicht gewählt |
| `Pulsar b1.0.jar` | 92506488 | 92506539 | fehlt; nicht gewählt |

Das dünne JAR benötigt einen zusätzlich vereinbarten Dependency-Classpath.
Shade besitzt weiterhin kein Main-Class-Attribut und `minimizeJar=true`;
es entfernt unter anderem Pbuffer/CodecWav und zwei Lombok-Processor-Bootstrapklassen.
Assembly erhält diese Klassen. Deshalb bleibt Shade unverändert und wird nicht als
zweite startfähige Ausgabe behauptet. Assembly enthält seine Dependencies bereits;
Vanilla-JAR und Maven-/Vanilla-Librarylisten dürfen nicht zusätzlich angehängt werden.

Der Bytevergleich vor/nach bestätigt identische Eintragsmengen und sämtliche
Klassenbytes. Geändert sind nur das Assembly-Manifest, die eingebettete POM und
der generierte Zeitstempel in pom.properties. Es wurde keine Klasse durch den Fix entfernt.

## Tatsächliches Test-first

Unveränderte Basis: `mvn -o clean package`, 94 frische XMLs, **565/0/0/3**.
Die drei echten Java8-`java -jar`-Proben endeten jeweils mit Exit 1:
Assembly fehlender Launchwrapper; dünn/Shade fehlendes Main-Class-Attribut.
Der erste neue JUnitlauf hatte vier Assertionfehler. Zwei waren Fixturefehler:
`DelayedPacketThread` ist eine Hilfsklasse, kein Module-Untertyp; Maven schließt
zwei `.DS_Store`-Finderdateien regulär aus. Die neue Fixture wurde fachlich korrigiert.
Der unveränderte Paketstand scheiterte danach gezielt an **zwei** Main-Class-Assertions
(POM und tatsächliches Manifest), bei 7 Fällen. Kein Compile-/Syntaxfehler als RED gewertet.
Erst danach erfolgte die POM-Korrektur. Eine weitere Probe mit zusätzlichem JNI-Fall
gegen das aufgehobene Baseline-JAR scheiterte nur noch am alten tatsächlichen Manifest.

Final: `mvn -o clean test` und `mvn -o package`, jeweils **566/0/0/3**, 95 frische XMLs.
Ein neuer Surefire-Fall prüft den POM-Vertrag; die Post-Package-Klassen heißen bewusst
`*Check` und werden separat mit JUnitCore ausgeführt. Der finale Paketlauf besteht
**8/8**: POM-Vertrag, fünf Jar-/Resource-/Discoveryfälle, vorhandener JNA/OSHI-Fall,
ein echter Windows64-Native-Linkfall. Diese Fälle werden nicht zu 566 addiert.
Ein zwischenzeitlicher Helferfehler beim Parameterdefault sowie ein falsch geerbter
PowerShell-Modulpfad im externen Runner wurden separat korrigiert; der finale Vollbuild
begann nach allen POM-/Test-/Helferänderungen. Vorläufige Läufe sind keine finalen Belege.

## JAR-, JNI-, SPI- und Ressourcenbefund

Der gepackte Main besitzt public static void main(String[]) und Major 52.
Alle Minecraft-/OptiFine-Klassen sind höchstens Major 52; höhere Modul-/Multi-Release-
Metadaten wurden getrennt inventarisiert. Keine unzulässige Java9-Basisklasse gefunden.
Alle Quellressourcen außer den beiden Maven-Defaultausschlüssen sind bytegleich erhalten.
Die kritischen Gson/Authlib/JNA/OSHI/Netty/Log4j/LWJGL/JInput/Twitch/Ogg/WAV-
Einstiege sind je einmal verfügbar und ohne Initialisierung ladbar.
Log4j-Pluginindex und sämtliche Serviceproviderdateien sind vorhanden;
114 Lombok-Provider liegen im speziellen `.SCL.lombok`-Format vor; für Assembly
bleibt danach kein unaufgelöster Provider. Sie wurden nicht als
gewöhnliche Spielservices ausgegeben. Reflections findet alle Module-Untertypen
aus den Quelldateien im gewählten JAR, ohne Module zu konstruieren.
Reflections initialisiert dabei tatsächlich den Log4j-Kontext. Das beweist keine
vollständige SPI-Registrierung oder GUI-/Shader-Discovery während einer Spielsitzung.

Die JNA-Probe bestätigt genau eine 5.13.0-Implementierung und erfolgreiche OSHI-
CPU-Discovery, ohne Hardware-/Spieleridentitäten zu protokollieren.
Die separate Java8-Windows64-Probe ruft den LWJGL-Timer per JNI auf und lädt
OpenAL64, JInput DX8/raw64 sowie Twitch samt vier vorab geladenen Abhängigkeiten.
14 Repository-DLLs sind gehasht und PE-architekturgeprüft; 32-bit-Dateien werden
nicht als 64-bit-Testbestand ausgegeben. Kein Display und kein Audiogerät erstellt;
keine Twitch-Sitzung, kein Streaming und kein Spielstart.

## Konflikte bleiben sichtbar

Der korrekt auf Runtime-Scope begrenzte Maven-Classpath enthält **63 Artefakte**.
Eine erste externe Scope-Abfrage enthielt zusätzlich JUnit/Hamcrest; nach Korrektur
des CLI-Parameters `includeScope=runtime` wurde sie ersetzt und nicht als Runtime gezählt.
Es bestehen **621 mehrfach gelieferte Klassennamen**, davon
**199 mit verschiedenen Bytes**: LWJGL 551/152, Vecmath 44/44,
Netty/Patchy 3/3, SoundSystem 23/0 (jeweils überlappend/verschieden).
Die tatsächliche Assembly-Auswahl entspricht bei Konflikten LWJGL 2.9.4-nightly,
Vecmath 1.5.2 und Netty 4.0.25.Final. Das Paket selbst enthält keine doppelten
ZIP-Klasseneinträge. Die effektive Eindeutigkeit beseitigt jedoch nicht den offenen
semantischen Vertrag, insbesondere Patchys alternative Netty-Bootstrapklassen.
R01-07 bleibt deshalb BLOCKED für die vollständige Runtimequalifikation.
Keine neue beobachtete Gameplayursache wurde behauptet oder durch Fremdcodeänderungen repariert.

## Was die neue Startprobe beweist

Assembly erreicht mit Java8 und expliziten Windows-Natives die reale
`joptsimple.MissingRequiredOptionException` für `accessToken, version` (Exit 1).
Es wurden keinerlei Authargumente übergeben. Die anderen beiden JAR-Proben scheitern
weiterhin am fehlenden Main-Class-Attribut. **Ein Manifestfix ist kein Spielstart.**
Die Proben laufen ausschließlich mit externem Prozess-CWD und isoliertem `user.home`.
`Main` hat vor seiner Argumentprüfung `gameDir=.` als Default; der Clientkonstruktor
wird nicht erreicht. GameDir-/Config-/Accountisolation im laufenden Spiel bleibt unbewiesen.
`Start.java` mit seinem festen Testtoken wurde weder ausgeführt noch verändert.

Belege: [01-baseline-summary.json](F:/neutron-rel-p01-evidence/01-baseline-summary.json), [baseline-jar-audit.json](F:/neutron-rel-p01-evidence/baseline-jar-audit.json),
[03-red-contract.log](F:/neutron-rel-p01-evidence/03-red-contract.log), [04-red-corrected.log](F:/neutron-rel-p01-evidence/04-red-corrected.log),
[08-final-clean-test-summary.json](F:/neutron-rel-p01-evidence/08-final-clean-test-summary.json), [09-final-package-summary.json](F:/neutron-rel-p01-evidence/09-final-package-summary.json),
[10-final-packaged.log](F:/neutron-rel-p01-evidence/10-final-packaged.log), [final-jar-audit.json](F:/neutron-rel-p01-evidence/final-jar-audit.json),
[jar-content-delta.json](F:/neutron-rel-p01-evidence/jar-content-delta.json), [runtime-dependency-tree.txt](F:/neutron-rel-p01-evidence/runtime-dependency-tree.txt),
[runtime-classpath-hashes.json](F:/neutron-rel-p01-evidence/runtime-classpath-hashes.json), [dependency-overlap-audit.json](F:/neutron-rel-p01-evidence/dependency-overlap-audit.json),
[installed-runtime-inventory.json](F:/neutron-rel-p01-evidence/installed-runtime-inventory.json), [00-toolchain.log](F:/neutron-rel-p01-evidence/00-toolchain.log).
