# Review — Windows-Playtest: Integration und Installer-Kandidat

**Historischer Kandidat R1, im Review wegen Start-/Stop-Rennen und Offline-Recheck zur Korrektur zurückgegeben.** Aktueller Stand: [REVIEW-WINDOWS-R2.md](REVIEW-WINDOWS-R2.md). Die nachfolgenden Artefakte/Hashes und 109 Tests dokumentieren ausschließlich R1.

**Technischer Kandidat, kein freigegebener Release.** Abschnitt 3 wurde nach unabhängiger Prüfung der drei Korrekturen und 94 Tests technisch akzeptiert. Danach hat der Review-Chat echte Integration und Windows-Paketierung freigegeben. Kein Electron-/Clientfenster und keine Installationsoberfläche wurden bedient. Die Escape-Sperre bleibt bestehen.

## Konkreter Installer

| Merkmal | Wert |
| --- | --- |
| Version | `0.1.0-playtest.1` |
| Datei | `F:\Neutron Launcher Playtest 20261028\Installer Candidate Review\Neutron-Launcher-0.1.0-playtest.1-Windows-x64.exe` |
| SHA256 | `7074c642753505255549f8573e5420b4ebf0bc0eccdd3c29c2f9a295c5d6d5a0` |
| Größe | 112.464.901 Bytes |
| App-Architektur | PE AMD64; native Electron-Fensterfunktionen, kein nachgezeichnetes Fenster. |
| Signierung | Installer und App `NotSigned`. Kein Signierungs-/SmartScreen-Pass. |
| Paketmetadaten | Paketversion `0.1.0-playtest.1`; Windows FileVersion `0.1.0-playtest.1`, normalisierte ProductVersion `0.1.0.0`. |

Alle maßgeblichen Daten maschinenlesbar: `F:\Puslar Backup - Copy\launcher\docs\evidence\windows-playtest\candidate.json`. Nur dieser Ordner „Installer Candidate Review“ ist der aktuelle Prüfkandidat. Ältere Ausgaben werden nicht als aktuelle Artefakte ausgegeben.

## Implementiert

- Typisierte und zur Laufzeit validierte IPC ausschließlich vom eigenen Main Frame unter `neutron://launcher/index.html`. Kein freier Shell-/URL-/Prozessaufruf aus dem Renderer. Sandbox/Context Isolation, keine Node-Integration, Navigation/neue Fenster blockiert.
- Standard-UI mit echten Backend-Snapshots. Keine Fixture-Builds, simulierten Logzeilen oder Beispielsteuerung im normalen App-Einstieg. Die alten Seeds bleiben ausdrücklich für Tests. Die drei Abschnitt-3-Korrekturen bleiben erhalten.
- Atomar gespeicherte Optionen/Themes/Sprache, Hardware-RAM-Grenze, echter Spiel-/Java-Pfadpicker, begrenzte zusätzliche JVM-Argumente, echte Berichts-/Export- und Schließaktionen.
- Verwaltetes Temurin 8u504-b01 und Maven 3.9.16, feste Prüfsummen, offizielle Minecraft-1.8.9-Assets/Natives, Windows-x64-Headerprüfung. Keine globalen Java-/Maven-/Git-/Python-Werkzeuge beim Spieler.
- Fest aufgelöster GitHub-Commit, ZIP-Archiv mit Pfad-/Link-/Größenprüfung, eigener Workspace/Dependency-Cache und direkter Java/Classworlds-Aufruf ohne Shell. Gehashte neue Distribution, Aktivierung erst nach Prüfung, alte Versionen bleiben erhalten.
- Getrennte reale Logs, maximal 2.000 UI-Zeilen, ID-/Offset-Scrollanker, Datei-Write-Batches, Rotation/Restore, redigierter Copy/Export/Diagnosebericht. Echte Exitcodes im Zustand.
- Schutz laufender Versionen, Prozessbaum-Abbruch, Single-Instance-Lock und persistente Prozessidentität für Wiederübernahme nach Launcherabbruch. PID, Programmdatei, Startzeit und Befehlszeilen-Hash schützen vor Verwechslung. Ein unprüfbarer Datensatz blockiert neue Starts/Builds. Neue stdout-Ausgabe eines wiederübernommenen Prozesses ist nicht erneut anbindbar.
- NSIS-Installer-Konfiguration ohne Datenlöschung bei normaler Deinstallation und ohne automatischen Appstart; eigenes Neutron-Symbol. Gepinnte Windows-CI vorbereitet, noch nicht auf GitHub ausgeführt.

Dateivertrag und enge Legacy-Anpassungen: [INTEGRATION.md](INTEGRATION.md). Version und Einschränkungen: [RELEASE-NOTES.md](RELEASE-NOTES.md). Aktuelle Gating-Matrix: [GATES.md](GATES.md).

## Tatsächlich geprüft

| Prüfung | Ergebnis | Beleg unter `docs/evidence/windows-playtest/` |
| --- | --- | --- |
| Renderer-/Backend-Typechecks und Produktionsbuild | Exit 0, ohne Appstart. | `commands.json`, `build.txt` |
| Vitest | **109/109, 0 Fehler.** 94 vorhandene Prüfungen plus 15 Backend-/OS-/Datei-/Prozesstests. | `tests.json`, `tests.txt` |
| Reale verwaltete Einrichtung | Eigenes heruntergeladenes Java/Maven auf F:, 722 Asset-Objekte und passende Natives gehasht. Keine vorbereiteten globalen Werkzeuge für den Produktionspfad. | `integration.json`, `maven-build-and-cancel.log` |
| Echte GitHub-Builds | Zweimal Commit `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`; Archiv-SHA256 `426aaaad120660f3b622543381c03e184ef40e36ebc64b9b251fe3b31cdc6524`. Je **400 JUnit-Fälle, 0 Failures/Errors, 3 Skipped**. | `maven-tests.json`, `integration.json` |
| Distribution und Bootstrap | 61 gehashte Client-Classpath-Dateien plus offizielles LWJGL-Paar; Bootstrap `--validateOnly` Exit 0 mit `NEUTRON_BOOTSTRAP_VALIDATED`. Kein Minecraft-Fenster. | `integration.json` |
| Versionsrückauswahl | Frühere geprüfte Build-Instanz ausgewählt und wieder zurückgewechselt. Beide Instanzen stammen vom gleichen Commit; keine Laufprüfung verschiedener Clientstände. | `integration.json` |
| Echter Maven-Abbruch | Prozessbaum am Einstieg in die Mavenphase abgebrochen; Auswahl und Build-Liste unverändert, bestehende Distribution erneut geprüft. | `cancellation.json` |
| Wiederherstellung ohne Netzwerk | Service neu instanziiert; gespeicherte Auswahl, Toolchain, Assets und Distribution ohne GitHub-Abfrage geprüft. Kein tatsächlicher Offline-Spielstart. | `integration.json` |
| Fehler-/Dateitests | Hashfehler/Abbruch ersetzen keine gültige Datei; Redirect-/Archiv-/Junction-Grenzen; Settings-Recovery; begrenzte Logrotation/Redigierung; injizierter Netzwerkfehler mit erhaltenem Profil/Registry. | `tests.json`, `backend.test.ts` im Quellinventar |
| Prozesse | Eigenen echten Hilfsprozess samt Kind beendet. Windows-CIM liest den eigenen Testprozess; Identitäts-/Zeit-/Befehlszeilenabweichung wird verworfen. Keine Wiederübernahme eines echten Minecraft-Prozesses getestet. | `tests.json` |
| Paketinhalt | Alle **25** eigenen kompilierten Renderer-/Backend-Dateien und **3** Adapterdateien bytegleich zum aktuellen Build. Beide Fonts und fünf Lizenztexte enthalten; benötigte ZIP-Module vorhanden. Keine `test_run`-/Design-/Git-/Evidenzdaten im Paket. | `package-inspection.json` |
| Installer | Aktueller NSIS-Build Exit 0, Hash, Architektur, Versionsmetadaten und fehlende Signatur lesend geprüft. Kein Installations-/Deinstallationsnachweis. | `installer.txt`, `signature-version.json`, `candidate.json` |

Ein vorhandener NSIS-Ausgabepfad ließ sich bei einem Folgeversuch nicht öffnen. Der erfolgreiche finale Build verwendet deshalb einen neuen eigenen Ausgabeordner, ohne bestehende Prozesse/Installer zu starten oder zu beenden. Beide Ausgaben sind getrennt protokolliert: `installer-initial-failed.txt` und `installer.txt`. Die Paketierung selbst führt den **stillen NSIS-WriteUninstaller-Buildhelfer** aus; dieser schreibt nur das spätere Deinstallationsprogramm und installiert/deinstalliert nichts. Dies ist kein Nutzer-Installerlauf und kein interaktiver Test.

Der erste strikte Maven-Versuch scheiterte an den Legacy-Koordinaten/fehlenden Prüfsummen. Der isolierte Adapter korrigiert sie und seedet acht offizielle gehashte JARs mit minimalen lokalen POMs. Strikte Prüfsummen wurden nicht abgeschaltet. Keine Combat-/Scaffold-/GUI-Klasse wurde dafür verändert. Der Shader-Pfadalias entsteht ausschließlich bei der JAR-Paketierung.

## Quellen und vorgesehener Veröffentlichungsumfang

Isolierter Checkout: `F:\Neutron Launcher Playtest 20261028\Publish Checkout`, Branch `codex/launcher-playtest`, Basis Remote-main `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`.

**52 ausdrücklich ausgewählte Launcher-/CI-Dateien**, geprüfter Git-Index-Baum `c12437b3f90bc42629798d1e52460bd479e33483`. Vollständige Pfade/Hashes: `publication-scope.json`; Staged-Diff: `publication-diff-stat.txt`. Keine Release-Commits existieren; keine Pushes/Tags/Releases. Geplanter eigener Commit: „Add Windows playtest launcher and guarded local client builds“. Vor einem tatsächlichen Commit/Push aktuellen Remote-Stand und unveränderten Umfang erneut prüfen lassen.

Ausgeschlossen sind sämtliche persönlichen `test_run/`-Daten, Forschungs-/Design-Arbeiten, `.vscode`, gemeinsame `src/`-/POM-Änderungen, Python-Entwicklerfixtures, lokale Evidenz, Logs, Caches, Credentials und generierte Buildordner. Es wurde nur im isolierten Index gestaged; der gemeinsame Index wurde nicht verändert. Eine anfangs unvollständige Sparse-Index-Basis wurde durch den strikten Pfadvergleich erkannt und im isolierten Checkout aus dessen unverändertem HEAD wieder aufgebaut. Der finale Staged-Diff enthält keine fremden Löschungen.

**Paralleländerung am gemeinsamen POM:** Gegenüber der Abschnitt-3-Baseline wurde während dieser Arbeit extern der Assembly-mainClass von `launchwrapper.Launch` auf `net.minecraft.client.main.Main` geändert. Diese Änderung wurde weder überschrieben noch kopiert oder gestaged. Die beiden gemeinsamen Bootstrap-Dateien stimmen weiterhin mit der Baseline überein. `candidate.json` enthält den ehrlichen Fingerprint-Abgleich; kein pauschaler „Root-POM unverändert“-Pass. Der Kandidat baut aus dem Remote-Archiv mit dem eigenen Adapter.

## Noch offen — Veröffentlichung blockiert

Reale Installer-Installation/Start/Deinstallation ohne Datenverlust, tatsächliches Launcher-/Clientfenster, Client-Settings/Shader-Alias, Offline-Spielstart, Build-and-play, Crash/Neustart/Prozessübernahme, Schließen, native Dialoge/Clipboard/Export, Themes/Sprachen/DPI/Fokus und Scrollverhalten bleiben ungeprüft. Browser-/Desktop-/Electron-/Clientsteuerung darf erst nach erneuter menschlicher Freigabe erfolgen. Kein grüner Unit-/Bootstrap-/Pakettest ersetzt diese Abnahme.

Signierungs-/Herausgeberzugang und SmartScreen-Reputation sind ungeklärt. Ein Pre-Release umgeht dieses Gate nicht. Linux/macOS bleiben langfristig ohne Unterstützungszusage. CI ist nur vorbereitet. Noch kein sauberer Launcher-Release-Commit und kein technischer Release-Go.

Bitte den konkreten Quellumfang, Backend-Vertrag und Installer-Kandidaten unabhängig prüfen. Bei Korrekturen weiter innerhalb dieses Meilensteins arbeiten; keine Veröffentlichung, solange die echten Stabilitäts- und Signierungsgates offen sind. Der Nutzer hat den späteren geprüften Commit/Push/Pre-Release bereits autorisiert; keine neue pauschale Release-Erlaubnis verlangen.

REVIEW BEREIT – Windows Playtest (Integration/Installer-Kandidat)
