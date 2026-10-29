# VIS-QA02 Motion contract

28.10.2026. Basis: `a4142772ab3ff64747f22215f922c5891153fe47`. Der Umfang ist die nachstehende Präsentation, keine universelle Minecraft-UI-Migration. Vollständige Pfade und ausgeschlossene Owner stehen im [Inventar](VIS_QA02_CONTROL_INVENTORY.md).

## Gemeinsame Präsentation

Genau eine neue reine Klasse, `src/main/java/me/youngjibbit/gui/UiSpring.java`, hält pro Control einen bestehenden MenuSpring und liest MenuMotion. Sie besitzt weder Uhr noch Geometrie, Callback, Value, Subscription oder Input-Dispatcher. MainMenu, MenuSpring und MenuMotion sind unverändert. Die alte `step(target,seconds,motion)`-API bleibt bytegleich.

| Zustand | Ziel | Sofortige Zusatzinformation |
|---|---:|---|
| Idle | 0 | feste vorhandene Grundfläche |
| Hover | 1 | innere helle Dekorfläche; begrenzter Lift/Scale und Helligkeit aus MenuMotion.glow |
| vorhandener Keyboard-Fokus | .55 | zwei innere helle Klammern, unabhängig von Border/Glow und Federfortschritt |
| linker visueller Press | -.45 | untere Linie; bestehender Action-Zeitpunkt bleibt erhalten |
| Release/Cancel/Fokuswechsel | neues Hover-/Fokus-/Idle-Ziel | keine Wiederholung oder Verzögerung einer Aktion |
| Disabled/nicht verfügbar | 0, sofort | keine aktive Feder-/Fokus-/Press-Präsentation; bestehende deaktivierte Beschriftung bleibt |

`GuiStyle.feedback` zeichnet ausschließlich innerhalb einer projizierten, mit dem vorhandenen Scissor geschnittenen Control-Kontur. Eine kleinere Innenfläche skaliert und hebt sich; Text, äußere Fläche, Rundungs-/Hit-Test und Input-Koordinaten werden nicht transformiert. Die sofortigen Fokusklammern und Press-Linie sind Formsignale, kein ausschließlich farbiges Feedback. Neue Konturen/Glow-Pipelines werden nicht eingeführt; die innere Helligkeit nutzt die bestehende Preset-Glow-Amplitude. Bestehende Surface-/Glow-Schalter behalten ihren Owner.

| Preset | Omega | Dämpfung | Lift bei Response 1 | Scale | Glow-Amplitude |
|---|---:|---:|---:|---:|---:|
| Calm | 22 | .92 | .8 | 1.010 | .12 |
| Balanced, Default | 19 | .76 | 1.6 | 1.020 | .22 |
| Expressive | 17 | .64 | 2.6 | 1.032 | .34 |

Das sind die unveränderten VIS-01-Werte, keine neuen Produktkennzahlen. MenuMotion begrenzt Renderresponses auf [-.6,1.15]; der jeweilige Innenabstand begrenzt den sichtbaren Weg zusätzlich. Presetwechsel erhalten den Federzustand.

## Zeit und Schalter

Jeder migrierte Screen liest pro Draw genau einmal `System.nanoTime()` und verteilt denselben begrenzten dt an seine Controls. Settings, ThemeEditor und iOS begrenzen auf 100 ms, Panel behält seine 50-ms-Grenze. ThemeEditor hatte zuvor keine interaktive Framezeit; sein neuer lokaler Frame-Zeitstempel ist ausschließlich Präsentation. Es gibt keine neue globale Uhr. MenuSpring behandelt negative/nicht endliche dt als 0 und begrenzt lange Schritte auf 250 ms.

| UI Animations | Reduced | Ambient | Interaktion |
|---|---|---|---|
| OFF | beliebig | beliebig | sofortiges Snap, einschließlich dt=0 |
| ON | ON | beliebig | unveränderte 120-ms-Gerade, beginnend am aktuellen Visualwert, kein Overshoot |
| ON | OFF | OFF | normale Preset-Feder; Hover bleibt aktiv |
| ON | OFF | ON | normale Preset-Feder |

UiEffects.beginFrame, globaler Gradienttakt, Theme-/Channel-/Pause-Formeln sind nicht verändert. Normale Reversals erhalten Geschwindigkeit und Position; Reduced-Reversals beginnen eine neue Gerade am aktuellen Wert. Disabled verwirft eine aktive visuelle Feder sofort. Init/Resize/Reopen/Close löschen die neu eingeführten transienten Präsentationszustände.

## Nachgewiesene Screen-Grenzen

| Oberfläche | Status | Fachlicher Owner bleibt |
|---|---|---|
| MainMenu, acht Aktionen | ALREADY_COVERED | gerundeter Press/Release, Cancel, Keyboard und bisherige Feder |
| SettingsMenu, sechs Aktionen | MIGRATED | `actionBounds`; Aktion weiter auf Mouse-down, Release löst keine zweite aus |
| SettingsMenu Value-/Slider-/Editzeilen | DEFERRED für neue Feder | vorhandene Focus-/Clip-/Value-/Drag-/Exact-Inputlogik unverändert |
| Panel Kategorie/Search/Themes | MIGRATED | originale Chrome-Rechtecke; Kategorie- und Themes-Keyboardrouten erhalten |
| Panel Modulkopf, Checkbox, Combo-Feld, Slider-Feld | MIGRATED | Component.hit/clip, settingsClip, Track-/Valuekoordinaten und bestehende Keyhandler |
| Panel einzelne Combo-Optionszeilen | DEFERRED für eigene Feder | bestehendes optionsClip und Auswahl/Reveal; keine neue Option-Capturelogik |
| iOS Kategorien | MIGRATED, Pointer-Präsentation | tatsächliche inverse Opening-/Fit-Transformation; originale Opening-Sperre; kein neuer Keyboard-Fokus erfunden |
| iOS Module/Checkbox/Combo/Slider | DEFERRED | bestehender sichtbarer Reveal-/Combo-Inputvertrag ist nicht deckungsgleich; keine versteckte Inputreparatur |
| ThemeEditor vorhandene Controls einschließlich Disabled | MIGRATED | actionBounds, enabled(id), Focus/Draft/Save/Cancel/Delete/Reset und Preview-Owner |
| AccountUI und Vanilla/Container | DEFERRED bzw. NOT_APPLICABLE | keine Auth-/Datei-/Credential-/Vanilla-Eingriffe |

Panel verteilt dt auch an nicht mehr sichtbare/cached Komponenten und setzt deren aktive Darstellung zurück. Die Headerfeder einer Modulkarte ändert nicht deren expandierte Höhe. Sichtbare Values können weiter reagieren, wenn der zugehörige Modulkopf oberhalb des Viewports liegt. Innere Dekorationen erhalten denselben äußeren Scissor; kein außerhalb des sichtbaren Clips klickbares Child wird neu erzeugt. Drag-/Resize-/Keybind-Modalowner bleiben unverändert.

## Beweise

`UiSpringTest`: exakte MenuSpring-/MenuMotion-Parität, 30/60/144/360 Schritte pro Sekunde bei gleicher Laufzeit, Reverse, Null/negative/NaN/Infinity-dt, Pausecap, Snap, Ambient OFF, lineare Reduced-Bewegung, Disabled und Reset. Dies sind deterministische Zeitfolgen, keine gemessenen Bildschirm-FPS.

`Qa02ScreenMotionTest`: echte Settings-/Panel-/Editor-/iOS-Handler und Layouts, stabile Rechtecke, einmalige Aktionen, Clip-/Closed-Children, echte Checkbox-/Sliderwerte, temporäre ThemeStore-Save/Cancel/Reset/Delete, Disabled und inverse iOS-Skalierung. Headless Panel-/Editor-Tests rufen zur Entsorgung die reine Präsentations-Resetnaht auf, weil der unveränderte TextEffects.close auch ohne Ressourcen GL abfragt. Der vollständige reale Closepfad wird separat im Pbuffer ausgeführt und auf leere neue Visualzustände geprüft; keine GPU-Ausführung wird aus einem JUnit-Erfolg abgeleitet.

`Qa02RenderCheck`, finale echte Readbacks: Calm/Expressive-Regionen unterscheiden sich in Settings um 5.465, Editor um 2.359, Panel-Chrome um 2.506, Checkbox um 523, Combo-Feld um 7.247, Slider-Feld um 7.252 und iOS-Kategorie um 3.616 Pixel. Alle drei Presets besitzen eigene Settingsbilder. Pointer-Handler, Disabled Delete/Cancel, Panel-Theme-Route, iOS-Kategorie unter realem Fit bei 320×224 sowie reale Close-/GL-/Scissorzustände werden zusätzlich geprüft. Die Pufferbilder laufen überwiegend mit Animations OFF, um Preset-Amplituden exakt zu vergleichen; Feder-/Reduced-Verläufe sind die gesonderten JUnit-Orakel.

Die iOS-Partikelliste wird im synthetischen Readback-Fixture geleert, um Hintergrundrauschen nicht als Controlnachweis zu zählen. Die Produktionspartikel und deren Owner bleiben unverändert. Die Clip-/Hit-/Scaleproben benutzen die tatsächlichen Screen- und Componentmethoden.

Die alte ThemeEditor-Statiksnapshot-Fixture erhielt erst nach belegtem Render-RED eine deterministische Präsentations-Beruhigung (16 × .25 s), ohne UiEffects-Zeitfortschritt, Schalteränderung oder gelockerte Gleichheitsassertion. Die ursprünglichen Handler-/Persistenz-/Ressourcenassertionen bleiben erhalten. Ausführungszahlen und Grenzen: [RESULTS.md](RESULTS.md).

NOT_RUN: natives Fenster, Alt-Tab/OS-Fokusereignisse, Fullscreen, Spielwelt, Shaderpack/OptiFine-Varianten und 144-FPS-Produktabnahme. Keine universelle Focus-/Motion-Migration der zurückgestellten Familien. STOPP nach VIS-QA02.
