# REL-P01 — Lokaler Einstieg und Launcher-Smoke

28.10.2026, Europe/Berlin. **B0 BLOCKED; B1/B2/B3/B4 NOT_RUN**.
Kein `LOCAL_CLIENT_SMOKE_PASSED`.

## Aktueller Nutzer- und Installationsbefund

Zu Beginn wurden nur bekannte Win32-Launcherpfade und die AppX-Inventur lesend
geprüft. Dort war kein offizieller Minecraft-Launcher nachweisbar. Keine
Launcher-Konto-/Profildatei, Microsoft-Caches, Saves, Chatlogs oder Prozessargumente
laufender Clients wurden gelesen. Diese Pfadsuche allein wäre kein globaler
Nichtinstallationsbeweis. Der Nutzer bestätigte anschließend ausdrücklich, dass er
den offiziellen Launcher nicht installieren werde und den Fokus auf den lokalen
Launcher wünscht. REL-P01 konzentriert sich deshalb auf den vorhandenen lokalen
Entrypoint und Packagingvertrag; es wurde kein Ersatzlogin oder Auth-Bypass gebaut.

Die vorhandenen technischen Vanilla-1.8.9-Dateien wurden frisch geprüft:
Client und Index entsprechen ihren installierten Größen/SHA1, **734/734** Assets
und **35/35** Windows-Library-/Nativearchive sind korrekt. Java-Metadaten nennen
Java 8 / jre-legacy und direkten Main. Dies beweist weder Eigentum, eine offizielle
Benutzerinstallation noch einen früheren erfolgreichen Vanilla-Start.
Es wurden ausschließlich Versions-/Asset-/Librarymetadaten gelesen.

## Ausgeführt und offen

| Teil | Status | Tatsächlicher Nachweis |
|---|---|---|
| Lokales Java8-Paket | LOCAL_PACKAGING_VERIFIED | Assembly-Main repariert, frische Vollsuite und acht Paket-/JNI-Fälle |
| Lokale Main-Argumentprüfung | PASS, begrenzt | Echter Java8-Exit 1 wegen fehlender Pflichtargumente; kein Token übergeben |
| B0 Nutzer-Vanilla-Voraussetzung | BLOCKED | Nutzer lehnt offizielle Installation ab; kein bestätigter Vanilla-Start |
| B1 Launcher-JSON-Preview | NOT_RUN | B0 fehlt; keine Launcher-JSON und kein inheritsFrom-Schema behauptet |
| B2 Installation/Freigabe | NOT_RUN | Kein konkreter Installationsantrag, kein Opt-in, kein Versions-/Profilwrite |
| B3 Fenster/MainMenu/Welt/GUI | BLOCKED / NOT_RUN | Kein regulärer Clientstart; keine Ersatzbilder oder Pbuffer als Spielbeleg |
| B3 Fokus/Resize/Fullscreen/Shader | NOT_RUN | Keine sichtbare Spielsitzung |
| B3 Shutdown/Exit/Neustart | NOT_RUN | Nur Fehlerexit vor Clientkonstruktion beobachtet |
| B4 Multiplayer/zweites Konto | NOT_RUN | Kein B3, kein autorisierter Server-/Zweikonten-Smoke |

## Der einzige Helfer ist Check-only

`tools/neutron-rel-local/check-runtime.ps1` hat keinen Install-, Start-, Download-
oder Uninstall-Modus. Default und `-Check` prüfen ausschließlich lesend; Exit 2
und JSON-Status BLOCKED bleiben ausdrücklich bestehen, solange vollständige
Native-/Classpath- und Nutzerfreigaben fehlen. Einen grünen Einzelbefund darf man
nicht in eine Startfreigabe umdeuten. Java wird nur mit `-version` ausgeführt;
fehlende oder beschädigte Eingaben bleiben fail-closed. Fehler melden Typ/Zeile,
keinen möglicherweise vertraulichen Inhalt einer Eingabedatei.

Beispiel nach lokalem Packaging, ohne Spielstart:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\neutron-rel-local\check-runtime.ps1 -Check
```

`-ExpectedJarSha256` kann den extern geprüften Artefakthash übernehmen.
`-IsolationRoot` prüft nur einen neuen externen Pfad; es wird nichts angelegt.
Bekannte fremde Profile, bestehende Pfade sowie ein Root innerhalb von Repo,
Benutzerhome oder MinecraftHome bestehen diese Prüfung nicht.
Der Helfer zählt keinen Dateihash als Authnachweis.

Neun externe Negativfälle bestehen mit Exit 2 und dem jeweils erwarteten BLOCKED-
Einzelcheck: fehlende Vanilla, falsches JDK, fehlendes JDK, kaputte JSON,
JAR-Hashabweichung, Vanilla-Hashabweichung, fremder Profilpfad, fehlender Launcher,
fehlendes JAR. Alle Fixturehashes bleiben unverändert. Der Default und ein Aufruf
mit geprüftem JAR-Hash wurden zusätzlich gegen den realen technischen Bestand ausgeführt.

## Isolation und harte Grenze

Die headless Proben verwenden externe CWD/Home-Verzeichnisse unter dem Evidenzordner;
sie erreichen weder Pulsar.init noch einen Welt-/Account-/Config-Ladevorgang.
Ein künftiger ausdrücklich autorisierter Spieltest müsste einen neuen externen
Root mit getrennten `game`, `cwd`, `home`-Kindern verwenden. Grund:
`Pulsar.init` nutzt CWD/Neutron/alts und user.home/MinecraftAccounts;
`ConfigManager` verwendet gameDir/pulsar/configs und ThemeRuntime ebenfalls gameDir.
Nur `--gameDir` genügt nicht. Keine alten Daten übernehmen.
R01-11 bleibt für diese tatsächlich laufenden Consumer BLOCKED.

Kein solches Spielverzeichnis, keine Version und kein Launcherprofil wurden
eingerichtet. Deshalb gibt es auch keinen konkreten zu genehmigenden Installations-
oder Backup/Uninstallplan. Eine spätere B1-Preview müsste zuerst ausschließlich
extern gegen die dann reale Launcherfassung validiert werden; B2 benötigt danach
Dateipfade, Inhalte, SHA256 und explizites Benutzer-Opt-in. Keine weitere Nachfrage
oder Installation in diesem Auftrag. **STOPP nach REL-P01.**

Belege: [installed-runtime-inventory.json](F:/neutron-rel-p01-evidence/installed-runtime-inventory.json), [launcher-detection.json](F:/neutron-rel-p01-evidence/launcher-detection.json),
[preflight-negative-tests.json](F:/neutron-rel-p01-evidence/preflight-negative-tests.json), [preflight-default.json](F:/neutron-rel-p01-evidence/preflight-default.json),
[preflight-reviewed.json](F:/neutron-rel-p01-evidence/preflight-reviewed.json), [isolation-proof.json](F:/neutron-rel-p01-evidence/isolation-proof.json),
[final-jar-audit.json](F:/neutron-rel-p01-evidence/final-jar-audit.json). Keine echte Fenster-/Welt-/Exit-Evidenz vorhanden.
