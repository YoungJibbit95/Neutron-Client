# Unified development snapshot — 2026-10-26

The integrated production and test source is based on local commit
`087d15945000f44a44fec11d5bb639963f83ad1e` on
`codex/revived-final-integration`. This report adds no production changes.

## Publication

The selected repository is `YoungJibbit95/Pulsar-Client`. Its existing `main`
history differs from the local development history. The PR therefore publishes
the integrated source snapshot on top of that repository's `main`, preserving
its history without uploading the separate local history of personal runtime
files. Source/build subtrees are compared by Git object IDs before publication.

The first PR commit removes the previously tracked `test_run` directory from
the published tree and ignores local runtime/build/IDE state. It does not delete
the owner's local game files and does not purge the remote repository's old
history. The following source commit contains the unified implementation.

## Latest corrections

- Shared Global/Local theme resolution and animation phase across surfaces,
  borders and text; explicit gradient/accent border selection; ArmorHUD RGBA.
- In-world menus retain their dimmed world background. Bloom passes restore
  consistent blending instead of producing opaque oversized halos.
- Microsoft browser authentication through an owned public desktop app,
  authorization-code/PKCE, validated loopback callback and stale-result guards.
  Direct login is session-only; remembered browser accounts store profile
  metadata, not tokens. See [setup](microsoft-browser-login.md).
- Twitch initialization is disabled through the existing compatibility API.
  SLF4J routes to Log4j; JNA and OSHI dependencies work in both packaged JARs.
- Formatted-text allocation/Unicode color correction, invalid skin-cache retry,
  and renderer/particle cleanup when leaving a world. See the
  [Minecraft source audit](minecraft-source-audit.md).

Earlier integrated work includes configuration persistence, shared materials,
HUD/GUI repair, account coordination, rotation/movement ownership, Scaffold,
Aura/AimAssist, bounded packet handling, Backtrack/LagRange/Tickbase, inventory
fixes and AutoHeal's separately guarded inventory-transfer option. The audit
reports retain the individual evidence and limits.

## Validation and remaining limits

Temurin 8u482 and Maven 3.9.15 were used. The original integration completed
`mvn -o clean package` and the publication worktree completed `mvn -o clean test`
with native GL, world-overlay and chat checks enabled. Each suite passed
**387 tests with no failures, errors or skipped tests**. Isolated child-JVM
assertions supplement those parent test counts.

Twelve additional fresh-JVM checks passed: iOS input ownership, GlowESP resource
ownership, ClickGUI, Arraylist, combined HUDs, resource lifetime, shared surface
consumers, motion preferences, native menu composition, theme synchronization and
pause-menu masks, account UI, and native nametag labels (six child assertions).
All used offscreen contexts; the theme/pause check used a synthetic checkerboard
background rather than a running game world.

Both assembly and minimized JARs passed isolated CPU/JNA, logging binding and
Log4j formatting checks without Maven's dependency classpath. This is dependency
validation, not standalone launcher certification.

The final local preservation check matched all 149 pre-existing modified/deleted
paths against their recorded hashes. A literal-secret scan of 4,419 publication
text files found no candidates; this is not a complete security audit.

The reported chat-typing crash remains **unreproduced**. Real GUI input, drawing,
history, completion and embedded-transport send tests passed; no fresh user crash
trace was available. The confirmed Minecraft fixes are not presented as proof
that this crash is resolved.

Microsoft login still requires the owner's registered Client ID and verification
of any applicable provider access requirements. Tests use synthetic providers
and local callbacks, not a personal account. No personal game session was started
or stopped. Offscreen Pbuffer/FBO checks do not establish native-window,
shaderpack, live-server or real-login behavior.

The standalone launch/asset distribution contract, fresh-machine dependency
bootstrap, remaining timer ownership and inactive legacy-option decisions, and
broader performance profiling remain follow-up work. See the audit reports;
this integration is not a declaration that every module or event path is done.
