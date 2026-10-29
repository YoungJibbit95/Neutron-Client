# VIS-QA02 Notification themes

28.10.2026. Grundlage: unveränderter QA01-NotificationManager am Commit `a4142772ab3ff64747f22215f922c5891153fe47`. Kein zweiter Notification-/Theme-/Config-Manager.

## Zwei unabhängige Appearance-Selektoren

| Value | ID | Optionen und Default | Registrierung |
|---|---|---|---|
| bestehendes Notification Design | notification_design | Dark, Outline, Liquid Glass, Global; Default Global, Index 3 | unveränderter alter Index |
| neues Notification Theme | notification_theme | Follow Global, Obsidian, Aurora, Neon Edge, Minimal; Default Follow Global, Index 0 | als 50. Value angehängt, Index 49 |

Die bisherige Reihenfolge aller 49 Values einschließlich Namen, IDs, Defaults und Combooptionen bleibt erhalten. Nur die sichtbare Notifications-Gruppe ordnet den neuen Value direkt hinter Notification Design ein. Beide gehören demselben ClientSettings-Modul und unveränderten Configcodec.

Qa02SettingsTest prüft alle 20 Theme-/Design-Paare über Save/Load, die vollständigen 49 Altwerte sowie Legacyprofile ohne neuen Schlüssel beim Kaltstart und beim warmen Profilwechsel. Der neue Selektor lädt dann Follow Global, der alte Designoverride bleibt erhalten. Nach zunächst ausschließlich belegtem Cold-Load bestätigte ein eigener warmer Behavior-RED die verbleibende Lücke.

ClientSettings nutzt dafür den vorhandenen öffentlichen beginConfigUpdate-Hook: Erst super (bestehender Callback-Batch), dann ausschließlich notificationTheme.reset(). Anschließend überschreibt ein vorhandener gespeicherter Schlüssel diesen Default regulär im selben Import. Keiner der 49 Altwerte wird zusätzlich zurückgesetzt. ConfigManager, Module und Value bleiben unverändert; ConfigManager unterdrückt bereits während apply die automatischen Persistenz-Callbacks und markiert einen expliziten Profilwechsel danach wie bisher. Das Warm-Load-Orakel prüft zusätzlich jeweils genau einen Callback für die alte Roundness-Änderung und den neuen Theme-Default. Keine zweite Persistenzschicht oder geänderte allgemeine Missing-Key-Policy.

ClientSettingsVis02Test wurde nach nachgewiesenem Längen-RED ausschließlich um die neue Ausschluss-ID und die eine zusätzliche Anzahl ergänzt. Sein 46-Zeilen-BEFORE-Array, alle alten Default-/Index-/Codec-Assertions und Toleranzen bleiben erhalten. Qa02SettingsTest ergänzt die komplette 49-Zeilen-QA01-Reihenfolge.

## Look und Materialhoheit

NotificationManager.drawCards benutzt weiterhin dieselben inheritedPanel/styledPanel-Aufrufe, Materialargumente, Surfaces, Blur-/Glow-/Border-Gates und denselben Content-Callback. Ein privater dekorativer Zusatz innerhalb dieses Callbacks ist der einzige neue Darstellungsweg. Kein Shader, keine neue FBO-Pipeline, keine neue lokale Phase und kein zusätzlicher Timer.

| Theme | Dekoration innerhalb der unveränderten Karte |
|---|---|
| Follow Global | kein Zusatzaufruf; alter Renderpfad unverändert |
| Obsidian | gedämpfte matte Innenfläche, schmale gedämpfte Theme-Kontur bei Border ON |
| Aurora | leichter Theme-Verlauf im Kopfbereich und dezente Trennlinie; Palette und Phase aus vorhandenen UiEffects-Samplern |
| Neon Edge | klare obere/seitliche Ecksegmente; vorhandene Border-Opacity und Gradient-/Accent-Wahl; keine Segmente bei Border OFF |
| Minimal | kompakter Innenrhythmus mit dezenter neutraler Trennung; unveränderte Kartenhöhe, Text-/Icon-/Timeline-Geometrie und Materialwahl |

Primary/Secondary werden aus dem aktuellen Theme über UiEffects.BORDER gesampelt, einschließlich bestehender Shared-/Sync-/Method-/Direction-/Speed-/Ambient-/Reduced-Policy. Konturteile beachten die bereits vorhandene Border Color Source=Accent. Text und Statussymbole bleiben bewusst Informationsink. Für Glow gelten allein die bisherigen Material-/Global-Gates; es gibt keinen neuen lokalen Glow-Pass. Minimal fügt keine Effektpipeline hinzu und überschreibt weder Liquid Glass noch eine globale Materialentscheidung.

## Status und Lifetime

NotificationType und die Quelltexte von icon/iconColor sind unverändert. ERROR bleibt #FF667F, auch bei Semantic Color Priority OFF, und trägt das Kreuz im Kreis. WARNING bleibt Dreieck mit Ausrufezeichen; SUCCESS/ENABLED Haken; INFO/DISABLED i. Bei Priority ON bleiben WARNING #FFC969, SUCCESS #65E0AD und INFO #71CAFF. OFF benutzt weiterhin den vorhandenen begrenzten Resolver. Keine Überfärbung dieser Symbole durch das neue Theme.

Titel und Body bleiben die vorhandenen hellen Farben. Die neue Dekoration wird vor ihnen gezeichnet. Timeline, inbox/drain/show/update, Queue/Promote/Coalesce, Expiry/Duration, vier Positionen, Thread-Grenzen, Layout/Wrap/Fit und Fortschrittsbalken sind im Produktionsdiff unverändert. Die bestehende Preview-toast-Aktion ist unverändert; die generisch eingereihte Preview liest beim Rendern denselben neuen Value wie jede Karte.

Qa02NotificationTest prüft Resolverparität in allen Appearance-Paaren und beiden Priorityzuständen sowie identische Coalesce-/Queue-/Expiry-/Promotionfolgen für alle fünf Themes. NotificationTimelineTest und Vis02NotificationTest laufen unverändert weiter.

## Getrennte GPU-Matrizen

Evidenzwurzel: `F:/neutron-vis-qa02-evidence`. Die Baseline stammt aus `native-baseline-red`, vor jedem Produktionsedit, mit echten Shadern/FBOs. Der Baseline-Lauf meldet erwartungsgemäß fehlende Motionwirkung; seine vier Notificationbilder sind dennoch gültige unveränderte Referenzen. Die nachfolgende finale Serie ist `native-qa02-delivery`.

| Matrix | Umfang | Orakel |
|---|---|---|
| Theme/Material | fünf Themes × alle vier alten Designs = 20 Karten | alle zehn Paarvergleiche pro Design unterscheiden sich; Follow Global jeweils 0 unterschiedliche Pixel zur alten Referenz |
| Status | fünf Themes × vier Rollen × Priority ON/OFF = 40 Karten | dunkles, kontrastarmes synthetisches Custom Theme; Border/Glow OFF; unveränderte exakte Glyphenmasken pro Rolle über alle Styles; vier verschiedene Formen |
| Live-Palette/Zeit | echte NotificationManager.render-Aufrufe, Flow/Ping-Pong/Pulse | A→B→A ohne neue Managerinstanz, Rückkehr jeweils 0 Regionspixel; .1/.8 bei .6 s ergeben .06/.48 Phase |
| Position | alle vier Ecken, 960×640/Scale 2 und 320×224/Scale 1 | echte Render-/Layoutbilder, Karten innerhalb der begrenzten Höhe |
| Gates/Fallback | Accent, Border OFF, NoShader/NoFBO-Flags, Release/Recreate | Accent-Pixel gegen Komposition; Neon OFF pixelgleich Follow Global; Phase bleibt; kein steady FBO-Zuwachs |

Die Obsidian-Kontur beachtet ebenfalls die bestehende Border Opacity. Ein zusätzlicher exakter ON/OFF-Vergleich bei Opacity=0 hat vor der einzeiligen Korrektur 500 Fehlerpixel nachgewiesen; die finale Serie verlangt 0. Die Custom-Matrix aktiviert ihr synthetisches Theme über die vorhandene Preview-API und prüft dessen Objektidentität, damit ein unbekannter gespeicherter Schlüssel nicht unbemerkt zum Presetfallback werden kann.

Die Statusprobe findet mindestens 202 ERROR-, 172 WARNING-, 190 SUCCESS- und 180 INFO-Inkpixel. Die Farbsignatur jeder Rolle bleibt in allen fünf Themes gleich; die vier Masken unterscheiden sich. JSON-Matrix und Originalbilder erlauben unabhängige Sichtung. Das ist eine begrenzte Dark-/Fixture-Lesbarkeitsprobe, keine allgemeine WCAG-/Custom-Theme-Freigabe.

`theme-matrix.json`, `native-qa02-delivery/status-matrix.json`, `native-qa02-delivery/pixel-evidence.json` und `screenshot-manifest.json` trennen Vergleichsregionen, Statusdaten und Original-PNG-Hashes/Maße/GUI-Scale/Theme/Preset. Pbuffer hat keine physische Monitor-DPI; diese wird ausdrücklich nicht erfunden.

Der pixelgenaue Follow-Global-Vergleich ruft den echten privaten drawCards-Pfad mit festem Timeline-Timestamp auf: So bleibt sein Lifetimebalken exakt vergleichbar. Zusätzlich prüfen Liveproben den öffentlichen render-Pfad; dabei ist die Dekorregion vom Lifetimebalken getrennt. Keine Umdeutung eines gefrorenen drawCards-Bilds zum zeitgesteuerten End-to-End-Nachweis.

Die isolierte finale Ressourcenprobe zählt 2 FBO-Erzeugungen und 2 Freigaben einschließlich eines absichtlichen Recreate. Nach zehn Warmframes erzeugen zehn weitere Frames keinen FBO. Diese Zählung ist bewusst auf die Ressourcenprobe begrenzt, nicht als Gesamtzählung aller Matrixbilder ausgegeben. Bestehende QA01/VIS-02-Ressourcen-/Gradientbelege und offene Realspiel-/Display-/Shaderpack-/FPS-Gates stehen in [RESULTS.md](RESULTS.md). STOPP nach VIS-QA02.
