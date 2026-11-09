# Windows-Playtest R4 — Heap-Fehler aus realem Build

Stand: 28. Oktober 2026. **Technischer Kandidat zur Prüfung, kein Release-Go.** R3 war technisch akzeptiert; der anschließende echte Build lieferte einen neuen Laufzeitfehler. Der Nutzer stoppte die Computersteuerung danach erneut mit physischem Escape. Seitdem keine weiteren Desktop-, Installer- oder Client-Eingaben. Diese Runde prüft ausschließlich CLI-/Paketpfade.

## Fehler und enge Korrektur

Der Nutzeroutput zu Commit `d5a277de779f6cf69768a2900e299c9af5810e1a` zeigt neun fehlgeschlagene Test-JVM-Starts mit `Could not reserve enough space for object heap`; 362 Fälle waren bis zum Abbruch gemeldet. Der betroffene Test startet Java ohne Heap-Parameter. Die echte JDK-Probe auf demselben Gerät zeigt automatisch 534.773.760 Bytes Startheap und 8.533.311.488 Bytes Maximalheap. Beim Auslesen des Fehlerzustands blieben etwa 1,9 GiB Windows-Commit-Spielraum. Der spätere erfolgreiche Nachtest fand unter anderer Systemlast statt; der ursprüngliche Speicherdruck wird nicht künstlich nachgestellt.

`processes.cts` setzt ausschließlich im neuen `buildEnvironment` feste eigene `JAVA_TOOL_OPTIONS=-Xms32M -Xmx512M` **nach** Entfernen fremder JVM-Injektionen. Damit erben Surefire und dessen zusätzliche Java-Kindprozesse begrenzte Defaults. Maven erhält explizit `-Xms64M -Xmx1536M`; die echte Probe bestätigt deren Vorrang. Der Spielstart bleibt bei `childEnvironment` ohne diese Variable und bei der gewählten Spiel-RAM-Grenze. Keine Testdeaktivierung, keine Änderung gemeinsamer Client-/Combat-/Scaffold-Dateien oder des gemeinsamen POM. Adapter weiterhin bytegleich zu R3.

Grundlage für die Initialisierung auch verschachtelter VMs: [Oracle JAVA_TOOL_OPTIONS](https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/envvars002.html). Die lokale Java-Probe und der reale Maven-Nachtest sind die Verhaltensbelege.

## Ausgeführte Prüfungen

- Beide Typechecks, Produktionsbuild und **120/120 Launcher-Tests** bestanden. Eine zusätzliche Regression startet einen echten verschachtelten Hilfsprozess und prüft geerbte Build-Grenzen, entfernte Injektionen sowie unveränderte bereinigte Spielumgebung.
- Derselbe fehlgeschlagene R3-Quellworkspace, bestehende kompilierte Quellen, vollständiges Maven-`test` offline mit korrigierter Umgebung: **400 Fälle, 0 Failures, 0 Errors, 3 vorhandene Skips**. Originalberichte vor dem Nachlauf auf F: erhalten. Dies ist kein neuer Clean-Build.
- Zusätzlich vollständiger produktiver `buildDistribution`-Lauf auf dem festen aktuellen GitHub-Commit `d5a277d`, frisches Quellverzeichnis, `clean package`: **400 Fälle, 0 Failures, 0 Errors, 3 Skips**. Distribution vollständig geprüft, Bootstrap `--validateOnly` Exit 0 und Marker `NEUTRON_BOOTSTRAP_VALIDATED`. Neue unveränderliche Version erzeugt; vorherige `state.json`/Auswahl unverändert. Kein Clientfenster.
- Frischer Windows-NSIS-Build Exit 0, eigener F:-Output/TEMP. 25 eigene kompilierte Dateien und 3 Adapterdateien im Paket bytegleich; AMD64, Version `0.1.0-playtest.4`, keine privaten Evidenz-/Spiel-/Git-Pfade.

## Exakter Kandidat

| Feld | Wert |
| --- | --- |
| Installer | `F:\Neutron Launcher Playtest 20261028\Installer Candidate R4\Neutron-Launcher-0.1.0-playtest.4-Windows-x64.exe` |
| SHA256 | `9830487166da91ec87af1eae444c79980616a28e775b5fa2d55169f269a950bf` |
| Bytes | 112466655 |
| Signatur | App und Installer `NotSigned` |
| Quellenbasis | `d5a277de779f6cf69768a2900e299c9af5810e1a` |
| Isolierter Git-Index-Baum | `6f1704838e93f21d4d12eb4ccd0135610169a747` |
| Veröffentlichungsumfang | 53 explizite Launcher-/CI-Dateien; keine Commits/Pushes/Tags/Releases |

Gegen R3 ändern sich nur sechs Quelldateien: `distribution.cts`, `processes.cts`, `service.cts` (Diagnoseversion), `package.json`, `package-lock.json` und `backend.test.ts`. Dazu aktuelle Status-/Releasedokumente. Vergleich mit den unveränderten R3-Dateien des alten isolierten Checkouts ist in `evidence/windows-playtest-r4/r3-to-r4.patch` enthalten. Dessen Basisdateien wurden gegen das R3-Quellinventar gehasht. Neuer eigener Checkout `F:\Neutron Launcher Playtest 20261028\Publish Checkout R4` auf aktuellem Remote-main schützt parallele Clientarbeit; der alte R3-Checkout bleibt erhalten.

## Grenzen und offene Gates

R3-Teilabnahme: echte unbeaufsichtigte Installation auf F:, Uninstaller vorhanden, 76 App-Dateien bytegleich, installierter Launcher, echte Ersteinrichtung, vier Themes DE/EN, 125 % UI-Skalierung, Schließdialog/Zurück. Der ursprüngliche Uninstaller-Überschreibfehler ist nicht sicher erklärt. Ein nativer Exportdialog blieb beim erneuten physischen Escape-Stopp offen. Keine vollständige UI-Abnahme, keine Deinstallation, kein echter Client-/Offline-/Settings-/Crash-/Wiederanlauf-Pass. **R4 wurde weder installiert noch interaktiv gestartet.** NSIS führt nur den bekannten stillen WriteUninstaller-Paketbauhelfer aus.

Der Nutzer möchte ein vorhandenes Zertifikat oder einen Signierdienst nutzen. In CurrentUser/My und LocalMachine/My kein Codesignaturzertifikat, keine erkennbaren konfigurierten Signier-Env-Namen. Nutzer kennt keinen Speicherort/Dienst. Signierung und SmartScreen bleiben offen; keine eigenmächtige unsignierte Veröffentlichung. CI nicht auf GitHub gelaufen. Vollständiger Release-Go bleibt ausgeschlossen, bis reale Endabnahme und Signierung geklärt sind.

Belege: `evidence/windows-playtest-r4/candidate.json`, `heap-regression.json`, `maven-retry-summary.json`, `full-build-regression.json`, vollständige Maven-Logs, `tests.json`, `commands.json`, `package-inspection.json`, `signature-version.json`, `source-inventory.json`, `publication-scope.json` und reproduzierbare interne CLI-Skripte als `.txt`. Interne Evidenz ist kein Release-Asset.

Bitte den Heap-Fix, die Regressionen und den exakten R4-Kandidaten unabhängig technisch prüfen. Keine neue Computersteuerung oder Veröffentlichung aus dieser Review-Anfrage ableiten.

## Unabhängige technische Abnahme

Der Review-Chat hat R4 unabhängig technisch abgenommen, ausdrücklich ohne Release-Go. Beide Typechecks und 120/120 Launcher-Tests erneut bestanden; echte verwaltete Java-8-Proben über den kompilierten `buildEnvironment`-Code bestätigen 32/512 MiB und explizite Maven-Flags 64/1536 MiB. Bereinigte Spielumgebung ohne `JAVA_TOOL_OPTIONS` und ausschließliche Verwendung am Maven-Pfad nachgeprüft. 25 kompilierte Dateien, 3 Adapterdateien und Installer-SHA256 unabhängig bestätigt. Die vollständigen Maven-Test-/Clean-Package-Belege wurden gelesen, vom Reviewer nicht nochmals ausgeführt. Kein weiterer konkreter Befund zum engen Fix. Feste Heapgrenzen garantieren keinen Erfolg unter beliebigem systemweitem Speicherdruck.

Weitere UI-/Installer-/Clientinteraktionen warten nach dem erneuten physischen Escape-Stopp auf neue ausdrückliche menschliche Freigabe. R4-Endabnahme und ein tatsächlich verfügbarer Signierzugang fehlen. Kein Commit/Push/Tag/Release oder neue Automation.
