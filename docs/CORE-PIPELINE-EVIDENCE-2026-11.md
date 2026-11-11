# Chunk pipeline: implementation and evidence

Date: 2026-11-11. Baseline: `97c263c792864d03c2271670121fd72d53bbfeb1`.

This change corrects chunk task lifecycle and reduces a measured CPU cache cost. It does **not** establish an in-game FPS, GPU-time or packet-to-visible improvement. The exact baseline artifact, isolated candidate class, ordered samples and validation boundaries are distinguished below.

## Changes and their contracts

### Cache bounds and reusable CPU state

`ChunkCacheOF` now allocates lookup arrays for the requested block range plus its explicit margin. A normal 16-cube rebuild uses a 20 × 20 × 20 lookup region instead of rounding the region to three whole chunks on every axis. Each of the light and block-state arrays therefore needs **8,000 entries instead of 110,592**, a **92.766% reduction in entries**. Queries outside the cache range retain the existing source-cache fallback. This concerns heap lookup arrays; it is not a reduction in mesh VBO size or the dispatcher's direct-buffer pool.

`RenderChunk.rebuildChunk` returns its cache arrays in `finally`. Its single-layer scratch array belongs to the individual build. The reusable renderer's cached world column is volatile and must match the requested x/z chunk coordinates. An older canceled worker can therefore no longer make another build use a loaded column from the renderer's previous location. Region translations are derived from the build's captured position. Repositioning marks the renderer dirty even if its previous task had already completed.

Relevant production code: [ChunkCacheOF](../src/main/java/net/optifine/override/ChunkCacheOF.java), [RenderChunk](../src/main/java/net/minecraft/client/renderer/chunk/RenderChunk.java).

### Persistent, bounded admission

Normal camera traversal preserves accepted tasks and waiting order. It no longer clears the compile queue each frame or compiles nearby player changes synchronously. A dirty renderer with a pending, compiling or uploading task keeps its dirty flag for a later rebuild; it does not cancel that task merely because another frame encounters it.

The compile queue remains bounded at 100 tasks. Queue entries capture immutable enqueue priority: urgent work receives a finite 50 ms credit, after which older normal work can precede newly urgent work. Successful admission alternates the preferred forced/normal class. This fairness starts before queue admission, so an already-expired frame budget that allows only one new task cannot indefinitely exclude all normal work. Active entries are skipped, clean entries are removed, and a rejected offer retains the dirty request for retry.

Transparency queue rejection now finishes the rejected task; it cannot remain pending outside the queue and block later sorts. The owner thread skips active transparency entries while retaining the existing maximum of one accepted transparency request per frame. World detach and dispatcher stop clear normal, forced and transparency waiters and the admission preference.

Relevant code: [RenderGlobal](../src/main/java/net/minecraft/client/renderer/RenderGlobal.java), [ChunkCompileQueue](../src/main/java/net/minecraft/client/renderer/chunk/ChunkCompileQueue.java), [ChunkRenderDispatcher](../src/main/java/net/minecraft/client/renderer/chunk/ChunkRenderDispatcher.java).

### Complete upload groups and resource ownership

Every completed mesh, including an empty mesh, has one owner-thread completion group. The group checks the renderer's current task identity and task status, uploads all required layers, then publishes the compiled chunk and its global tile-entity set under the renderer/task locks. CPU meshing no longer publishes the global tile-entity set itself. Empty meshes cannot publish directly from a compiler-thread immediate future.

The upload loop respects the incoming deadline instead of adding 100 ms. Its configured time and prepared-vertex-byte limits are **soft limits between complete groups**. At least one indivisible group can proceed when the deadline has expired or its byte size exceeds the budget. This guarantees progress, while keeping a normal frame from yielding halfway through a chunk revision. Byte accounting includes the uploader's existing quad-to-triangle expansion, and avoids expanding an already-converted buffer twice. These byte counts describe vertex data submitted by the client, not measured PCIe traffic or driver storage.

Builder ownership transfers once from CPU work to the completion future. Cancellation, interrupted acquisition and CPU/GL exceptions release an acquired lease once; completed tasks do not retain a pooled builder. Pooled and dedicated builders reset their drawing state and translation before reuse. Cancellation/repositioning is checked before any group uploads or publishes.

This is not transactional GPU storage: if a GL call throws after another layer has already uploaded, the existing fatal rendering-error path runs, with no VBO rollback. The normal-operation guarantee is that no frame boundary splits an accepted group. A single large group can still exceed the selected budget.

Relevant code: [ChunkRenderWorker](../src/main/java/net/minecraft/client/renderer/chunk/ChunkRenderWorker.java), [ChunkCompileTaskGenerator](../src/main/java/net/minecraft/client/renderer/chunk/ChunkCompileTaskGenerator.java), [ChunkFrameBudget](../src/main/java/me/youngjibbit/core/diagnostics/ChunkFrameBudget.java).

## Isolated cache benchmark

### Method and provenance

The baseline release JAR was built before production edits from the exact baseline revision. Its SHA-256 is `f80b4a65ceb77786b0469675fac3f014fe79fa17601cfdbc909bab46559a8fe8`. Both variants used this artifact and the same dependencies. The candidate classpath overrides **only `ChunkCacheOF.class`**; both use the same newly added CPU-only fixture and benchmark classes. Other pipeline changes therefore cannot explain this measurement.

Environment: Temurin `1.8.0_482-b08`, Linux x86-64, reported CPU AMD EPYC 9V45, nine processors visible to the JVM, `-Xms512m -Xmx512m`. The observed maximum heap was 514,850,816 bytes. This shared environment is not a controlled desktop gaming machine.

Three fresh JVM pairs ran in order **A/B, B/A, A/B**. Each JVM warmed up each case for 2,000 iterations, then retained 40 ordered samples of 100 iterations each. A sample is the average nanoseconds per cache lifecycle over those 100 iterations. All 240 samples per variant remain available; no outliers or cold samples were silently removed. The reported p95 is the nearest-rank p95 of these batch-average samples, not the p95 of individual rebuilds or game frames.

`reset_only` executes the production `renderStart`/`renderFinish` lifecycle, including pooled-array initialization and release. `reset_and_4096_queries` additionally queries light and block state at each of 4,096 positions in a section. The deterministic backing cache is a fixture; this does not exercise world/chunk locks, actual blocks, rendering, GL or packet arrival.

The candidate cache-source SHA-256 is `09af6622c5c9dc0b3a6e58d6ca361cfd9f16c727a7f67d9e1bc3ce90de99c522`; benchmark-source SHA-256 is `b2c617db0350c305221745d290c186adea0fd23950022a142248e929e43ab760`. Both were rechecked against the committed source files when copying the evidence.

### Results

Times below are microseconds per lifecycle. Percentages compare each candidate median with its paired baseline median.

| Case | Pair | Baseline median | Candidate median | Median change | Baseline p95 | Candidate p95 |
|---|---:|---:|---:|---:|---:|---:|
| Reset only | 1 | 34.368 | 3.570 | −89.61% | 41.327 | 3.933 |
| Reset only | 2 | 35.971 | 2.570 | −92.85% | 43.678 | 4.091 |
| Reset only | 3 | 33.247 | 2.704 | −91.87% | 36.886 | 3.561 |
| Reset + 4,096 positions | 1 | 41.376 | 22.148 | −46.47% | 52.256 | **52.795** |
| Reset + 4,096 positions | 2 | 41.103 | 17.287 | −57.94% | 62.603 | 19.311 |
| Reset + 4,096 positions | 3 | 38.519 | 21.763 | −43.50% | 41.537 | 23.991 |

The median of the three paired median changes is **−91.87% for reset only** and **−46.47% with queries**. The first query pair's p95 is **1.03% higher** in the candidate. That result is retained and prevents a claim that every measured tail improved. Three pairs provide limited evidence of the isolated mechanism; they are not a confidence interval or a substitute for balanced in-game runs.

### Raw evidence and reproduction boundary

The [benchmark source](../src/test/java/net/optifine/override/ChunkCacheOFBenchmark.java) accepts one output JSON path. Compile its fixture against the baseline JAR, then run it in separate JVMs with either the baseline JAR or the isolated candidate cache-class directory first in the production classpath. Use the same dependencies and JVM arguments. Do not run the candidate's complete production classes against the baseline and describe that as an isolated-cache comparison.

Evidence files:

- [Provenance](evidence/core-2026-11/benchmark-provenance.json), [calculated comparison](evidence/core-2026-11/cache-comparison.json), and [SHA-256 manifest](evidence/core-2026-11/sha256-manifest.json).
- Pair 1: [baseline](evidence/core-2026-11/cache-1-baseline.json), [candidate](evidence/core-2026-11/cache-1-candidate.json).
- Pair 2: [baseline](evidence/core-2026-11/cache-2-baseline.json), [candidate](evidence/core-2026-11/cache-2-candidate.json).
- Pair 3: [baseline](evidence/core-2026-11/cache-3-baseline.json), [candidate](evidence/core-2026-11/cache-3-candidate.json).

Compact JSON copies preserve every numeric value and the original sample ordering. Runtime files, client worlds, dependencies and build artifacts are not included in these committed evidence files.


## Primary-source transfer review

These upstream trees were read at the pinned revisions linked below. Their Minecraft versions, loader APIs, licensing and mutable-state assumptions differ from Neutron. They informed mechanisms and rejection criteria; no third-party implementation was copied. A license label is not a finding that its code can be imported into this project. Nothirium reuse permission was not established.

| Source/revision | Inspected concrete mechanism | Neutron transfer / possible drawback | Version/licence / verification |
|---|---|---|---|
| [Sodium 1d6ab4b](https://github.com/CaffeineMC/sodium/tree/1d6ab4b42fa043f091aafc47253410f897211370) | `ChunkJobQueue` persistent urgent/normal deque with estimated work accounting; `ChunkBuilder` worker-owned reusable contexts with cleanup; `LimitedResourceBudget` duration and byte limits; `ClonedChunkSectionCache` bounded invalidatable snapshots. | RenderGlobal/ChunkRenderDispatcher: persistent work, deduplication, time+byte upload limits, render-thread publication. Budget can delay chunk visibility; measure that jointly with frametimes. | Current dev = Sodium 0.9.3-beta.4 for MC26.3 Fabric/NeoForge; **PolyForm Shield 1.0.0 (noncompete)**, reference only. Test cancellation/revisions, world changes, queue size, upload budgets, packet-to-visible latency. |
| [Patcher 4ce6e196](https://github.com/Sk1erLLC/Patcher/tree/4ce6e196e5ad1339f8a0ab96eb5680c2f6464583) | `ChunkRenderDispatcherMixin_LimitUpdates` sleeps 50ms above per-frame count; `VisGraphMixin_LimitScan` stops camera-component flood fill once >1 outward face found. | 1.8.9-comparable. **Reject worker sleep:** arbitrary latency and not an upload/time budget. Camera-only early stop can be independently implemented after exact caller equivalence audit; never apply to full chunk connectivity. | Forge/Mixin; **CC BY-NC-SA4**. Verify cave traversal, single/multi-exit components and visual equality. |
| [Nothirium eed45119](https://github.com/Meldexun/Nothirium/tree/eed451195dbd8207a629523577429b6b0e5968fe) | `RenderChunkDispatcher` CPU executor/render-thread handoff; `RenderChunkTaskCompile` chunk-result publication and builder cleanup in finally; `DynamicVBO` sector allocation with GL45/ARB DSA, GL44/ARB storage, GL15 fallback. | Worker lifecycle reference; Neutron VboRegion already batches so profile fragmentation before replacing it. **Reject unbounded render queue drain.** | MC1.12.2, mod0.4.9 beta, RenderLib/MemoryUtil; no licence file in full tree, repo licence metadata null, reuse permission not established. Test canceled/throwing tasks, VBO and display-list paths, memory across reloads. |
| [FalseTweaks f9e72dd6](https://github.com/FalsePattern/FalseTweaks/tree/f9e72dd65ebc94bb47087309e29eeb8df72c2c38) | `MulticoreMipMapEngine` per-sprite CPU jobs, joined before atlas use; `ThreadedUpdateHooks`, `ThreadSafeBlockBounds` isolate mutable render/pass state. | TextureMap/TextureAtlasSprite CPU-only preparation can be parallel with explicit ownership; thread-local chunk scratch. Snapshot/queue overhead may outweigh work for small packs. | MC1.7.10 reference; **LGPLv3-only with separate third-party and occlusion notices**. README documents OptiFine mipmap/PBR incompatibilities; current threaded chunks require Beddium. Pixel equality, reload/resource generations and failure propagation mandatory. |
| [Legacy EntityCulling 2c006a97](https://github.com/tr7zw/Legacy_EntityCulling/tree/2c006a97579d7196e340f6b31eee017a8dad0bbd) | `CullTask` async CPU AABB rays, bounded tracing distance, preallocated vectors, forced-visible and large-entity fallback. | Conservative visibility policy useful; **reject mutable world-list iteration off-thread with caught CME/NPE**, seen in implementation. Must separate camera from shadows. | 1.8.9 conditionals; mod1.6.2; **tr7zw Protective License 2026**, noncommercial. Validate teleport/camera changes, nameplates, custom hooks, shadows; stale/unknown result renders conservatively. |


Patcher's camera-component flood-fill optimization was not implemented: the matching `getVisibleFacings` path has no callers in this Neutron tree. Full chunk connectivity must not use the same early exit. FalseTweaks mipmap parallelism and additional entity culling also remain outside this patch; each needs its own image, lifecycle and end-to-end timing evidence.

## Validation coverage

The targeted suite runs the production methods with Java 8 and JUnit 4.13.2. Small fixtures bypass GL/client bootstrap only where stated; they do not simulate a successful native driver. The final targeted core total is **41 passing tests**:

| Test class | Tests | Contract covered |
|---|---:|---|
| `ChunkCacheOFTest` | 4 | Positive/negative halo boundaries, asymmetric ranges, cache hits and six-face fallback, release/reuse invalidation, independent concurrent cache owners |
| `ChunkCompileQueueTest` | 4 | Finite urgent credit, older normal progress, FIFO ties, capacity/removal, immutable queued priority, blocking wakeup and timed operations |
| `ChunkRenderDispatcherTest` | 4 | Rejected transparency cleanup/retry, cancellation removal, active-task deduplication without clearing dirty, idempotent finish callbacks |
| `ChunkRenderWorkerLifecycleTest` | 7 | CPU/upload failure and interrupted allocation, cancellation during mesh or queued completion, empty-mesh owner-thread publication, stale-task rejection, once-only builder return |
| `ChunkUploadGroupTest` | 6 | Whole groups across byte/expired-deadline boundaries, canceled/replaced/stopped task rejection before upload, failed-layer nonpublication, byte estimate against actual CPU quad conversion |
| `CompiledChunkTraceTest` | 3 | Rebuild → resort → draw preserves the original data association, first-draw references are cleared, retired captures stay inert, shared dummy geometry retains no renderer trace |
| `RenderChunkCacheIdentityTest` | 2 | Loaded cache from another renderer location is rejected; overlapping old/new task lookups return their own column |
| `RenderGlobalSchedulingTest` | 7 | Zero/expired-budget forced/normal fairness, skipping active/removing clean entries, rejected admission retry, duplicate/followup requests, clearing all lifecycle waiters/preference, transparency progress past active work |
| `ChunkFrameBudgetTest` | 4 | Deadline cap, zero/expired budget, configuration validation, reversible defaults, monotonic-clock wrap |

The scheduling fixtures initially reproduced two failures in the first persistent-scheduler implementation: retained forced/transparency waiters after stop, and a ready transparency request blocked by an active first entry. Both were corrected and the suite rerun. Upload tests record layer calls in place of OpenGL; their assertions establish ordering, ownership and publication contracts, not rendered pixels. The final combined isolated run also included all 15 `ChunkPipelineMetricsTest` cases, for **56 passing tests** in that invocation.

`WorldUnloadNativeCheck` was also updated and compiled. In its real-context fixture it now checks that repositioning a clean `RenderChunk` marks it dirty, seeds all three waiting sets, and verifies their removal plus fairness reset on actual null-world detach. Its existing frustum test creates and deletes 64 real VBOs. **The updated native check was not executed locally:** the available headless native environment was blocked before creating a GL context. Its CI result remains a separate gate. The pure cache/scheduler tests are not a replacement for this native lifecycle check.

The initial source audit's suspected normal `stopChunkUpdates` deadlock remains **rejected**. The normal `loadRenderers` path deletes the old frustum and cancels its tasks before waiting for the dispatcher's builders. No stop-loop redesign or deadlock-fix claim is part of this patch. Clearing retained scheduling sets addresses a different, directly reproduced lifecycle problem.

## Measurement meanings

The opt-in pipeline trace associates task creation, CPU mesh work, owner-thread upload and a subsequent CPU draw submission with a bounded capture epoch. Missing observations remain missing. The current draw hook records completion of the containing layer's submission, so `firstDrawSubmitNanos` is a **CPU layer-end upper bound for the chunk's first observed submission**. It is not proof that the GPU finished the chunk, that pixels were visible, or that presentation occurred. Shadow submissions are excluded. A packet/data marker, when present, refers to the observed column handoff and does not turn these stages into a network-latency measurement.

Packet associations use their actual section mask. Only a rebuild can inherit a pending section marker; its first draw retires that marker, while cancelled or failed attempts preserve it for retry. Full column replacements and unloads forget older associations before any new mask is registered. A compiled mesh retains at most its original rebuild trace and the latest transparency-sort trace until drawing, then clears both references. This preserves the rebuild's data-to-submission observation even when a resort publishes before the first draw. Earlier superseded sorts can remain incomplete in the bounded collector; no unobserved draw is invented.

Separate GPU timestamp-pair diagnostics can measure complete named GPU command intervals. They use a bounded pending pool, only read ready query results, and do not introduce `glFinish` or a production flush. They do not measure individual chunk visibility or exclusive GPU work. Driver-free query tests prove scheduling and cleanup, not GPU performance. See the diagnostics implementation and its native probe for that separate evidence.

New `RenderDiagnostics` fields are opt-in render-thread counters, with fixed memory and no added GL queries or clock reads:

| Field | Meaning |
|---|---|
| `framebufferBinds` | Instrumented framebuffer-bind API requests, including redundant requests; not physical state transitions |
| `programBinds` | Instrumented program-bind API requests, including redundant binds and zero-program requests |
| `drawSubmissions` | Instrumented draw API invocations; one multi-draw or call-lists call counts once; display-list recorded commands can be included |
| `sceneCopies` | Instrumented framebuffer-to-texture copy calls, including relevant depth copies; not a measured transfer byte count |

The chunk VBO, multi-texture, uploader and common `GlStateManager` paths are counted at their existing raw calls. `VboRegion` already reaches the instrumented multi-draw wrapper and is not counted again. Additional shader-path raw calls are instrumented by the shader change. The counters do not intercept every raw GL call throughout the repository, and their different abstraction levels must not be summed as a total GPU-work metric. Disabled counters add only the guarded code path, with no behavior change to the GL calls.

## Remaining acceptance gates and deliberate limits

The CPU cache result is established in the stated fixture. Full-world performance and correctness still require the same route, world state, render distance, resource/shader pack, resolution, frame cap and VSync settings on both revisions, with warmup, alternating run order and retained raw frame/chunk data. Frame p50/p95/p99, long-frame rate, 1% low, chunk queue delay and completed chunk throughput should be evaluated together: shorter per-frame upload work can trade a smoother frame for a later visible chunk.

Native acceptance includes both VBO and display-list paths, render regions, transparency order, resource reload, teleport/reposition, world exit/rejoin and shaderpack changes. The cache suite does not establish full image equality. Actual GPU results must identify vendor/driver/context and must not be inferred from software-GL or injected-driver timings.

The existing `ChunkCache` still refers to live world data. Task deduplication, local scratch and owner-thread publication do **not** make arbitrary world reads immutable or all meshing code thread-safe. No broader worker-count expansion, world snapshot system, direct-memory pool cap or VBO allocator replacement is included. The initial direct-buffer allocation remains approximately 10 MiB per `RegionRenderCacheBuilder`, plus growth and the dedicated synchronous builder; its heap-based pool heuristic warrants a separately measured memory change.

The user's updated instruction explicitly authorizes a regular merge after technical review and the relevant CI checks so that in-game testing can proceed on `main`. The PR remains draft while those technical gates are pending. Target-hardware, full-world image and named shaderpack acceptance remain open after that authorized merge. This evidence supports the implementation review and isolated cache result without substituting for those acceptance results.
