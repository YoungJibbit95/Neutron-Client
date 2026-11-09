# Windows-Playtest — technische Kandidatenbelege

`candidate.json` ist der Einstieg mit aktuellem Installerpfad/SHA256, Version, Tests, Quellbaum und ausdrücklich offenen Gates. Der Installer ist unsigniert; keine Installation, Deinstallation oder App-/Client-UI-Prüfung ist enthalten.

- `commands.json`, `build.txt`, `tests.txt`, `tests.json`: ausgeführte Produktionschecks, 109 Tests und exakte Umgebungsziele auf F:.
- `installer.txt`, `installer-initial-failed.txt`: erfolgreicher aktueller Paketbau und getrennt festgehaltener fehlgeschlagener Wiederaufbau im älteren Ausgabeordner. Nur der neue Ordner „Installer Candidate Review“ ist der Prüfkandidat.
- `integration.json`, `maven-tests.json`, `maven-build-and-cancel.log`: echte verwaltete Einrichtung, zwei GitHub-Maven-Builds, Bootstrap-Validierung ohne Spiel, Versionsrückauswahl und Wiederherstellung ohne Netzwerk.
- `cancellation.json`: tatsächlicher Abbruch eines eigenen Maven-Prozessbaums mit unveränderter ausgewählter Version und Registry.
- `package-inspection.json`, `signature-version.json`: bytegleiche eigene ASAR-/Adapterdateien, lokale Fonts/Lizenzen, PE-x64, Metadaten und `NotSigned`.
- `source-inventory.json`, `publication-scope.json`, `publication-diff-stat.txt`: genaue eigene Dateien/Hashes, isolierter Index-Baum und explizite Ausschlüsse. Keine tatsächlichen Release-Commits/Pushes.
- `*.cjs.txt`, `*.py.txt`: lesbare Entwicklerhüllen der tatsächlich verwendeten Headless-Prüfungen. Die F:-Pfade sind interne Evidenz, keine Spieler-Voraussetzung und kein Release-Asset.

Der stille NSIS-WriteUninstaller-Helfer gehört zum Paketbau und ist kein Installationslauf. Die tatsächlichen UI-/Installations-/Clientstart-/Wiederanlaufprüfungen sowie Signierung/SmartScreen bleiben offene Release-Gates. Der gemeinsame Root-POM wurde parallel extern geändert und ist deshalb ausdrücklich kein bestandener Baseline-Vergleich; diese Änderung bleibt außerhalb des Kandidaten.
