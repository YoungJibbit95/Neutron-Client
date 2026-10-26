# Startkorrekturen und Audit-Welle 2

Basis: `1b7a10f608e20f64ea598f855d0771758189faaa`. Arbeitsbranch: `codex/startup-audit-wave-2`.

## Startpfad

- Twitch wird nicht mehr initialisiert. `DisabledStream` bedient die verbleibenden Vanilla-Aufrufe ohne SDK-Start, DLL-Ladevorgang oder Anmeldung. Der Streaming-Dialog meldet ausdrücklich die Deaktivierung. Alte Java-SDK-Typen bleiben vorerst als Kompatibilitätsabhängigkeit erhalten; dies ist keine vollständige Entfernung aller Streamingklassen.
- SLF4J 1.7 erhält den zur vorhandenen Log4j-Version passenden `log4j-slf4j-impl`-Adapter. Meldungen und Exceptions werden weitergeleitet statt verworfen. Logging bleibt aktiv.
- In der lokalen, nicht versionierten IntelliJ-Konfiguration `Launch Pulsar` wurden ausschließlich die vier falsch platzierten JVM-Flags von `PROGRAM_PARAMETERS` nach `VM_PARAMETERS` verschoben: `-Xms2G`, `-Xmx4G`, `-XX:ReservedCodeCacheSize=512M`, `-XX:+UseConcMarkSweepGC`. Sonstige Startargumente bleiben erhalten.
- Die ungenutzte Discord-RPC-Abhängigkeit mit eingebettetem altem JNA wurde entfernt. `jna` und `jna-platform` entsprechen mit 5.13.0 dem bestehenden OSHI 6.4.5. CPU-Erkennungsfehler werden künftig geloggt.
- Die Paketprüfung deckte zusätzlich auf, dass Shade-Minimierung reflektiv geladene Log4j-Plugins und den nativen JNA-Callbackpfad entfernt. Explizite Beibehaltung dieser beiden Bibliotheksgruppen erhält diese Laufzeitverträge. Das allgemeine Releaseformat und der fehlende Assembly-Einstiegspunkt sind damit nicht gelöst.

## Auditpakete

| Befund | Umsetzung | Grenze |
| --- | --- | --- |
| BLD-02 | Einheitliches JNA und echte CPU-Abfrage | Kein vollständiger Clientstart aus dem Releaseartefakt |
| MOD-02 | Scaffold-Sprintentscheidung vor eigenem AutoJump und C0B; Zustandswechsel nach PRE und vor Versand geprüft | Reguläre Vanilla-Physik wird nicht in einen zusätzlichen Planungsdurchlauf verschoben |
| MOD-06 | Beide Sprintstartbedingungen verbrauchen NoSlow-Freigabe | Eingabestärke und Vanilla-Grenzen bleiben erhalten |
| MC-03 | Ein aktiver CustomEntityRenderer wird beim Bootstrap erzeugt und als Reloadlistener registriert | Kein vollständiger nativer Fenster-/F3+T-Test |
| MC-01 | Event3D nach Transluzenz, vor finalem Handpass; echte Augenmatrizen und begrenzte GL-Zustandsbereiche | OptiFine hand0 kann bereits Tiefe beigetragen haben; keine Zusage reiner Welttiefe |
| MC-02 | Vanilla-Namen werden nur bei tatsächlichem Ersatz desselben Spielernamens unterdrückt | Keine neuen Tier-/Mob-Nametag-Designs |

Scaffold behält eine konservative Sprintunterdrückung für den aktuellen Schritt, wenn ein zunächst gültiger Plan erst durch Tower ungültig wird: Ein bereits ausgeführter Sprung lässt sich nicht zurücknehmen. Ein folgender Schritt ohne Plan gibt diese Entscheidung frei. C0B-Caches ändern sich erst nach lokaler Annahme der eigenen Aktion. Diese Annahme ist keine Serverbestätigung.

Der neue 3D-Hook überspringt Shadow-Pässe und trägt die jeweilige normale/anaglyphe Pass-ID. Die vorhandenen ESP-/Backtrack-Verbraucher bleiben durch Wände sichtbar; die spätere Platzierung verhindert, dass transparente Weltflächen diese Overlays nachträglich überzeichnen. Shaderpack-Betrieb bleibt gesondert abzunehmen.

## Prüfungen und Grenzen

Mit Temurin 8u482 und Maven 3.9.15 bestand `mvn -o clean package` mit aktivierten nativen Prüfungen: **367 Tests, keine Fehler oder Skips**, zusätzlich **51 Checks in isolierten Kind-JVMs**. Die beiden nativen WorldOverlay-/Reloadprogramme liefen innerhalb dieses Testsatzes.

Danach wurden ausschließlich Testdetails korrigiert: Die annotierte Nametags-Fixture lag im produktiv gescannten Modul-Namespace und führte im zusätzlichen UI-Programm zu einem doppelten Modulnamen. Sie liegt jetzt außerhalb dieses Namespace. Außerdem wird die tatsächliche JNA-Versionskonstante reflektiv gelesen, statt ihren möglicherweise vom Compiler eingesetzten Literalwert zu vergleichen. Die jeweiligen gezielten Nachprüfungen bestanden.

Die abschließenden **sechs isolierten Nametag-Checks**, **neun separaten Renderprogramme** (iOS-Capture, GlowESP, ClickGUI, Arraylist, kombinierte HUDs, Ressourcen, Verbraucher, Motion und native Pfade) sowie **beide isolierten JAR-Abhängigkeitsproben** bestanden. Gezielte Vorher-/Nachher-Prüfungen reproduzierten den JNA-Linkagefehler, Sprint-/NoSlow-Fehler und fehlerhafte Namensunterdrückung.

Protokolle: `F:/Pulsar-Recovery/analysis/audit-wave-2-final-build.log`, `audit-wave-2-label-final.log`, `audit-wave-2-extra-checks.log`, `audit-wave-2-dependency-probe/` sowie `target/audit-wave-2-validation/results.json`. Der feste Quellstand dieser Welle ist `4d7564a5b535ece8c37ea247b0e5c33bacf66096`.

Alle 149 zu Beginn vorhandenen fremden Änderungspfade blieben im Hashvergleich unverändert. Die oben genannte Änderung der ignorierten IntelliJ-Startkonfiguration ist absichtlich und separat. Keine persönliche Spielsession wurde gestartet oder beendet.

Der native Renderer-Test konstruiert den echten Renderer mit synthetischen Ressourcen und prüft wiederholte ShaderGroup-Zielersetzung, Freigabe alter GPU-Ziele und eine einzige Lightmap. Seine ShaderGroup hat keine Shaderprogramm-Pässe. Er belegt weder Shaderprogramm-Reload noch einen vollständigen Spielstart.

Die Paketprobe läuft mit Testprobe plus jeweils einem einzelnen Client-JAR, ohne Maven-Abhängigkeitsclasspath. Sie prüft CPU-Erkennung, eindeutiges JNA, Logger-Anbindung und Log4j-Formatierungsplugins. Sie ist ausdrücklich keine Startabnahme des Clients.

## Noch offen

Aus dem ursprünglichen Audit verbleiben UI-03 (Accent-Priorität), UI-04 (ArmorHUD-Alpha/Reduced Motion), BLD-01 (Releasevertrag), MOD-07 (Timer-Besitz), MOD-08 (funktionslose Altoptionen) und PERF-01 (wiederholte GL-Abfragen). Diese Themen sind nicht durch die Startkorrekturen erledigt. Reale Spiel-, Shaderpack- und Fensterwechselabnahme bleibt zusätzlich erforderlich.

## Technische Quellen

- [Apache Log4j: SLF4J-Anbindung](https://logging.apache.org/log4j/2.12.x/log4j-slf4j-impl/): SLF4J an den vorhandenen Log4j-Backend anschließen.
- [Apache Maven Shade: explizite Beibehaltung bei Minimierung](https://maven.apache.org/plugins-archives/maven-shade-plugin-3.3.0/examples/includes-excludes.html): ausdrücklich eingeschlossene Klassen bleiben erhalten.
- Der lokal aufgelöste OSHI-6.4.5-Parent legt JNA 5.13.0 fest; keine pauschale Bibliotheksaktualisierung.
