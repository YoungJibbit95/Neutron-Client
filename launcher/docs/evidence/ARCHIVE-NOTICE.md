# Historisches Launcher-Archiv

Diese Dateien bewahren Entwicklungs- und Prüfnachweise vom 28. Oktober 2026. Sie gehören zu den jeweils genannten alten Commits und Kandidaten (`0.1.0-playtest.1` bis `.5`). Sie sind keine aktuelle Main-Abnahme, keine neue native Prüfung und keine Release-Freigabe.

## Aussagegrenzen

- Originale Fehlversuche, offene Gates, spätere Ergänzungen und identische Kopien bleiben erhalten. Insbesondere enthalten R2/R3 wiederverwendete R1-Buildbelege; R3s spätere `windows-acceptance.json` erweitert den früheren README-Stand. Zeit, Version und konkretes Artefakt sind gemeinsam zu lesen.
- Alte Zustimmungen, Ablehnungen, Tool-Anweisungen und `technicalReleaseGo`-Felder dokumentieren ausschließlich die damalige Sitzung. Sie erteilen heute keine Berechtigung und ersetzen keine aktuelle Nutzerentscheidung.
- Absolute Windows-Pfade, PIDs, Tool-Installationsverzeichnisse und Entwicklerhüllen (`*.py.txt`, `*.cjs.txt`) sind historische Kontextdaten. Die Hüllen werden nicht automatisch ausgeführt. Nicht mitgelieferte Laufzeiten, lokale Arbeitsverzeichnisse oder Installer lassen sich allein aus einem protokollierten Hash nicht erneut prüfen.
- Die Bilder zeigen die dokumentierten alten Oberflächen. Ein Screenshot belegt weder die aktuelle Interaktion noch einen neuen plattformübergreifenden Test. Linux/macOS-Headerprüfungen sind keine nativen Starts.
- Die Archivdaten und `launcher/tools/` sind keine Spieler-Laufzeitdaten. Die Paketkonfiguration nimmt `dist`, `out-electron`, `package.json` und ausdrücklich benannte Ressourcen auf; das Archiv wird darüber nicht ins Laufzeitpaket übernommen.
- Die aktuelle Alpha-Freigabe setzt den späteren echten Nutzer-Playtest und dessen ausdrückliche Freigabe voraus. Dieses Archiv öffnet dieses Gate nicht.

## Herkunft übernommener Werkzeugausgaben

Die folgenden Lizenzen gelten für die genannten übernommenen Teile, nicht als neue Lizenz des gesamten Projekts:

| Herkunft | Gepinnter Stand | Archivinhalt | Lizenzkopie |
| --- | --- | --- | --- |
| [Impeccable / Paul Bakaus](https://github.com/pbakaus/impeccable/tree/5adaee704634e9031ec8943721c4ffa1a3c22221) | Skill `5adaee704634e9031ec8943721c4ffa1a3c22221`; Engine `engine-v0.1.18` | Werkzeughinweise im gespeicherten `section-3/impeccable-context.txt`; Projektbrief und Designtext darin stammen aus diesem Projekt. | [Apache 2.0](licenses/impeccable-Apache-2.0.txt) |
| [UI UX Pro Max / Next Level Builder](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/tree/28b3b8dee8c976df4fa3e94aed4e092ad43fcb03) | `28b3b8dee8c976df4fa3e94aed4e092ad43fcb03` | Die lokalen Such- und Generatorausgaben `section-1/react-search.json`, `ux-search.json` und `design-system*.json` enthalten Daten des Werkzeugs. Die Designvorschläge wurden damals ausdrücklich verworfen. | [MIT](licenses/ui-ux-pro-max-MIT.txt) |

Impeccables [ursprünglicher NOTICE-Text](licenses/impeccable-NOTICE.md) wird ebenfalls aufbewahrt. Er nennt zwei Plattformreferenzen; deren Quellinhalte werden hier nicht mitgeliefert.

Die Originalausgaben bleiben unverändert; dieser Hinweis und die beiden Lizenzkopien wurden bei der späteren Archivübernahme ergänzt. Die Impeccable-Lizenzdatei ist am dokumentierten Skill-Commit und am Engine-Tag identisch. Die fünf übernommenen React-/UX-Suchergebniszeilen stimmen mit den CSV-Daten am dokumentierten Commit überein.

`section-1/installed-skills.json` und `../SKILLS.md` verzeichnen außerdem `emilkowalski/skills` mit Commit `2d228630ba1446365f614a108cb8252f1feda202` (MIT). Das Archiv führt dafür Installationspfade und Datei-Hashes auf; der installierte Skill-Quellbaum ist nicht Bestandteil dieser Übernahme. Die vollständigen Lizenzen vorhandener Laufzeit-Abhängigkeiten verbleiben an ihren eigenen vorgesehenen Orten; diese Archivnotiz ersetzt keine Prüfung einer zukünftigen Distribution.
