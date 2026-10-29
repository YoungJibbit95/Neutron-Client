# Neutron 2.0 — OpenCode W00 Ergebnisse

**Datum:** 27.10.2026
**Paket:** unabhängige Startwelle W00 (NET-001, MOD-010, MOD-011)
**Ergebnis:** W00 beendet — beide Pflichtpakete bearbeitet, MOD-011 ohne Änderung mit Evidenz geschlossen.

## (a) Ausgangsbasis

| Punkt | Wert |
|---|---|
| Branch vor der Arbeit | `codex/revived-final-integration` |
| HEAD vor der Arbeit | `eeddb7da58978f04fad95ad8042175b7ad848d6a` |
| Audit-Snapshot | `c19312037dc76e7d4d02fa34fe5f28760bbd6e90` (nicht ausgecheckt) |
| Zuletzt abgeglichener `main` | `fd42cca463edc077b83979f19c433d750a64a877` (nicht ausgecheckt) |
| W00-Branch | `prep/neutron-opencode-wave00` (neu angelegt, kein bestehender Branch überschrieben) |
| Java | Oracle JDK 1.8.0_391 (`C:\Program Files\Java\jdk-1.8`), `JAVA_HOME` dafür gesetzt |
| Maven | Apache Maven 3.8.5, lokal vorhanden unter `~/.m2/wrapper/dists/...`; Aufruf mit `-o` (offline) |
| Working Tree vor Beginn | 147 geänderte Dateien, **keine** in `src/`, `pom.xml` oder Builddateien (nur `test_run/`-Laufzeitdaten, `.gitignore`, `.vscode/settings.json`) |

HEAD weicht von Audit-Snapshot und `main` ab. Deshalb wurden alle drei Befunde erneut am tatsächlich ausgecheckten Code geprüft, nicht aus den Audits übernommen. Die 147 vorbestehenden Änderungen wurden nicht angefasst, nicht gestasht und nicht committet.

## W00-0 — Bestehende Tests (Baseline vor Änderungen)

| Test | Kommando | Ergebnis vor W00 |
|---|---|---|
| `LegacyModuleConfigTest` | `mvn -o -Dtest=LegacyModuleConfigTest test` | bestanden, 3/3 |
| `UiEffectsTest` | `mvn -o -Dtest=UiEffectsTest test` | bestanden, 7/7 |
| `PacketFlowTest`, `NetworkShutdownTest` | gezielter Lauf | bestanden, 5/5 und 7/7 |
| `BacktrackConfigTest`, `TickbaseConfigTest`, `ModuleManagerTest` | gezielter Lauf | bestanden, 2/2, 3/3, 9/9 |

Testvorbilder: Netty/`EmbeddedChannel` in `src/test/java/me/youngjibbit/core/network/PacketFlowTest.java` und `NetworkShutdownTest.java`; Value-Visibility in `src/test/java/me/youngjibbit/utils/render/UiEffectsTest.java`.

## W00-1 — NET-001 (behoben)

**Ausgangsbefund:** In `src/main/java/net/minecraft/network/NetworkManager.java` Zeile 546 (Methode `setCompressionTreshold`) wurde im Zweig für einen bereits vorhandenen Handler `"compress"` der Handler `"decompress"` zum `NettyCompressionEncoder` gecastet. Audit-Hypothese bestätigt.

**Regressionstest zuerst, rot:** `src/test/java/me/youngjibbit/core/network/CompressionPipelineTest.java` (neu).
Erster Lauf: `java.lang.ClassCastException: net.minecraft.network.NettyCompressionDecoder cannot be cast to net.minecraft.network.NettyCompressionEncoder`, Stacktrace auf `NetworkManager.setCompressionTreshold(NetworkManager.java:546)`. Test 1/1 Errors.

**Änderung:** genau ein Handler-Lookup, `get("decompress")` → `get("compress")`, Datei `src/main/java/net/minecraft/network/NetworkManager.java`, Zeile 546. Kein Umbau von `PacketPipeline`, kein weiterer `NetworkManager`-Code, keine Kompressions-/Sendeänderung.

**Zweiter Lauf:** 1/1 bestanden.

Der Test deckt: erster positiver Threshold installiert beide Handler mit korrektem Typ, zweiter positiver Threshold aktualisiert beide Threshold-Werte ohne Exception, negativer Threshold entfernt beide Handler. Die Thresholds werden über das private Feld `treshold` gelesen (keine Produktionsänderung dafür nötig).

## W00-2 — MOD-010 (am HEAD nicht reproduzierbar, Test ergänzt)

**Ausgangsbefund:** Die Audit-Hypothese lautet, `ArrayListHUDModule` Zeile 64 dereferenziere mit `getModeSetting("Effects Source")` ein im Modul nicht vorhandenes Setting und könne dadurch bei `Mode.isVisible()` werfen.

**Prüfung am HEAD:** Nicht reproduzierbar. Das Setting existiert zur Laufzeit:

- `src/main/java/me/youngjibbit/module/impl/client/ArrayListHUDModule.java` Zeile 69 legt das Feld `surface` als `ArrayListSurface(this)` an. Feldinitialisierer laufen in Java vor dem Konstruktorrumpf (Zeile 64), unabhängig von ihrer Textposition.
- `src/main/java/me/youngjibbit/utils/render/UiEffects.java` Zeile 98 erzeugt im Konstruktor von `LocalSurface` ein `ComboValue("Effects Source", owner, ...)`. Der `Value`-Konstruktor registriert sich über `parent.registerValue(this)` am Modul (`Value.java` Zeilen 20–29, `Module.java` Zeilen 105–110).
- `Module.getModeSetting` liefert damit ein Objekt, keinen `null`.
- Der vorhandene Test `src/test/java/me/youngjibbit/utils/render/UiEffectsTest.java` Zeilen 23–26 prüft genau diese Sichtbarkeit (Global → unsichtbar, Local → sichtbar) und war in der Baseline 7/7 grün.

**Änderung:** keine Produktionsänderung. Der Audit-Vorschlag „Mode immer sichtbar“ hätte das bestehende, getestete Verhalten (Mode nur bei `Effects Source = Local` oder `Mode = None` anbieten) verändert; das wäre keine Minimalreparatur, sondern eine UX-Änderung. `Mode` bleibt als Setting erhalten, Default `Glass` und alle IDs unverändert.

**Regressionstest ergänzt:** `src/test/java/me/youngjibbit/module/impl/client/ArrayListModeVisibilityTest.java` (neu). Er prüft, dass ein frisch erzeugtes Modul für alle Kombinationen aus `Effects Source` (6 Optionen) und `Mode` (6 Optionen) ohne Exception antwortet und dass die Sichtbarkeit der bestehenden Semantik entspricht. Ergebnis: 1/1 bestanden.

GUI-Interaktion (Panel/iOS-Settings öffnen) wurde **nicht** gestartet; „GUI getestet“ wird hier nicht behauptet.

## W00-3 — MOD-011 (keine Änderung, Bedingung nicht erfüllt)

**Audit-Hypothese:** `LegacyModuleConfigTest` Zeilen 20–23 nutzt `back.getNumberSetting("Min Range")` und `tick.getNumberSetting("Delay")`, während die deklarierten Values `Min Server Distance` bzw. `Release Cooldown MS` heißen; die Getter sollen `null` liefern.

**Prüfung am HEAD:** Die Getter liefern keine `null`, weil beide Module `getValueByName` überschreiben:

- `src/main/java/me/youngjibbit/module/impl/exploit/Backtrack.java` Zeilen 88–97: `minrange` → `min_server_distance` (Zeile 94), zusätzlich `enemydistance` → `acquire_range` (Zeile 93).
- `src/main/java/me/youngjibbit/module/impl/exploit/Tickbase.java` Zeilen 61–68: `Delay`/`delay` → `release_cooldown_ms` (Zeile 66), `Ticks` → `charge_ticks`.

Die Audit-Aussage, `Module.getValueByName` vergleiche nur direkt, gilt für die Basisklasse, nicht für diese beiden Überschreibungen. `LegacyModuleConfigTest` war in der Baseline 3/3 grün, auch nach der NET-001-Änderung.

**Änderung:** keine. Die Testabfragen sind keine falschen Getter, sondern bewusst unterstützte Legacy-Aliase. Damit fehlt die vom Auftrag geforderte eindeutige Evidenz für eine Testkorrektur. Die Legacy-JSON-Schlüssel, die Persistenz und die Produktionssemantik bleiben unverändert. Es gibt keinen offenen Assertion-/Migrationswiderspruch, der einen Stopp dieses Teils erfordern würde.

## (c) Testkommandos und echte Ergebnisse

Alle Läufe mit `JAVA_HOME=C:\Program Files\Java\jdk-1.8` und der lokalen Maven-3.8.5-Distribution, Modus `-o` (offline, keine Downloads).

| # | Kommando | Ergebnis |
|---|---|---|
| 1 | `mvn -o -q -Dtest=LegacyModuleConfigTest test` (Baseline) | bestanden, 3/3 |
| 2 | `mvn -o -Dtest=UiEffectsTest test` (Baseline) | bestanden, 7/7 |
| 3 | `mvn -o -Dtest=CompressionPipelineTest test` **vor** dem Fix | fehlgeschlagen, 1 Error, `ClassCastException` bei `NetworkManager.java:546` |
| 4 | `mvn -o -Dtest=CompressionPipelineTest test` **nach** dem Fix | bestanden, 1/1 |
| 5 | `mvn -o -Dtest=ArrayListModeVisibilityTest test` (erste Fassung) | fehlgeschlagen, 1 Failure: eigene Erwartung „Mode bei Default sichtbar“ war falsch; keine Exception im Produktivcode |
| 6 | `mvn -o -Dtest=ArrayListModeVisibilityTest test` (korrigierte Erwartung) | bestanden, 1/1 |
| 7 | `mvn -o "-Dtest=LegacyModuleConfigTest,UiEffectsTest,PacketFlowTest,NetworkShutdownTest,CompressionPipelineTest,ArrayListModeVisibilityTest,ModuleManagerTest,BacktrackConfigTest,TickbaseConfigTest" test` | bestanden, 38/38 |
| 8 | `mvn -o clean test` (Gesamtsuite) | bestanden: 396 Tests, 0 Failures, 0 Errors, 3 übersprungen, BUILD SUCCESS, 2:17 min |

**Gesamtsuite:** `mvn -o clean test` auf JDK 8 und Maven 3.8.5, offline, Exit-Code 0. Übersprungen wurden drei umgebungsabhängige Tests: `WorldOverlayContractTest` (1), `GuiChatInputTest` (1), `GuiOverlayDebugGlStringsTest` (1). Diese Skips sind vorbestehend und wurden nicht durch W00 verursacht; sie sind keine bestandenen Render-/GL-Abnahmen.

`git diff --check` meldet ausschließlich Trailing Whitespace in der vorbestehenden, nicht von W00 stammenden Änderung `test_run/logs/latest.log`. Die W00-Commits sind sauber (`git diff --check eeddb7da5..HEAD` ohne Befund).

## (d) Bewusst nicht geprüft

- Keine Live-Server-, Account- oder Netzwerkverbindung; kein realer Kompressionswechsel gegen einen Server. NET-001 ist nur auf Pipeline-Ebene mit `EmbeddedChannel` belegt.
- Keine GUI-/Render-Abnahme (Panel, iOS-Settings, ClickGUI) und kein Spielstart; MOD-010 ist ein Value-/Visibility-Test, keine visuelle Abnahme.
- Kein Test der übrigen NET-Findings, keine Movement-, Combat-, Scaffold-, Gradienten- oder Config-Migrationsprüfung.
- Die 147 vorbestehenden Working-Tree-Änderungen wurden weder geprüft noch bereinigt.
- Kein `mvn package`, kein Release-Build, keine Versions-/Branding-Änderung.

## (e) Auswirkungen auf die späteren sieben Arbeitsbereiche

- **NET:** NET-001 ist geschlossen. Der Fix betrifft nur den Encoder-Lookup; `PacketPipeline`, Delay-Architektur und Owner-Lifecycle bleiben unberührt. NET-R01 kann die Regression über `CompressionPipelineTest` inspizieren.
- **VIS:** MOD-010 braucht keinen Produktivfix. Die Mode-Sichtbarkeit hängt weiterhin an `Effects Source` der gemeinsamen Surface; jede geplante Local-/Global-Gradienten-Bedienung muss diese Bindung und `UiEffectsTest` berücksichtigen. Kein Render-/ID-/Default-Eingriff.
- **MOD:** MOD-011 ist ohne Änderung geschlossen; Legacy-Aliase sind Teil des Ist-Vertrags. Wer die Aliase später entfernt, bricht `LegacyModuleConfigTest` und die Legacy-Config-Migration.
- **CORE:** Keine API-, Build- oder Configschema-Änderung. Zwei neue Testklassen, keine neuen Runtime-Services.
- **CMB/SCF/MOV:** Unberührt.
- **Release/Version:** Unberührt.

## (f) Ungelöste Blocker

- Keine fachlichen Blocker für W00.
- Umgebungsbedingt: Maven ist nicht im PATH; W00 nutzt die vorhandene lokale Distribution und `JAVA_HOME` auf JDK 8. Ein anderer Rechner ohne `~/.m2`-Cache kann den Lauf nicht offline wiederholen.
- Modell-/Auftragsregel: kein Push, kein PR, kein Tag, keine Änderung an `main`.

## Abschließender Diff (nur W00-Anteile)

```
fix(net): update compression encoder through the compress handler
   M  src/main/java/net/minecraft/network/NetworkManager.java   (1 Zeile)
   A  src/test/java/me/youngjibbit/core/network/CompressionPipelineTest.java

test(mod): guard ArrayList HUD mode visibility
   A  src/test/java/me/youngjibbit/module/impl/client/ArrayListModeVisibilityTest.java
   A  design/opencode-wave00/RESULTS.md
```

`git diff --stat` über den gesamten Baum zeigt zusätzlich die 147 vorbestehenden Änderungen (243 Insertions/207 Deletions, überwiegend Binärdateien aus `test_run/`). Diese sind **nicht** Teil von W00 und bleiben uncommittet im Arbeitsbaum. Die beiden W00-Commits liegen ausschließlich lokal auf `prep/neutron-opencode-wave00`; es wurde nicht gepusht, kein PR und kein Tag erstellt.
