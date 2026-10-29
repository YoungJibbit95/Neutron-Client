# CombatDiagnostics und Combat-Ablauf: lokale Prüfung am 29.10.2026

Arbeitsordner: `F:\Puslar Backup - Copy`. Diagnosebasis: PR #11,
`a2034e41dc7c1250c379bbdcabaa9fcb196abbc4`, zusammen mit dem bereits integrierten
Clientstand `d8d7697576f658fe70229a460c1dfa82d2d89261`. Kein zusätzlicher Worktree.

LiquidBounce Legacy wurde auf **`03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e`**
festgehalten. Die verlinkten Referenzstellen beziehen sich auf diesen Stand, nicht
auf einen beweglichen Branch. [Lizenz: GPLv3](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/LICENSE).
Es wurden Architektur und Verträge verglichen, keine fremden Kotlin-/Java-Funktionen übernommen.

## Vergleich

**1** = reproduzierbarer Implementierungsfehler; **2** = offene mögliche Fehlerquelle;
**3** = beabsichtigter Architekturunterschied. Bestehende Regressionen grenzen lokale
Fehlerklassen ein; sie beweisen weder Serverakzeptanz noch die Abwesenheit aller Fehler.

| Komponente | LiquidBounce Legacy | Neutron | Abweichung | Nachweis | Maßnahme |
|---|---|---|---|---|---|
| Rotation und Movement | [Mixin: PRE, C03, serverRotation, POST](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/injection/forge/mixins/entity/MixinEntityPlayerSP.java#L130-L242); KillAura aktualisiert über RotationUpdate und greift im GameTick-Handler an. | PRE-Anforderungen, Prioritätsauflösung, lokal akzeptiertes C03, `finishMotion`, POST-Angriff. | **3:** Neutron bindet Aura ausdrücklich an lokal akzeptierte Winkel; die Eventnamen beider Projekte sind keine identische Tick-Zeitachse. | `EntityPlayerSP.onUpdateWalkingPlayer`, `Aura.onMotion`; `CombatFlowTest` prüft Paketumschreibung und spätere Movement-Replays. | Ablauf beibehalten. Keine Änderung an PacketPipeline/Tickbase. |
| Ziel und Trefferprüfung | [KillAura: Zielgleichheit zum Raycast](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/features/module/modules/combat/KillAura.kt#L941-L970). | `validHit` prüft gewähltes Ziel, Szene, aktuelle akzeptierte Winkel und tatsächlichen nächsten Raycast-Treffer erneut, auch bei ausgeschalteter optionaler RayCast-Einstellung. | **3:** zusätzlicher obligatorischer Controller-Guard. Kein ungeschützter Aura-Angriffsweg reproduziert. | `Aura.validHit`, `PlayerControllerMP.tryAttackEntity`, `AttackHookTest`, `RotationModuleBindingChecks`. | Keine weitere funktionale Änderung. |
| Raycast-Ursprung und Box | [RaycastUtils](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/utils/rotation/RaycastUtils.kt#L31-L88) verwendet lokale Augen und kann Backtrack-Boxen einbeziehen. | Aktionen verwenden zuletzt gemeldete Position; sichtbare Hilfe kann explizit die lokale Position verwenden. Aktuelle Kollisionsboxen, Reichweite, nächste Entity und Blockhindernisse werden geprüft. | **3:** bewusst getrennte lokale und gemeldete Position. **2:** tatsächliche Server-Entityposition/Latenz bleibt unbekannt. | `RaycastService`, `AcceptedPositionRaycastTest`, numerische Box-/Hindernisfälle in `CombatFlowTest`. | Keine Boxen-/Reichweitenänderung ohne reproduzierbaren Gegenfall. |
| Swing und Controller | [PlayerExtension](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/utils/extensions/PlayerExtension.kt#L315-L331): Swing vor Controller; [KillAura](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/features/module/modules/combat/KillAura.kt#L820-L857) delegiert dorthin. | Manueller Klick und Aura schwingen vor dem gemeinsamen Controller. Dort folgen AttackEvent, ausgewähltes C02 und lokale Annahme/AttackResultEvent. | **3:** Neutron ergänzt Szenen-/Entity-Guards und gemeinsames Tickbudget. Kein zusätzlicher aktiver Aura-/AimAssist-C02-Sender gefunden. | `Minecraft.clickMouse`, `AttackHookTest`; `CombatFlowTest.manualAttackAndAutomaticAttackShareOneBudgetInBothOrders`. `PacketUtils.attackFakePlayer` hat im geprüften Quellbaum keinen Aufrufer. | Keine Änderung an Swing, Scheduler oder Controller. Trace-Ereignisse sind keine vollständige Erfassung beliebiger direkter/silent Paketsendungen. |
| AimAssist | [Aimbot](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/features/module/modules/combat/Aimbot.kt#L89-L130) arbeitet nach Motion-POST sichtbar. | `requestVisible` im PRE, Auflösung vor Movement; keine eigenen automatischen Angriffe. Aura/Scaffold haben höhere Priorität. | **3:** anderer Kamerazeitpunkt und explizite Arbitration. Manuelle Treffer behalten den normalen MouseOver-/Controllerpfad. | `AimAssist.onMotion`, `RotationManager.resolve`; 19 isolierte Modulbindungschecks einschließlich Zielwechsel, FOV, Kontext und Owner-Verlust. | AimAssist und Aura auf dem Server getrennt prüfen. |
| Sensitivität und Trajektorie | [Rotation.fixedSensitivity](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/utils/rotation/Rotation.kt#L73-L83) und [RotationUtils](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/utils/rotation/RotationUtils.kt#L589-L598) runden am Serverwinkel; [RotationSettings](https://github.com/CCBlueX/LiquidBounce/blob/03bc91a3c34031d1ea7400ef3b67c00adf6a4c6e/src/main/java/net/ccbluex/liquidbounce/utils/rotation/RotationSettings.kt#L25-L79) hält eigene Optionen. | Zeitabhängige, begrenzte Schritte mit getrennten Zuständen; Quantisierung behält ausdrücklich finale Schritte unterhalb eines GCD-Schritts. | **3:** Sub-GCD-Endschritt ist im Code beabsichtigt. **2:** Wirkung auf einen unbekannten Servercheck nicht belegt. | `RotationManager.quantize`, `RotationTrajectory`, `RotationTrajectoryTest`, `CombatFlowTest`. | Keine Jitter-/Speed-/Quantisierungsänderung aufgrund bloßer Referenzabweichung. Gerundete HUD-Zahlen beweisen keine exakte GCD-Einhaltung. |
| Unbekannte Wire-Winkel | Kein Gegenstück zum neuen Neutron-HUD erforderlich. | Ursprünglicher PR setzte ohne Wire-Beobachtung die Kamera als `localWire` ein. | **1:** erfundene Diagnoseevidenz, kein bestätigter Combat-Fehler. | Regression: F8 bei Kamera 37°, noch ohne Movement, meldete fälschlich `localWire=37.00/0.00`. | `wireKnown=false`, `NaN` und getrenntes `motionAccepted`; Regression bestanden. |
| Kamera beim Aura-Angriff | Sichtbare Kamera und Silent-Rotation sind getrennte Konzepte. | Aura setzt die Spielerpose für lokalen Angriff/Knockback kurzzeitig auf Wire-Winkel und stellt sie im `finally` zurück. Das ursprüngliche HUD hielt diese Zwischenpose für die Kamera. | **1:** bei Kamera 180° und Wire 73° erschien fälschlich Kamera 73°. | `CombatDiagnosticsIntegrationTest`: vor Korrektur fehlgeschlagen, danach beide Listener-Anmeldereihenfolgen erfolgreich. | Kamera vor Default-POST-Combat beobachten, Sample am Dispatch-Ende verwerfen, `playerPose` und `cameraSource` zusätzlich ausweisen. |

## Prüfung und Grenzen

Die anfängliche relevante Suite bestand mit 61 Tests. Neue Diagnose-Integrationstests
reproduzierten zwei Fehler des ursprünglichen PR-Moduls: **3 Tests, 2 Failures**.
Nach Korrektur bestanden Trace und Integration mit 6 Tests. Die abschließende vollständige
Java-8-Suite bestand mit **702 Tests, 0 Failures, 0 Errors, 0 Skipped**; bestehende native
Offscreen-Grafik-, Chat- und World-Overlay-Checks waren aktiviert.

F9/F8, Reflection-Discovery, Client-Kategorie, 120-Einträge-Limit, World-/Disable-Cleanup,
unbekannte/ungültige Winkel, POST-Reihenfolge, echte Kamera gegenüber Zwischenpose und
ausbleibende Diagnose-Pakete sind synthetisch geprüft. Beide ClickGUIs lesen die Client-
Kategorie des ModuleManager. Die **sichtbare Darstellung des neuen HUDs im laufenden Spiel**
ist damit noch nicht geprüft. Ebenso fehlen echte Serverlogs und der konkrete Intave-/Polar-
Checkname. Deshalb bleibt PR #11 offen; es wird kein behobener Anticheat-Flag behauptet.

Kein zusätzlicher reproduzierbarer funktionaler Fehler in Aura, AimAssist, Raycast,
RotationManager oder AttackScheduler wurde bestätigt. Es gibt deshalb keinen spekulativen
Combat-Fix-PR. MoveFix, PacketPipeline und Tickbase bleiben unverändert.

## Kurzer Testserver-Ablauf

1. Client aus dem Hauptcheckout neu bauen/starten. `CombatDiagnostics` unter Client oder
   mit F9 aktivieren; für exportierbare Zeilen `Console Attack Log` ausdrücklich einschalten.
2. Baseline ohne Combat-Module, nur Rotationen, AimAssist mit manuellen Klicks und Aura
   jeweils separat messen. Speed, NoSlow, Scaffold und Tickbase zunächst aus lassen;
   Autoblock und Sprint-/Item-Use-Einflüsse erst danach einzeln variieren.
3. Bei einer sichtbaren Serverwarnung F8 drücken. Build, Modus, Sensitivität, `epochMs`,
   `event`, `seq`, Kamerawinkel, Wire-Winkel und Target-ID mit dem **echten** serverseitigen
   Checknamen, Zeitpunkt und Schweregrad korrelieren. F8 ist ein manueller Marker.
4. F9-Aus, Weltwechsel, längere Sitzung und kleines Fenster auf korrekte Darstellung,
   Cleanup und persönliche Tastenkonflikte prüfen. Console-Logging bleibt standardmäßig aus.
5. `ATTACK_LOCAL_ACCEPTED` ist kein Server-Hit-Ack; S08 kann ein gewöhnlicher Teleport sein.
   Keine Rückschlüsse auf Intave-/Polar-Flags allein aus diesen beiden Client-Ereignissen.

Lokale Logs und Vorher-/Nachher-Nachweise liegen unter
`F:\Pulsar-Recovery\analysis\combat-diagnostics-pr11-20261029`.
