# CORE-I01d — Ergebnis und STOPP-Gate

28.10.2026: Analyse-, Test- und Dokumentationsauftrag bis zum vorgeschriebenen
Code-Gate ausgeführt. **Die produktive AutoHeal-Admission ist blockiert und
nicht implementiert.** Kein „bereits sicher“-No-Op: sieben echte Assertions der
neuen externen Diagnosesonde zeigen fehlende Initiierungsbedingungen.
Der freigegebene Änderungsumfang erlaubt keinen vollständigen sicheren Fix.

## Basis, Worktree und Quellen

| Punkt | Tatsächlich verifiziert |
| --- | --- |
| Ausgangsbasis | ausschließlich lokaler Branch `codex/neutron-core-i01c` |
| Vollständiger Base-Commit | `382e995496af336aab07ca1cf6f8a41e4e54160c` |
| Basisworktree | `F:\neutron-core-i01c`, sauber; HEAD und lokaler Branch exakt gleich |
| Eigener Branch / Worktree | `codex/neutron-core-i01d` / `F:\neutron-core-i01d` |
| Erstellung | Zielpfad und Branch waren frei; `git worktree add -b codex/neutron-core-i01d F:\neutron-core-i01d codex/neutron-core-i01c`; neuer Worktree zunächst sauber und exakt auf Base |
| Lokale Commitstruktur | zwei Dokumentationscommits nach erfolgreicher Abschlussregression; erster Commit `9c4fb3c99e003eb1e19b7ccd4606073732fc614c` direkt auf der Basis, zweiter korrigiert den externen Exportpfad; keine Code-/Fixtureänderung |
| Vollständiger End-Commit | in der externen Abschlusskopie dieses Berichts und `F:\neutron-core-i01d-results\final-state.json`; im Git-Worktree `git rev-parse HEAD` |
| Quellenpaket | `F:\neutron-core-i01d-sources`, außerhalb aller Repositories; zuerst `00_START_HIER.md` gelesen |
| Artefakte außerhalb Git | genau drei Abschlussberichte und Review-Patch `F:\neutron-core-i01d-results\neutron-core-i01d-review.patch` in `F:\neutron-core-i01d-results`; Logs/XML/Sonde/Schutzsnapshots im externen Quellenverzeichnis |

Der Endhash kann nicht Bestandteil seines eigenen Commitinhalts sein.
Die externe Abschlusskopie wird nach dem Commit um dessen vollen Hash und den
Patchhash ergänzt. Die drei fachlichen Berichte werden zusätzlich wie im
Dateiauftrag vorgesehen unter `design/neutron-codex-core-i01d/` committed;
damit sind auch die im letzten Nutzerauftrag ausdrücklich außerhalb des
Repositories gewünschten Berichte eigenständig verfügbar.

Auftrag und letzte direkte Nutzeranweisung bestimmen den Scope.
Die allgemeine Produktspezifikation v1.0, Zusatzberichte und historische R01-
Vorschläge erweitern ihn nicht. Insbesondere sind weder frühere Architektur-
skizzen noch numerische CMB-/SCF-/MOV-Vorschläge Implementierungsfreigaben.
Das vollständige lokale I01c-Diff von
`6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666` bis zur Basis wurde geprüft:
zustandsloser Adapter, Aura/AutoClicker2/HitDelayFix/Scaffold, neue Tests und
alle drei geänderten Placement-Fixtures. Die bestehenden Assertions wurden
nicht abgeschwächt. Die vollständigen lokalen I01c-Berichte, I01a-Vertrag und
I01b-Hookvertrag wurden zusätzlich direkt gelesen.

## Code-Gate und bewusst unverändertes Verhalten

Die reale API bietet keinen lesenden Nachweis der **aktiven** Outer-Tick-Dauer.
`RuntimeObservation.endOuterTick()` setzt das private `outerTickActive` zurück,
lässt jedoch Tick-ID, Scene-Liveness und leere Bodyhistorie unverändert.
Ein aktiver TickEvent ohne Step und ein beendeter Outer-Tick sind daher über
`getOuterTickId`, `captureScene`, `isCurrent`, `getCurrentStep`, `getLastSteps`
und `getLastWithheld` im nachgewiesenen Fall nicht unterscheidbar.
Ein positiver Zähler plus frische ACTIVE-Scene wäre eine fehlerhafte Zulassung.
Ordinary-Eligibility würde dagegen den geforderten TickEvent-Piloten abschalten.

Der Auftrag verlangt bei nötiger Änderung an `RuntimeObservation` ausdrücklich
**STOPP vor dieser Änderung**. Deshalb kein Produktfix, keine nur scheinbar
vollständige Hilfsschicht und kein zweiter Observer/Tickmanager. Auch reine
Pause-/Nullobserver-Teilguards würden die beauftragte Eigenschaft nicht erfüllen;
es wird keine solche Teilmigration als Abschluss ausgegeben.

Die sieben unerfüllten Anforderungen der externen JUnit-Sonde sind:

| Diagnosefall | Erwünschte Eigenschaft / beobachteter Widerspruch |
| --- | --- |
| kein beobachteter Tick, ID -1 | Itemuse muss verweigert werden; synthetische Pakete entstehen |
| beendeter Tick, positive ID und ACTIVE-Scene | kein neuer Start; synthetische Pakete entstehen |
| kanonischer Observer fehlt | keine neue Automationsaktion; synthetische Pakete entstehen |
| Pause=true, GUI=null, Fokus=true | SUSPENDED muss Start verweigern; synthetische Pakete entstehen |
| Player.worldObj passt nicht zu mc.theWorld | invalidierte Scene muss Start verweigern; synthetische Pakete entstehen |
| Observer durch Minecraft.shutdown zurückgesetzt | kein neuer Itemuse; synthetische Pakete entstehen |
| optionaler Transfer vor beobachtetem Tick | kein neuer C0E; Klickstart wird beobachtet |

Gültige Starts ohne Step, späteres Withheld, Ordinary+Extra ohne TickEvent-Replay,
GUI/Fokus/Tod/Cursor/using-Guards, manuelle Slotänderung, Stackmutation,
Using-Reentranz, wiederholtes Scenesampling/Epochinvalidierung sowie laufender
Transfer-ACK/Reject/Timeout nach Tickende liefern in derselben Sonde erfolgreiche
Gegenproben. Die weitergehende Scene-/Epoch-/Callback-Policy ist damit nicht
als vollständig implementiert nachgewiesen.

## Test-first-Verlauf und echte Zahlen

Alle Mavenaufrufe im eigenen Worktree mit prozesslokalem
`JAVA_HOME=C:\Program Files\Java\jdk-1.8`, Oracle **1.8.0_391** und Maven **3.8.5**
unter
`C:\Users\Administrator\.m2\wrapper\dists\apache-maven-3.8.5-bin\5i5jha092a3i37g0paqnfr15e0\apache-maven-3.8.5\bin\mvn.cmd`.
Jeder Aufruf enthält `-o`. Keine Dependencies oder dauerhaften Umgebungsänderungen.

| Lauf | Tests | Failures | Errors | Skipped | Status |
| --- | ---: | ---: | ---: | ---: | --- |
| `01-baseline.log`, neun aktuelle Bestandsklassen vor Sondeninstallation | 72 | 0 | 0 | 0 | GREEN, 88,322 s inklusive Runner |
| `02-admission-probe.log`, neue echte Listener-/Observer-Sonde vor jeglichem Produktionsfix | 23 | 7 | 0 | 0 | erwartetes Verhaltens-RED, 32,606 s inklusive Runner |
| `03-final-clean.log`, unveränderte Produktsuite nach Entfernung nur der eigenen temporären Sonde, `mvn -o clean test` | 475 | 0 | 0 | 3 | BUILD SUCCESS, 206,715 s inklusive Runner |

Baselineklassen: `SceneContextTest` (7), `AutomationEligibilityTest` (10),
`RuntimeObservationTest` (15), `RuntimeObservationHookTest` (11),
`AutomatedActionModuleTest` (13), `AutoHealSafetyTest` (3),
`AutoHealInventoryTransferTest` (9), `GuardedInventoryClickTest` (1),
`InventoryManagerSafetyTest` (3). Summe 72, neun frische XMLs.

Der Clean-Lauf enthält **88 frische XMLs**, 475 Tests einschließlich drei Skips,
also **472 nicht übersprungene Tests ohne Failure/Error**. Die unveränderten
Opt-in-Skips sind `WorldOverlayContractTest.nativeDispatchWhenRequested`,
`GuiChatInputTest.nativeInputAndDrawingWhenRequested` und
`GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings`.
Kein Skip zählt als bestanden. Die historische I01c-Zahl ist damit durch einen
eigenen neuen Lauf bestätigt, ohne die offene Admission als grün auszugeben.
`javap -private` bestätigt die fehlende öffentliche Tickaktivitäts-API in
`observer-javap.txt`; der kompilierte unveränderte AutoHeal hat major version 52
(`java-bytecode.txt`).

Die neue Sonde verwendet die bestehenden AutoHealInventoryTransferTest- und
RotationBehaviorHookTest-Fixtures, den kanonischen RuntimeObserver, echten
AutoHeal-Listener, reale Controller-/Packetguards und EmbeddedChannel.
Worldstorage/Health/Input sind synthetisch; es gibt keine zweite Clock oder
Produkt-Sceneimplementierung, keine Mocking-Library und keine neuen Dependencies.
Ihre Sicherheitsassertions laufen wirklich durch Surefire und scheitern am
Verhalten, nicht am Compiler. Es gab keinen `testCompile`-Fehler als RED-Ersatz.

Nach diesem Nachweis greift das Stop-Gate. Die einzige vorübergehend im neuen
Worktree hinzugefügte Datei war
`src/test/java/me/youngjibbit/module/impl/player/AutoHealTickEventGateProbeTest.java`.
Ihr unveränderter vollständiger Quelltext bleibt extern unter
`F:\neutron-core-i01d-sources\AutoHealTickEventGateProbeTest.java` erhalten.
Vor der sauberen Abschlussregression wurde ausschließlich diese eigene untracked
Sondendatei entfernt. Kein bestehender Test wurde gelöscht, ignoriert, geändert
oder abgeschwächt. Es gibt kein behauptetes RED→GREEN für die offene Admission.
Die grüne Vollsuite ist ein Regressionsnachweis der unveränderten Basis.

Reproduktion der Diagnose im eigenen sauberen Testworktree: die externe Sonde
temporär in ihr gleichnamiges Testpaket kopieren und
`mvn -o test -Dtest=AutoHealTickEventGateProbeTest` mit dem genannten JDK/Maven
ausführen; die sieben Fehlassertions bleiben erwartet, solange der APIblocker
und die Eintrittsguards unverändert sind. Danach nur diese eigene temporäre
Datei entfernen. Die externe Sonde wird nicht als erfüllte Produktregression
in den Review-Patch aufgenommen, entsprechend dem dokumentierten STOPP-Scope.
`reproduce_probe.py` automatisiert genau diese temporäre Installation im
festen eigenen Worktree und entfernt nur eine bytegleich unveränderte eigene
Sondendatei; der tatsächlich ausgeführte Originalnachweis bleibt im 02-Lauf.

`run_tests.py` sichert pro Lauf ausschließlich nach Startzeit frisch geschriebene
`TEST-*.xml` in ein eigenes externes Verzeichnis und summiert deren Tests,
Failures, Errors und Skips in `01-baseline-totals.json`,
`02-admission-probe-totals.json` und `03-final-clean-totals.json`.
Der finale Clean-Lauf beginnt nach vollständigem Löschen des eigenen Maven-targets;
alte Diagnose-XMLs werden nicht mitgezählt. Unterprozess-JUnitfälle vorhandener
Wrapper werden nicht nochmals zur Surefire-Summe addiert.

## Ergebnispaket und Allowlist

Der finale Review-Diff enthält ausschließlich diese drei **neuen** Dateien:

```text
design/neutron-codex-core-i01d/TICK_EVENT_ADMISSION_CONTRACT.md
design/neutron-codex-core-i01d/GUI_INVENTORY_HANDOFF.md
design/neutron-codex-core-i01d/RESULTS.md
```

Kein Produktcode und kein bestehendes Test-Fixture wird committed.
AutoHeal sowie alle verbotenen Hot Files, GUI-/Inventarmodule, POM,
Dependencies, Assets, Configs und `test_run/**` bleiben unverändert.
Der TickEvent-Vertrag enthält den lokalen Callgraph, die vor Produktcode
definierte Phasenmatrix, Fach-/Cleanup-/Callbackgrenzen und den konkreten Blocker.
Der GUI-Handoff enthält **33** konkrete Fälle mit Kontext/Phase/Operation/Soll/
tatsächlichem Nachweisstatus und acht benannte offene Gates; Mehrklicksequenzen,
manuelle Konkurrenz und AutoClose sind ausdrücklich keine heimliche Einklickmigration.

Der externe Patch wird aus den tatsächlich committed eigenen Änderungen mit
`git diff --binary 382e995496af336aab07ca1cf6f8a41e4e54160c..HEAD` byteerhaltend
exportiert. UTF-8/BOM, Patchhash, Dateiliste und Reverse-Apply-Check werden in
der externen Abschlusskopie protokolliert. Kein Patch wird committed.
Der erste Exportversuch direkt nach `F:\` scheiterte mit einem Dateisystem-
`PermissionError`; dort entstand keine Patchdatei. Der beschreibbare externe
Ergebnisordner wird stattdessen verwendet; dazu wurde nur dieser Bericht in
einem zweiten lokalen Dokumentationscommit präzisiert.

## Git-Schutz und Abschlussgrenze

Vor Beginn wurden alle **27** vorhandenen Worktrees read-only erfasst:
voller HEAD, vollständiger Porcelainstatus, SHA-256 des getrackten binären
HEAD-Diffs und des Indexdiffs in `before-protection.json`.
Der originale Checkout bleibt auf `prep/neutron-opencode-wave00`,
HEAD `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb`, mit **147** vorbestehenden
getrackten Änderungen. Sein anfänglicher binärer Diffhash ist
`ea20915ab1fd52e816248910c54fb0c4731989e928e5d7d64eea59b4f0dcc88d`;
der Index ist leer. Untracked Forschungsdateien wurden nicht inhaltlich
ausgewertet, geschrieben, importiert oder restauriert.
Der neue Worktree wurde allein durch Git aus der lokalen Basis befüllt.
Testprozesse verwenden sein eigenes target und target/test-home.

Abschlussvergleich aus `after-protection.json`: alle **27** vorhandenen
Worktrees stimmen in HEAD, vollständigem Porcelainstatus, getracktem binärem
Diffhash und Indexdiffhash mit dem Ausgangssnapshot überein (`changes: {}`).
Auch Originalcheckout und I01a-/I01b-/I01c-Worktrees sind damit in diesen
Prüfgrößen unverändert; die 147 fremden Originaländerungen bleiben erhalten.
Eine Status-/Diffkontrolle beweist keine Byteidentität aller unveränderten
untracked Dateien; fremde parallele Arbeit wird weder zurückgesetzt noch
als eigene Änderung ausgegeben. Der externe Schutzvergleich benennt
beobachtete Abweichungen statt sie zu überschreiben.

Vor Commit: erfolgreiche Bestands-/Volltests und eigene Allowlistkontrolle.
Nach Commit: sauberer eigener Status, `git diff --check BASE..HEAD`, exakte
Dateiliste, byteerhaltender externer Patch und dessen Reviewprüfung.
Keine Subagents, Fetches, Pushes, PRs, Merges, Tags, Resets, Stashes oder Git-Cleans.
Keine fremden Dateien gestaged. Keine Spiel-/Server-/GL-/Shader-/Releaseabnahme.
**STOPP nach CORE-I01d am dokumentierten Code-Gate; kein Folgeauftrag.**
