# Windows-Kandidat R2 — technische Nachweise

Einstieg: `candidate.json`, genauer Korrekturauftrag und Testgrenzen: `../../REVIEW-WINDOWS-R2.md`.

115 Tests und aktueller Produktions-/NSIS-Build bestehen. `tests/lifecycle.test.ts` prüft den echten Start-/Stop-/Shutdown-Kern mit eigenen Node-Hilfsprozessen und kontrollierten IO-Barrieren. Native Client-/Installer-/Launcherinteraktionen bleiben ausgesetzt.

`integration.json` ist die R2-Prüfung einer **vorhandenen** GitHub-Distribution samt Assets/Tools, Bootstrap-`--validateOnly` und Wiederherstellung ohne GitHub-Abfrage. Das Feld „github-commit-archive-maven-build“ in der Entwicklerhülle beschreibt deren Herkunft; es ist kein in R2 erneut ausgeführter vollständiger Build. Die zwei vollständigen Builds und Rückauswahl stammen aus der unveränderten Client-/Adapter-Baseline R1. `integration-baseline-r1.json` und `maven-tests.json` trennen diese Herkunft von der R2-Prüfung.

`cancellation.json` dokumentiert den erneut ausgeführten echten Maven-Abbruch mit dem neuen Prozessrunner. `package-inspection-r2.json` vergleicht die aktuellen eigenen Paketdateien byteweise; `signature-version-r2.json` meldet fehlende Signierung. `publication-scope.json` enthält 53 eigene Dateien und den isolierten Index-Baum, keine tatsächlichen Commits/Pushes.

Der stille NSIS-WriteUninstaller-Helfer ist Bestandteil der Paketerzeugung, kein Installationslauf. Kein UI-/Release-Pass. Interaktive Stabilitätsnachweise und geklärte Signierung bleiben erforderlich.
