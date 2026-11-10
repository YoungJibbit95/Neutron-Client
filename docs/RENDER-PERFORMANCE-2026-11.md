# Render-Performance: November 2026

**Stand:** 10.11.2026

**Vergleichsbasis:** `2508562100a66480c639e2c01dfdffb004d98008` (`main` vor diesem Lauf)

**Stack:** Minecraft 1.8.9 / OptiFine, LWJGL 2, Temurin 8u482, Maven 3.9.15

**Abnahmestand:** Lokale Kompilation und finale fokussierte Regression erfolgreich;
vollständige Windows-Regression und native Bild-/Zeitvergleiche noch **PENDING**.

Dieser Lauf reduziert wiederholte CPU-Arbeit, Allokationen und redundante
Framebuffer-Bindungen im vorhandenen Renderer. Blur-/Glow-Algorithmen, UI-Design
und Qualitätsparameter bleiben bestehen. Die ältere
[Performance-Roadmap](PERFORMANCE-ROADMAP.md) ist historischer Planungskontext;
ihre damaligen Quellenbefunde und Teststände gelten nicht automatisch für diese Basis.

## Befunde und Umsetzung

Die folgenden Ursachen wurden am Code nachvollzogen. Eine entfernte Operation
ist ein struktureller Befund; ihre Wirkung auf ganze Spielframes muss gemessen werden.

| Problem an der Vergleichsbasis | Umsetzung | Maßgebliche Quelle |
| --- | --- | --- |
| Wiederholte Theme-ID-Auflösung und Klonen des Preset-Arrays bei Renderabfragen | Aktuelles unveränderliches Theme bei Katalogmutationen auflösen; Preset-Liste intern wiederverwenden. Preview, Save, Delete und unbekannte IDs behalten ihre Regeln. | [ThemeCatalog](../src/main/java/me/youngjibbit/theme/ThemeCatalog.java) |
| Neue Gradientobjekte und wiederholte GUI-Dimensionsberechnung pro Shape | Begrenzter Gradient-Cache mit Options-, Theme-, Zeit- und Phaseninvalidierung; gemeinsame GUI-Auflösung mit Prüfung von Client, Displaygröße, GUI-Scale und Unicode-Modus bei jedem Zugriff. | [UiEffects](../src/main/java/me/youngjibbit/utils/render/UiEffects.java), [GuiResolution](../src/main/java/me/youngjibbit/utils/render/GuiResolution.java) |
| Jeder vollständige GL-State-Scope erzeugt eigene direkte Snapshot-Buffer | Abgeschlossene CPU-Speicherblöcke pro Thread wiederverwenden, höchstens 16 ungenutzte Blöcke behalten; gleichzeitig aktive Scopes besitzen getrennte Snapshots. | [RenderState](../src/main/java/me/youngjibbit/utils/render/RenderState.java), [RenderStateBuffers](../src/main/java/me/youngjibbit/utils/render/RenderStateBuffers.java) |
| Primitive Arrays für feste Uniformargumente und eine zusätzliche Projektions-Ergebnisarray pro Bloom-Rechteck | Feste Uniform-Overloads; direkt in das bestehende Rechteckpayload projizieren. Gewöhnliche Shader sparen Varargs-Allokationen; Custom-Subclass-Overrides bleiben erreichbar. | [ShaderUtil](../src/main/java/me/youngjibbit/utils/render/ShaderUtil.java), [GuiShaderBloom](../src/main/java/me/youngjibbit/utils/render/GuiShaderBloom.java), [ScreenRect](../src/main/java/me/youngjibbit/utils/render/ScreenRect.java) |
| Gaussian-Kernel wird bei wechselnden Radien erneut berechnet und bei jedem Pass erneut hochgeladen | Exakt gleiche Kernel für Radien 1–127 lazy speichern; jedes private Shaderprogramm lädt seine Gewichte nur bei Radiuswechsel oder nach Neuerstellung. | [GaussianBlur](../src/main/java/me/youngjibbit/utils/render/shader/blur/GaussianBlur.java), [GaussianKernelCache](../src/main/java/me/youngjibbit/utils/render/shader/blur/GaussianKernelCache.java) |
| Clear bindet ein FBO, löst es und der unmittelbar folgende Pass bindet es erneut | `bindAndClear()` lässt das Ziel gebunden; bestehendes `framebufferClear()` bindet weiterhin anschließend FBO 0. Die bisherigen Bloom-Depth-Attachments und ihr Zustandsverhalten bleiben erhalten. | [Framebuffer](../src/main/java/net/minecraft/client/shader/Framebuffer.java), [KawasePass](../src/main/java/me/youngjibbit/utils/render/shader/KawasePass.java), [KawaseBloom](../src/main/java/me/youngjibbit/utils/render/shader/KawaseBloom.java) |
| Ein Compatibility-Treiber kann beim Attribute-Pop einen inzwischen gelöschten Texturnamen neu anlegen; eine nachträgliche Existenzprüfung hält ihn dann für gültig | Gespeicherte Textur-IDs vor dem Pop prüfen und ausschließlich vorher ungültige Namen anschließend bereinigen. Die Anzahl der Existenzabfragen bleibt gleich, gültige Texturen und verschachtelte Scopes bleiben erhalten. | [RenderState](../src/main/java/me/youngjibbit/utils/render/RenderState.java), [SharedResourceCheck](../src/test/java/me/youngjibbit/module/impl/render/SharedResourceCheck.java) |
| Wiederholte Metrik-/Upload-Bereitschaftsprüfung kurzer Labels; neuer direkter Pixelbuffer pro kalter Glyphe | Feste Text-Caches mit je 64 Sets × 4 Slots und primitiven Wert-/Nutzungsarrays; keine Map-Knoten oder geboxten Breiten bei Verdrängung. Wiederverwendbarer Uploadbuffer; Alpha direkt aus dem unveränderten Java2D-ARGB-Raster lesen. | [TTFFontRenderer](../src/main/java/me/youngjibbit/utils/font/TTFFontRenderer.java), [FontTextCache](../src/main/java/me/youngjibbit/utils/font/FontTextCache.java) |
| Kopierte Render-, GL- und Farbhelfer mit auseinanderlaufender Wartung | Bestehende Einstiegspunkte delegieren an gemeinsame Implementierungen. Abweichende Altverträge bleiben ausdrücklich erhalten. | [RenderUtil](../src/main/java/me/youngjibbit/utils/RenderUtil.java), [GLUtils](../src/main/java/me/youngjibbit/utils/render/GLUtils.java), [ColorWheel](../src/main/java/me/youngjibbit/utils/render/ColorWheel.java) |

Der Gaussian-Cache benötigt über alle 127 Radien höchstens 33.020 Byte direkte
Koeffizientendaten. `bindAndClear()` entfernt zwei FBO-Bindungen je umgestelltem
Clear-/Draw-Paar; bei einem Bloom-Batch mit zwei Pyramidenstufen und einer Maske
sind das zehn Bindungen. Das sind aus dem Aufrufpfad abgeleitete Einsparungen,
keine gemessenen GPU-Zeiten. GLSL-Samplezahl und Renderauflösung wurden nicht reduziert.

Ein erster Versuch entfernte Bloom-Depth-Attachments und schaltete den Tiefentest
nach der Zielanlage ausdrücklich aus. Der native Vergleich fand damit sichtbare
Abweichungen in den vier geclippten Bloom-Aufnahmen; die übrigen zwölf Aufnahmen
waren bereits bytegleich. Dieser Versuch wurde zurückgenommen, um das bisherige
Kompositionsverhalten zu bewahren. Eine daraus erhoffte GPU-Speicherersparnis ist
kein Bestandteil des verbleibenden Performancegewinns.

## Verbindliche Erhaltungsverträge

- **Bild und Komposition:** Gleiche Radien, Samples, Passreihenfolge, Filter,
  Alpha-/Blend-Rechnung, UV-Ursprünge, Clipgrenzen und Themephasen. Der Legacy-Menüpfad
  behält seine abgerundete Breite und gebrochene Höhe. Kein reduziertes Effektpreset.
- **GL-Zustand:** Bestehende State-Abfragen und Restores bleiben erhalten;
  Raw GL und `GlStateManager` werden weiterhin synchronisiert. Buffer-Reuse ist
  Speicherwiederverwendung, kein globaler Cache vermeintlich aktueller GL-Zustände.
  Verschachtelte Scopes und Fehlerpfade müssen ihre eigenen Eintrittszustände herstellen.
- **Lebensdauer:** Keine GL-Arbeit auf Worker-Threads. Ressourcen bleiben ihrem
  Renderthread und Owner zugeordnet. Programmfreigabe invalidiert den Gaussian-Uploadstand;
  Resize und Ressourcenfreigabe dürfen keine alten FBOs oder Texturen weiterverwenden.
- **API und Schrift:** Alte Overloads, Varargs-Overrides und Legacy-Facades bleiben
  verwendbar. Fonts, Rasterisierung, Antialiasing, Glyph-Advances, Formatcodes,
  Unicode-/Fallback-Regeln und Atlasgrenzen bleiben bestehen. Fontmetriken bleiben
  ohne GL-Kontext nutzbar; Schließen gibt gespeicherte Texte und Uploadspeicher frei.
- **Invalidierung:** Einstellungsänderungen, Theme-Preview/-Wechsel, getrennte
  Gradientkanäle, Reduced Motion, GUI-Scale, Sprache und Displaygröße dürfen auch
  zwischen zwei Draws desselben Frames keine veralteten Ergebnisse liefern.

Die nativen Tests prüfen diese Verträge für ihre isolierten Szenen. Eine vollständige
Display-/Shaderpack-Abnahme ist davon getrennt und wird nicht durch statische Analyse ersetzt.

## Bisher gemessen: UI-Vorbereitung auf der CPU

Die [CPU-Rohdaten](performance/2026-11-10-cpu-results.json) enthalten sämtliche
hier verwendeten JVM-Läufe, Parameter, Checksummen, die Hardwareangabe und
SHA-256-Quellprüfsummen der finalen Fontimplementierung.

[UiPreparationBenchmark](../src/test/java/me/youngjibbit/utils/render/UiPreparationBenchmark.java)
misst eine Vorbereitung aus **einem echten `Theme.getDefaultTheme()` und den drei
Gradientabfragen TEXT/BORDER/GLOW**. Pro simuliertem Frame erfolgen 250 Vorbereitungen
und ein `beginFrame()` mit synthetischer Uhr. Je frischer JVM: 1.000 Warmup-Frames,
danach 4.000 Messframes beziehungsweise 1.000.000 Vorbereitungen. Baseline und
Änderung wurden in wechselnder Reihenfolge jeweils fünfmal gestartet.

Umgebung: Linux x86_64, AMD EPYC 9V74, Temurin `1.8.0_482-b08`. Allokationen werden
über `ThreadMXBean` auf dem Mess-Thread erfasst. GPU, Java2D-Glyphrasterisierung,
OpenGL-Submission, Welt und echtes Display sind nicht Teil dieser Messung.

| JVM-Lauf | Baseline ns/Vorbereitung | Änderung ns/Vorbereitung |
| --- | ---: | ---: |
| 1 | 86,758419 | 35,862374 |
| 2 | 103,319963 | 39,417007 |
| 3 | 91,136323 | 36,899746 |
| 4 | 82,531258 | 38,002772 |
| 5 | 90,439722 | 35,821583 |
| **Median** | **90,440** | **36,900** |

Allokation in jedem Lauf: **192,896 → 0,256 Byte/Vorbereitung**. Die Checksummen
stimmen in allen zehn Läufen überein (`97864929906090750`). Der CPU-Median dieser
Methodenszene sinkt um rund 59 %. Das ist keine Aussage über 59 % höhere Gesamt-FPS;
bei 250 Vorbereitungen entspricht die Differenz rechnerisch nur ungefähr
0,0134 ms pro simuliertem Frame. Die zusätzliche Entlastung anderer Pfade wird
hierdurch nicht mitgemessen.

## Finale Fontmessung: feste Cache-Slots

[FontMetricsBenchmark](../src/test/java/me/youngjibbit/utils/font/FontMetricsBenchmark.java)
misst ausschließlich `getWidth()` mit vorab erzeugten Eingabetexten und
`Font.DIALOG`, Plain, Größe 20, ohne OpenGL. Je Variante wurden fünf frische JVMs
in wechselnder Reihenfolge gestartet. Ein JVM-Messwert ist der Median aus sieben
Runden mit je 250.000 Aufrufen nach 250.000 Warmup-Aufrufen. Beim 300 Zeichen langen
Text sind es 25.000 Aufrufe je Runde und 100.000 Warmup-Aufrufe. Die Tabelle zeigt
den Median und die Spannweite dieser fünf JVM-Messwerte, keine Aufrufperzentile
oder Konfidenzintervalle.

| Szenario | Baseline Median ns/Aufruf | Änderung Median ns/Aufruf | Baseline-Spannweite ns | Änderungs-Spannweite ns |
| --- | ---: | ---: | ---: | ---: |
| 24 wiederholte UI-Labels | 139,884 | 13,409 | 124,482–210,081 | 6,921–15,357 |
| 192 wechselnde HUD-Labels | 315,555 | 139,479 | 188,102–362,633 | 137,101–148,158 |
| 512 Labels über Cachekapazität | 321,396 | 251,553 | 192,069–335,439 | 246,720–259,973 |
| Langer Text ohne Cacheaufnahme | 3.572,728 | 2.486,803 | 2.007,437–3.731,327 | 2.406,267–2.870,420 |

Die 24- und 192-Label-Szenarien waren in allen fünf Paaren schneller. Bei
512 Labels und langem Text war die Änderung trotz niedrigerem Median in jeweils
**zwei von fünf Paaren langsamer**. Diese Szenarien belegen deshalb keinen
durchgehend stabilen Zeitgewinn. Lange Texte umgehen den Cache; ihre niedrigere
Medianzeit wird nicht dem Cache zugeschrieben.

Die endgültige Konstruktion begrenzt jeden Cache auf 256 Einträge, prüft innerhalb
eines Sets bis zu vier vollständige Stringschlüssel und verdrängt dort den
am längsten ungenutzten Slot. Breiten und Nutzungsstände liegen in primitiven
Arrays. Auch Cache-Churn erzeugt dadurch keine Map-Knoten oder `Float`-Objekte;
die Erzeugung neuer Eingabestrings bleibt Sache des Aufrufers. Texte über
256 UTF-16-Einheiten werden nicht gehalten; Schließen leert beide Caches.

Die Änderung misst in allen Fontläufen **0 Byte/Aufruf** auf dem Java-Heap des
Mess-Threads. Bei wiederholten Labels beträgt der Baseline-Median 6,667 Byte
(vier Läufe 6,667, ein Lauf 0); in den übrigen Szenarien misst auch die Baseline 0.
Vorab erzeugte Strings, Fontkonstruktion sowie nativer und GPU-Speicher sind
ausgeschlossen. Alle zehn Checksummen sind identisch (`1060823331.548`).
Es werden ausschließlich Messungen der finalen Cachekonstruktion berichtet.

## Teststand und verbleibende Nachweise

| Prüfung | Stand dieses Dokuments |
| --- | --- |
| Lokale Source-/Test-Kompilation mit Temurin 8u482 und Maven 3.9.15 | **PASS** auf der finalen Fontimplementierung einschließlich aller Harnessquellen; Maven `BUILD SUCCESS`. |
| Fokussierte JUnit-Regression | **46/46 PASS**, 0 Failures, 0 Errors, 0 Skips: FontTextCache 4, GuiResolution 2, RenderStateBuffers 4, GaussianKernelCache 3, ColorUtilityCompatibility 2, UiEffects 12, ThemeModeApproachTwo 3, ThemeUiConsumer 3, ThemeModel 13. |
| Gaussian-Koeffizienten | **PASS:** 8.255 Koeffizienten aller 127 Radien bitidentisch zur bisherigen Formel; Bereichs- und Buffer-Reuse-Prüfung erfolgreich. |
| Vollständige lokale Linux-Suite | **NICHT GRÜN:** 1.963 Tests, 4 Failures, 276 Errors, 3 Skips. Viele subprocess-basierte Tests erwarten `java.exe`; drei Diagnostics-Assertions sind ebenfalls fehlgeschlagen. |
| Linux-Font-/Emoji-Assertion | Die Failure in `FontAtlasMetricsTest`, Zeile 27, wurde auf Baseline und Änderung reproduziert: jeweils 4 Tests, 1 Failure. Dieser Befund ist kein grüner Gesamtlauf. |
| Vollständige Java-8-/Windows-Regression | **PENDING**, maßgeblich über [java8-pr.yml](../.github/workflows/java8-pr.yml). Keine Tests zur Verbesserung des Ergebnisses deaktivieren. |
| Native RGBA-, GL-State- und Ressourcenvergleiche | **PENDING** über [RenderPerformanceBenchmark](../src/test/java/me/youngjibbit/module/impl/render/RenderPerformanceBenchmark.java) und den nativen Workflow. |
| Native Submission-/Completed-Zeiten p50/p95/p99 | **PENDING**; nach Abschluss mit exakten Revisionen, GL-Renderer und Artefaktlink ergänzen. |
| Reale Gesamt-FPS, Welt-/Client-Ladezeit, Fullscreen und Shaderpacks | **Nicht gemessen** in diesem Lauf. |

Separate fokussierte Läufe ergaben ebenfalls **4/4 FontTextCache-Tests** und
**35/35 UI-Tests**. Diese Mengen überlappen die 46 Tests und werden nicht addiert.
Der native Runner ist mit Java 8 kompiliert; auch die Baseline-Kompilation aller
2.386 Originalquellen mit 15 materialisierten Overlaydateien wurde geprüft.
**36 Python-Toolingtests sind grün**, und das gepinnte Linux-Native-Archiv wurde
geprüft. Diese Vorbereitungsprüfungen ersetzen die noch ausstehende GL-Ausführung
in GitHub Actions nicht.

[ShaderPerformanceCheck](../src/test/java/me/youngjibbit/module/impl/render/ShaderPerformanceCheck.java)
vergleicht unter anderem Gaussian-Pixel gegen einen unabhängig berechneten Referenzpfad,
feste/Array-Uniforms und Subclass-Overrides, Clear-RGBA/Depth/Viewport, verschachtelte
State-Snapshots und fehlschlagende Bloom-Batches.
[LegacyRenderFacadeCheck](../src/test/java/me/youngjibbit/module/impl/render/LegacyRenderFacadeCheck.java)
prüft delegierte Render- und GL-Verträge. Vorhandene Font-, UI-State-, Effekt-,
Kompositions- und Ressourcenprüfungen ergänzen diese Tests.

## Reproduzierbare Abnahme

Im vorgesehenen Checkout mit sauber erfasster Revision und Java 8 arbeiten;
keine persönlichen Welten, Accounts oder Laufzeitordner kopieren. Die folgenden
Shellbeispiele sind für Linux/CI; die vollständige Windows-Suite nutzt den bestehenden Workflow.

```bash
git rev-parse HEAD
java -version
mvn --version
mvn -B -ntp -DskipTests test-compile
mvn -B -ntp -Dtest=GuiResolutionTest,GaussianKernelCacheTest,RenderStateBuffersTest,FontTextCacheTest,UiEffectsTest,ThemeModelTest,ColorUtilityCompatibilityTest,ThemeUiConsumerTest,ThemeModeApproachTwoTest test
mvn -B -ntp dependency:build-classpath -Dmdep.outputFile=target/render-classpath.txt
```

Für den nativen Vergleich benötigt die Umgebung LWJGL-Linux-Natives, Mesa/GLX und
Xvfb. Der [Runner](../.github/scripts/check_render_performance.py) kompiliert ein
Baseline-Quelloverlay aus Git und verwendet denselben Benchmark für beide Fassungen.
Dabei müssen auch die ursprünglichen Aufrufer neu kompiliert werden, damit neue
Uniform-Overloads nicht gegen alte Klassen gelinkt werden. Der
[Workflow](../.github/workflows/render-performance.yml) hält Einrichtung und Aufruf fest:

```bash
xvfb-run -a -s '-screen 0 1280x960x24 +extension GLX +render -noreset' \
  python3 .github/scripts/check_render_performance.py \
  --base 2508562100a66480c639e2c01dfdffb004d98008 \
  --classpath target/render-classpath.txt \
  --output target/render-performance
```

Verglichen werden feste Text-/UI-, geclippte Theme-/Fixfarben-Bloom-, Gaussian-
und Glass-Szenen bei 960×640 mit GUI-Scale 2 sowie 854×480 mit GUI-Scale 1.
Vier Szenen, zwei Auflösungen und jeweils erster sowie warmer Capture ergeben
**16 exakte RGBA-Vergleichspaare**. Jeder Capture muss sich vom unmittelbar zuvor
gerenderten Hintergrund unterscheiden. Szenenabhängige Blur-, Masken-, Composite-
und Glyphbatch-Zähler müssen tatsächlich ausgeführte Arbeit bestätigen; warme
Glyphuploads dürfen null sein. Das verhindert eine scheinbare Bildparität durch
zwei wirkungslose Renderpfade.
RGBA-Vergleiche, GL-Fehler und Ressourcenverträge sind separat von Zeitmessungen
auszuwerten. Die Current-Suite führt die zusätzlichen Regressionen aus;
Warmup, Wiederholungen und Ausführungsreihenfolge müssen in den Artefakten erkennbar bleiben.

`submission` erfasst CPU-Abgabe an den Treiber. `completed` schließt das im Harness
benutzte `glFinish()` ein und beschreibt eine isolierte, fertig gerenderte Szene.
Beides ist weder ein GPU-Timerquery noch die Framezeit des laufenden Spiels.
Software-Mesa-Ergebnisse lassen sich nicht als Gewinn einer bestimmten Spieler-GPU ausgeben.
Für Gesamt-FPS und Ladezeiten sind anschließend identische Welt, Settings, Auflösung,
VSync-/FPS-Limit, JVM, Shaderpack und Hardware erforderlich; mehrere Läufe mit
p50/p95/p99 und GC-/Allokationsdaten bilden den Vergleich.

Vor dem Merge müssen die für die PR erforderlichen CI- und nativen Prüfergebnisse
auf ihrer tatsächlich zu integrierenden Revision vorliegen. Dieses Dokument ersetzt
weder fehlende Ergebnisse noch eine offene Prüfung durch eine FPS-Prognose.
