# CORE-I01a — Ergebnis

28.10.2026: Architektur-Gate und isolierte CORE-0-Vertragstypen abgeschlossen.
Keine Runtime-Anbindung und keine Folgeaufgabe begonnen.

## Git und Arbeitsbasis

| Punkt | Tatsächlich geprüftes Ergebnis |
| --- | --- |
| Ursprünglicher Checkout | `F:\Puslar Backup - Copy`, Branch `prep/neutron-opencode-wave00` |
| Basiscommit | `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb` (lokal vorhanden; kein Remote-Ersatz) |
| W00-Nachweis | `NetworkManager.setCompressionTreshold` liest Encoder über `get("compress")`; W00-Regressionstests am Commit vorhanden. |
| Eigener Worktree | `F:\neutron-core-i01a`, vor Anlage nicht vorhanden, nach Anlage sauber |
| Eigener Branch | `codex/neutron-core-i01a`, vor Anlage nicht vorhanden |
| Endcommit des getesteten Code-/Vertragsstands | `14d9ab4b39945510c2d6eddbe75230122a393ba6` — `feat(core): add isolated scene and automation contracts` |
| Dokumentationsabschluss | Nachfolgender Commit enthält ausschließlich diese RESULTS.md; dessen exakter Hash steht in der Chat-Übergabe und ist mit `git rev-parse codex/neutron-core-i01a` abrufbar. Ein Commit kann seinen eigenen Hash nicht in seinem Inhalt speichern. |

Der ursprüngliche Checkout bleibt bei seinen 147 vorbestehenden Änderungen und
zwei untracked Berichtsverzeichnissen (`design/opencode-wave01/`, `design/opencode-core-r01/`).
Statusliste und Fingerprint des vollständigen getrackten Diffs wurden vor/nach der
Arbeit verglichen und sind identisch (nach Normalisierung der Ausgabe-Zeilenenden).
Diff-Fingerprint: `a394935edbf56e31f30122e95de5c8214cd26a12`, ermittelt über
`git diff --no-ext-diff --binary | git hash-object --stdin` in PowerShell.
Keine dieser Dateien wurde editiert, gestaged, kopiert, gestasht, gelöscht oder bereinigt.
Kein Push, PR, Merge, Tag, Remote-Fetch oder Branchwechsel im ursprünglichen Checkout.

Das angekündigte entpackte Quellenverzeichnis war nicht vorhanden; die bereitgestellte
ZIP wurde nach dem Lesen von `00_START_HIER.md` nach
`C:\Users\Administrator\Downloads\Neutron_2_0_Codex_01_Zusatzquellen` entpackt.
Es wurden keine Quellenpaket-Dateien oder AGENTS-Vorlagen ins Repository übernommen.

## Genaue Dateiliste

Alle neun Dateien sind **neu**; keine bestehende Datei wurde verändert:

```text
A design/neutron-codex-core-i01a/CONTRACT_DECISIONS.md
A design/neutron-codex-core-i01a/RESULTS.md
A src/main/java/me/youngjibbit/core/contract/AutomationEligibility.java
A src/main/java/me/youngjibbit/core/contract/ExecutedStep.java
A src/main/java/me/youngjibbit/core/contract/SceneContext.java
A src/main/java/me/youngjibbit/core/contract/WithheldObservation.java
A src/test/java/me/youngjibbit/core/contract/AutomationEligibilityTest.java
A src/test/java/me/youngjibbit/core/contract/ExecutedStepTest.java
A src/test/java/me/youngjibbit/core/contract/SceneContextTest.java
```

`CONTRACT_DECISIONS.md` enthält die zwölf RV-Dispositionen, korrigierte PRE-/POST-Sequenzen,
API-Grenzen und die auf CORE-0 beschränkte Testauswahl. Die vier Java-Klassen verwenden
nur Java-8-Standardtypen. Eine Suche nach dem neuen Paket in `src/main/java` trifft
ausschließlich diese vier Dateien: kein Produktionsconsumer wurde angebunden.
Minecraft, Gameplay-Hooks, RuntimeServices/ClientRuntime, POM, Configs, native Assets,
Bestandstests und numerische Fachwerte bleiben unverändert.

## Tatsächlich ausgeführte Tests

Prozesslokale Umgebung aller drei Maven-Läufe:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-1.8'
$taskMaven = 'C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin\mvn.cmd'
# Arbeitsverzeichnis: F:\neutron-core-i01a
& $taskMaven -o '-Dtest=SceneContextTest,ExecutedStepTest,AutomationEligibilityTest' test
& $taskMaven -o clean test
```

Verifiziert: Oracle Java `1.8.0_391`, Maven `3.8.5`, Compiler Source/Target `1.8` aus
unveränderter POM. `javap -verbose` auf `AutomationEligibility.class` bestätigt
`major version: 52`. Die anfängliche Standardumgebung verwendete Java 21 und ein
ungültiges JAVA_HOME für Maven; nur die Testprozesse wurden auf JDK 8 gesetzt.
Keine dauerhafte Systemumstellung, Netzdownloads, neuen Dependencies oder Upgrades.

| Lauf | Kommando | Ergebnis / echte Surefire-Zahlen | Exit / Dauer laut Maven |
| --- | --- | --- | --- |
| Test-first ROT, vor Produktivtypen | fokussiertes Kommando oben | Erwarteter `testCompile`-Fehler: SceneContext/ExecutedStep/AutomationEligibility fehlen. Surefire nicht erreicht; keine Testzahlen behauptet. | 1 / 01:22 min |
| Fokussiert GRÜN, nach Implementierung | fokussiertes Kommando oben | **Tests run: 24, Failures: 0, Errors: 0, Skipped: 0** | 0 / 01:00 min |
| Gesamtsuite GRÜN | `& $taskMaven -o clean test` | **Tests run: 420, Failures: 0, Errors: 0, Skipped: 3**, BUILD SUCCESS | 0 / 02:12 min |

Die Gesamtsuite wurde zusätzlich aus **83** `target/surefire-reports/TEST-*.xml`
aggregiert: 420/0/0/3; damit 417 nicht übersprungene Testfälle ohne Failure/Error.
Die 24 neuen Fälle sind in beiden grünen Läufen enthalten:

| Klasse | Tests run | Failures | Errors | Skipped |
| --- | --- | --- | --- | --- |
| AutomationEligibilityTest | 10 | 0 | 0 | 0 |
| ExecutedStepTest | 7 | 0 | 0 | 0 |
| SceneContextTest | 7 | 0 | 0 | 0 |

Übersprungen wurden jeweils ein Fall aus `WorldOverlayContractTest`,
`GuiChatInputTest` und `GuiOverlayDebugGlStringsTest` (native/offscreen Opt-in-Prüfungen).
Diese Skips sind keine bestandene Render-/GL-Abnahme. W00/W01 meldeten historisch
396 Tests mit 3 Skips; diese Zahlen werden hier nicht als eigene Läufe ausgegeben.

Vollständige neue Konsolenlogs außerhalb des Repositories:

```text
C:\Windows\Temp\neutron-core-i01a-20261028\01-test-first-red.log
C:\Windows\Temp\neutron-core-i01a-20261028\02-focused.log
C:\Windows\Temp\neutron-core-i01a-20261028\03-full-clean-test.log
```

Die Surefire-XML-Dateien des Clean-Gesamtlaufs liegen im eigenen Worktree unter
`target/surefire-reports/`; sie wurden nicht committed.

## Vier wesentliche Beobachtungen

1. ORDINARY und EARNED_EXTRA beschreiben betretene Player-Bodies. WITHHELD ist
   ein eigener Beobachtungstyp ohne Body, ausgeführten Step-Index oder Callback.
2. Scene-Vergleiche prüfen Connection/Player/World per Referenzidentität sowie
   Connection-Generation und Packet-Context-Epoch. Suspend bewahrt Identität;
   invalidierte, fehlende oder nicht aktuelle Kontexte erteilen keine Eligibility.
3. Eligibility ist deterministische Vorprüfung für automatische Modulaktionen.
   Wiederholte Abfragen reservieren nichts. Der neue Test mit dem realen reinen
   AttackScheduler bestätigt, dass dessen Budget nicht verbraucht oder aufgehoben
   wird. Placement-/Inventory-Budgets bleiben API-seitig zusätzliche Bedingungen;
   neue Runtime-Kompositionsprüfungen dafür sind nicht Teil dieses Auftrags.
4. Die manuelle Controllergrenze ist mit dem bestehenden reinen Schedulerpfad
   ohne Step getestet. Das ist kein neuer Minecraft.clickMouse-/Inventory-Hook-Test.
   PRE-/POST-/Timer-/Input-Phasen wurden dokumentiert, nicht umgebaut.

## Offene Grenzen und Abschluss

Die Typen können echte Laufzeit oder aktuelle Scene-Daten nicht selbst beweisen.
Ein späterer Adapter muss den tatsächlichen Tickbase-Entscheidungspunkt beobachten,
Steps nur für betretene Bodies erfassen und vor jedem Versuch den aktuellen
Tick/Scene-/Abbruchzustand liefern. Bei Scene-ABA muss die Epoch fortschreiten.
Schwache Identitäten laufen nach Freigabe der echten Scene-Objekte ab;
historische Snapshots halten Player/World/Connection nicht künstlich am Leben.
Suspend bewahrt keine bestätigte Aktionsberechtigung; Resume benötigt erneut
Scene-/Slot-/Plan-/Owner-Prüfung und akzeptierte Rotation.

Input-Snapshot bewusst nach MOV-1 verschoben. Timer-Prioritäten/TTL/Next-Frame-Hook,
Input-Arbitration und getrennte Movement-/C0B-Apply-Phasen, Rotation-Owner,
Tickbase-Consumer und Legacy-/Release-Migrationen bleiben PROPOSED bzw. offen.
G-CMB-SPEED (vier Yaw-/Pitch-°/s-Werte), G-SCF-ANGLES (acht Scaffold-Profile) und
das getrennte G-MOV-SPEEDBASE werden hier nicht freigegeben oder numerisch befüllt.
Keine Live-Server-, Spiel-, Shaderpack-, GUI- oder Release-Abnahme behauptet.

Abschlussprüfungen im eigenen Worktree: `git status --short` leer,
`git diff --check` ohne Befund; auch der gesamte eigene Commitbereich wird mit
`git diff --check 3ea55cbc34e14ad479454c6ce0d31e48e6f581fb..HEAD` geprüft.
Diff und Dateiliste sind reproduzierbar mit:

```powershell
git log --oneline 3ea55cbc34e14ad479454c6ce0d31e48e6f581fb..HEAD
git diff --name-status 3ea55cbc34e14ad479454c6ce0d31e48e6f581fb..HEAD
git diff 3ea55cbc34e14ad479454c6ce0d31e48e6f581fb..HEAD
```

Nächster sicherer Schritt ist die Sichtung dieser Ergebnisse und des lokalen Diffs
durch den Auftraggeber. CORE-I01a endet hier; kein weiterer Track wurde gestartet.
