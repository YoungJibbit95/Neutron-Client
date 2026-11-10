# Demise timing modes

## Source and adaptation

Adapted with the repository owner's explicit permission from
`YoungJibbit95/DemisePrivate` at commit
`e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`:

- `src/main/java/wtf/demise/features/modules/impl/combat/TickBase.java`
- `src/main/java/wtf/demise/features/modules/impl/combat/BackTrack.java`

The source's Legit pulse and Blatant distance-window concepts become
**Demise Pulse** and **Demise Approach** in Tickbase. The existing **Player Steps**
mode remains the default, and previous mode indices are retained in Backtrack.

## Tickbase

Demise Pulse opens a short outbound movement window while a valid target is in
range. Demise Approach opens the window between Release Range and Charge Range.
Packet Hold MS and Pulse Gap MS control duration and recovery. Pulse is capped at
120 ms, Approach at 250 ms, with a maximum of twelve held packets. Refreshing a
window never extends its original deadline. An actual attack, target loss, range
exit, correction, disable, or context change ends the relevant owner window.

Only outbound C03 movement packets use the existing PacketPipeline owner.
Player simulation continues normally, CombatTimingCoordinator preserves its
single-owner contract, and existing action/lifecycle barriers remain in force.
The reference's global Blink and repeated synthetic attack loop are not imported.

## Backtrack

The appended **Demise** mode compares the received target position with the
currently visible position. Optional **Only When Needed**, **Demise Moving Away**,
**Demise Attack Range**, and **Demise Only Double** controls expose the source's
distance and recent out-of-range gates. Distances use the local eye to the target
bounding box at the respective positions.

The existing target-specific inbound movement queue remains authoritative.
Each selected delay is capped at 250 ms and the queue at twelve packets; this is
a rolling per-packet delay, not a 250 ms total pulse. Teleports, corrections,
impulses, world/connection changes, disable, and action barriers continue through
the existing lifecycle implementation.

## Evidence and acceptance

Activity widgets report actual owner occupancy and packet age. A local correction
after recent activity is an observation, not proof that the module caused a
server flag. Incident text keeps an unconfirmed cause explicit.

`DemiseTimingWindowTest` covers fixed deadlines, range gates, cooldowns, monotonic
clock edge cases, and the received/visible Backtrack policy.
`DemiseTimingFlowTest` exercises actual movement queues, continuing player steps,
exclusive ownership, activity age, disable flush, and inbound target movement.

The Alpha still needs in-game acceptance for straight/diagonal bridging, target
switches, packet timing under latency, and the native OpenGL Island transitions.
Automated build/test results are recorded in the PR and release workflow.
