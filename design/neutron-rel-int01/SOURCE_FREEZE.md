# SOURCE_FREEZE — PASS

28.10.2026, Europe/Berlin. Neutron-Client / REL-INT01-v2.


Basis: `a75c52a178782dae9e8d7c1aa8e7546925a94bb0` (REL-P02).
CMB-v2/SCF-1a-Endpunkt: `0c685d0df913da760d7e652f8672ad9603a68190`.
VIS-Endpunkt: `31f63cd4af2765a402f358cb6902c2a794770fc9`.

Alle 45 manifestierten Nutzdateien stimmen in Bytes/SHA256; keine zusätzliche Nutzdatei. Separater Master und ZIP-Master sind identisch. Alle Nutzdateien wurden vollständig eingelesen. Der große ABI-Anhang wurde über alle 621 Tabellenzeilen und 621 Signatursektionen programmatisch traversiert. Die gemischt kodierte historische VIS-02-Ergebnisdatei wurde unverändert erhalten.

REL-P02, CMB-v2, QA02 und SCF-1a waren an den vollen Pins sauber. Lokale Patches wurden mit Receipt, Sidecar soweit vorhanden, tatsächlichen Bytes und BASE..HEAD-Diff verglichen; alle vier Reverse-Apply-Checks bestanden. Die 20 QA02-Testquellen und 125 finalen QA02-Bilder stimmen mit ihren Hashbelegen überein. Berichte sind historische Inputs; die gemeinsame neue Regression steht in INTEGRATION_RESULTS.

CORE-I01e und CMB-1a sind Vorfahren der Basis. VIS-01 ist über `a8ffbee2b76ef8f7e2b52a20407fcb111999be2e` und `36ca09f541fb8974f181ddaa8b0137f3dcc64a84` enthalten; deren stabile Patch-IDs stimmen mit `e3492be7a0a415491e87fda23b2cb7f282fa2a4c` und `7f79895f4033ba7e2e58af95bec2a3775d082ab0` überein. Keine erneute VIS-01-Übernahme.

Die tatsächliche DAG enthält zusätzlich `fca2d3e333d83e92662bb8ff5498372531a9bdb5` zwischen VIS-02-Code und `534b8c4db8d3a6633eea6e663a74be17d18aadda`. Er enthält vier VIS-02-Berichte und ist der direkte fehlende Dokumentationsvorfahr. Deshalb elf Picks statt der verkürzten Zehnerliste; Reihenfolge und Eltern wurden vor Integration geprüft.

| Original-SHA | Integrations-SHA |
|---|---|
| `3a4ed6ba11a5eabc59f72283183f87c4af87e022` | `8dbb3a4d48b00f77c7ba18bbe54e798d9de16b8e` |
| `ce7015cb8d8cc74ef4a0b17065167ff2bb98f4d4` | `d255c5f529a8bf0f671452a69ace52f1fef6cbd9` |
| `7fc3a6a1323c4f7c7017cfe66b63024b8ff77b35` | `aec3eb48eadf6cd3fc2e22998f5186be10314dca` |
| `0c685d0df913da760d7e652f8672ad9603a68190` | `a3c1c812f16e21ab5b7c4e4126ca2a65cf8e2675` |
| `635522d63a4b7edfb965e83eb8c52fc28d79445b` | `39dcf8dbbb0e8f954fb77466e25cc24efb2d67e7` |
| `fca2d3e333d83e92662bb8ff5498372531a9bdb5` | `70ec9a423b9b0e1552408d2b6b91acb21f2268d6` |
| `534b8c4db8d3a6633eea6e663a74be17d18aadda` | `1737dca665793424b1f355095fe2700d0441dbdc` |
| `525c96ff8d0650767cfdfc5fb940daca7b6f6369` | `34acb0daa69fa65ee7090da3b48741b94efaa904` |
| `a4142772ab3ff64747f22215f922c5891153fe47` | `67155dc2bb6b53ff2cee15d675a958d05d7a4d64` |
| `b5ad1626bf4f984b269881e31da4ee0b72d26227` | `39cafac5d720fa3294f19c37bb54d9569f0aa0fb` |
| `31f63cd4af2765a402f358cb6902c2a794770fc9` | `e497680dfa909a2e632aedda02d93c4b211bd231` |

SCF-S01, SCF-1b/1b-v2, H1-06 und der erste CMB-T01-STOPP sind ausgeschlossen. Der neue ausdrückliche Freeze ersetzt die historischen PENDING-Anforderungen. Scaffold bleibt für den ersten realen Playtest OFF; keine Release-Lease und kein C09-Fix werden behauptet. Fremde Dirty-/Launcher-/test_run-/logs-Dateien wurden nicht importiert.

Branch und Worktree waren vor Anlage frei. Der eigene Worktree wurde erst nach Gate A PASS exakt auf REL-P02 mit worktree-lokalen Sparse-Ausschlüssen angelegt. Nach späterer ausdrücklicher Nutzeränderung und exakter Pfadbestätigung wurde der Hauptcheckout zusätzlich sicher vorgezogen; dies erweitert allein die Hauptquellkonsolidierung, keine Lösch-/Push-/Spielstartfreigabe.

Belege: [`source-freeze.json`](F:/neutron-rel-int01-v2-evidence/source-freeze.json), [`manifest-verification.json`](F:/neutron-rel-int01-v2-evidence/manifest-verification.json), [`document-intake.json`](F:/neutron-rel-int01-v2-evidence/document-intake.json), [`abi-document-intake.json`](F:/neutron-rel-int01-v2-evidence/abi-document-intake.json), [`qa02-source-and-image-verification.json`](F:/neutron-rel-int01-v2-evidence/qa02-source-and-image-verification.json), [`user-scope-amendment.json`](F:/neutron-rel-int01-v2-evidence/user-scope-amendment.json).
