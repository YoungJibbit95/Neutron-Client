# Sunset Graphite — verbindliche Gestaltungsgrundlage

Stand: 28. Oktober 2026. Aus dem abgestimmten Nutzerplan übernommen. Abschnitt 3 implementiert diese Vorgaben als Prototyp mit gemeinsamen Farb-Tokens, lokalen Fonts und Komponenten. Die Implementierung ist **visuell und interaktiv noch nicht abgenommen**. Quellen und nichtinteraktive Nachweise: [UI-Prototyp](docs/UI-PROTOTYPE.md). Die folgende Gestaltungsgrundlage bleibt verbindlich; neue Tokenwerte sind bis zur visuellen Abnahme vorläufig.

## Oberfläche

Startfenster ungefähr 1180 × 780, Minimum 960 × 640, skalierbar. Links eine stabile Sidebar von ungefähr 220 px, rechts die wechselnde Hauptansicht. Native Fensterfunktionen erhalten. Nutzungsmodus der App-Ansichten: Operate, also klare Aufgaben, Status und präzise Bedienung.

Ruhige dunkelgraue Verlaufsflächen mit orange-pinken Sunset-Akzenten. Dezenter Glow für Launch und aktive Zustände; Blur gezielt dekorativ einsetzen. Die Konsole braucht klare Kontraste und stabile, lesbare Zeilen. Gemeinsame semantische Tokens, Standard Sunset sowie Ocean, Violet und Graphite. Geist und Geist Mono für Logs werden lokal gebündelt; Lizenz und tatsächliche Dateien sind noch zu prüfen. Panels ungefähr 16 px Radius, Controls ungefähr 10 px.

Die ausdrücklichen Nutzervorgaben zu Fonts, Verläufen, Glow und Blur haben Vorrang vor allgemeinen Stilvorschlägen installierter Skills. Keine automatische Übernahme generierter Paletten oder Schriftvorschläge.

## Ansichten

| Ansicht | Inhalt |
| --- | --- |
| Launch Client | Zentrale Version mit Build-Datum, kurzem Commit und Bereitschaftsstatus; großer Launch-Button, sekundäres Update; darunter höhenverstellbare Konsole. |
| Launch Options | RAM-Slider mit Zahlenfeld; Working Directory mit Auswahl; verwaltetes/eigenes Java; Fenstergröße/Vollbild; zusätzliche JVM-Argumente unter Erweitert. |
| Launcher Settings | Theme-Vorschau; Deutsch/Englisch; UI-Skalierung; Effektstärke; reduzierte Bewegung. |
| Update | Installierte Builds, aktueller GitHub-Stand, Update-Prüfung, Neueste Version bauen, Bauen und spielen; ältere Builds auswählen/entfernen. |

## Zustände und Rückmeldung

Der Prototyp muss Erststart, keine Version, bereit, offline, Update verfügbar, Setup/Download/Build/Prüfung, Abbruch, Fehler, laufenden Client und Crash zeigen. Gesperrte Aktionen erklären den Grund; Logfilter und Scrollzustand bleiben bei neuen Zeilen stabil. Laufende Builds dürfen nicht entfernt oder ausgetauscht werden. Problembericht und Schließen bei laufendem Client erhalten prüfbare Dialogzustände.

Mockdaten sind als solche erkennbar und enthalten realistische, zusammenhängende Zeitpunkte, Commit-IDs und Logs. Sie dürfen keinen tatsächlich durchgeführten Build oder erfolgreichen Plattformstart vortäuschen.

## Motion und Bedienung

Buttonfeedback 100–160 ms, View-Wechsel ungefähr 180 ms, dezente Überblendung/Bewegung. Sidebar bleibt stabil. Öffnen/Einklappen der Konsole animiert den Container, ohne Logzeilen zu verwischen oder dauerhaft zu bewegen. Übergänge bleiben unterbrechbar. Tastatureingaben reagieren sofort. Systempräferenz und eigener Reduced-Motion-Schalter wirken zusammen; semantische Statusrückmeldung bleibt sichtbar.

Bevor UI-Code geändert wird: relevante installierte Skill-Anweisungen und Impeccables `reference/craft-floor.md` lesen. Keine UI-Implementierung in Abschnitt 1 oder 2. Für die App ist code-first vorgesehen; ein eventuell späterer Landingpage-Workflow wird hier nicht vorentschieden.

## Visuelle Abnahme in Abschnitt 3 und 5

Alle vier Ansichten und Fehler-/Ladezustände tatsächlich rendern, Screenshots anlegen und Interaktionen prüfen. Vier Themes × zwei Sprachen, Mindest-/Startgröße, hohe DPI/UI-Skalierung, Tastatur/Fokus, Reduced Motion und Loglast abdecken. Nachweise müssen gerendertes Verhalten zeigen; statische Codeprüfung allein genügt nicht.
