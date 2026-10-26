# Architecture cleanup — 2026-10-26

Source basis: `d056fead8632803322ae9531a5da715e1fc8224c` (includes the Main agent's completed AutoTool and isolated Scaffold test changes). Work happens on `codex/architecture-cleanup`; the original workspace and the other worktrees are not edited.

## Implemented

- **One settings owner:** `Module` owns its values. Removed `ValueStorage`, which duplicated those references globally without any remaining query consumers. Theme editor drafts no longer need global deregistration. Both values getters are read-only, and a module cannot adopt another module's setting.
- **One keybind source:** key dispatch reads registered modules' current bindings and uses an event snapshot. Removed the secondary key index and the `Module.setKey` dependency on the global manager. Callbacks may remove/rebind modules without dispatching to a stale instance. Key 0 remains unbound.
- **Registry consistency:** replacing/removing an enabled module disables it and detaches persistence callbacks. Removing an old instance cannot remove its replacement. Conflicting normalized IDs/names, including cross-namespace collisions, fail before registry mutation. Re-registering the same instance is a no-op. Category arrays are defensive snapshots.
- **Explicit composition:** `ModuleManager(Collection<? extends Module>)` permits using actual module/registry code without classpath discovery or partial `Unsafe` construction. Normal startup retains deterministic discovery of annotated modules.
- **Removed obsolete storage scaffolding:** `Storage` and the uncalled `StorageHelper` existed only for the redundant mutable global lists and empty initialization methods.
- **Removed six unused helpers:** `utils/FileUtils`, `utils/Reflection`, `utils/config/SettingsUtils`, `utils/entity/EntityUtil`, `utils/math/PositionHandler`, `utils/player/PositionUtils`. Source/test/resource references and the parallel worktrees were checked. Module/command discovery scans their respective implementation packages, not these utility packages. Apache Commons `FileUtils` and Minecraft classes are unaffected.

Persisted module/setting IDs, defaults, config formats and rendering algorithms are unchanged. No historical gameplay implementation was activated simply because it existed.

## Evidence

Eight registry regressions were first run against the old registry with only the explicit-construction seam added: six failed (replacement lifecycle, stale unregister, unregister cleanup, identifier collision, mutable category cache and changed shared keybinds). The final suite also checks setting ownership and read-only access.

The first untouched source basis (`e153e605d`) failed one existing Scaffold test due to shared synthetic client initialization. Main independently fixed its fixture in `d056fead8`; this worktree fast-forwarded to that fixed basis before final validation. No Scaffold production workaround was added here.

Validation: Temurin 8u482 / Maven 3.9.15, `mvn -o clean test`: **276 tests, zero failures/errors/skips**. Four standalone checks passed: ClickGUI, Arraylist, Theme Editor (with SessionInfo) and combined HUD/rotation/entity glow. They exercise actual module discovery and productive UI paths in Pbuffer/FBO fixtures, not a native game session. Test logs and offscreen previews are under `target`, never in personal runtime directories.

## Remaining findings and ownership

| Finding | Evidence / next action |
| --- | --- |
| Large render utility duplication | `utils.RenderUtil` and `utils.render.RUtil` have identical bodies for numerous image, rounded shape, ellipse, gradient and framebuffer functions. Migrate callers by coordinate/GL-state contract into the Visual agent's shared shader/effect path, then remove obsolete methods. Do not collapse every renderer into one giant class. |
| Duplicated color/gradient code | `interpolateTwoColorsSmoothly` is copied into ESP, Nametags, iOS, StarParticle and iOS controls; `ColorWheel` duplicates much of `utils.render.ColorUtil`. Include in the active global-gradient/effect work. |
| Unused visual candidates | `anim.Anim`/`Ease`, several easing classes, `AcrimonyRender`, `ColorWheel`, `ColorManager`, `PlayerHeadDrawer`, `HoverUtil` and `shader.Accessor` had no external Java consumers in this inspected basis (some only reference each other). Recheck against the active Visual branch before deleting; do not confuse them with the used animation/shader classes. |
| Duplicate Minecraft access | `MC`, `Eierlecken`, `Utils`, `Wrapper` overlap. Some also capture `fontRendererObj` statically. Consolidation crosses active UI, module and Minecraft-hook files; check initialization and lifecycle before migrating. |
| Slot ownership overlap | `SlotSpoofHandler` is constructed by Pulsar but has no observed consumers beyond its owner field. `SlotHandler` is discovered as a module and is read by NoSlow, so it is not dead code. Its method-style `@Subscribe` and coordination with actual slot synchronization need a dedicated behavior check before consolidation. |
| Runtime fallback instances | `RuntimeServices` creates fallback services alongside `ClientRuntime` services. Establish bootstrap ownership and test state handover before removing fallbacks; a cosmetic merge could reset live rotation/attack state. |

These areas were intentionally not edited concurrently with the running Visual, Account and historical-module work. The list is evidence for the next bounded cleanup, not a claim that the entire client is now audited or complete.

## Integration

Apply the cleanup commits after Main's fixed basis or merge the fixed branch. The only ThemeDraft change removes the obsolete `ValueStorage` import/cleanup call; keep any newer visual editor behavior when resolving that line. Do not restore global settings registration from another branch. Check consumers for removed storage APIs and rerun the full Java-8 suite plus ClickGUI, Arraylist, Theme Editor and combined-HUD checks.
