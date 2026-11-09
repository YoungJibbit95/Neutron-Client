# Abschnitt 3 — automatische Nachweise

Nur Quellcode, Build/Typechecks und nichtinteraktive Tests. **Keine Launcher-Screenshots, keine Browser-/Desktop-Interaktion, kein Electron-/Clientstart.** Die reale visuelle und interaktive Abnahme ist ausstehend.

- `verification.json`: genaue ausgeführte Kommandos/Exitcodes, 94 Tests, unveränderte Client-/POM-Fingerprints, alle erzeugten Artefaktpfade/Hashes und auf F: konfigurierte Caches/Temp.
- `build.txt`: tatsächlicher npm-Build mit Typechecks, geprüftem eigenem `dist/`-Ziel und Vite-Ausgabe. „Rendering chunks“ bezeichnet Bundling, keinen gerenderten Bildschirm.
- `tests.json`, `tests.txt`: tatsächliche Vitest-Ergebnisse. React-Serverausgabe ist HTML im Speicher, kein Browser und keine interaktive Prüfung.
- `impeccable-engine.json`, `impeccable-context.txt`: SHA-256-geprüfte Engine 0.1.18 und einmaliger Kontextlauf mit tatsächlichem Exit 0, ohne UI.
- `dependency-metadata.json`: vor Installation gelesene Registry-Versionen, Integritätswerte, Engines und Skript-Metadaten. Installation mit deaktivierten Lifecycle-Skripten; `package-lock.json` hält die tatsächliche Auflösung fest.
- `inventory.json`: absolute Pfade/Hashes der Abschnitt-3-Quellen und Dokumente; keine `node_modules`, Caches oder fremden Clientdateien als Release-Asset.
- `resources.json`: lesende Speicher-/Größenaufnahme eigener F:-Artefakte und Abgleich der drei Client-/POM-Baseline-Dateien.

Die neue Nutzerpriorität für einen stabilen Windows-Playtest ist in PLAN/PRODUCT/GATES und [PLAYTEST.md](../../PLAYTEST.md) festgehalten. Sie ist keine Freigabe, diesen Mock-Prototyp zu veröffentlichen. Abschnitt 4 und Release-Aktionen wurden nicht begonnen.
