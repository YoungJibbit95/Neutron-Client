# W08-B — Intave Momentum V2

Main baseline: `c6612bf43ec2ce45bf686714374516f80788057e`.
Stacked on Adaptive Bridge A3, head `98b8119088fb1192608b3aa9d77f8b28882f86d7`.

## Optional mode and existing behavior

Speed appends **Intave Momentum V2** at index 7. The previous seven labels, indices
and default Intave at index 1 remain. Existing Intave Momentum listeners and Intave
Boat behavior remain in place. The legacy Air Speed value is hidden/ignored by V2,
but its saved value remains available to older modes.

V2 uses the existing `Speed.applyBoatAcceleration` final hook, after all movement
listeners and accepted MoveFix correction. Its only gameplay write is the final
event's friction. Vanilla still adds acceleration, resolves wall collisions, jumps,
lands and applies drag. V2 does not request a jump or write velocity, position,
physical keys, timer speed, air movement factors or packets. Entering V2 from a
legacy mode that owns movement overrides performs that old mode's existing cleanup.

## Numeric profile

`IntaveMomentumProfile` owns only numbers. Inputs and results are immutable. Its
world-direction comparison uses the final corrected axes and yaw, so two MoveFix
representations of the same course keep the same ramp.

| State | Additional event friction |
|---|---|
| Coherent ground movement | Rise at most 0.004 per ordinary step; limit 0.012 and 12% of supplied friction |
| Ground wall contact | Limit 0.006 and 6% of supplied friction; Vanilla resolves blocked axes |
| Air after a valid ground run | At most 0.002, 0.0015, 0.001, 0.0005 over four real air steps, then zero |
| Air without a ground run | Zero |
| Landing | Restart with at most one ground increment |
| Sharp course change | Zero on the changing step, then a new ramp |
| Empty/invalid input or denied eligibility | Zero; clear numeric motion history |

Air also respects 10% of the supplied base friction. A 45-degree diagonal change
stays coherent; 90/180-degree changes restart. The profile rejects its additional
proposal if the resulting modeled horizontal magnitude exceeds 0.4. The host checks
that bound again with Vanilla's float sine table and normalization. It never clamps
existing velocity or replaces a rejected event's base friction.

## Runtime ownership

The host requires the active V2 module, owning thread, matching player/world and an
actual ordinary `RuntimeObservation` body in the active outer tick. Weak scene and
observer identities prevent retaining an old world. A consumed outer-tick tombstone
survives settings resets and disable/re-enable; a new observer gets a new profile.
Extra bodies, withheld updates, duplicate calls and skipped ticks earn no extra
acceleration or air duration. Context callbacks are followed by generation/scene
validation before a proposal can reach the final event.
The V2 path reads the current canonical Minecraft client and binds its identity as
well as the canonical observer after callbacks. It does not rely on the inherited
static-final legacy client reference for the new lifecycle gates.

Sprint and current usable input are required. Sneak, item use, flight permission,
liquids, ladders, webs, damage, GUI/focus loss or invalid motion remove the extra.
Manual jump and Scaffold's optional jump remain their original input decisions.
Scaffold's final edge-guard sneak is read by the V2 consumer and therefore blocks
its acceleration without changing residual velocity.

The actual applied `LocalImpulseEvent` clears the ramp. The existing pipeline's
`isRecoveringLocalImpulse` blocks V2 for its real time and grounded recovery contract;
a cancelled velocity packet does not become a fictional applied impulse. An accepted
inbound S08 correction resets the ramp and pauses it for 500 ms of monotonic time.
World/connection/owner changes and clock rollback start cold. These are private
numeric gates, not a new timer or packet pipeline.

## Evidence and limits

Isolated Temurin 8u482/JUnit: **20 tests, 0 failures, 0 errors, 0 skips**. Tests cover
generation and consumed-tick protection, pause, missing/extra steps, direction and
MoveFix equivalence, finite values, ground/wall/air/landing, relative limits and the
0.4 proposal bound.

A deterministic 20-step free-space recurrence with final forward 0.98, base ground
friction 0.13 and ground drag 0.54600006 measured baseline distance 5.274855391 and
profile distance 5.735861958. The 0.25 displacement threshold was reached on step 4
versus step 3. These are explicitly model values with prescribed ground/air state;
they do not execute a native jump, wall collision, full old Momentum run or server.

Initial B code gate, Temurin 8u482 / Maven 3.9.15 full-source local validation: **230 tests / 0
failures / 0 errors / 0 skips**, plus **15 unchanged legacy checks / 0 failures /
0 errors / 0 skips** in their required fresh JVMs. Aggregate: **245 / 0 / 0 / 0**.
The Maven selection is prior 199, Pure 20, Config 3 and productive Flow 8. Legacy
checks are SpeedMovementBasis 5, IntaveBoat 9 and the original Intave wall case 1.
The portable invocation uses the existing Wave2IsolatedCase.main entry point;
Windows wrappers, source assertions, POM and workflows remain unchanged.

The actual headless moveFlying comparison uses grounded sprinting, no wall, yaw 0,
strafe 0, forward 0.98 and zero incoming horizontal velocity:

| Base friction | Vanilla / old Momentum delta Z | Mature V2 delta Z | Difference |
|---|---:|---:|---:|
| 0.1 | 0.098000005 | 0.109760001 | 0.011759996 |
| 0.13 | 0.127399996 | 0.139159992 | 0.011759996 |

These values are real Java-hook measurements, separate from the recurrence above.
They do not include a complete legacy autojump cycle, collision trajectory or server.
Flow tests also cover final MoveFix/listener input, actual ordinary/extra/withheld
steps, same-tick resets, fresh manual/Scaffold input, ground/air/wall/landing/turns,
applied versus cancelled S12, five actual S08 handler/pause variants, lifecycle,
foreign override preservation and callback-driven observer replacement.

The first 245-test attempt exposed two legacy cases run without their required JVM
isolation and five new V2 current-client gate failures. The latter were corrected by
the canonical client binding, with assertions unchanged. The next 230-test run had
one remaining S08 fixture failure: its actual packet preprocessor required the
existing scoped Config.minecraft/RenderGlobal setup before applying player position.
The new fixture now supplies that existing headless setup and restores it afterward;
all five S08 variants, positions, real C06 and pause deadlines remain asserted.
That successful local run uses normal Java8 JIT, with no interpreter-only acceptance.

The first full Windows Java CI on synthetic merge
`99510931d16ae169c02a9f6dadb550e4042484ca` reported **1572 / 0 failures /
1 error / 3 skips**. Its only error was the new lifecycle test switching back to
the unchanged legacy Momentum listener: `Speed.momentumReady:175` through
`wallAcceleration`, before the V2 hook. The Release job on the same exact merge
passed **1572 / 0 / 0 / 3**, Golden 2 and the separate packaged-runtime check 6.
The headless fixture rebinds inherited static-final client references, so those
legacy paths require the existing fresh-JVM isolation contract. The new eight
Flow methods now use `Wave2IsolatedCase`, matching IntaveBoatSpeedTest. Their
method bodies and assertions are unchanged. Normal Java-8 JIT remains enabled;
production code, old tests, the shared helper, POM and workflows are unchanged.
Follow-up compilation passed and all eight original methods passed in eight fresh
normal Java-8 JVMs: **8 / 0 failures / 0 errors / 0 skips**. The complete original
fixture/test body is byte-identical inside Checks; Speed source and compiled class
hashes are unchanged. The prior 245-case code gate is retained as base evidence,
not reported as a repeated follow-up run. The initial failing CI remains recorded;
fresh full CI on the follow-up is required.

Exact-merge full Windows/Golden/Security/CodeQL/release/packaged-runtime results are
recorded on the PR after execution. Native Minecraft gameplay, server acknowledgement,
anti-cheat compatibility, actual FPS/TPS/latency and overall gameplay speed gains
remain **NOT_RUN**. No bypass or undetectability claim is made.
