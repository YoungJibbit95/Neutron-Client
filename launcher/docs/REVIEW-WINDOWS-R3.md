# Windows-Playtest — Korrekturrunde R3

**Neuer technischer Kandidat `0.1.0-playtest.3`, noch kein Release-Go.** R2 wurde nach unabhängig bestandenen 115 Tests wegen eines weiteren P1 bei fehlgeschlagenem `taskkill` zurückgegeben. R1/R2 bleiben historische Nachweise. Maßgeblich sind jetzt dieser Bericht und `docs/evidence/windows-playtest-r3/candidate.json`.

## Behobener P1

Vorher verwarf ein fehlgeschlagener Terminierungsversuch das `exited`-Promise. Stop verschluckte den Fehler, und der Service konnte Lease/Versionsschutz entfernen, obwohl der Client noch lebte.

Jetzt sind Kontrolle und Prozessende getrennt:

- `exited` schließt bei OS-`close` oder fehlgeschlagenem Spawn ab. Fehler einer Terminierung verwerfen dieses Promise nicht.
- Jeder Stop-Versuch hat ein eigenes Promise. Ein fehlgeschlagenes `taskkill` wird sichtbar zurückgewiesen; das Handle bleibt aktiv und ein neuer Versuch ist möglich. Eine nicht abgeschlossene Terminierung weist nach zehn Sekunden zurück, ohne Prozessende zu behaupten.
- Prozessgruppen halten Client, vorbereitende Java-/Identitätsprozesse und Hintergrundarbeiter fest. Ein fehlgeschlagener Stop lässt PID, Lease und Versionsschutz bestehen. Aufgabe/Cache-Workspace werden erst nach tatsächlichem Ende freigegeben.
- Abbruch eines fortlaufenden Mavenprozesses hält den Task geschützt. Das abgebrochene Signal verhindert spätere Aktivierung, selbst wenn der Prozess nach einem verweigerten Stop zunächst weiterarbeitet.
- Shutdown wartet nicht auf ein fiktives Prozessende: Ein Stop-Fehler wird zurückgemeldet, erfolgreicher Quit nicht behauptet und der Schließzustand für einen neuen Versuch zurückgesetzt.
- Die R2-Korrekturen — Startreservierung/Handshake und erreichbarer Offline-Recheck — bleiben erhalten.

## Gezielte Regressionen

Vier neue Tests gegenüber R2, insgesamt **119/119 bestanden**:

| Test | Beobachtetes Ergebnis |
| --- | --- |
| Erste Terminierung absichtlich verweigert | Eigener Node-Helfer lebt weiter, `exitCode=null`, `exited` bleibt offen und die Prozessgruppe aktiv. Zweiter Stop beendet denselben Helfer. |
| Client-Stop und Shutdown verweigert | Lease und `runningBuild` bleiben erhalten, zweiter Launch gesperrt, Shutdown weist sichtbar zurück. Erfolgreicher Retry beendet den Helfer und entfernt erst danach die Lease. |
| Terminierung hängt | Stop-Frist weist zurück, Prozess lebt und bleibt geschützt; erneuter Stop funktioniert. Die kurze Frist ist ausschließlich Testkonfiguration. |
| Hintergrundabbruch verweigert | Eigener tatsächlich laufender Worker bleibt geschützt; neue Starts/Checks blockiert, Shutdown weist zurück. Retry beendet ihn; Registry/ausgewählte Version unverändert. |

Nur eigene Node-CLI-Helfer und kontrollierte Terminierungsadapter; keine Electron-/Minecraft-/Installeroberflächen. Die vorhandenen verzögerten Start-/mkdir-/Lease-/Spawnfehler- und Offline-Regressionen sind ebenfalls erneut bestanden.

**Zusätzlich echter Mavenprozess:** Nicht nur der Helper-Test. Der aktuelle Service startete Maven aus dem eigenen verwalteten JDK. Erst nach bestätigtem OS-Spawn wurde dessen Terminierung gezielt verweigert. PID lebte danach, neue Clientstarts waren blockiert und Shutdown wies zurück. Anschließender erneuter Abbruch gelang; Auswahl und Buildanzahl blieben unverändert. Beleg: `maven-stop-failure-r3.json` mit drei Terminierungsversuchen. Kein Clientfenster.

## Installer und Paket

| Merkmal | Wert |
| --- | --- |
| Version | `0.1.0-playtest.3` |
| Datei | `F:\Neutron Launcher Playtest 20261028\Installer Candidate R3\Neutron-Launcher-0.1.0-playtest.3-Windows-x64.exe` |
| SHA256 | `be884235ea52bf307c25b86db5dd24f38e69301f1951dc730cccca0d27976970` |
| Größe | 112.466.909 Bytes |
| Architektur/Signatur | App PE AMD64, App und Installer `NotSigned`. |

Typechecks/Produktionsbuild, 119 Tests und frischer NSIS-Build Exit 0. Wieder 25 eigene kompilierte Renderer-/Backend-Dateien und 3 Adapterdateien bytegleich im Paket. Beide Fonts, Lizenzen und ZIP-Laufzeitmodule vorhanden; keine privaten Test-/Evidenzdaten im Paket. Der stille NSIS-WriteUninstaller-Buildhelfer installiert/deinstalliert nichts; keine Installationsoberfläche wurde bedient.

Bootstrap `--validateOnly`, gehashte Toolchain/Assets/Distribution und Wiederherstellung ohne GitHub-Abfrage erneut geprüft. Dafür wurde die vorhandene Distribution verwendet. Die beiden vollständigen GitHub-Maven-Builds bleiben ausdrücklich R1-Baseline für unveränderten Client/Adapter: Commit `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`, je 400 JUnit-Fälle, 0 Failures/Errors, 3 Skipped. Kein weiterer vollständiger Cold-Build wird behauptet. Der neue echte Maven-Fehlertest endet bewusst durch Abbruch.

## Konkreter Quellenumfang

Isolierter Checkout `F:\Neutron Launcher Playtest 20261028\Publish Checkout`, Branch `codex/launcher-playtest`, Basis `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`. **53** eigene Launcher-/CI-Dateien, Git-Index-Baum **`51ea77efe44de4d0007245131d49c1fd20134215`**. Liste/Hashes: `publication-scope.json`; keine tatsächlichen Release-Commits, Pushes, Tags oder Releases.

Persönliche Spielstände/Logs, Forschung/Design, gemeinsame Client-/POM-Änderungen, Credentials, Caches und interne Evidenz bleiben ausgeschlossen. Der gemeinsame Index wurde nicht geändert. Der parallel extern geänderte Assembly-mainClass bleibt unangetastet; beide gemeinsamen Bootstrap-Dateien stimmen weiterhin mit ihrer Baseline überein. Der abweichende Root-POM-Fingerprint ist im Bericht ausdrücklich kein bestandener Unverändert-Nachweis.

## Weiterhin offene Gates

Keine reale Installation/Deinstallation oder Launcher-/Client-UI, keine echten Settings-/Shader-/Offline-Spielstart-/Build-and-play-/Crash-/Wiederanlauf-/Schließ-/Dialog-/Export-/Fokus-/Scroll-/DPI-Prüfungen. Diese bleiben bis zu erneuter menschlicher Freigabe durch die Escape-Sperre angehalten. Die Prozesshelfer und der Bootstrap ohne Spiel ersetzen sie nicht.

Signierung/Herausgeber und SmartScreen sind ungeklärt. CI ist vorbereitet, nicht auf GitHub ausgeführt. Der spätere stabile Playtest-Release ist vom Nutzer autorisiert; keine neue pauschale Release-Erlaubnis verlangen. Vor tatsächlichem Push/Release sind konkrete saubere Commits, exakt auszuliefernder Installer und unabhängiger technischer Release-Go nötig.

Bitte den P1-Fix und diesen neuen Kandidaten unabhängig prüfen. Wenn keine weiteren unabhängig behebbaren Codefehler vorliegen, die tatsächlich extern fehlenden UI-/Signierungsvoraussetzungen getrennt benennen.

REVIEW BEREIT – Windows Playtest (Integration/Installer-Kandidat R3)
