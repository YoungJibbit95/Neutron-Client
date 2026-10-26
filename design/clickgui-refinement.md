# ClickGUI refinement — 2026-10-26

Surface mode: **Operate**. The main ClickGUI is a keyboard/mouse tool for finding modules, understanding state and changing settings while using the client. This pass refines the existing Pulsar/Neutron identity, rather than replacing it.

## Direction

- Keep the dark reference design, category navigation, existing branding and optional theme gradients, glow, blur, rounded geometry and Liquid Glass.
- Make the task path explicit: search/category → module state → settings → exact value. Give navigation, content headings and controls distinct roles.
- Use a consistent four-unit spacing rhythm, a less cramped toolbar, a real category/search-result heading and actual module count. Keep a compact shortcut footer and visible overflow indication where needed.
- Keep 24-unit module and setting rows. Align settings to a shared trailing edge; separate numeric values from slider fill, and make dropdown, toggle and keybind affordances recognizable.
- Reuse bundled Poppins typography for settings. Fit or clip long labels without covering values. Preserve custom theme choices; do not claim universal contrast for user-selected transparent or low-contrast palettes.
- Keep exact-number entry, search, theme routing, keyboard navigation, keybind collision indications, drag/resize and cancellation. Visible geometry and hit-testing must agree after layout changes.
- Motion communicates state and must remain interruptible. Reduced Motion must update checkbox state immediately as it already does for other controls.
- Inherit the existing shared scene/material renderer. No additional per-control framebuffer or blur pass, new theme system, browser UI or gameplay changes.

The iOS-style secondary GUI and other HUDs retain their own layout in this focused pass. Shared rendering compatibility is still checked. The new glass/Arraylist snapshot `c22c7293d` is integrated as the fixed material foundation.

## Design sources and adaptation

- [Impeccable](https://github.com/pbakaus/impeccable/blob/989c484c091d26f10fa102767eefc0da2e62ec67/.agents/skills/impeccable/SKILL.md), pinned at `989c484c091d26f10fa102767eefc0da2e62ec67`: Operate, layout, polish and craft-floor guidance. User-required materials and established visual identity override generic stylistic warnings.
- [apple-hig community skill](https://github.com/justinwetch/HIGAgentSkills/blob/2206ff935b4affa9ad249a5399a3e8e29f5f6edd/SKILL.md), pinned at `2206ff935b4affa9ad249a5399a3e8e29f5f6edd`: foundational guidance applied by the frame agent, with source distinction preserved.
- Apple's primary [layout](https://developer.apple.com/design/human-interface-guidelines/layout), [materials](https://developer.apple.com/design/human-interface-guidelines/materials), [motion](https://developer.apple.com/design/human-interface-guidelines/motion) and [typography](https://developer.apple.com/design/human-interface-guidelines/typography) documentation.

These are adapted design principles, not claims of native Apple UI or accessibility API compliance. The implementation is Minecraft 1.8.9 / Java 8 / LWJGL. No SF Symbols, web framework or new font package is introduced. Impeccable's context launcher/browser detector is not executed; existing source and native screenshot fixtures provide context and mechanical validation. Downloaded reference documents remain outside the project.

## Acceptance

Batch-review default, expanded settings, search/empty, dropdown, numeric edit, keybind conflict, compact and multiple GUI-scale captures. Verify real input routing, clipped controls, hidden content, scroll boundaries, reduced motion, all material modes and stable warmed render resources. Preserve the existing regression assertions while replacing outdated hard-coded test coordinates with current control geometry.

Native Pbuffer/FBO verification remains distinct from real in-game, native-window and shaderpack acceptance. Personal runtime data and the 149 existing changed paths must remain intact.

## Delivered and verified

Branch: `codex/clickgui-design-pass`. Source integration: `fbb12638f47729b23780390d634536dedcd6e891`, based on pass 8 `dec87d9d7091251ab108ba232f8ce62078816252`.

- Material foundation: `c22c7293dad064ab6258b88c8bb58ef40b6566ce`, including the refined Regular/Clear glass and direct Arraylist material selector.
- Controls: `8de9e18a056b2607c1fbee430c60657dba6da1a2`. Numeric fields support left-click exact entry independently of slider dragging. Dropdowns, toggles and numeric fields share a trailing edge; long labels fit available space. Keybinds use compact keycaps and retain conflict indications.
- Frame: `22b275a74c9bb2d5b48fde8144c10a4dd0179a0c`. Larger search/theme targets, category/result headings and counts, compact shortcuts and a scroll-position indicator. Existing module and setting row heights are retained.
- Combined interaction checks: `1d85e404223c5095d28fd607ddfcab59cf46598a`. Existing tests now use actual control geometry; additional checks cover numeric-field activation, label fitting and first-frame Reduced Motion checkbox pixels.

Temurin 8u482 / Maven 3.9.15: `mvn -o clean test` with native GL-context checks explicitly enabled passed **341 tests**, zero failures, errors or skips. Fifteen standalone programs then passed: ClickGUI interaction, frame layout, Arraylist, accounts, glass refinement, shared composition, nested surfaces, material consumers, resource lifetime, global effects, motion, GUI themes, combined HUDs, native surfaces and Main Menu.

The render logs contain no exception/error reports. The existing missing SLF4J-binding warning remains. Visual inspection covers default and expanded panels, compact layout, Liquid Glass and the Regular/Clear × Gaussian/Kawase/blur-disabled comparison. The shader and resource checks verify warmed-buffer reuse and resize/release behavior; these are not full-client FPS benchmarks.

The 149 pre-existing changed paths were compared against their SHA-256 manifest after integration and validation: zero differences, including intentional deletions. No personal runtime data, game session, login or push was used.

Evidence: `F:/Pulsar-Recovery/analysis/clickgui-design-build.log` and project `target/clickgui-design-validation/results.json` plus individual logs. The following captures are retained in version control so a later clean build does not remove them:

- [Default](<F:/Puslar Backup - Copy/src/test/resources/visual-previews/09-clickgui-default.png>)
- [Expanded settings](<F:/Puslar Backup - Copy/src/test/resources/visual-previews/09-clickgui-settings.png>)
- [Compact layout](<F:/Puslar Backup - Copy/src/test/resources/visual-previews/09-clickgui-small.png>)
- [Liquid Glass](<F:/Puslar Backup - Copy/src/test/resources/visual-previews/09-clickgui-glass.png>)

Manual acceptance: open the main ClickGUI, search for a module, edit a numeric field by clicking its value and pressing Enter, and exercise Tab/Shift+Tab, dropdowns and clipped settings. Compare Standard and configured materials, Reduced Motion, GUI scales and window resizing in a local test world. Native display, fullscreen events and shaderpack behavior remain unverified by these offscreen checks.
