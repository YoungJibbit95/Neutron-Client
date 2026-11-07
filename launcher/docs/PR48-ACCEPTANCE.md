# PR #48 acceptance

The launcher depends on the Java 8 correction in PR #66. Do not merge this stack into GitHub main until that dependency is approved and merged. Automated checks and manual acceptance are separate; unchecked items below are not passed tests.

## Automated checks

- TypeScript renderer/backend checks and the complete Vitest suite.
- Reviewed source preparation, original/effective file hashes, changed-input rejection before mutation and cancellation.
- Managed Temurin 8u482-b08 / Maven 3.9.15 setup, real source package build and Bootstrap validation without test classes.
- Exact-commit warm reuse, damaged-file rejection, offline integrity/launch preparation and process cancellation tests.
- Windows candidate CI, including install/rendered first-run/reinstall/uninstall of the actual unsigned NSIS candidate with synthetic personal-data preservation.
- Repository policy, dependency review and the Java 8 regression suite on the final revision.

## Manual Windows acceptance

Use the final candidate's `win-unpacked/Neutron Launcher.exe` with an absolute `NEUTRON_DATA_ROOT` pointing to the isolated acceptance data directory. Do not select personal worlds or account data. The existing installed launcher is not used or replaced by agent tests. No developer tools are required by the packaged launcher.

1. Open the launcher normally. Confirm a composed usable window appears, then close and reopen it. Confirm settings and selected version survive.
2. Check all four views at 960×640 and 1180×780, four themes, German/English, 90–125% app scale, keyboard focus, reduced motion and zero effects. Confirm no inaccessible controls, clipped error messages or console overlap. Exercise GPU-disabled fallback separately if available; record any untested mode.
3. Check RAM, window size/fullscreen, game directory and custom-Java validation. Cancel the real folder/file picker and confirm no setting changes. Restore managed Java before continuing.
4. Build the supported latest main after #66 is merged, or use the explicitly recorded acceptance source commit. Confirm the verified build is selected only after completion. Repeat the same build and confirm the verified-cache message. Cancel an update and confirm the previous build remains selected.
5. Start the actual client. Confirm the menu, input response, client Settings and resource-pack screen. Stop from the launcher; confirm the owned client exits. Restart it and exercise the launcher's close dialog: stay, minimize, and quit with clean process shutdown.
6. With networking unavailable, reopen the launcher and start the existing verified version. Restore networking afterward. Do not disable Windows security settings for this test.
7. Exercise console filter/search, pause/resume, copy and export to a chosen local file. Check a local diagnostic preview for redaction without posting it. After a failed launch/build, confirm the error is readable and the last playable build remains available.

Record candidate SHA256, source commit, Windows version, passed items and concrete failures. Report untested crash/GPU/DPI paths explicitly. The agent does not drive the GUI or perform a visible Minecraft smoke test.

## Publication

This PR does not authorize a release. The candidate is unsigned. Publisher signing, SmartScreen reputation and public distribution remain separate release gates. Linux/macOS support is not claimed.
