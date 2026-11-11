# Unabhängiger Review: Core, Shaderintegration und Messnachweise

Stand: 2026-11-11. Basis: `97c263c792864d03c2271670121fd72d53bbfeb1`.

Der Nutzer hat nach diesem Arbeitsdurchgang ausdrücklich die PR-Erstellung, erforderliche CI-Korrekturen und den regulären Merge nach `main` zum eigenen Testen autorisiert. Der lokale Quellreview und gezielte Tests stützen die unten beschriebenen Verträge. Der PR-/CI-Prozess bleibt erforderlich; fehlende Zielhardwaremessungen und reale Shaderpack-Szenen werden als offene Abnahmen nach dem autorisierten Merge weitergeführt. Sie sind nach dieser Nutzerentscheidung keine zusätzliche Draft- oder Merge-Sperre.

Allgemeine FPS-Verbesserungen, kürzere sichtbare Chunk-Ladezeiten oder vollständige externe Shaderpack-Kompatibilität sind weiterhin nicht nachgewiesen. Der vollständige lokale Testlauf enthält die unten dokumentierten Fehler; der SmartDisable-Fixture-Befund wurde anschließend gezielt reproduziert und korrigiert, während die vollständige unterstützte CI-Abnahme offen bleibt. Die neuen nativen Proben konnten hier keinen GL-Kontext erzeugen. CI-Ergebnisse müssen zur tatsächlich veröffentlichten Revision gehören. Eine erteilte Merge-Erlaubnis verändert weder diese Messgrenzen noch den Status eines fehlgeschlagenen Tests.

Dieser Bericht beschreibt den gemeinsam geprüften Arbeitsstand. Die endgültige Commit-ID und die CI-Ergebnisse müssen bei der PR-Übergabe hinzugefügt werden. Die [Nachweisprovenienz](evidence/diagnostics-2026-11/provenance.json) enthält Quellhashes der vier eigenständig getesteten Diagnoseklassen sowie Hashes der abgelegten Logs. Buildartefakte, Benutzerkonten, Welten und Abhängigkeiten sind keine Bestandteile dieser Nachweise.

## 1. Basis und Reviewmethode

Der Baseline-Release wurde vor den Produktionsänderungen aus der genannten Revision gebaut. Der gesicherte JAR hat SHA-256 `f80b4a65ceb77786b0469675fac3f014fe79fa17601cfdbc909bab46559a8fe8`. Die neue Diagnostik wurde mit Eclipse Temurin `1.8.0_482-b08` und JUnit `4.13.2` geprüft. Maven-Builds wurden zentral ausgeführt; die zusätzlichen Diagnoseprüfungen nutzten ein eigenes Klassen-Ausgabeverzeichnis und veränderten den laufenden Maven-Build nicht.

Die ausdrücklich gewünschte Referenz [Claude-Code-Game-Studios](https://github.com/Donchitos/Claude-Code-Game-Studios/tree/abc0de79d6ceb0b5d7aa693945849b3937afbe6a) wurde auf Commit `abc0de79d6ceb0b5d7aa693945849b3937afbe6a` festgehalten, Version 1.3.0 vom 2026-11-09. Gelesen wurden die Rollen `performance-analyst`, `engine-programmer`, `technical-artist`, `ui-programmer`, `qa-tester` und die Abläufe `perf-profile`, `architecture-review`, `code-review`, `regression-suite`, `test-evidence-review` einschließlich einschlägiger Referenzen. Quellen: [Rollen](https://github.com/Donchitos/Claude-Code-Game-Studios/tree/abc0de79d6ceb0b5d7aa693945849b3937afbe6a/.claude/agents), [Abläufe](https://github.com/Donchitos/Claude-Code-Game-Studios/tree/abc0de79d6ceb0b5d7aa693945849b3937afbe6a/.claude/skills), [MIT-Lizenz](https://github.com/Donchitos/Claude-Code-Game-Studios/blob/abc0de79d6ceb0b5d7aa693945849b3937afbe6a/LICENSE).

Ein Claude-Runtime war nicht verfügbar. Die genannten Abläufe dienten als Methodenreferenz; es wurde weder deren Ausführung behauptet noch deren Projektstruktur installiert. Übernommen wurden die klare Trennung zwischen Quellbefund, isoliertem Test, realem GL-Vertrag und tatsächlicher Spielmessung, explizite Ressourcen- und Thread-Eigentümerschaft, unabhängige Gegenprüfung und die Regel, fehlende Nachweise offen zu lassen. Qualität, Effekte und bestehende Abnahmekriterien wurden nicht zur Erzielung besserer Zahlen reduziert.

Der Profiling-Reviewer prüfte Core-/Scheduler- und Shaderänderungen einschließlich ihrer Aufrufer, Abbruchpfade und Veröffentlichung unabhängig von deren Implementierern. Die selbst implementierten Frame-/Chunk-/GPU-Captures wurden vom Core-Reviewer gegengeprüft; die native Capture-Fixture zusätzlich vom Shader-Reviewer. Reine Quellvermutungen wurden von reproduzierten Befunden getrennt. Der anfänglich vermutete normale `stopChunkUpdates`-Deadlock wurde nach Prüfung des tatsächlichen `loadRenderers`-Ablaufs verworfen; dieser Patch wird nicht als Deadlock-Fix ausgewiesen.

## 2. Ergebnis des Quellreviews

### Core und Chunkpipeline

Geprüft wurden die begrenzte Compile-Queue, die beständige Admission in `RenderGlobal`, Taskstatus und Abbruchcallbacks, Builder-Übernahme und Rückgabe, die vollständige Uploadgruppe, Tile-Entity-Veröffentlichung, Repositionierung, Cachebereich und die ersten Hauptkamera-Draw-Hooks.

Die Lockreihenfolge der geänderten Veröffentlichungspfade bleibt RenderChunk vor Task. Ein Builder-Lease wird einmal übernommen; Worker-`finally` und Abschlusscallbacks berücksichtigen erfolgreiche, abgebrochene und fehlgeschlagene Arbeit. Ein Abbruch kann den Builder nicht während einer laufenden geschützten Uploadgruppe an einen anderen Worker zurückgeben. Auch ein leerer Mesh wird auf dem Owner-Thread abgeschlossen. Die aktuelle Taskidentität wird vor Upload und Veröffentlichung geprüft. Global sichtbare Tile-Entity-Mengen werden zusammen mit dem akzeptierten Mesh auf dem Clientthread veröffentlicht.

Zeit- und Bytebudgets wirken zwischen vollständigen Gruppen. Eine Gruppe darf das weiche Budget überschreiten, damit Fortschritt möglich bleibt. Ein GL-Fehler während einer Gruppe führt weiterhin in den Rendering-Fehlerpfad; es gibt keine neue Behauptung einer VBO-Rollback-Transaktion. Die Cacheverkleinerung betrifft die CPU-Lookuparrays, nicht die direkten Meshbuffer. Die [Core-Evidenz](CORE-PIPELINE-EVIDENCE-2026-11.md) dokumentiert Implementierung, 41 gezielte Coretests und den separat ausgeführten Cachebenchmark einschließlich seiner Grenzen. Der [abschließende isolierte Corelauf](evidence/diagnostics-2026-11/core-and-chunk-56-tests.txt) bestand 56 Tests: diese 41 sowie die 15 Abschnitts-/Chunk-Metriktests; überlappende Tests dürfen nicht erneut als zusätzliche Abdeckung gezählt werden.

Der Zugriff auf `ChunkCache` bleibt ein Zugriff auf lebende Weltdaten. Lokaler Scratchspeicher, Taskidentität und eine Capture-Epoche bilden weder einen unveränderlichen Weltzustand noch einen allgemeinen Beweis für beliebige zusätzliche Worker. Entsprechend wurde keine pauschale Erweiterung der Workerzahl oder Thread-Sicherheit zugesichert.

### Shader- und Clientpassgrenzen

Geprüft wurden `RenderState`, `RenderStateBuffers`, `UiDrawState`, `ShaderUtil`, `Shaders.ClientRenderState`, `GlowESP`, `CustomEntityRenderer` und `ShaderWorldOverlayBridge`. Wesentliche Verträge sind die getrennten Uniform-Namensräume, die vorübergehende Freigabe von OptiFine-Alpha-/Blend-Overrides, die Wiederherstellung des logischen und tatsächlichen Zustands, verschachtelte Scopes sowie getrennte Read-/Draw-FBOs, Viewport, MRT-Ausgabe und Ressourcengenerationen.

Die verspätete World-Overlay-Ausgabe verwendet die finale Hauptkamerafarbe und eine eigene Kopie der späten Welttiefe. Die fremde Farbtextur wird nur als Ziel geliehen, nicht als eigener Besitz gelöscht oder zugleich gesampelt. Kamera, Welt, Frame, Auge, PartialTicks und Ressourcengeneration müssen weiterhin passen. Schattenpfade, Exception-Cleanup und die Freigabe innerhalb eines Callbacks wurden berücksichtigt. Der native Vertrag enthält auch Renderauflösungen unterhalb und oberhalb der Ausgabeauflösung; reale externe Packs bleiben eine separate Abnahme.

Beim Anaglyph-Pfad wurde bestätigt, dass die Gbuffer-Maske nach `setViewport` alle Kanäle freigibt. Die finale Clientausgabe benötigt deshalb ausdrücklich die jeweilige Cyan-/Rot-Maske. Zusätzlich setzt `beginRenderPass` für jedes Hauptkameraauge `isRenderingWorld=true` und `isCompositeRendered=false`; Schatteneintritte verändern diese Hauptkamera-Flags nicht. Die native Fixture ruft reale `beginRenderPass`/`setViewport`/`endRender`-Methoden auf und verlangt je Auge einen tatsächlichen Final-Draw, ohne Duplikat bei wiederholtem Abschluss. Diese Fixture ist implementiert und kompiliert, lokal jedoch nicht als GL-Test ausgeführt. Details und Packmatrix: [Shaderintegration](SHADERPACK-CLIENT-INTEGRATION-2026-11.md).

### Capture-Lifecycle und Exporte

Geprüft wurden `PerformanceCaptureSession`, Metadaten, CPU-Phasen, GC-Ereignisse, Command und die Minecraft-/EntityRenderer-Hooks. Vollständige Frameintervalle beginnen erst nach einer ersten abgeschlossenen Schleifengrenze. Der Hook liegt nach Swap und Limiter; die Messung hängt nicht von der HUD-Darstellung ab. CPU-/GPU-Zeilen enthalten echte Spielframe-IDs, sodass partielle Start-/Stop-Frames anhand des exportierten vollständigen Bereichs erkennbar bleiben.

Abbruch bei Welt-, Auflösungs-, zentralen Anzeigeeinstellungs- oder Shaderressourcenänderungen erhält bereits gesammelte Daten. Beim Stop werden Counter-Eigentümerschaft, CPU-/Chunk-Capture und GC-Listener zurückgegeben; GPU-Abfragen dürfen danach weiter asynchron auslaufen. Vor Kontextzerstörung werden verbleibende Query-Objekte im ursprünglichen Kontext entfernt. Verschachtelte `finally`-Blöcke verhindern, dass die diagnostische Bereinigung `Display.destroy` überspringt. GC-Callbacks prüfen ihre Capture-Epoche und JVM-Uptime-Grenzen.

Die Metadaten enthalten eine begrenzte Auswahl aus Plattform, effektiven VM-/Heap-/GC-Optionen, Grafikparametern, visuellen Modulen, Packnamen/-optionen und Artefakthashes. Account-, Serveradress- und Chattexte werden nicht eingesammelt. Shaderpack-Dateihashing findet bei explizitem Start statt, JAR-Hashing beim Export; kein Framehook schreibt Dateien oder hasht Archive. Leere Statistiken werden als JSON-`null` exportiert. Exportlabels erlauben ausschließlich 1–48 Buchstaben, Ziffern, Unterstrich oder Bindestrich.

### Gefundene und bearbeitete Randfälle

| Befund | Korrektur / Nachweis | Stand |
|---|---|---|
| Forced-/Transparenzwartemengen blieben nach Dispatcherstop/Weltdetach erhalten | Alle drei Mengen und Admission-Präferenz werden geleert; Scheduler-/Unload-Fixtures ergänzt | Quellreview und reine Tests abgeschlossen; nativer Unload offen |
| Aktiver erster Transparenzeintrag blockierte einen späteren bereiten Eintrag | Aktive Einträge werden übersprungen; eine erfolgreiche Admission pro Frame bleibt die Grenze | Gezielt getestet |
| Finaler Anaglyph-Overlaypass übernahm die Allkanal-Gbuffer-Maske | Augenmaske explizit begrenzt; Pixelorakel für Rot/Cyan | Quellreview abgeschlossen; natives Pixelgate offen |
| Zweites Auge ohne Hand konnte fertige Flags des ersten Auges übernehmen | Zwei Hauptkamera-Flags werden pro Auge zurückgesetzt; echte Final-Draw-Fixture | Quellreview abgeschlossen; natives Gate offen |
| Alte Paketmarkierung galt unbegrenzt auch für Sortierung und spätere lokale Rebuilds | Nur Rebuilds pro Section; erste Hauptkamera-Submission verbraucht genau die gebundene Markierung; Retry bleibt möglich | 15 Chunk-Metriktests bestanden |
| Vollständige Column-Ersetzung/Unload ließ Marks ausgelassener Sections bestehen | `forgetColumn` vor Full-Replacement/Unload; anschließend tatsächliche 16-Bit-Paketmaske; signed S21-Maske am Hook normalisiert | Regressionstest bestanden; Hooks gegengeprüft |
| GPU-Zeilen enthielten nur Pollnummern | Zusätzliche echte Spielframe-ID; Pollnummern bleiben reine Query-Lifecycle-Information | Reiner Join-Test bestanden; nativer Join-Test kompiliert |
| Cleanup konnte durch terminale Metadatenerfassung ausfallen | Bereinigung in `finally`, sichere terminale Metadaten, verschachtelter Shutdown | Quellreview abgeschlossen; native Capture-Fixture offen |
| Fehlgeschlagener neuer Capture-Start konnte teilweise neue Exportmetadaten zurücklassen | Metadaten zunächst lokal vorbereitet; nach einem Ressourcenstartfehler wird der Export kontrolliert ungültig gemacht | Korrigierte Starttransaktion unabhängig nachgeprüft |
| Rebuild-Trace konnte vor dem ersten Draw durch Sort-Trace ersetzt werden | Je CompiledChunk bleiben ursprünglicher Build-Trace und aktueller Sort-Trace erhalten; erste Submission meldet beide und räumt die Referenzen ab; DUMMY hält keine Traces | Unabhängiger Quellreview abgeschlossen; drei neue Tests im erfolgreichen 56er-Corelauf enthalten |
| Neuer Shader-Probenmarker passte nicht zum Runner | Erwarteter Marker auf den tatsächlich erweiterten PASS-Text synchronisiert | Shell-Syntax geprüft |

## 3. Definitionen und Messgrenzen

| Größe | Definition / zulässige Aussage | Grenze |
|---|---|---|
| Durchschnittliche FPS | Anzahl vollständiger Intervalle geteilt durch deren Zeitensumme | Kein arithmetischer Mittelwert einzelner FPS-Werte |
| p50/p95/p99 | Nearest-rank-Perzentile der Rohintervalle | Kleine Stichproben bleiben sichtbar; keine stillen Ausschlüsse |
| 1%-Low | `1000 / Mittelwert der langsamsten ceil(N * 0,01) Intervalle in ms` | Eigene Größe; nicht als p99-FPS etikettieren |
| Lange Frames | Intervalle strikt oberhalb des gespeicherten Schwellwerts; Standard 50 ms | Schwellwert, Anzahl und Anteil gemeinsam exportiert |
| CPU-Phasen | Verstrichene CPU-Uhrzeit zwischen benannten Eintritts-/Austrittshooks | Inklusiv; keine CPU-Auslastung. Nicht markierte Arbeit bleibt im Gesamtframe |
| GPU-Phasen | Asynchrone Differenz zweier tatsächlicher GPU-Timestamps | Kein exklusiver GPU-Auslastungswert; überlappende Phasen nicht addieren |
| Chunk-Handoff | Nach Paketdekodierung auf dem Clientthread, vor Weltanwendung, nur passende Section-Rebuilds | Serverarbeit und Übertragungszeit werden nicht gemessen |
| Erster Chunk-Draw | Ende der enthaltenden Hauptkamera-Layer-Submission | CPU-Obergrenze für erste beobachtete Submission; keine GPU-Fertigstellung oder sichtbare Präsentation |
| Uploadbytes | Tatsächlich vorbereitete Vertexbytes einschließlich vorhandener Quad-Konvertierung | Keine PCIe- oder Treiberspeichermessung |
| Rendercounter | Instrumentierte API-Aufrufe; redundante Binds zählen, Multi-Draw zählt einmal | Keine vollständige Erfassung aller rohen GL-Aufrufe oder physischer Zustandswechsel |
| Speicher/GC | Start-/Stop-Endpunkte, begrenzte GC-Ereignisse, verfügbare Renderthread-Allokationszähler | Enthält Captureaufwand und partielle Randframes; keine vollständige All-Thread-Allokationsmessung |

Frame- und GPU-Rohspeicher sind begrenzt. GPU-Capture verwendet höchstens 128 gleichzeitig ausstehende Paare und 131072 Ergebniszeilen. Beide `GL_QUERY_RESULT_AVAILABLE`-Flags werden geprüft, bevor einer der beiden 64-Bit-Werte gelesen wird. Der Produktionspfad verwendet weder `glFinish` noch einen erzwungenen Flush oder Warte-Loop. Unterstützte APIs sind GL33-Timestamps beziehungsweise `ARB_timer_query`; fehlender Kontext, fehlende Unterstützung, verworfene/offene Ergebnisse und ungültige Intervalle werden explizit ausgewiesen. Grundlage: [ARB_timer_query, Khronos-Spezifikation](https://registry.khronos.org/OpenGL/extensions/ARB/ARB_timer_query.txt).

Die Query-IDs gehören zum erzeugenden GL-Kontext. Bei Verlust/Fremdkontext werden keine IDs blind in einem anderen Kontext gelöscht. Eine versäumte Bereinigung vor endgültiger Kontextzerstörung kann daher einen bewusst gesperrten Capturezustand hinterlassen; der integrierte Shutdown ruft den Abbruch vorher auf. Native Treiberunterstützung kann aus einem Fake-Backend-Test nicht abgeleitet werden.

Chunk-Capture hält standardmäßig 8192, maximal 65536 Traces. Die Paketzuordnung ist auf höchstens 8192 Columns mit jeweils 16 Referenzen begrenzt. Traces halten eine kleine Capture-Epoche und ihre eigene Paketmarkierung, keine Session mit sämtlichen Zeilen. Neuere Pakete werden durch spätere Draw-Callbacks alter Traces nicht verbraucht. Cancel/Fail/Queue-Rejection verbrauchen keine Markierung. Ohne Beobachtung stehen Datenzeit und Markierungssequenz auf `-1`. Stop beendet neue Ereignisse; ein Callback, der seine Akzeptanzprüfung bereits passiert hat, kann noch seinen einzelnen synchronisierten Trace abschließen. Ein Snapshot ist danach eine unveränderliche Kopie, keine globale Welttransaktion.

## 4. Tatsächlich ausgeführte Prüfungen

Die vier unabhängigen Capture-Klassen bestanden zusammen **41 Tests**: `FrameTimeStatisticsTest` 5, `FrameTimeCaptureTest` 8, `ChunkPipelineMetricsTest` 15 und `GpuPassTimingsTest` 13. [Gesamtlog](evidence/diagnostics-2026-11/diagnostics-41-tests.txt), [neue Abschnittstests](evidence/diagnostics-2026-11/chunk-association-15-tests.txt), [Provenienz und Quellhashes](evidence/diagnostics-2026-11/provenance.json).

Diese Prüfungen decken Statistikgrenzen, NaN/Leerzustand, unveränderte Eingaben, begrenzte Aufzeichnung, deaktivierte Clock-/Driverpfade, Owner-Threads, alte Epochen, unveränderliche Exporte, Paketmasken/Retirement, Querybereitschaft, verschachtelte Marker, Saturierung, alte Tokens, Teilallokationsfehler und kontextgebundene Bereinigung ab. Sie führen keinen Spielrenderer aus und liefern keine realen GPU-Zeiten.

`GpuPassTimingsNativeCheck` und `PerformanceCaptureNativeCheck` wurden unter Java 8 kompiliert. Die zweite Probe verwendet einen tatsächlichen Pbuffer, die Produktions-Capture-Session, echte GL-Timestamps, instrumentierte Programm-/FBO-Aufrufe und JSON-/CSV-Dateien. Sie prüft vollständige Frame-IDs, Counter-Rückgabe, Settingsabbruch sowie einen absichtlich offenen Timestamp-Pair beim Kontextabbruch. Ein stale Token darf anschließend keine Query-IDs erzeugen. Das ist eine begrenzte Fixture, kein ausgeführter `runGameLoop` und keine Welt. [Kompilationslog](evidence/diagnostics-2026-11/capture-native-compile.txt).

Lokale native Ausführungsversuche scheiterten vor dem GL-Kontext mit `Could not open X display connection`, auch mit den vorgesehenen Headless-/llvmpipe-Optionen. Damit gibt es **kein lokales Native-PASS** und keine GPU-Messwerte. Die Capture-Fixture wurde danach noch um strengere Counter-/Pending-Assertions ergänzt und erneut kompiliert; der alte Kontextfehlerlog wird nicht als Ausführung dieser neuen Assertions ausgegeben. [GPU-Kontextfehler](evidence/diagnostics-2026-11/gpu-native-context-blocked.txt), [Capture-Kontextfehler](evidence/diagnostics-2026-11/capture-native-context-blocked.txt).

### Vollständiger lokaler Testlauf: weiterhin offen

Der erste vollständige Kandidatenlauf meldete **2240 Tests, 11 Fehler im Sinne von Assertions, 0 Test-Errors, 3 Skips**. Dieser Lauf liegt zeitlich vor den abschließenden Capture-/Reviewkorrekturen. Seine Fehler werden durch spätere gezielte Erfolge nicht gelöscht. Ein [unveränderter Auszug der Fehlerzusammenfassung](evidence/diagnostics-2026-11/candidate-suite-summary.txt) und der Hash des vollständigen lokalen Logs sind abgelegt.

| Gruppe | Beobachtung | Schlussfolgerung |
|---|---|---|
| `SmartDisableLifecycleTest` | 7 von 10 im vollständigen Lauf fehlgeschlagen; zunächst isoliert 10/10 gegen beide Varianten bestanden; später dieselben sieben Fehler mit gezielt kompiliertem Supplier auf Baseline und Kandidat reproduziert | Testfixture-Ursache und enge Korrektur im folgenden Nachtrag; vollständige CI-Abnahme weiterhin erforderlich |
| `JavaFxRuntimeTest` | Ein Fehler in der vorhandenen Windows-spezifischen JavaFX-Prüfung auf Linux | Unterstützter Windows-/Java-8-CI-Lauf erforderlich |
| `FontAtlasMetricsTest` | Ein Fehler bei Supplementary-/Emoji-Zeichen; entsprechende Baseline-Grenze vom Integrator festgestellt | Unterstützte Font-/Plattformumgebung und vollständiger CI-Nachweis erforderlich |
| `NetworkResearchHarnessTest` | Zwei Fehler mit `JAVA_TOOL_OPTIONS`-Startbanner auf erwartet leerem Prozess-stderr | Umgebungseffekt gesondert behandeln; das vorhandene Gate bleibt erhalten |

Für den SmartDisable-Vergleich wurde ausschließlich diese bestehende Suite in frischen Temurin-8-Prozessen ausgeführt. `JAVA_TOOL_OPTIONS` wurde nur für diese Prozesse entfernt; Temp-/Headless-Optionen wurden direkt als JVM-Argumente übergeben. Baseline-Classpath: aktuelle Testklassen, exakter gesicherter Baseline-JAR, dieselben Abhängigkeiten. Kandidaten-Classpath: dieselben Testklassen, vollständige Kandidatenklassen, dieselben Abhängigkeiten. [Baseline-Log](evidence/diagnostics-2026-11/smart-disable-baseline-isolated.txt), [Kandidaten-Log](evidence/diagnostics-2026-11/smart-disable-candidate-isolated.txt).

Ein erster Kandidatenvergleich kollidierte mit dem gerade geleerten Maven-Ausgabeverzeichnis und scheiterte an fehlenden Klassen. Er ist kein gültiger Vergleich und wurde nach vollständiger Kompilation ersetzt. Die zunächst nur vermutete JIT-Abhängigkeit der bestehenden Unsafe-Neubelegung von `static final Module.mc` wurde anschließend mit einem gezielten Reproducer untersucht. Die historischen Fehlerlogs bleiben unverändert erhalten; die nachstehende Korrektur betrifft ausschließlich die Testfixture.

### Nachtrag: SmartDisable-Fixture unter erzwungener Kompilation

Der bisherige Lifecycle-Test ersetzte für jede Fixture per `Unsafe` den statischen finalen Clientcache `Module.mc`. Der produktive Supplier aus `Module.enableSmartDisable` konnte dadurch nach Kompilation einen früheren Client beobachten. Mit Temurin 8u482, `-Xcomp` und `-XX:CompileCommand=compileonly,me/youngjibbit/module/Module.lambda$enableSmartDisable$0` scheiterten **dieselben sieben von zehn Tests sowohl gegen den unveränderten Baseline-JAR als auch gegen den Kandidaten**. Der bisherige Kandidatentest bestand mit `-Xint` alle zehn Fälle. Der Fehler ist damit unabhängig von den neuen Render-/Diagnostikänderungen gezielt reproduziert; ein bloßer Erfolg in einem kalten JVM-Prozess hatte ihn zuvor verdeckt.

Die Korrektur ändert nur `SmartDisableLifecycleTest`: Das synthetische `Guarded`-Modul erhält den bereits vorhandenen produktiven `ModuleSmartDisable(Module, Supplier<Minecraft>)` mit seinem eigenen Client. Die Instanz wird ausschließlich im Test in das nichtfinale Instanzfeld `Module.smartDisable` eingesetzt. Eine kleine eigene Fixture ersetzt die breitere Fixture, die den globalen Clientcache und weitere RuntimeServices neu band. Die Minimalfixture behält Player-/World-/Connection-Identitäten, Mainthread-Erkennung und die bisherige `isEntityAlive`-Semantik bei.

Der unabhängige Review hat alle zehn ursprünglichen Testmethoden und jede bisherige Assertion erhalten gefunden. `Module.setToggled`, `Module.onEnable`/`onDisable`, die produktive SmartDisable-Steuerung, Eventpriorität, Cleanup, verschachtelte Events und Configreset werden weiterhin ausgeführt. Es gibt keine direkten Testaufrufe von `ModuleSmartDisable.enable` oder `disable`, die die Module-Anbindung umgehen. Der vorhandene Vier-Modul-Test ruft über die realen Konstruktoren weiterhin das produktive `enableSmartDisable` auf und prüft die angehängten Opt-in-Einstellungen. Der Lifecycle-Test beansprucht ausdrücklich keinen Nachweis für das nachträgliche Neubinden eines statischen finalen Produktionscaches.

Ein zusätzlicher elfter Test aktiviert zwei echte Guarded-Lifecycles mit getrennten Clients. Der Tod des ersten Players stoppt nur dessen Modul, der zweite erst beim Tod seines eigenen Players. Nach Cleanup müssen globaler Minecraft-Singleton, `Module.mc` sowie die ursprünglichen World-/Player-Referenzen unverändert sein. Der Produktionsdiff für `Module.java` und `ModuleSmartDisable.java` gegenüber der Baseline ist leer; weder Gameplay noch die produktive SmartDisable-Funktion wurden geändert. Der vollständige CI-Lauf bleibt als Integrationsnachweis erforderlich.

| Fixture und JVM-Modus | Baseline-JAR | Kandidatenklassen |
|---|---|---|
| Ursprüngliche Fixture, `-Xcomp` mit produktivem Supplier als Compile-Ziel | 10 Tests, dieselben 7 Failures | 10 Tests, dieselben 7 Failures |
| Ursprüngliche Fixture, `-Xint` | Nicht Bestandteil dieses Interpreter-Reproducers | 10/10 bestanden |
| Korrigierte Fixture, normal | 11/11 bestanden | 11/11 bestanden |
| Korrigierte Fixture, `-Xint` | 11/11 bestanden | 11/11 bestanden |
| Korrigierte Fixture, `-Xcomp` mit produktivem **und neuem Fixture-Supplier** als Compile-Ziele | 11/11 bestanden | 11/11 bestanden |

Für die letzten beiden JIT-Läufe wurde auf Reviewanforderung zusätzlich `-XX:CompileCommand=compileonly,me/youngjibbit/core/network/SmartDisableLifecycleTest$Guarded.lambda$new$0` gesetzt. Beide `CompilerOracle`-Zeilen sind im Rohoutput enthalten. So wird auch der tatsächlich neu benutzte Supplier unter erzwungener Kompilation geprüft. Die sechs korrigierten Läufe waren jeweils frische Prozesse; sie sind sechs Ausführungen derselben elf Fälle und keine 66 unterschiedlichen Tests. `Module.class` und `ModuleSmartDisable.class` sind zwischen dem Baseline-JAR und den verwendeten Kandidatenklassen jeweils SHA-256-identisch.

Alle ursprünglichen Reproducer und sechs finalen Fixläufe einschließlich vollständiger Rohoutputs, JVM-Optionen, Klassen-/Quell-/Loghashes sind in [SmartDisable-JIT-Fixture-Evidenz](evidence/core-2026-11/smart-disable-jit-fixture.json) abgelegt. Die [Diagnostikprovenienz](evidence/diagnostics-2026-11/provenance.json) verknüpft den Nachtrag mit dem unabhängigen Review. Dieser gezielte Befund ist geschlossen; der unterstützte vollständige CI-Lauf zur abschließenden PR-Revision bleibt offen.

## 5. CI- und Abnahmegrenzen

Der Render-Performance-Workflow erhält zusätzliche Pfadfilter für Corediagnostik, Minecraft-Render-/Chunkpfade, OptiFine und relevante Tests. Sein bestehendes RGBA-Gate bleibt erhalten. Die synthetischen Zeitmessungen mit erzwungenem GPU-Abschluss bleiben als solche gekennzeichnet; sie stellen keinen neuen Hardware-FPS-Nachweis dar.

Der Native-Runner versucht nun zwölf getrennte JVM-Suites: die bisherigen acht UI-/Font-/ESP-Suites sowie Shader-State-Bridge, World-Overlay-Bridge, GPU-Queries und Capture-Orchestrierung. Die neuen Proben müssen ihre eigenen Erfolgsmarker liefern; World-Overlay-Pixelbilder werden mitgesichert. Bestehende Diagnostikfehler- und Bildgates werden nicht gelockert. Der erwartete WARN-Filter der Invalidierungsfixture ist eng auf Logger, Level und den exakt provozierten Text begrenzt, zählt genau zwei Meldungen und wird in `finally` entfernt. Andere Warnungen/Fehler bleiben vom vorhandenen Gate erfasst.

| Erforderlicher Nachweis | Stand bei diesem Review |
|---|---|
| Exaktes Baselineartefakt und isolierter Cachebenchmark | Vorhanden; Benchmarkaussage auf Cache-Fixture beschränkt |
| Reine Diagnose-/Scheduler-/Ressourcenlogik | Gezielte Tests bestanden; konkrete Klassen und Grenzen oben bzw. im Corebericht |
| Vollständiger kombinierter finaler Java-8-Build/Test | Integrator/CI muss finale Revision festhalten; lokaler Full-Suite-Fehler bleibt offen |
| Native GL-/Pixel-/Query-/Unload-/Capture-Gates | Lokal vor Kontextaufbau blockiert; CI-Ergebnisse fehlen noch |
| Reale Welt mit VBO und Display-List, Render Regions, Transparenz, Teleport, Reload, Unload/Rejoin | Offen |
| Externe Shaderpacks und gleichzeitige Clienteffekte mit Originalpresets und Bildern | Offen; Matrix im Shaderbericht |
| Zielhardware, vollständige Frameverteilung und Chunkfortschritt | Offen |
| Regulärer Merge über PR/CI nach `main` | Vom Nutzer ausdrücklich autorisiert; Durchführung und finale CI-Revision durch den Integrator festzuhalten |
| Hardware-/Spiel-/Shaderpack-Abnahme nach Merge | Transparent offen; gemäß Nutzerentscheidung keine zusätzliche Draft-Sperre |
| Allgemeine Performancebehauptung | Mangels Zielhardware-/Spielmessung nicht belegt |

## 6. Noch benötigte vergleichbare Spielmessung

Die [Abnahmeanleitung](CORE-RENDER-ACCEPTANCE-2026-11.md) definiert die Szenen. Für jede primäre Szene sind mindestens drei gepaarte 120-Sekunden-Läufe mit dokumentiertem Warmup und wechselnder Baseline-/Kandidatenreihenfolge vorgesehen. Weltzustand, Route/Kamera, Renderdistanz, Auflösung/Scale, Ressourcen-/Shaderpack einschließlich Hash/Optionen, Clientmodule, VSync, Framelimit und Java-/Heap-/GC-Konfiguration bleiben innerhalb eines Paars gleich. Beide Varianten erhalten dieselbe Messinstrumentierung; ein Instrumentierungspatch zur Baseline ist gesondert zu versionieren.

Jeder Lauf behält Rohintervalle, CPU-/GPU-/Chunk-CSV, JSON-Metadaten, Artefakthash und Ausschlussgründe. Offene, verworfene oder abgebrochene Messungen bleiben sichtbar. GPU-Rohzeiten werden mit Kontext/Vendor/Driver beschrieben; softwaregerenderte CI-Zeiten repräsentieren keine Ziel-GPU. Die 300–400-FPS-Ambition entspricht je benannter Konfiguration 3,33–2,50 ms pro Frame und ist hier ein Ziel, kein Ergebnis.

Frame-p50/p95/p99, 1%-Low und lange Frames sind gemeinsam mit Chunk-Queuezeit, Mesh-/Uploadzeit, abgeschlossenen Chunkaufgaben und Datenübergabe bis zur Draw-Submission zu betrachten. Ein besseres Frame-p95 mit stehendem Chunkfortschritt genügt nicht. Bildqualität, Tiefen-/Transparenz-/Schattenverhalten, Eingabereaktion sowie Ressourcen nach Reload und Unload müssen ebenfalls bestehen. Erst diese final-revisionsbezogenen Nachweise erlauben eine belastbare Bewertung des gesamten Clients.
