# Windows-Kandidat R3 — technische Nachweise

`candidate.json` und `../../REVIEW-WINDOWS-R3.md` sind maßgeblich. R3 trennt Terminierungsversuch und tatsächliches Prozessende; 119 Tests bestehen.

- `tests.json`, `commands.json`, `build.txt`, `installer.txt`: aktueller Build, Tests und frischer unsignierter Installer.
- `maven-stop-failure-r3.json`: echter gestarteter Mavenprozess, verweigerter Abbruch, noch lebender PID, blockierter neuer Start, zurückgewiesener Shutdown und erfolgreicher Retry ohne Änderung der Registry.
- `cancellation.json`: normaler aktueller Maven-Abbruch; `integration.json`: aktuelle Prüfung der vorhandenen Distribution, Bootstrap ohne Spielfenster und Wiederherstellung ohne GitHub-Abfrage.
- `integration-baseline-r1.json`, `maven-tests.json`: zwei vollständige Builds aus R1 für unveränderten Client/Adapter. In R3 kein neuer vollständiger Cold-Build; Provenienz nicht mit erneutem Build verwechseln.
- `package-inspection-r3.json`, `signature-version-r3.json`: bytegleiche eigene Paketdateien, PE AMD64, Version und fehlende Signierung.
- `source-inventory.json`, `publication-scope.json`, `publication-diff-stat.txt`: eigene aktuelle Dateien/Hashes und isolierter Index, keine tatsächlichen Commits/Pushes.

Nur der stille NSIS-Buildhelfer wurde für die Paketerzeugung ausgeführt. Keine Installation/Deinstallation oder App-/Client-/Installeroberfläche. Reale Stabilitäts- und Signierungsgates bleiben offen. Interne F:-Pfade und Entwicklertesthüllen sind keine Release-Assets oder Spieler-Voraussetzungen.
