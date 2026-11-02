# Dropdown ClickGUI

Select **ClickGui → Mode → Dropdown** in the existing PanelGUI, then reopen ClickGui
with Right Shift. Existing Panel/iOS mode indices and saved module settings remain
compatible.

The reference layout uses compact floating category columns, centered module names,
pink headers and a cyan/pink enabled-row gradient. **Cotton_candy** is the matching
existing theme; select it through **Themes**. Dropdown always follows the selected
global theme instead of changing it when opened.

- Left-click a module to toggle it; right-click to expand its settings. Expanded
  columns widen to keep the existing PanelGUI controls readable.
- Drag a category header; right-click the header to collapse the category.
- Scroll inside a category to move only that column. Small GUI sizes wrap columns.
- Middle-click a module, or focus it and press **K**, to assign its key. Escape
  cancels; Delete/Backspace removes the binding. Shared bindings are reported.
- **/** or **Ctrl+F** searches module names and descriptions across all categories.
  Escape first clears the search, then leaves the search field.
- **Tab / Shift+Tab** and **Up / Down** navigate modules and settings; Enter/Space
  toggles, Right/Left opens/closes module settings. Shared number fields support
  exact input, arrow adjustment and Escape cancellation.
- **Themes / Ctrl+T** opens the existing theme editor. **Reset / Home** resets
  column positions without changing module settings.

The variant uses `ArrayListSurface.withScene` to capture/blur one clean scene shared
by its disconnected columns. It inherits global surface material, opacity,
Gaussian/Kawase blur policy, glass, border, glow, roundness, shared gradient/motion
settings and reduced motion. Heading Glow uses the existing `TextEffects` path.
Disabled heading glow draws text directly without additional glyph-mask passes;
gradient OFF and Solid also disable the category-wide enabled-row ramp.
Checkboxes, sliders, dropdown choices, visibility and value updates are the same
production components as PanelGUI. GUI close cancels transient input and releases
owned scene/font-effect resources.

`me.youngjibbit.clickgui.DropdownGuiRenderCheck` is a standalone Java-8 Pbuffer/FBO
consumer check. It uses production controls, bundled fonts and a synthetic backdrop;
it does not start a world, log into an account or establish native Display/gameplay
acceptance. Run from a disposable subdirectory of `target` with `target/test-classes`, `target/classes`, plus
the Maven test dependency classpath and `-Djava.library.path=<project>/test_natives/windows`.
It writes `dropdown-preview/*.png` for visual review. The existing PanelGUI checks
remain required for the shared settings-renderer extraction.
