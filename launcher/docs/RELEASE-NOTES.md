# 0.1.0-playtest.5 — lokaler Windows-Kandidat

R5 korrigiert den unsichtbaren Spielstart unter Windows: Der Client wird über `javaw.exe` mit erlaubtem GUI-Fenster gestartet. Build, Java-Prüfung und Prozessabfrage bleiben versteckte Helfer. Der vorherige Starter setzte `SW_HIDE` auch für das LWJGL-Fenster; dadurch lief das Spiel mit Musik ohne sichtbares Fenster. Der isolierte Vorher-/Nachher-Lauf reproduzierte den Fehler, und der Nutzer bestätigte nach der Korrektur das sichtbare Fenster. Eine vollständige Java-8-Laufzeit muss `javaw.exe` neben dem ausgewählten Java enthalten.

R4 begrenzt den Startheap von Maven auf 64 MiB und den Heap seiner Test-JVMs einschließlich weiterer Java-Kindprozesse auf 32–512 MiB. Der echte R3-Build des Commits `d5a277de779f6cf69768a2900e299c9af5810e1a` scheiterte an neun nicht startenden Test-JVMs (`Could not reserve enough space for object heap`). Die ursprüngliche automatische Java-Grenze betrug auf dem Prüfgerät etwa 8 GiB pro JVM. Tests bleiben aktiviert; Spiel-RAM und bereinigte Spielumgebung werden nicht verändert. Beide Typechecks und 120 Launcher-Tests bestanden. Der erneute vollständige Testlauf im fehlgeschlagenen Quellverzeichnis besteht mit 400 Fällen, 0 Fehlern und 3 vorhandenen Überspringungen.

R3 trennt fehlgeschlagene Terminierung vom tatsächlichen Prozessende. Ein Stop-Fehler erhält PID, Lease und Versions-/Aufgabenschutz; erneutes Stoppen/Abbrechen bleibt möglich. Shutdown meldet Fehler zurück statt Erfolg zu behaupten oder auf dem noch laufenden Prozess unbegrenzt zu warten. Vier weitere Regressionen prüfen verweigerte und hängende Terminierung, Client-/Shutdown-Schutz und weiterlaufende Hintergrundarbeiter. Stand von R3: 119 Tests.

Bereits in R2 korrigiert: Der Clientstart reserviert vor jeder asynchronen Vorbereitung, meldet Spawn-Erfolg/-Fehler ausdrücklich und teilt sich mit Stop/Shutdown denselben Lebenszyklus. Fehler bereinigen einen verspäteten Lease erst nach bestätigtem Prozessende. Ein manueller Verbindungs-/Updatecheck bleibt nach Offline-Nutzung erreichbar. Sechs gezielte Regressionstests ergänzten den Stand von 109 auf 115 Prüfungen.

Noch nicht veröffentlicht. Windows x64 ist der einzige vorgesehene Playtest-Pfad. Linux/macOS sind nicht freigegeben. Keine Aussage über installierte Stabilität aus dem bloßen Paketbau ableiten.

## Enthalten

- Echte lokale Einrichtung von Java 8/Maven und offiziellen Minecraft-Assets/Natives, ohne Python/Git oder globale Entwicklungswerkzeuge beim Spieler.
- GitHub-Commit-Archiv, isolierter Maven-Build, gehashte Versionen, Prüfung vor Aktivierung und Auswahl einer früheren geprüften Version.
- Persistente Start-/Theme-/Spracheinstellungen, validierte IPC, echte Pfadauswahl und Schließaktionen.
- Getrennte echte Ausgabe, begrenzte virtuelle Konsole, rotierende redigierte Dateilogs und Export-/Berichtsvorschau.
- Windows-NSIS-Paketierung mit beibehaltenen Daten bei normaler Deinstallation laut Konfiguration. Noch kein realer Deinstallationsnachweis.

Die ursprünglichen vollständigen Client-Builds verwenden `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`; die aktuelle Speicherregression prüft `d5a277de779f6cf69768a2900e299c9af5810e1a` aus `YoungJibbit95/Neutron-Client`. Enge Bootstrap-/POM-/Ressourcenanpassungen des Launchers sind separat gehasht. Es wurden keine lokalen Combat-/Scaffold-/Clientänderungen übernommen.

## Vor Freigabe offen

R3 wurde auf F: installiert und dessen 76 App-Dateien bytegleich zum Paket geprüft. Installierter Launcher, Ersteinrichtung und vier Themes in DE/EN wurden tatsächlich bedient. Noch offen: Deinstallation, tatsächliches Clientfenster, Client-Settings und Shader-Alias, Offline-Spielstart, Build-and-play, Crash/Neustart, Prozessübernahme, Schließdialoge, Fokus/Scroll/DPI/Themes und echter Datei-/Logexport. Nach erneuter menschlicher Testfreigabe hat der Nutzer die Computersteuerung erneut mit physischem Escape gestoppt. Weitere Interaktionen bleiben angehalten; die Teilnachweise sind keine vollständige UI-/Release-Abnahme.

Der Kandidat ist **nicht signiert**. Herausgeber/Signierung und SmartScreen-Reputation sind ungeklärt und bleiben vor öffentlicher Auslieferung Gate. Kein technischer Release-Go, kein Push/Tag/Pre-Release erfolgt. CI ist vorbereitet, aber mangels freigegebenem Push noch nicht auf GitHub ausgeführt.

## Bekannte Grenzen

- R5 zeigte nach dem lokalen Update beim ersten Launcherstart eine leere Ansicht; nach Neustart funktionierten Oberfläche und Spielstart. Das bleibt ein offener Stabilitätsbefund für die öffentliche Release-Abnahme.
- Änderungen am upstream Build-POM benötigen eine geprüfte Adapteraktualisierung; normale Quelländerungen bei bekanntem POM werden weiterhin auf dem aufgelösten Commit gebaut.
- Unterbrochene einzelne Downloads beginnen erneut; vollständig geprüfte Objekte bleiben im Cache. Kein Byte-Range-Resume.
- Arbeitsverzeichnisse/Versionen bleiben zur Diagnose erhalten. Entfernen aus der Liste gibt aktuell keinen Speicher frei; mindestens 3 GiB frei vor Setup/Build, weiterer Speicher bei mehreren Builds nötig.
- Zusätzliche JVM-Argumente sind auf angegebene GC-Schalter beschränkt. Einige technische Backend-Fehlermeldungen sind Englisch, auch bei deutscher Oberfläche.
- Nach Wiederübernahme eines verwaisten Clientprozesses sind vorhandene Dateilogs verfügbar; dessen neue stdout-Ausgabe lässt sich nicht wieder anbinden. Der tatsächliche Wiederanlauf ist noch nicht geprüft.

Installer-SHA256, Paketinhalt, konkrete Quellen und Prüfergebnisse gehören zum unabhängigen Kandidatenreview. Vor Veröffentlichung müssen sich deren Hashes auf genau den auszuliefernden Installer und einen sauberen Launcher-Commit beziehen.
