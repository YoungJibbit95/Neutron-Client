# Arsenic reference and attribution

The combat recode uses Arsenic Client as a behavioral reference, pinned to
`ee93d48a4b78b6383601c63b7269eb4c0e397e85` (reviewed 2026-11-02).

Sources: [BackTrack](https://github.com/ArsenicClient/Arsenic/blob/ee93d48a4b78b6383601c63b7269eb4c0e397e85/src/main/java/arsenic/module/impl/ghost/BackTrack.java),
[LagRange](https://github.com/ArsenicClient/Arsenic/blob/ee93d48a4b78b6383601c63b7269eb4c0e397e85/src/main/java/arsenic/module/impl/ghost/LagRange.java),
[Velocity](https://github.com/ArsenicClient/Arsenic/blob/ee93d48a4b78b6383601c63b7269eb4c0e397e85/src/main/java/arsenic/module/impl/ghost/Velocity.java),
[JumpReset](https://github.com/ArsenicClient/Arsenic/blob/ee93d48a4b78b6383601c63b7269eb4c0e397e85/src/main/java/arsenic/module/impl/ghost/JumpReset.java).
Supporting code reviewed: LagManager/PacketDelayChannel, TimerBalance, SyntheticKeys,
BadPacketsManager and the run-tick/input hooks.

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
