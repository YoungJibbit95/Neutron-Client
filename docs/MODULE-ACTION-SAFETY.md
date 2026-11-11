# Optional stop guards for timing modules

Backtrack, LagRange and Tickbase append two independent, default-OFF controls:

- **Stop on Damage** turns the module off when local damage has actually been
  observed: hurt time, death/non-positive health, or a decrease from the current
  scene's sampled health. It uses normal module shutdown to release owned state.
- **Exclusive Timing** prevents a later-enabled timing module from proceeding
  alongside an older enabled one when either has this option enabled. The newer
  module disables itself at its preflight; it does not change the older module.

These controls stop work. They do not predict incoming hits, reschedule packets,
change delays, execute replacement actions, or automatically turn modules back
on. Re-enabling a module gives it a new activation order. With both controls OFF,
the guard does not require a health observation or impose this optional mutual
exclusion. Existing consumer eligibility checks still apply.

The damage baseline belongs to the current local player, world, connection and
connection context version. A scene change establishes a new baseline instead
of comparing a new scene's health with the old one. Enabling Stop on Damage in an
already running module must prime that baseline immediately, just as module
activation does. This matters for an initial health update that decreases health
without creating hurt time.

Event observers run on the client thread at highest priority, and consumers also
check the guard immediately before their work. A packet is observation evidence
only after completed inbound application on the current connection; PRE,
outbound and foreign-connection events do not trigger that observation. Shutdown
detaches the guard before normal cleanup, so nested cleanup events cannot repeat
the stop or admit another action from the stopped instance.

## Regression coverage

`ModuleActionSafetyTest` contains isolated cases for defaults/append-only
registration, hurt-time stops, actual initial S06 health application, OFF-to-ON
priming, healing and subsequent decreases, world/context changes, packet-phase
and connection filtering, both mutual-exclusion activation orders, reactivation,
client/thread boundaries, nested cleanup, real consumer preflights, and partial
configuration reset.

Health-dependent tests initialize DataWatcher entry 6 locally. Only the two
native health-packet cases additionally initialize max-health and food state.
Shared bare-player fixtures are unchanged. Each outer case uses
`Wave2IsolatedCase` so Java 8 JIT folding of synthetic static Minecraft references
cannot make one fixture observe another fixture's client.
