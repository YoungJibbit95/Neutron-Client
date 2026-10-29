# VIS-QA01 Ergebnisse

28.10.2026. QA01 abgeschlossen; STOPP nach diesem Auftrag. Keine QA02-, Gameplay-, CORE-, CMB-, SCF-, NET- oder Releaseänderungen, kein Push/Merge/Fetch und keine Subagents. Realspiel-/Display-/Shaderpack-/144-FPS-Freigaben bleiben NOT_RUN.

## Basis und Lieferung

Exakte Basis `534b8c4db8d3a6633eea6e663a74be17d18aadda`, Branch `codex/neutron-vis-02`, sauberer lokaler Worktree `F:/neutron-vis-02`. VIS-01 `7f79895f4033ba7e2e58af95bec2a3775d082ab0` als Vorfahr bestätigt. Zielbranch und Zielordner waren frei. Eigener Branch `codex/neutron-vis-qa01-gradients`, eigener Worktree `F:/neutron-vis-qa01-gradients`. Initialisierung ausschließlich hier mit worktree add --no-checkout und read-tree -mu HEAD; anschließend saubere Basis bestätigt.

ZIP extern unter `C:/Users/Administrator/Downloads/Neutron_QA01_sources_20261028` extrahiert. START zuerst gelesen, alle 26 Manifestquellen nach Bytezahl und SHA-256 unabhängig geprüft; direkt gelieferter Master bytegleich dem ZIP-Master. ZIP-SHA-256 `7e78018d70f4f48bf36755d84dfb0c9b4d63fdc6d8c89827b69d5e9005627345`. Review, Bugreport, v1.0, VIS-02-Code/Berichte/Tests/Patch sowie VIS-01-/Auditverträge als Quellen verwendet. Der historische VIS-02-Patch wurde niemals angewendet. Keine fehlenden oder erfundenen APIs.

Consumer-Matrix als erste eigene Datei vor neuen Tests/Produktionscode angelegt. Extern `before-production-receipt.json` bindet RED-Testquellen und Matrix an Hashes und bestätigt leeren Produktionsdiff vor dem Patch. Code/Test-Commit: `525c96ff8d0650767cfdfc5fb940daca7b6f6369`. Der folgende zweite lokale Commit enthält genau die vier Berichte. Sein vollständiger End-SHA und der sich darauf beziehende BASE..HEAD-Patchhash stehen in `review-receipt.json`, nicht als zirkulärer Selbsthash in diesem Bericht.

## Nutzerbugs: tatsächlich repariert und bereits korrekte Pfade

| Nutzer-ID | Befund und Ergebnis |
|---|---|
| VIS-P0-101 Hotbar | Eigener 2-s-Wallclockpfad reproduziert, sichtbar insbesondere bei unkonfiguriertem statischem Theme. Selected-Slot-Farbe jetzt UiEffects.GLOW; eigener Sinus/Helfer entfernt. Konfigurierter GLOBAL-Bloom war bereits kanonisch. Neun Slots/Items/Positionen und Ablauf unverändert |
| VIS-P0-102 ArrayList | GLOBAL-Palette/Text/Border/Fill am exakten VIS-02-Stand in QA01 nativ korrekt. Kein neuer Produktionsfix nötig. A→B→A, Methoden/Raten und bestehender Layout-/Local-/Exit-Rendercheck grün. Layout-frameDelta und unbenutzte Farbphase nicht blind entfernt |
| VIS-P0-103 Watermark | Theme-Stil ist bereits ein echter Shared-Textshaderconsumer, historische theme.isAnimated-Hypothese am VIS-02-Stand nicht reproduziert. Native Live-Theme-/Speedprobe grün. Wordmark/Status × Solid/Horizontal/Vertical/Theme × Glow ON/OFF belegt; explizite lokale Textstile bleiben unverändert |
| VIS-P0-104 ArmorHUD | Dekoration bereits global. Durability-Farbe driftete mit Phase/Methode und verlor Informationssemantik. Jetzt Verhältnisfarben 0/50/100% rot/gelb/grün, Semantic-Priority ON/OFF mit bestehendem Resolver, erhaltene Theme-Alpha; Barbreiten/Labels/Items unverändert |
| VIS-P0-105 ClickGUI | Panel-Titel bereits Shared-TEXT, läuft auch mit statischem Preset + global Animated; keine Produktionsänderung. iOS-Header-Glyphe war direkt mit altem 3-s-Sinus gefärbt und missachtete Gradients OFF/Static/Solid. Jetzt TextEffects.direct, Outlineargument aus BORDER-Sampler; Input/Transform/Komponenten unverändert |
| VIS-P0-106 Live-Themewechsel | Alle sechs realen Consumer im selben Prozess A→B→A ohne Reopen geprüft. Keine neue Theme-/Configschicht und kein Cachefix auf Verdacht. Ungeprüfte weitere GLOBAL-Consumer bleiben ausdrücklich DEFERRED |

## Exakte Datei-Allowlist

| Datei | Änderung |
|---|---|
| src/main/java/net/minecraft/client/gui/GuiIngame.java | ausschließlich dekorativer Hotbar-Colorpfad und sein nur dort verwendeter Interpolationshelfer |
| src/main/java/me/youngjibbit/module/impl/render/ArmorHUD.java | ausschließlich semantischer durabilityColor-Resolver |
| src/main/java/me/youngjibbit/gui/iosUi/iOS.java | Header-Textsampler, Outline-CPU-Farbargument, Entfernung des aktiven alten Sinuspfads |
| src/test/java/me/youngjibbit/module/impl/render/ArmorHudQa01Test.java | vier neue behavior-JUnitfälle mit vorhandenen APIs |
| src/test/java/me/youngjibbit/module/impl/render/VisQa01RenderCheck.java | eigenständiger nativer sechs-Consumer-Entry, echte Glyphen-/Pixel-/FBO-Proben |
| design/neutron-vis-qa01/VIS_QA01_CONSUMER_MATRIX.md | erster Code-Gate, Callgraphs, Quellenrollen, REDs und Scope |
| design/neutron-vis-qa01/VIS_QA01_GRADIENT_CONTRACT.md | erhaltene zentrale Verträge und drei minimale Reparaturen |
| design/neutron-vis-qa01/VIS_QA01_VISUAL_EVIDENCE.md | Fixture, Regionen, Bildserien, native Schutzanker und Grenzen |
| design/neutron-vis-qa01/RESULTS.md | dieser Bericht |

Genau 3 Produktionsdateien, 2 neue Testdateien, 4 Berichte. Bestehende Tests/Assertionen geändert: 0. UiEffects/ClientSettings, Flow/Ping-Pong/Pulse-GLSL, Theme-/Configowner, alle alten Value-IDs/-Defaults/-Optionen, ArrayList-/Watermark-/Panel-Produktion, VIS-01-Spring, Notifications, POM/Dependencies/Fonts/Assets und test_run sind bytegleich der Basis. Keine Gameplayänderung. Keine zusätzliche Scopefreigabe erforderlich.

## Ausgeführte Tests

Oracle JDK 1.8.0_391, Maven 3.8.5, sämtliche Mavenaufrufe offline `-o`, explizites JAVA_HOME und MAVEN_OPTS=-Xmx512m. Maven clean ausschließlich im eigenen target. `toolchain.txt`, `full-suite-counts.json` und `bytecode-major52.json` enthalten unabhängige Nachweise.

| Lauf / Log relativ zu F:/neutron-vis-qa01-evidence | Tests | Failures | Errors | Skips | Ergebnis |
|---|---:|---:|---:|---:|---|
| baseline-focused.log | 66 | 0 | 0 | 0 | unveränderte VIS-01-/VIS-02-/Theme-/Config-/Notification-Anker vor Produktionscode |
| red-armor.log | 4 | 3 | 0 | 0 | echte behavior-RED, keine Compile-RED |
| red-armor-verified.log | 4 | 3 | 0 | 0 | dieselben Armor-Fehler während Erweiterung der nativen Consumerprobe bestätigt |
| green-focused.log | 70 | 0 | 0 | 0 | 66 alte + 4 neue Fälle grün |
| full-clean-test.log, mvn -o clean test | **570** | **0** | **0** | **3** | BUILD SUCCESS, 99 frische XML-Klassen, 567 ohne Skip |

Die 566er-VIS-02-Basis bleibt vollständig erhalten; vier neue JUnitfälle ergeben 570, native Unterprogramme werden nicht zusätzlich gezählt. Unabhängige XML-Summe 570/0/0/3. Die drei alten Opt-in-Skips in WorldOverlayContractTest, GuiChatInputTest und GuiOverlayDebugGlStringsTest gelten nicht als bestanden. Vorhandene negative Fixture-/Fehlerpfadlogs innerhalb der grünen Suite sind keine zusätzlichen Surefire-Errors. Kein tatsächlicher Login oder Benutzeraccountdatensatz wurde benutzt.

Vollsuite enthält finalen Produktions-, JUnit- und Nativequellstand. Danach keine Code-/Teständerung. Alle 7 geänderten Produktions-/Inner-Class-Dateien haben Java major 52. Keine Assertions gelockert, keine Compilefehler als behavior-RED ausgegeben.

## Native Ergebnisse und konkrete Pixelbelege

| Lauf | Exit / Status |
|---|---|
| native-red.log | 1, erster Fixture-/Behaviorbefund, 197 PNGs; kein Abnahmelauf |
| native-red-verified.log | 1, sieben bestätigte Verhaltensverletzungen vor Produktionscode, 283 PNGs |
| native-green.log | 0, erste vollständige GREEN-Probe, 313 PNGs, Fill Strength 1; Extremkontrastgrenze dokumentiert |
| native-final.log | **0**, finale Standard-Fill-.28-Serie: **313 PNGs**, 289 Consumerbilder + 24 Scale-Paritätsbilder; 163 benannte Regionenvergleiche |
| native-vis02-regression.log | 0, unveränderte VIS-02-Probe: 36 PNGs, 1.280 Referenzpixel, max. 1/255, FBO 10:10 |
| native-menu-regression.log | 0, unveränderte VIS-01 MainMenuVisualCheck |
| native-arraylist-regression.log | 0, unveränderte ArrayListRenderCheck |
| native-panel-regression.log | 0, unveränderte ClickGuiRenderCheck |

Finale Consumerbilder: Hotbar 47, ArrayList 46, Watermark 61, ArmorHUD 47, Panel 45, iOS 43. Hilfsbilder des alten ArmorHudCheck liegen separat unter armor-preview und werden nicht in die 313 eigenen finalen PNGs hineingerechnet. Vorläufige/RED-/Regressionserien werden nicht zu einer angeblichen finalen QA01-Bildzahl aufsummiert.

Nativefinal: neue Scale-Parität **216 echte Pixel, maximale Abweichung 0/255** bei effektiven Scales 1–4, drei Methoden, zwei Phasen. Reale Panel-Titelmaske: mindestens **599 opake Glyphenpixel**, maximaler mittlerer RGB-Fehler **0.573/255**. iOS-Header: **33 opake Glyphenpixel**, maximaler mittlerer RGB-Fehler **4.849/255** einschließlich bestehender 95%-Deckkraft; Grenze 14/255 wegen Alpha-/Hintergrundkomposition. Dies prüft Glyphen, nicht bloß den Fill im Titelbereich.

Live A→B: Hotbar 2.136, ArrayList 17.447, Watermark 1.197, Armor 502, Panel 5.120 und iOS 1.020 veränderte Pixel in den benannten Regionen. A→B→A zurück: jeweils **0** unterschiedliche Pixel. Unkonfigurierter Hotbar-Staticvergleich nach 180 ms Wallclock: **0** veränderte Pixel. Watermark: 14 unabhängige Stil-/Glow-Pixelvergleiche, jeweils mindestens **834** veränderte Pixel (`watermark-pixel-comparisons.json`).

Finale QA01-FBO-Bilanz **30 erzeugt / 30 freigegeben**, GL-Prüfungen sauber, keine geleakte Scene/Batch. Ressourcenrelease/Recreate und simulierte Resizepfade erhalten die kanonische Phase. Die vier Bestands-Renderchecks sind eigenständig neu ausgeführt. Isolierte Mikrozeiten sind keine 144-FPS-Freigabe.

Bildsichtung: finale Hotbar mit neun Itemstacks, Armor mit 100/50/0%-Labels und gelber/grüner Bar, Wordmark, Panel- und iOS-Titel/Icons sowie mehrzeilige ArrayList geöffnet. Standard-.28-Serie ist lesbar; keine universelle Kontrastfreigabe beliebiger Custom Themes oder maximalem Fill 1. Dateien/Regionen/Settings stehen in native-final/pixel-evidence.json, PNG-Maße/Hashes in screenshot-manifest.json, Summen in native-final-summary.json. Sämtliche Originalbilder unveränderte Pbuffer-Readbacks.

## Die 16 Pflichtgates

PASS gilt jeweils im bezeichneten Offline-/Pbufferkontext, nicht als Realspielabnahme.

| Gate | Urteil | Nachweis / Grenze |
|---|---|---|
| Q01-01 | PASS Pbuffer | native-final liveThemes: sechs echte Consumer, offene Screeninstanzen, A→B→A, benannte Regionen; Weltspiel NOT_RUN |
| Q01-02 | PASS | native-final ratesAndMethods: Animated, .1/.8, identische Zeit .2/.6, gemessene Phasen und Consumerpixel; Theme-Motion unverändert |
| Q01-03 | PASS begrenzt | Vis02GradientTest Sync-Vertrag, UiEffectsTest, echte GLOBAL-Surfaces/Text/Glyphen und Methodenbilder; keine RGB-Gleichheit unterschiedlicher Positionen verlangt |
| Q01-04 | PASS begrenzt | vorhandene getrennte Channel-/Theme-/Local-Legacy-Tests, native Sync OFF/Shared OFF, ArrayList Local Legacy, Watermark lokale Stile; keine Overrides überschrieben |
| Q01-05 | PASS Pbuffer | drei Methoden in sechs Consumers × zwei Themes × zwei Raten × zwei Zeitpunkten; unveränderte VIS-02 CPU-/GLSL-Referenz grün |
| Q01-06 | PASS begrenzt | native Freeze für Ambient/Reduced/Animations/Static/Solid/Gradients OFF, vorhandene Pause-/Resume-/Motion-/Hover-JUnitfälle; kein echter Alt-Tab-Test |
| Q01-07 | PASS Pbuffer + Diff | echte neun Slotitems, Hotbar-Fallback-RED→GREEN, GLOW-Sampler; Render-/Input-/Item-/Eventlogik im Diff unverändert |
| Q01-08 | PASS Pbuffer | echte mehrzeilige GLOBAL-ArrayList, Livepalette/-Rate; unveränderte ArrayListRenderCheck Sort/Exit/Scale/Fonts/Local/Anchors, kein Layoutzeitumbau |
| Q01-09 | PASS Pbuffer | Theme/Horizontal/Vertical/Solid × Wordmark/Status × Glow ON/OFF; 14 eigene Pixelvergleiche und globale Rate-/Live-Themeproben; lokale Values unverändert |
| Q01-10 | PASS begrenzt | vier neue Armor-JUnitfälle, echte Items/Labels/Bars 0/50/100%, Native-Surfacewechsel und Semantic ON/OFF, bestehender Item-State-/NBT-Check; allgemeiner Kontrastreview OPEN |
| Q01-11 | PASS Pbuffer | statische Presets + global Animated: Panel-Titel 599 echte Pixel gegen CPU; iOS-Header 33 Pixel gegen CPU; bestehende Input-/Transformtests grün, keine Komponentenänderung |
| Q01-12 | PASS | vollständige unveränderte VIS-01-/VIS-02-/Config-/MainMenu-/Settingssuite und native MainMenu-/Panel-Regressionsanker |
| Q01-13 | PASS Pbuffer | 216 neue Pixel in Scales 1–4, max. 0/255; 1.280 unveränderte VIS-02-Pixel max. 1/255; reale Glyphenparität zusätzlich |
| Q01-14 | PASS begrenzt | 12×sechs Draws bei identischem Timestamp ohne Phasenzuwachs, Vis02ClockTest High-water/Pausencap, keine neue Produktionsclock |
| Q01-15 | PASS Pbuffer / NOT_RUN real | 30:30 FBO, GL-/Scene-/Batchchecks, Release/Recreate, simulierte Resize-/NoShader-/NoFBO-Pfade. iOS kompletter NoShader-Backdrop, native Display-/Reload-/Fullscreen-/Shaderpack-/144-FPS-Prüfung NOT_RUN |
| Q01-16 | PASS | JDK8 mvn -o clean test 570/0/0/3, 99 frische XMLs unabhängig summiert, 7 Klassen major52, exakte 9-Datei-Allowlist, kein Gameplaydiff |

## Git-Schutz und externe Artefakte

34 vorhandene Worktrees wurden vor Anlage read-only mit vollständigem Porcelainstatus, Branch/HEAD sowie SHA-256 von Indexdiff und getracktem HEAD-Diff aufgenommen. Nachaufnahme und Vergleich stehen extern in worktrees-after.json / worktrees-comparison.json. Es gab keine Schreib-, Staging- oder Checkoutoperation in fremden Worktrees; fremde parallele Änderungen werden nur ausgewiesen und niemals korrigiert. Eigene Gitmetadaten sind die regulären Worktree-/Branchoperationen, kein fremder Arbeitsbaum wurde angefasst.

Nachaufnahme: **alle 34 vorher vorhandenen fremden Worktrees sind in sämtlichen aufgenommenen Merkmalen unverändert**, einschließlich Originalcheckout und VIS-02. Die Registry enthält jetzt 37 Einträge: den eigenen QA01-Worktree und zwei unabhängig neu registrierte fremde Worktrees zusätzlich zur Ausgangsmenge. Diese neuen Registrierungen wurden nur gelesen, nicht von QA01 angelegt. Die eigene Aufnahme lag nach dem Codecommit vor dem Berichtcommit; der finale eigene saubere HEAD/Status wird zusätzlich im Reviewreceipt geprüft.

Nur fünf explizite Code-/Testpfade im ersten Commit, genau vier Berichte im zweiten. git diff --check und Allowlistprüfung vor Commit. Externer `VIS-QA01-review.patch` enthält BASE..HEAD einschließlich Berichten; `VIS-QA01-review.patch.sha256` und `review-receipt.json` binden Basis/End-SHA, Dateiliste, sauberen eigenen Status, Patchhash und `git apply --check --reverse`. `artifact-manifest.json` enthält Hashes der externen Belege, ohne sich selbst zirkulär zu hashen. Keine Binärbilder oder Logs im Repository.

## Verbleibend / STOPP

Kein offener notwendiger Produktionsfix innerhalb der sechs gemessenen QA01-Pfade. Die historischen Vermutungen für ArrayList/Watermark/Panel wurden am fertigen VIS-02-Stand differenziert geprüft, nicht als sechs unabhängig reparierte Implementierungsfehler ausgegeben. Weitere GLOBAL-Consumer/Themevarianten sind DEFERRED; Max-Fill-/Custom-Theme-Kontrast ist OPEN. Realspiel, natives Fenster/Fullscreen/F3+T, OptiFine-/Sildur-Shaderpack und 144 FPS sind NOT_RUN. Keine vollständige Produkt-/Release-/Cross-Track-Abnahme. QA02 nicht begonnen. STOPP nach VIS-QA01.
