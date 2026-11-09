# Nachweise für Abschnitt 1

Alle Dateien hier dokumentieren ausschließlich Installation, Baseline und lokale Skill-Funktionsprüfungen. Sie sind keine Launcher-Implementierung und kein Client-Laufnachweis.

- `installed-skills.json`: gepinnte Repository-Commits, SHA-256 der heruntergeladenen Archive, absolute Zielpfade und Hash jeder installierten Datei. Installierte Dateien wurden gegen unabhängig zuerst geladene Quellbäume verglichen.
- `*-install.txt`: tatsächliche Standardausgaben der drei Installer-Aufrufe; alle mit Exit 0. Aufrufmuster: `python <skill-installer>/scripts/install-skill-from-github.py --repo <repo> --ref <commit> --path <skill-paths> --dest C:\Users\Administrator\.codex\skills --method download`.
- `verification.json`: tatsächliche Integritäts-, Syntax-, CSV- und Funktionsprüfungen, genaue Suchkommandos und Baseline-Zusammenfassung. Zeitangaben sind ausdrücklich UTC.
- `ux-search.json`, `react-search.json`: tatsächlich zurückgegebene lokale Suchergebnisse.
- `design-system*.json`: tatsächliche Generatorausgaben für den Funktionstest, nach Relevanzprüfung verworfen; keine übernommenen Produktvorgaben.
- `client-source.json`: read-only GitHub-API-Nachweis, dass die vereinbarte Neutron-Quelle öffentlich ist und `main` existiert. Kein Build dieses Commits ausgeführt.

Audit-Skripte und vollständige persönliche Status-/Hashdaten bleiben lokal unter `%TEMP%\neutron-launcher-section1-20261028\`. Dort liegen `inspect_sources.py`, `install_reviewed_skills.py`, `verify_section1.py`, `check_search_relevance.py`, `baseline-private.json`, `existing-skills-hashes.json` und die entpackten Quellbäume. Der Reviewer kann diese Dateien lokal lesen. Das temporäre Verzeichnis ist kein dauerhafter Projektbestandteil; wesentliche nichtpersönliche Ergebnisse wurden hier gesichert.

Keine Java-/Maven-/Client-/UI-/Installer-Tests in Abschnitt 1. Impeccables Engine wurde nicht ausgeführt. Linux und macOS wurden nicht ausgeführt. Diese Grenzen stehen auch in der Gate-Matrix.
