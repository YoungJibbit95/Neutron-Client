# Windows x64 installation

Version 0.2.1 targets the requested stable channel; prerelease metadata remains
validated separately. The installer contains the launcher and an immutable source pin. It does
not contain a combined Minecraft/OptiFine client JAR, Minecraft assets, accounts,
worlds or developer build directories.

On a fresh machine, installation needs internet and at least 3 GiB free space.
This installer installs for the current Windows user only. Start it normally;
elevated or all-users installation is refused before launcher files are replaced.
The final NSIS step runs the installed launcher without opening a window. It
installs the hash-pinned Temurin 8u482 / Maven 3.9.15 toolchain, retrieves official
Minecraft 1.8.9 assets and matching x64 natives, then prepares the exact client
commit and source-archive SHA256 recorded in `resources/release.json`.
There is no moving `main` reference in this installation step. A changed archive
or build contract fails before Maven. The production Bootstrap validates the
packaged JAR/classpath before atomic selection of the new version.

For an exact commit already covered by the release's full Java-8 checks, the
packager may record `testedClientCommit` equal to `clientCommit`. Only that
installation build skips running the tests again. Normal update builds and
release/development checks retain their test gate. The release report must link
the original full-suite evidence; this metadata alone is not evidence.

After successful installation, Play hashes the selected client/runtime and starts
the installed `javaw.exe`. It does not resolve GitHub, download or invoke Maven.
An intact repeated installation reuses that exact verified local cache and runs
its production Bootstrap validation, including caches from older launchers.
Failed downloads, interruption or cancellation retain the previous selected client.
Uninstall retains launcher/game data. The log directory contains an installation
receipt and concrete errors; rerunning setup repairs an incomplete installation.
NSIS replaces the launcher EXE before preparing the client. A preparation failure
retains the previous selected **client**, but does not roll the launcher EXE back.
Unreadable existing state blocks automatic preparation without replacing settings,
even if the best-effort recovery copy could not be written.

Required validation, log flushing and writing `installer-result.json` precede
the final atomic `state.json` activation. The receipt is marked
`verified-before-activation` and counts only when its `selectedBuildId` matches
the saved selected version. A diagnostic-only log failure after that commit
does not turn a successful installation into a false failure.

`Neutron Launcher.exe --verify-alpha` checks the packaged pin and installed bytes
with launcher networking explicitly denied, without opening a game/window.
`--prepare-alpha --offline` also denies downloads and refuses a required rebuild.
This is used by the repeat-install acceptance. Offline account/session behavior
inside Minecraft is a separate client concern.

## Packaging and publication

The tracked `resources/release.json` contains the durable reviewed main commit
`1411f04c612c58190b5bc7ead0854c901e7945e3` and codeload SHA256
`317d6c2e5f0a5148123622b13849032de392901ac13ae9690a2e40df8df94e4b`.
The archive is 108,214,843 bytes. Its tree `01e65c06f4d8a9b0023310fd07f1fac9798b956d`
is the reviewed PR #88 final client: 1,152 native Java-8 tests without failures or
skips, six isolated packaged-runtime checks, and five successful PR checks.
CI and local packaging both check this same tracked pin; synthetic PR merge refs
are never substituted. Future releases must review a new durable commit/archive
hash and test evidence before changing the pin. Packaging rejects missing or
invalid metadata. Optional pin generation from explicit environment inputs refuses
to overwrite an existing file.

Publish the exact EXE that passed installer acceptance, without rebuilding it.
Publish under the separate `launcher-v0.2.1` tag so the full-client release
workflow is not triggered. Attach only the reviewed installer, SHA256 text and
release metadata. Verify the downloaded asset against the local SHA256 after
publication. The human authorized unsigned publication and subsequently requested
the stable channel on 7 November 2026. Publisher signing and SmartScreen reputation
are not claimed.

## Material and acceptance

The launcher optical model is adapted from the active Neutron
`Flauxy/Shaders/uiGlass.glsl`: lens shoulder, RGB dispersion, adaptive dark veil,
palette tint and directional edge reflection. A shared bounded WebGL canvas
samples its own clean/soft scene beneath DOM text. It redraws for layout/theme
changes, with no idle animation loop. Reduced motion/zero effects avoid the
material, and compilation/context failure restores opaque CSS panels.

Automated source/process tests and an installer receipt do not establish visual
acceptance. The final release report must state actual clean install, upgrade,
offline/cache/corruption results and measured times, plus unobserved GUI, DPI,
GPU-loss or client gameplay checks. No GUI control is authorized for the agent's
current release work.
