# Entwicklung und Tests im Hauptordner

Alle Arbeiten und IntelliJ-Tests finden in `F:\Puslar Backup - Copy` statt. Der dort
zum Abschluss ausgecheckte Branch ist `main`. Es gibt keinen zusätzlichen dauerhaften
Testcheckout und keine Kopie des Clients pro Aufgabe.

## Client starten

Das bereits geöffnete IntelliJ-Projekt bleibt im Hauptordner. Projekt-SDK und
Maven-Runner verwenden Temurin 8u482 (`temurin-1.8 (2)`), installiert unter
`C:\Users\Administrator\.jdks\temurin-1.8.0_482`.

Die Run-Konfiguration **Neutron Client (Java 8)** startet die vorhandene Klasse `Start`
mit Java 8, lokalen Windows-Natives und Arbeitsverzeichnis `$PROJECT_DIR$/test_run`.
Die bisherige Konfiguration **Launch Pulsar** kann ebenfalls verwendet werden.
Vor dem Start wird gebaut. Assets, Einstellungen und Welten bleiben am vorhandenen Ort;
es wird kein vollständiger Spielordner kopiert. Der Startname ist `NeutronTest` und die
Entwicklersession ist zunächst offline; regulärer Account-Login bleibt ein eigener Vorgang.

Maven 3.9.15 liegt lokal unter
`C:\Users\Administrator\scoop\apps\maven\3.9.15\bin\mvn.cmd`.
`JAVA_HOME` für Build-Befehle nur im jeweiligen Prozess auf das Java-8-JDK setzen.

## Aufgabe integrieren und veröffentlichen

1. Im Hauptordner Git-Status und laufende fremde Arbeit prüfen. Aufgaben seriell ausführen.
2. Fertige Änderungen oder Aufgabenbranches vollständig prüfen und gezielt in das lokale
   `main` integrieren. Bereits enthaltene oder durch neuere Fassungen ersetzte Änderungen
   nicht erneut einspielen. Historische Branches mit Laufzeitdateien benötigen einen
   geprüften Quellenpatch; ihre gesamte Historie gehört nicht in die Veröffentlichung.
3. Konflikte fachlich lösen und die betroffenen Java-8-Tests auf dem integrierten Stand ausführen.
4. Nach jedem abgeschlossenen Entwicklungsdurchgang die komplette Differenz und
   Historie zum vorgesehenen PR-Basisbranch prüfen. Ausschließlich eigene geprüfte
   Änderungen auf einen eigenen `codex/`-Aufgabenbranch committen und mit
   explizitem Ziel pushen, beispielsweise:

   ```powershell
   git push origin HEAD:refs/heads/codex/aufgabenbranch
   ```

5. Automatisch einen eigenen PR für diesen Durchgang nach
   `YoungJibbit95/Neutron-Client:main` erstellen. Hängt der Durchgang von einem noch
   offenen PR ab, dessen Branch als Basis für einen Stacked PR verwenden und die
   Abhängigkeit dokumentieren. Bei offenen erforderlichen Spiel-, Lifecycle-, CI-
   oder sonstigen Abnahme-Gates einen Draft-PR erstellen. Keine erneute Rückfrage
   für normale Commits, Pushes oder die PR-Erstellung; fehlender Spiel-/Serverzugang
   wird im Draft dokumentiert. `codex/test-integration` nur für ausdrücklich
   beauftragte gemeinsame Integrations-PRs verwenden. Merge und Veröffentlichung
   als Release/Deployment benötigen weiterhin den entsprechenden Nutzerauftrag
   und erfüllte Projektfreigaben; andernfalls den PR offen lassen.
6. Nach dem GitHub-Merge den Hauptcheckout sicher synchronisieren. Normale Merge-Commits
   erhalten die gemeinsame Integrationshistorie. Nicht direkt auf `origin/main` pushen.
7. Nicht mehr benötigte Worktrees erst nach Sicherung der Änderungen und lokalen Nachweise
   regulär entfernen. Git-Branch-Referenzen erhalten historische Commits ohne weitere
   ausgecheckte Quellverzeichnisse. Abschlussbericht nennt SHA, PR, Tests und lokale Restdaten.

`push.default=nothing` verlangt ein explizites Push-Ziel. Force-Push, erzwungenes Entfernen,
Verwerfen fremder Änderungen und vollständiges Cherry-Picken von `98ed75e23` sind ausgeschlossen.

Der ursprüngliche Entwicklungsstand bleibt über `codex/revived-final-integration` erhalten.
Der frühere historische lokale `main` bleibt unter `codex/main-before-intellij-20261029` erhalten.
