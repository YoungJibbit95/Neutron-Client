# Windows-Playtest — Korrekturrunde R2

**Historischer R2-Kandidat:** Die beiden ursprünglichen Korrekturen und 115 Tests wurden unabhängig bestätigt, jedoch ein weiterer P1 bei fehlgeschlagener Terminierung gefunden. Aktuell: [REVIEW-WINDOWS-R3.md](REVIEW-WINDOWS-R3.md). Kein R2-Release-Go.

**Erneute technische Prüfung, keine Release-Freigabe.** R1 wurde nach unabhängig bestandenen 109 Tests und bestätigtem Installerhash wegen zweier konkreter Fehler zurückgegeben. Beide Fehler sind korrigiert. Die Escape-Sperre wurde nicht aufgehoben; keine Launcher-/Client-/Installeroberfläche bedient und nichts veröffentlicht.

## Angeforderte Korrekturen

| Fall | Vorher | Jetzt | Gezielter Nachweis |
| --- | --- | --- | --- |
| P1 Start/Stop/Shutdown | `runProcess` wartete auf `mkdir`, während `launch` ein altes Lease-Promise abwartete. Stop konnte einen noch nicht registrierten Start verfehlen. | Reservierung von Version und `clientStarting` vor jedem Await. Sofort verfügbares Prozesshandle mit getrenntem `started`-/`exited`-Handshake; Spawn-Erfolg erst beim OS-`spawn`-Ereignis, Fehler lehnen den Handshake ab. Start/Stop umgehen die allgemeine Befehlswarteschlange und teilen sich Startabbruch, Handle und Bereinigung. Shutdown sperrt neue Starts und wartet denselben Ablauf ab. Lease-Bereinigung folgt dem abgeschlossenen oder fehlgeschlagenen Start, sodass kein verspäteter Lease wieder entstehen kann. | Fünf Lifecycle-Tests mit gezielt verzögerter Vorbereitung, exakt verzögertem Prozess-`mkdir`, sofortigem Stop/Shutdown, zweitem Launch, verzögerter Lease-Zuordnung und echtem Spawn-Fehler. Nur eigene unsichtbare Node-Hilfsprozesse; Vorbereitung/Identitätsadapter werden ersetzt. Kein Minecraft-/Electronstart. |
| P2 Offline-Sackgasse | Nach Checkfehler und lokalem Start/Ende waren `online=false`, Fehlerbanner weg und alle Check-Buttons deaktiviert. | Manueller Check hängt nicht vom zuletzt ermittelten Online-Wert ab. Sichtbare DE/EN-Aktion „Verbindung erneut prüfen“; aktive Aufgabe/Client sowie notwendige Einrichtung bleiben geschützt. Ein erfolgreicher Check aktualisiert wieder `online` und Commitzustand. Neue Builds bleiben bis zum erfolgreichen Recheck netzabhängig. | Backend-Sequenz `Checkfehler → lokaler Node-Helferstart/-stopp → erfolgreicher Check` und ein DE/EN-Server-HTML-Test für Launch/Updates mit Offline-, Busy- und Running-Guards. |

Die UI zeigt die Startreservierung ausdrücklich als „Clientstart wird vorbereitet“ mit Abbruch; sie verwendet dafür keine fiktive Build-Phasenliste. Das ist implementierter Code, kein visueller Pass.

## Aktueller Installer

| Merkmal | Wert |
| --- | --- |
| Version | `0.1.0-playtest.2` |
| Datei | `F:\Neutron Launcher Playtest 20261028\Installer Candidate R2\Neutron-Launcher-0.1.0-playtest.2-Windows-x64.exe` |
| SHA256 | `e87ef83ec58e3085c1eb749a05211ecf664c9ea9aaa09e28947ce5e2e0afe966` |
| Größe | 112.465.776 Bytes |
| Architektur/Signatur | App PE AMD64; Installer und App `NotSigned`. |

Maßgeblicher Beleg: `F:\Puslar Backup - Copy\launcher\docs\evidence\windows-playtest-r2\candidate.json`. R1 bleibt historisch; sein Installer ist nicht der aktuelle Kandidat.

## Prüfstand

- **115/115 Vitest-Tests, 0 Fehler**, Typechecks/Produktionsbuild und frischer NSIS-Paketbau Exit 0. Sechs gezielte neue Regressionstests gegenüber den unabhängig geprüften 109 Fällen. Die `mkdir`-Barriere prüft ausdrücklich den Prozess-Arbeitsordner; zuvor werden Hintergrundlogs geleert, damit kein zufälliger Logordner-Aufruf den Test erfüllt.
- Alle **25** eigenen kompilierten Renderer-/Backend-Dateien sowie **3** Adapterdateien erneut bytegleich im Paket geprüft. Beide Fonts, Lizenzen und ZIP-Laufzeitmodule enthalten; keine privaten Test-/Evidenzdaten im Paket.
- Echter Maven-Prozessbaum mit dem neuen Prozessrunner erneut abgebrochen; Auswahl und Registry unverändert. Beleg: `cancellation.json`.
- Vorhandene gehashte Toolchain, 722 Assets und Distribution mit dem neuen Runner erneut geprüft; Java-Bootstrap `--validateOnly` erneut Exit 0. Wiederherstellung ohne GitHub-Abfrage erneut geprüft. **Diese Runde verwendet die bereits gebaute Distribution und führt keinen weiteren vollständigen Maven-Build aus.**
- Die beiden vollständigen GitHub-Builds aus R1 bleiben für unveränderten Client/Adapter gültige Herkunftsnachweise: Commit `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`, je 400 JUnit-Fälle/0 Failures/0 Errors/3 Skipped, 61 Client-Classpath-Dateien plus offizielles LWJGL-Paar. Unveränderter Adapter-SHA256 `ce8898df1b0e6ac4d54a2a8fb6b0b93c98d0918ae58b06e69d0663daa0c7234c`. Kein neuer Cold-Build-Pass wird behauptet.
- Die Rückauswahl zwischen zwei geprüften Instanzen desselben Commits ist der R1-Baseline-Nachweis. Echte Laufzeit-Rollbacks unterschiedlicher Clientstände bleiben offen.
- NSIS verwendet nur seinen stillen WriteUninstaller-Buildhelfer. Kein Installations-/Deinstallationslauf oder interaktiver Installer-/Appstart. R2 verwendet einen neuen eigenen Ausgabeordner und wurde erfolgreich erzeugt.

Detaildateien unter `docs/evidence/windows-playtest-r2/`: `commands.json`, `tests.json`, `build.txt`, `installer.txt`, `package-inspection-r2.json`, `signature-version-r2.json`, `cancellation.json`, `integration.json`, `maven-tests.json`, `source-inventory.json` und `publication-scope.json`.

## Veröffentlichungsumfang

Aktueller isolierter Checkout `F:\Neutron Launcher Playtest 20261028\Publish Checkout`, Branch `codex/launcher-playtest`, Basis `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`. Nun **53** eigene Launcher-/CI-Dateien einschließlich des neuen Lifecycle-Tests. Git-Index-Baum **`ef77c7113b82d636b13492468f377be69411951e`**. Exakte Liste/Hashes und Ausschlüsse in `publication-scope.json`; keine tatsächlichen Release-Commits, Pushes, Tags oder Releases. Gemeinsamer Index unverändert.

Alle persönlichen `test_run/`-Daten, Forschungs-/Design-Arbeiten, gemeinsame Client-/POM-Änderungen, lokale Evidenz, Logs, Credentials, Caches und generierte Artefakte bleiben ausgeschlossen. Der parallel geänderte gemeinsame Assembly-mainClass bleibt unangetastet und ausdrücklich außerhalb dieses Kandidaten. Die zwei gemeinsamen Bootstrap-Dateien stimmen weiter mit ihrer Baseline überein. Kein falscher Root-POM-Unverändert-Pass.

## Weiterhin offene externe Gates

Keine reale Installation/Deinstallation, kein Launcher-/Clientfenster, keine echten Settings-/Shader-/Offline-Spielstart-/Build-and-play-/Crash-/Wiederanlauf-/Schließ-/Dialog-/Export-/Fokus-/Scroll-/DPI-Tests. Die neuen Helper-/SSR-Tests ersetzen diese Abnahme nicht. Ohne erneute menschliche Freigabe keine Computersteuerung oder Umgehung.

Signierung/Herausgeber und SmartScreen bleiben ungeklärt. CI ist vorbereitet, nicht auf GitHub ausgeführt. Vor tatsächlichem Push/Release aktuellen Remote-Stand, konkreten sauberen Commitumfang und exakt auszuliefernden Installer unabhängig prüfen lassen. Die Nutzerautorisierung für den späteren stabilen Playtest-Release besteht bereits; kein neues pauschales Release-Okay verlangen.

Bitte beide Korrekturen und den neuen Kandidaten unabhängig prüfen. Noch kein technischer Release-Go beantragt, solange reale Stabilitäts- und Signierungsgates offen sind.

REVIEW BEREIT – Windows Playtest (Integration/Installer-Kandidat R2)
