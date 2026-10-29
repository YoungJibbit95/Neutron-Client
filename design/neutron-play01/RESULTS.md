# PLAY-01: realer lokaler Start, keine Multiplayerfreigabe

28.10.2026, Europe/Berlin. Basis ist der lokal verifizierte saubere Integrations-
Commit `cd200f41b824e4f90fc8d3347ac0ea9f8ddfd2a0`. Eigener Worktree:
`F:\neutron-play01-real-window`, Branch `codex/neutron-play01-real-window`.
SOURCE_REUSED: keine erneuten Cherry-Picks, keine Übernahme aus dem dirty
Hauptcheckout und keine Änderung des gemeinsamen origin.

Der neue `Neutron-Dev-Start.cmd` führt tatsächlich Java 8 x64 aus. Die direkt
aufgerufene unminimierte Assembly erhält genau eine feste lokale Offlinefixture,
getrennte game/cwd/home-Verzeichnisse, geprüfte Windows64-Natives und lesend
referenzierte 1.8.9-Assets. 734 Asseteinträge wurden geprüft. Persönliche Saves,
Accounts und Launcherprofile wurden nicht importiert.

Ein natives OS-Fenster und das Neutron-MainMenu wurden sichtbar beobachtet.
Der erste Singleplayer-Klick zeigte einen echten Shader-Ressourcenfehler:
`Tenacity/shaders/vertex.vsh` statt des case-sensitiven JAR-Eintrags
`Tenacity/Shaders/vertex.vsh`. Die minimale Pfadkorrektur ist enthalten. Danach
wurden Weltwahl, Welterstellung und Terrain-Ladebildschirm erreicht.

Der folgende Weltstart scheiterte an der zu strengen Guard-Sperre des internen
Windows-Java-8-Selector-Pipes. Das Harness erlaubt jetzt ausschließlich den
Bootstrap-JDK-Aufruf `sun.nio.ch.PipeImpl$Initializer$LoopbackConnector` auf
Loopback mit ephemerem Listener. Ein normaler Loopback-Socket bleibt gesperrt.
17 Verweigerungsproben sowie ein echter positiver Selector-Test bestanden.
Der nachfolgende Clientstart erreichte das MainMenu und endete regulär mit
Java-Exit 0; Welt-/ClickGUI-Retest und ein vollständiges Quit/Restart-Paar sind
wegen der beendeten Computersteuerung nicht qualifiziert. Kein PLAY-01-Gesamt-PASS.

Die Minecraft-Startmeldung mit `Session.getSessionID()` wurde entfernt: Dieser
Wert enthält den Access-Token. Die fertige Minecraft-Klasse referenziert den
Aufruf nicht mehr. Es wurde keine echte Kontositzung gestartet oder geloggt.

Finale Java-8-Prüfung: `mvn -o clean test` und `mvn -o package` jeweils
678 Tests, 0 Failures, 0 Errors, 3 bestehende Opt-in-Skips aus 111 frischen XMLs.
Die 13 vorhandenen Browserauth-Tests verwenden lokale HTTP-Fixtures; sie sind
kein Microsoft-/Minecraft-Live-PASS. Fünf echte negative CMD-Fälle verwerfen
geschützte Wurzeln und falsche JAR-/Helper-/Native-/Klassenhashes vor Java-Start.
Die Scaffold-Placement-Fälle waren grün; die historische Flake-Ursache ist damit
nicht aufgeklärt. ABI bleibt PARTIAL. SCF-S01/H1-06 bleiben unverändert im Freeze;
Scaffold wurde im laufenden Client als false beobachtet.

Assembly SHA256:
`d65467e2d7e0790d4fa8c16c13141041b956ab97ba0aaab339ce5ad36ef79779`.
Die lokale Assembly enthält Spiel-/OptiFine-Bestand und wurde weder als
Kollegenpaket noch als GitHub-Release hochgeladen.

Der zunächst ausdrücklich genehmigte reine Tool-Export
`b7dbc16ddf5c157427bd6e951a0e73f1e259b1a3` wurde auf
`codex/neutron-dev-tools-public` gepusht und per Remote-SHA bestätigt. Dieser
parentlose Export ist kein mergefähiger Clientzweig. Auf den späteren PR-Auftrag
wurde deshalb eine eigene PR direkt auf dem aktuellen öffentlichen main
`1bc6c364b58a4eb86faf4cc9aab6e15153837cd9` erstellt:
[PR #6](https://github.com/YoungJibbit95/Neutron-Client/pull/6), Head
`cd1479af65d1eea791f9481e2e3b4f1e4017de43`, wurde als Draft angelegt und
zwischenzeitlich auf GitHub zusammengeführt: Merge-Commit
`d5a277de779f6cf69768a2900e299c9af5810e1a`. Dieser Agent hat keinen Merge
ausgelöst. Die PR enthält ausschließlich sieben
Tool-/Dokumentationsdateien einschließlich Selector-Korrektur. Keine neuen
Spielquellen, Assets, Natives, Binärpakete oder lokale private Historie wurden
hochgeladen. Die beiden lokalen Produktkorrekturen sind nicht Teil der PR.

Cleanup: Alle 25 Kandidaten wurden erneut nach HEAD, Status, Reachability,
ignored/untracked und Metadatengröße geprüft. Ein erster Privatdatenklassifikator
zählte Quellpakete namens account fälschlich mit; das ist im korrigierten Inventar
offengelegt. Fünf CORE-Bäume mit test_run/logs und zwei Bäume mit Kontodatei-
Fixtures bleiben vom Kopieren ausgeschlossen. Für die übrigen 18 wurden
vollständige Dateibaumarchive samt SHA256, extrahiertem Restore und Git-
Statusvergleich erstellt. Nach ausdrücklicher Freigabe genau dieser 18 Pfade
und erneuter Prüfung aller Dateien gegen die Archive wurden sie mit
`git worktree remove` ohne --force entfernt. Alle 18 Kommandos waren erfolgreich;
die Branchrefs blieben unverändert. Der gemeinsame Gitobjektbestand wurde wegen
privater Historienblobs nicht kopiert und bleibt eine ausdrücklich akzeptierte
Recovery-Abhängigkeit. Die sieben geschützten Pfade wurden nicht entfernt.

Die drei ausschließlich von diesem Auftrag angelegten Testwurzeln wurden nach
Marker-/Pfadprüfung ohne Kopie in den bestehenden Belegordner verschoben:
`F:\Neutron-PLAY01-20261028\runtime-tests\{setup-incomplete,first-window,current}`.
Der geprüfte lokale Einstieg ist jetzt
`F:\Neutron-PLAY01-20261028\Neutron-Dev-Start.cmd`; Probe nach Umzug: Exit 0.
Historische Prozesslogs behalten ihre ursprünglichen Pfadangaben.

Externe Belege liegen unter `F:\Neutron-PLAY01-20261028\evidence`: finale
13-/14-Maven-Summaries und XMLs, Prozess-/Fensterlogs, Screenshots, negative
CMD-Tests, Selector-Probe, verschobener-Einstieg-Probe, PR-/Push-Belege,
Cleanup-Dateimanifeste sowie abschließender Reviewpatch/SHA256/Reverse-Check.
