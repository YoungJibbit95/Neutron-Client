# Diagnostics HUD

`Diagnostics` is an opt-in module in **Render**. Its four widgets are independently
visible. The default is the compact user view; developer details, notifications and
every console channel default to off. It does not alter rotations, movement,
packets, queue leases or module settings.

## Layout and settings

- Open chat and drag a widget with the left mouse button, or use `.diag edit`.
- Scroll over a widget in chat/the editor to inspect overflowing details. Other
  wheel events continue to scroll chat normally.
- `.diag move performance 20 40` sets exact coordinates. The other widget names
  are `rotation`, `network` and `flags`. Each also has persistent X/Y settings.
- `.diag reset` restores the responsive two-column arrangement. Once moved, a
  widget keeps its own position. The global HUD reset includes Diagnostics.
- `.diag on`, `.diag off` and `.diag clear` control capture and correction history.
- `HUD Visible`, `Edit HUD In Chat`, screen bounds and the HUD grid are respected.
  F3 hides the widgets; selected console capture continues. The editor does not
  pause the game. Hidden widgets release their drag ownership.
- Use `Widget Scale` and the existing `Effects Source` controls for presentation.
  The compositor follows the existing theme, global effect switches and local
  overrides. `Animate Meters` smooths meter lengths with the existing response
  function (rate 24); numerical readings remain direct. Reduced Motion disables
  this smoothing. Graph samples do not animate between measurements.

## What the readings mean

**Performance** measures exclusive CPU submission time in the existing
`root/render/gameRenderer` profiler tree, without enabling or modifying F3's
profiler. Ranked percentages refer to the measured sections. HUD surface work and
the explicit ESP, GlowESP and Nametags scopes are inclusive measurements that
overlap the section tree and can overlap each other; do not add them to the total.
The diagnostics HUD measures its own panel work as well. CPU and driver work may
include stalls, but this is not a GPU timer or a measurement of GPU utilization.
The graph contains the last 60 frame intervals, including frame limiting/VSync.
The graph is not the render CPU total.

**Rotation Manager** separates camera angles, last locally accepted wire angles,
pending requests, arbitration priority, motion sequence and return-lease presence.
The immutable snapshot reads fields directly; it never evaluates request validity
or expires a lease. An old wire value is labelled as historical, and unavailable
wire values are shown as unknown. Local transport acceptance does not prove server
acceptance. User and developer rows have separate switches.

**Network** lists enabled Exploit modules, their actual available runtime status,
server-reported player-list ping, inbound/outbound queue sizes, remaining local
holds, requested delay and oldest queued age. Backtrack, LagRange and Tickbase
provide dedicated status text; an unsupported module is explicitly labelled as
having no runtime telemetry. `Network Values` exposes visible numeric, choice and
checkbox settings. The queue snapshot never pumps queues, tests match predicates,
renews leases or releases packets. Local hold time is not RTT. The client cannot
derive an unmodified ping or a module's causal effect on server ping from this
metadata, and does not invent either.

**Flag Detector** records successfully applied S08 position/look corrections.
Cancelled, failed and stale applications do not produce entries. Relative packet
flags are resolved by the real vanilla handler; the observer compares its applied
position and camera angles after the vanilla acknowledgement. A displacement over
0.05 blocks or angular change over 2 degrees is called a *possible setback*.
These thresholds suppress tiny corrections; they do not classify anticheat checks.
The first 100 player ticks/dead-player context is classified as synchronization.
Silent/replay applications are labelled separately and never produce setback
notifications. Ordinary teleports can also match the possible-setback thresholds.

Each of the last 24 events freezes enabled module context, recent local rotation
ownership, queue metadata and relevant settings. Developer rows list candidate
settings for controlled, one-variable A/B comparisons. An enabled module is not
proof of causation. No safe numeric setting, server check name or violation level
can be inferred; authoritative flag attribution requires server logs. No settings
are changed automatically. World/player/connection changes, disable and clear
invalidate pending evidence. Capture failures are isolated from packet handling
and counted in developer details.

## Console capture

`Performance Console`, `Rotation Console`, `Network Console` and `Flag Console`
are independent. Periodic channels use `Console Interval MS` (default 2000,
minimum 500). `Console Active Values` adds per-setting values to network output
and freezes them alongside correction output. Flag logs are emitted for each
applied correction; notifications have a separate configurable cooldown.

Lines use `[NeutronDiagnostics][channel] nanos=...`, invariant decimal formatting,
full profiler paths and explicit `UNKNOWN`/`UNCONFIRMED` markers. Correction IDs
link context, review candidates and value output. No packet payloads, account
identifiers or chat text are captured. The existing console/logging sink handles
storage; no separate diagnostic file writer is installed.

## Verification

Java 8 tests cover exclusive timing, disabled-path overhead (no clock reads),
thread/profiler isolation, bounded graphs/history, non-mutating queue snapshots,
configuration round trips, exclusive dragging, and the real S08/ack/cancellation
path. Existing packet, rotation and HUD tests remain applicable.

`DiagnosticsRenderCheck` runs through `SessionInfoRenderCheck` with
`-Dpulsar.diagnostics.check=true`, the Maven test classpath and
`-Djava.library.path=<project>/test_natives/windows`. Run it in
`target/diagnostics-20261107`. Its PNGs use the production OpenGL panels, shaders,
fonts and graph renderer with **synthetic readings and a checkerboard backdrop**.
They verify compact/developer/empty states, themes and small-screen/long-data
geometry. They are not live gameplay or performance benchmarks.

Live native-client layout/input, representative world rendering and a controlled
server session with corresponding server logs remain acceptance gates. Until those
are checked, the implementation's PR stays Draft.
