# REL-P00 — Ergebnis und Übergabe

28.10.2026: **SOURCE_INTEGRATED**, gemeinsame Regression **565/0/0/3**.
Packaging erzeugt lokale JARs; ihr direkter Start scheitert. Vollständiger
Client-/Launcherstart und Multiplayer sind nicht abgenommen.
Ergebnis: **LOCAL_ONLY / DISTRIBUTION_BLOCKED**. Kein Installer, keine Betafreigabe.

## Gates

| Gate | Status | Bedeutung |
|---|---|---|
| 0 Quellen/Pins/Isolation | PASS | 18 Manifestdateien korrekt, Masterprompt identisch, Quellbranches sauber, Ziel frei |
| A Integration/Regression | PASS | Exakte CMB-Basis plus zwei gepinnte VIS-Commits, konfliktfrei, 94 XMLs mit 565/0/0/3 |
| B1 Packaging-Build | PASS | `mvn -o package`; JAR-/Manifest-/Bytecode-/Dependencyprüfung durchgeführt |
| B1 direkter JAR-Start | FAIL | Fehlender Launchwrapper-Main bzw. fehlendes Main-Class-Attribut |
| B2 vollständiger lokaler Client | BLOCKED | Offizieller Launcher laut Nutzer nicht installiert; kein authentifizierter Start |
| B3 offizieller Launcher | NOT_RUN | B2 nicht bestanden, keine eigene JSON installiert |
| B4 Multiplayer | NOT_RUN | Kein bestätigter Testserver-/Zweikonten-Smoke |
| C Distribution/Installer | BLOCKED | Vollständige Mojang-/OptiFine-Bestandteile und fehlende End-to-End-/Rechtefreigaben |

## Schutz bestehender Arbeit und Allowlist

Vor Erstellung des neuen Worktrees wurden alle **33** vorhandenen Worktrees
lesend erfasst: HEAD, Branch, vollständiger Porcelainstatus und SHA256 des
binären getrackten HEAD-Diffs. Der Nachvergleich meldet **30 unverändert** und
drei beobachtete parallele Änderungen:

- Originalcheckout wechselte von `prep/neutron-opencode-wave00` auf
  `codex/revived-final-integration`, HEAD `eeddb7da58978f04fad95ad8042175b7ad848d6a`.
  Sein Porcelainstatus mit 147 getrackten Änderungen und sein Binärdiffhash
  bleiben exakt unverändert: `ea20915ab1fd52e816248910c54fb0c4731989e928e5d7d64eea59b4f0dcc88d`.
  Der fremde Checkout-Reflog zeigt den Branchwechsel; dieser Auftrag führte ihn nicht aus.
- SCF-1a änderte seinen Arbeitsdiff bei unverändertem HEAD.
- VIS-02 wechselte auf `fca2d3e333d83e92662bb8ff5498372531a9bdb5`
  und commitete seinen vorherigen Arbeitsdiff.

Keine dieser Änderungen wurde übernommen, überschrieben oder zurückgestellt.
Der Vergleich beweist keine unveränderten Inhalte aller untracked Dateien und
keine globale Schreibsperre gegen parallel arbeitende Prozesse.
Alle fremden Git-Leseaufrufe liefen mit `--no-optional-locks`.

Genau elf ursprüngliche VIS-Dateien wurden durch die zwei geprüften Cherry-Picks
übernommen. REL selbst ergänzt ausschließlich sechs Markdowndateien unter
`design/neutron-codex-rel-p00/`: diese vier Berichte, `LOCAL_RUNTIME_GATE.md`
und `NEUTRON_PRIVATE_PLAYTEST_SCOPE.md`. Keine POM-/Dependency-/Produktions-
oder Teständerung für REL, keine neuen Launcherprofile, keine Spiel-/Asset-
oder Kontodateien im Commit. Der Review-Patch gegen CMB enthält nur diesen
begrenzten VIS-Diff und die neuen Berichte, keine vollständigen Spielklassen.
Die Abschluss-Allowlist und der saubere eigene Status werden extern überprüft.

Alle ZIP-Dateien wurden vollständig gelesen; zuerst `00_START_HIER.md`, dann
Manifest und Masterprompt. Entpackort: `F:\neutron-rel-p00-evidence-20261028\sources`,
außerhalb sämtlicher Worktrees. Historische Produkt-/SCF-/NET-Dokumente wurden
als Kontext behandelt; sie erweiterten den ausdrücklichen REL-P00-Auftrag nicht.
Keine Subagents, Fetches, Pushes, PRs, Merges, Rebases, Tags, Resets, Stashes
oder Git-Cleans. Mavens Clean betraf nur das eigene target. Das gewünschte
Modell-/Reasoningprofil ist eine Hosteinstellung; eine Umstellung wurde hier
nicht vorgenommen oder unabhängig verifiziert.

## Konkrete nächste Schritte, nicht in REL-P00 ausgeführt

1. Regulären offiziellen Launcher durch den Nutzer installieren und mit eigener
   Lizenz anmelden; keine Zugangsdaten an den Agenten übergeben.
2. Separaten Packaging-/Loaderauftrag auf dem gepinnten Integrationsstand
   freigeben: tatsächlicher Main, Classpath-/Native-Konflikte, vollständige
   Reflexions-/Ressourcenprüfung sowie Arbeitsverzeichnis-/Home-Isolation.
   Ein Manifestfix allein ist keine Clientabnahme.
3. Eigenständige rechtlich geklärte Mod-/Transformationskomponente entwickeln;
   Mojang-/OptiFine-Klassen und ungeklärte Assets nicht in Testerpakete exportieren.
4. Danach Vanilla-Kontrolle, echter Client/Display/MainMenu/Welt/GUI/Exit,
   offizieller Launcher und erlaubter Zweikonten-Server-Smoke. Erst anschließend
   Installer-/Rollback-/Zweitrechnergates prüfen.

Bekannte Grenzen: drei native Opt-in-Skips; unvollständige VIS/CMB/SCF/NET-
Produktfeatures; keine Performance-/Shader-/Serverabnahme. Der Launcher fehlt,
die JAR-Entrypoints sind nicht verwendbar und eine lizenzgeprüfte Mod-only-
Distributionsform fehlt. An diesen Grenzen endet REL-P00.

## Berichte und vollständiger Evidenzindex

- [Integration und Build](REL_P00_INTEGRATION_AND_BUILD.md)
- [Lokaler Launcher](REL_P00_LOCAL_LAUNCHER_GATE.md)
- [Distribution und Rechte](REL_P00_DISTRIBUTION_AND_LICENSE.md)
- [Runtime-Matrix](LOCAL_RUNTIME_GATE.md), [Playtest-Scope](NEUTRON_PRIVATE_PLAYTEST_SCOPE.md)
- [Quellenmanifestprüfung](F:/neutron-rel-p00-evidence-20261028/source-verification.json)
- [Commitpins](F:/neutron-rel-p00-evidence-20261028/commit-preflight.json), [Dateivergleich](F:/neutron-rel-p00-evidence-20261028/integration-proof.json)
- [Toolchain](F:/neutron-rel-p00-evidence-20261028/toolchain.log)
- [Clean-Testlog](F:/neutron-rel-p00-evidence-20261028/01-clean-test.log), [94 XML-Summanden](F:/neutron-rel-p00-evidence-20261028/01-clean-test-summary.json)
- [Packaginglog](F:/neutron-rel-p00-evidence-20261028/02-package.log), [Packaging-XML-Summen](F:/neutron-rel-p00-evidence-20261028/02-package-summary.json)
- [JAR-Inhalte/Manifeste/Proben/Hashes](F:/neutron-rel-p00-evidence-20261028/jar-audit.json), [Paketabhängigkeitsproben](F:/neutron-rel-p00-evidence-20261028/packaged-dependency-probes.json)
- [Offizielle installierte Dateien](F:/neutron-rel-p00-evidence-20261028/installed-runtime-inventory.json)
- [Maven-Dependencylog](F:/neutron-rel-p00-evidence-20261028/03-dependencies.log), [Runtimebaum](F:/neutron-rel-p00-evidence-20261028/runtime-dependency-tree.txt), [Runtimehashes](F:/neutron-rel-p00-evidence-20261028/runtime-classpath-hashes.json)
- [Lizenzinventur](F:/neutron-rel-p00-evidence-20261028/direct-dependency-license-inventory.json)
- [Worktrees vorher](F:/neutron-rel-p00-evidence-20261028/worktrees-before.json), [nachher](F:/neutron-rel-p00-evidence-20261028/worktrees-after.json), [Vergleich](F:/neutron-rel-p00-evidence-20261028/worktrees-comparison.json)
- [Externer Review-Patch](F:/neutron-rel-p00-evidence-20261028/rel-p00-review.patch), [SHA256](F:/neutron-rel-p00-evidence-20261028/rel-p00-review.patch.sha256), [Endzustand](F:/neutron-rel-p00-evidence-20261028/final-state.json)

JAR-Listen, `javap`-Ausgaben, Entrypoint- und Paketabhängigkeitslogs liegen
ebenfalls im Evidenzordner; `artifact-manifest.json` führt deren SHA256 auf.
Die drei erzeugten JARs wurden nach allen Proben aus dem eigenen target in
den externen Unterordner `local-only-build` verschoben. Dieser Ordner ist
ausschließlich lokale Verwahrung und kein freigegebenes Testerpaket.
Der finale Berichtscommit und Patchhash werden erst nach dem Commit extern
festgehalten, um keinen sich selbst enthaltenden Hash zu behaupten.
**STOPP nach REL-P00; keine Folgewelle begonnen.**
