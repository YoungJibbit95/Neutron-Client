# Audit remediation — wave 1, 2026-10-26

Audit baseline: `dec87d9d7091251ab108ba232f8ce62078816252`. Implementation baseline: `ec2e0157dbeae0f0f385e919b536015b56d23db7`, including the later glass and ClickGUI work. The initially affected module/iOS/GlowESP files were unchanged between those baselines. Branch: `codex/audit-wave-1`. Verified source integration: `3ce33d8544c52f133dd2512b5d9fe301552c16fe`.

Original findings and package boundaries remain in [the independent audit](F:/Pulsar-Recovery/analysis/Pulsar-Audit_2026-10-26_dec87d9d/AUDIT.md). This wave addresses **six of its eighteen findings**, plus the separately requested inventory-transfer feature. It does not declare the entire client or the remaining twelve findings complete.

## Changes and evidence

| Audit ID | Change | Commit | Verification |
|---|---|---|---|
| MOD-05 | Rank the original enchanted pickaxe stack; prefer remaining durability on an efficiency tie. Recheck destructive inventory decisions against the current stack and competitors after inventory-open callbacks. | `93df3ca5d` | Incorrect plain/Efficiency-V selection reproduced before the fix; both slot orders, durability, changed/missing competing stacks and observed controller drop targets checked afterward. |
| MOD-03, MOD-04 | Null-safe healing-item selection, hotbar indices 0–8, selected-stack guarded use, conditional slot restoration, monotonic cooldown and context invalidation. Remove per-use sleeping threads. | `3b0331fe7` | Original water-bottle NPE reproduced separately; slot boundaries, unsuitable items, canceled/replaced sends, manual selection, item/context loss and disable/re-enable checked. |
| MOD-01 | Speed's direct direction writers use the accepted movement basis. Keep raw-input/event helpers separate to avoid a second transform. | `133258cec` | Three pre-fix direction failures; all five retained checks cover both silent owners, directional inputs, ground/air writers, all MoveFix modes, pending versus accepted rotation and fallback. Speed/timer magnitudes and camera behavior unchanged. |
| UI-01 | Cancel transient iOS header/child input on close and init without resetting settings or categories. | `f626ec712` | Native synthetic lifecycle probe fails before and passes after; repeated close/reinit, motion settings, no-gesture reopen and fresh capture checked. |
| UI-02 | Release Gaussian and Kawase ownership without losing the separate Gaussian buffer or deleting the shared list/output alias twice. | `5ffb722b2` | Twenty native transitions, both resize paths and repeated disable. Retired FBO/texture/depth deletion is inspected before the driver can reuse object IDs; active resources remain valid. |

## Separately requested AutoHeal inventory transfers

The user explicitly selected inventory transfers in addition to the hotbar fix. Controller seam `fc53e85fa` and feature `bf9deadf7` are separate commits. Existing vanilla `windowClick` remains unchanged; the new guarded entry validates before prediction and again after packet PRE listeners immediately before transport.

Enable **AutoHeal → Inventory Transfers** to use this feature. It defaults off to preserve existing profiles. Hotbar items take priority. The player-inventory transfer searches slots 9–35 and uses a nonselected hotbar slot, preferring an empty one. A full-hotbar mode-2 swap retains the displaced item in the source slot; it does not drop an item.

Only a matching, applied transaction ACK with a still-valid prediction permits subsequent use on a client tick. The protocol ACK is not independent proof of server stack identity or healing. Canceled transport restores only an untouched, owned local prediction. Conflicts, rejection or a five-second confirmation timeout stop automatic use and expose a waiting status. Uncertain accepted transfers require their matching terminal ACK followed by an authoritative full inventory resync; a pre-ACK inventory snapshot cannot substitute for that sequence. If the server never provides the required evidence, recovery waits for resync or a world/connection change rather than guessing or retrying the uncertain transaction.

At most one pending observer tracks transaction completion even while AutoHeal is disabled. It performs no healing action while disabled and detaches on resolution or context/connection cleanup. Tests include ordinary accepted and rejected completion while disabled, manual/slot changes, wrong/canceled ACKs, timeout, pre-ACK stale full inventory, reentrant unload/resync during outgoing PRE, and rejection before local prediction.

Supported healing selection remains **splash Instant Health II or higher** and **mushroom stew** in Soup mode. Soup requires server-side soup-healing behavior; vanilla instant healing is not implied. This change adds no automatic downward aiming or new rotation mode, and outgoing use acceptance is not proof of a server heal.

## Combined validation

Temurin 8u482 / Maven 3.9.15. Production and tests were freshly compiled with `mvn -o clean test` and native GL testing enabled. That first combined run exposed one later Scaffold-test NPE after the new Speed fixture. The small subset did not reproduce it deterministically; the shared static-final client mutation/JIT hazard was isolated using the project's existing child-JVM pattern. Commit `644ec4541` moves all five Speed assertions unchanged into their own JVM; no production Scaffold workaround was added.

The final `mvn -o test` run passed **358 parent tests, zero failures/errors/skips**. Five child JVM groups passed **37 checks** (4 + 19 + 5 + 7 + 2); their wrappers are already counted among the 358 parent tests. The final test sources were recompiled, while unchanged production classes came from the preceding clean build.

Nine standalone native programs passed: iOS capture, GlowESP ownership, main ClickGUI, Arraylist, combined HUDs, shared resources, material consumers, motion and native surfaces. No exception/error reports appeared in these render logs. Native Pbuffer/FBO and synthetic packet/controller fixtures do not establish actual world rendering, native window/fullscreen behavior, shaderpack compatibility, server healing or a release-JAR launch.

Evidence: `F:/Pulsar-Recovery/analysis/audit-wave-1-build.log` (initial failure), `audit-wave-1-final-tests.log` (final success), and project `target/audit-wave-1-validation/results.json` plus individual logs. Focused iOS/GlowESP before/after logs are versioned under `src/test/resources/audit-w1-ui`.

The original 149 foreign changed paths stayed outside these commits. **148 remain byte-identical** to the start manifest. `test_run/usercache.json` was updated during a parallel client run and was deliberately left untouched; it was not restored to the older manifest. No personal game session, login or push was initiated by the integration work; the running client was left alone.

## Remaining work and manual acceptance

Still open: MOD-02/06 (Sprint/NoSlow consumers), MOD-07/08 (timer ownership and inactive options), UI-03/04 (accent policy and Armor motion/alpha), BLD-01/02 (release entry and JNA), MC-01/02/03 (world-render hook, labels and renderer reload), PERF-01 (per-primitive GL snapshots). Dead-code cleanup and larger utility migrations remain separate packages.

Choose the release format before BLD-01/02, the accent precedence before UI-03, and the intended disposition of inactive options before MOD-08. Existing effects and unrelated module behavior remain required.

Manual acceptance should use a disposable test profile/world: compare plain and enchanted pickaxes; exercise AutoHeal with a suitable hotbar item, then enable Inventory Transfers and test empty/full hotbars and a manual slot change; compare Speed with accepted Aura/Scaffold movement; interrupt an iOS drag and reopen; switch GlowESP algorithms and resize. Soup/server behavior and real healing require their appropriate environment. No such gameplay acceptance is claimed here.
