# Island: analog reminder, scoped branding and read-only activity

This follow-up builds on merged PR #143 (`3e115c0b998885a3c8bd3612172007688c52b11b`). It requires the companion activity producer in PR #154: `core.activity.ScaffoldActivitySnapshot`, `InventoryActivitySnapshot`, and cached getters on the existing Scaffold, InventoryManager and Stealer instances. Module notices additionally require the passive transition producer in PR #156. The rendering patch does not own or change their planners, slot policies, placement admission, clicks, transactions or packets.

## Actual product behavior

- `ClockReminderSchedule` retains its existing ten-minute deduplication and event duration. The entire Island now uses a 64×64 circular clock target with radius 32, bounded on both axes together for tiny viewports. The existing `IslandMorphMotion` changes width, height and radius into the circle and back; there is no second scheduler or animation engine. The dial is centered and fills the circular frame instead of occupying a small badge next to text.
- `WatermarkHUDModule.renderIsland` captures one real wall-clock instant and local zone before each deferred paint. `IslandClockFace.capture` freezes hour, minute and second directions for that frame, including fractional seconds and the corresponding minute/hour contribution. The second hand sweeps while motion is enabled. Reduced motion or disabled animations snap the shape immediately and use actual whole-second hand positions; clock data keeps advancing. The reminder identity/TTL remains tied to its original mark, so refreshing hands does not repeat or extend the event. Only idle/clock content may remain in a crossfade, never a lost gameplay target.
- The idle wordmark renders only the literal `Neutron` with Audiowide Regular. Its glyph draw explicitly enters `UiEffects.gradientSpace` for the measured wordmark rectangle and `TextEffects.direct`, which binds the existing FontInk theme-text gradient. This follows the shared selected theme/gradient policy without a new framebuffer. Version, time, target names, status and every other label keep their existing fonts. FontManager loads the face lazily through the existing ownership/cache mechanism; missing/corrupt font data returns the existing body face. Name and version switches remain independent.
- `IslandObservations` copies scalar facts from fresh immutable activity snapshots. It maps producer enums to presentation text and reuses each snapshot's freshness contract. It never rescans an inventory, reruns geometry/planning or triggers an action. The newest fresh inventory producer owns the displayed state; repeated render frames enqueue no events.
- `Island Scaffold` defaults on; it reads the already-enabled Scaffold instance. Its badge is a display-only ItemStack reconstructed from the captured registry ID and metadata, with the captured usable block count, approach/profile and actual safety status. Unknown count/item stays unknown; the existing original N is a safe fallback if no item can be displayed.
- `Island Inventory` is a separate opt-in setting, default off. Its display distinguishes Inventory Manager and Stealer, shows actual action and known counters, and never invents a denominator or percentage. The old hidden fixture key stays inert for saved-config compatibility. The renderer does not call the fixture hook.
- New settings append after existing registrations. Existing legacy constructors remain valid test fixtures and retain their `fixtureOnly` presentation marker. Observed Scaffold/inventory and the real local clock carry observed provenance. Module toasts and measured local-correction warnings use the observed notice adapters described in `WAVE10-ISLAND-OBSERVED-NOTICES.md`; no anticheat confirmation is fabricated.

## Font origin and license

The TTF is unmodified Audiowide Regular, version 1.003, a static TrueType face with no `fvar` table. It is bundled at `src/main/resources/assets/minecraft/Audiowide-Regular.ttf`; its complete original copyright notice and SIL OFL 1.1 are bundled at `src/main/resources/META-INF/licenses/Audiowide-OFL.txt`.

Primary source pinned to Google Fonts commit `5bb28733cec313b63fbd415b574b5ae35c8aa8b8`:

- [Original TTF](https://github.com/google/fonts/blob/5bb28733cec313b63fbd415b574b5ae35c8aa8b8/ofl/audiowide/Audiowide-Regular.ttf): 69,916 bytes, Git blob `8b50bedc0f99bcfcb5686a3dbeb5b51d1c0190d5`, SHA-256 `c7c0f2b0f6fad8c623e31772ce79f94a4edb9321ffce9fce978ea892d20ae730`.
- [Original OFL](https://github.com/google/fonts/blob/5bb28733cec313b63fbd415b574b5ae35c8aa8b8/ofl/audiowide/OFL.txt): 4,430 bytes, Git blob `19bb4adffab57778a892f98e9552441208896226`.

The font retains the reserved Audiowide name and its own OFL terms. No modifications to the font or claim of unrestricted script coverage are made. Existing `pulsar/Logo1.png` remains unchanged; there is no new logo bitmap or substituted N glyph.

## Regression evidence and limits

New ordinary JUnit tests intended for the Java8 CI:

- `IslandClockFaceTest`: actual hand directions, invalid input, fractional seconds/hour share, local zone, immutable per-frame capture, unchanged reminder identity, reduced-motion stepping and separate brand/version switches.
- `IslandClockMorphTest`: actual circular frame down to 1×1, centered 64px dial geometry, the production `IslandMorphMotion` converging into and out of the circle at 30/60/144/240fps, immediate reduced-motion/off geometry and clipped resize/reversal paths.
- `IslandObservationsTest`: exact copied block metadata/status, unknown counts, expiry/future timestamps, truthful inventory progress, newest producer selection, no repeated-event queue, visibility flags.
- `AudiowideResourceTest`: Java AWT loads the exact TTF and its Neutron glyphs, absence of variable-font axes, byte-exact OFL packaging, wordmark-only font choice and missing-resource fallback.

Local supplementary execution used OpenJDK 21.0.13's compiler with `-source 8 -target 8`, not a Java8 runtime: 17 clock/branding/AWT assertions and 15 snapshot-adapter assertions passed against the actual immutable producer classes. No JUnit stubs were used. Parent CI must run the real Java8 complete suite against the final combined tree and package the assets.

The separate clock review delta additionally passed 448 standalone geometry/time/motion assertions using the actual `IslandLayout`, `IslandClockFace`, `IslandMorphMotion`, `MenuSpring` and `MenuMotion` sources on that same JDK21 runtime. The unused `MenuMotion.resolve(ClientSettings)` signature needed a compile-only local ClientSettings dependency whose lookup throws if invoked; the harness never calls it. This proves the isolated motion math, not integration with real settings. The delivered ordinary JUnit tests have no substitutes and require the real Java8 CI. The explicit FontInk gradient path is source-reviewed; native glyph-gradient appearance is still NOT_RUN.

**Native Minecraft/Windows screenshots, GL state/item render lifetime, exact appearance at 640×360 / 854×480 / 1280×720 / 1920×1080, reduced-motion visual behavior and p95/p99 frame cost: NOT_RUN.** Local AWT metrics and headless logic do not establish native rendering acceptance.
