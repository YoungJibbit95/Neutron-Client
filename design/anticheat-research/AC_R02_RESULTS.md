# AC-R02 — Abschluss und Schreibnachweis

Revision 2.0 · 28.10.2026. Auftrag abgeschlossen; kein Beginn einer H1/H2-Implementierung oder AC-R03.

## Ergebnis

- B1c-Endcommit `382e995496af336aab07ca1cf6f8a41e4e54160c` und Codecommit `b8c4b606dea386e7f62e30d9563f9950836ae44c` lokal geprüft; I01c-Worktree initial/final sauber. Research-Branch bleibt auf B0. I01d wurde nicht als implementiert oder abgeschlossen bewertet.
- Original-v1.0 aus dem Quellenpaket gelesen: O-01 Quellenverfügbarkeit geschlossen, O-02 für B1c Q, O-05 nur für die vier konkreten Pilotconsumer geklärt. O-03 Labor/AC-Configs bleibt offen; O-04 ist nun ein Entwurf, kein fertiger Export.
- H0 liefert minimales Trace-Schema samt zwölf vorgeschlagenen Prüffällen; H1 beschreibt Keep-Rotations/Plantrennung und kontextgebundenen 2–4-Tick-Disable-Auslauf; H2 trennt Gelegenheit, C0A, C02, lokale Transportstufen und externe Serverwirkung. Je Handoff sieben synthetische Vorschläge und getrennte spätere AC-Abnahmen.
- I01c-Ergebnis 475/0/0/3 ist ausschließlich T-report des vorhandenen Abschlussberichts; relevante Tests gelesen (T-code). Keine Tests, Builds, Gameplay-Änderungen, Live-Versuche oder Git-Änderungsoperationen ausgeführt.

## Vier neue Dateien

1. [Baseline und Gaps](AC_R02_BASELINE_AND_GAPS.md)
2. [Trace-Schema, Entwurf](AC_R02_TRACE_SCHEMA_DRAFT.md)
3. [SCF-/CMB-Handoffs H1/H2](AC_R02_SCF_CMB_HANDOFF.md)
4. [Dieser Abschluss](AC_R02_RESULTS.md)

Quellen außerhalb aller Repositories entpackt unter `%TEMP%/neutron-ac-r02-sources-20261028`; zwölf SHA256-Manifest-Einträge verifiziert. Der separate Nutzerprompt steuert den Auftrag, Review und Paket dienen als quellengebundener Kontext. Die vier Paket-AC-R01-Dateien sind mit den lokalen historischen Dateien bytegleich. Keine neue externe Anticheat-Recherche und keine Fremdcodeübernahme.

## Schreibschutzprüfung

Genau die vier oben genannten AC-R02-Dateien wurden im Repository angelegt. SHA256 der vier historischen AC-R01-Dateien und der drei älteren Researchberichte vor/nach unverändert (7/7). Die vorher bestehenden getrackten/untracked Änderungen wurden weder bereinigt noch aufgenommen. Git wurde nur lesend mit `GIT_OPTIONAL_LOCKS=0`/`--no-optional-locks` genutzt; keine Branch-/HEAD-/Indexänderung beauftragt.

Fingerprint-Definition: SHA256 über die unveränderten stdout-Bytes von `git --no-optional-locks diff --binary HEAD --`, inklusive staged und unstaged getrackter Differenz gegenüber HEAD, ohne untracked Dateien. Dokumentinventar-, Hash- und lokale Linkprüfung sind Dateiprüfungen; keine ausgeführten Softwaretests.

### Research-Checkout

| Messung | Initial | Final |
|---|---|---|
| head | `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb` | `3ea55cbc34e14ad479454c6ce0d31e48e6f581fb` |
| branch | `prep/neutron-opencode-wave00` | `prep/neutron-opencode-wave00` |
| tracked_diff_sha256 | `ea20915ab1fd52e816248910c54fb0c4731989e928e5d7d64eea59b4f0dcc88d` | `ea20915ab1fd52e816248910c54fb0c4731989e928e5d7d64eea59b4f0dcc88d` |

Initiale vollständige `git status --short`-Liste (153 Einträge):

```text
 M .gitignore
 M .vscode/settings.json
 M test_run/logs/latest.log
 M test_run/options.txt
 M "test_run/saves/MCP Development World/data/Mineshaft.dat"
 M "test_run/saves/MCP Development World/data/Temple.dat"
 M "test_run/saves/MCP Development World/data/Village.dat"
 M "test_run/saves/MCP Development World/data/villages.dat"
 M "test_run/saves/MCP Development World/data/villages_end.dat"
 M "test_run/saves/MCP Development World/data/villages_nether.dat"
 M "test_run/saves/MCP Development World/playerdata/03ba29a1-d6bd-32ba-90b2-375e4d65abc9.dat"
 M "test_run/saves/MCP Development World/playerdata/04ba6478-628e-32a2-915d-0b45ca8b366b.dat"
 M "test_run/saves/MCP Development World/playerdata/08e49200-0644-3742-931a-94fcbb560d07.dat"
 M "test_run/saves/MCP Development World/playerdata/0f12271c-8f6c-3b4a-9805-4e9544c8a791.dat"
 M "test_run/saves/MCP Development World/playerdata/1178851a-8246-318e-85f8-1ae4517330a2.dat"
 M "test_run/saves/MCP Development World/playerdata/17b67947-156d-3518-9500-f7e04df5e64d.dat"
 M "test_run/saves/MCP Development World/playerdata/1b8221f7-5861-330e-bef0-ac96b785ff27.dat"
 M "test_run/saves/MCP Development World/playerdata/2210beb6-4dc2-38a8-8f45-f0cb49bb6846.dat"
 M "test_run/saves/MCP Development World/playerdata/23d7e7bc-6e91-3bb0-aef5-04f8d5ce94c1.dat"
 M "test_run/saves/MCP Development World/playerdata/25b11e49-a7c5-3d9c-8271-d4f68f211051.dat"
 M "test_run/saves/MCP Development World/playerdata/2654e3c3-150d-3857-a426-0b141796a4e0.dat"
 M "test_run/saves/MCP Development World/playerdata/2659329e-1c65-3850-9659-d27fc655aa3c.dat"
 M "test_run/saves/MCP Development World/playerdata/27fc360e-fa24-3dff-9bfe-79a065a3b88e.dat"
 M "test_run/saves/MCP Development World/playerdata/29aa321a-1ce7-35de-bfab-f37fa4095c7d.dat"
 M "test_run/saves/MCP Development World/playerdata/34692304-57b2-31f8-a84c-d22c6ff753f7.dat"
 M "test_run/saves/MCP Development World/playerdata/37815b4b-a277-3e88-89c9-687629f7c447.dat"
 M "test_run/saves/MCP Development World/playerdata/39a095b5-aea9-3421-a918-d077b0105370.dat"
 M "test_run/saves/MCP Development World/playerdata/3bdd7bc1-57b3-3ed1-a14e-cc7341dec1b1.dat"
 M "test_run/saves/MCP Development World/playerdata/3c098a03-04cc-31fd-856f-be4ccab4daf7.dat"
 M "test_run/saves/MCP Development World/playerdata/3c604947-c962-3363-b382-8bb20e72179d.dat"
 M "test_run/saves/MCP Development World/playerdata/3f66a67c-db6c-36b7-b762-d7a40c17883b.dat"
 M "test_run/saves/MCP Development World/playerdata/41196538-4c2b-371d-b141-0e51ad033fac.dat"
 M "test_run/saves/MCP Development World/playerdata/4292534d-f2c8-3960-ae02-8fc77d827539.dat"
 M "test_run/saves/MCP Development World/playerdata/446355e4-985a-3d9c-a63c-4d95aa4b0dff.dat"
 M "test_run/saves/MCP Development World/playerdata/45c2c1e2-9dd4-3d62-8e53-82e2b4b32b4a.dat"
 M "test_run/saves/MCP Development World/playerdata/4bf8ac1c-047f-3d7f-9946-705bc571459f.dat"
 M "test_run/saves/MCP Development World/playerdata/4c4af65a-e991-33a8-a762-2d145c215578.dat"
 M "test_run/saves/MCP Development World/playerdata/538c2a55-7afc-37c7-ab76-b8f0ee8467dd.dat"
 M "test_run/saves/MCP Development World/playerdata/5d6f5056-9d82-3950-9ee4-6a4e4ed3cf33.dat"
 M "test_run/saves/MCP Development World/playerdata/5f90bf1f-575d-3542-89d7-731859eee6f2.dat"
 M "test_run/saves/MCP Development World/playerdata/68723020-3c7d-332b-9007-b1f8c7a00304.dat"
 M "test_run/saves/MCP Development World/playerdata/6b70ab1d-ea1f-3080-82f2-6a9952eea911.dat"
 M "test_run/saves/MCP Development World/playerdata/6ecd1629-dee4-373d-9457-481bd36b0826.dat"
 M "test_run/saves/MCP Development World/playerdata/8c1ef9e6-3632-3ca3-9eee-559f6e0a39df.dat"
 M "test_run/saves/MCP Development World/playerdata/8ce64f24-c135-3001-9ff7-8b03d3719e4f.dat"
 M "test_run/saves/MCP Development World/playerdata/905847c0-6737-32d8-85e6-3f64ecd32033.dat"
 M "test_run/saves/MCP Development World/playerdata/9a9101ac-937d-31fc-99f0-be5bc89dd1bc.dat"
 M "test_run/saves/MCP Development World/playerdata/a129ebab-93ca-3ac8-86a9-47cce1be86d0.dat"
 M "test_run/saves/MCP Development World/playerdata/a2d3cb04-2e77-32ea-a568-7872ba7e9c5c.dat"
 M "test_run/saves/MCP Development World/playerdata/ac42e7c5-bf90-3ead-ba50-c551df161044.dat"
 M "test_run/saves/MCP Development World/playerdata/ad4a30d4-53bd-30c6-b482-5d77fb42b2d7.dat"
 M "test_run/saves/MCP Development World/playerdata/b743052f-5855-37e7-a97b-fd551acafef4.dat"
 M "test_run/saves/MCP Development World/playerdata/bd11acc2-8e1a-348b-89a6-a309159aa437.dat"
 M "test_run/saves/MCP Development World/playerdata/bf0c47d3-02b6-3463-8e38-457226b1407c.dat"
 M "test_run/saves/MCP Development World/playerdata/c98187a9-e17b-3f48-8a33-882e62d18d19.dat"
 M "test_run/saves/MCP Development World/playerdata/ca560dd3-ebf9-3fc9-b2e6-088534082c81.dat"
 M "test_run/saves/MCP Development World/playerdata/d2fd1c5d-4760-35a0-aa0f-c197e25af2ac.dat"
 M "test_run/saves/MCP Development World/playerdata/d566deca-6b21-32cb-af90-4edf09eeac2e.dat"
 M "test_run/saves/MCP Development World/playerdata/d60321ce-54ba-31e5-bac3-cbb6a732015a.dat"
 M "test_run/saves/MCP Development World/playerdata/d75ffc85-72b0-3b94-be32-d104d89a3a6a.dat"
 M "test_run/saves/MCP Development World/playerdata/dfa57433-6d23-31fd-a282-bdca1175e553.dat"
 M "test_run/saves/MCP Development World/playerdata/e2fd3051-a7ee-3ec9-ae1f-a1b6240b0501.dat"
 M "test_run/saves/MCP Development World/playerdata/e5a660b6-9d51-36ee-858e-f73e02a3f36f.dat"
 M "test_run/saves/MCP Development World/playerdata/e7c5c79a-91ee-3db1-afe8-a6ff4d4f15fa.dat"
 M "test_run/saves/MCP Development World/playerdata/e98cb9c7-30ea-33d5-9363-b24219bed40f.dat"
 M "test_run/saves/MCP Development World/playerdata/ea1dedcc-65ad-3e4c-bece-937c54b0cb65.dat"
 M "test_run/saves/MCP Development World/playerdata/eb3e37b3-0c87-3026-b460-637c38fd4fdd.dat"
 M "test_run/saves/MCP Development World/playerdata/f2ba1573-0c0d-3978-bb86-e2dc9c0eb9a4.dat"
 M "test_run/saves/MCP Development World/playerdata/f79c4f00-774e-3daf-ac9c-aebcbcc29c64.dat"
 M "test_run/saves/MCP Development World/playerdata/f8a1463c-07b2-3433-918f-386b8accf0f1.dat"
 M "test_run/saves/MCP Development World/playerdata/fd2b0ed7-411b-3fc7-aa79-261670dbe34f.dat"
 M "test_run/saves/MCP Development World/playerdata/fe207da4-45aa-3a2a-bf67-72a700290388.dat"
 M "test_run/saves/MCP Development World/playerdata/fe736038-102e-365b-a97e-eabc472944ec.dat"
 M "test_run/saves/MCP Development World/region/r.-1.0.mca"
 M "test_run/saves/MCP Development World/region/r.-1.1.mca"
 M "test_run/saves/MCP Development World/region/r.0.-1.mca"
 M "test_run/saves/MCP Development World/region/r.0.0.mca"
 M "test_run/saves/MCP Development World/region/r.0.1.mca"
 M "test_run/saves/MCP Development World/region/r.1.-1.mca"
 M "test_run/saves/MCP Development World/region/r.1.0.mca"
 M "test_run/saves/MCP Development World/region/r.1.1.mca"
 M "test_run/saves/MCP Development World/session.lock"
 M "test_run/saves/MCP Development World/stats/03ba29a1-d6bd-32ba-90b2-375e4d65abc9.json"
 M "test_run/saves/MCP Development World/stats/04ba6478-628e-32a2-915d-0b45ca8b366b.json"
 M "test_run/saves/MCP Development World/stats/08e49200-0644-3742-931a-94fcbb560d07.json"
 M "test_run/saves/MCP Development World/stats/0f12271c-8f6c-3b4a-9805-4e9544c8a791.json"
 M "test_run/saves/MCP Development World/stats/1178851a-8246-318e-85f8-1ae4517330a2.json"
 M "test_run/saves/MCP Development World/stats/17b67947-156d-3518-9500-f7e04df5e64d.json"
 M "test_run/saves/MCP Development World/stats/1b8221f7-5861-330e-bef0-ac96b785ff27.json"
 M "test_run/saves/MCP Development World/stats/2210beb6-4dc2-38a8-8f45-f0cb49bb6846.json"
 M "test_run/saves/MCP Development World/stats/23d7e7bc-6e91-3bb0-aef5-04f8d5ce94c1.json"
 M "test_run/saves/MCP Development World/stats/25b11e49-a7c5-3d9c-8271-d4f68f211051.json"
 M "test_run/saves/MCP Development World/stats/2654e3c3-150d-3857-a426-0b141796a4e0.json"
 M "test_run/saves/MCP Development World/stats/2659329e-1c65-3850-9659-d27fc655aa3c.json"
 M "test_run/saves/MCP Development World/stats/27fc360e-fa24-3dff-9bfe-79a065a3b88e.json"
 M "test_run/saves/MCP Development World/stats/29aa321a-1ce7-35de-bfab-f37fa4095c7d.json"
 M "test_run/saves/MCP Development World/stats/34692304-57b2-31f8-a84c-d22c6ff753f7.json"
 M "test_run/saves/MCP Development World/stats/37815b4b-a277-3e88-89c9-687629f7c447.json"
 M "test_run/saves/MCP Development World/stats/39a095b5-aea9-3421-a918-d077b0105370.json"
 M "test_run/saves/MCP Development World/stats/3bdd7bc1-57b3-3ed1-a14e-cc7341dec1b1.json"
 M "test_run/saves/MCP Development World/stats/3c098a03-04cc-31fd-856f-be4ccab4daf7.json"
 M "test_run/saves/MCP Development World/stats/3c604947-c962-3363-b382-8bb20e72179d.json"
 M "test_run/saves/MCP Development World/stats/3f66a67c-db6c-36b7-b762-d7a40c17883b.json"
 M "test_run/saves/MCP Development World/stats/41196538-4c2b-371d-b141-0e51ad033fac.json"
 M "test_run/saves/MCP Development World/stats/4292534d-f2c8-3960-ae02-8fc77d827539.json"
 M "test_run/saves/MCP Development World/stats/446355e4-985a-3d9c-a63c-4d95aa4b0dff.json"
 M "test_run/saves/MCP Development World/stats/45c2c1e2-9dd4-3d62-8e53-82e2b4b32b4a.json"
 M "test_run/saves/MCP Development World/stats/4bf8ac1c-047f-3d7f-9946-705bc571459f.json"
 M "test_run/saves/MCP Development World/stats/4c4af65a-e991-33a8-a762-2d145c215578.json"
 M "test_run/saves/MCP Development World/stats/538c2a55-7afc-37c7-ab76-b8f0ee8467dd.json"
 M "test_run/saves/MCP Development World/stats/5d6f5056-9d82-3950-9ee4-6a4e4ed3cf33.json"
 M "test_run/saves/MCP Development World/stats/5f90bf1f-575d-3542-89d7-731859eee6f2.json"
 M "test_run/saves/MCP Development World/stats/68723020-3c7d-332b-9007-b1f8c7a00304.json"
 M "test_run/saves/MCP Development World/stats/6b70ab1d-ea1f-3080-82f2-6a9952eea911.json"
 M "test_run/saves/MCP Development World/stats/6ecd1629-dee4-373d-9457-481bd36b0826.json"
 M "test_run/saves/MCP Development World/stats/8c1ef9e6-3632-3ca3-9eee-559f6e0a39df.json"
 M "test_run/saves/MCP Development World/stats/8ce64f24-c135-3001-9ff7-8b03d3719e4f.json"
 M "test_run/saves/MCP Development World/stats/905847c0-6737-32d8-85e6-3f64ecd32033.json"
 M "test_run/saves/MCP Development World/stats/9a9101ac-937d-31fc-99f0-be5bc89dd1bc.json"
 M "test_run/saves/MCP Development World/stats/a129ebab-93ca-3ac8-86a9-47cce1be86d0.json"
 M "test_run/saves/MCP Development World/stats/a2d3cb04-2e77-32ea-a568-7872ba7e9c5c.json"
 M "test_run/saves/MCP Development World/stats/ac42e7c5-bf90-3ead-ba50-c551df161044.json"
 M "test_run/saves/MCP Development World/stats/ad4a30d4-53bd-30c6-b482-5d77fb42b2d7.json"
 M "test_run/saves/MCP Development World/stats/b743052f-5855-37e7-a97b-fd551acafef4.json"
 M "test_run/saves/MCP Development World/stats/bd11acc2-8e1a-348b-89a6-a309159aa437.json"
 M "test_run/saves/MCP Development World/stats/bf0c47d3-02b6-3463-8e38-457226b1407c.json"
 M "test_run/saves/MCP Development World/stats/c98187a9-e17b-3f48-8a33-882e62d18d19.json"
 M "test_run/saves/MCP Development World/stats/ca560dd3-ebf9-3fc9-b2e6-088534082c81.json"
 M "test_run/saves/MCP Development World/stats/d2fd1c5d-4760-35a0-aa0f-c197e25af2ac.json"
 M "test_run/saves/MCP Development World/stats/d566deca-6b21-32cb-af90-4edf09eeac2e.json"
 M "test_run/saves/MCP Development World/stats/d60321ce-54ba-31e5-bac3-cbb6a732015a.json"
 M "test_run/saves/MCP Development World/stats/d75ffc85-72b0-3b94-be32-d104d89a3a6a.json"
 M "test_run/saves/MCP Development World/stats/dfa57433-6d23-31fd-a282-bdca1175e553.json"
 M "test_run/saves/MCP Development World/stats/e2fd3051-a7ee-3ec9-ae1f-a1b6240b0501.json"
 M "test_run/saves/MCP Development World/stats/e5a660b6-9d51-36ee-858e-f73e02a3f36f.json"
 M "test_run/saves/MCP Development World/stats/e7c5c79a-91ee-3db1-afe8-a6ff4d4f15fa.json"
 M "test_run/saves/MCP Development World/stats/e98cb9c7-30ea-33d5-9363-b24219bed40f.json"
 M "test_run/saves/MCP Development World/stats/ea1dedcc-65ad-3e4c-bece-937c54b0cb65.json"
 M "test_run/saves/MCP Development World/stats/eb3e37b3-0c87-3026-b460-637c38fd4fdd.json"
 M "test_run/saves/MCP Development World/stats/f2ba1573-0c0d-3978-bb86-e2dc9c0eb9a4.json"
 M "test_run/saves/MCP Development World/stats/f79c4f00-774e-3daf-ac9c-aebcbcc29c64.json"
 M "test_run/saves/MCP Development World/stats/f8a1463c-07b2-3433-918f-386b8accf0f1.json"
 M "test_run/saves/MCP Development World/stats/fd2b0ed7-411b-3fc7-aa79-261670dbe34f.json"
 M "test_run/saves/MCP Development World/stats/fe207da4-45aa-3a2a-bf67-72a700290388.json"
 M "test_run/saves/MCP Development World/stats/fe736038-102e-365b-a97e-eabc472944ec.json"
 M test_run/servers.dat
 M test_run/usercache.json
?? design/anticheat-research/
?? design/intave-bypass-research-plan-2026-10-28.md
?? design/intave-research-round-2-2026-10-28.md
?? design/opencode-core-r01/
?? design/opencode-wave01/
?? design/research-rotation-aura-scaffold-2026-10-28.md
```

Finale vollständige `git status --short`-Liste (153 Einträge):

```text
 M .gitignore
 M .vscode/settings.json
 M test_run/logs/latest.log
 M test_run/options.txt
 M "test_run/saves/MCP Development World/data/Mineshaft.dat"
 M "test_run/saves/MCP Development World/data/Temple.dat"
 M "test_run/saves/MCP Development World/data/Village.dat"
 M "test_run/saves/MCP Development World/data/villages.dat"
 M "test_run/saves/MCP Development World/data/villages_end.dat"
 M "test_run/saves/MCP Development World/data/villages_nether.dat"
 M "test_run/saves/MCP Development World/playerdata/03ba29a1-d6bd-32ba-90b2-375e4d65abc9.dat"
 M "test_run/saves/MCP Development World/playerdata/04ba6478-628e-32a2-915d-0b45ca8b366b.dat"
 M "test_run/saves/MCP Development World/playerdata/08e49200-0644-3742-931a-94fcbb560d07.dat"
 M "test_run/saves/MCP Development World/playerdata/0f12271c-8f6c-3b4a-9805-4e9544c8a791.dat"
 M "test_run/saves/MCP Development World/playerdata/1178851a-8246-318e-85f8-1ae4517330a2.dat"
 M "test_run/saves/MCP Development World/playerdata/17b67947-156d-3518-9500-f7e04df5e64d.dat"
 M "test_run/saves/MCP Development World/playerdata/1b8221f7-5861-330e-bef0-ac96b785ff27.dat"
 M "test_run/saves/MCP Development World/playerdata/2210beb6-4dc2-38a8-8f45-f0cb49bb6846.dat"
 M "test_run/saves/MCP Development World/playerdata/23d7e7bc-6e91-3bb0-aef5-04f8d5ce94c1.dat"
 M "test_run/saves/MCP Development World/playerdata/25b11e49-a7c5-3d9c-8271-d4f68f211051.dat"
 M "test_run/saves/MCP Development World/playerdata/2654e3c3-150d-3857-a426-0b141796a4e0.dat"
 M "test_run/saves/MCP Development World/playerdata/2659329e-1c65-3850-9659-d27fc655aa3c.dat"
 M "test_run/saves/MCP Development World/playerdata/27fc360e-fa24-3dff-9bfe-79a065a3b88e.dat"
 M "test_run/saves/MCP Development World/playerdata/29aa321a-1ce7-35de-bfab-f37fa4095c7d.dat"
 M "test_run/saves/MCP Development World/playerdata/34692304-57b2-31f8-a84c-d22c6ff753f7.dat"
 M "test_run/saves/MCP Development World/playerdata/37815b4b-a277-3e88-89c9-687629f7c447.dat"
 M "test_run/saves/MCP Development World/playerdata/39a095b5-aea9-3421-a918-d077b0105370.dat"
 M "test_run/saves/MCP Development World/playerdata/3bdd7bc1-57b3-3ed1-a14e-cc7341dec1b1.dat"
 M "test_run/saves/MCP Development World/playerdata/3c098a03-04cc-31fd-856f-be4ccab4daf7.dat"
 M "test_run/saves/MCP Development World/playerdata/3c604947-c962-3363-b382-8bb20e72179d.dat"
 M "test_run/saves/MCP Development World/playerdata/3f66a67c-db6c-36b7-b762-d7a40c17883b.dat"
 M "test_run/saves/MCP Development World/playerdata/41196538-4c2b-371d-b141-0e51ad033fac.dat"
 M "test_run/saves/MCP Development World/playerdata/4292534d-f2c8-3960-ae02-8fc77d827539.dat"
 M "test_run/saves/MCP Development World/playerdata/446355e4-985a-3d9c-a63c-4d95aa4b0dff.dat"
 M "test_run/saves/MCP Development World/playerdata/45c2c1e2-9dd4-3d62-8e53-82e2b4b32b4a.dat"
 M "test_run/saves/MCP Development World/playerdata/4bf8ac1c-047f-3d7f-9946-705bc571459f.dat"
 M "test_run/saves/MCP Development World/playerdata/4c4af65a-e991-33a8-a762-2d145c215578.dat"
 M "test_run/saves/MCP Development World/playerdata/538c2a55-7afc-37c7-ab76-b8f0ee8467dd.dat"
 M "test_run/saves/MCP Development World/playerdata/5d6f5056-9d82-3950-9ee4-6a4e4ed3cf33.dat"
 M "test_run/saves/MCP Development World/playerdata/5f90bf1f-575d-3542-89d7-731859eee6f2.dat"
 M "test_run/saves/MCP Development World/playerdata/68723020-3c7d-332b-9007-b1f8c7a00304.dat"
 M "test_run/saves/MCP Development World/playerdata/6b70ab1d-ea1f-3080-82f2-6a9952eea911.dat"
 M "test_run/saves/MCP Development World/playerdata/6ecd1629-dee4-373d-9457-481bd36b0826.dat"
 M "test_run/saves/MCP Development World/playerdata/8c1ef9e6-3632-3ca3-9eee-559f6e0a39df.dat"
 M "test_run/saves/MCP Development World/playerdata/8ce64f24-c135-3001-9ff7-8b03d3719e4f.dat"
 M "test_run/saves/MCP Development World/playerdata/905847c0-6737-32d8-85e6-3f64ecd32033.dat"
 M "test_run/saves/MCP Development World/playerdata/9a9101ac-937d-31fc-99f0-be5bc89dd1bc.dat"
 M "test_run/saves/MCP Development World/playerdata/a129ebab-93ca-3ac8-86a9-47cce1be86d0.dat"
 M "test_run/saves/MCP Development World/playerdata/a2d3cb04-2e77-32ea-a568-7872ba7e9c5c.dat"
 M "test_run/saves/MCP Development World/playerdata/ac42e7c5-bf90-3ead-ba50-c551df161044.dat"
 M "test_run/saves/MCP Development World/playerdata/ad4a30d4-53bd-30c6-b482-5d77fb42b2d7.dat"
 M "test_run/saves/MCP Development World/playerdata/b743052f-5855-37e7-a97b-fd551acafef4.dat"
 M "test_run/saves/MCP Development World/playerdata/bd11acc2-8e1a-348b-89a6-a309159aa437.dat"
 M "test_run/saves/MCP Development World/playerdata/bf0c47d3-02b6-3463-8e38-457226b1407c.dat"
 M "test_run/saves/MCP Development World/playerdata/c98187a9-e17b-3f48-8a33-882e62d18d19.dat"
 M "test_run/saves/MCP Development World/playerdata/ca560dd3-ebf9-3fc9-b2e6-088534082c81.dat"
 M "test_run/saves/MCP Development World/playerdata/d2fd1c5d-4760-35a0-aa0f-c197e25af2ac.dat"
 M "test_run/saves/MCP Development World/playerdata/d566deca-6b21-32cb-af90-4edf09eeac2e.dat"
 M "test_run/saves/MCP Development World/playerdata/d60321ce-54ba-31e5-bac3-cbb6a732015a.dat"
 M "test_run/saves/MCP Development World/playerdata/d75ffc85-72b0-3b94-be32-d104d89a3a6a.dat"
 M "test_run/saves/MCP Development World/playerdata/dfa57433-6d23-31fd-a282-bdca1175e553.dat"
 M "test_run/saves/MCP Development World/playerdata/e2fd3051-a7ee-3ec9-ae1f-a1b6240b0501.dat"
 M "test_run/saves/MCP Development World/playerdata/e5a660b6-9d51-36ee-858e-f73e02a3f36f.dat"
 M "test_run/saves/MCP Development World/playerdata/e7c5c79a-91ee-3db1-afe8-a6ff4d4f15fa.dat"
 M "test_run/saves/MCP Development World/playerdata/e98cb9c7-30ea-33d5-9363-b24219bed40f.dat"
 M "test_run/saves/MCP Development World/playerdata/ea1dedcc-65ad-3e4c-bece-937c54b0cb65.dat"
 M "test_run/saves/MCP Development World/playerdata/eb3e37b3-0c87-3026-b460-637c38fd4fdd.dat"
 M "test_run/saves/MCP Development World/playerdata/f2ba1573-0c0d-3978-bb86-e2dc9c0eb9a4.dat"
 M "test_run/saves/MCP Development World/playerdata/f79c4f00-774e-3daf-ac9c-aebcbcc29c64.dat"
 M "test_run/saves/MCP Development World/playerdata/f8a1463c-07b2-3433-918f-386b8accf0f1.dat"
 M "test_run/saves/MCP Development World/playerdata/fd2b0ed7-411b-3fc7-aa79-261670dbe34f.dat"
 M "test_run/saves/MCP Development World/playerdata/fe207da4-45aa-3a2a-bf67-72a700290388.dat"
 M "test_run/saves/MCP Development World/playerdata/fe736038-102e-365b-a97e-eabc472944ec.dat"
 M "test_run/saves/MCP Development World/region/r.-1.0.mca"
 M "test_run/saves/MCP Development World/region/r.-1.1.mca"
 M "test_run/saves/MCP Development World/region/r.0.-1.mca"
 M "test_run/saves/MCP Development World/region/r.0.0.mca"
 M "test_run/saves/MCP Development World/region/r.0.1.mca"
 M "test_run/saves/MCP Development World/region/r.1.-1.mca"
 M "test_run/saves/MCP Development World/region/r.1.0.mca"
 M "test_run/saves/MCP Development World/region/r.1.1.mca"
 M "test_run/saves/MCP Development World/session.lock"
 M "test_run/saves/MCP Development World/stats/03ba29a1-d6bd-32ba-90b2-375e4d65abc9.json"
 M "test_run/saves/MCP Development World/stats/04ba6478-628e-32a2-915d-0b45ca8b366b.json"
 M "test_run/saves/MCP Development World/stats/08e49200-0644-3742-931a-94fcbb560d07.json"
 M "test_run/saves/MCP Development World/stats/0f12271c-8f6c-3b4a-9805-4e9544c8a791.json"
 M "test_run/saves/MCP Development World/stats/1178851a-8246-318e-85f8-1ae4517330a2.json"
 M "test_run/saves/MCP Development World/stats/17b67947-156d-3518-9500-f7e04df5e64d.json"
 M "test_run/saves/MCP Development World/stats/1b8221f7-5861-330e-bef0-ac96b785ff27.json"
 M "test_run/saves/MCP Development World/stats/2210beb6-4dc2-38a8-8f45-f0cb49bb6846.json"
 M "test_run/saves/MCP Development World/stats/23d7e7bc-6e91-3bb0-aef5-04f8d5ce94c1.json"
 M "test_run/saves/MCP Development World/stats/25b11e49-a7c5-3d9c-8271-d4f68f211051.json"
 M "test_run/saves/MCP Development World/stats/2654e3c3-150d-3857-a426-0b141796a4e0.json"
 M "test_run/saves/MCP Development World/stats/2659329e-1c65-3850-9659-d27fc655aa3c.json"
 M "test_run/saves/MCP Development World/stats/27fc360e-fa24-3dff-9bfe-79a065a3b88e.json"
 M "test_run/saves/MCP Development World/stats/29aa321a-1ce7-35de-bfab-f37fa4095c7d.json"
 M "test_run/saves/MCP Development World/stats/34692304-57b2-31f8-a84c-d22c6ff753f7.json"
 M "test_run/saves/MCP Development World/stats/37815b4b-a277-3e88-89c9-687629f7c447.json"
 M "test_run/saves/MCP Development World/stats/39a095b5-aea9-3421-a918-d077b0105370.json"
 M "test_run/saves/MCP Development World/stats/3bdd7bc1-57b3-3ed1-a14e-cc7341dec1b1.json"
 M "test_run/saves/MCP Development World/stats/3c098a03-04cc-31fd-856f-be4ccab4daf7.json"
 M "test_run/saves/MCP Development World/stats/3c604947-c962-3363-b382-8bb20e72179d.json"
 M "test_run/saves/MCP Development World/stats/3f66a67c-db6c-36b7-b762-d7a40c17883b.json"
 M "test_run/saves/MCP Development World/stats/41196538-4c2b-371d-b141-0e51ad033fac.json"
 M "test_run/saves/MCP Development World/stats/4292534d-f2c8-3960-ae02-8fc77d827539.json"
 M "test_run/saves/MCP Development World/stats/446355e4-985a-3d9c-a63c-4d95aa4b0dff.json"
 M "test_run/saves/MCP Development World/stats/45c2c1e2-9dd4-3d62-8e53-82e2b4b32b4a.json"
 M "test_run/saves/MCP Development World/stats/4bf8ac1c-047f-3d7f-9946-705bc571459f.json"
 M "test_run/saves/MCP Development World/stats/4c4af65a-e991-33a8-a762-2d145c215578.json"
 M "test_run/saves/MCP Development World/stats/538c2a55-7afc-37c7-ab76-b8f0ee8467dd.json"
 M "test_run/saves/MCP Development World/stats/5d6f5056-9d82-3950-9ee4-6a4e4ed3cf33.json"
 M "test_run/saves/MCP Development World/stats/5f90bf1f-575d-3542-89d7-731859eee6f2.json"
 M "test_run/saves/MCP Development World/stats/68723020-3c7d-332b-9007-b1f8c7a00304.json"
 M "test_run/saves/MCP Development World/stats/6b70ab1d-ea1f-3080-82f2-6a9952eea911.json"
 M "test_run/saves/MCP Development World/stats/6ecd1629-dee4-373d-9457-481bd36b0826.json"
 M "test_run/saves/MCP Development World/stats/8c1ef9e6-3632-3ca3-9eee-559f6e0a39df.json"
 M "test_run/saves/MCP Development World/stats/8ce64f24-c135-3001-9ff7-8b03d3719e4f.json"
 M "test_run/saves/MCP Development World/stats/905847c0-6737-32d8-85e6-3f64ecd32033.json"
 M "test_run/saves/MCP Development World/stats/9a9101ac-937d-31fc-99f0-be5bc89dd1bc.json"
 M "test_run/saves/MCP Development World/stats/a129ebab-93ca-3ac8-86a9-47cce1be86d0.json"
 M "test_run/saves/MCP Development World/stats/a2d3cb04-2e77-32ea-a568-7872ba7e9c5c.json"
 M "test_run/saves/MCP Development World/stats/ac42e7c5-bf90-3ead-ba50-c551df161044.json"
 M "test_run/saves/MCP Development World/stats/ad4a30d4-53bd-30c6-b482-5d77fb42b2d7.json"
 M "test_run/saves/MCP Development World/stats/b743052f-5855-37e7-a97b-fd551acafef4.json"
 M "test_run/saves/MCP Development World/stats/bd11acc2-8e1a-348b-89a6-a309159aa437.json"
 M "test_run/saves/MCP Development World/stats/bf0c47d3-02b6-3463-8e38-457226b1407c.json"
 M "test_run/saves/MCP Development World/stats/c98187a9-e17b-3f48-8a33-882e62d18d19.json"
 M "test_run/saves/MCP Development World/stats/ca560dd3-ebf9-3fc9-b2e6-088534082c81.json"
 M "test_run/saves/MCP Development World/stats/d2fd1c5d-4760-35a0-aa0f-c197e25af2ac.json"
 M "test_run/saves/MCP Development World/stats/d566deca-6b21-32cb-af90-4edf09eeac2e.json"
 M "test_run/saves/MCP Development World/stats/d60321ce-54ba-31e5-bac3-cbb6a732015a.json"
 M "test_run/saves/MCP Development World/stats/d75ffc85-72b0-3b94-be32-d104d89a3a6a.json"
 M "test_run/saves/MCP Development World/stats/dfa57433-6d23-31fd-a282-bdca1175e553.json"
 M "test_run/saves/MCP Development World/stats/e2fd3051-a7ee-3ec9-ae1f-a1b6240b0501.json"
 M "test_run/saves/MCP Development World/stats/e5a660b6-9d51-36ee-858e-f73e02a3f36f.json"
 M "test_run/saves/MCP Development World/stats/e7c5c79a-91ee-3db1-afe8-a6ff4d4f15fa.json"
 M "test_run/saves/MCP Development World/stats/e98cb9c7-30ea-33d5-9363-b24219bed40f.json"
 M "test_run/saves/MCP Development World/stats/ea1dedcc-65ad-3e4c-bece-937c54b0cb65.json"
 M "test_run/saves/MCP Development World/stats/eb3e37b3-0c87-3026-b460-637c38fd4fdd.json"
 M "test_run/saves/MCP Development World/stats/f2ba1573-0c0d-3978-bb86-e2dc9c0eb9a4.json"
 M "test_run/saves/MCP Development World/stats/f79c4f00-774e-3daf-ac9c-aebcbcc29c64.json"
 M "test_run/saves/MCP Development World/stats/f8a1463c-07b2-3433-918f-386b8accf0f1.json"
 M "test_run/saves/MCP Development World/stats/fd2b0ed7-411b-3fc7-aa79-261670dbe34f.json"
 M "test_run/saves/MCP Development World/stats/fe207da4-45aa-3a2a-bf67-72a700290388.json"
 M "test_run/saves/MCP Development World/stats/fe736038-102e-365b-a97e-eabc472944ec.json"
 M test_run/servers.dat
 M test_run/usercache.json
?? design/anticheat-research/
?? design/intave-bypass-research-plan-2026-10-28.md
?? design/intave-research-round-2-2026-10-28.md
?? design/opencode-core-r01/
?? design/opencode-wave01/
?? design/research-rotation-aura-scaffold-2026-10-28.md
```

### I01c-Worktree

| Messung | Initial | Final |
|---|---|---|
| head | `382e995496af336aab07ca1cf6f8a41e4e54160c` | `382e995496af336aab07ca1cf6f8a41e4e54160c` |
| branch | `codex/neutron-core-i01c` | `codex/neutron-core-i01c` |
| tracked_diff_sha256 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |

Initiale vollständige `git status --short`-Liste (0 Einträge):

```text
(leer)
```

Finale vollständige `git status --short`-Liste (0 Einträge):

```text
(leer)
```

Die normale Short-Statusliste fasst den bereits zuvor untracked Forschungsordner zusammen; deshalb erscheint trotz vier neuer Dateien dieselbe Ordnerzeile. Das zusätzliche Dateiinventar und die geschützten Hashes grenzen den Schreibumfang ab.

Keine parallelen fremden Änderungen in den initial/final erfassten Status-/HEAD-/Diff-Fingerprints der beiden geprüften Checkouts festgestellt; andere Codex-Worktrees wurden nicht verändert oder als vollständig überwacht behauptet.

## Abschlussgrenze

Offen bleiben echte Laborkonfiguration, Server-/Polar-Kanalnachweise, Trace-Implementierung/Privacy-/Drop-/144-FPS-Abnahme sowie getrennte numerische Produktgates. Alle Servereffekte bleiben H/U. Ende nach AC-R02.
