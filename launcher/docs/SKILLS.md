# Installierte Skills und Quellen

Installation am 28. Oktober 2026 mit dem vorhandenen `skill-installer` und dessen geprüftem `scripts/install-skill-from-github.py`. Installiert wurde per Download an fest aufgelösten Commit-IDs, ohne Git-Fallback, Paketmanager oder Ausführung von Repository-Installationspaketen. Alle Zielnamen wurden vorher gegen beide lokalen Skill-Verzeichnisse geprüft. Es gab keine Namenskonflikte; kein vorhandener Skill wurde überschrieben.

Neue Skills stehen ab dem nächsten Turn zur Verfügung. Sie liegen global für diesen Benutzer unter `C:\Users\Administrator\.codex\skills\`. Die Installation verändert keine Clientdateien.

## Quellen und Versionen

| Quelle | Gepinnter Commit | Installierter Inhalt |
| --- | --- | --- |
| [emilkowalski/skills](https://github.com/emilkowalski/skills/tree/2d228630ba1446365f614a108cb8252f1feda202/skills) | `2d228630ba1446365f614a108cb8252f1feda202` | Alle 15 Skills unter `skills/` samt Ressourcen. |
| [pbakaus/impeccable](https://github.com/pbakaus/impeccable/tree/5adaee704634e9031ec8943721c4ffa1a3c22221/.agents/skills/impeccable) | `5adaee704634e9031ec8943721c4ffa1a3c22221` | Codex-kompatibles `.agents/skills/impeccable` vollständig; Skill-Version 4.8.0, deklarierte Engine-Version 0.1.18. |
| [nextlevelbuilder/ui-ux-pro-max-skill](https://github.com/nextlevelbuilder/ui-ux-pro-max-skill/tree/28b3b8dee8c976df4fa3e94aed4e092ad43fcb03/.claude/skills/ui-ux-pro-max) | `28b3b8dee8c976df4fa3e94aed4e092ad43fcb03` | Selbständiges `ui-ux-pro-max` mit SKILL.md, echten `data/`, `scripts/` und `references/`-Verzeichnissen; keine verwaisten Verweise/Symlinks auf andere Repository-Verzeichnisse. |

## Absolute Installationspfade

Alle folgenden Pfade sind neu angelegt:

```text
C:\Users\Administrator\.codex\skills\animate
C:\Users\Administrator\.codex\skills\animate-expo
C:\Users\Administrator\.codex\skills\animation-performance
C:\Users\Administrator\.codex\skills\animation-vocabulary
C:\Users\Administrator\.codex\skills\apple-design
C:\Users\Administrator\.codex\skills\ask-sonner
C:\Users\Administrator\.codex\skills\break-ui
C:\Users\Administrator\.codex\skills\emil-design-eng
C:\Users\Administrator\.codex\skills\find-animation-opportunities
C:\Users\Administrator\.codex\skills\improve-animations
C:\Users\Administrator\.codex\skills\mobile-native
C:\Users\Administrator\.codex\skills\pick-ui-library
C:\Users\Administrator\.codex\skills\prototype
C:\Users\Administrator\.codex\skills\review-animations
C:\Users\Administrator\.codex\skills\write-swift
C:\Users\Administrator\.codex\skills\impeccable
C:\Users\Administrator\.codex\skills\ui-ux-pro-max
```

## Sicherheits- und Integritätsprüfung

Die Quellen wurden zuerst als Archive in einem eigenen temporären Audit-Verzeichnis heruntergeladen. Vor Extraktion wurden Zielpfade auf Archiv-Traversal geprüft. Ausgewählte Skill-Bäume wurden auf Symlinks geprüft; es waren keine vorhanden. Der vorhandene Installer wurde vor Benutzung gelesen; er prüft Skill-Pfade und verweigert existierende Zielverzeichnisse. Er führt den heruntergeladenen Skill-Inhalt nicht aus.

Nach Installation stimmen alle 159 installierten Dateien in 17 Skills per SHA-256 und Dateiliste mit den zuvor geprüften gepinnten Quellen überein. Die 125 bereits vorhandenen Dateien unter `.codex/skills` und `.agents/skills` blieben per Hashvergleich unverändert. Quellarchiv-Hashes und Datei-Fingerprints stehen in [installed-skills.json](evidence/section-1/installed-skills.json), Installer-Ausgaben daneben als `*-install.txt` (damit vorhandene Log-Ignore-Regeln die Nachweise nicht ausblenden).

## Gelesene Anweisungen und Anwendung im Folgeabschnitt

Gelesen wurden der Skill-Installer, die Schreibanweisungen und für die spätere Oberfläche die relevanten Anweisungen von Impeccable, UI/UX Pro Max sowie Emil zu Design Engineering, Animation, Performance, Review, Prototypen und Bibliothekswahl. Impeccables `init` und die Regeln für neue Oberflächen wurden auf den bereits bestätigten Produktplan bezogen. Die Nutzerentscheidung zu Stack und Gestaltung wird nicht erneut geöffnet. Die Produktunterlagen liegen bewusst nur im Launcher-Unterprojekt.

Für Abschnitt 3: Aufgabenmodus Operate, bereits festgelegte Designrichtung wahren; unmittelbar vor UI-Änderungen `impeccable/reference/craft-floor.md` lesen. UI muss gerendert und visuell kontrolliert werden. Motion braucht einen Zweck, kurze unterbrechbare Übergänge, Reduced Motion, keine Tastaturverzögerung und Lastprüfung. Allgemeine Stilverbote oder generierte Empfehlungen ersetzen keine ausdrückliche Sunset-/Geist-/Glow-/Blur-Vorgabe des Nutzers. Der generische Varianten-Picker des Prototype-Skills ersetzt nicht die bereits vereinbarten vier App-Ansichten oder das Review-Verfahren.

Die Impeccable-Engine bzw. `impeccable context` wurde in Abschnitt 1 nicht ausgeführt. Hier werden Skills installiert und Vorgaben dokumentiert, keine Oberfläche entworfen oder editiert. Der mitgelieferte Windows-Loader und sein SHA-256-Prüfpfad wurden gelesen. Er kann beim ersten späteren Aufruf ein Engine-Binary herunterladen; dessen tatsächliche Ausführung ist noch kein bestandener Test. Vor einem späteren Design-Einsatz muss der Kontextlauf erfolgen oder der im Skill beschriebene Fallback ausdrücklich angewendet werden. Es wurden keine Hooks oder globalen Design-Konfigurationen aktiviert.

## Funktionsprüfung UI/UX Pro Max

Vor Ausführung wurden Einstieg, Imports und lokale Daten-/Ausgabepfade der Python-Suche geprüft. Die geprüften Suchläufe nutzen lokale CSV-Daten; es wurde kein Paket installiert. 18 Python-Dateien sind syntaktisch gültig, alle 35 CSV-Dateien enthalten Datensätze. Tests mit `python -B` erzeugen keine Bytecode-Dateien im Skill.

| Lauf | Ergebnis | Evidenz |
| --- | --- | --- |
| `search.py "keyboard focus modal" --domain ux --json` | Exit 0, drei relevante Fokus-/Tastaturergebnisse. | [ux-search.json](evidence/section-1/ux-search.json) |
| `search.py "virtualize lists" --stack react --json` | Exit 0, zwei Ergebnisse zu Virtualisierung und stabilen Keys. | [react-search.json](evidence/section-1/react-search.json) |
| `search.py "desktop game launcher dark" --design-system --json -p "Neutron Launcher"` | Exit 0, gültige Ausgabe, aber generische Marketingstruktur; als Designvorschlag verworfen. | [design-system.json](evidence/section-1/design-system.json) |
| Ein engerer Wiederholungsversuch: `"gaming desktop launcher" --design-system --json` | Exit 0; Gaming-/Showcase-Vorschlag passt ebenfalls nicht als Gestaltungsautorität der operativen App. Nicht übernommen. | [design-system-retry.json](evidence/section-1/design-system-retry.json) |

Die Designsystem-JSONs sind ausschließlich Funktionstest-Evidenz. Kein generierter `MASTER.md`, keine Palette und kein Schriftvorschlag wurde in das Produkt übernommen. Maßgeblich bleibt [DESIGN.md](../DESIGN.md). Eine vollständige Upstream-Testsuite wurde nicht ausgeführt.
