# Release stability — 2026-11-13

UI, configuration and JavaFX sign-in patch notes. The associated pull request
records final check results and native image evidence.

## Scope

This patch changes presentation, configuration compatibility and callback parsing.
Gameplay, packet policies and Aura rotations remain unchanged. No release tag was published.

The current release request contains exactly these eight items. Items 1, 4 and 6
are handled separately and are not acceptance requirements for this patch.

| Item | Implemented area | Principal regression evidence |
| --- | --- | --- |
| 2 | Recover compatible legacy settings and isolate failed modules | Config suites, mixed valid/invalid fixtures, automatic-file preservation and deferred activation |
| 3 | Keep armor and held-item layers alongside GlowESP | Real living-layer pixels, four Glow modes, GUI scales 1/3, state/resource checks |
| 5 | Current diagnostics, suffixes and keyed status notices | Sampling, fake-clock transitions, cooldown, card/Island identity and occurrence-count tests |
| 7 | JavaFX callback, decline/retry and account/session handoff | Embedded callback through local service fixtures and client-thread installation; native JavaFX listener probe |
| 8 | Scoreboard layout, editor position and module fallback | Font/layout/drag/config tests and native hotbar-clearance pixels across viewport/module scales |
| 9 | Hotbar selection/item motion and module fallback | Rapid slot changes, reduced motion, actual items and native fallback checks |
| 10 | Hitpoint/halo display interpolation and clarity | Target/context resets, F1/two-eye frame identity, low-FPS motion and native overlay pixels |
| 11 | Proven unused work and bootstrap allocations | Reference review, fallback/canonical service identity and existing startup/resource tests |

## 2. Configuration and startup

Source: [ConfigManager](../src/main/java/me/youngjibbit/config/ConfigManager.java).

- Legacy `settings` objects were not read as setting collections; typed `value`
  wrappers were handled inconsistently. Both now use the existing value validators.
- Loading now recovers individual invalid known values with their declared defaults,
  including booleans, numeric bounds, enums, strings and colors. Valid neighboring
  settings still apply. Invalid toggle/key metadata falls back to disabled/unbound.
  New authored saves continue to validate strictly.
- Existing module/setting aliases and targeted migrations are retained. Unknown
  entries are reported; malformed legacy-array elements and unsupported individual
  module entries are isolated. Versioned legacy arrays are canonicalized before a
  later automatic save, retaining unknown settings. Repeated unknown array names
  pause automatic saving so canonicalization cannot silently discard an opaque value.
- Structurally invalid JSON, conflicting duplicate identities and unsupported
  top-level schema versions still fail safely without changing the source file.
- Selected linkage/initialization errors could escape runtime-exception handling.
  Restore, application and deferred activation now isolate these errors;
  fatal VM errors still propagate.
- Automatic saving pauses after lossy recovery or a failed application, preserving
  the automatic source file byte for byte. A named save can store recovered settings.
  Compatibility diagnostics identify the module/setting and fallback, with bounded
  message count rather than a notification per damaged value.
  Failed deferred activation is removed instead of being retried every tick.
- Cleanup stops incomplete changes and clears their subscriptions, including a
  failed disable hook that already set its enabled flag to false.
  Other compatible modules continue through preparation, callbacks and activation,
  including healthy deferred activations after a startup callback fails. Failed
  modules do not leave stale subscriptions or retry every tick. Fatal VM errors are
  not converted into ordinary compatibility failures.
- Original Alpha config/launcher stacktrace: **not supplied**; that crash is **not reproduced**. Synthetic cases cover only the faults above.

## 8 and 9. Custom HUD modules

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
Automatic placement also leaves a gap above the actual hotbar and normal native
armor/air rows. Existing manual placement retains the available editor/screen
area; active dragging and final layout use consistent bounds. Native regression
compares hotbar pixels before and after the sidebar at four viewports and three
module scales, in addition to checking all fifteen scoreboard rows.

The old 24-pixel hotbar selector overlapped 20-pixel slots. The selector now fits
its slot, with render-time selection and item-switch feedback. Game selection
changes immediately. Animation/reduced-motion settings apply; disable and world
changes reset motion and captured-scene resources.

## 3 and 10. Entity rendering and TargetESP

The normal living-entity layer call was commented out. Armor and held-item layers
are restored in the scene pass; the GlowESP mask copy does not replace that pass.
Both paths retain the spectator-layer guard.

TargetESP separates observed hitpoint coordinates from their rendered position.
A passive time-based filter smooths marker and halo presentation. Source/target or hit/miss
changes, large jumps and pauses clear old motion; repeated passes share one frame
position. Zero smoothing or reduced motion shows the sample directly. The halo
and marker have clearer cores, rims and default dimensions. The filter changes
no targeting, raycasts, rotations or network data. See [TargetESP lifecycle](TARGETESP-LIFECYCLE.md).
Halo inertia also uses the game's render sequence, so an F1-frozen decoration
clock cannot freeze target-following motion, and two eyes share one frame sample.
Accepted frame deltas up to 250 ms are consumed with bounded spring substeps;
10 FPS and 100 FPS movement/settling tests protect against losing elapsed time.

## 5. Live diagnostics and notifications

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

The module-level Backtrack/LagRange **Diagnostics** notices now use the same
keyed presentation rules. Previously their two-second early return left old
facts on screen and each different verbose message could become another card.
Current short state text refreshes during cooldown; new interruptions are limited
to state transitions. Routine observations do not inflate the occurrence count,
and disable/world changes retire stale holding text. This only reads the existing
activity snapshots and changes notification presentation.

## 7. JavaFX Microsoft callback

OpenAuth 1.1.6 forwards the full desktop callback URL. The parser rejected every
query, including Microsoft's documented numeric locale (`?lc=1033#...`).
Only optional `lc=[0-9]{1,10}` is now accepted. Exact HTTPS origin/path, state,
bearer-token and duplicate-field checks remain enforced; query credentials are
rejected.

The resolved OpenAuth 1.1.6 adapter only completed its response when a location
contained an access token. Declined/error redirects could therefore wait until
timeout. Neutron now also observes terminal desktop redirects through JavaFX's
public `ObservableValue`/`ChangeListener` interfaces, while the callback parser
still validates the origin, state and token before any exchange. Async window
initialization failures also complete the attempt. Declines and failures receive
fixed recovery messages; provider details and credentials are not displayed.

The end-to-end fixture runs an embedded callback through disposable local
Xbox/Minecraft service responses and the existing `LoginCoordinator`. It checks
that the old session remains until the client queue is executed, the correct
profile/account is then installed and persisted, transient tokens are not stored
in the account record, and a declined attempt can retry using the same adapter.
The Windows JavaFX probe exercises the real listener classes and WebEngine
location property with local HTML and synthetic callbacks, without an account or
network request.

The shared session coordinator, browser PKCE service and account/UI routing are
unchanged. PR #183 deliberately removes the JavaFX adapter; a future merge must
resolve that delete/modify overlap according to the chosen browser migration.
Evidence: [OpenAuth LoginFrame](https://github.com/Litarvan/OpenAuth/blob/fd5d5cd954459020424b8df368acc8f5e272c683/src/main/java/fr/litarvan/openauth/microsoft/LoginFrame.java) and [Microsoft desktop callback](https://learn.microsoft.com/en-us/archive/msdn-magazine/2016/january/windows-10-using-the-onedrive-rest-api-in-a-windows-10-app).

## 11. Removed obsolete paths and startup work

Removed the unused GlowESP `renderGlint` flag, redundant mask guards, commented debug compositor
and GuiIngame's duplicate HUD surfaces/theme helpers.
GuiIngame's commented native hotbar is now the active fallback.
Startup also drops an unused scheduled executor/accessor, an unused Pulsar-owned
PanelUtils instance and the obsolete MinecraftAccounts directory creation.
Repository-wide call-site and reflection searches found no consumers. The actual
account store remains owned by AccountManager under the configured client/alts path.
Also removed GlowESP's write-only outline-color array/factory and unused color
locals. The array was allocated on each 2D frame without a consumer. Registration,
events, resource names and the relevant reflective framebuffer probes do not use
the removed state.

`RuntimeServices` now avoids constructing five fallback services when a canonical
`ClientRuntime` already exists. A single lazy holder preserves the original group
construction order, captured owner thread and singleton identities for genuine
prebootstrap calls; installing/replacing the real runtime does not reset the
fallbacks. This removes a redundant retained service graph, with no claimed
measurement of overall startup time or FPS.

Startup review also confirmed one runtime bootstrap/module scan, already-lazy
font loading, and deferred unselected resource-pack metadata/icons. The
`GuiAltManager` construction is retained because it installs the UI singleton.
Deferring every ClickGUI variant would need separate first-open measurements and
was not mixed into this stability patch.

## Validation and outstanding acceptance

- Java 8 regression coverage includes config/auth, HUD motion/layout, suffix,
  notification, diagnostics and TargetESP lifecycle/filter behavior.
- Local final focused run: Temurin 8u482-b08 / Maven 3.9.15, **365 tests,
  0 failures, 0 errors, 0 skipped**. Windows-only runtime tests are left to CI.
- Client-bound Diagnostics fixtures use the existing fresh-JVM test boundary:
  the shared fixture replaces a static-final Minecraft field with Unsafe, which
  a warmed JIT can otherwise retain. Their assertions and ordinary JIT stay enabled.
- Windows CI and native checks must complete before merge. Native coverage includes
  fonts/items, GUI scales, fallback, armor/held layers, reduced motion, GL/FBO
  restoration and GPU-resource reuse; results are recorded in the pull request.
- **Not performed:** real Microsoft account sign-in in JavaFX on Windows.
- **Not performed in this Linux workspace:** interactive official Minecraft
  Launcher/IntelliJ starts or synchronization of the user's Windows checkout.
  Windows packaged-runtime and native CI are separate from those manual checks.
- **Not reproduced:** the original Alpha-to-0.3.0 launcher crash described above.
