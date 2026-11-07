# Current playtest gates

- The stale full-POM replacement is removed. The reviewed contract now checks the upstream POM, JavaFX build script and Bootstrap source/tests before applying seven official-coordinate aliases and omitting unused `patchy:1.7.7`. Current dependency/plugin versions and JavaFX packaging remain intact. A new build-input hash still requires review.
- Local repair validation (7 November 2026): both TypeScript checks and 132 automated launcher tests pass, including fresh source preparation, rejection of each changed build input before mutation, cancellation and old-toolchain migration/integrity checks. These checks do not substitute for a real Maven build, installed Windows start or interactive client acceptance.
- The Windows workflow's invalid job-level `runner.temp` references are replaced with step-scoped `RUNNER_TEMP` paths. A hosted-runner-only test now exercises the real NSIS install/start/reinstall/uninstall lifecycle and synthetic data preservation. Its actual CI result is still required.
- A verified cache entry requires the exact source commit, adapter fingerprint, runtime identifier/hash and a successful rehash of all client files. It does not validate a different upstream build configuration or authorize activation of corrupt bytes.
- No comparable full cold/warm Maven build was completed during this timeboxed pass. Cache lookup timings are explicitly separate from full build duration.
- The early rendered-window handshake was measured on a populated developer data fixture, not a clean Windows machine. The earlier blank-start report still requires repeatable normal-start, crash/recovery and deployment acceptance.
- Signing is unavailable. Do not publish an unsigned public release from this draft.
- Complete theme/language/DPI/reduced-motion and GPU-loss acceptance, installer update/uninstall, client settings, offline launch and export checks remain required.
