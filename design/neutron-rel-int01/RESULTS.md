# REL-INT01-v2 — lokaler Integrationsabschluss

28.10.2026, Europe/Berlin. Neutron-Client / REL-INT01-v2.


SOURCE_INTEGRATED und Java8-Test-/Packaginggate bestanden. Alle elf tatsächlich erforderlichen SCF/CMB/VIS-Picks sind lokal committed; vier getestete CMD-/Helper-/README-Artefakte ergänzen den Stand. Implementierungs-SHA vor Berichten: `556581c40df2632dd7639afeb406ad480d6ce91b`. Hauptcheckout F:\Puslar Backup - Copy auf Nutzerfolgeauftrag sicher vorgezogen; lokale POM-/Launcher-/Laufdatenänderungen bleiben erhalten. Die neue Releasekopie wird nach diesem Berichtscommit sauber abgeschlossen; endgültige SHA, kompletter Reviewpatch/SHA256 und Reversebeleg stehen extern.

| Phase | Status / Grenze |
|---|---|
| SOURCE_FREEZE | PASS; neue explizite Freezeentscheidung |
| SOURCE_INTEGRATED | PASS; elf -x-Picks konfliktfrei, 62 kanonische Endpunktdateien identisch |
| JAVA8_BUILD | PASS; zwei Clean-Suites und Package je 678/0/0/3; 111 XMLs je Vollsuite |
| ABI qualification | PARTIAL; 8 Paket/JNI und 9 ABI-Fälle grün, macOS/GLES/Patchy offen |
| CMD_CHECK_READY | PASS als Frontend; Runtime bleibt BLOCKED/Exit2 |
| CMD_START_READY | BLOCKED; getesteter NOT_READY-Starter ohne Gameaufruf |
| REAL_LOCAL_SMOKE | NOT_RUN; kein Opt-in, kein OS-Fenster-/Welt-/Exitbeleg |
| FRIEND_MULTIPLAYER | NOT_RUN; keine reguläre Auth-/2PC-Abnahme |
| DISTRIBUTION_RIGHTS | BLOCKED; voller Minecraft-/OptiFine-/Assetbestand |
| GITHUB_PUSH | NOT_RUN/BLOCKED; öffentliches Ziel verifiziert, keine Freigabe/Alpha-/Rechteclearance |
| CLEANUP_BACKUP | PARTIAL; 75 selektive Dokumente/Patches/Receipts rücklesegeprüft |
| CLEANUP_DELETE | NOT_RUN; exakte Pfadfreigabe PENDING, keine Löschung/Verschiebung |

Scaffold im ersten realen Playtest OFF. SCF-S01 und H1-06 bleiben ausgeschlossen; zwei reale synthetische Slotsonden bleiben erwartetes RED (2/1 C09 nach Disable, kein C08). Frühere Scaffold-NPE in zwei Clean-Suites, Package, zwei isolierten 31er-Läufen und gemischtem 58er-Lauf nicht reproduziert; keine behauptete Ursachenreparatur. Drei alte Native-Opt-in-Skips bleiben offen.

Alle sieben Pbufferprogramme bestehen; QA02 125 eigene Bilder und ausgeglichene 2:2-FBO-Probe, QA01 30:30, VIS-02 10:10. Keine FPS-, Display-, Shaderpack- oder globale UI-Freigabe. iOS-Untercontrols, AccountUI und die benannten Einzelvalue-/Optionsfedern bleiben DEFERRED.

13 CMD-Infrastrukturtests plus neun echte negative Preflights und drei zusätzliche reale Check-/Startfälle grün. Reale lokale Inventarchecks bestätigen Java8, gepinnten Assemblyhash, Manifest/EmbeddedClasses, Vanilla, Assets und Bibliotheken. Vollständige Laufzeit-, Isolations-, Session-/Loader- und Freigabegates bleiben blockiert.

Cleanup: 76 konkrete Pfade; 25 saubere, bereits per Commit erreichbare Kandidaten mit rund 35.46 GiB. Die vollständige Pfadliste und nur vorgeschlagene Kommandos stehen in CLEANUP_DRY_RUN. Ignorierte/private Restdaten und ganze Ordnerbackups bleiben vor Entfernung zu prüfen. Keine Löschfreigabe aus Quellintegration ableiten. Drei historische saubere Zweige besitzen jeweils einen nicht patchäquivalenten Commit und bleiben erhalten. Sämtliche Branchrefs, Evidence und fremde Launcherarbeit bleiben bestehen.

GitHub: YoungJibbit95/Neutron-Client existiert PUBLIC; alter Pulsar-origin unverändert. Kein Fetch/Push/Tag/PR/Remoteedit. Vor künftigem Push tatsächlicher Alpha-PASS, vollständiger Secret-/PII-/Lizenzscan der zu übertragenden Historie und exakte URL/Branch/SHA-Freigabe.

Berichte: SOURCE_FREEZE, INTEGRATION_RESULTS, CMD_LAUNCHER_GATE, ALPHA_PLAYTEST_RESULTS, DISTRIBUTION_DECISION, GITHUB_PUSH_GATE, CLEANUP_DRY_RUN und dieser RESULTS-Bericht; zusätzlich alle 32 Einzelfälle in GATE_MATRIX. Neue maschinenlesbare Prüfbelege und Binärartefakte liegen ausschließlich extern; solche Dateien wurden nicht zum Releasecommit hinzugefügt.

Belege: [`final-state.json`](F:/neutron-rel-int01-v2-evidence/final-state.json), [`gate-matrix.json`](F:/neutron-rel-int01-v2-evidence/gate-matrix.json), [`integration-provenance.json`](F:/neutron-rel-int01-v2-evidence/integration-provenance.json), [`main-source-consolidation.json`](F:/neutron-rel-int01-v2-evidence/main-source-consolidation.json), [`cleanup-receipt.json`](F:/neutron-rel-int01-v2-evidence/cleanup-receipt.json), [`push-gate.json`](F:/neutron-rel-int01-v2-evidence/push-gate.json), [`worktrees-comparison.json`](F:/neutron-rel-int01-v2-evidence/worktrees-comparison.json). Rohdiffs fremder Worktrees wurden vollständig im Speicher gemessen; aus Privacygründen nur Bytezahl/SHA gespeichert, keine Saves/Accountpayloads dupliziert. Keine allgemeine Untracked-Byteidentität behauptet.

STOPP nach diesem Auftrag. Keine Hintergrund-/Folgearbeit, kein Game-Start, keine Löschung oder Veröffentlichung nach Chatende.
