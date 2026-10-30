# Phase 3 — Runtime- und CI-Abnahme

30.10.2026, Europe/Berlin. Ergebnis: **Java-8-CI und lokale Native-Regression
PASS; begrenzte echte Spielprüfungen PASS; vollständige Spiel-/Serverfreigabe
weiter BLOCKED. Kein Merge und kein Release.**

## Stand und Stack

| PR | Basis | Head beim Beginn dieser Phase | Status |
|---|---|---|---|
| [#15](https://github.com/YoungJibbit95/Neutron-Client/pull/15) | main, 07b2e827ba935e31b9fb73125471ef8060d71f2e | 5504fbb50ea8e2cbfec72abcbea260a0c1ac2025 | offen, Draft, unverändert |
| [#16](https://github.com/YoungJibbit95/Neutron-Client/pull/16) | Branch von #15 | e92676aa008ec0c0a78dd7211e2e32a0e58e46c6 | offen, Draft, unverändert |
| [#17](https://github.com/YoungJibbit95/Neutron-Client/pull/17) | Branch von #16 | CI-Commit b6f2fc7e428a459464a081f690583bf704793acd; danach nur dieser Bericht | neuer eigener Draft |

Aufgabenbranch: `codex/cmb-scf-phase3-acceptance-20261030`. Die vorhandenen
Feature-Implementierungen, Java-Tests und pom.xml sind gegenüber #16 unverändert.
Es gab keinen reproduzierten neuen Featurefehler und deshalb keinen Featurefix.
Die alten Berichte und Testnachweise bleiben erhalten.

## Ursache der fehlenden CI und Korrektur

GitHub REST meldete Actions enabled=true und allowed_actions=all. Registriert war
genau ein Workflow: traffic-metrics.yml mit schedule und workflow_dispatch.
Die beiden vorhandenen erfolgreichen Läufe waren Badge-Läufe, keine Clienttests.
Auf den ursprünglichen Heads von #15 und #16 gab es null Check-Runs. Die fremde
ungetrackte launcher-windows.yml ist nicht veröffentlicht und bleibt unberührt.

Neu sind ausschließlich:

- `.github/workflows/java8-pr.yml`: pull_request opened/synchronize/reopened/
  ready_for_review, ohne Draft-Ausschluss oder main-only-Filter; Windows 2022,
  Temurin 8.0.482+8 x64, Maven 3.9.15 aus SHA512-geprüftem Apache-Archiv,
  festgelegte Action-Commit-SHAs, contents:read, keine gespeicherten Checkout-
  Credentials, frisches Maven-Repository und archivierte Testnachweise.
- `.github/scripts/check-java8-results.ps1`: zählt nur frische Surefire-XMLs,
  verweigert leere/fehlerhafte Ergebnisse und unbekannte Skips. Hosted CI erlaubt
  genau die drei bestehenden, unten benannten Native-Opt-ins; RequireNative
  verlangt null Skips. Sieben positive/negative Kontrollen bestanden.

Der [pull_request-Trigger](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#pull_request)
prüft den PR-Mergestand; damit ist auch der gestapelte PR erfasst. Es wird kein
pull_request_target mit privilegiertem Fremdcode verwendet. Weder Repository-
Berechtigungen noch andere Workflows wurden verändert.

## Tatsächlicher GitHub-Lauf

[Run 41062914350](https://github.com/YoungJibbit95/Neutron-Client/actions/runs/41062914350),
Job 124086870778: **SUCCESS**, alle Schritte einschließlich Artefakt-Upload.
Head b6f2fc7e428a459464a081f690583bf704793acd; tatsächlich ausgecheckter
PR-Mergecommit **d7b3b50e3970d946532d5a3930a354803fc3f52b**.
Das ist ein GitHub-Testcommit, kein ausgeführter Merge nach main.

Logs bestätigen Temurin 1.8.0_482-b08, Maven 3.9.15 und
**792 Tests / 0 Failures / 0 Errors / 3 Skips, 126 Suites**.
Das heruntergeladene Artefakt `java8-pr-17-1` (ID 12981915895) wurde anhand
seines GitHub-Digests geprüft und die 126 XMLs unabhängig neu summiert:
`8c883d8bb7b03a103b917eef411fcfa476a195a2db93a626ded60547fbf32638`.
Ein zunächst abgelaufener Log-Download lieferte HTTP 403; ein frisch angeforderter
Joblog und das Artefakt wurden anschließend erfolgreich bezogen.

Die genau drei Hosted-Skips sind:

1. WorldOverlayContractTest.nativeDispatchWhenRequested
2. GuiChatInputTest.nativeInputAndDrawingWhenRequested
3. GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings

Sie sind keine Native-PASSs des Hosted Runners. Die Native-Abnahme kommt aus dem
separaten lokalen Lauf. Die ursprünglichen Heads von #15/#16 erhalten durch
diesen Lauf keine rückwirkenden eigenen Checks; validiert wurde der kombinierte
Quellstand im Stack. Ein Bericht-Commit löst einen weiteren Lauf aus; dessen
aktueller Status ist in PR #17 sichtbar.

## Erneute lokale Regression

| Lauf | Tests / Failures / Errors / Skips | Frische Suites |
|---|---|---:|
| Leeres isoliertes Maven-Repository, Online-Auflösung | 792 / 0 / 0 / 3, fertig 14:22:49 CET | 126 |
| Vollsuite mit allen drei Native-Opt-ins, offline | **792 / 0 / 0 / 0**, fertig **14:26:33 CET** | **126** |
| Betroffener Fokus als Teilmenge derselben Vollsuite | 278 / 0 / 0 / 0 | 30 |

Kindprozess-Zahlen werden nicht zusätzlich auf die Surefire-Fälle addiert.
Der Kaltstart benutzte das neu heruntergeladene, identisch geprüfte Maven-Archiv;
er kopierte keinen Entwicklercache und keinen Client. Die Native-Invocation war:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-1.8.0_482'
& 'C:\Users\Administrator\scoop\apps\maven\3.9.15\bin\mvn.cmd' -o `
  '-Dpulsar.world.overlay.native=true' '-Dpulsar.chat.native=true' `
  '-Dpulsar.test.glContext=true' `
  '-Dorg.lwjgl.librarypath=F:\Puslar Backup - Copy\test_natives\windows' test
```

Tatsächlich ausgeführt wurde die ebenfalls SHA512-geprüfte Maven-3.9.15-Kopie
unter dem aufgabenbezogenen evidence/tooling, mit gleichem JDK und Parametern.
`automated-gate-anchors.json` bindet die geforderten PRE/POST-, OFF-, Resume-,
Deadline- und beiden Reentry-Fälle an erfolgreich ausgeführte frische XML-Fälle.

## Echte Spielprüfung: Beobachtungen und Grenzen

Die alte gestoppte Sitzung wurde nicht fortgesetzt. Der Nutzer autorisierte
ausdrücklich eine neue lokale Sitzung, übergab die Teststeuerung an Codex und
autorisierte nach weiteren Escape-Stopps nochmals einen neuen Versuch.
Ein Zwischenversuch scheiterte bereits beim Fensterinventar ohne App-Eingabe.
Nach der jüngsten ausdrücklichen Aufforderung funktionierte das frische Inventar.
Der Computer-Use-Skill und seine Stop-Regel wurden eingehalten; es wurde kein
gestoppter Aufruf durch eine andere Eingabetechnik umgangen.

Verwendet wurden ausschließlich das vorhandene IntelliJ-Projekt, die bestehende
Konfiguration **Neutron Client (Java 8)**, Temurin 8u482 und die eigene lokale
Creative/Superflat-Welt **New Worl**. Kein anderer Spielstand und kein externer
Server wurden geöffnet. Temporär waren G=Scaffold und H=ClickGUI gesetzt.

In der ersten neuen Sitzung waren eine verlängerte Steinspur und owner=scaffold
sichtbar, allerdings mit dazwischenliegenden physischen Nutzereingaben. Das ist
ein Teilnachweis, kein kontrollierter Brücken-PASS.

In der abschließenden, von Codex gesteuerten Sitzung wurde POST zunächst in der
Datei und später im echten GUI bestätigt. Testblöcke wurden mit Linksklick
entfernt; die fehlende Steinfläche erschien bei eingeschaltetem Scaffold wieder,
ohne manuelle Platzierungsklicks in diesen PRE/POST-Kontrollen. Das HUD zeigte
angenommene Scaffold-Rotationen, getrennt von der Kamera. Bei Disable nach einem
bestätigten Owner zeigte der vorhandene MOVE_LOCAL_POST-Verlauf:

- Tick/Sequenz 4119: owner=scaffold.
- Tick/Sequenz 4120, epochMs 1793367622898: owner=scaffold-release.
- Tick/Sequenz 4121, epochMs 1793367622947: owner=scaffold-release.
- Späterer laufender Frame: owner=null; Kamera und Wire wieder 12.6 / 76.3.

Die Kamera blieb dabei unverändert. Das belegt zwei tatsächlich angenommene
Release-Frames und den beobachteten Endzustand in diesem Versuch, keine exakte
Messung des separaten 200-ms-Ablaufs bei ausbleibenden Ticks.

Rotation OFF wurde im GUI gesetzt und in der gespeicherten Einstellung bestätigt.
Owner blieb null, Kamera und Wire blieben gleich. Nach Entfernen der Testfläche
wurde ein geometrisch ungültiger Versuch mit der bestehenden Rayguard-Meldung
abgelehnt. Ein positiver OFF-Platzierungsfall ließ sich nicht eindeutig herstellen
und bleibt offen. Spätere manuelle Supportversuche gehören nicht zu den vorherigen
PRE/POST-Autoplatzierungsnachweisen und werden nicht als deren Ergebnis gezählt.

Anschließend wurden im GUI PRE und Rotationen ON gesetzt. Nach Rückkehr erschien
zunächst "Noch keine gültige angenommene Rotation", dann ein frischer akzeptierter
Scaffold-Owner (Sequenzen 8146–8148) und erneut Stein an der entfernten Stelle.
Dies belegt diesen begrenzten Resume-/PRE-Fall, nicht alle Fokus-/Pause-/Welt-ABAs.

Kurze S-Eingaben über das Werkzeug änderten die abgelesenen Koordinaten nicht
(254.351 / 6.000 / 341.246). Daher kein kontrollierter Dauerbrücken-PASS. Native
Walking-Reentry-Injektion wurde nicht hergestellt; ihre Produktionspfade bestehen
in den automatisierten realen Walking-/EmbeddedChannel-Regressionen.

Am Ende: Scaffold deaktiviert, Save and Quit to Title, normaler Exit,
Client-Prozess beendet, Moduldatei und Optionen bytegenau zurückkopiert.
`test_run/logs/latest.log` war vom 28.10. und wurde ausdrücklich nicht als neue
Laufzeitevidenz verwendet. Echte UI-Beobachtungen stehen im Tooltranskript;
es wird kein neuer Paketdump, C08-Zähler oder externer Serverbeleg behauptet.

## Gate-Entscheidungen

PASS gilt jeweils nur für die ausdrücklich genannte Prüfebene.

| Gate | Status | Konkreter Nachweis / fehlende Voraussetzung |
|---|---|---|
| Stack #15 → #16 → #17 | PASS | Bases und unveränderte Feature-Heads über GitHub geprüft |
| Ursache fehlender PR-CI | PASS | Actions aktiv; zuvor nur Badge-Schedule/Dispatch; neuer PR-Workflow tatsächlich ausgeführt |
| Reproduzierbarer Java8-Hosted-Lauf | PASS | Run 41062914350; 126 Artefakt-XMLs selbst gezählt, 792/0/0/3, drei explizite GL-Opt-ins |
| Eigene CI-Checks auf ursprünglichem #15/#16-Head | BLOCKED | Keine rückwirkenden Checks; nach CI-Integration/Retarget neu auslösen |
| Java8 / bestehende CMB-/SCF-Matrix | PASS | 792/0/0/0 lokal; betroffener Fokus 278/0/0/0 enthalten |
| Native GL/Chat/Overlay | PASS | Lokale Vollsuite mit allen drei Opt-ins, null Skips |
| PRE/POST Produktionspfad | PASS | Frische ScaffoldClientFlowTest- und ScaffoldPacketWaveTest-Anker |
| OFF / Release / Resume / Reentry Produktionspfad | PASS | Frische OFF-, OwnerContinuity-, Release- und zwei verschachtelte Walking-Fälle |
| Echter Java8-Start, eigene Welt, Rendering | PASS | Autorisierte Sitzung im vorhandenen Client, F3 zeigt Java 1.8.0_482 |
| Begrenzte native PRE-/POST-Wiederplatzierung | PASS | Beobachtete entfernte/erneuerte Steinfläche, keine manuellen Platzierungsklicks während dieser Kontrollen |
| Native OFF: kein Source-Borrow, ungültiger Ray | PASS | Owner null, Kamera=Wire, Rayguard-Diagnose |
| Native OFF: positive Platzierung | BLOCKED | Kein eindeutig erfolgreicher kontrollierter Positivfall |
| Nativer Disable-Auslauf, beobachteter Tickpfad | PASS | Zwei release-Frames 4120/4121, danach owner null und Wire=Kamera |
| Native 200-ms-Deadline ohne Ticks | BLOCKED | Kein solcher Spielversuch; exakte Deadline nur Regression-PASS |
| Begrenzter nativer GUI-/PRE-Resume | PASS | Anfängliche Acceptance-Sperre, neue Sequenzen und Wiederplatzierung nach GUI-Schluss |
| Kontrollierte längere PRE-/POST-Brückenfahrt | BLOCKED | Werkzeug-Tastendrücke liefern keinen kontrollierten gehaltenen Bewegungsinput |
| Walking-Reentry im vollständigen Spiel | BLOCKED | Keine gezielte native Callback-Injektion; automatisierte Fälle PASS |
| Externer Server / Checkname / VL / Serverresultat | BLOCKED | Keine autorisierte externe Testumgebung bereitgestellt; kein Login oder Verbindungsversuch |
| Fremddateien / Optionen / Modulzustand / andere Welten | PASS | 269 Fremddateien und 637 Dateien anderer Welten hashgleich; ursprüngliche Konfigurationshashes wiederhergestellt |
| Merge / Release | BLOCKED | Offene Abnahme-Gates und ausdrückliche Freigabe fehlen; nichts gemergt/veröffentlicht als Release |

Kein neuer reproduzierter Feature-FAIL liegt vor. BLOCKED ist keine erfolgreiche
Abnahme und kein Nachweis eines Produktfehlers. Die ursprüngliche 42-Fall-Matrix
und der Phase-2-Vertrag bleiben die Referenz; Native-/Servergrenzen werden nicht
durch synthetische Tests umbenannt oder geschlossen.

## Merge-Empfehlung und Erhaltung

**Jetzt nicht mergen; alle drei PRs Draft lassen.** Für saubere Einzelabnahmen
den CI-Patch aus #17 nach separater Freigabe zunächst unabhängig nach main
überführen und #15/#16 auf dem neuen Mergestand neu prüfen. Danach offene
Runtime-/Serverkriterien abschließen oder deren Scope ausdrücklich entscheiden;
erst dann #15, anschließend #16 und die verbleibende Phase-3-Dokumentation
freigeben. Jede Retarget-/Mergeänderung benötigt neue Checks. Diese Empfehlung
ist kein Merge-Auftrag und wurde nicht ausgeführt.

Modul-SHA256: F46D68178AD30F192A5161B842D2CCADE7EC9DC685AE352DF75138A29375342C.
Options-SHA256: 0AD6D5C823D4DEE8D0477BF2FA10DDF23AB76B11BCBBDC59DE9472B27E29942F.
Nur New Worl enthält autorisierte Teständerungen. Alle 637 zuvor inventarisierten
Dateien anderer Welten sowie die 269 fremden ungetrackten Dateien sind unverändert.
Keine historischen Testnachweise überschrieben, kein Worktree/Klon erstellt oder
entfernt, kein Force-Push oder Push nach origin/main. Der Hauptcheckout kehrt
nach dem Bericht per sicherem Fast-forward auf lokales main zurück; GitHub-main
bleibt unverändert. Logs/XMLs, CI-Artefakt/Digest und kleine Konfigurationsbackups
liegen unter `F:\Pulsar-Recovery\analysis\cmb-scf-phase3-20261030\evidence`.
