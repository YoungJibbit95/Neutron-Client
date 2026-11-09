# R5 — sichtbares Spielfenster unter Windows

Der Nutzer autorisiert ausdrücklich Fix, neue Installation und Installer-Link; die dafür notwendigen Windows-Tests sind erneut erlaubt. Der unsichtbare Vorher-Test wurde auf Nutzerwunsch gezielt beendet; anschließend kein fremder Spielprozess angefasst.

Die R4-Prozessfunktion setzt für alle Starts `windowsHide: true`. [libuv](https://github.com/libuv/libuv/blob/v1.x/src/win/process.c) setzt dafür `STARTF_USESHOWWINDOW` mit `SW_HIDE`, auch für GUI-Fenster. Reale isolierte Reproduktion: Client erreicht ModuleManager/Menü, Musik läuft, aber kein targetierbares Spiel-Fenster. Der alte eigene PID-/Prozessbaum wurde beendet.

Enge Korrektur: `ProcessOptions` und der interne `PreparedClient` erlauben einen expliziten Sichtbarkeitswert; Standard bleibt versteckt. Nur der produktiv vorbereitete Clientplan setzt `windowsHide: false`. Nach Java-8-Prüfung wird das benachbarte `javaw.exe` verwendet, damit kein zusätzliches Konsolenfenster erscheint. Fehlendes javaw meldet einen erklärten Fehler. Verwaltete Toolchain hashprüft bereits javaw. Argumente, Pipes, Shell-Verbot, Prozessgruppe, Lease/Stop-/Retry-/Shutdown-Verhalten bleiben bestehen. Kein neues Renderer-/IPC-Feld. Build-Heaps aus R4 unverändert.

Realer Nachher-Test über den kompilierten produktiven Service: javaw-PID 193628, Client initialisiert, Fenster `Neutron | Dev 2.2 by youngjibbit for NeutronPlayer` über Sky entdeckt. Der Nutzer bestätigt ausdrücklich: „Es ist ein fenster da“. Screenshot des ersten Captures zeigte wegen Vordergrundwechsel den Chat und wurde als ungültiger Beleg gelöscht; kein eigener visueller Screenshot-Pass daraus abgeleitet. Danach Client über Service gestoppt und eigene vorherige Zustandsdatei wiederhergestellt.

Beide Typechecks/Produktionsbuild und **121/121 Launcher-Tests** bestanden. Zusätzliche Lifecycle-Regression prüft die Weitergabe des sichtbaren Clientplans sowie erfolgreichen Start/Stop. Keine Client- oder Adapterdatei geändert; vollständiger Mavenlauf aus R4 ist Baseline, kein neuer Mavenlauf behauptet.

Hashgeprüfter R4-zu-R5-Diff und Quellinventar: `evidence/windows-playtest-r5/r4-to-r5.patch`, `source-inventory.json`. Test-/Fensterbelege: `tests.json`, `window-regression.json`. Installer wird noch im neuen eigenen F:-Output erzeugt; dessen exakter Hash/Paketvergleich und tatsächliches Update der vorhandenen Installation folgen als getrennte Belege.

Bitte den konkreten engen Fix unabhängig technisch prüfen. Benutzer hat lokale Installation/Tests ausdrücklich beauftragt; keine öffentliche Veröffentlichung, kein Release-Go und keine Signierfreigabe daraus ableiten. Aktueller Kandidat ist 0.1.0-playtest.5. Signierzugang bleibt ungeklärt.

## Fertiger Kandidat und reale Installation

Installer `F:\Neutron Launcher Playtest 20261028\Installer Candidate R5\Neutron-Launcher-0.1.0-playtest.5-Windows-x64.exe`, SHA256 `a4d1b789ae208f3a1909123cd08ec0be239f8d7671a424c22967b435fc1e720f`, 112.464.891 Bytes, NotSigned. Frischer NSIS-Build Exit 0; 25 kompilierte und 3 Adapterdateien bytegleich. Alte vorhandene Installation unter `F:\Neutron Launcher Playtest 20261028\Installed R3` real auf Version 0.1.0-playtest.5 aktualisiert, Exit 0. Alle 76 installierten App-Dateien bytegleich zum R5-Paket. Vorhandene Benutzer-state.json blieb während Installation und Start/Stop bytegleich; keine persönlichen Profile ersetzt.

Erster Appstart zeigte zunächst eine leere Ansicht. Nach Schließen und Neustart mit Electron-Dateilogging renderte der Launcher normal; keine weitere Codekorrektur daran durchgeführt. Ursache/Startlatenz nicht abschließend diagnostiziert, kein allgemeiner Startup-Perf-Pass behauptet.

Aus der tatsächlich installierten App über „Neutron starten“ den vorhandenen d5a277d-Client gestartet. Javaw-PID 186772 mit gehashtem Lease, sichtbares vollständiges Hauptmenü direkt gesehen und screenshotbelegt (`installed-client-menu.png`). Echte stdout-Ausgabe im Launcher sichtbar. Über „Client beenden“ gestoppt: PID beendet, Lease entfernt, UI wieder „Spielbereit“ (`installed-launcher-after-stop.png`). Client bleibt beendet; Launcher bleibt bereit.

Der enge Codefix ist unabhängig technisch akzeptiert. Reviewer wies auf möglichen Konsoleneffekt des neuen Node-Testhelfers hin: Testadapter beobachtet nun die reale false-Weitergabe, setzt ausschließlich für den tatsächlichen CLI-Testhelper hidden=true. Keine Produktcodeänderung. Finaler Lifecycle-Testlauf 10/10 und beide Typechecks erneut bestanden; zuvor vollständiger Lauf 121/121. Finales Quellinventar/Diff aktualisiert. Öffentliche Release-Freigabe und Signierung weiterhin offen; keine Commits/Pushes/Releases.

## Abschließende unabhängige Paketprüfung

Review-Chat bestätigt unabhängig Installer-SHA256, Paketversion 5, 25 kompilierte und 3 Adapterdateien sowie alle 76 installierten App-Dateien. Benutzer-state.json unverändert bestätigt, finale Lifecycle-Tests 10/10 erneut bestanden, beide Screenshots angesehen. Lokaler Fensterfix/Installer darf als installiert und eng geprüft übergeben werden. Kein öffentlicher Release-Go.

Die einmal leere Ansicht ist ein **offener Stabilitätsbefund**, keine bloß abgenommene Performanceeinschränkung. Vor öffentlichem Release normale Starts ohne zusätzliches Logging reproduzierbar prüfen und gegebenenfalls korrigieren. Der noch sichtbare Fenstertitel „Neutron Launcher · Prototype“ ist vor öffentlicher Auslieferung konsistent zu benennen. Vollständige Endabnahme und Signierung bleiben offen.
