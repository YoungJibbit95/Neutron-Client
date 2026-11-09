# WAVE10 — Compact Creative HUD compositions

## Selection and real data

Explicit `Creative` selects a different geometry in ArmorHUD, TargetHUD, and Session Info. Former extra background layers and the armor contour pulse are replaced. All Value registration lines, option arrays, defaults, saved indices, functional controls, and Basic/Modern paths remain intact. There is no new global override, gameplay action, inventory mutation, packet, producer, texture, font, shader, or scheduler.

| Actual consumer | Creative composition | Preserved source |
| --- | --- | --- |
| `ArmorHUD.layout → creativeLayout → paintCreative` | Individual equipment tabs without an enclosing card. Vertical occupied rows are 22 pixels high with a three-pixel gap; empty slots form a compact strip. Horizontal occupied tabs are 34 pixels high. Original item rendering and labels sit inside each tab with a two-pixel durability rail. | Head/body/legs/feet/held order, copied original ItemStacks, smoothed actual durability, Percent/Value/Both/None, held/empty switches, orientation, user scale, chat dragging. |
| `TargetHUD.layout/editorPreview → paintCreative → portrait` | Detached 32 × 32 portrait and short information rail within a 196–244 × 46 composition. Health and absorption use separate rails. The existing portrait switch pulse contracts inside its tile. | Target validation, actual identity/skin/species badge, health/max health, measured distance, absorption, spring/reveal policy, selected palette, and deferred scene epoch checks. |
| `SessionInfo.layout → creativeLayout → creativeContent` | A 236 × 64 composition with separate elapsed-time block and factual Kills/Deaths columns; no repeated plates or decorative progress ring. | Same immutable SessionStatistics snapshot, elapsed clock including hours, verified counts, player name, and actual Live/Ended state. |

A backward-compatible `ArrayListSurface.withScene` overload shares a captured scene while honoring actual local flags and blur radius. It borrows a parent blur only when its radius matches; otherwise it reblurs the clean parent once. Existing overloads retain their behavior; equipment rendering does not allocate a framebuffer per slot. A policy-aware callback passes effective background/blur flags even on the shader-free path; segmented fills retain the central Global-versus-Local ownership contract. The pure `CreativeHudLayout` geometry is used by actual consumers. PR157 component scopes follow each Creative tab/block and the transformed portrait radius. The Creative skin scissor uses existing `GuiClip.pushTransformed` after Fit/reveal/pulse. Existing material controls still apply through the compositor. No performance improvement is claimed without native measurements.

## Bounds and motion

Target and Session uniformly fit the complete composition into the actual frame, preserving gaps at tiny viewports. Armor retains Scale and uniformly fits the entire equipment layout. Creative Armor/Session reduce the usual margin only when a very small viewport requires it. Complete content is clipped, including Target reveal scaling. Existing health, absorption, durability and reveal motion retain Animations/Reduced Motion policy. Removing the unused Creative contour springs leaves real durability springs unchanged.

## Verification

- `CreativeHudLayoutTest`: six methods for separate target parts, session blocks/counters, slot mapping/empty strips, horizontal order, disjoint geometry, and fitting down to one pixel.
- `CreativeHudCompositionTest`: four methods call actual module layouts with AWT fonts, copied Minecraft ItemStacks, observed SessionStatistics, and target editor preview. They cover functional Armor modes, immutable original items, real time/count text, mode selection, smaller footprints, and bounded placement. They do not execute native paint.
- Actual pure production geometry compiled with OpenJDK 21 `-source 8 -target 8`; the standalone harness passed **8,381 assertions**, including all 32 five-slot occupancy masks in both orientations. No JUnit substitutes were used.
- `CreativeHudScenePolicyTest`: four methods cover actual Session/Armor local-radius selection, global override, effective Global/Local background flags, and refusal to borrow mismatched/missing blur. These policy tests do not execute framebuffer operations.
- Eight changed Java files parse with zero Java-8-grammar errors. The 12 Armor, 4 Target and 8 Session Value registration lines are byte-for-byte unchanged.

Full Java 8 Maven/JUnit for this delta is **NOT_RUN** pending CI. Native Minecraft/OpenGL, item glint/lighting/state restoration, rectangular skin clipping, material/palette appearance, shaderpacks, chat overlap, screenshots and frame-time p95/p99 are **NOT_RUN**. Required captures remain 640 × 360, 854 × 480, 1280 × 720 and 1920 × 1080 with all three Creative selections, both Armor orientations, mixed/empty equipment, target switch/loss, long names, live/ended sessions and Reduced Motion. Pure geometry does not establish these render gates. Existing native Armor/Target/Session checks are retained; hosted native skips are not passes.
