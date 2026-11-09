# Review-Paket — Abschnitt 3

**Implementiert und automatisch geprüft; visuell/interaktiv ungeprüft.** Der Review-Chat hat nach akzeptierter technischer Teilprüfung von Abschnitt 2 ausschließlich die Implementierung dieses Prototyps freigegeben. Die Escape-Sperre bleibt wirksam. Kein Browser-/Desktop-/Electron-/Clientstart und keine Bedienung bestehender Fenster in diesem Abschnitt.

## Implementierter Umfang

- Electron-Shell mit nativen Fensterfunktionen, 1180×780 Startgröße und 960×640 Minimum. Sandbox/Context Isolation, keine Node-Integration, keine Navigation/neuen Fenster. Preload stellt nur `prototype` und eine leere Fähigkeitsliste bereit.
- Vier React-/TypeScript-Views: Client starten, Startoptionen, Launcher-Einstellungen und Updates. Stabile Sidebar, gemeinsame semantische Tokens, Sunset/Ocean/Violet/Graphite und vollständige DE-/EN-Texte.
- Lokal gebündelte Geist-/Geist-Mono-WOFF2-Fonts mit Lizenztexten; Lucide als einheitliche SVG-Iconfamilie.
- Dreizehn Beispielzustände, Erststart/leer/offline, vier Buildphasen, Abbruch/Fehler und laufend/Crash. Alle Daten und Aktionen sind sichtbar als Simulation gekennzeichnet.
- RAM-/Pfad-/Java-/Fenster-/JVM-Eingaben, Theme-Vorschau, UI-Skalierung, Effektstärke, System-/eigene Reduced Motion und kurze unterbrechbare View-Motion. Tastatureingaben warten nicht auf Animationen.
- Höhenverstellbare/einklappbare Konsole, getrennte Streams, Suche/Filter, begrenzter virtualisierter Logpuffer, Copy/Text-Export, Scrollpause und neue-Zeilen-Zähler als Frontend-Code.
- Native HTML-Dialoge für geschützte Build-Entfernung, drei simulierte Schließoptionen und redigierte Problembericht-/Issue-Vorschau. Keine Website wird geöffnet und nichts veröffentlicht.

Keine echte Hintergrundintegration: keine Java-/Maven-/Git-Prozesse aus der UI, kein produktiver Download/Build, keine persistenten Einstellungen, keine rotierenden vollständigen Dateilogs und kein Installer. Die Python-Werkzeuge aus Abschnitt 2 wurden nicht als Spieler-Voraussetzung eingebunden. Dies ist kein veröffentlichbarer Playtest.

## Korrekturen nach Review

Alle drei angeforderten Korrekturen sind im Code umgesetzt. Abschnitt 4 bleibt unbegonnen.

| Fall | Vorher | Jetzt | Nichtinteraktiver Nachweis |
| --- | --- | --- | --- |
| Erster Build → Fehler/Abbruch | Leeransicht verdeckte den Grund und bot nur den allgemeinen Build-Button. | DE/EN-Hinweis benennt Simulation, Fehler/Abbruch und fehlende installierte Version; aktivierbarer Retry startet erneut. Kein Verweis auf eine vermeintlich spielbare letzte Version. | Reducer-Kette `empty → build → fail/cancel`, Server-HTML in beiden Sprachen, Retry zurück zu `prepare`. |
| Pausierte Konsole bei vollem Puffer | Gespeicherter Pixelwert zeigte nach Rotation andere Zeilen; Navigation setzte Filter/Scrollzustand zurück. | Anker aus stabiler Log-ID und Teilzeilenoffset. Bei verdrängtem Anker kontrolliert auf älteste verfügbare Zeile und sichtbarer Hinweis „Ältere Ausgabe verworfen“. Browser-Scrollanker deaktiviert. Die Konsole bleibt bei Navigation eingehängt und außerhalb der Startansicht verborgen; Quelle, Filter, Suchtext, Pause und Anker bleiben im Arbeitsspeicher. | Berechnung mit vollem echten Modellpuffer, nachgeschobenen 120/2.000 Zeilen, erhaltener ID/Offset bzw. Rückfall auf Position 0; Server-HTML enthält die versteckte Konsole auch in anderen Views. Der Test führt keine Navigation aus. |
| Konsolenhöhe bei 640 px / UI-Skalierung | Fester Maximalwert 460 px konnte die Hauptansicht vollständig verdrängen. | `ResizeObserver` misst verfügbaren Hauptbereich, feste Geschwister, Abstände und Konsolenkopf. Maximalhöhe reserviert 160 px × UI-Skalierung für die scrollbare Hauptansicht. Messung bei Resize, Sprache, Skalierung und Größenänderung der Beispielsteuerung. Unter 122 px Inhaltsbudget klappt die Konsole platzbedingt zu; bei mehr Platz gilt wieder der Öffnungswunsch. Hauptbereich kann bei extremem Platzmangel insgesamt scrollen. | Pure Budgetrechnung für 640 px, 100/125/150 %, veränderten Kopf/Steuerungsbedarf und großes Fenster. DOM-Messung, ResizeObserver, Fokus und tatsächliche Erreichbarkeit sind weiterhin nicht ausgeführt. |

Fünf gezielte zusätzliche Tests statt einer weiteren breiten Matrix: drei Modell-/Berechnungstests und zwei Server-HTML-Tests. Diese Nachweise ersetzen keinen echten Layout-, Navigations- oder Scrolltest. Ein realer Test muss insbesondere gehaltene Zeile/Offset bei voller Pufferrotation, Verdrängungshinweis, Filter/Pause nach Navigation und erreichbare Hauptaktionen bei 960×640 mit 125 % UI-Skalierung prüfen.

## Konkrete Dateien

Alle einzelnen absoluten Pfade mit SHA-256 stehen in `F:\Puslar Backup - Copy\launcher\docs\evidence\section-3\inventory.json`.

| Absoluter Pfad | Inhalt |
| --- | --- |
| `F:\Puslar Backup - Copy\launcher\package.json` und `package-lock.json` | Gepinnte Abhängigkeiten und ausführbare Build-/Testskripte. |
| `F:\Puslar Backup - Copy\launcher\.npmrc` und `.gitignore` | Deaktivierte Lifecycle-Skripte; erzeugte lokale Daten ausgeschlossen. |
| `F:\Puslar Backup - Copy\launcher\index.html` | Lokaler Renderer-Einstieg mit restriktiver Produktions-CSP. |
| `F:\Puslar Backup - Copy\launcher\tsconfig.json`, `tsconfig.electron.json`, `vite.config.ts` | Strikte Typechecks und Renderer-/Electron-Build. |
| `F:\Puslar Backup - Copy\launcher\electron\main.cts`, `preload.cts`, `window-contract.cts` | Begrenzte Desktop-Shell und rein prüfbarer Fenster-/Berechtigungsvertrag. |
| `F:\Puslar Backup - Copy\launcher\src\App.tsx` | Vier Views, Konsole und Dialoge. |
| `F:\Puslar Backup - Copy\launcher\src\model.ts` | Beispielzustände, Schutz-/Rollback-/Aktivierungsregeln und begrenzte Logs. |
| `F:\Puslar Backup - Copy\launcher\src\copy.ts` | DE/EN und Formatierung. |
| `F:\Puslar Backup - Copy\launcher\src\tokens.ts`, `styles.css`, `main.tsx` | Themes, Layout, Motion, lokale Fonts und React-Einstieg. |
| `F:\Puslar Backup - Copy\launcher\scripts\check.mjs`, `build.mjs` | Shellfreie Node-Unterprozesse; Typechecks und Prüfung des eigenen erzeugten `dist/`-Ziels vor dem Build. |
| `F:\Puslar Backup - Copy\launcher\tests\model.test.ts`, `components.test.tsx` | Nichtinteraktive Modell-/Komponenten-/Kontrastrechnung. |
| `F:\Puslar Backup - Copy\launcher\public\licenses\` | Fünf mitgelieferte Font-/React-/Lucide-Lizenztexte. |
| `F:\Puslar Backup - Copy\launcher\README.md` | Entwicklerprüfung und wirksame Interaktionssperre. |
| `F:\Puslar Backup - Copy\launcher\PRODUCT.md`, `DESIGN.md` | Aktualisierte Produktpriorität bzw. Implementierungsstand ohne visuelle Freigabe. |
| `F:\Puslar Backup - Copy\launcher\docs\PLAN.md`, `GATES.md`, `PLAYTEST.md` | Akzeptierte technische Teilprüfung, aktuelle Freigaben und neuer Windows-Playtest-Auftrag. |
| `F:\Puslar Backup - Copy\launcher\docs\UI-PROTOTYPE.md`, `surfaces\desktop.md`, `REVIEW-3.md` | Oberflächenauftrag, genaue Funktions-/Prüfgrenzen und dieses Paket. |
| `F:\Puslar Backup - Copy\launcher\docs\evidence\section-3\` | Tatsächliche Build-/Testausgaben, Engine-/Paketmetadaten, Artefakthashes und Ressourcenaufnahme. |

## Automatisch getestet

| Prüfung | Ergebnis | Nachweis |
| --- | --- | --- |
| Dependency-Installation | 106 Pakete installiert; genaue Versionen/Integritätswerte im Lockfile; Lifecycle-Skripte deaktiviert; Cache/Temp auf F:. | `package-lock.json`, `dependency-metadata.json` |
| Build und Typechecks | Exit 0; React/Vite-Artefakte und drei Electron-CJS-Dateien erstellt. Electron wurde dabei nicht gestartet. | `evidence/section-3/build.txt`, `verification.json` |
| Vitest | **94/94 bestanden, 0 fehlgeschlagen.** | `evidence/section-3/tests.json` |
| Modell | 20 Prüfungen: Offline-Startzustand, geschützter laufender Build, Aktivierung erst nach Prüfung, Abbruch/Fehler erhalten vorherige Version, ungültige Startoptionen, RAM/JVM, Redigierung, begrenzte Logs, ID-/Offset-Anker, verdrängter Anker und Höhenbudget. | `tests/model.test.ts` |
| Komponenten | 32 View×Theme×Sprache-Fälle; 26 Zustand×Sprache-Fälle; zusätzliche Dialog-, Fortschritts-, Recovery-, Virtualisierungs-, Sprach- und Fenster-/Berechtigungsprüfungen. React erzeugt nur HTML-Strings, ohne DOM/Effekte/Benutzereingaben. | `tests/components.test.tsx` |
| Kontrastrechnung | Vier Themes: feste Text-/Sekundärfarben gegen vier Schichten und Text gegen beide Primär-Endpunkte jeweils ≥4,5:1. Keine Zertifizierung gerenderter Transparenz-/Hover-/Fokuszustände. | Vier Tests in `components.test.tsx` |
| Artefakte | 13 erzeugte Dateien; beide referenzierten Fonts lokal vorhanden, Lizenzen gebündelt, Produktions-CSP blockiert Netzwerkverbindungen. Pfade/Hashes maschinenlesbar. | `verification.json` |
| Bestehender Client/POM | `pom.xml`, produktiver Bootstrap und Bootstrap-Test unverändert gegenüber Beginn dieses Abschnitts. Keine Client-/Combat-/Scaffold-/GUI-Klasse bearbeitet. | `clientSourceAndPomPreserved` in `verification.json` |
| Impeccable | Engine 0.1.18 SHA-256 geprüft; einmaliger Kontextlauf Exit 0. Keine UI gestartet. | `impeccable-engine.json`, `impeccable-context.txt` |

Werkzeuge: Node 26.3.1, React/ReactDOM 19.3.1, TypeScript 7.0.2, Vite 8.3.5, Vitest 5.0.5, Electron-Typen/CLI 45.1.0. Die Electron-Laufzeit wurde nicht heruntergeladen oder gestartet. Der npm-Skript-Shell-PATH-Auflösungsfehler wurde mit einer ausschließlich pro Kindprozess gesetzten PowerShell behoben; keine globale Änderung. Alle finalen Kommandos/Variablen stehen in `verification.json`.

## Artefakt- und Ressourcenpfade

- Renderer: `F:\Puslar Backup - Copy\launcher\dist\index.html` mit zwei lokalen Fonts, JS/CSS und fünf Lizenztexten.
- Kompilierte Desktop-Shell: `F:\Puslar Backup - Copy\launcher\out-electron\main.cjs`, `preload.cjs`, `window-contract.cjs`.
- Caches/Temp/Engine: `F:\Neutron Launcher Section3 20261028\` mit `npm-cache`, `vite-cache`, `temp`, `impeccable`; `electron-cache` ist als F:-Ziel konfiguriert, keine Electron-Laufzeit heruntergeladen.
- Exaktes Build-/Artefaktinventar und Client-/POM-Abgleich: `F:\Puslar Backup - Copy\launcher\docs\evidence\section-3\verification.json`.
- Lesende Kapazitäts-/Größenaufnahme: `F:\Puslar Backup - Copy\launcher\docs\evidence\section-3\resources.json`. Keine Bereinigung auf C:; die in Abschnitt 2 abgelehnten Ziele blieben unangetastet.

## Visuell und interaktiv NICHT geprüft

Keine Screenshots dieses Launchers erstellt oder als geprüft ausgegeben. Die älteren Bilder unter `section-2/` zeigen ausschließlich den damaligen Minecraft-Client und sind kein Nachweis für den Launcher-Prototyp.

Offen bleiben: reale Mindest-/Startfenstergröße und Layout, hohe DPI/Skalierung, alle Themes/Sprachen im gerenderten Zustand, Fokusführung und Rückgabe nativer Dialoge, System-Reduced-Motion und unterbrochene Übergänge, Pointer-/Tastatur-Resize, Scrollpause/Zeilenzähler unter echter Loglast, Clipboard und Export. Browser/Computersteuerung darf dafür erst nach erneuter menschlicher Freigabe verwendet werden. Ein grüner Server-HTML-Test ersetzt diese Abnahme nicht.

Der vollständige Impeccable-Finish-/Render-Review wurde deshalb nicht ausgeführt. Die vorgegebenen Gestaltungsregeln bleiben verbindlich; die implementierten Tokenwerte sind bis zum echten Review vorläufig und nicht als visuell abgenommen kanonisiert.

## Aktualisierter nächster Lieferauftrag

Die neue Nutzerpriorität ist dauerhaft in PRODUCT, PLAN, GATES und [PLAYTEST.md](PLAYTEST.md) gespeichert: **als nächster Meilenstein ein stabiler Windows-x64-Installer mit echter Integration und Clientstart, anschließend GitHub-Pre-Release in `YoungJibbit95/Neutron-Client`. Kein Mock-Prototyp veröffentlichen.** Linux/macOS bleiben langfristig, ohne ungeprüfte Unterstützungszusage.

Notwendige projektbezogene Commits/Pushes, Release-Tag und Pre-Release samt Installer sind nach bestandenen Stabilitätsprüfungen vom Nutzer autorisiert. Das ersetzt das frühere pauschale Veröffentlichungsverbot ausschließlich dafür. Lokalen Snapshot/Remote-main zuerst klären, isolierte Änderungen und prüfbare Commit-Liste, keine fremden Client-Arbeiten/Spielstände/Logs/Token, kein Force-Push. Keine solche Release-Aktion wurde in Abschnitt 3 ausgeführt.

Vor Veröffentlichung beim Review-Chat anhalten und unabhängigen technischen Release-Go abwarten. Kein neues pauschales Nutzer-Okay zum bereits autorisierten Release verlangen. Reale Installer-/Setup-/GitHub-Build-/Client-/Updatefehler-/Abbruch-/Rollback-/Offline-/Settings-/Crash-/Export-Nachweise, Privatdaten-Ausschluss, Installer-SHA256, genaue Version/Commit, Release Notes und bekannte Einschränkungen sind Pflicht. Ungeklärte Signierung vor öffentlicher Auslieferung bleibt Gate; SmartScreen-/Signierungsstatus ehrlich ausweisen.

Die Escape-Sperre bleibt unverändert. Wenn notwendige reale UI-/Installationstests deshalb fehlen, bleibt der Release blockiert — unabhängig von grünen Unit-Tests. Der nächste Meilenstein aus Abschnitt 4 plus Windows-Paketierung aus Abschnitt 5 wurde noch nicht begonnen; der Review-Chat entscheidet über den nächsten Auftrag.

REVIEW BEREIT – Abschnitt 3
