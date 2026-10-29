# I01–I32 — vollständige Abnahmematrix

28.10.2026, Europe/Berlin. Neutron-Client / REL-INT01-v2.

Jedes Urteil gilt ausschließlich in der beschriebenen Grenze. NOT_RUN ist kein PASS. Final-HEAD und Patchhash stehen extern.

| Gate | Status | Konkreter Nachweis / Grenze | Externer Beleg |
|---|---|---|---|
| I01 | PASS | Exakter sauberer P02-Pin und lokaler Patch-/Receipt-/Reversebeleg | [`source-freeze.json`](F:/neutron-rel-int01-v2-evidence/source-freeze.json) |
| I02 | PASS | Exakter sauberer CMB-v2-Pin und Reviewprovenienz | [`source-freeze.json`](F:/neutron-rel-int01-v2-evidence/source-freeze.json) |
| I03 | PASS | QA02 clean, lokaler Patch/Sidecar/Receipt/Diff/Reverse; 125 Bildhashes | [`qa02-source-and-image-verification.json`](F:/neutron-rel-int01-v2-evidence/qa02-source-and-image-verification.json) |
| I04 | PASS | Expliziter neuer Freeze; SCF-S01/H1-06 OUT; Scaffold OFF | [`source-freeze.json`](F:/neutron-rel-int01-v2-evidence/source-freeze.json) |
| I05 | PASS | Ref/Pfad frei, eigener Worktree erst nach Gate A auf P02 | [`integration-provenance.json`](F:/neutron-rel-int01-v2-evidence/integration-provenance.json) |
| I06 | PASS | Alle 45 manifestierten Dateien korrekt; keine Extras | [`manifest-verification.json`](F:/neutron-rel-int01-v2-evidence/manifest-verification.json) |
| I07 | PARTIAL | 42 fremde Vor-Snapshots, Nachvergleich; Rohdiffbytes nur im Speicher/Hash aus Privacygründen; Hauptcheckout später ausdrücklich autorisiert aktualisiert | [`worktrees-comparison.json`](F:/neutron-rel-int01-v2-evidence/worktrees-comparison.json) |
| I08 | PASS | CORE/CMB Vorfahren, VIS-01 Patch-ID-Äquivalenz; kein Doppelpick | [`source-freeze.json`](F:/neutron-rel-int01-v2-evidence/source-freeze.json) |
| I09 | PASS | Beide SCF-1a-Picks, kanonische Endpunktbytes und aktive Tests | [`integration-provenance.json`](F:/neutron-rel-int01-v2-evidence/integration-provenance.json) |
| I10 | PASS | Beide CMB-v2-Picks nach SCF-1a, T01/T05/ID0/Reentry/Throwable | [`02-cmb-focused-summary.json`](F:/neutron-rel-int01-v2-evidence/02-cmb-focused-summary.json) |
| I11 | PASS | VIS-02-Code und beide tatsächlichen Dokumentationscommits; zentrale Phase | [`integration-provenance.json`](F:/neutron-rel-int01-v2-evidence/integration-provenance.json) |
| I12 | PASS | Beide QA01-Picks; sechs Consumer/JUnit/Pbuffer | [`native-receipts.json`](F:/neutron-rel-int01-v2-evidence/native-receipts.json) |
| I13 | PASS | QA02 Code/Docs, lokale Provenienz; DEFERRED bleibt ausgewiesen | [`integration-provenance.json`](F:/neutron-rel-int01-v2-evidence/integration-provenance.json) |
| I14 | PASS | Nur gepinnte Quellen und eigene Tools/Berichte; kein Dirty-/STOPP-/Launcherimport | [`integration-provenance.json`](F:/neutron-rel-int01-v2-evidence/integration-provenance.json) |
| I15 | PASS | Alle elf Picks konfliktfrei; keine Hunkentscheidung/kein behauptetes Fix-RED | [`integration-provenance.json`](F:/neutron-rel-int01-v2-evidence/integration-provenance.json) |
| I16 | PASS | 49 Altwerte/50.Selector, Cold/Warm-Legacy und Semantik frisch getestet | [`03-vis-focused-summary.json`](F:/neutron-rel-int01-v2-evidence/03-vis-focused-summary.json) |
| I17 | PASS | Aktive C03-Tickets einschließlich T01/T05 und Scene/Replay/Exceptions | [`02-cmb-focused-summary.json`](F:/neutron-rel-int01-v2-evidence/02-cmb-focused-summary.json) |
| I18 | PARTIAL | Aktive SCF-Basis grün; C09-Slotsonden 2/1 RED, H1-06 offen | [`known-c09-red-receipt.json`](F:/neutron-rel-int01-v2-evidence/known-c09-red-receipt.json) |
| I19 | PASS | Zwei frische Clean-Suites je 678/0/0/3, 111 XMLs, Java8 | [`06-full-clean-reverse-summary.json`](F:/neutron-rel-int01-v2-evidence/06-full-clean-reverse-summary.json) |
| I20 | PASS | Package 678/0/0/3, Assembly/Manifest/Services/Entries/Major52 | [`final-jar-audit.json`](F:/neutron-rel-int01-v2-evidence/final-jar-audit.json) |
| I21 | PARTIAL | 31/31/58 zusätzliche Fälle und drei Vollsuiten grün; Ursache nicht bewiesen/repariert | [`12-flake-mixed-reverse-summary.json`](F:/neutron-rel-int01-v2-evidence/12-flake-mixed-reverse-summary.json) |
| I22 | PARTIAL | 8 Paket/JNI + 9 ABI + 9 Negativfälle neu grün; Gesamt-ABI bleibt PARTIAL | [`09-abi-result.json`](F:/neutron-rel-int01-v2-evidence/09-abi-result.json) |
| I23 | PASS | Sieben echte Pbufferprogramme; FBO/Pixelbelege; kein Realspielanspruch | [`native-receipts.json`](F:/neutron-rel-int01-v2-evidence/native-receipts.json) |
| I24 | PASS | Check-Frontend read-only, Quoting und nonzero/Fehlercodevertrag geprüft | [`cmd-real-preflight-tests.json`](F:/neutron-rel-int01-v2-evidence/cmd-real-preflight-tests.json) |
| I25 | PASS | Startartefakt getestet fail-closed NOT_READY; tatsächliche Startbereitschaft BLOCKED | [`cmd-wrapper-tests.json`](F:/neutron-rel-int01-v2-evidence/cmd-wrapper-tests.json) |
| I26 | BLOCKED | Reale game/cwd/home-/Legitimitäts-/Egresskette und Opt-in fehlen | [`cmd-real-preflight-tests.json`](F:/neutron-rel-int01-v2-evidence/cmd-real-preflight-tests.json) |
| I27 | NOT_RUN | Kein echter Fenster/MainMenu/Welt/GUI/Exit-Smoke freigegeben/ausgeführt | [`native-receipts.json`](F:/neutron-rel-int01-v2-evidence/native-receipts.json) |
| I28 | BLOCKED | Kein rechtmäßiger Loader/Auth/Entitlement-/2PC-Multiplayernachweis | [`push-gate.json`](F:/neutron-rel-int01-v2-evidence/push-gate.json) |
| I29 | PARTIAL | 76-Pfad-Inventar und 75 rückgelesene Sicherungsdateien; keine vollständigen Ordnerbackups | [`cleanup-receipt.json`](F:/neutron-rel-int01-v2-evidence/cleanup-receipt.json) |
| I30 | PASS | Exakte Pfad-/Kommando-/Backup-/Approval-Matrix; keine Löschungen | [`cleanup-receipt.json`](F:/neutron-rel-int01-v2-evidence/cleanup-receipt.json) |
| I31 | BLOCKED | GitHub-Ziel PUBLIC verifiziert; Alpha, Rechte/Historienclearance, Remote-SHA und konkrete Freigabe fehlen | [`push-gate.json`](F:/neutron-rel-int01-v2-evidence/push-gate.json) |
| I32 | PASS | Selektive lokale Commits, eigener Cleanstatus, vollständiger Patch/SHA/Reverse und Schutzvergleich extern gebunden | [`final-state.json`](F:/neutron-rel-int01-v2-evidence/final-state.json) |
