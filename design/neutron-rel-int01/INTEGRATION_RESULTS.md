# INTEGRATION_RESULTS — SOURCE_INTEGRATED

28.10.2026, Europe/Berlin. Neutron-Client / REL-INT01-v2.


Eigener Branch `codex/neutron-rel-int01-private-playtest`, Worktree `F:\neutron-rel-int01-private-playtest`. Getesteter abschließender Implementierungsstand vor diesen Berichten: `556581c40df2632dd7639afeb406ad480d6ce91b`. Finale Berichts-SHA und Patchhash werden extern festgehalten; keine Selbsthashreferenz.

Elf konfliktfreie `cherry-pick -x`, nach jedem Pick `git diff --check`. Keine Konfliktfixes und kein künstliches RED/GREEN-Paar. 62 Dateien der SCF/CMB- und VIS-Endpunkte sind als Git-Blobs byteidentisch; normalisierte Worktree-Hashes stimmen. Windows-CRLF-Materialisierung ist separat erfasst. POM bleibt im Release-Worktree exakt REL-P02. Zwei eigene Implementierungscommits ergänzen ausschließlich vier CMD-/Helper-/README-Dateien; keine zusätzlichen Java-, Dependency- oder Gameplayänderungen.

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

| Lauf | frische XMLs | Tests/Failures/Errors/Skips | Exit |
|---|---:|---|---:|
| 01-scf-focused | 8 | 85/0/0/0 | 0 |
| 02-cmb-focused | 9 | 88/0/0/0 | 0 |
| 03-vis-focused | 15 | 88/0/0/0 | 0 |
| 04-full-clean-test | 111 | 678/0/0/3 | 0 |
| 06-full-clean-reverse | 111 | 678/0/0/3 | 0 |
| 07-package | 111 | 678/0/0/3 | 0 |
| 10-flake-isolated-a | 1 | 31/0/0/0 | 0 |
| 11-flake-isolated-b | 1 | 31/0/0/0 | 0 |
| 12-flake-mixed-reverse | 3 | 58/0/0/0 | 0 |

Oracle JDK 1.8.0_391 x64, Maven 3.8.5 ausschließlich offline `-o`; TEMP/TMP und java.io.tmpdir pro Prozess unter eigener externer Evidenz. Beide vollständigen Clean-Suites und Package ergeben unabhängig je 678/0/0/3 aus 111 frischen XMLs. Erst normaler, danach umgekehrt alphabetischer Klassenablauf. Die drei alten Opt-in-Skips betreffen WorldOverlayContractTest.nativeDispatchWhenRequested, GuiChatInputTest.nativeInputAndDrawingWhenRequested und GuiOverlayDebugGlStringsTest.realOffscreenContextRecreationRefreshesDriverStrings. Keine Addition historischer oder verschachtelter Testzahlen.

Scaffold-Flake: zweimal 31 isolierte Fälle und 58 gemischte Fälle zusätzlich grün. Der frühere NPE wurde nicht reproduziert; weder Ursache noch Reparatur sind bewiesen. Unsafe/static-final bleibt eine historische Hypothese. Keine Tests gelöscht, geschwächt oder neu übersprungen.

Frische Assembly: 123677330 Bytes, SHA256 `d603d69f9b4fc7a0ce508cb6353f3294549728dc48d8358f486c20dd888a2233`. Acht Paket-/JNI-Fälle und neun ABI-Fälle bestanden gegen genau diese Assembly. 63 Runtimeartefakte, 621 Überlappungen, 199 Byteabweichungen und 1198 Anwendungs-Memberreferenzen erneut geprüft. Alle 3634 kompilierten Projekt-Classfiles sind Major52. Archive, Manifest, Services, Ressourcen, Entrypoint und höhere Modul-/Multi-Release-Metadaten sind separat inventarisiert. Der no-session Main-Aufruf endet vor Clientkonstruktion mit fehlenden accessToken/version-Argumenten; kein Spielstart.

ABI bleibt PARTIAL: zwei macOS-Synthetic-Memberlücken, optionale GLES-Reflexion und Patchy-Semantik bleiben offen. Shade-/Thin-JAR bleiben ohne Startfreigabe. Keine POM-Änderung auf Verdacht.

Alle sieben frischen Pbufferprogramme bestanden. QA02: 125 eigene PNGs; QA01: 313 eigene plus zwei Armor-Hilfsbilder; VIS-02: 36; weitere MainMenu/Panel/Editor-Proben siehe native-receipts. QA02-FBO 2:2, QA01 30:30, VIS-02 10:10. QA01-Scaleprobe 216 Pixel/max. 0, VIS-02-Referenz 1280 Pixel/max. 1/255. Editor-, kleine iOS- und rote ERROR-Originalbilder wurden visuell geöffnet. Pbuffer ist kein natives Spielfenster und keine FPS-Abnahme.

Bekannte ausgeschlossene C09-Lücke frisch reproduziert: Disable im C09-PRE transportiert zwei C09, Disable im C08-PRE erzeugt ein Cleanup-C09; C08 bleibt jeweils 0. Beide externen Sonden bleiben erwartetes RED. H1-06/SCF-S01 nicht implementiert.

Hauptquelle: Auf ausdrücklichen Folgeauftrag wurde `F:\Puslar Backup - Copy` auf `556581c40df2632dd7639afeb406ad480d6ce91b` fast-forwarded, Branchname `codex/revived-final-integration` beibehalten. 147 andere zuvor geänderte getrackte Dateien blieben hashgleich; 129 untracked Namen erhalten (keine Behauptung über deren Bytes). Die lokale POM erhielt nur die kommende Main-Class-Hunk; sämtliche vorhandenen Repository-/vecmath-/launcher-dist-Ergänzungen bleiben unstaged. Vorher-POM extern bytegeprüft gesichert. Kein Reset, Stash, Clean oder Launcherimport. Der Hauptcheckout bleibt absichtlich dirty; dessen abweichende lokale POM ist nicht der qualifizierte Buildinput. Die späteren reinen Berichte werden anschließend per weiterer FF-Übernahme nachgeführt und extern protokolliert.

Vorläufige Prüfwerkzeugkorrekturen sind erhalten: ungültiger SystemRoot-Negativfixture ersetzt; Windows-PowerShell-Modulpfad prozesslokal korrigiert; Rohdiffgleichheit nach Basiswechsel durch genaue Erhaltungsorakel ersetzt; Git-Blob/CRLF-Oracle getrennt. Kein Java-Produktfehler dadurch als repariert ausgegeben.

Belege: [`integration-provenance.json`](F:/neutron-rel-int01-v2-evidence/integration-provenance.json), [`04-full-clean-test-summary.json`](F:/neutron-rel-int01-v2-evidence/04-full-clean-test-summary.json), [`06-full-clean-reverse-summary.json`](F:/neutron-rel-int01-v2-evidence/06-full-clean-reverse-summary.json), [`07-package-summary.json`](F:/neutron-rel-int01-v2-evidence/07-package-summary.json), [`08-packaged-result.json`](F:/neutron-rel-int01-v2-evidence/08-packaged-result.json), [`09-abi-result.json`](F:/neutron-rel-int01-v2-evidence/09-abi-result.json), [`abi-summary.json`](F:/neutron-rel-int01-v2-evidence/abi-summary.json), [`bytecode-major52.json`](F:/neutron-rel-int01-v2-evidence/bytecode-major52.json), [`native-receipts.json`](F:/neutron-rel-int01-v2-evidence/native-receipts.json), [`known-c09-red-receipt.json`](F:/neutron-rel-int01-v2-evidence/known-c09-red-receipt.json), [`main-source-consolidation.json`](F:/neutron-rel-int01-v2-evidence/main-source-consolidation.json).
