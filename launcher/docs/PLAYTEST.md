# Nächstes Lieferziel: stabiler Windows-Playtest

Verbindliche Nutzerpriorität vom 28. Oktober 2026: Einen releasebaren Windows-Installer des Launchers erstellen und auf GitHub veröffentlichen, damit Nutzer und Kollege den Client testen können — **nur bei ausreichender Stabilität**.

Dieses Dokument aktualisiert den Auftrag. Es behauptet weder, dass der aktuelle Mock-Prototyp veröffentlichbar ist, noch dass ein Installer oder die echte Hintergrundintegration bereits existiert.

## Umfang und Autorisierung

Nach Abschluss/Review des laufenden Abschnitts 3 folgt als nächster Meilenstein Windows x64: echte Hintergrundintegration aus Abschnitt 4 und Windows-Paketierung aus Abschnitt 5. Ziel ist ein GitHub-**Pre-Release** in `YoungJibbit95/Neutron-Client` samt funktionierendem Installer, nicht die Veröffentlichung einer reinen UI-Demo.

Der Nutzer autorisiert dafür notwendige projektbezogene Commits/Pushes, Release-Tag und Pre-Release mit Installer. Das frühere pauschale Veröffentlichungsverbot gilt für diese geprüfte Playtest-Auslieferung nicht mehr. Kein erneutes pauschales Nutzer-Okay für den bereits autorisierten Release; der Review-Chat muss dennoch vor Veröffentlichung nach unabhängiger Prüfung technischen Release-Go erteilen.

Keine fremden Änderungen, Spielstände, persönlichen Logs/Token, Zugangsdaten oder parallelen Client-Arbeiten veröffentlichen. Keine automatische Issue-Erstellung. Kein Force-Push. Linux/macOS bleiben langfristiger Umfang und werden bis zu echten Laufnachweisen nicht als unterstützt beworben.

## Sichere Quellbasis

Vor produktiven Änderungen oder Veröffentlichung den aktuellen Remote-main und den lokalen Stand neu lesen und vergleichen. Die historischen Abschnitt-2-IDs sind nur Baseline: lokaler HEAD `eeddb7da58978f04fad95ad8042175b7ad848d6a`, damals gelesener Remote-main `1bc6c364b58a4eb86faf4cc9aab6e15153837cd9`. Keine Aktualität oder Veröffentlichungsreife dieser IDs unterstellen.

Ein isolierter Checkout/Worktree mit konkreter, prüfbarer Änderungsliste und Commit-Liste verhindert einen ungeprüften Workspace-Push. Nur freigegebene Launcher-/Startänderungen übernehmen; die umfangreichen persönlichen `test_run/`-Änderungen und andere Chats bleiben ausgeschlossen. Diagnose-/Review-Evidenz mit lokalen Pfaden oder Hardwaredaten ist kein Installer- oder Release-Asset. Vor dem Push Inhalt und Diff der tatsächlich vorgesehenen Commits prüfen.

Der spätere Spieler benötigt weder globales Java/Maven/Git noch Python. Die Python-Werkzeuge aus Abschnitt 2 bleiben Entwicklerfixtures; sie sind kein Ersatz für integriertes Setup und Build. Renderer erhalten keine Shell-Schnittstelle. Maven-Abhängigkeiten, Herkunft/Integrität und unveränderliche Build-Manifeste müssen weiter abgesichert werden.

## Stabilitäts- und Freigabefolge

1. Echte Windows-Hintergrundintegration implementieren: lokal verwaltetes Java-8-JDK/Maven, GitHub-Quellarchiv auf konkretem Commit, Asset-/Native-Provisionierung, produktiver Clientstart, Einstellungen, Logs und Prozesse.
2. Windows-x64-Installer bauen und real installieren/starten/deinstallieren, ohne Nutzerdaten zu verlieren.
3. Updatefehler, Abbruch, Rollback und Offline-Start einer vorhandenen Version tatsächlich prüfen. Relevante UI-/Settings-/Crash-/Logexport-Pfade schließen. Keine bekannten Blocker in diesem Ablauf.
4. Signierung und Herausgeber-Zugang klären. Ungeklärte Signierung bleibt vor öffentlicher Auslieferung ein Gate; Pre-Release ist keine Umgehung. SmartScreen-/Signierungsstatus ehrlich dokumentieren.
5. Installer-SHA256, genaue Version und Commit, konkrete Release Notes und bekannte Einschränkungen bereitstellen.
6. Beim Review-Chat anhalten. Nach unabhängiger Prüfung und technischem Release-Go die autorisierten isolierten Commits/Pushes/Tag und das GitHub-Pre-Release mit dem geprüften Installer durchführen.

Die verbindliche Abnahmematrix steht unter W1–W7 in [GATES.md](GATES.md).

## Erneute menschliche Windows-Testfreigabe

Der Nutzer hat am 28. Oktober 2026 die frühere Escape-Sperre ausdrücklich für die reale Windows-Abnahme von Kandidat 0.1.0-playtest.3 aufgehoben: Installation/Deinstallation, Launcher-/Clientstart sowie Settings-, Offline-, Crash-, Schließ- und Exporttests sind autorisiert. Diese Freigabe ist kein bestandener Test. Tatsächliche Ergebnisse werden separat dokumentiert. Der Nutzer hat die Computersteuerung später erneut mit physischem Escape gestoppt. Seitdem keine weitere Desktop-/Browser-/Installer-/Clientsteuerung; die neue Buildfehler-Meldung autorisiert nicht automatisch eine Fortsetzung dieser Interaktionen. CLI-Analyse, Korrektur und nichtinteraktive Tests laufen weiter.

Für die Signierung wählte der Nutzer ein vorhandenes Zertifikat oder einen Signierdienst. In CurrentUser/My und LocalMachine/My wurde bisher kein Codesignaturzertifikat gefunden; passende Signier-Umgebungsvariablennamen fehlen. Der Nutzer kennt derzeit keinen Speicherort/Dienst. Die tatsächliche Signiermöglichkeit bleibt offen; keine Umstellung auf unsignierte öffentliche Auslieferung.

## Aktueller Stand

R3 ist vom Review-Chat technisch akzeptiert, ohne Release-Go. Die reale R3-Abnahme belegt bisher Installation auf F:, einen vorhandenen Uninstaller, 76 bytegleiche App-Dateien, installierten Launcher, echte Ersteinrichtung, vier Themes in DE/EN, 125 % UI-Skalierung und den Schließdialog mit Rückkehr zur laufenden Aufgabe. Der ursprüngliche Überschreibfehler am Uninstaller ist noch nicht sicher erklärt. Keine Deinstallation oder tatsächlicher Clientstart ist bestanden. Ein Exportdialog blieb beim erneuten physischen Escape-Stopp offen.

Der Nutzer lieferte danach den echten Buildfehler des aktuellen Remote-Commits `d5a277de779f6cf69768a2900e299c9af5810e1a`: neun zusätzliche Test-JVMs konnten ihren Heap nicht reservieren. R4 setzt begrenzte Build-/Test-Heaps, ohne Tests zu deaktivieren oder Spieleinstellungen zu ändern. Der erneute Testlauf desselben Quellverzeichnisses besteht mit 400 Tests, 0 Fehlern und 3 vorher vorhandenen Überspringungen. Weitere R4-Produktions-/Paketnachweise stehen im aktuellen Windows-Review.

Signiermöglichkeit und technische Endabnahme bleiben offen. Kein Release-Commit, Push, Tag oder Pre-Release. Der stille NSIS-WriteUninstaller-Helfer gehört zum Paketbau; ein R4-Paketbau ist kein R4-Installationsnachweis.
