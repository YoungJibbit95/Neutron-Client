# Integration pass 8 — shared visual composition

Date: 2026-10-26. Branch: `codex/integration-pass-8`.

- Base: `92b8af5c05b9bf826a86100cb9e2d4ee647e9619` (pass 7).
- Fixed incoming visual snapshot: `39b1ceb653d10ec4eb86852ae08e2eb0e9efba32`.
- Integration merge: `2a8bc9540`.
- Menu-title integration follow-up: `f8b07808e9de96ed4f6036a86d2aa504c6cff4e4`.

## Merge decisions

The MainMenu conflict combines the incoming shared `drawBackdrop` entry point with the existing neutron-star shader, quality level 2, spring feedback, clamped hover values, release activation and responsive placement. Account screens use that shared backdrop. Incoming accent-opacity handling is retained.

The corrected vertex shader supplies glyph color. The menu title therefore uses `TextEffects.direct` again, replacing the pass-7 single-color workaround. New pixel assertions check visible title glyphs, spatial horizontal/vertical gradients and two animation phases. The unchanged merge version fails the negative-control test; the corrected version passes.

RenderState adds fog synchronization while keeping Core/ARB/EXT dispatch and the temporary-FBO restoration path for deleted targets. SharedResourceCheck retains both the FBO regression suite and the new distinction between shared effect buffers and owned scene buffers.

ConfigManager keeps the existing network/rotation migrations and adds compatibility for saved local theme/gradient choices and text-glow extent. Existing explicit local choices remain local. The duplicate Standard-material fixture setup introduced by automatic merging was removed.

Network, rotation, Scaffold, Aura and the two pass-7 performance implementations were not modified. Both parent histories are preserved. Later work from the separate visual chat is outside this fixed snapshot.

## Validation

Temurin 8u482 / Maven 3.9.15. Clean package build: 340 tests, zero failures/errors/skips, with native GL-context testing explicitly enabled. The child JVMs also pass 32 productive binding checks. Following the title change, the final package build and affected menu/material-consumer checks are repeated.

Twenty-three separate render/statistics programs cover Menu, Shared composition, Nested surfaces, Material consumers, Shared resources, Accounts, ClickGUI, Arraylist, Statistics, SessionInfo, Effects, Fonts, Cleanup, Theme editor, GUI themes, Watermarks, ArmorHUD, Settings, Global effects, Notifications, Motion, Combined HUDs and Native controls.

Specific coverage includes all four materials across actual consumers, scene reuse for 1/20 sibling panels, notification stacking, no compounded nested opacity/blur, sharp foreground content, both gradient axes, both blur algorithms, master switches, local accents, RGBA themes, odd sizes/GUI scales, all available Core/ARB/EXT FBO entry points and two complete resource release/reopen cycles.

The 149 pre-existing changed paths, including runtime files and deletions, are checked against a SHA-256 manifest. No personal game session, login or push is performed.

Evidence: `F:/Pulsar-Recovery/analysis/integration-pass-8-build.log`, `integration-pass-8-final-build.log`, and project `target/integration-pass-8-visual/`. Maven's existing dependency-overlap warnings during shading remain; packaged-jar launch is not established by this build.

## Manual acceptance still required

Switch Clear/Gradient/Blur/Liquid Glass in Client Settings and check existing Local overrides explicitly. Exercise menus and both ClickGUIs, open/close account dialogs, overlap HUDs/notifications, change GUI scale and window size, save/reload a test config and restart. Leave a local singleplayer world for the neutron-star menu. Repeat with the intended shaderpack/native display configuration.

Pbuffer/FBO fixtures use synthetic scenes and controlled world/render data. They do not replace native-window, fullscreen, real world-camera, shaderpack or live account/session acceptance. Vanilla container textures and world geometry remain outside the material-composition scope.
