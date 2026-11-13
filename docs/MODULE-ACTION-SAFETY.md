# Operation interruption guards for timing modules

Backtrack, LagRange and Tickbase append two independent, default-OFF controls:

- **Abort on Damage** ends the current operation when local damage has actually been
  observed: hurt time, death/non-positive health, or a decrease from the current
  scene's sampled health. The module remains enabled; it releases its owned work
  through the module-specific abort path, without calling `onDisable` or changing
  the user's activation state. The historical `stop_on_damage` ID and both old
  and new display-name lookups remain supported.
- **Exclusive Timing** prevents a later-enabled timing module from proceeding
  alongside an older enabled one when either has this option enabled. The newer
  module aborts its current operation and waits at preflight. It keeps its original
  activation order and can resume after the conflict clears; it does not change
  the older module.

These controls stop current work. They do not predict incoming hits, change the
timing algorithms or execute replacement actions. A later operation requires the
consumer's normal current-context, target and frame checks. Manually re-enabling
a module gives it a new activation order. With both controls OFF,
the guard does not require a health observation or impose this optional mutual
exclusion. Existing consumer eligibility checks still apply.

The shared guard's default policy still disables its owner. **FakeLag**, added
after the original three consumers, keeps that policy and its **Stop on Damage**
label. Only Backtrack, LagRange and Tickbase opt into operation interruption.

Backtrack's **Abort on Knockback** retains the `release_on_velocity` ID and its
previous name aliases. Applied local knockback, corrections and world transitions
continue to use the existing cleanup conditions. No new packet selection or
delay policy is introduced by the interruption change.

The damage baseline belongs to the current local player, world, connection and
connection context version. A scene change establishes a new baseline instead
of comparing a new scene's health with the old one. Enabling Stop on Damage in an
already running module must prime that baseline immediately, just as module
activation does. This matters for an initial health update that decreases health
without creating hurt time.

Event observers run on the client thread at highest priority, and consumers also
check the guard immediately before their work. A packet is observation evidence
only after completed inbound application on the current connection; PRE,
outbound and foreign-connection events do not trigger that observation. An abort
keeps preflights closed throughout synchronous queue cleanup and the current
tick. Repeated damage or nested cleanup events do not repeat the same abort or
admit new work. Ordinary disable still detaches the guard before cleanup.

World unload clears the guard's scene references and prevents rebinding the old
Minecraft pointers while the unload event is still running. Load establishes a
new boundary; the next ordinary tick and the consumer's usual acquisition checks
are required before work resumes. Tickbase also clears the budget's player,
world and connection references. Backtrack discards its acquisition and receipt
state. LagRange discards its active window and budget; for same-world interruption
it retains only the existing unpublished-look barrier until actual movement is
published, and a world reset clears that barrier with the rest of the scene.

## Regression coverage

`ModuleActionSafetyTest` contains isolated cases for defaults/append-only
registration, hurt-time stops, actual initial S06 health application, OFF-to-ON
priming, healing and subsequent decreases, world/context changes, packet-phase
and connection filtering, both mutual-exclusion activation orders, reactivation,
client/thread boundaries, nested cleanup, real consumer preflights, and partial
configuration reset.

`OperationAbortLifecycleTest` covers the enabled-state contract, legacy setting
aliases, unchanged FakeLag presentation, repeated damage, nested cleanup,
activation-order conflicts, Backtrack queue drain and fresh acquisition, Tickbase
credit/window discard and a required fresh frame, LagRange recovery, and unload
while the old scene pointers remain visible. The ordinary shutdown assertions
for generic guard consumers remain in `ModuleActionSafetyTest`.

Health-dependent tests initialize DataWatcher entry 6 locally. Only the two
native health-packet cases additionally initialize max-health and food state.
Shared bare-player fixtures are unchanged. Each outer case uses
`Wave2IsolatedCase` so Java 8 JIT folding of synthetic static Minecraft references
cannot make one fixture observe another fixture's client.
