# Neutron: Menü, Library und kosmetische Render-Module

Diese Übersicht beschreibt die Erweiterung des vorhandenen Java-8/OpenGL-Clients.
Die aktiven `ClientSettings`, Themes und gemeinsamen Liquid-Glass-Effekte bleiben
maßgeblich. Die Herkunft der visuellen Inspiration steht in
[RENACITY-INSPIRATION.md](third-party/RENACITY-INSPIRATION.md); die bestehende
Font-Atlas-Dokumentation steht in [FONT-ATLAS.md](FONT-ATLAS.md).

## Hauptmenü

Das zentrierte Menü zeigt Singleplayer, Multiplayer, Account Manager, Settings,
Library und Exit als vertikale Aktionsliste vor dem vorhandenen Neutron-Sternshader.
Motion, Blur und Glow darunter schalten die entsprechenden aktiven
ClientSettings um. Die Library ist auch aus Panel-, Dropdown- und iOS-ClickGUI
erreichbar.

- Tab beziehungsweise Umschalt+Tab und die Pfeiltasten wechseln die Auswahl.
- Enter, Nummernblock-Enter oder Leertaste führen die ausgewählte Aktion aus.
- Escape setzt die Auswahl auf Singleplayer zurück.
- Ein Mausklick aktiviert eine Aktion erst beim Loslassen innerhalb ihrer Kontur.

Die äußere Trefferfläche bleibt während der Hoverbewegung stabil; Beschriftung
und innere Darstellung bewegen sich darin. Tastaturfokus erhält sofort sichtbare
Klammern, auch wenn globale Border- und Glow-Effekte abgeschaltet sind.

## Library

`SideGuiScreen` zeigt links alphabetisch sortierte Suchergebnisse und rechts die
Einstellungen des gewählten Moduls. Die Suche ignoriert Groß-/Kleinschreibung und
prüft Modulname, Kategorie, Beschreibung und Einstellungsnamen. Sie filtert nach
der eingegebenen zusammenhängenden Zeichenfolge. Configs, Themes, Client und
Accounts öffnen die vorhandenen Verwaltungsoberflächen.

| Eingabe | Wirkung |
| --- | --- |
| Strg+F | Suchfeld fokussieren |
| Strg+A im Suchfeld | Suchtext zur Ersetzung vormerken |
| Enter oder Pfeil ab im Suchfeld | Zur Ergebnisliste wechseln |
| Pfeil auf/ab in der Ergebnisliste | Voriges/nächstes Modul auswählen |
| Enter oder Pfeil rechts in der Ergebnisliste | Einstellungsbereich fokussieren |
| Leertaste in der Ergebnisliste | Gewähltes Modul umschalten |
| K in der Ergebnisliste oder am Modulkopf | Tastenbelegung aufnehmen |
| Mittlere Maustaste auf Ergebnis oder Modulkopf | Tastenbelegung aufnehmen |
| Escape während der Tastenaufnahme | Aufnahme abbrechen |
| Entf oder Rücktaste während der Tastenaufnahme | Modulbelegung entfernen |
| Tab / Umschalt+Tab | Navigation, Suche, Ergebnisse und sichtbare Einstellungen durchlaufen |
| Mausrad | Den Bereich unter dem Zeiger scrollen: Ergebnisse oder Einstellungen |

Escape leert zunächst eine nichtleere fokussierte Suche. Bei leerer Suche führt
Escape zurück; außerhalb der Suche schließt es zunächst eine offene Auswahlliste
oder kehrt zur vorherigen Oberfläche zurück. Die Library pausiert die Welt nicht.
Eine dauerhafte helle oder dunkle Unterlegung schützt Ergebnistext vor dem
bewegten Hintergrund. Die Tastaturauswahl in der Liste besitzt eigene Klammern.

## Gemeinsame Darstellung und Lebensdauer

Panel-, Dropdown- und iOS-Komponenten verwenden die gemeinsamen Themefarben und
`GuiStyle`-Rückmeldungen. Hover verändert vorhandene Schrift und Konturen;
Tastaturfokus und gedrückter Zustand erhalten Formsignale. Die iOS-Oberfläche
verwendet außerhalb einer Welt ebenfalls den Neutron-Hintergrund. Ihr Inhaltsbereich
scrollt geglättet; die frühere vernetzte Partikelschicht entfällt. Eine dauerhafte
Kontrastunterlegung schützt die Kategorienamen; die Auswahlmarkierung folgt der
aktuellen Kategorie und Fenstergeometrie.

`GuiMotion` steuert das Öffnen und Schließen der Library. Beim Schließen werden
neue Eingaben gesperrt; laufende Texteingaben und Dragvorgänge werden
beendet. `onGuiClosed` gibt die Library-Oberfläche frei und stellt den
vorherigen Zustand der Tastaturwiederholung wieder her.

`GuiTransitions` kopiert bei relevanten Bildschirmwechseln einmal das aktuelle
Framebufferbild in eine wiederverwendete Textur und blendet es über 180 ms aus.
Ohne aktive Bewegung gibt es keine Überblendung; Größenänderungen beenden eine
laufende Überblendung. Der normale Bildaufbau benötigt keine weitere Kopie.
Die Textur wird beim Client-Shutdown freigegeben.

## Kosmetische Render-Module

Die folgenden Module stehen in der Kategorie Render zur Verfügung:

| Modul | Darstellung und Einstellungen |
| --- | --- |
| Breadcrumbs | Zeitlich ausblendende Bewegungsspur; Duration und Width |
| ChinaHat | Thematisierter Hut über dem eigenen sichtbaren Spieler in der Drittpersonansicht; Radius und Opacity |
| Crosshair | Eigenes Fadenkreuz in der Erstpersonansicht; Length, Gap, Thickness, optionaler Center Dot und Dynamic-Spreizung beim Sprinten |
| JumpCircle | Ring beim Übergang vom Boden in eine Aufwärtsbewegung; Radius und Duration |
| Keystrokes | Tatsächliche Bewegungs-/Sprungbelegungen und optionale Maustasten; Position X/Y, im geöffneten Chat verschiebbar |
| NoHurtCam | Unterdrückt den Aufruf des Kameraeffekts bei Schaden |

Breadcrumbs und JumpCircle verwerfen ihre gespeicherten Punkte bei Weltwechsel
und beim Abschalten. Keystrokes gibt beim Abschalten Oberfläche und Dragbelegung
frei; Crosshair setzt seine Bewegungswerte zurück. Die HUD-Sichtbarkeit wird von
Crosshair und Keystrokes berücksichtigt.

GlowESP legt Shader erst bei Bedarf an und bereinigt entfernte Objekte aus seiner
Farbtabelle. Kawase verwendet die Iterations-Einstellung mit einem einstellbaren
Bereich von 2 bis 6; der Gauß-Pfad bleibt verfügbar. Beim Abschalten werden zugehörige
Framebuffer, Shader und Objektreferenzen freigegeben.

## Abnahme

Diese Beschreibung dokumentiert den Quellstand und ersetzt keine Spielabnahme.
Live-Client, Vollbildwechsel und die manuelle In-Game-Abnahme bleiben offen.
Die endgültigen Testergebnisse und Nachweise gehören zum jeweiligen PR.
