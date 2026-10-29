# VIS-QA01 sichtbare Evidenz

Alle Binärbilder, Logs und Pixelreceipts liegen extern unter `F:/neutron-vis-qa01-evidence`. Keine Originalbilder waren im Quellen-ZIP; alle nachfolgend bezeichneten Läufe wurden hier neu ausgeführt. Pbuffer ist ein echter GPU-Kontext, kein gestartetes Minecraft-Spiel und kein natives Display.

## Reproduzierbare Fixturebedingungen

Oracle Java 1.8.0_391 / Maven 3.8.5 offline. LWJGL2-Natives aus dem eigenen `test_natives/windows`; eigener JVM-Prozess und externes Arbeitsverzeichnis pro Lauf. NVIDIA GeForce RTX 3070 Ti, OpenGL 4.6.0 NVIDIA 616.92, GLSL-1.20-Quellen im Kompatibilitätskontext. Gerät/Version stehen auch im neu ausgeführten `native-vis02-regression.log`.

Eigener `VisQa01RenderCheck`: echte GuiIngame.renderTooltip mit neun synthetisch belegten Slots und Vanilla-Itemmodellen; ArrayListHUDModule mit drei synthetischen Modulen; WatermarkHUDModule; ArmorHUD.layout/paint mit echten Items und 0/50/100%-Zuständen; offener Panel-ClickGui und offener iOS-Screen. Keine Welt, kein Server, keine Accounts oder Benutzerdateien. ArmorHudCheck initialisiert den echten Atlas/Model-/Glintpfad und prüft Slotmapping, beide Layouts, Zustandswiederherstellung und unverändertes Item-NBT.

Die Gradientenzeit kommt ausschließlich aus der vorhandenen UiEffects-Testnaht. Normale Schritte sind höchstens 25 ms. draw ruft den vorhandenen Overlay-/EntityRenderer-Eintritt beginFrame bei gleichem Timestamp nach; kein eigener Consumerfortschritt. iOS-Partikel sind im Fixture entfernt und die bestehende Öffnungsgeometrie auf ihren Endzustand gesetzt, damit Pixelvergleiche Farbänderungen messen. Das verändert keinen Produktionscode oder Inputvertrag. Zwei unveränderte iOS-Warmup-Draws setzen die bestehende Auswahlmarkierung vor A→B→A.

## RED, erste GREEN-Probe und finale Serie

`native-red`: erster Versuch mit 197 PNGs; zusätzlich zum tatsächlichen Armor-RED enthielt er einen iOS-Erstframe-Vergleichsfehler im Fixture. Dieser bleibt nachvollziehbar erhalten und gilt nicht als Produktfehler.

`native-red-verified`: unveränderter Produktionscode, verbesserte gezielte Regionen, 283 PNGs, Exit 1. Sieben echte Verletzungen: unkonfigurierte Hotbar trotz statischer Policy; iOS-Header bei Gradients OFF in zwei Themes, Static und Solid; Durability-Phasendrift und fehlende Semantic-Priorität. `violations.json` enthält die genaue Liste. Es war kein Compile-RED.

`native-green`: erster erfolgreicher QA01-Lauf nach den drei Produktionspatches, 313 PNGs. Alle Consumer-/Policyassertionen bestanden; 216 neue Scale-Referenzpixel mit maximal 0/255 Abweichung; iOS-Glyphenmaske mit 33 opaken Pixeln und maximalem mittleren RGB-Fehler 2.506/255; FBO 30 erzeugt / 30 freigegeben. Er verwendete absichtlich Gradient Fill Strength 1. Die Bildsichtung zeigte bei diesem Extremwert geringe Text-/Flächenkontraste bis zum optischen Verschmelzen von identisch gesampeltem Titel und Fill. Dieser vorhandene Extremfall wird nicht als allgemein kontrastfreigegeben ausgegeben.

Die finale Serie verwendet den vorhandenen Standard-Fillwert `.28`, Surface=Gradient, Opacity=1, Horizontal, Animated, Sync ON, Blur/Glow zunächst OFF. Zusatzbilder decken explizites Glow und Local ab. Sie ergänzt einen separaten realen Panel-Titel-Glyphenmaskenvergleich gegen den CPU-Sampler. Finale Laufbezeichnung, Zahlen und Hashreceipts stehen in RESULTS.md; vorläufige Serien werden nicht zu finalen Consumerbildern addiert.

Final: `native-final.log`, Exit 0, 313 eigene PNGs (289 Consumerbilder, 24 Scale-Paritätsbilder), 163 benannte Regionenvergleiche. Titelmaske mindestens 599 opake Pixel, maximaler mittlerer RGB-Fehler 0.573/255; iOS-Icon 33 Pixel, maximal 4.849/255 bei 95%-Deckkraft. Scaleparität 216 Pixel mit maximal 0/255, FBO 30:30. Finale Watermark-Stil-/Glowvergleiche: 14, mindestens 834 veränderte Pixel je Paar. Finale Panel-/iOS-/ArrayListbilder geöffnet und lesbare Titel, Icons und Zeilen bestätigt. Die ersten Fill-1-Bilder bleiben getrennte Kontrastevidenz, keine universelle Produktfreigabe.

## Native Mindestmatrix und Pixelregionen

Themes: SynthWave (`FF0077FF` → `FFFF00A7`) und Bonfire (`FFFF8000` → `FFFF0000`), ohne Themeobjekt-Caches oder Neuinstanzierung. Pro Consumer: beide Themes × Flow/Ping-Pong/Pulse × Rate .1/.8 × Zeit .2/.6 s. Damit ergeben sich Phasen .02/.06 beziehungsweise .16/.48. Der Ratevergleich verwendet identisches dt; Theme-Motion wird dafür ausdrücklich nicht umgedeutet. Gradients OFF in beiden Themes, A→B→A im offenen Screen, Ambient/Reduced/Animations/Static/Solid und Sync OFF kommen hinzu.

| Consumer | Physische Region bei 960×640 / Scale 2 | Was sie belegt / Grenze |
|---|---|---|
| Hotbar | x300,y590,w356,h6 | dekorativer oberer Surface-/Borderstreifen; keine Itemfarben als Gradientbeleg. Unkonfigurierter Staticvergleich zusätzlich x370,y570,w210,h65 erfasst Selected-Slot-Halo |
| ArrayList | x20,y40,w250,h90 | reale mehrzeilige Text-/Fill-/Borderregion; Stepped-Layout/Sort/Exit/Scale separat im unveränderten ArrayListRenderCheck |
| Watermark | x20,y20,w190,h50 | reale Wordmark-Glyphen; Themechange/Rate. Wordmark und Status zusätzlich in allen vier Textstilen und mit/ohne Glow |
| ArmorHUD | x204,y204,w180,h4 | dekorativer Surface-/Borderstreifen; tatsächliche Durabilitylabels/-bars separat in semantic-priority-on/off-Bildern und JUnit |
| Panel | x48,y36,w160,h32 | tatsächlicher Header-/Titelbereich; finale Serie prüft zusätzlich opake Titelglyphen gegen CPU, um reine Filländerung nicht als Titelbeleg zu zählen |
| iOS | x338,y128,w30,h34 | Header-Icon; zusätzlich opake Glyphenmaske mit 33 Pixeln gegen denselben physischen TEXT-Sampler. Keine Hintergrund-/Partikeländerung als Iconparität |

`pixel-evidence.json` nennt Bilddatei, Consumer, Palette, Methode, Motion, Rate, Phase, Zeit, Gradients/Sync, Fill Strength, Region und RGB-Probe. Vergleichszeilen nennen beide Dateien, Region und Zahl veränderter Pixel. `screenshot-manifest.json` bindet Original-PNGs an Maße und SHA-256. Bilder sind unveränderte RGB-Readbacks, Standard-sRGB ohne gemessene Monitorkalibrierung.

CPU-/GLSL-Scaleprobe: effektive Scales 1/2/3/4, 1280×960, Flow/Ping-Pong/Pulse, zwei Phasen, je neun Pixelzentren einschließlich Viewportkanten. Dies ist eine Samplerparitätsprobe, keine vollständige Welt-HUD-/Inputabnahme aller Consumers bei jedem Scale. Die unveränderte VIS-02-Probe ergänzt alle fünf Modes, vier Achsen, vier Phasen und 1.280 Referenzpixel.

## Unveränderte native Schutzanker, hier neu gelaufen

| Lauf | Ergebnis und Bedeutung |
|---|---|
| native-vis02-regression | Exit 0; 36 PNGs, 1.280 Referenzpixel, max. 1/255; MainMenu/ArrayList Global, Freeze, Ressourcen-/Fallback, Notification-Symbole/-Karten, 10:10 FBO |
| native-menu-regression | Exit 0; unverändertes MainMenuVisualCheck: Presets, Hover/Focus/Pressed/Cancel, Capture, kleine/ungerade Größen und Scales 1–4 |
| native-arraylist-regression | Exit 0; unveränderte Layouts/Fonts/Padding/Sort/Exit/Anchors, Glow/Blur, GL-State, FBO-Reuse/Resize/Release |
| native-panel-regression | Exit 0; unveränderte Controls, Suche, Keybind, Zahlen, Scroll/Drag/Resize, Clipping, Theme und Bloom-Reuse |

Die isolierten Mikrozeiten der Bestandsprogramme sind keine FPS-Zusage. Die Programme liefen in getrennten Prozessen; parallele Offscreen-Regressionen liefern erst recht keine Performance-Rangfolge.

## Grenzen

NoShader/NoFBO sind bewusst erzwungene Flags im realen GL-Kontext. Surface/Text bleiben lesbar; keine identische Shaderanimation behauptet. Der iOS-Menübackdrop benötigt Shader, sein vollständiger NoShader-Ausfall ist NOT_RUN. Ressourcenrelease/Recreate und simulierte FBO-Resizes sind getestet; native OS-Fenster-/Fullscreen-/F3+T-/Shaderpack-Ereignisse, Realspiel und 144 FPS sind NOT_RUN. Komplexe Custom-Theme-/Monitorkontrastfreigabe bleibt OPEN. Keine globale Abnahme fremder GLOBAL-Consumer oder QA02.
