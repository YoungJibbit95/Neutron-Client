# WAVE07 TECH T2 / AI-AURA-RECORDER04 (Draft)

Base: `main@8cd765e61512bc8c111bee9cdd61c94e0f208b19`. T1 #121 is a separate, unmerged test-only Draft; Visual #115/#119/#122 is outside this PR.
No Vibe/Gothaj third-party implementation, model, data, weights, license-dependent helper or UI source copied. Historical rights review: `docs/WAVE06-AI-AURA-RESEARCH01.md`.

## Privacy and consent contract

- Nothing starts by default. The only entry is `NeutronAiManualRecorder.start(true, identityScope)`, an **explicit per-session action**. There is no startup listener, persistence of consent, background recording, automatic save or telemetry.
- `record` requires source `MANUAL_PHYSICAL_MOUSE`, a **separate** `automatedRotationActive=false` gate and a valid 40-value physical Neutron observation. Model inference/AI-generated motion **revokes and discards** the session; ordinary game events cannot become labels. Signed yaw/pitch degrees-per-second come solely from the future trusted physical-input consumer, never Aura's emitted pose.
- Identity tokens for account, world, player, connection, target, epoch and generation exist in RAM only. `onScopeChange` immediately drops the full capture and consent on any mismatch; `stopAndDiscard` and `revokeAndDelete` clear the state even when disk deletion fails.
- Fixed local `<trusted-game-dir>/neutron-ai/manual-v4/demo-00.nmd` through `demo-07.nmd` and `.pending` only, at most 8 files. No automatic load from arbitrary paths, network, chat, profile, token, username or server address fields. The decoder is binary-only.
- NMD4 schema: Minecraft 1.8.9 protocol 47, checkpoint schema 1, exact 160 inputs / 2 manual angular rates, big endian, relative elapsed-ms and two boolean *physical* event flags, hard byte limit below 6 MiB per file, 8,000 events, 30 minutes, <=250 ms contiguous gap, SHA-256 integrity trailer, strict finite and dimensional checks. SHA-256 is not human provenance/authenticity.
- File commit is explicit and atomic only (`CREATE_NEW` + `ATOMIC_MOVE`). Unsupported atomic filesystem fails closed. Owned directories are checked without following symlinks; only fixed managed file names may be deleted, never recursively traverse unknown data. No uploads or external dependencies.

## Real source/consumer boundary

This PR supplies a manual demonstration **recorder core and versioned local storage** using production `NeutronAiFeatures.populate`, `NeutronAiCheckpoint.INPUTS`, and `NeutronAiCore.MAX_*_DPS`; its 4-frame feature history is identical to the current model consumer. There is intentionally **no active game-data producer** until a visible, explicit consent and stop/delete UI plus a trusted raw-mouse provenance adapter have been accepted by the Visual worker. Starting in production without those controls would violate consent requirements. T3 will consume this strict schema and produce candidates only; no automatic model publication.

Visual-worker handoff (no Visual paths changed here): implement visible enable/recording-status, explicit start/stop/save and destructive-delete confirmation in its own branch, using these public API contracts. Physical input event hook and a current RotationManager/Aura owner check must be reviewed before enabling non-synthetic collection. Make session/account/world/reconnect changes call `onScopeChange`; do not persist consent.

## Verification

JUnit `NeutronAiManualDatasetTest` and `NeutronAiManualRecorderTest`: consent default, real Neutron feature history, provenance rejection, fixed binary format, digest, corruption/truncation, finite input, time/file/memory bounds, lifecycle invalidation, blocked writes, explicit local deletion, and non-owned data preservation. All inherited Neutron AI/core/Aura/Continuous Click/Scaffold tests must pass unchanged.

Temurin8u482 Maven3.9.15 full suite, security, CodeQL and release bundle are **PENDING** until the exact synthetic merge SHA is exercised. Native Minecraft, multiplayer, active user consent UX, privacy audit and latency/performance measurement **NOT_RUN**. No gameplay or user recording is enabled in this revision. No bypass/indistinguishability claims.
