# WAVE10 — Frozen Target Island composition

Base: PR161 ff9cacd3647916600a736da348c4bb9fc46499f3. Original Watermark blob: bdb3cf14a0ed96189828de26355f28887f571058. A later integration base must retain this original blob.

## Actual consumer and data

Watermark captures IslandTargetComposition from the existing immutable IslandPresentation before deferred rendering. It uses only sanitized name, exact preformatted HP/maximum/absorption text and existing health/absorption fractions. No target lookup, entity/skin access, provider, timer, framebuffer or Value is added.

The TARGET consumer now has its own 266×52 logical canvas: reticle, actual health arc, separate absorption arc when present, name/HP text, and independent health/absorption rails. The old common N-badge/generic target branch is removed. Sweep/rail widths equal frozen fractions. Absorption beyond one maximum-health unit follows the existing capped fraction; the observed amount remains in the copied text.

## Bounds, motion and lifetime

The complete canvas fits inside the current outer frame clip, including 1px viewports and intermediate morphs. Text respects width and measured-font height slots. One existing UiEffects time/motion capture is shared with Scaffold. Motion affects only the reticle; Reduced Motion/animations off remain static.

Selection, priorities, source loss and epoch/world/player fences remain unchanged. Only Idle/Clock may be held for crossfade. Missing/hidden/interrupted targets do not retain a composition. Existing RenderState scopes own all added GL drawing. There is no additional resource owner.

## Evidence and remaining gates

Five IslandTargetCompositionTest cases cover actual frozen facts, exact zero/quarter/full sweeps, source/priority withdrawal, static/moving policy and complete current-clip bounds.

Actual pure model plus existing actual Island classes: 26,506 standalone assertions PASS under OpenJDK21 source/target8. Three Java files parse with Java8 grammar. Exact four-file patch application is verified. This is not Java8 JUnit or native rendering evidence. Repository Java8 and native appearance/GL/font/screenshot gates remain NOT_RUN for this delta. Four Approach-2 reference boards remain unavailable.

Vectors are original geometry; no new asset, font, copied shader or third-party license.

