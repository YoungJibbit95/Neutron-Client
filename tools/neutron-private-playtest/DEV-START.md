# Lokaler Java-8-Developer-Start

`Neutron-Dev-Start.cmd` startet den echten Java-Prozess. Er ist ausschließlich
eine **OFFLINE_DEV_ONLY**-Singleplayer-Testkette, keine Multiplayer-Anmeldung.
`Neutron-Start.cmd` behält seinen bisherigen Check-only-Vertrag.

Voraussetzungen: Windows, Java-8-x64-JDK, PowerShell 5.1, Git, frisch gebaute
unminimierte Assembly und eigene lokale 1.8.9-Assets. Keine Downloads, Installer,
Accountimporte, System-Firewallregeln oder globalen Umgebungsänderungen.

Im eigenen Checkout mit Java 8 offline bauen und den SHA256 der Assembly prüfen.
Anschließend eine noch nicht existierende externe Testwurzel vorbereiten:

```powershell
.\prepare-neutron-dev.ps1 -IsolationRoot 'F:\Neutron Neuer Test' `
  -AssetsDir '<eigene Minecraft-Installation>\assets' `
  -VersionMetadata '<eigene Minecraft-Installation>\versions\1.8.9\1.8.9.json' `
  -ExpectedJarSha256 '<geprüfter SHA256>'
```

Danach aus CMD oder PowerShell:

```bat
Neutron-Dev-Start.cmd -ConfigPath "F:\Neutron Neuer Test\dev-config.json" -ProbeOnly
Neutron-Dev-Start.cmd -ConfigPath "F:\Neutron Neuer Test\dev-config.json"
```

Die Vorbereitung prüft den Index und alle referenzierten Assetobjekte gegen die
installierten Metadaten. Nur vier geprüfte AMD64-Natives werden aus dem eigenen
Checkout in die neue Testwurzel kopiert. Assets bleiben lesend referenziert.
Java, Assembly, Helperquellen, Helperklassen, Natives und Assetindex werden mit
Hashes gebunden. Eine Änderung erfordert erneute Prüfung und Vorbereitung.

Die Laufzeit verwendet getrennte `game`, `cwd`, `home`, Temp- und AppData-Pfade.
Alle Git-Worktrees sowie das echte Nutzerhome und Minecraft-Verzeichnis sind
geschützt. Junctions/Symlinks im Startpfad werden abgewiesen. Das Harness setzt
den festen Testnamen `NeutronDevOffline` und `accessToken=0`; keine Sessionwerte
werden aus vorhandenen Dateien übernommen. Zusätzliche Spielargumente werden
nicht akzeptiert. Scaffold muss OFF bleiben und wird im Clientthread beobachtet.

Vor dem Laden der Spielklassen installiert das Harness einen Java-8-
SecurityManager. Er verweigert Java-DNS-/Socket-/HTTP-/HTTPS-Zugriffe,
Kindprozesse, den Austausch des Guards sowie Dateizugriffe außerhalb der
erlaubten Runtime-/Testpfade. 17 Verweigerungsproben laufen vor jedem Spielstart.
Der Windows-Java-8-Selector benötigt einen internen Loopback-Kanal: Nur der
Bootstrap-JDK-Aufruf `sun.nio.ch.PipeImpl$Initializer$LoopbackConnector` darf
dazu einen ephemeren Listener und lokale Loopback-Verbindungen öffnen.
Eine normale Loopback-Socketverbindung bleibt gesperrt; das Öffnen eines echten
Selectors wird zusätzlich positiv geprüft. Dies ist
ein Guard für die geprüften Java-Netzpfade einschließlich OptiFine, keine
Kernel-Sandbox gegen bösartigen nativen Code. Die vier JNI-Dateien und die
ausgewählte Assembly bleiben deshalb gepinnte vertrauenswürdige Eingaben.

Der in der Assembly geerbte Netty-JAR-Index enthält einen alten Nachbarnamen.
Das Harness erlaubt dessen Existenzprüfung, bricht aber ab, falls eine solche
zweite JAR tatsächlich vorhanden ist. Es wird keine zusätzliche Bibliotheksliste
an die Assembly angehängt.

Unter `logs` entstehen getrennte UTF-8-stdout/stderr-Dateien und ein JSON-Beleg
mit Java-PID, Argumenten der festen Offlinefixture, OS-Fensterhandle und Exitcode.
Exit 0 beweist nur ein reguläres Prozessende; die tatsächliche Sichtprüfung von
MainMenu, Welt und GUI muss zusätzlich erfolgen. Vorprüfungsfehler liefern 24,
Wrapperfehler 20/21; Spiel-Exitcodes werden weitergereicht.

Für reguläres Multiplayer ist ein anderer, rechtmäßiger Session-/Loaderpfad
erforderlich. Der vorhandene Browserlogin verlangt eine eigene registrierte
Desktop-App; der Developer-Guard lässt ihn bewusst nicht passieren. Die Assembly
enthält Minecraft-/OptiFine-Spielbestand und ist keine freigegebene Kollegen-
oder GitHub-Distribution.
