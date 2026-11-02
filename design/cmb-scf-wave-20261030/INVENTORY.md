# CMB/SCF wave: inventory before production edits

30 October 2026. Lead: Codex. Local `main` and fetched `origin/main` both start at
`07b2e827ba935e31b9fb73125471ef8060d71f2e`. One registered checkout:
`F:\Puslar Backup - Copy`. No new branch, clone or worktree is needed.
The 18 briefing manifest hashes match the supplied ZIP. The bundle was absent at
the requested location and was extracted there, outside the repository.

Project AGENTS/workflow, all briefing files/tickets, and all mandatory
reference-index documents were read. Historical STOP instructions describe their
old tasks; the current user request authorizes this wave. The referenced complete
v1.0 specification is not present as an authoritative repository document; this
wave uses current code and the explicit conservative decisions in DECISIONS.md.

## Real callgraph

```text
Minecraft.runTick -> canonical observation.beginOuterTick (first ID 0)
 -> Tickbase -> observation.observePlayerStep (Ordinary or Earned Extra)
 -> EntityPlayerSP local body / physics
 -> onUpdateWalkingPlayer: bindConnection / beginMotion
 -> Motion PRE: Scaffold finalizes PRE plan / optional PRE placement
 -> current rotation request -> guarded C0B sprint/sneak
 -> RotationManager.resolve -> capture immutable C03 ticket
 -> C03/C04/C05/C06 -> Packet PRE/select -> existing movement delay/barrier
 -> final ticket guard -> transport -> observeAcceptedOutbound -> Packet POST
 -> finishMotion -> actual wire reporting -> Motion POST / optional POST placement
 -> finally endOuterTick (-1 active ID outside lifetime)
```

Withheld has an outer tick but no player body. Extra bodies share the outer ID and
cannot gain ordinary automated placement/attack admission. Physics stays before
new Motion PRE/C03. The pinned LiquidBounce legacy walking hook was inspected as
an architectural comparison; no external code is imported and no phase reorder
is justified.

Scaffold placement: immutable `PlacementTarget` (world/player, support/destination,
face/hit, selected/block slots, stack identity and copy) -> phase/source/sequence,
slot/geometry/raycast/delay/budget guards -> C09 selection -> guarded controller
-> C08 -> local ItemBlock result -> optional swing -> same-scene silent restore.
POST consumes the PRE frame target, never a newly searched target.

Aura: PRE target/request -> shared resolve/C03 -> POST ordinary admission +
accepted own pose/raycast -> AttackScheduler -> autoblock release -> final
controller/AttackEvent/C02 guard -> local result. Manual paths remain independent.

## Classification

| Area | Classification | Current evidence / action |
|---|---|---|
| CMB source PRE/POST, priority, pending vs accepted | ALREADY IMPLEMENTED | RotationOwnerPredicateTest; keep existing manager |
| C03 immutable resolve ticket, PRE cancel/rewrite/delay/nesting/epoch | ALREADY IMPLEMENTED | ResolvedC03SendTicketTest, ScaffoldLateC03TransportTest |
| C03 POST invalidation before finish; failed final sampling | REPRODUCED GAP | C03PostWaveTest, 4 assertion REDs with first-priority consumer |
| SCF immutable plan, camera/OFF, plan-gap accepted hold | ALREADY IMPLEMENTED | OwnerContinuity/OwnerPlacement/ClientFlow tests |
| C09 selection and restore validation at Apply | REPRODUCED GAP | 5 packet assertion REDs: disable, GUI, manual choice, replacement, restore |
| C08 in-place mutable packet envelope | REPRODUCED GAP | 4 REDs: stack, position, face, hit |
| AutoJump reentrant disable/re-enable/epoch | REPRODUCED GAP | 3 lifecycle REDs: old PRE reapplies timer |
| SCF-1b/H1-06 bounded disabled rotation lease | PRODUCT DECISION OPEN | Keep immediate hard release; see DECISIONS.md |
| Aura ownership/budget/manual arbitration | ALREADY IMPLEMENTED | Existing suite + new real Aura POST consumer positive control |
| Sprint basis PR13 / sensitivity grid PR14 | ALREADY IMPLEMENTED | Preserve all existing assertions/formulas |
| Bezier, general HUD/visual work, full Aura rewrite | OUT OF SCOPE | No changes |
| Multiplayer Intave/Polar | PRODUCT/ACCESS GATE OPEN | No authorized login or concrete server logs/check build supplied |

## Historical SCF acceptance mapping

All A01-A16 and H1-01-H1-07 refer to the actual SCF_1B_HANDOFF tests, not assumed
new work. A01-A11 and A13-A15 are already implemented. A12 is implemented only
as immediate hard release; its smoothing part remains deferred. A16's existing
callback/exception guards remain, with newly reproduced C09/envelope/AutoJump
boundaries added in this wave. H1-01/02/04 are retained; H1-03 gains C03-POST and
packet-envelope coverage; H1-05 retains active suspend/resume; H1-06 stays OPEN;
H1-07 gains callback-generation/exception evidence, with no lease guarantee.

## Preflight and ownership

Baseline `mvn -o test`: 724 tests, 0 failures, 0 errors, 3 existing native opt-in
skips, 118 fresh XML suites; Temurin 1.8.0_482-b08, Maven 3.9.15. No concurrent
Maven/IntelliJ build. Initial untracked launcher/workflow/design/research files
and ignored `.idea`, `test_run`, logs, target and local data are preserved.
Full initial status/worktree and XML receipts live under the external evidence
directory. `git diff --check` passed before production changes.

Five specialists were used, with at most three running concurrently: A1 CMB,
A2 SCF packets, A3 lifecycle, A4 combat, A5 independent QA. Research ran in
parallel; every file-writing slot and all builds/Git operations were serial.
Lead alone writes production and integrates Git. A2 wrote only its new packet
tests; A5 wrote only new Wave tests and the 42-case matrix.

Planned production scope: RotationManager ticket getter, EntityPlayerSP finish
boundary, ScaffoldPlacementService guarded sync, PlayerControllerMP guarded
C09/C08, Scaffold callback lifecycle. No PacketPipeline/CORE/POM/rotation-formula
or Aura changes. See TEST_MATRIX_RESULTS.md for every briefing matrix case.
