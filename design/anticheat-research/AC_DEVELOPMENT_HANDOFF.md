# Neutron 2.0 — AC-R01 Entwicklungsübergabe

Revision 1.0 · 28.10.2026. Read-only Research-Auftrag; Anforderungen und Tests zur späteren fachlichen Entscheidung. [Register](AC_RESEARCH_REGISTER.md) liefert Versionen/Evidenz, [Protokoll](AC_TEST_PROTOCOL.md) die Runs. Keine neue Implementierungsfreigabe oder Änderung des laufenden CORE-Zyklus.

## Verbindlicher Integrationsstand

Geprüfte abgeschlossene Basis ist B1b `6676fbcb8587c5ffa8928cbeb89c9a79ef6e3666` in `F:/neutron-core-i01b`. B0 im Research-Checkout bleibt älter. B1a/B1b sind lokal verfügbar und gelesen, nicht hier integriert. I01c zeigt uncommittete Consumer-/Admission-Arbeit; deren Inhalt und Testerfolg sind nicht Grundlage dieser Übergabe. Fachbereiche prüfen ihren tatsächlichen Zielcommit vor Umsetzung neu.

`SceneContext`, `ExecutedStep` (ORDINARY/EARNED_EXTRA), `WithheldObservation`, `AutomationEligibility` und `RuntimeObservation` verwenden. Keine API namens `StepContext` aus alten Research-Skizzen voraussetzen. `RuntimeServices.observation()` kann vor Bootstrap null sein. `getLastSteps`/`getLastWithheld` sind Diagnose der letzten Entscheidung, keine aktuelle Berechtigung. `getCurrentStep` im laufenden Body ist vorläufig; Eligibility allein bestätigt weder Abschluss noch korrekte Aktionsphase.

Automatische Aktionen benötigen frisch geprüften aktuellen Kontext und passenden Outer-Tick; vorhandene Owner-, Raycast-, Slot-, Controller- und Budget-Gates bleiben maßgeblich. Eligibility konsumiert/reserviert nichts und gilt nicht als globaler Vanilla-Interceptor. Withheld hat keinen Body; Extra erzeugt keine neue automatische Attack-/Placement-/Inventory-Berechtigung. Runtime-Consumer-Anbindung war in B1b noch nicht umgesetzt; I01c gehört seinem bestehenden Auftrag.

PRE-Placement geschieht vor applyRotations/resolve/aktuellem C03 und braucht vorherige lokal akzeptierte Pose plus fachlich gültigen Owner. POST braucht die aktuelle lokal akzeptierte Pose plus Owner. Scene/Plan/Slot/Face/Raycast bleiben zusätzliche Bedingungen. Beobachtung und Transportannahme bestätigen keinen Serverblock.

## Schnittstellenkarte

| Bedarf | Status an B1b | Zuständigkeit / Grenze |
|---|---|---|
| Scene/Generation/Epoch, aktuelle Steps/Withheld | vorhanden: CORE-Verträge und Observer | CORE besitzt Adapter; Research/VIS nur lesender Consumer, frische Prüfung nötig |
| Rotation-Requests/effektive Pose/Owner | vorhanden: RotationManager, RotationCorrections | CMB; Owner-Halteverhalten Scaffold noch Facharbeit |
| Attack-/Placement-/Inventarpfade | vorhandene Scheduler/Controller/Placement-Guards | CMB/SCF/MOD; keine zusätzliche Domain-Reservation |
| Packet FIFO/Holds/Barriere/Lifecycle | vorhanden: PacketPipeline/PacketDelays | NET; keine zweite Queue oder Replay-Implementierung |
| Timer-Lease/Neutralität | vorgeschlagen/offen, kein TimerArbiter in B1b | CORE/MOV; neutral verlangt Besitz, gültige Rate und ungeklärte Fremdwrites berücksichtigen; identische Fremdwrites per Wertvergleich nicht beweisbar |
| Raw-/Applied-Input-Snapshot/Arbitration | vorgeschlagen, nach MOV-1 verschoben | MOV; frische Eingabe vor Physik und Sprint/Sneak vor C0B getrennte Phasen |
| Begrenzter Trace-/Exportadapter | vorgeschlagen; Observer enthält keine Historie | CORE/NET; keine neue Session-/Configverwaltung, keine starken World-/Player-Referenzen halten |
| Item-Use-Abstimmung | vorgeschlagener Fachvertrag | CMB/MOV/MOD über existierende Pfade; kein neuer Packet-/Input-Store |
| Serverwirkung/Check-Export | Laborbedarf, kein Clientbeweis | Testserver-Owner; Polar mit PolarLogs separat, sonst unbekannt |

## Kleine priorisierte Übergabepakete

| Paket | Research-IDs / Test | Fachanforderung und Abschluss |
|---|---|---|
| H0: Diagnosezuordnung | AC-CORE-DIAG-001 / T-01 | CORE/NET ordnen Run, Step und Scene anhand vorhandener Werte zu. Scope-/Exception-/Withheld-/Resume-Fälle und unveränderte Eventfolge belegen; Export erst gesondert beauftragen |
| H1: Scaffold-Ownership | AC-INT-SCF-001 / T-03 | SCF mit CMB trennt Planende und Ownerfreigabe gemäß Produktprinzip. Zuerst aktuelle I01c-Admission berücksichtigen. PRE/POST, konkurrierende Aura, Sceneverlust und Disable separat abnehmen |
| H2: Combat-Sequenz | AC-INT-CMB-001 / T-04 | CMB/NET machen Gelegenheit, C0A, C02 und Serverwirkung unterscheidbar. Bestehende Barrieren und erneute Validierung nach Flush erhalten; kein Scheduler-Tuning allein aus CPS-Setting |
| H3: Rotationsevidenz | AC-INT-ROT-001 / T-02 | CMB erstellt bei späterem Auftrag direkten Quantisierungs-/Transportnachweis einschließlich Rest, Wrap und Clamp. Auswahl eines Algorithmus folgt Messung und fachlicher Entscheidung |
| H4: Movement-/Item-Kombination | AC-INT-MOV-001, AC-INT-CMB-002 / T-05/06 | MOV/CMB/MOD prüfen Inputbasis, Sprint-/Sprungimpuls und tatsächlichen Itemeffekt. Kein stiller Wechsel von PRECISE/Speedbasis/Legacy-Defaults |
| H5: Network-Releases | AC-INT-NET-001 / T-07 | NET bewertet Delay/FIFO/Barriere und Extra-Schritte mit serverseitigem Nettoeffekt und nachfolgender Timer-Mitigation. Kein Umgehen vorhandener Action-Gates |

Grim-spezifische T-02/T-03-Abnahmen separat nach gepinntem Build/Config führen (AC-GRI-*); Polar erst Logging-Abdeckung T-08 klären (AC-POL-*). Erkenntnisse ändern keine Intave-Anforderung allein aufgrund ähnlicher Checknamen.

## Sieben Rollen und offene Gates

CORE verantwortet Verträge und Lifecycle-Beobachtung; CMB Rotation/Attack/Autoblock; SCF Geometrie/Plan-/Owner-Laufzeit; MOV Input/Sprint/Jump/Speed/Velocity/NoSlow; NET Pipeline/Delay/Tickbase/Trace; MOD Settings-/Slot-/Config-Verträge; VIS Darstellung vorhandener Diagnosewerte ohne Gameplay-Schreiben. Numerische Freigaben bleiben getrennt: vier Rotationsgeschwindigkeiten `G-CMB-SPEED`, acht Scaffold-Profile `G-SCF-ANGLES`, Movement-Basis/Potion `G-MOV-SPEEDBASE`. Timer-/Input-Mechanismen, Altkonfigmigration und Legacy-Modi sind nicht durch Research entschieden.

Keine Abhängigkeit des laufenden I01c-Abschlusses von unzugänglichen Serverdaten oder diesem Research-Backlog einführen. Freigabe erfolgt je kleinem Fachpaket nach Register-/Protokollkriterien. Allgemeines Fly, Full Disabler und universelle sichere Einstellungen sind keine Entwicklungsanforderungen dieses Auftrags.
