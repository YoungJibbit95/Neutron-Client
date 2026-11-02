# Lazy font atlases

`TTFFontRenderer` keeps the existing font API and bundled font assets. Font lookup
and width/line-height measurement are CPU-only; drawing and disposal belong to
the owning render thread. Glyphs are rasterized on first use into padded,
linearly filtered texture atlases, with separate regular, bold, italic and
bold-italic styles. A warmed string submits one quad batch per consecutive atlas
run; its shadow adds a second pass. No texture is allocated per character.
Glyph images reserve eight raster pixels of padding on each side for the optional
halo. Without a halo, submitted quads crop six of those pixels on each side,
retaining a filtering margin without drawing the entire transparent border.

Unicode code points use the selected face where available, otherwise Java's
logical Dialog fallback. This is not a complex-script shaping engine. Metrics
and drawing share advances and formatting rules. Newlines, tabs, color/reset,
underline, strike and combined bold/italic are supported. Underlined spaces
retain their decoration. A global threshold of 8,192 measured glyphs, plus up to
four style fallback records, and eight atlas pages per style bound cache growth;
every used style reserves a visible fallback.
Closing the renderer releases its texture ownership exactly once. ArrayList,
SessionInfo and PanelUtils draw complete strings using the renderer's advances;
they do not compress spacing with per-character width adjustments. ArrayList
measurement, sorting, clipping and drawing use the same label metrics.

## Family selection and text glow

Client settings exposes `UI Font`: Original, DM Sans, Inter, Poppins, Product
Sans, SF Pro and Tenorite. Original preserves each caller's requested face.
Other choices remap recognized text faces while retaining the requested size
and available weight; Tenorite maps Medium to Regular because only Regular and
Bold are bundled. The existing SF Pro keys use the bundled SF Pro Rounded files.
Icon faces (`icon`, `icon1`, `iOSIcons`) retain their own font and do not receive
the atlas text halo. `getLocalFont` bypasses global family remapping. ArrayList's
explicit font choices use that path; its new `Follow global` option, the default
for a new instance, inherits `UI Font` and retains `Font Strength`. Existing
explicit choices remain valid, including Minecraft's separate renderer.

`Text Glow` defaults to `Module settings`, preserving existing module glow
choices. `Off` suppresses text glow; `On` applies the shared text settings to
eligible custom-font draws and existing whole-string text effects. Strength
ranges from 0 to 1 (default 0.45), radius from 0.5 to 3 GUI units (default 1.5),
and color is `Text` (default) or `Theme`. Global Glow is the master switch;
its intensity also scales text glow. These controls are appended to the existing
ClientSettings Values and persist through the existing configuration system.
Importing an older configuration restores defaults for missing typography keys.

`FontInk` shades the existing glyph atlas directly for sharp themed text and its
optional halo. Ordinary custom text adds no per-label framebuffer or blur pass.
Existing `TextEffects.draw`/`drawLocal` whole-string masks retain their reusable
framebuffer and blur path, with the shared glow override applied there. Capture
suppresses the atlas halo to avoid applying it twice. Cache bounds, render-thread
ownership and font disposal remain unchanged; shared shader resources are released
through `TextEffects.releaseResources`.

Raw OpenGL state changes during warmed drawing are scoped to preserve the
caller's active texture unit, binding, blend and color state. Cold uploads use
the existing complete `RenderState` guard. Text-effect alpha capture continues
to use the shared compositing convention. Atlas glow requires shader support
and leaves an unknown active caller shader untouched, so it is not guaranteed
inside arbitrary shader pipelines. Vanilla Minecraft text does not use this
atlas shader.

## Checks

- `FontManagerTest` and `FontAtlasMetricsTest`: lazy lookup, file lifetime,
  Unicode/format metrics, worker-thread metrics and closed lifecycle.
- `TypographySettingsTest`: family/size/weight mapping, icon exclusions,
  explicit local fonts, configuration round-trip and older-config defaults.
- `TypographyRenderCheck`: native glyph/halo rendering and resource/state checks.
- `FontResourcesCheck` through `SessionInfoRenderCheck` with
  `-Dpulsar.fonts.check=true`: actual Pbuffer rasterization, style laziness,
  deterministic pixels, warm batches/uploads, caller GL state, disposal/reopen.
- `DropdownGuiRenderCheck` and `ClickGuiRenderCheck`: production fonts in the
  existing controls, clipping, themes and shader composition.

These are isolated Pbuffer checks with synthetic data, not a full native Display,
world, fullscreen or shader-pack acceptance test. Batch/texture counts are
resource measurements, not an FPS claim.
The typography/settings captures and logs for the 2026-11-02 refinement are local
artifacts under `target/typography-ui-20261102`; they are not committed assets.
Final suite results and the remaining live acceptance gates belong in the PR.
