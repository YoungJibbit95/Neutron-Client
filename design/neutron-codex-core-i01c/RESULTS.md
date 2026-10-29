# CORE-I01c — Ergebnis

28.10.2026: Begrenzte automatische Attack-/Placement-Anbindung abgeschlossen,
Test-first-Verhaltens-RED und vollständige JDK-8-/Offline-Regression nachgewiesen.
Keine globale Action-Pipeline-Fertigstellung und kein Folgeauftrag.

## Lokale Basis und Git

| Punkt | Verifiziertes Ergebnis |
| --- | --- |
| Ausgangsbasis | ausschließlich lokaler Branch `codex/neutron-core-i01b` |
| Vollständiger Base-Commit | `6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666`, identisch zur lokalen Auflösung von `6676fbcb8` |
| I01b-Worktree | `F:\neutron-core-i01b`, bei Prüfung sauber; kein Edit |
| I01a-Worktree | `F:\neutron-core-i01a`, sauber, HEAD `eae42f43757c344784efdb53492c06eaf34f39ba`; nur gelesen |
| Neuer Branch / Worktree | `codex/neutron-core-i01c` / `F:\neutron-core-i01c` |
| Kollisions-/Initialprüfung | Branch und Pfad frei; `git worktree add -b ... codex/neutron-core-i01b`; danach leeres `git status --short` und Base-HEAD |
| Code-/Test-Commit | `b8c4b606dea386e7f62e30d9563f9950836ae44c` — `feat(core): admit automatic attacks and placements only in ordinary steps` |
| Berichtsabschluss | Direkter Folgecommit, ausschließlich die drei I01c-Berichte; voller End-Hash in der Chatübergabe bzw. `git rev-parse HEAD` |
| Endprüfung | Eigener Worktree nach Berichtscommit sauber; `git diff --check BASE..HEAD` und Dateiliste gegen die Auftragsgrenzen geprüft |

Ein Commit kann seinen eigenen Hash nicht in seinem Inhalt speichern.
Kein Fetch, Push, PR, Merge, Tag, Stash, Git-Clean, Git-Reset oder Branchwechsel
im Original. Keine fremden Dateien gestaged. Keine Subagents.

Auftrag und ZIP stammen aus den angegebenen Downloads-Pfaden. Das ZIP wurde
außerhalb sämtlicher Repositories nach
`F:\neutron-i01c-sources-20261028-110506` entpackt; zuerst wurde `00_START_HIER.md`
gelesen. Dort liegen auch Testlogs und XML-Sicherungen, nicht im Commit.
Quellenunterlagen sind Kontext, keine Freigabe weiterer v1.0-/R01-Migrationen.
Die lokalen APIs und Consumer wurden direkt gelesen; keine Signatur wurde geraten.
Die I01b-Paketberichte und lokalen Originalberichte stimmen nach Normalisierung
von CRLF/LF und abschließenden Leerzeilen textlich überein.

## Erhalt des ursprünglichen Checkouts

`F:\Puslar Backup - Copy` bleibt durch diesen Auftrag unbearbeitet auf
`prep/neutron-opencode-wave00`, HEAD `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`.
Der vollständige getrackte Binärdiff hat vor/nach der Arbeit denselben Fingerprint:
`a394935edbf56e31f30122e95de5c8214cd26a12`
(`git diff --no-ext-diff --binary | git hash-object --stdin`).
Die 147 vorbestehenden getrackten Änderungen wurden nicht angerührt.
Git selbst hat im neuen Worktree die getrackte Basis ausgecheckt; keine persönlichen
Laufzeitdaten aus dem alten Arbeitsverzeichnis wurden kopiert oder gestartet.

Eine vollständige Byteidentität aller **untracked** Dateien kann wegen beobachteter
paralleler Änderungen nicht behauptet werden: Beim Sicherungspunkt waren es
151 Statuszeilen / 155 geänderte oder untracked Dateien; ein späterer Vergleich
zählte 156 Dateien und genau diese Abweichungen:

- `design/intave-bypass-research-plan-2026-10-28.md`: vorhandene untracked Datei verändert.
- `design/intave-research-round-2-2026-10-28.md`: untracked Datei hinzugekommen.

Diese beiden Dateien wurden nur für die Erhaltungskontrolle gehasht, nicht inhaltlich
ausgewertet, geschrieben, gestaged oder restauriert; die fremde Arbeit bleibt
erhalten. Die getrackte Diffidentität ist davon unabhängig.
Bei der abschließenden Statuskontrolle wurde zusätzlich das neue untracked
Verzeichnis `design/anticheat-research/` beobachtet und ausschließlich als
Statuspfad erfasst. Der Originalcheckout wird offenbar parallel weiterbearbeitet;
I01c stellt diese fremden Änderungen nicht zurück.
I01a-/I01b-Worktrees und ihre Branch-HEADs blieben sauber und unverändert.

## Endgültiger Umfang und exakte Dateiliste

Fünf Produktionsdateien: genau die vier freigegebenen Module und eine neue,
25-zeilige zustandslose CORE-Brücke. Aura POST ist vor Scheduler/Autoblock gated,
PRE behält Targeting/Rotation, aber wiederholt keine Motion-Autoblock-Writes im Extra.
AutoClicker2 und HitDelayFix sichern nur ihre synthetischen Update-Klicks;
AutoClicker2 verändert bei Denial weder Timer noch künstlichen Use-Key-Zustand.
Scaffold prüft am Beginn `tryPlace` vor Service, Slot, Budget und Winkeldiagnose.
Die Brücke prüft aktuelle Scene, Step und Outer-ID über die unveränderte I01a-API.
Admission ist kein Budget, keine Reservation und kein Erfolgssignal.

```text
A src/main/java/me/youngjibbit/core/observation/AutomatedActionAdmission.java
M src/main/java/me/youngjibbit/module/impl/combat/Aura.java
M src/main/java/me/youngjibbit/module/impl/ghost/AutoClicker2.java
M src/main/java/me/youngjibbit/module/impl/ghost/HitDelayFix.java
M src/main/java/me/youngjibbit/module/impl/world/Scaffold.java
A src/test/java/me/youngjibbit/core/observation/AutomatedActionAdmissionTest.java
A src/test/java/me/youngjibbit/core/observation/AutomatedActionModuleTest.java
A src/test/java/me/youngjibbit/core/world/AutomatedPlacementAdmissionTest.java
M src/test/java/me/youngjibbit/core/world/ScaffoldClientFlowTest.java
M src/test/java/me/youngjibbit/core/world/LegacyScaffoldTest.java
M src/test/java/me/youngjibbit/core/combat/RotationModuleBindingChecks.java
A design/neutron-codex-core-i01c/ACTION_CONSUMER_MATRIX.md
A design/neutron-codex-core-i01c/ADMISSION_CONTRACT.md
A design/neutron-codex-core-i01c/RESULTS.md
```

Code-/Test-Commit: **11 Dateien, 558 Einfügungen, 6 entfernte Zeilen**;
Gesamtumfang einschließlich Berichten: **14 Dateien**.
Die drei bestehenden Testdateien mussten ihre direkten Walking-Aufrufe für
automatische Placement-Erwartungen um einen echten Ordinary-Observer-Scope ergänzen.
Die zwei zunächst übersehenen Binding-Fixtures wurden durch den ersten Gesamtlauf
nachgewiesen. Alle Assertions, Presets, Winkel, Schleifenlimits und erwarteten
Platzierungszahlen bleiben erhalten; keine Testerwartung wurde gelockert.
Details und konkrete Testmethoden stehen im Admission-Vertrag.

Keine Änderungen an `Minecraft`, `EntityPlayerSP`, Controller, `Tickbase`/
`TickbaseBudget`, Runtime-/Contractklassen, `AttackScheduler`, Placementservice,
`PacketPipeline`, RotationManager/-Corrections, MovementInput, POM/Dependencies,
Config, Assets, Defaults oder Spieldaten.

## Tatsächlich ausgeführte Tests

Alle Maven-Läufe im eigenen Worktree, prozesslokales `JAVA_HOME` auf
`C:\Program Files\Java\jdk-1.8`, **Oracle Java 1.8.0_391**, **Maven 3.8.5** aus
`C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5`.
Jeder Aufruf verwendet `-o`; kein Netzdownload und keine dauerhafte Java-Umstellung.
`javap -verbose` bestätigt **major version 52** für die neue Brücke;
`javap -private` bestätigt, dass sie keine Felder besitzt.

| Lauf / Log im externen Quellenverzeichnis | Tests run | Failures | Errors | Skipped | Ergebnis / Dauer |
| --- | ---: | ---: | ---: | ---: | --- |
| `01-baseline.log`: 8 bestehende Vertrags-/Observer-/Controller-/Scaffoldklassen, vor Produktion | 85 | 0 | 0 | 0 | GREEN / 01:47 min |
| `05-complete-red.log`: beide neuen Modultestklassen vor Produktionsänderung | 20 | 14 | 0 | 0 | erwartetes Verhaltens-RED / 32.935 s |
| `06-focused-green.log`: neue 29 Fälle + die 85 Gatefälle | 114 | 0 | 0 | 0 | GREEN / 01:11 min |
| `07-full-clean-test.log`: erster `mvn -o clean test` | 475 | 2 | 0 | 3 | zwei veraltete Placement-Fixtures / 02:09 min |
| `08-focused-regression.log`: 10 Klassen einschließlich beider korrigierter Prozesswrapper | 94 | 0 | 0 | 0 | GREEN / 31.450 s |
| `09-final-clean-test.log`: abschließender `mvn -o clean test` | **475** | **0** | **0** | **3** | **BUILD SUCCESS / 02:26 min** |

Der erste RED-Entwurf lief bereits mit 13/8/0/0; nach Entkopplung der Klick-Fixture
vom bestehenden Aura-Tickbudget waren es 13/10/0/0. Dadurch verdeckt dieses
Fachbudget den fehlenden Modulguard nicht. Ein Entwicklungszwischenlauf
`04-complete-red.log` hatte einen korrigierten checked-Exception-Deklarationsfehler
im Testhelper; er erreichte Surefire nicht und ist **kein** Verhaltens-RED-Nachweis.
Der maßgebliche RED-Lauf danach zeigt echte Assertions: zusätzliche C02-Versuche
beider Klickmodule, Aura-Scheduler-/Autoblock-Writes, Scaffold-C08/C09 sowie
veränderten Service-/Diagnosestatus bei ungültiger Admission.
Die neun zusätzlichen Brückentests wurden ebenfalls vor der Brücke geschrieben.

Der finale Clean-Lauf wurde unabhängig aus **88** frischen
`target/surefire-reports/TEST-*.xml` summiert: **475/0/0/3**, also
**472 nicht übersprungene Surefire-Testfälle ohne Failure/Error**.
Die 114er-XML-Sicherung enthält genau 11 Suiten; die finale 94er-Sicherung wurde
auf die zehn tatsächlich erneut ausgeführten Klassen gefiltert, damit alte XMLs
des vorherigen Gesamtlaufs nicht mitgezählt werden. Unterprozess-JUnitfälle der
bestehenden Harness-Wrapper werden nicht nochmals zur Surefire-Summe addiert.
Die historische I01b-Zahl 446/0/0/3 bleibt ein Vorbericht, kein neuer Basis-Gesamtlauf.

| Neue Testklasse | Testfälle im fokussierten und vollen GREEN |
| --- | ---: |
| `AutomatedActionAdmissionTest` | 9 |
| `AutomatedActionModuleTest` | 13 |
| `AutomatedPlacementAdmissionTest` | 7 |

Die drei Skips betreffen unverändert native Opt-in-Fälle:

- `WorldOverlayContractTest.nativeDispatchWhenRequested`
- `GuiChatInputTest.nativeInputAndDrawingWhenRequested`
- `GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings`

Kein Skip wird als bestanden gezählt. Keine GL-/Render-/Shader-/Serverabnahme.
Die finalen XMLs liegen im eigenen `target`; zusätzliche Sicherungen und Logs in
`F:\neutron-i01c-sources-20261028-110506` (`focused-surefire`, `first-full-surefire`,
`final-focused-surefire`, `final-surefire-totals.json`). Nichts davon wurde committed.

## Abdeckung, bewusst offene Pipeline und Abschlussgrenze

Nachgewiesen: Ordinary erreicht vorhandene Fachbudgets und native Controller;
Extra erzeugt keine neuen automatischen Aura-/Klick-/Scaffold-Versuche, auch nach
Ordinary im selben Outer-Tick. Withheld/History sind keine Berechtigung.
Frische Scene-/Epoch-/Identity-/Outer-ID-/GUI-/Pause-/Fokus-/Shutdown-Prüfungen,
Resume, wiederholte Abfragen ohne Reservation, Exception-/Early-Return-/Cancel-
Cleanup und unveränderte manuelle clickMouse-/windowClick-Pfade sind abgedeckt.
Reine Rotation/Movement bleiben im Extra aktiv; Denial setzt keine fremden Owner zurück.
PRE benutzt den vorher akzeptierten Winkel, POST aktuelle Acceptance; das
Placementbudget, Slot-Restore und Raycast-/Controllerablehnungen bleiben wirksam.

Grenze: Modulpaare benutzen den echten Observer mit einer synthetischen
Zwei-Callback-Entscheidung; die realen Tickbase-/Budget-/Cancellation-Hooks laufen
zusätzlich in den bestehenden I01b-Tests. Weltstorage/Input/Container sind Fixtures.
Ein laufender Step kann später abbrechen; Admission kann bereits gesendete Effekte
nicht rückwirkend verhindern. Existierende Service-/Callback-/Packetguards bleiben
erforderlich; unsichtbare Identitäts-ABA und spätere reentrante Änderungen sind
keine neue atomare Garantie dieses Piloten. Keine komplette Physik-/GameLoop-Session.

**Nicht migriert:** AutoHeal samt TickEvent/Inventartransfer, InventoryManager
im GuiInventory, Stealer im GuiChest, AutoTool, SlotHandler, FastPlace, MoreKB,
NoSlow und sonstige Input-/Packetmodifikatoren. GUI bedeutet häufig SUSPENDED,
AutoHeal liegt außerhalb des Playerbodys: Ordinary-only wäre dort fachlich falsch.
Stealer-Transaktionen und MoreKB-C0B bleiben beispielsweise ausdrücklich
**nicht vollständig durch Admission geschützt**. Die Consumer-/Deferred-/Manual-
Matrix enthält gesonderte Teststrategien; keine dieser Fachmigrationen wird behauptet.

Offen bleiben die bereits bestehenden Owner-/Keep-Rotation-/Timer-/Input-/Slot-
und numerischen CMB-/SCF-/MOV-/NET-/GUI-Gates. Keine releasePlacement-Bedingung
wurde umgedeutet, kein Produktdefault freigegeben, kein Folgeauftrag gestartet.

Reproduzierbare Endkontrolle im eigenen Worktree:

```powershell
git log --oneline 6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666..HEAD
git diff --name-status 6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666..HEAD
git diff --check 6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666..HEAD
git status --short
```

**STOPP nach CORE-I01c. Keine Pushes, Merges oder weiteren Änderungen.**
