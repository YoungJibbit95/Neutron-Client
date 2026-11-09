# AC-R02 — Baseline und offene Nachweise

Revision 2.0 · 28.10.2026 · Addendum zu AC-R01. Auftrag: Baseline synchronisieren und H0/H1/H2 präzisieren; keine neuen Umgehungsmodi. Evidenz bleibt `Q` (Code selbst gelesen), `T-code` (Testcode gelesen), `T-report` (fremder Testbericht), `D` (Dokumentation), `H` (Hypothese), `U` (unbelegt). Kein neuer Testlauf.

## Quellen und Rangfolge

Der Nutzer hat den separaten AC-R02-Prompt ausdrücklich beauftragt. Das Quellenpaket liefert Produktentscheidungen und Evidenz; das Integrationsreview ist eine Einordnung, keine zusätzliche Codefreigabe. Gelesen ab `00_START_HIER.md`: Original-v1.0, AC-R01-Review, vier AC-R01-Dateien, CORE-Berichte und Arbeitsregeln; lokale I01c-Dokumente und Java-Diffs haben gegenüber mitgebrachten Berichten Vorrang. Archiv außerhalb der Repositories unter `%TEMP%/neutron-ac-r02-sources-20261028` entpackt; alle zwölf Manifest-Einträge stimmen per SHA256. Die vier Paket-AC-R01-Dateien sind bytegleich mit den lokalen historischen Dateien.

Original: `01_VERBINDLICH/Neutron_2_0_Universelle_Produktspezifikation_v1.0.md`, SHA256 `10bb79575ca7b1463de71988fbc237b04f4bd7b6422a9304fa6ad341815f4d2c`. Besonders maßgeblich: §§2.1–2.3, 3.1, 5.1–5.2, 7.3 und 11. Quellenverfügbarkeit ist jetzt geklärt; numerische Produktgates bleiben offen.

## Lokale Git-Verifikation

| Basis | Verifizierter Stand | Einordnung |
|---|---|---|
| B0 Research | `F:/Puslar Backup - Copy`, `prep/neutron-opencode-wave00`, HEAD `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb` | unveränderte Arbeitsbasis; neuere CORE-Basis nicht integriert |
| B1a | Code `14d9ab4b39945510c2d6eddbe75230122a393ba6`, Abschluss `eae42f43757c344784efdb53492c06eaf34f39ba` | bestehende reine Scene-/Step-/Eligibility-Verträge |
| B1b | Code `d059f827ed7120f64e92392b51d1fe5c5d3f38bd`, Abschluss `6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666` | Observer/Hook-Basis |
| B1c | Code `b8c4b606dea386e7f62e30d9563f9950836ae44c`, Abschluss `382e995496af336aab07ca1cf6f8a41e4e54160c` | lokal vorhanden; `F:/neutron-core-i01c`, `codex/neutron-core-i01c`, HEAD exakt gleich, initial sauber |
| I01d | separater Worktree/Branch sichtbar | Inhalt/Abschluss nicht untersucht; keine Funktion daraus als fertig eingestuft |

Q beruht auf lokaler Commit-/Diffprüfung und Lektüre von `AutomatedActionAdmission`, den vier geänderten Modulpfaden, relevanten Observer-/Rotation-/Packetpfaden. [ADMISSION_CONTRACT](F:/neutron-core-i01c/design/neutron-codex-core-i01c/ADMISSION_CONTRACT.md), [ACTION_CONSUMER_MATRIX](F:/neutron-core-i01c/design/neutron-codex-core-i01c/ACTION_CONSUMER_MATRIX.md) und [RESULTS](F:/neutron-core-i01c/design/neutron-codex-core-i01c/RESULTS.md) wurden gelesen. B1c verändert weder Player-/Minecraft-/Controllerpfade noch RotationManager, Scheduler, Placementservice oder PacketPipeline gegenüber B1b.

**T-report:** I01c meldet abschließend 475 Tests, 0 Failures, 0 Errors, 3 Skipped (472 nicht übersprungene Fälle), JDK 8/Maven offline. Neuer Testcode: Admission 9, Module 13, Placement 7 Fälle. **T-code:** Methodik und relevante Assertions gelesen; Ordinary/Extra-Paare teils mit synthetischer Zwei-Callback-Entscheidung, World/Input/Container als Fixtures. Keine neue Ausführung und keine Live-/GL-/144-FPS-Abnahme durch AC-R02.

## Tatsächliche Admission-Grenzen an B1c

`AutomatedActionAdmission.mayAttempt` liest den kanonischen Observer und CurrentStep, erfasst Scene frisch, prüft unveränderte Step-Identität nach capture und delegiert an reine Eligibility. Kein eigener Zustand/Budget, keine Reservation. No-Step, Extra, stale/unterbrochener Step, Scene-/Epoch-Wechsel und Suspension verweigern diese automatische Vorprüfung. Positive Admission ist kein Phasen-, Abschluss- oder Servererfolgsbeweis.

| Consumer | Vorprüfung / erhaltene Grenzen |
|---|---|
| Aura | Motion POST vor Scheduler, erneut vor POST-Autoblock und in `validHit`; Motion PRE nur Autoblock-Nebenwirkungen gated. PRE-Targeting/Rotation bleibt. Manueller AttackEvent-/Lifecycle-Cleanup bleibt Bestand |
| AutoClicker2 | EventUpdate vor CPS/Timer und unmittelbar zugehörigen künstlichen Use-Key-Writes; generierter Klick trotz physischer Taste bleibt synthetisch |
| HitDelayFix | EventUpdate PRE vor Listener-`clickMouse`, nach vorhandenen Picking-/Keychecks |
| Scaffold | gemeinsamer Anfang von `tryPlace(pre)` vor Diagnose/Slot/Service; PRE/POST-Placement nur nach Vorprüfung. Plan/Rotation/Timer/Tower/`releasePlacement` unverändert |

TickEvent liegt einmal pro Outer-Tick **außerhalb** des Player-Bodys; AutoHeal/Transfers sind daher nicht Ordinary-only migriert. InventoryManager/Stealer benötigen fachlich offene GUI und sind bei generischer Scene-Sicht SUSPENDED; keine pauschale GUI-Ausnahme ableiten. AutoTool, SlotHandler, MoreKB, NoSlow, WTap2, Velocity und allgemeine Slot-/Packetmodifikatoren sind nicht global geschützt. Manuelle clickMouse/rightClickMouse/windowClick bleiben außerhalb einer globalen Admission-Sperre. Laufende ACK-/Cleanup-Phasen sind von Initiierung zu unterscheiden.

## O-01 bis O-13: Addendum, historische IDs erhalten

| Frage | AC-R02-Status / nächster kleiner Nachweis |
|---|---|
| O-01 | **Quellenverfügbarkeit geschlossen (D):** Original-v1.0 gelesen. Kein zusätzlicher Numerik-/Produktgate dadurch geschlossen |
| O-02 | **geschlossen für B1c (Q):** geforderter Abschluss-HEAD lokal verifiziert; neuer Fachauftrag muss seinen eigenen Zielcommit prüfen |
| O-03 | offen/U: keine AC-JARs, Serverconfigs, Autorisierung oder Live-Traces im Paket; kleinster Schritt: Laborinventar und Genehmigung dokumentieren |
| O-04 | **Entwurf geliefert:** [Trace-Schema](AC_R02_TRACE_SCHEMA_DRAFT.md). Implementierung, Drop-/Overhead-/144-FPS-Messung offen |
| O-05 | **partiell geklärt Q/T-code:** vier Consumerpfade angebunden; keine globale Inventory-/NET-Policy. Weitere Consumer nur gesonderter Fachauftrag |
| O-06 | offen: Quantisierungsrest Q aus AC-R01, direkte Transport-/Serverwirkung U. T-02 bleibt; kein neuer Algorithmus |
| O-07 | offen/H: MoveFix-Inputrekonstruktion/Serverabweichung; T-06, keine Änderung von Präzise-Default |
| O-08 | **Produktvertrag geklärt D, Umsetzung offen Q:** Planende ≠ Ownerfreigabe; Priorität bis Disable, 2–4-Tick-Auslauf nur gültiger Kontext. H1/T-03 |
| O-09 | offen: Swing/Attack/Serverwirkung noch ohne Run-Korrelation; H2/T-04 präzisiert |
| O-10 | offen/H: Backtrack-/Tickbase-/LagRange-Nettonutzen, T-07 erst nach Labornachweis |
| O-11 | offen/U: Grim-Experimental-Config und Polar-Kanalabdeckung; Polar zuerst T-08 |
| O-12 | offen für konkrete Übernahme: Rechteklärung vor jedem Fremdcode-Port; AC-R02 übernimmt keinen Intave-Code |
| O-13 | offen/U: kein neuer NCP-Commit/JAR/Config-Pin; keine NCP-Anforderung ergänzt |

## Research-IDs und Produktgates

AC-CORE-DIAG-001 erhält B1c-Q/T-code für die bestehende Admission, H0 bleibt Exportentwurf. AC-INT-SCF-001 erhält den verbindlichen v1.0-Halte-/Release-Vertrag und B1c-Placement-Vorprüfung; Ownerlücke bleibt. AC-INT-CMB-001 erhält B1c-Aura-/Klickconsumer-Abgrenzung; Timingwirkung bleibt H. AC-INT-ROT-001, AC-INT-CMB-002, AC-INT-MOV-001, AC-INT-NET-001 behalten ihre bisherigen Q/H/U-Grenzen. AC-GRI-SCF-001/AC-GRI-ROT-001 und AC-POL-DIAG-001/AC-POL-BEH-001 erhalten keine neue Serverevidenz. T-01…T-08 bleiben erhalten; R02-Prüffälle sind Unterfälle dieser IDs.

Externe Pins bleiben ausschließlich AC-R01-Referenzen: Intave `b950bdf7570b8624b7907e2c42dfb3b6d63c40f6`, Grim 2.0 `1299a2aa35b2138df2604f9c7d6bcae357afef31`, PolarLogs `e02b9e78769dd403ac8d42f720a1fa53687d48b7`. Keine neue externe Recherche, keine Aussage zur Zielserver-Aktivierung. PolarLogs erklärt keine proprietären Polar-Checks.

`G-CMB-SPEED`: vier Yaw-/Pitch-Startwerte °/s; `G-SCF-ANGLES`: acht Basic-Face-/Winkelprofile; `G-MOV-SPEEDBASE`: Movement/Potion/Legacy-Mathematik. Getrennt offen, keine numerischen Defaults. v1.0 beschließt gemeinsames Min-/Max-CPS-Startintervall für Rhythmuspresets, delegiert dessen Herleitung, setzt aber keine sichere Zahl. AutoBlock-Ziel Off/Visual/Smart mit Visual-Default ist Produktziel, kein durch I01c umgesetzter Moduswechsel. Full Disabler/Fly und neue Bypassmodi bleiben außerhalb AC-R02.
