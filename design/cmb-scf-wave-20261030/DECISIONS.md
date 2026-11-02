# Gate A decisions

Recorded by the integration lead on 30 October 2026, after baseline and behavioral
REDs and before the first production edit. Scope: existing CMB/SCF integration.

## D1: SCF-1b/H1-06 remains OPEN

Keep synchronous hard release. The suggested 3 real outer-tick advances or
200 ms monotone deadline is a new proposal, not a historical approved default.
The disabled Module unsubscribes Motion/Tick/World listeners. RotationCorrections
also requires toggled Scaffold. Current pure source predicates and frame-bound
C03 tickets provide no independent disabled lease expiry/override contract.
Extending ordinary disabled listeners would risk reopening automatic actions.

A safe future lease needs a separately reviewed CMB lifecycle/authority API for
expiry at actual query/resolve points, scene/observer/manager and enable-generation
binding, foreign-owner relinquishment and callback exceptions. No small
Scaffold-only lease meets that contract today. There is no timer thread, synthetic
tick, disabled listener or borrowed source right in this wave.

Future timing requirements: ID 0 valid; active -1 outside lifetime grants no tick;
Extra with the same outer ID counts once; real Withheld ticks count without a body;
pause permits no synthetic callback, with monotone expiry enforced on next real
access; wrap/backward tick invalidates conservatively. No exact mutation deadline
without a callback is promised. World/player/handler/pipeline/epoch/observer,
death/GUI/focus/shutdown/foreign owner and re-enable must terminate old leases.
These lease-specific cases are deferred, not reported as passed tests.

Immediate hard release remains the action rule. The proven AutoJump reentrancy
bug will be fixed by a local enable/disable generation token and validation after
the callback, so an old PRE cannot resume even after disable then re-enable in
the same motion sequence. No angle smoothing or timer policy changes are needed.

## D2: packet contracts

| Packet | Authority and final guard | Local result / replay boundary |
|---|---|---|
| C03/C04/C05/C06 | Existing immutable resolved Scaffold ticket at Apply; revalidate lifetime after Packet POST before confirming source | Actual transport remains true for reporting/diagnostics even when callbacks invalidate action authority. No extra C03 or replay renewal |
| C09 select | Same plan/scene/sequence/source and current selected slot; selected packet must still represent captured slot, checked after PRE and movement barrier | Cache changes only on actual transport; denied automatic selection consumes no placement attempt. Manual noarg path preserved |
| C08 | Same guarded PRE plan + controller gate + immutable packet position/face/stack/hit snapshot | In-place mutation cannot borrow identity. Local controller result is not server placement ACK |
| C09 cleanup | Same scene/player/world and captured cleanup selection at Apply | May release the action's temporary slot; cannot overwrite a newer manual selection or restore into replacement scene. Not a new placement right |

C08/C09 are real non-movement action barriers, not delay candidates. Existing
delayed C03 flushing is honored before final action guards. No new plan packet,
packet queue, global manual gate or global replay interceptor is introduced.
Rotation OFF retains accepted camera authority; numeric equality grants no source.

## D3: C03 transport versus authority

Add a read-only ticket-lifetime query using the existing `current()` validation.
The real walking caller samples it after transport, inside the same exception
reject/propagate boundary, then rechecks sequence/manager after sampling for
reentrancy. `finishMotion` receives false if that lifetime ended. The generic
numeric/owner `finishMotion` API remains unchanged. EventMotion.accepted and
reported wire position/yaw continue to describe transport that actually occurred.
This avoids false diagnostic rejection records for already sent packets.

## D4: gates/publication

Behavioral evidence only counts executable assertions; fixture compile errors and
masked default-priority probes are recorded separately. Existing assertions are
not weakened. Gate B needs unchanged REDs green; Gate C needs focus + fresh full
Java8 suite including native opt-ins, bytecode 52 and diff review. Gate D requires
a real IntelliJ launch attempt and honest gameplay limits. No server pass is
inferred. H1-06 is not implemented/published as complete and cannot be merged as
a release lease. Proven baseline corrections may be published after explicit
Gate-C review and Gate-D attempt. Project workflow uses the single main checkout
and `codex/test-integration` PR, never a direct origin/main push or force push.
