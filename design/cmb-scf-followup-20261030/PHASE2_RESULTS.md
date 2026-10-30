# CMB/SCF followup — completed code and GitHub handoff

30 October 2026, Europe/Berlin. Code and Java8 regression gates pass. Both
pull requests remain Draft because the complete game/server matrix and a
verified GitHub CI result are missing. No remote main merge or release.

## Delivery and dependency

| Round | Branch | Code commit | Handoff / PR |
|---|---|---|---|
| Previous 17 fixes | codex/cmb-scf-fixes-20261030 | aed53332d04e92a575a60ec0fa48ac9e1a0da257 | 5504fbb50ea8e2cbfec72abcbea260a0c1ac2025; [Draft #15](https://github.com/YoungJibbit95/Neutron-Client/pull/15) |
| New H2 + SCF-1b | codex/cmb-scf-wave2-20261030 | 3d6062a881f0f33a6c465ae391a415ea6a2c537b | [Draft #16](https://github.com/YoungJibbit95/Neutron-Client/pull/16); subsequent receipt commit changes only this report |

PR 15 targets GitHub main, verified/fetched at
`07b2e827ba935e31b9fb73125471ef8060d71f2e`. PR 16 targets
`codex/cmb-scf-fixes-20261030` at `5504fbb50ea8e2cbfec72abcbea260a0c1ac2025`.
It depends on PR 15 and is not an independent main patch. After PR 15 is accepted,
retarget/review PR 16 against the resulting main rather than merging out of order.

The historical `design/cmb-scf-wave-20261030/RESULTS.md` is preserved. The separate
[phase-1 receipt](PHASE1_HANDOFF.md), [preimplementation plan](PHASE2_PLAN.md) and
[RED ledger](PHASE2_RED_RECEIPTS.md) distinguish the two rounds and their evidence.

## Planned tasks implemented

**H2 / AC-INT-CMB-001 / T-04:** Extend existing CombatDiagnostics with a bounded
scalar Opportunity–Swing–Attack trace. Run/action/packet/event IDs distinguish
actual Aura opportunities, C0A and C02 local transport, denials, selected rewrites,
holds/barrier provenance, interrupted steps and stale tokens. Outside proved Aura
scope, origin is UNKNOWN. Server outcome is always UNKNOWN. Records/tokens retain
only primitives/enums; 120 records and explicit overwrite/capture/stale counters
bound the history. OFF returns before trace token/record/clock work. Existing
admission, scheduler, raycast and transport predicates are not rerun for logging.

**H1-06 / AC-INT-SCF-001 / T-03 / SCF-1b:** Adopt the separately documented
three-real-outer-advances / 200 ms rotation-only release, whichever ends first.
Two intermediate normally transported C03 frames can confirm the reserved
`scaffold-release` source; the third outer advance ends it. Extra bodies share
progress; Withheld advances the deadline without creating a body or packet.
Outer ID 0 is valid; an inactive historical ID is only an elapsed-time anchor
after actual acceptance. It grants no admission or synthetic send right.

Disable immediately unsubscribes Scaffold and clears its action/plan/input/slot/
timer/tower rights. Arm requires the original weak scene/observer/generation/
epoch/suspension evidence of an accepted resolved Scaffold C03, not a numeric
finish or pending wish. Ordinary/visible foreign offers, losing offers in the
disable frame and Aura rotations-OFF camera intent cancel release. Re-enable,
GUI/focus/pause, death, shutdown, identity replacement, observed ABA, epoch/
generation loss, tick wrap/backward movement and clock reversal revoke it.
Release tickets use the existing C03 seal and Apply/POST lifetime checks.

The new primitive observed-suspension marker preserves evidence of an observed
GUI-to-resume ABA without changing SceneContext identity/generation, eligibility,
step accounting or sampling on pure reads. No mutation without a callback or
detection of an entirely unobserved ABA is claimed.

Two real nested-walking review REDs required four added EntityPlayerSP lines:
check motion sequence and runtime-manager identity after resolve and after
finishMotion. Older callers then stop before ticket/send or reporting/POST work;
actual transport already completed is not rolled back. Existing packet, camera,
sprint, GCD and physics ordering remains in place.

The accepted release source loses both source and numeric accepted authority on
expiry/cancel. A pending lease that has never accepted its own C03 preserves the
pre-existing numeric wire basis from legacy reset("scaffold"); it grants neither
source nor new acceptance. This boundary is explicitly tested and is not an
unowned release right.

**Automatic PR rule:** AGENTS.md and docs/DEVELOPMENT-WORKFLOW.md now require a
separate codex branch/PR after every completed round, automatically Draft when
required gates remain open, and a stacked base for an unmerged dependency.
Normal commit/push/PR creation needs no new question. Merge/release remain gated.

Scope relative to PR 15: eight production files, seven new test/support files,
two project instruction files and three new phase-2 documents including this
receipt: **20 files**. No foreign research, launcher, runtime data, HUD/visual,
Bezier, CPS or full Aura rewrite is included.

## Reproducible RED / GREEN

Counts below are tests / failures / errors / skips. Repeated runs are not added.

| Evidence run | Result | Meaning |
|---|---|---|
| Phase-1 fresh full native | 748 / 0 / 0 / 0; 122 suites | Previous fixes, finished 12:07:41 CET |
| Phase-2 feature baseline | 20 / 17 / 0 / 0 | H2 7/6 and H1 13/11 before production; new acceptance gaps, not 17 more historical gameplay bugs |
| H2 independent review RED | 10 / 5 / 0 / 0 | Stale step-run completion, OTHER-selected C0A and PRE/predicate failure receipts |
| H2 corrected focus | 121 / 0 / 0 / 0 | All original 17 H2 cases and existing regression |
| H1 independent review RED | 278 / 2 / 0 / 0 | Only the two real nested-walking caller continuations failed |
| Same integrated focus GREEN | 278 / 0 / 0 / 0; 30 suites | Both caller corrections, finished 13:30:11 CET |
| Final full native GREEN | **792 / 0 / 0 / 0; 126 suites** | Finished **13:33:59 CET**, Maven 2:37 |

There are **44 new outer cases**: 17 H2, 13 release feature, three pure marker,
and 11 independent H1 boundary cases. H2 and boundary tests use fresh normal
Java8/JIT processes per assertion method, following the existing project pattern
for Unsafe/static-final Minecraft fixtures. Each outer case checks exactly one
child method, timeout, exit and success count. Child results are not counted a
second time. Original assertions/preconditions remain; no interpreter/compiler
disable, production workaround, POM change or skipped test is used.

The integration initially exposed fixture interference (267/14); preconditions
then reproduced inactive adapters before gameplay (267/17), and one isolated
method passed 1/0/0/0. Details and the later clean isolation are in the RED ledger.
The first full-native command failed PowerShell argument parsing before any
test; the corrected quoted command above supplied the actual final result.

Temurin `1.8.0_482-b08`, Maven `3.9.15`, offline, existing dependency/native caches:

```powershell
$env:JAVA_HOME = 'C:\Users\Administrator\.jdks\temurin-1.8.0_482'
& 'C:\Users\Administrator\scoop\apps\maven\3.9.15\bin\mvn.cmd' -o `
  '-Dpulsar.world.overlay.native=true' '-Dpulsar.chat.native=true' `
  '-Dpulsar.test.glContext=true' `
  '-Dorg.lwjgl.librarypath=F:\Puslar Backup - Copy\test_natives\windows' test
```

Only XML files written after the run start were archived and counted. Native
child output is not added to the Surefire total. javap verifies bytecode major
**52** for all eight changed production classes. `git diff --check` passes.
Raw logs, fresh XML, bytecode and preservation receipts remain under
`F:\Pulsar-Recovery\analysis\cmb-scf-followup-20261030\evidence`.

## Independent reviews

| Reviewer | Final local assessment |
|---|---|
| CMB owner / transport | GO; ticket/source/lease boundaries and optional H2 observation reviewed |
| SCF plan / packet | GO after both actual caller REDs and the two minimal identity/sequence checks |
| SCF lifecycle implementer | Product decision and lifecycle implementation complete; no parallel build/Git |
| Combat arbitration | GO for H2 and H1; rotations-OFF and visible foreign intent do not borrow release |
| Independent regression QA | GO; independently summed 30 GREEN XML suites, checked unchanged assertions and isolation |

These are local agent code/test reviews, not external AC-R03 or server approval.
Writers, Git and builds were serial in the sole checkout. Reviews/research and
independent test design were parallel; no overlapping source writers.

## Real runtime, CI and open gates

Phase 1 really launched the existing IntelliJ **Neutron Client (Java 8)** in
`test_run`, rendered a new Creative/Superflat world, used E inventory, placed a
visible stone block manually, enabled/disabled Scaffold on solid ground and
exited normally. Its separate own save is **New Worl**; no old save was opened or
replaced. This is a partial smoke, not sustained bridging or PRE/POST/Rotation-OFF
acceptance, and no native RShift GUI PASS is claimed.

During the phase-2 window inventory the Computer Use helper reported a stop by
the physical Escape key. No further Computer Use call/input was issued. The
applied [Computer Use SKILL.md](C:/Users/Administrator/.codex/plugins/cache/openai-bundled/computer-use/26.1027.30824/skills/computer-use/SKILL.md)
requires its linked guidance; Interrupted Turns says **"stop issuing app input"**
when Computer Use reports the user stopped it. The remaining code, tests and PR
handoff were independent of UI input and continued. Phase-2 gameplay remains
OPEN; native unit checks and synthetic/EmbeddedChannel cases do not replace it.

GitHub API queries on both published code/handoff SHAs returned **statuses=[]**
and **PR workflow_runs=[]**. There is no verified GitHub CI pass. No new CI or
foreign launcher workflow was published. Branch-protection approval and any
required CI check still need a real result; absence is not success.

Still open: controlled bridge/PRE-versus-POST/Rotation-OFF/disable-release/resume
gameplay matrix; authoritative server/build/checkname/VL evidence if server
acceptance is required; GitHub CI evidence and applicable merge approval.
Existing G-CMB-SPEED, G-SCF-ANGLES, G-MOV-SPEEDBASE and external AC-R03 research
gates are unchanged. No OptiFine/shader/GPU/frame-time or anticheat bypass/pass
claim. Both PRs stay Draft, with no remote main merge or release.

## Preserved work and final checkout

All **269 pre-existing untracked files** match their initial SHA-256 map.
Foreign launcher/workflow/design/research files remain local and uncommitted.
Module state `test_run/pulsar/configs/.last-state.json` matches its pre-smoke
backup byte-for-byte:
`F46D68178AD30F192A5161B842D2CCADE7EC9DC685AE352DF75138A29375342C`.
Options also match their original backup. No existing accounts, assets, saves,
IntelliJ files or unrelated work were discarded or staged.

The final local `main` carries the reviewed source and report by safe fast-forward
in `F:\Puslar Backup - Copy`. It is intentionally ahead of unchanged GitHub main
while the two Draft PRs await their gates; this is not a remote merge. Source
equality with tested commit 3d6062a881f0f33a6c465ae391a415ea6a2c537b is checked
after switching. The final receipt's commit SHA is visible as PR 16 HEAD.

Created worktrees/clones: **0**. Removed worktrees: **0**. Only the main checkout
remains. Local generated target/Surefire/native evidence, isolated-case logs,
small runtime backups and the new own test world remain; no full client or
test_run copy was made. Only obsolete generated classes belonging to the renamed
new tests were removed. No force push, direct origin/main push, stash/reset,
destructive cleanup or historical cherry-pick was used.
