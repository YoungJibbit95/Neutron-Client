# Shaderpack und Clientshader: Integration November 2026

**Analyse- und Vergleichsbasis:** `97c263c792864d03c2271670121fd72d53bbfeb1`.
**Zielstack:** Minecraft 1.8.9, eingebettetes `OptiFine_1.8.9_HD_U_L5`, LWJGL 2,
Java 8. Die Versionskennung stammt aus [Config](../src/main/java/net/minecraft/src/Config.java).

Die Änderungen führen eine explizite Grenze zwischen Shaderpack-Pässen und
Clientshadern ein. Welt-Overlays zeichnen bei aktivem Pack nach dessen finalem
Farbbild mit der gespeicherten Weltkamera und einer privaten Tiefenkopie.
Die Effekte behalten ihre eigene Blend-Rechnung. Ein Shaderpack, das diese Pfade
noch nicht im Spiel durchlaufen hat, erhält dadurch keinen Kompatibilitätsstatus.

**Abnahmestand dieses Dokuments:** Java-8-Kompilation und lokale Strukturtests
sind erfolgreich. Die neuen nativen Prüfszenen sind implementiert, lokal jedoch
**NOT_RUN**: Im Arbeitskontext konnte kein X11/Pbuffer-Kontext erstellt werden.
CI-Ergebnisse müssen mit ihrer tatsächlichen Kandidatenrevision ergänzt werden.
Beide unten benannten Sildur-Packs sowie Vollwelt-, Display- und Hardwaremessungen
bleiben **NOT_RUN**. Frühere UI-Prüfungen gelten nur für ihre damals benannten
Revisionen und Szenen; siehe [historischer Render-Bericht](RENDER-PERFORMANCE-2026-11.md).

## 1. Tatsächliche Passgrenzen des eingebetteten Renderers

Maßgeblich sind [EntityRenderer](../src/main/java/net/minecraft/client/renderer/EntityRenderer.java),
[Shaders](../src/main/java/net/optifine/shaders/Shaders.java),
[ShadersRender](../src/main/java/net/optifine/shaders/ShadersRender.java) und
[CustomEntityRenderer](../src/main/java/me/youngjibbit/utils/render/CustomEntityRenderer.java).
Moderne Shader-Dokumentation ersetzt diese konkrete 1.8.9-Implementierung nicht.

| Phase | Ziel und Zustand | Client-Verhalten |
| --- | --- | --- |
| `beginRender` / Shadowmap | Packeigene Shadow-FBOs und Shadowkamera; anschließend Rückkehr zum Haupt-`dfb` | Kein `Event3D` für die Shadowkamera. GlowESP nimmt hier keine zusätzlichen Silhouetten für die Hauptkamera-Maske auf. Normale Shadow-Geometrie bleibt erhalten. |
| `beginRenderPass` je Hauptkamera-Auge | Haupt-`dfb`, Weltviewport, Packprogramm; die Welt-/Final-Flags beginnen für jedes Auge erneut | `isRenderingWorld=true` und `isCompositeRendered=false` werden ausschließlich außerhalb des Shadowpasses gesetzt. Ein zweites Auge ohne Hand muss ebenfalls einen Final-Pass erhalten. |
| Welt, Entitäten, früher Handpass, transparente Geometrie, `endWater`, Forge-Render-last | Weltkamera und gemeinsamer Pack-Tiefenpuffer; mehrere Farbausgaben können Gbuffer-Metadaten tragen | Der bestehende Welt-Hook zeichnet ohne Pack sofort. Mit Pack speichert er Projektions-/Modellmatrix, ColorMask, Auge, PartialTicks, Frame, Welt- und Ressourcenidentität. Hier entsteht keine Clientfarbe im Gbuffer. |
| `renderHand1`, Composite, Final | Späte Handtiefe im `dfb`; Composite-Pässe verwenden dessen Texturen. Final zeichnet auf die Minecraft-Farbtextur. | Erst danach wird die aktuelle Hauptkamera-Tiefe kopiert. Damit gehört eine späte Hand-Tiefenwirkung zur Eingabe. Die Eingabe ist ausdrücklich keine reine Terrain-Tiefe. |
| `renderShaderpackWorldOverlays` | Eigenes Alias-FBO mit geliehener finaler Minecraft-Farbtextur und privater Tiefe | Einmaliger `Event3D` mit gespeicherter Kamera. Alpha, additive Mischung und Zielbild-abhängige Effekte arbeiten direkt auf der finalen Farbe. |
| Haupt-Tiefe löschen, `renderFPOverlay`, Richtungsanzeige | Bestehender First-Person-/Bildschirm-Pfad | Diese Schritte bleiben nach den Welt-Overlays. |
| Pfad ohne Hand | `endRender` führt einen noch ausstehenden Final-Pass aus; danach Overlay-Hook | Der Hook akzeptiert den bereits zurückgesetzten `isRenderingWorld`-Wert. Eine zweite Hook-Ausführung kann die gespeicherte Aufnahme nicht wiederholen. |

`endRender` behält seine Prüfung auf `isCompositeRendered`: Wenn der normale
Handpfad bereits Composite/Final ausgeführt hat, entsteht kein zweiter Final-Draw.
Die neue Rücksetzung sitzt am Beginn eines echten Hauptkamera-Passes und wird
im Shadowpass übersprungen.

### Stereo-Ausgabe

OptiFine setzt im Gbuffer über `setViewport` alle Farbkanäle frei. Diese gespeicherte
Maske allein ist daher keine passende Ausgabemaske für die finale Anaglyph-Farbe.
Die Overlay-Brücke beschränkt zusätzlich anhand des tatsächlichen Passes:
Pass 0 schreibt Cyan-Kanäle, Pass 1 Rot, Pass 2 verwendet die normale Maske.
Gespeicherte Einschränkungen und das Alpha-Bit bleiben erhalten. Der äußere
State-Scope stellt anschließend die Eintrittsmaske wieder her.

## 2. Regeln für Clientshader und OpenGL-Zustand

[RenderState](../src/main/java/me/youngjibbit/utils/render/RenderState.java) und
[UiDrawState](../src/main/java/me/youngjibbit/utils/render/UiDrawState.java)
sichern ihren tatsächlichen GL-Eintrittszustand, bevor sie einen
`Shaders.ClientRenderState` öffnen. Dieser Owner suspendiert das aktive Packprogramm,
die zugehörigen Uniform-Programmzuordnungen, Alpha-/Blend-Overrides und die
OptiFine-Welt-/Gbuffer-Flags. Verschachtelte Scopes teilen sich den äußeren
Pack-Owner und stellen jeweils ihren eigenen GL-Zustand wieder her.

| Vertrag | Umsetzung und Zweck |
| --- | --- |
| Programm und Uniform-Namensraum | Während eines Clientprogramms zeigen OptiFine-Uniform-Handles auf Programm 0. Modell-/Layer-Helfer können über `Shaders.useProgram` weder das Clientprogramm ersetzen noch Uniformpositionen eines anderen Programms überschreiben. |
| Drawbuffer | `Shaders.setDrawBuffers` bleibt während des Client-Scopes suspendiert. Beim Eintritt aus dem Haupt-Gbuffer wird die unmittelbare Clientausgabe auf `COLOR_ATTACHMENT0` begrenzt; Gbuffer-Metadaten sind keine Client-Farbausgänge. Die Welt-Overlay-Brücke zeichnet ohnehin erst auf das finale Einzel-Farbziel. |
| Alpha und Blend | Pack-Overrides werden vor Clientarbeit entsperrt. Die logischen Zustände hinter den Locks und die Overrides werden beim Verlassen in der richtigen Reihenfolge wiederhergestellt. Uniform-Handles bleiben während dieser Cache-Setter suspendiert. |
| Read- und Draw-FBO | Getrennte Bindungen werden unabhängig gespeichert und wiederhergestellt, wenn die vorhandene Core-/ARB-/EXT-Blit-API dies unterstützt. Ein gelöschtes Ziel wird nicht unter seiner alten ID neu erzeugt. |
| Viewport und Kopierquelle | `RenderState.bindTarget()` nimmt den aufgenommenen Draw-Target samt Viewport und macht dieses Ziel zur Read-Quelle des aktuellen Clientpasses. `close()` stellt dagegen die ursprünglich getrennten Read-/Draw-Bindungen wieder her. |
| Cache und tatsächliches GL | Raw-Attribute/Matrizen werden vor der endgültigen Pack-Rückkehr restauriert und mit `GlStateManager` synchronisiert. Eine globale, pauschale Zustandsrücksetzung ersetzt diese Grenze nicht. |
| Reload | Ressourcen-Generation und lebendes Programm müssen zur Aufnahme passen. Ein alter Pack-Owner darf nach einem Reload keine veraltete Programm-/Drawbuffer-Metadatenstruktur wieder veröffentlichen. |

[ShaderUtil](../src/main/java/me/youngjibbit/utils/render/ShaderUtil.java) öffnet
für einen eigenständigen `init`/`unload`-Aufruf aus einem aktiven Pack einen
äußeren Scope. Bereits gekapselte Clientpässe verwenden ihren vorhandenen Owner.
Die bestehenden Uniform-Caches und festen Argument-Overloads bleiben erhalten.
Die zusätzlichen Scopes setzen Shaderunterstützung voraus; minimale native
Renderer-Proben dürfen dadurch `Shaders` nicht vor einer Minecraft-Instanz laden.

[GlowESP](../src/main/java/me/youngjibbit/module/impl/render/GlowESP.java) kehrt
nach zusätzlichem Modell-/Chest-Maskenrendern über den aufgenommenen Target zurück.
Ein erzwungener Wechsel auf das Minecraft-Haupt-FBO ist dort entfernt. Der
Shadow-Guard betrifft diese zusätzliche Maske, nicht die eigentliche Geometrie
des Shaderpack-Shadowpasses.

## 3. Besitz der Overlay-Ressourcen und Lebensdauer

Die [ShaderWorldOverlayBridge](../src/main/java/me/youngjibbit/utils/render/ShaderWorldOverlayBridge.java)
besitzt höchstens ein Alias-FBO, eine Tiefentextur in Shader-Weltauflösung sowie
bei abweichender Auflösung eine zweite Tiefentextur in Displaygröße und ein
kleines Resampling-Programm. Kamera- und Maskenbuffer bleiben wiederverwendbar.
Es wird keine zusätzliche, vorab zusammengeblendete RGBA-Overlay-Ebene angelegt.

Die finale Minecraft-Farbtextur ist **geliehen**. Sie wird nach jedem Callback
vom Alias-FBO abgehängt und niemals von der Brücke gelöscht. Die gemeinsame
`dfbDepthTextures[0]` wird gelesen, niemals als schreibbares Client-Depth-Attachment
verwendet. `glCopyTexSubImage2D` erzeugt die private Aufnahme nach dem späten
Hand-/Final-Pass. Gleiche Auflösungen benötigen keine weitere Shaderstufe.

Bei unterschiedlicher Auflösung liest ein GLSL-1.20-Programm die erste private
Tiefentextur und schreibt `gl_FragDepth` in die zweite. Quelle und angehängtes
Ziel sind verschiedene Texturen. Der Pass nutzt `NEAREST`, `GL_ALWAYS`, aktivierte
Tiefenschreibmaske und ausgeschaltete Farbschreibmaske. Er benötigt keine neue
Bibliothek und keine zusätzliche GL30-Pflicht. Der anschließende Event-Pass setzt
Weltmatrizen, Displayviewport, `LEQUAL`, Culling, Weiß als aktuelle Farbe und den
bekannten unbeleuchteten Zustand ohne Nebel. Die benötigten Texturen und Shader
setzt jeder Effekt innerhalb seines eigenen Scopes.

| Auslöser | Freigabe/Invalidierung |
| --- | --- |
| Welt-/Displaygröße oder Pack-Generation geändert | Veraltete Aufnahme wird verworfen; ein neuer gültiger Pass erneuert die betroffene private Ressourcengruppe. |
| Kein aktives Pack oder keine Welt-Overlay-Listener | `CustomEntityRenderer` gibt seinen Helper frei; ohne Pack bleibt der sofortige bisherige Event-Pfad erhalten. |
| Resource-Manager-Reload | `CustomEntityRenderer.onResourceManagerReload` führt `super` aus und schließt die eigenen Ressourcen im `finally`; Minecraft invalidiert zusätzlich vor dem Reload. |
| Welt-/Dimensionswechsel | Minecraft gibt vor dem Wechsel der Weltidentität die eigenen Renderer-Ressourcen frei. |
| Fenster-/Framebuffergröße | Freigabe erfolgt vor der Neuanlage des Minecraft-Ziels. |
| Shutdown | Freigabe erfolgt auf dem Context-Thread vor `Display.destroy`, das auch bei einem Cleanup-Fehler im `finally` erreichbar bleibt. |
| Freigabe aus einem laufenden Callback | Die Aufnahme ist bereits verbraucht; Detach und tatsächliches Löschen werden bis zum Callback-Ende verschoben. |
| Callback-Exception | Detach, GL-Restore und Pack-Restore laufen weiter; die Exception bleibt sichtbar und der Event wird nicht erneut abgespielt. |

## 4. Fehlerverhalten und noch offene Grenzen

Eine Aufnahme mit falscher Welt, Generation, Frame, Auge, PartialTicks oder
Dimension wird nicht auf andere Daten angewandt. Fehlende/gelöschte Quellen,
inkomplette FBOs oder ein nicht anlegbares Depth-Programm erzeugen eine konkrete,
bei gleichem Grund begrenzte Warnung `Shaderpack world overlay unavailable`.
Der ungültige Dispatch wird ausgelassen; Module und Benutzereinstellungen werden
dabei nicht ausgeschaltet. Dieser Zustand ist eine diagnostizierte Einschränkung,
kein erfolgreicher Kompatibilitätstest. Ein unbemerkter Fallback mit alter Kamera,
fremder Tiefe oder Schreibzugriff in Pack-Metadaten ist nicht implementiert.

Auch ein fehlerfrei vorbereiteter Pass garantiert noch keine Übereinstimmung mit
beliebigen Packeffekten: eigene Vertex-Verformung, zeitliches Jitter,
packeigene Tiefenkonventionen und transparente Materialien können eigene
Bildprüfungen verlangen. Die Nearest-Tiefenskalierung muss besonders an
Silhouettenkanten geprüft werden. Listener, die selbst globale Targets oder
Ressourcen verwalten, brauchen Prüfung in ihren tatsächlichen Modul-Kombinationen.

Die private Tiefenkopie und gegebenenfalls der Resampling-Draw kosten GPU-Arbeit.
State-Abfragen und Restore bleiben ebenfalls echte Arbeit. Es wird weder ein
FPS-Gewinn noch allgemeine Leistungsneutralität behauptet. Opt-in Zähler für
FBO-/Programmbindungen, Draw-Submissions und Scene-Copies helfen beim Vergleich.
Sie zählen API-Aufrufe einschließlich redundanter Bindungen und Displaylist-Aufrufe,
keine GPU-Abschlüsse; nicht jeder historische Raw-GL-Helfer ist instrumentiert.

## 5. Prüfabdeckung und Abnahme

| Prüfung | Konkrete Aussage | Status dieses Dokuments |
| --- | --- | --- |
| Java-8-Kompilation | Shader-Produktion, aktiver EntityRenderer und neue Prüfszenen sind typgeprüft. `Unsafe` wird ausschließlich zur begrenzten Testfixture verwendet. | PASS für den lokal benannten Arbeitsstand; vollständige Kandidatenrevision durch CI festhalten |
| `RenderStateBuffersTest` | Getrennte verschachtelte Snapshots, wiederverwendeter direkter Speicher, begrenzter Idle-Pool, Thread-Trennung | PASS |
| `RenderStateFramebufferCheck.selection` | Auswahl der vorhandenen Core-/ARB-/EXT-FBO-API | PASS |
| `WorldOverlayContractTest` | Bootstrap, Hook-Reihenfolge, Hand-/Final-/FP-Grenze, Shadow-Guard, Per-Eye-Final-Reset, Listener- und Event-Vertrag | PASS für lokale Strukturprüfungen; nativer Opt-in nicht aktiviert |
| [ShaderCompatibilityNativeCheck](../src/test/java/me/youngjibbit/utils/render/ShaderCompatibilityNativeCheck.java) | Reale GL-Programme mit kollidierenden Uniformpositionen, MRT-Metadatenschutz, Pack-Overrides/deferred state, getrennte Read-/Draw-FBOs, Viewport, verschachtelte/Exception-/Standalone-Scopes, Shadowprogramm und Reload-Generation. Diese MRT-/Split-FBO-Testfixture verlangt GL30; sie ist kein Beleg für einen GL2-only-Treiber. | NOT_RUN lokal |
| [ShaderWorldOverlayNativeCheck](../src/test/java/me/youngjibbit/utils/render/ShaderWorldOverlayNativeCheck.java) | Produktionsevent mit synthetischer finaler Farbe und später Handtiefe: Alpha/additive Pixelorakel, Kamera, Augenkanäle, Quellen-/Main-Tiefe unverändert, 1:1/halbe/überhöhte Weltauflösung, Wiederverwendung, Resize/Generation/Welt/Frame/Auge/PartialTicks, Pack off/on, Exception/Callback-Release, detach/lifetime | NOT_RUN lokal |
| Derselbe native Overlay-Check, Pass-Flags | Echte `beginRenderPass`, `setViewport`, `renderCompositeFinal`, `endRender` mit leeren Fixture-Packprogrammen: beide no-hand Augen brauchen je einen tatsächlichen Final-Draw, explizites Final erhält keinen zweiten Draw, Shadoweintritt verändert keine Hauptkamera-Flags | NOT_RUN lokal; kein externer Pack |
| Bestehende `WorldOverlayNativeCheck` / `RendererReloadNativeCheck` | Isolierter unmittelbarer Kamerahook beziehungsweise Renderer-/ShaderGroup-Reload; begrenzte Fixture statt Vollspiel | Kandidatenlauf separat aus CI übernehmen |
| Reales Spiel und benannte externe Packs | Tatsächliche Gbuffer-/Shadow-/Composite-/Final-Programme, native Fensterereignisse, Hardware-Framezeiten, gesamte Modul-Kombination | NOT_RUN |

Der Overlay-Check schreibt kleine Pixelnachweise nach
`shader-world-overlay-preview`. Der Kompatibilitätscheck verwendet ein getrenntes
`shader-compatibility-preview`-Verzeichnis. Logs, Bilder und Programmstatus müssen
gemeinsam zur tatsächlich geprüften Revision archiviert werden. Ein synthetischer
GL-Erfolg ändert die folgenden Pack-Zeilen nicht automatisch auf PASS.

### Geplante externe Packmatrix

Die Auswahl folgt der [offiziellen Downloadseite](https://sildurs-shaders.github.io/downloads/)
und den [Changelogs](https://sildurs-shaders.github.io/changelogs/) vom Abruf am
11.11.2026. Die dort genannte breite Minecraft-Unterstützung ist ein Auswahlgrund,
keine Zusage für den eingebetteten L5-Unterumfang.

| Pack, exakte Version/Preset | Zweck | Archiv-SHA-256 / geprüfte Clientrevision | Reales Pack-Ergebnis |
| --- | --- | --- | --- |
| Sildur's Enhanced Default **v1.20 Fast** | Einfacherer, kostengünstigerer Vertreter mit aktivem Pack und Clienteffekten | Bei Testaufnahme festzuhalten; noch kein Archiv als geprüft ausgewiesen | **NOT_RUN** |
| Sildur's Vibrant shaders **v2.02 High** | Aufwendigerer Shadow-/Composite-Vertreter | Bei Testaufnahme festzuhalten; noch kein Archiv als geprüft ausgewiesen | **NOT_RUN** |
| Optional: [SEUS v11.0](https://www.sonicether.com/shaders/download/v11-0/) | Zusätzlicher Vertreter aus der damaligen Shadergeneration | Noch nicht Bestandteil einer bestandenen Matrix | **NOT_RUN** |

Für jeden verbindlichen Pack-Row sind mindestens dieselbe Welt/Kameraroute,
Shaderpreset, Renderqualität, GUI-Scale, Auflösung, GPU/Treiber und Clientmodule
zu erfassen. Erforderliche Bildfälle sind Schatten, Wasser/Glas, Entitäten/Chests,
Welt-Overlays und Nametags sowie gleichzeitig HUD/ClickGUI-Blur, Glow und Glas.
Die Haupt- und Shadowkamera-Masken müssen getrennt beurteilt werden. Lifecycle-Fälle
sind Packwechsel und Pack-off/on, Resource-Reload, Welt-/Dimensionswechsel,
Fenstergröße, Vollbild und GUI-Scale. Beide Anaglyph-Augen und der Pfad ohne Hand
brauchen eigene Aufnahmen. Vorher-/Nachher-Framezeiten und Lastdaten kommen aus
dem gleichen Ablauf, nicht aus Compilerfolg oder einer einzelnen Methodenzeit.

## 6. Gepinnte Primärquellen und übertragene Ideen

Es wurde kein fremder Client vollständig kopiert und kein moderner Renderer
als Java-8-Implementierung transplantiert. Die folgenden Revisionen beziehen sich
auf konkret gelesene Quelldateien; spätere Repository-Änderungen sind damit nicht
stillschweigend einbezogen.

| Quelle / Revision | Untersuchte Stellen | Transfer und Gegenentscheidung | Stack und Lizenzbefund |
| --- | --- | --- | --- |
| Iris `bff1e69cb6c5519d8745784aa9c8b92984de67e7` | [CompositeRenderer](https://github.com/IrisShaders/Iris/blob/bff1e69cb6c5519d8745784aa9c8b92984de67e7/common/src/main/java/net/irisshaders/iris/pipeline/CompositeRenderer.java), [FinalPassRenderer](https://github.com/IrisShaders/Iris/blob/bff1e69cb6c5519d8745784aa9c8b92984de67e7/common/src/main/java/net/irisshaders/iris/pipeline/FinalPassRenderer.java), GlFramebuffer | Explizite Passausgänge, Ping-Pong-Lesezustände, zugehörige FBOs, Trennung von Read-/Draw-Zielen und klare Freigabe. In Neutron als eigene eng begrenzte Besitz-/Passverträge umgesetzt. | Moderner Minecraft-/LWJGL3-Stack; API nicht direkt portierbar. [LICENSE: LGPLv3](https://github.com/IrisShaders/Iris/blob/bff1e69cb6c5519d8745784aa9c8b92984de67e7/LICENSE). |
| LiquidBounce **legacy**, `03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e` | [FramebufferShader.kt](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/utils/render/shader/FramebufferShader.kt), [build.gradle](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/build.gradle) | Modellmaske plus Postshader als Vergleich. `setupFrameBuffer` löscht/erstellt pro Start; Stop erzwingt Haupt-FBO und Programm 0. Diese Churn-/Restore-Muster wurden bewusst nicht übernommen. | Tatsächlich Java 8 / Minecraft 1.8.9 Forge, nicht der aktuelle Fabric-Client. [LICENSE: GPLv3](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/LICENSE). |
| Meteor `81448b4b2e3b1bae83df60e836c13109ab2d8a1d` | [Renderer2D](https://github.com/MeteorDevelopment/meteor-client/blob/81448b4b2e3b1bae83df60e836c13109ab2d8a1d/src/main/java/meteordevelopment/meteorclient/renderer/Renderer2D.java), [PostProcessShader](https://github.com/MeteorDevelopment/meteor-client/blob/81448b4b2e3b1bae83df60e836c13109ab2d8a1d/src/main/java/meteordevelopment/meteorclient/utils/render/postprocess/PostProcessShader.java) | Getrennte Geometriebatches, explizite Attachments/Sampler/Pipelines, langlebige Postprocess-Ziele. Bestehende Neutron-Batches bleiben geordnet; kein Zusammenziehen über Blend-, Clip- oder Scene-Grenzen. | Moderne Pipeline-/Textur-APIs und Java-Konstrukte, kein direkter 1.8.9-Port. [LICENSE: GPLv3](https://github.com/MeteorDevelopment/meteor-client/blob/81448b4b2e3b1bae83df60e836c13109ab2d8a1d/LICENSE). |
| OptiFine `07ec2ca62f81aa52cb9ed1a7fd49a4489c131b1e` | [shaders.txt](https://github.com/sp614x/optifine/blob/07ec2ca62f81aa52cb9ed1a7fd49a4489c131b1e/OptiFineDoc/doc/shaders.txt) | Gbuffer-/Shadow-/Composite-/Final-Begriffe, Drawbuffer- und Größenverträge als Referenz. Jede konkrete Funktion wird am eingebetteten L5-Code geprüft. | Dokumentation für 1.21.11; moderne Compute-/Prepare-/Formatfunktionen sind kein L5-Nachweis. Keine gesonderte Lizenzfreigabe für eine Codeübernahme aus dieser Dokumentdatei nachgewiesen; Nutzung als zitierte Referenz. |

Die Lizenzbefunde geben die gelesenen Repository-Lizenzdateien wieder. Die
Implementierung in diesem Patch ist eigenständig und auf die vorhandenen
Neutron-/OptiFine-Einstiegspunkte zugeschnitten. Architekturähnlichkeit ersetzt
weder Portierungsprüfung noch native oder spielbezogene Abnahme.
