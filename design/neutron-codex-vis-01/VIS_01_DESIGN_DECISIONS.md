# VIS-01 — Designentscheidungen

Stand: 28.10.2026. Freigabe: ausschließlich VIS-01. Basis: `3554f7a9545fbc03b91fbdf9af453ba851bf0f2c`, eigener Branch `codex/neutron-vis-01`, Worktree `F:\neutron-vis-01`.

## Verifizierter Bestand und Ziel

Der lokale MainMenu besitzt fünf Hauptaktionen und drei Utility-Aktionen, gemeinsame `ClipRect`-Geometrie, gerundete Hit-Tests, `MenuSpring`, einen Stellar-Shader und die vorhandene `ArrayListSurface`. Der Bestand hatte bereits FPS-unabhängige analytische Spring-Schritte. VIS-01 ersetzt diese Oberfläche nicht: Es ergänzt die sichtbare Tiefe und trennt Fokus, Hover und Pressed deutlicher.

Zwei neue Tests haben vor Produktionsänderungen reale Capture-Fehler nachgewiesen: Escape ließ einen Press bestehen; eine Tastaturaktivierung konnte anschließend durch den alten Maus-Release eine weitere Aktion auslösen. Die Korrektur bleibt in MainMenu. Bestehende Tests wurden nicht bearbeitet.

## Sichtbare Umsetzung

Die äußere Buttonfläche und ihr Hit-Test bleiben unverändert. Eine dezente getönte Innenfläche und das Label bewegen sich innerhalb dieser Kontur; das Label wird bei knappen Breiten/Höhen passend skaliert. Der Hover erhält Lift, kleinen Scale und einen vorhandenen Glow-Pass. Pressed erhält zusätzlich eine unmittelbar sichtbare untere Linie. Fokus erhält zwei innere helle Klammern, die auch bei ausgeschaltetem Global Border/Glow bestehen bleiben. Damit hängt Fokus nicht allein von einer Akzentfarbe ab.

Obsidian-Flächen verwenden dunkle neutrale Basistöne; Akzente stammen aus der bestehenden Theme-API. Custom Themes liefern weiterhin Hintergrund und Textfarbe. Theme-Auswahl, gespeicherte Palette, UI-Material-/Effekt-Eigentümer und Produktname/-version/-autor bleiben unverändert. Insbesondere wird kein neues persistentes Cyan-Standardtheme eingeführt. Die nativen Zusatzbilder verwenden ein ausdrücklich synthetisches Cyan-Theme; die Bestandsprüfung verwendet SynthWave.

## Motion-Presets

`UI Motion Intensity` ist genau ein neues `ComboValue` im vorhandenen ClientSettings-Modul und in `Gradients / Motion`. Seine persistente ID ist `ui_motion_intensity`; Reihenfolge: Calm, Balanced, Expressive; Default: Balanced. Ohne ClientSettings verwendet MainMenu ebenfalls Balanced. Keine neue Configschicht und keine Migration.

| Preset | Omega | Dämpfungsgrad | Lift bei Response 1 | Innerer/Label-Scale bei Response 1 | Lokale Halo-Alpha bei Response 1 |
|---|---:|---:|---:|---:|---:|
| Calm | 22 | 0,92 | 0,8 GUI-px | 1,010 | 0,12 |
| Balanced | 19 | 0,76 | 1,6 GUI-px | 1,020 | 0,22 |
| Expressive | 17 | 0,64 | 2,6 GUI-px | 1,032 | 0,34 |

`MenuMotion` ist ein kleiner UI-lokaler Präsentationstyp, kein Manager. Seine Werte werden direkt beim Zeichnen konsumiert. Die Response ist für Rendermaße auf [-0,6; 1,15] begrenzt; Glow ist nicht negativ. Expressive kann kontrolliert überschwingen. Fit und Innenabstände begrenzen die tatsächlich sichtbaren Wege bei kompakten Buttons. Ein Presetwechsel setzt die laufende Spring-Position/-Geschwindigkeit nicht zurück; unterschiedliche Amplituden gelten ab dem nächsten Frame.

## Reduced Motion, Altvertrag und Zeit

Priorität: `UI Animations=false` erzwingt sofortiges Snap; sonst ersetzt `Reduced Motion=true` die Feder durch eine 120-ms-Gerade vom aktuellen Wert zum Ziel. Zielwechsel starten diese Gerade am aktuellen Display-Wert. Keine asymptotische Endlosschleife und kein Overshoot. Die Spring-API `step(target, seconds, motion)` bleibt Balanced mit bisherigen Kennwerten und Snap bei `false`.

Alle acht Controls verwenden dieselbe einmal pro Draw ermittelte Framezeit. `UiEffects.beginFrame()` bleibt unverändert für den globalen UI-/Gradient-/Backdrop-Takt. Da dessen Phase bei Reduced Motion eingefroren wird und keine Frame-dt-API vorliegt, nutzt MainMenu seine bereits vorhandene monotone Framezeit für die kurzen Übergänge. Es gibt keine neue Uhr oder lokale Gradientphase. Negative und nicht endliche dt ergeben 0; endliche Pausen werden auf 250 ms begrenzt.

Ambient Motion, globale Flow/Ping-Pong/Pulse-Methoden, Semantic Color Priority, Panel/iOS/Dropdown, HUD, neue Shader, Fonts und Assets gehören nicht zu VIS-01.

## Abnahmegrenzen

Die tatsächlichen JUnit-, Native- und Screenshot-Ergebnisse stehen in RESULTS.md. Offscreen-Bilder belegen GPU-Rendering im isolierten Pbuffer, keine vollständige Spiel-/Display-/Shaderpack-Abnahme. Verbleibend: Produktreview der Kennwerte und Kontraste einschließlich schwieriger Custom Themes, reale Fenster-/Fullscreen-/Reload-Matrix, G-VIS-GRADIENT, G-VIS-HUD, G-QA-SHADER und G-QA-144FPS. Die fest verdrahtete vorhandene Backdrop-Qualität bleibt unverändert; daraus folgt kein neues Qualitätsprofil.
