# Compact Neutron UI and motion

## Design intent

This pass addresses the November 11 screenshot review. The Island, Modern/Creative HUDs, chat and menus use compact geometry, rounded visual indicators, restrained text and visible state transitions. Existing global and local surface/material settings remain the owners of appearance. Neutral fills inside menu controls improve legibility over bright worlds without introducing a new global dark overlay.

The implementation is based on main commit `42b4ffd1b35160b7beef90905834ea43d2a37888`. Changes concern presentation, layout, input-coordinate mapping and configuration compatibility.

## HUD composition

Logical GUI units are measured before the existing HUD scale is applied.

| Element | Modern | Creative |
| --- | --- | --- |
| TargetHUD | 142 × 34; integrated portrait, health rail and compact indicators | 142 × 34; detached circular portrait/orbit and adjacent capsule |
| Session Info | 154 × 42; compact time and counter composition | 154 × 40; orbit-led composition |
| ArmorHUD | Compact occupied/empty tiles with real items and durability | Compact separated tiles and animated indicators |

The TargetHUD reference Tenacity preview is 190 × 44. Thus the new compact modes use about 75% of its width and 77% of its height. Modern and Creative share a motion vocabulary but keep different silhouettes and hierarchy.

TargetHUD now exposes exactly Rue, Tenacity, Basic, Modern and Creative. Existing configurations migrate their named choices:

| Previous choice | Retained choice |
| --- | --- |
| Rise / Rue (Rise) | Rue |
| Neutron Default / Test | Basic |
| Neutron White / Neutron Outline / Neutron 2.0 | Modern |

The migration applies only to TargetHUD's mode value. Invalid or unrelated values retain normal validation behavior.

## Island attachment and docks

Notification and contextual content expand below a stable top capsule. The attachment occupies approximately 52–74% of the capsule width; content length no longer stretches the whole watermark. Rounded, feathered vector geometry gives status icons consistent weight. Check reveal, loading orbit and value changes animate within bounded space.

Circle widgets use six fixed positions: Left 1–3 and Right 1–3. In the HUD editor, drag a visible widget onto another dock and release to move or swap it. The three persisted dock settings are appended to existing values to preserve prior ordering. Rendering resolves duplicate saved positions without writing settings; a completed drop normalizes the visible arrangement before swapping.

While editing, layout reserves all six destinations, including in small GUI viewports. Normal rendering reserves the occupied composition. HUD reset restores the dock defaults.

## Chat presentation and input

The message history and typing bar share the existing inherited surface renderer. The message panel sizes itself to visible content. A bounded entrance/exit response supplies slide and bounce; row movement, focus and typing each have a small independent motion state.

The existing GuiTextField continues to own editing, selection, history and autocomplete. Public link picking uses the same frame snapshot, scale, clipping and animated line coordinates as rendering. Input clicks subtract the current typing transform. Resizing preserves the draft and cursor/selection and rewraps retained messages. A scrolled history anchor is tracked by original component identity and text offset. Refreshing a nonzero-ID message does not delete its retained source.

Reduced Motion or disabled UI animations settles positional channels immediately. Message age still controls the vanilla ten-second lifetime. Closing input is rendered briefly by the HUD after GuiChat leaves the active-screen slot.

## Menus and readability

Modern ClickGUI rows use 36 logical units, with smaller navigation, header and toggle controls. Category painting and hit testing share bounds. Creative keeps a responsive two-column composition. Legacy module rows receive readable local fills without additional nested glass captures.

Configuration cards use a denser 60-unit layout. Library, settings and theme controls use consistent spacing, clearer selected/hover states and separated action areas. The Library hub is bounded to 496 × 296; settings remain responsive within compact maximum frames.

## Performance and verification

Motion uses bounded scalar state, reusable arrays and existing surface ownership. This pass introduces no new shader, asset dependency or framebuffer type. Native checks assert stable GPU resource creation after warm-up. These checks are resource-lifecycle evidence; they are not a claim of improved in-game FPS.

The normal Java 8 regression remains required. The native UI driver runs six independent JVM suites: panel, dropdown, arraylist, session/HUD/Island, shader modes and chat. It retains production shader/FBO diagnostics and fails on missing checks, images or native errors. Coverage includes:

- Compact HUD geometry, mode migration and motion bounds.
- Dock placement, duplicate persisted slots, small viewports and drop behavior.
- Real chat glyph/link picking at multiple scales; editing, history, autocomplete and resize.
- Opening, typing, closing, expiry and reduced-motion behavior.
- Production menu controls, local readability and GL state restoration.
- Warm-render framebuffer, program, shader and font-texture allocation checks.

Original PNGs and logs are uploaded as CI artifacts. Bounded JPEG review copies of selected real fixture images are also embedded in job logs for remote visual inspection. The separate render-performance workflow retains its unchanged exact-pixel baseline checks and synthetic timing evidence.

Offscreen Pbuffer/FBO tests cover the production UI renderers on synthetic backdrops. They do not constitute full client bootstrap, live game-world, multiplayer, fullscreen or VSync acceptance. PR checks and their exact tested commit are the authoritative validation record.
