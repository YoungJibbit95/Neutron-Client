# Neutron Launcher — Windows x64

Version 0.3.0 downloads published Neutron JAR and version JSON files from
[GitHub Releases](https://github.com/YoungJibbit95/Neutron-Client/releases).
The catalog refreshes at startup, every five minutes while idle, manually and
before installation. Choose a version to download or download and start; the
newest stable client is the default and prereleases are marked separately.
Installed versions can be started locally.

Setup prepares managed Java 8, Minecraft assets/natives and verified release
files. Client source downloads, Maven and the source adapter are removed.
JAR/JSON hashes and sizes, the version contract, runtime and Bootstrap are checked
before activation. Failed operations preserve the prior selected client and settings.
The Electron/React interface, Liquid Glass, options and process console are retained.

## Installation

Run a launcher-v release EXE in your ordinary Windows account. The program and
persistent data folders are selected separately. First setup requires internet
and at least 3 GiB free on the data drive. Existing settings, versions and game
data remain on disk. The installer is unsigned and supports Windows x64 only.
See [installation](docs/RELEASE-INSTALLATION.md) and [release gates](docs/CURRENT-GATES.md).

## Development

Run from launcher/ using the pinned lockfile:

```powershell
npm ci --ignore-scripts
node scripts/check-release.mjs
node scripts/build.mjs
node node_modules/vitest/vitest.mjs run
node --test scripts/review-release.test.mjs scripts/publish-release.test.mjs
```

Desktop development requires an absolute NEUTRON_DATA_ROOT for isolated data.
No personal game directories, accounts or full client copies belong in the package.

## GitHub publication

Launcher Windows release builds and tests the actual NSIS candidate on PRs and
relevant main pushes. Independent artifact review checks the exact EXE, source
revision and standard-user cold/offline/upgrade receipts. Publication runs only
on main after a normal protected-branch merge and successful required checks.
The unchanged accepted EXE is uploaded, draft asset hashes are verified, and the
public download is rehashed after publication. Existing releases are retained.
Manual dispatch defaults to build-only; publish is accepted only on main.
