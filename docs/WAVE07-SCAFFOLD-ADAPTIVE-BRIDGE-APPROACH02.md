# WAVE07 SCAFFOLD-APPROACH02 — live Adaptive Bridge (Draft)

Starting main: 1338d0a692d00ee65cba017744558aab37bedaba. Scaffold research #105 and pure AdaptiveBridgePlanner #108 were merged and are reused. AI Learned PR #114 is completely separate.

## Opt-in mode and original guarantees
Append Scaffold Approach Adaptive Bridge at index 2; old Neutron index 0 DEFAULT and Arsenic index 1 unchanged. Config name scaffold_approach unchanged. Old mode implementations and defaults retain their semantics.
Only the existing ScaffoldPlacementService can validate the destination/face/actual stack/slot/world/player/loaded block/raycast. The new package-local AdaptiveBridgeScaffoldAdapter builds up to six host-validated candidate faces for that SAME destination and actual inventory stack; these are passed to the already merged pure AdaptiveBridgePlanner once per physical PRE motion, maximum six versus its hard 24-candidate bound. This produces a pure yaw/pitch proposal, never a C03 acceptance or C08 authorization.

## PRE → C03 → POST
Scaffold PRE records exactly one PlacementTarget/sequence, active scene, context/epoch and lifecycle generation. RotationManager's existing calculatePlanRotations and scaffold priority own the angular candidate, mouse GCD, MoveFix and competition. No placement occurs in PRE in this approach regardless of saved historical Pre setting.
Scaffold POST does not rescan for a new face/slot/stack. The existing service's alignAcceptedHit requires the exact original PRE support/face and an accepted source-owned C03. Only original tryPlace(false) calls placeWithAuthority; unchanged inventory/reach/loaded-world/action-budget/collision/raycast and C08 controller guards apply, including callback revalidation.
No new PacketPipeline, controller, spoof, click, timer or tower architecture. Fresh-input Eagle suggestions are deliberately inert in this minimal bundle; future movement/sneak submodes require separate review. Original sprint and lifecycle checks are reused. Lost focus, disable, world/connection change, repeated PRE or canceled/replaced/foreign C03 cannot publish an unauthorized placement.

## Source provenance and gates
No Vibe/Gothaj private decompiled Scaffold source, helpers, assets, fonts or shaders copied. FDPClient Kotlin is research only. See pinned source and rights matrix in merged docs/WAVE06-SCAFFOLD-RESEARCH01.md.
Five paths total: modified Scaffold.java; new core/world/AdaptiveBridgeScaffoldAdapter.java; new AdaptiveBridgeConfigTest.java (4 tests); new AdaptiveBridgeLiveFlowTest.java (6 tests); this gate document. Existing pure AdaptiveBridgePlannerTest (13) and legacy Neutron/Arsenic/packet/golden suites must pass unchanged.
Full Temurin Java8u482 Maven3.9.15 CI, CodeQL, Repository Security, and bundle compile PENDING until actual PR merge SHA checked. Native GL/Minecraft launch, real multiplayer/server/inventory race, performance/FPS and anticheat compatibility NOT_RUN. No bypass or undetectability claims.
Rollback: close the Draft. No main push/merge, release, force update or local Windows checkout changes.