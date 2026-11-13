# BedBreaker-Zeitanzeige und BedESP

Diese Erweiterung ergänzt den [Demise-BedBreaker](BEDBREAKER-DEMISE-PARITY.md)
um eine lesende Zeitschätzung und ein eigenständiges Render-Modul. Auswahl,
Abbau, Silent Rotations, MoveFix und der geglättete Rotationsrücklauf behalten
ihren bestehenden Ausführungspfad.

## BedBreaker: Zeit bis zum Bett

Die Glasfläche besitzt einen abgerundeten Fortschrittsbalken mit dem aktuellen
Theme-Verlauf, dezentem Glow, einer Lichtkante und Blur des tatsächlichen
Hintergrunds. Die bestehenden globalen Effekt-, Rundungs-, Farb-, Schrift- und
Bewegungseinstellungen bleiben wirksam. Bei deaktivierten Shader-Effekten bleibt
eine lesbare geometrische Darstellung erhalten.

Die Anzeige hat zwei klar bezeichnete Phasen:

| Phase | Zeit und Fortschritt |
| --- | --- |
| `TO BED` | Geschätzte verbleibende Zeit für die Schutzblöcke eines bestätigten Pfads zum Bett. Der Balken zählt bestätigte entfernte Blockschritte und den aktuellen Block anteilig. |
| `BED` | Das Bett selbst wird abgebaut. Zeit und Balken beziehen sich auf seinen tatsächlich beobachteten nativen Abbaufortschritt. |

`~` kennzeichnet eine Schätzung bei 20 Spielticks pro Sekunde. Verwendet werden
die aktuelle relative Blockhärte des Spielers, sein Werkzeug und Zustand,
Controller-Fortschritt, Anlaufticks und verbleibender Block-Hit-Delay. Entfernte
Pfadblöcke zählen erst nach einem bestätigten Weltzustand zum erledigten Anteil.
Ein Werkzeugwechsel, eine neue Mine oder ein geänderter Pfad erneuert die Basis.
Eine schwankende Server-Tickrate oder zusätzliche serverseitige Regeln sind
keine gemessenen Eingangsgrößen.
Die Zeitangabe berücksichtigt unterschiedliche Blockhärten; der Zugangs-Balken
zeigt den Anteil erledigter Blockschritte und ist nicht nach Zeit gewichtet.

Im Legit-Modus muss der gerade abgebaute Block nachweislich das erste Hindernis
eines beobachteten Strahls zum Bett sein. Weitere Blockschritte sind eine
bedingte Schätzung, falls dieser Pfad weiterverfolgt wird. Die Schätzung steuert
keinen Abbau und sucht keinen neuen Aktionspfad. Im direkten Demise-Modus wird
die tatsächliche Bett-Mine gemessen; Schutzblöcke werden dort nicht als fiktive
Zusatzarbeit angerechnet.

Bei Ausrichtung, Pause, fehlender Reichweite, unbekannten Chunks, unzerstörbaren
Blöcken oder einem unbestätigten Pfad wird ein Status mit `--` angezeigt. Es gibt
keinen unabhängig weiterlaufenden Countdown. Nach Verlust der Aktionsgrundlage
wird eine alte Zeitangabe nicht weitergezeichnet. Verzögerte Renderaufträge
prüfen Welt, Spieler, Verbindungsepoche, Frame und Aktionsrevision erneut.

### Einstellungen

Die vier neuen Werte stehen in der Gruppe **Indicator** nach den ursprünglichen
19 BedBreaker-/Host-Werten. Deren Registrierungsreihenfolge bleibt erhalten.

| Einstellung | Standard | Funktion |
| --- | --- | --- |
| Break Indicator | An | Anzeige aktivieren. |
| Indicator X | 50 % | Horizontale Position innerhalb der verfügbaren Bildschirmfläche. |
| Indicator Y | 68 % | Vertikale Position innerhalb der verfügbaren Bildschirmfläche. |
| Indicator Scale | 1 | Größe von 0.65 bis 1.5; kleine Viewports begrenzen die tatsächliche Größe. |

Bei geöffnetem Chat erscheint eine gekennzeichnete Vorschau, die mit der Maus
verschoben werden kann. HUD-Sperre, Raster und Bildschirmgrenzen werden beachtet.
Ein bereits im Spiel gehaltener Mausklick startet keinen neuen Drag. Einzelne
kosmetische Änderungen und reine Anzeige-Änderungsbatches beenden keine Mine
und setzen keine Rotation zurück. Ein gemischter Batch mit Aktionswerten behält
die bestehende BedBreaker-Invalidierung.

## Render → BedESP

BedESP erfasst geladene Bettblöcke, verbindet passende Kopf- und Fußhälften und
zeichnet eine gemeinsame Kontur. Die Kontur ist durch die Verteidigung sichtbar.
Fehlende oder entfernte Hälften werden nicht als weiterhin vorhandene Geometrie
ausgegeben. Das kleine Glaswidget wird über das tatsächliche Bett in die aktive
Kameraansicht projiziert, einschließlich der jeweiligen Anaglyph-/Shader-Kamera.
Hinter der Kamera, außerhalb des Bildschirms oder bei nicht auflösbarer
Überlappung wird das Widget ausgelassen.

| Einstellung | Standard | Auswahl / Bereich |
| --- | --- | --- |
| ESP Mode | Glow | Glow, Flame, Lines, Outline. Glow nutzt den bestehenden Neon-Kontur-Renderer. |
| Range | 48 | 8–128 Blöcke. |
| Color Source | Theme | Theme oder Team hint. Unbekannte Teams behalten die Theme-Farben. |
| Glow Strength | 0.8 | 0–2; globales Glow-Aus hat Vorrang. |
| Glow Radius | 5 | 1–16. |
| Box Opacity | 0.13 | 0–0.7. |
| Line Width | 1.25 | 0.5–4. |
| Widgets | An | Kompakte Bettinformationen einblenden. |
| Max Widgets | 8 | 1–16; nähere Betten reservieren zuerst ihren Platz. |
| Widget Scale | 1 | 0.65–1.5. |
| Widget Height | 0.85 | 0.3–2 Blöcke über der Bettoberkante. |
| Show Layers | An | Materialzusammenfassung der ersten beiden Schichten. |
| Show Team | An | Team-Farbhinweis beziehungsweise `Unknown team`. |
| Show Exposure | An | Freie Bettseiten, vollständig bedeckt oder unvollständig beobachtet. |

### Bedeutung der Bettinformationen

Schicht 1 und 2 sind die Blockflächen in Manhattan-Abstand 1 beziehungsweise 2
um beide Betthälften. Der Untergrund unter der Bettbasis gehört nicht zu diesen
Verteidigungsschichten. Die Anzeige nennt die häufigsten Materialien mit ihrer
Anzahl; `+N` steht für weitere Materialarten, `?` für noch unbekannte Zellen.
Die freie Unterseite zählt jedoch bei der Frage nach offenen Bettseiten mit.
Ein offener Rand bedeutet eine unmittelbar freie Bettseite; er ist keine
Garantie, dass von der aktuellen Spielerposition ein freier Strahl durch jede
weiter außen liegende Verteidigung existiert.

Minecraft 1.8.9 speichert an einem normalen Bett keine allgemeine BedWars-
Teamkennung. Deshalb wird Besitz nicht als gesicherte Tatsache erfunden.
Ein Farbhinweis entsteht nur bei einer eindeutigen, wiederholten Wollfarbe in
der beobachteten Verteidigung und wird etwa als `Blue?` angezeigt. Fehlende oder
widersprüchliche beobachtete Farben ergeben `Unknown team`. Ein Farbhinweis
kann auch bei noch unvollständig beobachteten Schichten unsicher bleiben. Serverabhängige
Team-Metadaten werden nicht vorgetäuscht.

### Lebensdauer und Arbeitsbudget

`BedWorldTracker` liest ausschließlich vorhandene Client-Chunks. Auf diesem
Minecraft-Stand ist `ChunkProviderClient.chunkExists` kein Beweis für einen
geladenen Chunk; geprüft werden stattdessen die tatsächliche Chunk-Instanz,
Koordinaten und `isLoaded`. Ein fehlender Chunk wird niemals als Luft behandelt.

Die Suche ist auf 32.768 Zellen und 64 Abschnittsbesuche pro Spielertick
begrenzt, außerdem auf 16 Neuentdeckungen und 8 Verteidigungsaktualisierungen.
Höchstens 64 Bettpaare werden vorgehalten. Renderframes lesen diese begrenzten
Snapshots und führen keine vollständige Weltsuche aus. Größere Reichweite kann
deshalb die Zeit bis zur Entdeckung eines entfernten Betts erhöhen.

Unload/Load, Verbindungswechsel, Korrekturepoche und Disable verwerfen den alten
Beobachtungsstand. Eigene Masken und Glasflächen werden wiederverwendet und
beim Beenden freigegeben. Die Renderer restaurieren den tatsächlichen
Framebuffer, Viewport, Shaderzustand, Matrizen und Clipzustand des Aufrufers.

## Überprüfung

Die begleitenden Tests decken Bettpaare über Chunkgrenzen, unbekannte Chunks,
Schichtmaterialien, freie Seiten, Suchbudgets, Zustandswechsel, native
Abbaufortschritte, Werkzeug-/Revisionswechsel sowie kosmetische Einstellungen
ohne zusätzliche Aktionen ab. Layouttests prüfen Projektion, Near-Plane,
Bildschirmgrenzen und überlappende Labels.

`BedVisualsNativeCheck` rendert mit dem Produktionscode in einem echten
Java-8-Pbuffer/FBO: vier ESP-Modi, Glas-/Gradientenpixel, verschiedene
Fortschrittsstände, unbekannte Zeit, mehrere Widgetzustände, Clip- und
GL-Restaurierung, wiederverwendete Ressourcen und deren Freigabe. Der Check
läuft als zusätzliche Suite im bestehenden Legacy-UI-Workflow. Das ersetzt
keinen Multiplayer-Spieltest oder einen Test mit einem externen Shaderpack.
