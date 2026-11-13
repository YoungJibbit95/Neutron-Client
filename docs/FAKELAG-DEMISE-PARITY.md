# FakeLag: Demise source port

FakeLag imports the complete **23-setting interface**, Pulse and Spoof policies, smart/simple criteria, range control, first-hit behavior and three real-position displays from `YoungJibbit95/DemisePrivate` at **`e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb`**. Its packet storage belongs to one Neutron PLAY connection and retains the existing guarded application path.

The main reference is [the pinned FakeLag.java](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/combat/FakeLag.java). [The source manifest](sources/demise-fakelag-e219a97.json) records 21 exact paths, Git blob IDs, byte lengths and SHA-256 values. Every materialized file was checked against the complete, non-truncated pinned Git tree. The pinned README declares AGPL-3; that tree has no root LICENSE file. This source reuse was explicitly requested by the user; attribution is retained in the implementation and test resources.

## Settings

Names, order, defaults, choice lists, float storage, ranges and GUI increments follow the source. Hidden controls retain their values and remain serializable.

| # | Setting | Default | Values / range / increment | Visible when |
|---|---|---|---|---|
| 1 | Mode | Pulse | Pulse, Spoof | Always |
| 2 | Smart | true | Boolean | Always |
| 3 | HurtTime to register attack | 3 | 0–10, step 1 | Smart |
| 4 | Early release | false | Boolean | Smart |
| 5 | Attack range | 4 | 0–15, step 0.1 | Smart is off |
| 6 | Always spoof | false | Boolean | Always |
| 7 | Search range | 6 | 1–15, step 0.1 | Always spoof is off |
| 8 | Recoil time (ms) | 50 | 0–1000, step 1 | Always |
| 9 | Delay (min ms) | 100 | 0–1000, step 1 | Always |
| 10 | Delay (max ms) | 100 | 0–1000, step 1 | Always |
| 11 | Delay on opponent attack | 200 | 0–500, step 1 | Mode = Spoof |
| 12 | Keep range mode | None | None, WTap, Timer down | Smart |
| 13 | Timer | 0.75 | 0.01–1, step 0.01 | Keep range mode = Timer down |
| 14 | Timer ticks | 1 | 1–10, step 1 | Keep range mode = Timer down |
| 15 | Experimental dynamic hurt time | false | Boolean | Smart |
| 16 | HurtTime to stop (>) | 0 | 0–10, step 1 | Always |
| 17 | Pause on backtrack | false | Boolean | Always |
| 18 | Force first hit | false | Boolean | Always |
| 19 | Display real pos | true | Boolean | Always |
| 20 | Render mode | FakePlayer | Box, FakePlayer, Line | Display real pos |
| 21 | Only onGround | false | Boolean | Always |
| 22 | Only on killAura | false | Boolean | Always |
| 23 | Team check | false | Boolean | Always |

The Timer controls intentionally depend only on the selected Keep range mode, even while Smart is off. `DemiseSliderValue` preserves source float assignments and rounds only GUI edits to the configured increment. Delay sliders retain independently stored endpoints; runtime sampling orders crossed endpoints to avoid the original invalid random interval.

Neutron adds its existing **Stop on Damage** and **Exclusive Timing** controls, both **false**. These are two host controls, separate from the 23 Demise settings.

## Target selection and criteria

The selector iterates `world.playerEntities`, compares the source float distance minus `0.5657f`, and replaces the selection on `<=`: equal-distance ties choose the last entity in the original iteration order. The initial search limit is `Search range + 1`, or `Float.MAX_VALUE` for Always spoof. Team check uses Demise's formatted-name color-prefix rule. A live Aura player target takes precedence when Aura is enabled, corresponding to Demise KillAura's explicit override. The port also requires the current world/entity identity and a live target.

Smart mode preserves the following source conditions:

- The selected target is inside Search range, unless Always spoof removes that limit.
- The hurt timer has elapsed **strictly more than 100 ms** after local hurtTime exceeded HurtTime to stop.
- Current player-to-target entity distance is strictly smaller than distance from the stored server position.
- A recorded attack stops lagging when the target's hurtTime is at or below HurtTime to register attack. The normal Update consumes that attack marker.

Smart mode does not add a line-of-sight test. Simple mode requires visibility from the target to the player, the configured local hurtTime limit, and the inclusive interval between Attack range and Search range. It retains the source float cast of box distance and the source range helper's ordering of crossed interval endpoints.

The geometry preserves `PlayerUtils.getDistanceToEntityBox` and the less obvious historical-distance behavior: **the nearest hitbox point is selected from current eyes, then measured from the supplied historical eyes**. Collision-border expansion and float entity-distance arithmetic are retained. Dynamic hurtTime counts every evaluation at local hurtTime 9, including repeated evaluation in an early-release callback; the counter resets on target hurtTime 10 or box distance above 4.5. Above two counts, mouse-ray distance greater than 3 is the source's explicit early success path. The source comment identifies the repeated count; the port does not silently replace it with per-hit counting.

Sources: [PlayerUtils](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/PlayerUtils.java), [RotationUtils](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/player/rotation/RotationUtils.java), [Entity](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/net/minecraft/entity/Entity.java).

### External-module mapping

`Only on killAura` checks Neutron Aura's enabled state. `Pause on backtrack` checks Neutron Backtrack's current connection-owned activity snapshot, corresponding to Demise BackTrack's `shouldLag`. Neither option enables the other module.

As in Demise, Only onGround, Only on killAura and Pause on backtrack gate the buffering Update. They do not disable the separate first-hit or already-active Keep range timer paths. Release callbacks with a valid target still evaluate their criteria while an outer buffering condition has just become false.

**AntiBot is an external dependency difference.** Demise's generic target helper excludes entries in its separate AntiBot module's `bots` list when that module is enabled. Neutron's existing AntiBot class is an empty stub and exposes no classification/list. The FakeLag port cannot import a nonexistent host classification; it does not claim to port AntiBot or add an invented bot test. All FakeLag-specific settings and selection rules listed above remain present. The pinned [AntiBot source](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/features/modules/impl/combat/AntiBot.java) is included in the provenance manifest to make this boundary reviewable.

## Pulse and Spoof

| Behavior | Pulse | Spoof |
|---|---|---|
| Source utility | BlinkComponent | LagUtils |
| Outbound selection | All normal PLAY packets except C00 KeepAlive | Exact REGULAR, BLINK and MOVEMENT source classes |
| Inbound selection | None | S1C EntityMetadata only |
| Chat | Held | Immediate |
| KeepAlive / transaction reply | KeepAlive immediate; transaction held | Both held |
| Timing | Recoil starts a window; elapsed delay ends it and resets both timers | Each ordinary Update renews the 100 ms lease; release uses the current sampled delay |
| Initial activation | Immediate when the Pulse policy enters its window | A new request activates on the next ordinary Update |
| Position observation | After accepted transport | Before transport for a timed release, with guards checked again afterward |

Spoof's exact outbound list is C00, C0F, C02, C0D, C0E, C0C, C0B, C08, C07, C09, C13, C15, C16, C17, C18, C19, C03 and its C04/C05/C06 concrete variants, and C0A. Class matching is exact as in LagUtils. FakeLag does not enable its velocity, teleport or remote-player-movement packet groups. Pre-PLAY handshake/login/status packets never enter this connection-owned hook.

The source LagUtils Update runs before the module Update. A newly requested Spoof window therefore starts listening on the next ordinary Update. Normal Spoof dispatch (disable, criteria loss or expired lease) publishes without a `PacketReleaseEvent`; only timed Spoof release performs the pre-transport position/early-release callback. Pulse observes after actual acceptance.

The adapter explicitly separates `beginUpdate` from `finishUpdate`. In Pulse's criteria-loss branch, dispatch observes the prior blinking/picked flags and attack marker. Only after callbacks finish are picked cleared and the elapsed-delay/blinking condition evaluated with fresh time. An early-release callback that clears blinking therefore prevents the later timer reset, as in the source. Pulse's criteria-success expiration still happens before dispatch. Spoof's criteria-loss flags and player-position reset follow dispatch. The normal attack marker clears at that boundary, before Force first hit can produce its next AttackEvent; an ineligible source return preserves the marker.

All source timer boundaries are strict `elapsed > threshold`, including recoil, delay, the 100 ms Spoof lease and `Timer ticks * 50 ms`. The dedicated queue supports the complete 0–1000 ms setting range; it does not clamp FakeLag to another timing module's 500 ms delay limit.

Spoof selects Delay on opponent attack only when Smart is on, the target is swinging, its wrapped yaw differs by more than 25° from the current player direction, and by less than 25° from the stored server position direction. Otherwise it samples the configured min/max delay. Pulse retains the original distinction between FakeLag's `blinking` flag and the underlying BlinkComponent buffer after criteria loss.

Sources: [BlinkComponent](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/packet/BlinkComponent.java), [LagUtils](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/packet/LagUtils.java), [TimerUtils](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/utils/math/TimerUtils.java), [event registration order](https://github.com/YoungJibbit95/DemisePrivate/blob/e219a97fb8aab3e2b6cb88bb10fb3daab80a17eb/src/main/java/wtf/demise/Demise.java).

## First hit, range control and early release

Force first hit retains the source conditions: selected target, first-hit flag, no item use, and an entity mouse-over. The clicked entity comes from that mouse-over, as in the original `clickMouse()`. The first-hit flag becomes available again when the selected target's box distance exceeds 3. The source's local first-hit and early-release chat notices are retained with a FakeLag prefix.

Neutron runs this action inside an observed **ordinary player Update**, through `AttackScheduler` and `PlayerControllerMP.AttackExecution.NATIVE_CLICK`. The scheduler reserves the shared tick/motion budget, then FakeLag publishes its own still-current queue before the swing/controller call. This preserves native cancellation and synchronous acceptance: a queued C02 cannot later complete an obsolete automatic click. Duplicate same-tick Updates, withheld bodies and earned extra bodies cannot create extra attempts. Other modules retain their existing scheduler and transport contracts.

After a registered hit, zero local hurtTime and a box distance in `(2.5, 3]` arm WTap or Timer down. WTap clears forward input once before vanilla slowdown. Timer down holds the configured speed for the configured number of 50 ms ticks, including an intervening buffering-eligibility return. The host timer lease restores the previous speed only when the same timer still contains FakeLag's last write. A different module's timer write is preserved. Disabling, changing settings or losing the scene releases the lease, including world/player replacement that retains Minecraft's timer object.

Early release preserves the source condition and maps its PRE-tick phase to the module's observed ordinary Update. A qualifying released position can move the local player to that position. During that Update, Spoof clears remaining queued packets while keeping the current envelope; Pulse finishes the already-running publication. The source evaluates `shouldLag()` before testing Smart/Early release: every observed released C03 still updates its stateful hurt counter and WTap/timer conditions, even when Early release is off. One deliberate correction applies: C03/C05 packets without movement carry zero position placeholders, so they do not replace the stored position or teleport the player to the origin; their criteria evaluation remains present.

## Rendering

Display real pos draws only while FakeLag's blinking state is active and the camera is in third person. Box translates the local player's box expanded by 0.1 to the stored/interpolated position with source alpha 150. FakePlayer uses Neutron's actual local-player renderer at that position and current head yaw. Line records moving positions on both motion phases, prunes points older than the current delay and uses the original width 2 and age-based alpha. Colors use Neutron's active theme in place of Demise Interface colors.

Float interpolation follows the source arithmetic. A newly acquired Pulse visual starts at its captured position instead of interpolating from world origin; Spoof retains its source reset/released-position rules. Path storage is bounded. The renderer uses Neutron's `RenderState` scope to restore OpenGL and cached host state. It creates no extra player entity and does not mutate a remote player's world position. FakePlayer uses the host's normal entity rendering path; Demise's extra render-manager argument forces its shadow/fire post-pass even during outline rendering, whereas Neutron's existing renderer keeps its ordinary outline-pass policy.

## Connection and lifecycle integration

The pipeline captures final PRE selection once. FakeLag stores the existing continuation; release does not send the packet through PRE again. The continuation still passes through the original PacketDelays/barrier rules, original action/handler/entity guards, actual transport/application and one POST. Spoof timed-release observers run only after initial guard validation and before a second guard check; Pulse observers require actual accepted transport. A PacketThreadUtil quick exit keeps its ticket until the scheduled application actually completes, with lifecycle/handler/entity guards still in force. The optional PacketApplication terminal callback finalizes held metadata exactly once on success, final veto or error; a later handler error discards only FakeLag's queue and still propagates. The pre-existing PacketApplication signature keeps its default behavior. Existing ordinary C03 replay remains possible, while a BedBreaker/Scaffold C03 ticket or native action whose original lifetime expired remains rejected.

The queue preserves FIFO when a release hook or POST listener sends another matching packet, including pressure at capacity. It has at most 1024 waiting entries, a bounded per-drain workload, a maximum age of 2000 ms, and bounds all outstanding envelopes including those subsequently held by PacketDelays. An abandoned scheduled metadata application is removed from the owned outstanding set after 2000 ms and its eventual callback cannot apply. These limits are scoped to FakeLag; other timing owners' policies are unchanged. A second owner's original delay is applied once, and existing action dependency barriers still run at the original continuation. Source NoEvent/silent invocations remain outside FakeLag capture.

Normal disable, mode/settings change, criteria loss and same-scene focus/GUI suspension publish only current envelopes. Join, respawn, correction, disconnect, death, world unload, invalid object identity, connection/epoch loss, reversed clocks and release failures discard obsolete work. An unloaded-world weak reference prevents reacquisition during an Unload callback before Minecraft replaces its world field. No static cross-connection packet queue exists. The original LagUtils world-change dispatch is intentionally replaced by discard because its old connection/scene is no longer valid.

The host also clears source leftover action/timer/path state on re-enable and configuration changes, rejects dead/replaced entities, uses monotonic elapsed time, and avoids overwriting a foreign timer owner. These changes make the original features obey Neutron's established scene and native-action contracts; they are not additional user settings. A read-only `NetHandlerPlayClient.isDoneLoadingTerrain()` accessor exposes the existing terrain-readiness flag required by the source BlinkComponent path; it changes no handler state. `MathHelper.func_181159_b` is the host name of the source's `MathHelper.atan2` implementation.

## Verification map

| Test class | Contract exercised |
|---|---|
| `FakeLagDemiseConfigTest` | All 23 source controls plus two host controls; defaults, ranges, increments, visibility, round-trip hidden values and exact pinned source blobs |
| `DemiseFakeLagPolicyTest` | Strict timing boundaries, Pulse flags, Spoof opponent timing, smart/simple criteria, float range, dynamic counter, WTap, timer ticks and geometry |
| `FakeLagPacketFlowTest` | Real PRE/POST path, exact packet modes, full 1000 ms delay, lease, guard checks, actual acceptance, reentrant FIFO, capacity renewal and secondary owner |
| `FakeLagLifecycleTest` | Join/respawn/correction/death/disconnect, invalid context, stale native/rotation guards, exceptions, queue bounds and connection replacement |
| `FakeLagBehaviorHookTest` | Observed ordinary Update, duplicate budget, native first hit, target selection, source pre/post-dispatch attack marker, update-only eligibility gates, timer duration, Spoof reset timing and reentrant unload |
| `FakeLagRotationContractTest` | Actual BedBreaker/NScaffold resolved-send tickets remain expired after Pulse/Spoof release; ordinary C03 replay retains its existing behavior |
| `PacketApplicationTerminalTest` | Optional terminal callback runs once on success, initial/final veto and scheduled exception, while QuickExit remains nonterminal and the old signature remains supported |

The integration report and PR checks record actual execution results; this document describes the assertions and source contract.
