# CMB/SCF wave — tested local checkpoint

30 October 2026. Computer Use was stopped by the user's physical Escape key
before the IntelliJ launch action. Work stopped at that point; nothing was
published or merged. This is a tested implementation checkpoint, not a completed
gameplay/server release. Original runtime module configuration was restored
byte-for-byte against the saved SHA-256. No world was created or modified.

## Implementation and evidence ledger

| Ticket | Previous fault / RED | Correction | GREEN / live boundary |
|---|---|---|---|
| CMB-POST | Generation/epoch/GUI callback after C03 could renew source; failed scene sampling left authority | RotationManager ticket lifetime query + EntityPlayerSP post-transport validation inside reject/propagate boundary | C03PostWaveTest, full suite; actual transport diagnostics preserved; no in-game test |
| CMB-DISCONNECT | Packet POST removing sendQueue caused NullPointerException (1 runtime-error RED) | Use captured connection for actual transport status | Same disconnect regression GREEN; no server test |
| SCF-C09 | Disable/GUI/manual/replacement callback sent obsolete slot; old restore overrode newer manual choice (5 assertion REDs) | Guard selection and cleanup at Apply after PRE/barrier; retain manual noarg path | ScaffoldPacketWaveTest; exact C09 select -> C08 -> C09 restore positive control |
| SCF-C08 | In-place stack/position/face/hit mutation inherited original packet identity (4 assertion REDs) | Seal guarded controller envelope | Original repro cases + independent field-only controls GREEN |
| SCF-LIFETIME | AutoJump callback disable/re-enable/epoch let old PRE reapply timer (3 assertion REDs) | Local lifecycle generation; validate after actual Jump/Tower and placement callback | ScaffoldLifecycleWaveTest GREEN; immediate hard release retained |
| COMBAT | No separate reproduced combat defect | No Aura/Autoblock production edit | Additional real Aura POST equal-angle foreign-owner control, both request orders |
| H1-06 | Disabled rotation lease has no safe independent authority/expiry API | Explicit OPEN decision; no lease implementation | No 3-tick/200-ms PASS claimed |

Packet sealing is enforced at local Pipeline Apply/transport acceptance. It is
not a socket immutability guarantee, server ACK, or global block on a foreign
sender making a separate later send. Safe same-scene cleanup may release an
already accepted temporary slot after placement admission ends; it grants no new
placement right and cannot overwrite a newer manual selection.

## Tests

Temurin OpenJDK `1.8.0_482-b08`, Maven `3.9.15`, offline Maven. All five changed
production classes have bytecode major 52. Existing test assertions were retained.

| Run | Tests | Failures | Errors | Skips | XML suites |
|---|---:|---:|---:|---:|---:|
| Unmodified baseline | 724 | 0 | 0 | 3 native opt-in | 118 |
| Initial packet RED | 11 | 8 | 0 | 0 | 1 |
| Initial QA behavioral RED | 8 | 4 | 0 | 0 | 3 |
| Restore RED | 1 | 1 | 0 | 0 | 1 |
| Correct-priority C03 RED | 4 | 4 | 0 | 0 | 1 |
| Disconnect review RED | 1 | 0 | 1 | 0 | 1 |
| First focused GREEN | 102 | 0 | 0 | 0 | 9 |
| Final complete focus | 278 | 0 | 0 | 0 | 31 |
| Full Java8 + native opt-ins | **748** | **0** | **0** | **0** | **122** |

Final new coverage: 24 tests; 16 distinct assertion REDs and one runtime-error
RED, plus seven controls. Repeated intermediate runs are not added together.
The first QA import compile error and initially masked equal-priority C03 probes
are documented in RED_RECEIPTS.md and are not product-fix evidence. Native child
process output is not counted again on top of Surefire XML totals. No regression
tolerance changed. Full suite finished at 11:30:03 Europe/Berlin, 1m37s.

The final full invocation enabled `pulsar.world.overlay.native`,
`pulsar.chat.native`, `pulsar.test.glContext`, and the existing Windows LWJGL
native directory. Native GL/chat/overlay checks passed; this does not prove
Scaffold gameplay. Runtime Intellij launch and offline PRE/POST/manual/disable
checks remain **not performed**, because Computer Use was stopped before launch.
No authorized multiplayer login/server build/checkname/VL was available, and no
Intave/Polar or S08-based success is claimed.

## Agent receipt

| Agent | References / contribution | Files / review |
|---|---|---|
| A1 CMB | CMB owner/ticket/transport, walking/observation/movement sources; twelve adversarial boundaries | Read-only audit and post-transport API/diff review; no blocker |
| A2 SCF packets | Real SCF plan/controller/pipeline sources and historical contracts | New ScaffoldPacketWaveTest; read-only production review; field-isolation feedback incorporated |
| A3 lifecycle | SCF-1b, active-outer contract, actual Module unsubscribe/Scaffold cleanup | Read-only OPEN decision and AutoJump/generation RED design |
| A4 combat | Aura/AimAssist/Scheduler/Autoblock/controller + existing combat/admission tests | Read-only audit; no independent combat fix; consumer control handed to QA |
| A5 QA | Briefing matrix and real test fixtures/contracts | Three new Wave classes and all-42-case matrix; independent diff/reentrancy review |
| Lead | All mandatory briefing/project/reference documents, preflight, RED/GREEN, integration decisions | All production writes, test runs, docs and configuration rollback; no concurrent writers/builds |

No agents created commits or worktrees. See INVENTORY.md, DECISIONS.md and the
42-case TEST_MATRIX_RESULTS.md for classification, ownership and evidence limits.

## Git and local data

Checkout `F:\Puslar Backup - Copy`, branch `main`. Start/fetched origin/main and
current commit are `07b2e827ba935e31b9fb73125471ef8060d71f2e`. Five production
files, four new Wave test files and this report folder are **uncommitted**.
No new commit SHA, PR or merge SHA exists yet. No push, force-push, checkout,
stash/reset/clean or historical-branch integration was performed. `diff --check`
passes. Main still points to the verified origin baseline with local tested edits.

Initial untracked launcher/workflow/design/research data remains outside staging.
No additional worktree/clone was created or removed (0/0). Existing target and
test_run were reused. External bundle and required logs/XML evidence remain at
`F:\Pulsar-Recovery\analysis\cmb-scf-wave-20261030`; the small original-config
backup remains in `evidence/runtime-backup`, with exact restoration verified.
No accounts/assets/worlds or raw runtime logs are included in these source edits.

## Remaining gates

Gate A/B and local Gate C are complete. Gate D launch/gameplay is open after the
explicit Computer Use stop; Gate E publication/review/merge is unperformed.
Before publishing, reread status/current origin, inspect the complete prospective
diff, record source commits and actual runtime outcome, then use only the project
`codex/test-integration` PR workflow. H1-06 stays OPEN and is not an implemented
lease awaiting merge. No unrelated HUD/visual/Bezier/full-Aura changes were made.

Implemented: proven C03/C08/C09 and callback-lifetime corrections on existing CMB/SCF.
Demonstrated: 17 unique behavioral REDs resolved, 278 focus and 748 full tests GREEN.
Open: real gameplay/server checks, publication/merge and future SCF-1b lease contract.
