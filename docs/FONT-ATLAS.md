# Lazy font atlases

`TTFFontRenderer` keeps the existing font API and bundled font assets. Font lookup
and width/line-height measurement are CPU-only; drawing and disposal belong to
the owning render thread. Glyphs are rasterized on first use into padded,
linearly filtered texture atlases, with separate regular, bold, italic and
bold-italic styles. A warmed string submits one quad batch per consecutive atlas
run; its shadow adds a second pass. No texture is allocated per character.

Unicode code points use the selected face where available, otherwise Java's
logical Dialog fallback. This is not a complex-script shaping engine. Metrics
and drawing share advances and formatting rules. Newlines, tabs, color/reset,
underline, strike and combined bold/italic are supported. Underlined spaces
retain their decoration. A global threshold of 8,192 measured glyphs, plus up to
four style fallback records, and eight atlas pages per style bound cache growth;
every used style reserves a visible fallback.
Closing the renderer releases its texture ownership exactly once.

Raw OpenGL state changes during warmed drawing are scoped to preserve the
caller's active texture unit, binding, blend and color state. Cold uploads use
the existing complete `RenderState` guard. Text-effect alpha capture continues
to use the shared compositing convention. No new theme or effect settings are
introduced.

## Checks

- `FontManagerTest` and `FontAtlasMetricsTest`: lazy lookup, file lifetime,
  Unicode/format metrics, worker-thread metrics and closed lifecycle.
- `FontResourcesCheck` through `SessionInfoRenderCheck` with
  `-Dpulsar.fonts.check=true`: actual Pbuffer rasterization, style laziness,
  deterministic pixels, warm batches/uploads, caller GL state, disposal/reopen.
- `DropdownGuiRenderCheck` and `ClickGuiRenderCheck`: production fonts in the
  existing controls, clipping, themes and shader composition.

These are isolated Pbuffer checks with synthetic data, not a full native Display,
world, fullscreen or shader-pack acceptance test. Batch/texture counts are
resource measurements, not an FPS claim.
