# Abschnitt 2 — vorläufige Nachweise

Dies ist eine Teilprüfung nach dem Escape-Abbruch der Computersteuerung durch den Nutzer. Es gibt keinen vollständigen Abschnitts- oder Plattform-Pass. [REVIEW-2.md](../../REVIEW-2.md) erklärt den genauen Stand.

- `verification.json`: tatsächliche Build-/JUnit-/Werkzeugtest-Ergebnisse, Dateihashes, Quellvergleich, Screenshot-Zuordnung, rein lesender Prozessstand und nicht ausgeführte Prüfungen.
- `warm-maven-output.txt`, `cold-maven-output.txt`: ungekürzte tatsächliche Maven-Ausgaben. Beide je 400 Tests, 0 Failures/Errors, 3 Skipped. Die Cold-Ausgabe enthält fehlende Repository-Prüfsummen als Warnungen.
- `*-build-command.json`, `*-bootstrap-tests.txt`, `python-unit-tests.txt`: tatsächliche Kommandos und Ergebnisse. Python-Tests starten keinen Client und verwenden nur F:-Temp.
- `final-launch-manifest.json`: Kopie des Manifests aus `F:\Neutron Launcher Section2 20261028\Final Distribution`, dessen Hashes gegen die echten Artefakte geprüft wurden.
- `*-runtime-manifest.json`, `native-architecture-inspection.json`: gepaarte offizielle LWJGL-Java-/Native-Lieferdateien; Header-/Hashprüfung ersetzt keinen Linux-/macOS-Start.
- `final-client-menu.png/.json`: tatsächliches finales Clientmenü; Bild-Hash verifiziert. Das Zeitfeld im ursprünglichen JSON ist die spätere Persistierungszeit des Bildpuffers, nicht der exakte Aufnahmezeitpunkt.
- `final-client-run.json`, `final-client-output.txt`: tatsächliche Startparameter und Clientausgabe; später gespeicherter Exitcode 0. Der reguläre Schließablauf wurde wegen Nutzerabbruch nicht getestet.
- `windows-client-menu.png`, `windows-window-capture.json`, `windows-client-run.json`, `windows-client-output.txt`, `before-alias-settings-crash.txt`: früheres Warm-Paket und protokollierter Settings-Crash vor dem Alias; keine finale UI-Abnahme.
- `disk-space-initial.json`, `disk-space-inspection.json`: lesende Kapazitäts- und Größenaufnahmen; alte und neue Aufnahme bleiben unterscheidbar.
- `resource-failures.json`: als solche gekennzeichnete Auszüge der tatsächlich zurückgegebenen Tool-Fehler und der abgelehnten Löschaktion, keine nachträglich ausgeführten Versuche.
- `changed-files.json`: absolute Pfade und SHA-256 sämtlicher Dateien dieses Abschnitts. Die Inventardatei enthält bewusst keinen Selbst-Hash.
- `repository-probes.json`: bereits vor dem UI-Abbruch ausgeführte read-only Repository-Probes; keine zusätzliche Netzwerkprüfung in der Dokumentationsfortsetzung.

Keine weitere Computersteuerung oder Umgehung des Nutzerabbruchs. Keine weiteren Clientstarts oder Schließ-/Killversuche. Abschnitt 3 bleibt gesperrt.
