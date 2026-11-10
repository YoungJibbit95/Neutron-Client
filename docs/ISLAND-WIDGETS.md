# Island clock, activity and notifications

These options appear in Watermark when an Island design is selected. Existing
saved setting IDs and indices are preserved; the new controls are appended.

| Setting | Behaviour |
| --- | --- |
| Island Clock | Existing right-hand time and ten-minute clock reminder. |
| Island Clock Style: Text / Modern | Digital time or the same live analogue face used by the full clock state, fitted on the right of the base capsule. |
| Island Notifications | Present the ordinary client notification queue inside the Island. The notification module's duration, category filters, coalescing and queue limits still apply. |
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
active, one notification occupies the Island, including its type, title, message,
coalesced count and remaining-duration rail. Other visible cards are parked with
their remaining display time and shown subsequently; they are not copied into a
second Island queue. Notification state takes precedence over other Island
content so it cannot expire behind a target or Scaffold view.

Ordinary toasts are used immediately when Watermark is disabled, a non-Island
design is selected, HUD/F1/debug visibility hides the Island, the world/player is
absent, world rendering is skipped, or a full-screen GUI covers it. Chat permits
Island routing. Existing notification-category filters continue to apply in both
presentations. The separate Island module-toggle and local-warning options remain
independent producers.

## Rendering and validation

The three detached circles share the capsule's captured scene and existing surface
compositor. No new framebuffer owner or animation clock is introduced. All live
data is copied before deferred painting; scene and epoch checks fence the queued
render pass. The full group is centered and scaled into the viewport, including
tiny viewports. The modern clock reuses `IslandClockFace`; the exploit signatures
and placement sparkline use the existing scoped vector rendering path.

Pure tests exercise group bounds, stable Scaffold content identity, measured CPS,
input-source filtering, observation expiry, icon bounds and Reduced Motion. Native
visual acceptance still requires checking the in-game renderer at the supported
GUI scales, both widget placements, global surface styles, the modern clock, a
notification burst and continuous Scaffold activity. A headless geometry test is
not a screenshot or an in-game visual acceptance result.
