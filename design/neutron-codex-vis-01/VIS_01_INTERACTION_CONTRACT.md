# VIS-01 — Interaktionsvertrag

Basis: `3554f7a9545fbc03b91fbdf9af453ba851bf0f2c`. Dieser Vertrag beschreibt die tatsächliche MainMenu-Implementierung und trennt ausgeführte Prüfungen von offenen nativen Gates; Laufzahlen stehen in RESULTS.md.

## Aktionen und Fokusfolge

| ID | Unverändertes Label | Aktivierung |
|---:|---|---|
| 0 | Singleplayer | `GuiSelectWorld(this)` |
| 1 | Multiplayer | `GuiMultiplayer(this)` |
| 2 | Account Manager | `GuiAltManager` |
| 3 | Settings | `SettingsMenu(this)` |
| 4 | Exit | `mc.shutdown()` |
| 5 | Motion: on/off | ausschließlich vorhandenes `animations` toggeln |
| 6 | Blur: on/off | ausschließlich vorhandenes `blur` toggeln |
| 7 | Glow: on/off | ausschließlich vorhandenes `glow` toggeln |

Tab und Rechts/Runter gehen zyklisch zur nächsten ID; Shift-Tab und Links/Hoch zur vorherigen. Return, Numpad Enter und Space aktivieren die fokussierte Aktion. Escape bricht Capture ab und setzt Fokus 0; es schließt das Root-Menü nicht. Jeder Key-Event beendet einen alten Maus-Press, bevor eine Tastaturaktion erfolgt. Fokus ist sofort fachlich gesetzt, unabhängig von der Motion-Response. Zwei innere Klammern markieren ihn auch ohne Hover/Glow/Border. Es gibt keine fachlich deaktivierte Aktion; VIS-01 erfindet keinen Disabled-Zustand und entzieht keiner Aktion Rechte.

## Maus und Geometrie

Nur linker Press innerhalb einer gerundeten Action-Kontur erfasst deren ID. Eine Aktion erfolgt erst beim linken Release in derselben Kontur; Capture wird vor der Aktion gelöscht. Release außerhalb oder über einer anderen Aktion verwirft den Press; wiederholter Release aktiviert nichts. Rechtsklick aktiviert nicht. Das Menü besitzt keine Scrollaktion.

Die gemeinsame `actionBounds(id)`-Geometrie dient Darstellung und Hit-Test. Rechte/untere Kanten sind exklusiv; unsichtbare runde Ecken sind nicht klickbar. `UiEffects.radius` liefert denselben Radius wie die vorhandenen Round-Primitiven. Die Grundfläche bewegt/skaliert sich nicht. Scale/Lift gelten ausschließlich für die kleinere Innenfläche und passend eingepassten Text, nie für zusätzliche Klickrechte. Außen liegender Glow ist dekorativ.

## Zustände und Lebenszyklus

| Zustand | Spring-Ziel | Unabhängige Darstellung/Rechte |
|---|---:|---|
| Idle | 0 | feste dunkle Grundkontur |
| Hover | 1 | Lift/Scale/Halo entsprechend Preset; keine Aktivierung |
| Keyboard Focus ohne Hover | 0,55 | sofort sichtbare Fokusklammern; alle Keyboardrechte |
| Pressed | -0,45 | gedrückte Materialdarstellung plus untere Linie; Aktivierung erst bei Release |
| Cancel | neues Hover-/Fokus-/Idle-Ziel | Spring setzt am aktuellen Wert fort; Capture endet sofort |

`initGui()` bei Resize/Reentry löscht Capture, alle Responses und Spring-Zustände; gültiger Fokus wird beim Resize erhalten. `onGuiClosed()` löscht dieselben Zustände, setzt Fokus 0 und schließt die vorhandene Surface. Ein veralteter Release nach Resize/Close/Reopen löst keine Aktion aus. Renderpausen beeinflussen keine Eingaberechte. Normale Übergänge erhalten Position und Geschwindigkeit bei Targetwechsel; Reduced Motion startet eine neue lineare Strecke am aktuellen Wert. Animation Off hat Vorrang.

## Tests und technische Grenzen

| Anforderung | Testzuordnung |
|---|---|
| V01-01/02/03, Setting/Default/IDs/Config | `MainMenuMotionTest.missingSettingsUseBalanced`, `ClientSettingsMotionTest`, unverändertes `ClientSettingsConfigTest` |
| V01-04, tatsächlicher MainMenu-Consumer und getrennte Rendermaße | `MainMenuInteractionTest.actualConsumerUsesPresetAndKeepsHitBoundsStableDuringMotion`, `MainMenuMotionTest.presetsDriveDistinctBoundedLiftScaleAndGlow`, native Preset-Bilder |
| V01-05/06/07/08/09, FPS/Reverse/Pauses/Reduced/Legacy | `MainMenuMotionTest`, `MainMenuInteractionTest.consumerSeparatesLinearReducedFromLegacySnapAndResetsOnLifecycle`, unveränderte `MainMenuCheck.springChecks` |
| V01-10/11/12, reale Maus-/Key-Handler | `MainMenuInteractionTest` einschließlich Escape, Doppelauslösung und Shift-Tab; unverändertes natives `MainMenuCheck` |
| V01-13/14, Bounds/Lifecycle | JUnit bei 480×320, 320×240, 367×245, 320×180 und 1920×1080 GUI-Einheiten; native Check-Größen plus Scale 1–4 |
| V01-15, Utility-Semantik | `MainMenuInteractionTest.utilityTogglesChangeOnlyTheirExistingValue`, natives Bestandscheck |
| V01-16/17, Theme/Effekt-/GPU-Grenzen | native Zusatzbilder mit Custom Cyan, Effekte OFF; übrige schwierige Themes und Low/Balanced/High-/NoGL-Matrix offen |
| V01-18, Referenzhardware/Shaderpack/FPS | NOT_RUN, separates Testlabor erforderlich |

JUnit ruft echte Eingabehandler auf und fängt Navigation ab, damit keine Accounts/Welten/Server/Shutdown gestartet werden. Der kleine ModuleManager-Fixture registriert echte ClientSettings-Values; Persistenztests verwenden den bestehenden ConfigManager in temporären Verzeichnissen. Keine vorhandene Assertion wurde gelockert.

Native Prüfungen laufen in `MainMenuVisualCheck` mit dem bestehenden `PbufferTestContext` und unverändertem `MainMenuCheck`, separatem JVM-Prozess und externem Arbeitsverzeichnis. Der Fixture erzwingt echte Shader/FBO-Fähigkeit, prüft Readback und Diagnosefehler und startet keinen Spielclient. Der vorhandene Surface-Fallback bleibt erhalten. Ein fehlender GL-Kontext oder Shader-/FBO-Ausfall ist durch diese Pbuffer-Prüfung nicht abgedeckt; insbesondere sind die vorhandenen Round-/Font-/Backdrop-Shader keine hier neu bewiesene vollständige NoGL-Implementierung. Keine Hotfile- oder Renderarchitekturänderung in VIS-01.
