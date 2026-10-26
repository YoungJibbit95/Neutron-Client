# Neutronenstern-Menü — Nutzerwunsch vom 26.10.2026

Die beigefügte `neutron-star-reference.frag` hält die vom Nutzer bereitgestellte Hintergrundbasis einschließlich des vorhandenen Kommentars `DS: hornidev` fest. Sie ist eine Designreferenz und wird nicht als produktiver Shader geladen.

## Auftrag für die laufende Visual-Arbeit

- Weltraum-/Neutronenstern-Komposition statt des bisherigen generischen Hintergrunds: strahlender kompakter Stern, pulsierende Corona, erkennbare rotierende Pulsar-Strahlen und ein räumlich wirkendes Sternenfeld/Nebel. Die prozeduralen FBM-Strukturen der Vorlage dienen als Materialbasis. Menütext und Controls behalten eine ruhige, lesbare Umgebung.
- Theme-Farben und globale Effekt-/Animationsvorgaben übernehmen. Hintergrundintensität, Bewegung und Qualität anpassbar; reduzierte Bewegung und Animation aus respektieren. Keine harten Bildschirmblitze.
- Hauptmenübuttons bekommen eine gedämpfte elastische Hover-/Fokus-Reaktion, einen eigenen Pressed-Zustand und weiches Rückfedern. Animation zeitbasiert und bei Richtungswechsel ohne Reset; Hit-Test, Clipping und Darstellung müssen zusammenpassen. Schnelle Mauswechsel, Tastaturfokus, kleine Auflösungen und verschiedene FPS testen.
- Shader auf die tatsächlich unterstützte Desktop-GLSL-Version des Clients portieren. Die ES-Extension ist keine Desktop-Abhängigkeit. Ungenutzte Werte entfernen, den doppelt geschriebenen Output auflösen und Farb-/Alpha-Ausgabe bewusst begrenzen. Die Vorlage nicht unverändert als fertig implementierten Neutronenstern ausgeben.
- Shader/Framebuffer wiederverwenden. Qualitätseinstellungen müssen einen messbaren Kostenunterschied haben; Vorschauen bei mehreren Zeitpunkten und Seitenverhältnissen liefern. Keinen neuen parallelen Theme-/Animations-/Effekt-Stack aufbauen.

## Unabhängiger Absturz beim Verlassen der Welt

Der lokale Report `crash-2026-10-26_14.35.27-client.txt` endet in `NetworkManager.sendTransport`: `Connection send queue exceeded limit`. Der integrierte Server sendet beim Spieler-/Chunk-Abbau noch Pakete nach dem Verbindungsende; diese landen fälschlich in der Vorverbindungsqueue. Das passiert vor dem Anzeigen des Main-Menüs und wird separat im Netzwerk-Lifecycle korrigiert. Nicht durch einen Catch um `GuiIngameMenu.actionPerformed` oder durch Abschalten von Effekten verdecken.
