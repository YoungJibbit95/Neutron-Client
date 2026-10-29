# VIS-QA02 Ergebnisse

28.10.2026. Auftrag: gemeinsame UI-Spring-Präsentation und zusätzliche Notification-Themes. Kein VIS-03, keine universelle UI-/Gameplay-/Releasefreigabe. Der Abschluss ist auf die belegten MIGRATED-Oberflächen begrenzt; ausdrücklich zurückgestellte Familien stehen unten. STOPP nach VIS-QA02.

## Basis, Quellen und Umfang

Start-Gate bestanden: sauberer `F:/neutron-vis-qa01-gradients`, Branch `codex/neutron-vis-qa01-gradients`, HEAD exakt `a4142772ab3ff64747f22215f922c5891153fe47`; `525c96ff8d0650767cfdfc5fb940daca7b6f6369` und `534b8c4db8d3a6633eea6e663a74be17d18aadda` als Vorfahren bestätigt. Neuer Branch und Zielpfad waren frei. Eigener Worktree: `F:/neutron-vis-qa02-motion-toast`, eigener Branch: `codex/neutron-vis-qa02-motion-toast`. Anlage mit --no-checkout und eigenen Sparse-Ausschlüssen, danach read-tree -mu HEAD. Kein fremder Checkout wurde umgeschaltet.

ZIP extern nach `F:/neutron-vis-qa02-sources` entpackt, mit Pfadprüfung vor der Extraktion. Zuerst 00_START_HIER, anschließend Manifest gelesen. Alle 23 Einträge stimmen in SHA256 und Bytezahl; keine zusätzliche unmanifestierte Nutzdatei. Separat gelieferter Master und Bundle-Master sind bytegleich, SHA256 `08fe9cafe20edf819c06cc175c295f7113a5d6cd430c10eebe165a229870bb44`. Vollständiger Receipt: `F:/neutron-vis-qa02-evidence/source-integrity.json`. Historische Audits/Review/Patch dienten nur als Evidenz; der QA01-Patch wurde nicht angewendet.

Projektname Neutron-Client. Tatsächlich lokal gelesenes Remote: `https://github.com/YoungJibbit95/Pulsar-Client.git`. Das ist eine lokale Remote-Konfiguration, keine Prüfung des aktuellen GitHub-Namens oder der Existenz. Keine Umbenennung, kein Fetch/Push/Merge/PR/Rebase/Reset/Stash/Clean/Tag, keine Subagents.

Das [Control-Inventar](VIS_QA02_CONTROL_INVENTORY.md) war die erste eigene Repositorydatei, vor Tests und Produktcode. `before-production-receipt.json` hält seinen ursprünglichen Hash, die neuen Testpfade, RED-Logs und den leeren Produktionsdiff fest. Spätere Statusklassifikation ist davon getrennt.

13 Produktionsdateien: UiSpring neu; minimale Präsentationsadapter in SettingsMenu, ClickGui, Component und vier Panel-Components, GuiStyle, ThemeEditorScreen und iOS-Kategorien; zusätzliche Value-Registrierung in ClientSettings und Dekoration in NotificationManager. Genau eine neue reine Präsentationsklasse. Fünf neue Testdateien sowie zwei gezielte bestehende Fixtureanpassungen. Genau vier neue Repositoryberichte.

MainMenu/MenuSpring/MenuMotion, UiEffects, TextEffects, Theme/ThemeCatalog/ThemeStore/ThemeRuntime, Shader/Assets/Fonts, POM/Dependencies, NotificationType, Config-/Modulecore und sämtliche net/minecraft-Dateien bleiben im Diff unverändert. Kein Gameplay-/Netzwerk-/Auth-/CORE-/SCF-/REL-Produktionsedit; keine test_run-/Account-/Save-/Launcherdatei geändert.

## Ergebnis und präzise UI-Abdeckung

| Oberfläche | Abschlussstatus |
|---|---|
| MainMenu acht Aktionen | ALREADY_COVERED; vollständig unverändert, Regression grün |
| Settings sechs Aktionen | MIGRATED; alte Mouse-down-Semantik, stabile Bounds, keine Doppelaktion |
| Panel Kategorien/Search/Themes, Modulkopf, Checkbox/Combo-Feld/Slider-Feld | MIGRATED; ursprüngliche Clip-/Value-/Inputowner erhalten |
| ThemeEditor 20 vorhandene Control-IDs | MIGRATED; Focus/Disabled, ursprüngliche Preview-/Save-/Cancel-/Delete-/Reset-Owner |
| iOS Kategorien | MIGRATED für Pointer-Präsentation einschließlich tatsächlicher Fit-/Inverse-Hit-Geometrie |
| Settings Valuezeilen und einzelne Panel-Combo-Optionszeilen | DEFERRED für eigene Feder-/Pressed-Adapter; Bestand und vorhandene Eingaberechte erhalten |
| iOS Module/Components | DEFERRED wegen bestehender Reveal-/Option-Hit-Abweichung; keine heimliche Inputreparatur |
| AccountUI/AccountForm | DEFERRED; statischer Paint-Helper ohne Lifecycle, gemischte Auth-/IO-Screenowner ausgeschlossen; ausschließlich synthetische bestehende Tests |
| Vanilla/Container/Slots und dekorative Preview-Samples | NOT_APPLICABLE zur QA02-Buttonmigration |

Details, Screenzeit und unveränderte Presets: [Motion-Vertrag](VIS_QA02_MOTION_CONTRACT.md). `UI Animations OFF` snappt, Reduced bei ON bleibt 120-ms-linear, Ambient OFF stoppt interaktive Federn nicht. Fokusklammern und Press-Linie funktionieren unabhängig von Border/Glow. Nur innere Dekorflächen bewegen sich.

Notification Theme hat Follow Global/Obsidian/Aurora/Neon Edge/Minimal; die 49 bisherigen Registrierungen und Notification Design bleiben erhalten. 20 Appearance-Paare roundtrippen im echten Codec; Legacy-Cold-Load und warmer Import eines ClientSettings-Profils ohne neuen Schlüssel nutzen Follow Global. Ausschließlich der neue Selektor erhält seinen Default im bestehenden UI-Modul-Config-Batch, vorhandene Schlüssel gewinnen danach regulär. Keine Änderung des allgemeinen Config-/Module-/Valuecore. [Notification-Vertrag und getrennte Matrizen](VIS_QA02_NOTIFICATION_THEMES.md).

## Test-first und JDK8 offline

Oracle JDK 1.8.0_391, Maven 3.8.5, MAVEN_OPTS=-Xmx512m. Alle Mavenaufrufe mit `-o`; kein POM-/Dependencywechsel. Standalone native Checks sind keine JUnit-Testzahlen.

| Lauf, relativ zu F:/neutron-vis-qa02-evidence | Tests | Failures | Errors | Skips | Einordnung |
|---|---:|---:|---:|---:|---|
| baseline-focused | 87 | 0 | 0 | 0 | unveränderter tatsächlicher Bestand |
| red-selector-behavior | 5 | 3 | 0 | 0 | Notification Theme fehlt, echte Behavior-REDs |
| first-green-and-legacy-red | 29 | 2 | 5 | 0 | Altwerte-Längen-RED; fehlender warmer Legacy-Default; GL-Abfrage im headless Close |
| green-focused-second | 31 | 0 | 0 | 0 | neue Motion-/Screen-/Config-/Timelinefälle und Altwertschutz |
| green-focused-final | 114 | 0 | 0 | 0 | neue Fälle plus 87 Bestandsanker |
| green-focused-fixture-final | 114 | 0 | 0 | 0 | Quellstand mit gezielter nativer Fixtureanpassung |
| green-focused-verified | 114 | 0 | 0 | 0 | korrigierte, identitätsgeprüfte Custom-Theme-Fixture |
| green-focused-release | 114 | 0 | 0 | 0 | einschließlich letzter Obsidian-Opacity-Korrektur |
| red-legacy-warm | 1 | 1 | 0 | 0 | warmer Legacy-Profilwechsel hält fälschlich Neon Edge; echtes Behavior-RED |
| green-focused-legacy-final | 115 | 0 | 0 | 0 | letzter Quellstand, Cold/Warm-Legacy-Default und Callback-Anzahl grün |
| full-clean-test-initial / full-clean-test-before-opacity / full-clean-test-before-legacy | je 597 | 0 | 0 | je 3 | erhaltene frühere Vollsuitebelege, keine Addition der Testzahlen |
| full-clean-test: exakt mvn -o clean test | **598** | **0** | **0** | **3** | letzter Quellstand, 103 frische XMLs unabhängig gezählt |

Keine fehlgeschlagene Anlaufphase wird als bestanden gezählt: `red-selector` hatte zwei korrigierte Compilerfehler im neuen Native-Fixture (Locale-Import und generischer Cast). `red-motion-api` ist ausdrücklich API-Compile-RED für die noch fehlende UiSpring-/Consumernaht, kein Behavior-RED. `green-focused-first` hatte eine korrigierte Test-Compile-Annahme über das finale TextEffects. Die headless Tests entsorgen reine Visualzustände; reale GL-Closures werden nativ geprüft.

Finale Rechnung: 570 gemeldete QA01-Referenzfälle plus 28 neue JUnitmethoden ergeben 598, davon 595 ohne Skip. Aus 103 frischen TEST-XMLs unabhängig summiert; keine nativen Unterprogramme oder intern gedruckten Subtests zusätzlich gezählt. Alle 19 Classfiles der 13 geänderten Produktionsquellen einschließlich Inner Classes haben major 52. Java-Version in den XMLs: 1.8.0_391. Die drei unveränderten Opt-in-Skips: WorldOverlayContractTest.nativeDispatchWhenRequested, GuiChatInputTest.nativeInputAndDrawingWhenRequested und GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings. Sie gelten nicht als bestanden. Negative Fixture-Logs innerhalb grüner Tests sind keine Surefire-Errors. Nach diesem Vollsuite-Quellstand keine weitere Code-/Teständerung.

Vor Produktcode: `native-baseline-red`, echter Pbuffer, Settings und Editor jeweils 0 Calm/Expressive-Differenzpixel. Vier alte Notificationmaterialbilder wurden dabei unverändert exportiert. Nachher: 5.465 bzw. 2.359 Differenzpixel in denselben Controlregionen. Keine Shaderuniform allein wird als sichtbarer Nachweis verwendet.

Die alte ThemeEditorCheck-Fixture scheiterte zunächst mit `Static preview still changes`: neu hinzugekommener Fokus-Spring war noch aktiv. Erst nach diesem dokumentierten Render-RED wurde einmalig die reine Präsentation vor dem statischen Snapshot deterministisch auslaufen gelassen. Keine alte Assertion oder Toleranz gelöscht/gelockert; kein Abschalten von Animation/Gradient als Testabkürzung. ClientSettingsVis02Test erhielt nach seinem Längen-RED lediglich den neuen Ausschluss und +1, das vollständige Altwertearray bleibt.

Die Bildsichtung entdeckte außerdem eine fehlerhafte Custom-Theme-Fixture: setDefaultTheme mit einer nicht registrierten Custom-ID wählte einen Fallback. Die endgültige Statusmatrix benutzt die vorhandene ThemeCatalog.preview-API und prüft explizit Identität und Palette des tatsächlich aktiven Themes. Die vorläufigen `native-qa02-final`-Bilder gelten deshalb nicht als Low-Contrast-Custom-Beweis; die korrigierte Vorserie `native-qa02-final-verified` bleibt separat erhalten. Schließlich belegte `native-border-opacity-red` 500 Differenzpixel trotz Global Border Opacity=0. Genau eine neue Obsidian-Alpha-Zeile wurde korrigiert; der exakte 0-Pixel-Nachtest bleibt erhalten. Kein alter Material-/Statuspfad wurde dafür geändert.

## Native Belege

| Lauf | Exit | Umfang |
|---|---:|---|
| native-qa02-first | 0 | erste grüne eigene Serie, 116 PNGs; nicht zur finalen Bildsumme addiert |
| native-qa02-delivery | 0 | **125 Original-PNGs**, 20 Theme-/Materialkarten, 40 semantische Karten, zusätzliche Screen-/Ecken-/Fit-/Fallback-/Recreatebilder |
| native-qa01-regression | 0 | unveränderter sechs-Consumer-Check, 313 eigene QA01-Bilder; 216 CPU/GLSL-Pixel max. 0/255, FBO 30:30 |
| native-vis02-regression-bounded | 0 | unveränderter VIS-02-Check, 1.280 Referenzpixel max. 1/255, FBO 10:10, tatsächliche Titel/HUD/Status-/Fallbackpfade |
| native-menu-regression | 0 | unveränderter MainMenuVisualCheck, alle drei Presets und GUI-Scales 1–4 |
| native-panel-layout-regression | 0 | unveränderter ClickGuiLayoutCheck, Maus/Key/Suche/Drag/Resize/Clips und kleine/ungerade Größen |
| native-panel-render-regression | 0 | unveränderter ClickGuiRenderCheck, Values/exakte Zahlen/Bind/Scroll/Clip und Surface-Reuse |
| native-editor-settings-final | 0 | echte bisherige Editor-/Settingschecks; Save/Cancel/Delete/Reset, statische Pixelgleichheit, 8 warme Editorframes ohne neue FBO/Programme; drei Resizes 23:23 |

GPU der echten Kontexte: NVIDIA GeForce RTX 3070 Ti, OpenGL 4.6.0 NVIDIA 616.92, GLSL-1.20-Produktionsshader. Synthetische Sessions/Module/Daten; keine Login-/PKCE-/Token-/Browser-/Realaccountaktion. Pbuffer, kein gestartetes Spiel oder natives Fenster.

Die ersten zwei VIS-02-Nativanläufe (`native-vis02-regression`, `...-retry`) scheiterten an nativer DirectByteBuffer-Speicherallokation. Sie bleiben als Umgebungsfehler dokumentiert. Der begrenzte sequentielle Lauf mit -Xmx512m, MaxDirectMemorySize=128m und SerialGC bestand ohne Teständerung. Keine FPS-Folgerung aus diesen isolierten, teils zunächst parallel gestarteten Checks.

Finaler eigener Pixelvergleich: alle 40 Theme-Paare über vier Materialien sind sichtbar verschieden; Follow Global ist in jedem Material bei fixierter Timeline zur alten drawCards-Referenz exakt gleich (0 Pixel). Statusmatrix separat, 40 Karten bei Border/Glow OFF und kontrastarmem synthetischem Theme: vier unterschiedliche Symbolmasken, innerhalb jeder Rolle über alle fünf Themes identisch. Kritisches ERROR bleibt rot. Eckenmatrix: vier Ecken × zwei Größen. Live öffentliche render-Proben: A→B→A in drei Methoden, jeweils 0 Rückkehrpixel; .1/.8 Speed ergibt nach .6 s .06/.48 Phase. Accent-Kontur-Pixelprüfung grün, Neon Border OFF pixelgleich Follow Global.

Settings/Editor/Panel/iOS-Presetregionen unterscheiden sich sichtbar; alle drei Panel-Valuefelder sind einzeln belegt. Echte native Click-/Disabled-/Close-/Fitproben sowie Scissor/Blendzustand und Recreate-Phase grün. Die isolierte QA02-Ressourcenprobe hat 2:2 FBO einschließlich Recreate und keinen Zuwachs in zehn warmen Folgeframes. Dieser Zähler wird nicht als Zähler aller Matrixbilder ausgegeben.

`screenshot-manifest.json`: 125 PNGs mit relativen Pfaden, SHA256, Bytezahl, Maßen, effektivem GUI-Scale, Theme/Preset/Design und ausdrücklich keiner erfundenen Pbuffer-DPI. `theme-matrix.json` enthält 46 Vergleichszeilen; `native-qa02-delivery/status-matrix.json` getrennt 40 Statuszeilen. `regression-screenshot-manifest.json` hasht die 436 separaten Bestandscheck-PNGs einschließlich ihrer Hilfsbilder; diese Zahl wird nicht zu einer angeblichen eigenen QA02-Serie addiert. Ausgewählte Originalkarten, Settings-, Panel-, Editor- und iOS-Bilder wurden visuell gesichtet.

## 22 Abnahmegates

PASS gilt ausschließlich im genannten JUnit/Pbuffer-/Diff-Kontext.

| Gate | Urteil | Konkreter Beleg / Grenze |
|---|---|---|
| Q02-01 | PASS | vorab geschriebene Owner-/Clip-/Inputinventur, finale Familienklassifikation statt Pauschalbehauptung |
| Q02-02 | PASS | MainMenu produktiv unverändert; Motion/Interaction/Vis02 und natives MainMenuVisualCheck |
| Q02-03 | PASS begrenzt | sechs Settingsaktionen, reale Down-/Release-/Key-/Bounds-/Resizefälle, native Preset/Press/Focus und kompletter Bestandscheck |
| Q02-04 | PASS begrenzt | Chrome/Modulkopf/Valuefelder migriert; einmalige echte Module-/Valueaktionen und Clip-/Revealtests; einzelne Optionsfedern DEFERRED |
| Q02-05 | PARTIAL / DEFERRED | Kategorie-Motion und reale inverse Fit-Hits geprüft; iOS-Components wegen Reveal-/Inputowner nicht migriert; QA01-Gradientregression grün |
| Q02-06 | PASS | echter temporärer ThemeCatalog/Store, no-write Hover, Disabled, Save/Cancel/Reset/Delete; native Bestandsfixture grün |
| Q02-07 | DEFERRED | Account-Mixed-Owner und fehlender Paint-Lifecycle; synthetischer Bestands-AccountUiTest, keine Auth-/Credentialaktionen |
| Q02-08 | PASS für MIGRATED | innerer Lift/Scale/Helligkeit, sofortige Fokusform ohne Border/Glow, Press-Linie und Disabled-Reset; Bilder und reine Zustände |
| Q02-09 | PASS | identische Laufzeit bei 30/60/144/360, Reversal/Invalid-dt/Pause, drei native Presetbilder; keine reale FPS-Messung |
| Q02-10 | PASS | 120-ms-Gerade, Snap, Ambient-unabhängige Interaktion; MainMenu-Regression |
| Q02-11 | PASS begrenzt | reale Release/Key/Resize/Reopen/Close-Visualzustände, unveränderte Captureowner; OS-Fokusereignisse NOT_RUN; zurückgestellte Familien ausgeschlossen |
| Q02-12 | PASS | komplettes 49-Altwerteorakel plus erhaltenes Design-ID/Index/Options-/Defaultorakel |
| Q02-13 | PASS Cold/Warm-Load | stabiler neuer Value, 20 echte Codec-Roundtrips, Legacy-Cold-Load und warmer ClientSettings-Import; neuer Default im bestehenden Batch, alte Roundness und neuer Selektor je ein Callback; kein CORE-Edit |
| Q02-14 | PASS Pbuffer | alle vier Materialien: echte alte/neue drawCards bei gleicher Timeline, jeweils 0 Pixel |
| Q02-15 | PASS Pbuffer | 5×4 Karten, alle 40 Paare unterscheidbar; bestehende Surfaces/Materialargumente unverändert |
| Q02-16 | PASS Pbuffer | öffentliche render-Proben, drei Methoden A→B→A, .1/.8 bei identischem dt; keine neue Dekouhr |
| Q02-17 | PASS begrenzt | 40 separate Statusbilder, vier unterschiedliche glyph masks, erhaltene Rollenfarben/Error und feste Textink; keine allgemeine Kontrastfreigabe |
| Q02-18 | PASS begrenzt | unveränderte Timeline-Testfälle + neue Themeschleifen, vier reale Ecken bei kleiner/normaler Größe; Inbox/Queue/Layout-Source unverändert |
| Q02-19 | PASS Pbuffer | gezielte 2:2-Ressourcenprobe, steady Frames, Recreate, State/Clip, erzwungene NoShader/NoFBO-Flags; kein physisch shaderloses Gerät beansprucht |
| Q02-20 | PASS im geprüften Scope | alte VIS-02-/QA01-CPUtests und native Pixel-/sechs-Consumer-Checks grün; ausgeschlossene Consumercodepfade unverändert |
| Q02-21 | PASS | finale Fokusfolge 115/0/0/0; exakt mvn -o clean test 598/0/0/3, 103 frische XMLs unabhängig summiert, 19 Classfiles major 52 |
| Q02-22 | PASS mit externem Endreceipt | Start-/Manifest-/Allowlist-/Diff-/Schutz-Gates; zwei eigene lokale Commits, vollständiger BASE..HEAD-Patch, SHA256 und Reverse-Check im nach dem Berichtscommit erzeugten review-receipt.json |

## Git- und Übergabereceipts

38 vorbestehende Worktrees vor Anlage mit vollständigem Porcelainstatus, Branch/HEAD und SHA256 des binären Index-/Tracked-Diffs aufgenommen. Zwei Nachaufnahmen: protection-after.json/protection-comparison.json sowie die finale protection-final.json/protection-final-comparison.json; Statusabfragen ohne optionale Gitlocks. Beide Vergleiche: alle 38 HEADs, Branches und Indexdiffhashes gleich, 36 Bäume in sämtlichen aufgenommenen Merkmalen gleich. Im ursprünglichen `F:/Puslar Backup - Copy` änderten sich Status und getrackter Arbeitsdiff, in `F:/neutron-cmb-t01-c03` nur der Porcelainstatus. Diese während des Laufs beobachteten Fremdänderungen sind nicht bereinigt oder übernommen worden. QA01 ist in jedem Merkmal unverändert, weiterhin exakt und sauber. Keine fremden Bäume gestagt, bereinigt, repariert oder beschrieben.

Exakte Pfade im scope-receipt.json. Vor jedem Commit Allowlist und git diff --check; zuerst eigener Code-/Testcommit, dann genau vier Berichte. Der vollständige BASE..HEAD-Binary-Reviewpatch, .sha256 und review-receipt.json liegen extern unter F:/neutron-vis-qa02-evidence. Receipt enthält den erst nach dem Berichtscommit ermittelten finalen HEAD, Dateiliste, sauberen eigenen Status und `git apply --check --reverse`; kein zirkulärer Bericht-Selbsthash.

Code-/Testcommit: `b5ad1626bf4f984b269881e31da4ee0b72d26227`, exakt 20 freigegebene Quell-/Testdateien. Der anschließende separate Commit enthält ausschließlich diese vier Berichte. Finaler End-HEAD und Patch-SHA256 stehen bewusst im externen Endreceipt. tested-source-hashes.json bindet alle 20 Dateien an den tatsächlich bestandenen Vollsuite-Stand; owner-source-guards.json bestätigt zusätzlich unveränderte Lifecycle-/Layout-/Icontexte, alte Materialaufrufe und ThemeEditor-Aktionsmethoden.

NOT_RUN: Realspiel, natives Display/Alt-Tab/Fullscreen/F3+T, Shaderpack/OptiFine-Varianten, allgemeine Custom-Theme-/WCAG-Abnahme und 144-FPS-Produktfreigabe. DEFERRED bleibt offen; kein universelles „alle Minecraft-UIs“-Ergebnis. Keine weiteren Wellen begonnen. STOPP nach VIS-QA02.
