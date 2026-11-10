# Island clock, activity and notifications

These options appear in Watermark when an Island design is selected. Existing
saved setting IDs and indices are preserved; the new controls are appended.

| Setting | Behaviour |
| --- | --- |
| Island Clock | Existing right-hand time and ten-minute clock reminder. |
| Island Clock Style: Text / Modern | Digital time or the same live analogue face used by the full clock state, fitted on the right of the base capsule. |
| Island Notifications | Route the ordinary client notification queue exclusively to the Island. The notification module's duration, category filters, coalescing and queue limits still apply; hidden Island content waits. |
| Island CPS Widget | Actual left/right mouse button-down events in the last second, shown as `L · R`. Releases, scroll events, other buttons and automatic module actions do not count. |
| Island FPS Widget | The client's measured debug FPS counter; an unavailable counter is shown as `--`. |
| Island Exploit Widget | Fresh observations from Backtrack, LagRange and Tickbase. Enabling a module alone never lights the activity indicator. |
| Island Widget Placement: Inside / Outside | One choice applies to all three widgets. Inside uses small inset cells; Outside uses separate round capsules at the base Island's height. |

The new optional widgets and global notification routing default to off. The clock
defaults to Text, preserving the existing base presentation. The old generic FPS
and Player fields remain available for non-Island watermark designs.

## Actual activity

The exploit cell identifies its source with an icon and a short label:

| Label | Icon | Observed data |
| --- | --- | --- |
| BT | Rewind chevrons | Backtrack's own held packet age in milliseconds; `Hold`. |
| LR | Staggered trails | LagRange's own held packet age, or its earned player-step credit when available. |
| TB | Lightning stroke | Tickbase's earned credit in ticks (`t`), or released extra steps (`+Nt`); `Ready` or `Fire`. |
| EXP | Quiet diamond | No fresh active observation; `Idle` and `--`. |

An observed release takes priority over a held queue; held work takes priority over
unused credit. Equal-priority active sources rotate every two seconds. The small
rim and icon pulse are decoration, not a percentage or an additional gameplay
signal. Reduced Motion freezes that decoration while the actual values continue
to update. Producer observations expire after 250 ms and are withdrawn on a scene,
connection or owner reset.

### Outside widgets and observed incidents

Resting outside widgets are exact circles: their corner arc radius is precisely
half their diameter. The dedicated Island contour factory does not change the
legacy HUD contour radius contract. During an incident a circle stretches into a
short capsule, retaining circular ends, and returns to the same resting circle.
Outside widgets remain beside every Island state, so a local warning, module
toast or clock reminder cannot hide an incident. Inside widgets stay in the base
row. Width and diameter shrink together in height-constrained viewports.

The FPS widget can briefly expand after a measured slow client frame or a clear
drop across the measured frame window. Sampling pauses while the HUD is hidden,
the client is unfocused or a screen is open; returning from a menu must not be
presented as a slow frame. The exploit widget can expand when a successfully
applied local correction follows actual observed exploit activity in the same
world, connection and packet epoch. Its text reports the measured correction and
the preceding activity. Temporal correlation is explicitly **not a confirmed
cause**, server flag or named check. Incidents expire after three seconds and do
not remain open merely because a module is enabled.

## Connected lower sheets

General notifications, local correction notices and verified target information keep the name, version,
clock and selected base widgets visible above their content. A single connected
outer contour grows down from the base; there is no second notification/target
card or horizontal border seam between the two areas. The base retains its top
anchor while the lower sheet opens. The modern target composition uses a 34-pixel
header plus a 52-pixel detail canvas; notification details use 58 pixels.
Local correction notices retain their 72-pixel measurement canvas below the base;
the active state selects exactly one lower content renderer.

Target health remains the actual frozen value. A translucent damage trail follows
the current health bar with a damped spring, while the name, health/absorption
values and surrounding reticle remain readable. Losing a target withdraws its
content immediately; only the empty shell may finish closing. Module-toggle
notices keep their whole-Island presentation.

The existing physical spring keeps its velocity on retargeting. Notification
sheets use a more damped vertical response; target acquisition has a slightly
more expressive response, and closing is quicker. FPS and exploit incident bars
use their own width springs. Reduced Motion snaps directly to readable geometry
and suppresses decorative motion.

## Stable Scaffold state

Scaffold uses one 224 × 46 logical canvas: narrower than the 234 × 34 modern base
capsule, with a small downward extension. Searching, aligning, ready, placing,
edge guard and recovering update the internal status glyph and label. They do not
restart the content fade or change the outer shape. Count, CPS and rotation
profile changes also leave the content transition untouched.

The count is the placement service's usable hotbar block count. Scaffold CPS is
the number of **confirmed placements** observed in the last second, not the
configured CPS, search count, attempted click count or physical mouse clicks. The
small two-second sparkline samples that measured rate eight times per second.
Counter resets, clock reversal, stale observations and long render gaps clear the
rate history instead of displaying an invented catch-up burst. Unknown telemetry
is displayed as `--`.

## One notification owner

NotificationManager keeps the single queue and lifetime clock. While routing is
active, one notification occupies the Island's attached lower sheet, including its type, title, message,
coalesced count and remaining-duration rail. Other visible cards are parked with
their remaining display time and shown subsequently; they are not copied into a
second Island queue. Notification state takes precedence over other Island
content so it cannot expire behind a target or Scaffold view.

With Island Notifications enabled, ordinary toasts remain suppressed even when
Watermark is disabled, a non-Island design is selected, HUD/F1/debug visibility
hides the Island, the world/player is absent, world rendering is skipped, or a
full-screen GUI covers it. The existing queue and its remaining display time
pause until the Island can present it. Chat permits Island presentation. Turning
the option off explicitly returns that same queue to ordinary toasts. Existing
notification-category filters continue to apply in both presentations. The
separate Island module-toggle and local-warning options remain independent
producers.

## Rendering and validation

The three detached circles share the capsule's captured scene and existing surface
compositor. No new framebuffer owner or animation clock is introduced. All live
data is copied before deferred painting; scene and epoch checks fence the queued
render pass. The full group is centered and scaled into the viewport, including
tiny viewports. The modern clock reuses `IslandClockFace`; the exploit signatures
and placement sparkline use the existing scoped vector rendering path.

Pure tests exercise group bounds, circular geometry at 720 angles, connected
sheet contours, stable Scaffold content identity, measured CPS, input-source
filtering, observation expiry, icon bounds and Reduced Motion. Native
visual acceptance still requires checking the in-game renderer at the supported
GUI scales, both widget placements, global surface styles, the modern clock, a
notification burst and continuous Scaffold activity. A headless geometry test is
not a screenshot or an in-game visual acceptance result.
