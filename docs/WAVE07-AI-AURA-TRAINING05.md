# WAVE07 TECH T3 / AI-AURA-TRAINING05 (Draft)

**Stacked review basis:** `codex/wave07-tech-t2-ai-aura-recorder04-20261108@fbe5d54a9590a753d5973a08f436e31f22a9f0b8`. This branch adds ONLY `NeutronAiTrainer`, the internal `NeutronAiCheckpoint.encodeLocalCandidate` method, `NeutronAiTrainerTest` and this file. Separate T1 and Visual branches are untouched. After T2 merges, rebase the T3 PR **without force pushing** onto the tested main; retest its exact merge SHA.

## Existing model and productive interface

- Single authoritative NAI1 `NeutronAiCheckpoint` schema and existing `NeutronAiCore` 160→16→2 `tanh` inference; no second ML framework or alternative checkpoint binary. The package-private encoder serializes the exact original header/dimensions, weights and SHA-256 then **decodes the resulting bytes** to validate the candidate.
- T2 `NeutronAiManualDataset` is the only input source. Fixed 160-feature rows with two bounded signed physical yaw/pitch velocity labels, no mouse-event identities, credentials, server strings, names, chat or telemetry.
- Training lives on one daemon `neutron-ai-local-cpu-trainer` via `ExecutorService`, with cancel and strictly increasing generation. New sessions invalidate older futures; neither backpropagation nor quality checks run in the calling Minecraft thread.

## Bounded training/validation

- Deterministic `java.util.Random(seed)` parameter initialization and training shuffle; mini-batch32 clipped-gradient tanh backprop; at most 64 epochs; default 24 epochs/30s/32MiB additional observed heap growth; strict absolute maximum 60s/64MiB. Each batch checks cancellation, generation, monotonic elapsed time and heap growth. No GPU/native dependency.
- Dataset max 8,000 rows and format version 4. At least 600 physical manual frames, 15 physical clicks, 3 manual reactions; additional minimum coverage **inside both** chronological training and validation sections.
- Chronological 80% training → 8-frame **embargo** → final approximately 20% validation (minimum 80 validation frames); no random shuffling across the temporal split. This separates the 4-frame history windows across the boundary. Shuffling *within* training uses the seed.
- Baseline = validation mean squared error of zero angular commands, observed on exactly the held-out validation section. A candidate must improve heldout loss by at least 2% and pass original checkpoint finite/weight/metric gates. Best-validation early stopping uses patience 8; fewer or uninformative rows fail closed. This is a conservative gate, **not** a measured real gameplay success rate.
- Report observed training time, peak additional heap allocation estimate and measured mean/max 128 real checkpoint inference calls after warmup; exceeding explicit per-run limits refuses the candidate.
- Return an in-memory candidate byte array only. **No file write, automatic model installation, auto-selection, deploy, uploading, ranking, trust claim, training service or telemetry**. `Status.CANDIDATE` means “structurally validated with synthetic/available manual holdout”, **not** “gameplay approved”. Tests contain only mathematical synthetic demonstrations. Production local training requires T2 explicit-consent capture and T4 trusted loader review.

## Review and safety gates

JUnit: deterministic synthetic convergence, byte-identical reproducibility, NAI1 serialization and corruption, bad dimensions and metrics, cancellation/generation, insufficient manual coverage, zero-quality labels, hard time/heap/inference limits. Keep all inherited Legacy/Config/Packet/Aura/Scaffold tests. Full Temurin8u482 Maven3.9.15 and exact synthetic GitHub PR merge SHA **PENDING** until CI completes. Native Minecraft, multiplayer, true manual data quality, privacy UX and sustained CPU/render-thread profiling **NOT_RUN**. No anticheat or indistinguishability guarantees.
