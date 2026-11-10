# Legacy-Oberfläche: Einstellungen, Library und Arraylist

## Bedienung

### ClickGUI

Ein Rechtsklick auf ein Modul öffnet dessen Einstellungen. Umfangreiche Module
zeigen jetzt eigene Einstellungsgruppen, etwa **Targets**, **Aiming**, **Movement**
oder **Appearance**. Der erste Bereich ist zunächst geöffnet. Kleine Module mit
höchstens fünf unterstützten Werten zeigen ihre Werte direkt.

Die eingerückte Gruppenzeile hat eine schmale Akzentlinie, eine Anzahl der aktuell
sichtbaren Werte und ein Plus-/Minus-Zeichen. Links- oder Rechtsklick klappt sie
auf beziehungsweise zu. Mit Tab sind die Gruppen erreichbar; Enter/Leertaste
schaltet die Gruppe, Rechts öffnet und Links schließt sie. Beim Schließen endet
eine laufende Werteingabe oder Slider-Bewegung sofort. Die Einstellung behält
ihren zuletzt bestätigten Wert.

Panel und kompakte Dropdown-ClickGUI verwenden dieselben Gruppen. Im Theme-Modus
Approach 2 bleibt die flache Einstellungsdarstellung verfügbar.

### Arraylist

**ArrayList → Module Prefixes** zeigt einen hellgrauen Zusatz hinter dem Namen:

| Modul | Zusatz |
| --- | --- |
| Aura | Auswahlmodus, beispielsweise `Switch` |
| Scaffold | Bridge-Ansatz; bei Neutron der gewählte Rotationsmodus |
| Backtrack | Tatsächlich gehaltene Verzögerung, beispielsweise `34 ms (Pulse)`; sonst `Idle (Pulse)` |
| Theme | Anzeigename der aktuellen Palette |
| Andere Module | Vorhandene Statusinformation oder ein sinnvoller Mode-Wert |

Der Zusatz hat eigene graue Schrift ohne Theme-Verlauf oder Theme-Textglow.
Containerbreite, Sortierung und Clipping berücksichtigen beide Textteile.
Das Backtrack-Label wird höchstens alle 50 ms abgefragt.

Für neue Einstellungen ist die Anzeige eingeschaltet. Die bestehende gespeicherte
ID `show_module_info` bleibt gültig. Ein älteres Profil, in dem diese Option auf
`false` steht, behält diese Entscheidung; die Anzeige lässt sich über den neuen
Namen **Module Prefixes** einschalten.

### Library und Configs

Die Library führt zu **Configs**, **Themes**, **Client settings** und **Accounts**.
Module werden über die ClickGUI bearbeitet.

Configs liegen als Karten in einem an die Fensterbreite angepassten Raster. Suche,
Namensfeld und die untere Aktionsleiste haben getrennte Bereiche. Die Leiste
enthält **Save**, **Load**, **Rename**, **Delete** und **Refresh**. Überschreiben,
Umbenennen und Löschen behalten ihre Bestätigungsschritte. Ein begonnenes Rename
bleibt bei einer Größenänderung des Fensters erhalten.

### Client Settings und Theme Studio

Die Client Settings verwenden einen Kategorienbereich und klar abgegrenzte
Wertezeilen. Bei kurzen Fenstern steht der Platz vor allem den Einstellungen zur
Verfügung. Ein geöffnetes Dropdown bleibt beim Resize innerhalb des Fensters,
mit sichtbarer aktiver Option. Die sichtbaren Endpunkte eines Sliders setzen
exakt dessen Minimum und Maximum.

Das Theme Studio gliedert die Bearbeitung in **Palette**, **Motion** und
**Edges & light**. F6 wechselt den Bereich; Tab führt vom aktiven Bereich in
dessen Felder. Die Vorschau verwendet die tatsächlichen RGBA-Werte des Entwurfs.
Speichern bestätigt den Entwurf, Abbrechen verwirft ihn. Ein Bereichswechsel
beendet eine zuvor vorgemerkte Löschbestätigung.

Library, Configs, Client Settings und Theme Studio zeichnen über einer geöffneten
Welt ausschließlich ihre Fenster und deren gewählte Materialien. Zusätzliche
bildschirmfüllende Abdunklungen entfallen.

## Umsetzung und Prüfung

`SubcategoryValue` ist ein Präsentationswert. Die Gruppen referenzieren die
ursprünglichen Modulwerte; deren IDs, Registrierung und Config-Serialisierung
bleiben erhalten. Ausblendung und Animation begrenzen sowohl die gezeichnete
Fläche als auch aktive Eingabebereiche. Reduzierte und ausgeschaltete Animationen
werden berücksichtigt.

Die gemeinsame `LegacyUiStyle` zeichnet ein Icon in einem einzigen Geometrie-Batch.
Die Config-Library hält gefilterte Listen vor und erzeugt Bewegungszustände nur für
angezeigte Karten. Arraylist-Metriken werden bei unverändertem Text wiederverwendet;
ein leerer Prefix erzeugt keinen zusätzlichen Textdurchlauf.

Der Workflow **Legacy UI native render** führt mit Temurin 8u482, Xvfb und Mesa vier
getrennte JVMs aus. Er verwendet die Produktions-Renderer und verlangt echte
Pbuffer-, Shader-, Framebuffer- und Pixel-Readback-Unterstützung. Die Bilder und
Logs sind PR-Artefakte; erzeugte Test-Configs und Fixture-Homes werden nicht
hochgeladen.

Abgedeckt werden ClickGUI- und Library-Eingaben, Gruppen-Clipping, Config-Aktionen,
Theme-Entwürfe, Client-Settings, Größenwechsel, Ressourcenfreigabe und die neutrale
Prefix-Farbe unter mehreren Paletten. Eine zusätzliche Pixelprüfung wechselt
wiederholt zwischen den tatsächlichen Menüs, einschließlich unterbrochener
Übergänge, und prüft den Hintergrund außerhalb der Fenster.

Die Render-Fixtures verwenden synthetische Eingaben und Hintergründe. Vollständiger
Clientstart, Welt-Rendering, native Vollbild-/VSync-Ereignisse und das Verhalten auf
einzelnen Grafiktreibern sind separate Laufzeitprüfungen. Die vollständige Java-8-
Regression läuft zusätzlich auf Windows.
