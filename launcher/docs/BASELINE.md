# Ausgangslage — Abschnitt 1

Erfasst am 28. Oktober 2026 im Workspace `F:\Puslar Backup - Copy`. Die maschinenlesbare Zusammenfassung und Prüfresultate liegen unter [evidence/section-1](evidence/section-1/).

## Repository

- HEAD bei Erfassung: `eeddb7da58978f04fad95ad8042175b7ad848d6a`.
- Lokaler Ursprung: `https://github.com/YoungJibbit95/Pulsar-Client.git`; keine Änderung des Remotes.
- Die vereinbarte Quelle `YoungJibbit95/Neutron-Client` wurde zusätzlich read-only über die GitHub-API geprüft: öffentlich, Standardbranch `main`, dort bei Prüfung Commit `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`. Siehe [client-source.json](evidence/section-1/client-source.json). Dieser Remote-Commit ist nicht der lokale HEAD; keiner von beiden wurde in Abschnitt 1 gebaut.
- 164 Status-Einträge bei der Baseline: 147 geänderte versionierte Dateien und 17 unversionierte Dateien. Die Zahlen sind eine Momentaufnahme bei paralleler Arbeit.
- Bestehende Änderungen betreffen insbesondere `.gitignore`, `.vscode/settings.json` und persönliche `test_run/`-Daten. Fremde unversionierte Dokumente liegen unter `design/`.
- Keine `AGENTS.md` in der Workspace-Dateisuche oder in `F:\AGENTS.md` bzw. im Workspace-Root gefunden. Keine bestehende `launcher/`-Anwendung gefunden.
- README, POM und `src/test/java/Start.java` wurden gelesen. Es wurden keine bestehenden Projektdateien bearbeitet, zurückgesetzt, gestagt oder committed.

Die vollständige lokale Statusliste und SHA-256-Fingerprints bestehender Änderungen liegen bewusst außerhalb der Projektdateien unter `%TEMP%\neutron-launcher-section1-20261028\baseline-private.json`. Sie enthalten persönliche Dateinamen und sind kein Auslieferungsbestandteil. Die Zusammenfassung im Projekt enthält keine Spielerdaten oder Token. Ein Hashvergleich am Abschnittsende dokumentiert mögliche zwischenzeitliche Änderungen, ohne sie zurückzusetzen.

## Build und Start

POM: `me.pulsar:Neutron:dev`, Java-Quell-/Zielstand 1.8, Maven; Assembly und Shade sind konfiguriert. Assembly nennt `net.minecraft.launchwrapper.Launch` als Einstieg; Shade minimiert Abhängigkeiten und verwendet noch den Dateinamen `Pulsar b1.0`. Dies ist kein nachgewiesener produktiver Distributionsstart.

README beschreibt Java 8 (dort Temurin 8u482) und Maven 3.9.15, Testquellen auf dem Entwicklungs-Classpath und einen manuellen Asset-Aufbau. Sie bezeichnet Bootstrap auf frischen Rechnern und eigenständige Asset-/Native-Auslieferung ausdrücklich als offene Aufgaben. Diese README-Aussagen sind Bestandsinformationen und wurden in Abschnitt 1 nicht durch einen Build nachgeprüft.

`Start` liegt ausschließlich unter Testquellen. Es setzt `org.lwjgl.librarypath` relativ auf `../test_natives/windows` oder für alle anderen Betriebssysteme auf `../test_natives/linux`. Damit fehlt ein macOS-Zweig. Assets sind relativ `assets`, Index `1.8`, Version `MavenMCP`. `net.minecraft.client.main.Main` bietet bereits Optionen für `gameDir`, `assetsDir`, `assetIndex` und `version`; eine spätere produktive Integration muss aktuellen Code erneut lesen.

Lokal gibt es Windows- und Linux-Testnatives (LWJGL/OpenAL/JInput), keine macOS-Natives. Bloß vorhandene Binärdateien beweisen keine Plattformkompatibilität. POM enthält LWJGL 2.9.4-nightly; Kompatibilität und Lieferquelle gehören zum frühen Gate in Abschnitt 2.

## Tatsächlich ausgeführte Werkzeugprüfung

| Prüfung | Ergebnis |
| --- | --- |
| Betriebssystem per CIM | Windows 11 Pro, 64-Bit. |
| `java -version` | Temurin OpenJDK 21.0.11, 64-Bit; aktuelle Shell nutzt Java 21. |
| `mvn -version` | Fehlgeschlagen: `JAVA_HOME` ist nicht korrekt definiert. Kein Maven-Build ausgeführt. |
| Lokale JDK-Verzeichnisse | `temurin8-jdk` und `temurin21-jdk` unter Scoop vorhanden; JDK-8-Lauffähigkeit hier noch nicht geprüft. |
| `node --version` | v26.3.1. |
| `python --version` | Python 3.13.16. |
| Git | Verfügbar; nur Status, HEAD, Branch und Remote gelesen. |

Die existierende Entwicklungsinstallation ist kein frisches System ohne Java/Maven/Git. Linux-, macOS-Intel- oder Apple-Silicon-Starts wurden nicht durchgeführt. Weder Client-Build/-Start noch UI, Download-Setup oder Installer wurden in Abschnitt 1 implementiert oder geprüft.

## Früh zu lösende Risiken

Java-8-/Maven-Aufruf muss unabhängig von fehlerhaftem globalem `JAVA_HOME` reproduzierbar sein. Testquellen-Einstieg, Packaging-Manifest und explizite Laufzeitpfade müssen zusammenpassen. Legacy-Repositories und unverifizierte Asset-/Native-Lieferwege können einen frischen Build blockieren. macOS bleibt bis zum tatsächlichen Start ein offenes Release-Gate. Parallele Client-Änderungen erfordern erneute Lektüre vor Abschnitt 2.
