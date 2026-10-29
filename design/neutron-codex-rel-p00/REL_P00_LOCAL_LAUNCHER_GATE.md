# REL-P00 — Lokaler Client und Launcher

Stand: 28.10.2026. **Kein vollständiger Clientstart; keine LOCAL_PLAYABLE_ALPHA-Freigabe.**
Der Nutzer antwortete auf die notwendige aktive Launcher-Anmeldung:
„Hab den Minecraft Launcher nicht installiert“.
Das ist der konkrete Umgebungsblocker; es wurden keine Tokens angefordert,
gelesen, kopiert, geloggt oder fingiert. Der Agent installiert keinen Launcher.

| Gate | Status | Tatsächliche Prüfung und Grenze |
|---|---|---|
| B1 Build | PASS | Java 8, `mvn -o package`, 565/0/0/3, drei lokale JARs |
| B1 ausführbarer JAR-Entrypoint | FAIL | Assembly: fehlender Launchwrapper; Shade/dünnes JAR: fehlendes Main-Class-Attribut; alle Proben Exit 1 |
| B1 Abhängigkeiten | PASS für inventarisierte Dateien | 63 Mavenartefakte gehasht; gezieltes JNA/OSHI-Pakettest je großem JAR bestanden; vollständige dynamische Laufzeit bleibt offen |
| B2 vollständiger Java8-/Display-/Clientstart | BLOCKED | Offizieller Launcher und aktive reguläre Anmeldung fehlen; kein Ersatz durch test-source Start oder Pbuffer |
| B3 offizieller Launcher / Neutron-Version-JSON | NOT_RUN | B2 ist nicht bestanden; keine eigene JSON, Installation oder Profiländerung |
| B4 Multiplayer | NOT_RUN | Kein eigener/erlaubter Testserver mit zwei regulären Konten für diesen Auftrag eingerichtet/bestätigt |
| C installierbares Testerpaket | BLOCKED | Start-/Server-/Rechtegates nicht bestanden |

## Vorhandene Runtime und erforderliche Pfade

Die vorhandene Standardinstallation enthält `versions/1.8.9/1.8.9.json` und
Client-JAR. Client-SHA1 entspricht der vorhandenen offiziellen Metadatenangabe
`3870888a6c3d349d3771a3e9d16c9bf5e076b908`.
Der Index `1.8` stimmt mit `f6ad102bcaa53b1a58358f16e376d548d44933ec` überein;
734/734 Assetobjekte und 35/35 unter Windows ausgewählte Library-/Native-Dateien
haben die erwarteten Hashes. Es wurden nur Versions-/Library-/Assetdateien
gelesen, keine Launcher-Konto- oder Profildateien.

Die JSON verlangt Java 8 (`jre-legacy`) und
`net.minecraft.client.main.Main`. Eine unveränderte Vanilla-JSON berücksichtigt
Neutrons geänderte Mavenabhängigkeiten nicht. Weder Downloads noch URLs oder
Hashes wurden erfunden. Die Dateien wurden nicht überschrieben.
Der vorhandene Bestand ist kein Nachweis einer laufenden Launcherinstallation
oder rechtmäßigen aktiven Session.

`F:\neutron-private-playtest` existierte nicht und wurde wegen der Blockade
nicht angelegt. Für einen späteren autorisierten Test sind getrennt zu prüfen:
GameDir, Prozessarbeitsverzeichnis, `user.home`, Java 8, natives-Windows-64,
Assetindex/Assets, Classpath und offizielle Anmeldung. Bestehende Saves und
Accounts dürfen nicht übernommen werden. Eine isolierte Configumstellung
wurde nicht in Produktionscode eingebaut.

## Nicht ausgeführte Akzeptanzfälle

MainMenu, Welt neu anlegen/laden, ClickGUI öffnen, echter OS-Fensterstart,
Resize/Fullscreen, Verbindung, Respawn, Weltwechsel, Disconnect und regulärer
Client-Exit: **NOT_RUN**. Auch OptiFine L5, NoShader/Shaderpack und Performance
sind nicht abgenommen. Historische VIS-Pbufferbilder ersetzen diese Fälle nicht.

Vanilla-Kontrolle, Neutron ohne Module, danach separat Aura, Scaffold,
Backtrack, LagRange und Queue-/Lifecycleprüfung auf erlaubtem 1.8.9-Server
mit zwei regulären Konten: **NOT_RUN**. Kein behaupteter Treffer-, Placement-,
Anticheat- oder Servererfolg. Paketproben endeten bereits vor Spiel-/Authstart.

Belege: [Runtime-Matrix](LOCAL_RUNTIME_GATE.md),
[installierte Dateien](F:/neutron-rel-p00-evidence-20261028/installed-runtime-inventory.json),
[JAR-Proben und Manifeste](F:/neutron-rel-p00-evidence-20261028/jar-audit.json),
[gezielte Paketabhängigkeitstests](F:/neutron-rel-p00-evidence-20261028/packaged-dependency-probes.json).
