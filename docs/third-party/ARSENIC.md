# Arsenic reference and attribution

The combat recode uses Arsenic Client as a behavioral reference, pinned to
`ee93d48a4b78b6383601c63b7269eb4c0e397e85` (reviewed 2026-11-02).

Sources: [BackTrack](https://github.com/ArsenicClient/Arsenic/blob/ee93d48a4b78b6383601c63b7269eb4c0e397e85/src/main/java/arsenic/module/impl/ghost/BackTrack.java),
[LagRange](https://github.com/ArsenicClient/Arsenic/blob/ee93d48a4b78b6383601c63b7269eb4c0e397e85/src/main/java/arsenic/module/impl/ghost/LagRange.java),
[Velocity](https://github.com/ArsenicClient/Arsenic/blob/ee93d48a4b78b6383601c63b7269eb4c0e397e85/src/main/java/arsenic/module/impl/ghost/Velocity.java),
[JumpReset](https://github.com/ArsenicClient/Arsenic/blob/ee93d48a4b78b6383601c63b7269eb4c0e397e85/src/main/java/arsenic/module/impl/ghost/JumpReset.java).
Supporting code reviewed: LagManager/PacketDelayChannel, TimerBalance, SyntheticKeys,
BadPacketsManager and the run-tick/input hooks.

The rotation/scaffold integration also ports the pure `AimCore`, `ScaffoldCore`,
`ScaffoldWorld`, `LaneNudge`, `SneakPresses`, `Box` and `Dir` classes from the same
revision into `me.youngjibbit.core.arsenic`. The source paths are respectively
`utils/aimcore`, `utils/scaffoldcore` and `utils/botcore` below
`src/main/java/arsenic`. Reviewed adapters: `utils/rotations/AimController`,
`SilentRotationManager`, `RotationUtils`, `utils/minecraft/ScaffoldUtil`,
`McScaffoldWorld`, `module/impl/blatant/KillAura`, `ghost/AimAssist` and
`world/Scaffold`.

The cores retain upstream algorithms. Namespace changes, a separate confirmed
movement yaw for Scaffold prediction and fresh-input preview methods are Neutron
adaptations. The surrounding adapters add scene/ownership resets, per-motion
sampling, bounded prediction, accepted-ray placement and existing input priority.
See [integration details](../ARSENIC-ROTATIONS-SCAFFOLD.md). This license also ships
in the client artifact as `META-INF/licenses/arsenic.txt`.

Neutron retains its connection-scoped FIFO pipeline, final-packet receipt tracking,
accepted-motion authority and controller attack gates. Arsenic's event bus, mixins
and packet replay shortcuts are not installed. The adaptations are covered by the
following upstream license:

```text
MIT License

Copyright (c) 2022 Arsenic Client Development

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
