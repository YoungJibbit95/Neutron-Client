# SCF-1a — Ergebnis und Evidenz

28.10.2026: Aktive Scaffold-Owner-Kontinuität über Planende/Planlücken und
source-korrektes Rotation-ON-Placement sind implementiert. Rotation OFF behält
den tatsächlich lokal akzeptierten Camera-/Vanilla-Pfad. Der frische Abschlusslauf
**`mvn -o clean test` meldet 567 Tests, 0 Failures, 0 Errors, 3 Skips**;
93 frische Surefire-XMLs bestätigen die Summe. 564 nicht übersprungene Fälle haben
keinen Failure/Error. Dies ist keine native Spiel-/Server-/AC-/Shaderabnahme.

## Basis, Branch und Grenzen

| Punkt | Tatsächlicher Stand |
| --- | --- |
| Read-only-Basisworktree | `F:\neutron-cmb-1a`, Branch `codex/neutron-cmb-1a`, sauber |
| Exakter Basis-HEAD | `88ee497d8fe2dca16e5eaae9e48548c7d46e7050` |
| CMB-API / Tests | `canUseRotation(String,boolean)` und 21 Tests in RotationOwnerPredicateTest vorgefunden und ausgeführt |
| Eigener Worktree / Branch | `F:\neutron-scf-1a`, `codex/neutron-scf-1a`, ab exakt diesem lokalen Commit; Pfad und Branch vorher frei |
| Initialer eigener Zustand | HEAD gleich Basis, kein eigener Diff/Status; Sparse-Checkout `/*`, `!/test_run/`, `!/logs/` übernommen; beide ausgeschlossenen Verzeichnisse fehlen |
| Code-/Test-Commit | `3a4ed6ba11a5eabc59f72283183f87c4af87e022` — `feat(scaffold): retain accepted owner across active plan gaps` |
| Dokumentationscommit | Ein anschließender Commit mit genau den drei Berichten; eigener endgültiger HEAD und Hash stehen nach Commitbildung extern in `F:\neutron-scf-1a-sources\final-state.json` und in der Chatantwort |

ZIP extern nach
`C:\Users\Administrator\Downloads\Neutron_2_0_Codex_08_SCF_1a_Zusatzquellen`
entpackt, zuerst Startdatei/Manifest, dann den vollständigen eingebetteten Masterprompt
gelesen; alle 19 Größen-/SHA-256-Einträge stimmen. Kein Quellcode aus Fremdclients
übernommen. Keine Subagents, kein Folgeauftrag. Keine Fetch-/Push-/PR-/Merge-/Rebase-/
Tag-/Stash-/Reset-/Git-Clean-Operation. Mavens Clean betrifft das eigene `target`.
Keine POM-, Dependency-, Ressourcen-, Config-, CMB-, CORE-, NET-, VIS- oder
MoveFix-Produktionsänderung. Keine fremden Saves/Accounts/Testwelten in den eigenen
Worktree oder in Commits übernommen.

## Vollständige Dateiliste gegenüber der Basis

| A/M | Datei |
| --- | --- |
| M | `src/main/java/me/youngjibbit/module/impl/world/Scaffold.java` |
| M | `src/main/java/me/youngjibbit/core/world/ScaffoldPlacementService.java` |
| M | `src/test/java/me/youngjibbit/core/combat/RotationModuleBindingChecks.java` |
| M | `src/test/java/me/youngjibbit/core/world/LegacyScaffoldTest.java` |
| A | `src/test/java/me/youngjibbit/core/world/ScaffoldOwnerContinuityTest.java` |
| A | `src/test/java/me/youngjibbit/core/world/ScaffoldOwnerPlacementIntegrationTest.java` |
| A | `design/neutron-codex-scf-1a/SCF_OWNER_PLAN_CONTRACT.md` |
| A | `design/neutron-codex-scf-1a/SCF_1B_HANDOFF.md` |
| A | `design/neutron-codex-scf-1a/RESULTS.md` |

## Implementierung und bewusst geänderte Tests

Plan-Cleanup, Source-Ende und kompletter Context-Cleanup sind getrennt. Der
Scene-gebundene Wunsch wird ausschließlich aus tatsächlich bestätigter Scaffold-Pose
gewonnen und in zulässigen Motion-PREs erneuert. Erfolgreiches PRE-Placement erneuert
ihn vor dem Return; POST beendet nur den Plan. Nach Cancel/Delay/Rewrite/Replay oder
Resume ist ein erneuter Wunsch keine Authority; passende lokale C03-Acceptance bleibt
Pflicht. Neue Ziele nutzen unveränderte Profile. OFF/Disable/harter Contextverlust
verwerfen den Wunsch, Disable gibt im SCF-1a-Interim sofort hart frei.

Der additive Serviceaufruf prüft bei ON die Source vor Slot/Budget und im späten
Controller-/Packetguard. Der alte numerische Aufruf bleibt erhalten. Admission bleibt
erste Voraussetzung, auch für den ersten Ordinary-Versuch; Extra/No-Step/Withheld
erhalten dadurch keine Aktionen. Späte Callbacks prüfen zusätzlich denselben
Modulplan/Frame und dieselbe Scene. `finally` schont manuelle Slots und Ersatzscenes.
Timerrelease prüft altes Objekt und eigenen Wert; vorhandene MoveFix-Mathematik bleibt.
Sceneprüfung und Ausführung lesen denselben aktuellen Minecraft-Singleton.

Nur zwei vorhandene Testmethoden wurden bewusst migriert:

- `RotationModuleBindingChecks.java:315` heißt nun
  `invalidScaffoldSupportKeepsAcceptedMovementUntilDisable`.
  Die alte Null-Owner-/Kamera-Bewegungserwartung bei aktivem Supportverlust
  (`Basis:330–332`, jetzt `:331–337`) wurde in bestätigten Scaffold-Owner und
  aus der gehaltenen Yaw berechnete Bewegung überführt. Die bisherigen Kamera-
  Bewegungsassertions stehen unverändert hinter einem expliziten Disable;
  unveränderte Stackgröße und Cleanup bleiben. Keine Formeländerung.
- `LegacyScaffoldTest.java:76` heißt nun
  `settingChangesInvalidateThePlanButOnlyRotationsOffReleasesOwnership`.
  Die kombinierte alte Erwartung (`Basis:82`, jetzt `:82–87`) wird getrennt:
  Round-Rots-Änderung beendet den Plan und behält den Owner; Rotation OFF entzieht
  ihn hart. Danach wird ON für die weitere Fixture wiederhergestellt.
  Die bisherigen Plan-/Nichtplatzierungsassertions bleiben erhalten.

`ScaffoldPlacementTest.rotationsOffStillRequiresActualAcceptedView` blieb vollständig
unverändert. Auch `scaffoldPriorityIsReleasedToAuraAfterTargetLossOrDisable` bleibt
unverändert: Seine Fixture besitzt keinen gültigen Gameplaykontext und prüft dort
weiterhin korrekt harte Invalidation. Der neue aktive Vertrag wird separat mit
echten gebundenen Walking-/Controller-/Packetpfaden geprüft. Admission-, Legacy-
Fixture- und sonstige bestehende Testdateien wurden nicht geändert.

## Test-first, Zwischenergebnisse und frische Abnahme

Alle Mavenaufrufe liefen mit `-o`, prozesslokalem
`JAVA_HOME=C:\Program Files\Java\jdk-1.8` (Oracle **1.8.0_391**), Maven **3.8.5**
aus dem vorgegebenen Wrapperverzeichnis, unverändertem POM und JUnit 4.13.2.
Keine dauerhafte Umgebungsänderung, kein -Xint, kein Test-Deaktivieren.
Kommandos/Zeiten/Exitcodes und frische XML-Kopien sind extern gesichert.

| Lauf / externe Evidenz | Tests / Failures / Errors / Skips | Bedeutung |
| --- | --- | --- |
| Historischer CMB-Bericht | 542 / 0 / 0 / 3 | Nur vorheriger Bericht, keine SCF-Abnahme |
| `baseline.log`, `baseline-xml/` | 95 / 0 / 0 / 0 | Eigene fokussierte Baseline vor erstem Edit, BUILD SUCCESS |
| `red-initial-xml/` | 19 / 13 / 0 / 0 | Erster neuer Testlauf; enthält die anschließend reparierte OFF-Fixture |
| `red.log`, `red-xml/` | 19 / 12 / 0 / 0 | Vor Produktionsänderung reproduzierte Assertions; keine Compile-REDs |
| `continuity-first.log` | 14 / 2 / 0 / 0 | Zusätzliche echte Observergenerationgrenze; siehe Einschränkung unten |
| `wish-red.log`, `wish-red-xml/` | 2 / 2 / 0 / 0 | Resume-/Reject-Wunsch bis frischer Acceptance fehlte im ersten Implementierungsstand |
| `timer-red.log` | 1 / 1 / 0 / 0 | Eigener unveränderter Timerwert blieb bei Epochcleanup stehen; anschließende Objektbindung repariert dies |
| `callback-red.log/.json`, `callback-red-xml/` | 1 / 1 / 0 / 0 | Planende im Settingscallback ließ trotz Source-Hold einen Klick zu; durch Plan-/Frame-Recheck geschlossen |
| `focused-complete.log/.json`, `focused-complete-xml/` | **120 / 0 / 0 / 0** | Letzte vollständige fokussierte Auswahl, 13 frische XMLs, BUILD SUCCESS |
| `full.log/.json`, `full-xml/` | **567 / 0 / 0 / 3** | **`mvn -o clean test`**, Exit 0, **93 frische XMLs**, BUILD SUCCESS |
| `continuity-standalone.log/.json` | **18 / 0 / 0 / 0** | Neue Owner-Continuity-Klasse separat grün |
| `placement-standalone-final.log/.json` | **7 / 0 / 0 / 0** | Neue Owner-Placement-Klasse separat grün |

Der initiale OFF-Test verlor seinen injizierten Plan durch den echten Settings-Cleanup;
die Fixture setzt diesen nach OFF erneut, bevor sie die akzeptierte Kamerapose prüft.
Erst der bereinigte Lauf zählt als fachlicher Nachweis. Eine frühe zusätzliche
`correctionEnabled()==false`-Erwartung nach Invalidation war zu stark: Das Flag allein
ist kein Authority-Predicate. Sie wurde im **neuen** Test durch wirkliche
MoveFlying-/Kamera-Fallbackassertions ersetzt und wird nicht als behobener
Produktionsfehler gezählt; der eigenständige Observergeneration-RED bleibt belegt.
Die 12 anfänglichen Fehler werden deshalb nicht pauschal als zwölf Produktbugs verkauft.
Keiner der REDs beruhte auf einer fehlenden neuen Java-Signatur.

Zwischenläufe `focused-first.log` (75/15/0/0), `focused-second.log` (79/1/0/0),
`focused-green.log` (119/0/0/0) und `focused-final.log` (120/1/0/0) bleiben extern
erhalten. Der letzte Zwischenfehler trat beim wiederholten Fixture-Clientwechsel auf;
frische Clientreferenzen in Sceneprüfung und Ausführung beseitigen die Inkonsistenz.
Der Replay-Test wurde zusätzlich an den tatsächlichen nicht rotierenden ersten C03
angepasst: Er behauptet keine sofortige Source-Bestätigung vor dem folgenden
passenden Look. Diese Zwischenläufe ersetzen die finale saubere Abnahme nicht.

Der vollständige Lauf startete am 28.10.2026 um **14:55:03 Europe/Berlin** und
endete um **14:57:45**, Mavenzeit **2:39 min**, Exit **0**, letzte Buildmeldung
**BUILD SUCCESS**. `run-check.ps1` übernimmt nur XMLs mit Änderungszeit ab Runbeginn;
zusätzlich entfernt `clean` alle alten eigenen Target-Ergebnisse vor der Vollregression.
Die bestehenden verschachtelten Native-Fixture-Wrapper behalten ihre bisherige
Zählweise; Kindtestausgaben werden nicht zusätzlich auf Surefire-Summen addiert.

Die drei bestehenden Skips sind ausdrücklich offen:

- `WorldOverlayContractTest.nativeDispatchWhenRequested` — Native-Opt-in nicht aktiv.
- `GuiChatInputTest.nativeInputAndDrawingWhenRequested` — Native-Opt-in nicht aktiv.
- `GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings`
  — „Opt-in native offscreen check“.

`javap -verbose` bestätigt für **Scaffold und ScaffoldPlacementService major version 52**;
die vollständigen Ausgaben liegen extern als `javap-Scaffold.txt` und
`javap-ScaffoldPlacementService.txt`. `git diff --check` wurde für den vollständigen
Basis..End-HEAD-Bereich ausgeführt; finale Exitcodes stehen in `final-state.json`.

## Geschützte Worktrees und parallele Abweichung

Vorher-/Nachherdaten stehen in `worktrees-before.txt`, den `before-/after-*-head.txt`,
Statusdateien, normalisierten SHA-256-Diffmetadaten und `protected-comparison.json`
im externen Evidenzordner. Leere initiale Git-Pipelines erzeugten keine Status-/
Diffdatei; die Auswertung behandelt diesen dokumentierten leeren Output als leer.
Eigene vollständige fremde Diff-Zwischenkopien wurden nach Hashbildung entfernt.

Alle **31** schon vorher registrierten fremden Worktrees wurden ausschließlich
gelesen. CMB, fünf CORE-Worktrees und VIS behalten exakt ihre vorherigen HEADs,
leeren Status und identischen getrackten Diffhashes; die übrigen früheren Worktrees
haben ebenfalls identische HEAD-/Status-/Diffmetadaten, mit folgender Ausnahme:

**Der Originalcheckout wurde parallel außerhalb dieser SCF-Arbeit gewechselt.**
Vorher `prep/neutron-opencode-wave00`, HEAD
`3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`; nachher
`codex/revived-final-integration`, HEAD
`eeddb7da58978f04fad95ad8042175b7ad848d6a`.
Sein Reflog nennt einen Checkout am **28.10.2026 14:58:07 +01:00**.
SCF führte dort keinen Checkout oder Edit aus und repariert/ändert diesen fremden
Zustand nicht. Die 147 vorbestehenden getrackten Änderungen und 17 untracked Einträge
haben identische Statusmetadaten und identischen relativen getrackten Diffhash
`40e8e83dc99e3f965d56d3d23a43dc39eb2e0e2322d78b3cee5dbf4110b2f49f`.
Wegen des fremden HEAD-Wechsels wird ausdrücklich **kein unveränderter Original-HEAD**
und keine allgemeine Byteidentität des Originalbaums behauptet. CMB-Basis und eigener
isolierter Branch sind davon unberührt. Untracked Researchinhalte wurden nicht gehasht;
gleiche Statusnamen sind kein Nachweis ihrer Byteidentität.

## Review-Patch und Ende

Nach dem Dokumentationscommit wird ausschließlich extern
`F:\neutron-scf-1a-sources\SCF_1a_review.patch` erzeugt:

```text
git diff --binary 88ee497d8fe2dca16e5eaae9e48548c7d46e7050..HEAD
git apply --check --reverse F:\neutron-scf-1a-sources\SCF_1a_review.patch
```

Der Patch enthält den gesamten Basis..End-HEAD-Diff einschließlich dieser drei Berichte.
End-HEAD, Patch-SHA-256, `diff --check`-/Reverse-Apply-Exitcodes, Dateiliste und sauberer
Endstatus werden nach Commitbildung in **`F:\neutron-scf-1a-sources\final-state.json`**
festgehalten. Dies vermeidet eine unmögliche Selbstreferenz des Patchhashes in seinem
eigenen Inhalt. Patch/Logs/XMLs/Quellenpaket sind nicht Teil der Commits.

Offen bleiben SCF-1b/H1-06 mit 2–4-Tick-Auslauf, weitere CMB-/MoveFix-Consumerarbeit,
`G-CMB-SPEED`, `G-SCF-ANGLES`, `G-MOV-SPEEDBASE` sowie native/Server-/AC-/Shader-/
Framezeit-/Releasegates. Kein fertiges AC-R03-Urteil vorgefunden, keine Freigabe erfunden.
Details und echte Testzuordnung: `SCF_1B_HANDOFF.md`. Nach diesem Schnitt STOPP.
