# VIS-02 Ergebnisse

Stand 28.10.2026. VIS-02 implementiert und lokal geprüft. STOPP nach VIS-02. Produkt-/Kontrastreview bleibt OPEN; Realspiel, Shaderpack und 144 FPS sind NOT_RUN. Keine Subagents, Fetches, Pushes, PRs, Merges, Rebases, Tags, Stashes, Resets oder Git Clean. Maven clean ausschließlich im eigenen Worktree.

## Version und Scope

Basis: sauberer lokaler codex/neutron-vis-01, `7f79895f4033ba7e2e58af95bec2a3775d082ab0`. Git bestätigt `e3492be7a0a415491e87fda23b2cb7f282fa2a4c` als Vorfahr. Eigener Branch codex/neutron-vis-02 in F:/neutron-vis-02, beide Ziele vor Anlage frei. Umsetzung: `635522d63a4b7edfb965e83eb8c52fc28d79445b`. Der folgende Dokumentationscommit enthält genau die vier freigegebenen Berichte. Dessen vollen End-SHA, den BASE..HEAD-Patchhash und Reverse-Apply-Status enthält extern review-receipt.json; der Bericht kann den Hash eines sich selbst enthaltenden Commits nicht vorab enthalten.

Zusatzquellen außerhalb aller Worktrees unter F:/neutron-vis-02-sources. START zuerst gelesen, alle 16 Manifest-Dateien auf Länge und SHA-256 geprüft. Direkt gelieferter Master ist bytegleich dem ZIP-Master. input-receipt.json enthält ZIP- und Masterhash, source-verification.json alle Einträge. Aktueller Master/Nutzerauftrag bestimmt den Scope; historische VIS-01-Grenzen sind keine VIS-02-Verbote. CMB/SCF wurden nicht übernommen. Code-Gate wurde vor Produktionsänderung geschrieben. ShaderUtil/Theme/FBO/Core bleiben unverändert.

## Jede geänderte Datei

| Datei (relativ zum eigenen Worktree) | Grund |
|---|---|
| src/main/java/me/youngjibbit/utils/render/UiEffects.java | zentrale dt-Begrenzung/High-water-Zeit, Ambient-Priorität, Mode 3/4 CPU, reiner SemanticRole-Resolver |
| src/main/java/me/youngjibbit/module/impl/client/ClientSettings.java | genau drei neue Values und Gruppeneinträge |
| src/main/resources/assets/minecraft/Flauxy/Shaders/uiGradient.glsl | Erweiterung des vorhandenen vec3-Modetransports, identische CPU-/GLSL-Formeln |
| src/main/java/me/youngjibbit/notifications/NotificationManager.java | minimale iconColor-Resolveranbindung; bestehende Form-/Text-/Lifetimepfade erhalten |
| src/test/java/me/youngjibbit/utils/render/UiEffectsTest.java | RED-belegter Zeit-Fixtureadapter für normalen kontinuierlichen Verlauf; 38 Assertions bytegleich |
| src/test/java/me/youngjibbit/utils/render/Vis02TestContext.java | echter Settings-/Owner-Fixture mit zentraler Fakeclock und Restore |
| src/test/java/me/youngjibbit/utils/render/Vis02ClockTest.java | drei dt-/Pause-/Aggregationstests |
| src/test/java/me/youngjibbit/utils/render/Vis02GradientTest.java | zwölf Methoden-/Channel-/Motion-/Legacy-/Semantiktests |
| src/test/java/me/youngjibbit/module/impl/client/ClientSettingsVis02Test.java | vier Tests: vollständiges 46-Value-Altsnapshot, neue Defaults/Gruppen, echter Configcodec |
| src/test/java/me/youngjibbit/notifications/Vis02NotificationTest.java | zwei Tests am echten Icon-Farbconsumer und an Timeline/Coalesce/Expiry/Promotion |
| src/test/java/me/youngjibbit/gui/MainMenuVis02Test.java | ein Test an tatsächlichem Hover/Reduced/Snap/Capture/Bounds |
| src/test/java/me/youngjibbit/utils/render/Vis02RenderCheck.java | neuer ausführbarer Pbuffer-Entry mit echten Pixeln/Consumern/Fallback/Ressourcenprüfungen |
| design/neutron-codex-vis-02/VIS_02_CODE_GATE.md | Befund, Callgraph, Formeln und Adapter-RED vor Produktions-/Fixtureänderung |
| design/neutron-codex-vis-02/VIS_02_GRADIENT_CONTRACT.md | verbindliche Zeit-/Mode-/Channel-/Legacy-/GPU-Verträge |
| design/neutron-codex-vis-02/VIS_02_SEMANTIC_MOTION.md | Settingskompatibilität, Prioritätstabelle, echte Semantic-Call-Site |
| design/neutron-codex-vis-02/RESULTS.md | dieser Abschlussbericht |

Allowlist: PASS, 4 Produktionsdateien, 8 Code-Testdateien einschließlich eines bestehenden zeitlichen Adapters, 4 Berichte. MainMenu, MenuMotion/MenuSpring, TextEffects, ArrayListHUDModule/ArrayListSurface, Theme/ThemeCatalog/Runtime/Store, ShaderUtil, Fonts, Config-/Value-/Module-Core, Minecraft/Pulsar, POM, Dependencies, Gameplay und test_run sind im Patch unverändert. Alte Gradient-IDs/Optionen/Defaults und Local-/Legacy-Verträge bleiben erhalten. Es gibt keinen neuen Clock-/Theme-/Configmanager.

## Tatsächlich ausgeführte JDK8-Läufe

Oracle Java 1.8.0_391, Maven 3.8.5 über den absoluten Wrapper-Distributionspfad; JAVA_HOME=C:/Program Files/Java/jdk-1.8, MAVEN_OPTS=-Xmx512m. Sämtliche Maven-Aufrufe mit -o; auch Classpathaufbau. toolchain.txt, javap-major52.txt und bytecode.json bestätigen Version und Major 52 (15 Produktionsklassen einschließlich Inner Classes). Keine POM-/Dependencyänderung. Bestehende Offline-Pluginwarnungen wurden nicht durch Downloads umgangen.

Alle Evidenzpfade nachfolgend relativ zu F:/neutron-vis-02-results.

| Lauf / Log | Tests | Failures | Errors | Skips | Befund |
|---|---:|---:|---:|---:|---|
| baseline-focused.log | 44 | 0 | 0 | 0 | vor Produktionscode alle sieben geforderten Bestandsklassen grün |
| red-clock.log | 3 | 2 | 0 | 0 | echte Failure-RED: 60 statt .1 s, .15 statt .1 s nach rückläufigem Reading |
| red-api-compile.log | 0 ausgeführt | — | — | — | Compile-RED: fehlende drei Values, SemanticRole/-API, iconColor; keine Surefirefälle behauptet |
| first-green-and-adapter-red.log | 29 | 3 | 0 | 0 | zwei alte Uhrfixtures erwarten ungekappte Sekunden; eine neue zu strenge Alphaassertion |
| green-focused.log | 66 | 0 | 0 | 0 | alle sieben alten und fünf neuen Testklassen grün |
| full-clean-test.log (mvn -o clean test) | **566** | **0** | **0** | **3** | frischer Clean-Lauf, BUILD SUCCESS, 98 XML-Klassen, 563 ohne Skip |

22 neue JUnitfälle sind in 566 enthalten und werden nicht nochmals addiert. Die drei unveränderten Opt-in-Skips betreffen WorldOverlayContractTest, GuiChatInputTest und GuiOverlayDebugGlStringsTest; sie sind nicht bestanden. XML unabhängig ausgewertet: full-suite-counts.json, full-surefire/ und focused-counts.json. Fokus-Snapshots können ältere XML enthalten; die Summierung wählt die tatsächlich im jeweiligen Lauf ausgeführten Klassen explizit aus. Keine Unterprozessausgaben zusätzlich als Surefirefälle zählen.

Fixtureadapter nach dokumentiertem RED: ausschließlich Zeitsteuerung in UiEffectsTest, keine Assertion/Toleranz/Erwartungswerte entfernt oder abgeschwächt; old-assertion-receipt.json bestätigt 38 bytegleiche Assertion-Texte. Neue Pulse-Alphaassertion wurde nach tatsächlichem Befund auf die unveränderte 254/255-Quantisierung des Themeinterpolators korrigiert. Kein Themeeingriff.

Die Vollsuite umfasst den finalen Produktionscode und sämtliche finalen JUnitfälle. Danach wurde ausschließlich der neue eigenständige Native-Entry kompiliert: eigener Erfolgslogtext für den strengen Diagnosefilter korrigiert und vier tatsächlich sichtbare Karten bei GUI-Scale 1 ergänzt. final-native-compile.log: BUILD SUCCESS. Kein bestehendes Nativefixture und kein JUnitfall danach geändert. Native-Quellversion entspricht 635522d63a4b7edfb965e83eb8c52fc28d79445b.

## Native Ausführung und Vorfälle

Alle JVMs mit JDK8, LWJGL2-Natives aus dem eigenen test_natives/windows und externen Arbeitsverzeichnissen. Reale Pbuffer-/FBO-/Shaderkompilierung, kein gestartetes Spiel, Accountlogin, Server oder Save. GPU: NVIDIA GeForce RTX 3070 Ti, OpenGL 4.6.0 NVIDIA 616.92. GLSL-Quellen behalten #version 120; das ist ein Kompiliernachweis auf diesem Kompatibilitätskontext, kein Nachweis für jede GL-1.20-Implementierung.

| Native Programm / Log | Exit | Status |
|---|---:|---|
| MainMenuVisualCheck, native-smoke.log, vorhandener Baseline-Modus auf bereits geändertem Code | 0 | echte Pbuffer-Ausführbarkeit vor neuem Native-Entry bestätigt; kein Vorhervergleich |
| Vis02RenderCheck, native-verified.log | 0 | finaler VIS-02-Pufferlauf vollständig PASS |
| MainMenuVisualCheck, native-menu-regression.log | 0 | unveränderte VIS-01-Fixtures: Presets, Actions/Bounds, Capture, Titel, Scales 1–4, Custom Theme, Effekt-OFF |
| ArrayListRenderCheck, native-hud-regression.log | 0 | unveränderte lokale Layouts/Fonts/Filter, Glow/Blur, Anchors, GL-State, FBO-Reuse/Resize/Release |
| ClickGuiRenderCheck, native-gui-regression.log | 0 | unveränderte Controls, Suche, Keybinds, Nummern, Scroll, Drag/Resize, Theme, Clipping, Bloom |

Vorfälle bleiben erhalten: native-before.log scheiterte mit Class-not-found, weil das vorherige Compile-RED Testklassen nicht hinterlassen hatte; dies ist kein negativer GPUbefund und kein erfolgreicher Vorherlauf. native-initial.log und native-final.log sind zwei fehlgeschlagene Nativeversuche: ihre Pixel-/Lebensdauerassertions liefen durch, aber der strenge unveränderte Diagnosefilter wies die eigenen Erfolgstexte mit error bzw. GL_NO_ERROR zurück. Nur eigener Logtext korrigiert, kein Filter/Assertion gelockert. Diese Versuche werden nicht als vollständige Pässe gezählt. Zusätzlich verbessert der finale Kartenfixture die native Sichtbarkeit aller vier Statusarten.

Finale VIS-02-Evidenz: native-verified/images/ enthält **36 PNGs**, davon 9 MainMenu- und 9 echte ArrayListHUD-GLOBAL-Bilder (Flow/Ping-Pong/Pulse je .07/.21/.41), 5 Mode-Referenzbilder, 3 eingefrorene HUD-Bilder, 4 Symbol- und 4 Kartenbilder sowie 2 Fallbackbilder. CPU/GPU: 1.280 echte Referenzpixel für 0..4, ±X/±Y, vier Phasen, Edge-/Halbpixelsampling; maximale Abweichung **1/255**. Titelglyphen zusätzlich gegen CPU-gradientColor geprüft (mittlerer RGB-Fehler <2, >150 opake Glyphenpixel pro Probe). Festphasenvergleich prüft sichtbare methodenabhängige GUI-/HUD-Pixel.

Native beweist Ambient/Reduced/Animations-Freeze an Titel, HUD und Backdrop, unveränderte Phase nach Shaderrelease/Recreate und Surfacebuffer-Resize, wiederhergestellten Target/Program/Scissor/Depth-State, und unterschiedliche echte Notification-Geometrien als Helligkeitsmasken auch bei gesättigtem Custom Theme, Priorität OFF und Border/Glow OFF. Alle vier echten Notificationkarten sind im finalen Fixture sichtbar. Fallback erzwingt jeweils shadersSupported=false bzw. fboEnable=false: lesbare Font-/Konturpixel, keine neue FBOallokation, GL clean. Das beweist den begrenzten vorhandenen Surface/Text-Fallback, keinen totalen GL-Ausfall des MainMenu-Backdrops.

FBO-Lebensdauer der eigenen Probe: **10 erstellt / 10 gelöscht**; keine neue Allokation in jedem 30-Draw-Steady-Test. Gemessene isolierte HUD-Mittelzeiten mit eingefrorener Phase: Flow 1.537313 ms, Ping-Pong .789990 ms, Pulse 1.065810 ms pro Draw inklusive Clear/Compositor und abschließendem glFinish. Kleine sequenzielle Probe, keine robuste Rangfolge oder Spiel-FPS-Aussage. Bestehende Native-Mikrozeiten ebenfalls keine 144-FPS-Abnahme.

Sichtkontrolle: MainMenu-Pulse, ArrayList-Flow, NoShader-Fallback und finale vier Notificationkarten geöffnet; Text und Symbolformen erkennbar, Error bleibt rot/mit Kreuz. PNGs sind TYPE_INT_RGB / Standard-sRGB ohne Monitorkalibrierung. screenshot-manifest.json enthält Maße/Hashes sämtlicher 173 PNGs einschließlich ausdrücklich fehlgeschlagener Versuche und vorhandener Regressionen; nur native-verified/ ist der finale VIS-02-Satz. artifact-manifest.json enthält externe PNG-/XML-/Log-/Receipt-/Patch-SHA256. Kein Binärbeleg liegt im Commit.

## Pflichtmatrix: konkrete Belege und Grenzen

Alle PASS-Angaben gelten am Implementierungs-SHA 635522d63a4b7edfb965e83eb8c52fc28d79445b, mit obiger Native-Fixture-Differenz zum Clean-Lauf. Source/CPU ist keine Liveabnahme.

| Fall | Status | Exakter Nachweis |
|---|---|---|
| V02-01 | PASS | ClientSettingsVis02Test alle vier Testmethoden; 46 alte Values vollständig verglichen, 49 gesamt, fehlende Keys Flow/true/true |
| V02-02 | PASS | MainMenuMotionTest, MainMenuInteractionTest, ClientSettingsMotionTest, ClientSettingsConfigTest; native-menu-regression |
| V02-03 | PASS | Vis02GradientTest.flowExactlyMatchesOldCosineIncludingWrapAndLargeCoordinates, methodsOnlyReplaceDynamicModesAndFlowRetainsModeTwo; native shaderParity/consumerShots |
| V02-04 | PASS | pingPongReversesSmoothlyAtBothTurningPoints; shaderParity, drei reale Phasen |
| V02-05 | PASS | pulseUsesCoherentWeightsAndRetainsOpaqueStopAlpha; shaderParity, drei reale Phasen |
| V02-06 | PASS | axesEdgesHalfPixelsAndAllModesAreFiniteAndBounded; shaderParity ±1/±2 an Pixelzentren und Edges |
| V02-07 | PASS Pbuffer | Vis02RenderCheck.shaderParity: 1.280 echte Pixel, maximale Abweichung 1/255 |
| V02-08 | PASS | synchronizedChannelsShareMethodPhaseDirectionAndFillPolicy; consumerShots an echten gemeinsamen UI-/HUD-Consumern |
| V02-09 | PASS | independentChannelsRetainOldSpeedDirectionAndMotionChoices; UiEffectsTest.channelsSharePhaseUntilExplicitlySeparated |
| V02-10 | PASS begrenzt | localLegacyRateAndCustomPaletteUsePausedCanonicalTime; ClientSettingsConfigTest Legacyimport; unveränderte native-hud-regression mit lokalen Layouts |
| V02-11 | PASS | ambientOffFreezesTimeAndPhaseButLeavesInteractiveResponse; MainMenuVis02Test; freezeAndReload |
| V02-12 | PASS | MainMenuMotionTest.reducedMotionIsFiniteLinearAndReversibleWithoutOvershoot; MainMenuVis02Test; freezeAndReload |
| V02-13 | PASS | motionPriorityMatrixKeepsReducedAndAnimationOffContracts; unveränderte Capture-/Escape-/Doppelklicktests; Nativefreeze |
| V02-14 | PASS | Vis02ClockTest alle drei Fälle: 100-ms-Pausencap, rückläufiges Reading, doppelte Readings/Partitionierung |
| V02-15 | PASS begrenzt | reinitializationInvalidationThemeChangesAndScenesNeverResetClock; freezeAndReload; Theme-/Configregression; echter Displayreload bleibt offen |
| V02-16 | PASS begrenzt | Vis02NotificationTest.realIconColorConsumerMapsExistingRolesAndErrorSurvivesOff; semanticShots: vier echte Symbole/Karten ON, gesättigtes Custom Theme, Glow/Border OFF |
| V02-17 | PASS begrenzt | UiEffects.semanticColor-Tests, derselbe echte Farbconsumer OFF, native Helligkeitsmasken/Labels; allgemeiner Kontrastreview OPEN |
| V02-18 | PASS | NotificationTimelineTest; ambientOffDoesNotFreezeCoalescingExpiryExitOrPromotion |
| V02-19 | PASS Pbuffer | consumerShots/verifyTitle: MainMenu/TextEffects, drei Phasen je Methode, feste Geometrie, 9 PNGs; alte Fokusboundsprüfungen grün |
| V02-20 | PASS Pbuffer | consumerShots: echte ArrayListHUD GLOBAL Fill/Border/Label, 9 PNGs; lokale Nativebestandsregression unverändert grün |
| V02-21 | PASS begrenzter Pbuffer-Fallback | fallbackShots/GL-State/10:10-Lifetime, explizit NoShader/NoFBO, vorhandene HUD/GUI-FBO-Checks; kein totaler GL-Ausfall |
| V02-22 | NOT_RUN / OPEN | Realspiel, OptiFine HD U L5 + Sildur Enhanced Default v1.20 Fast, echtes Display/Fullscreen und 144-FPS-Szenario nicht ausgeführt |

## Git-Schutz und Lieferung

worktrees-before.json, worktrees-after.json und worktrees-comparison.json: beim Vergleich nach Implementierung 31 von 32 fremden Worktrees unver�ndert. Die sp�tere Aufnahme worktrees-after-final-commit.json / worktrees-final-comparison.json zeigt 30 von 32 vollst�ndig unver�ndert, einschlie�lich sauberer VIS-01-Basis. F:/neutron-scf-1a �nderte unabh�ngig Status und getrackten Diffhash. Im Original F:/Puslar Backup - Copy wechselten parallel Branch und HEAD von prep/neutron-opencode-wave00 / 3ea55cbc34e14ad479454c6ce0d31e48e6f581fb zu codex/revived-final-integration / eeddb7da58978f04fad95ad8042175b7ad848d6a; Porcelainstatus und getrackter Diffhash blieben identisch (ea20915ab1fd52e816248910c54fb0c4731989e928e5d7d64eea59b4f0dcc88d). Diese fremden �nderungen wurden lediglich erfasst; VIS f�hrte dort keinen Branchwechsel und keine Dateioperation aus und �bernahm/revidierte nichts. Git-Diffs zum Hashen nur im Speicher gelesen, keine Saves/Runtime-/Accountdateien kopiert. Initialer Checkout im eigenen Worktree �ber --no-checkout und read-tree zur normalen Materialisierung; kein Fremdindex/Lock bearbeitet und keine Reparatur n�tig.

Vor/nach Commits diff --check und exakte namentliche Allowlist. Endzustand eigener Branch sauber. Reviewpatch wird bytegetreu mit git diff --binary BASE..HEAD außerhalb des Repos exportiert: F:/neutron-vis-02-results/VIS-02-review.patch. review-receipt.json und VIS-02-review.patch.sha256 binden ihn an vollständige Basis-/End-SHAs, Dateiliste, Patchhash und git apply --check --reverse im eigenen finalen Zustand. Der Receipt ist die überprüfbare finale Hashquelle. Keine Veröffentlichung.

## Verbleibende Gates / STOPP

Produkt-/Motion-/kontrastkritische Themefreigabe OPEN. G-VIS-GRADIENT für den begrenzten VIS-02-Scope hat CPU-/JUnit-/Pbufferevidenz, keine globale Produkt-/Releasefreigabe. G-QA-SHADER und G-QA-144FPS NOT_RUN; Fullscreen/Display/Shaderpack/Live-Low-Balanced-High-Matrix offen. G-VIS-HUD-Editor und VIS-03…06 nicht begonnen. Keine vollständige Client-/Welt-/Cross-Track-Regression nach einem Merge behauptet. Kein Ownershipblocker für den implementierten Scope. STOPP nach VIS-02.
