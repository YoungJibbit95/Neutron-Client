# Minecraft-Core, Rendering und Shader: Übergabe und Abnahme

Stand: 11. November 2026. Ziel: `YoungJibbit95/Neutron-Client:main`.

**Der Nutzer hat nach der Verbindungsunterbrechung ausdrücklich die Fertigstellung, PR-Erstellung, erforderliche CI-Korrekturen und den regulären Merge nach `main` zum eigenen Testen beauftragt.** Nach erfolgreichem technischem Review und den relevanten CI-Prüfungen wird deshalb regulär gemergt. Reale Welt-, Hardware- und Shaderpack-Abnahmen bleiben anschließend offen. Ein Vorteil im vollständigen Spielframe, stabile 300–400 FPS und die Zusammenarbeit mit den benannten externen Shaderpacks sind noch nicht nachgewiesen; ein erfolgreicher Build oder synthetischer GL-Test ersetzt diese Nachweise nicht.

## 1. Ausgangspunkt und verfügbare Umgebung

| Gegenstand | Verifizierter Stand |
|---|---|
| Basisrevision | `97c263c792864d03c2271670121fd72d53bbfeb1` |
| Unverändertes Baseline-Artefakt | `neutron-97c263c-baseline.jar`, vor den Produktionsänderungen gebaut |
| Baseline SHA-256 | `f80b4a65ceb77786b0469675fac3f014fe79fa17601cfdbc909bab46559a8fe8` |
| Toolchain | Temurin `1.8.0_482-b08`, Maven `3.9.15`; Baseline-Build erfolgreich |
| Laufzeitbasis | Minecraft 1.8.9, eingebettetes OptiFine `1.8.9_HD_U_L5`, LWJGL `2.9.4-nightly`; keine Bibliotheksmigration für diese Änderung |
| Lokale Messmaschine | AMD EPYC 9V45; 9 für die JVM sichtbare logische Prozessoren; Linux `6.18.57`, x86-64, glibc `2.41` |
| Isolierter Cache-Benchmark | Java 8, `-Xms512m -Xmx512m`, drei frische JVM-Paare; genaue Herkunft und Rohwerte im Core-Nachweis |
| GPU, Treiber, Display, Spielwelt | Für lokale In-game-/Hardware-Abnahme nicht verfügbar; kein entsprechendes Messergebnis |
| Windows-Hauptcheckout | `F:\Puslar Backup - Copy` war aus dieser Umgebung nicht erreichbar; dort wurde kein Stand behauptet oder überschrieben |
| Arbeitskopie | Ein temporärer Sparse-Checkout auf `codex/core-performance-shader-20261111`; kein zusätzlicher Git-Worktree, keine Kopie von Accounts, Welten oder privaten Laufzeitdaten |

Die vorbereitend genannte Revision `4d2091af…` ist nicht die Messbaseline dieses Durchgangs. Historische Messungen in `docs/RENDER-PERFORMANCE-2026-11.md` bleiben historische, enger abgegrenzte Nachweise. Der tatsächliche PR-/Kandidatencommit und die CI-Links müssen bei der Freigabe diesem Dokument beziehungsweise dem PR-Nachweis zugeordnet werden; die Arbeitsbranchbezeichnung allein identifiziert kein unveränderliches Messergebnis.

## 2. Zuordnung der drei Ziele

| Ziel | Umgesetzter Beitrag | Vorliegender Nachweis | Noch erforderliche Abnahme |
|---|---|---|---|
| 1. Chunkdaten schneller korrekt darstellen und Minecraft-Berechnungen verbessern | Exakte Cachegrenzen, persistente begrenzte Planung, faire Aufnahme dringender/normaler Arbeit, aktive Tasks nicht wiederholt ersetzen, korrekte Fehler-/Abbruchrückgabe, vollständige Uploadgruppen, Veröffentlichung auf dem Render-Thread | Drei isolierte Cache-Vergleichspaare; gezielte Java-8-Tests für Cache, Queue, Ownership, Scheduling und Budget | Vorgegenerierte Welt, schnelle Bewegung und Blockänderungen auf Zielhardware; Chunkfortschritt gemeinsam mit Frametimes; sichtbare neue Revisionen und Lifecycle prüfen |
| 2. Höhere und stabilere FPS mit allen vorgesehenen Clientvisuals | Entfernte 100-ms-Deadlineverlängerung, asynchrone nahe Rebuilds, begrenzte Uploadarbeit; begrenzte Frame-/CPU-/GPU-/Chunk-/GC-Erfassung; zusätzliche Renderzähler | Funktions-/Vertragstests der neuen Instrumentierung und Core-Pfade; eng abgegrenzte CPU-Methodenmessung | Sieben Szenen, drei gepaarte Läufe je Konfiguration, vollständige Bild- und Eingaberegression; echte CPU-/GPU-Verteilung und Framezeiten |
| 3. Externe Shaderpacks gleichzeitig mit Clientshadern | Explizite Pack-/Client-Passgrenzen; Wiederherstellung realer GL-Zustände und OptiFine-Metadaten; getrennte Read-/Draw-Ziele; spätere World-Overlay-Komposition mit eigener Tiefenkopie; Hauptkamera-/Schatten-Trennung | Unabhängiger Quellreview; kompilierte native Proben für Zustand und Overlay-Vertrag | Reale Shaderpack-Matrix mit allen angeforderten Clienteffekten, Originalarchiven/Presets, Bildern und Zielhardware-Messung |

Die Core-Entscheidungen, Quellenrevisionen, Lizenzen, Rohwerte und genauen Testgrenzen stehen in [CORE-PIPELINE-EVIDENCE-2026-11.md](CORE-PIPELINE-EVIDENCE-2026-11.md). Passvertrag, Shaderquellen und die konkrete Matrix stehen in [SHADERPACK-CLIENT-INTEGRATION-2026-11.md](SHADERPACK-CLIENT-INTEGRATION-2026-11.md). Dauerhafte Korrektheitsänderungen sind nicht an das Aktivieren eines neuen Optionsmoduls gebunden.

## 3. Bedienung der Aufzeichnung

### Spielbefehl

Der neue Clientbefehl heißt `.perf`, mit Alias `.performance`:

```text
.perf start 120
.perf status
.perf stop
.perf export s2_candidate_pair1_unlimited
```

`start` nimmt eine ganze Dauer von **1 bis 600 Sekunden** an; ohne Zahl werden 120 Sekunden verwendet. Eine laufende Sitzung muss vor einem neuen Start beendet werden. Die erste vollständige Framegrenze legt den Messbeginn fest; der teilweise gerenderte Startframe wird nicht als volles Intervall übernommen. Automatisch beendet wird nach dem vollständigen Intervall, das die gewünschte Dauer erreicht, oder bei ausgeschöpfter Framekapazität. `stop` beendet ausdrücklich am letzten vollständig beobachteten Intervall.

`export` ist nur nach Beendigung zulässig. Das Label besteht aus 1–48 ASCII-Buchstaben, Ziffern, Unterstrichen oder Bindestrichen. Jeder Export erzeugt im vorhandenen Spielverzeichnis einen neuen Unterordner unter `performance/<label>-<UTC-Zeit>-<eindeutiger Suffix>/`. Es wird kein Messlauf automatisch überschrieben. Der Export kann I/O-Fehler melden; ein vollständiger Datensatz benötigt alle unten genannten Dateien. Die Shaderpack-Datei wird beim ausdrücklichen Start vor der Messaufnahme gehasht, die Client-JAR beim Export. Hashberechnung und Dateischreiben können den Client kurz beschäftigen und gehören außerhalb des gemessenen Zeitfensters; Renderhooks schreiben keine Dateien.

Nach dem Stop darf die Spielschleife noch einige Frames laufen, damit ausstehende GPU-Abfragen ohne Blockieren ausgelesen werden. `gpu.pending` und `queryObjectsRemaining` im Export prüfen; bei ausstehenden Ergebnissen später erneut exportieren. Vorherige Exporte bleiben erhalten. Der Statusbefehl berechnet Statistiken ausdrücklich auf Anfrage; wiederholtes Abfragen während eines Vergleichslaufs verändert dessen CPU-Arbeit und ist zu vermeiden.

### Dateien und Bedeutung

| Datei | Inhalt und Abgrenzung |
|---|---|
| `capture.json` | Schema `neutron.performance.v1`, Label, Laufzustand/-grund, Frame-Statistik, Konfiguration am Anfang/Ende, Artefaktname/-SHA-256, Umgebung, Speicherendpunkte, GC-Ereignisse, Zählerdifferenzen, Kapazitäten und Verlustzähler |
| `frames.csv` | Geordnete vollständige Spielschleifenintervalle in ns/ms einschließlich Swap, Limiter und VSync; Index beginnt bei 0; lange Frames sind strikt größer als 50 ms |
| `cpu.csv` | Hauptthread-Wanduhrzeit je `WORLD`, `HUD`, `GUI` und Spiel-Frame-Sequenz; einschließlich Treiberaufrufen, kein exklusiver CPU-Zykluszähler |
| `gpu.csv` | Asynchrone GPU-Timestamp-Paare je Pass, Frame-Sequenz und Auslesezyklus; ungültige Bereiche und unvollständige Aufzeichnung bleiben erkennbar |
| `chunks.csv` | Chunk-/Section-Koordinaten, Daten-/Tasksequenz, Queue-/Mesh-/Upload-/Draw-Zeitpunkte, vorbereitete Vertexbytes und Ausgang/Abbruchgrund; `-1` bedeutet nicht beobachtet |

Durchschnitts-FPS werden aus `Framezahl / Summe der Frameintervalle` berechnet. Die Perzentile verwenden nearest rank. Die 1%-Low-FPS sind `1000 / Mittelwert der langsamsten ceil(N/100) Intervalle in Millisekunden`; sie sind nicht `1000 / p99`. Bei fehlenden vollständigen Frames werden nicht messbare JSON-Kennzahlen als `null` ausgegeben, nicht als erfolgreicher Nullwert.

`firstGameFrameSequence` und `lastCompleteGameFrameSequence` begrenzen den vollständigen Messbereich. CPU-/GPU-Proben aus einem teilweise gestoppten Frame dürfen nicht ungeprüft mit diesen vollständigen Frameintervallen verrechnet werden. `WORLD` umfasst den Weltaufruf einschließlich dessen Schatten-, Hand-, Composite- und Overlay-Arbeit. Vanilla-Postshader/Entity-Outlines, GuiTransitions/Benachrichtigungen, Ticks, Swap und Limiter liegen teilweise außerhalb der drei Phasenklammern, bleiben jedoch in den vollständigen Frameintervallen enthalten.

GPU-Zeiten sind Befehlsintervalle und keine exklusive Auslastung oder Präsentationslatenz. Die beiden Query-Ergebnisse werden erst nach bestätigter Verfügbarkeit gelesen; Produktionshooks führen weder `glFinish` noch einen zusätzlichen Flush aus. Ohne passenden aktuellen Kontext bzw. GL33/`ARB_timer_query` und Query-Unterstützung bleibt das Ergebnis ausdrücklich `NO_CONTEXT`/`UNSUPPORTED`. IDs werden nur im ursprünglichen Kontext gelöscht; bei Kontextwechsel wird die Bereinigung zurückgestellt, statt fremde IDs zu löschen.

Der Chunk-Draw-Marker liegt am Ende der betreffenden Hauptkamera-Layersubmission. Er ist eine obere Grenze für die CPU-Submission der passenden Revision, **kein Nachweis eines fertigen GPU-Bildes oder eines sichtbaren Pixels**. Die Datenmarke beobachtet die decodierte Paketspalte auf dem Clientthread. Servergenerierung, Übertragung und tatsächliche Displaylatenz werden dadurch nicht gemessen. Schatten-Submissions zählen nicht als erste Hauptkameradarstellung.

### Begrenzungen und Konfigurationswechsel

| Sammler | Von `.perf` verwendete Grenze | Verhalten bei Erreichen |
|---|---:|---|
| Frameintervalle | 131.072 | Aufnahme stoppt; keine Ringüberschreibung. Bei sehr hohen FPS kann dies vor 120/600 Sekunden eintreten |
| CPU-Phasen | 393.216 | Weitere Samples zählen als verworfen; vorhandene Rohdaten bleiben erhalten |
| GPU-Passproben | 131.072; höchstens 128 gleichzeitig offene Paare/256 Query-Objekte | Neue Marker werden begrenzt; vorhandene Paare laufen aus. Mehrere Pässe pro Frame können diese Grenze früher als die Frameaufnahme erreichen |
| Chunk-Traces | 8.192; Spaltenhistorie höchstens 8.192 | Neue Tasks zählen als verworfen; vorhandene Traces können ihre Stufen abschließen; alte Spaltenmarken können verdrängt werden |
| GC-Benachrichtigungen | 2.048 | Zusätzliche Ereignisse werden als verworfen gezählt |

Die Einzel-APIs erlauben begrenzte andere Kapazitäten: `FrameTimeCapture` maximal 1.000.000 Intervalle, `ChunkPipelineMetrics` maximal 65.536 Traces, `CpuPhaseTimings` maximal 3.000.000 Proben. Der Chatbefehl stellt diese Kapazitäten nicht um. Ihre Owner-Thread-Verträge gelten auch bei direkter Verwendung. `PerformanceCaptureSession.INSTANCE` koordiniert `start`, `frame`, CPU-/GPU-Passklammern, `stop`, `abort`, Kontextende und `export`; sie muss auf dem zuständigen Client-/Kontextthread verwendet werden. Ein Programm sollte eine Aufnahme nicht durch gleichzeitige direkte Starts einzelner Sammler verändern.

Welt-/Dimensionswechsel, Resize, Fullscreen, Resource-Reload, relevante Shaderressourcen- und überwachte Grafikänderungen beenden die laufende Sitzung mit Grund. Änderungen am Uploadbudget beenden sie ebenfalls. Die allgemeine Konfiguration wird zusätzlich an den Endpunkten verglichen. Gleichheit dieser beiden Snapshots beweist nicht, dass ein Wert dazwischen unverändert war; alle Einstellungen müssen während der Messung tatsächlich konstant bleiben.

Heap/DirectBuffer/GC-Zähler sind Endpunktdaten. Die Allokationsmessung umfasst nur den Render-Thread und nur, wenn die JVM diesen Zähler bereits unterstützt und aktiviert hat. GC-Ereignisdauer ist nicht automatisch eine Stop-the-world-Pause. Treiber-VRAM, sämtliche Worker-Allokationen und ein vollständiger GL-Aufrufstrom werden nicht gemessen. Renderzähler zählen instrumentierte API-Anforderungen, einschließlich redundanter Binds und gegebenenfalls Displaylist-Kommandos; ein MultiDraw-Aufruf zählt einmal. Sie sind keine physikalischen Zustandswechsel oder GPU-Abschlüsse.

Metadaten erfassen primitive Grafik-/OptiFine-Einstellungen, konfigurierte/aktive/Server-Resourcepack-Namen, verfügbare Shaderoptionen sowie Visualwerte der Kategorien Render/Client. JVM-Argumente und verfügbare effektive Heap-/GC-Optionen werden angegeben. Die reguläre Shaderpack-Datei kann beim Start mit SHA-256 identifiziert werden; Verzeichnis-Packs oder nicht auflösbare Dateien bleiben als nicht gehasht erkennbar. Weltstand, Kameraroute, fehlende Ressourcen-/Shaderarchiv-Hashes und ein vollständiges Preset aller sonstigen Module müssen dem Versuch gesondert zugeordnet werden. `-Dneutron.performance.revision=<vollständiger Commit>` kann ein Revisionslabel hinzufügen; die JAR-SHA-256 bleibt die Artefaktidentität. Bei einem Klassenverzeichnis kann keine JAR-SHA behauptet werden.

## 4. Client → Performance

Ohne aktiviertes Modul gelten **1 ms und 4 MiB pro Uploadphase**. Das Modul erlaubt **0,25–8 ms** und **0,25–32 MiB**. Deaktivieren stellt die Standardwerte wieder her. Die beiden Grenzen werden gemeinsam veröffentlicht; ungültige/nicht endliche Werte werden abgewiesen.

Eine vollständige Chunkrevision bleibt unteilbar. Mindestens eine Uploadgruppe darf trotz abgelaufener Deadline oder zu kleiner Bytegrenze fortschreiten. Ein großer Chunk kann deshalb die gewählten Grenzen überschreiten. Es handelt sich weder um eine harte Framezeitgarantie noch um eine adaptive Lastregelung. Die vorhandene Worker-/Builderzahl wurde nicht durch eine unbelegte zusätzliche Option erweitert.

Die Regler beeinflussen den Anteil der Uploadarbeit auf dem Render-Thread. Kleinere Werte können die Aktualisierung sichtbarer Chunks verzögern; größere Werte können Frametimes belasten. Eine sinnvolle Wahl braucht beide Messgrößen. Korrektheitskorrekturen, Cachegrenzen, deduplizierte Planung und gültige Veröffentlichung bleiben auch bei deaktiviertem Optionsmodul aktiv.

## 5. Tatsächlich gemessen und geprüft

Der unveränderte Baseline-Build war erfolgreich. Die lokale Core-Validierung hat **41 gezielte Tests bestanden**, einschließlich der später ergänzten Prüfung der Quad-/Triangle-Bytezählung, der Buffer-Rückgabe bei einem fehlgeschlagenen Uploadfuture und des Ablaufs Rebuild → Transparenz-Neusortierung → erste Draw-Submission. Der letzte isolierte Lauf umfasste zusätzlich alle 15 `ChunkPipelineMetricsTest`-Fälle und bestand damit **56 Tests**. Der unabhängige Core-Review hat Lock-Reihenfolge, aktuelle Taskidentität, Abbruch, einmalige Lease-Rückgabe und leere Owner-Thread-Veröffentlichung geprüft. Die Befunde zu verbleibenden Lifecycle-Wartemengen und blockierter Transparenzplanung wurden behoben und erneut getestet.

Die isolierte Cachemessung unterscheidet genau eine Produktionsklasse. Ergebnis: **110.592 → 8.000 Einträge je Lookup-Array**, entsprechend **−92,766%**. Über drei JVM-Paare beträgt der Median der gepaarten Medianänderungen **−91,87%** für die Cacheinitialisierung/-rückgabe und **−46,47%** mit Licht-/Blockabfragen an 4.096 Positionen. Im ersten Abfragepaar stieg das p95 der Batchmittelwerte um **1,03%**. Alle Werte und die ungünstigere Einzelmessung sind erhalten. Das ist ein CPU-Methodenbefund; daraus wird keine FPS- oder Sichtbarkeitsverbesserung hochgerechnet. [Rohdaten und Verfahren](CORE-PIPELINE-EVIDENCE-2026-11.md#isolated-cache-benchmark).

| Gate | Stand dieses Übergabedokuments |
|---|---|
| Exakter Baseline-Release-Build | PASS, oben genannte SHA-256 |
| Gezielte Core-/Scheduler-/Budgettests | PASS, 41 Tests unter Temurin 8u482; letzter gemeinsamer Lauf mit 15 Chunk-Metriktests: 56 PASS |
| Unabhängige Core-/Collector-/Shaderprüfung | Durchgeführt; behobene Befunde und Grenzen in den jeweiligen Nachweisen |
| Lokale vollständige Testsuite des Kandidaten | **FAIL**: 2.240 Tests, 11 Failures, 0 Errors, 3 Skips; nicht als grün gewertet |
| Gezielter integrierter Kandidaten-Release-Paketlauf | **BUILD SUCCESS**, 117 Tests, 0 Failures, 0 Errors, 1 bestehender Native-opt-in-Skip; 36,167 s. Enthält alle letzten Trace-/Integrationsänderungen; [Provenienz und Artefakthash](evidence/diagnostics-2026-11/integrated-focused-package.json), [vollständiger Buildlog](evidence/diagnostics-2026-11/integrated-focused-package.txt) |
| Neue native Proben | Java-8-kompiliert; lokale Ausführung vor GL-Kontextaufbau blockiert; keine lokale Native-PASS-Behauptung |
| GitHub Java-8-/Native-/Performance-/Release-Checks | PENDING bis zum konkreten PR-Commit; Ergebnisse und Artefakte zuordnen |
| Reale sieben Szenen und externe Shaderpacks | NOT RUN |
| Stabile 300–400 FPS auf Zielhardware | NOT ASSESSED; keine benannte getestete Spielerhardware vorhanden |
| Merge | Vom Nutzer ausdrücklich nach erfolgreichem technischem Review und relevanten CI-Prüfungen autorisiert; Hardware-/Spiel-/Pack-Abnahme folgt auf `main` |

Die neuen nativen Proben ergänzen vorhandene Bild-/Resource-/Unload-Prüfungen. Software-Mesa/Pbuffer kann GL-Verträge, Pixel und Ressourcenbereinigung nachweisen, aber weder die Ziel-GPU vertreten noch einen laufenden Client mit einem externen Pack ersetzen. Ein in JUnit absichtlich übersprungener opt-in Native-Test gilt nicht als ausgeführter GL-Test.

Die elf Failures des vollständigen lokalen Laufs werden nicht durch den gezielten Paketlauf aufgehoben: ein Windows-/JavaFX-bezogener Test und ein Font-Test scheiterten auch auf der Baseline; hinzu kommen zwei stderr-Assertions sowie sieben nur im Gesamtlauf beobachtete SmartDisable-Testfehler. Die vollständige SmartDisable-Klasse bestand isoliert sowohl auf Baseline als auch Kandidat mit 10/10 Tests. Das belegt den engeren isolierten Erfolg, aber weder eine aufgeklärte Ursache noch die Unschuld einer möglichen Testreihenfolge-/Zustandsabhängigkeit. Die finale Gesamtprüfung und die geeigneten Plattform-Checks bleiben offen.

## 6. Vorab festgelegtes Spiel-Messprotokoll

### Vergleichbare Instrumentierung

Die unveränderte Baseline `97c263c` enthält **kein `.perf`**. Ein Lauf mit neuer Instrumentierung nur im Kandidaten wäre kein sauberer A/B-Nachweis. Vor den Messungen eine der beiden Varianten festlegen:

1. Derselbe externe Frame-/Systemprofiler mit identischem Aufnahmeumfang auf Baseline und Kandidat. Die neue `.perf`-Aufnahme dient dann zusätzlicher Kandidatendiagnose; ihre Ergebnisse werden nicht mit anders erfassten Baselinewerten vermischt.
2. Eine gesondert geprüfte **reine Instrumentierungsbaseline**: ausschließlich die äquivalenten Messhooks und Exportfunktionen auf der Baseline, ohne Cache-, Scheduler-, Upload-, Shader- oder Optionsänderungen. Eigener Commit, vollständiger Diff, Artefakt-SHA und unabhängiger Review sind zwingend. Diese Variante darf nicht als unveränderte Original-JAR bezeichnet werden.

Bei Variante 1 darf `.perf` während der primären A/B-Framemessung entweder in beiden Varianten äquivalent vorhanden sein oder im Kandidaten ebenfalls deaktiviert bleiben. Instrumentierungskosten sind zusätzlich in separaten Wiederholungsläufen zu prüfen. Das unveränderte Baseline-Artefakt bleibt als Herkunftsanker erhalten.

### Umgebung und Reihenfolge

Vor dem ersten Lauf CPU/GPU/Treiber, Java/GC/Heap, Energieprofil, thermischen Zustand, Auflösung, GUI-Scale, Sichtweite, Vollbildmodus, Resourcepack, Visualpreset und Shaderarchiv-Hash festschreiben. Dieselbe Maschine, derselbe unveränderte Weltstand und dieselbe Kameraroute gelten für beide Varianten. Vorgegenerierte Chunks trennen Clientkosten von neuer Servergenerierung; lokaler integrierter Server oder Remoteverbindung wird als Versuchsbedingung dokumentiert.

Je zentraler Szene und Packkonfiguration mindestens **drei Paare**, Reihenfolge **A/B, B/A, A/B**, frische Clients und mindestens **60 Sekunden festes Warmup** vor jeweils **120 Sekunden Messung**. Falls Ressourcen oder Route danach noch nicht warm sind, die Warmup-Regel vor der eigentlichen Serie für beide Varianten gleich erweitern. Kalter Start, erste Shaderkompilierung und Resource-Reload bleiben gesonderte Szenen. Rohdaten aller Versuche, auch Abbrüche, bleiben erhalten.

Zwei getrennte Reihen: **unlimitiert, VSync aus** für Durchsatz sowie **120-FPS-Cap, VSync aus** für begrenztes Framepacing. Eine andere Alltagsschranke oder VSync-Konfiguration darf zusätzlich getestet werden, muss vorab benannt und als eigene Reihe ausgewertet werden. Pack aus/an wird ebenfalls getrennt ausgewertet. Eine Verbesserung bei abgeschalteten angeforderten Clientvisuals erfüllt keine der Reihen.

Ein vollständiges Protokoll enthält pro Run: Baseline-/Kandidatencommit und JAR-SHA, Szene, Paar, Reihenfolge, Warmup, Ist-Dauer, Framezahl, Endzustand, fehlende/verworfene Samples, feste Start-/Endkonfiguration, Route/Weltidentität, Preset-/Archiv-Hashes und Artefaktverzeichnis. Ein kapazitätsbedingt kürzerer Lauf ist kein vollständiger 120-Sekunden-Nachweis. Ausschlüsse brauchen einen vorab festgelegten technischen Grund, etwa falsche Konfiguration, Fokusverlust oder Messabbruch; ungünstige Frametimes allein sind kein Ausschlussgrund.

### Sieben Szenen

| Nr. | Festzulegender Ablauf | Primäre Bewertung und Bildgate |
|---|---|---|
| S1 | Bereits geladene Welt, feste Kamera/Route, alle vorgesehenen Clientvisuals und HUD aktiv | Frame-p95/p99, 1%-Low, lange Frames, GPU/CPU-Pässe; Darstellung und Animationen unverändert |
| S2 | Gleiche Bewegung durch vorgenerierte Chunks, festgelegte Geschwindigkeit und Blickrichtung | Verfügbare Daten → Submission, Queue/Mesh/Upload, fertiggestellte Revisionen je Zeit sowie Frametimes; keine fehlenden/alten Chunks |
| S3 | Reproduzierbare Folge vieler Blockänderungen bei gleichbleibender Kamera | Dirty-Zusammenfassung, Fortschritt normaler und dringender Arbeit, Taskabbrüche, Uploadspitzen; korrekte neue Revisionen |
| S4 | Dichte Szene mit festgelegten Entities/TileEntities, Partikeln und Spezialrenderern | Hauptthread-/GPU-Kosten, Speicher/GC; Schatten, Sichtbarkeit, Wasser/Glas und Spezialrenderer |
| S5 | Festes HUD-/ClickGUI-Skript mit Textänderungen, Eingabe, Blur, Glow, Liquid Glass und Animationen | UI-p95/p99, Draw-/Copy-/Bind-Zähler, Allokationen; Alpha, Clip, Fokus, Reihenfolge und Live-Hintergrund |
| S6 | Dieselbe geeignete Weltszene ohne Pack sowie mit jedem verbindlichen Pack/Preset, Clienteffekte gleichzeitig aktiv | Je Pack eigene Frame-/GPU-/Chunkauswertung; vollständige Shader-/Client-Bildmatrix |
| S7 | Je ein protokollierter 120-Sekunden-Ablauf mit festgelegten Zeitpunkten für Welt/Dimension, Reload, Packwechsel, Resize/GUI-Scale/Fullscreen | Keine stale Veröffentlichung, Ressourcenplateau, Wiederanlauf, Zustand und Bilder nach jedem Übergang; vollständige Übergangsframezeiten extern erfassen |

Auch S7 bekommt drei gepaarte Abläufe in beiden Framepacing-Konfigurationen. `.perf` bricht an diesen Übergängen absichtlich ab; ihre Teilfenster dokumentieren das Verhalten, erfassen aber nicht den kompletten Übergangsaufwand. Deshalb ist für S7 ein gleichartiger externer, durchlaufender Profiler notwendig. Erwartete Abbrüche zählen als Lifecycle-Vertrag, nicht als fehlgeschlagener stationärer Performancevergleich.

### Signal, Streuung und gemeinsame Bewertung

Vor Beginn die primären Größen festlegen: für S1/S4/S5/S6 Frame-p95; für S2/S3 sowohl Frame-p95 als auch p95 der verfügbaren Daten bis zur passenden CPU-Submission und der Durchsatz fertiggestellter Revisionen. p99, 1%-Low, lange Frames, durchschnittliche FPS, Queue/Mesh/Upload, Speicher und GC bleiben verpflichtende Begleitgrößen. Unvollständige Traces werden nicht still aus einem Erfolgsnenner entfernt; ihre Anzahl und Gründe gehören neben die abgeschlossenen Messwerte.

Für eine Zeitgröße sei die Verbesserung je Paar `d_i = 100 × (Baseline_i − Kandidat_i) / Baseline_i`. Für Durchsatz gilt entsprechend `d_i = 100 × (Kandidat_i − Baseline_i) / Baseline_i`. Als vorab festgelegte Paarstreuung dient `s = (max(d_i) − min(d_i)) / 2` in Prozentpunkten. Ein belastbarer Leistungsanspruch dieses Protokolls verlangt einen Median der Verbesserungen **größer als `max(5%, 2 × Paarstreuung)`**. Das ist eine konservative Entscheidungsschwelle, kein aus drei Paaren abgeleitetes Konfidenzintervall. Bei nicht ausreichendem Signal weitere vorab gleichartig geplante Paare aufnehmen; keinen passenden Teil der vorhandenen Läufe auswählen.

Eine erheblich verschlechterte Begleitgröße oder wichtige andere Szene bleibt ein offener Befund, auch wenn die primäre Größe gewinnt. Besonders darf ein niedrigeres Frame-p95 nicht mit stockendem Chunkfortschritt erkauft werden. Wiederholbare Bild-, Eingabe-, Shadow- oder Lifecyclefehler blockieren unabhängig von ihrem FPS-Effekt. Die 300–400-FPS-Ambition wird nur pro benannter Hardware und Konfiguration bewertet; 2,50–3,33 ms sind hier ein Zielbereich, kein gemessenes Ergebnis. „Stabil“ benötigt zusätzlich die vollständige Verteilung und lange Frames, nicht nur einen Durchschnittszähler.

## 7. Bild- und Shaderpack-Abnahme

Verbindliche Kandidaten sind **Sildur’s Enhanced Default v1.20 Fast** als einfacherer Vertreter und **Sildur’s Vibrant v2.02 High** als anspruchsvollerer Schatten-/Composite-Vertreter. Die Auswahl folgt den in der Shaderdokumentation verlinkten Autorenquellen; die allgemeine Versionsangabe des Autors ist keine Bestätigung der eingebetteten OptiFine-L5-Funktionen. Beide Reihen stehen derzeit auf **NOT RUN**, nicht auf „unterstützt“. Ein optionaler SEUS-v11.0-Lauf ist eine zusätzliche, separat zu bewertende Reihe.

Je Pack das Originalarchiv mit SHA-256, Preset, Optionen, Ressourcenpack und Clientvisualpreset erfassen. Die Matrix braucht gleichzeitig World-/Target-/Glow-/Nametag-Effekte sowie HUD/ClickGUI mit Blur, Glow und Glass, soweit sie zum festgelegten Visualpreset gehören. Wasser, Glas, Partikel, transparente Modelle und Chests, Schatten außerhalb des Hauptfrustums, Hand-/Screenoverlays und Zielwechsel kontrollieren. Native/halbierte/erhöhte Shaderauflösung, Resize, GUI-Scale und Pack-/Welt-/Resourcewechsel sind eigene Kontrollpunkte.

Für neue Bildvergleiche Kamera, Weltzeit, Wettereinstellungen, Animationsphase und Auflösung fixieren. Auf demselben Renderer bleibt bestehende exakte RGBA-Gleichheit bestehen, wo sie bereits gefordert ist. Neue Toleranzen dürfen nur vorab für begründete, tatsächlich treiberabhängige Unterschiede festgelegt werden. Ein sichtbarer Halo-, Alpha-, Tiefen-, Clip-, Reihenfolge- oder Schattenfehler darf nicht durch nachträglich gelockerte Pixelassertions verschwinden. Für dynamische Inhalte zusätzlich kontrollierte Bildfolgen und Eingabeabläufe aufzeichnen.

Die neue Bridge kopiert private Tiefe und komponiert Clienteffekte nach dem Pack-Finalpass, ohne dessen Gbuffer/MRT-Bedeutungen pauschal zu überschreiben. Die Tiefenquelle kann Handtiefe enthalten; sie ist nicht reine Geländetiefe. Zusätzliche Copy-/Resamplingkosten sind deshalb Teil der S6-Messung. Fehlende GL-Fähigkeit oder ein nachweislich inkompatibles Pack wird präzise aus der freigegebenen Matrix ausgeschlossen. Das Abschalten eines angeforderten Clienteffekts ist kein erfolgreicher Supportnachweis.

## 8. Verwendete Game-Studios-Methoden

Untersucht wurde [Donchitos/Claude-Code-Game-Studios](https://github.com/Donchitos/Claude-Code-Game-Studios/tree/abc0de79d6ceb0b5d7aa693945849b3937afbe6a) bei Commit `abc0de79d6ceb0b5d7aa693945849b3937afbe6a`, Release 1.3.0. Die [MIT-Lizenz](https://github.com/Donchitos/Claude-Code-Game-Studios/blob/abc0de79d6ceb0b5d7aa693945849b3937afbe6a/LICENSE) wurde geprüft. Claude Code samt Skill-Laufzeit war hier nicht installiert; es wurde keine Ausführung dieser Claude-Skills behauptet und kein vollständiges Template oder Hooksystem installiert.

Die fünf gelesenen Rollen waren `performance-analyst`, `engine-programmer`, `technical-artist`, `ui-programmer` und `qa-tester`. Die fünf gelesenen Skills waren `perf-profile`, `architecture-review`, `code-review`, `regression-suite` und `test-evidence-review`; ihre Quellen liegen in den gepinnten [agents](https://github.com/Donchitos/Claude-Code-Game-Studios/tree/abc0de79d6ceb0b5d7aa693945849b3937afbe6a/.claude/agents)- und [skills](https://github.com/Donchitos/Claude-Code-Game-Studios/tree/abc0de79d6ceb0b5d7aa693945849b3937afbe6a/.claude/skills)-Verzeichnissen.

Übertragen wurden die Trennung zwischen statischem Kandidaten und Messnachweis, explizite Thread-/Ressourcenverantwortung, Zuordnung jedes Ziels zu Code und Abnahme, Erhalt der geforderten Darstellung, Tests der ursprünglichen Fehler sowie eine unabhängige Prüfung der tatsächlichen Nachweise. Der Standardmodus von `team-polish` ersetzt weder Implementierung noch Abnahme. Die bestehende Maven-/Java-/Minecraft-Struktur und die bereits autorisierte Arbeitsweise blieben maßgeblich.

Die Arbeit war tatsächlich auf Core-/Chunk-Engineering, Shader-/Client-Rendering, Profiling/Messmethodik und Lead-Integration verteilt; unabhängige Reviews liefen anschließend über die jeweils anderen Bearbeiter. Klare Dateizuständigkeiten verhinderten konkurrierende Änderungen an den gemeinsamen Integrationspunkten.

## 9. Rücknahme und Freigabe

Rücknahme erfolgt als geprüfter, zusammenhängender Revert über einen Aufgabenbranch/PR, nicht als Force-Push oder blindes Überschreiben von `main`. Zwei fachliche Gruppen sind zu beachten:

- **Core:** persistente `RenderGlobal`-Planung, Compile-Queue/Task/Worker/Dispatcher, Uploadgruppen, `RenderChunk`/`CompiledChunk`-Veröffentlichung und ihre APIs müssen gemeinsam konsistent bleiben. Budgetmodul und Diagnoseaufrufer müssen zu den dann vorhandenen APIs passen. Die isolierte `ChunkCacheOF`-Boundsänderung kann gesondert zurückgenommen werden, wenn ihr eigener Regressionsnachweis dies verlangt.
- **Shaderintegration:** World-Bridge samt EntityRenderer-/Lifecycle-Hooks, OptiFine-Clientzustand und Generationen, RenderState/UiDrawState/ShaderUtil sowie die betroffenen Glow-/Overlay-Übergänge bilden eine zusammengehörige Gruppe. Keine halbe Bridge oder einseitige Wiederherstellung von GL- statt OptiFine-Zustand zurücklassen.

Nach einem Revert die betroffenen Java-8-, Bild-, Native- und Lifecycleprüfungen erneut ausführen und die Supportmatrix neu bewerten. Ein deaktiviertes `Performance`-Modul setzt nur die Uploadregler zurück; es ist kein Core-Rollback. Das Deaktivieren verlangter Visuals oder externer Packs zählt nicht als gelöste Kompatibilität.

Die PR bleibt während der technischen Integration und CI-Prüfung Draft. Nach erfolgreichem Review und den relevanten CI-Gates ist der reguläre Merge durch den aktualisierten Nutzerauftrag ausdrücklich autorisiert, damit die Spieltests auf `main` stattfinden können. Reale Bild-/Pack-/Hardwarebelege und ein Leistungsnutzen jenseits der Streuung bleiben offene Abnahmen; diese werden durch den Merge nicht als bestanden gewertet. PR-Link, finaler getesteter Commit, CI-/Artefaktlinks und tatsächlicher Merge-Commit werden aus GitHub übernommen und nicht vorweggenommen.

Der Windows-Hauptcheckout kann aus dieser Umgebung nicht synchronisiert werden. Nach einem zulässigen Merge muss dort `main` sicher aktualisiert werden, sobald der Checkout verfügbar ist. Bis dahin verbleiben nur die benannte temporäre Sparse-Arbeitskopie und aufgabenbezogene Build-/Testnachweise; es wurde kein Git-Worktree angelegt und folglich keiner entfernt. Gesicherte Rohmessungen bleiben erhalten; regenerierbare temporäre Hilfsdateien können nach der geprüften Übergabe entfernt werden.
