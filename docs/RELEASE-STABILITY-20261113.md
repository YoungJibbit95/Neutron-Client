# Release stability — 2026-11-13

UI, configuration and JavaFX sign-in patch notes. The associated pull request
records final check results and native image evidence.

## Scope

This patch changes presentation, configuration compatibility and callback parsing.
Gameplay, packet policies and Aura rotations remain unchanged. No release tag was published.

## Configuration and startup

Source: [ConfigManager](../src/main/java/me/youngjibbit/config/ConfigManager.java).

- Legacy `settings` objects were not read as setting collections; typed `value`
  wrappers were handled inconsistently. Both now use the existing value validators.
- Invalid known values still reject an import before applying any changes.
- Selected linkage/initialization errors could escape runtime-exception handling.
  Restore, application and deferred activation now isolate these errors;
  fatal VM errors still propagate.
- Automatic saving pauses after a failed application, preserving the source file.
  Failed deferred activation is removed instead of being retried every tick.
- Cleanup stops incomplete changes and clears their subscriptions, including a
  failed disable hook that already set its enabled flag to false.
  Untouched modules and already-successful changes retain their state.
- Original Alpha config/launcher stacktrace: **not supplied**; that crash is **not reproduced**. Synthetic cases cover only the faults above.

## Custom HUD modules

| Module | ID | Disabled behavior |
| --- | --- | --- |
| Custom Scoreboard | `custom_scoreboard` | Native Minecraft sidebar |
| Custom Hotbar | `custom_hotbar` | Native Minecraft hotbar |

Both designs were previously unconditional. Fresh profiles and legacy profiles
missing these two entries keep them enabled. An explicit saved `false` wins;
this migration does not enable other missing modules.

The old sidebar measured native-font text but drew custom-font text. The custom
renderer uses matching fonts, separate columns, bounded text and viewport-aware
sizing. Hidden entries are filtered before selecting 15 rows. Chat/HUD editor
dragging, saved anchors, reset and an empty-sidebar preview are supported. See [Custom HUD](CUSTOM-HUD.md).

The old 24-pixel hotbar selector overlapped 20-pixel slots. The selector now fits
its slot, with render-time selection and item-switch feedback. Game selection
changes immediately. Animation/reduced-motion settings apply; disable and world
changes reset motion and captured-scene resources.

## Entity rendering and TargetESP

The normal living-entity layer call was commented out. Armor and held-item layers
are restored in the scene pass; the GlowESP mask copy does not replace that pass.
Both paths retain the spectator-layer guard.

TargetESP separates observed hitpoint coordinates from their rendered position.
A passive time-based filter smooths marker and halo presentation. Source/target or hit/miss
changes, large jumps and pauses clear old motion; repeated passes share one frame
position. Zero smoothing or reduced motion shows the sample directly. The halo
and marker have clearer cores, rims and default dimensions. The filter changes
no targeting, raycasts, rotations or network data. See [TargetESP lifecycle](TARGETESP-LIFECYCLE.md).

## Live diagnostics and notifications

Activity suffixes sample each observed module independently every 50 ms and
refresh on toggle changes. Labels include `Idle`, `Hold 80 ms`, `Ready 3 ticks`
and `Step +1`, paired with animated state icons. Width caches include text/icons;
reduced motion freezes decorative animation.

Diagnostics refresh current values every 100 ms; history remains at 500 ms.
Plot callbacks read current presentation values/time rather than stale captures.
Correction notices show `Server correction`, movement/angle and `Cause unknown`;
they do not claim a confirmed server flag.

Keyed notices replace their latest facts in the toast/Island timeline. Updates
during cooldown change an existing notice without creating or extending one.
An unmatched update cannot evict another queued notice. Filtering and bounded
notification lifetime remain in force.

## JavaFX Microsoft callback

OpenAuth 1.1.6 forwards the full desktop callback URL. The parser rejected every
query, including Microsoft's documented numeric locale (`?lc=1033#...`).
Only optional `lc=[0-9]{1,10}` is now accepted. Exact HTTPS origin/path, state,
bearer-token and duplicate-field checks remain enforced; query credentials are
rejected. Session handoff and the separate browser-flow PR #183 are unchanged.
Evidence: [OpenAuth LoginFrame](https://github.com/Litarvan/OpenAuth/blob/fd5d5cd954459020424b8df368acc8f5e272c683/src/main/java/fr/litarvan/openauth/microsoft/LoginFrame.java) and [Microsoft desktop callback](https://learn.microsoft.com/en-us/archive/msdn-magazine/2016/january/windows-10-using-the-onedrive-rest-api-in-a-windows-10-app).

## Removed obsolete paths

Removed the unused GlowESP `renderGlint` flag, redundant mask guards, commented debug compositor
and GuiIngame's duplicate HUD surfaces/theme helpers.
GuiIngame's commented native hotbar is now the active fallback.
Startup also drops an unused scheduled executor/accessor, an unused Pulsar-owned
PanelUtils instance and the obsolete MinecraftAccounts directory creation.
Repository-wide call-site and reflection searches found no consumers. The actual
account store remains owned by AccountManager under the configured client/alts path.

## Validation and outstanding acceptance

- Java 8 regression coverage includes config/auth, HUD motion/layout, suffix,
  notification, diagnostics and TargetESP lifecycle/filter behavior.
- Windows CI and native checks must complete before merge. Native coverage includes
  fonts/items, GUI scales, fallback, armor/held layers, reduced motion, GL/FBO
  restoration and GPU-resource reuse; results are recorded in the pull request.
- **Not performed:** real Microsoft account sign-in in JavaFX on Windows.
- **Not reproduced:** the original Alpha-to-0.3.0 launcher crash described above.
