# WAVE06 — AI-AURA-CORE02 (pure Java 8, draft)

Status: independent neural rotation candidate **foundation only**. There is no Aura setting, renderer, live model loader, training worker, recorder, packet, click, placement or attack hook. No existing configs, modes, defaults or architecture files are changed.

## Gate 0, provenance and dependency

The frozen source register and privacy/rights analysis are in docs/WAVE06-AI-AURA-RESEARCH01.md (merged from PR #103, upstream Vibe SHA 584729c09ceafa8f1541260abc51bed3f97e91f4). Initial branch base: ce59fa121a724180e611e0ffa37962354c5eaf85, which includes the independent Research PR #103. Earlier Adaptive Math (#100), Aura (#101), and Speed/Hitpoint Presets (#102) are merged and MUST NOT be rewritten. VIS PR #104 owns GUI files; Scaffold PR #105 owns only research documentation.

Vibe's fourteen surrounding caller/UI integrations and thirteen aiaura classes are studied, not ported. Its top-level GPL-3.0 does not resolve third-party origins, weights or assets. Vibe's private/decompiled Gothaj Scaffold assets and helpers are explicitly BLOCKED. All three classes and tests below are independently authored with the Java 8 standard library; no Vibe/FDP source, checkpoint, real user training data, fonts or assets are included.

## B1 file and model schema

Three standalone Java classes in src/main/java/me/youngjibbit/core/combat/ai:

- **NeutronAiCheckpoint:** immutable, in-memory binary parser, dimensions and weight validation, fixed-workspace dense tanh inference.
- **NeutronAiFeatures:** independent target-relative, finite, normalized 40-value feature contract with no personal identifiers.
- **NeutronAiCore:** explicitly enabled, thread-confined, model-gated candidate calculator with motion-sequence idempotence and acquisition tombstones.

Version 1 input shape: 40 independent local features x four motion samples = **160**. Hidden layer: **16** tanh neurons; output: **2** tanh normalized yaw/pitch velocity commands. This differs intentionally from Vibe's 32/24 hidden and ten click/aim heads. No learned mouse-press, click-interval, target selection, hold-time, CPS or attack output exists. Fixed number of weights is **2,610**. Deterministic compute work is **2,592 multiply-adds and 18 tanh operations per accepted physical motion sample**; no unbounded model-dependent loops. This is an algorithmic cap, NOT a measured latency guarantee.

Fixed in-memory format (big-endian): 52-byte header with magic NAI1, schema version, exact dimensions/weight count, declared frame/click/reaction/epoch counts and finite training/validation losses; then exactly 2,610 float weights and a 32-byte SHA-256 digest. Total: **10,524 bytes**. Maximum accepted checkpoint envelope: **16 KiB**. The decoder rejects missing, short, long, wrong schema, wrong shape, malformed metadata, NaN/Infinity, weights outside [-8,8], and a mismatched digest. No unbounded allocation is based on untrusted input. The checkpoint never reads a file, parses JSON, opens a URL, or makes network calls.

Minimum declared metadata: 600–60,000 frames, at least 15 manual physical clicks and three reactions, at least one training epoch, finite losses within [0,20]. **These fields and SHA-256 are only syntactic/integrity checks, not authentication or proof of real user participation, consent or model quality.** A malicious actor can rehash forged contents. A future, separately reviewed local loader/trainer must verify origin, consent, account binding, schema authenticity, and held-out performance. No valid personal checkpoint is bundled.

## Frozen feature contracts and privacy

NeutronAiFeatures.Observation accepts only game geometry and kinematics: eye and target center coordinates, yaw/pitch, self/target velocities, target dimensions, reach and booleans for visibility, aliveness and grounded state. It rejects nonfinite coordinates/angles, pitch outside [-90,90], velocities outside ±4 blocks/tick, invalid dimensions/reach, dead or hidden targets and eye/target overlap. This is NOT a raycast; a future consumer must obtain visibility and target eligibility from Neutron's actual services.

Version-1 feature slots are frozen:

| Slots | Independent feature group |
|---|---|
| 0–1 | Wrapped yaw and pitch error |
| 2–6 | Normalized 3D/horizontal distance and x/y/z separation |
| 7–9 | Relative 3D velocity |
| 10–13 | Current and target yaw/pitch |
| 14–19 | Trigonometric yaw and yaw-error bases |
| 20–25 | Self and target velocity components |
| 26–28 | Grouped relative velocity components (reserved compatibility) |
| 29–32 | Distance/reach ratio, target width/height/depth |
| 33–34 | Closing-speed projection and pitch-error sine |
| 35–39 | Acquisition age, target validity gate, two grounded flags and lateral relative speed |

All values are clamped to finite [-1,1]. No account name, login, UUID, server/IP, chat, credential, raw physical input, per-user profile, file path or model-generated clicks appear in the vector. An account is represented only by an opaque **object identity token** within active acquisition validation; no account data is serialized.

## Candidate, thread, time and authority contract

- No model is installed by default. Missing/invalid model, disabled authorization, invalid geometry, wrong thread, mismatched identity or stale sequence returns **HOLD** with no inference. Only a future explicit trusted caller may provide structurally validated model bytes. Removing a model clears state.
- An acquisition requires owner, scene/world, play connection, target and account-scope object identities plus monotonically increasing connection epoch and acquisition generation. New tokens warm up with HOLD. Older or same-generation replacements cannot steal or resurrect a session; invalidation preserves the high-water marker.
- One finite positive time step per distinct motion sequence; duplicate/render/POST calls return the identical cached result. Long pause of 250 ms or more, backward time or revoked authorization invalidates the session. Elapsed time is capped at 75 ms for a single update, regardless of missed frames; signed System.nanoTime origins are supported.
- Neural commands are limited to 160 degrees/sec yaw and 100 degrees/sec pitch, commanded acceleration to 800 degrees/sec² and commanded jerk to 12,000 degrees/sec³. Wrong-direction commands produce no off-target step, and distance/pitch clamps forbid overshoot and pitch outside [-90,90]. The immediate terminal safety clamp does not constitute a universal strict jerk proof.
- The model can compute ONLY a rotation candidate. It never assumes a locally accepted C03, creates a C02, emits input, authorizes an attack, mutates MoveFix/RotationManager, executes raycasts or approves placement. The existing Neutron authority retains all of those operations.
- Per active acquisition raw scratch arrays: 40+160+16+2 floats, under 1 KiB excluding object overhead; no recording-length work or background threads. No wall-clock p95/heap profiler has been performed.

## Tests and review status

New JUnit 4 suites: **NeutronAiCheckpointTest (6), NeutronAiFeaturesTest (5), NeutronAiCoreTest (11)**, total **22** deterministic tests. Cases include corrupted/oversized/truncated/checksum-rehashed bad models; nonfinite metrics/weights/observations; forbidden dimensions/metadata; deterministic output and immutable parsed weights; no-model HOLD; thread confinement; motion reentry and render 20/60/144/240 Hz equivalence; target/account/scene/connection identity; sequence/generation tombstones; same-tick target switch; zero/long/backward time; opposing velocity; capped yaw/pitch and exact target settlement.

The GitHub-hosted Java-8 PR workflow must verify the **exact PR merge tree and real Maven/JUnit results**. Existing golden, network and UI suites must remain green. Native GL opt-ins, Minecraft launcher, online/server interactions, training, consent, model quality, CPU/heap profiling and real anti-cheat configurations remain **NOT_RUN**. No claim of human indistinguishability, bypass compatibility or future safety.

## Separate follow-up PRs — NO scope blending

**AI-AURA-AURA03:** Only after B1 CI and model/right/privacy review. Append explicit AI Learned under Aura Rot Mode, keep Basic/Adaptive/Arsenic defaults and config IDs; no model means no candidate or C02. Use Neutron's existing nearest sensitivity grid and confirmed C03/MoveFix/AttackScheduler/raycast authority, target/owner and world/connection invalidation, ConfigManager tests and real-motion fixtures.

**AI-AURA-RECORDER04:** Requires additional explicit user privacy approval. Visible opt-in, local-only manual data, narrow safe path, no personal/chat/server metadata, stop/delete control and no upload. No silent recording or weight import in B1.

**AI-AURA-TRAINING05:** Separate bounded CPU worker, cancel/epoch token, validation-only checkpoint publish, no synthetic-self training, temporal holdout with purge, fixed-seed reproducibility and resource budgets. UI coordination with Visual owner.

**SCAFFOLD-PLANNER02:** Only as a different pure-math Draft PR after reviewing Scaffold research #105; never copy Gothaj or create a new placement/packet pipeline.

Rollback: close this Draft PR; no preexisting source, config, local user files or runtime state was modified, and no Main merge, force push, workflow dispatch or release was performed.
