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

## Client Settings und Typografie

Settings öffnet die vorhandenen ClientSettings und HUD-Values direkt. Änderungen
wirken sofort, nutzen deren bestehende Callbacks und Konfigurationsspeicherung
und werden in einer Text-/Materialvorschau sichtbar. Es gibt keinen zweiten
Einstellungsspeicher. Ab einer Panelgröße von 560 × 330 GUI-Einheiten erscheint
links eine Kategorienleiste; bei kleineren Größen bleiben die Abschnittspfeile
und F6 beziehungsweise Umschalt+F6 erreichbar. Der Inhalt scrollt bei Bedarf.

Schalter zeigen On/Off und einen beweglichen Griff, Zahlen einen Schieberegler
mit Wertanzeige, Auswahlfelder eine aufklappbare Liste. Ruhige dauerhafte
Füllungen sichern den Kontrast. Hover verändert Schrift und Bedienelemente,
ohne ein Rechteck hinter der gesamten Zeile einzublenden. Tastaturfokus erhält
eigene Formmarkierungen; die aktive Kategorie und gewählte Popup-Option besitzen
eine schmale Akzentmarkierung.

| Eingabe | Wirkung |
| --- | --- |
| Kategorie anklicken / Abschnittspfeile | Abschnitt auswählen |
| F6 / Umschalt+F6 | Nächster/voriger Abschnitt, auch ohne Kategorienleiste |
| Tab / Umschalt+Tab oder Pfeil ab/auf | Sichtbare Einstellungen und Fußaktionen durchlaufen |
| Enter / Nummernblock-Enter / Leertaste | Schalter umlegen, Auswahlliste öffnen, exakte Zahleneingabe oder fokussierte Aktion ausführen |
| Pfeil links/rechts auf einer Einstellung | Zahl schrittweise ändern, Auswahl wechseln oder Schalter umlegen |
| Linke Maustaste im Zahlenregler | Wert ziehen |
| Rechte Maustaste auf einer Zahlenzeile | Exakten Wert eingeben |
| Enter in der Zahleneingabe / Escape | Gültigen Wert übernehmen / Eingabe abbrechen |
| Strg+A in der Zahleneingabe | Vorhandenen Wert zur Ersetzung vormerken |
| Pfeil auf/ab oder Pos1/Ende im Popup | Option ansteuern; Enter oder Leertaste übernimmt |
| Escape / Tab im Popup | Liste ohne Übernahme schließen |
| Mausrad | Einstellungen beziehungsweise offene Optionsliste scrollen |
| Bild auf/ab; Pos1/Ende außerhalb einer Eingabe | Einstellungen seitenweise scrollen; erste/letzte Einstellung fokussieren |
| Strg+R außerhalb einer Eingabe | ClientSettings und HUD-Values auf Standardwerte zurücksetzen |
| Escape ohne offene Eingabe oder Liste / Done | Zur vorherigen Oberfläche zurückkehren |

Exakte Zahlen müssen endlich sein und im Bereich der Einstellung liegen;
ungültige Eingaben bleiben mit einem Hinweis zur Korrektur offen. `Reset group`
setzt die aktuelle Gruppe zurück. `Preview toast` zeigt eine synthetische
Benachrichtigung; in `HUD behavior` ersetzt `Reset positions` diese Aktion.
`Game options` öffnet die Minecraft-Einstellungen. Beim Schließen werden
Dragvorgänge, Texteingaben und Popups beendet, Renderflächen freigegeben und der
vorherige Zustand der Tastaturwiederholung wiederhergestellt. Das Menü pausiert
die Welt nicht.

Die Gruppe `Typography` enthält folgende Einstellungen:

| Einstellung | Verhalten |
| --- | --- |
| UI Font | Original, DM Sans, Inter, Poppins, Product Sans, SF Pro oder Tenorite; Original erhält die bisher pro Oberfläche gewählte Schrift |
| Text Glow | Module settings erhält vorhandene Modulentscheidungen; Off unterdrückt Textglow; On verwendet die gemeinsamen Textglow-Werte |
| Text Glow Strength | 0 bis 1, Standard 0,45; zusätzlich durch Global Glow Intensity skaliert |
| Text Glow Radius | 0,5 bis 3 GUI-Einheiten, Standard 1,5 |
| Text Glow Color | Text oder Theme, Standard Text |

Global Glow bleibt der übergeordnete Schalter: Ist er aus, ist auch Textglow aus;
die Typografiegruppe zeigt dazu einen Hinweis. Schriftwechsel erhalten Größe
und verfügbare Stärke; Tenorite verwendet für Medium seine Regular-Schrift.
Icon-Schriften bleiben unverändert. Explizite ArrayList-Schriften bleiben lokal;
die neue Option `Follow global` übernimmt UI Font und erhält Font Strength.
Minecraft-Schrift bleibt ein eigener Renderer. Details zum Atlas, den beiden
Text-Effektpfaden und Shadergrenzen stehen in [FONT-ATLAS.md](FONT-ATLAS.md).

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
`SettingsInteractionTest` prüft Kategorien-/Popupnavigation, kompakte Bedienung,
Zahleneingaben und Rückkehr zum Elternmenü. `TypographySettingsTest` prüft
Schriftauflösung und Konfigurationsmigration; `TypographyRenderCheck` und
`ClientSettingsMenuCheck` ergänzen native Pbuffer-Rendernachweise. Die lokalen
Aufnahmen und Logs dieser Typografie-/Settings-Verfeinerung liegen unter
`target/typography-ui-20261102`. Die visuelle Prüfung der bereitgestellten
nativen Nachweise ersetzt keine Prüfung im laufenden Spiel.

Live-Client, Vollbildwechsel, Shaderpacks und die manuelle In-Game-Abnahme bleiben offen.
Die endgültigen Testergebnisse und Nachweise gehören zum jeweiligen PR.
