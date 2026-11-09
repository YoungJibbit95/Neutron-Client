# Neutron Launcher

<!-- impeccable:product-schema 1 -->

Stand: 28. Oktober 2026. Grundlage ist der bereits mit dem Nutzer abgestimmte und an diesen Implementierungs-Chat übergebene Plan. Dieses Dokument beschreibt das Zielprodukt; es behauptet keine bereits implementierten Funktionen.

## Platform

web

Die Oberfläche verwendet Webtechnik in einer Electron-Desktop-App. Zielsysteme: Windows x64, Linux x64 sowie macOS Intel und Apple Silicon über einen zunächst zu belegenden x64-Kompatibilitätspfad. Native ARM-Unterstützung des alten Clients gehört nicht zu V1. macOS darf erst nach einem tatsächlichen Client-Start als unterstützt gelten.

## Stack

Vom Nutzer festgelegt: Electron, React, TypeScript und Vite, isoliert in `launcher/`. Minecraft 1.8.9 bleibt ein Java-8/Maven-Projekt. Java und Maven laufen als getrennte Prozesse im Hintergrund.

## Users

Spieler möchten Neutron auf einem frischen Rechner installieren, aktualisieren und starten. Java, Maven und Git dürfen dafür nicht global installiert sein müssen. Die Oberfläche ist deutsch und englisch verfügbar. Die Minecraft-Anmeldung bleibt im Client.

## Product Purpose

Ein Desktop-Launcher richtet die lokale Laufzeit ein, baut den Client aus einem bestimmten GitHub-Commit und startet eine geprüfte Version direkt. Ein fehlgeschlagenes Update erhält die letzte spielbare Version. Vorhandene Builds lassen sich offline starten.

## Operating Context

- Produktquelle: <https://github.com/YoungJibbit95/Neutron-Client>, Branch `main`. Vor jedem Build wird der Branch auf einen konkreten Commit aufgelöst. Der lokale Git-Ursprung kann noch Pulsar-Client heißen.
- Der Spieler erhält ein Quellarchiv; Git ist keine Laufzeitvoraussetzung.
- Ersteinrichtung mit kurzem Assistenten für Speicherort und benötigte Downloads. Java-8-JDK, Maven, Assets und passende Natives werden lokal verwaltet.
- Getrennte Verzeichnisse für Einstellungen, Laufzeiten, Download-/Dependency-Cache, Build-Workspace, unveränderliche Builds, temporäre Daten, Spielverzeichnis und Logs. Das bestehende `test_run/` ist keine Datenquelle für die Auslieferung.
- Entwickler arbeiten parallel am Client. Bestehende Änderungen und persönliche Daten werden nicht zurückgesetzt, überschrieben, gestagt oder committed. Combat-/Scaffold-Module bleiben außerhalb dieses Auftrags.

## Capabilities and Constraints

Vier Ansichten: Launch Client, Launch Options, Launcher Settings und Update. Die genaue visuelle Vorgabe steht in [DESIGN.md](DESIGN.md).

Die typisierte Desktop-Schnittstelle umfasst Einstellungen, Versionen, Einrichtung, Build, Start/Stopp und Logexport. Fortschritt, Logs und Prozessstatus kommen als Events. Der Renderer erhält keine direkte Shell-Ausführung. Prozessargumente werden strukturiert übergeben.

Der Build meldet Vorbereiten → Herunterladen → Bauen → Prüfen → Bereit. Prozentwerte erscheinen nur für messbaren Fortschritt. Jeder Commit erhält einen eigenen Build mit Manifest für Version und Startparameter; Aktivierung erfolgt erst nach erfolgreicher Prüfung. Ein laufender Build des Spiels wird weder ersetzt noch gelöscht. Abbruch beendet den Prozessbaum und verwirft ausschließlich unvollständige temporäre Daten. „Bauen und spielen“ startet nach Erfolg automatisch.

Der produktive Einstieg benötigt explizite Assets, Natives und Arbeitsverzeichnis. Der bisherige Testquellen-Start erfüllt dies nicht. RAM-Vorgabe: min(4 GiB, ungefähr ein Viertel des physischen RAM), mindestens 1 GiB auf unterstützten Geräten; Grenzen und Systemreserve müssen validiert werden. Zusätzliche JVM-Argumente dürfen keine widersprüchlichen `-Xmx`-/`-Xms`-Werte einführen.

Build- und Client-Ausgabe bleiben getrennt. Logs haben Zeitstempel, Suche/Filter, Auto-Scroll, Kopieren und Export. Die UI virtualisiert einen begrenzten Puffer; rotierende Dateien behalten die vollständige Ausgabe innerhalb der definierten Aufbewahrung. Hochscrollen pausiert Auto-Scroll und zählt neue Zeilen. Ein Crash zeigt Exitcode, Report und Neustart/Melden. Ein Problembericht zeigt eine Vorschau und öffnet die GitHub-Issue-Seite; er veröffentlicht nichts automatisch. Diagnoseexport entfernt erkennbare Tokens und persönliche Pfadanteile.

Beim Fensterschließen mit laufendem Spiel stehen Minimieren, Client und Launcher beenden sowie Abbrechen zur Wahl. Normale native Fensterfunktionen bleiben erhalten.

## Brand Commitments

Name: Neutron. Festgelegte Designrichtung: Sunset Graphite. Keine erfundene Versionsnummer; beispielsweise „Development · a1b2c3d“ mit echtem Commit. Keine erfundenen Erfolgs-, Build- oder Plattformnachweise.

## Evidence on Hand

Lokales Maven-Projekt mit README, POM, Test-Startklasse und Windows-/Linux-Testnatives. Die genaue Ausgangslage und lokale Werkzeugprüfung stehen in [docs/BASELINE.md](docs/BASELINE.md). Abschnitt 1 führt keinen Client-Build oder Client-Start aus. Fehlende Laufnachweise, insbesondere macOS, bleiben offen.

## Product Principles

- Ein spielbarer installierter Build bleibt bei fehlgeschlagenen Updates verfügbar.
- Einrichtung und Fehlerbehebung sind für Spieler ohne Entwicklungswerkzeuge verständlich.
- Status und Fortschritt zeigen nachgewiesene Tatsachen.
- Die Konsole bleibt auch unter Last lesbar und bedienbar.
- Plattformunterstützung folgt tatsächlichen Laufnachweisen.

## Accessibility & Inclusion

Tastaturbedienung ohne künstliche Verzögerung, sichtbarer Fokus, lesbare Kontraste, skalierbare Oberfläche und hohe DPI. `prefers-reduced-motion` und ein eigener Schalter reduzieren Bewegung. Alle Themes und beide Sprachen müssen visuell und interaktiv geprüft werden.

## Delivery and Open Decisions

Aktualisierte Nutzerpriorität vom 28. Oktober 2026: Das nächste Lieferziel nach dem laufenden Abschnitt 3 ist ein stabiler **Windows-x64-Playtest-Installer mit echter Hintergrundintegration und tatsächlichem Client-Start**, anschließend ein GitHub-Pre-Release in `YoungJibbit95/Neutron-Client`, damit Nutzer und Kollege spielen können. Ein Mock-Prototyp darf nicht veröffentlicht werden. Linux-AppImage und macOS-DMG bleiben langfristiger Umfang und dürfen bis zu echten Nachweisen nicht als unterstützt beworben werden.

Der Nutzer autorisiert für diese geprüfte Playtest-Auslieferung notwendige projektbezogene Commits/Pushes, Release-Tag und GitHub-Pre-Release samt Installer. Diese eng begrenzte Autorisierung ersetzt das bisherige Veröffentlichungsverbot. Sie erlaubt weder einen ungeprüften Workspace-Push noch die Veröffentlichung fremder Änderungen oder privater Daten; kein Force-Push. Der Review-Chat muss vor Veröffentlichung nach unabhängiger Prüfung technischen Release-Go erteilen. Ein erneutes pauschales Nutzer-Okay zum bereits autorisierten Release ist nicht nötig.

Stabilitäts-Gates: echter Installer mit Installation/Start/Deinstallation ohne Datenverlust; lokal verwaltetes Java/Maven ohne globale Werkzeuge; GitHub-Build und echter Clientstart; Updatefehler, Abbruch, Rollback und Offline-Start; relevante UI-/Settings-/Crash-/Logexport-Pfade; keine bekannten Blocker; keine privaten Daten. Installer-SHA256, genaue Version/Commit, Release Notes und bekannte Einschränkungen gehören zur Auslieferung. Ungeklärte Signierung vor öffentlicher Auslieferung bleibt ein Gate; Signierung und SmartScreen-Status werden ehrlich ausgewiesen. Einzelheiten: [Windows-Playtest](docs/PLAYTEST.md).

Die Escape-Sperre für Computersteuerung bleibt wirksam. Fehlende menschliche Freigabe für notwendige reale UI-/Installationstests blockiert den Release auch bei grünen Unit-Tests. Abschnitt 3 ist technisch akzeptiert. Der danach freigegebene Windows-Meilenstein implementiert echte Hintergrundintegration und einen unsignierten Installer-Kandidaten. Kein reales Launcher-/Clientfenster und keine Installation wurden seit dem Escape-Abbruch ausgeführt.

Im Integrationskandidaten festgelegt und geprüft: Temurin 8u504-b01/Maven 3.9.16 mit Prüfsummen, offizielle 1.8.9-Assets/Windows-Natives, gerätebezogene RAM-Grenze und Logrotation. Liefervertrag und Testgrenzen: [INTEGRATION.md](docs/INTEGRATION.md). Tatsächliche Fenster-/Frischsystem-/Wiederanlaufnachweise bleiben offen; daraus folgt noch keine Release-Reife.

Außerhalb V1: Launcher-Selbstupdates, Anmeldung im Launcher und frei wählbare Fremd-Repositories.
