# W08-A2 — Adaptive / Godbridge rotation profiles

Stacked on A1 `b14e0aa09b27f023a3b6d145edc3608d490454d8`;
main baseline `c6612bf43ec2ce45bf686714374516f80788057e`.

| Adaptive Bridge setting | Choices / default |
|---|---|
| Bridge Rotation Profile | Adaptive / Godbridge; default Adaptive |
| Bridge Rotation Speed | Calm / Balanced / Fast; default Balanced |
| Bridge Rotation Variation | 0–1; default 0.25 |
| Yaw Speed / Pitch Speed | Existing bounds/defaults; also visible for Adaptive |

PRE keeps one valid face and immutable anchor. Godbridge acquires checked
45-degree yaw / 83.52-degree pitch geometry once, falling back to the valid
original point. Keeping retains actual/predicted reach and all service validation.
Small face proposals preserve support, destination, face, stack and identity.
Their actual angles enter the new manager path during collection, then existing
sensitivity quantization and arbitration. Original A1 POST authority remains.

The unchanged AdaptiveMotionController supplies continuous dynamics; a separate
FastNoiseLite face correction has a tangential L2 cap of 0.08 blocks (Adaptive)
or 0.035 (Godbridge), multiplied by variation/fade. Render duplicates cannot
advance it. Residual clamps, settling and final quantization can change emitted
finite-difference acceleration/jerk; internal caps are not universal packet bounds.

Exact host scope, live scene, owner and thread checks guard the new APIs.
Weak tombstones and private monotone ordinals support lower-epoch reconnects;
scoped rejection cannot clear replacements. Old Neutron/Arsenic defaults and
engines remain intact. Saved hidden Rotations=false does not disable Bridge.
Existing rotation-return leases are the only return mechanism.

Pure Temurin 8u482 gate: 14 tests / 0 failures / 0 errors / 0 skips, including
20 Hz opportunities with 20/60/144/240 FPS query patterns. The final local Maven
consumer/config gate passed 170 tests / 0 failures / 0 errors / 0 skips.
Exact-merge CI evidence is recorded on the PR. Native Minecraft, multiplayer,
server acknowledgement and real FPS/TPS/latency performance remain **NOT_RUN**.
