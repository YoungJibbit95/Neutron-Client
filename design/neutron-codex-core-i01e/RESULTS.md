# CORE-I01e — Ergebnis

28.10.2026: Phase A und Phase B abgeschlossen. Die aktive Outer-Tick-ID ist
read-only verfügbar; AutoHeal startet eigene Heal-/optionale Transferaktionen
nur aus gültigem PRE-TickEvent vor der Playerentscheidung. Die kanonische
JDK-8-Offline-Abschlussregression besteht mit **521/0/0/3**.

## Basis und lokale Commits

| Punkt | Verifizierter Stand |
| --- | --- |
| Basisbranch / Basisworktree | `codex/neutron-core-i01d` / `F:\neutron-core-i01d`, vor Beginn sauber |
| Exakter Ausgangscommit | `ae23848a7a33a28e8b860f43ed5662d52de5fbde` |
| Neuer Branch / eigener Worktree | `codex/neutron-core-i01e` / `F:\neutron-core-i01e` |
| Kollisionsprüfung | Zielbranch und Zielpfad frei; Git erzeugt den neuen Worktree direkt auf dem genannten lokalen Commit |
| Phase-A-Commit | `70a4dbbbedb32d14df60eb76f146975761910264` — `feat(core): expose active outer tick lifetime read-only` |
| Phase-B-Code-/Test-HEAD | `882471c47c7dcd9ba25986c856084fe0097ea0bb` — `fix(autoheal): admit starts only in active outer tick events` |
| Berichtsabschluss | Ein direkter zusätzlicher Commit ausschließlich mit den drei I01e-Berichten; sein voller Endhash steht nach Commit in der externen `final-state.json` und in `git rev-parse HEAD` |

Ein Commit kann seinen eigenen Hash und den Hash seines vollständigen Patches
nicht im Commitinhalt speichern. Deshalb enthält der externe Abschlussnachweis
den endgültigen HEAD, Patchhash und die abschließende Dateiliste.

Auftrag, Blockerreview und ZIP stammen aus den genannten Downloads-Pfaden.
Das ZIP wurde nach `F:\neutron-core-i01e-sources` außerhalb aller Git-Worktrees
entpackt; zuerst wurde dessen `00_START_HIER.md` gelesen. I01d-Verträge und
Berichte sowie I01b-/I01c-Handoffs wurden vollständig gelesen, die Produkt-
spezifikation in den relevanten CORE-/NET-/MOD-Abschnitten. Maßgeblich bleiben
der ausdrückliche Auftrag und die tatsächlichen lokalen Java-Signaturen.
Quellenberichte erteilen keine zusätzliche Fachfreigabe.

## Gate A und Gate B

Phase A ergänzt ausschließlich einen Getter des vorhandenen Aktivitätszustands.
`getOuterTickId()` und sämtliche Hooks bleiben erhalten. Sechs neue Tests und
zwei zusätzliche Assertions im bestehenden Hooktest belegen die verlangten
Lebensdauergrenzen. Nach Compile-RED folgen 32 grüne Tests, sauberer Diffcheck,
Java-8-Bytecode und der Nachweis genau einer Produktions-Observerkonstruktion.
Erst danach wird Phase A committed und Phase B begonnen.

Phase B ergänzt eine private zustandslose Startprüfung in AutoHeal: kanonischer
Observer, aktive ID einschließlich 0, PRE-TickEvent vor Body/History/Withheld,
zwei frische gültige ACTIVE-Scenes samt abschließender Observer-/Tickprüfung.
Bestehende Fachguards bleiben zusätzlich erforderlich. Bindung, Pendingstatus,
ACK/Reject/Resync/Timeout, Restore und Worldcleanup laufen unabhängig davon.
Es gibt keine Tickprüfung in späteren Packetcallbacks und keinen Controller-
Interceptor. Details und B01–B14 stehen in `AUTOHEAL_TICK_ADMISSION.md`.

Die externe I01d-Sonde ist tatsächlich vorhanden und wurde gelesen; die neue
dauerhafte Testklasse übernimmt ihre sieben fehlgeschlagenen Anforderungen und
erweitert sie auf 40 Tests. I01ds historische 23/7/0/0 und 475/0/0/3 bleiben
historische Befunde. Alle sieben Lücken scheitern in den neuen I01e-RED-Läufen
und bestehen anschließend im fokussierten und vollständigen GREEN.

## Tatsächliche Testläufe

Alle Läufe verwenden prozesslokal `JAVA_HOME=C:\Program Files\Java\jdk-1.8`,
Oracle **1.8.0_391**, Maven **3.8.5** aus
`C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin\mvn.cmd`
und ausnahmslos **`-o`**. POM, Libraries, Compilerlevel und globale Umgebung
bleiben unverändert. `javap -verbose` bestätigt für beide geänderten
Produktionsklassen **major version 52**.

Logs, Laufkommandos, Laufzeiten und frische XML-Sicherungen liegen extern unter
`F:\neutron-core-i01e-sources`. `run_tests.py` speichert pro Lauf nur seit dessen
Beginn frisch geschriebene XMLs und summiert Tests/Failures/Errors/Skipped.

| Lauf / externe Logdatei ohne `.log` | Befehl nach `mvn -o` | Tests | Failures | Errors | Skips | Tatsächlicher Status |
| --- | --- | ---: | ---: | ---: | ---: | --- |
| `01-phase-a-red` | `test -Dtest=<A>` | — | — | — | — | erwartetes API-Compile-RED: 19 fehlende Methodenverwendungen, kein Surefirelauf |
| `02-phase-a-green` | `test -Dtest=<A>` | 32 | 0 | 0 | 0 | Gate A GREEN, 84.374 s |
| `03-phase-b-red` | `test -Dtest=AutoHealTickEventGateProbeTest` | — | — | — | — | Testentwurf: ein falscher EventPhase-Paketname; korrigiert, kein Verhaltens-RED |
| `04-phase-b-behavior-red` | gleicher Filter, AutoHeal unverändert | 37 | 17 | 0 | 0 | 16 fachliche Assertions plus ein Testvorbereitungsfehler |
| `05-phase-b-final-red` | gleicher Filter, AutoHeal unverändert | 40 | 19 | 0 | 0 | 18 fachliche Assertions plus derselbe Testvorbereitungsfehler |
| `06-phase-b-green` | gleicher Filter, mit Admission | 40 | 1 | 0 | 0 | alle 18 fachlichen REDs behoben; nur Vorbereitung des zweiten Transferdurchlaufs fehlerhaft |
| `07-focused-fixture-check` | `test -Dtest=<Fokus>` | 124 | 18 | 0 | 0 | sieben alte Transferfixtures ohne Tickscope; elf Folgefehler durch nach frühem Testabbruch subscribed gebliebene Instanzen |
| `08-focused-green` | `test -Dtest=<Fokus>` | 124 | 0 | 0 | 0 | Bestandsfixture minimal korrigiert; einschließlich aller 40 neuen Fälle GREEN, 33.340 s |
| `09-final-clean` | tatsächlich nur `test` | 521 | 0 | 0 | 3 | Vollregression GREEN; trotz Lognamen kein Clean-Lauf, 86.433 s |
| `10-final-clean` | tatsächlich `test clean` | 521 laut Log | 0 | 0 | 3 | falsche Zielreihenfolge; nach Tests target gelöscht, deshalb keine XML-Summe verfügbar; kein kanonischer Clean-Nachweis |
| `11-canonical-clean` | **`clean test`** | **521** | **0** | **0** | **3** | **maßgeblicher frischer Abschluss: BUILD SUCCESS, 150.166 s** |

`<A>` ist `RuntimeObservationActiveTickTest,RuntimeObservationTest,RuntimeObservationHookTest`.
`<Fokus>` ist
`AutoHealTickEventGateProbeTest,AutoHealSafetyTest,AutoHealInventoryTransferTest,RuntimeObservation*,AutomatedActionAdmissionTest,AutomatedActionModuleTest,AutomationEligibilityTest,SceneContextTest,GuardedInventoryClickTest`.
Die zwei fehlerhaften Gesamtlauf-Aufrufe wurden transparent durch den abschließenden
exakten `mvn -o clean test` ersetzt. Weitere Tests nach erfolgreichem Abschluss
wurden nicht benötigt.

Die kanonische Summe wurde zusätzlich unabhängig aus **allen 90 frischen**
`target/surefire-reports/TEST-*.xml` berechnet:
**521 Tests, 0 Failures, 0 Errors, 3 Skips**, also 518 nicht übersprungene
Surefirefälle ohne Failure/Error. Die 46 zusätzlichen Fälle sind sechs Active-ID-
und 40 Admissiontests. Wrapper-Unterprozessfälle werden nicht doppelt gezählt.
`final-independent-xml-sum.json` hält Summe und Skipnamen extern fest.

Die drei unveränderten Opt-in-Skips bleiben offen:

- `WorldOverlayContractTest.nativeDispatchWhenRequested`
- `GuiChatInputTest.nativeInputAndDrawingWhenRequested`
- `GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings`

Kein Skip wird als bestanden gezählt. Es gibt keine Spiel-, Server-, native
Maus-, GL-, Shader- oder Releaseabnahme.

## Exakte Dateiliste und Fixturegrenzen

Genau zwei Produktionsdateien, vier Testdateien und drei neue Berichte:

```text
M src/main/java/me/youngjibbit/core/observation/RuntimeObservation.java
M src/main/java/me/youngjibbit/module/impl/player/AutoHeal.java
A src/test/java/me/youngjibbit/core/observation/RuntimeObservationActiveTickTest.java
M src/test/java/me/youngjibbit/core/observation/RuntimeObservationHookTest.java
M src/test/java/me/youngjibbit/module/impl/player/AutoHealInventoryTransferTest.java
A src/test/java/me/youngjibbit/module/impl/player/AutoHealTickEventGateProbeTest.java
A design/neutron-codex-core-i01e/ACTIVE_OUTER_TICK_CONTRACT.md
A design/neutron-codex-core-i01e/AUTOHEAL_TICK_ADMISSION.md
A design/neutron-codex-core-i01e/RESULTS.md
```

Jede geänderte Bestandsfixturezeile hat einen begrenzten Zweck:
Im Observer-Hooktest kommen zwei Active-ID-Assertions hinzu (World-Unload erhält
Lifetime, Shutdown beendet sie). Der Transfer-Tickhelper umschließt jeden echten
synthetisch geposteten TickEvent mit Begin/End im finally. Dessen bestehender
Weltwechsel setzt zusätzlich `player.worldObj` auf die neue aktuelle World.
Alle bestehenden Assertions und Erwartungen bleiben erhalten.
Im neuen Schleifentest wird vor jedem unabhängigen Transferfall das Inventar
geleert; sonst ist der zweite Start fachlich ein Hotbar-Heal statt eines Transfers.

`AutoHealInventoryTransfer`, `Minecraft`, Player, Controller, RuntimeServices,
ClientRuntime, Scene-/Eligibilityverträge, Tickbase, PacketPipeline, übrige
Module, POM/Dependencies, Assets, Defaults und `test_run/**` bleiben unverändert.
Es gibt keine zusätzliche Hilfsklasse, öffentlichen GUI-API oder neuen Owner.

## Schutz bestehender Worktrees

Vor Erstellung des eigenen Worktrees wurden alle **28** vorhandenen Worktrees
read-only erfasst: vollständiger HEAD, vollständiger Porcelainstatus und SHA-256
des binären getrackten HEAD-Diffs. Nach der Implementierung wurden dieselben
Prüfgrößen erneut erfasst. `worktrees-before.json`, `worktrees-after.json` und
`worktrees-comparison.json` bestätigen **28 Vergleiche, keine Abweichung**.
Die Reads verwenden `git --no-optional-locks`; keine fremden Indexwrites sind nötig.

Der Originalcheckout `F:\Puslar Backup - Copy` bleibt auf
`prep/neutron-opencode-wave00`, HEAD `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`,
mit seinen **147** vorbestehenden getrackten Änderungen und seinen untracked
Forschungsdateien. Sein binärer Diff-SHA-256 ist vor/nach identisch:
`ea20915ab1fd52e816248910c54fb0c4731989e928e5d7d64eea59b4f0dcc88d`.
Auch I01a/I01b/I01c/I01d bleiben in allen erfassten Prüfgrößen unverändert.
Der Nachweis behauptet keine Byteidentität jedes untracked Dateiinhalts.
Fremde Arbeit wurde weder importiert noch restauriert; alle Tests benutzen das
eigene target und target/test-home. Git hat allein die getrackte Basis ausgecheckt.

## Commit-, Patch- und Abschlussstatus

Vor jeder Commitstufe werden Diffcheck und Allowlist geprüft und ausschließlich
die namentlich erlaubten Dateien gestaged. Es gibt genau zwei lokale Code-/Test-
Commits sowie einen abschließenden reinen Berichtscommit. Der eigene Endstatus
und der vollständige Diffcheck werden nach dem Berichtscommit erneut geprüft.

Der vollständige Review-Patch von der genannten Basis bis zum End-HEAD wird
byteerhaltend durch Python aus `git diff --binary BASE HEAD` nach
`F:\neutron-core-i01e-sources\neutron-core-i01e-review.patch` exportiert.
`final-state.json` dokumentiert SHA-256, endgültigen HEAD und exakte Dateiliste;
Reverse-Apply-Check, UTF-8/BOM-Prüfung und Allowlist werden extern protokolliert.
Der Patch und die Testartefakte werden nicht committed.

Keine Subagents, Fetches, Pushes, PRs, Merges, Tags, Resets, Stashes, Git-Cleans
oder Branchwechsel in fremden Worktrees. Die Maven-Clean-Schritte betreffen
ausschließlich das eigene target. Der Schreibskill wurde nur für klare Berichte
angewandt; seine optionale Delegation wurde entsprechend Nutzerauftrag nicht genutzt.

Zwei frische Scenes plus abschließende Tick-/Observerprüfung schützen die
getesteten sichtbaren Reentranzgrenzen. Beliebige unsichtbare Identitäts-ABA und
Änderungen nach der letzten Prüfung erhalten keine atomare Garantie. Die
bestehenden späteren Slot-/Stack-/Packetguards bleiben maßgeblich.

Geschlossen ist ausschließlich **CORE-OUTER-LIFETIME**. Offen bleiben
**MOD-SEQUENCE, MOD-GUI-CONTEXT, MOD-SUSPEND-REASONS, MOD-MANUAL-PRIORITY,
MOD-ACK-RETRY, MOD-DELAY-CLOSE und MOD-PHASE-CANCEL** sowie alle unberührten
CMB-/SCF-/MOV-/NET-/VIS-/Releasegates. Die 33 GUI-Handofftests bleiben Vorschläge.
**STOPP nach CORE-I01e; keine weitere Facharbeit.**
