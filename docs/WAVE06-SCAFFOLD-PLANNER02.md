# WAVE06 — SCAFFOLD-PLANNER02 (C1: pure Java-8 support/face candidate)

**Status: independent PRE-only geometry planner. No live Scaffold consumer, no placement.**
This Draft is stacked on the still-open [Scaffold research PR #105](https://github.com/YoungJibbit95/Neutron-Client/pull/105), head at C1 branch creation 3d88c95090375e1124dcc9bdba72de283c7ebb20. Main at that moment: 9f40ae0366463a3c93a59f4749bd723607b6bf99. The pure AI Learned CORE02 PR #106 passed its separate Java-8 CI, but C1 does **not** depend on its source or import any AI-Aura files.

## Provenance and strict separation

Research and exact upstream blob SHAs are in docs/WAVE06-SCAFFOLD-RESEARCH01.md. Neutron currently has **Scaffold Approach = Neutron / Arsenic**, default Neutron, with its own PlacementTarget, ScaffoldPlacementService, RotationManager, MoveFix, PacketPipeline and TowerController. This PR preserves all of them unchanged. Vibe Scaffold at pinned source 584729c09ceafa8f1541260abc51bed3f97e91f4 has mixed private, decompiled Gothaj provenance with no documented upstream license. Its implementation, helpers, fonts, shaders and network hooks are **BLOCKED_FOR_COPY**. FDPClient Scaffold/Tower Kotlin at 07d03078b3fe12ac7278c678f56d0e7f607fbec2 is used as **RESEARCH_ONLY**, no copied code or assets.

The only proposed future name is **Adaptive Bridge**. This PR adds **no** Scaffold mode, config option, slot handler, keybinding, sprint/tower logic, packet path, native renderer or new placement service.

## C1 pure-code contract

New me.youngjibbit.core.world.AdaptiveBridgePlanner has **no Minecraft dependencies** and cannot cause a block placement. A future host adapter may supply at most **24** already checked support/face candidates from the existing ScaffoldPlacementService, plus a current motion PRE-only frame snapshot. The planner returns one immutable candidate Plan or HOLD. There is intentionally **no POST selection method**.

Frame inputs:
- Opaque owner/world/player/connection identities and monotonic connection epoch + acquisition generation. The wrong owner, old generation, different world/connection, rejected or disabled frame must HOLD.
- Player feet/eyes, yaw and bounded horizontal input/velocity, intended destination block coordinates, reach <=6, and a valid hotbar slot (0–8) plus **identity** of an already approved stack. No inventory search is done.
- The support option includes immutable support coordinates, face, hit point, slot, stack, world/player identities, support-state identity and host-supplied evidence flags for loaded chunk, collision, and validated placeable face. Missing/foreign/invalid candidate flags fail closed; a boolean flag here is **not proof** of real placement permission.

Pure geometry checks:
- Original destination must be exactly the selected support plus one of the six face offsets. The hit must lie on that exact unit block-face plane, within the block bounds (1e-6 tolerance). No new POST face or hit outside the support.
- Hit point must be reachable **both from current actual eyes and predicted eyes**, so prediction never grants additional range. Forward/back/side/diagonal movement uses camera yaw and clamped input, blended with bounded local velocity. Prediction is capped at **0.6 block horizontal** and teleport-like velocity jumps (>2 blocks/tick), invalid world coordinates, nonfinite rotations, invalid eye height or reach are refused.
- Candidate scoring uses predicted foot destination proximity, finite yaw turn relative to the current pose and actual eye distance; fixed stable input order breaks ties. Max 24 candidates, constant finite work, no scan proportional to the world or inventory.
- Plan may expose only a **sneak suggestion** when the *future host* has already identified a physical edge risk AND Eagle is explicitly enabled. No key write, movement-slowdown, timer or input event is produced here.
- The score is evaluated once per fresh motion sequence; repeated PRE/render observations with identical destination/stack return the same cached Plan. A changed target/stack **within the same sequence invalidates the acquisition**, never performs another selection. Stale callbacks cannot invalidate a newer generation. Old generation tombstones survive reset.

## C2 live contract (not implemented)

In a distinct reviewed Draft PR, append Adaptive Bridge as index 2 while preserving old Neutron/Arsenic option indices and default. Build the allowed SupportFace list only from the actual existing ScaffoldPlacementService and its checked blockslot/world/collision/reach/raycast sources. Revalidate scene/packet epoch/owner after callbacks. In PRE freeze a genuine PlacementTarget (support/face/hit/slot/stack/state/world/player); request source-owned rotation through RotationManager at SCAFFOLD_PRIORITY. Only locally **accepted** C03 can authorize POST. POST cannot search for another support/face/stack or consume a second budget: it may refine only the original PRE hit via alignAcceptedHit and must recheck all original inventory and collision facts using the existing placement service. No new C08, shadow packet pipeline or MoveFix engine. Existing return/Eagle/physical sneak/tower behavior remains under current owners.

## Tests / unverified gates

New JUnit 4 class AdaptiveBridgePlannerTest (13 pure tests): valid PRE intent; 0/90-degree forward, backward, side and diagonal prediction; negative coordinates/face plane; invalid support, collision, slot, stack, unloaded chunk, state or world; actual-vs-predicted reach; 24-option cap and deterministic tie; same-sequence caching/conflicting target; disable/rejection/stale generation; opt-in Eagle suggestion; foreign-thread no-op; NaN/Infinity, invalid velocity/eye and world height.

**CI pending at PR creation:** report exact synthetic PR merge SHA, Temurin Java 8u482 Maven JUnit totals, zero failures/errors, three existing native GL opt-in skips, legacy scaffold/rotation golden, security/CodeQL and bundle build. No native Minecraft/GL, real bridging, physical diagonal keys, placement C03/C08 receipt, inventory races, multiplayer server, latency or anticheat tests are run in this pure-code PR. No performance/bypass guarantees.

The C2 live consumer, optional tower/movement modes, privacy work, Backtrack/LagRange/Tickbase and Velocity/Speed remain separate future PRs.

**Rollback:** close the C1 Draft. No existing source, live user config or local checkout has been changed. No direct main merge, force push or release.
