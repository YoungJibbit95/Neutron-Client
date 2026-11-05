# Neutron Launcher — Windows playtest source

Electron/React launcher with managed Java/Maven, verified Minecraft assets, commit-specific local client builds, persistent options and real process output. The four views share the Sunset Graphite theme, compact left navigation, central launch/update controls and a resizable console. A lazy static WebGL glass light field has CSS fallback; reduced motion and zero effects skip GPU allocation.

## Development

Use Node with the pinned lockfile. Run from `launcher/`:

```powershell
npm ci --ignore-scripts
node scripts/build.mjs
node node_modules/vitest/vitest.mjs run
```

Desktop development requires an absolute `NEUTRON_DATA_ROOT` for isolated launcher data. The normal installed app uses its own application-data directory. No personal `test_run`, account data, build outputs or internal evidence is included here. See [measurement and change report](docs/SPRINT-20261105.md) and [current gates](docs/CURRENT-GATES.md).

## Current limitations

This is a draft playtest implementation, not a public release. The current GitHub-main POM is not yet supported by the reviewed source adapter: fresh source builds stop safely until that adapter is independently updated. Existing verified versions remain available. Signing, full cold-build comparison and complete Windows end-to-end acceptance remain open. No Linux/macOS support is claimed.
