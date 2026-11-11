# Network Research: Recording und Offline-Harness

Der Harness macht bestehende skalare Beobachtungen reproduzierbar auswertbar. Eine
Aufnahme enthält neben ihren Einträgen auch Herkunft, Run, Szenario und Verlustzähler.
Der Importer prüft diese Angaben; der Analyzer berechnet Statistiken ausschließlich
aus den erhaltenen Daten. Die CLI erzeugt die vorhandenen synthetischen Szenarien
und analysiert gespeicherte Aufnahmen ohne Minecraft-Start.

Die Implementierung ergänzt `NetworkResearch`, `NetworkResearchTrace` und
`NetworkResearchSimulation`. Die bestehenden Profile Backtrack, Lagrange und Tickbase
sowie die Szenarien Baseline, Jitter, Burst und Pause bleiben erhalten. Dieser PR
fügt keine neuen Gameplay-Modes hinzu und enthält keine Anti-Cheat-Implementierung.

## Verwendung im Client

| Befehl | Ergebnis |
| --- | --- |
| `.research on` | Aktiviert das vorhandene Modul mit seinen aktuellen Einstellungen. |
| `.research status` | Zeigt den bisherigen Aufnahmestatus. |
| `.research report` | Wertet einen unveränderlichen Snapshot aus: Herkunft, Samplezahl, gültige Tickintervalle, p95, Datenqualität und unbekanntes Serverergebnis. |
| `.research bundle` | Schreibt eine versionierte JSON-Aufnahme einschließlich Verlustzählern. |
| `.research export` | Schreibt das bisherige JSONL-Format; dessen Format bleibt unverändert. |
| `.research off` | Deaktiviert das Modul und setzt gemäß bestehendem Lifecycle die Aufnahme zurück. |

Dateien entstehen bei explizitem Export unter `pulsar/research/network-<UUID>.json`
beziehungsweise `.jsonl` im Spielverzeichnis. Vor dem Deaktivieren exportieren:
Disable, Profil-/Quellenwechsel und Session-/Weltgrenzen starten eine leere Aufnahme.
Bereits exportierte Dateien und zuvor erstellte Recording-Objekte bleiben erhalten.
Ein deaktiviertes Modul exportiert einen leeren Beobachtungs-Run, auch wenn die
Einstellung `Source` noch auf `Simulation` steht.

## Headless ausführen

Die Projekt-Toolchain ist Java 8; für die gemeinsame Abnahme gilt Temurin 8u482.
Ein normaler Paketbuild stellt die vorhandene Assembly mit ihren Abhängigkeiten her:

```sh
mvn -B -ntp package
java -cp target/Neutron-dev-jar-with-dependencies.jar me.youngjibbit.core.diagnostics.NetworkResearchHarness simulate pause tickbase > pause.json
java -cp target/Neutron-dev-jar-with-dependencies.jar me.youngjibbit.core.diagnostics.NetworkResearchHarness analyze pause.json
java -cp target/Neutron-dev-jar-with-dependencies.jar me.youngjibbit.core.diagnostics.NetworkResearchHarness verify pause.json
```

Die CLI lädt keinen Client, öffnet keine Verbindung und führt importierte Ereignisse
nicht aus. Ihre fünf Kernklassen benötigen ausschließlich Java 8 und die bereits im
Projekt verwendete Gson-Version. Sie lassen sich auch separat ohne Minecraft-Klassen
kompilieren und starten.

```text
simulate <baseline|jitter|burst|pause> <backtrack|lagrange|tickbase> [capacity]
analyze <recording.json|->
verify <recording.json|->
```

`simulate` schreibt eine Aufnahme nach stdout; die optionale Kapazität liegt zwischen
1 und 4096, Standard ist 512. Wiederholte Aufrufe mit identischen Parametern erzeugen
identische Inhalte. `analyze` und `verify` lesen eine explizite lokale Datei oder mit
`-` stdin. Beide schreiben denselben JSON-Bericht. Diagnosen gehen nach stderr.

| Exitcode | Bedeutung |
| --- | --- |
| 0 | Aufruf erfolgreich; bei `verify` wurden keine Aufnahmequalitätsprobleme gefunden. |
| 2 | Ungültige Argumente, ungültige Aufnahme oder I/O-Fehler. |
| 3 | `verify` hat eine lesbare Aufnahme mit Datenqualitätsproblemen ausgewertet. |

Ein erfolgreicher `verify`-Aufruf bestätigt Datenqualität gemäß diesem Schema. Er ist
keine Aussage über Serverakzeptanz, Erkennung oder Anti-Cheat-Kompatibilität.

## Aufnahmeformat, Version 1

Der feste Formatname ist `neutron-network-research`, `schemaVersion` ist `1`.
Alle folgenden Felder sind Pflichtfelder; unbekannte oder doppelte Felder werden
abgelehnt, ebenso zusätzliche Inhalte nach dem JSON-Dokument.

| Feld | Vertrag |
| --- | --- |
| `run` | Nichtnegative, lokale Run-Nummer; keine global eindeutige Session-ID. |
| `mode` | Eines der bestehenden Beobachtungsprofile. |
| `origin` | `CLIENT_OBSERVATION` oder `SIMULATION`. |
| `capacity` | Kapazität des Aufnahme-Ringpuffers, 1 bis 4096. |
| `appended` | Zahl aller erfolgreich angehängten Ereignisse des Runs. |
| `overwritten` | Aus dem Anfang des Ringpuffers verdrängte Ereignisse. |
| `rejected` | Vom Trace abgelehnte Messungen. |
| `captureFailures` | Vom vorhandenen Beobachter abgefangene Aufnahmefehler. |
| `scenario` | Bei Simulation einer der vier vorhandenen Szenarionamen, sonst leer. |
| `simulationRejected` | Ablehnungen des Offline-Schedulers; bei Beobachtung immer 0. |
| `entries` | Unveränderlicher Snapshot der vorhandenen skalaren Einträge in Anfügereihenfolge. |

Es gilt `appended = overwritten + entries.size`. Sequenznummern müssen ohne Lücken
von `overwritten + 1` bis `appended` verlaufen. Alle Einträge müssen zu Run und Profil
gehören. Quellen dürfen sich ausschließlich für ausdrückliche `SERVER_FLAG`-Marker
einer Beobachtungsaufnahme unterscheiden. Solche Marker benötigen
`SERVER_EVIDENCE`, eine Check-Bezeichnung und einen nichtleeren Belegverweis.
Simulationsaufnahmen können keine Server-Evidenz behaupten.

Der Parser liest begrenzt mit Gson-Streaming und ohne reflektive Objekterzeugung.
Er akzeptiert höchstens 4096 Einträge, 160 Zeichen pro Textfeld und insgesamt
16.777.216 Eingabezeichen. Die Gesamtgrenze berücksichtigt maximal escapte Texte
einer vollständig gefüllten Aufnahme. Ganzzahlfelder akzeptieren keine Strings,
Dezimalzahlen oder Exponentialschreibweise. Koordinaten müssen endlich sein.
Fehler nennen das betroffene Feld beziehungsweise den JSON-Pfad. Reader und Writer
bleiben Eigentum ihres Aufrufers und werden vom Codec nicht geschlossen.

Das alte JSONL-Format enthält keinen vollständigen Aufnahmevertrag. Es wird deshalb
nicht stillschweigend mit erfundenen Nullwerten für fehlende Verlustzähler importiert.
Für neue auswertbare Aufnahmen `.research bundle` verwenden.

## Auswertung und ihre Grenzen

- Ereignisse werden je `Kind` gezählt. Gleiche Paketbezeichnungen begründen keine
  Identitätszuordnung. Empfang, lokale Anwendung und lokales Senden ergeben daher
  keine gemessene Paketlatenz oder Serverbestätigung.
- Tickintervalle benötigen zwei erhaltene Tick-Beobachtungen. Ein aus dem Puffer
  verdrängter Vorgänger wird nicht rekonstruiert. Baselines und unpaarige Intervalle
  werden separat gezählt; `steps=-1` bleibt eine unbekannte Schrittzahl.
- p50 und p95 verwenden die Nearest-Rank-Methode, der Mittelwert die gültigen
  erhaltenen Intervalle. Ohne Messwerte stehen die Statistiken auf JSON `null`.
- Zeitstempel liegen in der lokalen monotonen Nanosekunden-Domäne, nicht in UTC.
  Kurze Intervalle über den vorzeichenbehafteten `nanoTime`-Überlauf bleiben gültig.
  Zeitrückgänge unterbrechen die Paarbildung; eine unplausible oder überlaufende
  Gesamtdauer wird nicht als gültige Dauer ausgegeben.
- Queue-Maxima beschreiben beobachtete skalare Queue-Snapshots. `queueSamples=0`
  bedeutet, dass keine gültige Queue-Messung vorliegt.
- Positionsabstände vergleichen die beiden Koordinatensätze eines erhaltenen
  Entity-Snapshots. Sie sind kein aktueller Serverzustand und kein Reach-Maß.
  Nicht endlich berechenbare Abstände werden mit Qualitätsmeldung ausgeschlossen.
- Verlustzähler und Qualitätsprobleme stehen im Bericht. Statistiken einer
  abgeschnittenen Aufnahme beziehen sich ausdrücklich auf deren erhaltenen Ausschnitt.
- Korrekturen erzeugen keine Server-Flags. Manuell gesetzte Belegverweise werden
  als `REFERENCES_PRESENT_UNVERIFIED` bezeichnet; die referenzierten Inhalte werden
  nicht geöffnet. `serverOutcome` bleibt `UNKNOWN`.

## Tests und Reproduktion

```sh
mvn -B -ntp -Dtest=NetworkResearchTraceTest,NetworkResearchRecordingTest,NetworkResearchAnalysisTest,NetworkResearchHarnessTest,NetworkResearchHookTest test
```

Die Tests decken Aufnahme-/Bericht-Roundtrips, alle zwölf Kombinationen aus vier
Szenarien und drei Profilen, JSON-Grenzen, Herkunftsregeln, Zeitüberläufe,
verdrängte Baselines, Fehlerzähler und CLI-Exitcodes ab. Die vorhandene
Embedded-Transport-Fixture prüft zusätzlich Export-/Weltgrenzen, Settingwechsel,
Disable und Aufnahmefehler bei unveränderter Paketidentität und unveränderten Queues.

Für echte Vergleichsläufe werden zusätzlich der Client-Commit, Server- und
Plugin-Versionen, die freigegebene Konfiguration, unabhängige Serverbelege und eine
dokumentierte Zeitzuordnung benötigt. Diese Informationen sind nicht automatisch
in einer skalaren Client-Aufnahme enthalten. Dieser Entwicklungsdurchgang hat keine
Erkennungsergebnisse für Intave oder Polar erzeugt.
