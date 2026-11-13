# Custom scoreboard and hotbar

The **Render → Custom Scoreboard** and **Render → Custom Hotbar** modules control
the two custom designs. Disabling either module restores its native Minecraft
renderer. New installations and older profiles without these two entries keep
the previously enabled custom designs; an explicitly saved disabled state wins.

The scoreboard measures and draws with the same selected UI fonts. Its heading,
score column and row spacing are independent, and the panel remains within the
available screen area across GUI scales. It filters hidden `#` entries before
selecting the fifteen visible rows. Very long text fits the available column with
an ellipsis; scores and the server's objective data are unchanged. A shorter
viewport scales the complete card so its rows remain reachable.
The automatic anchor leaves the hotbar and normal native status/armor rows clear,
including at the largest scoreboard scale in a small viewport. It uses the
hotbar's actual bounds, the native 49-pixel status area and a six-pixel gap. The
existing centered anchor is retained whenever it fits above that area.

Open chat or the existing HUD editor to drag the scoreboard. The drag bounds are
the rendered panel bounds, the grab offset is retained, and the shared HUD grid
and screen-bound settings apply. The HUD editor reserves its bottom controls.
The custom anchor is saved with the profile; **Reset HUD positions** restores the
automatic right-hand anchor. An editor preview is available without a server
sidebar. The module's **Show Scores** and **Scale** settings control its content.
Manually positioned cards retain the full screen/editor bounds; the automatic
bottom reserve does not override a chosen position.

The hotbar retains nine native slots and their current items, counts, durability
and pickup feedback. A twenty-pixel selection cell fits the twenty-pixel slot
spacing without covering its neighbours. The selection marker and subtle icon
feedback advance with render time; changing a hotbar slot still changes the game
selection immediately. Global animation and reduced-motion settings apply, and
the two animations can also be disabled in the module. Motion and captured scene
resources are reset on disable and world changes.

Both designs use the shared surface/effects settings. The custom hotbar's clean
scene capture can be reused by the following Event2D HUD pass. The native render
regression exercises actual fonts, items, fallback routing, GUI scales 1/2/3,
reduced motion, an empty-sidebar editor preview, GL/FBO restoration and warm GPU
resource reuse. It writes `release-custom-hud-resize.png` with the existing native
HUD evidence. `release-custom-hud-clearance.png` additionally records all four
viewports at scoreboard scales 0.65, 1 and 1.6; the check verifies that drawing the
sidebar leaves every pixel inside the already rendered hotbar unchanged. Live
launcher/game acceptance remains separate from those checks.
