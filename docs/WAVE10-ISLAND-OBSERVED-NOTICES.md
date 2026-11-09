# WAVE10 — Observed Island notices

## Sources and lifecycle

This delta extends the Activity/Clock Island without module toggles, inventory actions, world changes, packet sends, or an additional scheduler. Module toasts require the separate `ModuleToggleJournal` producer patch. The new Diagnostics producer passively copies scalars from its existing successfully applied S08 correction observation.

`LocalCorrectionSnapshot` contains a process-unique diagnostic run token, sequence, monotonic observation time, distance, and rotation delta. It retains no player, world, module, packet, inventory, or connection reference. Its getter returns a cached observation only on the client thread while the original player/world/connection context, enabled Diagnostics/Flag Detector settings, and 2.4-second lifetime remain valid. Initialization and replay corrections do not publish notices. History reset, flag-detector disable, scene loss, or expiry invalidates the cache. The unique run token prevents a recreated Diagnostics module from reusing an old notice identity.

The UI converts a fresh `ModuleToggleSnapshot` into a 1.9-second notice and a fresh correction into a 2.4-second notice. Expiry starts at producer observation; later render frames cannot extend it. The correction says **Possible setback** and **Local correction · cause unknown**. Distance and rotation measure that local correction; they do not establish an anti-cheat cause.

`IslandNoticeFlow` starts at the current module sequence and monotonic time when an Island session begins. It consumes each observation once, including hidden events, so re-enabling an option cannot replay old notices. Warning priority is processed before a simultaneous module change; a rejected lower-priority event is not queued. Producer loss, expiry, a changed diagnostic run, or a disabled option withdraws the corresponding observed notice. Scene changes reset the model, cursor, and transition. Deferred callbacks also fence captured world/player identities. Lost gameplay views do not survive through a crossfade.

`Island Module Toasts` and `Island Local Warnings` are appended after existing Island values, both defaulting to enabled and visible only for Island modes. Existing order/defaults are retained. Warnings require enabled Diagnostics and Flag Detector; the UI never enables them.

## State compositions

| State | Data | Composition |
| --- | --- | --- |
| Idle | Client identity | Original N with Audiowide Neutron in a local text-gradient domain. |
| Clock | One wall-clock capture before deferred drawing | Whole-Island bounded 64-pixel circle, centered hour/minute/second hands and the clock delta's Reduced Motion policy. |
| Target | Actual target snapshot | Identity and actual health/absorption rail. |
| Scaffold | Fresh immutable producer snapshot | Display-only original item ID/metadata, actual block count, approach/profile, and observed status. |
| Inventory | Fresh InventoryManager/Stealer snapshot | Tray symbol, actual producer/action and counters on a 236 × 42 logical canvas. Progress appears only with known processed count and a positive total; unknown totals have no invented percentage/animation. |
| Module toast | Fresh ModuleToggleJournal snapshot | 206 × 38 status row, check/minus symbol, actual module name and ON/OFF chip, without a repeated N badge. |
| Possible setback | Fresh Diagnostics correction | 254 × 72 warning, amber marker, uncertain-cause wording, and separate Distance/Rotation columns, without a repeated N badge. |

`IslandContentLayout` scales and centers the complete inventory/notice canvas inside the actual morph frame, bounded down to a one-pixel viewport. Native drawing retains scoped render-state restoration. Existing clock cadence, priorities, cooldowns, and fixture APIs remain unchanged.

## Regression evidence

Ten new repository JUnit methods span `LocalCorrectionSnapshotTest` (scalar/finite-value/TTL boundaries), `IslandNoticeFlowTest` (actual presentation, warning priority, uncertainty, expiry, no replay, source/option loss, scene/run identity, one-pixel geometry), and `DiagnosticsWarningSnapshotTest` (actual packet fixture: cancellation, application and original acknowledgement, read-only repeated getters, clear-history, initialization/replay, expiry/world loss, Flag Detector invalidation).

Local execution used OpenJDK 21 with `-source 8 -target 8`, not Java 8: 53 assertions passed against actual DTO/adapter/state/flow/presentation/layout sources. The earlier 448 clock/morph and 15 Activity-view assertions passed against these updated sources. Thirteen changed Java files parsed with zero Java-8-grammar errors. The clock harness retains its documented compile-only ClientSettings dependency for an unused MenuMotion resolver; it throws if invoked, is never called, and is excluded from this patch. No JUnit stubs were used or counted as repository tests.

Full Java 8 Maven/JUnit for this delta is **NOT_RUN** pending CI. Native OpenGL screenshots of all seven states, small/large viewport and Reduced Motion appearance, and frame-time p95/p99 remain **NOT_RUN**. Pure layout/source checks do not establish native visual correctness.
