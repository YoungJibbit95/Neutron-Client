# Umsetzung und Review-Vertrag

Grundlage: der am 28. Oktober 2026 übergebene, mit dem Nutzer abgestimmte Neutron-Launcher-Plan. Produktumfang: [PRODUCT.md](../PRODUCT.md), Gestaltung: [DESIGN.md](../DESIGN.md), Nachweismatrix: [GATES.md](GATES.md).

## Zusammenarbeit

Implementierungs-Chat arbeitet in fünf Abschnitten. Review-Chat: „Plane Nexus Client Launcher“, ID `01a18300-7319-71e1-8854-79ed424e2be8`, Host `local`.

Nach jedem Abschnitt endet der Implementierungs-Turn mit `REVIEW BEREIT – Abschnitt N`, konkreten Änderungen, absoluten Pfaden, Prüfungen mit Ergebnissen und offenen Risiken. Erst eine Nachricht des Review-Chats mit Freigabe oder Korrekturauftrag erlaubt die Fortsetzung. Keine stillschweigende Zustimmung, keine vorzeitige Implementierung späterer Abschnitte.

Es gibt parallele Client-Arbeit. Vor jeder späteren Client-/POM-Änderung aktuellen Stand erneut lesen und den Eingriff auf produktiven Start begrenzen. Keine Änderungen an Combat/Scaffold. Keine fremden Änderungen zurücksetzen, stagen, committen, überschreiben oder veröffentlichen. Die nachfolgende neue Playtest-Autorisierung ersetzt das frühere pauschale Push-/Veröffentlichungsverbot ausschließlich für die geprüfte Playtest-Auslieferung. Automatische Issue-Erstellung bleibt ausgeschlossen.

## Aktualisierter nächster Meilenstein: Windows-Playtest

Verbindliche neue Nutzerpriorität, wörtlich: „Merk dir das der Agent mit dem nächsten Schritt eine releasebare Playtest Version vom Launcher als Windows Installer bauen und auf den github releasen soll damit ich mit meinem kollegen den client testen kann aber wirklich nur wenn der launcher stabil genug ist“.

Nach dem laufenden Abschnitt 3 hat Windows x64 Vorrang: echte Integration aus Abschnitt 4 plus Windows-Installer aus Abschnitt 5 zu einer stabilen Playtest-Version verbinden. Danach GitHub-Pre-Release in `YoungJibbit95/Neutron-Client`. Keine Veröffentlichung eines Mock-Prototyps. Linux/macOS bleiben langfristiger Umfang, keine ungeprüften Unterstützungszusagen.

Für diese Auslieferung sind notwendige projektbezogene Commits/Pushes, ein Release-Tag und das GitHub-Pre-Release samt Installer vom Nutzer autorisiert. Vorher lokalen Snapshot und Remote-`main` sauber vergleichen, isolierte Änderungen und eine konkrete prüfbare Commit-Liste herstellen; kein ungeprüfter Workspace-Push, kein Force-Push, keine fremden Client-Arbeiten oder persönlichen Daten. Die Autorisierung verlangt kein erneutes pauschales Nutzer-Okay, wohl aber das technische Release-Go des Review-Chats nach unabhängiger Prüfung.

Die bisherigen Abschnitts-Reviews bleiben bestehen: zuerst Abschnitt 3 abgeben, danach den Windows-Playtest-Meilenstein vorbereiten und **vor Veröffentlichung erneut beim Review-Chat anhalten**. Mindest-Gates und Liefernachweise: [PLAYTEST.md](PLAYTEST.md) und [GATES.md](GATES.md). Ungeklärte Signierung bleibt vor öffentlicher Auslieferung ein Gate.

Die Escape-Sperre bleibt bestehen. Nichtinteraktive Umsetzung darf weitergehen; notwendige reale UI-/Installationsprüfungen brauchen erneute menschliche Freigabe. Fehlt sie, bleibt der Release blockiert. Grüne Unit-Tests allein erlauben keine Veröffentlichung.

## Abschnitte

| Abschnitt | Lieferumfang | Abschlussnachweis |
| --- | --- | --- |
| 1. Skills und Ausgangslage | Angeforderte Skills sicher installieren; Quellen, Konflikte, Pfade/Versionen und Baseline dokumentieren; Produktplan und Testmatrix festhalten. Keine Launcher-Implementierung. | Installationsinventar, Integritäts-/Funktionsprüfungen, Dokumente und offene Risiken für Review. |
| 2. Startfähigkeit | Reproduzierbarer Build, produktiver Einstieg mit Manifest, explizite Assets/Natives/Arbeitsverzeichnis; Legacy-Native- und Plattformpfade früh prüfen. | Tatsächlicher Windows-Build/-Start soweit verfügbar; getrennte Nachweise und Adapter/CI für weitere Plattformen. Fehlende Geräte und Laufnachweise offen, Review entscheidet über Fortsetzung. |
| 3. UI-Prototyp | Vier Views, alle Zustände, Themes, Motion und i18n mit realistischen Mockdaten. | Gerenderte Screenshots und geprüfte Interaktionen einschließlich Tastatur, DPI, Reduced Motion und Loglast. |
| 4. Hintergrundintegration | Setup, GitHub-Commit-Build, Versionsverwaltung/Rollback, Prozesse, Konsole, Einstellungen. | Integration mit realen Prozessen, Fehlerfällen, Offline-Start, Abbruch und Schutz laufender Versionen. |
| 5. Auslieferung | Installer/CI, verfügbare End-to-End-Prüfung, finaler visueller und funktionaler Review. | Plattformpakete und echte Frischsystem-Starts; klar benannte fehlende Hardware-, Signierungs- oder Release-Nachweise. |

## Technische Leitplanken für spätere Abschnitte

Electron Main verwaltet Fenster und Hintergrundarbeit; eine schmale typisierte Preload-Schnittstelle verbindet React mit Einstellungen, Versionen, Setup, Build, Start/Stopp und Logexport. Events transportieren Fortschritt, Logs und Prozesszustand. Renderer haben keine Shell-Schnittstelle. Downloads und Archive werden geprüft, Fehler erklären Wiederaufnahme und nächste Schritte.

Der Client wird aus dem fest aufgelösten `main`-Commit der vereinbarten Quelle gebaut. Quellarchiv statt Git beim Spieler, eigener Workspace und Dependency-Cache, getrennte Versionen und Aktivierung erst nach Prüfung. Manifest enthält echte Build-/Versions-/Startdaten. Letzter spielbarer Build bleibt bei jedem Fehler erhalten. Laufende Versionen bleiben geschützt.

Der Erststart provisioniert Java-8-JDK, Maven, Assets und passende Natives lokal. Entwicklungswerkzeuge auf diesem Rechner sind kein Frischsystemnachweis. macOS x64-Natives einschließlich Apple-Silicon-Kompatibilität sind ein frühes Release-Gate. CI-Erfolg ersetzt keinen realen Fenster-/Client-Start.

Client-/Build-Logs sind getrennt; begrenzte virtualisierte UI, vollständige rotierende Dateilogs, redigierter Diagnoseexport. Prozessabbruch muss den gesamten Baum erreichen; nur unvollständige temporäre Daten dürfen verworfen werden. Ein erfolgreicher Build kann auf Wunsch unmittelbar starten.

## Aktueller Stand

Abschnitt 1 ist freigegeben. Der Review-Chat hat die technische Teilprüfung von [Abschnitt 2](REVIEW-2.md) unabhängig akzeptiert: acht Python-Tests, 63 Manifest-Dateien, beide erfolgreichen Maven-Builds und tatsächlicher Menü-Screenshot. Das ist keine vollständige Laufzeit-/Release-Freigabe. Settings-Nachprüfung, geprüfte Schließinteraktion, Linux/macOS und Frischsystem bleiben offen.

Die davon unabhängige **Implementierung von Abschnitt 3** ist ausdrücklich freigegeben. Der Electron-/React-/TypeScript-Prototyp umfasst vier Views, vier Themes, DE/EN, Beispielzustände und kurze Motion. Automatische Prüfungen: Build/Typechecks und 94 nichtinteraktive Tests. Keine Browser-/Desktop-/Client-Interaktion, keine Screenshots dieses Prototyps. Die visuelle/interaktive Abnahme bleibt bis zur menschlichen Freigabe angehalten; [REVIEW-3.md](REVIEW-3.md) trennt den Stand. Der Review-Chat hat diese drei Korrekturen unabhängig geprüft und technisch akzeptiert. Echte Integration und Windows-Installer-Vorbereitung wurden danach ausdrücklich freigegeben; der aktuelle Vertrag steht in [INTEGRATION.md](INTEGRATION.md). Visuelle Abnahme und Release-Go bleiben getrennt und offen.

Produktive Integration muss Python/Git als Spielervoraussetzung entfernen und Maven-Abhängigkeiten/Integrität weiter absichern. Keine Änderungen an Client/POM in Abschnitt 3. Startvertrag: [STARTABILITY.md](STARTABILITY.md). Ursprüngliche Baseline: [BASELINE.md](BASELINE.md).

Der erste Windows-Kandidat wurde unabhängig mit 109 Tests geprüft und wegen zweier konkreter Fehler zur Korrektur zurückgegeben. R2 (0.1.0-playtest.2) behebt das Start-/Stop-/Shutdown-Rennen und die Offline-Recheck-Sackgasse mit sechs Regressionstests; 115 Prüfungen bestehen. Kein Release-Go, keine Aufhebung der Escape-Sperre. [Review R2](REVIEW-WINDOWS-R2.md).

R2 wurde nach unabhängig bestandenen 115 Tests wegen eines weiteren P1 bei fehlgeschlagenem taskkill zur Korrektur zurückgegeben. R3 (0.1.0-playtest.3) trennt Stop-Versuch und echtes Prozessende, behält Prozess-/Versions-/Aufgabenschutz bei Fehler und erlaubt Retry. Vier neue Regressionen, insgesamt 119 Tests. [Review R3](REVIEW-WINDOWS-R3.md). UI-/Signierungs-/Release-Gates bleiben offen.
