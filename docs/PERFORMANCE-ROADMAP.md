# Neutron Client 1.8.9 — Performance-Roadmap und Playtest-Gate

**Stand:** 2026-10-29  
**Baseline:** `main` bei `d5a277de779f6cf69768a2900e299c9af5810e1a`  
**Arbeitsbranch:** `perf/phase1-playtest-2026-10-29`  
**Stack:** Java 8, Minecraft 1.8.9 / OptiFine, LWJGL 2, OpenGL, Maven  
**Kontext:** Laufender UI-Recode; ein erster realer Multiplayer-Playtest ist geplant.  
**Status:** Source-Audit und Maßnahmenplan; FPS-Gewinne sind **noch nicht gemessen**.

> **Unverhandelbares Ziel:** Höhere und stabilere FPS bei gleicher Funktionalität und gleichem visuellen Profil. Kein Feature entfernen, kein Liquid Glass abschwächen, keine Shader vereinfachen, keine Blur-/Glow-Qualität senken. Wir optimieren, WIE die vorhandene Arbeit ausgeführt wird, nicht WAS am Ende sichtbar ist.

## 1. Regeln, Definition of Done und Grenzen

1. **Null Featureverlust:** Module, Einstellungen, GUI-Layouts (Panel und iOS), HUD-Stile, Animationen, Notifications, GlowESP/ESP, Gameplay- und Netzwerkverhalten bleiben erhalten.
2. **Visuelle Parität:** Radius, Shader-Logik, Pass-Zahl, Sample-Zahl, Renderauflösung, Gradients, Alpha/Blending, Masken, Clip-Verhalten, Textqualität, Reihenfolge und Animationstempo bleiben unverändert, sofern die Änderung nicht ausdrücklich nur redundant berechnete identische Ergebnisse zusammenführt.
3. **Kein künstliches FPS-Tuning:** Keine FPS-Caps ändern, keine VSync-Änderungen als Produktoptimierung verkaufen, keine Effekte abschalten, keine versteckten Quality-Fallbacks einbauen.
4. **Sicherer Zustand:** OpenGL-State und Minecrafts GlStateManager-Cache müssen nach jedem Scope gültig bleiben; keine GL-Arbeit auf Worker-Threads. Keine veralteten World-, Entity- oder Packet-Snapshots für entscheidungsrelevante Aktionen verwenden.
5. **Reproduzierbar:** Vorher/nachher auf gleicher Hardware, Auflösung, Shaderpack-, Welt-, Modul- und Einstellungskonfiguration vergleichen. Kein FPS-Versprechen ohne Messung.
6. **Kleine, isolierbare PRs:** Kein großflächiges Umbenennen, Löschen oder Reformatieren von UI-Dateien während des Recodes. Keine ungefragten Architektur-Megamerges.
7. **Rollback-fähig:** Jede Stufe separat testen und bei visueller, semantischer oder Framezeit-Regression rückgängig machen.

**Playtest-Definition of Done:** Projekt kompiliert und Tests bestehen; vollständige UI-Profile bleiben nutzbar; keine sichtbaren Grafikfehler oder neuen Crashes; Fokus- und Combat-Interaktionen laufen; FPS und Frametimes sind mindestens nicht schlechter. Ein Playtest darf nicht mit ungeprüften RenderState-/FrameGraph-Umbauten blockiert werden.

## 2. Tatsächliche Befunde aus dem Repository (nicht als Messung ausgeben)

| Stelle | Nachgewiesene Source-Eigenschaft | Interpretation |
| --- | --- | --- |
| `utils/render/RenderState.java` | Erzeugt viele Buffers und liest zahlreiche GL-States in Konstruktor/close; bei `UIUtils.drawRound` und verwandten Methoden häufig genutzt | Hohe Treiber-/Synchronisationskosten **möglich**; erst instrumentieren, dann auf gröbere Scopes umstellen |
| `utils/render/ScreenRect.java` | Fragt Modelview, Projection und Viewport je `project()` ab; projiziert vier Ecken per GLU | Cache mit gültiger Transform-Version könnte viele GL-Abfragen vermeiden |
| `module/impl/render/ESP.java` | Liest Matrix und Viewport im achtfachen Bounding-Box-Eckenloop erneut | Konkrete redundante Abfragen bei unveränderten Matrizen innerhalb dieser Projektion |
| `utils/render/ArrayListSurface.java` | Teilt bereits Effektbuffer und bietet verschachtelte Scene-Contexts; führt je nach Surface eigene Capture/Blur/Glow-Passes aus | Vorhandenes Sharing ausbauen, aber die korrekte Scene-/Layer-Reihenfolge erhalten |
| `utils/render/Blur.java` | Fullscreen-Copy per `glCopyTexSubImage2D` und Blur/Composite | Identische Captures/Radien auf identischer Szenenversion untersuchen |
| `utils/render/GuiShaderBloom.java` | Besitzt bereits Glow-Batching und Batch-Komposition | Behalten; kompatible Draw-Commands gezielter zusammenführen |
| `utils/render/shader/blur/GaussianBlur.java` | Gaussian-Gewichte nach Radius bereits gecacht | Nicht neu erfinden; bestehende Vorteile erhalten |
| `module/impl/render/GlowESP.java` | Eigene Outline-/Gaussian-/Kawase-Pfade; `setupGlowUniforms()` erzeugt Buffer und Gewichte wiederholt | Wiederverwendung bei identischem Radius möglich, ohne Radius/Shader/Passes zu ändern |
| `utils/font/TTFFontRenderer.java` | Zeichnet Glyphen als einzelne gebundene Texturen/Quads | Langfristig Glyph-Atlas und kompatibles Batching; Textmetrik/AA exakt erhalten |
| `module/impl/client/ArrayListHUDModule.java` | Erzeugt Label-Metriken und Glyph-Arrays und sortiert pro Frame | Statische Layoutdaten nach Text/Font/Scale/Settings invalidieren; Animation weiter pro Frame |
| `core/combat/TargetingService.java`, `module/impl/combat/Aura.java` | Entity-Scans, Sortierung und gültigkeitsabhängige Raycasts | Gemeinsame, **korrekt invalidierte** Tick-Queries prüfen; keine Attack-Gates abschwächen |
| `module/impl/combat/Velocity.java` | `parallelStream()` über aktive Potion-Effekte | Kleine sequenzielle Prüfung ist äquivalent und spart Parallelisierungs-Overhead |
| `config/ConfigManager.java` | Autosave mit Dirty-Delay, aber synchrones File-I/O inkl. `FileDescriptor.sync()` vom Tick aus | Potenzielle einzelne Frame-Stalls; Snapshot auf Client-Thread, atomisches I/O später auslagern |
| `core/bootstrap/RuntimeServices.java` | Laufzeit-Fallback-Instanzen zusätzlich zu `ClientRuntime` | Architekturfrage, nicht automatisch Performance-Bottleneck; Lifecycle vor Änderung testen |

**Bereits gut:** `ShaderUtil` cached Uniform-Locations und lebt über Draws; `FontManager` cached Renderer/Faces; Renderbuffer-Pools und Teile des Scene-Sharings existieren; Config-Autosave ist debounced. Diese Arbeit nicht unnötig ersetzen.

**Nicht belegte Annahme:** „120 FPS werden durch die Shader verursacht.“ Möglich, aber ebenso denkbar sind CPU/GL-Driver-, World-, OptiFine-, VSync- oder Tick-/I/O-Limits. Die Diagnose darf nicht vorweggenommen werden.

## 3. Integrationsstrategie während des laufenden UI-Recodes

- **Jetzt:** Nur isolierte Hotpath-Korrekturen in `ESP.java`, `Velocity.java` und `GlowESP.java`. Keine UI-Shaderdateien, Theme-Materialien, Global-/Local-Settings oder Render-Pipeline-Verträge anfassen.
- **Branch-Isolation:** Änderungen nur auf `perf/phase1-playtest-2026-10-29`, nicht direkt nach `main` mergen. PR gegen aktuelles `main`; Besitzer entscheidet nach Tests über Integration in den tatsächlichen Playtest-/UI-Branch.
- **Bei Recode-Konflikt:** Nicht blind eine ganze Renderdatei übernehmen. Änderungen gezielt übertragen; neue UI-Logik behalten. Falls die UI-Basis abweicht, Tests/visuellen Vergleich auf dem realen Merge-Commit wiederholen.
- **Für heute bewusst verschieben:** RenderState-API-Umbau, FrameGraph, Shader-/FBO-Komposition, Font-Atlas, Änderung der GUI-Komponentenstruktur und umfangreiche Minecraft-/OptiFine-Hooks.
- **Kein heimliches Merge:** Der Branch ist ein Review- und Playtest-Kandidat. Er ist **nicht** allein wegen eines erfolgreichen Source-Patches freigegeben.

## 4. Phase 0 — Profiling-Basis und reproduzierbarer Benchmark (P0)

**Ziel:** CPU- und GPU-Kosten auseinanderhalten, damit Optimierung nicht auf Verdacht erfolgt.

1. `RenderDiagnostics` opt-in behalten und erweitern: pro Frame Draws, GL-State-Reads, Shader-Passes, Fullscreen-Copies, FBO-Erstellung/Löschung, Batch-Größe und Sprite-/Glyph-Uploads. Diagnose selbst muss deaktiviert praktisch kostenlos sein.
2. CPU-Phasen über `System.nanoTime()` im vorhandenen Frame-/Tick-Kontext erfassen: GameLoop, `runTick`, EventDispatch nach Typ, World-Render, HUD, Gui, Blur, GlowESP, Font/Geometry, I/O. Ergebnisse in Ringbuffer schreiben; keine pro-Frame-Logs.
3. GPU-Phasen bei verfügbarer Extension mit **asynchronen** Timer-Queries erfassen, erst spätere Frames auslesen. Niemals `glFinish()` oder sofort blockierend auslesen; CPU-Zeit für Draw-Submission ist keine GPU-Zeit.
4. Szenarien mit exakt denselben Settings: (a) Welt ohne ClickGUI, (b) World + voller HUD/Hotbar/Scoreboard, (c) Panel-ClickGUI mit Liquid Glass, (d) iOS-ClickGUI mit Partikeln/Glow, (e) ESP/GlowESP mit vielen Entities, (f) Combat/Movement, (g) Welt-/Serverwechsel und GUI-Scale-/Resize.
5. Pro Szenario mehrere reproduzierbare Durchgänge nach Warmup; p50/p95/p99 Framezeit, 1%-Low, CPU-/GPU-Dauer, Allokationsrate und GC-Pausen dokumentieren. FPS allein verschleiert Stutter.
6. FPS-Cap und VSync **nur für Benchmarkmessungen** kontrollieren und dokumentieren; das ist keine Änderung der angebotenen Client-Features.

**Exit:** Ein Baseline-Protokoll mit Hardware, Java-Version, Spielauflösung, FPS-Limit/VSync, Shaderpack, Preset, aktiven Modulen und gemessenen Frametimes.

## 5. Phase 1 — Minimaler Playtest-Patch (P0, unabhängig von UI-Recode)

### P1-A — ESP-Projektion

Datei: `src/main/java/me/youngjibbit/module/impl/render/ESP.java`

- Modelview, Projection und Viewport **pro Entity-Projektionsabschnitt** nur einmal holen, nachdem World-Matrizen wiederhergestellt wurden, nicht pro Bounding-Box-Ecke.
- GUI-Scale für denselben Pass einmal berechnen; Ecken/Interpolation/GLU-Projektionsalgorithmus, Render-Reihenfolge und vorhandene Sichtbarkeitsprüfungen beibehalten.
- **Risiko:** Matrix kann zwischen Entities durch Overlay-Rendern geändert werden; deshalb vor jeder Entity-Projektion nach dem Matrix-Restore erneut erfassen, nicht blind über alle Entities hinweg cachen.
- **Prüfen:** ESP-Boxen beim Drehen, Springen, Sneaken, FOV-Wechsel, Resizing, mit OptiFine/Shaderpack und teilweiser Sichtbarkeit.

### P1-B — Velocity-Potion-Check

Datei: `src/main/java/me/youngjibbit/module/impl/combat/Velocity.java`

- Kleinere Menge aktiver Potion-Effekte sequenziell durchsuchen statt `parallelStream()`.
- Dieselben Booleans, Prioritäten, Short-Circuit-Semantik und Null-Voraussetzungen verwenden.
- **Prüfen:** Geschwindigkeit/Jump-Boost einzeln und kombiniert, beide Settings ein/aus, Treffer-/Knockback-Verhalten.

### P1-C — GlowESP-Weights

Datei: `src/main/java/me/youngjibbit/module/impl/render/GlowESP.java`

- Direct-FloatBuffer und Gaussian-Gewichte nach Radius wiederverwenden.
- **Wichtig:** Bisherige Uniform-Upload-Länge (256 Floats), Nullen hinter den aktiven Weights und alle Werte für denselben Radius exakt beibehalten. Gewichte bei Radius-Wechsel korrekt neu aufbauen; Upload weiterhin bei jedem benötigten Pass.
- **Prüfen:** Glow-Ausgabe bei mehreren Radiuswerten, Wechsel Gaussian/Kawase, Weltwechsel, Module Toggle, Resize, FBO-Reload.

**Playtest-Gate:** Diese drei Änderungen ohne UI-Recode-Dateien; erst lokal bauen und konkrete Spiel-/Grafikinteraktionen prüfen. Falls die Test-Infrastruktur nicht verfügbar ist, Patch nicht als „getestet“ bezeichnen.

## 6. Phase 2 — RenderState und ProjectionContext (P0 nach UI-Integration)

**Problem:** `RenderState` fragt Zustand sehr granular ab. `UIUtils.drawRound`, `drawRoundOutline` u. a. eröffnen jeweils vollständige Scopes. Eine parallele OpenGL-State-Cache-Struktur wäre riskant, da Raw GL, `GlStateManager` und OptiFine gemischt werden.

**Zielarchitektur:** Ein äußeres `RenderPassScope` übernimmt den vollständigen GL-Vertrag für einen zusammenhängenden UI-Pass. Untergeordnete `DrawScope`-Operationen verändern nur explizit besessene Zustände. Der äußere Scope stellt Zustand garantiert wieder her. Externe/legacy Renderer erhalten isolierte kompatible Guards.

**Schritte:**
1. Alle Raw-GL-/GlStateManager-Writer und ihre gegenseitige Synchronisation inventarisieren.
2. GL-State-Snapshots pro Render-Pass statt pro Shape, **nur** wo keine fremde Änderung dazwischen liegt.
3. Idempotente State-Setter und gezielte Invalidierung einbauen; Raw-GL-Hooks bleiben anfangs „unknown state“ und erzwingen erneute Synchronisation.
4. `ProjectionContext` mit Matrix-/Viewport-Snapshot und Version bei `glPushMatrix`, `glPopMatrix`, `glTranslate`, `glScale`, Projektionswechsel und externen Renderer-Grenzen.
5. Driver-Reads, CPU-Frametimes, Rendering und GL-Status mit Native-/FBO-Tests vergleichen.

**Nicht zulässig:** Blindes Weglassen von `glGet*` oder Restore-Calls ohne Ownership-Beweis; unversionierter globaler Matrixcache.

## 7. Phase 3 — Scene-Versionen, Blur/Glow und FrameGraph (P0/P1 nach UI-Freeze)

**Ziel:** Identische Fullscreen-Captures, Masken und Gaussian-/Kawase-Ergebnisse für **identische Eingaben** innerhalb des Render-Layers nur einmal berechnen.

- Passbeschreibung: World/HUD source → scene snapshot → blur result → mask/composite → Glow/Foreground. UI-Materialien dürfen ihren vorhandenen read-only Scene-Hintergrund weiter konsumieren.
- Cache-Key mindestens: Szenenversion, Source-Texture/Framebuffer-Generation, physische Auflösung, GUI-Scale, Blur-Methode, Radius, Qualität, Clip-/Transform-Abhängigkeiten und Layer-Generation.
- **Scene-Version erhöhen**, sobald ein zwischenliegender Draw den sichtbaren Hintergrund beeinflusst. Andernfalls darf Glass nicht versehentlich alten Hintergrund sampeln.
- Ping-Pong-FBOs und Bloom-Pyramiden mit klarem Owner-/Resize-/Reload-/World-Unload-Lifecycle. Keine Nutzung von Ressourcen nach Delete; GPU-Synchronisierung nicht erzwingen.
- Batch nur für kommutierende/kompatible Operationen. Alpha-Over, Blend-State, Clipping, Z-/Layer-Reihenfolge und Maske machen allgemeines Reordering unsicher.
- Blur-Schärfe, Sample-Anzahl, Glow-Intensität, Liquid-Glass-Refraction und Gradients **nicht** reduzieren.

**Exit:** gleiche native Renderbilder bei gleichem Time-Seed und stabilen Test-Szenen; weniger Kopien/Pässe oder weniger Framezeit, keine neue GPU-Stalls.

## 8. Phase 4 — Glyph-Atlas, Layout-Caches und Draw-Batching (P1)

- `TTFFontRenderer`: Identische Java2D-Glyphen mit derselben Rasterisierung, AA, Filterung, Texel-Ausrichtung, Metrik und Farbcode-Semantik in Atlanten packen. Nur kompatible Glyph-Quads gemeinsam zeichnen. Shadows, Fonts, Bold/Italic, Unicode-/Formatierungsfälle und Clipping auf Parität prüfen.
- `ArrayListHUDModule`: Immutable Messdaten anhand von Text/Font/Style/Scale/Settings cachen. Animierte Positionen, Transitionen, Sortier-Tie-Breaks und Theme-Phasen bleiben dynamisch.
- `ClickGui`/iOS: Bereits existierendes Panel-Viewport-Clipping und scissor-sensitive Partikel/Layers respektieren; keine sichtbaren Effekte „wegcullen“. Komponentengrößen nur bei echten Input-/Setting-Änderungen neu layouten.
- Keine Fontdatei austauschen oder die Auflösung/AA-Qualität als Abkürzung senken.

## 9. Phase 5 — Tick, Event, World, Netzwerk und I/O (P1/P2)

- `Aura`, `AimAssist`, `TargetingService`, `RaycastService`: Was kann innerhalb eines Ticks stabil geteilt werden, ohne Live-Hit-/LOS-/World-/Rotation-Checks zu verändern? Erst Query-Zählungen und Callgraph, dann korrekt invalidierte Tick-Snapshots.
- `ModuleManager`/Alpine: Listener-Zeiten messen; nicht reflexhaft Eventbus oder Reflections ersetzen, da Discovery überwiegend Startup ist und aktivierte Module bereits abonniert werden.
- `ConfigManager`: Client-Thread erzeugt konsistenten Zustand; geordneter I/O-Worker schreibt atomar, erhält fsync, Fehlerbehandlung, Shutdown-Flush und Config-Kompatibilität. Nie still Daten verlieren.
- `Multithreading`/Executors: Tasks, Pool-Wachstum und Shutdown messen; Background-Berechnungen nur für thread-sichere, immutable CPU-Daten.
- `RuntimeServices`: Fallback-Lifecycle/Owner-Übergabe testen, bevor doppelte Services angefasst werden. Quelle von Bugs oder Wartungsaufwand ist nicht automatisch ein FPS-Problem.
- Minecraft/OptiFine: Mit Profiler entity/chunk rebuild, RenderGlobal, GuiIngame, Texture-/Resource-Reload, Event3D-Hooks prüfen. Shaderpack-Kompatibilität zählt zum bestehenden Featurevertrag.

## 10. Mess- und Regressionsvertrag

| Metrik | Vorher/nachher | Gate |
| --- | --- | --- |
| Framezeit p50/p95/p99, 1%-Low | Pro festem Szenario | Keine reproduzierbare Regression, Verbesserung dort wo der Patch Kosten entfernt |
| CPU-Zeit nach Phase/Listener | Main thread | Regressionen identifizierbar; Instrumentierung nicht selbst limitierend |
| GPU-Zeit | Asynchrone Query, wenn unterstützt | Keine zusätzlichen GPU-Stalls |
| GL-State-Reads, Draw-Calls, Fullscreen-Copies, FBO- und Shader-Counter | Vorher/nachher | Zielgerichtete Reduktion redundanter Arbeit, keine Qualitätsreduktion |
| Visuelle Golden Images | Gleiches Preset, Scale, Zeitstand, Hintergrund | Strikte Gleichheit, wo deterministisch; bei Treiber-/AA-Abweichung manuell begründeter Review |
| Modul-/Packet-/Tick-Integration | Bestehende Tests und echter Playtest | Gleiche Semantik, keine Crashes, korrekter State-Restore |

**Wichtige vorhandene Prüfungen:** `WorldOverlayContractTest`, `WorldOverlayNativeCheck`, `RenderStateFramebufferCheck`, `SharedResourceCheck`, `LiquidGlassRefinementCheck`, `ClickGuiRenderCheck`, `ArrayListRenderCheck`, `EffectConsistencyCheck`, `ThemeSyncRenderCheck`, `IngameHudRenderCheck`, `TargetingServicePerformanceTest` sowie die Module-/Config-Tests.

**Lokal ausführen (Java 8 / Maven 3.9.x):**

~~~bash
mvn clean test
# Anschließend vorhandene Pbuffer-/FBO-Check-Mains nach Repo-Anleitung,
# dann Native-Fenster + tatsächliche World/Multiplayer-Tests.
~~~

Die Historie dokumentiert erfolgreiche Suites auf früheren Ständen, aber **keine** automatischen Tests für den hier neu vorgeschlagenen Patch. Der GitHub-Workflow-Ordner enthält derzeit nur `traffic-metrics.yml`; es existiert kein allgemeines CI-Gate. Ein erfolgreicher `mvn test` ist außerdem kein Ersatz für Native-Window-/Server-Playtests.

**Manueller Playtest-Smoke:**
- Panel-/iOS-ClickGUI öffnen, Kategorien, Slider, Theme-Editor, Resize und Scrolling prüfen.
- Liquid Glass, Gaussian, Kawase, Roundness, Glow, Text, Global-/Local-Themes, Animationen, HUD/Hotbar/Scoreboard/Notifications vergleichen.
- 2D ESP und GlowESP in Drittperson/Erstperson testen, bewegen, FOV ändern, Welt laden/verlassen, fullscreen/resize.
- Velocity bei aktiven Potion-Effekten und Combat/Movement mit mehreren eingeschalteten Modulen vergleichen.
- Frametimes, FPS und sichtbare Fehler notieren, nicht nur den höchsten FPS-Wert.
- Bei Crash, Pixelbruch, GL-State-Leak oder Combat-Semantikfehler betroffenen Performance-Commit zurücknehmen; nicht die Funktion deaktivieren.

## 11. Arbeitsaufteilung mit OpenCode (modellunabhängig)

**Geeigneter Ablauf bei knappem Codex-Budget:** Ein stärkeres Modell (z. B. das vorhandene Nemotron 3 Ultra) für Review/Abhängigkeiten und schwierige Render-Ownership-Fragen, ein schnelleres Modell (z. B. DeepSeek V4.1 Flash Free) für eng begrenzte Patches, danach wieder unabhängiger Review. Die tatsächliche Qualität muss pro Patch anhand von Tests überprüft werden; Modellnamen sind keine Korrektheitsgarantie.

**Copy-Paste-Agent-Brief:**

~~~text
Projekt: YoungJibbit95/Neutron-Client, Java 8, Minecraft 1.8.9, LWJGL 2.
Lies zuerst docs/PERFORMANCE-ROADMAP.md und die relevanten aktuellen Source-Dateien.
NICHT VERHANDELBAR:
- Keine Features, Module, Settings, visuellen Profile oder Shader-Effekte entfernen/abschwächen.
- Keine Verringerung von Blur/Glow-Radius, Samples, Passes, Auflösung oder Shaderqualität.
- Kein Gameplay-/Anti-Cheat-/Network-Semantik- oder Event-Order-Rewrite als Optimierung.
- Keine Änderungen im aktiven UI-Recode ohne separate, ausdrücklich überprüfte Scope-Freigabe.
- Nur einen eng begrenzten Task gleichzeitig; kein großflächiges Reformatieren.
- Kein Push/Merge auf main. Arbeite im Performance-Branch.
Für jeden Patch: (1) konkrete redundante Arbeit und Gültigkeitsannahmen nennen,
(2) minimalen Code-Diff erstellen, (3) bestehende Test- und Rendering-Verträge prüfen,
(4) tatsächliche Tests ausführen und Ergebnisse ehrlich protokollieren,
(5) potenzielle visuelle/semantische Regressionen und Rollback beschreiben.
Nie FPS-Zahlen erfinden oder Tests als bestanden behaupten, die nicht liefen.
~~~

**Task-Reihenfolge:** erst P1-A (ESP), dann P1-B (Velocity), dann P1-C (GlowESP). Danach Review + Playtest-Gate. P2–P5 erst auf Basis der echten Frametimes und nach Abgleich mit dem aktuellen UI-Recode.

## 12. Fortschritt und Entscheidungen

- [x] Source-Sichtung wichtiger Render-, HUD-, Event-, World-, Combat- und Config-Pfade.
- [x] Unverhandelbaren Feature-/Visual-Parity-Vertrag festgelegt.
- [x] Performance-Arbeit in separatem Branch isoliert.
- [ ] P1-A ESP-Projektionsabfragen optimiert und validiert.
- [ ] P1-B Velocity-Potion-Check optimiert und validiert.
- [ ] P1-C GlowESP-Gewichte wiederverwendet und validiert.
- [ ] `mvn clean test` auf exakt diesem Branch erfolgreich.
- [ ] Native-GL-/Visual-Smoke erfolgreich.
- [ ] Erster Multiplayer-Playtest und Baseline-/Delta-Messung.
- [ ] Gemessene Entscheidung über P2/FrameGraph und UI-Recode-Integration.

**Release-Status bis zu diesen Gates:** Kandidat, **nicht** als getestete FPS-Verbesserung oder fertiger Release deklarieren.
