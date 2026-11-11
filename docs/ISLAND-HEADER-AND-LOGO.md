# Island header and original logo

## Connected detail sheets

Notifications and TargetHUD retain the full 34 logical pixel header. The neck of the
connected lower sheet starts at the header's lower edge. Previously it started seven
pixels earlier, while the logo badge and modern clock still used the full header canvas.
That made the border cross behind the bottom of those elements.

The change retains the existing single silhouette, anchored top position, compact lower
sheet, independent outside widgets and spring animation. It adds neither a second
background nor a separate border around the lower sheet. Geometry regression coverage
checks the actual badge and clock circles across both base designs, inside-widget counts,
three scales, three attached states and opening phases.

## Original image and shared theme ink

`assets/minecraft/pulsar/Logo1.png` is unchanged. `ThemeLogoRenderer` samples the white
source texture and its existing alpha halo in a single quad. Sampling the complete PNG
at the previous source-to-screen scale preserves stroke placement and exposes the
surrounding falloff that the old tight crop clipped. The faint halo reaches the image's
transparent margin without a new rectangular crop. It does not substitute a font glyph
or create a blurred copy.
The Island's existing outer content clip still bounds the final draw; it can trim a
very faint part of the outermost falloff. The full-PNG sampling description does not
claim that every nontransparent source pixel appears outside that clip.

The GLSL 1.20 fragment uses the same `UiEffects` palette, direction, channel settings and
motion clock as themed text. Stroke color follows TEXT; the outer falloff follows GLOW.
Flow, Ping-Pong, Pulse and Perimeter Orbit therefore use the common controls. Global
Glow, the Watermark's resolved local Glow setting, theme strength and global intensity control the
original halo. Reduced/disabled motion freezes the
shared phase. The existing badge plate and its design-specific radius are preserved.

One shader program is cached per renderer, rebuilt after resource reload and closed on
Watermark disable or hidden HUD. Rendering uses no extra framebuffer, offscreen pass or
blur. Caller GL state is restored. Unsupported shaders fall back to the original tinted
texture; in that fallback the baked halo is part of the texture and cannot be separated
from the stroke.

## Validation entry points

- `IslandAttachmentLayoutTest`: complete header containment during attached states.
- `IslandCompactRenderCheck`: real modern clock with notification and TargetHUD painters.
- `ThemeLogoRenderCheck`: original PNG, halo beyond the old quad, palette direction,
  four animation modes, reduced motion, resource reload, shader fallback, restored GL
  state and zero new GPU resources across 120 warmed draws.

The latter two run in the existing isolated native session suite and export their actual
Pbuffer captures. They validate rendering behavior, not live multiplayer gameplay or
hardware-specific frame rates.
