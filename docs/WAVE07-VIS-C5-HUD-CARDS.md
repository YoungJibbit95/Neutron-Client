# WAVE07 VIS-C5a — TargetHUD, ArmorHUD and Keystrokes: first unified card revision

**Owner VIS, GitHub-only. Draft until source/CI/native gates.** Effective baseline main `756ee105d2716eab06c71db88e1bdeb17ea3b302`, after independently merged TECH Adaptive Bridge. No Windows checkout/worktree, main push, TECH, Packet, Combat or Inventory changes.

## Source / image references
- WAVE07 design revision ZIP: `01_IST_HUD.png` actual user screenshot vs `03_ZIEL_HUD_KONZEPT.png` generative target sketch; the latter is **not an in-game implementation proof**.
- Approach-2 crops: `10_TargetHUD.png`, `11_ArmorHUD.png`, `12_Hotbar.png`, `15_Render_ESP_Nametags_Tracers.png`. Images only inform source geometry/typography and cannot be copied as bitmap textures.
- Inspected `TargetHUD`, `ArmorHUD`, `Keystrokes`, `HUD`, `HudPlacement/HudReveal/HudSpring`, `ThemeCatalog`, `ThemeMode`, existing ConfigManager/TargetHud and armor item/GL regression tests.
- C2 [#115](https://github.com/YoungJibbit95/Neutron-Client/pull/115) active and Java8 red due to a malformed custom-theme test fixture; C3 [#117](https://github.com/YoungJibbit95/Neutron-Client/pull/117) current Java8/CodeQL/Security/Bundle green; C4a [#118](https://github.com/YoungJibbit95/Neutron-Client/pull/118) Java8/CodeQL/Security green, build checks independent. **All paths disjoint**, no active branches modified.

## Actual product consumers

| Real consumer | Legacy | Neutron 2.0 style on global opt-in |
| --- | --- | --- |
| `TargetHUD.layout → paint → content → portrait` | All 7 historic designs, one appended 2.0 option, saved White index 1, existing target validation, chat grid/drag, exact health/absorption/distance and world epoch | Neutral graphite ~86% opaque card, small selected-theme hairline, far less bloom/glow, clear 2-level text: name, factual Health **current/max**, real locally measured distance in meters, only real absorption when >0, five-unit ratio/absorption bar; existing spring/switch pulse, same clipped skin, no new texture. Source theme uses `ThemeCatalog.global().current()`, **not forced Pink** |
| `ArmorHUD.layout → paint` | Vertical/Horizontal, Durability Percent/Value/Both/None, background/blur/glow, held item, empty slots, functional scale, positions all retained | One graphite glass outer surface with restrained border, compact individually separated equipment cells, nuanced selected-theme hairline, brighter text and 3px existing semantic durability bars, previously implemented `HudSpring` durability smoothing retained. Real copied `ItemStack` and real `RenderItem` lighting/depth hooks unchanged. No new GL target |
| `Keystrokes.Event2D.render` | Existing seven optional key bindings, mouse buttons, positions, chat-only drag, old individual surface animations | One grouped `ArrayListSurface.panel` instead of per-key FBO/material pass; real `GameSettings` key down, seven bounded per-key `HudSpring` toggles, muted per-key graphite cell and theme-colored press fill, white readable glyphs, world/disable reset. Settings `animations/reducedMotion/motionIntensity` apply |

**No new packet/gameplay/inventory/click traffic**, no capture from game world other than existing actual target distances, armor inventory snapshots and key presses. Presses/state are read on the GUI render thread. Position changes can only follow a genuine chat drag capture, not passive rendering.

## Old configs, migration and scope boundaries
- No historical `TargetHUD Mode` or Armor functional choices are removed or reordered. Legacy's White default and old Mode indexes/labels stay loadable. No automatic config saves/migrations.
- `ArmorHUD Layout`, `Durability`, `Mouse Buttons`, scale, positions, bar controls remain **functional** and are not collapsed into cosmetic modes.
- Full global/local 2-visible-design migration is **not** complete: requires C2 palette ownership merged, named and index ConfigManager translator, and per-widget explicit-override semantics; do not prematurely reduce older enums. Modern applies by existing ThemeMode opt-in only unless the appended TargetHUD 2.0 Mode is explicitly selected.
- Hotbar new overlay was **not implemented here** because the existing vanilla/inventory hook and native input/GL are a separate contract. `ESP/GlowESP/Nametags` remain C6 world-render native-gated. No invented hotbar, slot or entity data.
- TargetHUD skin corners are still rectangular in the old `GuiClip` implementation even though the surrounding portrait tile is rounded. A true rounded skin mask requires the authorized native shader/Pbuffer gate; do not claim pixel-perfect correspondence to the generated concept.

## Tests and acceptance
- `Wave07ModernHudCardsTest` verifies actual modern TargetHUD palette method over all 20 presets, exact 8 historic/current mode options and default, full Armor functional modes and semantic durability, seven Keystrokes Spring slots and old key labels.
- `Wave07ModernHudConfigTest` runs real ConfigManager roundtrip for target mode/X/Y, Armor layout/durability/X/Y, Keystrokes mouse buttons/X/Y and unchanged defaults.
- Existing `TargetHudNeutronTwoModeTest`, `TargetHudMotionContractTest`, `ArmorHudQa01Test`, `ArmorHudCheck`, `Wave05HudOverlapCheck` and HUD drag contracts must remain.
- Pending actual Java8 PR merge commit, suites/tests/failures/errors/skips and CodeQL/Security/Bundle check. No Green until complete.
- **RUNTIME_PENDING/NOT_RUN**: native Pbuffer/LWJGL, GL state/texture/RenderItem, actual Minecraft 1.8.9 screenshots at 640×360/854×480/1280×720/1920×1080 with different palettes, world change, chat and shaderpacks, hotspots, collision/overlap and FPS p95/p99. Existing native checks are **not** assumed to have been executed.

No automatic merge/release/tag; every pending source/render gate retains Draft.
