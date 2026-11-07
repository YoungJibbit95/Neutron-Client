# Neutron Launcher — Windows x64 Alpha

Electron/React launcher with managed Java/Maven, verified Minecraft assets, commit-specific local client builds, persistent options and real process output. The installer prepares the pinned client before completing, so Play starts the local version without Maven. The four views share the Sunset Graphite theme and Neutron's Liquid Glass material, with a resizable console and opaque CSS fallback. Reduced motion and zero effects skip GPU allocation. See the [installation and release contract](docs/ALPHA-INSTALLATION.md).

## Development

Use Node with the pinned lockfile. Run from `launcher/`:

```powershell
npm ci --ignore-scripts
node scripts/build.mjs
node node_modules/vitest/vitest.mjs run
```

Desktop development requires an absolute `NEUTRON_DATA_ROOT` for isolated launcher data. The normal installed app uses its own application-data directory. No personal `test_run`, account data, build outputs or internal evidence is included here. See [measurement and change report](docs/SPRINT-20261105.md) and [current gates](docs/CURRENT-GATES.md).

## Current limitations

The user authorized an unsigned Windows x64 Alpha. Signing/SmartScreen reputation is not claimed. Only the exact installer that passes final acceptance may be published; see the current gates for executed and pending checks. No Linux/macOS support is claimed.

## Reviewed source builds

The launcher uses the upstream `launcher-dist` profile, Bootstrap and JavaFX bundle. `resources/source-adapter/contract.json` pins the reviewed POM, JavaFX build script and Bootstrap source/tests. A changed build input stops a fresh build before Maven and requires an explicit contract review. The adapter only maps seven historical Maven aliases to Mojang's checksum-verified coordinates and omits the unused, unavailable `patchy:1.7.7` artifact. All other dependency/plugin versions remain upstream versions. The source snapshot records both original build-input hashes and the effective build tree; no full replacement POM is shipped.

Managed tools are Temurin 8u482-b08 and Maven 3.9.15, pinned to vendor archive hashes. An older toolchain requires Setup again; immutable old files, saved settings, versions and game data remain on disk. Exact commit/adapter/runtime/client-byte matches can reuse verified builds after setup.

The Windows candidate workflow packages without publishing and tests the actual NSIS installer on a disposable hosted runner. Its lifecycle test refuses existing launcher installations/data, checks installed application bytes and the renderer startup handshake with a system-only PATH, then checks reinstall/uninstall and preservation of synthetic personal data. It deliberately refuses to run on a developer machine.
