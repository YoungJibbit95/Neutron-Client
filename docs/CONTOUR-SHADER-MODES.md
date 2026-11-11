# Kontur-Shader: Flame, Neon und Lines

## Auswahl und Bedienung

Die Effektform und die Bewegung des Farbverlaufs sind getrennte Einstellungen. `Gradient Method` bleibt für `Flow`, `Ping-Pong`, `Pulse` und `Perimeter Orbit` zuständig. Die drei Kontureffekte werden direkt im Render-Modul gewählt; für die UI gibt es eine eigene Auswahl.

| Bereich | Einstellung | Wirkung |
| --- | --- | --- |
| ESP | `Mode` → `Flame`, `Neon` oder `Lines` | Effekt um das projizierte Rechteck eines Objekts. Die bisherigen Modi bleiben verfügbar. |
| GlowESP | `Glow Mode` → `Standard`, `Flame`, `Neon` oder `Lines` | Effekt an der gefüllten Silhouette der ausgewählten Spieler, Tiere, Mobs und Truhen. |
| Nametags | `Mode` → `Static`, `Flame`, `Neon` oder `Lines` | Effekt um die Nametag-Fläche; der eigentliche Text bleibt lesbar. |
| Client Settings → Glow | `Global Glow` einschalten, `UI Glow Mode` wählen | `Standard`, `Flame` oder `Neon` für die angeschlossenen UI- und HUD-Glow-Flächen. `Lines` ist hier bewusst nicht auswählbar. |
| Client Settings → Typography | `Text Glow` → `On` | Globaler Textglow für geeignete Schriftzüge; `Module settings` behält die lokalen Entscheidungen bei, `Off` deaktiviert Textglow. |

`UI Glow Mode` aktiviert keine Welt-Module und überschreibt deren Moduswahl nicht. Für einen normalen UI-Glow bei gleichzeitigem Flame-ESP bleibt die UI auf `Standard`. Der globale Glow-Schalter steuert die UI; die explizit gewählten Welt-Effekte werden über ihre Module aktiviert und deaktiviert.

**Flame** verbindet einen hellen, an der Kontur haftenden Ansatz mit aufsteigenden, unterschiedlich langen Flammenzungen in den Theme-Farben. **Neon** hat einen nahezu weißen Kern, einen farbigen Übergang und eine breitere, schwächer werdende Aura. **Lines** zeichnet getrennte konzentrische Konturen mit dezentem Leuchten und einem umlaufenden Helligkeitsverlauf.

### Gemeinsame Regler

Die Regler stehen unter **Client Settings → World effects**. Flame- und Neon-Regler gelten auch für den jeweiligen UI-Modus. Dezimalwerte bleiben erhalten.

| Regler | Standard / Bereich | Bedeutung |
| --- | --- | --- |
| `Flame Speed` | `0.18` / `0.02–2` | Geschwindigkeit der aufsteigenden Muster. |
| `Flame Height` | `0.5` / `0–1` | Länge der Flammenzungen relativ zur Effektweite. |
| `Flame Motion` | `0.65` / `0–1` | Stärke des bewegungsabhängigen Gegenwinds. Bei `0` bleibt die zeitliche Flammenbewegung aktiv. |
| `Neon Core` | `0.5` / `0–1` | Breite des hellen Kerns. |
| `Neon Glow` | `1` / `0–2` | Stärke der äußeren Aura; der Kern bleibt separat erhalten. |
| `Line Segments` | `4` / `1–16`, ganzzahlig | Anzahl vollständiger Konturen, keine Unterteilung einer einzelnen Linie. |
| `Line Spacing` | `3` / `2–8` | Abstand der Konturen in logischen GUI-Pixeln. |
| `Line Trail` | `0.5` / `0–1` | Breite des umlaufenden helleren Abschnitts; höhere Werte verbreitern ihn. |

Unter **Glow** bestimmen `Glow Extent` (`9`, Bereich `1–24`) und `Glow Intensity` (`1`, Bereich `0–2`) die UI-Weite und -Intensität. ESP und Nametags übernehmen die globale Effektweite. **GlowESP** verwendet für Flame/Neon seinen eigenen `Radius` (`5`, Bereich `2–20`) sowie `Intensity` (`3`, Bereich `0.5–3.5`). Bei Lines legen Anzahl und Abstand die Ausdehnung fest. `Kawase Glow`, `Iterations` und `Offset` gehören zum bisherigen GlowESP-Standardpfad; sie steuern keine zusätzlichen Durchläufe der neuen Modi.

GlowESP nutzt mit `Color Mode = Sync` den Theme-Verlauf. `Random` und `Custom` behalten die Farbe aus der jeweiligen Objektmaske. Semantische Farben, etwa die grüne Team-Markierung bei ESP und Nametags, bleiben erhalten. Für die Palette gelten die gemeinsamen Gradient-Einstellungen; die Flammenform ist kein weiterer Gradient-Modus.

## Bewegung und Grenzen

Flame verwendet **physikinspirierte Advektion und gedämpfte Bildschirmbewegung**, keinen Fluid-Solver. Zwei kohärente Noise-Ebenen wandern nach oben, formen die Zungen und erhalten durch einen begrenzten Gegenwind ihre Biegung. Die Darstellung simuliert weder Druck noch Temperatur oder Kollisionen.

`EffectMotion` misst die Verschiebung in physischen Bildschirm-Pixeln pro Sekunde. Eine analytisch integrierte, kritisch gedämpfte Antwort lässt den Gegenwind weich aufbauen und ausklingen. Die Berechnung berücksichtigt das Zeitintervall; wiederholte Abfragen desselben Frames integrieren nicht mehrfach. Dadurch hängt die beabsichtigte Reaktion nicht von einer festen Tick- oder Bildrate ab.

- **ESP und Nametags:** ein Bewegungszustand pro Entity. Die projizierten Flächen reagieren einzeln auf Objekt- und Kamerabewegung; nicht mehr sichtbare Einträge werden entfernt.
- **GlowESP:** ein gemeinsamer Wind für die gesamte Silhouettenmaske, abgeleitet aus den projizierten Mittelpunkten der tatsächlich erfassten Objekte. Eine geänderte Objektmenge setzt den Zustand zurück. Gegenläufige Objektbewegungen können sich im Mittel aufheben; dieser Pfad berechnet keine separate Strömung für jede Figur.
- **UI:** dauerhaft zugeordnete Renderer wie `ArrayListSurface` und `TextEffects` können ihre Flächenbewegung verfolgen. Anonyme Rechtecke der Glow-Queue und direkt im Schriftatlas gerenderter Text erhalten die zeitliche Flammenanimation ohne eigenen Drag-Gegenwind. Kleine Layoutänderungen bleiben gedämpft; große Größenwechsel beginnen neutral.

Viewport-Wechsel, große Positions- oder Größensprünge, ungültige Eingaben, rückwärts laufende Zeit und längere Abfragepausen setzen die Bewegung zurück. Weltwechsel und Modulabschaltung löschen die zugehörigen Zustände. So wird ein Teleport oder Resize nicht als lang anhaltender Bewegungsimpuls übernommen.

`UI Animations = Off`, `Reduced Motion = On` oder `Ambient Motion = Off` halten die gemeinsame Effektzeit an und deaktivieren den Gegenwind, auch in den neuen Welt-Effekten. Das erneute Einschalten startet die Bewegung ohne aufgestauten Impuls. Ein statischer Farbverlauf allein schaltet die zeitliche Flammenanimation nicht aus.

## UI- und Config-Kompatibilität

Neue Values sind an die bestehende Registrierung angehängt; vorhandene Value-Indizes bleiben erhalten. Beim Laden älterer Configs ohne neue Schlüssel gelten `UI Glow Mode = Standard`, `Flame Motion = 0.65` und `Line Spacing = 3`.

Frühere geometrische Einträge unter `gradient_method` werden beim Import normalisiert:

| Alter Eintrag | Neuer `gradient_method` | Abgeleiteter `ui_glow_mode` |
| --- | --- | --- |
| `Flame` | `Flow` | `Flame` |
| `Neon` | `Flow` | `Neon` |
| `Lines` | `Flow` | `Standard` |

Ein ausdrücklich gespeicherter gültiger `ui_glow_mode` hat Vorrang, unabhängig von der Schlüsselreihenfolge. Ein neuer `ui_glow_mode = Lines` ist ungültig. Die Auswahl von Lines in ESP, GlowESP oder Nametags bleibt davon unberührt. Beim nächsten Speichern werden die getrennten Schlüssel verwendet.

Die UI-Anbindung nutzt vorhandene Glow-Flächen, Clipping und Textregeln. Feste semantische Farben werden weiter berücksichtigt. Der direkte `FontInk`-Pfad hält das Leuchten **kompakt im bestehenden Glyphenatlas**: begrenzte Abtastungen innerhalb der gepolsterten Glyphenzelle, eine kleine Effektweite und darüber die scharfe Schrift. Dafür entstehen keine zusätzlichen Text-FBOs oder Blur-Durchläufe. Bestehende `TextEffects`-Capture-Pfade behalten ihre eigene Renderkette; diese Aussage gilt nicht pauschal für sämtliche Text-Renderer.

## Technischer Aufbau und Kostenmodell

Die gemeinsamen GLSL-Funktionen in `contourEffects.glsl` definieren Konturlicht, Palette und Flammenverformung. `ContourEffects` überträgt Parameter und normalisiert die Einheiten auf physische Pixel. ESP-/Nametag-Rechtecke und UI-Flächen nutzen analytische Konturabstände; sie benötigen kein eigenes Silhouetten-FBO pro Rechteck.

Für **GlowESP** erhält `ContourMaskRenderer` die bereits gefüllte Objektmaske vor der bisherigen Outline-/Blur-Kette:

1. Ein Seed-Pass ermittelt Konturpunkte aus der gemeinsamen Maske.
2. Zwei wiederverwendete **Half-Resolution-Felder** mit jeweils `ceil(width / 2) × ceil(height / 2)` Pixeln tauschen in begrenzten Jump-Schritten die nächsten Konturpunkte aus. RGBA8 packt zwei 16-Bit-Koordinaten; Float-Render-Targets sind dafür nicht nötig.
3. Ein gemeinsamer Composite-Pass zeichnet den Effekt außerhalb der gefüllten Maske auf das ursprüngliche Renderziel.

Bei gleicher Effektweite, GUI-Skalierung und gleichem Linienabstand bleibt die Zahl dieser Nachbearbeitungsdurchläufe gegenüber **Entity-Anzahl und Lines-Anzahl fest**. Für Lines wird das Feld bereits für alle 16 unterstützten Konturen vorbereitet; der Shader bestimmt die nächstgelegene Linie direkt, ohne Schleife über die Linienzahl. Es gibt keine eigene FBO-Kette und keinen zusätzlichen Modell-Renderpass pro Entity für den Effekt.

Die Jump-Kette hängt weiterhin von der benötigten Reichweite ab: ein größerer Radius, Linienabstand oder GUI-Faktor kann ihre Länge ändern. Mehr Entities verursachen weiterhin Maskengeometrie und Projektion; mehr belegte Bildfläche verursacht mehr Pixelarbeit. Bei analytischem ESP/Nametags bleibt außerdem ein Draw pro dargestellter Fläche erforderlich. Eine konstante Composite-Passzahl ist daher keine Zusage konstanter Framezeit.

Programme und Felder werden bei Bedarf erstellt und wiederverwendet. Resize ersetzt die Felder, Abschaltung gibt Ressourcen frei; erneutes Aktivieren kann sie wieder anlegen. Der bisherige GlowESP-Standardpfad bleibt separat. Die neuen Nachbearbeitungspfade stellen Renderziel, Viewport, Clipping, Blend- und Texturzustand über `RenderState` wieder her.

## Validierung als Verfahren

Dieses Dokument beschreibt Verhalten und Prüfziele. Es behauptet weder eine bestandene native Prüfung noch gemessene Client-FPS.

- **Java-8-Tests:** mit der Projekt-Toolchain Temurin 8u482 `EffectMotionTest`, `ExtendedGradientControlsTest`, `ShaderEffectConfigMigrationTest` und die betroffenen UI-/Config-Tests ausführen. Zu prüfen sind zeitabhängige Dämpfung, wiederholte Frame-Abfragen, Reset-Fälle, Grenzwerte, gespeicherte Dezimalwerte und die Config-Migration.
- **Produktions-GLSL im Testkontext:** `ShaderModesRenderCheck` mit seinem Pbuffer-Kontext ausführen. Bilder und Report für Rechtecke, gefüllte Silhouetten, weiße Neon-Kerne, getrennte Lines, zeitliche Flame-Bewegung, UI/Text, Clipping, Resize und Ressourcen-Lebensdauer prüfen. Synthetische Masken und Pbuffer-Messungen ersetzen keinen laufenden Spielclient.
- **Native Spielprüfung:** alle Modi bei GUI-Skalierung 1/2/3, Fensteränderung, Modulwechsel, Weltwechsel, Kameraschwenks und sich unabhängig bewegenden Entities vergleichen. UI öffnen, schließen, scrollen und HUD-Flächen verschieben; Schriftlesbarkeit, Randbeschnitt und Rückkehr zum Standardpfad prüfen. Die drei Motion-Schalter einzeln aus- und einschalten.
- **Performancevergleich:** denselben reproduzierbaren Ablauf mit gleicher Auflösung, sichtbarer Objektmenge, Shaderpack-/VSync-Einstellung und warmgelaufenen Ressourcen messen. Framezeit-Verteilung, CPU/GPU-Anteile und Ressourcenanzahl aufnehmen; Lines mit 1/4/16 Konturen sowie unterschiedliche Objektmengen vergleichen. FBO-/Programm-Bilanzen nach wiederholtem Resize und Reaktivieren kontrollieren. Messungen des isolierten Renderers getrennt von End-to-End-Clientmessungen ausweisen.

### Quelleneinstieg

- [ClientSettings](../src/main/java/me/youngjibbit/module/impl/client/ClientSettings.java) und [ConfigManager](../src/main/java/me/youngjibbit/config/ConfigManager.java): Auswahl, Defaults und Migration.
- [ContourEffects](../src/main/java/me/youngjibbit/utils/render/ContourEffects.java), [ContourMaskRenderer](../src/main/java/me/youngjibbit/utils/render/ContourMaskRenderer.java) und [GLSL-Effekte](../src/main/resources/assets/minecraft/pulsar/shader/esp/contourEffects.glsl): Parameter, Reichweite und Renderkette.
- [EffectMotion](../src/main/java/me/youngjibbit/utils/render/EffectMotion.java) und [FontInk](../src/main/java/me/youngjibbit/utils/render/FontInk.java): Bewegungsvertrag und kompakter Atlas-Textglow.
