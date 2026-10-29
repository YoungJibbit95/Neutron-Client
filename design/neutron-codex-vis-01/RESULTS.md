# VIS-01 — Ergebnisse und Übergabe

Stand: 28.10.2026. **VIS-01 implementiert; STOPP nach dieser Welle.** Keine Subagents, kein Push, PR, Fetch, Merge, Tag, Stash, Reset oder Git Clean. `mvn -o clean test` wurde ausdrücklich im eigenen Worktree ausgeführt.

## Git, Quellen und Eigentümer

- Basis: `3554f7a9545fbc03b91fbdf9af453ba851bf0f2c` aus dem lokal geprüften, sauberen `F:\neutron-core-i01e` / `codex/neutron-core-i01e`.
- Eigener Worktree: `F:\neutron-vis-01`; eigener Branch: `codex/neutron-vis-01`. Pfad und Branch waren vorher frei.
- Implementierungscommit: `e3492be7a0a415491e87fda23b2cb7f282fa2a4c` (`feat(vis): polish main menu and add motion intensity presets`).
- Die drei Berichte folgen in einem separaten Dokumentationscommit. Dessen exakter SHA und der finale Review-Patch-SHA256 stehen nach dem Commit in `F:\neutron-vis-01-results\review-receipt.json`; der Bericht kann den Hash eines sich selbst enthaltenden Patches nicht selbst enthalten.
- Quellen extern unter `F:\neutron-vis-01-sources`. Alle 15 Manifest-Dateihashes stimmen. ZIP-SHA256: `d6f5f5430632d7edafca8c9b50d43739be4350298fde9a30f8a61a0934740351`; der separat gelieferte Masterprompt stimmt mit dem ZIP überein (`9cf23cb14478c2f59accddebc2fd476687dc1743535ee13e8eb5a8cc64677b5c`).

Die Dateien im Paket wurden gemäß direktem Nutzerauftrag als VIS-01-Spezifikation gelesen; das langfristige Register erteilt keine Freigabe für VIS-02 oder weitere Tracks. Historische Audit-Aussagen wurden nur gezielt am lokalen Java-Code geprüft. Insbesondere gilt der alte Reduced-Motion-Snap-Vorschlag nicht gegenüber dem linearen v1.0-Vertrag.

Die Worktree-Erstellung wurde technisch unterbrochen, nachdem bereits ein Teil des Basisbestands geschrieben war; es fehlte der Index. Nach Prüfung, dass kein eigener Checkout-Prozess mehr lief, wurde ausschließlich der eigene Initialisierungslock entfernt, der Index mit `git read-tree HEAD` initialisiert und der fehlende Basisbestand über `git checkout-index --stdin -z` vervollständigt (37.581 fehlende Dateien). Keine fremde Datei wurde dafür zurückgesetzt oder kopiert. Die abschließende Dateiliste bestätigt ausschließlich die erlaubten Änderungen; das externe Log ist `worktree-checkout-repair.log`.

## Exakte Änderungsliste

| Datei | Änderung |
|---|---|
| `src/main/java/me/youngjibbit/gui/MainMenu.java` | sichtbare Innenflächen, Lift/Scale/Glow, Fokusklammern, Pressed-Linie, Text-Fit, Capture-/Lifecycle-Korrektur, gemeinsamer Motion-Consumer |
| `src/main/java/me/youngjibbit/gui/MenuSpring.java` | kompatible Legacy-API, Preset-Kennwerte, linearer Reduced-Verlauf, sichere dt, exakter Nullzeitschritt und Reset |
| `src/main/java/me/youngjibbit/module/impl/client/ClientSettings.java` | genau ein `UI Motion Intensity`-ComboValue und Gruppenbindung, Balanced Default |
| `src/main/java/me/youngjibbit/gui/MenuMotion.java` | neuer kleiner visueller Preset-Helfer |
| `src/test/java/me/youngjibbit/gui/MainMenuInteractionTest.java` | 12 neue JUnit-Fälle über reale GUI-Handler und Consumer |
| `src/test/java/me/youngjibbit/gui/MainMenuMotionTest.java` | 8 neue JUnit-Fälle für Motion, FPS, Reverse, Pausen, Reduced und Legacy |
| `src/test/java/me/youngjibbit/module/impl/client/ClientSettingsMotionTest.java` | 3 neue JUnit-Fälle für Default/Gruppe/IDs/Config |
| `src/test/java/me/youngjibbit/gui/MainMenuVisualCheck.java` | neuer isolierter nativer Einstieg; ruft unverändertes MainMenuCheck auf und ergänzt Preset-/Custom-Theme-/Scale-Bilder |
| `design/neutron-codex-vis-01/VIS_01_DESIGN_DECISIONS.md` | Designentscheidungen und offene Produktgates |
| `design/neutron-codex-vis-01/VIS_01_INTERACTION_CONTRACT.md` | Eingabe-/Bounds-/Motion-/Lifecycle-Vertrag und Testzuordnung |
| `design/neutron-codex-vis-01/RESULTS.md` | dieser Abschlussbericht |

**Allowlist: PASS. Bestehende Tests geändert: 0.** Keine Adapterausnahme erforderlich; keine Assertion abgeschwächt. Keine Änderungen an `UiEffects`, Theme-/Config-Ownern, Shadern, Assets, Panel/iOS/HUD, `pom.xml`, CORE-/Gameplay-/CMB-Dateien oder `test_run`.

## Tatsächliche Testläufe

Oracle JDK **1.8.0_391**, `C:\Program Files\Java\jdk-1.8`, Windows amd64; Maven **3.8.5** über den absoluten Paketpfad. Jeder Build-/Test-/Classpath-Aufruf lief mit `-o`, explizitem `JAVA_HOME` und `MAVEN_OPTS=-Xmx512m`. `toolchain.txt` enthält `mvn -version`; `bytecode.json` bestätigt Major **52** für alle vier neuen/geänderten Produktionsklassen.

| Lauf / externes Log | Tests | Failures | Errors | Skips | Ergebnis |
|---|---:|---:|---:|---:|---|
| Basis: `baseline-focused.log` (`UiEffectsTest,ClientSettingsConfigTest`) | 12 | 0 | 0 | 0 | GREEN vor Produktionsänderung |
| `red-interaction.log` (`MainMenuInteractionTest`) | 9 | 2 | 0 | 0 | echte RED: Escape-Capture und Mouse/Keyboard-Doppelauslösung |
| `red-motion-compile.log` | — | — | — | — | Compile-RED: fehlendes `motionIntensity`, fehlender `MenuMotion`/neue Step-API; **0 Tests ausgeführt** |
| `first-implementation-focused.log` | 35 | 1 | 0 | 0 | Null-dt-Rundungsabweichung der Velocity um ca. 10^-15; durch echten No-op im Code behoben |
| `green-focused.log` | 35 | 0 | 0 | 0 | GREEN nach Korrektur |
| `full-clean-test.log` (`mvn -o clean test`) | **544** | **0** | **0** | **3** | **BUILD SUCCESS**, 93 Surefire-Klassen, 541 ohne Skip |
| `final-focused.log` | 35 | 0 | 0 | 0 | finale Test-/Native-Fixture-Kompilierung und fokussierte Nachprüfung |

Die finale Vollsuite endete um 13:48:41 Uhr Berlin mit 3:42 Minuten Laufzeit. Danach änderte sich ausschließlich der neue standalone Native-Fixture (effektiver GUI-Scale 4); dessen aktuelle Fassung wurde im finalen fokussierten Lauf kompiliert und anschließend nativ ausgeführt. Alle Produktionsdateien und sämtliche JUnit-Fälle entsprechen dem Vollsuite-Stand. Die 23 neuen JUnit-Fälle sind in den 544 enthalten und werden nicht zusätzlich addiert. Kindprozess-JUnit-Ausgaben bestehender Tests werden nicht als weitere Surefire-Tests gezählt.

Die drei unveränderten Opt-in-Skips sind jeweils ein Fall in `WorldOverlayContractTest`, `GuiChatInputTest` und `GuiOverlayDebugGlStringsTest`. Sie gelten **nicht** als bestanden. Die XML-Kopien liegen in `baseline-surefire`, `red-interaction-surefire`, `first-implementation-surefire`, `green-focused-surefire`, `full-surefire` und `final-focused-surefire`; `full-suite-counts.json` enthält die aus XML gelesenen Summanden.

## Native und sichtbare Evidenz

`MainMenuVisualCheck` verwendet den bestehenden `PbufferTestContext`, echte Shader/FBO/Readbacks und unverändertes `MainMenuCheck`; kein Start des Spiels, keine Accounts, Server, Saves oder persönliche Configs. Arbeitsverzeichnisse, Logs und PNGs liegen ausschließlich unter `F:\neutron-vis-01-results`.

- Vorher: `native-before.log`, Exit 0; `before\menu-preview\menu-before.png`, 960×640, GUI-Scale 2, SynthWave.
- Erste vollständige neue Native-Prüfung: `native-after-initial.log`, Exit 0. Die Bildkontrolle zeigte, dass das neue Custom-Theme-Fixture wegen fehlender Katalogregistrierung auf das vorhandene Fallback-Theme zurückfiel. Der **neue Fixture** wurde auf echte Katalog-Preview mit Identitätsassertion korrigiert; kein Produktions-Themecode wurde geändert.
- Final: `native-final.log`, Exit 0. Unveränderte Bestandschecks bestanden für Spring, gerundete Konturen, Click-Routing, Keyboard, Reopen, Utility-Toggles, eingefrorene Szene und sichtbare Titelgradienten. Ergänzungen bestanden für tatsächliche unterschiedliche Button-Pixel aller drei Presets, Fokus/Hover/Pressed/Cancel/Reduced und Custom-Theme sowie Effekt-OFF.
- Final insgesamt **40 Nachher-PNGs plus 1 Vorher-PNG**. Native Bestandsgrößen: 960×640/Scale 2, 640×480/Scale 2, 1101×733/Scale 3. Zusatzmatrix: 1101×733 bei effektiven Scales 1, 2, 3 und 1280×960 bei effektivem Scale 4 (= 320×240 GUI-Einheiten). Kleine/kompakte Geometrie wird zusätzlich in JUnit geprüft.
- Sichtkontrolle unter anderem von `menu-0.png`, `Balanced-hover.png`, `Balanced-pressed.png`, `cyan-scale-4.png` und `focus-effects-off.png`: Beschriftungen innerhalb der Buttons, gedrückte Linie klar unterscheidbar, Fokus auch ohne Border/Glow erkennbar. Weitere Preset-/Cancel-/Reduced-Bilder sind inspectierbare Evidenz, keine pauschale Produktabnahme.
- GPU: NVIDIA GeForce RTX 3070 Ti, OpenGL 4.6.0, Treiberkennung NVIDIA 616.92. RGB-PNGs über `BufferedImage.TYPE_INT_RGB` (Standard-sRGB; keine gemessene Monitor-/ICC-Kalibrierung). Bildgrößen und SHA256 stehen in `screenshot-manifest.json`.

Die im Bestandscheck gedruckten Shader-Quality-0/2-Mittelzeiten sind ein isolierter Offscreen-Mikrotest. Sie sind **kein** Normalspiel-/144-FPS-Nachweis und führen kein neues Low/Balanced/High-Setting ein.

## Schutzvergleich und externe Patches

`worktrees-before.json`, `worktrees-after.json` und `worktrees-comparison.json` enthalten die Read-only-Aufnahmen von HEAD, Status und SHA256 des getrackten Working-Tree-Diffs. **29 von 30 fremden Worktrees sind in allen drei Merkmalen unverändert**, einschließlich Originalcheckout und sauberer I01e-Basis. Der parallel aktive CMB-Worktree wechselte unabhängig von Basis `3554f7a...` zum eigenen Commit `88ee497d8fe2dca16e5eaae9e48548c7d46e7050`; seine anfänglichen drei Änderungen wurden dort eigenständig committed. VIS hat weder CMB übernommen noch dort geschrieben.

Der extern exportierte Implementierungs-Review-Patch liegt unter `F:\neutron-vis-01-results\VIS-01-implementation.patch`; SHA256: **`088ba43a495d9a6a137792aab7d4ce04c3abe5d413b8818e3da7cb471a844399`**. `implementation-receipt.json` bindet ihn an Basis und Implementierungscommit.

Zusätzlich wird nach dem Dokumentationscommit `F:\neutron-vis-01-results\VIS-01-review.patch` mit **allen eigenen Commits einschließlich dieser drei Berichte** exportiert. `review-receipt.json` und `VIS-01-review.patch.sha256` dokumentieren den endgültigen Patchhash, beide Commit-SHAs, die exakte finale Dateiliste und den sauberen Worktree. `artifact-manifest.json` enthält die externen Evidenzhashes; Screenshots/Logs/Review-Patch wurden nicht ins Repository aufgenommen.

## Offene Gates / STOPP

| Gate | Status |
|---|---|
| G-VIS-MAINMENU | Implementierung, JUnit und Pbuffer-Evidenz vorhanden; Produkt-/Motion-/Kontrastreview noch offen |
| G-VIS-GRADIENT | OFFEN, VIS-02; keine Freigabe/Implementierung |
| G-VIS-HUD | OFFEN, spätere Welle; keine Freigabe/Implementierung |
| G-QA-SHADER | **NOT_RUN** für OptiFine HD U L5 + Sildur's Enhanced Default v1.20 Fast und vollständige NoShader-/Fallback-/Reload-/Fullscreen-Matrix |
| G-QA-144FPS | **NOT_RUN**, keine Referenzhardware-/Normalspiel-Samples |

Native Display-Erstellung, echte OS-Resize-/Fullscreen-/VSync-Ereignisse, vollständiger Clientstart, Spielwelt, Shaderpack und komplexe Kontrastfälle sind nicht durch den isolierten Pbuffer bestanden. Vorhandene RenderState-/Surface-Fallback-Grenzen bleiben erhalten; ein vollständiger Shader-Ausfall wurde nicht simuliert. Kein notwendiger Eingriff in verbotene Dateien und kein Implementierungsblocker verbleibt für diesen VIS-01-Auftrag. **Keine weitere VIS-Welle begonnen. STOPP.**
