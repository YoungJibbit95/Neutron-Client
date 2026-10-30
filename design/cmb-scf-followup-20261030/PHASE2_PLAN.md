# Phase 2 contract and acceptance plan — before production edits

30 October 2026. Base: `5504fbb50ea8e2cbfec72abcbea260a0c1ac2025`, PR
[#15](https://github.com/YoungJibbit95/Neutron-Client/pull/15), branch
`codex/cmb-scf-fixes-20261030`. Work branch:
`codex/cmb-scf-wave2-20261030`. PR 2 is stacked on PR 1 while it is unmerged.
One checkout; serial writer, Git and build ownership remains with the lead.

## Selected planned tasks

| Task | Existing plan source | Deliverable / dependency |
|---|---|---|
| H2 / AC-INT-CMB-001 / T-04 | AC_DEVELOPMENT_HANDOFF, AC_R02_SCF_CMB_HANDOFF H2, research register P1 | A small opt-in scalar correlation adapter for actual Aura opportunity, C0A/C02 stages and local transport; existing scheduler/guards remain authoritative |
| H1-06 / AC-INT-SCF-001 / T-03 / SCF-1b | Tracked SCF_1B_HANDOFF A12/H1-06 and its proposed counting contract; prior wave DECISIONS D1 | Bounded disabled rotation release in the existing RotationManager; no disabled normal module listener or automated action |
| PR policy | Current user request | AGENTS.md and development workflow require a separate automatic PR per completed round, Draft for open gates, stacked bases where needed |

The AC research handoffs exist as local untracked reference material. They are
not imported or staged as foreign work. Their relevant H2 requirement is restated
here: opportunity, swing and attack are a partial order; a locally accepted C0A
may precede a denied C02. Queue holds currently apply to C03; C0A/C02 are barriers.
Server hits are not implied. H2-01..07 describe normal action, admission denial,
target loss, queue/rewrite, manual origin, lifecycle and interrupted/drop behavior.
This request authorizes the next implementation; historical research STOP notes
do not authorize unrelated rewrites. No invented CMB-2 task or complete Aura rewrite.

## H2 boundary

Use the existing CombatDiagnostics toggle/history and a bounded scalar record
ring. Records/tokens retain primitive values/enums only: run/action/packet/event
IDs, cause-action for barrier flushes, monotone time, outer/step, observed scene
generation, epoch, origin, actual stage/outcome. Outside a proved Aura scope,
origin remains UNKNOWN; a held physical key does not prove a manual caller.
Server outcome is always UNKNOWN. Reuse existing console logging; no HUD layout,
new settings screen, export subsystem, packet queue or gameplay budget.

Instrument actual branch results once: opportunity/admission and scheduler result
in Aura, selected/cancelled/held/barrier/denied/locally accepted in PacketPipeline,
existing controller events in Diagnostics. Do not call admission/raycast/transport
predicates again to obtain a log value. OFF returns before token/record/string/
clock allocation. Records cannot throw a new gameplay failure. Overwrite/capture
failures and interrupted scopes are explicit. Reentrant scopes restore their
own prior action without attaching an old packet to a new run.

Before production, a new regression using the existing history API must prove
the missing correlated Opportunity/C0A/C02 facts on a real Ordinary Aura path.
This is a missing diagnostic feature, not a claimed attack behavior defect.

## H1-06 product decision (new, not historical approval)

Approve a conservative **rotation-only** release: at most three real outer-ID
advances or 200 ms monotone time, whichever ends first. The existing manager owns
the sole optional lease. A separate reserved source `scaffold-release` can only
be generated internally and only confirmed by a fresh real C03 ticket.
`canUseRotation("scaffold", ...)` is false immediately at disable. Disabled
MoveFix/jump/body/placement/slot/timer/tower/input consumers stay disabled.

Arm only from an actually accepted Scaffold pose in the same active, living,
focused scene/manager/observer/connection/pipeline. Clear normal Scaffold plans
and source synchronously. Re-enable cancels the old lease before new work. A
regular foreign request wins and permanently cancels release, including visible
AimAssist and Aura with rotations OFF; no Scaffold-priority reservation remains.
Observe losing foreign offers in the disable frame conservatively.

The normal walking `resolve` may interpolate shortest wrapped yaw/pitch toward
the current camera, preserving the existing GCD grid and clamp. It creates no
extra C03, tick, thread or timerloop. Extend the existing Scaffold ticket contract
to release frames; a renamed source must never become a neutral transport path.

Start ID 0 is valid. Outside a tick, historical last outer ID may anchor elapsed
progress after a real accepted frame; it grants no send/admission right. Before
the first tick, no lease. Extra bodies do not advance the ID; real Withheld ticks
do. Backward/wrapped IDs, clock reversal, shutdown, scene/observer/manager or
generation/epoch replacement, death, GUI/focus/pause and foreign override end it.
Expiry takes effect at real owner/resolve/ticket access; no mutation without a
callback is promised. Expiry may preserve numeric wire history but no release
source right. A pure observed-generation/suspension revision read may support
revocation without a new Scene sample in every getter; no eligibility semantics,
SceneContext generation-on-suspend contract or player-body hooks may change.

Before production, real walking tests must fail on the old hard-release behavior
for bounded release frames and monotone expiry. The prior hard-release assertions
remain valid for immediate action/source denial and invalid contexts. No old test
may be weakened merely to accept pending source intent.

## Acceptance and review ownership

| Gate | Required evidence | Review |
|---|---|---|
| H2-01/02 | Real Ordinary Opportunity/C0A/C02, fresh local stages; Extra/NoStep admission consumes no scheduler/action | CMB + QA |
| H2-03/04 | Target/owner/scene loss after C0A and two C03 holds; C0A can remain, C02 fresh guard denies, barrier provenance stays original | CMB/NET + QA |
| H2-05/06/07 | UNKNOWN origin outside Aura, manual unchanged, lifecycle/reentry/exception/OFF/overflow evidence | Combat + QA |
| RELEASE-01..05 | Fresh source after real C03, three real tick advances/200ms, Extra/Withheld/ID0/history, no synthetic callbacks | Lifecycle + CMB + QA |
| RELEASE-06..11 | Aura/AimAssist override, re-enable generations, cancel/delay/rewrite/POST invalidation, GUI/scene/observer/epoch/ABA/shutdown/wrap, immediate action denial | CMB/NET + Lifecycle + QA |
| Java8 | Focus, full suite with native opt-ins, fresh XML count, bytecode52, diff check | Lead only builds |
| Runtime | Attempt existing IntelliJ configuration; distinguish actual game results from synthetic tests; configuration rollback and no existing world overwrite | Lead |
| Delivery | New result report, separate pushed branch, own stacked Draft PR if required gates remain open; no force/main push or speculative merge | Lead |

Expected shared production scope: RotationManager, Scaffold, RuntimeObservation
(read/revocation evidence only), Aura, PacketPipeline, CombatDiagnostics and small
scalar trace support. Add EntityPlayerSP only if a reproduced reentry contract
requires a caller boundary. No CPS/GCD/sprint formulas, PacketDelays, manual
controller admission, general HUD/visuals, Bezier or full Aura rewrite.
