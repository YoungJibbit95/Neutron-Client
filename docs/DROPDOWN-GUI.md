# Dropdown ClickGUI

Select **ClickGui → Mode → Dropdown** in the existing PanelGUI, then reopen ClickGui
with Right Shift. Existing Panel/iOS mode indices and saved module settings remain
compatible.

The reference layout uses compact floating category columns, centered module names,
pastel pink headers and a pink-to-cyan enabled-row gradient. **Neutron** is the
reference-matching preset; select it through **Themes → Use theme**. Dropdown follows the selected
global theme instead of changing it when opened.

- Left-click a module to toggle it; right-click to expand its settings. Columns
  retain their width; compact sliders, toggles and choice fields fit inside them.
  Module and category reveals reverse from their visible position on rapid clicks.
- Drag a category header; right-click the header to collapse the category.
- Scroll inside a category to move only that column, or drag its scrollbar thumb.
  Small GUI sizes wrap columns. Collapsing a category preserves its scroll position.
- Middle-click a module, or focus it and press **K**, to assign its key. Escape
  cancels; Delete/Backspace removes the binding. Shared bindings are reported.
- **/** or **Ctrl+F** searches module names and descriptions across all categories.
  Escape first clears the search, then leaves the search field.
- **Tab / Shift+Tab** and **Up / Down** navigate modules and settings; Enter/Space
  toggles, Right/Left opens/closes module settings. Shared number fields support
  exact input, arrow adjustment and Escape cancellation.
- **Themes / Ctrl+T** opens the theme library; **Configs / Ctrl+O** opens configs.
  PanelGUI and iOS have the same **Library** entry and shortcuts. **Reset / Home** resets
  column positions without changing module settings.

The variant uses `ArrayListSurface.withScene` to capture/blur one clean scene shared
by its disconnected columns. It inherits global surface material, opacity,
Gaussian/Kawase blur policy, glass, border, glow, roundness, shared gradient/motion
settings and reduced motion. Heading Glow uses the existing `TextEffects` path.
Disabled heading glow draws text directly without additional glyph-mask passes;
gradient OFF and Solid also disable the category-wide enabled-row ramp. The column
defines one gradient coordinate domain for its enabled rows, header, outline and
glow. Palette, direction, synchronization and animation phase remain controlled by
Client Settings; scrolling does not move the gradient domain. The new Neutron preset
is appended after the legacy presets, preserving their names and numeric indices.
Checkboxes, sliders, dropdown choices, visibility and value updates are the same
production components as PanelGUI. GUI close cancels transient input and releases
owned scene/font-effect resources.

## Config and theme library

The searchable Configs tab uses the existing `ConfigManager` and named JSON configs.
**Save as** saves the current setup; replacing an existing name and deleting a file
require confirmation. Case-only names refer to the same existing file on Windows.
Selecting a row only selects it; **Load** applies it. Errors remain visible in the
library. No account, world or runtime directory is copied.

The Themes tab shows literal preset swatches, so changing the current theme or
disabling gradients does not recolor the previews. **Use theme** applies a selection.
**Edit** opens a custom theme or a copy of a preset; **Duplicate** always makes a copy.
**Effects** opens the existing client effects settings.

The editor has a scrollable form, a larger live preview where space permits, exact
RGBA inputs and an HSV picker with alpha. Click the color swatch to open the picker;
Escape restores that color. Right-click a numeric field for exact input. **Save & use**
persists and selects the draft; **Cancel** restores the previously active theme.
Deleting a custom theme requires a second confirmation. Tab navigation reveals
offscreen fields, and clipped controls cannot receive pointer input.

`me.youngjibbit.clickgui.DropdownGuiRenderCheck` is a standalone Java-8 Pbuffer/FBO
consumer check. It uses production controls, bundled fonts and a synthetic backdrop;
it does not start a world, log into an account or establish native Display/gameplay
acceptance. Run from a disposable subdirectory of `target` with `target/test-classes`, `target/classes`, plus
the Maven test dependency classpath and `-Djava.library.path=<project>/test_natives/windows`.
It writes `dropdown-preview/*.png` for visual review. The existing PanelGUI checks
remain required for the shared settings-renderer extraction.
