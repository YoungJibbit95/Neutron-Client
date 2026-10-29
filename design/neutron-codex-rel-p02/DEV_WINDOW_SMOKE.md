# REL-P02 — Developer-Fenstersmoke

28.10.2026, Europe/Berlin. **NOT_RUN; Voraussetzungen zusätzlich BLOCKED.**
Es gab kein Nutzer-Opt-in für einen sichtbaren Developer-Prozess. `Start.main`
wurde nicht ausgeführt. Fenster, MainMenu, GUI, Resize, Quit, Neustart und
Multiplayer sind nicht getestet. Der offizielle Minecraft Launcher wird auf
Nutzerwunsch weder vorausgesetzt noch installiert.

## Tatsächlicher Starter und Isolationsvertrag

Der unveränderte `src/test/java/Start.java` setzt `org.lwjgl.librarypath` auf
`../test_natives/windows`, relativ zum Prozess-CWD. Danach ruft er den vorhandenen
Main mit `version=MavenMCP`, dem vorhandenen Testwert `accessToken=0`,
`assetsDir=assets`, `assetIndex=1.8` und leeren Userproperties auf. Das ist eine
Offline-Entwicklungsfixture, keine authentifizierte Sitzung. Sie gehört nicht
zum freigegebenen Assembly-Entrypoint und wird nicht zu einem Launcher umgebaut.

`Main` erzeugt erst nach der Argumentprüfung `Session` und `GameConfiguration`.
`FolderInformation(File, File, File, String)` übernimmt GameDir, Resourcepacks,
Assets und Index. Der Pfadvertrag des laufenden Clients erfordert drei getrennte
Verzeichnisse:

| Consumer | Vorgeschlagener neuer Pfad | Warum erforderlich |
|---|---|---|
| Minecraft/GameConfiguration, ConfigManager, ThemeRuntime | `F:\neutron-rel-p02-dev-window\game` | GameDir; darunter eigene Configs und Ressourcen |
| Prozess-CWD, Pulsar/AccountManager | `F:\neutron-rel-p02-dev-window\cwd` | Pulsar bildet hier `Neutron/alts` |
| JVM `user.home`, Pulsar | `F:\neutron-rel-p02-dev-window\home` | Pulsar bildet hier `MinecraftAccounts` |

Der Check-only-Plan prüft den noch nicht existierenden Root gegen sämtliche
39 vorher registrierten Worktrees, den eigenen Worktree, das echte Benutzerhome
und MinecraftHome: **42 disjunkte Schutzwurzeln**. Weder Root noch Kinder wurden
angelegt. Beleg: [isolation-plan.json](F:/neutron-rel-p02-evidence/isolation-plan.json).

Ein späterer ausdrücklich autorisierter Aufbau müsste den relativen Nativepfad
und `cwd/assets` mit einem überprüften, nur lesend verwendeten lokalen Bestand
auflösen. Verzeichnisreferenzen/Junctions wären dabei neue externe Writes mit
eigenem konkreten Plan. Es wurden keine Links angelegt, keine DLLs/Assets und
keine Saves/Configs/Accounts kopiert. `--assetsDir` einfach erneut anzuhängen
ist kein geprüfter Override der bestehenden jopt-simple-Einzeloption.
`--gameDir` ist im Starter noch nicht gesetzt und muss getrennt gebunden werden.

## Offline braucht eine tatsächliche Netzwerkgrenze

Der Name Offline-Starter verhindert keine Hintergrundverbindung. Der gelesene
Callgraph lautet `Config.initDisplay -> checkInitialized ->
startVersionCheckThread -> VersionCheckThread.run -> HttpURLConnection.connect`.
Der bestehende OptiFine-Versionscheck kann bereits beim Displaystart HTTP
versuchen; `snooperEnabled=false` verhindert diese Verbindung nicht, sondern
steuert lediglich zusätzliche Requestheader. Der Prozess wurde nicht gestartet.

Für einen späteren Offline-Fenstertest fehlt deshalb neben dem freiwilligen
Opt-in eine überprüfte äußere Egress-Sperre für genau diesen Prozess bzw. eine
isolierte Entwicklungsumgebung. Deren Verhalten einschließlich etwaiger
Startup-Exceptions müsste aufgezeichnet werden. Es wurden keine Firewallregeln,
JVM-Sicherheitspolicies, Game-/OptiFine-Quellen oder Starterargumente verändert.
Ein bloß ausgelassenes `--server` ist kein Nachweis dieser Grenze.

Auch hashkorrekte lokale Assets belegen keine Eigentums-/Entitlementfreigabe.
Diese Voraussetzung bleibt vom Nutzer zu bestätigen; es wurden keine
Konten-/Token-/Launcherprofildateien eingesehen.

## Ausgeführte Prüfungen und offene Grenzen

| Prüfung | Status | Nachweis |
|---|---|---|
| Starter-/Main-/GameConfiguration-Vertrag | PASS, statisch | Tatsächliche unveränderte Java-Quellen; Hashbindung extern |
| Drei getrennte künftige Pfade | PASS, Check-only | `isolation-plan.json`; keine GUI-Verzeichnisse erzeugt |
| `java -jar` ohne Authargumente | MAIN_ARGUMENTS_ONLY | Reale `MissingRequiredOptionException` für `accessToken`, `version`; Exit 1 |
| Headless ABI-/Paket-/JNI-Prüfungen | Separat in RESULTS | Externe CWD/Home-Sandboxes; kein Client-/Display-/Audiogerät |
| Developer-Opt-in / legitimer Datenbestand / Egress-Isolation | NOT_RUN / BLOCKED | Keine Freigabe bzw. kein verifizierter Aufbau |
| Fenster/MainMenu/Quit, GUI/Resize/Neustart | NOT_RUN | Keine visuelle oder Prozess-Exit-Evidenz einer Spielsitzung |
| Authentifizierte Sitzung / Multiplayer | NOT_RUN | Nicht beauftragt und nicht versucht |

Die Headless-JAR-Proben benutzen eigene externe CWD/Home-Verzeichnisse unter
dem Evidenzordner und erreichen keinen Minecraft-Konstruktor. Die Timer-/DLL-
Checks erzeugen keinen Displaykontext. Ihre Erfolge ersetzen kein Fenster.
Es gibt keinen Startup-Spielstack, weil kein Spielprozess gestartet wurde;
die unverändert dokumentierten ABI-/Reflexionsdiagnosen stehen in der Matrix.

**LOCAL_ONLY / DISTRIBUTION_BLOCKED. STOPP nach REL-P02.**
