# Windows release gates — Launcher 0.3.0

The current installation uses published JAR/JSON assets. Historical 0.2.x source-build
receipts do not accept a new executable.

- Package, lockfile and schema-2 release metadata must agree on version/repository.
- TypeScript, launcher behavior/integrity tests and artifact-review tests must pass.
- Windows PowerShell 5.1 x64/x86 storage fixtures must pass, including legacy profiles,
  complete data copies, selected-release receipts, stale receipts and invalid state.
- The exact NSIS candidate must pass ordinary-user cold install, elevated/all-users
  refusal, release verification, renderer startup, failure preservation, offline
  reinstall and uninstall. Fresh setup must not create Maven or client source data.
- The real historical 0.2.0 installer is followed by the exact candidate. The receipt
  distinguishes complete old-install upgrade from repair of an incomplete registered
  install; settings/data preservation and uninstall remain required.
- Independent artifact review checks EXE SHA-256, version, source SHA and completed
  receipts. Offline checks must identify the same selected JAR/JSON/manifest.
- Publication requires a normal protected-main merge and successful Java 8 / Windows
  regression, Repository policy and Dependency review checks; no rules are bypassed.
- The accepted EXE is uploaded unchanged. Draft hashes/sizes are checked before
  publication and the public download afterward. Published releases are not replaced.

The Actions run and attached release-metadata.json, installer-acceptance.json and
legacy-diagnostic.json identify actual completed checks and timings. This document
states gates, not acceptance of an untested candidate. Sprint/PR48 reports are
historical evidence for their own source-build versions.

The installer remains unsigned. Automated checks do not establish SmartScreen
reputation, every theme/language/DPI/GPU-loss combination or real server gameplay.
Accounts, worlds, settings and previous versions remain protected.
