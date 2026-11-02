# Phase 1: GitHub handoff of the previous CMB/SCF wave

30 October 2026. This receipt supplements the historical checkpoint in
`design/cmb-scf-wave-20261030/RESULTS.md`; that report is deliberately preserved.

## Source and publication

Verified remote: `https://github.com/YoungJibbit95/Neutron-Client.git`.
Fetched `origin/main` remains `07b2e827ba935e31b9fb73125471ef8060d71f2e`.
The reviewed fixes are committed as
`aed53332d04e92a575a60ec0fa48ac9e1a0da257`, on
`codex/cmb-scf-fixes-20261030`, and pushed to that explicit remote branch.
The user's followup requests a separate fix branch/PR; this supersedes the older
single `codex/test-integration` publication default for these two waves.

The source commit contains exactly five production files, four new Wave test
classes and five historical wave documents. This receipt is a separate docs
commit. No runtime data, launcher work, untracked research, accounts or worlds
are staged. No force push, direct main push, stash, reset, clone or worktree.

## Independent review and fresh validation

The previous CMB, SCF packet, lifecycle and combat audits remain the design basis.
Independent QA reread the final diff and verified the original 122 XML suites,
24 new Wave cases and bytecode 52 for all five changed production classes.
No new production blocker was found. The captured-connection fix closes the
previous `sendQueue=null` review case.

A new full run on the committed code completed at **12:07:41 Europe/Berlin**:
**748 tests, 0 failures, 0 errors, 0 skips, 122 fresh Surefire suites**.
Temurin `1.8.0_482-b08`, Maven `3.9.15`, `mvn -o test` with native world-overlay,
chat-input and GL-context opt-ins and the existing Windows native directory.
Native child processes are not added to the XML count. Historical REDs remain
16 distinct assertion failures plus one runtime-error repro, all GREEN here.

Fresh logs/XML and the independent status/hashes are retained locally under
`F:\Pulsar-Recovery\analysis\cmb-scf-followup-20261030\evidence`.

## Actual game smoke

The existing IntelliJ **Neutron Client (Java 8)** configuration was launched in
the existing `test_run`. The process used Temurin 8u482 and displayed
`Neutron | Dev 2.2 by youngjibbit for NeutronTest`.

A new, separate Creative/Superflat world was created. Text injection from the UI
tool was not accepted by the LWJGL text field even after a focus retry, so its
actual local save name is **`New Worl`**. No existing world was opened or replaced.
Observed in the real game: terrain rendering; native E inventory; moving a stone
block to the hotbar; manual right-click placing a visible stone block; G enabling
Scaffold on solid ground with the diagnostic "Unter dem Spieler wird kein Block
benötigt"; G disabling it again (persisted state independently read as false);
Escape game menu; Save and Quit to Title; normal client Exit.

This is a partial gameplay smoke, not a full scaffold bridge or server pass.
The requested controlled PRE versus POST placement, Rotation OFF, and repeatable
bridge/resume matrix was not completed. UI text input was unavailable and some
actions required fresh observations after local user input. An RShift attempt did
not establish a visible GUI-open result; the original key remains **54** in source
and restored configuration. No claim of native RShift PASS is made.

The original module state and options were backed up before the smoke and restored
after the game process exited. The module file's SHA-256 matches its original
backup byte-for-byte. The newly created test world and small local backup remain
as evidence; no full client, dependency, target or test_run copy was made.

## Gates and PR status

Code/RED/GREEN review and Java8 Gate C pass. The client-launch part of Gate D now
passes, but the complete gameplay matrix remains OPEN. No private multiplayer
login, specific server build/checkname/VL or authoritative server result is
available. SCF-1b/H1-06 is deliberately not part of these fixes and remains OPEN
on this branch; it is the next separately reviewed feature decision.

PR 1 must therefore be **Draft** against GitHub `main`. Its description includes
these tests, file scope, reviews and open gates. No merge or release is authorized
by partial runtime evidence. Phase 2 uses a separate branch based on this fix
branch and a stacked PR while PR 1 remains unmerged.
