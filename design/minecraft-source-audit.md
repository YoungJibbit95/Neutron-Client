# Minecraft client source audit — 2026-10-26

Reviewed from `4d7564a5b535ece8c37ea247b0e5c33bacf66096`, with the earlier
`dec87d9d` audit used to exclude already fixed renderer/nametag findings.
This is a risk-directed client review, not an exhaustive review of every
Minecraft/OptiFine source line. No server physics or gameplay optimization is included.

## Reported chat crash: still unreproduced

No fresh exception identifying the user's typing/chat crash was available.
Unchanged production code passed the real GUI input/draw/send checks described
below. The three confirmed fixes are independently evidenced bugs; none is a
proven explanation or fix for that unspecified crash.

`GuiChatInputTest` launches an isolated JVM with a real offscreen Pbuffer,
production shaders, default resources and the actual `GuiChat`, `GuiTextField`,
`GuiNewChat` and vanilla font. It covers ordinary/accented/CJK/Cyrillic characters,
surrogate input, control/section-sign rejection, selection/modifiers, cursor and
backspace, 100-character limiting and horizontal scrolling, history, completion
responses, received-line scrolling/refresh, and the actual
`GuiScreen.sendChatMessage → EntityPlayerSP → NetworkManager` path with an embedded
transport. Safe `.`, `.help`, `.help help`, `.unknown`, `.bind` and `.config`
dispatch also pass without sending local commands to the transport.

Limits: synthetic key state rather than OS keyboard events; no desktop clipboard
mutation, actual Enter-driven screen teardown, public server, full running world,
shader pack, user resource pack, or exhaustive module/configuration combinations.
The user's game process was not launched, stopped or modified. Fixture setup
failures were corrected as test issues and are not reported as product crashes.

## Implemented findings

### P2 — Whole-string case folding in FontRenderer

`FontRenderer.renderStringAtPos` folded the entire string once for every `§`
format token, in both shadow and foreground passes. Besides quadratic scanning
and allocation, Unicode U+0130 expands when lowercased, shifting the subsequent
format-code index. Rendering `"\u0130\u00a7cX"` therefore selected white instead of
red. Native GL color assertions reproduce that error before the change and pass
afterward, including uppercase/lowercase ASCII code controls.

The fix lowercases only the format-code character. Actual production
`drawStringWithShadow` allocation samples on Java 8, measured over 100 warmed
draws with ThreadMXBean:

| Characters / tokens | Before bytes per draw | After bytes per draw |
|---|---:|---:|
| 192 / 32 | 52,736 | 0 |
| 384 / 64 | 203,776 | 0 |
| 768 / 128 | 800,768 | 0 |

A later full lifecycle run measured 0 / 25 / 0 for the same cases. These are
specific warmed font samples, not a universal zero-allocation or FPS claim.
Formatted server/command text, names and scoreboards can benefit; ordinary typed
chat rejects section signs.

### P3 — Undecodable cached skins never retry

`ThreadDownloadImageData.loadTexture` handled ImageIO exceptions, but ImageIO can
also return null for an invalid cache without throwing. That path skipped the
download and left the texture unavailable. The fix routes null decode through
the existing IOException fallback.

`ThreadDownloadImageDataTest` proves the behavior against a loopback HTTP server:
an invalid four-byte cache starts exactly one download, loads a valid PNG, invokes
the availability callback once, and replaces the cache. The retry test fails
before the fix because no worker starts. Its valid-cache control makes zero
requests. No external endpoint or account is used.

### P2 — Null-world exit skips renderer and particle cleanup

`Minecraft.loadWorld(null)` cleared its world/player fields but called
`RenderGlobal.setWorldAndLoadRenderers` and `EffectRenderer.clearEffects` only
when the incoming world was non-null. The menu retained the previous renderer
world/frustum and particle world. This is last-world retention, not a demonstrated
unbounded leak across joins.

The existing consumer calls now also receive null on exit, in the same order.
Optional renderer/achievement/pack/overlay/audio/save-loader accesses are guarded
for partial bootstrap and failure recovery. Existing shutdown, freeMemory,
connecting, exit and disconnect callers retain their world-event ordering.

`WorldUnloadNativeCheck`, called by the native GUI test, executes actual
`Minecraft.loadWorld`, `RenderGlobal` cleanup and `EffectRenderer` cleanup. It
requires world-access removal exactly once, null renderer/frustum references,
deletion of a real ViewFrustum's 16 chunks/64 allocated VBOs, and cleared
particle/emitter/world references. It then loads the next synthetic world through
the actual Minecraft method and exits again. The old code fails the retained-world
assertion. Rejoin chunk-rebuild scheduling is a controlled adapter; this does not
claim full client/server or shader-pack lifecycle coverage.

`MinecraftWorldLifecycleTest` separately checks unloading before optional services
exist and independently present renderer/effect consumers. Both cases reproduce
the previous missing-renderer NPE before the guards.

## Reproduce the focused checks

Use Java 8 and the repository's Windows natives. From the repository root in
PowerShell:

```powershell
mvn -o -q "-Dtest=GuiChatInputTest,ThreadDownloadImageDataTest,MinecraftWorldLifecycleTest" "-Dpulsar.chat.native=true" "-Dorg.lwjgl.librarypath=$PWD/test_natives/windows" test
```

Verified: five tests, zero failures, zero errors and zero skipped tests with the
native flag enabled. The native check is opt-in; omitting
`pulsar.chat.native=true` skips it and does not verify GUI/VBO behavior.

## Reviewed boundaries and remaining work

Read targeted bootstrap/input/frame/world-switching paths; chat GUI/font and
command dispatch; renderer GUI hooks; texture/skin/resource ownership;
RenderGlobal/frustum/chunk-dispatch cancellation; particle cleanup and relevant
HUD drag consumers. Shared UI material consistency is reviewed separately.

The user's original chat crash remains open pending its actual exception or a
reproducible configuration. Full world/shader-pack/resource-reload interaction,
inactive shader visibility caches, and texture-download timeout/publication
behavior remain outside these bounded fixes. No further unvalidated finding is
claimed. A suspected normal rebuild stop deadlock was rejected after tracing
frustum deletion and compile-task cancellation before dispatcher stop.
