# Noch erforderliche Freigaben und Nachweise

Für den vorhandenen Browserlogin benötigt der Entwickler eine eigene Microsoft-
Desktop-App für persönliche Konten mit öffentlicher Client-ID und Redirect
`http://localhost/callback`. Die aktuelle Microsoft-Dokumentation nennt einen
Azure-/Entra-Tenant mit Registrierungsberechtigung als Voraussetzung. Zusätzlich
muss der Minecraft-/Xbox-API-Zugang der App live geprüft bzw. freigegeben sein.
Ein Client-Secret gehört nicht in diese Desktop-App.

Die bestehende Implementierung verwendet PKCE und hält Tokens im Speicher.
13 lokale Fixturetests bestehen; es gibt keine echte Microsoft-Sitzung und
keine Abnahme am zweiten Rechner. Der jetzige Offline-Guard blockiert diese
Anmeldung absichtlich. Eine gesonderte kontrollierte Online-Startkette und ein
Test mit eigener lizenzierter Java-Edition-Sitzung auf beiden Rechnern fehlen.
Eine Multiplayer-ZIP ist daher nicht fertig.

Für eine weitergebbare Einheit müssen Minecraft-/OptiFine-/Assetrechte und der
konkrete Loader-/Instanzvertrag geklärt sein. Die volle unminimierte Assembly
ist kein nachgewiesenes Mod-JAR. Keine Accounts, Tokens oder Saves ins Paket;
kein Testtoken als Multiplayerauth. Der vollständige Clientzweig bleibt lokal.

Die öffentliche PR #6 enthält nur sieben eigene Tools/Dokumente. Sie wurde
als Draft angelegt und inzwischen auf GitHub zusammengeführt (Merge-Commit
d5a277de779f6cf69768a2900e299c9af5810e1a). Dieser Agent hat keinen Merge
ausgelöst. Die lokalen Minecraft-/MenuShader-Korrekturen bleiben außerhalb der
öffentlichen PR und wurden nicht hochgeladen.

Für Cleanup werden vollständige konkrete Pfade und die einzelnen Backup-/
Restorebelege extern zur Prüfung vorgelegt. Sieben Pfade bleiben wegen des
bestätigten Privatdaten-Kopierverbots geschützt. Die Dateibaumarchive der 18
übrigen Pfade hängen für Git-Wiederherstellung weiter vom geschützten gemeinsamen
Gitbestand ab; eine unabhängige Historienkopie ist nicht erlaubt. Keine automatische
weitere Löschung, kein --force/clean/prune und keine Branchlöschung. Die
ausdrücklich freigegebenen 18 alten Worktrees sind nach erneuter Integritätsprüfung
entfernt; die sieben geschützten Pfade, der ursprüngliche dirty Hauptcheckout
und fremde Launcherprojekte bleiben erhalten.

SCF-S01, H1-06 und der bekannte C09-Disable-Auslauf bleiben zurückgestellt.
Scaffold darf im ersten Alpha-Test nicht aktiviert werden. Ein erfolgreicher
Build, MainMenu oder öffentlicher Tool-Push hebt diese Grenzen nicht auf.
