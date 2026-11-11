# Research-Infrastruktur: Audit und Entwicklungsbericht

Datum: 11. November 2026. Geprüfter Ausgangscommit:
[`fadf9711201bd8a6781f1a68e9d36db189e1d9b6`](https://github.com/YoungJibbit95/Neutron-Client/tree/fadf9711201bd8a6781f1a68e9d36db189e1d9b6).
Arbeitsbranch: `codex/research-recording-replay-20261111`.

Dieser Durchgang erweitert die vorhandene Forschungsinfrastruktur um versionierte
Aufnahmen, strikten Import, deterministische Offline-Auswertung, eine Headless-CLI
und Regressionstests. Er umfasst einen Architektur- und Provenienzaudit. Er ist
kein vollständiger Mechanik-Audit sämtlicher Gameplay-Modes und enthält keine
neuen Intave-/Polar-Umgehungen oder Messungen auf einem Anti-Cheat-Testserver.

## A. Codebase Audit

### Aktueller Bestand

Die historische Projektzusammenstellung vom 27. Oktober bezog sich auf
`c19312037dc76e7d4d02fa34fe5f28760bbd6e90`. Ihre Zahlen sind keine Messung des
heutigen Repositories. Am oben genannten Ausgangscommit wurden 414 Java-Dateien
unter `src/main/java/me/youngjibbit`, 432 Java-Testdateien, 34 Event-Java-Dateien
und neun GitHub-Workflow-Dateien erfasst. Dateizahlen sind keine Testfallzahlen.

Die Registry enthält im Quellbestand 51 mit `@ModuleInfo` annotierte Modulklassen.
Für die drei angefragten Hauptkategorien ergibt die strukturelle Inventur:

| Bereich | Annotierte Module | Weitere Java-Dateien im Bereich |
| --- | --- | --- |
| Movement | `Speed`, `Sprint` | Keine |
| Combat | `AimAssist`, `AntiBot`, `Aura`, `MoreKB`, `Velocity` | `DelayedPacketThread` |
| Exploit | `Backtrack`, `LagRange`, `MinibloxFix`, `Tickbase` | Keine |

Diese Tabelle inventarisiert Klassen. Sie bescheinigt weder die Vollständigkeit
eines Mode-Audits noch Wirksamkeit, Erkennungsverhalten oder Laufzeitregistrierung.

### Geprüfte Verträge

| Bereich | Befund und Konsequenz für die Integration |
| --- | --- |
| [ModuleManager](../src/main/java/me/youngjibbit/module/ModuleManager.java) | Sortierte Reflection-Discovery, gemeinsame Indizes, Abweisung doppelter Namen/IDs und unveränderliche externe Collections. Die Erweiterung benötigt kein neues Modul und keine zusätzliche Registry. |
| [Module](../src/main/java/me/youngjibbit/module/Module.java) | Alpine-Subscription über Enable/Disable, protokollierte Zustandswechsel und gebündelte Setting-Callbacks. Aufnahme und Settings bleiben im bestehenden Lifecycle. |
| [Value](../src/main/java/me/youngjibbit/value/Value.java), [ComboValue](../src/main/java/me/youngjibbit/value/impl/ComboValue.java) | Gemeinsame Setting-/Callback-Verträge. Die vorhandenen Profil-, Quellen- und Szenarioeinstellungen werden weiterverwendet. |
| [ConfigManager](../src/main/java/me/youngjibbit/config/ConfigManager.java) | Versioniertes Format, Werteprüfung und gebündelte Anwendung. Dieser PR führt kein zweites Konfigurationssystem ein. |
| [EventPacket](../src/main/java/me/youngjibbit/event/packet/EventPacket.java) | Ausgehendes POST steht für lokale Transportannahme. Der Bericht darf daraus keine Remote-Bestätigung berechnen. |
| [TickEvent](../src/main/java/me/youngjibbit/event/packet/TickEvent.java) | Das Beobachtungshook liegt vor abgeschlossener Spielersimulation; `steps=-1` bleibt eine unbekannte Schrittzahl. |
| [NetworkResearch](../src/main/java/me/youngjibbit/module/impl/client/NetworkResearch.java) | Bereits vorhandener Beobachter mit begrenztem Trace, Kontextvergleich und Resets. Neue Funktionen verwenden ausschließlich abgeschlossene skalare Snapshots. |
| [NetworkResearchSimulation](../src/main/java/me/youngjibbit/core/diagnostics/NetworkResearchSimulation.java) | Virtueller Metadaten-Scheduler. Die CLI nutzt die vier vorhandenen Szenarien ohne Paket- oder Spielzustandsausführung. |

### Bestätigte Lücken des ursprünglichen Exports

1. JSONL enthielt nur erhaltene Ereignisse, aber keine Kapazität, Formatversion,
   Überschreibungs-, Zurückweisungs- oder Aufnahmefehlerzähler.
2. Ein Paketklassenname identifiziert kein einzelnes Paket. Eine Zuordnung gleicher
   Namen würde unbelegte individuelle Latenzen erzeugen.
3. `SERVER_EVIDENCE` bezeichnete einen vom Aufrufer eingetragenen Verweis. Die
   Existenz und Authentizität des referenzierten Serverlogs wurde nicht geprüft.
4. Lokale monotone Zeitwerte erlauben ohne zusätzliche Synchronisierung keine
   direkte Zuordnung zu UTC oder zu einem anderen Prozess.
5. Disable und Kontextwechsel löschen den bisherigen Trace. Ein gewünschter
   Versuch muss vor diesem Lifecycle-Schritt exportiert werden.

Das neue Recording behebt die erste Lücke. Analyzer und Dokumentation machen die
übrigen Grenzen ausdrücklich sichtbar. Der alte JSONL-Export bleibt kompatibel.

### Verbleibende allgemeine Architekturprüfpunkte

`Module.setToggled` hat keinen allgemeinen Rollback für eine Exception im
Enable-/Disable-Hook. Config-Anwendung ist bei Fehlern ebenfalls nicht vollständig
transaktional; der vorhandene Schutz pausiert automatisches Speichern. Dies sind
statisch begründete Prüfpunkte, keine hier nachgestellten Laufzeitfehler. Sie werden
in diesem begrenzten Infrastruktur-PR nicht pauschal refaktoriert.

## B. Open-Source-Provenienz und Versionen

Geprüft wurden Repository-Metadaten, Lizenzhinweise und Builddeklarationen, jeweils
am angegebenen Commit. Die Angaben sind keine Ergebnisse eines Fremdprojekt-Builds.
Es wurde kein fremder Implementierungscode in diesen PR übernommen.

| Öffentliche Referenz | Fixierter Commit | Deklarierte Spiel-/Java-Basis | Lizenzbefund |
| --- | --- | --- | --- |
| FDPClient, `main` | `07d03078b3fe12ac7278c678f56d0e7f607fbec2` | Minecraft 1.8.9 / Forge; Java-Ziel 8 | GPLv3-Hauptlizenz; spezifische Hinweise beibehalten |
| Vibe, `main` | `4d37238c29384e2015e6c65b3eeb8a0991df3d50` | Minecraft 1.8.9; Build-JDK 21, Java-Ziel/Laufzeit 8 | Hauptlizenz GPLv3, dokumentierte Komponenten unter AGPL-3.0-only und weitere Komponentenlizenzen |
| LiquidBounce, `legacy` | `03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e` | Minecraft 1.8.9 / Forge; Java-/Kotlin-Ziel 8 | GPLv3 mit projektspezifischen Hinweisen |
| LiquidBounce, `nextgen` | `121f283f30a526ca535f322b14f9fadf4adfbb25` | Minecraft 26.3 / Fabric; JDK 25 | GPLv3; gelesene Builddateien enthalten Version-3-oder-später-Hinweise |

Primärbelege:

- FDPClient: [Build](https://github.com/SkidderMC/FDPClient/blob/07d03078b3fe12ac7278c678f56d0e7f607fbec2/build.gradle),
  [Lizenz](https://github.com/SkidderMC/FDPClient/blob/07d03078b3fe12ac7278c678f56d0e7f607fbec2/LICENSE).
- Vibe: [Build](https://github.com/SkidderClub/Vibe/blob/4d37238c29384e2015e6c65b3eeb8a0991df3d50/build.gradle),
  [README](https://github.com/SkidderClub/Vibe/blob/4d37238c29384e2015e6c65b3eeb8a0991df3d50/README.md),
  [Komponentenhinweise](https://github.com/SkidderClub/Vibe/blob/4d37238c29384e2015e6c65b3eeb8a0991df3d50/LICENSES/THIRD_PARTY_NOTICES.md).
- LiquidBounce Legacy: [Build](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/build.gradle),
  [Lizenz](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/LICENSE).
- LiquidBounce Nextgen: [Versionskatalog](https://github.com/CCBlueX/LiquidBounce/blob/121f283f30a526ca535f322b14f9fadf4adfbb25/gradle/libs.versions.toml),
  [Build](https://github.com/CCBlueX/LiquidBounce/blob/121f283f30a526ca535f322b14f9fadf4adfbb25/build.gradle.kts).

Die zusätzlich angegebene private Referenz DemisePrivate wurde nur für
Metadaten/Provenienz gesichtet. Private Quellinhalte werden in diesem öffentlichen
PR nicht veröffentlicht. Die Referenzen sind keine Zuschreibung der neu geschriebenen
Recording-, Analyzer- und Harness-Klassen an diese Projekte.

Ein GitHub-Lizenzlabel ersetzt keine Prüfung der konkret übernommenen Dateien.
Vibes eigene Komponentenhinweise dokumentieren beispielsweise abweichende Lizenzen
und offene Herkunftsnachweise. Dieser PR trifft keine pauschale Freigabeentscheidung
für solche Übernahmen. Neutrons bestehende Lizenz-/Packaging-Fragen bleiben ebenfalls
außerhalb des Umfangs.

## C. Implementierte Forschungswerkzeuge

- `NetworkResearchRecording`: unveränderlicher Snapshot mit Formatversion und
  Verlustzählern, strikter begrenzter Streaming-Codec und Quellenvalidierung.
- `NetworkResearchAnalysis`: reproduzierbare Zähler, Tickverteilungen, Queue-Maxima,
  beobachtete Positionsabstände und ausdrücklich gekennzeichnete Datenqualitätsprobleme.
- `NetworkResearchHarness`: eigenständige CLI für die vorhandenen synthetischen
  Szenarien, lokale Datei-/stdin-Analyse und automatisierbare Qualitätsprüfung.
- `.research report` und `.research bundle`: kleine Anbindung an das vorhandene
  Modul; keine zusätzlichen Eventhooks oder Gameplay-Schreibzugriffe.
- Regressionstests für Parser, Statistik, deterministische Wiederholung,
  CLI-Prozessverhalten und bestehende Aufnahme-/Kontextgrenzen.

Bedienung, Schema und Grenzen stehen in der
[Harness-Dokumentation](NETWORK-RESEARCH-HARNESS.md).

## D. Anti-Cheat-Evidenz und defensive Schlussfolgerungen

Es wurden keine Intave-/Polar-Versionen oder Serverkonfigurationen ausgeführt.
Erkennungsverhalten, False-Positive-Raten und konkrete Validierungslücken dieser
Produkte sind daher in diesem Durchgang **nicht gemessen**.

Die umgesetzten Schutzmaßnahmen betreffen die Verlässlichkeit der Forschung:
fehlende Belege bleiben unbekannt; lokale Transportannahme wird nicht zur
Serverbestätigung; Korrekturen werden nicht zu Flags; unvollständige Aufnahmen
tragen ihre Verluste mit; Herkunft und Rungrenzen werden geprüft. Ein erfolgreiches
synthetisches Szenario belegt lediglich den beschriebenen Offline-Datenvertrag.

## E. Validierung

Lokale Toolchain: Temurin 8u482-b08 und Maven 3.9.15, Archive gegen die
veröffentlichten Prüfsummen geprüft. Gson 2.15.0 und JUnit 4.13.2 sind bereits
Projektabhängigkeiten; es wurden keine Dependencies ergänzt.

| Prüfung | Ergebnis |
| --- | --- |
| Separat kompilierter Headless-Kern, ohne Minecraft-Klassen im Klassenpfad | 48 Tests erfolgreich, 0 Fehler/Skips |
| Research-Suites im vollständigen Maven-Projekt | 58 Tests erfolgreich, 0 Fehler/Skips; darin 10 Embedded-Transport-/Lifecycle-Tests |
| `mvn -B -ntp -Dtest=NetworkResearch*Test package` | Erfolgreich: dieselben 58 Tests und beide Paketierungswege |
| CLI aus der erzeugten Assembly-JAR | `simulate pause tickbase` und anschließendes `verify -` erfolgreich: 61 Einträge, 11 Tickintervalle, p95 300 ms, keine Qualitätsprobleme, Serverergebnis unbekannt |
| Vollständiger Paketbuild mit `-DskipTests` | Erfolgreich; dies allein ist kein bestandener Testlauf |
| Gesamte Regression unter Linux | 2036 Tests erfasst, 1 Failure, 275 Errors, 3 Skips; kein PASS |
| Windows-Gesamtregression und Repository-Checks | Ergebnisse am veröffentlichten Aufgabencommit im PR prüfen |

Die 275 Linux-Errors stammen aus vorhandenen Tests mit festem `java.exe`-Pfad.
Die weitere Failure ist `FontAtlasMetricsTest.unicodeAndSupplementaryCharactersHaveVisibleAdvances`.
Diese außerhalb der Änderung liegenden Tests wurden nicht verändert oder als
bestanden dargestellt. Die drei Skips entsprechen den bestehenden nativen Opt-ins.
Die neue Änderung ergänzt insgesamt 45 Testmethoden zu den 13 bereits bestehenden
Research-Testmethoden. Maßgeblich für die Gesamtfreigabe bleibt das Windows-CI-Gate.

Der unabhängige Code-Review fand zwei CLI-I/O-Fälle: reparierendes UTF-8-Decoding
über stdin und unterdrückte stdout-Schreibfehler. Sie wurden mit explizitem
Decoderverhalten, Streamfehlerprüfung und separaten JVM-Prozesstests adressiert.
Es werden keine Testergebnisse für einen gestarteten Spielclient behauptet.

## F. GitHub-Übergabe und Arbeitsumgebung

Die Änderung wird als [PR #178](https://github.com/YoungJibbit95/Neutron-Client/pull/178)
von `codex/research-recording-replay-20261111` nach `main` übergeben.
Der Implementierungscommit ist
[`7d7494c615456a22707bf81b7c0662071ffb63f9`](https://github.com/YoungJibbit95/Neutron-Client/commit/7d7494c615456a22707bf81b7c0662071ffb63f9).
Aktueller Check- und Merge-Status sind an diesem PR nachvollziehbar.
Die maßgeblichen Checks sind laut aktivem Ruleset
`Java 8 / Windows regression`, `Repository policy` und `Dependency review`;
offene Reviewthreads müssen geklärt sein. Ein Merge benötigt erfüllte Projektgates.

Der in `AGENTS.md` dokumentierte Windows-Hauptcheckout war in dieser Sitzung nicht
zugänglich. Verwendet wurde ein einzelner flacher Quellcheckout in der verfügbaren
Linux-Arbeitsumgebung, ohne zusätzliche Worktrees, Welten, Konten oder Spielordner.
Für den Build wurden die versionierten Quellen/Ressourcen und eine geprüfte Java-8-
Toolchain benötigt. Der Windows-Hauptcheckout und sein Branch wurden nicht verändert.
