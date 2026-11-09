# UI-Prototyp — Implementierungsstand Abschnitt 3

Historischer Abschnitt-3-Nachweis vom 28. Oktober 2026. Aktuelle echte Integration: [INTEGRATION.md](INTEGRATION.md). Die nachfolgenden Mock-/Shell-Beschreibungen beziehen sich auf den damaligen Review-Stand, nicht auf den jetzigen Standardbetrieb.

Stand: 28. Oktober 2026. Quellen und automatische Prüfungen sind vorhanden; eine visuelle oder interaktive Abnahme fehlt aufgrund der fortgeltenden Escape-Sperre. Kein Browser, Desktop, Electron-Fenster oder Minecraft-Client wurde in Abschnitt 3 gestartet oder bedient.

## Implementiert

| Oberfläche | Funktion im Prototyp |
| --- | --- |
| Client starten | Version/Commit/Datum, Bereitschaft, Launch-/Update-Aktionen, Offline-/Fehler-/Crash-Hinweise und Konsole. |
| Startoptionen | RAM-Slider/Zahlenfeld, Beispielordner-Auswahl, verwaltetes/eigenes Java, Fenstergröße/Vollbild und erweiterte JVM-Argumente mit Eingabevalidierung. |
| Launcher-Einstellungen | Vier Theme-Vorschauen, DE/EN, UI-Skalierung 90/100/110/125%, Effektstärke und reduzierte Bewegung inklusive Systempräferenz. |
| Updates | Beispiel-GitHub-Stand, Build/Build-and-play, Phasen, installierte Builds, Auswahl und geschützte Entfernung mit Bestätigung. |

Die ausklappbare Leiste „Beispielzustände“ bietet 13 Zustände: bereit, Erststart, kein Build, offline, Update verfügbar, Vorbereiten, Herunterladen, Bauen, Prüfen, abgebrochen, Fehler, laufender Client und Crash. Fortschritt ist nur beim Beispiel-Download beziffert; Build/Prüfung sind unbestimmt. Zustandsübergänge erfolgen ausdrücklich über Beispielaktionen, nicht über versteckte Timer oder tatsächliche Hintergrundprozesse.

„Bauen und spielen“ aktiviert erst nach der Prüfphase und unter gültigen Startoptionen. Fehler/Abbruch erhalten den installierten Build. Ausgewählte/laufende Builds können nicht entfernt oder während eines Laufs gewechselt werden. Diese Modellinvarianten sind Unit-geprüft; sie ersetzen keinen produktiven Prozess-/Rollback-Test.

Auch beim ersten Build ohne installierte Version zeigt die Startansicht Fehler/Abbruch als Simulation mit passendem Grund und Retry. Sie behauptet dabei keine spielbare vorherige Version. Die Reducer-Ketten mit beiden Sprachen sind als Server-HTML geprüft.

Erststart bietet Speicherort und eine Downloadübersicht mit gekennzeichneten Beispielgrößen. Die Ordnerauswahl setzt einen Beispielpfad, sie öffnet keinen nativen Dateidialog. Eingaben und Einstellungen bleiben im Arbeitsspeicher, ohne `localStorage` oder Dateischreibzugriff. RAM-Grenzen beziehen sich auf das ausgewiesene Beispielgerät; reale Hardware-/Reserveprüfung folgt bei der Integration.

## Konsole und Dialoge

Client-/Build-Streams, Zeitstempel, Suche, Schweregradfilter, Copy-/Text-Export, Auto-Scroll, Pause beim Hochscrollen und neue-Zeilen-Zähler sind als Frontend-Code vorhanden. Die Beispielpuffer bleiben auf 2.000 Zeilen begrenzt; 26-px-Zeilen werden mit Overscan virtualisiert. Ein 5.000-Zeilen-Unit-Input überschreitet den Puffer nicht; die serverseitige HTML-Ausgabe enthält weniger als 20 sichtbare Logzeilen. Das ist kein gemessener Fenster-/Scroll-Lasttest.

Die Konsole lässt sich per Pointer oder Tastatur in der Höhe ändern und einklappen. Größenänderung per Drag/Taste ist unmittelbar; das Auf-/Einklappen verwendet einen kurzen Übergang. Logzeilen selbst animieren nicht. System-/eigene Reduced-Motion-Optionen und Tastatureingaben schalten Bewegung ab.

Nach Review-Korrektur hält pausiertes Lesen eine stabile Log-ID plus Teilzeilenoffset. Verdrängt der Puffer diese ID, beginnt die Anzeige kontrolliert bei der ältesten verfügbaren Zeile und meldet die verworfene ältere Ausgabe. Die Komponente bleibt beim Ansichtswechsel eingehängt und verborgen; Quelle/Filter/Suche, Pause und Anker bleiben im Arbeitsspeicher. Die ID-/Offset-Rechnung unter Pufferrotation ist geprüft; echte Navigation und DOM-Scrollereignisse bleiben offen.

Die Maximalhöhe folgt dem gemessenen verfügbaren Hauptbereich statt pauschal 460 px. Feste Elemente/Abstände/Konsolenkopf werden abgezogen, 160 px × UI-Skalierung bleiben für die scrollbare Hauptansicht reserviert. ResizeObserver sowie Resize-/Sprach-/Skalierungswechsel lösen neue Berechnung aus. Unter 122 px Inhaltsbudget bleibt nur der Konsolenkopf; bei wieder genügend Platz wird der gespeicherte Öffnungswunsch verwendet. Ein insgesamt scrollbarer Hauptbereich dient als Rückfall bei extremem Platzmangel. Die pure Rechnung ist getestet, die DOM-Messung und Erreichbarkeit nicht.

Dialoge verwenden natives HTML-`dialog` mit Fokus-Rückgabe im Code. Es gibt Build-Entfernung, die drei Schließoptionen bei laufendem Beispiel-Client und eine redigierte Problembericht-/Issue-Vorschau. Die Schließsimulation bedient keine nativen Fenster; die Issue-Vorschau öffnet keine Website und veröffentlicht nichts. Erkennbare Tokens und persönliche Pfadsegmente werden in den Beispieltexten redigiert.

Nur das lokale Electron-Dokument darf über eine eng begrenzte Browser-Berechtigung bereinigten Clipboard-Text schreiben. Kamera, Clipboard-Read und fremde Dokumente bleiben blockiert. Dieser Vertrag ist als pure Funktion getestet; echter Clipboard-/Export-/Dialog-Fokus ist nicht interaktiv geprüft.

## Gestaltungsquelle im Code

Verbindlicher Brief: [DESIGN.md](../DESIGN.md). Implementierte, noch visuell ungeprüfte Tokens: `src/tokens.ts`; Layout und Motion: `src/styles.css`; Oberflächen: `src/App.tsx`. Die Tokenwerte werden zentral auf semantische CSS-Variablen abgebildet.

| Theme | Hintergrund | Primärverlauf | Text auf Primär |
| --- | --- | --- | --- |
| Sunset | `#171719` | `#ffb183` → `#ff85b5` | `#28161e` |
| Ocean | `#14191d` | `#8cddfa` → `#9bbcff` | `#12232d` |
| Violet | `#19171e` | `#c8aaff` → `#f0a6d6` | `#24172f` |
| Graphite | `#18181a` | `#e2d9cd` → `#c3c2ce` | `#242326` |

Geist Variable und Geist Mono Variable werden als lokale Latin-WOFF2-Dateien gebündelt, die die DE-/EN-Zeichen abdecken; keine Remote-Font-Anfrage. SIL-OFL-Texte sowie React-/ReactDOM-/Lucide-Lizenzen liegen unter `public/licenses/` und im Build. Eine visuelle Font-/Glyphenprüfung ist noch offen.

Die App hält die Sidebar stabil und verwendet eine skalierbare Hauptansicht. Panel-Radius 16 px, Controls 10 px. Buttons 120–140 ms, View-Wechsel 180 ms mit starkem Ease-out, keine Verzögerung vor einer Zustandsänderung. Theme- und Statusfarben tragen Bedeutung, dekorativer Glow/Blur bleibt an Hero/Launch bzw. Dialog-Hintergrund gebunden. Das native Fenster wird nicht nachgezeichnet.

Die Kontrastrechnung prüft normale Text-/Sekundärtextfarben gegen die vier festen Hintergrundschichten sowie Text gegen beide Primär-Endpunkte mit mindestens 4,5:1. Sie prüft nicht den tatsächlich komponierten Browserzustand, gerenderte Transparenzen, Fokus-Clipping oder Hardware/DPI.

## Angewandte Skills und Grenzen

Impeccable 4.8.0, Engine 0.1.18: Binary anhand des im gepinnten Skill enthaltenen SHA-256 geprüft; einmaliger `context`-Aufruf im Launcher-Unterprojekt mit Exit 0. Engine, Cache und Temp liegen auf F:. Die Python-Hülle konnte anschließend einen Pfeil nicht im cp1252-Terminal ausgeben; die vollständige UTF-8-Kontextdatei und der tatsächliche Engine-Exitcode waren bereits gespeichert. Der Kontextlauf wurde deshalb nicht wiederholt und nicht als fehlgeschlagen ausgegeben.

Gelesen/anwendbar: Impeccable `new-work`, `operate`, `craft-floor`, Dokumentationsregeln; Emil Design Engineering/Motion; UI/UX Pro Max. Die ausdrückliche, bereits bestätigte Vier-View-/Sunset-Gestaltung ersetzt keine neue offene Richtungswahl. Es wurde kein Browser-Entscheidungsdialog geöffnet und keine neue Stilrunde verlangt. Die relevanten lokalen Suchen betreffen React-Virtualisierung und Keyboard-/Fokus-Verhalten. Ein generischer Designsystem-Vorschlag wurde bereits in Abschnitt 1 als unpassend verworfen; er wurde nicht als Gestaltungsautorität übernommen.

Der Design-Skill verlangt normalerweise einen Blick auf das gerenderte Ergebnis. Dieser Schritt und ein vollständiger Finish-/Design-Review sind wegen der Nutzer-/Review-Sperre ausgesetzt. Es gibt deshalb keine visuelle Skill-Freigabe, keinen als abgenommen kanonisierten neuen Design-Sidecar und keine Launcher-Screenshots. Der festgelegte Brief bleibt erhalten, die Codewerte sind bis zum echten Review vorläufig.

## Automatisch geprüft / noch offen

Build/strikte Typechecks und 94 nichtinteraktive Tests: 32 View×Theme×Sprache-Fälle, 26 Zustands-/Sprachfälle, weitere Komponenten-/Dialog-/Kontrastprüfungen und 20 Modellprüfungen. Fünf gezielte Tests decken die drei Review-Korrekturen auf Modell-/Berechnungs-/Server-HTML-Ebene ab. Die Komponentenprüfung erzeugt React-HTML-Strings ohne DOM, Browser, Effekte oder Benutzereingaben.

Offen: echte Fenstergrößen 960×640/1180×780, hohe DPI/UI-Skalierung, alle Themes/Sprachen im gerenderten Zustand, Tastatur-/Fokus-Rückgabe, System-Reduced-Motion, unterbrochene Übergänge, Drag/Resize/Scroll, Clipboard/Export und Loglast in einem echten Fenster. Erst nach erneuter menschlicher Freigabe interaktiv prüfen.

Nächster Produktmeilenstein ist der stabile Windows-Playtest mit echter Integration/Installer, nicht die Veröffentlichung dieser Demo. [PLAYTEST.md](PLAYTEST.md) hält Autorisierung, Stabilitäts-Gates und den technischen Release-Go fest. Abschnitt 4 wurde hier nicht begonnen.
