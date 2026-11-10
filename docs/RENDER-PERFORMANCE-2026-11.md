# Render-Performance: November 2026

**Stand:** 10.11.2026

**Gemessener und geprüfter Quellstand (PR-Head):** `a786a0cb7b477426ea92e1802b16556bc2f923dc` ([PR 176](https://github.com/YoungJibbit95/Neutron-Client/pull/176))

**Integrierte native Vergleichsbasis:** `ff11e9746521e65ab67d5e70789684d06c7c1edd`

**Tatsächlich ausgeführter GitHub-Merge:** `1474bf35592f9c13ecfa1fa560dc60de0380a444`

**Historische CPU-Vergleichsbasis:** `2508562100a66480c639e2c01dfdffb004d98008` (vor der Main-Integration)

**Stack:** Minecraft 1.8.9 / OptiFine, LWJGL 2, Temurin 8u482, Maven 3.9.15

**Abnahmestand dieses Quellstands:** Beide vollständigen Windows-/Release-Regressionen,
die isolierte JAR-Prüfung, vier native JVM-Messpaare mit **64/64 bytegleichen RGBA-Vergleichen**
und vier Legacy-UI-Suites sind erfolgreich. Die unveränderten Java-Quellen wurden
zuvor zusätzlich mit 82 lokalen Fokustests geprüft.

**Gemessenes Ergebnis:** Die Java-Heap-Allokation der acht nativen Szenen sinkt
um 86,4–95,3 % im Median der gepaarten Vergleiche. Ein allgemeiner Zeitgewinn
ist nicht belegt: Die getrennten Blur-/Bloom-Phasen bleiben weitgehend gleich,
UI/Text bei 960×640 und der Gaussian-Mischmedian bei 854×480 sind in allen vier
Paaren langsamer. Zusätzlich verschlechtern sich die langsamen Frames in diesen
Szenen; UI-p99 und Radius-7-p95 liegen jeweils in allen vier Paaren höher.
Gesamt-FPS, Spielframe-Stabilität und Ladezeiten wurden nicht gemessen.

Dieser Lauf reduziert wiederholte CPU-Arbeit, Allokationen und redundante
Framebuffer-Bindungen im vorhandenen Renderer. Blur-/Glow-Algorithmen, UI-Design
und Qualitätsparameter bleiben bestehen. Die ältere
[Performance-Roadmap](PERFORMANCE-ROADMAP.md) ist historischer Planungskontext;
ihre damaligen Quellenbefunde und Teststände gelten nicht automatisch für diese Basis.

Bei der Integration des zwischenzeitlich weiterentwickelten `main` blieben die
38 Dateien der Legacy-UI-Arbeit bytegenau zum integrierten Main-Stand erhalten.
Die aktuelle Abnahmetabelle gehört zum oben genannten PR-Head und seiner
GitHub-Merge-Revision. Gegenüber dem ersten integrierten Stand `05cddf43` wurden
nur der Performance-Runner, seine Tests, sein Workflow und die Dokumentation
geändert; Production-Java, Java-Harness und Legacy-UI-Gate blieben bytegleich.
Die früheren CPU-Mikrobenchmarks und der erste native Einzelvergleich bleiben
mit ihren ursprünglichen Revisionen als historische Evidenz erhalten.

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

## Abschließender nativer Vergleich: vier balancierte JVM-Paare

[Native-CI-Run 42905778062](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42905778062)
vergleicht die Basis `ff11e9746521e65ab67d5e70789684d06c7c1edd` mit dem getesteten
Merge `1474bf35592f9c13ecfa1fa560dc60de0380a444` zum PR-Head `a786a0cb`.
Die [vollständigen Vierpaar-Rohdaten](performance/2026-11-10-native-four-pairs.json)
erhalten jedes Feld und sämtliche Samples des ursprünglichen `comparison.json`.
Provenienz sowie ergänzende Font- und Tail-Auswertungen sind separat gekennzeichnet.
Das 20.668.487 Byte große CI-Archiv wurde gegen seinen GitHub-SHA-256-Digest geprüft.

Je Variante liefen vier frische JVMs mit **60 Warmup- und 90 Messframes je Szene**.
Die vier Startreihenfolgen waren **A→B, B→A, A→B, B→A**; A bezeichnet die Baseline,
B die Änderung. Jedes Paar bestand seine 16 exakten RGBA-Vergleiche, insgesamt
**64/64**. Beide zusätzlichen Native-Contracts und beide Font-Probes bestanden.
Alle acht Render-JVMs melden dieselbe Umgebung: Temurin `1.8.0_482-b08`, Linux amd64,
Mesa 25.2.8, llvmpipe mit LLVM 20.1.2 und OpenGL 4.5 Compatibility. Der Runner setzt
`LP_NUM_THREADS=2`. 960×640 verwendet GUI-Scale 2, 854×480 GUI-Scale 1.

### Definition der Messwerte

`submission` misst die CPU-Abgabe an den Treiber; `completed` schließt `glFinish()`
ein. Hintergrundaufbau, Readback und Dateiausgabe liegen außerhalb dieser Zeiten.
Innerhalb jeder JVM werden p50/p95/p99 mit nearest rank aus den unveränderten
Samples bestimmt. Anschließend werden die vier JVM-p50 je Variante mit Median
und Min/Max beschrieben. Frames verschiedener JVMs werden nicht zusammengeworfen.

Die Zeitpfeile zeigen die Mediane A→B. Die vier Prozentwerte gehören zu Paar
1/2/3/4 und berechnen `100 × (B/A − 1)` innerhalb desselben Paares; ein Plus
bedeutet längere Zeit. Ein „gepaarter Median“ ist der Median dieser vier Änderungen,
nicht der Quotient der beiden Variantenmediane. Spannweiten beschreiben die vier
beobachteten JVMs und sind keine Konfidenzintervalle. Es gibt kein Geschwindigkeits-Gate.

### Gesamtszenen: beide Zeitmetriken und alle vier Paare

**Zwei beobachtete Verluste bleiben bestehen:** UI/Text bei 960×640 hat einen
gepaarten Completed-Median von **+2,07 %**, Gaussian bei 854×480 von **+14,18 %**.
Beide sind in allen vier Paaren langsamer. UI/Text bei 854×480 ist im gepaarten
Completed-Median **3,95 % schneller**, aber in einem Paar langsamer. Die übrigen
Gesamtszenen wechseln teilweise die Richtung oder zeigen nur kleine Abweichungen.
Eine allgemeine Verbesserung der Renderzeit ist damit nicht belegt.

| Szene | Submission-p50 A → B, ms | Submission-Δ Paar 1/2/3/4, % | Completed-p50 A → B, ms | Completed-Δ Paar 1/2/3/4, % |
| --- | ---: | ---: | ---: | ---: |
| UI/Text, 960×640 | 1,688 → 1,675 | -1,15 / +5,51 / -5,92 / +3,29 | 7,513 → 7,657 | +1,02 / +10,23 / +2,05 / +2,10 |
| Geclipptes Bloom, 960×640 | 44,297 → 44,257 | +0,09 / +0,29 / -0,14 / -1,11 | 45,987 → 45,946 | +0,04 / +0,31 / -0,06 / -1,18 |
| Gaussian Blur, 960×640 | 30,797 → 30,452 | +2,27 / -5,51 / +6,72 / -4,74 | 32,629 → 32,104 | +2,26 / -7,99 / +6,24 / -5,60 |
| Glasfläche, 960×640 | 74,327 → 74,141 | +0,01 / -0,64 / -0,40 / -0,16 | 92,860 → 92,615 | +0,02 / -0,91 / -0,31 / -0,22 |
| UI/Text, 854×480 | 1,519 → 1,418 | -5,14 / -8,91 / -11,41 / +0,94 | 3,405 → 3,257 | -3,39 / -4,52 / -6,98 / +0,35 |
| Geclipptes Bloom, 854×480 | 29,552 → 29,455 | -0,01 / -2,68 / +0,45 / -0,17 | 30,679 → 30,571 | +0,08 / -2,46 / +0,41 / -0,29 |
| Gaussian Blur, 854×480 | 17,180 → 20,100 | +20,37 / +7,89 / +29,83 / +5,50 | 18,323 → 21,476 | +20,30 / +8,05 / +27,70 / +6,63 |
| Glasfläche, 854×480 | 29,680 → 29,663 | -0,30 / -0,21 / -0,16 / +0,42 | 34,656 → 34,646 | -0,31 / -0,17 / -0,13 / +0,39 |

Die Spannweiten der einzelnen JVM-p50 werden zusätzlich ausgewiesen:

| Szene | Submission A: Min–Max, ms | Submission B: Min–Max, ms | Completed A: Min–Max, ms | Completed B: Min–Max, ms |
| --- | ---: | ---: | ---: | ---: |
| UI/Text, 960×640 | 1,627–1,706 | 1,588–1,800 | 7,409–7,576 | 7,564–8,288 |
| Geclipptes Bloom, 960×640 | 44,139–44,390 | 43,897–44,321 | 45,836–46,123 | 45,579–46,051 |
| Gaussian Blur, 960×640 | 25,509–33,324 | 26,088–32,779 | 27,118–36,052 | 27,732–34,400 |
| Glasfläche, 960×640 | 74,171–74,592 | 74,073–74,175 | 92,689–93,422 | 92,571–92,708 |
| UI/Text, 854×480 | 1,418–1,579 | 1,380–1,438 | 3,255–3,469 | 3,227–3,293 |
| Geclipptes Bloom, 854×480 | 29,358–29,644 | 28,851–29,632 | 30,486–30,742 | 29,985–30,764 |
| Gaussian Blur, 854×480 | 17,092–18,602 | 18,531–22,310 | 18,217–19,729 | 19,759–23,443 |
| Glasfläche, 854×480 | 29,582–29,771 | 29,604–29,706 | 34,545–34,770 | 34,581–34,679 |

### Gleiche Arbeitsphasen: Gaussian-Radien und Bloom-Iterationen

Gaussian alterniert zwischen Radius 7 und 11, Bloom zwischen zwei und drei
Iterationen. Die ursprüngliche Reihenfolge wird nach geradem und ungeradem
Frameindex **vor** jeder Sortierung getrennt; jede Phase besitzt 45 Samples je JVM.
Der Gesamt-p50 einer gleich großen Mischung liegt am oberen Rand der schnelleren
Hälfte. Langsame Frames dieser Hälfte können ihn verschieben, obwohl sich die
Phasenmediane kaum ändern. Die Gesamtergebnisse bleiben deshalb daneben erhalten.

| Szene und Phase | Submission-p50 A → B, ms | Gepaarter Submission-Δ-Median, % | Completed-p50 A → B, ms | Completed-Δ Paar 1/2/3/4, % |
| --- | ---: | ---: | ---: | ---: |
| Geclipptes Bloom, 960×640, Iterationen 2 | 42,293 → 42,243 | -0,18 | 43,982 → 43,916 | -0,31 / -0,09 / +0,64 / -0,78 |
| Geclipptes Bloom, 960×640, Iterationen 3 | 44,524 → 44,526 | +0,03 | 46,235 → 46,270 | +0,08 / +0,09 / +0,32 / -0,80 |
| Gaussian Blur, 960×640, Radius 7 | 25,109 → 25,064 | -0,02 | 26,733 → 26,692 | 0,00 / -0,45 / -0,08 / +0,13 |
| Gaussian Blur, 960×640, Radius 11 | 35,143 → 35,151 | +0,04 | 36,793 → 36,797 | +0,22 / -0,30 / -0,13 / +0,29 |
| Geclipptes Bloom, 854×480, Iterationen 2 | 28,206 → 28,178 | +0,03 | 29,325 → 29,302 | +0,16 / +0,33 / -0,17 / -1,09 |
| Geclipptes Bloom, 854×480, Iterationen 3 | 29,818 → 29,793 | -0,01 | 30,949 → 30,933 | -0,05 / +0,28 / -0,07 / -1,05 |
| Gaussian Blur, 854×480, Radius 7 | 16,855 → 16,879 | +0,19 | 17,951 → 17,990 | -0,52 / +0,69 / -0,18 / +0,72 |
| Gaussian Blur, 854×480, Radius 11 | 23,648 → 23,642 | -0,02 | 24,768 → 24,768 | -0,72 / +0,27 / -0,26 / +0,50 |

Die gepaarten Completed-Phasenmediane liegen zwischen −0,20 % und +0,25 %.
Das beschreibt weitgehend unveränderte typische Blur-/Bloom-Zeiten in dieser
Umgebung. **Die langsamsten Frames sind damit nicht entlastet:** Die folgenden
Tail-Verluste sind tatsächlich gemessen. Die schlechteren Gaussian-Gesamtwerte
sind daher nicht allein als rechnerischer Medianeffekt abzutun.

### Langsame Frames: p95/p99 und Ausreißerhäufigkeit

UI/Text bei 960×640 hat in **allen vier Paaren höhere Completed-p99**
(+5,75 % bis +14,36 %, gepaarter Median **+8,36 %**). Gaussian bei 854×480 und
Radius 7 hat in **allen vier Paaren höhere Completed-p95** (+1,33 % bis +13,78 %)
und höhere p99 (+6,63 % bis +27,70 %). Die gepaarten Mediane sind dort
**+1,40 % für p95 und +14,18 % für p99**. Dies ist eine materielle Abnahmegrenze.

Die folgende Aufschlüsselung verwendet ausschließlich die vorhandenen Rohsamples.
UI hat 90 Samples je JVM, jede Gaussian-Phase 45. p99 ist bei diesen kleinen
Stichproben jeweils das Maximum; p95 der Gaussian-Phase ist der 43. sortierte Wert.
Es werden keine Frames ausgeschlossen oder Perzentile über JVMs gepoolt.

| Szene/Phase, Completed-Perzentil | Δ in Paar 1/2/3/4, % | Gepaarter Δ-Median, % |
| --- | ---: | ---: |
| UI/Text, 960×640, p95 | +0,72 / +5,74 / -7,18 / +1,79 | +1,25 |
| UI/Text, 960×640, p99 | +9,01 / +7,71 / +5,75 / +14,36 | +8,36 |
| Gaussian Blur, 960×640, Radius 7, p95 | +0,24 / -17,79 / -0,16 / -11,45 | -5,80 |
| Gaussian Blur, 960×640, Radius 7, p99 | +2,26 / -7,99 / +6,24 / -5,60 | -1,67 |
| Gaussian Blur, 960×640, Radius 11, p95 | +13,66 / -3,06 / +0,68 / -7,07 | -1,19 |
| Gaussian Blur, 960×640, Radius 11, p99 | +4,40 / -0,49 / +7,92 / -10,11 | +1,96 |
| Gaussian Blur, 854×480, Radius 7, p95 | +1,42 / +1,33 / +1,39 / +13,78 | +1,40 |
| Gaussian Blur, 854×480, Radius 7, p99 | +20,30 / +8,05 / +27,70 / +6,63 | +14,18 |
| Gaussian Blur, 854×480, Radius 11, p95 | -0,49 / +0,26 / +0,22 / -6,18 | -0,13 |
| Gaussian Blur, 854×480, Radius 11, p99 | +17,64 / -20,92 / +35,18 / +9,65 | +13,65 |

Die Zahl langsamer Radius-7-Frames bei 854×480 oberhalb des 1,10-Fachen des
jeweiligen Baseline-Phasen-p50 steigt von **1 auf 8 von 180 Samples**; pro Paar
von `[0, 0, 0, 1]` auf `[2, 1, 1, 4]`. Oberhalb des jeweiligen Baseline-p95
steigt sie von 8 auf 30. UI/Text bei 960×640 überschreitet das 1,25-Fache des
jeweiligen Baseline-p50 in **69 gegenüber 98 von 360 Samples**. Diese nachträglich
gewählten Schwellen dienen der Beschreibung; sie sind keine vorab festgelegten Gates.

Bei Gaussian 854×480 entspricht der gemischte Gesamt-p50 in jedem Paar dem
langsamsten Radius-7-Sample. Das erklärt seine Empfindlichkeit und verbindet ihn
hier zugleich mit einem realen Tail-Verlust. Stabile Phasenmediane belegen daher
keine stabileren Frametimes. Die Ursache wird durch vier Cloud-/Software-Mesa-Paare
nicht isoliert. Die ohnehin erforderlichen Gates des anschließenden reinen
Dokumentationscommits liefern vier weitere Paare auf einem neuen Runner;
dort werden diese Tails erneut betrachtet. Ihr Ergebnis wird hier nicht vorweggenommen.

### Java-Heap-Allokation über vier JVM-Paare

Alle acht Szenen allokieren in **jedem** der vier Paare weniger auf dem Renderthread.
Die gepaarten Medianreduktionen betragen **86,4–95,3 %**. A zeigt den Median seiner
vier JVM-p50 mit Spannweite; der B-p50 ist je Szene über alle vier JVMs identisch.
Gemessen werden Byte pro synthetischem Frame auf dem Java-Heap. Direkter, nativer
und GPU-Speicher sind ausgeschlossen.

| Szene | A: Byte/Frame, Median [Min–Max] | B: Byte/Frame, alle vier JVM-p50 | Gepaarter Δ-Median |
| --- | ---: | ---: | ---: |
| UI/Text, 960×640 | 66.528 [66.528–66.528] | 5.568 | -91,6 % |
| Geclipptes Bloom, 960×640 | 48.984 [48.984–51.288] | 6.680 | -86,4 % |
| Gaussian Blur, 960×640 | 3.240 [3.240–3.240] | 336 | -89,6 % |
| Glasfläche, 960×640 | 18.760 [18.760–18.832] | 1.968 | -89,5 % |
| UI/Text, 854×480 | 86.496 [86.496–88.992] | 4.032 | -95,3 % |
| Geclipptes Bloom, 854×480 | 46.728 [46.728–49.032] | 4.504 | -90,4 % |
| Gaussian Blur, 854×480 | 3.240 [3.240–3.240] | 336 | -89,6 % |
| Glasfläche, 854×480 | 17.168 [17.168–17.240] | 816 | -95,2 % |

Dies ist ein klarer gemessener Gewinn der untersuchten UI-Pfade. Es ist kein
Nachweis entsprechend kürzerer Frames, geringerer GC-Pausen oder eines vergleichbaren
Rückgangs der Gesamtclient-Allokation. Der erste Einzelrun hatte kürzeres Warmup
und weniger Samples; Unterschiede zu ihm sind kein zusätzlicher Codegewinn.

### Zusätzliche Fontprobe dieses CI-Laufs

Der Runner startet unabhängig von den vier Renderpaaren je **eine** weitere JVM
für `FontMetricsBenchmark`. Die Werte sind Mediane aus sieben Runden, keine neue
Serie aus vier Font-JVM-Paaren. Die Originalausgaben sind in den ergänzenden
Rohdaten erhalten; beide Checksummen lauten `1142745105.229`.

| Szenario | Baseline → Änderung, ns/Aufruf | Beobachtung |
| --- | ---: | --- |
| 24 wiederholte UI-Labels | 148,444 → 10,000 | schneller |
| 192 wechselnde HUD-Labels | 195,585 → 133,675 | schneller |
| 512 Labels über Cachekapazität | 207,459 → 248,733 | **langsamer** |
| Langer Text ohne Cacheaufnahme | 2.171,353 → 2.654,090 | **langsamer** |

Die Änderung misst in allen vier Szenarien 0 Byte/Aufruf. Die Baseline misst bei
wiederholten Labels 6,667 Byte, sonst ebenfalls 0. Die beiden Zeitverluste bleiben
neben den früheren fünf JVM-Paaren sichtbar; ein universeller Font-Zeitgewinn
wird auch aus diesem aktuellen Einzelpaar nicht abgeleitet.

## Historische CPU-Messung: UI-Vorbereitung

Die [CPU-Rohdaten](performance/2026-11-10-cpu-results.json) enthalten sämtliche
hier verwendeten JVM-Läufe, Parameter, Checksummen, die Hardwareangabe und
SHA-256-Quellprüfsummen der finalen Fontimplementierung. Diese UI- und
Fontmessungen entstanden **vor der Main-Integration gegen `2508562100a66480c639e2c01dfdffb004d98008`**.
Sie sind keine Wiederholungsmessung des später integrierten CI-Stands.

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

## Historische CPU-Messung der finalen Font-Cachekonstruktion

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

## Historische Evidenz: erster nativer Vergleich

[Native-CI-Run 42901400853](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42901400853)
vergleicht die integrierte Basis `ff11e9746521e65ab67d5e70789684d06c7c1edd`
mit der ausgeführten PR-Merge-Revision `dc51651a6e94ba94d96af9595d4bdbc61ba57217`.
Die [vollständigen ersten nativen Rohdaten](performance/2026-11-10-native-first.json)
enthalten sämtliche 60 Samples je Szene und Variante, p50/p95/p99, Pixelprüfsummen,
Ressourcenzähler sowie die ergänzte Commit-/Artefaktprovenienz. Das Originalartefakt
wurde gegen seinen GitHub-SHA-256-Digest geprüft.

Es lief **ein frisches JVM-Paar in Reihenfolge Baseline → Änderung**, mit je
20 Warmup-Frames und 60 Messframes pro Szene. Umgebung: Temurin 8u482,
Mesa 25.2.8, llvmpipe mit LLVM 20.1.2, OpenGL 4.5 Compatibility,
RGBA8-/Depth24-/Stencil8-Pbuffer. Die folgende Tabelle beschreibt diesen einzelnen
Szenenvergleich und keine Schätzung über mehrere unabhängige JVM-Paare.

### Zeiten: sämtliche acht Szenen

Die Pfeile zeigen Baseline → Änderung. Alle Zeiten sind **p50 in Millisekunden**;
ein positives Δ bedeutet längere Zeit. `submission` misst die CPU-Abgabe an den
Treiber; `completed` umfasst zusätzlich `glFinish()`. Hintergrundaufbau, Readback
und Dateiausgabe liegen außerhalb dieser Zeitmessung.

| Szene | Submission ms | Δ | Completed ms | Δ |
| --- | ---: | ---: | ---: | ---: |
| UI/Text, 960×640, Scale 2 | 1,626 → 1,555 | -4,4 % | 7,331 → 8,125 | +10,8 % |
| Geclipptes Bloom, 960×640, Scale 2 | 42,959 → 42,499 | -1,1 % | 44,456 → 44,062 | -0,9 % |
| Gaussian Blur, 960×640, Scale 2 | 24,300 → 30,012 | +23,5 % | 25,728 → 31,431 | +22,2 % |
| Glasfläche, 960×640, Scale 2 | 73,616 → 74,102 | +0,7 % | 91,927 → 92,892 | +1,0 % |
| UI/Text, 854×480, Scale 1 | 1,467 → 1,377 | -6,2 % | 3,275 → 3,198 | -2,4 % |
| Geclipptes Bloom, 854×480, Scale 1 | 28,418 → 29,037 | +2,2 % | 29,394 → 30,081 | +2,3 % |
| Gaussian Blur, 854×480, Scale 1 | 21,155 → 21,274 | +0,6 % | 22,043 → 22,288 | +1,1 % |
| Glasfläche, 854×480, Scale 1 | 28,699 → 29,436 | +2,6 % | 33,501 → 34,357 | +2,6 % |

**Der Gaussian-Mischwert muss nach Arbeitsphase eingeordnet werden:** Die Szene
wechselt jeden Frame zwischen Radius 7 und 11. Ihr nearest-rank-p50 über 60 Samples
liegt an der Grenze zwischen den beiden Zeitverteilungen und reagiert dort stark
auf einen langsamen Frame der schnelleren Hälfte. Der gesamte Submission-p50
steigt in diesem Lauf um 23,5 %, der gesamte Completed-p50 um 22,2 %. Die getrennt
berechneten Completed-p50 liegen bei Radius 7 jedoch bei **25,133267 → 25,593479 ms
(+1,83 %)** und bei Radius 11 bei **35,279604 → 35,842829 ms (+1,60 %)**. Der große
Mischwertsprung belegt deshalb keinen gleichmäßigen Zeitverlust dieser Größenordnung
für beide Radien. Alle ursprünglichen Gesamtwerte und Samples bleiben erhalten.

### Java-Heap-Allokation

Die p50-Allokation auf dem Renderthread sinkt in diesen Szenen um **87,0–95,5 %**.
Gemessen werden Byte pro synthetischem Frame auf dem **Java-Heap**; direkter,
nativer und GPU-Speicher sind ausgeschlossen. Das ist kein Nachweis einer entsprechend
kürzeren Framezeit oder einer solchen Einsparung im gesamten Client.

| Szene | Byte/Frame, Baseline → Änderung | Δ |
| --- | ---: | ---: |
| UI/Text, 960×640, Scale 2 | 78.240 → 10.176 | -87,0 % |
| Geclipptes Bloom, 960×640, Scale 2 | 53.848 → 6.680 | -87,6 % |
| Gaussian Blur, 960×640, Scale 2 | 3.240 → 336 | -89,6 % |
| Glasfläche, 960×640, Scale 2 | 18.832 → 1.968 | -89,5 % |
| UI/Text, 854×480, Scale 1 | 89.952 → 4.032 | -95,5 % |
| Geclipptes Bloom, 854×480, Scale 1 | 49.032 → 4.504 | -90,8 % |
| Gaussian Blur, 854×480, Scale 1 | 3.240 → 336 | -89,6 % |
| Glasfläche, 854×480, Scale 1 | 17.240 → 816 | -95,3 % |

Der erste Lauf war Anlass für die oben vollständig dokumentierten vier
balancierten JVM-Paare mit längerem Warmup. Seine ursprünglichen positiven und
negativen Messwerte bleiben erhalten; die Wiederholung verwendet identischen
Java-Code und einen erweiterten Messrunner.

## Teststand und verbleibende Nachweise

| Prüfung | Ergebnis und Beleg zum gemessenen Quellstand |
| --- | --- |
| Lokale Source-/Test-Kompilation und fokussierte Regression | **82/82 PASS**, 0 Failures, 0 Errors, 0 Skips auf dem vorherigen integrierten Stand `05cddf43`; Production-Java und Java-Tests sind zu `a786a0cb` bytegleich. |
| Vollständige Java-8-/Windows-Regression | **1.991 Tests in 347 Suites, 0 Failures, 0 Errors, 3 vorgesehene Skips**; [Run 42905777842](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42905777842), XML-Summen unabhängig geprüft. |
| Vollständige Regression beim Release-Package | **Erneut 1.991 / 0 / 0 / 3**; [Run 42905778025](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42905778025). |
| Gepacktes JAR und Release-Werkzeuge | **6/6 isolierte JAR-Prüfungen und 45 Python-Tests PASS** im selben Release-Run; Paket-/Linkage-Prüfung, keine gemessene Client-Startzeit. |
| Native Bildparität | **64/64 bytegleich über vier Paare**, einschließlich erster und warmer Aufnahmen bei beiden Auflösungen; [Run 42905778062](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42905778062). |
| Native GL-State-, Font-, Shader- und Ressourcenverträge | **PASS** im selben Native-Run: Uniform-Overloads/Subclasses, Gaussian-Referenzpixel, verschachtelte Zustände, Fehlerpfade, FBO-APIs 0/1/2, Komposition und Legacy-Facades. |
| Freigabe und Wiederöffnung gemeinsamer Ressourcen | **Zwei Zyklen bestanden**, jeweils 17 FBOs erstellt / 17 gelöscht und 11 Programme erstellt / 11 gelöscht; keine wiederbelebten gelöschten Texturen. |
| Integrierte Legacy-UI-Abnahme | **Vier unabhängige JVM-Suites PASS**, alle vorgeschriebenen Bildgruppen geprüft; 128 Bild-/Logdateien veröffentlicht. [Run 42905777886](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42905777886). |
| Gaussian-Koeffizienten | **PASS:** 8.255 Koeffizienten aller 127 Radien bitidentisch zur bisherigen Formel; Bereichs- und Buffer-Reuse-Prüfung erfolgreich. |
| Native Zeit-/Allokationsmessung | **Vier vollständige Paare ausgewertet**, alle Rohwerte erhalten; 86,4–95,3 % geringere Heap-Allokation im gepaarten Median, gemischte Zeiten und die oben beschriebenen höheren Tail-Latenzen. Kein allgemeiner Zeitgewinn. |
| Reale Gesamt-FPS, Welt-/Client-Ladezeit, Fullscreen und Shaderpacks | **Nicht gemessen** in diesem Lauf. |

Die aktuellen Windows- und Release-Läufe gehören zum PR-Head
`a786a0cb7b477426ea92e1802b16556bc2f923dc` und testen jeweils die Merge-Revision
`1474bf35592f9c13ecfa1fa560dc60de0380a444` mit Temurin 8u482 und Maven 3.9.15.
1.991 zählt die drei Skips mit; **1.988 Tests wurden pro Lauf ausgeführt und bestanden**.
Die beiden Ausführungen werden nicht addiert. Die bestehenden Opt-in-Skips betreffen
`WorldOverlayContractTest.nativeDispatchWhenRequested`,
`GuiChatInputTest.nativeInputAndDrawingWhenRequested` und
`GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings`.
Die nativen Workflows besitzen ihre eigenen ausgeführten GL-Prüfungen.
Der normale Draft-Release-Job ist bei dieser PR erwartungsgemäß übersprungen;
Build, Paketprüfung und Artefaktveröffentlichung sind erfolgreich.

Die frühere vollständige Bildinventur des bytegleichen Legacy-UI-Harness ergab
114 PNGs: Panel 20, Dropdown 48, ArrayList 8 und Session/Theme/Settings/Library 38
([Run 42901400954](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/42901400954)).
Beim aktuellen erfolgreichen Lauf wurden sämtliche Suite- und Bildgruppen-Gates
sowie der 128-Dateien-Upload geprüft; seine PNGs wurden nicht erneut lokal materialisiert.

Für die integrierte native Baseline wurden **alle 2.391 ursprünglichen Java-Quellen**
neu kompiliert; nur 15 geänderte Originaldateien mussten als Quelloverlay materialisiert
werden. Die Legacy-UI-Suites belegen zusätzlich unter anderem pixelstabile statische
Editoransichten, animierte Vorschauen, geclippte Effekte, unverändertes Inneres der
ArrayList-Glowkontur, feindlich vorbelegten GL-Zustand sowie echte Config-/Settings-Aktionen.

Ein früherer vollständiger lokaler Linuxlauf auf der ursprünglichen Basis blieb
mit 1.963 Tests, 4 Failures, 276 Errors und 3 Skips nicht grün: Viele Subprozesse
erwarteten `java.exe`, dazu kamen Diagnostics-Assertions. Die Emoji-Failure in
`FontAtlasMetricsTest`, Zeile 27, wurde damals auf Baseline und Änderung gleichermaßen
reproduziert. Diese historischen Befunde werden nicht in grüne Linux-Gesamtergebnisse
umgedeutet; die oben verlinkten vollständigen Windows-Nachweise sind eigenständige Läufe.

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
mvn -B -ntp -Dtest=GuiResolutionTest,GaussianKernelCacheTest,RenderStateBuffersTest,FontTextCacheTest,UiEffectsTest,ThemeModelTest,ColorUtilityCompatibilityTest,ThemeUiConsumerTest,ThemeModeApproachTwoTest,SideGuiInteractionTest,SettingSubcategoryTest,ThemeEditorLayoutTest,ClickLibraryRenameLayoutTest,ModulePrefixesTest,LegacySettingsLayoutTest,Wave07ModernScreensTest test
mvn -B -ntp dependency:build-classpath -Dmdep.outputFile=target/render-classpath.txt
```

Für den nativen Vergleich benötigt die Umgebung LWJGL-Linux-Natives, Mesa/GLX,
Xvfb und das `xrandr`-Kommando aus `x11-xserver-utils`.
Der [Runner](../.github/scripts/check_render_performance.py) kompiliert ein
Baseline-Quelloverlay aus Git und verwendet denselben Benchmark für beide Fassungen.
Dabei müssen auch die ursprünglichen Aufrufer neu kompiliert werden, damit neue
Uniform-Overloads nicht gegen alte Klassen gelinkt werden. Der
[Workflow](../.github/workflows/render-performance.yml) hält Einrichtung und Aufruf fest.
Das folgende Beispiel reproduziert den Vierpaarvergleich mit dem erweiterten
Runner am oben genannten gemessenen Quellstand. Zur historischen Reproduktion des ersten Laufs ist dessen getestete Revision
`dc51651a6e94ba94d96af9595d4bdbc61ba57217` mit dem damaligen Runner und
`--warmup 20 --samples 60` maßgeblich; dieser kannte noch keine Option `--pairs`.

```bash
xvfb-run -a -s '-screen 0 1280x960x24 +extension GLX +render -noreset' \
  python3 .github/scripts/check_render_performance.py \
  --base ff11e9746521e65ab67d5e70789684d06c7c1edd \
  --classpath target/render-classpath.txt \
  --pairs 4 --warmup 60 --samples 90 \
  --output target/render-performance
```

Verglichen werden feste Text-/UI-, geclippte Theme-/Fixfarben-Bloom-, Gaussian-
und Glass-Szenen bei 960×640 mit GUI-Scale 2 sowie 854×480 mit GUI-Scale 1.
Vier Szenen, zwei Auflösungen und jeweils erster sowie warmer Capture ergeben
**16 exakte RGBA-Vergleichspaare je JVM-Paar, insgesamt 64 bei vier Paaren**.
Jeder Capture muss sich vom unmittelbar zuvor
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
